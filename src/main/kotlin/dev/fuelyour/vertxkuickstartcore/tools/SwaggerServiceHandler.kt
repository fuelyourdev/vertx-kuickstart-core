package dev.fuelyour.vertxkuickstartcore.tools

import dev.fuelyour.vertxkuickstartcore.annotations.Timeout
import dev.fuelyour.vertxkuickstartcore.exceptions.HTTPStatusCode
import dev.fuelyour.vertxkuickstartcore.exceptions.ResponseCodeException
import dev.fuelyour.vertxkuickstartcore.exceptions.TimeoutException
import dev.fuelyour.vertxkuickstartcore.exceptions.VertxKuickstartException
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponses
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.ext.web.FileUpload
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.jsonObjectOf
import java.lang.reflect.InvocationTargetException
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

interface ControllerSupplier {
    fun getControllerInstance(controllerName: String): Any
}

/**
 * Builds out service handlers for routing to the controllers
 */
class SwaggerServiceHandler(
    private val controllerSupplier: ControllerSupplier,
    serializer: Serializer,
    deserializer: Deserializer
) : Serializer by serializer, Deserializer by deserializer {
    fun createServiceHandlers(op: Operation, opId: String): RouteHandlers {
        val (controllerName, methodName) = opId.split('.')
        val controller =
            controllerSupplier.getControllerInstance(controllerName)
        val function =
            controller::class.functions.find { it.name == methodName }
                ?: throw Exception("Unable to parse operation $opId")

        return listOf(
            object : Handler<RoutingContext> {
                override fun handle(context: RoutingContext) {
                    GlobalScope.launch {
                        try {
                            val timeout =
                                function.findAnnotation<Timeout>()?.length
                                    ?: 30000
                            withTimeout(timeout) {
                                function.callWithParams(
                                    controller,
                                    context,
                                    op.parameters,
                                    op.requestBody,
                                    op.responses
                                )
                            }
                        } catch (ex: TimeoutCancellationException) {
                            replyWithError(
                                context, TimeoutException(
                                    "Timed out waiting for response",
                                    JsonArray(opId),
                                    ex
                                )
                            )
                        } catch (ex: Exception) {
                            replyWithError(context, ex)
                        }
                    }
                }
            }
        )
    }

    private suspend fun KFunction<*>.callWithParams(
        instance: Any?,
        context: RoutingContext,
        swaggerParams: List<Parameter>?,
        swaggerRequestBody: RequestBody?,
        swaggerResponses: ApiResponses
    ) {
        try {
            val params = buildParams(
                instance,
                context,
                swaggerParams,
                swaggerRequestBody
            )
            val response = callSuspendBy(params)
            handleResponse(context, response, swaggerResponses)
        } catch (e: InvocationTargetException) {
            val ex = e.targetException
            ex.printStackTrace()
            throw ex
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun KFunction<*>.buildParams(
        instance: Any?,
        context: RoutingContext,
        swaggerParams: List<Parameter>?,
        swaggerRequestBody: RequestBody?
    ): Map<KParameter, Any?> {
        val params: MutableMap<KParameter, Any?> = mutableMapOf()
        fullParameters.forEach { fullParam ->
            params[fullParam.param] = when {
                fullParam.param.kind == KParameter.Kind.INSTANCE -> instance
                fullParam.param.isSubclassOf(RoutingContext::class) -> context
                fullParam.param.isPathOrQueryParam(swaggerParams) ->
                    buildPathOrQueryParam(
                        swaggerParams,
                        fullParam.param,
                        context
                    )
                context.request().method() in listOf(
                    HttpMethod.POST,
                    HttpMethod.PATCH,
                    HttpMethod.PUT
                ) -> buildBodyParam(fullParam, context, swaggerRequestBody)
                else -> throw VertxKuickstartException(
                    "Parameter ${fullParam.name} in " +
                        "${fullParam.function.javaMethod?.declaringClass ?: ""}" +
                        ".${fullParam.function.name} does not match up with " +
                        "openapi definitions"
                )
            }
        }
        return params
    }

    private fun KParameter.isPathOrQueryParam(
        swaggerParams: List<Parameter>?
    ) =
        swaggerParams?.any {
            it.name == name && (it.`in` == "path" || it.`in` == "query")
        } ?: false

    private fun buildPathOrQueryParam(
        swaggerParams: List<Parameter>?,
        param: KParameter,
        context: RoutingContext
    ): Any? {
        return swaggerParams?.find { it.name == param.name }?.let { sp ->
            when (sp.`in`) {
                "path" -> parseParam(param, context.pathParam(param.name))
                "query" -> {
                    val queryParam = context.queryParam(param.name)
                    when {
                        param.isSubclassOf(List::class) -> queryParam
                        queryParam.isNotEmpty() -> parseParam(
                            param,
                            queryParam[0]
                        )
                        else -> null
                    }
                }
                else -> null
            }
        }
    }

    private fun buildBodyParam(
        fullParam: FullParameter,
        context: RoutingContext,
        swaggerRequestBody: RequestBody?
    ): Any? {
        val requestContentType = context.request().headers()["content-type"]
        val swaggerContentType = swaggerRequestBody?.content

        if (requestContentType.contains("application/json") &&
            swaggerContentType?.containsKey("application/json") == true) {

            return buildContentTypeApplicationJson(fullParam, context)
        } else if (requestContentType.contains("multipart/form-data") &&
            swaggerContentType?.containsKey("multipart/form-data") == true) {

            return buildContentTypeMultipartFormData(fullParam, context)
        } else {
            throw VertxKuickstartException(
                "Incomplete request body information"
            )
        }
    }

    private fun buildContentTypeMultipartFormData(
        fullParam: FullParameter,
        context: RoutingContext
    ): Any? {
        val getFormAttribute = {
            context.request().getFormAttribute(fullParam.name)
                ?: throw VertxKuickstartException(
                    "Request does not contain form attribute ${fullParam.name}"
                )
        }
        val toJsonObject = { JsonObject(getFormAttribute()) }
        val toJsonArray = { JsonArray(getFormAttribute()) }
        return when (fullParam.kclass) {
            JsonObject::class -> toJsonObject()
            JsonArray::class -> toJsonArray()
            List::class -> fullParam.type.instantiate(toJsonArray())
            Map::class -> fullParam.type.instantiate(toJsonObject())
            ByteArray::class -> getFormAttribute().toByteArray()
            Boolean::class -> getFormAttribute().toBoolean()
            Double::class -> getFormAttribute().toDouble()
            Float::class -> getFormAttribute().toFloat()
            Instant::class -> Instant.parse(getFormAttribute())
            Int::class -> getFormAttribute().toInt()
            Long::class -> getFormAttribute().toLong()
            String::class -> getFormAttribute()
            Field::class -> throw VertxKuickstartException(
                "Field not allowed as a controller function param"
            )
            FileUpload::class -> context.fileUploads().find {
                it.name() == fullParam.name
            } ?: throw VertxKuickstartException(
                "Request does not contain file upload ${fullParam.name}"
            )
            else -> fullParam.type.instantiate(toJsonObject())
        }
    }

    private fun buildContentTypeApplicationJson(
        fullParam: FullParameter,
        context: RoutingContext
    ): Any? {
        return when (val kclass = fullParam.kclass) {
            JsonObject::class -> context.bodyAsJson
            JsonArray::class -> context.bodyAsJsonArray
            List::class -> fullParam.type.instantiate(context.bodyAsJsonArray)
            Map::class -> fullParam.type.instantiate(context.bodyAsJson)
            ByteArray::class,
            Boolean::class,
            Double::class,
            Float::class,
            Instant::class,
            Int::class,
            Long::class,
            String::class,
            Field::class -> throw Exception(
                "${kclass.simpleName} not allowed as a " +
                    "controller function param"
            )
            else -> fullParam.type.instantiate(context.bodyAsJson)
        }
    }

    private fun parseParam(param: KParameter, value: String): Any {
        return when {
            param.isSubclassOf(Int::class) -> value.toInt()
            param.isSubclassOf(Long::class) -> value.toLong()
            param.isSubclassOf(Boolean::class) -> value.toBoolean()
            param.isSubclassOf(UUID::class) -> UUID.fromString(value)
            else -> value
        }
    }

    private fun KParameter.isSubclassOf(clazz: KClass<*>): Boolean =
        this.type.jvmErasure.isSubclassOf(clazz)

    fun createFailureHandlers(): RouteHandlers =
        listOf(
            object : Handler<RoutingContext> {
                override fun handle(context: RoutingContext) {
                    replyWithError(context, context.failure())
                }
            }
        )

    private fun handleResponse(
        context: RoutingContext,
        response: Any?,
        swaggerResponses: ApiResponses
    ) {
        val swaggerResponse = swaggerResponses
            .filter { (statusCode, _) -> statusCode.startsWith('2') }
            .map { (_, swaggerResponse) -> swaggerResponse }
            .firstOrNull()
            ?: throw VertxKuickstartException("No success response defined")
        val content = swaggerResponse.content
        if (content == null) {
            if (response == Unit) {
                context.response().end()
            } else {
                throw VertxKuickstartException(
                    "Returning response, but openapi response content is not " +
                        "defined"
                )
            }
        } else if (content.containsKey("application/json")) {
            context.response().putHeader("content-type", "application/json")
            if (response == null) {
                context.response()
                    .end(jsonObjectOf("response" to null).encode())
            } else when (response) {
                is Unit -> throw VertxKuickstartException(
                    "Openapi response defined, but response not returned"
                )
                is ClusterSerializable -> context.response()
                    .end(response.encode())
                is List<*> -> context.response()
                    .end(response.serialize().encode())
                is Map<*, *> -> context.response()
                    .end(response.serialize().encode())
                else -> context.response().end(response.serialize().encode())
            }
        } else {
            val contentType = content.keys.firstOrNull()
                ?: throw VertxKuickstartException(
                    "Openapi response content type not defined"
                )
            context.response().putHeader("content-type", contentType)
            if (response is ByteArray) {
                context.response()
                    .end(Buffer.buffer(response))
            } else if (response is DownloadFile) {
                context.response()
                    .putHeader(
                        "content-disposition",
                        "attachment; filename=${response.fileName}"
                    )
                    .end(Buffer.buffer(response.file))
            } else {
                throw VertxKuickstartException("Unable to process response")
            }
        }
    }

    private fun replyWithError(context: RoutingContext, failure: Throwable) {
        val response = context.response()
        when {
            failure is ResponseCodeException -> {
                response.putHeader("content-type", "application/json")
                response
                    .setStatusCode(failure.statusCode.value)
                    .end(failure.asJson().encode())
            }
            context.statusCode() <= 0 -> response
                .setStatusCode(HTTPStatusCode.INTERNAL_ERROR.value)
                .end(failure.message ?: "")
            else -> response
                .setStatusCode(context.statusCode())
                .end(failure.message ?: "")
        }
    }

    private fun ClusterSerializable.encode(): String {
        return when (this) {
            is JsonObject -> this.encode()
            is JsonArray -> this.encode()
            else -> this.toString()
        }
    }
}

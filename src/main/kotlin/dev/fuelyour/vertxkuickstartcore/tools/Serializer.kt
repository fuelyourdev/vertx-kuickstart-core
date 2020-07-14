package dev.fuelyour.vertxkuickstartcore.tools

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.lang.reflect.Modifier
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaGetter

data class Field<T>(
    val value: T?,
    val present: Boolean
)

/**
 * Auto serialize objects to json
 */
interface Serializer {
    fun List<*>.serialize(): JsonArray
    fun <T : Any> T.serialize(): JsonObject
    fun Map<*, *>.serialize(): JsonObject
}

class SerializerImpl(val includePrivate: Boolean = false) : Serializer {

    override fun List<*>.serialize(): JsonArray {
        val arr = JsonArray()
        forEach { item ->
            when (item) {
                is ByteArray -> arr.add(item)
                is Boolean -> arr.add(item)
                is Double -> arr.add(item)
                is Float -> arr.add(item)
                is Instant -> arr.add(item)
                is Int -> arr.add(item)
                is Long -> arr.add(item)
                is String -> arr.add(item)
                is UUID -> arr.add(item.toString())
                is List<*> -> arr.add(item.serialize())
                is Map<*, *> -> arr.add(item.serialize())
                is Field<*> -> arr.add(item.serialize())
                null -> arr.add(null as Any?)
                else -> arr.add(item.serialize())
            }
        }
        return arr
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> T.serialize(): JsonObject {
        val json = JsonObject()
        this::class.declaredMemberProperties.forEach { prop ->
            if (includePrivate || prop.isPublic()) {
                if (includePrivate)
                    prop.isAccessible = true
                val key = prop.name
                when (val value = prop.getter.call(this)) {
                    is ByteArray -> json.put(key, value)
                    is Boolean -> json.put(key, value)
                    is Double -> json.put(key, value)
                    is Float -> json.put(key, value)
                    is Instant -> json.put(key, value)
                    is Int -> json.put(key, value)
                    is Long -> json.put(key, value)
                    is String -> json.put(key, value)
                    is UUID -> json.put(key, value.toString())
                    is List<*> -> json.put(key, value.serialize())
                    is Map<*, *> -> json.put(key, value.serialize())
                    is Field<*> -> if (value.present) json.put(
                        key,
                        value.serialize()
                    )
                    else -> if (value != null) json.put(key, value.serialize())
                }
            }
        }
        return json
    }

    private fun <T : Any> KProperty1<out T, *>.isPublic() =
        javaGetter?.let {
                getter -> Modifier.isPublic(getter.modifiers)
        } ?: false

    override fun Map<*, *>.serialize(): JsonObject {
        val json = JsonObject()
        forEach { (keyObj, value) ->
            val key = keyObj.toString()
            when (value) {
                is ByteArray -> json.put(key, value)
                is Boolean -> json.put(key, value)
                is Double -> json.put(key, value)
                is Float -> json.put(key, value)
                is Instant -> json.put(key, value)
                is Int -> json.put(key, value)
                is Long -> json.put(key, value)
                is String -> json.put(key, value)
                is UUID -> json.put(key, value.toString())
                is List<*> -> json.put(key, value.serialize())
                is Map<*, *> -> json.put(key, value.serialize())
                is Field<*> -> if (value.present) json.put(
                    key,
                    value.serialize()
                )
                else -> if (value != null) json.put(key, value.serialize())
            }
        }
        return json
    }

    private fun Field<*>.serialize(): Any? =
        when (value) {
            is ByteArray,
            is Boolean,
            is Double,
            is Float,
            is Instant,
            is Int,
            is Long,
            is String -> value
            is UUID -> value.toString()
            is List<*> -> value.serialize()
            is Map<*, *> -> value.serialize()
            is Field<*> -> throw Exception("Field of field not allowed")
            null -> null
            else -> value.serialize()
        }
}

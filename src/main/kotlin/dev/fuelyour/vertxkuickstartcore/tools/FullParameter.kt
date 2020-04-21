package dev.fuelyour.vertxkuickstartcore.tools

import dev.fuelyour.vertxkuickstartcore.exceptions.VertxKuickstartException
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

/**
 * Gets and stores additional information about parameters so that the additional information is easily accessible
 * when needed.
 */
class FullParameter internal constructor(
  val function: KFunction<*>,
  val param: KParameter
) {
  val name: String
    get() = param.name ?: handleMissingParamName()

  val kclass: KClass<*>
    get() {
      val javaType = param.type.javaType
      if (javaType is Class<*> && javaType.isArray)
        return javaType.kotlin
      return param.type.jvmErasure
    }

  val type: Type
    get() = param.type.javaType

  private fun handleMissingParamName(): Nothing {
    throw VertxKuickstartException(
      "Parameter names for the function ${function.name} are missing")
  }
}

val KFunction<*>.fullParameters
  get() = parameters.map {
    FullParameter(this, it)
  }

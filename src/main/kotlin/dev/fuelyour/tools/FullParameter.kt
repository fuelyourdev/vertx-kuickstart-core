package dev.fuelyour.tools

import dev.fuelyour.exceptions.VertxKuickstartException
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmErasure

class FullParameter internal constructor(
  val function: KFunction<*>,
  val param: KParameter,
  val type: Type
) {
  val name: String
    get() = param.name ?: handleMissingParamName()

  val `class`: KClass<*>
    get() = param.type.jvmErasure

  private fun handleMissingParamName(): Nothing {
    throw VertxKuickstartException(
      "Parameter names for the function ${function.name} are missing")
  }
}

val KFunction<*>.fullParameters
  get() = parameters.map {
    FullParameter(this, it, getTypeForParamAt(it.index))
  }

private fun KFunction<*>.getTypeForParamAt(index: Int): Type {
  return javaConstructor?.let {
    it.genericParameterTypes[index]
  } ?: javaMethod?.let {
    if (instanceParameter != null) {
      when (index) {
        0 -> it.declaringClass
        else -> it.genericParameterTypes[index - 1]
      }
    } else {
      it.genericParameterTypes[index]
    }
  } ?: throw VertxKuickstartException(
    "Unable to find Java reflection info for $this")
}

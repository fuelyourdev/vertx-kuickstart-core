@file:Suppress("UNUSED_PARAMETER", "unused")

package dev.fuelyour.tools

import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import kotlin.reflect.full.declaredFunctions

fun topLevelFunction(param1: Long, param2: Float) { }

fun String.extensionFunction(param1: Int) { }

class TestClass(val param1: String, val param2: Int) {
  companion object {
    fun companionFunction(param1: Short) { }
  }
  fun classFunction(param1: Double, param2: Boolean) { }
}

object Singleton {
  fun singletonFunction(param1: Char, param2: Byte) { }
}

class DeserializerTest : Deserializer by DeserializerImpl(), StringSpec() {
  init {
    val kclass = TestClass::class
    val ctor = kclass.constructors.first()
    val classFun = kclass.declaredFunctions.first()
    val topLevelFun = ::topLevelFunction
    val companionFun = TestClass.Companion::companionFunction
    val singletonFun = Singleton::singletonFunction
    val extensionFun = String::extensionFunction

    "getTypeForParamAt works for all types of constructors and functions" {
      forall(
        row(ctor, 0, String::class.java),
        row(ctor, 1, Int::class.java),

        // For class functions, the first parameter is an instance
        // of the class
        row(classFun, 0, TestClass::class.java),
        row(classFun, 1, Double::class.java),
        row(classFun, 2, Boolean::class.java),

        // For extension functions, the first parameter is an instance
        // of the class being extended
        row(extensionFun, 0, String::class.java),
        row(extensionFun, 1, Int::class.java),

        row(topLevelFun, 0, Long::class.java),
        row(topLevelFun, 1, Float::class.java),

        row(companionFun, 0, Short::class.java),

        row(singletonFun, 0, Char::class.java),
        row(singletonFun, 1, Byte::class.java)
      ) { method, paramIndex, type ->
        method.getTypeForParamAt(paramIndex) shouldBe type
      }
    }

    "getTypeForParamAt throws IndexOutOfBoundsException" {
      forall(
        row(ctor, ctor.parameters.size),
        row(classFun, classFun.parameters.size),
        row(extensionFun, extensionFun.parameters.size),
        row(topLevelFun, topLevelFun.parameters.size),
        row(companionFun, companionFun.parameters.size),
        row(singletonFun, singletonFun.parameters.size)
      ) { method, paramIndex ->
        shouldThrow<IndexOutOfBoundsException> {
          method.getTypeForParamAt(paramIndex)
        }
      }
    }
  }
}
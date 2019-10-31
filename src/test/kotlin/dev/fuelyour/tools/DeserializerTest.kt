@file:Suppress("UNUSED_PARAMETER", "unused")

package dev.fuelyour.tools

import io.kotlintest.data.forall
import io.kotlintest.shouldBe
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
    "getTypeForParam works for all types of constructors and functions" {
      val kclass = TestClass::class
      val ctor = kclass.constructors.first()
      val classFun = kclass.declaredFunctions.first()
      val topLevelFun = ::topLevelFunction
      val companionFun = TestClass.Companion::companionFunction
      val singletonFun = Singleton::singletonFunction
      val extensionFun = String::extensionFunction

      forall(
        row(ctor, ctor.parameters[0], String::class.java),
        row(ctor, ctor.parameters[1], Int::class.java),

        // For class functions, the first parameter is an instance
        // of the class
        row(classFun, classFun.parameters[0], TestClass::class.java),
        row(classFun, classFun.parameters[1], Double::class.java),
        row(classFun, classFun.parameters[2], Boolean::class.java),

        // For extension functions, the first parameter is an instance
        // of the class being extended
        row(extensionFun, extensionFun.parameters[0], String::class.java),
        row(extensionFun, extensionFun.parameters[1], Int::class.java),

        row(topLevelFun, topLevelFun.parameters[0], Long::class.java),
        row(topLevelFun, topLevelFun.parameters[1], Float::class.java),

        row(companionFun, companionFun.parameters[0], Short::class.java),

        row(singletonFun, singletonFun.parameters[0], Char::class.java),
        row(singletonFun, singletonFun.parameters[1], Byte::class.java)
      ) { method, param, type ->
        method.getTypeForParam(param) shouldBe type
      }
    }
  }
}
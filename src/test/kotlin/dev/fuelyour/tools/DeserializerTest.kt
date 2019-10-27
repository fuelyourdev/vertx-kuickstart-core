package dev.fuelyour.tools

import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import kotlin.reflect.full.declaredFunctions

class DeserializerTest : Deserializer by DeserializerImpl(), StringSpec() {
  init {
    "getTypeForParam works for constructors and class functions" {
      class TestClass(val param1: String, val param2: Int) {
        fun testMethod(param1: Double, param2: Boolean) { }
      }
      val kclass = TestClass::class
      val ctor = kclass.constructors.first()
      val testMethod = kclass.declaredFunctions.first()
      forall(
        row(ctor, ctor.parameters[0], String::class.java),
        row(ctor, ctor.parameters[1], Int::class.java),
        row(testMethod, testMethod.parameters[1], Double::class.java),
        row(testMethod, testMethod.parameters[2], Boolean::class.java)
      ) { method, param, type ->
        method.getTypeForParam(param) shouldBe type
      }
    }
  }
}
package dev.fuelyour.tools

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonObjectOf
import java.time.Instant



class DeserializerInstantiateTest :
    Deserializer by DeserializerImpl(), StringSpec() {

  init {
    "instantiate can instantiate data classes" {

      data class LowerClass(
        val param1: Long
      )

      data class UpperClass(
        val param1: Boolean,
        val param2: Double,
        val param3: Float,
        val param4: Instant,
        val param5: Int,
        val param6: Long,
        val param7: String,
        val param8: Field<Int>,
        val param9: List<String>,
        val param10: Map<String, Double>,
        val param11: LowerClass
      )

      val instant = Instant.now()
      val json = JsonObject()
        .put("param1", true)
        .put("param2", 2.0)
        .put("param3", 1.0F)
        .put("param4", instant)
        .put("param5", 6)
        .put("param6", 7L)
        .put("param7", "Hello World")
        .put("param8", 9)
        .put("param9", listOf("Test", "Strings"))
        .put("param10", jsonObjectOf("key" to 11.0))
        .put("param11", jsonObjectOf("param1" to 12L))

      val expected = UpperClass(
        true,
        2.0,
        1.0F,
        instant,
        6,
        7L,
        "Hello World",
        Field(9, true),
        listOf("Test", "Strings"),
        mapOf("key" to 11.0),
        LowerClass(12L)
      )

      val result = UpperClass::class.instantiate(json)

      result shouldBe expected
    }

    "instantiate sets param to null if not in json" {

      data class LowerClass(
        val param1: Long
      )

      data class UpperClass(
        val param1: Boolean?,
        val param2: Double?,
        val param3: Float?,
        val param4: Instant?,
        val param5: Int?,
        val param6: Long?,
        val param7: String?,
        val param8: Field<Int?>,
        val param9: List<String>?,
        val param10: Map<String, Double>?,
        val param11: LowerClass?
      )

      val json = JsonObject()

      val expected = UpperClass(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Field(null, false),
        null,
        null,
        null
      )

      val result = UpperClass::class.instantiate(json)

      result shouldBe expected
    }

      "instantiate throws an exception if parameter is wrong type" {
        data class MyClass(val myParam: Int)

        val json = jsonObjectOf("myParam" to "String")
        val exception = shouldThrow<Exception> {
          MyClass::class.instantiate(json)
        }
        exception.message shouldBe "MyClass.myParam expects type Int " +
            "but was given the value: String"
      }

    "instantiate throws an exception if parameter is null and is not nullable" {
      data class ParamNotNullable(val param1: Int)

      val exception = shouldThrow<Exception> {
        ParamNotNullable::class.instantiate(JsonObject())
      }
      exception.message shouldBe "ParamNotNullable.param1 cannot be null"
    }
  }
}
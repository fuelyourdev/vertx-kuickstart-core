package dev.fuelyour.vertxkuickstartcore.tools

import dev.fuelyour.vertxkuickstartcore.exceptions.VertxKuickstartException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.row
import io.kotest.data.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonArrayOf
import io.vertx.kotlin.core.json.jsonObjectOf
import java.time.Instant

class DeserializerInstantiateFieldTest :
    Deserializer by DeserializerImpl(), StringSpec() {

  init {
    "A field can be used for any of the standard types" {
      forAll(
        row(
          type<Map<String, Field<Boolean>>>().type,
          jsonObjectOf("key" to true),
          mapOf("key" to Field(value=true, present=true))
        ),
        row(
          type<Map<String, Field<Double>>>().type,
          jsonObjectOf("key" to 2.5),
          mapOf("key" to Field(value=2.5, present=true))
        ),
        row(
          type<Map<String, Field<Float>>>().type,
          jsonObjectOf("key" to 0.3f),
          mapOf("key" to Field(value=0.3f, present=true))
        ),
        row(
          type<Map<String, Field<Instant>>>().type,
          JsonObject().put("key", Instant.EPOCH),
          mapOf("key" to Field(value=Instant.EPOCH, present=true))
        ),
        row(
          type<Map<String, Field<Int>>>().type,
          jsonObjectOf("key" to 2),
          mapOf("key" to Field(value=2, present=true))
        ),
        row(
          type<Map<String, Field<String>>>().type,
          jsonObjectOf("key" to "Hello World"),
          mapOf("key" to Field(value="Hello World", present=true))
        ),
        row(
          type<Map<String, Field<List<Double>>>>().type,
          jsonObjectOf("key" to jsonArrayOf(2.5)),
          mapOf("key" to Field(value=listOf(2.5), present=true))
        ),
        row(
          type<Map<String, Field<Map<String, Int>>>>().type,
          jsonObjectOf("key" to jsonObjectOf("innerKey" to 1)),
          mapOf("key" to Field(mapOf("innerKey" to 1), present=true))
        ),
        row(
          type<Map<String, Field<JsonObject>>>().type,
          jsonObjectOf("key" to jsonObjectOf("innerKey" to "value")),
          mapOf("key" to Field(jsonObjectOf("innerKey" to "value"), true))
        ),
        row(
          type<Map<String, Field<JsonArray>>>().type,
          jsonObjectOf("key" to jsonArrayOf(2.5)),
          mapOf("key" to Field(value= jsonArrayOf(2.5), present=true))
        ),
        row(
          type<Map<String, Field<TestEnum>>>().type,
          JsonObject().put("key", TestEnum.ENUM_VALUE_1),
          mapOf("key" to Field(value=TestEnum.ENUM_VALUE_1, present=true))
        ),
        row(
          type<Map<String, Field<TestDataClass>>>().type,
          jsonObjectOf("key" to jsonObjectOf("param1" to 1)),
          mapOf("key" to Field(value=TestDataClass(1), present=true))
        )
      ) { type, json, expected ->
        val result = type.instantiate(json)
        result shouldBe expected
      }
    }

    "Directly nesting Field types is not allowed" {
      val json = jsonObjectOf("key" to "value")

      val exception = shouldThrow<VertxKuickstartException> {
        type<Map<String, Field<Field<String>>>>().instantiate(json)
      }

      exception.message shouldBe "Field of Field type not allowed"
    }

    @Suppress("NAME_SHADOWING")
    "A Field of ByteArray is allowed" {
      val json = JsonObject().put("key", ByteArray(1) { 0.toByte()})

      val result = type<Map<String, Field<ByteArray>>>().instantiate(json)
      result shouldNotBe null
      result?.let { result ->
        result.size shouldBe 1
        result["key"] shouldNotBe null
        result["key"]?.value shouldNotBe null
        result["key"]?.value?.let { bytes ->
          bytes[0] shouldBe 0.toByte()
        }
      }
    }
  }
}
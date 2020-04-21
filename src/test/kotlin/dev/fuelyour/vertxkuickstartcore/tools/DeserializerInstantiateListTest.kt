package dev.fuelyour.vertxkuickstartcore.tools

import dev.fuelyour.vertxkuickstartcore.exceptions.VertxKuickstartException
import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonArrayOf
import io.vertx.kotlin.core.json.jsonObjectOf
import java.time.Instant

enum class TestEnum {
  ENUM_VALUE_1
}

data class TestDataClass(val param1: Int)

class DeserializerInstantiateListTest :
    Deserializer by DeserializerImpl(), StringSpec() {

  init {
    "FullType.instantiate can instantiate lists and keep type info" {
      val json1 = jsonArrayOf("value1", "value2")
      val expected1 = listOf("value1", "value2")
      val result1: List<String?>? = type<List<String>>().instantiate(json1)
      result1 shouldBe expected1

      val json2 = jsonArrayOf(1.3, 2.5, 3.6)
      val expected2 = listOf(1.3, 2.5, 3.6)
      val result2: List<Double?>? = type<List<Double>>().instantiate(json2)
      result2 shouldBe expected2
    }

    "Type.instantiate can instantiate lists, but loses the list type" {
      forall(
        row(
          type<List<Boolean>>().type,
          jsonArrayOf(true, false),
          listOf(true, false)
        ),
        row(
          type<List<Double>>().type,
          jsonArrayOf(1.3, 2.5, 3.6),
          listOf(1.3, 2.5, 3.6)
        ),
        row(
          type<List<Float>>().type,
          jsonArrayOf(1.2f),
          listOf(1.2f)
        ),
        row(
          //todo report vertx bugs
          type<List<Instant>>().type,
          JsonArray().add(Instant.EPOCH),
          listOf(Instant.EPOCH)
        ),
        row(
          type<List<Int>>().type,
          jsonArrayOf(1, 2, 3, 4, 5),
          listOf(1, 2, 3, 4, 5)
        ),
        row(
          type<List<String>>().type,
          jsonArrayOf("value1", "value2"),
          listOf("value1", "value2")
        ),
        row(
          type<List<List<Int>>>().type,
          jsonArrayOf(jsonArrayOf(1, 2), jsonArrayOf(3, 4, 5)),
          listOf(listOf(1, 2), listOf(3, 4, 5))
        ),
        row(
          type<List<Map<String, String>>>().type,
          jsonArrayOf(jsonObjectOf("key" to "value")),
          listOf(mapOf("key" to "value"))
        ),
        row(
          type<List<JsonObject>>().type,
          jsonArrayOf(jsonObjectOf("key" to "value")),
          listOf(jsonObjectOf("key" to "value"))
        ),
        row(
          type<List<JsonArray>>().type,
          jsonArrayOf(jsonArrayOf("value1", "value2")),
          listOf(jsonArrayOf("value1", "value2"))
        ),
        row(
          type<List<TestEnum>>().type,
          JsonArray().add(TestEnum.ENUM_VALUE_1),
          listOf(TestEnum.ENUM_VALUE_1)
        ),
        row(
          type<List<TestDataClass>>().type,
          jsonArrayOf(jsonObjectOf("param1" to 1)),
          listOf(TestDataClass(1))
        )
      ) { type, json, expected ->
        val result: Any? = type.instantiate(json)
        result shouldBe expected
      }
    }

    "instantiate will return null when it is given null" {
      val json: JsonArray? = null

      val result = type<List<String>>().instantiate(json)

      result shouldBe null
    }

    "instantiate will not instantiate a list of Field type" {
      val json = jsonArrayOf("Test", null)

      val exception = shouldThrow<VertxKuickstartException> {
        type<List<Field<String>>>().instantiate(json)
      }
      exception.message shouldBe "List of Field type not allowed"
    }

    @Suppress("NAME_SHADOWING")
    "instantiate can instantiate a list of ByteArrays" {
      val json = JsonArray().add(ByteArray(1) { 0.toByte()})

      val result = type<List<ByteArray>>().instantiate(json)
      result shouldNotBe null
      result?.let { result ->
        result.size shouldBe 1
        result[0] shouldNotBe null
        result[0].let { bytes ->
          bytes[0] shouldBe 0.toByte()
        }
      }
    }

    "instantiate can instantiate an array of Strings" {
      val json = jsonArrayOf("Hello", "World")

      val expected = arrayOf("Hello", "World")

      val result = type<Array<String>>().instantiate(json)

      result shouldBe expected
    }

    "instantiate can instantiate JsonArray" {
      val json = jsonArrayOf("Hello", "World")

      val result = type<JsonArray>().instantiate(json)

      result shouldBe json
    }

    //Todo test arrays
  }
}
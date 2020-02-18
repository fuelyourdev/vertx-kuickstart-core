package dev.fuelyour.tools

import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.row
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonArrayOf
import io.vertx.kotlin.core.json.jsonObjectOf
import java.time.Instant

class DeserializerInstantiateMapTest :
    Deserializer by DeserializerImpl(), StringSpec() {

  init {
    "FullType.instantiateMap can instantiate maps and keep type info" {
      val json1 = jsonObjectOf("key1" to "value1", "key2" to "value2")
      val expected1 = mapOf("key1" to "value1", "key2" to "value2")
      val result1: Map<String, String?>? =
        type<Map<String, String>>().instantiateMap(json1)
      result1 shouldBe expected1

      val json2 = jsonObjectOf("key1" to 1, "key2" to 2)
      val expected2 = mapOf("key1" to 1, "key2" to 2)
      val result2: Map<String, Int?>? =
        type<Map<String, Int>>().instantiateMap(json2)
      result2 shouldBe expected2
    }

    "Type.instantiateMap can instantiate maps, but loses the map type" {
      forall(
        row(
          type<Map<String, Boolean>>().type,
          jsonObjectOf("key1" to true, "key2" to false),
          mapOf("key1" to true, "key2" to false)
        ),
        row(
          type<Map<String, Double>>().type,
          jsonObjectOf("key1" to 1.3, "key2" to 2.5, "key3" to 3.6),
          mapOf("key1" to 1.3, "key2" to 2.5, "key3" to 3.6)
        ),
        row(
          type<Map<String, Float>>().type,
          jsonObjectOf("key" to 1.2f),
          mapOf("key" to 1.2f)
        ),
        row(
          //todo report vertx bugs
          type<Map<String, Instant>>().type,
          JsonObject().put("theKey", Instant.EPOCH),
          mapOf("theKey" to Instant.EPOCH)
        ),
        row(
          type<Map<String, Int>>().type,
          jsonObjectOf(
            "key1" to 1,
            "key2" to 2,
            "key3" to 3,
            "key4" to 4,
            "key5" to 5
          ),
          mapOf(
            "key1" to 1,
            "key2" to 2,
            "key3" to 3,
            "key4" to 4,
            "key5" to 5
          )
        ),
        row(
          type<Map<String, String>>().type,
          jsonObjectOf("key1" to "value1", "key2" to "value2"),
          mapOf("key1" to "value1", "key2" to "value2")
        ),
        row(
          type<Map<String, List<Int>>>().type,
          jsonObjectOf(
            "key1" to jsonArrayOf(1, 2),
            "key2" to jsonArrayOf(3, 4, 5)
          ),
          mapOf("key1" to listOf(1, 2), "key2" to listOf(3, 4, 5))
        ),
        row(
          type<Map<String, Map<String, String>>>().type,
          jsonObjectOf("upperKey" to jsonObjectOf("key" to "value")),
          mapOf("upperKey" to mapOf("key" to "value"))
        ),
        row(
          type<Map<String, JsonObject>>().type,
          jsonObjectOf("upperKey" to jsonObjectOf("key" to "value")),
          mapOf("upperKey" to jsonObjectOf("key" to "value"))
        ),
        row(
          type<Map<String, JsonArray>>().type,
          jsonObjectOf("key" to jsonArrayOf("value1", "value2")),
          mapOf("key" to jsonArrayOf("value1", "value2"))
        ),
        row(
          type<Map<String, TestEnum>>().type,
          JsonObject().put("key", TestEnum.ENUM_VALUE_1),
          mapOf("key" to TestEnum.ENUM_VALUE_1)
        ),
        row(
          type<Map<String, TestDataClass>>().type,
          jsonObjectOf("key" to jsonObjectOf("param1" to 1)),
          mapOf("key" to TestDataClass(1))
        )
      ) { type, json, expected ->
        val result: Map<String, Any?>? = type.instantiateMap(json)
        result shouldBe expected
      }
    }

    "instantiateMap will return null when it is given null" {
      val json: JsonObject? = null

      val result = type<Map<String, String>>().instantiateMap(json)

      result shouldBe null
    }

    "instantiateMap can instantiate a map of Field type" {
      val json = jsonObjectOf("key1" to "Test", "key2" to null)
      val expected = mapOf(
        "key1" to Field("Test", true),
        "key2" to Field(null, true)
      )

      val result = type<Map<String, Field<String>>>().instantiateMap(json)

      result shouldBe expected
    }

    @Suppress("NAME_SHADOWING")
    "instantiateMap can instantiate a map of ByteArrays" {
      val json = JsonObject().put("key", ByteArray(1) { 0.toByte()})

      val result = type<Map<String, ByteArray>>().instantiateMap(json)
      result shouldNotBe null
      result?.let { result ->
        result.size shouldBe 1
        result["key"] shouldNotBe null
        result["key"]?.let { bytes ->
          bytes[0] shouldBe 0.toByte()
        }
      }
    }
  }
}
package dev.fuelyour.vertxkuickstartcore.tools

import dev.fuelyour.vertxkuickstartcore.exceptions.VertxKuickstartException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonArrayOf
import io.vertx.kotlin.core.json.jsonObjectOf
import java.time.Instant

class DeserializerInstantiateMapTest : StringSpec() {

    init {
        "FullType.instantiate can instantiate maps and keep type info" {
            val json1 = jsonObjectOf("key1" to "value1", "key2" to "value2")
            val expected1 = mapOf("key1" to "value1", "key2" to "value2")
            val result1: Map<String, String?>? =
                type<Map<String, String>>().instantiate(json1)
            result1 shouldBe expected1

            val json2 = jsonObjectOf("key1" to 1, "key2" to 2)
            val expected2 = mapOf("key1" to 1, "key2" to 2)
            val result2: Map<String, Int?>? =
                type<Map<String, Int>>().instantiate(json2)
            result2 shouldBe expected2
        }

        "Type.instantiate can instantiate maps, but loses the map type" {
            forAll(
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
                    // todo report vertx bugs
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
                val result: Any? = type.instantiate(json)
                result shouldBe expected
            }
        }

        "instantiate will return null when it is given null" {
            val json: JsonObject? = null

            val result = type<Map<String, String>>().instantiate(json)

            result shouldBe null
        }

        "instantiate can instantiate a map of Field type" {
            val json = jsonObjectOf("key1" to "Test", "key2" to null)
            val expected = mapOf(
                "key1" to Field("Test", true),
                "key2" to Field(null, true)
            )

            val result = type<Map<String, Field<String>>>().instantiate(json)

            result shouldBe expected
        }

        @Suppress("NAME_SHADOWING")
        "instantiate can instantiate a map of ByteArrays" {
            val json = JsonObject().put("key", ByteArray(1) { 0.toByte() })

            val result = type<Map<String, ByteArray>>().instantiate(json)
            result shouldNotBe null
            result?.let { result ->
                result.size shouldBe 1
                result["key"] shouldNotBe null
                result["key"]?.let { bytes ->
                    bytes[0] shouldBe 0.toByte()
                }
            }
        }

        "instantiate can instantiate a map where the key is an Int" {
            val json = jsonObjectOf("1" to "value")

            val expected = mapOf(1 to "value")

            val result = type<Map<Int, String>>().instantiate(json)

            result shouldBe expected
        }

        "Failure to convert key String to Int gives informative message" {
            val json = jsonObjectOf("key" to "value")

            val exception = shouldThrow<VertxKuickstartException> {
                type<Map<Int, String>>().instantiate(json)
            }

            exception.message shouldBe "Cannot convert key value \"key\" to Int"
        }

        "instantiate can instantiate a map where the key is a Long" {
            val json = jsonObjectOf("1" to "value")

            val expected = mapOf(1L to "value")

            val result = type<Map<Long, String>>().instantiate(json)

            result shouldBe expected
        }

        "Failure to convert key String to Long gives informative message" {
            val json = jsonObjectOf("key" to "value")

            val exception = shouldThrow<VertxKuickstartException> {
                type<Map<Long, String>>().instantiate(json)
            }

            exception.message shouldBe
                "Cannot convert key value \"key\" to Long"
        }

        "Cannot instantiate a map with a complex key" {
            val json = jsonObjectOf("1: 2" to "value")

            val exception = shouldThrow<VertxKuickstartException> {
                type<Map<Map<Int, Int>, String>>().instantiate(json)
            }

            exception.message shouldBe "Unsupported key type for map: " +
                "java.util.Map<java.lang.Integer, ? extends java.lang.Integer>"
        }

        // Todo add other key types
        // todo test generic keys
        // Todo test Map<String, Array>
    }
}

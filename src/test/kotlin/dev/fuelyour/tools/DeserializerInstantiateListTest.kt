package dev.fuelyour.tools

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.vertx.kotlin.core.json.jsonArrayOf

class DeserializerInstantiateListTest :
    Deserializer by DeserializerImpl(), StringSpec() {

  init {
    "instantiateList can instantiate lists of Strings" {
      val json = jsonArrayOf("value1", "value2")
      val expected = listOf("value1", "value2")
      val result: List<String?>? = type<List<String>>().instantiateList(json)
      result shouldBe expected
    }
  }
}
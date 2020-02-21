package dev.fuelyour.tools

import dev.fuelyour.exceptions.VertxKuickstartException
import io.kotlintest.matchers.beInstanceOf
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.jsonArrayOf
import io.vertx.kotlin.core.json.jsonObjectOf
import java.lang.ClassCastException
import java.time.Instant

enum class MyEnum {
  MyValue1,
  MyValue2
}

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
        val param11: JsonObject,
        val param12: JsonArray,
        val param13: MyEnum,
        val param14: LowerClass
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
        .put("param11", jsonObjectOf("someKey" to "someValue"))
        .put("param12", jsonArrayOf("val1", "val2"))
        .put("param13", MyEnum.MyValue2)
        .put("param14", jsonObjectOf("param1" to 12L))

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
        jsonObjectOf("someKey" to "someValue"),
        jsonArrayOf("val1", "val2"),
        MyEnum.MyValue2,
        LowerClass(12L)
      )

      val result = type<UpperClass>().instantiate(json)

      result shouldBe expected
    }

    "instantiate can handle byte array params" {
      data class MyClass(val bytes: ByteArray) {

        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as MyClass

          if (!bytes.contentEquals(other.bytes)) return false

          return true
        }

        override fun hashCode(): Int {
          return bytes.contentHashCode()
        }

      }

      val json = jsonObjectOf("bytes" to ByteArray(1) {i -> i.toByte()})
      val expected = MyClass(ByteArray(1) {i -> i.toByte()})

      type<MyClass>().instantiate(json) shouldBe expected
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
        val param11: JsonObject?,
        val param12: JsonArray?,
        val param13: MyEnum?,
        val param14: LowerClass?
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
        null,
        null,
        null,
        null
      )

      val result = type<UpperClass>().instantiate(json)

      result shouldBe expected
    }

    "A Field param distinguishes between missing params and explicit nulls" {
      data class MyClass(
        val param1: Field<String>,
        val param2: Field<String>,
        val param3: Field<String>
      )

      val json = jsonObjectOf(
        "param1" to "present",
        "param2" to null
      )

      val expected = MyClass(
        Field("present", true),
        Field(null, true),
        Field(null, false)
      )

      val result = type<MyClass>().instantiate(json)

      result shouldBe expected
    }

    "instantiate throws an exception if parameter is wrong type" {
      data class MyClass(val myParam: Int)

      val json = jsonObjectOf("myParam" to "String")
      val exception = shouldThrow<VertxKuickstartException> {
        type<MyClass>().instantiate(json)
      }
      exception.message shouldBe "MyClass.myParam expects type Int " +
          "but was given the value: \"String\""
      exception.cause should beInstanceOf<ClassCastException>()
    }

    "instantiate throws an exception if parameter is null and is not nullable" {
      data class ParamNotNullable(val param1: Int)

      val exception = shouldThrow<VertxKuickstartException> {
        type<ParamNotNullable>().instantiate(JsonObject())
      }
      exception.message shouldBe "ParamNotNullable.param1 cannot be null"
    }

    "instantiate returns null if the json is null" {
      data class MyClass(val myParam: Int)

      type<MyClass>().instantiate(null) shouldBe null
    }

    "instantiate expects the class to have a primary constructor" {
      @Suppress("ConvertSecondaryConstructorToPrimary", "unused")
      class NoPrimaryCtor {
        val param1: Int
        constructor(param1: Int) {
          this.param1 = param1
        }
      }

      val exception = shouldThrow<VertxKuickstartException> {
        type<NoPrimaryCtor>().instantiate(JsonObject())
      }
      exception.message shouldBe "NoPrimaryCtor is missing a primary " +
          "constructor"
    }

    "instantiate can handle enum params" {
      data class ClassWithEnum(val myEnum: MyEnum)

      val json = jsonObjectOf("myEnum" to "MyValue1")
      val expected = ClassWithEnum(MyEnum.MyValue1)

      type<ClassWithEnum>().instantiate(json) shouldBe expected
    }

    "instantiate can handle null enum params" {
      data class ClassWithEnum(val myEnum: MyEnum?)

      val json = jsonObjectOf("myEnum" to null)
      val expected = ClassWithEnum(null)

      type<ClassWithEnum>().instantiate(json) shouldBe expected
    }

    "instantiate for enum with a value not in the enum throws an exception" {
      data class ClassWithEnum(val myEnum: MyEnum)

      val json = jsonObjectOf("myEnum" to "NotInEnum")

      val exception = shouldThrow<VertxKuickstartException> {
        type<ClassWithEnum>().instantiate(json)
      }
      exception.message shouldBe "Enum MyEnum does not contain value: NotInEnum"
    }

    "instantiate for enum with a non string value throws an exception" {
      data class ClassWithEnum(val myEnum: MyEnum)

      val json = jsonObjectOf("myEnum" to 1)

      val exception = shouldThrow<VertxKuickstartException> {
        type<ClassWithEnum>().instantiate(json)
      }
      exception.message shouldBe "ClassWithEnum.myEnum expects type MyEnum " +
          "but was given the value: 1"
      exception.cause should beInstanceOf<ClassCastException>()
    }

    "instantiate for custom class with list generics shouldn't loose generics" {
      data class ClassWithGenerics<T>(val value: T, val list: List<T>)
      data class OuterClass(
        val inner1: ClassWithGenerics<String>,
        val inner2: ClassWithGenerics<Int>,
        val inner3: ClassWithGenerics<List<Boolean>>,
        val inner4: ClassWithGenerics<List<ClassWithGenerics<Int>>>
      )

      val json = jsonObjectOf(
        "value" to jsonObjectOf(
          "inner1" to jsonObjectOf(
            "value" to "string",
            "list" to jsonArrayOf("value1")
          ),
          "inner2" to jsonObjectOf(
            "value" to 1,
            "list" to jsonArrayOf(2)
          ),
          "inner3" to jsonObjectOf(
            "value" to jsonArrayOf(true, false),
            "list" to jsonArrayOf(jsonArrayOf(false))
          ),
          "inner4" to jsonObjectOf(
            "value" to jsonArrayOf(
              jsonObjectOf(
                "value" to 1,
                "list" to jsonArrayOf(2)
              )
            ),
            "list" to jsonArrayOf(jsonArrayOf())
          )
        ),
        "list" to jsonArrayOf()
      )

      val expected = ClassWithGenerics(
        OuterClass(
          ClassWithGenerics("string", listOf("value1")),
          ClassWithGenerics(1, listOf(2)),
          ClassWithGenerics(listOf(true, false), listOf(listOf(false))),
          ClassWithGenerics(
            listOf(ClassWithGenerics(1, listOf(2))),
            listOf(listOf())
          )
        ),
        listOf()
      )

      val result = type<ClassWithGenerics<OuterClass>>().instantiate(json)

      result shouldBe expected
    }

    "instantiate for custom class with generics shouldn't loose generics" {
      data class ClassWithGenerics<T>(val value: T)
      data class OuterClass(
        val inner1: ClassWithGenerics<String>,
        val inner2: ClassWithGenerics<Int>,
        val inner3: ClassWithGenerics<List<Boolean>>,
        val inner4: ClassWithGenerics<List<ClassWithGenerics<Int>>>
      )

      val json = jsonObjectOf("value" to jsonObjectOf(
        "inner1" to jsonObjectOf("value" to "string"),
        "inner2" to jsonObjectOf("value" to 1),
        "inner3" to jsonObjectOf("value" to jsonArrayOf(true, false)),
        "inner4" to jsonObjectOf(
          "value" to jsonArrayOf(jsonObjectOf("value" to 1))
        )
      ) )

      val expected = ClassWithGenerics(
        OuterClass(
          ClassWithGenerics("string"),
          ClassWithGenerics(1),
          ClassWithGenerics(listOf(true, false)),
          ClassWithGenerics(listOf(ClassWithGenerics(1)))
        )
      )

      val result = type<ClassWithGenerics<OuterClass>>().instantiate(json)

      result shouldBe expected
    }

    "instantiate for custom class with generics should have clear error" {
      data class ClassWithGenerics<T>(val value: T)

      val json = jsonObjectOf("value" to 1)

      val exception = shouldThrow<VertxKuickstartException> {
        type<ClassWithGenerics<Boolean>>().instantiate(json)
      }

      exception.message shouldBe "ClassWithGenerics.value expects type " +
          "Boolean but was given the value: 1"
    }

    "instantiate works for a data class with an array" {
      data class ClassWithArray(
        val booleanArr: BooleanArray,
        val booleanObjArr: Array<Boolean>,
        val doubleArr: DoubleArray,
        val doubleObjArr: Array<Double>,
        val floatArr: FloatArray,
        val floatObjArr: Array<Float>,
        val intArr: IntArray,
        val intObjArr: Array<Int>,
        val longArr: LongArray,
        val longObjArr: Array<Long>,
        val stringArr: Array<String>,
        val instantArr: Array<Instant>
      ) {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as ClassWithArray

          if (!booleanArr.contentEquals(other.booleanArr)) return false
          if (!booleanObjArr.contentEquals(other.booleanObjArr)) return false
          if (!doubleArr.contentEquals(other.doubleArr)) return false
          if (!doubleObjArr.contentEquals(other.doubleObjArr)) return false
          if (!floatArr.contentEquals(other.floatArr)) return false
          if (!floatObjArr.contentEquals(other.floatObjArr)) return false
          if (!intArr.contentEquals(other.intArr)) return false
          if (!intObjArr.contentEquals(other.intObjArr)) return false
          if (!longArr.contentEquals(other.longArr)) return false
          if (!longObjArr.contentEquals(other.longObjArr)) return false
          if (!stringArr.contentEquals(other.stringArr)) return false
          if (!instantArr.contentEquals(other.instantArr)) return false

          return true
        }

        override fun hashCode(): Int {
          var result = booleanArr.contentHashCode()
          result = 31 * result + booleanObjArr.contentHashCode()
          result = 31 * result + doubleArr.contentHashCode()
          result = 31 * result + doubleObjArr.contentHashCode()
          result = 31 * result + floatArr.contentHashCode()
          result = 31 * result + floatObjArr.contentHashCode()
          result = 31 * result + intArr.contentHashCode()
          result = 31 * result + intObjArr.contentHashCode()
          result = 31 * result + longArr.contentHashCode()
          result = 31 * result + longObjArr.contentHashCode()
          result = 31 * result + stringArr.contentHashCode()
          result = 31 * result + instantArr.contentHashCode()
          return result
        }
      }

      val json = jsonObjectOf(
        "booleanArr" to jsonArrayOf(true, false),
        "booleanObjArr" to jsonArrayOf(false, true),
        "doubleArr" to jsonArrayOf(1.2, 3.4),
        "doubleObjArr" to jsonArrayOf(5.6, 7.8),
        "floatArr" to jsonArrayOf(1.1f, 2.2f),
        "floatObjArr" to jsonArrayOf(3.3f, 4.4f),
        "intArr" to jsonArrayOf(1, 2),
        "intObjArr" to jsonArrayOf(3, 4),
        "longArr" to jsonArrayOf(1L, 2L),
        "longObjArr" to jsonArrayOf(3L, 4L),
        "stringArr" to jsonArrayOf("value1", "value2"),
        "instantArr" to JsonArray().add(Instant.EPOCH)
      )
      val expected = ClassWithArray(
        booleanArr = booleanArrayOf(true, false),
        booleanObjArr = arrayOf(false, true),
        doubleArr = doubleArrayOf(1.2, 3.4),
        doubleObjArr = arrayOf(5.6, 7.8),
        floatArr = floatArrayOf(1.1f, 2.2f),
        floatObjArr = arrayOf(3.3f, 4.4f),
        intArr = intArrayOf(1, 2),
        intObjArr = arrayOf(3, 4),
        longArr = longArrayOf(1L, 2L),
        longObjArr = arrayOf(3L, 4L),
        stringArr = arrayOf("value1", "value2"),
        instantArr = arrayOf(Instant.EPOCH)
      )

      val result = type<ClassWithArray>().instantiate(json)

      result shouldBe expected
    }

    "instantiate works for a data class with a generic array" {
      data class ClassWithArray<T>(val arr: Array<T>) {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as ClassWithArray<*>

          if (!arr.contentEquals(other.arr)) return false

          return true
        }

        override fun hashCode(): Int {
          return arr.contentHashCode()
        }

      }

      val json = jsonObjectOf("arr" to jsonArrayOf(1, 2))
      val expected = ClassWithArray(arrayOf(1, 2))

      val result = type<ClassWithArray<Int>>().instantiate(json)

      result shouldBe expected
    }

    //todo test vararg

    //todo test array parameters

    //todo test error when expected array type and actual type are different

    //todo test double arrays
  }
}
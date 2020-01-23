package dev.fuelyour.tools

import dev.fuelyour.exceptions.VertxKuickstartException
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.lang.ClassCastException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod

typealias ListType<T> = FullType<List<T>>
typealias MapType<T> = FullType<Map<String, T>>

interface Deserializer {
  fun <T: Any> KClass<T>.instantiate(json: JsonObject?): T?
  fun <T: Any> ListType<T>.instantiateList(arr: JsonArray?): List<T?>?
  fun Type.instantiateList(arr: JsonArray?): List<Any?>?
  fun <T: Any> MapType<T>.instantiateMap(obj: JsonObject?): Map<String, T?>?
  fun Type.instantiateMap(obj: JsonObject?): Map<String, Any?>?
}

class DeserializerImpl: Deserializer {

  private val KFunction<*>.declaringClass: Class<*>
    get() =
      javaConstructor?.declaringClass
        ?: javaMethod?.declaringClass
        ?: throw VertxKuickstartException(
          "Unable to find Java reflection info for $this")

  override fun <T: Any> KClass<T>.instantiate(json: JsonObject?): T? {
    if (json == null) return null
    val ctor = primaryConstructor ?: handleMissingPrimaryConstructor()
    val params = ctor.fullParameters.map { param -> param.instantiate(json) }
    return ctor.call(*params.toTypedArray())
  }

  private fun FullParameter.instantiate(
    json: JsonObject
  ): Any? {
    return try {
      val value = when (kclass) {
        ByteArray::class -> json.getBinary(name)
        Boolean::class -> json.getBoolean(name)
        Double::class -> json.getDouble(name)
        Float::class -> json.getFloat(name)
        Instant::class -> json.getInstant(name)
        Int::class -> json.getInteger(name)
        Long::class -> json.getLong(name)
        String::class -> json.getString(name)
        Field::class -> type.instantiateField(json, name)
        List::class -> type.instantiateList(json.getJsonArray(name))
        Map::class -> type.instantiateMap(json.getJsonObject(name))
        JsonObject::class -> json.getJsonObject(name)
        JsonArray::class -> json.getJsonArray(name)
        else ->
          if (kclass.isEnum)
            kclass.instantiateEnum(json.getString(name))
          else
            kclass.instantiate(json.getJsonObject(name))
      }
      value.verifyOnlyNullIfParamAllows(this)
      value
    } catch (e: ClassCastException) {
        throw VertxKuickstartException(
          "${function.declaringClass.simpleName}.${name} " +
              "expects type ${kclass.simpleName} " +
              "but was given the value: ${json.getValue(name)}", e)
    }
  }

  private fun Any?.verifyOnlyNullIfParamAllows(param: FullParameter) {
    if (!param.param.type.isMarkedNullable && this == null) {
      throw VertxKuickstartException(
        "${param.function.declaringClass.simpleName}.${param.name} " +
            "cannot be null")
    }
  }

  private fun <T: Any> KClass<T>.handleMissingPrimaryConstructor(): Nothing {
    throw VertxKuickstartException(
      "$simpleName is missing a primary constructor"
    )
  }

  private val KClass<*>.isEnum: Boolean
    get() = java.isEnum

  @Suppress("UNCHECKED_CAST")
  private fun KClass<*>.instantiateEnum(enumString: String?): Any? {
    if (enumString == null) return null
    return with(java.enumConstants as Array<Enum<*>>) {
      try {
        first { it.name == enumString }
      } catch (e: NoSuchElementException) {
        throw VertxKuickstartException(
          "Enum $simpleName does not contain value: $enumString")
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T: Any> ListType<T>.instantiateList(
    arr: JsonArray?
  ): List<T?>? =
    this.type.instantiateList(arr) as List<T?>?

  override fun Type.instantiateList(arr: JsonArray?): List<Any?>? {
    if (arr == null) return null
    val range = 0 until arr.size()
    return this.let { type ->
      val genericType = type.getGenericType(0)
      when (val itemsKClass = genericType.kClass) {
        ByteArray::class -> range.map { arr.getBinary(it) }
        Boolean::class -> range.map { arr.getBoolean(it) }
        Double::class -> range.map { arr.getDouble(it) }
        Float::class -> range.map { arr.getFloat(it) }
        Instant::class -> range.map { arr.getInstant(it) }
        Int::class -> range.map { arr.getInteger(it) }
        Long::class -> range.map { arr.getLong(it) }
        String::class -> range.map { arr.getString(it) }
        Field::class -> throw VertxKuickstartException(
          "List of Field type not allowed"
        )
        List::class -> range.map {
          genericType.instantiateList(arr.getJsonArray(it))
        }
        Map::class -> range.map {
          genericType.instantiateMap(arr.getJsonObject(it))
        }
        JsonObject::class -> range.map { arr.getJsonObject(it) }
        JsonArray::class -> range.map { arr.getJsonArray(it) }
        else ->
          if (itemsKClass.isEnum) {
            range.map { itemsKClass.instantiateEnum(arr.getString(it)) }
          } else {
            range.map { itemsKClass.instantiate(arr.getJsonObject(it)) }
          }
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T: Any> MapType<T>.instantiateMap(
    obj: JsonObject?
  ): Map<String, T?>? =
    type.instantiateMap(obj) as Map<String, T?>?

  override fun Type.instantiateMap(obj: JsonObject?): Map<String, Any?>? {
    if (obj == null) return null
    return this.let { type ->
      val genericType = type.getGenericType(1)
      val itemsKClass = genericType.kClass
      val map = mutableMapOf<String, Any?>()
      obj.forEach { (key, _) ->
        when (itemsKClass) {
          ByteArray::class -> map[key] = obj.getBinary(key)
          Boolean::class -> map[key] = obj.getBoolean(key)
          Double::class -> map[key] = obj.getDouble(key)
          Float::class -> map[key] = obj.getFloat(key)
          Instant::class -> map[key] = obj.getInstant(key)
          Int::class -> map[key] = obj.getInteger(key)
          Long::class -> map[key] = obj.getLong(key)
          String::class -> map[key] = obj.getString(key)
          Field::class -> map[key] =
            genericType.instantiateField(obj, key)
          List::class -> map[key] =
            genericType.instantiateList(obj.getJsonArray(key))
          Map::class -> map[key] =
            genericType.instantiateMap(obj.getJsonObject(key))
          JsonObject::class -> map[key] = obj.getJsonObject(key)
          JsonArray::class -> map[key] = obj.getJsonArray(key)
          else ->
            if (itemsKClass.isEnum) {
              map[key] = itemsKClass.instantiateEnum(obj.getString(key))
            } else {
              map[key] = itemsKClass.instantiate(obj.getJsonObject(key))
            }
        }
      }
      map
    }
  }

  private fun Type.instantiateField(
    json: JsonObject,
    key: String
  ): Field<out Any?> {
    return this.let { type ->
      val genericType = type.getGenericType(0)
      when (val itemsKClass = genericType.kClass) {
        ByteArray::class -> Field(json.getBinary(key), json.containsKey(key))
        Boolean::class -> Field(json.getBoolean(key), json.containsKey(key))
        Double::class -> Field(json.getDouble(key), json.containsKey(key))
        Float::class -> Field(json.getFloat(key), json.containsKey(key))
        Instant::class -> Field(json.getInstant(key), json.containsKey(key))
        Int::class -> Field(json.getInteger(key), json.containsKey(key))
        Long::class -> Field(json.getLong(key), json.containsKey(key))
        String::class -> Field(json.getString(key), json.containsKey(key))
        Field::class -> throw VertxKuickstartException(
          "Field of Field type not allowed"
        )
        List::class -> Field(
          genericType.instantiateList(json.getJsonArray(key)),
          json.containsKey(key)
        )
        Map::class -> Field(
          genericType.instantiateMap(json.getJsonObject(key)),
          json.containsKey(key)
        )
        JsonObject::class -> Field(
          json.getJsonObject(key),
          json.containsKey(key)
        )
        JsonArray::class -> Field(json.getJsonArray(key), json.containsKey(key))
        else ->
          if (itemsKClass.isEnum) {
            Field(
              itemsKClass.instantiateEnum(json.getString(key)),
              json.containsKey(key)
            )
          } else {
            Field(
              itemsKClass.instantiate(json.getJsonObject(key)),
              json.containsKey(key)
            )
          }
      }
    }
  }

  private fun Type.getGenericType(index: Int): Type =
    let {
      val parameterizedType = it as ParameterizedType
      when (val typeArg = parameterizedType.actualTypeArguments[index]) {
        is WildcardType -> typeArg.upperBounds[0]
        else -> typeArg
      }
    }

  private val Type.kClass: KClass<*>
    get() = when (this) {
      is ParameterizedType -> rawType.typeName
      else -> typeName
    }.let { when (it) {
      "byte[]" -> ByteArray::class
      else -> Class.forName(it).kotlin
    } }
}

inline fun <reified T> type(): FullType<T> =
  type(object : TypeWrapper<T>() {}::class.java)

fun <T> type(typeWrapperClass: Class<out TypeWrapper<T>>): FullType<T> {
  val type = typeWrapperClass
    .let { it.genericSuperclass as ParameterizedType }
    .actualTypeArguments[0]
  return FullType(type)
}

@Suppress("unused")
class FullType<T> internal constructor(val type: Type)

open class TypeWrapper<T>

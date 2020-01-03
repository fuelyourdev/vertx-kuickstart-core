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

interface Deserializer {
  fun <T: Any> KClass<T>.instantiate(json: JsonObject?): T?
  fun FullParameter.instantiateList(arr: JsonArray?): List<Any?>?
  fun FullParameter.instantiateMap(obj: JsonObject?): Map<String, Any?>?
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
        Field::class -> type
          .instantiateField(json, name)
        List::class -> type
          .instantiateList(json.getJsonArray(name))
        Map::class -> type
          .instantiateMap(json.getJsonObject(name))
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
    throw VertxKuickstartException("$simpleName is missing a primary constructor")
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

  override fun FullParameter.instantiateList(arr: JsonArray?): List<Any?>? {
    return type.instantiateList(arr)
  }

  private fun Type.instantiateList(arr: JsonArray?): List<Any?>? {
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
        Field::class -> range.map { genericType.instantiateField(arr, it) }
        List::class -> range.map {
          genericType.instantiateList(arr.getJsonArray(it))
        }
        Map::class -> range.map {
          genericType.instantiateMap(arr.getJsonObject(it))
        }
        else -> range.map { itemsKClass.instantiate(arr.getJsonObject(it)) }
      }
    }
  }

  override fun FullParameter.instantiateMap(obj: JsonObject?): Map<String, Any?>? {
    return type.instantiateMap(obj)
  }

  private fun Type.instantiateMap(obj: JsonObject?): Map<String, Any?>? {
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
          else -> map[key] = itemsKClass.instantiate(obj.getJsonObject(key))
        }
      }
      map
    }
  }

  private fun Type?.instantiateField(
    arr: JsonArray,
    pos: Int
  ): Field<out Any?> {
    return this?.let { type ->
      val genericType = type.getGenericType(0)
      when (val itemsKClass = genericType.kClass) {
        ByteArray::class -> Field(arr.getBinary(pos), true)
        Boolean::class -> Field(arr.getBoolean(pos), true)
        Double::class -> Field(arr.getDouble(pos), true)
        Float::class -> Field(arr.getFloat(pos), true)
        Instant::class -> Field(arr.getInstant(pos), true)
        Int::class -> Field(arr.getInteger(pos), true)
        Long::class -> Field(arr.getLong(pos), true)
        String::class -> Field(arr.getString(pos), true)
        Field::class -> throw VertxKuickstartException(
          "Field of Field type not allowed")
        List::class -> Field(
          genericType.instantiateList(arr.getJsonArray(pos)),
          true
        )
        Map::class -> Field(
          genericType.instantiateMap(arr.getJsonObject(pos)),
          true
        )
        else -> Field(
          itemsKClass.instantiate(
            arr.getJsonObject(
              pos
            )
          ), true
        )
      }
    } ?: Field(arr.getValue(pos), true)
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
          "Field of Field type not allowed")
        List::class -> Field(
          genericType.instantiateList(json.getJsonArray(key)),
          json.containsKey(key)
        )
        Map::class -> Field(
          genericType.instantiateMap(json.getJsonObject(key)),
          json.containsKey(key)
        )
        else -> Field(
          itemsKClass.instantiate(json.getJsonObject(key)),
          json.containsKey(key)
        )
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
    }.let { Class.forName(it).kotlin }
}

inline fun <reified T> type(): Type {
  return object: TypeWrapper<T>() {}::class.java
    .let { it.genericSuperclass as ParameterizedType }
    .actualTypeArguments[0]
}

open class TypeWrapper<T>

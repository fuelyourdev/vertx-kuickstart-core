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
import kotlin.reflect.jvm.javaType

typealias ListType<T> = FullType<List<T>>
typealias MapType<T> = FullType<Map<String, T>>

interface Deserializer {
  fun <T: Any> FullType<T>.instantiate(json: JsonObject?): T?
  fun Type.instantiate(json: JsonObject?): Any?
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

  @Suppress("UNCHECKED_CAST")
  override fun <T: Any> FullType<T>.instantiate(json: JsonObject?): T? =
    type.instantiate(json) as T?

  override fun Type.instantiate(json: JsonObject?): Any? =
    instantiate(json, mapOf())

  fun Type.instantiate(
    json: JsonObject?,
    genericsMap: Map<String, Type>
  ): Any? {
    if (json == null) return null
    val kClass = resolveKClass(genericsMap)
    val classGenerics = kClass.java.typeParameters.mapIndexed { index, type ->
      type.name to let { it as ParameterizedType }.actualTypeArguments[index]
    }.toMap()
    val ctor = kClass.primaryConstructor
      ?: kClass.handleMissingPrimaryConstructor()
    val params = ctor.fullParameters.map { param ->
      val typeName = param.param.type.javaType.typeName
      if (typeName in classGenerics.keys) {
        param.instantiate(json, classGenerics, classGenerics[typeName])
      } else {
        param.instantiate(json, classGenerics)
      }
    }
    return ctor.call(*params.toTypedArray())
  }

  private fun FullParameter.instantiate(
    json: JsonObject,
    genericsMap: Map<String, Type>,
    typeOverride: Type? = null
  ): Any? {
    val resolvedType = typeOverride ?: type
    val kclass = type.resolveKClass(genericsMap)
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
        Field::class -> resolvedType.instantiateField(json, genericsMap, name)
        List::class ->
          resolvedType.instantiateList(json.getJsonArray(name), genericsMap)
        Map::class ->
          resolvedType.instantiateMap(json.getJsonObject(name), genericsMap)
        JsonObject::class -> json.getJsonObject(name)
        JsonArray::class -> json.getJsonArray(name)
        else ->
          if (kclass.isEnum)
            kclass.instantiateEnum(json.getString(name))
          else if (kclass.java.isArray)
            resolvedType.instantiateArray(json.getJsonArray(name), genericsMap)
          else
            resolvedType.instantiate(json.getJsonObject(name), genericsMap)
      }
      value.verifyOnlyNullIfParamAllows(this)
      value
    } catch (e: ClassCastException) {
      var value = json.getValue(name)
      if (value is String) {
        value = "\"$value\""
      }
      throw VertxKuickstartException(
        "${function.declaringClass.simpleName}.${name} " +
            "expects type ${kclass.simpleName} " +
            "but was given the value: $value", e)
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

  override fun Type.instantiateList(arr: JsonArray?): List<Any?>? =
    instantiateList(arr, mapOf())

  private fun Type.instantiateList(
    arr: JsonArray?,
    genericsMap: Map<String, Type>
  ): List<Any?>? {
    if (arr == null) return null
    val range = 0 until arr.size()
    return this.let { type ->
      val genericType = type.getGenericType(0)
      when (val itemsKClass = genericType.resolveKClass(genericsMap)) {
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
          genericType.instantiateList(arr.getJsonArray(it), genericsMap)
        }
        Map::class -> range.map {
          genericType.instantiateMap(arr.getJsonObject(it), genericsMap)
        }
        JsonObject::class -> range.map { arr.getJsonObject(it) }
        JsonArray::class -> range.map { arr.getJsonArray(it) }
        else ->
          if (itemsKClass.isEnum) {
            range.map { itemsKClass.instantiateEnum(arr.getString(it)) }
          } else if (itemsKClass.java.isArray) {
            range.map {
              genericType.instantiateArray(arr.getJsonArray(it), genericsMap)
            }
          } else {
            range.map {
              genericType.instantiate(arr.getJsonObject(it), genericsMap)
            }
          }
      }
    }
  }

  private fun Type.instantiateArray(
    arr: JsonArray?,
    genericsMap: Map<String, Type>
  ): Any? {
    if (arr == null) return null
    val range = 0 until arr.size()
    val kClass = resolveKClass(genericsMap)
    val arrType = kClass.java.componentType as Type
    val arrTypeKClass = arrType.resolveKClass(genericsMap)
    return when (kClass) {
      BooleanArray::class -> arr.list.map { it as Boolean }.toBooleanArray()
      DoubleArray::class -> arr.list.map { it as Double }.toDoubleArray()
      FloatArray::class -> arr.list.map { it as Float }.toFloatArray()
      IntArray::class -> arr.list.map { it as Int }.toIntArray()
      LongArray::class -> arr.list.map { it as Long }.toLongArray()
      else -> when (val itemsKClass = arrTypeKClass) {
        ByteArray::class -> range.map { arr.getBinary(it) }.toTypedArray()
        Boolean::class -> range.map { arr.getBoolean(it) }.toTypedArray()
        Double::class -> range.map { arr.getDouble(it) }.toTypedArray()
        Float::class -> range.map { arr.getFloat(it) }.toTypedArray()
        Instant::class -> range.map { arr.getInstant(it) }.toTypedArray()
        Int::class -> range.map { arr.getInteger(it) }.toTypedArray()
        Long::class -> range.map { arr.getLong(it) }.toTypedArray()
        String::class -> range.map { arr.getString(it) }.toTypedArray()
        Field::class -> throw VertxKuickstartException(
          "Array of Field type not allowed"
        )
        List::class -> range.map {
          arrType.instantiateList(arr.getJsonArray(it), genericsMap)
        }.toTypedArray()
        Map::class -> range.map {
          arrType.instantiateMap(arr.getJsonObject(it), genericsMap)
        }.toTypedArray()
        JsonObject::class -> range.map { arr.getJsonObject(it) }.toTypedArray()
        JsonArray::class -> range.map { arr.getJsonArray(it) }.toTypedArray()
        else ->
          if (itemsKClass.isEnum) {
            range.map { itemsKClass.instantiateEnum(arr.getString(it)) }
              .toTypedArray()
          } else if (itemsKClass.java.isArray) {
            range.map {
              arrType.instantiateArray(arr.getJsonArray(it), genericsMap)
            }.toTypedArray()
          } else {
            range.map {
              arrType.instantiate(arr.getJsonObject(it), genericsMap)
            }.toTypedArray()
          }
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T: Any> MapType<T>.instantiateMap(
    obj: JsonObject?
  ): Map<String, T?>? =
    type.instantiateMap(obj) as Map<String, T?>?

  override fun Type.instantiateMap(obj: JsonObject?): Map<String, Any?>? =
    instantiateMap(obj, mapOf())

  private fun Type.instantiateMap(
    obj: JsonObject?,
    genericsMap: Map<String, Type>
  ): Map<String, Any?>? {
    if (obj == null) return null
    return this.let { type ->
      val genericType = type.getGenericType(1)
      val itemsKClass = genericType.resolveKClass(genericsMap)
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
            genericType.instantiateField(obj, genericsMap, key)
          List::class -> map[key] =
            genericType.instantiateList(obj.getJsonArray(key), genericsMap)
          Map::class -> map[key] =
            genericType.instantiateMap(obj.getJsonObject(key), genericsMap)
          JsonObject::class -> map[key] = obj.getJsonObject(key)
          JsonArray::class -> map[key] = obj.getJsonArray(key)
          else ->
            if (itemsKClass.isEnum) {
              map[key] = itemsKClass.instantiateEnum(obj.getString(key))
            } else if (itemsKClass.java.isArray) {
              map[key] = genericType
                .instantiateArray(obj.getJsonArray(key), genericsMap)
            } else {
              map[key] = genericType
                .instantiate(obj.getJsonObject(key), genericsMap)
            }
        }
      }
      map
    }
  }

  private fun Type.instantiateField(
    json: JsonObject,
    genericsMap: Map<String, Type>,
    key: String
  ): Field<out Any?> {
    return this.let { type ->
      val genericType = type.getGenericType(0)
      when (val itemsKClass = genericType.resolveKClass(genericsMap)) {
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
          genericType.instantiateList(json.getJsonArray(key), genericsMap),
          json.containsKey(key)
        )
        Map::class -> Field(
          genericType.instantiateMap(json.getJsonObject(key), genericsMap),
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
          } else if (itemsKClass.java.isArray) {
            Field(
              genericType.instantiateArray(json.getJsonArray(key), genericsMap),
              json.containsKey(key)
            )
          } else {
            Field(
              genericType.instantiate(json.getJsonObject(key), genericsMap),
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

  private fun Type.resolveKClass(
    genericsMap: Map<String, Type> = mapOf()
  ): KClass<*> =
    baseTypeName.let { when (it) {
      "byte[]" -> ByteArray::class
      "boolean[]" -> BooleanArray::class
      "double[]" -> DoubleArray::class
      "float[]" -> FloatArray::class
      "int[]" -> IntArray::class
      "long[]" -> LongArray::class
      "boolean" -> Boolean::class
      "double" -> Double::class
      "float" -> Float::class
      "int" -> Int::class
      "long" -> Long::class
      else ->
        if (it.endsWith("[]")) {
          val name = it.substring(0, it.length - 2)
          if (name in genericsMap.keys) {
            val resolvedName = genericsMap[name]?.baseTypeName
            Class.forName("[L$resolvedName;").kotlin
          } else {
            Class.forName("[L$name;").kotlin
          }
        } else {
          if (it in genericsMap.keys) {
            val name = genericsMap[it]?.baseTypeName
            Class.forName(name).kotlin
          } else {
            Class.forName(it).kotlin
          }
        }
    } }

  private val Type.baseTypeName: String
    get() = when(this) {
      is ParameterizedType -> rawType.typeName
      else -> typeName
    }
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

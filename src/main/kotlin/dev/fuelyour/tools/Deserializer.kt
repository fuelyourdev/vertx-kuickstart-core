package dev.fuelyour.tools

import dev.fuelyour.exceptions.VertxKuickstartException
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.lang.ClassCastException
import java.lang.NumberFormatException
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod

/**
 * Auto deserialize json to the type given.
 */
interface Deserializer {
  fun <T: Any> FullType<T>.instantiate(json: JsonObject?): T?
  fun Type.instantiate(json: JsonObject?): Any?
  fun <T: Any> FullType<T>.instantiate(arr: JsonArray?): T?
  fun Type.instantiate(arr: JsonArray?): Any?
}

class DeserializerImpl: Deserializer {

  @Suppress("UNCHECKED_CAST")
  override fun <T:Any> FullType<T>.instantiate(json: JsonObject?): T? =
    type.instantiate(json) as T?

  override fun Type.instantiate(json: JsonObject?): Any? =
    when (resolveKClass(mapOf())) {
      Map::class -> instantiateMap(json)
      JsonObject::class -> json
      else -> instantiateObject(json)
    }

  @Suppress("UNCHECKED_CAST")
  override fun <T:Any> FullType<T>.instantiate(arr: JsonArray?): T? =
    type.instantiate(arr) as T?

  override fun Type.instantiate(arr: JsonArray?): Any? =
    when (resolveKClass(mapOf())) {
      List::class -> instantiateList(arr)
      JsonArray::class -> arr
      else -> instantiateArray(arr)
    }

  private val KFunction<*>.declaringClass: Class<*>
    get() =
      javaConstructor?.declaringClass
        ?: javaMethod?.declaringClass
        ?: throw VertxKuickstartException(
          "Unable to find Java reflection info for $this")

  private fun Type.instantiateObject(json: JsonObject?): Any? =
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
      param.instantiate(json, classGenerics)
    }
    return ctor.call(*params.toTypedArray())
  }


  private fun Type.resolve(genericsMap: Map<String, Type>): Type {
    val generic = genericsMap[typeName]
    return generic ?: this
  }

  private fun FullParameter.instantiate(
    json: JsonObject,
    genericsMap: Map<String, Type>
  ): Any? {
    val resolvedType = type.resolve(genericsMap)
    val kclass = resolvedType.resolveKClass(genericsMap)
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
          when {
            kclass.isEnum -> kclass.instantiateEnum(json.getString(name))
            kclass.java.isArray -> resolvedType.instantiateArray(
              json.getJsonArray(name),
              genericsMap
            )
            else -> resolvedType.instantiate(
              json.getJsonObject(name),
              genericsMap
            )
          }
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

  private fun Type.instantiateList(arr: JsonArray?): List<Any?>? =
    instantiateList(arr, mapOf())

  private fun Type.instantiateList(
    arr: JsonArray?,
    genericsMap: Map<String, Type>
  ): List<Any?>? {
    if (arr == null) return null
    val range = 0 until arr.size()
    return this.let { type ->
      val genericType = type.getGenericType(0)
      val resolvedType = genericType.resolve(genericsMap)
      when (val itemsKClass = resolvedType.resolveKClass(genericsMap)) {
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
          resolvedType.instantiateList(arr.getJsonArray(it), genericsMap)
        }
        Map::class -> range.map {
          resolvedType.instantiateMap(arr.getJsonObject(it), genericsMap)
        }
        JsonObject::class -> range.map { arr.getJsonObject(it) }
        JsonArray::class -> range.map { arr.getJsonArray(it) }
        else ->
          when {
            itemsKClass.isEnum -> {
              range.map { itemsKClass.instantiateEnum(arr.getString(it)) }
            }
            itemsKClass.java.isArray -> {
              range.map {
                resolvedType.instantiateArray(arr.getJsonArray(it), genericsMap)
              }
            }
            else -> {
              range.map {
                resolvedType.instantiate(arr.getJsonObject(it), genericsMap)
              }
            }
          }
      }
    }
  }

  private fun Type.instantiateArray(arr: JsonArray?): Any? =
    instantiateArray(arr, mapOf())

  private fun Type.instantiateArray(
    arr: JsonArray?,
    genericsMap: Map<String, Type>
  ): Any? {
    if (arr == null) return null
    val range = 0 until arr.size()
    val kClass = resolveKClass(genericsMap)
    val arrType = if (this is GenericArrayType)
      this.genericComponentType
    else
      kClass.java.componentType as Type
    val resolvedArrType = arrType.resolve(genericsMap)
    val arrTypeKClass = resolvedArrType.resolveKClass(genericsMap)
    return when (kClass) {
      BooleanArray::class -> arr.list.map { it as Boolean }.toBooleanArray()
      DoubleArray::class -> arr.list.map { it as Double }.toDoubleArray()
      FloatArray::class -> arr.list.map { it as Float }.toFloatArray()
      IntArray::class -> arr.list.map { it as Int }.toIntArray()
      LongArray::class -> arr.list.map { it as Long }.toLongArray()
      else -> when (arrTypeKClass) {
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
        List::class -> loadArray(arrTypeKClass, arr) { i ->
          resolvedArrType.instantiateList(arr.getJsonArray(i), genericsMap)
        }
        Map::class -> loadArray(arrTypeKClass, arr) {
          resolvedArrType.instantiateMap(arr.getJsonObject(it), genericsMap)
        }
        JsonObject::class -> range.map { arr.getJsonObject(it) }.toTypedArray()
        JsonArray::class -> range.map { arr.getJsonArray(it) }.toTypedArray()
        else ->
          when {
            arrTypeKClass.isEnum -> {
              range.map { arrTypeKClass.instantiateEnum(arr.getString(it)) }
                .toTypedArray()
            }
            arrTypeKClass.java.isArray -> {
              loadArray(arrTypeKClass, arr) { i ->
                resolvedArrType.instantiateArray(
                  arr.getJsonArray(i),
                  genericsMap
                )
              }
            }
            else -> {
              loadArray(arrTypeKClass, arr) {
                resolvedArrType.instantiate(arr.getJsonObject(it), genericsMap)
              }
            }
          }
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T:Any> loadArray(
    kclass: KClass<T>,
    arr: JsonArray,
    func: (Int) -> Any?
  ): Array<T> {
    val outerArray =
      java.lang.reflect.Array.newInstance(kclass.java, arr.size())
    for (i in 0 until arr.size()) {
      java.lang.reflect.Array.set(outerArray, i, func(i))
    }
    return outerArray as Array<T>
  }

  private fun Type.instantiateMap(obj: JsonObject?): Map<Any, Any?>? =
    instantiateMap(obj, mapOf())

  private fun Type.instantiateMap(
    obj: JsonObject?,
    genericsMap: Map<String, Type>
  ): Map<Any, Any?>? {
    if (obj == null) return null
    return this.let { type ->
      val keyGenericType = type.getGenericType(0)
      val keyResolvedType = keyGenericType.resolve(genericsMap)
      val keyKClass = keyResolvedType.resolveKClass(genericsMap)
      val keyTransformer: (String) -> Any = when (keyKClass) {
        String::class -> { key -> key }
        Int::class -> { key ->
          try {
            key.toInt()
          } catch (e: NumberFormatException) {
            throw VertxKuickstartException(
              "Cannot convert key value \"$key\" to Int",
              e
            )
          }
        }
        Long::class -> { key ->
          try {
            key.toLong()
          } catch (e: NumberFormatException) {
            throw VertxKuickstartException(
              "Cannot convert key value \"$key\" to Long",
              e
            )
          }
        }
        else -> throw VertxKuickstartException(
          "Unsupported key type for map: $keyResolvedType"
        )
      }

      val genericType = type.getGenericType(1)
      val resolvedType = genericType.resolve(genericsMap)
      val itemsKClass = resolvedType.resolveKClass(genericsMap)
      val map = mutableMapOf<Any, Any?>()
      obj.forEach { (key, _) ->
        when (itemsKClass) {
          ByteArray::class -> map[keyTransformer(key)] = obj.getBinary(key)
          Boolean::class -> map[keyTransformer(key)] = obj.getBoolean(key)
          Double::class -> map[keyTransformer(key)] = obj.getDouble(key)
          Float::class -> map[keyTransformer(key)] = obj.getFloat(key)
          Instant::class -> map[keyTransformer(key)] = obj.getInstant(key)
          Int::class -> map[keyTransformer(key)] = obj.getInteger(key)
          Long::class -> map[keyTransformer(key)] = obj.getLong(key)
          String::class -> map[keyTransformer(key)] = obj.getString(key)
          Field::class -> map[keyTransformer(key)] =
            resolvedType.instantiateField(obj, genericsMap, key)
          List::class -> map[keyTransformer(key)] =
            resolvedType.instantiateList(obj.getJsonArray(key), genericsMap)
          Map::class -> map[keyTransformer(key)] =
            resolvedType.instantiateMap(obj.getJsonObject(key), genericsMap)
          JsonObject::class -> map[keyTransformer(key)] = obj.getJsonObject(key)
          JsonArray::class -> map[keyTransformer(key)] = obj.getJsonArray(key)
          else ->
            when {
              itemsKClass.isEnum -> {
                map[keyTransformer(key)] =
                  itemsKClass.instantiateEnum(obj.getString(key))
              }
              itemsKClass.java.isArray -> {
                map[keyTransformer(key)] = resolvedType
                  .instantiateArray(obj.getJsonArray(key), genericsMap)
              }
              else -> {
                map[keyTransformer(key)] = resolvedType
                  .instantiate(obj.getJsonObject(key), genericsMap)
              }
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
      val resolvedType = genericType.resolve(genericsMap)
      when (val itemsKClass = resolvedType.resolveKClass(genericsMap)) {
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
          resolvedType.instantiateList(json.getJsonArray(key), genericsMap),
          json.containsKey(key)
        )
        Map::class -> Field(
          resolvedType.instantiateMap(json.getJsonObject(key), genericsMap),
          json.containsKey(key)
        )
        JsonObject::class -> Field(
          json.getJsonObject(key),
          json.containsKey(key)
        )
        JsonArray::class -> Field(json.getJsonArray(key), json.containsKey(key))
        else ->
          when {
            itemsKClass.isEnum -> {
              Field(
                itemsKClass.instantiateEnum(json.getString(key)),
                json.containsKey(key)
              )
            }
            itemsKClass.java.isArray -> {
              Field(
                resolvedType.instantiateArray(
                  json.getJsonArray(key),
                  genericsMap
                ),
                json.containsKey(key)
              )
            }
            else -> {
              Field(
                resolvedType.instantiate(json.getJsonObject(key), genericsMap),
                json.containsKey(key)
              )
            }
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
    baseTypeName.let {
      when (it) {
        "boolean" -> Boolean::class
        "double" -> Double::class
        "float" -> Float::class
        "int" -> Int::class
        "long" -> Long::class
        else -> {
          val name = determineClassName(it, genericsMap)
          Class.forName(name).kotlin
        }
      }
    }

  private fun determineClassName(
    typeName: String,
    genericsMap: Map<String, Type>
  ): String {
    return if (typeName.endsWith("[]")) {
      val endIndex = typeName.indexOf('[')
      val name = typeName.substring(0, endIndex)
      var nestingLevel = (typeName.length - endIndex) / 2
      val resolvedName = when (name) {
        "boolean" -> "Z"
        "byte" -> "B"
        "double" -> "D"
        "float" -> "F"
        "int" -> "I"
        "long" -> "J"
        else -> {
          val generic = genericsMap[name]
          if (generic != null) {
            val tempName = determineClassName(generic.baseTypeName, genericsMap)
            if (tempName.startsWith('[')) {
              if (tempName.endsWith(';')) {
                val indexOfL = tempName.indexOf('L')
                nestingLevel += indexOfL
                "L${tempName.substring(indexOfL + 1, tempName.length - 1)};"
              } else {
                val startIndex = tempName.lastIndexOf('[') + 1
                nestingLevel += startIndex
                tempName.substring(startIndex)
              }
            } else {
              "L$tempName;"
            }
          } else if (name.contains('<'))
            "L${name.substring(0, name.indexOf('<'))};"
          else
            "L$name;"
        }
      }
      val arrStr =
        (1..nestingLevel).joinToString(separator = "") { "[" }
      "$arrStr$resolvedName"
    } else {
      val generic = genericsMap[typeName]
      if (generic != null) {
        val name = generic.baseTypeName
        name
      } else {
        typeName
      }
    }
  }

  private val Type.baseTypeName: String
    get() = when(this) {
      is ParameterizedType -> rawType.typeName
      else -> typeName
    }
}

/**
 * Get the type of the class
 */
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

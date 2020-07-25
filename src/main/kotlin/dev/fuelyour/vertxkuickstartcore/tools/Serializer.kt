package dev.fuelyour.vertxkuickstartcore.tools

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.lang.reflect.Modifier
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaGetter

data class Field<T>(
    val value: T?,
    val present: Boolean
)

fun List<*>.serialize(includePrivate: Boolean = false): JsonArray {
    val arr = JsonArray()
    forEach { item ->
        when (item) {
            is ByteArray -> arr.add(item)
            is Boolean -> arr.add(item)
            is Double -> arr.add(item)
            is Float -> arr.add(item)
            is Instant -> arr.add(item)
            is Int -> arr.add(item)
            is Long -> arr.add(item)
            is String -> arr.add(item)
            is UUID -> arr.add(item.toString())
            is List<*> -> arr.add(item.serialize(includePrivate))
            is Map<*, *> -> arr.add(item.serialize(includePrivate))
            is Field<*> -> arr.add(item.serialize(includePrivate))
            null -> arr.add(null as Any?)
            else -> if (item::class.java.isEnum) {
                arr.add((item as Enum<*>).name)
            } else {
                arr.add(item.serialize(includePrivate))
            }
        }
    }
    return arr
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> T.serialize(includePrivate: Boolean = false): JsonObject {
    val json = JsonObject()
    this::class.declaredMemberProperties.forEach { prop ->
        if (includePrivate || prop.isPublic()) {
            if (includePrivate)
                prop.isAccessible = true
            val key = prop.name
            when (val value = prop.getter.call(this)) {
                is ByteArray -> json.put(key, value)
                is Boolean -> json.put(key, value)
                is Double -> json.put(key, value)
                is Float -> json.put(key, value)
                is Instant -> json.put(key, value)
                is Int -> json.put(key, value)
                is Long -> json.put(key, value)
                is String -> json.put(key, value)
                is UUID -> json.put(key, value.toString())
                is List<*> -> json.put(key, value.serialize(includePrivate))
                is Map<*, *> -> json.put(key, value.serialize(includePrivate))
                is Field<*> -> if (value.present) json.put(
                    key,
                    value.serialize(includePrivate)
                )
                else -> if (value != null) {
                    if (value::class.java.isEnum) {
                        json.put(key, (value as Enum<*>).name)
                    } else {
                        json.put(key, value.serialize(includePrivate))
                    }
                }
            }
        }
    }
    return json
}

fun Map<*, *>.serialize(includePrivate: Boolean = false): JsonObject {
    val json = JsonObject()
    forEach { (keyObj, value) ->
        val key = keyObj.toString()
        when (value) {
            is ByteArray -> json.put(key, value)
            is Boolean -> json.put(key, value)
            is Double -> json.put(key, value)
            is Float -> json.put(key, value)
            is Instant -> json.put(key, value)
            is Int -> json.put(key, value)
            is Long -> json.put(key, value)
            is String -> json.put(key, value)
            is UUID -> json.put(key, value.toString())
            is List<*> -> json.put(key, value.serialize(includePrivate))
            is Map<*, *> -> json.put(key, value.serialize(includePrivate))
            is Field<*> -> if (value.present) json.put(
                key,
                value.serialize(includePrivate)
            )
            else -> if (value != null) {
                if (value::class.java.isEnum) {
                    json.put(key, (value as Enum<*>).name)
                } else {
                    json.put(key, value.serialize(includePrivate))
                }
            }
        }
    }
    return json
}

private fun <T : Any> KProperty1<out T, *>.isPublic() =
    javaGetter?.let {
            getter -> Modifier.isPublic(getter.modifiers)
    } ?: false

private fun Field<*>.serialize(includePrivate: Boolean): Any? =
    when (value) {
        is ByteArray,
        is Boolean,
        is Double,
        is Float,
        is Instant,
        is Int,
        is Long,
        is String -> value
        is UUID -> value.toString()
        is List<*> -> value.serialize(includePrivate)
        is Map<*, *> -> value.serialize(includePrivate)
        is Field<*> -> throw Exception("Field of field not allowed")
        null -> null
        else -> if (value::class.java.isEnum) {
            (value as Enum<*>).name
        } else {
            value.serialize(includePrivate)
        }
    }

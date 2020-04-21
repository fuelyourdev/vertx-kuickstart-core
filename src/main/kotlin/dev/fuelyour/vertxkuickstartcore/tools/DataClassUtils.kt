package dev.fuelyour.vertxkuickstartcore.tools

import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties

/**
 * Helper function for combining an object and a patch, such that patch fields override the fields in the object if
 * they are present, otherwise the fields from the object come through. Note that the object and patch remain unchanged,
 * and a new object is created and returned.
 */
@Suppress("UNCHECKED_CAST")
fun <T:Any> T.applyPatch(patch: Any): T {
  if (this::class.isData) {
    val copy = this::class.declaredFunctions.first { it.name == "copy" }
    val args = patch::class.declaredMemberProperties
      .filter { copy.parameters.any { param -> param.name == it.name } }
      .map {
        copy.parameters.first { param -> param.name == it.name } to
            (it as KProperty1<Any, Any?>).get(patch)
      }
      .filter { with(it.second) {
        (this is Field<*> && present) || (this !is Field<*> && this != null)
      } }
      .map { it.first to with(it.second) {
        if (this is Field<*>) value else this
      } }
      .toMap() + mapOf(copy.parameters[0] to this)
    return copy.callBy(args) as T
  } else {
    throw Exception("Must be a data class")
  }
}

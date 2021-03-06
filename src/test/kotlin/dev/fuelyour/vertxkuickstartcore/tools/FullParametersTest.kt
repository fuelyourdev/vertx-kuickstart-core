@file:Suppress("UNUSED_PARAMETER", "unused")

package dev.fuelyour.vertxkuickstartcore.tools

import dev.fuelyour.vertxkuickstartcore.exceptions.VertxKuickstartException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import java.lang.reflect.ParameterizedType
import kotlin.reflect.full.declaredFunctions

fun topLevelFunction(param1: Long, param2: Float) {}
fun topLevelListFunction(param1: List<Long>, param2: List<Float>) {}

fun String.extensionFunction(param1: Int) {}
fun List<String>.extensionListFunction(param1: List<Int>) {}

class TestClass(val param1: String, val param2: Int) {
    companion object {
        fun companionFunction(param1: Short) {}
    }

    fun classFunction(param1: Double, param2: Boolean) {}
}

class TestListClass(val param1: List<String>, val param2: List<Int>) {
    companion object {
        fun companionListFunction(param1: List<Short>) {}
    }

    fun classListFunction(param1: List<Double>, param2: List<Boolean>) {}
}

object Singleton {
    fun singletonFunction(param1: Char, param2: Byte) {}
}

object ListSingleton {
    fun singletonListFunction(param1: List<Char>, param2: List<Byte>) {}
}

class FullParametersTest : StringSpec({

    val kclass = TestClass::class
    val ctor = kclass.constructors.first()
    val classFun = kclass.declaredFunctions.first()
    val topLevelFun = ::topLevelFunction
    val companionFun = TestClass.Companion::companionFunction
    val singletonFun = Singleton::singletonFunction
    val extensionFun = String::extensionFunction

    "fullParameters works for all types of constructors and functions" {
        forAll(
            row(ctor, 0, String::class.java),
            row(ctor, 1, Int::class.java),

            // For class functions, the first parameter is an instance
            // of the class
            row(classFun, 0, TestClass::class.java),
            row(classFun, 1, Double::class.java),
            row(classFun, 2, Boolean::class.java),

            // For extension functions, the first parameter is an instance
            // of the class being extended
            row(extensionFun, 0, String::class.java),
            row(extensionFun, 1, Int::class.java),

            row(topLevelFun, 0, Long::class.java),
            row(topLevelFun, 1, Float::class.java),

            row(companionFun, 0, Short::class.java),

            row(singletonFun, 0, Char::class.java),
            row(singletonFun, 1, Byte::class.java)
        ) { method, paramIndex, type ->
            val fullParam = method.fullParameters[paramIndex]
            fullParam.function shouldBe method
            fullParam.param shouldBe method.parameters[paramIndex]
            fullParam.type shouldBe type
        }
    }

    "FullParameter.name returns the name of the parameter, if available" {
        forAll(
            row(ctor, 0, "param1"),
            row(ctor, 1, "param2"),

            // For class functions, the first parameter is an instance
            // of the class
            row(classFun, 1, "param1"),
            row(classFun, 2, "param2"),

            // For extension functions, the first parameter is an instance
            // of the class being extended
            row(extensionFun, 1, "param1"),

            row(topLevelFun, 0, "param1"),
            row(topLevelFun, 1, "param2"),

            row(companionFun, 0, "param1"),

            row(singletonFun, 0, "param1"),
            row(singletonFun, 1, "param2")
        ) { method, paramIndex, paramName ->
            method.fullParameters[paramIndex].name shouldBe paramName
        }
    }

    "FullParameter.name throws an exception, if unavailable" {
        forAll(
            // For class functions, the first parameter is an instance
            // of the class
            row(classFun, 0),

            // For extension functions, the first parameter is an instance
            // of the class being extended
            row(extensionFun, 0)
        ) { method, paramIndex ->
            val exception = shouldThrow<VertxKuickstartException> {
                method.fullParameters[paramIndex].name
            }
            exception.message shouldBe
                "Parameter names for the function ${method.name} are missing"
        }
    }

    "fullParameters throws IndexOutOfBoundsException" {
        forAll(
            row(ctor, ctor.parameters.size),
            row(classFun, classFun.parameters.size),
            row(extensionFun, extensionFun.parameters.size),
            row(topLevelFun, topLevelFun.parameters.size),
            row(companionFun, companionFun.parameters.size),
            row(singletonFun, singletonFun.parameters.size)
        ) { method, paramIndex ->
            shouldThrow<IndexOutOfBoundsException> {
                method.fullParameters[paramIndex]
            }
        }
    }

    val listKclass = TestListClass::class
    val listCtor = listKclass.constructors.first()
    val listClassFun = listKclass.declaredFunctions.first()
    val listTopLevelFun = ::topLevelListFunction
    val listCompanionFun = TestListClass.Companion::companionListFunction
    val listSingletonFun = ListSingleton::singletonListFunction
    val listExtensionFun = List<String>::extensionListFunction

    "fullParameters can get the type of lists" {
        forAll(
            row(listCtor, 0, String::class.java),
            row(listCtor, 1, Int::class.javaObjectType),

            // For class functions, the first parameter is an instance
            // of the class
            row(listClassFun, 1, Double::class.javaObjectType),
            row(listClassFun, 2, Boolean::class.javaObjectType),

            // For extension functions, the first parameter is an instance
            // of the class being extended
            row(listExtensionFun, 0, String::class.java),
            row(listExtensionFun, 1, Int::class.javaObjectType),

            row(listTopLevelFun, 0, Long::class.javaObjectType),
            row(listTopLevelFun, 1, Float::class.javaObjectType),

            row(listCompanionFun, 0, Short::class.javaObjectType),

            row(listSingletonFun, 0, Char::class.javaObjectType),
            row(listSingletonFun, 1, Byte::class.javaObjectType)
        ) { method, paramIndex, listType ->
            val type =
                method.fullParameters[paramIndex].type as ParameterizedType
            type.rawType shouldBe List::class.java
            type.actualTypeArguments[0] shouldBe listType
        }
    }

    "FullParameter.kclass will get the KClass" {
        forAll(
            row(ctor, 0, String::class),
            row(ctor, 1, Int::class),

            // For class functions, the first parameter is an instance
            // of the class
            row(classFun, 0, TestClass::class),
            row(classFun, 1, Double::class),
            row(classFun, 2, Boolean::class),

            // For extension functions, the first parameter is an instance
            // of the class being extended
            row(extensionFun, 0, String::class),
            row(extensionFun, 1, Int::class),

            row(topLevelFun, 0, Long::class),
            row(topLevelFun, 1, Float::class),

            row(companionFun, 0, Short::class),

            row(singletonFun, 0, Char::class),
            row(singletonFun, 1, Byte::class),

            row(listCtor, 0, List::class),
            row(listCtor, 1, List::class),

            // For class functions, the first parameter is an instance
            // of the class
            row(listClassFun, 1, List::class),
            row(listClassFun, 2, List::class),

            // For extension functions, the first parameter is an instance
            // of the class being extended
            row(listExtensionFun, 0, List::class),
            row(listExtensionFun, 1, List::class),

            row(listTopLevelFun, 0, List::class),
            row(listTopLevelFun, 1, List::class),

            row(listCompanionFun, 0, List::class),

            row(listSingletonFun, 0, List::class),
            row(listSingletonFun, 1, List::class)
        ) { method, paramIndex, kclass ->
            method.fullParameters[paramIndex].kclass shouldBe kclass
        }
    }
})

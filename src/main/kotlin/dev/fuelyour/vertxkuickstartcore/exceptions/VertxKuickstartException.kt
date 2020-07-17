package dev.fuelyour.vertxkuickstartcore.exceptions

import java.lang.RuntimeException

/**
 * Base exception for vertx-quickstart-core libraries to inform users of the core libraries of misuse of the core
 * libraries.
 */
class VertxKuickstartException : RuntimeException {

    override val message: String
        get() = super.message ?: ""

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)
}

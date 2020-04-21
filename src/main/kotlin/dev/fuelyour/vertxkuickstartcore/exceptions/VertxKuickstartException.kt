package dev.fuelyour.vertxkuickstartcore.exceptions

/**
 * Base exception for vertx-quickstart-core libraries to inform users of the core libraries of misuse of the core
 * libraries.
 */
class VertxKuickstartException: Exception {

  constructor(message: String): super(message)

  constructor(message: String, cause: Throwable): super(message, cause)
}
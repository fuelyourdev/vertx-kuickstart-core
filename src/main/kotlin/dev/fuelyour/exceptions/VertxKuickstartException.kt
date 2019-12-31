package dev.fuelyour.exceptions

class VertxKuickstartException: Exception {

  constructor(message: String): super(message)

  constructor(message: String, cause: Throwable): super(message, cause)
}
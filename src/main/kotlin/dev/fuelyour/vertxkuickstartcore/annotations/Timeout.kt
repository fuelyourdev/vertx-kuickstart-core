package dev.fuelyour.vertxkuickstartcore.annotations

@Target(AnnotationTarget.FUNCTION)
annotation class Timeout(val length: Long = 30000)

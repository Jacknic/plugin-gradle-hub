package com.github.jacknic.plugin.gradlehub.service

/**
 * Gradle DSL type constants.
 *
 * Defined as string constants instead of an enum to avoid runtime class loading issues
 * in the IntelliJ test framework environment where kotlin-stdlib is not bundled.
 */
object DslType {
    const val GROOVY = "GROOVY"
    const val KOTLIN = "KOTLIN"
}

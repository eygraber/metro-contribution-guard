package com.eygraber.metro.guard

import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

internal val KotlinCompilation<*>.isProductionCompilation: Boolean
  get() = when(platformType) {
    KotlinPlatformType.androidJvm -> !name.endsWith("Test")
    KotlinPlatformType.common,
    KotlinPlatformType.jvm,
    KotlinPlatformType.js,
    KotlinPlatformType.native,
    KotlinPlatformType.wasm,
    -> name == KotlinCompilation.MAIN_COMPILATION_NAME
  }

@Suppress("BooleanPropertyNaming")
internal val KotlinTarget.producesJvmClasses: Boolean
  get() = platformType == KotlinPlatformType.jvm || platformType == KotlinPlatformType.androidJvm

internal fun String.capitalizedName(): String = replaceFirstChar { it.uppercaseChar() }

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

// The `kotlin-dsl` plugin pins the Kotlin Gradle plugin on this project's buildscript classpath to
// Gradle's embedded Kotlin version, which lags behind the version we run. Align it with the project's
// Kotlin version so this build script compiles against the same API the plugin uses at runtime
// (the `abiValidation` DSL below only exists in the newer API).
buildscript {
  configurations.classpath {
    resolutionStrategy.eachDependency {
      if(requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-gradle-plugin")) {
        useVersion(libs.versions.kotlin.get())
      }
    }
  }
}

plugins {
  `kotlin-dsl`
  alias(libs.plugins.conventionsKotlin)
  alias(libs.plugins.conventionsDetekt)
  alias(libs.plugins.conventionsPublishToMavenCentral)
  alias(libs.plugins.dependencyAnalysis)
}

kotlin {
  @OptIn(ExperimentalAbiValidation::class)
  abiValidation()
}

gradlePlugin {
  plugins {
    create("metroContributionIndex") {
      id = "com.eygraber.metro-contribution-index"
      implementationClass = "com.eygraber.metro.guard.MetroContributionIndexPlugin"
      displayName = "Metro Contribution Index Plugin"
      description = "Generates and publishes an index of a module's Metro contributions."
    }

    create("metroAggregationValidation") {
      id = "com.eygraber.metro-aggregation-validation"
      implementationClass = "com.eygraber.metro.guard.MetroAggregationValidationPlugin"
      displayName = "Metro Aggregation Validation Plugin"
      description = "Validates that every module declaring Metro contributions is visible to a Metro aggregation root."
    }
  }
}

// See https://github.com/gradle/gradle/issues/22466
val additionalPluginClasspath = configurations.register("additionalPluginClasspath")

dependencies {
  compileOnly(libs.buildscript.android)
  compileOnly(libs.buildscript.kotlin)

  implementation(libs.asm)

  testImplementation(gradleTestKit())
  testImplementation(libs.test.junit)
  testImplementation(libs.test.kotest.assertions.core)

  additionalPluginClasspath(libs.buildscript.android)
  additionalPluginClasspath(libs.buildscript.kotlin)
}

tasks {
  pluginUnderTestMetadata {
    pluginClasspath.from(additionalPluginClasspath)
  }
}

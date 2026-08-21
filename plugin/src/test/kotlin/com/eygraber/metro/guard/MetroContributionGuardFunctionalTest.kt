package com.eygraber.metro.guard

import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MetroContributionGuardFunctionalTest {
  @get:Rule
  val projectDir = TemporaryFolder()

  @Test
  fun `a disconnected contributing module fails validation unless it is excluded`() {
    writeTestBuild()

    val failure = runner(":app:validateMetroAggregationDependencies").buildAndFail()

    failure.output shouldContain ":contributed"
    failure.output shouldContain
      "@ContributesBinding(scope = com.example.contributed.AppScope) on com.example.contributed.ContributedImpl"

    val success = runner(":app:validateMetroAggregationDependencies", "-Pexclude=true").build()

    success.output shouldContain "BUILD SUCCESSFUL"
    success.output shouldContain "Isolated Projects is an incubating feature"
  }

  private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
    .withProjectDir(projectDir.root)
    .withPluginClasspath()
    .withArguments(*arguments, "--stacktrace")

  private fun writeTestBuild() {
    file(
      "gradle.properties",
      """
      org.gradle.isolated-projects=true
      """.trimIndent(),
    )

    file(
      "settings.gradle.kts",
      """
      dependencyResolutionManagement {
        repositories {
          mavenCentral()
        }
      }

      rootProject.name = "functional-test"

      include(":app", ":connected", ":contributed")
      """.trimIndent(),
    )

    file(
      "app/build.gradle.kts",
      """
      plugins {
        kotlin("jvm")
        id("com.eygraber.metro-aggregation-validation")
      }

      metroAggregationValidation {
        if(providers.gradleProperty("exclude").isPresent) {
          excludeProject(":contributed", "m*")
        }
      }

      dependencies {
        implementation(project(":connected"))
      }
      """.trimIndent(),
    )

    file(
      "connected/build.gradle.kts",
      """
      plugins {
        kotlin("jvm")
        id("com.eygraber.metro-contribution-index")
      }
      """.trimIndent(),
    )

    file(
      "connected/src/main/kotlin/com/example/connected/Connected.kt",
      """
      package com.example.connected

      class Connected
      """.trimIndent(),
    )

    file(
      "contributed/build.gradle.kts",
      """
      plugins {
        kotlin("jvm")
        id("com.eygraber.metro-contribution-index")
      }
      """.trimIndent(),
    )

    file(
      "contributed/src/main/kotlin/dev/zacsweers/metro/ContributesBinding.kt",
      """
      package dev.zacsweers.metro

      import kotlin.reflect.KClass

      @Retention(AnnotationRetention.BINARY)
      @Target(AnnotationTarget.CLASS)
      annotation class ContributesBinding(val scope: KClass<*>)
      """.trimIndent(),
    )

    file(
      "contributed/src/main/kotlin/metro/hints/Hints.kt",
      """
      @file:Suppress("unused", "FunctionName", "UNUSED_PARAMETER")

      package metro.hints

      import com.example.contributed.ContributedImpl

      fun com_example_contributed_AppScope(contributed: ContributedImpl) {}
      """.trimIndent(),
    )

    file(
      "contributed/src/main/kotlin/com/example/contributed/Contributed.kt",
      """
      package com.example.contributed

      import dev.zacsweers.metro.ContributesBinding

      abstract class AppScope private constructor()

      interface Repository

      @ContributesBinding(AppScope::class)
      class ContributedImpl : Repository
      """.trimIndent(),
    )
  }

  private fun file(path: String, content: String) {
    val file = File(projectDir.root, path)
    file.parentFile.mkdirs()
    file.writeText(content)
  }
}

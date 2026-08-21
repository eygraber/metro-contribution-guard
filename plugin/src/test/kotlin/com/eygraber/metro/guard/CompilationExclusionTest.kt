package com.eygraber.metro.guard

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Test

class CompilationExclusionTest {
  @Test
  fun `an exclusion without compilations matches every compilation`() {
    val exclusion = CompilationExclusion(":module", emptySet())

    exclusion.matches("devDebug").shouldBeTrue()
    exclusion.matches("jvmMain").shouldBeTrue()
  }

  @Test
  fun `an exact compilation name matches only that compilation`() {
    val exclusion = CompilationExclusion(":module", setOf("main"))

    exclusion.matches("main").shouldBeTrue()
    exclusion.matches("jvmMain").shouldBeFalse()
  }

  @Test
  fun `a trailing wildcard matches a prefix`() {
    val exclusion = CompilationExclusion(":module", setOf("mock*"))

    exclusion.matches("mockDebug").shouldBeTrue()
    exclusion.matches("mockRelease").shouldBeTrue()
    exclusion.matches("devDebug").shouldBeFalse()
  }

  @Test
  fun `a leading wildcard matches a suffix`() {
    val exclusion = CompilationExclusion(":module", setOf("*Release"))

    exclusion.matches("devRelease").shouldBeTrue()
    exclusion.matches("mockRelease").shouldBeTrue()
    exclusion.matches("devDebug").shouldBeFalse()
    exclusion.matches("releaseDebug").shouldBeFalse()
  }

  @Test
  fun `effective exclusions merge global and matching compilation exclusions`() {
    val effective = effectiveExclusions(
      globalExclusions = setOf(":e2e:fixtures"),
      compilationExclusions = listOf(
        CompilationExclusion(":screens:dev-settings:impl", setOf("*Release")),
        CompilationExclusion(":screens:custom-icons", setOf("mock*")),
      ),
      compilationId = "mockRelease",
    )

    effective.shouldContainExactly(
      ":e2e:fixtures",
      ":screens:dev-settings:impl",
      ":screens:custom-icons",
    )
  }
}

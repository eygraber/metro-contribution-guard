package com.eygraber.metro.guard

import com.eygraber.metro.guard.fixtures.NotContributed
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Test
import java.io.File

class MetroContributionScannerTest {
  private val fixtures = "com.eygraber.metro.guard.fixtures"

  @Suppress("MaxChainedCallsOnSameLine")
  private val testClassesRoot = File(
    NotContributed::class.java.protectionDomain.codeSource.location.toURI(),
  )

  @Test
  fun `scanning compiled classes finds every hinted contribution`() {
    val contributions = MetroContributionScanner.scan(listOf(testClassesRoot))

    contributions.shouldContainExactly(
      MetroContribution(
        kind = MetroContributionKind.ContributesTo,
        scope = "$fixtures.AppScope",
        origin = "$fixtures.SettingsDependencies",
      ),
      MetroContribution(
        kind = MetroContributionKind.ContributesBinding,
        scope = "$fixtures.AppScope",
        origin = "$fixtures.RealRepository",
      ),
      MetroContribution(
        kind = MetroContributionKind.ContributesBinding,
        scope = "$fixtures.UserScope",
        origin = "$fixtures.RealRepository",
      ),
      MetroContribution(
        kind = MetroContributionKind.ContributesIntoSet,
        scope = "$fixtures.UserScope",
        origin = "$fixtures.SetContribution",
      ),
      MetroContribution(
        kind = MetroContributionKind.ContributesIntoMap,
        scope = "$fixtures.AppScope",
        origin = "$fixtures.MapContribution",
      ),
      MetroContribution(
        kind = null,
        scope = "$fixtures.AppScope",
        origin = "$fixtures.ContainerContribution",
      ),
    )
  }

  @Test
  fun `an annotated class without a hint is not reported`() {
    val contributions = MetroContributionScanner.scan(listOf(testClassesRoot))

    contributions.none { it.origin == "$fixtures.AnnotatedButNoHint" }.shouldBeTrue()
  }

  @Test
  fun `scanning a missing root finds nothing`() {
    val contributions = MetroContributionScanner.scan(listOf(File(testClassesRoot, "does-not-exist")))

    contributions.shouldContainExactly(emptyList())
  }
}

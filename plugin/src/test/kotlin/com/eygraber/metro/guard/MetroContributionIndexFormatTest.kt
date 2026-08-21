package com.eygraber.metro.guard

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MetroContributionIndexFormatTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun `an index round trips through the file format`() {
    val index = MetroContributionIndex(
      projectPath = ":feature:settings",
      contributions = setOf(
        MetroContribution(
          kind = MetroContributionKind.ContributesBinding,
          scope = "com.example.AppScope",
          origin = "com.example.RealRepository",
        ),
        MetroContribution(
          kind = MetroContributionKind.ContributesTo,
          scope = "com.example.AppScope",
          origin = "com.example.SettingsDependencies",
        ),
        MetroContribution(
          kind = null,
          scope = "com.example.AppScope",
          origin = "com.example.GeneratedContainer",
        ),
      ),
    )

    val file = temporaryFolder.newFile()
    MetroContributionIndexFormat.write(file, index)

    MetroContributionIndexFormat.read(file) shouldBe index
  }

  @Test
  fun `an empty index round trips through the file format`() {
    val index = MetroContributionIndex(
      projectPath = ":no:contributions",
      contributions = emptySet(),
    )

    val file = temporaryFolder.newFile()
    MetroContributionIndexFormat.write(file, index)

    MetroContributionIndexFormat.read(file) shouldBe index
  }

  @Test
  fun `a file with an unknown header is ignored`() {
    val file = temporaryFolder.newFile()
    file.writeText("metro-contribution-index 999\n:project\n")

    MetroContributionIndexFormat.read(file).shouldBeNull()
  }

  @Test
  fun `malformed entries are ignored`() {
    val file = temporaryFolder.newFile()
    file.writeText(
      buildString {
        appendLine("metro-contribution-index 1")
        appendLine(":project")
        appendLine("NotARealKind\tcom.example.AppScope\tcom.example.Origin")
        appendLine("not enough parts")
        appendLine("ContributesTo\tcom.example.AppScope\tcom.example.Origin")
      },
    )

    MetroContributionIndexFormat.read(file) shouldBe MetroContributionIndex(
      projectPath = ":project",
      contributions = setOf(
        MetroContribution(
          kind = MetroContributionKind.ContributesTo,
          scope = "com.example.AppScope",
          origin = "com.example.Origin",
        ),
      ),
    )
  }
}

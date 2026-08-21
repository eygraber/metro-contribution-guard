package com.eygraber.metro.guard

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import org.junit.Test

class FindMissingContributionsTest {
  private val contribution = MetroContribution(
    kind = MetroContributionKind.ContributesBinding,
    scope = "com.example.AppScope",
    origin = "com.example.RealRepository",
  )

  private fun index(projectPath: String, contributing: Boolean = true) = MetroContributionIndex(
    projectPath = projectPath,
    contributions = if(contributing) setOf(contribution) else emptySet(),
  )

  @Test
  fun `a contributing module that is not visible is reported`() {
    val missing = findMissingContributions(
      indexes = listOf(index(":feature:visible"), index(":feature:missing")),
      visibleProjectPaths = setOf(":app", ":feature:visible"),
      excludedProjectPaths = emptySet(),
    )

    missing.map { it.projectPath }.shouldContainExactly(":feature:missing")
  }

  @Test
  fun `modules without contributions are not reported`() {
    val missing = findMissingContributions(
      indexes = listOf(index(":feature:empty", contributing = false)),
      visibleProjectPaths = setOf(":app"),
      excludedProjectPaths = emptySet(),
    )

    missing.shouldContainExactly(emptyList())
  }

  @Test
  fun `excluded modules are not reported`() {
    val missing = findMissingContributions(
      indexes = listOf(index(":feature:intentionally-missing")),
      visibleProjectPaths = setOf(":app"),
      excludedProjectPaths = setOf(":feature:intentionally-missing"),
    )

    missing.shouldContainExactly(emptyList())
  }

  @Test
  fun `missing modules are sorted by project path`() {
    val missing = findMissingContributions(
      indexes = listOf(index(":z"), index(":a"), index(":m")),
      visibleProjectPaths = emptySet(),
      excludedProjectPaths = emptySet(),
    )

    missing.map { it.projectPath }.shouldContainExactly(":a", ":m", ":z")
  }

  @Test
  fun `the failure message names the missing module and its contributions`() {
    val message = buildMissingContributionsMessage(
      projectPath = ":app",
      compilationId = "jvmMain",
      missing = listOf(index(":feature:missing")),
    )

    message shouldContain ":feature:missing"
    message shouldContain "@ContributesBinding(scope = com.example.AppScope) on com.example.RealRepository"
    message shouldContain "excludedProjects"
  }
}

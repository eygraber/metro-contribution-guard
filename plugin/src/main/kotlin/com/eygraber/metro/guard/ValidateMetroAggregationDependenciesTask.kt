package com.eygraber.metro.guard

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class ValidateMetroAggregationDependenciesTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val contributionIndexes: ConfigurableFileCollection

  @get:Input
  abstract val visibleProjectPaths: SetProperty<String>

  @get:Input
  abstract val excludedProjects: SetProperty<String>

  @get:Input
  abstract val compilationId: Property<String>

  @get:Input
  abstract val projectPath: Property<String>

  @get:OutputFile
  abstract val reportFile: RegularFileProperty

  @TaskAction
  fun validate() {
    val indexes = contributionIndexes
      .files
      .filter { it.isFile }
      .mapNotNull { file -> MetroContributionIndexFormat.read(file) }

    val missing = findMissingContributions(
      indexes = indexes,
      visibleProjectPaths = visibleProjectPaths.get() + projectPath.get(),
      excludedProjectPaths = excludedProjects.get(),
    )

    val report = when {
      missing.isEmpty() ->
        "All ${indexes.count { it.contributions.isNotEmpty() }} contributing module(s) " +
          "are visible to ${projectPath.get()} (${compilationId.get()})."

      else -> buildMissingContributionsMessage(
        projectPath = projectPath.get(),
        compilationId = compilationId.get(),
        missing = missing,
      )
    }

    val reportFile = reportFile.get().asFile
    reportFile.parentFile?.mkdirs()
    reportFile.writeText(report)

    if(missing.isNotEmpty()) {
      throw GradleException(report)
    }
  }
}

internal fun findMissingContributions(
  indexes: List<MetroContributionIndex>,
  visibleProjectPaths: Set<String>,
  excludedProjectPaths: Set<String>,
): List<MetroContributionIndex> =
  indexes
    .filter { index ->
      index.contributions.isNotEmpty() &&
        index.projectPath !in visibleProjectPaths &&
        index.projectPath !in excludedProjectPaths
    }
    .sortedBy { it.projectPath }

internal fun buildMissingContributionsMessage(
  projectPath: String,
  compilationId: String,
  missing: List<MetroContributionIndex>,
): String = buildString {
  appendLine(
    "${missing.size} module(s) declare Metro contributions " +
      "but are not visible to $projectPath ($compilationId compile classpath):",
  )
  for(index in missing) {
    appendLine()
    appendLine("  ${index.projectPath}")
    for(contribution in index.contributions.toSortedSet()) {
      @Suppress("ElseCaseInsteadOfExhaustiveWhen")
      val display = when(contribution.kind) {
        null -> "contribution to ${contribution.scope} from ${contribution.origin}"
        else -> "@${contribution.kind.simpleName}(scope = ${contribution.scope}) on ${contribution.origin}"
      }
      appendLine("    $display")
    }
  }
  appendLine()
  appendLine("Metro silently omits contributions from modules that are not on the compile classpath.")
  appendLine("Add the missing modules to $projectPath's dependencies,")
  append("or exclude intentionally disconnected modules via metroAggregationValidation.excludedProjects.")
}

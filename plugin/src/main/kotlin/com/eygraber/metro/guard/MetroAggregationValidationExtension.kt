package com.eygraber.metro.guard

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import java.io.Serializable
import javax.inject.Inject

abstract class MetroAggregationValidationExtension @Inject constructor(objects: ObjectFactory) {
  abstract val excludedProjects: SetProperty<String>

  internal val compilationExclusions: ListProperty<CompilationExclusion> =
    objects.listProperty(CompilationExclusion::class.java)

  fun excludeProject(projectPath: String, vararg fromCompilations: String) {
    compilationExclusions.add(
      CompilationExclusion(
        projectPath = projectPath,
        compilationPatterns = fromCompilations.toSet(),
      ),
    )
  }
}

internal data class CompilationExclusion(
  val projectPath: String,
  val compilationPatterns: Set<String>,
) : Serializable {
  fun matches(compilationId: String): Boolean =
    compilationPatterns.isEmpty() ||
      compilationPatterns.any { pattern -> pattern.toGlobRegex().matches(compilationId) }

  private companion object {
    @Suppress("ObjectPropertyNaming")
    private const val serialVersionUID = 1L
  }
}

private fun String.toGlobRegex(): Regex =
  split('*').joinToString(separator = ".*") { part -> Regex.escape(part) }.toRegex()

internal fun effectiveExclusions(
  globalExclusions: Set<String>,
  compilationExclusions: List<CompilationExclusion>,
  compilationId: String,
): Set<String> =
  globalExclusions +
    compilationExclusions
      .filter { it.matches(compilationId) }
      .map { it.projectPath }

package com.eygraber.metro.guard

import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import java.io.File

internal enum class MetroContributionKind(val annotationFqName: String) {
  ContributesTo("dev.zacsweers.metro.ContributesTo"),
  ContributesBinding("dev.zacsweers.metro.ContributesBinding"),
  ContributesIntoSet("dev.zacsweers.metro.ContributesIntoSet"),
  ContributesIntoMap("dev.zacsweers.metro.ContributesIntoMap"),
  ;

  val simpleName: String get() = annotationFqName.substringAfterLast('.')
}

internal data class MetroContribution(
  val kind: MetroContributionKind?,
  val scope: String,
  val origin: String,
) : Comparable<MetroContribution> {
  override fun compareTo(other: MetroContribution): Int = comparator.compare(this, other)

  companion object {
    private val comparator = compareBy<MetroContribution>(
      { it.kind?.ordinal ?: MetroContributionKind.entries.size },
      { it.scope },
      { it.origin },
    )
  }
}

internal data class MetroContributionIndex(
  val projectPath: String,
  val contributions: Set<MetroContribution>,
)

internal object MetroContributionIndexes {
  const val USAGE_ATTRIBUTE_VALUE = "metro-contribution-index"
  const val CATEGORY_ATTRIBUTE_VALUE = "metro-contribution-index"
  const val ELEMENTS_CONFIGURATION_NAME = "metroContributionIndexElements"
  const val DEPENDENCIES_CONFIGURATION_NAME = "metroContributionIndex"
  const val CLASSPATH_CONFIGURATION_NAME = "metroContributionIndexClasspath"
}

internal fun AttributeContainer.applyMetroContributionIndexAttributes(objects: ObjectFactory) {
  attribute(
    Usage.USAGE_ATTRIBUTE,
    objects.named(Usage::class.java, MetroContributionIndexes.USAGE_ATTRIBUTE_VALUE),
  )
  attribute(
    Category.CATEGORY_ATTRIBUTE,
    objects.named(Category::class.java, MetroContributionIndexes.CATEGORY_ATTRIBUTE_VALUE),
  )
}

internal object MetroContributionIndexFormat {
  private const val HEADER = "metro-contribution-index 1"
  private const val SEPARATOR = "\t"
  private const val ENTRY_PART_COUNT = 3
  private const val UNKNOWN_KIND = "-"

  fun write(file: File, index: MetroContributionIndex) {
    file.parentFile?.mkdirs()
    file.bufferedWriter().use { writer ->
      writer.appendLine(HEADER)
      writer.appendLine(index.projectPath)
      for(contribution in index.contributions.toSortedSet()) {
        writer.appendLine(
          listOf(
            contribution.kind?.name ?: UNKNOWN_KIND,
            contribution.scope,
            contribution.origin,
          ).joinToString(SEPARATOR),
        )
      }
    }
  }

  fun read(file: File): MetroContributionIndex? {
    val lines = file.readLines()
    if(lines.firstOrNull() != HEADER) return null
    val projectPath = lines.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
    val contributions = lines
      .asSequence()
      .drop(2)
      .filter { it.isNotBlank() }
      .mapNotNull { line -> readContribution(line) }
      .toSet()

    return MetroContributionIndex(
      projectPath = projectPath,
      contributions = contributions,
    )
  }

  private fun readContribution(line: String): MetroContribution? {
    val parts = line.split(SEPARATOR)
    if(parts.size != ENTRY_PART_COUNT) return null
    val kind = when(parts[0]) {
      UNKNOWN_KIND -> null
      else -> MetroContributionKind.entries.find { it.name == parts[0] } ?: return null
    }
    return MetroContribution(
      kind = kind,
      scope = parts[1],
      origin = parts[2],
    )
  }
}

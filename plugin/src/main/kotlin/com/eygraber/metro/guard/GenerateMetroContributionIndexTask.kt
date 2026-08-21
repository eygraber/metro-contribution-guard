package com.eygraber.metro.guard

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateMetroContributionIndexTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val compilationIndexes: ConfigurableFileCollection

  @get:Input
  abstract val projectPath: Property<String>

  @get:OutputFile
  abstract val indexFile: RegularFileProperty

  @TaskAction
  fun merge() {
    val contributions = compilationIndexes
      .files
      .filter { it.isFile }
      .mapNotNull { file -> MetroContributionIndexFormat.read(file) }
      .flatMapTo(sortedSetOf()) { index -> index.contributions }

    MetroContributionIndexFormat.write(
      file = indexFile.get().asFile,
      index = MetroContributionIndex(
        projectPath = projectPath.get(),
        contributions = contributions,
      ),
    )
  }
}

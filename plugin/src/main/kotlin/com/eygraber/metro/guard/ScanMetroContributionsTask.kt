package com.eygraber.metro.guard

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class ScanMetroContributionsTask : DefaultTask() {
  @get:Classpath
  abstract val classesDirectories: ConfigurableFileCollection

  @get:Classpath
  abstract val inputJars: ListProperty<RegularFile>

  @get:Classpath
  abstract val inputDirectories: ListProperty<Directory>

  @get:Input
  abstract val projectPath: Property<String>

  @get:OutputFile
  abstract val indexFile: RegularFileProperty

  @TaskAction
  fun scan() {
    val roots = classesDirectories.files +
      inputJars.get().map { it.asFile } +
      inputDirectories.get().map { it.asFile }

    MetroContributionIndexFormat.write(
      file = indexFile.get().asFile,
      index = MetroContributionIndex(
        projectPath = projectPath.get(),
        contributions = MetroContributionScanner.scan(roots),
      ),
    )
  }
}

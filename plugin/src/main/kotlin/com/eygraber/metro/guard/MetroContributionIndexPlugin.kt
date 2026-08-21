package com.eygraber.metro.guard

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class MetroContributionIndexPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val generateIndex = target.tasks.register<GenerateMetroContributionIndexTask>(
      "generateMetroContributionIndex",
    ) {
      description = "Merges per-compilation Metro contribution indexes into this project's published index."
      projectPath.set(target.path)
      indexFile.set(target.layout.buildDirectory.file("metro-contribution-index/metro-contributions.index"))
    }

    val elements = target.configurations.consumable(MetroContributionIndexes.ELEMENTS_CONFIGURATION_NAME) {
      attributes {
        applyMetroContributionIndexAttributes(target.objects)
      }
    }

    target.artifacts.add(elements.name, generateIndex.flatMap { it.indexFile })

    target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
      val main = target.extensions.getByType<SourceSetContainer>().named(SourceSet.MAIN_SOURCE_SET_NAME)
      registerScanTask(
        target = target,
        generateIndex = generateIndex,
        compilationId = SourceSet.MAIN_SOURCE_SET_NAME,
        classes = target.files(main.map { sourceSet -> sourceSet.output.classesDirs }),
      )
    }

    target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
      val kotlin = target.extensions.getByType<KotlinMultiplatformExtension>()
      kotlin.targets.configureEach {
        val kotlinTarget = this
        if(kotlinTarget.producesJvmClasses) {
          kotlinTarget.compilations.configureEach {
            val compilation = this
            if(compilation.isProductionCompilation) {
              registerScanTask(
                target = target,
                generateIndex = generateIndex,
                compilationId = "${kotlinTarget.name}${compilation.name.capitalizedName()}",
                classes = compilation.output.classesDirs,
              )
            }
          }
        }
      }
    }

    target.pluginManager.withPlugin("com.android.application") {
      target
        .extensions
        .getByType<ApplicationAndroidComponentsExtension>()
        .onVariants { variant -> configureAndroidVariant(target, generateIndex, variant) }
    }

    target.pluginManager.withPlugin("com.android.library") {
      target
        .extensions
        .getByType<LibraryAndroidComponentsExtension>()
        .onVariants { variant -> configureAndroidVariant(target, generateIndex, variant) }
    }
  }

  private fun configureAndroidVariant(
    target: Project,
    generateIndex: TaskProvider<GenerateMetroContributionIndexTask>,
    variant: Variant,
  ) {
    if(target.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) return

    val scanTask = target.tasks.register<ScanMetroContributionsTask>(
      "scanMetroContributionsFor${variant.name.capitalizedName()}",
    ) {
      description = "Scans the ${variant.name} classes for Metro contributions."
      projectPath.set(target.path)
      indexFile.set(
        target.layout.buildDirectory.file("metro-contribution-index/compilations/${variant.name}.index"),
      )
    }

    variant
      .artifacts
      .forScope(ScopedArtifacts.Scope.PROJECT)
      .use(scanTask)
      .toGet(
        ScopedArtifact.CLASSES,
        ScanMetroContributionsTask::inputJars,
        ScanMetroContributionsTask::inputDirectories,
      )

    generateIndex.configure {
      compilationIndexes.from(scanTask.flatMap { task -> task.indexFile })
    }
  }

  private fun registerScanTask(
    target: Project,
    generateIndex: TaskProvider<GenerateMetroContributionIndexTask>,
    compilationId: String,
    classes: FileCollection,
  ) {
    val scanTask = target.tasks.register<ScanMetroContributionsTask>(
      "scanMetroContributionsFor${compilationId.capitalizedName()}",
    ) {
      description = "Scans the $compilationId classes for Metro contributions."
      projectPath.set(target.path)
      indexFile.set(
        target.layout.buildDirectory.file("metro-contribution-index/compilations/$compilationId.index"),
      )
      classesDirectories.from(classes)
    }

    generateIndex.configure {
      compilationIndexes.from(scanTask.flatMap { task -> task.indexFile })
    }
  }
}

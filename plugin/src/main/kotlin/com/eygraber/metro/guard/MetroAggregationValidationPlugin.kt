package com.eygraber.metro.guard

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

class MetroAggregationValidationPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val extension = target.extensions.create(
      "metroAggregationValidation",
      MetroAggregationValidationExtension::class.java,
    )

    val indexDependencies = target
      .configurations
      .dependencyScope(MetroContributionIndexes.DEPENDENCIES_CONFIGURATION_NAME)
      .get()

    val indexClasspath = target.configurations.resolvable(
      MetroContributionIndexes.CLASSPATH_CONFIGURATION_NAME,
    ) {
      extendsFrom(indexDependencies)
      attributes {
        applyMetroContributionIndexAttributes(target.objects)
      }
    }

    target.rootProject.allprojects.forEach { other ->
      val projectPath = other.isolated.path
      if(projectPath != target.path) {
        val dependency = target.dependencies.project(mapOf("path" to projectPath)) as ProjectDependency
        dependency.isTransitive = false
        indexDependencies.dependencies.add(dependency)
      }
    }

    val indexFiles = indexClasspath
      .get()
      .incoming
      .artifactView { lenient(true) }
      .files

    val validateAll = target.tasks.register("validateMetroAggregationDependencies") {
      group = JavaBasePlugin.VERIFICATION_GROUP
      description =
        "Validates that every module declaring Metro contributions is visible to this module's " +
        "production compile classpaths."
    }

    target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
      registerCompilationValidation(
        target = target,
        validateAll = validateAll,
        extension = extension,
        indexFiles = indexFiles,
        compilationId = "main",
        visibleProjectPaths = target.visibleProjectPathsOf(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME),
      )
    }

    target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
      val kotlin = target.extensions.getByType<KotlinMultiplatformExtension>()
      kotlin.targets.configureEach {
        val kotlinTarget = this
        if(kotlinTarget.platformType != KotlinPlatformType.common) {
          kotlinTarget.compilations.configureEach {
            val compilation = this
            if(compilation.isProductionCompilation) {
              registerCompilationValidation(
                target = target,
                validateAll = validateAll,
                extension = extension,
                indexFiles = indexFiles,
                compilationId = "${kotlinTarget.name}${compilation.name.capitalizedName()}",
                visibleProjectPaths = target.visibleProjectPathsOf(compilation.compileDependencyConfigurationName),
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
        .onVariants { variant ->
          configureAndroidVariant(
            target = target,
            validateAll = validateAll,
            extension = extension,
            indexFiles = indexFiles,
            variant = variant,
          )
        }
    }

    target.pluginManager.withPlugin("com.android.library") {
      target
        .extensions
        .getByType<LibraryAndroidComponentsExtension>()
        .onVariants { variant ->
          configureAndroidVariant(
            target = target,
            validateAll = validateAll,
            extension = extension,
            indexFiles = indexFiles,
            variant = variant,
          )
        }
    }
  }

  private fun configureAndroidVariant(
    target: Project,
    validateAll: TaskProvider<Task>,
    extension: MetroAggregationValidationExtension,
    indexFiles: FileCollection,
    variant: Variant,
  ) {
    if(target.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) return

    registerCompilationValidation(
      target = target,
      validateAll = validateAll,
      extension = extension,
      indexFiles = indexFiles,
      compilationId = variant.name,
      visibleProjectPaths = variant
        .compileConfiguration
        .incoming
        .resolutionResult
        .rootComponent
        .map { root -> collectProjectPaths(root) },
    )
  }

  private fun registerCompilationValidation(
    target: Project,
    validateAll: TaskProvider<Task>,
    extension: MetroAggregationValidationExtension,
    indexFiles: FileCollection,
    compilationId: String,
    visibleProjectPaths: Provider<Set<String>>,
  ) {
    val validate = target.tasks.register<ValidateMetroAggregationDependenciesTask>(
      "validateMetroAggregationDependenciesFor${compilationId.capitalizedName()}",
    ) {
      description = "Validates Metro contribution visibility for the $compilationId compile classpath."
      contributionIndexes.from(indexFiles)
      excludedProjects.set(
        extension.excludedProjects.zip(extension.compilationExclusions) { global, exclusions ->
          effectiveExclusions(global, exclusions, compilationId)
        },
      )
      this.visibleProjectPaths.set(visibleProjectPaths)
      this.compilationId.set(compilationId)
      projectPath.set(target.path)
      reportFile.set(
        target.layout.buildDirectory.file("reports/metro-aggregation-validation/$compilationId.txt"),
      )
    }

    validateAll.configure {
      dependsOn(validate)
    }
  }

  private fun Project.visibleProjectPathsOf(configurationName: String): Provider<Set<String>> =
    configurations
      .named(configurationName)
      .flatMap { it.incoming.resolutionResult.rootComponent }
      .map { root -> collectProjectPaths(root) }
}

internal fun collectProjectPaths(root: ResolvedComponentResult): Set<String> {
  val paths = mutableSetOf<String>()
  val seen = mutableSetOf(root.id)
  val queue = ArrayDeque(listOf(root))

  while(queue.isNotEmpty()) {
    val component = queue.removeFirst()
    (component.id as? ProjectComponentIdentifier)?.let { paths += it.projectPath }
    for(dependency in component.dependencies) {
      if(dependency is ResolvedDependencyResult && seen.add(dependency.selected.id)) {
        queue += dependency.selected
      }
    }
  }

  return paths
}

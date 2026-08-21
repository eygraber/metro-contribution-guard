# Metro Contribution Guard

Gradle plugins that find [Metro] contributions that are missing from an aggregation root's compile classpath.

## Problem

Metro aggregates contributions from the compile classpath of the module that holds the dependency graph.
A module annotated with one of these annotations must be visible on that classpath:

- `@ContributesTo`
- `@ContributesBinding`
- `@ContributesIntoSet`
- `@ContributesIntoMap`

When such a module is not visible, the graph still compiles, but Metro omits the contributions.

## Plugins

The project publishes two plugins.

### com.eygraber.metro-contribution-index

Apply this plugin to every module that uses Metro.

The Metro compiler writes a hint function into the `metro.hints` package for each contribution.
The plugin registers a cacheable scan task for each production compilation (test compilations are ignored).
Each scan task reads the compiled hints and records each contribution:

- the contribution kind, when the class that contributes holds a known contribution annotation
- the contributed scope
- the class that contributes

The plugin merges the per-compilation records into one index file for the module.
A module without contributions produces an empty index.
The index is published through the consumable configuration `metroContributionIndexElements`.
The configuration does not change any production dependency configuration.

### com.eygraber.metro-aggregation-validation

Apply this plugin to each module that contains a Metro aggregation root.

It registers a standalone task; `validateMetroAggregationDependencies`.
The task collects the contribution indexes from all projects in the build.
It then resolves each production compile classpath of the module.
It fails when a module with contributions is not visible on a classpath.
The failure message lists each missing module and its contributions.

Both plugins are compatible with the configuration cache and with isolated projects.

## Usage

Apply the index plugin in each module that uses Metro:

```kotlin
plugins {
  id("com.eygraber.metro-contribution-index") version "0.1.0"
}
```

Apply the validation plugin in each aggregation root module:

```kotlin
plugins {
  id("com.eygraber.metro-aggregation-validation") version "0.1.0"
}
```

Run the validation:

```shell
./gradlew :app:validateMetroAggregationDependencies
```

## Exclusions

Exclude a module that you keep disconnected on purpose.

```kotlin
metroAggregationValidation {
  excludedProjects.add(
    ":some:intentionally-missing-module",
  )
}
```

Exclude a module from named compilations only.
A compilation name is a Kotlin compilation identifier such as `jvmMain`, or an Android variant name such as `devDebug`.
The name accepts `*` as a wildcard.

```kotlin
metroAggregationValidation {
  excludeProject(":screens:dev-settings:impl", "*Release")
  excludeProject(":screens:custom-icons", "dev*")
}
```

An exclusion suppresses validation for that module.
It does not add the module's contributions to Metro.

## Supported project types

| Plugin                          | Compilations                                    |
|---------------------------------|-------------------------------------------------|
| Kotlin/JVM                      | `main`                                          |
| Kotlin Multiplatform            | Production compilations of each platform target |
| Android application and library | Production variants                             |

## Limitations

The scan reads hints from JVM class files.
A Kotlin Multiplatform module without a JVM or Android target produces an empty index.
The index merges contributions from all targets of a module.
Validation of one platform classpath can therefore report a contribution that only exists on another platform.
Use an exclusion for that case.

[Metro]: https://zacsweers.github.io/metro/

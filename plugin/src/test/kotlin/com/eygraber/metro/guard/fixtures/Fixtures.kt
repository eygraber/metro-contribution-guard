@file:Suppress("AbstractClassCanBeInterface")

package com.eygraber.metro.guard.fixtures

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo

abstract class AppScope private constructor()

abstract class UserScope private constructor()

@ContributesTo(AppScope::class)
interface SettingsDependencies

@ContributesBinding(AppScope::class)
@ContributesBinding(UserScope::class)
class RealRepository

@ContributesIntoSet(UserScope::class)
class SetContribution

@ContributesIntoMap(AppScope::class)
class MapContribution

class ContainerContribution

@ContributesTo(AppScope::class)
interface AnnotatedButNoHint

class NotContributed

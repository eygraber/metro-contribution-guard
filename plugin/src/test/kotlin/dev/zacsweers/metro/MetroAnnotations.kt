package dev.zacsweers.metro

import kotlin.reflect.KClass

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class ContributesTo(val scope: KClass<*>)

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class ContributesBinding(val scope: KClass<*>)

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class ContributesIntoSet(val scope: KClass<*>)

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class ContributesIntoMap(val scope: KClass<*>)

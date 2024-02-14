package de.test.podvinci

/**
 * Tests with this annotation are ignored on CI. This could be reasonable
 * if the performance of the CI server is not enough to provide a reliable result.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class IgnoreOnCi

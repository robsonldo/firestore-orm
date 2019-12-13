package br.com.robsonldo.library.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Persisted(val value: Boolean = true)
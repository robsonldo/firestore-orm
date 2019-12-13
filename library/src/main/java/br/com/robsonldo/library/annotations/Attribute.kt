package br.com.robsonldo.library.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Attribute(val value: String, val save: Boolean = true)
package br.com.robsonldo.library.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Collection(
    val value: String,
    val params: String = "",
    val valueInObject: Boolean = true
)
package br.com.robsonldo.library.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Attribute(
    val value: String,
    val readOnly: Boolean = false,
    val canBeNull:Boolean = true,
    val ifNullDelete:Boolean = false
)
package br.com.robsonldo.library.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class AttributeReference(
    val collection: String = "",
    val referenceName: String,
    val attributeWithId: String,
    val path: String = "",
    val paramsAttributes: Array<String> = []
)
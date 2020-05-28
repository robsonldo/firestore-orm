package br.com.robsonldo.library.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class AttributeCollection(
    val collection: String,
    val keyFieldName: String = "",
    val key: String = "",
    val params: Array<String> = []
)
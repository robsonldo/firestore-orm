package br.com.robsonldo.library

class Utils private constructor() {

    companion object {

        fun convertInInt(obj: Any?): Int? {
            return when {
                obj == null -> null
                !isPrimitive(obj) -> null
                obj is Int -> obj
                obj is Long -> obj.toInt()
                obj is Double -> obj.toInt()
                obj is Float -> obj.toInt()
                obj is String && obj.isNumber() -> obj.toInt()
                else -> null
            }
        }

        fun convertInLong(obj: Any?): Long? {
            return when {
                obj == null -> null
                !isPrimitive(obj) -> null
                obj is Long -> obj
                obj is Int -> obj.toLong()
                obj is Double -> obj.toLong()
                obj is Float -> obj.toLong()
                obj is String && obj.isNumber() -> obj.toLong()
                else -> null
            }
        }

        fun convertInDouble(obj: Any?): Double? {
            return when {
                obj == null -> null
                !isPrimitive(obj) -> null
                obj is Double -> obj
                obj is Float -> obj.toDouble()
                obj is Long -> obj.toDouble()
                obj is Int -> obj.toDouble()
                obj is String && obj.isNumber() -> obj.toDouble()
                else -> null
            }
        }

        fun convertInFloat(obj: Any?): Float? {
            return when {
                obj == null -> null
                !isPrimitive(obj) -> null
                obj is Float -> obj
                obj is Double -> obj.toFloat()
                obj is Long -> obj.toFloat()
                obj is Int -> obj.toFloat()
                obj is String && obj.isNumber() -> obj.toFloat()
                else -> null
            }
        }

        fun isPrimitive(obj: Any): Boolean = obj::class.javaPrimitiveType != null
        fun String.isNumber() : Boolean = this.matches("^-?\\d+([.,]\\d+)?\$".toRegex())
    }
}
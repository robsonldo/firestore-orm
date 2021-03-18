package br.com.robsonldo.library

import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

class Utils private constructor() {

    companion object {

        @JvmStatic
        @Throws(Exception::class)
        fun getParameterizedTypeArgumentPosition(
            pt: ParameterizedType,
            position: Int
        ): Pair<ParameterizedType, Class<*>>? {
            fun valueInPair(
                pt: ParameterizedType,
                clazz: Class<*>
            ) = Pair(pt, clazz)

            var cPt = pt
            return when(cPt.actualTypeArguments[position]) {
                is Class<*> ->  {
                    valueInPair(cPt, cPt.actualTypeArguments[position] as Class<*>)
                }
                is ParameterizedType -> {
                    cPt = (cPt.actualTypeArguments[position] as ParameterizedType)
                    valueInPair(cPt, cPt.rawType as Class<*>)
                }
                is WildcardType -> {
                    cPt = (cPt.actualTypeArguments[position] as WildcardType)
                        .upperBounds[position] as ParameterizedType

                    valueInPair(cPt, cPt.rawType as Class<*>)
                }
                else -> null
            }
        }

        @JvmStatic
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

        @JvmStatic
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

        @JvmStatic
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

        @JvmStatic
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

        @JvmStatic
        fun isPrimitive(obj: Any): Boolean = obj::class.javaPrimitiveType != null

        @JvmStatic
        fun String.isNumber() : Boolean = this.matches("^-?\\d+([.,]\\d+)?\$".toRegex())
    }
}
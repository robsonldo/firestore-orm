package br.com.robsonldo.library

import br.com.robsonldo.library.annotations.ReadOnly
import br.com.robsonldo.library.annotations.TimestampAction
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.util.*
import kotlin.collections.HashMap

class DataParse private constructor() {

    companion object {

        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun <T: FireStoreORM<*>> documentSnapshotInObject(document: DocumentSnapshot, ref: T): T {
            ref.id = document.id
            ref.fieldId?.let { it.isAccessible = true; it.set(ref, ref.id) }

            return when {
                document.data != null -> fromMap(document.data!!, null, ref)
                else -> ref
            }
        }

        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun <T: FireStoreORM<*>> fromMap(data: MutableMap<String, Any?>, clazzVariable: Class<*>?,
                                         ref: T): T {

            ref.wasFound = true

            if (ref.collection?.valueInObject != true) {
                ref.valueInHashMap?.let {
                    it.isAccessible = true
                    it.set(ref, data)
                }

                return ref
            }

            loop@ for (entry in data) {
                val field: Field = ref.attributes[entry.key] ?: continue
                field.isAccessible = true

                when {
                    Collection::class.java.isAssignableFrom(field.type) -> {
                        val type = field.genericType as ParameterizedType
                        var typeClass: Class<*>? = null

                        if (type.actualTypeArguments[0] is Class<*>) {
                            typeClass = type.actualTypeArguments[0] as Class<*>
                        } else if (type.actualTypeArguments[0] is TypeVariable<*>) {
                            typeClass = clazzVariable
                        }

                        if (typeClass != null
                            && FireStoreORM::class.java.isAssignableFrom(typeClass)) {

                            val objects: MutableCollection<FireStoreORM<*>> = mutableListOf()

                            if (entry.value is Collection<*>) {
                                val mapArrays: MutableCollection<HashMap<String, Any?>> =
                                    entry.value as MutableCollection<HashMap<String, Any?>>

                                for (mapArray in mapArrays) {
                                    val fireStoreORM: FireStoreORM<*> =
                                        typeClass.newInstance() as FireStoreORM<*>

                                    objects.add(fromMap(mapArray, null, fireStoreORM))
                                }
                            }

                            field.set(ref, objects)
                        } else {
                            field.set(ref, entry.value)
                        }
                    }
                    MutableMap::class.java.isAssignableFrom(field.type) -> {
                        if (entry.value == null || entry.value !is MutableMap<*, *>) {
                            field.set(ref, null)
                            continue@loop
                        }

                        val type = field.genericType as ParameterizedType
                        var typeClass: Class<*>? = null

                        if (type.actualTypeArguments[1] is Class<*>) {
                            typeClass = type.actualTypeArguments[1] as Class<*>
                        }

                        val hash: MutableMap<String, Any?> =
                            if (TreeMap::class.java.isAssignableFrom(field.type)) {
                                TreeMap()
                            } else hashMapOf()

                        for (entryHash in entry.value as HashMap<String, Any?>) {
                            if (typeClass != null
                                && FireStoreORM::class.java.isAssignableFrom(typeClass)) {

                                var fireStoreORM: FireStoreORM<*> =
                                    typeClass.newInstance() as FireStoreORM<*>

                                fireStoreORM = fromMap(entryHash.value as MutableMap<String, Any?>,
                                    null, fireStoreORM)

                                hash[entryHash.key] = fireStoreORM
                            } else {
                                hash[entryHash.key] = manageDefinedTypes(typeClass, entryHash.value)
                            }
                        }

                        field.set(ref, hash)
                    }
                    FireStoreORM::class.java.isAssignableFrom(field.type) -> {
                        if (entry.value is MutableMap<*, *>) {
                            var typeClass: Class<*>? = null
                            if (field.genericType is ParameterizedType) {
                                val type = field.genericType as ParameterizedType
                                typeClass = type.actualTypeArguments[0] as Class<*>
                            }

                            val fireStoreORM: FireStoreORM<*> =
                                field.type.newInstance() as FireStoreORM<*>

                            field.set(ref, fromMap(entry.value as MutableMap<String, Any?>,
                                typeClass, fireStoreORM))
                        }
                    }
                    else -> field.set(ref, manageDefinedTypes(field.type, entry.value))
                }
            }

            return ref
        }

        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun <T: FireStoreORM<*>> toMap(ref: T): MutableMap<String, Any?> {
            if (ref.collection?.valueInObject != true) {
                var map: MutableMap<String, Any?>? = null
                ref.valueInHashMap?.let {
                    it.isAccessible = true
                    map = it.get(ref) as MutableMap<String, Any?>?
                }

                return map ?: mutableMapOf()
            }

            val map: MutableMap<String, Any?> = mutableMapOf()
            ref.fieldId?.let { it.isAccessible = true; it.set(ref, ref.id) }

            loop@ for (entry in ref.attributes) {
                val field = entry.value
                field.isAccessible = true

                if (field.isAnnotationPresent(ReadOnly::class.java)) continue@loop

                when {
                    field.get(ref) == null -> continue@loop
                    Collection::class.java.isAssignableFrom(field.type) -> {
                        val type = field.genericType as ParameterizedType
                        val typeClass: Class<*> = type.actualTypeArguments[0] as Class<*>

                        if (FireStoreORM::class.java.isAssignableFrom(typeClass)) {
                            val objects = field.get(ref) as MutableList<FireStoreORM<*>>
                            if (objects.isEmpty()) continue@loop

                            val objectsMap: MutableList<MutableMap<String, Any?>> = mutableListOf()
                            for (obj in objects) objectsMap.add(toMap(obj))

                            map[entry.key] = objectsMap
                        } else {
                            map[entry.key] = field.get(ref)!!
                        }
                    }
                    MutableMap::class.java.isAssignableFrom(field.type) -> {
                        if (field.get(ref) == null) {
                            map[entry.key] = null
                            continue@loop
                        }

                        val type: ParameterizedType = field.genericType as ParameterizedType
                        var typeClass: Class<*>? = null

                        if (type.actualTypeArguments[1] is Class<*>) {
                            typeClass = type.actualTypeArguments[1] as Class<*>
                        }

                        val hash: MutableMap<String, Any?> = hashMapOf()
                        for (entryHash in field.get(ref) as MutableMap<String, Any?>) {
                            if (typeClass != null
                                && FireStoreORM::class.java.isAssignableFrom(typeClass)) {

                                if (entryHash.value == null) {
                                    hash[entryHash.key] = null
                                } else {
                                    hash[entryHash.key] = toMap(entryHash.value as FireStoreORM<*>)
                                }
                            } else hash[entryHash.key] = entryHash.value
                        }

                        map[entry.key] = hash
                    }
                    FireStoreORM::class.java.isAssignableFrom(field.type) -> {
                        map[entry.key] = toMap(field.get(ref) as FireStoreORM<*>)
                    }
                    Timestamp::class.java.isAssignableFrom(field.type) -> {
                        val ta = field.getAnnotation(TimestampAction::class.java)
                        if (ta == null) map[entry.key] = field.get(ref)
                        else if ((ta.create && field.get(ref) == null) || ta.update) {
                            map[entry.key] = FieldValue.serverTimestamp()
                            field.set(ref, Timestamp(Date()))
                        }
                    }
                    else -> map[entry.key] = field.get(ref)
                }
            }

            return map
        }

        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        private fun manageDefinedTypes(clazz: Class<*>?, any: Any?): Any? {
            return when {
                clazz == null -> null
                Timestamp::class.java.isAssignableFrom(clazz) -> {
                    if (any is Timestamp) any
                    else if (any is MutableMap<*, *>) {
                        val map = any as MutableMap<String, Any?>
                        val second: Long? = Utils.convertInLong(map["_second"])
                        val nanoSeconds: Int? = Utils.convertInInt(map["_nanoseconds"])

                        if (second != null && nanoSeconds != null) {
                            Timestamp(second, nanoSeconds)
                        } else null
                    } else null
                }
                clazz == Long::class.javaObjectType && any is Number -> Utils.convertInLong(any)
                clazz == Double::class.javaObjectType && any is Number -> Utils.convertInDouble(any)
                clazz == Float::class.javaObjectType && any is Number -> Utils.convertInFloat(any)
                clazz == Int::class.javaObjectType && any is Number -> Utils.convertInInt(any)
                clazz == Boolean::class.javaObjectType -> {
                    if (any !is Boolean) false
                    else any
                }
                else -> any
            }
        }
    }
}
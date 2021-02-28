package br.com.robsonldo.library

import br.com.robsonldo.library.annotations.Attribute
import br.com.robsonldo.library.annotations.ReadOnly
import br.com.robsonldo.library.annotations.ThisIsNotNull
import br.com.robsonldo.library.annotations.TimestampAction
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.util.*

class DataParse private constructor() {

    companion object {

        @JvmStatic
        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun <T: FireStoreORM<*>> documentSnapshotInObject(
            document: DocumentSnapshot,
            ref: T
        ): T {
            ref.id = document.id

            ref.fieldId?.let {
                it.isAccessible = true
                it.set(ref, ref.id)
            }

            if (ref.documentSnapshotSave != null) { ref.documentSnapshot = document }

            return when (val data = document.data) {
                null -> ref
                else -> fromMap(data, null, ref)
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun <T: FireStoreORM<*>> fromMap(
            data: MutableMap<String, Any?>,
            clazzVariable: Class<*>?,
            ref: T
        ): T {
            ref.wasFound = true

            if (ref.collection?.valueInObject != true) {
                ref.valueInHashMap?.let {
                    it.isAccessible = true
                    it.set(ref, data)
                }

                return ref
            }

            loop@ for (entry in ref.attributes) {
                val value = data[entry.key]
                val field:Field = entry.value.apply { isAccessible = true }

                when {
                    value == null -> {
                        if (field.getAnnotation(Attribute::class.java)?.canBeNull == true
                            && !field.isAnnotationPresent(ThisIsNotNull::class.java)) {

                            field.set(ref, null)
                        }
                    }
                    Collection::class.java.isAssignableFrom(field.type) -> {
                        if (value !is Collection<*>) { continue@loop }

                        val pt = field.genericType as ParameterizedType
                        field.set(ref, collectionFromMap(
                            pt,
                            value as MutableCollection<Any?>,
                            pt.rawType as Class<MutableCollection<Any?>>
                        ))
                    }
                    MutableMap::class.java.isAssignableFrom(field.type) -> {
                        if (value !is MutableMap<*, *>) { continue@loop }

                        val pt = field.genericType as ParameterizedType
                        field.set(ref, mapFromMap(
                            pt,
                            value as MutableMap<String, Any?>,
                            pt.rawType as Class<MutableMap<String, Any?>>
                        ))
                    }
                    FireStoreORM::class.java.isAssignableFrom(field.type) -> {
                        if (value !is MutableMap<*, *>) { continue@loop }

                        var typeClass: Class<*>? = null
                        if (field.genericType is ParameterizedType) {
                            val type = field.genericType as ParameterizedType
                            typeClass = type.actualTypeArguments[0] as Class<*>
                        }

                        val fireStoreORM: FireStoreORM<*> = field.type.newInstance() as FireStoreORM<*>

                        field.set(
                            ref,
                            fromMap(value as MutableMap<String, Any?>, typeClass, fireStoreORM)
                        )
                    }
                    else -> field.set(ref, manageDefinedTypes(field.type, value))
                }
            }

            return ref
        }

        @JvmStatic
        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun <T: FireStoreORM<*>> toMap(ref: T): MutableMap<String, Any?> {
            if (ref.collection?.valueInObject != true) {
                return ref.valueInHashMap?.let {
                    it.isAccessible = true
                    it.get(ref) as MutableMap<String, Any?>?
                } ?: mutableMapOf()
            }

            val map: MutableMap<String, Any?> = mutableMapOf()

            ref.fieldId?.let {
                it.isAccessible = true
                it.set(ref, ref.id)
            }

            loop@ for (entry in ref.attributes) {
                val field = entry.value.apply { isAccessible = true }

                if (field.getAnnotation(Attribute::class.java)?.readOnly == true
                    || field.isAnnotationPresent(ReadOnly::class.java)) {

                    continue@loop
                }

                when {
                    field.get(ref) == null && !Timestamp::class.java.isAssignableFrom(field.type) -> {
                        map[entry.key] =
                            when (field.getAnnotation(Attribute::class.java)?.ifNullDelete) {
                                true -> FieldValue.delete()
                                else -> null
                            }
                    }
                    Collection::class.java.isAssignableFrom(field.type) -> {
                        field.get(ref)?.let {
                            map[entry.key] = collectionToMap(it as MutableCollection<Any?>)
                        }
                    }
                    MutableMap::class.java.isAssignableFrom(field.type) -> {
                        field.get(ref)?.let {
                            map[entry.key] = mapToMap(it as MutableMap<String, Any?>)
                        }
                    }
                    FireStoreORM::class.java.isAssignableFrom(field.type) -> {
                        map[entry.key] = toMap(field.get(ref) as FireStoreORM<*>)
                    }
                    Timestamp::class.java.isAssignableFrom(field.type) -> {
                        val ta = field.getAnnotation(TimestampAction::class.java)
                        if (ta == null) {
                            field.get(ref)?.let { map[entry.key] = field.get(ref) }
                        } else if ((ta.create && field.get(ref) == null) || ta.update) {
                            map[entry.key] = FieldValue.serverTimestamp()
                            field.set(ref, Timestamp(Date()))
                        }
                    }
                    else -> map[entry.key] = field.get(ref)
                }
            }

            return map
        }

        @JvmStatic
        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun <T: FireStoreORM<*>> getValueInField(ref: T, fieldName: String): Any? {

            fun accessingFieldsMap(
                map: MutableMap<*, *>,
                fields: List<String>
            ): Pair<Any?, List<String>> {
                if (fields.isEmpty()) { return Pair(map, fields) }

                val aAny = map[fields[0]] ?: return Pair(null, fields)
                val aFields = fields.subList(1, fields.size)

                return if (aAny is MutableMap<*, *> && fields.size > 1) {
                    accessingFieldsMap(aAny, aFields)
                } else { Pair(aAny, aFields) }
            }

            fun <E: FireStoreORM<*>> accessingFireStoreORM(
                ref: E,
                fields: List<String>
            ): Pair<Any?, List<String>> {
                if (fields.isEmpty()) { return Pair(ref, fields) }

                val aField: Field = ref.attributes[fields[0]] ?: return Pair(null, fields)
                val aFields = fields.subList(1, fields.size)

                aField.isAccessible = true

                val aAny = aField.get(ref) ?: return Pair(null, fields)
                return if (aAny is FireStoreORM<*> && fields.size > 1) {
                    accessingFireStoreORM(aAny, aFields)
                } else { Pair(aAny, aFields) }
            }

            var fields = fieldName.split(".")
            val field: Field = ref.attributes[fields[0]] ?: return null
            fields = fields.subList(1, fields.size)

            field.isAccessible = true
            var any: Any? = field.get(ref) ?: return null

            loop@ while (true) {
                when (any) {
                    is MutableMap<*, *> -> {
                        if (fields.isEmpty()) { break@loop }

                        val pair = accessingFieldsMap(any, fields)
                        fields = pair.second
                        any = pair.first
                    }
                    is FireStoreORM<*> -> {
                        if (fields.isEmpty()) {
                            any = toMap(any)
                            break@loop
                        }

                        val pair = accessingFireStoreORM(any, fields)
                        fields = pair.second
                        any = pair.first
                    }
                    else -> { break@loop }
                }
            }

            return valueToMap(any)
        }

        @JvmStatic
        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun <E: MutableMap<String, Any?>> mapFromMap(
            pt: ParameterizedType,
            map: MutableMap<String, Any?>,
            mapClazz: Class<MutableMap<String, Any?>>
        ): E {
            val cMap = if (mapClazz == Map::class.java) {
                mutableMapOf<String, Any?>()
            } else {
                mapClazz.newInstance() as E
            }

            val ptAndClazz = Utils.getParameterizedTypeArgumentPosition(pt, 1)
            val cPt = ptAndClazz?.first ?: pt
            val clazz: Class<*> = ptAndClazz?.second ?: Class::class.java

            for (entry in map) {
                entry.value?.let { cMap[entry.key] = valueFromMap(it, clazz, cPt) }
            }

            return cMap as E
        }

        @JvmStatic
        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun <E: MutableCollection<Any?>> collectionFromMap(
            pt: ParameterizedType,
            collection: MutableCollection<Any?>,
            collectionClazz: Class<MutableCollection<Any?>>
        ): E {
            val cCollection = when (collectionClazz) {
                MutableCollection::class.java, List::class.java -> mutableListOf<Any?>()
                Set::class.java -> mutableSetOf<Any?>()
                SortedSet::class.java, NavigableSet::class.java -> TreeSet<Any?>()
                Deque::class.java, Queue::class.java -> ArrayDeque<Any?>()
                else -> collectionClazz.newInstance() as E
            }

            val ptAndClazz = Utils.getParameterizedTypeArgumentPosition(pt, 0)
            val cPt = ptAndClazz?.first ?: pt
            val clazz: Class<*> = ptAndClazz?.second ?: Class::class.java

            for (i in collection) {
                i?.let { cCollection.add(valueFromMap(it, clazz, cPt)) }
            }

            return cCollection as E
        }

        @JvmStatic
        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun mapToMap(map: MutableMap<String, Any?>): MutableMap<String, Any?> {
            val cMap: MutableMap<String, Any?> = mutableMapOf()

            for (entry in map) {
                cMap[entry.key] = valueToMap(entry.value)
            }

            return cMap
        }

        @JvmStatic
        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun collectionToMap(collection: MutableCollection<Any?>): MutableCollection<Any?> {
            val cCollection: MutableCollection<Any?> = mutableListOf()

            for (i in collection) {
                cCollection.add(valueToMap(i))
            }

            return cCollection
        }

        @JvmStatic
        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun valueToMap(any: Any?): Any? = when (any) {
            is FireStoreORM<*> -> toMap(any)
            is MutableMap<*, *> -> mapToMap(any as MutableMap<String, Any?>)
            is MutableCollection<*> -> collectionToMap(any as MutableCollection<Any?>)
            else -> any
        }

        @JvmStatic
        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun valueFromMap(any: Any, clazz: Class<*>, pt: ParameterizedType? = null): Any? = when {
            FireStoreORM::class.java.isAssignableFrom(clazz) -> {
                fromMap(
                    any as MutableMap<String, Any?>,
                    null,
                    clazz.newInstance() as FireStoreORM<*>
                )
            }
            MutableMap::class.java.isAssignableFrom(clazz) && pt != null -> {
                mapFromMap(
                    pt,
                    any as MutableMap<String, Any?>,
                    clazz as Class<MutableMap<String, Any?>>
                )
            }
            MutableCollection::class.java.isAssignableFrom(clazz) && pt != null -> {
                collectionFromMap(
                    pt,
                    any as MutableCollection<Any?>,
                    clazz as Class<MutableCollection<Any?>>
                )
            }
            else -> manageDefinedTypes(clazz, any)
        }

        @JvmStatic
        @Throws(Exception::class)
        @Suppress("UNCHECKED_CAST")
        fun manageDefinedTypes(clazz: Class<*>?, any: Any?): Any? = when {
            clazz == null -> null
            Timestamp::class.java.isAssignableFrom(clazz) -> {
                when (any) {
                    is Timestamp -> any
                    is Long -> Timestamp(Date(any * 1000L))
                    is Int -> Timestamp(Date(any.toLong() * 1000L))
                    is MutableMap<*, *> -> {
                        val map = any as MutableMap<String, Any?>
                        val second: Long? = Utils.convertInLong(map["_second"])
                            ?: Utils.convertInLong(map["_seconds"])

                        val nanoSeconds: Int? = Utils.convertInInt(map["_nanoseconds"])

                        if (second != null && nanoSeconds != null) {
                            Timestamp(second, nanoSeconds)
                        } else { null }
                    }
                    else -> null
                }
            }
            (clazz == Long::class.java || clazz == Long::class.javaObjectType)
                    && any is Number -> {

                Utils.convertInLong(any)
            }
            (clazz == Double::class.java || clazz == Double::class.javaObjectType)
                    && any is Number -> {

                Utils.convertInDouble(any)
            }
            (clazz == Float::class.java || clazz == Float::class.javaObjectType)
                    && any is Number -> {

                Utils.convertInFloat(any)
            }
            (clazz == Int::class.java || clazz == Int::class.javaObjectType)
                    && any is Number -> {

                Utils.convertInInt(any)
            }
            clazz == Boolean::class.java || clazz == Boolean::class.javaObjectType -> {
                if (any !is Boolean) { false }
                else  { any }
            }
            else -> any
        }
    }
}
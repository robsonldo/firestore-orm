package br.com.robsonldo.library

import android.util.Log
import bolts.Continuation
import bolts.Task
import bolts.TaskCompletionSource
import br.com.robsonldo.library.annotations.AttributeReference
import br.com.robsonldo.library.exceptions.FireStoreORMException
import br.com.robsonldo.library.interfaces.OnCompletion
import br.com.robsonldo.library.interfaces.OnCompletionAll
import br.com.robsonldo.library.interfaces.OnInclude
import br.com.robsonldo.library.interfaces.OnIncludeAll
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType

class Include private constructor() {

    companion object {
        private const val TAG = "Include"

        @JvmStatic
        @JvmOverloads
        fun <T: FireStoreORM<out T>> objectGetIncludeAllReferences(
            fireStoreORM: T,
            onCompletion: OnCompletion<T>,
            validateValuesInAnnotation: Boolean = false
        ) {
            fireStoreORM.includeAll(object : OnIncludeAll {
                override fun onSuccess() {
                    onCompletion.onSuccess(fireStoreORM)
                }

                override fun onError(e: Exception) {
                    onCompletion.onError(e)
                }
            }, validateValuesInAnnotation)
        }

        @JvmStatic
        @JvmOverloads
        fun <T: FireStoreORM<out T>> collectionIncludeAll(
            collection: Collection<T>,
            onIncludeAll: OnIncludeAll,
            validateValuesInAnnotation: Boolean = false
        ) {
            if (collection.isEmpty()) { return onIncludeAll.onSuccess() }

            Task.call<Void> { null }.continueWithTask(Continuation<Void, Task<Void>> {
                val tasks: MutableList<Task<Void>> = mutableListOf()

                for (fso in collection) {
                    val tcs = TaskCompletionSource<Void>()
                    tasks.add(tcs.task)

                    includeAllReferences(fso, object : OnIncludeAll {
                        override fun onSuccess() {
                            tcs.setResult(null)
                        }

                        override fun onError(e: Exception) {
                            tcs.setError(e)
                        }
                    }, validateValuesInAnnotation)
                }

                return@Continuation Task.whenAll(tasks)
            }).continueWith { task ->
                when (task.error) {
                    null -> { onIncludeAll.onSuccess() }
                    else -> { onIncludeAll.onError(task.error) }
                }
            }
        }

        @JvmStatic
        @JvmOverloads
        fun <T: FireStoreORM<out T>> includeAllReferences(
            objIncludeAll: T,
            onIncludeAll: OnIncludeAll,
            validateValuesInAnnotation: Boolean = false
        ) {
            if (objIncludeAll.attributesReferences.isEmpty()) { return onIncludeAll.onSuccess() }

            Task.call<Void> { null }.continueWithTask(Continuation<Void, Task<Void>> {
                val tasks: MutableList<Task<Void>> = mutableListOf()

                for (entry in objIncludeAll.attributesReferences) {
                    val tcs = TaskCompletionSource<Void>()
                    tasks.add(tcs.task)

                    includeReference(objIncludeAll, entry.key, object : OnInclude {
                        override fun onSuccess() {
                            tcs.setResult(null)
                        }

                        override fun onError(e: Exception) {
                            tcs.setError(e)
                        }
                    }, validateValuesInAnnotation)
                }

                return@Continuation Task.whenAll(tasks)
            }).continueWith { task ->
                when (task.error) {
                    null -> { onIncludeAll.onSuccess() }
                    else -> { onIncludeAll.onError(task.error) }
                }
            }
        }

        @JvmStatic
        @JvmOverloads
        fun <T: FireStoreORM<out T>> includeReference(
            objInclude: T,
            referenceName: String,
            onInclude: OnInclude,
            validateValuesInAnnotation: Boolean = true
        ) {
            val field = objInclude.attributesReferences[referenceName]
                ?: return onInclude.onError(FireStoreORMException("ReferenceName ($referenceName) not found"))

            val are: AttributeReference = field.getAnnotation(AttributeReference::class.java)
                ?: return onInclude.onError(FireStoreORMException("Annotation not found"))

            val path = try {
                managePath(objInclude, are)
            } catch (e: Exception) {
                Log.e(TAG, e.message ?: "Error path.")

                return when (validateValuesInAnnotation) {
                    true -> onInclude.onError(e)
                    else -> onInclude.onSuccess()
                }
            }

            when {
                FireStoreORM::class.java.isAssignableFrom(field.type) -> {
                    getReferenceTypeFireStoreORM(
                        objInclude,
                        field,
                        path,
                        are,
                        onInclude,
                        validateValuesInAnnotation
                    )
                }
                Collection::class.java.isAssignableFrom(field.type) -> {
                    getReferenceTypeCollectionOfFireStoreORM(
                        objInclude,
                        field,
                        path,
                        are,
                        onInclude,
                        validateValuesInAnnotation
                    )
                }
                else -> {
                    onInclude.onError(FireStoreORMException("Reference Object not accepted"))
                }
            }
        }

        @JvmStatic
        @JvmOverloads
        fun <T: FireStoreORM<out T>> getReferenceTypeFireStoreORM(
            objInclude: T,
            fieldReference: Field,
            path: String?,
            are: AttributeReference,
            onInclude: OnInclude,
            validateValuesInAnnotation: Boolean = true
        ) {
            val id = try {
                getAttributeWithId(objInclude, are)
            } catch (e: Exception) {
                Log.e(TAG, e.message ?: "Id error")

                return when (validateValuesInAnnotation) {
                    true -> onInclude.onError(e)
                    else -> onInclude.onSuccess()
                }
            }

            fieldReference.isAccessible = true

            val fireStoreORM = (fieldReference.type.newInstance() as FireStoreORM<*>)
            path?.also { fireStoreORM.path = path }

            fireStoreORM.apply { this.id = id }.get(object : OnCompletion<FireStoreORM<*>> {
                override fun onSuccess(obj: FireStoreORM<*>) {
                    try {
                        fieldReference.set(objInclude, obj)
                        onInclude.onSuccess()
                    } catch (e: Exception) {
                        onInclude.onError(e)
                    }
                }

                override fun onError(e: Exception) {
                    onInclude.onError(e)
                }
            })
        }

        @JvmStatic
        @JvmOverloads
        fun <T: FireStoreORM<out T>> getReferenceTypeCollectionOfFireStoreORM(
            objInclude: T,
            fieldReference: Field,
            path: String?,
            are: AttributeReference,
            onInclude: OnInclude,
            validateValuesInAnnotation: Boolean = true
        ) {
            val id = try {
                getAttributeWithId(objInclude, are, false)
            } catch (e: Exception) {
                Log.e(TAG, e.message ?: "Id error")

                return when (validateValuesInAnnotation) {
                    true -> onInclude.onError(e)
                    else -> onInclude.onSuccess()
                }
            }

            fieldReference.isAccessible = true

            val pt = fieldReference.genericType as ParameterizedType
            val ptAndClazz = Utils.getParameterizedTypeArgumentPosition(pt, 0)
                ?: Pair(pt, Class::class.java)

            if (!FireStoreORM::class.java.isAssignableFrom(ptAndClazz.second)) {
                return onInclude.onError(
                    FireStoreORMException("Type in collection is not FireStoreORM Object")
                )
            }

            val fireStoreORM = (ptAndClazz.second.newInstance() as FireStoreORM<*>)
            path?.also { fireStoreORM.path = path }

            fireStoreORM.apply { if (id.isNotEmpty()) { this.id = id } }
                .all(object : OnCompletionAll<FireStoreORM<*>> {
                    override fun onSuccess(objs: MutableList<FireStoreORM<*>>) {
                        try {
                            fieldReference.set(objInclude, objs)
                            onInclude.onSuccess()
                        } catch (e: Exception) {
                            onInclude.onError(e)
                        }
                    }

                    override fun onError(e: Exception) {
                        onInclude.onError(e)
                    }
                })
        }

        @JvmStatic
        @JvmOverloads
        @Throws(Exception::class)
        fun <T: FireStoreORM<out T>> getAttributeWithId(
            objInclude: T,
            are: AttributeReference,
            isMandatory: Boolean = true
        ): String {
            if (are.attributeWithId.isEmpty() && !isMandatory) { return "" }

            val fieldWithId: Field = objInclude.attributes[are.attributeWithId]
                ?: throw FireStoreORMException("FieldWithId (${are.attributeWithId}) not found")

            if (!String::class.java.isAssignableFrom(fieldWithId.type)) {
                throw FireStoreORMException("FieldWithId (${are.attributeWithId}) is not a String")
            }

            fieldWithId.isAccessible = true
            return fieldWithId.get(objInclude) as String?
                ?: throw FireStoreORMException("FieldWithId (${are.attributeWithId}) is null")
        }

        @JvmStatic
        @Throws(Exception::class)
        fun <T: FireStoreORM<out T>> managePath(objCurrent: T, are: AttributeReference): String? {
            if (are.collection.isEmpty()) { return null }

            val values: Array<Any> = are.paramsAttributes.map {
                DataParse.getValueInField(objCurrent, it)
                    ?: throw FireStoreORMException("Param attribute: $it is null")
            }.toTypedArray()

            val pathNotFormed = String.format("%s%s", are.collection, are.path)
            return String.format(pathNotFormed, *values)
        }
    }
}
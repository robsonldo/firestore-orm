package br.com.robsonldo.library

import br.com.robsonldo.library.annotations.*
import br.com.robsonldo.library.annotations.Collection
import br.com.robsonldo.library.exceptions.FireStoreORMException
import br.com.robsonldo.library.interfaces.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.firestore.*
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.collections.HashMap
import kotlin.collections.Map
import kotlin.collections.MutableCollection
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.hashMapOf
import kotlin.collections.isEmpty
import kotlin.collections.mutableListOf
import kotlin.collections.set
import kotlin.collections.toTypedArray

typealias Get<T> = (obj: T) -> Unit
typealias OnGet<T> = (obj: T?) -> Unit
typealias OnAll<T> = (obj: T, type: DocumentChange.Type) -> Unit
typealias OnAllInit<T> = (objects: MutableList<T>) -> Unit
typealias All<T> = (obj: MutableList<T>) -> Unit
typealias Error = (e: Exception?) -> Unit

abstract class FireStoreORM<T : FireStoreORM<T>> {

    var id: String = ""
        get() {
            field = if (field != "") field else generateKey()
            return field
        }

    var path: String = ""
        internal set

    var mapValue: Map<String, Any>? = null
        internal set

    var wasFound: Boolean = false
        internal set

    var params: Array<String> = arrayOf()
        set(value) {
            field = value
            initPath()
        }

    @Transient private val database: FirebaseFirestore = FirebaseFirestore.getInstance()
    @Transient val collection: Collection? = getACollection()

    @Transient val documentSnapshotSave: DocumentSnapshotSave? = getADocumentSnapshotSave()
    @Transient val typeSource: Source = getATypeSource()

    @Transient var valueInHashMap: Field? = null
        internal set

    @Transient var fieldId: Field? = null
        internal set

    @Transient val attributes: HashMap<String, Field> = hashMapOf()
    @Transient val attributesReferences: HashMap<String, Field> = hashMapOf()

    @Transient private val onListenerRegistrations: MutableCollection<ListenerRegistration> = mutableListOf()

    @Transient var documentSnapshot: DocumentSnapshot? = null
        internal set

    init {
        initPath()
        persisted()
        managerFields(this::class.java)
    }

    @JvmOverloads
    @Suppress("UNCHECKED_CAST")
    open fun get(onCompletion: OnCompletion<T>, isIncludeAll: Boolean = false) {
        if (!validate { e -> onCompletion.onError(e) }) { return }

        try {
            getCollectionReference()
                .document(id)
                .get(typeSource)
                .addOnSuccessListener { snap ->
                    try {
                        val t = DataParse.documentSnapshotInObject(snap, this as T)

                        if (isIncludeAll) {
                            Include.objectGetIncludeAllReferences(t, onCompletion)
                        } else {
                            onCompletion.onSuccess(t)
                        }
                    } catch (e: Exception) {
                        onCompletion.onError(e)
                    }
                }
                .addOnFailureListener { e -> onCompletion.onError(e) }
        } catch (e: Exception) {
            onCompletion.onError(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    open fun onGet(onListenerGet: OnListenerGet<T>) {
        if (!validate(true) { e -> onListenerGet.onError(e) }) return

        try {
            val registration: ListenerRegistration = getCollectionReference()
                .document(id)
                .addSnapshotListener { snap, e ->
                    if (e != null) { return@addSnapshotListener onListenerGet.onError(e) }

                    if (snap != null && snap.exists()) {
                        try {
                            onListenerGet.onListener(
                                DataParse.documentSnapshotInObject(
                                    snap,
                                    this as T
                                )
                            )
                        } catch (e1: Exception) {
                            onListenerGet.onError(e1)
                        }
                    } else {
                        onListenerGet.onListener(null)
                    }
                }

            onListenerRegistrations.add(registration)
        } catch (e: Exception) {
            onListenerGet.onError(e)
        }
    }

    open fun all(onCompletionAll: OnCompletionAll<T>) {
        try {
            findAllQuery(getCollectionReference(), onCompletionAll)
        } catch (e: Exception) {
            onCompletionAll.onError(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    open fun findAllQuery(query: Query, onCompletionAll: OnCompletionAll<T>) {
        if (!validate(false) { e -> onCompletionAll.onError(e) }) { return }

        val list: MutableList<T> = mutableListOf()

        query
            .get(typeSource)
            .addOnCompleteListener { task ->
                when {
                    !task.isSuccessful -> {
                        onCompletionAll.onError(task.exception ?: Exception())
                    }
                    task.result == null -> { onCompletionAll.onSuccess(list) }
                    else -> {
                        for (snap in task.result!!) {
                            try {
                                list.add(
                                    DataParse.documentSnapshotInObject(
                                        snap,
                                        this::class.java.newInstance() as T
                                    ).apply { managerObjectByCaller(this@FireStoreORM) }
                                )
                            } catch (e: Exception) {
                                return@addOnCompleteListener onCompletionAll.onError(e)
                            }
                        }

                        onCompletionAll.onSuccess(list)
                    }
                }
            }
    }

    open fun onAll(onListenerAll: OnListenerAll<T>) {
        try {
            onFindAllQuery(getCollectionReference(), onListenerAll)
        } catch (e: Exception){
            onListenerAll.onError(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    open fun onFindAllQuery(query: Query, onListenerAll: OnListenerAll<T>) {
        if (!validate { e -> onListenerAll.onError(e) }) { return }

        var isInit = true
        val objects: MutableList<T> = mutableListOf()

        var registration: ListenerRegistration? = null
        registration = query
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    onListenerAll.onError(e)
                    return@addSnapshotListener removeListenerRegistration(registration)
                }

                if (snap == null) { return@addSnapshotListener }

                if (isInit) {
                    isInit = false

                    loop@ for (dc in snap.documentChanges) {
                        if (dc.type != DocumentChange.Type.ADDED) { continue@loop }

                        try {
                            objects.add(
                                DataParse.documentSnapshotInObject(
                                    dc.document,
                                    this::class.java.newInstance() as T
                                ).apply { managerObjectByCaller(this@FireStoreORM) }
                            )

                        } catch (e: Exception) {
                            removeListenerRegistration(registration)
                            return@addSnapshotListener onListenerAll.onError(e)
                        }
                    }

                    return@addSnapshotListener onListenerAll.onInit(objects)
                }

                loop@ for (dc in snap.documentChanges) {
                    var t: T

                    try {
                        t = this::class.java.newInstance() as T
                        t = DataParse.documentSnapshotInObject(dc.document, t).apply {
                            managerObjectByCaller(this@FireStoreORM)
                        }
                    } catch (e: Exception) {
                        removeListenerRegistration(registration)
                        return@addSnapshotListener onListenerAll.onError(e)
                    }

                    when (dc.type) {
                        DocumentChange.Type.ADDED -> { onListenerAll.onAdded(t) }
                        DocumentChange.Type.MODIFIED -> { onListenerAll.onModified(t) }
                        DocumentChange.Type.REMOVED -> { onListenerAll.onRemoved(t) }
                    }
                }
            }

        onListenerRegistrations.add(registration)
    }

    @JvmOverloads
    open fun save(merge: Boolean = true, onCompletion: OnCompletion<T>? = null) {
        prepareToSave(merge, onCompletion)
    }

    private fun prepareToSave(merge: Boolean = true, onCompletion: OnCompletion<T>? = null) {
        if (!validate { e -> onCompletion?.onError(e) }) { return }

        try {
            val df = getCollectionReference().document(id)
            save(DataParse.toMap(this), df, merge, onCompletion)
        } catch (e: Exception) {
            onCompletion?.onError(e)
        }
    }

    @Throws(Exception::class)
    @Suppress("UNCHECKED_CAST")
    private fun save(
        map: MutableMap<String, Any?>,
        df: DocumentReference,
        merge: Boolean = true,
        onCompletion: OnCompletion<T>? = null
    ) {

        val onSuccessListener = OnSuccessListener<Void> { onCompletion?.onSuccess(this as T) }
        val onFailureListener = OnFailureListener { e -> onCompletion?.onError(e) }

        if (merge) {
            df
                .set(map, SetOptions.merge())
                .addOnSuccessListener(onSuccessListener)
                .addOnFailureListener(onFailureListener)

        } else {
            df
                .set(map)
                .addOnSuccessListener(onSuccessListener)
                .addOnFailureListener(onFailureListener)
        }
    }

    @JvmOverloads
    @Suppress("UNCHECKED_CAST")
    fun updateIncrement(
        vararg fieldsAndIncrements: Pair<String, Long>,
        onCompletion: OnCompletion<T>? = null
    ) {
        if (!validateUpdateFields(
                *fieldsAndIncrements.filter { it.second != 0L }.map { it.first }.toTypedArray()
            ) { e -> onCompletion?.onError(e) }) {
                return
        }

        fun valueInFieldOrFieldIncrement(field: String, inc: Long): Any? {
            return when (inc) {
                0L -> { DataParse.getValueInField(this, field) }
                else -> { FieldValue.increment(inc) }
            }
        }

        try {
            val fieldsAndValues: MutableList<Any?> = mutableListOf()
            val df = getCollectionReference().document(id)

            val field = fieldsAndIncrements[0].first
            val value = valueInFieldOrFieldIncrement(field, fieldsAndIncrements[0].second)

            for (i in 1 until fieldsAndIncrements.size) {
                fieldsAndValues.add(fieldsAndIncrements[i].first)
                fieldsAndValues.add(
                    valueInFieldOrFieldIncrement(
                        fieldsAndIncrements[i].first,
                        fieldsAndIncrements[i].second
                    )
                )
            }

            df.update(field, value, *fieldsAndValues.toTypedArray())
                .addOnSuccessListener { onCompletion?.onSuccess(this as T) }
                .addOnFailureListener { e -> onCompletion?.onError(e) }

        } catch (e: Exception) {
            onCompletion?.onError(e)
        }
    }

    @JvmOverloads
    @Suppress("UNCHECKED_CAST")
    fun updateFieldValue(vararg fields: String, onCompletion: OnCompletion<T>? = null) {
        if (!validateUpdateFields(*fields) { e -> onCompletion?.onError(e) }) { return }

        try {
            val fieldsAndValues: MutableList<Any?> = mutableListOf()
            val df = getCollectionReference().document(id)

            val field = fields[0]
            val value: Any? = DataParse.getValueInField(this, fields[0])

            for (i in 1 until fields.size) {
                fieldsAndValues.add(fields[i])
                fieldsAndValues.add(DataParse.getValueInField(this, fields[i]))
            }

            df.update(field, value, *fieldsAndValues.toTypedArray())
                .addOnSuccessListener { onCompletion?.onSuccess(this as T) }
                .addOnFailureListener { e -> onCompletion?.onError(e) }

        } catch (e: Exception) {
            onCompletion?.onError(e)
        }
    }

    @JvmOverloads
    @Suppress("UNCHECKED_CAST")
    open fun delete(onCompletion: OnCompletion<T>? = null) {
        if (!validate { e -> onCompletion?.onError(e) }) { return }

        try {
            getCollectionReference()
                .document(id)
                .delete()
                .addOnSuccessListener { onCompletion?.onSuccess(this as T) }
                .addOnFailureListener { e -> onCompletion?.onError(e) }
        } catch (e: Exception) {
            onCompletion?.onError(e)
        }
    }

    @JvmOverloads
    open fun include(
        referenceName: String,
        onInclude: OnInclude? = null,
        validateValuesInAnnotation: Boolean = true
    ) {
        Include.includeReference(this, referenceName, object : OnInclude {
            override fun onSuccess() {
                onInclude?.onSuccess()
            }

            override fun onError(e: Exception) {
                onInclude?.onError(e)
            }
        }, validateValuesInAnnotation)
    }

    @JvmOverloads
    open fun includeAll(
        onIncludeAll: OnIncludeAll? = null,
        validateValuesInAnnotation: Boolean = true
    ) {
        Include.includeAllReferences(this, object : OnIncludeAll {
            override fun onSuccess() {
                onIncludeAll?.onSuccess()
            }

            override fun onError(e: Exception) {
                onIncludeAll?.onError(e)
            }
        }, validateValuesInAnnotation)
    }

    private fun generateKey(): String {
        return fieldId?.let { field ->
            field.isAccessible = true
            field.get(this) as String?
        } ?: getCollectionReference().document().id
    }

    private fun validate(verifyId: Boolean = true, exception: (e: Exception) -> Unit) = when {
        getACollection() == null -> {
            exception(FireStoreORMException("${getClassName()}: Collection not defined"))
            false
        }
        path.trim() == "" -> {
            exception(FireStoreORMException("${getClassName()}: Collection name is null or empty"))
            false
        }
        verifyId && id == "" -> {
            exception(FireStoreORMException("${getClassName()}: Id is null"))
            false
        }
        else -> { true }
    }

    private fun validateUpdateFields(
        vararg fields: String,
        exception: (e: Exception) -> Unit
    ) = when {
        !validate(true) { e -> exception(e) } -> { false }
        fields.isEmpty() -> {
            exception(FireStoreORMException("${getClassName()}: Fields is empty"))
            false
        }
        else -> { true }
    }

    fun getClassName() = this::class.java.name

    @Throws(Exception::class)
    fun getCollectionReference(): CollectionReference {
        return database.collection(path)
    }

    fun getACollection(): Collection? = this::class.java.getAnnotation(Collection::class.java)

    fun getADocumentSnapshotSave(): DocumentSnapshotSave? {
        return this::class.java.getAnnotation(DocumentSnapshotSave::class.java)
    }

    fun getATypeSource(): Source {
        return this::class.java.getAnnotation(TypeSource::class.java)?.value ?: Source.DEFAULT
    }

    private fun persisted() {
        val persisted = this::class.java.getAnnotation(Persisted::class.java)
        database.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(persisted?.value ?: false)
            .build()
    }

    private fun initPath() {
        path = collection?.value ?: ""
        if (collection?.params.equals("") || params.isEmpty()) { return }
        path = String.format(String.format("%s%s", path, collection?.params), *params)
    }

    fun managerObjectByCaller(caller: FireStoreORM<T>) {
        path = caller.path
    }

    private fun managerFields(clazz: Class<*>) {
        val fields = clazz.declaredFields

        loop@ for (field in fields) {
            when {
                fieldNotValid(field) -> { continue@loop }
                field.isAnnotationPresent(Attribute::class.java) -> {
                    val attribute: Attribute? = field.getAnnotation(Attribute::class.java)
                    if (attribute != null) { attributes[attribute.value] = field }
                    verifyAnnotationId(field)
                }
                field.isAnnotationPresent(ValueHashMap::class.java) -> {
                    valueInHashMap = field
                }
                field.isAnnotationPresent(AttributeReference::class.java) -> {
                    field.getAnnotation(AttributeReference::class.java)?.also {
                        attributesReferences[it.referenceName] = field
                    }
                }
                else -> {
                    attributes[field.name] = field
                    verifyAnnotationId(field)
                }
            }
        }

        val superClazz = clazz.superclass
        if (superClazz != clazz) { superClazz?.isAssignableFrom(FireStoreORM::class.java) }
    }

    private fun verifyAnnotationId(field: Field) {
        if (field.isAnnotationPresent(Id::class.java)
            && String::class.java.isAssignableFrom(field.type)) {

            fieldId = field
        }
    }

    private fun fieldNotValid(field: Field): Boolean {
        return field.isAnnotationPresent(Ignore::class.java) || Modifier.isTransient(field.modifiers)
                || Modifier.isStatic(field.modifiers) || Modifier.isFinal(field.modifiers)
    }

    fun removeAllListenerRegistrations() {
        for (registration in onListenerRegistrations) {
            removeListenerRegistration(registration)
        }

        onListenerRegistrations.clear()
    }

    private fun removeListenerRegistration(registration: ListenerRegistration?) {
        registration?.also {
            it.remove()
            onListenerRegistrations.remove(it)
        }
    }

    open fun save(merge: Boolean = true, get: Get<T>, error: Error) {
        save(merge, object : OnCompletion<T> {
            override fun onSuccess(obj: T) = get(obj)
            override fun onError(e: Exception) = error(e)
        })
    }

    open fun get(get: Get<T>, error: Error) {
        get(object : OnCompletion<T> {
            override fun onSuccess(obj: T) = get(obj)
            override fun onError(e: Exception) = error(e)
        })
    }

    open fun onGet(onGet: OnGet<T>, error: Error) {
        onGet(object : OnListenerGet<T> {
            override fun onListener(obj: T?) = onGet(obj)
            override fun onError(e: Exception) = error(e)
        })
    }

    open fun all(all: All<T>, error: Error) {
        try {
            findAllQuery(getCollectionReference(), all, error)
        } catch (e: Exception) {
            error(e)
        }
    }

    open fun findAllQuery(query: Query, all: All<T>, error: Error) {
        findAllQuery(query, object : OnCompletionAll<T> {
            override fun onError(e: Exception) = error(e)
            override fun onSuccess(objs: MutableList<T>) = all(objs)
        })
    }

    open fun onAll(onAllInit: OnAllInit<T>, onAll: OnAll<T>, error: Error) {
        try {
            onFindAllQuery(getCollectionReference(), onAllInit, onAll, error)
        } catch (e: Exception) {
            error(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    open fun onFindAllQuery(query: Query, onAllInit: OnAllInit<T>, onAll: OnAll<T>, error: Error) {
        onFindAllQuery(query, object: OnListenerAll<T> {
            override fun onInit(objects: MutableList<T>) = onAllInit(objects)
            override fun onAdded(obj: T) = onAll(obj, DocumentChange.Type.ADDED)
            override fun onModified(obj: T) = onAll(obj, DocumentChange.Type.MODIFIED)
            override fun onRemoved(obj: T) = onAll(obj, DocumentChange.Type.REMOVED)
            override fun onError(e: Exception) = error(e)
        })
    }
}
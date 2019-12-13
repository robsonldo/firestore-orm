package br.com.robsonldo.library

import br.com.robsonldo.library.exceptions.FireStoreORMException
import br.com.robsonldo.library.interfaces.OnCompletion
import br.com.robsonldo.library.interfaces.OnCompletionAll
import br.com.robsonldo.library.interfaces.OnListenerAll
import br.com.robsonldo.library.interfaces.OnListenerGet
import br.com.robsonldo.library.annotations.*
import br.com.robsonldo.library.annotations.Collection
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.firestore.*
import java.lang.reflect.Field
import java.lang.reflect.Modifier

typealias Get<T> = (obj: T) -> Unit
typealias OnGet<T> = (obj: T?) -> Unit
typealias OnAll<T> = (obj: T, type: DocumentChange.Type) -> Unit
typealias OnAllInit<T> = (objects: MutableList<T>) -> Unit
typealias All<T> = (obj: MutableList<T>) -> Unit
typealias Error = (e: Exception?) -> Unit

abstract class FireStoreORM<T : FireStoreORM<T>> {

    var id: String = ""
        get() { field = if (field != "") field else generateKey(); return field }

    var path: String = ""
        private set

    var mapValue: Map<String, Any>? = null
        private set

    var wasFound: Boolean = false
        internal set

    var params: Array<String> = arrayOf()

    @Transient private val database: FirebaseFirestore = FirebaseFirestore.getInstance()
    @Transient val collection: Collection? = getACollection()
    @Transient val typeSource: Source = getATypeSource()
    @Transient var valueInHashMap: Field? = null
    @Transient var fieldId: Field? = null
    @Transient var attributes: HashMap<String, Field> = hashMapOf()
    @Transient private var attributeCollections: MutableCollection<Pair<Field, AttributeCollection>> = mutableListOf()
    @Transient private val onListenerRegistrations: MutableCollection<ListenerRegistration> = mutableListOf()

    init {
        initPath()
        persisted()
        managerFields(this.javaClass)
    }

    @Suppress("UNCHECKED_CAST")
    open fun get(onCompletion: OnCompletion<T>) {
        if (!validate { e -> onCompletion.onError(e) }) return

        getCollection()
            .document(id)
            .get(typeSource)
            .addOnSuccessListener { snap ->
                try {
                    onCompletion.onSuccess(DataParse.documentSnapshotInObject(snap, this as T))
                } catch (e: Exception) {
                    onCompletion.onError(e)
                }
            }
            .addOnFailureListener { e -> onCompletion.onError(e) }
    }

    @Suppress("UNCHECKED_CAST")
    open fun onGet(onListenerGet: OnListenerGet<T>) {
        if (!validate(true) { e -> onListenerGet.onError(e) }) return

        val registration: ListenerRegistration = getCollection()
            .document(id)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    onListenerGet.onError(e)
                    return@addSnapshotListener
                }

                if (snap != null && snap.exists()) {
                    try {
                        onListenerGet.onListener(DataParse.documentSnapshotInObject(snap, this as T))
                    } catch (e1: Exception) {
                        onListenerGet.onError(e1)
                    }
                } else {
                    onListenerGet.onListener(null)
                }
            }

        onListenerRegistrations.add(registration)
    }

    open fun all(onCompletionAll: OnCompletionAll<T>) = findAllQuery(getCollection(), onCompletionAll)

    @Suppress("UNCHECKED_CAST")
    open fun findAllQuery(query: Query, onCompletionAll: OnCompletionAll<T>) {
        if (!validate(false) { e -> onCompletionAll.onError(e) }) return
        val list: MutableList<T> = mutableListOf()

        query
            .get(typeSource)
            .addOnCompleteListener { task ->
                when {
                    !task.isSuccessful -> onCompletionAll.onError(task.exception ?: Exception())
                    task.result == null -> onCompletionAll.onSuccess(list)
                    else -> {
                        for (snap in task.result!!) {
                            try {
                                list.add(DataParse.documentSnapshotInObject(snap, this as T))
                            } catch (e: Exception) {
                                onCompletionAll.onError(e)
                            }
                        }

                        onCompletionAll.onSuccess(list)
                    }
                }
            }
    }

    open fun onAll(onListenerAll: OnListenerAll<T>) = onFindAllQuery(getCollection(), onListenerAll)

    @Suppress("UNCHECKED_CAST")
    open fun onFindAllQuery(query: Query, onListenerAll: OnListenerAll<T>) {
        if (!validate { e -> onListenerAll.onError(e) }) return

        var isInit = true
        val objects: MutableList<T> = mutableListOf()

        var registration: ListenerRegistration? = null
        registration = getCollection()
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    onListenerAll.onError(e)
                    removeListenerRegistration(registration)
                    return@addSnapshotListener
                }

                if (snap == null) return@addSnapshotListener

                if (isInit) {
                    isInit = false

                    loop@ for (dc in snap.documentChanges) {
                        if (dc.type != DocumentChange.Type.ADDED) continue@loop

                        try {
                            objects.add(DataParse.documentSnapshotInObject(dc.document, this::class.java.newInstance() as T))
                        } catch (e: Exception) {
                            removeListenerRegistration(registration)
                            onListenerAll.onError(e)
                            return@addSnapshotListener
                        }
                    }

                    onListenerAll.onInit(objects)
                    return@addSnapshotListener
                }

                loop@ for (dc in snap.documentChanges) {
                    var t: T

                    try {
                        t = this::class.java.newInstance() as T
                        t = DataParse.documentSnapshotInObject(dc.document, t)
                    } catch (e: Exception) {
                        removeListenerRegistration(registration)
                        onListenerAll.onError(e)
                        return@addSnapshotListener
                    }

                    when (dc.type) {
                        DocumentChange.Type.ADDED -> onListenerAll.onAdded(t)
                        DocumentChange.Type.MODIFIED -> onListenerAll.onModified(t)
                        DocumentChange.Type.REMOVED -> onListenerAll.onRemoved(t)
                    }
                }
            }

        onListenerRegistrations.add(registration)
    }

    open fun save(merge: Boolean = true, onCompletion: OnCompletion<T>? = null) =
        prepareToSave(merge, onCompletion)

    private fun prepareToSave(merge: Boolean = true, onCompletion: OnCompletion<T>? = null) {
        if (!validate { e -> onCompletion?.onError(e) }) return

        try {
            val df = getCollection().document(id)
            save(DataParse.toMap(this), df, merge, onCompletion)
        } catch (e: Exception) {
            onCompletion?.onError(e)
        }
    }

    @Throws(Exception::class)
    @Suppress("UNCHECKED_CAST")
    private fun save(map: MutableMap<String, Any?>, df: DocumentReference, merge: Boolean = true, onCompletion: OnCompletion<T>?) {
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

    private fun generateKey(): String {
        var id: String? = null
        fieldId?.let { it.isAccessible = true; id = it.get(this) as String? }
        id = id ?: getCollection().document().id
        return id as String
    }

    private fun validate(verifyId: Boolean = true, exception: (e: Exception) -> Unit): Boolean {
        return when {
            getACollection() == null -> { exception(FireStoreORMException("${getClassName()}: Collection not defined")); false }
            path.trim() == "" -> { exception(FireStoreORMException("${getClassName()}: Collection name is null or empty")); false }
            verifyId && id == "" -> { exception(FireStoreORMException("${getClassName()}: Id is null")); false }
            else -> true
        }
    }

    private fun getClassName() = this::class.java.name
    private fun getCollection(): CollectionReference = database.collection(path)
    private fun getACollection(): Collection? = this::class.java.getAnnotation(Collection::class.java)

    private fun getATypeSource(): Source = this::class.java.getAnnotation(TypeSource::class.java)?.value ?: Source.DEFAULT

    private fun persisted() {
        val persisted = this::class.java.getAnnotation(Persisted::class.java)
        database.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(persisted?.value ?: false)
            .build()
    }

    private fun initPath() {
        path = collection?.value ?: ""
        if (collection?.params.equals("") || params.isEmpty()) return
        path = String.format(String.format("%s%s", path, collection?.params), params)
    }

    private fun managerFields(clazz: Class<*>) {
        val fields = clazz.declaredFields

        loop@ for (field in fields) {
            when {
                fieldNotValid(field) -> continue@loop
                field.isAnnotationPresent(Attribute::class.java) -> {
                    val attribute: Attribute? = field.getAnnotation(Attribute::class.java)
                    if (attribute != null) attributes[attribute.value] = field
                    verifyAnnotationId(field)
                }
                field.isAnnotationPresent(ValueHashMap::class.java) -> valueInHashMap = field
                field.isAnnotationPresent(AttributeCollection::class.java)
                        && FireStoreORM::class.java.isAssignableFrom(field.type) -> {

                    attributeCollections.add(Pair(field, field.getAnnotation(AttributeCollection::class.java)))
                }
                else -> {
                    attributes[field.name] = field
                    verifyAnnotationId(field)
                }
            }
        }

        val superClazz = clazz.superclass
        if (superClazz != clazz) superClazz?.isAssignableFrom(FireStoreORM::class.java)
    }

    private fun verifyAnnotationId(field: Field) {
        if (field.isAnnotationPresent(Id::class.java) && String::class.java.isAssignableFrom(field.type)) {
            fieldId = field
        }
    }

    private fun fieldNotValid(field: Field): Boolean {
        return field.isAnnotationPresent(Ignore::class.java) || Modifier.isTransient(field.modifiers)
                || Modifier.isStatic(field.modifiers) || Modifier.isFinal(field.modifiers)
    }

    fun removeAllListenerRegistrations() {
        for (registration in onListenerRegistrations) removeListenerRegistration(registration)
        onListenerRegistrations.clear()
    }

    private fun removeListenerRegistration(registration: ListenerRegistration?) {
        registration?.remove()
        onListenerRegistrations.remove(registration)
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

    open fun all(all: All<T>, error: Error) = findAllQuery(getCollection(), all, error)

    open fun findAllQuery(query: Query, all: All<T>, error: Error) {
        findAllQuery(query, object : OnCompletionAll<T> {
            override fun onSuccess(objs: MutableList<T>) = all(objs)
            override fun onError(e: Exception) = error(e)
        })
    }

    open fun onAll(onAllInit: OnAllInit<T>, onAll: OnAll<T>, error: Error) {
        onFindAllQuery(getCollection(), onAllInit, onAll, error)
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
package br.com.robsonldo.library.interfaces

import br.com.robsonldo.library.FireStoreORM
import java.lang.Exception

interface OnListenerAll<T: FireStoreORM<*>> {
    fun onInit(objects: MutableList<T>)
    fun onAdded(obj: T)
    fun onModified(obj: T)
    fun onRemoved(obj: T)
    fun onError(e: Exception)
}
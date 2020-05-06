package br.com.robsonldo.library.interfaces

import br.com.robsonldo.library.FireStoreORM

interface OnListenerGet<in T: FireStoreORM<*>> {
    fun onListener(obj: T?)
    fun onError(e: Exception)
}
package br.com.robsonldo.library.interfaces

import br.com.robsonldo.library.FireStoreORM

interface OnCompletionAll<T: FireStoreORM<*>> {
    fun onSuccess(objs: MutableList<T>)
    fun onError(e: Exception)
}
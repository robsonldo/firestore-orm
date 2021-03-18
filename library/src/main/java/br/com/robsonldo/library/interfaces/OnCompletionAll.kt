package br.com.robsonldo.library.interfaces

import br.com.robsonldo.library.FireStoreORM

interface OnCompletionAll<in T: FireStoreORM<*>> {
    fun onSuccess(objs: MutableList<@UnsafeVariance T>)
    fun onError(e: Exception)
}
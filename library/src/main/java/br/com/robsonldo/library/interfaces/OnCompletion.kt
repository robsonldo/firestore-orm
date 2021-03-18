package br.com.robsonldo.library.interfaces

import br.com.robsonldo.library.FireStoreORM

interface OnCompletion<in T: FireStoreORM<*>> {
    fun onSuccess(obj: @UnsafeVariance T)
    fun onError(e: Exception)
}
package br.com.robsonldo.library.interfaces

import br.com.robsonldo.library.FireStoreORM

interface OnCompletion<in T: FireStoreORM<*>> {
    fun onSuccess(obj: T)
    fun onError(e: Exception)
}
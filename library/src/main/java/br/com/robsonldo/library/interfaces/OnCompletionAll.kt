package br.com.robsonldo.library.interfaces

import br.com.robsonldo.library.FireStoreORM
import java.lang.Exception

interface OnCompletionAll<T: FireStoreORM<*>> {
    fun onSuccess(objs: MutableList<T>)
    fun onError(e: Exception)
}
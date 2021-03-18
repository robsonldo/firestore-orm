package br.com.robsonldo.library.interfaces

interface OnInclude {
    fun onSuccess()
    fun onError(e: Exception)
}
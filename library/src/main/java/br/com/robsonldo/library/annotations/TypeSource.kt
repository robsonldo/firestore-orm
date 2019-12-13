package br.com.robsonldo.library.annotations

import com.google.firebase.firestore.Source

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class TypeSource(val value: Source = Source.DEFAULT)
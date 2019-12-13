package br.com.robsonldo.firestoreorm.model

import br.com.robsonldo.library.FireStoreORM
import br.com.robsonldo.library.annotations.*
import br.com.robsonldo.library.annotations.Collection
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Source

@Persisted(false)
@TypeSource(Source.SERVER)
@Collection("Person")
class Person() : FireStoreORM<Person>() {

    constructor(id: String, vararg params: String) : this() {
        this.id = id
    }

    @Attribute("name")
    var name: String? = null

    @Attribute("lastName")
    var lastName: String? = null

    @Attribute("age")
    var age: Int? = null

    @Id
    @Attribute("id")
    var key: String? = null

    @Attribute("birth")
    var birth: Timestamp? = null

    @Attribute("address")
    var address: Address? = null

    var active: Boolean? = null
}
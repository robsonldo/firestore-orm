package br.com.robsonldo.firestoreorm.model

import br.com.robsonldo.library.FireStoreORM
import br.com.robsonldo.library.annotations.*
import br.com.robsonldo.library.annotations.Collection
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Source

@Persisted(false)
@TypeSource(Source.SERVER)
@Collection("person")
class Person() : FireStoreORM<Person>() {

    constructor(id: String, vararg params: String) : this() {
        this.id = id
        this.params = arrayOf(*params)
    }

    @Attribute("name")
    var name: String? = null

    @Attribute("lastName")
    var lastName: String? = null

    @Attribute("age")
    var age: Int? = null

    @Attribute("luckyNumber")
    var luckyNumber: Int? = null

    @Id
    @Attribute("id")
    var key: String? = null

    @Attribute("birth")
    var birth: Timestamp? = null

    @Attribute("address")
    var address: Address? = null

    @TimestampAction(create = true)
    @Attribute("createdAt")
    var createdAt: Timestamp? = null

    @TimestampAction(update = true)
    @Attribute("updatedAt")
    var updatedAt: Timestamp? = null

    var active: Boolean? = null
}
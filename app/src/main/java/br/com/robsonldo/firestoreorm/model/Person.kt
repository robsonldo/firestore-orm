package br.com.robsonldo.firestoreorm.model

import br.com.robsonldo.library.FireStoreORM
import br.com.robsonldo.library.annotations.*
import br.com.robsonldo.library.annotations.Collection
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Source

@Persisted(false)
@TypeSource(Source.SERVER)
@Collection("persons")
class Person() : FireStoreORM<Person>() {

    constructor(id: String, vararg params: String) : this() {
        this.id = id
        this.params = arrayOf(*params)
    }

    @Attribute("name")
    var name: String? = null

    @Attribute("lastName")
    var lastName: String? = null

    @ThisIsNotNull /* required in kotlin or in @Attribute canBeNull = false */
    @Attribute("age")
    var age: Int = 3

    @Id
    @Attribute("id")
    var key: String? = null

    @Attribute("birth")
    var birth: Timestamp? = null

    @Attribute("currentAddress")
    var myAddress: Address? = null

    @TimestampAction(create = true)
    @Attribute("createdAt")
    var createdAt: Timestamp? = null

    @TimestampAction(update = true)
    @Attribute("updatedAt")
    var updatedAt: Timestamp? = null

    var lastAddress: MutableMap<String, MutableMap<String, Address?>?> = mutableMapOf()
    var features: MutableMap<String, Boolean> = mutableMapOf()

    var luckyNumbers: MutableList<String> = mutableListOf()
    var friendsAddresses: MutableList<Address> = mutableListOf()
    var myAddresses: MutableList<MutableMap<String, Address>> = mutableListOf()

    var active: Boolean = true
}
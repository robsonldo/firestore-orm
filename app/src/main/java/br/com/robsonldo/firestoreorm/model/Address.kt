package br.com.robsonldo.firestoreorm.model

import br.com.robsonldo.library.FireStoreORM
import br.com.robsonldo.library.annotations.Attribute
import br.com.robsonldo.library.annotations.Collection

@Collection(value = "")
class Address() : FireStoreORM<Address>() {

    constructor(street: String, district: String, number: Int) : this() {
        this.street = street
        this.district = district
        this.number = number
    }

    @Attribute("district")
    var district: String? = null

    @Attribute("street")
    var street: String? = null

    @Attribute("number")
    var number: Int? = null
}
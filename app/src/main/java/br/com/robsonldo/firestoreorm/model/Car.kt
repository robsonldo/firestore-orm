package br.com.robsonldo.firestoreorm.model

import br.com.robsonldo.library.FireStoreORM
import br.com.robsonldo.library.annotations.Attribute
import br.com.robsonldo.library.annotations.Collection

@Collection(value = "Cars")
class Car() : FireStoreORM<Car>() {

    constructor(brand: String, model: String, year: Int) : this() {
        this.brand = brand
        this.model = model
        this.year = year
    }

    @Attribute("brand")
    var brand: String? = null

    @Attribute("model")
    var model: String? = null

    @Attribute("year")
    var year: Int? = null
}
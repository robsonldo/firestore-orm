package br.com.robsonldo.firestoreorm

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import br.com.robsonldo.firestoreorm.model.Address
import br.com.robsonldo.firestoreorm.model.Person
import br.com.robsonldo.library.interfaces.OnCompletion

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val person = Person()

        person.apply {
            name = "John"
            lastName = "Doe"
            age =  26
            active = true
            address = Address("Street X", "District Y", 111)
        }

        person.save(onCompletion = object : OnCompletion<Person> {
            override fun onSuccess(obj: Person) {

            }

            override fun onError(e: Exception) {

            }
        })
    }
}
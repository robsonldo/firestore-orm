package br.com.robsonldo.firestoreorm

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import br.com.robsonldo.firestoreorm.model.Address
import br.com.robsonldo.firestoreorm.model.Person
import br.com.robsonldo.library.interfaces.OnCompletion
import br.com.robsonldo.library.interfaces.OnCompletionAll

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val person = Person()

        person.apply {
            name = "Will"
            lastName = "James"
            age =  30
            active = true
            address = Address("Street X", "District Y", 111)
        }

        person.save(onCompletion = object : OnCompletion<Person> {
            override fun onSuccess(obj: Person) {
                Log.e(TAG, "Save success")
            }

            override fun onError(e: Exception) {
                Log.e(TAG, e.message ?: "Error")
            }
        })

        person.all(object : OnCompletionAll<Person> {
            override fun onSuccess(objs: MutableList<Person>) {
                Log.e(TAG, objs.size.toString())
            }

            override fun onError(e: Exception) {
                Log.e(TAG, e.message ?: "Error")
            }
        })
    }
}
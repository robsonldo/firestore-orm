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

        val personA = Person()

        personA.apply {
            name = "Will"
            lastName = "James"
            age = 18
            active = true
            myAddress = Address("Street X", "District Y", 1)

            lastAddress["1"] = mutableMapOf<String, Address?>(
                "11" to Address("Street B", "District B", 11)
            )

            lastAddress["2"] = mutableMapOf<String, Address?>(
                "22" to Address("Street A", "District A", 3)
            )

            features = mutableMapOf("tall" to true, "cool" to true, "nerd" to false)
            luckyNumbers = mutableListOf("10", "51", "48")

            friendsAddresses = mutableListOf(
                Address("Street L1 A", "District L1 A", 1),
                Address("Street L2 B", "District L2 B", 2)
            )

            myAddresses = mutableListOf(
                mutableMapOf(
                    "Home" to (myAddress ?: Address()),
                    "Work" to Address("Street W", "District W", 5)
            ))
        }

        /* saving the entire object */
        personA.save(onCompletion = object : OnCompletion<Person> {
            override fun onSuccess(obj: Person) {
                Log.i(TAG, "saved object id: ${obj.id}")
            }

            override fun onError(e: Exception) {
                Log.e(TAG, e.message ?: "Error")
            }
        })

        /* specifically updating an attribute */
        personA.lastAddress["2"]?.let { it["22"]?.apply { number = 13 } }
        personA.updateFieldValue("lastAddress.2.22.number", onCompletion = object : OnCompletion<Person> {
            override fun onSuccess(obj: Person) {
                Log.i(TAG, "updated object id: ${obj.id}")
            }

            override fun onError(e: Exception) {
                Log.e(TAG, e.message ?: "Error")
            }
        })


        /* Fetching all objects in the collection */
        Person().all(object : OnCompletionAll<Person> {
            override fun onSuccess(objs: MutableList<Person>) {
                Log.i(TAG, "number of people: ${objs.size}")
            }

            override fun onError(e: Exception) {
                Log.e(TAG, e.message ?: "Error")
            }
        })
    }
}
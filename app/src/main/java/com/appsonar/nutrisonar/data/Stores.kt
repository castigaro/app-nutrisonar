package com.appsonar.nutrisonar.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** JSON-Datei-Speicher für Personen — gleiche Bauart wie in den anderen Apps. */
object PersonStore {

    private var persons: MutableList<Person>? = null

    private fun file(context: Context): File = File(context.filesDir, "persons.json")

    private fun load(context: Context): MutableList<Person> {
        persons?.let { return it }
        val loaded = mutableListOf<Person>()
        val f = file(context)
        if (f.exists()) {
            runCatching {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) loaded.add(Person.fromJson(arr.getJSONObject(i)))
            }
        }
        persons = loaded
        return loaded
    }

    fun save(context: Context) {
        val arr = JSONArray()
        load(context).forEach { arr.put(it.toJson()) }
        file(context).writeText(arr.toString())
    }

    fun getAll(context: Context): List<Person> =
        load(context).sortedBy { it.name.lowercase() }

    fun get(context: Context, id: String): Person? =
        load(context).firstOrNull { it.id == id }

    fun add(context: Context, person: Person) {
        load(context).add(person)
        save(context)
    }

    fun delete(context: Context, person: Person) {
        load(context).removeAll { it.id == person.id }
        save(context)
        ProductStore.deleteAllOf(context, person.id)
        ReportStore.delete(context, person.id)
    }
}

/** JSON-Datei-Speicher für Produkte (alle Personen in einer Datei). */
object ProductStore {

    private var products: MutableList<Product>? = null

    private fun file(context: Context): File = File(context.filesDir, "products.json")

    private fun load(context: Context): MutableList<Product> {
        products?.let { return it }
        val loaded = mutableListOf<Product>()
        val f = file(context)
        if (f.exists()) {
            runCatching {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) loaded.add(Product.fromJson(arr.getJSONObject(i)))
            }
        }
        products = loaded
        return loaded
    }

    fun save(context: Context) {
        val arr = JSONArray()
        load(context).forEach { arr.put(it.toJson()) }
        file(context).writeText(arr.toString())
    }

    fun getFor(context: Context, personId: String): List<Product> =
        load(context).filter { it.personId == personId }.sortedBy { it.createdAt }

    fun add(context: Context, product: Product) {
        load(context).add(product)
        save(context)
    }

    fun delete(context: Context, product: Product) {
        val list = load(context)
        list.removeAll { it.id == product.id }
        save(context)
        val path = product.photoPath ?: return
        if (list.none { it.photoPath == path }) {
            runCatching { File(path).delete() }
        }
    }

    fun deleteAllOf(context: Context, personId: String) {
        val list = load(context)
        val removed = list.filter { it.personId == personId }
        list.removeAll { it.personId == personId }
        save(context)
        removed.mapNotNull { it.photoPath }.distinct().forEach { path ->
            if (list.none { it.photoPath == path }) runCatching { File(path).delete() }
        }
    }
}

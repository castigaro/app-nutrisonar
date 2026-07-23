package com.appsonar.nutrisonar.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Ein Nährstoff/Wirkstoff eines Produkts, normiert auf EINE Tablette/Kapsel. */
class NutrientAmount(
    var name: String,
    var amountPerPiece: Double,
    var unit: String, // mg, µg, g, IE
    var uncertain: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("amountPerPiece", amountPerPiece)
        put("unit", unit)
        put("uncertain", uncertain)
    }

    companion object {
        fun fromJson(json: JSONObject) = NutrientAmount(
            name = json.optString("name"),
            amountPerPiece = json.optDouble("amountPerPiece", 0.0),
            unit = json.optString("unit", "mg"),
            uncertain = json.optBoolean("uncertain", false),
        )
    }
}

/** Ein erfasstes Präparat samt Einnahmeschema. */
class Product(
    val id: String = UUID.randomUUID().toString(),
    val personId: String,
    var name: String,
    val nutrients: MutableList<NutrientAmount> = mutableListOf(),
    var scheduleText: String = "1-0-0",
    var piecesPerDay: Double = 1.0,
    var photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("personId", personId)
        put("name", name)
        put("nutrients", JSONArray().also { arr -> nutrients.forEach { arr.put(it.toJson()) } })
        put("scheduleText", scheduleText)
        put("piecesPerDay", piecesPerDay)
        put("photoPath", photoPath ?: JSONObject.NULL)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Product {
            val product = Product(
                id = json.optString("id", UUID.randomUUID().toString()),
                personId = json.optString("personId"),
                name = json.optString("name"),
                scheduleText = json.optString("scheduleText", "1-0-0"),
                piecesPerDay = json.optDouble("piecesPerDay", 1.0),
                photoPath = json.optString("photoPath").ifBlank { null }
                    .takeIf { json.opt("photoPath") != JSONObject.NULL },
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            )
            val arr = json.optJSONArray("nutrients") ?: JSONArray()
            for (i in 0 until arr.length()) {
                product.nutrients.add(NutrientAmount.fromJson(arr.getJSONObject(i)))
            }
            return product
        }
    }
}

/** Eine Person, für die analysiert wird. Eine Analyse-Datei pro Person. */
class Person(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var age: Int? = null,
    var weightKg: Double? = null,
    var medications: String = "",
    val history: MutableList<String> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("age", age ?: JSONObject.NULL)
        put("weightKg", weightKg ?: JSONObject.NULL)
        put("medications", medications)
        put("history", JSONArray().also { arr -> history.forEach { arr.put(it) } })
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Person {
            val person = Person(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name"),
                age = if (json.isNull("age")) null else json.optInt("age"),
                weightKg = if (json.isNull("weightKg")) null else json.optDouble("weightKg"),
                medications = json.optString("medications"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            )
            val arr = json.optJSONArray("history") ?: JSONArray()
            for (i in 0 until arr.length()) person.history.add(arr.getString(i))
            return person
        }
    }
}

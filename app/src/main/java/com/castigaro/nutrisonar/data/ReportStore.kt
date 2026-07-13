package com.castigaro.nutrisonar.data

import android.content.Context
import com.castigaro.nutrisonar.analysis.Aggregator
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Gespeicherter Analysebericht einer Person: die deterministisch berechneten
 * Zeilen plus die KI-Texte (Folgen, Wechselwirkungen, Fazit).
 */
class Report(
    val personId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val ampelLine: String,
    val rows: List<Aggregator.Row>,
    val others: List<Aggregator.Row>,
    val effects: Map<String, String>, // Anzeigename → Folgen/Nebenwirkungen
    val interactions: String,
    val summary: String,
    val productLines: List<String>,   // "Produkt (Schema, X Stück/Tag)"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("personId", personId)
        put("createdAt", createdAt)
        put("ampelLine", ampelLine)
        put("rows", rowsToJson(rows))
        put("others", rowsToJson(others))
        put("effects", JSONObject(effects))
        put("interactions", interactions)
        put("summary", summary)
        put("productLines", JSONArray().also { arr -> productLines.forEach { arr.put(it) } })
    }

    companion object {
        private fun rowsToJson(rows: List<Aggregator.Row>): JSONArray =
            JSONArray().also { arr ->
                rows.forEach { row ->
                    arr.put(JSONObject().apply {
                        put("nutrient", row.nutrient)
                        put("canonical", row.canonical ?: JSONObject.NULL)
                        put("amount", row.amount)
                        put("unit", row.unit)
                        put("limitText", row.limitText)
                        put("percent", row.percent ?: JSONObject.NULL)
                        put("status", row.status.name)
                        put("uncertain", row.uncertain)
                        put("note", row.note ?: JSONObject.NULL)
                    })
                }
            }

        private fun rowsFromJson(arr: JSONArray): List<Aggregator.Row> {
            val rows = mutableListOf<Aggregator.Row>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                rows.add(
                    Aggregator.Row(
                        nutrient = o.optString("nutrient"),
                        canonical = if (o.isNull("canonical")) null else o.optString("canonical"),
                        amount = o.optDouble("amount", 0.0),
                        unit = o.optString("unit"),
                        limitText = o.optString("limitText"),
                        percent = if (o.isNull("percent")) null else o.optDouble("percent"),
                        status = runCatching { Aggregator.Status.valueOf(o.optString("status")) }
                            .getOrDefault(Aggregator.Status.NONE),
                        uncertain = o.optBoolean("uncertain", false),
                        note = if (o.isNull("note")) null else o.optString("note"),
                    )
                )
            }
            return rows
        }

        fun fromJson(json: JSONObject): Report {
            val effectsJson = json.optJSONObject("effects") ?: JSONObject()
            val effects = mutableMapOf<String, String>()
            effectsJson.keys().forEach { key -> effects[key] = effectsJson.optString(key) }
            val productLines = mutableListOf<String>()
            val arr = json.optJSONArray("productLines") ?: JSONArray()
            for (i in 0 until arr.length()) productLines.add(arr.getString(i))
            return Report(
                personId = json.optString("personId"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                ampelLine = json.optString("ampelLine"),
                rows = rowsFromJson(json.optJSONArray("rows") ?: JSONArray()),
                others = rowsFromJson(json.optJSONArray("others") ?: JSONArray()),
                effects = effects,
                interactions = json.optString("interactions"),
                summary = json.optString("summary"),
                productLines = productLines,
            )
        }
    }
}

object ReportStore {

    private fun file(context: Context, personId: String): File {
        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        return File(dir, "report-$personId.json")
    }

    fun save(context: Context, report: Report) {
        file(context, report.personId).writeText(report.toJson().toString())
    }

    fun get(context: Context, personId: String): Report? {
        val f = file(context, personId)
        if (!f.exists()) return null
        return runCatching { Report.fromJson(JSONObject(f.readText())) }.getOrNull()
    }

    fun delete(context: Context, personId: String) {
        runCatching { file(context, personId).delete() }
    }
}

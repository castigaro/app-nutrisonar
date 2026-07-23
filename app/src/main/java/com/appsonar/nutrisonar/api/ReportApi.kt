package com.appsonar.nutrisonar.api

import android.content.Context
import com.appsonar.nutrisonar.analysis.Aggregator
import com.appsonar.nutrisonar.data.Person
import com.appsonar.nutrisonar.data.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Holt die Text-Bausteine des Berichts von der KI: mögliche Folgen je
 * auffälligem Nährstoff, Wechselwirkungen und Kurzfazit. Alle ZAHLEN kommen
 * aus der deterministischen Berechnung der App — die KI liefert nur Texte.
 */
object ReportApi {

    data class Texts(
        val effects: Map<String, String>,
        val interactions: String,
        val summary: String,
    )

    private val SYSTEM_PROMPT = """
        Du bist ein technischer Analyst für Nahrungsergänzungsmittel — KEINE
        ärztliche Beratung, der Nutzer weiß das. Keine allgemeinen
        Gesundheitsweisheiten, keine Disclaimer. Du bekommst eine fertig
        berechnete Tagesbilanz (Summen und UL-Vergleich sind schon gerechnet
        — rechne NICHT neu) und lieferst nur knappe deutsche Texte:
        - "effects": für jeden Nährstoff mit Status 🔴 oder ⚠️ (und nur für
          diese) 1-2 Sätze zu möglichen Folgen/Nebenwirkungen der GENANNTEN
          Tagesmenge. Schlüssel exakt der übergebene Anzeigename.
        - "interactions": systematische Wechselwirkungsprüfung der
          Inhaltsstoffe untereinander und mit den Medikamenten (falls
          angegeben). Besonders achten auf: Grapefruit(-Extrakt),
          Piperin/schwarzer Pfeffer, hohe Calcium-/Eisen-/Magnesiumgaben,
          Vitamin K. Auch ohne UL-Bezug benennen. Fehlen Medikamente, EIN
          kurzer Satz, dass die vollständige Prüfung erst mit der
          Medikamentenliste möglich ist. Kurze Absätze, keine Tabelle.
        - "summary": Kurzfazit in 2-4 Sätzen — auffälligste Punkte und wo
          man ggf. reduzieren oder streichen könnte.
        Antworte ausschließlich mit JSON:
        {"effects":[{"nutrient":"...","text":"..."}],"interactions":"...","summary":"..."}
    """.trimIndent()

    suspend fun texts(
        context: Context,
        person: Person,
        products: List<Product>,
        result: Aggregator.Result,
    ): Texts = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("person", JSONObject().apply {
                put("name", person.name)
                put("alter", person.age ?: JSONObject.NULL)
                put("gewichtKg", person.weightKg ?: JSONObject.NULL)
                put("medikamente", person.medications.ifBlank { "nicht angegeben" })
            })
            put("produkte", JSONArray().also { arr ->
                products.forEach { product ->
                    arr.put("${product.name} — ${product.scheduleText} " +
                        "(${Aggregator.formatAmount(product.piecesPerDay)} Stück/Tag)")
                }
            })
            put("tagesbilanz", JSONArray().also { arr ->
                result.rows.forEach { row ->
                    arr.put(JSONObject().apply {
                        put("nutrient", row.nutrient)
                        put("mengeProTag", "${Aggregator.formatAmount(row.amount)} ${row.unit}")
                        put("hoechstmenge", row.limitText)
                        put("ueberschreitung", Aggregator.exceedText(row.percent))
                        put("status", row.status.emoji)
                    })
                }
            })
            put("weitereWirkstoffe", JSONArray().also { arr ->
                result.others.forEach { row ->
                    arr.put("${row.nutrient} (${Aggregator.formatAmount(row.amount)} ${row.unit}/Tag)")
                }
            })
        }

        val text = LlmClient.request(
            context,
            systemPrompt = SYSTEM_PROMPT,
            userText = payload.toString(),
        )
        val json = LlmClient.extractJson(text)

        val effects = mutableMapOf<String, String>()
        val arr = json.optJSONArray("effects") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val nutrient = o.optString("nutrient").trim()
            val effectText = o.optString("text").trim()
            if (nutrient.isNotEmpty() && effectText.isNotEmpty()) effects[nutrient] = effectText
        }
        Texts(
            effects = effects,
            interactions = json.optString("interactions").trim(),
            summary = json.optString("summary").trim()
                .ifBlank { "Keine Auffälligkeiten." },
        )
    }
}

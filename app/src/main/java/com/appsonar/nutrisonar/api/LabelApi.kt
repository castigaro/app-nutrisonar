package com.appsonar.nutrisonar.api

import android.content.Context
import com.appsonar.nutrisonar.data.NutrientAmount
import com.appsonar.nutrisonar.util.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * Liest das Etikett eines Präparats vom Foto: Produktname und alle
 * Nährstoffe/Wirkstoffe mit Menge, Einheit und Bezugsgröße. Unsicher
 * lesbare Werte werden markiert, nie geraten. Die Mengen werden hier
 * bereits auf EINE Tablette/Kapsel normiert.
 */
object LabelApi {

    data class Extraction(
        val productName: String,
        val perPieces: Int,
        val nutrients: List<NutrientAmount>,
    )

    private val SYSTEM_PROMPT = """
        Du liest Etiketten von Nahrungsergänzungsmitteln von Fotos ab.
        Erfasse: Produktname, alle Nährstoffe UND sonstigen Wirkstoffe
        (auch Pflanzenextrakte wie Grapefruitextrakt oder Piperin) mit Menge
        und Einheit, sowie die Bezugsgröße der Tabelle (z. B. "pro 2 Kapseln"
        → per_pieces: 2; fehlt die Angabe, nimm 1).
        Regeln:
        - Mengen exakt so übernehmen, wie sie für per_pieces Stück gedruckt
          sind — NICHT selbst umrechnen.
        - Einheit nur aus: "mg", "µg", "g", "IE".
        - Ist ein Wert nicht sicher lesbar, setze "uncertain": true und trage
          die beste Lesung ein. NIEMALS raten, ohne es zu markieren.
        - %NRV-Angaben ignorieren, nur absolute Mengen erfassen.
        - Antworte ausschließlich mit JSON in exakt dieser Form:
          {"name":"...","per_pieces":N,"nutrients":[{"name":"...","amount":Zahl,"unit":"mg","uncertain":false}]}
    """.trimIndent()

    suspend fun extract(context: Context, photo: File): Extraction =
        withContext(Dispatchers.IO) {
            val text = LlmClient.request(
                context,
                systemPrompt = SYSTEM_PROMPT,
                userText = "Lies dieses Etikett ab.",
                imageBase64 = Photos.toBase64(photo),
            )
            val json = LlmClient.extractJson(text)
            val perPieces = json.optInt("per_pieces", 1).coerceAtLeast(1)
            val nutrients = mutableListOf<NutrientAmount>()
            val arr = json.optJSONArray("nutrients") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").trim()
                val amount = o.optDouble("amount", 0.0)
                if (name.isEmpty() || amount <= 0.0) continue
                nutrients.add(
                    NutrientAmount(
                        name = name,
                        amountPerPiece = amount / perPieces,
                        unit = o.optString("unit", "mg"),
                        uncertain = o.optBoolean("uncertain", false),
                    )
                )
            }
            Extraction(
                productName = json.optString("name").trim().ifBlank { "Unbenanntes Präparat" },
                perPieces = perPieces,
                nutrients = nutrients,
            )
        }
}

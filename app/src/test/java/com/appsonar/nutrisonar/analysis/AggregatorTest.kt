package com.appsonar.nutrisonar.analysis

import com.appsonar.nutrisonar.data.NutrientAmount
import com.appsonar.nutrisonar.data.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregatorTest {

    private fun product(vararg nutrients: NutrientAmount, piecesPerDay: Double = 1.0) =
        Product(personId = "p1", name = "Testprodukt").also {
            it.nutrients.addAll(nutrients)
            it.piecesPerDay = piecesPerDay
        }

    // ---- Summierung ----

    @Test
    fun `summiert Aliasse ueber Produkte hinweg inklusive Einheiten-Umrechnung`() {
        val result = Aggregator.aggregate(
            listOf(
                product(NutrientAmount("Pyridoxin", 500.0, "µg")),
                product(NutrientAmount("Vitamin B6", 0.5, "mg")),
            )
        )
        val row = result.rows.single()
        assertEquals("Vitamin B6", row.canonical)
        assertEquals(1.0, row.amount, 1e-9)
        assertEquals("mg", row.unit)
        // 1 mg von 12 mg UL ≈ 8,3 % → unkritisch
        assertEquals(Aggregator.Status.OK, row.status)
    }

    @Test
    fun `Tagesdosis ist Menge pro Stueck mal Stueck pro Tag`() {
        val result = Aggregator.aggregate(
            listOf(product(NutrientAmount("Zink", 5.0, "mg"), piecesPerDay = 2.0))
        )
        assertEquals(10.0, result.rows.single().amount, 1e-9)
    }

    @Test
    fun `Naehrstoffe mit Tagesdosis null werden ignoriert`() {
        val result = Aggregator.aggregate(listOf(product(NutrientAmount("Zink", 0.0, "mg"))))
        assertTrue(result.rows.isEmpty())
        assertTrue(result.others.isEmpty())
    }

    // ---- Ampel-Schwellen (Vitamin C, UL 2000 mg) ----

    private fun statusForVitaminC(mg: Double): Aggregator.Status =
        Aggregator.aggregate(listOf(product(NutrientAmount("Vitamin C", mg, "mg"))))
            .rows.single().status

    @Test
    fun `Ampel-Schwellen wechseln bei 90 und 110 Prozent`() {
        // Auch die haargenauen Grenzwerte müssen stabil sein: Fließkomma-Staub
        // (2200/2000×100 = 110,000…01) wird im Aggregator per Epsilon toleriert.
        assertEquals(Aggregator.Status.OK, statusForVitaminC(1798.0))     // 89,9 %
        assertEquals(Aggregator.Status.LIMIT, statusForVitaminC(1800.0))  // 90,0 %
        assertEquals(Aggregator.Status.LIMIT, statusForVitaminC(2200.0))  // 110,0 % exakt
        assertEquals(Aggregator.Status.OVER, statusForVitaminC(2210.0))   // 110,5 %
    }

    // ---- Niacin-Sonderfall ----

    @Test
    fun `Niacin unbekannter Form liefert beide Grenzwert-Zeilen`() {
        val result = Aggregator.aggregate(listOf(product(NutrientAmount("Niacin", 20.0, "mg"))))
        assertEquals(2, result.rows.size)
        assertTrue(result.rows.all { it.canonical == UlTable.NIACIN })
        assertTrue(result.rows.all { it.note?.contains("Etikett") == true })
        // Nicotinsäure-Grenze (10 mg) wäre bei 200 % → kritisch zuerst sortiert.
        assertEquals(Aggregator.Status.OVER, result.rows.first().status)
        assertEquals(200.0, result.rows.first().percent!!, 1e-9)
    }

    @Test
    fun `Niacin mit erkannter Form liefert genau eine Zeile`() {
        val result = Aggregator.aggregate(listOf(product(NutrientAmount("Nicotinamid", 20.0, "mg"))))
        val row = result.rows.single()
        assertEquals("Niacin (Nicotinamid)", row.nutrient)
        assertEquals(Aggregator.Status.OK, row.status) // 20 von 900 mg
        assertNull(row.note)
    }

    // ---- Randfälle ----

    @Test
    fun `unbekannte Wirkstoffe landen unter others`() {
        val result = Aggregator.aggregate(
            listOf(product(NutrientAmount("Ashwagandha-Extrakt", 300.0, "mg")))
        )
        assertTrue(result.rows.isEmpty())
        val other = result.others.single()
        assertEquals("Ashwagandha-Extrakt", other.nutrient)
        assertNull(other.canonical)
        assertEquals(Aggregator.Status.NONE, other.status)
    }

    @Test
    fun `fehlgeschlagene Umrechnung macht die Zeile unsicher statt falsch zu addieren`() {
        val result = Aggregator.aggregate(
            listOf(
                product(
                    NutrientAmount("Zink", 10.0, "mg"),
                    NutrientAmount("Zink", 2.0, "Tropfen"),
                )
            )
        )
        val row = result.rows.single()
        assertEquals(10.0, row.amount, 1e-9)
        assertTrue(row.uncertain)
    }

    @Test
    fun `Naehrstoff ohne UL wird gekennzeichnet statt bewertet`() {
        val result = Aggregator.aggregate(listOf(product(NutrientAmount("Biotin", 100.0, "µg"))))
        val row = result.rows.single()
        assertEquals("kein UL festgelegt", row.limitText)
        assertEquals(Aggregator.Status.NONE, row.status)
        assertNull(row.percent)
    }

    @Test
    fun `Sortierung stellt Kritisches nach oben`() {
        val result = Aggregator.aggregate(
            listOf(
                product(NutrientAmount("Selen", 100.0, "µg")),    // 39 % → OK
                product(NutrientAmount("Zink", 30.0, "mg")),      // 120 % → OVER
                product(NutrientAmount("Vitamin D", 95.0, "µg")), // 95 % → LIMIT
            )
        )
        assertEquals(
            listOf(Aggregator.Status.OVER, Aggregator.Status.LIMIT, Aggregator.Status.OK),
            result.rows.map { it.status },
        )
        assertTrue(result.ampelLine.contains("🔴 1"))
        assertTrue(result.ampelLine.contains("⚠️ 1"))
        assertTrue(result.ampelLine.contains("✅ 1"))
    }

    // ---- Formatierung ----

    @Test
    fun `exceedText zeigt nur echte Ueberschreitungen`() {
        assertEquals("—", Aggregator.exceedText(null))
        assertEquals("—", Aggregator.exceedText(100.0))
        assertEquals("+50 %", Aggregator.exceedText(150.0))
        assertEquals("+133 %", Aggregator.exceedText(233.4))
    }

    @Test
    fun `formatAmount nutzt deutsches Komma und laesst Ganzzahlen glatt`() {
        assertEquals("10", Aggregator.formatAmount(10.0))
        assertEquals("10,5", Aggregator.formatAmount(10.5))
        assertEquals("0,1", Aggregator.formatAmount(0.1))
    }
}

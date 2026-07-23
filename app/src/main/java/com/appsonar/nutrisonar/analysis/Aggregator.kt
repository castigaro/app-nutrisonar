package com.appsonar.nutrisonar.analysis

import com.appsonar.nutrisonar.data.Product
import kotlin.math.roundToInt

/**
 * Deterministische Auswertung: summiert jeden Nährstoff über alle Produkte
 * (Tagesdosis = Menge/Stück × Stück/Tag), rechnet Einheiten um und vergleicht
 * mit der UL-Tabelle. Die KI liefert nur Texte — nie Zahlen.
 */
object Aggregator {

    enum class Status(val emoji: String) { OK("✅"), LIMIT("⚠️"), OVER("🔴"), NONE("—") }

    data class Row(
        val nutrient: String,          // Anzeigename (ggf. mit Form-Zusatz)
        val canonical: String?,        // kanonischer Name oder null (weiterer Wirkstoff)
        val amount: Double,            // Tagesmenge
        val unit: String,
        val limitText: String,         // z. B. "300 mg (EFSA)" oder "kein UL festgelegt"
        val percent: Double?,          // Aufnahme/UL × 100, null ohne UL
        val status: Status,
        val uncertain: Boolean,        // mind. ein Quellwert war unsicher lesbar
        val note: String? = null,
    )

    data class Result(
        val rows: List<Row>,           // Nährstoffe mit/ohne UL, sortiert: kritisch zuerst
        val others: List<Row>,         // unbekannte Wirkstoffe (für Wechselwirkungen)
        val ampelLine: String,
    )

    fun aggregate(products: List<Product>): Result {
        // Tagesmengen je kanonischem Nährstoff aufsummieren.
        data class Sum(
            var amount: Double,
            var unit: String,
            var uncertain: Boolean,
            var niacinForm: UlTable.NiacinForm = UlTable.NiacinForm.UNKNOWN,
            var mixedNiacinForms: Boolean = false,
        )

        val sums = LinkedHashMap<String, Sum>()
        val others = mutableListOf<Row>()

        products.forEach { product ->
            product.nutrients.forEach { nutrient ->
                val daily = nutrient.amountPerPiece * product.piecesPerDay
                if (daily <= 0.0) return@forEach
                val canonical = UlTable.canonicalName(nutrient.name)
                if (canonical == null) {
                    // Unbekannter Wirkstoff: einzeln listen (keine Summierung über Aliasse).
                    others.add(
                        Row(
                            nutrient = nutrient.name,
                            canonical = null,
                            amount = daily,
                            unit = Units.normalizeUnit(nutrient.unit),
                            limitText = "—",
                            percent = null,
                            status = Status.NONE,
                            uncertain = nutrient.uncertain,
                        )
                    )
                    return@forEach
                }

                val targetUnit = UlTable.entryFor(canonical)?.unit
                    ?: Units.normalizeUnit(nutrient.unit)
                val converted = Units.convert(canonical, daily, nutrient.unit, targetUnit)
                val existing = sums[canonical]
                if (existing == null) {
                    sums[canonical] = Sum(
                        amount = converted ?: daily,
                        unit = if (converted != null) targetUnit else Units.normalizeUnit(nutrient.unit),
                        uncertain = nutrient.uncertain || converted == null,
                        niacinForm = UlTable.niacinForm(nutrient.name),
                    )
                } else {
                    val addable = Units.convert(canonical, daily, nutrient.unit, existing.unit)
                    if (addable != null) {
                        existing.amount += addable
                    } else {
                        existing.uncertain = true
                    }
                    existing.uncertain = existing.uncertain || nutrient.uncertain
                    val form = UlTable.niacinForm(nutrient.name)
                    if (canonical == UlTable.NIACIN && form != existing.niacinForm) {
                        existing.mixedNiacinForms = true
                    }
                }
            }
        }

        val rows = mutableListOf<Row>()
        sums.forEach { (canonical, sum) ->
            if (canonical == UlTable.NIACIN) {
                rows.addAll(niacinRows(sum.amount, sum.unit, sum.uncertain, sum.niacinForm, sum.mixedNiacinForms))
                return@forEach
            }
            val entry = UlTable.entryFor(canonical)
            if (entry == null) {
                rows.add(
                    Row(
                        nutrient = canonical, canonical = canonical,
                        amount = sum.amount, unit = sum.unit,
                        limitText = "kein UL festgelegt", percent = null,
                        status = Status.NONE, uncertain = sum.uncertain,
                    )
                )
            } else {
                val percent = sum.amount / entry.ul * 100.0
                rows.add(
                    Row(
                        nutrient = canonical, canonical = canonical,
                        amount = sum.amount, unit = sum.unit,
                        limitText = "${formatAmount(entry.ul)} ${entry.unit} (${entry.source})" +
                            (entry.note?.let { ", $it" } ?: ""),
                        percent = percent,
                        status = statusFor(percent),
                        uncertain = sum.uncertain,
                    )
                )
            }
        }

        // Kritisches nach oben, Rest alphabetisch.
        rows.sortWith(
            compareByDescending<Row> { it.status == Status.OVER }
                .thenByDescending { it.status == Status.LIMIT }
                .thenBy { it.nutrient.lowercase() }
        )

        val over = rows.count { it.status == Status.OVER }
        val limit = rows.count { it.status == Status.LIMIT }
        val ok = rows.count { it.status == Status.OK }
        val parts = mutableListOf<String>()
        if (over > 0) parts.add("🔴 $over deutlich über Grenze")
        if (limit > 0) parts.add("⚠️ $limit am Limit")
        parts.add("✅ $ok unkritisch")
        return Result(rows, others, parts.joinToString(" · "))
    }

    private fun niacinRows(
        amount: Double,
        unit: String,
        uncertain: Boolean,
        form: UlTable.NiacinForm,
        mixedForms: Boolean,
    ): List<Row> {
        fun row(entry: UlTable.UlEntry, label: String, note: String?): Row {
            val percent = amount / entry.ul * 100.0
            return Row(
                nutrient = label, canonical = UlTable.NIACIN,
                amount = amount, unit = unit,
                limitText = "${formatAmount(entry.ul)} ${entry.unit} (${entry.source}, ${entry.note})",
                percent = percent, status = statusFor(percent),
                uncertain = uncertain, note = note,
            )
        }
        return when {
            mixedForms || form == UlTable.NiacinForm.UNKNOWN -> listOf(
                row(UlTable.NIACIN_NICOTINIC_ACID, "Niacin (falls Nicotinsäure)", "Form bitte am Etikett klären"),
                row(UlTable.NIACIN_NICOTINAMIDE, "Niacin (falls Nicotinamid)", "Form bitte am Etikett klären"),
            )
            form == UlTable.NiacinForm.NICOTINIC_ACID ->
                listOf(row(UlTable.NIACIN_NICOTINIC_ACID, "Niacin (Nicotinsäure)", null))
            else ->
                listOf(row(UlTable.NIACIN_NICOTINAMIDE, "Niacin (Nicotinamid)", null))
        }
    }

    // Fließkomma-Staub darf die Ampel nicht kippen: 2200/2000×100 ergibt binär
    // 110,000…01 (zeigte Rot statt Gelb), und knapp unter 90 könnte umgekehrt
    // fälschlich Grün statt Gelb erscheinen. Die winzige Toleranz macht beide
    // Grenzen robust — und irrt an der 90er-Grenze zur warnenden Seite.
    private const val PERCENT_EPSILON = 1e-9

    private fun statusFor(percent: Double): Status = when {
        percent > 110.0 + PERCENT_EPSILON -> Status.OVER
        percent >= 90.0 - PERCENT_EPSILON -> Status.LIMIT
        else -> Status.OK
    }

    /** Überschreitung nach Prompt-Formel: (Aufnahme/UL − 1) × 100, nur wenn > 0. */
    fun exceedText(percent: Double?): String {
        if (percent == null) return "—"
        val exceed = percent - 100.0
        return if (exceed > 0) "+${exceed.roundToInt()} %" else "—"
    }

    fun formatAmount(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format("%.1f", value).replace('.', ',')
}

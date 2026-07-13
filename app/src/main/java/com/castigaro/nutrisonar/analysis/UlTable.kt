package com.castigaro.nutrisonar.analysis

/**
 * Tolerierbare Höchstmengen (UL) pro Tag für Erwachsene. Primär EFSA-Werte;
 * wo die EFSA keinen UL festgelegt hat, US-Wert (NIH/IOM) mit Kennzeichnung.
 * Nährstoffe ohne jeden UL stehen bewusst NICHT in der Tabelle — sie werden
 * im Bericht mit "kein UL festgelegt" geführt.
 *
 * Alle Werte in der Einheit des Eintrags; Mengen werden vor dem Vergleich
 * in diese Einheit umgerechnet (siehe [Units]).
 */
object UlTable {

    data class UlEntry(
        val canonical: String,
        val ul: Double,
        val unit: String,
        val source: String, // "EFSA" oder "US"
        val note: String? = null,
    )

    /** Sonderfall Niacin: formabhängiger Grenzwert. */
    const val NIACIN = "Niacin"
    val NIACIN_NICOTINIC_ACID = UlEntry(NIACIN, 10.0, "mg", "EFSA", "als Nicotinsäure")
    val NIACIN_NICOTINAMIDE = UlEntry(NIACIN, 900.0, "mg", "EFSA", "als Nicotinamid")

    private val ENTRIES = listOf(
        UlEntry("Vitamin A", 3000.0, "µg", "EFSA"),
        UlEntry("Vitamin D", 100.0, "µg", "EFSA"),
        UlEntry("Vitamin E", 300.0, "mg", "EFSA"),
        UlEntry("Vitamin C", 2000.0, "mg", "US"),
        UlEntry("Vitamin B6", 12.0, "mg", "EFSA"),
        UlEntry("Folsäure", 1000.0, "µg", "EFSA"),
        UlEntry("Calcium", 2500.0, "mg", "EFSA"),
        UlEntry("Magnesium", 250.0, "mg", "EFSA", "nur aus Präparaten"),
        UlEntry("Eisen", 45.0, "mg", "US"),
        UlEntry("Zink", 25.0, "mg", "EFSA"),
        UlEntry("Kupfer", 5.0, "mg", "EFSA"),
        UlEntry("Jod", 600.0, "µg", "EFSA"),
        UlEntry("Selen", 255.0, "µg", "EFSA"),
        UlEntry("Mangan", 11.0, "mg", "US"),
        UlEntry("Molybdän", 600.0, "µg", "EFSA"),
        UlEntry("Fluorid", 7.0, "mg", "EFSA"),
        UlEntry("Phosphor", 4000.0, "mg", "US"),
        UlEntry("Bor", 10.0, "mg", "EFSA"),
    ).associateBy { it.canonical }

    /** Nährstoffe ganz ohne UL — bekannt, aber ohne Grenzwert. */
    private val NO_UL = setOf(
        "Vitamin B1", "Vitamin B2", "Vitamin B12", "Biotin", "Pantothensäure",
        "Vitamin K", "Kalium", "Chrom", "Beta-Carotin",
    )

    fun entryFor(canonical: String): UlEntry? = ENTRIES[canonical]

    fun isKnownWithoutUl(canonical: String): Boolean = canonical in NO_UL

    /**
     * Normalisiert einen Nährstoffnamen vom Etikett auf den kanonischen Namen
     * (erkennt Doppelungen wie "B12" vs. "Cobalamin"). Unbekannte Wirkstoffe
     * (z. B. Pflanzenextrakte) liefern null — sie laufen als "weitere
     * Wirkstoffe" in die Wechselwirkungsprüfung.
     */
    fun canonicalName(raw: String): String? {
        val n = raw.lowercase()
        return when {
            "nicotins" in n || "nicotinamid" in n || "niacin" in n || "vitamin b3" in n -> NIACIN
            "cobalamin" in n || "b12" in n -> "Vitamin B12"
            "pyridoxin" in n || "b6" in n -> "Vitamin B6"
            "thiamin" in n || "b1" in n && "b12" !in n -> "Vitamin B1"
            "riboflavin" in n || "b2" in n -> "Vitamin B2"
            "folsäure" in n || "folat" in n || "folic" in n -> "Folsäure"
            "biotin" in n || "b7" in n -> "Biotin"
            "pantothen" in n || "b5" in n -> "Pantothensäure"
            "ascorbin" in n || "vitamin c" in n -> "Vitamin C"
            "cholecalciferol" in n || "vitamin d" in n -> "Vitamin D"
            "retinol" in n || ("vitamin a" in n && "beta" !in n) -> "Vitamin A"
            "beta-carotin" in n || "betacarotin" in n || "carotin" in n -> "Beta-Carotin"
            "tocopherol" in n || "vitamin e" in n -> "Vitamin E"
            "phyllochinon" in n || "menachinon" in n || "vitamin k" in n -> "Vitamin K"
            "calcium" in n || "kalzium" in n -> "Calcium"
            "magnesium" in n -> "Magnesium"
            "eisen" in n || "ferro" in n || "ferri" in n -> "Eisen"
            "zink" in n -> "Zink"
            "kupfer" in n -> "Kupfer"
            "jod" in n || "iod" in n -> "Jod"
            "selen" in n -> "Selen"
            "mangan" in n -> "Mangan"
            "molybdän" in n || "molybdaen" in n -> "Molybdän"
            "fluorid" in n -> "Fluorid"
            "phosphor" in n -> "Phosphor"
            "kalium" in n -> "Kalium"
            "chrom" in n -> "Chrom"
            "bor " == "$n " || n == "bor" -> "Bor"
            else -> null
        }
    }

    /** Erkennt bei Niacin die Form, sofern der Etikett-Name sie verrät. */
    fun niacinForm(raw: String): NiacinForm {
        val n = raw.lowercase()
        return when {
            "nicotinamid" in n || "niacinamid" in n -> NiacinForm.NICOTINAMIDE
            "nicotins" in n -> NiacinForm.NICOTINIC_ACID
            else -> NiacinForm.UNKNOWN
        }
    }

    enum class NiacinForm { NICOTINIC_ACID, NICOTINAMIDE, UNKNOWN }
}

/** Einheiten-Umrechnung für den UL-Vergleich. */
object Units {

    /**
     * Rechnet [amount] [unit] in die Ziel-Einheit [targetUnit] um.
     * IE wird nährstoffspezifisch umgerechnet (D: 40 IE = 1 µg,
     * A: 1 IE = 0,3 µg Retinol, E: 1 IE = 0,67 mg). Liefert null, wenn
     * keine sichere Umrechnung möglich ist.
     */
    fun convert(canonical: String?, amount: Double, unit: String, targetUnit: String): Double? {
        val u = normalizeUnit(unit)
        val t = normalizeUnit(targetUnit)
        if (u == t) return amount

        // IE zuerst in eine Massen-Einheit übersetzen.
        if (u == "IE") {
            val (massAmount, massUnit) = when (canonical) {
                "Vitamin D" -> amount / 40.0 to "µg"
                "Vitamin A" -> amount * 0.3 to "µg"
                "Vitamin E" -> amount * 0.67 to "mg"
                else -> return null
            }
            return convert(canonical, massAmount, massUnit, t)
        }

        val factorFromMg = mapOf("g" to 0.001, "mg" to 1.0, "µg" to 1000.0)
        val toMg = when (u) {
            "g" -> amount * 1000.0
            "mg" -> amount
            "µg" -> amount / 1000.0
            else -> return null
        }
        val factor = factorFromMg[t] ?: return null
        return toMg * factor
    }

    fun normalizeUnit(unit: String): String = when (unit.trim().lowercase()) {
        "g" -> "g"
        "mg" -> "mg"
        "µg", "ug", "mcg" -> "µg"
        "ie", "i.e.", "iu" -> "IE"
        else -> unit.trim()
    }
}

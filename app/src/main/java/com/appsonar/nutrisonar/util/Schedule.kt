package com.appsonar.nutrisonar.util

/**
 * Übersetzt ein Einnahmeschema in Stück/Tag.
 * Unterstützt: "1-0-1" (Summe), "jeden 2. Tag" (1/N), reine Zahlen ("2"),
 * "2x täglich". Liefert null, wenn nichts erkennbar ist.
 */
object Schedule {

    fun piecesPerDay(text: String): Double? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null

        // "1-0-1", "1 - 0 - 1", auch mit halben: "0,5-0-0"
        val dashParts = t.split('-').map { it.trim().replace(',', '.') }
        if (dashParts.size >= 2 && dashParts.all { it.toDoubleOrNull() != null }) {
            return dashParts.sumOf { it.toDouble() }
        }

        // "jeden 2. tag", "alle 3 tage"
        Regex("(?:jeden|alle)\\s+(\\d+)\\.?\\s*tag").find(t)?.let { match ->
            val n = match.groupValues[1].toIntOrNull() ?: return@let
            if (n >= 1) return 1.0 / n
        }

        // "2x täglich", "3 x am tag"
        Regex("(\\d+(?:[.,]\\d+)?)\\s*x").find(t)?.let { match ->
            return match.groupValues[1].replace(',', '.').toDoubleOrNull()
        }

        // reine Zahl
        return t.replace(',', '.').toDoubleOrNull()
    }
}

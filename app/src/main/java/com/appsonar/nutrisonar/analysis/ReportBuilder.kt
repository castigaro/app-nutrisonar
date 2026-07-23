package com.appsonar.nutrisonar.analysis

import com.appsonar.nutrisonar.data.Person
import com.appsonar.nutrisonar.data.Report
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Baut aus einem gespeicherten Bericht die Markdown-Datei nach der Struktur
 * der Projektanweisung: Gesamt-Ampel, Verlaufszeile, Kopf, Tabelle,
 * Wechselwirkungen, Kurzfazit, kurzer Hinweis.
 */
object ReportBuilder {

    private val DATE = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

    fun fileName(person: Person): String {
        val slug = person.name.lowercase(Locale.GERMANY)
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')
            .ifBlank { "person" }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
        return "analyse-$slug-$date.md"
    }

    fun markdown(person: Person, report: Report): String {
        val sb = StringBuilder()
        sb.appendLine("# Analyse Nahrungsergänzung — ${person.name}")
        sb.appendLine()
        sb.appendLine("**${report.ampelLine}**")
        sb.appendLine()
        if (person.history.isNotEmpty()) {
            sb.appendLine("_Verlauf: ${person.history.joinToString(" · ")}_")
            sb.appendLine()
        }

        sb.appendLine("**Datum:** ${DATE.format(Date(report.createdAt))}")
        val personParts = mutableListOf<String>()
        person.age?.let { personParts.add("$it Jahre") }
        person.weightKg?.let { personParts.add("${Aggregator.formatAmount(it)} kg") }
        if (person.medications.isNotBlank()) personParts.add("Medikamente: ${person.medications}")
        sb.appendLine("**Person:** ${person.name}" +
            (if (personParts.isEmpty()) "" else " (${personParts.joinToString(", ")})"))
        if (person.medications.isBlank()) {
            sb.appendLine()
            sb.appendLine("_Hinweis: Ohne Medikamentenliste ist keine vollständige " +
                "Wechselwirkungsprüfung möglich — jederzeit nachreichbar._")
        }
        sb.appendLine()

        sb.appendLine("**Produkte:** ${report.productLines.joinToString(" · ")}")
        sb.appendLine()

        sb.appendLine("| Nährstoff | Aufgenommen/Tag | Höchstmenge/Tag (Quelle) | Überschreitung | Mögliche Folgen/Nebenwirkungen |")
        sb.appendLine("|---|---|---|---|---|")
        report.rows.forEach { row ->
            val name = buildString {
                append(row.status.emoji).append(' ').append(row.nutrient)
                if (row.uncertain) append(" ⚠️ unsicher – bitte prüfen")
                row.note?.let { append(" _(").append(it).append(")_") }
            }
            val amount = "${Aggregator.formatAmount(row.amount)} ${row.unit}"
            val effects = report.effects[row.nutrient] ?: "—"
            sb.appendLine("| $name | $amount | ${row.limitText} | ${Aggregator.exceedText(row.percent)} | $effects |")
        }
        sb.appendLine()

        if (report.others.isNotEmpty()) {
            sb.appendLine("**Weitere Wirkstoffe (ohne UL):** " +
                report.others.joinToString(", ") {
                    "${it.nutrient} (${Aggregator.formatAmount(it.amount)} ${it.unit})" +
                        if (it.uncertain) " ⚠️" else ""
                })
            sb.appendLine()
        }

        sb.appendLine("## Wechselwirkungen")
        sb.appendLine()
        sb.appendLine(report.interactions.ifBlank { "Keine auffälligen Wechselwirkungen erkannt." })
        sb.appendLine()

        sb.appendLine("## Kurzfazit")
        sb.appendLine()
        sb.appendLine(report.summary)
        sb.appendLine()
        sb.appendLine("_Bewertung bei Bedarf mit Arzt oder Apotheke besprechen._")
        return sb.toString()
    }
}

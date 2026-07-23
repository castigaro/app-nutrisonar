package com.appsonar.nutrisonar

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.appsonar.nutrisonar.analysis.Aggregator
import com.appsonar.nutrisonar.analysis.ReportBuilder
import com.appsonar.nutrisonar.data.Person
import com.appsonar.nutrisonar.data.PersonStore
import com.appsonar.nutrisonar.data.Report
import com.appsonar.nutrisonar.data.ReportStore
import com.appsonar.nutrisonar.databinding.ActivityReportBinding
import com.appsonar.nutrisonar.databinding.RowReportNutrientBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Zeigt den gespeicherten Bericht nativ an; Teilen exportiert Markdown. */
class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private lateinit var person: Person
    private lateinit var report: Report

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val personId = intent.getStringExtra(EXTRA_PERSON_ID).orEmpty()
        val loadedPerson = PersonStore.get(this, personId)
        val loadedReport = ReportStore.get(this, personId)
        if (loadedPerson == null || loadedReport == null) {
            finish()
            return
        }
        person = loadedPerson
        report = loadedReport
        supportActionBar?.title = getString(R.string.report_title, person.name)

        render()
    }

    private fun render() {
        binding.ampelLine.text = report.ampelLine

        binding.historyLine.visibility =
            if (person.history.isEmpty()) View.GONE else View.VISIBLE
        binding.historyLine.text =
            getString(R.string.history_line, person.history.joinToString(" · "))

        val date = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(report.createdAt))
        val personParts = mutableListOf<String>()
        person.age?.let { personParts.add(getString(R.string.age_years, it)) }
        person.weightKg?.let { personParts.add(getString(R.string.weight_kg, Aggregator.formatAmount(it))) }
        if (person.medications.isNotBlank()) {
            personParts.add(getString(R.string.medications_line, person.medications))
        }
        binding.headText.text = buildString {
            append(getString(R.string.report_head, date, person.name))
            if (personParts.isNotEmpty()) append(" — ").append(personParts.joinToString(", "))
        }
        binding.medsMissingNote.visibility =
            if (person.medications.isBlank()) View.VISIBLE else View.GONE

        binding.productsText.text = report.productLines.joinToString("\n") { "• $it" }

        // Nährstoff-Zeilen als Karten (Tabelle passt nicht auf ein Handy).
        binding.nutrientRows.removeAllViews()
        report.rows.forEach { row ->
            val rowBinding = RowReportNutrientBinding.inflate(
                layoutInflater, binding.nutrientRows, true)
            rowBinding.rowTitle.text = buildString {
                append(row.status.emoji).append(' ').append(row.nutrient)
                if (row.uncertain) append("  ⚠️ ").append(getString(R.string.uncertain_check))
            }
            rowBinding.rowValues.text = getString(
                R.string.report_row_values,
                "${Aggregator.formatAmount(row.amount)} ${row.unit}",
                row.limitText,
                Aggregator.exceedText(row.percent),
            )
            row.note?.let {
                rowBinding.rowNote.visibility = View.VISIBLE
                rowBinding.rowNote.text = it
            }
            val effects = report.effects[row.nutrient]
            if (effects != null) {
                rowBinding.rowEffects.visibility = View.VISIBLE
                rowBinding.rowEffects.text = effects
            }
        }

        if (report.others.isNotEmpty()) {
            binding.othersTitle.visibility = View.VISIBLE
            binding.othersText.visibility = View.VISIBLE
            binding.othersText.text = report.others.joinToString("\n") {
                "• ${it.nutrient}: ${Aggregator.formatAmount(it.amount)} ${it.unit}/Tag" +
                    if (it.uncertain) " ⚠️" else ""
            }
        }

        binding.interactionsText.text = report.interactions
            .ifBlank { getString(R.string.no_interactions) }
        binding.summaryText.text = report.summary
    }

    private fun share() {
        val markdown = ReportBuilder.markdown(person, report)
        val dir = File(filesDir, "reports").apply { mkdirs() }
        val file = File(dir, ReportBuilder.fileName(person))
        file.writeText(markdown)
        val uri = FileProvider.getUriForFile(
            this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_report)))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_report, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_share -> {
            share()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_PERSON_ID = "personId"
    }
}

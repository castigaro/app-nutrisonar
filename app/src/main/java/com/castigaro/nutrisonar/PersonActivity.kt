package com.castigaro.nutrisonar

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.castigaro.common.llm.ProviderSettings
import com.castigaro.nutrisonar.analysis.Aggregator
import com.castigaro.nutrisonar.api.ReportApi
import com.castigaro.nutrisonar.data.Person
import com.castigaro.nutrisonar.data.PersonStore
import com.castigaro.nutrisonar.data.Product
import com.castigaro.nutrisonar.data.ProductStore
import com.castigaro.nutrisonar.data.Report
import com.castigaro.nutrisonar.data.ReportStore
import com.castigaro.nutrisonar.databinding.ActivityPersonBinding
import com.castigaro.nutrisonar.databinding.DialogScheduleBinding
import com.castigaro.nutrisonar.util.Photos
import com.castigaro.nutrisonar.util.Schedule
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Stand" einer Person: Produktliste, Erfassen, Analysieren, Simulieren. */
class PersonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonBinding
    private lateinit var adapter: ProductAdapter
    private lateinit var person: Person
    private var pendingPhoto: File? = null
    private var analyzing = false

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val photo = pendingPhoto
        pendingPhoto = null
        if (success && photo != null && photo.exists()) {
            Photos.downscale(photo)
            startActivity(
                Intent(this, CaptureActivity::class.java)
                    .putExtra(CaptureActivity.EXTRA_PHOTO_PATH, photo.absolutePath)
                    .putExtra(CaptureActivity.EXTRA_PERSON_ID, person.id)
            )
        } else {
            photo?.delete()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val loaded = PersonStore.get(this, intent.getStringExtra(EXTRA_PERSON_ID).orEmpty())
        if (loaded == null) {
            finish()
            return
        }
        person = loaded
        supportActionBar?.title = person.name

        adapter = ProductAdapter(
            onClick = { product -> showScheduleDialog(product) },
            onLongClick = { product -> confirmDelete(product) },
        )
        binding.productList.layoutManager = LinearLayoutManager(this)
        binding.productList.adapter = adapter

        binding.fabCapture.setOnClickListener {
            if (!ProviderSettings.isConfigured(this)) {
                Snackbar.make(binding.root, R.string.needs_key, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_settings) {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                    .show()
                return@setOnClickListener
            }
            val photo = Photos.newPhotoFile(this)
            pendingPhoto = photo
            takePicture.launch(Photos.uriFor(this, photo))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        renderHeader()
        val products = ProductStore.getFor(this, person.id)
        adapter.submit(products)
        binding.emptyState.visibility =
            if (products.isEmpty()) View.VISIBLE else View.GONE
        invalidateOptionsMenu()
    }

    private fun renderHeader() {
        val details = mutableListOf<String>()
        person.age?.let { details.add(getString(R.string.age_years, it)) }
        person.weightKg?.let { details.add(getString(R.string.weight_kg, Aggregator.formatAmount(it))) }
        binding.personDetails.text = if (details.isEmpty()) {
            getString(R.string.person_no_details)
        } else {
            details.joinToString(" · ")
        }
        binding.personMedications.text = if (person.medications.isBlank()) {
            getString(R.string.medications_missing)
        } else {
            getString(R.string.medications_line, person.medications)
        }
        binding.headerCard.setOnClickListener { showEditPersonDialog() }
    }

    private fun showEditPersonDialog() {
        val dialogBinding = com.castigaro.nutrisonar.databinding.DialogPersonBinding
            .inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.edit_person_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.inputName.text.toString().trim()
                if (name.isNotEmpty()) person.name = name
                person.age = dialogBinding.inputAge.text.toString().trim().toIntOrNull()
                person.weightKg = dialogBinding.inputWeight.text.toString().trim()
                    .replace(',', '.').toDoubleOrNull()
                person.medications = dialogBinding.inputMedications.text.toString().trim()
                PersonStore.save(this)
                supportActionBar?.title = person.name
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialogBinding.inputName.setText(person.name)
            dialogBinding.inputAge.setText(person.age?.toString().orEmpty())
            dialogBinding.inputWeight.setText(
                person.weightKg?.let { Aggregator.formatAmount(it).replace(',', '.') }.orEmpty()
            )
            dialogBinding.inputMedications.setText(person.medications)
        }
        dialog.show()
    }

    // ---- Produkte ----

    private fun showScheduleDialog(product: Product) {
        val dialogBinding = DialogScheduleBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(product.name)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = dialogBinding.inputProductName.text.toString().trim()
                if (newName.isNotEmpty()) product.name = newName
                val scheduleText = dialogBinding.inputSchedule.text.toString().trim()
                val pieces = Schedule.piecesPerDay(scheduleText)
                if (scheduleText.isNotEmpty() && pieces != null) {
                    product.scheduleText = scheduleText
                    product.piecesPerDay = pieces
                }
                ProductStore.save(this)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialogBinding.inputProductName.setText(product.name)
            dialogBinding.inputSchedule.setText(product.scheduleText, false)
            dialogBinding.inputSchedule.setSimpleItems(
                resources.getStringArray(R.array.schedule_suggestions))
        }
        dialog.show()
    }

    private fun confirmDelete(product: Product) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_product)
            .setMessage(getString(R.string.delete_product_message, product.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                ProductStore.delete(this, product)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Analyse ----

    private fun analyze() {
        if (analyzing) return
        val products = ProductStore.getFor(this, person.id)
        if (products.isEmpty()) {
            Snackbar.make(binding.root, R.string.no_products_yet, Snackbar.LENGTH_LONG).show()
            return
        }
        if (!ProviderSettings.isConfigured(this)) {
            Snackbar.make(binding.root, R.string.needs_key, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_settings) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .show()
            return
        }

        analyzing = true
        binding.analyzeProgress.visibility = View.VISIBLE
        binding.analyzeStatus.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val result = Aggregator.aggregate(products)
                val texts = ReportApi.texts(this@PersonActivity, person, products, result)
                val report = Report(
                    personId = person.id,
                    ampelLine = result.ampelLine,
                    rows = result.rows,
                    others = result.others,
                    effects = texts.effects,
                    interactions = texts.interactions,
                    summary = texts.summary,
                    productLines = products.map {
                        "${it.name} (${it.scheduleText}, " +
                            "${Aggregator.formatAmount(it.piecesPerDay)} St./Tag)"
                    },
                )
                ReportStore.save(this@PersonActivity, report)

                val date = SimpleDateFormat("dd.MM.", Locale.GERMANY).format(Date())
                val hadReport = person.history.isNotEmpty()
                person.history.add(if (hadReport) "$date aktualisiert" else "$date erstellt")
                PersonStore.save(this@PersonActivity)

                startActivity(
                    Intent(this@PersonActivity, ReportActivity::class.java)
                        .putExtra(ReportActivity.EXTRA_PERSON_ID, person.id)
                )
            } catch (e: Exception) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.analyze_error, e.message ?: "?"),
                    Snackbar.LENGTH_LONG,
                ).show()
            } finally {
                analyzing = false
                binding.analyzeProgress.visibility = View.GONE
                binding.analyzeStatus.visibility = View.GONE
            }
        }
    }

    // ---- Simulation: lokale Neuberechnung, nichts wird gespeichert ----

    private fun simulate() {
        val products = ProductStore.getFor(this, person.id)
        if (products.isEmpty()) {
            Snackbar.make(binding.root, R.string.no_products_yet, Snackbar.LENGTH_LONG).show()
            return
        }
        val names = products.map { "${it.name} (${it.scheduleText})" }.toTypedArray()
        val included = BooleanArray(products.size) { true }
        AlertDialog.Builder(this)
            .setTitle(R.string.simulate_title)
            .setMultiChoiceItems(names, included) { _, which, checked ->
                included[which] = checked
            }
            .setPositiveButton(R.string.simulate_run) { _, _ ->
                val subset = products.filterIndexed { index, _ -> included[index] }
                val result = Aggregator.aggregate(subset)
                showSimulationResult(subset.size, products.size, result)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSimulationResult(includedCount: Int, totalCount: Int, result: Aggregator.Result) {
        val lines = StringBuilder()
        lines.appendLine(result.ampelLine)
        lines.appendLine()
        val notable = result.rows.filter { it.status != Aggregator.Status.OK && it.status != Aggregator.Status.NONE }
        if (notable.isEmpty()) {
            lines.appendLine(getString(R.string.simulate_all_ok))
        } else {
            notable.forEach { row ->
                lines.appendLine(
                    "${row.status.emoji} ${row.nutrient}: " +
                        "${Aggregator.formatAmount(row.amount)} ${row.unit} " +
                        "(UL ${row.limitText}, ${Aggregator.exceedText(row.percent)})"
                )
            }
        }
        lines.appendLine()
        lines.append(getString(R.string.simulate_note, includedCount, totalCount))

        AlertDialog.Builder(this)
            .setTitle(R.string.simulate_result_title)
            .setMessage(lines.toString())
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    // ---- Menü ----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_person, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val hasReport = ReportStore.get(this, person.id) != null
        menu.findItem(R.id.action_show_report).isVisible = hasReport
        menu.findItem(R.id.action_analyze).title =
            getString(if (hasReport) R.string.action_update else R.string.action_analyze)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_analyze -> {
            analyze()
            true
        }
        R.id.action_simulate -> {
            simulate()
            true
        }
        R.id.action_show_report -> {
            startActivity(
                Intent(this, ReportActivity::class.java)
                    .putExtra(ReportActivity.EXTRA_PERSON_ID, person.id)
            )
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

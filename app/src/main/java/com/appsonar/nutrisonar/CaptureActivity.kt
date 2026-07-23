package com.appsonar.nutrisonar

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.appsonar.nutrisonar.analysis.Aggregator
import com.appsonar.nutrisonar.api.LabelApi
import com.appsonar.nutrisonar.data.NutrientAmount
import com.appsonar.nutrisonar.data.Product
import com.appsonar.nutrisonar.data.ProductStore
import com.appsonar.nutrisonar.databinding.ActivityCaptureBinding
import com.appsonar.nutrisonar.databinding.DialogNutrientBinding
import com.appsonar.nutrisonar.util.Schedule
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foto eines Präparats: Etikett per KI ablesen, Werte prüfen/korrigieren,
 * Einnahmeschema erfassen, speichern. Knappe Bestätigung — die Analyse
 * kommt erst später auf "Analysieren".
 */
class CaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaptureBinding
    private lateinit var adapter: NutrientAdapter
    private lateinit var photo: File
    private lateinit var personId: String
    private val nutrients = mutableListOf<NutrientAmount>()
    private var extracting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val path = intent.getStringExtra(EXTRA_PHOTO_PATH).orEmpty()
        personId = intent.getStringExtra(EXTRA_PERSON_ID).orEmpty()
        photo = File(path)
        if (!photo.exists() || personId.isEmpty()) {
            finish()
            return
        }
        binding.photoPreview.setImageBitmap(BitmapFactory.decodeFile(path))

        adapter = NutrientAdapter(
            onClick = { nutrient -> showNutrientDialog(nutrient) },
            onLongClick = { nutrient ->
                nutrients.remove(nutrient)
                adapter.submit(nutrients)
            },
        )
        binding.nutrientList.layoutManager = LinearLayoutManager(this)
        binding.nutrientList.adapter = adapter

        binding.inputSchedule.setSimpleItems(resources.getStringArray(R.array.schedule_suggestions))
        binding.inputSchedule.setText(getString(R.string.default_schedule), false)
        binding.inputSchedule.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = updateScheduleHint()
        })

        binding.buttonSave.setOnClickListener { save() }
        binding.buttonRetry.setOnClickListener { extract() }

        extract()
    }

    private fun extract() {
        extracting = true
        binding.progress.visibility = View.VISIBLE
        binding.statusText.setText(R.string.reading_label)
        binding.buttonRetry.visibility = View.GONE
        binding.buttonSave.isEnabled = false
        lifecycleScope.launch {
            try {
                val extraction = LabelApi.extract(this@CaptureActivity, photo)
                binding.inputProductName.setText(extraction.productName)
                nutrients.clear()
                nutrients.addAll(extraction.nutrients)
                adapter.submit(nutrients)
                val uncertain = nutrients.count { it.uncertain }
                binding.statusText.text = if (uncertain > 0) {
                    getString(R.string.label_read_uncertain, nutrients.size, uncertain)
                } else {
                    getString(R.string.label_read_ok, nutrients.size)
                }
                binding.buttonSave.isEnabled = true
                updateScheduleHint()
            } catch (e: Exception) {
                binding.statusText.text = getString(R.string.analyze_error, e.message ?: "?")
                binding.buttonRetry.visibility = View.VISIBLE
            } finally {
                extracting = false
                binding.progress.visibility = View.GONE
            }
        }
    }

    private fun updateScheduleHint() {
        val pieces = Schedule.piecesPerDay(binding.inputSchedule.text.toString())
        binding.scheduleHint.text = if (pieces == null) {
            getString(R.string.schedule_unreadable)
        } else {
            getString(R.string.schedule_pieces_per_day, Aggregator.formatAmount(pieces))
        }
    }

    private fun showNutrientDialog(nutrient: NutrientAmount) {
        val dialogBinding = DialogNutrientBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.edit_nutrient)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.inputNutrientName.text.toString().trim()
                val amount = dialogBinding.inputAmount.text.toString().trim()
                    .replace(',', '.').toDoubleOrNull()
                val unit = dialogBinding.inputUnit.text.toString().trim()
                if (name.isNotEmpty()) nutrient.name = name
                if (amount != null && amount > 0) nutrient.amountPerPiece = amount
                if (unit.isNotEmpty()) nutrient.unit = unit
                nutrient.uncertain = false // vom Nutzer geprüft
                adapter.submit(nutrients)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialogBinding.inputNutrientName.setText(nutrient.name)
            dialogBinding.inputAmount.setText(
                Aggregator.formatAmount(nutrient.amountPerPiece).replace(',', '.'))
            dialogBinding.inputUnit.setText(nutrient.unit, false)
            dialogBinding.inputUnit.setSimpleItems(arrayOf("mg", "µg", "g", "IE"))
        }
        dialog.show()
    }

    private fun save() {
        if (extracting) return
        val scheduleText = binding.inputSchedule.text.toString().trim()
        val pieces = Schedule.piecesPerDay(scheduleText)
        if (pieces == null) {
            Snackbar.make(binding.root, R.string.schedule_unreadable, Snackbar.LENGTH_LONG).show()
            return
        }
        if (nutrients.isEmpty()) {
            Snackbar.make(binding.root, R.string.no_nutrients, Snackbar.LENGTH_LONG).show()
            return
        }
        val product = Product(
            personId = personId,
            name = binding.inputProductName.text.toString().trim()
                .ifBlank { getString(R.string.unnamed_product) },
            scheduleText = scheduleText,
            piecesPerDay = pieces,
            photoPath = photo.absolutePath,
        )
        product.nutrients.addAll(nutrients)
        ProductStore.add(this, product)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_PHOTO_PATH = "photoPath"
        const val EXTRA_PERSON_ID = "personId"
    }
}

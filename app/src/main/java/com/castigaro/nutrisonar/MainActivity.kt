package com.castigaro.nutrisonar

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.castigaro.common.update.UpdateChecker
import com.castigaro.common.update.UpdateUi
import com.castigaro.nutrisonar.data.Person
import com.castigaro.nutrisonar.data.PersonStore
import com.castigaro.nutrisonar.data.ProductStore
import com.castigaro.nutrisonar.databinding.ActivityMainBinding
import com.castigaro.nutrisonar.databinding.DialogPersonBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PersonAdapter
    private val updateChecker = UpdateChecker(VERSION_URL, BuildConfig.VERSION_CODE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = PersonAdapter(
            onClick = { person -> openPerson(person) },
            onLongClick = { person -> confirmDelete(person) },
        )
        binding.personList.layoutManager = LinearLayoutManager(this)
        binding.personList.adapter = adapter

        binding.fabAddPerson.setOnClickListener { showPersonDialog(null) }

        // Stille Prüfung beim Start — zeigt nur etwas, wenn eine neue Version da ist.
        UpdateUi.checkSilently(this, binding.root, updateChecker)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val persons = PersonStore.getAll(this)
        adapter.submit(persons.map { person ->
            PersonAdapter.Entry(person, ProductStore.getFor(this, person.id).size)
        })
        binding.emptyState.visibility =
            if (persons.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openPerson(person: Person) {
        startActivity(
            Intent(this, PersonActivity::class.java)
                .putExtra(PersonActivity.EXTRA_PERSON_ID, person.id)
        )
    }

    /** Anlegen (person == null) oder Bearbeiten einer Person. */
    private fun showPersonDialog(person: Person?) {
        val dialogBinding = DialogPersonBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (person == null) R.string.add_person_title else R.string.edit_person_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.inputName.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val age = dialogBinding.inputAge.text.toString().trim().toIntOrNull()
                val weight = dialogBinding.inputWeight.text.toString().trim()
                    .replace(',', '.').toDoubleOrNull()
                val meds = dialogBinding.inputMedications.text.toString().trim()
                if (person == null) {
                    PersonStore.add(this, Person(name = name, age = age, weightKg = weight, medications = meds))
                } else {
                    person.name = name
                    person.age = age
                    person.weightKg = weight
                    person.medications = meds
                    PersonStore.save(this)
                }
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            person?.let {
                dialogBinding.inputName.setText(it.name)
                dialogBinding.inputAge.setText(it.age?.toString().orEmpty())
                dialogBinding.inputWeight.setText(
                    it.weightKg?.let { w -> if (w == w.toLong().toDouble()) w.toLong().toString() else w.toString() }.orEmpty()
                )
                dialogBinding.inputMedications.setText(it.medications)
            }
        }
        dialog.show()
    }

    private fun confirmDelete(person: Person) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_person)
            .setMessage(getString(R.string.delete_person_message, person.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                PersonStore.delete(this, person)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_help -> {
            startActivity(Intent(this, HelpActivity::class.java))
            true
        }
        R.id.action_check_update -> {
            UpdateUi.checkManually(this, binding.root, updateChecker)
            true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        private const val VERSION_URL =
            "https://appsonar.de/downloads/nutrisonar-version.json"
    }
}

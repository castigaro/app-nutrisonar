package com.appsonar.nutrisonar

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.appsonar.common.llm.ProviderSettingsController
import com.appsonar.nutrisonar.databinding.ActivitySettingsBinding

/**
 * Einstellungen rund um die KI-Anbindung. Oberfläche und Logik kommen aus
 * der gemeinsamen Bibliothek (provider_settings.xml +
 * ProviderSettingsController); gespeichert wird app-privat.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ProviderSettingsController(this, binding.providerSettings)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

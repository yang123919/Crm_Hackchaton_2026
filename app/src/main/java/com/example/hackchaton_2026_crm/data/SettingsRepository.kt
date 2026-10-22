package com.example.hackchaton_2026_crm.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class MessageLanguage(val label: String) { EN("EN"), BM("BM") }

data class Settings(
    val salespersonName: String = "Kelvin",
    val defaultOutlet: String? = null,
    val demoMode: Boolean = false,
    val whatsappLanguage: MessageLanguage = MessageLanguage.EN,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val NAME = stringPreferencesKey("salesperson_name")
        val OUTLET = stringPreferencesKey("default_outlet")
        val DEMO = booleanPreferencesKey("demo_mode")
        val LANGUAGE = stringPreferencesKey("whatsapp_language")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            salespersonName = p[Keys.NAME] ?: Settings().salespersonName,
            defaultOutlet = p[Keys.OUTLET],
            demoMode = p[Keys.DEMO] ?: Settings().demoMode,
            whatsappLanguage = p[Keys.LANGUAGE]
                ?.let { runCatching { MessageLanguage.valueOf(it) }.getOrNull() }
                ?: MessageLanguage.EN,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setSalespersonName(name: String) = context.dataStore.edit { it[Keys.NAME] = name }

    suspend fun setDefaultOutlet(outlet: String?) = context.dataStore.edit {
        if (outlet == null) it.remove(Keys.OUTLET) else it[Keys.OUTLET] = outlet
    }

    suspend fun setDemoMode(on: Boolean) = context.dataStore.edit { it[Keys.DEMO] = on }

    suspend fun setWhatsappLanguage(language: MessageLanguage) =
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
}

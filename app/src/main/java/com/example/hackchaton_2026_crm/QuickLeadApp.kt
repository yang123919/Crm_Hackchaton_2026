package com.example.hackchaton_2026_crm

import android.app.Application
import com.example.hackchaton_2026_crm.data.AppDatabase
import com.example.hackchaton_2026_crm.data.LeadRepository
import com.example.hackchaton_2026_crm.data.SeedData
import com.example.hackchaton_2026_crm.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; small enough that a DI framework isn't worth it. */
class AppContainer(app: Application) {
    val database = AppDatabase.build(app)
    val leadRepository = LeadRepository(database.leadDao())
    val settingsRepository = SettingsRepository(app)
}

class QuickLeadApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { SeedData.seedIfEmpty(container.database.leadDao()) }
    }
}

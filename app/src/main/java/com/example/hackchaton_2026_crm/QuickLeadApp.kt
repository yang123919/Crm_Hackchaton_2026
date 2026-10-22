package com.example.hackchaton_2026_crm

import android.app.Application
import com.example.hackchaton_2026_crm.ai.AiRouter
import com.example.hackchaton_2026_crm.ai.DemoAiService
import com.example.hackchaton_2026_crm.ai.GeminiAiService
import com.example.hackchaton_2026_crm.auth.AuthRepository
import com.example.hackchaton_2026_crm.auth.DataStoreSessionStore
import com.example.hackchaton_2026_crm.data.AppDatabase
import com.example.hackchaton_2026_crm.data.LeadRepository
import com.example.hackchaton_2026_crm.data.SeedData
import com.example.hackchaton_2026_crm.data.SettingsRepository
import com.example.hackchaton_2026_crm.reminder.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; small enough that a DI framework isn't worth it. */
class AppContainer(app: Application) {
    val database = AppDatabase.build(app)
    val leadRepository = LeadRepository(database.leadDao())
    val settingsRepository = SettingsRepository(app)

    val authRepository = AuthRepository(
        dao = database.userDao(),
        session = DataStoreSessionStore(app),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        // The salesperson's name signs WhatsApp messages and greets them on Today.
        onSignedIn = { settingsRepository.setSalespersonName(it.displayName) },
    )

    /** Real Gemini when GEMINI_API_KEY is set in local.properties; otherwise always Demo mode. */
    val aiService = AiRouter(
        settings = settingsRepository,
        real = BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
            ?.let { GeminiAiService(it, BuildConfig.GEMINI_MODEL) },
        demo = DemoAiService(),
    )
}

class QuickLeadApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { SeedData.seedIfEmpty(container.database.leadDao()) }
        Reminders.schedule(this)
    }
}

package com.example.hackchaton_2026_crm.ai

import com.example.hackchaton_2026_crm.data.FollowUpEntry
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.MessageLanguage
import com.example.hackchaton_2026_crm.data.SettingsRepository
import java.time.LocalDate

interface AiService {
    /** Spoken notes about a new customer → CRM fields. Output is NOT validated; run it through [Normalizer]. */
    suspend fun extractLead(transcript: String, today: LocalDate): LeadExtraction

    /** Spoken update after a call/visit → dated log entry, next step and suggested stage. */
    suspend fun extractFollowUp(transcript: String, lead: Lead, today: LocalDate): FollowUpExtraction

    /** Short, polite WhatsApp message to the customer. */
    suspend fun draftWhatsApp(
        lead: Lead,
        lastEntry: FollowUpEntry?,
        language: MessageLanguage,
        salesperson: String,
    ): String
}

/**
 * Picks the real LLM or the canned [DemoAiService] on every call, so flipping Demo mode
 * (or having no API key) takes effect immediately.
 */
class AiRouter(
    private val settings: SettingsRepository,
    private val real: AiService?,
    val demo: AiService,
) : AiService {
    val hasRealAi: Boolean get() = real != null

    suspend fun usesDemo(): Boolean = real == null || settings.current().demoMode

    private suspend fun pick(): AiService = if (usesDemo()) demo else real!!

    override suspend fun extractLead(transcript: String, today: LocalDate) =
        pick().extractLead(transcript, today)

    override suspend fun extractFollowUp(transcript: String, lead: Lead, today: LocalDate) =
        pick().extractFollowUp(transcript, lead, today)

    override suspend fun draftWhatsApp(
        lead: Lead,
        lastEntry: FollowUpEntry?,
        language: MessageLanguage,
        salesperson: String,
    ) = pick().draftWhatsApp(lead, lastEntry, language, salesperson)
}

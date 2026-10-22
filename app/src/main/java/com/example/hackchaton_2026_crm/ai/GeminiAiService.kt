package com.example.hackchaton_2026_crm.ai

import com.example.hackchaton_2026_crm.data.FollowUpEntry
import com.example.hackchaton_2026_crm.data.HealthIssue
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.MessageLanguage
import com.example.hackchaton_2026_crm.data.SeedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Gemini over plain HTTPS with JSON-schema output. Demo only: the key ships in the APK;
 * production would proxy through GCR's backend.
 */
class GeminiAiService(
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient = defaultClient,
) : AiService {

    override suspend fun extractLead(transcript: String, today: LocalDate): LeadExtraction =
        decode(generate(AiPrompts.leadSystem(today), transcript, AiPrompts.leadSchema))

    override suspend fun extractFollowUp(transcript: String, lead: Lead, today: LocalDate): FollowUpExtraction =
        decode(generate(AiPrompts.followUpSystem(lead, today), transcript, AiPrompts.followUpSchema))

    override suspend fun draftWhatsApp(
        lead: Lead,
        lastEntry: FollowUpEntry?,
        language: MessageLanguage,
        salesperson: String,
    ): String = generate(
        AiPrompts.whatsAppSystem(language, salesperson),
        AiPrompts.whatsAppUser(lead, lastEntry, LocalDate.now(SeedData.ZONE)),
        schema = null,
        // Creative text: higher than extraction so "Regenerate" gives a fresh wording.
        temperature = 0.9,
    ).trim()

    override suspend fun explainDataHealth(issues: List<HealthIssue>): HealthAdvice =
        decode(generate(AiPrompts.healthSystem, AiPrompts.healthUser(issues), AiPrompts.healthSchema))

    private inline fun <reified T> decode(text: String): T = try {
        AiJson.decodeFromString<T>(stripFences(text))
    } catch (e: SerializationException) {
        throw AiException("AI returned an unexpected format", e)
    } catch (e: IllegalArgumentException) {
        throw AiException("AI returned an unexpected format", e)
    }

    /** Low [temperature] for field extraction, where the same notes must give the same fields. */
    private suspend fun generate(system: String, user: String, schema: JsonObject?, temperature: Double = 0.2): String {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", user) } }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                if (schema != null) {
                    put("responseMimeType", "application/json")
                    put("responseSchema", schema)
                }
            }
        }
        // Gemini models return 503 "high demand" in spikes; a busy model fails in a second or two,
        // so trying the next one keeps the demo going instead of showing an error.
        var failure: AiException? = null
        for (name in listOf(model) + FALLBACK_MODELS.filter { it != model }) {
            try {
                return generateWith(name, body)
            } catch (e: AiException) {
                if (!e.retryable) throw e
                failure = e
            }
        }
        throw failure ?: AiException("AI unavailable")
    }

    private suspend fun generateWith(modelName: String, body: JsonObject): String {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON))
            .build()

        val raw = runInterruptible(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val code = response.code
                        throw AiException("AI error $code", retryable = code == 404 || code == 429 || code >= 500)
                    }
                    text
                }
            } catch (e: IOException) {
                // Not retried: a timeout already used the full 30 s.
                throw AiException("Couldn't reach AI", e)
            }
        }
        return try {
            AiJson.parseToJsonElement(raw).jsonObject["candidates"]!!.jsonArray[0]
                .jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray
                .joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                .ifBlank { throw AiException("AI returned an empty answer") }
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException("AI returned an unexpected format", e)
        }
    }

    private fun stripFences(text: String) =
        text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /**
         * Tried in order after the configured model when it is busy or retired. Checked against a new
         * free-tier key on 2026-10-23: the 2.5 models are closed to new keys; flash-lite is slow (~20 s)
         * but rarely busy, so it is last. Model names change over time; update this list when they do.
         */
        private val FALLBACK_MODELS = listOf(
            "gemini-3.5-flash",
            "gemini-3.6-flash",
            "gemini-3.7-flash",
            "gemini-flash-lite-latest",
        )

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

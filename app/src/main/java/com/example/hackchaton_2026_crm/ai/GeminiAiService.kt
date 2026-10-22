package com.example.hackchaton_2026_crm.ai

import com.example.hackchaton_2026_crm.data.FollowUpEntry
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.MessageLanguage
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
        AiPrompts.whatsAppUser(lead, lastEntry),
        schema = null,
    ).trim()

    private inline fun <reified T> decode(text: String): T = try {
        AiJson.decodeFromString<T>(stripFences(text))
    } catch (e: SerializationException) {
        throw AiException("AI returned an unexpected format", e)
    } catch (e: IllegalArgumentException) {
        throw AiException("AI returned an unexpected format", e)
    }

    private suspend fun generate(system: String, user: String, schema: JsonObject?): String {
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
                put("temperature", 0.2)
                if (schema != null) {
                    put("responseMimeType", "application/json")
                    put("responseSchema", schema)
                }
            }
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON))
            .build()

        val raw = runInterruptible(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw AiException("AI error ${response.code}")
                    text
                }
            } catch (e: IOException) {
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

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

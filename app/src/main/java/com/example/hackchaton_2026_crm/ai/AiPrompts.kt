package com.example.hackchaton_2026_crm.ai

import com.example.hackchaton_2026_crm.data.FollowUpEntry
import com.example.hackchaton_2026_crm.data.HealthIssue
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.MessageLanguage
import com.example.hackchaton_2026_crm.data.ReferenceLists
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** System prompts and Gemini response schemas (OpenAPI subset) for the AI contract in docs/PLAN.md. */
object AiPrompts {
    private val todayFormat = DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd", Locale.ENGLISH)

    private fun commonRules(today: LocalDate) = """
        The notes are Malaysian English and may mix in Malay or Chinese words
        (e.g. "esok" = tomorrow, "lusa" = day after tomorrow, "nak" = wants, "minggu depan" = next week).
        Today is ${todayFormat.format(today)} in Asia/Kuala_Lumpur. Resolve relative dates
        ("next Tuesday", "Friday", "esok", "this weekend") to YYYY-MM-DD. Follow-up dates are never in the past.
        The text is typed notes or text read from a photographed form by OCR, so it may contain typos or
        misread characters (O for 0, l for 1), mixed English and Malay, and form labels such as "Nama:" or
        "No. Tel:". Use the allowed lists below to resolve such mistakes when one option is clearly meant,
        but never guess between several.
        Never invent data: anything not said is null.
        suggested_sales_stage must be exactly one of: ${ReferenceLists.salesStages.joinToString(" | ")}; or null.
    """.trimIndent()

    fun leadSystem(today: LocalDate) = """
        You convert a car salesperson's spoken notes about a new customer into CRM fields for the CS Group dealership CRM.
        ${commonRules(today)}
        product must be exactly one of: ${ReferenceLists.products.joinToString(" | ")}; or null.
        outlet must be exactly one of: ${ReferenceLists.outlets.joinToString(" | ")}; or null.
        Names: drop titles (Mr, Mrs, Ms, Puan, Encik, Cik, Dato, Madam). The last spoken word of the name is last_name, the rest is first_name.
        mobile: the digits as spoken; spoken digits ("one seven three") become numbers.
        budget_rm is a number in ringgit ("120k" -> 120000).
        trade_in.used_car is true only if the customer will trade in a car; brand is the make (Myvi -> Perodua).
        follow_up.summary: one or two short English sentences written as a CRM log entry.
        follow_up.next_action: a short imperative, e.g. "Call back about test drive".
        follow_up.next_date: when the salesperson should act next.
        ic_number: a Malaysian IC number written YYMMDD-PB-####, only if one is in the notes.
        address: the customer's address as written, only if given. gender: "Male" or "Female" only if stated.
        Put the names of fields you are unsure about (e.g. "mobile", "last_name") in low_confidence_fields.
    """.trimIndent()

    fun followUpSystem(lead: Lead, today: LocalDate) = """
        You turn a car salesperson's spoken update about an existing customer into a CRM follow-up log entry.
        ${commonRules(today)}
        Customer: ${lead.fullName}, product ${lead.product}, current stage ${lead.salesStage}.
        Previous next action: ${lead.nextAction ?: "none"}.
        follow_up.summary: one or two short English sentences written as a CRM log entry.
        follow_up.next_action: a short imperative. follow_up.next_date: when the salesperson should act next.
        suggested_sales_stage: the stage after this update; keep the current stage if nothing changed.
    """.trimIndent()

    fun whatsAppSystem(language: MessageLanguage, salesperson: String) = """
        You write short, polite WhatsApp messages from $salesperson, a car salesperson at CS Group, to a customer.
        Write in ${if (language == MessageLanguage.BM) "Bahasa Malaysia" else "English"}.
        Never mention a date that is before today; if the follow-up is overdue, just ask when suits them.
        At most 60 words, friendly and professional, at most one emoji, no hashtags, no placeholders like [name].
        Sign off with "- $salesperson". Reply with the message text only.
    """.trimIndent()

    fun whatsAppUser(lead: Lead, lastEntry: FollowUpEntry?, today: LocalDate) = buildString {
        appendLine("Today: ${todayFormat.format(today)}")
        appendLine("Customer: ${lead.fullName}")
        appendLine("Car: ${lead.product}")
        appendLine("Stage: ${lead.salesStage}")
        lead.nextAction?.let { appendLine("Next action: $it") }
        lead.nextFollowUpAt?.let { appendLine("Next date: $it") }
        lastEntry?.let { appendLine("Last note (${it.date}): ${it.summary}") }
    }

    val healthSystem = """
        You are a data-quality assistant inside a car dealership CRM (CS Group, Malaysia).
        You receive a numbered list of data problems that a rule-based scan already found. Do not look for new ones.
        For each problem write ONE short, plain-English sentence for a busy salesperson: why it matters and what to do.
        At most 25 words each. Use only the facts given; never invent names, numbers or dates.
        Also write a "summary" of at most 30 words about the overall state of the data.
        Do not state counts in the summary; the screen already shows them.
        "explanations" must hold exactly one string per problem, in the same order as the list.
    """.trimIndent()

    fun healthUser(issues: List<HealthIssue>) = buildString {
        issues.forEachIndexed { i, issue ->
            appendLine("${i + 1}. [${issue.kind.label}, ${issue.severity.name.lowercase()}] ${issue.title}")
            appendLine("   Found: ${issue.detail}")
            appendLine("   Suggested: ${issue.fix?.label ?: "salesperson corrects it by hand"}")
        }
    }

    val healthSchema: JsonObject = obj {
        props {
            putJsonObject("summary") { put("type", "STRING") }
            putJsonObject("explanations") {
                put("type", "ARRAY")
                putJsonObject("items") { put("type", "STRING") }
            }
        }
        required("summary", "explanations")
    }

    val leadSchema: JsonObject = obj {
        props {
            nullableString("first_name")
            nullableString("last_name")
            nullableString("mobile")
            nullableString("email")
            nullableString("product", ReferenceLists.products)
            nullableString("outlet", ReferenceLists.outlets)
            putJsonObject("budget_rm") { put("type", "NUMBER"); put("nullable", true) }
            nullableString("expected_close_date")
            putJsonObject("trade_in") {
                put("type", "OBJECT")
                props {
                    putJsonObject("used_car") { put("type", "BOOLEAN") }
                    nullableString("brand")
                    nullableString("model")
                    putJsonObject("year") { put("type", "INTEGER"); put("nullable", true) }
                }
                required("used_car")
            }
            putJsonObject("follow_up") { followUpSchema() }
            nullableString("suggested_sales_stage", ReferenceLists.salesStages)
            nullableString("ic_number")
            nullableString("address")
            nullableString("gender", listOf("Male", "Female"))
            putJsonObject("low_confidence_fields") {
                put("type", "ARRAY")
                putJsonObject("items") { put("type", "STRING") }
            }
        }
        required("trade_in", "follow_up", "low_confidence_fields")
    }

    val followUpSchema: JsonObject = obj {
        props {
            putJsonObject("follow_up") { followUpSchema() }
            nullableString("suggested_sales_stage", ReferenceLists.salesStages)
        }
        required("follow_up")
    }

    private fun JsonObjectBuilder.followUpSchema() {
        put("type", "OBJECT")
        props {
            putJsonObject("summary") { put("type", "STRING") }
            putJsonObject("next_action") { put("type", "STRING") }
            nullableString("next_date")
        }
        required("summary", "next_action")
    }

    private fun obj(block: JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("type", "OBJECT")
        block()
    }

    private fun JsonObjectBuilder.props(block: JsonObjectBuilder.() -> Unit) = putJsonObject("properties", block)

    private fun JsonObjectBuilder.required(vararg names: String) =
        putJsonArray("required") { names.forEach { add(it) } }

    private fun JsonObjectBuilder.nullableString(name: String, options: List<String>? = null) =
        putJsonObject(name) {
            put("type", "STRING")
            put("nullable", true)
            if (options != null) putJsonArray("enum") { options.forEach { add(it) } }
        }
}

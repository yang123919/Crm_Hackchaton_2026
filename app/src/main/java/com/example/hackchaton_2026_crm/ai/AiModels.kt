package com.example.hackchaton_2026_crm.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lenient parser for LLM output: unknown keys and bad enum values must not crash the app. */
val AiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

@Serializable
data class TradeIn(
    @SerialName("used_car") val usedCar: Boolean = false,
    val brand: String? = null,
    val model: String? = null,
    val year: Int? = null,
)

@Serializable
data class FollowUpPart(
    val summary: String = "",
    @SerialName("next_action") val nextAction: String = "",
    /** YYYY-MM-DD */
    @SerialName("next_date") val nextDate: String? = null,
)

/** Raw AI answer for a new customer; field names match the AI contract in docs/PLAN.md. */
@Serializable
data class LeadExtraction(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val mobile: String? = null,
    val email: String? = null,
    val product: String? = null,
    val outlet: String? = null,
    @SerialName("budget_rm") val budgetRm: Double? = null,
    /** YYYY-MM-DD */
    @SerialName("expected_close_date") val expectedCloseDate: String? = null,
    @SerialName("trade_in") val tradeIn: TradeIn = TradeIn(),
    @SerialName("follow_up") val followUp: FollowUpPart = FollowUpPart(),
    @SerialName("suggested_sales_stage") val suggestedSalesStage: String? = null,
    @SerialName("low_confidence_fields") val lowConfidenceFields: List<String> = emptyList(),
    /** YYMMDD-PB-#### when the notes mention one (typed, or read from a scanned form). */
    @SerialName("ic_number") val icNumber: String? = null,
    val address: String? = null,
    val gender: String? = null,
)

/** Raw AI answer for a spoken follow-up update on an existing customer. */
@Serializable
data class FollowUpExtraction(
    @SerialName("follow_up") val followUp: FollowUpPart = FollowUpPart(),
    @SerialName("suggested_sales_stage") val suggestedSalesStage: String? = null,
)

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

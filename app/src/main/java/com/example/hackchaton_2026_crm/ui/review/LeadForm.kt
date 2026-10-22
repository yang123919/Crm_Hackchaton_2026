package com.example.hackchaton_2026_crm.ui.review

import com.example.hackchaton_2026_crm.ai.LeadField
import com.example.hackchaton_2026_crm.ai.NormalizedLead
import com.example.hackchaton_2026_crm.ai.Normalizer
import com.example.hackchaton_2026_crm.data.CaptureMetric
import com.example.hackchaton_2026_crm.data.FollowUpEntry
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.ReferenceLists
import com.example.hackchaton_2026_crm.ocr.IcInfo
import com.example.hackchaton_2026_crm.ocr.IcNumbers
import java.time.Instant
import java.time.LocalDate

/** Editable state of the Review & confirm form. Text fields stay as typed; parsing happens on save. */
data class LeadForm(
    val firstName: String = "",
    val lastName: String = "",
    val mobile: String = "",
    val email: String = "",
    /** Formatted YYMMDD-PB-#### (or what the user is still typing). */
    val icNumber: String = "",
    val address: String = "",
    /** "Male", "Female" or empty. */
    val gender: String = "",
    val product: String? = null,
    val outlet: String? = null,
    val budget: String = "",
    val closeDate: LocalDate? = null,
    val tradeIn: Boolean = false,
    val tradeInBrand: String = "",
    val tradeInModel: String = "",
    val tradeInYear: String = "",
    val nextAction: String = "",
    val nextDate: LocalDate? = null,
    val stage: String = ReferenceLists.DEFAULT_STAGE,
    val summary: String = "",
    /** ✨ marks. */
    val aiFilled: Set<LeadField> = emptySet(),
    /** "!" marks; cleared once the user edits the field. */
    val lowConfidence: Set<LeadField> = emptySet(),
) {
    val normalizedMobile: String? get() = Normalizer.phone(mobile)

    /** Required: first name, mobile, product, outlet (wireframe 4). Optional fields only fail if filled wrongly. */
    fun errors(today: LocalDate): Map<LeadField, String> = buildMap {
        if (firstName.isBlank()) put(LeadField.FIRST_NAME, "Required")
        when {
            mobile.isBlank() -> put(LeadField.MOBILE, "Required")
            normalizedMobile == null -> put(LeadField.MOBILE, "Not a valid Malaysian mobile (01X-XXX XXXX)")
        }
        if (product == null) put(LeadField.PRODUCT, "Required")
        if (outlet == null) put(LeadField.OUTLET, "Required")
        if (!Normalizer.isValidEmail(email)) put(LeadField.EMAIL, "Not a valid email")
        if (icNumber.isNotBlank() && IcNumbers.normalize(icNumber, today) == null) {
            put(LeadField.IC_NUMBER, "Not a valid IC number (YYMMDD-PB-####)")
        }
        if (budget.isNotBlank() && Normalizer.budget(budget) == null) put(LeadField.BUDGET, "Enter an amount, e.g. 120000")
        if (tradeIn && tradeInYear.isNotBlank() && Normalizer.year(tradeInYear.toIntOrNull(), today) == null) {
            put(LeadField.TRADE_IN, "Year looks wrong")
        }
        if (nextDate != null && nextDate.isBefore(today)) put(LeadField.NEXT_DATE, "Pick today or later")
    }

    fun toLead(today: LocalDate): Lead = Lead(
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        mobile = normalizedMobile ?: error("Invalid mobile"),
        email = email.trim().ifBlank { null },
        icNumber = IcNumbers.normalize(icNumber, today),
        address = address.trim().ifBlank { null },
        gender = gender.ifBlank { null },
        product = product ?: error("No product"),
        outlet = outlet ?: error("No outlet"),
        salesStage = stage,
        expectedCloseDate = closeDate,
        budgetRm = Normalizer.budget(budget),
        tradeInUsedCar = tradeIn,
        tradeInBrand = tradeInBrand.trim().ifBlank { null }.takeIf { tradeIn },
        tradeInModel = tradeInModel.trim().ifBlank { null }.takeIf { tradeIn },
        tradeInYear = Normalizer.year(tradeInYear.toIntOrNull(), today).takeIf { tradeIn },
        nextFollowUpAt = nextDate,
        nextAction = nextAction.trim().ifBlank { null },
    )

    fun toFirstEntry(today: LocalDate, transcript: String) = FollowUpEntry(
        leadId = 0,
        date = today,
        summary = summary.trim().ifBlank { "New customer captured." },
        nextAction = nextAction.trim(),
        nextDate = nextDate,
        rawTranscript = transcript,
    )

    /**
     * Puts what was read from a scanned IC on top of the form. The card is more reliable than
     * speech for who the person is, so it wins for name, IC, address and gender; the transcript
     * still supplies phone, car, budget and follow-up.
     */
    fun withIc(ic: IcInfo): LeadForm {
        if (ic.isEmpty) return this
        val filled = aiFilled.toMutableSet()
        val check = lowConfidence.toMutableSet()
        fun LeadForm.use(field: LeadField, has: Boolean, apply: LeadForm.() -> LeadForm): LeadForm =
            if (!has) this else apply().also { filled += field; check -= field }
        var f = this
        f = f.use(LeadField.FIRST_NAME, ic.firstName.isNotEmpty()) { copy(firstName = ic.firstName) }
        f = f.use(LeadField.LAST_NAME, ic.lastName.isNotEmpty()) { copy(lastName = ic.lastName) }
        f = f.use(LeadField.IC_NUMBER, ic.icNumber.isNotEmpty()) { copy(icNumber = ic.icNumber) }
        f = f.use(LeadField.ADDRESS, ic.address.isNotEmpty()) { copy(address = ic.address) }
        f = f.use(LeadField.GENDER, ic.gender.isNotEmpty()) { copy(gender = ic.gender) }
        ic.warnings.forEach { w ->
            when {
                w.startsWith("IC number") -> check += LeadField.IC_NUMBER
                w.startsWith("Name") -> check += LeadField.FIRST_NAME
                w.startsWith("Address") -> check += LeadField.ADDRESS
                w.startsWith("Gender") -> check += LeadField.GENDER
            }
        }
        return f.copy(aiFilled = filled, lowConfidence = check)
    }

    companion object {
        fun from(n: NormalizedLead, defaultOutlet: String?) = LeadForm(
            firstName = n.firstName,
            lastName = n.lastName,
            mobile = n.mobile,
            email = n.email,
            product = n.product,
            outlet = n.outlet ?: defaultOutlet,
            budget = n.budgetRm?.toString().orEmpty(),
            closeDate = n.expectedCloseDate,
            tradeIn = n.tradeIn,
            tradeInBrand = n.tradeInBrand,
            tradeInModel = n.tradeInModel,
            tradeInYear = n.tradeInYear?.toString().orEmpty(),
            nextAction = n.nextAction,
            nextDate = n.nextDate,
            stage = n.salesStage,
            summary = n.summary,
            aiFilled = n.aiFilled,
            lowConfidence = n.lowConfidence + if (n.mobile.isNotEmpty() && !n.mobileValid) setOf(LeadField.MOBILE) else emptySet(),
        )

        fun metric(startedAt: Instant) =
            CaptureMetric(startedAt = startedAt, savedAt = Instant.now(), type = CaptureMetric.TYPE_NEW_LEAD)
    }
}

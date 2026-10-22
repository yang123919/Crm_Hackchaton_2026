package com.example.hackchaton_2026_crm.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Malaysian driving licence stages (JPJ). */
enum class LicenceType(val label: String) {
    /** Competent Driving Licence: the full licence. */
    CDL("CDL (full)"),
    /** Probationary Driving Licence: the first 2 years after passing, red "P" on the card. */
    PDL("PDL (P)"),
    /** Learner's Driving Licence: may only drive with an instructor. */
    LDL("LDL (learner)"),
    ;

    companion object {
        fun fromName(name: String?): LicenceType? = entries.firstOrNull { it.name == name }
    }
}

enum class BookingKind(val label: String, val stage: String) {
    TEST_DRIVE("Test drive", "Test Drive"),
    BOOKING("Booking", "Booking"),
}

/**
 * Who may book a test drive or a vehicle booking. Company policy (CS Group): only a full CDL,
 * with a car class, not expired. All test-drive cars are automatic, so D and DA both qualify.
 */
object LicenceRules {
    val carClasses = setOf("D", "DA")

    data class Result(val allowed: Boolean, val reason: String)

    private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun check(
        type: LicenceType?,
        classes: List<String>,
        validTo: LocalDate?,
        today: LocalDate,
        /** False when the licence's IC number differs from the customer's. */
        sameIc: Boolean = true,
    ): Result = when {
        !sameIc -> Result(false, "This licence belongs to someone else: its IC number doesn't match the customer's.")
        type == null -> Result(false, "Confirm the licence type (CDL, PDL or LDL).")
        type == LicenceType.LDL -> Result(false, "Learner licence (LDL): cannot test drive.")
        type == LicenceType.PDL -> Result(false, "P licence (PDL): company policy allows CDL holders only.")
        validTo == null -> Result(false, "Licence expiry date not read. Rescan the licence.")
        validTo.isBefore(today) -> Result(false, "Licence expired on ${dayFormat.format(validTo)}.")
        classes.isEmpty() -> Result(false, "Licence class not read. Rescan the licence.")
        classes.none { it in carClasses } -> Result(false, "No car class (D or DA) on this licence.")
        else -> Result(true, "${summary(type, classes, validTo)}: OK to book.")
    }

    /** "CDL · D · valid to 4 May 2027". */
    fun summary(type: LicenceType?, classes: List<String>, validTo: LocalDate?): String = listOfNotNull(
        type?.name ?: "Type not confirmed",
        classes.takeIf { it.isNotEmpty() }?.joinToString(" "),
        validTo?.let { "valid to ${dayFormat.format(it)}" },
    ).joinToString(" · ")

    /** Stored on the lead as space-separated codes. */
    fun parseClasses(stored: String?): List<String> =
        stored.orEmpty().split(' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
}

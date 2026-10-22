package com.example.hackchaton_2026_crm.ai

import com.example.hackchaton_2026_crm.data.ReferenceLists
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Form fields of a new lead; [key] is the AI contract name used in `low_confidence_fields`. */
enum class LeadField(val key: String) {
    FIRST_NAME("first_name"),
    LAST_NAME("last_name"),
    MOBILE("mobile"),
    EMAIL("email"),
    PRODUCT("product"),
    OUTLET("outlet"),
    BUDGET("budget_rm"),
    CLOSE_DATE("expected_close_date"),
    TRADE_IN("trade_in"),
    NEXT_ACTION("next_action"),
    NEXT_DATE("next_date"),
    STAGE("suggested_sales_stage"),
    SUMMARY("summary");

    companion object {
        private val tradeInKeys = setOf("used_car", "brand", "model", "year")

        /** Accepts "mobile", "follow_up.next_date", "trade_in.brand", ... */
        fun fromKey(key: String): LeadField? {
            val last = key.substringAfterLast('.').trim().lowercase()
            if (last in tradeInKeys || key.startsWith("trade_in")) return TRADE_IN
            return entries.firstOrNull { it.key == last } ?: if (last == "sales_stage") STAGE else null
        }
    }
}

/** AI output after validation. Values the AI got wrong are null (or flagged) instead of trusted. */
data class NormalizedLead(
    val firstName: String = "",
    val lastName: String = "",
    /** 60XXXXXXXXX when valid, otherwise the digits as heard so the user can fix them. */
    val mobile: String = "",
    val mobileValid: Boolean = false,
    val email: String = "",
    val product: String? = null,
    val outlet: String? = null,
    val budgetRm: Long? = null,
    val expectedCloseDate: LocalDate? = null,
    val tradeIn: Boolean = false,
    val tradeInBrand: String = "",
    val tradeInModel: String = "",
    val tradeInYear: Int? = null,
    val nextAction: String = "",
    val nextDate: LocalDate? = null,
    val salesStage: String = ReferenceLists.DEFAULT_STAGE,
    val summary: String = "",
    val aiFilled: Set<LeadField> = emptySet(),
    val lowConfidence: Set<LeadField> = emptySet(),
)

object Normalizer {

    // ---- Phone ------------------------------------------------------------------------

    /** Digits only, with the Malaysian country code added: "012-345 6789" → "60123456789". */
    fun phoneDigits(raw: String?): String {
        val d = raw.orEmpty().filter(Char::isDigit)
        return when {
            d.isEmpty() -> ""
            d.startsWith("60") -> d
            d.startsWith("0") -> "6$d"
            d.startsWith("1") -> "60$d"
            else -> d
        }
    }

    /** Malaysian mobile: 601X + 7-8 digits (011 numbers are one digit longer). */
    fun isValidMobile(normalized: String): Boolean = when {
        !normalized.startsWith("601") -> false
        normalized.startsWith("6011") -> normalized.length == 12
        else -> normalized.length == 11
    }

    /** Normalised `60XXXXXXXXX`, or null if it isn't a valid Malaysian mobile number. */
    fun phone(raw: String?): String? = phoneDigits(raw).takeIf(::isValidMobile)

    private val digitWords = mapOf(
        "zero" to '0', "oh" to '0', "o" to '0', "kosong" to '0',
        "one" to '1', "satu" to '1', "two" to '2', "dua" to '2',
        "three" to '3', "tiga" to '3', "four" to '4', "empat" to '4',
        "five" to '5', "lima" to '5', "six" to '6', "enam" to '6',
        "seven" to '7', "tujuh" to '7', "eight" to '8', "lapan" to '8',
        "nine" to '9', "sembilan" to '9',
    )
    private val repeatWords = mapOf("double" to 2, "triple" to 3)

    /**
     * Turns runs of spoken digits into numbers: "one seven three double two" → "17322".
     * Runs shorter than 3 digits are left alone so "one day" stays as text.
     */
    fun spokenDigitsToNumbers(text: String): String {
        val tokens = Regex("""\S+""").findAll(text).toList()
        val out = StringBuilder()
        var last = 0
        var i = 0
        while (i < tokens.size) {
            val run = StringBuilder()
            var j = i
            var endOfRun = -1
            while (j < tokens.size) {
                val word = tokens[j].value.lowercase().trim(',', '.', ';', ':')
                val repeat = repeatWords[word]
                val digit = digitWords[word] ?: word.singleOrNull()?.takeIf(Char::isDigit)
                if (repeat != null && j + 1 < tokens.size) {
                    val next = tokens[j + 1].value.lowercase().trim(',', '.', ';', ':')
                    val d = digitWords[next] ?: next.singleOrNull()?.takeIf(Char::isDigit) ?: break
                    repeat(repeat) { run.append(d) }
                    endOfRun = j + 1
                    j += 2
                } else if (digit != null) {
                    run.append(digit)
                    endOfRun = j
                    j++
                } else break
            }
            if (run.length >= 3 && endOfRun >= i) {
                out.append(text, last, tokens[i].range.first).append(run)
                // Keep punctuation that followed the last spoken digit.
                val lastToken = tokens[endOfRun].value
                out.append(lastToken.takeLastWhile { it in ",.;:" })
                last = tokens[endOfRun].range.last + 1
                i = endOfRun + 1
            } else {
                i++
            }
        }
        out.append(text, last, text.length)
        return out.toString()
    }

    private val phoneLike = Regex("""(?<![\d.])\+?\d[\d \-]{7,15}\d(?![\d.])""")

    /** First phone-looking number in free text (spoken digits included), normalised if possible. */
    fun findPhone(text: String): String? = phoneLike.findAll(spokenDigitsToNumbers(text))
        .map { phoneDigits(it.value) }
        .filter { it.length in 9..13 }
        .sortedByDescending(::isValidMobile)
        .firstOrNull()

    // ---- Money ------------------------------------------------------------------------

    private val amount = Regex("""(\d+(?:\.\d+)?)\s*(k|thousand|ribu|mil|million|juta|m)?\b""")

    /** "120k" → 120000, "RM 120,000" → 120000, "1.2 mil" → 1200000. Bare numbers < 1000 mean thousands. */
    fun budget(raw: String?): Long? {
        val text = raw?.lowercase()?.replace("rm", "")?.replace(",", "")?.trim() ?: return null
        val m = amount.find(text) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        val value = when (m.groupValues[2]) {
            "k", "thousand", "ribu" -> n * 1_000
            "mil", "million", "juta", "m" -> n * 1_000_000
            else -> if (n < 1_000) n * 1_000 else n
        }
        return value.toLong().takeIf { it > 0 }
    }

    fun budget(value: Double?): Long? = value?.takeIf { it > 0 }?.let { if (it < 1_000) it * 1_000 else it }?.toLong()

    // ---- Dropdown values --------------------------------------------------------------

    private fun squash(s: String) = s.lowercase().filter(Char::isLetterOrDigit)

    /**
     * Maps an AI value onto exactly one allowed option, or null.
     * Exact (case-insensitive) wins; then a unique substring match ("X50" → "Proton X50");
     * then a unique best word overlap ("Aman Central outlet" → "Alor Setar Aman Central").
     */
    fun matchOption(value: String?, options: List<String>): String? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        options.firstOrNull { it.equals(v, ignoreCase = true) }?.let { return it }

        val sv = squash(v)
        if (sv.length < 2) return null
        options.firstOrNull { squash(it) == sv }?.let { return it }
        val contains = options.filter { squash(it).contains(sv) || sv.contains(squash(it)) }
        if (contains.size == 1) return contains.single()
        if (contains.size > 1) return null

        val words = v.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.length >= 2 }.toSet()
        val scored = options.map { opt ->
            opt to opt.lowercase().split(Regex("""[^a-z0-9]+""")).count { it in words }
        }.filter { it.second > 0 }
        val best = scored.maxOfOrNull { it.second } ?: return null
        return scored.filter { it.second == best }.singleOrNull()?.first
    }

    // ---- Dates ------------------------------------------------------------------------

    fun parseDate(raw: String?): LocalDate? =
        raw?.trim()?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Follow-up dates in the past are rejected (null) so the user has to pick one. */
    fun followUpDate(raw: String?, today: LocalDate): LocalDate? =
        parseDate(raw)?.takeIf { !it.isBefore(today) }

    private val weekdays = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY, "isnin" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "selasa" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY, "rabu" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "khamis" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY, "jumaat" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sabtu" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "ahad" to DayOfWeek.SUNDAY,
    )

    private val datePhrase = Regex(
        """\b(?:day after tomorrow|tomorrow|today|tonight|hari ini|esok|besok|lusa|""" +
            """(?:this )?weekend|hujung minggu|next week|minggu depan|""" +
            """in (\d+) days?|""" +
            """(?:(next|this|coming) )?(${weekdays.keys.sortedByDescending { it.length }.joinToString("|")}))\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Every relative date phrase in [text] with the date it means. "Next Tuesday" = the coming Tuesday. */
    fun findDates(text: String, today: LocalDate): List<Pair<IntRange, LocalDate>> =
        datePhrase.findAll(text).mapNotNull { m ->
            val phrase = m.value.lowercase()
            val date = when {
                phrase == "day after tomorrow" || phrase == "lusa" -> today.plusDays(2)
                phrase == "tomorrow" || phrase == "esok" || phrase == "besok" -> today.plusDays(1)
                phrase == "today" || phrase == "tonight" || phrase == "hari ini" -> today
                phrase.endsWith("weekend") || phrase == "hujung minggu" ->
                    today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                phrase == "next week" || phrase == "minggu depan" ->
                    today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                m.groupValues[1].isNotEmpty() -> today.plusDays(m.groupValues[1].toLong())
                else -> weekdays[m.groupValues[3].lowercase()]?.let { today.with(TemporalAdjusters.next(it)) }
            }
            date?.let { m.range to it }
        }.toList()

    fun resolveRelativeDate(text: String, today: LocalDate): LocalDate? = findDates(text, today).firstOrNull()?.second

    // ---- Names & misc -----------------------------------------------------------------

    private val titles = Regex(
        """^(?:(?:mr|mrs|ms|miss|mdm|madam|puan|pn|encik|en|cik|dato'?|datuk|datin|dr|tuan)\.?\s+)+""",
        RegexOption.IGNORE_CASE,
    )

    fun stripTitle(name: String?): String = name.orEmpty().trim().replace(titles, "").trim()

    private val emailRegex = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

    fun isValidEmail(email: String): Boolean = email.isBlank() || emailRegex.matches(email.trim())

    fun year(value: Int?, today: LocalDate): Int? = value?.takeIf { it in 1970..today.year + 1 }

    // ---- Whole lead -------------------------------------------------------------------

    fun normalizeLead(e: LeadExtraction, today: LocalDate): NormalizedLead {
        val low = e.lowConfidenceFields.mapNotNull(LeadField::fromKey).toMutableSet()
        val filled = mutableSetOf<LeadField>()
        fun <T> track(field: LeadField, value: T?): T? {
            if (value != null && value != "") filled += field
            return value
        }
        /** Matched to the list, but not verbatim: let the user double-check. */
        fun option(field: LeadField, raw: String?, options: List<String>): String? {
            val matched = matchOption(raw, options)
            if (matched != null && !matched.equals(raw?.trim(), ignoreCase = true)) low += field
            return track(field, matched)
        }

        val mobileDigits = phoneDigits(e.mobile)
        val trade = e.tradeIn
        return NormalizedLead(
            firstName = track(LeadField.FIRST_NAME, stripTitle(e.firstName)).orEmpty(),
            lastName = track(LeadField.LAST_NAME, e.lastName?.trim()).orEmpty(),
            mobile = track(LeadField.MOBILE, mobileDigits).orEmpty(),
            mobileValid = isValidMobile(mobileDigits),
            email = track(LeadField.EMAIL, e.email?.trim()).orEmpty(),
            product = option(LeadField.PRODUCT, e.product, ReferenceLists.products),
            outlet = option(LeadField.OUTLET, e.outlet, ReferenceLists.outlets),
            budgetRm = track(LeadField.BUDGET, budget(e.budgetRm)),
            expectedCloseDate = track(LeadField.CLOSE_DATE, followUpDate(e.expectedCloseDate, today)),
            tradeIn = trade.usedCar.also { if (it) filled += LeadField.TRADE_IN },
            tradeInBrand = trade.brand?.trim().orEmpty(),
            tradeInModel = trade.model?.trim().orEmpty(),
            tradeInYear = year(trade.year, today),
            nextAction = track(LeadField.NEXT_ACTION, e.followUp.nextAction.trim()).orEmpty(),
            nextDate = track(LeadField.NEXT_DATE, followUpDate(e.followUp.nextDate, today))
                .also { if (it == null && e.followUp.nextDate != null) low += LeadField.NEXT_DATE },
            salesStage = option(LeadField.STAGE, e.suggestedSalesStage, ReferenceLists.salesStages)
                ?: ReferenceLists.DEFAULT_STAGE,
            summary = track(LeadField.SUMMARY, e.followUp.summary.trim()).orEmpty(),
            aiFilled = filled,
            lowConfidence = low,
        )
    }
}

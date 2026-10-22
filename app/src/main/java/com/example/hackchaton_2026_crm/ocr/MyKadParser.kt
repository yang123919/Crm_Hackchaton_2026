package com.example.hackchaton_2026_crm.ocr

import com.example.hackchaton_2026_crm.ai.Normalizer
import java.time.LocalDate
import java.util.Locale

/** What could be read from a Malaysian MyKad photo. Every field can be missing. */
data class IcInfo(
    /** As printed, title-cased: "Nur Aisyah binti Abdul Rahman". */
    val fullName: String = "",
    /** Formatted YYMMDD-PB-####, or empty when no valid number was found. */
    val icNumber: String = "",
    val address: String = "",
    /** "Male" / "Female", or empty. */
    val gender: String = "",
    val birthDate: LocalDate? = null,
    /** Things the salesperson should double-check, in plain words. */
    val warnings: List<String> = emptyList(),
) {
    val isEmpty get() = fullName.isEmpty() && icNumber.isEmpty() && address.isEmpty()

    /** ******-**-1234: enough to recognise the card, not enough to misuse if the screen is seen. */
    val maskedIc get() = maskIc(icNumber)

    /** First/last split used by the CRM form. */
    val firstName get() = splitName(fullName).first
    val lastName get() = splitName(fullName).second

    companion object {
        fun maskIc(ic: String) = if (ic.length == 14) "******-**-" + ic.takeLast(4) else ic
    }
}

fun splitName(fullName: String): Pair<String, String> {
    val words = fullName.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "" to ""
    if (words.size == 1) return words[0] to ""
    // "Aisyah binti Rahman" -> first "Aisyah", last "binti Rahman" (the patronymic stays with the family part).
    val link = words.indexOfFirst { it.lowercase() in patronymics }
    if (link > 0) return words.take(link).joinToString(" ") to words.drop(link).joinToString(" ")
    return words.dropLast(1).joinToString(" ") to words.last()
}

private val patronymics = setOf("bin", "binti", "bt", "b.", "a/l", "a/p", "s/o", "d/o")

/**
 * Reads the text lines OCR returned (top to bottom) into name, IC number, address and gender.
 * Layout on a MyKad: IC number, then the name, then the address, with religion, "WARGANEGARA"
 * and the gender word around the edges. OCR order is not always reliable, so lines are
 * classified by what they look like instead of where they are.
 */
object MyKadParser {
    private const val MAX_ADDRESS = 200

    fun parse(lines: List<String>, today: LocalDate = LocalDate.now()): IcInfo {
        val cleaned = lines.map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
        val warnings = mutableListOf<String>()

        // ---- IC number (and the line it was on)
        val icHit = cleaned.firstNotNullOfOrNull { line -> findIc(line, today)?.let { line to it } }
        val ic = icHit?.second
        if (ic == null) warnings += "IC number not read"

        // ---- Gender: the printed word wins; the IC's last digit (odd = male) is the cross-check.
        val joined = cleaned.joinToString(" ").uppercase()
        val printed = when {
            Regex("""\bPEREMPUAN\b""").containsMatchIn(joined) -> "Female"
            Regex("""\bLELAKI\b""").containsMatchIn(joined) -> "Male"
            else -> ""
        }
        val fromDigit = ic?.let { if (it.number.last().digitToInt() % 2 == 1) "Male" else "Female" }.orEmpty()
        val gender = printed.ifEmpty { fromDigit }
        if (printed.isNotEmpty() && fromDigit.isNotEmpty() && printed != fromDigit) {
            warnings += "Gender printed on the card and the IC number disagree"
        }

        // ---- Name and address from what is left
        val icIdx = cleaned.indexOfFirst { it === icHit?.first }
        val restRows = cleaned.withIndex()
            .filter { it.index != icIdx }
            .map { it.index to stripNoiseWords(it.value) }
            .filter { (_, text) -> text.isNotEmpty() && !isMostlyNoise(text) }
        val rest = restRows.map { it.second }

        // On a MyKad the name sits right below the IC number, under the header text ("MyKad",
        // "IDENTITY CARD") that OCR often garbles. So prefer the first name-like row after the number,
        // and only fall back to one above it (a photo taken the other way up).
        val firstNameLine = restRows.indexOfFirst { (row, text) -> row > icIdx && looksLikeName(text) }
            .takeIf { it >= 0 } ?: rest.indexOfFirst(::looksLikeName)
        val nameLines = mutableListOf<String>()
        if (firstNameLine >= 0) {
            var i = firstNameLine
            while (i < rest.size && looksLikeName(rest[i]) && nameLines.size < 2) nameLines += rest[i++]
        }
        val nameIdx = if (nameLines.isEmpty()) -1 else firstNameLine
        val addressLines = rest.filterIndexed { i, _ -> nameIdx < 0 || i !in nameIdx until nameIdx + nameLines.size }

        val name = titleCaseName(nameLines.joinToString(" "))
        val address = addressLines.joinToString(", ") { titleCaseAddress(it) }.take(MAX_ADDRESS)
        if (name.isEmpty()) warnings += "Name not read"
        if (address.isEmpty()) warnings += "Address not read"

        return IcInfo(
            fullName = name,
            icNumber = ic?.formatted.orEmpty(),
            address = address,
            gender = gender,
            birthDate = ic?.birthDate,
            warnings = warnings,
        )
    }

    // ---- IC number -------------------------------------------------------------------------

    private data class Ic(val number: String, val birthDate: LocalDate) {
        val formatted get() = "${number.take(6)}-${number.substring(6, 8)}-${number.takeLast(4)}"
    }

    // OCR confuses these with digits inside a number.
    private val lookalikes = mapOf('O' to '0', 'o' to '0', 'Q' to '0', 'D' to '0', 'I' to '1', 'l' to '1', '|' to '1',
        'S' to '5', 'B' to '8', 'Z' to '2')

    private val icCandidate = Regex("""[0-9OoQDIl|SBZ]{6}\s*[-–—.:]?\s*[0-9OoQDIl|SBZ]{2}\s*[-–—.:]?\s*[0-9OoQDIl|SBZ]{4}""")

    /** The first valid IC number in [line], formatted YYMMDD-PB-####; also used for the number printed on a driving licence. */
    fun icIn(line: String, today: LocalDate): String? = findIc(line, today)?.formatted

    private fun findIc(line: String, today: LocalDate): Ic? {
        for (m in icCandidate.findAll(line)) {
            val digits = m.value.filter { it.isLetterOrDigit() || it == '|' }.map { lookalikes[it] ?: it }.joinToString("")
            if (digits.length != 12 || !digits.all(Char::isDigit)) continue
            val date = birthDate(digits.take(6), today) ?: continue
            if (digits.substring(6, 8) == "00") continue
            return Ic(digits, date)
        }
        return null
    }

    /** YYMMDD with the century guessed so nobody is born in the future. */
    fun birthDate(yymmdd: String, today: LocalDate): LocalDate? {
        val yy = yymmdd.take(2).toIntOrNull() ?: return null
        val mm = yymmdd.substring(2, 4).toIntOrNull() ?: return null
        val dd = yymmdd.substring(4, 6).toIntOrNull() ?: return null
        val year = if (2000 + yy <= today.year) 2000 + yy else 1900 + yy
        return runCatching { LocalDate.of(year, mm, dd) }.getOrNull()
    }

    // ---- Noise, names and addresses ---------------------------------------------------------

    private val noiseWords = setOf(
        "WARGANEGARA", "ISLAM", "LELAKI", "PEREMPUAN", "KRISTIAN", "BUDDHA", "HINDU", "BUKAN",
        "KAD", "PENGENALAN", "MALAYSIA", "MYKAD", "IDENTITY", "CARD", "WARGA", "NEGARA",
    )

    /** "ISLAM LELAKI" or "WARGANEGARA" on a line of its own are not part of the name or address. */
    private fun stripNoiseWords(line: String): String =
        line.split(' ').filter { !isNoiseToken(it) }.joinToString(" ").trim()

    /** Words printed on every card; OCR often garbles them ("MYKADG", "IDENTITYGARD"), so allow near misses. */
    private val headerWords = listOf("MYKAD", "IDENTITY", "PENGENALAN", "MALAYSIA", "WARGANEGARA", "PEREMPUAN", "LELAKI")

    private fun isNoiseToken(raw: String): Boolean {
        val t = raw.uppercase().filter(Char::isLetter)
        if (t.isEmpty()) return false
        if (t in noiseWords) return true
        if (t.length < 5) return false
        return headerWords.any { h ->
            t.contains(h) || (kotlin.math.abs(t.length - h.length) <= 1 &&
                Normalizer.editDistance(t, h) <= if (h.length >= 8) 2 else 1)
        }
    }

    private fun isMostlyNoise(line: String) = line.count(Char::isLetterOrDigit) < 3

    private val addressCues = Regex(
        """\b(JALAN|JLN|LORONG|LRG|TAMAN|TMN|KAMPUNG|KG|LOT|BLOK|BLK|PERSIARAN|LEBUH|BANDAR|PANGSAPURI|APARTMENT|""" +
            """KONDOMINIUM|FLAT|RUMAH|SEKSYEN|BATU|KM|MUKIM|DAERAH|PEKAN|FELDA|FELCRA|NO|PETI|TINGKAT|LEVEL|""" +
            """KUALA LUMPUR|PULAU PINANG|PENANG|KEDAH|PERAK|SELANGOR|JOHOR|KELANTAN|TERENGGANU|PAHANG|MELAKA|""" +
            """NEGERI SEMBILAN|PERLIS|SABAH|SARAWAK|LABUAN|PUTRAJAYA|WILAYAH PERSEKUTUAN|WP)\b""",
    )

    private fun looksLikeName(line: String): Boolean {
        if (line.any(Char::isDigit)) return false
        if (addressCues.containsMatchIn(line.uppercase())) return false
        val letters = line.count(Char::isLetter)
        // Names are letters, spaces and a few separators (. ' / @ -); anything else is OCR junk.
        return letters in 3..60 && line.all { it.isLetter() || it in " .'/@-" }
    }

    private val lowerCaseWords = setOf("bin", "binti", "bt", "b", "b.", "a/l", "a/p", "s/o", "d/o", "@", "dan")

    fun titleCaseName(raw: String): String = raw.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ") { w ->
        val lower = w.lowercase(Locale.ENGLISH)
        when {
            lower in lowerCaseWords -> lower
            // O'BRIEN, AH-KOW
            "'" in w || "-" in w -> lower.split("'", "-").joinToString(if ("'" in w) "'" else "-") { it.cap() }
            else -> lower.cap()
        }
    }

    /** Title case, but keep postcodes, lot numbers and short consonant-only tokens (KL, WP, JLN, SS2) upper. */
    fun titleCaseAddress(raw: String): String = raw.trim().trim(',').replace(glued, "$1 $2").split(' ').filter { it.isNotEmpty() }.joinToString(" ") { w ->
        val bare = w.trim(',', '.')
        when {
            bare.any(Char::isDigit) -> w.uppercase()
            bare.length <= 3 && bare.none { it.uppercaseChar() in "AEIOU" } -> w.uppercase()
            else -> w.lowercase(Locale.ENGLISH).cap()
        }
    }

    /** OCR often glues a house or lane number to the word before it: "CONTOH3" -> "CONTOH 3". Short codes (SS2, USJ2) stay. */
    private val glued = Regex("""\b([A-Za-z]{4,})(\d{1,3})\b""")

    private fun String.cap() = replaceFirstChar { it.titlecase(Locale.ENGLISH) }
}

object IcNumbers {
    /** "900101141234", "900101-14-1234" -> "900101-14-1234"; null when it can't be an IC number. */
    fun normalize(raw: String, today: LocalDate = LocalDate.now()): String? {
        val digits = raw.filter(Char::isDigit)
        if (digits.length != 12 || digits.substring(6, 8) == "00") return null
        MyKadParser.birthDate(digits.take(6), today) ?: return null
        return "${digits.take(6)}-${digits.substring(6, 8)}-${digits.takeLast(4)}"
    }
}

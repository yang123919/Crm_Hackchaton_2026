package com.example.hackchaton_2026_crm.ocr

import android.content.Context
import android.net.Uri
import com.example.hackchaton_2026_crm.data.LicenceType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** What could be read from a Malaysian driving licence photo. Every field can be missing. */
data class LicenceInfo(
    val fullName: String = "",
    /** Formatted YYMMDD-PB-####, or empty. */
    val icNumber: String = "",
    val address: String = "",
    /** Null when the photo can't tell (the salesperson then picks it). */
    val type: LicenceType? = null,
    /** True when [type] was read from the photo: it can then only change by rescanning. */
    val typeFromPhoto: Boolean = false,
    /** E.g. ["B2", "D"]. */
    val classes: List<String> = emptyList(),
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
    val warnings: List<String> = emptyList(),
) {
    val isEmpty get() = fullName.isEmpty() && icNumber.isEmpty() && classes.isEmpty() && validTo == null

    /**
     * The licence carries the same identity details as the IC (name, IC number, address), so it can
     * fill the customer form on its own. Gender is not printed; the IC number's last digit gives it.
     */
    fun toIcInfo(today: LocalDate = LocalDate.now()): IcInfo = IcInfo(
        fullName = fullName,
        icNumber = icNumber,
        address = address,
        gender = icNumber.lastOrNull()?.digitToIntOrNull()?.let { if (it % 2 == 1) "Male" else "Female" }.orEmpty(),
        birthDate = icNumber.takeIf { it.length >= 6 }?.let { MyKadParser.birthDate(it.take(6), today) },
        warnings = warnings.filter { it.startsWith("Name") || it.startsWith("IC number") || it.startsWith("Address") },
    )
}

/** Reads a driving licence photo on the phone; the photo is never uploaded or kept. */
object LicenceScanner {
    suspend fun scan(context: Context, uri: Uri): LicenceInfo = LicenceParser.parse(TextReader.rows(context, uri))
}

/**
 * Reads the OCR rows of a Malaysian driving licence ("LESEN MEMANDU / DRIVING LICENCE").
 * Layout: header with a red "P" badge on a probationary licence, the name, then labelled pairs:
 * "No. Pengenalan / Identity No." + "Tarikh Lahir / Date Of Birth", "Warganegara / Nationality" +
 * "Kelas / Class", "Tempoh / Validity" (from - to) and "Alamat / Address".
 */
object LicenceParser {
    private const val MAX_ADDRESS = 200
    private val date = Regex("""\b(\d{1,2})\s*[/.\-]\s*(\d{1,2})\s*[/.\-]\s*(\d{4})\b""")
    private val range = Regex("""(\d{1,2}\s*[/.\-]\s*\d{1,2}\s*[/.\-]\s*\d{4})\s*[-–—~to]+\s*(\d{1,2}\s*[/.\-]\s*\d{1,2}\s*[/.\-]\s*\d{4})""")
    private val classCode = Regex("""^(A1|A|B1|B2|B|C|DA|D|E1|E2|E|F|G|H|I|M)$""")
    private val labelWords = Regex(
        """(?i)\b(no\.?\s*pengenalan|identity|tarikh|date\s*of\s*birth|warganegara|nationality|kelas|class|tempoh|validity|alamat|address)\b""",
    )
    /** Printed on every card, including the "Jabatan Pengangkutan Jalan" watermark, so never part of a name. */
    private val headerWords = setOf(
        "LESEN", "MEMANDU", "DRIVING", "LICENCE", "LICENSE", "MALAYSIA", "BELAJAR", "PERCUBAAN",
        "JABATAN", "PENGANGKUTAN", "JALAN", "JPJ",
    )

    fun looksLikeLicence(rows: List<String>): Boolean {
        val text = rows.joinToString(" ").uppercase()
        return "LESEN MEMANDU" in text || Regex("""DRIVING\s+LICEN[CS]E""").containsMatchIn(text) ||
            (labelWords.findAll(text).count() >= 3 && "KELAS" in text)
    }

    fun parse(lines: List<String>, today: LocalDate = LocalDate.now()): LicenceInfo {
        val rows = lines.map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
        val warnings = mutableListOf<String>()
        if (!looksLikeLicence(rows)) warnings += "This doesn't look like a driving licence"

        // ---- IC number
        val icRow = rows.indexOfFirst { MyKadParser.icIn(it, today) != null }
        val ic = if (icRow >= 0) MyKadParser.icIn(rows[icRow], today).orEmpty() else ""
        if (ic.isEmpty()) warnings += "IC number not read"
        val birth = ic.takeIf { it.isNotEmpty() }?.let { MyKadParser.birthDate(it.take(6), today) }

        // ---- Validity: "05/05/2025 - 04/05/2027", else the dates that aren't the birth date
        val joined = rows.joinToString("\n")
        val (validFrom, validTo) = range.find(joined)
            ?.let { parseDate(it.groupValues[1]) to parseDate(it.groupValues[2]) }
            ?: run {
                val dates = date.findAll(joined).mapNotNull { parseDate(it.value) }.filter { it != birth }.sorted().toList()
                (if (dates.size >= 2) dates.first() else null) to dates.lastOrNull()
            }
        if (validTo == null) warnings += "Validity not read"
        else if (validTo.isBefore(today)) warnings += "Licence expired"

        // ---- Class: on the row under "Kelas / Class" (beside the nationality), or on the label row itself
        val classes = classesNear(rows)
        if (classes.isEmpty()) warnings += "Class not read"

        // ---- Type
        val type = detectType(rows, validFrom, validTo)
        if (type == null) warnings += "Licence type not clear: confirm CDL or PDL"

        // ---- Name: the name-like row above the IC labels; address: the rows after "Alamat / Address"
        val idLabel = rows.indexOfFirst { Regex("""(?i)pengenalan|identity""").containsMatchIn(it) }
        val nameSearchEnd = listOf(idLabel, icRow).filter { it >= 0 }.minOrNull() ?: rows.size
        val name = rows.take(nameSearchEnd).lastOrNull(::looksLikeName)?.let(MyKadParser::titleCaseName).orEmpty()
        if (name.isEmpty()) warnings += "Name not read"

        val addressLabel = rows.indexOfFirst { Regex("""(?i)\b(alamat|address)\b""").containsMatchIn(it) }
        val address = if (addressLabel < 0) "" else {
            val sameRow = rows[addressLabel].replace(Regex("""(?i).*\b(alamat\s*/?\s*address|address|alamat)\b\s*:?"""), "").trim()
            (listOf(sameRow) + rows.drop(addressLabel + 1))
                .filter { it.isNotEmpty() && !labelWords.containsMatchIn(it) && it.count(Char::isLetterOrDigit) >= 3 }
                .joinToString(", ") { MyKadParser.titleCaseAddress(it) }
                .take(MAX_ADDRESS)
        }
        if (address.isEmpty()) warnings += "Address not read"

        return LicenceInfo(
            fullName = name,
            icNumber = ic,
            address = address,
            type = type,
            typeFromPhoto = type != null,
            classes = classes,
            validFrom = validFrom,
            validTo = validTo,
            warnings = warnings,
        )
    }

    private fun classesNear(rows: List<String>): List<String> {
        val labelRow = rows.indexOfFirst { Regex("""(?i)\b(kelas|class)\b""").containsMatchIn(it) }
        if (labelRow < 0) return emptyList()
        val afterLabel = rows[labelRow].replace(Regex("""(?i).*\b(kelas\s*/\s*class|class)\b"""), "")
        val candidates = listOf(afterLabel) + rows.drop(labelRow + 1).take(1)
        return candidates.flatMap { row ->
            row.split(Regex("""[\s,;/]+""")).map { it.trim().uppercase() }.filter { classCode.matches(it) }
        }.distinct()
    }

    /**
     * A learner's or probationary licence says so (or shows the red "P" in the header); a full licence
     * says nothing. A probationary licence always runs exactly 2 years, so a different validity length
     * with no "P" means a full licence; exactly 2 years with no "P" read is left for the salesperson.
     */
    private fun detectType(rows: List<String>, from: LocalDate?, to: LocalDate?): LicenceType? {
        val text = rows.joinToString(" ").uppercase()
        if (Regex("""\bLDL\b|BELAJAR|LEARNER""").containsMatchIn(text)) return LicenceType.LDL
        if (Regex("""\bPDL\b|PERCUBAAN|PROBATION""").containsMatchIn(text)) return LicenceType.PDL
        val header = rows.take(4).filter { r -> headerWords.any { it in r.uppercase() } || r.trim().length <= 2 }
        if (header.any { r -> r.uppercase().split(Regex("""[^A-Z]+""")).any { it == "P" } }) return LicenceType.PDL
        if (from == null || to == null) return null
        val days = ChronoUnit.DAYS.between(from, to)
        return if (days in 725..735) null else LicenceType.CDL
    }

    private fun looksLikeName(line: String): Boolean {
        if (line.any(Char::isDigit) || labelWords.containsMatchIn(line)) return false
        val words = line.uppercase().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || words.any { it in headerWords }) return false
        val letters = line.count(Char::isLetter)
        return letters in 3..60 && words.size in 2..8 && line.all { it.isLetter() || it in " .'/@-" }
    }

    private val formats = listOf("d/M/yyyy", "d.M.yyyy", "d-M-yyyy").map { DateTimeFormatter.ofPattern(it) }

    private fun parseDate(raw: String): LocalDate? {
        val clean = raw.replace(Regex("""\s+"""), "")
        return formats.firstNotNullOfOrNull { f -> runCatching { LocalDate.parse(clean, f) }.getOrNull() }
    }
}

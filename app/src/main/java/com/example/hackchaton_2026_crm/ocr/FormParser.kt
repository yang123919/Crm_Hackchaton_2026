package com.example.hackchaton_2026_crm.ocr

import android.content.Context
import android.net.Uri

enum class FormField(val label: String) {
    NAME("Name"),
    PHONE("Phone"),
    EMAIL("Email"),
    IC("IC"),
    ADDRESS("Address"),
    CAR("Car"),
    BUDGET("Budget"),
    OUTLET("Outlet"),
    TRADE_IN("Trade-in"),
    REMARKS("Remarks"),
}

/** Reads a photographed customer form (English or Bahasa Malaysia labels) into fields. */
object FormScanner {
    suspend fun scan(context: Context, uri: Uri): FormParser.Result = FormParser.parse(TextReader.rows(context, uri))
}

/**
 * Turns the rows OCR read from a paper enquiry form into fields. It looks for known labels
 * ("Name", "Nama", "No. Tel", "H/P", "Alamat"...) and takes what follows, on the same row or
 * the next one. Printed forms work well; handwriting depends on how clear it is.
 */
object FormParser {

    data class Result(
        val fields: Map<FormField, String>,
        /** The OCR text itself, for the case where no labels were recognised. */
        val rawText: String,
    ) {
        /** One "Label: value" per line. The AI reads this exactly like typed notes. */
        fun toNotes(): String =
            if (fields.isEmpty()) rawText
            else FormField.entries.mapNotNull { f -> fields[f]?.let { "${f.label}: $it" } }.joinToString("\n")
    }

    // Longest alternatives first inside each group so "no tel" wins over "no".
    private val labels: List<Pair<FormField, Regex>> = listOf(
        FormField.NAME to """customer'?s?\s+name|full\s+name|nama\s+penuh|nama\s+pelanggan|nama|name""",
        FormField.PHONE to """no\.?\s*telefon|no\.?\s*tel|no\.?\s*h/?p|telephone|telefon|tel|phone\s*no\.?|phone|mobile\s*no\.?|mobile|handphone|h/?p|contact\s*no\.?|contact""",
        FormField.EMAIL to """e-?mail""",
        FormField.IC to """ic\s*no\.?|ic\s*number|no\.?\s*k/?p|nric|mykad|ic""",
        FormField.ADDRESS to """alamat|address""",
        FormField.CAR to """interested\s+in|model|car|kereta|vehicle|variant|product""",
        FormField.BUDGET to """budget|bajet""",
        FormField.OUTLET to """outlet|branch|cawangan|showroom""",
        FormField.TRADE_IN to """trade[- ]?in|tukar\s*ganti""",
        FormField.REMARKS to """remarks?|notes?|catatan|comments?|follow[- ]?up""",
    ).map { (field, alternatives) ->
        // label at the start of the row, then optional punctuation, then the value
        // (?![A-Za-z]) rather than \b so labels ending in "." ("Phone No.:") still match as a whole.
        field to Regex("""^\s*(?:$alternatives)(?![A-Za-z])\s*[:：=\-–.]*\s*(.*)$""", RegexOption.IGNORE_CASE)
    }

    private val multiLine = setOf(FormField.ADDRESS, FormField.REMARKS)

    /** Fields that are mostly digits, where OCR letter/digit mix-ups can be corrected safely. */
    private val numeric = listOf(FormField.IC, FormField.PHONE, FormField.BUDGET)

    private val lookalikes = mapOf('O' to '0', 'o' to '0', 'Q' to '0', 'D' to '0', 'I' to '1', 'l' to '1', '|' to '1',
        'S' to '5', 'B' to '8', 'Z' to '2', 'g' to '9', 'q' to '9')

    /** "12D.000" -> "120.000", "O12-345 678g" -> "012-345 6789". Values with few digits (words) are left alone. */
    internal fun fixLookalikes(value: String): String {
        val digits = value.count(Char::isDigit)
        val fixable = value.count { it in lookalikes }
        if (digits < 4 || fixable == 0 || digits < fixable * 2) return value
        return value.map { lookalikes[it] ?: it }.joinToString("")
    }

    /** "Date: 22/10/2026" or "Salesperson : Kelvin": a label we don't use, which ends a multi-line value. */
    private val anyLabel = Regex("""^\s*[A-Za-z][A-Za-z /.']{1,24}\s*[:：]""")

    private fun labelOf(row: String): Pair<FormField, String>? =
        labels.firstNotNullOfOrNull { (field, regex) -> regex.find(row)?.let { field to it.groupValues[1].trim() } }

    fun parse(rows: List<String>): Result {
        val lines = rows.map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
        val fields = LinkedHashMap<FormField, String>()
        var open: FormField? = null // the multi-line field still collecting rows

        var i = 0
        while (i < lines.size) {
            val row = lines[i]
            val hit = labelOf(row)
            when {
                hit != null -> {
                    val (field, inline) = hit
                    open = null
                    var value = inline
                    // "Name" on one row and the answer on the next, as in a table.
                    if (value.isEmpty() && i + 1 < lines.size && labelOf(lines[i + 1]) == null && !anyLabel.containsMatchIn(lines[i + 1])) {
                        value = lines[++i]
                    }
                    if (value.isNotEmpty() && field !in fields) fields[field] = value
                    if (field in multiLine && field in fields) open = field
                }
                open != null && !anyLabel.containsMatchIn(row) -> fields[open] = fields.getValue(open) + ", " + row
                else -> open = null
            }
            i++
        }
        for (f in numeric) fields[f]?.let { fields[f] = fixLookalikes(it) }
        return Result(fields, lines.joinToString("\n"))
    }

    /** Same parsing for notes that already look like "Label: value" lines (typed or from a scan). */
    fun parseText(text: String): Map<FormField, String> = parse(text.lines()).fields
}

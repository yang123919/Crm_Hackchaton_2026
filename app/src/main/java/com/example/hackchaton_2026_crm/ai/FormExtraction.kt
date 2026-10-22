package com.example.hackchaton_2026_crm.ai

import com.example.hackchaton_2026_crm.data.ReferenceLists
import com.example.hackchaton_2026_crm.ocr.FormField
import com.example.hackchaton_2026_crm.ocr.FormParser
import com.example.hackchaton_2026_crm.ocr.IcNumbers
import com.example.hackchaton_2026_crm.ocr.MyKadParser
import com.example.hackchaton_2026_crm.ocr.splitName
import java.time.LocalDate

/**
 * Offline extraction for notes that came from a scanned form ("Name: ...", "No. Tel: ...") or
 * mention an IC number. Runs on top of the offline free-text extractor in demo mode.
 */
object FormExtraction {
    private val icInText = Regex("""\d{6}\s*-?\s*\d{2}\s*-?\s*\d{4}""")
    private val emailInText = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")
    private val yearInText = Regex("""\b(19|20)\d{2}\b""")
    private val negative = setOf("no", "none", "nil", "n/a", "na", "tiada", "-", "x")

    fun enrich(base: LeadExtraction, text: String, today: LocalDate): LeadExtraction {
        val form = FormParser.parseText(text)
        var e = base

        // An IC number anywhere in the notes.
        val ic = form[FormField.IC]?.let { IcNumbers.normalize(it, today) }
            ?: icInText.find(text)?.value?.let { IcNumbers.normalize(it, today) }
        if (ic != null) e = e.copy(icNumber = ic)

        if (form.size < 2) return e // free text: the normal extractor already handled it

        form[FormField.NAME]?.let { raw ->
            val name = Normalizer.stripTitle(raw).let { if (it == it.uppercase()) MyKadParser.titleCaseName(it) else it }
            val (first, last) = splitName(name)
            e = e.copy(firstName = first.ifEmpty { null }, lastName = last.ifEmpty { null })
        }
        form[FormField.PHONE]?.let { raw ->
            e = e.copy(mobile = Normalizer.findPhone(raw) ?: raw.filter(Char::isDigit).ifEmpty { null })
        }
        form[FormField.EMAIL]?.let { raw -> emailInText.find(raw)?.let { e = e.copy(email = it.value) } }
        form[FormField.ADDRESS]?.let { e = e.copy(address = it) }
        form[FormField.CAR]?.let { raw ->
            Normalizer.matchOption(raw, ReferenceLists.products)?.let { e = e.copy(product = it) }
        }
        form[FormField.OUTLET]?.let { raw ->
            Normalizer.matchOption(raw, ReferenceLists.outlets)?.let { e = e.copy(outlet = it) }
        }
        form[FormField.BUDGET]?.let { raw -> Normalizer.budget(raw)?.let { e = e.copy(budgetRm = it.toDouble()) } }
        form[FormField.TRADE_IN]?.let { raw ->
            if (raw.trim().lowercase() !in negative) {
                val year = yearInText.find(raw)?.value?.toIntOrNull()
                val model = raw.replace(yearInText, "").trim(' ', ',', '.').ifEmpty { null }
                e = e.copy(tradeIn = TradeIn(usedCar = true, brand = null, model = model, year = year))
            } else e = e.copy(tradeIn = TradeIn(usedCar = false))
        }

        val summary = listOfNotNull(
            e.product?.let { "Interested in $it." },
            e.budgetRm?.let { "Budget ~RM${(it / 1_000).toLong()}k." },
            form[FormField.REMARKS],
        ).joinToString(" ")
        if (summary.isNotBlank()) e = e.copy(followUp = e.followUp.copy(summary = summary))
        return e
    }
}

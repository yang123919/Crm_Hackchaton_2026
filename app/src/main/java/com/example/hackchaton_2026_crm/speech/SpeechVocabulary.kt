package com.example.hackchaton_2026_crm.speech

import com.example.hackchaton_2026_crm.data.ReferenceLists

/** Words this app expects to hear, used to bias the recogniser and to rank its alternatives. */
object SpeechVocabulary {

    private val malayAndSales = listOf(
        "esok", "lusa", "minggu depan", "hujung minggu", "nak", "kereta", "ribu", "juta", "tempah",
        "pandu uji", "sebut harga", "trade in", "test drive", "quotation", "booking", "WhatsApp",
        "budget", "follow up", "Encik", "Puan", "Cik", "Datuk",
    )

    private val shortModelNames = listOf(
        "X50", "Saga", "Iriz", "Exora", "Tiggo 8", "Omoda 5", "Cannon", "Ioniq", "Elantra", "Santa Fe",
        "Starex", "Aircross", "3008", "5008", "308", "2008",
    )

    /** Passed to the recogniser (Android 13+) as a hint to favour these words. */
    val biasingStrings: List<String> =
        (ReferenceLists.products + ReferenceLists.outlets + shortModelNames + malayAndSales).distinct()

    private val terms: List<String> = biasingStrings.map { it.lowercase() }

    /** How many distinct expected terms appear in [text]. */
    fun hits(text: String): Int {
        val lower = " ${text.lowercase()} "
        return terms.count { " $it " in lower || lower.contains(" $it,") || lower.contains(" $it.") }
    }
}

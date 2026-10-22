package com.example.hackchaton_2026_crm.speech

import com.example.hackchaton_2026_crm.ai.Normalizer

/**
 * The recogniser returns several alternatives for what it heard. Its first guess is usually best,
 * but not when a later one contains our car names or a valid phone number: those are strong
 * signs of the right reading. Each alternative is corrected, then scored.
 */
object SpeechRanker {
    private const val TERM_BONUS = 0.15
    private const val PHONE_BONUS = 0.25

    fun pickBest(candidates: List<String>, confidences: FloatArray? = null): String {
        val corrected = candidates.mapIndexed { i, c -> i to SpeechCorrector.fix(c) }.filter { it.second.isNotBlank() }
        if (corrected.isEmpty()) return ""
        if (corrected.size == 1) return corrected.single().second

        fun score(i: Int, text: String): Double {
            val confidence = confidences?.getOrNull(i)?.takeIf { it >= 0f }?.toDouble() ?: (1.0 - 0.1 * i)
            val phone = Normalizer.findPhone(text)?.let(Normalizer::isValidMobile) == true
            return confidence + TERM_BONUS * SpeechVocabulary.hits(text) + if (phone) PHONE_BONUS else 0.0
        }
        // maxBy keeps the earliest on ties, so the recogniser's own first choice wins a dead heat.
        return corrected.maxBy { (i, text) -> score(i, text) }.second
    }
}

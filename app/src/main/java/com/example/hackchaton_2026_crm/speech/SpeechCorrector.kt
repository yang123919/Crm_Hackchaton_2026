package com.example.hackchaton_2026_crm.speech

import com.example.hackchaton_2026_crm.ai.Normalizer

/**
 * Fixes what speech recognisers typically get wrong in a car dealership: model names heard as
 * ordinary words ("ex fifty", "tiger 8", "ionic"), spoken digits, and "120 k". Safe to run
 * repeatedly (running it on its own output changes nothing), so it can be applied to live
 * phrases and again right before the AI call.
 */
object SpeechCorrector {

    private class Rule(pattern: String, val replacement: String) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private val rules = listOf(
        // Proton X50 / X70 / X90: "ex fifty", "x 50", "eggs 50"
        Rule("""\b(?:x|ex|eggs)\s*(?:50|fifty)\b""", "X50"),
        Rule("""\b(?:x|ex|eggs)\s*(?:70|seventy)\b""", "X70"),
        Rule("""\b(?:x|ex|eggs)\s*(?:90|ninety)\b""", "X90"),
        // Chery Tiggo 8 Pro: "tiger 8", "tigo eight"
        Rule("""\b(?:tiggo|tigo|tiger|tigger|tego)\s*(?:8|eight)\b""", "Tiggo 8"),
        Rule("""\bomoda\s*(?:5|five)\b""", "Omoda 5"),
        Rule("""\b(?:c|sea|see)\s*(?:3|three)\s*air\s*cross\b""", "C3 Aircross"),
        Rule("""\b(?:ionic|ionique|iron ik|i on ic)\b""", "Ioniq"),
        Rule("""\bproton\s+(?:iris|eerie's|eeriz|iriz)\b""", "Proton Iriz"),
        Rule("""\b(?:gwm|g w m|great wall)\s+can+on\b""", "GWM Cannon"),
        Rule("""\b(?:ex\s*ora|exhora)\b""", "Exora"),
        Rule("""\bsanta\s*fe[ye]?\b""", "Santa Fe"),
        Rule("""\bstar\s*(?:ex|x)\b""", "Starex"),
        // "120 k" / "120 K" -> "120k"
        Rule("""\b(\d+(?:\.\d+)?)\s+k\b""", "$1k"),
    )

    fun fix(text: String): String {
        if (text.isBlank()) return text
        // Digits first so "one two zero k" becomes "120 k" and then "120k".
        var out = Normalizer.spokenDigitsToNumbers(text)
        for (rule in rules) out = rule.regex.replace(out, rule.replacement)
        return out
    }
}

package com.example.hackchaton_2026_crm.speech

import com.example.hackchaton_2026_crm.ai.DemoAiService
import com.example.hackchaton_2026_crm.ai.Normalizer
import com.example.hackchaton_2026_crm.data.ReferenceLists
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SpeechCorrectorTest {
    @Test fun modelNames() {
        assertEquals("interested in the X50", SpeechCorrector.fix("interested in the ex fifty"))
        assertEquals("wants X50 and X70", SpeechCorrector.fix("wants x 50 and eggs seventy"))
        assertEquals("the Tiggo 8 Pro", SpeechCorrector.fix("the tiger eight Pro"))
        assertEquals("Hyundai Ioniq", SpeechCorrector.fix("Hyundai ionic"))
        assertEquals("a C3 Aircross", SpeechCorrector.fix("a sea three air cross"))
        assertEquals("GWM Cannon", SpeechCorrector.fix("g w m canon"))
        assertEquals("Proton Iriz", SpeechCorrector.fix("proton iris"))
    }

    @Test fun doesNotTouchOrdinaryWords() {
        val text = "He saw a box 50 metres away and the iris of his eye"
        assertEquals(text, SpeechCorrector.fix(text))
    }

    @Test fun digitsAndBudget() {
        assertEquals("call 0123456789 budget 120k", SpeechCorrector.fix("call zero one two three four five six seven eight nine budget one two zero k"))
        assertEquals("budget 85k", SpeechCorrector.fix("budget 85 k"))
    }

    @Test fun isIdempotent() {
        listOf(
            "interested in the ex fifty, budget 120 k, call zero one two 345 6789",
            "tiger eight pro and santa fey", "plain sentence with nothing to fix", "",
        ).forEach { assertEquals(SpeechCorrector.fix(it), SpeechCorrector.fix(SpeechCorrector.fix(it))) }
    }
}

class SpeechRankerTest {
    @Test fun prefersTheAlternativeWithOurCarAndAPhoneNumber() {
        val best = SpeechRanker.pickBest(
            listOf(
                "Mr tan call zero one two three four five six seven eight nine wants the egg fifty",
                "Mr tan 012 345 6789 interested in the Proton X50",
                "Mister Dan",
            ),
        )
        assertTrue(best, best.contains("X50"))
    }

    @Test fun keepsTheFirstChoiceWhenNothingDistinguishesThem() {
        assertEquals("hello there", SpeechRanker.pickBest(listOf("hello there", "hello their", "hollow there")))
    }

    @Test fun usesConfidenceScores_andSurvivesBlanks() {
        assertEquals("good one", SpeechRanker.pickBest(listOf("", "bad one", "good one"), floatArrayOf(0.9f, 0.2f, 0.8f)))
        assertEquals("", SpeechRanker.pickBest(emptyList()))
        assertEquals("", SpeechRanker.pickBest(listOf("", " ")))
    }
}

class NearMissMatchingTest {
    @Test fun oneEditAwayFromExactlyOneOption() {
        assertEquals("Hyundai Ioniq Hev Plus", Normalizer.matchOption("ionic", ReferenceLists.products))
        assertEquals("Tiggo 8 Pro", Normalizer.matchOption("tigo", ReferenceLists.products))
        assertEquals("Proton Saga", Normalizer.matchOption("sagaa", ReferenceLists.products))
    }

    @Test fun neverGuessesWhenUnsureOrShort() {
        assertEquals(null, Normalizer.matchOption("xyz", ReferenceLists.products)) // too short to be a near miss
        assertEquals(null, Normalizer.matchOption("hyundai", ReferenceLists.products)) // several options
        assertEquals(null, Normalizer.matchOption("zebra", ReferenceLists.products))
    }

    @Test fun editDistance() {
        assertEquals(0, Normalizer.editDistance("saga", "saga"))
        assertEquals(1, Normalizer.editDistance("ionic", "ioniq"))
        assertEquals(1, Normalizer.editDistance("tigo", "tiggo"))
        assertTrue(Normalizer.editDistance("abc", "xyz") > 1)
    }
}

/** The same mis-heard sentences, end to end through correction and extraction. */
class MisheardEndToEndTest {
    private val ai = DemoAiService(delayMs = 0)
    private val today = LocalDate.of(2026, 10, 22)

    private suspend fun product(heard: String) = ai.extractLead(SpeechCorrector.fix(heard), today).product

    @Test fun carNamesSurviveMishearing() = runTest {
        assertEquals("Proton X50", product("mister tan interested in the ex fifty"))
        assertEquals("Proton X50", product("puan lim wants eggs 50 budget 120 k"))
        assertEquals("Tiggo 8 Pro", product("encik ali tiger eight pro quotation"))
        assertEquals("Hyundai Ioniq Hev Plus", product("mr wong the hyundai ionic hybrid"))
        assertEquals("C3 Aircross SUV", product("cik siti sea three air cross"))
    }

    @Test fun phoneAndBudgetFromSpokenDigits() = runTest {
        val e = ai.extractLead(
            SpeechCorrector.fix("Mr Lee zero one two three four five six seven eight nine budget one two zero k wants saga"), today,
        )
        assertEquals("60123456789", Normalizer.phone(e.mobile))
        assertEquals(120_000.0, e.budgetRm!!, 0.0)
        assertEquals("Proton Saga", e.product)
    }
}

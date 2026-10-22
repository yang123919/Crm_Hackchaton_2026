package com.example.hackchaton_2026_crm.ai

import com.example.hackchaton_2026_crm.data.ReferenceLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NormalizerTest {
    /** A Thursday, same as the wireframes. */
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun phone_acceptsCommonFormats() {
        assertEquals("60123456789", Normalizer.phone("012-345 6789"))
        assertEquals("60123456789", Normalizer.phone("+60 12-345 6789"))
        assertEquals("60123456789", Normalizer.phone("60123456789"))
        assertEquals("60123456789", Normalizer.phone("123456789"))
        assertEquals("601123456789", Normalizer.phone("011-2345 6789"))
    }

    @Test fun phone_rejectsWrongLength() {
        assertNull(Normalizer.phone("012-345 678"))
        assertNull(Normalizer.phone("0123456789012"))
        assertNull(Normalizer.phone("03-1234 5678")) // landline
        assertNull(Normalizer.phone(""))
        assertNull(Normalizer.phone(null))
    }

    @Test fun spokenDigits_becomeNumbers() {
        assertEquals("Puan Aisyah, 0173228991, nak Saga",
            Normalizer.spokenDigitsToNumbers("Puan Aisyah, zero one seven three two two eight nine nine one, nak Saga"))
        assertEquals("call 0112", Normalizer.spokenDigitsToNumbers("call zero double one two"))
        assertEquals("one day later", Normalizer.spokenDigitsToNumbers("one day later"))
    }

    @Test fun findPhone_inFreeText() {
        assertEquals("60123456789", Normalizer.findPhone("Mr Tan, 012-345 6789, interested in X50"))
        assertEquals("60173228991", Normalizer.findPhone("kosong satu tujuh tiga dua dua lapan sembilan sembilan satu"))
        assertNull(Normalizer.findPhone("budget 120k, Myvi 2018"))
    }

    @Test fun budget_parsesShorthand() {
        assertEquals(120_000L, Normalizer.budget("120k"))
        assertEquals(120_000L, Normalizer.budget("RM 120,000"))
        assertEquals(1_200_000L, Normalizer.budget("1.2 mil"))
        assertEquals(85_000L, Normalizer.budget("85 ribu"))
        assertEquals(120_000L, Normalizer.budget("120"))
        assertEquals(120_000L, Normalizer.budget(120_000.0))
        assertNull(Normalizer.budget("no budget"))
    }

    @Test fun matchOption_fuzzyButStrict() {
        assertEquals("Proton X50", Normalizer.matchOption("X50", ReferenceLists.products))
        assertEquals("Proton X50", Normalizer.matchOption("proton x50", ReferenceLists.products))
        assertEquals("Tiggo 8 Pro", Normalizer.matchOption("Tiggo 8", ReferenceLists.products))
        assertEquals("Alor Setar Aman Central", Normalizer.matchOption("Aman Central", ReferenceLists.outlets))
        assertEquals("Follow Up", Normalizer.matchOption("follow up", ReferenceLists.salesStages))
        assertEquals("Test Drive", Normalizer.matchOption("test-drive", ReferenceLists.salesStages))
        // Ambiguous or unknown → null, never a guess.
        assertNull(Normalizer.matchOption("Proton", ReferenceLists.products))
        assertNull(Normalizer.matchOption("Honda City", ReferenceLists.products))
        assertNull(Normalizer.matchOption("", ReferenceLists.products))
    }

    @Test fun relativeDates_resolveFromToday() {
        assertEquals(LocalDate.of(2026, 10, 23), Normalizer.resolveRelativeDate("call him tomorrow", today))
        assertEquals(LocalDate.of(2026, 10, 23), Normalizer.resolveRelativeDate("follow up esok", today))
        assertEquals(LocalDate.of(2026, 10, 24), Normalizer.resolveRelativeDate("datang lusa", today))
        assertEquals(LocalDate.of(2026, 10, 27), Normalizer.resolveRelativeDate("test drive next Tuesday", today))
        assertEquals(LocalDate.of(2026, 10, 23), Normalizer.resolveRelativeDate("call back Friday", today))
        assertEquals(LocalDate.of(2026, 10, 29), Normalizer.resolveRelativeDate("call back Thursday", today))
        assertEquals(LocalDate.of(2026, 10, 24), Normalizer.resolveRelativeDate("booking this weekend", today))
        assertEquals(LocalDate.of(2026, 10, 26), Normalizer.resolveRelativeDate("minggu depan", today))
        assertEquals(LocalDate.of(2026, 10, 25), Normalizer.resolveRelativeDate("in 3 days", today))
        assertNull(Normalizer.resolveRelativeDate("he sat down", today))
    }

    @Test fun followUpDate_rejectsPast() {
        assertEquals(today, Normalizer.followUpDate("2026-10-22", today))
        assertNull(Normalizer.followUpDate("2026-10-21", today))
        assertNull(Normalizer.followUpDate("next week", today))
    }

    @Test fun normalizeLead_validatesEverything() {
        val n = Normalizer.normalizeLead(
            LeadExtraction(
                firstName = "Mr Tan Ah", lastName = "Kow", mobile = "012-345 6789",
                product = "X50", outlet = "Somewhere", budgetRm = 120.0,
                tradeIn = TradeIn(usedCar = true, brand = "Perodua", model = "Myvi", year = 1890),
                followUp = FollowUpPart("Wants X50.", "Test drive", "2020-01-01"),
                suggestedSalesStage = "test drive",
                lowConfidenceFields = listOf("follow_up.next_action", "trade_in.year"),
            ),
            today,
        )
        assertEquals("Tan Ah", n.firstName)
        assertEquals("60123456789", n.mobile)
        assertTrue(n.mobileValid)
        assertEquals("Proton X50", n.product)
        assertNull(n.outlet)
        assertEquals(120_000L, n.budgetRm)
        assertNull(n.tradeInYear)
        assertNull(n.nextDate)
        assertEquals("Test Drive", n.salesStage)
        assertTrue(LeadField.PRODUCT in n.lowConfidence) // fuzzy-matched
        assertTrue(LeadField.NEXT_DATE in n.lowConfidence) // past date dropped
        assertTrue(LeadField.NEXT_ACTION in n.lowConfidence)
        assertTrue(LeadField.TRADE_IN in n.lowConfidence)
        assertFalse(LeadField.OUTLET in n.aiFilled)
    }

    @Test fun normalizeFollowUp_keepsStageAndDropsPastDate() {
        val n = Normalizer.normalizeFollowUp(
            FollowUpExtraction(FollowUpPart(" OK with price. ", "Collect booking", "2026-10-01"), "Made up stage"),
            currentStage = "Test Drive", today = today,
        )
        assertEquals("OK with price.", n.summary)
        assertEquals("Test Drive", n.salesStage)
        assertNull(n.nextDate)
        assertTrue(n.dateDropped)

        val ok = Normalizer.normalizeFollowUp(
            FollowUpExtraction(FollowUpPart("x", "y", "2026-10-24"), "booking"), "Test Drive", today,
        )
        assertEquals("Booking", ok.salesStage)
        assertEquals(LocalDate.of(2026, 10, 24), ok.nextDate)
        assertFalse(ok.dateDropped)
    }

    @Test fun normalizeLead_flagsInvalidMobile() {
        val n = Normalizer.normalizeLead(LeadExtraction(mobile = "1732289910"), today)
        assertEquals("601732289910", n.mobile)
        assertFalse(n.mobileValid)
    }
}

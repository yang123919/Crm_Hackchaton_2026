package com.example.hackchaton_2026_crm.ai

import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.MessageLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The sample voice lines from docs/PLAN.md must all parse, offline. */
class DemoAiServiceTest {
    private val today = LocalDate.of(2026, 10, 22)
    private val ai = DemoAiService(delayMs = 0)

    @Test fun sample1_tanAhKow() = runTest {
        val e = ai.extractLead(
            "Just met Mr Tan Ah Kow, 012-345 6789, interested in the X50, budget around 120k, " +
                "wants test drive next Tuesday, call him back Friday.",
            today,
        )
        assertEquals("Tan Ah", e.firstName)
        assertEquals("Kow", e.lastName)
        assertEquals("60123456789", e.mobile)
        assertEquals("Proton X50", e.product)
        assertEquals(120_000.0, e.budgetRm!!, 0.0)
        assertEquals("Test Drive", e.suggestedSalesStage)
        assertEquals("Call back about test drive", e.followUp.nextAction)
        assertEquals("2026-10-23", e.followUp.nextDate)
        assertTrue(e.followUp.summary.contains("Tue 27 Oct"))
    }

    @Test fun sample2_aisyahSpokenDigitsAndTradeIn() = runTest {
        val e = ai.extractLead(
            "Puan Aisyah, one seven three two two eight nine nine one zero, nak Saga, trade in Myvi 2018, follow up esok.",
            today,
        )
        assertEquals("Aisyah", e.firstName)
        assertEquals("Proton Saga", e.product)
        assertTrue(e.tradeIn.usedCar)
        assertEquals("Perodua", e.tradeIn.brand)
        assertEquals("Myvi", e.tradeIn.model)
        assertEquals(2018, e.tradeIn.year)
        assertEquals("2026-10-23", e.followUp.nextDate)
        assertNotNull(e.mobile)
        // Heard digits don't form a valid number: the review screen must show it red.
        assertTrue("mobile" in e.lowConfidenceFields)
    }

    @Test fun sample3_limButterworth() = runTest {
        val e = ai.extractLead(
            "Mr Lim from Butterworth, wants the Tiggo 8 Pro, no budget yet, send him quotation, WhatsApp him tomorrow.",
            today,
        )
        assertEquals("Lim", e.firstName)
        assertEquals("Butterworth Sunway Carnival", e.outlet)
        assertEquals("Tiggo 8 Pro", e.product)
        assertEquals(null, e.budgetRm)
        assertEquals("Quotation", e.suggestedSalesStage)
        assertEquals("Send quotation on WhatsApp", e.followUp.nextAction)
        assertEquals("2026-10-23", e.followUp.nextDate)
        assertFalse(e.tradeIn.usedCar)
    }

    @Test fun sample4_followUpBooking() = runTest {
        val lead = Lead(firstName = "Tan Ah", lastName = "Kow", mobile = "60123456789",
            product = "Proton X50", outlet = "CS Insta", salesStage = "Test Drive")
        val f = ai.extractFollowUp("Called Mr Tan, he's ok with the price, booking this weekend.", lead, today)
        assertEquals("Booking", f.suggestedSalesStage)
        assertEquals("He's ok with the price, booking this weekend.", f.followUp.summary)
        assertEquals("2026-10-24", f.followUp.nextDate)
    }

    @Test fun messyInput_neverCrashes() = runTest {
        listOf("", "   ", "hmm", "123", "budget", "trade in", "Mr", "call call call esok esok").forEach {
            Normalizer.normalizeLead(ai.extractLead(it, today), today)
        }
    }

    @Test fun whatsApp_bothLanguages() = runTest {
        val lead = Lead(firstName = "Aisyah", mobile = "60173228991", product = "Proton Saga",
            outlet = "CS Insta", salesStage = "Quotation")
        assertTrue(ai.draftWhatsApp(lead, null, MessageLanguage.EN, "Kelvin").contains("quotation"))
        assertTrue(ai.draftWhatsApp(lead, null, MessageLanguage.BM, "Kelvin").contains("sebut harga"))
    }

    @Test fun whatsApp_neverMentionsOverdueDate() = runTest {
        val lead = Lead(firstName = "Ahmad", mobile = "60139876543", product = "Omoda 5 Comfort",
            outlet = "CS Insta", salesStage = "Test Drive", nextFollowUpAt = LocalDate.of(2020, 1, 5))
        val text = ai.draftWhatsApp(lead, null, MessageLanguage.EN, "Kelvin")
        assertFalse(text.contains("5 Jan"))
        assertTrue(text.contains("test drive?"))
    }
}

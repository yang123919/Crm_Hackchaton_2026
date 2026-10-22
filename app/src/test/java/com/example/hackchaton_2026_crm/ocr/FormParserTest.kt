package com.example.hackchaton_2026_crm.ocr

import com.example.hackchaton_2026_crm.ai.DemoAiService
import com.example.hackchaton_2026_crm.ai.LeadExtraction
import com.example.hackchaton_2026_crm.ai.LeadField
import com.example.hackchaton_2026_crm.ai.Normalizer
import com.example.hackchaton_2026_crm.ui.review.LeadForm
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** All people, numbers and addresses are made up. */
class FormParserTest {
    private val today = LocalDate.of(2026, 10, 22)

    private val printedEnglishForm = listOf(
        "CS GROUP CUSTOMER ENQUIRY FORM",
        "Date: 22/10/2026",
        "Name: Tan Ah Kow",
        "IC No: 900101-14-1234",
        "Tel: 012-345 6789",
        "Email: tan@example.com",
        "Address: No 5 Jalan Damai",
        "11900 Bayan Lepas",
        "Pulau Pinang",
        "Model: X50",
        "Budget: RM 120,000",
        "Outlet: Alor Setar Aman Central",
        "Trade-in: Myvi 2018",
        "Remarks: wants test drive",
        "Salesperson: Kelvin",
    )

    @Test fun printedEnglishForm_everyField() {
        val f = FormParser.parse(printedEnglishForm).fields
        assertEquals("Tan Ah Kow", f[FormField.NAME])
        assertEquals("900101-14-1234", f[FormField.IC])
        assertEquals("012-345 6789", f[FormField.PHONE])
        assertEquals("tan@example.com", f[FormField.EMAIL])
        // The address continues over three rows and stops at the next label.
        assertEquals("No 5 Jalan Damai, 11900 Bayan Lepas, Pulau Pinang", f[FormField.ADDRESS])
        assertEquals("X50", f[FormField.CAR])
        assertEquals("RM 120,000", f[FormField.BUDGET])
        assertEquals("Alor Setar Aman Central", f[FormField.OUTLET])
        assertEquals("Myvi 2018", f[FormField.TRADE_IN])
        // "Salesperson:" is a label we don't use, so it ends the remarks instead of joining them.
        assertEquals("wants test drive", f[FormField.REMARKS])
    }

    @Test fun formTitleStartingWithCustomer_isNotTheName() {
        val f = FormParser.parse(listOf("CUSTOMER ENQUIRY FORM", "Name: Tan Ah Kow", "Tel: 012-345 6789")).fields
        assertEquals("Tan Ah Kow", f[FormField.NAME])
    }

    @Test fun malayLabels_valueOnTheNextRow() {
        val f = FormParser.parse(
            listOf("Nama", "Aisyah binti Rahman", "No. Tel", "017-322 8991", "Alamat", "12 Jalan Merah", "08000 Sungai Petani", "Kereta", "Saga"),
        ).fields
        assertEquals("Aisyah binti Rahman", f[FormField.NAME])
        assertEquals("017-322 8991", f[FormField.PHONE])
        assertEquals("12 Jalan Merah, 08000 Sungai Petani", f[FormField.ADDRESS])
        assertEquals("Saga", f[FormField.CAR])
    }

    @Test fun ocrJoinedLabelAndValue_withoutColons() {
        val f = FormParser.parse(listOf("Name Lim Mei Ling", "H/P 013-9876543", "Phone No.: 011-2345 6789", "No KP 850630-10-7788")).fields
        assertEquals("Lim Mei Ling", f[FormField.NAME])
        assertEquals("013-9876543", f[FormField.PHONE]) // the first phone field wins
        assertEquals("850630-10-7788", f[FormField.IC])
    }

    @Test fun lookalikeLettersInNumbersAreFixed_butWordsAreNot() {
        val f = FormParser.parse(listOf("Tel: O12-345 678g", "IC No: 9OO1O1-14-l234", "Budget: RM 12D.000", "Remarks: Sold to Bob")).fields
        assertEquals("012-345 6789", f[FormField.PHONE])
        assertEquals("900101-14-1234", f[FormField.IC])
        assertEquals("RM 120.000", f[FormField.BUDGET])
        assertEquals("Sold to Bob", f[FormField.REMARKS]) // not a numeric field
        assertEquals("Boss", FormParser.fixLookalikes("Boss")) // too few digits to be a number
    }

    @Test fun noLabels_keepsTheRawText() {
        val r = FormParser.parse(listOf("hello there", "some scribbles"))
        assertTrue(r.fields.isEmpty())
        assertEquals("hello there\nsome scribbles", r.toNotes())
    }

    @Test fun toNotes_isStableLabelledLines() {
        val notes = FormParser.parse(printedEnglishForm).toNotes()
        assertTrue(notes.startsWith("Name: Tan Ah Kow\nPhone: 012-345 6789"))
        assertEquals(FormParser.parseText(notes), FormParser.parse(printedEnglishForm).fields) // round trip
    }

    @Test fun garbageNeverCrashes() {
        listOf(emptyList(), listOf(""), listOf(":::", "Name:", "Tel"), listOf("x".repeat(2000))).forEach {
            FormParser.parse(it).toNotes()
        }
    }

    // ---- scanned form -> lead fields (offline extractor)

    @Test fun formNotes_becomeALead() = runTest {
        val notes = FormParser.parse(printedEnglishForm).toNotes()
        val e = DemoAiService(delayMs = 0).extractLead(notes, today)
        assertEquals("Tan Ah", e.firstName)
        assertEquals("Kow", e.lastName)
        assertEquals("60123456789", Normalizer.phone(e.mobile))
        assertEquals("tan@example.com", e.email)
        assertEquals("900101-14-1234", e.icNumber)
        assertEquals("No 5 Jalan Damai, 11900 Bayan Lepas, Pulau Pinang", e.address)
        assertEquals("Proton X50", e.product)
        assertEquals("Alor Setar Aman Central", e.outlet)
        assertEquals(120_000.0, e.budgetRm!!, 0.0)
        assertTrue(e.tradeIn.usedCar)
        assertEquals("Myvi", e.tradeIn.model)
        assertEquals(2018, e.tradeIn.year)
        assertTrue(e.followUp.summary.contains("wants test drive"))
    }

    @Test fun allCapsNameOnAForm_isTitleCased_andNoTradeInStaysOff() = runTest {
        val e = DemoAiService(delayMs = 0).extractLead("Name: NUR AISYAH BINTI ABDUL RAHMAN\nTel: 017-322 8991\nTrade-in: None", today)
        assertEquals("Nur Aisyah", e.firstName)
        assertEquals("binti Abdul Rahman", e.lastName)
        assertFalse(e.tradeIn.usedCar)
    }

    @Test fun icTypedInFreeText_isFound() = runTest {
        val e = DemoAiService(delayMs = 0).extractLead("Mr Tan, IC 900101141234, wants the X50", today)
        assertEquals("900101-14-1234", e.icNumber)
        assertEquals("Proton X50", e.product)
    }

    @Test fun invalidIcInNotes_isIgnored() = runTest {
        assertNull(DemoAiService(delayMs = 0).extractLead("Mr Tan, IC 901301141234", today).icNumber)
    }

    // ---- AI answer -> review form

    @Test fun aiIcAddressGender_reachTheForm_andBadOnesAreDropped() {
        val n = Normalizer.normalizeLead(
            LeadExtraction(firstName = "Tan", icNumber = "900101141234", address = " 5 Jalan Damai ", gender = "lelaki"), today,
        )
        assertEquals("900101-14-1234", n.icNumber)
        assertEquals("5 Jalan Damai", n.address)
        assertEquals("Male", n.gender)
        val form = LeadForm.from(n, null)
        assertEquals("Male", form.gender)
        assertTrue(LeadField.IC_NUMBER in form.aiFilled)

        val bad = Normalizer.normalizeLead(LeadExtraction(icNumber = "12345", gender = "robot"), today)
        assertEquals("", bad.icNumber)
        assertEquals("", bad.gender)
    }
}

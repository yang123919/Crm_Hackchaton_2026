package com.example.hackchaton_2026_crm.ui.review

import com.example.hackchaton_2026_crm.ai.LeadExtraction
import com.example.hackchaton_2026_crm.ai.LeadField
import com.example.hackchaton_2026_crm.ai.Normalizer
import com.example.hackchaton_2026_crm.ai.TradeIn
import com.example.hackchaton_2026_crm.ocr.IcInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LeadFormTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun emptyForm_requiresNameMobileProductOutlet() {
        assertEquals(
            setOf(LeadField.FIRST_NAME, LeadField.MOBILE, LeadField.PRODUCT, LeadField.OUTLET),
            LeadForm().errors(today).keys,
        )
    }

    @Test fun defaultOutlet_fillsMissingOutlet() {
        val n = Normalizer.normalizeLead(LeadExtraction(firstName = "Lim"), today)
        val form = LeadForm.from(n, defaultOutlet = "CS Insta")
        assertEquals("CS Insta", form.outlet)
        assertTrue(LeadField.OUTLET !in form.aiFilled)
    }

    @Test fun validForm_buildsNormalisedLead() {
        val form = LeadForm(
            firstName = " Tan Ah ", lastName = "Kow", mobile = "012-345 6789",
            product = "Proton X50", outlet = "CS Insta", budget = "120k",
            tradeIn = false, tradeInModel = "Myvi", nextDate = today.plusDays(1),
        )
        assertTrue(form.errors(today).isEmpty())
        val lead = form.toLead(today)
        assertEquals("Tan Ah", lead.firstName)
        assertEquals("60123456789", lead.mobile)
        assertEquals(120_000L, lead.budgetRm)
        assertNull(lead.tradeInModel) // trade-in switched off → details dropped
        assertEquals(today.plusDays(1), lead.nextFollowUpAt)
    }

    @Test fun invalidMobileFromAi_isFlagged() {
        val n = Normalizer.normalizeLead(
            LeadExtraction(mobile = "1732289910", tradeIn = TradeIn(usedCar = true, year = 2018)), today,
        )
        val form = LeadForm.from(n, null)
        assertTrue(LeadField.MOBILE in form.lowConfidence)
        assertTrue(LeadField.MOBILE in form.errors(today))
        assertEquals("2018", form.tradeInYear)
    }

    @Test fun scannedIc_overridesNameAndFillsIcAddressGender() {
        val n = Normalizer.normalizeLead(LeadExtraction(firstName = "Aisha", mobile = "0123456789"), today)
        val ic = IcInfo(
            fullName = "Nur Aisyah binti Abdul Rahman", icNumber = "900101-14-1234",
            address = "No 12, Jalan Contoh 3, 50450 Kuala Lumpur", gender = "Female",
        )
        val form = LeadForm.from(n, null).withIc(ic)
        assertEquals("Nur Aisyah", form.firstName)
        assertEquals("binti Abdul Rahman", form.lastName)
        assertEquals("900101-14-1234", form.icNumber)
        assertEquals("Female", form.gender)
        assertEquals("60123456789", form.normalizedMobile) // phone still comes from speech
        assertTrue(LeadField.IC_NUMBER in form.aiFilled)
    }

    @Test fun icNumber_isValidatedAndNormalisedOnSave() {
        val base = LeadForm(firstName = "A", mobile = "0123456789", product = "Proton X50", outlet = "CS Insta")
        assertTrue(LeadField.IC_NUMBER in base.copy(icNumber = "901301-14-1234").errors(today))
        assertTrue(LeadField.IC_NUMBER !in base.copy(icNumber = "").errors(today))
        assertEquals("900101-14-1234", base.copy(icNumber = "900101141234").toLead(today).icNumber)
        assertNull(base.copy(icNumber = "").toLead(today).icNumber)
        assertEquals("******-**-1234", base.copy(icNumber = "900101-14-1234").toLead(today).maskedIc)
    }

    @Test fun emptyIcScan_changesNothing() {
        val form = LeadForm(firstName = "Lim")
        assertEquals(form, form.withIc(IcInfo()))
    }
}

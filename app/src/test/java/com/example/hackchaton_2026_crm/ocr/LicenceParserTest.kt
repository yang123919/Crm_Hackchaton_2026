package com.example.hackchaton_2026_crm.ocr

import com.example.hackchaton_2026_crm.data.LicenceRules
import com.example.hackchaton_2026_crm.data.LicenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** All names and numbers below are made up. Rows mimic what OCR returns for the card, top to bottom. */
class LicenceParserTest {
    private val today = LocalDate.of(2026, 10, 23)

    private fun card(header: String, validity: String, cls: String = "D") = listOf(
        header,
        "DRIVING LICENCE",
        "TAN AH KOW",
        "No. Pengenalan / Identity No. Tarikh Lahir / Date Of Birth",
        "900101-14-5677 01/01/1990",
        "Warganegara / Nationality Kelas / Class",
        "MALAYSIA $cls",
        "Tempoh / Validity",
        validity,
        "Alamat / Address",
        "7 LORONG DAMAI 2",
        "11900 BAYAN LEPAS",
        "PULAU PINANG",
    )

    @Test fun fullLicence_readsEverything() {
        val info = LicenceParser.parse(card("LESEN MEMANDU MALAYSIA", "12/03/2024 - 11/03/2029"), today)
        assertEquals("Tan Ah Kow", info.fullName)
        assertEquals("900101-14-5677", info.icNumber)
        assertEquals(listOf("D"), info.classes)
        assertEquals(LocalDate.of(2024, 3, 12), info.validFrom)
        assertEquals(LocalDate.of(2029, 3, 11), info.validTo)
        assertEquals(LicenceType.CDL, info.type)
        assertEquals("7 Lorong Damai 2, 11900 Bayan Lepas, Pulau Pinang", info.address)
        assertTrue(info.warnings.toString(), info.warnings.isEmpty())
    }

    @Test fun redPInTheHeader_isProbationary() {
        val info = LicenceParser.parse(card("LESEN MEMANDU MALAYSIA P", "05/05/2025 - 04/05/2027"), today)
        assertEquals(LicenceType.PDL, info.type)
    }

    @Test fun pBadgeReadAsItsOwnRow_isProbationary() {
        val rows = listOf("P") + card("LESEN MEMANDU MALAYSIA", "05/05/2025 - 04/05/2027")
        assertEquals(LicenceType.PDL, LicenceParser.parse(rows, today).type)
    }

    @Test fun twoYearsWithoutAP_isLeftForTheSalesperson() {
        val info = LicenceParser.parse(card("LESEN MEMANDU MALAYSIA", "05/05/2025 - 04/05/2027"), today)
        assertNull(info.type)
        assertFalse(info.typeFromPhoto)
        assertTrue(info.warnings.any { it.startsWith("Licence type") })
    }

    // ---- A type read from the photo is locked; only an unclear one may be picked by hand ---------

    @Test fun typeReadFromThePhoto_isLocked() {
        assertTrue(LicenceParser.parse(card("LESEN MEMANDU MALAYSIA P", "05/05/2025 - 04/05/2027"), today).typeFromPhoto)
        assertTrue(LicenceParser.parse(card("LESEN MEMANDU MALAYSIA", "12/03/2024 - 11/03/2029"), today).typeFromPhoto)
    }

    private fun lead(type: LicenceType?, fromPhoto: Boolean?) = com.example.hackchaton_2026_crm.data.Lead(
        firstName = "Tan", mobile = "60123456789", product = "Proton X50", outlet = "CS Insta",
        licenceType = type?.name, licenceTypeFromPhoto = fromPhoto,
    )

    @Test fun savedLicence_lockFollowsWhereTheTypeCameFrom() {
        assertTrue(lead(LicenceType.PDL, fromPhoto = true).licenceTypeLocked)
        assertFalse(lead(LicenceType.CDL, fromPhoto = false).licenceTypeLocked)
        assertFalse(lead(null, fromPhoto = false).licenceTypeLocked)
        // Saved before this was tracked: a P licence must not become switchable.
        assertTrue(lead(LicenceType.PDL, fromPhoto = null).licenceTypeLocked)
        assertFalse(lead(null, fromPhoto = null).licenceTypeLocked)
    }

    @Test fun severalClasses_andAutoOnly() {
        assertEquals(listOf("B2", "D"), LicenceParser.parse(card("LESEN MEMANDU MALAYSIA", "12/03/2024 - 11/03/2029", "B2 D"), today).classes)
        assertEquals(listOf("DA"), LicenceParser.parse(card("LESEN MEMANDU MALAYSIA", "12/03/2024 - 11/03/2029", "DA"), today).classes)
    }

    @Test fun birthDateIsNotTakenForValidity_whenTheDashIsLost() {
        val info = LicenceParser.parse(card("LESEN MEMANDU MALAYSIA", "12/03/2024 11/03/2029"), today)
        assertEquals(LocalDate.of(2024, 3, 12), info.validFrom)
        assertEquals(LocalDate.of(2029, 3, 11), info.validTo)
    }

    @Test fun watermarkIsNotTheName() {
        val rows = card("LESEN MEMANDU MALAYSIA", "12/03/2024 - 11/03/2029").toMutableList()
        rows.add(3, "Jabatan Pengangkutan Jalan")
        assertEquals("Tan Ah Kow", LicenceParser.parse(rows, today).fullName)
    }

    @Test fun anIcCard_isFlagged() {
        val info = LicenceParser.parse(listOf("KAD PENGENALAN", "MyKad", "900101-14-5677", "TAN AH KOW"), today)
        assertTrue(info.warnings.contains("This doesn't look like a driving licence"))
    }

    @Test fun licenceFillsTheSameFieldsAsAnIc() {
        val ic = LicenceParser.parse(card("LESEN MEMANDU MALAYSIA", "12/03/2024 - 11/03/2029"), today).toIcInfo(today)
        assertEquals("Tan Ah", ic.firstName)
        assertEquals("Kow", ic.lastName)
        assertEquals("900101-14-5677", ic.icNumber)
        assertEquals("Male", ic.gender)
    }

    // ---- Booking rule: CDL only, car class, not expired --------------------------------------

    private fun check(type: LicenceType?, classes: List<String> = listOf("D"), validTo: LocalDate? = today.plusYears(2), sameIc: Boolean = true) =
        LicenceRules.check(type, classes, validTo, today, sameIc)

    @Test fun cdlWithCarClass_canBook() {
        assertTrue(check(LicenceType.CDL).allowed)
        assertTrue(check(LicenceType.CDL, listOf("DA")).allowed)
        assertTrue(check(LicenceType.CDL, listOf("B2", "D")).allowed)
    }

    @Test fun onlyCdl() {
        assertFalse(check(LicenceType.PDL).allowed)
        assertFalse(check(LicenceType.LDL).allowed)
        assertFalse(check(null).allowed)
    }

    @Test fun expiredOrMotorcycleOnlyOrSomeoneElses_cannotBook() {
        assertFalse(check(LicenceType.CDL, validTo = today.minusDays(1)).allowed)
        assertTrue(check(LicenceType.CDL, validTo = today).allowed)
        assertFalse(check(LicenceType.CDL, listOf("B2")).allowed)
        assertFalse(check(LicenceType.CDL, emptyList()).allowed)
        assertFalse(check(LicenceType.CDL, validTo = null).allowed)
        assertFalse(check(LicenceType.CDL, sameIc = false).allowed)
    }
}

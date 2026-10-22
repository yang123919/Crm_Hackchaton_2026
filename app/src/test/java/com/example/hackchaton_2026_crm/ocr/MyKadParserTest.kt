package com.example.hackchaton_2026_crm.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** All names and numbers below are made up. */
class MyKadParserTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun cleanCard_allFourFields() {
        val info = MyKadParser.parse(
            listOf(
                "KAD PENGENALAN", "MALAYSIA", "MyKad",
                "900101-14-1234",
                "NUR AISYAH BINTI ABDUL RAHMAN",
                "NO 12, JALAN CONTOH 3",
                "TAMAN SAMPEL",
                "50450 KUALA LUMPUR",
                "WILAYAH PERSEKUTUAN",
                "WARGANEGARA", "ISLAM", "PEREMPUAN",
            ),
            today,
        )
        assertEquals("900101-14-1234", info.icNumber)
        assertEquals("Nur Aisyah binti Abdul Rahman", info.fullName)
        assertEquals("Female", info.gender)
        assertEquals(LocalDate.of(1990, 1, 1), info.birthDate)
        assertEquals(
            "No 12, Jalan Contoh 3, Taman Sampel, 50450 Kuala Lumpur, Wilayah Persekutuan",
            info.address,
        )
        assertTrue(info.warnings.isEmpty())
        assertEquals("Nur Aisyah", info.firstName)
        assertEquals("binti Abdul Rahman", info.lastName)
        assertEquals("******-**-1234", info.maskedIc)
    }

    @Test fun chineseName_lastWordIsLastName() {
        val info = MyKadParser.parse(
            listOf("010203-07-5011", "TAN AH KOW", "7 LORONG DAMAI 2", "11900 BAYAN LEPAS", "PULAU PINANG", "LELAKI", "WARGANEGARA"),
            today,
        )
        assertEquals("Tan Ah Kow", info.fullName)
        assertEquals("Tan Ah", info.firstName)
        assertEquals("Kow", info.lastName)
        assertEquals("Male", info.gender)
        assertEquals(LocalDate.of(2001, 2, 3), info.birthDate)
        assertEquals("7 Lorong Damai 2, 11900 Bayan Lepas, Pulau Pinang", info.address)
    }

    @Test fun ocrMixesUpDigitsAndPunctuation() {
        // O for 0, l for 1, and a dot instead of a dash.
        val info = MyKadParser.parse(listOf("9OOlOl.14.l234", "AHMAD BIN ABDULLAH", "LOT 5 KAMPUNG BARU", "08000 SUNGAI PETANI", "KEDAH"), today)
        assertEquals("900101-14-1234", info.icNumber)
        assertEquals("Ahmad bin Abdullah", info.fullName)
        assertEquals("Ahmad", info.firstName)
        assertEquals("bin Abdullah", info.lastName)
    }

    @Test fun genderFallsBackToLastDigit_andDisagreementIsFlagged() {
        val noWord = MyKadParser.parse(listOf("900101-14-1235", "ALI BIN ABU", "NO 1 JALAN 1", "KEDAH"), today)
        assertEquals("Male", noWord.gender) // odd = male
        val clash = MyKadParser.parse(listOf("900101-14-1235", "ALI BIN ABU", "NO 1 JALAN 1", "KEDAH", "PEREMPUAN"), today)
        assertEquals("Female", clash.gender) // printed word wins
        assertTrue(clash.warnings.any { it.contains("disagree") })
    }

    @Test fun nameBeforeNumber_stillFound() {
        val info = MyKadParser.parse(listOf("SITI NURHALIZA BINTI TARUDIN", "850630-10-7788", "12 JALAN MERAH", "KEDAH"), today)
        assertEquals("Siti Nurhaliza binti Tarudin", info.fullName)
        assertEquals("850630-10-7788", info.icNumber)
        assertEquals("12 Jalan Merah, Kedah", info.address)
    }

    @Test fun invalidDateIsNotAnIc() {
        // Month 13 can't be a birth date, so this is not read as an IC number.
        val info = MyKadParser.parse(listOf("901301-14-1234", "ALI BIN ABU"), today)
        assertEquals("", info.icNumber)
        assertTrue(info.warnings.contains("IC number not read"))
    }

    @Test fun centuryIsNeverInTheFuture() {
        assertEquals(LocalDate.of(2005, 5, 5), MyKadParser.birthDate("050505", today))
        assertEquals(LocalDate.of(1927, 5, 5), MyKadParser.birthDate("270505", today))
        assertEquals(null, MyKadParser.birthDate("990231", today)) // 31 Feb
    }

    @Test fun garbageNeverCrashes() {
        listOf(emptyList(), listOf(""), listOf("???", "!!"), listOf("1234", "abc"), listOf("a".repeat(500))).forEach {
            val info = MyKadParser.parse(it, today)
            assertEquals("", info.icNumber)
            assertTrue(info.warnings.contains("IC number not read"))
            assertTrue(info.fullName.length <= 60 && info.address.length <= 200)
        }
    }

    @Test fun addressNumberGluedToWord_isSplit_butShortCodesStay() {
        assertEquals("No 12, Jalan Contoh 3, SS2, 50450 Kuala Lumpur", MyKadParser.parse(
            listOf("900101-14-1234", "AHMAD BIN ABU", "NO 12, JALAN CONTOH3", "SS2", "50450 KUALA LUMPUR"), today,
        ).address)
    }

    @Test fun splitName_edgeCases() {
        assertEquals("" to "", splitName(""))
        assertEquals("Aisyah" to "", splitName("Aisyah"))
        assertEquals("Raj" to "a/l Kumar", splitName("Raj a/l Kumar"))
        assertEquals("Lim Mei" to "Ling", splitName("Lim Mei Ling"))
    }
}

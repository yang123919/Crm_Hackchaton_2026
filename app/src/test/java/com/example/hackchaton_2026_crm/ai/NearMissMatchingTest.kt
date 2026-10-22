package com.example.hackchaton_2026_crm.ai

import com.example.hackchaton_2026_crm.data.ReferenceLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Misspelt dropdown values (typos, OCR slips) resolve only when exactly one option is one edit away. */
class NearMissMatchingTest {
    @Test fun oneEditAwayFromExactlyOneOption() {
        assertEquals("Hyundai Ioniq Hev Plus", Normalizer.matchOption("ionic", ReferenceLists.products))
        assertEquals("Tiggo 8 Pro", Normalizer.matchOption("tigo", ReferenceLists.products))
        assertEquals("Proton Saga", Normalizer.matchOption("sagaa", ReferenceLists.products))
    }

    @Test fun neverGuessesWhenUnsureOrShort() {
        assertNull(Normalizer.matchOption("xyz", ReferenceLists.products))
        assertNull(Normalizer.matchOption("hyundai", ReferenceLists.products)) // several options
        assertNull(Normalizer.matchOption("zebra", ReferenceLists.products))
    }

    @Test fun editDistance() {
        assertEquals(0, Normalizer.editDistance("saga", "saga"))
        assertEquals(1, Normalizer.editDistance("ionic", "ioniq"))
        assertEquals(1, Normalizer.editDistance("tigo", "tiggo"))
        assertTrue(Normalizer.editDistance("abc", "xyz") > 1)
    }

    @Test fun gender() {
        assertEquals("Male", Normalizer.gender("Lelaki"))
        assertEquals("Female", Normalizer.gender(" PEREMPUAN "))
        assertNull(Normalizer.gender("other"))
        assertNull(Normalizer.gender(null))
    }
}

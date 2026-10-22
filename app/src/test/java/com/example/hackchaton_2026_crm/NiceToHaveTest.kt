package com.example.hackchaton_2026_crm

import com.example.hackchaton_2026_crm.data.CaptureMetric
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.reminder.Reminders
import com.example.hackchaton_2026_crm.ui.today.averageCaptureSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class NiceToHaveTest {
    private val t0 = Instant.parse("2026-10-22T02:00:00Z")
    private val today = LocalDate.of(2026, 10, 22)

    private fun metric(seconds: Long, type: String = CaptureMetric.TYPE_NEW_LEAD) =
        CaptureMetric(startedAt = t0, savedAt = t0.plusSeconds(seconds), type = type)

    @Test fun averageCapture_onlyNewLeadsAndIgnoresAbandoned() {
        assertNull(averageCaptureSeconds(emptyList()))
        assertEquals(
            25,
            averageCaptureSeconds(
                listOf(metric(20), metric(30), metric(5, CaptureMetric.TYPE_FOLLOW_UP), metric(4_000)),
            ),
        )
    }

    private fun lead(name: String, due: LocalDate?, stage: String = "Follow Up") =
        Lead(firstName = name, mobile = "60123456789", product = "Proton X50", outlet = "CS Insta",
            salesStage = stage, nextFollowUpAt = due)

    @Test fun reminderMessage_countsOverdueAndToday() {
        val (title, text) = Reminders.message(
            listOf(
                lead("Tan", today.minusDays(2)),
                lead("Ahmad", today.minusDays(4)),
                lead("Aisyah", today),
                lead("Farid", today.minusDays(1), stage = "Closed Won"),
            ),
            today,
        )!!
        assertEquals("2 overdue · 1 due today", title)
        assertEquals("Follow up with Ahmad, Tan, Aisyah", text)
    }

    @Test fun reminderMessage_nullWhenNothingDue() {
        assertNull(Reminders.message(listOf(lead("Farid", today, stage = "Completed")), today))
    }
}

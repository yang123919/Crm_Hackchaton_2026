package com.example.hackchaton_2026_crm.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LeadGroupsTest {
    // A Thursday; the week started on Monday 19 Oct.
    private val today = LocalDate.of(2026, 10, 22)

    private fun lead(name: String, due: LocalDate?, stage: String = "Follow Up", createdDaysAgo: Long = 30) = Lead(
        firstName = name, mobile = "60123456789", product = "Proton X50", outlet = "CS Insta",
        salesStage = stage, nextFollowUpAt = due,
        createdAt = today.minusDays(createdDaysAgo).atTime(10, 0).atZone(SeedData.ZONE).toInstant(),
    )

    private val leads = listOf(
        lead("OldOverdue", today.minusDays(4)),
        lead("RecentOverdue", today.minusDays(1)),
        lead("DueToday", today),
        lead("Future", today.plusDays(3)),
        lead("NoDate", null),
        lead("ClosedOverdue", today.minusDays(2), stage = "Closed Won"),
        lead("NewToday", null, createdDaysAgo = 0),
        lead("NewMonday", today.plusDays(1), createdDaysAgo = 3),
        lead("LastSunday", null, createdDaysAgo = 4),
    )

    @Test fun overdue_excludesClosedAndFuture_oldestFirst() {
        assertEquals(listOf("OldOverdue", "RecentOverdue"), LeadGroups.overdue(leads, today).map { it.firstName })
    }

    @Test fun dueToday_onlyToday() {
        assertEquals(listOf("DueToday"), LeadGroups.dueToday(leads, today).map { it.firstName })
    }

    @Test fun capturedThisWeek_sinceMonday_newestFirst() {
        assertEquals(listOf("NewToday", "NewMonday"), LeadGroups.capturedThisWeek(leads, today).map { it.firstName })
    }
}

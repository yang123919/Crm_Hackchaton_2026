package com.example.hackchaton_2026_crm.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The three counters on Today; each opens the list of customers behind its number. */
enum class LeadGroup(val title: String, val emptyText: String) {
    OVERDUE("Overdue", "Nothing overdue. Nice work."),
    DUE_TODAY("Due today", "No follow-ups due today."),
    CAPTURED_THIS_WEEK("Captured this week", "No customers captured this week yet."),
}

/** One definition of who is overdue / due / captured, shared by the counters and their lists so the numbers always match. */
object LeadGroups {
    private fun open(leads: List<Lead>) =
        leads.filter { it.nextFollowUpAt != null && it.salesStage !in ReferenceLists.closedStages }

    fun overdue(leads: List<Lead>, today: LocalDate): List<Lead> =
        open(leads).filter { it.nextFollowUpAt!! < today }.sortedBy { it.nextFollowUpAt }

    fun dueToday(leads: List<Lead>, today: LocalDate): List<Lead> =
        open(leads).filter { it.nextFollowUpAt == today }.sortedBy { it.fullName }

    fun weekStart(today: LocalDate): Instant =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(SeedData.ZONE).toInstant()

    /** Customers created since Monday, newest first. */
    fun capturedThisWeek(leads: List<Lead>, today: LocalDate): List<Lead> =
        leads.filter { it.createdAt >= weekStart(today) }.sortedByDescending { it.createdAt }

    fun of(group: LeadGroup, leads: List<Lead>, today: LocalDate): List<Lead> = when (group) {
        LeadGroup.OVERDUE -> overdue(leads, today)
        LeadGroup.DUE_TODAY -> dueToday(leads, today)
        LeadGroup.CAPTURED_THIS_WEEK -> capturedThisWeek(leads, today)
    }
}

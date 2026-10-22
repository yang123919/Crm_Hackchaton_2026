package com.example.hackchaton_2026_crm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DataHealthTest {
    private val today = LocalDate.of(2026, 10, 22)

    private fun lead(
        id: Long,
        name: String = "Aisyah",
        mobile: String = "60173228991",
        ic: String? = null,
        stage: String = "Follow Up",
        due: LocalDate? = today.plusDays(2),
        tradeIn: Boolean = false,
        tradeBrand: String? = null,
        budget: Long? = null,
        email: String? = null,
    ) = Lead(
        id = id, firstName = name, mobile = mobile, icNumber = ic, product = "Proton X50", outlet = "CS Insta",
        salesStage = stage, nextFollowUpAt = due, tradeInUsedCar = tradeIn, tradeInBrand = tradeBrand,
        budgetRm = budget, email = email,
        createdAt = today.minusDays(30).atTime(10, 0).atZone(SeedData.ZONE).toInstant(),
    )

    private fun entry(id: Long, leadId: Long, date: LocalDate = today, summary: String, secs: Long = id) =
        FollowUpEntry(id = id, leadId = leadId, date = date, summary = summary, createdAt = Instant.ofEpochSecond(secs))

    private fun scan(leads: List<Lead>, entries: List<FollowUpEntry> = emptyList()) = DataHealth.scan(leads, entries, today)

    @Test fun cleanData_hasNoIssues() {
        val issues = scan(
            listOf(lead(1), lead(2, name = "Wong", mobile = "601123456789")),
            listOf(entry(1, 1, summary = "Called")),
        )
        assertTrue(issues.toString(), issues.isEmpty())
    }

    @Test fun sameIc_withDifferentPhones_isOneDuplicateGroup() {
        val issues = scan(listOf(lead(1, ic = "900101-14-1234"), lead(2, mobile = "60123456789", ic = "900101141234")))
        assertEquals(1, issues.size)
        val issue = issues.single()
        assertEquals(IssueKind.DUPLICATE_CUSTOMER, issue.kind)
        assertEquals(Severity.HIGH, issue.severity)
        val fix = issue.fix as HealthFix.MergeCustomers
        assertEquals(setOf(1L, 2L), setOf(fix.keeperId) + fix.dropIds)
    }

    @Test fun samePhone_isDuplicate_andWarnsToCheckThePerson() {
        val issue = scan(listOf(lead(1), lead(2, name = "Siti"))).single()
        assertEquals(IssueKind.DUPLICATE_CUSTOMER, issue.kind)
        assertTrue(issue.detail.contains("Check they are the same person"))
    }

    @Test fun merge_keepsTheMoreCompleteRecord() {
        val sparse = lead(1)
        val rich = lead(2, email = "a@b.com", budget = 80_000)
        val fix = scan(listOf(sparse, rich)).single().fix as HealthFix.MergeCustomers
        assertEquals(2L, fix.keeperId)
        assertEquals(listOf(1L), fix.dropIds)
    }

    @Test fun merge_isTransitive() {
        // 1 and 2 share a phone, 2 and 3 share an IC: all three are one person.
        val issues = scan(
            listOf(
                lead(1, mobile = "60171111111"),
                lead(2, mobile = "60171111111", ic = "900101141234"),
                lead(3, mobile = "60172222222", ic = "900101141234"),
            ),
        )
        assertEquals(1, issues.size)
        assertEquals(2, (issues.single().fix as HealthFix.MergeCustomers).dropIds.size)
    }

    @Test fun mergedLead_fillsGapsFromTheOthers() {
        val keeper = lead(1)
        val other = lead(2, email = "a@b.com", budget = 80_000, tradeIn = true, tradeBrand = "Perodua")
        val merged = DataHealth.merge(keeper, listOf(other))
        assertEquals(1L, merged.id)
        assertEquals("a@b.com", merged.email)
        assertEquals(80_000L, merged.budgetRm)
        assertTrue(merged.tradeInUsedCar)
        assertEquals("Perodua", merged.tradeInBrand)
    }

    @Test fun duplicateWhatsApp_sameDay_keepsOnlyTheNewest() {
        val prefix = FollowUpEntry.WHATSAPP_PREFIX
        val issue = scan(
            listOf(lead(1)),
            listOf(
                entry(1, 1, summary = "$prefix \"Hi Aisyah, saya Kelvin\"", secs = 10),
                entry(2, 1, summary = "$prefix \"Hi Aisyah, this is Kelvin\"", secs = 30),
                entry(3, 1, summary = "$prefix \"Hi again\"", secs = 20),
            ),
        ).single()
        assertEquals(IssueKind.DUPLICATE_HISTORY, issue.kind)
        assertEquals(listOf(1L, 3L), (issue.fix as HealthFix.RemoveEntries).entryIds)
    }

    @Test fun whatsApp_onDifferentDays_isNotDuplicate() {
        val prefix = FollowUpEntry.WHATSAPP_PREFIX
        val issues = scan(
            listOf(lead(1)),
            listOf(
                entry(1, 1, date = today, summary = "$prefix \"a\""),
                entry(2, 1, date = today.minusDays(1), summary = "$prefix \"b\""),
            ),
        )
        assertTrue(issues.isEmpty())
    }

    @Test fun identicalNotes_sameDay_areDuplicates() {
        val issue = scan(
            listOf(lead(1)),
            listOf(entry(1, 1, summary = "Wants Saga"), entry(2, 1, summary = " wants saga ")),
        ).single()
        assertEquals(listOf(1L), (issue.fix as HealthFix.RemoveEntries).entryIds)
    }

    @Test fun invalidPhone_isFlaggedWithNoAutoFix() {
        val issue = scan(listOf(lead(1, mobile = "12345"))).single()
        assertEquals(IssueKind.INVALID_PHONE, issue.kind)
        assertNull(issue.fix)
    }

    @Test fun inconsistentDetails_areListedInOneIssue() {
        val issue = scan(listOf(lead(1, tradeIn = true, budget = 800, email = "not-an-email"))).single()
        assertEquals(IssueKind.INCONSISTENT, issue.kind)
        assertTrue(issue.detail.contains("Trade-in is ticked"))
        assertTrue(issue.detail.contains("budget"))
        assertTrue(issue.detail.contains("email"))
    }

    @Test fun staleFollowUp_needs14Days_andIgnoresClosedCustomers() {
        val leads = listOf(
            lead(1, name = "Old", mobile = "60171111111", due = today.minusDays(14)),
            lead(2, name = "Recent", mobile = "60172222222", due = today.minusDays(13)),
            lead(3, name = "Closed", mobile = "60173333333", due = today.minusDays(40), stage = "Closed Won"),
        )
        val issue = scan(leads).single()
        assertEquals(IssueKind.STALE_FOLLOW_UP, issue.kind)
        assertEquals(1L, issue.leadId)
    }

    @Test fun issues_areSortedWorstFirst() {
        val issues = scan(
            listOf(
                lead(1, name = "Old", mobile = "60171111111", due = today.minusDays(30)),
                lead(2, name = "BadPhone", mobile = "999"),
            ),
        )
        assertEquals(listOf(Severity.HIGH, Severity.LOW), issues.map { it.severity })
    }
}

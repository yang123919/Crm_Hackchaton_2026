package com.example.hackchaton_2026_crm.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * Fake "GCR TEST"-style customers. Dates are relative to the install day so the
 * Today screen always has overdue and due-today cards for the demo.
 */
object SeedData {
    val ZONE: ZoneId = ZoneId.of("Asia/Kuala_Lumpur")

    suspend fun seedIfEmpty(dao: LeadDao, today: LocalDate = LocalDate.now(ZONE)) {
        if (dao.count() > 0) return
        seeds(today).forEach { (lead, entries) ->
            val id = dao.insert(lead)
            entries.forEach { dao.insertEntry(it.copy(leadId = id)) }
        }
    }

    private fun daysAgo(today: LocalDate, n: Long) =
        today.minusDays(n).atTime(10, 0).atZone(ZONE).toInstant()

    private fun seeds(today: LocalDate): List<Pair<Lead, List<FollowUpEntry>>> = listOf(
        Lead(
            firstName = "Tan Ah", lastName = "Kow", mobile = "60123456789",
            product = "Proton X50", outlet = "Alor Setar Aman Central", salesStage = "Follow Up",
            budgetRm = 120_000, nextFollowUpAt = today.minusDays(2),
            nextAction = "Call back about test drive",
            createdAt = daysAgo(today, 9), updatedAt = daysAgo(today, 5),
        ) to listOf(
            FollowUpEntry(
                leadId = 0, date = today.minusDays(9),
                summary = "Walk-in at Aman Central. Interested in X50, budget ~RM120k.",
                nextAction = "Send brochure", nextDate = today.minusDays(5),
            ),
            FollowUpEntry(
                leadId = 0, date = today.minusDays(5),
                summary = "Sent brochure on WhatsApp. Wants to try the car first.",
                nextAction = "Call back about test drive", nextDate = today.minusDays(2),
            ),
        ),
        Lead(
            firstName = "GCR TEST", lastName = "Ahmad", mobile = "60139876543",
            product = "Omoda 5 Comfort", outlet = "CS Insta", salesStage = "Test Drive",
            nextFollowUpAt = today.minusDays(4), nextAction = "Ask for test drive feedback",
            createdAt = daysAgo(today, 12), updatedAt = daysAgo(today, 6),
        ) to listOf(
            FollowUpEntry(
                leadId = 0, date = today.minusDays(6),
                summary = "Test drove Omoda 5. Comparing with Proton X50.",
                nextAction = "Ask for test drive feedback", nextDate = today.minusDays(4),
            ),
        ),
        Lead(
            firstName = "Aisyah", lastName = "binti Rahman", mobile = "60173228991",
            product = "Proton Saga", outlet = "Alor Setar Aeon Big", salesStage = "Quotation",
            budgetRm = 45_000, tradeInUsedCar = true, tradeInBrand = "Perodua",
            tradeInModel = "Myvi", tradeInYear = 2018,
            nextFollowUpAt = today, nextAction = "Send quotation, trade-in Myvi",
            createdAt = daysAgo(today, 3), updatedAt = daysAgo(today, 1),
        ) to listOf(
            FollowUpEntry(
                leadId = 0, date = today.minusDays(3),
                summary = "Wants Saga, trading in 2018 Myvi. Needs quotation with trade-in value.",
                nextAction = "Send quotation, trade-in Myvi", nextDate = today,
            ),
        ),
        Lead(
            firstName = "Wong", lastName = "Mei Ling", mobile = "60164441234",
            product = "Hyundai Ioniq Hev Plus", outlet = "SP Village Mall", salesStage = "Follow up 2",
            budgetRm = 140_000, nextFollowUpAt = today, nextAction = "Confirm loan documents",
            createdAt = daysAgo(today, 2), updatedAt = daysAgo(today, 2),
        ) to listOf(
            FollowUpEntry(
                leadId = 0, date = today.minusDays(2),
                summary = "Likes the Ioniq hybrid. Asked about bank loan options.",
                nextAction = "Confirm loan documents", nextDate = today,
            ),
        ),
        Lead(
            firstName = "Rajesh", lastName = "Kumar", mobile = "60192223344",
            product = "3008 MidLife", outlet = "Butterworth Sunway Carnival", salesStage = "Booking",
            budgetRm = 160_000, expectedCloseDate = today.plusDays(14),
            nextFollowUpAt = today.plusDays(3), nextAction = "Collect booking fee",
            createdAt = daysAgo(today, 20), updatedAt = daysAgo(today, 1),
        ) to listOf(
            FollowUpEntry(
                leadId = 0, date = today.minusDays(1),
                summary = "Agreed on price. Will pay booking fee this week.",
                nextAction = "Collect booking fee", nextDate = today.plusDays(3),
            ),
        ),
        Lead(
            firstName = "GCR TEST", lastName = "Farid", mobile = "60115556677",
            product = "GWM Cannon", outlet = "B.M AEON Alma", salesStage = "Closed Won",
            status = LeadStatus.CONVERTED, budgetRm = 150_000,
            createdAt = daysAgo(today, 40), updatedAt = daysAgo(today, 10),
        ) to listOf(
            FollowUpEntry(
                leadId = 0, date = today.minusDays(10),
                summary = "Car delivered. Customer happy.",
            ),
        ),
    )
}

package com.example.hackchaton_2026_crm.data

import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class LeadRepository(private val dao: LeadDao) {
    fun observeLeads(): Flow<List<Lead>> = dao.observeAll()

    fun observeLead(id: Long): Flow<Lead?> = dao.observe(id)

    fun observeEntries(leadId: Long): Flow<List<FollowUpEntry>> = dao.observeEntries(leadId)

    fun observeCapturedThisWeek(today: LocalDate = LocalDate.now(SeedData.ZONE)): Flow<Int> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return dao.observeCreatedSince(monday.atStartOfDay(SeedData.ZONE).toInstant())
    }

    fun observeMetrics(): Flow<List<CaptureMetric>> = dao.observeMetrics()

    suspend fun getLead(id: Long): Lead? = dao.get(id)

    suspend fun dueOnOrBefore(today: LocalDate): List<Lead> = dao.dueOnOrBefore(today)

    suspend fun findByMobile(mobile: String): Lead? = dao.findByMobile(mobile)

    suspend fun findByIc(icNumber: String): Lead? = dao.findByIc(icNumber)

    suspend fun latestEntry(leadId: Long): FollowUpEntry? = dao.latestEntry(leadId)

    suspend fun createLead(lead: Lead, firstEntry: FollowUpEntry?): Long =
        dao.insertLeadWithEntry(lead, firstEntry)

    suspend fun updateLead(lead: Lead) = dao.update(lead.copy(updatedAt = Instant.now()))

    suspend fun deleteLead(id: Long) = dao.delete(id)

    suspend fun logWhatsApp(entry: FollowUpEntry) = dao.logWhatsApp(entry)

    suspend fun scanHealth(today: LocalDate): List<HealthIssue> =
        DataHealth.scan(dao.allLeads(), dao.allEntries(), today)

    suspend fun applyFix(fix: HealthFix) {
        when (fix) {
            is HealthFix.MergeCustomers -> {
                val keeper = dao.get(fix.keeperId) ?: return
                val others = fix.dropIds.mapNotNull { dao.get(it) }
                if (others.isEmpty()) return
                dao.mergeLeads(DataHealth.merge(keeper, others).copy(updatedAt = Instant.now()), others.map { it.id })
            }
            is HealthFix.RemoveEntries -> dao.deleteEntries(fix.entryIds)
        }
    }

    suspend fun addEntry(entry: FollowUpEntry) = dao.insertEntry(entry)

    /** Saves a follow-up log entry and the lead's new stage / next step together. */
    suspend fun addFollowUp(lead: Lead, entry: FollowUpEntry) =
        dao.updateLeadWithEntry(lead.copy(updatedAt = Instant.now()), entry)

    /**
     * Keeps a scanned licence's type, classes and expiry; also fills the IC number if the customer had none.
     * [typeFromPhoto]: the photo showed the type, so it stays locked until the next scan.
     */
    suspend fun saveLicence(
        lead: Lead,
        type: LicenceType?,
        typeFromPhoto: Boolean,
        classes: List<String>,
        validTo: LocalDate?,
        icNumber: String? = null,
    ) = updateLead(
        lead.copy(
            licenceType = type?.name,
            licenceTypeFromPhoto = typeFromPhoto,
            licenceClasses = classes.joinToString(" ").ifBlank { null },
            licenceValidTo = validTo,
            icNumber = lead.icNumber ?: icNumber?.takeIf { it.isNotBlank() },
        ),
    )

    /**
     * Books a test drive or a vehicle booking: moves the customer to that stage, sets the date as the
     * next step and logs it with the licence that was checked. Refuses unless [LicenceRules] allows it.
     */
    suspend fun book(lead: Lead, kind: BookingKind, date: LocalDate, time: String, today: LocalDate): Boolean {
        val type = LicenceType.fromName(lead.licenceType)
        val classes = LicenceRules.parseClasses(lead.licenceClasses)
        if (!LicenceRules.check(type, classes, lead.licenceValidTo, today).allowed) return false
        val action = "${kind.label} at $time"
        addFollowUp(
            lead.copy(salesStage = kind.stage, nextFollowUpAt = date, nextAction = action),
            FollowUpEntry(
                leadId = lead.id,
                date = today,
                summary = "${kind.label} booked for ${bookingDay.format(date)}, $time. " +
                    "Licence checked: ${LicenceRules.summary(type, classes, lead.licenceValidTo)}" +
                    (if (lead.licenceTypeLocked) "." else " (type picked by the salesperson; the photo couldn't tell)."),
                nextAction = action,
                nextDate = date,
            ),
        )
        return true
    }

    private val bookingDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    suspend fun recordMetric(metric: CaptureMetric) = dao.insertMetric(metric)
}

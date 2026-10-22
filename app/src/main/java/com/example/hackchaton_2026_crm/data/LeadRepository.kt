package com.example.hackchaton_2026_crm.data

import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

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

    suspend fun findByMobile(mobile: String): Lead? = dao.findByMobile(mobile)

    suspend fun latestEntry(leadId: Long): FollowUpEntry? = dao.latestEntry(leadId)

    suspend fun createLead(lead: Lead, firstEntry: FollowUpEntry?): Long =
        dao.insertLeadWithEntry(lead, firstEntry)

    suspend fun updateLead(lead: Lead) = dao.update(lead.copy(updatedAt = Instant.now()))

    suspend fun addEntry(entry: FollowUpEntry) = dao.insertEntry(entry)

    /** Saves a follow-up log entry and the lead's new stage / next step together. */
    suspend fun addFollowUp(lead: Lead, entry: FollowUpEntry) =
        dao.updateLeadWithEntry(lead.copy(updatedAt = Instant.now()), entry)

    suspend fun recordMetric(metric: CaptureMetric) = dao.insertMetric(metric)
}

package com.example.hackchaton_2026_crm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface LeadDao {
    @Query("SELECT * FROM leads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Lead>>

    @Query("SELECT * FROM leads WHERE id = :id")
    fun observe(id: Long): Flow<Lead?>

    @Query("SELECT * FROM leads WHERE id = :id")
    suspend fun get(id: Long): Lead?

    @Query("SELECT * FROM leads WHERE mobile = :mobile LIMIT 1")
    suspend fun findByMobile(mobile: String): Lead?

    /** Dates are stored as ISO strings, so string comparison orders them correctly. */
    @Query("SELECT * FROM leads WHERE nextFollowUpAt IS NOT NULL AND nextFollowUpAt <= :today")
    suspend fun dueOnOrBefore(today: LocalDate): List<Lead>

    @Query("SELECT * FROM leads WHERE icNumber = :icNumber LIMIT 1")
    suspend fun findByIc(icNumber: String): Lead?

    @Query("SELECT COUNT(*) FROM leads")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM leads WHERE createdAt >= :since")
    fun observeCreatedSince(since: Instant): Flow<Int>

    @Insert
    suspend fun insert(lead: Lead): Long

    @Update
    suspend fun update(lead: Lead)

    /** Follow-up entries go with it (ForeignKey.CASCADE). */
    @Query("DELETE FROM leads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM follow_up_entries WHERE leadId = :leadId ORDER BY date DESC, createdAt DESC")
    fun observeEntries(leadId: Long): Flow<List<FollowUpEntry>>

    @Query(
        """SELECT e.* FROM follow_up_entries e WHERE e.leadId = :leadId
           ORDER BY e.date DESC, e.createdAt DESC LIMIT 1"""
    )
    suspend fun latestEntry(leadId: Long): FollowUpEntry?

    @Insert
    suspend fun insertEntry(entry: FollowUpEntry): Long

    @Transaction
    suspend fun insertLeadWithEntry(lead: Lead, entry: FollowUpEntry?): Long {
        val id = insert(lead)
        if (entry != null) insertEntry(entry.copy(leadId = id))
        return id
    }

    @Transaction
    suspend fun updateLeadWithEntry(lead: Lead, entry: FollowUpEntry) {
        update(lead)
        insertEntry(entry)
    }

    @Update
    suspend fun updateEntry(entry: FollowUpEntry)

    @Query("DELETE FROM follow_up_entries WHERE id IN (:ids)")
    suspend fun deleteEntries(ids: List<Long>)

    @Query("UPDATE follow_up_entries SET leadId = :to WHERE leadId = :from")
    suspend fun moveEntries(from: Long, to: Long)

    @Query("SELECT * FROM follow_up_entries WHERE leadId = :leadId AND date = :date ORDER BY createdAt DESC, id DESC")
    suspend fun entriesOn(leadId: Long, date: LocalDate): List<FollowUpEntry>

    /**
     * Logs "WhatsApp sent" once per customer per day: opening WhatsApp again (another language,
     * another try) replaces today's entry instead of stacking a second one. Also tidies old duplicates.
     */
    @Transaction
    suspend fun logWhatsApp(entry: FollowUpEntry) {
        val today = entriesOn(entry.leadId, entry.date).filter { it.summary.startsWith(FollowUpEntry.WHATSAPP_PREFIX) }
        if (today.isEmpty()) {
            insertEntry(entry)
            return
        }
        updateEntry(entry.copy(id = today.first().id))
        deleteEntries(today.drop(1).map { it.id })
    }

    @Query("SELECT * FROM leads")
    suspend fun allLeads(): List<Lead>

    @Query("SELECT * FROM follow_up_entries")
    suspend fun allEntries(): List<FollowUpEntry>

    /** Saves the merged customer, moves the others' history onto it, then deletes the others. */
    @Transaction
    suspend fun mergeLeads(merged: Lead, dropIds: List<Long>) {
        update(merged)
        dropIds.forEach {
            moveEntries(it, merged.id)
            delete(it)
        }
    }

    @Insert
    suspend fun insertMetric(metric: CaptureMetric)

    @Query("SELECT * FROM capture_metrics")
    fun observeMetrics(): Flow<List<CaptureMetric>>
}

package com.example.hackchaton_2026_crm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

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

    @Query("SELECT COUNT(*) FROM leads")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM leads WHERE createdAt >= :since")
    fun observeCreatedSince(since: Instant): Flow<Int>

    @Insert
    suspend fun insert(lead: Lead): Long

    @Update
    suspend fun update(lead: Lead)

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

    @Insert
    suspend fun insertMetric(metric: CaptureMetric)

    @Query("SELECT * FROM capture_metrics")
    fun observeMetrics(): Flow<List<CaptureMetric>>
}

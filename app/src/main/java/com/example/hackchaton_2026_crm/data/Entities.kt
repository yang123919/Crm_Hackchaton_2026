package com.example.hackchaton_2026_crm.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** Mirrors the SuiteCRM Lead/Opportunity fields used by CS Group. */
@Entity(tableName = "leads", indices = [Index("mobile"), Index("icNumber")])
data class Lead(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstName: String,
    val lastName: String = "",
    /** Normalised as 60XXXXXXXXX. */
    val mobile: String,
    val email: String? = null,
    /** Malaysian IC, formatted YYMMDD-PB-####. Shown masked; the scan photo is never kept. */
    val icNumber: String? = null,
    val address: String? = null,
    /** "Male" or "Female". */
    val gender: String? = null,
    val product: String,
    val outlet: String,
    val status: String = LeadStatus.NEW,
    val salesStage: String = ReferenceLists.DEFAULT_STAGE,
    val expectedCloseDate: LocalDate? = null,
    val budgetRm: Long? = null,
    val tradeInUsedCar: Boolean = false,
    val tradeInBrand: String? = null,
    val tradeInModel: String? = null,
    val tradeInYear: Int? = null,
    val nextFollowUpAt: LocalDate? = null,
    val nextAction: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    val maskedIc: String? get() = icNumber?.let(com.example.hackchaton_2026_crm.ocr.IcInfo::maskIc)
}

object LeadStatus {
    const val NEW = "New"
    const val CONVERTED = "Converted"
}

@Entity(
    tableName = "follow_up_entries",
    foreignKeys = [ForeignKey(
        entity = Lead::class,
        parentColumns = ["id"],
        childColumns = ["leadId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("leadId")],
)
data class FollowUpEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val leadId: Long,
    val date: LocalDate,
    val summary: String,
    val nextAction: String = "",
    val nextDate: LocalDate? = null,
    val rawTranscript: String = "",
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        /** Summaries logged by the WhatsApp sheet start with this; used to keep one such entry per day. */
        const val WHATSAPP_PREFIX = "WhatsApp sent:"
    }
}

@Entity(tableName = "capture_metrics")
data class CaptureMetric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Instant,
    val savedAt: Instant,
    val type: String,
) {
    companion object {
        const val TYPE_NEW_LEAD = "new_lead"
        const val TYPE_FOLLOW_UP = "follow_up"
    }
}

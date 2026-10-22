package com.example.hackchaton_2026_crm.ui

import com.example.hackchaton_2026_crm.data.SeedData
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Format {
    private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val longDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val rm = NumberFormat.getIntegerInstance(Locale.US)

    fun today(): LocalDate = LocalDate.now(SeedData.ZONE)

    /** "Tue 27 Oct" */
    fun shortDate(date: LocalDate): String = shortDate.format(date)

    /** "22 Oct 2026" */
    fun longDate(date: LocalDate): String = longDate.format(date)

    fun rm(amount: Long): String = "RM ${rm.format(amount)}"

    /** "2 days late", "Today", "Tomorrow", "in 3 days" */
    fun dueLabel(date: LocalDate, today: LocalDate = today()): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days < -1 -> "${-days} days late"
            days == -1L -> "1 day late"
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            else -> "in $days days"
        }
    }

    /** 60123456789 → +60 12-345 6789 */
    fun phone(mobile: String): String {
        if (!mobile.startsWith("60") || mobile.length < 11) return mobile
        val local = mobile.drop(2)
        val prefixLen = if (local.startsWith("11")) 3 else 2
        val prefix = local.take(prefixLen)
        val rest = local.drop(prefixLen)
        return "+60 $prefix-${rest.dropLast(4)} ${rest.takeLast(4)}"
    }

    fun greeting(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}

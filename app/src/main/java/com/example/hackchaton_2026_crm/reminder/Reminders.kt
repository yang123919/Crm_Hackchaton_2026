package com.example.hackchaton_2026_crm.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.hackchaton_2026_crm.MainActivity
import com.example.hackchaton_2026_crm.QuickLeadApp
import com.example.hackchaton_2026_crm.R
import com.example.hackchaton_2026_crm.data.Lead
import com.example.hackchaton_2026_crm.data.ReferenceLists
import com.example.hackchaton_2026_crm.data.SeedData
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Daily "never forget a follow-up" notification (nice-to-have 11). */
object Reminders {
    private const val CHANNEL = "follow_ups"
    private const val WORK = "daily_follow_up_reminder"
    private const val NOTIFICATION_ID = 1
    const val KEY_ALWAYS = "always"
    private val AT: LocalTime = LocalTime.of(9, 0)

    /** Every day at 09:00 Malaysia time. Safe to call on every app start. */
    fun schedule(context: Context) {
        val now = ZonedDateTime.now(SeedData.ZONE)
        var next = now.with(AT)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** "Send test reminder" in Settings: notifies now, even when nothing is due. */
    fun sendNow(context: Context) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf(KEY_ALWAYS to true)).build(),
        )
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Title and body for the leads due on or before [today]; null when nothing is due. */
    fun message(due: List<Lead>, today: LocalDate): Pair<String, String>? {
        val open = due.filter { it.salesStage !in ReferenceLists.closedStages && it.nextFollowUpAt != null }
        if (open.isEmpty()) return null
        val overdue = open.count { it.nextFollowUpAt!! < today }
        val dueToday = open.size - overdue
        val title = listOfNotNull(
            overdue.takeIf { it > 0 }?.let { "$it overdue" },
            dueToday.takeIf { it > 0 }?.let { "$it due today" },
        ).joinToString(" · ")
        val names = open.sortedBy { it.nextFollowUpAt }.take(3).joinToString(", ") { it.fullName } +
            if (open.size > 3) " and ${open.size - 3} more" else ""
        return title to "Follow up with $names"
    }

    fun notify(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Follow-up reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_quicklead)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as QuickLeadApp
        val today = LocalDate.now(SeedData.ZONE)
        val due = app.container.leadRepository.dueOnOrBefore(today)
        val message = Reminders.message(due, today)
            ?: if (inputData.getBoolean(Reminders.KEY_ALWAYS, false)) "All caught up" to "No follow-ups due today." else null
        message?.let { (title, text) -> Reminders.notify(applicationContext, title, text) }
        return Result.success()
    }
}

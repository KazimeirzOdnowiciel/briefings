package dev.lukat.briefing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Fires the briefing check on the minute.
 *
 * WorkManager alone drifts: it is deferrable by design, and on a phone left idle
 * overnight the 08:45 check can slide by an hour. An exact alarm wakes the device,
 * then hands off to the same worker that does the fetch — so the fetch logic stays
 * in one place and only the timing changes.
 */
object Alarms {
    private const val REQUEST = 4711
    val RUN_AT: LocalTime = LocalTime.of(8, 45)

    fun nextRunAt(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var next = now.with(RUN_AT).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next
    }

    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, BriefingAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val at = nextRunAt()
        val millis = at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val exact = manager != null &&
            (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms())
        if (exact) {
            runCatching {
                manager!!.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            }
                .onFailure { fallback(context, millis) }
        } else {
            fallback(context, millis)
        }
    }

    /** Inexact path: a WorkManager job that still runs today, just not to the second. */
    private fun fallback(context: Context, millis: Long) {
        val delay = (millis - System.currentTimeMillis()).coerceAtLeast(60_000)
        val request = OneTimeWorkRequestBuilder<BriefingWorker>()
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("daily-briefing", androidx.work.ExistingWorkPolicy.REPLACE, request)
    }

    fun describeNextRun(): String {
        val next = nextRunAt()
        val minutes = Duration.between(LocalDateTime.now(), next).toMinutes()
        return "next check ${next.toLocalTime()} (in ${minutes / 60}h ${minutes % 60}m)"
    }
}

class BriefingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        BriefingWorker.schedule(context)          // book tomorrow first, never lose the chain
        val work = OneTimeWorkRequestBuilder<BriefingWorker>().build()
        WorkManager.getInstance(context).enqueue(work)
    }
}

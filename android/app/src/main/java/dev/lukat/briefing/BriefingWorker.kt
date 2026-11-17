package dev.lukat.briefing

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Wakes a little after the 08:00 publish, fetches the edition, stores it and says so —
 * even with the app closed. Reschedules itself for the next morning.
 */
class BriefingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        Notify.ensureChannels(context)
        val result = try {
            val raw = loadFeedRaw()
            val feed = parseFeed(raw)
            FeedStore.save(context, raw)
            EditionStore.save(context, raw)
            runCatching { BriefingWidget().updateAll(context) }

            val prefs = context.getSharedPreferences("briefing", Context.MODE_PRIVATE)
            val lastNotified = prefs.getString("notified_date", null)
            // already read it before the alarm? then the notification would be noise
            if (feed.date != lastNotified && !Seen.isSeen(context, feed.date)) {
                val stories = feed.sections.sumOf { section -> section.items.count { it.type == "story" } }
                Notify.post(
                    context, Notify.BRIEFING,
                    feed.title ?: "Today's briefing",
                    "${feed.date} · $stories stories · ${feed.readMinutes ?: 0} min read"
                )
                val notable = feed.sections
                    .flatMap { it.items }
                    .filter { it.type == "score" && (it.flag != null || (it.homeScore ?: 0) + (it.awayScore ?: 0) >= 240) }
                if (notable.isNotEmpty()) {
                    val first = notable.first()
                    Notify.post(
                        context, Notify.SPORT,
                        "Sport: ${notable.size} game(s) worth a look",
                        "${first.home} ${first.homeScore}–${first.awayScore} ${first.away}" +
                            (first.flag?.let { " · $it" } ?: "") +
                            (first.note?.let { " · $it" } ?: "")
                    )
                }
                prefs.edit().putString("notified_date", feed.date).apply()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
        schedule(context)
        return result
    }

    companion object {
        private const val WORK = "daily-briefing"

        /** 08:45 local — after the pipeline publishes, before most people are out the door. */
        private val RUN_AT: LocalTime = LocalTime.of(8, 45)

        fun nextRunDelayMillis(now: LocalDateTime = LocalDateTime.now()): Long {
            var next = now.with(RUN_AT)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next).toMillis()
        }

        fun schedule(context: Context) {
            Alarms.schedule(context)
        }

        /** Kept for the old call sites; the alarm path is the real scheduler now. */
        fun scheduleLegacyWorkManager(context: Context) {
            val request = OneTimeWorkRequestBuilder<BriefingWorker>()
                .setInitialDelay(nextRunDelayMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("scheduled" to true))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

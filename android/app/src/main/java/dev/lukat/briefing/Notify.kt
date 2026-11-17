package dev.lukat.briefing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Two channels on purpose: muting sport should not mean muting the briefing.
 * Android keeps the user's choice per channel, so this is the only way to give
 * them a per-section switch without building a settings screen.
 */
object Notify {
    const val BRIEFING = "briefing"
    const val SPORT = "sport"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            Triple(BRIEFING, "Daily briefing", "A new edition is ready each morning"),
            Triple(SPORT, "Sport", "Upsets, overtime and big performances only"),
        ).forEach { (id, name, description) ->
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                this.description = description
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun post(context: Context, channel: String, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, channel.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(channel.hashCode(), notification)
    }
}

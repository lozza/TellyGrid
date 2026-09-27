package io.github.lozza.tellygrid.playback

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.lozza.tellygrid.MainActivity
import io.github.lozza.tellygrid.R
import io.github.lozza.tellygrid.data.GuidePreferences
import io.github.lozza.tellygrid.data.GuideReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Device-local alert delivery for guide reminders. Alarms use a small inexact
 * window, so Android 12+ never needs the special exact-alarm permission.
 */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: GuideReminder, notificationsAllowed: Boolean) {
        if (!notificationsAllowed || reminder.startsAtEpochMillis <= System.currentTimeMillis()) return
        val start = reminder.startsAtEpochMillis
        alarms.setWindow(AlarmManager.RTC_WAKEUP, start, REMINDER_WINDOW_MILLIS, pendingIntent(reminder))
    }

    fun cancel(reminder: GuideReminder) {
        pendingIntent(reminder).also { intent ->
            alarms.cancel(intent)
            intent.cancel()
        }
    }

    /** Recreates pending future alerts after app launch, reboot, or a permission change. */
    fun reconcile(reminders: Set<GuideReminder>, notificationsAllowed: Boolean) {
        if (!notificationsAllowed) return
        reminders.forEach { schedule(it, notificationsAllowed) }
    }

    private fun pendingIntent(reminder: GuideReminder): PendingIntent = PendingIntent.getBroadcast(
        context,
        reminderRequestCode(reminder),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXTRA_CHANNEL, reminder.channelName.ifBlank { reminder.channelId })
            .putExtra(EXTRA_TITLE, reminder.programmeTitle.ifBlank { "Programme starting" }),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val ACTION_REMINDER = "io.github.lozza.tellygrid.action.GUIDE_REMINDER"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_TITLE = "title"
        private const val CHANNEL_ID = "programme_reminders"
        private const val REMINDER_WINDOW_MILLIS = 5 * 60 * 1000L

        internal fun reminderRequestCode(reminder: GuideReminder): Int =
            31 * (31 * reminder.channelId.hashCode() + reminder.programmeId.hashCode()) +
                reminder.startsAtEpochMillis.hashCode()

        internal fun notificationsAllowed(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        internal fun showNotification(context: Context, channel: String, title: String) {
            if (!notificationsAllowed(context)) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Programme reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Alerts for programmes saved in TellyGrid"
                },
            )
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify(
                (channel + title).hashCode(),
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.app_icon)
                    .setContentTitle(title)
                    .setContentText("Starting now on $channel")
                    .setContentIntent(openApp)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_REMINDER) return
            showNotification(
                context,
                intent.getStringExtra(EXTRA_CHANNEL).orEmpty(),
                intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            )
        }
    }

    class ReconcileReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val reminders = GuidePreferences(context.applicationContext).settings.first().reminders
                    ReminderScheduler(context.applicationContext).reconcile(
                        reminders,
                        notificationsAllowed(context.applicationContext),
                    )
                } finally {
                    pending.finish()
                }
            }
        }
    }
}

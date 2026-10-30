package com.josiglo.agenda.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.josiglo.agenda.MainActivity
import com.josiglo.agenda.R
import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Kind
import com.josiglo.agenda.data.Store
import com.josiglo.agenda.settings.Settings

/**
 * Keeps the notifications in line with the items. [sync] is safe to call any
 * time: it posts what is due, removes what isn't, and sets one alarm for the
 * next notification or the next day start, whichever comes first. Store
 * changes, boots, time changes and the alarm itself all call it.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    const val EXTRA_ITEM = "item"

    @Synchronized
    fun sync(context: Context, now: Long = System.currentTimeMillis()) {
        val store = Store.get(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        createChannel(context, manager)
        val open = store.items.value.filter { it.remindAt != null && !it.done && !it.isSubtask && it.kind != Kind.INBOX }
        val due = open.filter { it.remindAt!! <= now }
        val dueIds = due.map { it.id.toInt() }.toSet()
        manager.activeNotifications.filter { it.id !in dueIds }.forEach { manager.cancel(it.id) }
        if (canPost(context)) {
            due.forEach { item ->
                manager.notify(item.id.toInt(), build(context, item, silent = item.alertedAt == item.remindAt))
                store.markAlerted(item.id, item.remindAt!!)
            }
        }
        val nextReminder = open.mapNotNull { it.remindAt }.filter { it > now }.minOrNull()
        val clock = Settings.get(context).preferences.value.clock()
        val nextDay = clock.end(clock.today(now))
        setAlarm(context, minOf(nextReminder ?: nextDay, nextDay))
    }

    fun canPost(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canScheduleExact(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun setAlarm(context: Context, at: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canScheduleExact(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun createChannel(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    // Pinned so it can't be swiped away on the lock screen. Where Android lets
    // it be swiped away anyway, the delete intent posts it again.
    private fun build(context: Context, item: Item, silent: Boolean): Notification {
        val id = item.id.toInt()
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_ITEM, item.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context, id,
            Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_DONE).putExtra(EXTRA_ITEM, item.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissed = PendingIntent.getBroadcast(
            context, id,
            Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_DISMISSED).putExtra(EXTRA_ITEM, item.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val reschedule = PendingIntent.getActivity(
            context, id,
            Intent(context, RescheduleActivity::class.java).putExtra(EXTRA_ITEM, item.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = listOfNotNull(item.note, item.place).joinToString(" · ").ifEmpty { null }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.title)
            .setContentText(text)
            .setStyle(text?.let { NotificationCompat.BigTextStyle().bigText(it) })
            .setWhen(item.remindAt ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(silent)
            .setContentIntent(open)
            .setDeleteIntent(dismissed)
            .addAction(0, context.getString(R.string.done), done)
            .addAction(0, context.getString(R.string.reschedule), reschedule)
            .build()
    }
}

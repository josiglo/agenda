package com.josiglo.reminders.notify

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.josiglo.reminders.backup.BackupJobService
import com.josiglo.reminders.data.Store
import com.josiglo.reminders.settings.Settings

/**
 * Sets the alarm again after a restart, an app update or a clock change, and
 * posts the notifications that are still open.
 */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> {
                Store.get(context).purge(Settings.get(context).preferences.value.clock())
                BackupJobService.ensureScheduled(context)
            }
        }
    }
}

package com.josiglo.agenda.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings as AndroidSettings
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.josiglo.agenda.BuildConfig
import com.josiglo.agenda.R
import com.josiglo.agenda.backup.Backup
import com.josiglo.agenda.calendar.CalendarInfo
import com.josiglo.agenda.calendar.Calendars
import com.josiglo.agenda.data.Store
import com.josiglo.agenda.notify.Reminders
import com.josiglo.agenda.settings.Settings
import com.josiglo.agenda.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onPlaces: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings.get(context) }
    val preferences by settings.preferences.collectAsStateWithLifecycle()
    // Bumped on resume and after permission prompts, so permission states and calendars reload.
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose {}
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks++ }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        checks++
        Reminders.sync(context)
    }
    val calendars by produceState<List<CalendarInfo>?>(null, checks) {
        value = withContext(Dispatchers.IO) { if (Calendars.canRead(context)) Calendars.list(context) else null }
    }
    val canPost = remember(checks) { Reminders.canPost(context) }
    val canExact = remember(checks) { Reminders.canScheduleExact(context) }
    var editingTime by remember { mutableStateOf<TimeSetting?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionLabel(stringResource(R.string.calendars_on_today))
            Group {
                val list = calendars
                when {
                    list == null -> SettingRow(
                        stringResource(R.string.calendar_access),
                        stringResource(R.string.calendar_access_summary),
                        onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
                    )
                    list.isEmpty() -> SettingRow(stringResource(R.string.no_calendars), null)
                    else -> list.forEachIndexed { i, calendar ->
                        if (i > 0) GroupDivider()
                        val on = calendar.id in preferences.calendars
                        val toggle = {
                            settings.update { p -> p.copy(calendars = if (on) p.calendars - calendar.id else p.calendars + calendar.id) }
                        }
                        SettingRow(calendar.name, calendar.account.takeIf { it != calendar.name }, onClick = toggle) {
                            Switch(checked = on, onCheckedChange = { toggle() })
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.notifications))
            Group {
                if (!canPost && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SettingRow(
                        stringResource(R.string.notification_access),
                        stringResource(R.string.notification_access_summary),
                        onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                    GroupDivider()
                }
                SettingRow(
                    stringResource(R.string.exact_alarms),
                    stringResource(if (canExact) R.string.exact_alarms_allowed else R.string.exact_alarms_denied),
                    onClick = if (!canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                            )
                        }
                    } else {
                        null
                    },
                )
                GroupDivider()
                SettingRow(
                    stringResource(R.string.morning),
                    stringResource(R.string.morning_summary, Format.time(preferences.morning)),
                    onClick = { editingTime = TimeSetting.MORNING },
                )
                GroupDivider()
                SettingRow(
                    stringResource(R.string.evening),
                    stringResource(R.string.evening_summary, Format.time(preferences.evening)),
                    onClick = { editingTime = TimeSetting.EVENING },
                )
                GroupDivider()
                SettingRow(
                    stringResource(R.string.day_start),
                    stringResource(R.string.day_start_summary, Format.time(preferences.dayStart)),
                    onClick = { editingTime = TimeSetting.DAY_START },
                )
            }

            SectionLabel(stringResource(R.string.places))
            Group {
                val places = Store.get(context).places()
                SettingRow(
                    stringResource(R.string.places),
                    if (places.isEmpty()) stringResource(R.string.places_none) else places.joinToString(),
                    onClick = onPlaces,
                )
            }

            BackupSection()

            SectionLabel(stringResource(R.string.look))
            Group {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = preferences.theme == mode,
                                onClick = { settings.update { it.copy(theme = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                                label = { Text(stringResource(mode.label), maxLines = 1) },
                            )
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GroupDivider()
                    val toggle = { settings.update { it.copy(wallpaperColors = !it.wallpaperColors) } }
                    SettingRow(stringResource(R.string.wallpaper_colors), stringResource(R.string.wallpaper_colors_summary), onClick = toggle) {
                        Switch(checked = preferences.wallpaperColors, onCheckedChange = { toggle() })
                    }
                }
            }

            SectionLabel(stringResource(R.string.about))
            Group { SettingRow(stringResource(R.string.version), BuildConfig.VERSION_NAME) }
            Spacer(Modifier.heightIn(min = 24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    editingTime?.let { which ->
        TimeOfDayPicker(
            initial = which.get(preferences),
            onPicked = { time ->
                settings.update { which.set(it, time) }
                // A new day start moves the reset, so the alarm moves with it.
                Reminders.sync(context)
                editingTime = null
            },
            onDismiss = { editingTime = null },
        )
    }
}

private enum class TimeSetting {
    MORNING,
    EVENING,
    DAY_START;

    fun get(p: com.josiglo.agenda.settings.Preferences): LocalTime = when (this) {
        MORNING -> p.morning
        EVENING -> p.evening
        DAY_START -> p.dayStart
    }

    fun set(p: com.josiglo.agenda.settings.Preferences, time: LocalTime) = when (this) {
        MORNING -> p.copy(morning = time)
        EVENING -> p.copy(evening = time)
        DAY_START -> p.copy(dayStart = time)
    }
}

@Composable
fun SettingRow(title: String, summary: String?, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** Picks the folder for the daily backup, backs up right away and restores a backup file. */
@Composable
private fun BackupSection() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // Bumped after every change, so the folder summary reloads.
    var changes by remember { mutableIntStateOf(0) }
    var choosing by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf<Uri?>(null) }
    val summary by produceState<String?>(null, changes) {
        value = withContext(Dispatchers.IO) { folderSummary(context) }
    }

    // A new folder only needs a backup if the job, which starts with it, hasn't written one.
    fun backUpNow(onlyIfDue: Boolean = false) {
        scope.launch {
            val done = withContext(Dispatchers.IO) {
                runCatching { if (onlyIfDue) Backup.backUpIfDue(context) else Backup.backUpToFolder(context) }
                    .onFailure { Log.w("Settings", "backup failed", it) }
                    .isSuccess
            }
            Toast.makeText(context, if (done) R.string.backup_done else R.string.backup_failed, Toast.LENGTH_SHORT).show()
            changes++
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        scope.launch {
            withContext(Dispatchers.IO) { Backup.setFolder(context, tree) }
            backUpNow(onlyIfDue = true)
        }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file ->
        restoring = file
    }

    SectionLabel(stringResource(R.string.backup))
    Group {
        SettingRow(
            stringResource(R.string.backup_folder),
            summary ?: stringResource(R.string.backup_folder_none),
            onClick = { if (summary == null) folderPicker.launch(null) else choosing = true },
        )
        GroupDivider()
        SettingRow(
            stringResource(R.string.backup_now),
            pluralStringResource(R.plurals.backup_now_summary, Backup.KEEP, Backup.KEEP),
            onClick = { if (summary == null) folderPicker.launch(null) else backUpNow() },
        )
        GroupDivider()
        SettingRow(
            stringResource(R.string.backup_restore),
            stringResource(R.string.backup_restore_summary),
            onClick = { restorePicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
        )
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.backup_folder)) },
            confirmButton = {
                TextButton(onClick = {
                    choosing = false
                    folderPicker.launch(null)
                }) { Text(stringResource(R.string.backup_change_folder)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    choosing = false
                    scope.launch {
                        withContext(Dispatchers.IO) { Backup.setFolder(context, null) }
                        changes++
                    }
                }) { Text(stringResource(R.string.backup_stop)) }
            },
        )
    }
    restoring?.let { file ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_message)) },
            confirmButton = {
                TextButton(onClick = {
                    restoring = null
                    scope.launch {
                        val done = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openInputStream(file)!!.use { Backup.restore(context, it) }
                            }.onFailure { Log.w("Settings", "restore failed", it) }.isSuccess
                        }
                        Toast.makeText(
                            context,
                            if (done) R.string.backup_restored else R.string.backup_restore_failed,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }) { Text(stringResource(R.string.backup_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { restoring = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** The folder name and when the last backup ran, or null without a folder. */
private fun folderSummary(context: Context): String? {
    val tree = Backup.folder(context) ?: return null
    val name = runCatching {
        context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: tree.lastPathSegment
    val last = Backup.lastBackup(context)
    return if (last == 0L) {
        context.getString(R.string.backup_folder_never, name)
    } else {
        context.getString(R.string.backup_folder_last, name, DateUtils.getRelativeTimeSpanString(context, last, true))
    }
}

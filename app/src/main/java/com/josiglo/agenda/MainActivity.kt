package com.josiglo.agenda

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.josiglo.agenda.backup.BackupJobService
import com.josiglo.agenda.data.Store
import com.josiglo.agenda.notify.Reminders
import com.josiglo.agenda.settings.Settings
import com.josiglo.agenda.ui.AgendaApp
import com.josiglo.agenda.ui.AgendaTheme

class MainActivity : ComponentActivity() {
    private var openItem by mutableStateOf<Long?>(null)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Reminders.sync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BackupJobService.ensureScheduled(this)
        if (savedInstanceState == null) {
            openItem = itemOf(intent)
            if (!Reminders.canPost(this)) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            AgendaTheme {
                AgendaApp(openItem, onOpenHandled = { openItem = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openItem = itemOf(intent)
    }

    override fun onResume() {
        super.onResume()
        // The day may have changed while the app was in the background.
        Store.get(this).purge(Settings.get(this).preferences.value.clock())
    }

    private fun itemOf(intent: Intent?): Long? =
        intent?.getLongExtra(Reminders.EXTRA_ITEM, 0)?.takeIf { it != 0L }
}

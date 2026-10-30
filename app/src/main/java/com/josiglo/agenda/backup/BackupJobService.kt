package com.josiglo.agenda.backup

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Writes a backup to the folder once a day, see [Backup]. */
class BackupJobService : JobService() {
    companion object {
        private const val TAG = "BackupJob"
        private const val JOB_ID = 1
        private const val INTERVAL_MS = 24 * 60 * 60_000L

        fun schedule(context: Context) {
            context.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, BackupJobService::class.java))
                    .setPeriodic(INTERVAL_MS)
                    .setPersisted(true)
                    .build(),
            )
        }

        /** Schedules the job if a folder is set and it isn't scheduled, like after an update. */
        fun ensureScheduled(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (Backup.folder(context) != null && scheduler.getPendingJob(JOB_ID) == null) schedule(context)
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            val failed = try {
                Backup.backUpIfDue(this@BackupJobService)
                false
            } catch (e: Exception) {
                // The folder may be gone or its permission revoked.
                Log.w(TAG, "backup failed", e)
                true
            }
            jobFinished(params, failed)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.cancel()
        return true
    }
}

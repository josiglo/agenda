package com.josiglo.agenda.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.josiglo.agenda.data.Store
import com.josiglo.agenda.settings.Settings
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The settings and all items as one file, written to a folder the user
 * picked once a day. All calls block, so use them off the main thread.
 */
object Backup {
    private const val TAG = "Backup"
    private const val FOLDER_KEY = "folder"
    private const val LAST_KEY = "last"
    private const val FILE_PREFIX = "agenda-backup-"
    private const val FILE_SUFFIX = ".json"
    private const val MIME = "application/json"
    const val KEEP = 3
    private const val DUE_MS = 20 * 60 * 60_000L

    // Kept apart from the settings, since the folder permission belongs to
    // this phone and a restore must not change it.
    private fun prefs(context: Context) = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun folder(context: Context): Uri? = prefs(context).getString(FOLDER_KEY, null)?.toUri()

    fun lastBackup(context: Context): Long = prefs(context).getLong(LAST_KEY, 0)

    /** Turns the daily backup on for [tree], or off for null. */
    fun setFolder(context: Context, tree: Uri?) {
        prefs(context).edit(commit = true) {
            putString(FOLDER_KEY, tree?.toString())
            remove(LAST_KEY)
        }
        if (tree != null) BackupJobService.schedule(context) else BackupJobService.cancel(context)
    }

    /**
     * Writes a backup unless one was written in the last [DUE_MS]. The job
     * runs right after it is scheduled, also when the app reschedules it on
     * start, so this keeps it to one backup a day.
     */
    @Synchronized
    fun backUpIfDue(context: Context) {
        if (System.currentTimeMillis() - lastBackup(context) >= DUE_MS) backUpToFolder(context)
    }

    /** Writes a backup to the folder and keeps the newest [KEEP]. */
    @Synchronized
    fun backUpToFolder(context: Context) {
        val tree = folder(context) ?: return
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.ROOT).format(Date()) + FILE_SUFFIX
        val file = DocumentsContract.createDocument(context.contentResolver, folder, MIME, name)
            ?: throw IllegalStateException("could not create $name")
        try {
            val data = BackupData(Settings.get(context).preferences.value, Store.get(context).items.value)
            context.contentResolver.openOutputStream(file, "wt")!!.use { it.write(BackupFormat.encode(data).toByteArray()) }
        } catch (e: Exception) {
            DocumentsContract.deleteDocument(context.contentResolver, file)
            throw e
        }
        prefs(context).edit(commit = true) { putLong(LAST_KEY, System.currentTimeMillis()) }
        backups(context, tree).sortedByDescending { it.first }.drop(KEEP).forEach { (oldName, uri) ->
            try {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (e: Exception) {
                Log.w(TAG, "could not delete $oldName", e)
            }
        }
    }

    // The backups in the folder by file name, whose date sorts them.
    private fun backups(context: Context, tree: Uri): List<Pair<String, Uri>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(1)
                    if (name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)) {
                        add(name to DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)))
                    }
                }
            }
        }.orEmpty()
    }

    /**
     * Replaces the settings and all items with the backup in [input]. Throws
     * without changing anything if [input] isn't a backup.
     */
    fun restore(context: Context, input: InputStream) {
        val data = BackupFormat.decode(input.readBytes().decodeToString())
        Store.get(context).replaceAll(data.items)
        Settings.get(context).save(data.preferences)
    }
}

package rs.plan1k.regulacija

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ContentValues
import android.content.Context
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Daily, local JSON snapshots. Never removes or modifies the user's existing data. */
object DailyBackup {
    private const val JOB_ID = 18492
    private const val PREFS = "1k_hit_daily_backup"
    private const val LAST_DAY = "last_successful_day"
    private const val PERIOD_MS = 24L * 60 * 60 * 1000

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, DailyBackupJob::class.java))
            .setPersisted(true)
            .setPeriodic(PERIOD_MS)
            .build()
        // Keep an existing periodic schedule instead of resetting it on every app launch.
        if (scheduler.allPendingJobs.none { it.id == JOB_ID }) scheduler.schedule(job)
    }

    @Synchronized
    fun backupIfNeeded(context: Context): Boolean {
        val app = context.applicationContext
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_DAY, null) == day) return false
        val json = Store(app).use { it.exportJson() }
        val name = "1K-Hit-$day.json"
        if (Build.VERSION.SDK_INT >= 29) {
            saveInDownloads(app, name, json)
        } else {
            // Scoped-storage Downloads requires API 29; use app-specific external storage on older phones.
            val dir = File(app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: app.filesDir, "1K Hit Backups")
            if (!dir.exists() && !dir.mkdirs()) error("Ne mogu da napravim backup folder")
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(json, Charsets.UTF_8)
            val target = File(dir, name)
            if (!tmp.renameTo(target)) { tmp.delete(); error("Ne mogu da sačuvam backup") }
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LAST_DAY, day).apply()
        return true
    }

    private fun saveInDownloads(context: Context, name: String, json: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        // A fresh date gets a fresh file. An existing date's file is left untouched.
        val existing = resolver.query(
            collection, arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(name, "${Environment.DIRECTORY_DOWNLOADS}/1K Hit Backups/"), null
        )?.use { cursor -> cursor.moveToFirst() } ?: false
        if (existing) return
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/1K Hit Backups/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(collection, values) ?: error("Ne mogu da napravim backup fajl")
        try {
            val out = resolver.openOutputStream(uri) ?: error("Ne mogu da otvorim backup fajl")
            out.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}

class DailyBackupJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            var retry = false
            try { DailyBackup.backupIfNeeded(applicationContext) }
            catch (_: Exception) { retry = true }
            finally { jobFinished(params, retry) }
        }.start()
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean = true
}

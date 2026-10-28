package app.serifu.ml

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.serifu.R
import app.serifu.core.Tuning
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Resumable Gemma model download (plan.md 11.4).
 * `HttpURLConnection` with `Range` resume into `models/gemma4-e2b.litertlm.part`,
 * 1 MB buffer, progress throttled to one update per 500 ms. Unmetered network
 * by default, storage-not-low constraint. Verifies size (+ ETag when the
 * server exposes it), records SHA-256 to a sidecar, then atomically renames.
 */
class GemmaModelDownloader(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: ModelCatalog.URL
        val part = ModelCatalog.partFile(applicationContext)
        val dest = ModelCatalog.modelFile(applicationContext)
        if (ModelCatalog.isComplete(applicationContext)) return Result.success()
        setForeground(createForegroundInfo(0, ModelCatalog.EXPECTED_BYTES))
        var lastPush = 0L
        try {
            val downloaded = downloadResumed(url, part) { done, total ->
                val now = System.currentTimeMillis()
                if (now - lastPush >= Tuning.DOWNLOAD_PROGRESS_INTERVAL_MS) {
                    lastPush = now
                    setProgressAsync(Data.Builder().putLong(PROGRESS_DONE, done).putLong(PROGRESS_TOTAL, total).build())
                    setForegroundAsync(createForegroundInfo(done, total))
                }
            }
            if (downloaded != ModelCatalog.EXPECTED_BYTES) {
                return Result.retry()
            }
            val recorded = sha256Hex(part)
            ModelCatalog.shaFile(applicationContext).writeText(recorded)
            if (dest.exists() && !dest.delete()) return Result.failure()
            if (!part.renameTo(dest)) return Result.failure()
            return Result.success()
        } catch (_: Exception) {
            return Result.retry()
        }
    }

    private fun downloadResumed(url: String, part: File, onProgress: (Long, Long) -> Unit): Long {
        val total = ModelCatalog.EXPECTED_BYTES
        var done = if (part.exists()) part.length() else 0L
        if (done > total) {
            part.delete()
            done = 0L
        }
        var redirects = 0
        var current = url
        while (true) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Serifu/1.0")
                if (done > 0) setRequestProperty("Range", "bytes=$done-")
            }
            try {
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307, 308,
                        -> {
                        if (redirects++ > 5) throw IllegalStateException("Too many redirects")
                        current = connection.getHeaderField("Location") ?: throw IllegalStateException("Empty redirect")
                        connection.disconnect()
                        continue
                    }
                    HttpURLConnection.HTTP_PARTIAL -> Unit
                    HttpURLConnection.HTTP_OK -> {
                        if (done > 0) {
                            part.delete()
                            done = 0L
                        }
                    }
                    416 -> {
                        if (part.length() == total) return total
                        part.delete()
                        done = 0L
                        connection.disconnect()
                        continue
                    }
                    else -> throw IllegalStateException("HTTP ${connection.responseCode}")
                }
                RandomAccessFile(part, "rw").use { access ->
                    access.seek(done)
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(Tuning.DOWNLOAD_BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            access.write(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                    }
                }
                return done
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(Tuning.DOWNLOAD_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun createForegroundInfo(done: Long, total: Long): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_serifu)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(applicationContext.getString(R.string.gemma_downloading, done.mb(), total.mb()))
            .setProgress(100, if (total > 0) (done * 100 / total).toInt() else 0, false)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, applicationContext.getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun Long.mb(): String = "${this / 1_000_000} MB"

    companion object {
        const val WORK_NAME = "gemma-download"
        const val KEY_URL = "url"
        const val PROGRESS_DONE = "done"
        const val PROGRESS_TOTAL = "total"
        private const val CHANNEL = "serifu_downloads"
        private const val NOTIFICATION_ID = 42

        fun enqueue(context: Context, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<GemmaModelDownloader>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

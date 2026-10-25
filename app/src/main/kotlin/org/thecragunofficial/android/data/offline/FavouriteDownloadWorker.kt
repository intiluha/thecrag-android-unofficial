package org.thecragunofficial.android.data.offline

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.thecragunofficial.android.TheCragApp
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Runs one favourite's download as a foreground WorkManager job with a progress
 * notification, so it survives the app being backgrounded. Plain network
 * failures are retried with backoff; anything else fails the job and the
 * favourite shows the error with a Retry button.
 */
class FavouriteDownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, 0L)
        val refresh = inputData.getBoolean(KEY_REFRESH, false)
        val favourites = TheCragApp.graph(applicationContext).favourites
        val name = favourites.get(id)?.name ?: return Result.failure()
        var lastShown = 0L
        suspend fun show(done: Int, total: Int) {
            try {
                setForeground(foregroundInfo(name, done, total))
            } catch (e: Exception) {
                // Foreground start not allowed from the background on recent Android: run quietly instead.
            }
        }
        show(0, 0)
        return try {
            favourites.download(id, refresh) { done, total ->
                val now = System.currentTimeMillis()
                if (now - lastShown > 1000) { lastShown = now; setProgressAsync(workDataOf(KEY_DONE to done, KEY_TOTAL to total)) }
            }
            Result.success()
        } catch (e: DownloadHttpException) {
            Result.failure()
        } catch (e: IOException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun foregroundInfo(name: String, done: Int, total: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Offline downloads", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $name")
            .setContentText(if (total > 0) "$done / $total" else "Scanning area")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, n)
        }
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_REFRESH = "refresh"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        private const val CHANNEL = "offline"
        private const val NOTIFICATION_ID = 1
        private const val MAX_RETRIES = 5

        fun workName(id: Long) = "favourite-$id"

        fun enqueue(context: Context, id: Long, refresh: Boolean) {
            val request = OneTimeWorkRequestBuilder<FavouriteDownloadWorker>()
                .setInputData(workDataOf(KEY_ID to id, KEY_REFRESH to refresh))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, id: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        }
    }
}

package com.training.trackplanner.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import java.util.concurrent.TimeUnit

/** Durable scheduling for automatic Cloud Backup. The worker contains the runtime safety gate. */
internal object CloudBackupScheduler {
    const val UNIQUE_WORK_NAME = "whatyougottado-cloud-backup"
    const val COALESCING_WORK_NAME = "whatyougottado-cloud-backup-debounce"
    const val PERIODIC_WORK_NAME = "whatyougottado-cloud-backup-safety-check"
    const val COALESCING_DELAY_MILLIS = 10 * 60 * 1000L
    const val RETRY_BACKOFF_MILLIS = 15 * 60 * 1000L
    const val PERIODIC_INTERVAL_HOURS = 12L

    fun schedule(context: Context, delayMillis: Long = COALESCING_DELAY_MILLIS) {
        val wake = OneTimeWorkRequestBuilder<CloudBackupWakeWorker>()
            .setConstraints(connectedConstraint())
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        runCatching {
            // Reschedule waiting debounce work without cancelling an upload.
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                COALESCING_WORK_NAME, ExistingWorkPolicy.REPLACE, wake
            )
        }
    }

    /** Only the debounce worker starts uploads. An active upload is never REPLACEd. */
    internal fun enqueueUpload(context: Context) {
        val upload = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setConstraints(connectedConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, upload
        )
    }

    /** Schedule only an outstanding, enabled obligation. A disabled backup is not retried. */
    internal suspend fun scheduleIfPending(
        context: Context,
        db: TrainingDatabase,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val state = db.cloudBackupStateDao().getOrCreate()
        if (!state.cloudBackupPending || !state.cloudBackupEnabled) return false
        val fromLatestChange = state.lastLocalChangeAt
            ?.let { (it + COALESCING_DELAY_MILLIS - now).coerceAtLeast(0L) }
            ?: 0L
        val fromRetry = state.nextRetryAt
            ?.let { (it - now).coerceAtLeast(0L) }
            ?: 0L
        schedule(context, maxOf(fromLatestChange, fromRetry))
        return true
    }

    fun cancel(context: Context) {
        runCatching {
            val manager = WorkManager.getInstance(context.applicationContext)
            manager.cancelUniqueWork(COALESCING_WORK_NAME)
            manager.cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    /** A process-death and reboot-safe periodic check. It only enqueues work when pending. */
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<CloudBackupSafetyWorker>(
            PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS
        ).setConstraints(connectedConstraint()).build()
        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    private fun connectedConstraint(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}

/** Replaceable 10-minute debounce trigger. A new edit never cancels CloudBackupWorker. */
internal class CloudBackupWakeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = TrainingDatabase.get(applicationContext)
        val state = db.cloudBackupStateDao().getOrCreate()
        if (!state.cloudBackupPending || !state.cloudBackupEnabled) return Result.success()
        val delay = state.lastLocalChangeAt
            ?.let {
                (it + CloudBackupScheduler.COALESCING_DELAY_MILLIS -
                    System.currentTimeMillis()).coerceAtLeast(0L)
            } ?: 0L
        if (delay > 0L) {
            CloudBackupScheduler.schedule(applicationContext, delay)
        } else {
            CloudBackupScheduler.enqueueUpload(applicationContext)
        }
        return Result.success()
    }
}

internal class CloudBackupSafetyWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = TrainingDatabase.get(applicationContext)
        CloudBackupScheduler.scheduleIfPending(applicationContext, db)
        return Result.success()
    }
}

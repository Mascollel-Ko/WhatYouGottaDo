package com.training.trackplanner.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import java.util.concurrent.TimeUnit

/** Durable scheduling for automatic Cloud Backup. The worker contains the runtime safety gate. */
internal object CloudBackupScheduler {
    const val UNIQUE_WORK_NAME = "whatyougottado-cloud-backup"
    const val PERIODIC_WORK_NAME = "whatyougottado-cloud-backup-safety-check"
    const val COALESCING_DELAY_MILLIS = 10 * 60 * 1000L
    const val RETRY_BACKOFF_MILLIS = 15 * 60 * 1000L
    const val PERIODIC_INTERVAL_HOURS = 12L

    fun schedule(context: Context, delayMillis: Long = COALESCING_DELAY_MILLIS) {
        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setConstraints(connectedConstraint())
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        runCatching {
            val workManager = WorkManager.getInstance(context.applicationContext)
            val policy = runCatching {
                if (workManager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
                        .any { it.state == WorkInfo.State.RUNNING }
                ) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
            }.getOrDefault(ExistingWorkPolicy.REPLACE)
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                policy,
                request
            )
        }
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
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
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

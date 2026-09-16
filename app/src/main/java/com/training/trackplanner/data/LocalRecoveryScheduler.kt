package com.training.trackplanner.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal object LocalRecoveryScheduler {
    private const val UNIQUE_NAME = "local-recovery-refresh"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<LocalRecoveryWorker>()
            .setInitialDelay(RecoveryRevision.STABILIZATION_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}

internal class LocalRecoveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = TrainingDatabase.get(applicationContext)
        val state = db.cloudBackupStateDao().get() ?: return Result.success()
        val expected = RecoveryRevision.from(state) ?: return Result.success()
        val repo = TrainingRepository(db, applicationContext)
        return if (repo.refreshLocalRecoveryIfStable(expected)) Result.success()
        else {
            LocalRecoveryScheduler.schedule(applicationContext)
            Result.success()
        }
    }
}

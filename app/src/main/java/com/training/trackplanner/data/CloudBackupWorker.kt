package com.training.trackplanner.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/**
 * Automatic Cloud Backup execution. All account, enablement, pending and lineage checks are
 * repeated at execution time; queued work never carries a trusted user id.
 */
internal class CloudBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = TrainingDatabase.get(context)
        val state = db.cloudBackupStateDao().getOrCreate()
        if (!state.cloudBackupPending) return Result.success()
        if (!state.cloudBackupEnabled) {
            db.cloudBackupStateDao().updateRetry(state.retryAttempt, null, "CLOUD_BACKUP_DISABLED")
            return Result.success()
        }

        val auth = CloudAuthRepository(context)
        val session = auth.refreshIfNeeded()
        if (session == null) {
            db.cloudBackupStateDao().updateRetry(state.retryAttempt, null, "LOGGED_OUT")
            return Result.success()
        }

        val repository = TrainingRepository(db, context)
        val account = repository.cloudAccountEntrySnapshot()
        val action = runCatching {
            CloudAccountEntryClassifier.classify(account, session.userId)
        }.getOrElse {
            db.cloudBackupStateDao().updateRetry(state.retryAttempt, null, "ACCOUNT_STATE_INVALID")
            return Result.success()
        }
        if (action !in SAFE_AUTOMATIC_ACTIONS) {
            // Guest comparison, account archive and fresh-restore decisions belong to the
            // foreground account flow. Automatic work must leave the obligation untouched.
            db.cloudBackupStateDao().updateRetry(state.retryAttempt, null, action.name)
            return Result.success()
        }
        if (!CloudBackupConfig.fromBuildConfig().configured) {
            db.cloudBackupStateDao().updateRetry(state.retryAttempt, null, "CLOUD_NOT_CONFIGURED")
            return Result.success()
        }

        return try {
            // Logout/account changes can happen while work is queued. Recheck the current
            // store immediately before starting the upload and never trust worker input.
            val current = auth.refreshIfNeeded()
            check(current != null && current.userId.equals(session.userId, ignoreCase = true)) {
                "LOGGED_OUT"
            }
            repository.uploadCloudBackup(
                session = current,
                automatic = true
            )
            // An edit during the upload leaves the newer revision pending. Re-arm the
            // unique work using the latest change/retry timestamps.
            CloudBackupScheduler.scheduleIfPending(context, db)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val code = cloudFailureCode(error)
            if (isTransientCloudFailure(error, code)) {
                Result.retry()
            } else {
                db.cloudBackupStateDao().updateRetry(state.retryAttempt, null, code)
                Result.success()
            }
        }
    }

    private fun cloudFailureCode(error: Throwable): String =
        generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotBlank) }
            .firstOrNull()
            ?.take(120)
            ?: "CLOUD_UPLOAD_FAILED"

    private fun isTransientCloudFailure(error: Throwable, code: String): Boolean {
        if (code in NON_RETRYABLE_CODES) return false
        if (code.startsWith("AUTH_")) return false
        return error is IOException || code in TRANSIENT_CODES
    }

    private companion object {
        val SAFE_AUTOMATIC_ACTIONS = setOf(
            CloudAccountEntryAction.ENABLE_CLOUD,
            CloudAccountEntryAction.ADOPT_GUEST_LOCAL_DATA,
            CloudAccountEntryAction.RESUME_SAME_ACCOUNT
        )
        val NON_RETRYABLE_CODES = setOf(
            "LOGGED_OUT",
            "CLOUD_BACKUP_DISABLED",
            "CLOUD_NOT_CONFIGURED",
            "ACCOUNT_STATE_INVALID",
            "AUTO_RESTORE_CURRENT",
            "REQUIRE_GUEST_CLOUD_COMPARISON",
            "REQUIRE_ACCOUNT_ARCHIVE",
            "GUEST_CLOUD_COMPARISON_REQUIRED",
            "ACCOUNT_ARCHIVE_REQUIRED",
            "INVALID_CANONICAL_BACKUP",
            "UNSUPPORTED_BACKUP_VERSION",
            "INVALID_SERVER_RESPONSE"
        )
        val TRANSIENT_CODES = setOf(
            "NETWORK_UNAVAILABLE",
            "TIMEOUT",
            "R2_PUT_FAILED",
            "CLOUD_REQUEST_FAILED",
            "R2_GET_FAILED",
            "TEMPORARY_FAILURE",
            "HTTP_408",
            "HTTP_429",
            "HTTP_500",
            "HTTP_502",
            "HTTP_503",
            "HTTP_504"
        )
    }
}

/** Process-local serialization between manual and automatic Cloud operations. */
internal object CloudBackupRuntimeMutex {
    private val mutex = kotlinx.coroutines.sync.Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}

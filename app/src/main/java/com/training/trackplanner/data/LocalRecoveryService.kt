package com.training.trackplanner.data

import androidx.room.withTransaction

internal enum class RecoveryTrigger {
    BEFORE_MANUAL_IMPORT, BEFORE_BULK_CHANGE, BEFORE_CLOUD_RESTORE, BEFORE_CONFLICT_CLOUD_RESTORE
}


internal data class RecoveryRevision(val revision: Long, val changedAt: Long) {
    fun delayMillis(now: Long): Long = (changedAt + STABILIZATION_MILLIS - now).coerceAtLeast(0)
    companion object {
        const val STABILIZATION_MILLIS = 5 * 60 * 1000L
        fun from(state: CloudBackupState?): RecoveryRevision? = state?.lastLocalChangeAt?.let {
            RecoveryRevision(state.localRevision, it)
        }
    }
}

/** File IO finishes before any protected mutation. The Room pointer is the commit record,
 * including on a trusted restore: user data, lineage and pointer commit or roll back together.
 */
internal class LocalRecoveryService(
    private val db: TrainingDatabase,
    private val store: LocalRecoveryStore,
    private val canonical: suspend (Long) -> CanonicalBackupContent,
    private val applyRecovery: suspend (RecordCsvImportData.Restore) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun current(): ValidatedLocalRecovery? = withLocalDataGate {
        activeId()?.let { store.validate(it) }
    }

    suspend fun snapshot(): ValidatedLocalRecovery = withLocalDataGate {
        val candidate = buildCandidate()
        db.withTransaction { promote(candidate) }
        cleanup()
        candidate
    }

    suspend fun refreshStable(expected: RecoveryRevision): Boolean = withLocalDataGate {
        val actual = RecoveryRevision.from(db.cloudBackupStateDao().get())
        if (actual != expected || expected.delayMillis(now()) > 0) return@withLocalDataGate false
        snapshot()
        true
    }

    suspend fun <T> protect(trigger: RecoveryTrigger, operation: suspend () -> T): T = withLocalDataGate {
        // All four triggers intentionally share the same fail-closed durability boundary.
        check(trigger in RecoveryTrigger.entries)
        snapshot()
        operation()
    }

    suspend fun <T> externalImport(operation: suspend () -> T): T =
        protect(RecoveryTrigger.BEFORE_MANUAL_IMPORT) {
            db.withTransaction {
                val result = operation()
                db.startExternalCloudBranchInTransaction()
                result
            }
        }

    suspend fun restore(): Unit = withLocalDataGate {
        val previous = checkNotNull(activeId()) { "No Local Recovery exists" }
        val a = store.validate(previous)
        val b = buildCandidate()
        db.withTransaction {
            val installation = db.cloudBackupStateDao().getOrCreate()
            applyRecovery(a.data)
            val m = a.metadata
            // Restore logical ancestry, never credentials, installation identity, retry state,
            // enablement or an unverified Cloud-success claim.
            db.cloudBackupStateDao().restoreTrusted(CloudBackupState(
                installId = installation.installId, accountUserId = m.accountUserId,
                localBaseBackupId = m.localBaseBackupId, localRevision = m.localRevision,
                cloudBackupEnabled = installation.cloudBackupEnabled,
                cloudBackupPending = m.cloudBackupPending, lastLocalChangeAt = m.lastLocalChangeAt
            ))
            promote(b)
        }
        cleanup()
    }

    suspend fun cleanup(): Unit = withLocalDataGate {
        val active = activeId()
        // A corrupt active recovery is retained for diagnosis; cleanup must not hide corruption.
        if (active != null) store.validate(active)
        store.cleanup(active)
    }

    private suspend fun buildCandidate(): ValidatedLocalRecovery {
        val time = now()
        val captured = db.withTransaction {
            canonical(time) to db.cloudBackupStateDao().getOrCreate()
        }
        return store.candidate(captured.first, captured.second, time)
    }
    private suspend fun activeId() = db.appMetaDao().value(POINTER_KEY)
    private suspend fun promote(value: ValidatedLocalRecovery) {
        db.appMetaDao().upsert(AppMeta(POINTER_KEY, value.metadata.snapshotId))
    }
    companion object { const val POINTER_KEY = "local_recovery.active_generation" }
}

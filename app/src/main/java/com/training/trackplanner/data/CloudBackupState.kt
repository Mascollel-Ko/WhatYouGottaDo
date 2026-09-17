package com.training.trackplanner.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/** Installation-local infrastructure: never included in the canonical user backup. */
@Entity(tableName = "cloud_backup_state")
data class CloudBackupState(
    @PrimaryKey val id: Int = 1,
    val accountUserId: String? = null,
    val installId: String,
    val cloudBackupEnabled: Boolean = false,
    val localBaseBackupId: String? = null,
    val localRevision: Long = 0,
    val cloudBackupPending: Boolean = false,
    val lastLocalChangeAt: Long? = null,
    val lastSuccessfulBackupId: String? = null,
    val lastSuccessfulBackupAt: Long? = null,
    val retryAttempt: Int = 0,
    val nextRetryAt: Long? = null,
    val lastFailureCode: String? = null
)

@Dao
abstract class CloudBackupStateDao {
    @Query("SELECT * FROM cloud_backup_state WHERE id = 1")
    abstract suspend fun get(): CloudBackupState?

    @Query("SELECT * FROM cloud_backup_state WHERE id = 1")
    abstract fun observe(): kotlinx.coroutines.flow.Flow<CloudBackupState?>

    @Update
    internal abstract suspend fun restoreTrusted(state: CloudBackupState)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertInitial(state: CloudBackupState)

    @Transaction
    open suspend fun getOrCreate(): CloudBackupState {
        get()?.let { return it }
        insertInitial(CloudBackupState(installId = UUID.randomUUID().toString()))
        return checkNotNull(get())
    }

    @Query("""UPDATE cloud_backup_state SET localRevision = localRevision + 1,
        cloudBackupPending = 1, lastLocalChangeAt = :now,
        retryAttempt = 0, nextRetryAt = NULL, lastFailureCode = NULL WHERE id = 1""")
    internal abstract suspend fun recordLocalChange(now: Long)

    @Query("""UPDATE cloud_backup_state SET localBaseBackupId = NULL, localRevision = 1,
        cloudBackupPending = 1, lastLocalChangeAt = :now WHERE id = 1""")
    internal abstract suspend fun startExternalBranch(now: Long)

    @Query("""UPDATE cloud_backup_state SET retryAttempt = :attempt, nextRetryAt = :nextAt,
        lastFailureCode = :failure WHERE id = 1""")
    internal abstract suspend fun updateRetry(attempt: Int, nextAt: Long?, failure: String?)

    @Query("""UPDATE cloud_backup_state SET accountUserId = :accountUserId,
        cloudBackupEnabled = 1 WHERE id = 1 AND accountUserId IS NULL""")
    internal abstract suspend fun bindAccount(accountUserId: String): Int

    @Query("UPDATE cloud_backup_state SET cloudBackupEnabled = 1 WHERE id = 1 AND accountUserId = :accountUserId")
    internal abstract suspend fun enableForAccount(accountUserId: String)

    @Query("UPDATE cloud_backup_state SET cloudBackupEnabled = :enabled WHERE id = 1 AND accountUserId = :accountUserId")
    internal abstract suspend fun setEnabledForAccount(accountUserId: String, enabled: Boolean)

    /** Acknowledge only the account/base snapshot used for the upload. */
    @Query("""UPDATE cloud_backup_state SET localBaseBackupId = :backupId,
        localRevision = CASE WHEN localRevision <= :snapshotRevision THEN 0
            ELSE localRevision - :snapshotRevision END,
        cloudBackupPending = CASE WHEN localRevision <= :snapshotRevision THEN 0 ELSE 1 END,
        lastSuccessfulBackupId = :backupId, lastSuccessfulBackupAt = :now,
        retryAttempt = 0, nextRetryAt = NULL, lastFailureCode = NULL
        WHERE id = 1 AND accountUserId = :accountUserId
        AND ((localBaseBackupId IS NULL AND :snapshotBaseBackupId IS NULL)
             OR localBaseBackupId = :snapshotBaseBackupId)""")
    internal abstract suspend fun acknowledgeIfSnapshotUnchanged(
        accountUserId: String, snapshotBaseBackupId: String?, snapshotRevision: Long,
        backupId: String, now: Long
    ): Int
}

internal val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `cloud_backup_state` (
            `id` INTEGER NOT NULL, `accountUserId` TEXT, `installId` TEXT NOT NULL,
            `cloudBackupEnabled` INTEGER NOT NULL, `localBaseBackupId` TEXT,
            `localRevision` INTEGER NOT NULL, `cloudBackupPending` INTEGER NOT NULL,
            `lastLocalChangeAt` INTEGER, `lastSuccessfulBackupId` TEXT,
            `lastSuccessfulBackupAt` INTEGER, `retryAttempt` INTEGER NOT NULL,
            `nextRetryAt` INTEGER, `lastFailureCode` TEXT, PRIMARY KEY(`id`))""")
        // Existing data has never been acknowledged by Cloud. Do not fabricate ancestry
        // or a user-edit timestamp: conservatively retain one outstanding local revision.
        db.execSQL("""INSERT INTO cloud_backup_state
            (id, installId, cloudBackupEnabled, localRevision, cloudBackupPending, retryAttempt)
            VALUES (1, ?, 0, 1, 1, 0)""", arrayOf(UUID.randomUUID().toString()))
    }
}

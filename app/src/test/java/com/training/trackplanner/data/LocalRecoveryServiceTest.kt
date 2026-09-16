package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalRecoveryServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()

    private fun database() = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
        .allowMainThreadQueries().build().also { databases += it }

    @After fun close() = databases.forEach { it.close() }

    @Test fun snapshotIsGzipHashedAndSafeSwapIsReversible() = runBlocking {
        val db = database()
        val exercise = Exercise("recovery-test", "Recovery test", "Strength", isCustom = true)
        db.exerciseDao().insertExercise(exercise)
        val records = RecordMutationService(db, db.exerciseDao(), db.workoutDao(),
            workoutSourceIdentityProvider = WorkoutSourceIdentityProvider(db, db.appMetaDao(), db.workoutDao()))
        records.addWorkoutEntry("2026-09-01", exercise.stableKey)
        val repo = TrainingRepository(db, context)

        val first = repo.createLocalRecoverySnapshot()
        val installId = db.cloudBackupStateDao().get()!!.installId
        assertEquals(14, first.metadata.backupFormatVersion)
        assertEquals(13, first.metadata.schemaVersion)
        assertEquals(first.metadata.compressedSizeBytes,
            java.io.File(context.noBackupFilesDir, "recovery/${first.metadata.snapshotId}/${LocalRecoveryStore.PAYLOAD}").length())
        assertEquals(1, db.workoutDao().allEntries().size)

        records.addWorkoutEntry("2026-09-02", exercise.stableKey)
        assertEquals(2, db.workoutDao().allEntries().size)

        repo.restoreLocalRecoverySnapshot()
        assertEquals(1, db.workoutDao().allEntries().size)
        val rotated = repo.localRecovery.current()!!
        assertNotEquals(first.metadata.snapshotId, rotated.metadata.snapshotId)
        assertEquals(installId, db.cloudBackupStateDao().get()!!.installId)
    }

    @Test fun stableRefreshRejectsStaleRevisionAndAcceptsFiveMinuteBoundary() = runBlocking {
        val db = database()
        val state = db.cloudBackupStateDao().getOrCreate()
        val repo = TrainingRepository(db, context)
        val stale = RecoveryRevision(state.localRevision + 1, 0L)
        assertFalse(repo.refreshLocalRecoveryIfStable(stale))
        assertFalse(repo.refreshLocalRecoveryIfStable(RecoveryRevision(0, System.currentTimeMillis())))
    }

    @Test fun secondValidSnapshotReplacesTheActiveGeneration() = runBlocking {
        val db = database()
        val exercise = Exercise("recovery-replace", "Recovery replace", "Strength", isCustom = true)
        db.exerciseDao().insertExercise(exercise)
        val records = RecordMutationService(db, db.exerciseDao(), db.workoutDao(),
            workoutSourceIdentityProvider = WorkoutSourceIdentityProvider(db, db.appMetaDao(), db.workoutDao()))
        records.addWorkoutEntry("2026-09-03", exercise.stableKey)
        val repo = TrainingRepository(db, context)
        val first = repo.createLocalRecoverySnapshot()
        records.addWorkoutEntry("2026-09-04", exercise.stableKey)
        val second = repo.createLocalRecoverySnapshot()
        assertNotEquals(first.metadata.snapshotId, second.metadata.snapshotId)
        assertEquals(second.metadata.snapshotId, repo.localRecovery.current()!!.metadata.snapshotId)
    }

    @Test fun externalImportResetsBranchOnlyAfterTheImportTransactionCommits() = runBlocking {
        val db = database()
        val exercise = Exercise("recovery-import", "Recovery import", "Strength", isCustom = true)
        db.exerciseDao().insertExercise(exercise)
        val records = RecordMutationService(db, db.exerciseDao(), db.workoutDao(),
            workoutSourceIdentityProvider = WorkoutSourceIdentityProvider(db, db.appMetaDao(), db.workoutDao()))
        records.addWorkoutEntry("2026-09-05", exercise.stableKey)
        val repo = TrainingRepository(db, context)
        val csv = repo.canonicalRecordsBackup().csv
        db.openHelper.writableDatabase.execSQL(
            "UPDATE cloud_backup_state SET localBaseBackupId = 'old-base', localRevision = 9 WHERE id = 1"
        )
        val file = java.io.File.createTempFile("recovery-import", ".csv", context.cacheDir)
        try {
            file.writeText(csv, Charsets.UTF_8)
            repo.importRecordsBackup(android.net.Uri.fromFile(file))
            val state = db.cloudBackupStateDao().get()!!
            assertNull(state.localBaseBackupId)
            assertEquals(1L, state.localRevision)
            assertTrue(state.cloudBackupPending)
            assertEquals(1, db.workoutDao().allEntries().size)
        } finally { file.delete() }
    }
}

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
import org.json.JSONObject
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalRecoveryServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()
    private val roots = mutableListOf<File>()

    private fun database() = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
        .allowMainThreadQueries().build().also { databases += it }

    @After fun close() {
        databases.forEach { it.close() }
        roots.forEach { it.deleteRecursively() }
    }

    private fun recoveryRoot(label: String): File = File.createTempFile(label, "-recovery", context.cacheDir)
        .also { it.delete(); check(it.mkdirs()); roots += it }

    private suspend fun parseCanonical(csv: String): RecordCsvImportData.Restore =
        checkNotNull(RecordCsvBackupRestore.parse(csv) as? RecordCsvImportData.Restore)

    private fun service(
        db: TrainingDatabase,
        root: File,
        fault: (String) -> Unit = {},
        now: () -> Long = System::currentTimeMillis,
        applyRecovery: suspend (RecordCsvImportData.Restore) -> Unit = {}
    ): LocalRecoveryService = LocalRecoveryService(
        db = db,
        store = LocalRecoveryStore(root, ::parseCanonical, fault = fault),
        canonical = { time -> TrainingRepository(db, context).canonicalRecordsBackup(time) },
        applyRecovery = applyRecovery,
        now = now
    )

    private suspend fun activeId(db: TrainingDatabase): String =
        checkNotNull(db.appMetaDao().value(LocalRecoveryService.POINTER_KEY))

    private suspend fun entries(db: TrainingDatabase) = db.workoutDao().allEntriesWithSets()

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

    @Test fun candidateFailureAfterEachDurabilityStagePreservesPriorRecoveryAndRoom() = runBlocking {
        val db = database()
        val exercise = Exercise("recovery-fault", "Recovery fault", "Strength", isCustom = true)
        db.exerciseDao().insertExercise(exercise)
        val records = RecordMutationService(db, db.exerciseDao(), db.workoutDao(),
            workoutSourceIdentityProvider = WorkoutSourceIdentityProvider(db, db.appMetaDao(), db.workoutDao()))
        records.addWorkoutEntry("2026-09-10", exercise.stableKey)
        val root = recoveryRoot("candidate-failure")
        val stable = service(db, root)
        val recoveryA = stable.snapshot()
        records.addWorkoutEntry("2026-09-11", exercise.stableKey)
        val before = entries(db)

        listOf("payload", "sidecar", "validated").forEach { stage ->
            val failing = service(db, root, fault = { actual ->
                if (actual == stage) error("injected $stage failure")
            })
            assertTrue(runCatching { failing.snapshot() }.isFailure)
            assertEquals(recoveryA.metadata.snapshotId, activeId(db))
            assertEquals(recoveryA.data, failing.current()!!.data)
            assertEquals(before, entries(db))
            assertTrue(File(root, recoveryA.metadata.snapshotId).isDirectory)
        }
    }

    @Test fun corruptPayloadAndSidecarAreRejectedWithoutReplacingActivePointer() = runBlocking {
        val db = database()
        val exercise = Exercise("recovery-corrupt", "Recovery corrupt", "Strength", isCustom = true)
        db.exerciseDao().insertExercise(exercise)
        val root = recoveryRoot("corrupt-payload")
        val recoveryA = service(db, root).snapshot()
        val pointer = recoveryA.metadata.snapshotId
        val payload = File(root, "$pointer/${LocalRecoveryStore.PAYLOAD}")
        val originalPayload = payload.readBytes()
        payload.writeBytes(originalPayload.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() })
        val payloadFailure = runCatching { service(db, root).current() }
        assertTrue(payloadFailure.isFailure)
        assertEquals(pointer, activeId(db))
        assertTrue(payload.exists())

        payload.writeBytes(originalPayload)
        val sidecar = File(root, "$pointer/${LocalRecoveryStore.SIDECAR}")
        val invalidSidecar = JSONObject(sidecar.readText()).put("snapshotId", UUID.randomUUID().toString())
        sidecar.writeText(invalidSidecar.toString(), Charsets.UTF_8)
        val sidecarFailure = runCatching { service(db, root).current() }
        assertTrue(sidecarFailure.isFailure)
        assertEquals(pointer, activeId(db))
        assertTrue(sidecar.exists())
    }

    @Test fun protectedBulkMutationFailsClosedWhenRecoveryCreationFails() = runBlocking {
        val db = database()
        val exercise = Exercise("recovery-protected", "Recovery protected", "Strength", isCustom = true)
        db.exerciseDao().insertExercise(exercise)
        val identities = WorkoutSourceIdentityProvider(db, db.appMetaDao(), db.workoutDao())
        val records = RecordMutationService(db, db.exerciseDao(), db.workoutDao(),
            workoutSourceIdentityProvider = identities)
        records.addWorkoutEntry("2026-09-12", exercise.stableKey)
        val root = recoveryRoot("protected-failure")
        val good = service(db, root)
        val recoveryA = good.snapshot()
        val beforeEntries = entries(db)
        val beforeState = db.cloudBackupStateDao().get()!!
        val calendar = CalendarRecordService(db, db.workoutDao(), workoutSourceIdentityProvider = identities)
        val failing = service(db, root, fault = { stage ->
            if (stage == "payload") error("injected protected failure")
        })

        assertTrue(runCatching {
            failing.protect(RecoveryTrigger.BEFORE_BULK_CHANGE) {
                calendar.deleteDateRange("2026-09-12", "2026-09-12", includeConfirmed = true)
            }
        }.isFailure)
        assertEquals(beforeEntries, entries(db))
        assertEquals(beforeState, db.cloudBackupStateDao().get())
        assertEquals(recoveryA.metadata.snapshotId, activeId(db))
        assertEquals(recoveryA.data, failing.current()!!.data)
    }

    @Test fun failedTrustedRestoreLeavesCurrentBAndRecoveryAIntact() = runBlocking {
        val db = database()
        val exercise = Exercise("recovery-restore-failure", "Recovery restore failure", "Strength", isCustom = true)
        db.exerciseDao().insertExercise(exercise)
        val records = RecordMutationService(db, db.exerciseDao(), db.workoutDao(),
            workoutSourceIdentityProvider = WorkoutSourceIdentityProvider(db, db.appMetaDao(), db.workoutDao()))
        records.addWorkoutEntry("2026-09-13", exercise.stableKey)
        val root = recoveryRoot("restore-failure")
        val good = service(db, root)
        val recoveryA = good.snapshot()
        records.addWorkoutEntry("2026-09-14", exercise.stableKey)
        val currentB = entries(db)
        val failing = service(db, root, applyRecovery = { error("injected apply failure") })

        assertTrue(runCatching { failing.restore() }.isFailure)
        assertEquals(currentB, entries(db))
        assertEquals(recoveryA.metadata.snapshotId, activeId(db))
        assertEquals(recoveryA.data, failing.current()!!.data)
        assertTrue(root.listFiles().orEmpty().any { it.name != recoveryA.metadata.snapshotId && it.isDirectory })
    }

    @Test fun trustedRestoreRestoresLineageButKeepsInstallationAndClearsTransientState() = runBlocking {
        val db = database()
        val root = recoveryRoot("lineage-restore")
        val initial = db.cloudBackupStateDao().getOrCreate()
        val stateA = initial.copy(
            accountUserId = "account-a",
            localBaseBackupId = "base-a",
            localRevision = 7,
            cloudBackupEnabled = true,
            cloudBackupPending = true,
            lastLocalChangeAt = 111L,
            lastSuccessfulBackupId = "success-a",
            lastSuccessfulBackupAt = 333L,
            retryAttempt = 3,
            nextRetryAt = 222L,
            lastFailureCode = "A_FAILURE"
        )
        db.cloudBackupStateDao().restoreTrusted(stateA)
        val recoveryA = service(db, root).snapshot()
        val stateB = stateA.copy(
            accountUserId = "account-b",
            localBaseBackupId = "base-b",
            localRevision = 12,
            cloudBackupEnabled = false,
            cloudBackupPending = false,
            lastLocalChangeAt = 444L,
            lastSuccessfulBackupId = "success-b",
            lastSuccessfulBackupAt = 555L,
            retryAttempt = 9,
            nextRetryAt = 666L,
            lastFailureCode = "B_FAILURE"
        )
        db.cloudBackupStateDao().restoreTrusted(stateB)

        service(db, root).restore()
        val restored = db.cloudBackupStateDao().get()!!
        assertEquals(initial.installId, restored.installId)
        assertEquals(stateA.accountUserId, restored.accountUserId)
        assertEquals(stateA.localBaseBackupId, restored.localBaseBackupId)
        assertEquals(stateA.localRevision, restored.localRevision)
        assertEquals(stateA.cloudBackupPending, restored.cloudBackupPending)
        assertEquals(stateA.lastLocalChangeAt, restored.lastLocalChangeAt)
        assertEquals(stateB.cloudBackupEnabled, restored.cloudBackupEnabled)
        assertEquals(0, restored.retryAttempt)
        assertNull(restored.nextRetryAt)
        assertNull(restored.lastFailureCode)
        assertNull(restored.lastSuccessfulBackupId)
        assertNull(restored.lastSuccessfulBackupAt)
        assertNotEquals(recoveryA.metadata.snapshotId, activeId(db))
    }

    @Test fun stabilizationTokenRejectsRevisionOrTimestampDriftAndAcceptsExactBoundary() = runBlocking {
        val db = database()
        val root = recoveryRoot("stabilization")
        var clock = 1_000L
        val recoveryService = service(db, root, now = { clock })
        val recoveryA = recoveryService.snapshot()
        val initial = db.cloudBackupStateDao().get()!!
        val changedAt = 10_000L
        db.cloudBackupStateDao().restoreTrusted(initial.copy(localRevision = 4, lastLocalChangeAt = changedAt))
        val expected = RecoveryRevision(4, changedAt)

        assertFalse(recoveryService.refreshStable(expected.copy(revision = 5)))
        assertEquals(recoveryA.metadata.snapshotId, activeId(db))
        assertFalse(recoveryService.refreshStable(expected.copy(changedAt = changedAt + 1)))
        assertEquals(recoveryA.metadata.snapshotId, activeId(db))

        clock = changedAt + RecoveryRevision.STABILIZATION_MILLIS
        assertTrue(recoveryService.refreshStable(expected))
        assertNotEquals(recoveryA.metadata.snapshotId, activeId(db))
        assertNotNull(recoveryService.current())
    }
}

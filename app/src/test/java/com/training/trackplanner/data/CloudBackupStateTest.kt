package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CloudBackupStateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()
    private fun database() = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
        .allowMainThreadQueries().build().also { databases += it }
    @After fun close() = databases.forEach { it.close() }
    private suspend fun state(db: TrainingDatabase) = db.cloudBackupStateDao().getOrCreate()
    private suspend fun seed(db: TrainingDatabase) = db.exerciseDao().insertExercise(
        Exercise("cloud-test", "Cloud test", "Strength", isCustom = true))
    private fun records(db: TrainingDatabase) = RecordMutationService(db, db.exerciseDao(), db.workoutDao(),
        workoutSourceIdentityProvider = WorkoutSourceIdentityProvider(db, db.appMetaDao(), db.workoutDao()))

    @Test fun freshSingletonIsStableAcrossConcurrentCallsAndReopen() = runBlocking {
        val name = "cloud-install-${UUID.randomUUID()}"
        val db = Room.databaseBuilder(context, TrainingDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val pair = coroutineScope { val first = async { state(db) }; val second = async { state(db) }; first.await() to second.await() }
            assertEquals(pair.first, pair.second)
            UUID.fromString(pair.first.installId)
            assertEquals(0L, pair.first.localRevision); assertFalse(pair.first.cloudBackupPending)
            assertNull(pair.first.accountUserId); assertNull(pair.first.localBaseBackupId)
            assertFalse(pair.first.cloudBackupEnabled)
            db.close()
            val reopened = Room.databaseBuilder(context, TrainingDatabase::class.java, name).allowMainThreadQueries().build()
            try { assertEquals(pair.first, state(reopened)) } finally { reopened.close() }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun multiRowRecordsAndSecondOperationCountOnceAndPreserveBase() = runBlocking {
        val db = database(); seed(db); state(db)
        db.openHelper.writableDatabase.execSQL("UPDATE cloud_backup_state SET localBaseBackupId = 'trusted-base' WHERE id = 1")
        val id = records(db).addWorkoutEntry("2026-09-01", "cloud-test")
        assertEquals(1, db.workoutDao().setsForEntry(id).size)
        assertEquals(1L, state(db).localRevision); assertTrue(state(db).cloudBackupPending)
        assertNotNull(state(db).lastLocalChangeAt); assertEquals("trusted-base", state(db).localBaseBackupId)
        val set = db.workoutDao().setsForEntry(id).single()
        records(db).updateSet(set.copy(reps = 7, confirmed = true))
        assertEquals(2L, state(db).localRevision)
        records(db).updateSet(db.workoutDao().setsForEntry(id).single())
        assertEquals(2L, state(db).localRevision)
        assertEquals("trusted-base", state(db).localBaseBackupId)
    }

    @Test fun nestedCompoundSaveCountsOneAndMissingDeleteCountsZero() = runBlocking {
        val db = database(); seed(db)
        db.withCloudRevision(CloudMutationScope.workouts(listOf("2026-09-01"))) {
            val id = records(db).addWorkoutEntry("2026-09-01", "cloud-test")
            records(db).addSet(db.workoutDao().findEntryById(id)!!)
        }
        assertEquals(1L, state(db).localRevision)
        assertEquals(2, db.workoutDao().allSets().size)
        CalendarRecordService(db, db.workoutDao()).deleteDate("2026-09-02")
        assertEquals(1L, state(db).localRevision)
    }

    @Test fun domainFailureAndOuterRollbackLeaveBothSidesUnchanged() = runBlocking {
        val db = database(); seed(db); val initial = state(db)
        try {
            db.withCloudRevision(CloudMutationScope.workouts(listOf("2026-09-01"))) {
                records(db).addWorkoutEntry("2026-09-01", "cloud-test")
                error("domain failure")
            }
            fail("must fail")
        } catch (_: IllegalStateException) { }
        assertTrue(db.workoutDao().allEntries().isEmpty()); assertEquals(initial, state(db))
        try {
            db.withTransaction {
                records(db).addWorkoutEntry("2026-09-01", "cloud-test")
                assertEquals(1L, state(db).localRevision)
                error("after revision write, before commit")
            }
            fail("must fail")
        } catch (_: IllegalStateException) { }
        assertTrue(db.workoutDao().allEntries().isEmpty()); assertEquals(initial, state(db))
    }

    @Test fun revisionWriteFailureRollsBackSuccessfulDomainWrites() = runBlocking {
        val db = database(); seed(db); val initial = state(db)
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_cloud_revision BEFORE UPDATE ON cloud_backup_state
            BEGIN SELECT RAISE(ABORT, 'injected revision failure'); END""")
        var failed = false
        try { records(db).addWorkoutEntry("2026-09-01", "cloud-test") } catch (_: Exception) { failed = true }
        assertTrue(failed); assertTrue(db.workoutDao().allEntries().isEmpty())
        assertTrue(db.workoutDao().allSets().isEmpty()); assertEquals(initial, state(db))
    }

    @Test fun infrastructureAndRetryWritesDoNotCountButPortablePreferencesDo() = runBlocking {
        val db = database(); val initial = state(db)
        db.withCloudRevision(CloudMutationScope.PORTABLE_META) {
            db.appMetaDao().upsert(AppMeta("theme", "dark"))
            db.appMetaDao().upsert(AppMeta("locale", "ko"))
            db.appMetaDao().upsert(AppMeta("transfer_report_test", "diagnostic"))
            db.cloudBackupStateDao().updateRetry(2, 123L, "OFFLINE")
        }
        assertEquals(initial.localRevision, state(db).localRevision)
        assertFalse(state(db).cloudBackupPending)
        db.withCloudRevision(CloudMutationScope.PORTABLE_META) {
            db.appMetaDao().upsert(AppMeta(PersonalizedProgramPlanningService.PREFERENCES_KEY, "{}"))
        }
        assertEquals(1L, state(db).localRevision)
    }

    @Test fun repositoryProgramApplyDeleteAndCalendarOperationsCountPerOperation() = runBlocking {
        val db = database(); seed(db); val repo = TrainingRepository(db, context)
        val program = repo.createProgram(); assertEquals(1L, state(db).localRevision)
        repo.addExerciseToProgram(program, 1, 1, "cloud-test"); assertEquals(2L, state(db).localRevision)
        repo.addExerciseToProgram(program, 1, 1, "cloud-test"); assertEquals(3L, state(db).localRevision)
        repo.applyProgramToDates(program, "2026-09-01", ProgramApplyMode.Append)
        assertEquals(4L, state(db).localRevision); assertEquals(2, db.workoutDao().allEntries().size)
        val keys = db.workoutDao().allEntries().associate { it.backupSourceId to it.sessionStableKey }
        repo.moveDate("2026-09-01", "2026-09-02", CalendarConflictMode.Append)
        assertEquals(5L, state(db).localRevision)
        assertEquals(keys, db.workoutDao().allEntries().associate { it.backupSourceId to it.sessionStableKey })
        repo.copyDate("2026-09-02", "2026-09-03", false, CalendarConflictMode.Append)
        assertEquals(6L, state(db).localRevision)
        repo.deleteProgram(program); assertEquals(7L, state(db).localRevision)
        repo.deleteProgram(program); assertEquals(7L, state(db).localRevision)
        repo.deleteDateRange("2026-09-02", "2026-09-03", true)
        assertEquals(8L, state(db).localRevision); assertTrue(db.workoutDao().allEntries().isEmpty())
    }

    @Test fun dailyChangesCountOnceDespiteMetricAndCheckInRows() = runBlocking {
        val db = database(); val service = DailyStatusService(db, db.dailyMetricDao(), db.dailyCheckInDao())
        service.saveDailyMetric("2026-09-01", 8.0, 70.0)
        assertEquals(1L, state(db).localRevision)
        service.saveDailyMetric("2026-09-01", 8.0, 70.0)
        assertEquals(1L, state(db).localRevision)
        service.upsertDailyCheckIn(db.dailyCheckInDao().getForDate("2026-09-01")!!.copy(note = "changed"))
        assertEquals(2L, state(db).localRevision)
        service.deleteDailyCheckIn("2026-09-01"); assertEquals(3L, state(db).localRevision)
    }

    @Test fun repositoryExerciseProfileAndSmashChangesUseSameRevisionBoundary() = runBlocking {
        val db = database(); seed(db); val repo = TrainingRepository(db, context)
        repo.setExerciseActive("cloud-test", true); assertEquals(0L, state(db).localRevision)
        repo.setExerciseActive("cloud-test", false); assertEquals(1L, state(db).localRevision)
        repo.saveInitialUserProfile(InitialUserProfile(bodyWeightKg = 70.0))
        assertEquals(2L, state(db).localRevision)
        repo.saveInitialUserProfile(db.initialUserProfileDao().profile()!!)
        assertEquals(2L, state(db).localRevision)
        repo.addSmashSpeed("2026-09-01", 200.0); assertEquals(3L, state(db).localRevision)
        repo.deleteSmashSpeed(db.smashSpeedDao().all().single().id); assertEquals(4L, state(db).localRevision)
    }

    @Test fun largeDateScopeDoesNotExceedSqliteParameterLimit() = runBlocking {
        val db = database(); seed(db)
        val dates = (0L..1200L).map { java.time.LocalDate.of(2026, 1, 1).plusDays(it).toString() }
        db.withCloudRevision(CloudMutationScope.workouts(dates)) {
            records(db).addWorkoutEntry(dates.last(), "cloud-test")
        }
        assertEquals(1L, state(db).localRevision)
    }

    @Test fun canonicalExportExcludesInfrastructureAndRetainsFormat14Schema13() = runBlocking {
        val db = database(); seed(db); records(db).addWorkoutEntry("2026-09-01", "cloud-test")
        val before = state(db)
        val content = TrainingRepository(db, context).canonicalRecordsBackup(1234L)
        val parsed = RecordCsvBackupRestore.parse(content.csv) as RecordCsvImportData.Restore
        assertEquals(14, parsed.manifest!!.formatVersion); assertEquals(13, parsed.backupSchemaVersion)
        assertFalse(content.csv.contains("cloud_backup_state")); assertFalse(content.csv.contains(before.installId))
        assertEquals(before, state(db))
    }

    @Test fun futureExternalImportBoundaryRequiresTransactionAndRollsBackAtomically() = runBlocking {
        val db = database(); seed(db); state(db)
        db.openHelper.writableDatabase.execSQL("UPDATE cloud_backup_state SET localBaseBackupId = 'trusted-base', localRevision = 4 WHERE id = 1")
        val before = state(db)
        try { db.startExternalCloudBranchInTransaction(); fail("requires transaction") } catch (_: IllegalStateException) { }
        try {
            db.withTransaction {
                db.startExternalCloudBranchInTransaction()
                assertNull(state(db).localBaseBackupId); assertEquals(1L, state(db).localRevision)
                error("import failed")
            }
        } catch (_: IllegalStateException) { }
        assertEquals(before, state(db))
        db.withTransaction { db.startExternalCloudBranchInTransaction() }
        assertEquals(before.installId, state(db).installId)
        assertNull(state(db).localBaseBackupId); assertEquals(1L, state(db).localRevision)
        assertTrue(state(db).cloudBackupPending)
    }

    @Test fun cloudAckKeepsMutationsAddedAfterSnapshotAsPending() = runBlocking {
        val db = database()
        val dao = db.cloudBackupStateDao()
        dao.getOrCreate()
        assertEquals(1, dao.bindAccount("cloud-user"))
        dao.recordLocalChange(10L)
        val snapshot = dao.get()!!
        dao.recordLocalChange(20L)
        assertEquals(1, dao.acknowledgeIfSnapshotUnchanged(
            accountUserId = "cloud-user",
            snapshotBaseBackupId = snapshot.localBaseBackupId,
            snapshotRevision = snapshot.localRevision,
            backupId = "new-backup",
            now = 30L
        ))
        val acknowledged = dao.get()!!
        assertEquals("new-backup", acknowledged.localBaseBackupId)
        assertEquals(1L, acknowledged.localRevision)
        assertTrue(acknowledged.cloudBackupPending)
    }

    @Test fun cloudAckDoesNothingAfterAccountLineageChanges() = runBlocking {
        val db = database()
        val dao = db.cloudBackupStateDao()
        dao.getOrCreate(); dao.bindAccount("cloud-user"); dao.recordLocalChange(10L)
        val snapshot = dao.get()!!
        db.openHelper.writableDatabase.execSQL("UPDATE cloud_backup_state SET accountUserId = 'other-user' WHERE id = 1")
        assertEquals(0, dao.acknowledgeIfSnapshotUnchanged("cloud-user", snapshot.localBaseBackupId,
            snapshot.localRevision, "stale-backup", 20L))
        assertEquals("other-user", dao.get()!!.accountUserId)
    }

    @Test fun room32MigrationPreservesEveryExistingTableAndCreatesConservativeState() = runBlocking {
        val name = "c32-${UUID.randomUUID().toString().take(8)}"
        context.getDatabasePath(name).parentFile!!.mkdirs()
        val schemaFile = listOf(File("schemas/com.training.trackplanner.data.TrainingDatabase/32.json"),
            File("app/schemas/com.training.trackplanner.data.TrainingDatabase/32.json")).first { it.exists() }
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database").getJSONArray("entities")
        val tables = (0 until schema.length()).map { schema.getJSONObject(it).getString("tableName") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(32) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    for (i in 0 until schema.length()) {
                        val entity = schema.getJSONObject(i); val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = entity.getJSONArray("indices")
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                        if (table in setOf("exercises", "workout_entries", "training_programs")) {
                            val fields = entity.getJSONArray("fields")
                            val columns = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
                            val values = (0 until fields.length()).map { n ->
                                val field = fields.getJSONObject(n)
                                when { !field.getBoolean("notNull") -> "NULL"; field.getString("affinity") == "TEXT" -> "''"; else -> "1" }
                            }.toMutableList()
                            for (key in listOf("stableKey", "exerciseStableKey")) if (key in columns) values[columns.indexOf(key)] = "'preserved-key'"
                            if ("sessionStableKey" in columns) values[columns.indexOf("sessionStableKey")] = "'preserved-session'"
                            if ("backupSourceId" in columns) values[columns.indexOf("backupSourceId")] = "'preserved-source'"
                            db.execSQL("INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${values.joinToString()})")
                        }
                    }
                    db.execSQL("INSERT INTO workout_sets (id,entryId,setIndex,reps,weightKg,seconds,confirmed,manualWeight) VALUES (1,1,1,5,100,0,1,1)")
                    db.execSQL("INSERT INTO app_meta (`key`,value,updatedAt) VALUES ('theme','dark',1)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        fun snapshot(db: SupportSQLiteDatabase) = tables.associateWith { table ->
            db.query("SELECT * FROM `$table` ORDER BY rowid").use { c -> buildList {
                while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) })
            } }
        }
        val before = snapshot(helper.writableDatabase); helper.close()
        val db = Room.databaseBuilder(context, TrainingDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(MIGRATION_32_33, TrainingDatabase.MIGRATION_33_34).build()
        try {
            val state = state(db)
            assertEquals(before, snapshot(db.openHelper.writableDatabase))
            UUID.fromString(state.installId); assertNull(state.localBaseBackupId); assertNull(state.accountUserId)
            assertNull(state.lastSuccessfulBackupId); assertNull(state.lastLocalChangeAt)
            assertEquals(1L, state.localRevision); assertTrue(state.cloudBackupPending)
            assertFalse(state.cloudBackupEnabled)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

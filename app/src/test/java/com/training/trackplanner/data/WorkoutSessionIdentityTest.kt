package com.training.trackplanner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
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
class WorkoutSessionIdentityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()
    private val exercise = Exercise("session-test", "Session test", "Strength", isCustom = true)
    private fun database() = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
        .allowMainThreadQueries().build().also { databases += it }
    @After fun close() { databases.forEach { it.close() } }
    private fun identities(db: TrainingDatabase) = WorkoutSourceIdentityProvider(db, db.appMetaDao(), db.workoutDao())
    private fun mutation(db: TrainingDatabase) = RecordMutationService(db, db.exerciseDao(), db.workoutDao(),
        workoutSourceIdentityProvider = identities(db))
    private fun calendar(db: TrainingDatabase) = CalendarRecordService(db, db.workoutDao(),
        workoutSourceIdentityProvider = identities(db))
    private suspend fun entry(db: TrainingDatabase, date: String): WorkoutEntry {
        val id = mutation(db).addWorkoutEntry(date, exercise.stableKey)
        return db.workoutDao().findEntryById(id)!!
    }
    private suspend fun restore(db: TrainingDatabase, csv: String): RecordCsvTransferResult {
        val file = File.createTempFile("session-backup", ".csv", context.cacheDir)
        return try {
            file.writeText(csv)
            TrainingRepository(db, context).importRecordsBackup(Uri.fromFile(file))
        } finally { file.delete() }
    }
    private suspend fun backup(db: TrainingDatabase): String = RecordCsvBackupRestore.wrapWithManifest(
        RecordCsvBackupRestore.buildRestoreCsv(db.workoutDao().allEntriesWithSets(), emptyList(), db.exerciseDao().allExercises()),
        "session-test", 1, emptyMap())

    @Test fun manualCreationJoinsStoredSessionAndEditsPreserveBothIdentities() = runBlocking {
        val db = database(); db.exerciseDao().insertExercise(exercise)
        val first = entry(db, "2026-09-01")
        val second = entry(db, "2026-09-01")
        val other = entry(db, "2026-09-02")
        UUID.fromString(first.sessionStableKey)
        assertEquals(first.sessionStableKey, second.sessionStableKey)
        assertNotEquals(first.sessionStableKey, other.sessionStableKey)
        assertNotEquals(first.backupSourceId, second.backupSourceId)
        mutation(db).updateWorkoutEntry(first.copy(notes = "edit", date = "2026-09-03",
            sessionStableKey = "caller-must-not-replace", backupSourceId = "caller-must-not-replace"))
        val changed = db.workoutDao().findEntryById(first.id)!!
        assertEquals(first.sessionStableKey, changed.sessionStableKey)
        assertEquals(first.backupSourceId, changed.backupSourceId)
        mutation(db).updateSet(db.workoutDao().setsForEntry(first.id).single().copy(reps = 9, confirmed = true))
        assertEquals(first.sessionStableKey, db.workoutDao().findEntryById(first.id)!!.sessionStableKey)
    }

    @Test fun moveCopyRangeAndPushPreserveSessionPartitionsAndEntryIdentityRules() = runBlocking {
        val db = database(); db.exerciseDao().insertExercise(exercise)
        val a = entry(db, "2026-09-01"); val b = entry(db, "2026-09-01")
        val c = entry(db, "2026-09-02")
        val service = calendar(db)
        service.moveDate("2026-09-02", "2026-09-01", CalendarConflictMode.Append)
        val before = db.workoutDao().entriesWithSets("2026-09-01").map { it.entry }
        assertEquals(setOf(a.sessionStableKey, c.sessionStableKey), before.map { it.sessionStableKey }.toSet())
        assertEquals(c.backupSourceId, before.single { it.sessionStableKey == c.sessionStableKey }.backupSourceId)
        service.copyDate("2026-09-01", "2026-09-05", false, CalendarConflictMode.Append)
        val copied = db.workoutDao().entriesWithSets("2026-09-05").map { it.entry }
        assertEquals(listOf(1, 2), copied.groupingBy { it.sessionStableKey }.eachCount().values.sorted())
        assertTrue(copied.none { row -> before.any { it.sessionStableKey == row.sessionStableKey || it.backupSourceId == row.backupSourceId } })
        service.copyDateRangeAsPlan("2026-09-01", "2026-09-01", "2026-09-06", CalendarConflictMode.Append)
        val ranged = db.workoutDao().entriesWithSets("2026-09-06").map { it.entry }
        assertEquals(listOf(1, 2), ranged.groupingBy { it.sessionStableKey }.eachCount().values.sorted())
        assertTrue(ranged.none { row -> (before + copied).any { it.sessionStableKey == row.sessionStableKey } })
        val allBeforePush = db.workoutDao().allEntriesWithSets().associate { it.entry.backupSourceId to it.entry.sessionStableKey }
        service.pushFuturePlan("2026-09-01", 1)
        assertEquals(allBeforePush, db.workoutDao().allEntriesWithSets().associate { it.entry.backupSourceId to it.entry.sessionStableKey })
        assertEquals(a.sessionStableKey, b.sessionStableKey)
    }

    @Test fun partialPlanPushKeepsSessionAndRetainsExistingSplitEntryPolicy() = runBlocking {
        val db = database(); db.exerciseDao().insertExercise(exercise)
        val original = entry(db, "2026-09-01")
        db.workoutDao().updateSet(db.workoutDao().setsForEntry(original.id).single().copy(confirmed = true))
        db.workoutDao().insertSet(WorkoutSet(entryId = original.id, setIndex = 2, confirmed = false))
        calendar(db).pushFuturePlan("2026-09-01", 2)
        val retained = db.workoutDao().entriesWithSets("2026-09-01").single()
        val shifted = db.workoutDao().entriesWithSets("2026-09-03").single()
        assertEquals(original.sessionStableKey, retained.entry.sessionStableKey)
        assertEquals(original.sessionStableKey, shifted.entry.sessionStableKey)
        assertEquals(original.backupSourceId, retained.entry.backupSourceId)
        assertNotEquals(original.backupSourceId, shifted.entry.backupSourceId)
        assertTrue(retained.sets.single().confirmed); assertFalse(shifted.sets.single().confirmed)
    }

    @Test fun eachProgramApplicationCreatesNewSessionsPerScheduledDay() = runBlocking {
        val db = database(); db.exerciseDao().insertExercise(exercise)
        val programId = db.programDao().insertProgram(TrainingProgram(name = "Session program", durationDays = 7))
        for (day in 1..2) for (order in 1..2) {
            db.programDao().insertProgramItem(TrainingProgramItem(programId = programId, weekNumber = 1,
                dayOfWeek = day, orderIndex = order, exerciseStableKey = exercise.stableKey,
                exerciseName = exercise.name, category = exercise.category, setCount = 1, reps = 5))
        }
        val service = ProgramPlanService(db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() },
            workoutSourceIdentityProvider = identities(db))
        service.applyProgramToDates(programId, "2026-09-01", ProgramApplyMode.Append)
        val first = db.workoutDao().allEntriesWithSets().map { it.entry }
        assertEquals(2, first.map { it.sessionStableKey }.toSet().size)
        first.groupBy { it.date }.values.forEach { rows -> assertEquals(1, rows.map { it.sessionStableKey }.toSet().size) }
        service.applyProgramToDates(programId, "2026-09-01", ProgramApplyMode.Append)
        val all = db.workoutDao().allEntriesWithSets().map { it.entry }
        assertEquals(4, all.map { it.sessionStableKey }.toSet().size)
        assertEquals(8, all.map { it.backupSourceId }.toSet().size)
    }

    @Test fun currentBackupRoundTripPreservesExactSessionAndEntryKeys() = runBlocking {
        val source = database(); source.exerciseDao().insertExercise(exercise)
        entry(source, "2026-09-01"); entry(source, "2026-09-01"); entry(source, "2026-09-02")
        calendar(source).moveDate("2026-09-02", "2026-09-01", CalendarConflictMode.Append)
        val csv = backup(source)
        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore
        assertEquals(14, parsed.manifest!!.formatVersion); assertEquals(13, parsed.backupSchemaVersion)
        val target = database()
        assertEquals(3, restore(target, csv).entryCount)
        val expected = source.workoutDao().allEntriesWithSets().associate { it.entry.backupSourceId to it.entry.sessionStableKey }
        assertEquals(expected, target.workoutDao().allEntriesWithSets().associate { it.entry.backupSourceId to it.entry.sessionStableKey })
        assertEquals(0, restore(target, csv).entryCount)
        assertEquals(expected, target.workoutDao().allEntriesWithSets().associate { it.entry.backupSourceId to it.entry.sessionStableKey })
    }

    @Test fun olderFormatsRestoreOneNewSessionPerDateAndKeepSourceIdentity() = runBlocking {
        val source = database(); source.exerciseDao().insertExercise(exercise)
        entry(source, "2026-09-01"); entry(source, "2026-09-01"); entry(source, "2026-09-02")
        // Source-less legacy restore intentionally uses content duplicate detection.
        source.workoutDao().allEntriesWithSets().forEach {
            source.workoutDao().updateEntry(it.entry.copy(notes = "legacy-entry-${it.entry.id}"))
        }
        val rows = source.workoutDao().allEntriesWithSets()
        for (format in listOf(11, 12, 13)) {
            val body = RecordCsvBackupRestore.buildRestoreCsv(rows, emptyList(), listOf(exercise))
                .replace("session_stable_key", "legacy_unused_column")
                .replace(Regex("(?m)^13,"), "${format - 1},")
                .let { if (format == 11) it.replace("entry_source_id", "legacy_unused_source") else it }
            val wrapped = RecordCsvBackupRestore.wrapWithManifest(body, "legacy", 1, emptyMap())
            val header = wrapped.substringBefore('\n').split(',').toMutableList().also { it[1] = format.toString() }.joinToString(",")
            val csv = header + "\n" + wrapped.substringAfter('\n')
            val target = database(); assertEquals(3, restore(target, csv).entryCount)
            val restored = target.workoutDao().allEntriesWithSets().map { it.entry }
            assertEquals(2, restored.map { it.sessionStableKey }.toSet().size)
            restored.groupBy { it.date }.values.forEach { group -> assertEquals(1, group.map { it.sessionStableKey }.toSet().size) }
            restored.forEach { UUID.fromString(it.sessionStableKey) }
            if (format >= 12) {
                assertEquals(rows.map { it.entry.backupSourceId }.toSet(), restored.map { it.backupSourceId }.toSet())
            } else {
                val lineage = target.appMetaDao().value(WorkoutSourceIdentityProvider.SOURCE_DATABASE_LINEAGE_ID)!!
                assertEquals(3, restored.map { it.backupSourceId }.toSet().size)
                assertTrue(restored.all { it.backupSourceId!!.startsWith("$lineage:workout_entry:") })
            }
            assertTrue(restored.none { row -> rows.any { it.entry.sessionStableKey == row.sessionStableKey } })
            assertEquals(0, restore(target, csv).entryCount)
            assertEquals(restored, target.workoutDao().allEntriesWithSets().map { it.entry })
            val upgraded = TrainingRepository(target, context).canonicalRecordsBackup(1234L)
            val current = RecordCsvBackupRestore.parse(upgraded.csv) as RecordCsvImportData.Restore
            assertEquals(14, current.manifest!!.formatVersion)
            assertEquals(13, current.backupSchemaVersion)
            assertEquals(restored.associate { it.backupSourceId to it.sessionStableKey },
                current.toWorkoutGraphs().associate { it.sourceId to it.sessionStableKey })
            assertFalse(upgraded.csv.contains("parent_backup_id"))
            assertFalse(upgraded.csv.contains("backup_id"))
            val clean = database()
            assertEquals(3, restore(clean, upgraded.csv).entryCount)
            val reexported = TrainingRepository(clean, context).canonicalRecordsBackup(1234L)
            val again = RecordCsvBackupRestore.parse(reexported.csv) as RecordCsvImportData.Restore
            assertCanonicalAuthorityEquals(current, again)
        }
    }

    @Test fun missingOrContradictorySessionInNewBackupFailsBeforeMutation() = runBlocking {
        val source = database(); source.exerciseDao().insertExercise(exercise)
        val original = entry(source, "2026-09-01")
        val body = RecordCsvBackupRestore.buildRestoreCsv(source.workoutDao().allEntriesWithSets(), emptyList(), listOf(exercise))
        assertTrue(runCatching { RecordCsvBackupRestore.parse(RecordCsvBackupRestore.wrapWithManifest(
            body.replace(original.sessionStableKey, ""), "test", 1, emptyMap())) }.isFailure)
        val sets = source.workoutDao().setsForEntry(original.id)
        val twoSetBody = RecordCsvBackupRestore.buildRestoreCsv(listOf(WorkoutEntryWithSets(original,
            sets + sets.single().copy(setIndex = 2))), emptyList(), listOf(exercise))
        assertTrue(runCatching { RecordCsvBackupRestore.parse(RecordCsvBackupRestore.wrapWithManifest(
            twoSetBody.replaceFirst(original.sessionStableKey, UUID.randomUUID().toString()), "test", 1, emptyMap())) }.isFailure)
        assertEquals(original, source.workoutDao().findEntryById(original.id))
    }

    @Test fun copyingSplitSessionAcrossDateRangeUsesOneNewSession() = runBlocking {
        val db = database(); db.exerciseDao().insertExercise(exercise)
        val original = entry(db, "2026-09-01")
        db.workoutDao().updateSet(db.workoutDao().setsForEntry(original.id).single().copy(confirmed = true))
        db.workoutDao().insertSet(WorkoutSet(entryId = original.id, setIndex = 2, confirmed = false))
        val service = calendar(db)
        service.pushFuturePlan("2026-09-01", 1)
        service.copyDateRangeAsPlan("2026-09-01", "2026-09-02", "2026-09-10", CalendarConflictMode.Append)
        val copied = listOf("2026-09-10", "2026-09-11").flatMap { db.workoutDao().entriesWithSets(it) }
        assertEquals(2, copied.size)
        assertEquals(1, copied.map { it.entry.sessionStableKey }.toSet().size)
        assertNotEquals(original.sessionStableKey, copied.first().entry.sessionStableKey)
    }

    @Test fun sessionChangesInvalidatePreflightAndLegacyAbsenceDoesNotConflict() = runBlocking {
        val db = database(); db.exerciseDao().insertExercise(exercise)
        val original = entry(db, "2026-09-01")
        val graph = db.workoutDao().allEntriesWithSets().single().toRestoreGraph()
        val changed = graph.copy(sessionStableKey = UUID.randomUUID().toString())
        assertFalse(graph.hasSameContent(changed))
        assertNotEquals(graph.contentToken(), changed.contentToken())
        assertTrue(graph.hasSameContent(graph.copy(sessionStableKey = null)))
        val csv = backup(db)
        val file = File.createTempFile("session-preflight", ".csv", context.cacheDir)
        try {
            file.writeText(csv)
            val repository = TrainingRepository(db, context)
            repository.prepareRecordsRestore(Uri.fromFile(file))
            repository.planRecordsRestore(WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES,
                ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES)
            db.workoutDao().updateEntry(original.copy(sessionStableKey = changed.sessionStableKey!!))
            assertTrue(runCatching { repository.confirmRecordsRestore() }.isFailure)
            assertEquals(changed.sessionStableKey, db.workoutDao().findEntryById(original.id)!!.sessionStableKey)
        } finally { file.delete() }
    }

    @Test fun legacy13UpgradePreservesCompleteProgramGraphAndAuthoritativeSections() = runBlocking {
        val source = database()
        source.exerciseDao().insertExercise(exercise.copy(activityKind = "TRAINING_EXERCISE", volumeLoadEligible = true))
        val programId = source.programDao().insertProgram(TrainingProgram(name = "Portable program", durationDays = 21))
        for (week in 1..3) for (order in 1..2) {
            val itemId = source.programDao().insertProgramItem(TrainingProgramItem(programId = programId,
                weekNumber = week, dayOfWeek = 1, orderIndex = order, exerciseStableKey = exercise.stableKey,
                exerciseName = exercise.name, category = exercise.category, setCount = 2, reps = 5, weightKg = 100.0))
            source.programDao().insertProgramItemSets(listOf(
                TrainingProgramItemSet(programItemId = itemId, setIndex = 1, reps = 5, weightKg = 100.0),
                TrainingProgramItemSet(programItemId = itemId, setIndex = 2, reps = 5, weightKg = 100.0)))
        }
        val progression = ProgramProgressionService(source)
        progression.author(programId)
        source.programProgressionDao().tracks().forEach {
            source.programProgressionDao().putTrack(it.copy(mode = ProgressionMode.CUSTOM, rule = it.rule.copy(incrementKg = 2.5)))
        }
        ProgramPlanService(source, source.exerciseDao(), source.workoutDao(), source.programDao(), { it }, { emptySet() },
            identities(source)).applyProgramToDates(programId, "2026-09-01", ProgramApplyMode.Append)
        source.workoutDao().entriesWithSets("2026-09-01").flatMap { it.sets }.forEach {
            source.workoutDao().updateSet(it.copy(confirmed = true, rpe = 7.0))
        }
        progression.refresh()
        source.programDao().upsertProgramTombstone(TrainingProgramTombstone("deleted-program", 3L))
        source.dailyMetricDao().upsert(DailyMetric("2026-09-01", sleepHours = 8.0, bodyWeightKg = 75.0, updatedAt = 4L))
        source.dailyCheckInDao().upsert(DailyCheckIn("2026-09-01", overallFatigue = 2, note = "check-in", createdAt = 4L, updatedAt = 4L))
        source.initialUserProfileDao().upsert(InitialUserProfile(bodyWeightKg = 75.0, heightCm = 180.0))
        val parent = source.workoutDao().allEntriesWithSets().first().entry.id
        source.smashSpeedDao().upsert(SmashSpeedRecord(date = "2026-09-01", speedKmh = 250.0, parentWorkoutEntryId = parent))
        source.appMetaDao().upsert(AppMeta(PersonalizedProgramPlanningService.PREFERENCES_KEY, "{}", 4L))
        val field = ExerciseMetadataFieldPolicyRegistry.definition("exercise.description")!!
        source.exerciseMetadataUserOverrideDao().upsert(ExerciseMetadataUserOverrideEntity(
            stableKey = exercise.stableKey, fieldScope = field.fieldScope.name, fieldKey = "exercise.description",
            valueEncoding = field.valueEncoding.name, value = "Portable description", isExplicitEmpty = false,
            source = ExerciseMetadataOverrideSource.USER_EDIT.name, semanticCanonicalRevisionAtEdit = "test", updatedAt = 4L))
        val sourceCanonical = TrainingRepository(source, context).canonicalRecordsBackup(1234L)
        val body = sourceCanonical.csv.substringAfter('\n')
            .replace("session_stable_key", "legacy_unused_column").replace(Regex("(?m)^13,"), "12,")
        val wrapped = RecordCsvBackupRestore.wrapWithManifest(body, "legacy13", 1, emptyMap())
        val header = wrapped.substringBefore('\n').split(',').toMutableList().also { it[1] = "13" }.joinToString(",")
        val legacy = header + "\n" + wrapped.substringAfter('\n')
        val historical = RecordCsvBackupRestore.parse(legacy) as RecordCsvImportData.Restore
        assertEquals(13, historical.manifest!!.formatVersion); assertEquals(12, historical.backupSchemaVersion)
        assertEquals(ProgramProgressionBackup.types, historical.progressionRows.map { it.type }.toSet())
        val upgradedDb = database()
        assertEquals(6, restore(upgradedDb, legacy).entryCount)
        val canonical = TrainingRepository(upgradedDb, context).canonicalRecordsBackup(1234L)
        assertArrayEquals(canonical.csv.toByteArray(Charsets.UTF_8), canonical.utf8Bytes())
        val normalized = RecordCsvBackupRestore.parse(canonical.csv) as RecordCsvImportData.Restore
        assertEquals(14, normalized.manifest!!.formatVersion); assertEquals(13, normalized.backupSchemaVersion)
        assertEquals(historical.programSnapshot, normalized.programSnapshot)
        assertEquals(historical.checkInRows.single().createdAt, normalized.checkInRows.single().createdAt)
        assertEquals(historical.toWorkoutGraphs().map { it.sourceId }.toSet(), normalized.toWorkoutGraphs().map { it.sourceId }.toSet())
        assertEquals(3, normalized.toWorkoutGraphs().map { it.sessionStableKey }.toSet().size)
        normalized.toWorkoutGraphs().groupBy { it.date }.values.forEach {
            assertEquals(1, it.map { row -> row.sessionStableKey }.toSet().size)
        }
        assertTrue(normalized.metadataSnapshotRows.isNotEmpty()); assertTrue(normalized.metadataUserOverrideRows.isNotEmpty())
        assertTrue(normalized.portableAppMetaRows.isNotEmpty()); assertTrue(normalized.progressionRows.isNotEmpty())
        val clean = database(); restore(clean, canonical.csv)
        val again = TrainingRepository(clean, context).canonicalRecordsBackup(1234L)
        assertCanonicalAuthorityEquals(normalized, RecordCsvBackupRestore.parse(again.csv) as RecordCsvImportData.Restore)
        // Manual file export is a transport for exactly the same canonical body.
        val file = File.createTempFile("canonical-manual", ".csv", context.cacheDir)
        try {
            TrainingRepository(clean, context).exportRecordsBackup(Uri.fromFile(file))
            assertEquals(again.csv.substringAfter('\n'), file.readText().substringAfter('\n'))
        } finally { file.delete() }
        assertFalse(again.csv.contains("parent_backup_id")); assertFalse(again.csv.contains("backup_id"))
        val existingTarget = database()
        existingTarget.dailyCheckInDao().upsert(DailyCheckIn("2026-09-01", createdAt = 99L, updatedAt = 99L))
        restore(existingTarget, canonical.csv)
        assertEquals(99L, existingTarget.dailyCheckInDao().getForDate("2026-09-01")!!.createdAt)
    }

    @Test fun frozenSeptember13BackupPreservesIdentityThroughCurrentExportAndCleanRestore() = runBlocking {
        val csv = javaClass.getResource("/backup-history/september-format-13.csv")!!.readText(Charsets.UTF_8)
        val historical = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore
        assertEquals(13, historical.manifest!!.formatVersion)
        assertEquals(12, historical.backupSchemaVersion)
        assertFalse("session_stable_key" in csv.lineSequence().drop(1).first().split(','))
        assertTrue(historical.setRows.all { it.sessionStableKey == null && !it.entrySourceId.isNullOrBlank() })
        assertEquals(ProgramProgressionBackup.types, historical.progressionRows.map { it.type }.toSet())
        val historicalGraphs = historical.toWorkoutGraphs()
        assertEquals(6, historicalGraphs.size)
        val target = database()
        assertEquals(6, restore(target, csv).entryCount)
        val restored = target.workoutDao().allEntriesWithSets().map { it.entry }
        restored.forEach { UUID.fromString(it.sessionStableKey) }
        assertEquals(3, restored.map { it.sessionStableKey }.toSet().size)
        restored.groupBy { it.date }.values.forEach { sameDate ->
            assertEquals(1, sameDate.map { it.sessionStableKey }.toSet().size)
        }
        val canonical = TrainingRepository(target, context).canonicalRecordsBackup(1234L)
        val normalized = RecordCsvBackupRestore.parse(canonical.csv) as RecordCsvImportData.Restore
        assertEquals(14, normalized.manifest!!.formatVersion)
        assertEquals(13, normalized.backupSchemaVersion)
        assertTrue(normalized.setRows.all { !it.sessionStableKey.isNullOrBlank() })
        assertEquals(restored.associate { it.backupSourceId to it.sessionStableKey },
            normalized.toWorkoutGraphs().associate { it.sourceId to it.sessionStableKey })
        // Compare source IDs and complete workout values while allowing the new session field.
        assertEquals(historicalGraphs.associate { it.sourceId to it.contentToken() },
            normalized.toWorkoutGraphs().associate { it.sourceId to it.copy(sessionStableKey = null).contentToken() })
        assertEquals(historical.exerciseRows.map { it.stableKey }.toSet(), normalized.exerciseRows.map { it.stableKey }.toSet())
        assertEquals(historical.programSnapshot, normalized.programSnapshot)
        assertEquals(historical.progressionRows, normalized.progressionRows)
        assertEquals(historical.metadataSnapshotRows, normalized.metadataSnapshotRows)
        assertEquals(historical.metadataUserOverrideRows, normalized.metadataUserOverrideRows)
        assertEquals(historical.portableAppMetaRows, normalized.portableAppMetaRows)
        assertEquals(historical.checkInRows, normalized.checkInRows)
        assertFalse(canonical.csv.contains("parent_backup_id"))
        assertFalse(canonical.csv.contains("backup_id"))
        val clean = database()
        assertEquals(6, restore(clean, canonical.csv).entryCount)
        assertEquals(restored.associate { it.backupSourceId to it.sessionStableKey },
            clean.workoutDao().allEntriesWithSets().associate { it.entry.backupSourceId to it.entry.sessionStableKey })
        val again = TrainingRepository(clean, context).canonicalRecordsBackup(1234L)
        assertCanonicalAuthorityEquals(normalized, RecordCsvBackupRestore.parse(again.csv) as RecordCsvImportData.Restore)
    }

    private fun assertCanonicalAuthorityEquals(expected: RecordCsvImportData.Restore, actual: RecordCsvImportData.Restore) {
        assertEquals(expected.toWorkoutGraphs().associate { it.sourceId to it.contentToken() },
            actual.toWorkoutGraphs().associate { it.sourceId to it.contentToken() })
        assertEquals(expected.programSnapshot, actual.programSnapshot)
        assertEquals(expected.exerciseRows, actual.exerciseRows)
        assertEquals(expected.profileRows, actual.profileRows)
        assertEquals(expected.dailyRows, actual.dailyRows)
        assertEquals(expected.checkInRows, actual.checkInRows)
        assertEquals(expected.smashSpeedRows.map { it.copy(parentWorkoutEntryId = null) },
            actual.smashSpeedRows.map { it.copy(parentWorkoutEntryId = null) })
        assertEquals(expected.runtimeMetadataRows, actual.runtimeMetadataRows)
        assertEquals(expected.metadataSnapshotRows, actual.metadataSnapshotRows)
        assertEquals(expected.metadataUserOverrideRows, actual.metadataUserOverrideRows)
        assertEquals(expected.portableAppMetaRows, actual.portableAppMetaRows)
        assertEquals(expected.progressionRows, actual.progressionRows)
        // Existing restore policy rebuilds derived strength state from raw records;
        // rebuild timestamps/event UUIDs are not portable user-data authority.
        assertEquals(expected.posteriorFormatPresent, actual.posteriorFormatPresent)
        assertEquals(expected.posteriorRevisions.map { it.revisionKey }, actual.posteriorRevisions.map { it.revisionKey })
        fun derivedCounts(data: RecordCsvImportData.Restore) = listOf(data.posteriorEvents.size,
            data.posteriorHistory.size, data.posteriorModelStates.size, data.curvePosteriors.size,
            data.posteriorEvidence.size, data.posteriorRevisions.size, data.posteriorLocalStates.size,
            data.posteriorLocalHistory.size, data.posteriorProxyHistory.size)
        assertEquals(derivedCounts(expected), derivedCounts(actual))
    }

    @Test fun room31MigrationGroupsLegacyDatesAndValidatesRoom32Schema() = runBlocking {
        val name = "session-migration-${UUID.randomUUID()}"
        val schemaFile = listOf(File("schemas/com.training.trackplanner.data.TrainingDatabase/31.json"),
            File("app/schemas/com.training.trackplanner.data.TrainingDatabase/31.json")).first { it.exists() }
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(31) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = entity.getJSONArray("indices")
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                        if (table == "exercises" || table == "workout_entries") {
                            val fields = entity.getJSONArray("fields")
                            val columns = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
                            val values = (0 until fields.length()).map { index ->
                                val field = fields.getJSONObject(index)
                                when {
                                    !field.getBoolean("notNull") -> "NULL"
                                    field.getString("affinity") == "TEXT" -> "''"
                                    else -> "0"
                                }
                            }.toMutableList()
                            if (table == "exercises") {
                                values[columns.indexOf("stableKey")] = "'legacy-exercise'"
                                db.execSQL("INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${values.joinToString()})")
                            } else for (id in 1..3) {
                                values[columns.indexOf("id")] = id.toString()
                                values[columns.indexOf("exerciseStableKey")] = "'legacy-exercise'"
                                values[columns.indexOf("date")] = if (id < 3) "'2026-09-01'" else "'2026-09-02'"
                                values[columns.indexOf("backupSourceId")] = "'source-$id'"
                                values[columns.indexOf("notes")] = "'preserved-$id'"
                                db.execSQL("INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${values.joinToString()})")
                            }
                        }
                    }
                    db.execSQL("INSERT INTO workout_sets (id,entryId,setIndex,reps,weightKg,seconds,confirmed,manualWeight) VALUES (1,1,1,5,100,0,1,1)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase; helper.close()
        val db = Room.databaseBuilder(context, TrainingDatabase::class.java, name)
            .allowMainThreadQueries().addMigrations(MIGRATION_31_32, MIGRATION_32_33, TrainingDatabase.MIGRATION_33_34).build().also { databases += it }
        val migrated = db.workoutDao().allEntriesWithSets().sortedBy { it.entry.id }
        assertEquals(3, migrated.size)
        assertEquals(migrated[0].entry.sessionStableKey, migrated[1].entry.sessionStableKey)
        assertNotEquals(migrated[0].entry.sessionStableKey, migrated[2].entry.sessionStableKey)
        migrated.forEachIndexed { index, row ->
            UUID.fromString(row.entry.sessionStableKey)
            assertEquals("source-${index + 1}", row.entry.backupSourceId)
            assertEquals("preserved-${index + 1}", row.entry.notes)
        }
        assertTrue(migrated.first().sets.single().confirmed)
        assertEquals(100.0, migrated.first().sets.single().weightKg, 0.0)
        db.close(); databases.remove(db)
        val reopened = Room.databaseBuilder(context, TrainingDatabase::class.java, name).allowMainThreadQueries().build()
        try { assertEquals(migrated, reopened.workoutDao().allEntriesWithSets().sortedBy { it.entry.id }) }
        finally { reopened.close(); context.deleteDatabase(name) }
    }
}

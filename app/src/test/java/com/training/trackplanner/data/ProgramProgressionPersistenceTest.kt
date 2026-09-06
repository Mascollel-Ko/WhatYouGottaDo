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
class ProgramProgressionPersistenceTest {
    private val databases = mutableListOf<TrainingDatabase>()
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrainingDatabase::class.java).allowMainThreadQueries().build().also { databases += it }
    @After fun close() { databases.forEach { it.close() } }
    private fun service(db: TrainingDatabase) = ProgramPlanService(db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() })
    private suspend fun fixture(db: TrainingDatabase): Long {
        db.exerciseDao().insertExercise(Exercise("exact", "Squat", "", activityKind = "TRAINING_EXERCISE", volumeLoadEligible = true))
        val id = db.programDao().insertProgram(TrainingProgram(name = "Original program", durationDays = 21))
        (1..3).forEach { week -> db.programDao().insertProgramItem(TrainingProgramItem(programId = id, weekNumber = week, dayOfWeek = 1, orderIndex = 1,
            exerciseStableKey = "exact", exerciseName = "Squat", category = "", setCount = 3, reps = 5, weightKg = 140.0)) }
        ProgramProgressionService(db).author(id)
        val dao = db.programProgressionDao()
        val track = dao.tracks().single()
        dao.putTrack(track.copy(mode = ProgressionMode.CUSTOM, rule = track.rule.copy(incrementKg = 2.5)))
        return id
    }
    private suspend fun apply(db: TrainingDatabase, id: Long, date: String = "2026-09-01") {
        service(db).applyProgramToDates(id, date, ProgramApplyMode.Append)
        db.workoutDao().allEntriesWithSets().forEach { db.workoutDao().updateEntry(it.entry.copy(backupSourceId = "source-${it.entry.id}")) }
    }
    private suspend fun confirm(db: TrainingDatabase, entryId: Long) {
        db.workoutDao().setsForEntry(entryId).forEach { db.workoutDao().updateSet(it.copy(confirmed = true, rpe = 7.0)) }
        ProgramProgressionService(db).refresh()
    }
    @Test fun authorTracksBeforeApplyAndTwoApplicationsRemainSeparateAfterTemplateDelete() = runBlocking {
        val db = database(); val id = fixture(db)
        assertEquals(3, db.programProgressionDao().items().size)
        assertEquals(1, db.programProgressionDao().tracks().size)
        apply(db, id); apply(db, id, "2026-10-01")
        val links = db.programProgressionDao().links()
        assertEquals(2, links.map { it.applicationId }.distinct().size)
        service(db).deleteProgram(id)
        assertEquals(links, db.programProgressionDao().links())
        assertEquals("Original program", links.first().programName)
    }
    @Test fun suggestionsPendingUntilResolutionAndOnlyImmediateTargetChanges() = runBlocking {
        val db = database(); apply(db, fixture(db))
        val entries = db.workoutDao().allEntriesWithSets().sortedBy { it.entry.date }
        // Existing target145 differs from previous actual140; accepting142.5 is an increase.
        val mutation = RecordMutationService(db, db.exerciseDao(), db.workoutDao())
        entries[1].sets.forEach { mutation.updateSet(it.copy(weightKg = 145.0)) }
        confirm(db, entries[0].entry.id)
        val dao = db.programProgressionDao(); val suggestion = dao.suggestions().single()
        assertEquals(ProgressionDirection.INCREASE, suggestion.direction)
        assertEquals(145.0, suggestion.currentPlanKg!!, 0.0)
        assertEquals(142.5, suggestion.suggestedKg!!, 0.0)
        assertEquals(145.0, db.workoutDao().setsForEntry(entries[1].entry.id).first().weightKg, 0.0)
        ProgramProgressionService(db).refresh() // dismiss/no resolution leaves pending
        assertEquals(ProgressionResolution.PENDING, dao.suggestions().single().resolution)
        assertTrue(ProgramProgressionService(db).resolve(suggestion.id, ProgressionResolution.ACCEPTED))
        assertEquals(142.5, db.workoutDao().setsForEntry(entries[1].entry.id).first().weightKg, 0.0)
        assertEquals(140.0, db.workoutDao().setsForEntry(entries[2].entry.id).first().weightKg, 0.0)
        val target = dao.prescriptions(entries[1].entry.id).first()
        assertEquals(140.0, target.originalKg, 0.0); assertEquals(142.5, target.plannedKg, 0.0)
        assertFalse(ProgramProgressionService(db).resolve(suggestion.id, ProgressionResolution.ACCEPTED))
    }
    @Test fun sourceEditsStalePendingAndKeepAndManualAreDurable() = runBlocking {
        val db = database(); apply(db, fixture(db))
        val entries = db.workoutDao().allEntriesWithSets().sortedBy { it.entry.date }
        confirm(db, entries[0].entry.id)
        val old = db.programProgressionDao().suggestions().single()
        db.workoutDao().setsForEntry(entries[0].entry.id).forEach { db.workoutDao().updateSet(it.copy(rpe = 10.0)) }
        val progression = ProgramProgressionService(db)
        assertFalse(progression.resolve(old.id, ProgressionResolution.ACCEPTED))
        assertEquals(ProgressionResolution.STALE, db.programProgressionDao().suggestions().first { it.id == old.id }.resolution)
        val fresh = db.programProgressionDao().suggestions().single { it.resolution == ProgressionResolution.PENDING }
        assertTrue(progression.resolve(fresh.id, ProgressionResolution.MANUAL_OVERRIDE, 141.0))
        assertEquals(141.0, db.workoutDao().setsForEntry(entries[1].entry.id).first().weightKg, 0.0)
        confirm(db, entries[1].entry.id)
        val next = db.programProgressionDao().suggestions().single { it.resolution == ProgressionResolution.PENDING }
        assertTrue(progression.resolve(next.id, ProgressionResolution.KEPT_CURRENT_PLAN))
        assertEquals(140.0, db.workoutDao().setsForEntry(entries[2].entry.id).first().weightKg, 0.0)
    }
    @Test fun overrideSurvivesAuthoringAndTemplateEditDoesNotRewriteApplied() = runBlocking {
        val db = database(); val id = fixture(db); val dao = db.programProgressionDao()
        val binding = dao.items().first()
        val progression = ProgramProgressionService(db)
        progression.configure(binding.programItemId, ProgressionLinkMode.EXISTING, binding.trackId, ProgressionRole.ASSISTANCE, ProgressionMode.CUSTOM, ProgressionRule(incrementKg = 1.0))
        progression.author(id)
        assertEquals(ProgressionRole.ASSISTANCE, dao.tracks().single().role)
        apply(db, id)
        val before = dao.links(); val prescriptions = dao.prescriptions()
        val item = db.programDao().itemsForProgram(id).first()
        service(db).updateProgramItem(item.copy(weightKg = 200.0))
        assertEquals(before, dao.links()); assertEquals(prescriptions, dao.prescriptions())
    }
    @Test fun movePushRetainLineageWhileGenericCopyDetaches() = runBlocking {
        val db = database(); apply(db, fixture(db))
        val dao = db.programProgressionDao(); val before = dao.links().first()
        val calendar = CalendarRecordService(db, db.workoutDao())
        calendar.moveDate("2026-09-01", "2026-09-02", CalendarConflictMode.Append)
        val moved = db.workoutDao().entriesWithSets("2026-09-02").single()
        assertEquals(before.applicationId, dao.link(moved.entry.id)!!.applicationId)
        assertEquals(before.sequence, dao.link(moved.entry.id)!!.sequence)
        calendar.copyDate("2026-09-02", "2026-09-03", false, CalendarConflictMode.Append)
        assertNull(dao.link(db.workoutDao().entriesWithSets("2026-09-03").single().entry.id))
        calendar.pushFuturePlan("2026-09-02", 1)
        assertEquals(3, dao.links().size)
        assertEquals(9, dao.prescriptions().size)
    }
    @Test fun deletedTemplateGraphRoundTripsWithEntryIdRemapAndResolution() = runBlocking {
        val db = database(); val id = fixture(db); apply(db, id)
        val entries = db.workoutDao().allEntriesWithSets().sortedBy { it.entry.date }
        confirm(db, entries.first().entry.id)
        val suggestion = db.programProgressionDao().suggestions().single()
        ProgramProgressionService(db).resolve(suggestion.id, ProgressionResolution.KEPT_CURRENT_PLAN)
        service(db).deleteProgram(id)
        val rows = ProgramProgressionBackup.export(db)
        ProgramProgressionBackup.validate(rows)
        val body = RecordCsvBackupRestore.buildRestoreCsv(db.workoutDao().allEntriesWithSets(), emptyList(), db.exerciseDao().allExercises(), progressionRows = rows)
        val csv = RecordCsvBackupRestore.wrapWithManifest(body, "test", 1, mapOf("execution_graph_row" to rows.size))
        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore
        assertEquals(rows, parsed.progressionRows)
        assertEquals(13, parsed.manifest!!.formatVersion)
        val restored = database()
        restored.exerciseDao().insertExercise(db.exerciseDao().allExercises().single())
        val mapping = entries.associate { row ->
            val entryId = restored.workoutDao().insertEntry(row.entry.copy(id = row.entry.id + 100))
            db.workoutDao().setsForEntry(row.entry.id).forEach { restored.workoutDao().insertSet(it.copy(id = 0, entryId = entryId)) }
            row.entry.backupSourceId!! to entryId
        }
        ProgramProgressionBackup.restore(restored, parsed.progressionRows, mapping)
        assertEquals(db.programProgressionDao().tracks(), restored.programProgressionDao().tracks())
        assertEquals(db.programProgressionDao().applications(), restored.programProgressionDao().applications())
        assertEquals(db.programProgressionDao().links().map { it.copy(entryId = it.entryId + 100) }, restored.programProgressionDao().links())
        assertEquals(ProgressionResolution.KEPT_CURRENT_PLAN, restored.programProgressionDao().suggestions().single().resolution)
        assertEquals(9, restored.programProgressionDao().prescriptions().size)
        assertTrue(restored.programDao().allPrograms().isEmpty())
    }

    @Test fun currentPlanStructureEditsNeverEraseOriginalAndActualConfirmationFreezesTargets() = runBlocking {
        val db = database(); apply(db, fixture(db))
        val entry = db.workoutDao().allEntriesWithSets().first().entry
        val mutation = RecordMutationService(db, db.exerciseDao(), db.workoutDao())
        mutation.deleteSet(db.workoutDao().setsForEntry(entry.id)[1])
        val deleted = db.programProgressionDao().prescriptions(entry.id)
        assertEquals(3, deleted.count { it.originalExists })
        assertEquals(2, deleted.count { it.plannedSetIndex != null })
        mutation.addSet(entry)
        val added = db.programProgressionDao().prescriptions(entry.id)
        assertEquals(3, added.count { it.originalExists })
        assertEquals(1, added.count { !it.originalExists })
        val set = db.workoutDao().setsForEntry(entry.id).first()
        mutation.updateSet(set.copy(weightKg = 137.5, confirmed = true, rpe = 8.0))
        assertEquals(140.0, db.programProgressionDao().prescriptions(entry.id).first().plannedKg, 0.0)
        assertEquals(137.5, db.workoutDao().findSetById(set.id)!!.weightKg, 0.0)
    }

    @Test fun snapshotFromExistingCanonicalE1rmDoesNotDriftWhenLaterRecordsChange() = runBlocking {
        val db = database()
        db.exerciseDao().insertExercise(Exercise("rm", "Name is irrelevant", "", activityKind = "TRAINING_EXERCISE", progressMetricType = "ESTIMATED_1RM", estimated1RmEligible = true))
        val record = db.workoutDao().insertEntry(WorkoutEntry(date = "2026-01-01", exerciseStableKey = "rm", exerciseName = "Anything", category = ""))
        val set = db.workoutDao().insertSet(WorkoutSet(entryId = record, setIndex = 1, reps = 5, weightKg = 100.0, confirmed = true))
        val id = db.programDao().insertProgram(TrainingProgram(name = "RM snapshot", durationDays = 7))
        db.programDao().insertProgramItem(TrainingProgramItem(programId = id, weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = "rm", exerciseName = "Anything", category = "", setCount = 3, reps = 5, weightKg = 80.0))
        val progression = ProgramProgressionService(db); progression.author(id)
        val before = db.programProgressionDao().items().single().signature
        assertNotNull(before.oneRmSnapshotKg)
        db.workoutDao().updateSet(db.workoutDao().findSetById(set)!!.copy(weightKg = 200.0, rpe = 10.0))
        progression.author(id)
        assertEquals(before, db.programProgressionDao().items().single().signature)
    }

    @Test fun malformedWireEnumsFailClosed() = runBlocking {
        val db = database(); apply(db, fixture(db))
        val rows = ProgramProgressionBackup.export(db).map { row ->
            if (row.type == "execution_track") row.copy(payload = org.json.JSONObject(row.payload).put("mode", "ARBITRARY").toString()) else row
        }
        assertTrue(runCatching { ProgramProgressionBackup.validate(rows) }.isFailure)
    }

    @Test fun autoResetRejoinsCompatibleTrackAndMissingTargetSeparatesForDirectReview() = runBlocking {
        val db = database(); val id = fixture(db); val dao = db.programProgressionDao()
        val first = dao.items().first(); val progression = ProgramProgressionService(db)
        progression.configure(first.programItemId, ProgressionLinkMode.SEPARATE, null, ProgressionRole.ASSISTANCE, ProgressionMode.APP, ProgressionRule())
        assertNotEquals(first.trackId, dao.items().first { it.programItemId == first.programItemId }.trackId)
        progression.configure(first.programItemId, ProgressionLinkMode.AUTO, null, ProgressionRole.AUTO, ProgressionMode.APP, ProgressionRule())
        assertEquals(first.trackId, dao.items().first { it.programItemId == first.programItemId }.trackId)
        progression.configure(first.programItemId, ProgressionLinkMode.EXISTING, "deleted-draft-target", ProgressionRole.AUTO, ProgressionMode.APP, ProgressionRule())
        progression.author(id)
        val changed = dao.items().first { it.programItemId == first.programItemId }
        assertEquals(ProgressionLinkMode.SEPARATE, changed.linkMode)
        assertEquals(ProgressionMode.DIRECT, dao.tracks().single { it.id == changed.trackId }.mode)
    }

    @Test fun savedGeneratedEditPreservesLogicalIdentityStyleAndExplicitOverride() = runBlocking {
        val db = database(); val id = fixture(db); val dao = db.programProgressionDao()
        val before = dao.items().first()
        dao.putItem(before.copy(signature = before.signature.copy(style = "HEAVY_LIGHT_MEDIUM", variant = "HEAVY", plannerRole = ProgressionRole.MAIN)))
        ProgramProgressionService(db).configure(before.programItemId, ProgressionLinkMode.EXISTING, before.trackId, ProgressionRole.ASSISTANCE, ProgressionMode.CUSTOM, ProgressionRule(incrementKg = 1.0))
        val item = db.programDao().itemsForProgram(id).first { it.id == before.programItemId }
        val draft = ProgramSkeletonItem(localId = "existing-${item.id}", weekNumber = item.weekNumber, dayOfWeek = item.dayOfWeek, orderIndex = item.orderIndex,
            exerciseStableKey = item.exerciseStableKey, exerciseName = item.exerciseName, category = item.category, setCount = 3, reps = 5, weightKg = 140.0,
            seconds = 0, restSeconds = 60, prescription = "", selectionReason = "", weightSource = "MANUAL_INPUT",
            trainingSlot = ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name, dayIntensity = ProgramDayIntensity.MODERATE.name)
        val request = ProgramSkeletonRequest("Edited", ProgramGoal.STRENGTH, 3, 45, emptySet(), "", 0.4, "AUTO", ProgramPeriodizationType.AUTO)
        service(db).saveGeneratedProgram(id, GeneratedProgramSkeleton("Edited", 21, request, ProgramPeriodizationType.AUTO, emptyList(), listOf(draft)))
        val after = dao.items().single()
        assertEquals(before.logicalItemId, after.logicalItemId)
        assertEquals("HEAVY_LIGHT_MEDIUM", after.signature.style)
        assertEquals("HEAVY", after.signature.variant)
        assertEquals(ProgressionRole.ASSISTANCE, dao.tracks().single { it.id == after.trackId }.role)
        assertEquals(1.0, dao.tracks().single { it.id == after.trackId }.rule.incrementKg!!, 0.0)
    }

    @Test fun graphRejectsMismatchedExerciseSourceAndDanglingPrescriptions() = runBlocking {
        val db = database(); apply(db, fixture(db))
        val rows = ProgramProgressionBackup.export(db)
        val mismatched = db.workoutDao().allEntriesWithSets().associate { it.entry.backupSourceId!! to "other-exact-key" }
        assertTrue(runCatching { ProgramProgressionBackup.validate(rows, mismatched) }.isFailure)
        assertTrue(runCatching { ProgramProgressionBackup.validate(rows.filterNot { it.type == "execution_link" }) }.isFailure)
    }

    @Test fun negativeSuggestedLoadInBackupFailsClosed() = runBlocking {
        val db = database(); apply(db, fixture(db))
        confirm(db, db.workoutDao().allEntriesWithSets().minBy { it.entry.date }.entry.id)
        val rows = ProgramProgressionBackup.export(db).map { row ->
            if (row.type == "execution_suggestion") row.copy(payload = org.json.JSONObject(row.payload).put("suggestedKg", -2.5).toString()) else row
        }
        assertTrue(runCatching { ProgramProgressionBackup.validate(rows) }.isFailure)
    }

    @Test fun format12Schema11WithoutExecutionRowsIsStillReadable() {
        val body = RecordCsvBackupRestore.buildRestoreCsv(emptyList(), emptyList(), emptyList())
            .replace(Regex("(?m)^12,"), "11,")
        val wrapped = RecordCsvBackupRestore.wrapWithManifest(body, "old", 1, emptyMap(), capabilities = setOf(RecordCsvBackupRestore.EXPLICIT_METADATA_USER_OVERRIDES_CAPABILITY))
        val header = wrapped.substringBefore('\n').split(',').toMutableList().also { it[1] = "12" }.joinToString(",")
        val parsed = RecordCsvBackupRestore.parse(header + "\n" + wrapped.substringAfter('\n')) as RecordCsvImportData.Restore
        assertEquals(11, parsed.backupSchemaVersion)
        assertTrue(parsed.progressionRows.isEmpty())
    }
}

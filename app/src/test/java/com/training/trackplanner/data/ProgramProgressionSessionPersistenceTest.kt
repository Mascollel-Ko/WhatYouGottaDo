package com.training.trackplanner.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.skeletonFromProgram
import com.training.trackplanner.data.personalized.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ProgramProgressionSessionPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databases = mutableListOf<TrainingDatabase>()
    private val databaseRoot = java.nio.file.Files.createTempDirectory("wgtd-sessions-").toFile()
    private val databaseContext = object : ContextWrapper(context) {
        override fun getDatabasePath(name: String) = java.io.File(databaseRoot, name)
    }
    private fun database(name: String? = null) = (if (name == null) Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
        else Room.databaseBuilder(databaseContext, TrainingDatabase::class.java, name)).allowMainThreadQueries().build().also { databases += it }
    @After fun close() { databases.forEach { it.close() }; databaseRoot.listFiles().orEmpty().forEach { it.delete() }; databaseRoot.delete() }
    private fun service(db: TrainingDatabase) = ProgramPlanService(db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() })
    private suspend fun seed(db: TrainingDatabase) { for (key in listOf("squat", "bench")) db.exerciseDao().insertExercise(Exercise(key, key, "", activityKind = "TRAINING_EXERCISE", volumeLoadEligible = true)) }
    private suspend fun editor(db: TrainingDatabase, id: Long) = skeletonFromProgram(TrainingRepository(db, context).programEditorSnapshot(id))
    private fun linked(mode: ProgressionMode = ProgressionMode.CUSTOM): GeneratedProgramSkeleton {
        var draft = manualSessionDraft().choose("item-0", ProgressionLinkMode.SEPARATE, mode = mode)
        draft = draft.choose("item-2", ProgressionLinkMode.EXISTING, draft.sessionKey("item-0"), mode = mode)
        return draft.choose("item-1", ProgressionLinkMode.SEPARATE, role = ProgressionRole.ASSISTANCE, mode = mode)
    }
    @Test fun generatedContinuitySplitRetainsBothOccurrencesAcrossSaveReopenAndApply() = runBlocking {
        val f = PostGenerationFixture
        val snapshot = f.snapshot()
        val allocation = SplitAwareContinuityAllocation(PersonalizedPrescriptionPlanner()).allocate(snapshot, f.state(),
            listOf(f.source("press", 4)), emptyList(), emptyList(), 2, 20)
        val rows = allocation.days.flatMap { (day, atoms) -> atoms.mapIndexed { index, atom ->
            residualItem(snapshot, atom.timed.item, atom.timed.prescription, "split_${day}_$index", day, index + 1)
                .copy(progressionRole = ProgressionRole.MAIN)
        } }
        assertEquals(listOf(2, 2), rows.map { it.setCount })
        var draft = f.plan(rows, listOf(1, 2), 20).reconcileProgression(setOf("press"))
        val firstId = draft.items.first().localId
        draft = draft.choose(firstId, ProgressionLinkMode.SEPARATE, role = ProgressionRole.ASSISTANCE, mode = ProgressionMode.CUSTOM)
        val explicitKey = draft.sessionKey(firstId)
        val name = "generated-split-${UUID.randomUUID()}.db"
        val first = database(name)
        first.exerciseDao().insertExercise(Exercise("press", "Press", "Strength", activityKind = "TRAINING_EXERCISE", volumeLoadEligible = true))
        val id = service(first).saveGeneratedProgram(null, draft)
        first.close()
        val reopened = database(name)
        val loaded = editor(reopened, id)
        assertEquals(6, loaded.items.size)
        assertEquals(12, loaded.items.sumOf { it.setCount })
        assertTrue(loaded.items.groupBy { it.weekNumber }.values.all { week -> week.map { it.dayOfWeek }.distinct().size == 2 })
        assertEquals(ProgressionMode.CUSTOM, loaded.progressionSessions.single { it.key == explicitKey }.track.mode)
        assertEquals(ProgressionRole.ASSISTANCE, loaded.progressionSessions.single { it.key == explicitKey }.track.roleOverride)
        service(reopened).applyProgramToDates(id, "2026-09-07", ProgramApplyMode.Append)
        val applied = reopened.workoutDao().allEntriesWithSets()
        assertEquals(6, applied.size)
        assertEquals(12, applied.sumOf { it.sets.size })
        assertTrue(applied.flatMap { it.sets }.none { it.confirmed })
        assertEquals(6, applied.map { it.entry.date }.distinct().size)
    }
    @Test fun fullCustomRuleAndDisconnectedMemberSurviveBothEditorAndDetailConfiguration() = runBlocking {
        val db = database(); seed(db)
        val rule = ProgressionRule(requireCompletion = false, rpeThreshold = 8.5, successesRequired = 2, incrementKg = 2.5,
            firstFailure = FirstProgressionFailure.REVIEW, failuresBeforeDecrease = 3, decreasePercent = 7.5, missingRpe = MissingProgressionRpe.COMPLETION_ONLY)
        var draft = linked()
        val key = draft.sessionKey("item-0")
        draft = draft.choose("item-0", ProgressionLinkMode.EXISTING, key, rule = rule)
        val id = service(db).saveGeneratedProgram(null, draft)
        val loaded = editor(db, id)
        assertEquals(rule, loaded.progressionSessions.single { it.key == key }.track.rule)
        val friday = db.programProgressionDao().items().single { it.trackId == key && it.logicalItemId == draft.items.single { it.localId == "item-2" }.progressionBinding!!.logicalItemId }
        ProgramProgressionService(db).configure(friday.programItemId, ProgressionLinkMode.OFF, key, ProgressionRole.ASSISTANCE, ProgressionMode.CUSTOM, rule)
        val changed = editor(db, id)
        assertEquals(ProgressionMode.CUSTOM, changed.progressionSessions.single { it.key == key }.track.mode)
        assertEquals(ProgressionRole.ASSISTANCE, changed.progressionSessions.single { it.key == key }.track.roleOverride)
        assertEquals(ProgressionLinkMode.OFF, changed.items.single { it.progressionBinding?.logicalItemId == friday.logicalItemId }.progressionBinding!!.linkMode)
        service(db).saveGeneratedProgram(id, changed)
        service(db).applyProgramToDates(id, "2026-09-07", ProgramApplyMode.Append)
        assertEquals(ProgressionMode.OFF, db.programProgressionDao().links().single { it.sourceItemId == friday.logicalItemId }.mode)
        assertEquals(ProgressionMode.CUSTOM, db.programProgressionDao().links().single { it.trackId == key && it.sourceItemId != friday.logicalItemId }.mode)
    }
    @Test fun allModesCustomRuleAndLogicalGraphSurviveReopenAndUnchangedSave() = runBlocking {
        for (mode in ProgressionMode.entries) {
            val name = "session-${UUID.randomUUID()}.db"
            val first = database(name); seed(first)
            val draft = linked(mode)
            val id = service(first).saveGeneratedProgram(null, draft)
            val bindings = first.programProgressionDao().items().map { it.copy(programItemId = 0) }.sortedBy { it.logicalItemId }
            val tracks = first.programProgressionDao().tracks().sortedBy { it.id }
            first.close()
            val db = database(name)
            val loaded = editor(db, id)
            assertEquals(tracks, loaded.progressionSessions.map { it.track }.sortedBy { it.id })
            assertEquals(mode, loaded.progressionSessions.single { it.key == draft.sessionKey("item-0") }.track.mode)
            assertEquals(ProgressionRole.MAIN, loaded.progressionSessions.single { it.key == draft.sessionKey("item-0") }.track.roleOverride)
            assertEquals(if (mode == ProgressionMode.APP) ProgressionRule() else ProgressionRule(incrementKg = 2.5), loaded.progressionSessions.single { it.key == draft.sessionKey("item-0") }.track.rule)
            service(db).saveGeneratedProgram(id, loaded.reconcileProgression(setOf("squat", "bench")))
            assertEquals(bindings, db.programProgressionDao().items().map { it.copy(programItemId = 0) }.sortedBy { it.logicalItemId })
            assertEquals(tracks, db.programProgressionDao().tracks().sortedBy { it.id })
        }
    }
    @Test fun zeroWeightSessionPersistsThenPositivePrescriptionKeepsExplicitIdentity() = runBlocking {
        val db = database(); seed(db)
        val draft = manualSessionDraft(0.0).choose("item-0", ProgressionLinkMode.SEPARATE)
        val id = service(db).saveGeneratedProgram(null, draft)
        val loaded = editor(db, id)
        val key = draft.sessionKey("item-0")
        val item = loaded.items.single { it.progressionBinding?.sessionKey == key }
        val updated = loaded.upsertDraftItem(item.copy(weightKg = 100.0, setPrescriptions = item.setPrescriptions.map { it.copy(weightKg = 100.0) })).reconcileProgression(setOf("squat", "bench"))
        service(db).saveGeneratedProgram(id, updated)
        assertEquals(key, db.programProgressionDao().items().single { it.logicalItemId == item.progressionBinding!!.logicalItemId }.trackId)
        assertEquals(ProgressionBase.UNIFORM, db.programProgressionDao().tracks().single { it.id == key }.basePolicy)
    }
    @Test fun generatedStyleVariantAnchorRoleAndUserOverrideHydrateExactly() = runBlocking {
        val db = database(); seed(db)
        val generated = manualSessionDraft().copy(items = manualSessionDraft().items.map { it.copy(progressionBinding = null,
            progressionStyle = "DUP_LIKE_UNDULATING", progressionVariant = "STRENGTH", progressionAnchorSetIndex = 1, progressionRole = ProgressionRole.MAIN) }, progressionSessions = emptyList())
            .reconcileProgression(setOf("squat", "bench"))
        val id = service(db).saveGeneratedProgram(null, generated)
        val loaded = editor(db, id)
        assertTrue(loaded.items.all { it.progressionStyle == "DUP_LIKE_UNDULATING" && it.progressionVariant == "STRENGTH" && it.progressionAnchorSetIndex == 1 && it.progressionRole == ProgressionRole.MAIN })
        val item = loaded.items.first()
        val changed = loaded.choose(item.localId, ProgressionLinkMode.SEPARATE, role = ProgressionRole.ASSISTANCE)
        service(db).saveGeneratedProgram(id, changed)
        val again = editor(db, id)
        assertEquals(ProgressionRole.ASSISTANCE, again.progressionSessions.single { it.key == changed.sessionKey(item.localId) }.track.roleOverride)
    }
    @Test fun manualApplicationMovePushAndDetachedCopyKeepExactLineageAndPrescription() = runBlocking {
        val db = database(); seed(db); val draft = linked(); val id = service(db).saveGeneratedProgram(null, draft)
        service(db).applyProgramToDates(id, "2026-09-07", ProgramApplyMode.Append)
        val dao = db.programProgressionDao(); val before = dao.links()
        assertEquals(2, before.count { it.trackId == draft.sessionKey("item-0") })
        assertEquals(1, before.count { it.trackId == draft.sessionKey("item-1") })
        val first = before.first { it.trackId == draft.sessionKey("item-0") && it.dayOfWeek == 1 }
        val prescriptions = dao.prescriptions(first.entryId)
        val calendar = CalendarRecordService(db, db.workoutDao())
        calendar.moveDate("2026-09-07", "2026-09-08", CalendarConflictMode.Append)
        val moved = dao.links().single { it.sourceItemId == first.sourceItemId }
        assertEquals(first.copy(entryId = moved.entryId), moved)
        assertEquals(prescriptions.map { it.copy(entryId = moved.entryId) }, dao.prescriptions(moved.entryId))
        calendar.pushFuturePlan("2026-09-08", 1)
        assertEquals(before.map { it.copy(entryId = 0) }.sortedBy { it.sourceItemId }, dao.links().map { it.copy(entryId = 0) }.sortedBy { it.sourceItemId })
        calendar.copyDate("2026-09-09", "2026-09-20", false, CalendarConflictMode.Append)
        val copied = db.workoutDao().entriesWithSets("2026-09-20")
        assertTrue(copied.isNotEmpty())
        assertTrue(copied.all { dao.link(it.entry.id) == null })
    }
    @Test fun backupRoundTripRehydratesSameUserSessionGraphWithRemappedItems() = runBlocking {
        val db = database(); seed(db); val id = service(db).saveGeneratedProgram(null, linked())
        val snapshot = TrainingRepository(db, context).programEditorSnapshot(id)
        val rows = ProgramProgressionBackup.export(db); ProgramProgressionBackup.validate(rows)
        val restored = database(); seed(restored)
        val newId = restored.programDao().insertProgram(snapshot.program.copy(id = 0))
        snapshot.items.forEach { item ->
            val itemId = restored.programDao().insertProgramItem(item.copy(id = item.id + 100, programId = newId))
            restored.programDao().insertProgramItemSets(snapshot.sets.filter { it.programItemId == item.id }.map { it.copy(id = 0, programItemId = itemId) })
        }
        ProgramProgressionBackup.restore(restored, rows, emptyMap())
        val loaded = editor(restored, newId)
        assertEquals(snapshot.tracks.sortedBy { it.id }, loaded.progressionSessions.map { it.track }.sortedBy { it.id })
        assertEquals(snapshot.bindings.map { it.logicalItemId }.toSet(), loaded.items.map { it.progressionBinding!!.logicalItemId }.toSet())
    }
}

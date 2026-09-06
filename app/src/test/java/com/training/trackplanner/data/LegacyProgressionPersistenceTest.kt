package com.training.trackplanner.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.skeletonFromProgram
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyProgressionPersistenceTest {
    @Test fun manualLegacyGraphSurvivesDiskReopenEditResaveApplyAndPerformedRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = java.nio.file.Files.createTempDirectory("legacy-session-").toFile()
        val diskContext = object : ContextWrapper(context) {
            override fun getDatabasePath(name: String) = java.io.File(directory, name)
        }
        fun open() = Room.databaseBuilder(diskContext, TrainingDatabase::class.java, "program.db").allowMainThreadQueries().build()
        var db = open()
        try {
            frozenLegacyExercises().forEach { db.exerciseDao().insertExercise(it.copy(activityKind = "TRAINING_EXERCISE", volumeLoadEligible = true)) }
            val frozen = legacySessionSkeleton()
            val fingerprint = frozenFingerprint(frozen)
            val contextForDraft = TrainingRepository(db, context).progressionDraftContext()
            val group = frozen.items.groupBy { it.exerciseStableKey }.values.first { it.size >= 4 }
            var overlay = LegacyProgressionDraft().reconcile(frozen, contextForDraft)
            val rule = ProgressionRule(requireCompletion = false, rpeThreshold = 8.5, successesRequired = 2,
                incrementKg = 2.5, firstFailure = FirstProgressionFailure.REVIEW, failuresBeforeDecrease = 3,
                decreasePercent = 7.5, missingRpe = MissingProgressionRpe.COMPLETION_ONLY)
            overlay = overlay.choose(frozen, group[0].localId, ProgressionLinkMode.SEPARATE,
                role = ProgressionRole.ASSISTANCE, rule = rule)
            val shared = overlay.bindings.getValue(group[0].localId).sessionKey
            overlay = overlay.choose(frozen, group[1].localId, ProgressionLinkMode.EXISTING, shared,
                role = ProgressionRole.ASSISTANCE, rule = rule)
            overlay = overlay.choose(frozen, group[2].localId, ProgressionLinkMode.OFF)
            overlay = overlay.choose(frozen, group[3].localId, ProgressionLinkMode.AUTO)
            val expected = group.take(4).associate { it.localId to overlay.bindings.getValue(it.localId) }
            val id = service(db).saveLegacyAutoProgram(null, frozen, overlay)
            val originalRows = db.programDao().itemsForProgram(id).sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }))
            assertEquals(frozen.items.map { it.toTrainingProgramItem(id) }, originalRows.map { it.copy(id = 0) })
            db.close()
            db = open()
            var editor = skeletonFromProgram(TrainingRepository(db, context).programEditorSnapshot(id))
            for (binding in expected.values) {
                val loaded = editor.items.single { it.progressionBinding?.logicalItemId == binding.logicalItemId }.progressionBinding!!
                assertEquals(binding.sessionKey, loaded.sessionKey)
                assertEquals(binding.linkMode, loaded.linkMode)
            }
            val track = editor.progressionSessions.single { it.key == shared }.track
            assertEquals(rule, track.rule)
            assertEquals(ProgressionRole.ASSISTANCE, track.roleOverride)
            assertEquals(ProgressionMode.CUSTOM, track.mode)
            val item = editor.items.first()
            editor = editor.upsertDraftItem(item.copy(restSeconds = item.restSeconds + 10)).copy(suggestedName = "edited")
                .reconcileProgression(contextForDraft.eligibleKeys, contextForDraft.oneRmSnapshots)
            service(db).saveGeneratedProgram(id, editor)
            val reopened = skeletonFromProgram(TrainingRepository(db, context).programEditorSnapshot(id))
            expected.values.forEach { binding ->
                val loaded = reopened.items.single { it.progressionBinding?.logicalItemId == binding.logicalItemId }.progressionBinding!!
                assertEquals(binding.sessionKey, loaded.sessionKey)
                assertEquals(binding.linkMode, loaded.linkMode)
            }
            service(db).applyProgramToDates(id, "2026-09-07", ProgramApplyMode.Append)
            val links = db.programProgressionDao().links()
            val first = links.single { it.sourceItemId == expected.getValue(group[0].localId).logicalItemId }
            val joined = links.single { it.sourceItemId == expected.getValue(group[1].localId).logicalItemId }
            val off = links.single { it.sourceItemId == expected.getValue(group[2].localId).logicalItemId }
            assertEquals(shared, first.trackId); assertEquals(shared, joined.trackId)
            assertEquals(rule, first.rule); assertEquals(ProgressionMode.CUSTOM, first.mode)
            assertEquals(ProgressionRole.ASSISTANCE, first.role)
            assertEquals(ProgressionMode.OFF, off.mode)
            val sets = db.workoutDao().setsForEntry(first.entryId)
            assertTrue(sets.none { it.confirmed })
            val originalPrescription = db.programProgressionDao().prescriptions(first.entryId)
            RecordMutationService(db, db.exerciseDao(), db.workoutDao()).updateSet(sets.first().copy(confirmed = true, rpe = 7.0))
            assertTrue(db.workoutDao().setsForEntry(first.entryId).first().confirmed)
            assertEquals(first, db.programProgressionDao().link(first.entryId))
            assertEquals(originalPrescription, db.programProgressionDao().prescriptions(first.entryId))
            assertEquals(fingerprint, frozenFingerprint(frozen))
        } finally {
            db.close()
            directory.listFiles().orEmpty().forEach { it.delete() }
            directory.delete()
        }
    }

    private fun service(db: TrainingDatabase) = ProgramPlanService(db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() })
}

package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.program.legacy.*
import com.training.trackplanner.skeletonFromProgram
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyAutoPersistenceTest {
    @Test fun finalizedRowsAndNormalizedRequestSurviveSaveReopenAndApplyWithoutDraftMutation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java).allowMainThreadQueries().build()
        try {
            val exercises = frozenLegacyExercises().map { it.copy(activityKind = "TRAINING_EXERCISE", volumeLoadEligible = true) }
            exercises.forEach { db.exerciseDao().insertExercise(it) }
            val draft = LegacyAutoProgramBuilder().build(frozenLegacyRequest(4, 4, 45, .5).copy(
                availableEquipment = setOf("IGNORED"), excludedExerciseText = "ignored", sportStrengthRatio = "ignored"
            ), exercises)
            val fingerprint = frozenFingerprint(draft)
            val service = ProgramPlanService(db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() })
            val id = service.saveLegacyAutoProgram(null, draft)
            val saved = db.programDao().findProgram(id)!!
            assertEquals("", saved.availableEquipment)
            assertEquals("", saved.excludedExerciseText)
            assertEquals("AUTO", saved.sportStrengthRatio)
            assertEquals(draft.request.weeklyTrainingDays, saved.weeklyTrainingDays)
            assertEquals(draft.request.sessionMinutes, saved.sessionMinutes)
            assertEquals(draft.durationDays, saved.durationDays)
            assertEquals(draft.periodizationType.name, saved.periodizationType)
            val rows = db.programDao().itemsForProgram(id).sortedWith(compareBy({ it.weekNumber }, { it.dayOfWeek }, { it.orderIndex }))
            assertEquals(draft.items.map { it.toTrainingProgramItem(id).copy(id = 0) }, rows.map { it.copy(id = 0) })
            val sets = db.programDao().programItemSetsForProgram(id).groupBy { it.programItemId }
            draft.items.zip(rows).forEach { (item, row) ->
                assertEquals(LegacyAutoSetRows.resolve(item), ProgramSetPrescriptionResolver.resolve(row, sets.getValue(row.id)))
            }
            assertTrue(db.programProgressionDao().tracks().isNotEmpty())
            assertEquals(rows.size, db.programProgressionDao().items().size)
            val tracks = db.programProgressionDao().tracks().sortedBy { it.id }
            val editor = skeletonFromProgram(TrainingRepository(db, context).programEditorSnapshot(id))
            assertEquals(rows.size, editor.items.size)
            assertTrue(editor.progressionSessions.isNotEmpty())
            service.saveGeneratedProgram(id, editor)
            assertEquals(tracks, db.programProgressionDao().tracks().sortedBy { it.id })
            service.applyProgramToDates(id, "2026-09-07", ProgramApplyMode.Append)
            val entries = db.workoutDao().allEntriesWithSets()
            assertEquals(rows.size, entries.size)
            assertTrue(entries.flatMap { it.sets }.none { it.confirmed })
            assertEquals(rows.size, db.programProgressionDao().links().size)
            assertEquals(fingerprint, frozenFingerprint(draft))
        } finally { db.close() }
    }
}

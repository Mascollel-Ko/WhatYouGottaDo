package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProgramEffortPersistenceTest {
    private val databases = mutableListOf<TrainingDatabase>()

    @After fun close() = databases.forEach(TrainingDatabase::close)

    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), TrainingDatabase::class.java
    ).allowMainThreadQueries().build().also(databases::add)

    @Test fun applyPersistsPlannedTargetSeparatelyFromActualAndEditsPreserveIt() = runBlocking {
        val db = database()
        db.exerciseDao().insertExercise(Exercise("rpe", "RPE", "STRENGTH"))
        val programId = db.programDao().insertProgram(TrainingProgram(name = "RPE program", durationDays = 7))
        val itemId = db.programDao().insertProgramItem(TrainingProgramItem(
            programId = programId, weekNumber = 1, dayOfWeek = 1, orderIndex = 1,
            exerciseStableKey = "rpe", exerciseName = "RPE", category = "STRENGTH",
            setCount = 2, reps = 8, weightKg = 60.0
        ))
        db.programDao().insertProgramItemSets(listOf(
            TrainingProgramItemSet(programItemId = itemId, setIndex = 1, reps = 8, weightKg = 60.0, seconds = 0, targetRpeMin = 7.0),
            TrainingProgramItemSet(programItemId = itemId, setIndex = 2, reps = 8, weightKg = 60.0, seconds = 0, targetRpeMin = 7.0)
        ))
        val service = ProgramPlanService(db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() })
        service.applyProgramToDates(programId, "2026-09-27", ProgramApplyMode.Append)
        val entry = db.workoutDao().allEntriesWithSets().single()
        assertEquals(listOf(null, null), entry.sets.map { it.rpe })
        assertEquals(listOf(7.0, 7.0), db.programProgressionDao().prescriptions(entry.entry.id).map { it.plannedTargetRpeMin })

        val mutation = RecordMutationService(db, db.exerciseDao(), db.workoutDao())
        mutation.updateSet(entry.sets.first().copy(reps = 9, weightKg = 62.5))
        assertEquals(7.0, db.programProgressionDao().prescriptions(entry.entry.id).first().plannedTargetRpeMin)
        mutation.addSet(entry.entry)
        assertNull(db.programProgressionDao().prescriptions(entry.entry.id).single { !it.originalExists }.plannedTargetRpeMin)
    }

    @Test fun programFingerprintChangesWhenOnlyTargetChanges() = runBlocking {
        val db = database()
        db.exerciseDao().insertExercise(Exercise("fingerprint", "Fingerprint", "STRENGTH"))
        suspend fun create(target: Double?): Long {
            val id = db.programDao().insertProgram(TrainingProgram(name = "P", durationDays = 7))
            val item = db.programDao().insertProgramItem(TrainingProgramItem(
                programId = id, weekNumber = 1, dayOfWeek = 1, orderIndex = 1,
                exerciseStableKey = "fingerprint", exerciseName = "Fingerprint", category = "STRENGTH",
                setCount = 1, reps = 8, weightKg = 60.0
            ))
            db.programDao().insertProgramItemSets(listOf(TrainingProgramItemSet(programItemId = item, setIndex = 1, reps = 8, weightKg = 60.0, seconds = 0, targetRpeMin = target)))
            return id
        }
        val service = ProgramPlanService(db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() })
        assertNotEquals(service.programFingerprint(create(null)), service.programFingerprint(create(7.0)))
    }
}

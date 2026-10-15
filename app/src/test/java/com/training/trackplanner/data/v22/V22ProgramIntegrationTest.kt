package com.training.trackplanner.data.v22

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ProgramApplyMode
import com.training.trackplanner.data.TrainingDatabase
import com.training.trackplanner.data.TrainingRepository
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class V22ProgramIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()

    @After
    fun closeDatabases() = databases.forEach(TrainingDatabase::close)

    @Test
    fun `generated plan saves and applies through existing program session and progression paths`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also(databases::add)
        db.exerciseDao().insertExercise(
            Exercise(
                stableKey = "test_curl",
                name = "테스트 컬",
                category = "근력운동",
                defaultRestSeconds = 75,
                activityKind = "STRENGTH",
                planningEligibility = "ELIGIBLE"
            )
        )
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = "2026-10-10",
                exerciseStableKey = "test_curl",
                exerciseName = "테스트 컬",
                category = "근력운동",
                completedAt = 1L
            )
        )
        repeat(8) { index ->
            db.workoutDao().insertSet(
                WorkoutSet(
                    entryId = entryId,
                    setIndex = index + 1,
                    reps = 10,
                    weightKg = 20.0,
                    rpe = 8.0,
                    confirmed = true,
                    manualWeight = true
                )
            )
        }
        val repository = TrainingRepository(db, context)
        val generated = repository.generateV22Program(
            V22GenerationRequest(
                name = "V2-2 integration",
                weeks = 2,
                days = 1,
                mode = V22Mode.A,
                asOf = LocalDate.of(2026, 10, 11),
                sessionMinutes = 45
            )
        )

        val programId = repository.saveGeneratedProgram(null, generated.skeleton)
        val program = requireNotNull(db.programDao().findProgram(programId))
        val items = db.programDao().itemsForProgram(programId)
        val storedSets = db.programDao().programItemSetsForProgram(programId)
        assertEquals(14, program.durationDays)
        assertEquals(1, program.weeklyTrainingDays)
        assertEquals(45, program.sessionMinutes)
        assertNull(program.canonicalBuilderProtocolVersion)
        assertNull(program.canonicalPlannerRuntimeVersion)
        assertEquals(2, items.size)
        assertEquals(4, storedSets.size)
        assertTrue(storedSets.all { it.weightKg == 20.0 && it.reps == 10 })

        repository.applyProgramToDates(programId, "2026-11-01", ProgramApplyMode.Append)
        val applied = db.workoutDao().entriesWithSetsBetween("2026-11-01", "2026-11-14")
        assertEquals(2, applied.size)
        assertEquals(4, applied.flatMap { it.sets }.size)
        assertTrue(applied.flatMap { it.sets }.all { !it.confirmed && it.weightKg == 20.0 })
        assertEquals(2, db.programProgressionDao().links().size)
        assertEquals(4, db.programProgressionDao().prescriptions().size)
        assertEquals(8, db.workoutDao().entriesWithSets("2026-10-10").single().sets.size)
        assertFalse(generated.plan.authority.executionMissCarryover)
        assertTrue(generated.plan.authority.structuralPlanningOnly)
    }
}

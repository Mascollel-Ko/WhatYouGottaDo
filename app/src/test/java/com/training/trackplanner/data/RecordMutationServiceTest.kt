package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.fatigue.DailyFatigueCalculator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecordMutationServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun manualPlanCopiesOnlyWeightRepsAndManualWeightFromLatestConfirmedSet() = runBlocking {
        val db = newDatabase()
        val exercise = addExercise(db, "barbell_deadlift", "데드리프트")
        val sourceEntryId = addHistory(
            db,
            exercise,
            date = "2026-07-01",
            sets = listOf(
                set(1, reps = 3, weightKg = 180.0, confirmed = true).copy(
                    manualWeight = true,
                    seconds = 45,
                    rpe = 9.0,
                    restSecondsOverride = 240
                )
            )
        )
        val source = db.workoutDao().setsForEntry(sourceEntryId).single()

        val entryId = service(db).addWorkoutEntry("2026-07-02", exercise.stableKey)
        val planned = db.workoutDao().setsForEntry(entryId).single()

        assertTrue(planned.id != source.id)
        assertTrue(planned.entryId != source.entryId)
        assertEquals(entryId, planned.entryId)
        assertEquals(1, planned.setIndex)
        assertEquals(3, planned.reps)
        assertEquals(180.0, planned.weightKg, 0.001)
        assertTrue(planned.manualWeight)
        assertFalse(planned.confirmed)
        assertEquals(null, planned.rpe)
        assertEquals(null, planned.restSecondsOverride)
        assertEquals(0, planned.seconds)
    }

    @Test
    fun latestConfirmedQueryUsesDatePerformanceEntryAndSetOrdering() = runBlocking {
        val db = newDatabase()
        val exercise = addExercise(db, "barbell_back_squat", "스쿼트")
        addHistory(
            db,
            exercise,
            date = "2026-07-01",
            performedAt = 1_000L,
            sets = listOf(set(1, 5, 100.0, true))
        )
        addHistory(
            db,
            exercise,
            date = "2026-07-02",
            performedAt = 2_000L,
            sets = listOf(set(1, 5, 120.0, true))
        )
        addHistory(
            db,
            exercise,
            date = "2026-07-02",
            performedAt = 3_000L,
            sets = listOf(set(1, 4, 130.0, true))
        )
        addHistory(
            db,
            exercise,
            date = "2026-07-02",
            performedAt = 3_000L,
            sets = listOf(
                set(1, 3, 140.0, true),
                set(2, 2, 145.0, true),
                set(3, 1, 999.0, false)
            )
        )

        val source = db.workoutDao().latestConfirmedSetForExerciseAtOrBefore(
            exercise.stableKey,
            "2026-07-03"
        )!!
        val entryId = service(db).addWorkoutEntry("2026-07-03", exercise.stableKey)
        val planned = db.workoutDao().setsForEntry(entryId).single()

        assertEquals(145.0, source.weightKg, 0.001)
        assertEquals(2, source.setIndex)
        assertEquals(145.0, planned.weightKg, 0.001)
        assertEquals(2, planned.reps)
    }

    @Test
    fun unconfirmedAndFutureSetsNeverLeakIntoPastPlan() = runBlocking {
        val db = newDatabase()
        val exercise = addExercise(db, "barbell_bench_press", "벤치프레스")
        addHistory(
            db,
            exercise,
            "2026-07-05",
            sets = listOf(set(1, 3, 180.0, true))
        )
        addHistory(
            db,
            exercise,
            "2026-07-09",
            sets = listOf(set(1, 8, 220.0, false))
        )
        addHistory(
            db,
            exercise,
            "2026-07-20",
            sets = listOf(set(1, 1, 250.0, true))
        )

        val entryId = service(db).addWorkoutEntry("2026-07-10", exercise.stableKey)
        val planned = db.workoutDao().setsForEntry(entryId).single()

        assertEquals(180.0, planned.weightKg, 0.001)
        assertEquals(3, planned.reps)
    }

    @Test
    fun stableKeyMatchSurvivesRenameAndRejectsSameDisplayName() = runBlocking {
        val db = newDatabase()
        val target = addExercise(db, "target_press", "새 프레스")
        val other = addExercise(db, "other_press", "새 프레스")
        addHistory(
            db,
            target,
            "2026-07-01",
            snapshotName = "예전 프레스",
            sets = listOf(set(1, 6, 80.0, true))
        )
        addHistory(
            db,
            other,
            "2026-07-02",
            sets = listOf(set(1, 1, 999.0, true))
        )

        val entryId = service(db).addWorkoutEntry("2026-07-03", target.stableKey)
        val planned = db.workoutDao().setsForEntry(entryId).single()

        assertEquals(80.0, planned.weightKg, 0.001)
        assertEquals(6, planned.reps)
    }

    @Test
    fun customExerciseUsesTheSameExactStableKeyPrefill() = runBlocking {
        val db = newDatabase()
        val exercise = addExercise(
            db,
            stableKey = "user_ex_123e4567-e89b-12d3-a456-426614174000",
            name = "사용자 운동",
            isCustom = true
        )
        addHistory(
            db,
            exercise,
            "2026-07-01",
            sets = listOf(set(1, 10, 32.5, true).copy(manualWeight = true))
        )

        val entryId = service(db).addWorkoutEntry("2026-07-02", exercise.stableKey)
        val planned = db.workoutDao().setsForEntry(entryId).single()

        assertEquals(32.5, planned.weightKg, 0.001)
        assertEquals(10, planned.reps)
        assertTrue(planned.manualWeight)
    }

    @Test
    fun noConfirmedHistoryKeepsCategoryAwareDefaults() = runBlocking {
        val db = newDatabase()
        val exercise = addExercise(db, "new_strength", "새 운동")

        val entryId = service(db).addWorkoutEntry("2026-07-02", exercise.stableKey)
        val planned = db.workoutDao().setsForEntry(entryId).single()

        assertEquals(0.0, planned.weightKg, 0.001)
        assertEquals(0, planned.reps)
        assertFalse(planned.manualWeight)
        assertFalse(planned.confirmed)
    }

    @Test
    fun addSetStillDuplicatesCurrentPlannedValuesAndResetsCompletionFields() = runBlocking {
        val db = newDatabase()
        val exercise = addExercise(db, "barbell_row", "바벨 로우")
        addHistory(
            db,
            exercise,
            "2026-07-01",
            sets = listOf(set(1, 8, 90.0, true).copy(manualWeight = true))
        )
        val mutation = service(db)
        val entryId = mutation.addWorkoutEntry("2026-07-02", exercise.stableKey)
        val entry = db.workoutDao().findEntryById(entryId)!!

        mutation.addSet(entry)
        val sets = db.workoutDao().setsForEntry(entryId).sortedBy(WorkoutSet::setIndex)

        assertEquals(2, sets.size)
        assertEquals(sets[0].reps, sets[1].reps)
        assertEquals(sets[0].weightKg, sets[1].weightKg, 0.001)
        assertEquals(sets[0].manualWeight, sets[1].manualWeight)
        assertFalse(sets[1].confirmed)
        assertEquals(null, sets[1].rpe)
        assertEquals(null, sets[1].restSecondsOverride)
    }

    @Test
    fun prefilledPlanDoesNotBecomeConfirmedFatigueOrStrengthEvidence() = runBlocking {
        val db = newDatabase()
        val exercise = addExercise(db, "barbell_deadlift", "데드리프트")
        addHistory(
            db,
            exercise,
            "2026-07-01",
            sets = listOf(set(1, 3, 180.0, true).copy(manualWeight = true))
        )
        val repository = TrainingRepository(db, context)
        val targetDate = "2026-07-02"

        val entryId = repository.addWorkoutEntry(targetDate, exercise.stableKey)
        val allRecords = db.workoutDao().allEntriesWithSets()
        val calculator = DailyFatigueCalculator(RuntimeExerciseMetadataCatalogProvider.get(context))
        val withPlan = calculator.calculate(
            LocalDate.parse(targetDate),
            listOf(exercise),
            allRecords,
            initialProfile = null
        )
        val withoutPlan = calculator.calculate(
            LocalDate.parse(targetDate),
            listOf(exercise),
            allRecords.filterNot { record -> record.entry.id == entryId },
            initialProfile = null
        )

        assertEquals(0, db.workoutDao().countConfirmedSetsOnDate(targetDate))
        assertEquals(
            withoutPlan.state.confirmedTrainingLoad,
            withPlan.state.confirmedTrainingLoad,
            0.001
        )
        assertEquals(withoutPlan.state.overallFatigueIndex, withPlan.state.overallFatigueIndex)
        assertTrue(db.strengthPosteriorDao().allEvents().isEmpty())
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun service(db: TrainingDatabase): RecordMutationService =
        RecordMutationService(db, db.exerciseDao(), db.workoutDao())

    private suspend fun addExercise(
        db: TrainingDatabase,
        stableKey: String,
        name: String,
        isCustom: Boolean = false
    ): Exercise =
        Exercise(
            stableKey = stableKey,
            name = name,
            category = "근력운동",
            mode = "무게*횟수",
            isCustom = isCustom
        ).also { exercise -> db.exerciseDao().insertExercise(exercise) }

    private suspend fun addHistory(
        db: TrainingDatabase,
        exercise: Exercise,
        date: String,
        snapshotName: String = exercise.name,
        performedAt: Long? = 1_000L,
        sets: List<WorkoutSet>
    ): Long {
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = date,
                exerciseStableKey = exercise.stableKey,
                exerciseName = snapshotName,
                category = exercise.category,
                createdAt = performedAt ?: 500L,
                completedAt = performedAt,
                firstConfirmedAt = performedAt,
                performedAt = performedAt
            )
        )
        sets.forEach { workoutSet ->
            db.workoutDao().insertSet(workoutSet.copy(id = 0, entryId = entryId))
        }
        return entryId
    }

    private fun set(
        index: Int,
        reps: Int,
        weightKg: Double,
        confirmed: Boolean
    ): WorkoutSet =
        WorkoutSet(
            entryId = 0,
            setIndex = index,
            reps = reps,
            weightKg = weightKg,
            confirmed = confirmed
        )
}

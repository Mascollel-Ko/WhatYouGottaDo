package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecordRangeProgramServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()

    @After
    fun closeDatabases() {
        databases.forEach(TrainingDatabase::close)
    }

    @Test
    fun `record range preserves exact sets gaps duplicates and source before applying`() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val squat = Exercise(stableKey = "squat", name = "스쿼트", category = "근력운동")
        val timed = Exercise(stableKey = "shuttle", name = "셔틀", category = "스포츠")
        db.exerciseDao().insertExercise(squat)
        db.exerciseDao().insertExercise(timed)

        val firstEntry = insertEntry(db, "2026-07-01", squat, 1)
        insertSet(db, firstEntry, 1, 5, 100.0, 0, true)
        insertSet(db, firstEntry, 2, 8, 80.0, 0, false)
        val duplicateEntry = insertEntry(db, "2026-07-01", squat, 2)
        insertSet(db, duplicateEntry, 1, 10, 40.0, 0, true)
        val timedEntry = insertEntry(db, "2026-07-09", timed, 1)
        insertSet(db, timedEntry, 1, 0, 0.0, 45, false)

        val summary = service.recordRangeProgramSummary("2026-07-09", "2026-07-01")
        assertEquals("2026-07-01", summary.startDate)
        assertEquals("2026-07-09", summary.endDate)
        assertEquals(9, summary.durationDays)
        assertEquals(3, summary.entryCount)
        assertEquals(4, summary.setCount)
        assertEquals(2, summary.confirmedSetCount)
        assertEquals(2, summary.unconfirmedSetCount)

        val programId = service.createProgramFromRecordRange(
            "2026-07-09",
            "2026-07-01",
            "기록 기반"
        )
        val program = checkNotNull(db.programDao().findProgram(programId))
        val items = db.programDao().itemsForProgram(programId)
        val storedSets = db.programDao().programItemSetsForProgram(programId)
        assertEquals(9, program.durationDays)
        assertEquals(1, program.weeklyTrainingDays)
        assertEquals(listOf(1, 2), items.filter { it.weekNumber == 1 }.map { it.orderIndex })
        assertEquals(2, items.count { it.exerciseStableKey == squat.stableKey })
        assertEquals(2, items.single { it.id == storedSets.first().programItemId }.setCount)
        assertEquals(
            listOf(
                ProgramSetPrescription(1, 5, 100.0, 0),
                ProgramSetPrescription(2, 8, 80.0, 0)
            ),
            storedSets.filter { it.programItemId == items.first().id }.map {
                ProgramSetPrescription(it.setIndex, it.reps, it.weightKg, it.seconds)
            }
        )
        assertEquals(2, items.single { it.exerciseStableKey == timed.stableKey }.weekNumber)
        assertEquals(2, items.single { it.exerciseStableKey == timed.stableKey }.dayOfWeek)

        val sourceAfter = db.workoutDao().entriesWithSetsBetween("2026-07-01", "2026-07-09")
        assertEquals(listOf(true, false, true, false), sourceAfter.flatMap { it.sets }.map { it.confirmed })

        service.applyProgramToDates(programId, "2026-08-04", ProgramApplyMode.Append)
        val applied = db.workoutDao().entriesWithSetsBetween("2026-08-04", "2026-08-12")
        assertEquals(listOf("2026-08-04", "2026-08-04", "2026-08-12"), applied.map { it.entry.date })
        assertTrue(applied.flatMap { it.sets }.all { !it.confirmed })
        assertEquals(
            listOf(5 to 100.0, 8 to 80.0),
            applied.first().sets.sortedBy { it.setIndex }.map { it.reps to it.weightKg }
        )
        assertEquals(45, applied.last().sets.single().seconds)
        assertEquals(3, service.programItems(programId).first().size)
        assertEquals(4, service.programItemSets(programId).first().size)
    }

    @Test
    fun `stored sets are authoritative while legacy items use scalar fallback`() {
        val item = TrainingProgramItem(
            id = 4,
            programId = 1,
            weekNumber = 1,
            dayOfWeek = 1,
            orderIndex = 1,
            exerciseStableKey = "squat",
            exerciseName = "스쿼트",
            category = "근력운동",
            setCount = 3,
            reps = 5,
            weightKg = 100.0
        )
        val legacy = ProgramSetPrescriptionResolver.resolve(item, emptyList())
        assertEquals(3, legacy.size)
        assertTrue(legacy.all { it.reps == 5 && it.weightKg == 100.0 })

        val canonical = ProgramSetPrescriptionResolver.resolve(
            item,
            listOf(
                TrainingProgramItemSet(2, item.id, 2, 8, 80.0, 0),
                TrainingProgramItemSet(1, item.id, 1, 3, 110.0, 0)
            )
        )
        assertEquals(
            listOf(
                ProgramSetPrescription(1, 3, 110.0, 0),
                ProgramSetPrescription(2, 8, 80.0, 0)
            ),
            canonical
        )
        assertFalse(canonical == legacy)
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also(databases::add)

    private fun service(db: TrainingDatabase) = ProgramPlanService(
        db = db,
        exerciseDao = db.exerciseDao(),
        workoutDao = db.workoutDao(),
        programDao = db.programDao(),
        runtimeMetadataCatalogResolver = { error("not used without a training gate") },
        prescriptionNoteFormatter = { it },
        builtInProgramKeys = { emptySet() }
    )

    private suspend fun insertEntry(
        db: TrainingDatabase,
        date: String,
        exercise: Exercise,
        displayOrder: Int
    ): Long = db.workoutDao().insertEntry(
        WorkoutEntry(
            date = date,
            exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name,
            category = exercise.category,
            displayOrder = displayOrder
        )
    )

    private suspend fun insertSet(
        db: TrainingDatabase,
        entryId: Long,
        setIndex: Int,
        reps: Int,
        weightKg: Double,
        seconds: Int,
        confirmed: Boolean
    ) {
        db.workoutDao().insertSet(
            WorkoutSet(
                entryId = entryId,
                setIndex = setIndex,
                reps = reps,
                weightKg = weightKg,
                seconds = seconds,
                confirmed = confirmed
            )
        )
    }
}

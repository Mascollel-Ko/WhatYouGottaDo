package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.TrainingViewModel
import com.training.trackplanner.analysis.core.SystemAnalysisDateProvider
import com.training.trackplanner.analysis.readiness.TrainingGateSnapshot
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
        insertSet(db, timedEntry, 1, 0, 0.0, 60, false)
        insertSet(db, timedEntry, 2, 0, 0.0, 45, false)

        val summary = service.recordRangeProgramSummary("2026-07-09", "2026-07-01")
        assertEquals("2026-07-01", summary.startDate)
        assertEquals("2026-07-09", summary.endDate)
        assertEquals(9, summary.durationDays)
        assertEquals(3, summary.entryCount)
        assertEquals(5, summary.setCount)
        assertEquals(2, summary.confirmedSetCount)
        assertEquals(3, summary.unconfirmedSetCount)

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
        assertEquals(listOf(true, false, true, false, false), sourceAfter.flatMap { it.sets }.map { it.confirmed })

        service.applyProgramToDates(programId, "2026-08-04", ProgramApplyMode.Append)
        val applied = db.workoutDao().entriesWithSetsBetween("2026-08-04", "2026-08-12")
        assertEquals(listOf("2026-08-04", "2026-08-04", "2026-08-12"), applied.map { it.entry.date })
        assertTrue(applied.flatMap { it.sets }.all { !it.confirmed })
        assertEquals(
            listOf(5 to 100.0, 8 to 80.0),
            applied.first().sets.sortedBy { it.setIndex }.map { it.reps to it.weightKg }
        )
        assertEquals(listOf(60, 45), applied.last().sets.sortedBy { it.setIndex }.map { it.seconds })
        assertEquals(3, service.programItems(programId).first().size)
        assertEquals(5, service.programItemSets(programId).first().size)
    }

    @Test
    fun `legacy scalar prescriptions apply exactly today and in the future`() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val exercises = listOf(
            Exercise(
                stableKey = "heavy_lower",
                name = "고중량 하체",
                category = "근력운동",
                bodyRegion = "LOWER",
                axialLoadLevel = "HIGH",
                neuralHeavyWeight = 1.0
            ),
            Exercise(
                stableKey = "high_impact",
                name = "고충격 점프",
                category = "기능성운동",
                jointStressTags = "HIGH_IMPACT",
                elasticSscWeight = 1.0
            ),
            Exercise(
                stableKey = "cod",
                name = "방향전환",
                category = "배드민턴",
                courtMovementTypes = "CHANGE_OF_DIRECTION"
            ),
            Exercise(
                stableKey = "overhead",
                name = "오버헤드 프레스",
                category = "근력운동",
                movementPattern = "OVERHEAD_PRESS",
                overheadSwingWeight = 1.0
            ),
            Exercise(
                stableKey = "upper_push",
                name = "벤치 프레스",
                category = "근력운동",
                movementPattern = "HORIZONTAL_PUSH",
                bodyRegion = "UPPER"
            )
        )
        exercises.forEach { db.exerciseDao().insertExercise(it) }
        val programId = db.programDao().insertProgram(
            TrainingProgram(name = "정확 적용", durationDays = 1)
        )
        exercises.forEachIndexed { index, exercise ->
            db.programDao().insertProgramItem(
                TrainingProgramItem(
                    programId = programId,
                    weekNumber = 1,
                    dayOfWeek = 1,
                    orderIndex = index + 1,
                    exerciseStableKey = exercise.stableKey,
                    exerciseName = exercise.name,
                    category = exercise.category,
                    setCount = index + 2,
                    reps = index + 5,
                    weightKg = 40.0 + index * 10.0
                )
            )
        }

        val today = SystemAnalysisDateProvider().today()
        val future = today.plusDays(30)
        service.applyProgramToDates(programId, today.toString(), ProgramApplyMode.Append)
        service.applyProgramToDates(programId, future.toString(), ProgramApplyMode.Append)

        val todayEntries = db.workoutDao().entriesWithSetsBetween(today.toString(), today.toString())
        val futureEntries = db.workoutDao().entriesWithSetsBetween(future.toString(), future.toString())
        assertEquals(exercises.map(Exercise::stableKey), todayEntries.map { it.entry.exerciseStableKey })
        assertEquals(exercises.map(Exercise::stableKey), futureEntries.map { it.entry.exerciseStableKey })
        todayEntries.zip(futureEntries).forEachIndexed { index, (current, later) ->
            val expectedCount = index + 2
            val expectedReps = index + 5
            val expectedWeight = 40.0 + index * 10.0
            assertEquals(expectedCount, current.sets.size)
            assertEquals(current.sets.map { it.reps to it.weightKg }, later.sets.map { it.reps to it.weightKg })
            assertTrue(current.sets.all { it.reps == expectedReps && it.weightKg == expectedWeight && !it.confirmed })
            assertTrue(later.sets.all { !it.confirmed })
        }
    }

    @Test
    fun `overwrite preserves confirmed history while append preserves planned rows`() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val exercise = Exercise(stableKey = "row", name = "로우", category = "근력운동")
        db.exerciseDao().insertExercise(exercise)
        val programId = db.programDao().insertProgram(TrainingProgram(name = "충돌 계약", durationDays = 1))
        db.programDao().insertProgramItem(
            TrainingProgramItem(
                programId = programId,
                weekNumber = 1,
                dayOfWeek = 1,
                orderIndex = 1,
                exerciseStableKey = exercise.stableKey,
                exerciseName = exercise.name,
                category = exercise.category,
                setCount = 2,
                reps = 8,
                weightKg = 60.0
            )
        )

        val overwriteDate = "2026-09-01"
        val confirmedId = insertEntry(db, overwriteDate, exercise.copy(name = "완료 기록"), 1)
        insertSet(db, confirmedId, 1, 5, 70.0, 0, true)
        val plannedId = insertEntry(db, overwriteDate, exercise.copy(name = "기존 계획"), 2)
        insertSet(db, plannedId, 1, 10, 30.0, 0, false)
        service.applyProgramToDates(programId, overwriteDate, ProgramApplyMode.Overwrite)

        val overwritten = db.workoutDao().entriesWithSetsBetween(overwriteDate, overwriteDate)
        assertEquals(listOf("완료 기록", "로우"), overwritten.map { it.entry.exerciseName })
        assertTrue(overwritten.first().sets.single().confirmed)
        assertTrue(overwritten.last().sets.all { !it.confirmed })

        val appendDate = "2026-10-01"
        val appendPlannedId = insertEntry(db, appendDate, exercise.copy(name = "유지 계획"), 1)
        insertSet(db, appendPlannedId, 1, 12, 20.0, 0, false)
        service.applyProgramToDates(programId, appendDate, ProgramApplyMode.Append)
        assertEquals(
            listOf("유지 계획", "로우"),
            db.workoutDao().entriesWithSetsBetween(appendDate, appendDate).map { it.entry.exerciseName }
        )
    }

    @Test
    fun `program application call chain has no training gate parameter`() {
        listOf(
            TrainingViewModel::class.java to "applyProgram",
            TrainingRepository::class.java to "applyProgramToDates",
            ProgramPlanService::class.java to "applyProgramToDates"
        ).forEach { (type, methodName) ->
            val methods = type.declaredMethods.filter { it.name == methodName }
            assertTrue("$methodName must exist on ${type.simpleName}", methods.isNotEmpty())
            assertTrue(
                "$methodName must not accept TrainingGateSnapshot",
                methods.all { method ->
                    method.parameterTypes.none { parameter -> parameter == TrainingGateSnapshot::class.java }
                }
            )
        }
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

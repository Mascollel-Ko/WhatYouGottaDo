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
import java.util.zip.GZIPInputStream
import java.io.InputStreamReader

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

    @Test
    fun `real history A B C programs survive preview edit save apply and requery`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also(databases::add)
        val history = realHistoryRows()
        seedHistory(db, history)
        val repository = TrainingRepository(db, context)
        val asOf = LocalDate.of(2026, 10, 11)
        val inferredDays = V22PythonParityEngine.generateBaseline(
            history,
            asOf,
            "whatyougottado_backup_2026-10-12-v22.csv",
            null
        ).trainingDays
        assertEquals(3, inferredDays)
        val historyBefore = db.workoutDao().entriesWithSetsBetween("2026-06-01", "2026-10-11")
            .flatMap { row -> row.sets.map { set -> listOf(row.entry.date, row.entry.exerciseStableKey, set.reps, set.weightKg, set.rpe) } }

        data class IntegrationCase(
            val mode: V22Mode,
            val weeks: Int,
            val days: Int,
            val focus: String?,
            val startDate: LocalDate
        )
        val cases = listOf(
            IntegrationCase(V22Mode.A, 2, 3, null, LocalDate.of(2027, 1, 4)),
            IntegrationCase(V22Mode.B, 8, inferredDays, null, LocalDate.of(2027, 2, 1)),
            IntegrationCase(V22Mode.C, 12, 5, "barbell_back_squat", LocalDate.of(2027, 4, 5))
        )

        cases.forEach { case ->
            val request = V22GenerationRequest(
                name = "real-${case.mode}-${case.weeks}w-${case.days}d",
                weeks = case.weeks,
                days = case.days,
                mode = case.mode,
                asOf = asOf,
                focusStableKey = case.focus,
                sessionMinutes = 60,
                backupName = "whatyougottado_backup_2026-10-12-v22.csv"
            )
            val generated = repository.generateV22Program(request)
            assertEquals(V22PythonParityEngine.build(history, request), generated.plan)
            val preview = generated.skeleton
            assertEquals(case.weeks * 7, preview.durationDays)
            assertEquals(case.weeks, generated.plan.weeks.size)
            assertEquals(V22PythonParityEngine.splitBlocks(case.weeks).size, generated.plan.blocks.size)
            assertTrue(preview.items.isNotEmpty())

            // This mirrors an allowed preview edit without altering the Python-derived prescription.
            val edited = preview.copy(suggestedName = "${preview.suggestedName} · 검증 편집")
            val programId = repository.saveGeneratedProgram(null, edited)
            val storedProgram = requireNotNull(db.programDao().findProgram(programId))
            val storedItems = db.programDao().itemsForProgram(programId)
            val storedSets = db.programDao().programItemSetsForProgram(programId).groupBy { it.programItemId }
            assertEquals(edited.suggestedName, storedProgram.name)
            assertEquals(case.weeks * 7, storedProgram.durationDays)
            assertEquals(case.days, storedProgram.weeklyTrainingDays)
            assertEquals(edited.items.size, storedItems.size)
            edited.items.zip(storedItems).forEach { (expected, actual) ->
                assertEquals(expected.weekNumber, actual.weekNumber)
                assertEquals(expected.dayOfWeek, actual.dayOfWeek)
                assertEquals(expected.orderIndex, actual.orderIndex)
                assertEquals(expected.exerciseStableKey, actual.exerciseStableKey)
                assertEquals(expected.setCount, actual.setCount)
                val actualSets = storedSets[actual.id].orEmpty()
                assertEquals(expected.setPrescriptions.size, actualSets.size)
                expected.setPrescriptions.zip(actualSets).forEach { (expectedSet, actualSet) ->
                    assertEquals(expectedSet.setIndex, actualSet.setIndex)
                    assertEquals(expectedSet.reps, actualSet.reps)
                    assertEquals(expectedSet.weightKg, actualSet.weightKg, 0.0)
                    assertEquals(expectedSet.seconds, actualSet.seconds)
                    assertEquals(expectedSet.loadState, actualSet.loadState)
                    assertNull(actualSet.targetRpeMin)
                }
            }

            repository.applyProgramToDates(programId, case.startDate.toString(), ProgramApplyMode.Append)
            val endDate = case.startDate.plusDays((case.weeks * 7 - 1).toLong())
            val applied = db.workoutDao().entriesWithSetsBetween(case.startDate.toString(), endDate.toString())
            assertEquals(storedItems.size, applied.size)
            assertTrue(applied.flatMap { it.sets }.all { !it.confirmed && it.targetRpeMin == null })
            val expectedSchedule = storedItems.groupingBy { item ->
                val date = case.startDate.plusDays(((item.weekNumber - 1) * 7L) + item.dayOfWeek - 1L)
                "${date}|${item.exerciseStableKey}|${item.setCount}"
            }.eachCount()
            val actualSchedule = applied.groupingBy { row ->
                "${row.entry.date}|${row.entry.exerciseStableKey}|${row.sets.size}"
            }.eachCount()
            assertEquals(expectedSchedule, actualSchedule)
            assertFalse(generated.plan.authority.executionMissCarryover)
        }

        val historyAfter = db.workoutDao().entriesWithSetsBetween("2026-06-01", "2026-10-11")
            .flatMap { row -> row.sets.map { set -> listOf(row.entry.date, row.entry.exerciseStableKey, set.reps, set.weightKg, set.rpe) } }
        assertEquals(historyBefore, historyAfter)
    }

    private suspend fun seedHistory(db: TrainingDatabase, history: List<V22HistoryRow>) {
        val latest = history.groupBy(V22HistoryRow::stableKey).mapValues { (_, values) -> values.maxBy(V22HistoryRow::date) }
        db.exerciseDao().insertAll(latest.values.map { row ->
            Exercise(
                stableKey = row.stableKey,
                name = row.exerciseName,
                category = row.category,
                activityKind = if (row.category == "근력운동") "STRENGTH" else "FUNCTIONAL",
                planningEligibility = "ELIGIBLE"
            )
        })
        history.groupBy { row -> listOf(row.date.toString(), row.stableKey, row.exerciseName, row.category) }
            .forEach { (identity, values) ->
                val entryId = db.workoutDao().insertEntry(
                    WorkoutEntry(
                        date = identity[0],
                        exerciseStableKey = identity[1],
                        exerciseName = identity[2],
                        category = identity[3],
                        completedAt = 1L
                    )
                )
                values.forEachIndexed { index, row ->
                    db.workoutDao().insertSet(
                        WorkoutSet(
                            entryId = entryId,
                            setIndex = index + 1,
                            reps = row.reps?.toInt() ?: 0,
                            weightKg = row.weightKg ?: 0.0,
                            seconds = row.seconds?.toInt() ?: 0,
                            confirmed = true,
                            manualWeight = (row.weightKg ?: 0.0) > 0.0,
                            rpe = row.rpe
                        )
                    )
                }
            }
    }

    private fun realHistoryRows(): List<V22HistoryRow> = GZIPInputStream(
        checkNotNull(javaClass.classLoader?.getResourceAsStream(
            "v22-real-history/whatyougottado_backup_2026-10-12-v22.csv.gz"
        ))
    ).use { input -> V22HistoryInput.fromBackupCsv(InputStreamReader(input, Charsets.UTF_8)) }
}

package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CalendarRecordServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun copyDateKeepConfirmedTruePreservesMixedSetState() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val exerciseStableKey = insertExercise(db)
        insertEntryWithSets(
            db = db,
            date = "2026-06-01",
            exerciseStableKey = exerciseStableKey,
            exerciseName = "Source lift",
            confirmedStates = listOf(true, false),
            completedAt = 1_000L,
            firstConfirmedAt = 1_000L
        )

        service.copyDate(
            sourceDate = "2026-06-01",
            targetDate = "2026-06-08",
            keepConfirmed = true,
            conflictMode = CalendarConflictMode.Append
        )

        val copied = entriesOn(db, "2026-06-08").single()
        assertEquals(listOf(true, false), copied.confirmedStates())
        assertEquals(listOf(1, 2), copied.setIndices())
        assertNotNull(copied.entry.completedAt)
        assertNotNull(copied.entry.firstConfirmedAt)
        assertNotEquals(1_000L, copied.entry.completedAt)
        assertEquals(listOf(true, false), entriesOn(db, "2026-06-01").single().confirmedStates())
        assertEquals(1_000L, entriesOn(db, "2026-06-01").single().entry.completedAt)
    }

    @Test
    fun copyDateKeepConfirmedFalseCopiesAsPlan() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val exerciseStableKey = insertExercise(db)
        insertEntryWithSets(
            db = db,
            date = "2026-06-01",
            exerciseStableKey = exerciseStableKey,
            exerciseName = "Source lift",
            confirmedStates = listOf(true, true),
            completedAt = 1_000L,
            firstConfirmedAt = 1_000L
        )

        service.copyDate(
            sourceDate = "2026-06-01",
            targetDate = "2026-06-08",
            keepConfirmed = false,
            conflictMode = CalendarConflictMode.Append
        )

        val copied = entriesOn(db, "2026-06-08").single()
        assertEquals(listOf(false, false), copied.confirmedStates())
        assertNull(copied.entry.completedAt)
        assertNull(copied.entry.firstConfirmedAt)
        assertEquals(listOf(true, true), entriesOn(db, "2026-06-01").single().confirmedStates())
    }

    @Test
    fun copyDateOverwriteClearsDestinationBeforeCopy() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val exerciseStableKey = insertExercise(db)
        insertEntryWithSets(db, "2026-06-01", exerciseStableKey, "Source lift", listOf(true))
        insertEntryWithSets(db, "2026-06-08", exerciseStableKey, "Old target", listOf(false))

        service.copyDate(
            sourceDate = "2026-06-01",
            targetDate = "2026-06-08",
            keepConfirmed = false,
            conflictMode = CalendarConflictMode.Overwrite
        )

        val targetEntries = entriesOn(db, "2026-06-08")
        assertEquals(1, targetEntries.size)
        assertEquals("Source lift", targetEntries.single().entry.exerciseName)
        assertEquals(listOf(false), targetEntries.single().confirmedStates())
    }

    @Test
    fun copyDateAppendKeepsDestinationAndAddsCopiedRecords() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val exerciseStableKey = insertExercise(db)
        val existingTargetId = insertEntryWithSets(
            db = db,
            date = "2026-06-08",
            exerciseStableKey = exerciseStableKey,
            exerciseName = "Existing target",
            confirmedStates = listOf(true),
            createdAt = 10L,
            displayOrder = 1
        )
        insertEntryWithSets(
            db = db,
            date = "2026-06-01",
            exerciseStableKey = exerciseStableKey,
            exerciseName = "Source lift",
            confirmedStates = listOf(false),
            createdAt = 20L,
            displayOrder = 1
        )

        service.copyDate(
            sourceDate = "2026-06-01",
            targetDate = "2026-06-08",
            keepConfirmed = false,
            conflictMode = CalendarConflictMode.Append
        )

        val targetEntries = entriesOn(db, "2026-06-08")
        assertEquals(2, targetEntries.size)
        assertEquals("Existing target", targetEntries.first { it.entry.id == existingTargetId }.entry.exerciseName)
        assertEquals(listOf(true), targetEntries.first { it.entry.id == existingTargetId }.confirmedStates())
        assertEquals(1, targetEntries.first { it.entry.id == existingTargetId }.entry.displayOrder)
        assertEquals(
            listOf("Existing target", "Source lift"),
            RecordEntryOrdering.ordered(targetEntries).map { it.entry.exerciseName }
        )
    }

    @Test
    fun deleteDateRangeWithoutConfirmedKeepsConfirmedSetsAndReindexes() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val exerciseStableKey = insertExercise(db)
        insertEntryWithSets(db, "2026-06-01", exerciseStableKey, "Mixed", listOf(false, true))
        insertEntryWithSets(db, "2026-06-02", exerciseStableKey, "Plan only", listOf(false))
        insertEntryWithSets(db, "2026-06-04", exerciseStableKey, "Outside", listOf(false))

        service.deleteDateRange(
            startDate = "2026-06-01",
            endDate = "2026-06-02",
            includeConfirmed = false
        )

        val remainingMixed = entriesOn(db, "2026-06-01").single()
        assertEquals(listOf(true), remainingMixed.confirmedStates())
        assertEquals(listOf(1), remainingMixed.setIndices())
        assertTrue(entriesOn(db, "2026-06-02").isEmpty())
        assertEquals(1, entriesOn(db, "2026-06-04").size)
    }

    @Test
    fun deleteDateRangeWithConfirmedDeletesAllRecordsInRange() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val exerciseStableKey = insertExercise(db)
        insertEntryWithSets(db, "2026-06-01", exerciseStableKey, "Confirmed", listOf(true))
        insertEntryWithSets(db, "2026-06-02", exerciseStableKey, "Plan", listOf(false))
        insertEntryWithSets(db, "2026-06-04", exerciseStableKey, "Outside", listOf(true))

        service.deleteDateRange(
            startDate = "2026-06-01",
            endDate = "2026-06-02",
            includeConfirmed = true
        )

        assertTrue(entriesOn(db, "2026-06-01").isEmpty())
        assertTrue(entriesOn(db, "2026-06-02").isEmpty())
        assertEquals(1, entriesOn(db, "2026-06-04").size)
    }

    @Test
    fun copyDateRangeAsPlanCopiesOffsetsAsUnconfirmed() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val exerciseStableKey = insertExercise(db)
        insertEntryWithSets(db, "2026-06-01", exerciseStableKey, "Day one", listOf(true))
        insertEntryWithSets(db, "2026-06-02", exerciseStableKey, "Day two", listOf(false, true))

        service.copyDateRangeAsPlan(
            sourceStart = "2026-06-01",
            sourceEnd = "2026-06-02",
            targetStart = "2026-06-08",
            conflictMode = CalendarConflictMode.Append
        )

        assertEquals(listOf(false), entriesOn(db, "2026-06-08").single().confirmedStates())
        assertEquals(listOf(false, false), entriesOn(db, "2026-06-09").single().confirmedStates())
        assertEquals(listOf(true), entriesOn(db, "2026-06-01").single().confirmedStates())
        assertEquals(listOf(false, true), entriesOn(db, "2026-06-02").single().confirmedStates())
    }

    @Test
    fun moveDateCopiesToTargetAndDeletesSource() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val exerciseStableKey = insertExercise(db)
        insertEntryWithSets(db, "2026-06-01", exerciseStableKey, "Move me", listOf(true, false))
        insertEntryWithSets(db, "2026-06-08", exerciseStableKey, "Old target", listOf(false))

        service.moveDate(
            sourceDate = "2026-06-01",
            targetDate = "2026-06-08",
            conflictMode = CalendarConflictMode.Overwrite
        )

        assertTrue(entriesOn(db, "2026-06-01").isEmpty())
        val target = entriesOn(db, "2026-06-08").single()
        assertEquals("Move me", target.entry.exerciseName)
        assertEquals(listOf(true, false), target.confirmedStates())
        assertNotNull(target.entry.completedAt)
        assertNotNull(target.entry.firstConfirmedAt)
    }

    @Test
    fun calendarConflictSummaryCountsExistingDatesEntriesAndSets() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val exerciseStableKey = insertExercise(db)
        insertEntryWithSets(db, "2026-06-01", exerciseStableKey, "Plan", listOf(false, false))
        insertEntryWithSets(db, "2026-06-02", exerciseStableKey, "Confirmed", listOf(true))

        val empty = service.calendarConflictSummary(listOf("2026-06-03"))
        assertEquals(1, empty.affectedDateCount)
        assertEquals(0, empty.existingDateCount)
        assertFalse(empty.hasExistingEntries)

        val summary = service.calendarConflictSummary(
            listOf("2026-06-01", "2026-06-02", "2026-06-03")
        )
        assertEquals(3, summary.affectedDateCount)
        assertEquals(2, summary.existingDateCount)
        assertEquals(2, summary.existingEntryCount)
        assertEquals(3, summary.existingSetCount)
        assertEquals(1, summary.existingConfirmedSetCount)
        assertTrue(summary.hasExistingEntries)
    }

    @Test
    fun pushFuturePlanByMultipleDaysPreservesGapsAndDestinationData() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val key = insertExercise(db)
        val sourceIds = listOf(
            insertEntryWithSets(db, "2026-08-29", key, "Squat plan", listOf(false)),
            insertEntryWithSets(db, "2026-08-31", key, "Upper plan", listOf(false)),
            insertEntryWithSets(db, "2026-09-01", key, "Hinge plan", listOf(false))
        )
        insertEntryWithSets(db, "2026-08-31", key, "Existing history", listOf(true))
        insertEntryWithSets(db, "2026-09-02", key, "Existing plan", listOf(false))

        val result = service.pushFuturePlan("2026-08-29", 2)

        assertEquals(4, result.shiftedEntryCount)
        assertEquals(4, result.shiftedSetCount)
        assertTrue(entriesOn(db, "2026-08-29").isEmpty())
        assertTrue(entriesOn(db, "2026-08-30").isEmpty())
        assertEquals(listOf("Existing history", "Squat plan"), entriesOn(db, "2026-08-31").map { it.entry.exerciseName }.sorted())
        assertTrue(entriesOn(db, "2026-09-01").isEmpty())
        assertEquals(listOf("Upper plan"), entriesOn(db, "2026-09-02").map { it.entry.exerciseName })
        assertEquals(listOf("Hinge plan"), entriesOn(db, "2026-09-03").map { it.entry.exerciseName })
        assertEquals(listOf("Existing plan"), entriesOn(db, "2026-09-04").map { it.entry.exerciseName })
        val allEntries = db.workoutDao().allEntries()
        val allSets = db.workoutDao().allSets()
        assertEquals(allEntries.size, allEntries.map { it.id }.distinct().size)
        assertEquals(allSets.size, allSets.map { it.id }.distinct().size)
        assertTrue(sourceIds.none { id -> allEntries.any { it.id == id } })
    }

    @Test
    fun pushByOneMovesEachPlanExactlyOnce() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val key = insertExercise(db)
        insertEntryWithSets(db, "2026-08-29", key, "Day one", listOf(false))
        insertEntryWithSets(db, "2026-08-30", key, "Day two", listOf(false))

        service.pushFuturePlan("2026-08-29", 1)

        assertTrue(entriesOn(db, "2026-08-29").isEmpty())
        assertEquals(listOf("Day one"), entriesOn(db, "2026-08-30").map { it.entry.exerciseName })
        assertEquals(listOf("Day two"), entriesOn(db, "2026-08-31").map { it.entry.exerciseName })
        assertTrue(entriesOn(db, "2026-09-01").isEmpty())
    }

    @Test
    fun pushSplitsMixedEntryAndLeavesConfirmedHistoryOnOriginalDate() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val key = insertExercise(db)
        val originalId = insertEntryWithSets(
            db, "2026-08-29", key, "Mixed", listOf(true, false, true, false),
            completedAt = 1_000L, firstConfirmedAt = 900L
        )

        service.pushFuturePlan("2026-08-29", 3)

        val original = entriesOn(db, "2026-08-29").single()
        assertEquals(originalId, original.entry.id)
        assertEquals(listOf(true, true), original.confirmedStates())
        assertEquals(listOf(1, 2), original.setIndices())
        assertEquals(1_000L, original.entry.completedAt)
        assertEquals(900L, original.entry.firstConfirmedAt)
        val shifted = entriesOn(db, "2026-09-01").single()
        assertNotEquals(originalId, shifted.entry.id)
        assertEquals(listOf(false, false), shifted.confirmedStates())
        assertEquals(listOf(1, 2), shifted.setIndices())
        assertNull(shifted.entry.completedAt)
        assertNull(shifted.entry.firstConfirmedAt)
        assertNull(shifted.entry.performedAt)
    }

    @Test
    fun pushWithNoFuturePlanIsSafeNoOp() = runBlocking {
        val db = newDatabase()
        val service = service(db)
        val key = insertExercise(db)
        val id = insertEntryWithSets(db, "2026-08-29", key, "History", listOf(true))

        val result = service.pushFuturePlan("2026-08-29", 2)

        assertFalse(result.shifted)
        assertEquals(id, entriesOn(db, "2026-08-29").single().entry.id)
    }

    @Test
    fun pushRejectsInvalidAndOverflowingDayCounts() = runBlocking {
        val service = service(newDatabase())
        listOf(0, -1, 36_501).forEach { count ->
            runCatching { service.pushFuturePlan("2026-08-29", count) }
                .onSuccess { fail("Expected day count $count to fail") }
                .onFailure { assertTrue(it is IllegalArgumentException) }
        }
        assertNull(com.training.trackplanner.validPlanPushDayCount("2026-08-29", ""))
        assertNull(com.training.trackplanner.validPlanPushDayCount("2026-08-29", "1.5"))
        assertNull(com.training.trackplanner.validPlanPushDayCount("+999999999-12-31", "1"))
    }

    @Test
    fun pushFailureRollsBackEveryDeletion() = runBlocking {
        val db = newDatabase()
        val key = insertExercise(db)
        val firstId = insertEntryWithSets(db, "2026-08-29", key, "First", listOf(false))
        val secondId = insertEntryWithSets(db, "2026-08-30", key, "Second", listOf(false))
        val service = CalendarRecordService(
            db = db,
            workoutDao = db.workoutDao(),
            beforePlanShiftInsert = { index -> if (index == 1) error("forced failure") }
        )

        runCatching { service.pushFuturePlan("2026-08-29", 1) }
            .onSuccess { fail("Expected injected failure") }

        assertEquals(firstId, entriesOn(db, "2026-08-29").single().entry.id)
        assertEquals(secondId, entriesOn(db, "2026-08-30").single().entry.id)
        assertTrue(entriesOn(db, "2026-08-31").isEmpty())
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun service(db: TrainingDatabase): CalendarRecordService =
        CalendarRecordService(db, db.workoutDao())

    private suspend fun insertExercise(db: TrainingDatabase): String {
        db.exerciseDao().insertExercise(
            Exercise(
                name = "Test exercise",
                category = "Strength",
                stableKey = "test.exercise"
            )
        )
        return "test.exercise"
    }

    private suspend fun insertEntryWithSets(
        db: TrainingDatabase,
        date: String,
        exerciseStableKey: String,
        exerciseName: String,
        confirmedStates: List<Boolean>,
        createdAt: Long = 100L,
        displayOrder: Int = 1,
        completedAt: Long? = if (confirmedStates.any { it }) 1_000L else null,
        firstConfirmedAt: Long? = if (confirmedStates.any { it }) 1_000L else null
    ): Long {
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = date,
                exerciseStableKey = exerciseStableKey,
                exerciseName = exerciseName,
                category = "Strength",
                createdAt = createdAt,
                completedAt = completedAt,
                displayOrder = displayOrder,
                firstConfirmedAt = firstConfirmedAt
            )
        )
        confirmedStates.forEachIndexed { index, confirmed ->
            db.workoutDao().insertSet(
                WorkoutSet(
                    entryId = entryId,
                    setIndex = index + 1,
                    reps = 5,
                    weightKg = 100.0,
                    confirmed = confirmed
                )
            )
        }
        return entryId
    }

    private suspend fun entriesOn(db: TrainingDatabase, date: String): List<WorkoutEntryWithSets> =
        db.workoutDao().entriesWithSets(date)

    private fun WorkoutEntryWithSets.confirmedStates(): List<Boolean> =
        sets.sortedBy { it.setIndex }.map { it.confirmed }

    private fun WorkoutEntryWithSets.setIndices(): List<Int> =
        sets.sortedBy { it.setIndex }.map { it.setIndex }
}

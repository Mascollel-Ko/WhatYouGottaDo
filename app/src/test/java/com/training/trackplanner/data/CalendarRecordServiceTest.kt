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
        val exerciseId = insertExercise(db)
        insertEntryWithSets(
            db = db,
            date = "2026-06-01",
            exerciseId = exerciseId,
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
        val exerciseId = insertExercise(db)
        insertEntryWithSets(
            db = db,
            date = "2026-06-01",
            exerciseId = exerciseId,
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
        val exerciseId = insertExercise(db)
        insertEntryWithSets(db, "2026-06-01", exerciseId, "Source lift", listOf(true))
        insertEntryWithSets(db, "2026-06-08", exerciseId, "Old target", listOf(false))

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
        val exerciseId = insertExercise(db)
        val existingTargetId = insertEntryWithSets(
            db = db,
            date = "2026-06-08",
            exerciseId = exerciseId,
            exerciseName = "Existing target",
            confirmedStates = listOf(true),
            createdAt = 10L,
            displayOrder = 1
        )
        insertEntryWithSets(
            db = db,
            date = "2026-06-01",
            exerciseId = exerciseId,
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
        val exerciseId = insertExercise(db)
        insertEntryWithSets(db, "2026-06-01", exerciseId, "Mixed", listOf(false, true))
        insertEntryWithSets(db, "2026-06-02", exerciseId, "Plan only", listOf(false))
        insertEntryWithSets(db, "2026-06-04", exerciseId, "Outside", listOf(false))

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
        val exerciseId = insertExercise(db)
        insertEntryWithSets(db, "2026-06-01", exerciseId, "Confirmed", listOf(true))
        insertEntryWithSets(db, "2026-06-02", exerciseId, "Plan", listOf(false))
        insertEntryWithSets(db, "2026-06-04", exerciseId, "Outside", listOf(true))

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
        val exerciseId = insertExercise(db)
        insertEntryWithSets(db, "2026-06-01", exerciseId, "Day one", listOf(true))
        insertEntryWithSets(db, "2026-06-02", exerciseId, "Day two", listOf(false, true))

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
        val exerciseId = insertExercise(db)
        insertEntryWithSets(db, "2026-06-01", exerciseId, "Move me", listOf(true, false))
        insertEntryWithSets(db, "2026-06-08", exerciseId, "Old target", listOf(false))

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
        val exerciseId = insertExercise(db)
        insertEntryWithSets(db, "2026-06-01", exerciseId, "Plan", listOf(false, false))
        insertEntryWithSets(db, "2026-06-02", exerciseId, "Confirmed", listOf(true))

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

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun service(db: TrainingDatabase): CalendarRecordService =
        CalendarRecordService(db, db.workoutDao())

    private suspend fun insertExercise(db: TrainingDatabase): Long =
        db.exerciseDao().insertExercise(
            Exercise(
                name = "Test exercise",
                category = "Strength",
                stableKey = "test.exercise"
            )
        )

    private suspend fun insertEntryWithSets(
        db: TrainingDatabase,
        date: String,
        exerciseId: Long,
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
                exerciseId = exerciseId,
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

package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarCopyRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun singleDateCopyWithStateStillPreservesMixedSetState() = runBlocking {
        val db = newDatabase()
        val repository = TrainingRepository(db, context)
        val exerciseId = insertExercise(db)
        insertEntryWithSets(
            db = db,
            date = "2026-06-01",
            exerciseId = exerciseId,
            confirmedStates = listOf(true, false)
        )

        repository.copyDate(
            sourceDate = "2026-06-01",
            targetDate = "2026-06-08",
            keepConfirmed = true,
            conflictMode = CalendarConflictMode.Append
        )

        assertEquals(listOf(true, false), setStatesOn(db, "2026-06-08"))
    }

    @Test
    fun rangeCopyDefaultsToPlanState() = runBlocking {
        val db = newDatabase()
        val repository = TrainingRepository(db, context)
        val exerciseId = insertExercise(db)
        insertEntryWithSets(
            db = db,
            date = "2026-06-01",
            exerciseId = exerciseId,
            confirmedStates = listOf(true, false)
        )
        insertEntryWithSets(
            db = db,
            date = "2026-06-02",
            exerciseId = exerciseId,
            confirmedStates = listOf(true)
        )

        repository.copyDateRangeAsPlan(
            sourceStart = "2026-06-01",
            sourceEnd = "2026-06-02",
            targetStart = "2026-06-08",
            conflictMode = CalendarConflictMode.Append
        )

        assertEquals(listOf(false, false), setStatesOn(db, "2026-06-08"))
        assertEquals(listOf(false), setStatesOn(db, "2026-06-09"))
        assertNull(db.workoutDao().entriesWithSets("2026-06-08").single().entry.completedAt)
    }

    @Test
    fun rangeCopyWithStatePreservesMixedSetStateAcrossTargetDates() = runBlocking {
        val db = newDatabase()
        val repository = TrainingRepository(db, context)
        val exerciseId = insertExercise(db)
        insertEntryWithSets(
            db = db,
            date = "2026-06-01",
            exerciseId = exerciseId,
            confirmedStates = listOf(true, false, true)
        )
        insertEntryWithSets(
            db = db,
            date = "2026-06-02",
            exerciseId = exerciseId,
            confirmedStates = listOf(false, true)
        )

        repository.copyDateRangeAsPlan(
            sourceStart = "2026-06-01",
            sourceEnd = "2026-06-02",
            targetStart = "2026-06-08",
            conflictMode = CalendarConflictMode.Append,
            keepConfirmed = true
        )

        assertEquals(listOf(true, false, true), setStatesOn(db, "2026-06-08"))
        assertEquals(listOf(false, true), setStatesOn(db, "2026-06-09"))
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private suspend fun insertExercise(db: TrainingDatabase): Long =
        db.exerciseDao().insertExercise(
            Exercise(
                name = "테스트 스쿼트",
                category = "근력",
                stableKey = "test.squat"
            )
        )

    private suspend fun insertEntryWithSets(
        db: TrainingDatabase,
        date: String,
        exerciseId: Long,
        confirmedStates: List<Boolean>
    ) {
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = date,
                exerciseId = exerciseId,
                exerciseName = "테스트 스쿼트",
                category = "근력",
                completedAt = if (confirmedStates.any { it }) 1_000L else null,
                firstConfirmedAt = if (confirmedStates.any { it }) 1_000L else null
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
    }

    private suspend fun setStatesOn(db: TrainingDatabase, date: String): List<Boolean> =
        db.workoutDao()
            .entriesWithSets(date)
            .flatMap { entry -> entry.sets.sortedBy { it.setIndex }.map { it.confirmed } }
}

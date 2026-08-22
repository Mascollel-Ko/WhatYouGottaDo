package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class RecordPresentationOrderServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun `manual reorder persists only display order and marks the date`() = runBlocking {
        val db = newDatabase()
        val ids = addEntries(db)
        val before = db.workoutDao().entriesWithSets(DATE).associateBy { it.entry.id }

        assertTrue(service(db).reorder(DATE, listOf(ids[1], ids[2], ids[0])))

        val after = RecordEntryOrdering.ordered(db.workoutDao().entriesWithSets(DATE))
        assertEquals(listOf(ids[1], ids[2], ids[0]), after.map { it.entry.id })
        after.forEachIndexed { index, record ->
            val original = checkNotNull(before[record.entry.id])
            assertEquals(original.entry.copy(displayOrder = index + 1), record.entry)
            assertEquals(original.sets, record.sets)
        }
        assertEquals(
            RecordManualOrderPolicy.markerValue(ids),
            db.appMetaDao().value(RecordManualOrderPolicy.key(DATE))
        )
        assertTrue(db.strengthPosteriorDao().allEvents().isEmpty())
    }

    @Test
    fun `first to last and last to middle orders are accepted while invalid graph is rejected`() = runBlocking {
        val db = newDatabase()
        val ids = addEntries(db)
        val presentation = service(db)

        assertTrue(presentation.reorder(DATE, listOf(ids[1], ids[2], ids[0])))
        assertTrue(presentation.reorder(DATE, listOf(ids[1], ids[0], ids[2])))
        assertFalse(presentation.reorder(DATE, listOf(ids[0], ids[1], 999L)))
        assertEquals(
            listOf(ids[1], ids[0], ids[2]),
            RecordEntryOrdering.ordered(db.workoutDao().entriesWithSets(DATE)).map { it.entry.id }
        )
    }

    @Test
    fun `later first confirmation does not replace a user established order`() = runBlocking {
        val db = newDatabase()
        val ids = addEntries(db)
        val manualOrder = listOf(ids[2], ids[0], ids[1])
        assertTrue(service(db).reorder(DATE, manualOrder))
        val targetSet = db.workoutDao().setsForEntry(ids[1]).single()

        RecordMutationService(
            db = db,
            exerciseDao = db.exerciseDao(),
            workoutDao = db.workoutDao(),
            appMetaDao = db.appMetaDao()
        ).updateSet(targetSet.copy(confirmed = true))

        assertEquals(
            manualOrder,
            RecordEntryOrdering.ordered(db.workoutDao().entriesWithSets(DATE)).map { it.entry.id }
        )
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun service(db: TrainingDatabase) =
        RecordPresentationOrderService(db, db.workoutDao(), db.appMetaDao())

    private suspend fun addEntries(db: TrainingDatabase): List<Long> =
        listOf("스쿼트", "벤치프레스", "데드리프트").mapIndexed { index, name ->
            val stableKey = "exercise_$index"
            db.exerciseDao().insertExercise(
                Exercise(stableKey = stableKey, name = name, category = "근력운동", mode = "무게*횟수")
            )
            val entryId = db.workoutDao().insertEntry(
                WorkoutEntry(
                    date = DATE,
                    exerciseStableKey = stableKey,
                    exerciseName = name,
                    category = "근력운동",
                    notes = "note-$index",
                    rpe = 7.0 + index,
                    maxReps = 10 + index,
                    createdAt = 1_000L + index,
                    completedAt = null,
                    displayOrder = index + 1,
                    firstConfirmedAt = null,
                    performedAt = null,
                    backupSourceId = "source-$index"
                )
            )
            db.workoutDao().insertSet(
                WorkoutSet(
                    entryId = entryId,
                    setIndex = 1,
                    reps = 5 + index,
                    weightKg = 50.0 + index,
                    rpe = 8.0,
                    restSecondsOverride = 90
                )
            )
            entryId
        }

    private companion object {
        const val DATE = "2026-08-22"
    }
}

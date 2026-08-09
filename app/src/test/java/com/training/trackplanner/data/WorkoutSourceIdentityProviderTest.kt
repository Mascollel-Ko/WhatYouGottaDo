package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class WorkoutSourceIdentityProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun lineageIsPersistentAndGeneratedSourceIdsAreUnique() = runBlocking {
        val db = newDatabase()
        val first = provider(db)
        val lineage = first.sourceDatabaseLineageId()
        val second = provider(db)

        assertEquals(lineage, second.sourceDatabaseLineageId())
        assertEquals(lineage, db.appMetaDao().value(WorkoutSourceIdentityProvider.SOURCE_DATABASE_LINEAGE_ID))
        assertNotEquals(first.newWorkoutSourceId(), first.newWorkoutSourceId())
        assertEquals("foreign:workout_entry:7", first.sourceIdForImport(" foreign:workout_entry:7 "))
    }

    @Test
    fun backfillAssignsDeterministicIdsWithoutChangingExistingSourceIdentity() = runBlocking {
        val db = newDatabase()
        val exercise = insertExercise(db)
        val missingId = db.workoutDao().insertEntry(entry(exercise, "2026-08-01"))
        val retainedId = db.workoutDao().insertEntry(
            entry(exercise, "2026-08-02").copy(backupSourceId = "source-db:workout_entry:retained")
        )
        val provider = provider(db)
        val lineage = provider.sourceDatabaseLineageId()

        assertEquals(1, provider.backfillMissingWorkoutSourceIds())

        assertEquals(
            "$lineage:workout_entry:$missingId",
            db.workoutDao().findEntryById(missingId)!!.backupSourceId
        )
        assertEquals(
            "source-db:workout_entry:retained",
            db.workoutDao().findEntryById(retainedId)!!.backupSourceId
        )
        assertEquals(0, provider.backfillMissingWorkoutSourceIds())
    }

    @Test
    fun repositoryCreateCopyAndMoveApplySourceIdentitySemantics() = runBlocking {
        val db = newDatabase()
        val exercise = insertExercise(db)
        val repository = TrainingRepository(db, context)
        val originalId = repository.addWorkoutEntry("2026-08-03", exercise.stableKey)
        val originalSource = db.workoutDao().findEntryById(originalId)!!.backupSourceId
        assertNotNull(originalSource)

        repository.copyDate(
            sourceDate = "2026-08-03",
            targetDate = "2026-08-04",
            keepConfirmed = true,
            conflictMode = CalendarConflictMode.Append
        )
        val copied = db.workoutDao().entriesWithSets("2026-08-04").single().entry
        assertNotNull(copied.backupSourceId)
        assertNotEquals(originalSource, copied.backupSourceId)

        repository.moveDate(
            sourceDate = "2026-08-03",
            targetDate = "2026-08-05",
            conflictMode = CalendarConflictMode.Append
        )
        val moved = db.workoutDao().entriesWithSets("2026-08-05").single().entry
        assertEquals(originalSource, moved.backupSourceId)
        assertTrue(db.workoutDao().entriesWithSets("2026-08-03").isEmpty())
    }

    @Test
    fun uniqueIndexRejectsDuplicateNonNullSourceIdentity() = runBlocking {
        val db = newDatabase()
        val exercise = insertExercise(db)
        val duplicatedSource = "source-db:workout_entry:duplicate"
        db.workoutDao().insertEntry(entry(exercise, "2026-08-06").copy(backupSourceId = duplicatedSource))

        val failure = runCatching {
            db.workoutDao().insertEntry(entry(exercise, "2026-08-07").copy(backupSourceId = duplicatedSource))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(1, db.workoutDao().entriesWithSets("2026-08-06").size)
        assertTrue(db.workoutDao().entriesWithSets("2026-08-07").isEmpty())
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun provider(db: TrainingDatabase): WorkoutSourceIdentityProvider =
        WorkoutSourceIdentityProvider(db, db.appMetaDao(), db.workoutDao())

    private suspend fun insertExercise(db: TrainingDatabase): Exercise {
        val exercise = Exercise(stableKey = "source.identity.exercise", name = "Source identity", category = "Strength")
        db.exerciseDao().insertExercise(exercise)
        return exercise
    }

    private fun entry(exercise: Exercise, date: String): WorkoutEntry = WorkoutEntry(
        date = date,
        exerciseStableKey = exercise.stableKey,
        exerciseName = exercise.name,
        category = exercise.category
    )
}

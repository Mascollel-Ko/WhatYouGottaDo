package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CanonicalStableKeyCutoverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
        .allowMainThreadQueries().build()

    @After fun close() = db.close()

    @Test
    fun oneTimeCutoverKeepsExplicitUserEditAndCustomExercise() = runBlocking {
        val canonical = SeedData.exactExerciseMetadataByStableKey(context).getValue("barbell_back_squat")
        val stale = canonical.copy(primaryMuscles = "HAMSTRING", secondaryMuscles = "BICEPS")
        db.exerciseDao().insertExercise(stale)
        val custom = Exercise(
            name = "내 사용자 운동", stableKey = "user_canonical_cutover_test",
            category = "근력운동", isCustom = true,
            primaryMuscles = "CHEST", secondaryMuscles = "TRICEPS"
        )
        db.exerciseDao().insertExercise(custom)
        val revision = "legacy-metadata-before-cutover"
        fun override(stableKey: String, value: String) = ExerciseMetadataUserOverrideEntity(
            stableKey = stableKey,
            fieldScope = ExerciseMetadataFieldScope.EXERCISE.name,
            fieldKey = "exercise.primaryMuscles",
            valueEncoding = ExerciseMetadataValueEncoding.TOKEN_SET.name,
            value = value,
            isExplicitEmpty = false,
            source = ExerciseMetadataOverrideSource.USER_EDIT.name,
            semanticCanonicalRevisionAtEdit = revision
        )
        db.exerciseMetadataUserOverrideDao().upsert(override(stale.stableKey, "HAMSTRING"))
        db.exerciseMetadataUserOverrideDao().upsert(override(custom.stableKey, "CHEST"))

        val repo = TrainingRepository(db, context)
        repo.seedIfNeeded()

        val restored = db.exerciseDao().findByStableKey(canonical.stableKey)!!
        // The developer-materialized secondary=BICEPS is discarded. The
        // explicit USER_EDIT primary=HAMSTRING survives above the canonical base.
        assertEquals("HAMSTRING", restored.primaryMuscles)
        assertEquals(canonical.secondaryMuscles, restored.secondaryMuscles)
        val savedUserRows = db.exerciseMetadataUserOverrideDao().findByStableKey(canonical.stableKey)
        assertEquals(1, savedUserRows.size)
        assertEquals(ExerciseMetadataOverrideSource.USER_EDIT.name, savedUserRows.single().source)
        assertEquals("exercise.primaryMuscles", savedUserRows.single().fieldKey)
        val customRestored = db.exerciseDao().findByStableKey(custom.stableKey)!!
        assertEquals(custom.primaryMuscles, customRestored.primaryMuscles)
        assertTrue(db.exerciseMetadataUserOverrideDao().findByStableKey(custom.stableKey).isNotEmpty())
        assertEquals("done", db.appMetaDao().value(
            ExerciseMetadataReconciliationService.MUSCLE_CUTOVER_KEY
        ))

        repo.seedIfNeeded()
        assertEquals("HAMSTRING", db.exerciseDao().findByStableKey(canonical.stableKey)!!.primaryMuscles)
        assertEquals(1, db.exerciseMetadataUserOverrideDao().findByStableKey(canonical.stableKey).size)
        assertEquals(custom.primaryMuscles, db.exerciseDao().findByStableKey(custom.stableKey)!!.primaryMuscles)
    }

    @Test
    fun developerMaterializedMuscleDifferencesWithoutUserEditAreReplaced() = runBlocking {
        val canonical = SeedData.exactExerciseMetadataByStableKey(context).getValue("barbell_deadlift")
        val developerCopy = canonical.copy(primaryMuscles = "BICEPS", secondaryMuscles = "TRICEPS")
        db.exerciseDao().insertExercise(developerCopy)

        val repo = TrainingRepository(db, context)
        repo.seedIfNeeded()

        val afterCutover = db.exerciseDao().findByStableKey(canonical.stableKey)!!
        assertEquals(canonical.primaryMuscles, afterCutover.primaryMuscles)
        assertEquals(canonical.secondaryMuscles, afterCutover.secondaryMuscles)
        assertTrue(db.exerciseMetadataUserOverrideDao().findByStableKey(canonical.stableKey).isEmpty())
        repo.seedIfNeeded() // the same one-time cutover is idempotent
        assertEquals(canonical.primaryMuscles, db.exerciseDao().findByStableKey(canonical.stableKey)!!.primaryMuscles)
    }
}

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
    fun oneTimeCutoverReplacesBuiltinMuscleOverrideAndKeepsCustom() = runBlocking {
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
        assertEquals(canonical.primaryMuscles, restored.primaryMuscles)
        assertEquals(canonical.secondaryMuscles, restored.secondaryMuscles)
        assertTrue(db.exerciseMetadataUserOverrideDao().findByStableKey(canonical.stableKey)
            .none { it.fieldKey == "exercise.primaryMuscles" || it.fieldKey == "exercise.secondaryMuscles" })
        val customRestored = db.exerciseDao().findByStableKey(custom.stableKey)!!
        assertEquals(custom.primaryMuscles, customRestored.primaryMuscles)
        assertTrue(db.exerciseMetadataUserOverrideDao().findByStableKey(custom.stableKey).isNotEmpty())
        assertEquals("done", db.appMetaDao().value(
            ExerciseMetadataReconciliationService.MUSCLE_CUTOVER_KEY
        ))

        repo.seedIfNeeded()
        assertEquals(canonical.primaryMuscles, db.exerciseDao().findByStableKey(canonical.stableKey)!!.primaryMuscles)
        assertEquals(custom.primaryMuscles, db.exerciseDao().findByStableKey(custom.stableKey)!!.primaryMuscles)
    }
}

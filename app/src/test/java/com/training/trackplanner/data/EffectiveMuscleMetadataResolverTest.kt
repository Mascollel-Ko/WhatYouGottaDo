package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EffectiveMuscleMetadataResolverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val canonical = CanonicalExerciseMetadataRepository(context)
    private val resolver = EffectiveMuscleMetadataResolver(canonical.muscleRoleResolver())
    private var db: TrainingDatabase? = null

    @After fun closeDb() { db?.close() }

    @Test
    fun uneditedBuiltInUsesCanonicalSixRoleAuthority() {
        val bench = canonical.exercises().first { it.stableKey == "barbell_bench_press" }
        val effective = resolver.resolve(bench, emptyList())
        assertEquals(EffectiveMuscleMetadataSource.CANONICAL, effective.source)
        assertTrue(effective.usesCanonicalRoles)
        assertFalse(effective.needsMigrationReview)
        assertEquals(setOf("CHEST"),
            effective.canonical.forRole(CanonicalMuscleRole.PRIMARY).mapTo(mutableSetOf()) { it.muscleCode })
    }

    @Test
    fun explicitEmptyAndPartialUserOverridesAreNotSilentlyDropped() {
        val bench = canonical.exercises().first { it.stableKey == "barbell_bench_press" }
        val clear = override(bench.stableKey, "exercise.primaryMuscles", "", true)
        val effective = resolver.resolve(bench, listOf(clear))
        assertEquals(EffectiveMuscleMetadataSource.LEGACY_USER_OVERRIDE, effective.source)
        assertTrue(effective.usesCanonicalRoles)
        assertTrue(effective.needsMigrationReview)
        assertTrue(effective.canonical.requiresOverridePolicy)
        assertEquals("CHEST", effective.canonical.forRole(CanonicalMuscleRole.PRIMARY).single().muscleCode)

        val secondary = override(bench.stableKey, "exercise.secondaryMuscles", "TRICEPS")
        val both = resolver.resolve(bench, listOf(clear, secondary))
        assertTrue(both.usesCanonicalRoles)
        assertTrue(both.needsMigrationReview)
        assertEquals(effective.canonical.relations, both.canonical.relations)
    }

    @Test
    fun unrelatedOverridesDoNotDisableCanonicalRelations() {
        val bench = canonical.exercises().first { it.stableKey == "barbell_bench_press" }
        val unrelated = ExerciseMetadataUserOverrideEntity(
            stableKey = bench.stableKey,
            fieldScope = ExerciseMetadataFieldScope.EXERCISE.name,
            fieldKey = "exercise.description",
            valueEncoding = ExerciseMetadataValueEncoding.STRING.name,
            value = "User note",
            isExplicitEmpty = false,
            source = ExerciseMetadataOverrideSource.USER_EDIT.name,
            semanticCanonicalRevisionAtEdit = "test"
        )
        assertTrue(resolver.resolve(bench, listOf(unrelated)).usesCanonicalRoles)
    }

    @Test
    fun unreviewedCustomAndHistoryOnlyDoNotExposeOldMusclesAsLiveRoles() {
        val custom = Exercise(
            stableKey = "user_ex_muscle_example",
            name = "User movement",
            category = "User",
            primaryMuscles = "QUADRICEPS",
            secondaryMuscles = "GLUTE",
            isCustom = true
        )
        val customResult = resolver.resolve(custom, emptyList())
        assertEquals(EffectiveMuscleMetadataSource.LEGACY_CUSTOM_EXERCISE, customResult.source)
        assertFalse(customResult.usesCanonicalRoles)
        assertTrue(customResult.canonical.relations.isEmpty())
        assertTrue(customResult.needsMigrationReview)
        val history = canonical.exercises(includeHistory = true)
            .first { it.stableKey == "ex_99728d25" }
        val historyResult = resolver.resolve(history, emptyList())
        assertEquals(EffectiveMuscleMetadataSource.LEGACY_HISTORY_ONLY, historyResult.source)
        assertFalse(historyResult.usesCanonicalRoles)
        assertTrue(historyResult.canonical.relations.isEmpty())
    }

    @Test
    fun invalidIdentityAndDuplicateOverridesFailClosed() {
        val bench = canonical.exercises().first { it.stableKey == "barbell_bench_press" }
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(bench,
                listOf(override("barbell_back_squat", "exercise.primaryMuscles", "CHEST")))
        }
        val row = override(bench.stableKey, "exercise.primaryMuscles", "CHEST")
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(bench, listOf(row, row))
        }
    }

    @Test
    fun repositoryReadsRealOverrideDaoForSingleAndBatchWithoutWriting() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries().build().also { db = it }
        val bench = canonical.exercises().first { it.stableKey == "barbell_bench_press" }
        val custom = Exercise(stableKey = "user_ex_muscle_example", name = "Custom",
            category = "Custom", primaryMuscles = "BICEPS", isCustom = true)
        database.exerciseDao().insertExercise(bench)
        database.exerciseDao().insertExercise(custom)
        val repository = TrainingRepository(database, context)

        assertTrue(repository.resolvedMuscleMetadata(bench.stableKey)!!.usesCanonicalRoles)
        assertEquals(EffectiveMuscleMetadataSource.LEGACY_CUSTOM_EXERCISE,
            repository.resolvedMuscleMetadata(custom.stableKey)!!.source)
        database.exerciseMetadataUserOverrideDao().upsert(
            override(bench.stableKey, "exercise.primaryMuscles", "", true))
        val single = repository.resolvedMuscleMetadata(bench.stableKey)!!
        assertEquals(EffectiveMuscleMetadataSource.LEGACY_USER_OVERRIDE, single.source)
        assertTrue(single.usesCanonicalRoles)
        assertTrue(single.needsMigrationReview)
        val batched = repository.resolvedMuscleMetadataByStableKey()
        assertEquals(single, batched.getValue(bench.stableKey))
        assertFalse(batched.getValue(custom.stableKey).usesCanonicalRoles)
        assertNull(repository.resolvedMuscleMetadata("missing_key"))
        assertEquals(1, database.exerciseMetadataUserOverrideDao()
            .findByStableKey(bench.stableKey).size)
    }

    private fun override(stableKey: String, fieldKey: String, value: String,
                         empty: Boolean = false) = ExerciseMetadataUserOverrideEntity(
        stableKey = stableKey,
        fieldScope = ExerciseMetadataFieldScope.EXERCISE.name,
        fieldKey = fieldKey,
        valueEncoding = ExerciseMetadataValueEncoding.TOKEN_SET.name,
        value = value,
        isExplicitEmpty = empty,
        source = ExerciseMetadataOverrideSource.RESTORED_USER_OVERRIDE.name,
        semanticCanonicalRevisionAtEdit = "test"
    )
}

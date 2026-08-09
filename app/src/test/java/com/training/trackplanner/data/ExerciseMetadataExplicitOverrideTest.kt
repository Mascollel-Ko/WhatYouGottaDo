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
class ExerciseMetadataExplicitOverrideTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun builtInSaveTracksOnlyDirtyFieldsAndReturnToCanonicalDeletesOnlyThatField() = runBlocking {
        val db = newDatabase()
        val seed = canonicalSeed { exercise -> exercise.description.isNotEmpty() }
        db.exerciseDao().insertExercise(seed)
        val repository = TrainingRepository(db, context)
        val original = repository.exerciseEditorData(seed.stableKey)

        repository.saveExerciseEditor(original)
        assertTrue(db.exerciseMetadataUserOverrideDao().findByStableKey(seed.stableKey).isEmpty())

        repository.saveExerciseEditor(
            original.copy(
                exercise = original.exercise.copy(name = "Ignored rename", description = ""),
            )
        )
        var rows = db.exerciseMetadataUserOverrideDao().findByStableKey(seed.stableKey)
        assertEquals(listOf("exercise.description"), rows.map { it.fieldKey })
        assertTrue(rows.single().isExplicitEmpty)
        assertEquals("", rows.single().value)
        assertEquals(seed.name, db.exerciseDao().findByStableKey(seed.stableKey)!!.name)

        val afterSingleEdit = repository.exerciseEditorData(seed.stableKey)
        repository.saveExerciseEditor(
            afterSingleEdit.copy(
                exercise = afterSingleEdit.exercise.copy(category = "User category"),
                metadata = afterSingleEdit.metadata.copy(programSlot = "USER_SLOT")
            )
        )
        rows = db.exerciseMetadataUserOverrideDao().findByStableKey(seed.stableKey)
        assertEquals(
            setOf("exercise.category", "exercise.description", "runtime.programSlot"),
            rows.mapTo(mutableSetOf()) { it.fieldKey }
        )

        val afterMultipleEdits = repository.exerciseEditorData(seed.stableKey)
        repository.saveExerciseEditor(
            afterMultipleEdits.copy(exercise = afterMultipleEdits.exercise.copy(category = seed.category))
        )
        rows = db.exerciseMetadataUserOverrideDao().findByStableKey(seed.stableKey)
        assertEquals(
            setOf("exercise.description", "runtime.programSlot"),
            rows.mapTo(mutableSetOf()) { it.fieldKey }
        )
    }

    @Test
    fun resetRemovesExplicitOverridesAndRestoresCanonicalEffectiveState() = runBlocking {
        val db = newDatabase()
        val seed = canonicalSeed { exercise -> exercise.description.isNotEmpty() }
        db.exerciseDao().insertExercise(seed.copy(isActive = false, archivedAt = 91L, needsReview = true))
        val repository = TrainingRepository(db, context)
        val original = repository.exerciseEditorData(seed.stableKey)
        repository.saveExerciseEditor(
            original.copy(
                exercise = original.exercise.copy(description = "User description"),
                metadata = original.metadata.copy(programSlot = "USER_SLOT")
            )
        )

        assertTrue(repository.resetExerciseMetadataOverride(seed.stableKey))

        val restored = db.exerciseDao().findByStableKey(seed.stableKey)!!
        val effective = repository.exerciseEditorData(seed.stableKey)
        val canonicalRuntime = CanonicalExerciseMetadataRepositoryProvider.get(context)
            .runtimeMetadataCatalog()
            .resolve(seed)!!
        assertTrue(db.exerciseMetadataUserOverrideDao().findByStableKey(seed.stableKey).isEmpty())
        assertEquals(seed.description, restored.description)
        assertEquals(canonicalRuntime.programSlot, effective.metadata.programSlot)
        assertFalse(restored.isActive)
        assertEquals(91L, restored.archivedAt)
        assertTrue(restored.needsReview)
    }

    @Test
    fun effectiveResolverUsesNewCanonicalBaseThenExactRelationOverride() {
        val canonical = canonicalSeed()
        val materialized = canonical.copy(
            category = "Old canonical category",
            defaultRestSeconds = canonical.defaultRestSeconds + 30
        )
        val nextCanonical = canonical.copy(defaultRestSeconds = canonical.defaultRestSeconds + 15)
        val canonicalRuntime = RuntimeExerciseMetadataDefaults.forExercise(nextCanonical)
        val overrides = listOf(
            overrideRow(canonical.stableKey, "exercise.category", "User category"),
            overrideRow(canonical.stableKey, "relation.trainingRoles", "ROLE_B")
        )

        val result = ExerciseMetadataEffectiveStateResolver(
            canonicalExercisesByStableKey = mapOf(canonical.stableKey to nextCanonical),
            canonicalRuntimeMetadataCatalog = RuntimeExerciseMetadataCatalog.of(listOf(canonicalRuntime)),
            canonicalTrainingRolesByStableKey = mapOf(canonical.stableKey to setOf("ROLE_A", "ROLE_C"))
        ).resolve(
            materializedExercise = materialized,
            materializedRuntimeMetadata = RuntimeExerciseMetadataDefaults.forExercise(materialized),
            materializedTrainingRoles = setOf("LEGACY_ROLE"),
            overrides = overrides
        )

        assertEquals("User category", result.exercise.category)
        assertEquals(nextCanonical.defaultRestSeconds, result.exercise.defaultRestSeconds)
        assertEquals(setOf("ROLE_B"), result.trainingRoles)
    }

    @Test
    fun reconciliationDoesNotInferOverridesFromLegacyMaterializedRows() = runBlocking {
        val db = newDatabase()
        val seed = canonicalSeed()
        db.exerciseDao().insertExercise(seed.copy(category = "Legacy materialized edit"))
        db.runtimeExerciseMetadataDao().upsert(
            RuntimeExerciseMetadataDefaults.forExercise(seed).copy(programSlot = "LEGACY_SLOT").toEntity()
        )

        TrainingRepository(db, context).seedIfNeeded()

        assertTrue(db.exerciseMetadataUserOverrideDao().all().isEmpty())
        assertEquals(seed.category, db.exerciseDao().findByStableKey(seed.stableKey)!!.category)
        assertEquals(
            CanonicalExerciseMetadataRepositoryProvider.get(context)
                .runtimeMetadataCatalog()
                .resolve(seed)!!
                .programSlot,
            db.runtimeExerciseMetadataDao().findByStableKey(seed.stableKey)!!.programSlot
        )
    }

    @Test
    fun invalidBatchIsRejectedBeforeAnyOverrideIsWritten() = runBlocking {
        val db = newDatabase()
        val seed = canonicalSeed()
        db.exerciseDao().insertExercise(seed)
        val valid = overrideRow(seed.stableKey, "exercise.category", "User category")
        val invalid = overrideRow(
            stableKey = seed.stableKey,
            fieldKey = "exercise.defaultRestSeconds",
            value = "",
            isExplicitEmpty = true
        )

        val failure = runCatching {
            db.exerciseMetadataUserOverrideDao().upsertAll(listOf(valid, invalid))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(db.exerciseMetadataUserOverrideDao().all().isEmpty())
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun canonicalSeed(predicate: (Exercise) -> Boolean = { true }): Exercise =
        SeedData.exactExerciseMetadataByStableKey(context).values.first(predicate)

    private fun overrideRow(
        stableKey: String,
        fieldKey: String,
        value: String,
        isExplicitEmpty: Boolean = false
    ): ExerciseMetadataUserOverrideEntity {
        val definition = requireNotNull(ExerciseMetadataFieldPolicyRegistry.definition(fieldKey))
        return ExerciseMetadataUserOverrideEntity(
            stableKey = stableKey,
            fieldScope = definition.fieldScope.name,
            fieldKey = fieldKey,
            valueEncoding = definition.valueEncoding.name,
            value = value,
            isExplicitEmpty = isExplicitEmpty,
            source = ExerciseMetadataOverrideSource.USER_EDIT.name,
            semanticCanonicalRevisionAtEdit = "test-semantic-revision",
            updatedAt = 1L
        )
    }
}

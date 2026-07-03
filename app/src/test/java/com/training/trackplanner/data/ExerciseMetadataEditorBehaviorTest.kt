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
class ExerciseMetadataEditorBehaviorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun exerciseEditorDataForNewExerciseReturnsCustomDraftDefaultsAndSortedCopySources() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        insertExercise(db, name = "B source", stableKey = "source.b")
        insertExercise(db, name = "A source", stableKey = "source.a")

        val data = repository.exerciseEditorData(null)

        assertEquals("", data.exercise.name)
        assertTrue(data.exercise.category.isNotBlank())
        assertTrue(data.exercise.isCustom)
        assertEquals(RuntimeExerciseMetadataDefaults.forIdentity("", ""), data.metadata)
        assertTrue(data.options.values("activityKind").isNotEmpty())
        assertEquals(listOf("A source", "B source"), data.copySources.map { it.exercise.name })
    }

    @Test
    fun exerciseEditorDataForExistingExerciseReturnsEffectiveMetadataAndExcludesSelfCopySource() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val exerciseId = insertExercise(db, name = "Seed lift", stableKey = "seed.lift")
        insertExercise(db, name = "Other lift", stableKey = "other.lift")
        val override = RuntimeExerciseMetadataDefaults.forIdentity("seed.lift", "Seed lift")
            .copy(programSlot = "ROOM_SLOT")
        db.runtimeExerciseMetadataDao().upsert(override.toEntity())

        val data = repository.exerciseEditorData(exerciseId)

        assertEquals(exerciseId, data.exercise.id)
        assertEquals("Seed lift", data.exercise.name)
        assertEquals("seed.lift", data.exercise.stableKey)
        assertEquals("ROOM_SLOT", data.metadata.programSlot)
        assertFalse(data.copySources.any { it.exercise.id == exerciseId })
    }

    @Test
    fun saveExerciseEditorCreatesCustomExerciseWithUniqueStableKeyAndOverride() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val draft = repository.exerciseEditorData(null)
        val exercise = draft.exercise.copy(
            name = "Custom hinge",
            category = "Custom",
            defaultRestSeconds = 90
        )
        val metadata = draft.metadata.copy(
            programSlot = "CUSTOM_SLOT",
            analysisEligibility = MetadataTokenField.parse("FATIGUE")
        )

        val savedId = repository.saveExerciseEditor(draft.copy(exercise = exercise, metadata = metadata))
        val saved = db.exerciseDao().findById(savedId)!!
        val savedMetadata = db.runtimeExerciseMetadataDao().findByStableKey(saved.stableKey)!!.toRuntimeMetadata()

        assertTrue(saved.stableKey.isNotBlank())
        assertTrue(saved.stableKey.startsWith(UserExerciseStableKeyGenerator.PREFIX))
        assertTrue(saved.isCustom)
        assertEquals("Custom hinge", saved.name)
        assertEquals(saved.stableKey, savedMetadata.stableKey)
        assertEquals("Custom hinge", savedMetadata.exerciseName)
        assertEquals("CUSTOM_SLOT", savedMetadata.programSlot)
        assertFalse(savedMetadata.safeForSeedMutation)
    }

    @Test
    fun saveExerciseEditorUpdatesExistingExerciseWhilePreservingStableKey() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val exerciseId = insertExercise(
            db = db,
            name = "Existing custom",
            stableKey = "user_ex_existing",
            isCustom = true
        )
        val data = repository.exerciseEditorData(exerciseId)

        val savedId = repository.saveExerciseEditor(
            data.copy(
                exercise = data.exercise.copy(
                    name = "Existing custom edited",
                    category = "Edited",
                    defaultRestSeconds = 120
                ),
                metadata = data.metadata.copy(programSlot = "EDITED_SLOT")
            )
        )

        val saved = db.exerciseDao().findById(savedId)!!
        val savedMetadata = db.runtimeExerciseMetadataDao().findByStableKey("user_ex_existing")!!.toRuntimeMetadata()
        assertEquals(exerciseId, savedId)
        assertEquals("user_ex_existing", saved.stableKey)
        assertEquals("Existing custom edited", saved.name)
        assertEquals(120, saved.defaultRestSeconds)
        assertEquals("EDITED_SLOT", savedMetadata.programSlot)
        assertEquals("Existing custom edited", savedMetadata.exerciseName)
    }

    @Test
    fun saveExerciseEditorValidationFailsWithoutPartialWrites() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val baselineExerciseCount = db.exerciseDao().countExercises()
        val draft = repository.exerciseEditorData(null)

        assertFailsWithValidationError {
            repository.saveExerciseEditor(draft.copy(exercise = draft.exercise.copy(name = " ", category = "Custom")))
        }
        assertFailsWithValidationError {
            repository.saveExerciseEditor(draft.copy(exercise = draft.exercise.copy(name = "Name", category = " ")))
        }
        assertFailsWithValidationError {
            repository.saveExerciseEditor(
                draft.copy(exercise = draft.exercise.copy(name = "Name", category = "Custom", defaultRestSeconds = 3601))
            )
        }

        assertEquals(baselineExerciseCount, db.exerciseDao().countExercises())
        assertTrue(db.runtimeExerciseMetadataDao().all().isEmpty())
    }

    @Test
    fun resetExerciseMetadataOverrideForSeedExerciseDeletesOverrideAndRestoresSeedRow() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val seed = SeedData.exactExerciseMetadataByStableKey(context).values.first()
        val exerciseId = db.exerciseDao().insertExercise(
            seed.copy(
                id = 0,
                name = "Broken seed",
                category = "Broken",
                imageAssetName = "",
                isActive = false,
                archivedAt = 42L,
                needsReview = true
            )
        )
        db.runtimeExerciseMetadataDao().upsert(
            RuntimeExerciseMetadataDefaults.forIdentity(seed.stableKey, "Broken seed")
                .copy(programSlot = "OVERRIDE_SLOT")
                .toEntity()
        )

        val result = repository.resetExerciseMetadataOverride(exerciseId)
        val restored = db.exerciseDao().findById(exerciseId)!!

        assertTrue(result)
        assertNull(db.runtimeExerciseMetadataDao().findByStableKey(seed.stableKey))
        assertEquals(seed.name, restored.name)
        assertEquals(seed.category, restored.category)
        assertEquals(seed.stableKey, restored.stableKey)
        assertEquals(exerciseId, restored.id)
        assertFalse(restored.isActive)
        assertEquals(42L, restored.archivedAt)
        assertFalse(restored.isCustom)
        assertTrue(restored.needsReview)
    }

    @Test
    fun resetExerciseMetadataOverrideForMissingExerciseReturnsFalse() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)

        assertFalse(repository.resetExerciseMetadataOverride(999L))
        assertEquals(0, db.exerciseDao().countExercises())
        assertTrue(db.runtimeExerciseMetadataDao().all().isEmpty())
    }

    @Test
    fun resolveRuntimeMetadataAndByExerciseIdReflectOverridePriorityForAllExercises() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val overriddenId = insertExercise(db, name = "Override lift", stableKey = "override.lift")
        val defaultId = insertExercise(db, name = "Default lift", stableKey = "default.lift")
        db.runtimeExerciseMetadataDao().upsert(
            RuntimeExerciseMetadataDefaults.forIdentity("override.lift", "Override lift")
                .copy(programSlot = "OVERRIDE_SLOT")
                .toEntity()
        )

        val overridden = db.exerciseDao().findById(overriddenId)!!
        val resolved = repository.resolveRuntimeMetadata(overridden)
        val byId = repository.resolvedRuntimeMetadataByExerciseId()

        assertEquals("OVERRIDE_SLOT", resolved.programSlot)
        assertEquals(setOf(overriddenId, defaultId), byId.keys)
        assertEquals("OVERRIDE_SLOT", byId.getValue(overriddenId).programSlot)
        assertEquals(RuntimeExerciseMetadataDefaults.forExercise(db.exerciseDao().findById(defaultId)!!), byId.getValue(defaultId))
    }

    @Test
    fun setExerciseActivePreservesMetadataOverride() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val exerciseId = insertExercise(db, name = "Archive me", stableKey = "archive.me", isCustom = true)
        db.runtimeExerciseMetadataDao().upsert(
            RuntimeExerciseMetadataDefaults.forIdentity("archive.me", "Archive me")
                .copy(programSlot = "ARCHIVE_SLOT")
                .toEntity()
        )

        repository.setExerciseActive(exerciseId, false)
        val archived = db.exerciseDao().findById(exerciseId)!!
        assertFalse(archived.isActive)
        assertNotNull(archived.archivedAt)
        assertEquals("ARCHIVE_SLOT", db.runtimeExerciseMetadataDao().findByStableKey("archive.me")!!.programSlot)

        repository.setExerciseActive(exerciseId, true)
        val active = db.exerciseDao().findById(exerciseId)!!
        assertTrue(active.isActive)
        assertNull(active.archivedAt)
        assertEquals("ARCHIVE_SLOT", db.runtimeExerciseMetadataDao().findByStableKey("archive.me")!!.programSlot)
    }

    @Test
    fun deleteExerciseIfUnusedDeletesOnlyUnusedCustomExerciseAndItsOverride() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val unusedCustomId = insertExercise(db, "Unused custom", "user_ex_unused", isCustom = true)
        val workoutReferencedId = insertExercise(db, "Workout custom", "user_ex_workout", isCustom = true)
        val programReferencedId = insertExercise(db, "Program custom", "user_ex_program", isCustom = true)
        val seedId = insertExercise(db, "Seed", "seed.exercise", isCustom = false)
        listOf(
            "user_ex_unused" to "Unused custom",
            "user_ex_workout" to "Workout custom",
            "user_ex_program" to "Program custom",
            "seed.exercise" to "Seed"
        ).forEach { (stableKey, name) ->
            db.runtimeExerciseMetadataDao().upsert(RuntimeExerciseMetadataDefaults.forIdentity(stableKey, name).toEntity())
        }
        insertWorkoutReference(db, workoutReferencedId)
        insertProgramReference(db, programReferencedId)

        assertEquals(ExerciseDeleteResult(deleted = true, referenced = false), repository.deleteExerciseIfUnused(unusedCustomId))
        assertNull(db.exerciseDao().findById(unusedCustomId))
        assertNull(db.runtimeExerciseMetadataDao().findByStableKey("user_ex_unused"))

        assertEquals(ExerciseDeleteResult(deleted = false, referenced = true), repository.deleteExerciseIfUnused(workoutReferencedId))
        assertNotNull(db.exerciseDao().findById(workoutReferencedId))
        assertNotNull(db.runtimeExerciseMetadataDao().findByStableKey("user_ex_workout"))

        assertEquals(ExerciseDeleteResult(deleted = false, referenced = true), repository.deleteExerciseIfUnused(programReferencedId))
        assertNotNull(db.exerciseDao().findById(programReferencedId))
        assertNotNull(db.runtimeExerciseMetadataDao().findByStableKey("user_ex_program"))

        assertEquals(ExerciseDeleteResult(deleted = false, referenced = true), repository.deleteExerciseIfUnused(seedId))
        assertNotNull(db.exerciseDao().findById(seedId))
        assertNotNull(db.runtimeExerciseMetadataDao().findByStableKey("seed.exercise"))
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun repository(db: TrainingDatabase): TrainingRepository =
        TrainingRepository(db, context)

    private suspend fun insertExercise(
        db: TrainingDatabase,
        name: String,
        stableKey: String,
        isCustom: Boolean = false
    ): Long =
        db.exerciseDao().insertExercise(
            Exercise(
                name = name,
                category = "Strength",
                stableKey = stableKey,
                isCustom = isCustom
            )
        )

    private suspend fun insertWorkoutReference(db: TrainingDatabase, exerciseId: Long) {
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = "2026-07-03",
                exerciseId = exerciseId,
                exerciseName = "Referenced",
                category = "Strength"
            )
        )
        db.workoutDao().insertSet(WorkoutSet(entryId = entryId, setIndex = 1))
    }

    private suspend fun insertProgramReference(db: TrainingDatabase, exerciseId: Long) {
        val programId = db.programDao().insertProgram(
            TrainingProgram(name = "Program", durationDays = 7)
        )
        db.programDao().insertProgramItem(
            TrainingProgramItem(
                programId = programId,
                weekNumber = 1,
                dayOfWeek = 1,
                orderIndex = 1,
                exerciseId = exerciseId,
                exerciseName = "Referenced",
                category = "Strength"
            )
        )
    }

    private suspend fun assertFailsWithValidationError(block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}

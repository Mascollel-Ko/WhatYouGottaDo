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
        val exerciseStableKey = insertExercise(db, name = "Seed lift", stableKey = "seed.lift")
        insertExercise(db, name = "Other lift", stableKey = "other.lift")
        val override = RuntimeExerciseMetadataDefaults.forIdentity("seed.lift", "Seed lift")
            .copy(programSlot = "ROOM_SLOT")
        db.runtimeExerciseMetadataDao().upsert(override.toEntity())

        val data = repository.exerciseEditorData(exerciseStableKey)

        assertEquals(exerciseStableKey, data.exercise.stableKey)
        assertEquals("Seed lift", data.exercise.name)
        assertEquals("seed.lift", data.exercise.stableKey)
        assertEquals("ROOM_SLOT", data.metadata.programSlot)
        assertFalse(data.copySources.any { it.exercise.stableKey == exerciseStableKey })
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

        val savedKey = repository.saveExerciseEditor(draft.copy(exercise = exercise, metadata = metadata))
        val saved = db.exerciseDao().findByStableKey(savedKey)!!
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
        val exerciseStableKey = insertExercise(
            db = db,
            name = "Existing custom",
            stableKey = "user_ex_existing",
            isCustom = true
        )
        val data = repository.exerciseEditorData(exerciseStableKey)

        val savedKey = repository.saveExerciseEditor(
            data.copy(
                exercise = data.exercise.copy(
                    name = "Existing custom edited",
                    category = "Edited",
                    defaultRestSeconds = 120
                ),
                metadata = data.metadata.copy(programSlot = "EDITED_SLOT")
            )
        )

        val saved = db.exerciseDao().findByStableKey(savedKey)!!
        val savedMetadata = db.runtimeExerciseMetadataDao().findByStableKey("user_ex_existing")!!.toRuntimeMetadata()
        assertEquals(exerciseStableKey, savedKey)
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
        db.exerciseDao().insertExercise(
            seed.copy(
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

        val result = repository.resetExerciseMetadataOverride(seed.stableKey)
        val restored = db.exerciseDao().findByStableKey(seed.stableKey)!!
        val restoredMetadata = db.runtimeExerciseMetadataDao()
            .findByStableKey(seed.stableKey)!!
            .toRuntimeMetadata()

        assertTrue(result)
        assertEquals(
            CanonicalExerciseMetadataRepositoryProvider.get(context)
                .runtimeMetadataCatalog()
                .resolve(seed),
            restoredMetadata
        )
        assertEquals(seed.name, restored.name)
        assertEquals(seed.category, restored.category)
        assertEquals(seed.stableKey, restored.stableKey)
        assertFalse(restored.isActive)
        assertEquals(42L, restored.archivedAt)
        assertFalse(restored.isCustom)
        assertTrue(restored.needsReview)
    }

    @Test
    fun resetExerciseMetadataOverrideForMissingExerciseReturnsFalse() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)

        assertFalse(repository.resetExerciseMetadataOverride("missing.exercise"))
        assertEquals(0, db.exerciseDao().countExercises())
        assertTrue(db.runtimeExerciseMetadataDao().all().isEmpty())
    }

    @Test
    fun resolveRuntimeMetadataAndByExerciseIdReflectOverridePriorityForAllExercises() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val overriddenKey = insertExercise(db, name = "Override lift", stableKey = "override.lift")
        val defaultKey = insertExercise(db, name = "Default lift", stableKey = "default.lift")
        db.runtimeExerciseMetadataDao().upsert(
            RuntimeExerciseMetadataDefaults.forIdentity("override.lift", "Override lift")
                .copy(programSlot = "OVERRIDE_SLOT")
                .toEntity()
        )

        val overridden = db.exerciseDao().findByStableKey(overriddenKey)!!
        val resolved = repository.resolveRuntimeMetadata(overridden)
        val byKey = repository.resolvedRuntimeMetadataByExerciseStableKey()

        assertEquals("OVERRIDE_SLOT", resolved.programSlot)
        assertEquals(setOf(overriddenKey, defaultKey), byKey.keys)
        assertEquals("OVERRIDE_SLOT", byKey.getValue(overriddenKey).programSlot)
        assertEquals(
            RuntimeExerciseMetadataDefaults.forExercise(db.exerciseDao().findByStableKey(defaultKey)!!),
            byKey.getValue(defaultKey)
        )
    }

    @Test
    fun setExerciseActivePreservesMetadataOverride() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val exerciseStableKey = insertExercise(db, name = "Archive me", stableKey = "archive.me", isCustom = true)
        db.runtimeExerciseMetadataDao().upsert(
            RuntimeExerciseMetadataDefaults.forIdentity("archive.me", "Archive me")
                .copy(programSlot = "ARCHIVE_SLOT")
                .toEntity()
        )

        repository.setExerciseActive(exerciseStableKey, false)
        val archived = db.exerciseDao().findByStableKey(exerciseStableKey)!!
        assertFalse(archived.isActive)
        assertNotNull(archived.archivedAt)
        assertEquals("ARCHIVE_SLOT", db.runtimeExerciseMetadataDao().findByStableKey("archive.me")!!.programSlot)

        repository.setExerciseActive(exerciseStableKey, true)
        val active = db.exerciseDao().findByStableKey(exerciseStableKey)!!
        assertTrue(active.isActive)
        assertNull(active.archivedAt)
        assertEquals("ARCHIVE_SLOT", db.runtimeExerciseMetadataDao().findByStableKey("archive.me")!!.programSlot)
    }

    @Test
    fun historyOnlyExerciseCannotBeReactivated() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val history = SeedData.exactExerciseMetadataByStableKey(context).getValue("ex_bd072cd")
        db.exerciseDao().insertExercise(history)

        repository.setExerciseActive(history.stableKey, true)

        assertFalse(db.exerciseDao().findByStableKey(history.stableKey)!!.isActive)
    }

    @Test
    fun deleteExerciseIfUnusedDeletesOnlyUnusedCustomExerciseAndItsOverride() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val unusedCustomKey = insertExercise(db, "Unused custom", "user_ex_unused", isCustom = true)
        val workoutReferencedKey = insertExercise(db, "Workout custom", "user_ex_workout", isCustom = true)
        val programReferencedKey = insertExercise(db, "Program custom", "user_ex_program", isCustom = true)
        val seedKey = insertExercise(db, "Seed", "seed.exercise", isCustom = false)
        listOf(
            "user_ex_unused" to "Unused custom",
            "user_ex_workout" to "Workout custom",
            "user_ex_program" to "Program custom",
            "seed.exercise" to "Seed"
        ).forEach { (stableKey, name) ->
            db.runtimeExerciseMetadataDao().upsert(RuntimeExerciseMetadataDefaults.forIdentity(stableKey, name).toEntity())
        }
        insertWorkoutReference(db, workoutReferencedKey)
        insertProgramReference(db, programReferencedKey)

        assertEquals(ExerciseDeleteResult(deleted = true, referenced = false), repository.deleteExerciseIfUnused(unusedCustomKey))
        assertNull(db.exerciseDao().findByStableKey(unusedCustomKey))
        assertNull(db.runtimeExerciseMetadataDao().findByStableKey("user_ex_unused"))

        assertEquals(ExerciseDeleteResult(deleted = false, referenced = true), repository.deleteExerciseIfUnused(workoutReferencedKey))
        assertNotNull(db.exerciseDao().findByStableKey(workoutReferencedKey))
        assertNotNull(db.runtimeExerciseMetadataDao().findByStableKey("user_ex_workout"))

        assertEquals(ExerciseDeleteResult(deleted = false, referenced = true), repository.deleteExerciseIfUnused(programReferencedKey))
        assertNotNull(db.exerciseDao().findByStableKey(programReferencedKey))
        assertNotNull(db.runtimeExerciseMetadataDao().findByStableKey("user_ex_program"))

        assertEquals(ExerciseDeleteResult(deleted = false, referenced = true), repository.deleteExerciseIfUnused(seedKey))
        assertNotNull(db.exerciseDao().findByStableKey(seedKey))
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
    ): String {
        db.exerciseDao().insertExercise(
            Exercise(
                name = name,
                category = "Strength",
                stableKey = stableKey,
                isCustom = isCustom
            )
        )
        return stableKey
    }

    private suspend fun insertWorkoutReference(db: TrainingDatabase, exerciseStableKey: String) {
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = "2026-07-03",
                exerciseStableKey = exerciseStableKey,
                exerciseName = "Referenced",
                category = "Strength"
            )
        )
        db.workoutDao().insertSet(WorkoutSet(entryId = entryId, setIndex = 1))
    }

    private suspend fun insertProgramReference(db: TrainingDatabase, exerciseStableKey: String) {
        val programId = db.programDao().insertProgram(
            TrainingProgram(name = "Program", durationDays = 7)
        )
        db.programDao().insertProgramItem(
            TrainingProgramItem(
                programId = programId,
                weekNumber = 1,
                dayOfWeek = 1,
                orderIndex = 1,
                exerciseStableKey = exerciseStableKey,
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

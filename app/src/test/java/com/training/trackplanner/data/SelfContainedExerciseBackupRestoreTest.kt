package com.training.trackplanner.data

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SelfContainedExerciseBackupRestoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()
    private val databaseRoot = File(System.getProperty("java.io.tmpdir"), "wygd-room-${UUID.randomUUID()}")
    private val databaseContext = object : ContextWrapper(context) {
        override fun getDatabasePath(name: String): File = File(databaseRoot, name)
    }

    @After
    fun closeDatabases() {
        databases.forEach { database -> runCatching { database.close() } }
        databaseRoot.deleteRecursively()
    }

    @Test
    fun currentBuiltInUsesCurrentCanonicalAuthorityForMaterializedBackupDifferences() = runBlocking {
        val source = inMemoryDatabase()
        val canonical = SeedData.exactExerciseMetadataByStableKey(context).getValue("barbell_deadlift")
        val edited = canonical.copy(
            name = "Legacy deadlift label",
            equipment = "BENCH|DUMBBELL",
            equipmentTags = "BENCH|DUMBBELL",
            primaryMuscles = "GLUTEUS_MAXIMUS|QUADRICEPS",
            secondaryMuscles = "FOREARMS|HAMSTRINGS",
            movementPattern = "USER_HINGE",
            analysisEligibility = "BALANCE|FATIGUE"
        )
        source.exerciseDao().insertExercise(edited)
        source.runtimeExerciseMetadataDao().upsert(
            RuntimeExerciseMetadataDefaults.forExercise(edited).copy(
                exerciseName = edited.name,
                programSlot = "MAIN_UPPER_PULL",
                movementFamily = "USER_EDITED_FAMILY",
                analysisEligibility = MetadataTokenField.parse("BALANCE|FATIGUE"),
                safeForSeedMutation = false
            ).toEntity()
        )
        source.exerciseRoleRelationDao().upsertTrainingRoles(
            listOf(ExerciseTrainingRoleRelation(edited.stableKey, "STABILITY", "USER", "APPROVED"))
        )
        source.exerciseRoleRelationDao().upsertProgramSlotCapabilities(
            listOf(ExerciseProgramSlotCapabilityRelation(edited.stableKey, "ACCESSORY_SLOT", "USER", "APPROVED"))
        )
        insertWorkout(source, edited, id = 11, createdAt = 100L)
        insertProgram(source, edited, "user_program_canonical_name")

        val backup = export(source)
        val targetName = namedDatabaseName()
        var target = namedDatabase(targetName)
        var repository = TrainingRepository(target, context)
        repository.seedIfNeeded()
        val currentKeys = target.exerciseDao().allExercises().mapTo(mutableSetOf(), Exercise::stableKey)

        repository.importRecordsBackup(writeBackup(backup))
        assertCurrentBuiltInState(target, canonical)
        assertTrue(target.exerciseDao().allExercises().map(Exercise::stableKey).containsAll(currentKeys))

        target.appMetaDao().upsert(AppMeta("exercise_seed_version", "0"))
        repository.seedIfNeeded()
        assertCurrentBuiltInState(target, canonical)

        target.close()
        databases.remove(target)
        target = namedDatabase(targetName)
        repository = TrainingRepository(target, context)
        repository.seedIfNeeded()
        assertCurrentBuiltInState(target, canonical)

        val parsed = RecordCsvBackupRestore.parse(export(target)) as RecordCsvImportData.Restore
        val snapshots = parsed.metadataSnapshotRows.filter { row -> row.stableKey == canonical.stableKey }
        assertTrue(parsed.manifest != null)
        assertEquals(
            ExerciseMetadataFieldPolicyRegistry.canonicalize(
                canonical.equipment,
                ExerciseMetadataValueEncoding.TOKEN_SET
            ),
            snapshots.single { it.fieldKey == "exercise.equipment" }.value
        )
        assertEquals(
            CanonicalExerciseMetadataRepositoryProvider.get(context)
                .runtimeMetadataCatalog()
                .resolve(canonical)!!
                .programSlot,
            snapshots.single { it.fieldKey == "runtime.programSlot" }.value
        )
        assertTrue(parsed.metadataUserOverrideRows.none { it.stableKey == canonical.stableKey })
    }

    @Test
    fun explicitUserOverridesRoundTripForCurrentBuiltIn() = runBlocking {
        val source = inMemoryDatabase()
        val canonical = SeedData.exactExerciseMetadataByStableKey(context).getValue("barbell_deadlift")
        source.exerciseDao().insertExercise(canonical)
        source.exerciseMetadataUserOverrideDao().upsertAll(
            listOf(
                overrideRow(canonical.stableKey, "exercise.primaryMuscles", "GLUTEUS_MAXIMUS|QUADRICEPS"),
                overrideRow(canonical.stableKey, "runtime.programSlot", "MAIN_UPPER_PULL")
            )
        )

        val backup = export(source)
        val target = inMemoryDatabase()
        val repository = TrainingRepository(target, context)
        repository.seedIfNeeded()
        repository.importRecordsBackup(writeBackup(backup))

        val restoredOverrides = target.exerciseMetadataUserOverrideDao().findByStableKey(canonical.stableKey)
        val effective = repository.exerciseEditorData(canonical.stableKey)
        assertEquals(2, restoredOverrides.size)
        assertEquals("GLUTEUS_MAXIMUS|QUADRICEPS", effective.exercise.primaryMuscles)
        assertEquals("MAIN_UPPER_PULL", effective.metadata.programSlot)

        val roundTrip = RecordCsvBackupRestore.parse(export(target)) as RecordCsvImportData.Restore
        assertEquals(
            restoredOverrides.map { it.fieldKey to it.value }.toSet(),
            roundTrip.metadataUserOverrideRows
                .filter { it.stableKey == canonical.stableKey }
                .map { it.fieldKey to it.value }
                .toSet()
        )
    }

    @Test
    fun absentCanonicalIdentityRestoresAsHistoricalAndRoundTrips() = runBlocking {
        val source = inMemoryDatabase()
        val historical = Exercise(
            stableKey = "removed_complete_exercise",
            name = "Removed complete exercise",
            category = "Strength",
            equipment = "CABLE",
            primaryMuscles = "RHOMBOIDS",
            secondaryMuscles = "BICEPS_BRACHII",
            movementPattern = "ROW",
            analysisEligibility = "FATIGUE",
            isActive = true
        )
        source.exerciseDao().insertExercise(historical)
        val runtime = RuntimeExerciseMetadataDefaults.forExercise(historical).copy(
            programSlot = "SECONDARY_STRENGTH_SLOT",
            analysisEligibility = MetadataTokenField.parse("FATIGUE")
        )
        source.runtimeExerciseMetadataDao().upsert(runtime.toEntity())
        source.exerciseRoleRelationDao().upsertTrainingRoles(
            listOf(ExerciseTrainingRoleRelation(historical.stableKey, "STRENGTH", "USER", "APPROVED"))
        )
        insertWorkout(source, historical, id = 21, createdAt = 200L)
        insertProgram(source, historical, "user_program_historical")

        val target = inMemoryDatabase()
        val repository = TrainingRepository(target, context)
        repository.seedIfNeeded()
        val currentKeys = target.exerciseDao().allExercises().mapTo(mutableSetOf(), Exercise::stableKey)
        val result = repository.importRecordsBackup(writeBackup(export(source)))
        val restored = target.exerciseDao().findByStableKey(historical.stableKey)!!
        val restoredRuntime = target.runtimeExerciseMetadataDao().findByStableKey(historical.stableKey)!!.toRuntimeMetadata()

        assertEquals(1, result.entryCount)
        assertFalse(restored.isActive)
        assertEquals("HISTORY_ONLY", restored.planningEligibility)
        assertEquals(historical.name, restored.name)
        assertEquals(historical.equipment, restored.equipment)
        assertEquals(historical.primaryMuscles, restored.primaryMuscles)
        assertEquals("HISTORY_ONLY", restoredRuntime.planningEligibility)
        assertEquals("SECONDARY_STRENGTH_SLOT", restoredRuntime.programSlot)
        assertEquals(historical.stableKey, target.workoutDao().allEntries().single().exerciseStableKey)
        assertEquals(historical.stableKey, target.programDao().allProgramItems().single().exerciseStableKey)
        assertTrue(target.exerciseDao().allExercises().map(Exercise::stableKey).containsAll(currentKeys))
        assertFalse(ProgramCandidateAuthority.allows(restored.stableKey))

        val roundTrip = RecordCsvBackupRestore.parse(export(target)) as RecordCsvImportData.Restore
        assertTrue(roundTrip.exerciseRows.any { row -> row.stableKey == historical.stableKey })
        assertTrue(roundTrip.metadataSnapshotRows.any { row -> row.stableKey == historical.stableKey })
    }

    @Test
    fun distinctSourceEntryIdentitiesPreserveIdenticalEntriesAndRepeatedRestoreIsIdempotent() = runBlocking {
        val source = inMemoryDatabase()
        val exercise = SeedData.exactExerciseMetadataByStableKey(context).getValue("barbell_back_squat")
        source.exerciseDao().insertExercise(exercise)
        source.runtimeExerciseMetadataDao().upsert(RuntimeExerciseMetadataDefaults.forExercise(exercise).toEntity())
        insertWorkout(source, exercise, id = 31, createdAt = 300L)
        insertWorkout(source, exercise, id = 32, createdAt = 300L)
        val backup = export(source)
        val target = inMemoryDatabase()
        val repository = TrainingRepository(target, context)

        val first = repository.importRecordsBackup(writeBackup(backup))
        val second = repository.importRecordsBackup(writeBackup(backup))
        val entries = target.workoutDao().allEntries()

        assertEquals(2, first.entryCount)
        assertEquals(0, second.entryCount)
        assertEquals(2, second.skippedDuplicateCount)
        assertEquals(2, entries.size)
        assertEquals(2, entries.mapNotNull(WorkoutEntry::backupSourceId).distinct().size)
    }

    @Test
    fun allCurrentHistoryOnlyIdentitiesRestoreWithoutHardcodedCount() = runBlocking {
        val historyOnly = SeedData.exactExerciseMetadataByStableKey(context).values
            .filter { exercise -> exercise.planningEligibility == "HISTORY_ONLY" }
        val keys = historyOnly.mapTo(mutableSetOf(), Exercise::stableKey)
        assertTrue(keys.containsAll(setOf("single_leg_rdl", "ex_bd072cd")))
        val target = inMemoryDatabase()

        val result = TrainingRepository(target, context).importRecordsBackup(
            writeBackup(RecordCsvBackupRestore.buildRestoreCsv(emptyList(), emptyList(), exercises = historyOnly))
        )

        assertEquals(historyOnly.size, result.exerciseCount)
        assertEquals(keys, target.exerciseDao().allExercises().mapTo(mutableSetOf(), Exercise::stableKey))
        assertTrue(target.exerciseDao().allExercises().all { exercise ->
            !exercise.isActive && exercise.planningEligibility == "HISTORY_ONLY"
        })
    }

    @Test
    fun explicitEmptyIsRestoredWhileMissingFieldKeepsCurrentValue() {
        val current = Exercise(
            stableKey = "field_policy_test",
            name = "Current",
            category = "Strength",
            equipment = "BARBELL",
            secondaryMuscles = "HAMSTRINGS"
        )
        val restored = ExerciseMetadataFieldPolicyRegistry.restore(
            exercise = current,
            runtimeMetadata = RuntimeExerciseMetadataDefaults.forExercise(current),
            rows = listOf(
                ExerciseMetadataSnapshotRow(
                    stableKey = current.stableKey,
                    fieldKey = "exercise.secondaryMuscles",
                    fieldScope = ExerciseMetadataFieldScope.EXERCISE,
                    valueEncoding = ExerciseMetadataValueEncoding.TOKEN_SET,
                    value = "",
                    isExplicitEmpty = true
                )
            )
        )

        assertEquals("", restored.exercise.secondaryMuscles)
        assertEquals("BARBELL", restored.exercise.equipment)
    }

    @Test
    fun contradictoryBackupDefinitionFailsBeforeChangingDatabase() = runBlocking {
        val target = inMemoryDatabase()
        val existing = Exercise("keep_identity", "Keep", "Strength")
        target.exerciseDao().insertExercise(existing)
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            exercises = listOf(
                Exercise("contradictory_identity", "First", "Strength"),
                Exercise("contradictory_identity", "Second", "Mobility")
            )
        )

        val repository = TrainingRepository(target, context)
        assertTrue(runCatching { repository.importRecordsBackup(writeBackup(csv)) }.isFailure)
        assertEquals(listOf(existing), target.exerciseDao().allExercises())
        assertNull(repository.latestDataTransferReport())
    }

    private fun inMemoryDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also(databases::add)

    private fun namedDatabase(name: String): TrainingDatabase =
        Room.databaseBuilder(
            databaseContext.also { databaseRoot.mkdirs() },
            TrainingDatabase::class.java,
            name
        )
            .allowMainThreadQueries()
            .build()
            .also(databases::add)

    private fun namedDatabaseName(): String =
        "self-contained-backup-${UUID.randomUUID()}.db"

    private suspend fun insertWorkout(
        db: TrainingDatabase,
        exercise: Exercise,
        id: Long,
        createdAt: Long
    ) {
        db.workoutDao().insertEntry(
            WorkoutEntry(
                id = id,
                date = "2026-08-05",
                exerciseStableKey = exercise.stableKey,
                exerciseName = exercise.name,
                category = exercise.category,
                restSeconds = 120,
                notes = "same session",
                rpe = 8.0,
                createdAt = createdAt,
                completedAt = 500L,
                displayOrder = 4,
                firstConfirmedAt = 400L,
                performedAt = 450L
            )
        )
        db.workoutDao().insertSet(
            WorkoutSet(
                entryId = id,
                setIndex = 1,
                reps = 5,
                weightKg = 100.0,
                confirmed = true,
                manualWeight = true,
                rpe = 8.0,
                restSecondsOverride = 150
            )
        )
    }

    private suspend fun insertProgram(db: TrainingDatabase, exercise: Exercise, stableKey: String) {
        val programId = db.programDao().insertProgram(
            TrainingProgram(stableKey = stableKey, name = "Backup program", durationDays = 7)
        )
        db.programDao().insertProgramItem(
            TrainingProgramItem(
                programId = programId,
                weekNumber = 1,
                dayOfWeek = 1,
                orderIndex = 1,
                exerciseStableKey = exercise.stableKey,
                exerciseName = exercise.name,
                category = exercise.category,
                setCount = 3,
                reps = 5,
                weightKg = 100.0
            )
        )
    }

    private suspend fun assertCurrentBuiltInState(db: TrainingDatabase, canonical: Exercise) {
        val exercise = db.exerciseDao().findByStableKey("barbell_deadlift")!!
        val runtime = db.runtimeExerciseMetadataDao().findByStableKey(exercise.stableKey)!!.toRuntimeMetadata()
        val canonicalRepository = CanonicalExerciseMetadataRepositoryProvider.get(context)
        val canonicalRuntime = canonicalRepository.runtimeMetadataCatalog().resolve(canonical)!!
        val roles = db.exerciseRoleRelationDao().allTrainingRoles()
            .filter { row -> row.exerciseStableKey == exercise.stableKey }
            .mapTo(mutableSetOf(), ExerciseTrainingRoleRelation::trainingRoleCode)
        val capabilities = db.exerciseRoleRelationDao().allProgramSlotCapabilities()
            .filter { row -> row.exerciseStableKey == exercise.stableKey }
            .mapTo(mutableSetOf(), ExerciseProgramSlotCapabilityRelation::capabilityCode)

        assertEquals(canonical.name, exercise.name)
        assertEquals(canonical.equipment, exercise.equipment)
        assertEquals(canonical.primaryMuscles, exercise.primaryMuscles)
        assertEquals(canonical.secondaryMuscles, exercise.secondaryMuscles)
        assertEquals(canonical.movementPattern, exercise.movementPattern)
        assertEquals(canonical.name, runtime.exerciseName)
        assertEquals(canonicalRuntime.programSlot, runtime.programSlot)
        assertEquals(canonicalRuntime.movementFamily, runtime.movementFamily)
        assertEquals(
            canonicalRepository.trainingRoleRelations()
                .filter { it.exerciseStableKey == canonical.stableKey }
                .mapTo(mutableSetOf(), ExerciseTrainingRoleRelation::trainingRoleCode),
            roles
        )
        assertEquals(
            canonicalRepository.programSlotCapabilityRelations()
                .filter { it.exerciseStableKey == canonical.stableKey }
                .mapTo(mutableSetOf(), ExerciseProgramSlotCapabilityRelation::capabilityCode),
            capabilities
        )
        assertEquals(canonical.name, db.workoutDao().allEntries().single().exerciseName)
        assertEquals(canonical.name, db.programDao().allProgramItems().single().exerciseName)
    }

    private fun overrideRow(
        stableKey: String,
        fieldKey: String,
        value: String
    ): ExerciseMetadataUserOverrideEntity {
        val definition = requireNotNull(ExerciseMetadataFieldPolicyRegistry.definition(fieldKey))
        return ExerciseMetadataUserOverrideEntity(
            stableKey = stableKey,
            fieldScope = definition.fieldScope.name,
            fieldKey = fieldKey,
            valueEncoding = definition.valueEncoding.name,
            value = value,
            isExplicitEmpty = false,
            source = ExerciseMetadataOverrideSource.USER_EDIT.name,
            semanticCanonicalRevisionAtEdit = "test-semantic-revision",
            updatedAt = 1L
        )
    }

    private suspend fun export(db: TrainingDatabase): String {
        val file = File.createTempFile("self-contained-backup", ".csv")
        file.deleteOnExit()
        TrainingRepository(db, context).exportRecordsBackup(Uri.fromFile(file))
        return file.readText(Charsets.UTF_8)
    }

    private fun writeBackup(csv: String): Uri {
        val file = File.createTempFile("self-contained-restore", ".csv")
        file.writeText(csv, Charsets.UTF_8)
        file.deleteOnExit()
        return Uri.fromFile(file)
    }
}

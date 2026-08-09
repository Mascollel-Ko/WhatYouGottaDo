package com.training.trackplanner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
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
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SafeBackupRestoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()

    @After
    fun closeDatabases() {
        databases.forEach { db -> runCatching { db.close() } }
    }

    @Test
    fun preflightAndBothChoicePlansDoNotMutateRoom() = runBlocking {
        val backup = primaryBackup()
        val target = newDatabase()
        populatePrimaryTarget(target)
        val before = snapshot(target)
        val repository = TrainingRepository(target, context)

        val preparation = repository.prepareRecordsRestore(writeBackup(backup))
        assertTrue(preparation.hasOverlappingWorkoutDates)
        repository.planRecordsRestore(
            WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES,
            ExerciseListRestoreMode.APPLY_BACKUP_ACTIVE_EXERCISE_LIST
        )

        assertEquals(before, snapshot(target))
        assertNull(repository.latestDataTransferReport())
        repository.cancelPendingRecordsRestore()
    }

    @Test
    fun twoByTwoRestoreMatrixPreservesWorkoutAndExerciseAuthority() = runBlocking {
        val backup = primaryBackup()
        val parsed = RecordCsvBackupRestore.parse(backup) as RecordCsvImportData.Restore
        assertTrue(parsed.exerciseRows.single { it.stableKey == SHARED_CUSTOM_KEY }.isActive)
        WorkoutRestoreMode.entries.forEach { workoutMode ->
            ExerciseListRestoreMode.entries.forEach { exerciseMode ->
                val target = newDatabase()
                populatePrimaryTarget(target)
                assertFalse(target.exerciseDao().findByStableKey(SHARED_CUSTOM_KEY)!!.isActive)
                val repository = TrainingRepository(target, context)

                val preparation = repository.prepareRecordsRestore(writeBackup(backup))
                val impact = repository.planRecordsRestore(workoutMode, exerciseMode)
                val result = repository.confirmRecordsRestore()

                assertEquals(2, preparation.impact.overlappingWorkoutDateCount)
                assertEquals(2, impact.currentEntriesOnOverlappingDates)
                assertEquals(2, impact.backupEntriesOnOverlappingDates)
                assertEquals(
                    "$workoutMode/$exerciseMode: $impact",
                    1,
                    impact.activeExercisesThatWouldBeAddedCount
                )
                assertEquals(1, impact.sameStableKeyCustomDefinitionsThatWouldBeReplacedCount)
                assertEquals(1, impact.backupOverrideFieldsThatWouldReplaceCurrentCount)
                assertEquals(3, result.entryCount)
                val expectedTotal = if (workoutMode == WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES) 4 else 6
                assertEquals(expectedTotal, target.workoutDao().allEntries().size)
                assertEquals(expectedTotal, target.workoutDao().allSets().size)
                assertEquals(
                    if (workoutMode == WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES) 1 else 2,
                    target.workoutDao().entriesWithSets(DATE_B).size
                )

                val entries = target.workoutDao().allEntries()
                assertEquals(entries.size, entries.mapNotNull(WorkoutEntry::backupSourceId).distinct().size)
                assertTrue(entries.all { entry -> target.exerciseDao().findByStableKey(entry.exerciseStableKey) != null })

                val representedCanonical = target.exerciseDao().findByStableKey(CANONICAL_KEY)!!
                val sharedCustom = target.exerciseDao().findByStableKey(SHARED_CUSTOM_KEY)!!
                val targetOnly = target.exerciseDao().findByStableKey(TARGET_ONLY_CUSTOM_KEY)!!
                val targetOnlyMissing = target.exerciseDao().findByStableKey(TARGET_ONLY_MISSING_KEY)!!
                val omittedCanonical = target.exerciseDao().findByStableKey(OMITTED_CANONICAL_KEY)!!
                assertEquals("Backup shared custom", sharedCustom.name)
                assertTrue(sharedCustom.isActive)
                assertEquals(false, omittedCanonical.isActive)
                assertEquals(88L, omittedCanonical.archivedAt)
                assertTrue(omittedCanonical.needsReview)
                assertNotNull(target.exerciseMetadataUserOverrideDao().findField(
                    OMITTED_CANONICAL_KEY,
                    ExerciseMetadataFieldScope.EXERCISE.name,
                    "exercise.description"
                ))

                val representedOverrides = target.exerciseMetadataUserOverrideDao()
                    .findByStableKey(CANONICAL_KEY)
                    .associate { row -> row.fieldKey to row.value }
                assertEquals("Backup category", representedOverrides["exercise.category"])
                if (exerciseMode == ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES) {
                    assertTrue(representedCanonical.isActive)
                    assertTrue(targetOnly.isActive)
                    assertTrue(targetOnlyMissing.isActive)
                    assertEquals("Target description", representedOverrides["exercise.description"])
                    assertEquals(0, impact.targetOnlyNonCanonicalExercisesDeactivatedCount)
                    assertEquals(0, impact.currentMetadataOverrideFieldsThatWouldBeRemovedCount)
                } else {
                    assertFalse(representedCanonical.isActive)
                    assertFalse(targetOnly.isActive)
                    assertFalse(targetOnlyMissing.isActive)
                    assertTrue(representedOverrides.keys == setOf("exercise.category"))
                    assertEquals(2, impact.targetOnlyNonCanonicalExercisesDeactivatedCount)
                    assertEquals(1, impact.currentMetadataOverrideFieldsThatWouldBeRemovedCount)
                }
                assertNotNull(target.exerciseMetadataUserOverrideDao().findField(
                    TARGET_ONLY_MISSING_KEY,
                    ExerciseMetadataFieldScope.EXERCISE.name,
                    "exercise.description"
                ))

                assertEquals(
                    "TARGET",
                    target.appMetaDao().value(WorkoutSourceIdentityProvider.SOURCE_DATABASE_LINEAGE_ID)
                )
                assertEquals(
                    "TARGET_REQUIRED",
                    target.appMetaDao().value(ExerciseMetadataReconciliationService.REQUIRED_KEY)
                )
                assertEquals(
                    "TARGET_COMPLETED",
                    target.appMetaDao().value(ExerciseMetadataReconciliationService.COMPLETED_KEY)
                )
            }
        }
    }

    @Test
    fun representedZeroOverridesClearOnlyRepresentedAuthorityInApplyMode() = runBlocking {
        val source = newDatabase()
        val represented = seed(CANONICAL_KEY).copy(isActive = true)
        source.exerciseDao().insertExercise(represented)
        val backup = export(source)

        val target = newDatabase()
        val omitted = seed(OMITTED_CANONICAL_KEY).copy(isActive = false, archivedAt = 88L, needsReview = true)
        target.exerciseDao().insertExercise(represented)
        target.exerciseDao().insertExercise(omitted)
        target.exerciseMetadataUserOverrideDao().upsert(override(CANONICAL_KEY, "exercise.description", "remove me"))
        target.exerciseMetadataUserOverrideDao().upsert(
            override(OMITTED_CANONICAL_KEY, "exercise.description", "keep me")
        )
        val repository = TrainingRepository(target, context)

        repository.prepareRecordsRestore(writeBackup(backup))
        repository.planRecordsRestore(
            WorkoutRestoreMode.APPEND_TO_CURRENT,
            ExerciseListRestoreMode.APPLY_BACKUP_ACTIVE_EXERCISE_LIST
        )
        repository.confirmRecordsRestore()

        assertTrue(target.exerciseMetadataUserOverrideDao().findByStableKey(CANONICAL_KEY).isEmpty())
        assertEquals(
            "keep me",
            target.exerciseMetadataUserOverrideDao().findByStableKey(OMITTED_CANONICAL_KEY).single().value
        )
    }

    @Test
    fun sameSourceIdentityUsesExactSkipAppendWinsAndOverlapReplaceWins() = runBlocking {
        val backup = singleWorkoutBackup(DATE_B, reps = 5, sourceId = SAME_SOURCE_ID)

        val exactTarget = newDatabase()
        insertExercise(exactTarget, custom(SAME_SOURCE_EXERCISE_KEY, "Same source"))
        insertWorkout(exactTarget, SAME_SOURCE_EXERCISE_KEY, "Same source", DATE_B, 5, SAME_SOURCE_ID, 100L)
        val exactResult = restore(exactTarget, backup, WorkoutRestoreMode.APPEND_TO_CURRENT)
        assertEquals(1, exactTarget.workoutDao().allEntries().size)
        assertEquals(1, exactResult.skippedDuplicateCount)

        val appendTarget = newDatabase()
        insertExercise(appendTarget, custom(SAME_SOURCE_EXERCISE_KEY, "Same source"))
        insertWorkout(appendTarget, SAME_SOURCE_EXERCISE_KEY, "Same source", DATE_B, 9, SAME_SOURCE_ID, 100L)
        val appendPreparation = TrainingRepository(appendTarget, context).let { repository ->
            repository.prepareRecordsRestore(writeBackup(backup))
            val impact = repository.planRecordsRestore(
                WorkoutRestoreMode.APPEND_TO_CURRENT,
                ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES
            )
            repository.confirmRecordsRestore()
            impact
        }
        assertEquals(1, appendPreparation.sameSourceIdentityDifferentContentCount)
        assertEquals(9, appendTarget.workoutDao().allSets().single().reps)

        val replaceTarget = newDatabase()
        insertExercise(replaceTarget, custom(SAME_SOURCE_EXERCISE_KEY, "Same source"))
        insertWorkout(replaceTarget, SAME_SOURCE_EXERCISE_KEY, "Same source", DATE_B, 9, SAME_SOURCE_ID, 100L)
        restore(replaceTarget, backup, WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES)
        assertEquals(5, replaceTarget.workoutDao().allSets().single().reps)

        val crossDayTarget = newDatabase()
        insertExercise(crossDayTarget, custom(SAME_SOURCE_EXERCISE_KEY, "Same source"))
        insertWorkout(crossDayTarget, SAME_SOURCE_EXERCISE_KEY, "Same source", DATE_A, 9, SAME_SOURCE_ID, 100L)
        val crossDayRepository = TrainingRepository(crossDayTarget, context)
        val crossDayPreparation = crossDayRepository.prepareRecordsRestore(writeBackup(backup))
        assertFalse(crossDayPreparation.hasOverlappingWorkoutDates)
        val crossDayImpact = crossDayRepository.planRecordsRestore(
            WorkoutRestoreMode.APPEND_TO_CURRENT,
            ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES
        )
        assertEquals(1, crossDayImpact.outOfScopeSameSourceIdentityDivergenceCount)
        crossDayRepository.confirmRecordsRestore()
        assertEquals(listOf(DATE_A), crossDayTarget.workoutDao().allEntries().map { it.date })
    }

    @Test
    fun crossDaySameSourceInsideReplaceScopeMovesToBackupDate() = runBlocking {
        val source = newDatabase()
        val exercise = custom(SAME_SOURCE_EXERCISE_KEY, "Same source")
        insertExercise(source, exercise)
        insertWorkout(source, exercise.stableKey, exercise.name, DATE_A, 4, "SOURCE:overlap", 90L)
        insertWorkout(source, exercise.stableKey, exercise.name, DATE_B, 5, SAME_SOURCE_ID, 100L)
        val backup = export(source)

        val target = newDatabase()
        insertExercise(target, exercise)
        insertWorkout(target, exercise.stableKey, exercise.name, DATE_A, 9, SAME_SOURCE_ID, 80L)
        val repository = TrainingRepository(target, context)
        repository.prepareRecordsRestore(writeBackup(backup))
        val impact = repository.planRecordsRestore(
            WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES,
            ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES
        )

        assertEquals(1, impact.sameSourceIdentityDifferentContentCount)
        assertEquals(0, impact.outOfScopeSameSourceIdentityDivergenceCount)
        repository.confirmRecordsRestore()

        val entries = target.workoutDao().allEntries()
        assertEquals(setOf(DATE_A, DATE_B), entries.map(WorkoutEntry::date).toSet())
        assertEquals(2, entries.mapNotNull(WorkoutEntry::backupSourceId).distinct().size)
        val sameSourceEntryId = entries.single { it.backupSourceId == SAME_SOURCE_ID }.id
        assertEquals(5, target.workoutDao().allSets().single { it.entryId == sameSourceEntryId }.reps)
    }

    @Test
    fun malformedDuplicateImmutableSourceIdentityFailsBeforeMutation() = runBlocking {
        val exercise = custom(SAME_SOURCE_EXERCISE_KEY, "Same source")
        val backup = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = listOf(
                workoutFixture(41L, DATE_A, 4, SAME_SOURCE_ID, exercise),
                workoutFixture(42L, DATE_B, 5, SAME_SOURCE_ID, exercise)
            ),
            metrics = emptyList(),
            exercises = listOf(exercise),
            sourceDatabaseLineageId = "malformed-source"
        )
        val target = newDatabase()
        insertExercise(target, exercise)
        val before = snapshot(target)

        assertTrue(
            runCatching {
                TrainingRepository(target, context).prepareRecordsRestore(writeBackup(backup))
            }.isFailure
        )
        assertEquals(before, snapshot(target))
    }

    @Test
    fun legacyBackupWithoutProgramSnapshotProtectsCurrentProgramReference() = runBlocking {
        val sourceExercise = custom("legacy_source_exercise", "Legacy source")
        val backup = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            exercises = listOf(sourceExercise)
        )
        val target = newDatabase()
        val retained = custom(TARGET_ONLY_MISSING_KEY, "Program-only missing", isActive = false)
            .copy(isCustom = false, archivedAt = 9L)
        insertExercise(target, retained)
        val programId = target.programDao().insertProgram(
            TrainingProgram(stableKey = "program_reference_test", name = "Reference test", durationDays = 7)
        )
        target.programDao().insertProgramItem(
            TrainingProgramItem(
                programId = programId,
                weekNumber = 1,
                dayOfWeek = 1,
                orderIndex = 0,
                exerciseStableKey = retained.stableKey,
                exerciseName = retained.name,
                category = retained.category
            )
        )
        val repository = TrainingRepository(target, context)
        repository.prepareRecordsRestore(writeBackup(backup))
        val impact = repository.planRecordsRestore(
            WorkoutRestoreMode.APPEND_TO_CURRENT,
            ExerciseListRestoreMode.APPLY_BACKUP_ACTIVE_EXERCISE_LIST
        )

        assertEquals(1, impact.referencedExercisesRequiringInternalRetentionCount)
        repository.confirmRecordsRestore()
        assertNotNull(target.exerciseDao().findByStableKey(retained.stableKey))
        assertEquals(retained.stableKey, target.programDao().allProgramItems().single().exerciseStableKey)
    }

    @Test
    fun replacingOverlapRemapsDependentSmashAndPreservesIndependentSameDateRecord() = runBlocking {
        val source = newDatabase()
        insertExercise(source, custom(SHARED_CUSTOM_KEY, "Backup shared custom"))
        val sourceEntryId = insertWorkout(
            source,
            SHARED_CUSTOM_KEY,
            "Backup shared custom",
            DATE_B,
            5,
            "SOURCE:smash",
            100L
        )
        source.smashSpeedDao().upsert(
            SmashSpeedRecord(
                date = DATE_B,
                speedKmh = 301.0,
                attemptIndex = 1,
                note = "backup-parent",
                parentWorkoutEntryId = sourceEntryId
            )
        )
        val backup = export(source)

        val target = newDatabase()
        insertExercise(target, custom(SHARED_CUSTOM_KEY, "Target shared custom"))
        val oldEntryId = insertWorkout(
            target,
            SHARED_CUSTOM_KEY,
            "Target shared custom",
            DATE_B,
            9,
            "TARGET:old",
            200L
        )
        target.smashSpeedDao().upsert(
            SmashSpeedRecord(date = DATE_B, speedKmh = 250.0, attemptIndex = 1, note = "old-parent", parentWorkoutEntryId = oldEntryId)
        )
        target.smashSpeedDao().upsert(
            SmashSpeedRecord(date = DATE_B, speedKmh = 260.0, attemptIndex = 2, note = "independent")
        )

        restore(target, backup, WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES)

        val records = target.smashSpeedDao().all()
        assertEquals(setOf("backup-parent", "independent"), records.mapNotNull { it.note }.toSet())
        assertNull(records.single { it.note == "independent" }.parentWorkoutEntryId)
        val restoredParent = records.single { it.note == "backup-parent" }.parentWorkoutEntryId
        assertNotNull(restoredParent)
        assertNotEquals(oldEntryId, restoredParent)
        assertNotNull(target.workoutDao().findEntryById(restoredParent!!))
    }

    @Test
    fun legacyNumericParentUsesBackupGraphAndNeverTargetNumericIdentity() = runBlocking {
        val backupExercise = custom("legacy_numeric_backup", "Legacy numeric backup")
        val backupEntry = WorkoutEntry(
            id = 42L,
            date = DATE_B,
            exerciseStableKey = backupExercise.stableKey,
            exerciseName = backupExercise.name,
            category = backupExercise.category,
            createdAt = 100L
        )
        val bodyWithGeneratedSource = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = listOf(
                WorkoutEntryWithSets(
                    backupEntry,
                    listOf(WorkoutSet(entryId = 42L, setIndex = 1, reps = 5, confirmed = true))
                )
            ),
            metrics = emptyList(),
            exercises = listOf(backupExercise),
            smashSpeeds = listOf(
                SmashSpeedRecord(
                    id = 7L,
                    date = DATE_B,
                    speedKmh = 300.0,
                    attemptIndex = 1,
                    note = "legacy-parent",
                    parentWorkoutEntryId = 42L
                )
            ),
            sourceDatabaseLineageId = "legacy-test"
        )
        val legacyBody = bodyWithGeneratedSource.replace("legacy-test:workout_entry:42", "")

        val target = newDatabase()
        val targetExercise = custom("target_numeric_collision", "Target collision")
        insertExercise(target, targetExercise)
        val targetId = insertWorkout(
            target,
            targetExercise.stableKey,
            targetExercise.name,
            DATE_A,
            3,
            "TARGET:numeric",
            90L,
            explicitId = 42L
        )
        assertEquals(42L, targetId)

        TrainingRepository(target, context).importRecordsBackup(writeBackup(legacyBody))

        val imported = target.workoutDao().allEntries().single { it.exerciseStableKey == backupExercise.stableKey }
        val parent = target.smashSpeedDao().all().single { it.note == "legacy-parent" }.parentWorkoutEntryId
        assertNotEquals(42L, imported.id)
        assertEquals(imported.id, parent)
        assertEquals(2, target.workoutDao().allEntries().size)
    }

    @Test
    fun staleContentFingerprintAbortsBeforeRestoreMutation() = runBlocking {
        val backup = primaryBackup()
        val target = newDatabase()
        populatePrimaryTarget(target)
        val repository = TrainingRepository(target, context)
        repository.prepareRecordsRestore(writeBackup(backup))
        repository.planRecordsRestore(
            WorkoutRestoreMode.REPLACE_OVERLAPPING_DATES,
            ExerciseListRestoreMode.APPLY_BACKUP_ACTIVE_EXERCISE_LIST
        )
        val changedSet = target.workoutDao().allSets().first().copy(reps = 99)
        target.workoutDao().updateSet(changedSet)

        assertTrue(runCatching { repository.confirmRecordsRestore() }.isFailure)
        assertEquals(99, target.workoutDao().findSetById(changedSet.id)!!.reps)
        assertTrue(target.workoutDao().allEntries().none { it.date == DATE_D })
        assertEquals("Target shared custom", target.exerciseDao().findByStableKey(SHARED_CUSTOM_KEY)!!.name)
    }

    @Test
    fun restoreFailureRollsBackAllUserDomainMutations() = runBlocking {
        val source = newDatabase()
        val exercise = custom("rollback_source", "Rollback source")
        insertExercise(source, exercise)
        val sourceEntryId = insertWorkout(
            source,
            exercise.stableKey,
            exercise.name,
            DATE_D,
            5,
            "SOURCE:rollback",
            100L
        )
        source.smashSpeedDao().upsert(
            SmashSpeedRecord(
                date = DATE_D,
                speedKmh = 300.0,
                attemptIndex = 0,
                note = "invalid-during-restore",
                parentWorkoutEntryId = sourceEntryId
            )
        )
        val backup = export(source)

        val target = newDatabase()
        val retained = custom("rollback_target", "Rollback target")
        insertExercise(target, retained)
        insertWorkout(target, retained.stableKey, retained.name, DATE_A, 3, "TARGET:rollback", 90L)
        val before = snapshot(target)
        val repository = TrainingRepository(target, context)
        repository.prepareRecordsRestore(writeBackup(backup))
        repository.planRecordsRestore(
            WorkoutRestoreMode.APPEND_TO_CURRENT,
            ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES
        )

        assertTrue(runCatching { repository.confirmRecordsRestore() }.isFailure)
        val after = snapshot(target).copy(appMeta = before.appMeta)
        assertEquals(before, after)
    }

    @Test
    fun appMetaPolicyKeepsInfrastructureKeysTargetOwned() {
        assertEquals(
            BackupAppMetaAuthority.LOCAL_INFRASTRUCTURE_STATE,
            BackupAppMetaPolicy.authority(WorkoutSourceIdentityProvider.SOURCE_DATABASE_LINEAGE_ID)
        )
        assertFalse(BackupAppMetaPolicy.isSourceOverwriteAllowed(ExerciseMetadataReconciliationService.REQUIRED_KEY))
        assertFalse(BackupAppMetaPolicy.isSourceOverwriteAllowed(ExerciseMetadataReconciliationService.COMPLETED_KEY))
        assertFalse(BackupAppMetaPolicy.isSourceOverwriteAllowed("unknown_future_key"))
    }

    private suspend fun primaryBackup(): String {
        val source = newDatabase()
        source.exerciseDao().insertExercise(seed(CANONICAL_KEY).copy(isActive = false, archivedAt = 22L, needsReview = true))
        source.exerciseMetadataUserOverrideDao().upsert(
            override(CANONICAL_KEY, "exercise.category", "Backup category")
        )
        val sharedCustom = custom(SHARED_CUSTOM_KEY, "Backup shared custom", isActive = true)
        insertExercise(source, sharedCustom)
        insertWorkout(source, sharedCustom.stableKey, sharedCustom.name, DATE_B, 5, "SOURCE:B", 101L)
        insertWorkout(source, sharedCustom.stableKey, sharedCustom.name, DATE_C, 6, "SOURCE:C", 102L)
        insertWorkout(source, sharedCustom.stableKey, sharedCustom.name, DATE_D, 7, "SOURCE:D", 103L)
        source.appMetaDao().upsert(AppMeta(WorkoutSourceIdentityProvider.SOURCE_DATABASE_LINEAGE_ID, "SOURCE"))
        source.appMetaDao().upsert(AppMeta(ExerciseMetadataReconciliationService.REQUIRED_KEY, "SOURCE_REQUIRED"))
        source.appMetaDao().upsert(AppMeta(ExerciseMetadataReconciliationService.COMPLETED_KEY, "SOURCE_COMPLETED"))
        return export(source)
    }

    private suspend fun populatePrimaryTarget(target: TrainingDatabase) {
        target.exerciseDao().insertExercise(seed(CANONICAL_KEY).copy(isActive = true))
        target.exerciseMetadataUserOverrideDao().upsert(
            override(CANONICAL_KEY, "exercise.category", "Target category")
        )
        target.exerciseMetadataUserOverrideDao().upsert(
            override(CANONICAL_KEY, "exercise.description", "Target description")
        )
        target.exerciseDao().insertExercise(
            seed(OMITTED_CANONICAL_KEY).copy(isActive = false, archivedAt = 88L, needsReview = true)
        )
        target.exerciseMetadataUserOverrideDao().upsert(
            override(OMITTED_CANONICAL_KEY, "exercise.description", "Omitted canonical override")
        )
        insertExercise(target, custom(SHARED_CUSTOM_KEY, "Target shared custom", isActive = false).copy(archivedAt = 77L))
        val targetOnly = custom(TARGET_ONLY_CUSTOM_KEY, "Target only custom", isActive = true)
        insertExercise(target, targetOnly)
        val targetOnlyMissing = custom(TARGET_ONLY_MISSING_KEY, "Target only catalogue-missing", isActive = true)
            .copy(isCustom = false)
        insertExercise(target, targetOnlyMissing)
        target.exerciseMetadataUserOverrideDao().upsert(
            override(TARGET_ONLY_MISSING_KEY, "exercise.description", "Target-only missing override")
        )
        insertWorkout(target, targetOnly.stableKey, targetOnly.name, DATE_A, 3, "TARGET:A", 201L)
        insertWorkout(target, targetOnly.stableKey, targetOnly.name, DATE_B, 4, "TARGET:B", 202L)
        insertWorkout(target, targetOnly.stableKey, targetOnly.name, DATE_C, 5, "TARGET:C", 203L)
        target.appMetaDao().upsert(AppMeta(WorkoutSourceIdentityProvider.SOURCE_DATABASE_LINEAGE_ID, "TARGET"))
        target.appMetaDao().upsert(AppMeta(ExerciseMetadataReconciliationService.REQUIRED_KEY, "TARGET_REQUIRED"))
        target.appMetaDao().upsert(AppMeta(ExerciseMetadataReconciliationService.COMPLETED_KEY, "TARGET_COMPLETED"))
    }

    private suspend fun singleWorkoutBackup(date: String, reps: Int, sourceId: String): String {
        val source = newDatabase()
        val exercise = custom(SAME_SOURCE_EXERCISE_KEY, "Same source")
        insertExercise(source, exercise)
        insertWorkout(source, exercise.stableKey, exercise.name, date, reps, sourceId, 100L)
        return export(source)
    }

    private suspend fun restore(
        target: TrainingDatabase,
        backup: String,
        workoutMode: WorkoutRestoreMode
    ): RecordCsvTransferResult {
        val repository = TrainingRepository(target, context)
        repository.prepareRecordsRestore(writeBackup(backup))
        repository.planRecordsRestore(
            workoutMode,
            ExerciseListRestoreMode.PRESERVE_CURRENT_ACTIVE_EXERCISES
        )
        return repository.confirmRecordsRestore()
    }

    private suspend fun export(db: TrainingDatabase): String {
        val file = File.createTempFile("safe-restore-export", ".csv")
        file.deleteOnExit()
        TrainingRepository(db, context).exportRecordsBackup(Uri.fromFile(file))
        return file.readText(Charsets.UTF_8)
    }

    private fun writeBackup(text: String): Uri {
        val file = File.createTempFile("safe-restore-import", ".csv")
        file.writeText(text, Charsets.UTF_8)
        file.deleteOnExit()
        return Uri.fromFile(file)
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also(databases::add)

    private suspend fun insertExercise(db: TrainingDatabase, exercise: Exercise) {
        db.exerciseDao().insertExercise(exercise)
        db.runtimeExerciseMetadataDao().upsert(RuntimeExerciseMetadataDefaults.forExercise(exercise).toEntity())
    }

    private suspend fun insertWorkout(
        db: TrainingDatabase,
        stableKey: String,
        name: String,
        date: String,
        reps: Int,
        sourceId: String,
        createdAt: Long,
        explicitId: Long = 0L
    ): Long {
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                id = explicitId,
                date = date,
                exerciseStableKey = stableKey,
                exerciseName = name,
                category = "Strength",
                restSeconds = 120,
                createdAt = createdAt,
                displayOrder = 1,
                backupSourceId = sourceId
            )
        )
        db.workoutDao().insertSet(
            WorkoutSet(
                entryId = entryId,
                setIndex = 1,
                reps = reps,
                weightKg = 100.0,
                confirmed = true,
                manualWeight = true,
                rpe = 8.0
            )
        )
        return entryId
    }

    private fun workoutFixture(
        id: Long,
        date: String,
        reps: Int,
        sourceId: String,
        exercise: Exercise
    ): WorkoutEntryWithSets = WorkoutEntryWithSets(
        entry = WorkoutEntry(
            id = id,
            date = date,
            exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name,
            category = exercise.category,
            createdAt = id,
            backupSourceId = sourceId
        ),
        sets = listOf(
            WorkoutSet(
                id = id,
                entryId = id,
                setIndex = 1,
                reps = reps,
                weightKg = 100.0,
                confirmed = true,
                manualWeight = true,
                rpe = 8.0
            )
        )
    )

    private fun seed(stableKey: String): Exercise =
        SeedData.exactExerciseMetadataByStableKey(context).getValue(stableKey)

    private fun custom(stableKey: String, name: String, isActive: Boolean = true): Exercise = Exercise(
        stableKey = stableKey,
        name = name,
        category = "Strength",
        description = "$name definition",
        equipment = "DUMBBELL",
        movementPattern = "PRESS",
        isActive = isActive,
        isCustom = true
    )

    private fun override(stableKey: String, fieldKey: String, value: String): ExerciseMetadataUserOverrideEntity {
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

    private suspend fun snapshot(db: TrainingDatabase): RoomSnapshot = RoomSnapshot(
        exercises = db.exerciseDao().allExercises(),
        overrides = db.exerciseMetadataUserOverrideDao().all(),
        workouts = db.workoutDao().allEntriesWithSets(),
        smash = db.smashSpeedDao().all(),
        dailyMetrics = db.dailyMetricDao().allMetrics(),
        checkIns = db.dailyCheckInDao().all(),
        programs = db.programDao().allPrograms(),
        programItems = db.programDao().allProgramItems(),
        appMeta = db.appMetaDao().all()
    )

    private data class RoomSnapshot(
        val exercises: List<Exercise>,
        val overrides: List<ExerciseMetadataUserOverrideEntity>,
        val workouts: List<WorkoutEntryWithSets>,
        val smash: List<SmashSpeedRecord>,
        val dailyMetrics: List<DailyMetric>,
        val checkIns: List<DailyCheckIn>,
        val programs: List<TrainingProgram>,
        val programItems: List<TrainingProgramItem>,
        val appMeta: List<AppMeta>
    )

    private companion object {
        const val CANONICAL_KEY = "barbell_deadlift"
        const val OMITTED_CANONICAL_KEY = "barbell_back_squat"
        const val SHARED_CUSTOM_KEY = "user_shared_custom"
        const val TARGET_ONLY_CUSTOM_KEY = "user_target_only_custom"
        const val TARGET_ONLY_MISSING_KEY = "catalogue_missing_target_only"
        const val SAME_SOURCE_EXERCISE_KEY = "user_same_source"
        const val SAME_SOURCE_ID = "SOURCE:same"
        const val DATE_A = "2026-08-01"
        const val DATE_B = "2026-08-02"
        const val DATE_C = "2026-08-03"
        const val DATE_D = "2026-08-04"
    }
}

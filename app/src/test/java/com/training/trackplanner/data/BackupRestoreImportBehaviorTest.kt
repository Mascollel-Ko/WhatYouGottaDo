package com.training.trackplanner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.tissue.TissueRcvAssetRepository
import com.training.trackplanner.analysis.tissue.TissueRcvEventLedgerBuilder
import com.training.trackplanner.analysis.tissue.TissueWorkoutRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupRestoreImportBehaviorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun legacyRuntimeSnapshotDoesNotBecomeExplicitOverrideAuthority() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val seed = SeedData.exactExerciseMetadataByStableKey(context).values.first()
        val override = RuntimeExerciseMetadataDefaults.forIdentity(seed.stableKey, seed.name)
            .copy(programSlot = "RESTORED_SLOT", safeForSeedMutation = true)
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            exercises = listOf(seed),
            runtimeMetadata = listOf(override)
        )

        val result = repository.importRecordsBackup(writeBackup(csv))
        val restoredExercise = db.exerciseDao().findByStableKey(seed.stableKey)!!
        val restoredOverride = db.runtimeExerciseMetadataDao().findByStableKey(seed.stableKey)!!.toRuntimeMetadata()
        val resolved = repository.resolveRuntimeMetadata(restoredExercise)
        val canonical = CanonicalExerciseMetadataRepositoryProvider.get(context)
            .runtimeMetadataCatalog()
            .resolve(seed)!!

        assertEquals("restore", result.format)
        assertEquals(1, result.exerciseCount)
        assertEquals("RESTORED_SLOT", restoredOverride.programSlot)
        assertEquals(canonical.programSlot, resolved.programSlot)
        assertTrue(db.exerciseMetadataUserOverrideDao().findByStableKey(seed.stableKey).isEmpty())
        assertFalse(restoredOverride.safeForSeedMutation)
    }

    @Test
    fun legacyMaterializedDifferencesDoNotBecomeBuiltInOverrides() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val seed = SeedData.exactExerciseMetadataByStableKey(context).getValue("barbell_deadlift")
        val edited = seed.copy(primaryMuscles = "${seed.primaryMuscles}|QUADRICEPS")
        val override = RuntimeExerciseMetadataDefaults.forExercise(edited)
        val entry = WorkoutEntry(
            id = 71,
            date = "2026-07-20",
            exerciseStableKey = edited.stableKey,
            exerciseName = edited.name,
            category = edited.category
        )
        val set = WorkoutSet(
            id = 72,
            entryId = entry.id,
            setIndex = 1,
            reps = 3,
            weightKg = 180.0,
            confirmed = true,
            manualWeight = true
        )
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = listOf(WorkoutEntryWithSets(entry, listOf(set))),
            metrics = emptyList(),
            exercises = listOf(edited),
            runtimeMetadata = listOf(override)
        )
        db.exerciseDao().insertExercise(seed)

        val first = repository.importRecordsBackup(writeBackup(csv))
        assertEquals(1, first.entryCount)
        assertLegacyDifferenceIsNotOverride(db, seed)

        repository.seedIfNeeded()
        assertLegacyDifferenceIsNotOverride(db, seed)

        val duplicate = repository.importRecordsBackup(writeBackup(csv))
        assertEquals(0, duplicate.entryCount)
        assertEquals(1, duplicate.skippedDuplicateCount)
        assertLegacyDifferenceIsNotOverride(db, seed)
    }

    @Test
    fun restoreBackupPreservesCustomExerciseStableKeyAndOverride() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val exercise = Exercise(
            name = "Custom restore lift",
            category = "Strength",
            stableKey = "legacy_custom_restore_lift",
            primaryMuscles = "QUADRICEPS",
            isCustom = true
        )
        val metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise)
            .copy(programSlot = "CUSTOM_RESTORE_SLOT", safeForSeedMutation = false)
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            exercises = listOf(exercise),
            runtimeMetadata = listOf(metadata)
        )

        val result = repository.importRecordsBackup(writeBackup(csv))
        val restoredExercise = db.exerciseDao().findByStableKey("legacy_custom_restore_lift")!!
        val restoredMetadata = db.runtimeExerciseMetadataDao().findByStableKey("legacy_custom_restore_lift")!!.toRuntimeMetadata()

        assertEquals(1, result.exerciseCount)
        assertEquals("Custom restore lift", restoredExercise.name)
        assertTrue(restoredExercise.isCustom)
        assertEquals("CUSTOM_RESTORE_SLOT", restoredMetadata.programSlot)
    }

    @Test
    fun restoreNormalizesImportedBadmintonAndUsesExistingDurationRpeTissueMappings() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val canonicalKey = "ex_ae9ecdbc"
        val badminton = SeedData.exactExerciseMetadataByStableKey(context).getValue(canonicalKey)
        val entry = WorkoutEntry(
            id = 41,
            date = LocalDate.now().toString(),
            exerciseStableKey = badminton.stableKey,
            exerciseName = badminton.name,
            category = badminton.category,
            rpe = 8.0
        )
        val set = WorkoutSet(
            id = 42,
            entryId = entry.id,
            setIndex = 1,
            reps = 0,
            weightKg = 0.0,
            seconds = 45 * 60,
            confirmed = true,
            rpe = 8.0
        )
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = listOf(WorkoutEntryWithSets(entry, listOf(set))),
            metrics = emptyList(),
            exercises = listOf(badminton)
        ).replace(canonicalKey, "imported_배드민턴")

        val result = repository.importRecordsBackup(writeBackup(csv))
        val restoredExercise = db.exerciseDao().findByStableKey(canonicalKey)!!
        val restoredEntry = db.workoutDao().allEntriesWithSets().single()
        val catalog = TissueRcvAssetRepository.fromAssets(context).catalog
        val ledger = TissueRcvEventLedgerBuilder(catalog, ZoneId.systemDefault()).build(
            listOf(TissueWorkoutRecord.from(restoredEntry, restoredExercise, bodyWeightKg = null))
        )
        val expectedMappingKeys = catalog.authorityRows
            .filter { row -> row.exerciseStableKey == canonicalKey }
            .map { row -> row.loadUnitStableKey to row.loadProfileP }
            .toSet()
        val actualMappingKeys = ledger.events
            .map { event -> event.key.loadUnitStableKey to event.key.loadDimension }
            .toSet()

        assertEquals(1, result.entryCount)
        assertEquals(canonicalKey, restoredExercise.stableKey)
        assertNull(db.exerciseDao().findByStableKey("imported_배드민턴"))
        assertEquals(restoredExercise.stableKey, restoredEntry.entry.exerciseStableKey)
        assertTrue(restoredEntry.sets.all { restoredSet -> restoredSet.reps == 0 })
        assertTrue(ledger.events.isNotEmpty())
        assertEquals(expectedMappingKeys, actualMappingKeys)
        assertTrue(ledger.events.all { event -> event.exerciseStableKey == canonicalKey })
        assertTrue(ledger.events.all { event -> event.rawDose == 45.0 * 60.0 })
        assertTrue(ledger.events.all { event -> event.selectedEffort.value == 0.8 })
        assertFalse(ledger.diagnostics.any { diagnostic -> "missing reviewed mapping" in diagnostic })
    }

    @Test
    fun restoreMapsReviewedGenericKeysAcrossExerciseWorkoutAndProgramRows() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val mappings = listOf(
            Triple("ex_d2bb7946", "루마니안 데드리프트", "barbell_romanian_deadlift"),
            Triple(
                "ex_8380d7fe",
                "원암 하프 닐링 프레스",
                "half_kneeling_single_arm_dumbbell_press"
            ),
            Triple(
                "ex_8e1b313e",
                "하프 닐링 원암 프레스",
                "half_kneeling_single_arm_dumbbell_press"
            ),
            Triple(
                "ex_66e8c8c2",
                "하프 닐링 프레스",
                "half_kneeling_single_arm_dumbbell_press"
            )
        )
        val exercises = mappings.map { (sourceKey, sourceName, _) ->
            Exercise(name = sourceName, category = "근력운동", stableKey = sourceKey)
        }
        val entries = mappings.mapIndexed { index, (sourceKey, sourceName, _) ->
            val entry = WorkoutEntry(
                id = (index + 1).toLong(),
                date = "2026-07-${10 + index}",
                exerciseStableKey = sourceKey,
                exerciseName = sourceName,
                category = "근력운동"
            )
            WorkoutEntryWithSets(
                entry,
                listOf(
                    WorkoutSet(
                        id = (index + 101).toLong(),
                        entryId = entry.id,
                        setIndex = 1,
                        reps = 6,
                        weightKg = 40.0,
                        confirmed = true
                    )
                )
            )
        }
        val program = TrainingProgram(
            stableKey = "user_program_legacy_direct_mapping",
            name = "Legacy direct mapping",
            durationDays = 7
        )
        val programItems = mappings.mapIndexed { index, (sourceKey, sourceName, _) ->
            ProgramBackupItem(
                programStableKey = program.stableKey,
                weekNumber = 1,
                dayOfWeek = index + 1,
                orderIndex = 1,
                exerciseStableKey = sourceKey,
                exerciseName = sourceName,
                category = "근력운동",
                restSeconds = 90,
                prescription = "",
                setCount = 1,
                reps = 6,
                weightKg = 40.0,
                seconds = 0,
                trainingSlot = null,
                dayIntensity = null,
                weightSource = null
            )
        }
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = entries,
            metrics = emptyList(),
            exercises = exercises,
            programs = listOf(program),
            programItems = programItems,
            includeProgramSnapshot = true
        )

        val result = repository.importRecordsBackup(writeBackup(csv))
        val expectedKeys = mappings.map { mapping -> mapping.third }.sorted()
        val restoredEntryKeys = db.workoutDao().allEntriesWithSets()
            .map { row -> row.entry.exerciseStableKey }
            .sorted()
        val restoredProgramKeys = db.programDao().allProgramItems()
            .map(TrainingProgramItem::exerciseStableKey)
            .sorted()
        val restoredProgramNames = db.programDao().allProgramItems()
            .map(TrainingProgramItem::exerciseName)
            .toSet()
        val report = repository.latestDataTransferReport()!!

        assertEquals(2, result.exerciseCount)
        assertEquals(4, result.entryCount)
        assertEquals(4, result.setCount)
        assertEquals(1, result.programCount)
        assertEquals(4, result.programItemCount)
        assertEquals(expectedKeys, restoredEntryKeys)
        assertEquals(expectedKeys, restoredProgramKeys)
        assertEquals(
            setOf("루마니안 바벨 데드리프트", "하프 닐링 원암 덤벨 프레스"),
            restoredProgramNames
        )
        assertEquals(
            "루마니안 바벨 데드리프트",
            db.exerciseDao().findByStableKey("barbell_romanian_deadlift")?.name
        )
        assertEquals(
            "하프 닐링 원암 덤벨 프레스",
            db.exerciseDao().findByStableKey("half_kneeling_single_arm_dumbbell_press")?.name
        )
        assertTrue(mappings.all { (sourceKey, _, _) -> db.exerciseDao().findByStableKey(sourceKey) == null })
        assertFalse(
            (report.warnings + report.errors).any { diagnostic ->
                diagnostic.code == DataTransferDiagnosticCodes.AMBIGUOUS_LEGACY_EXERCISE_SPLIT
            }
        )
    }

    @Test
    fun restoreSkipsDeletedGenericOneArmRowExerciseDefinitionWithWarning() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val deletedKey = "ex_e3487166"
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            exercises = listOf(
                Exercise(name = "원암 로우", category = "근력운동", stableKey = deletedKey)
            )
        )

        val result = repository.importRecordsBackup(writeBackup(csv))
        val report = repository.latestDataTransferReport()!!

        assertEquals(0, result.exerciseCount)
        assertEquals(1, result.warningCount)
        assertNull(db.exerciseDao().findByStableKey(deletedKey))
        assertFalse(SeedData.exercises(context).any { exercise -> exercise.stableKey == deletedKey })
        assertTrue(
            report.warnings.any { diagnostic ->
                diagnostic.code == DataTransferDiagnosticCodes.LEGACY_DELETED_EXERCISE &&
                    diagnostic.sourceExerciseStableKey == deletedKey
            }
        )
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun restoreBackupUsesDailyMetricSleepAsCanonicalCheckInSleep() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = listOf(DailyMetric(date = "2026-07-01", sleepHours = 7.25, bodyWeightKg = 72.0)),
            checkIns = listOf(DailyCheckIn(date = "2026-07-01", sleepHours = 5.0, overallFatigue = 4))
        )

        val result = repository.importRecordsBackup(writeBackup(csv))
        val metric = db.dailyMetricDao().metric("2026-07-01")!!
        val checkIn = db.dailyCheckInDao().getForDate("2026-07-01")!!

        assertEquals(1, result.dailyMetricCount)
        assertEquals(1, result.dailyCheckInCount)
        assertEquals(7.25, metric.sleepHours ?: 0.0, 0.001)
        assertEquals(72.0, metric.bodyWeightKg ?: 0.0, 0.001)
        assertEquals(7.25, checkIn.sleepHours ?: 0.0, 0.001)
        assertEquals(4, checkIn.overallFatigue)
    }

    @Test
    fun restoreBackupPromotesCheckInSleepWhenDailyMetricMissing() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val csv = """
            schema_version,row_type,date,sleep_hours,overall_fatigue,lower_body_fatigue,joint_tendon_discomfort,focus_motivation,checkin_note
            2,check_in,2026-07-02,6.5,3,2,1,4,promoted sleep
        """.trimIndent()

        val result = repository.importRecordsBackup(writeBackup(csv))
        val metric = db.dailyMetricDao().metric("2026-07-02")!!
        val checkIn = db.dailyCheckInDao().getForDate("2026-07-02")!!

        assertEquals(1, result.dailyMetricCount)
        assertEquals(1, result.dailyCheckInCount)
        assertEquals(6.5, metric.sleepHours ?: 0.0, 0.001)
        assertEquals(6.5, checkIn.sleepHours ?: 0.0, 0.001)
        assertEquals(null, metric.bodyWeightKg)
    }

    @Test
    fun restoreBackupTreatsOutOfRangeSleepAsMissingWithoutDroppingOtherRecords() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val csv = """
            schema_version,row_type,date,entry_key,entry_order,exercise_name,category,confirmed,rest_seconds,set_index,set_confirmed,reps,weight_kg,seconds,sleep_hours,overall_fatigue,stable_key
            2,check_in,2026-07-18,,,,,,,,,,,,25,4,
            2,set,2026-07-18,e1,1,스쿼트,근력운동,1,120,1,1,5,100,0,25,,barbell_back_squat
        """.trimIndent()

        val result = repository.importRecordsBackup(writeBackup(csv))
        val checkIn = db.dailyCheckInDao().getForDate("2026-07-18")!!
        val entries = db.workoutDao().entriesWithSets("2026-07-18")

        assertEquals("restore", result.format)
        assertEquals(1, result.dailyCheckInCount)
        assertEquals(1, result.entryCount)
        assertEquals(1, result.setCount)
        assertEquals(null, checkIn.sleepHours)
        assertEquals(4, checkIn.overallFatigue)
        assertEquals(null, db.dailyMetricDao().metric("2026-07-18"))
        assertEquals("스쿼트", entries.single().entry.exerciseName)
    }

    @Test
    fun restoreBackupRoundTripsHabitualTrainingIntensity() = runBlocking {
        val db = newDatabase()
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            initialProfile = InitialUserProfile(bodyWeightKg = 72.5, habitualTrainingIntensity = "HARD")
        )

        repository(db).importRecordsBackup(writeBackup(csv))

        assertEquals("HARD", db.initialUserProfileDao().profile()?.habitualTrainingIntensity)
        assertEquals(72.5, db.initialUserProfileDao().profile()?.bodyWeightKg ?: 0.0, 0.001)
    }

    @Test
    fun restoreBackupKeepsMissingOrUnknownHabitualIntensityNeutral() = runBlocking {
        val db = newDatabase()
        val oldCsv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            initialProfile = InitialUserProfile(bodyWeightKg = 70.0)
        )
        repository(db).importRecordsBackup(writeBackup(oldCsv))
        assertEquals(null, db.initialUserProfileDao().profile()?.habitualTrainingIntensity)

        val newCsv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            initialProfile = InitialUserProfile(bodyWeightKg = 71.0, habitualTrainingIntensity = "HARD")
        )
        val invalidCsv = newCsv.lineSequence().joinToString("\n") { line ->
            if (",habitualTrainingIntensity," in line) line.replace("HARD", "EXTREME") else line
        }
        repository(db).importRecordsBackup(writeBackup(invalidCsv))

        assertEquals(null, db.initialUserProfileDao().profile()?.habitualTrainingIntensity)
        assertEquals(71.0, db.initialUserProfileDao().profile()?.bodyWeightKg ?: 0.0, 0.001)
    }

    @Test
    fun restoreBackupSkipsDuplicateSmashSpeedRows() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val duplicate = SmashSpeedRecord(
            date = "2026-07-03",
            speedKmh = 231.5,
            attemptIndex = 2,
            note = "same attempt"
        )
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            smashSpeeds = listOf(duplicate.copy(id = 1), duplicate.copy(id = 2))
        )

        val result = repository.importRecordsBackup(writeBackup(csv))
        val restored = db.smashSpeedDao().forDate("2026-07-03")

        assertEquals(1, result.smashSpeedCount)
        assertEquals(1, result.skippedDuplicateCount)
        assertEquals(1, restored.size)
        assertEquals(231.5, restored.single().speedKmh, 0.001)
    }

    @Test
    fun restoreBackupGroupsSetsPreservesStateAndSkipsDuplicateEntries() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val exercise = Exercise(name = "스쿼트", category = "Strength", stableKey = "barbell_back_squat")
        val entry = WorkoutEntry(
            id = 11,
            date = "2026-07-04",
            exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name,
            category = exercise.category,
            restSeconds = 120,
            notes = "restore entry"
        )
        val sets = listOf(
            WorkoutSet(entryId = entry.id, setIndex = 1, reps = 5, weightKg = 100.0, confirmed = true, rpe = 8.0),
            WorkoutSet(entryId = entry.id, setIndex = 2, reps = 3, weightKg = 105.0, confirmed = false, rpe = 7.0)
        )
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = listOf(WorkoutEntryWithSets(entry, sets)),
            metrics = emptyList(),
            exercises = listOf(exercise)
        )

        val firstResult = repository.importRecordsBackup(writeBackup(csv))
        val duplicateResult = repository.importRecordsBackup(writeBackup(csv))
        val restoredEntries = db.workoutDao().entriesWithSets("2026-07-04")
        val restoredSets = restoredEntries.single().sets.sortedBy { it.setIndex }

        assertEquals(1, firstResult.entryCount)
        assertEquals(2, firstResult.setCount)
        assertEquals(0, firstResult.skippedDuplicateCount)
        assertEquals(0, duplicateResult.entryCount)
        assertEquals(0, duplicateResult.setCount)
        assertEquals(1, duplicateResult.skippedDuplicateCount)
        assertEquals(1, restoredEntries.size)
        assertTrue(restoredSets[0].confirmed)
        assertFalse(restoredSets[1].confirmed)
        assertEquals(5, restoredSets[0].reps)
        assertEquals(105.0, restoredSets[1].weightKg, 0.001)
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun repository(db: TrainingDatabase): TrainingRepository =
        TrainingRepository(db, context)

    private suspend fun assertLegacyDifferenceIsNotOverride(db: TrainingDatabase, canonical: Exercise) {
        val effective = TrainingRepository(db, context).exerciseEditorData(canonical.stableKey)
        val restoredRecord = db.workoutDao().allEntriesWithSets().single()

        assertEquals(canonical.primaryMuscles, effective.exercise.primaryMuscles)
        assertTrue(db.exerciseMetadataUserOverrideDao().findByStableKey(canonical.stableKey).isEmpty())
        assertNotNull(db.runtimeExerciseMetadataDao().findByStableKey(canonical.stableKey))
        assertEquals(canonical.stableKey, restoredRecord.entry.exerciseStableKey)
        assertEquals(1, restoredRecord.sets.size)
    }

    private fun writeBackup(csv: String): Uri {
        val file = File.createTempFile("restore-import", ".csv")
        file.writeText(csv, Charsets.UTF_8)
        file.deleteOnExit()
        return Uri.fromFile(file)
    }
}

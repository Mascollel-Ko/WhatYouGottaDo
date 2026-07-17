package com.training.trackplanner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
    fun restoreBackupPreservesRuntimeMetadataOverridePrecedence() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val seed = SeedData.exactExerciseMetadataByStableKey(context).values.first()
        val override = RuntimeExerciseMetadataDefaults.forIdentity(seed.stableKey, seed.name)
            .copy(programSlot = "RESTORED_SLOT", safeForSeedMutation = true)
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            exercises = listOf(seed.copy(id = 1)),
            runtimeMetadata = listOf(override)
        )

        val result = repository.importRecordsBackup(writeBackup(csv))
        val restoredExercise = db.exerciseDao().findByStableKey(seed.stableKey)!!
        val restoredOverride = db.runtimeExerciseMetadataDao().findByStableKey(seed.stableKey)!!.toRuntimeMetadata()
        val resolved = repository.resolveRuntimeMetadata(restoredExercise)

        assertEquals("restore", result.format)
        assertEquals(1, result.exerciseCount)
        assertEquals("RESTORED_SLOT", restoredOverride.programSlot)
        assertEquals("RESTORED_SLOT", resolved.programSlot)
        assertFalse(restoredOverride.safeForSeedMutation)
    }

    @Test
    fun restoreBackupPreservesCustomExerciseStableKeyAndOverride() = runBlocking {
        val db = newDatabase()
        val repository = repository(db)
        val exercise = Exercise(
            id = 7,
            name = "Custom restore lift",
            category = "Strength",
            stableKey = "user_ex_restore_lift",
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
        val restoredExercise = db.exerciseDao().findByStableKey("user_ex_restore_lift")!!
        val restoredMetadata = db.runtimeExerciseMetadataDao().findByStableKey("user_ex_restore_lift")!!.toRuntimeMetadata()

        assertEquals(1, result.exerciseCount)
        assertEquals("Custom restore lift", restoredExercise.name)
        assertTrue(restoredExercise.isCustom)
        assertEquals("CUSTOM_RESTORE_SLOT", restoredMetadata.programSlot)
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
            schema_version,row_type,date,entry_key,entry_order,exercise_name,category,confirmed,rest_seconds,set_index,set_confirmed,reps,weight_kg,seconds,sleep_hours,overall_fatigue
            2,check_in,2026-07-18,,,,,,,,,,,,25,4
            2,set,2026-07-18,e1,1,스쿼트,근력운동,1,120,1,1,5,100,0,25,
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
        val exercise = Exercise(id = 3, name = "Restore squat", category = "Strength", stableKey = "restore_squat")
        val entry = WorkoutEntry(
            id = 11,
            date = "2026-07-04",
            exerciseId = exercise.id,
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

    private fun writeBackup(csv: String): Uri {
        val file = File.createTempFile("restore-import", ".csv")
        file.writeText(csv, Charsets.UTF_8)
        file.deleteOnExit()
        return Uri.fromFile(file)
    }
}

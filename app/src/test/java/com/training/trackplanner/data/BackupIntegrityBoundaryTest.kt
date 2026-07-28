package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupIntegrityBoundaryTest {
    @Test
    fun preflightCollectsAllBlockingReferenceErrors() {
        val exercise = Exercise(stableKey = "valid", name = "Valid", category = "근력운동")
        val program = TrainingProgram(id = 3, stableKey = "program", name = "Program", durationDays = 7)
        val result = BackupPreflightValidator.validate(
            exercises = listOf(exercise),
            workoutEntries = listOf(
                WorkoutEntry(id = 10, date = "2026-07-28", exerciseStableKey = "", exerciseName = "Blank", category = "근력운동"),
                WorkoutEntry(id = 11, date = "2026-07-28", exerciseStableKey = "missing", exerciseName = "Missing", category = "근력운동")
            ),
            workoutSets = listOf(WorkoutSet(id = 20, entryId = 999, setIndex = 1)),
            programs = listOf(program),
            programItems = listOf(
                TrainingProgramItem(
                    id = 30,
                    programId = program.id,
                    weekNumber = 1,
                    dayOfWeek = 1,
                    orderIndex = 0,
                    exerciseStableKey = "",
                    exerciseName = "Blank",
                    category = "근력운동"
                ),
                TrainingProgramItem(
                    id = 31,
                    programId = 999,
                    weekNumber = 1,
                    dayOfWeek = 1,
                    orderIndex = 1,
                    exerciseStableKey = "missing",
                    exerciseName = "Missing",
                    category = "근력운동"
                )
            ),
            runtimeMetadata = emptyList(),
            migrationIssues = emptyList()
        )

        val codes = result.errors.mapTo(mutableSetOf(), DataTransferDiagnostic::code)
        assertTrue(DataTransferDiagnosticCodes.WORKOUT_EXERCISE_STABLE_KEY_MISSING in codes)
        assertTrue(DataTransferDiagnosticCodes.WORKOUT_EXERCISE_STABLE_KEY_UNRESOLVED in codes)
        assertTrue(DataTransferDiagnosticCodes.ORPHAN_WORKOUT_SET in codes)
        assertTrue(DataTransferDiagnosticCodes.WORKOUT_ENTRY_WITHOUT_SET in codes)
        assertTrue(DataTransferDiagnosticCodes.PROGRAM_EXERCISE_STABLE_KEY_MISSING in codes)
        assertTrue(DataTransferDiagnosticCodes.ORPHAN_PROGRAM_ITEM in codes)
    }

    @Test
    fun manifestValidatesHashCountsAndCarriesNoExerciseIdColumn() {
        val body = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            exercises = listOf(Exercise(stableKey = "canonical", name = "Canonical", category = "근력운동"))
        )
        val counts = RecordCsvBackupRestore.backupEntityCounts(
            exerciseCount = 1,
            dailyMetricCount = 0,
            dailyCheckInCount = 0,
            smashSpeedCount = 0,
            profileCount = 0,
            entryCount = 0,
            setCount = 0,
            runtimeMetadataCount = 0,
            programCount = 0,
            programItemCount = 0,
            programTombstoneCount = 0
        )
        val backup = RecordCsvBackupRestore.wrapWithManifest(body, "0.5.0.6", 1L, counts)
        val parsed = RecordCsvBackupRestore.parse(backup) as RecordCsvImportData.Restore

        assertEquals(RecordCsvBackupRestore.CURRENT_BACKUP_FORMAT_VERSION, parsed.manifest?.formatVersion)
        assertTrue("exercise_id" !in body.lineSequence().first())

        val hashFailure = assertThrows(DataTransferFormatException::class.java) {
            RecordCsvBackupRestore.parse(backup.replace("Canonical", "Changed"))
        }
        assertEquals(DataTransferDiagnosticCodes.RESTORE_HASH_MISMATCH, hashFailure.diagnosticCode)

        val badCounts = counts + ("exercise" to 2)
        val countFailure = assertThrows(DataTransferFormatException::class.java) {
            RecordCsvBackupRestore.parse(
                RecordCsvBackupRestore.wrapWithManifest(body, "0.5.0.6", 1L, badCounts)
            )
        }
        assertEquals(DataTransferDiagnosticCodes.RESTORE_COUNT_MISMATCH, countFailure.diagnosticCode)
    }
}

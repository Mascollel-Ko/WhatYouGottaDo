package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreCanonicalizerSemanticAuthorityTest {
    private val canonicalizer = BackupRestoreCanonicalizer(
        LegacyExerciseImportMapper.fromMappings(emptyList())
    )

    @Test
    fun blankStableKeyBecomesDeterministicCustomIdentityWithoutCanonicalSemantics() {
        val name = "Canonical-looking squat"
        val canonical = Exercise(
            stableKey = "barbell_back_squat",
            name = name,
            category = "Strength",
            movementPattern = "SQUAT",
            planningEligibility = "PROGRAM_SELECTABLE"
        )
        val data = restoreData(name)

        val first = canonicalizer.canonicalize(data, mapOf(canonical.stableKey to canonical))
        val second = canonicalizer.canonicalize(data, mapOf(canonical.stableKey to canonical))

        assertTrue(first.errors.isEmpty())
        val imported = first.data.exerciseRows.single()
        assertTrue(UserExerciseStableKeyGenerator.isUserExerciseKey(imported.stableKey))
        assertNotEquals(canonical.stableKey, imported.stableKey)
        assertEquals(imported.stableKey, second.data.exerciseRows.single().stableKey)
        assertEquals(name, imported.name)
        assertTrue(imported.isCustom)
        assertTrue(imported.needsReview)
        assertEquals("", imported.movementPattern)
        assertEquals("", imported.primaryMuscles)
        assertEquals(imported.stableKey, first.data.setRows.single().stableKey)
        assertEquals(imported.stableKey, first.data.programSnapshot!!.items.single().exerciseStableKey)
        assertTrue(first.data.runtimeMetadataRows.isEmpty())
    }

    private fun restoreData(name: String): RecordCsvImportData.Restore {
        val exercise = RestoreExerciseRow(
            name = name,
            stableKey = "",
            category = "Strength",
            detail1 = "",
            detail2 = "",
            mode = "",
            description = "",
            defaultRestSeconds = 60,
            imageAssetName = "",
            primaryMuscles = "QUADRICEPS",
            secondaryMuscles = "GLUTE",
            equipment = "BARBELL",
            movementPattern = "SQUAT",
            movementCategory = "STRENGTH",
            forceType = "PUSH",
            bodyRegion = "LOWER",
            laterality = "BILATERAL",
            plane = "SAGITTAL",
            legacyTrainingRole = "MAIN_STRENGTH",
            trainingRoleCodes = setOf(TrainingRole.STRENGTH),
            programSlotCapabilityCodes = setOf(ProgramSlotCapability.MAIN_STRENGTH_SLOT),
            sportTransferDirect = "NONE",
            sportTransferSupportive = "NONE",
            loadProfile = "HEAVY",
            metadataConfidence = "HIGH",
            isActive = true,
            isCustom = false,
            needsReview = false
        )
        val set = RestoreSetRow(
            date = "2026-08-15",
            entryKey = "entry-1",
            entryOrder = 0,
            exerciseName = name,
            stableKey = "",
            category = "Strength",
            confirmed = true,
            restSeconds = 60,
            rpe = 8.0,
            maxReps = null,
            notes = "",
            setIndex = 0,
            setConfirmed = true,
            reps = 5,
            weightKg = 100.0,
            seconds = 0,
            sleepHours = null,
            bodyWeightKg = null
        )
        val runtime = RuntimeExerciseMetadataDefaults.forIdentity("", name).copy(
            movementFamily = "SQUAT",
            planningEligibility = "PROGRAM_SELECTABLE"
        )
        val programKey = "program-1"
        return RecordCsvImportData.Restore(
            exerciseRows = listOf(exercise),
            profileRows = emptyList(),
            dailyRows = emptyList(),
            setRows = listOf(set),
            runtimeMetadataRows = listOf(runtime),
            warningCount = 0,
            programSnapshot = RestoreProgramSnapshot(
                schemaVersion = 1,
                programs = listOf(TrainingProgram(stableKey = programKey, name = "Plan", durationDays = 7)),
                items = listOf(
                    ProgramBackupItem(
                        programStableKey = programKey,
                        weekNumber = 1,
                        dayOfWeek = 1,
                        orderIndex = 0,
                        exerciseStableKey = "",
                        exerciseName = name,
                        category = "Strength",
                        restSeconds = 60,
                        prescription = "",
                        setCount = 1,
                        reps = 5,
                        weightKg = 100.0,
                        seconds = 0,
                        trainingSlot = null,
                        dayIntensity = null,
                        weightSource = null
                    )
                ),
                sets = emptyList(),
                tombstones = emptyList()
            )
        )
    }
}

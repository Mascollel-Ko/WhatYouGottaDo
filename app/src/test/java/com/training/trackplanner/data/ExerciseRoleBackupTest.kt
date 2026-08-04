package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseRoleBackupTest {
    @Test
    fun newBackupRoundTripsNormalizedRoleRelationsWithoutLegacyColumn() {
        val exercise = Exercise(name = "Fixture", category = "Strength", stableKey = "role_backup_fixture")
        val csv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            exercises = listOf(exercise),
            trainingRoleRelations = listOf(
                ExerciseTrainingRoleRelation(exercise.stableKey, TrainingRole.STRENGTH.name, "test", "APPROVED")
            ),
            programSlotCapabilityRelations = listOf(
                ExerciseProgramSlotCapabilityRelation(
                    exercise.stableKey,
                    ProgramSlotCapability.MAIN_STRENGTH_SLOT.name,
                    "test",
                    "APPROVED"
                )
            )
        )
        val header = csv.lineSequence().first()
        val restored = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore
        val row = restored.exerciseRows.single()

        assertFalse(header.split(',').contains("training_role"))
        assertTrue(header.split(',').contains("training_role_codes"))
        assertEquals(setOf(TrainingRole.STRENGTH), row.trainingRoleCodes)
        assertEquals(setOf(ProgramSlotCapability.MAIN_STRENGTH_SLOT), row.programSlotCapabilityCodes)
        assertEquals("", row.legacyTrainingRole)
    }

    @Test
    fun oldBackupRoleUsesImportOnlyCompatibilityMapping() {
        val csv = """
            schema_version,row_type,exercise_name,stable_key,category,training_role
            8,exercise,Legacy fixture,legacy_role_fixture,Strength,MAIN_STRENGTH
        """.trimIndent()
        val restored = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore
        val row = restored.exerciseRows.single()
        val resolution = LegacyTrainingRoleImportMapper.resolve(row.legacyTrainingRole)

        assertEquals(setOf(TrainingRole.STRENGTH), resolution.trainingRoles)
        assertEquals(setOf(ProgramSlotCapability.MAIN_STRENGTH_SLOT), resolution.programSlotCapabilities)
        assertTrue(row.trainingRoleCodes.isEmpty())
        assertTrue(row.programSlotCapabilityCodes.isEmpty())
    }
}

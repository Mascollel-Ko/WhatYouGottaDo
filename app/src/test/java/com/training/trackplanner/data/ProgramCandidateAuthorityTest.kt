package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramCandidateAuthorityTest {
    @Test
    fun authorityIsAnExactTypedViewOfProgramRuleTables() {
        val declared = listOf(
            ProgramRuleTables.mainExercises.values.flatten(),
            ProgramRuleTables.pairedAccessories.values.flatten(),
            ProgramRuleTables.smallPartAccessories.values.flatten(),
            ProgramRuleTables.badmintonAccessories.values.flatten()
        ).flatten().mapTo(linkedSetOf(), ProgramExerciseSpec::stableKey)

        assertEquals(59, declared.size)
        assertEquals(declared, ProgramCandidateAuthority.allAllowedStableKeys)
        assertEquals(
            ProgramRuleTables.mainExercises.mapValues { (_, specs) -> specs.mapTo(linkedSetOf(), ProgramExerciseSpec::stableKey) },
            ProgramCandidateAuthority.mainStableKeysByArea
        )
        assertEquals(
            ProgramRuleTables.badmintonAccessories.mapValues { (_, specs) -> specs.mapTo(linkedSetOf(), ProgramExerciseSpec::stableKey) },
            ProgramCandidateAuthority.badmintonAccessoryStableKeysByCategory
        )
    }

    @Test
    fun metadataRolesAndSimilarNamesCannotAdmitUnknownStableKeys() {
        val unknown = listOf(
            exercise("unknown_pallof", "Pallof anti rotation core"),
            exercise("unknown_direct_core", "Direct core", movementPattern = "CORE|ANTI_ROTATION"),
            exercise("unknown_badminton", "Badminton supportive exercise", badmintonTransferStrength = "DIRECT"),
            exercise("barbell_back_squat_copy", "스쿼트")
        )
        val metadata = unknown.map { exercise ->
            ExerciseMetadataAdapter.fromFields(
                mapOf(
                    "stableKey" to exercise.stableKey,
                    "exerciseName" to exercise.name,
                    "currentActivityKind" to "TRAINING_EXERCISE",
                    "planningEligibility" to "PROGRAM_SELECTABLE",
                    "movementFamily" to "CORE",
                    "movementSubtype" to "ANTI_ROTATION",
                    "programSlot" to "STABILITY_SLOT",
                    "badmintonTransferLevel" to "DIRECT",
                    "badmintonTransferType" to "ANTI_ROTATION"
                )
            )
        }
        val roles = ExerciseRoleRelationCatalog.of(
            trainingRelations = unknown.map { exercise ->
                ExerciseTrainingRoleRelation(exercise.stableKey, TrainingRole.STABILITY.name, "TEST", "APPROVED")
            },
            capabilityRelations = unknown.map { exercise ->
                ExerciseProgramSlotCapabilityRelation(
                    exercise.stableKey,
                    ProgramSlotCapability.STABILITY_SLOT.name,
                    "TEST",
                    "APPROVED"
                )
            }
        )

        val inventory = ProgramCandidateInventory().collect(
            exercises = unknown,
            runtimeMetadataCatalog = RuntimeExerciseMetadataCatalog.of(metadata),
            availableEquipment = emptySet(),
            roleRelationCatalog = roles
        )

        assertEquals(unknown.size, inventory.allActive)
        assertEquals(0, inventory.programSelectable)
        assertTrue(inventory.candidates.isEmpty())
        assertTrue(inventory.reservoir.candidates.isEmpty())
    }

    @Test
    fun exactApprovedStableKeyRemainsEligibleAfterDisplayNameAndMetadataChange() {
        val approved = exercise(
            stableKey = "barbell_back_squat",
            name = "Localized renamed exercise",
            movementPattern = "UNRELATED_LABEL"
        )
        val metadata = ExerciseMetadataAdapter.fromFields(
            mapOf(
                "stableKey" to approved.stableKey,
                "exerciseName" to "다국어 이름",
                "currentActivityKind" to "TRAINING_EXERCISE",
                "planningEligibility" to "PROGRAM_SELECTABLE",
                "movementFamily" to "UNRELATED_METADATA"
            )
        )

        val inventory = ProgramCandidateInventory().collect(
            exercises = listOf(approved),
            runtimeMetadataCatalog = RuntimeExerciseMetadataCatalog.of(listOf(metadata)),
            availableEquipment = emptySet()
        )

        assertEquals(listOf("barbell_back_squat"), inventory.candidates.map { it.exercise.stableKey })
    }

    private fun exercise(
        stableKey: String,
        name: String,
        movementPattern: String = "",
        badmintonTransferStrength: String = ""
    ): Exercise = Exercise(
        stableKey = stableKey,
        name = name,
        category = "근력운동",
        movementPattern = movementPattern,
        badmintonTransferStrength = badmintonTransferStrength,
        activityKind = "TRAINING_EXERCISE",
        planningEligibility = "PROGRAM_SELECTABLE"
    )
}

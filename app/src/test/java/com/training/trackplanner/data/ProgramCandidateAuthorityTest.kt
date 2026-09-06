package com.training.trackplanner.data

import com.training.trackplanner.data.program.legacy.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramCandidateAuthorityTest {
    @Test
    fun authorityIsAnExactTypedViewOfProgramRuleTables() {
        val declared = listOf(
            LegacyAutoRuleTables.mainExercises.values.flatten(),
            LegacyAutoRuleTables.pairedAccessories.values.flatten(),
            LegacyAutoRuleTables.smallPartAccessories.values.flatten(),
            LegacyAutoRuleTables.badmintonAccessories.values.flatten()
        ).flatten().mapTo(linkedSetOf(), LegacyAutoExerciseSpec::stableKey)

        assertEquals(58, declared.size)
        assertEquals(declared, LegacyAutoCandidateAuthority.allAllowedStableKeys)
        assertEquals(
            LegacyAutoRuleTables.mainExercises.mapValues { (_, specs) -> specs.mapTo(linkedSetOf(), LegacyAutoExerciseSpec::stableKey) },
            LegacyAutoCandidateAuthority.mainStableKeysByArea
        )
        assertEquals(
            LegacyAutoRuleTables.badmintonAccessories.mapValues { (_, specs) -> specs.mapTo(linkedSetOf(), LegacyAutoExerciseSpec::stableKey) },
            LegacyAutoCandidateAuthority.badmintonAccessoryStableKeysByCategory
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
        assertTrue(unknown.none { exercise -> LegacyAutoCandidateAuthority.allows(exercise.stableKey) })
    }

    @Test
    fun exactApprovedStableKeyRemainsEligibleAfterDisplayNameAndMetadataChange() {
        val approved = exercise(
            stableKey = "barbell_back_squat",
            name = "Localized renamed exercise",
            movementPattern = "UNRELATED_LABEL"
        )
        assertTrue(LegacyAutoCandidateAuthority.allows(approved.stableKey))
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

package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramFallbackRolePolicyTest {
    @Test
    fun fallbackAnchorRequiresPrimaryStructuralAnchorCapability() {
        val calf = candidate(ProgramSlotId.CALF_ANKLE_CAPACITY)
        val forearm = candidate(ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT)
        val prehab = candidate(
            ProgramSlotId.RECOVERY_PREHAB_LIGHT,
            movementFamily = "RECOVERY_PREHAB_LIGHT"
        )
        val squat = candidate(ProgramSlotId.LOWER_SQUAT_PATTERN)
        val singleLeg = candidate(ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL)

        assertFalse(calf.allowedForRole(ProgramTrainingSlot.LOWER_STRENGTH, ProgramExerciseRole.ANCHOR))
        assertFalse(forearm.allowedForRole(ProgramTrainingSlot.UPPER_STRENGTH, ProgramExerciseRole.ANCHOR))
        assertFalse(prehab.allowedForRole(ProgramTrainingSlot.RECOVERY_WEAKPOINT, ProgramExerciseRole.ANCHOR))
        assertTrue(squat.allowedForRole(ProgramTrainingSlot.LOWER_STRENGTH, ProgramExerciseRole.ANCHOR))
        assertTrue(singleLeg.allowedForRole(ProgramTrainingSlot.LOWER_TRANSFER_FULL, ProgramExerciseRole.ANCHOR))
    }

    @Test
    fun corePrehabAndSupportRejectReactiveCandidates() {
        val controlledCore = candidate(
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY,
            movementFamily = "TRUNK_ANTI_ROTATION_STABILITY",
            movementSubtype = "PALLOF_PRESS"
        )
        val explosiveCore = candidate(
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY,
            movementFamily = "TRUNK_ANTI_ROTATION_STABILITY",
            movementSubtype = "MED_BALL_ROTATIONAL_SLAM"
        )
        val controlledPrehab = candidate(
            ProgramSlotId.RECOVERY_PREHAB_LIGHT,
            movementFamily = "RECOVERY_PREHAB_LIGHT",
            movementSubtype = "WALL_SLIDE"
        )
        val explosivePrehab = candidate(
            ProgramSlotId.RECOVERY_PREHAB_LIGHT,
            movementFamily = "RECOVERY_PREHAB_LIGHT",
            movementSubtype = "POWER_SLAM"
        )
        val controlledRotation = candidate(
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
            movementFamily = "ROTATIONAL_KINETIC_CHAIN",
            movementSubtype = "CABLE_CHOP"
        )
        val explosiveRotation = candidate(
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
            movementFamily = "ROTATIONAL_KINETIC_CHAIN",
            movementSubtype = "MED_BALL_ROTATIONAL_THROW"
        )

        assertTrue(controlledCore.allowedForRole(ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT, ProgramExerciseRole.CORE))
        assertFalse(explosiveCore.allowedForRole(ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT, ProgramExerciseRole.CORE))
        assertTrue(controlledPrehab.allowedForRole(ProgramTrainingSlot.RECOVERY_WEAKPOINT, ProgramExerciseRole.PREHAB))
        assertFalse(explosivePrehab.allowedForRole(ProgramTrainingSlot.RECOVERY_WEAKPOINT, ProgramExerciseRole.PREHAB))
        assertTrue(controlledRotation.allowedForRole(ProgramTrainingSlot.WEAKPOINT_ACCESSORY, ProgramExerciseRole.SUPPORT))
        assertFalse(explosiveRotation.allowedForRole(ProgramTrainingSlot.WEAKPOINT_ACCESSORY, ProgramExerciseRole.SUPPORT))
    }

    @Test
    fun requestedSlotUsesStructuredCapabilitiesButNotNameOnlyWeakFallback() {
        val strong = candidate(ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT)
        val structuredWeak = candidate(
            primary = null,
            weak = ProgramSlotId.ACCESSORY_ROTATION,
            source = SlotCapabilitySource.RUNTIME_METADATA
        )
        val nameOnlyWeak = candidate(
            primary = null,
            weak = ProgramSlotId.ACCESSORY_ROTATION,
            source = SlotCapabilitySource.NAME_FALLBACK
        )

        assertEquals(
            ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT,
            strong.resolvedSlotForRole(ProgramExerciseRole.ACCESSORY)
        )
        assertEquals(
            ProgramSlotId.ACCESSORY_ROTATION,
            structuredWeak.resolvedSlotForRole(ProgramExerciseRole.ACCESSORY)
        )
        assertNull(nameOnlyWeak.resolvedSlotForRole(ProgramExerciseRole.ACCESSORY))
    }

    private fun candidate(
        primary: ProgramSlotId?,
        movementFamily: String = primary?.name.orEmpty(),
        movementSubtype: String = primary?.name.orEmpty(),
        weak: ProgramSlotId? = null,
        source: SlotCapabilitySource = SlotCapabilitySource.RUNTIME_METADATA
    ): ProgramCandidate {
        val exercise = Exercise(
            id = nextId++,
            name = movementSubtype.ifBlank { "test exercise" },
            category = "test",
            stableKey = "fallback-role-$nextId"
        )
        val metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            movementFamily = movementFamily,
            movementSubtype = movementSubtype,
            programSlot = movementFamily,
            planningEligibility = "PROGRAM_SELECTABLE",
            progressMetricType = "LOAD_REPS",
            strengthProgressionGroup = movementFamily,
            primaryStressProfile = "GENERAL_STRENGTH_STRESS",
            stressMagnitudeHint = "MODERATE",
            recoveryDurationClass = "MEDIUM"
        )
        return ProgramCandidate(
            exercise = exercise,
            metadata = metadata,
            canonical = true,
            slotCapabilities = SlotCapabilityProfile(
                primary = primary?.let(::setOf).orEmpty(),
                secondary = emptySet(),
                weakMatches = weak?.let(::setOf).orEmpty(),
                source = source,
                confidence = if (source == SlotCapabilitySource.NAME_FALLBACK) {
                    SlotCapabilityConfidence.LOW
                } else {
                    SlotCapabilityConfidence.HIGH
                }
            )
        )
    }

    private companion object {
        var nextId = 9000L
    }
}

package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.features.AnalysisExerciseFeatures
import com.training.trackplanner.analysis.features.ExerciseAnalysisMapper
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalBadmintonTransferAuthorityTest {
    @Test
    fun canonicalNoneBlocksGenericPlaneLateralityMuscleFatigueAndBraceInference() {
        val features = features(
            Exercise(
                stableKey = "generic_fixture",
                name = "Generic fixture",
                category = "training",
                movementPattern = "ANTI_ROTATION",
                primaryMuscles = "SHOULDER|FOREARM|BACK",
                forceType = "BRACE",
                plane = "FRONTAL",
                laterality = "UNILATERAL",
                fatigueCategories = "DECELERATION|ANTI_ROTATION|GRIP_FOREARM",
                systemicLoadWeight = 0.5,
                neuralHeavyWeight = 0.5
            ),
            canonicalAuthority = true
        )

        assertTrue(BadmintonTransferMetadataMapper.transferAxes(features).isEmpty())
        assertTrue(BadmintonTransferMetadataMapper.legacyTransferAxesForAudit(features).isNotEmpty())
    }

    @Test
    fun explicitBadmintonRelationsStillProduceTheirIntendedAxes() {
        assertAxis(
            BadmintonTransferAxis.DECELERATION_LANDING,
            types = "CHANGE_OF_DIRECTION_DIRECT",
            qualities = "DECELERATION"
        )
        assertAxis(
            BadmintonTransferAxis.LATERAL_MOVEMENT,
            types = "FOOTWORK_DIRECT",
            targets = "FIRST_STEP"
        )
        assertAxis(
            BadmintonTransferAxis.UNILATERAL_STABILITY,
            targets = "FRONT_COURT_LUNGE"
        )
        assertAxis(
            BadmintonTransferAxis.ROTATION_CONTROL,
            types = "ANTI_ROTATION_STABILITY_SUPPORTIVE",
            targets = "ANTI_ROTATION_STABILITY"
        )
        assertAxis(
            BadmintonTransferAxis.RACKET_SUPPORT,
            targets = "SMASH"
        )
    }

    @Test
    fun axialBracingDoesNotCreateBadmintonAntiRotation() {
        val features = features(
            Exercise(
                stableKey = "heavy_squat_fixture",
                name = "Heavy squat fixture",
                category = "strength",
                movementPattern = "SQUAT",
                forceType = "SQUAT",
                axialLoadLevel = "HIGH",
                systemicLoadWeight = 0.75,
                neuralHeavyWeight = 0.75,
                recoveryDecayProfile = "VERY_LONG"
            )
        )

        assertFalse(BadmintonTransferAxis.ROTATION_CONTROL in BadmintonTransferMetadataMapper.transferAxes(features))
    }

    @Test
    fun canonicalSwitchDoesNotChangeFatigueCost() {
        val canonical = features(
            Exercise(
                stableKey = "fatigue_fixture",
                name = "Fatigue fixture",
                category = "strength",
                axialLoadLevel = "MODERATE",
                systemicLoadWeight = 0.7,
                neuralHeavyWeight = 0.5,
                recoveryDecayProfile = "LONG"
            ),
            types = "GENERAL_STRENGTH_SUPPORTIVE"
        )
        val legacy = canonical.copy(
            canonicalBadmintonTransferLevel = "",
            canonicalBadmintonTransferTypes = emptySet(),
            canonicalBadmintonSkillTargets = emptySet(),
            badmintonPhysicalQualities = emptySet()
        )

        assertEquals(
            BadmintonTransferMetadataMapper.fatigueCost(legacy),
            BadmintonTransferMetadataMapper.fatigueCost(canonical)
        )
    }

    @Test
    fun antiRotationObjectiveRequiresExplicitBadmintonAuthorityNotExerciseName() {
        val nameOnly = features(
            Exercise(
                stableKey = "generic_pallof_fixture",
                name = "Generic Pallof press",
                category = "training",
                movementPattern = "ANTI_ROTATION"
            )
        )
        val explicit = features(
            Exercise(
                stableKey = "landmine_anti_rotation",
                name = "Explicit anti-rotation fixture",
                category = "training"
            ),
            types = "ANTI_ROTATION_STABILITY_SUPPORTIVE",
            targets = "ANTI_ROTATION_STABILITY",
            qualities = "ANTI_ROTATION_STABILITY"
        )

        assertFalse("ANTI_ROTATION" in BadmintonTransferMetadataMapper.objectiveKeys(nameOnly))
        assertTrue("ANTI_ROTATION" in BadmintonTransferMetadataMapper.objectiveKeys(explicit))
    }

    private fun assertAxis(
        axis: BadmintonTransferAxis,
        types: String = "NONE",
        targets: String = "NONE",
        qualities: String = "NONE"
    ) {
        val exercise = Exercise(
            stableKey = "explicit_${axis.name.lowercase()}",
            name = "Explicit ${axis.name}",
            category = "training"
        )
        assertTrue(axis in BadmintonTransferMetadataMapper.transferAxes(features(exercise, types, targets, qualities)))
    }

    private fun features(
        exercise: Exercise,
        types: String = "NONE",
        targets: String = "NONE",
        qualities: String = "NONE",
        canonicalAuthority: Boolean = false
    ): AnalysisExerciseFeatures {
        val metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            badmintonTransferLevel = if (types == "NONE" && targets == "NONE" && qualities == "NONE") "NONE" else "SUPPORTIVE",
            badmintonTransferType = MetadataTokenField.parse(types),
            badmintonSkillTargets = MetadataTokenField.parse(targets),
            badmintonPhysicalQualities = MetadataTokenField.parse(qualities)
        )
        return ExerciseAnalysisMapper.fromExercise(exercise, metadata, canonicalAuthority)
    }
}

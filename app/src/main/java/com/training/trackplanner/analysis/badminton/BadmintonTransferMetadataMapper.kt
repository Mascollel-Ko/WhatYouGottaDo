package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.features.AnalysisExerciseFeatures
import com.training.trackplanner.analysis.trends.BadmintonTrainingMethodLabels

internal object BadmintonTransferMetadataMapper {
    fun transferType(features: AnalysisExerciseFeatures): BadmintonTransferType =
        when (features.canonicalBadmintonTransferLevel.ifBlank { features.badmintonTransferStrength }) {
            "DIRECT" -> BadmintonTransferType.DIRECT
            "SUPPORTIVE" -> BadmintonTransferType.SUPPORTIVE
            "GENERAL" -> BadmintonTransferType.GENERAL_STRENGTH
            "NONE" -> BadmintonTransferType.NONE
            else -> {
                val hasLowTransferMetadata =
                    "BADMINTON_TRANSFER" in features.analysisEligibility ||
                        features.badmintonTransferRoles.isNotEmpty() ||
                        features.badmintonSkillTargets.isNotEmpty()
                if (hasLowTransferMetadata) BadmintonTransferType.LOW else BadmintonTransferType.NONE
            }
        }

    fun transferAxes(features: AnalysisExerciseFeatures): Set<BadmintonTransferAxis> =
        if (features.hasCanonicalBadmintonAuthority()) {
            canonicalTransferAxes(features)
        } else {
            legacyTransferAxesForAudit(features)
        }

    internal fun legacyTransferAxesForAudit(features: AnalysisExerciseFeatures): Set<BadmintonTransferAxis> {
        val axes = linkedSetOf<BadmintonTransferAxis>()
        val skillTargets = features.badmintonSkillTargets + features.canonicalBadmintonSkillTargets
        val transferTokens = features.badmintonTransferRoles +
            features.canonicalBadmintonTransferTypes +
            features.badmintonPhysicalQualities

        if (
            "DECELERATION" in features.fatigueCategories ||
            "ELASTIC_SSC" in features.fatigueCategories ||
            "DECELERATION" in transferTokens ||
            "JUMP_LANDING" in transferTokens ||
            "DECELERATION_CONTROL" in skillTargets ||
            "JUMP_LANDING_CONTROL" in skillTargets ||
            "DECELERATION" in features.courtMovementTypes ||
            "JUMP_LANDING" in features.courtMovementTypes ||
            features.forceType in setOf("LAND", "DECELERATE")
        ) {
            axes += BadmintonTransferAxis.DECELERATION_LANDING
        }

        if (
            features.laterality in setOf("UNILATERAL", "ALTERNATING", "ASYMMETRIC") ||
            "UNILATERAL_LOWER" in features.balanceContributionTags ||
            "UNILATERAL_UPPER" in features.balanceContributionTags ||
            "HIP_STABILITY" in features.balanceContributionTags ||
            "KNEE_CONTROL" in features.balanceContributionTags ||
            "LUNGE_REACH" in transferTokens ||
            "LUNGE_REACH" in skillTargets
        ) {
            axes += BadmintonTransferAxis.UNILATERAL_STABILITY
        }

        if (
            "FOOTWORK" in transferTokens ||
            "ACCELERATION" in transferTokens ||
            features.courtMovementTypes.any { type ->
                type in setOf(
                    "SPLIT_STEP",
                    "FIRST_STEP",
                    "LATERAL_MOVE",
                    "CROSSOVER",
                    "FRONT_LUNGE",
                    "REAR_COURT",
                    "MULTI_DIRECTION",
                    "REACTION_RANDOM",
                    "RECOVERY_STEP"
                )
            } ||
            features.movementPattern in setOf("FOOTWORK", "LOCOMOTION") ||
            features.plane == "FRONTAL"
        ) {
            axes += BadmintonTransferAxis.LATERAL_MOVEMENT
        }

        if (
            features.movementPattern in setOf("ROTATION", "ANTI_ROTATION") ||
            "ROTATION_POWER" in features.fatigueCategories ||
            "ANTI_ROTATION" in features.fatigueCategories ||
            "ROTATION_POWER" in transferTokens ||
            "ANTI_ROTATION_STABILITY" in transferTokens ||
            "ROTATION_SEQUENCING" in skillTargets ||
            "ANTI_ROTATION_STABILITY" in skillTargets ||
            "ROTATION" in features.balanceContributionTags ||
            "ANTI_ROTATION" in features.balanceContributionTags ||
            features.forceType in setOf("ROTATE", "BRACE")
        ) {
            axes += BadmintonTransferAxis.ROTATION_CONTROL
        }

        if (
            "OVERHEAD_POWER" in transferTokens ||
            "SHOULDER_CARE" in transferTokens ||
            "GRIP_FOREARM" in transferTokens ||
            "OVERHEAD_POWER" in skillTargets ||
            "GRIP_ENDURANCE" in skillTargets ||
            "SHOULDER_DURABILITY" in skillTargets ||
            "OVERHEAD_REPETITION" in features.fatigueCategories ||
            "GRIP_FOREARM" in features.fatigueCategories ||
            features.primaryMuscles.any { muscle ->
                muscle in setOf("SHOULDERS", "ROTATOR_CUFF", "FOREARM_GRIP", "LATS_UPPER_BACK")
            } ||
            features.secondaryMuscles.any { muscle ->
                muscle in setOf("SHOULDERS", "ROTATOR_CUFF", "FOREARM_GRIP", "LATS_UPPER_BACK")
            }
        ) {
            axes += BadmintonTransferAxis.RACKET_SUPPORT
        }

        if (
            features.supportsConditioningOrSkillAnalysis ||
            "CONDITIONING" in transferTokens ||
            "CONDITIONING" in skillTargets ||
            "FOOTWORK_SPEED" in skillTargets
        ) {
            axes += BadmintonTransferAxis.AEROBIC_FOOTWORK
        }

        if (
            features.supportsLowFatigueControlAnalysis ||
            "LOW_FATIGUE_REHAB" in features.fatigueCategories ||
            "RECOVERY_ONLY" in features.analysisEligibility ||
            (
                features.systemicLoadWeight <= 0.25 &&
                    features.neuralHeavyWeight <= 0.25 &&
                    features.neuralSpeedWeight <= 0.25 &&
                    (features.antiRotationWeight > 0.0 || features.localLoadWeight > 0.0)
                )
        ) {
            axes += BadmintonTransferAxis.LOW_FATIGUE_CONTROL
        }

        return axes
    }

    private fun canonicalTransferAxes(features: AnalysisExerciseFeatures): Set<BadmintonTransferAxis> {
        val axes = linkedSetOf<BadmintonTransferAxis>()
        val types = features.canonicalBadmintonTransferTypes
        val targets = features.canonicalBadmintonSkillTargets
        val qualities = features.badmintonPhysicalQualities

        if (
            types.any { it in DECELERATION_TYPES } ||
            targets.any { it in DECELERATION_TARGETS } ||
            qualities.any { it in DECELERATION_QUALITIES }
        ) {
            axes += BadmintonTransferAxis.DECELERATION_LANDING
        }
        if (
            types.any { it in UNILATERAL_TYPES } ||
            targets.any { it in UNILATERAL_TARGETS } ||
            qualities.any { it in UNILATERAL_QUALITIES }
        ) {
            axes += BadmintonTransferAxis.UNILATERAL_STABILITY
        }
        if (
            types.any { it in LATERAL_TYPES } ||
            targets.any { it in LATERAL_TARGETS } ||
            qualities.any { it in LATERAL_QUALITIES }
        ) {
            axes += BadmintonTransferAxis.LATERAL_MOVEMENT
        }
        if (
            types.any { it in ROTATION_TYPES } ||
            targets.any { it in ROTATION_TARGETS } ||
            qualities.any { it in ROTATION_QUALITIES }
        ) {
            axes += BadmintonTransferAxis.ROTATION_CONTROL
        }
        if (
            types.any { it in RACKET_TYPES } ||
            targets.any { it in RACKET_TARGETS } ||
            qualities.any { it in RACKET_QUALITIES }
        ) {
            axes += BadmintonTransferAxis.RACKET_SUPPORT
        }
        if (
            types.any { it in CONDITIONING_TYPES } ||
            targets.any { it in CONDITIONING_TARGETS } ||
            qualities.any { it in CONDITIONING_QUALITIES }
        ) {
            axes += BadmintonTransferAxis.AEROBIC_FOOTWORK
        }
        if (types.any { it in LOW_FATIGUE_SUPPORT_TYPES }) {
            axes += BadmintonTransferAxis.LOW_FATIGUE_CONTROL
        }
        return axes
    }

    private fun AnalysisExerciseFeatures.hasCanonicalBadmintonAuthority(): Boolean =
        canonicalBadmintonAuthority ||
            canonicalBadmintonTransferTypes.any { it in CANONICAL_TRANSFER_TYPES } ||
            canonicalBadmintonSkillTargets.any { it in CANONICAL_SKILL_TARGETS } ||
            badmintonPhysicalQualities.any { it in CANONICAL_PHYSICAL_QUALITIES }

    fun objectiveKeys(features: AnalysisExerciseFeatures): Set<String> =
        if (features.hasCanonicalBadmintonAuthority()) {
            BadmintonTrainingMethodLabels.keysFrom(
                courtMovementTypes = features.badmintonPhysicalQualities,
                transferRoles = features.canonicalBadmintonTransferTypes,
                skillTargets = features.canonicalBadmintonSkillTargets,
                includeAntiRotation = features.hasExplicitCanonicalAntiRotationObjective()
            )
        } else {
            BadmintonTrainingMethodLabels.keysFrom(
                courtMovementTypes = features.courtMovementTypes,
                transferRoles = features.badmintonTransferRoles,
                skillTargets = features.badmintonSkillTargets + features.canonicalBadmintonSkillTargets,
                includeAntiRotation = features.exerciseStableKey in LEGACY_EXPLICIT_ANTI_ROTATION_OBJECTIVE_KEYS
            )
        }

    private fun AnalysisExerciseFeatures.hasExplicitCanonicalAntiRotationObjective(): Boolean =
        exerciseStableKey in LEGACY_EXPLICIT_ANTI_ROTATION_OBJECTIVE_KEYS &&
            (
                canonicalBadmintonTransferTypes.any { it in ROTATION_TYPES } ||
                    canonicalBadmintonSkillTargets.any { it == "ANTI_ROTATION_STABILITY" } ||
                    badmintonPhysicalQualities.any { it == "ANTI_ROTATION_STABILITY" }
                )

    fun fatigueCost(features: AnalysisExerciseFeatures): BadmintonTransferFatigueCost {
        val loadSignal = listOf(
            features.systemicLoadWeight,
            features.neuralHeavyWeight,
            features.neuralSpeedWeight,
            features.decelerationWeight,
            features.elasticSscWeight
        ).maxOrNull() ?: 0.0

        return when {
            features.recoveryDecayProfile == "VERY_LONG" ||
                features.axialLoadLevel == "HIGH" ||
                loadSignal >= 0.85 -> BadmintonTransferFatigueCost.VERY_HIGH
            features.recoveryDecayProfile == "LONG" ||
                features.axialLoadLevel == "MODERATE" ||
                loadSignal >= 0.65 -> BadmintonTransferFatigueCost.HIGH
            loadSignal >= 0.35 -> BadmintonTransferFatigueCost.MEDIUM
            else -> BadmintonTransferFatigueCost.LOW
        }
    }

    private val DECELERATION_TYPES = emptySet<String>()
    private val DECELERATION_TARGETS = setOf(
        "DECELERATION_CONTROL",
        "JUMP_LANDING_CONTROL"
    )
    private val DECELERATION_QUALITIES = setOf("DECELERATION")
    private val UNILATERAL_TYPES = emptySet<String>()
    private val UNILATERAL_TARGETS = setOf("FRONT_COURT_LUNGE", "LATERAL_LUNGE")
    private val UNILATERAL_QUALITIES = emptySet<String>()
    private val LATERAL_TYPES = setOf("FOOTWORK_DIRECT")
    private val LATERAL_TARGETS = emptySet<String>()
    private val LATERAL_QUALITIES = emptySet<String>()
    private val ROTATION_TYPES = setOf("ROTATION_POWER_SUPPORTIVE", "ANTI_ROTATION_STABILITY_SUPPORTIVE")
    private val ROTATION_TARGETS = setOf("ROTATION_SEQUENCING", "ANTI_ROTATION_STABILITY")
    private val ROTATION_QUALITIES = setOf(
        "ROTATIONAL_CONTROL",
        "ROTATIONAL_POWER",
        "ROTATIONAL_STRENGTH",
        "ANTI_ROTATION_STABILITY"
    )
    private val RACKET_TYPES = emptySet<String>()
    private val RACKET_TARGETS = setOf(
        "OVERHEAD_CLEAR",
        "OVERHEAD_POWER",
        "SMASH",
        "DRIVE",
        "GRIP_ENDURANCE",
        "SHOULDER_DURABILITY"
    )
    private val RACKET_QUALITIES = emptySet<String>()
    private val CONDITIONING_TYPES = emptySet<String>()
    private val CONDITIONING_TARGETS = emptySet<String>()
    private val CONDITIONING_QUALITIES = emptySet<String>()
    private val LOW_FATIGUE_SUPPORT_TYPES = setOf("CORE_STABILITY_SUPPORTIVE")
    private val LEGACY_EXPLICIT_ANTI_ROTATION_OBJECTIVE_KEYS = setOf("landmine_anti_rotation")

    private val CANONICAL_TRANSFER_TYPES = setOf(
        "ANKLE_CALF_SSC_SUPPORTIVE",
        "ANTI_ROTATION_STABILITY_SUPPORTIVE",
        "CHANGE_OF_DIRECTION_DIRECT",
        "CORE_STABILITY_SUPPORTIVE",
        "FOOTWORK_DIRECT",
        "GENERAL_CONDITIONING_SUPPORTIVE",
        "GENERAL_POWER_SUPPORTIVE",
        "GENERAL_STRENGTH_SUPPORTIVE",
        "GRIP_WRIST_SUPPORTIVE",
        "LOWER_BODY_SUPPORTIVE",
        "LUNGE_REACH_DIRECT",
        "OVERHEAD_HITTING_DIRECT",
        "OVERHEAD_POWER_SUPPORTIVE",
        "RALLY_CONDITIONING_DIRECT",
        "REACTION_DECISION_DIRECT",
        "ROTATION_POWER_SUPPORTIVE",
        "SHOULDER_CARE_SUPPORTIVE",
        "SHOULDER_SCAPULAR_SUPPORTIVE"
    )
    private val CANONICAL_SKILL_TARGETS = setOf(
        "ANTI_ROTATION_STABILITY",
        "CHANGE_OF_DIRECTION",
        "DECELERATION_CONTROL",
        "DEFENSIVE_COVERAGE",
        "DRIVE",
        "FIRST_STEP",
        "FRONT_COURT_LUNGE",
        "LATERAL_LUNGE",
        "LATERAL_MOVEMENT",
        "MULTI_SHUTTLE_ENDURANCE",
        "NET_PLAY",
        "OVERHEAD_CLEAR",
        "OVERHEAD_POWER",
        "RALLY_TOLERANCE",
        "ROTATION_SEQUENCING",
        "SHOULDER_DURABILITY",
        "SMASH",
        "SPLIT_STEP"
    )
    private val CANONICAL_PHYSICAL_QUALITIES = setOf(
        "ACCELERATION",
        "AEROBIC_BASE",
        "ANAEROBIC_REPEATABILITY",
        "ANKLE_STIFFNESS",
        "ANTI_ROTATION_STABILITY",
        "CALF_ELASTICITY",
        "CORE_BRACING",
        "DECELERATION",
        "FRONTAL_PLANE_CONTROL",
        "GRIP_ENDURANCE",
        "HIP_CONTROL",
        "HIP_KNEE_EXTENSION_STRENGTH",
        "LOWER_BODY_FORCE",
        "POSTERIOR_CHAIN",
        "REACTIVE_AGILITY",
        "ROTATIONAL_CONTROL",
        "ROTATIONAL_POWER",
        "ROTATIONAL_STRENGTH",
        "ROTATOR_CUFF_CONTROL",
        "SCAPULAR_CONTROL",
        "SHOULDER_DURABILITY",
        "SINGLE_LEG_STABILITY",
        "UPPER_BODY_EXPLOSIVE_POWER"
    )

}

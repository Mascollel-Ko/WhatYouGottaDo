package com.training.trackplanner.analysis.contracts

import com.training.trackplanner.analysis.features.AnalysisExerciseFeatures

/** Frozen test oracle for the retired seven-axis rows in the v1 contract CSV. */
internal object LegacyBadmintonContractOracle {
    fun transferType(features: AnalysisExerciseFeatures): LegacyBadmintonTransferType =
        when (features.canonicalBadmintonTransferLevel.ifBlank { features.badmintonTransferStrength }) {
            "DIRECT" -> LegacyBadmintonTransferType.DIRECT
            "SUPPORTIVE" -> LegacyBadmintonTransferType.SUPPORTIVE
            "GENERAL" -> LegacyBadmintonTransferType.GENERAL_STRENGTH
            "NONE" -> LegacyBadmintonTransferType.NONE
            else -> {
                val hasMetadata =
                    "BADMINTON_TRANSFER" in features.analysisEligibility ||
                        features.badmintonTransferRoles.isNotEmpty() ||
                        features.badmintonSkillTargets.isNotEmpty()
                if (hasMetadata) LegacyBadmintonTransferType.LOW else LegacyBadmintonTransferType.NONE
            }
        }

    fun axes(features: AnalysisExerciseFeatures): Set<LegacyBadmintonTransferAxis> {
        val axes = linkedSetOf<LegacyBadmintonTransferAxis>()
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
        ) axes += LegacyBadmintonTransferAxis.DECELERATION_LANDING

        if (
            features.laterality in setOf("UNILATERAL", "ALTERNATING", "ASYMMETRIC") ||
            features.balanceContributionTags.any {
                it in setOf("UNILATERAL_LOWER", "UNILATERAL_UPPER", "HIP_STABILITY", "KNEE_CONTROL")
            } ||
            "LUNGE_REACH" in transferTokens ||
            "LUNGE_REACH" in skillTargets
        ) axes += LegacyBadmintonTransferAxis.UNILATERAL_STABILITY

        if (
            transferTokens.any { it in setOf("FOOTWORK", "ACCELERATION") } ||
            features.courtMovementTypes.any {
                it in setOf(
                    "SPLIT_STEP", "FIRST_STEP", "LATERAL_MOVE", "CROSSOVER", "FRONT_LUNGE",
                    "REAR_COURT", "MULTI_DIRECTION", "REACTION_RANDOM", "RECOVERY_STEP"
                )
            } ||
            features.movementPattern in setOf("FOOTWORK", "LOCOMOTION") ||
            features.plane == "FRONTAL"
        ) axes += LegacyBadmintonTransferAxis.LATERAL_MOVEMENT

        if (
            features.movementPattern in setOf("ROTATION", "ANTI_ROTATION") ||
            features.fatigueCategories.any { it in setOf("ROTATION_POWER", "ANTI_ROTATION") } ||
            transferTokens.any { it in setOf("ROTATION_POWER", "ANTI_ROTATION_STABILITY") } ||
            skillTargets.any { it in setOf("ROTATION_SEQUENCING", "ANTI_ROTATION_STABILITY") } ||
            features.balanceContributionTags.any { it in setOf("ROTATION", "ANTI_ROTATION") } ||
            features.forceType in setOf("ROTATE", "BRACE")
        ) axes += LegacyBadmintonTransferAxis.ROTATION_CONTROL

        if (
            transferTokens.any { it in setOf("OVERHEAD_POWER", "SHOULDER_CARE", "GRIP_FOREARM") } ||
            skillTargets.any { it in setOf("OVERHEAD_POWER", "GRIP_ENDURANCE", "SHOULDER_DURABILITY") } ||
            features.fatigueCategories.any { it in setOf("OVERHEAD_REPETITION", "GRIP_FOREARM") } ||
            (features.primaryMuscles + features.secondaryMuscles).any {
                it in setOf("SHOULDERS", "ROTATOR_CUFF", "FOREARM_GRIP", "LATS_UPPER_BACK")
            }
        ) axes += LegacyBadmintonTransferAxis.RACKET_SUPPORT

        if (
            features.supportsConditioningOrSkillAnalysis ||
            "CONDITIONING" in transferTokens ||
            "CONDITIONING" in skillTargets ||
            "FOOTWORK_SPEED" in skillTargets
        ) axes += LegacyBadmintonTransferAxis.AEROBIC_FOOTWORK

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
        ) axes += LegacyBadmintonTransferAxis.LOW_FATIGUE_CONTROL

        return axes
    }

    fun fatigueCost(features: AnalysisExerciseFeatures): LegacyBadmintonFatigueCost {
        val loadSignal = listOf(
            features.systemicLoadWeight,
            features.neuralHeavyWeight,
            features.neuralSpeedWeight,
            features.decelerationWeight,
            features.elasticSscWeight
        ).maxOrNull() ?: 0.0

        return when {
            features.recoveryDecayProfile == "VERY_LONG" ||
                features.axialLoadLevel == "HIGH" || loadSignal >= 0.85 -> LegacyBadmintonFatigueCost.VERY_HIGH
            features.recoveryDecayProfile == "LONG" ||
                features.axialLoadLevel == "MODERATE" || loadSignal >= 0.65 -> LegacyBadmintonFatigueCost.HIGH
            loadSignal >= 0.35 -> LegacyBadmintonFatigueCost.MEDIUM
            else -> LegacyBadmintonFatigueCost.LOW
        }
    }
}

internal enum class LegacyBadmintonTransferType { DIRECT, SUPPORTIVE, GENERAL_STRENGTH, LOW, NONE }

internal enum class LegacyBadmintonTransferAxis {
    DECELERATION_LANDING,
    UNILATERAL_STABILITY,
    LATERAL_MOVEMENT,
    ROTATION_CONTROL,
    RACKET_SUPPORT,
    AEROBIC_FOOTWORK,
    LOW_FATIGUE_CONTROL
}

internal enum class LegacyBadmintonFatigueCost { LOW, MEDIUM, HIGH, VERY_HIGH }

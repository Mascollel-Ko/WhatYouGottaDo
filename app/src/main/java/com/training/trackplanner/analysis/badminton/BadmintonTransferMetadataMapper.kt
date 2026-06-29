package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.features.AnalysisExerciseFeatures

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

    fun transferAxes(features: AnalysisExerciseFeatures): Set<BadmintonTransferAxis> {
        val axes = linkedSetOf<BadmintonTransferAxis>()
        val movementPatternTokens = features.movementPattern.tokens()
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
            features.movementPattern in setOf("SQUAT", "HINGE", "LUNGE") ||
            features.forceType in setOf("LOWER_BODY", "HINGE") ||
            movementPatternTokens.any { token ->
                token in setOf(
                    "KNEE_DOMINANT",
                    "HIP_HINGE",
                    "LOWER_BODY_STRENGTH",
                    "SQUAT_PATTERN",
                    "LUNGE_PATTERN",
                    "POSTERIOR_CHAIN_STRENGTH",
                    "UNILATERAL_LOWER"
                )
            } ||
            transferTokens.any { token ->
                token in setOf(
                    "LOWER_BODY_FORCE",
                    "LOWER_BODY_SUPPORTIVE",
                    "HIP_KNEE_EXTENSION_STRENGTH"
                )
            } ||
            features.adaptiveBaselineGroups.any { group ->
                group in setOf("HEAVY_LOWER", "HINGE", "SQUAT_PATTERN")
            } ||
            features.balanceContributionTags.any { tag ->
                tag in setOf("LOWER_PUSH", "LOWER_PULL", "POSTERIOR_CHAIN", "ANTERIOR_CHAIN")
            }
        ) {
            axes += BadmintonTransferAxis.LOWER_BODY_STRENGTH
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
            features.trainingRole in setOf("CONDITIONING", "SKILL") ||
            features.movementCategory in setOf("CONDITIONING", "SKILL_DRILL") ||
            "CONDITIONING" in transferTokens ||
            "CONDITIONING" in skillTargets ||
            "FOOTWORK_SPEED" in skillTargets
        ) {
            axes += BadmintonTransferAxis.AEROBIC_FOOTWORK
        }

        if (
            features.trainingRole in setOf("PREHAB", "STABILITY", "MOBILITY", "RECOVERY") ||
            features.movementCategory in setOf("STABILITY", "MOBILITY", "PREHAB", "RECOVERY") ||
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

    private fun String.tokens(): Set<String> =
        split('|', ',', '/', ';', ' ')
            .map { token -> token.trim() }
            .filter { token -> token.isNotEmpty() }
            .toSet()
}

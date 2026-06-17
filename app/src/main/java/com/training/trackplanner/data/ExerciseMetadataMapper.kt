package com.training.trackplanner.data

data class SeedMetadataSource(
    val movementPatternTokens: Set<String> = emptySet(),
    val movementCategoryToken: String = "",
    val forceTypeToken: String = "",
    val planeToken: String = "",
    val isUnilateral: Boolean? = null
)

object ExerciseMetadataMapper {
    fun applySeedMetadata(
        exercise: Exercise,
        source: SeedMetadataSource
    ): Exercise =
        applyMetadata(exercise, source)

    fun applyLegacyMetadata(exercise: Exercise): Exercise =
        applyMetadata(
            exercise = exercise,
            seed = SeedMetadataSource(
                movementPatternTokens = exercise.movementPattern.splitTokens(),
                movementCategoryToken = exercise.movementCategory,
                forceTypeToken = exercise.forceType,
                planeToken = exercise.plane,
                isUnilateral = null
            )
        )

    private fun applyMetadata(
        exercise: Exercise,
        seed: SeedMetadataSource
    ): Exercise {
        val source = MetadataSource.from(exercise, seed)
        val movementPattern = source.toMovementPattern()
        val movementCategory = source.toMovementCategory(movementPattern)
        val compoundType = source.toCompoundType(movementPattern)
        val forceType = source.toForceType(movementPattern)
        val plane = source.toPlane(movementPattern)
        val laterality = source.toLaterality()
        val axialLoadLevel = source.toAxialLoadLevel(movementPattern)
        val trainingRole = source.toTrainingRole(movementPattern, movementCategory, compoundType)
        val badmintonRoles = source.toBadmintonTransferRoles(movementPattern)
        val weights = source.toWeights(
            movementPattern = movementPattern,
            movementCategory = movementCategory,
            compoundType = compoundType,
            axialLoadLevel = axialLoadLevel,
            trainingRole = trainingRole,
            badmintonRoles = badmintonRoles
        )
        val fatigueCategories = source.toFatigueCategories(weights, trainingRole)
        val baselineGroups = source.toBaselineGroups(
            movementPattern = movementPattern,
            forceType = forceType,
            axialLoadLevel = axialLoadLevel,
            badmintonRoles = badmintonRoles,
            fatigueCategories = fatigueCategories
        )
        val recoveryDecayProfile = weights.toRecoveryDecayProfile(trainingRole)
        val progressMetricType = source.toProgressMetricType(
            movementPattern = movementPattern,
            movementCategory = movementCategory,
            compoundType = compoundType,
            trainingRole = trainingRole
        )
        val estimated1RmEligible = progressMetricType == ProgressMetricType.ESTIMATED_1RM
        val volumeLoadEligible = progressMetricType in setOf(
            ProgressMetricType.ESTIMATED_1RM,
            ProgressMetricType.VOLUME_LOAD,
            ProgressMetricType.REPS_AT_LOAD
        )
        val strengthProgressionGroup = source.toStrengthProgressionGroup(movementPattern)
        val hypertrophyVolumeGroup = source.toHypertrophyVolumeGroup(movementPattern)
        val mainLiftGroup = source.toMainLiftGroup(movementPattern)
        val accessoryContributionGroup = source.toAccessoryContributionGroup(
            movementPattern = movementPattern,
            trainingRole = trainingRole,
            badmintonRoles = badmintonRoles
        )
        val badmintonTransferStrength = source.toBadmintonTransferStrength(badmintonRoles)
        val courtMovementTypes = source.toCourtMovementTypes(
            movementPattern = movementPattern,
            badmintonRoles = badmintonRoles
        )
        val badmintonSkillTargets = source.toBadmintonSkillTargets(
            badmintonRoles = badmintonRoles,
            courtMovementTypes = courtMovementTypes
        )
        val jointStressTags = source.toJointStressTags(movementPattern)
        val stabilityDemandLevel = source.toStabilityDemandLevel(
            movementPattern = movementPattern,
            laterality = laterality,
            badmintonRoles = badmintonRoles
        )
        val mobilityDemandLevel = source.toMobilityDemandLevel(movementPattern)
        val balanceContributionTags = source.toBalanceContributionTags(
            movementPattern = movementPattern,
            laterality = laterality
        )
        val analysisEligibility = source.toAnalysisEligibility(
            trainingRole = trainingRole,
            progressMetricType = progressMetricType,
            badmintonTransferStrength = badmintonTransferStrength,
            balanceContributionTags = balanceContributionTags
        )
        val confidence = source.toConfidence(seed)

        val mapped = exercise.copy(
            movementPattern = movementPattern.name,
            movementCategory = movementCategory.name,
            equipment = exercise.equipment.ifBlank { exercise.equipmentTags },
            compoundType = compoundType.name,
            forceType = forceType.name,
            plane = plane.name,
            laterality = laterality.name,
            axialLoadLevel = axialLoadLevel.name,
            trainingRole = trainingRole.name,
            badmintonTransferRoles = badmintonRoles.toTokenString(),
            fatigueCategories = fatigueCategories.toTokenString(),
            adaptiveBaselineGroups = baselineGroups.toTokenString(),
            recoveryDecayProfile = recoveryDecayProfile.name,
            systemicLoadWeight = weights.systemicLoadWeight,
            neuralHeavyWeight = weights.neuralHeavyWeight,
            neuralSpeedWeight = weights.neuralSpeedWeight,
            localLoadWeight = weights.localLoadWeight,
            decelerationWeight = weights.decelerationWeight,
            elasticSscWeight = weights.elasticSscWeight,
            rotationPowerWeight = weights.rotationPowerWeight,
            antiRotationWeight = weights.antiRotationWeight,
            overheadSwingWeight = weights.overheadSwingWeight,
            gripLoadWeight = weights.gripLoadWeight,
            progressMetricType = progressMetricType.name,
            strengthProgressionGroup = strengthProgressionGroup.name,
            hypertrophyVolumeGroup = hypertrophyVolumeGroup.name,
            mainLiftGroup = mainLiftGroup.name,
            accessoryContributionGroup = accessoryContributionGroup.name,
            estimated1RmEligible = estimated1RmEligible,
            volumeLoadEligible = volumeLoadEligible,
            badmintonTransferStrength = badmintonTransferStrength.name,
            courtMovementTypes = courtMovementTypes.toTokenString(),
            badmintonSkillTargets = badmintonSkillTargets.toTokenString(),
            jointStressTags = jointStressTags.toTokenString(),
            stabilityDemandLevel = stabilityDemandLevel.name,
            mobilityDemandLevel = mobilityDemandLevel.name,
            balanceContributionTags = balanceContributionTags.toTokenString(),
            analysisEligibility = analysisEligibility.toTokenString(),
            metadataConfidence = confidence.name
        ).withInferredPlanningMetadata()
        val check = MetadataSanityChecker.check(mapped)
        return if (check.needsReview && mapped.metadataConfidence != MetadataConfidence.NEEDS_REVIEW.name) {
            mapped.copy(metadataConfidence = MetadataConfidence.NEEDS_REVIEW.name)
        } else {
            mapped
        }
    }

    private data class MetadataSource(
        val tokens: Set<String>,
        val categoryToken: String,
        val forceToken: String,
        val planeToken: String,
        val lateralityToken: String,
        val loadProfileToken: String,
        val trainingRoleToken: String,
        val sportTransferTokens: Set<String>,
        val primaryMuscleTokens: Set<String>,
        val secondaryMuscleTokens: Set<String>
    ) {
        fun hasAny(vararg candidates: String): Boolean =
            candidates.any { candidate ->
                candidate in tokens ||
                    candidate == categoryToken ||
                    candidate == forceToken ||
                    candidate == planeToken ||
                    candidate == lateralityToken ||
                    candidate == loadProfileToken ||
                    candidate == trainingRoleToken ||
                    candidate in sportTransferTokens ||
                    candidate in primaryMuscleTokens ||
                    candidate in secondaryMuscleTokens
            }

        companion object {
            fun from(exercise: Exercise, seed: SeedMetadataSource): MetadataSource {
                val sourceTokens = if (seed.movementPatternTokens.isNotEmpty()) {
                    seed.movementPatternTokens
                } else {
                    exercise.movementPattern.splitTokens()
                }
                val laterality = when {
                    seed.isUnilateral == true -> "UNILATERAL"
                    seed.isUnilateral == false -> "BILATERAL"
                    else -> exercise.laterality
                }
                return MetadataSource(
                    tokens = sourceTokens + exercise.stabilityRoles.splitTokens() + exercise.accessoryRoles.splitTokens(),
                    categoryToken = seed.movementCategoryToken.ifBlank { exercise.movementCategory },
                    forceToken = seed.forceTypeToken.ifBlank { exercise.forceType },
                    planeToken = seed.planeToken.ifBlank { exercise.plane },
                    lateralityToken = laterality,
                    loadProfileToken = exercise.loadProfile,
                    trainingRoleToken = exercise.trainingRole,
                    sportTransferTokens = exercise.sportTransferDirect.splitTokens() + exercise.sportTransferSupportive.splitTokens(),
                    primaryMuscleTokens = exercise.primaryMuscles.splitTokens(),
                    secondaryMuscleTokens = exercise.secondaryMuscles.splitTokens()
                )
            }
        }
    }

    private data class FatigueWeights(
        val systemicLoadWeight: Double,
        val neuralHeavyWeight: Double,
        val neuralSpeedWeight: Double,
        val localLoadWeight: Double,
        val decelerationWeight: Double,
        val elasticSscWeight: Double,
        val rotationPowerWeight: Double,
        val antiRotationWeight: Double,
        val overheadSwingWeight: Double,
        val gripLoadWeight: Double
    )

    private fun MetadataSource.toMovementPattern(): MovementPattern = when {
        hasAny("HIP_HINGE", "HINGE_LOWER", "POSTERIOR_CHAIN_STRENGTH", "HINGE") -> MovementPattern.HINGE
        hasAny("SQUAT_PATTERN", "KNEE_DOMINANT", "KNEE_DOMINANT_LOWER") -> MovementPattern.SQUAT
        hasAny("LUNGE_PATTERN", "SINGLE_LEG_STRENGTH") -> MovementPattern.LUNGE
        hasAny("PUSH_HORIZONTAL", "HORIZONTAL_PUSH", "CHEST_STRENGTH") -> MovementPattern.PUSH_HORIZONTAL
        hasAny("PUSH_VERTICAL", "VERTICAL_PUSH", "SHOULDER_STRENGTH") -> MovementPattern.PUSH_VERTICAL
        hasAny("PULL_HORIZONTAL", "HORIZONTAL_PULL", "BACK_STRENGTH") -> MovementPattern.PULL_HORIZONTAL
        hasAny("PULL_VERTICAL", "VERTICAL_PULL") -> MovementPattern.PULL_VERTICAL
        hasAny("LOADED_CARRY", "CARRY") -> MovementPattern.CARRY
        hasAny("ROTATION", "ROTATION_CORE", "ROTATIONAL_POWER", "TRUNK_ROTATION") -> MovementPattern.ROTATION
        hasAny("ANTI_ROTATION", "ANTI_ROTATION_CORE", "ANTI_ROTATION_SUPPORT", "ANTI_EXTENSION", "ANTI_LATERAL_FLEXION") -> MovementPattern.ANTI_ROTATION
        hasAny("FOOTWORK_DIRECT", "FOOTWORK_LIGHT", "SPORT_SKILL", "REACTION_AGILITY_DIRECT", "CHANGE_OF_DIRECTION_DIRECT") -> MovementPattern.FOOTWORK
        hasAny("LOW_LEVEL_HOP", "SINGLE_LEG_DECEL") -> MovementPattern.HOP
        hasAny("LATERAL_DECELERATION_DIRECT", "DECELERATION_DIRECT", "DECELERATION_SUPPORT") -> MovementPattern.BOUND
        hasAny("PLYOMETRIC_SSC", "JUMPING", "JUMP_ROPE") -> MovementPattern.JUMP
        hasAny("CARDIO", "RUNNING_DRILL", "AEROBIC_ANAEROBIC") -> MovementPattern.LOCOMOTION
        hasAny("MOBILITY", "HIP_MOBILITY", "SHOULDER_MOBILITY", "ANKLE_HIP_MOBILITY") -> MovementPattern.MOBILITY
        hasAny("SHOULDER_PREHAB", "SHOULDER_EXTERNAL_ROTATION", "EXTERNAL_ROTATION_SUPPORT") -> MovementPattern.PREHAB
        hasAny(
            "ELBOW_FLEXION",
            "ELBOW_EXTENSION",
            "WRIST_FOREARM",
            "FOREARM_ACCESSORY",
            "SHOULDER_ABDUCTION",
            "SHOULDER_ISOLATION",
            "SHOULDER_HORIZONTAL_ABDUCTION"
        ) || forceToken == "ISOLATION" -> MovementPattern.ISOLATION
        else -> MovementPattern.SQUAT
    }

    private fun MetadataSource.toMovementCategory(pattern: MovementPattern): MovementCategory = when {
        categoryToken == "PLYOMETRIC" || pattern in setOf(MovementPattern.JUMP, MovementPattern.HOP, MovementPattern.BOUND) -> MovementCategory.PLYOMETRIC
        categoryToken in setOf("AGILITY", "SPORT_SPECIFIC") || hasAny("REACTION_AGILITY_DIRECT") -> MovementCategory.REACTIVE
        categoryToken == "RUNNING_DRILL" -> MovementCategory.SPEED
        categoryToken == "ACCESSORY" -> MovementCategory.HYPERTROPHY
        categoryToken == "SUPPORT" && pattern == MovementPattern.PREHAB -> MovementCategory.PREHAB
        categoryToken == "SUPPORT" -> MovementCategory.STABILITY
        categoryToken == "CARDIO" || categoryToken == "CONDITIONING" -> MovementCategory.CONDITIONING
        categoryToken == "SPORT" || pattern == MovementPattern.FOOTWORK -> MovementCategory.SKILL_DRILL
        categoryToken == "MOBILITY" || pattern == MovementPattern.MOBILITY -> MovementCategory.MOBILITY
        trainingRoleToken == "PREHAB" -> MovementCategory.PREHAB
        trainingRoleToken == "RECOVERY" -> MovementCategory.RECOVERY
        trainingRoleToken == "TEST" -> MovementCategory.TEST
        trainingRoleToken == "POWER" || categoryToken == "POWER" -> MovementCategory.POWER
        trainingRoleToken == "HYPERTROPHY" -> MovementCategory.HYPERTROPHY
        else -> MovementCategory.STRENGTH
    }

    private fun MetadataSource.toCompoundType(pattern: MovementPattern): CompoundType = when {
        pattern == MovementPattern.ISOLATION -> CompoundType.ISOLATION
        pattern in setOf(MovementPattern.FOOTWORK, MovementPattern.HOP, MovementPattern.BOUND) -> CompoundType.DRILL
        pattern in setOf(MovementPattern.ROTATION, MovementPattern.ANTI_ROTATION, MovementPattern.PREHAB, MovementPattern.MOBILITY) -> CompoundType.HYBRID
        else -> CompoundType.COMPOUND
    }

    private fun MetadataSource.toForceType(pattern: MovementPattern): FatigueForceType = when (pattern) {
        MovementPattern.PUSH_HORIZONTAL,
        MovementPattern.PUSH_VERTICAL -> FatigueForceType.PUSH
        MovementPattern.PULL_HORIZONTAL,
        MovementPattern.PULL_VERTICAL -> FatigueForceType.PULL
        MovementPattern.HINGE -> FatigueForceType.HINGE
        MovementPattern.SQUAT,
        MovementPattern.LUNGE -> FatigueForceType.SQUAT
        MovementPattern.ROTATION -> FatigueForceType.ROTATE
        MovementPattern.ANTI_ROTATION,
        MovementPattern.PREHAB,
        MovementPattern.MOBILITY,
        MovementPattern.ISOLATION -> FatigueForceType.BRACE
        MovementPattern.CARRY -> FatigueForceType.CARRY
        MovementPattern.JUMP,
        MovementPattern.HOP -> FatigueForceType.LAND
        MovementPattern.BOUND -> FatigueForceType.DECELERATE
        MovementPattern.FOOTWORK,
        MovementPattern.LOCOMOTION -> FatigueForceType.ACCELERATE
    }

    private fun MetadataSource.toPlane(pattern: MovementPattern): Plane = when (planeToken) {
        "FRONTAL" -> Plane.FRONTAL
        "TRANSVERSE", "ANTI_ROTATION" -> Plane.TRANSVERSE
        "MULTIPLANAR", "MULTI_PLANAR" -> Plane.MULTI_PLANAR
        "SAGITTAL", "ANTI_EXTENSION" -> Plane.SAGITTAL
        else -> when (pattern) {
            MovementPattern.ROTATION,
            MovementPattern.ANTI_ROTATION,
            MovementPattern.FOOTWORK,
            MovementPattern.BOUND -> Plane.MULTI_PLANAR
            MovementPattern.CARRY -> Plane.MULTI_PLANAR
            else -> Plane.SAGITTAL
        }
    }

    private fun MetadataSource.toLaterality(): FatigueLaterality = when (lateralityToken) {
        "UNILATERAL_ALTERNATING" -> FatigueLaterality.ALTERNATING
        "CONTRALATERAL" -> FatigueLaterality.ASYMMETRIC
        "UNILATERAL", "UNILATERAL_UPPER", "UNILATERAL_LOWER" -> FatigueLaterality.UNILATERAL
        else -> FatigueLaterality.BILATERAL
    }

    private fun MetadataSource.toAxialLoadLevel(pattern: MovementPattern): AxialLoadLevel = when {
        loadProfileToken == "HIGH_AXIAL_LOAD" -> AxialLoadLevel.HIGH
        loadProfileToken in setOf("LUMBAR_STRESS_HIGH", "HIGH_LOAD") -> AxialLoadLevel.MODERATE
        pattern in setOf(MovementPattern.HINGE, MovementPattern.SQUAT) -> AxialLoadLevel.MODERATE
        pattern == MovementPattern.LUNGE -> AxialLoadLevel.LOW
        loadProfileToken in setOf("LOW_LOAD", "LOW_AXIAL_LOAD") -> AxialLoadLevel.LOW
        else -> AxialLoadLevel.NONE
    }

    private fun MetadataSource.toTrainingRole(
        pattern: MovementPattern,
        category: MovementCategory,
        compoundType: CompoundType
    ): FatigueTrainingRole = when {
        category == MovementCategory.PLYOMETRIC -> FatigueTrainingRole.PLYOMETRIC
        category == MovementCategory.REACTIVE || category == MovementCategory.SPEED -> FatigueTrainingRole.SPEED_REACTIVE
        category == MovementCategory.POWER -> FatigueTrainingRole.POWER
        category == MovementCategory.PREHAB || pattern == MovementPattern.PREHAB -> FatigueTrainingRole.PREHAB
        category == MovementCategory.MOBILITY || pattern == MovementPattern.MOBILITY -> FatigueTrainingRole.MOBILITY
        category == MovementCategory.CONDITIONING || pattern == MovementPattern.LOCOMOTION -> FatigueTrainingRole.CONDITIONING
        category == MovementCategory.SKILL_DRILL || pattern == MovementPattern.FOOTWORK -> FatigueTrainingRole.SKILL
        category == MovementCategory.TEST -> FatigueTrainingRole.TEST
        category == MovementCategory.RECOVERY -> FatigueTrainingRole.RECOVERY
        category == MovementCategory.STABILITY || pattern == MovementPattern.ANTI_ROTATION -> FatigueTrainingRole.STABILITY
        compoundType == CompoundType.ISOLATION || category == MovementCategory.HYPERTROPHY -> FatigueTrainingRole.ACCESSORY
        pattern in setOf(MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.PUSH_HORIZONTAL, MovementPattern.PULL_VERTICAL) -> FatigueTrainingRole.MAIN_STRENGTH
        else -> FatigueTrainingRole.SECONDARY_STRENGTH
    }

    private fun MetadataSource.toBadmintonTransferRoles(
        pattern: MovementPattern
    ): Set<BadmintonTransferRole> {
        val roles = linkedSetOf<BadmintonTransferRole>()
        if (pattern == MovementPattern.FOOTWORK || hasAny("BADMINTON_FOOTWORK", "FOOTWORK_DIRECT")) {
            roles += BadmintonTransferRole.FOOTWORK
        }
        if (hasAny("BADMINTON_DIRECT_PLAY")) {
            roles += BadmintonTransferRole.FOOTWORK
            roles += BadmintonTransferRole.CONDITIONING
        }
        if (hasAny("REACTION", "REACTION_AGILITY_DIRECT")) roles += BadmintonTransferRole.REACTION
        if (hasAny("ACCELERATION_DECELERATION_TRANSITION")) roles += BadmintonTransferRole.ACCELERATION
        if (hasAny("DECELERATION", "DECELERATION_DIRECT", "LATERAL_DECELERATION_DIRECT", "CHANGE_OF_DIRECTION_DIRECT")) {
            roles += BadmintonTransferRole.DECELERATION
        }
        if (pattern == MovementPattern.LUNGE) roles += BadmintonTransferRole.LUNGE_REACH
        if (pattern in setOf(MovementPattern.JUMP, MovementPattern.HOP, MovementPattern.BOUND) || hasAny("LANDING_CONTROL", "JUMP_POWER")) {
            roles += BadmintonTransferRole.JUMP_LANDING
        }
        if (hasAny("OVERHEAD_POWER", "SMASH_POWER")) roles += BadmintonTransferRole.OVERHEAD_POWER
        if (pattern == MovementPattern.ROTATION || hasAny("ROTATIONAL_POWER")) roles += BadmintonTransferRole.ROTATION_POWER
        if (pattern == MovementPattern.ANTI_ROTATION) roles += BadmintonTransferRole.ANTI_ROTATION_STABILITY
        if (hasAny("FOREARM_GRIP_SUPPORT", "GRIP_SUPPORT", "GRIP_ENDURANCE", "GRIP_FOREARM_SUPPORT")) {
            roles += BadmintonTransferRole.GRIP_FOREARM
        }
        if (hasAny("SHOULDER_PREHAB", "ROTATOR_CUFF_SUPPORT", "SHOULDER_STABILITY_SUPPORT")) {
            roles += BadmintonTransferRole.SHOULDER_CARE
        }
        if (hasAny("COURT_CONDITIONING") || pattern == MovementPattern.LOCOMOTION) {
            roles += BadmintonTransferRole.CONDITIONING
        }
        return roles.ifEmpty { setOf(BadmintonTransferRole.NONE) }
    }

    private fun MetadataSource.toWeights(
        movementPattern: MovementPattern,
        movementCategory: MovementCategory,
        compoundType: CompoundType,
        axialLoadLevel: AxialLoadLevel,
        trainingRole: FatigueTrainingRole,
        badmintonRoles: Set<BadmintonTransferRole>
    ): FatigueWeights {
        val isHeavyLower = movementPattern in setOf(MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.LUNGE)
        val isUpperCompound = movementPattern in setOf(
            MovementPattern.PUSH_HORIZONTAL,
            MovementPattern.PUSH_VERTICAL,
            MovementPattern.PULL_HORIZONTAL,
            MovementPattern.PULL_VERTICAL
        ) && compoundType == CompoundType.COMPOUND
        val isPrehab = trainingRole == FatigueTrainingRole.PREHAB || movementPattern == MovementPattern.PREHAB
        val systemic = when {
            isPrehab -> 0.0
            isHeavyLower && axialLoadLevel in setOf(AxialLoadLevel.MODERATE, AxialLoadLevel.HIGH) -> 0.75
            isUpperCompound -> 0.5
            movementCategory == MovementCategory.CONDITIONING -> 0.5
            compoundType == CompoundType.ISOLATION -> 0.25
            else -> 0.5
        }
        val neuralHeavy = when {
            isPrehab -> 0.0
            isHeavyLower && axialLoadLevel in setOf(AxialLoadLevel.MODERATE, AxialLoadLevel.HIGH) -> 0.75
            isUpperCompound -> 0.5
            compoundType == CompoundType.ISOLATION -> 0.25
            else -> 0.5
        }
        val neuralSpeed = when {
            movementCategory in setOf(MovementCategory.REACTIVE, MovementCategory.SPEED) -> 0.75
            movementCategory == MovementCategory.PLYOMETRIC -> 0.5
            badmintonRoles.anyCourtRole() -> 0.5
            else -> 0.0
        }
        val local = when {
            compoundType == CompoundType.ISOLATION || trainingRole == FatigueTrainingRole.ACCESSORY -> 0.75
            isPrehab -> 0.25
            else -> 0.5
        }
        val deceleration = when {
            BadmintonTransferRole.DECELERATION in badmintonRoles || movementPattern == MovementPattern.BOUND -> 0.75
            movementPattern == MovementPattern.FOOTWORK -> 0.5
            else -> 0.0
        }
        val elastic = when {
            movementPattern in setOf(MovementPattern.JUMP, MovementPattern.HOP, MovementPattern.BOUND) -> 0.75
            movementCategory == MovementCategory.PLYOMETRIC -> 0.5
            else -> 0.0
        }
        val rotationPower = if (movementPattern == MovementPattern.ROTATION || BadmintonTransferRole.ROTATION_POWER in badmintonRoles) 0.75 else 0.0
        val antiRotation = if (movementPattern == MovementPattern.ANTI_ROTATION || BadmintonTransferRole.ANTI_ROTATION_STABILITY in badmintonRoles) 0.75 else 0.0
        val overhead = when {
            BadmintonTransferRole.OVERHEAD_POWER in badmintonRoles -> 0.75
            BadmintonTransferRole.SHOULDER_CARE in badmintonRoles -> 0.5
            movementPattern == MovementPattern.PUSH_VERTICAL || hasAny("SHOULDER", "ROTATOR_CUFF") -> 0.5
            else -> 0.0
        }
        val grip = when {
            BadmintonTransferRole.GRIP_FOREARM in badmintonRoles -> 0.75
            movementPattern in setOf(MovementPattern.CARRY, MovementPattern.PULL_VERTICAL) -> 0.5
            hasAny("FOREARM", "GRIP") -> 0.5
            else -> 0.0
        }
        return FatigueWeights(
            systemicLoadWeight = systemic,
            neuralHeavyWeight = neuralHeavy,
            neuralSpeedWeight = neuralSpeed,
            localLoadWeight = local,
            decelerationWeight = deceleration,
            elasticSscWeight = elastic,
            rotationPowerWeight = rotationPower,
            antiRotationWeight = antiRotation,
            overheadSwingWeight = overhead,
            gripLoadWeight = grip
        )
    }

    private fun MetadataSource.toFatigueCategories(
        weights: FatigueWeights,
        trainingRole: FatigueTrainingRole
    ): Set<FatigueCategory> {
        val categories = linkedSetOf<FatigueCategory>()
        if (weights.systemicLoadWeight > 0.0) categories += FatigueCategory.SYSTEMIC
        if (weights.neuralHeavyWeight > 0.0) categories += FatigueCategory.NEURAL_HEAVY
        if (weights.neuralSpeedWeight > 0.0) categories += FatigueCategory.NEURAL_SPEED
        if (weights.localLoadWeight > 0.0) categories += FatigueCategory.LOCAL_MUSCLE
        if (weights.decelerationWeight > 0.0) categories += FatigueCategory.DECELERATION
        if (weights.elasticSscWeight > 0.0) categories += FatigueCategory.ELASTIC_SSC
        if (weights.rotationPowerWeight > 0.0) categories += FatigueCategory.ROTATION_POWER
        if (weights.antiRotationWeight > 0.0) categories += FatigueCategory.ANTI_ROTATION
        if (weights.overheadSwingWeight > 0.0) categories += FatigueCategory.OVERHEAD_REPETITION
        if (weights.gripLoadWeight > 0.0) categories += FatigueCategory.GRIP_FOREARM
        if (trainingRole == FatigueTrainingRole.PREHAB) categories += FatigueCategory.LOW_FATIGUE_REHAB
        return categories
    }

    private fun MetadataSource.toBaselineGroups(
        movementPattern: MovementPattern,
        forceType: FatigueForceType,
        axialLoadLevel: AxialLoadLevel,
        badmintonRoles: Set<BadmintonTransferRole>,
        fatigueCategories: Set<FatigueCategory>
    ): Set<AdaptiveBaselineGroup> {
        val groups = linkedSetOf<AdaptiveBaselineGroup>()
        if (axialLoadLevel in setOf(AxialLoadLevel.MODERATE, AxialLoadLevel.HIGH)) groups += AdaptiveBaselineGroup.SYSTEMIC
        if (movementPattern in setOf(MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.LUNGE)) {
            groups += AdaptiveBaselineGroup.HEAVY_LOWER
        }
        if (movementPattern == MovementPattern.HINGE) groups += AdaptiveBaselineGroup.HINGE
        if (movementPattern in setOf(MovementPattern.SQUAT, MovementPattern.LUNGE)) groups += AdaptiveBaselineGroup.SQUAT_PATTERN
        if (forceType == FatigueForceType.PUSH) groups += AdaptiveBaselineGroup.UPPER_PUSH
        if (forceType == FatigueForceType.PULL) groups += AdaptiveBaselineGroup.UPPER_PULL
        if (movementPattern == MovementPattern.PUSH_VERTICAL || FatigueCategory.OVERHEAD_REPETITION in fatigueCategories) {
            groups += AdaptiveBaselineGroup.SHOULDER_OVERHEAD
        }
        if (FatigueCategory.GRIP_FOREARM in fatigueCategories) groups += AdaptiveBaselineGroup.GRIP_FOREARM
        if (badmintonRoles.anyCourtRole()) groups += AdaptiveBaselineGroup.BADMINTON_COURT
        if (FatigueCategory.DECELERATION in fatigueCategories) groups += AdaptiveBaselineGroup.DECELERATION
        if (FatigueCategory.ELASTIC_SSC in fatigueCategories) groups += AdaptiveBaselineGroup.ELASTIC_SSC
        if (FatigueCategory.ROTATION_POWER in fatigueCategories) groups += AdaptiveBaselineGroup.ROTATION_POWER
        if (FatigueCategory.ANTI_ROTATION in fatigueCategories) groups += AdaptiveBaselineGroup.ANTI_ROTATION
        if (FatigueCategory.LOW_FATIGUE_REHAB in fatigueCategories) groups += AdaptiveBaselineGroup.RECOVERY_LOW_LOAD
        if (groups.isEmpty()) {
            when {
                movementPattern in setOf(MovementPattern.MOBILITY, MovementPattern.PREHAB) ||
                    trainingRoleToken in setOf("MOBILITY", "PREHAB", "RECOVERY") -> groups += AdaptiveBaselineGroup.RECOVERY_LOW_LOAD
                primaryMuscleTokens.any { token -> token in setOf("HAMSTRING", "GLUTE", "GLUTE_MEDIUS", "ERECTOR_SPINAE") } -> groups += AdaptiveBaselineGroup.HINGE
                primaryMuscleTokens.any { token -> token in setOf("QUADRICEPS", "RECTUS_FEMORIS", "CALF") } -> groups += AdaptiveBaselineGroup.SQUAT_PATTERN
                primaryMuscleTokens.any { token -> token in setOf("CHEST", "UPPER_CHEST", "SHOULDER", "TRICEPS") } -> groups += AdaptiveBaselineGroup.UPPER_PUSH
                primaryMuscleTokens.any { token -> token in setOf("BACK", "LAT", "RHOMBOID", "TRAPEZIUS", "BICEPS") } -> groups += AdaptiveBaselineGroup.UPPER_PULL
                else -> groups += AdaptiveBaselineGroup.RECOVERY_LOW_LOAD
            }
        }
        return groups
    }

    private fun FatigueWeights.toRecoveryDecayProfile(
        trainingRole: FatigueTrainingRole
    ): RecoveryDecayProfile = when {
        trainingRole in setOf(FatigueTrainingRole.PREHAB, FatigueTrainingRole.MOBILITY, FatigueTrainingRole.RECOVERY) -> RecoveryDecayProfile.MINIMAL
        systemicLoadWeight >= 0.75 || neuralHeavyWeight >= 0.75 -> RecoveryDecayProfile.VERY_LONG
        decelerationWeight >= 0.75 || elasticSscWeight >= 0.75 -> RecoveryDecayProfile.LONG
        localLoadWeight >= 0.75 || neuralSpeedWeight >= 0.5 -> RecoveryDecayProfile.MEDIUM
        else -> RecoveryDecayProfile.SHORT
    }

    private fun MetadataSource.toConfidence(seed: SeedMetadataSource): MetadataConfidence = when {
        seed.movementPatternTokens.isNotEmpty() && seed.movementCategoryToken.isNotBlank() -> MetadataConfidence.HIGH
        tokens.isNotEmpty() -> MetadataConfidence.MEDIUM
        else -> MetadataConfidence.LOW
    }

    private fun MetadataSource.toProgressMetricType(
        movementPattern: MovementPattern,
        movementCategory: MovementCategory,
        compoundType: CompoundType,
        trainingRole: FatigueTrainingRole
    ): ProgressMetricType = when {
        trainingRole == FatigueTrainingRole.TEST -> ProgressMetricType.MAX_REPS_TEST
        trainingRole in setOf(
            FatigueTrainingRole.PREHAB,
            FatigueTrainingRole.MOBILITY,
            FatigueTrainingRole.RECOVERY
        ) -> ProgressMetricType.NOT_PROGRESS_TARGET
        movementCategory in setOf(MovementCategory.REACTIVE, MovementCategory.PLYOMETRIC, MovementCategory.SKILL_DRILL) ||
            movementPattern == MovementPattern.FOOTWORK -> ProgressMetricType.QUALITY_BASED
        movementCategory == MovementCategory.CONDITIONING ||
            movementPattern == MovementPattern.LOCOMOTION -> ProgressMetricType.TIME_OR_DISTANCE
        compoundType == CompoundType.ISOLATION ||
            trainingRole == FatigueTrainingRole.ACCESSORY ||
            movementCategory == MovementCategory.HYPERTROPHY -> ProgressMetricType.VOLUME_LOAD
        movementPattern in setOf(
            MovementPattern.SQUAT,
            MovementPattern.HINGE,
            MovementPattern.PUSH_HORIZONTAL,
            MovementPattern.PUSH_VERTICAL,
            MovementPattern.PULL_HORIZONTAL,
            MovementPattern.PULL_VERTICAL
        ) -> ProgressMetricType.ESTIMATED_1RM
        movementPattern == MovementPattern.LUNGE -> ProgressMetricType.REPS_AT_LOAD
        else -> ProgressMetricType.QUALITY_BASED
    }

    private fun MetadataSource.toStrengthProgressionGroup(
        movementPattern: MovementPattern
    ): StrengthProgressionGroup = when (movementPattern) {
        MovementPattern.SQUAT -> StrengthProgressionGroup.SQUAT
        MovementPattern.HINGE -> StrengthProgressionGroup.HINGE
        MovementPattern.LUNGE -> StrengthProgressionGroup.LUNGE
        MovementPattern.PUSH_HORIZONTAL -> StrengthProgressionGroup.UPPER_PUSH_HORIZONTAL
        MovementPattern.PUSH_VERTICAL -> StrengthProgressionGroup.UPPER_PUSH_VERTICAL
        MovementPattern.PULL_HORIZONTAL -> StrengthProgressionGroup.UPPER_PULL_HORIZONTAL
        MovementPattern.PULL_VERTICAL -> StrengthProgressionGroup.UPPER_PULL_VERTICAL
        MovementPattern.CARRY -> StrengthProgressionGroup.CARRY
        MovementPattern.ROTATION,
        MovementPattern.ANTI_ROTATION -> StrengthProgressionGroup.CORE
        MovementPattern.FOOTWORK -> StrengthProgressionGroup.BADMINTON_TEST
        else -> StrengthProgressionGroup.NONE
    }

    private fun MetadataSource.toHypertrophyVolumeGroup(
        movementPattern: MovementPattern
    ): HypertrophyVolumeGroup = when {
        "CHEST" in primaryMuscleTokens || movementPattern == MovementPattern.PUSH_HORIZONTAL -> HypertrophyVolumeGroup.CHEST
        "BACK" in primaryMuscleTokens || "LAT" in primaryMuscleTokens || movementPattern in setOf(
            MovementPattern.PULL_HORIZONTAL,
            MovementPattern.PULL_VERTICAL
        ) -> HypertrophyVolumeGroup.BACK
        primaryMuscleTokens.any { token -> token.contains("SHOULDER") || token.contains("DELTOID") } ||
            movementPattern == MovementPattern.PUSH_VERTICAL -> HypertrophyVolumeGroup.SHOULDERS
        primaryMuscleTokens.any { token -> token in setOf("BICEPS", "TRICEPS") } -> HypertrophyVolumeGroup.ARMS
        "FOREARM" in primaryMuscleTokens || "GRIP" in primaryMuscleTokens -> HypertrophyVolumeGroup.FOREARM_GRIP
        "QUADRICEPS" in primaryMuscleTokens -> HypertrophyVolumeGroup.QUADS
        primaryMuscleTokens.any { token -> token in setOf("HAMSTRING", "ERECTOR_SPINAE") } ||
            movementPattern == MovementPattern.HINGE -> HypertrophyVolumeGroup.POSTERIOR_CHAIN
        primaryMuscleTokens.any { token -> token.startsWith("GLUTE") } -> HypertrophyVolumeGroup.GLUTES
        "CALF" in primaryMuscleTokens -> HypertrophyVolumeGroup.CALVES
        primaryMuscleTokens.any { token -> token in setOf("CORE", "DEEP_CORE", "OBLIQUE") } -> HypertrophyVolumeGroup.CORE
        else -> HypertrophyVolumeGroup.NONE
    }

    private fun MetadataSource.toMainLiftGroup(
        movementPattern: MovementPattern
    ): MainLiftGroup = when (movementPattern) {
        MovementPattern.SQUAT -> MainLiftGroup.SQUAT
        MovementPattern.HINGE -> MainLiftGroup.DEADLIFT
        MovementPattern.PUSH_HORIZONTAL -> MainLiftGroup.BENCH_PRESS
        MovementPattern.PUSH_VERTICAL -> MainLiftGroup.OVERHEAD_PRESS
        MovementPattern.PULL_VERTICAL -> MainLiftGroup.PULL_UP
        MovementPattern.PULL_HORIZONTAL -> MainLiftGroup.ROW
        MovementPattern.LUNGE -> MainLiftGroup.LUNGE
        else -> MainLiftGroup.NONE
    }

    private fun MetadataSource.toAccessoryContributionGroup(
        movementPattern: MovementPattern,
        trainingRole: FatigueTrainingRole,
        badmintonRoles: Set<BadmintonTransferRole>
    ): AccessoryContributionGroup = when {
        BadmintonTransferRole.GRIP_FOREARM in badmintonRoles -> AccessoryContributionGroup.GRIP_FOREARM
        BadmintonTransferRole.SHOULDER_CARE in badmintonRoles || trainingRole == FatigueTrainingRole.PREHAB -> AccessoryContributionGroup.SHOULDER_CARE
        badmintonRoles.anyCourtRole() -> AccessoryContributionGroup.BADMINTON_SUPPORT
        movementPattern in setOf(MovementPattern.PUSH_HORIZONTAL, MovementPattern.PUSH_VERTICAL) -> AccessoryContributionGroup.UPPER_PUSH_ACCESSORY
        movementPattern in setOf(MovementPattern.PULL_HORIZONTAL, MovementPattern.PULL_VERTICAL) -> AccessoryContributionGroup.UPPER_PULL_ACCESSORY
        movementPattern == MovementPattern.HINGE -> AccessoryContributionGroup.POSTERIOR_CHAIN_ACCESSORY
        movementPattern in setOf(MovementPattern.SQUAT, MovementPattern.LUNGE) -> AccessoryContributionGroup.LOWER_ACCESSORY
        movementPattern == MovementPattern.ANTI_ROTATION -> AccessoryContributionGroup.CORE_STABILITY
        else -> AccessoryContributionGroup.NONE
    }

    private fun MetadataSource.toBadmintonTransferStrength(
        badmintonRoles: Set<BadmintonTransferRole>
    ): BadmintonTransferStrength = when {
        badmintonRoles.any { role ->
            role in setOf(
                BadmintonTransferRole.FOOTWORK,
                BadmintonTransferRole.REACTION,
                BadmintonTransferRole.DECELERATION,
                BadmintonTransferRole.LUNGE_REACH,
                BadmintonTransferRole.JUMP_LANDING
            )
        } -> BadmintonTransferStrength.DIRECT
        badmintonRoles.any { role ->
            role in setOf(
                BadmintonTransferRole.OVERHEAD_POWER,
                BadmintonTransferRole.ROTATION_POWER,
                BadmintonTransferRole.ANTI_ROTATION_STABILITY,
                BadmintonTransferRole.GRIP_FOREARM,
                BadmintonTransferRole.SHOULDER_CARE
            )
        } -> BadmintonTransferStrength.SUPPORTIVE
        BadmintonTransferRole.CONDITIONING in badmintonRoles -> BadmintonTransferStrength.GENERAL
        else -> BadmintonTransferStrength.NONE
    }

    private fun MetadataSource.toCourtMovementTypes(
        movementPattern: MovementPattern,
        badmintonRoles: Set<BadmintonTransferRole>
    ): Set<CourtMovementType> {
        val courtTypes = linkedSetOf<CourtMovementType>()
        if (BadmintonTransferRole.FOOTWORK in badmintonRoles || movementPattern == MovementPattern.FOOTWORK) {
            courtTypes += CourtMovementType.MULTI_DIRECTION
            courtTypes += CourtMovementType.FIRST_STEP
        }
        if (hasAny("SPLIT_STEP")) courtTypes += CourtMovementType.SPLIT_STEP
        if (BadmintonTransferRole.REACTION in badmintonRoles) courtTypes += CourtMovementType.REACTION_RANDOM
        if (BadmintonTransferRole.DECELERATION in badmintonRoles) courtTypes += CourtMovementType.DECELERATION
        if (BadmintonTransferRole.LUNGE_REACH in badmintonRoles) courtTypes += CourtMovementType.FRONT_LUNGE
        if (BadmintonTransferRole.JUMP_LANDING in badmintonRoles) courtTypes += CourtMovementType.JUMP_LANDING
        if (hasAny("LATERAL_DECELERATION_DIRECT")) courtTypes += CourtMovementType.LATERAL_MOVE
        if (hasAny("REAR_COURT")) courtTypes += CourtMovementType.REAR_COURT
        if (badmintonRoles.anyCourtRole()) courtTypes += CourtMovementType.RECOVERY_STEP
        return courtTypes.ifEmpty { setOf(CourtMovementType.NONE) }
    }

    private fun MetadataSource.toBadmintonSkillTargets(
        badmintonRoles: Set<BadmintonTransferRole>,
        courtMovementTypes: Set<CourtMovementType>
    ): Set<BadmintonSkillTarget> {
        val targets = linkedSetOf<BadmintonSkillTarget>()
        if (BadmintonTransferRole.FOOTWORK in badmintonRoles) targets += BadmintonSkillTarget.FOOTWORK_SPEED
        if (BadmintonTransferRole.REACTION in badmintonRoles || CourtMovementType.REACTION_RANDOM in courtMovementTypes) {
            targets += BadmintonSkillTarget.FIRST_STEP_REACTION
        }
        if (BadmintonTransferRole.DECELERATION in badmintonRoles || CourtMovementType.DECELERATION in courtMovementTypes) {
            targets += BadmintonSkillTarget.DECELERATION_CONTROL
        }
        if (BadmintonTransferRole.LUNGE_REACH in badmintonRoles) targets += BadmintonSkillTarget.LUNGE_REACH
        if (CourtMovementType.REAR_COURT in courtMovementTypes) targets += BadmintonSkillTarget.REAR_COURT_RECOVERY
        if (BadmintonTransferRole.JUMP_LANDING in badmintonRoles) targets += BadmintonSkillTarget.JUMP_LANDING_CONTROL
        if (BadmintonTransferRole.OVERHEAD_POWER in badmintonRoles) targets += BadmintonSkillTarget.OVERHEAD_POWER
        if (BadmintonTransferRole.ROTATION_POWER in badmintonRoles) targets += BadmintonSkillTarget.ROTATION_SEQUENCING
        if (BadmintonTransferRole.ANTI_ROTATION_STABILITY in badmintonRoles) targets += BadmintonSkillTarget.ANTI_ROTATION_STABILITY
        if (BadmintonTransferRole.GRIP_FOREARM in badmintonRoles) targets += BadmintonSkillTarget.GRIP_ENDURANCE
        if (BadmintonTransferRole.SHOULDER_CARE in badmintonRoles) targets += BadmintonSkillTarget.SHOULDER_DURABILITY
        if (BadmintonTransferRole.CONDITIONING in badmintonRoles) targets += BadmintonSkillTarget.CONDITIONING
        return targets.ifEmpty { setOf(BadmintonSkillTarget.NONE) }
    }

    private fun MetadataSource.toJointStressTags(
        movementPattern: MovementPattern
    ): Set<JointStressTag> {
        val tags = linkedSetOf<JointStressTag>()
        if (primaryMuscleTokens.any { token -> token.contains("SHOULDER") || token.contains("DELTOID") || token == "ROTATOR_CUFF" } ||
            movementPattern == MovementPattern.PUSH_VERTICAL
        ) {
            tags += JointStressTag.SHOULDER
        }
        if (primaryMuscleTokens.any { token -> token in setOf("BICEPS", "TRICEPS") }) tags += JointStressTag.ELBOW
        if (primaryMuscleTokens.any { token -> token in setOf("FOREARM", "GRIP") }) tags += JointStressTag.WRIST
        if (movementPattern == MovementPattern.HINGE || primaryMuscleTokens.any { token -> token == "ERECTOR_SPINAE" }) {
            tags += JointStressTag.LOW_BACK
        }
        if (movementPattern in setOf(MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.LUNGE) ||
            primaryMuscleTokens.any { token -> token.startsWith("GLUTE") || token == "HIP_ADDUCTOR" }
        ) {
            tags += JointStressTag.HIP
        }
        if (movementPattern in setOf(MovementPattern.SQUAT, MovementPattern.LUNGE, MovementPattern.FOOTWORK) ||
            "QUADRICEPS" in primaryMuscleTokens
        ) {
            tags += JointStressTag.KNEE
        }
        if (movementPattern in setOf(MovementPattern.JUMP, MovementPattern.HOP, MovementPattern.BOUND, MovementPattern.FOOTWORK) ||
            "CALF" in primaryMuscleTokens
        ) {
            tags += JointStressTag.ANKLE_ACHILLES
        }
        return tags
    }

    private fun MetadataSource.toStabilityDemandLevel(
        movementPattern: MovementPattern,
        laterality: FatigueLaterality,
        badmintonRoles: Set<BadmintonTransferRole>
    ): StabilityDemandLevel = when {
        movementPattern in setOf(MovementPattern.ANTI_ROTATION, MovementPattern.BOUND, MovementPattern.FOOTWORK) ||
            laterality in setOf(FatigueLaterality.UNILATERAL, FatigueLaterality.ASYMMETRIC, FatigueLaterality.ALTERNATING) ||
            badmintonRoles.anyCourtRole() -> StabilityDemandLevel.HIGH
        movementPattern in setOf(MovementPattern.LUNGE, MovementPattern.CARRY, MovementPattern.ROTATION, MovementPattern.PREHAB) -> StabilityDemandLevel.MODERATE
        movementPattern in setOf(MovementPattern.MOBILITY, MovementPattern.ISOLATION) -> StabilityDemandLevel.LOW
        else -> StabilityDemandLevel.LOW
    }

    private fun MetadataSource.toMobilityDemandLevel(
        movementPattern: MovementPattern
    ): MobilityDemandLevel = when {
        movementPattern == MovementPattern.MOBILITY -> MobilityDemandLevel.HIGH
        movementPattern in setOf(MovementPattern.LUNGE, MovementPattern.ROTATION, MovementPattern.PUSH_VERTICAL) -> MobilityDemandLevel.MODERATE
        movementPattern == MovementPattern.PREHAB -> MobilityDemandLevel.LOW
        else -> MobilityDemandLevel.LOW
    }

    private fun MetadataSource.toBalanceContributionTags(
        movementPattern: MovementPattern,
        laterality: FatigueLaterality
    ): Set<BalanceContributionTag> {
        val tags = linkedSetOf<BalanceContributionTag>()
        when (movementPattern) {
            MovementPattern.PUSH_HORIZONTAL,
            MovementPattern.PUSH_VERTICAL -> tags += BalanceContributionTag.UPPER_PUSH
            MovementPattern.PULL_HORIZONTAL,
            MovementPattern.PULL_VERTICAL -> tags += BalanceContributionTag.UPPER_PULL
            MovementPattern.SQUAT,
            MovementPattern.LUNGE -> {
                tags += BalanceContributionTag.LOWER_PUSH
                tags += BalanceContributionTag.SQUAT_PATTERN
                tags += BalanceContributionTag.ANTERIOR_CHAIN
            }
            MovementPattern.HINGE -> {
                tags += BalanceContributionTag.LOWER_PULL
                tags += BalanceContributionTag.HINGE
                tags += BalanceContributionTag.POSTERIOR_CHAIN
            }
            MovementPattern.ROTATION -> tags += BalanceContributionTag.ROTATION
            MovementPattern.ANTI_ROTATION -> tags += BalanceContributionTag.ANTI_ROTATION
            else -> Unit
        }
        if (laterality in setOf(FatigueLaterality.UNILATERAL, FatigueLaterality.ALTERNATING, FatigueLaterality.ASYMMETRIC)) {
            if (movementPattern in setOf(MovementPattern.PUSH_HORIZONTAL, MovementPattern.PUSH_VERTICAL, MovementPattern.PULL_HORIZONTAL, MovementPattern.PULL_VERTICAL)) {
                tags += BalanceContributionTag.UNILATERAL_UPPER
            } else {
                tags += BalanceContributionTag.UNILATERAL_LOWER
            }
        }
        if (primaryMuscleTokens.any { token -> token.contains("SHOULDER") || token == "ROTATOR_CUFF" }) {
            tags += BalanceContributionTag.SHOULDER_CARE
        }
        if (tokens.any { token -> token.contains("SCAPULAR") }) tags += BalanceContributionTag.SCAPULAR_STABILITY
        if (primaryMuscleTokens.any { token -> token in setOf("FOREARM", "GRIP") }) tags += BalanceContributionTag.GRIP_FOREARM
        if ("CALF" in primaryMuscleTokens) tags += BalanceContributionTag.CALF_ACHILLES
        if (primaryMuscleTokens.any { token -> token.startsWith("GLUTE") }) tags += BalanceContributionTag.HIP_STABILITY
        if ("QUADRICEPS" in primaryMuscleTokens) tags += BalanceContributionTag.KNEE_CONTROL
        if (movementPattern in setOf(MovementPattern.JUMP, MovementPattern.HOP, MovementPattern.BOUND, MovementPattern.FOOTWORK)) {
            tags += BalanceContributionTag.ANKLE_STIFFNESS
        }
        return tags
    }

    private fun MetadataSource.toAnalysisEligibility(
        trainingRole: FatigueTrainingRole,
        progressMetricType: ProgressMetricType,
        badmintonTransferStrength: BadmintonTransferStrength,
        balanceContributionTags: Set<BalanceContributionTag>
    ): Set<AnalysisEligibility> {
        if (trainingRole == FatigueTrainingRole.RECOVERY) {
            return setOf(AnalysisEligibility.RECOVERY_ONLY)
        }
        val eligibility = linkedSetOf<AnalysisEligibility>()
        if (trainingRole != FatigueTrainingRole.TEST && trainingRole != FatigueTrainingRole.PREHAB) {
            eligibility += AnalysisEligibility.FATIGUE
        }
        if (progressMetricType == ProgressMetricType.ESTIMATED_1RM || progressMetricType == ProgressMetricType.REPS_AT_LOAD) {
            eligibility += AnalysisEligibility.STRENGTH_PROGRESS
        }
        if (progressMetricType == ProgressMetricType.VOLUME_LOAD) {
            eligibility += AnalysisEligibility.HYPERTROPHY_VOLUME
        }
        if (badmintonTransferStrength != BadmintonTransferStrength.NONE) {
            eligibility += AnalysisEligibility.BADMINTON_TRANSFER
        }
        if (balanceContributionTags.isNotEmpty()) {
            eligibility += AnalysisEligibility.BALANCE
        }
        if (trainingRole == FatigueTrainingRole.PREHAB) {
            eligibility += AnalysisEligibility.RECOVERY_ONLY
            eligibility += AnalysisEligibility.BALANCE
        }
        if (trainingRole == FatigueTrainingRole.TEST || progressMetricType == ProgressMetricType.MAX_REPS_TEST) {
            eligibility.remove(AnalysisEligibility.FATIGUE)
            eligibility.remove(AnalysisEligibility.STRENGTH_PROGRESS)
            eligibility.remove(AnalysisEligibility.HYPERTROPHY_VOLUME)
            eligibility += AnalysisEligibility.TEST_ONLY
        }
        return eligibility.ifEmpty { setOf(AnalysisEligibility.EXCLUDED_FROM_ANALYSIS) }
    }

    private fun Set<BadmintonTransferRole>.anyCourtRole(): Boolean =
        any { role ->
            role in setOf(
                BadmintonTransferRole.FOOTWORK,
                BadmintonTransferRole.REACTION,
                BadmintonTransferRole.DECELERATION,
                BadmintonTransferRole.LUNGE_REACH,
                BadmintonTransferRole.JUMP_LANDING,
                BadmintonTransferRole.CONDITIONING
            )
        }

    private fun <E : Enum<E>> Set<E>.toTokenString(): String =
        joinToString(",") { value -> value.name }

    internal fun String.splitTokens(): Set<String> =
        split(',', '|', '/', ';')
            .map { value -> value.trim().uppercase() }
            .filter { value -> value.isNotEmpty() && value != "NONE" }
            .toSet()
}

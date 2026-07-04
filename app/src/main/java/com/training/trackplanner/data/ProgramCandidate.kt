package com.training.trackplanner.data

import java.util.Locale
import kotlin.math.max

internal data class ProgramCandidate(
    val exercise: Exercise,
    val metadata: RuntimeExerciseMetadata?,
    val canonical: Boolean,
    val slotCapabilities: SlotCapabilityProfile = SlotCapabilityResolver.DEFAULT.resolve(exercise, metadata)
) {
    private val identityTokens: Set<String> = buildSet {
        metadata?.let { value ->
            add(value.movementFamily)
            add(value.movementSubtype)
            add(value.programSlot)
            add(value.redundancyGroup)
            add(value.strengthProgressionGroup)
        }
        if (!canonical) {
            add(exercise.movementPattern)
            add(exercise.movementCategory)
            add(exercise.trainingRole)
            add(exercise.strengthProgressionGroup)
        }
    }.flatMap(::splitTokens).toSet()

    private val structuredTokens: Set<String> = buildSet {
        metadata?.let { value ->
            add(value.activityKind)
            add(value.planningEligibility)
            add(value.movementFamily)
            add(value.movementSubtype)
            add(value.programSlot)
            add(value.redundancyGroup)
            add(value.progressMetricType)
            add(value.strengthProgressionGroup)
            add(value.primaryStressProfile)
            add(value.stressMagnitudeHint)
            add(value.badmintonTransferLevel)
            add(value.appCueProfile)
            addAll(value.analysisEligibility.values)
            addAll(value.secondaryStressTags.values)
            addAll(value.jointImpactStressTags.values)
            addAll(value.badmintonTransferType.values)
            addAll(value.badmintonPhysicalQualities.values)
            addAll(value.sportContextTags.values)
        }
        if (!canonical) {
            add(exercise.activityKind)
            add(exercise.planningEligibility)
            add(exercise.movementPattern)
            add(exercise.movementCategory)
            add(exercise.trainingRole)
            add(exercise.badmintonTransferStrength)
            add(exercise.badmintonTransferRoles)
            add(exercise.progressMetricType)
            add(exercise.fatigueCategories)
        }
    }.flatMap(::splitTokens).toSet()

    val isRecovery: Boolean = identityHas("RECOVERY", "PREHAB", "MOBILITY", "CONTROL")
    val isCore: Boolean = identityHas("CORE", "ANTI_ROTATION", "ROTATION_CONTROL", "TRUNK")
    val isScapularStabilityExposure: Boolean = identityHas(
        "SCAP",
        "ROTATOR_CUFF",
        "REAR_DELT",
        "HORIZONTAL_PULL",
        "VERTICAL_PULL"
    ) || has("SCAPULAR_CONTROL", "SHOULDER_DURABILITY", "ROTATOR_CUFF_CONTROL")
    val isRehabLikeActivation: Boolean = rehabLikeActivationScore() >= 4
    val isIsolation: Boolean = identityHas("ISOLATION", "BICEPS", "TRICEPS", "CURL", "EXTENSION_ACCESSORY")
    val highStress: Boolean = metadata?.stressMagnitudeHint in setOf("HIGH", "VERY_HIGH") ||
        has("HIGH_AXIAL", "HIGH_NEURAL", "HIGH_IMPACT")
    val isHeavyLower: Boolean =
        identityHas("HEAVY_HINGE", "SQUAT_HEAVY_AXIAL", "MAIN_HINGE_STRENGTH", "MAIN_LOWER_STRENGTH") ||
            (identityHas("SQUAT", "HINGE", "DEADLIFT") && highStress)
    val isHighImpact: Boolean = has("PLYOMETRIC", "LANDING", "HOP", "JUMP", "ELASTIC_SSC", "JOINT_IMPACT")
    val isHighIntensityCod: Boolean = has("CHANGE_OF_DIRECTION", "DECELERATION", "COURT_FOOTWORK", "REACTION_DRILL")
    private val isCodSpecific: Boolean = metadata?.appCueProfile == "RANDOM_BEEP_CUE" ||
        has("FOOTWORK", "SPLIT_STEP", "CHANGE_OF_DIRECTION", "DECELERATION", "REACTION", "COURT_MOVEMENT")
    private val isReactivePowerSpecific: Boolean = isHighImpact ||
        has(
            "ELASTIC_SSC", "REACTIVE_POWER", "EXPLOSIVE_POWER", "NEURAL_SPEED", "FIRST_STEP",
            "BALLISTIC", "SLAM", "THROW", "TOSS", "PUSH_PRESS"
        )
    val isAnchor: Boolean = identityHas("COMPOUND", "HEAVY_HINGE", "SQUAT_HEAVY", "VERTICAL_PULL", "HORIZONTAL_PULL")
    val isDirectSportSession: Boolean = metadata?.activityKind == "SPORT_SESSION" ||
        identityHas("BADMINTON_SESSION_SPORT_RECORDS", "OTHER_SPORT_SESSION_RECORDS") ||
        (metadata?.progressBehavior == ProgressMetricRuntimeBehavior.SESSION_DURATION &&
            has("BADMINTON_MATCH", "BADMINTON_LESSON", "DIRECT_PLAY_SESSION", "OTHER_SPORT_SESSION"))
    val isSportLike: Boolean = isDirectSportSession
    val isTimed: Boolean = metadata?.progressBehavior in setOf(
        ProgressMetricRuntimeBehavior.REPS_OR_TIME,
        ProgressMetricRuntimeBehavior.DISTANCE_OR_TIME,
        ProgressMetricRuntimeBehavior.SESSION_DURATION,
        ProgressMetricRuntimeBehavior.QUALITY_BASED
    )
    val badmintonFit: Double = when (metadata?.badmintonTransferLevel ?: exercise.badmintonTransferStrength) {
        "DIRECT" -> 1.0
        "SUPPORTIVE", BadmintonTransferStrength.SUPPORTIVE.name -> 0.75
        "GENERAL" -> 0.35
        else -> if (metadata?.appCueProfile == "RANDOM_BEEP_CUE") 0.85 else 0.1
    }
    val strengthFit: Double = when {
        has("STRENGTH_PROGRESS", "STRENGTH_VOLUME", "HYPERTROPHY_VOLUME", "ESTIMATED_1RM", "LOAD_REPS") -> 1.0
        isCore || isRecovery -> 0.5
        else -> 0.25
    }
    val metadataConfidenceFit: Double = if (canonical) 0.8 else 0.0
    private val isLowerPattern: Boolean = slotCapabilities.hasAny(
        ProgramSlotId.LOWER_SQUAT_PATTERN,
        ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN,
        ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL,
        ProgramSlotId.CALF_ANKLE_CAPACITY
    ) || identityHas(
        "LOWER", "SQUAT", "HINGE", "DEADLIFT", "LUNGE", "CALF", "ANKLE", "HIP", "KNEE", "HAMSTRING", "GLUTE"
    )
    private val isUpperPattern: Boolean = slotCapabilities.hasAny(
        ProgramSlotId.UPPER_PULL_ANCHOR,
        ProgramSlotId.UPPER_PUSH_SUPPORT,
        ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
        ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT
    ) || identityHas(
        "UPPER", "PULL", "ROW", "SCAP", "SHOULDER", "PUSH", "PRESS", "CHEST", "ELBOW", "BICEPS", "TRICEPS", "GRIP", "FOREARM"
    )

    fun isProgramSelectable(): Boolean = exercise.isActive && !isDirectSportSession && when {
        metadata != null -> metadata.planningEligibility == "PROGRAM_SELECTABLE"
        else -> exercise.isProgramSelectableExercise()
    }

    fun matchesEquipment(selected: Set<String>): Boolean {
        if (selected.isEmpty()) return true
        val equipment = splitTokens(exercise.equipment)
        return equipment.isEmpty() || "NONE" in equipment || equipment.any(selected::contains)
    }

    fun allowedForSlot(slot: ProgramTrainingSlot): Boolean = when (slot) {
        ProgramTrainingSlot.LOWER_STRENGTH,
        ProgramTrainingSlot.LOWER_STRENGTH_HEAVY,
        ProgramTrainingSlot.LOWER_TRANSFER_FULL -> isLowerPattern || isCore || isRecovery
        ProgramTrainingSlot.UPPER_STRENGTH,
        ProgramTrainingSlot.UPPER_STRENGTH_SCAP,
        ProgramTrainingSlot.UPPER_SCAP_CORE_FULL -> isUpperPattern || isCore || isRecovery
        ProgramTrainingSlot.BADMINTON_TRANSFER -> badmintonFit >= 0.35 || isCore || isRecovery
        ProgramTrainingSlot.BADMINTON_COD,
        ProgramTrainingSlot.BADMINTON_COD_DECEL -> isCodSpecific || isCore || isRecovery
        ProgramTrainingSlot.POWER_REACTIVE,
        ProgramTrainingSlot.POWER_REACTIVE_LIGHT -> isReactivePowerSpecific || isCodSpecific || isCore || isRecovery
        ProgramTrainingSlot.RECOVERY_PREHAB,
        ProgramTrainingSlot.RECOVERY_WEAKPOINT,
        ProgramTrainingSlot.MICRO_RECOVERY -> isRecovery || isCore || (!highStress && !isAnchor && !isHighImpact && !isHighIntensityCod)
        else -> true
    }

    fun allowedForRole(slot: ProgramTrainingSlot, role: ProgramExerciseRole): Boolean {
        if (role == ProgramExerciseRole.ANCHOR && isRehabLikeActivation) return false
        return when (role) {
            ProgramExerciseRole.ANCHOR -> slotCapabilities.primary.any(ANCHOR_CAPABILITY_SLOTS::contains)
            ProgramExerciseRole.CORE -> hasStrongCapability(CORE_CAPABILITY_SLOTS) &&
                !isHighImpact && !isHighIntensityCod && !isReactivePowerSpecific
            ProgramExerciseRole.PREHAB -> isRecovery && hasStrongCapability(PREHAB_CAPABILITY_SLOTS) &&
                !isHighImpact && !isHighIntensityCod && !isReactivePowerSpecific
            ProgramExerciseRole.TRANSFER -> when (slot) {
                ProgramTrainingSlot.BADMINTON_COD,
                ProgramTrainingSlot.BADMINTON_COD_DECEL -> isCodSpecific &&
                    hasStrongCapability(TRANSFER_CAPABILITY_SLOTS)
                ProgramTrainingSlot.POWER_REACTIVE,
                ProgramTrainingSlot.POWER_REACTIVE_LIGHT ->
                    (isReactivePowerSpecific || isCodSpecific) && hasStrongCapability(TRANSFER_CAPABILITY_SLOTS)
                ProgramTrainingSlot.BADMINTON_TRANSFER -> badmintonFit >= 0.90 &&
                    hasStrongCapability(TRANSFER_CAPABILITY_SLOTS)
                else -> hasStrongCapability(TRANSFER_CAPABILITY_SLOTS)
            }
            ProgramExerciseRole.SUPPORT -> hasStrongCapability(SUPPORT_CAPABILITY_SLOTS) &&
                !isHighImpact && !isHighIntensityCod && !isReactivePowerSpecific
            ProgramExerciseRole.ACCESSORY -> hasResolvableCapability(ACCESSORY_CAPABILITY_SLOTS)
        }
    }

    fun resolvedSlotForRole(role: ProgramExerciseRole): ProgramSlotId? {
        val eligible = capabilitySlotsForRole(role)
        return slotCapabilities.primary.firstOrNull(eligible::contains)
            ?: slotCapabilities.secondary.firstOrNull(eligible::contains)
            ?: slotCapabilities.weakMatches
                .takeIf { hasAcceptableWeakCapability }
                ?.firstOrNull(eligible::contains)
    }

    fun allowedForTemplateSlot(
        plannedSlot: ProgramTrainingSlot,
        templateSlot: TemplateExerciseSlot,
        coveragePolicy: CoverageAccountingPolicy
    ): Boolean {
        val target = templateSlot.targetSlot
        if (target == null) {
            return allowedForSlot(plannedSlot) && allowedForRole(plannedSlot, templateSlot.role)
        }
        if (templateSlot.role == ProgramExerciseRole.ANCHOR && isRehabLikeActivation) return false
        if (templateSlot.role == ProgramExerciseRole.CORE && isReactivePowerSpecific) return false
        if (templateSlot.role == ProgramExerciseRole.PREHAB &&
            (isReactivePowerSpecific || isHighImpact || isHighIntensityCod)
        ) {
            return false
        }
        val credit = coveragePolicy.credit(slotCapabilities, target)
        return if (templateSlot.required) {
            credit.value >= CoverageCredit.PARTIAL.value
        } else {
            credit != CoverageCredit.NONE
        }
    }

    fun roleFit(role: ProgramExerciseRole): Double = when (role) {
        ProgramExerciseRole.ANCHOR -> if (isAnchor) 1.0 else if (strengthFit >= 0.8) 0.55 else 0.0
        ProgramExerciseRole.TRANSFER -> badmintonFit
        ProgramExerciseRole.SUPPORT -> when {
            isHighImpact || isHighIntensityCod -> 0.1
            !isIsolation && !isRecovery -> max(strengthFit, badmintonFit * 0.7)
            else -> 0.25
        }
        ProgramExerciseRole.CORE -> if (isCore) 1.0 else 0.0
        ProgramExerciseRole.PREHAB -> if (isRecovery) 1.0 else 0.1
        ProgramExerciseRole.ACCESSORY -> if (isIsolation || (!isAnchor && !isSportLike)) 0.9 else 0.2
    }

    fun slotFit(slot: ProgramTrainingSlot): Double = when (slot) {
        ProgramTrainingSlot.LOWER_STRENGTH,
        ProgramTrainingSlot.LOWER_STRENGTH_HEAVY,
        ProgramTrainingSlot.LOWER_TRANSFER_FULL -> if (isLowerPattern) 1.0 else 0.2
        ProgramTrainingSlot.UPPER_STRENGTH,
        ProgramTrainingSlot.UPPER_STRENGTH_SCAP,
        ProgramTrainingSlot.UPPER_SCAP_CORE_FULL -> if (isUpperPattern) 1.0 else 0.2
        ProgramTrainingSlot.BADMINTON_TRANSFER -> badmintonFit
        ProgramTrainingSlot.BADMINTON_COD,
        ProgramTrainingSlot.BADMINTON_COD_DECEL -> if (isCodSpecific) 1.0 else 0.1
        ProgramTrainingSlot.POWER_REACTIVE,
        ProgramTrainingSlot.POWER_REACTIVE_LIGHT -> if (isReactivePowerSpecific || isCodSpecific) 1.0 else 0.1
        ProgramTrainingSlot.RECOVERY_PREHAB,
        ProgramTrainingSlot.RECOVERY_WEAKPOINT,
        ProgramTrainingSlot.MICRO_RECOVERY -> if (isRecovery || isCore) 1.0 else 0.2
        else -> max(strengthFit, badmintonFit)
    }

    fun templateSpecificityFit(targetSlot: ProgramSlotId?): Double = when (targetSlot) {
        ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT -> when {
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY in slotCapabilities.secondary -> 0.90
            badmintonFit >= 0.75 -> 0.35
            else -> 0.0
        }
        else -> 0.0
    }

    private fun has(vararg needles: String): Boolean = needles.any { needle ->
        structuredTokens.any { token -> token.matchesMetadataNeedle(needle) }
    }

    private fun identityHas(vararg needles: String): Boolean = needles.any { needle ->
        identityTokens.any { token -> token.matchesMetadataNeedle(needle) }
    }

    private fun hasStrongCapability(eligible: Set<ProgramSlotId>): Boolean =
        slotCapabilities.primary.any(eligible::contains) ||
            slotCapabilities.secondary.any(eligible::contains)

    private fun hasResolvableCapability(eligible: Set<ProgramSlotId>): Boolean =
        hasStrongCapability(eligible) ||
            (hasAcceptableWeakCapability && slotCapabilities.weakMatches.any(eligible::contains))

    private val hasAcceptableWeakCapability: Boolean
        get() = slotCapabilities.source in setOf(
            SlotCapabilitySource.RUNTIME_METADATA,
            SlotCapabilitySource.LEGACY_METADATA
        ) && slotCapabilities.confidence != SlotCapabilityConfidence.NONE

    private fun capabilitySlotsForRole(role: ProgramExerciseRole): Set<ProgramSlotId> = when (role) {
        ProgramExerciseRole.ANCHOR -> ANCHOR_CAPABILITY_SLOTS
        ProgramExerciseRole.TRANSFER -> TRANSFER_CAPABILITY_SLOTS
        ProgramExerciseRole.SUPPORT -> SUPPORT_CAPABILITY_SLOTS
        ProgramExerciseRole.CORE -> CORE_CAPABILITY_SLOTS
        ProgramExerciseRole.PREHAB -> PREHAB_CAPABILITY_SLOTS
        ProgramExerciseRole.ACCESSORY -> ACCESSORY_CAPABILITY_SLOTS
    }

    private fun String.matchesMetadataNeedle(needle: String): Boolean =
        if (needle.length <= 3) {
            this == needle ||
                startsWith("${needle}_") ||
                endsWith("_${needle}") ||
                contains("_${needle}_")
        } else {
            contains(needle)
        }

    private fun rehabLikeActivationScore(): Int {
        val metadataValue = metadata ?: return 0
        var score = 0
        if (metadataValue.primaryStressProfile.contains("LOW_LOAD_PREHAB_CONTROL")) score += 2
        if (has("LOW_LOAD_CONTROL", "LOCAL_STABILIZER_LOAD", "MOTOR_CONTROL")) score += 1
        if (identityHas("RECOVERY_PREHAB", "ROTATOR_CUFF_CARE", "MOBILITY")) score += 2
        if (metadataValue.progressBehavior == ProgressMetricRuntimeBehavior.QUALITY_BASED) score += 1
        if (metadataValue.strengthProgressionGroup in setOf("", "NONE", "NOT_APPLICABLE")) score += 1
        if (metadataValue.stressMagnitudeHint == "LOW" && metadataValue.recoveryDurationClass == "SHORT") score += 1

        val equipmentTokens = splitTokens(exercise.equipment)
        if (exercise.defaultRestSeconds <= 45 && equipmentTokens.any {
                it in setOf("BODYWEIGHT", "BAND", "WALL", "맨몸", "밴드", "벽")
            }
        ) {
            score += 1
        }
        if (metadataValue.movementSubtype in LOW_LOAD_ACTIVATION_SUBTYPES) score += 4

        val hasProgression = metadataValue.strengthProgressionGroup !in setOf("", "NONE", "NOT_APPLICABLE")
        if (hasProgression) score -= 2
        if (identityHas("MAIN_", "STRENGTH", "COMPOUND", "HEAVY")) score -= 3
        if (identityHas("SCAPULAR_RETRACTION_EXTERNAL_ROTATION", "REAR_DELT")) score -= 2
        if (identityHas("ACCESSORY") && equipmentTokens.any {
                it in setOf("CABLE", "DUMBBELL", "BARBELL", "MACHINE", "케이블", "덤벨", "바벨", "머신")
            }
        ) {
            score -= 2
        }
        return score
    }

    private companion object {
        val ANCHOR_CAPABILITY_SLOTS = setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN,
            ProgramSlotId.UPPER_PULL_ANCHOR,
            ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL
        )
        val CORE_CAPABILITY_SLOTS = setOf(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY)
        val PREHAB_CAPABILITY_SLOTS = setOf(ProgramSlotId.RECOVERY_PREHAB_LIGHT)
        val SUPPORT_CAPABILITY_SLOTS = setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN,
            ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL,
            ProgramSlotId.CALF_ANKLE_CAPACITY,
            ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT,
            ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT,
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
            ProgramSlotId.UPPER_PUSH_SUPPORT
        )
        val TRANSFER_CAPABILITY_SLOTS = setOf(
            ProgramSlotId.BADMINTON_FOOTWORK_REACTION,
            ProgramSlotId.BADMINTON_DECEL_COD,
            ProgramSlotId.POWER_REACTIVE_LOW_VOLUME,
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT
        )
        val ACCESSORY_CAPABILITY_SLOTS = SUPPORT_CAPABILITY_SLOTS + setOf(
            ProgramSlotId.ACCESSORY_ROTATION,
            ProgramSlotId.RECOVERY_PREHAB_LIGHT,
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY
        )
        val LOW_LOAD_ACTIVATION_SUBTYPES = setOf(
            "WALL_SLIDE",
            "SCAPULAR_PUSH_UP",
            "BAND_EXTERNAL_ROTATION",
            "BIRD_DOG",
            "DEAD_BUG",
            "SUPERMAN",
            "SCAPULAR_PULL_UP"
        )
    }
}

private fun splitTokens(value: String): Set<String> = value
    .split('|', ',', '/', ';', ' ')
    .map { it.trim().uppercase(Locale.US) }
    .filter(String::isNotBlank)
    .toSet()

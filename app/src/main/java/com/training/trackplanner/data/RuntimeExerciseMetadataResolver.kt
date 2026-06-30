package com.training.trackplanner.data

import java.util.UUID

object UserExerciseStableKeyGenerator {
    const val PREFIX = "user_ex_"

    fun generate(uuid: UUID = UUID.randomUUID()): String = PREFIX + uuid.toString()

    fun isUserExerciseKey(value: String): Boolean = value.startsWith(PREFIX)
}

internal object ExerciseStableKeyPolicy {
    fun preserveOnEdit(existing: Exercise, edited: Exercise, repairedKey: String): Exercise =
        edited.copy(
            id = existing.id,
            stableKey = existing.stableKey.ifBlank { repairedKey },
            isCustom = existing.isCustom,
            isActive = existing.isActive,
            archivedAt = existing.archivedAt
        )

    fun mergeSeed(existing: Exercise, seed: Exercise): Exercise? {
        if (existing.isCustom) return null
        return seed.copy(
            id = existing.id,
            stableKey = existing.stableKey,
            imageAssetName = seed.imageAssetName.ifBlank { existing.imageAssetName },
            isActive = existing.isActive,
            archivedAt = existing.archivedAt,
            isCustom = existing.isCustom,
            needsReview = existing.needsReview || seed.needsReview
        )
    }
}

internal object ExerciseSeedMetadataPolicy {
    fun applyBuiltInSeedMetadata(
        exercise: Exercise,
        seedByStableKey: Map<String, Exercise>
    ): Exercise {
        val seed = seedByStableKey[exercise.stableKey.seedLookupKey()] ?: return exercise
        return seed.copy(
            id = exercise.id,
            stableKey = seed.stableKey,
            imageAssetName = seed.imageAssetName.ifBlank { exercise.imageAssetName },
            isActive = exercise.isActive,
            archivedAt = exercise.archivedAt,
            isCustom = false,
            needsReview = exercise.needsReview || seed.needsReview
        )
    }

    fun seedMap(exercises: List<Exercise>): Map<String, Exercise> =
        exercises.associateBy { exercise -> exercise.stableKey.seedLookupKey() }

    fun isBuiltInStableKey(stableKey: String, seedByStableKey: Map<String, Exercise>): Boolean =
        stableKey.seedLookupKey() in seedByStableKey
}

class RuntimeExerciseMetadataResolver(
    private val canonicalCatalog: RuntimeExerciseMetadataCatalog,
    persistedRows: Collection<RuntimeExerciseMetadata>
) {
    private val persistedByStableKey = persistedRows.associateBy { it.stableKey }

    fun resolve(exercise: Exercise): RuntimeExerciseMetadata {
        val persisted = persistedByStableKey[exercise.stableKey]
        val canonical = canonicalCatalog.resolve(exercise)
        val exerciseDerived = RuntimeExerciseMetadataDefaults.forExercise(exercise)
        val repairSource = canonical ?: exerciseDerived
        return when {
            persisted != null && canonical != null -> persisted.copy(
                progressMetricType = persisted.progressMetricType.ifSet() ?: repairSource.progressMetricType,
                strengthProgressionGroup = persisted.strengthProgressionGroup.ifSet()
                    ?: repairSource.strengthProgressionGroup,
                analysisEligibility = persisted.analysisEligibility.takeIf { field -> field.values.isNotEmpty() }
                    ?: repairSource.analysisEligibility,
                appCueProfile = canonical.appCueProfile
            )
            persisted != null -> persisted.copy(
                progressMetricType = persisted.progressMetricType.ifSet() ?: repairSource.progressMetricType,
                strengthProgressionGroup = persisted.strengthProgressionGroup.ifSet()
                    ?: repairSource.strengthProgressionGroup,
                analysisEligibility = persisted.analysisEligibility.takeIf { field -> field.values.isNotEmpty() }
                    ?: repairSource.analysisEligibility
            )
            canonical != null -> canonical
            else -> exerciseDerived
        }
    }

    fun catalog(exercises: Collection<Exercise>): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataCatalog.of(exercises.map(::resolve))
}

private fun String.ifSet(): String? =
    takeUnless { value ->
        value.isBlank() ||
            value.equals("NONE", ignoreCase = true) ||
            value.equals("NOT_APPLICABLE", ignoreCase = true)
    }

private fun String.seedLookupKey(): String = trim().lowercase()

object RuntimeExerciseMetadataDefaults {
    fun forExercise(exercise: Exercise): RuntimeExerciseMetadata {
        val progressMetricType = exercise.progressMetricType.ifSet()
            ?: if (exercise.estimated1RmEligible) "ESTIMATED_1RM"
            else if (exercise.volumeLoadEligible) "VOLUME_LOAD"
            else "NOT_APPLICABLE"
        val analysisTokens = exercise.analysisEligibility.splitRuntimeTokens().toMutableSet()
        if (exercise.estimated1RmEligible) analysisTokens += "STRENGTH_PROGRESS"
        if (exercise.volumeLoadEligible) analysisTokens += "HYPERTROPHY_VOLUME"
        val analysisEligibility = MetadataTokenField.parse(
            analysisTokens.takeIf { it.isNotEmpty() }?.joinToString("|") ?: "NONE"
        )
        val strengthProgressionGroup = exercise.strengthProgressionGroup.ifSet()
            ?: exercise.mainLiftGroup.ifSet()
            ?: exercise.familyId.ifSet()
            ?: "NOT_APPLICABLE"

        return RuntimeExerciseMetadata(
            stableKey = exercise.stableKey,
            exerciseName = exercise.name,
            activityKind = exercise.activityKind.ifSet() ?: "EXERCISE",
            planningEligibility = exercise.planningEligibility.ifSet() ?: "PROGRAM_SELECTABLE",
            movementFamily = exercise.movementPattern.ifSet() ?: "NOT_APPLICABLE",
            movementSubtype = exercise.movementCategory.ifSet() ?: "NOT_APPLICABLE",
            programSlot = "NOT_APPLICABLE",
            redundancyGroup = exercise.familyId.ifSet() ?: "NOT_APPLICABLE",
            progressMetricType = progressMetricType,
            strengthProgressionGroup = strengthProgressionGroup,
            analysisEligibility = analysisEligibility,
            primaryStressProfile = exercise.loadProfile.ifSet()
                ?: exercise.fatigueCategories.splitRuntimeTokens().firstOrNull()
                ?: "LOW_LOAD_PREHAB_CONTROL_STRESS",
            secondaryStressTags = MetadataTokenField.parse(exercise.fatigueCategories),
            tendonStressTags = MetadataTokenField.parse(exercise.jointStressTags),
            ligamentJointStabilityStressTags = MetadataTokenField.parse(exercise.jointStressTags),
            jointImpactStressTags = MetadataTokenField.parse(exercise.jointStressTags),
            cognitiveStressTags = MetadataTokenField.parse("NONE"),
            sportContextTags = MetadataTokenField.parse(exercise.courtMovementTypes),
            recoveryDecayProfile = exercise.recoveryDecayProfile.ifSet() ?: "SHORT",
            stressMagnitudeHint = exercise.loadProfile.ifSet() ?: "LOW",
            badmintonTransferLevel = exercise.badmintonTransferStrength.ifSet() ?: "NONE",
            badmintonTransferType = MetadataTokenField.parse(exercise.badmintonTransferRoles),
            badmintonSkillTargets = MetadataTokenField.parse(exercise.badmintonSkillTargets),
            badmintonPhysicalQualities = MetadataTokenField.parse(exercise.courtMovementTypes),
            transferConfidence = "NONE",
            sourceConfidenceLevel = exercise.metadataConfidence.ifSet() ?: "HEURISTIC_ACCEPTED",
            finalSourceStatus = if (exercise.metadataConfidence.ifSet() != null) {
                "SOURCE_ACCEPTED"
            } else {
                "SOURCE_ACCEPTED_WITH_LIMITATION"
            },
            neuromuscularStressLevel = levelFromWeight(exercise.neuralHeavyWeight + exercise.neuralSpeedWeight),
            systemicMuscularStressLevel = levelFromWeight(exercise.systemicLoadWeight),
            localMuscularStressLevel = levelFromWeight(exercise.localLoadWeight),
            jointTendonImpactStressLevel = levelFromWeight(exercise.decelerationWeight + exercise.elasticSscWeight),
            movementFocusDemandLevel = exercise.stabilityDemandLevel.ifSet() ?: "LOW",
            recoveryDurationClass = exercise.recoveryDecayProfile.ifSet() ?: "SHORT",
            safeForSeedMutation = false
        )
    }

    fun forIdentity(stableKey: String, exerciseName: String): RuntimeExerciseMetadata =
        RuntimeExerciseMetadata(
            stableKey = stableKey,
            exerciseName = exerciseName,
            activityKind = "EXERCISE",
            planningEligibility = "PROGRAM_SELECTABLE",
            movementFamily = "NOT_APPLICABLE",
            movementSubtype = "NOT_APPLICABLE",
            programSlot = "NOT_APPLICABLE",
            redundancyGroup = "NOT_APPLICABLE",
            progressMetricType = "NOT_APPLICABLE",
            strengthProgressionGroup = "NOT_APPLICABLE",
            analysisEligibility = MetadataTokenField.parse("NONE"),
            primaryStressProfile = "LOW_LOAD_PREHAB_CONTROL_STRESS",
            secondaryStressTags = MetadataTokenField.parse("NONE"),
            tendonStressTags = MetadataTokenField.parse("NONE"),
            ligamentJointStabilityStressTags = MetadataTokenField.parse("NONE"),
            jointImpactStressTags = MetadataTokenField.parse("NONE"),
            cognitiveStressTags = MetadataTokenField.parse("NONE"),
            sportContextTags = MetadataTokenField.parse("NONE"),
            recoveryDecayProfile = "SHORT",
            stressMagnitudeHint = "LOW",
            badmintonTransferLevel = "NONE",
            badmintonTransferType = MetadataTokenField.parse("NONE"),
            badmintonSkillTargets = MetadataTokenField.parse("NONE"),
            badmintonPhysicalQualities = MetadataTokenField.parse("NONE"),
            transferConfidence = "NONE",
            sourceConfidenceLevel = "HEURISTIC_ACCEPTED",
            finalSourceStatus = "SOURCE_ACCEPTED_WITH_LIMITATION",
            neuromuscularStressLevel = "LOW",
            systemicMuscularStressLevel = "LOW",
            localMuscularStressLevel = "LOW",
            jointTendonImpactStressLevel = "LOW",
            movementFocusDemandLevel = "LOW",
            recoveryDurationClass = "SHORT",
            safeForSeedMutation = false
        )
}

private fun String.splitRuntimeTokens(): Set<String> =
    split(',', '|', '/', ';')
        .map { value -> value.trim() }
        .filter { value -> value.isNotEmpty() && !value.equals("NONE", ignoreCase = true) }
        .toSet()

private fun levelFromWeight(weight: Double): String =
    when {
        weight >= 0.7 -> "HIGH"
        weight >= 0.35 -> "MODERATE"
        else -> "LOW"
    }

data class RuntimeMetadataEditorOptions(
    private val valuesByField: Map<String, List<String>>
) {
    fun values(field: String, current: String = ""): List<String> =
        (valuesByField[field].orEmpty() + current.takeIf(String::isNotBlank).orEmpty())
            .filter(String::isNotBlank)
            .distinct()
            .sorted()

    companion object {
        fun from(metadata: Collection<RuntimeExerciseMetadata>): RuntimeMetadataEditorOptions {
            fun single(selector: (RuntimeExerciseMetadata) -> String): List<String> =
                metadata.map(selector).filter(String::isNotBlank).distinct()
            fun tokens(selector: (RuntimeExerciseMetadata) -> MetadataTokenField): List<String> =
                metadata.flatMap { selector(it).values }.filter(String::isNotBlank).distinct()
            fun merged(field: String, observed: List<String>): List<String> =
                (defaultValuesByField[field].orEmpty() + observed)
                    .filter(String::isNotBlank)
                    .distinct()

            return RuntimeMetadataEditorOptions(
                mapOf(
                    "activityKind" to merged("activityKind", single(RuntimeExerciseMetadata::activityKind)),
                    "planningEligibility" to merged("planningEligibility", single(RuntimeExerciseMetadata::planningEligibility)),
                    "movementFamily" to merged("movementFamily", single(RuntimeExerciseMetadata::movementFamily)),
                    "movementSubtype" to merged("movementSubtype", single(RuntimeExerciseMetadata::movementSubtype)),
                    "programSlot" to merged("programSlot", single(RuntimeExerciseMetadata::programSlot)),
                    "redundancyGroup" to merged("redundancyGroup", single(RuntimeExerciseMetadata::redundancyGroup)),
                    "progressMetricType" to merged("progressMetricType", single(RuntimeExerciseMetadata::progressMetricType)),
                    "strengthProgressionGroup" to merged("strengthProgressionGroup", single(RuntimeExerciseMetadata::strengthProgressionGroup)),
                    "analysisEligibility" to merged("analysisEligibility", tokens(RuntimeExerciseMetadata::analysisEligibility)),
                    "primaryStressProfile" to merged("primaryStressProfile", single(RuntimeExerciseMetadata::primaryStressProfile)),
                    "secondaryStressTags" to merged("secondaryStressTags", tokens(RuntimeExerciseMetadata::secondaryStressTags)),
                    "tendonStressTags" to merged("tendonStressTags", tokens(RuntimeExerciseMetadata::tendonStressTags)),
                    "ligamentJointStabilityStressTags" to merged("ligamentJointStabilityStressTags", tokens(RuntimeExerciseMetadata::ligamentJointStabilityStressTags)),
                    "jointImpactStressTags" to merged("jointImpactStressTags", tokens(RuntimeExerciseMetadata::jointImpactStressTags)),
                    "cognitiveStressTags" to merged("cognitiveStressTags", tokens(RuntimeExerciseMetadata::cognitiveStressTags)),
                    "sportContextTags" to merged("sportContextTags", tokens(RuntimeExerciseMetadata::sportContextTags)),
                    "recoveryDecayProfile" to merged("recoveryDecayProfile", single(RuntimeExerciseMetadata::recoveryDecayProfile)),
                    "stressMagnitudeHint" to merged("stressMagnitudeHint", single(RuntimeExerciseMetadata::stressMagnitudeHint)),
                    "badmintonTransferLevel" to merged("badmintonTransferLevel", single(RuntimeExerciseMetadata::badmintonTransferLevel)),
                    "badmintonTransferType" to merged("badmintonTransferType", tokens(RuntimeExerciseMetadata::badmintonTransferType)),
                    "badmintonSkillTargets" to merged("badmintonSkillTargets", tokens(RuntimeExerciseMetadata::badmintonSkillTargets)),
                    "badmintonPhysicalQualities" to merged("badmintonPhysicalQualities", tokens(RuntimeExerciseMetadata::badmintonPhysicalQualities)),
                    "transferConfidence" to merged("transferConfidence", single(RuntimeExerciseMetadata::transferConfidence)),
                    "sourceConfidenceLevel" to merged("sourceConfidenceLevel", single(RuntimeExerciseMetadata::sourceConfidenceLevel)),
                    "finalSourceStatus" to merged("finalSourceStatus", single(RuntimeExerciseMetadata::finalSourceStatus)),
                    "neuromuscularStressLevel" to merged("neuromuscularStressLevel", single(RuntimeExerciseMetadata::neuromuscularStressLevel)),
                    "systemicMuscularStressLevel" to merged("systemicMuscularStressLevel", single(RuntimeExerciseMetadata::systemicMuscularStressLevel)),
                    "localMuscularStressLevel" to merged("localMuscularStressLevel", single(RuntimeExerciseMetadata::localMuscularStressLevel)),
                    "jointTendonImpactStressLevel" to merged("jointTendonImpactStressLevel", single(RuntimeExerciseMetadata::jointTendonImpactStressLevel)),
                    "movementFocusDemandLevel" to merged("movementFocusDemandLevel", single(RuntimeExerciseMetadata::movementFocusDemandLevel)),
                    "recoveryDurationClass" to merged("recoveryDurationClass", single(RuntimeExerciseMetadata::recoveryDurationClass))
                )
            )
        }

        private val levels = listOf("LOW", "MODERATE", "HIGH", "VERY_HIGH")
        private val durations = listOf("SHORT", "MEDIUM", "LONG", "VERY_LONG")
        private val defaultValuesByField = mapOf(
            "activityKind" to listOf("EXERCISE", "SPORT_SESSION"),
            "planningEligibility" to listOf("PROGRAM_SELECTABLE", "FATIGUE_ONLY", "ANALYSIS_ONLY", "HIDDEN"),
            "movementFamily" to MovementPattern.entries.map { it.name } + ProgramSlotId.entries.map { it.name } + listOf("NOT_APPLICABLE"),
            "movementSubtype" to MovementPattern.entries.map { it.name } + listOf("NOT_APPLICABLE"),
            "programSlot" to ProgramSlotId.entries.map { it.name } + listOf(
                "NOT_APPLICABLE",
                "MAIN_LOWER_STRENGTH",
                "MAIN_HINGE_STRENGTH",
                "HORIZONTAL_PULL_STRENGTH",
                "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY",
                "OVERHEAD_PUSH_STRENGTH_OR_ACCESSORY",
                "BADMINTON_FOOTWORK",
                "DECELERATION_LANDING",
                "ROTATIONAL_KINETIC_CHAIN",
                "SCAPULAR_SHOULDER_SUPPORT",
                "TRUNK_ANTI_ROTATION_STABILITY",
                "POWER_REACTIVE_LOW_VOLUME",
                "RECOVERY_PREHAB_LIGHT"
            ),
            "redundancyGroup" to ProgramSlotId.entries.map { it.name } + listOf("NOT_APPLICABLE"),
            "progressMetricType" to listOf(
                "NOT_APPLICABLE",
                "LOAD_REPS",
                "VOLUME_LOAD",
                "ESTIMATED_1RM",
                "REPS_OR_TIME",
                "SESSION_DURATION",
                "TIME_DISTANCE",
                "QUALITY_BASED",
                "COUNT_ONLY"
            ),
            "strengthProgressionGroup" to StrengthProgressionGroup.entries.map { it.name } + ProgramSlotId.entries.map { it.name },
            "analysisEligibility" to AnalysisEligibility.entries.map { it.name },
            "primaryStressProfile" to listOf(
                "LOW_LOAD_PREHAB_CONTROL_STRESS",
                "HEAVY_AXIAL_LOWER_STRESS",
                "HINGE_POSTERIOR_CHAIN_STRESS",
                "HORIZONTAL_PUSH_STRESS",
                "HORIZONTAL_ROW_STRESS",
                "VERTICAL_PULL_STRESS",
                "OVERHEAD_PUSH_STRESS",
                "ROTATIONAL_CORE_STABILITY_STRESS",
                "PLYOMETRIC_LANDING_STRESS",
                "COURT_SPORT_MOVEMENT_STRESS",
                "CARDIO_CONDITIONING_STRESS"
            ),
            "secondaryStressTags" to FatigueCategory.entries.map { it.name } + listOf(
                "QUAD_LOAD",
                "HAMSTRING_LOAD",
                "GLUTE_LOAD",
                "CHEST_LOAD",
                "LAT_LOAD",
                "TRICEPS_LOAD",
                "BICEPS_LOAD",
                "GRIP_FOREARM_LOAD",
                "CORE_BRACING_LOAD",
                "NEURAL_LOAD",
                "ROTATION_POWER_LOAD",
                "DECELERATION_LOAD",
                "ELASTIC_SSC_LOAD"
            ),
            "tendonStressTags" to JointStressTag.entries.map { it.name } + listOf("PATELLAR_TENDON_STRESS", "ACHILLES_TENDON_STRESS", "ROTATOR_CUFF_TENDON_STRESS"),
            "ligamentJointStabilityStressTags" to JointStressTag.entries.map { it.name } + listOf("KNEE_VALGUS_CONTROL_STRESS", "LUMBOPELVIC_CONTROL_STRESS", "SHOULDER_SCAPULAR_STABILITY_STRESS"),
            "jointImpactStressTags" to listOf("DECELERATION_IMPACT", "JUMP_LANDING_IMPACT_STRESS", "COURT_DECELERATION_IMPACT", "LOW_LEVEL_REACTIVE_IMPACT"),
            "cognitiveStressTags" to listOf("REACTION_LOAD", "DECISION_MAKING_LOAD", "MOTOR_LEARNING_LOAD", "TECHNICAL_CONCENTRATION_LOAD"),
            "sportContextTags" to listOf("BADMINTON_DIRECT_TRANSFER", "BADMINTON_FOOTWORK", "BADMINTON_MULTI_SHUTTLE", "GENERAL_CONDITIONING", "OTHER_SPORT_SESSION"),
            "recoveryDecayProfile" to durations,
            "stressMagnitudeHint" to levels,
            "badmintonTransferLevel" to BadmintonTransferStrength.entries.map { it.name },
            "badmintonTransferType" to BadmintonTransferRole.entries.map { it.name },
            "badmintonSkillTargets" to BadmintonSkillTarget.entries.map { it.name },
            "badmintonPhysicalQualities" to CourtMovementType.entries.map { it.name } + BalanceContributionTag.entries.map { it.name },
            "transferConfidence" to listOf("NONE", "LOW", "MEDIUM", "HIGH"),
            "sourceConfidenceLevel" to listOf("HEURISTIC_ACCEPTED", "ANATOMY_SUPPORTED", "SOURCE_WEAK_BUT_ACCEPTABLE", "VERIFIED_FAMILY", "VERIFIED_EXACT"),
            "finalSourceStatus" to listOf("SOURCE_ACCEPTED", "SOURCE_ACCEPTED_WITH_LIMITATION"),
            "neuromuscularStressLevel" to levels,
            "systemicMuscularStressLevel" to levels,
            "localMuscularStressLevel" to levels,
            "jointTendonImpactStressLevel" to levels,
            "movementFocusDemandLevel" to levels,
            "recoveryDurationClass" to durations
        )
    }
}

data class ExerciseRuntimeMetadataEditorData(
    val exercise: Exercise,
    val metadata: RuntimeExerciseMetadata,
    val options: RuntimeMetadataEditorOptions,
    val copySources: List<ExerciseMetadataCopySource> = emptyList()
)

data class ExerciseMetadataCopySource(
    val exercise: Exercise,
    val metadata: RuntimeExerciseMetadata
)

internal fun Exercise.copyEditableMetadataFrom(source: Exercise): Exercise =
    copy(
        category = source.category,
        familyId = source.familyId,
        familyName = source.familyName,
        familyRole = source.familyRole,
        familyE1rmMultiplier = source.familyE1rmMultiplier,
        movementPattern = source.movementPattern,
        movementCategory = source.movementCategory,
        primaryMuscles = source.primaryMuscles,
        secondaryMuscles = source.secondaryMuscles,
        equipment = source.equipment,
        equipmentTags = source.equipmentTags,
        compoundType = source.compoundType,
        forceType = source.forceType,
        bodyRegion = source.bodyRegion,
        plane = source.plane,
        laterality = source.laterality,
        axialLoadLevel = source.axialLoadLevel,
        trainingRole = source.trainingRole,
        stabilityRoles = source.stabilityRoles,
        sportTransferDirect = source.sportTransferDirect,
        sportTransferSupportive = source.sportTransferSupportive,
        badmintonTransferRoles = source.badmintonTransferRoles,
        fatigueCategories = source.fatigueCategories,
        adaptiveBaselineGroups = source.adaptiveBaselineGroups,
        accessoryRoles = source.accessoryRoles,
        loadProfile = source.loadProfile,
        recoveryDecayProfile = source.recoveryDecayProfile,
        systemicLoadWeight = source.systemicLoadWeight,
        neuralHeavyWeight = source.neuralHeavyWeight,
        neuralSpeedWeight = source.neuralSpeedWeight,
        localLoadWeight = source.localLoadWeight,
        decelerationWeight = source.decelerationWeight,
        elasticSscWeight = source.elasticSscWeight,
        rotationPowerWeight = source.rotationPowerWeight,
        antiRotationWeight = source.antiRotationWeight,
        overheadSwingWeight = source.overheadSwingWeight,
        gripLoadWeight = source.gripLoadWeight,
        progressMetricType = source.progressMetricType,
        strengthProgressionGroup = source.strengthProgressionGroup,
        hypertrophyVolumeGroup = source.hypertrophyVolumeGroup,
        mainLiftGroup = source.mainLiftGroup,
        accessoryContributionGroup = source.accessoryContributionGroup,
        estimated1RmEligible = source.estimated1RmEligible,
        volumeLoadEligible = source.volumeLoadEligible,
        badmintonTransferStrength = source.badmintonTransferStrength,
        courtMovementTypes = source.courtMovementTypes,
        badmintonSkillTargets = source.badmintonSkillTargets,
        jointStressTags = source.jointStressTags,
        stabilityDemandLevel = source.stabilityDemandLevel,
        mobilityDemandLevel = source.mobilityDemandLevel,
        balanceContributionTags = source.balanceContributionTags,
        analysisEligibility = source.analysisEligibility,
        activityKind = source.activityKind,
        planningEligibility = source.planningEligibility,
        metadataConfidence = source.metadataConfidence
    )

internal fun RuntimeExerciseMetadata.copyEditableMetadataFrom(source: RuntimeExerciseMetadata): RuntimeExerciseMetadata =
    source.copy(
        stableKey = stableKey,
        exerciseName = exerciseName,
        safeForSeedMutation = false,
        appCueProfile = appCueProfile
    )

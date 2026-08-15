package com.training.trackplanner.data

import java.util.UUID
import java.nio.charset.StandardCharsets

object UserExerciseStableKeyGenerator {
    const val PREFIX = "user_ex_"

    fun generate(uuid: UUID = UUID.randomUUID()): String = PREFIX + uuid.toString()

    fun generateDeterministic(sourceIdentity: String): String =
        PREFIX + UUID.nameUUIDFromBytes(sourceIdentity.toByteArray(StandardCharsets.UTF_8)).toString()

    fun isUserExerciseKey(value: String): Boolean = value.startsWith(PREFIX)
}

internal object ExerciseStableKeyPolicy {
    fun preserveOnEdit(existing: Exercise, edited: Exercise, repairedKey: String): Exercise =
        edited.copy(
            stableKey = existing.stableKey.ifBlank { repairedKey },
            isCustom = existing.isCustom,
            isActive = existing.isActive,
            archivedAt = existing.archivedAt
        )

    fun mergeSeed(existing: Exercise, seed: Exercise): Exercise? {
        if (existing.isCustom) return null
        return seed.copy(
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
            stableKey = exercise.stableKey,
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
        return when {
            persisted != null && canonical != null -> persisted.copy(
                stableKey = canonical.stableKey,
                exerciseName = canonical.exerciseName,
                planningEligibility = if (canonical.planningEligibility == "HISTORY_ONLY") {
                    canonical.planningEligibility
                } else {
                    persisted.planningEligibility
                },
                recoveryDecayProfile = canonical.recoveryDecayProfile,
                safeForSeedMutation = false,
                appCueProfile = canonical.appCueProfile
            )
            persisted != null -> persisted.copy(
                stableKey = exercise.stableKey,
                exerciseName = exercise.name,
                safeForSeedMutation = false
            )
            canonical != null -> canonical
            else -> RuntimeExerciseMetadataDefaults.forIdentity(exercise.stableKey, exercise.name)
        }
    }

    fun catalog(exercises: Collection<Exercise>): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataCatalog.of(
            metadata = exercises.map(::resolve),
            canonicalBadmintonAuthorityKeys = exercises
                .map(Exercise::stableKey)
                .filter(canonicalCatalog::hasCanonicalBadmintonAuthority)
        )
}

private fun String.ifSet(): String? =
    takeUnless { value ->
        value.isBlank() ||
            value.equals("NONE", ignoreCase = true) ||
            value.equals("NOT_APPLICABLE", ignoreCase = true)
    }

private fun String.seedLookupKey(): String = trim().lowercase()

object RuntimeExerciseMetadataDefaults {
    fun forExercise(exercise: Exercise): RuntimeExerciseMetadata =
        forIdentity(exercise.stableKey, exercise.name)

    fun forIdentity(stableKey: String, exerciseName: String): RuntimeExerciseMetadata =
        RuntimeExerciseMetadata(
            stableKey = stableKey,
            exerciseName = exerciseName,
            activityKind = "UNKNOWN",
            planningEligibility = "HIDDEN",
            movementFamily = "NOT_APPLICABLE",
            movementSubtype = "NOT_APPLICABLE",
            programSlot = "NOT_APPLICABLE",
            redundancyGroup = "NOT_APPLICABLE",
            progressMetricType = "NOT_APPLICABLE",
            strengthProgressionGroup = "NOT_APPLICABLE",
            analysisEligibility = MetadataTokenField.parse("NONE"),
            primaryStressProfile = "NOT_APPLICABLE",
            secondaryStressTags = MetadataTokenField.parse("NONE"),
            tendonStressTags = MetadataTokenField.parse("NONE"),
            ligamentJointStabilityStressTags = MetadataTokenField.parse("NONE"),
            jointImpactStressTags = MetadataTokenField.parse("NONE"),
            cognitiveStressTags = MetadataTokenField.parse("NONE"),
            sportContextTags = MetadataTokenField.parse("NONE"),
            recoveryDecayProfile = "NOT_APPLICABLE",
            stressMagnitudeHint = "NONE",
            badmintonTransferLevel = "NONE",
            badmintonTransferType = MetadataTokenField.parse("NONE"),
            badmintonSkillTargets = MetadataTokenField.parse("NONE"),
            badmintonPhysicalQualities = MetadataTokenField.parse("NONE"),
            transferConfidence = "NONE",
            sourceConfidenceLevel = "UNREVIEWED",
            finalSourceStatus = "UNAVAILABLE",
            neuromuscularStressLevel = "NONE",
            systemicMuscularStressLevel = "NONE",
            localMuscularStressLevel = "NONE",
            jointTendonImpactStressLevel = "NONE",
            movementFocusDemandLevel = "NONE",
            recoveryDurationClass = "NOT_APPLICABLE",
            safeForSeedMutation = false
        )
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
        internal fun knownValuesByField(): Map<String, List<String>> = defaultValuesByField

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
    val copySources: List<ExerciseMetadataCopySource> = emptyList(),
    val originalExercise: Exercise = exercise,
    val originalMetadata: RuntimeExerciseMetadata = metadata
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

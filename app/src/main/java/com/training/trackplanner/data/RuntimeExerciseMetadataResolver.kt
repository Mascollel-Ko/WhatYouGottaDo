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

            return RuntimeMetadataEditorOptions(
                mapOf(
                    "activityKind" to single(RuntimeExerciseMetadata::activityKind),
                    "planningEligibility" to single(RuntimeExerciseMetadata::planningEligibility),
                    "movementFamily" to single(RuntimeExerciseMetadata::movementFamily),
                    "movementSubtype" to single(RuntimeExerciseMetadata::movementSubtype),
                    "programSlot" to single(RuntimeExerciseMetadata::programSlot),
                    "redundancyGroup" to single(RuntimeExerciseMetadata::redundancyGroup),
                    "progressMetricType" to single(RuntimeExerciseMetadata::progressMetricType),
                    "strengthProgressionGroup" to single(RuntimeExerciseMetadata::strengthProgressionGroup),
                    "analysisEligibility" to tokens(RuntimeExerciseMetadata::analysisEligibility),
                    "primaryStressProfile" to single(RuntimeExerciseMetadata::primaryStressProfile),
                    "secondaryStressTags" to tokens(RuntimeExerciseMetadata::secondaryStressTags),
                    "tendonStressTags" to tokens(RuntimeExerciseMetadata::tendonStressTags),
                    "ligamentJointStabilityStressTags" to tokens(RuntimeExerciseMetadata::ligamentJointStabilityStressTags),
                    "jointImpactStressTags" to tokens(RuntimeExerciseMetadata::jointImpactStressTags),
                    "cognitiveStressTags" to tokens(RuntimeExerciseMetadata::cognitiveStressTags),
                    "sportContextTags" to tokens(RuntimeExerciseMetadata::sportContextTags),
                    "recoveryDecayProfile" to single(RuntimeExerciseMetadata::recoveryDecayProfile),
                    "stressMagnitudeHint" to single(RuntimeExerciseMetadata::stressMagnitudeHint),
                    "badmintonTransferLevel" to single(RuntimeExerciseMetadata::badmintonTransferLevel),
                    "badmintonTransferType" to tokens(RuntimeExerciseMetadata::badmintonTransferType),
                    "badmintonSkillTargets" to tokens(RuntimeExerciseMetadata::badmintonSkillTargets),
                    "badmintonPhysicalQualities" to tokens(RuntimeExerciseMetadata::badmintonPhysicalQualities),
                    "transferConfidence" to single(RuntimeExerciseMetadata::transferConfidence),
                    "sourceConfidenceLevel" to single(RuntimeExerciseMetadata::sourceConfidenceLevel),
                    "finalSourceStatus" to single(RuntimeExerciseMetadata::finalSourceStatus),
                    "neuromuscularStressLevel" to single(RuntimeExerciseMetadata::neuromuscularStressLevel),
                    "systemicMuscularStressLevel" to single(RuntimeExerciseMetadata::systemicMuscularStressLevel),
                    "localMuscularStressLevel" to single(RuntimeExerciseMetadata::localMuscularStressLevel),
                    "jointTendonImpactStressLevel" to single(RuntimeExerciseMetadata::jointTendonImpactStressLevel),
                    "movementFocusDemandLevel" to single(RuntimeExerciseMetadata::movementFocusDemandLevel),
                    "recoveryDurationClass" to single(RuntimeExerciseMetadata::recoveryDurationClass)
                )
            )
        }
    }
}

data class ExerciseRuntimeMetadataEditorData(
    val exercise: Exercise,
    val metadata: RuntimeExerciseMetadata,
    val options: RuntimeMetadataEditorOptions
)

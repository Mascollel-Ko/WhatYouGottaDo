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

    fun resolve(exercise: Exercise): RuntimeExerciseMetadata =
        persistedByStableKey[exercise.stableKey]
            ?: canonicalCatalog.resolveByStableKey(exercise.stableKey)
            ?: RuntimeExerciseMetadataDefaults.forExercise(exercise)

    fun catalog(exercises: Collection<Exercise>): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataCatalog.of(exercises.map(::resolve))
}

object RuntimeExerciseMetadataDefaults {
    fun forExercise(exercise: Exercise): RuntimeExerciseMetadata =
        forIdentity(exercise.stableKey, exercise.name)

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

package com.training.trackplanner.data

import java.util.Locale

private fun String.isLegacyImportedKey(): Boolean =
    isBlank() || startsWith("imported_", ignoreCase = true)

data class MetadataTokenField(
    val raw: String,
    val values: List<String>
) {
    operator fun contains(value: String): Boolean =
        values.any { token -> token.equals(value, ignoreCase = true) }

    companion object {
        fun parse(raw: String?): MetadataTokenField {
            val source = raw.orEmpty()
            if (source.isBlank() || source.trim().equals("NONE", ignoreCase = true)) {
                return MetadataTokenField(raw = source, values = emptyList())
            }
            val seen = linkedSetOf<String>()
            val values = source.split('|')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .filterNot { token -> token.equals("NONE", ignoreCase = true) }
                .filter { token -> seen.add(token.uppercase(Locale.ROOT)) }
            return MetadataTokenField(raw = source, values = values)
        }
    }
}

enum class ProgressMetricRuntimeBehavior {
    LOAD_REPS,
    VOLUME_LOAD,
    ESTIMATED_1RM,
    REPS_OR_TIME,
    DISTANCE_OR_TIME,
    SESSION_DURATION,
    QUALITY_BASED,
    COUNT_ONLY,
    NOT_APPLICABLE,
    UNSUPPORTED_SAFE_NO_OP
}

enum class AnalysisTokenRuntimeSupport {
    CURRENT_CALCULATOR,
    PRESERVED_SAFE_NO_OP
}

enum class RuntimeBadmintonTransferLevel {
    DIRECT,
    SUPPORTIVE,
    GENERAL,
    NONE,
    UNKNOWN
}

data class RuntimeExerciseMetadata(
    val stableKey: String,
    val exerciseName: String,
    val activityKind: String,
    val planningEligibility: String,
    val movementFamily: String,
    val movementSubtype: String,
    val programSlot: String,
    val redundancyGroup: String,
    val progressMetricType: String,
    val strengthProgressionGroup: String,
    val analysisEligibility: MetadataTokenField,
    val primaryStressProfile: String,
    val secondaryStressTags: MetadataTokenField,
    val tendonStressTags: MetadataTokenField,
    val ligamentJointStabilityStressTags: MetadataTokenField,
    val jointImpactStressTags: MetadataTokenField,
    val cognitiveStressTags: MetadataTokenField,
    val sportContextTags: MetadataTokenField,
    val recoveryDecayProfile: String,
    val stressMagnitudeHint: String,
    val badmintonTransferLevel: String,
    val badmintonTransferType: MetadataTokenField,
    val badmintonSkillTargets: MetadataTokenField,
    val badmintonPhysicalQualities: MetadataTokenField,
    val transferConfidence: String,
    val sourceConfidenceLevel: String,
    val finalSourceStatus: String,
    val neuromuscularStressLevel: String,
    val systemicMuscularStressLevel: String,
    val localMuscularStressLevel: String,
    val jointTendonImpactStressLevel: String,
    val movementFocusDemandLevel: String,
    val recoveryDurationClass: String,
    val safeForSeedMutation: Boolean
) {
    val progressBehavior: ProgressMetricRuntimeBehavior
        get() = ExerciseMetadataAdapter.progressMetricBehavior(progressMetricType)

    val transferLevel: RuntimeBadmintonTransferLevel
        get() = ExerciseMetadataAdapter.badmintonTransferLevel(badmintonTransferLevel)

    fun broadLegacyFatigueCategories(): Set<String> =
        ExerciseMetadataAdapter.broadLegacyFatigueCategories(this)
}

class RuntimeExerciseMetadataCatalog private constructor(
    metadata: Collection<RuntimeExerciseMetadata>
) {
    private val byStableKey = metadata
        .filter { it.stableKey.isNotBlank() }
        .associateBy { it.stableKey.trim().lowercase(Locale.ROOT) }
    private val byExerciseName = metadata
        .filter { it.exerciseName.isNotBlank() }
        .associateBy { it.exerciseName.trim().lowercase(Locale.ROOT) }

    val size: Int
        get() = byStableKey.size

    fun resolveByStableKey(stableKey: String): RuntimeExerciseMetadata? =
        byStableKey[stableKey.trim().lowercase(Locale.ROOT)]

    fun resolveLegacyName(exerciseName: String): RuntimeExerciseMetadata? =
        byExerciseName[exerciseName.trim().lowercase(Locale.ROOT)]

    fun resolve(stableKey: String, exerciseName: String, allowNameFallback: Boolean): RuntimeExerciseMetadata? =
        resolveByStableKey(stableKey)
            ?: if (allowNameFallback) resolveLegacyName(exerciseName) else null

    fun resolve(exercise: Exercise): RuntimeExerciseMetadata? =
        resolve(
            stableKey = exercise.stableKey,
            exerciseName = exercise.name,
            allowNameFallback = exercise.isCustom || exercise.stableKey.isLegacyImportedKey()
        )

    companion object {
        val EMPTY = RuntimeExerciseMetadataCatalog(emptyList())

        fun of(metadata: Collection<RuntimeExerciseMetadata>): RuntimeExerciseMetadataCatalog =
            RuntimeExerciseMetadataCatalog(metadata)
    }
}

object ExerciseMetadataAdapter {
    private val currentAnalysisTokens = setOf(
        "FATIGUE",
        "STRENGTH_PROGRESS",
        "HYPERTROPHY_VOLUME",
        "BADMINTON_TRANSFER",
        "BALANCE",
        "RECOVERY_ONLY"
    )

    fun fromFields(fields: Map<String, String>): RuntimeExerciseMetadata =
        RuntimeExerciseMetadata(
            stableKey = fields.value("stableKey", "stable_key"),
            exerciseName = fields.value("exerciseName", "exercise_name"),
            activityKind = fields.value("currentActivityKind", "activityKind"),
            planningEligibility = fields.value("planningEligibility", "currentPlanningEligibility"),
            movementFamily = fields.value("movementFamily"),
            movementSubtype = fields.value("movementSubtype"),
            programSlot = fields.value("programSlot"),
            redundancyGroup = fields.value("redundancyGroup"),
            progressMetricType = fields.value("progressMetricType"),
            strengthProgressionGroup = fields.value("strengthProgressionGroup"),
            analysisEligibility = MetadataTokenField.parse(fields.value("analysisEligibility")),
            primaryStressProfile = fields.value("primaryStressProfile"),
            secondaryStressTags = MetadataTokenField.parse(fields.value("secondaryStressTags")),
            tendonStressTags = MetadataTokenField.parse(fields.value("tendonStressTags")),
            ligamentJointStabilityStressTags = MetadataTokenField.parse(
                fields.value("ligamentJointStabilityStressTags")
            ),
            jointImpactStressTags = MetadataTokenField.parse(fields.value("jointImpactStressTags")),
            cognitiveStressTags = MetadataTokenField.parse(fields.value("cognitiveStressTags")),
            sportContextTags = MetadataTokenField.parse(fields.value("sportContextTags")),
            recoveryDecayProfile = fields.value("recoveryDecayProfile"),
            stressMagnitudeHint = fields.value("stressMagnitudeHint"),
            badmintonTransferLevel = fields.value("badmintonTransferLevel"),
            badmintonTransferType = MetadataTokenField.parse(fields.value("badmintonTransferType")),
            badmintonSkillTargets = MetadataTokenField.parse(fields.value("badmintonSkillTargets")),
            badmintonPhysicalQualities = MetadataTokenField.parse(
                fields.value("badmintonPhysicalQualities")
            ),
            transferConfidence = fields.value("transferConfidence"),
            sourceConfidenceLevel = fields.value("sourceConfidenceLevel"),
            finalSourceStatus = fields.value("finalSourceStatus"),
            neuromuscularStressLevel = fields.value("neuromuscularStressLevel"),
            systemicMuscularStressLevel = fields.value("systemicMuscularStressLevel"),
            localMuscularStressLevel = fields.value("localMuscularStressLevel"),
            jointTendonImpactStressLevel = fields.value("jointTendonImpactStressLevel"),
            movementFocusDemandLevel = fields.value("movementFocusDemandLevel"),
            recoveryDurationClass = fields.value("recoveryDurationClass"),
            safeForSeedMutation = fields.value("safeForSeedMutation").toBooleanFlag()
        )

    fun fromCsv(csv: String): List<RuntimeExerciseMetadata> {
        val rows = csv.lineSequence()
            .filter { line -> line.isNotBlank() }
            .map(::parseCsvLine)
            .toList()
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { value -> value.trim().trimStart('\uFEFF') }
        return rows.drop(1).map { values ->
            fromFields(
                header.mapIndexed { index, key ->
                    key to values.getOrElse(index) { "" }
                }.toMap()
            )
        }
    }

    fun progressMetricBehavior(raw: String): ProgressMetricRuntimeBehavior =
        when (raw.trim().uppercase(Locale.ROOT)) {
            "LOAD_REPS", "LOAD_REPS_OR_REPS", "LOAD_REPS_OR_TIME", "MACHINE_LOAD_REPS", "REPS_AT_LOAD" ->
                ProgressMetricRuntimeBehavior.LOAD_REPS
            "VOLUME_LOAD" -> ProgressMetricRuntimeBehavior.VOLUME_LOAD
            "ESTIMATED_1RM" -> ProgressMetricRuntimeBehavior.ESTIMATED_1RM
            "REPS_OR_TIME", "TIME", "TIME_OR_COMPLETION", "TIME_OR_REPS" ->
                ProgressMetricRuntimeBehavior.REPS_OR_TIME
            "DISTANCE_OR_TIME_LOAD", "TIME_DISTANCE", "TIME_DISTANCE_PACE_OR_INTENSITY", "TIME_OR_DISTANCE" ->
                ProgressMetricRuntimeBehavior.DISTANCE_OR_TIME
            "SESSION_DURATION" -> ProgressMetricRuntimeBehavior.SESSION_DURATION
            "QUALITY_BASED", "QUALITY_LOAD_REPS" -> ProgressMetricRuntimeBehavior.QUALITY_BASED
            "COUNT_ONLY" -> ProgressMetricRuntimeBehavior.COUNT_ONLY
            "NOT_APPLICABLE", "NONE", "" -> ProgressMetricRuntimeBehavior.NOT_APPLICABLE
            else -> ProgressMetricRuntimeBehavior.UNSUPPORTED_SAFE_NO_OP
        }

    fun analysisTokenSupport(raw: String): AnalysisTokenRuntimeSupport =
        if (raw.trim().uppercase(Locale.ROOT) in currentAnalysisTokens) {
            AnalysisTokenRuntimeSupport.CURRENT_CALCULATOR
        } else {
            AnalysisTokenRuntimeSupport.PRESERVED_SAFE_NO_OP
        }

    fun badmintonTransferLevel(raw: String): RuntimeBadmintonTransferLevel =
        when (raw.trim().uppercase(Locale.ROOT)) {
            "DIRECT" -> RuntimeBadmintonTransferLevel.DIRECT
            "SUPPORTIVE" -> RuntimeBadmintonTransferLevel.SUPPORTIVE
            "GENERAL" -> RuntimeBadmintonTransferLevel.GENERAL
            "NONE", "" -> RuntimeBadmintonTransferLevel.NONE
            else -> RuntimeBadmintonTransferLevel.UNKNOWN
        }

    fun broadLegacyFatigueCategories(metadata: RuntimeExerciseMetadata): Set<String> =
        buildSet {
            val tokens = buildList {
                add(metadata.primaryStressProfile)
                addAll(metadata.secondaryStressTags.values)
                addAll(metadata.tendonStressTags.values)
                addAll(metadata.ligamentJointStabilityStressTags.values)
                addAll(metadata.jointImpactStressTags.values)
            }.map { it.uppercase(Locale.ROOT) }
            if (tokens.any { it.contains("AXIAL") || it.contains("SYSTEMIC") }) add("SYSTEMIC")
            if (tokens.any { it.contains("BALLISTIC") || it.contains("PLYOMETRIC") }) add("NEURAL_SPEED")
            if (tokens.any { it.contains("DECELERATION") || it.contains("LANDING") }) add("DECELERATION")
            if (tokens.any { it.contains("SSC") || it.contains("ELASTIC") }) add("ELASTIC_SSC")
            if (tokens.any { it.contains("OVERHEAD") || it.contains("RACKET") }) add("OVERHEAD_REPETITION")
            if (tokens.any { it.contains("GRIP") || it.contains("FOREARM") }) add("GRIP_FOREARM")
            if (tokens.any { it.contains("ISOLATION") || it.contains("LOCAL") }) add("LOCAL_MUSCLE")
            if (tokens.any { it.contains("PREHAB") || it.contains("RECOVERY") }) add("LOW_FATIGUE_REHAB")
        }

    private fun Map<String, String>.value(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> this[key]?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()

    private fun String.toBooleanFlag(): Boolean =
        trim().uppercase(Locale.ROOT) in setOf("1", "TRUE", "YES", "Y")

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        values += current.toString()
        return values
    }
}

package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.MetadataSanityChecker

data class ExerciseReadinessRow(
    val exerciseId: Long,
    val exerciseName: String,
    val metadataConfidence: String,
    val fatigueReady: ReadinessStatus,
    val progressReady: ReadinessStatus,
    val badmintonReady: ReadinessStatus,
    val balanceReady: ReadinessStatus,
    val missingFields: List<String>,
    val suspiciousMappings: List<String>,
    val needsReviewReason: String
)

data class MetadataAnalysisReadinessReport(
    val rows: List<ExerciseReadinessRow>,
    val summary: MetadataReadinessSummary,
    val stringParsingResidues: List<String>,
    val mappingLayerExists: Boolean
)

data class MetadataReadinessSummary(
    val totalExerciseCount: Int,
    val fatigueReadyCounts: Map<ReadinessStatus, Int>,
    val progressReadyCounts: Map<ReadinessStatus, Int>,
    val badmintonReadyCounts: Map<ReadinessStatus, Int>,
    val balanceReadyCounts: Map<ReadinessStatus, Int>,
    val metadataConfidenceCounts: Map<String, Int>,
    val topMissingFields: List<Pair<String, Int>>,
    val needsReviewExerciseNames: List<String>
)

enum class ReadinessStatus {
    YES,
    PARTIAL,
    NO
}

object MetadataReadinessReporter {
    fun generate(exercises: List<Exercise>): MetadataAnalysisReadinessReport {
        val sanityReport = MetadataSanityChecker.checkAll(exercises)
        val sanityByName = sanityReport.issues.groupBy { issue -> issue.exerciseName }
        val rows = exercises.map { exercise ->
            val missingFields = exercise.missingAnalysisFields()
            val suspicious = sanityByName[exercise.name]
                ?.map { issue -> "${issue.field}: ${issue.message}" }
                .orEmpty()
            ExerciseReadinessRow(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                metadataConfidence = exercise.metadataConfidence,
                fatigueReady = fatigueReady(exercise, missingFields, suspicious),
                progressReady = progressReady(exercise, missingFields, suspicious),
                badmintonReady = badmintonReady(exercise, missingFields, suspicious),
                balanceReady = balanceReady(exercise, missingFields, suspicious),
                missingFields = missingFields,
                suspiciousMappings = suspicious,
                needsReviewReason = suspicious.joinToString("; ")
            )
        }

        return MetadataAnalysisReadinessReport(
            rows = rows,
            summary = MetadataReadinessSummary(
                totalExerciseCount = rows.size,
                fatigueReadyCounts = rows.groupingBy { row -> row.fatigueReady }.eachCount(),
                progressReadyCounts = rows.groupingBy { row -> row.progressReady }.eachCount(),
                badmintonReadyCounts = rows.groupingBy { row -> row.badmintonReady }.eachCount(),
                balanceReadyCounts = rows.groupingBy { row -> row.balanceReady }.eachCount(),
                metadataConfidenceCounts = exercises.groupingBy { exercise -> exercise.metadataConfidence }.eachCount().toSortedMap(),
                topMissingFields = rows
                    .flatMap { row -> row.missingFields }
                    .groupingBy { field -> field }
                    .eachCount()
                    .entries
                    .sortedByDescending { entry -> entry.value }
                    .take(10)
                    .map { entry -> entry.key to entry.value },
                needsReviewExerciseNames = rows
                    .filter { row -> row.suspiciousMappings.isNotEmpty() || row.metadataConfidence in setOf("LOW", "NEEDS_REVIEW") }
                    .map { row -> row.exerciseName }
                    .sorted()
            ),
            stringParsingResidues = knownStringParsingResidues,
            mappingLayerExists = true
        )
    }

    private fun fatigueReady(
        exercise: Exercise,
        missingFields: List<String>,
        suspicious: List<String>
    ): ReadinessStatus =
        readiness(
            missingFields = missingFields.filter { field ->
                field in setOf(
                    "fatigueCategories",
                    "adaptiveBaselineGroups",
                    "recoveryDecayProfile"
                )
            },
            suspicious = suspicious,
            requiredEligibility = "FATIGUE",
            eligibility = exercise.analysisEligibility
        )

    private fun progressReady(
        exercise: Exercise,
        missingFields: List<String>,
        suspicious: List<String>
    ): ReadinessStatus =
        readiness(
            missingFields = missingFields.filter { field ->
                field in setOf(
                    "progressMetricType",
                    "strengthProgressionGroup",
                    "hypertrophyVolumeGroup",
                    "mainLiftGroup",
                    "accessoryContributionGroup"
                )
            },
            suspicious = suspicious.filter { issue -> issue.contains("progress", ignoreCase = true) },
            requiredEligibility = null,
            eligibility = exercise.analysisEligibility
        )

    private fun badmintonReady(
        exercise: Exercise,
        missingFields: List<String>,
        suspicious: List<String>
    ): ReadinessStatus =
        readiness(
            missingFields = missingFields.filter { field ->
                field in setOf(
                    "badmintonTransferStrength",
                    "badmintonTransferRoles",
                    "courtMovementTypes",
                    "badmintonSkillTargets"
                )
            },
            suspicious = suspicious.filter { issue -> issue.contains("badminton", ignoreCase = true) || issue.contains("court", ignoreCase = true) },
            requiredEligibility = null,
            eligibility = exercise.analysisEligibility
        )

    private fun balanceReady(
        exercise: Exercise,
        missingFields: List<String>,
        suspicious: List<String>
    ): ReadinessStatus =
        readiness(
            missingFields = missingFields.filter { field ->
                field in setOf(
                    "jointStressTags",
                    "stabilityDemandLevel",
                    "mobilityDemandLevel",
                    "balanceContributionTags"
                )
            },
            suspicious = suspicious.filter { issue -> issue.contains("balance", ignoreCase = true) },
            requiredEligibility = null,
            eligibility = exercise.analysisEligibility
        )

    private fun readiness(
        missingFields: List<String>,
        suspicious: List<String>,
        requiredEligibility: String?,
        eligibility: String
    ): ReadinessStatus = when {
        missingFields.isNotEmpty() -> ReadinessStatus.NO
        suspicious.isNotEmpty() -> ReadinessStatus.PARTIAL
        requiredEligibility != null && requiredEligibility !in eligibility.splitTokens() && "RECOVERY_ONLY" !in eligibility.splitTokens() && "TEST_ONLY" !in eligibility.splitTokens() -> ReadinessStatus.PARTIAL
        else -> ReadinessStatus.YES
    }

    private fun Exercise.missingAnalysisFields(): List<String> =
        listOf(
            "movementPattern" to movementPattern,
            "movementCategory" to movementCategory,
            "primaryMuscles" to primaryMuscles,
            "equipment" to equipment,
            "compoundType" to compoundType,
            "forceType" to forceType,
            "plane" to plane,
            "laterality" to laterality,
            "axialLoadLevel" to axialLoadLevel,
            "trainingRole" to trainingRole,
            "badmintonTransferRoles" to badmintonTransferRoles,
            "fatigueCategories" to fatigueCategories,
            "adaptiveBaselineGroups" to adaptiveBaselineGroups,
            "recoveryDecayProfile" to recoveryDecayProfile,
            "progressMetricType" to progressMetricType,
            "strengthProgressionGroup" to strengthProgressionGroup,
            "hypertrophyVolumeGroup" to hypertrophyVolumeGroup,
            "mainLiftGroup" to mainLiftGroup,
            "accessoryContributionGroup" to accessoryContributionGroup,
            "badmintonTransferStrength" to badmintonTransferStrength,
            "courtMovementTypes" to courtMovementTypes,
            "badmintonSkillTargets" to badmintonSkillTargets,
            "stabilityDemandLevel" to stabilityDemandLevel,
            "mobilityDemandLevel" to mobilityDemandLevel,
            "analysisEligibility" to analysisEligibility
        ).filter { (_, value) -> value.isBlank() }
            .map { (field, _) -> field }

    private fun String.splitTokens(): Set<String> =
        split(',', '|', '/', ';')
            .map { value -> value.trim() }
            .filter { value -> value.isNotEmpty() && value != "NONE" }
            .toSet()

    private val knownStringParsingResidues = listOf(
        "ExerciseScreen.kt and CommonUi.kt: search UI only",
        "RecordScreen.kt: category UI branch for sports input",
        "TrainingRepository.kt: time-mode default set behavior",
        "SeedData.kt: legacy seed fallback heuristics, not used by new analysis mapper"
    )
}

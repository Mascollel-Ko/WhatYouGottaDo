package com.training.trackplanner.data

data class MetadataSanityIssue(
    val exerciseName: String,
    val field: String,
    val message: String,
    val severity: MetadataSanitySeverity
)

enum class MetadataSanitySeverity {
    ERROR,
    NEEDS_REVIEW
}

data class MetadataSanityCheckResult(
    val exerciseName: String,
    val issues: List<MetadataSanityIssue>
) {
    val hasErrors: Boolean
        get() = issues.any { issue -> issue.severity == MetadataSanitySeverity.ERROR }

    val needsReview: Boolean
        get() = issues.isNotEmpty()
}

data class MetadataSanityReport(
    val totalExerciseCount: Int,
    val confidenceCounts: Map<String, Int>,
    val issueCount: Int,
    val errorCount: Int,
    val needsReviewExerciseNames: List<String>,
    val lowConfidenceExerciseNames: List<String>,
    val issues: List<MetadataSanityIssue>
)

object MetadataSanityChecker {
    fun check(exercise: Exercise): MetadataSanityCheckResult {
        val issues = mutableListOf<MetadataSanityIssue>()
        exercise.weightFields().forEach { (field, value) ->
            if (value !in 0.0..1.0) {
                issues += exercise.issue(
                    field = field,
                    message = "weight must be between 0.0 and 1.0",
                    severity = MetadataSanitySeverity.ERROR
                )
            }
        }

        val movementCategories = exercise.movementCategory.splitTokens()
        val fatigueCategories = exercise.fatigueCategories.splitTokens()
        val badmintonRoles = exercise.badmintonTransferRoles.splitTokens()
        val baselineGroups = exercise.adaptiveBaselineGroups.splitTokens()
        val analysisEligibility = exercise.analysisEligibility.splitTokens()
        val courtMovementTypes = exercise.courtMovementTypes.splitTokens()
        val badmintonSkillTargets = exercise.badmintonSkillTargets.splitTokens()
        val balanceTags = exercise.balanceContributionTags.splitTokens()
        val jointStressTags = exercise.jointStressTags.splitTokens()

        exercise.requiredMetadataFields().forEach { (field, value) ->
            if (value.isBlank()) {
                issues += exercise.review(field, "required analysis metadata field is blank")
            }
        }

        if ("REACTIVE" in movementCategories && exercise.neuralSpeedWeight <= 0.0) {
            issues += exercise.review("neuralSpeedWeight", "REACTIVE movement must have neural speed load")
        }
        if ("NEURAL_SPEED" in fatigueCategories && exercise.neuralSpeedWeight <= 0.0) {
            issues += exercise.review("neuralSpeedWeight", "NEURAL_SPEED fatigue must have neural speed load")
        }
        if ("NEURAL_HEAVY" in fatigueCategories && exercise.neuralHeavyWeight <= 0.0) {
            issues += exercise.review("neuralHeavyWeight", "NEURAL_HEAVY fatigue must have heavy neural load")
        }
        if ("DECELERATION" in fatigueCategories && exercise.decelerationWeight <= 0.0) {
            issues += exercise.review("decelerationWeight", "DECELERATION fatigue must have deceleration load")
        }
        if ("ELASTIC_SSC" in fatigueCategories && exercise.elasticSscWeight <= 0.0) {
            issues += exercise.review("elasticSscWeight", "ELASTIC_SSC fatigue must have elastic SSC load")
        }
        if ("ROTATION_POWER" in fatigueCategories && exercise.rotationPowerWeight <= 0.0) {
            issues += exercise.review("rotationPowerWeight", "ROTATION_POWER fatigue must have rotation load")
        }
        if ("ANTI_ROTATION" in fatigueCategories && exercise.antiRotationWeight <= 0.0) {
            issues += exercise.review("antiRotationWeight", "ANTI_ROTATION fatigue must have anti-rotation load")
        }
        if ("OVERHEAD_REPETITION" in fatigueCategories && exercise.overheadSwingWeight <= 0.0) {
            issues += exercise.review("overheadSwingWeight", "OVERHEAD_REPETITION fatigue must have overhead load")
        }
        if ("GRIP_FOREARM" in fatigueCategories && exercise.gripLoadWeight <= 0.0) {
            issues += exercise.review("gripLoadWeight", "GRIP_FOREARM fatigue must have grip load")
        }
        if (badmintonRoles.anyCourtRole() && "BADMINTON_COURT" !in baselineGroups) {
            issues += exercise.review("adaptiveBaselineGroups", "court transfer roles require BADMINTON_COURT baseline")
        }
        if (
            exercise.badmintonTransferStrength == "DIRECT" &&
            (badmintonRoles.isEmpty() || badmintonRoles == setOf("NONE"))
        ) {
            issues += exercise.review("badmintonTransferRoles", "DIRECT badminton transfer requires roles")
        }
        if (
            exercise.badmintonTransferStrength in setOf("DIRECT", "SUPPORTIVE") &&
            "BADMINTON_TRANSFER" !in analysisEligibility
        ) {
            issues += exercise.review("analysisEligibility", "badminton transfer requires BADMINTON_TRANSFER eligibility")
        }
        if (
            courtMovementTypes.anyRealValue() &&
            !badmintonRoles.anyRealValue() &&
            !badmintonSkillTargets.anyRealValue()
        ) {
            issues += exercise.review("courtMovementTypes", "court movement requires badminton roles or skill targets")
        }
        if ("REACTION_RANDOM" in courtMovementTypes && exercise.neuralSpeedWeight <= 0.0) {
            issues += exercise.review("neuralSpeedWeight", "REACTION_RANDOM court work requires neural speed load")
        }
        if ("DECELERATION" in courtMovementTypes && exercise.decelerationWeight <= 0.0) {
            issues += exercise.review("decelerationWeight", "DECELERATION court work requires deceleration load")
        }
        if (
            "JUMP_LANDING" in courtMovementTypes &&
            exercise.elasticSscWeight <= 0.0 &&
            exercise.decelerationWeight <= 0.0
        ) {
            issues += exercise.review("elasticSscWeight", "JUMP_LANDING court work requires elastic or deceleration load")
        }
        if (exercise.badmintonTransferStrength == "NONE" && courtMovementTypes.anyRealValue()) {
            issues += exercise.review("badmintonTransferStrength", "NONE transfer cannot have court movement types")
        }
        if (exercise.progressMetricType == "ESTIMATED_1RM" && !exercise.estimated1RmEligible) {
            issues += exercise.review("estimated1RmEligible", "ESTIMATED_1RM requires estimated1RmEligible=true")
        }
        if (exercise.progressMetricType == "VOLUME_LOAD" && !exercise.volumeLoadEligible) {
            issues += exercise.review("volumeLoadEligible", "VOLUME_LOAD requires volumeLoadEligible=true")
        }
        if (exercise.progressMetricType == "NOT_PROGRESS_TARGET" && "STRENGTH_PROGRESS" in analysisEligibility) {
            issues += exercise.review("analysisEligibility", "NOT_PROGRESS_TARGET cannot include STRENGTH_PROGRESS")
        }
        if (
            exercise.trainingRole in setOf("PREHAB", "RECOVERY") &&
            exercise.progressMetricType !in setOf("NOT_PROGRESS_TARGET", "QUALITY_BASED")
        ) {
            issues += exercise.review("progressMetricType", "prehab/recovery should not use normal progress metrics")
        }
        if (exercise.trainingRole == "TEST" && "TEST_ONLY" !in analysisEligibility) {
            issues += exercise.review("analysisEligibility", "TEST role requires TEST_ONLY eligibility")
        }
        if (
            exercise.laterality in setOf("UNILATERAL", "ALTERNATING", "ASYMMETRIC") &&
            "UNILATERAL_LOWER" !in balanceTags &&
            "UNILATERAL_UPPER" !in balanceTags
        ) {
            issues += exercise.review("balanceContributionTags", "unilateral/asymmetric work requires unilateral balance tag")
        }
        if (exercise.movementPattern == "ROTATION" && "ROTATION" !in balanceTags) {
            issues += exercise.review("balanceContributionTags", "ROTATION movement requires ROTATION balance tag")
        }
        if (exercise.movementPattern == "ANTI_ROTATION" && "ANTI_ROTATION" !in balanceTags) {
            issues += exercise.review("balanceContributionTags", "ANTI_ROTATION movement requires ANTI_ROTATION balance tag")
        }
        if (
            exercise.trainingRole == "PREHAB" &&
            jointStressTags.isEmpty() &&
            "SHOULDER_DURABILITY" !in badmintonSkillTargets &&
            exercise.stabilityDemandLevel == "NONE" &&
            exercise.mobilityDemandLevel == "NONE"
        ) {
            issues += exercise.review("jointStressTags", "prehab should carry joint, mobility, or stability metadata")
        }
        if (analysisEligibility.isEmpty()) {
            issues += exercise.review("analysisEligibility", "analysis eligibility cannot be blank")
        }
        if (exercise.compoundType == "ISOLATION" && exercise.systemicLoadWeight >= 0.75) {
            issues += exercise.review("systemicLoadWeight", "isolation work has unusually high systemic load")
        }
        if (exercise.trainingRole == "PREHAB" && exercise.systemicLoadWeight >= 0.5) {
            issues += exercise.review("systemicLoadWeight", "prehab work has unusually high systemic load")
        }
        if (exercise.axialLoadLevel == "HIGH" && exercise.systemicLoadWeight == 0.0) {
            issues += exercise.review("systemicLoadWeight", "high axial load cannot have zero systemic load")
        }
        if (
            exercise.recoveryDecayProfile == "VERY_LONG" &&
            exercise.systemicLoadWeight < 0.5 &&
            exercise.decelerationWeight < 0.5
        ) {
            issues += exercise.review("recoveryDecayProfile", "very long decay needs systemic or deceleration support")
        }

        return MetadataSanityCheckResult(
            exerciseName = exercise.name,
            issues = issues
        )
    }

    fun checkAll(exercises: List<Exercise>): MetadataSanityReport {
        val results = exercises.map(::check)
        val issues = results.flatMap { result -> result.issues }
        val needsReviewNames = exercises
            .filter { exercise ->
                exercise.metadataConfidence in setOf("NEEDS_REVIEW", "LOW") ||
                    results.first { result -> result.exerciseName == exercise.name }.needsReview
            }
            .map { exercise -> exercise.name }
            .distinct()
            .sorted()
        return MetadataSanityReport(
            totalExerciseCount = exercises.size,
            confidenceCounts = exercises
                .groupingBy { exercise -> exercise.metadataConfidence }
                .eachCount()
                .toSortedMap(),
            issueCount = issues.size,
            errorCount = issues.count { issue -> issue.severity == MetadataSanitySeverity.ERROR },
            needsReviewExerciseNames = needsReviewNames,
            lowConfidenceExerciseNames = exercises
                .filter { exercise -> exercise.metadataConfidence in setOf("LOW", "NEEDS_REVIEW") }
                .map { exercise -> exercise.name }
                .sorted(),
            issues = issues
        )
    }

    private fun Exercise.weightFields(): List<Pair<String, Double>> =
        listOf(
            "systemicLoadWeight" to systemicLoadWeight,
            "neuralHeavyWeight" to neuralHeavyWeight,
            "neuralSpeedWeight" to neuralSpeedWeight,
            "localLoadWeight" to localLoadWeight,
            "decelerationWeight" to decelerationWeight,
            "elasticSscWeight" to elasticSscWeight,
            "rotationPowerWeight" to rotationPowerWeight,
            "antiRotationWeight" to antiRotationWeight,
            "overheadSwingWeight" to overheadSwingWeight,
            "gripLoadWeight" to gripLoadWeight
        )

    private fun Exercise.requiredMetadataFields(): List<Pair<String, String>> =
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
            "analysisEligibility" to analysisEligibility,
            "metadataConfidence" to metadataConfidence
        )

    private fun Exercise.review(field: String, message: String): MetadataSanityIssue =
        issue(field, message, MetadataSanitySeverity.NEEDS_REVIEW)

    private fun Exercise.issue(
        field: String,
        message: String,
        severity: MetadataSanitySeverity
    ): MetadataSanityIssue =
        MetadataSanityIssue(
            exerciseName = name,
            field = field,
            message = message,
            severity = severity
        )

    private fun Set<String>.anyCourtRole(): Boolean =
        any { role ->
            role in setOf(
                "FOOTWORK",
                "REACTION",
                "DECELERATION",
                "LUNGE_REACH",
                "JUMP_LANDING"
            )
        }

    private fun Set<String>.anyRealValue(): Boolean =
        any { value -> value.isNotBlank() && value != "NONE" }

    private fun String.splitTokens(): Set<String> =
        split(',', '|', '/', ';')
            .map { value -> value.trim().uppercase() }
            .filter { value -> value.isNotEmpty() && value != "NONE" }
            .toSet()
}

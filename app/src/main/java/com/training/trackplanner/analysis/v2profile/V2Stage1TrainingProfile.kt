package com.training.trackplanner.analysis.v2profile

import java.time.LocalDate

/** V2-1 is a descriptive history snapshot. It does not prescribe training or estimate safety. */
data class V2Stage1TrainingProfile(
    val asOf: LocalDate,
    /** Completed, date-valid sets through [asOf], including sets older than the displayed windows. */
    val completedSetCount: Int,
    val exclusions: V2Stage1Exclusions,
    val windows: List<V2Stage1WindowSummary>,
    /** All exercises with at least one completed resistance set in the 56-day window. */
    val exercises: List<V2Stage1ExerciseSummary>,
    val interpretation: V2Stage1Interpretation = V2Stage1Interpretation.DESCRIPTIVE_ONLY,
    val caveats: List<String> = DEFAULT_CAVEATS
) {
    /** V2-1B descriptive performance evidence; it never changes exercise retention or V2-1A equality. */
    val stage1B: V2Stage1BAnalysis by lazy { V2Stage1BAnalyzer.analyze(this) }

    val topExercises: List<V2Stage1ExerciseSummary>
        get() = exercises.take(20)

    companion object {
        val DEFAULT_CAVEATS = listOf(
            "Completed-set counts describe logged exposure; they are not muscle-stimulus, fatigue, recovery, or safe-volume estimates.",
            "Other-category sets may include drills. Court-session duration and intensity are not inferred from set rows.",
            "Logged load units and dumbbell conventions are not independently verified.",
            "Unperformed and incomplete sets are excluded. Missing load, repetitions, or RPE remain unknown."
        )
    }
}

enum class V2Stage1Interpretation { DESCRIPTIVE_ONLY }

data class V2Stage1Exclusions(
    val unconfirmedSets: Int = 0,
    val loadCalibrationPendingSets: Int = 0,
    val invalidDateCompletedSets: Int = 0,
    val futureCompletedSets: Int = 0,
    val olderThan56DaysCompletedSets: Int = 0
)

data class V2Stage1WindowSummary(
    val days: Int,
    val start: LocalDate,
    val end: LocalDate,
    val completedSetCount: Int,
    val resistanceSetCount: Int,
    val otherSetCount: Int,
    /** Set-count normalization only; this is not a stimulus or recovery estimate. */
    val resistanceWeeklySetEquivalent: Double,
    /** Set-count normalization only; this is not a stimulus or recovery estimate. */
    val allWeeklySetEquivalent: Double,
    val activeDays: Int,
    val resistanceDays: Int,
    val sessionCount: Int,
    val resistanceExerciseCount: Int,
    /** Exact contributing dates and exercise set counts are retained as calculation evidence. */
    val contributingDates: List<LocalDate>,
    val contributingResistanceDates: List<LocalDate>,
    /** Per-date counts support review of active days and resistance-day totals. */
    val dailyEvidence: List<V2Stage1DailyEvidence>,
    /** The date/key pairs used for session deduplication. */
    val sessionEvidence: List<V2Stage1SessionEvidence>,
    val resistanceExerciseFrequencies: List<V2Stage1ExerciseFrequency>
)

data class V2Stage1DailyEvidence(
    val date: LocalDate,
    val completedSetCount: Int,
    val resistanceSetCount: Int,
    val sessionKeys: List<String>
)

data class V2Stage1SessionEvidence(
    val date: LocalDate,
    val sessionKey: String,
    val sourceEntryIds: List<Long>
)

data class V2Stage1ExerciseFrequency(
    val stableKey: String,
    val name: String,
    val completedSetCount: Int
)

data class V2Stage1ExerciseSummary(
    val stableKey: String,
    val name: String,
    val category: String,
    /** Read-only snapshot of existing metadata; null means the current catalog has no matching row. */
    val metadata: V2Stage1ExerciseMetadata?,
    val exposureByWindow: Map<Int, V2Stage1ExerciseExposure>,
    val performance: V2Stage1ExercisePerformance,
    val maintenancePriorityEvidence: V2Stage1MaintenancePriorityEvidence
)

data class V2Stage1ExerciseMetadata(
    val activityKind: String,
    val planningEligibility: String,
    val movementPattern: String,
    val movementCategory: String,
    val primaryMuscles: String,
    val secondaryMuscles: String,
    val equipment: String,
    val equipmentTags: String,
    val metadataConfidence: String,
    val needsReview: Boolean
)

data class V2Stage1ExerciseExposure(
    val completedSetCount: Int,
    val sessionCount: Int,
    val trainingDays: Int,
    val lastCompletedDate: LocalDate?,
    val daysSinceLastCompleted: Int?
)

data class V2Stage1ExercisePerformance(
    /** Positive, explicitly logged load only. No load is inferred from a bodyweight or unknown row. */
    val latestExplicitLoad: V2Stage1RecordedSetEvidence?,
    val highestLoggedLoad: V2Stage1RecordedSetEvidence?,
    val recordedRpeSetCount: Int,
    val meanRecordedRpe: Double?,
    /** Raw evidence used by the summaries above, bounded to the last 56 calendar days. */
    val evidence: List<V2Stage1RecordedSetEvidence>
)

data class V2Stage1RecordedSetEvidence(
    val entryId: Long,
    val date: LocalDate,
    val sessionKey: String,
    val setIndex: Int,
    val loadState: String,
    /** Raw saved weight, including zero, is retained to distinguish it from a missing value. */
    val recordedWeightKg: Double?,
    /** The weight used for latest/peak summaries; null unless it is positive and explicitly logged. */
    val explicitPositiveWeightKg: Double?,
    val repetitions: Int?,
    val rpe: Double?
)

enum class V2Stage1MaintenanceFactor {
    GOAL_RELEVANCE,
    MOVEMENT_COVERAGE,
    PERFORMANCE_TREND,
    USER_PREFERENCE,
    SAFETY_REVIEW,
    REDUNDANCY,
    EQUIPMENT_AVAILABILITY,
    TIME_COST
}

enum class V2Stage1MaintenanceDecision { NOT_EVALUATED }

/**
 * Exposure is one input a future maintenance decision may use. V2-1 deliberately leaves the
 * decision unscored until goal, movement, performance, preference, safety, and practical factors
 * are available. Frequency alone can never produce a priority rank here.
 */
data class V2Stage1MaintenancePriorityEvidence(
    val exposureByWindow: Map<Int, V2Stage1ExerciseExposure>,
    val unevaluatedFactors: Set<V2Stage1MaintenanceFactor> = V2Stage1MaintenanceFactor.entries.toSet(),
    val decision: V2Stage1MaintenanceDecision = V2Stage1MaintenanceDecision.NOT_EVALUATED,
    val priorityScore: Double? = null
)

/** Flat read-only input keeps the analyzer independent from database writes and backup formats. */
data class V2Stage1SetObservation(
    val entryId: Long,
    val date: String,
    val sessionStableKey: String?,
    val backupSourceId: String?,
    val exerciseStableKey: String,
    val exerciseName: String,
    val category: String,
    val setIndex: Int,
    val confirmed: Boolean,
    val analysisEligibleCompletedSet: Boolean,
    val loadState: String,
    val weightKg: Double,
    val repetitions: Int,
    val rpe: Double?
)

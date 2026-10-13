package com.training.trackplanner.analysis.v2profile

import java.time.LocalDate

/** Descriptive performance evidence for V2-1B; never a prescription or safety decision. */
data class V2Stage1BAnalysis(
    val asOf: LocalDate,
    val exercises: List<V2Stage1BExerciseAnalysis>,
    val summary: V2Stage1BSummary = summarize(exercises)
) {
    companion object {
        private fun summarize(exercises: List<V2Stage1BExerciseAnalysis>) = V2Stage1BSummary(
            exerciseCount = exercises.size,
            scoredPerformanceCount = exercises.count { it.performance.latestScoredSet != null },
            trendAvailableCount = exercises.count { it.trend.direction != V2Stage1BTrendDirection.NO_TREND_EVIDENCE },
            trendDirections = exercises.groupingBy { it.trend.direction.name }.eachCount(),
            rosterRestrictedByStage1B = 0
        )
    }
}

data class V2Stage1BSummary(
    val exerciseCount: Int,
    val scoredPerformanceCount: Int,
    val trendAvailableCount: Int,
    val trendDirections: Map<String, Int>,
    val rosterRestrictedByStage1B: Int
)

data class V2Stage1BExerciseAnalysis(
    val stableKey: String,
    val name: String,
    val completedSets56d: Int,
    val sessionCount56d: Int,
    val trainingDays56d: Int,
    val lastPerformedDate: LocalDate?,
    val performance: V2Stage1BPerformance,
    val trend: V2Stage1BTrend,
    val warnings: List<String>,
    val rosterEligibilityFromThisStage: V2Stage1BRosterEligibility,
    val sessionEvidence: List<V2Stage1BSessionEvidence>,
    val dayEvidence: List<V2Stage1BDayEvidence>
)

enum class V2Stage1BPerformanceStatus { ESTIMATED_WITH_WARNINGS, RAW_RECORD_ONLY }

enum class V2Stage1BTrendDirection {
    IMPROVING,
    STABLE,
    DECLINING,
    NO_TREND_EVIDENCE
}

enum class V2Stage1BTrendConfidence { LOW, MODERATE, INSUFFICIENT }

enum class V2Stage1BRosterEligibility { NOT_RESTRICTED_BY_ANALYSIS_CONFIDENCE }

data class V2Stage1BPerformance(
    val status: V2Stage1BPerformanceStatus,
    val latestScoredSet: V2Stage1BScoredSetEvidence?,
    val latestRawSet: V2Stage1BSetEvidence?,
    val bestLoadIndex56d: V2Stage1BScoredSetEvidence?,
    val metric: String = "EXTERNAL_LOAD_INDEX_NOT_ACTUAL_1RM"
)

/** Retains the original V2-1A record beside the optional V2-1B derived score. */
data class V2Stage1BSetEvidence(
    val entryId: Long,
    val date: LocalDate,
    val sessionKey: String,
    val setIndex: Int,
    val loadState: String,
    val recordedWeightKg: Double?,
    val explicitPositiveWeightKg: Double?,
    val repetitions: Int?,
    val recordedRpe: Double?,
    val normalizedRpe: Double?,
    val rirAssumed: Double?,
    val loadIndex: Double?,
    val scoreExclusionReason: String?
)

data class V2Stage1BScoredSetEvidence(
    val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    val setIndex: Int,
    val rirAssumed: Double,
    val loadIndex: Double,
    val entryId: Long,
    val date: LocalDate,
    val sessionKey: String
)

data class V2Stage1BSessionEvidence(
    val date: LocalDate,
    val sessionKey: String,
    val best: V2Stage1BScoredSetEvidence?,
    val lastRecordedSet: V2Stage1BSetEvidence?,
    val completedSetCount: Int,
    val scoredSetCount: Int,
    val sets: List<V2Stage1BSetEvidence>
)

data class V2Stage1BDayEvidence(
    val date: LocalDate,
    val representativeSessionKey: String,
    val representativeLoadIndex: Double,
    val sessionKeys: List<String>
)

data class V2Stage1BTrend(
    val direction: V2Stage1BTrendDirection,
    val confidence: V2Stage1BTrendConfidence,
    val changePercent: Double?,
    val baselineIndex: Double?,
    val recentIndex: Double?,
    val baselineDates: List<LocalDate>,
    val recentDates: List<LocalDate>,
    val warnings: List<String>
)

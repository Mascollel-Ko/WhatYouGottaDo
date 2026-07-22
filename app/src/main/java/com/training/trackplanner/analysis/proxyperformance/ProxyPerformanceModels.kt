package com.training.trackplanner.analysis.proxyperformance

import java.time.LocalDate

enum class MajorLiftTarget(val labelKo: String) {
    BENCH_PRESS("벤치프레스"),
    SQUAT("스쿼트"),
    DEADLIFT("데드리프트")
}

enum class ProxyLatentFactor {
    PRESS_SHARED,
    HORIZONTAL_PRESS_SPECIFIC,
    KNEE_EXTENSION,
    HIP_EXTENSION_POSTERIOR_CHAIN,
    TRUNK_BRACING,
    BENCH_SPECIFIC,
    SQUAT_SPECIFIC,
    DEADLIFT_SPECIFIC
}

enum class ProxyModelVariant {
    M0_LOCF,
    M1_TARGET_ONLY,
    M2_SHARED_FACTORS,
    M3_SHARED_PLUS_TARGET_SPECIFIC
}

enum class ProxyPerformanceConfidence {
    INSUFFICIENT,
    LOW,
    MODERATE,
    HIGH
}

enum class ProxyObservationStatus {
    NO_OBSERVATION,
    PROXY_ONLY,
    DIRECT_OBSERVATION
}

enum class ProxyContributionDirection {
    POSITIVE,
    NEUTRAL,
    NEGATIVE
}

enum class ProxyPerformanceDiagnosticCode {
    NON_FINITE_INPUT,
    SINGULAR_INNOVATION_COVARIANCE,
    CHOLESKY_FAILED,
    BOUNDED_JITTER_APPLIED,
    OBSERVATION_SKIPPED,
    MODEL_FALLBACK_USED,
    INCOMPLETE_RECORD_COVERAGE
}

data class ProxyPerformanceDiagnostic(
    val code: ProxyPerformanceDiagnosticCode,
    val message: String,
    val date: LocalDate? = null,
    val workoutEntryId: Long? = null
)

data class ProxyPerformanceLoading(
    val factors: Map<ProxyLatentFactor, Double>,
    val reasons: List<String>,
    val metadataConfidence: Double
) {
    init {
        require(factors.values.all { value -> value.isFinite() && value in 0.0..1.0 })
        require(metadataConfidence.isFinite() && metadataConfidence in 0.0..1.0)
    }

    val isUsable: Boolean
        get() = factors.values.any { value -> value > 0.0 }
}

data class ProxyPerformanceObservation(
    val workoutEntryId: Long,
    val date: LocalDate,
    val performedAt: Long?,
    val displayOrder: Int,
    val exerciseId: Long,
    val exerciseStableKey: String,
    val exerciseName: String,
    val directTarget: MajorLiftTarget?,
    val canonicalE1rmKg: Double,
    val effortAdjustedPerformanceKg: Double,
    val observationVariance: Double,
    val quality: Double,
    val rpeAvailable: Boolean,
    val sourceSetIds: List<Long>,
    val loading: ProxyPerformanceLoading
)

data class ProxyContribution(
    val workoutEntryId: Long,
    val date: LocalDate,
    val exerciseStableKey: String,
    val exerciseName: String,
    val target: MajorLiftTarget,
    val loadingWeight: Double,
    val standardizedInnovation: Double,
    val direction: ProxyContributionDirection,
    val reasons: List<String>
)

data class ProxyModelBacktest(
    val variant: ProxyModelVariant,
    val maeKg: Double?,
    val rmseKg: Double?,
    val meanBiasKg: Double?,
    val medianAbsoluteErrorKg: Double?,
    val intervalCoverage80: Double?,
    val meanIntervalWidth80Kg: Double?,
    val gaussianLogPredictiveDensity: Double?,
    val directTestSessions: Int,
    val distinctProxyExercises: Int,
    val proxyObservations: Int
)

data class ProxyBacktestSummary(
    val selectedModel: ProxyModelVariant,
    val candidates: List<ProxyModelBacktest>,
    val selectionReason: String
)

data class MajorLiftPosteriorPoint(
    val weekStart: LocalDate,
    val priorMedianKg: Double?,
    val priorLow80Kg: Double?,
    val priorHigh80Kg: Double?,
    val posteriorMedianKg: Double?,
    val posteriorLow80Kg: Double?,
    val posteriorHigh80Kg: Double?,
    val actualCanonicalE1rmKg: Double?,
    val observationStatus: ProxyObservationStatus,
    val directObservationCountToDate: Int,
    val proxyObservationCountToDate: Int,
    val modelFingerprint: String
)

data class SessionExpectationComparison(
    val workoutEntryId: Long,
    val date: LocalDate,
    val target: MajorLiftTarget,
    val exerciseStableKey: String,
    val actualCanonicalE1rmKg: Double,
    val effortAdjustedPerformanceKg: Double,
    val expectedMedianKg: Double?,
    val expectedLow80Kg: Double?,
    val expectedHigh80Kg: Double?,
    val differenceKg: Double?,
    val predictivePercentile: Double?,
    val standardizedSurprise: Double?,
    val confidence: ProxyPerformanceConfidence,
    val evidence: List<ProxyContribution>,
    val modelFingerprint: String
)

data class MajorLiftProxySummary(
    val selectedModel: ProxyModelVariant,
    val weeklyPosterior: List<MajorLiftPosteriorPoint>,
    val sessionComparisons: List<SessionExpectationComparison>,
    val proxyContributions: List<ProxyContribution>,
    val backtest: ProxyBacktestSummary,
    val confidence: ProxyPerformanceConfidence,
    val latestDirectObservationDate: LocalDate?,
    val directObservationCount: Int,
    val proxyObservationCount: Int,
    val distinctProxyExerciseCount: Int,
    val modelFingerprint: String
)

data class ProxyPerformanceSummary(
    val modelVersion: String,
    val targets: Map<MajorLiftTarget, MajorLiftProxySummary>,
    val generatedAtDate: LocalDate,
    val diagnostics: List<ProxyPerformanceDiagnostic>
) {
    companion object {
        fun empty(date: LocalDate): ProxyPerformanceSummary = ProxyPerformanceSummary(
            modelVersion = ProxyPerformanceStateModel.MODEL_VERSION,
            targets = emptyMap(),
            generatedAtDate = date,
            diagnostics = emptyList()
        )
    }
}

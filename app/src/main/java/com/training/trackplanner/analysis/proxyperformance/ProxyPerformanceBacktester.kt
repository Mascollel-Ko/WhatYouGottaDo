package com.training.trackplanner.analysis.proxyperformance

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

internal data class ProxyBacktestEvaluation(
    val summary: ProxyBacktestSummary,
    val runs: Map<ProxyModelVariant, ProxyModelRun>
)

internal object ProxyPerformanceBacktester {
    fun evaluate(
        target: MajorLiftTarget,
        observations: List<ProxyPerformanceObservation>,
        generatedAtDate: LocalDate
    ): ProxyBacktestEvaluation {
        val runs = listOf(
            ProxyModelVariant.M1_TARGET_ONLY,
            ProxyModelVariant.M2_SHARED_FACTORS,
            ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC
        ).associateWith { variant ->
            ProxyPerformanceEstimator.runModel(target, variant, observations, generatedAtDate)
        }
        val candidates = listOf(locf(target, observations)) + runs.values.map(::metrics)
        val selection = select(candidates)
        return ProxyBacktestEvaluation(
            summary = ProxyBacktestSummary(
                selectedModel = selection.first,
                candidates = candidates,
                selectionReason = selection.second
            ),
            runs = runs
        )
    }

    private fun select(candidates: List<ProxyModelBacktest>): Pair<ProxyModelVariant, String> {
        val m1 = candidates.first { result -> result.variant == ProxyModelVariant.M1_TARGET_ONLY }
        val m2 = candidates.first { result -> result.variant == ProxyModelVariant.M2_SHARED_FACTORS }
        val m3 = candidates.first { result -> result.variant == ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC }
        val availableProxyCount = maxOf(m2.proxyObservations, m3.proxyObservations)
        val availableProxyExercises = maxOf(m2.distinctProxyExercises, m3.distinctProxyExercises)
        if (m1.directTestSessions < MIN_DIRECT_TESTS) {
            return ProxyModelVariant.M1_TARGET_ONLY to "Fewer than $MIN_DIRECT_TESTS prior-only direct tests; target-only fallback."
        }
        if (availableProxyCount < MIN_PROXY_OBSERVATIONS || availableProxyExercises < MIN_PROXY_EXERCISES) {
            return ProxyModelVariant.M1_TARGET_ONLY to "Proxy history is too sparse for validated cross-loading."
        }
        if (!materiallyImproves(m2, m1)) {
            return ProxyModelVariant.M1_TARGET_ONLY to "Shared proxy factors did not materially improve rolling target-only error."
        }
        if (
            m3.directTestSessions >= MIN_DIRECT_TESTS_FOR_M3 &&
            m3.proxyObservations >= MIN_PROXY_OBSERVATIONS_FOR_M3 &&
            m3.distinctProxyExercises >= MIN_PROXY_EXERCISES_FOR_M3 &&
            materiallyImproves(m3, m2) &&
            intervalsAcceptable(m3)
        ) {
            return ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC to
                "Full shared plus target-specific model improved rolling error with calibrated intervals."
        }
        return ProxyModelVariant.M2_SHARED_FACTORS to
            "Shared factors improved rolling error; full residual model lacked additional validated benefit."
    }

    private fun materiallyImproves(candidate: ProxyModelBacktest, baseline: ProxyModelBacktest): Boolean {
        val candidateMae = candidate.maeKg ?: return false
        val baselineMae = baseline.maeKg ?: return false
        if (!intervalsAcceptable(candidate)) return false
        val candidateWidth = candidate.meanIntervalWidth80Kg ?: return false
        val baselineWidth = baseline.meanIntervalWidth80Kg ?: return false
        val candidateDensity = candidate.gaussianLogPredictiveDensity ?: return false
        val baselineDensity = baseline.gaussianLogPredictiveDensity ?: return false
        return candidateMae <= baselineMae * MAX_RELATIVE_MAE &&
            (candidate.rmseKg ?: Double.POSITIVE_INFINITY) <=
            (baseline.rmseKg ?: Double.POSITIVE_INFINITY) * MAX_RELATIVE_RMSE &&
            candidateWidth <= baselineWidth * MAX_RELATIVE_INTERVAL_WIDTH &&
            candidateDensity >= baselineDensity - MAX_LOG_DENSITY_REGRESSION
    }

    private fun intervalsAcceptable(result: ProxyModelBacktest): Boolean {
        if (result.directTestSessions < MIN_TESTS_FOR_COVERAGE_GATE) return true
        val coverage = result.intervalCoverage80 ?: return false
        return coverage in MIN_ACCEPTABLE_COVERAGE..1.0
    }

    private fun metrics(run: ProxyModelRun): ProxyModelBacktest {
        val samples = run.sessionComparisons.mapNotNull { comparison ->
            val expected = comparison.expectedMedianKg ?: return@mapNotNull null
            val low = comparison.expectedLow80Kg ?: return@mapNotNull null
            val high = comparison.expectedHigh80Kg ?: return@mapNotNull null
            BacktestSample(comparison.effortAdjustedPerformanceKg, expected, low, high)
        }
        return summarize(
            variant = run.variant,
            samples = samples,
            distinctProxyExercises = run.distinctProxyExerciseCount,
            proxyObservations = run.proxyObservationCount
        )
    }

    private fun locf(
        target: MajorLiftTarget,
        observations: List<ProxyPerformanceObservation>
    ): ProxyModelBacktest {
        val direct = observations
            .filter { observation -> observation.directTarget == target }
            .sortedWith(
                compareBy<ProxyPerformanceObservation>(ProxyPerformanceObservation::date)
                    .thenBy { observation -> observation.performedAt ?: Long.MIN_VALUE }
                    .thenBy(ProxyPerformanceObservation::displayOrder)
                    .thenBy(ProxyPerformanceObservation::workoutEntryId)
            )
        val errors = mutableListOf<Double>()
        val samples = mutableListOf<BacktestSample>()
        var previous: Double? = null
        direct.forEach { observation ->
            previous?.let { expected ->
                val scale = robustScale(errors).coerceAtLeast(expected * DEFAULT_LOCF_RELATIVE_SD)
                samples += BacktestSample(
                    actual = observation.effortAdjustedPerformanceKg,
                    expected = expected,
                    low80 = (expected - Z_80 * scale).coerceAtLeast(0.0),
                    high80 = expected + Z_80 * scale
                )
                errors += observation.effortAdjustedPerformanceKg - expected
            }
            previous = observation.effortAdjustedPerformanceKg
        }
        return summarize(
            variant = ProxyModelVariant.M0_LOCF,
            samples = samples,
            distinctProxyExercises = 0,
            proxyObservations = 0
        )
    }

    private fun summarize(
        variant: ProxyModelVariant,
        samples: List<BacktestSample>,
        distinctProxyExercises: Int,
        proxyObservations: Int
    ): ProxyModelBacktest {
        if (samples.isEmpty()) {
            return ProxyModelBacktest(
                variant = variant,
                maeKg = null,
                rmseKg = null,
                meanBiasKg = null,
                medianAbsoluteErrorKg = null,
                intervalCoverage80 = null,
                meanIntervalWidth80Kg = null,
                gaussianLogPredictiveDensity = null,
                directTestSessions = 0,
                distinctProxyExercises = distinctProxyExercises,
                proxyObservations = proxyObservations
            )
        }
        val errors = samples.map { sample -> sample.actual - sample.expected }
        val absoluteErrors = errors.map(::abs)
        val logDensity = samples.map { sample ->
            val sd = ((sample.high80 - sample.low80) / (2.0 * Z_80)).coerceAtLeast(MIN_SD_KG)
            val error = sample.actual - sample.expected
            -0.5 * ln(2.0 * PI * sd.pow(2)) - 0.5 * (error / sd).pow(2)
        }.average()
        return ProxyModelBacktest(
            variant = variant,
            maeKg = absoluteErrors.average(),
            rmseKg = sqrt(errors.map { error -> error * error }.average()),
            meanBiasKg = errors.average(),
            medianAbsoluteErrorKg = median(absoluteErrors),
            intervalCoverage80 = samples.count { sample -> sample.actual in sample.low80..sample.high80 }.toDouble() / samples.size,
            meanIntervalWidth80Kg = samples.map { sample -> sample.high80 - sample.low80 }.average(),
            gaussianLogPredictiveDensity = logDensity,
            directTestSessions = samples.size,
            distinctProxyExercises = distinctProxyExercises,
            proxyObservations = proxyObservations
        )
    }

    private fun robustScale(errors: List<Double>): Double {
        if (errors.isEmpty()) return 0.0
        val center = median(errors)
        return 1.4826 * median(errors.map { error -> abs(error - center) })
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private data class BacktestSample(
        val actual: Double,
        val expected: Double,
        val low80: Double,
        val high80: Double
    )

    private const val MIN_DIRECT_TESTS = 3
    private const val MIN_PROXY_OBSERVATIONS = 4
    private const val MIN_PROXY_EXERCISES = 2
    private const val MIN_DIRECT_TESTS_FOR_M3 = 6
    private const val MIN_PROXY_OBSERVATIONS_FOR_M3 = 8
    private const val MIN_PROXY_EXERCISES_FOR_M3 = 3
    private const val MIN_TESTS_FOR_COVERAGE_GATE = 5
    private const val MIN_ACCEPTABLE_COVERAGE = 0.40
    private const val MAX_RELATIVE_MAE = 0.97
    private const val MAX_RELATIVE_RMSE = 1.02
    private const val MAX_RELATIVE_INTERVAL_WIDTH = 1.75
    private const val MAX_LOG_DENSITY_REGRESSION = 0.50
    private const val DEFAULT_LOCF_RELATIVE_SD = 0.12
    private const val MIN_SD_KG = 0.25
    private const val Z_80 = 1.2815515655446004
}

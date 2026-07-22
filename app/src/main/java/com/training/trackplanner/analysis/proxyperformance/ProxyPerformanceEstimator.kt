package com.training.trackplanner.analysis.proxyperformance

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import org.apache.commons.math3.distribution.NormalDistribution

internal data class ProxyModelRun(
    val variant: ProxyModelVariant,
    val weeklyPosterior: List<MajorLiftPosteriorPoint>,
    val sessionComparisons: List<SessionExpectationComparison>,
    val proxyContributions: List<ProxyContribution>,
    val diagnostics: List<ProxyPerformanceDiagnostic>,
    val directObservationCount: Int,
    val proxyObservationCount: Int,
    val distinctProxyExerciseCount: Int,
    val latestDirectObservationDate: LocalDate?,
    val fingerprint: String
)

internal object ProxyPerformanceEstimator {
    fun build(
        observations: List<ProxyPerformanceObservation>,
        generatedAtDate: LocalDate,
        initialDiagnostics: List<ProxyPerformanceDiagnostic> = emptyList()
    ): ProxyPerformanceSummary {
        val selectedDiagnostics = mutableListOf<ProxyPerformanceDiagnostic>()
        val fallbackDiagnostics = mutableListOf<ProxyPerformanceDiagnostic>()
        val targets = MajorLiftTarget.entries.associateWith { target ->
            val evaluation = ProxyPerformanceBacktester.evaluate(target, observations, generatedAtDate)
            val selectedRun = evaluation.runs.getValue(evaluation.summary.selectedModel)
            val availableEvidence = evaluation.runs.getValue(ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC)
            selectedDiagnostics += selectedRun.diagnostics
            if (selectedRun.variant == ProxyModelVariant.M1_TARGET_ONLY && availableEvidence.proxyObservationCount > 0) {
                fallbackDiagnostics += ProxyPerformanceDiagnostic(
                    ProxyPerformanceDiagnosticCode.MODEL_FALLBACK_USED,
                    "Proxy evidence did not pass the rolling selection policy for ${target.name}; target-only model retained."
                )
            }
            MajorLiftProxySummary(
                selectedModel = selectedRun.variant,
                weeklyPosterior = selectedRun.weeklyPosterior,
                sessionComparisons = selectedRun.sessionComparisons.asReversed(),
                proxyContributions = selectedRun.proxyContributions.asReversed(),
                backtest = evaluation.summary,
                confidence = confidenceFor(selectedRun),
                latestDirectObservationDate = selectedRun.latestDirectObservationDate,
                directObservationCount = selectedRun.directObservationCount,
                proxyObservationCount = availableEvidence.proxyObservationCount,
                distinctProxyExerciseCount = availableEvidence.distinctProxyExerciseCount,
                modelFingerprint = selectedRun.fingerprint
            )
        }
        return ProxyPerformanceSummary(
            modelVersion = ProxyPerformanceStateModel.MODEL_VERSION,
            targets = targets,
            generatedAtDate = generatedAtDate,
            diagnostics = initialDiagnostics + selectedDiagnostics + fallbackDiagnostics
        )
    }

    fun runModel(
        target: MajorLiftTarget,
        variant: ProxyModelVariant,
        observations: List<ProxyPerformanceObservation>,
        generatedAtDate: LocalDate
    ): ProxyModelRun {
        require(variant != ProxyModelVariant.M0_LOCF)
        val relevant = observations
            .filter { observation ->
                observation.directTarget == target ||
                    (variant != ProxyModelVariant.M1_TARGET_ONLY &&
                        ProxyPerformanceLoadingBuilder.targetAffinity(observation.loading, target) > 0.0)
            }
            .sortedWith(
                compareBy<ProxyPerformanceObservation>(ProxyPerformanceObservation::date)
                    .thenBy { observation -> observation.performedAt ?: Long.MIN_VALUE }
                    .thenBy(ProxyPerformanceObservation::displayOrder)
                    .thenBy(ProxyPerformanceObservation::workoutEntryId)
                    .thenBy(ProxyPerformanceObservation::exerciseStableKey)
            )
        val fingerprint = fingerprint(target, variant, relevant)
        if (relevant.isEmpty()) {
            return ProxyModelRun(
                variant = variant,
                weeklyPosterior = emptyList(),
                sessionComparisons = emptyList(),
                proxyContributions = emptyList(),
                diagnostics = emptyList(),
                directObservationCount = 0,
                proxyObservationCount = 0,
                distinctProxyExerciseCount = 0,
                latestDirectObservationDate = null,
                fingerprint = fingerprint
            )
        }
        val config = ProxyPerformanceStateModel.config(target, variant)
        val filter = ProxyPerformanceKalmanFilter(config)
        var state = filter.initialState()
        val baselines = OnlineExerciseBaselines()
        val comparisons = mutableListOf<SessionExpectationComparison>()
        val contributions = mutableListOf<ProxyContribution>()
        val diagnostics = mutableListOf<ProxyPerformanceDiagnostic>()
        val weekly = mutableListOf<MajorLiftPosteriorPoint>()
        var directCount = 0
        var proxyCount = 0
        val proxyExerciseKeys = linkedSetOf<String>()
        var latestDirectDate: LocalDate? = null
        var targetBaselineKey: String? = null
        val start = relevant.first().date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = maxOf(generatedAtDate, relevant.last().date)
        val byDate = relevant.groupBy(ProxyPerformanceObservation::date)
        var currentDate = start
        var currentWeek: WeekAccumulator? = null
        while (!currentDate.isAfter(end)) {
            if (currentDate != start) {
                val prediction = filter.predict(state, 1L)
                state = prediction.state
                diagnostics += prediction.diagnostics
            }
            if (currentDate.dayOfWeek == DayOfWeek.MONDAY || currentWeek == null) {
                val prior = estimateTarget(
                    target = target,
                    variant = variant,
                    state = state,
                    baseline = targetBaselineKey?.let(baselines::stats),
                    predictive = false
                )
                currentWeek = WeekAccumulator(currentDate, prior)
            }
            byDate[currentDate].orEmpty().forEach { observation ->
                val isDirect = observation.directTarget == target
                val baselineBefore = baselines.stats(observation.exerciseStableKey)
                val directBaselineBefore = if (isDirect) baselineBefore else targetBaselineKey?.let(baselines::stats)
                val expected = if (isDirect) {
                    estimateTarget(target, variant, state, directBaselineBefore, predictive = true)
                } else {
                    null
                }
                if (isDirect) {
                    comparisons += comparison(
                        target = target,
                        observation = observation,
                        expected = expected,
                        confidence = confidenceForCounts(directCount, proxyCount),
                        evidence = contributions.takeLast(MAX_EVIDENCE_COUNT),
                        fingerprint = fingerprint
                    )
                    directCount += 1
                    latestDirectDate = observation.date
                    targetBaselineKey = observation.exerciseStableKey
                    currentWeek?.actualCanonicalE1rmKg = maxOf(
                        currentWeek?.actualCanonicalE1rmKg ?: 0.0,
                        observation.canonicalE1rmKg
                    )
                    currentWeek?.directObservations = currentWeek?.directObservations?.plus(1) ?: 1
                } else {
                    proxyCount += 1
                    proxyExerciseKeys += observation.exerciseStableKey
                    currentWeek?.proxyObservations = currentWeek?.proxyObservations?.plus(1) ?: 1
                }
                if (baselineBefore != null) {
                    val normalized = baselineBefore.normalize(observation.effortAdjustedPerformanceKg)
                    val loading = config.factors.map { factor -> observation.loading.factors.getOrDefault(factor, 0.0) }
                        .toDoubleArray()
                    if (normalized != null && loading.any { value -> value > 0.0 }) {
                        val priorSignal = loading.indices.sumOf { index -> loading[index] * state.mean.getEntry(index) }
                        val residualMagnitude = abs(normalized - priorSignal)
                        val robustVarianceMultiplier = (
                            1.0 + (residualMagnitude - 1.0).coerceAtLeast(0.0).let { excess -> excess * excess }
                            ).coerceAtMost(MAX_ROBUST_VARIANCE_MULTIPLIER)
                        val step = filter.update(
                            state = state,
                            observation = normalized,
                            loading = loading,
                            observationVariance = observation.observationVariance * robustVarianceMultiplier,
                            date = observation.date,
                            workoutEntryId = observation.workoutEntryId
                        )
                        state = step.state
                        diagnostics += step.diagnostics
                        if (step.updated && !isDirect) {
                            contributions += contribution(
                                target = target,
                                observation = observation,
                                innovation = step.innovation,
                                innovationVariance = step.innovationVariance
                            )
                        }
                    }
                }
                baselines.add(observation.exerciseStableKey, observation.effortAdjustedPerformanceKg)
            }
            if (currentDate.dayOfWeek == DayOfWeek.SUNDAY || currentDate == end) {
                val week = checkNotNull(currentWeek)
                val posterior = estimateTarget(
                    target = target,
                    variant = variant,
                    state = state,
                    baseline = targetBaselineKey?.let(baselines::stats),
                    predictive = false
                )
                weekly += week.toPoint(
                    posterior = posterior,
                    directObservationCount = directCount,
                    proxyObservationCount = proxyCount,
                    fingerprint = fingerprint
                )
            }
            currentDate = currentDate.plusDays(1)
        }
        return ProxyModelRun(
            variant = variant,
            weeklyPosterior = weekly,
            sessionComparisons = comparisons,
            proxyContributions = contributions,
            diagnostics = diagnostics,
            directObservationCount = directCount,
            proxyObservationCount = proxyCount,
            distinctProxyExerciseCount = proxyExerciseKeys.size,
            latestDirectObservationDate = latestDirectDate,
            fingerprint = fingerprint
        )
    }

    private fun comparison(
        target: MajorLiftTarget,
        observation: ProxyPerformanceObservation,
        expected: TargetEstimate?,
        confidence: ProxyPerformanceConfidence,
        evidence: List<ProxyContribution>,
        fingerprint: String
    ): SessionExpectationComparison {
        val surprise = expected?.standardized(observation.effortAdjustedPerformanceKg)
        val percentile = surprise?.let { value -> NormalDistribution().cumulativeProbability(value) }
        return SessionExpectationComparison(
            workoutEntryId = observation.workoutEntryId,
            date = observation.date,
            target = target,
            exerciseStableKey = observation.exerciseStableKey,
            actualCanonicalE1rmKg = observation.canonicalE1rmKg,
            effortAdjustedPerformanceKg = observation.effortAdjustedPerformanceKg,
            expectedMedianKg = expected?.medianKg,
            expectedLow80Kg = expected?.low80Kg,
            expectedHigh80Kg = expected?.high80Kg,
            differenceKg = expected?.let { value -> observation.effortAdjustedPerformanceKg - value.medianKg },
            predictivePercentile = percentile,
            standardizedSurprise = surprise,
            confidence = confidence,
            evidence = evidence.sortedByDescending { contribution ->
                abs(contribution.standardizedInnovation) * contribution.loadingWeight
            }.take(MAX_EVIDENCE_COUNT),
            modelFingerprint = fingerprint
        )
    }

    private fun contribution(
        target: MajorLiftTarget,
        observation: ProxyPerformanceObservation,
        innovation: Double?,
        innovationVariance: Double?
    ): ProxyContribution {
        val standardized = if (innovation != null && innovationVariance != null && innovationVariance > 0.0) {
            innovation / sqrt(innovationVariance)
        } else {
            0.0
        }
        return ProxyContribution(
            workoutEntryId = observation.workoutEntryId,
            date = observation.date,
            exerciseStableKey = observation.exerciseStableKey,
            exerciseName = observation.exerciseName,
            target = target,
            loadingWeight = ProxyPerformanceLoadingBuilder.targetAffinity(observation.loading, target),
            standardizedInnovation = standardized,
            direction = when {
                standardized > CONTRIBUTION_THRESHOLD -> ProxyContributionDirection.POSITIVE
                standardized < -CONTRIBUTION_THRESHOLD -> ProxyContributionDirection.NEGATIVE
                else -> ProxyContributionDirection.NEUTRAL
            },
            reasons = observation.loading.reasons
        )
    }

    private fun estimateTarget(
        target: MajorLiftTarget,
        variant: ProxyModelVariant,
        state: ProxyKalmanState,
        baseline: ExerciseBaselineStats?,
        predictive: Boolean
    ): TargetEstimate? {
        baseline ?: return null
        val config = ProxyPerformanceStateModel.config(target, variant)
        val loadingByFactor = ProxyPerformanceLoadingBuilder.targetLoading(target, variant)
        val loading = config.factors.map { factor -> loadingByFactor.getOrDefault(factor, 0.0) }.toDoubleArray()
        if (loading.all { value -> value == 0.0 }) return null
        val h = org.apache.commons.math3.linear.ArrayRealVector(loading, false)
        val normalizedMean = h.dotProduct(state.mean)
        val normalizedVariance = h.dotProduct(state.covariance.operate(h)) +
            if (predictive) PREDICTIVE_OBSERVATION_VARIANCE else 0.0
        if (!normalizedMean.isFinite() || !normalizedVariance.isFinite() || normalizedVariance < 0.0) return null
        val meanLog = baseline.centerLog + baseline.scaleLog * normalizedMean
        val sdLog = baseline.scaleLog * sqrt(normalizedVariance.coerceAtLeast(MIN_VARIANCE))
        return TargetEstimate(
            medianKg = exp(meanLog),
            low80Kg = exp(meanLog - Z_80 * sdLog),
            high80Kg = exp(meanLog + Z_80 * sdLog),
            meanLog = meanLog,
            sdLog = sdLog.coerceAtLeast(MIN_LOG_SD)
        )
    }

    private fun confidenceFor(run: ProxyModelRun): ProxyPerformanceConfidence =
        confidenceForCounts(run.directObservationCount, run.proxyObservationCount)

    private fun confidenceForCounts(directCount: Int, proxyCount: Int): ProxyPerformanceConfidence = when {
        directCount >= 8 && proxyCount >= 8 -> ProxyPerformanceConfidence.HIGH
        directCount >= 4 && proxyCount >= 4 -> ProxyPerformanceConfidence.MODERATE
        directCount >= 2 -> ProxyPerformanceConfidence.LOW
        else -> ProxyPerformanceConfidence.INSUFFICIENT
    }

    private fun fingerprint(
        target: MajorLiftTarget,
        variant: ProxyModelVariant,
        observations: List<ProxyPerformanceObservation>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun append(value: String) {
            digest.update(value.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
        }
        append(ProxyPerformanceStateModel.MODEL_VERSION)
        append(target.name)
        append(variant.name)
        observations.forEach { observation ->
            append(observation.date.toString())
            append(observation.performedAt?.toString().orEmpty())
            append(observation.displayOrder.toString())
            append(observation.workoutEntryId.toString())
            append(observation.exerciseStableKey)
            append(observation.effortAdjustedPerformanceKg.toBits().toString())
            append(observation.observationVariance.toBits().toString())
            ProxyLatentFactor.entries.forEach { factor ->
                append(observation.loading.factors.getOrDefault(factor, 0.0).toBits().toString())
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private class OnlineExerciseBaselines {
        private val valuesByStableKey = mutableMapOf<String, ArrayDeque<Double>>()

        fun stats(stableKey: String): ExerciseBaselineStats? {
            val values = valuesByStableKey[stableKey]?.toList().orEmpty()
            if (values.isEmpty()) return null
            val center = median(values)
            val mad = median(values.map { value -> abs(value - center) })
            return ExerciseBaselineStats(
                centerLog = center,
                scaleLog = (MAD_TO_SD * mad).coerceIn(MIN_BASELINE_SCALE, MAX_BASELINE_SCALE),
                count = values.size
            )
        }

        fun add(stableKey: String, performanceKg: Double) {
            if (performanceKg.isFinite() && performanceKg > 0.0) {
                val values = valuesByStableKey.getOrPut(stableKey) { ArrayDeque() }
                values.addLast(ln(performanceKg))
                if (values.size > MAX_BASELINE_HISTORY) values.removeFirst()
            }
        }

        private fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
        }
    }

    private data class ExerciseBaselineStats(
        val centerLog: Double,
        val scaleLog: Double,
        val count: Int
    ) {
        fun normalize(performanceKg: Double): Double? =
            performanceKg.takeIf { value -> value.isFinite() && value > 0.0 }
                ?.let { value -> (ln(value) - centerLog) / scaleLog }
    }

    private data class TargetEstimate(
        val medianKg: Double,
        val low80Kg: Double,
        val high80Kg: Double,
        val meanLog: Double,
        val sdLog: Double
    ) {
        fun standardized(performanceKg: Double): Double? =
            performanceKg.takeIf { value -> value.isFinite() && value > 0.0 }
                ?.let { value -> (ln(value) - meanLog) / sdLog }
    }

    private data class WeekAccumulator(
        val weekStart: LocalDate,
        val prior: TargetEstimate?,
        var actualCanonicalE1rmKg: Double? = null,
        var directObservations: Int = 0,
        var proxyObservations: Int = 0
    ) {
        fun toPoint(
            posterior: TargetEstimate?,
            directObservationCount: Int,
            proxyObservationCount: Int,
            fingerprint: String
        ): MajorLiftPosteriorPoint = MajorLiftPosteriorPoint(
            weekStart = weekStart,
            priorMedianKg = prior?.medianKg,
            priorLow80Kg = prior?.low80Kg,
            priorHigh80Kg = prior?.high80Kg,
            posteriorMedianKg = posterior?.medianKg,
            posteriorLow80Kg = posterior?.low80Kg,
            posteriorHigh80Kg = posterior?.high80Kg,
            actualCanonicalE1rmKg = actualCanonicalE1rmKg,
            observationStatus = when {
                directObservations > 0 -> ProxyObservationStatus.DIRECT_OBSERVATION
                proxyObservations > 0 -> ProxyObservationStatus.PROXY_ONLY
                else -> ProxyObservationStatus.NO_OBSERVATION
            },
            directObservationCountToDate = directObservationCount,
            proxyObservationCountToDate = proxyObservationCount,
            modelFingerprint = fingerprint
        )
    }

    private const val Z_80 = 1.2815515655446004
    private const val PREDICTIVE_OBSERVATION_VARIANCE = 0.16
    private const val MIN_VARIANCE = 1e-12
    private const val MIN_LOG_SD = 1e-6
    private const val MIN_BASELINE_SCALE = 0.04
    private const val MAX_BASELINE_SCALE = 0.35
    private const val MAD_TO_SD = 1.4826
    private const val CONTRIBUTION_THRESHOLD = 0.25
    private const val MAX_EVIDENCE_COUNT = 4
    private const val MAX_BASELINE_HISTORY = 64
    private const val MAX_ROBUST_VARIANCE_MULTIPLIER = 25.0
}

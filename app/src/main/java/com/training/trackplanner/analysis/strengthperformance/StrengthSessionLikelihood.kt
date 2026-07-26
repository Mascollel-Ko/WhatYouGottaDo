package com.training.trackplanner.analysis.strengthperformance

import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.analysis.strengthperformance.curve.ResolvedRepetitionCurve
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import org.apache.commons.math3.analysis.integration.gauss.GaussIntegratorFactory
import org.apache.commons.math3.distribution.NormalDistribution

enum class StrengthObservationType {
    DIRECT_1RM,
    STRONG_NRM,
    RPE_MIXTURE_OBSERVATION,
    MISSING_RPE_LOWER_CENSORED,
    FAILURE_UPPER_CENSORED,
    UNSUPPORTED_REPETITION_RANGE,
    UNRESOLVED_LOAD,
    EXCLUDED_INVALID_SET
}

data class StrengthLikelihoodComponent(
    val rir: Int,
    val probability: Double,
    val logCenter: Double,
    val logVariance: Double
)

sealed interface StrengthSetLikelihood {
    val isProper: Boolean
    fun logValueAt(logCapacity: Double): Double

    data class GaussianMixture(
        val components: List<StrengthLikelihoodComponent>,
        val supportedMixtureMass: Double
    ) : StrengthSetLikelihood {
        init {
            require(components.isNotEmpty())
            require(components.all {
                it.rir >= 0 && it.probability > 0.0 && it.logCenter.isFinite() &&
                    it.logVariance.isFinite() && it.logVariance > 0.0
            })
            require(abs(components.sumOf(StrengthLikelihoodComponent::probability) - 1.0) <= 1e-9)
            require(supportedMixtureMass in 0.0..1.0)
        }

        override val isProper: Boolean = true

        override fun logValueAt(logCapacity: Double): Double = logSumExp(
            components.map { component ->
                ln(component.probability) + normalLogDensity(
                    logCapacity,
                    component.logCenter,
                    component.logVariance
                )
            }
        )
    }

    data class LowerCensored(
        val logThreshold: Double,
        val logStandardDeviation: Double
    ) : StrengthSetLikelihood {
        override val isProper: Boolean = false

        override fun logValueAt(logCapacity: Double): Double =
            ln(STANDARD_NORMAL.cumulativeProbability(
                (logCapacity - logThreshold) / logStandardDeviation
            ).coerceAtLeast(MIN_PROBABILITY))
    }

    data class UpperCensored(
        val logThreshold: Double,
        val logStandardDeviation: Double
    ) : StrengthSetLikelihood {
        override val isProper: Boolean = false

        override fun logValueAt(logCapacity: Double): Double =
            ln(STANDARD_NORMAL.cumulativeProbability(
                (logThreshold - logCapacity) / logStandardDeviation
            ).coerceAtLeast(MIN_PROBABILITY))
    }
}

data class StrengthSetEvidence(
    val setId: Long,
    val setIndex: Int,
    val repetitions: Int,
    val rpe: Double?,
    val resolvedLoad: ResolvedStrengthLoad,
    val curveRelativeLoad: Double,
    val capacityCenterKg: Double,
    val lowerBoundKg: Double,
    val logVariance: Double,
    val observationType: StrengthObservationType,
    val evidenceFingerprint: String,
    val likelihood: StrengthSetLikelihood,
    val supportedMixtureMass: Double = 1.0
) {
    val isStrong: Boolean get() = observationType in setOf(
        StrengthObservationType.DIRECT_1RM,
        StrengthObservationType.STRONG_NRM
    )

    val isFailure: Boolean get() = observationType == StrengthObservationType.FAILURE_UPPER_CENSORED
    val isTwoSided: Boolean get() = likelihood.isProper
}

data class StrengthSetLikelihoodBuildResult(
    val evidence: StrengthSetEvidence? = null,
    val exclusionType: StrengthObservationType? = null,
    val diagnostic: String? = null
) {
    init {
        require((evidence != null) xor (exclusionType != null))
    }
}

class StrengthExerciseSessionLikelihood(
    val setEvidence: List<StrengthSetEvidence>,
    val dayEffectLogStandardDeviation: Double = DEFAULT_DAY_EFFECT_LOG_SD
) {
    init {
        require(setEvidence.isNotEmpty())
        require(dayEffectLogStandardDeviation.isFinite() && dayEffectLogStandardDeviation > 0.0)
    }

    val hasProperLikelihood: Boolean
        get() = setEvidence.any(StrengthSetEvidence::isTwoSided)

    fun asScalarLikelihood(properOnly: Boolean = false): ScalarGridLikelihood {
        val evidence = if (properOnly) setEvidence.filter(StrengthSetEvidence::isTwoSided) else setEvidence
        require(evidence.isNotEmpty())
        return ScalarGridLikelihood(
            support = evidence.flatMap { item ->
                when (val likelihood = item.likelihood) {
                    is StrengthSetLikelihood.GaussianMixture -> likelihood.components.map { component ->
                        ScalarLikelihoodSupport(
                            component.logCenter,
                            sqrt(component.logVariance + dayEffectLogStandardDeviation.pow(2))
                        )
                    }
                    is StrengthSetLikelihood.LowerCensored -> listOf(
                        ScalarLikelihoodSupport(
                            likelihood.logThreshold,
                            likelihood.logStandardDeviation + dayEffectLogStandardDeviation
                        )
                    )
                    is StrengthSetLikelihood.UpperCensored -> listOf(
                        ScalarLikelihoodSupport(
                            likelihood.logThreshold,
                            likelihood.logStandardDeviation + dayEffectLogStandardDeviation
                        )
                    )
                }
            },
            evaluator = { logCapacity -> integratedLogLikelihood(logCapacity, evidence) }
        )
    }

    private fun integratedLogLikelihood(
        logCapacity: Double,
        evidence: List<StrengthSetEvidence>
    ): Double {
        val terms = GH_POINTS.indices.map { index ->
            val sessionEffect = SQRT_TWO * dayEffectLogStandardDeviation * GH_POINTS[index]
            ln(GH_WEIGHTS[index]) - LOG_SQRT_PI +
                evidence.sumOf { item -> item.likelihood.logValueAt(logCapacity + sessionEffect) }
        }
        return logSumExp(terms)
    }

    companion object {
        const val LIKELIHOOD_VERSION = "strength-session-likelihood-2.0.0"
        private const val DEFAULT_DAY_EFFECT_LOG_SD = 0.055
        private const val GH_NODE_COUNT = 15
        private val GH_INTEGRATOR = GaussIntegratorFactory().hermite(GH_NODE_COUNT)
        private val GH_POINTS = DoubleArray(GH_NODE_COUNT) { GH_INTEGRATOR.getPoint(it) }
        private val GH_WEIGHTS = DoubleArray(GH_NODE_COUNT) { GH_INTEGRATOR.getWeight(it) }
        private val SQRT_TWO = sqrt(2.0)
        private val LOG_SQRT_PI = 0.5 * ln(Math.PI)
    }
}

data class StrengthExerciseSessionObservation(
    val sessionKey: String,
    val date: LocalDate,
    val exerciseStableKey: String,
    val exerciseName: String,
    val directTargetKey: StrengthPerformanceTargetKey?,
    val targetLoadings: List<StrengthProxyLoadingSpec>,
    val observationType: StrengthObservationType,
    val capacityMedianKg: Double,
    val capacityLow80Kg: Double,
    val capacityHigh80Kg: Double,
    val lowerBoundOnly: Boolean,
    val upperBoundOnly: Boolean,
    val failureUpperBoundKg: Double?,
    val logVariance: Double,
    val directObservedLoadKg: Double?,
    val bodyWeightKg: Double?,
    val rawAddedWeightKg: Double?,
    val bodyWeightSource: BodyWeightSource,
    val curveProfileId: String,
    val curveMatchLevel: String,
    val curveVarianceMultiplier: Double,
    val curveSubjectKey: String,
    val sourceSetIds: List<Long>,
    val strongObservationCount: Int,
    val diagnostics: List<String>,
    val evidenceFingerprint: String,
    val setEvidence: List<StrengthSetEvidence>,
    val sessionLikelihood: StrengthExerciseSessionLikelihood
)

object StrengthSetLikelihoodBuilder {
    fun build(
        set: WorkoutSet,
        entryRpe: Double?,
        resolvedLoad: ResolvedStrengthLoad,
        curve: ResolvedRepetitionCurve,
        rirPolicy: RpeRirPolicy
    ): StrengthSetEvidence? = buildResult(set, entryRpe, resolvedLoad, curve, rirPolicy).evidence

    fun buildResult(
        set: WorkoutSet,
        entryRpe: Double?,
        resolvedLoad: ResolvedStrengthLoad,
        curve: ResolvedRepetitionCurve,
        rirPolicy: RpeRirPolicy
    ): StrengthSetLikelihoodBuildResult {
        if (!set.confirmed || set.reps < 0) {
            return excluded(set, StrengthObservationType.EXCLUDED_INVALID_SET, "INVALID_OR_UNCONFIRMED_SET")
        }
        if (!resolvedLoad.isResolved) {
            return excluded(
                set,
                StrengthObservationType.UNRESOLVED_LOAD,
                "LOAD_${resolvedLoad.confidence.name}_${resolvedLoad.semantics.name}"
            )
        }
        val totalLoad = checkNotNull(resolvedLoad.totalLoadKg)
        val rpe = set.rpe.validRpe() ?: entryRpe.validRpe()
        if (set.reps == 0) {
            if (rpe != 10.0) {
                return excluded(set, StrengthObservationType.EXCLUDED_INVALID_SET, "ZERO_REPETITIONS_REQUIRES_RPE10")
            }
            val variance = (FAILURE_LOG_SD.pow(2) + resolvedLoad.loadVarianceContribution)
                .coerceAtLeast(GENERAL_VARIANCE_FLOOR)
            return StrengthSetLikelihoodBuildResult(
                evidence = StrengthSetEvidence(
                    setId = set.id,
                    setIndex = set.setIndex,
                    repetitions = 0,
                    rpe = rpe,
                    resolvedLoad = resolvedLoad,
                    curveRelativeLoad = 1.0,
                    capacityCenterKg = totalLoad,
                    lowerBoundKg = totalLoad,
                    logVariance = variance,
                    observationType = StrengthObservationType.FAILURE_UPPER_CENSORED,
                    evidenceFingerprint = fingerprint(
                        set.id.toString(), set.setIndex.toString(), "failure", set.weightKg.toBits().toString(),
                        rpe.toBits().toString(), totalLoad.toBits().toString(), curve.profile.id.value
                    ),
                    likelihood = StrengthSetLikelihood.UpperCensored(ln(totalLoad), sqrt(variance))
                )
            )
        }
        val baseEvaluation = curve.evaluate(set.reps.toDouble())
        val baseRelativeLoad = baseEvaluation.relativeLoad ?: return excluded(
            set,
            StrengthObservationType.UNSUPPORTED_REPETITION_RANGE,
            "UNSUPPORTED_REPETITIONS:${set.reps}"
        )
        val lowerBound = totalLoad / baseRelativeLoad
        if (!lowerBound.isFinite() || lowerBound <= 0.0) {
            return excluded(set, StrengthObservationType.EXCLUDED_INVALID_SET, "NON_FINITE_CAPACITY")
        }

        if (rpe == null) {
            val variance = (MISSING_RPE_LOG_SD.pow(2) + resolvedLoad.loadVarianceContribution)
                .coerceAtLeast(GENERAL_VARIANCE_FLOOR)
            return StrengthSetLikelihoodBuildResult(
                evidence = evidence(
                    set = set,
                    rpe = null,
                    resolvedLoad = resolvedLoad,
                    curve = curve,
                    curveRelativeLoad = baseRelativeLoad,
                    capacityCenterKg = lowerBound,
                    lowerBoundKg = lowerBound,
                    logVariance = variance,
                    observationType = StrengthObservationType.MISSING_RPE_LOWER_CENSORED,
                    likelihood = StrengthSetLikelihood.LowerCensored(ln(lowerBound), sqrt(variance)),
                    fingerprintSuffix = "missing-rpe"
                )
            )
        }

        val distribution = rirPolicy.resolve(rpe) ?: return excluded(
            set,
            StrengthObservationType.EXCLUDED_INVALID_SET,
            "UNSUPPORTED_RPE:$rpe"
        )
        val supported = distribution.probabilities.mapNotNull { probability ->
            val repetitionsAtFailure = set.reps + probability.rir
            val evaluation = curve.evaluate(repetitionsAtFailure.toDouble())
            val relativeLoad = evaluation.relativeLoad ?: return@mapNotNull null
            val conditionalVariance = branchVariance(
                repetitionsAtFailure = repetitionsAtFailure,
                direct = set.reps == 1 && rpe == 10.0,
                curveVarianceMultiplier = curve.varianceMultiplier,
                loadVariance = resolvedLoad.loadVarianceContribution,
                setIndex = set.setIndex
            )
            StrengthLikelihoodComponent(
                rir = probability.rir,
                probability = probability.probability,
                logCenter = ln(totalLoad / relativeLoad),
                logVariance = conditionalVariance
            )
        }
        val supportedMass = supported.sumOf(StrengthLikelihoodComponent::probability)
        if (supportedMass + 1e-12 < RpeRirPolicy.MINIMUM_SUPPORTED_MIXTURE_MASS) {
            return excluded(
                set,
                StrengthObservationType.UNSUPPORTED_REPETITION_RANGE,
                "SUPPORTED_RIR_MASS:${"%.6f".format(java.util.Locale.ROOT, supportedMass)}"
            )
        }
        val components = supported.map { component ->
            component.copy(probability = component.probability / supportedMass)
        }
        val logMean = components.sumOf { component -> component.probability * component.logCenter }
        val logVariance = components.sumOf { component ->
            component.probability * (
                component.logVariance + (component.logCenter - logMean).pow(2)
                )
        }.coerceAtLeast(
            if (set.reps == 1 && rpe == 10.0) DIRECT_VARIANCE_FLOOR else GENERAL_VARIANCE_FLOOR
        )
        val type = when {
            set.reps == 1 && rpe == 10.0 -> StrengthObservationType.DIRECT_1RM
            rpe == 10.0 -> StrengthObservationType.STRONG_NRM
            else -> StrengthObservationType.RPE_MIXTURE_OBSERVATION
        }
        val mixture = StrengthSetLikelihood.GaussianMixture(components, supportedMass)
        return StrengthSetLikelihoodBuildResult(
            evidence = evidence(
                set = set,
                rpe = rpe,
                resolvedLoad = resolvedLoad,
                curve = curve,
                curveRelativeLoad = baseRelativeLoad,
                capacityCenterKg = exp(logMean),
                lowerBoundKg = lowerBound,
                logVariance = logVariance,
                observationType = type,
                likelihood = mixture,
                supportedMixtureMass = supportedMass,
                fingerprintSuffix = components.joinToString(";") {
                    "${it.rir}:${it.probability}:${it.logCenter}:${it.logVariance}"
                }
            )
        )
    }

    private fun evidence(
        set: WorkoutSet,
        rpe: Double?,
        resolvedLoad: ResolvedStrengthLoad,
        curve: ResolvedRepetitionCurve,
        curveRelativeLoad: Double,
        capacityCenterKg: Double,
        lowerBoundKg: Double,
        logVariance: Double,
        observationType: StrengthObservationType,
        likelihood: StrengthSetLikelihood,
        supportedMixtureMass: Double = 1.0,
        fingerprintSuffix: String
    ): StrengthSetEvidence = StrengthSetEvidence(
        setId = set.id,
        setIndex = set.setIndex,
        repetitions = set.reps,
        rpe = rpe,
        resolvedLoad = resolvedLoad,
        curveRelativeLoad = curveRelativeLoad,
        capacityCenterKg = capacityCenterKg,
        lowerBoundKg = lowerBoundKg,
        logVariance = logVariance,
        observationType = observationType,
        evidenceFingerprint = fingerprint(
            set.id.toString(),
            set.setIndex.toString(),
            set.reps.toString(),
            set.weightKg.toBits().toString(),
            rpe?.toBits()?.toString().orEmpty(),
            resolvedLoad.totalLoadKg?.toBits()?.toString().orEmpty(),
            curve.profile.id.value,
            curve.personalTheta.toBits().toString(),
            RpeRirPolicy.POLICY_VERSION,
            fingerprintSuffix
        ),
        likelihood = likelihood,
        supportedMixtureMass = supportedMixtureMass
    )

    private fun excluded(
        set: WorkoutSet,
        type: StrengthObservationType,
        diagnostic: String?
    ): StrengthSetLikelihoodBuildResult = StrengthSetLikelihoodBuildResult(
        exclusionType = type,
        diagnostic = "${type.name}:${set.id}:${diagnostic.orEmpty()}"
    )

    private fun branchVariance(
        repetitionsAtFailure: Int,
        direct: Boolean,
        curveVarianceMultiplier: Double,
        loadVariance: Double,
        setIndex: Int
    ): Double {
        val curveSd = CURVE_BASE_LOG_SD + (repetitionsAtFailure - 1).coerceAtLeast(0) * CURVE_REP_LOG_SD
        val conditionalExecutionSd = (if (direct) DIRECT_LOG_SD else CONDITIONAL_EXECUTION_LOG_SD) *
            (1.0 + setIndex.coerceAtLeast(0) * SET_ORDER_SD_GROWTH)
        return (
            curveSd.pow(2) * curveVarianceMultiplier +
                conditionalExecutionSd.pow(2) +
                loadVariance
            ).coerceAtLeast(if (direct) DIRECT_VARIANCE_FLOOR else GENERAL_VARIANCE_FLOOR)
    }

    private fun Double?.validRpe(): Double? =
        this?.takeIf { value -> value.isFinite() && value in 1.0..10.0 }

    private const val DIRECT_LOG_SD = 0.020
    private const val CONDITIONAL_EXECUTION_LOG_SD = 0.055
    private const val CURVE_BASE_LOG_SD = 0.035
    private const val CURVE_REP_LOG_SD = 0.0025
    private const val SET_ORDER_SD_GROWTH = 0.04
    private const val MISSING_RPE_LOG_SD = 0.38
    private const val FAILURE_LOG_SD = 0.24
    const val DIRECT_VARIANCE_FLOOR = 0.0004
    private const val GENERAL_VARIANCE_FLOOR = 0.0016
}

object StrengthSessionObservationBuilder {
    fun build(
        record: WorkoutEntryWithSets,
        exercise: Exercise,
        registry: StrengthPerformanceRegistry,
        curveRegistry: RepetitionCurveRegistry,
        loadResolver: StrengthPerformanceLoadResolver,
        rirPolicy: RpeRirPolicy,
        personalTheta: Double = 0.0
    ): StrengthExerciseSessionObservation? {
        val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull() ?: return null
        val targetLoadings = registry.proxyLoadings(exercise)
        if (targetLoadings.isEmpty()) return null
        val semantics = targetLoadings.map(StrengthProxyLoadingSpec::loadSemantics).distinct().singleOrNull()
            ?: return null
        val curve = curveRegistry.resolve(exercise.stableKey, exercise.isCustom, personalTheta)
        val setResults = record.sets.sortedWith(compareBy(WorkoutSet::setIndex, WorkoutSet::id)).map { set ->
            StrengthSetLikelihoodBuilder.buildResult(
                set = set,
                entryRpe = record.entry.rpe,
                resolvedLoad = loadResolver.resolve(date, set, semantics),
                curve = curve,
                rirPolicy = rirPolicy
            )
        }
        val setEvidence = setResults.mapNotNull(StrengthSetLikelihoodBuildResult::evidence)
        if (setEvidence.isEmpty()) return null
        val failures = setEvidence.filter(StrengthSetEvidence::isFailure)
        val successful = setEvidence.filterNot(StrengthSetEvidence::isFailure)
        val strong = successful.filter(StrengthSetEvidence::isStrong)
        val selected = if (strong.isNotEmpty()) strong else successful.ifEmpty { failures }
        val weightedMedian = weightedMedian(selected)
        val sessionLikelihood = StrengthExerciseSessionLikelihood(setEvidence)
        val likelihoodMoments = sessionLikelihood.takeIf(StrengthExerciseSessionLikelihood::hasProperLikelihood)?.let {
            ScalarGridPosteriorEngine.likelihoodMoments(it.asScalarLikelihood(properOnly = true))
        }
        val centerLog = likelihoodMoments?.mean ?: ln(weightedMedian.capacityCenterKg)
        val residuals = selected.map { evidence -> abs(ln(evidence.capacityCenterKg) - centerLog) }
        val robustSpread = median(residuals)
        val baseVariance = likelihoodMoments?.variance ?: selected.sumOf { evidence -> 1.0 / evidence.logVariance }
            .takeIf { precision -> precision > 0.0 }?.let { precision -> 1.0 / precision }
            ?: weightedMedian.logVariance
        val effectiveEvidenceCount = selected.size.coerceAtMost(3)
        val sameSessionFloor = weightedMedian.logVariance / effectiveEvidenceCount
        val contradictory = robustSpread > CONTRADICTION_LOG_THRESHOLD
        val variance = maxOf(baseVariance, sameSessionFloor, robustSpread.pow(2), MIN_SESSION_LOG_VARIANCE) *
            if (contradictory) CONTRADICTION_MULTIPLIER else 1.0
        val sd = kotlin.math.sqrt(variance)
        val directTarget = registry.directTarget(exercise.stableKey)?.targetKey
        val directOneRep = strong.filter { evidence ->
            evidence.observationType == StrengthObservationType.DIRECT_1RM
        }.maxByOrNull(StrengthSetEvidence::capacityCenterKg)
        val type = when {
            directOneRep != null && directTarget != null -> StrengthObservationType.DIRECT_1RM
            strong.isNotEmpty() -> StrengthObservationType.STRONG_NRM
            failures.isNotEmpty() && successful.isEmpty() -> StrengthObservationType.FAILURE_UPPER_CENSORED
            selected.any { evidence -> evidence.observationType == StrengthObservationType.RPE_MIXTURE_OBSERVATION } ->
                StrengthObservationType.RPE_MIXTURE_OBSERVATION
            else -> StrengthObservationType.MISSING_RPE_LOWER_CENSORED
        }
        val bodyweightEvidence = selected.firstOrNull { evidence -> evidence.resolvedLoad.resolvedBodyWeightKg != null }
        val fingerprint = fingerprint(
            "date:${record.entry.date}", exercise.stableKey, curve.profile.id.value,
            curve.matchLevel.name, curve.varianceMultiplier.toBits().toString(),
            *setEvidence.map(StrengthSetEvidence::evidenceFingerprint).toTypedArray()
        )
        return StrengthExerciseSessionObservation(
            sessionKey = "date:${record.entry.date}",
            date = date,
            exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name,
            directTargetKey = directTarget,
            targetLoadings = targetLoadings,
            observationType = type,
            capacityMedianKg = exp(centerLog),
            capacityLow80Kg = exp(centerLog - Z_80 * sd),
            capacityHigh80Kg = exp(centerLog + Z_80 * sd),
            lowerBoundOnly = successful.isNotEmpty() && successful.none(StrengthSetEvidence::isTwoSided),
            upperBoundOnly = successful.isEmpty() && failures.isNotEmpty(),
            failureUpperBoundKg = failures.minOfOrNull(StrengthSetEvidence::capacityCenterKg),
            logVariance = variance,
            directObservedLoadKg = directOneRep?.resolvedLoad?.totalLoadKg,
            bodyWeightKg = bodyweightEvidence?.resolvedLoad?.resolvedBodyWeightKg,
            rawAddedWeightKg = bodyweightEvidence?.resolvedLoad?.rawAddedWeightKg,
            bodyWeightSource = bodyweightEvidence?.resolvedLoad?.bodyWeightSource ?: BodyWeightSource.UNRESOLVED,
            curveProfileId = curve.profile.id.value,
            curveMatchLevel = curve.matchLevel.name,
            curveVarianceMultiplier = curve.varianceMultiplier,
            curveSubjectKey = curve.curveSubjectKey,
            sourceSetIds = setEvidence.map(StrengthSetEvidence::setId),
            strongObservationCount = strong.size,
            diagnostics = buildList {
                if (contradictory) add("CONTRADICTORY_SAME_SESSION_EVIDENCE")
                if (failures.isNotEmpty()) add("RPE10_ZERO_REP_FAILURE")
                likelihoodMoments?.diagnostics?.let { grid ->
                    add("SESSION_GRID:${grid.gridPointCount}:${grid.expansionCount}:${grid.fingerprint}")
                }
                addAll(setResults.mapNotNull(StrengthSetLikelihoodBuildResult::diagnostic))
            },
            evidenceFingerprint = fingerprint,
            setEvidence = setEvidence,
            sessionLikelihood = sessionLikelihood
        )
    }

    private fun weightedMedian(evidence: List<StrengthSetEvidence>): StrengthSetEvidence {
        val ordered = evidence.sortedWith(
            compareBy<StrengthSetEvidence>(StrengthSetEvidence::capacityCenterKg)
                .thenBy(StrengthSetEvidence::setIndex)
                .thenBy(StrengthSetEvidence::setId)
        )
        val weights = ordered.map { item -> 1.0 / item.logVariance / (1.0 + item.setIndex.coerceAtLeast(0) * 0.05) }
        val threshold = weights.sum() / 2.0
        var accumulated = 0.0
        return ordered.firstOrNull { item ->
            accumulated += weights[ordered.indexOf(item)]
            accumulated >= threshold
        } ?: ordered.last()
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private const val Z_80 = 1.2815515655446004
    private const val CONTRADICTION_LOG_THRESHOLD = 0.12
    private const val CONTRADICTION_MULTIPLIER = 2.0
    private const val MIN_SESSION_LOG_VARIANCE = 0.0004
}

private val STANDARD_NORMAL = NormalDistribution(0.0, 1.0)
private const val MIN_PROBABILITY = 1e-300

private fun normalLogDensity(value: Double, mean: Double, variance: Double): Double =
    -0.5 * (ln(2.0 * Math.PI * variance) + (value - mean).pow(2) / variance)

internal fun logSumExp(values: List<Double>): Double {
    val maximum = values.maxOrNull() ?: return Double.NEGATIVE_INFINITY
    if (!maximum.isFinite()) return maximum
    return maximum + ln(values.sumOf { value -> exp(value - maximum) })
}

internal fun fingerprint(vararg values: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    values.forEach { value ->
        digest.update(value.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

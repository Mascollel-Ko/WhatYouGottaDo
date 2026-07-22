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
import kotlin.math.pow

enum class StrengthObservationType {
    DIRECT_1RM,
    STRONG_NRM,
    CONSERVATIVE_LOWER_BOUND,
    MISSING_RPE_LOWER_BOUND
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
    val evidenceFingerprint: String
) {
    val isStrong: Boolean get() = observationType in setOf(
        StrengthObservationType.DIRECT_1RM,
        StrengthObservationType.STRONG_NRM
    )
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
    val setEvidence: List<StrengthSetEvidence>
)

object StrengthSetLikelihoodBuilder {
    fun build(
        set: WorkoutSet,
        entryRpe: Double?,
        resolvedLoad: ResolvedStrengthLoad,
        curve: ResolvedRepetitionCurve
    ): StrengthSetEvidence? {
        if (!set.confirmed || !resolvedLoad.isResolved) return null
        val evaluation = curve.evaluate(set.reps.toDouble())
        val relativeLoad = evaluation.relativeLoad ?: return null
        val totalLoad = checkNotNull(resolvedLoad.totalLoadKg)
        val capacity = totalLoad / relativeLoad
        if (!capacity.isFinite() || capacity <= 0.0) return null
        val rpe = set.rpe.validRpe() ?: entryRpe.validRpe()
        val type = when {
            rpe == 10.0 && set.reps == 1 -> StrengthObservationType.DIRECT_1RM
            rpe == 10.0 -> StrengthObservationType.STRONG_NRM
            rpe != null -> StrengthObservationType.CONSERVATIVE_LOWER_BOUND
            else -> StrengthObservationType.MISSING_RPE_LOWER_BOUND
        }
        val modelSd = when (type) {
            StrengthObservationType.DIRECT_1RM -> DIRECT_LOG_SD
            StrengthObservationType.STRONG_NRM -> STRONG_NRM_BASE_SD + (set.reps - 1) * STRONG_NRM_REP_SD
            StrengthObservationType.CONSERVATIVE_LOWER_BOUND ->
                LOWER_BOUND_BASE_SD + (10.0 - checkNotNull(rpe)) * LOWER_BOUND_RPE_SD
            StrengthObservationType.MISSING_RPE_LOWER_BOUND -> MISSING_RPE_LOG_SD
        }
        val variance = (
            modelSd.pow(2) * curve.varianceMultiplier + resolvedLoad.loadVarianceContribution
            ).coerceAtLeast(if (type == StrengthObservationType.DIRECT_1RM) DIRECT_VARIANCE_FLOOR else GENERAL_VARIANCE_FLOOR)
        val fingerprint = fingerprint(
            set.id.toString(), set.setIndex.toString(), set.reps.toString(),
            set.weightKg.toBits().toString(), rpe?.toBits()?.toString().orEmpty(),
            totalLoad.toBits().toString(), curve.profile.id.value, curve.personalTheta.toBits().toString()
        )
        return StrengthSetEvidence(
            setId = set.id,
            setIndex = set.setIndex,
            repetitions = set.reps,
            rpe = rpe,
            resolvedLoad = resolvedLoad,
            curveRelativeLoad = relativeLoad,
            capacityCenterKg = capacity,
            lowerBoundKg = capacity,
            logVariance = variance,
            observationType = type,
            evidenceFingerprint = fingerprint
        )
    }

    private fun Double?.validRpe(): Double? = this?.takeIf { value -> value.isFinite() && value in 1.0..10.0 }

    private const val DIRECT_LOG_SD = 0.020
    private const val STRONG_NRM_BASE_SD = 0.055
    private const val STRONG_NRM_REP_SD = 0.006
    private const val LOWER_BOUND_BASE_SD = 0.20
    private const val LOWER_BOUND_RPE_SD = 0.035
    private const val MISSING_RPE_LOG_SD = 0.38
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
        personalTheta: Double = 0.0
    ): StrengthExerciseSessionObservation? {
        val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull() ?: return null
        val targetLoadings = registry.proxyLoadings(exercise.stableKey)
        if (targetLoadings.isEmpty()) return null
        val semantics = targetLoadings.map(StrengthProxyLoadingSpec::loadSemantics).distinct().singleOrNull()
            ?: return null
        val curve = curveRegistry.resolve(exercise.stableKey, exercise.isCustom, personalTheta)
        val setEvidence = record.sets.sortedWith(compareBy(WorkoutSet::setIndex, WorkoutSet::id)).mapNotNull { set ->
            StrengthSetLikelihoodBuilder.build(
                set = set,
                entryRpe = record.entry.rpe,
                resolvedLoad = loadResolver.resolve(date, set, semantics),
                curve = curve
            )
        }
        if (setEvidence.isEmpty()) return null
        val strong = setEvidence.filter(StrengthSetEvidence::isStrong)
        val selected = if (strong.isNotEmpty()) strong else setEvidence
        val weightedMedian = weightedMedian(selected)
        val centerLog = ln(weightedMedian.capacityCenterKg)
        val residuals = selected.map { evidence -> abs(ln(evidence.capacityCenterKg) - centerLog) }
        val robustSpread = median(residuals)
        val baseVariance = selected.sumOf { evidence -> 1.0 / evidence.logVariance }
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
            selected.any { evidence -> evidence.observationType == StrengthObservationType.CONSERVATIVE_LOWER_BOUND } ->
                StrengthObservationType.CONSERVATIVE_LOWER_BOUND
            else -> StrengthObservationType.MISSING_RPE_LOWER_BOUND
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
            capacityMedianKg = weightedMedian.capacityCenterKg,
            capacityLow80Kg = exp(centerLog - Z_80 * sd),
            capacityHigh80Kg = exp(centerLog + Z_80 * sd),
            lowerBoundOnly = strong.isEmpty(),
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
            diagnostics = if (contradictory) listOf("CONTRADICTORY_SAME_SESSION_EVIDENCE") else emptyList(),
            evidenceFingerprint = fingerprint,
            setEvidence = setEvidence
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

internal fun fingerprint(vararg values: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    values.forEach { value ->
        digest.update(value.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

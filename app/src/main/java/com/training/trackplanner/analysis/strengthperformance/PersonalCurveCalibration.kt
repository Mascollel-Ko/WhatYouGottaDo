package com.training.trackplanner.analysis.strengthperformance

import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveProfile
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

enum class PersonalCurveStatus {
    CANONICAL_ONLY,
    CALIBRATING,
    PERSONALIZED
}

data class PersonalCurvePosterior(
    val curveSubjectKey: String,
    val canonicalProfileId: String,
    val thetaGrid: List<Double>,
    val posteriorWeights: List<Double>,
    val totalObservationCount: Int,
    val strongObservationCount: Int,
    val distinctRepRangeCount: Int,
    val minObservedReps: Int?,
    val maxObservedReps: Int?,
    val calibrationStatus: PersonalCurveStatus,
    val curveVersion: String,
    val posteriorFingerprint: String,
    val updatedAt: Long
) {
    val meanTheta: Double get() = thetaGrid.indices.sumOf { index -> thetaGrid[index] * posteriorWeights[index] }

    init {
        require(thetaGrid.size == posteriorWeights.size && thetaGrid.isNotEmpty())
        require(posteriorWeights.all { weight -> weight.isFinite() && weight >= 0.0 })
        require(kotlin.math.abs(posteriorWeights.sum() - 1.0) < 1e-8)
    }
}

object PersonalCurveCalibrator {
    val DEFAULT_GRID: List<Double> = (-14..14).map { index -> index * 0.025 }

    fun initial(curveSubjectKey: String, profile: RepetitionCurveProfile, now: Long): PersonalCurvePosterior {
        val rawWeights = DEFAULT_GRID.map { theta -> exp(-0.5 * (theta / PRIOR_SD).pow(2)) }
        val weights = normalize(rawWeights)
        return PersonalCurvePosterior(
            curveSubjectKey = curveSubjectKey,
            canonicalProfileId = profile.id.value,
            thetaGrid = DEFAULT_GRID,
            posteriorWeights = weights,
            totalObservationCount = 0,
            strongObservationCount = 0,
            distinctRepRangeCount = 0,
            minObservedReps = null,
            maxObservedReps = null,
            calibrationStatus = PersonalCurveStatus.CANONICAL_ONLY,
            curveVersion = profile.provenance.curveVersion,
            posteriorFingerprint = fingerprint(curveSubjectKey, profile.id.value, *weights.map(Double::toBits).map(Long::toString).toTypedArray()),
            updatedAt = now
        )
    }

    fun update(
        current: PersonalCurvePosterior,
        profile: RepetitionCurveProfile,
        evidence: List<StrengthSetEvidence>,
        referenceCapacityKg: Double,
        now: Long
    ): PersonalCurvePosterior {
        val usable = evidence.filter { item ->
            item.isStrong && item.repetitions >= 2 && referenceCapacityKg.isFinite() && referenceCapacityKg > 0.0
        }
        if (usable.isEmpty()) return current
        val logWeights = current.posteriorWeights.map { weight -> ln(weight.coerceAtLeast(MIN_WEIGHT)) }.toMutableList()
        current.thetaGrid.forEachIndexed { index, theta ->
            val sessionLogLikelihood = usable.sumOf { item ->
                val adjustedRepetitions = 1.0 + exp(-theta) * (item.repetitions - 1.0)
                val relativeLoad = profile.evaluate(adjustedRepetitions).relativeLoad ?: return@sumOf MIN_LOG_LIKELIHOOD
                val expectedLogLoad = ln(referenceCapacityKg * relativeLoad)
                val residual = ln(checkNotNull(item.resolvedLoad.totalLoadKg)) - expectedLogLoad
                (-0.5 * residual.pow(2) / item.logVariance).coerceAtLeast(MIN_LOG_LIKELIHOOD)
            }
            logWeights[index] += sessionLogLikelihood.coerceAtLeast(MIN_SESSION_LOG_LIKELIHOOD)
        }
        val maximum = logWeights.max()
        val weights = normalize(logWeights.map { value -> exp(value - maximum) })
        val minReps = minOf(current.minObservedReps ?: Int.MAX_VALUE, usable.minOf(StrengthSetEvidence::repetitions))
        val maxReps = maxOf(current.maxObservedReps ?: Int.MIN_VALUE, usable.maxOf(StrengthSetEvidence::repetitions))
        val distinctRangeCount = when {
            current.distinctRepRangeCount == 0 -> usable.map(StrengthSetEvidence::repetitions).distinct().size
            usable.any { item -> item.repetitions < (current.minObservedReps ?: item.repetitions) ||
                item.repetitions > (current.maxObservedReps ?: item.repetitions) } -> current.distinctRepRangeCount + 1
            else -> current.distinctRepRangeCount
        }.coerceAtMost(12)
        val strongCount = current.strongObservationCount + usable.size
        val status = when {
            strongCount >= 6 && distinctRangeCount >= 3 -> PersonalCurveStatus.PERSONALIZED
            strongCount >= 2 -> PersonalCurveStatus.CALIBRATING
            else -> PersonalCurveStatus.CANONICAL_ONLY
        }
        return current.copy(
            posteriorWeights = weights,
            totalObservationCount = current.totalObservationCount + evidence.size,
            strongObservationCount = strongCount,
            distinctRepRangeCount = distinctRangeCount,
            minObservedReps = minReps,
            maxObservedReps = maxReps,
            calibrationStatus = status,
            posteriorFingerprint = fingerprint(
                current.curveSubjectKey, profile.id.value, strongCount.toString(), distinctRangeCount.toString(),
                *weights.map(Double::toBits).map(Long::toString).toTypedArray()
            ),
            updatedAt = now
        )
    }

    private fun normalize(values: List<Double>): List<Double> {
        val sum = values.sum()
        require(sum.isFinite() && sum > 0.0)
        return values.map { value -> value / sum }
    }

    private const val PRIOR_SD = 0.12
    private const val MIN_WEIGHT = 1e-300
    private const val MIN_LOG_LIKELIHOOD = -30.0
    private const val MIN_SESSION_LOG_LIKELIHOOD = -45.0
}

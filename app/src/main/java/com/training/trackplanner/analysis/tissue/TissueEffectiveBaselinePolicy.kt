package com.training.trackplanner.analysis.tissue

import kotlin.math.exp

enum class TissueBaselineProvenance {
    PRIOR_ONLY,
    PERSONAL_ONLY,
    MIXED
}

data class TissueAdjustedPriorBaseline(
    val loadUnitStableKey: String,
    val priorProfileId: String,
    val result: TissueAdjustedPriorResult
) {
    val boundaries: TissuePriorBoundaries get() = result.boundaries
}

data class TissueEffectiveBaseline(
    val loadUnitStableKey: String,
    val adjustedPrior: TissueAdjustedPriorBaseline,
    val personalBaseline: TissuePersonalBaseline?,
    val calibrationWeight: TissuePerUnitCalibrationWeight,
    val effectiveWeight: Double,
    val boundaries: TissuePriorBoundaries,
    val provenance: TissueBaselineProvenance,
    val diagnostics: List<TissueBaselineDiagnostic>
)

data class TissueRelativeStateResult(
    val modeledStatus: TissueCanonicalStatus,
    val status: TissueCanonicalStatus,
    val relativeBandPosition: Double?,
    val symptomOverride: TissueSymptomOverride,
    val diagnostics: List<String>
)

object TissueEffectiveBaselinePolicy {
    const val WEIGHT_EPSILON = 1e-9

    fun adjustedPrior(
        registry: TissuePriorRegistry,
        loadUnitStableKey: String,
        localHour: Int,
        profileInputs: TissuePriorUserProfileInputs
    ): TissueAdjustedPriorBaseline? {
        require(localHour in 0..23)
        val profile = registry.profileFor(loadUnitStableKey) ?: return null
        val base = profile.boundariesByLocalHour[localHour] ?: return null
        return TissueAdjustedPriorBaseline(
            loadUnitStableKey,
            profile.priorProfileId,
            TissuePriorAdjustment.adjust(base, profile.adjustment, profileInputs)
        )
    }

    fun mix(
        adjustedPrior: TissueAdjustedPriorBaseline,
        personalBaseline: TissuePersonalBaseline?,
        calibrationWeight: TissuePerUnitCalibrationWeight
    ): TissueEffectiveBaseline {
        require(adjustedPrior.loadUnitStableKey == calibrationWeight.loadUnitStableKey)
        val diagnostics = personalBaseline?.diagnostics.orEmpty().toMutableList()
        val validPersonal = personalBaseline?.takeIf {
            it.isValid && it.loadUnitStableKey == adjustedPrior.loadUnitStableKey
        }
        val effectiveWeight = if (validPersonal == null) 0.0 else calibrationWeight.value
        val personal = validPersonal?.boundaries
        val prior = adjustedPrior.boundaries
        val candidate = if (personal == null || effectiveWeight <= WEIGHT_EPSILON) {
            prior
        } else if (effectiveWeight >= 1.0 - WEIGHT_EPSILON) {
            personal
        } else {
            runCatching {
                TissuePriorBoundaries(
                    meaningfulFloor = prior.meaningfulFloor,
                    q30 = blend(prior.q30, personal.q30, effectiveWeight),
                    q80 = blend(prior.q80, personal.q80, effectiveWeight),
                    q95 = blend(prior.q95, personal.q95, effectiveWeight)
                )
            }.getOrNull()
        }
        val boundaries = candidate ?: prior.also {
            diagnostics += TissueBaselineDiagnostic(
                "EFFECTIVE_BASELINE_INVALID_ORDER",
                "Mixed boundaries were invalid; adjusted prior boundaries were retained."
            )
        }
        val acceptedWeight = if (candidate == null) 0.0 else effectiveWeight
        val provenance = when {
            acceptedWeight <= WEIGHT_EPSILON -> TissueBaselineProvenance.PRIOR_ONLY
            acceptedWeight >= 1.0 - WEIGHT_EPSILON -> TissueBaselineProvenance.PERSONAL_ONLY
            else -> TissueBaselineProvenance.MIXED
        }
        return TissueEffectiveBaseline(
            loadUnitStableKey = adjustedPrior.loadUnitStableKey,
            adjustedPrior = adjustedPrior,
            personalBaseline = validPersonal,
            calibrationWeight = calibrationWeight,
            effectiveWeight = acceptedWeight,
            boundaries = boundaries,
            provenance = provenance,
            diagnostics = diagnostics
        )
    }

    private fun blend(prior: Double, personal: Double, weight: Double): Double =
        prior * (1.0 - weight) + personal * weight
}

object TissueRelativeStateClassifier {
    fun classify(
        currentLoad: Double,
        baseline: TissueEffectiveBaseline?,
        symptomOverride: TissueSymptomOverride
    ): TissueRelativeStateResult {
        val modeled = when {
            baseline == null || !currentLoad.isFinite() || currentLoad < 0.0 -> TissueCanonicalStatus.UNAVAILABLE
            currentLoad <= baseline.boundaries.meaningfulFloor -> TissueCanonicalStatus.LOW
            currentLoad < baseline.boundaries.q30 -> TissueCanonicalStatus.LOW
            currentLoad < baseline.boundaries.q80 -> TissueCanonicalStatus.MODERATE
            currentLoad < baseline.boundaries.q95 -> TissueCanonicalStatus.HIGH
            else -> TissueCanonicalStatus.VERY_HIGH
        }
        val overrideMinimum = when (symptomOverride) {
            TissueSymptomOverride.NONE -> null
            TissueSymptomOverride.CAUTION -> TissueCanonicalStatus.HIGH
            TissueSymptomOverride.BLOCK -> TissueCanonicalStatus.VERY_HIGH
        }
        val finalStatus = if (overrideMinimum != null && overrideMinimum.severity > modeled.severity) {
            overrideMinimum
        } else {
            modeled
        }
        return TissueRelativeStateResult(
            modeledStatus = modeled,
            status = finalStatus,
            relativeBandPosition = baseline?.let { relativeBandPosition(currentLoad, it.boundaries) },
            symptomOverride = symptomOverride,
            diagnostics = buildList {
                if (baseline == null) add("No valid prior baseline is available for this stable key.")
                if (finalStatus != modeled) add("Symptom/function override raised the product-facing state.")
            }
        )
    }

    fun relativeBandPosition(currentLoad: Double, boundaries: TissuePriorBoundaries): Double {
        if (!currentLoad.isFinite() || currentLoad <= boundaries.meaningfulFloor) return 0.0
        return when {
            currentLoad < boundaries.q30 -> interpolate(
                currentLoad,
                boundaries.meaningfulFloor,
                boundaries.q30,
                0.0,
                1.0
            )
            currentLoad < boundaries.q80 -> interpolate(currentLoad, boundaries.q30, boundaries.q80, 1.0, 2.0)
            currentLoad < boundaries.q95 -> interpolate(currentLoad, boundaries.q80, boundaries.q95, 2.0, 3.0)
            else -> 3.0 + (1.0 - exp(-(currentLoad - boundaries.q95) / boundaries.q95.coerceAtLeast(1e-12)))
        }
    }

    private fun interpolate(value: Double, from: Double, to: Double, low: Double, high: Double): Double =
        low + (value - from) / (to - from) * (high - low)
}

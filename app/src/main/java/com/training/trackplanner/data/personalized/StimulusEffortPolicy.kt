package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.validatedTargetRpeMin

/** Canonical B6 effort thresholds. Numeric RPE validity remains a separate concern. */
internal fun TrainableQuality.canonicalEffortTarget(): StimulusEffortTarget = when (this) {
    TrainableQuality.STRENGTH -> StimulusEffortTarget(6.0, 4)
    TrainableQuality.HYPERTROPHY -> StimulusEffortTarget(7.0, 3)
    else -> StimulusEffortTarget(0.0, Int.MAX_VALUE)
}

/** A persisted target is sufficient only when it is valid and meets the quality threshold. */
internal fun ProgramSetPrescription.satisfiesEffortTarget(effortTarget: StimulusEffortTarget): Boolean {
    val target = targetRpeMin.validatedTargetRpeMin() ?: return false
    return target >= effortTarget.minimumRpe
}

internal fun PlannedPrescription.fullyEncodesEffort(effortTarget: StimulusEffortTarget): Boolean =
    sets.isNotEmpty() && sets.all { it.satisfiesEffortTarget(effortTarget) }

internal fun canonicalExecutionAuthority(
    quality: TrainableQuality?,
    prescription: PlannedPrescription?
): StimulusPrescriptionExecutionAuthority = when {
    quality == TrainableQuality.HYPERTROPHY &&
        prescription?.fullyEncodesEffort(quality.canonicalEffortTarget()) == true ->
        StimulusPrescriptionExecutionAuthority.FULLY_ENCODED
    quality == TrainableQuality.HYPERTROPHY -> StimulusPrescriptionExecutionAuthority.CONDITIONAL_ON_UNPERSISTED_EFFORT
    quality == TrainableQuality.STRENGTH -> StimulusPrescriptionExecutionAuthority.FULLY_ENCODED
    else -> StimulusPrescriptionExecutionAuthority.UNRESOLVED
}

internal fun PlannedPrescription.effortInsufficiencyReason(effortTarget: StimulusEffortTarget): String? {
    if (sets.any { it.targetRpeMin.validatedTargetRpeMin() == null }) return "B6_EFFORT_TARGET_MISSING"
    if (sets.any { !it.satisfiesEffortTarget(effortTarget) }) return "B6_EFFORT_TARGET_BELOW_CANONICAL_MINIMUM"
    return null
}

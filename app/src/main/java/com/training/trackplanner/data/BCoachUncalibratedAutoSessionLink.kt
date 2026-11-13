package com.training.trackplanner.data

/**
 * B program generation starts without a load. A REVIEW signature is not proof that
 * repetitions are incompatible; still, never join unlike schemes or manual intent.
 * Only the program author opts into this matcher using the B generation marker.
 */
internal object BCoachUncalibratedAutoSessionLink {
    fun compatible(a: ProgressionSignature, b: ProgressionSignature): Boolean =
        a.basePolicy == ProgressionBase.REVIEW &&
            b.basePolicy == ProgressionBase.REVIEW &&
            a.baseKg == null && b.baseKg == null &&
            a.anchorSetIndex == null && b.anchorSetIndex == null &&
            a.exerciseStableKey == b.exerciseStableKey &&
            a.exerciseStableKey.isNotBlank() &&
            a.setCount > 0 && a.setCount == b.setCount &&
            a.repsPattern.isNotBlank() && a.repsPattern == b.repsPattern &&
            a.style == b.style && a.variant == b.variant &&
            a.trainingSlot == b.trainingSlot && a.dayIntensity == b.dayIntensity &&
            a.plannerRole == b.plannerRole
}

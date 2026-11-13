package com.training.trackplanner.data

/**
 * B-Coach generates initially uncalibrated working loads (REVIEW).
 * The legacy inferred-track rule intentionally refuses to link all REVIEW rows.
 *
 * Within a *B-Coach generated program only*, two uncalibrated AUTO rows may share
 * a session when their exercise identity, intended reps, training slot and intensity
 * agree. Never use this for user-explicit SEPARATE/OFF/EXISTING rows.
 */
internal object BCoachUncalibratedLinkPolicy {
    fun isBProgram(metadataJson: String?): Boolean =
        metadataJson?.contains("\"bCoachProgressionEquipmentSchema\"") == true &&
            metadataJson.contains("\"linked-g-1\"")

    fun sameUncalibratedIntent(a: ProgressionSignature, b: ProgressionSignature): Boolean =
        a.exerciseStableKey == b.exerciseStableKey &&
            a.basePolicy == ProgressionBase.REVIEW &&
            b.basePolicy == ProgressionBase.REVIEW &&
            a.baseKg == null && b.baseKg == null &&
            a.setCount == b.setCount &&
            a.repsPattern == b.repsPattern &&
            a.style == b.style &&
            a.variant == b.variant &&
            a.trainingSlot == b.trainingSlot &&
            a.dayIntensity == b.dayIntensity &&
            a.plannerRole == b.plannerRole
}

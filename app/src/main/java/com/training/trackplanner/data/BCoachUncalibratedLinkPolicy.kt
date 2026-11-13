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

    /**
     * For B's generated AUTO rows, a normal progression may legitimately vary
     * the prescribed repetitions or sets between weeks. Those changes must not
     * cut the same exercise's progression chain. Only comparable occurrences
     * on the same weekday are grouped automatically; a user may explicitly
     * connect other weekdays. Do not bridge different roles, slots or variants.
     */
    fun sameBRepeatedExercise(
        a: ProgressionSignature, b: ProgressionSignature,
        earlierDay: Int, laterDay: Int
    ): Boolean =
        earlierDay == laterDay &&
            a.exerciseStableKey == b.exerciseStableKey &&
            a.basePolicy == ProgressionBase.REVIEW &&
            b.basePolicy == ProgressionBase.REVIEW &&
            a.baseKg == null && b.baseKg == null &&
            a.style == b.style && a.variant == b.variant &&
            a.trainingSlot == b.trainingSlot &&
            a.plannerRole == b.plannerRole

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

package com.training.trackplanner.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BCoachUncalibratedAutoSessionLinkTest {
    private fun sig(
        exercise: String = "barbell_back_squat",
        reps: String = "6|6|6",
        style: String = "",
        variant: String = "",
        intensity: String = "",
        slot: String = "",
        base: Double? = null,
        basePolicy: ProgressionBase = ProgressionBase.REVIEW
    ) = ProgressionSignature(
        exerciseStableKey = exercise, setCount = 3,
        repsPattern = reps, baseKg = base, oneRmSnapshotKg = null,
        relativeIntensity = null, style = style, variant = variant,
        trainingSlot = slot, dayIntensity = intensity,
        basePolicy = basePolicy
    )

    @Test fun exactUnweightedBWorkoutsCanShareAutoTrack() {
        assertTrue(BCoachUncalibratedAutoSessionLink.compatible(sig(), sig()))
    }
    @Test fun unlikeExercisesRepPlansAndStrengthIntentCannotMerge() {
        assertFalse(BCoachUncalibratedAutoSessionLink.compatible(
            sig(), sig(exercise = "barbell_deadlift")))
        assertFalse(BCoachUncalibratedAutoSessionLink.compatible(
            sig(), sig(reps = "8|8|8")))
        assertFalse(BCoachUncalibratedAutoSessionLink.compatible(
            sig(), sig(variant = "VOLUME")))
        assertFalse(BCoachUncalibratedAutoSessionLink.compatible(
            sig(), sig(intensity = "LIGHT")))
        assertFalse(BCoachUncalibratedAutoSessionLink.compatible(
            sig(), sig(slot = "ASSISTANCE")))
    }
    @Test fun loadedOrNonReviewSessionsUseOriginalProgressionRules() {
        assertFalse(BCoachUncalibratedAutoSessionLink.compatible(sig(), sig(base = 140.0)))
        assertFalse(BCoachUncalibratedAutoSessionLink.compatible(
            sig(), sig(basePolicy = ProgressionBase.UNIFORM)))
    }
}

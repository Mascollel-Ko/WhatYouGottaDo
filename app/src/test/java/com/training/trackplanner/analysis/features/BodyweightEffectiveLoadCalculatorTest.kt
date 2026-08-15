package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyweightEffectiveLoadCalculatorTest {
    @Test
    fun verifiedCanonicalStableKeysPreserveNumericalPolicy() {
        val expectedAtTenAddedKg = mapOf(
            "pull_up" to 900.0,
            "ex_6466fe77" to 900.0,
            "ex_6463edad" to 900.0,
            "ex_deca2b61" to 900.0,
            "ex_e1894690" to 900.0,
            "ex_e41e8dcf" to 900.0,
            "ex_e41f4c2b" to 900.0,
            "ex_e4f911bb" to 900.0,
            "ex_d9084b5e" to 580.0,
            "ex_e159d15a" to 580.0,
            "gymnastic_ring_inverted_row" to 580.0,
            "suspension_trainer_inverted_row" to 580.0,
            "one_arm_gymnastic_ring_row" to 580.0,
            "one_arm_suspension_trainer_row" to 580.0,
            "ex_28902b13" to 590.0,
            "ex_73b0b63f" to 590.0,
            "ex_c4535de3" to 590.0,
            "ex_debf6a8b" to 590.0,
            "ex_fa2e73b3" to 590.0,
            "ex_3caa236b" to 630.0,
            "ex_fb67af37" to 710.0
        )

        assertEquals(expectedAtTenAddedKg.keys, BodyweightLoadProfileAuthority.supportedStableKeys())
        expectedAtTenAddedKg.forEach { (stableKey, expected) ->
            assertEquals(
                stableKey,
                expected,
                BodyweightEffectiveLoadCalculator.volumeLoad(exercise("renamed-$stableKey", stableKey), set(10.0), 80.0),
                0.001
            )
        }
    }

    @Test
    fun arbitraryNamesCannotGrantBodyweightSemantics() {
        listOf("Push Up", "Pull Up", "Assisted Pull Up", "\uD478\uC2DC\uC5C5", "\uB525\uC2A4").forEach { name ->
            assertEquals(100.0, BodyweightEffectiveLoadCalculator.volumeLoad(exercise(name, "unknown-$name"), set(10.0), 80.0), 0.001)
        }
    }

    @Test
    fun resolvesBodyweightByDateThenPreviousMetricThenInitialProfile() {
        val metrics = listOf(
            DailyMetric(date = "2026-07-09", bodyWeightKg = 78.0),
            DailyMetric(date = "2026-07-10", bodyWeightKg = 80.0)
        )
        val profile = InitialUserProfile(bodyWeightKg = 75.0)

        assertEquals(80.0, BodyweightEffectiveLoadCalculator.bodyWeightFor("2026-07-10", metrics, profile) ?: 0.0, 0.001)
        assertEquals(80.0, BodyweightEffectiveLoadCalculator.bodyWeightFor("2026-07-11", metrics, profile) ?: 0.0, 0.001)
        assertEquals(75.0, BodyweightEffectiveLoadCalculator.bodyWeightFor("2026-07-08", metrics, profile) ?: 0.0, 0.001)
        assertNull(BodyweightEffectiveLoadCalculator.bodyWeightFor("2026-07-08", emptyList(), null))
    }

    @Test
    fun lowerBodyAndDrillNamesRemainOnRawVolumePath() {
        listOf("lunge", "bodyweight squat", "split squat", "glute bridge", "six corner shadow footwork").forEach { name ->
            assertEquals(0.0, BodyweightEffectiveLoadCalculator.volumeLoad(exercise(name, "unknown-$name"), set(), 80.0), 0.001)
        }
    }

    @Test
    fun keepsRawPathWhenBodyweightIsUnavailable() {
        val pullUp = exercise("renamed", "pull_up")

        assertEquals(0.0, BodyweightEffectiveLoadCalculator.volumeLoad(pullUp, set(), null), 0.001)
        assertEquals(100.0, BodyweightEffectiveLoadCalculator.volumeLoad(pullUp, set(10.0), null), 0.001)
    }

    private fun exercise(name: String, stableKey: String): Exercise =
        Exercise(name = name, category = "strength", stableKey = stableKey)

    private fun set(weightKg: Double = 0.0): WorkoutSet =
        WorkoutSet(entryId = 1, setIndex = 1, reps = 10, weightKg = weightKg, confirmed = true)
}

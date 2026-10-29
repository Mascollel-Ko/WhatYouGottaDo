package com.training.trackplanner.analysis.readiness

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** MUS-17/18/19/20: the existing decay, baseline and pressure stack must consume
 * the canonical stableKey body-part keys, not resurrect old muscle strings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CanonicalMuscleDownstreamContractTest {
    private val repository = CanonicalExerciseMetadataRepository(
        ApplicationProvider.getApplicationContext<android.content.Context>()
    )
    private val roles = repository.muscleRoleResolver()
    private val date = LocalDate.parse("2026-10-15")

    @Test
    fun canonicalStableKeyFeedsResidualBaselineAndPressureWithoutLegacyRegions() {
        val squat = repository.exercises().first { it.stableKey == "barbell_back_squat" }
        val bogus = squat.copy(primaryMuscles = "BICEPS,TRICEPS,HAMSTRING", secondaryMuscles = "LAT")
        val rows = listOf(
            WorkoutEntryWithSets(
                entry = WorkoutEntry(
                    date = date.toString(),
                    exerciseStableKey = squat.stableKey,
                    exerciseName = "renamed unrelated exercise",
                    category = squat.category
                ),
                sets = listOf(
                    WorkoutSet(entryId = 1, setIndex = 1, reps = 6, weightKg = 100.0, confirmed = true)
                )
            )
        )
        fun calculate(exercise: com.training.trackplanner.data.Exercise) =
            DailyAnalysisLoadAggregator().aggregate(
                entriesWithSets = rows,
                exerciseMap = mapOf(exercise.stableKey to exercise),
                canonicalMuscleRoles = roles
            )

        val original = calculate(squat)
        val tampered = calculate(bogus)
        assertEquals(original, tampered)
        val residual = ResidualFatigueCalculator().calculate(tampered, date)
        assertTrue("quads" in residual.residualByBodyPart)
        assertTrue("glutes" in residual.residualByBodyPart)
        assertFalse("elbow_flexors" in residual.residualByBodyPart)
        assertFalse("hamstrings" in residual.residualByBodyPart)
        val stats = StatisticalBaselineCalculator().calculate(tampered, residual, date)
        assertEquals(residual.residualByBodyPart.keys, stats.bodyPartStats.keys)
        val adaptive = AdaptiveBaselineCalculator().calculate(tampered, stats)
        val pressure = FatiguePressureCalculator().calculate(residual, stats, adaptive)
        assertEquals(residual.residualByBodyPart.keys, pressure.bodyPartPressures.keys)
        assertTrue(pressure.bodyPartPressures.values.all { it.currentResidualLoad >= 0.0 })
    }

    @Test
    fun existingDecayPolicyIsUnchangedByCanonicalSource() {
        val calculator = ResidualFatigueCalculator()
        assertTrue(calculator.decayFactor("LONG", 1) > calculator.decayFactor("SHORT", 1))
        assertEquals(0.0, calculator.decayFactor("MEDIUM", 15), 0.0)
    }
}

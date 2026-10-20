package com.training.trackplanner.analysis.lab

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder.MuscleBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exact case-level contract between canonical anatomy, exercise UI and the legacy muscle graph.
 * No blanket reinterpretation of the remaining provisional PR #28 muscle rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CanonicalMuscleUiAnalysisAlignmentTest {
    private val repo = CanonicalExerciseMetadataRepository(
        ApplicationProvider.getApplicationContext<android.content.Context>()
    )
    private val exercises by lazy { repo.exercises(includeHistory = true).associateBy { it.stableKey } }
    private val relations by lazy { repo.muscleRelations().groupBy { it.exerciseStableKey } }

    @Test fun skaterSquatAndBoxStepupKeepBothKneeAndHipPrimeMovers() {
        listOf("ex_ee378da7", "ex_6232f4bc").forEach { key ->
            val primary = relations.getValue(key).filter { it.relationType == "PRIMARY" }
                .mapTo(mutableSetOf()) { it.relationValue }
            assertEquals(key, setOf("QUADS", "GLUTES"), primary)
            assertEquals(key, setOf(MuscleBucket.QUADS, MuscleBucket.GLUTES),
                MuscleLoadInputBuilder.contributions(exercises.getValue(key)).keys
                    .filterTo(mutableSetOf()) { it == MuscleBucket.QUADS || it == MuscleBucket.GLUTES })
        }
    }

    @Test fun knownFalsePrimaryAssignmentsAreNotDisplayedOrCredited() {
        val cases = mapOf(
            "ex_eb636bac" to (setOf(MuscleBucket.TRICEPS) to emptySet()),
            "ex_bb4b4276" to (setOf(MuscleBucket.SHOULDERS) to setOf(MuscleBucket.TRICEPS)),
            "ex_1dbee10e" to (setOf(MuscleBucket.CHEST) to setOf(MuscleBucket.SHOULDERS, MuscleBucket.TRICEPS)),
            "ex_4aa3725a" to (setOf(MuscleBucket.SHOULDERS) to setOf(MuscleBucket.TRICEPS)),
            "ex_c5043892" to (setOf(MuscleBucket.QUADS, MuscleBucket.GLUTES) to emptySet()),
            "ex_da56a4fe" to (setOf(MuscleBucket.TRICEPS) to emptySet())
        )
        for ((key, expected) in cases) {
            val exercise = exercises.getValue(key)
            val contributions = MuscleLoadInputBuilder.contributions(exercise)
            for (p in expected.first) assertEquals(key, 1.0, contributions.getValue(p), 0.001)
            for (s in expected.second) assertEquals(key, 0.5, contributions.getValue(s), 0.001)
            assertEquals(key, expected.first + expected.second, contributions.keys)
            assertFalse(key, MuscleBucket.BACK_LATS in contributions)
            val primaryCodes = relations.getValue(key).filter { it.relationType == "PRIMARY" }
                .mapTo(mutableSetOf()) { it.relationValue }
            assertTrue(key, primaryCodes.isNotEmpty())
        }
    }

    @Test fun correctedHypertrophyGroupingFollowsTrainingTarget() {
        val expected = mapOf(
            "dumbbell_goblet_squat" to "QUADS",
            "kettlebell_goblet_squat" to "QUADS",
            "ex_85f12271" to "QUADS",
            "ex_d67e5761" to "QUADS",
            "ex_b69ab702" to "ARMS",
            "ex_e159d15a" to "BACK",
            "chest_supported_row_machine" to "BACK",
            "dumbbell_chest_supported_row" to "BACK",
            "ex_6a966452" to "BACK",
            "ex_eb636bac" to "ARMS",
            "ex_bb4b4276" to "SHOULDERS",
            "ex_4aa3725a" to "SHOULDERS",
            "ex_c5043892" to "QUADS",
            "ex_da56a4fe" to "ARMS"
        )
        for ((key, group) in expected) {
            assertEquals(key, group, exercises.getValue(key).hypertrophyVolumeGroup)
        }
    }
}

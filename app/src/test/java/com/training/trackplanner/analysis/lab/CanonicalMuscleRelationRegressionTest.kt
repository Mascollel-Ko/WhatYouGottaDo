package com.training.trackplanner.analysis.lab

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder.MuscleBucket
import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.weekly.WeeklyAnalysisFeatureSnapshotBuilder
import com.training.trackplanner.analysis.trends.TrendMetricId
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CanonicalMuscleRelationRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = CanonicalExerciseMetadataRepository(context)

    @Test
    fun correctedRelationsKeepOnlyReviewedMuscleRolesAndWeights() {
        val expected = mapOf(
            "band_pallof_press" to mapOf("ANTERIOR_CORE" to primary(), "LATERAL_CORE" to primary()),
            "cable_pallof_press" to mapOf("ANTERIOR_CORE" to primary(), "LATERAL_CORE" to primary()),
            "chest_supported_row_machine" to rowRelations(),
            "dumbbell_chest_supported_row" to rowRelations(),
            "ex_6a966452" to rowRelations(),
            "dumbbell_spider_curl" to mapOf("BICEPS" to primary()),
            "ez_bar_spider_curl" to mapOf("BICEPS" to primary()),
            "ex_b69ab702" to mapOf("BICEPS" to primary()),
            "ex_6466fe77" to mapOf("BACK_LATS" to primary(), "BICEPS" to secondary()),
            "cable_hip_adduction" to mapOf("HIP_ADDUCTOR" to primary()),
            "hip_adduction_machine" to mapOf("HIP_ADDUCTOR" to primary()),
            "ex_728da646" to mapOf("GLUTE_MEDIUS" to primary()),
            "ex_a8385c4a" to mapOf("HIP_ADDUCTOR" to primary(), "LATERAL_CORE" to secondary()),
            "gymnastic_ring_inverted_row" to invertedRowRelations(),
            "suspension_trainer_inverted_row" to invertedRowRelations(),
            "ex_ab468462" to legPressRelations(),
            "ex_a091b9fe" to legPressRelations()
        )
        val rowsByExercise = repository.muscleRelations()
            .filter { relation -> relation.exerciseStableKey in expected.keys }
            .groupBy { relation -> relation.exerciseStableKey }

        val actual = expected.keys.associateWith { stableKey ->
            rowsByExercise[stableKey].orEmpty().associate { relation ->
                relation.relationValue to (relation.relationType to relation.coefficient)
            }
        }

        assertEquals(expected, actual)
        assertTrue(repository.muscleRelations().none { relation ->
            relation.exerciseStableKey in CHEST_TARGETS && relation.relationValue == "CHEST"
        })
        assertTrue(repository.muscleRelations().none { relation ->
            relation.exerciseStableKey in LEG_PRESS_KEYS && relation.relationValue == "HAMSTRINGS"
        })
        assertTrue(repository.identities().map { identity -> identity.stableKey }.containsAll(expected.keys))
    }

    @Test
    fun bundledExerciseMetadataChangesTheActualMuscleLoadSeries() {
        val exercises = repository.exercises(includeHistory = true).associateBy { exercise -> exercise.stableKey }
        val expectedContributions = mapOf(
            "band_pallof_press" to emptyMap(),
            "cable_pallof_press" to emptyMap(),
            "ex_99728d25" to emptyMap(),
            "chest_supported_row_machine" to rowContributions(),
            "dumbbell_chest_supported_row" to rowContributions(),
            "ex_6a966452" to rowContributions(),
            "ex_8e51640a" to rowContributions(),
            "dumbbell_spider_curl" to mapOf(MuscleBucket.BICEPS to 1.0),
            "ez_bar_spider_curl" to mapOf(MuscleBucket.BICEPS to 1.0),
            "ex_516f4456" to mapOf(MuscleBucket.BICEPS to 1.0),
            "ex_b69ab702" to mapOf(MuscleBucket.BICEPS to 1.0),
            "ex_6466fe77" to mapOf(MuscleBucket.BACK_LATS to 1.0, MuscleBucket.BICEPS to 0.5),
            "cable_hip_adduction" to mapOf(MuscleBucket.ADDUCTOR_ABDUCTOR to 1.0),
            "hip_adduction_machine" to mapOf(MuscleBucket.ADDUCTOR_ABDUCTOR to 1.0),
            "ex_728da646" to mapOf(MuscleBucket.GLUTES to 1.0),
            "ex_a8385c4a" to mapOf(MuscleBucket.ADDUCTOR_ABDUCTOR to 1.0),
            "gymnastic_ring_inverted_row" to invertedRowContributions(),
            "suspension_trainer_inverted_row" to invertedRowContributions(),
            "ex_e159d15a" to invertedRowContributions(),
            "ex_ab468462" to legPressContributions(),
            "ex_a091b9fe" to legPressContributions()
        )

        expectedContributions.forEach { (stableKey, expected) ->
            val exercise = exercises.getValue(stableKey)
            val actual = MuscleLoadInputBuilder.contributions(exercise)
            assertEquals(stableKey, expected, actual)
            assertFalse(stableKey, MuscleBucket.CHEST in actual)
        }

        val pallofSeries = seriesFor(exercises.getValue("band_pallof_press"))
        assertTrue(pallofSeries[TrendMetricId.MUSCLE_CHEST_LOAD_DAILY].orEmpty().isEmpty())

        val rowSeries = seriesFor(exercises.getValue("chest_supported_row_machine"))
        assertEquals(1_000.0, rowSeries.getValue(TrendMetricId.MUSCLE_BACK_LATS_LOAD_DAILY).single().value!!, 0.01)
        assertEquals(500.0, rowSeries.getValue(TrendMetricId.MUSCLE_BICEPS_LOAD_DAILY).single().value!!, 0.01)
        assertEquals(500.0, rowSeries.getValue(TrendMetricId.MUSCLE_SHOULDERS_LOAD_DAILY).single().value!!, 0.01)
        assertTrue(rowSeries[TrendMetricId.MUSCLE_CHEST_LOAD_DAILY].orEmpty().isEmpty())

        val legPressSeries = seriesFor(exercises.getValue("ex_ab468462"))
        assertEquals(1_000.0, legPressSeries.getValue(TrendMetricId.MUSCLE_QUADS_LOAD_DAILY).single().value!!, 0.01)
        assertEquals(500.0, legPressSeries.getValue(TrendMetricId.MUSCLE_GLUTES_LOAD_DAILY).single().value!!, 0.01)
        assertTrue(legPressSeries[TrendMetricId.MUSCLE_HAMSTRINGS_LOAD_DAILY].orEmpty().isEmpty())
    }

    @Test
    fun weightedWeeklyAnatomyGraphUsesCorrectedRelationCoefficients() {
        val exercises = repository.exercises(includeHistory = true).associateBy { exercise -> exercise.stableKey }
        val week = LocalDate.of(2026, 10, 12)

        val pallof = weeklySnapshotFor(exercises.getValue("band_pallof_press"))
        assertEquals(1_000.0, pallof.cell(AnalysisFeatureKey.anatomy("ANTERIOR_CORE"), week)?.value ?: 0.0, 0.01)
        assertEquals(1_000.0, pallof.cell(AnalysisFeatureKey.anatomy("LATERAL_CORE"), week)?.value ?: 0.0, 0.01)
        assertEquals(0.0, pallof.cell(AnalysisFeatureKey.anatomy("CHEST"), week)?.value ?: 0.0, 0.01)

        val row = weeklySnapshotFor(exercises.getValue("chest_supported_row_machine"))
        assertEquals(1_000.0, row.cell(AnalysisFeatureKey.anatomy("BACK_LATS"), week)?.value ?: 0.0, 0.01)
        assertEquals(500.0, row.cell(AnalysisFeatureKey.anatomy("BICEPS"), week)?.value ?: 0.0, 0.01)
        assertEquals(500.0, row.cell(AnalysisFeatureKey.anatomy("SHOULDERS"), week)?.value ?: 0.0, 0.01)
        assertEquals(0.0, row.cell(AnalysisFeatureKey.anatomy("CHEST"), week)?.value ?: 0.0, 0.01)

        val crossBodyCurl = weeklySnapshotFor(exercises.getValue("ex_b69ab702"))
        assertEquals(1_000.0, crossBodyCurl.cell(AnalysisFeatureKey.anatomy("BICEPS"), week)?.value ?: 0.0, 0.01)
        assertEquals(0.0, crossBodyCurl.cell(AnalysisFeatureKey.anatomy("CHEST"), week)?.value ?: 0.0, 0.01)

        val legPress = weeklySnapshotFor(exercises.getValue("ex_ab468462"))
        assertEquals(1_000.0, legPress.cell(AnalysisFeatureKey.anatomy("QUADS"), week)?.value ?: 0.0, 0.01)
        assertEquals(500.0, legPress.cell(AnalysisFeatureKey.anatomy("GLUTES"), week)?.value ?: 0.0, 0.01)
        assertEquals(0.0, legPress.cell(AnalysisFeatureKey.anatomy("HAMSTRINGS"), week)?.value ?: 0.0, 0.01)

        val adduction = weeklySnapshotFor(exercises.getValue("cable_hip_adduction"))
        assertEquals(1_000.0, adduction.cell(AnalysisFeatureKey.anatomy("HIP_ADDUCTOR"), week)?.value ?: 0.0, 0.01)
        assertEquals(0.0, adduction.cell(AnalysisFeatureKey.anatomy("GLUTES"), week)?.value ?: 0.0, 0.01)
        assertEquals(0.0, adduction.cell(AnalysisFeatureKey.anatomy("ANTERIOR_CORE"), week)?.value ?: 0.0, 0.01)

        val abduction = weeklySnapshotFor(exercises.getValue("ex_728da646"))
        assertEquals(1_000.0, abduction.cell(AnalysisFeatureKey.anatomy("GLUTE_MEDIUS"), week)?.value ?: 0.0, 0.01)
        assertEquals(0.0, abduction.cell(AnalysisFeatureKey.anatomy("ANTERIOR_CORE"), week)?.value ?: 0.0, 0.01)

        val copenhagen = weeklySnapshotFor(exercises.getValue("ex_a8385c4a"))
        assertEquals(1_000.0, copenhagen.cell(AnalysisFeatureKey.anatomy("HIP_ADDUCTOR"), week)?.value ?: 0.0, 0.01)
        assertEquals(500.0, copenhagen.cell(AnalysisFeatureKey.anatomy("LATERAL_CORE"), week)?.value ?: 0.0, 0.01)
        assertEquals(0.0, copenhagen.cell(AnalysisFeatureKey.anatomy("GLUTES"), week)?.value ?: 0.0, 0.01)
    }

    private fun weeklySnapshotFor(exercise: com.training.trackplanner.data.Exercise) =
        WeeklyAnalysisFeatureSnapshotBuilder.build(
            today = LocalDate.of(2026, 10, 14),
            metricSeries = emptyMap(),
            entriesWithSets = listOf(
                WorkoutEntryWithSets(
                    entry = WorkoutEntry(
                        date = "2026-10-14",
                        exerciseStableKey = exercise.stableKey,
                        exerciseName = exercise.name,
                        category = exercise.category
                    ),
                    sets = listOf(
                        WorkoutSet(
                            entryId = 1,
                            setIndex = 1,
                            weightKg = 100.0,
                            reps = 10,
                            confirmed = true
                        )
                    )
                )
            ),
            exercises = listOf(exercise),
            dailyMetrics = emptyList(),
            initialProfile = null,
            muscleRelations = repository.muscleRelations(),
            sourceRevision = 1L
        )

    private fun seriesFor(exercise: com.training.trackplanner.data.Exercise) =
        StrengthAndMuscleMetricSeriesBuilder.build(
            entriesWithSets = listOf(
                WorkoutEntryWithSets(
                    entry = WorkoutEntry(
                        date = "2026-10-14",
                        exerciseStableKey = exercise.stableKey,
                        exerciseName = exercise.name,
                        category = exercise.category
                    ),
                    sets = listOf(
                        WorkoutSet(
                            entryId = 1,
                            setIndex = 1,
                            weightKg = 100.0,
                            reps = 10,
                            confirmed = true
                        )
                    )
                )
            ),
            exercises = listOf(exercise)
        )

    private fun rowRelations() = mapOf(
        "BACK_LATS" to primary(),
        "BICEPS" to secondary(),
        "SHOULDERS" to secondary()
    )

    private fun invertedRowRelations() = rowRelations()

    private fun legPressRelations() = mapOf("QUADS" to primary(), "GLUTES" to secondary())

    private fun rowContributions() = mapOf(
        MuscleBucket.BACK_LATS to 1.0,
        MuscleBucket.BICEPS to 0.5,
        MuscleBucket.SHOULDERS to 0.5
    )

    private fun invertedRowContributions() = rowContributions()

    private fun legPressContributions() = mapOf(MuscleBucket.QUADS to 1.0, MuscleBucket.GLUTES to 0.5)

    private fun primary(): Pair<String, Double?> = "PRIMARY" to 1.0

    private fun secondary(): Pair<String, Double?> = "SECONDARY" to 0.5

    private companion object {
        val CHEST_TARGETS = setOf(
            "band_pallof_press", "cable_pallof_press", "chest_supported_row_machine",
            "dumbbell_chest_supported_row", "ex_6a966452", "dumbbell_spider_curl",
            "ez_bar_spider_curl", "ex_b69ab702", "ex_6466fe77",
            "gymnastic_ring_inverted_row", "suspension_trainer_inverted_row"
        )
        val LEG_PRESS_KEYS = setOf("ex_ab468462", "ex_a091b9fe")
    }
}

package com.training.trackplanner.analysis.readiness

import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.features.AnalysisFeatureExtractor
import com.training.trackplanner.analysis.features.ExerciseAnalysisMapper
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CanonicalReadinessMuscleSourceTest {
    private val repo = CanonicalExerciseMetadataRepository(
        ApplicationProvider.getApplicationContext<android.content.Context>()
    )
    private val roles = repo.muscleRoleResolver()
    private val aggregate = DailyAnalysisLoadAggregator()

    private fun exercise(key: String): Exercise =
        repo.exercises().first { it.stableKey == key }.copy(localLoadWeight = 1.0)

    private fun fatigue(exercise: Exercise) = aggregate.aggregate(
        entriesWithSets = listOf(
            WorkoutEntryWithSets(
                entry = WorkoutEntry(
                    date = "2026-10-15", exerciseStableKey = exercise.stableKey,
                    exerciseName = exercise.name, category = exercise.category
                ),
                sets = listOf(
                    WorkoutSet(entryId = 1, setIndex = 1, reps = 6, weightKg = 100.0, confirmed = true)
                )
            )
        ),
        exerciseMap = mapOf(exercise.stableKey to exercise),
        canonicalMuscleRoles = roles
    ).single()

    @Test
    fun squatIgnoresCorruptLegacyHamstringsAndUnrelatedArmOverrides() {
        val squat = exercise("barbell_back_squat")
        val corrupted = squat.copy(primaryMuscles = "HAMSTRING,BICEPS", secondaryMuscles = "TRICEPS")
        assertEquals(fatigue(squat).bodyPartLoads, fatigue(corrupted).bodyPartLoads)
        val body = fatigue(corrupted).bodyPartLoads
        assertTrue(body.getValue("quads") > 0.0)
        assertTrue(body.getValue("glutes") > 0.0)
        assertFalse("hamstrings" in body)
        assertFalse("elbow_flexors" in body)
        assertFalse("elbow_extensors" in body)
    }

    @Test
    fun benchUsesRoleSpecificFatigueShareNotMuscleGraphShare() {
        val bench = exercise("barbell_bench_press").copy(
            primaryMuscles = "HAMSTRING", secondaryMuscles = "BICEPS"
        )
        val parts = fatigue(bench).bodyPartLoads
        assertTrue(parts.getValue("chest") > parts.getValue("shoulders"))
        assertEquals(
            parts.getValue("chest") * TodayReadinessConstants.LOCAL_SECONDARY_SHARE,
            parts.getValue("shoulders"), 0.0001
        )
        assertFalse("hamstrings" in parts)
        assertFalse("elbow_flexors" in parts)
    }

    @Test
    fun deadliftBackStabilizationDoesNotBecomeAPrimaryRegionLoad() {
        val deadlift = exercise("barbell_deadlift").copy(primaryMuscles = "BACK,LAT")
        val parts = fatigue(deadlift).bodyPartLoads
        assertTrue(parts.getValue("hamstrings") > 0.0)
        assertTrue(parts.getValue("glutes") > 0.0)
        assertFalse("lats_upper_back" in parts)
        // Axial low-back stress remains a separate, existing readiness policy.
    }

    @Test
    fun unknownKeyCannotInheritBuiltInMusclesFromNameOrOldStrings() {
        val impostor = Exercise(
            stableKey = "unknown_bench", name = "벤치프레스", category = "근력운동",
            primaryMuscles = "CHEST", localLoadWeight = 1.0
        )
        val features = ExerciseAnalysisMapper.fromExercise(
            impostor, canonicalMuscles = roles.lookup(impostor)
        )
        assertTrue(features.primaryMuscles.isEmpty())
    }

    @Test
    fun customExerciseRequiresConfirmedRolesBeforeReadinessMuscleAttribution() {
        val custom = Exercise(
            stableKey = "user_custom_a", name = "사용자 운동", category = "근력운동",
            isCustom = true, primaryMuscles = "CHEST", secondaryMuscles = "TRICEPS"
        )
        val unreviewed = ExerciseAnalysisMapper.fromExercise(
            custom, canonicalMuscles = roles.lookup(custom)
        )
        assertTrue(unreviewed.primaryMuscles.isEmpty())
        assertTrue(unreviewed.secondaryMuscles.isEmpty())
        val codec = com.training.trackplanner.data.UserMuscleRoleCodec
        val choice = listOf(
            codec.entry(custom.stableKey, "CHEST", com.training.trackplanner.data.CanonicalMuscleRole.PRIMARY),
            codec.entry(custom.stableKey, "TRICEPS", com.training.trackplanner.data.CanonicalMuscleRole.SECONDARY)
        )
        val override = com.training.trackplanner.data.ExerciseMetadataUserOverrideEntity(
            stableKey = custom.stableKey,
            fieldScope = com.training.trackplanner.data.ExerciseMetadataFieldScope.MUSCLE_RELATION.name,
            fieldKey = codec.FIELD_KEY,
            valueEncoding = com.training.trackplanner.data.ExerciseMetadataValueEncoding.TOKEN_SET.name,
            value = codec.encode(choice),
            isExplicitEmpty = false,
            source = com.training.trackplanner.data.ExerciseMetadataOverrideSource.USER_EDIT.name,
            semanticCanonicalRevisionAtEdit = "test"
        )
        val reviewed = ExerciseAnalysisMapper.fromExercise(
            custom, canonicalMuscles = roles.withUserOverrides(listOf(override)).lookup(custom)
        )
        assertEquals(setOf("CHEST"), reviewed.primaryMuscles)
        assertEquals(setOf("TRICEPS"), reviewed.secondaryMuscles)
    }

    @Test
    fun lateralCoreCanonicalCodeIsNotMisreadAsLatissimusInReadiness() {
        val pallof = exercise("band_pallof_press").copy(
            primaryMuscles = "LAT", secondaryMuscles = "BACK"
        )
        val parts = fatigue(pallof).bodyPartLoads
        assertTrue(parts.getValue("core_abs_obliques") > 0.0)
        assertFalse("lats_upper_back" in parts)
    }

    @Test
    fun canonicalLateralCoreAndCanonicalBackRemainDistinctInFeatureProjection() {
        val pallof = exercise("band_pallof_press")
        val core = ExerciseAnalysisMapper.fromExercise(
            pallof, canonicalMuscles = roles.lookup(pallof)
        )
        assertTrue("LATERAL_CORE" in core.primaryMuscles)
        assertFalse("BACK_LATS" in core.primaryMuscles)
        val row = exercise("ex_de46b7f6")
        val back = ExerciseAnalysisMapper.fromExercise(row, canonicalMuscles = roles.lookup(row))
        assertTrue("BACK_LATS" in back.primaryMuscles)
        assertFalse("LATERAL_CORE" in back.primaryMuscles)
    }

    @Test
    fun sharedAnalysisFeatureExtractorReceivesCanonicalMuscleRoles() {
        val press = exercise("barbell_bench_press").copy(
            primaryMuscles = "BICEPS", secondaryMuscles = "HAMSTRING"
        )
        val features = AnalysisFeatureExtractor.fromExercise(
            press, canonicalMuscles = roles.lookup(press)
        )
        assertEquals(setOf("CHEST"), features.primaryMuscles)
        assertTrue("TRICEPS" in features.secondaryMuscles)
        assertFalse("BICEPS" in features.primaryMuscles)
        assertFalse("HAMSTRING" in features.secondaryMuscles)
    }

    @Test
    fun roleAssignmentDoesNotChangeSystemicLoad() {
        val squat = exercise("barbell_back_squat")
        val corrupted = squat.copy(primaryMuscles = "HAMSTRING", secondaryMuscles = "BICEPS")
        val first = fatigue(squat)
        val second = fatigue(corrupted)
        assertEquals(first.categoryLoads, second.categoryLoads)
        assertEquals(first.completedSetCount, second.completedSetCount)
    }
}

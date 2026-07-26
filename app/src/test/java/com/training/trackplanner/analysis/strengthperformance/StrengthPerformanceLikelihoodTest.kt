package com.training.trackplanner.analysis.strengthperformance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.DailyCheckIn
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrengthPerformanceLikelihoodTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = StrengthPerformanceRegistry.fromContext(context)
    private val curves = RepetitionCurveRegistry.fromContext(context)
    private val rirPolicy = RpeRirPolicy.fromContext(context)

    @Test
    fun `target registry is string keyed and weighted pull-up is first class`() {
        assertEquals(4, registry.targets().size)
        assertEquals(
            StrengthPerformanceRegistry.WEIGHTED_PULL_UP,
            registry.directTarget("ex_e41f4c2b")?.targetKey
        )
        assertTrue(registry.loading("ex_32219f7a", StrengthPerformanceRegistry.BENCH_PRESS)!!.loadingWeight > 0.0)
        assertTrue(registry.orderedFactorSchema().any { key -> key.value == "strength.factor.target.weighted_pull_up" })

        val synthetic = StrengthPerformanceRegistry.fromCsv(
            targetCsv = """
                targetKey,displayNameKo,anchorStableKeys,closeVariationStableKeys,loadSemantics,curveSelectionPolicyKey,sharedFactorLoadings,targetSpecificFactorKey,supportedRepMin,supportedRepMax,directObservationPolicy,enabled,configVersion
                strength.synthetic,Synthetic,synthetic_anchor,,EXTERNAL_LOAD,curve.policy.general,strength.factor.synthetic:1.0,strength.factor.target.synthetic,1,12,RPE10_ONLY,true,test
            """.trimIndent(),
            proxyCsv = """
                exerciseStableKey,targetKey,relationship,loadingWeight,factorLoadings,loadSemantics,configVersion
                synthetic_anchor,strength.synthetic,DIRECT_ANCHOR,1.0,strength.factor.target.synthetic:1.0,EXTERNAL_LOAD,test
            """.trimIndent()
        )
        assertEquals("strength.synthetic", synthetic.targets().single().targetKey.value)
    }

    @Test
    fun `weighted pull-up resolves total load with bodyweight priority and age uncertainty`() {
        val exactResolver = StrengthPerformanceLoadResolver(
            dailyMetrics = listOf(DailyMetric("2026-07-01", bodyWeightKg = 80.0)),
            dailyCheckIns = listOf(DailyCheckIn("2026-07-10", bodyWeightKg = 82.0)),
            initialProfile = InitialUserProfile(bodyWeightKg = 75.0)
        )
        val set = set(id = 1, reps = 1, weight = 20.0, rpe = 10.0)
        val exact = exactResolver.resolve(
            LocalDate.parse("2026-07-10"), set, StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD
        )
        assertEquals(102.0, exact.totalLoadKg!!, 0.0)
        assertEquals(BodyWeightSource.EXACT_DATE, exact.bodyWeightSource)

        val prior = exactResolver.resolve(
            LocalDate.parse("2026-07-20"), set, StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD
        )
        assertEquals(102.0, prior.totalLoadKg!!, 0.0)
        assertEquals(BodyWeightSource.MOST_RECENT_PRIOR, prior.bodyWeightSource)
        assertEquals(10L, prior.bodyWeightAgeDays)
        assertTrue(prior.loadVarianceContribution > exact.loadVarianceContribution)

        val profile = StrengthPerformanceLoadResolver(emptyList(), emptyList(), InitialUserProfile(bodyWeightKg = 75.0))
            .resolve(LocalDate.parse("2026-07-20"), set, StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD)
        assertEquals(95.0, profile.totalLoadKg!!, 0.0)
        assertEquals(BodyWeightSource.INITIAL_PROFILE, profile.bodyWeightSource)

        val missing = StrengthPerformanceLoadResolver(emptyList(), emptyList(), null)
            .resolve(LocalDate.parse("2026-07-20"), set, StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD)
        assertFalse(missing.isResolved)
        assertNull(missing.totalLoadKg)
    }

    @Test
    fun `one rep RPE 10 is direct while lower and missing RPE are lower bounds`() {
        val curve = curves.resolve("barbell_bench_press")
        val loadResolver = StrengthPerformanceLoadResolver(emptyList(), emptyList(), null)
        fun evidence(reps: Int, rpe: Double?): StrengthSetEvidence {
            val source = set(id = reps.toLong(), reps = reps, weight = 100.0, rpe = rpe)
            return checkNotNull(
                StrengthSetLikelihoodBuilder.build(
                    source, null,
                    loadResolver.resolve(LocalDate.parse("2026-07-10"), source, StrengthLoadSemantics.EXTERNAL_LOAD),
                    curve,
                    rirPolicy
                )
            )
        }
        val direct = evidence(1, 10.0)
        assertEquals(StrengthObservationType.DIRECT_1RM, direct.observationType)
        assertEquals(100.0, direct.capacityCenterKg, 1e-12)
        assertTrue(direct.logVariance >= StrengthSetLikelihoodBuilder.DIRECT_VARIANCE_FLOOR)

        assertEquals(StrengthObservationType.STRONG_NRM, evidence(5, 10.0).observationType)
        assertEquals(StrengthObservationType.RPE_MIXTURE_OBSERVATION, evidence(1, 9.0).observationType)
        assertEquals(StrengthObservationType.MISSING_RPE_LOWER_CENSORED, evidence(1, null).observationType)
        assertTrue(evidence(5, 7.0).logVariance > evidence(5, 9.0).logVariance)
        assertTrue(evidence(5, null).logVariance > evidence(5, 9.0).logVariance)
    }

    @Test
    fun `zero repetitions at RPE 10 is retained as a failed upper-bound signal`() {
        val curve = curves.resolve("barbell_bench_press")
        val source = set(id = 9, reps = 0, weight = 100.0, rpe = 10.0)
        val evidence = checkNotNull(
            StrengthSetLikelihoodBuilder.build(
                source,
                null,
                StrengthPerformanceLoadResolver(emptyList(), emptyList(), null)
                    .resolve(LocalDate.parse("2026-07-10"), source, StrengthLoadSemantics.EXTERNAL_LOAD),
                curve,
                rirPolicy
            )
        )

        assertEquals(StrengthObservationType.FAILURE_UPPER_CENSORED, evidence.observationType)
        assertEquals(100.0, evidence.capacityCenterKg, 0.0)
        assertFalse(evidence.isStrong)
    }

    @Test
    fun `session aggregation is one observation and contradictory evidence widens it`() {
        val resolver = StrengthPerformanceLoadResolver(emptyList(), emptyList(), null)
        val exercise = Exercise(id = 1, name = "Bench", category = "strength", stableKey = "barbell_bench_press")
        val consistent = session(
            exercise,
            listOf(set(1, 5, 100.0, 10.0), set(2, 5, 101.0, 10.0)),
            resolver
        )
        val contradictory = session(
            exercise,
            listOf(set(1, 5, 60.0, 10.0), set(2, 5, 120.0, 10.0)),
            resolver
        )
        assertEquals(2, consistent.sourceSetIds.size)
        assertEquals(StrengthObservationType.STRONG_NRM, consistent.observationType)
        assertTrue(contradictory.diagnostics.contains("CONTRADICTORY_SAME_SESSION_EVIDENCE"))
        assertTrue(contradictory.logVariance > consistent.logVariance)
    }

    @Test
    fun `multi-repetition strong evidence updates bounded personal curve posterior`() {
        val profile = checkNotNull(curves.profile(RepetitionCurveRegistry.GENERAL_PROFILE_ID))
        val initial = PersonalCurveCalibrator.initial("exercise:test", profile, 1L)
        val resolver = StrengthPerformanceLoadResolver(emptyList(), emptyList(), null)
        val curve = curves.resolve("barbell_back_squat")
        val evidence = listOf(5, 8).mapIndexed { index, reps ->
            val source = set(index.toLong() + 1, reps, if (reps == 5) 85.0 else 75.0, 10.0)
            checkNotNull(
                StrengthSetLikelihoodBuilder.build(
                    source, null,
                    resolver.resolve(LocalDate.parse("2026-07-10"), source, StrengthLoadSemantics.EXTERNAL_LOAD),
                    curve,
                    rirPolicy
                )
            )
        }
        val updated = PersonalCurveCalibrator.update(initial, profile, evidence, 100.0, 2L)
        assertEquals(1.0, updated.posteriorWeights.sum(), 1e-10)
        assertEquals(2, updated.strongObservationCount)
        assertEquals(2, updated.distinctRepRangeCount)
        assertNotEquals(initial.posteriorFingerprint, updated.posteriorFingerprint)
        assertTrue(updated.meanTheta in -0.35..0.35)
    }

    @Test
    fun `eligible squat metadata receives a conservative relevant-movement proxy`() {
        val squat = Exercise(
            id = 9,
            name = "Front squat",
            category = "Strength",
            stableKey = "front-squat-test",
            movementPattern = "KNEE_DOMINANT_LOWER",
            strengthProgressionGroup = "FRONT_SQUAT",
            estimated1RmEligible = true
        )

        val loading = registry.proxyLoadings(squat).single()
        assertEquals(StrengthPerformanceRegistry.BACK_SQUAT, loading.targetKey)
        assertEquals("METADATA_PROXY", loading.relationship)
        assertTrue(loading.loadingWeight in 0.0..1.0)
    }

    private fun session(
        exercise: Exercise,
        sets: List<WorkoutSet>,
        loadResolver: StrengthPerformanceLoadResolver
    ): StrengthExerciseSessionObservation = checkNotNull(
        StrengthSessionObservationBuilder.build(
            record = WorkoutEntryWithSets(
                entry = WorkoutEntry(id = 10, date = "2026-07-10", exerciseId = exercise.id, exerciseName = exercise.name, category = "strength"),
                sets = sets
            ),
            exercise = exercise,
            registry = registry,
            curveRegistry = curves,
            loadResolver = loadResolver,
            rirPolicy = rirPolicy
        )
    )

    private fun set(id: Long, reps: Int, weight: Double, rpe: Double?): WorkoutSet = WorkoutSet(
        id = id,
        entryId = 10,
        setIndex = id.toInt() - 1,
        reps = reps,
        weightKg = weight,
        confirmed = true,
        rpe = rpe
    )
}

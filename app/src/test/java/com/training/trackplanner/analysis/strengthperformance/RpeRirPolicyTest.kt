package com.training.trackplanner.analysis.strengthperformance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RpeRirPolicyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val policy = RpeRirPolicy.fromContext(context)
    private val curves = RepetitionCurveRegistry.fromContext(context)

    @Test
    fun `policy distributions are normalized monotone and RPE 10 is a point mass`() {
        val distributions = policy.supportedRpes().map { rpe -> checkNotNull(policy.resolve(rpe)) }

        distributions.forEach { distribution ->
            assertEquals(1.0, distribution.probabilities.sumOf(RirProbability::probability), 1e-12)
            assertTrue(distribution.probabilities.all { it.rir >= 0 && it.probability > 0.0 })
        }
        assertEquals(listOf(RirProbability(0, 1.0)), checkNotNull(policy.resolve(10.0)).probabilities)
        assertTrue(distributions.map(ResolvedRirDistribution::expectedRir).zipWithNext().all {
            (lowerRpe, higherRpe) -> lowerRpe >= higherRpe
        })
    }

    @Test
    fun `arbitrary supported RPE is deterministically interpolated`() {
        val first = checkNotNull(policy.resolve(8.7))
        val second = checkNotNull(policy.resolve(8.7))

        assertTrue(first.interpolated)
        assertEquals(first, second)
        assertEquals(1.0, first.probabilities.sumOf(RirProbability::probability), 1e-12)
        assertNull(policy.resolve(5.5))
    }

    @Test
    fun `known RPE likelihood represents RIR uncertainty exactly once`() {
        val source = WorkoutSet(
            id = 1,
            entryId = 1,
            setIndex = 0,
            reps = 5,
            weightKg = 100.0,
            confirmed = true,
            rpe = 8.0
        )
        val evidence = checkNotNull(
            StrengthSetLikelihoodBuilder.build(
                set = source,
                entryRpe = null,
                resolvedLoad = StrengthPerformanceLoadResolver(emptyList(), emptyList(), null).resolve(
                    LocalDate.parse("2026-07-10"),
                    source,
                    StrengthLoadSemantics.EXTERNAL_LOAD
                ),
                curve = curves.resolve("barbell_back_squat"),
                rirPolicy = policy
            )
        )
        val mixture = evidence.likelihood as StrengthSetLikelihood.GaussianMixture
        val mean = mixture.components.sumOf { it.probability * it.logCenter }
        val totalVariance = mixture.components.sumOf {
            it.probability * (it.logVariance + (it.logCenter - mean).pow(2))
        }

        assertEquals(StrengthObservationType.RPE_MIXTURE_OBSERVATION, evidence.observationType)
        assertEquals(totalVariance, evidence.logVariance, 1e-12)
        assertTrue(mixture.components.map(StrengthLikelihoodComponent::rir).containsAll(listOf(1, 2, 3)))
    }

    @Test
    fun `unsupported RIR mass fails closed instead of silently renormalizing`() {
        val source = WorkoutSet(
            id = 2,
            entryId = 1,
            setIndex = 0,
            reps = 20,
            weightKg = 50.0,
            confirmed = true,
            rpe = 6.0
        )
        val result = StrengthSetLikelihoodBuilder.buildResult(
            set = source,
            entryRpe = null,
            resolvedLoad = StrengthPerformanceLoadResolver(emptyList(), emptyList(), null).resolve(
                LocalDate.parse("2026-07-10"),
                source,
                StrengthLoadSemantics.EXTERNAL_LOAD
            ),
            curve = curves.resolve("barbell_back_squat"),
            rirPolicy = policy
        )

        assertNull(result.evidence)
        assertEquals(StrengthObservationType.UNSUPPORTED_REPETITION_RANGE, result.exclusionType)
        assertNotNull(result.diagnostic)
    }
}

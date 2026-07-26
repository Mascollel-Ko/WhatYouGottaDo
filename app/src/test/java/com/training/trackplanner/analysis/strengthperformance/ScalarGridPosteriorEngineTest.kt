package com.training.trackplanner.analysis.strengthperformance

import kotlin.math.ln
import kotlin.math.pow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScalarGridPosteriorEngineTest {
    @Test
    fun `proper likelihood can move an overestimated prior downward`() {
        val likelihood = gaussianLikelihood(ln(100.0), 0.04)
        val result = ScalarGridPosteriorEngine.posteriorMoments(
            priorMean = ln(150.0),
            priorVariance = 0.08,
            likelihood = likelihood
        )

        assertTrue(result.mean < ln(150.0))
        assertTrue(result.mean > ln(100.0))
        assertTrue(result.variance < 0.08)
        assertTrue(result.diagnostics.normalized)
    }

    @Test
    fun `soft lower and upper censor likelihoods update opposite tails`() {
        val lower = ScalarGridLikelihood(
            listOf(ScalarLikelihoodSupport(ln(120.0), 0.20))
        ) { value -> StrengthSetLikelihood.LowerCensored(ln(120.0), 0.20).logValueAt(value) }
        val upper = ScalarGridLikelihood(
            listOf(ScalarLikelihoodSupport(ln(80.0), 0.20))
        ) { value -> StrengthSetLikelihood.UpperCensored(ln(80.0), 0.20).logValueAt(value) }

        assertTrue(
            ScalarGridPosteriorEngine.posteriorMoments(ln(90.0), 0.10, lower).mean > ln(90.0)
        )
        assertTrue(
            ScalarGridPosteriorEngine.posteriorMoments(ln(110.0), 0.10, upper).mean < ln(110.0)
        )
    }

    @Test
    fun `scalar moments project back to a finite symmetric covariance`() {
        val priorMean = doubleArrayOf(ln(100.0), 0.0)
        val priorCovariance = arrayOf(
            doubleArrayOf(0.12, 0.03),
            doubleArrayOf(0.03, 0.08)
        )
        val projection = doubleArrayOf(1.0, 0.5)
        val result = ScalarGridPosteriorEngine.project(
            priorMean,
            priorCovariance,
            projection,
            gaussianLikelihood(ln(125.0), 0.05)
        )
        val repeated = ScalarGridPosteriorEngine.project(
            priorMean,
            priorCovariance,
            projection,
            gaussianLikelihood(ln(125.0), 0.05)
        )

        assertTrue(result.mean.all(Double::isFinite))
        assertEquals(result.covariance[0][1], result.covariance[1][0], 1e-12)
        assertTrue(result.covariance.indices.all { result.covariance[it][it] > 0.0 })
        assertArrayEquals(result.mean, repeated.mean, 0.0)
        result.covariance.indices.forEach { row ->
            assertArrayEquals(result.covariance[row], repeated.covariance[row], 0.0)
        }
        assertEquals(result.diagnostics.fingerprint, repeated.diagnostics.fingerprint)
    }

    private fun gaussianLikelihood(mean: Double, variance: Double): ScalarGridLikelihood =
        ScalarGridLikelihood(listOf(ScalarLikelihoodSupport(mean, kotlin.math.sqrt(variance)))) { value ->
            -0.5 * (ln(2.0 * Math.PI * variance) + (value - mean).pow(2) / variance)
        }
}

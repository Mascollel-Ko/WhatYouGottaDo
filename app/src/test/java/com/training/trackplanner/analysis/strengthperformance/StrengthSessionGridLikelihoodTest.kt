package com.training.trackplanner.analysis.strengthperformance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrengthSessionGridLikelihoodTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val curves = RepetitionCurveRegistry.fromContext(context)
    private val policy = RpeRirPolicy.fromContext(context)
    private val resolver = StrengthPerformanceLoadResolver(emptyList(), emptyList(), null)

    @Test
    fun `session likelihood and grid moments are invariant to set order`() {
        val evidence = listOf(
            evidence(1, 5, 100.0, 8.0),
            evidence(2, 5, 102.0, 9.0),
            evidence(3, 4, 105.0, 9.5)
        )
        val reference = StrengthExerciseSessionLikelihood(evidence).asScalarLikelihood()
        val referenceMoments = ScalarGridPosteriorEngine.posteriorMoments(
            priorMean = kotlin.math.ln(125.0),
            priorVariance = 0.12,
            likelihood = reference
        )

        evidence.permutations().forEach { permutation ->
            val likelihood = StrengthExerciseSessionLikelihood(permutation).asScalarLikelihood()
            listOf(4.5, 4.8, 5.1).forEach { point ->
                assertEquals(reference.logValueAt(point), likelihood.logValueAt(point), 1e-12)
            }
            val moments = ScalarGridPosteriorEngine.posteriorMoments(
                priorMean = kotlin.math.ln(125.0),
                priorVariance = 0.12,
                likelihood = likelihood
            )
            assertEquals(referenceMoments.mean, moments.mean, 1e-12)
            assertEquals(referenceMoments.variance, moments.variance, 1e-12)
        }
    }

    @Test
    fun `common day effect prevents same-day sets from behaving like independent dates`() {
        val one = StrengthExerciseSessionLikelihood(listOf(evidence(1, 5, 100.0, 8.0)))
        val four = StrengthExerciseSessionLikelihood(
            (1L..4L).map { id -> evidence(id, 5, 100.0, 8.0) }
        )
        val oneMoments = ScalarGridPosteriorEngine.posteriorMoments(
            kotlin.math.ln(125.0), 0.16, one.asScalarLikelihood()
        )
        val fourMoments = ScalarGridPosteriorEngine.posteriorMoments(
            kotlin.math.ln(125.0), 0.16, four.asScalarLikelihood()
        )

        assertTrue(fourMoments.variance < oneMoments.variance)
        assertTrue(fourMoments.variance > oneMoments.variance / 4.0)
    }

    private fun evidence(id: Long, reps: Int, weight: Double, rpe: Double): StrengthSetEvidence {
        val set = WorkoutSet(
            id = id,
            entryId = 1,
            setIndex = id.toInt() - 1,
            reps = reps,
            weightKg = weight,
            confirmed = true,
            rpe = rpe
        )
        return checkNotNull(
            StrengthSetLikelihoodBuilder.build(
                set = set,
                entryRpe = null,
                resolvedLoad = resolver.resolve(
                    LocalDate.parse("2026-07-10"),
                    set,
                    StrengthLoadSemantics.EXTERNAL_LOAD
                ),
                curve = curves.resolve("barbell_back_squat"),
                rirPolicy = policy
            )
        )
    }

    private fun <T> List<T>.permutations(): List<List<T>> =
        if (size <= 1) listOf(this) else indices.flatMap { index ->
            val head = this[index]
            (take(index) + drop(index + 1)).permutations().map { tail -> listOf(head) + tail }
        }
}

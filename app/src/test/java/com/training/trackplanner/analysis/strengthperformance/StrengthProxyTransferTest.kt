package com.training.trackplanner.analysis.strengthperformance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrengthProxyTransferTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = StrengthPerformanceRegistry.fromContext(context)
    private val curves = RepetitionCurveRegistry.fromContext(context)
    private val rirPolicy = RpeRirPolicy.fromContext(context)
    private val exercise = Exercise(
        name = "Incline dumbbell press",
        category = "Strength",
        stableKey = "ex_a61f1e96"
    )

    @Test
    fun `registry keeps non-direct proxies on shared factors only`() {
        val proxies = registry.targets().flatMap { target ->
            listOfNotNull(registry.loading(exercise.stableKey, target.targetKey))
        }.filterNot(StrengthProxyLoadingSpec::isDirectAnchor)

        assertTrue(proxies.isNotEmpty())
        assertTrue(proxies.all { it.proxyMode == StrengthProxyMode.LOCAL_INNOVATION_SHARED_ONLY })
        assertTrue(proxies.all { row ->
            row.factorLoadings.keys.none { it.value.startsWith("strength.factor.target.") }
        })
        val vector = StrengthProxyTransfer.vector(registry.orderedFactorSchema(), proxies.single())
        registry.orderedFactorSchema().forEachIndexed { index, factor ->
            if (factor.value.startsWith("strength.factor.target.")) assertEquals(0.0, vector[index], 0.0)
        }
    }

    @Test
    fun `first local session does not move bench and second positive innovation moves shared factors only`() {
        val initial = StrengthPosteriorModel.initialState(registry, null)
        val first = compute(initial, emptyMap(), observation("2026-06-01", 50.0), "event-1")
        assertArrayEquals(initial.mean, first.state.mean, 0.0)
        assertTrue(first.proxyTransfers.isEmpty())

        val targetFactor = StrengthFactorKey("strength.factor.target.bench_press")
        val targetIndex = initial.orderedFactorSchema.indexOf(targetFactor)
        val second = compute(
            first.state,
            first.localStates,
            observation("2026-06-15", 60.0),
            "event-2"
        )

        assertEquals(1, second.proxyTransfers.size)
        assertEquals(first.state.mean[targetIndex], second.state.mean[targetIndex], 0.0)
        assertTrue(second.state.mean.indices.any { index ->
            !initial.orderedFactorSchema[index].value.startsWith("strength.factor.target.") &&
                second.state.mean[index] > first.state.mean[index]
        })
        val bench = checkNotNull(registry.target(StrengthPerformanceRegistry.BENCH_PRESS))
        assertTrue(
            StrengthPosteriorModel.distribution(second.state, bench).median >
                StrengthPosteriorModel.distribution(first.state, bench).median
        )
    }

    @Test
    fun `proxy innovation is invariant to a common load scale offset`() {
        fun transfer(firstLoad: Double, secondLoad: Double): StrengthProxyTransferRecord {
            val initial = StrengthPosteriorModel.initialState(registry, null)
            val first = compute(initial, emptyMap(), observation("2026-06-01", firstLoad), "event-1")
            return compute(first.state, first.localStates, observation("2026-06-15", secondLoad), "event-2")
                .proxyTransfers.single()
        }

        val base = transfer(50.0, 60.0)
        val doubled = transfer(100.0, 120.0)
        assertEquals(base.innovationResidualLog, doubled.innovationResidualLog, 1e-10)
        assertEquals(base.innovationVariance, doubled.innovationVariance, 1e-10)
    }

    @Test
    fun `simultaneous proxy update is input-order invariant`() {
        val state = StrengthPosteriorModel.initialState(registry, null)
        val loading = checkNotNull(registry.loading(exercise.stableKey, StrengthPerformanceRegistry.BENCH_PRESS))
        fun row(key: String, residual: Double) = StrengthProxyTransferRecord(
            eventUuid = "event",
            sessionKey = key,
            exerciseStableKey = exercise.stableKey,
            targetKey = loading.targetKey,
            factorLoadings = loading.factorLoadings,
            transferCoefficient = loading.transferCoefficient,
            innovationResidualLog = residual,
            innovationVariance = 0.03,
            transferLogVariance = loading.transferLogVariance,
            evidenceFingerprint = key
        )
        val first = row("a", 0.08)
        val second = row("b", -0.02)
        val loadingMap = mapOf("a" to loading, "b" to loading)
        val forward = StrengthProxyTransfer.update(state, listOf(first, second), loadingMap)
        val reverse = StrengthProxyTransfer.update(state, listOf(second, first), loadingMap)

        assertArrayEquals(forward.mean, reverse.mean, 1e-12)
        forward.covariance.indices.forEach { index ->
            assertArrayEquals(forward.covariance[index], reverse.covariance[index], 1e-12)
        }
    }

    private fun compute(
        state: StrengthPosteriorState,
        locals: Map<String, StrengthExerciseLocalState>,
        observation: StrengthExerciseSessionObservation,
        eventUuid: String
    ): StrengthPosteriorComputation = StrengthPosteriorModel.compute(
        eventUuid = eventUuid,
        date = observation.date,
        currentState = state,
        observations = listOf(observation),
        registry = registry,
        curves = curves,
        curvePosteriorBySubject = emptyMap(),
        currentLocalStates = locals,
        now = 1_000L
    )

    private fun observation(date: String, weight: Double): StrengthExerciseSessionObservation =
        checkNotNull(
            StrengthSessionObservationBuilder.build(
                record = WorkoutEntryWithSets(
                    entry = WorkoutEntry(
                        id = date.hashCode().toLong(),
                        date = date,
                        exerciseStableKey = exercise.stableKey,
                        exerciseName = exercise.name,
                        category = exercise.category
                    ),
                    sets = listOf(
                        WorkoutSet(
                            id = date.hashCode().toLong(),
                            entryId = date.hashCode().toLong(),
                            setIndex = 0,
                            reps = 8,
                            weightKg = weight,
                            confirmed = true,
                            rpe = 10.0
                        )
                    )
                ),
                exercise = exercise,
                registry = registry,
                curveRegistry = curves,
                loadResolver = StrengthPerformanceLoadResolver(emptyList(), emptyList(), null),
                rirPolicy = rirPolicy
            )
        )
}

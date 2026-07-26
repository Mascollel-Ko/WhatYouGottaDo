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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.EigenDecomposition

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrengthPosteriorModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = StrengthPerformanceRegistry.fromContext(context)
    private val curves = RepetitionCurveRegistry.fromContext(context)

    @Test
    fun `versioned vector codec round trips and rejects checksum changes`() {
        val values = doubleArrayOf(1.0, -2.5, Math.PI, 0.0)
        val encoded = VersionedDoubleArrayCodec.encode(values)

        assertArrayEquals(values, VersionedDoubleArrayCodec.decode(encoded), 0.0)
        assertTrue(runCatching { VersionedDoubleArrayCodec.decode(encoded.replace("sha256=", "sha256=0")) }.isFailure)

        val covariance = arrayOf(
            doubleArrayOf(2.0, 0.25, -0.5),
            doubleArrayOf(0.25, 3.0, 0.75),
            doubleArrayOf(-0.5, 0.75, 4.0)
        )
        val unpacked = VersionedDoubleArrayCodec.unpackLowerTriangle(
            VersionedDoubleArrayCodec.packLowerTriangle(covariance),
            covariance.size
        )
        covariance.indices.forEach { row ->
            assertArrayEquals(covariance[row], unpacked[row], 0.0)
        }
    }

    @Test
    fun `direct one rep observation narrows the target posterior above its floor`() {
        val initial = StrengthPosteriorModel.initialState(registry, null)
        val target = checkNotNull(registry.target(StrengthPerformanceRegistry.BENCH_PRESS))
        val before = StrengthPosteriorModel.distribution(initial, target)
        val result = compute(initial, observation(reps = 1, weight = 100.0, rpe = 10.0))
        val after = StrengthPosteriorModel.distribution(result.state, target)
        val history = result.history.single { row -> row.targetKey == target.targetKey.value }

        assertTrue(after.median > before.median)
        assertTrue(after.logVariance < before.logVariance)
        assertTrue(after.logVariance >= StrengthSetLikelihoodBuilder.DIRECT_VARIANCE_FLOOR)
        assertEquals(100.0, history.directObservedLoad!!, 0.0)
        assertEquals(StrengthObservationType.DIRECT_1RM.name, history.directObservationType)
        assertTrue(history.priorLow95!! < history.priorMedian!! && history.priorMedian!! < history.priorHigh95!!)
        assertTrue(history.posteriorLow95!! < history.posteriorMedian!! && history.posteriorMedian!! < history.posteriorHigh95!!)
    }

    @Test
    fun `submaximal evidence below the prior remains a conservative lower bound`() {
        val initial = StrengthPosteriorModel.initialState(registry, null)
        val result = compute(initial, observation(reps = 1, weight = 20.0, rpe = 8.0))

        assertArrayEquals(initial.mean, result.state.mean, 0.0)
        initial.covariance.indices.forEach { row ->
            assertArrayEquals(initial.covariance[row], result.state.covariance[row], 0.0)
        }
        assertEquals(
            StrengthObservationType.CONSERVATIVE_LOWER_BOUND.name,
            result.history.single { it.targetKey == StrengthPerformanceRegistry.BENCH_PRESS.value }.directObservationType
        )
    }

    @Test
    fun `zero repetition RPE 10 failure lowers an overconfident prior without becoming a one rep max`() {
        val initial = StrengthPosteriorModel.initialState(registry, null)
        val target = checkNotNull(registry.target(StrengthPerformanceRegistry.BENCH_PRESS))
        val before = StrengthPosteriorModel.distribution(initial, target)
        val result = compute(initial, observation(reps = 0, weight = 40.0, rpe = 10.0))
        val after = StrengthPosteriorModel.distribution(result.state, target)

        assertTrue(after.median < before.median)
        assertEquals(
            StrengthObservationType.FAILURE_UPPER_BOUND.name,
            result.history.single { row -> row.targetKey == StrengthPerformanceRegistry.BENCH_PRESS.value }.directObservationType
        )
    }

    @Test
    fun `eight relevant squat sessions move the posterior from its initial prior`() {
        val target = checkNotNull(registry.target(StrengthPerformanceRegistry.BACK_SQUAT))
        var state = StrengthPosteriorModel.initialState(registry, null)
        val before = StrengthPosteriorModel.distribution(state, target)
        val weeklyPriorMedians = mutableListOf<Double>()
        var previousPosteriorMedian: Double? = null
        repeat(8) { index ->
            val date = LocalDate.parse("2026-07-01").plusWeeks(index.toLong())
            val exercise = Exercise(
                id = index.toLong() + 1,
                name = "Front squat",
                category = "Strength",
                stableKey = "front-squat-$index",
                movementPattern = "KNEE_DOMINANT_LOWER",
                strengthProgressionGroup = "FRONT_SQUAT",
                estimated1RmEligible = true
            )
            val record = WorkoutEntryWithSets(
                entry = WorkoutEntry(id = index.toLong() + 1, date = date.toString(), exerciseId = exercise.id, exerciseName = exercise.name, category = exercise.category),
                sets = listOf(WorkoutSet(id = index.toLong() + 1, entryId = exercise.id, setIndex = 1, reps = 5, weightKg = 100.0, confirmed = true, rpe = 10.0))
            )
            val observation = checkNotNull(
                StrengthSessionObservationBuilder.build(
                    record = record,
                    exercise = exercise,
                    registry = registry,
                    curveRegistry = curves,
                    loadResolver = StrengthPerformanceLoadResolver(emptyList(), emptyList(), null)
                )
            )
            val result = StrengthPosteriorModel.compute(
                eventUuid = "squat-$index",
                date = date,
                currentState = state,
                observations = listOf(observation),
                registry = registry,
                curves = curves,
                curvePosteriorBySubject = emptyMap(),
                now = 3_000L + index
            )
            val point = result.history.single { row -> row.targetKey == target.targetKey.value }
            weeklyPriorMedians += checkNotNull(point.priorMedian)
            previousPosteriorMedian?.let { previous ->
                assertEquals(previous, checkNotNull(point.priorMedian), 1e-9)
            }
            previousPosteriorMedian = point.posteriorMedian
            state = result.state
        }

        assertTrue(weeklyPriorMedians.last() > weeklyPriorMedians.first())
        assertTrue(StrengthPosteriorModel.distribution(state, target).median > before.median)
    }

    @Test
    fun `identical inputs produce identical state history and evidence fingerprints`() {
        val initial = StrengthPosteriorModel.initialState(registry, null)
        val observation = observation(reps = 5, weight = 80.0, rpe = 10.0)
        val first = compute(initial, observation)
        val second = compute(initial, observation)

        assertEquals(first.evidenceFingerprint, second.evidenceFingerprint)
        assertEquals(first.history, second.history)
        assertEquals(
            StrengthPosteriorModel.toEntity(first.state, 2_000L).stateFingerprint,
            StrengthPosteriorModel.toEntity(second.state, 2_000L).stateFingerprint
        )
        assertFalse(first.evidence.isEmpty())
    }

    @Test
    fun `version 2 model state remains readable after failure evidence support is added`() {
        val current = StrengthPosteriorModel.toEntity(
            StrengthPosteriorModel.initialState(registry, null),
            2_000L
        )
        val oldVersion = "strength-performance-model-2.0.0"
        val legacy = current.copy(
            modelVersion = oldVersion,
            stateFingerprint = fingerprint(
                current.orderedFactorSchema,
                current.stateMeanEncoded,
                current.packedCovarianceEncoded,
                current.lastProcessedEventUuid.orEmpty(),
                current.lastProcessedDate.orEmpty(),
                oldVersion,
                current.factorSchemaVersion
            )
        )

        assertArrayEquals(
            StrengthPosteriorModel.initialState(registry, null).mean,
            StrengthPosteriorModel.fromEntity(legacy).mean,
            0.0
        )
    }

    @Test
    fun `Joseph update keeps covariance symmetric positive semidefinite and intervals ordered`() {
        var state = StrengthPosteriorModel.initialState(registry, null)
        repeat(12) { index ->
            state = StrengthPosteriorModel.compute(
                eventUuid = "event-$index",
                date = LocalDate.parse("2026-07-01").plusDays(index.toLong()),
                currentState = state,
                observations = listOf(observation(reps = 1 + index % 5, weight = 85.0 + index, rpe = 10.0)),
                registry = registry,
                curves = curves,
                curvePosteriorBySubject = emptyMap(),
                now = 2_000L + index
            ).state
        }

        state.covariance.indices.forEach { row ->
            state.covariance.indices.forEach { column ->
                assertEquals(state.covariance[row][column], state.covariance[column][row], 1e-12)
            }
        }
        val eigenvalues = EigenDecomposition(Array2DRowRealMatrix(state.covariance)).realEigenvalues
        assertTrue(eigenvalues.all { value -> value >= -1e-9 })
        registry.targets().forEach { target ->
            val distribution = StrengthPosteriorModel.distribution(state, target)
            assertTrue(distribution.low95 <= distribution.low80)
            assertTrue(distribution.low80 <= distribution.low50)
            assertTrue(distribution.low50 <= distribution.median)
            assertTrue(distribution.median <= distribution.high50)
            assertTrue(distribution.high50 <= distribution.high80)
            assertTrue(distribution.high80 <= distribution.high95)
        }
    }

    @Test
    fun `non-finite state fails closed and canonical curve weights normalize`() {
        assertTrue(
            runCatching {
                StrengthPosteriorState(
                    orderedFactorSchema = listOf(StrengthFactorKey("bad")),
                    mean = doubleArrayOf(Double.NaN),
                    covariance = arrayOf(doubleArrayOf(1.0))
                )
            }.isFailure
        )
        val profile = checkNotNull(curves.resolve("barbell_bench_press").profile)
        val posterior = PersonalCurveCalibrator.initial("exercise:bench", profile, now = 1L)
        assertEquals(1.0, posterior.posteriorWeights.sum(), 1e-12)
        assertTrue(posterior.posteriorWeights.all { weight -> weight.isFinite() && weight >= 0.0 })
    }

    private fun compute(
        initial: StrengthPosteriorState,
        observation: StrengthExerciseSessionObservation
    ): StrengthPosteriorComputation = StrengthPosteriorModel.compute(
        eventUuid = "event-1",
        date = LocalDate.parse("2026-07-20"),
        currentState = initial,
        observations = listOf(observation),
        registry = registry,
        curves = curves,
        curvePosteriorBySubject = emptyMap(),
        now = 2_000L
    )

    private fun observation(reps: Int, weight: Double, rpe: Double?): StrengthExerciseSessionObservation {
        val exercise = Exercise(
            id = 1,
            name = "Bench press",
            category = "Strength",
            stableKey = "barbell_bench_press"
        )
        val record = WorkoutEntryWithSets(
            entry = WorkoutEntry(
                id = 1,
                date = "2026-07-20",
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                category = exercise.category
            ),
            sets = listOf(
                WorkoutSet(
                    id = 1,
                    entryId = 1,
                    setIndex = 1,
                    reps = reps,
                    weightKg = weight,
                    confirmed = true,
                    rpe = rpe
                )
            )
        )
        return checkNotNull(
            StrengthSessionObservationBuilder.build(
                record = record,
                exercise = exercise,
                registry = registry,
                curveRegistry = curves,
                loadResolver = StrengthPerformanceLoadResolver(emptyList(), emptyList(), null)
            )
        )
    }
}

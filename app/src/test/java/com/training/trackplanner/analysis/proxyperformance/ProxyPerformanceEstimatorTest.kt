package com.training.trackplanner.analysis.proxyperformance

import java.time.LocalDate
import kotlin.math.abs
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.CholeskyDecomposition
import org.apache.commons.math3.linear.MatrixUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyPerformanceEstimatorTest {
    @Test
    fun currentSessionCannotInfluenceItsOwnExpectation() {
        val prior = listOf(
            direct(1, "2026-01-01", MajorLiftTarget.BENCH_PRESS, 100.0),
            direct(2, "2026-01-08", MajorLiftTarget.BENCH_PRESS, 102.0),
            proxy(3, "2026-01-10", "db_bench", 60.0, benchProxyLoading()),
            proxy(4, "2026-01-17", "db_bench", 66.0, benchProxyLoading())
        )
        val lowerCurrent = direct(5, "2026-01-22", MajorLiftTarget.BENCH_PRESS, 105.0)
        val higherCurrent = lowerCurrent.copy(canonicalE1rmKg = 125.0, effortAdjustedPerformanceKg = 125.0)

        val lowerExpectation = run(prior + lowerCurrent, MajorLiftTarget.BENCH_PRESS)
            .sessionComparisons.last { comparison -> comparison.workoutEntryId == 5L }
        val higherExpectation = run(prior + higherCurrent, MajorLiftTarget.BENCH_PRESS)
            .sessionComparisons.last { comparison -> comparison.workoutEntryId == 5L }

        assertEquals(lowerExpectation.expectedMedianKg, higherExpectation.expectedMedianKg)
        assertEquals(lowerExpectation.expectedLow80Kg, higherExpectation.expectedLow80Kg)
        assertEquals(lowerExpectation.expectedHigh80Kg, higherExpectation.expectedHigh80Kg)
        assertNotEquals(lowerExpectation.differenceKg, higherExpectation.differenceKg)
    }

    @Test
    fun laterSessionsDoNotRewriteEarlierExpectation() {
        val throughTarget = listOf(
            direct(1, "2026-01-01", MajorLiftTarget.BENCH_PRESS, 100.0),
            direct(2, "2026-01-08", MajorLiftTarget.BENCH_PRESS, 102.0),
            proxy(3, "2026-01-10", "db_bench", 60.0, benchProxyLoading()),
            proxy(4, "2026-01-17", "db_bench", 66.0, benchProxyLoading()),
            direct(5, "2026-01-22", MajorLiftTarget.BENCH_PRESS, 106.0)
        )
        val later = listOf(
            proxy(6, "2026-02-01", "db_bench", 80.0, benchProxyLoading()),
            direct(7, "2026-02-08", MajorLiftTarget.BENCH_PRESS, 120.0)
        )

        val earlier = run(throughTarget, MajorLiftTarget.BENCH_PRESS)
            .sessionComparisons.last { comparison -> comparison.workoutEntryId == 5L }
        val rebuilt = run(throughTarget + later, MajorLiftTarget.BENCH_PRESS)
            .sessionComparisons.first { comparison -> comparison.workoutEntryId == 5L }

        assertEquals(earlier.expectedMedianKg, rebuilt.expectedMedianKg)
        assertEquals(earlier.standardizedSurprise, rebuilt.standardizedSurprise)
    }

    @Test
    fun sameDateEntryOrderingIsDeterministic() {
        val observations = listOf(
            direct(1, "2026-01-01", MajorLiftTarget.SQUAT, 140.0),
            direct(2, "2026-01-08", MajorLiftTarget.SQUAT, 142.0),
            proxy(3, "2026-01-15", "rdl", 100.0, rdlLoading()).copy(performedAt = 200L, displayOrder = 2),
            proxy(4, "2026-01-15", "rdl", 110.0, rdlLoading()).copy(performedAt = 100L, displayOrder = 1),
            direct(5, "2026-01-22", MajorLiftTarget.SQUAT, 145.0)
        )

        val ordered = run(observations, MajorLiftTarget.SQUAT)
        val shuffled = run(observations.reversed(), MajorLiftTarget.SQUAT)

        assertEquals(ordered.sessionComparisons, shuffled.sessionComparisons)
        assertEquals(ordered.weeklyPosterior, shuffled.weeklyPosterior)
        assertEquals(ordered.fingerprint, shuffled.fingerprint)
    }

    @Test
    fun proxyImprovementRaisesPosteriorAndDistantPressMovesLess() {
        val directHistory = listOf(
            direct(1, "2026-01-01", MajorLiftTarget.BENCH_PRESS, 100.0),
            direct(2, "2026-01-08", MajorLiftTarget.BENCH_PRESS, 100.0)
        )
        val dumbbell = directHistory + listOf(
            proxy(3, "2026-01-10", "db_bench", 60.0, benchProxyLoading()),
            proxy(4, "2026-01-17", "db_bench", 72.0, benchProxyLoading())
        )
        val overhead = directHistory + listOf(
            proxy(3, "2026-01-10", "overhead", 60.0, overheadLoading()),
            proxy(4, "2026-01-17", "overhead", 72.0, overheadLoading())
        )

        val baseline = run(directHistory, MajorLiftTarget.BENCH_PRESS).weeklyPosterior.last().posteriorMedianKg!!
        val dumbbellEstimate = run(dumbbell, MajorLiftTarget.BENCH_PRESS).weeklyPosterior.last().posteriorMedianKg!!
        val overheadEstimate = run(overhead, MajorLiftTarget.BENCH_PRESS).weeklyPosterior.last().posteriorMedianKg!!

        assertTrue(dumbbellEstimate > baseline)
        assertTrue(overheadEstimate > baseline)
        assertTrue(dumbbellEstimate - baseline > overheadEstimate - baseline)
    }

    @Test
    fun squatAndDeadliftShareLowerBodyFactorsAsymmetrically() {
        val squatHistory = listOf(
            direct(1, "2026-01-01", MajorLiftTarget.SQUAT, 140.0),
            direct(2, "2026-01-08", MajorLiftTarget.SQUAT, 140.0)
        )
        val deadliftHistory = listOf(
            direct(10, "2026-01-01", MajorLiftTarget.DEADLIFT, 180.0),
            direct(11, "2026-01-08", MajorLiftTarget.DEADLIFT, 180.0)
        )
        val improvedDeadlift = deadliftHistory + listOf(
            proxy(12, "2026-01-10", "rdl", 100.0, rdlLoading()),
            proxy(13, "2026-01-17", "rdl", 120.0, rdlLoading())
        )
        val improvedSquat = squatHistory + listOf(
            proxy(3, "2026-01-10", "lunge", 60.0, lungeLoading()),
            proxy(4, "2026-01-17", "lunge", 72.0, lungeLoading())
        )

        val deadliftBase = run(deadliftHistory, MajorLiftTarget.DEADLIFT).weeklyPosterior.last().posteriorMedianKg!!
        val deadliftRaised = run(improvedDeadlift, MajorLiftTarget.DEADLIFT).weeklyPosterior.last().posteriorMedianKg!!
        val squatBase = run(squatHistory, MajorLiftTarget.SQUAT).weeklyPosterior.last().posteriorMedianKg!!
        val squatRaised = run(improvedSquat, MajorLiftTarget.SQUAT).weeklyPosterior.last().posteriorMedianKg!!

        assertTrue(deadliftRaised > deadliftBase)
        assertTrue(squatRaised > squatBase)
    }

    @Test
    fun conflictingProxySignalsPreserveMoreUncertainty() {
        val history = listOf(
            direct(1, "2026-01-01", MajorLiftTarget.BENCH_PRESS, 100.0),
            direct(2, "2026-01-08", MajorLiftTarget.BENCH_PRESS, 100.0),
            proxy(3, "2026-01-10", "db_bench", 60.0, benchProxyLoading()),
            proxy(4, "2026-01-11", "machine_press", 80.0, benchProxyLoading()),
            proxy(5, "2026-01-17", "db_bench", 72.0, benchProxyLoading())
        )
        val consistent = run(
            history + proxy(6, "2026-01-18", "machine_press", 96.0, benchProxyLoading()),
            MajorLiftTarget.BENCH_PRESS
        ).weeklyPosterior.last()
        val conflicting = run(
            history + proxy(6, "2026-01-18", "machine_press", 64.0, benchProxyLoading()),
            MajorLiftTarget.BENCH_PRESS
        ).weeklyPosterior.last()

        val consistentWidth = (consistent.posteriorHigh80Kg!! - consistent.posteriorLow80Kg!!) / consistent.posteriorMedianKg!!
        val conflictingWidth = (conflicting.posteriorHigh80Kg!! - conflicting.posteriorLow80Kg!!) / conflicting.posteriorMedianKg!!
        assertTrue("consistent=$consistentWidth conflicting=$conflictingWidth", conflictingWidth > consistentWidth)
    }

    @Test
    fun directTargetUpdatesSpecificResidualWhileDistantProxyDoesNot() {
        val config = ProxyPerformanceStateModel.config(
            MajorLiftTarget.BENCH_PRESS,
            ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC
        )
        val filter = ProxyPerformanceKalmanFilter(config)
        val specificIndex = config.factors.indexOf(ProxyLatentFactor.BENCH_SPECIFIC)
        val directLoading = config.factors.map { factor ->
            ProxyPerformanceLoadingBuilder.targetLoading(
                MajorLiftTarget.BENCH_PRESS,
                ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC
            ).getOrDefault(factor, 0.0)
        }.toDoubleArray()
        val distantLoading = config.factors.map { factor -> overheadLoading().getOrDefault(factor, 0.0) }.toDoubleArray()

        val directState = filter.update(filter.initialState(), 1.0, directLoading, 0.1).state
        val proxyState = filter.update(filter.initialState(), 1.0, distantLoading, 0.1).state

        assertTrue(directState.mean.getEntry(specificIndex) > 0.0)
        assertEquals(0.0, proxyState.mean.getEntry(specificIndex), 0.0)
    }

    @Test
    fun predictionOnlyWeeksIncreaseUncertaintyWithoutCreatingObservations() {
        val observations = listOf(
            direct(1, "2026-01-01", MajorLiftTarget.DEADLIFT, 180.0),
            direct(2, "2026-01-08", MajorLiftTarget.DEADLIFT, 182.0)
        )
        val result = ProxyPerformanceEstimator.runModel(
            MajorLiftTarget.DEADLIFT,
            ProxyModelVariant.M1_TARGET_ONLY,
            observations,
            LocalDate.parse("2026-04-01")
        )
        val observedWeek = result.weeklyPosterior.last { point ->
            point.observationStatus == ProxyObservationStatus.DIRECT_OBSERVATION && point.posteriorLow80Kg != null
        }
        val finalWeek = result.weeklyPosterior.last()
        val observedWidth = observedWeek.posteriorHigh80Kg!! - observedWeek.posteriorLow80Kg!!
        val finalWidth = finalWeek.posteriorHigh80Kg!! - finalWeek.posteriorLow80Kg!!

        assertEquals(ProxyObservationStatus.NO_OBSERVATION, finalWeek.observationStatus)
        assertTrue(finalWidth > observedWidth)
        assertEquals(2, finalWeek.directObservationCountToDate)
    }

    @Test
    fun kalmanJosephUpdateStaysSymmetricPositiveDefiniteAndFailsClosed() {
        val config = ProxyPerformanceStateModel.config(MajorLiftTarget.BENCH_PRESS, ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC)
        val filter = ProxyPerformanceKalmanFilter(config)
        val updated = filter.update(
            state = filter.initialState(),
            observation = 0.5,
            loading = config.factors.map { factor -> if (factor == ProxyLatentFactor.PRESS_SHARED) 0.8 else 0.1 }.toDoubleArray(),
            observationVariance = 0.2
        )

        val covariance = updated.state.covariance
        assertTrue(updated.updated)
        assertTrue((0 until covariance.rowDimension).all { row ->
            (0 until covariance.columnDimension).all { column ->
                abs(covariance.getEntry(row, column) - covariance.getEntry(column, row)) < 1e-10
            }
        })
        CholeskyDecomposition(covariance)

        val rejected = filter.update(
            state = updated.state,
            observation = Double.NaN,
            loading = DoubleArray(config.factors.size) { 1.0 },
            observationVariance = 0.2
        )
        assertFalse(rejected.updated)
        assertEquals(updated.state, rejected.state)
        assertEquals(ProxyPerformanceDiagnosticCode.NON_FINITE_INPUT, rejected.diagnostics.single().code)
    }

    @Test
    fun boundedJitterIsDeterministic() {
        val config = ProxyPerformanceStateModel.config(MajorLiftTarget.BENCH_PRESS, ProxyModelVariant.M1_TARGET_ONLY)
        val filter = ProxyPerformanceKalmanFilter(config)
        val factor = config.factors.single()
        val persistence = config.persistence.getValue(factor)
        val processNoise = config.processNoise.getValue(factor)
        val covarianceBeforePrediction = -(processNoise + 5e-8) / (persistence * persistence)
        val nearlyInvalid = ProxyKalmanState(
            mean = ArrayRealVector(doubleArrayOf(0.0)),
            covariance = MatrixUtils.createRealMatrix(arrayOf(doubleArrayOf(covarianceBeforePrediction)))
        )

        val first = filter.predict(nearlyInvalid, 7)
        val second = filter.predict(nearlyInvalid, 7)

        assertEquals(first.state.covariance, second.state.covariance)
        assertEquals(first.diagnostics, second.diagnostics)
        assertEquals(ProxyPerformanceDiagnosticCode.BOUNDED_JITTER_APPLIED, first.diagnostics.single().code)
        assertTrue(first.state.covariance.getEntry(0, 0) > 0.0)
    }

    @Test
    fun sameInputProducesSameSummaryAndFingerprint() {
        val observations = listOf(
            direct(1, "2026-01-01", MajorLiftTarget.BENCH_PRESS, 100.0),
            direct(2, "2026-01-08", MajorLiftTarget.BENCH_PRESS, 102.0),
            proxy(3, "2026-01-10", "db_bench", 60.0, benchProxyLoading()),
            proxy(4, "2026-01-17", "db_bench", 65.0, benchProxyLoading())
        )

        val first = ProxyPerformanceEstimator.build(observations, LocalDate.parse("2026-02-01"))
        val second = ProxyPerformanceEstimator.build(observations, LocalDate.parse("2026-02-01"))

        assertEquals(first, second)
        assertEquals(
            first.targets.getValue(MajorLiftTarget.BENCH_PRESS).modelFingerprint,
            second.targets.getValue(MajorLiftTarget.BENCH_PRESS).modelFingerprint
        )
    }

    @Test
    fun noProxyHistoryForcesTargetOnlySelection() {
        val observations = (0 until 8).map { index ->
            direct(index.toLong() + 1, LocalDate.parse("2026-01-01").plusWeeks(index.toLong()).toString(), MajorLiftTarget.BENCH_PRESS, 100.0 + index)
        }

        val evaluation = ProxyPerformanceBacktester.evaluate(
            MajorLiftTarget.BENCH_PRESS,
            observations,
            LocalDate.parse("2026-03-01")
        )

        assertEquals(ProxyModelVariant.M1_TARGET_ONLY, evaluation.summary.selectedModel)
    }

    @Test
    fun consistentSyntheticProxiesCanSelectSharedModelWhileSparseTargetStaysM1() {
        val observations = mutableListOf<ProxyPerformanceObservation>()
        val start = LocalDate.parse("2026-01-01")
        repeat(12) { index ->
            val week = start.plusWeeks(index.toLong())
            observations += proxy(index * 3L + 1, week.toString(), "db_bench", 60.0 + index * 2.0, benchProxyLoading())
            observations += proxy(index * 3L + 2, week.plusDays(1).toString(), "machine_press", 80.0 + index * 2.5, benchProxyLoading())
            observations += direct(index * 3L + 3, week.plusDays(3).toString(), MajorLiftTarget.BENCH_PRESS, 100.0 + index * 2.2)
        }
        observations += (0 until 6).map { index ->
            direct(100L + index, start.plusWeeks(index.toLong()).toString(), MajorLiftTarget.SQUAT, 140.0 + index)
        }

        val bench = ProxyPerformanceBacktester.evaluate(
            MajorLiftTarget.BENCH_PRESS,
            observations,
            start.plusWeeks(13)
        )
        val squat = ProxyPerformanceBacktester.evaluate(
            MajorLiftTarget.SQUAT,
            observations,
            start.plusWeeks(13)
        )

        assertEquals(
            "Bench candidates: ${bench.summary.candidates}",
            ProxyModelVariant.M2_SHARED_FACTORS,
            bench.summary.selectedModel
        )
        assertEquals(ProxyModelVariant.M1_TARGET_ONLY, squat.summary.selectedModel)
    }

    @Test
    fun misleadingProxyHistoryIsRejectedByRollingSelection() {
        val start = LocalDate.parse("2026-01-01")
        val observations = mutableListOf<ProxyPerformanceObservation>()
        repeat(12) { index ->
            val week = start.plusWeeks(index.toLong())
            observations += proxy(index * 3L + 1, week.toString(), "db_bench", 90.0 - index * 2.0, benchProxyLoading())
            observations += proxy(index * 3L + 2, week.plusDays(1).toString(), "machine_press", 110.0 - index * 2.0, benchProxyLoading())
            observations += direct(index * 3L + 3, week.plusDays(3).toString(), MajorLiftTarget.BENCH_PRESS, 100.0 + index * 2.0)
        }

        val evaluation = ProxyPerformanceBacktester.evaluate(
            MajorLiftTarget.BENCH_PRESS,
            observations,
            start.plusWeeks(13)
        )

        assertEquals(ProxyModelVariant.M1_TARGET_ONLY, evaluation.summary.selectedModel)
    }

    private fun run(
        observations: List<ProxyPerformanceObservation>,
        target: MajorLiftTarget
    ): ProxyModelRun = ProxyPerformanceEstimator.runModel(
        target = target,
        variant = ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC,
        observations = observations,
        generatedAtDate = observations.maxOf(ProxyPerformanceObservation::date)
    )

    private fun direct(
        id: Long,
        date: String,
        target: MajorLiftTarget,
        performanceKg: Double
    ): ProxyPerformanceObservation = observation(
        id = id,
        date = date,
        stableKey = when (target) {
            MajorLiftTarget.BENCH_PRESS -> "barbell_bench_press"
            MajorLiftTarget.SQUAT -> "barbell_back_squat"
            MajorLiftTarget.DEADLIFT -> "conventional_deadlift"
        },
        performanceKg = performanceKg,
        directTarget = target,
        factors = ProxyPerformanceLoadingBuilder.targetLoading(target, ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC)
    )

    private fun proxy(
        id: Long,
        date: String,
        stableKey: String,
        performanceKg: Double,
        factors: Map<ProxyLatentFactor, Double>
    ): ProxyPerformanceObservation = observation(id, date, stableKey, performanceKg, null, factors)

    private fun observation(
        id: Long,
        date: String,
        stableKey: String,
        performanceKg: Double,
        directTarget: MajorLiftTarget?,
        factors: Map<ProxyLatentFactor, Double>
    ): ProxyPerformanceObservation = ProxyPerformanceObservation(
        workoutEntryId = id,
        date = LocalDate.parse(date),
        performedAt = id * 1_000,
        displayOrder = id.toInt(),
        exerciseId = id,
        exerciseStableKey = stableKey,
        exerciseName = stableKey,
        directTarget = directTarget,
        canonicalE1rmKg = performanceKg,
        effortAdjustedPerformanceKg = performanceKg,
        observationVariance = 0.10,
        quality = 1.0,
        rpeAvailable = true,
        sourceSetIds = listOf(id),
        loading = ProxyPerformanceLoading(factors, factors.keys.map(ProxyLatentFactor::name), 1.0)
    )

    private fun benchProxyLoading(): Map<ProxyLatentFactor, Double> = mapOf(
        ProxyLatentFactor.PRESS_SHARED to 0.85,
        ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 0.90,
        ProxyLatentFactor.BENCH_SPECIFIC to 0.25
    )

    private fun overheadLoading(): Map<ProxyLatentFactor, Double> = mapOf(
        ProxyLatentFactor.PRESS_SHARED to 0.55,
        ProxyLatentFactor.HORIZONTAL_PRESS_SPECIFIC to 0.15
    )

    private fun rdlLoading(): Map<ProxyLatentFactor, Double> = mapOf(
        ProxyLatentFactor.KNEE_EXTENSION to 0.15,
        ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 1.00,
        ProxyLatentFactor.TRUNK_BRACING to 0.70,
        ProxyLatentFactor.DEADLIFT_SPECIFIC to 0.40
    )

    private fun lungeLoading(): Map<ProxyLatentFactor, Double> = mapOf(
        ProxyLatentFactor.KNEE_EXTENSION to 0.85,
        ProxyLatentFactor.HIP_EXTENSION_POSTERIOR_CHAIN to 0.55,
        ProxyLatentFactor.TRUNK_BRACING to 0.35,
        ProxyLatentFactor.SQUAT_SPECIFIC to 0.20
    )
}

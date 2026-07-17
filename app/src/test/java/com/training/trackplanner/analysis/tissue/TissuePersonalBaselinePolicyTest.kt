package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TissuePersonalBaselinePolicyTest {
    @Test
    fun weightedQuantileUsesDeterministicCumulativeNearestRank() {
        val samples = listOf(
            TissueWeightedSample(3.0, 2.0),
            TissueWeightedSample(1.0, 1.0),
            TissueWeightedSample(2.0, 1.0)
        )

        assertEquals(2.0, TissueWeightedQuantilePolicy.quantile(samples, 0.30), 1e-9)
        assertEquals(3.0, TissueWeightedQuantilePolicy.quantile(samples, 0.80), 1e-9)
        assertEquals(3.0, TissueWeightedQuantilePolicy.quantile(samples.reversed(), 0.80), 1e-9)
    }

    @Test
    fun duplicateValuesAndFractionalWeightsRemainDeterministic() {
        val samples = listOf(
            TissueWeightedSample(1.0, 0.5),
            TissueWeightedSample(1.0, 0.5),
            TissueWeightedSample(2.0, 0.5),
            TissueWeightedSample(4.0, 0.5)
        )

        assertEquals(1.0, TissueWeightedQuantilePolicy.quantile(samples, 0.30), 1e-9)
        assertEquals(4.0, TissueWeightedQuantilePolicy.quantile(samples, 0.95), 1e-9)
    }

    @Test
    fun personalBaselineUsesOnlySamplesStrictlyAboveFloor() {
        val history = history((1..20).map { index -> index.toDouble() })
        val weight = TissuePerUnitWeightPolicy.calculate("unit", history)
        val residuals = history.dates.associate { date -> date.date to (date.date.dayOfMonth - 1).toDouble() }

        val baseline = TissuePersonalBaselinePolicy.derive("unit", "unit", 5.0, weight, residuals)

        assertTrue(baseline.isValid)
        assertTrue(baseline.activeSamples.all { it.value > 5.0 })
        assertTrue(baseline.boundaries!!.meaningfulFloor < baseline.boundaries.q30)
    }

    @Test
    fun identityMismatchEmptyNonFiniteAndDegenerateInputsFallBack() {
        val history = history((1..20).map(Int::toDouble))
        val weight = TissuePerUnitWeightPolicy.calculate("unit", history)
        val values = history.dates.associate { it.date to 2.0 }

        assertFalse(TissuePersonalBaselinePolicy.derive("unit", "other", 0.1, weight, values).isValid)
        assertFalse(TissuePersonalBaselinePolicy.derive("unit", "unit", 10.0, weight, values).isValid)
        assertFalse(
            TissuePersonalBaselinePolicy.derive(
                "unit",
                "unit",
                0.1,
                weight,
                values + (history.dates.first().date to Double.NaN)
            ).isValid
        )
        val degenerate = TissuePersonalBaselinePolicy.derive("unit", "unit", 0.1, weight, values)
        assertNull(degenerate.boundaries)
        assertTrue(degenerate.diagnostics.any { it.code == "PERSONAL_BASELINE_DEGENERATE_QUANTILES" })
    }

    @Test
    fun noExposureInfluenceKeepsPersonalBaselineInvalid() {
        val history = history(listOf(1.0, 2.0, 3.0), exposureCount = 3)
        val weight = TissuePerUnitWeightPolicy.calculate("unit", history)
        val baseline = TissuePersonalBaselinePolicy.derive(
            "unit",
            "unit",
            0.1,
            weight,
            history.dates.associate { it.date to 5.0 }
        )

        assertFalse(baseline.isValid)
        assertEquals(0.0, weight.exposureWeight, 1e-9)
    }

    @Test
    fun historicalSamplerUsesSameLocalHourAndRejectsFutureEvents() {
        val zone = ZoneId.of("Asia/Seoul")
        val date = LocalDate.of(2026, 7, 1)
        val curve = TissueRecoveryCurve(
            id = "curve",
            displayName = "curve",
            outcome = "test",
            interpolation = "PCHIP",
            evidenceGrade = "test",
            evidenceNote = "test",
            knots = listOf(TissueRecoveryKnot(0.0, 1.0), TissueRecoveryKnot(48.0, 0.0))
        )
        val calculator = TissueResidualCalculator(TissueRecoveryCurveRepository(mapOf("curve" to curve)), zone)
        val sampler = TissueHistoricalResidualSampler(calculator, zone)
        val atTen = TissueHistoricalResidualSampler.evaluationEpochMillis(date, 10, zone)
        val history = TissueCalibrationHistory(
            date.plusDays(10),
            date.plusDays(3),
            listOf(
                TissueCalibrationHistorySegment(
                    date,
                    date,
                    1.0,
                    listOf(TissueCalibrationHistoryDate(date, 1.0, true))
                )
            )
        )
        val available = event("available", atTen - 3_600_000, "unit")
        val future = event("future", atTen + 3_600_000, "unit")

        val samples = sampler.sampleUnit("unit", listOf(available, future), history, 10)

        assertTrue(samples.getValue(date) > 0.0)
        assertEquals(
            calculator.calculate(available, atTen)!!.currentResidualRange.upper,
            samples.getValue(date),
            1e-9
        )
    }

    @Test
    fun historicalSamplerTraversesDstByLocalDateRatherThanFixedMilliseconds() {
        val zone = ZoneId.of("America/New_York")
        val before = LocalDate.of(2026, 3, 7)
        val after = before.plusDays(1)
        val beforeEpoch = TissueHistoricalResidualSampler.evaluationEpochMillis(before, 10, zone)
        val afterEpoch = TissueHistoricalResidualSampler.evaluationEpochMillis(after, 10, zone)

        assertEquals(23L * 3_600_000L, afterEpoch - beforeEpoch)
        assertEquals(10, java.time.Instant.ofEpochMilli(afterEpoch).atZone(zone).hour)
    }

    private fun history(values: List<Double>, exposureCount: Int = 12): TissueCalibrationHistory {
        val start = LocalDate.of(2026, 1, 1)
        val dates = values.mapIndexed { index, _ ->
            TissueCalibrationHistoryDate(start.plusDays(index.toLong()), 1.0, index < exposureCount)
        }
        return TissueCalibrationHistory(
            start.plusDays(30),
            start.plusDays(23),
            listOf(TissueCalibrationHistorySegment(dates.first().date, dates.last().date, 1.0, dates))
        )
    }

    private fun event(id: String, time: Long, stableKey: String): TissueExposureEvent = TissueExposureEvent(
        eventId = id,
        recordId = id.hashCode().toLong(),
        exerciseStableKey = id,
        exerciseName = id,
        key = TissueRcvLoadKey(stableKey, "dimension"),
        jointComplexStableKey = "joint",
        tissueClass = "TENDON",
        initialExposure = 1.0,
        rawDose = 1.0,
        doseReference56d = 1.0,
        normalizedDose = 1.0,
        selectedEffort = TissueEffortSelection(1.0, TissueEffortSource.SET_RPE),
        magnitudeM = 1.0,
        rapidityS = 1.0,
        contextModifier = 1.0,
        mappingRoleWeight = 1.0,
        curveIds = mapOf(TissueRecoveryChannel.FUNCTIONAL_CAPACITY to "curve"),
        performedTime = TissueEventTimeRange(time, time, TissueTimestampPrecision.EXACT),
        scoreVersion = "test",
        protocolVersion = "test",
        curveVersion = "test",
        evidenceGrade = "test",
        sourceRefs = emptyList(),
        diagnostics = emptyList()
    )
}

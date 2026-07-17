package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TissuePerUnitWeightPolicyTest {
    @Test
    fun exposureRampUsesExactThreeFourAndTwelveDayBoundaries() {
        assertEquals(0.0, weight(observationDays = 56, exposureDays = 0).exposureWeight, 1e-9)
        assertEquals(0.0, weight(observationDays = 56, exposureDays = 3).exposureWeight, 1e-9)
        assertEquals(1.0 / 9.0, weight(observationDays = 56, exposureDays = 4).exposureWeight, 1e-9)
        assertEquals(1.0, weight(observationDays = 56, exposureDays = 12).exposureWeight, 1e-9)
    }

    @Test
    fun spanWeightUsesWeightedCalendarDays() {
        assertEquals(0.5, weight(observationDays = 28, exposureDays = 12).spanWeight, 1e-9)
        assertEquals(1.0, weight(observationDays = 56, exposureDays = 12).spanWeight, 1e-9)
        assertEquals(1.0, weight(observationDays = 70, exposureDays = 12).spanWeight, 1e-9)
    }

    @Test
    fun finalWeightIsMinimumOfSpanAndExposureComponents() {
        val spanLimited = weight(observationDays = 28, exposureDays = 12)
        val exposureLimited = weight(observationDays = 56, exposureDays = 6)

        assertEquals(0.5, spanLimited.value, 1e-9)
        assertEquals(1.0 / 3.0, exposureLimited.value, 1e-9)
    }

    @Test
    fun sameDayEventsAndDimensionsCountAsOneExposureDate() {
        val anchor = LocalDate.of(2026, 7, 17)
        val exposure = anchor.minusDays(10)
        val history = TissueCalibrationHistoryPolicy.build(
            anchorDate = anchor,
            confirmedWorkoutDates = listOf(exposure, exposure, anchor),
            unitExposureDates = listOf(exposure, exposure, exposure, anchor),
            maxWeightedObservationDays = 56.0
        )
        val result = TissuePerUnitWeightPolicy.calculate("unit", history)

        assertEquals(1.0, result.weightedDistinctExposureDays, 1e-9)
        assertEquals(0.0, result.value, 1e-9)
    }

    @Test
    fun fractionalRetentionWeightsSpanAndExposureTogether() {
        val result = weight(observationDays = 28.5, exposureDays = 4.5)

        assertEquals(28.5 / 56.0, result.spanWeight, 1e-9)
        assertEquals(1.5 / 9.0, result.exposureWeight, 1e-9)
        assertEquals(1.5 / 9.0, result.value, 1e-9)
    }

    @Test
    fun unitsRemainIndependentAndBounded() {
        val first = weight("first", observationDays = 56, exposureDays = 12)
        val second = weight("second", observationDays = 20, exposureDays = 4)

        assertEquals(1.0, first.value, 1e-9)
        assertEquals(1.0 / 9.0, second.value, 1e-9)
        listOf(first, second).forEach { value ->
            assertTrue(value.value.isFinite())
            assertTrue(value.value in 0.0..1.0)
        }
    }

    @Test
    fun excludedGapDatesDoNotEnterSpanAndGlobalResetStopsAllOlderHistory() {
        val anchor = LocalDate.of(2026, 7, 17)
        val cutoffExposure = anchor.minusDays(7)
        val olderExposure = cutoffExposure.minusDays(29)
        val history = TissueCalibrationHistoryPolicy.build(
            anchorDate = anchor,
            confirmedWorkoutDates = listOf(olderExposure, cutoffExposure, anchor),
            unitExposureDates = listOf(olderExposure, cutoffExposure, anchor),
            maxWeightedObservationDays = 56.0
        )

        assertTrue(history.dates.none { it.date == olderExposure })
        assertEquals(1.0, history.weightedDistinctExposureDays, 1e-9)
    }

    private fun weight(
        stableKey: String = "unit",
        observationDays: Int,
        exposureDays: Int
    ): TissuePerUnitCalibrationWeight = weight(stableKey, observationDays.toDouble(), exposureDays.toDouble())

    private fun weight(
        stableKey: String = "unit",
        observationDays: Double,
        exposureDays: Double
    ): TissuePerUnitCalibrationWeight {
        val start = LocalDate.of(2026, 1, 1)
        val wholeExposureDays = exposureDays.toInt()
        val fractionalExposure = exposureDays - wholeExposureDays
        val dateCount = kotlin.math.ceil(observationDays).toInt()
        val dates = (0 until dateCount).map { index ->
            val weight = minOf(1.0, observationDays - index).coerceAtLeast(0.0)
            TissueCalibrationHistoryDate(
                date = start.plusDays(index.toLong()),
                weight = weight,
                isUnitExposure = index < wholeExposureDays ||
                    (index == dateCount - 1 && fractionalExposure > 0.0 && weight == fractionalExposure)
            )
        }
        val segments = dates.groupBy(TissueCalibrationHistoryDate::weight).values.map { rows ->
            TissueCalibrationHistorySegment(rows.first().date, rows.last().date, rows.first().weight, rows)
        }
        return TissuePerUnitWeightPolicy.calculate(
            stableKey,
            TissueCalibrationHistory(start.plusDays(100), start.plusDays(93), segments)
        )
    }
}

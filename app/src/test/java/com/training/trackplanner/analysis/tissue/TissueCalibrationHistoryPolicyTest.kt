package com.training.trackplanner.analysis.tissue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TissueCalibrationHistoryPolicyTest {
    private val anchor = LocalDate.of(2026, 7, 17)

    @Test
    fun anchorUsesLatestConfirmedWorkoutOrPersistedCheckInOnly() {
        val workout = anchor.minusDays(3)
        val checkIn = anchor.minusDays(1)

        assertEquals(
            checkIn,
            TissueCalibrationAnchorPolicy.latestConfirmationDate(listOf(workout), listOf(checkIn))
        )
        assertEquals(workout, TissueCalibrationAnchorPolicy.latestConfirmationDate(listOf(workout), emptyList()))
        assertEquals(null, TissueCalibrationAnchorPolicy.latestConfirmationDate(emptyList(), emptyList()))
    }

    @Test
    fun recentSevenCalendarDatesAreExcludedAndAminusSevenIsEligible() {
        val dates = (0L..20L).map(anchor::minusDays)
        val history = history(workouts = dates, exposures = dates)

        assertEquals(anchor.minusDays(7), history.dates.last().date)
        assertTrue((0L..6L).none { offset -> history.dates.any { it.date == anchor.minusDays(offset) } })
    }

    @Test
    fun sevenSessionsAcrossFourteenDatesDoNotBecomeSevenExcludedSessions() {
        val sessions = (0L..12L step 2).map(anchor::minusDays)
        val history = history(workouts = sessions, exposures = sessions)

        assertTrue(history.dates.any { it.date == anchor.minusDays(8) })
        assertFalse(history.dates.any { it.date == anchor.minusDays(6) })
    }

    @Test
    fun duplicateSameDayActivitiesRemainOneCalendarDate() {
        val date = anchor.minusDays(10)
        val history = history(
            workouts = listOf(date, date, anchor),
            exposures = listOf(date, date, anchor)
        )

        assertEquals(1, history.dates.count { it.date == date })
        assertEquals(1.0, history.weightedDistinctExposureDays, 1e-9)
    }

    @Test
    fun globalGapBoundariesUseExactDateCounts() {
        assertGlobalGap(6, expectedIncludedGapDates = 6, expectedOlderWeight = 1.0)
        assertGlobalGap(7, expectedIncludedGapDates = 0, expectedOlderWeight = 1.0)
        assertGlobalGap(13, expectedIncludedGapDates = 0, expectedOlderWeight = 1.0)
        assertGlobalGap(14, expectedIncludedGapDates = 0, expectedOlderWeight = 0.5)
        assertGlobalGap(27, expectedIncludedGapDates = 0, expectedOlderWeight = 0.5)
        assertGlobalGap(28, expectedIncludedGapDates = 0, expectedOlderWeight = 0.0)
    }

    @Test
    fun perUnitGapBoundariesUseExactDateCountsAndIgnoreUnrelatedTraining() {
        assertUnitGap(13, expectedIncludedGapDates = 13, expectedOlderWeight = 1.0)
        assertUnitGap(14, expectedIncludedGapDates = 0, expectedOlderWeight = 0.5)
        assertUnitGap(27, expectedIncludedGapDates = 0, expectedOlderWeight = 0.5)
        assertUnitGap(28, expectedIncludedGapDates = 0, expectedOlderWeight = 0.0)
    }

    @Test
    fun sameGlobalAndUnitBoundaryAppliesHalfRetentionOnce() {
        val newer = anchor.minusDays(7)
        val older = newer.minusDays(15)
        val history = history(
            workouts = listOf(older, newer, anchor),
            exposures = listOf(older, newer, anchor)
        )

        assertEquals(0.5, history.dates.single { it.date == older }.weight, 1e-9)
    }

    @Test
    fun twoDistinctMediumBoundariesAccumulateRetention() {
        val newest = anchor.minusDays(7)
        val middle = newest.minusDays(15)
        val oldest = middle.minusDays(15)
        val history = history(
            workouts = listOf(oldest, middle, newest, anchor),
            exposures = listOf(oldest, middle, newest, anchor)
        )

        assertEquals(0.25, history.dates.single { it.date == oldest }.weight, 1e-9)
    }

    @Test
    fun zeroRetentionBoundaryStopsOlderTraversal() {
        val newer = anchor.minusDays(7)
        val older = newer.minusDays(29)
        val history = history(
            workouts = listOf(older, newer, anchor),
            exposures = listOf(older, newer, anchor)
        )

        assertFalse(history.dates.any { it.date == older })
    }

    private fun assertGlobalGap(length: Int, expectedIncludedGapDates: Int, expectedOlderWeight: Double) {
        val newer = anchor.minusDays(7)
        val older = newer.minusDays(length.toLong() + 1)
        val history = history(
            workouts = listOf(older, newer, anchor),
            exposures = listOf(older, newer, anchor)
        )
        val gapDates = (1L..length.toLong()).map(older::plusDays)

        assertEquals(expectedIncludedGapDates, history.dates.count { it.date in gapDates })
        if (expectedOlderWeight == 0.0) {
            assertFalse(history.dates.any { it.date == older })
        } else {
            assertEquals(expectedOlderWeight, history.dates.single { it.date == older }.weight, 1e-9)
        }
    }

    private fun assertUnitGap(length: Int, expectedIncludedGapDates: Int, expectedOlderWeight: Double) {
        val newer = anchor.minusDays(7)
        val older = newer.minusDays(length.toLong() + 1)
        val allWorkoutDates = (0L..(length + 8).toLong()).map(anchor::minusDays)
        val history = history(
            workouts = allWorkoutDates,
            exposures = listOf(older, newer, anchor)
        )
        val gapDates = (1L..length.toLong()).map(older::plusDays)

        assertEquals(expectedIncludedGapDates, history.dates.count { it.date in gapDates })
        if (expectedOlderWeight == 0.0) {
            assertFalse(history.dates.any { it.date == older })
        } else {
            assertEquals(expectedOlderWeight, history.dates.single { it.date == older }.weight, 1e-9)
        }
    }

    private fun history(
        workouts: Collection<LocalDate>,
        exposures: Collection<LocalDate>
    ): TissueCalibrationHistory = TissueCalibrationHistoryPolicy.build(
        anchorDate = anchor,
        confirmedWorkoutDates = workouts,
        unitExposureDates = exposures,
        maxWeightedObservationDays = 200.0
    )
}

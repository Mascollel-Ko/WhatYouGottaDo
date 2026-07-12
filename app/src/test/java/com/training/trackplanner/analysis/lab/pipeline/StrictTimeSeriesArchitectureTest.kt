package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictTimeSeriesArchitectureTest {
    @Test
    fun ingestionOwnsCanonicalContinuousCalendarAndMissingCells() {
        val metric = TrendMetricId.BADMINTON_TRAINING
        val input = RawTimeSeriesInput.fromTrendSeries(
            mapOf(
                metric to listOf(
                    TrendDataPoint(LocalDate.parse("2026-01-06"), 1.0),
                    TrendDataPoint(LocalDate.parse("2026-01-20"), 3.0)
                )
            )
        )

        val catalog = input.ingest(request(metric))
        val series = catalog.seriesByMetric.getValue(metric)

        assertEquals(
            listOf(LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-12"), LocalDate.parse("2026-01-19")),
            catalog.calendar.weeks
        )
        assertEquals(StrictCellState.MISSING, series.cells[1].state)
        assertNull(series.cells[1].value)
    }

    @Test
    fun conflictingRawObservationsNeverBecomeNumeric() {
        val metric = TrendMetricId.BADMINTON_TRAINING
        val input = RawTimeSeriesInput.createValidated(
            listOf(
                RawTimeSeriesObservation(metric, LocalDate.parse("2026-01-05"), 1.0, sourceIndex = 0),
                RawTimeSeriesObservation(metric, LocalDate.parse("2026-01-06"), 2.0, sourceIndex = 1)
            )
        )

        val cell = input.ingest(request(metric)).seriesByMetric.getValue(metric).cells.single()

        assertEquals(StrictCellState.CONFLICT, cell.state)
        assertNull(cell.value)
        assertEquals(2, cell.provenance.size)
    }

    @Test
    fun identityTypesDeriveFingerprintsAndDefensivelyCopyCollections() {
        val weeks = mutableListOf(LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-12"))
        val calendar = CanonicalCalendar.createValidated(weeks)
        weeks.clear()
        val same = CanonicalCalendar.createValidated(listOf(LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-12")))
        val changed = CanonicalCalendar.createValidated(listOf(LocalDate.parse("2026-01-12"), LocalDate.parse("2026-01-19")))

        assertEquals(2, calendar.weeks.size)
        assertEquals(calendar.fingerprint, same.fingerprint)
        assertNotEquals(calendar.fingerprint, changed.fingerprint)
        assertTrue(CanonicalCalendar::class.java.declaredConstructors.filterNot { it.isSynthetic }.all { Modifier.isPrivate(it.modifiers) })
        assertTrue(LifecycleValidatedLevelSeries::class.java.declaredConstructors.filterNot { it.isSynthetic }.all { Modifier.isPrivate(it.modifiers) })
    }

    private fun request(metric: TrendMetricId) = StrictPreparationRequest(
        xMetric = metric,
        yMetrics = listOf(TrendMetricId.FATIGUE_COMPOSITE)
    )
}

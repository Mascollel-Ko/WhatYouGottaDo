package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSeriesCalendarGridTest {
    @Test
    fun alignmentBuildsContinuousIsoWeekGridAndPreservesMissingWeeks() {
        val alignment = fixture()

        assertEquals(
            listOf("2025-12-22", "2025-12-29", "2026-01-05", "2026-01-12"),
            alignment.weeks.map(LocalDate::toString)
        )
        assertEquals(
            listOf(
                TimeSeriesCellState.OBSERVED_VALUE,
                TimeSeriesCellState.OBSERVED_VALUE,
                TimeSeriesCellState.STRUCTURAL_ZERO,
                TimeSeriesCellState.OBSERVED_VALUE
            ),
            alignment.grid!!.cellsByMetric.getValue(TrendMetricId.BADMINTON_TRAINING).map(TimeSeriesCell::state)
        )
        assertEquals(
            listOf(
                TimeSeriesCellState.OBSERVED_VALUE,
                TimeSeriesCellState.MISSING,
                TimeSeriesCellState.OBSERVED_VALUE,
                TimeSeriesCellState.OBSERVED_VALUE
            ),
            alignment.grid!!.cellsByMetric.getValue(TrendMetricId.FATIGUE_COMPOSITE).map(TimeSeriesCell::state)
        )
    }

    @Test
    fun structuralZeroIsNotTreatedAsMissingValue() {
        val alignment = fixture()

        assertEquals(0.0, alignment.valueAt(TrendMetricId.BADMINTON_TRAINING, 2)!!, 0.0)
        assertNull(alignment.valueAt(TrendMetricId.FATIGUE_COMPOSITE, 1))
        assertEquals(0.0, alignment.qualitySummaries.getValue(TrendMetricId.BADMINTON_TRAINING).rawMissingRate, 0.0)
        assertEquals(0.25, alignment.qualitySummaries.getValue(TrendMetricId.FATIGUE_COMPOSITE).rawMissingRate, 0.0)
    }

    @Test
    fun lagDifferenceAndHorizonRequireExactCalendarWeeks() {
        val alignment = fixture()

        assertEquals(0.0, alignment.exactLag(TrendMetricId.BADMINTON_TRAINING, 3, 1)!!, 0.0)
        assertNull(alignment.exactLag(TrendMetricId.FATIGUE_COMPOSITE, 2, 1))
        assertNull(alignment.exactDifference(TrendMetricId.FATIGUE_COMPOSITE, 2))
        assertEquals(3.0, alignment.exactHorizon(TrendMetricId.FATIGUE_COMPOSITE, 0, 2)!!, 0.0)
    }

    @Test
    fun exactRowsKeepExclusionReasons() {
        val alignment = fixture()
        val (rows, exclusions) = alignment.buildExactRows(
            sourceMetric = TrendMetricId.FATIGUE_COMPOSITE,
            targetMetric = TrendMetricId.BADMINTON_TRAINING,
            lag = 1,
            horizon = 1
        )

        assertTrue(rows.isEmpty())
        assertTrue(exclusions.any { it.reason in setOf(TimeSeriesRowExclusionReason.MISSING_SOURCE, TimeSeriesRowExclusionReason.MISSING_LAG) })
        assertTrue(exclusions.all { it.sourceWeek != null && it.cellReferences.isNotEmpty() })
        val first = exclusions.first()
        assertTrue(first.cellReferences.any { it.role == TimeSeriesCellRole.SOURCE })
        assertTrue(first.cellReferences.any { it.role == TimeSeriesCellRole.TARGET })
        assertTrue(first.cellReferences.any { it.role == TimeSeriesCellRole.LAG && it.lagOrder == 1 })
    }

    @Test
    fun lifecycleMetadataCreatesEveryCellStateWithoutDroppingNullWeeks() {
        val alignment = TimeSeriesAlignmentService().alignObservations(
            metrics = listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE),
            observations = listOf(
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2025-12-22"), 99.0),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2025-12-29"), 1.0),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-05"), null, TimeSeriesCellState.MISSING, "user skipped entry"),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-26"), 2.0),
                TimeSeriesObservation(TrendMetricId.FATIGUE_COMPOSITE, LocalDate.parse("2026-01-26"), null)
            ),
            lifecycleMetadata = mapOf(
                TrendMetricId.BADMINTON_TRAINING to MetricLifecycleMetadata(
                    availableFromWeek = LocalDate.parse("2025-12-29"),
                    structuralZeroAllowed = true,
                    notApplicableWeeks = setOf(LocalDate.parse("2026-01-19"))
                ),
                TrendMetricId.FATIGUE_COMPOSITE to MetricLifecycleMetadata(
                    availableFromWeek = LocalDate.parse("2025-12-22"),
                    versionDiscontinuityWeeks = setOf(LocalDate.parse("2026-01-26"))
                )
            )
        )!!

        val badmintonStates = alignment.grid!!.cellsByMetric.getValue(TrendMetricId.BADMINTON_TRAINING).map(TimeSeriesCell::state)
        assertEquals(TimeSeriesCellState.PRE_METRIC_CREATION, badmintonStates[0])
        assertEquals(TimeSeriesCellState.OBSERVED_VALUE, badmintonStates[1])
        assertEquals(TimeSeriesCellState.MISSING, badmintonStates[2])
        assertEquals(TimeSeriesCellState.STRUCTURAL_ZERO, badmintonStates[3])
        assertEquals(TimeSeriesCellState.NOT_APPLICABLE, badmintonStates[4])
        assertEquals(TimeSeriesCellState.OBSERVED_VALUE, badmintonStates[5])
        assertEquals(TimeSeriesCellState.VERSION_DISCONTINUITY, alignment.grid.cellsByMetric.getValue(TrendMetricId.FATIGUE_COMPOSITE)[5].state)
        assertNull(alignment.valueAt(TrendMetricId.BADMINTON_TRAINING, 0))
    }

    @Test
    fun rawDatesAreCanonicalizedButExplicitWeekStartValidationRejectsTuesday() {
        val alignment = TimeSeriesAlignmentService().align(
            listOf(TrendMetricId.BADMINTON_TRAINING),
            mapOf(TrendMetricId.BADMINTON_TRAINING to listOf(TrendDataPoint(LocalDate.parse("2026-01-06"), 1.0)))
        )!!

        assertEquals(LocalDate.parse("2026-01-05"), alignment.weeks.single())
        assertTrue(
            runCatching {
                TimeSeriesObservation(
                    TrendMetricId.BADMINTON_TRAINING,
                    LocalDate.parse("2026-01-06"),
                    null,
                    TimeSeriesCellState.MISSING
                )
            }.isFailure
        )
    }

    @Test
    fun unselectedMetricsDoNotExpandGridBounds() {
        val alignment = TimeSeriesAlignmentService().align(
            listOf(TrendMetricId.BADMINTON_TRAINING),
            mapOf(
                TrendMetricId.BADMINTON_TRAINING to listOf(TrendDataPoint(LocalDate.parse("2026-01-05"), 1.0)),
                TrendMetricId.FATIGUE_COMPOSITE to listOf(TrendDataPoint(LocalDate.parse("2024-01-01"), 9.0))
            )
        )!!

        assertEquals(listOf(LocalDate.parse("2026-01-05")), alignment.weeks)
    }

    @Test
    fun unknownActivationDoesNotInferPreCreationFromFirstObservation() {
        val alignment = TimeSeriesAlignmentService().alignObservations(
            metrics = listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE),
            observations = listOf(
                TimeSeriesObservation(TrendMetricId.FATIGUE_COMPOSITE, LocalDate.parse("2026-01-05"), 1.0),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-19"), 2.0)
            ),
            lifecycleMetadata = mapOf(TrendMetricId.BADMINTON_TRAINING to MetricLifecycleMetadata(structuralZeroAllowed = true))
        )!!

        val states = alignment.grid!!.cellsByMetric.getValue(TrendMetricId.BADMINTON_TRAINING).map(TimeSeriesCell::state)
        assertEquals(TimeSeriesCellState.MISSING, states[0])
        assertEquals(TimeSeriesCellState.MISSING, states[1])
        assertEquals(TimeSeriesCellState.OBSERVED_VALUE, states[2])
    }

    @Test
    fun firstObservationActivationWorksOnlyWhenExplicitlyEnabled() {
        val alignment = TimeSeriesAlignmentService().alignObservations(
            metrics = listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE),
            observations = listOf(
                TimeSeriesObservation(TrendMetricId.FATIGUE_COMPOSITE, LocalDate.parse("2026-01-05"), 1.0),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-19"), 2.0)
            ),
            lifecycleMetadata = mapOf(
                TrendMetricId.BADMINTON_TRAINING to MetricLifecycleMetadata(
                    structuralZeroAllowed = true,
                    activationPolicy = MetricActivationPolicy.FIRST_OBSERVATION_ALLOWED
                )
            )
        )!!

        val states = alignment.grid!!.cellsByMetric.getValue(TrendMetricId.BADMINTON_TRAINING).map(TimeSeriesCell::state)
        assertEquals(TimeSeriesCellState.PRE_METRIC_CREATION, states[0])
        assertEquals(TimeSeriesCellState.PRE_METRIC_CREATION, states[1])
        assertEquals(TimeSeriesCellState.OBSERVED_VALUE, states[2])
    }

    @Test
    fun conflictingObservationsBecomeConflictCellsInsteadOfArbitrarySourceChoice() {
        val alignment = TimeSeriesAlignmentService().alignObservations(
            metrics = listOf(TrendMetricId.BADMINTON_TRAINING),
            observations = listOf(
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-05"), 10.0, source = "b"),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-05"), 20.0, source = "a")
            )
        )!!

        val cell = alignment.grid!!.cell(TrendMetricId.BADMINTON_TRAINING, 0)!!
        assertEquals(TimeSeriesCellState.CONFLICT, cell.state)
        assertEquals(2, cell.candidateCount)
        assertTrue(cell.conflictProvenance!!.unresolvedConflict)
        assertNull(cell.value)
        assertNull(alignment.valueAt(TrendMetricId.BADMINTON_TRAINING, 0))
    }

    @Test
    fun typedRevisionOrderingWinsWithoutLexicalVersionOrdering() {
        val alignment = TimeSeriesAlignmentService().alignObservations(
            metrics = listOf(TrendMetricId.BADMINTON_TRAINING),
            observations = listOf(
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-05"), 9.0, version = "9", revisionNumber = 9),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-05"), 10.0, version = "10", revisionNumber = 10)
            )
        )!!

        val cell = alignment.grid!!.cell(TrendMetricId.BADMINTON_TRAINING, 0)!!
        assertEquals(TimeSeriesCellState.OBSERVED_VALUE, cell.state)
        assertEquals(10.0, cell.value!!, 0.0)
        assertEquals(ObservationConflictSelectionRule.TYPED_REVISION_ORDER, cell.conflictProvenance!!.selectionRule)
        assertEquals(2, cell.conflictProvenance.candidates.size)
    }

    @Test
    fun stateValueInvariantRejectsContradictions() {
        assertTrue(runCatching { TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-05"), null, TimeSeriesCellState.OBSERVED_VALUE) }.isFailure)
        assertTrue(runCatching { TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-05"), Double.NaN, TimeSeriesCellState.OBSERVED_VALUE) }.isFailure)
        assertTrue(runCatching { TimeSeriesCell(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-05"), TimeSeriesCellState.STRUCTURAL_ZERO, 3.0) }.isFailure)
        assertTrue(runCatching { TimeSeriesCell(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-05"), TimeSeriesCellState.VERSION_DISCONTINUITY, 1.0) }.isFailure)
        assertTrue(runCatching { TimeSeriesCell(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-05"), TimeSeriesCellState.CONFLICT, null) }.isFailure)
    }

    @Test
    fun stationarizePreservesLifecycleProvenanceAndRecomputesMissingRate() {
        val alignment = TimeSeriesAlignmentService().alignObservations(
            metrics = listOf(TrendMetricId.BADMINTON_TRAINING),
            observations = (0..10).map { index ->
                val week = LocalDate.parse("2026-01-05").plusWeeks(index.toLong())
                if (index == 5) {
                    TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, week, null, TimeSeriesCellState.VERSION_DISCONTINUITY)
                } else {
                    TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, week, index.toDouble())
                }
            }
        )!!

        val result = TimeSeriesAlignmentService().stationarize(
            alignment,
            listOf(IntegrationDiagnostic(TrendMetricId.BADMINTON_TRAINING, IntegrationOrder.I1, 0.0, 0.0, 0.0, 0.0, "fixture"))
        )!!.first

        val cells = result.grid!!.cellsByMetric.getValue(TrendMetricId.BADMINTON_TRAINING)
        assertEquals(alignment.weeks, result.weeks)
        assertEquals(TimeSeriesCellState.MISSING, cells[0].state)
        assertEquals(TimeSeriesCellState.VERSION_DISCONTINUITY, cells[5].state)
        assertEquals(TimeSeriesCellState.VERSION_DISCONTINUITY, cells[6].state)
        assertTrue(cells[5].sourceCells.any { it.role == TimeSeriesCellRole.SOURCE })
        assertTrue(result.qualitySummaries.getValue(TrendMetricId.BADMINTON_TRAINING).unusableRate > 0.0)
    }

    @Test
    fun allLevelStationarizationPreservesFirstWeekAndPreparedSeries() {
        val weeks = (0 until 8).map { LocalDate.parse("2026-01-05").plusWeeks(it.toLong()) }
        val alignment = TimeSeriesAlignmentService().align(
            listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE),
            mapOf(
                TrendMetricId.BADMINTON_TRAINING to weeks.mapIndexed { index, week -> TrendDataPoint(week, index.toDouble()) },
                TrendMetricId.FATIGUE_COMPOSITE to weeks.mapIndexed { index, week -> TrendDataPoint(week, index.toDouble() * 2.0) }
            )
        )!!
        val result = TimeSeriesAlignmentService().stationarize(
            alignment,
            listOf(
                IntegrationDiagnostic(TrendMetricId.BADMINTON_TRAINING, IntegrationOrder.I0, 0.0, 0.0, null, null, "fixture"),
                IntegrationDiagnostic(TrendMetricId.FATIGUE_COMPOSITE, IntegrationOrder.I0, 0.0, 0.0, null, null, "fixture")
            )
        )!!.first

        assertEquals(alignment.weeks, result.weeks)
        assertEquals(alignment.weeks.first(), result.preparedSeries.getValue(TrendMetricId.BADMINTON_TRAINING).weeks.first())
    }

    @Test
    fun qualitySummarySeparatesMissingLifecycleAndConflictCounts() {
        val alignment = TimeSeriesAlignmentService().alignObservations(
            metrics = listOf(TrendMetricId.BADMINTON_TRAINING),
            observations = listOf(
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-12"), 1.0),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-19"), 2.0, source = "a"),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-19"), 3.0, source = "b")
            ),
            lifecycleMetadata = mapOf(
                TrendMetricId.BADMINTON_TRAINING to MetricLifecycleMetadata(availableFromWeek = LocalDate.parse("2026-01-05"))
            )
        )!!

        val summary = alignment.qualitySummaries.getValue(TrendMetricId.BADMINTON_TRAINING)
        assertEquals(1, summary.missingCount)
        assertEquals(1, summary.conflictCount)
        assertEquals(0, summary.preMetricCreationCount)
        assertTrue(summary.unusableRate > 0.0)
        assertTrue(summary.coverageRate < 1.0)
    }

    @Test
    fun candidateScreeningConsumesPreparedSeriesAndRejectsBlockingStates() {
        val weeks = (0 until 8).map { LocalDate.parse("2026-01-05").plusWeeks(it.toLong()) }
        val alignment = TimeSeriesAlignmentService().alignObservations(
            metrics = listOf(TrendMetricId.BADMINTON_TRAINING),
            observations = weeks.flatMapIndexed { index, week ->
                if (index == 4) {
                    listOf(
                        TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, week, 1.0, source = "a"),
                        TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, week, 2.0, source = "b")
                    )
                } else {
                    listOf(TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, week, index.toDouble()))
                }
            }
        )!!
        val service = TimeSeriesAlignmentService()

        val reason = service.usablePreparedCandidate(
            TrendMetricId.BADMINTON_TRAINING,
            weeks.toSet(),
            service.prepareSeries(alignment)
        )

        assertEquals("series has unresolved conflict cells", reason)
    }

    @Test
    fun gridRejectsInvalidWeekAndCellShapes() {
        val monday = LocalDate.parse("2026-01-05")
        val metric = TrendMetricId.BADMINTON_TRAINING
        assertTrue(runCatching {
            TimeSeriesCalendarGrid.createValidated(
                listOf(monday, monday),
                mapOf(metric to listOf(cell(metric, monday), cell(metric, monday)))
            )
        }.isFailure)
        assertTrue(runCatching {
            TimeSeriesCalendarGrid.createValidated(
                listOf(monday, monday.plusDays(8)),
                mapOf(metric to listOf(cell(metric, monday), cell(metric, monday.plusDays(8))))
            )
        }.isFailure)
        assertTrue(runCatching {
            TimeSeriesCalendarGrid.createValidated(
                listOf(monday, monday.plusWeeks(1)),
                mapOf(metric to listOf(cell(metric, monday)))
            )
        }.isFailure)
        assertTrue(runCatching {
            TimeSeriesCalendarGrid.createValidated(
                listOf(monday),
                mapOf(metric to listOf(cell(TrendMetricId.FATIGUE_COMPOSITE, monday)))
            )
        }.isFailure)
    }

    @Test
    fun restrictToWeeksDoesNotWrapNonContiguousSelectionAsGrid() {
        val alignment = fixture()
        assertNull(TimeSeriesAlignmentService().restrictToWeeks(alignment, listOf(alignment.weeks[0], alignment.weeks[2])))
        assertTrue(TimeSeriesAlignmentService().restrictToWeeks(alignment, listOf(alignment.weeks[1], alignment.weeks[2])) != null)
    }

    @Test
    fun sameMetricSourceAndTargetCellReferencesDoNotOverwriteEachOther() {
        val alignment = fixture()
        val (_, exclusions) = alignment.buildExactRows(
            sourceMetric = TrendMetricId.FATIGUE_COMPOSITE,
            targetMetric = TrendMetricId.FATIGUE_COMPOSITE,
            lag = 1,
            horizon = 1
        )

        val exclusion = exclusions.first()
        assertEquals(3, exclusion.cellReferences.size)
        assertTrue(exclusion.cellReferences.any { it.role == TimeSeriesCellRole.SOURCE && it.metric == TrendMetricId.FATIGUE_COMPOSITE })
        assertTrue(exclusion.cellReferences.any { it.role == TimeSeriesCellRole.TARGET && it.metric == TrendMetricId.FATIGUE_COMPOSITE })
        assertFalse(exclusion.cellReferences.map { it.role }.toSet().size == 1)
    }

    @Test
    fun everySourceWeekIsIncludedOrExcludedWithBoundaryReasons() {
        val alignment = fixture()
        val (rows, exclusions) = alignment.buildExactRows(
            sourceMetric = TrendMetricId.BADMINTON_TRAINING,
            targetMetric = TrendMetricId.BADMINTON_TRAINING,
            lag = 1,
            horizon = 1
        )

        assertEquals(alignment.weeks.size, rows.size + exclusions.size)
        assertTrue(exclusions.any { it.reason == TimeSeriesRowExclusionReason.SOURCE_BEFORE_REQUIRED_LAGS })
        assertTrue(exclusions.any { it.reason == TimeSeriesRowExclusionReason.TARGET_OUTSIDE_GRID })
    }

    @Test
    fun lagAndHorizonInputValidationIsExplicit() {
        val alignment = fixture()
        assertTrue(runCatching { alignment.buildExactRows(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE, -1, 1) }.isFailure)
        assertTrue(runCatching { alignment.buildExactRows(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE, 1, -1) }.isFailure)
        val (rows, exclusions) = alignment.buildExactRows(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.BADMINTON_TRAINING, 0, 0)
        assertTrue(rows.isNotEmpty())
        assertEquals(alignment.weeks.size, rows.size + exclusions.size)
    }

    private fun fixture(): TimeSeriesAlignment {
        val weeks = listOf("2025-12-22", "2025-12-29", "2026-01-12").map(LocalDate::parse)
        return TimeSeriesAlignmentService().align(
            listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE),
            mapOf(
                TrendMetricId.BADMINTON_TRAINING to weeks.mapIndexed { index, week -> TrendDataPoint(week, (index + 1).toDouble()) },
                TrendMetricId.FATIGUE_COMPOSITE to listOf(
                    TrendDataPoint(LocalDate.parse("2025-12-22"), 0.0),
                    TrendDataPoint(LocalDate.parse("2026-01-05"), 3.0),
                    TrendDataPoint(LocalDate.parse("2026-01-12"), 5.0)
                )
            ),
            structuralZeroMetrics = setOf(TrendMetricId.BADMINTON_TRAINING)
        )!!
    }

    private fun cell(metric: TrendMetricId, week: LocalDate): TimeSeriesCell =
        TimeSeriesCell(metric, week, TimeSeriesCellState.OBSERVED_VALUE, 1.0)
}

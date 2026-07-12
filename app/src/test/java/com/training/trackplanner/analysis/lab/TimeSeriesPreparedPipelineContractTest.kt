package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSeriesPreparedPipelineContractTest {
    @Test
    fun qualitySummaryCountsEachEligibleCellOnceWhenDiagnosticsOverlap() {
        val metric = TrendMetricId.BADMINTON_TRAINING
        val weeks = weeks(6)
        val cells = listOf(
            TimeSeriesCell(metric, weeks[0], TimeSeriesCellState.MISSING, null, missingReason = "transformation unavailable: MISSING"),
            TimeSeriesCell(metric, weeks[1], TimeSeriesCellState.VERSION_DISCONTINUITY, null, missingReason = "transformation unavailable: VERSION_DISCONTINUITY"),
            TimeSeriesCell(metric, weeks[2], TimeSeriesCellState.CONFLICT, null, missingReason = "transformation unavailable: CONFLICT", conflictProvenance = conflictProvenance(metric, weeks[2])),
            TimeSeriesCell(metric, weeks[3], TimeSeriesCellState.OBSERVED_VALUE, 4.0),
            TimeSeriesCell(metric, weeks[4], TimeSeriesCellState.PRE_METRIC_CREATION, null),
            TimeSeriesCell(metric, weeks[5], TimeSeriesCellState.NOT_APPLICABLE, null)
        )

        val summary = MetricDataQualitySummary.fromCells(cells)

        assertEquals(4, summary.modelEligibleWeekCount)
        assertEquals(1, summary.usableCount)
        assertEquals(3, summary.unusableCount)
        assertEquals(3, summary.transformationFailureCount)
        assertEquals(0.75, summary.unusableRate, 0.0)
        assertEquals(0.25, summary.coverageRate, 0.0)
        assertTrue(summary.unusableRate <= 1.0)
        assertEquals(summary.modelEligibleWeekCount, summary.usableCount + summary.unusableCount)
    }

    @Test
    fun restrictionAndStationarizationPreserveLifecycleFingerprint() {
        val metadata = MetricLifecycleMetadata(
            availableFromWeek = LocalDate.parse("2026-01-05"),
            availableUntilWeek = LocalDate.parse("2026-03-23"),
            structuralZeroAllowed = true,
            activationPolicy = MetricActivationPolicy.REGISTRY_DEFINED,
            notApplicableWeeks = setOf(LocalDate.parse("2026-03-30")),
            versionDiscontinuityRanges = listOf(TimeSeriesWeekRange(LocalDate.parse("2026-02-09"), LocalDate.parse("2026-02-09"))),
            provenance = LifecycleMetadataProvenance(
                source = "registry",
                sourceVersion = "phase-a-test",
                registryVersion = "2026-07",
                inferencePolicy = "fixture",
                metadataVersion = 7
            )
        )
        val alignment = TimeSeriesAlignmentService().alignObservations(
            metrics = listOf(TrendMetricId.BADMINTON_TRAINING),
            observations = weeks(12).mapIndexed { index, week ->
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, week, index.toDouble())
            },
            lifecycleMetadata = mapOf(TrendMetricId.BADMINTON_TRAINING to metadata)
        )!!
        val original = alignment.preparedSeries.getValue(TrendMetricId.BADMINTON_TRAINING).lifecycleMetadata

        val restricted = TimeSeriesAlignmentService().restrictToWeeks(alignment, alignment.weeks.drop(1).take(9))!!
        val stationarized = TimeSeriesAlignmentService().stationarize(
            alignment,
            listOf(IntegrationDiagnostic(TrendMetricId.BADMINTON_TRAINING, IntegrationOrder.I1, 0.0, 0.0, 0.0, 0.0, "fixture"))
        )!!.first

        assertEquals(original, restricted.preparedSeries.getValue(TrendMetricId.BADMINTON_TRAINING).lifecycleMetadata)
        assertEquals(original.fingerprint(), restricted.preparedSeries.getValue(TrendMetricId.BADMINTON_TRAINING).lifecycleMetadata.fingerprint())
        assertEquals(original, stationarized.preparedSeries.getValue(TrendMetricId.BADMINTON_TRAINING).lifecycleMetadata)
        assertEquals(original.fingerprint(), stationarized.preparedSeries.getValue(TrendMetricId.BADMINTON_TRAINING).lifecycleMetadata.fingerprint())
    }

    @Test
    fun heterogeneousRevisionSchemesProduceConflictWithoutOrderingFieldsTogether() {
        val alignment = TimeSeriesAlignmentService().alignObservations(
            metrics = listOf(TrendMetricId.BADMINTON_TRAINING),
            observations = listOf(
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-05"), 1.0, revisionNumber = 2),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, LocalDate.parse("2026-01-05"), 2.0, versionSequence = 3)
            )
        )!!

        val cell = alignment.grid!!.cell(TrendMetricId.BADMINTON_TRAINING, 0)!!

        assertEquals(TimeSeriesCellState.CONFLICT, cell.state)
        assertEquals(ObservationConflictSelectionRule.HETEROGENEOUS_REVISION_SCHEME_CONFLICT, cell.conflictProvenance!!.selectionRule)
        assertNull(cell.value)
    }

    @Test
    fun tiedHighestRevisionWithDifferentValuesIsConflictAndPermutationInvariant() {
        val monday = LocalDate.parse("2026-01-05")
        val observations = listOf(
            TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, monday, 1.0, revisionNumber = 1, source = "old"),
            TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, monday, 2.0, revisionNumber = 2, source = "a"),
            TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, monday, 3.0, revisionNumber = 2, source = "b")
        )
        val results = listOf(
            observations,
            listOf(observations[2], observations[0], observations[1]),
            listOf(observations[1], observations[2], observations[0])
        ).map { ordered ->
            TimeSeriesAlignmentService().alignObservations(listOf(TrendMetricId.BADMINTON_TRAINING), ordered)!!
                .grid!!.cell(TrendMetricId.BADMINTON_TRAINING, 0)!!
        }

        results.forEach { cell ->
            assertEquals(TimeSeriesCellState.CONFLICT, cell.state)
            assertEquals(ObservationConflictSelectionRule.TIED_HIGHEST_REVISION_CONFLICT, cell.conflictProvenance!!.selectionRule)
            assertEquals(3, cell.conflictProvenance.candidates.size)
        }
        assertEquals(results.first().state, results.last().state)
        assertEquals(results.first().conflictProvenance!!.selectionRule, results.last().conflictProvenance!!.selectionRule)
    }

    @Test
    fun tiedHighestRevisionWithIdenticalValuesMerges() {
        val monday = LocalDate.parse("2026-01-05")
        val alignment = TimeSeriesAlignmentService().alignObservations(
            metrics = listOf(TrendMetricId.BADMINTON_TRAINING),
            observations = listOf(
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, monday, 1.0, revisionNumber = 1),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, monday, 2.0, revisionNumber = 2, source = "a"),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, monday, 2.0, revisionNumber = 2, source = "b")
            )
        )!!

        val cell = alignment.grid!!.cell(TrendMetricId.BADMINTON_TRAINING, 0)!!

        assertEquals(TimeSeriesCellState.OBSERVED_VALUE, cell.state)
        assertEquals(2.0, cell.value!!, 0.0)
        assertEquals(ObservationConflictSelectionRule.IDENTICAL_DUPLICATE_MERGE, cell.conflictProvenance!!.selectionRule)
        assertTrue(!cell.conflictProvenance.unresolvedConflict)
    }

    @Test
    fun ordinaryObservationTimeIsNotRevisionAuthority() {
        val monday = LocalDate.parse("2026-01-05")
        val alignment = TimeSeriesAlignmentService().alignObservations(
            metrics = listOf(TrendMetricId.BADMINTON_TRAINING),
            observations = listOf(
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, monday, 1.0, observedAt = Instant.parse("2026-01-06T00:00:00Z")),
                TimeSeriesObservation(TrendMetricId.BADMINTON_TRAINING, monday, 2.0, observedAt = Instant.parse("2026-01-07T00:00:00Z"))
            )
        )!!

        val cell = alignment.grid!!.cell(TrendMetricId.BADMINTON_TRAINING, 0)!!

        assertEquals(TimeSeriesCellState.CONFLICT, cell.state)
        assertEquals(ObservationConflictSelectionRule.UNRESOLVED_CONFLICT, cell.conflictProvenance!!.selectionRule)
    }

    @Test
    fun preparedSeriesFactoryRejectsInconsistentCellsAndDerivesSummary() {
        val metric = TrendMetricId.BADMINTON_TRAINING
        val weeks = weeks(3)
        val valid = PreparedMetricSeries.createValidated(
            metric = metric,
            weeks = weeks,
            cells = weeks.mapIndexed { index, week -> TimeSeriesCell(metric, week, TimeSeriesCellState.OBSERVED_VALUE, index.toDouble()) },
            transformation = "level",
            lifecycleMetadata = MetricLifecycleMetadata(),
            provenance = listOf("test")
        )

        assertEquals(3, valid.qualitySummary.usableCount)
        assertEquals(valid.qualitySummary, MetricDataQualitySummary.fromCells(valid.cells))
        assertEquals(valid.contiguousSegments, contiguousUsableSegments(valid.cells))
        assertTrue(
            runCatching {
                PreparedMetricSeries.createValidated(
                    metric = metric,
                    weeks = weeks,
                    cells = weeks.map { week -> TimeSeriesCell(TrendMetricId.FATIGUE_COMPOSITE, week, TimeSeriesCellState.OBSERVED_VALUE, 1.0) },
                    transformation = "level",
                    lifecycleMetadata = MetricLifecycleMetadata(),
                    provenance = listOf("test")
                )
            }.isFailure
        )
    }

    @Test
    fun preparedSystemDerivesCommonRowsAndDeterministicFingerprints() {
        val weeks = weeks(10)
        val first = preparedSeries(TrendMetricId.BADMINTON_TRAINING, weeks) { index -> index.toDouble() }
        val second = preparedSeries(TrendMetricId.FATIGUE_COMPOSITE, weeks) { index -> if (index == 4) null else index.toDouble() * 2.0 }
        val prepared = mapOf(first.metric to first, second.metric to second)

        val system = PreparedTimeSeriesSystem.createValidated(
            orderedMetrics = listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE),
            preparedSeries = prepared,
            lag = 1,
            horizon = 1
        )
        val reversed = PreparedTimeSeriesSystem.createValidated(
            orderedMetrics = listOf(TrendMetricId.FATIGUE_COMPOSITE, TrendMetricId.BADMINTON_TRAINING),
            preparedSeries = prepared,
            lag = 1,
            horizon = 1
        )
        val differentLag = PreparedTimeSeriesSystem.createValidated(
            orderedMetrics = listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE),
            preparedSeries = prepared,
            lag = 2,
            horizon = 1
        )

        assertEquals(system.preparationFingerprint, reversed.preparationFingerprint)
        assertNotEquals(system.preparationFingerprint, differentLag.preparationFingerprint)
        assertTrue(system.commonUsableRows.none { row ->
            row.sourceWeek == weeks[4] || row.targetWeek == weeks[4] || weeks[4] in row.lagWeeks
        })
    }

    private fun weeks(count: Int): List<LocalDate> =
        (0 until count).map { LocalDate.parse("2026-01-05").plusWeeks(it.toLong()) }

    private fun preparedSeries(
        metric: TrendMetricId,
        weeks: List<LocalDate>,
        valueAt: (Int) -> Double?
    ): PreparedMetricSeries =
        PreparedMetricSeries.createValidated(
            metric = metric,
            weeks = weeks,
            cells = weeks.mapIndexed { index, week ->
                valueAt(index)?.let { value ->
                    TimeSeriesCell(metric, week, TimeSeriesCellState.OBSERVED_VALUE, value)
                } ?: TimeSeriesCell(metric, week, TimeSeriesCellState.MISSING, null)
            },
            transformation = "level",
            lifecycleMetadata = MetricLifecycleMetadata(),
            provenance = listOf("test")
        )

    private fun conflictProvenance(metric: TrendMetricId, week: LocalDate): ObservationConflictProvenance =
        ObservationConflictProvenance(
            candidates = listOf(
                ObservationCandidateProvenance(metric, week, 1.0, null, "a", ObservationRevision(), null),
                ObservationCandidateProvenance(metric, week, 2.0, null, "b", ObservationRevision(), null)
            ),
            selectedCandidate = null,
            selectionRule = ObservationConflictSelectionRule.UNRESOLVED_CONFLICT,
            identicalCandidates = false,
            unresolvedConflict = true
        )
}

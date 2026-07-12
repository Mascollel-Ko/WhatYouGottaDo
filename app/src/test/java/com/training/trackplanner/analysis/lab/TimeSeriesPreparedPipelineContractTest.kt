package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.analysis.trends.TrendDataPoint
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

    @Test
    fun transformationPlanIsBuiltBeforeSelectionAndExcludesInconclusiveOptionalCandidates() {
        val service = TimeSeriesAlignmentService()
        val alignment = service.align(
            listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE),
            mapOf(
                TrendMetricId.BADMINTON_TRAINING to weeks(10).map { TrendDataPoint(it, 5.0) },
                TrendMetricId.FATIGUE_COMPOSITE to weeks(10).map { TrendDataPoint(it, 7.0) }
            )
        )!!

        val plan = service.transformationPlan(alignment, mandatoryMetrics = setOf(TrendMetricId.BADMINTON_TRAINING))
        val catalog = service.preparedCandidateCatalog(alignment, plan)!!

        assertEquals(SeriesTransformation.LEVEL, plan.plansByMetric.getValue(TrendMetricId.BADMINTON_TRAINING).transformation)
        assertEquals(SeriesTransformation.EXCLUDED, plan.plansByMetric.getValue(TrendMetricId.FATIGUE_COMPOSITE).transformation)
        assertTrue(TrendMetricId.FATIGUE_COMPOSITE !in catalog.preparedSeriesByMetric)
        assertEquals(plan.planFingerprint, service.transformationPlan(alignment, mandatoryMetrics = setOf(TrendMetricId.BADMINTON_TRAINING)).planFingerprint)
    }

    @Test
    fun transformedPreparedCatalogIsSharedBySelectionAndFinalPreparation() {
        val service = TimeSeriesAlignmentService()
        val metric = TrendMetricId.BADMINTON_TRAINING
        val alignment = service.align(
            listOf(metric),
            mapOf(metric to weeks(10).mapIndexed { index, week -> TrendDataPoint(week, (index * index).toDouble()) })
        )!!
        val diagnostic = IntegrationDiagnostic(metric, IntegrationOrder.I1, 0.0, 0.0, 0.0, 0.0, "fixture")
        val plan = TimeSeriesTransformationPlan(
            plansByMetric = mapOf(
                metric to MetricTransformationPlan(
                    metric = metric,
                    integrationOrder = IntegrationOrder.I1,
                    transformation = SeriesTransformation.FIRST_DIFFERENCE,
                    diagnosticSource = IntegrationDiagnosticSource.AUTOMATIC_INTEGRATION_DIAGNOSTIC,
                    decisionReason = "fixture"
                )
            ),
            diagnostics = mapOf(metric to diagnostic)
        )

        val catalog = service.preparedCandidateCatalog(alignment, plan)!!
        val selectorView = catalog.preparedSeriesByMetric.getValue(metric)
        val finalView = service.alignmentFromPrepared(listOf(metric), catalog.preparedSeriesByMetric)!!.preparedSeries.getValue(metric)

        assertEquals(selectorView.cells.map(TimeSeriesCell::value), finalView.cells.map(TimeSeriesCell::value))
        assertEquals("first difference", finalView.transformation)
        assertEquals(plan.planFingerprint, plan.copy().planFingerprint)
    }

    @Test
    fun roleAwareRowsDoNotRequireFutureTargetsForContemporaneousControls() {
        val weeks = weeks(12)
        val x = preparedSeries(TrendMetricId.BADMINTON_TRAINING, weeks) { it.toDouble() + 1.0 }
        val y = preparedSeries(TrendMetricId.FATIGUE_COMPOSITE, weeks) { it.toDouble() * 2.0 + 1.0 }
        val control = preparedSeries(TrendMetricId.STRENGTH_VOLUME, weeks) { index -> if (index >= 9) null else index.toDouble() + 3.0 }
        val prepared = listOf(x, y, control).associateBy(PreparedMetricSeries::metric)
        val defaultRows = PreparedTimeSeriesSystem.createValidated(
            orderedMetrics = prepared.keys.toList(),
            preparedSeries = prepared,
            lag = 1,
            horizon = 2
        ).commonUsableRows
        val roleRows = PreparedTimeSeriesSystem.createValidated(
            orderedMetrics = prepared.keys.toList(),
            preparedSeries = prepared,
            lag = 1,
            horizon = 2,
            rowRequirements = listOf(
                VariableRowRequirement(
                    metric = x.metric,
                    roles = setOf(TimeSeriesVariableRole.SHOCK_SOURCE, TimeSeriesVariableRole.ENDOGENOUS_STATE),
                    requireSourceValue = true,
                    requiredLagOffsets = setOf(1),
                    requiredTargetOffsets = emptySet(),
                    requireShockEstimationRows = true
                ),
                VariableRowRequirement(
                    metric = y.metric,
                    roles = setOf(TimeSeriesVariableRole.RESPONSE, TimeSeriesVariableRole.ENDOGENOUS_STATE),
                    requireSourceValue = true,
                    requiredLagOffsets = setOf(1),
                    requiredTargetOffsets = setOf(2)
                ),
                VariableRowRequirement(
                    metric = control.metric,
                    roles = setOf(TimeSeriesVariableRole.CONTEMPORANEOUS_CONTROL),
                    requireSourceValue = true,
                    requiredLagOffsets = emptySet(),
                    requiredTargetOffsets = emptySet()
                )
            )
        ).commonUsableRows

        assertTrue(weeks[7] !in defaultRows.map(PreparedTimeSeriesRowIdentity::sourceWeek))
        assertTrue(weeks[7] in roleRows.map(PreparedTimeSeriesRowIdentity::sourceWeek))
        assertTrue(roleRows.size > defaultRows.size)
    }

    @Test
    fun requestedHorizonAndRoleChangesAffectRowFingerprint() {
        val weeks = weeks(12)
        val first = preparedSeries(TrendMetricId.BADMINTON_TRAINING, weeks) { it.toDouble() }
        val second = preparedSeries(TrendMetricId.FATIGUE_COMPOSITE, weeks) { it.toDouble() * 2.0 }
        val prepared = mapOf(first.metric to first, second.metric to second)

        val horizonOne = PreparedTimeSeriesSystem.createValidated(prepared.keys.toList(), prepared, lag = 1, horizon = 1)
        val horizonFour = PreparedTimeSeriesSystem.createValidated(prepared.keys.toList(), prepared, lag = 1, horizon = 4)
        val sourceOnly = PreparedTimeSeriesSystem.createValidated(
            prepared.keys.toList(),
            prepared,
            lag = 1,
            horizon = 1,
            rowRequirements = prepared.keys.map { metric ->
                VariableRowRequirement(metric, setOf(TimeSeriesVariableRole.CONTEMPORANEOUS_CONTROL), true, emptySet(), emptySet())
            }
        )

        assertNotEquals(horizonOne.preparationFingerprint, horizonFour.preparationFingerprint)
        assertNotEquals(horizonOne.preparationFingerprint, sourceOnly.preparationFingerprint)
    }

    @Test
    fun dynamicCompatibilityScalingIgnoresValuesOutsideAllowedRows() {
        val weeks = weeks(40)
        fun alignment(outlier: Boolean): TimeSeriesAlignment =
            TimeSeriesAlignmentService().align(
                listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE),
                mapOf(
                    TrendMetricId.BADMINTON_TRAINING to weeks.mapIndexed { index, week ->
                        TrendDataPoint(week, if (outlier && index == weeks.lastIndex) 1_000_000.0 else kotlin.math.sin(index / 3.0) + index * 0.1)
                    },
                    TrendMetricId.FATIGUE_COMPOSITE to weeks.mapIndexed { index, week ->
                        TrendDataPoint(week, if (outlier && index == weeks.lastIndex) -1_000_000.0 else kotlin.math.cos(index / 4.0) + index * 0.05)
                    }
                )
            )!!
        val allowed = weeks.drop(3).take(30).toSet()
        val clean = BayesianVarEstimator().fitSystem(alignment(false), listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE), emptyList(), 1, false, allowed)!!
        val dirty = BayesianVarEstimator().fitSystem(alignment(true), listOf(TrendMetricId.BADMINTON_TRAINING, TrendMetricId.FATIGUE_COMPOSITE), emptyList(), 1, false, allowed)!!

        assertEquals(clean.observations, dirty.observations)
        assertEquals(clean.residualCovariance[0][0], dirty.residualCovariance[0][0], 1e-9)
        assertEquals(clean.residualCovariance[1][1], dirty.residualCovariance[1][1], 1e-9)
    }

    @Test
    fun preparedSeriesFactoryValidatesLifecycleCellSemantics() {
        val week = LocalDate.parse("2026-01-05")
        assertTrue(
            runCatching {
                PreparedMetricSeries.createValidated(
                    metric = TrendMetricId.BADMINTON_TRAINING,
                    weeks = listOf(week),
                    cells = listOf(TimeSeriesCell(TrendMetricId.BADMINTON_TRAINING, week, TimeSeriesCellState.STRUCTURAL_ZERO, 0.0)),
                    transformation = "level",
                    lifecycleMetadata = MetricLifecycleMetadata(structuralZeroAllowed = false),
                    provenance = listOf("test")
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                PreparedMetricSeries.createValidated(
                    metric = TrendMetricId.BADMINTON_TRAINING,
                    weeks = listOf(week),
                    cells = listOf(TimeSeriesCell(TrendMetricId.BADMINTON_TRAINING, week, TimeSeriesCellState.OBSERVED_VALUE, 1.0)),
                    transformation = "level",
                    lifecycleMetadata = MetricLifecycleMetadata(notApplicableWeeks = setOf(week)),
                    provenance = listOf("test")
                )
            }.isFailure
        )
        val first = MetricLifecycleMetadata(
            versionDiscontinuityRanges = listOf(
                TimeSeriesWeekRange(week.plusWeeks(2), week.plusWeeks(3)),
                TimeSeriesWeekRange(week, week)
            )
        )
        val second = MetricLifecycleMetadata(
            versionDiscontinuityRanges = listOf(
                TimeSeriesWeekRange(week, week),
                TimeSeriesWeekRange(week.plusWeeks(2), week.plusWeeks(3)),
                TimeSeriesWeekRange(week, week)
            )
        )
        assertEquals(first.fingerprint(), second.fingerprint())
        assertTrue(
            runCatching {
                MetricLifecycleMetadata(
                    versionDiscontinuityRanges = listOf(
                        TimeSeriesWeekRange(week, week.plusWeeks(2)),
                        TimeSeriesWeekRange(week.plusWeeks(1), week.plusWeeks(3))
                    )
                )
            }.isFailure
        )
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

package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictTimeSeriesEndToEndTest {
    @Test
    fun rawWeeklyDataProducesOneImmutablePreparationContextWithoutRunningEstimators() {
        val x = TrendMetricId.BADMINTON_TRAINING
        val y1 = TrendMetricId.FATIGUE_COMPOSITE
        val y2 = TrendMetricId.STRENGTH_PERFORMANCE
        val z = TrendMetricId.SLEEP_HOURS
        val optional = TrendMetricId.STRENGTH_VOLUME
        val weeks = weeks(36)
        val walk = (0 until weeks.size).runningFold(0.0) { total, index ->
            total + if (index % 2 == 0) 1.0 else -0.35
        }.dropLast(1)
        val lifecycle = StrictMetricLifecycle.createValidated(
            availableFromWeek = weeks[2],
            structuralZeroAllowed = true,
            notApplicableRanges = listOf(StrictWeekRange(weeks[4], weeks[4])),
            versionDiscontinuityRanges = listOf(StrictWeekRange(weeks[5], weeks[5])),
            provenance = "end-to-end fixture"
        )
        val required = buildList {
            weeks.forEachIndexed { index, week ->
                add(RawTimeSeriesObservation(x, week, if (index % 2 == 0) 1.0 else -1.0, sourceIndex = index))
                add(RawTimeSeriesObservation(y1, week, walk[index], sourceIndex = index))
                add(RawTimeSeriesObservation(y2, week, if (index % 2 == 0) 2.0 else -2.0, sourceIndex = index))
                add(RawTimeSeriesObservation(z, week, if (index % 2 == 0) 3.0 else -3.0, sourceIndex = index))
            }
        }
        val optionalObservations = buildList {
            add(RawTimeSeriesObservation(optional, weeks[2], 0.0, StrictCellState.STRUCTURAL_ZERO, sourceIndex = 2))
            add(RawTimeSeriesObservation(optional, weeks[3], null, StrictCellState.MISSING, sourceIndex = 3))
            (6 until weeks.size).forEach { index ->
                add(RawTimeSeriesObservation(optional, weeks[index], 7.0, source = "primary", sourceIndex = index))
            }
            add(RawTimeSeriesObservation(optional, weeks[20], 8.0, source = "conflict", sourceIndex = 200))
        }
        val request = StrictPreparationRequest(
            x,
            listOf(y1, y2),
            controls = listOf(z),
            optionalCandidates = listOf(optional),
            horizons = setOf(1, 2, 4)
        )

        val result = StrictTimeSeriesPreparationPipeline.prepare(
            RawTimeSeriesInput.createValidated(required + optionalObservations, mapOf(optional to lifecycle)),
            request
        )

        assertTrue(result is StrictPreparationResult.Success)
        val context = (result as StrictPreparationResult.Success).context
        val optionalSeries = context.validatedLevelSeriesByMetric.getValue(optional)
        assertEquals(weeks, context.canonicalCalendar.weeks)
        assertEquals(StrictCellState.PRE_METRIC_CREATION, optionalSeries.cells[0].state)
        assertEquals(StrictCellState.STRUCTURAL_ZERO, optionalSeries.cells[2].state)
        assertEquals(StrictCellState.MISSING, optionalSeries.cells[3].state)
        assertEquals(StrictCellState.NOT_APPLICABLE, optionalSeries.cells[4].state)
        assertEquals(StrictCellState.VERSION_DISCONTINUITY, optionalSeries.cells[5].state)
        assertEquals(StrictCellState.CONFLICT, optionalSeries.cells[20].state)
        assertNull(optionalSeries.cells[20].value)

        val segments = context.contiguousSegmentsByMetric.getValue(optional)
        assertEquals(listOf(1, 14, 15), segments.map { it.length })
        assertTrue(segments.none { segment -> weeks[3] in segment.weeks || weeks[4] in segment.weeks || weeks[5] in segment.weeks || weeks[20] in segment.weeks })
        val assessment = context.integrationAssessmentsByMetric.getValue(optional)
        assertEquals(IntegrationAssessmentStatus.INCONCLUSIVE, assessment.status)
        assertEquals(segments.map { it.fingerprint }, assessment.segmentDiagnostics.map { it.segment.fingerprint })
        assertTrue(optional in context.candidateCatalog.excludedCandidates)
        assertTrue(optional !in context.transformedSeriesByMetric)

        val y1Decision = context.canonicalTransformationPlan.decisionsByMetric.getValue(y1)
        assertEquals(CanonicalSeriesTransformation.FIRST_DIFFERENCE, y1Decision.transformation)
        assertEquals(context.validatedLevelSeriesByMetric.getValue(y1).fingerprint, context.transformedSeriesByMetric.getValue(y1).sourceLevelFingerprint)
        assertEquals(EstimatorSeriesRepresentation.VALIDATED_LEVEL, context.estimatorRepresentationPlan.decisionsByMetric.getValue(y1).johansenRepresentation)
        assertEquals(ResponseDisplayScale.CUMULATIVE_LEVEL_RESPONSE, context.responseScalePlansByMetric.getValue(y1).displayScale)
        assertEquals(ResponseDisplayScale.LEVEL_RESPONSE, context.responseScalePlansByMetric.getValue(y2).displayScale)

        val bvar = BvarPreparedView.from(context)
        val blp = BlpPreparedView.from(context)
        assertEquals(context.fingerprint, bvar.rootContextFingerprint)
        assertEquals(context.fingerprint, blp.rootContextFingerprint)
        val h1 = RowPlanner.plan(context, blp, 1, request.horizons, 1, HorizonPolicy.PER_HORIZON)
        val h4 = RowPlanner.plan(context, blp, 1, request.horizons, 4, HorizonPolicy.PER_HORIZON)
        val controlRequirement = h1.specification.requirements.single { it.metric == z }
        assertTrue(controlRequirement.requiredTargetOffsets.isEmpty())
        assertNotEquals(h1.fingerprint, h4.fingerprint)
        val bvarRows = RowPlanner.plan(context, bvar, 1, setOf(1), 1, HorizonPolicy.DECLARED_REFERENCE_HORIZON)
        val scaling = ScalingPlanner.plan(context, bvar, bvarRows, bvarRows.rows.take(12).map { it.sourceWeek })
        assertEquals(12, scaling.trainingRows.size)
        assertEquals(bvar.fingerprint, scaling.sourceViewFingerprint)
        assertTrue(result.readinessDiagnostics.any { "no Bayesian estimator has run" in it })
        assertTrue(context.fingerprint.isNotBlank())
        assertTrue(context.candidateCatalog.fingerprint.isNotBlank())
        assertTrue(h1.fingerprint.isNotBlank())
        assertTrue(scaling.fingerprint.isNotBlank())
    }

    private fun weeks(count: Int): List<LocalDate> =
        (0 until count).map { LocalDate.parse("2026-01-05").plusWeeks(it.toLong()) }
}

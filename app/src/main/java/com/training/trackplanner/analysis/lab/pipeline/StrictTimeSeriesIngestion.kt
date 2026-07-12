package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.lab.ObservationConflictProvenance
import com.training.trackplanner.analysis.lab.TimeSeriesAlignment
import com.training.trackplanner.analysis.lab.TimeSeriesAlignmentService
import com.training.trackplanner.analysis.lab.TimeSeriesCell
import com.training.trackplanner.analysis.lab.TimeSeriesCellState
import com.training.trackplanner.analysis.lab.TimeSeriesObservation
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal data class RawTimeSeriesObservation(
    val metric: TrendMetricId,
    val date: LocalDate,
    val value: Double?,
    val declaredState: StrictCellState? = null,
    val source: String = "raw",
    val sourceIndex: Int = 0,
    val authoritativeProvenance: List<StrictObservationProvenance> = emptyList()
) {
    init {
        if (value != null) require(value.isFinite())
        if (declaredState == StrictCellState.CONFLICT) require(authoritativeProvenance.size > 1) {
            "strict conflict observations require authoritative conflict provenance"
        }
    }
}

internal class RawTimeSeriesInput private constructor(
    private val observations: List<RawTimeSeriesObservation>,
    private val lifecycleByMetric: Map<TrendMetricId, StrictMetricLifecycle>
) {
    fun ingest(request: StrictPreparationRequest): LifecycleValidatedLevelCatalog {
        require(request.allMetrics.isNotEmpty())
        val requestedObservations = observations.filter { it.metric in request.allMetrics }
        val bounds = buildList {
            addAll(requestedObservations.map { canonicalWeek(it.date) })
            request.allMetrics.forEach { metric ->
                lifecycleByMetric[metric]?.availableFromWeek?.let(::add)
                lifecycleByMetric[metric]?.availableUntilWeek?.let(::add)
                lifecycleByMetric[metric]?.notApplicableRanges?.flatMap { listOf(it.startWeek, it.endWeek) }?.let(::addAll)
                lifecycleByMetric[metric]?.versionDiscontinuityRanges?.flatMap { listOf(it.startWeek, it.endWeek) }?.let(::addAll)
            }
        }
        require(bounds.isNotEmpty()) { "raw input has no calendar bounds" }
        val first = bounds.min()
        val last = bounds.max()
        val calendar = CanonicalCalendar.createValidated(generateSequence(first) { week ->
            week.plusWeeks(1).takeIf { !it.isAfter(last) }
        }.toList())
        val grouped = requestedObservations.groupBy { it.metric to canonicalWeek(it.date) }
        val series = request.allMetrics.sortedBy { it.name }.associateWith { metric ->
            val lifecycle = lifecycleByMetric[metric] ?: StrictMetricLifecycle.createValidated()
            val cells = calendar.weeks.map { week ->
                lifecycleCell(metric, week, lifecycle, grouped[metric to week].orEmpty())
            }
            LifecycleValidatedLevelSeries.createValidated(metric, calendar, cells, lifecycle)
        }
        return LifecycleValidatedLevelCatalog.createValidated(calendar, series)
    }

    companion object {
        fun fromTrendSeries(
            seriesByMetric: Map<TrendMetricId, List<TrendDataPoint>>,
            lifecycleByMetric: Map<TrendMetricId, StrictMetricLifecycle> = emptyMap()
        ): RawTimeSeriesInput {
            val observations = seriesByMetric.entries.flatMap { (metric, points) ->
                points.map { point -> TimeSeriesObservation(metric, point.weekStart, point.value, source = "TrendDataPoint") }
            }
            val alignment = TimeSeriesAlignmentService().alignObservations(seriesByMetric.keys, observations)
                ?: error("resolved time-series alignment is unavailable")
            return fromResolvedAlignment(alignment, lifecycleByMetric)
        }

        fun fromResolvedAlignment(
            alignment: TimeSeriesAlignment,
            lifecycleByMetric: Map<TrendMetricId, StrictMetricLifecycle> = emptyMap()
        ): RawTimeSeriesInput {
            val grid = alignment.grid ?: error("resolved alignment must carry a validated calendar grid")
            return createValidated(
                grid.cellsByMetric.values.flatten().mapIndexed { index, cell -> cell.toRawObservation(index) },
                lifecycleByMetric
            )
        }

        fun createValidated(
            observations: Collection<RawTimeSeriesObservation>,
            lifecycleByMetric: Map<TrendMetricId, StrictMetricLifecycle> = emptyMap()
        ): RawTimeSeriesInput {
            require(observations.isNotEmpty()) { "raw observations cannot be empty" }
            val items = observations.toList()
            val duplicateKeys = items.groupBy { it.metric to canonicalWeek(it.date) }.filterValues { it.size > 1 }.keys
            require(duplicateKeys.isEmpty()) {
                "strict ingestion requires one authoritative resolved observation per metric/week"
            }
            return RawTimeSeriesInput(items, lifecycleByMetric.toMap())
        }

        private fun lifecycleCell(
            metric: TrendMetricId,
            week: LocalDate,
            lifecycle: StrictMetricLifecycle,
            observations: List<RawTimeSeriesObservation>
        ): LifecycleValidatedCell {
            require(observations.size <= 1) { "strict ingestion boundary received unresolved duplicate observations" }
            val provenance = observations.flatMap { observation ->
                observation.authoritativeProvenance.ifEmpty {
                    listOf(StrictObservationProvenance(observation.source, observation.sourceIndex, observation.value, observation.declaredState))
                }
            }.sortedWith(
                compareBy<StrictObservationProvenance> { it.sourceIndex }
                    .thenBy { it.source }
                    .thenBy { it.value }
                    .thenBy { it.declaredState?.name }
            )
            if (lifecycle.notApplicableRanges.any { it.contains(week) }) {
                return LifecycleValidatedCell(metric, week, StrictCellState.NOT_APPLICABLE, null, provenance)
            }
            if (lifecycle.versionDiscontinuityRanges.any { it.contains(week) }) {
                return LifecycleValidatedCell(metric, week, StrictCellState.VERSION_DISCONTINUITY, null, provenance)
            }
            lifecycle.availableFromWeek?.let { activeFrom ->
                if (week.isBefore(activeFrom)) return LifecycleValidatedCell(metric, week, StrictCellState.PRE_METRIC_CREATION, null, provenance)
            }
            lifecycle.availableUntilWeek?.let { activeUntil ->
                if (week.isAfter(activeUntil)) return LifecycleValidatedCell(metric, week, StrictCellState.NOT_APPLICABLE, null, provenance)
            }
            if (observations.isEmpty()) return LifecycleValidatedCell(metric, week, StrictCellState.MISSING, null)
            val observation = observations.single()
            val state = observation.declaredState ?: if (observation.value == null) StrictCellState.MISSING else StrictCellState.OBSERVED_VALUE
            return LifecycleValidatedCell(metric, week, state, observation.value, provenance)
        }

        private fun TimeSeriesCell.toRawObservation(index: Int): RawTimeSeriesObservation =
            RawTimeSeriesObservation(
                metric = metric,
                date = weekStart,
                value = value,
                declaredState = state.toStrictState(),
                source = source ?: "TimeSeriesAlignmentService",
                sourceIndex = index,
                authoritativeProvenance = provenance(index)
            )

        private fun TimeSeriesCell.provenance(index: Int): List<StrictObservationProvenance> =
            conflictProvenance?.toStrictProvenance() ?: listOf(
                StrictObservationProvenance(
                    source ?: "TimeSeriesAlignmentService",
                    index,
                    value,
                    state.toStrictState()
                )
            )

        private fun ObservationConflictProvenance.toStrictProvenance(): List<StrictObservationProvenance> =
            candidates.sortedWith(
                compareBy({ it.source.orEmpty() }, { it.value ?: Double.NEGATIVE_INFINITY }, { it.state?.name.orEmpty() }, { it.version.orEmpty() })
            ).mapIndexed { index, candidate ->
                StrictObservationProvenance(
                    candidate.source ?: "TimeSeriesAlignmentService",
                    index,
                    candidate.value,
                    candidate.state?.toStrictState(),
                    selectionRule.name,
                    unresolvedConflict
                )
            }

        private fun TimeSeriesCellState.toStrictState(): StrictCellState = when (this) {
            TimeSeriesCellState.OBSERVED_VALUE -> StrictCellState.OBSERVED_VALUE
            TimeSeriesCellState.STRUCTURAL_ZERO -> StrictCellState.STRUCTURAL_ZERO
            TimeSeriesCellState.MISSING -> StrictCellState.MISSING
            TimeSeriesCellState.NOT_APPLICABLE -> StrictCellState.NOT_APPLICABLE
            TimeSeriesCellState.PRE_METRIC_CREATION -> StrictCellState.PRE_METRIC_CREATION
            TimeSeriesCellState.VERSION_DISCONTINUITY -> StrictCellState.VERSION_DISCONTINUITY
            TimeSeriesCellState.CONFLICT -> StrictCellState.CONFLICT
        }

        private fun canonicalWeek(date: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}

internal class LifecycleValidatedLevelCatalog private constructor(
    val calendar: CanonicalCalendar,
    seriesByMetric: Map<TrendMetricId, LifecycleValidatedLevelSeries>,
    val fingerprint: String
) {
    val seriesByMetric: Map<TrendMetricId, LifecycleValidatedLevelSeries> = seriesByMetric.toMap()

    companion object {
        fun createValidated(
            calendar: CanonicalCalendar,
            seriesByMetric: Map<TrendMetricId, LifecycleValidatedLevelSeries>
        ): LifecycleValidatedLevelCatalog {
            require(seriesByMetric.isNotEmpty())
            require(seriesByMetric.all { (metric, series) -> metric == series.metric && series.calendar.fingerprint == calendar.fingerprint })
            val fingerprint = strictFingerprint(
                listOf(calendar.fingerprint) + seriesByMetric.toSortedMap(compareBy { it.name }).values.map { it.fingerprint }
            )
            return LifecycleValidatedLevelCatalog(calendar, seriesByMetric, fingerprint)
        }
    }
}

internal object StrictTimeSeriesPreparationPipeline {
    fun prepare(
        rawInput: RawTimeSeriesInput,
        request: StrictPreparationRequest,
        policy: StrictPreparationPolicy = StrictPreparationPolicy.conservative()
    ): StrictPreparationResult = runCatching {
        PreparedAnalysisContext.createValidated(request, rawInput.ingest(request), policy)
    }.getOrElse { failure ->
        StrictPreparationResult.Failure(
            StrictPreparationFailureCode.PREPARED_CONTEXT_INCONSISTENT,
            listOf(failure.message ?: "strict preparation failed")
        )
    }

    fun prepareTrendSeries(
        seriesByMetric: Map<TrendMetricId, List<TrendDataPoint>>,
        request: StrictPreparationRequest,
        lifecycleByMetric: Map<TrendMetricId, StrictMetricLifecycle> = emptyMap(),
        policy: StrictPreparationPolicy = StrictPreparationPolicy.conservative()
    ): StrictPreparationResult = runCatching {
        RawTimeSeriesInput.fromTrendSeries(seriesByMetric, lifecycleByMetric)
    }.map { input -> prepare(input, request, policy) }
        .getOrElse { failure ->
            StrictPreparationResult.Failure(
                StrictPreparationFailureCode.INVALID_RAW_INPUT,
                listOf(failure.message ?: "strict raw input validation failed")
            )
        }
}

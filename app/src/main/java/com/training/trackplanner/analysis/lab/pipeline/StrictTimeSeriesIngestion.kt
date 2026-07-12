package com.training.trackplanner.analysis.lab.pipeline

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
    val sourceIndex: Int = 0
) {
    init {
        if (value != null) require(value.isFinite())
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
        ): RawTimeSeriesInput = createValidated(
            seriesByMetric.entries.flatMap { (metric, points) ->
                points.mapIndexed { index, point ->
                    RawTimeSeriesObservation(metric, point.weekStart, point.value, source = "TrendDataPoint", sourceIndex = index)
                }
            },
            lifecycleByMetric
        )

        fun createValidated(
            observations: Collection<RawTimeSeriesObservation>,
            lifecycleByMetric: Map<TrendMetricId, StrictMetricLifecycle> = emptyMap()
        ): RawTimeSeriesInput {
            require(observations.isNotEmpty()) { "raw observations cannot be empty" }
            return RawTimeSeriesInput(observations.toList(), lifecycleByMetric.toMap())
        }

        private fun lifecycleCell(
            metric: TrendMetricId,
            week: LocalDate,
            lifecycle: StrictMetricLifecycle,
            observations: List<RawTimeSeriesObservation>
        ): LifecycleValidatedCell {
            val provenance = observations.map { observation ->
                StrictObservationProvenance(observation.source, observation.sourceIndex, observation.value, observation.declaredState)
            }.sortedBy { it.sourceIndex }
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
                if (week.isAfter(activeUntil)) return LifecycleValidatedCell(metric, week, StrictCellState.MISSING, null, provenance)
            }
            if (observations.isEmpty()) return LifecycleValidatedCell(metric, week, StrictCellState.MISSING, null)
            val states = observations.map { observation ->
                observation.declaredState ?: if (observation.value == null) StrictCellState.MISSING else StrictCellState.OBSERVED_VALUE
            }
            val values = observations.map(RawTimeSeriesObservation::value)
            if (states.distinct().size > 1 || values.distinct().size > 1) {
                return LifecycleValidatedCell(metric, week, StrictCellState.CONFLICT, null, provenance)
            }
            return LifecycleValidatedCell(metric, week, states.first(), values.first(), provenance)
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

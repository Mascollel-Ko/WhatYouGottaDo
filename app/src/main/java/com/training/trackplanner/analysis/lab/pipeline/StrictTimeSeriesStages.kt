package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendMetricId
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate

internal enum class StrictCellState {
    OBSERVED_VALUE,
    STRUCTURAL_ZERO,
    MISSING,
    PRE_METRIC_CREATION,
    NOT_APPLICABLE,
    VERSION_DISCONTINUITY,
    CONFLICT
}

internal data class StrictWeekRange(
    val startWeek: LocalDate,
    val endWeek: LocalDate
) {
    init {
        require(startWeek.dayOfWeek == DayOfWeek.MONDAY && endWeek.dayOfWeek == DayOfWeek.MONDAY)
        require(!startWeek.isAfter(endWeek))
    }

    fun contains(week: LocalDate): Boolean = !week.isBefore(startWeek) && !week.isAfter(endWeek)
}

internal class StrictMetricLifecycle private constructor(
    val availableFromWeek: LocalDate?,
    val availableUntilWeek: LocalDate?,
    val structuralZeroAllowed: Boolean,
    val notApplicableRanges: List<StrictWeekRange>,
    val versionDiscontinuityRanges: List<StrictWeekRange>,
    val provenance: String,
    val fingerprint: String
) {
    companion object {
        fun createValidated(
            availableFromWeek: LocalDate? = null,
            availableUntilWeek: LocalDate? = null,
            structuralZeroAllowed: Boolean = false,
            notApplicableRanges: Collection<StrictWeekRange> = emptyList(),
            versionDiscontinuityRanges: Collection<StrictWeekRange> = emptyList(),
            provenance: String = "unspecified"
        ): StrictMetricLifecycle {
            availableFromWeek?.let { require(it.dayOfWeek == DayOfWeek.MONDAY) }
            availableUntilWeek?.let { require(it.dayOfWeek == DayOfWeek.MONDAY) }
            if (availableFromWeek != null && availableUntilWeek != null) {
                require(!availableFromWeek.isAfter(availableUntilWeek))
            }
            val notApplicable = normalizeRanges(notApplicableRanges)
            val discontinuities = normalizeRanges(versionDiscontinuityRanges)
            require(noOverlaps(notApplicable)) { "not-applicable ranges overlap" }
            require(noOverlaps(discontinuities)) { "version-discontinuity ranges overlap" }
            require(notApplicable.none { left -> discontinuities.any { right -> rangesOverlap(left, right) } }) {
                "not-applicable and version-discontinuity ranges overlap"
            }
            val parts = listOf(
                availableFromWeek?.toString().orEmpty(),
                availableUntilWeek?.toString().orEmpty(),
                structuralZeroAllowed.toString(),
                notApplicable.joinToString(",") { "${it.startWeek}:${it.endWeek}" },
                discontinuities.joinToString(",") { "${it.startWeek}:${it.endWeek}" },
                provenance
            )
            return StrictMetricLifecycle(
                availableFromWeek,
                availableUntilWeek,
                structuralZeroAllowed,
                notApplicable,
                discontinuities,
                provenance,
                strictFingerprint(parts)
            )
        }

        private fun normalizeRanges(ranges: Collection<StrictWeekRange>): List<StrictWeekRange> =
            ranges.distinct().sortedWith(compareBy<StrictWeekRange> { it.startWeek }.thenBy { it.endWeek })

        private fun noOverlaps(ranges: List<StrictWeekRange>): Boolean =
            ranges.zipWithNext().all { (left, right) -> left.endWeek.isBefore(right.startWeek) }

        private fun rangesOverlap(left: StrictWeekRange, right: StrictWeekRange): Boolean =
            !left.endWeek.isBefore(right.startWeek) && !right.endWeek.isBefore(left.startWeek)
    }
}

internal class CanonicalCalendar private constructor(
    weeks: List<LocalDate>,
    val fingerprint: String
) {
    val weeks: List<LocalDate> = weeks.toList()

    companion object {
        fun createValidated(weeks: Collection<LocalDate>): CanonicalCalendar {
            val ordered = weeks.toList()
            require(ordered.isNotEmpty()) { "canonical calendar cannot be empty" }
            require(ordered == ordered.sorted() && ordered.distinct().size == ordered.size)
            require(ordered.all { it.dayOfWeek == DayOfWeek.MONDAY })
            require(ordered.zipWithNext().all { (left, right) -> left.plusWeeks(1) == right })
            return CanonicalCalendar(ordered, strictFingerprint(ordered.map(LocalDate::toString)))
        }
    }
}

internal data class StrictObservationProvenance(
    val source: String,
    val sourceIndex: Int,
    val value: Double?,
    val declaredState: StrictCellState?
)

internal data class LifecycleValidatedCell(
    val metric: TrendMetricId,
    val week: LocalDate,
    val state: StrictCellState,
    val value: Double?,
    val provenance: List<StrictObservationProvenance> = emptyList()
) {
    init {
        require(week.dayOfWeek == DayOfWeek.MONDAY)
        when (state) {
            StrictCellState.OBSERVED_VALUE -> require(value?.isFinite() == true)
            StrictCellState.STRUCTURAL_ZERO -> require(value == 0.0)
            else -> require(value == null)
        }
        if (state == StrictCellState.CONFLICT) require(provenance.size > 1)
    }
}

internal class LifecycleValidatedLevelSeries private constructor(
    val metric: TrendMetricId,
    val calendar: CanonicalCalendar,
    cells: List<LifecycleValidatedCell>,
    val lifecycle: StrictMetricLifecycle,
    val fingerprint: String
) {
    val cells: List<LifecycleValidatedCell> = cells.toList()

    companion object {
        fun createValidated(
            metric: TrendMetricId,
            calendar: CanonicalCalendar,
            cells: List<LifecycleValidatedCell>,
            lifecycle: StrictMetricLifecycle
        ): LifecycleValidatedLevelSeries {
            require(cells.size == calendar.weeks.size)
            cells.forEachIndexed { index, cell ->
                require(cell.metric == metric && cell.week == calendar.weeks[index])
                validateLifecycle(cell, lifecycle)
            }
            val fingerprint = strictFingerprint(
                listOf(metric.name, calendar.fingerprint, lifecycle.fingerprint) + cells.map { cell ->
                    listOf(
                        cell.week,
                        cell.state,
                        cell.value,
                        cell.provenance.sortedBy { it.sourceIndex }.joinToString("|") {
                            "${it.source}:${it.sourceIndex}:${it.value}:${it.declaredState}"
                        }
                    ).joinToString(":")
                }
            )
            return LifecycleValidatedLevelSeries(metric, calendar, cells, lifecycle, fingerprint)
        }

        private fun validateLifecycle(cell: LifecycleValidatedCell, lifecycle: StrictMetricLifecycle) {
            val activeFrom = lifecycle.availableFromWeek
            val activeUntil = lifecycle.availableUntilWeek
            val inNotApplicable = lifecycle.notApplicableRanges.any { it.contains(cell.week) }
            val inDiscontinuity = lifecycle.versionDiscontinuityRanges.any { it.contains(cell.week) }
            when (cell.state) {
                StrictCellState.STRUCTURAL_ZERO -> {
                    require(lifecycle.structuralZeroAllowed)
                    require(activeFrom != null && !cell.week.isBefore(activeFrom))
                    require(activeUntil == null || !cell.week.isAfter(activeUntil))
                    require(!inNotApplicable && !inDiscontinuity)
                }
                StrictCellState.PRE_METRIC_CREATION -> require(activeFrom != null && cell.week.isBefore(activeFrom))
                StrictCellState.NOT_APPLICABLE -> require(inNotApplicable || (activeUntil != null && cell.week.isAfter(activeUntil)))
                StrictCellState.VERSION_DISCONTINUITY -> require(inDiscontinuity)
                StrictCellState.OBSERVED_VALUE -> {
                    require(activeFrom == null || !cell.week.isBefore(activeFrom))
                    require(activeUntil == null || !cell.week.isAfter(activeUntil))
                    require(!inNotApplicable && !inDiscontinuity)
                }
                StrictCellState.MISSING -> {
                    require(activeFrom == null || !cell.week.isBefore(activeFrom))
                    require(activeUntil == null || !cell.week.isAfter(activeUntil))
                    require(!inNotApplicable && !inDiscontinuity)
                }
                StrictCellState.CONFLICT -> {
                    require(activeFrom == null || !cell.week.isBefore(activeFrom))
                    require(activeUntil == null || !cell.week.isAfter(activeUntil))
                    require(!inNotApplicable && !inDiscontinuity)
                }
            }
        }
    }
}

internal data class StrictPreparationRequest(
    val xMetric: TrendMetricId,
    val yMetrics: List<TrendMetricId>,
    val controls: List<TrendMetricId> = emptyList(),
    val optionalCandidates: List<TrendMetricId> = emptyList(),
    val horizons: Set<Int> = setOf(1)
) {
    init {
        require(yMetrics.isNotEmpty())
        require(horizons.isNotEmpty() && horizons.all { it >= 0 })
        require(xMetric !in yMetrics)
    }

    val requiredMetrics: Set<TrendMetricId> = (listOf(xMetric) + yMetrics + controls).toSet()
    val allMetrics: Set<TrendMetricId> = (requiredMetrics + optionalCandidates).toSet()
}

internal enum class StrictPreparationFailureCode {
    INVALID_RAW_INPUT,
    INVALID_LIFECYCLE_METADATA,
    INSUFFICIENT_CONTIGUOUS_SAMPLE,
    INCONCLUSIVE_TRANSFORMATION,
    TRANSFORMATION_PLAN_INCOMPLETE,
    REPRESENTATION_PLAN_INCOMPLETE,
    RESPONSE_SCALE_PLAN_INCOMPLETE,
    PREPARED_CONTEXT_INCONSISTENT,
    ROW_PLAN_INCONSISTENT,
    SCALING_PLAN_INCONSISTENT
}

internal sealed interface StrictPreparationResult {
    data class Success(val context: PreparedAnalysisContext, val readinessDiagnostics: List<String>) : StrictPreparationResult
    data class Failure(
        val code: StrictPreparationFailureCode,
        val diagnostics: List<String>,
        val partialContext: PreparedAnalysisContext? = null
    ) : StrictPreparationResult
}

internal fun strictFingerprint(parts: Collection<Any?>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val canonical = parts.joinToString("\u001f") { it?.toString().orEmpty() }
    return digest.digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
}

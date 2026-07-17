package com.training.trackplanner.analysis.tissue

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class TissueCalibrationHistoryDate(
    val date: LocalDate,
    val weight: Double,
    val isUnitExposure: Boolean
)

data class TissueCalibrationHistorySegment(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val weight: Double,
    val dates: List<TissueCalibrationHistoryDate>
)

data class TissueCalibrationHistory(
    val anchorDate: LocalDate?,
    val personalHistoryCutoff: LocalDate?,
    val segments: List<TissueCalibrationHistorySegment>
) {
    val dates: List<TissueCalibrationHistoryDate> = segments.flatMap(TissueCalibrationHistorySegment::dates)
    val weightedValidObservationDays: Double = dates.sumOf(TissueCalibrationHistoryDate::weight)
    val weightedDistinctExposureDays: Double = dates.filter(TissueCalibrationHistoryDate::isUnitExposure)
        .sumOf(TissueCalibrationHistoryDate::weight)
}

object TissueCalibrationAnchorPolicy {
    fun latestConfirmationDate(
        confirmedWorkoutDates: Collection<LocalDate>,
        persistedCheckInDates: Collection<LocalDate>
    ): LocalDate? = (confirmedWorkoutDates.asSequence() + persistedCheckInDates.asSequence()).maxOrNull()
}

object TissueCalibrationHistoryPolicy {
    const val MAX_WEIGHTED_OBSERVATION_DAYS = 56.0

    fun build(
        anchorDate: LocalDate?,
        confirmedWorkoutDates: Collection<LocalDate>,
        unitExposureDates: Collection<LocalDate>,
        maxWeightedObservationDays: Double = MAX_WEIGHTED_OBSERVATION_DAYS
    ): TissueCalibrationHistory {
        require(maxWeightedObservationDays.isFinite() && maxWeightedObservationDays > 0.0)
        if (anchorDate == null) return TissueCalibrationHistory(null, null, emptyList())
        val cutoff = anchorDate.minusDays(7)
        val workouts = confirmedWorkoutDates.filter { !it.isAfter(anchorDate) }.toSortedSet()
        val exposures = unitExposureDates.filter { !it.isAfter(anchorDate) }.toSortedSet()
        val firstExposure = exposures.firstOrNull()
            ?: return TissueCalibrationHistory(anchorDate, cutoff, emptyList())
        if (firstExposure.isAfter(cutoff)) return TissueCalibrationHistory(anchorDate, cutoff, emptyList())

        val globalGaps = gaps(workouts, anchorDate, GapKind.GLOBAL)
        val unitGaps = gaps(exposures, anchorDate, GapKind.UNIT)
        val excludedRanges = (globalGaps + unitGaps).filter(CalendarGap::excluded)
        val retentionByOlderEdge = (globalGaps + unitGaps)
            .filter { it.excluded || it.retention < 1.0 }
            .groupBy(CalendarGap::olderSegmentEnd)
            .mapValues { (_, values) -> values.minOf(CalendarGap::retention) }

        var cumulativeWeight = 1.0
        var accumulatedDays = 0.0
        val selectedNewestFirst = mutableListOf<TissueCalibrationHistoryDate>()
        var date = cutoff
        while (!date.isBefore(firstExposure) && accumulatedDays < maxWeightedObservationDays) {
            retentionByOlderEdge[date]?.let { cumulativeWeight *= it }
            if (cumulativeWeight <= 0.0) break
            if (excludedRanges.none { date in it.startDate..it.endDate }) {
                val weight = minOf(cumulativeWeight, maxWeightedObservationDays - accumulatedDays)
                if (weight > 0.0) {
                    selectedNewestFirst += TissueCalibrationHistoryDate(date, weight, date in exposures)
                    accumulatedDays += weight
                }
            }
            date = date.minusDays(1)
        }

        val ordered = selectedNewestFirst.asReversed()
        return TissueCalibrationHistory(anchorDate, cutoff, ordered.toSegments())
    }

    private fun gaps(
        activityDates: Collection<LocalDate>,
        anchorDate: LocalDate,
        kind: GapKind
    ): List<CalendarGap> {
        val ordered = activityDates.filter { !it.isAfter(anchorDate) }.distinct().sorted()
        if (ordered.isEmpty()) return emptyList()
        val result = mutableListOf<CalendarGap>()
        ordered.zipWithNext().forEach { (older, newer) ->
            gap(older, newer.minusDays(1), kind)?.let(result::add)
        }
        gap(ordered.last(), anchorDate, kind)?.let(result::add)
        return result
    }

    private fun gap(lastActiveDate: LocalDate, gapEndInclusive: LocalDate, kind: GapKind): CalendarGap? {
        val start = lastActiveDate.plusDays(1)
        if (start.isAfter(gapEndInclusive)) return null
        val length = ChronoUnit.DAYS.between(start, gapEndInclusive) + 1
        val excluded = when (kind) {
            GapKind.GLOBAL -> length >= 7
            GapKind.UNIT -> length >= 14
        }
        val retention = when (kind) {
            GapKind.GLOBAL -> when {
                length >= 28 -> 0.0
                length >= 14 -> 0.5
                else -> 1.0
            }
            GapKind.UNIT -> when {
                length >= 28 -> 0.0
                length >= 14 -> 0.5
                else -> 1.0
            }
        }
        return CalendarGap(start, gapEndInclusive, lastActiveDate, excluded, retention)
    }

    private fun List<TissueCalibrationHistoryDate>.toSegments(): List<TissueCalibrationHistorySegment> {
        if (isEmpty()) return emptyList()
        val segments = mutableListOf<MutableList<TissueCalibrationHistoryDate>>()
        forEach { current ->
            val active = segments.lastOrNull()
            val previous = active?.lastOrNull()
            if (previous == null || previous.date.plusDays(1) != current.date || previous.weight != current.weight) {
                segments += mutableListOf(current)
            } else {
                active += current
            }
        }
        return segments.map { dates ->
            TissueCalibrationHistorySegment(dates.first().date, dates.last().date, dates.first().weight, dates)
        }
    }

    private enum class GapKind { GLOBAL, UNIT }

    private data class CalendarGap(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val olderSegmentEnd: LocalDate,
        val excluded: Boolean,
        val retention: Double
    )
}

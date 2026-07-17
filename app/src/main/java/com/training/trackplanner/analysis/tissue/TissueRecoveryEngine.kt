package com.training.trackplanner.analysis.tissue

import com.training.trackplanner.data.WorkoutEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object TissuePerformedTimeResolver {
    fun resolve(entry: WorkoutEntry, zoneId: ZoneId): TissueEventTimeRange {
        entry.performedAt?.let { performedAt ->
            return TissueEventTimeRange(performedAt, performedAt, TissueTimestampPrecision.EXACT)
        }
        val date = runCatching { LocalDate.parse(entry.date) }.getOrNull()
            ?: return TissueEventTimeRange(
                null,
                null,
                TissueTimestampPrecision.MISSING,
                listOf("Workout date is not a valid ISO local date.")
            )
        return TissueEventTimeRange(
            earliestEpochMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            latestEpochMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1,
            precision = TissueTimestampPrecision.DATE_ONLY_RANGE,
            diagnostics = listOf("Legacy record has date-only timestamp precision.")
        )
    }
}

class TissueRecoveryCurveRepository(
    private val curves: Map<String, TissueRecoveryCurve>
) {
    fun value(curveId: String, elapsedHours: Double): Double {
        val curve = requireNotNull(curves[curveId]) { "Unknown recovery curve: $curveId" }
        return TissuePchipInterpolator.value(curve.knots, elapsedHours.coerceAtLeast(0.0))
            .coerceIn(0.0, 1.25)
    }

    fun range(curveId: String, fromHours: Double, toHours: Double): TissueResidualRange {
        require(fromHours <= toHours)
        val curve = requireNotNull(curves[curveId]) { "Unknown recovery curve: $curveId" }
        val points = buildList {
            add(fromHours)
            add(toHours)
            curve.knots.mapTo(this) { it.elapsedHours }
        }.filter { it in fromHours..toHours }
        val values = points.map { value(curveId, it) }
        return TissueResidualRange(values.minOrNull() ?: 0.0, values.maxOrNull() ?: 0.0)
    }
}

class TissueResidualCalculator(
    private val curveRepository: TissueRecoveryCurveRepository,
    private val zoneId: ZoneId
) {
    fun calculate(event: TissueExposureEvent, nowEpochMillis: Long): TissueEventResidual? {
        val earliest = event.performedTime.earliestEpochMillis ?: return null
        val latest = event.performedTime.latestEpochMillis ?: return null
        if (earliest > nowEpochMillis) return null

        val feasibleLatest = minOf(latest, nowEpochMillis)
        val minElapsed = hoursBetween(feasibleLatest, nowEpochMillis)
        val maxElapsed = hoursBetween(earliest, nowEpochMillis)
        val channelResiduals = event.curveIds.mapValues { (_, curveId) ->
            val range = curveRepository.range(curveId, minElapsed, maxElapsed)
            TissueResidualRange(
                lower = event.initialExposure * range.lower,
                upper = event.initialExposure * range.upper
            )
        }
        val modeled = channelResiduals.filterKeys {
            it != TissueRecoveryChannel.BIOLOGICAL_REMODELING_ACTIVITY
        }.values
        val current = TissueResidualRange(
            lower = modeled.maxOfOrNull(TissueResidualRange::lower) ?: 0.0,
            upper = modeled.maxOfOrNull(TissueResidualRange::upper) ?: 0.0
        )
        return TissueEventResidual(
            event = event,
            channelResiduals = channelResiduals,
            currentResidualRange = current,
            biologicalActivityRange = channelResiduals[TissueRecoveryChannel.BIOLOGICAL_REMODELING_ACTIVITY],
            diagnostics = event.performedTime.diagnostics
        )
    }

    private fun hoursBetween(fromEpochMillis: Long, toEpochMillis: Long): Double =
        ChronoUnit.MILLIS.between(
            Instant.ofEpochMilli(fromEpochMillis).atZone(zoneId),
            Instant.ofEpochMilli(toEpochMillis).atZone(zoneId)
        ) / 3_600_000.0
}

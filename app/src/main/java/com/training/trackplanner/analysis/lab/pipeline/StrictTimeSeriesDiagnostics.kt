package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import kotlin.math.sqrt

internal enum class IntegrationAssessmentStatus {
    CONFIRMED_I0,
    CONFIRMED_I1,
    INCONCLUSIVE,
    UNSUPPORTED,
    INSUFFICIENT_CONTIGUOUS_SAMPLE
}

internal enum class IntegrationConfidenceStatus {
    CONFIRMED,
    INCONCLUSIVE
}

internal enum class SegmentIntegrationDecision {
    I0,
    I1,
    UNSUPPORTED
}

internal class ContiguousUsableSegment private constructor(
    val metric: TrendMetricId,
    weeks: List<LocalDate>,
    values: List<Double>,
    val fingerprint: String
) {
    val weeks: List<LocalDate> = weeks.toList()
    val values: List<Double> = values.toList()
    val startWeek: LocalDate = this.weeks.first()
    val endWeek: LocalDate = this.weeks.last()
    val length: Int = this.weeks.size

    companion object {
        fun createValidated(
            metric: TrendMetricId,
            weeks: List<LocalDate>,
            values: List<Double>
        ): ContiguousUsableSegment {
            require(weeks.isNotEmpty() && weeks.size == values.size)
            require(weeks.zipWithNext().all { (left, right) -> left.plusWeeks(1) == right })
            require(values.all(Double::isFinite))
            return ContiguousUsableSegment(
                metric,
                weeks,
                values,
                strictFingerprint(listOf(metric.name) + weeks.zip(values).map { (week, value) -> "$week:$value" })
            )
        }
    }
}

internal class SegmentIntegrationDiagnostic private constructor(
    val segment: ContiguousUsableSegment,
    val eligible: Boolean,
    val decision: SegmentIntegrationDecision?,
    val adfLevelStatistic: Double?,
    val kpssLevelStatistic: Double?,
    val adfDifferenceStatistic: Double?,
    val kpssDifferenceStatistic: Double?,
    val exclusionReason: String?,
    val diagnosticMethod: String,
    val diagnosticVersion: String,
    val fingerprint: String
) {
    companion object {
        fun excluded(segment: ContiguousUsableSegment, reason: String): SegmentIntegrationDiagnostic =
            SegmentIntegrationDiagnostic(
                segment,
                false,
                null,
                null,
                null,
                null,
                null,
                reason,
                DIAGNOSTIC_METHOD,
                DIAGNOSTIC_VERSION,
                strictFingerprint(listOf(segment.fingerprint, false, reason, DIAGNOSTIC_METHOD, DIAGNOSTIC_VERSION))
            )

        fun assessed(
            segment: ContiguousUsableSegment,
            decision: SegmentIntegrationDecision,
            level: StationarityStatistics,
            difference: StationarityStatistics
        ): SegmentIntegrationDiagnostic = SegmentIntegrationDiagnostic(
            segment,
            true,
            decision,
            level.adf,
            level.kpss,
            difference.adf,
            difference.kpss,
            null,
            DIAGNOSTIC_METHOD,
            DIAGNOSTIC_VERSION,
            strictFingerprint(
                listOf(
                    segment.fingerprint,
                    true,
                    decision.name,
                    level.adf,
                    level.kpss,
                    difference.adf,
                    difference.kpss,
                    DIAGNOSTIC_METHOD,
                    DIAGNOSTIC_VERSION
                )
            )
        )
    }
}

internal data class StationarityStatistics(
    val adf: Double,
    val kpss: Double,
    val stationary: Boolean
)

internal class IntegrationOrderAssessment private constructor(
    val metric: TrendMetricId,
    val status: IntegrationAssessmentStatus,
    val integrationOrder: Int?,
    segmentDiagnostics: List<SegmentIntegrationDiagnostic>,
    val diagnosticMethod: String,
    val diagnosticVersion: String,
    val confidenceStatus: IntegrationConfidenceStatus,
    val assessmentReason: String,
    val sourceLevelSeriesFingerprint: String,
    val fingerprint: String
) {
    val segmentDiagnostics: List<SegmentIntegrationDiagnostic> = segmentDiagnostics.toList()

    companion object {
        fun createValidated(
            metric: TrendMetricId,
            status: IntegrationAssessmentStatus,
            integrationOrder: Int?,
            segmentDiagnostics: List<SegmentIntegrationDiagnostic>,
            confidenceStatus: IntegrationConfidenceStatus,
            assessmentReason: String,
            sourceLevelSeriesFingerprint: String
        ): IntegrationOrderAssessment {
            require(segmentDiagnostics.all { it.segment.metric == metric })
            require((status == IntegrationAssessmentStatus.CONFIRMED_I0) == (integrationOrder == 0))
            require((status == IntegrationAssessmentStatus.CONFIRMED_I1) == (integrationOrder == 1))
            if (status !in setOf(IntegrationAssessmentStatus.CONFIRMED_I0, IntegrationAssessmentStatus.CONFIRMED_I1)) {
                require(integrationOrder == null)
            }
            val fingerprint = strictFingerprint(
                listOf(
                    metric.name,
                    status.name,
                    integrationOrder,
                    segmentDiagnostics.joinToString(",") { it.fingerprint },
                    DIAGNOSTIC_METHOD,
                    DIAGNOSTIC_VERSION,
                    confidenceStatus.name,
                    assessmentReason,
                    sourceLevelSeriesFingerprint
                )
            )
            return IntegrationOrderAssessment(
                metric,
                status,
                integrationOrder,
                segmentDiagnostics,
                DIAGNOSTIC_METHOD,
                DIAGNOSTIC_VERSION,
                confidenceStatus,
                assessmentReason,
                sourceLevelSeriesFingerprint,
                fingerprint
            )
        }
    }
}

internal object SegmentAwareIntegrationAssessmentAuthority {
    fun assess(catalog: LifecycleValidatedLevelCatalog): Map<TrendMetricId, IntegrationOrderAssessment> =
        catalog.seriesByMetric.toSortedMap(compareBy { it.name }).mapValues { (_, series) -> assess(series) }

    fun segments(series: LifecycleValidatedLevelSeries): List<ContiguousUsableSegment> {
        val segments = mutableListOf<ContiguousUsableSegment>()
        val weeks = mutableListOf<LocalDate>()
        val values = mutableListOf<Double>()
        fun flush() {
            if (weeks.isNotEmpty()) segments += ContiguousUsableSegment.createValidated(series.metric, weeks.toList(), values.toList())
            weeks.clear()
            values.clear()
        }
        series.cells.forEach { cell ->
            if (cell.state in USABLE_STATES && cell.value?.isFinite() == true) {
                weeks += cell.week
                values += cell.value
            } else {
                flush()
            }
        }
        flush()
        return segments
    }

    private fun assess(series: LifecycleValidatedLevelSeries): IntegrationOrderAssessment {
        val diagnostics = segments(series).map { segment ->
            if (segment.length < MIN_DIAGNOSTIC_SAMPLE) {
                SegmentIntegrationDiagnostic.excluded(segment, "segment has ${segment.length} rows; minimum is $MIN_DIAGNOSTIC_SAMPLE")
            } else {
                val level = stationarity(segment.values)
                val difference = stationarity(segment.values.zipWithNext { left, right -> right - left })
                val decision = when {
                    level.stationary -> SegmentIntegrationDecision.I0
                    difference.stationary -> SegmentIntegrationDecision.I1
                    else -> SegmentIntegrationDecision.UNSUPPORTED
                }
                SegmentIntegrationDiagnostic.assessed(segment, decision, level, difference)
            }
        }
        val eligible = diagnostics.filter(SegmentIntegrationDiagnostic::eligible)
        val unresolvedDefect = series.cells.any { it.state == StrictCellState.CONFLICT }
        val decisions = eligible.mapNotNull(SegmentIntegrationDiagnostic::decision).toSet()
        val status = when {
            unresolvedDefect -> IntegrationAssessmentStatus.INCONCLUSIVE
            eligible.isEmpty() -> IntegrationAssessmentStatus.INSUFFICIENT_CONTIGUOUS_SAMPLE
            decisions == setOf(SegmentIntegrationDecision.I0) -> IntegrationAssessmentStatus.CONFIRMED_I0
            decisions == setOf(SegmentIntegrationDecision.I1) -> IntegrationAssessmentStatus.CONFIRMED_I1
            decisions == setOf(SegmentIntegrationDecision.UNSUPPORTED) -> IntegrationAssessmentStatus.UNSUPPORTED
            else -> IntegrationAssessmentStatus.INCONCLUSIVE
        }
        val order = when (status) {
            IntegrationAssessmentStatus.CONFIRMED_I0 -> 0
            IntegrationAssessmentStatus.CONFIRMED_I1 -> 1
            else -> null
        }
        val reason = when (status) {
            IntegrationAssessmentStatus.CONFIRMED_I0 -> "all eligible contiguous segments agree on I(0)"
            IntegrationAssessmentStatus.CONFIRMED_I1 -> "all eligible contiguous segments agree on I(1)"
            IntegrationAssessmentStatus.INSUFFICIENT_CONTIGUOUS_SAMPLE -> "no contiguous segment meets the minimum sample"
            IntegrationAssessmentStatus.UNSUPPORTED -> "all eligible segments remain non-stationary after first differencing"
            IntegrationAssessmentStatus.INCONCLUSIVE -> if (unresolvedDefect) {
                "unresolved preparation conflict"
            } else {
                "eligible contiguous-segment diagnostics disagree"
            }
        }
        return IntegrationOrderAssessment.createValidated(
            series.metric,
            status,
            order,
            diagnostics,
            if (order != null) IntegrationConfidenceStatus.CONFIRMED else IntegrationConfidenceStatus.INCONCLUSIVE,
            reason,
            series.fingerprint
        )
    }

    private fun stationarity(values: List<Double>): StationarityStatistics {
        if (values.size < MIN_DIAGNOSTIC_SAMPLE || variance(values) <= EPSILON) {
            return StationarityStatistics(0.0, Double.POSITIVE_INFINITY, false)
        }
        val changes = values.zipWithNext { previous, current -> current - previous }
        val lagged = values.dropLast(1)
        val meanX = lagged.average()
        val meanY = changes.average()
        val centeredX = lagged.map { it - meanX }
        val denominator = centeredX.sumOf { it * it }.coerceAtLeast(EPSILON)
        val rho = centeredX.indices.sumOf { index -> centeredX[index] * (changes[index] - meanY) } / denominator
        val residuals = centeredX.indices.map { index -> changes[index] - meanY - rho * centeredX[index] }
        val sigma2 = residuals.sumOf { it * it } / maxOf(1, changes.size - 2)
        val adf = rho / sqrt((sigma2 / denominator).coerceAtLeast(EPSILON))
        val centered = values.map { it - values.average() }
        val cumulative = centered.runningFold(0.0) { total, value -> total + value }.drop(1)
        val longRun = neweyWestVariance(centered).coerceAtLeast(EPSILON)
        val kpss = cumulative.sumOf { it * it } / (values.size * values.size * longRun)
        return StationarityStatistics(adf, kpss, adf < ADF_REJECT && kpss < KPSS_RETAIN)
    }

    private fun variance(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }

    private fun neweyWestVariance(values: List<Double>): Double {
        val lag = sqrt(values.size.toDouble()).toInt().coerceAtLeast(1)
        var result = values.sumOf { it * it } / values.size
        for (offset in 1..lag) {
            val covariance = values.drop(offset).indices.sumOf { index -> values[index + offset] * values[index] } / values.size
            result += 2.0 * (1.0 - offset.toDouble() / (lag + 1)) * covariance
        }
        return result
    }

    private val USABLE_STATES = setOf(StrictCellState.OBSERVED_VALUE, StrictCellState.STRUCTURAL_ZERO)
    private const val MIN_DIAGNOSTIC_SAMPLE = 8
    private const val EPSILON = 1e-9
    private const val ADF_REJECT = -2.86
    private const val KPSS_RETAIN = 0.463
}

internal const val DIAGNOSTIC_METHOD = "segment-aware-adf-kpss"
internal const val DIAGNOSTIC_VERSION = "phase-a-strict-v1"

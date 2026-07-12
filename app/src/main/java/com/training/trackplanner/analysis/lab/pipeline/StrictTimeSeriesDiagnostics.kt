package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.lab.StableLinearAlgebra
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import org.apache.commons.math3.distribution.NormalDistribution

internal enum class IntegrationAssessmentStatus {
    SUPPORTED_I0,
    SUPPORTED_I1,
    INCONCLUSIVE,
    UNSUPPORTED,
    INSUFFICIENT_CONTIGUOUS_SAMPLE
}

internal enum class IntegrationConfidenceStatus {
    SUPPORTED,
    INCONCLUSIVE
}

internal enum class SegmentIntegrationDecision {
    SUPPORTED_I0,
    SUPPORTED_I1,
    UNSUPPORTED,
    INCONCLUSIVE
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

internal data class AdfReferenceDiagnostic(
    val statistic: Double,
    val pValue: Double,
    val selectedLag: Int,
    val effectiveObservationCount: Int,
    val criticalValues: Map<String, Double>,
    val bestAic: Double,
    val deterministicTerm: String,
    val diagnosticVersion: String
)

internal data class KpssReferenceDiagnostic(
    val statistic: Double,
    val pValue: Double,
    val pValueBoundary: String?,
    val selectedLag: Int,
    val criticalValues: Map<String, Double>,
    val deterministicTerm: String,
    val diagnosticVersion: String
)

internal data class StationarityStatistics(
    val adfDiagnostic: AdfReferenceDiagnostic?,
    val kpssDiagnostic: KpssReferenceDiagnostic?,
    val inconclusiveReason: String?
) {
    val adf: Double? = adfDiagnostic?.statistic
    val kpss: Double? = kpssDiagnostic?.statistic
    val adfRejectsUnitRoot: Boolean = adfDiagnostic?.let { it.statistic < it.criticalValues.getValue("5%") } == true
    val kpssRejectsStationarity: Boolean = kpssDiagnostic?.let { it.statistic > it.criticalValues.getValue("5%") } == true
    val conclusive: Boolean = adfDiagnostic != null && kpssDiagnostic != null && inconclusiveReason == null
}

internal class SegmentIntegrationDiagnostic private constructor(
    val segment: ContiguousUsableSegment,
    val eligible: Boolean,
    val decision: SegmentIntegrationDecision?,
    val adfLevelStatistic: Double?,
    val kpssLevelStatistic: Double?,
    val adfDifferenceStatistic: Double?,
    val kpssDifferenceStatistic: Double?,
    val levelAdf: AdfReferenceDiagnostic?,
    val levelKpss: KpssReferenceDiagnostic?,
    val differenceAdf: AdfReferenceDiagnostic?,
    val differenceKpss: KpssReferenceDiagnostic?,
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
            level.adfDiagnostic,
            level.kpssDiagnostic,
            difference.adfDiagnostic,
            difference.kpssDiagnostic,
            listOfNotNull(level.inconclusiveReason, difference.inconclusiveReason).joinToString("; ").ifBlank { null },
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
                    level.adfDiagnostic?.selectedLag,
                    level.kpssDiagnostic?.selectedLag,
                    difference.adfDiagnostic?.selectedLag,
                    difference.kpssDiagnostic?.selectedLag,
                    DIAGNOSTIC_METHOD,
                    DIAGNOSTIC_VERSION
                )
            )
        )
    }
}

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
            require((status == IntegrationAssessmentStatus.SUPPORTED_I0) == (integrationOrder == 0))
            require((status == IntegrationAssessmentStatus.SUPPORTED_I1) == (integrationOrder == 1))
            if (status !in setOf(IntegrationAssessmentStatus.SUPPORTED_I0, IntegrationAssessmentStatus.SUPPORTED_I1)) {
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
            if (segment.length < MIN_INTEGRATION_SEGMENT_WEEKS) {
                SegmentIntegrationDiagnostic.excluded(
                    segment,
                    "segment has ${segment.length} rows; minimum is $MIN_INTEGRATION_SEGMENT_WEEKS"
                )
            } else {
                val level = stationarity(segment.values)
                val difference = stationarity(segment.values.zipWithNext { left, right -> right - left })
                SegmentIntegrationDiagnostic.assessed(segment, classify(level, difference), level, difference)
            }
        }
        val eligible = diagnostics.filter(SegmentIntegrationDiagnostic::eligible)
        val unresolvedDefect = series.cells.any { it.state == StrictCellState.CONFLICT }
        val decisions = eligible.mapNotNull(SegmentIntegrationDiagnostic::decision).toSet()
        val status = when {
            unresolvedDefect -> IntegrationAssessmentStatus.INCONCLUSIVE
            eligible.isEmpty() -> IntegrationAssessmentStatus.INSUFFICIENT_CONTIGUOUS_SAMPLE
            decisions == setOf(SegmentIntegrationDecision.SUPPORTED_I0) -> IntegrationAssessmentStatus.SUPPORTED_I0
            decisions == setOf(SegmentIntegrationDecision.SUPPORTED_I1) -> IntegrationAssessmentStatus.SUPPORTED_I1
            decisions == setOf(SegmentIntegrationDecision.UNSUPPORTED) -> IntegrationAssessmentStatus.UNSUPPORTED
            else -> IntegrationAssessmentStatus.INCONCLUSIVE
        }
        val order = when (status) {
            IntegrationAssessmentStatus.SUPPORTED_I0 -> 0
            IntegrationAssessmentStatus.SUPPORTED_I1 -> 1
            else -> null
        }
        val reason = when (status) {
            IntegrationAssessmentStatus.SUPPORTED_I0 -> "all eligible contiguous segments support I(0)"
            IntegrationAssessmentStatus.SUPPORTED_I1 -> "all eligible contiguous segments support I(1)"
            IntegrationAssessmentStatus.INSUFFICIENT_CONTIGUOUS_SAMPLE -> "no contiguous segment meets the 32-week minimum sample"
            IntegrationAssessmentStatus.UNSUPPORTED -> "all eligible segments remain unsupported after first differencing"
            IntegrationAssessmentStatus.INCONCLUSIVE -> if (unresolvedDefect) {
                "unresolved preparation conflict"
            } else {
                "eligible contiguous-segment diagnostics disagree or are inconclusive"
            }
        }
        return IntegrationOrderAssessment.createValidated(
            series.metric,
            status,
            order,
            diagnostics,
            if (order != null) IntegrationConfidenceStatus.SUPPORTED else IntegrationConfidenceStatus.INCONCLUSIVE,
            reason,
            series.fingerprint
        )
    }

    private fun classify(level: StationarityStatistics, difference: StationarityStatistics): SegmentIntegrationDecision {
        if (!level.conclusive) return SegmentIntegrationDecision.INCONCLUSIVE
        if (level.adfRejectsUnitRoot && !level.kpssRejectsStationarity) return SegmentIntegrationDecision.SUPPORTED_I0
        if (!level.adfRejectsUnitRoot && level.kpssRejectsStationarity) {
            if (!difference.conclusive) return SegmentIntegrationDecision.INCONCLUSIVE
            if (difference.adfRejectsUnitRoot && !difference.kpssRejectsStationarity) return SegmentIntegrationDecision.SUPPORTED_I1
            if (!difference.adfRejectsUnitRoot && difference.kpssRejectsStationarity) return SegmentIntegrationDecision.UNSUPPORTED
        }
        return SegmentIntegrationDecision.INCONCLUSIVE
    }

    private fun stationarity(values: List<Double>): StationarityStatistics {
        val adf = runCatching { adf(values) }
        val kpss = runCatching { kpss(values) }
        return StationarityStatistics(
            adf.getOrNull(),
            kpss.getOrNull(),
            listOfNotNull(adf.exceptionOrNull()?.message, kpss.exceptionOrNull()?.message).joinToString("; ").ifBlank { null }
        )
    }

    private fun adf(values: List<Double>): AdfReferenceDiagnostic {
        require(values.size >= 3 && values.all(Double::isFinite)) { "ADF input is not finite or long enough" }
        require(values.max() != values.min()) { "ADF input is constant" }
        val n = values.size
        val maxLag = minOf(
            ceil(12.0 * (n / 100.0).pow(0.25)).toInt(),
            n / 2 - ADF_DETERMINISTIC_COLUMNS - 1
        )
        require(maxLag >= 0) { "ADF maxlag is negative for sample size $n" }
        val commonFits = (0..maxLag).map { lag -> lag to adfRegression(values, maxLag, lag) }
        val (selectedLag, selectedCommonFit) = commonFits.minWith(compareBy<Pair<Int, StableLinearAlgebra.OrdinaryLeastSquaresResult>> { it.second.aic }.thenBy { it.first })
        val finalFit = adfRegression(values, selectedLag, selectedLag)
        val statistic = finalFit.tValues[0]
        require(statistic.isFinite()) { "ADF statistic is not finite" }
        val effectiveNobs = values.size - 1 - selectedLag
        return AdfReferenceDiagnostic(
            statistic = statistic,
            pValue = mackinnonPValue(statistic),
            selectedLag = selectedLag,
            effectiveObservationCount = effectiveNobs,
            criticalValues = mackinnonCriticalValues(effectiveNobs),
            bestAic = selectedCommonFit.aic,
            deterministicTerm = "c",
            diagnosticVersion = DIAGNOSTIC_VERSION
        )
    }

    private fun adfRegression(
        values: List<Double>,
        rowLag: Int,
        usedLag: Int
    ): StableLinearAlgebra.OrdinaryLeastSquaresResult {
        val differences = values.zipWithNext { previous, current -> current - previous }
        val rowCount = differences.size - rowLag
        require(rowCount > usedLag + ADF_DETERMINISTIC_COLUMNS + 1) { "ADF regression has too few rows" }
        val y = DoubleArray(rowCount) { row -> differences[rowLag + row] }
        val x = Array(rowCount) { row ->
            val diffIndex = rowLag + row
            DoubleArray(usedLag + 2) { column ->
                when {
                    column == 0 -> values[diffIndex]
                    column <= usedLag -> differences[diffIndex - column]
                    else -> 1.0
                }
            }
        }
        return StableLinearAlgebra.ordinaryLeastSquares(x, y)
    }

    private fun kpss(values: List<Double>): KpssReferenceDiagnostic {
        require(values.size >= 2 && values.all(Double::isFinite)) { "KPSS input is not finite or long enough" }
        require(values.max() != values.min()) { "KPSS input is constant" }
        val mean = values.average()
        val residuals = values.map { it - mean }
        val lag = kpssAutoLag(residuals)
        val sHat = kpssLongRunVariance(residuals, lag)
        require(sHat.isFinite() && sHat > 0.0) { "KPSS long-run variance is invalid" }
        var total = 0.0
        val cumulative = residuals.map {
            total += it
            total
        }
        val eta = cumulative.sumOf { it * it } / (values.size * values.size)
        val statistic = eta / sHat
        require(statistic.isFinite()) { "KPSS statistic is not finite" }
        val p = interpolate(statistic, KPSS_CRITICAL_VALUES, KPSS_P_VALUES)
        return KpssReferenceDiagnostic(
            statistic = statistic,
            pValue = p,
            pValueBoundary = when (p) {
                KPSS_P_VALUES.first() -> "GREATER_THAN_OR_EQUAL_0.10"
                KPSS_P_VALUES.last() -> "LESS_THAN_OR_EQUAL_0.01"
                else -> null
            },
            selectedLag = lag,
            criticalValues = mapOf("10%" to 0.347, "5%" to 0.463, "2.5%" to 0.574, "1%" to 0.739),
            deterministicTerm = "c",
            diagnosticVersion = DIAGNOSTIC_VERSION
        )
    }

    private fun kpssAutoLag(residuals: List<Double>): Int {
        val n = residuals.size
        val covLags = n.toDouble().pow(2.0 / 9.0).toInt()
        var s0 = residuals.sumOf { it * it } / n
        var s1 = 0.0
        for (lag in 1..covLags) {
            val product = (lag until n).sumOf { index -> residuals[index] * residuals[index - lag] } / (n / 2.0)
            s0 += product
            s1 += lag * product
        }
        require(s0.isFinite() && abs(s0) > NUMERIC_EPSILON) { "KPSS auto-lag denominator is invalid" }
        val sHat = s1 / s0
        val gammaHat = 1.1447 * (sHat * sHat).pow(1.0 / 3.0)
        return minOf((gammaHat * n.toDouble().pow(1.0 / 3.0)).toInt(), n - 1).coerceAtLeast(0)
    }

    private fun kpssLongRunVariance(residuals: List<Double>, lag: Int): Double {
        val n = residuals.size
        var result = residuals.sumOf { it * it }
        for (offset in 1..lag) {
            val product = (offset until n).sumOf { index -> residuals[index] * residuals[index - offset] }
            result += 2.0 * product * (1.0 - offset / (lag + 1.0))
        }
        return result / n
    }

    private fun mackinnonPValue(testStatistic: Double): Double {
        if (testStatistic > TAU_MAX_C) return 1.0
        if (testStatistic < TAU_MIN_C) return 0.0
        val coefficients = if (testStatistic <= TAU_STAR_C) TAU_C_SMALL_P else TAU_C_LARGE_P
        val z = coefficients.withIndex().sumOf { (power, coefficient) -> coefficient * testStatistic.pow(power) }
        return NORMAL.cumulativeProbability(z)
    }

    private fun mackinnonCriticalValues(nobs: Int): Map<String, Double> =
        mapOf(
            "1%" to polynomialAtInverseN(doubleArrayOf(-3.43035, -6.5393, -16.786, -79.433), nobs),
            "5%" to polynomialAtInverseN(doubleArrayOf(-2.86154, -2.8903, -4.234, -40.040), nobs),
            "10%" to polynomialAtInverseN(doubleArrayOf(-2.56677, -1.5384, -2.809, 0.0), nobs)
        )

    private fun polynomialAtInverseN(coefficients: DoubleArray, nobs: Int): Double {
        val x = 1.0 / nobs
        return coefficients.withIndex().sumOf { (power, coefficient) -> coefficient * x.pow(power) }
    }

    private fun interpolate(value: Double, xs: DoubleArray, ys: DoubleArray): Double {
        if (value <= xs.first()) return ys.first()
        if (value >= xs.last()) return ys.last()
        val right = xs.indexOfFirst { value <= it }
        val left = right - 1
        val ratio = (value - xs[left]) / (xs[right] - xs[left])
        return ys[left] + ratio * (ys[right] - ys[left])
    }

    private val USABLE_STATES = setOf(StrictCellState.OBSERVED_VALUE, StrictCellState.STRUCTURAL_ZERO)
    private val NORMAL = NormalDistribution()
    private val KPSS_CRITICAL_VALUES = doubleArrayOf(0.347, 0.463, 0.574, 0.739)
    private val KPSS_P_VALUES = doubleArrayOf(0.10, 0.05, 0.025, 0.01)
    private val TAU_C_SMALL_P = doubleArrayOf(2.1659, 1.4412, 0.038269)
    private val TAU_C_LARGE_P = doubleArrayOf(1.7339, 0.93202, -0.12745, -0.010368)
    private const val TAU_STAR_C = -1.61
    private const val TAU_MIN_C = -18.83
    private const val TAU_MAX_C = 2.74
    private const val ADF_DETERMINISTIC_COLUMNS = 1
    private const val NUMERIC_EPSILON = 1e-12
}

internal const val MIN_INTEGRATION_SEGMENT_WEEKS = 32
internal const val DIAGNOSTIC_METHOD = "statsmodels-adfuller-kpss-c"
internal const val DIAGNOSTIC_VERSION = "phase-a-statsmodels-0.14.6-strict-v1"

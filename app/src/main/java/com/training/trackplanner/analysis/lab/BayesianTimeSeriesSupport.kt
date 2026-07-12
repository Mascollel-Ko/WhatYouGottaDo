package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.sqrt

internal class TimeSeriesAlignmentService {
    fun align(
        metrics: Collection<TrendMetricId>,
        series: Map<TrendMetricId, List<TrendDataPoint>>,
        structuralZeroMetrics: Set<TrendMetricId> = emptySet()
    ): TimeSeriesAlignment? {
        val unique = metrics.distinct()
        if (unique.isEmpty()) return null
        val maps = unique.associateWith { metric ->
            series[metric].orEmpty().mapNotNull { point -> point.value?.let { point.weekStart to it } }.toMap()
        }
        val bounds = maps.values.flatMap { it.keys }.takeIf { it.isNotEmpty() } ?: return null
        val allWeeks = generateSequence(bounds.minOrNull()!!) { week ->
            week.plusWeeks(1).takeIf { it <= bounds.maxOrNull()!! }
        }.toList()
        val cellsByMetric = maps.mapValues { (metric, values) ->
            allWeeks.map { week ->
                val value = values[week]
                when {
                    value != null -> TimeSeriesCell(week, TimeSeriesCellState.OBSERVED_VALUE, value)
                    metric in structuralZeroMetrics -> TimeSeriesCell(week, TimeSeriesCellState.STRUCTURAL_ZERO, 0.0)
                    else -> TimeSeriesCell(week, TimeSeriesCellState.MISSING, null)
                }
            }
        }
        val grid = TimeSeriesCalendarGrid(allWeeks, cellsByMetric)
        val missingRates = cellsByMetric.mapValues { (_, cells) -> cells.count { it.state == TimeSeriesCellState.MISSING }.toDouble() / allWeeks.size }
        return TimeSeriesAlignment(
            weeks = allWeeks,
            valuesByMetric = cellsByMetric.mapValues { (_, cells) -> cells.map { it.value ?: Double.NaN } },
            excludedMetrics = emptyMap(),
            missingRates = missingRates,
            grid = grid
        )
    }

    fun usableCandidate(
        metric: TrendMetricId,
        requiredWeeks: Set<LocalDate>,
        series: Map<TrendMetricId, List<TrendDataPoint>>
    ): String? {
        val values = series[metric].orEmpty().mapNotNull { point -> point.value?.let { point.weekStart to it } }.toMap()
        val missingRate = if (requiredWeeks.isEmpty()) 1.0 else 1.0 - values.keys.intersect(requiredWeeks).size.toDouble() / requiredWeeks.size
        if (missingRate > MAX_MISSING_RATE) return "missing rate exceeds 25%"
        val present = values.filterKeys { it in requiredWeeks }.values
        if (present.size < MIN_OBSERVATIONS || variance(present) <= EPSILON) return "series has insufficient variation"
        return null
    }

    fun restrictToWeeks(
        alignment: TimeSeriesAlignment,
        weeks: List<LocalDate>
    ): TimeSeriesAlignment? {
        val indices = weeks.mapNotNull { week -> alignment.weeks.indexOf(week).takeIf { it >= 0 } }
        if (indices.size != weeks.size) return null
        return alignment.copy(
            weeks = weeks,
            valuesByMetric = alignment.valuesByMetric.mapValues { (_, values) -> indices.map(values::get) },
            grid = alignment.grid?.let { grid ->
                TimeSeriesCalendarGrid(
                    weeks,
                    grid.cellsByMetric.mapValues { (_, cells) -> indices.map(cells::get) }
                )
            }
        )
    }

    fun stationarize(
        alignment: TimeSeriesAlignment,
        diagnostics: List<IntegrationDiagnostic>
    ): Pair<TimeSeriesAlignment, Map<TrendMetricId, String>>? {
        val orders = diagnostics.associate { it.metric to it.levelOrder }
        val transformed = alignment.valuesByMetric.mapValues { (metric, values) ->
            values.indices.drop(1).map { index ->
                when (orders[metric]) {
                    IntegrationOrder.I1 -> alignment.exactDifference(metric, index) ?: Double.NaN
                    else -> values[index].takeIf(Double::isFinite) ?: Double.NaN
                }
            }
        }
        if (transformed.values.any { values -> values.count(Double::isFinite) < MIN_OBSERVATIONS }) return null
        val descriptions = alignment.valuesByMetric.keys.associateWith { metric ->
            if (orders[metric] == IntegrationOrder.I1) "first difference" else "level"
        }
        val weeks = alignment.weeks.drop(1)
        val cellsByMetric = transformed.mapValues { (_, values) ->
            values.mapIndexed { index, value ->
                TimeSeriesCell(
                    weekStart = weeks[index],
                    state = if (value.isFinite()) TimeSeriesCellState.OBSERVED_VALUE else TimeSeriesCellState.MISSING,
                    value = value.takeIf(Double::isFinite)
                )
            }
        }
        return alignment.copy(
            weeks = weeks,
            valuesByMetric = transformed,
            grid = TimeSeriesCalendarGrid(weeks, cellsByMetric)
        ) to descriptions
    }

    private companion object {
        const val MIN_OBSERVATIONS = 8
        const val MAX_MISSING_RATE = 0.25
        const val EPSILON = 1e-9
    }
}

internal class IntegrationOrderAnalyzer {
    fun diagnose(metric: TrendMetricId, values: List<Double>): IntegrationDiagnostic {
        val level = stationarity(values)
        val differences = values.zipWithNext { first, second -> second - first }
        val difference = stationarity(differences)
        val order = when {
            level.stationary -> IntegrationOrder.I0
            difference.stationary -> IntegrationOrder.I1
            differences.size >= 16 && difference.adfStatistic >= -0.5 && difference.kpssStatistic > 1.0 -> IntegrationOrder.I2_OR_HIGHER
            else -> IntegrationOrder.INCONCLUSIVE
        }
        return IntegrationDiagnostic(
            metric = metric,
            levelOrder = order,
            adfLevelStatistic = level.adfStatistic,
            kpssLevelStatistic = level.kpssStatistic,
            adfDifferenceStatistic = difference.adfStatistic,
            kpssDifferenceStatistic = difference.kpssStatistic,
            message = when (order) {
                IntegrationOrder.I0 -> "level-stationary by ADF and KPSS"
                IntegrationOrder.I1 -> "first difference is stationary by ADF and KPSS"
                IntegrationOrder.I2_OR_HIGHER -> "first difference is not stationary"
                IntegrationOrder.INCONCLUSIVE -> "ADF and KPSS are inconclusive"
            }
        )
    }

    private fun stationarity(values: List<Double>): Stationarity {
        if (values.size < 8 || variance(values) <= EPSILON) return Stationarity(0.0, Double.POSITIVE_INFINITY, false)
        val y = values.drop(1).zip(values.dropLast(1)).map { (current, previous) -> current - previous }
        val x = values.dropLast(1)
        val meanX = x.average()
        val meanY = y.average()
        val centeredX = x.map { it - meanX }
        val denominator = centeredX.sumOf { it * it }.coerceAtLeast(EPSILON)
        val rho = centeredX.indices.sumOf { index -> centeredX[index] * (y[index] - meanY) } / denominator
        val residuals = centeredX.indices.map { index -> y[index] - meanY - rho * centeredX[index] }
        val sigma2 = residuals.sumOf { it * it } / maxOf(1, y.size - 2)
        val adf = rho / sqrt((sigma2 / denominator).coerceAtLeast(EPSILON))
        val centered = values.map { it - values.average() }
        val cumulative = centered.runningFold(0.0) { total, value -> total + value }.drop(1)
        val longRun = neweyWestVariance(centered).coerceAtLeast(EPSILON)
        val kpss = cumulative.sumOf { it * it } / (values.size * values.size * longRun)
        return Stationarity(adf, kpss, adf < ADF_REJECT && kpss < KPSS_RETAIN)
    }

    private fun neweyWestVariance(values: List<Double>): Double {
        val lag = sqrt(values.size.toDouble()).toInt().coerceAtLeast(1)
        var variance = values.sumOf { it * it } / values.size
        for (offset in 1..lag) {
            val covariance = values.drop(offset).indices.sumOf { index -> values[index + offset] * values[index] } / values.size
            variance += 2.0 * (1.0 - offset.toDouble() / (lag + 1)) * covariance
        }
        return variance
    }

    private data class Stationarity(val adfStatistic: Double, val kpssStatistic: Double, val stationary: Boolean)

    private companion object {
        const val EPSILON = 1e-9
        const val ADF_REJECT = -2.86
        const val KPSS_RETAIN = 0.463
    }
}

internal class BayesianLinearRegression private constructor() {
    data class Posterior(
        val mean: DoubleArray,
        val covariance: Array<DoubleArray>,
        val residualVariance: Double,
        val logEvidence: Double
    )

    companion object {
        fun fit(x: List<DoubleArray>, y: DoubleArray, priorPrecision: DoubleArray): Posterior? {
            val columns = x.firstOrNull()?.size ?: return null
            if (x.size != y.size || priorPrecision.size != columns) return null
            val xtx = Array(columns) { DoubleArray(columns) }
            val xty = DoubleArray(columns)
            x.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { i, xi ->
                    xty[i] += xi * y[rowIndex]
                    row.forEachIndexed { j, xj -> xtx[i][j] += xi * xj }
                }
            }
            priorPrecision.indices.forEach { index -> xtx[index][index] += priorPrecision[index] }
            val covarianceBase = runCatching {
                StableLinearAlgebra.solveSpd(xtx, identity(columns))
            }.getOrNull() ?: return null
            val mean = runCatching { StableLinearAlgebra.solveSpd(xtx, xty) }.getOrNull() ?: return null
            val residuals = x.indices.map { row -> y[row] - x[row].indices.sumOf { col -> x[row][col] * mean[col] } }
            val sigma2 = (residuals.sumOf { it * it } / maxOf(1, x.size - columns)).coerceAtLeast(1e-9)
            val covariance = Array(columns) { row -> DoubleArray(columns) { col -> covarianceBase[row][col] * sigma2 } }
            val logEvidence = -0.5 * (x.size * kotlin.math.ln(2.0 * Math.PI * sigma2) + residuals.sumOf { it * it } / sigma2 + StableLinearAlgebra.logDetSpd(xtx))
            return Posterior(mean, covariance, sigma2, logEvidence)
        }

        private fun identity(size: Int): Array<DoubleArray> =
            Array(size) { row -> DoubleArray(size) { column -> if (row == column) 1.0 else 0.0 } }
    }
}

internal fun TimeSeriesAlignment.valueAt(metric: TrendMetricId, index: Int): Double? =
    valuesByMetric[metric]?.getOrNull(index)?.takeIf(Double::isFinite)

internal fun TimeSeriesAlignment.exactLag(metric: TrendMetricId, index: Int, lagWeeks: Int): Double? {
    val sourceIndex = index - lagWeeks
    if (sourceIndex < 0 || weeks[sourceIndex].plusWeeks(lagWeeks.toLong()) != weeks[index]) return null
    return valueAt(metric, sourceIndex)
}

internal fun TimeSeriesAlignment.exactDifference(metric: TrendMetricId, index: Int): Double? {
    if (index <= 0 || weeks[index - 1].plusWeeks(1) != weeks[index]) return null
    val current = valueAt(metric, index) ?: return null
    val previous = valueAt(metric, index - 1) ?: return null
    return current - previous
}

internal fun TimeSeriesAlignment.exactHorizon(metric: TrendMetricId, index: Int, horizon: Int): Double? {
    val targetIndex = index + horizon
    if (targetIndex !in weeks.indices || weeks[index].plusWeeks(horizon.toLong()) != weeks[targetIndex]) return null
    return valueAt(metric, targetIndex)
}

internal fun TimeSeriesAlignment.buildExactRows(
    sourceMetric: TrendMetricId,
    targetMetric: TrendMetricId,
    lag: Int,
    horizon: Int
): Pair<List<TimeSeriesModelRow>, List<TimeSeriesRowExclusion>> {
    val rows = mutableListOf<TimeSeriesModelRow>()
    val exclusions = mutableListOf<TimeSeriesRowExclusion>()
    for (index in lag until weeks.size - horizon) {
        val target = exactHorizon(targetMetric, index, horizon)
        val source = valueAt(sourceMetric, index)
        val lags = (1..lag).map { offset -> exactLag(sourceMetric, index, offset) }
        val reason = when {
            index + horizon !in weeks.indices || weeks[index].plusWeeks(horizon.toLong()) != weeks[index + horizon] -> TimeSeriesRowExclusionReason.DISCONTINUOUS_HORIZON
            target == null -> TimeSeriesRowExclusionReason.MISSING_TARGET
            source == null -> TimeSeriesRowExclusionReason.MISSING_SOURCE
            lags.any { it == null } -> TimeSeriesRowExclusionReason.MISSING_LAG
            else -> null
        }
        if (reason == null) {
            rows += TimeSeriesModelRow(
                targetWeek = weeks[index + horizon],
                sourceWeek = weeks[index],
                lagWeeks = (1..lag).map { offset -> weeks[index - offset] },
                horizon = horizon,
                target = target!!,
                source = source!!,
                lags = lags.filterNotNull()
            )
        } else {
            exclusions += TimeSeriesRowExclusion(
                targetWeek = weeks.getOrElse((index + horizon).coerceAtMost(weeks.lastIndex)) { weeks[index] },
                sourceWeek = weeks[index],
                lagWeeks = (1..lag).mapNotNull { offset -> weeks.getOrNull(index - offset) },
                horizon = horizon,
                reason = reason,
                cellStates = listOf(sourceMetric, targetMetric).distinct().associateWith { metric ->
                    grid?.cell(metric, index)?.state ?: TimeSeriesCellState.MISSING
                }
            )
        }
    }
    return rows to exclusions
}

internal fun variance(values: Collection<Double>): Double {
    if (values.isEmpty()) return 0.0
    val mean = values.average()
    return values.sumOf { value -> (value - mean) * (value - mean) } / values.size
}

internal fun normalizeLogWeights(logWeights: Map<Int, Double>): Map<Int, Double> {
    val max = logWeights.values.maxOrNull() ?: return emptyMap()
    val weights = logWeights.mapValues { (_, value) -> exp(value - max) }
    val total = weights.values.sum().coerceAtLeast(1e-12)
    return weights.mapValues { (_, value) -> value / total }
}

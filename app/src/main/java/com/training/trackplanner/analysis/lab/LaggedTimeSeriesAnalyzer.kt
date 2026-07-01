package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import kotlin.math.sqrt

internal enum class LaggedCoefficientRole {
    INTERCEPT,
    MAIN_X,
    LAG_Y,
    LAG_X,
    CONTROL
}

internal fun laggedPriorPrecision(role: LaggedCoefficientRole): Double {
    val priorSd = when (role) {
        LaggedCoefficientRole.INTERCEPT -> 1000.0
        LaggedCoefficientRole.MAIN_X -> 0.75
        LaggedCoefficientRole.LAG_Y,
        LaggedCoefficientRole.LAG_X -> 0.50
        LaggedCoefficientRole.CONTROL -> 0.35
    }
    return 1.0 / (priorSd * priorSd)
}

data class LaggedEffectPoint(
    val horizonWeeks: Int,
    val estimate: Double,
    val low80: Double,
    val high80: Double,
    val observations: Int,
    val residualVariance: Double
) {
    val intervalWidth80: Double
        get() = high80 - low80
}

data class LaggedTimeSeriesResult(
    val xMetric: TrendMetricId,
    val yMetric: TrendMetricId,
    val controls: List<TrendMetricId>,
    val points: List<LaggedEffectPoint>,
    val observations: Int,
    val candidateWeeks: Int,
    val confidence: AnalysisConfidence,
    val warnings: List<String>,
    val summary: String,
    val automaticAdjustments: List<String> = listOf("Y 전주값", "X 전주값")
)

class LaggedTimeSeriesAnalyzer {
    fun analyze(
        xMetric: TrendMetricId,
        yMetric: TrendMetricId,
        controls: List<TrendMetricId>,
        metricSeries: Map<TrendMetricId, List<TrendDataPoint>>,
        horizons: IntRange = 1..4
    ): LaggedTimeSeriesResult {
        val warnings = mutableListOf("탐색적 시차 분석입니다. 인과관계로 단정하지 마세요.")
        if (xMetric == yMetric) {
            return unavailable(xMetric, yMetric, controls, "X와 Y는 서로 다른 지표를 선택하세요.", warnings)
        }
        val xByWeek = metricSeries[xMetric].orEmpty().toValueMap()
        val yByWeek = metricSeries[yMetric].orEmpty().toValueMap()
        val candidateWeeks = xByWeek.keys.intersect(yByWeek.keys).size
        if (xByWeek.size < MIN_WEEKS || yByWeek.size < MIN_WEEKS) {
            return unavailable(xMetric, yMetric, controls, "분석 가능한 주간 데이터가 부족합니다. 최소 8주 이상 필요합니다.", warnings, candidateWeeks)
        }
        val controlMaps = controls.associateWith { metric -> metricSeries[metric].orEmpty().toValueMap() }
        val points = horizons.mapNotNull { horizon ->
            horizonPoint(horizon, xByWeek, yByWeek, controlMaps, warnings)
        }
        val observations = points.maxOfOrNull { it.observations } ?: 0
        val confidence = when {
            observations >= 40 -> AnalysisConfidence.HIGH
            observations >= 24 -> AnalysisConfidence.MEDIUM
            observations >= 12 -> AnalysisConfidence.MEDIUM_LOW
            else -> AnalysisConfidence.LOW
        }
        val summary = when {
            points.isEmpty() -> "분석 가능한 주간 데이터가 부족합니다."
            points.any { it.low80 > 0.0 } -> "${label(xMetric)}가 높았던 주 이후 ${label(yMetric)}가 뒤따라 높아지는 구간이 보입니다."
            points.any { it.high80 < 0.0 } -> "${label(xMetric)}가 높았던 주 이후 ${label(yMetric)}가 뒤따라 낮아지는 구간이 보입니다."
            else -> "현재 기록만으로는 두 지표의 시차 관계가 뚜렷하지 않습니다."
        }
        if (controls.size * 4 > observations.coerceAtLeast(1)) {
            warnings += "선택한 통제변수가 관측 주 수에 비해 많습니다. 추정값이 불안정하거나 prior에 더 많이 의존할 수 있습니다."
        }
        return LaggedTimeSeriesResult(xMetric, yMetric, controls, points, observations, candidateWeeks, confidence, warnings.distinct(), summary)
    }

    private fun horizonPoint(
        horizon: Int,
        xByWeek: Map<LocalDate, Double>,
        yByWeek: Map<LocalDate, Double>,
        controlMaps: Map<TrendMetricId, Map<LocalDate, Double>>,
        warnings: MutableList<String>
    ): LaggedEffectPoint? {
        val rows = xByWeek.keys.sorted().mapNotNull { week ->
            val y = yByWeek[week.plusWeeks(horizon.toLong())] ?: return@mapNotNull null
            val x = xByWeek[week] ?: return@mapNotNull null
            val yLag = yByWeek[week.minusWeeks(1)] ?: return@mapNotNull null
            val xLag = xByWeek[week.minusWeeks(1)] ?: return@mapNotNull null
            val controls = controlMaps.values.map { it[week] ?: return@mapNotNull null }
            RegressionRow(y, listOf(x, yLag, xLag) + controls)
        }
        val coefficientCount = 1 + 3 + controlMaps.size
        if (rows.size < maxOf(MIN_WEEKS, coefficientCount + 3)) return null
        val standardized = standardize(rows) ?: run {
            warnings += "X 또는 Y의 변동이 거의 없어 분석하지 않았습니다."
            return null
        }
        val beta = ridge(standardized.x, standardized.y) ?: return null
        val estimate = beta.mean.getOrNull(1) ?: return null
        val se = sqrt(beta.covarianceApprox.getOrNull(1)?.getOrNull(1)?.coerceAtLeast(0.0) ?: 0.0)
        return LaggedEffectPoint(
            horizonWeeks = horizon,
            estimate = estimate,
            low80 = estimate - Z80 * se,
            high80 = estimate + Z80 * se,
            observations = rows.size,
            residualVariance = beta.residualVariance
        )
    }

    private fun standardize(rows: List<RegressionRow>): StandardizedRows? {
        val yStats = stats(rows.map { it.y })
        if (yStats.sd <= EPSILON) return null
        val columnStats = rows.first().x.indices.map { index -> stats(rows.map { it.x[index] }) }
        if (columnStats.first().sd <= EPSILON) return null
        val x = rows.map { row ->
            listOf(1.0) + row.x.mapIndexed { index, value ->
                val stat = columnStats[index]
                if (stat.sd <= EPSILON) 0.0 else (value - stat.mean) / stat.sd
            }
        }
        val y = rows.map { (it.y - yStats.mean) / yStats.sd }
        return StandardizedRows(x, y)
    }

    private fun ridge(x: List<List<Double>>, y: List<Double>): RegressionFit? {
        val p = x.firstOrNull()?.size ?: return null
        val xtx = Array(p) { DoubleArray(p) }
        val xty = DoubleArray(p)
        x.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { i, xi ->
                xty[i] += xi * y[rowIndex]
                row.forEachIndexed { j, xj -> xtx[i][j] += xi * xj }
            }
        }
        repeat(p) { index -> xtx[index][index] += laggedPriorPrecision(roleFor(index)) }
        val inverse = invert(xtx) ?: return null
        val mean = DoubleArray(p) { i -> (0 until p).sumOf { j -> inverse[i][j] * xty[j] } }
        val residuals = x.mapIndexed { index, row -> y[index] - row.indices.sumOf { col -> row[col] * mean[col] } }
        val df = maxOf(1, x.size - p)
        // ponytail: simple ridge posterior covariance approximation; replace with effective df only if needed.
        val sigma2 = maxOf(EPSILON, residuals.sumOf { it * it } / df)
        val covariance = inverse.map { row -> row.map { it * sigma2 } }
        return RegressionFit(mean.toList(), covariance, sigma2)
    }

    private fun roleFor(index: Int): LaggedCoefficientRole = when (index) {
        0 -> LaggedCoefficientRole.INTERCEPT
        1 -> LaggedCoefficientRole.MAIN_X
        2 -> LaggedCoefficientRole.LAG_Y
        3 -> LaggedCoefficientRole.LAG_X
        else -> LaggedCoefficientRole.CONTROL
    }

    private fun invert(matrix: Array<DoubleArray>): List<DoubleArray>? {
        val n = matrix.size
        val a = Array(n) { row -> DoubleArray(n * 2) { col -> if (col < n) matrix[row][col] else if (col - n == row) 1.0 else 0.0 } }
        for (pivot in 0 until n) {
            val best = (pivot until n).maxBy { row -> kotlin.math.abs(a[row][pivot]) }
            if (kotlin.math.abs(a[best][pivot]) <= EPSILON) return null
            val tmp = a[pivot]
            a[pivot] = a[best]
            a[best] = tmp
            val divisor = a[pivot][pivot]
            for (col in 0 until n * 2) a[pivot][col] /= divisor
            for (row in 0 until n) {
                if (row == pivot) continue
                val factor = a[row][pivot]
                for (col in 0 until n * 2) a[row][col] -= factor * a[pivot][col]
            }
        }
        return List(n) { row -> a[row].copyOfRange(n, n * 2) }
    }

    private fun List<TrendDataPoint>.toValueMap(): Map<LocalDate, Double> =
        mapNotNull { point -> point.value?.let { point.weekStart to it } }.toMap()

    private fun stats(values: List<Double>): Stats {
        val mean = values.average()
        val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        return Stats(mean, sd)
    }

    private fun unavailable(
        xMetric: TrendMetricId,
        yMetric: TrendMetricId,
        controls: List<TrendMetricId>,
        message: String,
        warnings: List<String>,
        candidateWeeks: Int = 0
    ) = LaggedTimeSeriesResult(xMetric, yMetric, controls, emptyList(), 0, candidateWeeks, AnalysisConfidence.LOW, (warnings + message).distinct(), message)

    private fun label(metric: TrendMetricId): String =
        AnalysisMetricRegistry.descriptor(metric)?.displayName ?: metric.name

    private data class RegressionRow(val y: Double, val x: List<Double>)
    private data class StandardizedRows(val x: List<List<Double>>, val y: List<Double>)
    private data class RegressionFit(
        val mean: List<Double>,
        val covarianceApprox: List<List<Double>>,
        val residualVariance: Double
    )
    private data class Stats(val mean: Double, val sd: Double)

    private companion object {
        private const val MIN_WEEKS = 8
        private const val EPSILON = 1e-9
        private const val Z80 = 1.28155
    }
}

package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.proxyperformance.MajorLiftProxySummary
import com.training.trackplanner.analysis.proxyperformance.MajorLiftTarget
import com.training.trackplanner.analysis.proxyperformance.ProxyContributionDirection
import com.training.trackplanner.analysis.proxyperformance.ProxyModelBacktest
import com.training.trackplanner.analysis.proxyperformance.ProxyModelVariant
import com.training.trackplanner.analysis.proxyperformance.ProxyPerformanceConfidence
import com.training.trackplanner.analysis.proxyperformance.ProxyPerformanceSummary
import com.training.trackplanner.analysis.proxyperformance.SessionExpectationComparison
import com.training.trackplanner.analysis.trends.AnalysisChartTemporalPolicy
import com.training.trackplanner.analysis.trends.ChartSeries
import com.training.trackplanner.analysis.trends.ChartSpec
import com.training.trackplanner.analysis.trends.ChartTimeGranularity
import com.training.trackplanner.analysis.trends.ChartType
import com.training.trackplanner.analysis.trends.IntervalBand
import com.training.trackplanner.analysis.trends.IntervalPoint
import com.training.trackplanner.analysis.trends.TrendChartRange
import com.training.trackplanner.analysis.trends.TrendDataPoint
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun ProxyPerformanceStrengthCards(summary: ProxyPerformanceSummary?) {
    if (summary == null) return
    var selectedName by rememberSaveable { mutableStateOf(MajorLiftTarget.BENCH_PRESS.name) }
    val selected = MajorLiftTarget.entries.firstOrNull { target -> target.name == selectedName }
        ?: MajorLiftTarget.BENCH_PRESS
    val targetSummary = summary.targets[selected] ?: return
    EstimatedMajorLiftCapabilityCard(selected, targetSummary) { target -> selectedName = target.name }
    ExpectedVersusActualCard(selected, targetSummary)
    ProxyEvidenceCard(selected, targetSummary)
}

@Composable
private fun EstimatedMajorLiftCapabilityCard(
    selected: MajorLiftTarget,
    summary: MajorLiftProxySummary,
    onSelected: (MajorLiftTarget) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("proxy-performance-capability-card"),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("추정 주요 운동 수행능력", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TargetSelector(selected, onSelected)
            val spec = proxyPosteriorChartSpec(selected, summary)
            if (spec.lineSeries.none { series -> series.points.any { point -> point.value != null } }) {
                InfoCard("직접 기록이 더 쌓이면 관련 운동 신호를 kg 범위로 추정할 수 있습니다.")
            } else {
                analysisChartPeriodLabel(spec)?.let { period ->
                    Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnalysisChartSpecView(spec)
                ChartSeriesLegend(spec.lineSeries)
            }
            Text(
                "${proxyModelLabel(summary.selectedModel)} · 신뢰도 ${confidenceLabel(summary.confidence)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "직접 ${summary.directObservationCount}회 · 프록시 ${summary.proxyObservationCount}회 · 관련 운동 ${summary.distinctProxyExerciseCount}개",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            summary.latestDirectObservationDate?.let { date ->
                Text("최근 직접 기록 $date", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "관련 운동 수행을 함께 반영한 추정 범위입니다. 실제 e1RM 기록을 대체하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExpectedVersusActualCard(target: MajorLiftTarget, summary: MajorLiftProxySummary) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("proxy-performance-expectation-card"),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${target.labelKo} 예상 대비 수행", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (summary.sessionComparisons.isEmpty()) {
                InfoCard("직접 수행 기록이 아직 없습니다.")
            } else {
                summary.sessionComparisons.take(3).forEach { comparison ->
                    SessionComparisonContent(comparison)
                }
            }
        }
    }
}

@Composable
private fun SessionComparisonContent(comparison: SessionExpectationComparison) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(comparison.date.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text("실제 e1RM ${kg(comparison.actualCanonicalE1rmKg)}")
        Text("노력도 보정 수행치 ${kg(comparison.effortAdjustedPerformanceKg)}")
        val expected = comparison.expectedMedianKg
        if (expected == null) {
            Text("세션 전 예상: 직접 기록 부족", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("직접 기록이 부족해 비교 신뢰도가 낮습니다.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("세션 전 예상 ${kg(expected)}")
            Text("80% 예상 범위 ${kg(comparison.expectedLow80Kg)}~${kg(comparison.expectedHigh80Kg)}")
            Text("예상 대비 ${signedKg(comparison.differenceKg)}")
            comparison.predictivePercentile?.let { percentile ->
                Text("예측분포 내 위치 ${percentileLabel(percentile)}", style = MaterialTheme.typography.labelMedium)
            }
            Text(expectationInterpretation(comparison), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProxyEvidenceCard(target: MajorLiftTarget, summary: MajorLiftProxySummary) {
    val recent = remember(summary.proxyContributions) {
        summary.proxyContributions.distinctBy { contribution -> contribution.exerciseStableKey }.take(5)
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("proxy-performance-evidence-card"),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("최근 ${target.labelKo} 추정 신호", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (recent.isEmpty()) {
                InfoCard("반영할 수 있는 관련 운동 변화가 아직 부족합니다.")
            } else {
                recent.forEach { contribution ->
                    Text(
                        "${contribution.exerciseName} · ${contributionDirectionLabel(contribution.direction)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        contribution.reasons.joinToString(" · ", transform = ::factorLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProxyPerformanceLabCard(summary: ProxyPerformanceSummary?) {
    if (summary == null) return
    var selectedName by rememberSaveable { mutableStateOf(MajorLiftTarget.BENCH_PRESS.name) }
    val selected = MajorLiftTarget.entries.firstOrNull { target -> target.name == selectedName }
        ?: MajorLiftTarget.BENCH_PRESS
    val targetSummary = summary.targets[selected] ?: return
    Card(
        modifier = Modifier.fillMaxWidth().testTag("proxy-performance-lab-card"),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("주요 운동 프록시 추정 실험", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "관련 운동의 수행 신호를 이용한 실험적 사후추정입니다. 실제 기록을 대체하지 않으며, 기존 Bayesian 시계열 분석과 별도입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TargetSelector(selected) { target -> selectedName = target.name }
            Text("선택 모델 ${proxyModelLabel(targetSummary.selectedModel)}", fontWeight = FontWeight.SemiBold)
            Text("모델 ${summary.modelVersion} · 지문 ${targetSummary.modelFingerprint.take(12)}", style = MaterialTheme.typography.labelSmall)
            Text(
                "직접 ${targetSummary.directObservationCount} · 프록시 ${targetSummary.proxyObservationCount} · 관련 운동 ${targetSummary.distinctProxyExerciseCount}",
                style = MaterialTheme.typography.labelSmall
            )
            AnalysisChartSpecView(proxyPosteriorChartSpec(selected, targetSummary))
            Text("롤링 사전검증", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            targetSummary.backtest.candidates.forEach { result -> BacktestResultRow(result) }
            Text(targetSummary.backtest.selectionReason, style = MaterialTheme.typography.bodySmall)
            Text("상위 프록시 loading", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            targetSummary.proxyContributions
                .distinctBy { contribution -> contribution.exerciseStableKey }
                .sortedByDescending { contribution -> contribution.loadingWeight }
                .take(5)
                .forEach { contribution ->
                Text(
                    "${contribution.exerciseName}: ${oneDecimal(contribution.loadingWeight)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text("최근 주요 innovation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            targetSummary.proxyContributions
                .take(20)
                .sortedByDescending { contribution -> abs(contribution.standardizedInnovation) }
                .take(5)
                .forEach { contribution ->
                    Text(
                        "${contribution.exerciseName}: ${oneDecimal(contribution.standardizedInnovation)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            summary.diagnostics.takeIf { diagnostics -> diagnostics.isNotEmpty() }?.let { diagnostics ->
                Text(
                    "수치 진단: ${diagnostics.groupingBy { diagnostic -> diagnostic.code }.eachCount().entries.joinToString { (code, count) -> "$code $count" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TargetSelector(selected: MajorLiftTarget, onSelected: (MajorLiftTarget) -> Unit) {
    AnalysisChipRow(
        labels = MajorLiftTarget.entries.map(MajorLiftTarget::labelKo),
        selected = MajorLiftTarget.entries.indexOf(selected),
        onSelect = { index -> onSelected(MajorLiftTarget.entries[index]) }
    )
}

@Composable
private fun BacktestResultRow(result: ProxyModelBacktest) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(result.variant.name.substringBefore('_'), style = MaterialTheme.typography.labelMedium)
        Text(
            "MAE ${result.maeKg?.let(::oneDecimal) ?: "-"} · RMSE ${result.rmseKg?.let(::oneDecimal) ?: "-"} · 80% ${result.intervalCoverage80?.let { value -> "${(value * 100).roundToInt()}%" } ?: "-"}",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

internal fun proxyPosteriorChartSpec(
    target: MajorLiftTarget,
    summary: MajorLiftProxySummary
): ChartSpec {
    val points = summary.weeklyPosterior
    val estimates = points.map { point -> TrendDataPoint(point.weekStart, point.posteriorMedianKg) }
    val actual = points.map { point -> TrendDataPoint(point.weekStart, point.actualCanonicalE1rmKg) }
    val proxyOnly = points.mapNotNull { point ->
        if (point.observationStatus != com.training.trackplanner.analysis.proxyperformance.ProxyObservationStatus.PROXY_ONLY) {
            return@mapNotNull null
        }
        TrendDataPoint(point.weekStart, point.posteriorMedianKg)
    }
    val interval = points.mapNotNull { point ->
        val low = point.posteriorLow80Kg ?: return@mapNotNull null
        val high = point.posteriorHigh80Kg ?: return@mapNotNull null
        IntervalPoint(point.weekStart, low, high)
    }
    val values = estimates.mapNotNull(TrendDataPoint::value) + actual.mapNotNull(TrendDataPoint::value) +
        interval.flatMap { point -> listOf(point.lower, point.upper) }
    val yRange = TrendChartRange.values(values)
    return ChartSpec(
        type = ChartType.LINE,
        title = "${target.labelKo} 실제와 추정",
        lineSeries = listOf(
            ChartSeries("추정 중앙값", estimates),
            ChartSeries("실제 e1RM", actual, connectPoints = false),
            ChartSeries("프록시만 반영된 주", proxyOnly, connectPoints = false)
        ),
        intervalBand = IntervalBand("80% 추정 범위", interval),
        yMin = yRange?.first,
        yMax = yRange?.second,
        timeGranularity = ChartTimeGranularity.WEEKLY,
        xDomain = AnalysisChartTemporalPolicy.weeklyDomain(points.map { point -> point.weekStart }),
        valueUnit = "kg"
    )
}

internal fun expectationInterpretation(comparison: SessionExpectationComparison): String {
    val actual = comparison.effortAdjustedPerformanceKg
    val low = comparison.expectedLow80Kg ?: return "직접 기록이 부족해 비교 신뢰도가 낮습니다."
    val high = comparison.expectedHigh80Kg ?: return "직접 기록이 부족해 비교 신뢰도가 낮습니다."
    return when {
        actual > high -> "예상 범위보다 높게 수행했습니다."
        actual < low -> "예상보다 낮게 수행했습니다."
        else -> "예상 범위 안에서 수행했습니다."
    }
}

private fun proxyModelLabel(variant: ProxyModelVariant): String = when (variant) {
    ProxyModelVariant.M0_LOCF -> "M0 · 직전값 기준"
    ProxyModelVariant.M1_TARGET_ONLY -> "M1 · 직접 기록"
    ProxyModelVariant.M2_SHARED_FACTORS -> "M2 · 공유 요인"
    ProxyModelVariant.M3_SHARED_PLUS_TARGET_SPECIFIC -> "M3 · 공유+종목 요인"
}

private fun confidenceLabel(confidence: ProxyPerformanceConfidence): String = when (confidence) {
    ProxyPerformanceConfidence.INSUFFICIENT -> "부족"
    ProxyPerformanceConfidence.LOW -> "낮음"
    ProxyPerformanceConfidence.MODERATE -> "보통"
    ProxyPerformanceConfidence.HIGH -> "높음"
}

private fun contributionDirectionLabel(direction: ProxyContributionDirection): String = when (direction) {
    ProxyContributionDirection.POSITIVE -> "양의 신호"
    ProxyContributionDirection.NEUTRAL -> "중립"
    ProxyContributionDirection.NEGATIVE -> "음의 신호"
}

private fun factorLabel(reason: String): String = when (reason) {
    "PRESS_SHARED" -> "공유 프레스"
    "HORIZONTAL_PRESS_SPECIFIC" -> "수평 프레스"
    "KNEE_EXTENSION" -> "무릎 신전"
    "HIP_EXTENSION_POSTERIOR_CHAIN" -> "고관절 신전"
    "TRUNK_BRACING" -> "몸통 고정"
    "BENCH_SPECIFIC" -> "벤치 특이성"
    "SQUAT_SPECIFIC" -> "스쿼트 특이성"
    "DEADLIFT_SPECIFIC" -> "데드리프트 특이성"
    else -> reason
}

private fun kg(value: Double?): String = value?.let { number -> "${oneDecimal(number)} kg" } ?: "-"

private fun signedKg(value: Double?): String = value?.let { number ->
    String.format(Locale.US, "%+.1f kg", number)
} ?: "-"

private fun percentileLabel(percentile: Double): String = when {
    percentile >= 0.5 -> "상위 ${((1.0 - percentile) * 100).roundToInt().coerceAtLeast(1)}%"
    else -> "하위 ${(percentile * 100).roundToInt().coerceAtLeast(1)}%"
}

private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

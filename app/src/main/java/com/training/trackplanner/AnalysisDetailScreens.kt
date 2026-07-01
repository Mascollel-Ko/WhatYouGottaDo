package com.training.trackplanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.coach.CoachAnalysisInsightSummary
import com.training.trackplanner.analysis.coach.CoachFatigueCauseSummary
import com.training.trackplanner.analysis.coach.CoachingSignalsSummary
import com.training.trackplanner.analysis.fatigue.ContributionGrouping
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisPeriod
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisUiState
import com.training.trackplanner.analysis.fatigue.FatigueTarget
import com.training.trackplanner.analysis.fatigue.ui.FatigueAnalysisSection
import com.training.trackplanner.analysis.lab.AnalysisMetricRegistry
import com.training.trackplanner.analysis.lab.MuscleBucketSelection
import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder
import com.training.trackplanner.analysis.readiness.PhaseAwareTodayStatus
import com.training.trackplanner.analysis.readiness.TodayFatigueStatusLabeler
import com.training.trackplanner.analysis.readiness.TodayReadinessSummary
import com.training.trackplanner.analysis.badminton.BadmintonTransferSummary
import com.training.trackplanner.analysis.trends.BadmintonTrainingMethodLabels
import com.training.trackplanner.analysis.trends.BadmintonTrainingMethodSeries
import com.training.trackplanner.analysis.trends.BarItem
import com.training.trackplanner.analysis.trends.ChartSeries
import com.training.trackplanner.analysis.trends.ChartSpec
import com.training.trackplanner.analysis.trends.ChartType
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import com.training.trackplanner.analysis.trends.RepRangeWeekShare
import com.training.trackplanner.analysis.trends.TrendChartRange
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.analysis.trends.label
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@Composable
internal fun FatigueAndConditionAnalysisContent(
    readiness: TodayReadinessSummary?,
    todayStatus: PhaseAwareTodayStatus?,
    fatigueAnalysis: FatigueAnalysisUiState,
    coachInsight: CoachAnalysisInsightSummary,
    coachingSignals: CoachingSignalsSummary,
    performanceTrend: PerformanceTrendSummary?,
    onPeriodChange: (FatigueAnalysisPeriod) -> Unit,
    onFatigueTargetToggle: (FatigueTarget) -> Unit,
    onContributionTargetChange: (FatigueTarget) -> Unit,
    onContributionGroupingChange: (ContributionGrouping) -> Unit,
    onContributionSourcesApply: (Set<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        todayStatus?.let { TodayReadinessCard(it) }
            ?: readiness?.let { TodayReadinessCard(it) }
            ?: InfoCard("오늘 상태를 계산하고 있습니다.")
        FatigueAxisCauseCard(
            readiness = todayStatus?.current ?: readiness,
            summary = coachInsight.fatigueCauses,
            combinedHeadline = coachInsight.combinedHeadline,
            checkInGuidance = coachInsight.checkInGuidance
        )
        CoachingSignalsCard(coachingSignals)
        FatigueAnalysisSection(
            state = fatigueAnalysis,
            onPeriodChange = onPeriodChange,
            onFatigueTargetToggle = onFatigueTargetToggle,
            onContributionTargetChange = onContributionTargetChange,
            onContributionGroupingChange = onContributionGroupingChange,
            onContributionSourcesApply = onContributionSourcesApply
        )
        performanceTrend?.let { summary ->
            AnalysisSectionChart(
                title = "피로도 종합지수(주별)",
                spec = ChartSpec(
                    type = ChartType.LINE,
                    title = "피로도 종합지수(주별)",
                    lineSeries = listOf(ChartSeries("피로도 종합지수", summary.fatigueCompositeSeries.dataPoints))
                ),
                note = "주 단위로 집계한 종합 피로 흐름입니다."
            )
        } ?: InfoCard("피로도 추세를 계산하고 있습니다.")
    }
}

@Composable
internal fun BadmintonTransferAnalysisContent(
    coachInsight: CoachAnalysisInsightSummary,
    badmintonTransfer: BadmintonTransferSummary?,
    performanceTrend: PerformanceTrendSummary?
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        performanceTrend?.let { BadmintonTransferObjectiveSentenceCard(it) }
            ?: InfoCard("배드민턴 전이 목적별 자극량을 계산하고 있습니다.")
        performanceTrend?.let { BadmintonTrainingLoadCharts(it) }
            ?: InfoCard("배드민턴 훈련량 추세를 계산하고 있습니다.")
    }
}

@Composable
internal fun StrengthTrendAnalysisContent(performanceTrend: PerformanceTrendSummary?) {
    if (performanceTrend == null) {
        InfoCard("근력운동 추이를 계산하고 있습니다.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MainLiftE1rmCard(performanceTrend)
        MuscleLoadShareCard(performanceTrend)
        MuscleLoadShareTrendCard(performanceTrend)
        RepRangeShareCard(performanceTrend.repRangeWeeks)
        RepRangeShareTrendCard(performanceTrend.repRangeWeeks)
    }
}

@Composable
private fun FatigueAxisCauseCard(
    readiness: TodayReadinessSummary?,
    summary: CoachFatigueCauseSummary,
    combinedHeadline: String?,
    checkInGuidance: List<String>
) {
    val axes = readiness?.let(TodayFatigueStatusLabeler::axisStates).orEmpty()
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("피로도별 현재 상태와 주요 기여 운동", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                combinedHeadline ?: summary.headline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            checkInGuidance.take(2).forEach { guidance ->
                Text(guidance, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            axes.forEach { axis ->
                val causes = summary.causes
                    .filter { cause -> axis.label in cause.affectedAxes }
                    .take(2)
                    .map { cause -> cause.label }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(axis.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(axis.displayLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            "주요 기여 운동: ${causes.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "최근 기여 운동 없음"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (axes.isEmpty()) {
                Text("현재 피로 축을 계산하고 있습니다.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BadmintonTransferObjectiveSentenceCard(summary: PerformanceTrendSummary) {
    val methodSummary = BadmintonTrainingMethodSeries.summary(summary.badmintonDailyLoads)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(methodSummary.sentence, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "아래 차트와 같은 배드민턴 전이 목적 기준으로 요약합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BadmintonTrainingLoadCharts(summary: PerformanceTrendSummary) {
    var mode by rememberSaveable { mutableStateOf(BadmintonLoadMode.TOTAL) }
    var showMethodDescription by rememberSaveable { mutableStateOf(false) }
    val methodTotals = BadmintonTrainingMethodSeries.totals(summary.badmintonDailyLoads)
    val methodKeys = methodTotals.keys.toList()
    var selectedMethod by rememberSaveable(methodTotals.keys.joinToString()) {
        mutableStateOf(methodTotals.maxByOrNull { it.value }?.key.orEmpty())
    }
    if (selectedMethod !in methodKeys) {
        selectedMethod = methodTotals.maxByOrNull { it.value }?.key.orEmpty()
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("배드민턴 관련 훈련량 선택", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AnalysisChipRow(
                    labels = BadmintonLoadMode.entries.map { it.label },
                    selected = BadmintonLoadMode.entries.indexOf(mode),
                    onSelect = { index -> mode = BadmintonLoadMode.entries[index] }
                )
                if (mode == BadmintonLoadMode.METHOD) {
                    if (methodTotals.isEmpty()) {
                        InfoCard("전이 목적 메타데이터가 있는 기록이 부족합니다.")
                    } else {
                        AnalysisChipRow(
                            labels = methodKeys.map(BadmintonTrainingMethodLabels::label),
                            selected = methodKeys.indexOf(selectedMethod).coerceAtLeast(0),
                            onSelect = { index -> selectedMethod = methodKeys[index] }
                        )
                    }
                }
            }
        }
        AnalysisSectionChart(
            title = "배드민턴 관련 훈련량(일별)",
            spec = ChartSpec(
                type = ChartType.LINE,
                title = "배드민턴 관련 훈련량(일별)",
                lineSeries = listOf(
                    ChartSeries(
                        badmintonSeriesLabel(mode, selectedMethod),
                        summary.badmintonDailyLoads.map { point ->
                            TrendDataPoint(point.date, point.valueFor(mode, selectedMethod))
                        }
                    )
                )
            ),
            note = "확인된 기록만 사용한 일별 관련 훈련량입니다."
        )
        AnalysisSectionChart(
            title = "배드민턴 관련 훈련량(주별)",
            spec = ChartSpec(
                type = ChartType.LINE,
                title = "배드민턴 관련 훈련량(주별)",
                lineSeries = listOf(ChartSeries(badmintonSeriesLabel(mode, selectedMethod), weeklyBadmintonPoints(summary, mode, selectedMethod)))
            ),
            note = "월별이 아니라 각 주마다 하나의 포인트를 찍는 주별 차트입니다."
        )
        val comparisonGroups = BadmintonTrainingMethodSeries.recentComparisonGroups(summary.badmintonDailyLoads)
        if (comparisonGroups.isNotEmpty()) {
            AnalysisSectionChart(
                title = "최근 7일 vs 28일 전이 목적 비교",
                spec = ChartSpec(
                    type = ChartType.STACKED_BAR,
                    title = "최근 7일 vs 28일 전이 목적 비교",
                    stackedBars = comparisonGroups
                ),
                note = "라켓 보조 같은 구 전이축이 아니라 풋워크, 가속, 감속, 리액션 등 전이 목적 기준으로 비교합니다."
            )
        }
        if (methodTotals.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "배드민턴 전이 목적별 자극량",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showMethodDescription = true }) {
                        Text("설명 보기")
                    }
                    AnalysisChartSpecView(
                        ChartSpec(
                            type = ChartType.HORIZONTAL_BAR,
                            title = "배드민턴 전이 목적별 자극량",
                            bars = methodTotals.entries
                                .sortedByDescending { it.value }
                                .map { (key, value) -> BarItem(BadmintonTrainingMethodLabels.label(key), value) }
                        )
                    )
                    Text(
                        "이 차트는 전체 대비 나눠 보는 표가 아니라 전이 목적별 자극량을 보여줍니다. 운동 하나가 여러 전이 목적에 동시에 해당할 수 있어 자극량은 중복 반영됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnalysisSectionChart(
                title = "주별 배드민턴 전이 자극량",
                spec = ChartSpec(
                    type = ChartType.STACKED_BAR,
                    title = "주별 배드민턴 전이 자극량",
                    stackedBars = BadmintonTrainingMethodSeries.weeklyStackedGroups(summary.badmintonDailyLoads)
                ),
                note = "각 주마다 어떤 배드민턴 전이 목적의 자극이 많았는지 보여줍니다. 운동 하나가 여러 전이 목적에 동시에 해당할 수 있어 자극량은 중복 반영됩니다. 월별이 아니라 주별 집계입니다."
            )
        }
        if (showMethodDescription) {
            BadmintonMethodDescriptionDialog(
                methodKeys = methodKeys,
                examples = summary.badmintonMethodExamples,
                onDismiss = { showMethodDescription = false }
            )
        }
    }
}

@Composable
private fun BadmintonMethodDescriptionDialog(
    methodKeys: List<String>,
    examples: Map<String, List<String>>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        title = { Text("배드민턴 전이 목적 설명") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                methodKeys.forEach { key ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(BadmintonTrainingMethodLabels.label(key), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(BadmintonTrainingMethodLabels.description(key), style = MaterialTheme.typography.bodySmall)
                        val sample = examples[key].orEmpty().take(2)
                        Text(
                            text = if (sample.isEmpty()) "예시 운동: 기록 없음" else "예시 운동: ${sample.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun MainLiftE1rmCard(summary: PerformanceTrendSummary) {
    AnalysisSectionChart(
        title = "주요 운동 e1RM",
        spec = metricLineSpec(
            title = "주요 운동 e1RM",
            summary = summary,
            metrics = listOf(
                TrendMetricId.BENCH_PRESS_E1RM,
                TrendMetricId.SQUAT_E1RM,
                TrendMetricId.DEADLIFT_E1RM
            )
        ),
        note = "확인된 실제 수행 세트만 사용하며, 기록 없는 주는 0으로 채우지 않습니다."
    )
}

@Composable
private fun MuscleLoadShareCard(summary: PerformanceTrendSummary) {
    AnalysisSectionChart(
        title = "근육군별 운동량 비율",
        spec = ChartSpec(
            type = ChartType.HORIZONTAL_BAR,
            title = "근육군별 운동량 비율",
            bars = latestMuscleShare(summary)
        ),
        note = "근육군별 운동량은 운동 기록과 메타데이터를 바탕으로 추정한 상대 지표입니다."
    )
}

@Composable
private fun MuscleLoadShareTrendCard(summary: PerformanceTrendSummary) {
    val buckets = StrengthAndMuscleMetricSeriesBuilder.MuscleBucket.values().toList()
    val available = buckets.filter { bucket -> summary.metricSeries[bucket.dailyMetric].orEmpty().any { it.value != null } }
    val defaults = MuscleBucketSelection.defaultMetrics(available, summary.metricSeries)
    val selected = remember { mutableStateListOf<TrendMetricId>().apply { addAll(defaults) } }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("근육군별 운동량 비율 추이", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (available.isEmpty()) {
                InfoCard("근육군별 운동량 기록이 부족합니다.")
                return@Column
            }
            Surface(
                modifier = Modifier.clickable { showPicker = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    text = MuscleBucketSelection.summary(selected, available),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            AnalysisChartSpecView(muscleShareTrendSpec(summary, selected.toList()))
        }
    }
    if (showPicker) {
        MuscleBucketPickerDialog(
            available = available,
            selected = selected.toSet(),
            defaults = defaults,
            onDismiss = { showPicker = false },
            onApply = { metrics ->
                selected.clear()
                selected.addAll(metrics)
                showPicker = false
            }
        )
    }
}

@Composable
private fun MuscleBucketPickerDialog(
    available: List<StrengthAndMuscleMetricSeriesBuilder.MuscleBucket>,
    selected: Set<TrendMetricId>,
    defaults: Set<TrendMetricId>,
    onDismiss: () -> Unit,
    onApply: (List<TrendMetricId>) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val draft = remember(selected) { mutableStateListOf<TrendMetricId>().apply { addAll(selected) } }
    val filtered = MuscleBucketSelection.filter(available, query)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = draft.isNotEmpty(),
                onClick = { onApply(draft.toList()) }
            ) {
                Text("적용")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                draft.clear()
                draft.addAll(defaults)
            }) {
                Text("초기화")
            }
        },
        title = { Text("근육군 선택") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("검색") }
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MuscleBucketSelection.quickGroups.forEach { group ->
                        AnalysisSelectableChip(group.label, false) {
                            val groupMetrics = available
                                .filter { bucket -> bucket in group.buckets }
                                .map { it.dailyMetric }
                            if (groupMetrics.isNotEmpty()) {
                                draft.clear()
                                draft.addAll(groupMetrics)
                            }
                        }
                    }
                }
                Text(MuscleBucketSelection.summary(draft, available), style = MaterialTheme.typography.labelMedium)
                if (draft.isEmpty()) {
                    Text("최소 1개 이상 선택하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filtered.forEach { bucket ->
                        AnalysisSelectableChip(bucket.label, bucket.dailyMetric in draft) {
                            if (bucket.dailyMetric in draft) draft.remove(bucket.dailyMetric) else draft.add(bucket.dailyMetric)
                        }
                    }
                    if (filtered.isEmpty()) {
                        Text("검색 결과가 없습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    )
}

@Composable
private fun RepRangeShareCard(weeks: List<RepRangeWeekShare>) {
    AnalysisSectionChart(
        title = "반복수 기반 강도 구간 비중",
        spec = ChartSpec(
            type = ChartType.HORIZONTAL_BAR,
            title = "반복수 기반 강도 구간 비중",
            bars = latestRepRangeShare(weeks)
        ),
        note = "1~5회는 저반복/고강도, 6~9회는 중반복/중강도, 10회 이상은 고반복/볼륨으로 분류합니다."
    )
}

@Composable
private fun RepRangeShareTrendCard(weeks: List<RepRangeWeekShare>) {
    val ranges = listOf(
        RepRangeLine("저반복/고강도", RepRangeWeekShare::lowRepShare),
        RepRangeLine("중반복/중강도", RepRangeWeekShare::moderateRepShare),
        RepRangeLine("고반복/볼륨", RepRangeWeekShare::highRepShare)
    )
    val selected = remember { mutableStateListOf<Int>().apply { addAll(ranges.indices) } }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("반복수 구간 비중 추이", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ranges.forEachIndexed { index, range ->
                    AnalysisSelectableChip(range.label, index in selected) {
                        if (index in selected) selected.remove(index) else selected.add(index)
                        if (selected.isEmpty()) selected.add(index)
                    }
                }
            }
            val lineSeries = selected.sorted().map { index ->
                val range = ranges[index]
                ChartSeries(
                    range.label,
                    weeks.map { week -> TrendDataPoint(week.weekStart, range.value(week).takeIf { week.confirmedSetCount > 0 }) }
                )
            }
            val yRange = TrendChartRange.percent(lineSeries.flatMap { series -> series.points.mapNotNull { it.value } })
            AnalysisChartSpecView(
                ChartSpec(
                    type = ChartType.LINE,
                    title = "반복수 구간 비중 추이",
                    lineSeries = lineSeries,
                    yMin = yRange?.first,
                    yMax = yRange?.second
                )
            )
        }
    }
}

@Composable
private fun AnalysisSectionChart(title: String, spec: ChartSpec, note: String? = null) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            AnalysisChartSpecView(spec)
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun metricLineSpec(
    title: String,
    summary: PerformanceTrendSummary,
    metrics: List<TrendMetricId>
): ChartSpec = ChartSpec(
    type = ChartType.LINE,
    title = title,
    lineSeries = metrics.mapNotNull { metric ->
        val points = summary.metricSeries[metric].orEmpty()
        if (points.none { it.value != null }) null else ChartSeries(metric.label(), points)
    }
)

private fun latestMuscleShare(summary: PerformanceTrendSummary): List<BarItem> {
    val raw = StrengthAndMuscleMetricSeriesBuilder.MuscleBucket.values().mapNotNull { bucket ->
        val value = summary.metricSeries[bucket.dailyMetric].orEmpty().lastOrNull { it.value != null }?.value ?: return@mapNotNull null
        bucket.label to value
    }
    val total = raw.sumOf { (_, value) -> value }
    if (total <= 0.0) return emptyList()
    return raw.sortedByDescending { (_, value) -> value }
        .map { (label, value) -> BarItem(label, value / total * 100.0) }
}

private fun muscleShareTrendSpec(summary: PerformanceTrendSummary, selectedMetrics: List<TrendMetricId>): ChartSpec {
    val buckets = StrengthAndMuscleMetricSeriesBuilder.MuscleBucket.values()
    val weekStarts = buckets.flatMap { bucket -> summary.metricSeries[bucket.dailyMetric].orEmpty().map { it.weekStart } }
        .distinct()
        .sorted()
    val totalsByWeek = weekStarts.associateWith { week ->
        buckets.sumOf { bucket ->
            summary.metricSeries[bucket.dailyMetric].orEmpty().firstOrNull { it.weekStart == week }?.value ?: 0.0
        }
    }
    val series = selectedMetrics.mapNotNull { metric ->
        val descriptor = AnalysisMetricRegistry.descriptor(metric) ?: return@mapNotNull null
        ChartSeries(
            descriptor.displayName.removePrefix("주간 ").removeSuffix(" 운동량"),
            weekStarts.map { week ->
                val value = summary.metricSeries[metric].orEmpty().firstOrNull { it.weekStart == week }?.value ?: 0.0
                val total = totalsByWeek[week] ?: 0.0
                TrendDataPoint(week, if (total > 0.0) value / total * 100.0 else null)
            }
        )
    }
    val yRange = TrendChartRange.percent(series.flatMap { item -> item.points.mapNotNull { it.value } })
    return ChartSpec(
        type = ChartType.LINE,
        title = "근육군별 운동량 비율 추이",
        lineSeries = series,
        yMin = yRange?.first,
        yMax = yRange?.second
    )
}

private fun latestRepRangeShare(weeks: List<RepRangeWeekShare>): List<BarItem> {
    val latest = weeks.lastOrNull { week -> week.confirmedSetCount > 0 } ?: return emptyList()
    return listOf(
        BarItem("저반복/고강도(1~5회)", latest.lowRepShare),
        BarItem("중반복/중강도(6~9회)", latest.moderateRepShare),
        BarItem("고반복/볼륨(10회 이상)", latest.highRepShare)
    )
}

private enum class BadmintonLoadMode(val label: String) {
    TOTAL("전체"),
    DIRECT("직접"),
    TRANSFER("전이"),
    METHOD("전이 목적")
}

private data class RepRangeLine(
    val label: String,
    val value: (RepRangeWeekShare) -> Double
)

private fun badmintonSeriesLabel(mode: BadmintonLoadMode, method: String): String = when (mode) {
    BadmintonLoadMode.TOTAL -> "전체 배드민턴 관련 훈련량"
    BadmintonLoadMode.DIRECT -> "직접 배드민턴 훈련량"
    BadmintonLoadMode.TRANSFER -> "배드민턴 전이 훈련량"
    BadmintonLoadMode.METHOD -> BadmintonTrainingMethodLabels.label(method.ifBlank { "전이 목적" })
}

private fun com.training.trackplanner.analysis.trends.BadmintonDailyLoadPoint.valueFor(
    mode: BadmintonLoadMode,
    method: String
): Double = when (mode) {
    BadmintonLoadMode.TOTAL -> totalRaw
    BadmintonLoadMode.DIRECT -> courtRaw + footworkReactiveRaw
    BadmintonLoadMode.TRANSFER -> supportRaw
    BadmintonLoadMode.METHOD -> methodRaw[method] ?: 0.0
}

private fun weeklyBadmintonPoints(
    summary: PerformanceTrendSummary,
    mode: BadmintonLoadMode,
    method: String
): List<TrendDataPoint> =
    summary.badmintonDailyLoads
        .groupBy { point -> point.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .toSortedMap()
        .map { (week, points) ->
            TrendDataPoint(week, points.sumOf { point -> point.valueFor(mode, method) }.takeIf { it > 0.0 })
        }

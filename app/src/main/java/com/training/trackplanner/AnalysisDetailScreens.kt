package com.training.trackplanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import com.training.trackplanner.analysis.trends.TrendDataPoint
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
    val methodTotals = performanceTrend
        ?.let { BadmintonTrainingMethodSeries.totals(it.badmintonDailyLoads) }
        .orEmpty()
    val availableMethodKeys = methodTotals.keys.toList().ifEmpty { BadmintonTrainingMethodSeries.objectiveKeys }
    val defaultMethodKeys = defaultBadmintonMethodKeys(methodTotals, availableMethodKeys)
    var selectedMethodKeysText by rememberSaveable(availableMethodKeys.joinToString("|")) {
        mutableStateOf(defaultMethodKeys.joinToString("|"))
    }
    val selectedMethodKeys = selectedMethodKeysText
        .split("|")
        .filter { it in availableMethodKeys }
        .ifEmpty { defaultMethodKeys }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        performanceTrend?.let { BadmintonTransferObjectiveSentenceCard(it, selectedMethodKeys.toSet()) }
            ?: InfoCard("배드민턴 전이 목적별 자극량을 계산하고 있습니다.")
        performanceTrend?.let {
            BadmintonTrainingLoadCharts(
                summary = it,
                availableMethodKeys = availableMethodKeys,
                selectedMethodKeys = selectedMethodKeys,
                onSelectedMethodKeysChange = { keys -> selectedMethodKeysText = keys.joinToString("|") }
            )
        }
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
private fun BadmintonTransferObjectiveSentenceCard(
    summary: PerformanceTrendSummary,
    selectedMethodKeys: Set<String>
) {
    val methodSummary = BadmintonTrainingMethodSeries.summary(summary.badmintonDailyLoads, selectedMethodKeys)
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
private fun BadmintonTrainingLoadCharts(
    summary: PerformanceTrendSummary,
    availableMethodKeys: List<String>,
    selectedMethodKeys: List<String>,
    onSelectedMethodKeysChange: (List<String>) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(BadmintonLoadMode.TOTAL) }
    var showMethodDescription by rememberSaveable { mutableStateOf(false) }
    var showMethodPicker by rememberSaveable { mutableStateOf(false) }
    val methodTotals = BadmintonTrainingMethodSeries.totals(summary.badmintonDailyLoads)
    val selectedMethodSet = selectedMethodKeys.toSet()
    val selectedMethodTotals = BadmintonTrainingMethodSeries.totals(summary.badmintonDailyLoads, selectedMethodSet)
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
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMethodPicker = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                text = badmintonMethodSelectionSummary(selectedMethodKeys),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
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
        val comparisonGroups = BadmintonTrainingMethodSeries.recentComparisonGroups(summary.badmintonDailyLoads, selectedMethodSet)
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
        if (selectedMethodTotals.isNotEmpty()) {
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
                            bars = selectedMethodTotals.entries
                                .sortedByDescending { it.value }
                                .map { (key, value) ->
                                    BarItem(
                                        BadmintonTrainingMethodLabels.label(key),
                                        value,
                                        BadmintonTrainingMethodSeries.colorIndex(key),
                                        key
                                    )
                                }
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
                    stackedBars = BadmintonTrainingMethodSeries.weeklyStackedGroups(summary.badmintonDailyLoads, selectedMethodSet)
                ),
                note = "각 주마다 어떤 배드민턴 전이 목적의 자극이 많았는지 보여줍니다. 운동 하나가 여러 전이 목적에 동시에 해당할 수 있어 자극량은 중복 반영됩니다. 월별이 아니라 주별 집계입니다."
            )
        } else {
            InfoCard("선택한 전이 목적의 기록이 없습니다.")
        }
        if (showMethodDescription) {
            BadmintonMethodDescriptionDialog(
                methodKeys = selectedMethodKeys,
                examples = summary.badmintonMethodExamples,
                onDismiss = { showMethodDescription = false }
            )
        }
        if (showMethodPicker) {
            BadmintonMethodPickerDialog(
                available = availableMethodKeys,
                selected = selectedMethodKeys.toSet(),
                defaults = defaultBadmintonMethodKeys(methodTotals, availableMethodKeys).toSet(),
                onDismiss = { showMethodPicker = false },
                onApply = { keys ->
                    onSelectedMethodKeysChange(keys)
                    showMethodPicker = false
                }
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
private fun BadmintonMethodPickerDialog(
    available: List<String>,
    selected: Set<String>,
    defaults: Set<String>,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val draft = remember(selected) { mutableStateListOf<String>().apply { addAll(selected) } }
    val filtered = filterBadmintonMethodKeys(available, query)
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
                draft.addAll(defaults.ifEmpty { available.take(1).toSet() })
            }) {
                Text("초기화")
            }
        },
        title = { Text("전이 목적 선택") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("검색") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        draft.clear()
                        draft.addAll(available)
                    }) {
                        Text("전체 선택")
                    }
                    TextButton(onClick = {
                        draft.clear()
                        draft.addAll(defaults.ifEmpty { available.take(1).toSet() })
                    }) {
                        Text("권장 선택")
                    }
                }
                Text(badmintonMethodSelectionSummary(draft), style = MaterialTheme.typography.labelMedium)
                if (draft.isEmpty()) {
                    Text("최소 1개 이상 선택하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filtered.forEach { key ->
                        val checked = key in draft
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) draft.remove(key) else draft.add(key)
                                },
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (key !in draft) draft.add(key)
                                    } else {
                                        draft.remove(key)
                                    }
                                }
                            )
                            Text(
                                modifier = Modifier.padding(top = 12.dp),
                                text = BadmintonTrainingMethodLabels.label(key),
                                style = MaterialTheme.typography.bodyMedium
                            )
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

private fun defaultBadmintonMethodKeys(
    totals: Map<String, Double>,
    available: List<String>
): List<String> {
    val fallback = listOf("FOOTWORK", "ACCELERATION", "REACTION", "DECELERATION")
        .filter { it in available }
    return totals.entries
        .sortedByDescending { it.value }
        .map { it.key }
        .take(4)
        .ifEmpty { fallback }
        .ifEmpty { available.take(1) }
}

private fun badmintonMethodSelectionSummary(keys: Collection<String>): String {
    val labels = keys.map(BadmintonTrainingMethodLabels::label)
    return when {
        labels.isEmpty() -> "전이 목적 선택"
        labels.size <= 3 -> labels.joinToString(", ") + " 선택됨"
        else -> "${labels.take(2).joinToString(", ")} 외 ${labels.size - 2}개 선택됨"
    }
}

private fun filterBadmintonMethodKeys(available: List<String>, query: String): List<String> {
    val needle = query.trim()
    if (needle.isEmpty()) return available
    return available.filter { key ->
        key.contains(needle, ignoreCase = true) ||
            BadmintonTrainingMethodLabels.label(key).contains(needle, ignoreCase = true)
    }
}

private enum class BadmintonLoadMode(val label: String) {
    TOTAL("전체"),
    DIRECT("직접"),
    TRANSFER("전이"),
    METHOD("전이 목적")
}

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

package com.training.trackplanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.training.trackplanner.analysis.lab.AnalysisMetricRegistry
import com.training.trackplanner.analysis.lab.MuscleBucketSelection
import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder
import com.training.trackplanner.analysis.trends.BarItem
import com.training.trackplanner.analysis.trends.ChartSeries
import com.training.trackplanner.analysis.trends.ChartSpec
import com.training.trackplanner.analysis.trends.ChartTimeGranularity
import com.training.trackplanner.analysis.trends.ChartType
import com.training.trackplanner.analysis.trends.AnalysisChartTemporalPolicy
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import com.training.trackplanner.analysis.trends.RepRangeWeekShare
import com.training.trackplanner.analysis.trends.TrendChartRange
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.analysis.trends.label

@Composable
internal fun MainLiftE1rmCard(summary: PerformanceTrendSummary) {
    val spec = mainLiftE1rmSpec(summary.metricSeries)
    AnalysisSectionChart(
        title = "주요 운동 e1RM",
        spec = spec,
        note = "확인된 실제 수행 세트만 사용하며, 기록 없는 주는 0으로 채우지 않습니다.",
        footer = { ChartSeriesLegend(spec.lineSeries) }
    )
}

internal fun mainLiftE1rmSpec(
    metricSeries: Map<TrendMetricId, List<TrendDataPoint>>
): ChartSpec {
    val metrics = listOf(
        TrendMetricId.BENCH_PRESS_E1RM,
        TrendMetricId.SQUAT_E1RM,
        TrendMetricId.DEADLIFT_E1RM
    )
    val series = metrics.mapNotNull { metric ->
        val points = metricSeries[metric].orEmpty()
        if (points.none { it.value?.isFinite() == true }) null else ChartSeries(metric.label(), points)
    }
    val domain = AnalysisChartTemporalPolicy.weeklyDomain(
        series.flatMap { item -> item.points.map(TrendDataPoint::weekStart) }
    )
    val yRange = TrendChartRange.values(
        series.flatMap { item -> item.points.mapNotNull { point -> point.value?.takeIf(Double::isFinite) } }
    )
    return ChartSpec(
        type = ChartType.LINE,
        title = "주요 운동 e1RM",
        lineSeries = series,
        yMin = yRange?.first,
        yMax = yRange?.second,
        timeGranularity = ChartTimeGranularity.WEEKLY,
        xDomain = domain,
        valueUnit = "kg"
    )
}

@Composable
internal fun MuscleLoadShareCard(summary: PerformanceTrendSummary) {
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
internal fun MuscleLoadShareTrendCard(summary: PerformanceTrendSummary) {
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
            val spec = muscleShareTrendSpec(summary, selected.toList())
            analysisChartPeriodLabel(spec)?.let { period ->
                Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnalysisChartSpecView(spec)
            ChartSeriesLegend(
                series = spec.lineSeries,
                latestValueFormatter = { "${formatDecimal(it)}%" }
            )
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
internal fun RepRangeShareCard(weeks: List<RepRangeWeekShare>) {
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
internal fun RepRangeShareTrendCard(weeks: List<RepRangeWeekShare>) {
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
            val spec = ChartSpec(
                type = ChartType.LINE,
                title = "반복수 구간 비중 추이",
                lineSeries = lineSeries,
                yMin = yRange?.first,
                yMax = yRange?.second,
                timeGranularity = ChartTimeGranularity.WEEKLY,
                xDomain = AnalysisChartTemporalPolicy.weeklyDomain(weeks.map(RepRangeWeekShare::weekStart)),
                valueUnit = "%"
            )
            analysisChartPeriodLabel(spec)?.let { period ->
                Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnalysisChartSpecView(spec)
        }
    }
}

@Composable
internal fun AnalysisSectionChart(
    title: String,
    spec: ChartSpec,
    note: String? = null,
    footer: @Composable (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            analysisChartPeriodLabel(spec)?.let { period ->
                Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnalysisChartSpecView(spec)
            footer?.invoke()
            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ChartSeriesLegend(
    series: List<ChartSeries>,
    latestValueFormatter: ((Double) -> String)? = { "${formatDecimal(it)}kg" }
) {
    if (series.isEmpty()) return
    val colors = analysisChartPalette()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        series.forEachIndexed { index, item ->
            val latest = item.points.lastOrNull { point -> point.value != null }?.value
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors[index % colors.size].copy(alpha = 0.18f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = colors[index % colors.size]
                    ) {}
                    val label = if (latestValueFormatter != null && latest != null) {
                        "${item.label} · ${latestValueFormatter(latest)}"
                    } else {
                        item.label
                    }
                    Text(text = label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

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
        yMax = yRange?.second,
        timeGranularity = ChartTimeGranularity.WEEKLY,
        xDomain = AnalysisChartTemporalPolicy.weeklyDomain(weekStarts),
        valueUnit = "%"
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

private data class RepRangeLine(
    val label: String,
    val value: (RepRangeWeekShare) -> Double
)

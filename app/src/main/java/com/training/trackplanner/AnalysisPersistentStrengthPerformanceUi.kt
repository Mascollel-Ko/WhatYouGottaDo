package com.training.trackplanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthHistoryPoint
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthPerformanceSummary
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthTargetSummary
import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.data.StrengthAnalysisLifecycleStatus
import com.training.trackplanner.analysis.trends.AnalysisChartTemporalPolicy
import com.training.trackplanner.analysis.trends.ChartSeries
import com.training.trackplanner.analysis.trends.ChartSpec
import com.training.trackplanner.analysis.trends.ChartTimeGranularity
import com.training.trackplanner.analysis.trends.ChartType
import com.training.trackplanner.analysis.trends.IntervalBand
import com.training.trackplanner.analysis.trends.IntervalPoint
import com.training.trackplanner.analysis.trends.TrendChartRange
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.localization.localizedUiText
import java.util.Locale
import kotlin.math.roundToInt

internal enum class StrengthPerformanceDisplayMode {
    LEVEL,
    GROWTH_RATE
}

internal data class StrengthPerformanceSelectionState(
    val selectedTargetKeys: List<String>,
    val focusedTargetKey: String,
    val displayMode: StrengthPerformanceDisplayMode
)

internal data class PersistentStrengthGrowthPoint(
    val sessionDate: java.time.LocalDate,
    val previousMedianKg: Double?,
    val currentMedianKg: Double?,
    val medianGrowthPercent: Double?,
    val lowGrowthPercent: Double?,
    val highGrowthPercent: Double?,
    val directObservationGrowthPercent: Double?
)

private val SQUAT_COLOR = 0xFF1565C0.toInt()
private val BENCH_PRESS_COLOR = 0xFFD32F2F.toInt()
private val DEADLIFT_COLOR = 0xFF2E7D32.toInt()
private val WEIGHTED_PULL_UP_COLOR = 0xFF6D4C41.toInt()

internal fun strengthPerformanceTargetColor(targetKey: String): Color? = when (targetKey) {
    StrengthPerformanceRegistry.BACK_SQUAT.value -> Color(SQUAT_COLOR)
    StrengthPerformanceRegistry.BENCH_PRESS.value -> Color(BENCH_PRESS_COLOR)
    StrengthPerformanceRegistry.CONVENTIONAL_DEADLIFT.value -> Color(DEADLIFT_COLOR)
    StrengthPerformanceRegistry.WEIGHTED_PULL_UP.value -> Color(WEIGHTED_PULL_UP_COLOR)
    else -> null
}

internal fun initialStrengthPerformanceSelectionState(
    orderedTargetKeys: List<String>
): StrengthPerformanceSelectionState {
    val first = orderedTargetKeys.first()
    return StrengthPerformanceSelectionState(
        selectedTargetKeys = listOf(first),
        focusedTargetKey = first,
        displayMode = StrengthPerformanceDisplayMode.LEVEL
    )
}

internal fun toggleStrengthPerformanceTarget(
    state: StrengthPerformanceSelectionState,
    targetKey: String,
    orderedTargetKeys: List<String>
): StrengthPerformanceSelectionState {
    if (targetKey !in orderedTargetKeys) return state
    val selected = state.selectedTargetKeys.filter { it in orderedTargetKeys }
    if (targetKey in selected) {
        if (selected.size == 1) return state
        val remaining = orderedTargetKeys.filter { it in selected && it != targetKey }
        return state.copy(
            selectedTargetKeys = remaining,
            focusedTargetKey = if (state.focusedTargetKey == targetKey) remaining.first() else state.focusedTargetKey
        )
    }
    val expanded = orderedTargetKeys.filter { it in selected || it == targetKey }
    return state.copy(selectedTargetKeys = expanded, focusedTargetKey = targetKey)
}

internal fun persistentStrengthGrowthHistory(
    target: PersistentStrengthTargetSummary
): List<PersistentStrengthGrowthPoint> = target.history.mapIndexed { index, point ->
    val previousMedian = target.history.getOrNull(index - 1)?.posteriorMedianKg
        ?.takeIf { value -> value.isFinite() && value > 0.0 }
    val currentMedian = point.posteriorMedianKg?.takeIf { value -> value.isFinite() && value > 0.0 }
    fun versusPrevious(value: Double?): Double? =
        value?.takeIf { current -> current.isFinite() && current > 0.0 }
            ?.takeIf { previousMedian != null }
            ?.let { current -> ((current / previousMedian!!) - 1.0) * 100.0 }
    PersistentStrengthGrowthPoint(
        sessionDate = point.sessionDate,
        previousMedianKg = previousMedian,
        currentMedianKg = currentMedian,
        medianGrowthPercent = versusPrevious(currentMedian),
        lowGrowthPercent = versusPrevious(point.posteriorLow80Kg),
        highGrowthPercent = versusPrevious(point.posteriorHigh80Kg),
        directObservationGrowthPercent = point.sessionObservationMedianKg
            .takeIf { point.hasDirectTargetScaleObservation() }
            ?.let(::versusPrevious)
    )
}

@Composable
internal fun PersistentStrengthPerformanceCards(
    summary: PersistentStrengthPerformanceSummary?,
    rebuildRunning: Boolean = false,
    onRetryRebuild: () -> Unit = {}
) {
    if (summary == null) return
    if (rebuildRunning) {
        InfoCard("보존된 운동 기록으로 근력 분석을 처음부터 다시 계산하고 있습니다.\n시간이 걸려도 화면을 닫지 않아도 됩니다.")
        return
    }
    when (summary.lifecycleStatus) {
        StrengthAnalysisLifecycleStatus.REBUILDING -> {
            InfoCard("현재 근력 분석 모델로 과거 운동 기록을 재계산하고 있습니다.")
            return
        }
        StrengthAnalysisLifecycleStatus.REBUILD_FAILED -> {
            StrengthRebuildFailureContent(summary, onRetryRebuild)
            return
        }
        StrengthAnalysisLifecycleStatus.CURRENT -> Unit
    }
    if (summary.targets.isEmpty()) return
    val orderedTargetKeys = summary.targets.map(PersistentStrengthTargetSummary::targetKey)
    var selectedTargetKeys by rememberSaveable { mutableStateOf(listOf(orderedTargetKeys.first())) }
    var focusedTargetKey by rememberSaveable { mutableStateOf(orderedTargetKeys.first()) }
    var displayMode by rememberSaveable { mutableStateOf(StrengthPerformanceDisplayMode.LEVEL) }
    val selectedTargets = summary.targets.filter { target -> target.targetKey in selectedTargetKeys }
        .ifEmpty { listOf(summary.targets.first()) }
    val focusedTarget = selectedTargets.firstOrNull { target -> target.targetKey == focusedTargetKey }
        ?: selectedTargets.first()
    val currentState = StrengthPerformanceSelectionState(
        selectedTargetKeys = selectedTargets.map(PersistentStrengthTargetSummary::targetKey),
        focusedTargetKey = focusedTarget.targetKey,
        displayMode = displayMode
    )
    PersistentStrengthCapabilityCard(
        summary = summary,
        selectedTargets = selectedTargets,
        focusedTarget = focusedTarget,
        displayMode = displayMode,
        onModeChanged = { mode -> displayMode = mode },
        onTargetToggled = { targetKey ->
            val updated = toggleStrengthPerformanceTarget(currentState, targetKey, orderedTargetKeys)
            selectedTargetKeys = updated.selectedTargetKeys
            focusedTargetKey = updated.focusedTargetKey
        },
        onFocused = { targetKey -> focusedTargetKey = targetKey }
    )
    PersistentStrengthHistoryCard(selectedTargets, focusedTarget, displayMode)
}

@Composable
private fun StrengthRebuildFailureContent(
    summary: PersistentStrengthPerformanceSummary,
    onRetryRebuild: () -> Unit
) {
    var detailsVisible by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoCard("현재 근력 분석을 재계산하지 못했습니다. 원시 운동 기록은 삭제되지 않았습니다.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRetryRebuild) {
                Text("처음부터 재시도")
            }
            TextButton(onClick = { detailsVisible = !detailsVisible }) {
                Text(if (detailsVisible) "자세히 닫기" else "자세히")
            }
        }
        if (detailsVisible) {
            val diagnosticCode = localizedUiText(
                "진단 코드: ${summary.lifecycleDiagnosticCode ?: "REBUILD_FAILED"}"
            )
            val diagnosticMessageLines = mutableListOf<String>()
            for (line in (summary.lifecycleDiagnosticMessage
                ?: "상세 실패 메시지가 저장되지 않았습니다.").lines()) {
                diagnosticMessageLines += localizedUiText(line)
            }
            val diagnosticMessage = diagnosticMessageLines.joinToString("\n")
            Text(
                text = "$diagnosticCode\n$diagnosticMessage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "재시도하면 근력 분석 파생 결과만 지우고 보존된 운동 기록을 날짜순으로 다시 계산합니다.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PersistentStrengthCapabilityCard(
    summary: PersistentStrengthPerformanceSummary,
    selectedTargets: List<PersistentStrengthTargetSummary>,
    focusedTarget: PersistentStrengthTargetSummary,
    displayMode: StrengthPerformanceDisplayMode,
    onModeChanged: (StrengthPerformanceDisplayMode) -> Unit,
    onTargetToggled: (String) -> Unit,
    onFocused: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("persistent-strength-capability-card"),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("현재 수행능력 추정", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            StrengthPerformanceModeSelector(displayMode, onModeChanged)
            PersistentTargetSelector(
                targets = summary.targets,
                selectedTargetKeys = selectedTargets.map(PersistentStrengthTargetSummary::targetKey).toSet(),
                focusedTargetKey = focusedTarget.targetKey,
                onToggled = onTargetToggled
            )
            if (selectedTargets.size == 1) {
                PersistentStrengthSingleTargetCurrent(focusedTarget, displayMode)
            } else {
                selectedTargets.forEach { target ->
                    PersistentStrengthCompactCurrentRow(
                        target = target,
                        displayMode = displayMode,
                        focused = target.targetKey == focusedTarget.targetKey,
                        onClick = { onFocused(target.targetKey) }
                    )
                }
            }
            PersistentStrengthDiagnostics(summary, focusedTarget)
        }
    }
}

@Composable
private fun StrengthPerformanceModeSelector(
    selected: StrengthPerformanceDisplayMode,
    onSelected: (StrengthPerformanceDisplayMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StrengthPerformanceDisplayMode.entries.forEach { mode ->
            QuietChoiceChip(
                label = if (mode == StrengthPerformanceDisplayMode.LEVEL) "레벨" else "성장률",
                selected = mode == selected,
                onClick = { onSelected(mode) }
            )
        }
    }
}

@Composable
private fun PersistentStrengthSingleTargetCurrent(
    target: PersistentStrengthTargetSummary,
    displayMode: StrengthPerformanceDisplayMode
) {
    when (displayMode) {
        StrengthPerformanceDisplayMode.LEVEL -> {
            val medianLabel = if (target.isWeightedPullUp()) "추정 총부하" else "사후분포 중앙값"
            Text(
                "${localizedUiText(medianLabel)} ${kgOrDash(target.currentMedianKg)}",
                fontWeight = FontWeight.SemiBold
            )
            Text("80% 범위 ${rangeOrDash(target.currentLow80Kg, target.currentHigh80Kg)}")
            if (target.isWeightedPullUp()) {
                Text("현재 체중 기준 추가중량 ${signedKgOrDash(target.currentAddedWeightKg)}")
            }
            Text(
                target.latestDirectObservationKg?.let { load ->
                    "최근 직접 1RM ${kgOrDash(load)} · ${target.latestDirectObservationDate}"
                } ?: "최근 직접 1RM 기록 없음",
                style = MaterialTheme.typography.bodySmall
            )
        }
        StrengthPerformanceDisplayMode.GROWTH_RATE -> {
            val latest = persistentStrengthGrowthHistory(target).lastOrNull()
            Text(
                "최근 성장률 ${percentOrDash(latest?.medianGrowthPercent)}",
                fontWeight = FontWeight.SemiBold
            )
            if (latest?.previousMedianKg == null) {
                Text("이전 추정 없음")
            } else {
                Text("이전 사후분포 중앙값 ${kgOrDash(latest.previousMedianKg)}")
                Text("현재 사후분포 중앙값 ${kgOrDash(latest.currentMedianKg)}")
                Text(
                    "이전 중앙값 대비 현재 추정 범위 ${percentRangeOrDash(latest.lowGrowthPercent, latest.highGrowthPercent)}"
                )
            }
        }
    }
}

@Composable
private fun PersistentStrengthCompactCurrentRow(
    target: PersistentStrengthTargetSummary,
    displayMode: StrengthPerformanceDisplayMode,
    focused: Boolean,
    onClick: () -> Unit
) {
    val color = strengthPerformanceTargetColor(target.targetKey) ?: MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = if (focused) 0.18f else 0.07f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(modifier = Modifier.size(10.dp), shape = RoundedCornerShape(8.dp), color = color) {}
            Text(
                text = target.comparisonDisplayName(),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when (displayMode) {
                StrengthPerformanceDisplayMode.LEVEL -> {
                    Text(
                        text = kgOrDash(target.currentMedianKg),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "80% ${rangeOrDash(target.currentLow80Kg, target.currentHigh80Kg)}",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                StrengthPerformanceDisplayMode.GROWTH_RATE -> {
                    Text(
                        text = percentOrDash(
                            persistentStrengthGrowthHistory(target).lastOrNull()?.medianGrowthPercent
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun PersistentStrengthDiagnostics(
    summary: PersistentStrengthPerformanceSummary,
    selected: PersistentStrengthTargetSummary
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "관련 세션 ${selected.relevantSessionCount} · 직접 1RM ${selected.directObservationCount} · RPE 확률 관측 ${selected.knownRpeObservationCount} · 강한 nRM ${selected.strongNrmObservationCount} · 프록시 혁신 ${selected.proxyObservationCount} · 실패 ${selected.failureObservationCount}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "곡선 보정 ${selected.curveCalibrationStatus ?: "CANONICAL_ONLY"} · 최근 처리 ${selected.lastProcessedSessionDate ?: "-"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "활성 revision ${summary.activeRevisionKey ?: "-"} · RIR 정책 ${summary.rirPolicyVersion ?: "-"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "모델 ${selected.modelVersion ?: "-"} · 곡선 ${selected.curveVersion ?: "-"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "프록시 운동의 절대 중량을 대상 운동 중량으로 직접 환산하지 않습니다. 운동 자체 변화 중 공유 근력 신호만 반영합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
    }
}

@Composable
private fun PersistentStrengthHistoryCard(
    selectedTargets: List<PersistentStrengthTargetSummary>,
    focusedTarget: PersistentStrengthTargetSummary,
    displayMode: StrengthPerformanceDisplayMode
) {
    var expanded by rememberSaveable(focusedTarget.targetKey) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("persistent-strength-history-card"),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("세션별 사후분포 기록", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (selectedTargets.all { target -> target.history.isEmpty() }) {
                InfoCard("처리된 완료 세션이 아직 없습니다.")
            } else {
                val spec = persistentStrengthHistoryChartSpec(
                    targets = selectedTargets,
                    displayMode = displayMode,
                    focusedTargetKey = focusedTarget.targetKey
                )
                AnalysisChartSpecView(spec)
                PersistentStrengthComparisonLegend(selectedTargets, displayMode)
                Text(
                    "실선: 사후분포 중앙값 · 음영: 80% 범위 · ○: RPE/RIR 기반 직접 세션 관측",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (displayMode == StrengthPerformanceDisplayMode.GROWTH_RATE) {
                    Text(
                        "성장률 음영은 이전 중앙값 대비 현재 추정 범위이며 정확한 성장률 사후분포가 아닙니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "기록 접기" else "세션 상세 보기")
                }
                if (expanded) {
                    val growthByEvent = persistentStrengthGrowthHistory(focusedTarget)
                        .zip(focusedTarget.history)
                        .associate { (growth, history) -> history.eventUuid to growth }
                    focusedTarget.history.asReversed().take(12).forEach { point ->
                        PersistentStrengthHistoryRow(
                            target = focusedTarget,
                            point = point,
                            growth = growthByEvent[point.eventUuid],
                            displayMode = displayMode
                        )
                    }
                    if (focusedTarget.localExerciseDetails.isNotEmpty()) {
                        Text("관련 운동 자체 추정", fontWeight = FontWeight.SemiBold)
                        focusedTarget.localExerciseDetails.forEach { local ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("persistent-strength-local-detail"),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text("${local.exerciseName} · ${local.sessionDate}", fontWeight = FontWeight.SemiBold)
                                Text("세션 전 local prior ${kgOrDash(local.priorMedianKg)}")
                                Text("세션 likelihood ${kgOrDash(local.sessionLikelihoodMedianKg)}")
                                Text("세션 innovation ${percentOrDash(local.innovationPercent)}")
                                Text("세션 후 local posterior ${kgOrDash(local.posteriorMedianKg)}")
                                Text(
                                    if (local.proxyTransferApplied) {
                                        "프록시 전이 적용 · 공유 근력 신호만 반영"
                                    } else {
                                        "프록시 전이 제외 · ${local.proxyTransferExclusionReason ?: "조건 미충족"}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersistentStrengthHistoryRow(
    target: PersistentStrengthTargetSummary,
    point: PersistentStrengthHistoryPoint,
    growth: PersistentStrengthGrowthPoint?,
    displayMode: StrengthPerformanceDisplayMode
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("persistent-strength-history-row"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(point.sessionDate.toString(), fontWeight = FontWeight.SemiBold)
        Text("세션 전 추정 ${kgOrDash(point.priorMedianKg)} · 80% ${rangeOrDash(point.priorLow80Kg, point.priorHigh80Kg)}")
        Text("관측 ${kgOrDash(point.sessionObservationMedianKg)} · ${observationLabel(point.strongObservationType)}")
        Text("세션 후 추정 ${kgOrDash(point.posteriorMedianKg)} · 80% ${rangeOrDash(point.posteriorLow80Kg, point.posteriorHigh80Kg)}")
        Text("중앙값 변화 ${signedKgOrDash(point.posteriorMeanChangeKg)} · 구간 폭 변화 ${signedKgOrDash(point.intervalWidthChange80Kg)}")
        if (displayMode == StrengthPerformanceDisplayMode.GROWTH_RATE) {
            if (growth?.medianGrowthPercent == null) {
                Text("성장률 이전 추정 없음")
            } else {
                Text(
                    "성장률 ${percentOrDash(growth.medianGrowthPercent)} · 이전 ${kgOrDash(growth.previousMedianKg)} · 현재 ${kgOrDash(growth.currentMedianKg)}"
                )
                Text(
                    "이전 중앙값 대비 현재 추정 범위 ${percentRangeOrDash(growth.lowGrowthPercent, growth.highGrowthPercent)}"
                )
            }
        }
        point.predictivePercentile?.let { percentile -> Text("예측분포 내 위치 ${(percentile * 100).roundToInt()}%") }
        Text("곡선 ${point.curveProfileId ?: "-"} · 매칭 ${point.curveMatchLevel ?: "-"} · 보정 ${point.curveCalibrationStatus ?: "-"}")
        if (target.isWeightedPullUp()) {
            Text(
                "당시 체중 ${kgOrDash(point.bodyWeightKgAtProcessing)} · 당시 추가중량 ${signedKgOrDash(point.rawAddedWeightKgAtProcessing)} · 당시 총부하 ${kgOrDash(point.totalLoadKgAtProcessing)}"
            )
            Text("체중 출처 ${point.bodyWeightSource ?: "-"}", style = MaterialTheme.typography.labelSmall)
        }
        Text(
            "근거 ${point.sourceEvidenceStatus} · 세트 ${point.sourceSetCountAtProcessing} · 지문 ${point.evidenceFingerprint.take(12)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun PersistentStrengthPerformanceLabCard(summary: PersistentStrengthPerformanceSummary?) {
    if (summary == null) return
    when (summary.lifecycleStatus) {
        StrengthAnalysisLifecycleStatus.REBUILDING -> {
            InfoCard("현재 근력 분석 모델로 과거 운동 기록을 재계산하고 있습니다.")
            return
        }
        StrengthAnalysisLifecycleStatus.REBUILD_FAILED -> {
            val message = localizedUiText("현재 근력 분석을 재계산하지 못했습니다.")
            val diagnostic = summary.lifecycleDiagnosticCode
                ?.let { "\n${localizedUiText("진단 코드: $it")}" }
                .orEmpty()
            InfoCard(message + diagnostic)
            return
        }
        StrengthAnalysisLifecycleStatus.CURRENT -> Unit
    }
    if (summary.targets.isEmpty()) return
    var selectedKey by rememberSaveable { mutableStateOf(summary.targets.first().targetKey) }
    val selected = summary.targets.firstOrNull { target -> target.targetKey == selectedKey } ?: summary.targets.first()
    Card(
        modifier = Modifier.fillMaxWidth().testTag("persistent-strength-lab-card"),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("지속형 수행능력 사후분포 진단", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "완료 세션 이벤트 원장과 저장된 필터링 사후분포를 진단합니다. Bayesian 시계열 실험실과는 별도 모델입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PersistentTargetSelector(summary.targets, selected) { target -> selectedKey = target.targetKey }
            Text("대상 키 ${selected.targetKey}")
            Text("곡선 ${selected.curveProfileId ?: "-"} · 매칭 ${selected.curveMatchLevel ?: "-"}")
            Text("분산 배수 ${decimalOrDash(selected.curveVarianceMultiplier)} · 개인 보정 ${selected.curveCalibrationStatus ?: "-"}")
            Text("직접 ${selected.directObservationCount} · 프록시 ${selected.proxyObservationCount}")
            Text("이벤트 원장 전체 ${summary.eventCount} · 대기 ${summary.pendingEventCount} · 실패 ${summary.failedEventCount}")
            Text("최근 이벤트 지문 ${summary.latestEventFingerprint?.take(16) ?: "-"}")
            Text("모델 경계 ${summary.modelVersionBoundaries.ifEmpty { listOf("-") }.joinToString()}")
            Text("곡선 경계 ${summary.curveVersionBoundaries.ifEmpty { listOf("-") }.joinToString()}")
            Text("상태 지문 ${summary.modelStateFingerprint?.take(16) ?: "-"} · 스키마 ${summary.factorSchemaVersion ?: "-"}")
            Text("활성 revision ${summary.activeRevisionKey ?: "-"} · 상태 ${summary.activeRevisionStatus ?: "-"}")
            Text("이전 revision ${summary.supersededRevisionCount} · 재빌드 출처 ${summary.activeRevisionReason ?: "-"}")
            Text("local 운동 상태 ${summary.localExerciseStateCount} · 적용 proxy 전이 ${summary.proxyTransferCount}")
            Text("target-specific proxy 위반 ${summary.targetSpecificProxyViolationCount}")
            Text("RIR 정책 ${summary.rirPolicyVersion ?: "-"}")
            Text(
                "grid 진단 ${summary.gridDiagnosticCount} · 지원 범위 밖 반복 ${summary.unsupportedRepetitionEvidenceCount}"
            )
            Text("부트스트랩 ${summary.bootstrapProvenance ?: "없음"}")
            Text("백업 복원 ${summary.backupRestorationProvenance ?: "없음"}")
            Text(
                "수치 진단 ${summary.numericalDiagnostics.ifEmpty { listOf("정상") }.joinToString()}",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun PersistentTargetSelector(
    targets: List<PersistentStrengthTargetSummary>,
    selected: PersistentStrengthTargetSummary,
    onSelected: (PersistentStrengthTargetSummary) -> Unit
) {
    AnalysisChipRow(
        labels = targets.map(PersistentStrengthTargetSummary::displayNameKo),
        selected = targets.indexOfFirst { target -> target.targetKey == selected.targetKey }.coerceAtLeast(0),
        onSelect = { index -> onSelected(targets[index]) }
    )
}

@Composable
private fun PersistentTargetSelector(
    targets: List<PersistentStrengthTargetSummary>,
    selectedTargetKeys: Set<String>,
    focusedTargetKey: String,
    onToggled: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        targets.forEach { target ->
            val color = strengthPerformanceTargetColor(target.targetKey) ?: MaterialTheme.colorScheme.primary
            val selected = target.targetKey in selectedTargetKeys
            FilterChip(
                modifier = Modifier.testTag("persistent-strength-target-${target.targetKey}"),
                selected = selected,
                onClick = { onToggled(target.targetKey) },
                label = { Text(target.displayNameKo, maxLines = 1, softWrap = false) },
                leadingIcon = {
                    Surface(
                        modifier = Modifier.size(9.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = color
                    ) {}
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(
                        alpha = if (target.targetKey == focusedTargetKey) 0.24f else 0.12f
                    ),
                    selectedLabelColor = color
                )
            )
        }
    }
}

@Composable
private fun PersistentStrengthComparisonLegend(
    targets: List<PersistentStrengthTargetSummary>,
    displayMode: StrengthPerformanceDisplayMode
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        targets.forEach { target ->
            val color = strengthPerformanceTargetColor(target.targetKey) ?: MaterialTheme.colorScheme.primary
            val latest = when (displayMode) {
                StrengthPerformanceDisplayMode.LEVEL -> kgOrDash(target.currentMedianKg)
                StrengthPerformanceDisplayMode.GROWTH_RATE ->
                    percentOrDash(persistentStrengthGrowthHistory(target).lastOrNull()?.medianGrowthPercent)
            }
            Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.14f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(modifier = Modifier.size(8.dp), shape = RoundedCornerShape(8.dp), color = color) {}
                    Text("${target.comparisonDisplayName()} · $latest", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

internal fun persistentStrengthHistoryChartSpec(
    summary: PersistentStrengthTargetSummary
): ChartSpec = persistentStrengthHistoryChartSpec(
    targets = listOf(summary),
    displayMode = StrengthPerformanceDisplayMode.LEVEL,
    focusedTargetKey = summary.targetKey
)

internal fun persistentStrengthHistoryChartSpec(
    targets: List<PersistentStrengthTargetSummary>,
    displayMode: StrengthPerformanceDisplayMode,
    focusedTargetKey: String
): ChartSpec {
    val posteriorSeries = targets.map { target ->
        val points = when (displayMode) {
            StrengthPerformanceDisplayMode.LEVEL ->
                target.history.map { point -> TrendDataPoint(point.sessionDate, point.posteriorMedianKg) }
            StrengthPerformanceDisplayMode.GROWTH_RATE ->
                persistentStrengthGrowthHistory(target).mapNotNull { point ->
                    point.medianGrowthPercent?.let { value -> TrendDataPoint(point.sessionDate, value) }
                }
        }
        ChartSeries(
            label = "${target.comparisonDisplayName()} 사후분포 중앙값",
            points = points,
            seriesKey = target.targetKey,
            colorKey = target.targetKey,
            connectAcrossDomainGaps = true
        )
    }
    val observationSeries = targets.map { target ->
        val points = when (displayMode) {
            StrengthPerformanceDisplayMode.LEVEL ->
                target.history.mapNotNull { point ->
                    point.sessionObservationMedianKg
                        ?.takeIf { value -> value.isFinite() && value > 0.0 && point.hasDirectTargetScaleObservation() }
                        ?.let { value -> TrendDataPoint(point.sessionDate, value) }
                }
            StrengthPerformanceDisplayMode.GROWTH_RATE ->
                persistentStrengthGrowthHistory(target).mapNotNull { point ->
                    point.directObservationGrowthPercent?.let { value -> TrendDataPoint(point.sessionDate, value) }
                }
        }
        ChartSeries(
            label = "${target.comparisonDisplayName()} 직접 세션 관측",
            points = points,
            connectPoints = false,
            seriesKey = "${target.targetKey}.observation",
            colorKey = target.targetKey,
            hollowPoints = true
        )
    }
    val intervalBands = targets.map { target ->
        val points = when (displayMode) {
            StrengthPerformanceDisplayMode.LEVEL -> target.history.mapNotNull { point ->
                val low = point.posteriorLow80Kg
                val high = point.posteriorHigh80Kg
                if (low == null || high == null) null else IntervalPoint(point.sessionDate, low, high)
            }
            StrengthPerformanceDisplayMode.GROWTH_RATE ->
                persistentStrengthGrowthHistory(target).mapNotNull { point ->
                    val low = point.lowGrowthPercent
                    val high = point.highGrowthPercent
                    if (low == null || high == null) null else IntervalPoint(point.sessionDate, low, high)
                }
        }
        IntervalBand(
            label = "${target.comparisonDisplayName()} 80% 범위",
            points = points,
            seriesKey = target.targetKey,
            colorKey = target.targetKey,
            alpha = if (target.targetKey == focusedTargetKey) 0.18f else 0.10f
        )
    }
    val allValues = posteriorSeries.flatMap { series -> series.points.mapNotNull(TrendDataPoint::value) } +
        observationSeries.flatMap { series -> series.points.mapNotNull(TrendDataPoint::value) } +
        intervalBands.flatMap { band -> band.points.flatMap { point -> listOf(point.lower, point.upper) } }
    val yRange = TrendChartRange.values(
        if (displayMode == StrengthPerformanceDisplayMode.GROWTH_RATE) allValues + 0.0 else allValues
    )
    val domain = AnalysisChartTemporalPolicy.dailyDomain(
        targets.flatMap { target -> target.history.map(PersistentStrengthHistoryPoint::sessionDate) }
    )
    return ChartSpec(
        type = ChartType.LINE,
        title = if (displayMode == StrengthPerformanceDisplayMode.LEVEL) {
            "수행능력 레벨 비교"
        } else {
            "직전 추정 대비 성장률"
        },
        lineSeries = posteriorSeries + observationSeries,
        intervalBands = intervalBands,
        horizontalReferenceValues = if (displayMode == StrengthPerformanceDisplayMode.GROWTH_RATE) listOf(0.0) else emptyList(),
        yMin = yRange?.first,
        yMax = yRange?.second,
        timeGranularity = ChartTimeGranularity.DAILY,
        xDomain = domain,
        valueUnit = if (displayMode == StrengthPerformanceDisplayMode.LEVEL) "kg" else "%",
        enableVerticalZoom = true
    )
}

private fun PersistentStrengthTargetSummary.isWeightedPullUp(): Boolean =
    loadSemantics == StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD

private fun PersistentStrengthTargetSummary.comparisonDisplayName(): String =
    if (isWeightedPullUp()) "$displayNameKo 총부하" else displayNameKo

private fun PersistentStrengthHistoryPoint.hasDirectTargetScaleObservation(): Boolean =
    directObservationType != "NONE" && sessionObservationMedianKg?.let { value -> value.isFinite() && value > 0.0 } == true

private fun observationLabel(value: String): String = when (value) {
    "DIRECT_1RM" -> "직접 1RM"
    "STRONG_NRM" -> "강한 nRM 관측"
    "RPE_MIXTURE_OBSERVATION" -> "RPE 확률 관측"
    "MISSING_RPE_LOWER_CENSORED", "MISSING_RPE_LOWER_BOUND" -> "RPE 미입력 하한 관측"
    "FAILURE_UPPER_CENSORED", "FAILURE_UPPER_BOUND" -> "실패 상한 관측"
    "CONSERVATIVE_LOWER_BOUND" -> "이전 버전 보수적 하한 관측"
    "NONE" -> "프록시 관측"
    else -> value
}

private fun kgOrDash(value: Double?): String = value?.takeIf(Double::isFinite)?.let { kg ->
    String.format(Locale.US, "%.1f kg", kg)
} ?: "-"

private fun signedKgOrDash(value: Double?): String = value?.takeIf(Double::isFinite)?.let { kg ->
    String.format(Locale.US, "%+.1f kg", kg)
} ?: "-"

private fun rangeOrDash(low: Double?, high: Double?): String =
    if (low?.isFinite() == true && high?.isFinite() == true) "${kgOrDash(low)} ~ ${kgOrDash(high)}" else "-"

private fun decimalOrDash(value: Double?): String = value?.takeIf(Double::isFinite)?.let { number ->
    String.format(Locale.US, "%.2f", number)
} ?: "-"

private fun percentOrDash(value: Double?): String = value?.takeIf(Double::isFinite)?.let { percent ->
    String.format(Locale.US, "%+.1f%%", percent)
} ?: "-"

private fun percentRangeOrDash(low: Double?, high: Double?): String =
    if (low?.isFinite() == true && high?.isFinite() == true) {
        "${percentOrDash(low)} ~ ${percentOrDash(high)}"
    } else {
        "-"
    }

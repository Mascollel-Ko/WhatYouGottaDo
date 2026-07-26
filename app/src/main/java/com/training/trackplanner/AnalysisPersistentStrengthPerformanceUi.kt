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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthHistoryPoint
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthPerformanceSummary
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthTargetSummary
import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
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
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun PersistentStrengthPerformanceCards(summary: PersistentStrengthPerformanceSummary?) {
    if (summary == null) return
    when (summary.lifecycleStatus) {
        StrengthAnalysisLifecycleStatus.REBUILDING -> {
            InfoCard("현재 근력 분석 모델로 과거 운동 기록을 재계산하고 있습니다.")
            return
        }
        StrengthAnalysisLifecycleStatus.REBUILD_FAILED -> {
            val diagnostic = summary.lifecycleDiagnosticCode?.let { "\n진단 코드: $it" }.orEmpty()
            InfoCard("현재 근력 분석을 재계산하지 못했습니다.$diagnostic")
            return
        }
        StrengthAnalysisLifecycleStatus.CURRENT -> Unit
    }
    if (summary.targets.isEmpty()) return
    var selectedKey by rememberSaveable { mutableStateOf(summary.targets.first().targetKey) }
    val selected = summary.targets.firstOrNull { target -> target.targetKey == selectedKey } ?: summary.targets.first()
    PersistentStrengthCapabilityCard(summary, selected) { target -> selectedKey = target.targetKey }
    PersistentStrengthHistoryCard(selected)
}

@Composable
private fun PersistentStrengthCapabilityCard(
    summary: PersistentStrengthPerformanceSummary,
    selected: PersistentStrengthTargetSummary,
    onSelected: (PersistentStrengthTargetSummary) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("persistent-strength-capability-card"),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("현재 수행능력 추정", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            PersistentTargetSelector(summary.targets, selected, onSelected)
            val medianLabel = if (selected.isWeightedPullUp()) "추정 총부하" else "사후분포 중앙값"
            Text("$medianLabel ${kgOrDash(selected.currentMedianKg)}", fontWeight = FontWeight.SemiBold)
            Text("80% 범위 ${rangeOrDash(selected.currentLow80Kg, selected.currentHigh80Kg)}")
            if (selected.isWeightedPullUp()) {
                Text("현재 체중 기준 추가중량 ${signedKgOrDash(selected.currentAddedWeightKg)}")
            }
            Text(
                selected.latestDirectObservationKg?.let { load ->
                    "최근 직접 1RM ${kgOrDash(load)} · ${selected.latestDirectObservationDate}"
                } ?: "최근 직접 1RM 기록 없음",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "관련 세션 ${selected.relevantSessionCount} · 직접 1RM ${selected.directObservationCount} · " +
                    "RPE 확률 관측 ${selected.knownRpeObservationCount} · 강한 nRM ${selected.strongNrmObservationCount} · " +
                    "프록시 혁신 ${selected.proxyObservationCount} · 실패 ${selected.failureObservationCount}",
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
}

@Composable
private fun PersistentStrengthHistoryCard(summary: PersistentStrengthTargetSummary) {
    var expanded by rememberSaveable(summary.targetKey) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("persistent-strength-history-card"),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("세션별 사후분포 기록", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (summary.history.isEmpty()) {
                InfoCard("처리된 완료 세션이 아직 없습니다.")
            } else {
                val spec = persistentStrengthHistoryChartSpec(summary)
                AnalysisChartSpecView(spec)
                ChartSeriesLegend(spec.lineSeries)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "기록 접기" else "세션 상세 보기")
                }
                if (expanded) {
                    summary.history.asReversed().take(12).forEach { point ->
                        PersistentStrengthHistoryRow(summary, point)
                    }
                    if (summary.localExerciseDetails.isNotEmpty()) {
                        Text("관련 운동 자체 추정", fontWeight = FontWeight.SemiBold)
                        summary.localExerciseDetails.forEach { local ->
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
    point: PersistentStrengthHistoryPoint
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
            val diagnostic = summary.lifecycleDiagnosticCode?.let { "\n진단 코드: $it" }.orEmpty()
            InfoCard("현재 근력 분석을 재계산하지 못했습니다.$diagnostic")
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

internal fun persistentStrengthHistoryChartSpec(summary: PersistentStrengthTargetSummary): ChartSpec {
    val posterior = summary.history.map { point -> TrendDataPoint(point.sessionDate, point.posteriorMedianKg) }
    val observation = summary.history.map { point -> TrendDataPoint(point.sessionDate, point.sessionObservationMedianKg) }
    val interval = summary.history.mapNotNull { point ->
        val low = point.posteriorLow80Kg
        val high = point.posteriorHigh80Kg
        if (low == null || high == null) null else IntervalPoint(point.sessionDate, low, high)
    }
    val yRange = TrendChartRange.values(
        posterior.mapNotNull(TrendDataPoint::value) + observation.mapNotNull(TrendDataPoint::value) +
            interval.flatMap { point -> listOf(point.lower, point.upper) }
    )
    return ChartSpec(
        type = ChartType.LINE,
        title = "${summary.displayNameKo} 수행능력 추정",
        lineSeries = listOf(
            ChartSeries("현재 수행능력 posterior", posterior),
            ChartSeries("세트 기반 세션 관측", observation, connectPoints = false)
        ),
        intervalBand = IntervalBand("posterior 80% 범위", interval),
        yMin = yRange?.first,
        yMax = yRange?.second,
        timeGranularity = ChartTimeGranularity.DAILY,
        xDomain = AnalysisChartTemporalPolicy.dailyDomain(summary.history.map(PersistentStrengthHistoryPoint::sessionDate)),
        valueUnit = "kg"
    )
}

private fun PersistentStrengthTargetSummary.isWeightedPullUp(): Boolean =
    loadSemantics == StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD

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

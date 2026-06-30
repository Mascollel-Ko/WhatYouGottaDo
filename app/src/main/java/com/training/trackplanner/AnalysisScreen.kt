package com.training.trackplanner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class AnalysisDestination(val title: String, val body: String) {
    FATIGUE("오늘 컨디션 및 피로도 분석", "현재 피로 상태와 피로 축별 주요 기여 운동을 봅니다."),
    BADMINTON("배드민턴 전이 분석", "배드민턴 관련 훈련 자극과 주간 흐름을 봅니다."),
    STRENGTH("근력운동 추이 분석", "주요 리프팅, 근육군, 반복수 구간 흐름을 봅니다.")
}

@Composable
internal fun AnalysisScreen(viewModel: TrainingViewModel) {
    val stats by viewModel.analysisStats.collectAsState()
    val readiness by viewModel.todayReadinessSummary.collectAsState()
    val todayStatus by viewModel.phaseAwareTodayStatus.collectAsState()
    val fatigueAnalysis by viewModel.fatigueAnalysisState.collectAsState()
    val badmintonTransfer by viewModel.badmintonTransferSummary.collectAsState()
    val coachInsight by viewModel.coachAnalysisInsight.collectAsState()
    val coachingSignals by viewModel.coachingSignalsSummary.collectAsState()
    val performanceTrend by viewModel.performanceTrendSummary.collectAsState()
    var destination by rememberSaveable { mutableStateOf<AnalysisDestination?>(null) }
    BackHandler(enabled = destination != null) { destination = null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = destination?.title ?: "분석",
                body = destination?.body ?: "필요한 분석 화면으로 들어가 핵심 지표를 확인합니다."
            )
        }
        if (destination == null) {
            item {
                AnalysisHubContent(
                    stats = stats,
                    onFatigueClick = { destination = AnalysisDestination.FATIGUE },
                    onBadmintonClick = { destination = AnalysisDestination.BADMINTON },
                    onStrengthClick = { destination = AnalysisDestination.STRENGTH }
                )
            }
        } else {
            item { AnalysisBackButton(onClick = { destination = null }) }
            item {
                when (destination) {
                    AnalysisDestination.FATIGUE -> FatigueAndConditionAnalysisContent(
                        readiness = readiness,
                        todayStatus = todayStatus,
                        fatigueAnalysis = fatigueAnalysis,
                        coachInsight = coachInsight,
                        coachingSignals = coachingSignals,
                        performanceTrend = performanceTrend,
                        onPeriodChange = viewModel::selectFatigueAnalysisPeriod,
                        onFatigueTargetToggle = viewModel::toggleFatigueTrendTarget,
                        onContributionTargetChange = viewModel::selectFatigueContributionTarget,
                        onContributionGroupingChange = viewModel::selectFatigueContributionGrouping,
                        onContributionSourcesApply = viewModel::selectFatigueContributionSources
                    )
                    AnalysisDestination.BADMINTON -> BadmintonTransferAnalysisContent(
                        coachInsight = coachInsight,
                        badmintonTransfer = badmintonTransfer,
                        performanceTrend = performanceTrend
                    )
                    AnalysisDestination.STRENGTH -> StrengthTrendAnalysisContent(performanceTrend)
                    null -> Unit
                }
            }
        }
    }
}

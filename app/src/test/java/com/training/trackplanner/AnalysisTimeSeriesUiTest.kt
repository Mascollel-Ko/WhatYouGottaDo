package com.training.trackplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.lab.BayesianIrfPoint
import com.training.trackplanner.analysis.lab.BayesianResponseIrf
import com.training.trackplanner.analysis.lab.BayesianTimeSeriesModel
import com.training.trackplanner.analysis.lab.BayesianTimeSeriesResult
import com.training.trackplanner.analysis.lab.TimeSeriesAnalysisRequest
import com.training.trackplanner.analysis.lab.TimeSeriesAnalysisUiState
import com.training.trackplanner.analysis.lab.TimeSeriesExecutionStage
import com.training.trackplanner.analysis.lab.TimeSeriesPerformanceProfile
import com.training.trackplanner.analysis.lab.TimeSeriesPreflight
import com.training.trackplanner.analysis.lab.TimeSeriesPreflightBlocker
import com.training.trackplanner.analysis.lab.TimeSeriesPreflightBlockerCode
import com.training.trackplanner.analysis.lab.TimeSeriesPreflightStatus
import com.training.trackplanner.analysis.lab.TimeSeriesUnavailableReason
import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.trends.CompositeTrendSeries
import com.training.trackplanner.analysis.trends.PerformanceTrendSummary
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class AnalysisTimeSeriesUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val request = TimeSeriesAnalysisRequest(
        TrendMetricId.BADMINTON_TRAINING,
        listOf(TrendMetricId.FATIGUE_COMPOSITE),
        emptyList(),
        2
    )

    @Test
    fun blockedPreflightDisablesAnalyzeAndShowsUsableHistory() {
        val preflight = readyPreflight().copy(
            status = TimeSeriesPreflightStatus.BLOCKED,
            transformedUsableWeeks = 17,
            requestedEstimatorRows = 14,
            blockers = listOf(
                TimeSeriesPreflightBlocker(
                    TimeSeriesPreflightBlockerCode.INSUFFICIENT_ROWS_AFTER_LAG_HORIZON,
                    observed = 14,
                    required = 24
                )
            )
        )
        content(TimeSeriesAnalysisUiState.PreflightReady(request, preflight))

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("변환 후 공통 사용 가능: 17주", substring = true))
        compose.onNodeWithText("변환 후 공통 사용 가능: 17주", substring = true).assertIsDisplayed()
        compose.onNodeWithText("분석하기").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(
            hasText("시차와 반응 기간을 적용한 추정 행은 14개이며 현재 조합에는 최소 24개가 필요합니다.")
        )
        compose.onNodeWithText("시차와 반응 기간을 적용한 추정 행은 14개이며 현재 조합에는 최소 24개가 필요합니다.")
            .assertExists()
    }

    @Test
    fun runningStateIsVisibleImmediatelyAndAnalyzeIsDisabled() {
        content(TimeSeriesAnalysisUiState.Running(1, request, readyPreflight(), TimeSeriesExecutionStage.FITTING_MODEL))

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("탐색적 분석을 실행하고 있습니다."))
        compose.onNodeWithText("탐색적 분석을 실행하고 있습니다.").assertIsDisplayed()
        compose.onNodeWithText("탐색적 시차 모형을 맞추는 중입니다.").assertIsDisplayed()
        compose.onNodeWithText("분석하기").assertIsNotEnabled()
    }

    @Test
    fun analyzeClickSubmitsRequestImmediately() {
        val analyzeInvoked = AtomicBoolean(false)
        content(
            state = TimeSeriesAnalysisUiState.PreflightReady(request, readyPreflight()),
            onAnalyze = { analyzeInvoked.set(true) }
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("분석하기"))
        compose.onNode(hasText("분석하기") and hasClickAction()).assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(analyzeInvoked.get())
    }

    @Test
    fun successShowsExploratoryTypeHorizonObservationsAndUncertainty() {
        content(
            TimeSeriesAnalysisUiState.Success(
                request,
                successResult(),
                readyPreflight(),
                performance()
            )
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("탐색적 시차 분석 결과"))
        compose.onNodeWithText("탐색적 시차 분석 결과").assertIsDisplayed()
        compose.onNodeWithText("실행 분석: 탐색적 국소 투영 호환 모형").assertIsDisplayed()
        compose.onNodeWithText("요청 horizon: 2주 · 실제 horizon: 2주").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("80% 불확실성 구간은", substring = true))
        compose.onNodeWithText("80% 불확실성 구간은", substring = true).assertExists()
        compose.onNodeWithText("Bayesian 시계열 분석 결과").assertDoesNotExist()
    }

    @Test
    fun unavailableReasonIsProminent() {
        content(
            TimeSeriesAnalysisUiState.Unavailable(
                request,
                TimeSeriesUnavailableReason.ALL_ESTIMATORS_FAILED,
                "internal detail",
                null,
                readyPreflight()
            )
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("분석할 수 없습니다"))
        compose.onNodeWithText("분석할 수 없습니다").assertIsDisplayed()
        compose.onNodeWithText("현재 자료에서는 통과한 탐색 모형이 없습니다.").assertIsDisplayed()
    }

    @Test
    fun failedStateOffersRetryWithoutRawException() {
        val retried = AtomicBoolean(false)
        content(
            TimeSeriesAnalysisUiState.Failed(request, "safe", "TS-AB12", readyPreflight()),
            onRetry = { retried.set(true) }
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("분석 실행 오류"))
        compose.onNodeWithText("진단 코드: TS-AB12").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()
        assertTrue(retried.get())
    }

    @Test
    fun exploratoryScreenFitsLargeTextDarkTheme() {
        content(
            TimeSeriesAnalysisUiState.PreflightReady(request, readyPreflight()),
            fontScale = 1.3f,
            darkTheme = true
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("탐색적 시차 분석"))
        compose.onNodeWithText("탐색적 시차 분석").assertIsDisplayed()
        compose.onNodeWithText("Bayesian 시계열 분석").assertDoesNotExist()
    }

    private fun content(
        state: TimeSeriesAnalysisUiState = TimeSeriesAnalysisUiState.Idle,
        stateProvider: () -> TimeSeriesAnalysisUiState = { state },
        onAnalyze: (TimeSeriesAnalysisRequest) -> Unit = {},
        onRetry: () -> Unit = {},
        fontScale: Float = 1f,
        darkTheme: Boolean = false
    ) {
        compose.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, fontScale)) {
                TrainingTrackPlannerTheme(darkTheme = darkTheme) {
                    Box(Modifier.width(360.dp).height(720.dp)) {
                        LazyColumn {
                            item {
                                LaggedTimeSeriesAnalysisContent(
                                    summary = summary(),
                                    executionState = stateProvider(),
                                    onRequestChanged = {},
                                    onAnalyze = onAnalyze,
                                    onRetry = onRetry,
                                    onCancel = {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun readyPreflight() = TimeSeriesPreflight(
        status = TimeSeriesPreflightStatus.READY,
        availableFrom = LocalDate.parse("2026-01-05"),
        availableUntil = LocalDate.parse("2026-08-10"),
        alignedWeeks = 32,
        transformedUsableWeeks = 31,
        requestedEstimatorRows = 28,
        requiredMinimumRows = 24,
        maximumFeasibleHorizon = 2,
        blockers = emptyList(),
        warnings = emptyList()
    )

    private fun performance() = TimeSeriesPerformanceProfile(
        stageDurationsMillis = TimeSeriesExecutionStage.entries.associateWith { 1L },
        candidateCount = 8,
        responseCount = 1,
        lagCandidateCount = 4,
        horizonCount = 3,
        estimatedModelFitUpperBound = 480
    )

    private fun successResult() = BayesianTimeSeriesResult(
        request = request,
        model = BayesianTimeSeriesModel.BAYESIAN_LOCAL_PROJECTION,
        responses = listOf(
            BayesianResponseIrf(
                TrendMetricId.FATIGUE_COMPOSITE,
                listOf(BayesianIrfPoint(0, 0.2, 0.1, 0.3, 28), BayesianIrfPoint(1, 0.1, -0.1, 0.2, 27))
            )
        ),
        usedHorizon = 2,
        alignment = null,
        integrationDiagnostics = emptyList(),
        cointegration = null,
        lagPosterior = null,
        automaticEndogenous = emptyList(),
        automaticSelectionDiagnostics = emptyList(),
        choleskyOrder = emptyList(),
        choleskySensitivity = null,
        transformations = emptyMap(),
        confidence = AnalysisConfidence.MEDIUM,
        warnings = emptyList(),
        summary = "internal"
    )

    private fun summary(): PerformanceTrendSummary {
        val start = LocalDate.parse("2026-01-05")
        val x = (0 until 32).map { index -> TrendDataPoint(start.plusWeeks(index.toLong()), (index % 7).toDouble()) }
        val y = (0 until 32).map { index -> TrendDataPoint(start.plusWeeks(index.toLong()), (index % 5).toDouble()) }
        fun composite(metric: TrendMetricId, points: List<TrendDataPoint>) = CompositeTrendSeries(
            title = metric.name,
            metricId = metric,
            dataPoints = points,
            forecastRange = null,
            confidence = AnalysisConfidence.MEDIUM,
            dataSufficiency = "enough"
        )
        return PerformanceTrendSummary(
            strengthPerformanceSeries = composite(TrendMetricId.STRENGTH_PERFORMANCE, emptyList()),
            badmintonTrainingSeries = composite(TrendMetricId.BADMINTON_TRAINING, x),
            fatigueCompositeSeries = composite(TrendMetricId.FATIGUE_COMPOSITE, y),
            forecastRanges = emptyMap(),
            trendSentence = "",
            confidence = AnalysisConfidence.MEDIUM,
            detailSections = emptyList(),
            dashboardChartSpecs = emptyList(),
            metricSeries = mapOf(
                TrendMetricId.BADMINTON_TRAINING to x,
                TrendMetricId.FATIGUE_COMPOSITE to y
            )
        )
    }
}

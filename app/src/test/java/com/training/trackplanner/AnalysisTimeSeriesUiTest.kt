package com.training.trackplanner

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.lab.StrictBayesianLabResult
import com.training.trackplanner.analysis.lab.StrictBayesianLabUiState
import com.training.trackplanner.analysis.lab.StrictFailureDiagnostics
import com.training.trackplanner.analysis.lab.StrictFailureStage
import com.training.trackplanner.analysis.lab.StrictLabAnalysisRequest
import com.training.trackplanner.analysis.lab.StrictLabAnalysisMode
import com.training.trackplanner.analysis.lab.StrictLabBlocker
import com.training.trackplanner.analysis.lab.StrictLabBlockerCode
import com.training.trackplanner.analysis.lab.StrictLabExecutionStage
import com.training.trackplanner.analysis.lab.StrictLabFailureCode
import com.training.trackplanner.analysis.lab.StrictLabFeatureCatalog
import com.training.trackplanner.analysis.lab.StrictLabFeatureOption
import com.training.trackplanner.analysis.lab.StrictLabPreflight
import com.training.trackplanner.analysis.lab.StrictLabResponse
import com.training.trackplanner.analysis.lab.StrictLabResponsePoint
import com.training.trackplanner.analysis.lab.StrictRelaxationRoute
import com.training.trackplanner.analysis.lab.StrictSamplingReliabilityMode
import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.strictbayes.StrictPosteriorSummary
import com.training.trackplanner.analysis.lab.weekly.AnalysisFeatureFamily
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import java.time.LocalDate
import java.util.Locale
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

    private val request = StrictLabAnalysisRequest(X, listOf(Y), emptyList(), 2)

    @Test
    fun blockedPreflightDisablesAnalyzeAndShowsStrictReason() {
        val preflight = readyPreflight().copy(
            blockers = listOf(StrictLabBlocker(StrictLabBlockerCode.PHASE_A_INELIGIBLE))
        )
        content(StrictBayesianLabUiState.PreflightReady(request, preflight))

        compose.onNodeWithText("완료된 주간 기록: 32주").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("엄격 Bayesian 분석하기").assertIsNotEnabled()
        compose.onNodeWithText("현재 기록으로 승인된 엄격 모형 입력을 만들 수 없습니다.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun runningStateIsVisibleImmediatelyAndAnalyzeIsDisabled() {
        content(
            StrictBayesianLabUiState.Running(
                1,
                request,
                readyPreflight(),
                StrictLabExecutionStage.SAMPLING_POSTERIOR
            )
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("엄격 Bayesian 분석을 실행하고 있습니다."))
        compose.onNodeWithText("엄격 Bayesian 분석을 실행하고 있습니다.").assertIsDisplayed()
        compose.onNodeWithText("posterior를 표본추출하는 중입니다.").assertIsDisplayed()
        compose.onNodeWithText("엄격 Bayesian 분석하기").assertIsNotEnabled()
    }

    @Test
    fun analyzeClickSubmitsStrictRequestImmediately() {
        val analyzeInvoked = AtomicBoolean(false)
        content(
            state = StrictBayesianLabUiState.PreflightReady(request, readyPreflight()),
            onAnalyze = { _, mode ->
                assertTrue(mode == StrictLabAnalysisMode.STRICT)
                analyzeInvoked.set(true)
            }
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("엄격 Bayesian 분석하기"))
        compose.onNode(hasText("엄격 Bayesian 분석하기") and hasClickAction()).assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(analyzeInvoked.get())
    }

    @Test
    fun successShowsPosteriorMedianOfficialLagAndUncertainty() {
        content(
            StrictBayesianLabUiState.Success(
                request,
                successResult(),
                readyPreflight()
            )
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bayesian 분석 결과"))
        compose.onNodeWithText("Bayesian 분석 결과").assertIsDisplayed()
        compose.onNodeWithText("시차 posterior: 1주 70.000%, 2주 30.000%").assertIsDisplayed()
        compose.onNodeWithText("1주 후: 중앙값 0.100 · 80% 구간 -2.000~2.200").assertIsDisplayed()
        compose.onNodeWithText("posterior 중앙값과 80% 구간입니다.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("완화된 분석 기준으로 계산된 탐색적 결과입니다.").assertDoesNotExist()
    }

    @Test
    fun typedFailureOffersDetailsAndRetryWithoutReplacingPosteriorWithFallback() {
        val retried = AtomicBoolean(false)
        content(
            StrictBayesianLabUiState.Failed(
                request = request,
                preflight = readyPreflight(),
                failure = StrictFailureDiagnostics(
                    code = StrictLabFailureCode.MCMC_CONVERGENCE_FAILED,
                    stage = StrictFailureStage.STABILIZATION,
                    primaryReason = "posterior chain이 수렴 기준을 통과하지 못했습니다.",
                    technicalDetails = listOf("rhat exceeded policy"),
                    diagnosticId = "SB-AB12"
                )
            ),
            onRetry = { retried.set(true) }
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("분석을 완료하지 못했습니다"))
        compose.onNodeWithText("분석을 완료하지 못했습니다").assertIsDisplayed()
        compose.onNodeWithText("자세히").performClick()
        compose.onNodeWithText("실패 로그").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("진단 코드: SB-AB12"))
        compose.onNodeWithText("진단 코드: SB-AB12").assertIsDisplayed()
        compose.onNodeWithText("rhat exceeded policy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()
        assertTrue(retried.get())
    }

    @Test
    fun relaxedRetryIsOnlyShownForEligibleReliabilityFailures() {
        val relaxed = AtomicBoolean(false)
        content(
            StrictBayesianLabUiState.Failed(
                request,
                readyPreflight(),
                StrictFailureDiagnostics(
                    StrictLabFailureCode.MONTE_CARLO_PRECISION_NOT_REACHED,
                    StrictFailureStage.PRODUCTION,
                    "posterior precision failure",
                    availableRelaxationRoutes = setOf(StrictRelaxationRoute.RELAX_SAMPLING_RELIABILITY)
                )
            ),
            onRelaxedRetry = { relaxed.set(true) }
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("완화해서 결과 보기"))
        compose.onNodeWithText("완화해서 결과 보기").assertIsDisplayed().performClick()
        assertTrue(relaxed.get())
    }

    @Test
    fun numericalFailureDoesNotOfferRelaxedRetry() {
        content(
            StrictBayesianLabUiState.Failed(
                request,
                readyPreflight(),
                StrictFailureDiagnostics(
                    StrictLabFailureCode.NUMERICAL_SPD_FAILURE,
                    StrictFailureStage.NUMERICAL,
                    "strict SPD failure"
                )
            )
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("분석을 완료하지 못했습니다"))
        compose.onNodeWithText("분석을 완료하지 못했습니다").assertIsDisplayed()
        compose.onNodeWithText("완화해서 결과 보기").assertDoesNotExist()
    }

    @Test
    fun relaxedSuccessIsMarkedWithoutChangingStrictSuccessPresentation() {
        content(
            StrictBayesianLabUiState.Success(
                request,
                successResult().copy(
                    analysisMode = StrictLabAnalysisMode.RELAXED,
                    samplingReliabilityMode = StrictSamplingReliabilityMode.RELAXED
                ),
                readyPreflight()
            )
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("완화된 분석 기준으로 계산된 탐색적 결과입니다."))
        compose.onNodeWithText("완화된 분석 기준으로 계산된 탐색적 결과입니다.").assertIsDisplayed()
        compose.onNodeWithText("Bayesian 분석 결과").assertIsDisplayed()
    }

    @Test
    fun relaxedModeCanBeSelectedBeforeAnalysis() {
        var selectedMode: StrictLabAnalysisMode? = null
        content(
            state = StrictBayesianLabUiState.PreflightReady(request, readyPreflight()),
            onAnalyze = { _, mode -> selectedMode = mode }
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("완화"))
        compose.onNodeWithText("완화").performClick()
        compose.onNodeWithText("일부 표현·모형 단순화·표본추출 기준을 완화한 탐색용 분석입니다.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("완화 Bayesian 분석하기").performScrollTo().performClick()

        assertTrue(selectedMode == StrictLabAnalysisMode.RELAXED)
    }

    @Test
    fun relaxedFailureShowsExportButNoFurtherRelaxationAtLargeFont() {
        content(
            state = StrictBayesianLabUiState.Failed(
                request,
                readyPreflight(),
                StrictFailureDiagnostics(
                    StrictLabFailureCode.MCMC_CONVERGENCE_FAILED,
                    StrictFailureStage.PRODUCTION,
                    "relaxed posterior failure",
                    analysisMode = StrictLabAnalysisMode.RELAXED
                )
            ),
            fontScale = 1.3f
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("실패 기록 내보내기"))
        compose.onNodeWithText("실패 기록 내보내기").assertIsDisplayed()
        compose.onNodeWithText("자세히").assertIsDisplayed().performClick()
        compose.onNodeWithText("실패 로그").assertIsDisplayed()
        compose.onNodeWithText("완화해서 결과 보기").assertDoesNotExist()
    }

    @Test
    fun strictScreenFitsLargeTextDarkTheme() {
        content(
            StrictBayesianLabUiState.PreflightReady(request, readyPreflight()),
            fontScale = 1.3f,
            darkTheme = true
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("엄격 Bayesian 시차 분석"))
        compose.onNodeWithText("엄격 Bayesian 시차 분석").assertIsDisplayed()
        compose.onNodeWithText("탐색적 시차 분석 결과").assertDoesNotExist()
    }

    private fun content(
        state: StrictBayesianLabUiState = StrictBayesianLabUiState.Idle,
        onAnalyze: (StrictLabAnalysisRequest, StrictLabAnalysisMode) -> Unit = { _, _ -> },
        onRetry: () -> Unit = {},
        onRelaxedRetry: () -> Unit = {},
        fontScale: Float = 1f,
        darkTheme: Boolean = false
    ) {
        compose.setContent {
            val currentDensity = LocalDensity.current
            val baseContext = LocalContext.current
            val koreanConfiguration = remember {
                Configuration(baseContext.resources.configuration).apply { setLocale(Locale.KOREAN) }
            }
            val koreanContext = remember { baseContext.createConfigurationContext(koreanConfiguration) }
            CompositionLocalProvider(
                LocalContext provides koreanContext,
                LocalConfiguration provides koreanConfiguration,
                LocalDensity provides Density(currentDensity.density, fontScale)
            ) {
                TrainingTrackPlannerTheme(darkTheme = darkTheme) {
                    Box(Modifier.width(360.dp).height(720.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            LaggedTimeSeriesAnalysisContent(
                                featureCatalog = featureCatalog(),
                                executionState = state,
                                onRequestChanged = {},
                                onAnalyze = onAnalyze,
                                onRetry = onRetry,
                                onRelaxedRetry = onRelaxedRetry,
                                onCancel = {}
                            )
                        }
                    }
                }
            }
        }
    }

    private fun readyPreflight() = StrictLabPreflight(
        snapshotFingerprint = "snapshot-v1",
        availableFrom = LocalDate.parse("2026-01-05"),
        availableUntil = LocalDate.parse("2026-08-10"),
        closedWeeks = 32,
        blockers = emptyList(),
        warnings = emptyList()
    )

    private fun successResult(): StrictBayesianLabResult {
        val diagnostics = StrictPosteriorSummary(
            mean = 0.1,
            median = 0.1,
            lower80 = -2.0,
            upper80 = 2.2,
            rhat = 1.0,
            bulkEss = 500.0,
            tailEss = 500.0,
            mcseToSd = 0.04
        )
        return StrictBayesianLabResult(
            request = request,
            responses = listOf(
                StrictLabResponse(
                    feature = Y,
                    displayName = "피로 종합",
                    points = listOf(StrictLabResponsePoint(1, 0.1, -2.0, 2.2, diagnostics))
                )
            ),
            officialLagProbability = mapOf(1 to 0.7, 2 to 0.3),
            simplificationDiagnostics = emptyList(),
            summary = "불확실성이 큰 posterior입니다.",
            preparedInputFingerprint = "prepared",
            posteriorFingerprint = "posterior"
        )
    }

    private fun featureCatalog(): StrictLabFeatureCatalog {
        val from = LocalDate.parse("2026-01-05")
        val until = LocalDate.parse("2026-08-10")
        val x = StrictLabFeatureOption(
            X,
            "배드민턴 훈련 부하",
            AnalysisFeatureFamily.TRAINING_FLOW,
            32,
            from,
            until,
            enabled = true,
            disabledReason = null
        )
        val y = StrictLabFeatureOption(
            Y,
            "피로 종합",
            AnalysisFeatureFamily.RECOVERY_CHECK_IN,
            32,
            from,
            until,
            enabled = true,
            disabledReason = null
        )
        return StrictLabFeatureCatalog(
            xFeatures = listOf(x, y),
            responseFeatures = listOf(y),
            controlFeatures = listOf(x, y),
            snapshotFingerprint = "snapshot-v1"
        )
    }

    private companion object {
        val X: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD)
        val Y: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.FATIGUE_COMPOSITE)
    }
}

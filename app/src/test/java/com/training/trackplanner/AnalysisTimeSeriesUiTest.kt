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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.lab.StrictBayesianLabResult
import com.training.trackplanner.analysis.lab.StrictBayesianLabUiState
import com.training.trackplanner.analysis.lab.StrictFailureDiagnostics
import com.training.trackplanner.analysis.lab.StrictFailureStage
import com.training.trackplanner.analysis.lab.StrictLabAnalysisRequest
import com.training.trackplanner.analysis.lab.StrictLabBlocker
import com.training.trackplanner.analysis.lab.StrictLabBlockerCode
import com.training.trackplanner.analysis.lab.StrictLabExecutionStage
import com.training.trackplanner.analysis.lab.StrictLabFailureCode
import com.training.trackplanner.analysis.lab.StrictLabFeatureCatalog
import com.training.trackplanner.analysis.lab.StrictLabFeatureOption
import com.training.trackplanner.analysis.lab.StrictLabPreflight
import com.training.trackplanner.analysis.lab.StrictLabResponse
import com.training.trackplanner.analysis.lab.StrictLabResponsePoint
import com.training.trackplanner.analysis.lab.StrictSamplingAssessment
import com.training.trackplanner.analysis.lab.StrictSamplingDiagnosticClassification
import com.training.trackplanner.analysis.lab.StrictSamplingDiagnosticWindow
import com.training.trackplanner.analysis.lab.pipeline.AnalysisFeatureKey
import com.training.trackplanner.analysis.lab.strictbayes.StrictPosteriorSummary
import com.training.trackplanner.analysis.lab.strictbayes.StrictSamplingPolicy
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
    fun blockedPreflightDisablesAnalyze() {
        val preflight = readyPreflight().copy(
            blockers = listOf(StrictLabBlocker(StrictLabBlockerCode.PHASE_A_INELIGIBLE))
        )
        content(StrictBayesianLabUiState.PreflightReady(request, preflight))

        compose.onNodeWithText("완료된 주간 기록: 32주").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bayesian 분석하기").assertIsNotEnabled()
    }

    @Test
    fun runningStateIsImmediateAndAnalyzeIsDisabled() {
        content(
            StrictBayesianLabUiState.Running(
                1,
                request,
                readyPreflight(),
                StrictLabExecutionStage.SAMPLING_POSTERIOR
            )
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bayesian 분석을 실행하고 있습니다."))
        compose.onNodeWithText("Bayesian 분석을 실행하고 있습니다.").assertIsDisplayed()
        compose.onNodeWithText("Bayesian 분석하기").assertIsNotEnabled()
    }

    @Test
    fun analyzeUsesOneAutomaticActionWithoutModeSelector() {
        val invoked = AtomicBoolean(false)
        content(
            StrictBayesianLabUiState.PreflightReady(request, readyPreflight()),
            onAnalyze = { invoked.set(true) }
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bayesian 분석하기"))
        compose.onNode(hasText("Bayesian 분석하기") and hasClickAction()).assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(invoked.get())
        compose.onNodeWithText("분석 기준").assertDoesNotExist()
        compose.onNodeWithText("완화해서 결과 보기").assertDoesNotExist()
    }

    @Test
    fun availableStrictShowsPosteriorClassificationDetailsAndExport() {
        content(StrictBayesianLabUiState.Available(request, result(StrictSamplingDiagnosticClassification.STRICT), readyPreflight()))

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bayesian 분석 결과"))
        compose.onNodeWithText("앱에서 분석에 맞게 변환한 배드민턴 훈련 부하 값이, 분석에 사용된 공통 주간 기록의 표본표준편차 1개만큼 증가한 경우")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("strict-response-irf-${Y.value}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1주 후: 중앙값 0.100 · 80% 구간 -2.000~2.200").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("MCMC 진단: 엄격 기준 충족").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("시차 posterior: 1주 70.000%, 2주 30.000%").assertDoesNotExist()
        compose.onNodeWithText("분석 상세").performScrollTo().performClick()
        compose.onNodeWithText("FINAL STATUS").assertIsDisplayed()
        compose.onAllNodesWithText("내보내기").assertCountEquals(2)
    }

    @Test
    fun availableLimitedStillShowsPosteriorValues() {
        content(StrictBayesianLabUiState.Available(request, result(StrictSamplingDiagnosticClassification.LIMITED), readyPreflight()))

        compose.onNodeWithText("MCMC 진단: 제한적").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1주 후: 중앙값 0.100 · 80% 구간 -2.000~2.200").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("분석할 수 없음").assertDoesNotExist()
    }

    @Test
    fun multipleResponsesRenderSeparateInterpretationsAndGraphs() {
        val result = result(
            StrictSamplingDiagnosticClassification.STRICT,
            listOf(Y to "피로 종합", Y2 to "준비도")
        )
        content(StrictBayesianLabUiState.Available(request, result, readyPreflight()))

        compose.onNodeWithTag("strict-response-irf-${Y.value}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("strict-response-irf-${Y2.value}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("피로 종합의 반응은", substring = true).assertExists()
        compose.onNodeWithText("준비도의 반응은", substring = true).assertExists()
    }

    @Test
    fun unavailableUsesBlockerLanguageAndUniversalDetails() {
        val retried = AtomicBoolean(false)
        content(
            StrictBayesianLabUiState.Unavailable(
                request,
                readyPreflight(),
                StrictFailureDiagnostics(
                    StrictLabFailureCode.NUMERICAL_SPD_FAILURE,
                    StrictFailureStage.NUMERICAL,
                    "유한한 posterior를 만들 수 없습니다.",
                    diagnosticId = "SB-AB12"
                )
            ),
            onRetry = { retried.set(true) }
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("분석할 수 없음"))
        compose.onNodeWithText("분석할 수 없음").assertIsDisplayed()
        compose.onNodeWithText("분석 상세").performClick()
        compose.onAllNodesWithText("Terminal blocker: NUMERICAL_SPD_FAILURE").assertCountEquals(2)
        compose.onAllNodesWithText("내보내기").assertCountEquals(2)
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("다시 시도").performClick()
        assertTrue(retried.get())
    }

    @Test
    fun detailReportScrollsAt360DpAndLargeFont() {
        content(
            StrictBayesianLabUiState.Available(request, result(StrictSamplingDiagnosticClassification.RELAXED), readyPreflight()),
            fontScale = 1.3f,
            darkTheme = true
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("MCMC 진단: 완화 기준 충족"))
        compose.onNodeWithText("분석 상세").performScrollTo().performClick()
        compose.onNodeWithText("SAMPLING DIAGNOSTICS").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("내보내기").assertCountEquals(2)
    }

    private fun content(
        state: StrictBayesianLabUiState,
        onAnalyze: (StrictLabAnalysisRequest) -> Unit = {},
        onRetry: () -> Unit = {},
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
                                onCancel = {}
                            )
                        }
                    }
                }
            }
        }
    }

    private fun readyPreflight() = StrictLabPreflight(
        "snapshot-v1",
        LocalDate.parse("2026-01-05"),
        LocalDate.parse("2026-08-10"),
        32,
        emptyList(),
        emptyList()
    )

    private fun result(
        classification: StrictSamplingDiagnosticClassification,
        responseFeatures: List<Pair<AnalysisFeatureKey, String>> = listOf(Y to "피로 종합")
    ): StrictBayesianLabResult {
        val posterior = StrictPosteriorSummary(0.1, 0.1, -2.0, 2.2, 1.0, 500.0, 500.0, 0.04)
        val strict = StrictSamplingPolicy.appRuntime().snapshot("STRICT")
        val relaxed = StrictSamplingPolicy.relaxedAppRuntime().snapshot("RELAXED")
        val assessment = StrictSamplingAssessment(
            classification,
            strict,
            relaxed,
            listOf(
                StrictSamplingDiagnosticWindow(
                    StrictFailureStage.PRODUCTION,
                    500,
                    if (classification == StrictSamplingDiagnosticClassification.STRICT) 1.005 else 1.03,
                    "response[fatigue,1]",
                    120.0,
                    110.0,
                    0.09,
                    classification == StrictSamplingDiagnosticClassification.STRICT,
                    classification != StrictSamplingDiagnosticClassification.LIMITED
                )
            ),
            500,
            500,
            classification == StrictSamplingDiagnosticClassification.STRICT,
            classification != StrictSamplingDiagnosticClassification.LIMITED,
            false
        )
        val responses = responseFeatures.map { (feature, name) ->
            StrictLabResponse(feature, name, listOf(StrictLabResponsePoint(1, 0.1, -2.0, 2.2, posterior)))
        }
        val lagProbability = mapOf(1 to 0.7, 2 to 0.3)
        val shock = com.training.trackplanner.analysis.lab.StrictLabShockDefinitionFactory
            .standardizedTrainingRowShock(X, "배드민턴 훈련 부하")
        return StrictBayesianLabResult(
            request = request,
            responses = responses,
            officialLagProbability = lagProbability,
            shockDefinition = shock,
            presentation = com.training.trackplanner.analysis.lab.BayesianResponsePresentationFactory.create(
                shock,
                responses,
                lagProbability,
                classification
            ),
            simplificationDiagnostics = emptyList(),
            summary = "불확실성이 큰 posterior입니다.",
            preparedInputFingerprint = "prepared",
            posteriorFingerprint = "posterior",
            samplingAssessment = assessment,
            closedWeeks = 32,
            availableFrom = LocalDate.parse("2026-01-05"),
            availableUntil = LocalDate.parse("2026-08-10"),
            commonRows = 20,
            selectedPmax = 2,
            rowPlanFingerprint = "rows",
            scalingFingerprint = "scaling",
            designFingerprint = "design"
        )
    }

    private fun featureCatalog(): StrictLabFeatureCatalog {
        val from = LocalDate.parse("2026-01-05")
        val until = LocalDate.parse("2026-08-10")
        val x = StrictLabFeatureOption(X, "배드민턴 훈련 부하", AnalysisFeatureFamily.TRAINING_FLOW, 32, from, until, true, null)
        val y = StrictLabFeatureOption(Y, "피로 종합", AnalysisFeatureFamily.RECOVERY_CHECK_IN, 32, from, until, true, null)
        val y2 = StrictLabFeatureOption(Y2, "준비도", AnalysisFeatureFamily.RECOVERY_CHECK_IN, 32, from, until, true, null)
        return StrictLabFeatureCatalog(listOf(x, y, y2), listOf(y, y2), listOf(x, y, y2), "snapshot-v1")
    }

    private companion object {
        val X: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.BADMINTON_PRACTICE_LOAD)
        val Y: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.FATIGUE_COMPOSITE)
        val Y2: AnalysisFeatureKey = AnalysisFeatureKey.metric(TrendMetricId.RECOVERY_CHECKIN_COMPOSITE)
    }
}

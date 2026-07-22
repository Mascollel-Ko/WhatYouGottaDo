package com.training.trackplanner

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.training.trackplanner.analysis.proxyperformance.MajorLiftPosteriorPoint
import com.training.trackplanner.analysis.proxyperformance.MajorLiftProxySummary
import com.training.trackplanner.analysis.proxyperformance.MajorLiftTarget
import com.training.trackplanner.analysis.proxyperformance.ProxyBacktestSummary
import com.training.trackplanner.analysis.proxyperformance.ProxyModelBacktest
import com.training.trackplanner.analysis.proxyperformance.ProxyModelVariant
import com.training.trackplanner.analysis.proxyperformance.ProxyObservationStatus
import com.training.trackplanner.analysis.proxyperformance.ProxyPerformanceConfidence
import com.training.trackplanner.analysis.proxyperformance.ProxyPerformanceSummary
import com.training.trackplanner.analysis.proxyperformance.SessionExpectationComparison
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalysisProxyPerformanceUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun strengthCardsLabelActualAndEstimatedValuesSeparately() {
        content { ProxyPerformanceStrengthCards(summary()) }

        compose.onNodeWithTag("proxy-performance-capability-card").assertIsDisplayed()
        compose.onNodeWithText("추정 주요 운동 수행능력").assertIsDisplayed()
        compose.onNodeWithText("실제 e1RM 104.0 kg").assertExists()
        compose.onNodeWithText("노력도 보정 수행치 106.0 kg").assertExists()
        compose.onNodeWithText("실제 e1RM 기록을 대체하지 않습니다.", substring = true).assertExists()
    }

    @Test
    fun posteriorChartUsesHistoricalIntervalBandNotForecastRange() {
        val target = summary().targets.getValue(MajorLiftTarget.BENCH_PRESS)
        val spec = proxyPosteriorChartSpec(MajorLiftTarget.BENCH_PRESS, target)

        assertNotNull(spec.intervalBand)
        assertNull(spec.forecastRange)
        assertFalse(spec.lineSeries.single { series -> series.label == "실제 e1RM" }.connectPoints)
        assertFalse(spec.lineSeries.single { series -> series.label == "프록시만 반영된 주" }.connectPoints)
    }

    @Test
    fun insufficientExpectedValueUsesLowConfidenceCopy() {
        val comparison = summary().targets.getValue(MajorLiftTarget.BENCH_PRESS)
            .sessionComparisons.single()
            .copy(expectedMedianKg = null, expectedLow80Kg = null, expectedHigh80Kg = null)

        assertFalse(expectationInterpretation(comparison).contains("낮게 수행"))
        content {
            ProxyPerformanceStrengthCards(
                summary().copy(
                    targets = summary().targets.mapValues { (_, value) -> value.copy(sessionComparisons = listOf(comparison)) }
                )
            )
        }
        compose.onNodeWithText("직접 기록이 부족해 비교 신뢰도가 낮습니다.").assertExists()
    }

    @Test
    fun laboratoryCardIsExplicitlyExperimentalAndSeparateFromBayesianAnalysis() {
        content { ProxyPerformanceLabCard(summary()) }

        compose.onNodeWithTag("proxy-performance-lab-card").assertIsDisplayed()
        compose.onNodeWithText("주요 운동 프록시 추정 실험").assertIsDisplayed()
        compose.onNodeWithText("기존 Bayesian 시계열 분석과 별도입니다.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("롤링 사전검증").assertExists()
        compose.onNodeWithText("선택 모델 M2 · 공유 요인").assertExists()
        compose.onNodeWithText("상위 프록시 loading").assertExists()
        compose.onNodeWithText("최근 주요 innovation").assertExists()
    }

    private fun content(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            TrainingTrackPlannerTheme {
                Column { content() }
            }
        }
    }

    private fun summary(): ProxyPerformanceSummary {
        val targetSummary = MajorLiftProxySummary(
            selectedModel = ProxyModelVariant.M2_SHARED_FACTORS,
            weeklyPosterior = listOf(
                MajorLiftPosteriorPoint(
                    weekStart = LocalDate.parse("2026-01-05"),
                    priorMedianKg = 100.0,
                    priorLow80Kg = 95.0,
                    priorHigh80Kg = 105.0,
                    posteriorMedianKg = 102.0,
                    posteriorLow80Kg = 97.0,
                    posteriorHigh80Kg = 107.0,
                    actualCanonicalE1rmKg = 104.0,
                    observationStatus = ProxyObservationStatus.DIRECT_OBSERVATION,
                    directObservationCountToDate = 3,
                    proxyObservationCountToDate = 5,
                    modelFingerprint = "abcdef0123456789"
                )
            ),
            sessionComparisons = listOf(
                SessionExpectationComparison(
                    workoutEntryId = 1,
                    date = LocalDate.parse("2026-01-07"),
                    target = MajorLiftTarget.BENCH_PRESS,
                    exerciseStableKey = "barbell_bench_press",
                    actualCanonicalE1rmKg = 104.0,
                    effortAdjustedPerformanceKg = 106.0,
                    expectedMedianKg = 101.0,
                    expectedLow80Kg = 96.0,
                    expectedHigh80Kg = 105.0,
                    differenceKg = 5.0,
                    predictivePercentile = 0.84,
                    standardizedSurprise = 1.0,
                    confidence = ProxyPerformanceConfidence.MODERATE,
                    evidence = emptyList(),
                    modelFingerprint = "abcdef0123456789"
                )
            ),
            proxyContributions = emptyList(),
            backtest = ProxyBacktestSummary(
                selectedModel = ProxyModelVariant.M2_SHARED_FACTORS,
                candidates = listOf(
                    ProxyModelBacktest(
                        variant = ProxyModelVariant.M2_SHARED_FACTORS,
                        maeKg = 3.0,
                        rmseKg = 4.0,
                        meanBiasKg = 0.5,
                        medianAbsoluteErrorKg = 2.5,
                        intervalCoverage80 = 0.8,
                        meanIntervalWidth80Kg = 10.0,
                        gaussianLogPredictiveDensity = -2.0,
                        directTestSessions = 6,
                        distinctProxyExercises = 3,
                        proxyObservations = 8
                    )
                ),
                selectionReason = "Shared factors improved rolling error."
            ),
            confidence = ProxyPerformanceConfidence.MODERATE,
            latestDirectObservationDate = LocalDate.parse("2026-01-07"),
            directObservationCount = 3,
            proxyObservationCount = 5,
            distinctProxyExerciseCount = 2,
            modelFingerprint = "abcdef0123456789"
        )
        return ProxyPerformanceSummary(
            modelVersion = "proxy-performance-1.0.0",
            targets = MajorLiftTarget.entries.associateWith { targetSummary },
            generatedAtDate = LocalDate.parse("2026-01-10"),
            diagnostics = emptyList()
        )
    }
}

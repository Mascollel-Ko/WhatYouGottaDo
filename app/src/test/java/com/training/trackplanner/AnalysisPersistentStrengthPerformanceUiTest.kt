package com.training.trackplanner

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthPerformanceSummary
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthTargetSummary
import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalysisPersistentStrengthPerformanceUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `selector exposes all registry targets and weighted pull-up load semantics`() {
        content { PersistentStrengthPerformanceCards(summary()) }

        compose.onNodeWithTag("persistent-strength-capability-card").assertIsDisplayed()
        listOf("벤치프레스", "스쿼트", "데드리프트", "중량 풀업").forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }
        compose.onNodeWithText("중량 풀업").performClick()
        compose.onNodeWithText("추정 총부하 100.0 kg").assertIsDisplayed()
        compose.onNodeWithText("현재 체중 기준 추가중량 +10.0 kg").assertIsDisplayed()
        compose.onNodeWithText("직접 1RM과 비선형 반복 곡선 기반 세션 관측을 결합한 추정값입니다.").assertIsDisplayed()
    }

    @Test
    fun `laboratory card reports persisted ledger and version provenance`() {
        content { PersistentStrengthPerformanceLabCard(summary()) }

        compose.onNodeWithTag("persistent-strength-lab-card").assertIsDisplayed()
        compose.onNodeWithText("이벤트 원장 전체 2 · 대기 1 · 실패 0").assertIsDisplayed()
        compose.onNodeWithText("부트스트랩 completed|INITIAL_INSTALLATION_BOOTSTRAP|1").assertExists()
        compose.onNodeWithText("백업 복원 PERSISTED_POSTERIOR_BACKUP|2").assertExists()
        compose.onNodeWithText("Bayesian 시계열 실험실과는 별도 모델입니다.", substring = true).assertExists()
    }

    @Test
    fun `legacy Epley chart is clearly demoted to comparison value`() {
        assertEquals(
            "기존 공식 환산값",
            mainLiftE1rmSpec(
                mapOf(
                    TrendMetricId.BENCH_PRESS_E1RM to emptyList(),
                    TrendMetricId.SQUAT_E1RM to emptyList(),
                    TrendMetricId.DEADLIFT_E1RM to emptyList()
                )
            ).title
        )
    }

    private fun content(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            TrainingTrackPlannerTheme {
                Column { content() }
            }
        }
    }

    private fun summary() = PersistentStrengthPerformanceSummary(
        targets = listOf(
            target("strength.bench_press", "벤치프레스", 100.0),
            target("strength.back_squat", "스쿼트", 120.0),
            target("strength.conventional_deadlift", "데드리프트", 150.0),
            target(
                key = "strength.weighted_pull_up",
                name = "중량 풀업",
                median = 100.0,
                semantics = StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD,
                bodyWeight = 90.0,
                addedWeight = 10.0
            )
        ),
        eventCount = 2,
        pendingEventCount = 1,
        failedEventCount = 0,
        latestEventFingerprint = "0123456789abcdef",
        modelStateFingerprint = "fedcba9876543210",
        modelVersionBoundaries = listOf("strength-performance-model-2.0.0"),
        curveVersionBoundaries = listOf("strength-repetition-curves-1.0.0"),
        factorSchemaVersion = "strength-factor-schema-2.0.0",
        bootstrapProvenance = "completed|INITIAL_INSTALLATION_BOOTSTRAP|1",
        backupRestorationProvenance = "PERSISTED_POSTERIOR_BACKUP|2",
        numericalDiagnostics = emptyList()
    )

    private fun target(
        key: String,
        name: String,
        median: Double,
        semantics: StrengthLoadSemantics = StrengthLoadSemantics.EXTERNAL_LOAD,
        bodyWeight: Double? = null,
        addedWeight: Double? = null
    ) = PersistentStrengthTargetSummary(
        targetKey = key,
        displayNameKo = name,
        loadSemantics = semantics,
        currentMedianKg = median,
        currentLow80Kg = median - 5.0,
        currentHigh80Kg = median + 5.0,
        currentBodyWeightKg = bodyWeight,
        currentAddedWeightKg = addedWeight,
        latestDirectObservationKg = median,
        latestDirectObservationDate = null,
        relevantSessionCount = 4,
        directObservationCount = 1,
        strongNrmObservationCount = 2,
        proxyObservationCount = 3,
        failureObservationCount = 0,
        curveProfileId = "curve.general.v1",
        curveMatchLevel = "GENERAL_TARGET_POLICY",
        curveVarianceMultiplier = 1.25,
        curveCalibrationStatus = "CANONICAL_ONLY",
        lastProcessedSessionDate = null,
        modelVersion = "strength-performance-model-2.0.0",
        curveVersion = "strength-repetition-curves-1.0.0",
        history = emptyList()
    )
}

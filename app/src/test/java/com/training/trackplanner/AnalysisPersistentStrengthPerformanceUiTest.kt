package com.training.trackplanner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthExerciseLocalSummary
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthHistoryPoint
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthPerformanceSummary
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthTargetSummary
import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import java.time.LocalDate
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
        compose.onNodeWithText("활성 revision strength-revision-3.0.0 · RIR 정책 strength-rpe-rir-policy-1.0.0")
            .assertIsDisplayed()
        compose.onNodeWithText("프록시 운동의 절대 중량을 대상 운동 중량으로 직접 환산하지 않습니다.", substring = true)
            .assertExists()
    }

    @Test
    fun `laboratory card reports persisted ledger and version provenance`() {
        content { PersistentStrengthPerformanceLabCard(summary()) }

        compose.onNodeWithTag("persistent-strength-lab-card").assertIsDisplayed()
        compose.onNodeWithText("이벤트 원장 전체 2 · 대기 1 · 실패 0").assertIsDisplayed()
        compose.onNodeWithText("부트스트랩 completed|INITIAL_INSTALLATION_BOOTSTRAP|1").assertExists()
        compose.onNodeWithText("백업 복원 PERSISTED_POSTERIOR_BACKUP|2").assertExists()
        compose.onNodeWithText("이전 revision 1 · 재빌드 출처 MODEL_CORRECTION_REBUILD_0_5_0_3").assertExists()
        compose.onNodeWithText("local 운동 상태 2 · 적용 proxy 전이 1").assertExists()
        compose.onNodeWithText("target-specific proxy 위반 0").assertExists()
        compose.onNodeWithText("Bayesian 시계열 실험실과는 별도 모델입니다.", substring = true).assertExists()
    }

    @Test
    fun `exercise local detail explains innovation without absolute proxy conversion`() {
        val base = summary()
        val target = base.targets.first().copy(
            history = listOf(historyPoint()),
            localExerciseDetails = listOf(
                PersistentStrengthExerciseLocalSummary(
                    exerciseStableKey = "ex_a61f1e96",
                    exerciseName = "인클라인 덤벨 프레스",
                    sessionDate = LocalDate.of(2026, 7, 20),
                    priorMedianKg = 52.0,
                    sessionLikelihoodMedianKg = 60.0,
                    innovationPercent = 15.4,
                    posteriorMedianKg = 56.0,
                    proxyTransferApplied = true,
                    proxyTransferExclusionReason = null
                )
            )
        )
        content { PersistentStrengthPerformanceCards(base.copy(targets = listOf(target))) }

        compose.onNodeWithText("세션 상세 보기").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("기록 접기").assertExists()
        compose.onNodeWithTag("persistent-strength-local-detail", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("세션 전 local prior 52.0 kg").assertExists()
        compose.onNodeWithText("세션 innovation +15.4%").assertExists()
        compose.onNodeWithText("프록시 전이 적용 · 공유 근력 신호만 반영").assertExists()
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
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
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
        numericalDiagnostics = emptyList(),
        activeRevisionKey = "strength-revision-3.0.0",
        activeRevisionStatus = "ACTIVE",
        activeRevisionReason = "MODEL_CORRECTION_REBUILD_0_5_0_3",
        rirPolicyVersion = "strength-rpe-rir-policy-1.0.0",
        localExerciseStateCount = 2,
        proxyTransferCount = 1,
        supersededRevisionCount = 1
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
        history = emptyList(),
        knownRpeObservationCount = 1
    )

    private fun historyPoint() = PersistentStrengthHistoryPoint(
        eventUuid = "event-1",
        sessionDate = LocalDate.of(2026, 7, 20),
        priorMedianKg = 95.0,
        priorLow80Kg = 90.0,
        priorHigh80Kg = 100.0,
        posteriorMedianKg = 100.0,
        posteriorLow80Kg = 95.0,
        posteriorHigh80Kg = 105.0,
        directObservedLoadKg = null,
        directObservationType = "NONE",
        sessionObservationMedianKg = 102.0,
        sessionObservationLow80Kg = 96.0,
        sessionObservationHigh80Kg = 108.0,
        posteriorMeanChangeKg = 5.0,
        intervalWidthChange80Kg = 0.0,
        predictivePercentile = 0.6,
        strongObservationType = "RPE_MIXTURE_OBSERVATION",
        curveProfileId = "curve.general.v1",
        curveMatchLevel = "GENERAL_TARGET_POLICY",
        curveCalibrationStatus = "CANONICAL_ONLY",
        bodyWeightKgAtProcessing = null,
        rawAddedWeightKgAtProcessing = null,
        totalLoadKgAtProcessing = null,
        bodyWeightSource = null,
        sourceEvidenceStatus = "AVAILABLE",
        sourceSetCountAtProcessing = 2,
        evidenceFingerprint = "evidence-1",
        modelVersion = "strength-performance-model-3.0.0",
        curveVersion = "repetition-curve-assets-2.0.0",
        factorSchemaVersion = "strength-factor-schema-2.0.0"
    )
}

package com.training.trackplanner

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.v22.BCoachObjectiveAxis
import com.training.trackplanner.data.v22.BCoachPlanAlternative
import com.training.trackplanner.data.v22.BCoachProductionStatus
import com.training.trackplanner.data.v22.BCoachV2Policy
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramLoadSource
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w320dp-h640dp")
class BCoachProductionReviewDialogUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `review actions remain reachable at 320dp and large text`() {
        var applied = false
        var kept = false
        var selected: String? = null
        val alternatives = listOf(
            BCoachPlanAlternative(
                status = BCoachProductionStatus.USER_REVIEW_PROPOSED,
                label = "SAFE_BASELINE",
                weeklySetAverage = 2.0,
                changeCount = 0,
                candidateId = "C01",
                recommended = true,
                axisScores = BCoachObjectiveAxis.entries.associateWith { 0.1 },
                scenarioLosses = mapOf("LOW_STRESS" to 0.1, "BASE" to 0.2, "HIGH_STRESS" to 0.3),
                prescriptionSummary = listOf("legacy text must not drive review")
            ),
            BCoachPlanAlternative(
                status = BCoachProductionStatus.USER_REVIEW_PROPOSED,
                label = "JOINT_SEED",
                weeklySetAverage = 3.0,
                changeCount = 1,
                candidateId = "C02",
                axisScores = BCoachObjectiveAxis.entries.associateWith { 0.2 },
                scenarioLosses = mapOf("LOW_STRESS" to 0.2, "BASE" to 0.3, "HIGH_STRESS" to 0.4)
            )
        )
        val request = ProgramSkeletonRequest(
            "검토", ProgramGoal.FUNCTIONAL_CONDITIONING, 1, 60, emptySet(), "", 0.0,
            "AUTO", ProgramPeriodizationType.AUTO, 12
        )
        val plan = GeneratedProgramSkeleton(
            suggestedName = "검토", durationDays = 84, request = request,
            periodizationType = ProgramPeriodizationType.AUTO,
            weekPlans = (1..12).map { week -> ProgramWeekPlan(
                week, "BUILD", 1.0, 1.0, 2, 8.0, 2, 1, false
            ) },
            items = (1..12).map { week -> ProgramSkeletonItem(
                localId = "w$week", weekNumber = week, dayOfWeek = 1, orderIndex = 1,
                exerciseStableKey = "barbell_back_squat", exerciseName = "아주 긴 이름의 바벨 백 스쿼트 ${week}주",
                category = "근력", restSeconds = 120, prescription = "test", setCount = 2,
                reps = 0, weightKg = 0.0, seconds = 0, selectionReason = "test",
                weightSource = "B_COACH_V2_SET_SPECIFIC",
                setPrescriptions = listOf(
                    ProgramSetPrescription(1, 5, 100.0, 0, 8.0, ProgramLoadState.EXPLICIT_LOAD,
                        loadSource = ProgramLoadSource.PERSONAL_MODEL, expectedRir = 2.0),
                    ProgramSetPrescription(2, 8, 0.0, 0, 8.0,
                        ProgramLoadState.USER_CALIBRATION_REQUIRED, estimatedLoadKg = 82.5,
                        loadSource = ProgramLoadSource.CALIBRATION_REQUIRED, expectedRir = 1.5)
                )
            ) },
            weekDaySchedule = (1..12).associateWith { setOf(1) }
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                TrainingTrackPlannerTheme {
                    BCoachProductionReviewDialog(
                        changes = List(12) { "PRESCRIPTION:1/1/ex_$it:2->3sets" },
                        unresolvedCodes = listOf("APPROVED_NEED_INTERVAL_UNAVAILABLE"),
                        unreliableExerciseCount = 4,
                        alternatives = alternatives,
                        selectedCandidateId = "C01",
                        candidateForReview = { if (it == "C01") plan else plan.copy(suggestedName = "대안") },
                        onSelectCandidate = { selected = it },
                        onApply = { applied = true },
                        onDismiss = { kept = true }
                    )
                }
            }
        }

        compose.onNodeWithText("B-Coach 전체 블록 후보 검토").assertIsDisplayed()
        compose.onAllNodesWithText(BCoachV2Policy.DISCLAIMER).assertCountEquals(1)
        compose.onAllNodesWithText("legacy text must not drive review", substring = true)
            .assertCountEquals(0)
        compose.onNodeWithTag("b_coach_review_content").performScrollToNode(hasText("아주 긴 이름의 바벨 백 스쿼트 1주"))
        compose.onNodeWithText("아주 긴 이름의 바벨 백 스쿼트 1주").assertIsDisplayed()
        compose.onNodeWithText("약 82.5kg · 장비 단위 확인 필요", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("b_coach_review_content").performScrollToNode(hasText("12주"))
        compose.onNodeWithText("12주").performClick()
        compose.onNodeWithText("아주 긴 이름의 바벨 백 스쿼트 12주").assertIsDisplayed()
        compose.onNodeWithTag("b_coach_review_content").assert(hasScrollAction())
        listOf("b_coach_review_apply", "b_coach_review_keep").forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$tag outside 320dp viewport: $bounds", bounds.left >= 0.dp && bounds.right <= 320.dp)
        }
        compose.onNodeWithTag("b_coach_candidate_C02").performScrollTo().performClick()
        compose.onNodeWithTag("b_coach_review_apply").performClick()
        compose.onNodeWithTag("b_coach_review_keep").performClick()
        compose.runOnIdle {
            assertTrue("applied=$applied kept=$kept selected=$selected", applied && kept && selected == "C02")
        }
    }
}

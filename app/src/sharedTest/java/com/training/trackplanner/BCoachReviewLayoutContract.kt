package com.training.trackplanner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.v22.BCoachObjectiveAxis
import com.training.trackplanner.data.v22.BCoachPlanAlternative
import com.training.trackplanner.data.v22.BCoachProductionStatus
import com.training.trackplanner.data.v22.BCoachSportShadowReport
import com.training.trackplanner.data.v22.BCoachSportShadowCandidate
import com.training.trackplanner.data.v22.BCoachSportShadowObjective
import com.training.trackplanner.data.v22.BCoachSportShadowExercise
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
abstract class BCoachReviewLayoutContract {
    @get:Rule val compose = createComposeRule()
    protected open fun captureReviewScreen(name: String) = Unit

    private fun displayText(value: String) = com.training.trackplanner.localization.LocalizedPresentation.uiText(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, value
    )

    @Test
    fun pairedDumbbellLoadAndCollapsedEditorControls() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val key = "ex_a61f1e96"
        val plan = GeneratedProgramSkeleton(
            suggestedName = "pair", durationDays = 7,
            request = ProgramSkeletonRequest("pair", ProgramGoal.FUNCTIONAL_CONDITIONING, 2, 60,
                emptySet(), "", 0.0, "AUTO", ProgramPeriodizationType.AUTO, 1),
            periodizationType = ProgramPeriodizationType.AUTO,
            weekPlans = listOf(ProgramWeekPlan(1, "BUILD", 1.0, 1.0, 2, 8.0, 2, 1, false)),
            weekDaySchedule = mapOf(1 to setOf(1, 4)),
            planningMetadataJson = """{"engine":"B_BALANCED_BLOCK_PROGRAM_V1"}""",
            items = listOf(ProgramSkeletonItem("press", 1, 1, 1, key, "인클라인 덤벨 프레스", "근력",
                90, "test", 2, 8, 58.0, 0, "test", "USER_FIXED",
                setPrescriptions = (1..2).map { ProgramSetPrescription(it, 8, 58.0, 0, 7.0, ProgramLoadState.EXPLICIT_LOAD) }))
        )
        compose.setContent {
            TrainingTrackPlannerTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ProgramSkeletonPreview(plan, emptyList(), emptyMap()) {}
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.program_pair_load, "29", "58"), substring = true)
            .performScrollTo().assertIsDisplayed()
        val moveUp = context.getString(R.string.program_move_up)
        compose.onAllNodesWithText(moveUp).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.program_show_item_controls)).performScrollTo().performClick()
        compose.onNodeWithText(moveUp).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.program_close_item_controls)).performScrollTo().performClick()
        compose.onAllNodesWithText(moveUp).assertCountEquals(0)
        captureReviewScreen("paired-press-compact")
    }

    @Test
    fun reviewActionsReachable() {
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
                sessionTimes = (1..12).associate { week -> (week to 1) to
                    com.training.trackplanner.data.v22.BCoachSessionTime(8.0, 3.0, week.toDouble(), 2.0, 33.0) },
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
        compose.onNodeWithText(displayText("아주 긴 이름의 바벨 백 스쿼트 1주")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(displayText("2세트 · 8회 · 약 82.5kg · 장비 단위 확인 필요 · 예상 RIR 1.5 · 휴식 120초"))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12주").performScrollTo().performClick()
        compose.onNodeWithText(displayText("아주 긴 이름의 바벨 백 스쿼트 12주")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12주 월요일 · 예상", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(displayText("아주 긴 이름의 바벨 백 스쿼트 12주")).performScrollTo()
        captureReviewScreen("candidate-week12")
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
    @Test
    fun sportComparisonReachable(){
        var closed = false
        val request = ProgramSkeletonRequest(
            "Shadow", ProgramGoal.FUNCTIONAL_CONDITIONING, 1, 60, emptySet(),
            "", 0.0, "AUTO", ProgramPeriodizationType.AUTO, 12
        )
        val plan = GeneratedProgramSkeleton(
            suggestedName = "Shadow", durationDays = 84, request = request,
            periodizationType = ProgramPeriodizationType.AUTO,
            weekPlans = (1..12).map { week ->
                ProgramWeekPlan(week, "BUILD", 1.0, 1.0, 2, 8.0, 2, 1, false)
            },
            items = (1..12).map { week ->
                ProgramSkeletonItem(
                    localId = "shadow-w" + week, weekNumber = week, dayOfWeek = 1,
                    orderIndex = 1, exerciseStableKey = "shadow-drill",
                    exerciseName = "앞뒤 랜덤 콕줍기 장문 이름 " + week + "주",
                    category = "기능성", restSeconds = 60, prescription = "test",
                    setCount = 2, reps = 8, weightKg = 0.0, seconds = 0,
                    selectionReason = "test", weightSource = "B_COACH_V2_SET_SPECIFIC",
                    setPrescriptions = listOf(
                        ProgramSetPrescription(1, 8, 0.0, 0, null, ProgramLoadState.NOT_APPLICABLE),
                        ProgramSetPrescription(2, 8, 0.0, 0, null, ProgramLoadState.NOT_APPLICABLE)
                    )
                )
            },
            weekDaySchedule = (1..12).associateWith { setOf(1) }
        )
        val objective = BCoachSportShadowObjective("REACTION", 12, 3.0, 2.0, 4.0, 0.0)
        val base = BCoachSportShadowCandidate(
            "BASE", true, 0.4, 0.7, 0, 1, 58.0, 2.0,
            listOf(objective.copy(meanDose = 0.0, shortfallFraction = 1.0)), emptyList()
        )
        val alternative = BCoachSportShadowCandidate(
            "ALT", false, 0.5, 0.0, 1, 1, 50.0, 2.0,
            listOf(objective), listOf(
                BCoachSportShadowExercise("앞뒤 랜덤 콕줍기", "shadow-drill", 2.0, listOf("REACTION"))
            )
        )
        val report = BCoachSportShadowReport(
            productionCandidateId = "BASE", shadowLeaderId = "ALT",
            candidates = listOf(alternative, base),
            notInEvaluatedPool = listOf("래터럴 바운드"),
            evaluatedCandidateCount = 2
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                TrainingTrackPlannerTheme {
                    BCoachSportShadowDialog(report, candidateForReview = { plan }, onClose = {
                        closed = true
                    })
                }
            }
        }
        compose.onNodeWithText("후보별 스포츠 훈련량").assertIsDisplayed()
        compose.onNodeWithTag("b_coach_sport_shadow_content").assert(hasScrollAction())
        compose.onNodeWithTag("b_coach_sport_shadow_content")
            .performScrollToNode(hasText("세부 스포츠 목표"))
        compose.onNodeWithText("세부 스포츠 목표").assertIsDisplayed()
        compose.onNodeWithText("부족률", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("b_coach_sport_shadow_content")
            .performScrollToNode(hasText("12주"))
        compose.onNodeWithTag("b_coach_shadow_week_12").performClick()
        compose.onNodeWithTag("b_coach_sport_shadow_content").performScrollToKey("shadow-w12")
        compose.onNodeWithText("앞뒤 랜덤 콕줍기 장문 이름 12주")
            .performScrollTo().assertIsDisplayed()
        captureReviewScreen("sport-week12")
        compose.onNodeWithTag("b_coach_sport_shadow_close").assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

}

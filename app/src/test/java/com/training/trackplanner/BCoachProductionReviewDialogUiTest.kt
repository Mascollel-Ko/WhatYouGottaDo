package com.training.trackplanner

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.v22.BCoachObjectiveAxis
import com.training.trackplanner.data.v22.BCoachPlanAlternative
import com.training.trackplanner.data.v22.BCoachProductionStatus
import com.training.trackplanner.data.v22.BCoachV2Policy
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
                prescriptionSummary = listOf("W1/D1 barbell_back_squat: 5rep@100.0kg")
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
                        onSelectCandidate = { selected = it },
                        onApply = { applied = true },
                        onDismiss = { kept = true }
                    )
                }
            }
        }

        compose.onNodeWithText("B-Coach 전체 블록 후보 검토").assertIsDisplayed()
        compose.onAllNodesWithText(BCoachV2Policy.DISCLAIMER).assertCountEquals(1)
        compose.onAllNodesWithText("W1/D1 barbell_back_squat: 5rep@100.0kg", substring = true)
            .assertCountEquals(1)
        compose.onNodeWithTag("b_coach_review_content").assert(hasScrollAction())
        listOf("b_coach_review_apply", "b_coach_review_keep").forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$tag outside 320dp viewport: $bounds", bounds.left >= 0.dp && bounds.right <= 320.dp)
        }
        compose.onNodeWithTag("b_coach_candidate_C02").performClick()
        compose.onNodeWithTag("b_coach_review_apply").performClick()
        compose.onNodeWithTag("b_coach_review_keep").performClick()
        compose.runOnIdle { assertTrue(applied && kept && selected == "C02") }
    }
}

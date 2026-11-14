package com.training.trackplanner

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                TrainingTrackPlannerTheme {
                    BCoachProductionReviewDialog(
                        changeCount = 12,
                        unreliableExerciseCount = 4,
                        onApply = { applied = true },
                        onDismiss = { kept = true }
                    )
                }
            }
        }

        compose.onNodeWithText("B-Coach 전체 블록 후보 검토").assertIsDisplayed()
        compose.onNodeWithTag("b_coach_review_content").assert(hasScrollAction())
        listOf("b_coach_review_apply", "b_coach_review_keep").forEach { tag ->
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$tag outside 320dp viewport: $bounds", bounds.left >= 0.dp && bounds.right <= 320.dp)
        }
        compose.onNodeWithTag("b_coach_review_apply").performClick()
        compose.onNodeWithTag("b_coach_review_keep").performClick()
        compose.runOnIdle { assertTrue(applied && kept) }
    }
}

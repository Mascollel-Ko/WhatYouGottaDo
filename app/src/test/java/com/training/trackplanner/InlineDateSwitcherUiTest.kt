package com.training.trackplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class InlineDateSwitcherUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dateUsesFullRowAndBalancedControlsAt320DpAndLargeText() {
        assertLayout(width = 320.dp, fontScale = 1.5f)
    }

    @Test
    fun dateUsesFullRowAndBalancedControlsAt360Dp() {
        assertLayout(width = 360.dp, fontScale = 1f)
    }

    private fun assertLayout(width: androidx.compose.ui.unit.Dp, fontScale: Float) {
        content(width = width, fontScale = fontScale)
        val date = compose.onNodeWithTag("plan-start-date").getUnclippedBoundsInRoot()
        val minus = compose.onNodeWithTag("plan-start-date-minus").getUnclippedBoundsInRoot()
        val plus = compose.onNodeWithTag("plan-start-date-plus").getUnclippedBoundsInRoot()

        assertTrue(date.bottom <= minus.top)
        assertEquals((minus.right - minus.left).value, (plus.right - plus.left).value, 0.5f)
        assertTrue(date.right - date.left >= plus.right - minus.left)
    }

    @Test
    fun manualEditingAndDateSteppingKeepExistingBehavior() {
        content()
        compose.onNodeWithTag("plan-start-date-minus").performClick()
        compose.onNodeWithTag("plan-start-date").assertTextContains("2026-08-28")
        compose.onNodeWithTag("plan-start-date-plus").performClick()
        compose.onNodeWithTag("plan-start-date").assertTextContains("2026-08-29")

        compose.onNodeWithTag("plan-start-date").performTextReplacement("invalid")
        compose.onNodeWithTag("plan-start-date-minus").assertIsNotEnabled()
        compose.onNodeWithTag("plan-start-date-plus").assertIsNotEnabled()
    }

    private fun content(width: androidx.compose.ui.unit.Dp = 320.dp, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            var date by remember { mutableStateOf("2026-08-29") }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                TrainingTrackPlannerTheme {
                    Box(Modifier.width(width)) {
                        InlineDateSwitcher(dateText = date, onDateChange = { date = it })
                    }
                }
            }
        }
        compose.onNodeWithTag("plan-start-date").assertIsEnabled()
    }
}

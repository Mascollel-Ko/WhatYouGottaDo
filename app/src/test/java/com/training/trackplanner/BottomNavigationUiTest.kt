package com.training.trackplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
@Config(sdk = [34])
class BottomNavigationUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersConsistentIconsAndKoreanLabels() {
        content(selectedTab = AppTab.Home)

        AppTab.entries.forEach { tab ->
            compose.onNodeWithContentDescription(tab.label, useUnmergedTree = true)
                .assertIsDisplayed()
            compose.onNodeWithText(tab.label).assertIsDisplayed()
        }
        compose.onNodeWithText("홈").assertIsSelected()
        compose.onNodeWithText("기록").assertIsNotSelected()
    }

    @Test
    fun selectingATabUsesTheExistingNavigationCallback() {
        var selected: AppTab? = null
        content(selectedTab = AppTab.Home, onTabSelected = { selected = it })

        compose.onNodeWithText("분석").performClick()

        assertEquals(AppTab.Analysis, selected)
    }

    @Test
    fun labelsRemainInsideCompactWidthAtLargeFontInDarkTheme() {
        content(selectedTab = AppTab.Analysis, fontScale = 1.5f, darkTheme = true)

        AppTab.entries.forEach { tab ->
            val bounds = compose.onNodeWithText(tab.label).getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= 0.dp)
            assertTrue(bounds.right <= 360.dp)
            assertTrue(bounds.bottom > bounds.top)
        }
    }

    private fun content(
        selectedTab: AppTab,
        fontScale: Float = 1f,
        darkTheme: Boolean = false,
        onTabSelected: (AppTab) -> Unit = {}
    ) {
        compose.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale)
            ) {
                TrainingTrackPlannerTheme(darkTheme = darkTheme) {
                    Box(Modifier.width(360.dp)) {
                        AppBottomNavigation(
                            selectedTab = selectedTab,
                            onTabSelected = onTabSelected
                        )
                    }
                }
            }
        }
    }
}

package com.training.trackplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.AnalysisStats
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
class AnalysisHubUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun groupedAnalysisEntriesRemainVisibleAndClickable() {
        var destination = ""
        content(
            onFatigueClick = { destination = "fatigue" },
            onConnectiveTissueClick = { destination = "tissue" },
            onLaggedLabClick = { destination = "lagged" }
        )

        analysisEntries().forEach { title ->
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(title))
            compose.onNodeWithText(title).assertIsDisplayed()
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("오늘 컨디션 및 피로도 분석"))
        compose.onNodeWithText("오늘 컨디션 및 피로도 분석").performClick()
        assertEquals("fatigue", destination)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("시계열 분석"))
        compose.onNodeWithText("시계열 분석").performClick()
        assertEquals("lagged", destination)
    }

    @Test
    fun groupedEntriesFitCompactLargeTextDarkTheme() {
        content(fontScale = 1.3f, darkTheme = true)

        analysisEntries().forEach { title ->
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(title))
            val bounds = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= 0.dp)
            assertTrue(bounds.right <= 320.dp)
            assertTrue(bounds.bottom > bounds.top)
        }
    }

    private fun content(
        fontScale: Float = 1f,
        darkTheme: Boolean = false,
        onFatigueClick: () -> Unit = {},
        onConnectiveTissueClick: () -> Unit = {},
        onLaggedLabClick: () -> Unit = {}
    ) {
        compose.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale)
            ) {
                TrainingTrackPlannerTheme(darkTheme = darkTheme) {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        LazyColumn {
                            item {
                                AnalysisHubContent(
                                    stats = AnalysisStats(12, 3450.0, 1800),
                                    onFatigueClick = onFatigueClick,
                                    onBadmintonClick = {},
                                    onStrengthClick = {},
                                    onConnectiveTissueClick = onConnectiveTissueClick,
                                    onRelationshipLabClick = {},
                                    onLaggedLabClick = onLaggedLabClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun analysisEntries(): List<String> = listOf(
        "오늘 컨디션 및 피로도 분석",
        "배드민턴 전이 분석",
        "근력운동 추이 분석",
        "연결조직 분석",
        "관계 탐색",
        "시계열 분석"
    )
}

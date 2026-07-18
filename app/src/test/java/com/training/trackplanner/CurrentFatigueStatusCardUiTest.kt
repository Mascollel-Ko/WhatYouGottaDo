package com.training.trackplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.FatigueConfidence
import com.training.trackplanner.analysis.fatigue.FatigueReadinessLabel
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CurrentFatigueStatusCardUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun summaryShowsExactlyTheCanonicalFiveAxesAsQuietRows() {
        content()

        compose.onNodeWithText("현재 상태: 42 · 피로도 보통").assertIsDisplayed()
        canonicalAxes().forEach { axis ->
            compose.onAllNodesWithText(axis).assertCountEquals(1)
        }
        compose.onAllNodesWithText("관절/건/충격").assertCountEquals(0)
        compose.onAllNodesWithText("동작·집중").assertCountEquals(0)
        compose.onAllNodesWithText("캡틴체어 레그 레이즈").assertCountEquals(0)
    }

    @Test
    fun canonicalRowsRemainReadableAtLargeFontInDarkTheme() {
        content(fontScale = 1.5f, darkTheme = true)

        canonicalAxes().forEach { axis ->
            val bounds = compose.onNodeWithText(axis).getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= 0.dp)
            assertTrue(bounds.right <= 320.dp)
            assertTrue(bounds.bottom > bounds.top)
        }
    }

    private fun content(fontScale: Float = 1f, darkTheme: Boolean = false) {
        compose.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale)
            ) {
                TrainingTrackPlannerTheme(darkTheme = darkTheme) {
                    Box(Modifier.width(320.dp)) {
                        CurrentFatigueStatusCard(state())
                    }
                }
            }
        }
    }

    private fun state() = DailyFatigueState(
        date = LocalDate.of(2026, 7, 19),
        highForceNeuralFatigue = 20.0,
        systemicMuscularFatigue = 40.0,
        localMuscularFatigue = 55.0,
        highSpeedFatigue = 72.0,
        reactiveFatigue = 84.0,
        recoveryPressure = 0.0,
        highForceNeuralScore = 20,
        systemicMuscularScore = 40,
        localMuscularScore = 55,
        highSpeedScore = 72,
        reactiveScore = 84,
        recoveryPressureScore = 0,
        overallFatigueIndex = 42,
        readinessLabel = FatigueReadinessLabel.NORMAL,
        cautionReasons = emptyList(),
        confidence = FatigueConfidence.HIGH
    )

    private fun canonicalAxes(): List<String> = listOf(
        "고중량·힘 신경계",
        "전신 근육",
        "국소 근육",
        "고속",
        "반응"
    )
}

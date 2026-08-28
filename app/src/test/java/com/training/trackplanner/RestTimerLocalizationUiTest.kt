package com.training.trackplanner

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class RestTimerLocalizationUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun foregroundAndMiniTimerRenderEnglishFromSemanticState() {
        val state = RestTimerState(
            isRunning = true,
            remainingSeconds = 65,
            totalSeconds = 90,
            exerciseStableKey = "ez_bar_spider_curl",
            storedExerciseName = "EZ바 스파이더 컬",
            nextSetNumber = 2,
            hasNextTarget = true
        )
        compose.setContent {
            TrainingTrackPlannerTheme {
                Column {
                    RestTimerForegroundBar(state, onOpenTarget = {}, onDismiss = {})
                    RestTimerMiniBar(state, onStop = {})
                }
            }
        }
        compose.onAllNodesWithText("Rest 01:05").assertCountEquals(2)
        compose.onNodeWithText("Next: EZ-Bar Spider Curl · Set 2 ready").assertIsDisplayed()
        compose.onNodeWithText("Stop").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide rest timer bar").assertIsDisplayed()
    }

    @Test
    fun timerPermissionHelpRendersEnglish() {
        compose.setContent {
            TrainingTrackPlannerTheme {
                RestTimerPermissionHint(
                    notificationPermissionNeeded = true,
                    overlayPermissionGranted = false,
                    onRequestNotification = {},
                    onOpenOverlaySettings = {}
                )
            }
        }
        compose.onNodeWithText("Allow notifications").assertIsDisplayed()
        compose.onNodeWithText("Overlay settings").assertIsDisplayed()
    }
}

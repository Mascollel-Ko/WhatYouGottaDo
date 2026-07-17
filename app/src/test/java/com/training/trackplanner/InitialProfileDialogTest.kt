package com.training.trackplanner

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InitialProfileDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun habitualIntensitySelectionSavesStableKey() {
        var saved: InitialUserProfile? = null
        compose.setContent {
            TrainingTrackPlannerTheme {
                InitialProfileDialog(null, onDismiss = {}, onSave = { saved = it })
            }
        }

        val scroll = compose.onNode(hasScrollAction())
        scroll.performScrollToNode(hasText("평소 운동 강도"))
        compose.onNodeWithText("강한 편").performClick()
        compose.onNodeWithText("저장").performClick()

        assertEquals("HARD", saved?.habitualTrainingIntensity)
    }

    @Test
    fun savedHabitualIntensityIsSelectedWhenEditorReopens() {
        compose.setContent {
            TrainingTrackPlannerTheme {
                InitialProfileDialog(
                    InitialUserProfile(habitualTrainingIntensity = "NORMAL"),
                    onDismiss = {},
                    onSave = {}
                )
            }
        }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("평소 운동 강도"))
        compose.onNodeWithText("보통").assertIsSelected()
    }
}

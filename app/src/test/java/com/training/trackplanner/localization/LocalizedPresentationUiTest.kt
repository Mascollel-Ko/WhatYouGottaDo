package com.training.trackplanner.localization

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.training.trackplanner.ExerciseListItem
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class LocalizedPresentationUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun builtInNameLocalizesWhileCustomNameRemainsVerbatim() {
        compose.setContent {
            TrainingTrackPlannerTheme {
                Column {
                    ExerciseListItem(
                        Exercise(
                            stableKey = "barbell_deadlift",
                            name = "데드리프트",
                            category = "근력운동"
                        ),
                        selected = false
                    )
                    ExerciseListItem(
                        Exercise(
                            stableKey = "user_delete",
                            name = "삭제",
                            category = "근력운동",
                            isCustom = true
                        ),
                        selected = false
                    )
                }
            }
        }

        compose.onNodeWithText("Barbell Deadlift").assertIsDisplayed()
        compose.onNodeWithText("삭제").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsNotDisplayed()
    }
}

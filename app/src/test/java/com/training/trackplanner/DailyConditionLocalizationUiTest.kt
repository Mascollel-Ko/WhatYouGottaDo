package com.training.trackplanner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.training.trackplanner.data.DailyCheckIn
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class DailyConditionLocalizationUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun englishEditorLocalizesEveryFieldAndAction() {
        compose.setContent {
            TrainingTrackPlannerTheme {
                DailyConditionEditorDialog(
                    targetDate = LocalDate.now(),
                    checkIn = DailyCheckIn(LocalDate.now().toString()),
                    onDismiss = {},
                    onSave = {}
                )
            }
        }

        listOf(
            "Today's check-in",
            "Reference signals used with training load in coach analysis.",
            "Sleep",
            "Body weight",
            "Overall fatigue",
            "Lower-body fatigue",
            "Joint/tendon discomfort",
            "Focus/motivation",
            "Save",
            "Cancel"
        ).forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun englishCompactSummaryUsesLocalizedWrapperValues() {
        compose.setContent {
            TrainingTrackPlannerTheme {
                HomeDailyCheckInCard(
                    checkIn = DailyCheckIn(
                        date = LocalDate.now().toString(),
                        sleepHours = 8.0,
                        bodyWeightKg = 80.5,
                        overallFatigue = 2,
                        lowerBodyFatigue = 3,
                        jointTendonDiscomfort = 1,
                        focusMotivation = 5
                    ),
                    onSave = {}
                )
            }
        }
        compose.onNodeWithText("Condition today").assertIsDisplayed()
        compose.onNodeWithText("Sleep 8 h · Body weight 80.5 kg · Overall 2 · Lower body 3 · Discomfort 1 · Focus/motivation 5")
            .assertIsDisplayed()
        compose.onNodeWithText("Edit").assertIsDisplayed()
    }
}

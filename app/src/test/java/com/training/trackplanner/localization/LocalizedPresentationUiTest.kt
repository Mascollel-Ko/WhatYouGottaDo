package com.training.trackplanner.localization

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.training.trackplanner.ExerciseDetailCard
import com.training.trackplanner.ExerciseListItem
import com.training.trackplanner.ProgramCard
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisUiState
import com.training.trackplanner.analysis.fatigue.ui.FatigueAnalysisSection
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.TrainingProgram
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

    @Test
    fun fatigueAnalysisUsesApprovedSemanticLabels() {
        compose.setContent {
            TrainingTrackPlannerTheme {
                FatigueAnalysisSection(
                    state = FatigueAnalysisUiState(isLoading = false),
                    onPeriodChange = {},
                    onFatigueTargetToggle = {},
                    onContributionTargetChange = {},
                    onContributionGroupingChange = {},
                    onContributionSourcesApply = {}
                )
            }
        }

        listOf(
            "Fatigue analysis",
            "Overview",
            "Details",
            "High-load axes",
            "Lower-load axes"
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onAllNodesWithText("This will appear once more workout history is available.")
            .assertCountEquals(2)
        listOf("simply", "in detail", "high load", "spare load")
            .forEach { compose.onNodeWithText(it).assertIsNotDisplayed() }
    }

    @Test
    fun exerciseDetailRendersTheLocalizedBuiltInDescription() {
        compose.setContent {
            TrainingTrackPlannerTheme {
                ExerciseDetailCard(
                    Exercise(
                        stableKey = "ex_8633d8db",
                        name = "EZ바 컬",
                        category = "근력운동",
                        description = "EZ바를 쥐고 팔꿈치를 고정해 말아 올린다."
                    )
                )
            }
        }

        compose.onNodeWithText("EZ-Bar Curl").assertIsDisplayed()
        compose.onNodeWithText(
            "Hold the EZ-bar with your elbows fixed and curl it upward. This variation builds biceps volume while reducing wrist strain."
        ).assertIsDisplayed()
        compose.onNodeWithText("EZ바를 쥐고 팔꿈치를 고정해 말아 올린다.").assertIsNotDisplayed()
    }

    @Test
    fun programCardLocalizesOnlySeededProgramNames() {
        compose.setContent {
            TrainingTrackPlannerTheme {
                Column {
                    ProgramCard(
                        program = TrainingProgram(
                            stableKey = "3",
                            name = "배드민턴 파워 보조 4주",
                            durationDays = 28
                        ),
                        onClick = {},
                        onApply = {},
                        onEdit = {},
                        onDelete = {}
                    )
                    ProgramCard(
                        program = TrainingProgram(
                            stableKey = "user_program_test",
                            name = "나의 프로그램",
                            durationDays = 28
                        ),
                        onClick = {},
                        onApply = {},
                        onEdit = {},
                        onDelete = {}
                    )
                }
            }
        }

        compose.onNodeWithText("Badminton Strength Support - 4 Weeks").assertIsDisplayed()
        compose.onNodeWithText("나의 프로그램").assertIsDisplayed()
    }
}

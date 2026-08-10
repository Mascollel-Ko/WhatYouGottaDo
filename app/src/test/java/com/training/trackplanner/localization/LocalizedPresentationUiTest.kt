package com.training.trackplanner.localization

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.training.trackplanner.CurrentFatigueStatusCard
import com.training.trackplanner.DataTransferReportDialog
import com.training.trackplanner.ExerciseDetailCard
import com.training.trackplanner.ExerciseInfoDialog
import com.training.trackplanner.ExerciseListItem
import com.training.trackplanner.ProgramCard
import com.training.trackplanner.TodaySummaryCard
import com.training.trackplanner.analysis.fatigue.FatigueAnalysisUiState
import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.FatigueConfidence
import com.training.trackplanner.analysis.fatigue.FatigueReadinessLabel
import com.training.trackplanner.analysis.fatigue.HomeFatigueCardSummary
import com.training.trackplanner.analysis.fatigue.HomeFatigueReading
import com.training.trackplanner.analysis.fatigue.HomeTodaySummaryState
import com.training.trackplanner.analysis.fatigue.ui.FatigueAnalysisSection
import com.training.trackplanner.data.DataTransferOperation
import com.training.trackplanner.data.DataTransferReport
import com.training.trackplanner.data.DataTransferStage
import com.training.trackplanner.data.DataTransferStages
import com.training.trackplanner.data.DataTransferStatus
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import java.time.LocalDate
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

    @Test
    fun homeLocalizesCurrentAndProjectedFatigueSemanticValues() {
        val summary = HomeTodaySummaryState.empty(LocalDate.of(2026, 8, 11)).copy(
            fatigueCard = HomeFatigueCardSummary(
                primaryPrefix = "운동 전",
                primary = HomeFatigueReading(8, "피로도 낮음"),
                projectionPrefix = "끝나면 예상 피로도",
                projection = HomeFatigueReading(42, "예상 피로도 보통"),
                axisMessage = "모든 피로도가 양호합니다. 힘차게 운동!",
                levelCountMessage = "축별 상태: 매우 높음(0), 높음(0), 보통(0), 낮음(5)"
            )
        )
        compose.setContent { TrainingTrackPlannerTheme { TodaySummaryCard(summary) } }

        compose.onNodeWithText("Current status: 8 · low fatigue", substring = true).assertExists()
        compose.onNodeWithText(
            "Projected fatigue after completion: 42 · Expected fatigue is moderate",
            substring = true
        ).assertExists()
        listOf("피로도 낮음", "예상 피로도 보통", "끝나면 예상 피로도")
            .forEach { compose.onAllNodesWithText(it, substring = true).assertCountEquals(0) }
    }

    @Test
    fun fatigueDetailsLocalizeEveryAxisAndRuntimeState() {
        compose.setContent {
            TrainingTrackPlannerTheme {
                CurrentFatigueStatusCard(
                    state = DailyFatigueState(
                        date = LocalDate.of(2026, 8, 11),
                        highForceNeuralFatigue = 0.0,
                        systemicMuscularFatigue = 0.0,
                        localMuscularFatigue = 0.0,
                        highSpeedFatigue = 0.0,
                        reactiveFatigue = 0.0,
                        recoveryPressure = 0.0,
                        highForceNeuralScore = 10,
                        systemicMuscularScore = 20,
                        localMuscularScore = 30,
                        highSpeedScore = 40,
                        reactiveScore = 50,
                        recoveryPressureScore = 0,
                        overallFatigueIndex = 22,
                        readinessLabel = FatigueReadinessLabel.LOW,
                        cautionReasons = emptyList(),
                        confidence = FatigueConfidence.HIGH
                    ),
                    projectedOfi = 42
                )
            }
        }

        listOf(
            "High weight/strength nervous system",
            "full body muscles",
            "local muscle",
            "High-speed",
            "reaction"
        ).forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Current status: 22 · low fatigue").assertExists()
        listOf("고중량·힘 신경계", "전신 근육", "국소 근육", "고속", "반응", "피로도 낮음")
            .forEach { compose.onAllNodesWithText(it, substring = true).assertCountEquals(0) }
    }

    @Test
    fun exerciseInfoLocalizesBuiltInMetadataWithoutChangingCustomTextPolicy() {
        val exercise = Exercise(
            stableKey = "ex_8633d8db",
            name = "EZ바 컬",
            category = "근력운동",
            movementPattern = "HINGE",
            movementCategory = "STRENGTH",
            primaryMuscles = "LAT",
            secondaryMuscles = "TRICEPS",
            equipment = "DUMBBELL",
            equipmentTags = "DUMBBELL",
            mode = "WEIGHT_REPS"
        )
        compose.setContent {
            TrainingTrackPlannerTheme {
                ExerciseInfoDialog(
                    exercise = exercise,
                    metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise),
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText("EZ-Bar Curl").assertIsDisplayed()
        listOf("LAT", "TRICEPS", "DUMBBELL", "근력운동", "덤벨", "광배근")
            .forEach { compose.onAllNodesWithText(it).assertCountEquals(0) }
    }

    @Test
    fun latestOperationDetailsUseEnglishPresentationText() {
        val report = DataTransferReport(
            operationId = "fixture",
            operation = DataTransferOperation.BACKUP,
            status = DataTransferStatus.SUCCESS,
            startedAt = 1L,
            completedAt = 2L,
            fileDisplayName = "records.csv",
            currentStage = DataTransferStages.COMPLETED,
            stages = listOf(DataTransferStage(DataTransferStages.WRITING, 1L, 2L)),
            entityCounts = mapOf("workoutSets" to 12)
        )
        compose.setContent {
            TrainingTrackPlannerTheme {
                DataTransferReportDialog(report, onDismiss = {}, onSave = {})
            }
        }

        compose.onNodeWithText("Latest operation details").assertIsDisplayed()
        compose.onNodeWithText("Task: Back up records", substring = true).assertExists()
        compose.onNodeWithText("Current stage: Completed", substring = true).assertExists()
        listOf("최근 작업 상세", "기록 백업", "현재 단계", "파일 쓰기", "개수")
            .forEach { compose.onAllNodesWithText(it, substring = true).assertCountEquals(0) }
    }
}

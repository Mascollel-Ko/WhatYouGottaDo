package com.training.trackplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseMetadataCopySource
import com.training.trackplanner.data.ExerciseRuntimeMetadataEditorData
import com.training.trackplanner.data.MetadataTokenField
import com.training.trackplanner.data.ProgramBuildProgressState
import com.training.trackplanner.data.ProgramOptimizationSummary
import com.training.trackplanner.data.ProgramUserNotice
import com.training.trackplanner.data.ProgramUserNoticeCode
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.RuntimeMetadataEditorOptions
import com.training.trackplanner.data.emptyProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR")
class MetadataPresentationUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun programNoticesRemainReadableAt320DpAndLargeFontWithoutRawCodes() {
        val notices = listOf(
            ProgramUserNotice(ProgramUserNoticeCode.MAIN_EXERCISE_PRIORITY_RESTORED),
            ProgramUserNotice(
                ProgramUserNoticeCode.EXCLUDED_EXERCISES_APPLIED,
                count = 3
            ),
            ProgramUserNotice(
                ProgramUserNoticeCode.PREFERRED_EXERCISES_INCLUDED,
                selectedCount = 2,
                totalCount = 4
            )
        )
        sizedContent {
            ProgramBuildProgressCard(
                progress = ProgramBuildProgressState.Completed(
                    skeleton = emptyProgramSkeleton(request(), emptyMap()),
                    summary = ProgramOptimizationSummary(notices)
                ),
                onRetry = {}
            )
        }

        val texts = listOf(
            "프로그램 구성 결과",
            "근력 메인 운동이 보조 운동에 밀리지 않도록 구성을 보정했습니다.",
            "지정한 제외 운동 3개를 프로그램에서 제외했습니다.",
            "선호 운동 4개 중 2개를 프로그램에 포함했습니다."
        )
        texts.forEach { text ->
            compose.onNodeWithText(text).assertExists()
            val bounds = compose.onNodeWithText(text).getUnclippedBoundsInRoot()
            assertTrue("$text exceeds 320dp", bounds.right <= 320.dp)
        }
        compose.onNodeWithText("프로그램 구성 결과").assertIsDisplayed()
        compose.onAllNodes(hasText("PROGRAM_", substring = true)).assertCountEquals(0)
    }

    @Test
    fun metadataFieldShowsLocalizedLabelsWrapsChipsAndReturnsCanonicalCode() {
        var saved = ""
        sizedContent {
            MetadataSingleSelectField(
                label = "프로그램 사용 여부",
                value = "ANALYSIS_ONLY",
                options = listOf("PROGRAM_SELECTABLE", "ANALYSIS_ONLY"),
                field = MetadataDisplayField.PLANNING_ELIGIBILITY,
                onValueChange = { saved = it }
            )
            MetadataMultiSelectField(
                label = "분석 대상",
                selected = listOf(
                    "FATIGUE",
                    "STRENGTH_PROGRESS",
                    "HYPERTROPHY_VOLUME",
                    "BADMINTON_TRANSFER"
                ),
                options = listOf(
                    "FATIGUE",
                    "STRENGTH_PROGRESS",
                    "HYPERTROPHY_VOLUME",
                    "BADMINTON_TRANSFER"
                ),
                field = MetadataDisplayField.ANALYSIS_ELIGIBILITY,
                onValueChange = {}
            )
        }

        compose.onNodeWithText("분석에만 사용").performClick()
        compose.onNodeWithText("프로그램에 사용 가능").performClick()
        compose.onNodeWithText("적용").performClick()

        assertEquals("PROGRAM_SELECTABLE", saved)
        compose.onNodeWithText("근력 진행").assertIsDisplayed()
        compose.onNodeWithText("그 외 1개").assertIsDisplayed()
        compose.onAllNodes(hasText("PROGRAM_SELECTABLE", substring = true)).assertCountEquals(0)
    }

    @Test
    fun exerciseInfoAndCopyDialogsUseGroupedLocalizedMetadata() {
        val exercise = exercise()
        val metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            programSlot = "UPPER_PUSH_SUPPORT",
            primaryStressProfile = "HEAVY_AXIAL_LOWER_STRESS",
            badmintonTransferLevel = "SUPPORTIVE",
            badmintonTransferType = MetadataTokenField(
                raw = "GENERAL_STRENGTH_SUPPORTIVE",
                values = listOf("GENERAL_STRENGTH_SUPPORTIVE")
            ),
            recoveryDurationClass = "VERY_LONG"
        )
        sizedContent {
            ExerciseInfoDialog(
                exercise = exercise,
                metadata = metadata,
                onDismiss = {}
            )
        }

        compose.onNodeWithText("주요 동작").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("사용 근육과 장비"))
        compose.onNodeWithText("사용 근육과 장비").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("프로그램 활용"))
        compose.onNodeWithText("프로그램 활용").assertIsDisplayed()
        compose.onNodeWithText("상체 밀기 보조").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("스트레스와 회복"))
        compose.onNodeWithText("스트레스와 회복").assertIsDisplayed()
        compose.onNodeWithText("고중량 축성 하체 스트레스").assertExists()
        compose.onAllNodes(hasText("UPPER_PUSH_SUPPORT", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("VERY_LONG", substring = true)).assertCountEquals(0)
    }

    @Test
    fun metadataCopyDialogShowsLocalizedProgramRole() {
        val exercise = exercise()
        val metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            programSlot = "UPPER_PUSH_SUPPORT"
        )
        sizedContent {
            ExerciseMetadataCopyDialog(
                sources = listOf(ExerciseMetadataCopySource(exercise, metadata)),
                onDismiss = {},
                onSelect = {}
            )
        }
        compose.onNodeWithText("상체 밀기 보조", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasText("UPPER_PUSH_SUPPORT", substring = true)).assertCountEquals(0)
    }

    @Test
    fun fullEditorKeepsPrimaryActionsReachableAt320DpAndLargeFont() {
        val exercise = exercise().copy(stableKey = "", name = "")
        val metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise)
        sizedContent {
            RuntimeMetadataExerciseEditorDialog(
                initial = ExerciseRuntimeMetadataEditorData(
                    exercise = exercise,
                    metadata = metadata,
                    options = RuntimeMetadataEditorOptions.from(listOf(metadata))
                ),
                onDismiss = {},
                onSave = {}
            )
        }

        compose.onNodeWithText("운동 이름").performTextInput("긴 이름의 사용자 운동")
        compose.onNodeWithText("저장").assertIsDisplayed()
        compose.onNodeWithText("닫기").assertIsDisplayed()
        val saveBounds = compose.onNodeWithText("저장").getUnclippedBoundsInRoot()
        assertTrue(saveBounds.right <= 320.dp)
    }

    private fun sizedContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 1.5f)
            ) {
                TrainingTrackPlannerTheme {
                    Box(Modifier.width(320.dp)) {
                        androidx.compose.foundation.layout.Column {
                            content()
                        }
                    }
                }
            }
        }
    }

    private fun exercise(): Exercise =
        Exercise(
            stableKey = "fixture_press",
            name = "인클라인 덤벨 프레스",
            category = "근력",
            movementPattern = "PUSH_HORIZONTAL",
            movementCategory = "STRENGTH",
            primaryMuscles = "대흉근",
            secondaryMuscles = "삼두근,전면 삼각근",
            equipment = "덤벨",
            forceType = "PUSH",
            bodyRegion = "상체",
            laterality = "BILATERAL",
            axialLoadLevel = "LOW",
            trainingRole = "SECONDARY_STRENGTH",
            metadataConfidence = "HIGH"
        )

    private fun request(): ProgramSkeletonRequest =
        ProgramSkeletonRequest(
            name = "presentation",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 3,
            sessionMinutes = 45,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.6,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = 4
        )
}

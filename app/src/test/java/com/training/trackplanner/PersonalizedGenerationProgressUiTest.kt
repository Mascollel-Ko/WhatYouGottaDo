package com.training.trackplanner

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import com.training.trackplanner.data.*
import com.training.trackplanner.data.personalized.*
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w320dp-h640dp")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class PersonalizedGenerationProgressUiTest {
    @get:Rule val compose = createComposeRule()
    private val question = PersonalizedPlanningQuestion("INTENT", "목표", listOf(PersonalizedPlanningAnswerOption("MIXED", "함께 훈련")))
    private val state = mutableStateOf<ProgramBuildProgressState>(ProgramBuildProgressState.Idle)
    private var calls = 0
    private var frozen: Map<String, String>? = null
    private fun flow() {
        compose.setContent {
            var pending by remember { mutableStateOf(true) }
            var answers by remember { mutableStateOf(emptyMap<String, String>()) }
            TrainingTrackPlannerTheme {
                when (val progress = state.value) {
                    is ProgramBuildProgressState.Running -> PersonalizedGenerationProgressDialog(progress)
                    is ProgramBuildProgressState.Completed -> Text("생성된 편집 미리보기")
                    is ProgramBuildProgressState.Failed -> ProgramBuildProgressCard(progress) { state.value = ProgramBuildProgressState.Idle }
                    else -> if (pending) PersonalizedPlanningQuestionDialog(listOf(question), answers,
                        { id, value -> answers = answers + (id to value) }, {
                            frozen = answers.toMap(); pending = false; calls++
                            state.value = ProgramBuildProgressState.Running(5, PersonalizedPlannerStage.INPUT.message)
                        }, {})
                }
            }
        }
    }
    private fun generate() {
        compose.onNodeWithText("함께 훈련").performClick()
        compose.onNodeWithText("이 답변으로 생성").performClick()
    }

    @Test fun answeredGenerateImmediatelyReplacesQuestionsAndFreezesAnswers() {
        flow()
        compose.onNodeWithText("이 답변으로 생성").assertIsNotEnabled()
        generate()
        compose.onNodeWithText("함께 훈련").assertDoesNotExist()
        compose.onNodeWithText("이 답변으로 생성").assertDoesNotExist()
        compose.onNodeWithText("프로그램을 구성하는 중입니다").assertIsDisplayed()
        compose.onNodeWithTag("personalized-progress-bar").assertExists()
        compose.onNodeWithText("5%").assertIsDisplayed()
        assertEquals(mapOf("INTENT" to "MIXED"), frozen); assertEquals(1, calls)
        compose.onNodeWithText("잠시만 기다려 주세요.").assertDoesNotExist()
    }

    @Test fun duplicateClickActionCannotSubmitTwiceBeforeRecomposition() {
        compose.setContent { TrainingTrackPlannerTheme {
            PersonalizedPlanningQuestionDialog(listOf(question), mapOf("INTENT" to "MIXED"), { _, _ -> }, { calls++ }, {})
        } }
        val action = compose.onNodeWithText("이 답변으로 생성").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { action(); action() }
        assertEquals(1, calls)
    }

    @Test fun actualRunningPercentageAndMessageRenderWithoutTimer() {
        state.value = ProgramBuildProgressState.Running(20, "분석 단계")
        flow()
        for ((percent, message) in listOf(20 to "분석 단계", 50 to "배치 단계", 80 to "회복 확인 단계")) {
            compose.runOnIdle { state.value = ProgramBuildProgressState.Running(percent, message) }
            compose.onNodeWithText("$percent%").assertIsDisplayed()
            compose.onNodeWithText(message).assertIsDisplayed()
            compose.onNodeWithTag("personalized-progress-bar").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(percent / 100f, 0f..1f))
        }
    }

    @Test fun completionClosesModalAndExposesPreview() {
        flow(); generate()
        compose.runOnIdle { state.value = ProgramBuildProgressState.Completed(ProgramOptimizationSummary()) }
        compose.onNodeWithText("프로그램을 구성하는 중입니다").assertDoesNotExist()
        compose.onNodeWithText("생성된 편집 미리보기").assertIsDisplayed()
    }

    @Test fun failureClosesModalAndExposesExistingRetryWithoutQuestions() {
        flow(); generate()
        compose.runOnIdle { state.value = ProgramBuildProgressState.Failed("실제 생성 오류") }
        compose.onNodeWithText("프로그램을 구성하는 중입니다").assertDoesNotExist()
        compose.onNodeWithText("함께 훈련").assertDoesNotExist()
        compose.onNodeWithText("실제 생성 오류").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertIsDisplayed()
    }

    @Test fun narrowDialogAtLargeFontKeepsExplanatoryTextReadableWithoutClipping() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.3f)) {
                TrainingTrackPlannerTheme { PersonalizedGenerationProgressDialog(ProgramBuildProgressState.Running(80, PersonalizedPlannerStage.FEASIBILITY.message)) }
            }
        }
        compose.onNodeWithText("최적의 프로그램이 아닐 수 있습니다.").assertIsDisplayed()
        val body = compose.onNodeWithText("생성 결과를 바탕으로 자신에게 맞게 프로그램을 조정해 보세요.")
        body.assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            action(layouts)
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { assertFalse(it.hasVisualOverflow); assertTrue(it.lineCount < 10) }
        }
        compose.onNodeWithText("80%").assertIsDisplayed()
    }
}

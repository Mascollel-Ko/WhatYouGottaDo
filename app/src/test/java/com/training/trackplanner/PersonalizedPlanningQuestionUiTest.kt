package com.training.trackplanner

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.training.trackplanner.data.personalized.*
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR")
class PersonalizedPlanningQuestionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allQuestionsMustBeAnsweredAndScrollingReachesTheLastAnswer() {
        val questions = listOf(
            PersonalizedPlanningQuestion(QUESTION_STRENGTH_INTENT, "근력 목표", (1..4).map {
                PersonalizedPlanningAnswerOption("STRENGTH_$it", "근력 선택 $it")
            }),
            PersonalizedPlanningQuestion(QUESTION_BADMINTON_INTENT, "배드민턴 포함", listOf(
                PersonalizedPlanningAnswerOption("ENABLED", "포함"),
                PersonalizedPlanningAnswerOption("DISABLED", "포함하지 않음"))),
            PersonalizedPlanningQuestion(QUESTION_FREE_WEIGHT, "프리웨이트", listOf(
                PersonalizedPlanningAnswerOption("WILLING", "포함 가능"),
                PersonalizedPlanningAnswerOption("AVOID", "피하고 싶음"))))
        var generated: Map<String, String>? = null
        compose.setContent {
            var answers by remember { mutableStateOf(emptyMap<String, String>()) }
            TrainingTrackPlannerTheme {
                PersonalizedPlanningQuestionDialog(questions, answers,
                    onAnswer = { id, value -> answers = answers + (id to value) },
                    onGenerate = { generated = answers }, onDismiss = {})
            }
        }
        compose.onNodeWithText("이 답변으로 생성").assertIsNotEnabled()
        compose.onNodeWithText("근력 선택 1").performScrollTo().performClick()
        compose.onNodeWithText("포함").performScrollTo().performClick()
        compose.onNodeWithText("이 답변으로 생성").assertIsNotEnabled()
        compose.onNodeWithText("포함 가능").performScrollTo().performClick()
        compose.onNodeWithText("이 답변으로 생성").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(3, generated!!.size) }
    }

    @Test fun dismissDoesNotGenerate() {
        var dismissed = false
        var generated = false
        compose.setContent {
            TrainingTrackPlannerTheme {
                PersonalizedPlanningQuestionDialog(listOf(PersonalizedPlanningQuestion(
                    "question", "질문", listOf(PersonalizedPlanningAnswerOption("YES", "포함")))),
                    emptyMap(), { _, _ -> }, { generated = true }, { dismissed = true })
            }
        }
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertFalse(generated)
        }
    }

    @Test fun interruptionQuestionsAcceptUnsureAndRemainScrollableAfterCoreAnswers() {
        val ids=listOf(QUESTION_STRENGTH_INTENT,QUESTION_BADMINTON_INTENT,QUESTION_FREE_WEIGHT,
            QUESTION_INTERRUPTION_CAUSE,QUESTION_INTERRUPTION_FREQUENCY)
        val questions=ids.mapIndexed { i,id -> PersonalizedPlanningQuestion(id,"사전 질문 ${i+1}",
            (1..5).map { PersonalizedPlanningAnswerOption(if (it==5) "UNSURE" else "OPTION_$it","질문 ${i+1} 선택 $it") }) }
        var generated: Map<String,String>?=null
        compose.setContent {
            var answers by remember { mutableStateOf(emptyMap<String,String>()) }
            TrainingTrackPlannerTheme { PersonalizedPlanningQuestionDialog(questions,answers,
                { id,value -> answers=answers+(id to value) },{ generated=answers },{}) }
        }
        for (i in 1..4) compose.onNodeWithText("질문 $i 선택 5").performScrollTo().performClick()
        compose.onNodeWithText("이 답변으로 생성").assertIsNotEnabled()
        compose.onNodeWithText("질문 5 선택 5").performScrollTo().performClick()
        compose.onNodeWithText("이 답변으로 생성").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(ids.associateWith { "UNSURE" },generated) }
    }

    @Test @Config(sdk=[34],qualifiers="en")
    fun interruptionPromptAndAnswersUseGeneratedEnglishAuthority() {
        compose.setContent { TrainingTrackPlannerTheme {
            PersonalizedPlanningQuestionDialog(listOf(PersonalizedPlanningQuestion(QUESTION_INTERRUPTION_FREQUENCY,
                "외부 일정 때문에 운동량이 줄어드는 일이 얼마나 자주 있나요?",listOf(
                    PersonalizedPlanningAnswerOption("FREQUENT","한 달에 여러 번"),
                    PersonalizedPlanningAnswerOption("UNSURE","잘 모르겠음")))),emptyMap(),{ _,_ -> },{},{})
        } }
        compose.onNodeWithText("How often do outside commitments reduce your training?").assertExists()
        compose.onNodeWithText("Several times a month").assertExists()
        compose.onNodeWithText("Not sure").assertExists()
    }
}

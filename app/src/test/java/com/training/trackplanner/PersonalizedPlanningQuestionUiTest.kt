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
}

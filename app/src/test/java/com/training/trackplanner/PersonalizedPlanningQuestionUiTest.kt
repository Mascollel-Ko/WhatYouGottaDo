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

    @Test @Config(sdk=[34],qualifiers="ko-rKR-w320dp-h640dp")
    fun narrowRowsAreSingleLineAndAnswersSurviveScrollAndRecomposition() {
        val ids=listOf(QUESTION_STRENGTH_INTENT,QUESTION_BADMINTON_INTENT,QUESTION_FREE_WEIGHT)+
            (0..14).map { weekCauseQuestionId(java.time.LocalDate.parse("2026-08-24").minusWeeks(it.toLong())) }
        val questions=ids.map { PersonalizedPlanningQuestion(it,"전체 의미를 보존한 긴 질문",listOf(
            PersonalizedPlanningAnswerOption("YES","의도적으로 쉬거나 디로드함"),PersonalizedPlanningAnswerOption("NO","다른 이유"))) }
        val answers=mutableStateOf(emptyMap<String,String>())
        val scale=mutableStateOf(1f)
        compose.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,scale.value)) {
                TrainingTrackPlannerTheme { PersonalizedPlanningQuestionDialog(questions,answers.value,
                    { id,value -> answers.value=answers.value+(id to value) },{},{}) }
            }
        }
        compose.onNodeWithText("의도적으로 쉬거나 디로드함").assertDoesNotExist()
        compose.onNodeWithTag("preflight_questions").assert(hasScrollAction())
        for(id in ids.take(3)) compose.onNodeWithTag("question_row_$id").assertIsDisplayed()
        compose.onNodeWithTag("question_answer_${ids.first()}").performClick()
        compose.onNodeWithText("의도적으로 쉬거나 디로드함").performClick()
        compose.runOnIdle { assertEquals(mapOf(ids.first() to "YES"),answers.value); scale.value=1.3f }
        compose.onNodeWithTag("question_answer_${ids.last()}").performScrollTo().performClick()
        compose.onNodeWithText("다른 이유").performClick()
        compose.onNodeWithTag("question_answer_${ids.first()}").performScrollTo()
        compose.runOnIdle { assertEquals(mapOf(ids.first() to "YES",ids.last() to "NO"),answers.value) }
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onAllNodes(hasText("근력 목표") or hasText("의도적으로 쉬거나 디로드함"),useUnmergedTree=true)
            .fetchSemanticsNodes().forEach { node ->
                node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { null }?.action?.invoke(layouts)
            }
        assertEquals(2,layouts.size)
        assertTrue(layouts.all { it.lineCount==1 })
        compose.onNodeWithText("취소").assertIsDisplayed()
        compose.onNodeWithText("이 답변으로 생성").assertIsDisplayed().assertIsNotEnabled()
    }

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
        compose.onNodeWithTag("question_answer_$QUESTION_STRENGTH_INTENT").performClick()
        compose.onNodeWithText("근력 선택 1").performScrollTo().performClick()
        compose.onNodeWithTag("question_answer_$QUESTION_BADMINTON_INTENT").performClick()
        compose.onNodeWithText("포함").performScrollTo().performClick()
        compose.onNodeWithText("이 답변으로 생성").assertIsNotEnabled()
        compose.onNodeWithTag("question_answer_$QUESTION_FREE_WEIGHT").performClick()
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
            "INTERRUPTION_CAUSE_2026-08-24","INTERRUPTION_CAUSE_2026-08-17","INTERRUPTION_CAUSE_2026-08-10",QUESTION_INTERRUPTION_FREQUENCY)
        val questions=ids.mapIndexed { i,id -> PersonalizedPlanningQuestion(id,"사전 질문 ${i+1}",
            (1..5).map { PersonalizedPlanningAnswerOption(if (it==5) "UNSURE" else "OPTION_$it","질문 ${i+1} 선택 $it") }) }
        var generated: Map<String,String>?=null
        compose.setContent {
            var answers by remember { mutableStateOf(emptyMap<String,String>()) }
            TrainingTrackPlannerTheme { PersonalizedPlanningQuestionDialog(questions,answers,
                { id,value -> answers=answers+(id to value) },{ generated=answers },{}) }
        }
        for (i in 1..6) {
            compose.onNodeWithTag("question_answer_${ids[i-1]}").performScrollTo().performClick()
            compose.onNodeWithText("질문 $i 선택 5").performScrollTo().performClick()
        }
        compose.onNodeWithText("이 답변으로 생성").assertIsNotEnabled()
        compose.onNodeWithTag("question_answer_${ids.last()}").performScrollTo().performClick()
        compose.onNodeWithText("질문 7 선택 5").performScrollTo().performClick()
        compose.onNodeWithText("이 답변으로 생성").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(ids.associateWith { "UNSURE" },generated) }
    }

    @Test @Config(sdk=[34],qualifiers="en")
    fun interruptionPromptAndAnswersUseGeneratedEnglishAuthority() {
        compose.setContent { TrainingTrackPlannerTheme {
            PersonalizedPlanningQuestionDialog(listOf(PersonalizedPlanningQuestion(QUESTION_INTERRUPTION_FREQUENCY,
                "외부 일정 때문에 운동 계획이 깨지는 일이 얼마나 자주 있나요?",listOf(
                    PersonalizedPlanningAnswerOption("FREQUENT","한 달에 여러 번"),
                    PersonalizedPlanningAnswerOption("UNSURE","잘 모르겠음")))),emptyMap(),{ _,_ -> },{},{})
        } }
        compose.onNodeWithText("Schedule disruption frequency").assertExists()
        compose.onNodeWithTag("question_answer_$QUESTION_INTERRUPTION_FREQUENCY").performClick()
        compose.onNodeWithText("Several times a month").assertExists()
        compose.onNodeWithText("Not sure").assertExists()
    }

    @Test @Config(sdk=[34],qualifiers="en")
    fun datedWeekQuestionUsesGeneratedEnglishAndStoresCanonicalWeekId() {
        val start=java.time.LocalDate.parse("2026-08-17")
        val end=start.plusDays(6)
        var selected: Pair<String,String>?=null
        compose.setContent { TrainingTrackPlannerTheme {
            PersonalizedPlanningQuestionDialog(listOf(PersonalizedPlanningQuestion(weekCauseQuestionId(start),
                "${start}~${end}에는 평소보다 운동량이 크게 줄었습니다. 이 주의 주된 이유는 무엇이었나요?",
                listOf(PersonalizedPlanningAnswerOption("UNKNOWN","기억나지 않음"),
                    PersonalizedPlanningAnswerOption("INTENTIONAL_DELOAD","의도적으로 쉬거나 디로드함")))),
                emptyMap(),{ id,value -> selected=id to value },{},{})
        } }
        compose.onNodeWithText("8/17~8/23 workload reduction reason").assertExists()
        compose.onNodeWithTag("question_answer_${weekCauseQuestionId(start)}").performClick()
        compose.onNodeWithText("Intentional rest or deload").assertExists()
        compose.onNodeWithText("I do not remember").performClick()
        compose.runOnIdle { assertEquals("INTERRUPTION_CAUSE_2026-08-17" to "UNKNOWN",selected) }
    }
}

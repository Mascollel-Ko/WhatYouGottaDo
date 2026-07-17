package com.training.trackplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppExplanationUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeEntryAppearsOnceBelowProgramActionAndNavigates() {
        var opened = false
        sizedContent(width = 360.dp, height = 420.dp) {
            CompactHomeActionGroup(
                onRecord = {},
                onProgram = {},
                onOpenAppExplanation = { opened = true }
            )
        }

        compose.onAllNodesWithText("이 앱이 분석하는 것 보기").assertCountEquals(1)
        val programBounds = compose.onNodeWithText("프로그램으로 시작하기")
            .getUnclippedBoundsInRoot()
        val entryBounds = compose.onNodeWithText(
            "이 앱이 분석하는 것 보기",
            useUnmergedTree = true
        )
            .getUnclippedBoundsInRoot()
        assertTrue(entryBounds.top >= programBounds.bottom)
        assertTrue(entryBounds.bottom - entryBounds.top <= 36.dp)

        compose.onNodeWithContentDescription("이 앱이 분석하는 내용 설명 열기")
            .performClick()
        assertTrue(opened)
    }

    @Test
    fun explanationShowsApprovedSectionsAndFollowUpNavigation() {
        var destination: AppInfoRoute? = null
        var backCount = 0
        sizedContent(width = 360.dp, height = 760.dp) {
            AppExplanationScreen(
                onBack = { backCount += 1 },
                onOpenAnalysisGuide = { destination = AppInfoRoute.AnalysisGuide },
                onOpenCalculationPrinciples = {
                    destination = AppInfoRoute.CalculationPrinciples
                }
            )
        }

        compose.onNodeWithText("앱 설명").assertIsDisplayed()
        val hero = compose.onNodeWithText("운동 기록을 분석과 계획으로")
        hero.assertIsDisplayed()
        val heroBounds = hero.getUnclippedBoundsInRoot()
        assertTrue(heroBounds.bottom - heroBounds.top <= 36.dp)
        featureTitles().forEach { title ->
            compose.onAllNodesWithText(title).assertCountEquals(1)
            val titleBounds = compose.onNodeWithText(title, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            assertTrue(titleBounds.bottom - titleBounds.top <= 36.dp)
        }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("점수는 진단이 아닙니다"))
        compose.onAllNodesWithText("점수는 진단이 아닙니다").assertCountEquals(1)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("분석 결과 읽는 법"))
        compose.onNodeWithText("분석 결과 읽는 법").performClick()
        assertEquals(AppInfoRoute.AnalysisGuide, destination)

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("계산 원리와 한계 보기"))
        compose.onNodeWithText("계산 원리와 한계 보기").performClick()
        assertEquals(AppInfoRoute.CalculationPrinciples, destination)

        compose.onNodeWithContentDescription("뒤로 가기").performClick()
        assertEquals(1, backCount)
    }

    @Test
    fun guideRemainsScrollableAtCompactAccessibleSize() {
        sizedContent(width = 320.dp, height = 640.dp, fontScale = 1.3f, darkTheme = true) {
            AnalysisGuideScreen(onBack = {})
        }
        compose.onNodeWithText("분석 결과 읽는 법").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(
            hasText("기록이 적은 초기에는 개인 기준이 충분하지 않아 결과가 제한적일 수 있습니다.")
        )
        compose.onNodeWithText(
            "기록이 적은 초기에는 개인 기준이 충분하지 않아 결과가 제한적일 수 있습니다."
        ).assertIsDisplayed()
    }

    @Test
    fun principlesRemainScrollableAndHandleMissingBrowserAtCompactAccessibleSize() {
        sizedContent(width = 320.dp, height = 640.dp, fontScale = 1.3f) {
            CalculationPrinciplesScreen(
                onBack = {},
                onOpenPublicProtocols = { false }
            )
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("공개 프로토콜 문서 보기"))
        compose.onNodeWithText("공개 프로토콜 문서 보기").performClick()
        compose.onNodeWithText("브라우저를 열 수 없습니다.").assertIsDisplayed()
    }

    @Test
    fun guideSectionsAreComplete() {
        sizedContent(width = 360.dp, height = 720.dp) {
            AnalysisGuideScreen(onBack = {})
        }
        guideTitles().forEach { title ->
            compose.onAllNodesWithText(title).assertCountEquals(1)
        }
    }

    @Test
    fun protocolFamiliesAreComplete() {
        sizedContent(width = 360.dp, height = 720.dp) {
            CalculationPrinciplesScreen(
                onBack = {},
                onOpenPublicProtocols = { true }
            )
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("공개하는 주요 프로토콜"))
        protocolFamilies().forEach { family ->
            compose.onAllNodesWithText(family).assertCountEquals(1)
        }
    }

    private fun sizedContent(
        width: Dp,
        height: Dp,
        fontScale: Float = 1f,
        darkTheme: Boolean = false,
        content: @Composable () -> Unit
    ) {
        compose.setContent {
            SizedTheme(width, height, fontScale, darkTheme, content)
        }
    }

    private fun featureTitles(): List<String> = listOf(
        "피로를 다섯 가지로 구분합니다",
        "피로한 근육을 보여줍니다",
        "연결조직의 부하를 따로 봅니다",
        "배드민턴 훈련을 분석합니다",
        "근력운동량을 계산합니다",
        "운동 프로그램 초안을 만듭니다"
    )

    private fun guideTitles(): List<String> = listOf(
        "전체 피로 상태",
        "다섯 가지 피로 축",
        "피로한 근육",
        "연결조직 분석",
        "훈련 분산",
        "점수가 높을 때"
    )

    private fun protocolFamilies(): List<String> = listOf(
        "전체 피로 상태 계산",
        "다섯 가지 피로 축 계산",
        "연결조직 부하와 회복 추정",
        "배드민턴 훈련 분류와 운동량",
        "근력운동 분류와 운동량",
        "자동 프로그램 생성 규칙"
    )
}

@Composable
private fun SizedTheme(
    width: Dp,
    height: Dp,
    fontScale: Float = 1f,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(currentDensity.density, fontScale)
    ) {
        TrainingTrackPlannerTheme(darkTheme = darkTheme) {
            Box(Modifier.width(width).height(height)) {
                content()
            }
        }
    }
}

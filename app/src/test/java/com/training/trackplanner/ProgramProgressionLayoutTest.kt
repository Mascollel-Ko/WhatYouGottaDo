package com.training.trackplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.*
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-w411dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgramProgressionLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun width320Normal() = checkWidth(320, 1f)
    @Test fun width320Large() = checkWidth(320, 1.3f)
    @Test fun width360Normal() = checkWidth(360, 1f)
    @Test fun width360Large() = checkWidth(360, 1.3f)
    @Test fun width411Normal() = checkWidth(411, 1f)
    @Test fun width411Large() = checkWidth(411, 1.3f)

    private fun checkWidth(width: Int, font: Float) {
        val suggestion = ProgressionSuggestion(applicationId = "app", trackId = "track", sourceEntryId = 1, targetEntryId = 2,
            previousActualKg = 140.0, currentPlanKg = 145.0, suggestedKg = 142.5, judgmentRpe = 7.0,
            direction = ProgressionDirection.INCREASE, reasons = "TARGET_COMPLETED_MANAGEABLE_EFFORT", evidenceHash = "hash", rule = ProgressionRule())
        val settings = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, font)) {
                TrainingTrackPlannerTheme {
                    Box(Modifier.width(width.dp)) {
                        if (settings.value) ProgressionSettingsContent(
                            ProgramProgressionTrack(id = "track", programStableKey = "program", exerciseStableKey = "squat", label = "스쿼트 · 5회 · 메인", mode = ProgressionMode.CUSTOM),
                            ProgramProgressionItem(1, trackId = "track", signature = ProgressionSignature("squat", 3, "5|5|5", 140.0, null, null)), emptyList()) { _, _, _, _, _ -> }
                        else ProgressionSuggestionContent(suggestion) { _, _ -> }
                    }
                }
            }
        }
        for (label in listOf("제안 적용", "현재 계획 유지", "직접 입력")) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            assertSingleLineWithin(label, width)
        }
        val apply = compose.onNodeWithTag("progression-apply").getUnclippedBoundsInRoot()
        val keep = compose.onNodeWithTag("progression-keep").getUnclippedBoundsInRoot()
        assertTrue(keep.top > apply.top)
        compose.runOnIdle { settings.value = true }
        for (label in listOf("진행 세션", "자동으로 연결", "새 진행 세션", "연결하지 않음")) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            assertSingleLineWithin(label, width)
        }
        compose.onNodeWithText("자동").performScrollTo().performClick()
        for (label in listOf("메인", "보조")) assertSingleLineWithin(label, width)
        compose.onNodeWithText("메인").performClick()
        compose.onNodeWithText("내 기준").performScrollTo().performClick()
        for (label in listOf("앱 기준", "직접 판단")) assertSingleLineWithin(label, width)
        compose.onNodeWithText("직접 판단").performClick()
        assertSingleLineWithin("직접 판단", width)
        compose.onNodeWithTag("progression-save").performScrollTo().assertIsDisplayed()
        assertSingleLineWithin("저장", width)
    }

    @Test fun zeroWeightManualControlOpensSessionChoicesAndStoresNewSession() {
        val draft = mutableStateOf(manualSessionDraft(0.0))
        val original = draft.value.sessionKey("item-0")
        compose.setContent {
            TrainingTrackPlannerTheme {
                Box(Modifier.width(320.dp)) {
                    ProgressionDraftControl(draft.value.items.single { it.localId == "item-0" }, draft.value) { draft.value = it }
                }
            }
        }
        compose.onNodeWithTag("progression-session-control").assertIsDisplayed().performClick()
        compose.onNodeWithText("새 진행 세션").performScrollTo().performClick()
        compose.onNodeWithTag("progression-save").performScrollTo().performClick()
        compose.runOnIdle {
            assertNotEquals(original, draft.value.sessionKey("item-0"))
            assertEquals(ProgressionLinkMode.SEPARATE, draft.value.items.single { it.localId == "item-0" }.progressionBinding!!.linkMode)
        }
    }
    private fun assertSingleLineWithin(label: String, width: Int) {
        val node = compose.onNodeWithText(label, useUnmergedTree = true)
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue("$label exceeds $width dp", bounds.left >= 0.dp && bounds.right <= width.dp)
        val result = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
        assertEquals(1, result.single().lineCount)
        val layout = result.single()
        // Compose can reuse a paragraph measured at the button's max width, then shrink
        // the Text node to its glyph width. Check rendered line extents, not paragraph width.
        assertFalse("$label ellipsized", layout.isLineEllipsized(0))
        assertTrue("$label clipped horizontally", layout.getLineLeft(0) >= -1f && layout.getLineRight(0) <= layout.size.width + 1f)
        assertTrue("$label clipped vertically", layout.getLineTop(0) >= -1f && layout.getLineBottom(0) <= layout.size.height + 1f)
    }
}

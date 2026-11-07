package com.training.trackplanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.data.ProgramEditScope
import com.training.trackplanner.data.PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE
import com.training.trackplanner.data.personalized.PostGenerationFixture
import com.training.trackplanner.data.v22.BBalanced12WeekEngine
import com.training.trackplanner.data.v22.BVolumePattern
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="ko-w320dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgramEditScopeUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun scopeSurvivesSaveRestoreAtNarrowWidthAndLargeFont() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {
            var scope by rememberSaveable { mutableStateOf(ProgramEditScope.ALL_WEEKS) }
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                TrainingTrackPlannerTheme { Box(Modifier.width(288.dp)) { ProgramEditScopeControl(scope,true) { scope=it } } }
            }
        }
        compose.onNodeWithText("전체 주에 적용 ✓").assertIsDisplayed()
        compose.onNodeWithTag("scope-week").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("주별 수정 ✓").assertIsDisplayed()
        val bounds=compose.onNodeWithTag("scope-all").getUnclippedBoundsInRoot()
        assertTrue(bounds.right-bounds.left<=288.dp)
    }
    @Test fun divergentDraftDisablesAllWeeksWithoutMutation() {
        var calls=0
        compose.setContent { TrainingTrackPlannerTheme { ProgramEditScopeControl(ProgramEditScope.INDIVIDUAL_WEEK,false) { calls++ } } }
        compose.onNodeWithTag("scope-all").assertIsNotEnabled()
        compose.onNodeWithText("선택한 주기화에서는 주별 수정만 사용할 수 있습니다.").assertIsDisplayed()
        assertEquals(0,calls)
    }
    @Test fun previewDefaultsToIndividualWeekAndBlocksOnlyMismatchedExercise() {
        val fixture=PostGenerationFixture
        val base=fixture.plan(listOf(fixture.row("press",1,id="press"),fixture.row("row",1,id="row",order=2)),horizon=4)
        val metadata=JSONObject().put("schema",BBalanced12WeekEngine.BLOCK_SCHEMA)
            .put("blockSpecs",JSONArray().put(JSONObject().put("pattern",BVolumePattern.STEADY.name))).toString()
        val draft=base.copy(
            planningMetadataJson=metadata,
            items=base.items.map { item ->
                if(item.weekNumber==3 && item.exerciseStableKey=="press") item.copy(
                    weightKg=5.0,setPrescriptions=item.setPrescriptions.map { it.copy(weightKg=5.0) }) else item
            }
        )
        compose.setContent { TrainingTrackPlannerTheme {
            ProgramSkeletonPreview(draft,emptyList(),emptyMap()) {}
        } }
        compose.onNodeWithText("주별 수정 ✓").assertIsDisplayed()
        compose.onNodeWithTag("scope-all").performClick()
        compose.onNodeWithText("세트·반복·중량 수정은 동일한 모든 주에 적용됩니다.").assertIsDisplayed()
        compose.onAllNodesWithText("수정")[0].performClick()
        assertEquals(PROGRAM_ALL_WEEKS_MISMATCH_MESSAGE,ShadowToast.getTextOfLatestToast())
    }
}

package com.training.trackplanner

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="ko-rKR-w411dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanningSummaryUiTest {
    @get:Rule val compose=createComposeRule()
    private val model=PlanningSummaryPresenter.present(PlanningSummaryFixture.populated())

    @Test fun collapsedByDefaultExpandsExactlyFiveSectionsAndRestoresState() {
        val restore=StateRestorationTester(compose)
        restore.setContent { TrainingTrackPlannerTheme { Column(Modifier.verticalScroll(rememberScrollState())) { PlanningSummaryCard(model) } } }
        compose.onNodeWithText("기록 기반 계획").assertIsDisplayed()
        compose.onNodeWithTag("summary-section-OBSERVED_STATE").assertDoesNotExist()
        compose.onNodeWithTag("summary-toggle").performClick()
        PlanningSummarySection.entries.forEach { compose.onNodeWithTag("summary-section-${it.name}").assertExists() }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("summary-section-FINAL_ALLOCATION").assertExists()
        compose.onNodeWithTag("summary-toggle").performClick()
        compose.onNodeWithTag("summary-section-OBSERVED_STATE").assertDoesNotExist()
    }

    @Test fun koreanAndEnglishNarrowNormalAndLargeFontsStayReadable() {
        var language by mutableStateOf("ko")
        var width by mutableStateOf(320)
        var font by mutableFloatStateOf(1f)
        compose.setContent {
            val base=LocalContext.current
            val config=Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val context=base.createConfigurationContext(config)
            val density=LocalDensity.current
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides config,LocalDensity provides Density(density.density,font)) {
                TrainingTrackPlannerTheme { Column(Modifier.width(width.dp).verticalScroll(rememberScrollState())) { PlanningSummaryCard(model) } }
            }
        }
        compose.onNodeWithTag("summary-toggle").performClick()
        for(lang in listOf("ko","en")) for(dp in listOf(320,411)) for(scale in listOf(1f,1.3f)) {
            compose.runOnIdle { language=lang; width=dp; font=scale }
            compose.waitForIdle()
            compose.onNodeWithText(if(lang=="ko") "최근 기록에서 본 상태" else "Observed in recent records").assertExists()
            val card=compose.onNodeWithTag("planning-summary").getUnclippedBoundsInRoot()
            assertTrue(card.right-card.left<=dp.dp)
            val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),useUnmergedTree=true).fetchSemanticsNodes()
            assertTrue(nodes.isNotEmpty())
            nodes.forEach { node ->
                val layouts=mutableListOf<TextLayoutResult>()
                node.config.getOrElseNullable(SemanticsActions.GetTextLayoutResult) { null }?.action?.invoke(layouts)
                layouts.forEach { layout ->
                    assertFalse("$lang/$dp/$scale: ${layout.layoutInput.text}; size=${layout.size}; paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}; constraints=${layout.layoutInput.constraints}; lines=${layout.lineCount}; widthOverflow=${layout.didOverflowWidth}; heightOverflow=${layout.didOverflowHeight}",layout.hasVisualOverflow)
                    assertTrue("Vertical-looking text: ${layout.layoutInput.text}",layout.lineCount<=6)
                }
            }
        }
    }
}

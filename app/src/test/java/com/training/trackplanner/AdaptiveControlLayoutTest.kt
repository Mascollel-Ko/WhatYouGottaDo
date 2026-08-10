package com.training.trackplanner

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.localization.LocalizedPresentation
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class AdaptiveControlLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun initialProfileWrapsWholeEnglishOptionsAt320DpAndLargeFont() {
        sizedContent(320.dp, 1.5f) {
            InitialProfileDialog(InitialUserProfile(), onDismiss = {}, onSave = {})
        }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Recent exercise hiatus"))
        val oneToTwo = compose.onNodeWithText("1-2 weeks").assertIsDisplayed().getUnclippedBoundsInRoot()
        val eightOrMore = compose.onNodeWithText("8 weeks or more").assertIsDisplayed().getUnclippedBoundsInRoot()

        assertTrue(oneToTwo.right - oneToTwo.left > oneToTwo.bottom - oneToTwo.top)
        assertTrue(eightOrMore.right - eightOrMore.left > eightOrMore.bottom - eightOrMore.top)
        assertTrue(eightOrMore.top > oneToTwo.top)
        assertTrue(oneToTwo.right <= 320.dp && eightOrMore.right <= 320.dp)
    }

    @Test
    fun exerciseFiltersAndActionsWrapInside360DpViewport() {
        val categories = listOf("전체", "근력", "기능성", "유산소/운동", "배드민턴")
        val translator = MetadataTranslator.from(englishContext())
        val categoryLabels = categories.map { category ->
            val translated = if (category == "전체") category
                else translator.translate("exercise.category", category).orEmpty()
            LocalizedPresentation.uiText(englishContext(), translated)
        }
        sizedContent(360.dp, 1.3f) {
            Column {
                ExerciseCategoryFilterControls(categories, "전체", onSelect = {})
                ExerciseManagementControls(
                    manageMode = true,
                    showHidden = false,
                    onAdd = {},
                    onToggleManage = {},
                    onToggleHidden = {}
                )
            }
        }

        (categoryLabels + listOf("Add exercise", "Management end", "Hidden view")).forEach { label ->
            val bounds = compose.onNodeWithText(label).assertExists().getUnclippedBoundsInRoot()
            assertTrue("$label exceeds viewport", bounds.left >= 0.dp && bounds.right <= 360.dp)
            assertTrue("$label fragmented", bounds.right - bounds.left > bounds.bottom - bounds.top)
        }
        val add = compose.onNodeWithText("Add exercise").getUnclippedBoundsInRoot()
        val hidden = compose.onNodeWithText("Hidden view").getUnclippedBoundsInRoot()
        assertTrue(hidden.top > add.top)
    }

    @Test
    fun recordNavigationKeepsEnglishControlsReadableAt320Dp() {
        sizedContent(320.dp, 1.5f) {
            RecordDateSwitcher(
                date = LocalDate.of(2026, 8, 11),
                onPrevious = {},
                onNext = {},
                onOpenCalendar = {}
            )
        }

        listOf("previous day", "the next day", "calendar").forEach { label ->
            val bounds = compose.onNodeWithText(label).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$label exceeds viewport", bounds.left >= 0.dp && bounds.right <= 320.dp)
            assertTrue("$label fragmented", bounds.right - bounds.left > bounds.bottom - bounds.top)
        }
        val date = compose.onNodeWithText("2026-08-11").getUnclippedBoundsInRoot()
        val previous = compose.onNodeWithText("previous day").getUnclippedBoundsInRoot()
        assertTrue(previous.top > date.top)
    }

    private fun sizedContent(width: Dp, fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                TrainingTrackPlannerTheme {
                    Box(Modifier.width(width)) {
                        content()
                    }
                }
            }
        }
    }

    private fun englishContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        )
    }
}

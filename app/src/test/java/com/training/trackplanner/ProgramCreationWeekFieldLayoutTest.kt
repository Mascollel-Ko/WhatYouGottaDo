package com.training.trackplanner

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgramCreationWeekFieldLayoutTest {
    @get:Rule val compose = createComposeRule()
    @OptIn(ExperimentalLayoutApi::class)
    @Test fun temporalDropdownValueAndPopupFitWithActualEditorInsets() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val locales = listOf(Locale.KOREAN, Locale.ENGLISH).associateWith { base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(it) }) }
        val language = mutableStateOf(Locale.KOREAN)
        val width = mutableStateOf(320)
        val font = mutableStateOf(1f)
        val week = mutableStateOf(3)
        compose.setContent {
            val density = LocalDensity.current
            val context = locales.getValue(language.value)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration,
                LocalDensity provides Density(density.density, font.value)) {
                TrainingTrackPlannerTheme {
                    Column(Modifier.width(width.value.dp).padding(screenPadding()).padding(16.dp)) {
                        ProgramOptionRow {
                            ProgramDropdown("기간", week.value, (3..8).toList(), { programWeekLabel(it) }, { week.value = it },
                                Modifier.weight(1f).widthIn(min = 180.dp).testTag("week-field"))
                            ProgramDropdown("일수", 7, (3..7).toList(), { stringResource(R.string.program_days_per_week, it) }, {},
                                Modifier.weight(1f).widthIn(min = 180.dp))
                        }
                    }
                }
            }
        }
        for (locale in locales.keys) for (dp in listOf(320, 360, 411)) for (scale in listOf(1f, 1.3f)) {
            compose.runOnIdle { language.value = locale; width.value = dp; font.value = scale }
            for (value in 3..8) {
                compose.runOnIdle { week.value = value }
                val field = compose.onNodeWithText(locales.getValue(locale).getString(R.string.program_week_number, value))
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onAllNodes(
                    hasText(locales.getValue(locale).getString(R.string.program_week_number, value)) and
                        SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                    useUnmergedTree = true
                ).onFirst().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertEquals(locales.getValue(locale).getString(R.string.program_week_number, value), layout.layoutInput.text.text)
                assertEquals(1, layout.lineCount)
                assertTrue(layout.getLineRight(0) <= layout.size.width + 1)
                field.performClick()
                compose.onAllNodesWithText(locales.getValue(locale).getString(R.string.program_week_number, value)).onLast().assertIsDisplayed().performClick()
            }
        }
    }
}

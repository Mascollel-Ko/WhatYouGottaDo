package com.training.trackplanner

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.localization.LocalizedPresentation
import java.time.DayOfWeek
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
class ProgramPreviewLayoutMatrixTest {
    @get:Rule val compose = createComposeRule()
    @Test fun ko320Normal() = matrix(Locale.KOREAN, 320, 1f)
    @Test fun ko320Large() = matrix(Locale.KOREAN, 320, 1.3f)
    @Test fun ko360Normal() = matrix(Locale.KOREAN, 360, 1f)
    @Test fun ko360Large() = matrix(Locale.KOREAN, 360, 1.3f)
    @Test fun ko411Normal() = matrix(Locale.KOREAN, 411, 1f)
    @Test fun ko411Large() = matrix(Locale.KOREAN, 411, 1.3f)
    @Test fun en320Normal() = matrix(Locale.ENGLISH, 320, 1f)
    @Test fun en320Large() = matrix(Locale.ENGLISH, 320, 1.3f)
    @Test fun en360Normal() = matrix(Locale.ENGLISH, 360, 1f)
    @Test fun en360Large() = matrix(Locale.ENGLISH, 360, 1.3f)
    @Test fun en411Normal() = matrix(Locale.ENGLISH, 411, 1f)
    @Test fun en411Large() = matrix(Locale.ENGLISH, 411, 1.3f)

    private fun matrix(locale: Locale, width: Int, font: Float) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
        val current = mutableStateOf(PreviewLayoutCase(3, 3, 0))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration,
                LocalDensity provides Density(density.density, font)) {
                key(current.value) { FullProgramPreviewUnderTest(current.value, width) }
            }
        }
        var count = 0
        val dayLabels = if (locale.language == "ko") listOf("월", "화", "수", "목", "금", "토", "일")
            else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
        for (weeks in 3..8) for (days in 3..7) for (kind in 0..2) {
            val case = PreviewLayoutCase(weeks, days, kind)
            compose.runOnIdle { current.value = case }
            checkRow("program-week", (1..weeks).toList(), width)
            checkRow("program-day-toggle", (1..7).toList(), width)
            for (week in 1..weeks) {
                checkLabel("program-week-$week", if (locale.language == "ko") "${week}주" else "W$week", width)
                compose.onNodeWithTag("program-week-$week")
                    .assertContentDescriptionEquals(context.getString(R.string.program_week_number, week))
            }
            for (day in 1..7) {
                checkLabel("program-day-toggle-$day", dayLabels[day - 1], width)
                compose.onNodeWithTag("program-day-toggle-$day")
                    .assertContentDescriptionEquals(LocalizedPresentation.weekday(context, DayOfWeek.of(day)))
            }
            val active = if (kind == 0) previewLegacy(case).weekDaySchedule.getValue(1).sorted() else (1..days).toList()
            checkRow("program-day-view", active, width)
            for (day in active) checkLabel("program-day-view-$day", dayLabels[day - 1], width)
            compose.onNodeWithTag("program-week-$weeks").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("program-day-view-${active.last()}").performScrollTo().assertIsDisplayed().performClick()
            if (kind == 2) compose.onAllNodesWithTag("progression-session-control").assertCountEquals(0)
            count++
        }
        assertEquals(90, count)
    }

    private fun checkRow(tag: String, values: List<Int>, width: Int) {
        val row = compose.onNodeWithTag("$tag-row").getUnclippedBoundsInRoot()
        assertTrue("$tag row outside viewport", row.left >= 0.dp && row.right <= width.dp)
        assertEquals("$tag must occupy exactly one control line", 48.dp, row.bottom - row.top)
        var previousRight = row.left
        val cells = values.map { compose.onNodeWithTag("$tag-$it").getUnclippedBoundsInRoot() }
        cells.forEach { cell ->
            assertEquals("$tag cells wrap vertically", row.top, cell.top)
            assertEquals("$tag cell bottom", row.bottom, cell.bottom)
            assertTrue("$tag overlapping/outside row", cell.left >= previousRight && cell.right <= row.right)
            val cellWidth = cell.right - cell.left
            val firstWidth = cells.first().right - cells.first().left
            assertTrue("$tag equal-width cells", kotlin.math.abs(cellWidth.value - firstWidth.value) <= 1f)
            assertTrue("$tag excessive cell width", cellWidth <= 40.dp)
            previousRight = cell.right
        }
    }

    private fun checkLabel(tag: String, text: String, width: Int) {
        val node = compose.onNodeWithTag("$tag-label", useUnmergedTree = true)
        node.assertTextEquals(text)
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue("$tag horizontal bounds $bounds", bounds.left >= 0.dp && bounds.right <= width.dp)
        val control = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue("$tag control outside viewport", control.left >= 0.dp && control.right <= width.dp)
        assertTrue("$tag label outside cell", bounds.left >= control.left && bounds.right <= control.right &&
            bounds.top >= control.top && bounds.bottom <= control.bottom)
        assertTrue("$tag tap height", control.bottom - control.top >= 48.dp)
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals("$tag wrapped", 1, layout.lineCount)
        assertFalse("$tag ellipsized", layout.isLineEllipsized(0))
        assertTrue("$tag clipped horizontally: ${layout.getLineLeft(0)}..${layout.getLineRight(0)} in ${layout.size}",
            layout.getLineLeft(0) >= -1f && layout.getLineRight(0) <= layout.size.width + 1f)
        assertTrue("$tag clipped vertically", layout.getLineTop(0) >= -1f && layout.getLineBottom(0) <= layout.size.height + 1f)
    }
}

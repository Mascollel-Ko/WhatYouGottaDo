package com.training.trackplanner

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.trends.ChartSeries
import com.training.trackplanner.analysis.trends.ChartSpec
import com.training.trackplanner.analysis.trends.ChartTimeGranularity
import com.training.trackplanner.analysis.trends.ChartType
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-w360dp-h800dp")
class AnalysisPlotChartZoomTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fullViewportUsesCompleteXDomainAndAutomaticYRange() {
        assertEquals(
            PlotChartViewport(0.0, 8.0, 88.0, 94.0),
            PlotChartZoomPolicy.auto(domainSize = 9, yMin = 88.0, yMax = 94.0)
        )
        assertEquals(
            PlotChartViewport(-0.5, 0.5, 90.5, 91.5),
            PlotChartZoomPolicy.auto(domainSize = 1, yMin = 91.0, yMax = 91.0)
        )
    }

    @Test
    fun pinchNarrowsBothAxesAroundItsFocalPoint() {
        val full = PlotChartZoomPolicy.auto(domainSize = 9, yMin = 88.0, yMax = 94.0)
        val focalX = 0.8
        val focalY = 0.25
        val xUnderFinger = full.xAtFraction(focalX)
        val yUnderFinger = full.yAtFraction(focalY)

        val zoomed = PlotChartZoomPolicy.update(
            current = full,
            full = full,
            gestureScale = 2f,
            focalXFraction = focalX,
            focalYFraction = focalY
        )

        assertEquals(4.0, zoomed.xSpan, 0.0001)
        assertEquals(3.0, zoomed.ySpan, 0.0001)
        assertEquals(xUnderFinger, zoomed.xAtFraction(focalX), 0.0001)
        assertEquals(yUnderFinger, zoomed.yAtFraction(focalY), 0.0001)
        assertNotEquals((full.yMin + full.yMax) / 2.0, (zoomed.yMin + zoomed.yMax) / 2.0, 0.0001)
    }

    @Test
    fun twoFingerPanMovesViewportWithoutChangingSpan() {
        val full = PlotChartZoomPolicy.auto(domainSize = 9, yMin = 88.0, yMax = 94.0)
        val zoomed = PlotChartZoomPolicy.update(full, full, 2f, 0.5, 0.5)
        val panned = PlotChartZoomPolicy.update(
            current = zoomed,
            full = full,
            gestureScale = 1f,
            focalXFraction = 0.5,
            focalYFraction = 0.5,
            panXFraction = 0.1,
            panYFraction = 0.1
        )

        assertEquals(zoomed.xSpan, panned.xSpan, 0.0001)
        assertEquals(zoomed.ySpan, panned.ySpan, 0.0001)
        assertNotEquals(zoomed.xMin, panned.xMin, 0.0001)
        assertNotEquals(zoomed.yMin, panned.yMin, 0.0001)
    }

    @Test
    fun viewportRejectsInvalidAndExtremeGestureInput() {
        val full = PlotChartZoomPolicy.auto(domainSize = 9, yMin = 88.0, yMax = 94.0)
        val invalid = PlotChartViewport(Double.NaN, 0.0, Double.NEGATIVE_INFINITY, 0.0)
        val recovered = PlotChartZoomPolicy.update(invalid, full, Float.NaN, Double.NaN, Double.NaN)
        val maximumZoom = PlotChartZoomPolicy.update(full, full, Float.MAX_VALUE, 2.0, -1.0)
        val maximumZoomOut = PlotChartZoomPolicy.update(maximumZoom, full, Float.MIN_VALUE, 0.5, 0.5)

        assertEquals(full, recovered)
        assertTrue(maximumZoom.xSpan > 0.0 && maximumZoom.ySpan > 0.0)
        assertTrue(maximumZoom.xMin.isFinite() && maximumZoom.xMax.isFinite())
        assertTrue(maximumZoom.yMin.isFinite() && maximumZoom.yMax.isFinite())
        assertEquals(full, maximumZoomOut)
        assertTrue(PlotChartZoomPolicy.isAuto(full, full))
    }

    @Test
    fun persistentStrengthChartShowsResetOnlyAfterTwoDimensionalPinch() {
        val dates = (0L..2L).map { LocalDate.of(2026, 8, 1).plusDays(it) }
        val spec = ChartSpec(
            type = ChartType.LINE,
            title = "수행능력 레벨 비교",
            lineSeries = listOf(
                ChartSeries(
                    label = "벤치프레스",
                    points = dates.mapIndexed { index, date -> TrendDataPoint(date, 90.0 + index) }
                )
            ),
            yMin = 88.0,
            yMax = 94.0,
            timeGranularity = ChartTimeGranularity.DAILY,
            xDomain = dates,
            valueUnit = "kg",
            enablePlotZoom = true
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                TrainingTrackPlannerTheme {
                    AnalysisTrendChart(spec, Modifier.height(150.dp))
                }
            }
        }

        compose.onNodeWithText("축 맞춤").assertDoesNotExist()
        compose.onNodeWithTag("persistent-strength-plot-zoom-chart", useUnmergedTree = true).performTouchInput {
            pinch(
                start0 = Offset(centerX - 20f, centerY - 20f),
                end0 = Offset(centerX - 70f, centerY - 70f),
                start1 = Offset(centerX + 20f, centerY + 20f),
                end1 = Offset(centerX + 70f, centerY + 70f)
            )
        }
        compose.onNodeWithText("확대됨").assertIsDisplayed()
        compose.onNodeWithText("축 맞춤").assertIsDisplayed().performClick()
        compose.onNodeWithText("축 맞춤").assertDoesNotExist()
    }

    @Test
    fun zoomKeepsSourceSeriesVisualWeightAndOneFingerContractUntouched() {
        val date = LocalDate.of(2026, 8, 1)
        val spec = ChartSpec(
            type = ChartType.LINE,
            title = "chart",
            lineSeries = listOf(ChartSeries("series", listOf(TrendDataPoint(date, 90.0))))
        )
        val snapshot = spec.copy()
        val full = PlotChartZoomPolicy.auto(domainSize = 4, yMin = 80.0, yMax = 100.0)
        PlotChartZoomPolicy.update(full, full, 2f, 0.25, 0.75)
        assertEquals(snapshot, spec)

        val chartSource = source("AnalysisChartUi.kt")
        val strengthSource = source("AnalysisPersistentStrengthPerformanceUi.kt")
        assertTrue(chartSource.contains("count { change -> change.pressed } >= 2"))
        assertTrue(chartSource.contains("clipRect(left = 0f"))
        assertTrue(chartSource.contains("radius = 4f"))
        assertTrue(chartSource.contains("Stroke(width = 4f)"))
        assertFalse(chartSource.contains("graphicsLayer"))
        assertTrue(strengthSource.contains("enablePlotZoom = true"))
        assertFalse(strengthSource.contains("enableVerticalZoom"))
    }

    private fun source(name: String): String =
        sequenceOf(
            File("src/main/java/com/training/trackplanner/$name"),
            File("app/src/main/java/com/training/trackplanner/$name")
        ).first(File::exists).readText(Charsets.UTF_8)
}

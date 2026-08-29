package com.training.trackplanner

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.training.trackplanner.analysis.trends.ChartSeries
import com.training.trackplanner.analysis.trends.ChartSpec
import com.training.trackplanner.analysis.trends.ChartTimeGranularity
import com.training.trackplanner.analysis.trends.ChartType
import com.training.trackplanner.analysis.trends.ScatterPoint
import com.training.trackplanner.analysis.trends.StackedAreaLayer
import com.training.trackplanner.analysis.trends.StackedBarGroup
import com.training.trackplanner.analysis.trends.StackedBarSegment
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
    fun inspectionUsesActualNearestDomainScatterAndStackedValues() {
        val dates = (0L..2L).map { LocalDate.of(2026, 8, 29).plusDays(it) }
        val viewport = PlotChartZoomPolicy.auto(3, 0.0, 250.0)
        val line = ChartSpec(
            ChartType.LINE,
            "line",
            lineSeries = listOf(
                ChartSeries("Squat", dates.mapIndexed { index, date -> TrendDataPoint(date, 170.0 + index) }),
                ChartSeries("Deadlift", listOf(TrendDataPoint(dates[1], 211.7)))
            ),
            timeGranularity = ChartTimeGranularity.DAILY,
            valueUnit = "kg"
        )
        val lineInspection = requireNotNull(inspectLineChart(line, dates, viewport, 0.55))
        assertEquals(listOf(171.0, 211.7), lineInspection.values.map { it.value })

        val stacked = ChartSpec(
            ChartType.STACKED_BAR,
            "stacked",
            stackedBars = listOf(
                StackedBarGroup("day", listOf(StackedBarSegment("Strength", 3.0), StackedBarSegment("Court", 2.0)), dates[1])
            ),
            timeGranularity = ChartTimeGranularity.DAILY
        )
        assertEquals(listOf(3.0, 2.0), inspectStackedBarChart(stacked, stacked.stackedBars, dates, viewport, 0.5)?.values?.map { it.value })

        val scatter = ChartSpec(
            ChartType.SCATTER,
            "scatter",
            scatterPoints = listOf(ScatterPoint(1.0, 10.0, "A"), ScatterPoint(9.0, 90.0, "B"))
        )
        val scatterViewport = PlotChartZoomPolicy.bounds(0.0, 10.0, 0.0, 100.0)
        val scatterInspection = requireNotNull(inspectScatterChart(scatter, scatterViewport, 0.88, 0.12))
        assertEquals("B", scatterInspection.domainLabel)
        assertEquals(9.0, scatterInspection.values.single().xValue!!, 0.0)
        assertEquals(90.0, scatterInspection.values.single().value, 0.0)

        val area = line.copy(
            type = ChartType.STACKED_AREA,
            lineSeries = emptyList(),
            stackedAreaLayers = listOf(StackedAreaLayer("Load", listOf(TrendDataPoint(dates[1], 4.5))))
        )
        assertEquals(4.5, inspectStackedAreaChart(area, dates, viewport, 0.5)?.values?.single()?.value!!, 0.0)
    }

    @Test
    fun collisionPlacementIsBoundedSeparatedStableAndKeepsTrueTargets() {
        val cases = listOf(
            listOf(50f, 50f),
            listOf(48f, 49f, 50f, 51f),
            listOf(0f, 1f, 2f),
            listOf(98f, 99f, 100f),
            List(12) { 50f }
        )
        cases.forEach { desired ->
            val first = placeInspectionLabels(desired, 0f, 110f, 8f)
            val second = placeInspectionLabels(desired, 0f, 110f, 8f)
            assertEquals(first, second)
            assertEquals(desired, first.sortedBy { it.originalIndex }.map { it.desiredY })
            assertTrue(first.all { it.placedY in 0f..110f })
            val sorted = first.sortedBy { it.placedY }
            assertTrue(sorted.zipWithNext().all { (a, b) -> b.placedY - a.placedY >= 7.99f })
        }
    }

    @Test
    fun automaticPointLabelsRequireMeaningfulZoomAndReadableDensity() {
        val full = PlotChartZoomPolicy.auto(21, 0.0, 100.0)
        val insignificant = PlotChartViewport(0.2, 19.8, 1.0, 99.0)
        val zoomed = PlotChartViewport(4.0, 16.0, 20.0, 80.0)

        assertFalse(PointValueLabelPolicy.shouldShow(full, full, 5))
        assertFalse(PointValueLabelPolicy.shouldShow(insignificant, full, 5))
        assertTrue(PointValueLabelPolicy.shouldShow(zoomed, full, 5))
        assertFalse(PointValueLabelPolicy.shouldShow(zoomed, full, 19))
        assertFalse(PointValueLabelPolicy.shouldShow(full, full, 5))
    }

    @Test
    fun visiblePointFilteringExcludesBothOffscreenAxes() {
        val viewport = PlotChartViewport(2.0, 5.0, 20.0, 80.0)
        val visible = ChartPointValue(3.0, 50.0, 50.0)
        assertEquals(
            listOf(visible),
            visibleChartPointValues(
                listOf(
                    ChartPointValue(1.0, 50.0, 10.0),
                    visible,
                    ChartPointValue(3.0, 90.0, 90.0),
                    ChartPointValue(Double.NaN, 50.0, 1.0)
                ),
                viewport
            )
        )
    }

    @Test
    fun chartCandidatesContainOnlyActualLineScatterAndLayerValues() {
        val dates = (0L..2L).map { LocalDate.of(2026, 8, 29).plusDays(it) }
        val line = ChartSpec(
            ChartType.LINE,
            "line",
            lineSeries = listOf(
                ChartSeries("A", listOf(TrendDataPoint(dates[0], 160.2), TrendDataPoint(dates[2], 173.4))),
                ChartSeries("B", listOf(TrendDataPoint(dates[1], null)))
            )
        )
        assertEquals(listOf(160.2, 173.4), linePointValueCandidates(line, dates).map { it.value })

        val scatter = line.copy(
            type = ChartType.SCATTER,
            lineSeries = emptyList(),
            scatterPoints = listOf(ScatterPoint(145.0, 82.4, "actual"))
        )
        assertEquals(ChartPointValue(145.0, 82.4, 82.4), scatterPointValueCandidates(scatter).single())

        val area = line.copy(
            type = ChartType.STACKED_AREA,
            lineSeries = emptyList(),
            stackedAreaLayers = listOf(
                StackedAreaLayer("base", listOf(TrendDataPoint(dates[0], 4.0))),
                StackedAreaLayer("top", listOf(TrendDataPoint(dates[0], 2.0), TrendDataPoint(dates[2], 3.0)))
            )
        )
        val areaPoints = stackedAreaPointValueCandidates(area, dates)
        assertEquals(listOf(4.0, 2.0, 3.0), areaPoints.map { it.value })
        assertEquals(listOf(2.0, 5.0, 1.5), areaPoints.map { it.y })
    }

    @Test
    fun stackedBarCandidatesUseSegmentValuesWithoutChangingGeometry() {
        val date = LocalDate.of(2026, 8, 29)
        val groups = listOf(
            StackedBarGroup(
                "day",
                listOf(StackedBarSegment("A", 3.0), StackedBarSegment("tiny", 0.1), StackedBarSegment("B", 2.0)),
                date
            )
        )
        val candidates = stackedBarPointValueCandidates(groups, listOf(date))
        assertEquals(listOf(3.0, 0.1, 2.0), candidates.map { it.value })
        assertEquals(listOf(1.5, 3.05, 4.1), candidates.map { it.y })
        assertEquals(
            listOf(0, 2),
            readablePointValueIndices(
                candidates.mapIndexed { index, point -> point.copy(availableHeight = listOf(30f, 2f, 24f)[index]) },
                listOf(12f, 12f, 12f)
            )
        )
    }

    @Test
    fun pointLabelBoxesStayInsideAllPlotEdgesAndKeepTrueTargets() {
        val points = listOf(Offset(0f, 1f), Offset(100f, 99f), Offset(50f, 50f))
        val sizes = listOf(Size(30f, 12f), Size(30f, 12f), Size(28f, 12f))
        val first = placePointValueLabels(points, sizes, 100f, 100f, 3f)
        val second = placePointValueLabels(points, sizes, 100f, 100f, 3f)

        assertEquals(first, second)
        assertEquals(points, first.map { Offset(it.pointX, it.pointY) })
        assertTrue(first.all { it.labelX >= 0f && it.labelX + it.width <= 100f })
        assertTrue(first.all { it.labelY >= 0f && it.labelY + it.height <= 100f })
        assertTrue(placePointValueLabels(List(12) { Offset(50f, 50f) }, List(12) { Size(30f, 12f) }, 100f, 100f, 3f).isEmpty())
    }

    @Test
    fun compactPointFormattingUsesExistingAnalysisConventions() {
        assertEquals("91", formatPointValue(91.0))
        assertEquals("173.4", formatPointValue(173.44))
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
        compose.onNodeWithTag("analysis-cartesian-chart", useUnmergedTree = true).performTouchInput {
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
        compose.onNodeWithTag("analysis-cartesian-chart", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("벤치프레스 91 kg").assertIsDisplayed()
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
        assertTrue(chartSource.contains("count { it.pressed } >= 2"))
        assertTrue(chartSource.contains("clipRect(left = 0f"))
        assertTrue(chartSource.contains("radius = 4f"))
        assertTrue(chartSource.contains("Stroke(width = 4f)"))
        assertFalse(chartSource.contains("graphicsLayer"))
        assertFalse(chartSource.contains("persistent-strength-plot-zoom-chart"))
        assertTrue(chartSource.split("cartesianChartGestures(").size - 1 >= 5)
        assertTrue(strengthSource.contains("enablePlotZoom = true"))
        assertFalse(strengthSource.contains("enableVerticalZoom"))
    }

    private fun source(name: String): String =
        sequenceOf(
            File("src/main/java/com/training/trackplanner/$name"),
            File("app/src/main/java/com/training/trackplanner/$name")
        ).first(File::exists).readText(Charsets.UTF_8)
}

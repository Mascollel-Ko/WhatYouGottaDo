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
import com.training.trackplanner.analysis.trends.ChartType
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.ui.theme.TrainingTrackPlannerTheme
import java.time.LocalDate
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-w360dp-h800dp")
class AnalysisVerticalChartZoomTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun viewportNarrowsWidensAndResetsWithoutChangingAutoRange() {
        val auto = VerticalChartZoomPolicy.auto(88.0, 94.0)
        val narrowed = VerticalChartZoomPolicy.zoom(auto, auto, 2f)
        val widened = VerticalChartZoomPolicy.zoom(narrowed, auto, 0.5f)

        assertEquals(3.0, narrowed.span, 0.0001)
        assertEquals(auto, widened)
        assertTrue(VerticalChartZoomPolicy.isAuto(widened, auto))
        assertEquals(0.3, VerticalChartZoomPolicy.zoom(auto, auto, 100f).span, 0.0001)
        assertEquals(24.0, VerticalChartZoomPolicy.zoom(auto, auto, 0.01f).span, 0.0001)
    }

    @Test
    fun persistentStrengthChartShowsResetOnlyAfterPinch() {
        val spec = ChartSpec(
            type = ChartType.LINE,
            title = "수행능력 레벨 비교",
            lineSeries = listOf(
                ChartSeries(
                    label = "벤치프레스",
                    points = listOf(
                        TrendDataPoint(LocalDate.of(2026, 8, 1), 90.0),
                        TrendDataPoint(LocalDate.of(2026, 8, 2), 91.0)
                    )
                )
            ),
            yMin = 88.0,
            yMax = 94.0,
            valueUnit = "kg",
            enableVerticalZoom = true
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
        compose.onNodeWithTag("persistent-strength-y-zoom-chart", useUnmergedTree = true).performTouchInput {
            pinch(
                start0 = Offset(centerX - 20f, centerY),
                end0 = Offset(centerX - 70f, centerY),
                start1 = Offset(centerX + 20f, centerY),
                end1 = Offset(centerX + 70f, centerY)
            )
        }
        compose.onNodeWithText("축 맞춤").assertIsDisplayed().performClick()
        compose.onNodeWithText("축 맞춤").assertDoesNotExist()
    }

    @Test
    fun zoomLeavesSeriesAndVisualWeightConstantsUntouched() {
        val spec = ChartSpec(type = ChartType.LINE, title = "chart")
        VerticalChartZoomPolicy.zoom(
            current = VerticalChartZoomPolicy.auto(80.0, 100.0),
            auto = VerticalChartZoomPolicy.auto(80.0, 100.0),
            gestureScale = 2f
        )
        assertEquals(ChartSpec(type = ChartType.LINE, title = "chart"), spec)

        val source = sequenceOf(
            File("src/main/java/com/training/trackplanner/AnalysisChartUi.kt"),
            File("app/src/main/java/com/training/trackplanner/AnalysisChartUi.kt")
        ).first(File::exists).readText(Charsets.UTF_8)
        assertTrue(source.contains("radius = 4f"))
        assertTrue(source.contains("Stroke(width = 4f)"))
        assertTrue(!source.contains("graphicsLayer"))
    }
}

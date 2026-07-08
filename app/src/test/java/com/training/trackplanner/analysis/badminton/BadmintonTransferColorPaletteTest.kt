package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.trends.BadmintonDailyLoadPoint
import com.training.trackplanner.analysis.trends.BadmintonTrainingMethodSeries
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BadmintonTransferColorPaletteTest {
    @Test
    fun badmintonTransferCategoriesUseDistinctColors() {
        val objectiveColors = BadmintonTrainingMethodSeries.objectiveKeys
            .map(BadmintonTransferColorPalette::colorForKey)
        val axisColors = BadmintonTransferAxis.entries
            .map { axis -> BadmintonTransferColorPalette.colorForKey(axis.name) }
        val typeColors = listOf(
            BadmintonTransferType.DIRECT,
            BadmintonTransferType.SUPPORTIVE,
            BadmintonTransferType.GENERAL_STRENGTH,
            BadmintonTransferType.LOW
        ).map { type -> BadmintonTransferColorPalette.colorForKey(type.name) }

        assertEquals(objectiveColors.size, objectiveColors.toSet().size)
        assertEquals(axisColors.size, axisColors.toSet().size)
        assertEquals(typeColors.size, typeColors.toSet().size)
    }

    @Test
    fun badmintonTransferColorMappingIsStable() {
        val keys = BadmintonTrainingMethodSeries.objectiveKeys
        val forward = keys.associateWith(BadmintonTransferColorPalette::colorForKey)
        val reversed = keys.asReversed().associateWith(BadmintonTransferColorPalette::colorForKey)

        assertEquals(forward, reversed)
        keys.forEach { key ->
            assertEquals(forward.getValue(key), BadmintonTransferColorPalette.colorForKey(key.lowercase()))
        }
    }

    @Test
    fun badmintonTransferLegendUsesSameColorsAsChart() {
        val points = listOf(
            BadmintonDailyLoadPoint(
                date = LocalDate.parse("2026-07-01"),
                courtRaw = 0.0,
                footworkReactiveRaw = 0.0,
                supportRaw = 0.0,
                methodRaw = mapOf("FOOTWORK" to 10.0, "REACTION" to 6.0)
            )
        )
        val recentSegments = BadmintonTrainingMethodSeries.recentComparisonGroups(points)
            .flatMap { group -> group.segments }
        val weeklySegments = BadmintonTrainingMethodSeries.weeklyStackedGroups(points)
            .flatMap { group -> group.segments }

        recentSegments.forEach { segment ->
            val weeklySegment = weeklySegments.single { it.label == segment.label }
            assertEquals(segment.colorKey, weeklySegment.colorKey)
            assertEquals(
                BadmintonTransferColorPalette.colorForKey(segment.colorKey.orEmpty()),
                BadmintonTransferColorPalette.colorForKey(weeklySegment.colorKey.orEmpty())
            )
        }
    }

    @Test
    fun badmintonTransferChartDataCarriesCategoryColorKeys() {
        val metrics = BadmintonTransferMetrics(
            totalTransferStimulus7d = 10.0,
            totalTransferStimulus28d = 20.0,
            transferRatio7dTo28dAverage = null,
            axisShare7d = BadmintonTransferAxis.entries.associateWith { axis ->
                if (axis == BadmintonTransferAxis.LATERAL_MOVEMENT) 1.0 else 0.0
            },
            axisShare28d = BadmintonTransferAxis.entries.associateWith { axis ->
                if (axis == BadmintonTransferAxis.ROTATION_CONTROL) 1.0 else 0.0
            },
            transferTypeShare7d = mapOf(
                BadmintonTransferType.DIRECT to 0.6,
                BadmintonTransferType.SUPPORTIVE to 0.4
            ),
            topTransferExercises7d = emptyList(),
            recommendedAxis = BadmintonTransferAxis.LATERAL_MOVEMENT,
            recommendationSentence = "",
            cautionLevel = BadmintonTransferCautionLevel.NORMAL,
            detailInsightText = "",
            recommendedExerciseCandidates = emptyList()
        )

        val chartData = BadmintonTransferChartDataBuilder().build(metrics)

        assertEquals(
            BadmintonTransferAxis.entries.map { it.name },
            chartData.axisShareBars.map { it.colorKey }
        )
        assertEquals(
            listOf("DIRECT", "SUPPORTIVE", "GENERAL_STRENGTH", "LOW"),
            chartData.transferTypeShareBars.map { it.colorKey }
        )
    }

    @Test
    fun badmintonTransferSummaryColorDataDoesNotChangeAnalysisValues() {
        val metrics = BadmintonTransferMetrics(
            totalTransferStimulus7d = 10.0,
            totalTransferStimulus28d = 20.0,
            transferRatio7dTo28dAverage = null,
            axisShare7d = mapOf(BadmintonTransferAxis.LATERAL_MOVEMENT to 0.7),
            axisShare28d = mapOf(BadmintonTransferAxis.LATERAL_MOVEMENT to 0.5),
            transferTypeShare7d = mapOf(BadmintonTransferType.DIRECT to 0.7),
            topTransferExercises7d = emptyList(),
            recommendedAxis = BadmintonTransferAxis.LATERAL_MOVEMENT,
            recommendationSentence = "",
            cautionLevel = BadmintonTransferCautionLevel.NORMAL,
            detailInsightText = "",
            recommendedExerciseCandidates = emptyList()
        )
        val summary = BadmintonTransferSummary(
            metrics = metrics,
            chartData = BadmintonTransferChartDataBuilder().build(metrics),
            confidence = AnalysisConfidence.MEDIUM
        )

        assertEquals(0.7, summary.metrics.axisShare7d.getValue(BadmintonTransferAxis.LATERAL_MOVEMENT), 0.001)
        assertEquals(0.7, summary.chartData.axisShareBars.first { it.colorKey == "LATERAL_MOVEMENT" }.value, 0.001)
    }
}

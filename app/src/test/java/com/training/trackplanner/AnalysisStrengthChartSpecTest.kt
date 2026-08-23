package com.training.trackplanner

import androidx.compose.ui.graphics.Color
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthHistoryPoint
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthPerformanceSummary
import com.training.trackplanner.analysis.strengthperformance.PersistentStrengthTargetSummary
import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.trends.TrendMetricId
import com.training.trackplanner.data.persistentStrengthPosteriorMetricSeries
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisStrengthChartSpecTest {
    @Test
    fun growthRateUsesAdjacentPosteriorPointsWithinTheSameTarget() {
        val squat = target(
            key = StrengthPerformanceRegistry.BACK_SQUAT.value,
            name = "스쿼트",
            medians = listOf(100.0, 105.0, 102.9)
        )
        val bench = target(
            key = StrengthPerformanceRegistry.BENCH_PRESS.value,
            name = "벤치프레스",
            medians = listOf(200.0, 220.0)
        )

        val squatGrowth = persistentStrengthGrowthHistory(squat)
        val benchGrowth = persistentStrengthGrowthHistory(bench)

        assertNull(squatGrowth.first().medianGrowthPercent)
        assertEquals(5.0, squatGrowth[1].medianGrowthPercent!!, 0.0001)
        assertEquals(-2.0, squatGrowth[2].medianGrowthPercent!!, 0.0001)
        assertEquals(10.0, benchGrowth[1].medianGrowthPercent!!, 0.0001)
        assertEquals(squat.history.map { it.sessionDate }, squatGrowth.map { it.sessionDate })
    }

    @Test
    fun multiTargetChartUsesFixedColorsUnionDomainAndDirectObservationsOnly() {
        val first = LocalDate.of(2026, 7, 1)
        val targets = listOf(
            target(StrengthPerformanceRegistry.BACK_SQUAT.value, "스쿼트", listOf(100.0, 105.0), first),
            target(StrengthPerformanceRegistry.BENCH_PRESS.value, "벤치프레스", listOf(80.0), first.plusDays(1)),
            target(StrengthPerformanceRegistry.CONVENTIONAL_DEADLIFT.value, "데드리프트", listOf(140.0), first.plusDays(2)),
            target(
                StrengthPerformanceRegistry.WEIGHTED_PULL_UP.value,
                "중량 풀업",
                listOf(95.0),
                first.plusDays(3),
                StrengthLoadSemantics.BODYWEIGHT_PLUS_ADDED_LOAD
            )
        )
        val proxyOnlyPoint = targets.first().history.first().copy(
            sessionObservationMedianKg = 999.0,
            directObservationType = "NONE"
        )
        val withProxyOnlyObservation = targets.toMutableList().also { items ->
            items[0] = items[0].copy(history = listOf(proxyOnlyPoint) + items[0].history.drop(1))
        }

        val spec = persistentStrengthHistoryChartSpec(
            targets = withProxyOnlyObservation,
            displayMode = StrengthPerformanceDisplayMode.LEVEL,
            focusedTargetKey = StrengthPerformanceRegistry.BACK_SQUAT.value
        )
        val posteriorSeries = spec.lineSeries.filterNot { series -> series.seriesKey?.endsWith(".observation") == true }

        assertEquals(4, posteriorSeries.size)
        assertEquals(4, spec.intervalBands.size)
        assertEquals(
            withProxyOnlyObservation.flatMap { target -> target.history.map { it.sessionDate } }.distinct().sorted(),
            spec.xDomain
        )
        assertFalse(spec.lineSeries.flatMap { it.points }.any { point -> point.value == 999.0 })
        assertTrue(spec.lineSeries.filter { it.seriesKey?.endsWith(".observation") == true }.all { !it.connectPoints })
        assertEquals(Color(0xFF1565C0.toInt()), strengthPerformanceTargetColor(StrengthPerformanceRegistry.BACK_SQUAT.value))
        assertEquals(Color(0xFFD32F2F.toInt()), strengthPerformanceTargetColor(StrengthPerformanceRegistry.BENCH_PRESS.value))
        assertEquals(Color(0xFF2E7D32.toInt()), strengthPerformanceTargetColor(StrengthPerformanceRegistry.CONVENTIONAL_DEADLIFT.value))
        assertEquals(Color(0xFF6D4C41.toInt()), strengthPerformanceTargetColor(StrengthPerformanceRegistry.WEIGHTED_PULL_UP.value))
        assertEquals(withProxyOnlyObservation.map { it.targetKey }, spec.intervalBands.map { it.seriesKey })
        assertTrue(spec.enablePlotZoom)
    }

    @Test
    fun labPerformanceMetricUsesTheLastPersistedPosteriorMedianInEachWeek() {
        val first = LocalDate.of(2026, 7, 6)
        val bench = target(
            key = StrengthPerformanceRegistry.BENCH_PRESS.value,
            name = "벤치프레스",
            medians = listOf(100.0, 105.0, 103.0),
            firstDate = first
        ).let { target ->
            target.copy(
                history = target.history.mapIndexed { index, point ->
                    point.copy(sessionDate = if (index < 2) first.plusDays(index.toLong()) else first.plusWeeks(1))
                }
            )
        }

        val series = persistentStrengthPosteriorMetricSeries(summary(listOf(bench)))
            .getValue(TrendMetricId.BENCH_PRESS_E1RM)

        assertEquals(listOf(first, first.plusWeeks(1)), series.map { it.weekStart })
        assertEquals(listOf(105.0, 103.0), series.map { it.value })
    }

    private fun summary(targets: List<PersistentStrengthTargetSummary>) =
        PersistentStrengthPerformanceSummary(
            targets = targets,
            eventCount = 0,
            pendingEventCount = 0,
            failedEventCount = 0,
            latestEventFingerprint = null,
            modelStateFingerprint = null,
            modelVersionBoundaries = emptyList(),
            curveVersionBoundaries = emptyList(),
            factorSchemaVersion = null,
            bootstrapProvenance = null,
            backupRestorationProvenance = null,
            numericalDiagnostics = emptyList()
        )

    private fun target(
        key: String,
        name: String,
        medians: List<Double>,
        firstDate: LocalDate = LocalDate.of(2026, 7, 1),
        semantics: StrengthLoadSemantics = StrengthLoadSemantics.EXTERNAL_LOAD
    ) = PersistentStrengthTargetSummary(
        targetKey = key,
        displayNameKo = name,
        loadSemantics = semantics,
        currentMedianKg = medians.last(),
        currentLow80Kg = medians.last() - 5.0,
        currentHigh80Kg = medians.last() + 5.0,
        currentBodyWeightKg = null,
        currentAddedWeightKg = null,
        latestDirectObservationKg = null,
        latestDirectObservationDate = null,
        relevantSessionCount = medians.size,
        directObservationCount = medians.size,
        strongNrmObservationCount = 0,
        proxyObservationCount = 0,
        failureObservationCount = 0,
        curveProfileId = null,
        curveMatchLevel = null,
        curveVarianceMultiplier = null,
        curveCalibrationStatus = null,
        lastProcessedSessionDate = firstDate.plusDays(medians.lastIndex.toLong()),
        modelVersion = "model",
        curveVersion = "curve",
        history = medians.mapIndexed { index, median ->
            historyPoint(
                id = "$key-$index",
                date = firstDate.plusDays(index.toLong()),
                median = median
            )
        }
    )

    private fun historyPoint(
        id: String,
        date: LocalDate,
        median: Double
    ) = PersistentStrengthHistoryPoint(
        eventUuid = id,
        sessionDate = date,
        priorMedianKg = null,
        priorLow80Kg = null,
        priorHigh80Kg = null,
        posteriorMedianKg = median,
        posteriorLow80Kg = median - 5.0,
        posteriorHigh80Kg = median + 5.0,
        directObservedLoadKg = median,
        directObservationType = "RPE_MIXTURE_OBSERVATION",
        sessionObservationMedianKg = median + 1.0,
        sessionObservationLow80Kg = median - 4.0,
        sessionObservationHigh80Kg = median + 6.0,
        posteriorMeanChangeKg = null,
        intervalWidthChange80Kg = null,
        predictivePercentile = null,
        strongObservationType = "RPE_MIXTURE_OBSERVATION",
        curveProfileId = null,
        curveMatchLevel = null,
        curveCalibrationStatus = null,
        bodyWeightKgAtProcessing = null,
        rawAddedWeightKgAtProcessing = null,
        totalLoadKgAtProcessing = null,
        bodyWeightSource = null,
        sourceEvidenceStatus = "AVAILABLE",
        sourceSetCountAtProcessing = 1,
        evidenceFingerprint = id,
        modelVersion = "model",
        curveVersion = "curve",
        factorSchemaVersion = "schema"
    )
}

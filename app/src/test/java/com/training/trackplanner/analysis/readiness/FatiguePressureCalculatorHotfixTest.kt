package com.training.trackplanner.analysis.readiness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FatiguePressureCalculatorHotfixTest {
    @Test
    fun zeroResidualWithHighStatsDoesNotBecomeHighFatigue() {
        val result = FatiguePressureCalculator().calculate(
            residual = residual(0.0),
            stats = stats(zScore = 3.0, percentile = 99.0),
            adaptiveBaseline = adaptive(tolerance = 100.0)
        ).categoryPressures.getValue(FatigueCategoryKey.SYSTEMIC)

        assertEquals(FatigueLevel.LOW, result.level)
        assertEquals(0.0, result.currentResidualLoad, 0.0001)
    }

    @Test
    fun lowRatioDoesNotBecomeHighFatigue() {
        val result = FatiguePressureCalculator().calculate(
            residual = residual(10.0),
            stats = stats(zScore = 3.0, percentile = 99.0),
            adaptiveBaseline = adaptive(tolerance = 100.0)
        ).categoryPressures.getValue(FatigueCategoryKey.SYSTEMIC)

        assertEquals(FatigueLevel.LOW, result.level)
        assertTrue((result.pressure ?: 1.0) <= 0.2)
    }

    @Test
    fun missingBaselineWithZeroResidualDoesNotRenderAsHighRatio() {
        val result = FatiguePressureCalculator().calculate(
            residual = residual(0.0),
            stats = stats(zScore = null, percentile = null),
            adaptiveBaseline = adaptive(tolerance = null)
        ).categoryPressures.getValue(FatigueCategoryKey.SYSTEMIC)

        assertEquals(FatigueLevel.LOW, result.level)
        assertNull(result.pressure)
    }

    @Test
    fun actualHighLoadCanStillBecomeHighFatigue() {
        val result = FatiguePressureCalculator().calculate(
            residual = residual(162.0),
            stats = stats(zScore = 1.7, percentile = 90.0),
            adaptiveBaseline = adaptive(tolerance = 100.0)
        ).categoryPressures.getValue(FatigueCategoryKey.SYSTEMIC)

        assertTrue(result.level.ordinal >= FatigueLevel.HIGH.ordinal)
    }

    @Test
    fun relativePressureThresholdsUseReadinessHotfixCutoffs() {
        val belowRatio = FatiguePressureCalculator().calculate(
            residual = residual(137.0),
            stats = stats(zScore = null, percentile = null),
            adaptiveBaseline = adaptive(tolerance = 100.0)
        ).categoryPressures.getValue(FatigueCategoryKey.SYSTEMIC)
        val atRatio = FatiguePressureCalculator().calculate(
            residual = residual(138.0),
            stats = stats(zScore = null, percentile = null),
            adaptiveBaseline = adaptive(tolerance = 100.0)
        ).categoryPressures.getValue(FatigueCategoryKey.SYSTEMIC)

        assertEquals(FatigueLevel.NORMAL, belowRatio.level)
        assertEquals(FatigueLevel.ELEVATED, atRatio.level)
    }

    @Test
    fun zScoreThresholdsUseReadinessHotfixCutoffsAndPercentilesStayStable() {
        val belowZ = FatiguePressureCalculator().calculate(
            residual = residual(100.0),
            stats = stats(zScore = 1.195, percentile = null),
            adaptiveBaseline = adaptive(tolerance = null)
        ).categoryPressures.getValue(FatigueCategoryKey.SYSTEMIC)
        val atZ = FatiguePressureCalculator().calculate(
            residual = residual(100.0),
            stats = stats(zScore = 1.196, percentile = null),
            adaptiveBaseline = adaptive(tolerance = null)
        ).categoryPressures.getValue(FatigueCategoryKey.SYSTEMIC)
        val belowPercentile = FatiguePressureCalculator().calculate(
            residual = residual(100.0),
            stats = stats(zScore = null, percentile = 86.0),
            adaptiveBaseline = adaptive(tolerance = null)
        ).categoryPressures.getValue(FatigueCategoryKey.SYSTEMIC)
        val atPercentile = FatiguePressureCalculator().calculate(
            residual = residual(100.0),
            stats = stats(zScore = null, percentile = 87.0),
            adaptiveBaseline = adaptive(tolerance = null)
        ).categoryPressures.getValue(FatigueCategoryKey.SYSTEMIC)

        assertEquals(FatigueLevel.NORMAL, belowZ.level)
        assertEquals(FatigueLevel.ELEVATED, atZ.level)
        assertEquals(FatigueLevel.NORMAL, belowPercentile.level)
        assertEquals(FatigueLevel.ELEVATED, atPercentile.level)
    }

    private fun residual(load: Double): ResidualFatigueSnapshot =
        ResidualFatigueSnapshot(
            residualByCategory = mapOf(FatigueCategoryKey.SYSTEMIC to load),
            residualByBodyPart = emptyMap(),
            residualByAdaptiveBaselineGroup = emptyMap(),
            highestResidualCategories = listOf(FatigueCategoryKey.SYSTEMIC),
            highestResidualBodyParts = emptyList()
        )

    private fun stats(zScore: Double?, percentile: Double?): StatisticalBaselineSnapshot =
        StatisticalBaselineSnapshot(
            categoryStats = mapOf(
                FatigueCategoryKey.SYSTEMIC to BaselineStat(
                    rollingMean = 50.0,
                    rollingStd = 10.0,
                    zScore = zScore,
                    percentile = percentile,
                    ewmaBaseline = 50.0,
                    pressure = null,
                    trend = BaselineTrend.STABLE,
                    confidence = AnalysisConfidence.MEDIUM,
                    sampleDays = 42
                )
            ),
            baselineGroupStats = emptyMap(),
            bodyPartStats = emptyMap(),
            overallConfidence = AnalysisConfidence.MEDIUM
        )

    private fun adaptive(tolerance: Double?): AdaptiveBaselineSnapshot =
        AdaptiveBaselineSnapshot(
            toleranceByCategory = mapOf(FatigueCategoryKey.SYSTEMIC to tolerance).filterValues { it != null }.mapValues { it.value!! },
            toleranceByBaselineGroup = emptyMap(),
            toleranceByBodyPart = emptyMap(),
            confidenceByCategory = mapOf(FatigueCategoryKey.SYSTEMIC to AnalysisConfidence.MEDIUM),
            trendByCategory = mapOf(FatigueCategoryKey.SYSTEMIC to BaselineTrend.STABLE),
            successfulExposureCountByCategory = emptyMap(),
            failedExposureCountByCategory = emptyMap(),
            dataSufficiency = AnalysisConfidence.MEDIUM,
            baselineAdjustmentNotes = emptyList()
        )
}

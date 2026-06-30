package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder.MuscleBucket
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MuscleBucketSelectionTest {
    @Test
    fun defaultMetricsUseTopThreeRecentLoads() {
        val today = LocalDate.parse("2026-06-30")
        val series = mapOf(
            MuscleBucket.CHEST.dailyMetric to listOf(TrendDataPoint(today, 10.0)),
            MuscleBucket.BACK_LATS.dailyMetric to listOf(TrendDataPoint(today, 30.0)),
            MuscleBucket.SHOULDERS.dailyMetric to listOf(TrendDataPoint(today, 20.0)),
            MuscleBucket.QUADS.dailyMetric to listOf(TrendDataPoint(today, 40.0))
        )

        val result = MuscleBucketSelection.defaultMetrics(
            available = listOf(MuscleBucket.CHEST, MuscleBucket.BACK_LATS, MuscleBucket.SHOULDERS, MuscleBucket.QUADS),
            series = series
        )

        assertEquals(setOf(MuscleBucket.QUADS.dailyMetric, MuscleBucket.BACK_LATS.dailyMetric, MuscleBucket.SHOULDERS.dailyMetric), result)
    }

    @Test
    fun filterMatchesKoreanLabelAndInternalKey() {
        val buckets = listOf(MuscleBucket.CHEST, MuscleBucket.BACK_LATS, MuscleBucket.ROTATION_CORE)

        assertEquals(listOf(MuscleBucket.CHEST), MuscleBucketSelection.filter(buckets, "가슴"))
        assertEquals(listOf(MuscleBucket.BACK_LATS), MuscleBucketSelection.filter(buckets, "BACK"))
        assertEquals(listOf(MuscleBucket.ROTATION_CORE), MuscleBucketSelection.filter(buckets, "rotation"))
    }

    @Test
    fun summaryHandlesMultipleSelection() {
        val metrics = listOf(
            TrendMetricId.MUSCLE_QUADS_LOAD_DAILY,
            TrendMetricId.MUSCLE_BACK_LATS_LOAD_DAILY,
            TrendMetricId.MUSCLE_SHOULDERS_LOAD_DAILY,
            TrendMetricId.MUSCLE_CHEST_LOAD_DAILY
        )

        val summary = MuscleBucketSelection.summary(
            metrics,
            listOf(MuscleBucket.QUADS, MuscleBucket.BACK_LATS, MuscleBucket.SHOULDERS, MuscleBucket.CHEST)
        )

        assertTrue(summary.contains("외 1개 선택됨"))
    }
}

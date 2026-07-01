package com.training.trackplanner.analysis.trends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BadmintonTrainingMethodSeriesTest {
    @Test
    fun weeklyStackedGroupsAggregateByWeekNotMonth() {
        val monday = LocalDate.parse("2026-06-01")
        val nextMonday = monday.plusWeeks(1)
        val groups = BadmintonTrainingMethodSeries.weeklyStackedGroups(
            listOf(
                BadmintonDailyLoadPoint(monday, 0.0, 10.0, 0.0, mapOf("FOOTWORK" to 10.0)),
                BadmintonDailyLoadPoint(monday.plusDays(2), 0.0, 5.0, 0.0, mapOf("FOOTWORK" to 5.0)),
                BadmintonDailyLoadPoint(nextMonday, 0.0, 7.0, 0.0, mapOf("REACTION" to 7.0))
            )
        )

        assertEquals(listOf("2026-06-01", "2026-06-08"), groups.map { it.label })
        assertEquals(15.0, groups.first().segments.single().value, 0.001)
        assertEquals("리액션", groups.last().segments.single().label)
    }

    @Test
    fun totalsPreserveDuplicatedMultiLabelStimulus() {
        val totals = BadmintonTrainingMethodSeries.totals(
            listOf(
                BadmintonDailyLoadPoint(LocalDate.parse("2026-06-01"), 0.0, 10.0, 0.0, mapOf("FOOTWORK" to 10.0, "REACTION" to 10.0))
            )
        )

        assertEquals(10.0, totals.getValue("FOOTWORK"), 0.001)
        assertEquals(10.0, totals.getValue("REACTION"), 0.001)
    }

    @Test
    fun totalsDuplicateReactionAccelerationFootworkStimulus() {
        val totals = BadmintonTrainingMethodSeries.totals(
            listOf(
                BadmintonDailyLoadPoint(
                    LocalDate.parse("2026-06-01"),
                    0.0,
                    10.0,
                    0.0,
                    mapOf("FOOTWORK" to 10.0, "REACTION" to 10.0, "ACCELERATION" to 10.0)
                )
            )
        )

        assertEquals(10.0, totals.getValue("FOOTWORK"), 0.001)
        assertEquals(10.0, totals.getValue("REACTION"), 0.001)
        assertEquals(10.0, totals.getValue("ACCELERATION"), 0.001)
    }

    @Test
    fun summaryUsesTransferObjectiveLabelsNotLegacyAxisLabels() {
        val summary = BadmintonTrainingMethodSeries.summary(
            listOf(
                BadmintonDailyLoadPoint(
                    LocalDate.parse("2026-06-10"),
                    0.0,
                    0.0,
                    0.0,
                    mapOf(
                        "RACKET_SUPPORT" to 100.0,
                        "UNILATERAL_STABILITY" to 100.0,
                        "LOW_FATIGUE_CONTROL" to 100.0,
                        "FOOTWORK" to 40.0,
                        "ACCELERATION" to 30.0,
                        "DECELERATION" to 5.0
                    )
                )
            )
        )

        assertTrue(summary.topKeys.contains("FOOTWORK"))
        assertTrue(summary.sentence.contains("풋워크"))
        listOf("라켓 보조", "전이축 비중", "편측 안정성", "저피로 제어").forEach { legacyLabel ->
            assertFalse(summary.sentence.contains(legacyLabel))
        }
    }

    @Test
    fun totalsFilterLegacyAxisKeysOutOfObjectiveChartData() {
        val totals = BadmintonTrainingMethodSeries.totals(
            listOf(
                BadmintonDailyLoadPoint(
                    LocalDate.parse("2026-06-10"),
                    0.0,
                    0.0,
                    0.0,
                    mapOf(
                        "RACKET_SUPPORT" to 100.0,
                        "UNILATERAL_STABILITY" to 100.0,
                        "LOW_FATIGUE_CONTROL" to 100.0,
                        "REACTION" to 12.0
                    )
                )
            )
        )

        assertEquals(setOf("REACTION"), totals.keys)
    }

    @Test
    fun recentComparisonGroupsUseTransferObjectiveLabels() {
        val groups = BadmintonTrainingMethodSeries.recentComparisonGroups(
            listOf(
                BadmintonDailyLoadPoint(LocalDate.parse("2026-06-01"), 0.0, 0.0, 0.0, mapOf("DECELERATION" to 28.0)),
                BadmintonDailyLoadPoint(LocalDate.parse("2026-06-10"), 0.0, 0.0, 0.0, mapOf("FOOTWORK" to 14.0, "RACKET_SUPPORT" to 99.0))
            )
        )

        assertEquals(listOf("최근 7일", "최근 28일 평균(7일 환산)"), groups.map { it.label })
        val labels = groups.flatMap { group -> group.segments.map { it.label } }
        assertTrue("풋워크" in labels)
        assertTrue("감속" in labels)
        assertFalse("라켓 보조" in labels)
    }
}

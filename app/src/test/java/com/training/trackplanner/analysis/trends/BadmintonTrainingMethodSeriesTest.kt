package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveDailyPoint
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
                BadmintonObjectiveDailyPoint(monday, mapOf("FOOTWORK" to 10.0)),
                BadmintonObjectiveDailyPoint(monday.plusDays(2), mapOf("FOOTWORK" to 5.0)),
                BadmintonObjectiveDailyPoint(nextMonday, mapOf("REACTION" to 7.0))
            )
        )

        assertEquals(listOf(monday, nextMonday), groups.map { it.weekStart })
        assertEquals(
            listOf(
                AnalysisChartTemporalPolicy.weekLabel(monday).compactLabel,
                AnalysisChartTemporalPolicy.weekLabel(nextMonday).compactLabel
            ),
            groups.map { it.label }
        )
        assertEquals(9, groups.first().segments.size)
        assertEquals(15.0, groups.first().segments.single { it.colorKey == "FOOTWORK" }.value, 0.001)
        assertEquals(7.0, groups.last().segments.single { it.colorKey == "REACTION" }.value, 0.001)
    }

    @Test
    fun weeklyVolumeAndTransferUseTheSameLabelForTheSameWeek() {
        val weekStart = LocalDate.parse("2026-06-29")
        val group = BadmintonTrainingMethodSeries.weeklyStackedGroups(
            listOf(
                BadmintonObjectiveDailyPoint(
                    weekStart.plusDays(2),
                    mapOf("FOOTWORK" to 10.0)
                )
            )
        ).single()
        val volumeLabel = AnalysisChartTemporalPolicy.weekLabel(weekStart)

        assertEquals(weekStart, group.weekStart)
        assertEquals(volumeLabel.compactLabel, group.label)
        assertEquals("7월 1주 · 6월 29일~7월 5일", volumeLabel.detailedLabel)
    }

    @Test
    fun totalsPreserveDuplicatedMultiLabelStimulus() {
        val totals = BadmintonTrainingMethodSeries.totals(
            listOf(
                BadmintonObjectiveDailyPoint(LocalDate.parse("2026-06-01"), mapOf("FOOTWORK" to 10.0, "REACTION" to 10.0))
            )
        )

        assertEquals(10.0, totals.getValue("FOOTWORK"), 0.001)
        assertEquals(10.0, totals.getValue("REACTION"), 0.001)
    }

    @Test
    fun totalsDuplicateReactionAccelerationFootworkStimulus() {
        val totals = BadmintonTrainingMethodSeries.totals(
            listOf(
                BadmintonObjectiveDailyPoint(
                    LocalDate.parse("2026-06-01"),
                    mapOf("FOOTWORK" to 10.0, "REACTION" to 10.0, "ACCELERATION" to 10.0)
                )
            )
        )

        assertEquals(10.0, totals.getValue("FOOTWORK"), 0.001)
        assertEquals(10.0, totals.getValue("REACTION"), 0.001)
        assertEquals(10.0, totals.getValue("ACCELERATION"), 0.001)
    }

    @Test
    fun totalsCanFilterSelectedObjectivesOnly() {
        val totals = BadmintonTrainingMethodSeries.totals(
            points = listOf(
                BadmintonObjectiveDailyPoint(
                    LocalDate.parse("2026-06-01"),
                    mapOf("FOOTWORK" to 10.0, "REACTION" to 12.0, "ACCELERATION" to 8.0)
                )
            ),
            selectedKeys = setOf("FOOTWORK", "REACTION")
        )

        assertEquals(setOf("REACTION", "FOOTWORK"), totals.keys)
        assertEquals(12.0, totals.getValue("REACTION"), 0.001)
        assertEquals(10.0, totals.getValue("FOOTWORK"), 0.001)
    }

    @Test
    fun recentComparisonGroupsRespectSelectedObjectivesAndStableColors() {
        val selected = setOf("FOOTWORK", "REACTION")
        val groups = BadmintonTrainingMethodSeries.recentComparisonGroups(
            points = listOf(
                BadmintonObjectiveDailyPoint(LocalDate.parse("2026-06-01"), mapOf("DECELERATION" to 28.0)),
                BadmintonObjectiveDailyPoint(LocalDate.parse("2026-06-10"), mapOf("FOOTWORK" to 14.0, "REACTION" to 7.0))
            ),
            selectedKeys = selected
        )

        val labels = selected.map(BadmintonTrainingMethodLabels::label).toSet()
        val segments = groups.flatMap { it.segments }
        assertEquals(listOf(2, 2), groups.map { it.segments.size })
        assertTrue(segments.all { it.label in labels })
        assertTrue(segments.any { it.colorIndex == BadmintonTrainingMethodSeries.colorIndex("FOOTWORK") })
        assertTrue(segments.any { it.colorIndex == BadmintonTrainingMethodSeries.colorIndex("REACTION") })
    }

    @Test
    fun recentComparisonKeepsAllNineIncludingZerosWithoutChangingWindowArithmetic() {
        val today = LocalDate.parse("2026-06-28")
        val groups = BadmintonTrainingMethodSeries.recentComparisonGroups(
            points = listOf(
                BadmintonObjectiveDailyPoint(today.minusDays(20), mapOf("FOOTWORK" to 28.0)),
                BadmintonObjectiveDailyPoint(today, mapOf("REACTION" to 14.0))
            )
        )

        assertEquals(listOf(9, 9), groups.map { it.segments.size })
        assertEquals(
            BadmintonTrainingMethodSeries.objectiveKeys,
            groups.first().segments.mapNotNull { it.colorKey }
        )
        assertEquals(14.0, groups[0].segments.single { it.colorKey == "REACTION" }.value, 0.001)
        assertEquals(0.0, groups[0].segments.single { it.colorKey == "FOOTWORK" }.value, 0.001)
        assertEquals(3.5, groups[1].segments.single { it.colorKey == "REACTION" }.value, 0.001)
        assertEquals(7.0, groups[1].segments.single { it.colorKey == "FOOTWORK" }.value, 0.001)
        assertEquals(0.0, groups[1].segments.single { it.colorKey == "ANTI_ROTATION" }.value, 0.001)
    }

    @Test
    fun weeklyStackedGroupsKeepAllNineIncludingZeroObjectives() {
        val groups = BadmintonTrainingMethodSeries.weeklyStackedGroups(
            listOf(
                BadmintonObjectiveDailyPoint(
                    LocalDate.parse("2026-06-01"),
                    mapOf("REACTION" to 12.0)
                )
            )
        )

        assertEquals(9, groups.single().segments.size)
        assertEquals(12.0, groups.single().segments.single { it.colorKey == "REACTION" }.value, 0.001)
        assertEquals(0.0, groups.single().segments.single { it.colorKey == "ANTI_ROTATION" }.value, 0.001)
    }

    @Test
    fun weeklyStackedGroupsRespectSelectedObjectivesAndStableColors() {
        val groups = BadmintonTrainingMethodSeries.weeklyStackedGroups(
            points = listOf(
                BadmintonObjectiveDailyPoint(
                    LocalDate.parse("2026-06-01"),
                    mapOf("FOOTWORK" to 10.0, "REACTION" to 6.0, "ACCELERATION" to 3.0)
                )
            ),
            selectedKeys = setOf("REACTION")
        )

        val segment = groups.single().segments.single()
        assertEquals(BadmintonTrainingMethodLabels.label("REACTION"), segment.label)
        assertEquals(BadmintonTrainingMethodSeries.colorIndex("REACTION"), segment.colorIndex)
    }

    @Test
    fun summaryUsesTransferObjectiveLabelsNotLegacyAxisLabels() {
        val summary = BadmintonTrainingMethodSeries.summary(
            listOf(
                BadmintonObjectiveDailyPoint(
                    LocalDate.parse("2026-06-10"),
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
        assertTrue(summary.sentence.contains(BadmintonTrainingMethodLabels.label("FOOTWORK")))
        listOf("RACKET_SUPPORT", "UNILATERAL_STABILITY", "LOW_FATIGUE_CONTROL").forEach { legacyKey ->
            assertFalse(summary.sentence.contains(legacyKey))
        }
    }

    @Test
    fun totalsFilterLegacyAxisKeysOutOfObjectiveChartData() {
        val totals = BadmintonTrainingMethodSeries.totals(
            listOf(
                BadmintonObjectiveDailyPoint(
                    LocalDate.parse("2026-06-10"),
                    mapOf(
                        "RACKET_SUPPORT" to 100.0,
                        "UNILATERAL_STABILITY" to 100.0,
                        "LOW_FATIGUE_CONTROL" to 100.0,
                        "REACTION" to 12.0
                    )
                )
            )
        )

        assertEquals(BadmintonTrainingMethodSeries.objectiveKeys, totals.keys.toList())
        assertEquals(12.0, totals.getValue("REACTION"), 0.001)
        assertFalse("RACKET_SUPPORT" in totals)
    }

    @Test
    fun objectiveBarsAlwaysShowAllNineIncludingZero() {
        val bars = BadmintonTrainingMethodSeries.objectiveBars(
            listOf(
                BadmintonObjectiveDailyPoint(
                    LocalDate.parse("2026-06-10"),
                    mapOf("REACTION" to 12.0)
                )
            )
        )

        assertEquals(BadmintonTrainingMethodSeries.objectiveKeys.map(BadmintonTrainingMethodLabels::label), bars.map { it.label })
        assertEquals(9, bars.size)
        assertEquals(0.0, bars.first { it.colorKey == "ANTI_ROTATION" }.value, 0.0)
        assertEquals(12.0, bars.first { it.colorKey == "REACTION" }.value, 0.0)
    }

    @Test
    fun recentComparisonGroupsUseTransferObjectiveLabels() {
        val groups = BadmintonTrainingMethodSeries.recentComparisonGroups(
            listOf(
                BadmintonObjectiveDailyPoint(LocalDate.parse("2026-06-01"), mapOf("DECELERATION" to 28.0)),
                BadmintonObjectiveDailyPoint(LocalDate.parse("2026-06-10"), mapOf("FOOTWORK" to 14.0, "RACKET_SUPPORT" to 99.0))
            )
        )

        assertEquals(listOf("최근 7일", "최근 28일 평균(7일 환산)"), groups.map { it.label })
        val labels = groups.flatMap { group -> group.segments.map { it.label } }
        assertTrue(BadmintonTrainingMethodLabels.label("FOOTWORK") in labels)
        assertTrue(BadmintonTrainingMethodLabels.label("DECELERATION") in labels)
        assertFalse(BadmintonTrainingMethodLabels.label("RACKET_SUPPORT") in labels)
    }

    @Test
    fun summaryDoesNotEmitLowerBodyDeficitLanguage() {
        val summary = BadmintonTrainingMethodSeries.summary(
            listOf(
                BadmintonObjectiveDailyPoint(
                    LocalDate.parse("2026-06-10"),
                    mapOf(
                        "FOOTWORK" to 15.0,
                        "ACCELERATION" to 10.0,
                        "REACTION" to 5.0
                    )
                )
            )
        )

        listOf("하체 부족", "하체 결핍", "lower body deficit").forEach { forbidden ->
            assertFalse(summary.sentence.contains(forbidden, ignoreCase = true))
        }
    }
}

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
        assertEquals(BadmintonTrainingMethodLabels.label("REACTION"), groups.last().segments.single().label)
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
    fun totalsCanFilterSelectedObjectivesOnly() {
        val totals = BadmintonTrainingMethodSeries.totals(
            points = listOf(
                BadmintonDailyLoadPoint(
                    LocalDate.parse("2026-06-01"),
                    0.0,
                    30.0,
                    0.0,
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
                BadmintonDailyLoadPoint(LocalDate.parse("2026-06-01"), 0.0, 0.0, 0.0, mapOf("DECELERATION" to 28.0)),
                BadmintonDailyLoadPoint(LocalDate.parse("2026-06-10"), 0.0, 0.0, 0.0, mapOf("FOOTWORK" to 14.0, "REACTION" to 7.0))
            ),
            selectedKeys = selected
        )

        val labels = selected.map(BadmintonTrainingMethodLabels::label).toSet()
        val segments = groups.flatMap { it.segments }
        assertTrue(segments.all { it.label in labels })
        assertTrue(segments.any { it.colorIndex == BadmintonTrainingMethodSeries.colorIndex("FOOTWORK") })
        assertTrue(segments.any { it.colorIndex == BadmintonTrainingMethodSeries.colorIndex("REACTION") })
    }

    @Test
    fun weeklyStackedGroupsRespectSelectedObjectivesAndStableColors() {
        val groups = BadmintonTrainingMethodSeries.weeklyStackedGroups(
            points = listOf(
                BadmintonDailyLoadPoint(
                    LocalDate.parse("2026-06-01"),
                    0.0,
                    0.0,
                    0.0,
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
        assertTrue(summary.sentence.contains(BadmintonTrainingMethodLabels.label("FOOTWORK")))
        listOf("RACKET_SUPPORT", "UNILATERAL_STABILITY", "LOW_FATIGUE_CONTROL").forEach { legacyKey ->
            assertFalse(summary.sentence.contains(legacyKey))
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
        assertTrue(BadmintonTrainingMethodLabels.label("FOOTWORK") in labels)
        assertTrue(BadmintonTrainingMethodLabels.label("DECELERATION") in labels)
        assertFalse(BadmintonTrainingMethodLabels.label("RACKET_SUPPORT") in labels)
    }

    @Test
    fun summaryDoesNotEmitLowerBodyDeficitLanguage() {
        val summary = BadmintonTrainingMethodSeries.summary(
            listOf(
                BadmintonDailyLoadPoint(
                    LocalDate.parse("2026-06-10"),
                    0.0,
                    30.0,
                    0.0,
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

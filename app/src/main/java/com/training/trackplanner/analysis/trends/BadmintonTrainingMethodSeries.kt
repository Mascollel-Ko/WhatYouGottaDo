package com.training.trackplanner.analysis.trends

import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

object BadmintonTrainingMethodSeries {
    val objectiveKeys: List<String> = listOf(
        "ACCELERATION",
        "DECELERATION",
        "FOOTWORK",
        "JUMP_LANDING",
        "LUNGE_REACH",
        "REACTION",
        "CONDITIONING",
        "ANTI_ROTATION",
        "ROTATION_POWER"
    )

    fun totals(points: List<BadmintonDailyLoadPoint>): Map<String, Double> {
        val totals = linkedMapOf<String, Double>()
        points.forEach { point ->
            point.methodRaw.forEach { (key, value) ->
                if (value <= 0.0) return@forEach
                BadmintonTrainingMethodLabels.keysFrom(
                    courtMovementTypes = emptySet(),
                    transferRoles = setOf(key),
                    skillTargets = emptySet()
                ).forEach { objective ->
                    if (objective in objectiveKeys) totals[objective] = (totals[objective] ?: 0.0) + value
                }
            }
        }
        return totals.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }

    fun summary(points: List<BadmintonDailyLoadPoint>): BadmintonTrainingMethodSummary {
        val today = points.maxOfOrNull { it.date }
            ?: return BadmintonTrainingMethodSummary("최근 7일 배드민턴 전이 목적 기록이 부족합니다.", emptyList(), emptyList())
        val recent7 = totals(points.filter { it.date >= today.minusDays(6) && it.date <= today })
        if (recent7.isEmpty()) {
            return BadmintonTrainingMethodSummary("최근 7일 배드민턴 전이 목적 기록이 부족합니다.", emptyList(), emptyList())
        }
        val topKeys = recent7.entries.take(2).map { it.key }
        val lowKeys = objectiveKeys
            .filter { it !in topKeys }
            .sortedBy { recent7[it] ?: 0.0 }
            .take(2)
        val topText = topKeys.joinToLabelText()
        val lowText = lowKeys.joinToLabelText()
        val sentence = if (lowKeys.isEmpty()) {
            "최근 7일은 $topText 자극이 많습니다."
        } else {
            "최근 7일은 $topText 자극이 많고, $lowText 자극은 상대적으로 적습니다."
        }
        return BadmintonTrainingMethodSummary(sentence, topKeys, lowKeys)
    }

    fun recentComparisonGroups(points: List<BadmintonDailyLoadPoint>): List<StackedBarGroup> {
        val today = points.maxOfOrNull { it.date } ?: return emptyList()
        val recent7 = totals(points.filter { it.date >= today.minusDays(6) && it.date <= today })
        val recent28 = totals(points.filter { it.date >= today.minusDays(27) && it.date <= today })
            .mapValues { (_, value) -> value / 28.0 * 7.0 }
        if (recent7.isEmpty() && recent28.isEmpty()) return emptyList()
        return listOf(
            StackedBarGroup("최근 7일", recent7.toSegments()),
            StackedBarGroup("최근 28일 평균(7일 환산)", recent28.toSegments())
        )
    }

    fun weeklyStackedGroups(points: List<BadmintonDailyLoadPoint>): List<StackedBarGroup> =
        points
            .groupBy { point -> point.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .toSortedMap()
            .mapNotNull { (week, rows) ->
                val byLabel = linkedMapOf<String, Double>()
                rows.forEach { point ->
                    // ponytail: methodRaw intentionally duplicates multi-label stimulus per transfer objective.
                    point.methodRaw.forEach { (key, value) ->
                        val label = BadmintonTrainingMethodLabels.label(key)
                        if (value > 0.0) byLabel[label] = (byLabel[label] ?: 0.0) + value
                    }
                }
                val segments = byLabel.entries
                    .filter { it.value > 0.0 }
                    .map { (label, value) -> StackedBarSegment(label, value) }
                if (segments.isEmpty()) null else StackedBarGroup(week.toString(), segments)
            }

    private fun Map<String, Double>.toSegments(): List<StackedBarSegment> =
        entries
            .filter { (_, value) -> value > 0.0 }
            .sortedByDescending { (_, value) -> value }
            .map { (key, value) -> StackedBarSegment(BadmintonTrainingMethodLabels.label(key), value) }

    private fun List<String>.joinToLabelText(): String =
        joinToString("와 ") { key -> BadmintonTrainingMethodLabels.label(key) }
}

data class BadmintonTrainingMethodSummary(
    val sentence: String,
    val topKeys: List<String>,
    val lowKeys: List<String>
)

package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.badminton.BadmintonObjective

object BadmintonTrainingMethodSeries {
    val objectiveKeys: List<String> = BadmintonObjective.entries.map(BadmintonObjective::name)

    fun colorIndex(key: String): Int =
        objectiveKeys.indexOf(BadmintonTrainingMethodLabels.canonicalObjectiveKey(key)).takeIf { it >= 0 } ?: 0

    fun totals(points: List<BadmintonDailyLoadPoint>, selectedKeys: Set<String>? = null): Map<String, Double> {
        val allowed = selectedKeys?.mapNotNull(BadmintonTrainingMethodLabels::canonicalObjectiveKey)?.toSet()
        val keys = objectiveKeys.filter { key -> allowed == null || key in allowed }
        val totals = linkedMapOf<String, Double>().apply { keys.forEach { key -> put(key, 0.0) } }
        points.forEach { point ->
            point.objectiveStimulus.forEach { (sourceKey, value) ->
                val objective = BadmintonTrainingMethodLabels.canonicalObjectiveKey(sourceKey) ?: return@forEach
                if (objective in totals && value.isFinite() && value > 0.0) {
                    totals[objective] = totals.getValue(objective) + value
                }
            }
        }
        return totals
    }

    fun objectiveBars(points: List<BadmintonDailyLoadPoint>): List<BarItem> {
        val totals = totals(points)
        return objectiveKeys.map { key ->
            BarItem(
                label = BadmintonTrainingMethodLabels.label(key),
                value = totals.getValue(key),
                colorIndex = colorIndex(key),
                colorKey = key
            )
        }
    }

    fun summary(points: List<BadmintonDailyLoadPoint>, selectedKeys: Set<String>? = null): BadmintonTrainingMethodSummary {
        val today = points.maxOfOrNull { it.date }
            ?: return BadmintonTrainingMethodSummary("최근 7일 배드민턴 전이 목적 기록이 부족합니다.", emptyList(), emptyList())
        val recent7 = totals(points.filter { it.date >= today.minusDays(6) && it.date <= today }, selectedKeys)
        if (recent7.values.none { it > 0.0 }) {
            return BadmintonTrainingMethodSummary("최근 7일 배드민턴 전이 목적 기록이 부족합니다.", emptyList(), objectiveKeys.take(2))
        }
        val topKeys = recent7.entries.filter { it.value > 0.0 }.sortedByDescending { it.value }.take(2).map { it.key }
        val candidateKeys = selectedKeys
            ?.mapNotNull(BadmintonTrainingMethodLabels::canonicalObjectiveKey)
            ?.filter { it in objectiveKeys }
            ?: objectiveKeys
        val lowKeys = candidateKeys.filter { it !in topKeys }.sortedBy { recent7[it] ?: 0.0 }.take(2)
        val topText = topKeys.joinToLabelText()
        val sentence = if (lowKeys.isEmpty()) {
            "최근 7일은 $topText 자극이 많습니다."
        } else {
            "최근 7일은 $topText 자극이 많고, ${lowKeys.joinToLabelText()} 자극은 상대적으로 적습니다."
        }
        return BadmintonTrainingMethodSummary(sentence, topKeys, lowKeys)
    }

    fun recentComparisonGroups(points: List<BadmintonDailyLoadPoint>, selectedKeys: Set<String>? = null): List<StackedBarGroup> {
        val today = points.maxOfOrNull { it.date } ?: return emptyList()
        val recent7 = totals(points.filter { it.date >= today.minusDays(6) && it.date <= today }, selectedKeys)
        val recent28 = totals(points.filter { it.date >= today.minusDays(27) && it.date <= today }, selectedKeys)
            .mapValues { (_, value) -> value / 28.0 * 7.0 }
        if (recent7.values.none { it > 0.0 } && recent28.values.none { it > 0.0 }) return emptyList()
        return listOf(
            StackedBarGroup("최근 7일", recent7.toSegments()),
            StackedBarGroup("최근 28일 평균(7일 환산)", recent28.toSegments())
        )
    }

    fun weeklyStackedGroups(points: List<BadmintonDailyLoadPoint>, selectedKeys: Set<String>? = null): List<StackedBarGroup> {
        val allowed = selectedKeys?.mapNotNull(BadmintonTrainingMethodLabels::canonicalObjectiveKey)?.toSet()
        return points.groupBy { point -> AnalysisChartTemporalPolicy.weekStart(point.date) }
            .toSortedMap()
            .mapNotNull { (week, rows) ->
                val byKey = linkedMapOf<String, Double>()
                rows.forEach { point ->
                    // Multi-label objective stimulus intentionally overlaps; it is never divided 1/n.
                    point.objectiveStimulus.forEach { (sourceKey, value) ->
                        val objective = BadmintonTrainingMethodLabels.canonicalObjectiveKey(sourceKey) ?: return@forEach
                        if (objective in objectiveKeys && (allowed == null || objective in allowed) && value > 0.0) {
                            byKey[objective] = (byKey[objective] ?: 0.0) + value
                        }
                    }
                }
                val segments = objectiveKeys.mapNotNull { key ->
                    val value = byKey[key]?.takeIf { it > 0.0 } ?: return@mapNotNull null
                    StackedBarSegment(BadmintonTrainingMethodLabels.label(key), value, colorIndex(key), key)
                }
                segments.takeIf(List<StackedBarSegment>::isNotEmpty)?.let {
                    StackedBarGroup(
                        label = AnalysisChartTemporalPolicy.weekLabel(week).compactLabel,
                        segments = it,
                        weekStart = week
                    )
                }
            }
    }

    private fun Map<String, Double>.toSegments(): List<StackedBarSegment> =
        objectiveKeys.mapNotNull { key ->
            val value = get(key)?.takeIf { it > 0.0 } ?: return@mapNotNull null
            StackedBarSegment(BadmintonTrainingMethodLabels.label(key), value, colorIndex(key), key)
        }

    private fun List<String>.joinToLabelText(): String =
        joinToString("·") { key -> BadmintonTrainingMethodLabels.label(key) }
}

data class BadmintonTrainingMethodSummary(
    val sentence: String,
    val topKeys: List<String>,
    val lowKeys: List<String>
)

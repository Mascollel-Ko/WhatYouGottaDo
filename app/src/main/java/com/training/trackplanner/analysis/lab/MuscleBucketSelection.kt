package com.training.trackplanner.analysis.lab

import com.training.trackplanner.analysis.lab.StrengthAndMuscleMetricSeriesBuilder.MuscleBucket
import com.training.trackplanner.analysis.trends.TrendDataPoint
import com.training.trackplanner.analysis.trends.TrendMetricId

object MuscleBucketSelection {
    data class QuickGroup(val label: String, val buckets: Set<MuscleBucket>)

    val quickGroups: List<QuickGroup> = listOf(
        QuickGroup("가슴", setOf(MuscleBucket.CHEST)),
        QuickGroup("어깨", setOf(MuscleBucket.SHOULDERS)),
        QuickGroup("팔", setOf(MuscleBucket.BICEPS, MuscleBucket.TRICEPS, MuscleBucket.FOREARM_GRIP)),
        QuickGroup("복근/코어", setOf(MuscleBucket.ANTERIOR_CORE, MuscleBucket.LATERAL_CORE, MuscleBucket.ROTATION_CORE)),
        QuickGroup("등", setOf(MuscleBucket.BACK_LATS)),
        QuickGroup("하체", setOf(MuscleBucket.QUADS, MuscleBucket.HAMSTRINGS, MuscleBucket.GLUTES, MuscleBucket.ADDUCTOR_ABDUCTOR)),
        QuickGroup("둔근/햄스트링", setOf(MuscleBucket.GLUTES, MuscleBucket.HAMSTRINGS, MuscleBucket.POSTERIOR_CHAIN_ERECTORS)),
        QuickGroup("종아리/아킬레스", setOf(MuscleBucket.CALVES))
    )

    fun defaultMetrics(
        available: List<MuscleBucket>,
        series: Map<TrendMetricId, List<TrendDataPoint>>
    ): Set<TrendMetricId> =
        available
            .sortedByDescending { bucket ->
                series[bucket.dailyMetric].orEmpty().lastOrNull { it.value != null }?.value ?: 0.0
            }
            .take(3)
            .map { it.dailyMetric }
            .toSet()

    fun filter(available: List<MuscleBucket>, query: String): List<MuscleBucket> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return available
        return available.filter { bucket ->
            listOf(bucket.label, bucket.name, bucket.dailyMetric.name).any { value ->
                needle in value.lowercase()
            }
        }
    }

    fun summary(metrics: Collection<TrendMetricId>, available: List<MuscleBucket>): String {
        val labels = available
            .filter { bucket -> bucket.dailyMetric in metrics }
            .map { it.label }
        if (labels.isEmpty()) return "기본 근육군 선택"
        return if (labels.size <= 3) {
            labels.joinToString(", ") + " 선택됨"
        } else {
            labels.take(3).joinToString(", ") + " 외 ${labels.size - 3}개 선택됨"
        }
    }
}

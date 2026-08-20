package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendMetricId
import java.util.Locale

interface StrictSeriesKey {
    val stableId: String
}

internal val StrictSeriesKey.name: String
    get() = stableId

@JvmInline
internal value class AnalysisFeatureKey private constructor(val value: String) :
    StrictSeriesKey,
    Comparable<AnalysisFeatureKey> {
    override val stableId: String
        get() = value

    init {
        require(value.isNotBlank())
        require(value == value.trim())
    }

    override fun compareTo(other: AnalysisFeatureKey): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        fun metric(metric: TrendMetricId): AnalysisFeatureKey = AnalysisFeatureKey(metric.stableId)

        fun exercise(exerciseStableKey: String, feature: String): AnalysisFeatureKey =
            AnalysisFeatureKey("exercise:${token(exerciseStableKey)}:${token(feature)}")

        fun anatomy(anatomyKey: String, feature: String = "dose"): AnalysisFeatureKey =
            AnalysisFeatureKey("anatomy:${token(anatomyKey)}:${token(feature)}")

        fun parse(value: String): AnalysisFeatureKey = AnalysisFeatureKey(value.trim())

        private fun token(value: String): String = value.trim().lowercase(Locale.ROOT).also { require(it.isNotBlank()) }
    }
}

@JvmInline
internal value class AnalysisSourceKey private constructor(val value: String) : Comparable<AnalysisSourceKey> {
    val name: String
        get() = value

    init {
        require(value.isNotBlank())
        require(value == value.trim())
    }

    override fun compareTo(other: AnalysisSourceKey): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        fun metric(metric: TrendMetricId): AnalysisSourceKey = AnalysisSourceKey("metric:${metric.name}")

        fun exercise(exerciseStableKey: String): AnalysisSourceKey =
            AnalysisSourceKey("exercise:${exerciseStableKey.trim().lowercase(Locale.ROOT)}")

        fun anatomy(anatomyKey: String): AnalysisSourceKey =
            AnalysisSourceKey("anatomy:${anatomyKey.trim().lowercase(Locale.ROOT)}")

        fun parse(value: String): AnalysisSourceKey = AnalysisSourceKey(value.trim())
    }
}

internal fun TrendMetricId.toAnalysisFeatureKey(): AnalysisFeatureKey = AnalysisFeatureKey.metric(this)

internal fun TrendMetricId.toAnalysisSourceKey(): AnalysisSourceKey = AnalysisSourceKey.metric(this)

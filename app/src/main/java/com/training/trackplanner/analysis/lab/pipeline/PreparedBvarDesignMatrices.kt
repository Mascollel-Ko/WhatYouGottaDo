package com.training.trackplanner.analysis.lab.pipeline

internal enum class StrictBvarDesignRole {
    CORE_DYNAMIC,
    CANDIDATE_SOURCE
}

internal data class StrictBvarDesignColumn(
    val index: Int,
    val feature: StrictSeriesKey,
    val source: AnalysisSourceKey?,
    val lag: Int,
    val role: StrictBvarDesignRole
)

internal class PreparedBvarLagDesign private constructor(
    val lag: Int,
    val x: Array<DoubleArray>,
    val y: Array<DoubleArray>,
    val columns: List<StrictBvarDesignColumn>,
    val sourceInputFingerprint: String,
    val fingerprint: String
) {
    init {
        require(lag > 0 && x.isNotEmpty() && x.size == y.size)
        require(y[0].isNotEmpty() && y.all { it.size == y[0].size && it.all(Double::isFinite) })
        require(columns.isNotEmpty() && columns.map { it.index } == columns.indices.toList())
        require(x.all { row -> row.size == columns.size && row.all(Double::isFinite) })
        val candidateColumns = columns.filter { it.role == StrictBvarDesignRole.CANDIDATE_SOURCE }
        require(candidateColumns.isNotEmpty() && candidateColumns.all { it.source != null })
        require(candidateColumns.groupBy { it.source }.values.all { group ->
            group.map { it.lag }.distinct().sorted() == (1..lag).toList()
        })
    }

    companion object {
        fun createValidated(
            lag: Int,
            x: Array<DoubleArray>,
            y: Array<DoubleArray>,
            columns: List<StrictBvarDesignColumn>,
            sourceInputFingerprint: String
        ): PreparedBvarLagDesign = PreparedBvarLagDesign(
            lag,
            x.map(DoubleArray::clone).toTypedArray(),
            y.map(DoubleArray::clone).toTypedArray(),
            columns.toList(),
            sourceInputFingerprint,
            strictFingerprint(
                listOf(
                    sourceInputFingerprint,
                    lag,
                    columns.joinToString("|") { "${it.index}:${it.feature.stableId}:${it.source}:${it.lag}:${it.role}" },
                    x.joinToString("|") { it.joinToString(",") },
                    y.joinToString("|") { it.joinToString(",") },
                    PREPARED_BVAR_DESIGN_VERSION
                )
            )
        )
    }
}

internal class PreparedBvarComparisonDesign private constructor(
    val input: FutureBvarComparisonInput,
    val responseFeatures: List<StrictSeriesKey>,
    val focalFeature: StrictSeriesKey,
    val focalSource: AnalysisSourceKey,
    val responseScalePlans: Map<StrictSeriesKey, ResponseScalePlan>,
    val responseScalingStatistics: Map<StrictSeriesKey, ScalingStatistic>,
    val maximumResponseHorizon: Int,
    val designsByLag: Map<Int, PreparedBvarLagDesign>,
    val fingerprint: String
) {
    val comparisonRowCount: Int
        get() = designsByLag.getValue(designsByLag.keys.first()).y.size

    init {
        require(designsByLag.keys == input.feasibleLags)
        require(designsByLag.values.all { it.sourceInputFingerprint == input.fingerprint })
        require(focalFeature in input.view.candidateMetrics)
        require(input.sourceGrouping.featuresBySource[focalSource]?.contains(focalFeature) == true)
        require(responseScalePlans.keys == responseFeatures.toSet())
        require(responseScalingStatistics.keys == responseFeatures.toSet())
        require(maximumResponseHorizon > 0)
        val referenceY = designsByLag.getValue(designsByLag.keys.first()).y
        require(designsByLag.values.all { it.y.contentDeepEquals(referenceY) })
    }

    companion object {
        fun createValidated(
            input: FutureBvarComparisonInput,
            responseFeatures: List<StrictSeriesKey>,
            focalFeature: StrictSeriesKey,
            focalSource: AnalysisSourceKey,
            responseScalePlans: Map<StrictSeriesKey, ResponseScalePlan>,
            responseScalingStatistics: Map<StrictSeriesKey, ScalingStatistic>,
            maximumResponseHorizon: Int,
            designsByLag: Map<Int, PreparedBvarLagDesign>
        ): PreparedBvarComparisonDesign = PreparedBvarComparisonDesign(
            input,
            responseFeatures.toList(),
            focalFeature,
            focalSource,
            responseScalePlans.toMap(),
            responseScalingStatistics.toMap(),
            maximumResponseHorizon,
            designsByLag.toSortedMap(),
            strictFingerprint(
                listOf(
                    input.fingerprint,
                    responseFeatures.joinToString(",") { it.stableId },
                    focalFeature.stableId,
                    focalSource.value,
                    responseScalePlans.toSortedMap(compareBy { it.stableId }).entries.joinToString("|") {
                        "${it.key.stableId}:${it.value.fingerprint}"
                    },
                    responseScalingStatistics.toSortedMap(compareBy { it.stableId }).entries.joinToString("|") {
                        "${it.key.stableId}:${it.value.mean}:${it.value.scale}"
                    },
                    maximumResponseHorizon,
                    designsByLag.toSortedMap().entries.joinToString("|") { "${it.key}:${it.value.fingerprint}" },
                    PREPARED_BVAR_COMPARISON_DESIGN_VERSION
                )
            )
        )
    }
}

internal object BvarDesignMatrixMaterializer {
    fun materialize(
        context: PreparedAnalysisContext,
        input: FutureBvarComparisonInput
    ): PreparedBvarComparisonDesign {
        require(input.view.rootContextFingerprint == context.fingerprint)
        val indexByWeek = context.canonicalCalendar.weeks.withIndex().associate { it.value to it.index }
        val statistics = input.scalingPlan.baseScalingPlan.statisticsByMetric
        val conditional = input.scalingPlan.conditionalOnFeatureByFeature
        val designs = input.feasibleLags.associateWith { lag ->
            val rowPlan = input.comparisonPlan.plansByLag.getValue(lag)
            val columns = buildList {
                input.view.responseMetrics.forEach { feature ->
                    (1..lag).forEach { lagIndex ->
                        add(StrictBvarDesignColumn(size, feature, null, lagIndex, StrictBvarDesignRole.CORE_DYNAMIC))
                    }
                }
                input.sourceGrouping.featuresBySource.forEach { (source, features) ->
                    features.forEach { feature ->
                        (1..lag).forEach { lagIndex ->
                            add(StrictBvarDesignColumn(size, feature, source, lagIndex, StrictBvarDesignRole.CANDIDATE_SOURCE))
                        }
                    }
                }
            }
            val rowsByWeek = rowPlan.rows.associateBy(PreparedRowIdentity::sourceWeek)
            val y = input.comparisonPlan.commonSourceWeeks.map { week ->
                val index = indexByWeek.getValue(week)
                input.view.responseMetrics.map { feature ->
                    standardizedValue(input.view, feature, index, statistics, conditional)
                }.toDoubleArray()
            }.toTypedArray()
            val x = input.comparisonPlan.commonSourceWeeks.map { week ->
                val row = rowsByWeek.getValue(week)
                columns.map { column ->
                    val lagWeek = row.lagWeeks.getValue(column.lag)
                    standardizedValue(
                        input.view,
                        column.feature,
                        indexByWeek.getValue(lagWeek),
                        statistics,
                        conditional
                    )
                }.toDoubleArray()
            }.toTypedArray()
            PreparedBvarLagDesign.createValidated(lag, x, y, columns, input.fingerprint)
        }
        val focalSource = input.sourceGrouping.sourceByFeature.getValue(input.view.focalFeature)
        val responseStatistics = input.view.responseMetrics.associateWith(statistics::getValue)
        val responseScalePlans = input.view.responseMetrics.associateWith(context.responseScalePlansByMetric::getValue)
        return PreparedBvarComparisonDesign.createValidated(
            input = input,
            responseFeatures = input.view.responseMetrics,
            focalFeature = input.view.focalFeature,
            focalSource = focalSource,
            responseScalePlans = responseScalePlans,
            responseScalingStatistics = responseStatistics,
            maximumResponseHorizon = context.request.horizons.max(),
            designsByLag = designs
        )
    }

    private fun standardizedValue(
        view: BvarPreparedView,
        feature: StrictSeriesKey,
        index: Int,
        statistics: Map<StrictSeriesKey, ScalingStatistic>,
        conditionalOnFeatureByFeature: Map<StrictSeriesKey, StrictSeriesKey>
    ): Double {
        val value = requireNotNull(view.value(feature, index)) { "prepared row lacks ${feature.stableId}" }
        val statistic = statistics.getValue(feature)
        val onFeature = conditionalOnFeatureByFeature[feature]
        if (onFeature != null) {
            val on = requireNotNull(view.value(onFeature, index)) { "conditional support is missing for ${feature.stableId}" }
            if (on <= 0.5) return 0.0
        }
        return (value - statistic.mean) / statistic.scale
    }
}

internal const val PREPARED_BVAR_DESIGN_VERSION = "strict-bvar-lag-design-v0.7"
internal const val PREPARED_BVAR_COMPARISON_DESIGN_VERSION = "strict-bvar-comparison-design-v0.7-response-v1"

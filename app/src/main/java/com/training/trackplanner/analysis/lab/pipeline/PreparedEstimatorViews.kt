package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendMetricId

internal enum class EstimatorPurpose {
    BVAR_FIT,
    BLP_RESPONSE,
    JOHANSEN_LEVEL_SYSTEM,
    VECM_FIT,
    FUTURE_VARIABLE_SELECTION,
    DIAGNOSTIC_ONLY
}

internal sealed class PreparedEstimatorView protected constructor(
    val purpose: EstimatorPurpose,
    val rootContextFingerprint: String,
    metrics: List<TrendMetricId>,
    levelSeriesByMetric: Map<TrendMetricId, LifecycleValidatedLevelSeries>,
    transformedSeriesByMetric: Map<TrendMetricId, TransformedPreparedSeries>,
    representationsByMetric: Map<TrendMetricId, EstimatorRepresentationDecision>,
    val fingerprint: String
) {
    val metrics: List<TrendMetricId> = metrics.toList()
    val levelSeriesByMetric: Map<TrendMetricId, LifecycleValidatedLevelSeries> = levelSeriesByMetric.toMap()
    val transformedSeriesByMetric: Map<TrendMetricId, TransformedPreparedSeries> = transformedSeriesByMetric.toMap()
    val representationsByMetric: Map<TrendMetricId, EstimatorRepresentationDecision> = representationsByMetric.toMap()

    fun sourceCell(metric: TrendMetricId, index: Int): LifecycleValidatedCell? = when (purpose) {
        EstimatorPurpose.JOHANSEN_LEVEL_SYSTEM -> levelSeriesByMetric[metric]?.cells?.getOrNull(index)
        EstimatorPurpose.VECM_FIT -> {
            val level = levelSeriesByMetric[metric]?.cells?.getOrNull(index)
            val difference = transformedSeriesByMetric[metric]?.cells?.getOrNull(index)
            if (level.isUsable() && difference.isUsable()) level else null
        }
        else -> transformedSeriesByMetric[metric]?.cells?.getOrNull(index)
    }

    fun value(metric: TrendMetricId, index: Int): Double? = sourceCell(metric, index)?.value
}

internal class BvarPreparedView private constructor(
    rootContextFingerprint: String,
    metrics: List<TrendMetricId>,
    transformed: Map<TrendMetricId, TransformedPreparedSeries>,
    representations: Map<TrendMetricId, EstimatorRepresentationDecision>,
    fingerprint: String
) : PreparedEstimatorView(
    EstimatorPurpose.BVAR_FIT,
    rootContextFingerprint,
    metrics,
    emptyMap(),
    transformed,
    representations,
    fingerprint
) {
    companion object {
        fun from(context: PreparedAnalysisContext): BvarPreparedView {
            val metrics = context.request.requiredMetrics.sortedBy { it.name }
            val transformed = metrics.associateWith(context.transformedSeriesByMetric::getValue)
            val representations = metrics.associateWith(context.estimatorRepresentationPlan.decisionsByMetric::getValue)
            require(representations.values.all { it.bvarRepresentation == EstimatorSeriesRepresentation.CANONICAL_STATIONARY })
            return BvarPreparedView(
                context.fingerprint,
                metrics,
                transformed,
                representations,
                viewFingerprint(context, EstimatorPurpose.BVAR_FIT, metrics, representations)
            )
        }
    }
}

internal class BlpPreparedView private constructor(
    rootContextFingerprint: String,
    metrics: List<TrendMetricId>,
    transformed: Map<TrendMetricId, TransformedPreparedSeries>,
    representations: Map<TrendMetricId, EstimatorRepresentationDecision>,
    val responseScalePlansByMetric: Map<TrendMetricId, ResponseScalePlan>,
    fingerprint: String
) : PreparedEstimatorView(
    EstimatorPurpose.BLP_RESPONSE,
    rootContextFingerprint,
    metrics,
    emptyMap(),
    transformed,
    representations,
    fingerprint
) {
    companion object {
        fun from(context: PreparedAnalysisContext): BlpPreparedView {
            val metrics = context.request.requiredMetrics.sortedBy { it.name }
            val transformed = metrics.associateWith(context.transformedSeriesByMetric::getValue)
            val representations = metrics.associateWith(context.estimatorRepresentationPlan.decisionsByMetric::getValue)
            require(representations.values.all { it.blpResponseRepresentation == EstimatorSeriesRepresentation.CANONICAL_STATIONARY })
            val scales = context.request.yMetrics.distinct().associateWith(context.responseScalePlansByMetric::getValue)
            return BlpPreparedView(
                context.fingerprint,
                metrics,
                transformed,
                representations,
                scales,
                strictFingerprint(
                    listOf(viewFingerprint(context, EstimatorPurpose.BLP_RESPONSE, metrics, representations)) +
                        scales.toSortedMap(compareBy { it.name }).values.map { it.fingerprint }
                )
            )
        }
    }
}

internal class JohansenPreparedView private constructor(
    rootContextFingerprint: String,
    metrics: List<TrendMetricId>,
    levels: Map<TrendMetricId, LifecycleValidatedLevelSeries>,
    representations: Map<TrendMetricId, EstimatorRepresentationDecision>,
    fingerprint: String
) : PreparedEstimatorView(
    EstimatorPurpose.JOHANSEN_LEVEL_SYSTEM,
    rootContextFingerprint,
    metrics,
    levels,
    emptyMap(),
    representations,
    fingerprint
) {
    companion object {
        fun from(context: PreparedAnalysisContext, metrics: List<TrendMetricId>): JohansenPreparedView {
            val ordered = metrics.distinct().sortedBy { it.name }
            require(ordered.isNotEmpty() && ordered.all { it in context.request.requiredMetrics })
            val levels = ordered.associateWith(context.validatedLevelSeriesByMetric::getValue)
            val representations = ordered.associateWith(context.estimatorRepresentationPlan.decisionsByMetric::getValue)
            require(representations.values.all { it.johansenRepresentation == EstimatorSeriesRepresentation.VALIDATED_LEVEL })
            return JohansenPreparedView(
                context.fingerprint,
                ordered,
                levels,
                representations,
                viewFingerprint(context, EstimatorPurpose.JOHANSEN_LEVEL_SYSTEM, ordered, representations)
            )
        }
    }
}

internal class VecmPreparedView private constructor(
    rootContextFingerprint: String,
    metrics: List<TrendMetricId>,
    levels: Map<TrendMetricId, LifecycleValidatedLevelSeries>,
    transformed: Map<TrendMetricId, TransformedPreparedSeries>,
    representations: Map<TrendMetricId, EstimatorRepresentationDecision>,
    fingerprint: String
) : PreparedEstimatorView(
    EstimatorPurpose.VECM_FIT,
    rootContextFingerprint,
    metrics,
    levels,
    transformed,
    representations,
    fingerprint
) {
    companion object {
        fun from(context: PreparedAnalysisContext, metrics: List<TrendMetricId>): VecmPreparedView {
            val ordered = metrics.distinct().sortedBy { it.name }
            require(ordered.isNotEmpty() && ordered.all { it in context.request.requiredMetrics })
            val levels = ordered.associateWith(context.validatedLevelSeriesByMetric::getValue)
            val transformed = ordered.associateWith(context.transformedSeriesByMetric::getValue)
            val representations = ordered.associateWith(context.estimatorRepresentationPlan.decisionsByMetric::getValue)
            require(representations.values.all {
                it.vecmRepresentation == EstimatorSeriesRepresentation.VALIDATED_LEVEL_AND_ALIGNED_FIRST_DIFFERENCE
            })
            return VecmPreparedView(
                context.fingerprint,
                ordered,
                levels,
                transformed,
                representations,
                viewFingerprint(context, EstimatorPurpose.VECM_FIT, ordered, representations)
            )
        }
    }
}

internal class CandidateEligibilityView private constructor(
    rootContextFingerprint: String,
    metrics: List<TrendMetricId>,
    transformed: Map<TrendMetricId, TransformedPreparedSeries>,
    representations: Map<TrendMetricId, EstimatorRepresentationDecision>,
    fingerprint: String
) : PreparedEstimatorView(
    EstimatorPurpose.FUTURE_VARIABLE_SELECTION,
    rootContextFingerprint,
    metrics,
    emptyMap(),
    transformed,
    representations,
    fingerprint
) {
    companion object {
        fun from(context: PreparedAnalysisContext): CandidateEligibilityView {
            val metrics = context.candidateCatalog.eligibleCandidates
            val transformed = metrics.associateWith(context.transformedSeriesByMetric::getValue)
            val representations = metrics.associateWith(context.estimatorRepresentationPlan.decisionsByMetric::getValue)
            return CandidateEligibilityView(
                context.fingerprint,
                metrics,
                transformed,
                representations,
                viewFingerprint(context, EstimatorPurpose.FUTURE_VARIABLE_SELECTION, metrics, representations)
            )
        }
    }
}

private fun viewFingerprint(
    context: PreparedAnalysisContext,
    purpose: EstimatorPurpose,
    metrics: List<TrendMetricId>,
    representations: Map<TrendMetricId, EstimatorRepresentationDecision>
): String = strictFingerprint(
    listOf(
        context.fingerprint,
        purpose.name,
        metrics.joinToString(",") { it.name },
        representations.toSortedMap(compareBy { it.name }).values.joinToString(",") { it.fingerprint },
        VIEW_VERSION
    )
)

private fun LifecycleValidatedCell?.isUsable(): Boolean =
    this?.state in setOf(StrictCellState.OBSERVED_VALUE, StrictCellState.STRUCTURAL_ZERO) && this?.value?.isFinite() == true

internal const val VIEW_VERSION = "phase-a-prepared-view-v1"

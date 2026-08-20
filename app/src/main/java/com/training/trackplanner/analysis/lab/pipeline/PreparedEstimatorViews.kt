package com.training.trackplanner.analysis.lab.pipeline


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
    metrics: List<StrictSeriesKey>,
    levelSeriesByMetric: Map<StrictSeriesKey, LifecycleValidatedLevelSeries>,
    transformedSeriesByMetric: Map<StrictSeriesKey, TransformedPreparedSeries>,
    representationsByMetric: Map<StrictSeriesKey, EstimatorRepresentationDecision>,
    val fingerprint: String
) {
    val metrics: List<StrictSeriesKey> = metrics.toList()
    val levelSeriesByMetric: Map<StrictSeriesKey, LifecycleValidatedLevelSeries> = levelSeriesByMetric.toMap()
    val transformedSeriesByMetric: Map<StrictSeriesKey, TransformedPreparedSeries> = transformedSeriesByMetric.toMap()
    val representationsByMetric: Map<StrictSeriesKey, EstimatorRepresentationDecision> = representationsByMetric.toMap()

    fun sourceCell(metric: StrictSeriesKey, index: Int): LifecycleValidatedCell? = when (purpose) {
        EstimatorPurpose.JOHANSEN_LEVEL_SYSTEM -> levelSeriesByMetric[metric]?.cells?.getOrNull(index)
        EstimatorPurpose.VECM_FIT -> {
            val level = levelSeriesByMetric[metric]?.cells?.getOrNull(index)
            val difference = transformedSeriesByMetric[metric]?.cells?.getOrNull(index)
            if (level.isUsable() && difference.isUsable()) level else null
        }
        else -> transformedSeriesByMetric[metric]?.cells?.getOrNull(index)
    }

    fun value(metric: StrictSeriesKey, index: Int): Double? = sourceCell(metric, index)?.value
}

internal class BvarPreparedView private constructor(
    rootContextFingerprint: String,
    metrics: List<StrictSeriesKey>,
    transformed: Map<StrictSeriesKey, TransformedPreparedSeries>,
    representations: Map<StrictSeriesKey, EstimatorRepresentationDecision>,
    val focalFeature: StrictSeriesKey,
    responseMetrics: List<StrictSeriesKey>,
    candidateMetrics: List<StrictSeriesKey>,
    supportMetrics: List<StrictSeriesKey>,
    sourceByCandidate: Map<StrictSeriesKey, AnalysisSourceKey>,
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
    val responseMetrics: List<StrictSeriesKey> = responseMetrics.toList()
    val candidateMetrics: List<StrictSeriesKey> = candidateMetrics.toList()
    val supportMetrics: List<StrictSeriesKey> = supportMetrics.toList()
    val sourceByCandidate: Map<StrictSeriesKey, AnalysisSourceKey> = sourceByCandidate.toMap()

    init {
        require(focalFeature in candidateMetrics)
        require(responseMetrics.isNotEmpty() && responseMetrics.all { it in metrics })
        require(candidateMetrics.all { it in metrics } && candidateMetrics.none { it in responseMetrics })
        require(supportMetrics.all { it in metrics } && supportMetrics.none { it in responseMetrics || it in candidateMetrics })
        require(sourceByCandidate.keys == candidateMetrics.toSet())
    }

    companion object {
        fun from(context: PreparedAnalysisContext): BvarPreparedView {
            val metrics = context.request.requiredMetrics.sortedBy { it.name }
            val transformed = metrics.associateWith(context.transformedSeriesByMetric::getValue)
            val representations = metrics.associateWith(context.estimatorRepresentationPlan.decisionsByMetric::getValue)
            require(representations.values.all { it.bvarRepresentation == EstimatorSeriesRepresentation.CANONICAL_STATIONARY })
            val focal = context.request.xMetric
            require(focal is com.training.trackplanner.analysis.trends.TrendMetricId) {
                "dynamic feature sources must use BvarPreparedView.fromV07"
            }
            return BvarPreparedView(
                context.fingerprint,
                metrics,
                transformed,
                representations,
                focal,
                context.request.yMetrics.distinct().sortedBy { it.name },
                listOf(focal),
                emptyList(),
                mapOf(focal to focal.toAnalysisSourceKey()),
                viewFingerprint(context, EstimatorPurpose.BVAR_FIT, metrics, representations)
            )
        }

        fun fromV07(
            context: PreparedAnalysisContext,
            eligibleCandidates: List<StrictSeriesKey>,
            sourceByFeature: Map<StrictSeriesKey, AnalysisSourceKey>,
            supportFeatures: List<StrictSeriesKey> = context.request.supportMetrics
        ): BvarPreparedView {
            val responses = context.request.yMetrics.distinct().sortedBy { it.name }
            val supports = supportFeatures.distinct()
                .filterNot { it in responses }
                .sortedBy { it.name }
            val candidates = (
                listOf(context.request.xMetric) + context.request.controls + eligibleCandidates
                ).distinct().filterNot { it in responses || it in supports }.sortedBy { it.name }
            val metrics = (responses + candidates + supports).distinct()
            require(metrics.all { it in context.transformedSeriesByMetric })
            require(sourceByFeature.keys.containsAll(candidates))
            val transformed = metrics.associateWith(context.transformedSeriesByMetric::getValue)
            val representations = metrics.associateWith(context.estimatorRepresentationPlan.decisionsByMetric::getValue)
            require(representations.values.all { it.bvarRepresentation == EstimatorSeriesRepresentation.CANONICAL_STATIONARY })
            val sources = candidates.associateWith(sourceByFeature::getValue)
            val baseFingerprint = viewFingerprint(context, EstimatorPurpose.BVAR_FIT, metrics, representations)
            return BvarPreparedView(
                context.fingerprint,
                metrics,
                transformed,
                representations,
                context.request.xMetric,
                responses,
                candidates,
                supports,
                sources,
                strictFingerprint(
                    listOf(
                        baseFingerprint,
                        context.request.xMetric.stableId,
                        responses.joinToString(",") { it.stableId },
                        candidates.joinToString(",") { it.stableId },
                        supports.joinToString(",") { it.stableId },
                        sources.entries.joinToString(",") { "${it.key.stableId}:${it.value.value}" },
                        BVAR_V07_VIEW_VERSION
                    )
                )
            )
        }
    }
}

internal class BlpPreparedView private constructor(
    rootContextFingerprint: String,
    metrics: List<StrictSeriesKey>,
    transformed: Map<StrictSeriesKey, TransformedPreparedSeries>,
    representations: Map<StrictSeriesKey, EstimatorRepresentationDecision>,
    val responseScalePlansByMetric: Map<StrictSeriesKey, ResponseScalePlan>,
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
    metrics: List<StrictSeriesKey>,
    levels: Map<StrictSeriesKey, LifecycleValidatedLevelSeries>,
    representations: Map<StrictSeriesKey, EstimatorRepresentationDecision>,
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
        fun from(context: PreparedAnalysisContext, metrics: List<StrictSeriesKey>): JohansenPreparedView {
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
    metrics: List<StrictSeriesKey>,
    levels: Map<StrictSeriesKey, LifecycleValidatedLevelSeries>,
    transformed: Map<StrictSeriesKey, TransformedPreparedSeries>,
    representations: Map<StrictSeriesKey, EstimatorRepresentationDecision>,
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
        fun from(context: PreparedAnalysisContext, metrics: List<StrictSeriesKey>): VecmPreparedView {
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
    metrics: List<StrictSeriesKey>,
    transformed: Map<StrictSeriesKey, TransformedPreparedSeries>,
    representations: Map<StrictSeriesKey, EstimatorRepresentationDecision>,
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
    metrics: List<StrictSeriesKey>,
    representations: Map<StrictSeriesKey, EstimatorRepresentationDecision>
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
internal const val BVAR_V07_VIEW_VERSION = "phase-a-bvar-v07-view-v1"

package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendMetricId

internal data class CandidateExclusion(
    val metric: TrendMetricId,
    val reason: String,
    val integrationAssessmentFingerprint: String
)

internal class PreparedCandidateCatalog private constructor(
    eligibleCandidates: List<TrendMetricId>,
    excludedCandidates: Map<TrendMetricId, CandidateExclusion>,
    preparedSeriesByMetric: Map<TrendMetricId, TransformedPreparedSeries>,
    integrationAssessments: Map<TrendMetricId, IntegrationOrderAssessment>,
    transformationDecisions: Map<TrendMetricId, CanonicalTransformationDecision>,
    val fingerprint: String
) {
    val eligibleCandidates: List<TrendMetricId> = eligibleCandidates.toList()
    val excludedCandidates: Map<TrendMetricId, CandidateExclusion> = excludedCandidates.toMap()
    val preparedSeriesByMetric: Map<TrendMetricId, TransformedPreparedSeries> = preparedSeriesByMetric.toMap()
    val integrationAssessments: Map<TrendMetricId, IntegrationOrderAssessment> = integrationAssessments.toMap()
    val transformationDecisions: Map<TrendMetricId, CanonicalTransformationDecision> = transformationDecisions.toMap()

    companion object {
        fun createValidated(
            request: StrictPreparationRequest,
            transformedCatalog: TransformedPreparedCatalog,
            assessments: Map<TrendMetricId, IntegrationOrderAssessment>,
            transformationPlan: CanonicalTransformationPlan
        ): PreparedCandidateCatalog {
            val eligible = request.optionalCandidates.distinct().filter { metric ->
                metric in transformedCatalog.seriesByMetric && assessments[metric]?.status in CONFIRMED_STATUSES
            }.sortedBy { it.name }
            val excluded = request.optionalCandidates.distinct().filterNot(eligible::contains).associateWith { metric ->
                val assessment = assessments.getValue(metric)
                CandidateExclusion(
                    metric,
                    transformedCatalog.excludedMetrics[metric] ?: "candidate does not satisfy strict data-quality preparation",
                    assessment.fingerprint
                )
            }.toSortedMap(compareBy { it.name })
            val fingerprint = strictFingerprint(
                listOf(
                    eligible.joinToString(",") { it.name },
                    excluded.entries.joinToString(",") { "${it.key.name}:${it.value.reason}:${it.value.integrationAssessmentFingerprint}" },
                    transformedCatalog.fingerprint,
                    transformationPlan.fingerprint
                )
            )
            return PreparedCandidateCatalog(
                eligible,
                excluded,
                transformedCatalog.seriesByMetric,
                assessments,
                transformationPlan.decisionsByMetric,
                fingerprint
            )
        }

        private val CONFIRMED_STATUSES = setOf(
            IntegrationAssessmentStatus.CONFIRMED_I0,
            IntegrationAssessmentStatus.CONFIRMED_I1
        )
    }
}

internal class PreparedAnalysisContext private constructor(
    val request: StrictPreparationRequest,
    val canonicalCalendar: CanonicalCalendar,
    lifecycleMetadataByMetric: Map<TrendMetricId, StrictMetricLifecycle>,
    validatedLevelSeriesByMetric: Map<TrendMetricId, LifecycleValidatedLevelSeries>,
    contiguousSegmentsByMetric: Map<TrendMetricId, List<ContiguousUsableSegment>>,
    integrationAssessmentsByMetric: Map<TrendMetricId, IntegrationOrderAssessment>,
    val canonicalTransformationPlan: CanonicalTransformationPlan,
    val estimatorRepresentationPlan: EstimatorRepresentationPlan,
    transformedSeriesByMetric: Map<TrendMetricId, TransformedPreparedSeries>,
    responseScalePlansByMetric: Map<TrendMetricId, ResponseScalePlan>,
    val candidateCatalog: PreparedCandidateCatalog,
    val preparationPolicy: StrictPreparationPolicy,
    diagnostics: List<String>,
    val fingerprint: String
) {
    val lifecycleMetadataByMetric: Map<TrendMetricId, StrictMetricLifecycle> = lifecycleMetadataByMetric.toMap()
    val validatedLevelSeriesByMetric: Map<TrendMetricId, LifecycleValidatedLevelSeries> = validatedLevelSeriesByMetric.toMap()
    val contiguousSegmentsByMetric: Map<TrendMetricId, List<ContiguousUsableSegment>> = contiguousSegmentsByMetric.mapValues { it.value.toList() }
    val integrationAssessmentsByMetric: Map<TrendMetricId, IntegrationOrderAssessment> = integrationAssessmentsByMetric.toMap()
    val transformedSeriesByMetric: Map<TrendMetricId, TransformedPreparedSeries> = transformedSeriesByMetric.toMap()
    val responseScalePlansByMetric: Map<TrendMetricId, ResponseScalePlan> = responseScalePlansByMetric.toMap()
    val diagnostics: List<String> = diagnostics.toList()

    companion object {
        fun createValidated(
            request: StrictPreparationRequest,
            levelCatalog: LifecycleValidatedLevelCatalog,
            policy: StrictPreparationPolicy = StrictPreparationPolicy.conservative()
        ): StrictPreparationResult {
            if (levelCatalog.seriesByMetric.keys != request.allMetrics) {
                return StrictPreparationResult.Failure(
                    StrictPreparationFailureCode.PREPARED_CONTEXT_INCONSISTENT,
                    listOf("level catalog metrics must equal requested required and optional metrics")
                )
            }
            val assessments = SegmentAwareIntegrationAssessmentAuthority.assess(levelCatalog)
            val planResult = CanonicalTransformationAuthority.createPlan(levelCatalog, assessments, request, policy)
            if (planResult is CanonicalTransformationPlanResult.Failure) {
                return StrictPreparationResult.Failure(planResult.code, planResult.diagnostics)
            }
            val transformationPlan = (planResult as CanonicalTransformationPlanResult.Success).plan
            val transformedCatalog = TransformedPreparedCatalog.createValidated(levelCatalog, transformationPlan)
            if (request.requiredMetrics.any { it !in transformedCatalog.seriesByMetric }) {
                return StrictPreparationResult.Failure(
                    StrictPreparationFailureCode.TRANSFORMATION_PLAN_INCOMPLETE,
                    listOf("every required X/Y/Z metric needs a confirmed transformed representation")
                )
            }
            val representationPlan = EstimatorRepresentationPlan.createValidated(transformationPlan, assessments)
            val responseScales = runCatching {
                request.yMetrics.distinct().associateWith { metric ->
                    ResponseScalePlan.createValidated(transformationPlan.decisionsByMetric.getValue(metric))
                }
            }.getOrElse {
                return StrictPreparationResult.Failure(
                    StrictPreparationFailureCode.RESPONSE_SCALE_PLAN_INCOMPLETE,
                    listOf(it.message ?: "response scale plan failed")
                )
            }
            val candidateCatalog = PreparedCandidateCatalog.createValidated(request, transformedCatalog, assessments, transformationPlan)
            val segments = levelCatalog.seriesByMetric.mapValues { (_, series) ->
                SegmentAwareIntegrationAssessmentAuthority.segments(series)
            }
            val lifecycle = levelCatalog.seriesByMetric.mapValues { it.value.lifecycle }
            val diagnostics = buildList {
                add("strict preparation only; no Bayesian estimator has run")
                add("optional statistical selection disabled until PHASE E")
                assessments.values.filter { it.integrationOrder == null }.forEach {
                    add("${it.metric}: ${it.status} (${it.assessmentReason})")
                }
            }
            val fingerprint = strictFingerprint(
                listOf(
                    request.xMetric.name,
                    request.yMetrics.joinToString(",") { it.name },
                    request.controls.joinToString(",") { it.name },
                    request.optionalCandidates.joinToString(",") { it.name },
                    request.horizons.sorted().joinToString(","),
                    levelCatalog.calendar.fingerprint,
                    lifecycle.toSortedMap(compareBy { it.name }).values.joinToString(",") { it.fingerprint },
                    levelCatalog.seriesByMetric.toSortedMap(compareBy { it.name }).values.joinToString(",") { it.fingerprint },
                    assessments.toSortedMap(compareBy { it.name }).values.joinToString(",") { it.fingerprint },
                    transformationPlan.fingerprint,
                    representationPlan.fingerprint,
                    transformedCatalog.fingerprint,
                    responseScales.toSortedMap(compareBy { it.name }).values.joinToString(",") { it.fingerprint },
                    candidateCatalog.fingerprint,
                    policy.fingerprint,
                    CONTEXT_VERSION
                )
            )
            val context = PreparedAnalysisContext(
                request,
                levelCatalog.calendar,
                lifecycle,
                levelCatalog.seriesByMetric,
                segments,
                assessments,
                transformationPlan,
                representationPlan,
                transformedCatalog.seriesByMetric,
                responseScales,
                candidateCatalog,
                policy,
                diagnostics,
                fingerprint
            )
            return StrictPreparationResult.Success(context, diagnostics)
        }
    }
}

internal const val CONTEXT_VERSION = "phase-a-prepared-context-v1"

package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ln

internal enum class OptionalMetricInconclusivePolicy {
    EXCLUDE_FROM_ELIGIBLE_CANDIDATES,
    RETAIN_AS_DIAGNOSTIC_ONLY
}

internal enum class RequiredMetricInconclusivePolicy {
    FAIL_STRICT_PREPARATION,
    REQUIRE_EXPLICIT_ROBUSTNESS_PLAN
}

internal class StrictPreparationPolicy private constructor(
    val optionalInconclusivePolicy: OptionalMetricInconclusivePolicy,
    val requiredInconclusivePolicy: RequiredMetricInconclusivePolicy,
    explicitTransformations: Map<TrendMetricId, CanonicalSeriesTransformation>,
    val fingerprint: String
) {
    val explicitTransformations: Map<TrendMetricId, CanonicalSeriesTransformation> = explicitTransformations.toMap()

    companion object {
        fun conservative(): StrictPreparationPolicy = createValidated()

        fun createValidated(
            optionalInconclusivePolicy: OptionalMetricInconclusivePolicy = OptionalMetricInconclusivePolicy.EXCLUDE_FROM_ELIGIBLE_CANDIDATES,
            requiredInconclusivePolicy: RequiredMetricInconclusivePolicy = RequiredMetricInconclusivePolicy.FAIL_STRICT_PREPARATION,
            explicitTransformations: Map<TrendMetricId, CanonicalSeriesTransformation> = emptyMap()
        ): StrictPreparationPolicy {
            require(explicitTransformations.values.none { it == CanonicalSeriesTransformation.EXCLUDED })
            val ordered = explicitTransformations.toSortedMap(compareBy { it.name })
            return StrictPreparationPolicy(
                optionalInconclusivePolicy,
                requiredInconclusivePolicy,
                ordered,
                strictFingerprint(
                    listOf(optionalInconclusivePolicy.name, requiredInconclusivePolicy.name) +
                        ordered.map { "${it.key.name}:${it.value.name}" }
                )
            )
        }
    }
}

internal enum class CanonicalSeriesTransformation {
    LEVEL,
    FIRST_DIFFERENCE,
    LOG_LEVEL,
    LOG_DIFFERENCE,
    EXCLUDED
}

internal class CanonicalTransformationDecision private constructor(
    val metric: TrendMetricId,
    val integrationAssessmentFingerprint: String,
    val transformation: CanonicalSeriesTransformation,
    val confirmed: Boolean,
    val policyForced: Boolean,
    val decisionReason: String,
    val transformationVersion: String,
    val fingerprint: String
) {
    companion object {
        fun createValidated(
            metric: TrendMetricId,
            assessment: IntegrationOrderAssessment,
            transformation: CanonicalSeriesTransformation,
            confirmed: Boolean,
            policyForced: Boolean,
            decisionReason: String
        ): CanonicalTransformationDecision {
            require(metric == assessment.metric)
            if (transformation != CanonicalSeriesTransformation.EXCLUDED) require(confirmed)
            val fingerprint = strictFingerprint(
                listOf(
                    metric.name,
                    assessment.fingerprint,
                    transformation.name,
                    confirmed,
                    policyForced,
                    decisionReason,
                    TRANSFORMATION_VERSION
                )
            )
            return CanonicalTransformationDecision(
                metric,
                assessment.fingerprint,
                transformation,
                confirmed,
                policyForced,
                decisionReason,
                TRANSFORMATION_VERSION,
                fingerprint
            )
        }
    }
}

internal class CanonicalTransformationPlan private constructor(
    decisionsByMetric: Map<TrendMetricId, CanonicalTransformationDecision>,
    val sourceAssessmentFingerprints: Map<TrendMetricId, String>,
    val policyFingerprint: String,
    val fingerprint: String
) {
    val decisionsByMetric: Map<TrendMetricId, CanonicalTransformationDecision> = decisionsByMetric.toMap()

    companion object {
        fun createValidated(
            decisionsByMetric: Map<TrendMetricId, CanonicalTransformationDecision>,
            assessments: Map<TrendMetricId, IntegrationOrderAssessment>,
            policy: StrictPreparationPolicy
        ): CanonicalTransformationPlan {
            require(decisionsByMetric.isNotEmpty() && decisionsByMetric.keys == assessments.keys)
            decisionsByMetric.forEach { (metric, decision) ->
                require(decision.metric == metric)
                require(decision.integrationAssessmentFingerprint == assessments.getValue(metric).fingerprint)
            }
            val ordered = decisionsByMetric.toSortedMap(compareBy { it.name })
            val assessmentFingerprints = assessments.mapValues { it.value.fingerprint }.toSortedMap(compareBy { it.name })
            return CanonicalTransformationPlan(
                ordered,
                assessmentFingerprints,
                policy.fingerprint,
                strictFingerprint(
                    ordered.values.map { it.fingerprint } + assessmentFingerprints.values + policy.fingerprint + TRANSFORMATION_VERSION
                )
            )
        }
    }
}

internal sealed interface CanonicalTransformationPlanResult {
    data class Success(val plan: CanonicalTransformationPlan) : CanonicalTransformationPlanResult
    data class Failure(val code: StrictPreparationFailureCode, val diagnostics: List<String>) : CanonicalTransformationPlanResult
}

internal object CanonicalTransformationAuthority {
    fun createPlan(
        catalog: LifecycleValidatedLevelCatalog,
        assessments: Map<TrendMetricId, IntegrationOrderAssessment>,
        request: StrictPreparationRequest,
        policy: StrictPreparationPolicy = StrictPreparationPolicy.conservative()
    ): CanonicalTransformationPlanResult {
        if (catalog.seriesByMetric.keys != assessments.keys) {
            return CanonicalTransformationPlanResult.Failure(
                StrictPreparationFailureCode.TRANSFORMATION_PLAN_INCOMPLETE,
                listOf("every level series requires one integration assessment")
            )
        }
        val requiredFailures = request.requiredMetrics.mapNotNull { metric ->
            assessments[metric]?.takeUnless { it.status in CONFIRMED_STATUSES }?.let { "$metric: ${it.status}" }
                ?: if (metric !in assessments) "$metric: missing assessment" else null
        }
        if (requiredFailures.isNotEmpty()) {
            return CanonicalTransformationPlanResult.Failure(
                StrictPreparationFailureCode.INCONCLUSIVE_TRANSFORMATION,
                requiredFailures
            )
        }
        val decisions = assessments.toSortedMap(compareBy { it.name }).mapValues { (metric, assessment) ->
            val automatic = when (assessment.status) {
                IntegrationAssessmentStatus.CONFIRMED_I0 -> CanonicalSeriesTransformation.LEVEL
                IntegrationAssessmentStatus.CONFIRMED_I1 -> CanonicalSeriesTransformation.FIRST_DIFFERENCE
                else -> CanonicalSeriesTransformation.EXCLUDED
            }
            val transformation = policy.explicitTransformations[metric] ?: automatic
            CanonicalTransformationDecision.createValidated(
                metric,
                assessment,
                transformation,
                confirmed = transformation != CanonicalSeriesTransformation.EXCLUDED,
                policyForced = metric in policy.explicitTransformations,
                decisionReason = if (metric in policy.explicitTransformations) {
                    "explicit canonical transformation policy: ${transformation.name}"
                } else when (transformation) {
                    CanonicalSeriesTransformation.LEVEL -> "confirmed I(0)"
                    CanonicalSeriesTransformation.FIRST_DIFFERENCE -> "confirmed I(1); differenced once by canonical authority"
                    CanonicalSeriesTransformation.EXCLUDED -> when (policy.optionalInconclusivePolicy) {
                        OptionalMetricInconclusivePolicy.EXCLUDE_FROM_ELIGIBLE_CANDIDATES -> "optional metric excluded because integration is unconfirmed"
                        OptionalMetricInconclusivePolicy.RETAIN_AS_DIAGNOSTIC_ONLY -> "optional metric retained as diagnostic only and excluded from modeling"
                    }
                    else -> error("unsupported automatic transformation")
                }
            )
        }
        return CanonicalTransformationPlanResult.Success(
            CanonicalTransformationPlan.createValidated(decisions, assessments, policy)
        )
    }

    private val CONFIRMED_STATUSES = setOf(
        IntegrationAssessmentStatus.CONFIRMED_I0,
        IntegrationAssessmentStatus.CONFIRMED_I1
    )
}

internal class TransformedPreparedSeries private constructor(
    val metric: TrendMetricId,
    val calendar: CanonicalCalendar,
    cells: List<LifecycleValidatedCell>,
    val transformation: CanonicalSeriesTransformation,
    val sourceLevelFingerprint: String,
    val transformationDecisionFingerprint: String,
    val fingerprint: String
) {
    val cells: List<LifecycleValidatedCell> = cells.toList()

    companion object {
        fun createValidated(
            source: LifecycleValidatedLevelSeries,
            decision: CanonicalTransformationDecision
        ): TransformedPreparedSeries {
            require(source.metric == decision.metric)
            require(decision.confirmed && decision.transformation != CanonicalSeriesTransformation.EXCLUDED)
            val cells = when (decision.transformation) {
                CanonicalSeriesTransformation.LEVEL -> source.cells
                CanonicalSeriesTransformation.FIRST_DIFFERENCE -> differenceCells(source, logValues = false)
                CanonicalSeriesTransformation.LOG_LEVEL -> logCells(source)
                CanonicalSeriesTransformation.LOG_DIFFERENCE -> differenceCells(source, logValues = true)
                CanonicalSeriesTransformation.EXCLUDED -> error("excluded metric has no transformed series")
            }
            val fingerprint = strictFingerprint(
                listOf(source.fingerprint, decision.fingerprint, decision.transformation.name) + cells.map { "${it.week}:${it.state}:${it.value}" }
            )
            return TransformedPreparedSeries(
                source.metric,
                source.calendar,
                cells,
                decision.transformation,
                source.fingerprint,
                decision.fingerprint,
                fingerprint
            )
        }

        private fun logCells(source: LifecycleValidatedLevelSeries): List<LifecycleValidatedCell> =
            source.cells.map { cell ->
                val value = cell.value
                if (cell.state in USABLE_STATES && value != null && value > 0.0) {
                    LifecycleValidatedCell(cell.metric, cell.week, StrictCellState.OBSERVED_VALUE, ln(value), cell.provenance)
                } else if (cell.state in LIFECYCLE_STATES) {
                    cell
                } else {
                    LifecycleValidatedCell(cell.metric, cell.week, StrictCellState.MISSING, null, cell.provenance)
                }
            }

        private fun differenceCells(
            source: LifecycleValidatedLevelSeries,
            logValues: Boolean
        ): List<LifecycleValidatedCell> = source.cells.mapIndexed { index, current ->
            if (current.state in LIFECYCLE_STATES) return@mapIndexed current
            val previous = source.cells.getOrNull(index - 1)
            val currentValue = current.value
            val previousValue = previous?.value
            val usable = current.state in USABLE_STATES && previous?.state in USABLE_STATES &&
                currentValue != null && previousValue != null && (!logValues || (currentValue > 0.0 && previousValue > 0.0))
            if (!usable) {
                LifecycleValidatedCell(current.metric, current.week, StrictCellState.MISSING, null, current.provenance)
            } else {
                val value = if (logValues) ln(currentValue!!) - ln(previousValue!!) else currentValue!! - previousValue!!
                LifecycleValidatedCell(current.metric, current.week, StrictCellState.OBSERVED_VALUE, value, current.provenance + previous.provenance)
            }
        }

        private val USABLE_STATES = setOf(StrictCellState.OBSERVED_VALUE, StrictCellState.STRUCTURAL_ZERO)
        private val LIFECYCLE_STATES = setOf(
            StrictCellState.PRE_METRIC_CREATION,
            StrictCellState.NOT_APPLICABLE,
            StrictCellState.VERSION_DISCONTINUITY,
            StrictCellState.CONFLICT
        )
    }
}

internal class TransformedPreparedCatalog private constructor(
    val calendar: CanonicalCalendar,
    seriesByMetric: Map<TrendMetricId, TransformedPreparedSeries>,
    excludedMetrics: Map<TrendMetricId, String>,
    val transformationPlanFingerprint: String,
    val fingerprint: String
) {
    val seriesByMetric: Map<TrendMetricId, TransformedPreparedSeries> = seriesByMetric.toMap()
    val excludedMetrics: Map<TrendMetricId, String> = excludedMetrics.toMap()

    companion object {
        fun createValidated(
            levelCatalog: LifecycleValidatedLevelCatalog,
            plan: CanonicalTransformationPlan
        ): TransformedPreparedCatalog {
            require(levelCatalog.seriesByMetric.keys == plan.decisionsByMetric.keys)
            val included = plan.decisionsByMetric.filterValues { it.transformation != CanonicalSeriesTransformation.EXCLUDED }
                .mapValues { (metric, decision) ->
                    TransformedPreparedSeries.createValidated(levelCatalog.seriesByMetric.getValue(metric), decision)
                }
            val excluded = plan.decisionsByMetric.filterValues { it.transformation == CanonicalSeriesTransformation.EXCLUDED }
                .mapValues { it.value.decisionReason }
            return TransformedPreparedCatalog(
                levelCatalog.calendar,
                included,
                excluded,
                plan.fingerprint,
                strictFingerprint(
                    listOf(levelCatalog.calendar.fingerprint, plan.fingerprint) +
                        included.toSortedMap(compareBy { it.name }).values.map { it.fingerprint } +
                        excluded.toSortedMap(compareBy { it.name }).map { "${it.key.name}:${it.value}" }
                )
            )
        }
    }
}

internal enum class EstimatorSeriesRepresentation {
    CANONICAL_STATIONARY,
    VALIDATED_LEVEL,
    VALIDATED_LEVEL_AND_ALIGNED_FIRST_DIFFERENCE,
    UNAVAILABLE
}

internal class EstimatorRepresentationDecision private constructor(
    val metric: TrendMetricId,
    val bvarRepresentation: EstimatorSeriesRepresentation,
    val blpResponseRepresentation: EstimatorSeriesRepresentation,
    val johansenRepresentation: EstimatorSeriesRepresentation,
    val vecmRepresentation: EstimatorSeriesRepresentation,
    val canonicalTransformationFingerprint: String,
    val decisionReason: String,
    val fingerprint: String
) {
    companion object {
        fun createValidated(
            decision: CanonicalTransformationDecision,
            assessment: IntegrationOrderAssessment
        ): EstimatorRepresentationDecision {
            require(decision.metric == assessment.metric)
            require(decision.integrationAssessmentFingerprint == assessment.fingerprint)
            val levelEligible = assessment.status == IntegrationAssessmentStatus.CONFIRMED_I1
            val vecmEligible = levelEligible && decision.transformation == CanonicalSeriesTransformation.FIRST_DIFFERENCE
            val representation = EstimatorRepresentationDecision(
                decision.metric,
                if (decision.confirmed) EstimatorSeriesRepresentation.CANONICAL_STATIONARY else EstimatorSeriesRepresentation.UNAVAILABLE,
                if (decision.confirmed) EstimatorSeriesRepresentation.CANONICAL_STATIONARY else EstimatorSeriesRepresentation.UNAVAILABLE,
                if (levelEligible) EstimatorSeriesRepresentation.VALIDATED_LEVEL else EstimatorSeriesRepresentation.UNAVAILABLE,
                if (vecmEligible) EstimatorSeriesRepresentation.VALIDATED_LEVEL_AND_ALIGNED_FIRST_DIFFERENCE else EstimatorSeriesRepresentation.UNAVAILABLE,
                decision.fingerprint,
                if (levelEligible) "I(1) level and stationary representations remain distinct" else "stationary representation only",
                ""
            )
            return EstimatorRepresentationDecision(
                representation.metric,
                representation.bvarRepresentation,
                representation.blpResponseRepresentation,
                representation.johansenRepresentation,
                representation.vecmRepresentation,
                representation.canonicalTransformationFingerprint,
                representation.decisionReason,
                strictFingerprint(
                    listOf(
                        representation.metric.name,
                        representation.bvarRepresentation.name,
                        representation.blpResponseRepresentation.name,
                        representation.johansenRepresentation.name,
                        representation.vecmRepresentation.name,
                        representation.canonicalTransformationFingerprint,
                        representation.decisionReason,
                        REPRESENTATION_VERSION
                    )
                )
            )
        }
    }
}

internal class EstimatorRepresentationPlan private constructor(
    decisionsByMetric: Map<TrendMetricId, EstimatorRepresentationDecision>,
    val canonicalTransformationPlanFingerprint: String,
    val fingerprint: String
) {
    val decisionsByMetric: Map<TrendMetricId, EstimatorRepresentationDecision> = decisionsByMetric.toMap()

    companion object {
        fun createValidated(
            transformationPlan: CanonicalTransformationPlan,
            assessments: Map<TrendMetricId, IntegrationOrderAssessment>
        ): EstimatorRepresentationPlan {
            val decisions = transformationPlan.decisionsByMetric.mapValues { (metric, decision) ->
                EstimatorRepresentationDecision.createValidated(decision, assessments.getValue(metric))
            }
            return EstimatorRepresentationPlan(
                decisions,
                transformationPlan.fingerprint,
                strictFingerprint(
                    decisions.toSortedMap(compareBy { it.name }).values.map { it.fingerprint } +
                        transformationPlan.fingerprint + REPRESENTATION_VERSION
                )
            )
        }
    }
}

internal enum class ResponseEstimationScale {
    LEVEL,
    FIRST_DIFFERENCE,
    LOG_LEVEL,
    LOG_DIFFERENCE
}

internal enum class ResponseDisplayScale {
    LEVEL_RESPONSE,
    CUMULATIVE_LEVEL_RESPONSE,
    LOG_RESPONSE,
    APPROXIMATE_PERCENT_RESPONSE
}

internal enum class InverseTransformationRule {
    IDENTITY,
    CUMULATIVE_SUM,
    EXPONENTIAL,
    CUMULATIVE_EXPONENTIAL
}

internal enum class UncertaintyTransformationPolicy {
    TRANSFORM_EACH_POSTERIOR_DRAW_THEN_RECOMPUTE_INTERVALS
}

internal class ResponseScalePlan private constructor(
    val metric: TrendMetricId,
    val estimationScale: ResponseEstimationScale,
    val displayScale: ResponseDisplayScale,
    val inverseTransformationRule: InverseTransformationRule,
    val cumulativeResponse: Boolean,
    val baselineValueRequired: Boolean,
    val exactInversionAvailable: Boolean,
    val uncertaintyTransformationPolicy: UncertaintyTransformationPolicy,
    val interpretationLabel: String,
    val transformationDecisionFingerprint: String,
    val fingerprint: String
) {
    companion object {
        fun createValidated(decision: CanonicalTransformationDecision): ResponseScalePlan {
            require(decision.confirmed)
            val values = when (decision.transformation) {
                CanonicalSeriesTransformation.LEVEL -> ScaleValues(
                    ResponseEstimationScale.LEVEL,
                    ResponseDisplayScale.LEVEL_RESPONSE,
                    InverseTransformationRule.IDENTITY,
                    false,
                    false,
                    true,
                    "level response"
                )
                CanonicalSeriesTransformation.FIRST_DIFFERENCE -> ScaleValues(
                    ResponseEstimationScale.FIRST_DIFFERENCE,
                    ResponseDisplayScale.CUMULATIVE_LEVEL_RESPONSE,
                    InverseTransformationRule.CUMULATIVE_SUM,
                    true,
                    true,
                    true,
                    "cumulative level response reconstructed from first differences"
                )
                CanonicalSeriesTransformation.LOG_LEVEL -> ScaleValues(
                    ResponseEstimationScale.LOG_LEVEL,
                    ResponseDisplayScale.LOG_RESPONSE,
                    InverseTransformationRule.EXPONENTIAL,
                    false,
                    true,
                    true,
                    "log-level response with exact exponential inversion"
                )
                CanonicalSeriesTransformation.LOG_DIFFERENCE -> ScaleValues(
                    ResponseEstimationScale.LOG_DIFFERENCE,
                    ResponseDisplayScale.APPROXIMATE_PERCENT_RESPONSE,
                    InverseTransformationRule.CUMULATIVE_EXPONENTIAL,
                    true,
                    true,
                    true,
                    "log-difference response with draw-wise cumulative exponential inversion"
                )
                CanonicalSeriesTransformation.EXCLUDED -> error("excluded response has no scale plan")
            }
            return ResponseScalePlan(
                decision.metric,
                values.estimation,
                values.display,
                values.inverse,
                values.cumulative,
                values.baselineRequired,
                values.exact,
                UncertaintyTransformationPolicy.TRANSFORM_EACH_POSTERIOR_DRAW_THEN_RECOMPUTE_INTERVALS,
                values.label,
                decision.fingerprint,
                strictFingerprint(
                    listOf(
                        decision.metric.name,
                        values.estimation.name,
                        values.display.name,
                        values.inverse.name,
                        values.cumulative,
                        values.baselineRequired,
                        values.exact,
                        UncertaintyTransformationPolicy.TRANSFORM_EACH_POSTERIOR_DRAW_THEN_RECOMPUTE_INTERVALS.name,
                        values.label,
                        decision.fingerprint,
                        RESPONSE_SCALE_VERSION
                    )
                )
            )
        }

        private data class ScaleValues(
            val estimation: ResponseEstimationScale,
            val display: ResponseDisplayScale,
            val inverse: InverseTransformationRule,
            val cumulative: Boolean,
            val baselineRequired: Boolean,
            val exact: Boolean,
            val label: String
        )
    }
}

internal data class RejectedShockDrawDiagnostic(
    val drawId: String,
    val reason: String
)

internal class IdentifiedShockPosterior private constructor(
    val sourceMetric: TrendMetricId,
    orderedEndogenousMetrics: List<TrendMetricId>,
    val structuralOrdering: String,
    val normalizationPolicy: String,
    posteriorDrawIds: List<String>,
    drawWeights: Map<String, Double>,
    shockSeriesByDraw: Map<String, List<Double>>,
    val calendar: CanonicalCalendar,
    sourceCovarianceDrawFingerprintByDraw: Map<String, String>,
    val sourceBvarPosteriorFingerprint: String,
    val sourceContextFingerprint: String,
    val sourceSystemViewFingerprint: String,
    rejectedDrawDiagnostics: List<RejectedShockDrawDiagnostic>,
    val fingerprint: String
) {
    val orderedEndogenousMetrics: List<TrendMetricId> = orderedEndogenousMetrics.toList()
    val posteriorDrawIds: List<String> = posteriorDrawIds.toList()
    val drawWeights: Map<String, Double> = drawWeights.toMap()
    val shockSeriesByDraw: Map<String, List<Double>> = shockSeriesByDraw.mapValues { it.value.toList() }
    val sourceCovarianceDrawFingerprintByDraw: Map<String, String> = sourceCovarianceDrawFingerprintByDraw.toMap()
    val rejectedDrawDiagnostics: List<RejectedShockDrawDiagnostic> = rejectedDrawDiagnostics.toList()

    companion object {
        fun createValidated(
            sourceMetric: TrendMetricId,
            orderedEndogenousMetrics: List<TrendMetricId>,
            structuralOrdering: String,
            normalizationPolicy: String,
            posteriorDrawIds: List<String>,
            drawWeights: Map<String, Double>,
            shockSeriesByDraw: Map<String, List<Double>>,
            calendar: CanonicalCalendar,
            sourceCovarianceDrawFingerprintByDraw: Map<String, String>,
            sourceBvarPosteriorFingerprint: String,
            sourceContextFingerprint: String,
            sourceSystemViewFingerprint: String,
            rejectedDrawDiagnostics: List<RejectedShockDrawDiagnostic> = emptyList()
        ): IdentifiedShockPosterior {
            require(sourceMetric in orderedEndogenousMetrics)
            require(posteriorDrawIds.size >= 2) { "posterior shock contract requires multiple draw-specific series" }
            require(posteriorDrawIds.distinct().size == posteriorDrawIds.size)
            val ids = posteriorDrawIds.toSet()
            require(drawWeights.keys == ids && shockSeriesByDraw.keys == ids && sourceCovarianceDrawFingerprintByDraw.keys == ids)
            require(drawWeights.values.all { it.isFinite() && it > 0.0 })
            require(abs(drawWeights.values.sum() - 1.0) <= 1e-9)
            require(shockSeriesByDraw.values.all { values -> values.size == calendar.weeks.size && values.all(Double::isFinite) })
            require(sourceCovarianceDrawFingerprintByDraw.values.all(String::isNotBlank))
            require(sourceBvarPosteriorFingerprint.isNotBlank() && sourceContextFingerprint.isNotBlank() && sourceSystemViewFingerprint.isNotBlank())
            val fingerprint = strictFingerprint(
                listOf(
                    sourceMetric.name,
                    orderedEndogenousMetrics.joinToString(",") { it.name },
                    structuralOrdering,
                    normalizationPolicy,
                    posteriorDrawIds.joinToString(","),
                    posteriorDrawIds.joinToString(",") { "$it:${drawWeights.getValue(it)}" },
                    posteriorDrawIds.joinToString(",") { "$it:${shockSeriesByDraw.getValue(it).joinToString("|")}" },
                    calendar.fingerprint,
                    posteriorDrawIds.joinToString(",") { "$it:${sourceCovarianceDrawFingerprintByDraw.getValue(it)}" },
                    sourceBvarPosteriorFingerprint,
                    sourceContextFingerprint,
                    sourceSystemViewFingerprint,
                    rejectedDrawDiagnostics.sortedBy { it.drawId }.joinToString(",") { "${it.drawId}:${it.reason}" },
                    SHOCK_POSTERIOR_VERSION
                )
            )
            return IdentifiedShockPosterior(
                sourceMetric,
                orderedEndogenousMetrics,
                structuralOrdering,
                normalizationPolicy,
                posteriorDrawIds,
                drawWeights,
                shockSeriesByDraw,
                calendar,
                sourceCovarianceDrawFingerprintByDraw,
                sourceBvarPosteriorFingerprint,
                sourceContextFingerprint,
                sourceSystemViewFingerprint,
                rejectedDrawDiagnostics,
                fingerprint
            )
        }
    }
}

internal const val TRANSFORMATION_VERSION = "phase-a-canonical-transformation-v1"
internal const val REPRESENTATION_VERSION = "phase-a-estimator-representation-v1"
internal const val RESPONSE_SCALE_VERSION = "phase-a-response-scale-v1"
internal const val SHOCK_POSTERIOR_VERSION = "phase-a-future-shock-posterior-v1"

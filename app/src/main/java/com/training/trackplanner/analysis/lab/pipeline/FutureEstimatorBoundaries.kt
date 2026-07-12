package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate

internal class FutureBvarInput private constructor(
    val view: BvarPreparedView,
    val rowPlan: PreparedRowPlan,
    val scalingPlan: PreparedScalingPlan,
    val priorFingerprint: String,
    val fingerprint: String
) {
    companion object {
        fun createValidated(
            view: BvarPreparedView,
            rowPlan: PreparedRowPlan,
            scalingPlan: PreparedScalingPlan,
            priorFingerprint: String
        ): FutureBvarInput {
            require(priorFingerprint.isNotBlank())
            require(rowPlan.sourceViewFingerprint == view.fingerprint)
            require(scalingPlan.sourceViewFingerprint == view.fingerprint)
            require(scalingPlan.sourceRowPlanFingerprint == rowPlan.fingerprint)
            return FutureBvarInput(
                view,
                rowPlan,
                scalingPlan,
                priorFingerprint,
                strictFingerprint(listOf(view.fingerprint, rowPlan.fingerprint, scalingPlan.fingerprint, priorFingerprint))
            )
        }
    }
}

internal class BvarPosteriorSourceIdentity private constructor(
    val sourceMetric: TrendMetricId,
    orderedEndogenousMetrics: List<TrendMetricId>,
    val sourceContextFingerprint: String,
    val sourceSystemViewFingerprint: String,
    val sourceRowPlanFingerprint: String,
    val sourceScalingPlanFingerprint: String,
    val sourcePriorFingerprint: String,
    val sourceBvarInputFingerprint: String,
    val sourceBvarPosteriorFingerprint: String,
    eligibleSourceWeeks: List<LocalDate>,
    val fingerprint: String
) {
    val orderedEndogenousMetrics: List<TrendMetricId> = orderedEndogenousMetrics.toList()
    val eligibleSourceWeeks: List<LocalDate> = eligibleSourceWeeks.toList()

    companion object {
        fun createValidated(
            input: FutureBvarInput,
            sourceMetric: TrendMetricId,
            sourceBvarPosteriorFingerprint: String,
            eligibleSourceWeeks: List<LocalDate> = input.rowPlan.rows.map { it.sourceWeek }
        ): BvarPosteriorSourceIdentity {
            require(sourceMetric in input.view.metrics)
            require(sourceBvarPosteriorFingerprint.isNotBlank())
            val rowPlanWeeks = input.rowPlan.rows.map { it.sourceWeek }.toSet()
            val eligibleWeeks = eligibleSourceWeeks.toList()
            require(eligibleWeeks.isNotEmpty() && eligibleWeeks.distinct().size == eligibleWeeks.size)
            require(eligibleWeeks.all { it in rowPlanWeeks })
            val orderedMetrics = input.view.metrics
            val fingerprint = strictFingerprint(
                listOf(
                    sourceMetric.name,
                    orderedMetrics.joinToString(",") { it.name },
                    input.view.rootContextFingerprint,
                    input.view.fingerprint,
                    input.rowPlan.fingerprint,
                    input.scalingPlan.fingerprint,
                    input.priorFingerprint,
                    input.fingerprint,
                    sourceBvarPosteriorFingerprint,
                    eligibleWeeks.joinToString(","),
                    BVAR_POSTERIOR_SOURCE_IDENTITY_VERSION
                )
            )
            return BvarPosteriorSourceIdentity(
                sourceMetric,
                orderedMetrics,
                input.view.rootContextFingerprint,
                input.view.fingerprint,
                input.rowPlan.fingerprint,
                input.scalingPlan.fingerprint,
                input.priorFingerprint,
                input.fingerprint,
                sourceBvarPosteriorFingerprint,
                eligibleWeeks,
                fingerprint
            )
        }
    }
}

internal enum class PosteriorPropagationPolicy {
    DRAW_BY_DRAW_WITHOUT_MEAN_SHOCK_COLLAPSE
}

internal class FutureBlpInput private constructor(
    val view: BlpPreparedView,
    val rowPlan: PreparedRowPlan,
    val identifiedShockPosterior: IdentifiedShockPosterior,
    val horizonPolicy: HorizonPolicy,
    val posteriorPropagationPolicy: PosteriorPropagationPolicy,
    val fingerprint: String
) {
    companion object {
        fun createValidated(
            view: BlpPreparedView,
            rowPlan: PreparedRowPlan,
            identifiedShockPosterior: IdentifiedShockPosterior,
            horizonPolicy: HorizonPolicy,
            posteriorPropagationPolicy: PosteriorPropagationPolicy = PosteriorPropagationPolicy.DRAW_BY_DRAW_WITHOUT_MEAN_SHOCK_COLLAPSE
        ): FutureBlpInput {
            require(rowPlan.sourceViewFingerprint == view.fingerprint)
            require(rowPlan.rootContextFingerprint == view.rootContextFingerprint)
            require(rowPlan.specification.estimatorPurpose == EstimatorPurpose.BLP_RESPONSE)
            require(rowPlan.specification.horizonPolicy == horizonPolicy && horizonPolicy != HorizonPolicy.NOT_APPLICABLE)
            require(identifiedShockPosterior.sourceContextFingerprint == view.rootContextFingerprint)
            require(identifiedShockPosterior.sourceIdentity.orderedEndogenousMetrics == view.metrics)
            val shockRequirement = rowPlan.specification.requirements.singleOrNull { StrictVariableRole.SHOCK_SOURCE in it.roles }
            require(shockRequirement != null && identifiedShockPosterior.sourceMetric == shockRequirement.metric)
            val responseMetrics = rowPlan.specification.requirements
                .filter { StrictVariableRole.RESPONSE in it.roles }
                .map { it.metric }
                .toSet()
            require(responseMetrics == view.responseScalePlansByMetric.keys)
            require(view.responseScalePlansByMetric.all { (metric, scale) ->
                scale.transformationDecisionFingerprint == view.representationsByMetric.getValue(metric).canonicalTransformationFingerprint
            })
            val shockWeeks = identifiedShockPosterior.eligibleSourceWeeks.toSet()
            require(rowPlan.rows.all { it.sourceWeek in shockWeeks })
            require(view.responseScalePlansByMetric.isNotEmpty())
            return FutureBlpInput(
                view,
                rowPlan,
                identifiedShockPosterior,
                horizonPolicy,
                posteriorPropagationPolicy,
                strictFingerprint(
                    listOf(
                        view.fingerprint,
                        rowPlan.fingerprint,
                        identifiedShockPosterior.fingerprint,
                        horizonPolicy.name,
                        posteriorPropagationPolicy.name
                    )
                )
            )
        }
    }
}

internal const val BVAR_POSTERIOR_SOURCE_IDENTITY_VERSION = "phase-a-bvar-posterior-source-identity-v1"

internal class FutureJohansenInput private constructor(
    val view: JohansenPreparedView,
    val rowPlan: PreparedRowPlan,
    val fingerprint: String
) {
    companion object {
        fun createValidated(view: JohansenPreparedView, rowPlan: PreparedRowPlan): FutureJohansenInput {
            require(rowPlan.sourceViewFingerprint == view.fingerprint)
            return FutureJohansenInput(view, rowPlan, strictFingerprint(listOf(view.fingerprint, rowPlan.fingerprint)))
        }
    }
}

internal class FutureVecmInput private constructor(
    val view: VecmPreparedView,
    val rowPlan: PreparedRowPlan,
    val rankConfigurationFingerprint: String,
    val fingerprint: String
) {
    companion object {
        fun createValidated(
            view: VecmPreparedView,
            rowPlan: PreparedRowPlan,
            rankConfigurationFingerprint: String
        ): FutureVecmInput {
            require(rowPlan.sourceViewFingerprint == view.fingerprint)
            require(rankConfigurationFingerprint.isNotBlank())
            return FutureVecmInput(
                view,
                rowPlan,
                rankConfigurationFingerprint,
                strictFingerprint(listOf(view.fingerprint, rowPlan.fingerprint, rankConfigurationFingerprint))
            )
        }
    }
}

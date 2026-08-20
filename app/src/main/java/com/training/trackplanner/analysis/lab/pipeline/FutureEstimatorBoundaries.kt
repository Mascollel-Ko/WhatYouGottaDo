package com.training.trackplanner.analysis.lab.pipeline

import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.sqrt

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

internal enum class StrictDeterministicTermPolicy {
    COMMON_ROW_CENTERING_NO_INTERCEPT
}

internal class PriorActiveSourcePolicy private constructor(
    val fraction: Double,
    val minimum: Double,
    val maximum: Double,
    val version: String,
    val fingerprint: String
) {
    fun targetFor(sourceCount: Int): Double {
        require(sourceCount > 0)
        val upper = sourceCount * (1.0 - 1e-9)
        return (sourceCount * fraction).coerceIn(minimum.coerceAtMost(upper), maximum.coerceAtMost(upper))
    }

    companion object {
        fun fractional(
            fraction: Double = 0.10,
            minimum: Double = 0.50,
            maximum: Double = 20.0,
            version: String = "prior-active-source-fraction-v1"
        ): PriorActiveSourcePolicy {
            require(fraction in 0.0..1.0 && fraction > 0.0)
            require(minimum > 0.0 && maximum >= minimum && version.isNotBlank())
            return PriorActiveSourcePolicy(
                fraction,
                minimum,
                maximum,
                version,
                strictFingerprint(listOf(fraction, minimum, maximum, version))
            )
        }
    }
}

internal class CandidateSourceGrouping private constructor(
    val sourceViewFingerprint: String,
    val sourceByFeature: Map<StrictSeriesKey, AnalysisSourceKey>,
    val featuresBySource: Map<AnalysisSourceKey, List<StrictSeriesKey>>,
    val groupingVersion: String,
    val fingerprint: String
) {
    val sourceCount: Int
        get() = featuresBySource.size

    companion object {
        fun createValidated(
            view: BvarPreparedView,
            sourceByFeature: Map<StrictSeriesKey, AnalysisSourceKey> = view.sourceByCandidate,
            groupingVersion: String
        ): CandidateSourceGrouping {
            require(groupingVersion.isNotBlank())
            require(sourceByFeature.keys == view.candidateMetrics.toSet())
            val grouped = sourceByFeature.entries
                .groupBy({ it.value }, { it.key })
                .mapValues { (_, features) -> features.distinct().sortedBy { it.stableId } }
                .toSortedMap()
            require(grouped.isNotEmpty())
            require(grouped.values.map(List<StrictSeriesKey>::size).distinct().size == 1) {
                "Every candidate source must expose the same number of feature roles"
            }
            return CandidateSourceGrouping(
                view.fingerprint,
                sourceByFeature.toMap(),
                grouped,
                groupingVersion,
                strictFingerprint(
                    listOf(
                        view.fingerprint,
                        groupingVersion,
                        grouped.entries.joinToString("|") { (source, features) ->
                            "${source.value}:${features.joinToString(",") { it.stableId }}"
                        },
                        CANDIDATE_SOURCE_GROUPING_BOUNDARY_VERSION
                    )
                )
            )
        }
    }
}

internal object TauZeroCalibration {
    fun calibrate(
        sourceCount: Int,
        comparisonRowCount: Int,
        lag: Int,
        priorActiveSourceTarget: Double
    ): Double {
        require(sourceCount > 0 && comparisonRowCount > 0 && lag > 0)
        require(priorActiveSourceTarget > 0.0 && priorActiveSourceTarget < sourceCount)
        var lower = 0.0
        var upper = 1.0
        while (effectiveOpenSources(upper, sourceCount, comparisonRowCount, lag) < priorActiveSourceTarget) {
            upper *= 2.0
            require(upper.isFinite()) { "tau0 calibration did not bracket a finite root" }
        }
        repeat(160) {
            val middle = (lower + upper) / 2.0
            if (effectiveOpenSources(middle, sourceCount, comparisonRowCount, lag) < priorActiveSourceTarget) {
                lower = middle
            } else {
                upper = middle
            }
        }
        return (lower + upper) / 2.0
    }

    fun effectiveOpenSources(
        tau: Double,
        sourceCount: Int,
        comparisonRowCount: Int,
        lag: Int
    ): Double {
        require(tau >= 0.0 && tau.isFinite())
        require(sourceCount > 0 && comparisonRowCount > 0 && lag > 0)
        val openness = (1..lag).sumOf { lagIndex ->
            val decayVariance = lagIndex.toDouble().pow(-4.0)
            val a = tau * sqrt(comparisonRowCount * decayVariance)
            a / (1.0 + a)
        }
        return sourceCount.toDouble() * openness / lag
    }
}

internal class FutureBvarComparisonInput private constructor(
    val view: BvarPreparedView,
    val comparisonPlan: PreparedLagComparisonPlan,
    val scalingPlan: PreparedComparisonScalingPlan,
    val sourceGrouping: CandidateSourceGrouping,
    val priorActiveSourcePolicy: PriorActiveSourcePolicy,
    val priorActiveSourceTarget: Double,
    val tauZeroByLag: Map<Int, Double>,
    val deterministicTermPolicy: StrictDeterministicTermPolicy,
    val priorFingerprint: String,
    val fingerprint: String
) {
    val feasibleLags: Set<Int>
        get() = comparisonPlan.feasibleLags

    companion object {
        fun createValidated(
            view: BvarPreparedView,
            comparisonPlan: PreparedLagComparisonPlan,
            scalingPlan: PreparedComparisonScalingPlan,
            sourceGrouping: CandidateSourceGrouping,
            priorActiveSourcePolicy: PriorActiveSourcePolicy,
            deterministicTermPolicy: StrictDeterministicTermPolicy =
                StrictDeterministicTermPolicy.COMMON_ROW_CENTERING_NO_INTERCEPT
        ): FutureBvarComparisonInput {
            require(comparisonPlan.sourceViewFingerprint == view.fingerprint)
            require(comparisonPlan.rootContextFingerprint == view.rootContextFingerprint)
            require(scalingPlan.sourceViewFingerprint == view.fingerprint)
            require(scalingPlan.rootContextFingerprint == view.rootContextFingerprint)
            require(scalingPlan.comparisonPlanFingerprint == comparisonPlan.fingerprint)
            require(sourceGrouping.sourceViewFingerprint == view.fingerprint)
            require(sourceGrouping.sourceByFeature.keys == view.candidateMetrics.toSet())
            require(sourceGrouping.sourceByFeature == view.sourceByCandidate)
            require(comparisonPlan.plansByLag.values.all { plan ->
                plan.rows.map(PreparedRowIdentity::sourceWeek) == comparisonPlan.commonSourceWeeks
            })
            val target = priorActiveSourcePolicy.targetFor(sourceGrouping.sourceCount)
            val tauZero = comparisonPlan.feasibleLags.associateWith { lag ->
                TauZeroCalibration.calibrate(
                    sourceGrouping.sourceCount,
                    comparisonPlan.commonSourceWeeks.size,
                    lag,
                    target
                )
            }
            require(tauZero.values.all { it.isFinite() && it > 0.0 })
            val priorFingerprint = strictFingerprint(
                listOf(
                    STRICT_BVAR_V07_PRIOR_VERSION,
                    "kappa=2",
                    "lagVariance=l^-4",
                    "Sigma~IW(I_m,m+2)",
                    priorActiveSourcePolicy.fingerprint,
                    sourceGrouping.sourceCount,
                    comparisonPlan.commonSourceWeeks.size,
                    target,
                    tauZero.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" },
                    deterministicTermPolicy.name
                )
            )
            return FutureBvarComparisonInput(
                view,
                comparisonPlan,
                scalingPlan,
                sourceGrouping,
                priorActiveSourcePolicy,
                target,
                tauZero,
                deterministicTermPolicy,
                priorFingerprint,
                strictFingerprint(
                    listOf(
                        view.rootContextFingerprint,
                        view.fingerprint,
                        comparisonPlan.fingerprint,
                        scalingPlan.fingerprint,
                        sourceGrouping.fingerprint,
                        priorFingerprint,
                        STRICT_BVAR_V07_BOUNDARY_VERSION
                    )
                )
            )
        }
    }
}

internal class BvarPosteriorSourceIdentity private constructor(
    val sourceMetric: StrictSeriesKey,
    orderedEndogenousMetrics: List<StrictSeriesKey>,
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
    val orderedEndogenousMetrics: List<StrictSeriesKey> = orderedEndogenousMetrics.toList()
    val eligibleSourceWeeks: List<LocalDate> = eligibleSourceWeeks.toList()

    companion object {
        fun createValidated(
            input: FutureBvarInput,
            sourceMetric: StrictSeriesKey,
            sourceBvarPosteriorFingerprint: String,
            eligibleSourceWeeks: List<LocalDate> = input.rowPlan.rows.map { it.sourceWeek }
        ): BvarPosteriorSourceIdentity {
            require(sourceMetric in input.view.metrics)
            require(sourceBvarPosteriorFingerprint.isNotBlank())
            val rowPlanWeeks = input.rowPlan.rows.map { it.sourceWeek }
            val eligibleWeeks = eligibleSourceWeeks.toList()
            require(rowPlanWeeks.isNotEmpty())
            require(eligibleWeeks == rowPlanWeeks) {
                "BVAR shock eligible weeks must exactly match the authoritative row plan"
            }
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
internal const val CANDIDATE_SOURCE_GROUPING_BOUNDARY_VERSION = "candidate-source-grouping-boundary-v1"
internal const val STRICT_BVAR_V07_PRIOR_VERSION = "strict-group-horseshoe-prior-v0.7"
internal const val STRICT_BVAR_V07_BOUNDARY_VERSION = "strict-bvar-multi-lag-boundary-v0.7"

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

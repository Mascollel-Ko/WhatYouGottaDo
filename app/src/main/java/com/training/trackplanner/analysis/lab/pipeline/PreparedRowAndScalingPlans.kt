package com.training.trackplanner.analysis.lab.pipeline

import java.time.LocalDate
import kotlin.math.sqrt

internal enum class StrictVariableRole {
    SHOCK_SOURCE,
    CANDIDATE_SOURCE,
    ENDOGENOUS_STATE,
    RESPONSE,
    CONTEMPORANEOUS_CONTROL,
    CONDITIONAL_SUPPORT,
    LAGGED_CONTROL
}

internal class VariableRowRequirement private constructor(
    val metric: StrictSeriesKey,
    roles: Set<StrictVariableRole>,
    val sourceRequired: Boolean,
    requiredLagOffsets: Set<Int>,
    requiredTargetOffsets: Set<Int>,
    val shockEstimationRequired: Boolean,
    val fingerprint: String
) {
    val roles: Set<StrictVariableRole> = roles.toSet()
    val requiredLagOffsets: Set<Int> = requiredLagOffsets.toSet()
    val requiredTargetOffsets: Set<Int> = requiredTargetOffsets.toSet()

    companion object {
        fun createValidated(
            metric: StrictSeriesKey,
            roles: Set<StrictVariableRole>,
            sourceRequired: Boolean,
            requiredLagOffsets: Set<Int>,
            requiredTargetOffsets: Set<Int>,
            shockEstimationRequired: Boolean = false
        ): VariableRowRequirement {
            require(roles.isNotEmpty())
            require(requiredLagOffsets.all { it > 0 } && requiredTargetOffsets.all { it >= 0 })
            val fingerprint = strictFingerprint(
                listOf(
                    metric.name,
                    roles.map { it.name }.sorted().joinToString(","),
                    sourceRequired,
                    requiredLagOffsets.sorted().joinToString(","),
                    requiredTargetOffsets.sorted().joinToString(","),
                    shockEstimationRequired
                )
            )
            return VariableRowRequirement(
                metric,
                roles,
                sourceRequired,
                requiredLagOffsets,
                requiredTargetOffsets,
                shockEstimationRequired,
                fingerprint
            )
        }
    }
}

internal enum class HorizonPolicy {
    PER_HORIZON,
    SHARED_MULTI_HORIZON,
    DECLARED_REFERENCE_HORIZON,
    NOT_APPLICABLE
}

internal enum class StrictRowComparisonPolicy {
    COMMON_USABLE_ROWS
}

internal class PreparedRowSpecification private constructor(
    requirements: List<VariableRowRequirement>,
    val lag: Int,
    requestedHorizons: Set<Int>,
    val referenceHorizon: Int?,
    val horizonPolicy: HorizonPolicy,
    val estimatorPurpose: EstimatorPurpose,
    val rowComparisonPolicy: StrictRowComparisonPolicy,
    val sourceViewFingerprint: String,
    val fingerprint: String
) {
    val requirements: List<VariableRowRequirement> = requirements.toList()
    val requestedHorizons: Set<Int> = requestedHorizons.toSet()

    companion object {
        fun createValidated(
            requirements: List<VariableRowRequirement>,
            lag: Int,
            requestedHorizons: Set<Int>,
            referenceHorizon: Int?,
            horizonPolicy: HorizonPolicy,
            estimatorPurpose: EstimatorPurpose,
            rowComparisonPolicy: StrictRowComparisonPolicy,
            sourceViewFingerprint: String
        ): PreparedRowSpecification {
            require(requirements.isNotEmpty() && requirements.map { it.metric }.distinct().size == requirements.size)
            require(lag >= 0)
            if (horizonPolicy == HorizonPolicy.NOT_APPLICABLE) {
                require(requestedHorizons.isEmpty() && referenceHorizon == null)
                require(estimatorPurpose != EstimatorPurpose.BLP_RESPONSE)
            } else {
                require(requestedHorizons.isNotEmpty() && requestedHorizons.all { it in STRICT_HORIZON_RANGE })
                require(referenceHorizon in requestedHorizons)
            }
            val ordered = requirements.sortedBy { it.metric.name }
            val fingerprint = strictFingerprint(
                listOf(
                    ordered.joinToString(",") { it.fingerprint },
                    lag,
                    requestedHorizons.sorted().joinToString(","),
                    referenceHorizon,
                    horizonPolicy.name,
                    estimatorPurpose.name,
                    rowComparisonPolicy.name,
                    sourceViewFingerprint,
                    ROW_SPECIFICATION_VERSION
                )
            )
            return PreparedRowSpecification(
                ordered,
                lag,
                requestedHorizons,
                referenceHorizon,
                horizonPolicy,
                estimatorPurpose,
                rowComparisonPolicy,
                sourceViewFingerprint,
                fingerprint
            )
        }
    }
}

internal class PreparedRowIdentity private constructor(
    val sourceWeek: LocalDate,
    targetWeeks: Map<Int, LocalDate>,
    lagWeeks: Map<Int, LocalDate>,
    val rowSpecificationFingerprint: String,
    val fingerprint: String
) {
    val targetWeeks: Map<Int, LocalDate> = targetWeeks.toMap()
    val lagWeeks: Map<Int, LocalDate> = lagWeeks.toMap()

    companion object {
        fun createValidated(
            sourceWeek: LocalDate,
            targetWeeks: Map<Int, LocalDate>,
            lagWeeks: Map<Int, LocalDate>,
            specification: PreparedRowSpecification
        ): PreparedRowIdentity {
            require(targetWeeks.keys.all { it in specification.requestedHorizons })
            require(lagWeeks.keys.all { it > 0 })
            val fingerprint = strictFingerprint(
                listOf(
                    sourceWeek,
                    targetWeeks.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" },
                    lagWeeks.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" },
                    specification.fingerprint
                )
            )
            return PreparedRowIdentity(sourceWeek, targetWeeks, lagWeeks, specification.fingerprint, fingerprint)
        }
    }
}

internal data class PreparedRowExclusion(
    val sourceWeek: LocalDate,
    val reason: String
)

internal class PreparedRowPlan private constructor(
    val sourceViewFingerprint: String,
    val rootContextFingerprint: String,
    val specification: PreparedRowSpecification,
    rows: List<PreparedRowIdentity>,
    exclusions: List<PreparedRowExclusion>,
    val fingerprint: String
) {
    val rows: List<PreparedRowIdentity> = rows.toList()
    val exclusions: List<PreparedRowExclusion> = exclusions.toList()

    companion object {
        fun createValidated(
            view: PreparedEstimatorView,
            specification: PreparedRowSpecification,
            rows: List<PreparedRowIdentity>,
            exclusions: List<PreparedRowExclusion>
        ): PreparedRowPlan {
            require(specification.sourceViewFingerprint == view.fingerprint)
            require(rows.all { it.rowSpecificationFingerprint == specification.fingerprint })
            return PreparedRowPlan(
                view.fingerprint,
                view.rootContextFingerprint,
                specification,
                rows,
                exclusions,
                strictFingerprint(
                    listOf(
                        view.rootContextFingerprint,
                        view.fingerprint,
                        specification.fingerprint,
                        rows.joinToString(",") { it.fingerprint },
                        exclusions.joinToString(",") { "${it.sourceWeek}:${it.reason}" },
                        ROW_PLAN_VERSION
                    )
                )
            )
        }
    }
}

internal object VariableRoleAuthority {
    fun requirements(
        context: PreparedAnalysisContext,
        view: PreparedEstimatorView,
        purpose: EstimatorPurpose,
        lag: Int,
        targetOffsets: Set<Int>
    ): List<VariableRowRequirement> {
        val request = context.request
        return view.metrics.sortedBy { it.name }.map { metric ->
            val roles = buildSet {
                if (metric == request.xMetric) add(StrictVariableRole.SHOCK_SOURCE)
                if (metric in request.yMetrics) add(StrictVariableRole.RESPONSE)
                if (metric in request.controls) add(StrictVariableRole.CONTEMPORANEOUS_CONTROL)
                if (metric in request.supportMetrics) add(StrictVariableRole.CONDITIONAL_SUPPORT)
                if (purpose == EstimatorPurpose.BVAR_FIT && view is BvarPreparedView && metric in view.candidateMetrics) {
                    add(StrictVariableRole.CANDIDATE_SOURCE)
                }
                val endogenous = when {
                    purpose == EstimatorPurpose.BVAR_FIT && view is BvarPreparedView -> metric in view.responseMetrics
                    purpose in setOf(EstimatorPurpose.JOHANSEN_LEVEL_SYSTEM, EstimatorPurpose.VECM_FIT) ->
                        metric !in request.controls && metric !in request.supportMetrics
                    else -> false
                }
                if (endogenous) {
                    add(StrictVariableRole.ENDOGENOUS_STATE)
                }
            }
            val sourceRequired = when {
                StrictVariableRole.CONTEMPORANEOUS_CONTROL in roles || StrictVariableRole.CONDITIONAL_SUPPORT in roles -> true
                purpose == EstimatorPurpose.BLP_RESPONSE && roles == setOf(StrictVariableRole.RESPONSE) -> false
                else -> true
            }
            val lagOffsets = if (
                StrictVariableRole.ENDOGENOUS_STATE in roles ||
                StrictVariableRole.CANDIDATE_SOURCE in roles ||
                StrictVariableRole.SHOCK_SOURCE in roles && purpose != EstimatorPurpose.JOHANSEN_LEVEL_SYSTEM
            ) (1..lag).toSet() else emptySet()
            val requiredTargets = if (StrictVariableRole.RESPONSE in roles && purpose == EstimatorPurpose.BLP_RESPONSE) targetOffsets else emptySet()
            VariableRowRequirement.createValidated(
                metric,
                roles,
                sourceRequired,
                lagOffsets,
                requiredTargets,
                shockEstimationRequired = StrictVariableRole.SHOCK_SOURCE in roles && purpose == EstimatorPurpose.BVAR_FIT
            )
        }
    }
}

internal object RowPlanner {
    fun plan(
        context: PreparedAnalysisContext,
        view: PreparedEstimatorView,
        lag: Int,
        requestedHorizons: Set<Int>,
        referenceHorizon: Int?,
        horizonPolicy: HorizonPolicy
    ): PreparedRowPlan {
        require(view.rootContextFingerprint == context.fingerprint)
        require(view.purpose != EstimatorPurpose.FUTURE_VARIABLE_SELECTION) {
            "PHASE A candidate eligibility has no statistical row-ranking plan"
        }
        val targetOffsets = when (horizonPolicy) {
            HorizonPolicy.SHARED_MULTI_HORIZON -> requestedHorizons
            HorizonPolicy.PER_HORIZON, HorizonPolicy.DECLARED_REFERENCE_HORIZON -> setOf(requireNotNull(referenceHorizon))
            HorizonPolicy.NOT_APPLICABLE -> emptySet()
        }
        val requirements = VariableRoleAuthority.requirements(context, view, view.purpose, lag, targetOffsets)
        val specification = PreparedRowSpecification.createValidated(
            requirements,
            lag,
            requestedHorizons,
            referenceHorizon,
            horizonPolicy,
            view.purpose,
            StrictRowComparisonPolicy.COMMON_USABLE_ROWS,
            view.fingerprint
        )
        val weeks = context.canonicalCalendar.weeks
        val rows = mutableListOf<PreparedRowIdentity>()
        val exclusions = mutableListOf<PreparedRowExclusion>()
        weeks.indices.forEach { index ->
            val targetIndices = targetOffsets.associateWith { index + it }
            val lagIndices = requirements.flatMap { it.requiredLagOffsets }.distinct().associateWith { index - it }
            val inBounds = targetIndices.values.all { it in weeks.indices } && lagIndices.values.all { it in weeks.indices }
            val usable = inBounds && requirements.all { requirement ->
                (!requirement.sourceRequired || view.sourceCell(requirement.metric, index).isUsable()) &&
                    requirement.requiredLagOffsets.all { offset -> view.sourceCell(requirement.metric, index - offset).isUsable() } &&
                    requirement.requiredTargetOffsets.all { offset -> view.sourceCell(requirement.metric, index + offset).isUsable() }
            }
            if (usable) {
                rows += PreparedRowIdentity.createValidated(
                    weeks[index],
                    targetIndices.mapValues { weeks[it.value] },
                    lagIndices.mapValues { weeks[it.value] },
                    specification
                )
            } else {
                exclusions += PreparedRowExclusion(weeks[index], if (!inBounds) "required offset outside canonical calendar" else "required role cell unavailable")
            }
        }
        return PreparedRowPlan.createValidated(view, specification, rows, exclusions)
    }

    fun planWithoutHorizon(
        context: PreparedAnalysisContext,
        view: PreparedEstimatorView,
        lag: Int
    ): PreparedRowPlan = plan(context, view, lag, emptySet(), null, HorizonPolicy.NOT_APPLICABLE)

    fun planLagComparison(
        context: PreparedAnalysisContext,
        view: BvarPreparedView,
        requestedPmax: Int,
        minimumCommonRows: Int = 3
    ): PreparedLagComparisonPlan {
        require(requestedPmax >= 1)
        require(minimumCommonRows >= 3)
        val degradation = mutableListOf<String>()
        for (pmax in requestedPmax downTo 1) {
            val provisional = (1..pmax).associateWith { lag -> planWithoutHorizon(context, view, lag) }
            val commonWeeks = provisional.values
                .map { plan -> plan.rows.map(PreparedRowIdentity::sourceWeek).toSet() }
                .reduce(Set<LocalDate>::intersect)
                .sorted()
            if (commonWeeks.size < minimumCommonRows) {
                degradation += "Pmax=$pmax has ${commonWeeks.size} common rows"
                continue
            }
            val commonSet = commonWeeks.toSet()
            val plans = provisional.mapValues { (_, plan) ->
                val retained = plan.rows.filter { it.sourceWeek in commonSet }
                val excluded = plan.exclusions + plan.rows
                    .filterNot { it.sourceWeek in commonSet }
                    .map { PreparedRowExclusion(it.sourceWeek, "excluded by cross-lag common comparison domain") }
                PreparedRowPlan.createValidated(view, plan.specification, retained, excluded)
            }
            if (pmax < requestedPmax) degradation += "reduced Pmax from $requestedPmax to $pmax"
            return PreparedLagComparisonPlan.createValidated(
                view = view,
                requestedPmax = requestedPmax,
                plansByLag = plans,
                commonSourceWeeks = commonWeeks,
                degradationDiagnostics = degradation
            )
        }
        throw IllegalArgumentException("NO_FEASIBLE_COMMON_LAG_PLAN: ${degradation.joinToString("; ")}")
    }
}

internal class PreparedLagComparisonPlan private constructor(
    val sourceViewFingerprint: String,
    val rootContextFingerprint: String,
    val requestedPmax: Int,
    plansByLag: Map<Int, PreparedRowPlan>,
    commonSourceWeeks: List<LocalDate>,
    degradationDiagnostics: List<String>,
    val fingerprint: String
) {
    val plansByLag: Map<Int, PreparedRowPlan> = plansByLag.toSortedMap()
    val feasibleLags: Set<Int> = plansByLag.keys.toSortedSet()
    val pmax: Int = feasibleLags.max()
    val commonSourceWeeks: List<LocalDate> = commonSourceWeeks.toList()
    val degradationDiagnostics: List<String> = degradationDiagnostics.toList()

    companion object {
        fun createValidated(
            view: BvarPreparedView,
            requestedPmax: Int,
            plansByLag: Map<Int, PreparedRowPlan>,
            commonSourceWeeks: List<LocalDate>,
            degradationDiagnostics: List<String>
        ): PreparedLagComparisonPlan {
            require(requestedPmax >= 1)
            require(plansByLag.isNotEmpty() && plansByLag.keys == (1..plansByLag.keys.max()).toSet())
            require(commonSourceWeeks.size >= 3 && commonSourceWeeks == commonSourceWeeks.distinct().sorted())
            require(plansByLag.values.all { plan ->
                plan.sourceViewFingerprint == view.fingerprint &&
                    plan.rootContextFingerprint == view.rootContextFingerprint &&
                    plan.rows.map(PreparedRowIdentity::sourceWeek) == commonSourceWeeks
            })
            val fingerprint = strictFingerprint(
                listOf(
                    view.rootContextFingerprint,
                    view.fingerprint,
                    requestedPmax,
                    plansByLag.keys.sorted().joinToString(","),
                    commonSourceWeeks.joinToString(","),
                    plansByLag.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value.fingerprint}" },
                    degradationDiagnostics.joinToString("|"),
                    LAG_COMPARISON_PLAN_VERSION
                )
            )
            return PreparedLagComparisonPlan(
                view.fingerprint,
                view.rootContextFingerprint,
                requestedPmax,
                plansByLag,
                commonSourceWeeks,
                degradationDiagnostics,
                fingerprint
            )
        }
    }
}

internal enum class ScalingPolicy {
    STANDARDIZE_TRAINING_ROWS
}

internal data class ScalingStatistic(
    val mean: Double,
    val scale: Double
)

internal enum class ScalingFailureCode {
    TOO_FEW_TRAINING_VALUES,
    NEAR_CONSTANT_TRAINING_SERIES,
    NON_FINITE_TRAINING_SERIES
}

internal class ScalingPlanFailureException(
    val code: ScalingFailureCode,
    val metric: StrictSeriesKey,
    message: String
) : IllegalArgumentException("$code: $message")

internal class PreparedScalingPlan private constructor(
    statisticsByMetric: Map<StrictSeriesKey, ScalingStatistic>,
    trainingRows: List<LocalDate>,
    val sourceViewFingerprint: String,
    val sourceRowPlanFingerprint: String,
    val rootContextFingerprint: String,
    val scalingPolicy: ScalingPolicy,
    diagnostics: List<String>,
    val fingerprint: String
) {
    val statisticsByMetric: Map<StrictSeriesKey, ScalingStatistic> = statisticsByMetric.toMap()
    val trainingRows: List<LocalDate> = trainingRows.toList()
    val diagnostics: List<String> = diagnostics.toList()

    companion object {
        fun createValidated(
            view: PreparedEstimatorView,
            rowPlan: PreparedRowPlan,
            statisticsByMetric: Map<StrictSeriesKey, ScalingStatistic>,
            trainingRows: List<LocalDate>,
            policy: ScalingPolicy,
            diagnostics: List<String>
        ): PreparedScalingPlan {
            require(rowPlan.sourceViewFingerprint == view.fingerprint)
            require(rowPlan.rootContextFingerprint == view.rootContextFingerprint)
            require(statisticsByMetric.keys == view.metrics.toSet())
            require(statisticsByMetric.values.all { it.mean.isFinite() && it.scale.isFinite() && it.scale > 0.0 })
            return PreparedScalingPlan(
                statisticsByMetric,
                trainingRows,
                view.fingerprint,
                rowPlan.fingerprint,
                view.rootContextFingerprint,
                policy,
                diagnostics,
                strictFingerprint(
                    listOf(
                        view.rootContextFingerprint,
                        view.fingerprint,
                        rowPlan.fingerprint,
                        trainingRows.joinToString(","),
                        statisticsByMetric.toSortedMap(compareBy { it.name }).entries.joinToString(",") {
                            "${it.key.name}:${it.value.mean}:${it.value.scale}"
                        },
                        policy.name,
                        SCALING_PLAN_VERSION
                    )
                )
            )
        }
    }
}

internal object ScalingPlanner {
    fun plan(
        context: PreparedAnalysisContext,
        view: PreparedEstimatorView,
        rowPlan: PreparedRowPlan,
        trainingRows: Collection<LocalDate>,
        policy: ScalingPolicy = ScalingPolicy.STANDARDIZE_TRAINING_ROWS
    ): PreparedScalingPlan {
        require(view.rootContextFingerprint == context.fingerprint)
        val eligibleRows = rowPlan.rows.map { it.sourceWeek }.toSet()
        val orderedTraining = trainingRows.distinct().sorted()
        require(orderedTraining.isNotEmpty() && orderedTraining.all { it in eligibleRows })
        val indexByWeek = context.canonicalCalendar.weeks.withIndex().associate { it.value to it.index }
        val statistics = view.metrics.associateWith { metric ->
            val values = orderedTraining.map { week ->
                view.value(metric, indexByWeek.getValue(week)) ?: error("training row lacks a finite prepared value for $metric")
            }
            scalingStatistic(metric, values)
        }
        return PreparedScalingPlan.createValidated(
            view,
            rowPlan,
            statistics,
            orderedTraining,
            policy,
            listOf("scaling statistics use ${orderedTraining.size} declared training rows only")
        )
    }

    fun planForComparison(
        context: PreparedAnalysisContext,
        view: BvarPreparedView,
        comparisonPlan: PreparedLagComparisonPlan,
        conditionalOnFeatureByFeature: Map<StrictSeriesKey, StrictSeriesKey> = emptyMap()
    ): PreparedComparisonScalingPlan {
        require(comparisonPlan.sourceViewFingerprint == view.fingerprint)
        val indexByWeek = context.canonicalCalendar.weeks.withIndex().associate { it.value to it.index }
        val statistics = view.metrics.associateWith { metric ->
            if (metric in view.supportMetrics) return@associateWith ScalingStatistic(mean = 0.0, scale = 1.0)
            val selectedWeeks = comparisonPlan.commonSourceWeeks.filter { week ->
                val onFeature = conditionalOnFeatureByFeature[metric] ?: return@filter true
                val index = indexByWeek.getValue(week)
                view.value(onFeature, index)?.let { it > 0.5 } == true
            }
            val values = selectedWeeks.map { week ->
                view.value(metric, indexByWeek.getValue(week))
                    ?: error("comparison row lacks a finite prepared value for ${metric.stableId}")
            }
            scalingStatistic(metric, values)
        }
        val representative = comparisonPlan.plansByLag.getValue(comparisonPlan.pmax)
        val base = PreparedScalingPlan.createValidated(
            view,
            representative,
            statistics,
            comparisonPlan.commonSourceWeeks,
            ScalingPolicy.STANDARDIZE_TRAINING_ROWS,
            buildList {
                add("one common scaling identity for lags ${comparisonPlan.feasibleLags}")
                if (view.supportMetrics.isNotEmpty()) add("conditional support features retain identity scale and are not model columns")
                conditionalOnFeatureByFeature.keys.sortedBy { it.stableId }.forEach {
                    add("${it.stableId} centered on exposed comparison rows only")
                }
            }
        )
        return PreparedComparisonScalingPlan.createValidated(
            view,
            comparisonPlan,
            base,
            conditionalOnFeatureByFeature
        )
    }

    private fun scalingStatistic(metric: StrictSeriesKey, values: List<Double>): ScalingStatistic {
        if (values.size < 3) {
            throw ScalingPlanFailureException(ScalingFailureCode.TOO_FEW_TRAINING_VALUES, metric, "$metric has ${values.size} training values")
        }
        if (values.any { !it.isFinite() }) {
            throw ScalingPlanFailureException(ScalingFailureCode.NON_FINITE_TRAINING_SERIES, metric, "$metric has non-finite training values")
        }
        if (values.map(Double::toRawBits).distinct().size < 2) {
            throw ScalingPlanFailureException(ScalingFailureCode.NEAR_CONSTANT_TRAINING_SERIES, metric, "$metric has fewer than two distinguishable values")
        }
        val mean = values.average()
        if (!mean.isFinite()) {
            throw ScalingPlanFailureException(ScalingFailureCode.NON_FINITE_TRAINING_SERIES, metric, "$metric mean is not finite")
        }
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        if (!variance.isFinite() || variance < 0.0) {
            throw ScalingPlanFailureException(ScalingFailureCode.NON_FINITE_TRAINING_SERIES, metric, "$metric variance is not finite")
        }
        val scale = sqrt(variance)
        val maxAbs = values.maxOf { kotlin.math.abs(it) }
        val minimumScale = maxOf(1e-12, 1e-10 * maxOf(1.0, maxAbs))
        if (!scale.isFinite() || scale <= minimumScale) {
            throw ScalingPlanFailureException(ScalingFailureCode.NEAR_CONSTANT_TRAINING_SERIES, metric, "$metric scale $scale <= $minimumScale")
        }
        return ScalingStatistic(mean, scale)
    }
}

internal class PreparedComparisonScalingPlan private constructor(
    val sourceViewFingerprint: String,
    val rootContextFingerprint: String,
    val comparisonPlanFingerprint: String,
    val baseScalingPlan: PreparedScalingPlan,
    conditionalOnFeatureByFeature: Map<StrictSeriesKey, StrictSeriesKey>,
    val fingerprint: String
) {
    val conditionalOnFeatureByFeature: Map<StrictSeriesKey, StrictSeriesKey> =
        conditionalOnFeatureByFeature.toMap()

    companion object {
        fun createValidated(
            view: BvarPreparedView,
            comparisonPlan: PreparedLagComparisonPlan,
            baseScalingPlan: PreparedScalingPlan,
            conditionalOnFeatureByFeature: Map<StrictSeriesKey, StrictSeriesKey>
        ): PreparedComparisonScalingPlan {
            require(comparisonPlan.sourceViewFingerprint == view.fingerprint)
            require(baseScalingPlan.sourceViewFingerprint == view.fingerprint)
            require(baseScalingPlan.rootContextFingerprint == view.rootContextFingerprint)
            require(baseScalingPlan.trainingRows == comparisonPlan.commonSourceWeeks)
            require(baseScalingPlan.sourceRowPlanFingerprint == comparisonPlan.plansByLag.getValue(comparisonPlan.pmax).fingerprint)
            require(conditionalOnFeatureByFeature.keys.all { it in view.metrics })
            require(conditionalOnFeatureByFeature.values.all { it in view.metrics })
            return PreparedComparisonScalingPlan(
                view.fingerprint,
                view.rootContextFingerprint,
                comparisonPlan.fingerprint,
                baseScalingPlan,
                conditionalOnFeatureByFeature,
                strictFingerprint(
                    listOf(
                        view.rootContextFingerprint,
                        view.fingerprint,
                        comparisonPlan.fingerprint,
                        baseScalingPlan.fingerprint,
                        conditionalOnFeatureByFeature.toSortedMap(compareBy { it.stableId }).entries.joinToString(",") {
                            "${it.key.stableId}:${it.value.stableId}"
                        },
                        CONDITIONAL_FEATURE_ENGINEERING_VERSION,
                        COMPARISON_SCALING_VERSION
                    )
                )
            )
        }
    }
}

private fun LifecycleValidatedCell?.isUsable(): Boolean =
    this?.state in setOf(StrictCellState.OBSERVED_VALUE, StrictCellState.STRUCTURAL_ZERO) && this?.value?.isFinite() == true

internal const val ROW_SPECIFICATION_VERSION = "phase-a-row-specification-v1"
internal const val ROW_PLAN_VERSION = "phase-a-row-plan-v1"
internal const val SCALING_PLAN_VERSION = "phase-a-scaling-plan-v1"
internal const val LAG_COMPARISON_PLAN_VERSION = "phase-a-cross-lag-common-rows-v1"
internal const val COMPARISON_SCALING_VERSION = "phase-a-common-comparison-scaling-v1"
internal const val CONDITIONAL_FEATURE_ENGINEERING_VERSION = "conditional-exposure-deviation-v1"

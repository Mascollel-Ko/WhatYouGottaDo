package com.training.trackplanner.analysis.lab.pipeline

import com.training.trackplanner.analysis.trends.TrendMetricId
import java.time.LocalDate
import kotlin.math.sqrt

internal enum class StrictVariableRole {
    SHOCK_SOURCE,
    ENDOGENOUS_STATE,
    RESPONSE,
    CONTEMPORANEOUS_CONTROL,
    LAGGED_CONTROL
}

internal class VariableRowRequirement private constructor(
    val metric: TrendMetricId,
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
            metric: TrendMetricId,
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
        purpose: EstimatorPurpose,
        lag: Int,
        targetOffsets: Set<Int>
    ): List<VariableRowRequirement> {
        val request = context.request
        return request.requiredMetrics.sortedBy { it.name }.map { metric ->
            val roles = buildSet {
                if (metric == request.xMetric) add(StrictVariableRole.SHOCK_SOURCE)
                if (metric in request.yMetrics) add(StrictVariableRole.RESPONSE)
                if (metric in request.controls) add(StrictVariableRole.CONTEMPORANEOUS_CONTROL)
                if (purpose in setOf(EstimatorPurpose.BVAR_FIT, EstimatorPurpose.JOHANSEN_LEVEL_SYSTEM, EstimatorPurpose.VECM_FIT) && metric !in request.controls) {
                    add(StrictVariableRole.ENDOGENOUS_STATE)
                }
            }
            val sourceRequired = when {
                StrictVariableRole.CONTEMPORANEOUS_CONTROL in roles -> true
                purpose == EstimatorPurpose.BLP_RESPONSE && roles == setOf(StrictVariableRole.RESPONSE) -> false
                else -> true
            }
            val lagOffsets = if (
                StrictVariableRole.ENDOGENOUS_STATE in roles ||
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
        val requirements = VariableRoleAuthority.requirements(context, view.purpose, lag, targetOffsets)
            .filter { it.metric in view.metrics }
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
    message: String
) : IllegalArgumentException("$code: $message")

internal class PreparedScalingPlan private constructor(
    statisticsByMetric: Map<TrendMetricId, ScalingStatistic>,
    trainingRows: List<LocalDate>,
    val sourceViewFingerprint: String,
    val sourceRowPlanFingerprint: String,
    val rootContextFingerprint: String,
    val scalingPolicy: ScalingPolicy,
    diagnostics: List<String>,
    val fingerprint: String
) {
    val statisticsByMetric: Map<TrendMetricId, ScalingStatistic> = statisticsByMetric.toMap()
    val trainingRows: List<LocalDate> = trainingRows.toList()
    val diagnostics: List<String> = diagnostics.toList()

    companion object {
        fun createValidated(
            view: PreparedEstimatorView,
            rowPlan: PreparedRowPlan,
            statisticsByMetric: Map<TrendMetricId, ScalingStatistic>,
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

    private fun scalingStatistic(metric: TrendMetricId, values: List<Double>): ScalingStatistic {
        if (values.size < 3) {
            throw ScalingPlanFailureException(ScalingFailureCode.TOO_FEW_TRAINING_VALUES, "$metric has ${values.size} training values")
        }
        if (values.any { !it.isFinite() }) {
            throw ScalingPlanFailureException(ScalingFailureCode.NON_FINITE_TRAINING_SERIES, "$metric has non-finite training values")
        }
        if (values.map(Double::toRawBits).distinct().size < 2) {
            throw ScalingPlanFailureException(ScalingFailureCode.NEAR_CONSTANT_TRAINING_SERIES, "$metric has fewer than two distinguishable values")
        }
        val mean = values.average()
        if (!mean.isFinite()) {
            throw ScalingPlanFailureException(ScalingFailureCode.NON_FINITE_TRAINING_SERIES, "$metric mean is not finite")
        }
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        if (!variance.isFinite() || variance < 0.0) {
            throw ScalingPlanFailureException(ScalingFailureCode.NON_FINITE_TRAINING_SERIES, "$metric variance is not finite")
        }
        val scale = sqrt(variance)
        val maxAbs = values.maxOf { kotlin.math.abs(it) }
        val minimumScale = maxOf(1e-12, 1e-10 * maxOf(1.0, maxAbs))
        if (!scale.isFinite() || scale <= minimumScale) {
            throw ScalingPlanFailureException(ScalingFailureCode.NEAR_CONSTANT_TRAINING_SERIES, "$metric scale $scale <= $minimumScale")
        }
        return ScalingStatistic(mean, scale)
    }
}

private fun LifecycleValidatedCell?.isUsable(): Boolean =
    this?.state in setOf(StrictCellState.OBSERVED_VALUE, StrictCellState.STRUCTURAL_ZERO) && this?.value?.isFinite() == true

internal const val ROW_SPECIFICATION_VERSION = "phase-a-row-specification-v1"
internal const val ROW_PLAN_VERSION = "phase-a-row-plan-v1"
internal const val SCALING_PLAN_VERSION = "phase-a-scaling-plan-v1"

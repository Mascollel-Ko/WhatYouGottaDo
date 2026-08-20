package com.training.trackplanner.analysis.lab.pipeline

internal enum class StrictBvarPlanningFailureCode {
    PREPARATION_FAILED,
    FOCAL_FEATURE_UNAVAILABLE,
    NO_FOCAL_VARIATION,
    NO_TARGET_VARIATION,
    NO_FEASIBLE_COMMON_LAG_PLAN,
    METADATA_INCOMPLETE,
    REPRESENTATION_POLICY_UNAVAILABLE,
    REPRESENTATION_DIAGNOSTIC_CONFLICT,
    SCALING_UNAVAILABLE,
    SOURCE_IDENTITY_UNAVAILABLE
}

internal sealed interface StrictBvarPlanningResult {
    data class Success(
        val context: PreparedAnalysisContext,
        val input: FutureBvarComparisonInput,
        val diagnostics: List<String>
    ) : StrictBvarPlanningResult

    data class Failure(
        val code: StrictBvarPlanningFailureCode,
        val diagnostics: List<String>
    ) : StrictBvarPlanningResult
}

/**
 * Coordinates existing Phase A authorities. It owns no ingestion, transformation,
 * row, or scaling formula.
 */
internal object StrictBvarV07PlanningAuthority {
    fun plan(
        bundle: StrictPhaseAInputBundle,
        requestedPmax: Int = 4,
        priorActiveSourcePolicy: PriorActiveSourcePolicy = PriorActiveSourcePolicy.fractional()
    ): StrictBvarPlanningResult {
        val prepared = StrictTimeSeriesPreparationPipeline.prepare(bundle)
        if (prepared is StrictPreparationResult.Failure) {
            return StrictBvarPlanningResult.Failure(
                mapPreparationFailure(prepared),
                prepared.diagnostics
            )
        }
        val context = (prepared as StrictPreparationResult.Success).context
        val requiredCandidates = (listOf(context.request.xMetric) + context.request.controls)
            .distinct()
            .sortedBy { it.stableId }
        if (requiredCandidates.any { it !in context.transformedSeriesByMetric }) {
            return StrictBvarPlanningResult.Failure(
                StrictBvarPlanningFailureCode.FOCAL_FEATURE_UNAVAILABLE,
                listOf("A focal source has no approved prepared representation")
            )
        }

        val remainingOptional = context.candidateCatalog.eligibleCandidates
            .filterNot { it in requiredCandidates }
            .toMutableList()
        val diagnostics = prepared.readinessDiagnostics.toMutableList()
        context.candidateCatalog.excludedCandidates.values.forEach { exclusion ->
            diagnostics += "excluded auto candidate ${exclusion.metric.stableId}: ${exclusion.reason}"
        }

        while (true) {
            val candidates = (requiredCandidates + remainingOptional).distinct().sortedBy { it.stableId }
            val conditional = bundle.conditionalOnFeatureByFeature.filterKeys { feature ->
                feature in candidates || feature in context.request.yMetrics
            }
            val supportFeatures = conditional.values.distinct().filter { it in context.transformedSeriesByMetric }
            val missingRequiredSupport = conditional
                .filterKeys { it in requiredCandidates || it in context.request.yMetrics }
                .values.any { it !in context.transformedSeriesByMetric }
            if (missingRequiredSupport) {
                return StrictBvarPlanningResult.Failure(
                    StrictBvarPlanningFailureCode.FOCAL_FEATURE_UNAVAILABLE,
                    listOf("A focal conditional feature lacks its reviewed ON support feature")
                )
            }

            val attempt = runCatching {
                val view = BvarPreparedView.fromV07(
                    context,
                    remainingOptional,
                    bundle.sourceByFeature,
                    supportFeatures
                )
                val comparison = RowPlanner.planLagComparison(context, view, requestedPmax)
                val scaling = ScalingPlanner.planForComparison(context, view, comparison, conditional)
                val grouping = CandidateSourceGrouping.createValidated(
                    view,
                    groupingVersion = STRICT_SOURCE_GROUPING_VERSION
                )
                val input = FutureBvarComparisonInput.createValidated(
                    view,
                    comparison,
                    scaling,
                    grouping,
                    priorActiveSourcePolicy
                )
                input
            }
            if (attempt.isSuccess) {
                val input = attempt.getOrThrow()
                diagnostics += input.comparisonPlan.degradationDiagnostics
                if (remainingOptional.size < context.candidateCatalog.eligibleCandidates.size) {
                    diagnostics += "automatic candidates were deterministically reduced to a feasible common-row model"
                }
                return StrictBvarPlanningResult.Success(context, input, diagnostics.distinct())
            }

            if (remainingOptional.isEmpty()) {
                val failure = attempt.exceptionOrNull()
                return StrictBvarPlanningResult.Failure(
                    mapPlanningFailure(failure, context),
                    diagnostics + (failure?.message ?: "strict BVAR planning failed")
                )
            }
            val removed = remainingOptional.minWithOrNull(
                compareBy<StrictSeriesKey> { usableCount(context, it) }
                    .thenByDescending { it.stableId }
            ) ?: error("optional candidate list changed unexpectedly")
            remainingOptional.remove(removed)
            diagnostics += "removed auto candidate ${removed.stableId} before posterior sampling: ${attempt.exceptionOrNull()?.message}"
        }
    }

    private fun usableCount(context: PreparedAnalysisContext, feature: StrictSeriesKey): Int =
        context.transformedSeriesByMetric[feature]?.cells.orEmpty().count { cell ->
            cell.state in setOf(StrictCellState.OBSERVED_VALUE, StrictCellState.STRUCTURAL_ZERO) &&
                cell.value?.isFinite() == true
        }

    private fun mapPreparationFailure(failure: StrictPreparationResult.Failure): StrictBvarPlanningFailureCode = when (failure.code) {
        StrictPreparationFailureCode.INCONCLUSIVE_TRANSFORMATION,
        StrictPreparationFailureCode.TRANSFORMATION_ASSESSMENT_CONFLICT ->
            StrictBvarPlanningFailureCode.REPRESENTATION_DIAGNOSTIC_CONFLICT
        StrictPreparationFailureCode.TRANSFORMATION_PLAN_INCOMPLETE,
        StrictPreparationFailureCode.REPRESENTATION_PLAN_INCOMPLETE,
        StrictPreparationFailureCode.RESPONSE_SCALE_PLAN_INCOMPLETE,
        StrictPreparationFailureCode.INSUFFICIENT_CONTIGUOUS_SAMPLE ->
            StrictBvarPlanningFailureCode.REPRESENTATION_POLICY_UNAVAILABLE
        StrictPreparationFailureCode.INVALID_LIFECYCLE_METADATA -> StrictBvarPlanningFailureCode.METADATA_INCOMPLETE
        else -> StrictBvarPlanningFailureCode.PREPARATION_FAILED
    }

    private fun mapPlanningFailure(
        failure: Throwable?,
        context: PreparedAnalysisContext
    ): StrictBvarPlanningFailureCode = when (failure) {
        is ScalingPlanFailureException -> when (failure.code) {
            ScalingFailureCode.NEAR_CONSTANT_TRAINING_SERIES -> when (failure.metric) {
                context.request.xMetric -> StrictBvarPlanningFailureCode.NO_FOCAL_VARIATION
                in context.request.yMetrics -> StrictBvarPlanningFailureCode.NO_TARGET_VARIATION
                else -> StrictBvarPlanningFailureCode.SCALING_UNAVAILABLE
            }
            else -> StrictBvarPlanningFailureCode.SCALING_UNAVAILABLE
        }
        is IllegalArgumentException -> when {
            failure.message.orEmpty().contains("NO_FEASIBLE_COMMON_LAG_PLAN") ->
                StrictBvarPlanningFailureCode.NO_FEASIBLE_COMMON_LAG_PLAN
            failure.message.orEmpty().contains("source", ignoreCase = true) ->
                StrictBvarPlanningFailureCode.SOURCE_IDENTITY_UNAVAILABLE
            else -> StrictBvarPlanningFailureCode.PREPARATION_FAILED
        }
        else -> StrictBvarPlanningFailureCode.PREPARATION_FAILED
    }
}

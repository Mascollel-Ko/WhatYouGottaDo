package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.TrainableQuality
import org.json.JSONArray
import org.json.JSONObject

/**
 * The semantic strategy chosen by the Phase B3 shadow portfolio.  These values describe the
 * shape of a decision only; they never carry a numeric dose or authorize a planner action.
 */
enum class StimulusDoseStrategy {
    HOLD_PERSONAL_BASELINE,
    HOLD_PERSONAL_BASELINE_ALLOW_PROGRESSION,
    RESTORE_PERSONAL_BASELINE,
    INTRODUCE_DIRECT_STIMULUS,
    DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY,
    MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY,
    MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY,
    REDISTRIBUTE_PERSONAL_BASELINE,
    REDISTRIBUTE_DIRECTION_ONLY,
    REDUCE_OR_RESTRUCTURE,
    NO_MINIMUM_TARGET,
    UNRESOLVED
}

enum class StimulusPortfolioComparisonStatus { MATCH, DIFFERENT, UNAVAILABLE }

data class StimulusQualityTrainingDecision(
    val quality: TrainableQuality,
    val relevance: NeedRelevance,
    val needDecision: TrainingNeedDecision,
    val strategy: StimulusDoseStrategy,
    val priority: TargetPriority,
    val needConfidence: PlanningConfidence,
    val baselineConfidence: PlanningConfidence?,
    val baselineAvailable: Boolean,
    val hasPersonalDirectBaseline: Boolean,
    val baselineSource: SuccessfulDoseSource?,
    val baselineEligibleWeekCount: Int,
    val baselineDirectUnitsMedian: Double?,
    val baselineDirectSessionsMedian: Double?,
    val baselineExposureWeekFrequency: Double?,
    val reasonCodes: List<String>,
    val evidence: List<String>
)

data class StimulusTaskTrainingDecision(
    val task: String,
    val relevance: NeedRelevance,
    val needDecision: TrainingNeedDecision,
    val strategy: StimulusDoseStrategy,
    val priority: TargetPriority,
    val needConfidence: PlanningConfidence,
    val numericBaselineAuthority: Boolean = false,
    val reasonCodes: List<String>,
    val evidence: List<String>
)

data class StimulusQualityPortfolioComparison(
    val quality: TrainableQuality,
    val status: StimulusPortfolioComparisonStatus,
    val legacyNeedDecision: TrainingNeedDecision?,
    val canonicalNeedDecision: TrainingNeedDecision?,
    val legacyPriority: TargetPriority?,
    val canonicalPriority: TargetPriority?,
    val legacyAction: TargetStimulusAction?,
    val canonicalStrategy: StimulusDoseStrategy?,
    val reasonCodes: List<String>,
    val legacyBaselineAvailable: Boolean? = null,
    val canonicalBaselineAvailable: Boolean? = null,
    val legacyBaselineSource: SuccessfulDoseSource? = null,
    val canonicalBaselineSource: SuccessfulDoseSource? = null
)

data class StimulusTaskPortfolioComparison(
    val task: String,
    val status: StimulusPortfolioComparisonStatus,
    val legacyNeedDecision: TrainingNeedDecision?,
    val canonicalNeedDecision: TrainingNeedDecision?,
    val legacyPriority: TargetPriority?,
    val canonicalPriority: TargetPriority?,
    val legacyAction: TargetStimulusAction?,
    val canonicalStrategy: StimulusDoseStrategy?,
    val reasonCodes: List<String>
)

data class StimulusPortfolioComparison(
    val qualityComparisons: List<StimulusQualityPortfolioComparison>,
    val taskComparisons: List<StimulusTaskPortfolioComparison>,
    val reasonCodes: List<String> = emptyList()
) {
    val qualityMatchCount: Int get() = qualityComparisons.count { it.status == StimulusPortfolioComparisonStatus.MATCH }
    val qualityDifferenceCount: Int get() = qualityComparisons.count { it.status == StimulusPortfolioComparisonStatus.DIFFERENT }
    val taskMatchCount: Int get() = taskComparisons.count { it.status == StimulusPortfolioComparisonStatus.MATCH }
    val taskDifferenceCount: Int get() = taskComparisons.count { it.status == StimulusPortfolioComparisonStatus.DIFFERENT }
    val unavailableCount: Int get() = (qualityComparisons + taskComparisons).count { it.status == StimulusPortfolioComparisonStatus.UNAVAILABLE }
}

data class StimulusTrainingDecisionPortfolio(
    val qualityDecisions: List<StimulusQualityTrainingDecision>,
    val taskDecisions: List<StimulusTaskTrainingDecision>,
    val unresolved: List<String>,
    val comparison: StimulusPortfolioComparison? = null,
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false,
    val selectionAuthority: Boolean = false,
    val placementAuthority: Boolean = false
)

/** Builds B3 in O(Q+T) from the two already summarized shadow objects. */
internal class StimulusTrainingDecisionPortfolioEngine {
    fun build(
        profile: AthleteStimulusNeedProfile,
        baseline: LedgerBackedQualityDoseHistory
    ): StimulusTrainingDecisionPortfolio {
        val qualities = profile.qualityNeeds.map { need ->
            val band = baseline.bands[need.quality]
            val available = baseline.available
            val hasDirect = available && band?.hasPersonalDirectBaseline == true
            val strategy = qualityStrategy(need.decision, available, hasDirect)
            val reasonCodes = buildList {
                addAll(need.reasonCodes)
                add("NEED_${need.decision.name}")
                add("NEED_DECISION_${need.decision.name}")
                add("BASELINE_STATE_${when {
                    !available -> "UNAVAILABLE"
                    hasDirect -> "PERSONAL_DIRECT"
                    else -> "NO_PERSONAL_DIRECT"
                }}")
                if (!available) {
                    add("PERSONAL_BASELINE_UNAVAILABLE")
                    add("CANONICAL_BASELINE_UNAVAILABLE")
                    add("RESTORE_VS_NOVEL_DOSE_UNRESOLVED")
                } else if (!hasDirect) {
                    add("VALID_COMPLETED_WEEK_HISTORY")
                    add("NO_PERSONAL_DIRECT_BASELINE")
                    add("CANONICAL_PERSONAL_DIRECT_BASELINE_ABSENT")
                    if (need.decision == TrainingNeedDecision.DEVELOP) {
                        add("INTRODUCE_DIRECT_STIMULUS_WITHOUT_NUMERIC_DOSE_AUTHORITY")
                    }
                    if ((band?.supportiveUnitsMedian ?: 0.0) > 0.0) {
                        add("SUPPORTIVE_ONLY_HISTORY_NOT_USED_AS_DIRECT_BASELINE")
                    }
                } else {
                    add("PERSONAL_DIRECT_BASELINE_AVAILABLE")
                    add("CANONICAL_PERSONAL_DIRECT_BASELINE_PRESENT")
                    if (need.decision == TrainingNeedDecision.DEVELOP) add("RESTORE_BEFORE_INVENTING_NEW_DOSE")
                }
                when (need.response) {
                    TrainingResponseState.POSITIVE_RESPONSE -> add("POSITIVE_RESPONSE_SUPPORTS_CONTINUITY")
                    TrainingResponseState.NEGATIVE_RESPONSE -> add("NEGATIVE_RESPONSE_REQUIRES_CAUSE_INSPECTION")
                    TrainingResponseState.INSUFFICIENT_EVIDENCE -> add("INSUFFICIENT_RESPONSE_EVIDENCE")
                    TrainingResponseState.STABLE_RESPONSE -> Unit
                }
                if (need.decision == TrainingNeedDecision.MAINTAIN_OR_PROGRESS) {
                    add("PROGRESSION_DOES_NOT_REQUIRE_AUTOMATIC_VOLUME_INCREASE")
                }
                if (need.decision == TrainingNeedDecision.REDUCE) {
                    add("NEGATIVE_RESPONSE_REQUIRES_CAUSE_INSPECTION")
                    add("NO_AUTOMATIC_REDUCTION_AUTHORITY")
                }
                if (profile.executionModifiers.isNotEmpty()) add("CURRENT_EXECUTION_CONSTRAINT_PRESENT")
                if (baseline.available && band == null) add("CANONICAL_BASELINE_BAND_UNAVAILABLE")
                if (band?.confidence == PlanningConfidence.LOW) add("LOW_BASELINE_CONFIDENCE")
            }.distinct()
            val evidence = buildList {
                add("needConfidence=${need.confidence.name}")
                add("baselineAvailable=$available")
                add("hasPersonalDirectBaseline=$hasDirect")
                band?.source?.let { add("baselineSource=${it.name}") }
                band?.directUnitsMedian?.let { add("baselineDirectUnitsMedian=$it") }
                band?.directSessionsMedian?.let { add("baselineDirectSessionsMedian=$it") }
                band?.directExposureWeekFrequency?.let { add("baselineExposureWeekFrequency=$it") }
                add("baselineEligibleWeekCount=${band?.eligibleWeekCount ?: 0}")
            }
            StimulusQualityTrainingDecision(
                quality = need.quality,
                relevance = need.relevance,
                needDecision = need.decision,
                strategy = strategy,
                priority = priority(need.relevance, need.decision),
                needConfidence = need.confidence,
                baselineConfidence = band?.confidence?.takeIf { available },
                baselineAvailable = available,
                hasPersonalDirectBaseline = hasDirect,
                baselineSource = band?.source?.takeIf { available },
                baselineEligibleWeekCount = band?.eligibleWeekCount ?: 0,
                baselineDirectUnitsMedian = band?.directUnitsMedian,
                baselineDirectSessionsMedian = band?.directSessionsMedian,
                baselineExposureWeekFrequency = band?.directExposureWeekFrequency,
                reasonCodes = reasonCodes,
                evidence = evidence
            )
        }
        val tasks = profile.sportTaskNeeds.map { need ->
            val strategy = taskStrategy(need.decision)
            val reasons = buildList {
                addAll(need.reasonCodes)
                add("NO_LEDGER_BACKED_COMPLETED_WEEK_TASK_BASELINE_IN_B3")
                add("TASK_REMAINS_DIRECTION_ONLY")
                add("NO_EXPLICIT_BADMINTON_TASK_PRIORITY")
                if (profile.executionModifiers.isNotEmpty()) add("CURRENT_EXECUTION_CONSTRAINT_PRESENT")
            }.distinct()
            StimulusTaskTrainingDecision(
                task = need.task,
                relevance = need.relevance,
                needDecision = need.decision,
                strategy = strategy,
                priority = priority(need.relevance, need.decision),
                needConfidence = need.confidence,
                reasonCodes = reasons,
                evidence = need.evidence + "numericBaselineAuthority=false"
            )
        }
        return StimulusTrainingDecisionPortfolio(
            qualityDecisions = qualities,
            taskDecisions = tasks,
            unresolved = (qualities.filter { it.strategy == StimulusDoseStrategy.UNRESOLVED }
                .map { "QUALITY_${it.quality.name}_UNRESOLVED" } +
                tasks.filter { it.strategy == StimulusDoseStrategy.UNRESOLVED }
                    .map { "TASK_${it.task}_UNRESOLVED" }).distinct()
        )
    }

    private fun qualityStrategy(
        decision: TrainingNeedDecision,
        baselineAvailable: Boolean,
        hasDirectBaseline: Boolean
    ): StimulusDoseStrategy = when (decision) {
        TrainingNeedDecision.UNKNOWN, TrainingNeedDecision.PROGRESS -> StimulusDoseStrategy.UNRESOLVED
        TrainingNeedDecision.NO_EXTRA_NEED -> StimulusDoseStrategy.NO_MINIMUM_TARGET
        TrainingNeedDecision.DEVELOP -> when {
            !baselineAvailable -> StimulusDoseStrategy.DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY
            hasDirectBaseline -> StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE
            else -> StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS
        }
        TrainingNeedDecision.MAINTAIN -> if (hasDirectBaseline) StimulusDoseStrategy.HOLD_PERSONAL_BASELINE
        else StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY
        TrainingNeedDecision.MAINTAIN_OR_PROGRESS -> if (hasDirectBaseline) StimulusDoseStrategy.HOLD_PERSONAL_BASELINE_ALLOW_PROGRESSION
        else StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY
        TrainingNeedDecision.REDISTRIBUTE -> if (hasDirectBaseline) StimulusDoseStrategy.REDISTRIBUTE_PERSONAL_BASELINE
        else StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY
        TrainingNeedDecision.REDUCE -> StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE
    }

    private fun taskStrategy(decision: TrainingNeedDecision): StimulusDoseStrategy = when (decision) {
        TrainingNeedDecision.UNKNOWN, TrainingNeedDecision.PROGRESS -> StimulusDoseStrategy.UNRESOLVED
        TrainingNeedDecision.NO_EXTRA_NEED -> StimulusDoseStrategy.NO_MINIMUM_TARGET
        TrainingNeedDecision.DEVELOP -> StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS
        TrainingNeedDecision.MAINTAIN -> StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY
        TrainingNeedDecision.MAINTAIN_OR_PROGRESS -> StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY
        TrainingNeedDecision.REDISTRIBUTE -> StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY
        TrainingNeedDecision.REDUCE -> StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE
    }
}

/** Observation-only comparison; it never selects a winner or changes either portfolio. */
internal class StimulusTrainingDecisionPortfolioComparisonEngine {
    fun compare(
        legacy: TrainingDecisionPortfolio,
        canonical: StimulusTrainingDecisionPortfolio,
        baseline: LedgerBackedQualityDoseHistory,
        legacyHistory: QualityDoseHistory? = null
    ): StimulusPortfolioComparison {
        val quality = canonical.qualityDecisions.map { current ->
            val old = legacy.qualityDecisions.firstOrNull { it.quality == current.quality }
            compareQuality(old, current, baseline.comparisons[current.quality], legacyHistory?.bands?.get(current.quality))
        }
        val tasks = canonical.taskDecisions.map { current ->
            val old = legacy.taskDecisions.firstOrNull { it.task == current.task }
            compareTask(old, current)
        }
        return StimulusPortfolioComparison(quality, tasks,
            buildList {
                if (baseline.available) add("CANONICAL_LEDGER_BACKED_INPUT_AVAILABLE")
                else add("CANONICAL_INPUT_UNAVAILABLE")
            })
    }

    private fun compareQuality(
        old: QualityTrainingDecision?,
        current: StimulusQualityTrainingDecision,
        b2Comparison: QualityDoseHistoryShadowComparison?,
        legacyBand: SuccessfulDoseBand?
    ): StimulusQualityPortfolioComparison {
        if (old == null) return StimulusQualityPortfolioComparison(
            current.quality, StimulusPortfolioComparisonStatus.UNAVAILABLE, null, current.needDecision,
            null, current.priority, null, current.strategy, listOf("CANONICAL_INPUT_UNAVAILABLE"),
            legacyBand?.hasPersonalDirectBaseline, current.hasPersonalDirectBaseline,
            legacyBand?.source, current.baselineSource
        )
        val reasons = linkedSetOf<String>()
        val needMatches = old.needDecision == current.needDecision
        if (needMatches) reasons += "NEED_DECISION_MATCH" else reasons += "NEED_DECISION_CHANGED_BY_LEDGER_SEMANTICS"
        val priorityMatches = old.priority == current.priority
        if (priorityMatches) reasons += "PRIORITY_MATCH"
        else if (!needMatches) reasons += "PRIORITY_CHANGED_WITH_CANONICAL_NEED"
        else reasons += "PRIORITY_DIFFERENCE"
        val oldSemantic = qualitySemantic(old.action)
        val baselineKnown = legacyBand != null
        val baselineMatches = if (baselineKnown) {
            legacyBand?.hasPersonalDirectBaseline == current.hasPersonalDirectBaseline &&
                legacyBand?.source == current.baselineSource
        } else oldSemantic == current.strategy
        if (oldSemantic == current.strategy) {
            reasons += "ACTION_SEMANTICS_MATCH"
            if (baselineMatches) reasons += "LEGACY_AND_CANONICAL_BASELINE_MATCH"
            else reasons += "CANONICAL_BASELINE_DIFFERS_FROM_LEGACY"
        } else if (!needMatches) {
            reasons += "ACTION_CHANGED_BY_CANONICAL_NEED"
            reasons += "CANONICAL_BASELINE_DIFFERS_FROM_LEGACY"
        } else {
            reasons += "ACTION_CHANGED_BY_CANONICAL_BASELINE"
            reasons += "CANONICAL_BASELINE_DIFFERS_FROM_LEGACY"
        }
        when {
            !current.baselineAvailable -> reasons += "CANONICAL_BASELINE_UNAVAILABLE"
            current.hasPersonalDirectBaseline -> reasons += "CANONICAL_PERSONAL_DIRECT_BASELINE_PRESENT"
            else -> reasons += "CANONICAL_PERSONAL_DIRECT_BASELINE_ABSENT"
        }
        b2Comparison?.reasonCodes?.let(reasons::addAll)
        if (current.reasonCodes.contains("SUPPORTIVE_ONLY_HISTORY_NOT_USED_AS_DIRECT_BASELINE")) {
            reasons += "SUPPORTIVE_ONLY_HISTORY_NOT_USED_AS_DIRECT_BASELINE"
        }
        val status = if (needMatches && priorityMatches && oldSemantic == current.strategy && baselineMatches)
            StimulusPortfolioComparisonStatus.MATCH else StimulusPortfolioComparisonStatus.DIFFERENT
        return StimulusQualityPortfolioComparison(current.quality, status, old.needDecision, current.needDecision,
            old.priority, current.priority, old.action, current.strategy, reasons.toList(),
            legacyBand?.hasPersonalDirectBaseline, current.hasPersonalDirectBaseline,
            legacyBand?.source, current.baselineSource)
    }

    private fun compareTask(old: TaskTrainingDecision?, current: StimulusTaskTrainingDecision): StimulusTaskPortfolioComparison {
        if (old == null) return StimulusTaskPortfolioComparison(
            current.task, StimulusPortfolioComparisonStatus.UNAVAILABLE, null, current.needDecision,
            null, current.priority, null, current.strategy, listOf("CANONICAL_INPUT_UNAVAILABLE")
        )
        val reasons = linkedSetOf<String>("NO_LEDGER_BACKED_COMPLETED_WEEK_TASK_BASELINE_IN_B3", "TASK_REMAINS_DIRECTION_ONLY")
        val needMatches = old.needDecision == current.needDecision
        if (needMatches) reasons += "NEED_DECISION_MATCH" else reasons += "NEED_DECISION_CHANGED_BY_LEDGER_SEMANTICS"
        val priorityMatches = old.priority == current.priority
        if (priorityMatches) reasons += "PRIORITY_MATCH"
        else if (!needMatches) reasons += "PRIORITY_CHANGED_WITH_CANONICAL_NEED"
        val oldSemantic = taskSemantic(old.action)
        if (oldSemantic == current.strategy) reasons += "ACTION_SEMANTICS_MATCH"
        else reasons += "ACTION_CHANGED_BY_CANONICAL_BASELINE"
        val status = if (needMatches && priorityMatches && oldSemantic == current.strategy)
            StimulusPortfolioComparisonStatus.MATCH else StimulusPortfolioComparisonStatus.DIFFERENT
        return StimulusTaskPortfolioComparison(current.task, status, old.needDecision, current.needDecision,
            old.priority, current.priority, old.action, current.strategy, reasons.toList())
    }

    private fun qualitySemantic(action: TargetStimulusAction): StimulusDoseStrategy = when (action) {
        TargetStimulusAction.HOLD_SUCCESSFUL_DOSE -> StimulusDoseStrategy.HOLD_PERSONAL_BASELINE
        TargetStimulusAction.HOLD_DOSE_ALLOW_PROGRESSION -> StimulusDoseStrategy.HOLD_PERSONAL_BASELINE_ALLOW_PROGRESSION
        TargetStimulusAction.RESTORE_PERSONAL_BASELINE -> StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE
        TargetStimulusAction.INTRODUCE_DIRECT_STIMULUS -> StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS
        TargetStimulusAction.REDISTRIBUTE_EXISTING_DOSE -> StimulusDoseStrategy.REDISTRIBUTE_PERSONAL_BASELINE
        TargetStimulusAction.REDUCE_OR_RESTRUCTURE -> StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE
        TargetStimulusAction.NO_MINIMUM_TARGET -> StimulusDoseStrategy.NO_MINIMUM_TARGET
        TargetStimulusAction.UNRESOLVED -> StimulusDoseStrategy.UNRESOLVED
    }

    private fun taskSemantic(action: TargetStimulusAction): StimulusDoseStrategy = when (action) {
        TargetStimulusAction.HOLD_SUCCESSFUL_DOSE -> StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY
        TargetStimulusAction.HOLD_DOSE_ALLOW_PROGRESSION -> StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY
        TargetStimulusAction.RESTORE_PERSONAL_BASELINE, TargetStimulusAction.INTRODUCE_DIRECT_STIMULUS -> StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS
        TargetStimulusAction.REDISTRIBUTE_EXISTING_DOSE -> StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY
        TargetStimulusAction.REDUCE_OR_RESTRUCTURE -> StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE
        TargetStimulusAction.NO_MINIMUM_TARGET -> StimulusDoseStrategy.NO_MINIMUM_TARGET
        TargetStimulusAction.UNRESOLVED -> StimulusDoseStrategy.UNRESOLVED
    }
}

private fun priority(relevance: NeedRelevance, decision: TrainingNeedDecision): TargetPriority = when (relevance) {
    NeedRelevance.HIGH -> if (decision in setOf(TrainingNeedDecision.DEVELOP, TrainingNeedDecision.PROGRESS)) TargetPriority.PRIMARY else TargetPriority.MAINTENANCE
    NeedRelevance.MODERATE -> if (decision == TrainingNeedDecision.DEVELOP) TargetPriority.SECONDARY else TargetPriority.MAINTENANCE
    NeedRelevance.LOW -> TargetPriority.BACKGROUND
    NeedRelevance.NONE -> TargetPriority.NONE
    NeedRelevance.UNKNOWN -> TargetPriority.UNRESOLVED
}

internal fun StimulusTrainingDecisionPortfolio.toCompactJson(): JSONObject = JSONObject()
    .put("shadowOnly", shadowOnly)
    .put("prescriptionAuthority", prescriptionAuthority)
    .put("selectionAuthority", selectionAuthority)
    .put("placementAuthority", placementAuthority)
    .put("unresolved", JSONArray(unresolved))
    .put("qualityDecisions", JSONArray(qualityDecisions.map { decision -> JSONObject()
        .put("quality", decision.quality.name).put("relevance", decision.relevance.name)
        .put("needDecision", decision.needDecision.name).put("strategy", decision.strategy.name)
        .put("priority", decision.priority.name).put("needConfidence", decision.needConfidence.name)
        .put("baselineConfidence", decision.baselineConfidence?.name)
        .put("baselineAvailable", decision.baselineAvailable)
        .put("hasPersonalDirectBaseline", decision.hasPersonalDirectBaseline)
        .put("baselineSource", decision.baselineSource?.name)
        .put("baselineEligibleWeekCount", decision.baselineEligibleWeekCount)
        .put("baselineDirectUnitsMedian", decision.baselineDirectUnitsMedian)
        .put("baselineDirectSessionsMedian", decision.baselineDirectSessionsMedian)
        .put("baselineExposureWeekFrequency", decision.baselineExposureWeekFrequency)
        .put("reasonCodes", JSONArray(decision.reasonCodes)).put("evidence", JSONArray(decision.evidence))
    }))
    .put("taskDecisions", JSONArray(taskDecisions.map { decision -> JSONObject()
        .put("task", decision.task).put("relevance", decision.relevance.name)
        .put("needDecision", decision.needDecision.name).put("strategy", decision.strategy.name)
        .put("priority", decision.priority.name).put("needConfidence", decision.needConfidence.name)
        .put("numericBaselineAuthority", decision.numericBaselineAuthority)
        .put("reasonCodes", JSONArray(decision.reasonCodes)).put("evidence", JSONArray(decision.evidence))
    }))
    .put("comparison", comparison?.toCompactJson())

private fun StimulusPortfolioComparison.toCompactJson(): JSONObject = JSONObject()
    .put("qualityMatchCount", qualityMatchCount).put("qualityDifferenceCount", qualityDifferenceCount)
    .put("taskMatchCount", taskMatchCount).put("taskDifferenceCount", taskDifferenceCount)
    .put("unavailableCount", unavailableCount).put("reasonCodes", JSONArray(reasonCodes))
    .put("qualityComparisons", JSONArray(qualityComparisons.map { comparison -> JSONObject()
        .put("quality", comparison.quality.name).put("status", comparison.status.name)
        .put("legacyNeedDecision", comparison.legacyNeedDecision?.name)
        .put("canonicalNeedDecision", comparison.canonicalNeedDecision?.name)
        .put("legacyPriority", comparison.legacyPriority?.name).put("canonicalPriority", comparison.canonicalPriority?.name)
        .put("legacyAction", comparison.legacyAction?.name).put("canonicalStrategy", comparison.canonicalStrategy?.name)
        .put("legacyBaselineAvailable", comparison.legacyBaselineAvailable)
        .put("canonicalBaselineAvailable", comparison.canonicalBaselineAvailable)
        .put("legacyBaselineSource", comparison.legacyBaselineSource?.name)
        .put("canonicalBaselineSource", comparison.canonicalBaselineSource?.name)
        .put("reasonCodes", JSONArray(comparison.reasonCodes))
    }))
    .put("taskComparisons", JSONArray(taskComparisons.map { comparison -> JSONObject()
        .put("task", comparison.task).put("status", comparison.status.name)
        .put("legacyNeedDecision", comparison.legacyNeedDecision?.name)
        .put("canonicalNeedDecision", comparison.canonicalNeedDecision?.name)
        .put("legacyPriority", comparison.legacyPriority?.name).put("canonicalPriority", comparison.canonicalPriority?.name)
        .put("legacyAction", comparison.legacyAction?.name).put("canonicalStrategy", comparison.canonicalStrategy?.name)
        .put("reasonCodes", JSONArray(comparison.reasonCodes))
    }))

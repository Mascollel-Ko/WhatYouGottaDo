package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.TrainableQuality
import org.json.JSONArray
import org.json.JSONObject

/** Numeric authority owned by the canonical B4 shadow layer. */
enum class StimulusTargetNumericAuthority {
    PERSONAL_SUCCESSFUL_DOSE,
    PERSONAL_RESTORE_BASELINE,
    DIRECTION_ONLY,
    NONE,
    UNRESOLVED
}

/** A validated, non-materialized personal exposure envelope. */
data class StimulusTargetRange(
    val min: Double,
    val preferred: Double,
    val max: Double
) {
    init {
        require(min.isFinite() && preferred.isFinite() && max.isFinite())
        require(min >= 0.0 && preferred >= 0.0 && max >= 0.0)
        require(min <= preferred && preferred <= max)
    }
}

data class StimulusQualityTarget(
    val quality: TrainableQuality,
    val strategy: StimulusDoseStrategy,
    val priority: TargetPriority,
    val numericAuthority: StimulusTargetNumericAuthority,
    val baselineSource: SuccessfulDoseSource?,
    val baselineConfidence: PlanningConfidence?,
    val weeklyDirectUnitsTarget: StimulusTargetRange?,
    val weeklyDirectSessionsTarget: StimulusTargetRange?,
    val exposureWeekDirectUnitsReference: StimulusTargetRange?,
    val exposureWeekDirectSessionsReference: StimulusTargetRange?,
    val exposureWeekFrequencyReference: Double?,
    val reasonCodes: List<String>,
    val evidence: List<String>,
    val baselineAvailable: Boolean = false,
    val hasPersonalDirectBaseline: Boolean = false,
    val numericBaselineUsable: Boolean = false,
    val evidenceBasis: StimulusEvidenceBasis = evidenceBasisForQuality(quality),
    val prescriptionRealizationAuthority: Boolean = evidenceBasis == StimulusEvidenceBasis.REALIZED_PRESCRIPTION_CLASSIFIED
)

data class StimulusTaskTarget(
    val task: String,
    val strategy: StimulusDoseStrategy,
    val priority: TargetPriority,
    val numericAuthority: StimulusTargetNumericAuthority,
    val reasonCodes: List<String>,
    val evidence: List<String>,
    val weeklyDirectUnitsTarget: StimulusTargetRange? = null,
    val weeklyDirectSessionsTarget: StimulusTargetRange? = null,
    val evidenceBasis: StimulusEvidenceBasis = StimulusEvidenceBasis.CANONICAL_TASK_RELATION,
    val prescriptionRealizationAuthority: Boolean = false
)

enum class StimulusTargetComparisonStatus { MATCH, DIFFERENT, UNAVAILABLE }

data class StimulusQualityTargetComparison(
    val quality: TrainableQuality,
    val status: StimulusTargetComparisonStatus,
    val legacyAction: TargetStimulusAction?,
    val canonicalStrategy: StimulusDoseStrategy?,
    val legacyNumericAuthority: TargetNumericAuthority?,
    val canonicalNumericAuthority: StimulusTargetNumericAuthority?,
    val reasonCodes: List<String>
)

data class StimulusTaskTargetComparison(
    val task: String,
    val status: StimulusTargetComparisonStatus,
    val legacyAction: TargetStimulusAction?,
    val canonicalStrategy: StimulusDoseStrategy?,
    val legacyNumericAuthority: TargetNumericAuthority?,
    val canonicalNumericAuthority: StimulusTargetNumericAuthority?,
    val reasonCodes: List<String>
)

data class StimulusTargetPlanLegacyComparison(
    val qualityComparisons: List<StimulusQualityTargetComparison>,
    val taskComparisons: List<StimulusTaskTargetComparison>,
    val reasonCodes: List<String> = emptyList()
) {
    val qualityMatchCount: Int get() = qualityComparisons.count { it.status == StimulusTargetComparisonStatus.MATCH }
    val qualityDifferenceCount: Int get() = qualityComparisons.count { it.status == StimulusTargetComparisonStatus.DIFFERENT }
    val taskMatchCount: Int get() = taskComparisons.count { it.status == StimulusTargetComparisonStatus.MATCH }
    val taskDifferenceCount: Int get() = taskComparisons.count { it.status == StimulusTargetComparisonStatus.DIFFERENT }
    val unavailableCount: Int get() = qualityComparisons.count { it.status == StimulusTargetComparisonStatus.UNAVAILABLE } +
        taskComparisons.count { it.status == StimulusTargetComparisonStatus.UNAVAILABLE }
}

enum class StimulusTargetControlStatus {
    BELOW_BAND,
    WITHIN_BAND,
    ABOVE_BAND,
    DIRECT_PRESENT,
    DIRECT_ABSENT,
    NO_MINIMUM,
    UNRESOLVED,
    DISTRIBUTION_COMPARISON_DEFERRED
}

data class StimulusQualityControlProgramAudit(
    val quality: TrainableQuality,
    val strategy: StimulusDoseStrategy,
    val numericAuthority: StimulusTargetNumericAuthority,
    val targetWeeklyDirectUnits: StimulusTargetRange?,
    val plannedWeeklyDirectUnits: Double?,
    val weeklyDirectUnitsStatus: StimulusTargetControlStatus,
    val targetWeeklyDirectSessions: StimulusTargetRange?,
    val plannedWeeklyDirectSessions: Double?,
    val weeklyDirectSessionsStatus: StimulusTargetControlStatus,
    val exposureWeekFrequencyReference: Double?,
    val plannedExposureWeekFrequency: Double?,
    val exposureWeekFrequencyDelta: Double?,
    val reasonCodes: List<String>,
    val evidenceBasis: StimulusEvidenceBasis = StimulusEvidenceBasis.UNCLASSIFIED,
    val prescriptionRealizationAuthority: Boolean = false
) {
    val unitStatus: StimulusTargetControlStatus get() = weeklyDirectUnitsStatus
    val sessionStatus: StimulusTargetControlStatus get() = weeklyDirectSessionsStatus
}

data class StimulusTaskControlProgramAudit(
    val task: String,
    val strategy: StimulusDoseStrategy,
    val numericAuthority: StimulusTargetNumericAuthority,
    val plannedDirectUnits: Double?,
    val status: StimulusTargetControlStatus,
    val reasonCodes: List<String>,
    val evidenceBasis: StimulusEvidenceBasis = StimulusEvidenceBasis.CANONICAL_TASK_RELATION,
    val prescriptionRealizationAuthority: Boolean = false
)

data class StimulusTargetControlProgramAudit(
    val planningHorizonWeeks: Int,
    val qualityAudits: List<StimulusQualityControlProgramAudit>,
    val taskAudits: List<StimulusTaskControlProgramAudit>,
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false,
    val selectionAuthority: Boolean = false,
    val placementAuthority: Boolean = false,
    val schedulingAuthority: Boolean = false
)

data class StimulusTargetPlan(
    val qualityTargets: List<StimulusQualityTarget>,
    val taskTargets: List<StimulusTaskTarget>,
    val unresolved: List<String>,
    val legacyComparison: StimulusTargetPlanLegacyComparison? = null,
    val controlProgramAudit: StimulusTargetControlProgramAudit? = null,
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false,
    val selectionAuthority: Boolean = false,
    val placementAuthority: Boolean = false,
    val schedulingAuthority: Boolean = false
)

/** Converts B3 decisions and B2 bands into an observation-only canonical target envelope. */
class StimulusTargetPlanEngine {
    fun build(
        portfolio: StimulusTrainingDecisionPortfolio,
        baseline: LedgerBackedQualityDoseHistory
    ): StimulusTargetPlan {
        val qualityTargets = portfolio.qualityDecisions.map { decision -> buildQualityTarget(decision, baseline) }
        val taskTargets = portfolio.taskDecisions.map { decision -> buildTaskTarget(decision) }
        return StimulusTargetPlan(
            qualityTargets = qualityTargets,
            taskTargets = taskTargets,
            unresolved = (portfolio.unresolved + qualityTargets.filter {
                it.numericAuthority == StimulusTargetNumericAuthority.UNRESOLVED
            }.map { "QUALITY_${it.quality.name}_UNRESOLVED" } + taskTargets.filter {
                it.numericAuthority == StimulusTargetNumericAuthority.UNRESOLVED
            }.map { "TASK_${it.task}_UNRESOLVED" }).distinct()
        )
    }

    private fun buildQualityTarget(
        decision: StimulusQualityTrainingDecision,
        baseline: LedgerBackedQualityDoseHistory
    ): StimulusQualityTarget {
        val band = baseline.bands[decision.quality]
        // B4 consumes B3's authority decision, while retaining a defensive check against
        // stale/inconsistent hand-built portfolios and an unavailable backing ledger.
        val baselineUsable = decision.numericBaselineUsable &&
            baseline.available && band?.hasPersonalDirectBaseline == true
        val weeklyUnits = band?.weeklyUnitsRange()
        val weeklySessions = band?.weeklySessionsRange()
        val weeklyValid = baselineUsable && weeklyUnits != null && weeklySessions != null
        val authority = numericAuthority(
            decision.strategy,
            decision.baselineAvailable && baseline.available,
            baselineUsable,
            weeklyValid
        )
        val numeric = authority == StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE ||
            authority == StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE
        val exposureUnits = band?.exposureUnitsRangeOrNull()
        val exposureSessions = band?.exposureSessionsRangeOrNull()
        val frequency = band?.directExposureWeekFrequency?.takeIf { it.isFinite() && it >= 0.0 }
        val reasons = linkedSetOf<String>().apply {
            addAll(decision.reasonCodes)
            baseline.comparisons[decision.quality]?.reasonCodes?.let { addAll(it) }
            add("B4_TARGET_IS_OBSERVED_PERSONAL_EXPOSURE_NOT_OPTIMALITY")
            add("QUALITY_TARGET_ENVELOPES_ARE_NON_ADDITIVE")
            when (authority) {
                StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE -> add("PERSONAL_SUCCESSFUL_DOSE_BAND")
                StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE -> {
                    add("PERSONAL_RESTORE_BASELINE")
                    add("RESTORE_BEFORE_INVENTING_NEW_DOSE")
                }
                StimulusTargetNumericAuthority.DIRECTION_ONLY -> {
                    if (decision.strategy in PERSONAL_BASELINE_STRATEGIES) {
                        add("B3_B2_BASELINE_CONTRACT_MISMATCH")
                        add("PERSONAL_BASELINE_NUMERIC_AUTHORITY_UNAVAILABLE")
                    }
                    addAll(directionReasons(decision.strategy, baselineUsable, weeklyValid))
                }
                StimulusTargetNumericAuthority.NONE -> {
                    add("NO_SEPARATE_DEVELOPMENT_FLOOR")
                    add("INCIDENTAL_STIMULUS_ALLOWED")
                }
                StimulusTargetNumericAuthority.UNRESOLVED -> {
                    add("CANONICAL_PERSONAL_BASELINE_NOT_AVAILABLE")
                    add("TARGET_DECISION_UNRESOLVED")
                }
            }
            if (decision.strategy == StimulusDoseStrategy.HOLD_PERSONAL_BASELINE_ALLOW_PROGRESSION) {
                add("PROGRESSION_DOES_NOT_REQUIRE_AUTOMATIC_VOLUME_INCREASE")
            }
            if (decision.strategy == StimulusDoseStrategy.REDISTRIBUTE_PERSONAL_BASELINE) {
                add("DISTRIBUTION_CHANGE_REQUIRED")
                add("DISTRIBUTION_AUTHORITY_DEFERRED")
            }
            if (numeric && (exposureUnits == null || exposureSessions == null || frequency == null)) {
                add("EXPOSURE_WEEK_REFERENCE_NOT_AVAILABLE")
                add("FINAL_PROGRAM_EXPOSURE_WEEK_DISTRIBUTION_NOT_AVAILABLE_IN_B4")
            } else if (numeric) {
                add("EXPOSURE_WEEK_DISTRIBUTION_REFERENCE_ONLY_IN_B4")
            }
            if (band != null && baselineUsable && !weeklyValid) {
                add("MALFORMED_OR_INCOMPLETE_PERSONAL_BASELINE")
                add("PERSONAL_BASELINE_NUMERIC_AUTHORITY_UNAVAILABLE")
            }
            if (decision.evidenceBasis == StimulusEvidenceBasis.CANONICAL_CAPABILITY_PROXY) {
                add("CAPABILITY_PROXY_TARGET_NOT_REALIZED_PRESCRIPTION_AUTHORITY")
            }
        }
        return StimulusQualityTarget(
            quality = decision.quality,
            strategy = decision.strategy,
            priority = decision.priority,
            numericAuthority = authority,
            baselineSource = decision.baselineSource,
            baselineConfidence = decision.baselineConfidence,
            weeklyDirectUnitsTarget = weeklyUnits.takeIf { numeric },
            weeklyDirectSessionsTarget = weeklySessions.takeIf { numeric },
            exposureWeekDirectUnitsReference = exposureUnits.takeIf { numeric },
            exposureWeekDirectSessionsReference = exposureSessions.takeIf { numeric },
            exposureWeekFrequencyReference = frequency.takeIf { numeric },
            reasonCodes = reasons.toList(),
            evidence = decision.evidence + listOf(
                "b4NumericAuthority=${authority.name}",
                "b4WeeklyTargetNumericAuthority=${authority.name}",
                "b4WeeklyTargetShadowOnly=true",
                "b4ExposureWeekDistributionReferenceOnly=${numeric && (exposureUnits != null || exposureSessions != null || frequency != null)}",
                "b4QualityTargetsAreNonAdditive=true"
            ),
            baselineAvailable = decision.baselineAvailable,
            hasPersonalDirectBaseline = decision.observedPersonalDirectBaseline,
            numericBaselineUsable = decision.numericBaselineUsable,
            evidenceBasis = decision.evidenceBasis,
            prescriptionRealizationAuthority = decision.evidenceBasis == StimulusEvidenceBasis.REALIZED_PRESCRIPTION_CLASSIFIED
        )
    }

    private fun buildTaskTarget(decision: StimulusTaskTrainingDecision): StimulusTaskTarget {
        val authority = when (decision.strategy) {
            StimulusDoseStrategy.NO_MINIMUM_TARGET -> StimulusTargetNumericAuthority.NONE
            StimulusDoseStrategy.UNRESOLVED -> StimulusTargetNumericAuthority.UNRESOLVED
            else -> StimulusTargetNumericAuthority.DIRECTION_ONLY
        }
        val reasons = linkedSetOf<String>().apply {
            addAll(decision.reasonCodes)
            when (authority) {
                StimulusTargetNumericAuthority.DIRECTION_ONLY -> {
                    add("CANONICAL_TASK_REMAINS_DIRECTION_ONLY")
                    add("TASK_NUMERIC_BASELINE_NOT_ADOPTED_IN_B4")
                }
                StimulusTargetNumericAuthority.NONE -> add("INCIDENTAL_STIMULUS_ALLOWED")
                StimulusTargetNumericAuthority.UNRESOLVED -> add("TARGET_DECISION_UNRESOLVED")
                else -> Unit
            }
        }
        return StimulusTaskTarget(
            task = decision.task,
            strategy = decision.strategy,
            priority = decision.priority,
            numericAuthority = authority,
            reasonCodes = reasons.toList(),
            evidence = decision.evidence + "b4NumericAuthority=${authority.name}",
            evidenceBasis = decision.evidenceBasis,
            prescriptionRealizationAuthority = false
        )
    }

    private fun numericAuthority(
        strategy: StimulusDoseStrategy,
        baselineAvailable: Boolean,
        baselineUsable: Boolean,
        weeklyValid: Boolean
    ): StimulusTargetNumericAuthority = when (strategy) {
        StimulusDoseStrategy.NO_MINIMUM_TARGET -> StimulusTargetNumericAuthority.NONE
        StimulusDoseStrategy.UNRESOLVED -> StimulusTargetNumericAuthority.UNRESOLVED
        StimulusDoseStrategy.HOLD_PERSONAL_BASELINE -> personalAuthority(
            strategy, baselineAvailable, baselineUsable, weeklyValid, StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE
        )
        StimulusDoseStrategy.HOLD_PERSONAL_BASELINE_ALLOW_PROGRESSION -> personalAuthority(
            strategy, baselineAvailable, baselineUsable, weeklyValid, StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE
        )
        StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE -> personalAuthority(
            strategy, baselineAvailable, baselineUsable, weeklyValid, StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE
        )
        StimulusDoseStrategy.REDISTRIBUTE_PERSONAL_BASELINE -> personalAuthority(
            strategy, baselineAvailable, baselineUsable, weeklyValid, StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE
        )
        StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
        StimulusDoseStrategy.DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY,
        StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY,
        StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY,
        StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY,
        StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE -> StimulusTargetNumericAuthority.DIRECTION_ONLY
    }

    private fun personalAuthority(
        strategy: StimulusDoseStrategy,
        baselineAvailable: Boolean,
        baselineUsable: Boolean,
        weeklyValid: Boolean,
        authority: StimulusTargetNumericAuthority
    ): StimulusTargetNumericAuthority = when {
        !baselineAvailable -> StimulusTargetNumericAuthority.UNRESOLVED
        !baselineUsable || !weeklyValid -> StimulusTargetNumericAuthority.DIRECTION_ONLY
        else -> authority
    }

    private fun directionReasons(strategy: StimulusDoseStrategy, baselineUsable: Boolean, weeklyValid: Boolean): List<String> = buildList {
        when (strategy) {
            StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS -> {
                add("NO_PERSONAL_DIRECT_BASELINE")
                add("NOVEL_DIRECT_STIMULUS_NUMERIC_DOSE_NOT_AUTHORIZED")
            }
            StimulusDoseStrategy.DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY -> add("PERSONAL_BASELINE_UNAVAILABLE")
            StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY,
            StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY -> add("DIRECT_PRESENCE_ONLY")
            StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY -> add("DISTRIBUTION_AUTHORITY_DEFERRED")
            StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE -> {
                add("NEGATIVE_RESPONSE_PRESENT")
                add("CAUSE_NOT_IDENTIFIED")
                add("AUTOMATIC_NUMERIC_REDUCTION_NOT_AUTHORIZED")
            }
            else -> if (!baselineUsable || !weeklyValid) add("NO_PERSONAL_DIRECT_BASELINE")
        }
    }

    private fun SuccessfulDoseBand.weeklyUnitsRange(): StimulusTargetRange? =
        range(weeklyDirectUnitsQ25, weeklyDirectUnitsMedian, weeklyDirectUnitsQ75)

    private fun SuccessfulDoseBand.weeklySessionsRange(): StimulusTargetRange? =
        range(weeklyDirectSessionsQ25, weeklyDirectSessionsMedian, weeklyDirectSessionsQ75)

    private fun SuccessfulDoseBand.exposureUnitsRangeOrNull(): StimulusTargetRange? =
        range(exposureWeekDirectUnitsQ25, exposureWeekDirectUnitsMedian, exposureWeekDirectUnitsQ75)

    private fun SuccessfulDoseBand.exposureSessionsRangeOrNull(): StimulusTargetRange? =
        range(exposureWeekDirectSessionsQ25, exposureWeekDirectSessionsMedian, exposureWeekDirectSessionsQ75)

    private fun range(min: Double?, preferred: Double?, max: Double?): StimulusTargetRange? {
        if (min == null || preferred == null || max == null) return null
        return runCatching { StimulusTargetRange(min, preferred, max) }.getOrNull()
    }

    private companion object {
        val PERSONAL_BASELINE_STRATEGIES = setOf(
            StimulusDoseStrategy.HOLD_PERSONAL_BASELINE,
            StimulusDoseStrategy.HOLD_PERSONAL_BASELINE_ALLOW_PROGRESSION,
            StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE,
            StimulusDoseStrategy.REDISTRIBUTE_PERSONAL_BASELINE
        )
    }
}

/** Compares B4 against the unchanged legacy target plan without selecting a winner. */
class StimulusTargetPlanComparisonEngine {
    fun compare(legacy: TargetStimulusPlan, canonical: StimulusTargetPlan): StimulusTargetPlanLegacyComparison {
        val qualities = canonical.qualityTargets.map { target ->
            compareQuality(legacy.qualityTargets.firstOrNull { it.quality == target.quality }, target)
        }
        val tasks = canonical.taskTargets.map { target ->
            compareTask(legacy.taskTargets.firstOrNull { it.task == target.task }, target)
        }
        return StimulusTargetPlanLegacyComparison(
            qualityComparisons = qualities,
            taskComparisons = tasks,
            reasonCodes = listOf("LEGACY_AND_CANONICAL_TARGETS_ARE_OBSERVATION_ONLY", "NO_TARGET_PLANNER_WINNER_SELECTED")
        )
    }

    private fun compareQuality(old: QualityStimulusTarget?, current: StimulusQualityTarget): StimulusQualityTargetComparison {
        if (old == null) return StimulusQualityTargetComparison(
            current.quality, StimulusTargetComparisonStatus.UNAVAILABLE, null, current.strategy, null,
            current.numericAuthority, listOf("LEGACY_TARGET_UNAVAILABLE")
        )
        val reasons = linkedSetOf<String>()
        val expectedAction = current.strategy.legacyAction()
        val strategyMatch = old.action == expectedAction
        if (strategyMatch) reasons += "TARGET_STRATEGY_MATCH" else reasons += "TARGET_STRATEGY_DIFFERS_FROM_LEGACY"
        val oldAuthority = old.numericAuthority
        val currentAuthority = current.numericAuthority
        val authorityMatch = oldAuthority.toCanonical() == currentAuthority
        if (authorityMatch) reasons += "NUMERIC_AUTHORITY_MATCH" else reasons += "NUMERIC_AUTHORITY_DIFFERS"
        if (!current.hasPersonalDirectBaseline && oldAuthority in setOf(
                TargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE, TargetNumericAuthority.PERSONAL_RESTORE_BASELINE
            )) reasons += "CANONICAL_PERSONAL_BASELINE_NOT_AVAILABLE"
        if (old.targetWeeklyDirectUnitsMin != current.weeklyDirectUnitsTarget?.min ||
            old.targetWeeklyDirectUnitsPreferred != current.weeklyDirectUnitsTarget?.preferred ||
            old.targetWeeklyDirectUnitsMax != current.weeklyDirectUnitsTarget?.max) reasons += "CANONICAL_WEEKLY_UNIT_BAND_DIFFERS"
        if (old.targetWeeklyDirectSessionsMin != current.weeklyDirectSessionsTarget?.min ||
            old.targetWeeklyDirectSessionsPreferred != current.weeklyDirectSessionsTarget?.preferred ||
            old.targetWeeklyDirectSessionsMax != current.weeklyDirectSessionsTarget?.max) reasons += "CANONICAL_WEEKLY_SESSION_BAND_DIFFERS"
        if (old.targetExposureWeekDirectUnitsMin != current.exposureWeekDirectUnitsReference?.min ||
            old.targetExposureWeekDirectUnitsPreferred != current.exposureWeekDirectUnitsReference?.preferred ||
            old.targetExposureWeekDirectUnitsMax != current.exposureWeekDirectUnitsReference?.max ||
            old.targetExposureWeekDirectSessionsMin != current.exposureWeekDirectSessionsReference?.min ||
            old.targetExposureWeekDirectSessionsPreferred != current.exposureWeekDirectSessionsReference?.preferred ||
            old.targetExposureWeekDirectSessionsMax != current.exposureWeekDirectSessionsReference?.max ||
            old.targetExposureWeekFrequency != current.exposureWeekFrequencyReference) reasons += "CANONICAL_EXPOSURE_REFERENCE_DIFFERS"
        val priorityMatch = old.priority == current.priority
        if (!priorityMatch) reasons += "TARGET_PRIORITY_DIFFERS_FROM_LEGACY"
        val status = if (strategyMatch && authorityMatch && priorityMatch &&
            old.targetWeeklyDirectUnitsMin == current.weeklyDirectUnitsTarget?.min &&
            old.targetWeeklyDirectUnitsPreferred == current.weeklyDirectUnitsTarget?.preferred &&
            old.targetWeeklyDirectUnitsMax == current.weeklyDirectUnitsTarget?.max &&
            old.targetWeeklyDirectSessionsMin == current.weeklyDirectSessionsTarget?.min &&
            old.targetWeeklyDirectSessionsPreferred == current.weeklyDirectSessionsTarget?.preferred &&
            old.targetWeeklyDirectSessionsMax == current.weeklyDirectSessionsTarget?.max &&
            old.targetExposureWeekDirectUnitsMin == current.exposureWeekDirectUnitsReference?.min &&
            old.targetExposureWeekDirectUnitsPreferred == current.exposureWeekDirectUnitsReference?.preferred &&
            old.targetExposureWeekDirectUnitsMax == current.exposureWeekDirectUnitsReference?.max &&
            old.targetExposureWeekDirectSessionsMin == current.exposureWeekDirectSessionsReference?.min &&
            old.targetExposureWeekDirectSessionsPreferred == current.exposureWeekDirectSessionsReference?.preferred &&
            old.targetExposureWeekDirectSessionsMax == current.exposureWeekDirectSessionsReference?.max &&
            old.targetExposureWeekFrequency == current.exposureWeekFrequencyReference
        ) StimulusTargetComparisonStatus.MATCH else StimulusTargetComparisonStatus.DIFFERENT
        return StimulusQualityTargetComparison(current.quality, status, old.action, current.strategy, oldAuthority, currentAuthority, reasons.toList())
    }

    private fun compareTask(old: TaskStimulusTarget?, current: StimulusTaskTarget): StimulusTaskTargetComparison {
        if (old == null) return StimulusTaskTargetComparison(
            current.task, StimulusTargetComparisonStatus.UNAVAILABLE, null, current.strategy, null,
            current.numericAuthority, listOf("LEGACY_TARGET_UNAVAILABLE")
        )
        val reasons = linkedSetOf<String>()
        val strategyMatch = old.action == current.strategy.legacyAction()
        if (strategyMatch) reasons += "TARGET_STRATEGY_MATCH" else reasons += "TARGET_STRATEGY_DIFFERS_FROM_LEGACY"
        val oldAuthority = old.numericAuthority
        val authorityMatch = oldAuthority.toCanonical() == current.numericAuthority
        if (authorityMatch) reasons += "NUMERIC_AUTHORITY_MATCH" else reasons += "NUMERIC_AUTHORITY_DIFFERS"
        if (oldAuthority != TargetNumericAuthority.DIRECTION_ONLY && current.numericAuthority == StimulusTargetNumericAuthority.DIRECTION_ONLY) {
            reasons += "LEGACY_TASK_NUMERIC_BASELINE_NOT_ADOPTED_IN_B4"
        }
        if (current.numericAuthority == StimulusTargetNumericAuthority.DIRECTION_ONLY) reasons += "CANONICAL_TASK_REMAINS_DIRECTION_ONLY"
        val priorityMatch = old.priority == current.priority
        if (!priorityMatch) reasons += "TARGET_PRIORITY_DIFFERS_FROM_LEGACY"
        val status = if (strategyMatch && authorityMatch && priorityMatch &&
            old.targetWeeklyDirectUnitsMin == current.weeklyDirectUnitsTarget?.min &&
            old.targetWeeklyDirectUnitsPreferred == current.weeklyDirectUnitsTarget?.preferred &&
            old.targetWeeklyDirectUnitsMax == current.weeklyDirectUnitsTarget?.max
        ) StimulusTargetComparisonStatus.MATCH else StimulusTargetComparisonStatus.DIFFERENT
        return StimulusTaskTargetComparison(current.task, status, old.action, current.strategy, oldAuthority, current.numericAuthority, reasons.toList())
    }

    private fun StimulusDoseStrategy.legacyAction(): TargetStimulusAction = when (this) {
        StimulusDoseStrategy.HOLD_PERSONAL_BASELINE -> TargetStimulusAction.HOLD_SUCCESSFUL_DOSE
        StimulusDoseStrategy.HOLD_PERSONAL_BASELINE_ALLOW_PROGRESSION -> TargetStimulusAction.HOLD_DOSE_ALLOW_PROGRESSION
        StimulusDoseStrategy.RESTORE_PERSONAL_BASELINE -> TargetStimulusAction.RESTORE_PERSONAL_BASELINE
        StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
        StimulusDoseStrategy.DEVELOP_DIRECT_STIMULUS_DIRECTION_ONLY -> TargetStimulusAction.INTRODUCE_DIRECT_STIMULUS
        StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_DIRECTION_ONLY,
        StimulusDoseStrategy.MAINTAIN_DIRECT_STIMULUS_ALLOW_PROGRESSION_DIRECTION_ONLY -> TargetStimulusAction.HOLD_SUCCESSFUL_DOSE
        StimulusDoseStrategy.REDISTRIBUTE_PERSONAL_BASELINE,
        StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY -> TargetStimulusAction.REDISTRIBUTE_EXISTING_DOSE
        StimulusDoseStrategy.REDUCE_OR_RESTRUCTURE -> TargetStimulusAction.REDUCE_OR_RESTRUCTURE
        StimulusDoseStrategy.NO_MINIMUM_TARGET -> TargetStimulusAction.NO_MINIMUM_TARGET
        StimulusDoseStrategy.UNRESOLVED -> TargetStimulusAction.UNRESOLVED
    }

    private fun TargetNumericAuthority.toCanonical(): StimulusTargetNumericAuthority = when (this) {
        TargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE -> StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE
        TargetNumericAuthority.PERSONAL_RESTORE_BASELINE -> StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE
        TargetNumericAuthority.DIRECTION_ONLY -> StimulusTargetNumericAuthority.DIRECTION_ONLY
        TargetNumericAuthority.NONE -> StimulusTargetNumericAuthority.NONE
    }
}

/** Compares existing final coverage against B4 targets without affecting generation. */
class StimulusTargetControlProgramAuditEngine {
    fun audit(
        plan: StimulusTargetPlan,
        finalAudit: FinalStimulusNeedAuditResult?,
        planningHorizonWeeks: Int
    ): StimulusTargetControlProgramAudit {
        val horizon = planningHorizonWeeks.coerceAtLeast(1)
        val qualityAudits = plan.qualityTargets.map { target ->
            val evidence = finalAudit?.finalQualityCoverage?.get(target.quality)
            val plannedUnits = evidence?.directUnits?.toDouble()?.div(horizon)
            val plannedSessions = evidence?.directSessions?.toDouble()?.div(horizon)
            val plannedFrequency = evidence?.directExposureWeeks?.toDouble()?.div(horizon)
            val reasons = linkedSetOf<String>()
            val unitsStatus = controlStatus(target, target.weeklyDirectUnitsTarget, plannedUnits, evidence?.directUnits, reasons)
            val sessionsStatus = controlStatus(target, target.weeklyDirectSessionsTarget, plannedSessions, evidence?.directSessions, reasons)
            if (target.numericAuthority in setOf(
                    StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
                    StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE
                )) reasons += "FINAL_PROGRAM_EXPOSURE_WEEK_DISTRIBUTION_NOT_AVAILABLE_IN_B4"
            if (target.exposureWeekFrequencyReference != null && plannedFrequency != null) reasons += "EXPOSURE_WEEK_DISTRIBUTION_REFERENCE_ONLY_IN_B4"
            if (target.strategy == StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY) reasons += "DISTRIBUTION_COMPARISON_DEFERRED"
            if (target.strategy == StimulusDoseStrategy.REDISTRIBUTE_PERSONAL_BASELINE) {
                reasons += "DISTRIBUTION_CHANGE_REQUIRED"
                reasons += "DISTRIBUTION_AUTHORITY_DEFERRED"
            }
            if (target.evidenceBasis == StimulusEvidenceBasis.CANONICAL_CAPABILITY_PROXY) {
                reasons += "CAPABILITY_PROXY_BAND_COMPARISON_NOT_REALIZED_STIMULUS"
            }
            if (finalAudit == null) reasons += "FINAL_PROGRAM_AUDIT_UNAVAILABLE"
            StimulusQualityControlProgramAudit(
                quality = target.quality,
                strategy = target.strategy,
                numericAuthority = target.numericAuthority,
                targetWeeklyDirectUnits = target.weeklyDirectUnitsTarget,
                plannedWeeklyDirectUnits = plannedUnits,
                weeklyDirectUnitsStatus = unitsStatus,
                targetWeeklyDirectSessions = target.weeklyDirectSessionsTarget,
                plannedWeeklyDirectSessions = plannedSessions,
                weeklyDirectSessionsStatus = sessionsStatus,
                exposureWeekFrequencyReference = target.exposureWeekFrequencyReference,
                plannedExposureWeekFrequency = plannedFrequency,
                exposureWeekFrequencyDelta = if (target.exposureWeekFrequencyReference != null && plannedFrequency != null) plannedFrequency - target.exposureWeekFrequencyReference else null,
                reasonCodes = reasons.toList(),
                evidenceBasis = target.evidenceBasis,
                prescriptionRealizationAuthority = target.prescriptionRealizationAuthority
            )
        }
        val taskAudits = plan.taskTargets.map { target ->
            val evidence = finalAudit?.finalTaskCoverage?.get(target.task)
            val plannedUnits = evidence?.directUnits?.toDouble()?.div(horizon)
            val reasons = linkedSetOf<String>().apply {
                if (target.numericAuthority == StimulusTargetNumericAuthority.UNRESOLVED) add("TARGET_DECISION_UNRESOLVED")
                else if (target.numericAuthority == StimulusTargetNumericAuthority.NONE) add("NO_MINIMUM_TARGET")
                else if (target.strategy == StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY) add("DISTRIBUTION_COMPARISON_DEFERRED")
                else add(if ((evidence?.directUnits ?: 0) > 0) "DIRECT_PRESENT" else "DIRECT_ABSENT")
                if (finalAudit == null) add("FINAL_PROGRAM_AUDIT_UNAVAILABLE")
            }
            StimulusTaskControlProgramAudit(target.task, target.strategy, target.numericAuthority, plannedUnits,
                when (target.numericAuthority) {
                    StimulusTargetNumericAuthority.NONE -> StimulusTargetControlStatus.NO_MINIMUM
                    StimulusTargetNumericAuthority.UNRESOLVED -> StimulusTargetControlStatus.UNRESOLVED
                    else -> if (target.strategy == StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY) StimulusTargetControlStatus.DISTRIBUTION_COMPARISON_DEFERRED
                    else if ((evidence?.directUnits ?: 0) > 0) StimulusTargetControlStatus.DIRECT_PRESENT else StimulusTargetControlStatus.DIRECT_ABSENT
                }, reasons.toList(), target.evidenceBasis, target.prescriptionRealizationAuthority)
        }
        return StimulusTargetControlProgramAudit(horizon, qualityAudits, taskAudits)
    }

    private fun controlStatus(
        target: StimulusQualityTarget,
        range: StimulusTargetRange?,
        planned: Double?,
        directUnits: Int?,
        reasons: MutableSet<String>
    ): StimulusTargetControlStatus = when (target.numericAuthority) {
        StimulusTargetNumericAuthority.NONE -> StimulusTargetControlStatus.NO_MINIMUM
        StimulusTargetNumericAuthority.UNRESOLVED -> StimulusTargetControlStatus.UNRESOLVED
        StimulusTargetNumericAuthority.DIRECTION_ONLY -> {
            if (target.strategy == StimulusDoseStrategy.REDISTRIBUTE_DIRECTION_ONLY) {
                reasons += "DISTRIBUTION_COMPARISON_DEFERRED"
                StimulusTargetControlStatus.DISTRIBUTION_COMPARISON_DEFERRED
            } else if ((directUnits ?: 0) > 0) StimulusTargetControlStatus.DIRECT_PRESENT
            else StimulusTargetControlStatus.DIRECT_ABSENT
        }
        StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE,
        StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE -> when {
            range == null || planned == null -> StimulusTargetControlStatus.UNRESOLVED
            planned < range.min -> StimulusTargetControlStatus.BELOW_BAND
            planned > range.max -> StimulusTargetControlStatus.ABOVE_BAND
            else -> StimulusTargetControlStatus.WITHIN_BAND
        }
    }
}

internal fun StimulusTargetPlan.toCompactJson(): JSONObject = JSONObject()
    .put("shadowOnly", shadowOnly)
    .put("prescriptionAuthority", prescriptionAuthority)
    .put("selectionAuthority", selectionAuthority)
    .put("placementAuthority", placementAuthority)
    .put("schedulingAuthority", schedulingAuthority)
    .put("unresolved", JSONArray(unresolved))
    .put("qualityTargets", JSONArray(qualityTargets.map { target -> JSONObject()
        .put("quality", target.quality.name).put("strategy", target.strategy.name).put("priority", target.priority.name)
        .put("numericAuthority", target.numericAuthority.name).put("baselineSource", target.baselineSource?.name)
        .put("baselineConfidence", target.baselineConfidence?.name)
        .put("baselineAvailable", target.baselineAvailable).put("hasPersonalDirectBaseline", target.hasPersonalDirectBaseline)
        .put("numericBaselineUsable", target.numericBaselineUsable)
        .put("evidenceBasis", target.evidenceBasis.name)
        .put("prescriptionRealizationAuthority", target.prescriptionRealizationAuthority)
        .put("weeklyDirectUnitsTarget", target.weeklyDirectUnitsTarget?.toJson())
        .put("weeklyDirectSessionsTarget", target.weeklyDirectSessionsTarget?.toJson())
        .put("exposureWeekDirectUnitsReference", target.exposureWeekDirectUnitsReference?.toJson())
        .put("exposureWeekDirectSessionsReference", target.exposureWeekDirectSessionsReference?.toJson())
        .put("exposureWeekFrequencyReference", target.exposureWeekFrequencyReference)
        .put("reasonCodes", JSONArray(target.reasonCodes)).put("evidence", JSONArray(target.evidence))
    }))
    .put("taskTargets", JSONArray(taskTargets.map { target -> JSONObject()
        .put("task", target.task).put("strategy", target.strategy.name).put("priority", target.priority.name)
        .put("numericAuthority", target.numericAuthority.name)
        .put("evidenceBasis", target.evidenceBasis.name)
        .put("prescriptionRealizationAuthority", target.prescriptionRealizationAuthority)
        .put("weeklyDirectUnitsTarget", target.weeklyDirectUnitsTarget?.toJson())
        .put("weeklyDirectSessionsTarget", target.weeklyDirectSessionsTarget?.toJson())
        .put("reasonCodes", JSONArray(target.reasonCodes)).put("evidence", JSONArray(target.evidence))
    }))
    .put("legacyComparison", legacyComparison?.toJson())
    .put("controlProgramAudit", controlProgramAudit?.toJson())

private fun StimulusTargetRange.toJson() = JSONObject().put("min", min).put("preferred", preferred).put("max", max)

private fun StimulusTargetPlanLegacyComparison.toJson() = JSONObject()
    .put("qualityMatchCount", qualityMatchCount).put("qualityDifferenceCount", qualityDifferenceCount)
    .put("taskMatchCount", taskMatchCount).put("taskDifferenceCount", taskDifferenceCount)
    .put("unavailableCount", unavailableCount).put("reasonCodes", JSONArray(reasonCodes))
    .put("qualityComparisons", JSONArray(qualityComparisons.map { JSONObject()
        .put("quality", it.quality.name).put("status", it.status.name).put("legacyAction", it.legacyAction?.name)
        .put("canonicalStrategy", it.canonicalStrategy?.name).put("legacyNumericAuthority", it.legacyNumericAuthority?.name)
        .put("canonicalNumericAuthority", it.canonicalNumericAuthority?.name).put("reasonCodes", JSONArray(it.reasonCodes))
    }))
    .put("taskComparisons", JSONArray(taskComparisons.map { JSONObject()
        .put("task", it.task).put("status", it.status.name).put("legacyAction", it.legacyAction?.name)
        .put("canonicalStrategy", it.canonicalStrategy?.name).put("legacyNumericAuthority", it.legacyNumericAuthority?.name)
        .put("canonicalNumericAuthority", it.canonicalNumericAuthority?.name).put("reasonCodes", JSONArray(it.reasonCodes))
    }))

private fun StimulusTargetControlProgramAudit.toJson() = JSONObject()
    .put("planningHorizonWeeks", planningHorizonWeeks).put("shadowOnly", shadowOnly)
    .put("prescriptionAuthority", prescriptionAuthority).put("selectionAuthority", selectionAuthority)
    .put("placementAuthority", placementAuthority).put("schedulingAuthority", schedulingAuthority)
    .put("qualityAudits", JSONArray(qualityAudits.map { JSONObject()
        .put("quality", it.quality.name).put("strategy", it.strategy.name).put("numericAuthority", it.numericAuthority.name)
        .put("evidenceBasis", it.evidenceBasis.name)
        .put("prescriptionRealizationAuthority", it.prescriptionRealizationAuthority)
        .put("plannedWeeklyDirectUnits", it.plannedWeeklyDirectUnits).put("weeklyDirectUnitsStatus", it.weeklyDirectUnitsStatus.name)
        .put("plannedWeeklyDirectSessions", it.plannedWeeklyDirectSessions).put("weeklyDirectSessionsStatus", it.weeklyDirectSessionsStatus.name)
        .put("exposureWeekFrequencyReference", it.exposureWeekFrequencyReference)
        .put("plannedExposureWeekFrequency", it.plannedExposureWeekFrequency)
        .put("exposureWeekFrequencyDelta", it.exposureWeekFrequencyDelta)
        .put("reasonCodes", JSONArray(it.reasonCodes))
    }))
    .put("taskAudits", JSONArray(taskAudits.map { JSONObject()
        .put("task", it.task).put("strategy", it.strategy.name).put("numericAuthority", it.numericAuthority.name)
        .put("plannedDirectUnits", it.plannedDirectUnits).put("status", it.status.name)
        .put("evidenceBasis", it.evidenceBasis.name)
        .put("prescriptionRealizationAuthority", it.prescriptionRealizationAuthority)
        .put("reasonCodes", JSONArray(it.reasonCodes))
    }))

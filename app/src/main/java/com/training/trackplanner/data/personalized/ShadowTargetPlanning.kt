package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.CanonicalExercisePhysicalQualityCatalog
import com.training.trackplanner.data.ExercisePhysicalQualityRelation
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.StimulusCapabilityLevel
import com.training.trackplanner.data.TrainableQuality
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** Strategy only: this trace never selects exercises or authorizes a dose. */
enum class TargetStimulusAction {
    HOLD_SUCCESSFUL_DOSE,
    HOLD_DOSE_ALLOW_PROGRESSION,
    RESTORE_PERSONAL_BASELINE,
    INTRODUCE_DIRECT_STIMULUS,
    REDISTRIBUTE_EXISTING_DOSE,
    REDUCE_OR_RESTRUCTURE,
    NO_MINIMUM_TARGET,
    UNRESOLVED
}

enum class TargetPriority { PRIMARY, SECONDARY, MAINTENANCE, BACKGROUND, NONE, UNRESOLVED }

enum class TargetNumericAuthority {
    PERSONAL_SUCCESSFUL_DOSE,
    PERSONAL_RESTORE_BASELINE,
    DIRECTION_ONLY,
    NONE
}

enum class SuccessfulDoseSource {
    NORMAL_COMPLETED_WEEKS,
    RECENT_ACTIVE_WEEKS_FALLBACK,
    CURRENT_28D_FALLBACK,
    NO_PERSONAL_BASELINE
}

data class SuccessfulDoseBand(
    val eligibleWeekCount: Int,
    val directUnitsQ25: Double?,
    val directUnitsMedian: Double?,
    val directUnitsQ75: Double?,
    val directSessionsQ25: Double?,
    val directSessionsMedian: Double?,
    val directSessionsQ75: Double?,
    val supportiveUnitsQ25: Double?,
    val supportiveUnitsMedian: Double?,
    val supportiveUnitsQ75: Double?,
    val confidence: PlanningConfidence,
    val source: SuccessfulDoseSource
) {
    val hasPersonalDirectBaseline: Boolean
        get() = source in setOf(SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS, SuccessfulDoseSource.RECENT_ACTIVE_WEEKS_FALLBACK) &&
            eligibleWeekCount >= 2 && directUnitsMedian != null && directUnitsMedian > 0.0
}

data class QualityDoseHistory(
    val bands: Map<TrainableQuality, SuccessfulDoseBand>,
    val taskBands: Map<String, SuccessfulDoseBand>,
    val indexedWeekCount: Int,
    val excludedWeekCount: Int,
    val notes: List<String> = emptyList()
)

data class QualityTrainingDecision(
    val quality: TrainableQuality,
    val needDecision: TrainingNeedDecision,
    val action: TargetStimulusAction,
    val priority: TargetPriority,
    val confidence: PlanningConfidence,
    val reasonCodes: List<String>,
    val evidence: List<String>
)

data class TaskTrainingDecision(
    val task: String,
    val needDecision: TrainingNeedDecision,
    val action: TargetStimulusAction,
    val priority: TargetPriority,
    val confidence: PlanningConfidence,
    val explicitUserTaskPriority: Boolean,
    val reasonCodes: List<String>,
    val evidence: List<String>
)

data class TrainingDecisionPortfolio(
    val qualityDecisions: List<QualityTrainingDecision>,
    val taskDecisions: List<TaskTrainingDecision>,
    val unresolved: List<String>,
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false
)

data class QualityStimulusTarget(
    val quality: TrainableQuality,
    val action: TargetStimulusAction,
    val priority: TargetPriority,
    val baseline: SuccessfulDoseBand?,
    val targetDirectUnitsMin: Double?,
    val targetDirectUnitsPreferred: Double?,
    val targetDirectUnitsMax: Double?,
    val targetDirectSessionsMin: Double?,
    val targetDirectSessionsPreferred: Double?,
    val targetDirectSessionsMax: Double?,
    val numericAuthority: TargetNumericAuthority,
    val confidence: PlanningConfidence,
    val reasonCodes: List<String>,
    val evidence: List<String>
)

data class TaskStimulusTarget(
    val task: String,
    val action: TargetStimulusAction,
    val priority: TargetPriority,
    val baseline: SuccessfulDoseBand?,
    val targetDirectUnitsMin: Double?,
    val targetDirectUnitsPreferred: Double?,
    val targetDirectUnitsMax: Double?,
    val numericAuthority: TargetNumericAuthority,
    val confidence: PlanningConfidence,
    val explicitUserTaskPriority: Boolean,
    val reasonCodes: List<String>,
    val evidence: List<String>
)

data class TargetStimulusPlan(
    val qualityTargets: List<QualityStimulusTarget>,
    val taskTargets: List<TaskStimulusTarget>,
    val decisionPortfolio: TrainingDecisionPortfolio,
    val unresolved: List<String>,
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false
)

enum class TargetComparisonStatus {
    BELOW_TARGET_BAND,
    WITHIN_TARGET_BAND,
    ABOVE_TARGET_BAND,
    DIRECTION_ONLY,
    NO_MINIMUM_TARGET,
    UNRESOLVED
}

data class QualityTargetComparison(
    val quality: TrainableQuality,
    val plannedCapabilityUnits: Int,
    val targetMin: Double?,
    val targetPreferred: Double?,
    val targetMax: Double?,
    val status: TargetComparisonStatus,
    val reasonCodes: List<String> = listOf("PLANNED_CAPABILITY_COVERAGE_NOT_REALIZED_STIMULUS")
)

data class TaskTargetComparison(
    val task: String,
    val plannedCapabilityUnits: Int,
    val targetMin: Double?,
    val targetPreferred: Double?,
    val targetMax: Double?,
    val status: TargetComparisonStatus,
    val reasonCodes: List<String> = listOf("PLANNED_CAPABILITY_COVERAGE_NOT_REALIZED_STIMULUS")
)

data class TargetPlanComparison(
    val qualityComparisons: List<QualityTargetComparison>,
    val taskComparisons: List<TaskTargetComparison>,
    val notes: List<String>,
    val shadowOnly: Boolean = true,
    val prescriptionAuthority: Boolean = false
)

/** One indexed pass over the completed-week history. Direct and supportive remain separate views. */
class QualityDoseHistoryAnalyzer {
    fun analyze(
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        catalog: CanonicalExercisePhysicalQualityCatalog
    ): QualityDoseHistory {
        val completeEnd = completedTrainingWeekEnd(snapshot.cutoff)
        val weekEnds = (7 downTo 0).map { completeEnd.minusDays(it * 7L) }
        val contextByStart = state.trainingStateAssessment?.weeklyContext.orEmpty().associateBy { it.start }
        val relationByKey = catalog.trainingRelations().groupBy(ExercisePhysicalQualityRelation::exerciseStableKey)
        val taskKeys = (snapshot.badmintonDirectObjectives.keys + snapshot.badmintonSupportiveObjectives.keys).toSet()
        val firstStart = weekEnds.minOf { it }.minusDays(6)
        val lastEnd = weekEnds.maxOf { it }
        val rowsByWeekStart = snapshot.allConfirmedSets
            .asSequence()
            .filter { it.date in firstStart..lastEnd }
            .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        val weeks = weekEnds.map { end ->
            val start = end.minusDays(6)
            val context = contextByStart[start]
            val rows = rowsByWeekStart[start].orEmpty().filter {
                snapshot.activityKind(it.stableKey) in TrainingStatePolicy.controllableDomains
            }
            WeekSlice(start, end, rows, context?.excludedFromTolerance == true)
        }
        val excluded = weeks.count { it.excluded }
        val eligible = weeks.filter { !it.excluded && it.rows.isNotEmpty() }
        val bands = TrainableQuality.entries.associateWith { quality ->
            bandForQuality(quality, eligible, relationByKey, weeks, snapshot)
        }
        val taskBands = taskKeys.associateWith { task -> bandForTask(task, eligible, weeks, snapshot) }
        return QualityDoseHistory(bands, taskBands, weeks.size, excluded,
            listOf("COMPLETED_ISO_WEEKS_ONLY", "ACTIVE_WEEK_REQUIRES_CONFIRMED_CONTROLLABLE_RECORD",
                "QUALITY_TARGET_ENVELOPES_OVERLAP_AND_ARE_NOT_ADDITIVE_WORKLOAD_BUDGETS"))
    }

    private fun bandForQuality(
        quality: TrainableQuality,
        eligible: List<WeekSlice>,
        relationByKey: Map<String, List<ExercisePhysicalQualityRelation>>,
        allWeeks: List<WeekSlice>,
        snapshot: PlanningHistorySnapshot
    ): SuccessfulDoseBand {
        fun values(weeks: List<WeekSlice>) = weeks.map { week ->
            val direct = week.rows.filter { row -> relationByKey[row.stableKey].orEmpty().any { it.qualityId == quality && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY } }
            val supportive = week.rows.filter { row -> relationByKey[row.stableKey].orEmpty().any { it.qualityId == quality && it.relationLevel == StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY } }
            DoseValues(direct.size.toDouble(), direct.map { it.date }.toSet().size.toDouble(), supportive.size.toDouble(), supportive.map { it.date }.toSet().size.toDouble())
        }
        return band(values(eligible), values(allWeeks.filter { it.start >= snapshot.cutoff.minusDays(27).with(java.time.DayOfWeek.MONDAY) }))
    }

    private fun bandForTask(task: String, eligible: List<WeekSlice>, allWeeks: List<WeekSlice>, snapshot: PlanningHistorySnapshot): SuccessfulDoseBand {
        fun values(weeks: List<WeekSlice>) = weeks.map { week ->
            val direct = week.rows.filter { task in snapshot.badmintonDirectObjectives[it.stableKey].orEmpty() }
            val supportive = week.rows.filter { task in snapshot.badmintonSupportiveObjectives[it.stableKey].orEmpty() }
            DoseValues(direct.size.toDouble(), direct.map { it.date }.toSet().size.toDouble(), supportive.size.toDouble(), supportive.map { it.date }.toSet().size.toDouble())
        }
        return band(values(eligible), values(allWeeks.filter { it.start >= snapshot.cutoff.minusDays(27).with(java.time.DayOfWeek.MONDAY) }))
    }

    private fun band(normalValues: List<DoseValues>, currentValues: List<DoseValues>): SuccessfulDoseBand {
        val directNormal = normalValues.filter { it.directUnits > 0.0 }
        val source: SuccessfulDoseSource
        val values: List<DoseValues>
        when {
            directNormal.isNotEmpty() -> { source = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS; values = directNormal }
            currentValues.any { it.directUnits > 0.0 } -> { source = SuccessfulDoseSource.CURRENT_28D_FALLBACK; values = currentValues.filter { it.directUnits > 0.0 } }
            normalValues.any { it.supportiveUnits > 0.0 } -> { source = SuccessfulDoseSource.RECENT_ACTIVE_WEEKS_FALLBACK; values = normalValues.filter { it.supportiveUnits > 0.0 } }
            else -> { source = SuccessfulDoseSource.NO_PERSONAL_BASELINE; values = emptyList() }
        }
        val confidence = when {
            values.size >= 4 && consistent(values.map { it.directUnits }) -> PlanningConfidence.HIGH
            values.size >= 2 -> PlanningConfidence.MODERATE
            else -> PlanningConfidence.LOW
        }
        return SuccessfulDoseBand(values.size,
            quantile(values.map { it.directUnits }, .25), quantile(values.map { it.directUnits }, .50), quantile(values.map { it.directUnits }, .75),
            quantile(values.map { it.directSessions }, .25), quantile(values.map { it.directSessions }, .50), quantile(values.map { it.directSessions }, .75),
            quantile(values.map { it.supportiveUnits }, .25), quantile(values.map { it.supportiveUnits }, .50), quantile(values.map { it.supportiveUnits }, .75),
            confidence, source)
    }

    private fun consistent(values: List<Double>): Boolean {
        val positive = values.filter { it > 0.0 }
        if (positive.isEmpty()) return false
        val median = quantile(positive, .50) ?: return false
        return median > 0.0 && (quantile(positive, .75) ?: median) / median <= 2.5
    }

    /** Same nearest-index/rounding convention as the existing resistance baseline utility. */
    private fun quantile(values: List<Double>, q: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * q).roundToInt()]
    }

    private data class WeekSlice(val start: LocalDate, val end: LocalDate, val rows: List<PlanningSetRecord>, val excluded: Boolean)
    private data class DoseValues(val directUnits: Double, val directSessions: Double, val supportiveUnits: Double, val supportiveSessions: Double)
}

class TrainingDecisionPortfolioEngine {
    fun build(profile: AthleteNeedsProfile, history: QualityDoseHistory): TrainingDecisionPortfolio {
        val qualities = profile.qualityNeeds.map { need ->
            val priority = priority(need.relevance, need.decision)
            val action = action(need.decision, history.bands[need.quality])
            QualityTrainingDecision(need.quality, need.decision, action, priority, need.confidence,
                buildList {
                    addAll(need.reasonCodes)
                    if (need.quality == TrainableQuality.HYPERTROPHY &&
                        need.response == TrainingResponseState.INSUFFICIENT_EVIDENCE) {
                        add("NO_DIRECT_HYPERTROPHY_OUTCOME_AUTHORITY")
                    }
                    if (need.response == TrainingResponseState.POSITIVE_RESPONSE) add("POSITIVE_RESPONSE_SUPPORTS_CONTINUITY")
                    if (need.decision == TrainingNeedDecision.MAINTAIN_OR_PROGRESS) add("PROGRESSION_DOES_NOT_REQUIRE_AUTOMATIC_VOLUME_INCREASE")
                    if (need.decision == TrainingNeedDecision.REDUCE) add("NEGATIVE_RESPONSE_REQUIRES_CAUSE_INSPECTION")
                    if (profile.executionModifiers.isNotEmpty()) add("CURRENT_EXECUTION_CONSTRAINT_PRESENT")
                }, need.evidence + listOf("personalBaselineSource=${history.bands[need.quality]?.source}"))
        }
        val tasks = profile.sportTaskNeeds.map { need ->
            TaskTrainingDecision(need.task, taskDecision(need), action(need.decision, history.taskBands[need.task]),
                priority(need.relevance, need.decision), need.confidence, false,
                need.reasonCodes + "NO_EXPLICIT_BADMINTON_TASK_PRIORITY",
                need.evidence + "taskPriorityAuthority=NONE")
        }
        return TrainingDecisionPortfolio(qualities, tasks,
            (qualities.filter { it.priority == TargetPriority.UNRESOLVED }.map { "QUALITY_${it.quality.name}_UNRESOLVED" } +
                tasks.filter { it.priority == TargetPriority.UNRESOLVED }.map { "TASK_${it.task}_UNRESOLVED" }).distinct())
    }

    private fun action(decision: TrainingNeedDecision, band: SuccessfulDoseBand?): TargetStimulusAction = when (decision) {
        TrainingNeedDecision.MAINTAIN -> TargetStimulusAction.HOLD_SUCCESSFUL_DOSE
        TrainingNeedDecision.MAINTAIN_OR_PROGRESS -> TargetStimulusAction.HOLD_DOSE_ALLOW_PROGRESSION
        TrainingNeedDecision.DEVELOP -> if (band?.hasPersonalDirectBaseline == true) TargetStimulusAction.RESTORE_PERSONAL_BASELINE else TargetStimulusAction.INTRODUCE_DIRECT_STIMULUS
        TrainingNeedDecision.REDISTRIBUTE -> TargetStimulusAction.REDISTRIBUTE_EXISTING_DOSE
        TrainingNeedDecision.REDUCE -> TargetStimulusAction.REDUCE_OR_RESTRUCTURE
        TrainingNeedDecision.NO_EXTRA_NEED -> TargetStimulusAction.NO_MINIMUM_TARGET
        TrainingNeedDecision.UNKNOWN, TrainingNeedDecision.PROGRESS -> TargetStimulusAction.UNRESOLVED
    }

    private fun taskDecision(need: SportTaskNeed): TrainingNeedDecision = need.decision

    private fun priority(relevance: NeedRelevance, decision: TrainingNeedDecision): TargetPriority = when (relevance) {
        NeedRelevance.HIGH -> if (decision in setOf(TrainingNeedDecision.DEVELOP, TrainingNeedDecision.PROGRESS)) TargetPriority.PRIMARY else TargetPriority.MAINTENANCE
        NeedRelevance.MODERATE -> if (decision == TrainingNeedDecision.DEVELOP) TargetPriority.SECONDARY else TargetPriority.MAINTENANCE
        NeedRelevance.LOW -> TargetPriority.BACKGROUND
        NeedRelevance.NONE -> TargetPriority.NONE
        NeedRelevance.UNKNOWN -> TargetPriority.UNRESOLVED
    }
}

class TargetStimulusPlanEngine {
    fun build(portfolio: TrainingDecisionPortfolio, history: QualityDoseHistory): TargetStimulusPlan {
        val qualityTargets = portfolio.qualityDecisions.map { decision ->
            val band = history.bands[decision.quality]
            val numeric = numericAuthority(decision.action, band)
            val useBand = numeric in setOf(TargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE, TargetNumericAuthority.PERSONAL_RESTORE_BASELINE)
            QualityStimulusTarget(decision.quality, decision.action, decision.priority, band,
                band?.takeIf { useBand }?.directUnitsQ25, band?.takeIf { useBand }?.directUnitsMedian, band?.takeIf { useBand }?.directUnitsQ75,
                band?.takeIf { useBand }?.directSessionsQ25, band?.takeIf { useBand }?.directSessionsMedian, band?.takeIf { useBand }?.directSessionsQ75,
                numeric, decision.confidence, decision.reasonCodes + reasonsFor(decision.action, numeric, band), decision.evidence)
        }
        val taskTargets = portfolio.taskDecisions.map { decision ->
            val band = history.taskBands[decision.task]
            val maintenanceBaseline = decision.action in setOf(TargetStimulusAction.HOLD_SUCCESSFUL_DOSE, TargetStimulusAction.HOLD_DOSE_ALLOW_PROGRESSION) &&
                band?.hasPersonalDirectBaseline == true
            val numeric = if ((decision.explicitUserTaskPriority || maintenanceBaseline) && band?.hasPersonalDirectBaseline == true) {
                numericAuthority(decision.action, band)
            } else TargetNumericAuthority.DIRECTION_ONLY
            val useBand = (decision.explicitUserTaskPriority || maintenanceBaseline) && numeric != TargetNumericAuthority.DIRECTION_ONLY
            TaskStimulusTarget(decision.task, decision.action, decision.priority, band,
                band?.takeIf { useBand }?.directUnitsQ25, band?.takeIf { useBand }?.directUnitsMedian, band?.takeIf { useBand }?.directUnitsQ75,
                numeric, decision.confidence, decision.explicitUserTaskPriority,
                decision.reasonCodes + "NO_EXPLICIT_BADMINTON_TASK_PRIORITY", decision.evidence)
        }
        return TargetStimulusPlan(qualityTargets, taskTargets, portfolio,
            portfolio.unresolved + listOf("TARGET_PLAN_IS_DIRECTIONAL_SHADOW_ONLY"))
    }

    private fun numericAuthority(action: TargetStimulusAction, band: SuccessfulDoseBand?): TargetNumericAuthority = when {
        band?.hasPersonalDirectBaseline != true -> if (action == TargetStimulusAction.NO_MINIMUM_TARGET) TargetNumericAuthority.NONE else TargetNumericAuthority.DIRECTION_ONLY
        action == TargetStimulusAction.RESTORE_PERSONAL_BASELINE -> TargetNumericAuthority.PERSONAL_RESTORE_BASELINE
        action in setOf(TargetStimulusAction.HOLD_SUCCESSFUL_DOSE, TargetStimulusAction.HOLD_DOSE_ALLOW_PROGRESSION,
            TargetStimulusAction.REDISTRIBUTE_EXISTING_DOSE) -> TargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE
        action == TargetStimulusAction.NO_MINIMUM_TARGET -> TargetNumericAuthority.NONE
        else -> TargetNumericAuthority.DIRECTION_ONLY
    }

    private fun reasonsFor(action: TargetStimulusAction, authority: TargetNumericAuthority, band: SuccessfulDoseBand?): List<String> = buildList {
        when (action) {
            TargetStimulusAction.HOLD_DOSE_ALLOW_PROGRESSION -> add("PROGRESSION_DOES_NOT_REQUIRE_AUTOMATIC_VOLUME_INCREASE")
            TargetStimulusAction.HOLD_SUCCESSFUL_DOSE -> if (band?.hasPersonalDirectBaseline == true) add("REPEATEDLY_TOLERATED_EXPOSURE")
            TargetStimulusAction.RESTORE_PERSONAL_BASELINE -> {
                add("RECENT_EXPOSURE_BELOW_PERSONAL_NORMAL")
                add("RESTORE_BEFORE_INVENTING_NEW_DOSE")
            }
            TargetStimulusAction.INTRODUCE_DIRECT_STIMULUS -> add("NO_PERSONAL_DOSE_BASELINE")
            TargetStimulusAction.REDISTRIBUTE_EXISTING_DOSE -> add("DISTRIBUTION_DOWNSTREAM_UNRESOLVED")
            TargetStimulusAction.REDUCE_OR_RESTRUCTURE -> {
                add("NEGATIVE_RESPONSE_PRESENT")
                add("CAUSE_NOT_IDENTIFIED")
                add("VOLUME_REDUCTION_NOT_ASSUMED")
                add("NEGATIVE_RESPONSE_REQUIRES_CAUSE_INSPECTION")
            }
            TargetStimulusAction.NO_MINIMUM_TARGET -> {
                add("NO_SEPARATE_DEVELOPMENT_FLOOR")
                add("INCIDENTAL_STIMULUS_ALLOWED")
            }
            TargetStimulusAction.UNRESOLVED -> add("TARGET_DECISION_UNRESOLVED")
        }
        if (authority == TargetNumericAuthority.DIRECTION_ONLY) {
            add("NOVEL_STIMULUS_REQUIRES_DOWNSTREAM_PRESCRIPTION_AUTHORITY")
        } else if (authority == TargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE || authority == TargetNumericAuthority.PERSONAL_RESTORE_BASELINE) {
            add("PERSONAL_SUCCESSFUL_DOSE_BAND")
        }
    }
}

class TargetPlanComparisonEngine {
    fun compare(
        plan: TargetStimulusPlan,
        generated: GeneratedProgramSkeleton,
        snapshot: PlanningHistorySnapshot,
        catalog: CanonicalExercisePhysicalQualityCatalog
    ): TargetPlanComparison {
        val relations = catalog.trainingRelations().groupBy(ExercisePhysicalQualityRelation::exerciseStableKey)
        val plannedWeekCount = generated.request.durationWeeks.coerceAtLeast(1)
        fun averageWeeklyUnits(items: List<com.training.trackplanner.data.ProgramSkeletonItem>): Int =
            (items.groupBy { it.weekNumber }.values.sumOf { rows -> rows.sumOf { it.setCount } }.toDouble() / plannedWeekCount).roundToInt()
        val qualityCounts = TrainableQuality.entries.associateWith { quality ->
            averageWeeklyUnits(generated.items.filter { item ->
                relations[item.exerciseStableKey].orEmpty().any {
                    it.qualityId == quality && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY
                }
            })
        }
        val qualityComparisons = plan.qualityTargets.map { target ->
            QualityTargetComparison(target.quality, qualityCounts[target.quality] ?: 0, target.targetDirectUnitsMin,
                target.targetDirectUnitsPreferred, target.targetDirectUnitsMax, status(target.targetDirectUnitsMin, target.targetDirectUnitsMax, qualityCounts[target.quality] ?: 0, target.numericAuthority, target.action))
        }
        val taskCounts = plan.taskTargets.associate { target ->
            target.task to averageWeeklyUnits(generated.items.filter { item ->
                snapshot.activityKind(item.exerciseStableKey) in setOf(PlannedActivityKind.STRUCTURED_BADMINTON_DRILL, PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL, PlannedActivityKind.RESISTANCE) &&
                    target.task in snapshot.badmintonDirectObjectives[item.exerciseStableKey].orEmpty()
            })
        }
        val taskComparisons = plan.taskTargets.map { target ->
            TaskTargetComparison(target.task, taskCounts[target.task] ?: 0, target.targetDirectUnitsMin, target.targetDirectUnitsPreferred,
                target.targetDirectUnitsMax, status(target.targetDirectUnitsMin, target.targetDirectUnitsMax, taskCounts[target.task] ?: 0, target.numericAuthority, target.action))
        }
        return TargetPlanComparison(qualityComparisons, taskComparisons,
            listOf("PLANNED_CAPABILITY_COVERAGE_IS_AN_AUDIT_APPROXIMATION", "NOT_PROOF_OF_REALIZED_PHYSIOLOGICAL_STIMULUS",
                "QUALITY_TARGET_ENVELOPES_ARE_OVERLAPPING_SEMANTIC_VIEWS_NOT_ADDITIVE_WEEKLY_WORKLOAD_BUDGETS"))
    }

    private fun status(min: Double?, max: Double?, planned: Int, authority: TargetNumericAuthority, action: TargetStimulusAction): TargetComparisonStatus = when {
        action == TargetStimulusAction.UNRESOLVED -> TargetComparisonStatus.UNRESOLVED
        action == TargetStimulusAction.NO_MINIMUM_TARGET -> TargetComparisonStatus.NO_MINIMUM_TARGET
        min == null || authority == TargetNumericAuthority.DIRECTION_ONLY -> TargetComparisonStatus.DIRECTION_ONLY
        planned < min -> TargetComparisonStatus.BELOW_TARGET_BAND
        max != null && planned > max -> TargetComparisonStatus.ABOVE_TARGET_BAND
        else -> TargetComparisonStatus.WITHIN_TARGET_BAND
    }
}

internal fun TrainingDecisionPortfolio.toJson(): JSONObject = JSONObject()
    .put("shadowOnly", shadowOnly)
    .put("prescriptionAuthority", prescriptionAuthority)
    .put("unresolved", JSONArray(unresolved))
    .put("qualityDecisions", JSONArray(qualityDecisions.map { JSONObject()
        .put("quality", it.quality.name).put("needDecision", it.needDecision.name).put("action", it.action.name)
        .put("priority", it.priority.name).put("confidence", it.confidence.name)
        .put("reasonCodes", JSONArray(it.reasonCodes)).put("evidence", JSONArray(it.evidence))
    }))
    .put("taskDecisions", JSONArray(taskDecisions.map { JSONObject()
        .put("task", it.task).put("needDecision", it.needDecision.name).put("action", it.action.name)
        .put("priority", it.priority.name).put("confidence", it.confidence.name)
        .put("explicitUserTaskPriority", it.explicitUserTaskPriority)
        .put("reasonCodes", JSONArray(it.reasonCodes)).put("evidence", JSONArray(it.evidence))
    }))

internal fun TargetStimulusPlan.toJson(): JSONObject = JSONObject()
    .put("shadowOnly", shadowOnly)
    .put("prescriptionAuthority", prescriptionAuthority)
    .put("unresolved", JSONArray(unresolved))
    .put("decisionPortfolio", decisionPortfolio.toJson())
    .put("qualityTargets", JSONArray(qualityTargets.map { JSONObject()
        .put("quality", it.quality.name).put("action", it.action.name).put("priority", it.priority.name)
        .put("baseline", it.baseline?.toJson()).put("targetDirectUnitsMin", it.targetDirectUnitsMin)
        .put("targetDirectUnitsPreferred", it.targetDirectUnitsPreferred).put("targetDirectUnitsMax", it.targetDirectUnitsMax)
        .put("targetDirectSessionsMin", it.targetDirectSessionsMin).put("targetDirectSessionsPreferred", it.targetDirectSessionsPreferred)
        .put("targetDirectSessionsMax", it.targetDirectSessionsMax).put("numericAuthority", it.numericAuthority.name)
        .put("confidence", it.confidence.name).put("reasonCodes", JSONArray(it.reasonCodes)).put("evidence", JSONArray(it.evidence))
    }))
    .put("taskTargets", JSONArray(taskTargets.map { JSONObject()
        .put("task", it.task).put("action", it.action.name).put("priority", it.priority.name)
        .put("baseline", it.baseline?.toJson()).put("targetDirectUnitsMin", it.targetDirectUnitsMin)
        .put("targetDirectUnitsPreferred", it.targetDirectUnitsPreferred).put("targetDirectUnitsMax", it.targetDirectUnitsMax)
        .put("numericAuthority", it.numericAuthority.name).put("confidence", it.confidence.name)
        .put("explicitUserTaskPriority", it.explicitUserTaskPriority)
        .put("reasonCodes", JSONArray(it.reasonCodes)).put("evidence", JSONArray(it.evidence))
    }))

private fun SuccessfulDoseBand.toJson(): JSONObject = JSONObject()
    .put("eligibleWeekCount", eligibleWeekCount)
    .put("directUnitsQ25", directUnitsQ25).put("directUnitsMedian", directUnitsMedian).put("directUnitsQ75", directUnitsQ75)
    .put("directSessionsQ25", directSessionsQ25).put("directSessionsMedian", directSessionsMedian).put("directSessionsQ75", directSessionsQ75)
    .put("supportiveUnitsQ25", supportiveUnitsQ25).put("supportiveUnitsMedian", supportiveUnitsMedian).put("supportiveUnitsQ75", supportiveUnitsQ75)
    .put("confidence", confidence.name).put("source", source.name)

internal fun TargetPlanComparison.toJson(): JSONObject = JSONObject()
    .put("shadowOnly", shadowOnly).put("prescriptionAuthority", prescriptionAuthority)
    .put("notes", JSONArray(notes))
    .put("qualityComparisons", JSONArray(qualityComparisons.map { JSONObject()
        .put("quality", it.quality.name).put("plannedCapabilityUnits", it.plannedCapabilityUnits)
        .put("targetMin", it.targetMin).put("targetPreferred", it.targetPreferred).put("targetMax", it.targetMax)
        .put("status", it.status.name).put("reasonCodes", JSONArray(it.reasonCodes))
    }))
    .put("taskComparisons", JSONArray(taskComparisons.map { JSONObject()
        .put("task", it.task).put("plannedCapabilityUnits", it.plannedCapabilityUnits)
        .put("targetMin", it.targetMin).put("targetPreferred", it.targetPreferred).put("targetMax", it.targetMax)
        .put("status", it.status.name).put("reasonCodes", JSONArray(it.reasonCodes))
    }))

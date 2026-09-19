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
    /** Backwards-compatible aliases for the weekly direct-dose distribution. */
    val directUnitsQ25: Double? = null,
    val directUnitsMedian: Double? = null,
    val directUnitsQ75: Double? = null,
    val directSessionsQ25: Double? = null,
    val directSessionsMedian: Double? = null,
    val directSessionsQ75: Double? = null,
    /** Backwards-compatible aliases for the weekly supportive-dose distribution. */
    val supportiveUnitsQ25: Double? = null,
    val supportiveUnitsMedian: Double? = null,
    val supportiveUnitsQ75: Double? = null,
    val confidence: PlanningConfidence = PlanningConfidence.LOW,
    val source: SuccessfulDoseSource = SuccessfulDoseSource.NO_PERSONAL_BASELINE,
    val weeklyDirectUnitsQ25: Double? = directUnitsQ25,
    val weeklyDirectUnitsMedian: Double? = directUnitsMedian,
    val weeklyDirectUnitsQ75: Double? = directUnitsQ75,
    val weeklyDirectSessionsQ25: Double? = directSessionsQ25,
    val weeklyDirectSessionsMedian: Double? = directSessionsMedian,
    val weeklyDirectSessionsQ75: Double? = directSessionsQ75,
    val exposureWeekDirectUnitsQ25: Double? = directUnitsQ25,
    val exposureWeekDirectUnitsMedian: Double? = directUnitsMedian,
    val exposureWeekDirectUnitsQ75: Double? = directUnitsQ75,
    val exposureWeekDirectSessionsQ25: Double? = directSessionsQ25,
    val exposureWeekDirectSessionsMedian: Double? = directSessionsMedian,
    val exposureWeekDirectSessionsQ75: Double? = directSessionsQ75,
    val directExposureWeekCount: Int = 0,
    val directExposureWeekFrequency: Double? = null,
    val weeklySupportiveUnitsQ25: Double? = supportiveUnitsQ25,
    val weeklySupportiveUnitsMedian: Double? = supportiveUnitsMedian,
    val weeklySupportiveUnitsQ75: Double? = supportiveUnitsQ75,
    val weeklySupportiveSessionsQ25: Double? = null,
    val weeklySupportiveSessionsMedian: Double? = null,
    val weeklySupportiveSessionsQ75: Double? = null
) {
    val hasPersonalDirectBaseline: Boolean
        get() {
            val compatibleExposureWeeks = if (directExposureWeekCount > 0) directExposureWeekCount
            // Legacy manually-constructed bands only have the weekly alias fields. A positive
            // alias is safe to interpret as the old direct-baseline signal; a zero alias is
            // deliberately not, because analyzer-produced supportive-only bands retain zero
            // active weeks in their weekly distribution.
            else if (directUnitsMedian != null && directUnitsMedian > 0.0) eligibleWeekCount
            else 0
            return source in setOf(SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS, SuccessfulDoseSource.RECENT_ACTIVE_WEEKS_FALLBACK) &&
                eligibleWeekCount >= 2 && compatibleExposureWeeks >= 2
        }
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
    val evidence: List<String>,
    val targetWeeklyDirectUnitsMin: Double? = targetDirectUnitsMin,
    val targetWeeklyDirectUnitsPreferred: Double? = targetDirectUnitsPreferred,
    val targetWeeklyDirectUnitsMax: Double? = targetDirectUnitsMax,
    val targetWeeklyDirectSessionsMin: Double? = targetDirectSessionsMin,
    val targetWeeklyDirectSessionsPreferred: Double? = targetDirectSessionsPreferred,
    val targetWeeklyDirectSessionsMax: Double? = targetDirectSessionsMax,
    val targetExposureWeekDirectUnitsMin: Double? = null,
    val targetExposureWeekDirectUnitsPreferred: Double? = null,
    val targetExposureWeekDirectUnitsMax: Double? = null,
    val targetExposureWeekDirectSessionsMin: Double? = null,
    val targetExposureWeekDirectSessionsPreferred: Double? = null,
    val targetExposureWeekDirectSessionsMax: Double? = null,
    val targetExposureWeekFrequency: Double? = baseline?.directExposureWeekFrequency
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
    val evidence: List<String>,
    val targetWeeklyDirectUnitsMin: Double? = targetDirectUnitsMin,
    val targetWeeklyDirectUnitsPreferred: Double? = targetDirectUnitsPreferred,
    val targetWeeklyDirectUnitsMax: Double? = targetDirectUnitsMax,
    val targetExposureWeekDirectUnitsMin: Double? = null,
    val targetExposureWeekDirectUnitsPreferred: Double? = null,
    val targetExposureWeekDirectUnitsMax: Double? = null,
    val targetExposureWeekFrequency: Double? = baseline?.directExposureWeekFrequency
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
    val reasonCodes: List<String> = listOf("PLANNED_CAPABILITY_COVERAGE_NOT_REALIZED_STIMULUS"),
    val targetWeeklyMin: Double? = targetMin,
    val targetWeeklyPreferred: Double? = targetPreferred,
    val targetWeeklyMax: Double? = targetMax,
    val targetExposureWeekPreferred: Double? = null,
    val targetExposureWeekFrequency: Double? = null
)

data class TaskTargetComparison(
    val task: String,
    val plannedCapabilityUnits: Int,
    val targetMin: Double?,
    val targetPreferred: Double?,
    val targetMax: Double?,
    val status: TargetComparisonStatus,
    val reasonCodes: List<String> = listOf("PLANNED_CAPABILITY_COVERAGE_NOT_REALIZED_STIMULUS"),
    val targetWeeklyMin: Double? = targetMin,
    val targetWeeklyPreferred: Double? = targetPreferred,
    val targetWeeklyMax: Double? = targetMax,
    val targetExposureWeekPreferred: Double? = null,
    val targetExposureWeekFrequency: Double? = null
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
        // The objective maps are exerciseStableKey -> objective names. taskBands is keyed by
        // those objective names so it can join SportTaskNeed.task without a second translation.
        val taskKeys = (snapshot.badmintonDirectObjectives.values.flatten() +
            snapshot.badmintonSupportiveObjectives.values.flatten()).toSet()
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
            bandForQuality(quality, eligible, relationByKey, snapshot)
        }
        val taskBands = taskKeys.associateWith { task -> bandForTask(task, eligible, snapshot) }
        return QualityDoseHistory(bands, taskBands, weeks.size, excluded,
            listOf("COMPLETED_ISO_WEEKS_ONLY", "ACTIVE_WEEK_REQUIRES_CONFIRMED_CONTROLLABLE_RECORD",
                "QUALITY_TARGET_ENVELOPES_OVERLAP_AND_ARE_NOT_ADDITIVE_WORKLOAD_BUDGETS",
                "STRENGTH_HYPERTROPHY_HISTORY_IS_PRESCRIPTION_AWARE_WHERE_SUPPORTED",
                "AMBIGUOUS_REALIZED_STIMULUS_IS_NOT_ASSIGNED_TO_STRENGTH_OR_HYPERTROPHY",
                "POWER_RFD_SSC_RETAIN_CANONICAL_CAPABILITY_EXPOSURE"))
    }

    private fun bandForQuality(
        quality: TrainableQuality,
        eligible: List<WeekSlice>,
        relationByKey: Map<String, List<ExercisePhysicalQualityRelation>>,
        snapshot: PlanningHistorySnapshot
    ): SuccessfulDoseBand {
        fun values(weeks: List<WeekSlice>) = weeks.map { week ->
            val direct = week.rows.filter { row -> relationByKey[row.stableKey].orEmpty().any {
                it.qualityId == quality && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY &&
                    prescriptionCompatible(quality, row)
            } }
            val supportive = week.rows.filter { row -> relationByKey[row.stableKey].orEmpty().any {
                it.qualityId == quality && it.relationLevel == StimulusCapabilityLevel.SUPPORTIVE_CAPABILITY &&
                    prescriptionCompatible(quality, row)
            } }
            DoseValues(direct.size.toDouble(), direct.map { it.date }.toSet().size.toDouble(),
                supportive.size.toDouble(), supportive.map { it.date }.toSet().size.toDouble())
        }
        val recentStart = snapshot.cutoff.minusDays(27).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return band(values(eligible), values(eligible.filter { it.start >= recentStart }))
    }

    private fun bandForTask(task: String, eligible: List<WeekSlice>, snapshot: PlanningHistorySnapshot): SuccessfulDoseBand {
        fun values(weeks: List<WeekSlice>) = weeks.map { week ->
            val direct = week.rows.filter { task in snapshot.badmintonDirectObjectives[it.stableKey].orEmpty() }
            val supportive = week.rows.filter { task in snapshot.badmintonSupportiveObjectives[it.stableKey].orEmpty() }
            DoseValues(direct.size.toDouble(), direct.map { it.date }.toSet().size.toDouble(), supportive.size.toDouble(), supportive.map { it.date }.toSet().size.toDouble())
        }
        val recentStart = snapshot.cutoff.minusDays(27).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return band(values(eligible), values(eligible.filter { it.start >= recentStart }))
    }

    private fun band(normalValues: List<DoseValues>, currentValues: List<DoseValues>): SuccessfulDoseBand {
        val normalExposure = normalValues.filter { it.directUnits > 0.0 }
        val currentExposure = currentValues.filter { it.directUnits > 0.0 }
        val source: SuccessfulDoseSource
        val weeklyValues: List<DoseValues>
        val exposureValues: List<DoseValues>
        when {
            normalExposure.isNotEmpty() -> {
                source = SuccessfulDoseSource.NORMAL_COMPLETED_WEEKS
                weeklyValues = normalValues
                exposureValues = normalExposure
            }
            currentExposure.isNotEmpty() -> {
                source = SuccessfulDoseSource.CURRENT_28D_FALLBACK
                weeklyValues = currentValues
                exposureValues = currentExposure
            }
            normalValues.any { it.supportiveUnits > 0.0 } -> {
                source = SuccessfulDoseSource.RECENT_ACTIVE_WEEKS_FALLBACK
                weeklyValues = normalValues
                exposureValues = emptyList()
            }
            else -> {
                source = SuccessfulDoseSource.NO_PERSONAL_BASELINE
                weeklyValues = normalValues
                exposureValues = emptyList()
            }
        }
        val confidence = when {
            exposureValues.size >= 4 && consistent(exposureValues.map { it.directUnits }) -> PlanningConfidence.HIGH
            exposureValues.size >= 2 -> PlanningConfidence.MODERATE
            else -> PlanningConfidence.LOW
        }
        val weeklyUnitsQ25 = quantile(weeklyValues.map { it.directUnits }, .25)
        val weeklyUnitsMedian = quantile(weeklyValues.map { it.directUnits }, .50)
        val weeklyUnitsQ75 = quantile(weeklyValues.map { it.directUnits }, .75)
        val weeklySessionsQ25 = quantile(weeklyValues.map { it.directSessions }, .25)
        val weeklySessionsMedian = quantile(weeklyValues.map { it.directSessions }, .50)
        val weeklySessionsQ75 = quantile(weeklyValues.map { it.directSessions }, .75)
        val supportiveUnitsQ25 = quantile(weeklyValues.map { it.supportiveUnits }, .25)
        val supportiveUnitsMedian = quantile(weeklyValues.map { it.supportiveUnits }, .50)
        val supportiveUnitsQ75 = quantile(weeklyValues.map { it.supportiveUnits }, .75)
        val supportiveSessionsQ25 = quantile(weeklyValues.map { it.supportiveSessions }, .25)
        val supportiveSessionsMedian = quantile(weeklyValues.map { it.supportiveSessions }, .50)
        val supportiveSessionsQ75 = quantile(weeklyValues.map { it.supportiveSessions }, .75)
        val exposureFrequency = directFrequency(exposureValues.size, weeklyValues.size)
        return SuccessfulDoseBand(
            eligibleWeekCount = weeklyValues.size,
            directUnitsQ25 = weeklyUnitsQ25,
            directUnitsMedian = weeklyUnitsMedian,
            directUnitsQ75 = weeklyUnitsQ75,
            directSessionsQ25 = weeklySessionsQ25,
            directSessionsMedian = weeklySessionsMedian,
            directSessionsQ75 = weeklySessionsQ75,
            supportiveUnitsQ25 = supportiveUnitsQ25,
            supportiveUnitsMedian = supportiveUnitsMedian,
            supportiveUnitsQ75 = supportiveUnitsQ75,
            confidence = confidence,
            source = source,
            weeklyDirectUnitsQ25 = weeklyUnitsQ25,
            weeklyDirectUnitsMedian = weeklyUnitsMedian,
            weeklyDirectUnitsQ75 = weeklyUnitsQ75,
            weeklyDirectSessionsQ25 = weeklySessionsQ25,
            weeklyDirectSessionsMedian = weeklySessionsMedian,
            weeklyDirectSessionsQ75 = weeklySessionsQ75,
            exposureWeekDirectUnitsQ25 = quantile(exposureValues.map { it.directUnits }, .25),
            exposureWeekDirectUnitsMedian = quantile(exposureValues.map { it.directUnits }, .50),
            exposureWeekDirectUnitsQ75 = quantile(exposureValues.map { it.directUnits }, .75),
            exposureWeekDirectSessionsQ25 = quantile(exposureValues.map { it.directSessions }, .25),
            exposureWeekDirectSessionsMedian = quantile(exposureValues.map { it.directSessions }, .50),
            exposureWeekDirectSessionsQ75 = quantile(exposureValues.map { it.directSessions }, .75),
            directExposureWeekCount = exposureValues.size,
            directExposureWeekFrequency = exposureFrequency,
            weeklySupportiveUnitsQ25 = supportiveUnitsQ25,
            weeklySupportiveUnitsMedian = supportiveUnitsMedian,
            weeklySupportiveUnitsQ75 = supportiveUnitsQ75,
            weeklySupportiveSessionsQ25 = supportiveSessionsQ25,
            weeklySupportiveSessionsMedian = supportiveSessionsMedian,
            weeklySupportiveSessionsQ75 = supportiveSessionsQ75
        )
    }

    private fun prescriptionCompatible(quality: TrainableQuality, row: PlanningSetRecord): Boolean = when (quality) {
        TrainableQuality.STRENGTH -> provisionalRealizedStimulusClass(row) == RealizedStimulusClass.STRENGTH_LIKE
        TrainableQuality.HYPERTROPHY -> provisionalRealizedStimulusClass(row) == RealizedStimulusClass.HYPERTROPHY_LIKE
        else -> true
    }

    private fun directFrequency(exposureWeeks: Int, eligibleWeeks: Int): Double? =
        if (eligibleWeeks == 0) null else exposureWeeks.toDouble() / eligibleWeeks.toDouble()

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
            val targetBand = band?.takeIf { useBand }
            QualityStimulusTarget(decision.quality, decision.action, decision.priority, band,
                targetBand?.weeklyDirectUnitsQ25, targetBand?.weeklyDirectUnitsMedian, targetBand?.weeklyDirectUnitsQ75,
                targetBand?.weeklyDirectSessionsQ25, targetBand?.weeklyDirectSessionsMedian, targetBand?.weeklyDirectSessionsQ75,
                numeric, decision.confidence, decision.reasonCodes + reasonsFor(decision.action, numeric, band), decision.evidence,
                targetWeeklyDirectUnitsMin = targetBand?.weeklyDirectUnitsQ25,
                targetWeeklyDirectUnitsPreferred = targetBand?.weeklyDirectUnitsMedian,
                targetWeeklyDirectUnitsMax = targetBand?.weeklyDirectUnitsQ75,
                targetWeeklyDirectSessionsMin = targetBand?.weeklyDirectSessionsQ25,
                targetWeeklyDirectSessionsPreferred = targetBand?.weeklyDirectSessionsMedian,
                targetWeeklyDirectSessionsMax = targetBand?.weeklyDirectSessionsQ75,
                targetExposureWeekDirectUnitsMin = targetBand?.exposureWeekDirectUnitsQ25,
                targetExposureWeekDirectUnitsPreferred = targetBand?.exposureWeekDirectUnitsMedian,
                targetExposureWeekDirectUnitsMax = targetBand?.exposureWeekDirectUnitsQ75,
                targetExposureWeekDirectSessionsMin = targetBand?.exposureWeekDirectSessionsQ25,
                targetExposureWeekDirectSessionsPreferred = targetBand?.exposureWeekDirectSessionsMedian,
                targetExposureWeekDirectSessionsMax = targetBand?.exposureWeekDirectSessionsQ75,
                targetExposureWeekFrequency = targetBand?.directExposureWeekFrequency)
        }
        val taskTargets = portfolio.taskDecisions.map { decision ->
            val band = history.taskBands[decision.task]
            val maintenanceBaseline = decision.action in setOf(TargetStimulusAction.HOLD_SUCCESSFUL_DOSE, TargetStimulusAction.HOLD_DOSE_ALLOW_PROGRESSION) &&
                band?.hasPersonalDirectBaseline == true
            val numeric = if ((decision.explicitUserTaskPriority || maintenanceBaseline) && band?.hasPersonalDirectBaseline == true) {
                numericAuthority(decision.action, band)
            } else TargetNumericAuthority.DIRECTION_ONLY
            val useBand = (decision.explicitUserTaskPriority || maintenanceBaseline) && numeric != TargetNumericAuthority.DIRECTION_ONLY
            val targetBand = band?.takeIf { useBand }
            TaskStimulusTarget(decision.task, decision.action, decision.priority, band,
                targetBand?.weeklyDirectUnitsQ25, targetBand?.weeklyDirectUnitsMedian, targetBand?.weeklyDirectUnitsQ75,
                numeric, decision.confidence, decision.explicitUserTaskPriority,
                decision.reasonCodes + "NO_EXPLICIT_BADMINTON_TASK_PRIORITY", decision.evidence,
                targetWeeklyDirectUnitsMin = targetBand?.weeklyDirectUnitsQ25,
                targetWeeklyDirectUnitsPreferred = targetBand?.weeklyDirectUnitsMedian,
                targetWeeklyDirectUnitsMax = targetBand?.weeklyDirectUnitsQ75,
                targetExposureWeekDirectUnitsMin = targetBand?.exposureWeekDirectUnitsQ25,
                targetExposureWeekDirectUnitsPreferred = targetBand?.exposureWeekDirectUnitsMedian,
                targetExposureWeekDirectUnitsMax = targetBand?.exposureWeekDirectUnitsQ75,
                targetExposureWeekFrequency = targetBand?.directExposureWeekFrequency)
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
            val planned = qualityCounts[target.quality] ?: 0
            QualityTargetComparison(target.quality, planned, target.targetWeeklyDirectUnitsMin,
                target.targetWeeklyDirectUnitsPreferred, target.targetWeeklyDirectUnitsMax,
                status(target.targetWeeklyDirectUnitsMin, target.targetWeeklyDirectUnitsMax, planned, target.numericAuthority, target.action),
                targetWeeklyMin = target.targetWeeklyDirectUnitsMin,
                targetWeeklyPreferred = target.targetWeeklyDirectUnitsPreferred,
                targetWeeklyMax = target.targetWeeklyDirectUnitsMax,
                targetExposureWeekPreferred = target.targetExposureWeekDirectUnitsPreferred,
                targetExposureWeekFrequency = target.targetExposureWeekFrequency)
        }
        val taskCounts = plan.taskTargets.associate { target ->
            target.task to averageWeeklyUnits(generated.items.filter { item ->
                snapshot.activityKind(item.exerciseStableKey) in setOf(PlannedActivityKind.STRUCTURED_BADMINTON_DRILL, PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL, PlannedActivityKind.RESISTANCE) &&
                    target.task in snapshot.badmintonDirectObjectives[item.exerciseStableKey].orEmpty()
            })
        }
        val taskComparisons = plan.taskTargets.map { target ->
            val planned = taskCounts[target.task] ?: 0
            TaskTargetComparison(target.task, planned, target.targetWeeklyDirectUnitsMin, target.targetWeeklyDirectUnitsPreferred,
                target.targetWeeklyDirectUnitsMax, status(target.targetWeeklyDirectUnitsMin, target.targetWeeklyDirectUnitsMax, planned, target.numericAuthority, target.action),
                targetWeeklyMin = target.targetWeeklyDirectUnitsMin,
                targetWeeklyPreferred = target.targetWeeklyDirectUnitsPreferred,
                targetWeeklyMax = target.targetWeeklyDirectUnitsMax,
                targetExposureWeekPreferred = target.targetExposureWeekDirectUnitsPreferred,
                targetExposureWeekFrequency = target.targetExposureWeekFrequency)
        }
        return TargetPlanComparison(qualityComparisons, taskComparisons,
            listOf("TARGET_HISTORY_IS_PRESCRIPTION_AWARE_WHERE_SUPPORTED",
                "PLANNED_PROGRAM_SIDE_IS_CAPABILITY_PROJECTION_ONLY",
                "CAPABILITY_COMPARISON_DOES_NOT_PROVE_FUTURE_REALIZED_STIMULUS",
                "NOT_PROOF_OF_REALIZED_PHYSIOLOGICAL_STIMULUS",
                "PLANNED_CAPABILITY_COVERAGE_IS_AN_AUDIT_APPROXIMATION",
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
        .put("targetWeeklyDirectUnitsMin", it.targetWeeklyDirectUnitsMin)
        .put("targetWeeklyDirectUnitsPreferred", it.targetWeeklyDirectUnitsPreferred)
        .put("targetWeeklyDirectUnitsMax", it.targetWeeklyDirectUnitsMax)
        .put("targetDirectSessionsMin", it.targetDirectSessionsMin).put("targetDirectSessionsPreferred", it.targetDirectSessionsPreferred)
        .put("targetDirectSessionsMax", it.targetDirectSessionsMax).put("numericAuthority", it.numericAuthority.name)
        .put("targetWeeklyDirectSessionsMin", it.targetWeeklyDirectSessionsMin)
        .put("targetWeeklyDirectSessionsPreferred", it.targetWeeklyDirectSessionsPreferred)
        .put("targetWeeklyDirectSessionsMax", it.targetWeeklyDirectSessionsMax)
        .put("targetExposureWeekDirectUnitsMin", it.targetExposureWeekDirectUnitsMin)
        .put("targetExposureWeekDirectUnitsPreferred", it.targetExposureWeekDirectUnitsPreferred)
        .put("targetExposureWeekDirectUnitsMax", it.targetExposureWeekDirectUnitsMax)
        .put("targetExposureWeekDirectSessionsMin", it.targetExposureWeekDirectSessionsMin)
        .put("targetExposureWeekDirectSessionsPreferred", it.targetExposureWeekDirectSessionsPreferred)
        .put("targetExposureWeekDirectSessionsMax", it.targetExposureWeekDirectSessionsMax)
        .put("targetExposureWeekFrequency", it.targetExposureWeekFrequency)
        .put("confidence", it.confidence.name).put("reasonCodes", JSONArray(it.reasonCodes)).put("evidence", JSONArray(it.evidence))
    }))
    .put("taskTargets", JSONArray(taskTargets.map { JSONObject()
        .put("task", it.task).put("action", it.action.name).put("priority", it.priority.name)
        .put("baseline", it.baseline?.toJson()).put("targetDirectUnitsMin", it.targetDirectUnitsMin)
        .put("targetDirectUnitsPreferred", it.targetDirectUnitsPreferred).put("targetDirectUnitsMax", it.targetDirectUnitsMax)
        .put("targetWeeklyDirectUnitsMin", it.targetWeeklyDirectUnitsMin)
        .put("targetWeeklyDirectUnitsPreferred", it.targetWeeklyDirectUnitsPreferred)
        .put("targetWeeklyDirectUnitsMax", it.targetWeeklyDirectUnitsMax)
        .put("targetExposureWeekDirectUnitsMin", it.targetExposureWeekDirectUnitsMin)
        .put("targetExposureWeekDirectUnitsPreferred", it.targetExposureWeekDirectUnitsPreferred)
        .put("targetExposureWeekDirectUnitsMax", it.targetExposureWeekDirectUnitsMax)
        .put("targetExposureWeekFrequency", it.targetExposureWeekFrequency)
        .put("numericAuthority", it.numericAuthority.name).put("confidence", it.confidence.name)
        .put("explicitUserTaskPriority", it.explicitUserTaskPriority)
        .put("reasonCodes", JSONArray(it.reasonCodes)).put("evidence", JSONArray(it.evidence))
    }))

private fun SuccessfulDoseBand.toJson(): JSONObject = JSONObject()
    .put("eligibleWeekCount", eligibleWeekCount)
    .put("directUnitsQ25", directUnitsQ25).put("directUnitsMedian", directUnitsMedian).put("directUnitsQ75", directUnitsQ75)
    .put("directSessionsQ25", directSessionsQ25).put("directSessionsMedian", directSessionsMedian).put("directSessionsQ75", directSessionsQ75)
    .put("supportiveUnitsQ25", supportiveUnitsQ25).put("supportiveUnitsMedian", supportiveUnitsMedian).put("supportiveUnitsQ75", supportiveUnitsQ75)
    .put("weeklyDirectUnitsQ25", weeklyDirectUnitsQ25).put("weeklyDirectUnitsMedian", weeklyDirectUnitsMedian).put("weeklyDirectUnitsQ75", weeklyDirectUnitsQ75)
    .put("weeklyDirectSessionsQ25", weeklyDirectSessionsQ25).put("weeklyDirectSessionsMedian", weeklyDirectSessionsMedian).put("weeklyDirectSessionsQ75", weeklyDirectSessionsQ75)
    .put("exposureWeekDirectUnitsQ25", exposureWeekDirectUnitsQ25).put("exposureWeekDirectUnitsMedian", exposureWeekDirectUnitsMedian).put("exposureWeekDirectUnitsQ75", exposureWeekDirectUnitsQ75)
    .put("exposureWeekDirectSessionsQ25", exposureWeekDirectSessionsQ25).put("exposureWeekDirectSessionsMedian", exposureWeekDirectSessionsMedian).put("exposureWeekDirectSessionsQ75", exposureWeekDirectSessionsQ75)
    .put("directExposureWeekCount", directExposureWeekCount).put("directExposureWeekFrequency", directExposureWeekFrequency)
    .put("weeklySupportiveUnitsQ25", weeklySupportiveUnitsQ25).put("weeklySupportiveUnitsMedian", weeklySupportiveUnitsMedian).put("weeklySupportiveUnitsQ75", weeklySupportiveUnitsQ75)
    .put("weeklySupportiveSessionsQ25", weeklySupportiveSessionsQ25).put("weeklySupportiveSessionsMedian", weeklySupportiveSessionsMedian).put("weeklySupportiveSessionsQ75", weeklySupportiveSessionsQ75)
    .put("confidence", confidence.name).put("source", source.name)

internal fun TargetPlanComparison.toJson(): JSONObject = JSONObject()
    .put("shadowOnly", shadowOnly).put("prescriptionAuthority", prescriptionAuthority)
    .put("notes", JSONArray(notes))
    .put("qualityComparisons", JSONArray(qualityComparisons.map { JSONObject()
        .put("quality", it.quality.name).put("plannedCapabilityUnits", it.plannedCapabilityUnits)
        .put("targetMin", it.targetMin).put("targetPreferred", it.targetPreferred).put("targetMax", it.targetMax)
        .put("targetWeeklyMin", it.targetWeeklyMin).put("targetWeeklyPreferred", it.targetWeeklyPreferred).put("targetWeeklyMax", it.targetWeeklyMax)
        .put("targetExposureWeekPreferred", it.targetExposureWeekPreferred).put("targetExposureWeekFrequency", it.targetExposureWeekFrequency)
        .put("status", it.status.name).put("reasonCodes", JSONArray(it.reasonCodes))
    }))
    .put("taskComparisons", JSONArray(taskComparisons.map { JSONObject()
        .put("task", it.task).put("plannedCapabilityUnits", it.plannedCapabilityUnits)
        .put("targetMin", it.targetMin).put("targetPreferred", it.targetPreferred).put("targetMax", it.targetMax)
        .put("targetWeeklyMin", it.targetWeeklyMin).put("targetWeeklyPreferred", it.targetWeeklyPreferred).put("targetWeeklyMax", it.targetWeeklyMax)
        .put("targetExposureWeekPreferred", it.targetExposureWeekPreferred).put("targetExposureWeekFrequency", it.targetExposureWeekFrequency)
        .put("status", it.status.name).put("reasonCodes", JSONArray(it.reasonCodes))
    }))

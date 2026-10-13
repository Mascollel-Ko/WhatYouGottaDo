package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonRequest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/**
 * Demonstrated normal resistance workload.  This is an engineering planning
 * authority; it is not MRV, a physiological optimum, or a medical clearance.
 */
data class ResistanceVolumeBudget(
    val resistanceNormalWeekCount: Int = 0,
    val resistanceActiveWeekCount: Int = 0,
    val resistanceWeeklyQ25: Double = 0.0,
    val resistanceWeeklyMedian: Double = 0.0,
    val resistanceWeeklyQ75: Double = 0.0,
    val resistanceBaselineSource: String = "ANCHOR_DERIVED_FALLBACK",
    val resistanceBaselineSets: Double = 0.0,
    val globalDoseFactor: Double = 1.0,
    val resistanceCoreTarget: Int = 0,
    val resistanceDayRelease: Int = 0,
    val resistanceTimeCeiling: Int = 0,
    val resistanceUsefulDemand: Int = 0,
    val resistanceTargetSets: Int = 0,
    val secondsPerResistanceSet: Double = 0.0,
    val resistanceAuthorizedBeforeCompletion: Int = 0,
    val resistanceCompletionAddedSets: Int = 0,
    val resistanceFinalSets: Int = 0
) {
    val q25: Double get() = resistanceWeeklyQ25
    val median: Double get() = resistanceWeeklyMedian
    val q75: Double get() = resistanceWeeklyQ75
}

data class PerformanceVolumeBudget(
    val targetBouts: Int = 0,
    val authorizedBouts: Int = 0,
    val finalBouts: Int = 0
)

/** Domain targets remain separate until the final scheduling gates. */
data class DomainVolumeBudget(
    val resistance: ResistanceVolumeBudget = ResistanceVolumeBudget(),
    val structuredBadminton: PerformanceVolumeBudget = PerformanceVolumeBudget(),
    val athleticPerformance: PerformanceVolumeBudget = PerformanceVolumeBudget()
) {
    val totalTargetUnits: Int
        get() = resistance.resistanceTargetSets + structuredBadminton.targetBouts + athleticPerformance.targetBouts
}

data class CourtLoadTrace(
    val baselineLoad: Double = 0.0,
    val recentLoad: Double = 0.0,
    val deviation: Double = 0.0
)

internal object ResistanceVolumePlanner {
    private val resistanceOnly = setOf(PlannedActivityKind.RESISTANCE)

    fun completeWeekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weeklyResistanceSets(snapshot: PlanningHistorySnapshot): Map<LocalDate, Int> {
        val lower = snapshot.cutoff.minusDays(55)
        val completeEnd = snapshot.cutoff.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return snapshot.allConfirmedSets
            .asSequence()
            .filter { it.date >= lower && it.date <= completeEnd && snapshot.activityKind(it.stableKey) in resistanceOnly }
            .groupBy { completeWeekStart(it.date) }
            .mapValues { (_, rows) -> rows.size }
            .toSortedMap()
    }

    fun courtTrace(snapshot: PlanningHistorySnapshot): CourtLoadTrace {
        val values = snapshot.weeklyCourtLoad
            .filterKeys { it >= snapshot.cutoff.minusDays(55) && !it.isAfter(snapshot.cutoff) }
            .toSortedMap()
            .values
            .filter(Double::isFinite)
            .toList()
        val recent = median(values.takeLast(2)) ?: 0.0
        val baselineValues = values.dropLast(minOf(2, values.size))
        val baseline = median(baselineValues)
            ?: median(values)
            ?: 0.0
        val deviation = if (baseline <= 0.0) 0.0 else ((recent - baseline) / baseline).coerceIn(0.0, 1.0)
        return CourtLoadTrace(baseline, recent, deviation)
    }

    fun plan(
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        request: ProgramSkeletonRequest,
        usefulDemand: Int,
        anchorFallback: Double = 0.0,
        traceId: String? = null
    ): ResistanceVolumeBudget {
        val planningStarted = System.nanoTime()
        val weekly = weeklyResistanceSets(snapshot)
        val contextByWeek = state.trainingStateAssessment?.weeklyContext.orEmpty().associateBy { it.start }
        // The canonical analyzer supplies a context for every observed week.
        // A small synthetic or pre-context snapshot has no such annotation;
        // absent an exclusion, treat that complete week as normal evidence.
        val normal = weekly.filter { (contextByWeek[it.key]?.context == WeeklyTrainingContext.NORMAL || contextByWeek[it.key] == null) &&
            contextByWeek[it.key]?.excludedFromTolerance != true && it.value > 0 }.values.toList()
        val active = weekly.filter { contextByWeek[it.key]?.excludedFromTolerance != true && it.value > 0 }.values.toList()
        val recent = weekly.values.filter { it > 0 }
        val baselineValues: List<Int>
        val baseline: Double
        val source: String
        var baselineWeekStarts: Set<LocalDate> = emptySet()
        when {
            normal.isNotEmpty() -> {
                baselineValues = normal
                baselineWeekStarts = weekly.filterKeys { week ->
                    (contextByWeek[week]?.context == WeeklyTrainingContext.NORMAL || contextByWeek[week] == null) &&
                        contextByWeek[week]?.excludedFromTolerance != true
                }.keys
                baseline = median(normal.map(Int::toDouble))!!
                source = "NORMAL_COMPLETE_WEEK_MEDIAN"
            }
            active.isNotEmpty() -> {
                baselineValues = active
                baselineWeekStarts = weekly.filterKeys { week -> contextByWeek[week]?.excludedFromTolerance != true }.keys
                baseline = median(active.map(Int::toDouble))!!
                source = "ACTIVE_COMPLETE_WEEK_MEDIAN"
            }
            recent.isNotEmpty() -> {
                baselineValues = recent
                baselineWeekStarts = weekly.keys
                baseline = median(recent.map(Int::toDouble))!!
                source = "RECENT_RESISTANCE_WEEK_MEDIAN"
            }
            else -> {
                baselineValues = emptyList()
                baseline = anchorFallback.coerceAtLeast(0.0)
                source = "ANCHOR_DERIVED_FALLBACK"
            }
        }
        val q25 = quantile(baselineValues, .25)
        val q50 = quantile(baselineValues, .50)
        val q75 = quantile(baselineValues, .75)
        val global = (state.trainingStateAssessment?.globalDoseFactor ?: 1.0).coerceIn(.80, 1.0)
        val core = (baseline * global).roundToInt().coerceAtLeast(0)
        val normalDayCounts = weekly.keys.filter { (contextByWeek[it]?.context == WeeklyTrainingContext.NORMAL || contextByWeek[it] == null) &&
            contextByWeek[it]?.excludedFromTolerance != true }
            .map { week -> snapshot.allConfirmedSets.filter { completeWeekStart(it.date) == week && snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE }
                .map(PlanningSetRecord::date).distinct().size }
            .filter { it > 0 }
        val activeDayCounts = weekly.keys.map { week -> snapshot.allConfirmedSets.filter { completeWeekStart(it.date) == week &&
            snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE }.map(PlanningSetRecord::date).distinct().size }
            .filter { it > 0 }
        val dBase = (median((normalDayCounts.ifEmpty { activeDayCounts }).map(Int::toDouble)) ?: 1.0).coerceAtLeast(1.0)
        val setsPerDay = baseline / dBase
        val extraDays = (request.weeklyTrainingDays - dBase).coerceAtLeast(0.0)
        val release = (core + extraDays * setsPerDay).roundToInt().coerceAtLeast(core)
        val resistanceRows = snapshot.allConfirmedSets.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE &&
            !it.date.isBefore(snapshot.cutoff.minusDays(55)) && !it.date.isAfter(snapshot.cutoff) }
        val sessionSeconds = resistanceRows.groupBy(PlanningSetRecord::date).mapNotNull { (_, rows) ->
            val count = rows.size
            if (count == 0) null else rows.sumOf { if (it.seconds > 0) it.seconds else 45 }.toDouble() /
                count.coerceAtLeast(1)
        }
        val secondsPerSet = median(sessionSeconds)?.coerceAtLeast(45.0) ?: 135.0
        val availableSeconds = request.weeklyTrainingDays * request.sessionMinutes * 60
        val timeCeiling = floor(availableSeconds / secondsPerSet).toInt().coerceAtLeast(0)
        val target = minOf(release, timeCeiling, usefulDemand.coerceAtLeast(0))
        val lower = snapshot.cutoff.minusDays(55)
        val completeEnd = snapshot.cutoff.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val firstWeek = lower.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val allWeeks = generateSequence(firstWeek) { previous -> previous.plusWeeks(1).takeIf { !it.isAfter(completeEnd) } }
            .filter { !it.isAfter(completeEnd) }.toList()
        val selectedBaselineWeeks = baselineWeekStarts
        val resistanceRowsByWeek = snapshot.allConfirmedSets.asSequence()
            .filter { snapshot.activityKind(it.stableKey) in resistanceOnly }
            .groupBy { completeWeekStart(it.date) }
        val weeklyDetails = JSONArray(allWeeks.map { week ->
            val count = weekly[week] ?: 0
            val weekRows = resistanceRowsByWeek[week].orEmpty().filter { it.date >= lower && it.date <= completeEnd }
            val context = contextByWeek[week]
            val included = week in selectedBaselineWeeks
            val excludedReason = when {
                count == 0 -> "NO_CONFIRMED_RESISTANCE_SET_ROWS_NOT_A_ZERO_TRAINING_ASSERTION"
                context?.excludedFromTolerance == true -> "WEEK_EXCLUDED_FROM_TOLERANCE"
                included -> null
                context?.context != null && context.context != WeeklyTrainingContext.NORMAL -> "NON_NORMAL_WEEK_OUTSIDE_SELECTED_BASELINE"
                else -> "NOT_SELECTED_BY_BASELINE_FALLBACK_POLICY"
            }
            JSONObject().put("weekStart", week.toString()).put("weekEnd", week.plusDays(6).toString())
                .put("resistancePhysicalSetCount", count).put("confirmedResistanceRowsPresent", count > 0)
                .put("resistanceTrainingDayCount", weekRows.map { it.date }.distinct().size)
                .put("resistanceTrainingDays", JSONArray(weekRows.map { it.date }.distinct().sorted().map(LocalDate::toString)))
                .put("trainingContext", context?.context?.name ?: "UNANNOTATED")
                .put("contextCause", context?.cause?.name ?: "UNKNOWN")
                .put("contextSource", context?.source?.name ?: "UNRESOLVED")
                .put("excludedFromTolerance", context?.excludedFromTolerance ?: false)
                .put("contextReasonCodes", JSONArray(context?.reasonCodes.orEmpty()))
                .put("includedInSelectedBaseline", included).put("exclusionReason", excludedReason)
        })
        val outsideWindowConfirmedSets = snapshot.allConfirmedSets.count {
            snapshot.activityKind(it.stableKey) in resistanceOnly && (it.date.isBefore(lower) || it.date.isAfter(completeEnd))
        }
        val outsideWindowRowsByWeek = snapshot.allConfirmedSets.asSequence().filter {
            snapshot.activityKind(it.stableKey) in resistanceOnly && (it.date.isBefore(lower) || it.date.isAfter(completeEnd))
        }.groupBy { completeWeekStart(it.date) }
        val outsideWindowWeeklyCounts = JSONArray(outsideWindowRowsByWeek.toSortedMap().map { (week, rows) -> JSONObject().put("weekStart", week.toString())
                .put("physicalSetCount", rows.size).put("trainingDayCount", rows.map { it.date }.distinct().size)
                .put("exclusionReason", "OUTSIDE_56_DAY_RESISTANCE_PLANNER_WINDOW") })
        GenerationDecisionTraceStore.record(traceId, "RESISTANCE_VOLUME_WEEKS", "ResistanceVolumePlanner.plan/weeklyResistanceSets + baseline selection",
            inputs = JSONObject().put("cutoff", snapshot.cutoff.toString()).put("windowStart", lower.toString())
                .put("completeWeekEnd", completeEnd.toString()).put("windowDays", 56)
                .put("outsideWindowConfirmedResistanceSetCount", outsideWindowConfirmedSets),
            criteria = JSONObject().put("physicalSetsOnly", true).put("zeroSetWeekIncludedInProductionBaseline", false)
                .put("baselinePolicy", "NORMAL_COMPLETE_WEEK_MEDIAN > ACTIVE_COMPLETE_WEEK_MEDIAN > RECENT_RESISTANCE_WEEK_MEDIAN > ANCHOR_DERIVED_FALLBACK"),
            comparisons = JSONObject().put("weeklyEvidence", weeklyDetails)
                .put("olderOrIncompleteWeeks", outsideWindowWeeklyCounts)
                .put("productionInputWeeks", JSONArray(weekly.keys.map(LocalDate::toString)))
                .put("selectedBaselineWeeks", JSONArray(selectedBaselineWeeks.sorted().map(LocalDate::toString)))
                .put("selectedBaselineValuesPhysicalSets", JSONArray(baselineValues))
                .put("selectedBaselineMeanPhysicalSets", baselineValues.takeIf { it.isNotEmpty() }?.average())
                .put("selectedBaselineMedianPhysicalSets", baseline)
                .put("selectedBaselineQ25PhysicalSets", q25).put("selectedBaselineQ75PhysicalSets", q75)
                .put("normalTrainingDaysPerWeekMedian", dBase).put("setsPerTrainingDay", setsPerDay)
                .put("secondsPerSetMedian", secondsPerSet).put("availableWeeklySeconds", availableSeconds),
            decision = "RESISTANCE_VOLUME_BASELINE_AND_LIMITS_COMPUTED",
            reasonCodes = listOf(source) + if (outsideWindowConfirmedSets > 0) listOf("OLDER_THAN_56_DAYS_EXCLUDED_FROM_PLANNER_BASELINE") else emptyList(),
            nextStageInputs = JSONObject().put("baseline", baseline).put("globalDoseFactor", global)
                .put("coreTarget", core).put("dayRelease", release).put("timeCeiling", timeCeiling)
                .put("usefulDemand", usefulDemand.coerceAtLeast(0)).put("targetSets", target)
                .put("sequentialLimits", JSONArray(listOf(
                    JSONObject().put("limit", "GLOBAL_DOSE_FACTOR").put("before", baseline)
                        .put("after", core).put("reduction", (baseline - core).coerceAtLeast(0.0)),
                    JSONObject().put("limit", "DAY_RELEASE").put("before", core)
                        .put("after", release).put("reduction", (core - release).coerceAtLeast(0)),
                    JSONObject().put("limit", "SESSION_TIME_CEILING").put("before", release)
                        .put("after", minOf(release, timeCeiling)).put("reduction", (release - timeCeiling).coerceAtLeast(0)),
                    JSONObject().put("limit", "USEFUL_DEMAND").put("before", minOf(release, timeCeiling))
                        .put("after", target).put("reduction", (minOf(release, timeCeiling) - target).coerceAtLeast(0))
                )))
                .put("bindingLimits", JSONArray(buildList {
                    if (release <= timeCeiling && release <= usefulDemand.coerceAtLeast(0)) add("DAY_RELEASE")
                    if (timeCeiling <= release && timeCeiling <= usefulDemand.coerceAtLeast(0)) add("SESSION_TIME_CEILING")
                    if (usefulDemand.coerceAtLeast(0) <= release && usefulDemand.coerceAtLeast(0) <= timeCeiling) add("USEFUL_DEMAND")
                })),
            startedNanos = planningStarted)
        return ResistanceVolumeBudget(
            resistanceNormalWeekCount = normal.size,
            resistanceActiveWeekCount = active.size,
            resistanceWeeklyQ25 = q25,
            resistanceWeeklyMedian = q50,
            resistanceWeeklyQ75 = q75,
            resistanceBaselineSource = source,
            resistanceBaselineSets = baseline,
            globalDoseFactor = global,
            resistanceCoreTarget = core,
            resistanceDayRelease = release,
            resistanceTimeCeiling = timeCeiling,
            resistanceUsefulDemand = usefulDemand.coerceAtLeast(0),
            resistanceTargetSets = target,
            secondsPerResistanceSet = secondsPerSet,
            resistanceAuthorizedBeforeCompletion = 0,
            resistanceCompletionAddedSets = 0,
            resistanceFinalSets = 0
        )
    }

    private fun median(values: List<Double>): Double? = trainingMedian(values)
    private fun quantile(values: List<Int>, q: Double): Double = quantileDouble(values.map(Int::toDouble), q)
    private fun quantileDouble(values: List<Double>, q: Double): Double = if (values.isEmpty()) 0.0 else {
        val sorted = values.sorted()
        sorted[((sorted.size - 1) * q).roundToInt()]
    }
}

package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSkeletonRequest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
import kotlin.math.roundToInt

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
        anchorFallback: Double = 0.0
    ): ResistanceVolumeBudget {
        val weekly = weeklyResistanceSets(snapshot)
        val contextByWeek = state.trainingStateAssessment?.weeklyContext.orEmpty().associateBy { it.start }
        val normal = weekly.filter { contextByWeek[it.key]?.context == WeeklyTrainingContext.NORMAL &&
            contextByWeek[it.key]?.excludedFromTolerance != true && it.value > 0 }.values.toList()
        val active = weekly.filter { contextByWeek[it.key]?.excludedFromTolerance != true && it.value > 0 }.values.toList()
        val recent = weekly.values.filter { it > 0 }
        val baselineValues: List<Int>
        val baseline: Double
        val source: String
        when {
            normal.isNotEmpty() -> {
                baselineValues = normal
                baseline = median(normal.map(Int::toDouble))!!
                source = "NORMAL_COMPLETE_WEEK_MEDIAN"
            }
            active.isNotEmpty() -> {
                baselineValues = active
                baseline = median(active.map(Int::toDouble))!!
                source = "ACTIVE_COMPLETE_WEEK_MEDIAN"
            }
            recent.isNotEmpty() -> {
                baselineValues = recent
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
        val normalDayCounts = weekly.keys.filter { it in contextByWeek &&
            contextByWeek[it]?.context == WeeklyTrainingContext.NORMAL && contextByWeek[it]?.excludedFromTolerance != true }
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

package com.training.trackplanner.data.personalized

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

enum class TrainingState { PRODUCTIVE_HIGH_LOAD, TOLERATED_HIGH_LOAD, PRODUCTIVE_NORMAL, STABLE, ACCUMULATING_STRAIN, MALADAPTATION_PATTERN, HARD_RESTRICTION, UNCERTAIN }
enum class WeeklyTrainingContext { NORMAL, EXTERNAL_INTERRUPTION_LIKELY, EVENT_OR_TAPER_LIKELY, RECOVERY_REDUCTION_LIKELY, UNEXPLAINED_LOW_WEEK }
enum class InterruptionCause { EXTERNAL, EVENT, FATIGUE, MIXED, UNSURE }
enum class InterruptionFrequency { NEVER, RARE, MONTHLY, FREQUENT, VERY_FREQUENT, UNSURE }
enum class ScheduleTier { CORE_MUST_DO, IMPORTANT, OPTIONAL_CAPACITY }

/** Observations from the existing canonical OFI series, never a second fatigue engine. */
data class PlanningDailyStrain(val date: LocalDate, val overallFatigueIndex: Double,
    val highForceNeuralScore: Double, val systemicMuscularScore: Double, val localMuscularScore: Double,
    val highSpeedScore: Double, val reactiveScore: Double, val recoveryPressureScore: Double,
    val confirmedTrainingLoad: Double) {
    val axes: List<Double> get() = listOf(highForceNeuralScore, systemicMuscularScore, localMuscularScore, highSpeedScore, reactiveScore)
}
data class TrainingStateInput(val cutoff: LocalDate, val records: List<PlanningSetRecord>,
    val daily: List<PlanningDailyStrain>, val domains: Map<String, PlannedActivityKind>,
    val coverage: Map<String, MovementCoverage>, val loadSupportedKeys: Set<String>,
    val signals: Map<String, CanonicalStrengthSignal>, val recovery: PlanningRecoverySignals,
    val restSeconds: Map<String, Int> = emptyMap(), val weeklyCourtLoad: Map<LocalDate, Double> = emptyMap(),
    val hardRestrictedModes: Set<String> = emptySet(), val interruptionCause: InterruptionCause = InterruptionCause.UNSURE,
    val interruptionFrequency: InterruptionFrequency = InterruptionFrequency.UNSURE)

data class LongitudinalStrainProfile(val ofi7: Double?, val baselineMedian: Double?, val baselineMad: Double?,
    val robustScale: Double?, val personalZ: Double?, val absoluteStrain: Double?, val relativeStrain: Double?,
    val persistence: Double?, val ofiTrend: Double?, val maxAxisElevation: Double?, val elevatedAxisCount: Int,
    val baselineDays: Int, val recentDays: Int, val strainScore: Double?)
data class ExerciseAdaptationEvidence(val stableKey: String, val movement: MovementCoverage,
    val posteriorChangePercent: Double?, val posteriorResponse: Double?, val posteriorConfidence: Double,
    val matchedLoadChange: Double?, val matchedRepChange: Double?, val matchedRpeEfficiency: Double?,
    val rawPerformanceResponse: Double?, val validRawComparisonCount: Int, val rawConfidence: Double,
    val responseScore: Double?, val confidence: Double)
data class MovementAdaptationEvidence(val response: Double, val confidence: Double)
data class LongitudinalAdaptationProfile(val exercises: List<ExerciseAdaptationEvidence>,
    val movements: Map<MovementCoverage, MovementAdaptationEvidence>, val globalAdaptationResponse: Double?,
    val positiveBreadth: Double?, val negativeBreadth: Double?, val rpeDrift: Double?)
data class ToleranceWindow(val units: Double?, val days: Double?, val density: Double?)
data class WorkloadToleranceProfile(val current: ToleranceWindow, val prior: ToleranceWindow,
    val workloadStability: Double?, val dayStability: Double?, val densityStability: Double?, val rpeStability: Double?, val score: Double?)
data class WeeklyWorkloadEvidence(val start: LocalDate, val end: LocalDate, val units: Int, val minutes: Double,
    val days: Int, val courtLoad: Double, val medianRpe: Double?, val performanceResponse: Double?,
    val negativeBreadth: Double?, val rpeDrift: Double?, val context: WeeklyTrainingContext = WeeklyTrainingContext.NORMAL,
    val low: Boolean = false, val localTypicalUnits: Double? = null, val reasonCodes: List<String> = emptyList(),
    val excludedFromTolerance: Boolean = false)
data class StableWorkloadRun(val start: LocalDate, val end: LocalDate, val durationWeeks: Int, val units: Double,
    val minutes: Double, val days: Double, val response: Double?, val rpeDrift: Double?, val weight: Double)
/** Demonstrated repeatable workload, NOT MRV, maximum safe volume, or optimal volume. */
data class SustainableWorkloadEvidence(val sustainableWeeklyControllableUnits: Double?, val sustainableWeeklyMinutes: Double?,
    val sustainableDaysPerWeek: Double?, val longestStableRunWeeks: Int, val successfulStableRunCount: Int,
    val observationWeeks: Int, val confidence: PlanningConfidence, val runs: List<StableWorkloadRun>,
    val rawWeeklyMean: Double?, val normalWeekMedian: Double?, val questionRequired: Boolean, val robustSchedule: Boolean,
    val reasonCodes: List<String>)
data class TrainingStateAssessment(val strain: LongitudinalStrainProfile, val adaptation: LongitudinalAdaptationProfile,
    val tolerance: WorkloadToleranceProfile, val weeklyContext: List<WeeklyWorkloadEvidence>, val sustainable: SustainableWorkloadEvidence,
    val strainScore: Double?, val productiveEvidence: Double?, val maladaptationEvidence: Double?,
    val positiveBreadth: Double?, val negativeBreadth: Double?, val state: TrainingState, val confidence: PlanningConfidence,
    val globalDoseFactor: Double, val globalHardRestriction: Boolean, val hardRestrictionCodes: List<String>, val reasonCodes: List<String>) {
    val permitsSustainableRelease: Boolean get() = hardRestrictionCodes.isEmpty() &&
        state in setOf(TrainingState.PRODUCTIVE_HIGH_LOAD, TrainingState.TOLERATED_HIGH_LOAD, TrainingState.PRODUCTIVE_NORMAL) &&
        sustainable.confidence == PlanningConfidence.HIGH
}

/** Planner-only engineering policies; independent Python raw-input goldens own parity. */
object TrainingStatePolicy {
    const val LOW_WEEK_RATIO = .625
    const val BASELINE_SCALE_FLOOR = 8.0
    val controllableDomains = setOf(PlannedActivityKind.RESISTANCE, PlannedActivityKind.STRUCTURED_BADMINTON_DRILL, PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL)
    val majorMovements = setOf(MovementCoverage.LOWER_KNEE, MovementCoverage.POSTERIOR_CHAIN, MovementCoverage.HORIZONTAL_PUSH,
        MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PUSH, MovementCoverage.VERTICAL_PULL)
}

class TrainingStateAnalyzer {
    fun assess(input: TrainingStateInput): TrainingStateAssessment {
        val end = input.cutoff
        val recent = input.daily.filter { it.date in end.minusDays(6)..end }
        val previous = input.daily.filter { it.date in end.minusDays(13)..end.minusDays(7) }
        val baseline = input.daily.filter { it.date in end.minusDays(55)..end.minusDays(7) }
        val ofi7 = median(recent.map { it.overallFatigueIndex })
        val base = baseline.map { it.overallFatigueIndex }
        val relative = relative(ofi7, base)
        val absolute = ofi7?.let { clamp((it-55)/35) }
        val rel = relative.z?.let { clamp(it/2) }
        val q80 = base.sorted().takeIf { it.isNotEmpty() }?.let { it[((it.size-1)*.8).roundToInt()] }
        val persistence = if (recent.isNotEmpty() && q80 != null) recent.count { it.overallFatigueIndex > q80 }.toDouble()/recent.size else null
        val old = median(previous.map { it.overallFatigueIndex })
        val trend = if (ofi7 != null && old != null) clamp((ofi7-old)/15,-1.0,1.0) else null
        val axisZ = (0..4).map { a -> relative(median(recent.map { it.axes[a] }),baseline.map { it.axes[a] }).z }
        val axis = axisZ.mapNotNull { it?.let { v -> clamp(v/2) } }.maxOrNull()
        val recovery = input.recovery
        val readiness = mapOf("READY" to 0.0,"NORMAL" to 0.0,"GOOD" to 0.0,"CAUTION" to .30,"FATIGUED" to .60,"UNKNOWN" to .15)[recovery.readinessStatus]
        val tissue = mapOf("NORMAL" to 0.0,"LOW" to .1,"MODERATE" to .3,"ELEVATED" to .3,"HIGH" to .6,"UNKNOWN" to .1)[recovery.tissueStatus]
        val soft = listOfNotNull(readiness,tissue).maxOrNull()
        val s = available(absolute to .30, rel to .25, persistence to .15, trend?.coerceAtLeast(0.0) to .10, axis to .10, soft to .10)
        val strain = LongitudinalStrainProfile(ofi7,relative.center,relative.mad,relative.scale,relative.z,absolute,rel,
            persistence,trend,axis,axisZ.count { it != null && it >= 1 },baseline.size,recent.size,s)
        val current = input.records.between(end.minusDays(27),end)
        val prior = input.records.between(end.minusDays(55),end.minusDays(28))
        val adaptation = adaptation(input,current,prior)
        val (weeks,sustainable) = weeklyContext(input)
        fun window(rows: List<PlanningSetRecord>, start: LocalDate, finish: LocalDate): ToleranceWindow {
            val relevant = weeks.filter { it.end in start..finish }
            val excluded = relevant.filter { it.excludedFromTolerance }
            val usable = rows.filter { input.domains[it.stableKey] in TrainingStatePolicy.controllableDomains &&
                excluded.none { w -> it.date in w.start..w.end } }
            val denominator = relevant.size-excluded.size
            return ToleranceWindow(if (denominator>0) usable.size.toDouble()/denominator else null,
                if (denominator>0) usable.map { it.date }.distinct().size.toDouble()/denominator else null,
                median(usable.groupBy { it.date }.values.map { it.size.toDouble() }))
        }
        val now = window(current,end.minusDays(27),end)
        val then = window(prior,end.minusDays(55),end.minusDays(28))
        fun stability(a: Double?, b: Double?) = if (a!=null && b!=null && b>0) clamp((a/b-.60)/.30) else null
        val work=stability(now.units,then.units); val days=stability(now.days,then.days); val density=stability(now.density,then.density)
        val drift=adaptation.rpeDrift
        val rpe=drift?.let { clamp(1-maxOf(0.0,it)/1.5) }
        val t=available(work to .35,days to .25,density to .20,rpe to .20)
        val tolerance=WorkloadToleranceProfile(now,then,work,days,density,rpe,t)
        val a=adaptation.globalAdaptationResponse; val positive=adaptation.positiveBreadth; val negative=adaptation.negativeBreadth
        val p=available(a?.let { clamp((it+.20)/.80) } to .50,t to .30,positive to .20)
        val unresolved=weeks.takeLast(8).any { it.context==WeeklyTrainingContext.UNEXPLAINED_LOW_WEEK }
        val m=available(a?.let { clamp((-it-.10)/.50) } to .40,negative to .25,
            t?.takeUnless { unresolved }?.let { 1-it } to .20,drift?.let { clamp(maxOf(0.0,it)/1.5) } to .15)
        val active=(0..3).count { i -> current.any { input.domains[it.stableKey] in TrainingStatePolicy.controllableDomains && it.date in end.minusDays(6+i*7L)..end.minusDays(i*7L) } }
        val confidence=when {
            baseline.size>=28 && adaptation.movements.size>=3 && active>=3 -> PlanningConfidence.HIGH
            baseline.size>=14 && adaptation.movements.size>=2 && active>=2 -> PlanningConfidence.MODERATE
            else -> PlanningConfidence.LOW
        }
        val local=recovery.tissueRestrictedStableKeys
        val hard=buildList {
            if (recovery.readinessStatus=="LIMITED") add("READINESS_LIMITED")
            if (recovery.tissueStatus in setOf("VERY_HIGH","BLOCKED")) add("TISSUE_${recovery.tissueStatus}")
            addAll(local.sorted().map { "LOCAL_TISSUE:$it" })
            addAll(input.hardRestrictedModes.sorted().map { "EXPLICIT_MODE:$it" })
        }
        val globalHard=recovery.readinessStatus=="LIMITED" || (recovery.tissueStatus in setOf("VERY_HIGH","BLOCKED") && local.isEmpty())
        val state=when {
            hard.isNotEmpty() -> TrainingState.HARD_RESTRICTION
            confidence==PlanningConfidence.LOW -> TrainingState.UNCERTAIN
            m!=null && m>=.65 && negative!=null && negative>=.50 -> TrainingState.MALADAPTATION_PATTERN
            s!=null && s>=.60 && p!=null && p>=.65 && m!=null && m<.30 -> TrainingState.PRODUCTIVE_HIGH_LOAD
            s!=null && s>=.60 && p!=null && p>=.45 && m!=null && m<.45 -> TrainingState.TOLERATED_HIGH_LOAD
            s!=null && s>=.60 && m!=null && m>=.40 -> TrainingState.ACCUMULATING_STRAIN
            s!=null && s<.60 && p!=null && p>=.65 -> TrainingState.PRODUCTIVE_NORMAL
            else -> TrainingState.STABLE
        }
        var factor=clamp(1-.15*(s?:0.0)*(1-(p?:0.0))-.10*(m?:0.0),.80,1.0)
        if (confidence==PlanningConfidence.LOW) factor=1.0
        if (globalHard) factor=minOf(factor,if (recovery.readinessStatus=="LIMITED" || recovery.tissueStatus=="BLOCKED") .75 else .80)
        return TrainingStateAssessment(strain,adaptation,tolerance,weeks,sustainable,s,p,m,positive,negative,state,confidence,factor,
            globalHard,hard,listOf("SINGLE_GLOBAL_SOFT_DOSE","CORRELATED_CONFIDENCE_CAPPED")+
                if (unresolved) listOf("UNEXPLAINED_LOW_WEEK_NOT_ASSUMED_FAILURE") else emptyList())
    }

    private fun adaptation(input: TrainingStateInput, current: List<PlanningSetRecord>, prior: List<PlanningSetRecord>, posterior: Boolean=true): LongitudinalAdaptationProfile {
        val keys=(current+prior).map { it.stableKey }.distinct().filter { input.domains[it]==PlannedActivityKind.RESISTANCE }.sorted()
        val responses=keys.map { key ->
            val raw=matched(current.filter { it.stableKey==key },prior.filter { it.stableKey==key },key in input.loadSupportedKeys)
            val signal=input.signals[key].takeIf { posterior }
            val change=signal?.posteriorChangePercent
            val count=signal?.observationCount?:0
            val p=change?.takeIf { it.isFinite() && count>=2 }?.let { tanh(it/5) }
            val pc=if (p!=null) clamp(count/6.0) else 0.0
            ExerciseAdaptationEvidence(key,input.coverage[key]?:MovementCoverage.OTHER,change,p,pc,raw.load,raw.reps,raw.rpe,
                raw.response,raw.count,raw.confidence,available(p to .65,raw.response to .35),maxOf(pc,raw.confidence))
        }
        val groups=TrainingStatePolicy.majorMovements.sortedBy { it.name }.mapNotNull { movement ->
            val members=responses.filter { it.movement==movement && it.responseScore!=null && it.confidence>0 }
            if (members.isEmpty()) null else movement to MovementAdaptationEvidence(requireNotNull(weightedMedian(members.map { it.responseScore to it.confidence })),members.maxOf { it.confidence })
        }.toMap()
        val total=groups.values.sumOf { it.confidence }
        return LongitudinalAdaptationProfile(responses,groups,
            if (total>0) groups.values.sumOf { it.response*it.confidence }/total else null,
            if (total>0) groups.values.filter { it.response>=.20 }.sumOf { it.confidence }/total else null,
            if (total>0) groups.values.filter { it.response<=-.20 }.sumOf { it.confidence }/total else null,
            median(responses.mapNotNull { it.matchedRpeEfficiency?.let { rpe -> -rpe } }))
    }

    private data class Matched(val load: Double?,val reps: Double?,val rpe: Double?,val response: Double?,val count: Int,val confidence: Double)
    private fun matched(current: List<PlanningSetRecord>,prior: List<PlanningSetRecord>,loadSupported: Boolean): Matched {
        val values=List(3) { mutableListOf<Double>() }
        val dates=mutableSetOf<LocalDate>(); val withRpe=mutableSetOf<LocalDate>()
        val order=compareBy<PlanningSetRecord> { it.date }.thenBy { it.setIndex }
        for (component in 0..2) {
            val unused=prior.sortedWith(order).toMutableList()
            for (row in current.sortedWith(order)) {
                val index=unused.indices.filter { i ->
                    val old=unused[i]
                    val both=row.rpe!=null && old.rpe!=null
                    val sameWeight=if (old.weightKg>0) abs(row.weightKg-old.weightKg)<=.02*old.weightKg else row.weightKg==0.0
                    val reps=abs(row.reps-old.reps)
                    when(component) {
                        0 -> loadSupported && old.weightKg>0 && row.weightKg>0 && row.reps>0 && reps<=if (both) 1 else 0
                        1 -> sameWeight && row.reps>0 && old.reps>0
                        else -> sameWeight && reps<=1 && row.reps>0 && both
                    } && (component!=0 || !both || row.rpe!!<=old.rpe!!+.5)
                }.minWithOrNull(compareBy<Int> { abs(row.reps-unused[it].reps) }.thenBy { abs(row.weightKg-unused[it].weightKg) }
                    .thenBy { unused[it].date }.thenBy { it }) ?: continue
                val old=unused.removeAt(index)
                values[component] += when(component) { 0 -> row.weightKg/old.weightKg-1; 1 -> (row.reps-old.reps).toDouble(); else -> old.rpe!!-row.rpe!! }
                dates+=row.date
                if (row.rpe!=null && old.rpe!=null) withRpe+=row.date
            }
        }
        val load=median(values[0]); val reps=median(values[1]); val rpe=median(values[2])
        return Matched(load,reps,rpe,available(load?.let { clamp(it/.05,-1.0,1.0) } to .40,
            reps?.let { clamp(it/3,-1.0,1.0) } to .35,rpe?.let { clamp(it/1.5,-1.0,1.0) } to .25),dates.size,
            clamp(dates.size/6.0)*(if (withRpe.isEmpty()) .6 else 1.0))
    }

    private fun weeklyContext(input: TrainingStateInput): Pair<List<WeeklyWorkloadEvidence>,SustainableWorkloadEvidence> {
        val rows=input.records.filter { input.domains[it.stableKey] in TrainingStatePolicy.controllableDomains }
        val first=input.records.minOfOrNull { it.date }?:input.cutoff
        val raw=(11 downTo 0).mapNotNull { offset ->
            val end=input.cutoff.minusDays(offset*7L); val start=end.minusDays(6)
            if (start<first) return@mapNotNull null
            val group=rows.between(start,end)
            val seconds=group.groupBy { it.date to it.stableKey }.values.sumOf { batch ->
                batch.sumOf { if (it.seconds>0) it.seconds else 45 }+(batch.size-1)*(input.restSeconds[batch.first().stableKey]?:60) }
            val performance=adaptation(input,group,rows.between(start.minusDays(7),start.minusDays(1)),false)
            WeeklyWorkloadEvidence(start,end,group.size,seconds/60.0,group.map { it.date }.distinct().size,
                input.weeklyCourtLoad[end]?:0.0,median(group.mapNotNull { it.rpe }),performance.globalAdaptationResponse,performance.negativeBreadth,performance.rpeDrift)
        }
        val weeks=raw.mapIndexed { i,week ->
            val neighbors=raw.subList(maxOf(0,i-3),i)+raw.subList(i+1,minOf(raw.size,i+4))
            val initial=median(neighbors.filter { it.units>0 }.map { it.units.toDouble() })
            val typical=median(neighbors.filter { initial!=null && it.units>=TrainingStatePolicy.LOW_WEEK_RATIO*initial }.map { it.units.toDouble() })
            val low=typical!=null && week.units<TrainingStatePolicy.LOW_WEEK_RATIO*typical
            val deteriorating=(week.negativeBreadth?:0.0)>=.5 && (week.performanceResponse?:0.0)<=-.2 || (week.rpeDrift?:0.0)>1
            var context=WeeklyTrainingContext.NORMAL; var reasons=emptyList<String>()
            if (low) {
                val returned=raw.subList(i+1,minOf(raw.size,i+3)).any { it.units>=.85*typical!! }
                val preceding=raw.subList(maxOf(0,i-2),i)
                val stableBefore=preceding.size==2 && preceding.all { it.units>=.85*typical!! && (it.negativeBreadth?:0.0)<.5 && (it.rpeDrift?:0.0)<=1 }
                val courtTypical=median(neighbors.filter { it.courtLoad>0 }.map { it.courtLoad })
                val event=courtTypical!=null && week.courtLoad>=1.5*courtTypical
                when {
                    input.interruptionCause in setOf(InterruptionCause.EXTERNAL,InterruptionCause.EVENT) -> {
                        context=if (input.interruptionCause==InterruptionCause.EXTERNAL) WeeklyTrainingContext.EXTERNAL_INTERRUPTION_LIKELY else WeeklyTrainingContext.EVENT_OR_TAPER_LIKELY
                        reasons=listOf("USER_CONFIRMED_CONTEXT")
                    }
                    input.interruptionCause==InterruptionCause.FATIGUE || deteriorating -> {
                        context=WeeklyTrainingContext.RECOVERY_REDUCTION_LIKELY
                        reasons=listOf(if (input.interruptionCause==InterruptionCause.FATIGUE) "USER_REPORTED_FATIGUE" else "PERFORMANCE_OR_RPE_DETERIORATION")
                    }
                    returned && event -> { context=WeeklyTrainingContext.EVENT_OR_TAPER_LIKELY; reasons=listOf("COURT_RISE_AND_SC_RETURN") }
                    returned && stableBefore && !deteriorating -> { context=WeeklyTrainingContext.EXTERNAL_INTERRUPTION_LIKELY; reasons=listOf("ISOLATED_DROP_AND_RETURN_NO_OBSERVED_DECLINE") }
                    else -> { context=WeeklyTrainingContext.UNEXPLAINED_LOW_WEEK; reasons=listOf("CAUSE_UNKNOWN") }
                }
            }
            week.copy(context=context,low=low,localTypicalUnits=typical,reasonCodes=reasons,
                excludedFromTolerance=context in setOf(WeeklyTrainingContext.EXTERNAL_INTERRUPTION_LIKELY,WeeklyTrainingContext.EVENT_OR_TAPER_LIKELY))
        }
        val runs=mutableListOf<List<WeeklyWorkloadEvidence>>(); var current=mutableListOf<WeeklyWorkloadEvidence>()
        for (week in weeks) {
            val usable=week.context==WeeklyTrainingContext.NORMAL && week.units>0 && (week.negativeBreadth?:0.0)<.5 &&
                (week.rpeDrift?:0.0)<=1 && (week.performanceResponse==null || week.performanceResponse>=-.10)
            val stable=current.isEmpty() || week.units.toDouble()/maxOf(1,current.last().units) in .70..1.40
            if (!usable || !stable) { if (current.size>=2) runs+=current.toList(); current=mutableListOf() }
            if (usable) current+=week
        }
        if (current.size>=2) runs+=current.toList()
        val summaries=runs.map { run -> StableWorkloadRun(run.first().start,run.last().end,run.size,
            median(run.map { it.units.toDouble() })!!,median(run.map { it.minutes })!!,median(run.map { it.days.toDouble() })!!,
            median(run.mapNotNull { it.performanceResponse }),median(run.mapNotNull { it.rpeDrift }),
            run.size.toDouble().pow(2)*.92.pow((input.cutoff.toEpochDay()-run.last().end.toEpochDay())/7.0)) }
        val longest=summaries.maxOfOrNull { it.durationWeeks }?:0; val observation=summaries.sumOf { it.durationWeeks }
        val performanceObserved=summaries.any { it.response!=null }
        val confidence=when {
            performanceObserved && (longest>=4 || observation>=4 && runs.size>=2) -> PlanningConfidence.HIGH
            longest>=3 || observation>=4 -> PlanningConfidence.MODERATE
            else -> PlanningConfidence.LOW
        }
        return weeks to SustainableWorkloadEvidence(weightedMedian(summaries.map { it.units to it.weight }),
            weightedMedian(summaries.map { it.minutes to it.weight }),weightedMedian(summaries.map { it.days to it.weight }),
            longest,runs.size,weeks.size,confidence,summaries,weeks.takeIf { it.isNotEmpty() }?.map { it.units }?.average(),
            median(weeks.filter { it.context==WeeklyTrainingContext.NORMAL }.map { it.units.toDouble() }),
            weeks.count { it.low }>=2 && input.interruptionCause in setOf(InterruptionCause.UNSURE,InterruptionCause.MIXED),
            input.interruptionFrequency in setOf(InterruptionFrequency.MONTHLY,InterruptionFrequency.FREQUENT,InterruptionFrequency.VERY_FREQUENT),
            listOf("DEMONSTRATED_NOT_MAXIMAL_OR_SAFE_CAPACITY")+if (summaries.isEmpty()) listOf("NO_MULTI_WEEK_STABLE_RUN") else emptyList())
    }
}

private data class Relative(val center: Double?,val mad: Double?,val scale: Double?,val z: Double?)
private fun relative(current: Double?, baseline: List<Double>): Relative {
    val center=median(baseline); val mad=center?.let { median(baseline.map { x -> abs(x-it) }) }
    val scale=mad?.let { maxOf(TrainingStatePolicy.BASELINE_SCALE_FLOOR,1.4826*it) }
    return Relative(center,mad,scale,if (current!=null && center!=null && scale!=null) (current-center)/scale else null)
}
private fun clamp(x: Double,low: Double=0.0,high: Double=1.0)=x.coerceIn(low,high)
internal fun trainingMedian(values: List<Double>): Double? = values.filter(Double::isFinite).sorted().let {
    if (it.isEmpty()) null else if (it.size%2==0) (it[it.size/2-1]+it[it.size/2])/2 else it[it.size/2]
}
private fun median(values: List<Double>)=trainingMedian(values)
private fun available(vararg parts: Pair<Double?,Double>): Double? {
    val valid=parts.filter { it.first!=null }
    return if (valid.isEmpty()) null else valid.sumOf { it.first!!*it.second }/valid.sumOf { it.second }
}
private fun weightedMedian(parts: List<Pair<Double?,Double>>): Double? {
    val valid=parts.filter { it.first!=null && it.second>0 }.sortedBy { it.first }; val half=valid.sumOf { it.second }/2
    var seen=0.0
    for ((value,weight) in valid) { seen+=weight; if (seen>=half) return value }
    return null
}
private fun List<PlanningSetRecord>.between(start: LocalDate,end: LocalDate)=filter { it.date in start..end }

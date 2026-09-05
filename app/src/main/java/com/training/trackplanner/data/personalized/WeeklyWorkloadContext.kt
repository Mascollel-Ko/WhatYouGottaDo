package com.training.trackplanner.data.personalized

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.pow

enum class WeeklyContextCause { EXTERNAL, EVENT_OR_TAPER, FATIGUE, INTENTIONAL_DELOAD, OTHER, UNKNOWN }
enum class WeeklyContextSource { USER_CONFIRMED, HIGH_CONFIDENCE_INFERRED, INFERRED, UNRESOLVED }
data class WeeklyContextAnnotation(val weekStart: LocalDate, val cause: WeeklyContextCause,
    val source: WeeklyContextSource, val answeredAtEpochMillis: Long? = null) {
    init { require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "Week identity must be ISO Monday" } }
}

/** Partial current/leading weeks cannot establish a weekly drop or a sustainable workload. */
internal fun completedTrainingWeekEnd(cutoff: LocalDate): LocalDate =
    cutoff.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

/** Scheduling interpretation only: raw performance, RPE and physiological history are immutable. */
internal class WeeklyWorkloadContextAnalyzer {
    fun evaluate(raw: List<WeeklyWorkloadEvidence>, input: TrainingStateInput): Pair<List<WeeklyWorkloadEvidence>, SustainableWorkloadEvidence> {
        val weeks=raw.mapIndexed { i,week ->
            val neighbors=raw.subList(maxOf(0,i-3),i)+raw.subList(i+1,minOf(raw.size,i+4))
            val initial=trainingMedian(neighbors.filter { it.units>0 }.map { it.units.toDouble() })
            val typical=trainingMedian(neighbors.filter { initial!=null && it.units>=TrainingStatePolicy.LOW_WEEK_RATIO*initial }.map { it.units.toDouble() })
            val low=typical!=null && week.units<TrainingStatePolicy.LOW_WEEK_RATIO*typical
            val annotation=input.weekAnnotations[week.start]?.takeIf { it.weekStart==week.start && it.source==WeeklyContextSource.USER_CONFIRMED }
            var context=WeeklyTrainingContext.NORMAL
            var cause=WeeklyContextCause.UNKNOWN
            var source=WeeklyContextSource.UNRESOLVED
            var reasons=emptyList<String>()
            if (low) {
                val returned=raw.subList(i+1,minOf(raw.size,i+3)).any { it.units>=.85*typical!! }
                val preceding=raw.subList(maxOf(0,i-2),i)
                val noPriorDecline=preceding.isNotEmpty() && preceding.none(::deteriorating)
                val stableBefore=preceding.size==2 && noPriorDecline && preceding.all { it.units>=.85*typical!! }
                val courtTypical=trainingMedian(neighbors.filter { it.courtLoad>0 }.map { it.courtLoad })
                val event=courtTypical!=null && week.courtLoad>=1.5*courtTypical
                when {
                    annotation!=null -> {
                        cause=annotation.cause; source=annotation.source
                        context=when(cause) {
                            WeeklyContextCause.EXTERNAL -> WeeklyTrainingContext.EXTERNAL_INTERRUPTION_LIKELY
                            WeeklyContextCause.EVENT_OR_TAPER -> WeeklyTrainingContext.EVENT_OR_TAPER_LIKELY
                            WeeklyContextCause.FATIGUE -> WeeklyTrainingContext.RECOVERY_REDUCTION_LIKELY
                            else -> WeeklyTrainingContext.UNEXPLAINED_LOW_WEEK
                        }
                        reasons=listOf("EXACT_WEEK_USER_ANSWER", "CAUSE_${cause.name}")
                    }
                    deteriorating(week) -> {
                        context=WeeklyTrainingContext.RECOVERY_REDUCTION_LIKELY
                        source=WeeklyContextSource.INFERRED
                        reasons=listOf("PERFORMANCE_OR_RPE_DETERIORATION")
                    }
                    returned && event && noPriorDecline && !deteriorating(week) -> {
                        context=WeeklyTrainingContext.EVENT_OR_TAPER_LIKELY
                        source=WeeklyContextSource.HIGH_CONFIDENCE_INFERRED
                        reasons=listOf("COURT_RISE_AND_SC_RETURN_NOT_USER_CONFIRMED")
                    }
                    returned && stableBefore && !deteriorating(week) -> {
                        context=WeeklyTrainingContext.EXTERNAL_INTERRUPTION_LIKELY
                        source=WeeklyContextSource.HIGH_CONFIDENCE_INFERRED
                        reasons=listOf("ISOLATED_DROP_AND_RETURN_NOT_USER_CONFIRMED")
                    }
                    else -> { context=WeeklyTrainingContext.UNEXPLAINED_LOW_WEEK; reasons=listOf("CAUSE_UNKNOWN") }
                }
            }
            week.copy(context=context,low=low,localTypicalUnits=typical,reasonCodes=reasons,cause=cause,source=source,
                excludedFromTolerance=low && source==WeeklyContextSource.USER_CONFIRMED &&
                    cause in setOf(WeeklyContextCause.EXTERNAL,WeeklyContextCause.EVENT_OR_TAPER))
        }.toMutableList()
        val runs=mutableListOf<List<WeeklyWorkloadEvidence>>()
        var current=mutableListOf<WeeklyWorkloadEvidence>()
        val bridged=mutableSetOf<LocalDate>()
        fun flush() { if (current.size>=2) runs+=current.toList(); current=mutableListOf() }
        for ((i,week) in weeks.withIndex()) {
            // One confirmed gap may bridge observed normal segments; no imputation or inferred bridges.
            val next=weeks.getOrNull(i+1)
            val beforeTypical=trainingMedian(current.takeLast(2).map { it.units.toDouble() })
            val bridge=week.excludedFromTolerance && !deteriorating(week) && current.size>=2 &&
                current.last().end==week.start.minusDays(1) && current[current.lastIndex-1].end==week.start.minusDays(8) &&
                next!=null && usable(next) && beforeTypical!=null && next.units>=.85*beforeTypical &&
                next.units/beforeTypical<=1.40 && current.takeLast(2).all { it.units>=.85*beforeTypical }
            if (bridge) { bridged+=week.start; continue }
            val stable=current.isEmpty() || week.units.toDouble()/maxOf(1,current.last().units) in .70..1.40
            if (!usable(week) || !stable) flush()
            if (usable(week)) current+=week
        }
        flush()
        val summaries=runs.map { run ->
            val n=run.size; val performance=run.count { it.performanceResponse!=null }
            val coverage=performance.toDouble()/n
            val response=trainingMedian(run.mapNotNull { it.performanceResponse })
            val negative=trainingMedian(run.mapNotNull { it.negativeBreadth })
            val drift=trainingMedian(run.mapNotNull { it.rpeDrift })
            val confidence=when {
                n>=4 && performance>=2 && coverage>=.50 -> PlanningConfidence.HIGH
                n>=3 && (performance>0 || run.any { it.rpeDrift!=null }) -> PlanningConfidence.MODERATE
                else -> PlanningConfidence.LOW
            }
            StableWorkloadRun(run.first().start,run.last().end,n,
                trainingMedian(run.map { it.units.toDouble() })!!,trainingMedian(run.map { it.minutes })!!,
                trainingMedian(run.map { it.days.toDouble() })!!,response,drift,
                n.toDouble().pow(2)*.92.pow((input.cutoff.toEpochDay()-run.last().end.toEpochDay())/7.0),
                n,((run.last().end.toEpochDay()-run.first().start.toEpochDay()+1)/7).toInt(),
                performance,coverage,negative,confidence,confidence==PlanningConfidence.HIGH)
        }
        val high=summaries.filter { it.qualifiedForCapacityRelease }
        val confidence=when {
            high.isNotEmpty() -> PlanningConfidence.HIGH
            summaries.any { it.confidence==PlanningConfidence.MODERATE } -> PlanningConfidence.MODERATE
            else -> PlanningConfidence.LOW
        }
        // An unvalidated long run cannot dominate HIGH capacity released by a different run.
        val capacityRuns=high.ifEmpty { summaries }
        for (i in weeks.indices) if (weeks[i].start in bridged && summaries.any { weeks[i].start in it.start..it.end })
            weeks[i]=weeks[i].copy(bridgesStableRun=true)
        return weeks to SustainableWorkloadEvidence(weightedMedian(capacityRuns.map { it.units to it.weight }),
            weightedMedian(capacityRuns.map { it.minutes to it.weight }),weightedMedian(capacityRuns.map { it.days to it.weight }),
            summaries.maxOfOrNull { it.observedSuccessfulWeeks }?:0,summaries.size,weeks.size,confidence,summaries,
            weeks.takeIf { it.isNotEmpty() }?.map { it.units }?.average(),
            trainingMedian(weeks.filter { it.context==WeeklyTrainingContext.NORMAL }.map { it.units.toDouble() }),
            weeks.any { it.low && it.source!=WeeklyContextSource.USER_CONFIRMED },
            input.interruptionFrequency in setOf(InterruptionFrequency.MONTHLY,InterruptionFrequency.FREQUENT,InterruptionFrequency.VERY_FREQUENT),
            listOf("DEMONSTRATED_NOT_MAXIMAL_OR_SAFE_CAPACITY","RUN_LOCAL_PERFORMANCE_AUTHORITY","COMPLETE_ISO_WEEKS")+
                if (summaries.isEmpty()) listOf("NO_MULTI_WEEK_STABLE_RUN") else emptyList())
    }

    private fun deteriorating(w: WeeklyWorkloadEvidence) =
        (w.negativeBreadth?:0.0)>=.5 && (w.performanceResponse?:0.0)<=-.2 || (w.rpeDrift?:0.0)>1
    private fun usable(w: WeeklyWorkloadEvidence) = w.context==WeeklyTrainingContext.NORMAL && w.units>0 &&
        (w.negativeBreadth?:0.0)<.5 && (w.rpeDrift?:0.0)<=1 && (w.performanceResponse==null || w.performanceResponse>=-.10)
    private fun weightedMedian(parts: List<Pair<Double,Double>>): Double? {
        var seen=0.0; val half=parts.sumOf { it.second }/2
        for ((v,w) in parts.sortedBy { it.first }) { seen+=w; if (seen>=half) return v }
        return null
    }
}

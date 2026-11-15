package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.ProgramSkeletonItem
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

internal data class BCoachV2ScheduleEvaluation(
    val recoveryScore: Double,
    val maximumDayMinutes: Double,
    val dayMinutes: Map<String, Double>,
    val daysOverLimit: Int,
    val unmappedAxisExerciseCount: Int
)

internal object BCoachV2ScheduleLoad {
    private val axisSelectors = linkedMapOf<String, (Exercise) -> Double>(
        "systemic" to Exercise::systemicLoadWeight,
        "neuralHeavy" to Exercise::neuralHeavyWeight,
        "neuralSpeed" to Exercise::neuralSpeedWeight,
        "localMuscular" to Exercise::localLoadWeight,
        "deceleration" to Exercise::decelerationWeight,
        "elasticSsc" to Exercise::elasticSscWeight
    )

    fun evaluate(
        plan: GeneratedProgramSkeleton,
        baseline: GeneratedProgramSkeleton,
        request: V22GenerationRequest,
        history: List<V22HistoryRow>,
        exercises: Map<String, Exercise>,
        scenario: BCoachV2Scenario
    ): BCoachV2ScheduleEvaluation {
        val start = request.asOf.plusDays(1)
        fun plannedDate(item: ProgramSkeletonItem): LocalDate =
            start.plusDays((item.weekNumber - 1L) * 7L + (item.dayOfWeek - 1L))
        fun loadByDate(items: List<ProgramSkeletonItem>): Map<LocalDate, Map<String, Double>> =
            items.groupBy(::plannedDate).mapValues { (_, dayItems) ->
                axisSelectors.mapValues { (_, selector) ->
                    dayItems.sumOf { item ->
                        val exercise = exercises[item.exerciseStableKey]
                        if (exercise == null) 0.0
                        else ProgramSetPrescriptionResolver.resolve(item).size * selector(exercise)
                    }
                }
            }
        val planned = loadByDate(plan.items)
        val baselineLoads = loadByDate(baseline.items)
        val observed = history.filter { row -> row.date in request.asOf.minusDays(27)..request.asOf }
            .groupBy(V22HistoryRow::date).mapValues { (_, rows) ->
                axisSelectors.mapValues { (_, selector) ->
                    rows.sumOf { row -> exercises[row.stableKey]?.let(selector) ?: 0.0 }
                }
            }
        val allPlanDates = planned.keys
        val periodStart = allPlanDates.minOrNull()?.minusDays(3) ?: start.minusDays(3)
        val periodEnd = allPlanDates.maxOrNull() ?: start
        val scores = axisSelectors.keys.map { axis ->
            val observedActivity = observed.values.mapNotNull { values -> values.getValue(axis).takeIf { it > 0.0 } }
            val baselineActivity = baselineLoads.values.mapNotNull { values -> values.getValue(axis).takeIf { it > 0.0 } }
            val scale = max(1.0, max(q75(observedActivity), q75(baselineActivity)))
            val transformed = generateSequence(periodStart) { day ->
                day.plusDays(1).takeIf { it <= periodEnd }
            }.map { day ->
                val residual = BCoachV2Policy.recoveryKernels.getValue(axis).mapIndexed { lag, kernel ->
                    val date = day.minusDays(lag.toLong())
                    val actual = if (date <= request.asOf) observed[date]?.get(axis) ?: 0.0 else 0.0
                    val simulated = if (date > request.asOf) planned[date]?.get(axis) ?: 0.0 else 0.0
                    (actual + simulated) * kernel * scenario.recoveryKernelFactor
                }.sum()
                val ratio = residual / scale
                (ratio / (1.0 + ratio)).unitInterval()
            }.toList()
            if (transformed.isEmpty()) 0.0
            else 0.40 * transformed.average() + 0.60 * (transformed.maxOrNull() ?: 0.0)
        }
        val cap = (request.sessionMinutes ?: BCoachV2Policy.DEFAULT_SESSION_MINUTES).toDouble()
        val minutes = plan.items.groupBy { it.weekNumber to it.dayOfWeek }.map { (slot, items) ->
            "${slot.first}:${slot.second}" to timeUpper(items, exercises)
        }.toMap()
        return BCoachV2ScheduleEvaluation(
            recoveryScore = scores.averageOrZero().unitInterval(),
            maximumDayMinutes = minutes.values.maxOrNull() ?: 0.0,
            dayMinutes = minutes,
            daysOverLimit = minutes.values.count { it > cap + 1e-9 },
            unmappedAxisExerciseCount = plan.items.map(ProgramSkeletonItem::exerciseStableKey).distinct()
                .count { exercises[it] == null }
        )
    }

    fun timeUpper(items: List<ProgramSkeletonItem>, exercises: Map<String, Exercise>): Double {
        val inner = BCoachV2Policy.ESTIMATED_WARMUP_MINUTES + items.sumOf { item ->
            val exercise = exercises[item.exerciseStableKey]
            val setup = if (exercise?.equipment.orEmpty().isNotBlank()) {
                BCoachV2Policy.ESTIMATED_SETUP_KNOWN_MINUTES
            } else BCoachV2Policy.ESTIMATED_SETUP_UNKNOWN_MINUTES
            val sets = ProgramSetPrescriptionResolver.resolve(item)
            val active = sets.sumOf { set ->
                (set.seconds.takeIf { it > 0 }?.toDouble()
                    ?: BCoachV2Policy.ESTIMATED_ACTIVE_SET_SECONDS) / 60.0
            }
            val rest = max(0, sets.size - 1) * item.restSeconds.coerceAtLeast(45) / 60.0
            setup + active + rest + 0.75
        }
        return BCoachV2Policy.TIME_UPPER_EXTRA_MINUTES + BCoachV2Policy.TIME_UPPER_FACTOR * inner
    }

    private fun q75(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val rank = ceil(0.75 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}

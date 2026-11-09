package com.training.trackplanner.data.v22

import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.Exercise
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Post-prescription, within-block weekday optimizer. Moves complete exercise items only.
 * All actual weekly set prescriptions are reallocated by original (weekday, stableKey),
 * so progression/peak-week differences are preserved without recreating prescriptions.
 *
 * The cost is a deterministic PLANNING HEURISTIC, not a measured fatigue model.
 * Main-slot exercises, unknown muscle metadata, weekday identities and day count are fixed.
 */
internal object BBalancedWeeklyPlacementOptimizer {
    private const val MAX_EVALUATIONS = 3_000
    private const val MAX_STEPS = 12
    private const val MIN_IMPROVEMENT = 0.05
    private const val EPS = 1e-8

    data class Move(val stableKey: String, val fromDay: Int, val toDay: Int)
    data class Metrics(
        val concentration: Double,
        val adjacentExposure: Double,
        val estimatedPeakMinutes: Double,
        val overtimeCost: Double,
        val totalCost: Double
    )
    data class Result(
        val sessions: List<V22Session>,
        val weeks: List<List<V22Session>>,
        val before: Metrics?,
        val after: Metrics?,
        val moves: List<Move>,
        val evaluated: Int,
        val status: String
    ) {
        val changed: Boolean get() = moves.isNotEmpty()
    }

    private data class Atom(val sourceIndex: Int, val sourceDay: Int, val item: V22ExercisePlan)
    private data class Scored(
        val assignment: IntArray,
        val metrics: Metrics,
        val weeklyOvertime: List<Double>,
        val weeklyAdjacent: List<Double>,
        val dailyTimes: List<List<Double>>,
        val dailyPhysicalSets: List<List<Int>>,
        val dailyWeightedSets: List<List<Double>>,
        val muscleLoadsByWeek: List<Map<String, List<Double>>>
    )
    private data class MuscleProfile(val roles: Map<String, Double>)
    private fun V22ExercisePlan.itemRoleIsMain(): Boolean = role.contains("MAIN", ignoreCase = true)

    fun optimize(
        sessions: List<V22Session>,
        weeks: List<List<V22Session>>,
        exercises: Map<String, Exercise>,
        muscleRelations: List<CanonicalMetadataRelation>,
        mainKeys: Set<String>,
        sessionMinutes: Int?
    ): Result {
        fun unchanged(status: String, before: Metrics? = null, evaluated: Int = 0) =
            Result(sessions, weeks, before, before, emptyList(), evaluated, status)

        if (sessions.size < 2 || weeks.isEmpty()) return unchanged("NOT_APPLICABLE")
        val days = sessions.map(V22Session::session)
        if (days.distinct().size != days.size || days.any { it !in 1..7 }) {
            return unchanged("UNSUPPORTED_WEEKDAY_IDENTITIES")
        }
        val expected = sessions.map { it.items.map(V22ExercisePlan::stableKey).sorted() }
        if (expected.any { it.isEmpty() || it.size != it.distinct().size } ||
            weeks.any { week ->
                week.map(V22Session::session) != days ||
                    week.map { it.items.map(V22ExercisePlan::stableKey).sorted() } != expected
            }) return unchanged("WEEKLY_EXERCISE_IDENTITY_MISMATCH")

        val profiles = muscleRelations.asSequence()
            .filter { it.domain == CanonicalRelationDomain.MUSCLE &&
                it.relationType in setOf("PRIMARY", "SECONDARY") &&
                it.relationValue.isNotBlank() }
            .groupBy(CanonicalMetadataRelation::exerciseStableKey)
            .mapValues { (_, rows) ->
                MuscleProfile(rows.groupBy(CanonicalMetadataRelation::relationValue).mapValues { (_, roles) ->
                    if (roles.any { it.relationType == "PRIMARY" }) 1.0 else 0.5
                })
            }

        // Provisional muscle-role rows may aid review but do not grant movement authority.
        val provisionalKeys = muscleRelations.asSequence()
            .filter { it.domain == CanonicalRelationDomain.MUSCLE &&
                it.relationType in setOf("PRIMARY", "SECONDARY") &&
                it.status.startsWith("PROVISIONAL", ignoreCase = true) }
            .map(CanonicalMetadataRelation::exerciseStableKey).toSet()

        val atoms = sessions.flatMapIndexed { index, session ->
            session.items.map { Atom(index, session.session, it) }
        }
        val movable = atoms.map { atom ->
            val exercise = exercises[atom.item.stableKey]
            atom.item.stableKey !in mainKeys && atom.item.stableKey !in provisionalKeys &&
                !atom.item.role.contains("MAIN", ignoreCase = true) &&
                exercise != null && exercise.isActive && !exercise.needsReview &&
                !exercise.isCustom && exercise.planningEligibility != "HISTORY_ONLY" &&
                profiles[atom.item.stableKey]?.roles?.isNotEmpty() == true
        }
        if (movable.none { it }) return unchanged("NO_REVIEWED_MOVABLE_EXERCISES")

        // Variants already contain the completed per-week prescription. Never average
        // away peak weeks, a progressive week, or time-based sets to score feasibility.
        fun distribute(source: List<V22Session>, assignment: IntArray): List<V22Session> {
            val items = Array(sessions.size) { mutableListOf<V22ExercisePlan>() }
            val bySource = source.map { session -> session.items.associateBy(V22ExercisePlan::stableKey) }
            // Keep the resident exercises (and thus original MAIN-first order) before arrivals.
            atoms.indices.filter { assignment[it] == atoms[it].sourceIndex }.forEach { i ->
                val atom = atoms[i]
                items[assignment[i]] += bySource[atom.sourceIndex].getValue(atom.item.stableKey)
            }
            atoms.indices.filter { assignment[it] != atoms[it].sourceIndex }.forEach { i ->
                val atom = atoms[i]
                items[assignment[i]] += bySource[atom.sourceIndex].getValue(atom.item.stableKey)
            }
            return source.mapIndexed { index, session ->
                session.copy(items = items[index],
                    nominalSets = items[index].sumOf(V22ExercisePlan::setCount))
            }
        }

        fun valid(assignment: IntArray): Boolean {
            for (day in sessions.indices) {
                val items = atoms.indices.filter { assignment[it] == day }
                if (items.isEmpty()) return false
                if (items.map { atoms[it].item.stableKey }.distinct().size != items.size) return false
            }
            return true
        }

        fun calculate(assignment: IntArray): Scored {
            var concentration = 0.0
            var adjacent = 0.0
            var peak = 0.0
            var overtime = 0.0
            var timeSpread = 0.0
            var setSpread = 0.0
            var workloadSpread = 0.0
            val dailyTimes = mutableListOf<List<Double>>()
            val dailyPhysicalSets = mutableListOf<List<Int>>()
            val dailyWeightedSets = mutableListOf<List<Double>>()
            val muscleLoadsByWeek = mutableListOf<Map<String, List<Double>>>()
            val overByWeek = mutableListOf<Double>()
            val adjacentByWeek = mutableListOf<Double>()
            for (week in weeks) {
                val allocated = distribute(week, assignment)
                val time = allocated.map { BBalancedDayPlacementPlanner.estimateMinutes(it, exercises) }
                peak = max(peak, time.maxOrNull() ?: 0.0)
                dailyTimes += time
                val daySets = allocated.map(V22Session::nominalSets)
                dailyPhysicalSets += daySets
                val weightedSets = allocated.map { session ->
                    session.items.sumOf { item ->
                        val primary = item.stableKey in mainKeys ||
                            item.itemRoleIsMain() ||
                            item.stableKey in setOf("barbell_back_squat", "barbell_deadlift", "barbell_romanian_deadlift")
                        item.setCount * if (primary) 1.5 else 1.0
                    }
                }
                dailyWeightedSets += weightedSets
                val averageSets = daySets.average()
                setSpread += daySets.sumOf { (it - averageSets) * (it - averageSets) } / days.size
                val averageWorkload = weightedSets.average()
                workloadSpread += weightedSets.sumOf {
                    (it - averageWorkload) * (it - averageWorkload)
                } / days.size
                val averageTime = time.average()
                timeSpread += time.sumOf { (it - averageTime) * (it - averageTime) } / time.size
                val over = if (sessionMinutes == null) 0.0 else
                    time.sumOf { (max(0.0, it - sessionMinutes)).let { excess -> excess * excess } }
                overByWeek += over
                overtime += over
                val perMuscle = linkedMapOf<String, DoubleArray>()
                allocated.forEachIndexed { index, day ->
                    day.items.forEach { item ->
                        profiles[item.stableKey]?.roles.orEmpty().forEach { (muscle, weight) ->
                            val values = perMuscle.getOrPut(muscle) { DoubleArray(days.size) }
                            values[index] += item.setCount * weight
                        }
                    }
                }
                muscleLoadsByWeek += perMuscle.mapValues { (_, loads) -> loads.toList() }
                val beforeWeekAdjacent = adjacent
                perMuscle.values.forEach { loads ->
                    val total = loads.sum()
                    if (total <= 0.0) return@forEach
                    concentration += (loads.sumOf { it * it } / total - total / days.size)
                        .coerceAtLeast(0.0)
                    for (i in days.indices) for (j in i + 1 until days.size) {
                        val delta = abs(days[i] - days[j])
                        if (min(delta, 7 - delta) == 1) {
                            adjacent += min(loads[i], loads[j])
                        }
                    }
                }
                // Adjacent exposures to the same exact exercise warrant an extra cost.
                val occurrences = allocated.flatMapIndexed { dayIndex, session ->
                    session.items.map { it.stableKey to (dayIndex to it.setCount) }
                }.groupBy({ it.first }, { it.second })
                occurrences.values.forEach { matches ->
                    for (i in matches.indices) for (j in i + 1 until matches.size) {
                        val a = matches[i]
                        val b = matches[j]
                        val delta = abs(days[a.first] - days[b.first])
                        if (min(delta, 7 - delta) == 1) adjacent += min(a.second, b.second) * 2.0
                    }
                }
                adjacentByWeek += adjacent - beforeWeekAdjacent
            }
            val normalizer = weeks.size.toDouble()
            concentration /= normalizer
            adjacent /= normalizer
            overtime /= normalizer
            timeSpread /= normalizer
            setSpread /= normalizer
            workloadSpread /= normalizer
            val cost = 1.8 * concentration + 3.0 * adjacent +
                2.5 * overtime + 0.11 * peak + 0.035 * timeSpread +
                0.18 * setSpread + 0.30 * workloadSpread
            return Scored(assignment.copyOf(),
                Metrics(concentration, adjacent, peak, overtime, cost),
                overByWeek, adjacentByWeek, dailyTimes, dailyPhysicalSets, dailyWeightedSets,
                muscleLoadsByWeek)
        }

        val original = IntArray(atoms.size) { atoms[it].sourceIndex }
        var evaluated = 1
        var best = calculate(original)
        val initial = best
        for (step in 0 until MAX_STEPS) {
            if (evaluated >= MAX_EVALUATIONS) break
            var next: Scored? = null
            fun consider(trial: IntArray) {
                if (evaluated >= MAX_EVALUATIONS || !valid(trial)) return
                evaluated++
                val candidate = calculate(trial)
                // A previously feasible prescription must remain feasible in EVERY
                // week. If already over time, never worsen that week's excess.
                if (candidate.weeklyOvertime.indices.any { index ->
                        candidate.weeklyOvertime[index] > initial.weeklyOvertime[index] + EPS ||
                            candidate.weeklyAdjacent[index] > initial.weeklyAdjacent[index] + EPS
                    }) return
                // Every evaluated week must remain safe independently. A lower summed
                // overtime penalty must never make an originally valid day exceed its
                // individual time limit or create a new busiest training day.
                if (candidate.dailyTimes.indices.any { week ->
                    val beforeTime = initial.dailyTimes[week]
                    val afterTime = candidate.dailyTimes[week]
                    if ((afterTime.maxOrNull() ?: 0.0) > (beforeTime.maxOrNull() ?: 0.0) + EPS)
                        return@any true
                    if (sessionMinutes == null) return@any false
                    beforeTime.indices.any { day ->
                        val before = beforeTime[day]
                        val after = afterTime[day]
                        if (before <= sessionMinutes + EPS) after > sessionMinutes + EPS
                        else after > before + EPS
                    }
                }) return
                // Set and workload caps are bounded by the original peak AND an
                // explicit relative-to-week-mean envelope. This is not a measured
                // fatigue or one-repetition-max calculation.
                if (candidate.dailyPhysicalSets.indices.any { week ->
                    val beforeSets = initial.dailyPhysicalSets[week]
                    val afterSets = candidate.dailyPhysicalSets[week]
                    val beforeLoads = initial.dailyWeightedSets[week]
                    val afterLoads = candidate.dailyWeightedSets[week]
                    val setCap = max(beforeSets.average() * 1.4, beforeSets.average() + 2.0)
                    val loadCap = max(beforeLoads.average() * 1.4, beforeLoads.average() + 2.0)
                    val peakSets = beforeSets.maxOrNull() ?: 0
                    val peakLoad = beforeLoads.maxOrNull() ?: 0.0
                    if ((afterSets.maxOrNull() ?: 0) > peakSets ||
                        (afterLoads.maxOrNull() ?: 0.0) > peakLoad + EPS) return@any true
                    beforeSets.indices.any { day ->
                        val badSets = if (beforeSets[day] <= setCap + EPS)
                            afterSets[day] > setCap + EPS
                        else afterSets[day] > beforeSets[day]
                        val badLoad = if (beforeLoads[day] <= loadCap + EPS)
                            afterLoads[day] > loadCap + EPS
                        else afterLoads[day] > beforeLoads[day] + EPS
                        badSets || badLoad
                    }
                }) return
                // No single muscle may develop a larger single-day planned-set peak
                // just because a different muscle's distribution improved more.
                if (candidate.muscleLoadsByWeek.indices.any { week ->
                    initial.muscleLoadsByWeek[week].any { (muscle, before) ->
                        val after = candidate.muscleLoadsByWeek[week][muscle].orEmpty()
                        (after.maxOrNull() ?: 0.0) > (before.maxOrNull() ?: 0.0) + EPS
                    }
                }) return
                if (candidate.metrics.totalCost >= best.metrics.totalCost - MIN_IMPROVEMENT) return
                if (next == null || candidate.metrics.totalCost < next!!.metrics.totalCost - EPS) {
                    next = candidate
                }
            }
            for (i in atoms.indices) {
                if (!movable[i] || evaluated >= MAX_EVALUATIONS) continue
                for (day in sessions.indices) {
                    if (day == best.assignment[i]) continue
                    consider(best.assignment.copyOf().also { it[i] = day })
                }
            }
            for (i in atoms.indices) {
                if (!movable[i] || evaluated >= MAX_EVALUATIONS) continue
                for (j in i + 1 until atoms.size) {
                    if (!movable[j] || best.assignment[i] == best.assignment[j]) continue
                    val trial = best.assignment.copyOf()
                    val old = trial[i]
                    trial[i] = trial[j]
                    trial[j] = old
                    consider(trial)
                }
            }
            val improvement = next ?: break
            best = improvement
        }

        if (best.assignment.contentEquals(original)) {
            return unchanged("NO_BETTER_SAFE_LAYOUT", initial.metrics, evaluated)
        }
        val moves = atoms.indices.filter { best.assignment[it] != atoms[it].sourceIndex }
            .map { Move(atoms[it].item.stableKey, atoms[it].sourceDay, days[best.assignment[it]]) }
        return Result(
            distribute(sessions, best.assignment),
            weeks.map { distribute(it, best.assignment) },
            initial.metrics,
            best.metrics,
            moves,
            evaluated,
            "IMPROVED_PLANNING_HEURISTIC_NOT_RECOVERY_CLEARANCE"
        )
    }
}

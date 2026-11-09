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
        val weeklyAdjacent: List<Double>
    )
    private data class MuscleProfile(val roles: Map<String, Double>)

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
            val overByWeek = mutableListOf<Double>()
            val adjacentByWeek = mutableListOf<Double>()
            for (week in weeks) {
                val allocated = distribute(week, assignment)
                val time = allocated.map { BBalancedDayPlacementPlanner.estimateMinutes(it, exercises) }
                peak = max(peak, time.maxOrNull() ?: 0.0)
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
            val cost = 1.8 * concentration + 3.0 * adjacent +
                2.5 * overtime + 0.11 * peak + 0.035 * timeSpread
            return Scored(assignment.copyOf(),
                Metrics(concentration, adjacent, peak, overtime, cost), overByWeek, adjacentByWeek)
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

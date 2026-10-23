package com.training.trackplanner.data.v22

import com.training.trackplanner.data.Exercise
import kotlin.math.abs
import kotlin.math.max

/**
 * B12 v0.1: bounded, deterministic placement *proposal*, not canonical OFI/tissue clearance.
 * Never edits the exercises, set prescriptions or total weekly volume.
 * Days are explicit Monday-based slots; cyclic spacing includes Sunday -> Monday.
 */
internal object BBalancedDayPlacementPlanner {
    private const val MAX_EVALUATIONS = 12_000
    private val heavyKeys = setOf("barbell_back_squat", "barbell_deadlift", "barbell_romanian_deadlift")
    private val days3 = listOf(1, 3, 5) // Mon/Wed/Fri
    private val days4 = listOf(1, 2, 4, 6) // Mon/Tue/Thu/Sat

    data class Result(
        val sessions: List<V22Session>,
        val requestedDays: Int,
        val effectiveDays: Int,
        val expanded: Boolean,
        val maxEstimatedMinutes: Double?,
        val requestedMaxEstimatedMinutes: Double?,
        val candidateEvaluations: Int,
        val diagnosis: String
    )

    private data class Atom(val originalDay: Int, val item: V22ExercisePlan)
    private data class Scored(val assignment: IntArray, val cost: Double, val peakMinutes: Double)
    private data class SearchResult(val sessions: List<V22Session>, val score: Scored, val evaluated: Int)

    fun estimateMinutes(session: V22Session, exercises: Map<String, Exercise>): Double {
        if (session.items.isEmpty()) return 0.0
        var total = 8.0 // one general warmup; not a measured duration
        for (item in session.items) {
            val exercise = exercises[item.stableKey]
            val heavy = item.stableKey in heavyKeys
            val unilateral = exercise?.laterality.orEmpty().contains("UNILATERAL", true) ||
                exercise?.movementPattern.orEmpty().contains("UNILATERAL", true)
            val preparation = when (item.stableKey) {
                "barbell_back_squat" -> 10.0
                "barbell_deadlift" -> 13.0
                "barbell_romanian_deadlift" -> 6.0
                else -> if (item.stableKey == "ex_e41f4c2b") 5.0 else 0.0
            }
            val setup = if (heavy) 3.5 else if (exercise?.equipment.orEmpty().isNotBlank()) 2.3 else 1.5
            val restSeconds = max(exercise?.defaultRestSeconds ?: 60, if (heavy) 125 else 45)
            val activeSeconds = item.sets.sumOf { set ->
                val nominal = (set.seconds?.takeIf { it > 0 } ?: 45).toDouble()
                if (unilateral) nominal * 1.5 else nominal
            }
            total += setup + preparation + activeSeconds / 60.0 +
                max(0, item.setCount - 1) * restSeconds / 60.0 + 0.75
        }
        // Explicit engineering buffer. This is NOT a validated upper confidence bound.
        return total * 1.12 + 2.0
    }

    fun optimize(
        baseline: List<V22Session>,
        requestedDays: Int,
        maxAllowedDays: Int,
        sessionMinutes: Int?,
        exercises: Map<String, Exercise>
    ): Result {
        val original = baseline.map { session ->
            session.copy(items = session.items.map { it.copy(sets = it.sets.map(V22ObservedSet::copy)) })
        }
        fun fallback(reason: String): Result {
            val peak = original.maxOfOrNull { estimateMinutes(it, exercises) }
            val status = if (sessionMinutes != null && peak != null && peak > sessionMinutes)
                "ESTIMATED_TIME_OVER_LIMIT_REVIEW_REQUIRED; $reason"
            else reason
            return Result(original, requestedDays, original.size, false, peak, peak, 0,
                "$status; NO_CANONICAL_OFI_OR_TISSUE_CLEARANCE; ESTIMATES_NOT_MEASURED")
        }
        if (requestedDays !in 1..7 || baseline.size != requestedDays) return fallback("UNSUPPORTED_DAY_COUNT")
        if (requestedDays != 3 || maxAllowedDays < 4 || sessionMinutes == null) {
            return fallback("FIXED_DAYS_NO_AUTHORIZED_EXPANSION")
        }
        val atoms = original.flatMapIndexed { day, session ->
            session.items.map { Atom(day, it) }
        }
        if (atoms.size < 4 || atoms.map { it.item.stableKey }.distinct().size < 4) {
            return fallback("INSUFFICIENT_DISTINCT_ITEMS_FOR_FOUR_DAYS")
        }
        val three = search(atoms, days3, exercises, sessionMinutes)
        val four = search(atoms, days4, exercises, sessionMinutes)
        // Five minutes of planning margin before accepting the requested three-day layout.
        // Adding a day must also produce a nontrivial time saving.
        val chosen = four.score.peakMinutes <= three.score.peakMinutes - 4.0 &&
            three.score.peakMinutes > sessionMinutes - 5.0 &&
            four.sessions.all { it.items.isNotEmpty() } &&
            four.sessions.all { session -> session.items.map { it.stableKey }.distinct().size == session.items.size }
        val result = if (chosen) four else three
        val peak = result.score.peakMinutes
        val status = when {
            peak > sessionMinutes -> "ESTIMATED_TIME_OVER_LIMIT_REVIEW_REQUIRED"
            chosen -> "EXPANDED_TO_FOUR_DAYS_ESTIMATED_ONLY"
            else -> "THREE_DAYS_RETAINED_ESTIMATED_ONLY"
        }
        return Result(
            result.sessions, requestedDays, result.sessions.size, chosen, peak,
            three.score.peakMinutes, three.evaluated + four.evaluated,
            "$status; NO_CANONICAL_OFI_OR_TISSUE_CLEARANCE; ESTIMATES_NOT_MEASURED"
        )
    }

    private fun search(
        atoms: List<Atom>,
        days: List<Int>,
        exercises: Map<String, Exercise>,
        requestedMinutes: Int
    ): SearchResult {
        var evaluated = 0
        val n = atoms.size
        val d = days.size

        fun score(assignment: IntArray): Scored {
            evaluated++
            val buckets = Array(d) { mutableListOf<V22ExercisePlan>() }
            atoms.forEachIndexed { index, atom -> buckets[assignment[index]] += atom.item }
            var cost = 0.0
            val sessionTimes = buckets.mapIndexed { index, items ->
                if (items.isEmpty()) cost += 10_000.0
                if (items.map { it.stableKey }.distinct().size != items.size) cost += 10_000.0
                if (items.count { it.stableKey in heavyKeys } > 1) cost += 2_000.0
                val session = V22Session(days[index], items, items.sumOf { it.setCount })
                val time = estimateMinutes(session, exercises)
                cost += 3.0 * max(0.0, time - requestedMinutes + 5.0).let { it * it }
                time
            }
            val peak = sessionTimes.maxOrNull() ?: 0.0
            cost += peak * 0.14
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    val first = atoms[i].item
                    val second = atoms[j].item
                    val delta = abs(days[assignment[i]] - days[assignment[j]])
                    val distance = minOf(delta, 7 - delta)
                    if (assignment[i] == assignment[j]) continue
                    if (distance == 1) {
                        if (first.stableKey == second.stableKey) cost += 20.0
                        if (first.stableKey in heavyKeys && second.stableKey in heavyKeys) cost += 24.0
                        val firstLower = lowerBody(first.stableKey, exercises)
                        val secondLower = lowerBody(second.stableKey, exercises)
                        if (firstLower && secondLower) cost += 2.0
                    }
                    if (distance == 2 && first.stableKey in heavyKeys && second.stableKey in heavyKeys) {
                        cost += 2.0
                    }
                }
            }
            assignment.forEachIndexed { index, day ->
                if (day != atoms[index].originalDay) cost += 0.12
            }
            return Scored(assignment.copyOf(), cost, peak)
        }

        var best: Scored? = null
        // Stable restarts; fixed seed order is reproducible across JVM and Android.
        for (restart in 0 until 8) {
            if (evaluated >= MAX_EVALUATIONS) break
            val starting = IntArray(n) { index ->
                if (restart == 0) minOf(atoms[index].originalDay, d - 1)
                else (index * 7 + restart * 3 + index / 4) % d
            }
            var current = score(starting)
            for (iteration in 0 until 28) {
                if (evaluated >= MAX_EVALUATIONS) break
                var next: Scored? = null
                for (i in 0 until n) {
                    for (day in 0 until d) {
                        if (day == current.assignment[i] || evaluated >= MAX_EVALUATIONS) continue
                        val trial = current.assignment.copyOf().also { it[i] = day }
                        val s = score(trial)
                        if (next == null || s.cost < next.cost - 1e-9) next = s
                    }
                }
                for (i in 0 until n) {
                    for (j in i + 1 until n) {
                        if (current.assignment[i] == current.assignment[j] || evaluated >= MAX_EVALUATIONS) continue
                        val trial = current.assignment.copyOf()
                        val prior = trial[i]
                        trial[i] = trial[j]
                        trial[j] = prior
                        val s = score(trial)
                        if (next == null || s.cost < next.cost - 1e-9) next = s
                    }
                }
                if (next == null || next.cost >= current.cost - 1e-9) break
                current = next
            }
            if (best == null || current.cost < best.cost - 1e-9) best = current
        }
        val final = requireNotNull(best)
        val sessions = days.mapIndexed { dayIndex, weekday ->
            val items = atoms.indices.filter { final.assignment[it] == dayIndex }.map { atoms[it].item }
            V22Session(weekday, items, items.sumOf { it.setCount })
        }
        return SearchResult(sessions, final, evaluated)
    }

    private fun lowerBody(key: String, exercises: Map<String, Exercise>): Boolean {
        if (key in heavyKeys) return true
        val exercise = exercises[key] ?: return false
        return exercise.bodyRegion.contains("LOWER", true) ||
            exercise.movementPattern.contains("LOWER", true) ||
            exercise.movementPattern.contains("SQUAT", true) ||
            exercise.movementPattern.contains("LUNGE", true)
    }
}

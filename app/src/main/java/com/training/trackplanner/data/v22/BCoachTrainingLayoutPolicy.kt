package com.training.trackplanner.data.v22

import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.ProgramSkeletonItem
import kotlin.math.abs

/** Session placement preference, not a physiological dose or injury model.
 * Uses effective typed muscle relations (including user overrides), never name matching. */
internal object BCoachTrainingLayoutPolicy {
    const val VERSION = "B_LAYOUT_HHI_V1"
    const val PREFERENCE_WEIGHT = 0.10
    const val MAX_ROUNDS = 3
    const val MAX_NEIGHBORS = 12
    private const val EPS = 1e-9
    private val missing = setOf("", "NONE", "UNKNOWN", "NOT_APPLICABLE", "UNCLASSIFIED")

    data class Score(val concentration: Double, val comparableVolume: Double) {
        fun loss(preference: BTrainingLayoutPreference): Double =
            if (comparableVolume <= EPS) 0.0 else when (preference) {
                BTrainingLayoutPreference.AUTO_BALANCED -> 0.0
                BTrainingLayoutPreference.FULL_BODY_DISTRIBUTED -> concentration
                BTrainingLayoutPreference.BODY_PART_FOCUSED -> 1.0 - concentration
            }
    }

    fun coefficients(relations: List<CanonicalMetadataRelation>): Map<String, Map<String, Double>> =
        relations.asSequence().filter {
            it.domain == CanonicalRelationDomain.MUSCLE && it.relationValue !in missing &&
                it.relationType in setOf("PRIMARY", "SECONDARY")
        }.groupBy { it.exerciseStableKey }.mapValues { (_, rows) ->
            rows.groupBy { it.relationValue }.mapValues { (_, roles) ->
                if (roles.any { it.relationType == "PRIMARY" }) 1.0 else 0.5
            }
        }

    private data class Group(val week: Int, val block: Int)
    private fun groups(plan: GeneratedProgramSkeleton, calendar: BBlockCalendar?): Map<Group, List<ProgramSkeletonItem>> =
        plan.items.filter { row ->
            !row.selectionRole.contains("PEAK", true) &&
                plan.weekPlans.none { it.weekIndex == row.weekNumber &&
                    (it.deloadFlag || it.weekType.contains("RECOVERY", true) || it.weekType.contains("PEAK", true)) } &&
                (calendar == null || calendar.blockIndex(row.weekNumber, row.dayOfWeek) != null)
        }.groupBy { Group(it.weekNumber, calendar?.blockIndex(it.weekNumber, it.dayOfWeek) ?: 0) }

    private fun movable(row: ProgramSkeletonItem): Boolean =
        !BCoachSessionComposition.isFixedInSession(row)

    /** Only score muscles with two or more independently placeable exposures.
     * One exercise occurrence cannot be split merely to satisfy a mathematical target.
     * N is capped by the number of occurrences as well as the actual training days.
     * Weight muscles by their fixed weekly role-weighted set volume. */
    fun sessionGroupScore(rows: List<ProgramSkeletonItem>, muscle: Map<String, Map<String, Double>>): Score {
        val days = rows.map { it.dayOfWeek }.distinct().size
        if (days < 2) return Score(0.0, 0.0)
        data class Exposure(val day: Int, val volume: Double, val movable: Boolean)
        val exposures = linkedMapOf<String, MutableList<Exposure>>()
        for (row in rows) {
            val sets = ProgramSetPrescriptionResolver.resolve(row).size
            if (sets <= 0 || row.directSportSession || row.rehabLikeActivation) continue
            for ((code, credit) in muscle[row.exerciseStableKey].orEmpty()) {
                if (credit > 0.0) exposures.getOrPut(code) { mutableListOf() }
                    .add(Exposure(row.dayOfWeek, sets * credit, movable(row)))
            }
        }
        var weighted = 0.0
        var volume = 0.0
        for (entries in exposures.values) {
            val n = minOf(days, entries.size)
            if (n < 2 || entries.none { it.movable }) continue
            val total = entries.sumOf { it.volume }
            if (total <= EPS) continue
            val hhi = entries.groupBy { it.day }.values.sumOf { day ->
                val share = day.sumOf { it.volume } / total
                share * share
            }
            val floor = 1.0 / n
            weighted += ((hhi - floor) / (1.0 - floor)).coerceIn(0.0, 1.0) * total
            volume += total
        }
        return Score(if (volume > EPS) weighted / volume else 0.0, volume)
    }

    fun score(plan: GeneratedProgramSkeleton, calendar: BBlockCalendar?,
        muscle: Map<String, Map<String, Double>>): Score {
        val scores = groups(plan, calendar).values.map { sessionGroupScore(it, muscle) }
        val volume = scores.sumOf { it.comparableVolume }
        return Score(if (volume > EPS) scores.sumOf { it.concentration * it.comparableVolume } / volume else 0.0, volume)
    }

    /** The entire exercise row, set prescriptions, load provenance, link identity
     * and per-week roster must survive. Only day/order may differ. */
    fun samePrescription(before: GeneratedProgramSkeleton, after: GeneratedProgramSkeleton): Boolean {
        if (before.items.size != after.items.size || before.progressionSessions != after.progressionSessions ||
            before.weekPlans != after.weekPlans || before.weekDaySchedule != after.weekDaySchedule) return false
        val old = before.items.associateBy { it.localId }
        if (old.size != before.items.size || after.items.map { it.localId }.distinct().size != old.size) return false
        return after.items.all { row ->
            val prior = old[row.localId] ?: return@all false
            row.copy(dayOfWeek = prior.dayOfWeek, orderIndex = prior.orderIndex) == prior &&
                (!BCoachSessionComposition.isFixedInSession(prior) ||
                    (row.dayOfWeek == prior.dayOfWeek && row.orderIndex == prior.orderIndex))
        }
    }

    private data class Pattern(val block: Int, val dayA: Int, val keyA: String,
        val dayB: Int, val keyB: String?)
    private data class Edit(val rows: List<ProgramSkeletonItem>, val gain: Double)

    /** Compare one-way moves and swaps even if the receiving day has no overlap.
     * Repeated key/day patterns are applied within a block, not across a rest break.
     * Pre-rank by preference gain, then re-evaluate shortlisted layouts with the
     * full existing engine. No new exercises, split sets, or extra training days. */
    fun neighbors(plan: GeneratedProgramSkeleton, request: V22GenerationRequest,
        exercises: Map<String, Exercise>, mains: Set<String>,
        muscle: Map<String, Map<String, Double>>): List<Pair<String, GeneratedProgramSkeleton>> {
        val pref = request.trainingLayoutPreference
        if (pref == BTrainingLayoutPreference.AUTO_BALANCED || muscle.isEmpty()) return emptyList()
        val calendar = request.blockProgram?.let(::BBlockCalendar)
        val edits = linkedMapOf<Pattern, MutableList<Edit>>()
        val cap = (request.sessionMinutes ?: BCoachV2Policy.DEFAULT_SESSION_MINUTES).toDouble()
        for ((group, rows) in groups(plan, calendar)) {
            // A peak-containing group is not redistributed even if only its other rows are ordinary.
            if (plan.items.any { it.weekNumber == group.week && it.selectionRole.contains("PEAK", true) }) continue
            val days = rows.groupBy { it.dayOfWeek }.toSortedMap()
            if (days.size < 2) continue
            val before = sessionGroupScore(rows, muscle)
            if (before.comparableVolume <= EPS) continue
            fun offer(a: ProgramSkeletonItem, b: ProgramSkeletonItem?, dayB: Int) {
                val dayA = a.dayOfWeek
                val left = days.getValue(dayA)
                val right = days.getValue(dayB)
                if (b == null && left.size < 2) return
                if (right.any { it.exerciseStableKey == a.exerciseStableKey } ||
                    (b != null && left.any { it.exerciseStableKey == b.exerciseStableKey })) return
                val movedA = a.copy(dayOfWeek = dayB,
                    orderIndex = b?.orderIndex ?: (right.maxOf { it.orderIndex } + 1))
                val movedB = b?.copy(dayOfWeek = dayA, orderIndex = a.orderIndex)
                val newA = BCoachSessionComposition.mainFirst(left.filterNot { it.localId == a.localId } +
                    listOfNotNull(movedB), mains)
                val newB = BCoachSessionComposition.mainFirst(right.filterNot { it.localId == b?.localId } + movedA, mains)
                if (newA.isEmpty() || newB.isEmpty()) return
                if (BCoachV2ScheduleLoad.timeUpper(newA, exercises) > cap + EPS ||
                    BCoachV2ScheduleLoad.timeUpper(newB, exercises) > cap + EPS) return
                val revised = rows.filter { it.dayOfWeek != dayA && it.dayOfWeek != dayB } + newA + newB
                val after = sessionGroupScore(revised, muscle)
                if (abs(before.comparableVolume - after.comparableVolume) > EPS) return
                val gain = (before.loss(pref) - after.loss(pref)) * before.comparableVolume
                if (gain <= EPS) return
                val pattern = Pattern(group.block, dayA, a.exerciseStableKey, dayB, b?.exerciseStableKey)
                edits.getOrPut(pattern) { mutableListOf() }.add(Edit(revised, gain))
            }
            for ((dayA, left) in days) for ((dayB, right) in days) {
                if (dayA == dayB) continue
                for (a in left.filter(::movable)) {
                    offer(a, null, dayB)
                    if (dayA < dayB) for (b in right.filter(::movable)) offer(a, b, dayB)
                }
            }
        }
        return edits.entries.sortedWith(compareByDescending<Map.Entry<Pattern, MutableList<Edit>>> {
            it.value.sumOf { edit -> edit.gain }
        }.thenBy { it.key.block }.thenBy { it.key.dayA }.thenBy { it.key.keyA }
            .thenBy { it.key.dayB }.thenBy { it.key.keyB.orEmpty() })
            .take(MAX_NEIGHBORS).mapNotNull { (pattern, occurrences) ->
                val changed = occurrences.flatMap { it.rows }.associateBy { it.localId }
                val candidate = plan.copy(items = plan.items.map { changed[it.localId] ?: it })
                if (!samePrescription(plan, candidate)) null else
                    ("LAYOUT_${if (pattern.keyB == null) "MOVE" else "SWAP"}_B${pattern.block}_" +
                        "D${pattern.dayA}_${pattern.keyA}_D${pattern.dayB}_${pattern.keyB.orEmpty()}") to candidate
            }
    }
}

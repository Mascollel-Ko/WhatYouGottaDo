package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.analysis.core.CoreClass
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.CanonicalStrengthExposureCapability
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.personalized.BBalancedTaskCatalog
import com.training.trackplanner.data.personalized.BBalancedTaskDomain
import com.training.trackplanner.data.personalized.BBalancedTaskReview
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * B-only bounded search: candidate exercises, physical sets and weekdays are
 * evaluated together. No fixed accessory-set floor or exercise-count cap.
 * Utilities are scheduling heuristics, NOT validated physiological adaptations.
 * The frozen A/C and historical B12 comparator paths must not use this search.
 */
internal object BJointExerciseSetOptimizer {
    private const val BEAM_WIDTH = 6
    private const val MAX_ROUNDS = 14
    private const val MAX_EVALUATIONS = 4400
    private const val MIN_GAIN = 0.001
    private const val EPS = 1e-8

    data class Result(
        val sessions: List<V22Session>,
        val baselineScore: Double,
        val finalScore: Double,
        val evaluated: Int,
        val changes: List<String>,
        val status: String
    ) {
        val changed: Boolean get() = changes.isNotEmpty()
    }

    private data class Facets(
        val primary: Set<String>,
        val core: Set<String>,
        val badminton: Map<BadmintonObjective, Double>,
        val anchor: String?,
        val reviewed: Boolean
    )
    private data class Candidate(
        val sessions: List<V22Session>,
        val score: Double,
        val changes: List<String>,
        val signature: String
    )

    fun optimize(
        sessions: List<V22Session>,
        goalImportance: BBalancedGoalImportance,
        blockGoal: BBlockGoal,
        autoFocus: BBalancedGoal,
        sessionMinutes: Int?,
        exercises: Map<String, Exercise>,
        muscleRelations: List<CanonicalMetadataRelation>,
        coreCatalog: CanonicalCoreCatalog,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        taskCatalog: BBalancedTaskCatalog,
        mainKeys: Set<String>
    ): Result {
        fun unchanged(status: String) = Result(sessions, 0.0, 0.0, 0, emptyList(), status)
        if (sessions.isEmpty() || sessions.any { it.items.isEmpty() } ||
            sessions.map(V22Session::session).distinct().size != sessions.size ||
            sessions.any { it.session !in 1..7 || it.items.any { item ->
                item.setCount <= 0 || item.setCount != item.sets.size
            } }) return unchanged("INVALID_BASELINE")
        if (sessions.flatMap(V22Session::items).size > 72) return unchanged("SEARCH_SIZE_LIMIT")

        val primary = muscleRelations.asSequence().filter {
            it.domain == CanonicalRelationDomain.MUSCLE && it.relationType == "PRIMARY" &&
                it.relationValue.isNotBlank() && !it.status.startsWith("PROVISIONAL", true)
        }.groupBy(CanonicalMetadataRelation::exerciseStableKey)
            .mapValues { (_, rows) -> rows.mapTo(linkedSetOf(), CanonicalMetadataRelation::relationValue) }
        val provisional = muscleRelations.asSequence().filter {
            it.domain == CanonicalRelationDomain.MUSCLE && it.status.startsWith("PROVISIONAL", true)
        }.mapTo(hashSetOf(), CanonicalMetadataRelation::exerciseStableKey)
        val cache = hashMapOf<String, Facets>()
        fun facets(key: String): Facets = cache.getOrPut(key) {
            val exercise = exercises[key]
            val reviewedCore = taskCatalog.forStableKey(key).filter {
                it.domain == BBalancedTaskDomain.CORE && it.review == BBalancedTaskReview.REVIEWED
            }.mapTo(linkedSetOf()) { it.task }
            val directCore = coreCatalog.resolve(key)?.coreClass == CoreClass.DIRECT
            val sport = badmintonCatalog.relations(key).associate {
                it.objective to when (it.transferLevel) {
                    BadmintonObjectiveTransferLevel.DIRECT -> 1.0
                    BadmintonObjectiveTransferLevel.SUPPORTIVE -> 0.6
                    BadmintonObjectiveTransferLevel.GENERAL -> 0.25
                    BadmintonObjectiveTransferLevel.LOW -> 0.1
                    BadmintonObjectiveTransferLevel.NONE -> 0.0
                }
            }.filterValues { it > 0 }
            Facets(primary[key].orEmpty(),
                if (directCore) reviewedCore.ifEmpty { setOf("DIRECT_CORE") } else emptySet(),
                sport,
                CanonicalStrengthExposureCapability.movementAnchor(key)?.name,
                exercise != null && exercise.isActive && !exercise.needsReview &&
                    !exercise.isCustom && exercise.planningEligibility != "HISTORY_ONLY" &&
                    key !in provisional &&
                    (primary[key]?.isNotEmpty() == true || directCore || sport.isNotEmpty()))
        }
        // MAIN_STRENGTH_SLOT is a capability, not a user lock. Protect explicit mains
        // and the dominant prescribed strength anchor, not every capable accessory.
        val explicitMains = sessions.flatMap(V22Session::items).filter {
            it.role.contains("MAIN", true)
        }.mapTo(hashSetOf(), V22ExercisePlan::stableKey)
        val anchorMains = sessions.flatMap(V22Session::items).filter { item ->
            item.stableKey in mainKeys && item.setCount >= 2 &&
                item.sets.any { (it.reps ?: 0) in 1..6 } &&
                CanonicalStrengthExposureCapability.movementAnchor(item.stableKey) != null
        }.groupBy { CanonicalStrengthExposureCapability.movementAnchor(it.stableKey) }
            .values.mapNotNull { group ->
                group.maxWithOrNull(compareBy<V22ExercisePlan> { it.setCount }
                    .thenByDescending { it.stableKey })?.stableKey
            }.toSet()
        fun isProtected(item: V22ExercisePlan): Boolean =
            item.stableKey in explicitMains || item.stableKey in anchorMains ||
                !facets(item.stableKey).reviewed

        val originalMain = sessions.flatMap { day ->
            day.items.filter(::isProtected).map { item -> day.session to item }
        }
        val initialKeys = sessions.flatMap(V22Session::items).mapTo(linkedSetOf(), V22ExercisePlan::stableKey)
        val mustKeepMuscles = initialKeys.flatMap { facets(it).primary }.toSet()
        val mustKeepCore = initialKeys.flatMap { facets(it).core }.toSet()
        val mustKeepSport = initialKeys.flatMap { facets(it).badminton.keys }.toSet()
        val mainAnchors = sessions.flatMap(V22Session::items).filter { item ->
            CanonicalStrengthExposureCapability.strengthPossible(item.stableKey) &&
                item.sets.any { (it.reps ?: 0) in 1..6 }
        }.mapNotNull { facets(it.stableKey).anchor }.distinct()
        val originalTimes = sessions.map { BBalancedDayPlacementPlanner.estimateMinutes(it, exercises) }
        val maxTime = sessionMinutes?.takeIf { it > 0 }
        val weights = BBalancedGoal.entries.associateWith { (goalImportance.value(it) ?: 3).toDouble() }
        val weightSum = weights.values.sum().coerceAtLeast(1.0)

        fun signature(plan: List<V22Session>): String = plan.joinToString("|") { day ->
            day.session.toString() + ":" + day.items.joinToString(",") { item ->
                item.stableKey + "=" + item.setCount
            }
        }
        fun rebuilt(day: V22Session, items: List<V22ExercisePlan>) =
            day.copy(items = items, nominalSets = items.sumOf(V22ExercisePlan::setCount))
        fun valid(plan: List<V22Session>): Boolean {
            if (plan.map(V22Session::session) != sessions.map(V22Session::session)) return false
            if (plan.any { day -> day.items.isEmpty() ||
                day.items.any { it.setCount !in 1..6 || it.setCount != it.sets.size } ||
                day.items.map(V22ExercisePlan::stableKey).distinct().size != day.items.size }) return false
            if (plan.flatMap { day ->
                day.items.filter(::isProtected).map { item -> day.session to item }
            } != originalMain) return false
            val keys = plan.flatMap(V22Session::items).mapTo(linkedSetOf(), V22ExercisePlan::stableKey)
            for (index in plan.indices) {
                val after = BBalancedDayPlacementPlanner.estimateMinutes(plan[index], exercises)
                val before = originalTimes[index]
                if (maxTime != null && if (before <= maxTime + EPS)
                    after > maxTime + EPS else after > before + EPS) return false
            }
            return true
        }

        // Each goal retains its own unit; these bounded values only rank candidate
        // schedules. Saturation scales are heuristics, not prescribed sets or targets.
        fun score(plan: List<V22Session>): Double {
            val mainSets = linkedMapOf<String, Int>()
            val muscles = linkedMapOf<String, Double>()
            val core = linkedMapOf<String, Double>()
            val sport = linkedMapOf<BadmintonObjective, Double>()
            val items = plan.flatMap(V22Session::items)
            for (item in items) {
                val facet = facets(item.stableKey)
                if (facet.anchor != null &&
                    item.sets.any { (it.reps ?: 0) in 1..6 } &&
                    CanonicalStrengthExposureCapability.strengthPossible(item.stableKey)) {
                    mainSets[facet.anchor] = (mainSets[facet.anchor] ?: 0) + item.setCount
                }
                facet.primary.forEach { muscles[it] = (muscles[it] ?: 0.0) + item.setCount }
                facet.core.forEach { core[it] = (core[it] ?: 0.0) + item.setCount }
                facet.badminton.forEach { (objective, coefficient) ->
                    sport[objective] = (sport[objective] ?: 0.0) + item.setCount * coefficient
                }
            }
            // The first supported exposure is more valuable than stacking another
            // nearly identical one. Saturation scales are planning heuristics.
            fun diminishing(value: Double, scale: Double) = 1.0 - exp(-value / scale)
            val strengthUtility = if (mainAnchors.isEmpty()) 0.0 else mainAnchors.map {
                diminishing((mainSets[it] ?: 0).toDouble(), 1.75)
            }.average()
            val hypertrophyBase = if (mustKeepMuscles.isEmpty()) 0.0 else mustKeepMuscles.map {
                diminishing(muscles[it] ?: 0.0, 1.6)
            }.average()
            val additionalMuscles = muscles.keys - mustKeepMuscles
            val novelMuscleCoverage = additionalMuscles.sumOf {
                diminishing(muscles[it] ?: 0.0, 1.6)
            } / max(5, mustKeepMuscles.size)
            val hypertrophyUtility = hypertrophyBase + 0.30 * novelMuscleCoverage
            val coreUtility = if (mustKeepCore.isEmpty()) 0.0 else mustKeepCore.map {
                diminishing(core[it] ?: 0.0, 1.4)
            }.average()
            val sportUtility = if (mustKeepSport.isEmpty()) 0.0 else mustKeepSport.map {
                diminishing(sport[it] ?: 0.0, 1.5)
            }.average()
            val utility = (
                weights.getValue(BBalancedGoal.STRENGTH) * strengthUtility +
                weights.getValue(BBalancedGoal.HYPERTROPHY) * hypertrophyUtility +
                weights.getValue(BBalancedGoal.CORE) * coreUtility +
                weights.getValue(BBalancedGoal.SPORT_SUPPORT) * sportUtility
            ) / weightSum
            val duration = plan.sumOf { BBalancedDayPlacementPlanner.estimateMinutes(it, exercises) }
            val newKeys = items.map(V22ExercisePlan::stableKey).toSet() - initialKeys
            val adjacent = plan.indices.sumOf { i ->
                val firstDay = plan[i]
                val first = firstDay.items.flatMap { item ->
                    facets(item.stableKey).primary.map { it to item.setCount }
                }.groupBy({ it.first }, { it.second }).mapValues { (_, counts) -> counts.sum() }
                plan.indices.filter { j ->
                    j > i && minOf(abs(firstDay.session - plan[j].session),
                        7 - abs(firstDay.session - plan[j].session)) == 1
                }.sumOf { j ->
                    val second = plan[j].items.flatMap { item ->
                        facets(item.stableKey).primary.map { it to item.setCount }
                    }.groupBy({ it.first }, { it.second }).mapValues { (_, counts) -> counts.sum() }
                    first.entries.sumOf { (muscle, count) -> min(count, second[muscle] ?: 0) }
                }
            }
            // Complementary movement-pattern balance is soft: one set of push is
            // never automatically forbidden by a pull deficit.
            val pushSets = items.sumOf { item ->
                if (exercises[item.stableKey]?.movementPattern.orEmpty().uppercase()
                        .contains("PUSH")) item.setCount else 0
            }
            val pullSets = items.sumOf { item ->
                if (exercises[item.stableKey]?.movementPattern.orEmpty().uppercase()
                        .contains("PULL")) item.setCount else 0
            }
            val directionalImbalance = if (pushSets + pullSets == 0) 0.0 else
                abs(pushSets - pullSets).toDouble() / (pushSets + pullSets)
            // Set volume, exercise setup, repeated local exposure and session
            // time are resource costs. Do not penalize 1-set exercises by fiat.
            val physicalSets = items.sumOf(V22ExercisePlan::setCount)
            return utility - 0.012 * physicalSets - 0.014 * items.size -
                0.10 * duration / ((maxTime ?: 60) * plan.size) -
                0.010 * newKeys.size - 0.0020 * adjacent -
                0.045 * directionalImbalance
        }

        val baseline = score(sessions)
        val start = Candidate(sessions, baseline, emptyList(), signature(sessions))
        var best = start
        var beam = listOf(start)
        var evaluated = 1
        val eligible = exercises.values.filter { exercise ->
            val profile = facets(exercise.stableKey)
            profile.reviewed && (profile.primary.isNotEmpty() ||
                profile.core.isNotEmpty() || profile.badminton.isNotEmpty())
        }.sortedBy(Exercise::stableKey)

        for (round in 0 until MAX_ROUNDS) {
            if (evaluated >= MAX_EVALUATIONS) break
            val pool = linkedMapOf<String, Candidate>()
            fun offer(plan: List<V22Session>, oldPath: List<String>, action: String) {
                if (evaluated >= MAX_EVALUATIONS || !valid(plan)) return
                val key = signature(plan)
                if (key in pool) return
                evaluated++
                val value = score(plan)
                if (value < baseline - 0.025) return
                val next = Candidate(plan, value, oldPath + action, key)
                pool[key] = next
                if (value > best.score + MIN_GAIN ||
                    (abs(value - best.score) <= MIN_GAIN &&
                        value > baseline + MIN_GAIN && key < best.signature)) best = next
            }
            for (node in beam) {
                for (dayIndex in node.sessions.indices) {
                    val day = node.sessions[dayIndex]
                    for (index in day.items.indices) {
                        val item = day.items[index]
                        if (isProtected(item)) continue
                        val key = item.stableKey
                        fun modify(items: List<V22ExercisePlan>, action: String) {
                            val candidate = node.sessions.toMutableList()
                            candidate[dayIndex] = rebuilt(day, items)
                            offer(candidate, node.changes, action)
                        }
                        if (item.setCount < 4) {
                            val additional = item.sets.last().copy(weightKg = null,
                                observedRpe = null, sourceDate = "",
                                loadStatus = "B_JOINT_EXTRA_SET_USER_REVIEW")
                            modify(day.items.toMutableList().also {
                                it[index] = item.copy(setCount = item.setCount + 1,
                                    sets = item.sets + additional)
                            }, "ADD_SET:" + day.session + ":" + key)
                        }
                        if (item.setCount > 1) {
                            modify(day.items.toMutableList().also {
                                it[index] = item.copy(setCount = item.setCount - 1,
                                    sets = item.sets.dropLast(1))
                            }, "REMOVE_SET:" + day.session + ":" + key)
                        } else if (day.items.size > 1) {
                            modify(day.items.filterIndexed { position, _ -> position != index },
                                "DROP_REDUNDANT:" + day.session + ":" + key)
                        }
                        for (otherIndex in node.sessions.indices) {
                            if (otherIndex == dayIndex || day.items.size <= 1) continue
                            val other = node.sessions[otherIndex]
                            if (other.items.any { it.stableKey == key }) continue
                            val updated = node.sessions.toMutableList()
                            updated[dayIndex] = rebuilt(day,
                                day.items.filterIndexed { position, _ -> position != index })
                            updated[otherIndex] = rebuilt(other, other.items + item)
                            offer(updated, node.changes,
                                "MOVE:" + day.session + "->" + other.session + ":" + key)
                        }
                        if (item.setCount > 2) continue
                        val old = exercises[key] ?: continue
                        val used = node.sessions.flatMap(V22Session::items)
                            .mapTo(hashSetOf(), V22ExercisePlan::stableKey)
                        val replacements = eligible.asSequence().filter {
                            it.stableKey !in used && it.category == old.category &&
                                it.equipment == old.equipment &&
                                it.equipmentTags == old.equipmentTags &&
                                it.defaultRestSeconds <= max(old.defaultRestSeconds, 60) &&
                                it.progressMetricType == old.progressMetricType
                        }.map {
                            val before = facets(key)
                            val after = facets(it.stableKey)
                            val novelty = (after.primary - before.primary).size +
                                2 * (after.core - before.core).size +
                                2 * (after.badminton.keys - before.badminton.keys).size
                            novelty to it
                        }.sortedWith(compareByDescending<Pair<Int, Exercise>> { it.first }
                            .thenBy { it.second.stableKey }).take(4).map { it.second }.toList()
                        for (replacement in replacements) {
                            val effective = if (blockGoal == BBlockGoal.AUTO) when (autoFocus) {
                                BBalancedGoal.STRENGTH -> BBlockGoal.STRENGTH
                                BBalancedGoal.HYPERTROPHY -> BBlockGoal.HYPERTROPHY
                                BBalancedGoal.CORE, BBalancedGoal.SPORT_SUPPORT -> BBlockGoal.SPORT_SUPPORT
                            } else blockGoal
                            val purpose = BBlockPurposePrescription.purpose(effective, false, replacement)
                            val repsRange = BBlockPurposePrescription.repRange(purpose, false)
                            val reps = if (replacement.progressMetricType == "TIME_OR_DISTANCE") null
                                else (repsRange.first + repsRange.last) / 2
                            for (setCount in 1..2) {
                                val changed = item.copy(
                                    exercise = replacement.name, stableKey = replacement.stableKey,
                                    category = replacement.category, setCount = setCount,
                                    sets = List(setCount) {
                                        V22ObservedSet(null, reps, null, null, "",
                                            "B_JOINT_NEW_EXERCISE_USER_CALIBRATION_REQUIRED")
                                    },
                                    historicalSets28d = 0, historicalSets56d = 0,
                                    source = "B_JOINT_REPLACEMENT_USER_REVIEW_REQUIRED",
                                    prescriptionAuthority = "B_JOINT_NEW_EXERCISE_REVIEW_REQUIRED",
                                    plannedProgression = "B_JOINT_REPLACEMENT_NO_INHERITED_LOAD_OR_REPS"
                                )
                                modify(day.items.toMutableList().also { it[index] = changed },
                                    "REPLACE:" + day.session + ":" + key + "->" +
                                        replacement.stableKey + ":" + setCount)
                            }
                        }
                    }
                }
            }
            if (pool.isEmpty()) break
            beam = pool.values.sortedWith(compareByDescending<Candidate> { it.score }
                .thenBy { it.signature }).take(BEAM_WIDTH)
        }
        return if (best.score <= baseline + MIN_GAIN)
            Result(sessions, baseline, baseline, evaluated, emptyList(), "NO_PROVEN_IMPROVEMENT")
        else Result(best.sessions, baseline, best.score, evaluated,
            best.changes, "IMPROVED_BOUNDED_JOINT_SEARCH_USER_REVIEW")
    }
}

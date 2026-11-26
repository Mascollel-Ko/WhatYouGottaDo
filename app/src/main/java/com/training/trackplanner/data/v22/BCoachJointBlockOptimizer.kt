package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.analysis.core.CoreClass
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.CanonicalStrengthExposureCapability
import com.training.trackplanner.data.Exercise
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * B block-only planning heuristic. All weekday/roster/set/strategy candidates
 * receive the SAME whole-block score, including periodized weekly prescriptions.
 * No score is a physiological adaptation prediction or medical fatigue diagnosis.
 *
 * Historical observations are not proof that earlier planned block work was done.
 * A future block is a conditional proposal until actual, linked execution exists.
 */
internal object BCoachJointBlockOptimizer {
    private const val MAX_EVALUATIONS = 3600
    private const val BEAM_WIDTH = 7
    private const val ROUNDS = 11
    private const val EPS = 1e-8

    enum class Strategy { DEVELOP, MAINTAIN, RECOVER, REDISTRIBUTE }

    data class Evidence(
        val completedObservedSets28d: Int,
        val completedObservedSets56d: Int,
        val observedTrainingDates28d: Int,
        val comparableStrengthKeys: Int,
        val relativeStrengthTrend: Double?,
        val ratedSets28d: Int,
        val meanObservedRpe28d: Double?,
        val plannedCompletionRate: Double?,
        val authority: String
    )

    data class Alternative(
        val strategy: Strategy,
        val weekdays: List<Int>,
        val weeklySets: Int,
        val peakMinutes: Double,
        val score: Double,
        val status: String
    )

    data class Result(
        val sessions: List<V22Session>,
        val strategy: Strategy,
        val evidence: Evidence,
        val originalScore: Double,
        val finalScore: Double,
        val evaluated: Int,
        val alternatives: List<Alternative>,
        val changes: List<String>,
        val status: String,
        val candidateDiagnostics: List<String> = emptyList()
    ) {
        val changed: Boolean get() = changes.isNotEmpty()
    }

    private data class Profile(
        val primary: Set<String>,
        val secondary: Set<String>,
        val directCore: Boolean,
        val sport: Double,
        val anchor: String?,
        val eligible: Boolean
    )

    private data class Scored(
        val sessions: List<V22Session>,
        val score: Double,
        val strategy: Strategy,
        val strategyScores: Map<Strategy, Double>,
        val peakMinutes: Double,
        val feasible: Boolean,
        val signature: String,
        val changes: List<String>
    )

    fun evidence(
        rows: List<V22HistoryRow>, asOf: LocalDate,
        linkedDuePlannedSets: Int? = null, linkedConfirmedSets: Int? = null
    ): Evidence {
        val recent28 = rows.filter { !it.date.isAfter(asOf) && !it.date.isBefore(asOf.minusDays(27)) }
        val recent56 = rows.filter { !it.date.isAfter(asOf) && !it.date.isBefore(asOf.minusDays(55)) }
        // Rows represent recorded sets. There is no guaranteed completed-plan link
        // in V22HistoryRow, so planned completion must remain UNKNOWN.
        val valid = recent56.filter { it.weightKg != null && it.weightKg > 0.0 &&
            it.reps != null && it.reps in 1.0..12.0 }
        val trends = valid.groupBy(V22HistoryRow::stableKey).values.mapNotNull { group ->
            val byDate = group.groupBy(V22HistoryRow::date).toSortedMap()
            if (byDate.size < 2) null else {
                val first = byDate.values.first().maxOf { it.weightKg!! * (1.0 + it.reps!! / 30.0) }
                val last = byDate.values.last().maxOf { it.weightKg!! * (1.0 + it.reps!! / 30.0) }
                if (first > 0.0) ((last - first) / first).coerceIn(-0.25, 0.25) else null
            }
        }
        val rated = recent28.mapNotNull(V22HistoryRow::rpe).filter { it in 1.0..10.0 }
        val completion = if (linkedDuePlannedSets != null && linkedDuePlannedSets > 0 &&
            linkedConfirmedSets != null && linkedConfirmedSets >= 0)
            (linkedConfirmedSets.toDouble() / linkedDuePlannedSets).coerceIn(0.0, 1.0)
            else null
        return Evidence(
            completedObservedSets28d = recent28.size,
            completedObservedSets56d = recent56.size,
            observedTrainingDates28d = recent28.map(V22HistoryRow::date).distinct().size,
            comparableStrengthKeys = trends.size,
            relativeStrengthTrend = trends.takeIf { it.size >= 2 }?.average(),
            ratedSets28d = rated.size,
            meanObservedRpe28d = rated.takeIf { it.size >= 6 }?.average(),
            plannedCompletionRate = completion,
            authority = if (completion != null) "LINKED_SAME_APPLICATION_CONFIRMED_SETS"
                else "OBSERVED_WORKOUT_ROWS_ONLY_NO_PLAN_EXECUTION_LINK"
        )
    }

    /**
     * originalMains is a selected prescription, not MAIN_STRENGTH_SLOT capability.
     * Keep explicit main roles, and retain the strongest low-rep anchor when present.
     */
    fun selectedMainKeys(sessions: List<V22Session>, capabilityKeys: Set<String>): Set<String> {
        val items = sessions.flatMap(V22Session::items)
        val explicit = items.filter { it.role.contains("MAIN", ignoreCase = true) }
            .mapTo(linkedSetOf(), V22ExercisePlan::stableKey)
        val anchors = items.filter { it.stableKey in capabilityKeys &&
            it.setCount >= 2 && it.sets.any { set -> (set.reps ?: 0) in 1..6 } }
            .groupBy { CanonicalStrengthExposureCapability.movementAnchor(it.stableKey) }
            .filterKeys { it != null }.values.mapNotNull { candidates ->
                candidates.maxWithOrNull(compareBy<V22ExercisePlan> { it.setCount }
                    .thenByDescending { it.stableKey })?.stableKey
            }
        return explicit + anchors
    }

    fun optimize(
        baseline: List<V22Session>,
        block: BBlockDefinition,
        focus: BBalancedGoal,
        request: V22GenerationRequest,
        history: List<V22HistoryRow>,
        exercises: Map<String, Exercise>,
        muscleRelations: List<CanonicalMetadataRelation>,
        coreCatalog: CanonicalCoreCatalog,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        capabilityKeys: Set<String>
    ): Result {
        val observations = evidence(history, request.asOf,
            request.linkedDuePlannedSets, request.linkedConfirmedSets)
        fun unchanged(status: String) = Result(baseline, Strategy.MAINTAIN,
            observations, 0.0, 0.0, 0, emptyList(), emptyList(), status)
        if (baseline.size != request.days || baseline.isEmpty() ||
            baseline.any { it.items.isEmpty() || it.items.any { item ->
                item.setCount != item.sets.size ||
                    item.setCount !in 1..BCoachV2Policy.MAX_PER_EXERCISE_SETS
            } }) return unchanged("INVALID_BASELINE")
        val baseDays = baseline.map(V22Session::session)
        if (baseDays.distinct().size != baseDays.size || baseDays.any { it !in 1..7 })
            return unchanged("INVALID_WEEKDAYS")

        val indexed = muscleRelations.filter { it.domain == CanonicalRelationDomain.MUSCLE &&
            it.relationValue.isNotBlank() && !it.status.startsWith("PROVISIONAL", true) }
            .groupBy(CanonicalMetadataRelation::exerciseStableKey)
        val profiles = hashMapOf<String, Profile>()
        fun profile(key: String): Profile = profiles.getOrPut(key) {
            val e = exercises[key]
            val muscle = indexed[key].orEmpty()
            val primary = muscle.filter { it.relationType == "PRIMARY" }
                .mapTo(linkedSetOf(), CanonicalMetadataRelation::relationValue)
            val secondary = muscle.filter { it.relationType == "SECONDARY" }
                .mapTo(linkedSetOf(), CanonicalMetadataRelation::relationValue)
            val core = coreCatalog.resolve(key)?.coreClass == CoreClass.DIRECT
            val sport = badmintonCatalog.relations(key).sumOf { row ->
                when (row.transferLevel) {
                    BadmintonObjectiveTransferLevel.DIRECT -> 1.0
                    BadmintonObjectiveTransferLevel.SUPPORTIVE -> 0.55
                    BadmintonObjectiveTransferLevel.GENERAL -> 0.2
                    BadmintonObjectiveTransferLevel.LOW -> 0.07
                    BadmintonObjectiveTransferLevel.NONE -> 0.0
                }
            }
            Profile(primary, secondary, core, sport,
                CanonicalStrengthExposureCapability.movementAnchor(key)?.name,
                e != null && e.isActive && !e.needsReview && !e.isCustom &&
                    e.activityKind != "SPORT_SESSION" &&
                    e.planningEligibility !in setOf("HISTORY_ONLY", "FATIGUE_ONLY", "ANALYSIS_ONLY") &&
                    (primary.isNotEmpty() || core || sport > 0.0))
        }
        val baselineItems = baseline.flatMap(V22Session::items)
        val initialKeys = baselineItems.mapTo(linkedSetOf(), V22ExercisePlan::stableKey)
        val selectedMains = selectedMainKeys(baseline, capabilityKeys)
        val locked = initialKeys.filterTo(linkedSetOf()) { key ->
            key in selectedMains || !profile(key).eligible
        }
        val initialCounts = baselineItems.groupBy(V22ExercisePlan::stableKey)
            .mapValues { (_, items) -> items.sumOf(V22ExercisePlan::setCount) }
        val referenceSets = baselineItems.sumOf(V22ExercisePlan::setCount).toDouble()
            .coerceAtLeast(1.0)
        val majorMuscles = listOf("BACK_LATS", "CHEST", "SHOULDERS",
            "QUADRICEPS", "GLUTES", "HAMSTRINGS")
        fun equipmentTokens(value: String): Set<String> = value
            .split(Regex("[/,;+&]"))
            .map { it.trim().uppercase() }.filter(String::isNotEmpty).toSet()
        val availableEquipment = request.availableEquipment
            ?.flatMap(::equipmentTokens)?.toSet()
        fun permitted(e: Exercise): Boolean {
            val required = equipmentTokens(e.equipment)
            if (required.isEmpty() || required.all { it == "BODYWEIGHT" || it == "맨몸" }) return true
            return availableEquipment != null && required.all { it in availableEquipment }
        }
        val eligible = exercises.values.asSequence().filter { profile(it.stableKey).eligible &&
            !BCoachV2Policy.isTestOnlyExercise(it) &&
            permitted(it) }.sortedBy(Exercise::stableKey).toList()
        val shortlistedSportKeys = linkedSetOf<String>()
        val scoredSportKeys = linkedSetOf<String>()
        val muscleUniverse = (baselineItems.flatMap { profile(it.stableKey).primary } +
            majorMuscles.filter { muscle ->
                eligible.any { muscle in profile(it.stableKey).primary }
            }).distinct()
        val strengthTargetKeys = BCoachStrengthCoveragePolicy.targetKeys(exercises, request.availableEquipment, initialKeys)
        val strengthTarget = BCoachV2Policy.d0(BBalancedGoal.STRENGTH, request.bGoalImportance.strength ?: 3)
        val goalWeights = BBalancedGoal.entries.associateWith {
            (request.bGoalImportance.value(it) ?: 3).toDouble()
        }
        // Every canonical objective is evaluated separately. Summing all transfer
        // links into one scalar and clipping each exercise at 1.5 destroyed
        // distinctions between otherwise different badminton exercises.
        val sportObjectives = badmintonCatalog.allRelations()
            .map { it.objective.name }.distinct().sorted()
        val weightTotal = goalWeights.values.sum().coerceAtLeast(1.0)
        val duration = request.sessionMinutes
        val rawCandidateDays = if (request.fixedWeekdays != null) {
            listOf(request.fixedWeekdays.sorted())
        } else {
            val allowed = request.allowedWeekdays ?: (1..7).toSet()
            fun combinations(source: List<Int>, n: Int): List<List<Int>> =
                if (n == 0) listOf(emptyList()) else source.flatMapIndexed { i, day ->
                    combinations(source.drop(i + 1), n - 1).map { listOf(day) + it }
                }
            combinations(allowed.sorted(), request.days)
        }.filter { it.size == request.days && it.distinct().size == request.days &&
            it.all { d -> d in 1..7 } }
        fun longestConsecutiveRun(days: List<Int>): Int {
            val selected = days.toSet()
            return selected.maxOfOrNull { start ->
                var count = 0
                var day = start
                while (count < 7 && day in selected) {
                    count++
                    day = day % 7 + 1
                }
                count
            } ?: 0
        }
        val nonClusteredDays = rawCandidateDays.filter { longestConsecutiveRun(it) < 3 }
        val candidateDays = if (request.fixedWeekdays == null && nonClusteredDays.isNotEmpty()) {
            nonClusteredDays
        } else rawCandidateDays
        if (candidateDays.isEmpty()) return unchanged("NO_WEEKDAY_COMBINATION_ALLOWED")

        val amplitude = BBlockPrescriptionApplicator.resolveAmplitude(
            block, history, request.asOf, baseline
        )
        fun makeWeeks(sessions: List<V22Session>): List<List<V22Session>> =
            BBlockPeriodization.steps(block).mapIndexed { index, stage ->
                val mainForPeriodization = selectedMains + sessions.flatMap(V22Session::items)
                    .filter { it.role.contains("MAIN", ignoreCase = true) }.map(V22ExercisePlan::stableKey)
                sessions.map { day ->
                    val adjusted = BBlockPrescriptionApplicator.apply(day, stage, focus,
                        block, mainForPeriodization, exercises, amplitude)
                    BBlockPurposePrescription.finishWeek(adjusted, block,
                        index == block.weeks - 1, mainForPeriodization)
                }
            }
        fun signature(sessions: List<V22Session>): String = sessions.joinToString("|") { day ->
            day.session.toString() + ":" + day.items.joinToString(",") { item ->
                item.stableKey + "=" + item.setCount
            }
        }
        fun rebuilt(day: V22Session, items: List<V22ExercisePlan>) =
            day.copy(items = items, nominalSets = items.sumOf(V22ExercisePlan::setCount))
        fun minimumSets(key: String): Int {
            val exercise = exercises[key]
            if (exercise?.progressMetricType == "TIME_OR_DISTANCE") return 1
            val kind = listOf(exercise?.category, exercise?.movementPattern)
                .joinToString(" ").uppercase()
            if (listOf("RECOVERY", "MOBILITY", "TEST", "DRILL").any(kind::contains)) return 1
            return if (key in selectedMains || (key !in initialKeys &&
                CanonicalStrengthExposureCapability.strengthPossible(key))) 3 else 2
        }
        fun valid(sessions: List<V22Session>): Boolean =
            sessions.size == request.days &&
                sessions.map(V22Session::session).toSet().size == request.days &&
                sessions.all { day ->
                    day.items.isNotEmpty() &&
                        day.items.map(V22ExercisePlan::stableKey).distinct().size == day.items.size &&
                        day.items.all {
                            it.setCount in 1..BCoachV2Policy.MAX_PER_EXERCISE_SETS &&
                                it.setCount == it.sets.size
                        }
                } && baselineItems.filter { it.stableKey in locked }
                    .groupingBy(V22ExercisePlan::stableKey).eachCount() ==
                    sessions.flatMap(V22Session::items)
                        .filter { it.stableKey in locked }
                        .groupingBy(V22ExercisePlan::stableKey).eachCount()

        // Scores are normalized, intentionally interpretable heuristic units.
        fun saturate(value: Double, scale: Double) = 1.0 - exp(-value.coerceAtLeast(0.0) / scale)
        fun score(sessions: List<V22Session>, changes: List<String>): Scored {
            val weekly = makeWeeks(sessions)
            var peak = 0.0
            var benefit = 0.0
            var burden = 0.0
            var totalSets = 0.0
            var overtime = 0.0
            for (week in weekly) {
                val all = week.flatMap(V22Session::items)
                val daySets = week.map { it.nominalSets.toDouble() }
                val dayTimes = week.map { BBalancedDayPlacementPlanner.estimateMinutes(it, exercises) }
                peak = max(peak, dayTimes.maxOrNull() ?: 0.0)
                if (duration != null) overtime += dayTimes.sumOf {
                    val over = max(0.0, it - duration)
                    over * over
                }
                totalSets += daySets.sum()
                val muscleDose = linkedMapOf<String, Double>()
                val strengthDose = linkedMapOf<String, Double>()
                var coreDose = 0.0
                val sportDoseByObjective = linkedMapOf<String, Double>()
                val patterns = linkedMapOf<String, Double>()
                for (item in all) {
                    val p = profile(item.stableKey)
                    p.primary.forEach {
                        muscleDose[it] = (muscleDose[it] ?: 0.0) + item.setCount
                    }
                    p.secondary.filter { it !in p.primary }.forEach {
                        muscleDose[it] = (muscleDose[it] ?: 0.0) + 0.35 * item.setCount
                    }
                    if (p.anchor != null) {
                        strengthDose[item.stableKey] = (strengthDose[item.stableKey] ?: 0.0) +
                            item.sets.count { (it.reps ?: 0) in 1..6 }
                    }
                    if (p.directCore) coreDose += item.setCount
                    badmintonCatalog.relations(item.stableKey).forEach { relation ->
                        val credit = when (relation.transferLevel) {
                            BadmintonObjectiveTransferLevel.DIRECT -> 1.0
                            BadmintonObjectiveTransferLevel.SUPPORTIVE -> 0.55
                            BadmintonObjectiveTransferLevel.GENERAL -> 0.2
                            BadmintonObjectiveTransferLevel.LOW -> 0.07
                            BadmintonObjectiveTransferLevel.NONE -> 0.0
                        }
                        val objective = relation.objective.name
                        sportDoseByObjective[objective] =
                            (sportDoseByObjective[objective] ?: 0.0) + item.setCount * credit
                    }
                    val pattern = exercises[item.stableKey]?.movementPattern.orEmpty().uppercase()
                    val className = when {
                        "PULL" in pattern || "ROW" in pattern -> "PULL"
                        "PUSH" in pattern || "PRESS" in pattern -> "PUSH"
                        "SQUAT" in pattern || "LUNGE" in pattern -> "SQUAT"
                        "HINGE" in pattern || "DEADLIFT" in pattern -> "HINGE"
                        else -> null
                    }
                    if (className != null) patterns[className] =
                        (patterns[className] ?: 0.0) + item.setCount
                }
                val muscleUtility = if (muscleUniverse.isEmpty()) 0.0 else
                    muscleUniverse.map { saturate(muscleDose[it] ?: 0.0, 5.0) }.average()
                val strengthUtility = BCoachStrengthCoveragePolicy.meanCoverage(strengthTargetKeys,
                    strengthDose, strengthTarget)
                val coreUtility = saturate(coreDose, 4.0)
                // Preserve the full objective vector: a set contributes only to
                // relations explicitly reviewed for its own stableKey.
                // The 6.0 scale is a provisional scheduling heuristic, not a
                // validated sporting adaptation or a mandatory training target.
                val sportUtility = if (sportObjectives.isEmpty()) 0.0 else
                    sportObjectives.map { objective ->
                        saturate(sportDoseByObjective[objective] ?: 0.0, 6.0)
                    }.average()
                val weighted = (
                    goalWeights.getValue(BBalancedGoal.STRENGTH) * strengthUtility +
                    goalWeights.getValue(BBalancedGoal.HYPERTROPHY) * muscleUtility +
                    goalWeights.getValue(BBalancedGoal.CORE) * coreUtility +
                    goalWeights.getValue(BBalancedGoal.SPORT_SUPPORT) * sportUtility
                ) / weightTotal
                // Pattern coverage is a small tie-breaker, never a substitute for target dose.
                val patternUtility = listOf("PUSH", "PULL", "SQUAT", "HINGE")
                    .count { (patterns[it] ?: 0.0) > 0.0 } / 4.0
                benefit += weighted + 0.025 * patternUtility
                val avgSets = daySets.average().coerceAtLeast(1.0)
                val setSpread = daySets.sumOf { (it - avgSets) * (it - avgSets) } /
                    (daySets.size * avgSets * avgSets)
                val avgTime = dayTimes.average().coerceAtLeast(1.0)
                val timeSpread = dayTimes.sumOf { (it - avgTime) * (it - avgTime) } /
                    (dayTimes.size * avgTime * avgTime)
                burden += 0.075 * setSpread + 0.08 * timeSpread
                for (i in week.indices) for (j in i + 1 until week.size) {
                    val gap = abs(week[i].session - week[j].session)
                    if (min(gap, 7 - gap) != 1) continue
                    val left = linkedMapOf<String, Int>()
                    val right = linkedMapOf<String, Int>()
                    for (item in week[i].items) profile(item.stableKey).primary.forEach {
                        left[it] = (left[it] ?: 0) + item.setCount
                    }
                    for (item in week[j].items) profile(item.stableKey).primary.forEach {
                        right[it] = (right[it] ?: 0) + item.setCount
                    }
                    burden += left.entries.sumOf { (muscle, sets) ->
                        min(sets, right[muscle] ?: 0)
                    } * 0.0025
                }
            }
            val n = weekly.size.toDouble()
            benefit /= n
            burden /= n
            totalSets /= n
            overtime /= n
            val keys = sessions.flatMap(V22Session::items).mapTo(linkedSetOf(), V22ExercisePlan::stableKey)
            val newKeys = keys - initialKeys
            val removedKeys = (initialKeys - keys) - locked
            val itemCount = sessions.sumOf { it.items.size }
            val deviation = abs(totalSets - referenceSets) / referenceSets
            val uncertainty = if (observations.observedTrainingDates28d < 3) 0.015 else 0.0
            val baseCost = burden + 0.0012 * totalSets + 0.006 * itemCount +
                0.012 * newKeys.size + 0.006 * removedKeys.size + uncertainty +
                overtime * 0.08
            val lowLinkedCompletion = observations.plannedCompletionRate?.let { it < 0.65 } == true
            val stressSignal = observations.meanObservedRpe28d?.let {
                if (it >= 8.5 && observations.ratedSets28d >= 6) 1.0 else 0.0
            } ?: 0.0
            val strategies = Strategy.entries
            val values = strategies.map { strategy ->
                val targetRatio = when (strategy) {
                    Strategy.DEVELOP -> 1.08
                    Strategy.MAINTAIN -> 1.0
                    Strategy.RECOVER -> 0.75
                    Strategy.REDISTRIBUTE -> 1.0
                }
                val volumeMismatch = abs(totalSets - referenceSets * targetRatio) / referenceSets
                val prior = when (strategy) {
                    Strategy.DEVELOP -> if (lowLinkedCompletion) -0.020
                        else if ((observations.relativeStrengthTrend ?: 0.0) > 0.015) 0.012 else -0.005
                    Strategy.MAINTAIN -> if (lowLinkedCompletion) 0.015 else 0.006
                    Strategy.RECOVER -> if (stressSignal > 0.0) 0.015 else -0.05
                    Strategy.REDISTRIBUTE -> 0.005
                }
                val strategyCost = when (strategy) {
                    Strategy.DEVELOP -> 0.040 * volumeMismatch
                    Strategy.MAINTAIN -> 0.035 * volumeMismatch
                    Strategy.RECOVER -> 0.060 * volumeMismatch
                    Strategy.REDISTRIBUTE -> 0.035 * deviation
                }
                strategy to (benefit - baseCost - strategyCost + prior)
            }
            val winner = values.maxWithOrNull(compareBy<Pair<Strategy, Double>> { it.second }
                .thenByDescending { it.first.ordinal })!!
            val feasible = overtime < EPS && sessions.all { day ->
                day.items.isNotEmpty() && day.items.all { item ->
                    item.setCount >= minimumSets(item.stableKey)
                }
            }
            return Scored(sessions, winner.second, winner.first,
                values.toMap(), peak, feasible,
                signature(sessions), changes)
        }

        var evaluated = 0
        val visited = hashSetOf<String>()
        val all = mutableListOf<Scored>()
        fun consider(sessions: List<V22Session>, path: List<String>): Scored? {
            if (evaluated >= MAX_EVALUATIONS || !valid(sessions)) return null
            val id = signature(sessions)
            if (!visited.add(id)) return null
            evaluated++
            val c = score(sessions, path)
            all += c
            return c
        }
        // Evaluate EVERY allowed weekday combination even when the starting roster
        // is unchanged. For four available training days this is all 35 subsets.
        val seeds = candidateDays.mapNotNull { days ->
            consider(days.mapIndexed { i, day -> baseline[i].copy(session = day) },
                if (days == baseDays) emptyList() else listOf("WEEKDAYS:" + days.joinToString(",")))
        }
        if (seeds.isEmpty()) return unchanged("NO_VALID_SEED")
        val initial = seeds.find { it.sessions.map(V22Session::session) == baseDays }
            ?: seeds.first()
        var best = seeds.filter(Scored::feasible)
            .maxWithOrNull(compareBy<Scored> { it.score }.thenByDescending { it.signature })
        var beam = seeds.sortedWith(compareByDescending<Scored> { it.score }
            .thenBy { it.signature }).take(BEAM_WIDTH)

        for (round in 0 until ROUNDS) {
            if (evaluated >= MAX_EVALUATIONS) break
            val roundCandidates = mutableListOf<Scored>()
            fun offer(plan: List<V22Session>, from: Scored, label: String): Boolean {
                val candidate = consider(plan, from.changes + label) ?: return false
                roundCandidates += candidate
                if (candidate.feasible && (best == null || candidate.score > best!!.score + EPS ||
                        (abs(candidate.score - best!!.score) <= EPS &&
                            candidate.signature < best!!.signature))) best = candidate
                return true
            }
            for (node in beam) {
                if (evaluated >= MAX_EVALUATIONS) break
                val plan = node.sessions
                val used = plan.flatMap(V22Session::items).mapTo(hashSetOf(), V22ExercisePlan::stableKey)
                val currentMuscles = plan.flatMap(V22Session::items)
                    .flatMap { item -> profile(item.stableKey).primary.map { it to item.setCount } }
                    .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.sum() }
                val rankedGeneral = eligible.asSequence().filter { it.stableKey !in used }
                    .map { e ->
                        e to profile(e.stableKey).primary.sumOf {
                            1.0 / (1.0 + (currentMuscles[it] ?: 0))
                        }.plus(if (profile(e.stableKey).directCore) 0.45 else 0.0)
                            .plus(profile(e.stableKey).sport * 0.15)
                    }.sortedWith(compareByDescending<Pair<Exercise, Double>> { it.second }
                        .thenBy { it.first.stableKey }).take(7).map { it.first }.toList()
                val sportImportance = request.bGoalImportance.sportSupport ?: 3
                val sportLimit = when {
                    sportImportance >= 5 -> 6
                    sportImportance >= 3 -> 3
                    else -> 1
                }
                val coveredObjectives = used.flatMap { key ->
                    badmintonCatalog.relations(key).filter { row ->
                        row.transferLevel == BadmintonObjectiveTransferLevel.DIRECT ||
                            row.transferLevel == BadmintonObjectiveTransferLevel.SUPPORTIVE
                    }.map { it.objective.name }
                }.toSet()
                fun skillTags(e: Exercise): Set<String> =
                    e.badmintonSkillTargets.split(Regex("[,|;]"))
                        .map(String::trim).filter(String::isNotEmpty).toSet()
                val coveredSkillTags = used.flatMap { key ->
                    exercises[key]?.let(::skillTags).orEmpty()
                }.toSet()
                val rankedSport = eligible.asSequence().filter { e ->
                    e.stableKey !in used && badmintonCatalog.relations(e.stableKey).any {
                        it.transferLevel == BadmintonObjectiveTransferLevel.DIRECT ||
                            it.transferLevel == BadmintonObjectiveTransferLevel.SUPPORTIVE
                    }
                }.sortedWith(compareByDescending<Exercise> { e ->
                    badmintonCatalog.relations(e.stableKey).count { row ->
                        row.transferLevel == BadmintonObjectiveTransferLevel.DIRECT &&
                            row.objective.name !in coveredObjectives
                    }
                }.thenByDescending { e -> (skillTags(e) - coveredSkillTags).size }
                    .thenByDescending { e -> profile(e.stableKey).sport }
                    .thenBy(Exercise::stableKey)).take(sportLimit).toList()
                val coveredAnchors = BCoachStrengthCoveragePolicy.anchors(used)
                val rankedStrength = BCoachStrengthCoveragePolicy.preferredKeys(strengthTargetKeys.filter {
                    it !in used && CanonicalStrengthExposureCapability.movementAnchor(it) !in coveredAnchors &&
                        exercises[it]?.let { e -> BCoachStrengthCoveragePolicy.selectable(e, request.availableEquipment) } == true
                }).groupBy { CanonicalStrengthExposureCapability.movementAnchor(it) }.values
                    .mapNotNull { it.firstOrNull()?.let(exercises::get) }
                val additions = (rankedStrength + rankedSport + rankedGeneral).distinctBy(Exercise::stableKey)
                shortlistedSportKeys += additions.filter { badmintonCatalog.relations(it.stableKey).any { row ->
                    row.transferLevel == BadmintonObjectiveTransferLevel.DIRECT ||
                        row.transferLevel == BadmintonObjectiveTransferLevel.SUPPORTIVE
                } }.map(Exercise::stableKey)
                for (i in plan.indices) {
                    val day = plan[i]
                    for (j in day.items.indices) {
                        val item = day.items[j]
                        val key = item.stableKey
                        val adjustable = profile(key).eligible
                        val editable = key !in locked
                        fun modify(items: List<V22ExercisePlan>, action: String) {
                            val next = plan.toMutableList()
                            next[i] = rebuilt(day, items)
                            offer(next, node, action)
                        }
                        if (adjustable && item.setCount < BCoachV2Policy.MAX_PER_EXERCISE_SETS) {
                            val newSet = item.sets.last().copy(
                                weightKg = null, observedRpe = null,
                                sourceDate = "", loadStatus = "B_COACH_EXTRA_SET_USER_REVIEW")
                            modify(day.items.toMutableList().also {
                                it[j] = item.copy(setCount = item.setCount + 1,
                                    sets = item.sets + newSet)
                            }, "ADD_SET:" + day.session + ":" + key)
                        }
                        if (adjustable &&
                            item.setCount > minimumSets(key)) {
                            modify(day.items.toMutableList().also {
                                it[j] = item.copy(setCount = item.setCount - 1,
                                    sets = item.sets.dropLast(1))
                            }, "REMOVE_SET:" + day.session + ":" + key)
                        }
                        if (editable && day.items.size > 1) {
                            modify(day.items.filterIndexed { index, _ -> index != j },
                                "REMOVE_EXERCISE:" + day.session + ":" + key)
                        }
                        // Unreviewed metadata does not authorize moving an item.
                        if (!profile(key).eligible) continue
                        // Move entire prescription; no load/RPE copied between exercises.
                        for (k in plan.indices) {
                            if (k == i || day.items.size <= 1 ||
                                plan[k].items.any { it.stableKey == key }) continue
                            val next = plan.toMutableList()
                            next[i] = rebuilt(day, day.items.filterIndexed { index, _ -> index != j })
                            next[k] = rebuilt(plan[k], plan[k].items + item)
                            offer(next, node,
                                "MOVE:" + key + ":" + day.session + "->" + plan[k].session)
                        }
                    }
                    // Insert 1, 2 or 3 sets: let the full score decide whether a
                    // one-set probe or a coherent multi-set replacement is worthwhile.
                    for (addition in additions) {
                        if (day.items.size >= 12) break
                        val timed = addition.progressMetricType == "TIME_OR_DISTANCE"
                        val strengthMain = addition.stableKey in rankedStrength.map(Exercise::stableKey)
                        val purpose = if (strengthMain) BBlockGoal.STRENGTH else
                            BBlockPurposePrescription.purpose(block.goal, false, addition)
                        val range = BBlockPurposePrescription.repRange(purpose, strengthMain)
                        val item = V22ExercisePlan(
                            exercise = addition.name, stableKey = addition.stableKey,
                            category = addition.category, role = if (strengthMain) "MAIN_STRENGTH_REVIEW"
                                else "B_COACH_ACCESSORY_REVIEW",
                            setCount = 1,
                            sets = listOf(V22ObservedSet(null,
                                if (timed) null else (range.first + range.last) / 2,
                                null, null, "", "B_COACH_NEW_EXERCISE_REVIEW")),
                            historicalSets28d = 0, historicalSets56d = 0,
                            source = "B_COACH_NEW_EXERCISE_USER_REVIEW_REQUIRED",
                            prescriptionAuthority = "B_COACH_USER_CALIBRATION_REQUIRED"
                        )
                        for (count in minimumSets(addition.stableKey)..3) {
                            val next = plan.toMutableList()
                            next[i] = rebuilt(day, day.items + item.copy(setCount = count,
                                sets = List(count) { item.sets.first().copy() }))
                            if (offer(next, node,
                                    "INSERT:" + day.session + ":" + addition.stableKey + ":" + count)) {
                                scoredSportKeys += addition.stableKey
                            }
                        }
                        // Composite replace: allows a two/three-set substitution even
                        // when the intermediate removal would lose score.
                        day.items.indices.filter { day.items[it].stableKey !in locked }
                            .take(5).forEach { oldIndex ->
                                for (count in minimumSets(addition.stableKey)..3) {
                                    val next = plan.toMutableList()
                                    next[i] = rebuilt(day, day.items.toMutableList().also { items ->
                                        items[oldIndex] = item.copy(setCount = count,
                                            sets = List(count) { item.sets.first().copy() })
                                    })
                                    if (offer(next, node,
                                            "REPLACE:" + day.items[oldIndex].stableKey +
                                                "->" + addition.stableKey + ":" + count)) {
                                        scoredSportKeys += addition.stableKey
                                    }
                                }
                            }
                    }
                }
            }
            if (roundCandidates.isEmpty()) break
            beam = roundCandidates.sortedWith(compareByDescending<Scored> { it.score }
                .thenBy { it.signature }).take(BEAM_WIDTH)
        }
        fun topAlternatives(): List<Alternative> {
            val feasible = all.filter(Scored::feasible)
            val bestByStrategy = Strategy.entries.mapNotNull { strategy ->
                val candidate = feasible.maxWithOrNull(
                    compareBy<Scored> { it.strategyScores.getValue(strategy) }
                        .thenByDescending { it.signature }
                ) ?: return@mapNotNull null
                Alternative(strategy, candidate.sessions.map(V22Session::session),
                    candidate.sessions.sumOf(V22Session::nominalSets), candidate.peakMinutes,
                    candidate.strategyScores.getValue(strategy), "FEASIBLE_STRATEGY_COMPARISON")
            }
            val distinctWeekdays = feasible.sortedWith(compareByDescending<Scored> { it.score }
                .thenBy { it.signature }).distinctBy {
                    it.sessions.map(V22Session::session)
                }.take(4).map { candidate ->
                    Alternative(candidate.strategy, candidate.sessions.map(V22Session::session),
                        candidate.sessions.sumOf(V22Session::nominalSets), candidate.peakMinutes,
                        candidate.score, "FEASIBLE_WEEKDAY_COMPARISON")
                }
            return bestByStrategy + distinctWeekdays
        }
        fun candidateDiagnostics(selectedSessions: List<V22Session>): List<String> {
            val selected = selectedSessions.flatMap(V22Session::items)
                .mapTo(hashSetOf(), V22ExercisePlan::stableKey)
            val recruited = badmintonCatalog.allRelations().asSequence().filter {
                it.transferLevel == BadmintonObjectiveTransferLevel.DIRECT ||
                    it.transferLevel == BadmintonObjectiveTransferLevel.SUPPORTIVE
            }.map { it.exerciseStableKey }.distinct().sorted().toList()
            return recruited.map { key ->
                val exercise = exercises[key]
                val active = exercise?.isActive == true
                val selectable = exercise != null && exercise.activityKind != "SPORT_SESSION" &&
                    exercise.planningEligibility !in setOf("HISTORY_ONLY", "FATIGUE_ONLY", "ANALYSIS_ONLY") &&
                    !exercise.needsReview && !exercise.isCustom
                val equipment = when {
                    exercise == null -> "NOT_EVALUATED"
                    permitted(exercise) -> "ELIGIBLE"
                    request.availableEquipment == null -> "EQUIPMENT_UNKNOWN"
                    else -> "EQUIPMENT_UNAVAILABLE(${exercise.equipment})"
                }
                val final = when {
                    exercise == null -> "METADATA_MISSING"
                    !active -> "DB_INACTIVE"
                    !selectable -> "PROGRAM_NOT_SELECTABLE"
                    equipment != "ELIGIBLE" -> equipment
                    key in selected -> "SELECTED"
                    key in scoredSportKeys -> "SCORED_NOT_SELECTED"
                    key in shortlistedSportKeys -> "SHORTLISTED_NOT_SCORED"
                    else -> "ELIGIBLE_NOT_SHORTLISTED"
                }
                "SPORT_CANDIDATE:$key:METADATA=${exercise != null}:DB_ACTIVE=$active:" +
                    "PROGRAM_SELECTABLE=$selectable:EQUIPMENT=$equipment:RECRUITED=true:" +
                    "SHORTLIST=${key in shortlistedSportKeys}:SCORED=${key in scoredSportKeys}:FINAL=$final"
            }
        }
        if (best == null) return Result(baseline, Strategy.MAINTAIN, observations,
            initial.score, initial.score, evaluated, topAlternatives(),
            emptyList(), "NO_FEASIBLE_PROGRAM_KEEP_BASELINE_REVIEW_REQUIRED",
            candidateDiagnostics(baseline))
        val chosen = best!!
        return Result(chosen.sessions, chosen.strategy, observations,
            initial.score, chosen.score, evaluated, topAlternatives(),
            chosen.changes,
            if (chosen.changes.isEmpty()) "BASELINE_PREFERRED_BY_WHOLE_BLOCK_SCORE"
            else "COACH_WHOLE_BLOCK_JOINT_OPTIMIZATION_USER_REVIEW",
            candidateDiagnostics(chosen.sessions))
    }
}

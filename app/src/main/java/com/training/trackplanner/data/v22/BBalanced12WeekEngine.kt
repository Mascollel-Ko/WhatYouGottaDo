package com.training.trackplanner.data.v22

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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Live B-v1 planner. The Python-parity engine remains frozen and is used only as the incumbent
 * roster/comparator. Every numeric change stays inside an observed-history weekly ceiling.
 */
internal object BBalanced12WeekEngine {
    const val SCHEMA = "B_BALANCED_12_WEEK_V1"
    const val TEMPLATE_ID = "V2_2_B_BALANCED_12_WEEK_V1"
    const val MINIMUM_IMPROVEMENT = 0.005
    private const val FOCUS_ALLOCATION_WEIGHT = 0.1

    private data class Contribution(
        val strength: Double,
        val hypertrophyMuscles: Set<String>,
        val core: Double,
        val sport: Double,
        val reviewedCoreTasks: Set<String>,
        val reviewRequiredTasks: Set<String>
    ) {
        fun value(goal: BBalancedGoal): Double = when (goal) {
            BBalancedGoal.STRENGTH -> strength
            BBalancedGoal.HYPERTROPHY -> hypertrophyMuscles.size.toDouble()
            BBalancedGoal.CORE -> core
            BBalancedGoal.SPORT_SUPPORT -> sport
        }
    }

    private data class Candidate(
        val focuses: List<BBalancedGoal>,
        val sessions: List<List<V22Session>>,
        val evaluations: List<List<BBalancedGoalEvaluation>>,
        val score: Double,
        val moves: Int
    )

    fun build(
        rows: List<V22HistoryRow>,
        request: V22GenerationRequest,
        exercises: Map<String, Exercise>,
        muscleRelations: List<CanonicalMetadataRelation>,
        coreCatalog: CanonicalCoreCatalog,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        taskCatalog: BBalancedTaskCatalog
    ): BBalanced12WeekPlan {
        require(request.mode == V22Mode.B) { "B 12-week engine only accepts mode B" }
        require(request.weeks == 12) { "B balanced programs are fixed at 12 weeks" }
        require(request.days in 1..7) { "days" }
        require(request.sessionMinutes == null || request.sessionMinutes > 0) { "minutes" }

        val legacy = runCatching { V22PythonParityEngine.build(rows, request.copy(mode = V22Mode.B)) }.getOrNull()
            ?.takeIf { plan -> plan.weeks.firstOrNull()?.sessions?.any { it.items.isNotEmpty() } == true }
        val contributionCatalog = contributionMap(
            exercises.keys,
            muscleRelations,
            coreCatalog,
            badmintonCatalog,
            taskCatalog
        )
        val incumbent = legacy?.weeks?.firstOrNull()?.sessions
            ?: coldStartBaseline(request, exercises, contributionCatalog)
        val contributions = contributionCatalog.filterKeys {
            key -> incumbent.any { session -> session.items.any { it.stableKey == key } }
        }
        // Do not destroy sets to satisfy the old under-counting timer. Preserve the roster,
        // evaluate three versus four days only when explicitly authorized, and surface
        // unresolved time/recovery limitations rather than reporting false feasibility.
        val placement = BBalancedDayPlacementPlanner.optimize(
            incumbent, request.days, request.bMaxTrainingDays ?: request.days,
            request.sessionMinutes, exercises
        )
        val trimmed = placement.sessions
        val targets = targets(rows, request.asOf, request.bGoalImportance, contributions)
        val historyCeilings = historyCeilings(rows, request.asOf)

        var evaluated = 0
        var feasible = 0
        var accepted = 0
        var best: Candidate? = null
        val goals = BBalancedGoal.entries
        for (first in goals) for (second in goals) for (third in goals) {
            evaluated++
            val focuses = listOf(first, second, third)
            val blocks = focuses.map { focus ->
                rebalance(trimmed, focus, contributions, historyCeilings)
            }
            if (blocks.any { !structurallyFeasible(it) }) continue
            feasible++
            val evaluations = blocks.map { evaluate(it, targets, contributions) }
            val moves = blocks.indices.sumOf { index -> changedPhysicalSets(trimmed, blocks[index]) }
            val score = score(evaluations, targets, focuses) - moves * 0.002
            val candidate = Candidate(focuses, blocks, evaluations, score, moves)
            val current = best
            if (current == null || score > current.score + MINIMUM_IMPROVEMENT ||
                (kotlin.math.abs(score - current.score) <= MINIMUM_IMPROVEMENT && candidateKey(candidate) < candidateKey(current))) {
                if (current != null && score > current.score + MINIMUM_IMPROVEMENT) accepted++
                best = candidate
            }
        }
        val chosen = requireNotNull(best) { "No feasible B balanced candidate within the supplied time constraint" }
        val blocks = chosen.focuses.mapIndexed { index, focus ->
            BBalancedBlock(
                block = index + 1,
                startWeek = index * 4 + 1,
                endWeek = index * 4 + 4,
                focus = focus,
                focusReasons = focusReasons(focus, targets, chosen.evaluations[index]),
                sessions = chosen.sessions[index],
                evaluation = chosen.evaluations[index]
            )
        }
        val weeks = blocks.flatMap { block ->
            (block.startWeek..block.endWeek).map { week ->
                val sessions = block.sessions.map { session ->
                    session.copy(items = session.items.map { item ->
                        item.copy(
                            sets = item.sets.map(V22ObservedSet::copy),
                            prescriptionAuthority = "PERSONAL_HISTORY_CONTINUITY_REVIEW_REQUIRED",
                            plannedProgression = "CONDITIONAL_NEXT_COMPARABLE_SESSION_ONLY",
                            block = block.block
                        )
                    })
                }
                V22Week(week, block.block, sessions, sessions.sumOf(V22Session::nominalSets))
            }
        }
        val search = BBalancedSearchStats(
            evaluatedCandidates = evaluated,
            feasibleCandidates = feasible,
            acceptedImprovements = accepted,
            minimumImprovement = MINIMUM_IMPROVEMENT,
            terminationReason = "BOUNDED_EXHAUSTIVE_4x4x4_FOCUS_SEQUENCE"
        )
        val input = V22PlanInput(
            backup = request.backupName,
            asOf = request.asOf.toString(),
            weeks = 12,
            days = placement.effectiveDays,
            mode = V22Mode.B.name,
            focusStableKey = null,
            sessionMinutes = request.sessionMinutes
        )
        val limitations = listOf(
            "Core and sport-support targets remain direction-only because reviewed numeric dose authority is unavailable.",
            "Hypertrophy personal-response confidence is zero without direct muscle-size observations.",
            "Historical kilograms are references only; the future skeleton requires user calibration.",
            "Candidate search reuses incumbent exercises and never exceeds each exercise's observed weekly ceiling.",
            "Goal satisfaction is a planning coverage ratio, not a predicted adaptation probability.",
            placement.diagnosis
        )
        val deletionReview = auditDeletionCandidates(trimmed, targets, contributions, exercises)
        val metadata = JSONObject(metadataJson(input, targets, blocks, search, contributions, limitations))
            .put("placementAudit", JSONObject()
                .put("requestedDays", placement.requestedDays)
                .put("effectiveDays", placement.effectiveDays)
                .put("expanded", placement.expanded)
                .put("candidateEvaluations", placement.candidateEvaluations)
                .put("estimatedPeakMinutes", placement.maxEstimatedMinutes ?: JSONObject.NULL)
                .put("requestedDaysEstimatedPeakMinutes", placement.requestedMaxEstimatedMinutes ?: JSONObject.NULL)
                .put("status", placement.diagnosis)
                .put("authority", "HEURISTIC_PLACEMENT_REVIEW_ONLY_NOT_OFI_OR_TISSUE_PROJECTION"))
            .put("deletionAudit", deletionReview)
            .put("deletionPolicy", "AUDIT_ONLY_NO_AUTOMATIC_REMOVAL")
            .toString()
        return BBalanced12WeekPlan(
            schema = SCHEMA,
            input = input,
            goalTargets = targets,
            blocks = blocks,
            weeks = weeks,
            search = search,
            legacyComparatorSchema = legacy?.schema ?: "UNAVAILABLE_NO_ELIGIBLE_PERSONAL_HISTORY",
            limitations = limitations,
            metadataJson = metadata
        ).also(::validate)
    }

    private fun coldStartBaseline(
        request: V22GenerationRequest,
        exercises: Map<String, Exercise>,
        contributions: Map<String, Contribution>
    ): List<V22Session> {
        val eligible = exercises.values.filter { it.isActive && it.planningEligibility != "HISTORY_ONLY" }
            .sortedBy(Exercise::stableKey)
        val chosen = linkedSetOf<String>()
        CanonicalStrengthExposureCapability.MovementAnchor.entries.forEach { anchor ->
            eligible.firstOrNull { it.stableKey in CanonicalStrengthExposureCapability.approvedStableKeys(anchor) }
                ?.let { chosen += it.stableKey }
        }
        eligible.firstOrNull { contributions[it.stableKey]?.core == 1.0 }?.let { chosen += it.stableKey }
        eligible.firstOrNull { (contributions[it.stableKey]?.sport ?: 0.0) >= 0.6 }?.let { chosen += it.stableKey }
        if (chosen.isEmpty()) eligible.firstOrNull()?.let { chosen += it.stableKey }
        eligible.asSequence().filter { it.stableKey !in chosen }.take((request.days - chosen.size).coerceAtLeast(0))
            .forEach { chosen += it.stableKey }
        require(chosen.isNotEmpty()) { "No eligible exercise metadata for B cold-start generation" }
        val items = chosen.map { key ->
            val exercise = exercises.getValue(key)
            val setCount = if (contributions[key]?.strength == 1.0) 2 else 1
            V22ExercisePlan(
                exercise = exercise.name,
                stableKey = key,
                category = exercise.category,
                role = "CANONICAL_METADATA_COLD_START",
                setCount = setCount,
                sets = List(setCount) { index ->
                    V22ObservedSet(null, null, null, null, request.asOf.toString(), "NO_HISTORY_USER_CALIBRATION_REQUIRED_${index + 1}")
                },
                historicalSets28d = 0,
                historicalSets56d = 0,
                source = "COLD_START_CANONICAL_METADATA_REVIEW_REQUIRED"
            )
        }
        val buckets = List(request.days) { mutableListOf<V22ExercisePlan>() }
        items.forEachIndexed { index, item -> buckets[index % request.days] += item }
        return buckets.mapIndexed { index, bucket ->
            val populated = if (bucket.isNotEmpty()) bucket else mutableListOf(items[index % items.size].copy())
            V22Session(index + 1, populated, populated.sumOf(V22ExercisePlan::setCount))
        }
    }

    fun validate(plan: BBalanced12WeekPlan): BBalanced12WeekPlan {
        check(plan.input.mode == V22Mode.B.name && plan.input.weeks == 12)
        check(plan.blocks.size == 3)
        check(plan.blocks.map { it.startWeek to it.endWeek } == listOf(1 to 4, 5 to 8, 9 to 12))
        check(plan.weeks.map(V22Week::week) == (1..12).toList())
        plan.blocks.forEach { block ->
            check(block.sessions.size == plan.input.days)
            block.sessions.forEach { session ->
                check(session.nominalSets == session.items.sumOf(V22ExercisePlan::setCount))
                check(session.items.all { it.setCount == it.sets.size && it.setCount > 0 })
            }
        }
        return plan
    }

    private fun contributionMap(
        keys: Set<String>,
        muscleRelations: List<CanonicalMetadataRelation>,
        coreCatalog: CanonicalCoreCatalog,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        taskCatalog: BBalancedTaskCatalog
    ): Map<String, Contribution> {
        val primaryMuscles = muscleRelations.asSequence()
            .filter { it.domain == CanonicalRelationDomain.MUSCLE && it.relationType == "PRIMARY" }
            .groupBy(CanonicalMetadataRelation::exerciseStableKey)
            .mapValues { (_, rows) -> rows.mapTo(linkedSetOf(), CanonicalMetadataRelation::relationValue) }
        return keys.associateWith { key ->
            val sport = badmintonCatalog.relations(key).maxOfOrNull { relation ->
                when (relation.transferLevel) {
                    BadmintonObjectiveTransferLevel.DIRECT -> 1.0
                    BadmintonObjectiveTransferLevel.SUPPORTIVE -> 0.6
                    BadmintonObjectiveTransferLevel.GENERAL -> 0.25
                    BadmintonObjectiveTransferLevel.LOW -> 0.1
                    BadmintonObjectiveTransferLevel.NONE -> 0.0
                }
            } ?: 0.0
            Contribution(
                strength = if (CanonicalStrengthExposureCapability.strengthPossible(key)) 1.0 else 0.0,
                hypertrophyMuscles = primaryMuscles[key].orEmpty(),
                core = if (coreCatalog.resolve(key)?.coreClass == CoreClass.DIRECT) 1.0 else 0.0,
                sport = sport,
                reviewedCoreTasks = taskCatalog.forStableKey(key).filter {
                    it.domain == BBalancedTaskDomain.CORE && it.review == BBalancedTaskReview.REVIEWED
                }.mapTo(linkedSetOf()) { it.task },
                reviewRequiredTasks = taskCatalog.forStableKey(key).filter {
                    it.review == BBalancedTaskReview.REVIEW_REQUIRED
                }.mapTo(linkedSetOf()) { "${it.domain}:${it.task}" }
            )
        }
    }

    private fun targets(
        rows: List<V22HistoryRow>,
        asOf: LocalDate,
        requested: BBalancedGoalImportance,
        contributions: Map<String, Contribution>
    ): List<BBalancedGoalTarget> {
        val recent = rows.filter { it.date in asOf.minusDays(27)..asOf && it.stableKey in contributions }
        val doses = dose(recent.groupingBy(V22HistoryRow::stableKey).eachCount(), contributions)
        val performance = strengthResponse(rows, asOf, contributions)
        val recordQuality = if (recent.isEmpty()) 0.0 else recent.count {
            (it.reps != null || it.seconds != null) && it.rpe != null
        }.toDouble() / recent.size
        val cH = rounded(min(1.0, recent.size / 48.0) * (0.5 + 0.5 * recordQuality))
        val ranked = doses.entries.sortedWith(compareByDescending<Map.Entry<BBalancedGoal, Double>> { it.value }.thenBy { it.key.ordinal })
            .map(Map.Entry<BBalancedGoal, Double>::key)
        fun recommended(goal: BBalancedGoal): Int = (3 + (if (doses.getValue(goal) > 0.0) 1 else 0) +
            (if (ranked.firstOrNull() == goal) 1 else 0)).coerceIn(1, 5)
        fun importance(goal: BBalancedGoal) = requested.value(goal) ?: recommended(goal)
        fun source(goal: BBalancedGoal) = if (requested.value(goal) == null) "RECORD_BASED_RECOMMENDATION_V1" else "USER_OVERRIDE"

        return BBalancedGoal.entries.map { goal ->
            val research = when (goal) {
                BBalancedGoal.STRENGTH -> 2.0
                BBalancedGoal.HYPERTROPHY -> 10.0
                BBalancedGoal.CORE, BBalancedGoal.SPORT_SUPPORT -> null
            }
            val researchSource = when (goal) {
                BBalancedGoal.STRENGTH -> "ACSM_2026_AT_LEAST_TWO_SETS_PER_EXERCISE"
                BBalancedGoal.HYPERTROPHY -> "SCHOENFELD_OGBORN_KRIEGER_2017_10_PLUS_WEEKLY_SETS_CATEGORY"
                else -> null
            }
            val recentDose = doses.getValue(goal).takeIf { recent.isNotEmpty() }
            val responseCandidate = performance.candidateDose.takeIf { goal == BBalancedGoal.STRENGTH }
            val cP = if (goal == BBalancedGoal.STRENGTH) performance.confidence else 0.0
            val target = research?.let { r ->
                val h = recentDose ?: r
                val p = responseCandidate ?: h
                rounded((r + cH * h + cP * p) / (1.0 + cH + cP))
            }
            val maintenance = if (goal == BBalancedGoal.STRENGTH) performance.maintenanceDose else null
            BBalancedGoalTarget(
                goal = goal,
                unit = when (goal) {
                    BBalancedGoal.STRENGTH -> "DIRECT_STRENGTH_SETS_PER_ACTIVE_ANCHOR_PER_WEEK"
                    BBalancedGoal.HYPERTROPHY -> "PRIMARY_MUSCLE_DIRECT_SET_PROJECTIONS_PER_REPRESENTED_MUSCLE_PER_WEEK"
                    BBalancedGoal.CORE -> "DIRECT_CORE_PHYSICAL_SETS_PER_WEEK"
                    BBalancedGoal.SPORT_SUPPORT -> "CANONICAL_BADMINTON_TRANSFER_WEIGHTED_PHYSICAL_SETS_PER_WEEK"
                },
                importance = importance(goal),
                importanceSource = source(goal),
                researchReference = research,
                researchReferenceSource = researchSource,
                recentHistory = recentDose,
                personalResponseCandidate = responseCandidate,
                confidence = BBalancedConfidence(
                    history = cH,
                    personalResponse = cP,
                    reasons = buildList {
                        add("CONFIRMED_RECENT_SETS=${recent.size}")
                        add("RECORD_COMPLETENESS=${rounded(recordQuality)}")
                        if (goal == BBalancedGoal.HYPERTROPHY) add("STRENGTH_CHANGE_IS_NOT_DIRECT_MUSCLE_GROWTH_EVIDENCE")
                        if (goal == BBalancedGoal.STRENGTH) addAll(performance.reasons)
                    }
                ),
                developmentTarget = target,
                maintenanceFloor = maintenance,
                maintenanceSource = maintenance?.let { "LOWEST_OBSERVED_NON_DECLINING_STRENGTH_WEEK" },
                numericAuthority = when {
                    target != null -> BBalancedNumericAuthority.RESEARCH_REFERENCE_AND_PERSONAL_HISTORY
                    recentDose != null -> BBalancedNumericAuthority.DIRECTION_ONLY
                    else -> BBalancedNumericAuthority.UNRESOLVED
                },
                reasons = buildList {
                    if (target == null) add("NO_REVIEWED_NUMERIC_DOSE_AUTHORITY")
                    if (maintenance == null) add("PERSONAL_MAINTENANCE_FLOOR_NOT_OBSERVED")
                    if (recent.isEmpty()) add("NO_PERSONAL_HISTORY_GENERATION_CONTINUES_WITH_LOW_CONFIDENCE")
                }
            )
        }
    }

    private data class StrengthResponse(
        val candidateDose: Double?,
        val maintenanceDose: Double?,
        val confidence: Double,
        val reasons: List<String>
    )

    private fun strengthResponse(
        rows: List<V22HistoryRow>,
        asOf: LocalDate,
        contributions: Map<String, Contribution>
    ): StrengthResponse {
        val selected = rows.filter {
            it.date in asOf.minusDays(55)..asOf && contributions[it.stableKey]?.strength == 1.0 &&
                it.weightKg != null && it.weightKg > 0.0 && it.reps != null && it.reps in 1.0..12.0
        }
        val trends = selected.groupBy(V22HistoryRow::stableKey).mapNotNull { (key, values) ->
            val sessions = values.groupBy(V22HistoryRow::date).toSortedMap().mapValues { (_, day) ->
                day.maxOf { requireNotNull(it.weightKg) * (1.0 + requireNotNull(it.reps) / 30.0) }
            }
            if (sessions.size < 2) null else Triple(key, sessions.values.first(), sessions.values.last())
        }
        val improved = trends.count { it.third >= it.second * 1.01 }
        val declined = trends.count { it.third < it.second * 0.99 }
        val consistent = trends.isNotEmpty() && (improved == 0 || declined == 0)
        val confidence = rounded(min(0.75, trends.size / 4.0) * if (consistent) 1.0 else 0.35)
        val improvingKeys = trends.filter { it.third >= it.second }.mapTo(mutableSetOf()) { it.first }
        val weekly = selected.filter { it.stableKey in improvingKeys }.groupBy {
            ChronoUnit.DAYS.between(it.date, asOf).toInt() / 7
        }.values.map { weekRows ->
            val anchors = weekRows.mapNotNull { CanonicalStrengthExposureCapability.movementAnchor(it.stableKey) }.distinct().size
            if (anchors == 0) 0.0 else weekRows.size.toDouble() / anchors
        }.filter { it > 0.0 }
        return StrengthResponse(
            candidateDose = weekly.average().takeIf { weekly.isNotEmpty() }?.let(::rounded),
            maintenanceDose = weekly.minOrNull()?.let(::rounded),
            confidence = confidence,
            reasons = listOf(
                "COMPARABLE_STRENGTH_KEYS=${trends.size}",
                "IMPROVING_KEYS=$improved",
                "DECLINING_KEYS=$declined",
                if (consistent) "RESPONSE_DIRECTION_CONSISTENT" else "CONFLICTING_RESPONSE_REDUCED_CONFIDENCE"
            )
        )
    }

    private fun historyCeilings(rows: List<V22HistoryRow>, asOf: LocalDate): Map<String, Int> =
        rows.filter { it.date in asOf.minusDays(55)..asOf }.groupBy(V22HistoryRow::stableKey).mapValues { (_, values) ->
            max(1, max(ceil(values.count { it.date >= asOf.minusDays(27) } / 4.0), ceil(values.size / 8.0)).toInt())
        }

    private fun rebalance(
        baseline: List<V22Session>,
        focus: BBalancedGoal,
        contributions: Map<String, Contribution>,
        ceilings: Map<String, Int>
    ): List<V22Session> {
        data class Located(val sessionIndex: Int, val itemIndex: Int, val item: V22ExercisePlan)
        val located = baseline.flatMapIndexed { sessionIndex, session ->
            session.items.mapIndexed { itemIndex, item -> Located(sessionIndex, itemIndex, item) }
        }
        val receiver = located.filter { locatedItem ->
            contributions[locatedItem.item.stableKey]?.value(focus) ?: 0.0 > 0.0 &&
                locatedItem.item.setCount < ceilings.getOrDefault(locatedItem.item.stableKey, locatedItem.item.setCount)
        }.sortedWith(compareByDescending<Located> { contributions.getValue(it.item.stableKey).value(focus) }
            .thenBy { it.item.setCount }.thenBy { it.item.stableKey }.thenBy { it.sessionIndex }).firstOrNull()
            ?: return baseline.map { it.copy(items = it.items.map(V22ExercisePlan::copy)) }
        val donor = located.filter { it.item.stableKey != receiver.item.stableKey && it.item.setCount >= 2 }
            .sortedWith(compareBy<Located> { contributions.getValue(it.item.stableKey).value(focus) }
                .thenByDescending { it.item.setCount }.thenBy { it.item.stableKey }.thenBy { it.sessionIndex }).firstOrNull()
            ?: return baseline.map { it.copy(items = it.items.map(V22ExercisePlan::copy)) }
        return baseline.mapIndexed { sessionIndex, session ->
            val items = session.items.mapIndexed { itemIndex, item ->
                when {
                    sessionIndex == donor.sessionIndex && itemIndex == donor.itemIndex ->
                        item.copy(setCount = item.setCount - 1, sets = item.sets.dropLast(1))
                    sessionIndex == receiver.sessionIndex && itemIndex == receiver.itemIndex ->
                        item.copy(setCount = item.setCount + 1, sets = item.sets + item.sets.last().copy())
                    else -> item.copy(sets = item.sets.map(V22ObservedSet::copy))
                }
            }
            session.copy(items = items, nominalSets = items.sumOf(V22ExercisePlan::setCount))
        }
    }

    private fun evaluate(
        sessions: List<V22Session>,
        targets: List<BBalancedGoalTarget>,
        contributions: Map<String, Contribution>
    ): List<BBalancedGoalEvaluation> {
        val counts = sessions.flatMap(V22Session::items).groupBy(V22ExercisePlan::stableKey)
            .mapValues { (_, items) -> items.sumOf(V22ExercisePlan::setCount) }
        val doses = dose(counts, contributions)
        return targets.map { target ->
            val planned = rounded(doses.getValue(target.goal))
            val developmentShortfall = target.developmentTarget?.let { max(0.0, it - planned) }?.let(::rounded)
            val maintenanceShortfall = target.maintenanceFloor?.let { max(0.0, it - planned) }?.let(::rounded)
            BBalancedGoalEvaluation(
                goal = target.goal,
                plannedDose = planned,
                developmentTarget = target.developmentTarget,
                maintenanceFloor = target.maintenanceFloor,
                developmentShortfall = developmentShortfall,
                maintenanceShortfall = maintenanceShortfall,
                status = when {
                    maintenanceShortfall != null && maintenanceShortfall > 0.0 -> "BELOW_MAINTENANCE_REVIEW"
                    developmentShortfall != null && developmentShortfall > 0.0 -> "BELOW_DEVELOPMENT_TARGET"
                    target.developmentTarget != null -> "TARGET_MET"
                    planned > 0.0 -> "DIRECTIONAL_COVERAGE_PRESENT"
                    else -> "REVIEW_REQUIRED_NO_NUMERIC_AUTHORITY"
                }
            )
        }
    }

    private fun dose(counts: Map<String, Int>, contributions: Map<String, Contribution>): Map<BBalancedGoal, Double> {
        val activeAnchors = counts.keys.mapNotNull(CanonicalStrengthExposureCapability::movementAnchor).distinct().size
        val muscles = counts.keys.flatMap { contributions[it]?.hypertrophyMuscles.orEmpty() }.distinct()
        val strengthSets = counts.entries.sumOf { (key, sets) -> sets * (contributions[key]?.strength ?: 0.0) }
        val muscleCredits = counts.entries.sumOf { (key, sets) -> sets * (contributions[key]?.hypertrophyMuscles?.size ?: 0) }
        return mapOf(
            BBalancedGoal.STRENGTH to if (activeAnchors == 0) 0.0 else strengthSets / activeAnchors,
            BBalancedGoal.HYPERTROPHY to if (muscles.isEmpty()) 0.0 else muscleCredits.toDouble() / muscles.size,
            BBalancedGoal.CORE to counts.entries.sumOf { (key, sets) -> sets * (contributions[key]?.core ?: 0.0) },
            BBalancedGoal.SPORT_SUPPORT to counts.entries.sumOf { (key, sets) -> sets * (contributions[key]?.sport ?: 0.0) }
        )
    }

    private fun score(
        evaluations: List<List<BBalancedGoalEvaluation>>,
        targets: List<BBalancedGoalTarget>,
        focuses: List<BBalancedGoal>
    ): Double {
        val importance = targets.associate { it.goal to it.importance.toDouble() }
        // Score the whole horizon together. Diminishing coverage utility lets similarly important
        // goals take different blocks, while a genuinely dominant goal can still repeat.
        val horizonScore = targets.sumOf { goalTarget ->
            val perBlock = evaluations.map { block ->
                val item = block.first { it.goal == goalTarget.goal }
                val target = item.developmentTarget
                val satisfaction = if (target != null && target > 0.0) min(1.0, item.plannedDose / target)
                    // Direction-only goals use a bounded coverage utility, never a physiological target claim.
                    else item.plannedDose / (item.plannedDose + 1.0)
                val maintenancePenalty = item.maintenanceFloor?.takeIf { it > 0.0 }?.let { floor ->
                    max(0.0, floor - item.plannedDose) / floor
                } ?: 0.0
                satisfaction to maintenancePenalty
            }
            val horizonCoverage = perBlock.map(Pair<Double, Double>::first).average()
            val horizonMaintenancePenalty = perBlock.map(Pair<Double, Double>::second).average()
            importance.getValue(goalTarget.goal) * (sqrt(horizonCoverage) - horizonMaintenancePenalty * 1.5)
        } / importance.values.sum()
        val directPriorityScore = evaluations.map { block ->
            block.sumOf { item ->
                val target = item.developmentTarget
                val satisfaction = if (target != null && target > 0.0) min(1.0, item.plannedDose / target)
                else item.plannedDose / (item.plannedDose + 1.0)
                val maintenancePenalty = item.maintenanceFloor?.takeIf { it > 0.0 }?.let { floor ->
                    max(0.0, floor - item.plannedDose) / floor
                } ?: 0.0
                importance.getValue(item.goal) * (satisfaction - maintenancePenalty * 1.5)
            } / importance.values.sum()
        }.average()
        val harmonicFocusAllocation = focuses.groupingBy { it }.eachCount().entries.sumOf { (goal, count) ->
            importance.getValue(goal) * (1..count).sumOf { repetition -> 1.0 / repetition }
        } / (focuses.size * importance.values.sum())
        return directPriorityScore * 0.8 + horizonScore * 0.2 +
            harmonicFocusAllocation * FOCUS_ALLOCATION_WEIGHT
    }

    private fun structurallyFeasible(sessions: List<V22Session>): Boolean =
        sessions.isNotEmpty() && sessions.all { session ->
            session.items.isNotEmpty() &&
                session.items.all { it.setCount > 0 && it.setCount == it.sets.size }
        }

    private fun changedPhysicalSets(base: List<V22Session>, candidate: List<V22Session>): Int {
        fun counts(sessions: List<V22Session>) = sessions.flatMap(V22Session::items)
            .groupBy(V22ExercisePlan::stableKey)
            .mapValues { (_, items) -> items.sumOf(V22ExercisePlan::setCount) }
        val a = counts(base)
        val b = counts(candidate)
        return (a.keys + b.keys).sumOf { kotlin.math.abs(a.getOrDefault(it, 0) - b.getOrDefault(it, 0)) } / 2
    }


    /**
     * Counterfactual exercise-removal review only. Deliberately does NOT mutate the plan.
     * Metadata gaps and protected main lifts never become low-score auto-removal choices.
     */
    private fun auditDeletionCandidates(
        sessions: List<V22Session>,
        targets: List<BBalancedGoalTarget>,
        contributions: Map<String, Contribution>,
        exercises: Map<String, Exercise>
    ): JSONArray {
        val counts = sessions.flatMap(V22Session::items).groupBy(V22ExercisePlan::stableKey)
            .mapValues { (_, items) -> items.sumOf(V22ExercisePlan::setCount) }
        val baselineDose = dose(counts, contributions)
        val rows = counts.keys.map { key ->
            val metadata = exercises[key]
            val relation = contributions[key]
            val remaining = counts - key
            val reducedDose = dose(remaining, contributions)
            val primaryElsewhere = remaining.keys.flatMap { contributions[it]?.hypertrophyMuscles.orEmpty() }.toSet()
            val uniquePrimary = relation?.hypertrophyMuscles.orEmpty() - primaryElsewhere
            val tasksElsewhere = remaining.keys.flatMap { contributions[it]?.reviewedCoreTasks.orEmpty() }.toSet()
            val uniqueCoreTasks = relation?.reviewedCoreTasks.orEmpty() - tasksElsewhere
            val protected = CanonicalStrengthExposureCapability.strengthPossible(key) ||
                key in setOf("barbell_back_squat", "barbell_deadlift", "barbell_romanian_deadlift")
            val unresolved = metadata == null || metadata.isCustom || metadata.needsReview ||
                relation == null || relation.reviewRequiredTasks.isNotEmpty()
            val weightedLoss = targets.sumOf { target ->
                val before = baselineDose.getValue(target.goal)
                val after = reducedDose.getValue(target.goal)
                val scale = target.developmentTarget?.takeIf { it > 0.0 }
                    ?: before.coerceAtLeast(1.0)
                target.importance * ((before - after).coerceAtLeast(0.0) / scale)
            }
            // Large, explicit coverage-preservation costs. These numbers rank review
            // hypotheses, not experimentally measured training adaptations.
            val reviewCost = rounded(weightedLoss + uniquePrimary.size * 5.0 +
                uniqueCoreTasks.size * 5.0)
            val status = when {
                protected -> "PROTECTED_MAIN_STRENGTH_CAPABILITY"
                unresolved -> "UNRESOLVED_CANONICAL_METADATA"
                else -> "REVIEW_ELIGIBLE_NOT_AUTOMATICALLY_REMOVABLE"
            }
            JSONObject().put("stableKey", key)
                .put("exercise", metadata?.name ?: key)
                .put("weeklySets", counts.getValue(key))
                .put("importanceWeightedLoss", reviewCost)
                .put("status", status)
                .put("uniquePrimaryMusclesAtRisk", JSONArray(uniquePrimary.sorted()))
                .put("uniqueCoreTasksAtRisk", JSONArray(uniqueCoreTasks.sorted()))
                .put("notRemoved", true)
        }.sortedWith(compareBy<JSONObject> { it.optString("status") != "REVIEW_ELIGIBLE_NOT_AUTOMATICALLY_REMOVABLE" }
            .thenBy { it.optDouble("importanceWeightedLoss") }.thenBy { it.optString("stableKey") })
        return JSONArray(rows)
    }

    private fun focusReasons(
        focus: BBalancedGoal,
        targets: List<BBalancedGoalTarget>,
        evaluation: List<BBalancedGoalEvaluation>
    ): List<String> {
        val target = targets.single { it.goal == focus }
        val result = evaluation.single { it.goal == focus }
        return listOf(
            "IMPORTANCE=${target.importance}:${target.importanceSource}",
            "PLANNED_DOSE=${result.plannedDose}:${target.unit}",
            "DEVELOPMENT_SHORTFALL=${result.developmentShortfall ?: "UNRESOLVED"}",
            "MAINTENANCE_SHORTFALL=${result.maintenanceShortfall ?: "UNRESOLVED"}",
            "FOCUS_MAY_REPEAT_ACROSS_BLOCKS"
        )
    }

    private fun candidateKey(candidate: Candidate): String = candidate.focuses.joinToString("|") { it.name }

    private fun metadataJson(
        input: V22PlanInput,
        targets: List<BBalancedGoalTarget>,
        blocks: List<BBalancedBlock>,
        search: BBalancedSearchStats,
        contributions: Map<String, Contribution>,
        limitations: List<String>
    ): String = JSONObject()
        .put("schema", SCHEMA)
        .put("input", JSONObject().put("asOf", input.asOf).put("weeks", input.weeks).put("days", input.days)
            .put("sessionMinutes", input.sessionMinutes ?: JSONObject.NULL))
        .put("goalTargets", JSONArray(targets.map { target ->
            JSONObject().put("goal", target.goal.name).put("unit", target.unit).put("importance", target.importance)
                .put("importanceSource", target.importanceSource)
                .put("developmentTarget", target.developmentTarget ?: JSONObject.NULL)
                .put("maintenanceFloor", target.maintenanceFloor ?: JSONObject.NULL)
                .put("numericAuthority", target.numericAuthority.name)
                .put("historyConfidence", target.confidence.history)
                .put("personalResponseConfidence", target.confidence.personalResponse)
        }))
        .put("blocks", JSONArray(blocks.map { block ->
            JSONObject().put("block", block.block).put("startWeek", block.startWeek).put("endWeek", block.endWeek)
                .put("focus", block.focus.name).put("focusReasons", JSONArray(block.focusReasons))
                .put("evaluation", JSONArray(block.evaluation.map(::evaluationJson)))
        }))
        .put("search", JSONObject().put("evaluatedCandidates", search.evaluatedCandidates)
            .put("feasibleCandidates", search.feasibleCandidates).put("acceptedImprovements", search.acceptedImprovements)
            .put("minimumImprovement", search.minimumImprovement).put("terminationReason", search.terminationReason))
        .put("contributions", JSONObject().also { output -> contributions.toSortedMap().forEach { (key, value) ->
            output.put(key, JSONObject().put("STRENGTH", value.strength)
                .put("HYPERTROPHY", value.hypertrophyMuscles.size).put("CORE", value.core).put("SPORT_SUPPORT", value.sport)
                .put("reviewedCoreTasks", JSONArray(value.reviewedCoreTasks.sorted()))
                .put("reviewRequiredTasks", JSONArray(value.reviewRequiredTasks.sorted())))
        }})
        .put("doseDivisors", JSONObject()
            .put("STRENGTH", contributions.keys.mapNotNull(CanonicalStrengthExposureCapability::movementAnchor).distinct().size.coerceAtLeast(1))
            .put("HYPERTROPHY", contributions.values.flatMap(Contribution::hypertrophyMuscles).distinct().size.coerceAtLeast(1))
            .put("CORE", 1)
            .put("SPORT_SUPPORT", 1))
        .put("limitations", JSONArray(limitations))
        .put("userEdited", false)
        .toString()

    private fun evaluationJson(value: BBalancedGoalEvaluation): JSONObject = JSONObject()
        .put("goal", value.goal.name).put("plannedDose", value.plannedDose)
        .put("developmentTarget", value.developmentTarget ?: JSONObject.NULL)
        .put("maintenanceFloor", value.maintenanceFloor ?: JSONObject.NULL)
        .put("developmentShortfall", value.developmentShortfall ?: JSONObject.NULL)
        .put("maintenanceShortfall", value.maintenanceShortfall ?: JSONObject.NULL)
        .put("status", value.status)

    private fun rounded(value: Double): Double = round(value * 1000.0) / 1000.0
}

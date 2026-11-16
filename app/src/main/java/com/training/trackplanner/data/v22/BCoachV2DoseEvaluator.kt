package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.core.CanonicalCoreCatalog
import com.training.trackplanner.analysis.core.CoreClass
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.data.CanonicalMetadataRelation
import com.training.trackplanner.data.CanonicalRelationDomain
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSetPrescriptionResolver
import com.training.trackplanner.data.ProgramSkeletonItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

internal data class BCoachV2CandidateEvaluation(
    val axesByScenario: Map<BCoachV2Scenario, Map<BCoachObjectiveAxis, Double>>,
    val lossesByScenario: Map<BCoachV2Scenario, Double>,
    val needEnvelopes: List<BCoachNeedEnvelope>,
    val effectiveSetBreakdown: List<String>,
    val hardConstraints: List<BCoachConstraintResult>,
    val reasonCodes: List<String>,
    val maximumDayMinutes: Double,
    val planningStartDate: LocalDate,
    val sportObjectiveWeeks: List<BCoachSportShadowWeek> = emptyList()
) {
    val baseAxes: Map<BCoachObjectiveAxis, Double>
        get() = axesByScenario.getValue(BCoachV2Scenario.BASE)
    val baseLoss: Double
        get() = lossesByScenario.getValue(BCoachV2Scenario.BASE)
}

internal object BCoachV2DoseEvaluator {
    private val defaultHypertrophyMuscles = linkedSetOf(
        "QUADS", "GLUTES", "HAMSTRINGS", "CALVES", "CHEST", "BACK_LATS", "SHOULDERS",
        "BICEPS", "TRICEPS"
    )

    private data class Task(
        val id: String,
        val goal: BBalancedGoal,
        val historyWeekly: List<Double>,
        val center: Double,
        val historyReference: Double,
        val evidenceStatus: BCoachEvidenceStatus
    )

    private data class Context(
        val muscleCredits: Map<String, Map<String, Double>>,
        val coreCatalog: CanonicalCoreCatalog,
        val badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        val mainExerciseKeys: Set<String>,
        val exercises: Map<String, Exercise>,
        val rirPolicy: RpeRirPolicy?,
        val referenceCapacityByStableKey: Map<String, Double>
    )

    fun evaluate(
        plan: GeneratedProgramSkeleton,
        baseline: GeneratedProgramSkeleton,
        request: V22GenerationRequest,
        history: List<V22HistoryRow>,
        exercises: Map<String, Exercise>,
        muscleRelations: List<CanonicalMetadataRelation>,
        coreCatalog: CanonicalCoreCatalog,
        badmintonCatalog: CanonicalBadmintonObjectiveCatalog,
        mainExerciseKeys: Set<String>,
        goalWeights: List<BCoachGoalWeight>,
        baseRirByLocalId: Map<String, Double> = emptyMap(),
        baseIntensityByLocalId: Map<String, Double> = emptyMap(),
        rirByScenario: Map<BCoachV2Scenario, Map<String, Double>> = emptyMap(),
        intensityByScenario: Map<BCoachV2Scenario, Map<String, Double>> = emptyMap(),
        baseRirBySetId: Map<String, Double> = emptyMap(),
        baseIntensityBySetId: Map<String, Double> = emptyMap(),
        rirByScenarioAndSet: Map<BCoachV2Scenario, Map<String, Double>> = emptyMap(),
        intensityByScenarioAndSet: Map<BCoachV2Scenario, Map<String, Double>> = emptyMap(),
        rirPolicy: RpeRirPolicy? = null,
        badmintonHistory: List<V22BadmintonHistorySession> = emptyList(),
        referenceCapacityByStableKey: Map<String, Double> = emptyMap()
    ): BCoachV2CandidateEvaluation {
        val historyAtCutoff = history.filter { !it.date.isAfter(request.asOf) }
        val reasons = linkedSetOf<String>()
        historyAtCutoff.filter { it.rpe == null }.mapTo(reasons) {
            "HISTORY_RPE_UNAVAILABLE:${it.stableKey}"
        }
        historyAtCutoff.filter { row ->
            row.weightKg?.let { it.isFinite() && it > 0.0 } == true &&
                referenceCapacityByStableKey[row.stableKey] == null
        }.mapTo(reasons) { "HISTORY_RELATIVE_INTENSITY_UNAVAILABLE:${it.stableKey}" }
        val context = Context(
            muscleCredits = muscleCredits(muscleRelations),
            coreCatalog = coreCatalog,
            badmintonCatalog = badmintonCatalog,
            mainExerciseKeys = mainExerciseKeys,
            exercises = exercises,
            rirPolicy = rirPolicy,
            referenceCapacityByStableKey = referenceCapacityByStableKey
        )
        val tasks = tasks(plan, baseline, historyAtCutoff, request, goalWeights, context)
        BBalancedGoal.entries.filter { goal -> tasks.none { it.goal == goal } }
            .forEach { reasons += "NO_ELIGIBLE_TASK:${it.name}" }
        val weeks = (1..plan.request.durationWeeks.coerceAtLeast(1)).toList()
        val taskCounts = tasks.groupingBy(Task::goal).eachCount()
        val weightByGoal = goalWeights.associate { it.goal to checkNotNull(it.normalizedImportance) }
        val scenarioAxes = linkedMapOf<BCoachV2Scenario, Map<BCoachObjectiveAxis, Double>>()
        val needBase = mutableListOf<BCoachNeedEnvelope>()
        val breakdown = mutableListOf<String>()
        val sportObjectiveWeeks = mutableListOf<BCoachSportShadowWeek>()
        val progressionByScenario = BCoachV2Scenario.entries.associateWith { scenario ->
            progressionFit(
                plan,
                mainExerciseKeys,
                rirByScenario[scenario] ?: baseRirByLocalId,
                intensityByScenario[scenario] ?: baseIntensityByLocalId,
                rirByScenarioAndSet[scenario] ?: baseRirBySetId,
                intensityByScenarioAndSet[scenario] ?: baseIntensityBySetId
            )
        }
        val progressionSpread = progressionByScenario.values.let { values ->
            (values.maxOrNull()!! - values.minOrNull()!!).unitInterval()
        }

        BCoachV2Scenario.entries.forEach { scenario ->
            var gapWeighted = 0.0
            var excessWeighted = 0.0
            var doseWeight = 0.0
            val rampValues = mutableListOf<Double>()
            val previous = mutableMapOf<String, Double>()
            weeks.forEach { week ->
                val peak = isPeakWeek(plan, week)
                val recovery = isRecoveryWeek(plan, week)
                val multiplier = when {
                    recovery -> BCoachV2Policy.RECOVERY_WEEK_MULTIPLIER
                    else -> BCoachV2Policy.stageMultiplier(stageForWeek(plan, week))
                }
                val blockWeights = blockGoalWeights(plan, week, weightByGoal)
                tasks.forEach { task ->
                    val dose = plannedDose(
                        plan, week, task, scenario, context,
                        rirByScenarioAndSet[scenario] ?: baseRirBySetId,
                        intensityByScenarioAndSet[scenario] ?: baseIntensityBySetId
                    )
                    val interval = if (peak) 0.0 to 0.0
                        else BCoachV2Policy.interval(task.center, multiplier, scenario)
                    if (scenario == BCoachV2Scenario.BASE) {
                        if (!peak && task.goal == BBalancedGoal.SPORT_SUPPORT) {
                            sportObjectiveWeeks += BCoachSportShadowWeek(
                                objective = task.id.removePrefix("SPORT:"), week = week,
                                dose = dose, lower = interval.first, upper = interval.second
                            )
                        }
                        needBase += BCoachNeedEnvelope(
                            goal = task.goal,
                            block = blockForWeek(plan, week),
                            week = week,
                            lower = interval.first,
                            upper = interval.second,
                            source = "${BCoachV2Policy.POLICY_VERSION}:${task.id}",
                            status = BCoachValueStatus.READY,
                            reasonCodes = listOf(task.evidenceStatus.name),
                            taskId = task.id,
                            center = task.center,
                            exemptFromNeedScoring = peak
                        )
                        breakdown += "${task.id}|w=$week|dose=${canonical(dose)}|L=${canonical(interval.first)}|" +
                            "U=${canonical(interval.second)}|evidence=${task.evidenceStatus.name}"
                    }
                    if (!peak) {
                        val lower = interval.first
                        val upper = interval.second
                        val gap = (max(0.0, lower - dose) / max(lower, 1.0)).unitInterval()
                        val excess = (max(0.0, dose - upper) / max(upper, 1.0)).pow(2).unitInterval()
                        val taskWeight = blockWeights.getValue(task.goal) / max(1, taskCounts[task.goal] ?: 0)
                        gapWeighted += taskWeight * gap
                        excessWeighted += taskWeight * excess
                        doseWeight += taskWeight
                    }
                    val reference = previous[task.id] ?: task.historyReference
                    rampValues += BCoachV2Policy.ramp(dose, reference, recovery)
                    previous[task.id] = dose
                }
            }
            val schedule = BCoachV2ScheduleLoad.evaluate(
                plan, baseline, request, historyAtCutoff, exercises, scenario,
                badmintonHistory = badmintonHistory,
                rirBySetId = rirByScenarioAndSet[scenario] ?: baseRirBySetId,
                intensityBySetId = intensityByScenarioAndSet[scenario] ?: baseIntensityBySetId
            )
            val progression = progressionByScenario.getValue(scenario)
            val change = planChange(plan, baseline, historyAtCutoff)
            val totalSets = plan.items.sumOf { ProgramSetPrescriptionResolver.resolve(it).size }
            val calibrationSets = plan.items.sumOf { item ->
                ProgramSetPrescriptionResolver.resolve(item).count {
                    it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED &&
                        !isNonLoad(item, exercises[item.exerciseStableKey])
                }
            }
            val mainItems = plan.items.filter { it.exerciseStableKey in mainExerciseKeys }
            val unsupportedMain = mainItems.count { it.localId !in baseRirByLocalId }
            val uniqueItems = plan.items.distinctBy(ProgramSkeletonItem::exerciseStableKey)
            val unreviewed = uniqueItems.count {
                !isReviewedExercise(exercises[it.exerciseStableKey])
            }
            val uncertainty = (
                0.40 * unsupportedMain.toDouble() / max(1, mainItems.size) +
                    0.30 * unreviewed.toDouble() / max(1, uniqueItems.size) +
                    0.20 * calibrationSets.toDouble() / max(1, totalSets) +
                    0.10 * progressionSpread
                ).unitInterval()
            val cap = (request.sessionMinutes ?: BCoachV2Policy.DEFAULT_SESSION_MINUTES).toDouble()
            val execution = ((schedule.maximumDayMinutes / cap - 0.75) / 0.25).unitInterval()
            val axes = linkedMapOf(
                BCoachObjectiveAxis.GOAL_DEFICIT to
                    if (doseWeight == 0.0) 0.0 else (gapWeighted / weeks.size).unitInterval(),
                BCoachObjectiveAxis.GOAL_EXCESS to
                    if (doseWeight == 0.0) 0.0 else (excessWeighted / weeks.size).unitInterval(),
                BCoachObjectiveAxis.PROGRESSION_FIT to progression,
                BCoachObjectiveAxis.RECOVERY_LOAD to schedule.recoveryScore,
                BCoachObjectiveAxis.RAMP_CHANGE to rampValues.averageOrZero().unitInterval(),
                BCoachObjectiveAxis.EXECUTION to execution,
                BCoachObjectiveAxis.PLAN_CHANGE to change,
                BCoachObjectiveAxis.UNCERTAINTY to uncertainty
            )
            scenarioAxes[scenario] = axes
        }
        val losses = scenarioAxes.mapValues { (_, scores) -> BCoachV2Policy.weightedLoss(scores) }
        val baseSchedule = BCoachV2ScheduleLoad.evaluate(
            plan, baseline, request, historyAtCutoff, exercises, BCoachV2Scenario.BASE,
            badmintonHistory = badmintonHistory,
            rirBySetId = baseRirBySetId,
            intensityBySetId = baseIntensityBySetId
        )
        val constraints = hardConstraints(plan, baseline, request, baseSchedule, exercises)
        if (baseSchedule.unmappedAxisExerciseCount > 0) reasons +=
            "UNMAPPED_AXIS_EXERCISES:${baseSchedule.unmappedAxisExerciseCount}"
        return BCoachV2CandidateEvaluation(
            axesByScenario = scenarioAxes,
            lossesByScenario = losses,
            needEnvelopes = needBase,
            effectiveSetBreakdown = breakdown,
            hardConstraints = constraints,
            reasonCodes = reasons.toList().sorted(),
            maximumDayMinutes = baseSchedule.maximumDayMinutes,
            planningStartDate = baseSchedule.planningStartDate,
            sportObjectiveWeeks = sportObjectiveWeeks
        )
    }

    private fun tasks(
        plan: GeneratedProgramSkeleton,
        baseline: GeneratedProgramSkeleton,
        history: List<V22HistoryRow>,
        request: V22GenerationRequest,
        goalWeights: List<BCoachGoalWeight>,
        context: Context
    ): List<Task> {
        val keys = (plan.items + baseline.items).map(ProgramSkeletonItem::exerciseStableKey).toSet() +
            history.map(V22HistoryRow::stableKey)
        val taskDefinitions = buildList<Pair<String, BBalancedGoal>> {
            keys.filter { it in context.mainExerciseKeys }.sorted().forEach {
                add("STRENGTH:$it" to BBalancedGoal.STRENGTH)
            }
            defaultHypertrophyMuscles.forEach { add("HYPERTROPHY:$it" to BBalancedGoal.HYPERTROPHY) }
            if (keys.any { context.coreCatalog.resolve(it)?.coreClass == CoreClass.DIRECT }) {
                add("CORE:DIRECT" to BBalancedGoal.CORE)
            }
            // SPORT task identity belongs to the authority catalog, not to whichever
            // exercises happened to survive in one candidate. Missing objectives then
            // receive a genuine zero dose and candidates stay comparable.
            context.badmintonCatalog.allRelations()
                .filter { it.transferLevel.coefficient > 0.0 }
                .map { it.objective.name }.distinct()
                .sorted().forEach { add("SPORT:$it" to BBalancedGoal.SPORT_SUPPORT) }
        }.distinct()
        val importance = goalWeights.associate { it.goal to checkNotNull(it.explicitImportance) }
        val completeWeeks = completeWeekStarts(request.asOf)
        val rowsByWeek = completeWeeks.associateWith { start ->
            history.filter { it.date in start..start.plusDays(6) }
        }
        return taskDefinitions.map { (id, goal) ->
            val d0 = BCoachV2Policy.d0(goal, importance.getValue(goal))
            val weekly = completeWeeks.mapNotNull { start ->
                val taskDoses = rowsByWeek.getValue(start).map { row ->
                    historyDose(row, id, goal, context)
                }
                taskDoses.sum().takeIf { taskDoses.any { it > 0.0 } }
            }
            val center = BCoachV2Policy.personalizedCenter(d0, weekly, BCoachV2Policy.dMax(goal))
            Task(
                id = id,
                goal = goal,
                historyWeekly = weekly,
                center = center,
                historyReference = if (weekly.size >= BCoachV2Policy.PERSONAL_VALID_WEEKS_MIN)
                    median(weekly) else d0,
                evidenceStatus = if (weekly.size >= BCoachV2Policy.PERSONAL_VALID_WEEKS_MIN)
                    BCoachEvidenceStatus.OBSERVED else BCoachEvidenceStatus.POLICY_ASSUMED
            )
        }
    }

    private fun plannedDose(
        plan: GeneratedProgramSkeleton,
        week: Int,
        task: Task,
        scenario: BCoachV2Scenario,
        context: Context,
        rirBySetId: Map<String, Double>,
        intensityBySetId: Map<String, Double>
    ): Double = plan.items.asSequence().filter { it.weekNumber == week }.sumOf { item ->
        ProgramSetPrescriptionResolver.resolve(item).sumOf { set ->
            val setId = BCoachV2LoadCandidateResolver.setId(item.localId, set.setIndex)
            val workingRir = rirBySetId[setId] ?: set.expectedRir ?:
                set.targetRpeMin?.let { 10.0 - it }
            val relativeIntensity = intensityBySetId[setId] ?: set.relativeIntensity
            setDose(item.exerciseStableKey, task.id, task.goal, scenario, context, item, set,
                qEffort(workingRir), relativeIntensity)
        }
    }

    private fun historyDose(
        row: V22HistoryRow,
        taskId: String,
        goal: BBalancedGoal,
        context: Context
    ): Double {
        val set = ProgramSetPrescription(
            setIndex = row.setIndex,
            reps = row.reps?.toInt() ?: 0,
            weightKg = row.weightKg ?: 0.0,
            seconds = row.seconds?.toInt() ?: 0,
            targetRpeMin = row.rpe,
            loadState = if ((row.weightKg ?: 0.0) > 0.0) ProgramLoadState.EXPLICIT_LOAD
                else ProgramLoadState.USER_CALIBRATION_REQUIRED
        )
        val effort = row.rpe?.let { rpe ->
            context.rirPolicy?.resolve(rpe)?.probabilities?.sumOf { value ->
                value.probability * qEffort(value.rir.toDouble())
            }
        } ?: 1.0
        val relativeIntensity = context.referenceCapacityByStableKey[row.stableKey]
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { capacity -> row.weightKg?.takeIf { it.isFinite() && it > 0.0 }?.div(capacity) }
        val item = ProgramSkeletonItem(
            localId = "history", weekNumber = 1, dayOfWeek = 1, orderIndex = 1,
            exerciseStableKey = row.stableKey, exerciseName = row.exerciseName, category = row.category,
            restSeconds = 60, prescription = "history", setCount = 1, reps = set.reps,
            weightKg = set.weightKg, seconds = set.seconds, selectionReason = "history",
            weightSource = "history", setPrescriptions = listOf(set)
        )
        return setDose(row.stableKey, taskId, goal, BCoachV2Scenario.BASE, context, item, set,
            effort, relativeIntensity)
    }

    private fun setDose(
        stableKey: String,
        taskId: String,
        goal: BBalancedGoal,
        scenario: BCoachV2Scenario,
        context: Context,
        item: ProgramSkeletonItem,
        set: ProgramSetPrescription,
        effortCredit: Double,
        relativeIntensity: Double?
    ): Double = structuralCredit(stableKey, taskId, goal, scenario, context) *
        qRep(set, goal) * effortCredit * qRelativeIntensity(goal, relativeIntensity) *
        qLoad(set, item, context.exercises[stableKey]) * qMetric(set, goal)

    private fun structuralCredit(
        stableKey: String,
        taskId: String,
        goal: BBalancedGoal,
        scenario: BCoachV2Scenario,
        context: Context
    ): Double = when (goal) {
        BBalancedGoal.STRENGTH -> if (taskId == "STRENGTH:$stableKey" && stableKey in context.mainExerciseKeys) 1.0 else 0.0
        BBalancedGoal.HYPERTROPHY -> {
            val muscle = taskId.removePrefix("HYPERTROPHY:")
            val base = context.muscleCredits[stableKey]?.get(muscle) ?: 0.0
            if (base >= 1.0) 1.0 else (base * scenario.indirectCreditFactor).coerceAtMost(1.0)
        }
        BBalancedGoal.CORE -> if (context.coreCatalog.resolve(stableKey)?.coreClass == CoreClass.DIRECT) 1.0 else 0.0
        BBalancedGoal.SPORT_SUPPORT -> {
            val objective = taskId.removePrefix("SPORT:")
            context.badmintonCatalog.relations(stableKey).filter { it.objective.name == objective }
                .maxOfOrNull { relation -> sportCredit(relation.transferLevel) } ?: 0.0
        }
    }

    private fun qRep(set: ProgramSetPrescription, goal: BBalancedGoal): Double = when (goal) {
        BBalancedGoal.HYPERTROPHY -> when (set.reps) {
            in 5..30 -> 1.0
            else -> if (set.reps > 0) 0.5 else 0.0
        }
        BBalancedGoal.STRENGTH -> when (set.reps) {
            in 1..6 -> 1.0
            in 7..10 -> 0.5
            in 11..Int.MAX_VALUE -> 0.25
            else -> 0.0
        }
        BBalancedGoal.CORE -> if (set.reps > 0 || set.seconds > 0) 1.0 else 0.5
        BBalancedGoal.SPORT_SUPPORT -> if (set.reps > 0 || set.seconds > 0) 1.0 else 0.75
    }

    internal fun qEffort(rir: Double?): Double = when {
        rir == null -> 1.0
        rir <= 4.0 -> 1.0
        rir < 6.0 -> 1.0 - (rir - 4.0) * 0.125
        rir < 8.0 -> 0.75 - (rir - 6.0) * 0.125
        else -> 0.5
    }

    private fun qRelativeIntensity(goal: BBalancedGoal, relativeIntensity: Double?): Double {
        val value = relativeIntensity?.takeIf { it.isFinite() && it > 0.0 } ?: return 1.0
        return when (goal) {
            BBalancedGoal.STRENGTH -> (0.5 + (value - 0.50) / 0.30 * 0.5).coerceIn(0.5, 1.0)
            BBalancedGoal.HYPERTROPHY -> (0.75 + (value - 0.30) / 0.30 * 0.25).coerceIn(0.75, 1.0)
            BBalancedGoal.CORE, BBalancedGoal.SPORT_SUPPORT -> 1.0
        }
    }

    private fun qLoad(set: ProgramSetPrescription, item: ProgramSkeletonItem, exercise: Exercise?): Double = when {
        isNonLoad(item, exercise) -> 1.0
        set.loadState == ProgramLoadState.EXPLICIT_LOAD && set.weightKg > 0.0 -> 1.0
        set.estimatedLoadKg?.let { it.isFinite() && it > 0.0 } == true -> 1.0
        else -> BCoachV2Policy.UNKNOWN_RESISTANCE_SET_CREDIT
    }

    private fun qMetric(set: ProgramSetPrescription, goal: BBalancedGoal): Double = when (goal) {
        BBalancedGoal.CORE, BBalancedGoal.SPORT_SUPPORT -> when {
            set.reps > 0 || set.seconds > 0 -> 1.0
            else -> 0.5
        }
        else -> 1.0
    }

    private fun progressionFit(
        plan: GeneratedProgramSkeleton,
        mainExerciseKeys: Set<String>,
        rirByLocalId: Map<String, Double>,
        intensityByLocalId: Map<String, Double>,
        rirBySetId: Map<String, Double>,
        intensityBySetId: Map<String, Double>
    ): Double {
        val mains = plan.items.filter { it.exerciseStableKey in mainExerciseKeys }
        if (mains.isEmpty()) return 0.0
        return mains.flatMap { item ->
            ProgramSetPrescriptionResolver.resolve(item).map { set ->
                val setId = BCoachV2LoadCandidateResolver.setId(item.localId, set.setIndex)
                val rir = rirBySetId[setId] ?: rirByLocalId[item.localId] ?: return@map 0.35
                val bounds = if ("SPORT" in item.selectionRole) 2.0..4.0 else 1.0..3.0
                val rirDistance = intervalDistance(rir, bounds.start, bounds.endInclusive) / 4.0
                val intensity = intensityBySetId[setId] ?: intensityByLocalId[item.localId]
                if (intensity == null || "STRENGTH" !in item.selectionRole) rirDistance.unitInterval()
                else (rirDistance + 0.5 * intervalDistance(intensity, 0.80, 1.0) / 0.20).unitInterval()
            }
        }.averageOrZero().unitInterval()
    }

    private fun planChange(
        plan: GeneratedProgramSkeleton,
        baseline: GeneratedProgramSkeleton,
        history: List<V22HistoryRow>
    ): Double {
        fun slots(value: GeneratedProgramSkeleton) = value.items.associateBy {
            Triple(it.weekNumber, it.dayOfWeek, it.exerciseStableKey)
        }
        val before = slots(baseline)
        val after = slots(plan)
        val added = (after.keys - before.keys).size
        val removedKeys = before.keys - after.keys
        val removed = removedKeys.size
        var repsChanged = 0
        var setsChanged = 0
        before.keys.intersect(after.keys).forEach { key ->
            val left = ProgramSetPrescriptionResolver.resolve(before.getValue(key))
            val right = ProgramSetPrescriptionResolver.resolve(after.getValue(key))
            if (left.size != right.size) setsChanged++
            repsChanged += left.zip(right).count { (a, b) -> a.reps != b.reps }
        }
        val denominator = max(4, baseline.items.size * 2)
        val base = (added + removed + repsChanged + setsChanged).toDouble() / denominator
        val successfulKeys = history.filter { (it.reps ?: 0.0) > 0.0 }.map(V22HistoryRow::stableKey).toSet()
        val anchorRemoved = removedKeys.any { it.third in successfulKeys }
        return (base + if (anchorRemoved) 0.10 else 0.0).unitInterval()
    }

    private fun hardConstraints(
        plan: GeneratedProgramSkeleton,
        baseline: GeneratedProgramSkeleton,
        request: V22GenerationRequest,
        schedule: BCoachV2ScheduleEvaluation,
        exercises: Map<String, Exercise>
    ): List<BCoachConstraintResult> = buildList {
        add(constraint("SESSION_TIME_LIMIT", if (schedule.daysOverLimit == 0)
            BCoachConstraintStatus.PASS else BCoachConstraintStatus.FAIL,
            if (schedule.daysOverLimit == 0) null else "DAYS_OVER=${schedule.daysOverLimit}"))
        add(constraint("PROGRAM_BOUNDS", if (plan.items.all {
                it.weekNumber in 1..plan.request.durationWeeks && it.dayOfWeek in 1..7
            }) BCoachConstraintStatus.PASS else BCoachConstraintStatus.FAIL))
        add(constraint("SET_PRESCRIPTION_INTEGRITY", if (plan.items.all { item ->
                item.setCount > 0 && item.setCount <= BCoachV2Policy.MAX_PER_EXERCISE_SETS &&
                    ProgramSetPrescriptionResolver.resolve(item).size == item.setCount
            }) BCoachConstraintStatus.PASS else BCoachConstraintStatus.FAIL))
        add(constraint("DUPLICATE_ORDER_SLOT", if (plan.items.map {
                Triple(it.weekNumber, it.dayOfWeek, it.orderIndex)
            }.distinct().size == plan.items.size) BCoachConstraintStatus.PASS else BCoachConstraintStatus.FAIL))
        add(constraint("DUPLICATE_EXERCISE_SESSION", if (plan.items.map {
                Triple(it.weekNumber, it.dayOfWeek, it.exerciseStableKey)
            }.distinct().size == plan.items.size) BCoachConstraintStatus.PASS else BCoachConstraintStatus.FAIL))
        val fixed = request.fixedWeekdays
        if (fixed != null) add(constraint("FIXED_WEEKDAYS", if ((1..plan.request.durationWeeks).all { week ->
                plan.items.filter { it.weekNumber == week }.map(ProgramSkeletonItem::dayOfWeek).toSet() == fixed
            })
            BCoachConstraintStatus.PASS else BCoachConstraintStatus.FAIL))
        if (shouldEnforceNonClusteredSchedule(request)) add(constraint(
            "CONSECUTIVE_TRAINING_DAYS",
            if ((1..plan.request.durationWeeks).all { week ->
                    longestConsecutiveRun(plan.items.filter { it.weekNumber == week }
                        .map(ProgramSkeletonItem::dayOfWeek).toSet()) < 3
                }) BCoachConstraintStatus.PASS else BCoachConstraintStatus.FAIL
        ))
        val allowed = request.allowedWeekdays ?: (1..7).toSet()
        add(constraint("ALLOWED_WEEKDAYS", if (plan.items.all { it.dayOfWeek in allowed })
            BCoachConstraintStatus.PASS else BCoachConstraintStatus.FAIL))
        add(constraint("LOAD_STATE_VALID", if (plan.items.flatMap(ProgramSetPrescriptionResolver::resolve).all {
                it.weightKg.isFinite() && it.weightKg >= 0.0 &&
                    !(it.loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED && it.weightKg != 0.0)
            }) BCoachConstraintStatus.PASS else BCoachConstraintStatus.FAIL))
        val baselineKeys = baseline.items.map(ProgramSkeletonItem::exerciseStableKey).toSet()
        val automaticallyAdded = plan.items.map(ProgramSkeletonItem::exerciseStableKey).toSet() - baselineKeys
        add(constraint("REVIEWED_AUTOMATIC_EXERCISE", if (automaticallyAdded.all { key ->
                isReviewedExercise(exercises[key])
            }) BCoachConstraintStatus.PASS else BCoachConstraintStatus.FAIL,
            if (automaticallyAdded.all { key ->
                    isReviewedExercise(exercises[key])
                }) null else "UNREVIEWED_AUTOMATIC_EXERCISE"))
        val userLockedKeys = baseline.items.filter {
            isExplicitUserExerciseLock(it.selectionReason)
        }.map(ProgramSkeletonItem::exerciseStableKey).toSet()
        add(constraint("USER_EXERCISE_LOCKS", if (userLockedKeys.all { key ->
                plan.items.any { it.exerciseStableKey == key }
            }) BCoachConstraintStatus.PASS else BCoachConstraintStatus.FAIL))
    }

    internal fun isExplicitUserExerciseLock(selectionReason: String): Boolean {
        val reason = selectionReason.trim().uppercase()
        return reason.contains("USER_EXERCISE_LOCK") || reason.contains("USER_LOCKED") ||
            reason.contains("LOCKED_BY_USER")
    }

    internal fun shouldEnforceNonClusteredSchedule(request: V22GenerationRequest): Boolean {
        if (request.fixedWeekdays != null || request.days < 3) return false
        val allowed = (request.allowedWeekdays ?: (1..7).toSet()).filter { it in 1..7 }.sorted()
        fun combinations(source: List<Int>, count: Int): List<Set<Int>> =
            if (count == 0) listOf(emptySet())
            else source.flatMapIndexed { index, day ->
                combinations(source.drop(index + 1), count - 1).map { it + day }
            }
        return combinations(allowed, request.days).any { longestConsecutiveRun(it) < 3 }
    }

    internal fun longestConsecutiveRun(days: Set<Int>): Int = days.maxOfOrNull { start ->
        var count = 0
        var day = start
        while (count < 7 && day in days) {
            count++
            day = day % 7 + 1
        }
        count
    } ?: 0

    private fun isReviewedExercise(exercise: Exercise?): Boolean {
        val confidence = exercise?.metadataConfidence?.trim()?.uppercase().orEmpty()
        return exercise != null && !exercise.needsReview && confidence !in setOf(
            "", "UNKNOWN", "UNREVIEWED", "REVIEW_REQUIRED"
        )
    }

    private fun blockGoalWeights(
        plan: GeneratedProgramSkeleton,
        week: Int,
        base: Map<BBalancedGoal, Double>
    ): Map<BBalancedGoal, Double> {
        val focus = plan.items.firstOrNull { it.weekNumber == week }?.selectionRole.orEmpty()
        val selected = when {
            "STRENGTH" in focus -> BBalancedGoal.STRENGTH
            "HYPERTROPHY" in focus -> BBalancedGoal.HYPERTROPHY
            "SPORT_SUPPORT" in focus -> BBalancedGoal.SPORT_SUPPORT
            else -> null
        }
        return BBalancedGoal.entries.associateWith { goal ->
            if (selected == null) base.getValue(goal)
            else (1.0 - BCoachV2Policy.FIXED_BLOCK_BOOST) * base.getValue(goal) +
                if (goal == selected) BCoachV2Policy.FIXED_BLOCK_BOOST else 0.0
        }
    }

    private fun muscleCredits(relations: List<CanonicalMetadataRelation>): Map<String, Map<String, Double>> =
        relations.asSequence().filter { it.domain == CanonicalRelationDomain.MUSCLE }
            .groupBy(CanonicalMetadataRelation::exerciseStableKey).mapValues { (_, rows) ->
                rows.groupBy(CanonicalMetadataRelation::relationValue).mapValues { (_, sameMuscle) ->
                    sameMuscle.maxOf { relation ->
                        when (relation.relationType) {
                            "PRIMARY" -> 1.0
                            "SECONDARY" -> 0.5
                            else -> 0.0
                        }
                    }
                }
            }

    private fun completeWeekStarts(asOf: LocalDate): List<LocalDate> {
        val currentMonday = asOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (4 downTo 1).map { currentMonday.minusWeeks(it.toLong()) }
    }

    private fun isPeakWeek(plan: GeneratedProgramSkeleton, week: Int): Boolean =
        plan.weekPlans.firstOrNull { it.weekIndex == week }?.weekType?.contains("PEAK") == true ||
            plan.items.any { it.weekNumber == week && "PEAK" in it.selectionRole }

    private fun isRecoveryWeek(plan: GeneratedProgramSkeleton, week: Int): Boolean =
        plan.weekPlans.firstOrNull { it.weekIndex == week }?.let { it.deloadFlag || "RECOVERY" in it.weekType } == true

    private fun blockForWeek(plan: GeneratedProgramSkeleton, week: Int): Int =
        plan.items.firstOrNull { it.weekNumber == week }?.selectionReason
            ?.let { Regex("B12_BLOCK_(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 1

    private fun stageForWeek(plan: GeneratedProgramSkeleton, week: Int): Int {
        val block = blockForWeek(plan, week)
        val weeksInBlock = (1..week).filter { blockForWeek(plan, it) == block && !isRecoveryWeek(plan, it) }
        return (weeksInBlock.indexOf(week)).coerceAtLeast(0)
    }

    private fun isNonLoad(item: ProgramSkeletonItem, exercise: Exercise?): Boolean {
        val metric = exercise?.progressMetricType.orEmpty().uppercase()
        return item.seconds > 0 || listOf("DURATION", "TIME", "DISTANCE", "JUMP", "SPEED", "QUALITY")
            .any(metric::contains)
    }

    private fun sportCredit(level: BadmintonObjectiveTransferLevel): Double = when (level) {
        BadmintonObjectiveTransferLevel.DIRECT -> 1.0
        BadmintonObjectiveTransferLevel.SUPPORTIVE -> 0.6
        BadmintonObjectiveTransferLevel.GENERAL -> 0.25
        BadmintonObjectiveTransferLevel.LOW -> 0.1
        BadmintonObjectiveTransferLevel.NONE -> 0.0
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    }

    private fun intervalDistance(value: Double, lower: Double, upper: Double): Double =
        max(0.0, lower - value) + max(0.0, value - upper)

    private fun canonical(value: Double): String =
        java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun constraint(
        id: String,
        status: BCoachConstraintStatus,
        reason: String? = null
    ) = BCoachConstraintResult(id, status, listOfNotNull(reason))

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}

package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramDaySelector
import com.training.trackplanner.data.ProgramOptimizationSummary
import com.training.trackplanner.data.ProgramBadmintonCategory
import com.training.trackplanner.data.ProgramIntensityResolver
import com.training.trackplanner.data.ProgramRuleTables
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.round
import kotlin.math.roundToInt

data class PlannedExercise(
    val stableKey: String,
    val role: String,
    val reason: String,
    val priority: Int,
    val styleVariant: String = "",
    val style: StrengthProgrammingStyle = StrengthProgrammingStyle.NONE,
    val targetSets: Int = 0,
    val transition: AnchorTransition? = null,
    val representedGapCodes: Set<String> = emptySet(),
    val representedObjectives: Set<String> = emptySet(),
    val supportiveObjectives: Set<String> = emptySet(),
    val material: Boolean = true
)

class ExerciseContinuityPlanner {
    fun select(state: AthletePlanningState, transitions: Map<String, AnchorTransition>, allocations: Map<String, Int>, weeklyDays: Int): List<PlannedExercise> {
        return state.anchors.flatMap { anchor ->
            val transition = transitions.getValue(anchor.stableKey)
            val style = anchor.style.takeIf { anchor.styleConfidence != PlanningConfidence.LOW } ?: StrengthProgrammingStyle.NONE
            val totalSets = allocations.getOrDefault(anchor.stableKey, 0)
            if (totalSets <= 0) return@flatMap emptyList()
            val multiDay = style in setOf(StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING, StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM, StrengthProgrammingStyle.DUP_LIKE_UNDULATING)
            var exposures = if (!multiDay) 1 else when (transition.structureTreatment) {
                StructureTreatment.PRESERVE -> 3
                StructureTreatment.PRESERVE_CORE_REBALANCE, StructureTreatment.PARTIAL_CONTINUITY -> 2
                StructureTreatment.ROTATE_EMPHASIS -> 1
            }
            if (transition.doseTreatment == DoseTreatment.REDUCE_MODERATELY) exposures = minOf(exposures, 2)
            exposures = minOf(exposures, totalSets, weeklyDays).coerceAtLeast(1)
            val full = if (style == StrengthProgrammingStyle.DUP_LIKE_UNDULATING) listOf("STRENGTH", "VOLUME", "MODERATE") else listOf("HEAVY", "LIGHT", "MEDIUM")
            val variants = when {
                exposures == 1 -> listOf(if ("heavy_exposure" in transition.moderatedFeatures) full.last() else full.first())
                exposures == 2 && "heavy_exposure" in transition.moderatedFeatures -> full.filterNot { it in setOf("HEAVY", "STRENGTH") }.take(2)
                exposures == 2 -> listOf(full.first(), full[1])
                else -> full
            }
            val counts = splitSets(totalSets, variants.size)
            variants.mapIndexed { index, variant ->
                PlannedExercise(
                    stableKey = anchor.stableKey,
                    role = if (variant.isBlank()) "CONTINUITY_${anchor.movementGroup}" else "STYLE_${variant}_${anchor.movementGroup}",
                    reason = "${anchor.sessions}회 완료 세션을 관찰했고 다음 블록은 ${transition.structureTreatment.name}/${transition.doseTreatment.name}로 처리했습니다.",
                    priority = when (transition.structureTreatment) {
                        StructureTreatment.PRESERVE -> 100
                        StructureTreatment.PRESERVE_CORE_REBALANCE -> 95
                        StructureTreatment.PARTIAL_CONTINUITY -> 75
                        StructureTreatment.ROTATE_EMPHASIS -> 60
                    },
                    styleVariant = if (multiDay) variant else "",
                    style = style,
                    targetSets = counts[index],
                    transition = transition
                )
            }
        }
    }

    private fun splitSets(total: Int, parts: Int): List<Int> = List(parts) { index -> total / parts + if (index < total % parts) 1 else 0 }
}

class GapCandidateSelector {
    fun select(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, gaps: List<AdaptationGap>, used: Set<String>, allAlternatives: Boolean = false): List<PlannedExercise> {
        val chosen = used.toMutableSet()
        val orderedGaps = gaps.withIndex().sortedWith(
            compareBy<IndexedValue<AdaptationGap>> { gapPriorityRank(it.value.priority) }.thenBy { it.index }
        ).map(IndexedValue<AdaptationGap>::value)
        return orderedGaps.flatMap { gap ->
            if (gap.code == "BADMINTON_FOUNDATIONAL_ONRAMP" && state.badmintonIntent != BadmintonPlanningIntent.ENABLED) return@flatMap emptyList()
            if (gap.code == "RESISTANCE_FOUNDATIONAL_ONRAMP") {
                return@flatMap listOf(
                    MovementCoverage.LOWER_KNEE,
                    MovementCoverage.POSTERIOR_CHAIN,
                    MovementCoverage.HORIZONTAL_PUSH,
                    MovementCoverage.HORIZONTAL_PULL
                ).mapNotNull { coverage ->
                    selectableCandidates(snapshot, state, chosen)
                        .firstOrNull { key -> snapshot.activityKind(key) == PlannedActivityKind.RESISTANCE && snapshot.movementCoverage(key) == coverage }
                        ?.also(chosen::add)
                        ?.let { key -> PlannedExercise(key, "FOUNDATIONAL_${coverage.name}", gap.reason, 90, targetSets = 2, representedGapCodes = setOf(gap.code)) }
                }
            }
            val historyKeys = snapshot.allConfirmedSets.mapTo(mutableSetOf(), PlanningSetRecord::stableKey)
            val objective = badmintonObjectiveFromGap(gap.code)
            val target = when (gap.code.substringAfter("HYPERTROPHY_REBALANCE_", gap.code)) {
                "LOWER_KNEE" -> setOf(MovementCoverage.LOWER_KNEE)
                "POSTERIOR_CHAIN" -> setOf(MovementCoverage.POSTERIOR_CHAIN)
                "HORIZONTAL_PUSH" -> setOf(MovementCoverage.HORIZONTAL_PUSH)
                "UPPER_PULL" -> setOf(MovementCoverage.HORIZONTAL_PULL, MovementCoverage.VERTICAL_PULL)
                "CORE_DIRECT" -> setOf(MovementCoverage.CORE_DIRECT)
                "CALVES" -> setOf(MovementCoverage.CALVES)
                "ARMS_BICEPS" -> setOf(MovementCoverage.ARMS_BICEPS)
                "ARMS_TRICEPS" -> setOf(MovementCoverage.ARMS_TRICEPS)
                else -> emptySet()
            }
            val candidates = selectableCandidates(snapshot, state, if (allAlternatives) used else chosen).asSequence()
                .filter { key ->
                    when {
                        gap.code == "BADMINTON_FOUNDATIONAL_ONRAMP" -> snapshot.activityKind(key) in PERFORMANCE_ACTIVITY_KINDS && snapshot.badmintonDirectObjectives[key].orEmpty().isNotEmpty() && snapshot.hasSafePerformancePrescription(key)
                        objective.isNotBlank() -> (objective in snapshot.badmintonDirectObjectives[key].orEmpty() ||
                            objective in snapshot.badmintonSupportiveObjectives[key].orEmpty()) &&
                            (snapshot.activityKind(key) == PlannedActivityKind.RESISTANCE ||
                                (snapshot.activityKind(key) in PERFORMANCE_ACTIVITY_KINDS && snapshot.hasSafePerformancePrescription(key)))
                        else -> snapshot.activityKind(key) == PlannedActivityKind.RESISTANCE && snapshot.movementCoverage(key) in target
                    }
                }
                .sortedWith(
                    compareByDescending<String> { objective in snapshot.badmintonDirectObjectives[it].orEmpty() }
                        .thenByDescending { it in historyKeys }
                        .thenByDescending { state.freeWeightWillingness != FreeWeightWillingness.PREFER_FAMILIAR || !snapshot.isFreeWeight(it) }
                        .thenByDescending { snapshot.metadata[it]?.sourceConfidenceLevel == "HIGH" }
                        .thenBy { it }
                )
                .toList()
            val selectedKeys = if (allAlternatives) candidates else candidates.take(1)
            selectedKeys.map { key ->
                chosen += key
                val priority = when (gap.priority) { "HIGH" -> 100; "MEDIUM", "MODERATE" -> 90; else -> 70 }
                val targetSets = if (snapshot.activityKind(key) in PERFORMANCE_ACTIVITY_KINDS)
                    PerformancePrescriptionResolver.resolve(snapshot, key)!!.sets.size.coerceAtLeast(2) else 2
                val supportiveOnly = objective in snapshot.badmintonSupportiveObjectives[key].orEmpty() &&
                    objective !in snapshot.badmintonDirectObjectives[key].orEmpty()
                PlannedExercise(key, if (supportiveOnly) "BADMINTON_SUPPORTIVE_$objective"
                    else if (gap.code.startsWith("BADMINTON")) "BADMINTON_OBJECTIVE_$objective" else "COVERAGE_${gap.code}",
                    if (supportiveOnly) "해당 목표의 보조운동으로 배정했습니다. 직접 훈련을 대체하지는 않습니다." else gap.reason,
                    priority, targetSets = targetSets,
                    representedGapCodes = setOf(gap.code), representedObjectives = snapshot.badmintonDirectObjectives[key].orEmpty(),
                    supportiveObjectives = snapshot.badmintonSupportiveObjectives[key].orEmpty(),
                    material = gap.contributesTransitionPressure)
            }
        }
    }

    private fun selectableCandidates(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, chosen: Set<String>): List<String> {
        val historyKeys = snapshot.allConfirmedSets.mapTo(mutableSetOf(), PlanningSetRecord::stableKey)
        return snapshot.exercises.keys.asSequence()
            .filter { key -> key !in chosen && snapshot.metadata[key]?.planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE") }
            .filterNot(snapshot::explicitlyRestricted)
            .filter { key -> state.freeWeightWillingness !in setOf(FreeWeightWillingness.AVOID, FreeWeightWillingness.UNRESOLVED) || !snapshot.isFreeWeight(key) || key in historyKeys }
            .sortedWith(
                compareByDescending<String> { it in historyKeys }
                    .thenByDescending { state.freeWeightWillingness != FreeWeightWillingness.PREFER_FAMILIAR || !snapshot.isFreeWeight(it) }
                    .thenByDescending { snapshot.metadata[it]?.sourceConfidenceLevel == "HIGH" }
                    .thenBy { it }
            )
            .toList()
    }

    private fun gapPriorityRank(priority: String): Int = when (priority) {
        "HIGH" -> 0
        "MEDIUM", "MODERATE" -> 1
        else -> 2
    }
}

private val PERFORMANCE_ACTIVITY_KINDS = setOf(
    PlannedActivityKind.STRUCTURED_BADMINTON_DRILL,
    PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL
)

internal fun badmintonObjectiveFromGap(code: String): String = when {
    code.startsWith("BADMINTON_DROP_") -> code.removePrefix("BADMINTON_DROP_")
    code.startsWith("BADMINTON_UNDERREPRESENTED_") -> code.removePrefix("BADMINTON_UNDERREPRESENTED_")
    code.startsWith("BADMINTON_DEVELOP_") -> code.removePrefix("BADMINTON_DEVELOP_")
    else -> ""
}

internal fun PlannedExercise.supportiveGapCodes(): Set<String> = representedGapCodes.filterTo(linkedSetOf()) {
    val objective = badmintonObjectiveFromGap(it)
    objective in supportiveObjectives && objective !in representedObjectives
}

internal fun PlanningHistorySnapshot.reviewedBadmintonCategory(stableKey: String): ProgramBadmintonCategory? =
    ProgramRuleTables.badmintonAccessories.entries.firstOrNull { (_, specs) -> specs.any { it.stableKey == stableKey } }?.key

private fun PlanningHistorySnapshot.hasSafePerformancePrescription(stableKey: String): Boolean =
    PerformancePrescriptionResolver.resolve(this, stableKey) != null


data class PlannedPrescription(
    val text: String,
    val sets: List<ProgramSetPrescription>,
    val restSeconds: Int,
    val weightSource: String
)

class PersonalizedPrescriptionPlanner {
    fun prescribe(snapshot: PlanningHistorySnapshot, item: PlannedExercise, style: StrengthProgrammingStyle, week: Int): PlannedPrescription =
        prescribe(snapshot, StrengthIntent.MIXED, item, style)

    fun prescribe(snapshot: PlanningHistorySnapshot, strengthIntent: StrengthIntent, item: PlannedExercise, style: StrengthProgrammingStyle): PlannedPrescription {
        if (snapshot.activityKind(item.stableKey) in PERFORMANCE_ACTIVITY_KINDS) return PerformancePrescriptionResolver.prescribe(snapshot, item)
        val history = snapshot.allConfirmedSets.filter { it.stableKey == item.stableKey }
        val latestDate = history.maxByOrNull { it.date.toEpochDay() }?.date
        val latestWeek = latestDate?.let { it.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) to it.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) }
        val last = history.filter { latestWeek == (it.date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) to it.date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)) }
            .maxWithOrNull(compareBy<PlanningSetRecord> { it.weightKg }.thenBy { it.reps })
        if (last == null) {
            val count = item.targetSets.coerceAtLeast(2)
            val sets = List(count) { index -> ProgramSetPrescription(index + 1, 8, 0.0, 0) }
            return PlannedPrescription("8–12회 × ${count}세트 · RPE 6–8 (첫 세션에서 중량 확인)", sets, 90, "PROVISIONAL_RPE_NO_INVENTED_LOAD")
        }
        val anchor = snapshot.canonicalStrengthSignals[item.stableKey]
        val recentSessions = history.groupBy(PlanningSetRecord::date).toSortedMap().values.toList().takeLast(2)
        val provenTwice = recentSessions.size == 2 && recentSessions.all { rows ->
            rows.any { it.weightKg == last.weightKg && it.reps >= last.reps && (it.rpe ?: 10.0) <= 8.0 }
        }
        val progression = when {
            item.stableKey in snapshot.recoverySignals.tissueRestrictedStableKeys || snapshot.recoverySignals.readinessStatus == "LIMITED" ||
                (snapshot.recoverySignals.tissueStatus in setOf("VERY_HIGH", "BLOCKED") && snapshot.recoverySignals.tissueRestrictedStableKeys.isEmpty()) -> ProgressionDecision.REDUCE
            (anchor?.posteriorChangePercent ?: 0.0) < -2.0 -> ProgressionDecision.REVIEW
            !snapshot.explicitlyRestricted(item.stableKey) && provenTwice && (anchor?.posteriorChangePercent ?: 0.0) > 0.0 && strengthIntent in setOf(StrengthIntent.STRENGTH_PRIORITY, StrengthIntent.MIXED) -> ProgressionDecision.ADVANCE
            else -> ProgressionDecision.HOLD
        }
        val load = when (progression) {
            ProgressionDecision.ADVANCE -> round(last.weightKg * 1.025 * 2) / 2
            ProgressionDecision.REDUCE -> round(last.weightKg * .90 * 2) / 2
            else -> last.weightKg
        }
        val reps = last.reps.coerceAtLeast(1)
        val progressionLabel = progression.name
        fun straight(count: Int, targetReps: Int = reps, targetLoad: Double = load) = List(count) { ProgramSetPrescription(it + 1, targetReps, targetLoad, 0) }
        return when (style) {
            StrengthProgrammingStyle.STRAIGHT_5X5 -> PlannedPrescription("${load.clean()} kg × 5회 × ${item.targetSets.coerceAtLeast(1)}세트 · RPE 7–8.5 · $progressionLabel", straight(item.targetSets.coerceAtLeast(1), 5), 180, "CANONICAL_POSTERIOR_$progressionLabel")
            StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS -> PlannedPrescription("${load.clean()} kg × ${reps.coerceIn(3, 6)}회 × ${item.targetSets.coerceAtLeast(1)}세트 · RPE 7–8.5 · $progressionLabel", straight(item.targetSets.coerceAtLeast(1), reps.coerceIn(3, 6)), 180, "CANONICAL_POSTERIOR_$progressionLabel")
            StrengthProgrammingStyle.TOP_SET_BACKOFF -> {
                val backoff = round(load * .9 * 2) / 2
                val count = item.targetSets.coerceAtLeast(1)
                val sets = listOf(ProgramSetPrescription(1, reps.coerceIn(3, 6), load, 0)) + List((count - 1).coerceAtLeast(0)) { index -> ProgramSetPrescription(index + 2, (reps + 1).coerceAtLeast(6), backoff, 0) }
                PlannedPrescription("Top ${load.clean()} kg × ${sets[0].reps}회; Backoff ${backoff.clean()} kg × ${sets.drop(1).firstOrNull()?.reps ?: reps}회 × ${(count - 1).coerceAtLeast(0)} · RPE 7.5–8.5 · $progressionLabel", sets, 180, "CANONICAL_POSTERIOR_$progressionLabel")
            }
            StrengthProgrammingStyle.TOP_SET_HYPERTROPHY -> PlannedPrescription("${load.clean()} kg × ${reps.coerceIn(6, 15)}회 × ${item.targetSets.coerceAtLeast(1)}세트 · RPE 8–9 · $progressionLabel", straight(item.targetSets.coerceAtLeast(1), reps.coerceIn(6, 15)), 150, "CANONICAL_POSTERIOR_$progressionLabel")
            StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING, StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM -> {
                val factor = when (item.styleVariant) { "LIGHT" -> .80; "HEAVY" -> 1.0; else -> .90 }
                val target = round(load * factor * 2) / 2
                val count = item.targetSets.coerceAtLeast(1)
                if (style == StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING && item.styleVariant == "HEAVY" && count >= 4 && "within_session_ramping" in item.transition?.preservedFeatures.orEmpty() && "within_session_ramping" !in item.transition?.moderatedFeatures.orEmpty()) {
                    val ramp = listOf(.60 to 5, .75 to 5, .90 to 5, 1.0 to 3, .85 to 8).take(count).mapIndexed { index, (ratio, targetReps) -> ProgramSetPrescription(index + 1, targetReps, round(target * ratio * 2) / 2, 0) }
                    PlannedPrescription("HEAVY 램핑 ${count}세트 · $progressionLabel", ramp, 180, "INCUMBENT_STYLE_$progressionLabel")
                } else PlannedPrescription("${item.styleVariant} ${target.clean()} kg × ${if (item.styleVariant == "HEAVY") "3–5" else "5"}회 × ${count}세트 · $progressionLabel", straight(count, if (item.styleVariant == "HEAVY") minOf(reps, 4) else minOf(reps, 5), target), 180, "INCUMBENT_STYLE_$progressionLabel")
            }
            StrengthProgrammingStyle.DUP_LIKE_UNDULATING -> {
                val targetReps = when (item.styleVariant) { "VOLUME" -> 8; "STRENGTH" -> 5; else -> 6 }
                val safeReps = minOf(reps, targetReps)
                val factor = when (item.styleVariant) { "STRENGTH" -> 1.0; "MODERATE" -> .92; else -> .84 }
                val target = round(load * factor * 2) / 2
                PlannedPrescription("${item.styleVariant} ${safeReps}회 × ${item.targetSets.coerceAtLeast(1)}세트 · RPE 7–8.5 · $progressionLabel", straight(item.targetSets.coerceAtLeast(1), safeReps, target), 150, "INCUMBENT_STYLE_$progressionLabel")
            }
            else -> {
                val targetReps = reps
                val count = item.targetSets.takeIf { it > 0 } ?: history.countLastSession().coerceIn(2, 4)
                PlannedPrescription("${if (load > 0) "${load.clean()} kg × " else ""}$targetReps 회 × $count 세트 · RPE 7–8.5 · $progressionLabel", straight(count, targetReps), 120, "CANONICAL_POSTERIOR_$progressionLabel")
            }
        }
    }
}

class ProgramProjectionValidator {
    fun errors(skeleton: GeneratedProgramSkeleton, genericCourtLoad: Double = skeleton.personalizedDecision?.genericCourtLoad ?: 0.0): List<String> = buildList {
        if (skeleton.items.any { it.exerciseStableKey in setOf("ex_ae9ecdbc", "ex_badminton_lesson") }) add("일반 배드민턴 세션은 프로그램 항목으로 생성할 수 없습니다.")
        if ((1..skeleton.request.durationWeeks).any { week -> skeleton.items.none { it.weekNumber == week } }) add("선택된 계획 기간이 모두 생성되지 않았습니다.")
        if (skeleton.items.groupBy { it.weekNumber to it.dayOfWeek }.any { (_, rows) -> rows.sumOf { it.estimatedDurationSeconds } > skeleton.request.sessionMinutes * 60 }) add("예상 세션 시간이 사용 가능한 시간을 넘었습니다.")
        if (skeleton.request.weeklyTrainingDays !in 2..5) add("기록 기반 계획 빈도는 주 2~5일이어야 합니다.")
        if (skeleton.items.any { it.exerciseStableKey.isBlank() }) add("canonical stableKey가 없는 운동이 있습니다.")
    }
}

class ProgramRepairPolicy {
    fun repair(
        skeleton: GeneratedProgramSkeleton,
        errors: List<String>,
        retentionPriorityByLocalId: Map<String, Int> = emptyMap(),
        genericCourtLoad: Double = skeleton.personalizedDecision?.genericCourtLoad ?: 0.0
    ): GeneratedProgramSkeleton {
        if (errors.isEmpty()) return skeleton
        val secondsLimit = skeleton.request.sessionMinutes * 60
        val reduced = skeleton.items
            .groupBy { it.weekNumber to it.dayOfWeek }
            .values
            .flatMap { day ->
                var used = 0
                day.sortedWith(
                    compareByDescending<ProgramSkeletonItem> { retentionPriorityByLocalId[it.localId] ?: 0 }
                        .thenBy(ProgramSkeletonItem::orderIndex)
                        .thenBy(ProgramSkeletonItem::localId)
                ).filter { item ->
                    val fits = used + item.estimatedDurationSeconds <= secondsLimit
                    if (fits) used += item.estimatedDurationSeconds
                    fits
                }.sortedBy(ProgramSkeletonItem::orderIndex)
            }
        return skeleton.copy(items = reduced)
    }
}

class PersonalizedProgramBuilder(
    private val continuityPlanner: ExerciseContinuityPlanner = ExerciseContinuityPlanner(),
    private val prescriptionPlanner: PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner(),
    private val validator: ProgramProjectionValidator = ProgramProjectionValidator(),
    private val repairPolicy: ProgramRepairPolicy = ProgramRepairPolicy()
) {
    fun build(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, gaps: List<AdaptationGap>, intent: BlockIntent, horizon: Int, request: ProgramSkeletonRequest, answers: PersonalizedPlanningAnswers, priorDecisionId: String?): GeneratedProgramSkeleton {
        val transitionPlanner = AdaptationTransitionPlanner()
        val transitions = state.anchors.associate { anchor -> anchor.stableKey to transitionPlanner.decide(anchor, state, gaps) }
        val recentResistance = snapshot.allConfirmedSets.filter {
            !it.date.isBefore(snapshot.cutoff.minusDays(55)) && snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE
        }
        val weeklyResistance = recentResistance.groupBy {
            it.date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) to it.date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        }.values.map(List<*>::size)
        val baselineResistance = weeklyResistance.average().takeIf { it.isFinite() } ?: state.anchors.sumOf { anchor ->
            anchor.sets.toDouble() / (state.styleFeaturesByAnchor[anchor.stableKey]?.weeksObserved ?: 1).coerceAtLeast(1)
        }
        val systemicDoseFactor = state.trainingStateAssessment?.globalDoseFactor ?: 1.0
        val normalWeeks = state.trainingStateAssessment?.weeklyContext.orEmpty().filter { it.context == WeeklyTrainingContext.NORMAL }
        val normalResistance = trainingMedian(normalWeeks.map { week -> snapshot.allConfirmedSets.count {
            it.date in week.start..week.end && snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE }.toDouble() })
        val continuityReference = if (state.trainingStateAssessment?.permitsSustainableRelease == true)
            maxOf(baselineResistance, normalResistance ?: baselineResistance) else baselineResistance
        val resistanceContinuityDemand = continuityReference.roundToInt().coerceAtLeast(if (state.anchors.isEmpty()) 0 else 1)
        val demand = MaterialDemandResolver().resolve(snapshot, state, gaps, request)
        val materialKeys = demand.candidates.filter(PlannedExercise::material).mapTo(mutableSetOf(), PlannedExercise::stableKey)
        val performanceContinuity = snapshot.allConfirmedSets.filter {
            !it.date.isBefore(snapshot.cutoff.minusDays(27)) && !it.date.isAfter(snapshot.cutoff) &&
                snapshot.activityKind(it.stableKey) in PERFORMANCE_ACTIVITY_KINDS &&
                it.stableKey !in materialKeys && it.stableKey !in request.excludedExerciseStableKeys &&
                !snapshot.explicitlyRestricted(it.stableKey) &&
                (state.badmintonIntent == BadmintonPlanningIntent.ENABLED ||
                    snapshot.activityKind(it.stableKey) != PlannedActivityKind.STRUCTURED_BADMINTON_DRILL)
        }.groupBy(PlanningSetRecord::stableKey).filterValues { rows -> rows.map(PlanningSetRecord::date).distinct().size >= 2 }
            .mapNotNull { (key, rows) ->
                PerformancePrescriptionResolver.resolve(snapshot, key) ?: return@mapNotNull null
                if (key in state.recoverySignals.tissueRestrictedStableKeys) return@mapNotNull null
                val weeks = rows.map { it.date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) }.distinct().size.coerceAtLeast(1)
                val count = (rows.size.toDouble() / weeks).roundToInt().coerceAtLeast(1)
                PlannedExercise(key, "PERFORMANCE_CONTINUITY", "최근 반복한 경기력 훈련의 완료 처방을 유지했습니다.", 85,
                    targetSets = count, material = false)
            }
        val continuityDemand = resistanceContinuityDemand + performanceContinuity.sumOf(PlannedExercise::targetSets)
        val materialCandidates = demand.candidates.filter(PlannedExercise::material).sortedWith(
            compareByDescending<PlannedExercise> { it.priority }.thenBy { it.stableKey })
        val optionalCandidates = demand.candidates.filterNot(PlannedExercise::material).take(1)
        val lead = transitions.values.maxByOrNull(AnchorTransition::rotationPressure)
        val share = when (lead?.structureTreatment) {
            StructureTreatment.PRESERVE -> 0.0
            StructureTreatment.PRESERVE_CORE_REBALANCE -> minOf(.35, .16 + .18 * lead.rotationPressure)
            StructureTreatment.PARTIAL_CONTINUITY -> minOf(.48, .28 + .20 * lead.rotationPressure)
            StructureTreatment.ROTATE_EMPHASIS -> minOf(.62, .42 + .20 * lead.rotationPressure)
            null -> 0.0
        }
        val materialRequested = materialCandidates.sumOf(PlannedExercise::targetSets)
        val envelope = ExecutionCapacityPlanner().envelope(snapshot, state, request, baselineResistance,
            continuityDemand + materialRequested, systemicDoseFactor)
        val coreReserve = if (state.anchors.isEmpty()) 0 else minOf(continuityDemand, state.anchors.size).coerceAtLeast(1)
        val capacityExpanded = baselineResistance < 4.0 && materialCandidates.any { it.priority >= 100 } && systemicDoseFactor >= .92
        val capacity = if (envelope.historicalSessionObservationCount < 4)
            minOf(envelope.finalControllableUnits, if (capacityExpanded) maxOf(continuityDemand, coreReserve + (materialCandidates.firstOrNull()?.targetSets ?: 0)) else continuityDemand)
            else envelope.finalControllableUnits
        val finite = FiniteExecutionAllocator.allocate(capacity, continuityDemand, materialCandidates.map(PlannedExercise::targetSets), share, coreReserve,
            materialCandidates.indices.filterTo(mutableSetOf()) { snapshot.activityKind(materialCandidates[it].stableKey) == PlannedActivityKind.RESISTANCE })
        val anchorWeights = state.anchors.associate { anchor ->
            val weeks = (state.styleFeaturesByAnchor[anchor.stableKey]?.weeksObserved ?: 1).coerceAtLeast(1)
            val transition = transitions.getValue(anchor.stableKey)
            anchor.stableKey to maxOf(.20, anchor.sets.toDouble() / weeks) * maxOf(.25, transition.continuityScore) * transition.localDoseFactor
        }
        val incumbentWeights = anchorWeights + performanceContinuity.associate { it.stableKey to it.targetSets.toDouble() }
        val incumbentAllocations = proportionalAllocation(incumbentWeights.entries.sortedByDescending { it.value }
            .take(finite.continuity).associate { it.toPair() }, finite.continuity)
        val allocations = incumbentAllocations.filterKeys { it in anchorWeights }
        val days = request.weeklyTrainingDays.coerceIn(2, 5)
        val continuity = continuityPlanner.select(state, transitions, allocations, days) +
            performanceContinuity.mapNotNull { item -> incumbentAllocations[item.stableKey]?.let { item.copy(targetSets = it) } }
        val gapItems = materialCandidates.mapIndexedNotNull { index, item ->
            finite.material[index].takeIf { it > 0 }?.let { item.copy(targetSets = it) }
        }
        val spare = capacity - finite.continuity - finite.material.sum()
        val optional = optionalCandidates.filter { it.targetSets <= spare }
        val selected = continuity + gapItems + optional
        require(selected.isNotEmpty()) { "NO_EXECUTABLE_PLANNING_DEMAND" }
        val placement = TimedExecutionAllocationPlanner(prescriptionPlanner).allocate(
            snapshot, state, continuity, gapItems, optional, days, request.sessionMinutes)
        val timed = placement.days.values.flatten()
        val logical = placement.days
        val placementDeferred = placement.deferred
        val performanceItems = selected.filter { snapshot.activityKind(it.stableKey) in PERFORMANCE_ACTIVITY_KINDS }
        val targetResistance = selected.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE }.sumOf(PlannedExercise::targetSets)
        val schedule = ProgramDaySelector.defaultSchedule(horizon, days)
        val retentionPriorities = mutableMapOf<String, Int>()
        val items = buildList {
            (1..horizon).forEach { week ->
                val weekDays = schedule.getValue(week).sorted()
                logical.forEach { (logicalDay, rows) ->
                    rows.forEachIndexed { index, timedItem ->
                        val item = timedItem.item
                        val exercise = snapshot.exercises.getValue(item.stableKey)
                        val meta = snapshot.metadata[item.stableKey]
                        val rx = timedItem.prescription
                        val scalar = rx.sets.first()
                        val estimatedSeconds = timedItem.estimatedSeconds
                        val localId = "personalized_${week}_${logicalDay}_${index}_${item.stableKey}"
                        retentionPriorities[localId] = item.priority
                        add(ProgramSkeletonItem(
                            localId = localId, weekNumber = week, dayOfWeek = weekDays[logicalDay - 1], orderIndex = index + 1,
                            exerciseStableKey = item.stableKey, exerciseName = exercise.name, category = exercise.category, restSeconds = rx.restSeconds, prescription = rx.text,
                            setCount = rx.sets.size, reps = scalar.reps, weightKg = scalar.weightKg, seconds = scalar.seconds, selectionReason = item.reason, weightSource = rx.weightSource,
                            trainingSlot = item.role, stableKey = item.stableKey, selectionRole = item.role, movementFamily = meta?.movementFamily.orEmpty(), movementSubtype = meta?.movementSubtype.orEmpty(),
                            metadataProgramSlot = meta?.programSlot.orEmpty(), redundancyGroup = meta?.redundancyGroup.orEmpty(), strengthProgressionGroup = meta?.strengthProgressionGroup.orEmpty(),
                            primaryStressProfile = meta?.primaryStressProfile.orEmpty(), stressMagnitudeHint = meta?.stressMagnitudeHint.orEmpty(), neuromuscularStressLevel = meta?.neuromuscularStressLevel.orEmpty(),
                            systemicMuscularStressLevel = meta?.systemicMuscularStressLevel.orEmpty(), localMuscularStressLevel = meta?.localMuscularStressLevel.orEmpty(), jointTendonImpactStressLevel = meta?.jointTendonImpactStressLevel.orEmpty(),
                            movementFocusDemandLevel = meta?.movementFocusDemandLevel.orEmpty(), recoveryDurationClass = meta?.recoveryDurationClass.orEmpty(), badmintonTransferLevel = meta?.badmintonTransferLevel.orEmpty(), estimatedDurationSeconds = estimatedSeconds, setPrescriptions = rx.sets
                        ))
                    }
                }
            }
        }
        val rawSkeleton = GeneratedProgramSkeleton(
            suggestedName = request.name, durationDays = horizon * 7, request = request, periodizationType = request.periodizationType,
            weekPlans = (1..horizon).map { ProgramWeekPlan(it, "PERSONALIZED_REVIEW", 1.0, 1.0, 2, 8.0, 2, if (state.badmintonIntent == BadmintonPlanningIntent.ENABLED) 1 else 0, false, 6.0, 8.5) },
            items = items, weekDaySchedule = schedule, warnings = intent.constraints, optimizationSummary = ProgramOptimizationSummary(), templateId = "RECORD_BASED_PERSONALIZED_V0120", representativeTemplate = false,
            personalizedDecision = null
        )
        val repaired = repairPolicy.repair(rawSkeleton, validator.errors(rawSkeleton, state.genericCourtLoad), retentionPriorities, state.genericCourtLoad)
        val remaining = validator.errors(repaired, state.genericCourtLoad)
        require(remaining.isEmpty()) { remaining.joinToString(" ") }
        val firstWeek = repaired.items.filter { it.weekNumber == 1 }
        val pressureGapCodes = gaps.filter(AdaptationGap::contributesTransitionPressure).mapTo(mutableSetOf(), AdaptationGap::code)
        val materializedGaps = gapItems.filter { candidate -> firstWeek.any { it.exerciseStableKey == candidate.stableKey } }
        val plannedResistanceSets = firstWeek.filter { snapshot.activityKind(it.exerciseStableKey) == PlannedActivityKind.RESISTANCE }.sumOf(ProgramSkeletonItem::setCount)
        val plannedDrillBouts = firstWeek.filter { snapshot.activityKind(it.exerciseStableKey) == PlannedActivityKind.STRUCTURED_BADMINTON_DRILL }.sumOf(ProgramSkeletonItem::setCount)
        val plannedAthleticBouts = firstWeek.filter { snapshot.activityKind(it.exerciseStableKey) == PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL }.sumOf(ProgramSkeletonItem::setCount)
        val budget = PlanningBudget(
            baselineResistanceSets = baselineResistance,
            targetResistanceSets = targetResistance,
            plannedResistanceSets = plannedResistanceSets,
            targetStructuredBadmintonBouts = performanceItems.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.STRUCTURED_BADMINTON_DRILL }.sumOf(PlannedExercise::targetSets),
            plannedStructuredBadmintonBouts = plannedDrillBouts,
            systemicDoseFactor = systemicDoseFactor,
            targetAthleticPerformanceBouts = performanceItems.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL }.sumOf(PlannedExercise::targetSets),
            plannedAthleticPerformanceBouts = plannedAthleticBouts,
            execution = ExecutionAllocationTrace(
                capacity = envelope.copy(finalControllableUnits = plannedResistanceSets + plannedDrillBouts + plannedAthleticBouts),
                continuityRequestedUnits = continuityDemand,
                continuityAllocatedUnits = firstWeek.filter { row -> continuity.any { it.stableKey == row.exerciseStableKey } }.sumOf(ProgramSkeletonItem::setCount),
                materialGapRequestedUnits = materialRequested,
                materialGapAllocatedUnits = firstWeek.filter { row -> gapItems.any { it.stableKey == row.exerciseStableKey } }.sumOf(ProgramSkeletonItem::setCount),
                selectedMaterialGaps = gapItems.flatMap { it.representedGapCodes }.distinct().filter { it in pressureGapCodes },
                representedMaterialGaps = materializedGaps.flatMap { it.representedGapCodes - it.supportiveGapCodes() }.distinct().filter { it in pressureGapCodes },
                deferredMaterialGaps = (demand.deferred + materialCandidates.filterIndexed { index, _ -> index in finite.deferred }
                    .flatMap { it.representedGapCodes }.associateWith { "FINITE_CAPACITY" } +
                    placementDeferred.flatMap { it.item.representedGapCodes }.associateWith { "SESSION_TIME_OR_STYLE_SPACING" } +
                    materializedGaps.flatMap { it.supportiveGapCodes() }.associateWith { "SUPPORTIVE_ONLY_DIRECT_EXPOSURE_NOT_REPLACED" })
                    .filterKeys { code -> code in pressureGapCodes && materializedGaps.none {
                        code in (it.representedGapCodes - it.supportiveGapCodes()) } },
                optionalDevelopmentalItems = optional.filter { candidate -> firstWeek.any { it.exerciseStableKey == candidate.stableKey } }.map(PlannedExercise::stableKey),
                candidateAudit = demand.audit + materialCandidates.filterIndexed { index, _ -> index in finite.deferred }
                    .associate { it.stableKey to "FINITE_CAPACITY" } + selected.associate { it.stableKey to if (firstWeek.any { row -> row.exerciseStableKey == it.stableKey }) {
                    if (it.supportiveGapCodes().isNotEmpty()) "MATERIALIZED_SUPPORTIVE_GAP"
                    else if (it in gapItems) "MATERIALIZED_GAP" else "MATERIALIZED_CONTINUITY"
                } else "SESSION_TIME_OR_STYLE_SPACING" },
                representedGapCodesByStableKey = materializedGaps.associate { it.stableKey to (it.representedGapCodes - it.supportiveGapCodes()) },
                prescriptionSources = timed.associate { it.item.stableKey to it.prescription.weightSource },
                supportiveGapCodesByStableKey = materializedGaps.filter { it.supportiveGapCodes().isNotEmpty() }
                    .associate { it.stableKey to it.supportiveGapCodes() },
                scheduleTiers = timed.associate { it.item.stableKey to it.item.scheduleTier() }
            )
        )
        val fingerprint = personalizedProgramFingerprint(repaired.request, repaired.items)
        val decision = PersonalizedPlanningDecision(
            decisionId = UUID.randomUUID().toString(), protocolVersion = PERSONALIZED_PLANNER_PROTOCOL, generatedAtEpochMillis = System.currentTimeMillis(), historyCutoff = snapshot.cutoff.toString(), historyWindowDays = minOf(56, state.historyDays),
            planningHorizonWeeks = horizon, adaptationIntentMinWeeks = intent.adaptationMinWeeks, adaptationIntentMaxWeeks = intent.adaptationMaxWeeks, observedTrainingBehavior = state.observedBehavior.name,
            strengthIntent = state.strengthIntent.name, strengthIntentProvenance = if (snapshot.preferences.strengthIntent != null || QUESTION_STRENGTH_INTENT in answers.values) "EXPLICIT_USER" else "INFERRED_OR_UNRESOLVED",
            badmintonIntent = state.badmintonIntent.name, badmintonIntentProvenance = if (snapshot.preferences.badmintonIntent != null || QUESTION_BADMINTON_INTENT in answers.values) "EXPLICIT_USER" else "PROFILE_OR_UNRESOLVED",
            primaryAdaptation = intent.primary, secondaryTargets = gaps.map(AdaptationGap::code), strengthStyle = state.observedStrengthStyle.name, strengthStyleProvenance = "OBSERVED_HISTORY_ONLY", weeklyFrequency = days,
            confidence = state.confidence.name, reasonCodes = intent.reasonCodes + listOf("RESOLVED_WEEKLY_DAYS_${days}", "WEEKLY_COURT_LOAD_NORMALIZED", "CROSS_DOMAIN_FINITE_EXECUTION_ALLOCATION") + if (capacityExpanded) listOf("MINIMAL_CAPACITY_EXPANSION") else emptyList(), reasons = intent.reasons, constraints = intent.constraints, metadataAuthorityVersion = PERSONALIZED_AUTHORITY_VERSION, priorDecisionId = priorDecisionId, userAnswers = answers.values,
            originalGenerationFingerprint = fingerprint, recoverySignalCodes = state.recoverySignals.sourceCodes.sorted(), genericCourtLoad = state.genericCourtLoad, objectiveExposure = state.objectiveExposure,
            anchorTransitions = transitions.values.sortedBy(AnchorTransition::stableKey), planningBudget = budget,
            movementRepresentations = state.movementRepresentations,
            badmintonObjectiveRepresentations = state.badmintonObjectiveRepresentations,
            adaptationGaps = gaps,
            trainingStateAssessment = state.trainingStateAssessment,
            weeklyFrequencyEvidence = WeeklyDosePlanner().resolve(state,state.anchors.size+gaps.size)
        )
        return repaired.copy(personalizedDecision = decision)
    }


    private fun proportionalAllocation(weights: Map<String, Double>, total: Int, minimum: Int = 1): Map<String, Int> {
        if (weights.isEmpty() || total <= 0) return emptyMap()
        val floorTotal = minimum * weights.size
        val effectiveTotal = maxOf(total, floorTotal)
        val weightTotal = weights.values.sum().coerceAtLeast(.0001)
        val raw = weights.mapValues { minimum + (effectiveTotal - floorTotal) * it.value / weightTotal }
        val allocated = raw.mapValuesTo(mutableMapOf()) { maxOf(minimum, it.value.toInt()) }
        while (allocated.values.sum() < effectiveTotal) {
            val key = weights.keys.maxWith(compareBy<String> { raw.getValue(it) - allocated.getValue(it) }.thenBy { it })
            allocated[key] = allocated.getValue(key) + 1
        }
        while (allocated.values.sum() > effectiveTotal) {
            val candidates = allocated.filterValues { it > minimum }
            if (candidates.isEmpty()) break
            val key = candidates.keys.maxWith(compareBy<String> { allocated.getValue(it) - raw.getValue(it) }.thenBy { it })
            allocated[key] = allocated.getValue(key) - 1
        }
        return allocated
    }
}

internal fun personalizedProgramFingerprint(request: ProgramSkeletonRequest, items: List<ProgramSkeletonItem>): String {
    val source = buildString {
        append(listOf(request.name, request.goal.name, request.durationWeeks, request.weeklyTrainingDays, request.sessionMinutes).joinToString("|"))
        items.sortedWith(compareBy(ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek, ProgramSkeletonItem::orderIndex, ProgramSkeletonItem::exerciseStableKey)).forEach { item ->
            append('\n').append(listOf(item.weekNumber, item.dayOfWeek, item.orderIndex, item.exerciseStableKey, item.restSeconds, item.prescription, item.setPrescriptions.joinToString { "${it.reps}:${it.weightKg}:${it.seconds}" }).joinToString("|"))
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
}

private fun List<PlanningSetRecord>.countLastSession(): Int {
    val date = maxOf(PlanningSetRecord::date)
    return count { it.date == date }.coerceIn(1, 5)
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

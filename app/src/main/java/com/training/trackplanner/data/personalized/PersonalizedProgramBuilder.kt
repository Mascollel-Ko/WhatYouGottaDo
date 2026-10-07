package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramOptimizationSummary
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
    val material: Boolean = true,
    /** Exact C24 task B6 grant; null for every non-task or deferred task owner. */
    val taskProtocolAuthorization: TaskProtocolB6Authorization? = null,
    /** One-based protocol exposure identity within a week; set only on a scheduled occurrence. */
    val taskProtocolExposureIndex: Int? = null
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
    fun select(
        snapshot: PlanningHistorySnapshot,
        state: AthletePlanningState,
        gaps: List<AdaptationGap>,
        used: Set<String>,
        allAlternatives: Boolean = false,
        includePrescriptionDemand: Boolean = true
    ): List<PlannedExercise> {
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
                        ?.let { key -> PlannedExercise(key, "FOUNDATIONAL_${coverage.name}", gap.reason, 90,
                            targetSets = if (includePrescriptionDemand) 2 else 0, representedGapCodes = setOf(gap.code)) }
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
                val targetSets = if (!includePrescriptionDemand) 0 else if (snapshot.activityKind(key) in PERFORMANCE_ACTIVITY_KINDS)
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

internal fun PlanningHistorySnapshot.reviewedBadmintonCategory(stableKey: String): ReviewedBadmintonCategory? =
    RecordBasedReviewedPolicy.badmintonKeys.entries.firstOrNull { (_, keys) -> stableKey in keys }?.key

private fun PlanningHistorySnapshot.hasSafePerformancePrescription(stableKey: String): Boolean =
    PerformancePrescriptionResolver.resolve(this, stableKey) != null


data class PlannedPrescription(
    val text: String,
    val sets: List<ProgramSetPrescription>,
    val restSeconds: Int,
    val weightSource: String
)

class PersonalizedPrescriptionPlanner private constructor(private val computationMemo: PlanningComputationMemo?) {
    constructor() : this(null)
    internal fun scopedTo(memo: PlanningComputationMemo): PersonalizedPrescriptionPlanner =
        if (computationMemo === memo) this else withMemo(memo)

    private companion object {
        fun withMemo(memo: PlanningComputationMemo): PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner(memo)
    }

    fun prescribe(snapshot: PlanningHistorySnapshot, item: PlannedExercise, style: StrengthProgrammingStyle, week: Int): PlannedPrescription =
        prescribe(snapshot, StrengthIntent.MIXED, item, style)

    fun prescribe(snapshot: PlanningHistorySnapshot, strengthIntent: StrengthIntent, item: PlannedExercise, style: StrengthProgrammingStyle): PlannedPrescription =
        computationMemo?.prescription(snapshot, strengthIntent, item, style) {
            prescribeUncached(snapshot, strengthIntent, item, style)
        } ?: prescribeUncached(snapshot, strengthIntent, item, style)

    private fun prescribeUncached(snapshot: PlanningHistorySnapshot, strengthIntent: StrengthIntent, item: PlannedExercise, style: StrengthProgrammingStyle): PlannedPrescription {
        item.taskProtocolAuthorization?.let { authorization ->
            val definition = authorization.definition
            val shape = definition.shape
            require(item.stableKey == definition.stableKey && item.role == definition.selectionRole)
            require(authorization.materializationActivityKind == snapshot.activityKind(item.stableKey))
            require(shape.loadMode == TaskPrescriptionLoadMode.NO_EXTERNAL_LOAD)
            val secondsForCapacity = when (shape.mode) {
                TaskPrescriptionMode.DURATION_SECONDS -> requireNotNull(shape.seconds)
                TaskPrescriptionMode.DURATION_RANGE_SECONDS -> requireNotNull(shape.maxSeconds)
                else -> 0
            }
            val reps = shape.reps ?: 0
            val sets = List(shape.setCount) { index -> ProgramSetPrescription(
                setIndex = index + 1,
                reps = reps,
                weightKg = 0.0,
                seconds = secondsForCapacity,
                loadState = com.training.trackplanner.data.ProgramLoadState.NOT_APPLICABLE
            ) }
            return PlannedPrescription(shape.format(), sets, shape.restSeconds, definition.provenance.name)
        }
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
    internal fun repairWithProvenance(
        skeleton: GeneratedProgramSkeleton,
        errors: List<String>,
        retentionPriorityByLocalId: Map<String, Int> = emptyMap(),
        genericCourtLoad: Double = skeleton.personalizedDecision?.genericCourtLoad ?: 0.0,
        authorizedDemandIdsByLocalId: Map<String, String> = emptyMap()
    ): ProgramRepairResult {
        if (errors.isEmpty()) return ProgramRepairResult(skeleton, emptyList())
        val secondsLimit = skeleton.request.sessionMinutes * 60
        val removed = mutableListOf<OwnerAllocationProvenance>()
        val reduced = skeleton.items.groupBy { it.weekNumber to it.dayOfWeek }.values.flatMap { day ->
            var used = 0
            day.sortedWith(compareByDescending<ProgramSkeletonItem> { retentionPriorityByLocalId[it.localId] ?: 0 }
                .thenBy(ProgramSkeletonItem::orderIndex).thenBy(ProgramSkeletonItem::localId))
                .filter { item ->
                    val fits = used + item.estimatedDurationSeconds <= secondsLimit
                    if (fits) used += item.estimatedDurationSeconds else removed += OwnerAllocationProvenance(
                        StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole),
                        OwnerAllocationStage.PROGRAM_REPAIR, OwnerAllocationAction.REMOVED,
                        ownerAllocationState(item), null, OwnerAllocationCause.SESSION_TIME_LIMIT,
                        authorizedDemandIds = authorizedDemandIdsByLocalId[item.localId]?.let(::setOf).orEmpty(),
                        evidenceCodes = listOf("PROGRAM_REPAIR_SESSION_TIME_FIT"))
                    fits
                }.sortedBy(ProgramSkeletonItem::orderIndex)
        }
        return ProgramRepairResult(skeleton.copy(items = reduced), removed.deterministicOwnerOrder())
    }

    fun repair(
        skeleton: GeneratedProgramSkeleton,
        errors: List<String>,
        retentionPriorityByLocalId: Map<String, Int> = emptyMap(),
        genericCourtLoad: Double = skeleton.personalizedDecision?.genericCourtLoad ?: 0.0
    ): GeneratedProgramSkeleton = repairWithProvenance(skeleton, errors, retentionPriorityByLocalId, genericCourtLoad).skeleton
}

internal data class ProgramRepairResult(val skeleton: GeneratedProgramSkeleton,
    val ownerAllocationProvenance: List<OwnerAllocationProvenance>)

class PersonalizedProgramBuilder(
    private val continuityPlanner: ExerciseContinuityPlanner = ExerciseContinuityPlanner(),
    private val prescriptionPlanner: PersonalizedPrescriptionPlanner = PersonalizedPrescriptionPlanner(),
    private val validator: ProgramProjectionValidator = ProgramProjectionValidator(),
    private val repairPolicy: ProgramRepairPolicy = ProgramRepairPolicy()
) {
    /** Last generation's observation-only counters, exposed for diagnostics/tests. */
    internal var lastPerformanceMetrics: Map<String, Int> = emptyMap()
        private set
    /** Scoped experimental authority; kept out of the legacy reflective buildCore seam. */
    private var activeExactPrescriptionAuthorizationProvider: ExactPrescriptionAuthorizationProvider? = null
    /** Optional typed failure translation used only by the production canonical branch. */
    private var activeCanonicalFailureEmitter: ((StimulusCanonicalEvaluationFailure) -> Nothing)? = null

    internal fun build(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, gaps: List<AdaptationGap>, intent: BlockIntent, horizon: Int, request: ProgramSkeletonRequest, answers: PersonalizedPlanningAnswers, priorDecisionId: String?, explicitWeeklyDays: Boolean = true,
        frequency: PlanningFrequencyProvenance = PlanningFrequencyProvenance(WeeklyDosePlanner().resolve(state, state.anchors.size + gaps.size),
            request.weeklyTrainingDays, if (explicitWeeklyDays) PlanningFrequencySource.EXPLICIT_USER else PlanningFrequencySource.AUTO),
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE,
        materialDemandOverride: MaterialDemand? = null,
        regionalTargetPlan: RegionalExperimentalTargetPlan? = null,
        exactPrescriptionAuthorizationProvider: ExactPrescriptionAuthorizationProvider? = null,
        canonicalB5PowerOwnerIdentities: Set<StimulusPrescriptionOwnerIdentity> = emptySet(),
        canonicalB5TaskOwnerIdentitiesWithoutExecutableB6: Set<StimulusPrescriptionOwnerIdentity> = emptySet(),
        canonicalFailureEmitter: ((StimulusCanonicalEvaluationFailure) -> Nothing)? = null): GeneratedProgramSkeleton =
        buildWithArtifacts(snapshot, state, gaps, intent, horizon, request, answers, priorDecisionId, explicitWeeklyDays,
            frequency, progress, materialDemandOverride, regionalTargetPlan, exactPrescriptionAuthorizationProvider,
            canonicalB5PowerOwnerIdentities,
            canonicalB5TaskOwnerIdentitiesWithoutExecutableB6,
            canonicalFailureEmitter).program

    /**
     * Builds the final CONTROL skeleton and the B5 seed from the same completed owner state.
     * The seed is captured only after completion, rebalancing, frequency expansion and post-split
     * reflow have finished, so it never projects an intermediate/raw skeleton.
     */
    internal fun buildWithArtifacts(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, gaps: List<AdaptationGap>, intent: BlockIntent, horizon: Int, request: ProgramSkeletonRequest, answers: PersonalizedPlanningAnswers, priorDecisionId: String?, explicitWeeklyDays: Boolean = true,
        frequency: PlanningFrequencyProvenance = PlanningFrequencyProvenance(WeeklyDosePlanner().resolve(state, state.anchors.size + gaps.size),
            request.weeklyTrainingDays, if (explicitWeeklyDays) PlanningFrequencySource.EXPLICIT_USER else PlanningFrequencySource.AUTO),
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE,
        materialDemandOverride: MaterialDemand? = null,
        regionalTargetPlan: RegionalExperimentalTargetPlan? = null,
        exactPrescriptionAuthorizationProvider: ExactPrescriptionAuthorizationProvider? = null,
        canonicalB5PowerOwnerIdentities: Set<StimulusPrescriptionOwnerIdentity> = emptySet(),
        canonicalB5TaskOwnerIdentitiesWithoutExecutableB6: Set<StimulusPrescriptionOwnerIdentity> = emptySet(),
        canonicalFailureEmitter: ((StimulusCanonicalEvaluationFailure) -> Nothing)? = null): PersonalizedProgramBuildArtifacts {
        val performanceMetrics = PlannerPerformanceMetrics()
        val memo = PlanningComputationMemo(performanceMetrics)
        val memoSnapshot = memo.wrap(snapshot)
        val generationPrescriptions = prescriptionPlanner.scopedTo(memo)
        val previousExactAuthorization = activeExactPrescriptionAuthorizationProvider
        val previousCanonicalFailureEmitter = activeCanonicalFailureEmitter
        activeExactPrescriptionAuthorizationProvider = exactPrescriptionAuthorizationProvider
        activeCanonicalFailureEmitter = canonicalFailureEmitter
        val placed = try {
            buildBeforeReflow(memoSnapshot, state, gaps, intent, horizon, request, answers, priorDecisionId, explicitWeeklyDays,
                frequency, progress, generationPrescriptions, performanceMetrics, materialDemandOverride, regionalTargetPlan,
                exactPrescriptionAuthorizationProvider, canonicalB5PowerOwnerIdentities,
                canonicalB5TaskOwnerIdentitiesWithoutExecutableB6)
        } finally {
            activeExactPrescriptionAuthorizationProvider = previousExactAuthorization
            activeCanonicalFailureEmitter = previousCanonicalFailureEmitter
        }
        val reviewed = PostSplitWeeklyReflow().review(placed, memoSnapshot, state, progress,
            ReflowEvaluationCounts(performanceMetrics = performanceMetrics), canonicalFailureEmitter)
        val result = if (reviewed.trace.state == "NOT_APPLICABLE_NO_MANDATORY_SPLIT") placed
            else reviewed.skeleton.copy(personalizedDecision = reviewed.skeleton.personalizedDecision?.let { decision -> decision.copy(
                postSplitReflow = reviewed.trace,
                planningBudget = decision.planningBudget?.withOwnerAllocationProvenance(reviewed.trace.ownerAllocationProvenance,
                    exactPrescriptionAuthorizationProvider)
            ) })
        lastPerformanceMetrics = performanceMetrics.asMap()
        // Assert the immutable authority after every placement/restoration/reflow stage; never trim the result.
        regionalTargetPlan?.authorizedPrescriptionBySelectionRole?.forEach { (owner, prescription) ->
            result.items.groupBy { it.weekNumber }.forEach { (week, rows) ->
                val units = rows.filter { regionalSelectionIdentity(result, it) == owner }.sumOf { it.setPrescriptions.size }
                if (units > prescription.sets.size) {
                    canonicalFailureEmitter?.invoke(StimulusCanonicalEvaluationFailure(
                        StimulusCanonicalEvaluationFailureReason.REGIONAL_AUTHORIZATION_FAILURE,
                        "REGIONAL_AUTHORIZATION_OVERRUN"
                    ))
                    check(units <= prescription.sets.size) {
                        "REGIONAL_AUTHORIZATION_OVERRUN: $owner week=$week units=$units authorized=${prescription.sets.size}"
                    }
                }
            }
        }
        exactPrescriptionAuthorizationProvider?.authorizedOwners?.forEach { (owner, prescription) ->
            result.items.groupBy { it.weekNumber }.forEach { (week, rows) ->
                val units = rows.filter { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole }
                    .sumOf { it.setPrescriptions.size }
                if (units > prescription.sets.size) {
                    canonicalFailureEmitter?.invoke(StimulusCanonicalEvaluationFailure(
                        StimulusCanonicalEvaluationFailureReason.B6_AUTHORIZATION_FAILURE,
                        "B6_AUTHORIZATION_OVERRUN"
                    ))
                    check(units <= prescription.sets.size) {
                        "B6_AUTHORIZATION_OVERRUN: $owner week=$week units=$units authorized=${prescription.sets.size}"
                    }
                }
            }
        }
        val finalizedProgram = observeBoundedMaterialDemand(result)
        // `observeBoundedMaterialDemand` only updates decision diagnostics. Project both canonical
        // sibling inputs from the builder's finalized prescription rows, never from the program object.
        val finalizedPrescriptionRows = result.items.map { item ->
            StimulusFinalizedPrescriptionRow(
                StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole),
                PlannedPrescription(item.prescription, item.setPrescriptions, item.restSeconds, item.weightSource)
            )
        }
        val incumbentSeed = StimulusIncumbentIdentitySeed.fromFinalizedOwners(
            finalizedPrescriptionRows.map { row ->
                StimulusIncumbentIdentity(row.owner.stableKey, row.owner.selectionRole)
            }
        )
        val prescriptionBaseline = StimulusIncumbentPrescriptionBaseline.fromFinalizedRows(finalizedPrescriptionRows)
        return PersonalizedProgramBuildArtifacts(finalizedProgram, incumbentSeed, prescriptionBaseline)
    }

    private fun buildBeforeReflow(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, gaps: List<AdaptationGap>, intent: BlockIntent,
        horizon: Int, request: ProgramSkeletonRequest, answers: PersonalizedPlanningAnswers, priorDecisionId: String?, explicitWeeklyDays: Boolean,
        frequency: PlanningFrequencyProvenance, progress: PersonalizedPlannerProgressReporter,
        generationPrescriptions: PersonalizedPrescriptionPlanner,
        performanceMetrics: PlannerPerformanceMetrics,
        materialDemandOverride: MaterialDemand?,
        regionalTargetPlan: RegionalExperimentalTargetPlan?,
        exactPrescriptionAuthorizationProvider: ExactPrescriptionAuthorizationProvider?,
        canonicalB5PowerOwnerIdentities: Set<StimulusPrescriptionOwnerIdentity>,
        canonicalB5TaskOwnerIdentitiesWithoutExecutableB6: Set<StimulusPrescriptionOwnerIdentity>): GeneratedProgramSkeleton {
        if (!frequency.explicitIncrease) return buildCore(snapshot, state, gaps, intent, horizon, request, answers, priorDecisionId,
            explicitWeeklyDays, frequency, progress = progress, generationPrescriptions = generationPrescriptions,
            performanceMetrics = performanceMetrics, materialDemandOverride = materialDemandOverride,
            regionalTargetPlan = regionalTargetPlan, canonicalB5PowerOwnerIdentities = canonicalB5PowerOwnerIdentities,
            canonicalB5TaskOwnerIdentitiesWithoutExecutableB6 = canonicalB5TaskOwnerIdentitiesWithoutExecutableB6)
        // BASE is fully evaluated before expansion. Its nested work must not consume expansion's milestone range.
        val baseProgress = PersonalizedPlannerProgressReporter { stage ->
            progress.report(if (stage.percent > 50) PersonalizedPlannerStage.BASE_REVIEW else stage)
        }
        val base = buildCore(snapshot, state, gaps, intent, horizon, request.copy(weeklyTrainingDays = frequency.algorithmRecommendedDays),
            answers, priorDecisionId, true, frequency, progress = baseProgress, generationPrescriptions = generationPrescriptions,
            performanceMetrics = performanceMetrics, materialDemandOverride = materialDemandOverride,
            regionalTargetPlan = regionalTargetPlan, canonicalB5PowerOwnerIdentities = canonicalB5PowerOwnerIdentities,
            canonicalB5TaskOwnerIdentitiesWithoutExecutableB6 = canonicalB5TaskOwnerIdentitiesWithoutExecutableB6)
        progress.report(PersonalizedPlannerStage.EXPANSION)
        return FrequencyExpansionPlanner(generationPrescriptions, performanceMetrics).expand(snapshot, state, request, base, frequency,
            exactPrescriptionAuthorizationProvider,
            { authorized, capacity ->
            progress.report(PersonalizedPlannerStage.EXPANSION_RECHECK)
            var result: CompletionResult? = null
            buildCore(snapshot, state, gaps, intent, horizon, request, answers, priorDecisionId, true, frequency, authorized, capacity,
                progress = PersonalizedPlannerProgressReporter { progress.report(PersonalizedPlannerStage.EXPANSION_RECHECK) }, generationPrescriptions = generationPrescriptions,
                performanceMetrics = performanceMetrics, materialDemandOverride = materialDemandOverride,
                regionalTargetPlan = regionalTargetPlan, canonicalB5PowerOwnerIdentities = canonicalB5PowerOwnerIdentities,
                canonicalB5TaskOwnerIdentitiesWithoutExecutableB6 = canonicalB5TaskOwnerIdentitiesWithoutExecutableB6) {
                result = it
                it.skeleton
            }
            checkNotNull(result)
        })
    }

    private fun buildCore(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, gaps: List<AdaptationGap>, intent: BlockIntent,
        horizon: Int, request: ProgramSkeletonRequest, answers: PersonalizedPlanningAnswers, priorDecisionId: String?, explicitWeeklyDays: Boolean,
        frequency: PlanningFrequencyProvenance, authorizedOverride: List<AuthorizedSchedulingDemand>? = null,
        capacityOverride: WeeklyCapacityEnvelope? = null,
        generationPrescriptions: PersonalizedPrescriptionPlanner = prescriptionPlanner,
        progress: PersonalizedPlannerProgressReporter = PersonalizedPlannerProgressReporter.NONE,
        performanceMetrics: PlannerPerformanceMetrics = PlannerPerformanceMetrics(),
        materialDemandOverride: MaterialDemand? = null,
        regionalTargetPlan: RegionalExperimentalTargetPlan? = null,
        canonicalB5PowerOwnerIdentities: Set<StimulusPrescriptionOwnerIdentity> = emptySet(),
        canonicalB5TaskOwnerIdentitiesWithoutExecutableB6: Set<StimulusPrescriptionOwnerIdentity> = emptySet(),
        finish: ((CompletionResult) -> GeneratedProgramSkeleton)? = null): GeneratedProgramSkeleton {
        val exactPrescriptionAuthorizationProvider = activeExactPrescriptionAuthorizationProvider
        progress.report(PersonalizedPlannerStage.DEMAND)
        val transitionPlanner = AdaptationTransitionPlanner()
        val transitions = state.anchors.associate { anchor -> anchor.stableKey to transitionPlanner.decide(anchor, state, gaps) }
        val anchorFallbackResistance = state.anchors.sumOf { anchor ->
            anchor.sets.toDouble() / (state.styleFeaturesByAnchor[anchor.stableKey]?.weeksObserved ?: 1).coerceAtLeast(1)
        }
        val systemicDoseFactor = state.trainingStateAssessment?.globalDoseFactor ?: 1.0
        // Keep the established continuity demand as the scheduling input.  The
        // independent resistance budget below is the new volume authority and
        // audit trace; continuity still goes through the existing canonical
        // allocator so placement behavior remains stable.
        val recentResistance = snapshot.allConfirmedSets.filter {
            !it.date.isBefore(snapshot.cutoff.minusDays(55)) && snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE
        }
        val schedulingWeeklyResistance = recentResistance.groupBy {
            it.date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) to it.date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        }.values.map(List<*>::size)
        val schedulingBaselineResistance = schedulingWeeklyResistance.average().takeIf { it.isFinite() } ?: anchorFallbackResistance
        val normalWeeks = state.trainingStateAssessment?.weeklyContext.orEmpty().filter { it.context == WeeklyTrainingContext.NORMAL }
        val normalResistance = trainingMedian(normalWeeks.map { week -> snapshot.allConfirmedSets.count {
            it.date in week.start..week.end && snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE
        }.toDouble() })
        val schedulingContinuityReference = if (state.trainingStateAssessment?.permitsSustainableRelease == true)
            maxOf(schedulingBaselineResistance, normalResistance ?: schedulingBaselineResistance) else schedulingBaselineResistance
        val schedulingContinuityDemand = schedulingContinuityReference.roundToInt().coerceAtLeast(if (state.anchors.isEmpty()) 0 else 1)
        val baseDemand = MaterialDemandResolver().resolve(
            snapshot, state, gaps, request,
            // Preserve the pre-existing legacy comparator's candidate-unit shape. Canonical
            // EXP/region generation passes false and gets set demand only from exact B6.
            includeLegacyComparatorCandidateUnits = exactPrescriptionAuthorizationProvider == null && regionalTargetPlan == null
        )
        val ownedBaseDemand = regionalTargetPlan?.let {
            RegionalMaterialDemandOwnershipFilter.filter(baseDemand, snapshot, state, it.ownedKeys, generationPrescriptions)
        } ?: baseDemand
        val mergedDemand = when {
            regionalTargetPlan != null -> mergeTypedMaterialDemand(ownedBaseDemand, regionalTargetPlan.demand, regionalTargetPlan.authorizedPrescriptionBySelectionRole.keys)
            // Canonical B5 owns every selected target identity. Keep the builder's independent
            // non-target demand, but replace a same-exercise generic owner with B5's exact
            // owner role so the typed B6 authorization survives materialization.
            materialDemandOverride != null -> mergeCanonicalMaterialDemand(baseDemand, materialDemandOverride)
            else -> baseDemand
        }
        val taskAuthorityFilteredDemand = if (regionalTargetPlan == null && canonicalB5TaskOwnerIdentitiesWithoutExecutableB6.isNotEmpty()) {
            filterCanonicalB5TaskDemandWithoutExecutableB6(mergedDemand, canonicalB5TaskOwnerIdentitiesWithoutExecutableB6)
        } else mergedDemand
        val b5SelectedQualityOwners = exactPrescriptionAuthorizationProvider?.b5SelectedQualityOwners.orEmpty()
        val canonicalB5OwnersRequiringExactQualityB6 = b5SelectedQualityOwners + canonicalB5PowerOwnerIdentities
        val qualityAuthorityFilteredDemand = if (regionalTargetPlan == null && exactPrescriptionAuthorizationProvider != null && canonicalB5OwnersRequiringExactQualityB6.isNotEmpty()) {
            // Apply the same pre-allocation B6 boundary to every canonical B5 Quality owner,
            // while retaining C21's explicit Power identity source. Filtering only Power let
            // denied Strength/Hypertrophy candidates enter frequency expansion; replacing that
            // source instead of unioning it would reopen the Power fallback.
            filterCanonicalB5DemandWithoutExecutableB6(taskAuthorityFilteredDemand, canonicalB5OwnersRequiringExactQualityB6, exactPrescriptionAuthorizationProvider)
        } else taskAuthorityFilteredDemand
        // Material-demand is candidate-selection evidence, not permission to invent a dose.
        // Filter its newly proposed executable candidates before budgets/allocation so they
        // cannot be resurrected by frequency expansion, reflow, or residual completion.
        // Canonical EXP/region generation has an explicit execution-authority input. The
        // late CONTROL comparator intentionally has none and remains a separately built
        // rollback baseline; do not apply EXP authority filtering to that comparator build.
        val authorityReresolvedDemand = if (exactPrescriptionAuthorizationProvider != null || regionalTargetPlan != null) {
            reResolveMaterialDemandCandidatesWithExistingAuthority(
                qualityAuthorityFilteredDemand, exactPrescriptionAuthorizationProvider, regionalTargetPlan
            )
        } else qualityAuthorityFilteredDemand
        val demand = if (exactPrescriptionAuthorizationProvider != null || regionalTargetPlan != null) {
            filterMaterialDemandCandidatesWithoutExactExecutionAuthority(
                authorityReresolvedDemand, exactPrescriptionAuthorizationProvider, regionalTargetPlan
            )
        } else authorityReresolvedDemand
        val materialDemandOriginOwners = demand.candidateOrigins.flatMapTo(linkedSetOf()) { origin ->
            listOfNotNull(origin.owner, origin.authorityResolution?.finalOwner)
        }
        val retainedDemandOwners = demand.candidates.mapTo(linkedSetOf()) {
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.role)
        }
        val deniedMaterialDemandOwners = authorityReresolvedDemand.candidates.asSequence()
            .map { StimulusPrescriptionOwnerIdentity(it.stableKey, it.role) }
            .filter { it in materialDemandOriginOwners && it !in retainedDemandOwners }
            .toSet()
        val materialKeys = demand.candidates.filter(PlannedExercise::material).mapTo(mutableSetOf(), PlannedExercise::stableKey)
        val canonicalB5StableKeys = if (regionalTargetPlan == null) {
            materialDemandOverride?.candidates?.mapTo(linkedSetOf(), PlannedExercise::stableKey).orEmpty()
        } else emptySet()
        // A deferred Power target must not regain an executable-looking legacy row through the
        // separate performance-continuity path. Other B5 qualities retain their existing behavior.
        val canonicalB5PowerStableKeys = canonicalB5PowerOwnerIdentities.mapTo(linkedSetOf()) { it.stableKey }
        val provisionalResistance = ResistanceVolumePlanner.plan(snapshot, state, request, Int.MAX_VALUE, anchorFallbackResistance)
        val baselineResistance = schedulingBaselineResistance
        val resistanceContinuityDemand = schedulingContinuityDemand
        val performanceContinuity = snapshot.allConfirmedSets.filter {
            !it.date.isBefore(snapshot.cutoff.minusDays(27)) && !it.date.isAfter(snapshot.cutoff) &&
                snapshot.activityKind(it.stableKey) in PERFORMANCE_ACTIVITY_KINDS &&
                it.stableKey !in materialKeys && it.stableKey !in canonicalB5PowerStableKeys &&
                it.stableKey !in request.excludedExerciseStableKeys &&
                !snapshot.explicitlyRestricted(it.stableKey) &&
                (state.badmintonIntent == BadmintonPlanningIntent.ENABLED ||
                    snapshot.activityKind(it.stableKey) != PlannedActivityKind.STRUCTURED_BADMINTON_DRILL)
        }.groupBy(PlanningSetRecord::stableKey).filterValues { rows -> rows.map(PlanningSetRecord::date).distinct().size >= 2 }
            .mapNotNull { (key, rows) ->
                if (StimulusPrescriptionOwnerIdentity(key, "PERFORMANCE_CONTINUITY") in deniedMaterialDemandOwners) {
                    return@mapNotNull null
                }
                PerformancePrescriptionResolver.resolve(snapshot, key) ?: return@mapNotNull null
                if (key in state.recoverySignals.tissueRestrictedStableKeys) return@mapNotNull null
                val weeks = rows.map { it.date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) }.distinct().size.coerceAtLeast(1)
                val count = (rows.size.toDouble() / weeks).roundToInt().coerceAtLeast(1)
                PlannedExercise(key, "PERFORMANCE_CONTINUITY", "최근 반복한 경기력 훈련의 완료 처방을 유지했습니다.", 85,
                    targetSets = count, material = false)
            }
        val continuityDemand = resistanceContinuityDemand + performanceContinuity.sumOf(PlannedExercise::targetSets)
        val anchorWeights = state.anchors.associate { anchor ->
            val weeks = (state.styleFeaturesByAnchor[anchor.stableKey]?.weeksObserved ?: 1).coerceAtLeast(1)
            val transition = transitions.getValue(anchor.stableKey)
            anchor.stableKey to maxOf(.20, anchor.sets.toDouble() / weeks) * maxOf(.25, transition.continuityScore) * transition.localDoseFactor
        }
        val materialCandidates = demand.candidates.filter(PlannedExercise::material).sortedWith(
            compareByDescending<PlannedExercise> { snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE }
                .thenByDescending { it.priority }.thenBy { it.stableKey })
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
        val resistanceUsefulDemand = resistanceContinuityDemand +
            materialCandidates.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE }.sumOf(PlannedExercise::targetSets) +
            optionalCandidates.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE }.sumOf(PlannedExercise::targetSets)
        val rawResistanceBudget = ResistanceVolumePlanner.plan(snapshot, state, request, resistanceUsefulDemand, anchorFallbackResistance)
        val capacityExpanded = baselineResistance < 4.0 && materialCandidates.any { it.priority >= 100 } && systemicDoseFactor >= .92
        val resistanceBudget = rawResistanceBudget.copy(
            resistanceTargetSets = if (capacityExpanded)
                minOf(rawResistanceBudget.resistanceTimeCeiling, resistanceUsefulDemand)
            else rawResistanceBudget.resistanceTargetSets
        )
        val domains = DomainVolumeBudget(
            resistance = resistanceBudget,
            structuredBadminton = PerformanceVolumeBudget(
                targetBouts = performanceContinuity.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.STRUCTURED_BADMINTON_DRILL }.sumOf(PlannedExercise::targetSets) +
                    materialCandidates.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.STRUCTURED_BADMINTON_DRILL }.sumOf(PlannedExercise::targetSets),
                authorizedBouts = performanceContinuity.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.STRUCTURED_BADMINTON_DRILL }.sumOf(PlannedExercise::targetSets),
                finalBouts = 0
            ),
            athleticPerformance = PerformanceVolumeBudget(
                targetBouts = performanceContinuity.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL }.sumOf(PlannedExercise::targetSets) +
                    materialCandidates.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL }.sumOf(PlannedExercise::targetSets),
                authorizedBouts = performanceContinuity.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL }.sumOf(PlannedExercise::targetSets),
                finalBouts = 0
            )
        )
        val envelope = capacityOverride ?: ExecutionCapacityPlanner().envelope(snapshot, state, request, baselineResistance,
            continuityDemand + materialRequested, systemicDoseFactor, domains)
        // The canonical finite allocator reserves the established incumbent
        // continuity.  The independent resistance target is recorded as a
        // volume authorization and does not turn unused budget into filler.
        val coreReserve = if (state.anchors.isEmpty()) 0 else
            minOf(continuityDemand, state.anchors.size).coerceAtLeast(1)
        val capacity = if (canonicalB5StableKeys.isNotEmpty()) {
            // Keep the canonical planner's complete continuity allocation alongside B5/B6
            // demand when the measured session-time envelope allows it. B5 owners that replace
            // a continuity key are removed from that lane below, so their target prescription
            // occupies the same exercise demand instead of displacing unrelated owners.
            minOf(envelope.finalControllableUnits, continuityDemand + materialRequested)
        } else if (envelope.historicalSessionObservationCount < 4)
            minOf(envelope.finalControllableUnits,
                if (capacityExpanded) maxOf(continuityDemand, coreReserve + (materialCandidates.firstOrNull()?.targetSets ?: 0)) else continuityDemand)
            else envelope.finalControllableUnits
        val bounded = regionalTargetPlan?.let { BoundedMaterialDemandAllocation(snapshot, state, request, materialCandidates,
            it, generationPrescriptions, capacity, continuityDemand, coreReserve, activeCanonicalFailureEmitter) }
        val finite = bounded?.finite ?: FiniteExecutionAllocator.allocate(capacity, continuityDemand, materialCandidates.map(PlannedExercise::targetSets), share, coreReserve,
            materialCandidates.indices.filterTo(mutableSetOf()) {
                val item = materialCandidates[it]
                snapshot.activityKind(item.stableKey) == PlannedActivityKind.RESISTANCE &&
                    RegionalSelectionIdentity(item.stableKey, item.role) !in regionalTargetPlan?.authorizedPrescriptionBySelectionRole.orEmpty()
            }, materialCandidates.map { StimulusPrescriptionOwnerIdentity(it.stableKey, it.role) })
        // Keep the established continuity allocator as the final scheduling
        // authority.  Domain budgets authorize independently; placement still
        // receives the same finite, canonical continuity demand ordering.
        val incumbentWeights = anchorWeights + performanceContinuity.associate { it.stableKey to it.targetSets.toDouble() }
        val incumbentAllocations = proportionalAllocation(incumbentWeights.entries.sortedByDescending { it.value }
            .take(finite.continuity).associate { it.toPair() }, finite.continuity)
        val fullContinuityAllocations = proportionalAllocation(incumbentWeights.entries.sortedByDescending { it.value }
            .associate { it.toPair() }, continuityDemand)
        val finiteConstrainedOwners = buildSet {
            materialCandidates.forEachIndexed { index, item ->
                if (finite.material[index] < item.targetSets) add(item.stableKey)
            }
            fullContinuityAllocations.forEach { (key, requested) ->
                if ((incumbentAllocations[key] ?: 0) < requested) add(key)
            }
        }
        val allocations = incumbentAllocations.filterKeys { it in anchorWeights }
        val days = request.weeklyTrainingDays.coerceIn(2, 5)
        val placementContext = PlacementContext(snapshot, state, days, request.sessionMinutes)
        val excludedConflictingOwners = exactPrescriptionAuthorizationProvider?.ownerExecutionDispositions
            ?.filterValues { it == StimulusPrescriptionOwnerExecutionDisposition.EXCLUDE_CONFLICTING_ADDITION }
            ?.keys.orEmpty()
        fun isExecutableOwner(item: PlannedExercise): Boolean =
            StimulusPrescriptionOwnerIdentity(item.stableKey, item.role) !in excludedConflictingOwners
        val continuity = (authorizedOverride?.filter { it.continuity }?.map { it.item } ?: (continuityPlanner.select(state, transitions, allocations, days) +
            performanceContinuity.mapNotNull { item -> incumbentAllocations[item.stableKey]?.let { item.copy(targetSets = it) } }))
            .filterNot { it.stableKey in canonicalB5StableKeys }
            .filterNot { StimulusPrescriptionOwnerIdentity(it.stableKey, it.role) in deniedMaterialDemandOwners }
            .filter(::isExecutableOwner)
        val executableOwnerIdentities = materialCandidates.filter(::isExecutableOwner)
            .mapTo(linkedSetOf()) { StimulusPrescriptionOwnerIdentity(it.stableKey, it.role) }
        val finiteOwnerProvenance = if (authorizedOverride == null) finite.ownerAllocationProvenance
            .filter { it.owner in executableOwnerIdentities } else emptyList()
        var nextFiniteDemandIndex = continuity.size
        val gapItems = if (authorizedOverride != null) {
            authorizedOverride.filter { !it.continuity && it.item.material }.map { it.item }
        } else materialCandidates.mapIndexedNotNull { index, item ->
            val allocatedSets = finite.material[index]
            if (allocatedSets == 0) null else {
                val selectedItem = item.copy(targetSets = allocatedSets)
                if (!isExecutableOwner(selectedItem)) null else {
                    val authorizedDemandId = "authorized_${nextFiniteDemandIndex++}"
                    selectedItem
                }
            }
        }
        val finiteOwnerEventsByDemand = gapItems.mapIndexed { index, item ->
            StimulusPrescriptionOwnerIdentity(item.stableKey, item.role) to "authorized_${continuity.size + index}"
        }.toMap()
        val finiteOwnerEvents = finiteOwnerProvenance.map { event ->
            val authorities = exactPrescriptionAuthorizationProvider?.authorizedPrescriptions?.keys.orEmpty()
                .filter { it.owner == event.owner }
            event.copy(
                before = event.before?.copy(week = null),
                after = event.after?.copy(week = null),
                qualities = event.qualities + authorities.map { it.quality.name },
                authorizedDemandIds = finiteOwnerEventsByDemand[event.owner]?.let(::setOf).orEmpty()
            )
        }.flatMap { event ->
            (1..horizon).map { week -> event.copy(before = event.before?.copy(week = week), after = event.after?.copy(week = week)) }
        }
        val spare = capacity - finite.continuity - finite.material.sum()
        val optional = (authorizedOverride?.filter { !it.continuity && !it.item.material }?.map { it.item } ?: optionalCandidates.filter { it.targetSets <= spare })
            .filter(::isExecutableOwner)
        val selected = continuity + gapItems + optional
        // Capture original demand before finite capacity is allowed to erase it. No selection changes in this trace stage.
        val originalAllocations = proportionalAllocation(incumbentWeights.entries.sortedByDescending { it.value }
            .associate { it.toPair() }, continuityDemand)
        val originalContinuity = continuityPlanner.select(state, transitions, originalAllocations.filterKeys { it in anchorWeights }, days)
            .filterNot { it.stableKey in canonicalB5StableKeys } +
            performanceContinuity.map { it.copy(targetSets = originalAllocations[it.stableKey] ?: it.targetSets) }
        val candidates = bounded?.let { allocation -> allocation.candidates + capacityCandidateTrace(snapshot, state,
            originalContinuity.map { it to true } + optionalCandidates.map { it to false }, selected, generationPrescriptions)
            .map { it.copy(originalRank = it.originalRank + allocation.candidates.size) } } ?: capacityCandidateTrace(snapshot, state,
            materialCandidates.map { it to false } + originalContinuity.map { it to true } + optionalCandidates.map { it to false },
            selected, generationPrescriptions,
            authorizedPrescriptionFor = exactPrescriptionAuthorizationProvider?.let { provider -> { item ->
                when (val resolution = provider.resolveOwnerPrescription(item)) {
                    is ExactOwnerPrescriptionResolution.Authorized -> resolution.prescription
                    is ExactOwnerPrescriptionResolution.PreserveIncumbent -> resolution.prescription
                    ExactOwnerPrescriptionResolution.ExcludeConflictingAddition,
                    ExactOwnerPrescriptionResolution.NoExecutableAuthority,
                    ExactOwnerPrescriptionResolution.NoExactAuthority -> null
                }
            } } ?: regionalTargetPlan?.let { plan -> { item -> plan.authorizedPrescriptionFor(item) } },
            authorizedPrescriptionSource = if (exactPrescriptionAuthorizationProvider != null) {
                PrescriptionAuthoritySource.EXPERIMENTAL_MATERIAL_AUTHORIZED
            } else PrescriptionAuthoritySource.REGIONAL_TARGET_AUTHORIZED)
        val retained = retainedIncumbentSupply(snapshot, state, gaps, request, candidates, generationPrescriptions)
        if (selected.isEmpty() && excludedConflictingOwners.isEmpty() &&
            (exactPrescriptionAuthorizationProvider != null || regionalTargetPlan != null)) {
            val failure = StimulusCanonicalEvaluationFailure(
                reason = StimulusCanonicalEvaluationFailureReason.NO_EXECUTABLE_PLANNING_DEMAND,
                unresolvedMaterialDemandGaps = demand.unresolvedGapCodes,
                materialDemandAuthorityResolutions = demand.candidateOrigins.mapNotNull { it.authorityResolution }
            )
            activeCanonicalFailureEmitter?.invoke(failure)
            throw failure
        }
        progress.report(PersonalizedPlannerStage.PLACEMENT)
        val allocator = SplitAwareContinuityAllocation(generationPrescriptions, progress, placementContext, performanceMetrics)
        val regionalAuthorized = if (regionalTargetPlan != null && authorizedOverride == null) {
            selected.mapIndexed { index, item ->
                val prescription = if (index >= continuity.size && item.material) requireNotNull(bounded).prescriptionFor(item)
                    else regionalTargetPlan.authorizedPrescriptionFor(item)
                        ?: generationPrescriptions.prescribe(snapshot, state.strengthIntent, item, item.style)
                AuthorizedSchedulingDemand("authorized_$index", item, prescription, index < continuity.size)
            }
        } else null
        val exactAuthorized = if (exactPrescriptionAuthorizationProvider != null && authorizedOverride == null && regionalAuthorized == null) {
            selected.flatMapIndexed { index, item ->
                item.taskProtocolAuthorization?.let { taskAuthorization ->
                    val exposureIndices = item.taskProtocolExposureIndex?.let(::listOf) ?:
                        (1..(item.targetSets / taskAuthorization.definition.shape.setCount)
                            .coerceAtMost(taskAuthorization.definition.weeklyExposures)).toList()
                    return@flatMapIndexed exposureIndices.map { exposureIndex ->
                        val occurrence = item.copy(targetSets = taskAuthorization.definition.shape.setCount,
                            taskProtocolExposureIndex = exposureIndex)
                        AuthorizedSchedulingDemand(
                            "authorized_${index}_task_${exposureIndex}", occurrence,
                            generationPrescriptions.prescribe(snapshot, state.strengthIntent, occurrence, occurrence.style),
                            continuity = false
                        )
                    }
                }
                when (val resolution = exactPrescriptionAuthorizationProvider.resolveOwnerPrescription(item)) {
                    is ExactOwnerPrescriptionResolution.ExcludeConflictingAddition -> emptyList()
                    is ExactOwnerPrescriptionResolution.PreserveIncumbent ->
                        listOf(AuthorizedSchedulingDemand("authorized_$index", item, resolution.prescription, index < continuity.size))
                    is ExactOwnerPrescriptionResolution.Authorized ->
                        listOf(AuthorizedSchedulingDemand("authorized_$index", item, resolution.prescription, index < continuity.size))
                    ExactOwnerPrescriptionResolution.NoExecutableAuthority -> emptyList()
                    // Candidate selection and placement demand do not grant prescription
                    // authority. New rows originating in material demand were already removed
                    // before allocation and must never reach a generic executable fallback.
                    // Preserve the established non-material-demand continuity path, which is
                    // neither a new coverage row nor authority for a material-demand candidate.
                    ExactOwnerPrescriptionResolution.NoExactAuthority -> if (
                        StimulusPrescriptionOwnerIdentity(item.stableKey, item.role) in materialDemandOriginOwners
                    ) emptyList() else listOf(AuthorizedSchedulingDemand(
                        "authorized_$index", item,
                        generationPrescriptions.prescribe(snapshot, state.strengthIntent, item, item.style),
                        index < continuity.size
                    ))
                }
            }
        } else null
        if (exactAuthorized != null && exactAuthorized.isEmpty() && excludedConflictingOwners.isEmpty()) {
            val unresolvedFailure = StimulusCanonicalEvaluationFailure(
                reason = StimulusCanonicalEvaluationFailureReason.NO_EXECUTABLE_PLANNING_DEMAND,
                detailCode = "EXACT_PRESCRIPTION_AUTHORITY_REQUIRED",
                unresolvedMaterialDemandGaps = demand.unresolvedGapCodes,
                materialDemandAuthorityResolutions = demand.candidateOrigins.mapNotNull { it.authorityResolution }
            )
            activeCanonicalFailureEmitter?.invoke(unresolvedFailure)
            throw unresolvedFailure
        }
        val placement = when {
            authorizedOverride != null -> allocator.allocateAuthorized(snapshot, state, authorizedOverride, days, request.sessionMinutes, request)
            regionalAuthorized != null -> allocator.allocateAuthorized(snapshot, state, regionalAuthorized, days, request.sessionMinutes, request)
            exactAuthorized != null -> allocator.allocateAuthorized(snapshot, state, exactAuthorized, days, request.sessionMinutes, request)
            else -> allocator.allocate(snapshot, state, continuity, gapItems, optional, days, request.sessionMinutes, request)
        }
        progress.report(PersonalizedPlannerStage.FEASIBILITY)
        val timed = placement.days.values.flatten().map { it.timed }
        val logical = placement.days
        val placementDeferred = placement.deferred
        val performanceItems = selected.filter { snapshot.activityKind(it.stableKey) in PERFORMANCE_ACTIVITY_KINDS }
        val targetResistance = resistanceBudget.resistanceTargetSets
        val schedule = RecordBasedReviewedPolicy.defaultSchedule(horizon, days)
        val materialOwnerProvenance = demand.ownerAllocationProvenance.flatMap { event ->
            (1..horizon).map { week -> event.copy(before = event.before?.copy(week = week), after = event.after?.copy(week = week)) }
        }.deterministicOwnerOrder()
        val placementOwnerProvenance = expandLogicalPlacementProvenance(placement.trace.ownerAllocationProvenance, schedule,
            emittedWeeks = (1..horizon).toSet())
            .map { it.withExactAuthority(exactPrescriptionAuthorizationProvider) }
        val exactFiniteOwnerProvenance = finiteOwnerEvents.map { event ->
            val sourceItem = materialCandidates.singleOrNull {
                it.stableKey == event.owner.stableKey && it.role == event.owner.selectionRole
            }
            val allocatedDemand = event.authorizedDemandIds.singleOrNull()?.let { demandId ->
                placement.trace.authorized.singleOrNull { it.id == demandId }
            }
            fun exactState(template: OwnerAllocationState?, count: Int, item: PlannedExercise?, accepted: PlannedPrescription?): OwnerAllocationState? {
                if (template == null || item == null) return template
                val requested = item.copy(targetSets = count)
                val prescription = when {
                    requested.taskProtocolAuthorization != null ->
                        generationPrescriptions.prescribe(snapshot, state.strengthIntent, requested, requested.style)
                    exactPrescriptionAuthorizationProvider != null -> when (
                        val resolution = exactPrescriptionAuthorizationProvider.resolveOwnerPrescription(requested)
                    ) {
                        is ExactOwnerPrescriptionResolution.Authorized -> resolution.prescription
                        is ExactOwnerPrescriptionResolution.PreserveIncumbent -> resolution.prescription
                        ExactOwnerPrescriptionResolution.ExcludeConflictingAddition,
                        ExactOwnerPrescriptionResolution.NoExecutableAuthority,
                        ExactOwnerPrescriptionResolution.NoExactAuthority -> null
                    }
                    regionalTargetPlan != null -> regionalTargetPlan.authorizedPrescriptionFor(requested, count)
                    else -> generationPrescriptions.prescribe(snapshot, state.strengthIntent, requested, requested.style)
                }
                val resolved = accepted ?: prescription
                return template.copy(setCount = count, setPrescriptions = resolved?.sets.orEmpty(),
                    prescription = resolved?.text, selectionRole = event.owner.selectionRole)
            }
            val requestedCount = requireNotNull(event.before).setCount
            event.copy(
                before = exactState(event.before, requestedCount, sourceItem, null),
                after = event.after?.let { exactState(it, it.setCount, allocatedDemand?.item, allocatedDemand?.prescription) }
            ).withExactAuthority(exactPrescriptionAuthorizationProvider)
        }.deterministicOwnerOrder()
        val retentionPriorities = mutableMapOf<String, Int>()
        val postProcessAtoms = mutableMapOf<String, String>()
        val postProcessSources = mutableMapOf<String, PlannedExercise>()
        val postProcessOrigins = mutableMapOf<String, AuthorizedAtomOrigin>()
        val items = buildList {
            (1..horizon).forEach { week ->
                val weekDays = schedule.getValue(week).sorted()
                logical.forEach { (logicalDay, rows) ->
                    rows.forEachIndexed { index, scheduledAtom ->
                        val timedItem = scheduledAtom.timed
                        val item = timedItem.item
                        val exercise = snapshot.exercises.getValue(item.stableKey)
                        val meta = snapshot.metadata[item.stableKey]
                        val rx = timedItem.prescription
                        val scalar = rx.sets.first()
                        val estimatedSeconds = timedItem.estimatedSeconds
                        val localId = "personalized_${week}_${logicalDay}_${index}_${item.stableKey}"
                        retentionPriorities[localId] = item.priority
                        val atomId = "slot_${logicalDay}_${index}"
                        postProcessAtoms[localId] = atomId
                        postProcessSources[atomId] = item
                        postProcessOrigins[atomId] = scheduledAtom.origin
                        add(ProgramSkeletonItem(
                            localId = localId, weekNumber = week, dayOfWeek = weekDays[logicalDay - 1], orderIndex = index + 1,
                            progressionStyle = item.style.takeUnless { it in setOf(StrengthProgrammingStyle.NONE, StrengthProgrammingStyle.UNRESOLVED) }?.name.orEmpty(),
                            progressionVariant = item.styleVariant,
                            progressionRole = MainSchedulingPolicy.role(item, item in continuity || scheduledAtom.origin.splitGroupId.isNotBlank() && placement.trace.authorized.any { it.id == scheduledAtom.origin.authorizedDemandId && it.continuity }),
                            progressionAnchorSetIndex = if (item.style in setOf(StrengthProgrammingStyle.TOP_SET_BACKOFF, StrengthProgrammingStyle.TOP_SET_HYPERTROPHY, StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING)) rx.sets.maxByOrNull { it.weightKg }?.setIndex else null,
                            exerciseStableKey = item.stableKey, exerciseName = exercise.name, category = exercise.category, restSeconds = rx.restSeconds, prescription = rx.text,
                            setCount = rx.sets.size, reps = scalar.reps, weightKg = scalar.weightKg, seconds = scalar.seconds, selectionReason = item.reason, weightSource = rx.weightSource,
                            trainingSlot = item.role, stableKey = item.stableKey, selectionRole = item.role, movementFamily = meta?.movementFamily.orEmpty(), movementSubtype = meta?.movementSubtype.orEmpty(),
                            metadataProgramSlot = meta?.programSlot.orEmpty(), redundancyGroup = meta?.redundancyGroup.orEmpty(), strengthProgressionGroup = meta?.strengthProgressionGroup.orEmpty(),
                            primaryStressProfile = meta?.primaryStressProfile.orEmpty(), stressMagnitudeHint = meta?.stressMagnitudeHint.orEmpty(), neuromuscularStressLevel = meta?.neuromuscularStressLevel.orEmpty(),
                            systemicMuscularStressLevel = meta?.systemicMuscularStressLevel.orEmpty(), localMuscularStressLevel = meta?.localMuscularStressLevel.orEmpty(), jointTendonImpactStressLevel = meta?.jointTendonImpactStressLevel.orEmpty(),
                            movementFocusDemandLevel = meta?.movementFocusDemandLevel.orEmpty(), recoveryDurationClass = meta?.recoveryDurationClass.orEmpty(), badmintonTransferLevel = meta?.badmintonTransferLevel.orEmpty(), estimatedDurationSeconds = estimatedSeconds, setPrescriptions = rx.sets,
                            taskProtocolSemanticsJson = item.taskProtocolAuthorization?.let { authorization ->
                                item.taskProtocolExposureIndex?.let { exposureIndex ->
                                    TaskProtocolExposureMetadata(authorization, exposureIndex).toJsonString()
                                }
                            }
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
        val repairResult = repairPolicy.repairWithProvenance(rawSkeleton, validator.errors(rawSkeleton, state.genericCourtLoad),
            retentionPriorities, state.genericCourtLoad, postProcessAtoms.mapValues { (_, atom) -> postProcessOrigins.getValue(atom).authorizedDemandId })
        val repaired = repairResult.skeleton
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
            resistance = resistanceBudget.copy(resistanceAuthorizedBeforeCompletion = selected.filter {
                snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE
            }.sumOf(PlannedExercise::targetSets)),
            domains = domains,
            execution = ExecutionAllocationTrace(
                capacity = envelope,
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
                scheduleTiers = timed.associate { it.item.stableKey to it.item.scheduleTier() },
                constrainedOwnerStableKeys = finiteConstrainedOwners + selected.groupBy(PlannedExercise::stableKey)
                    .filter { (key, owners) ->
                        owners.sumOf(PlannedExercise::targetSets) > firstWeek.filter { it.exerciseStableKey == key }.sumOf(ProgramSkeletonItem::setCount)
                    }.keys + placementDeferred.mapTo(linkedSetOf()) { it.item.stableKey },
                ownerAllocationProvenance = (materialOwnerProvenance + exactFiniteOwnerProvenance + placementOwnerProvenance + repairResult.ownerAllocationProvenance)
                    .map { it.withExactAuthority(exactPrescriptionAuthorizationProvider) }.deterministicOwnerOrder(),
                materialDemandCandidateOrigins = demand.candidateOrigins,
                unresolvedMaterialDemandGaps = demand.unresolvedGapCodes
            )
        )
        val fingerprint = personalizedProgramFingerprint(repaired.request, repaired.items)
        val decision = PersonalizedPlanningDecision(
            decisionId = UUID.randomUUID().toString(), protocolVersion = PERSONALIZED_PLANNER_PROTOCOL, generatedAtEpochMillis = System.currentTimeMillis(), historyCutoff = snapshot.cutoff.toString(), historyWindowDays = minOf(56, state.historyDays),
            planningHorizonWeeks = horizon, adaptationIntentMinWeeks = intent.adaptationMinWeeks, adaptationIntentMaxWeeks = intent.adaptationMaxWeeks, observedTrainingBehavior = state.observedBehavior.name,
            strengthIntent = state.strengthIntent.name, strengthIntentProvenance = if (snapshot.preferences.strengthIntent != null || QUESTION_STRENGTH_INTENT in answers.values) "EXPLICIT_USER" else "INFERRED_OR_UNRESOLVED",
            badmintonIntent = state.badmintonIntent.name, badmintonIntentProvenance = if (snapshot.preferences.badmintonIntent != null || QUESTION_BADMINTON_INTENT in answers.values) "EXPLICIT_USER" else "PROFILE_OR_UNRESOLVED",
            primaryAdaptation = intent.primary, secondaryTargets = gaps.map(AdaptationGap::code), strengthStyle = state.observedStrengthStyle.name, strengthStyleProvenance = "OBSERVED_HISTORY_ONLY", weeklyFrequency = days,
            confidence = state.confidence.name, reasonCodes = intent.reasonCodes + listOf("RESOLVED_WEEKLY_DAYS_${days}", "WEEKLY_COURT_LOAD_NORMALIZED", "DOMAIN_SEPARATE_VOLUME_AUTHORIZATION", "COMBINED_SCHEDULING_CANONICAL_VALIDATION") + if (capacityExpanded) listOf("MINIMAL_CAPACITY_EXPANSION") else emptyList(), reasons = intent.reasons, constraints = intent.constraints, metadataAuthorityVersion = PERSONALIZED_AUTHORITY_VERSION, priorDecisionId = priorDecisionId, userAnswers = answers.values,
            originalGenerationFingerprint = fingerprint, recoverySignalCodes = state.recoverySignals.sourceCodes.sorted(), genericCourtLoad = state.genericCourtLoad, objectiveExposure = state.objectiveExposure,
            anchorTransitions = transitions.values.sortedBy(AnchorTransition::stableKey), planningBudget = budget,
            courtBaselineLoad = state.courtBaselineLoad,
            recentCourtLoad = state.recentCourtLoad,
            courtDeviation = state.courtDeviation,
            lowerNegativeEvidence = state.lowerNegativeEvidence,
            courtInterference = state.courtInterference,
            authorizedScheduling = placement.trace.copy(origins = postProcessOrigins, initialWeek = firstWeek,
                localOrigins = postProcessAtoms.mapValues { postProcessOrigins.getValue(it.value) },
                ownerAllocationProvenance = placementOwnerProvenance),
            movementRepresentations = state.movementRepresentations,
            badmintonObjectiveRepresentations = state.badmintonObjectiveRepresentations,
            adaptationGaps = gaps,
            trainingStateAssessment = state.trainingStateAssessment,
            weeklyFrequencyEvidence = frequency.recommendation,
            frequencyDemand = FrequencyDemandProvenance(frequency, candidates, placement.trace.authorized, envelope,
                plannedResistanceSets + plannedDrillBouts + plannedAthleticBouts,
                state.fullEligibleIncumbentRanking, retained,
                boundedMaterialAllocation = bounded?.let { BoundedMaterialAllocationTrace(capacity,
                    it.bounds.filter { bound -> bound.rejection == null }.sumOf { bound -> bound.maximumUnits } +
                        candidates.filter { candidate -> candidate.continuity || !candidate.item.material }.sumOf { candidate -> candidate.requestedUnits } +
                        (if (frequency.explicitIncrease) retained.sumOf { candidate -> candidate.requestedUnits } else 0),
                    it.bounds) })
        )
        // INITIAL SKELETON: all existing selection, placement, repair, validation and fingerprinting end here.
        val initialSkeleton = repaired.copy(personalizedDecision = decision)
        val authorized = try {
            placement.trace.authorized.map { AuthorizedPrescription(it.id, it.item, it.prescription, it.continuity) }
        } catch (failure: Exception) {
            if (failure is java.util.concurrent.CancellationException) throw failure
            if (activeCanonicalFailureEmitter != null) throw failure
            return initialSkeleton.copy(personalizedDecision = decision.copy(residualCompletion = ResidualCompletionTrace(
                "POST_PROCESS_FAILED_SAFE_AUTHORIZED_PRESCRIPTION", fingerprint, fingerprint, snapshot.cutoff.plusDays(1).toString())))
        }
        val completion = ResidualCompletion(generationPrescriptions, progress).complete(initialSkeleton, snapshot, state, gaps,
            authorized, envelope, postProcessAtoms, postProcessSources, explicitWeeklyDays, snapshot.planDayProjection, postProcessOrigins,
            regionalTargetPlan?.authorizedPrescriptionBySelectionRole?.keys.orEmpty(),
            exactAuthorizationOnly = bounded != null || exactPrescriptionAuthorizationProvider != null)
        val completedWeek = completion.skeleton.items.filter { it.weekNumber == 1 }
        fun completedUnits(kind: PlannedActivityKind) = completedWeek.filter { snapshot.activityKind(it.exerciseStableKey) == kind }
            .sumOf { it.setPrescriptions.size }
        val completedResistance = completedUnits(PlannedActivityKind.RESISTANCE)
        val completedStructured = completedUnits(PlannedActivityKind.STRUCTURED_BADMINTON_DRILL)
        val completedAthletic = completedUnits(PlannedActivityKind.ATHLETIC_PERFORMANCE_DRILL)
        val finalizedBudget = budget.copy(
            plannedResistanceSets = completedResistance,
            plannedStructuredBadmintonBouts = completedStructured,
            plannedAthleticPerformanceBouts = completedAthletic,
            resistance = budget.resistance?.copy(
                resistanceCompletionAddedSets = (completedResistance - (budget.resistance.resistanceAuthorizedBeforeCompletion)).coerceAtLeast(0),
                resistanceFinalSets = completedResistance
            ),
            domains = budget.domains?.let { domains -> domains.copy(
                resistance = domains.resistance.copy(
                    resistanceCompletionAddedSets = (completedResistance - domains.resistance.resistanceAuthorizedBeforeCompletion).coerceAtLeast(0),
                    resistanceFinalSets = completedResistance
                ),
                structuredBadminton = domains.structuredBadminton.copy(finalBouts = completedStructured),
                athleticPerformance = domains.athleticPerformance.copy(finalBouts = completedAthletic)
            ) }
        )
        if (finish != null) return finish(completion.copy(skeleton = observeBoundedMaterialDemand(completion.skeleton.copy(personalizedDecision = decision.copy(
            authorizedScheduling = completion.skeleton.personalizedDecision?.authorizedScheduling,
            residualCompletion = completion.trace,
            planningBudget = finalizedBudget.withOwnerAllocationProvenance(completion.trace.ownerAllocationProvenance,
                exactPrescriptionAuthorizationProvider),
            weeklyFrequency = completion.skeleton.request.weeklyTrainingDays)))))
        // The second stage owns only placement. Its fail-safe is CompletedPlan, never InitialSkeleton.
        progress.report(PersonalizedPlannerStage.BALANCE)
        val rebalanced = BoundedDayRebalancer().rebalance(completion, snapshot, state, snapshot.planDayProjection,
            RebalanceEvaluationCounts(performanceMetrics = performanceMetrics))
        return observeBoundedMaterialDemand(rebalanced.skeleton.copy(personalizedDecision = decision.copy(
            authorizedScheduling = completion.skeleton.personalizedDecision?.authorizedScheduling,
            residualCompletion = completion.trace,
            dayRebalancing = rebalanced.trace,
            frequencyDemand = decision.frequencyDemand?.copy(actualMaterializedUnits = completedWeek.sumOf { it.setPrescriptions.size }),
            // Existing execution trace remains the initial allocation audit; display counts describe the completed plan.
            planningBudget = finalizedBudget.withOwnerAllocationProvenance(
                completion.trace.ownerAllocationProvenance + rebalanced.trace.ownerAllocationProvenance,
                exactPrescriptionAuthorizationProvider),
            weeklyFrequency = completion.skeleton.request.weeklyTrainingDays)))
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

private fun mergeTypedMaterialDemand(base: MaterialDemand, experimental: MaterialDemand, experimentalKeys: Set<RegionalSelectionIdentity>): MaterialDemand {
    val merged = linkedMapOf<Pair<Boolean, RegionalSelectionIdentity>, PlannedExercise>()
    (base.candidates + experimental.candidates).forEach { candidate ->
        val exactIdentity = RegionalSelectionIdentity(candidate.stableKey, candidate.role)
        val isExperimental = exactIdentity in experimentalKeys
        val mergeIdentity = isExperimental to if (isExperimental) exactIdentity else RegionalSelectionIdentity(candidate.stableKey, "")
        val old = merged[mergeIdentity]
        merged[mergeIdentity] = if (old == null) candidate else old.copy(
            role = if (isExperimental) candidate.role else old.role,
            styleVariant = if (isExperimental) candidate.styleVariant else old.styleVariant,
            style = if (isExperimental) candidate.style else old.style,
            targetSets = maxOf(old.targetSets, candidate.targetSets),
            priority = maxOf(old.priority, candidate.priority),
            representedGapCodes = old.representedGapCodes + candidate.representedGapCodes,
            reason = if (isExperimental) candidate.reason else old.reason
        )
    }
    return MaterialDemand(
        candidates = merged.values.toList(),
        deferred = base.deferred + experimental.deferred,
        audit = base.audit + experimental.audit,
        ownerAllocationProvenance = (base.ownerAllocationProvenance + experimental.ownerAllocationProvenance).distinct()
            .deterministicOwnerOrder(),
        candidateOrigins = (base.candidateOrigins + experimental.candidateOrigins).distinct().sortedWith(compareBy(
            { it.owner.stableKey }, { it.owner.selectionRole }, { it.gapCodes.sorted().joinToString("|") }
        )),
        candidateAlternatives = (base.candidateAlternatives + experimental.candidateAlternatives).distinct(),
        unresolvedGapCodes = base.unresolvedGapCodes + experimental.unresolvedGapCodes
    )
}

internal fun personalizedProgramFingerprint(request: ProgramSkeletonRequest, items: List<ProgramSkeletonItem>): String {
    val source = buildString {
        append(listOf(request.name, request.goal.name, request.durationWeeks, request.weeklyTrainingDays, request.sessionMinutes).joinToString("|"))
        items.sortedWith(compareBy(ProgramSkeletonItem::weekNumber, ProgramSkeletonItem::dayOfWeek, ProgramSkeletonItem::orderIndex, ProgramSkeletonItem::exerciseStableKey)).forEach { item ->
            append('\n').append(listOf(item.weekNumber, item.dayOfWeek, item.orderIndex, item.exerciseStableKey, item.selectionRole, item.restSeconds, item.prescription, item.taskProtocolSemanticsJson, item.setPrescriptions.joinToString { "${it.reps}:${it.weightKg}:${it.seconds}:${it.loadState}" + it.targetRpeMin?.let { target -> ":${targetRpeFingerprint(target)}" }.orEmpty() }).joinToString("|"))
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
}

internal fun mergeCanonicalMaterialDemand(base: MaterialDemand, canonical: MaterialDemand): MaterialDemand {
    val canonicalKeys = canonical.candidates.mapTo(linkedSetOf(), PlannedExercise::stableKey)
    return MaterialDemand(
        candidates = base.candidates.filterNot { it.stableKey in canonicalKeys } + canonical.candidates,
        deferred = base.deferred + canonical.deferred,
        audit = base.audit + canonical.audit,
        ownerAllocationProvenance = (base.ownerAllocationProvenance + canonical.ownerAllocationProvenance).distinct()
            .deterministicOwnerOrder(),
        candidateOrigins = (base.candidateOrigins + canonical.candidateOrigins).distinct().sortedWith(compareBy(
            { it.owner.stableKey }, { it.owner.selectionRole }, { it.gapCodes.sorted().joinToString("|") }
        )),
        candidateAlternatives = (base.candidateAlternatives + canonical.candidateAlternatives).distinct(),
        unresolvedGapCodes = base.unresolvedGapCodes + canonical.unresolvedGapCodes
    )
}

internal enum class MaterialDemandExecutionDeferral(val reasonCode: String) {
    NO_EXACT_EXECUTABLE_PRESCRIPTION_AUTHORITY("MATERIAL_DEMAND_NO_EXACT_EXECUTABLE_PRESCRIPTION_AUTHORITY"),
    NO_SUPPORTED_AUTHORIZED_OWNER_AFTER_RESELECTION("MATERIAL_DEMAND_NO_SUPPORTED_AUTHORIZED_OWNER_AFTER_RESELECTION")
}

private fun exactMaterialDemandPrescription(
    candidate: PlannedExercise,
    provider: ExactPrescriptionAuthorizationProvider?,
    regionalTargetPlan: RegionalExperimentalTargetPlan?
): PlannedPrescription? {
    regionalTargetPlan?.authorizedPrescriptionFor(candidate)?.let { return it }
    return when (val resolution = provider?.resolveOwnerPrescription(candidate)) {
        is ExactOwnerPrescriptionResolution.Authorized -> resolution.prescription
        is ExactOwnerPrescriptionResolution.PreserveIncumbent -> resolution.prescription
        ExactOwnerPrescriptionResolution.ExcludeConflictingAddition,
        ExactOwnerPrescriptionResolution.NoExecutableAuthority,
        ExactOwnerPrescriptionResolution.NoExactAuthority,
        null -> null
    }
}

private fun hasExactMaterialDemandExecutionAuthority(
    candidate: PlannedExercise,
    provider: ExactPrescriptionAuthorizationProvider?,
    regionalTargetPlan: RegionalExperimentalTargetPlan?
): Boolean {
    val identity = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.role)
    val taskGrant = candidate.taskProtocolAuthorization
    if (taskGrant != null) {
        val definition = taskGrant.definition
        return taskGrant.status == TaskProtocolB6Status.AUTHORIZED_APPROVED_TASK_PROTOCOL &&
            definition.provenance == TaskProtocolPolicyProvenance.USER_APPROVED_PROJECT_POLICY &&
            ApprovedBadmintonTaskProtocols.exact(identity.stableKey, identity.selectionRole,
                definition.primaryTask) == definition &&
            definition.stableKey == identity.stableKey && definition.selectionRole == identity.selectionRole &&
            taskGrant.attributedTasks.isNotEmpty() && taskGrant.attributedTasks.all { it in definition.authorizedTasks &&
                taskGrant.transferEvidence[it] == com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel.DIRECT
            }
    }
    // An exact quality prescription is usable for a material-demand candidate only when B5
    // selected that same owner identity. A compatible prescription from another selection path
    // cannot silently convert a candidate into a B5-owned executable row.
    if (regionalTargetPlan == null && identity !in provider?.b5SelectedQualityOwners.orEmpty()) return false
    return exactMaterialDemandPrescription(candidate, provider, regionalTargetPlan) != null
}

/**
 * First return a denied material-demand candidate to the existing finite candidate selector.
 * Only an alternative with exact authority for its full owner identity and all original gap
 * codes can replace it. This lookup carries no dose authority and cannot invent a fallback.
 */
internal fun reResolveMaterialDemandCandidatesWithExistingAuthority(
    demand: MaterialDemand,
    provider: ExactPrescriptionAuthorizationProvider?,
    regionalTargetPlan: RegionalExperimentalTargetPlan? = null
): MaterialDemand {
    if (demand.candidateOrigins.isEmpty()) return demand
    val candidates = demand.candidates.toMutableList()
    val updatedOrigins = demand.candidateOrigins.map { origin ->
        val original = candidates.firstOrNull {
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.role) == origin.owner
        } ?: return@map origin
        if (hasExactMaterialDemandExecutionAuthority(original, provider, regionalTargetPlan)) {
            exactMaterialDemandPrescription(original, provider, regionalTargetPlan)?.let { exact ->
                if (original.targetSets <= 0) {
                    val index = candidates.indexOfFirst { StimulusPrescriptionOwnerIdentity(it.stableKey, it.role) == origin.owner }
                    if (index >= 0) candidates[index] = original.copy(targetSets = exact.sets.size)
                }
            }
            return@map origin.copy(authorityResolution = ExecutionAuthorityResolution(
                status = ExecutionAuthorityResolutionStatus.READY,
                reason = ExecutionAuthorityResolutionReason.EXACT_AUTHORITY_AVAILABLE,
                returnTarget = ExecutionAuthorityReturnTarget.NONE,
                originalOwner = origin.owner,
                attemptedOwners = listOf(origin.owner),
                finalOwner = origin.owner
            ))
        }

        val b6Recovery = provider?.executionAuthorityResolutions?.get(origin.owner)
        // B6's typed return target takes precedence. Re-selection can resolve only an owner
        // identity problem; it cannot manufacture a missing B4 target, history reference, or load.
        if (b6Recovery != null && b6Recovery.status !in setOf(
                ExecutionAuthorityResolutionStatus.NEEDS_OWNER_RESELECTION,
                ExecutionAuthorityResolutionStatus.READY
            )) {
            val typedRecovery = b6Recovery.copy(
                originalOwner = origin.owner,
                attemptedOwners = (b6Recovery.attemptedOwners + origin.owner).distinct()
            )
            return@map origin.copy(authorityResolution = typedRecovery)
        }

        val attempted = linkedSetOf<StimulusPrescriptionOwnerIdentity>().apply {
            addAll(b6Recovery?.attemptedOwners.orEmpty())
            add(origin.owner)
        }
        var replacement: PlannedExercise? = null
        for (alternative in demand.candidateAlternatives) {
            if (!alternative.gapCodes.containsAll(origin.gapCodes)) continue
            val candidate = alternative.candidate
            val identity = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.role)
            if (!attempted.add(identity)) continue
            if (hasExactMaterialDemandExecutionAuthority(candidate, provider, regionalTargetPlan)) {
                val exact = exactMaterialDemandPrescription(candidate, provider, regionalTargetPlan)
                replacement = candidate.copy(targetSets = exact?.sets?.size?.takeIf { candidate.targetSets <= 0 } ?: candidate.targetSets)
                break
            }
        }

        if (replacement != null) {
            candidates.removeAll { StimulusPrescriptionOwnerIdentity(it.stableKey, it.role) == origin.owner }
            val replacementIdentity = StimulusPrescriptionOwnerIdentity(replacement.stableKey, replacement.role)
            val existingIndex = candidates.indexOfFirst {
                StimulusPrescriptionOwnerIdentity(it.stableKey, it.role) == replacementIdentity
            }
            if (existingIndex >= 0) {
                val existing = candidates[existingIndex]
                candidates[existingIndex] = existing.copy(
                    representedGapCodes = existing.representedGapCodes + replacement.representedGapCodes
                )
            } else {
                candidates += replacement
            }
            origin.copy(authorityResolution = ExecutionAuthorityResolution(
                status = ExecutionAuthorityResolutionStatus.READY,
                reason = ExecutionAuthorityResolutionReason.EXACT_AUTHORITY_AVAILABLE,
                returnTarget = ExecutionAuthorityReturnTarget.MATERIAL_DEMAND_CANDIDATE_SELECTION,
                originalOwner = origin.owner,
                attemptedOwners = attempted.toList(),
                finalOwner = replacementIdentity
            ))
        } else {
            origin.copy(authorityResolution = ExecutionAuthorityResolution(
                status = ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY,
                reason = ExecutionAuthorityResolutionReason.OWNER_CANDIDATES_EXHAUSTED,
                returnTarget = ExecutionAuthorityReturnTarget.MATERIAL_DEMAND_CANDIDATE_SELECTION,
                originalOwner = origin.owner,
                attemptedOwners = attempted.toList()
            ))
        }
    }
    val unresolvedGaps = updatedOrigins.asSequence()
        .filter { it.authorityResolution?.status != null &&
            it.authorityResolution.status != ExecutionAuthorityResolutionStatus.READY }
        .flatMap { it.gapCodes.asSequence() }
        .toSet()
    val unresolvedOwners = updatedOrigins.filter {
        it.authorityResolution?.status == ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY
    }.associate { origin ->
        "${origin.owner.stableKey}#${origin.owner.selectionRole}" to
            MaterialDemandExecutionDeferral.NO_SUPPORTED_AUTHORIZED_OWNER_AFTER_RESELECTION.reasonCode
    }
    return demand.copy(
        candidates = candidates,
        deferred = demand.deferred + unresolvedOwners,
        audit = demand.audit + unresolvedOwners,
        candidateOrigins = updatedOrigins,
        unresolvedGapCodes = demand.unresolvedGapCodes + unresolvedGaps
    )
}

/**
 * Material-demand candidates can remain in origin diagnostics, but only an exact B6 grant,
 * an explicitly preserved incumbent, or an exact C24 Task B6 grant may cross into scheduling.
 * This runs before allocation so later frequency/completion stages receive no denied candidate.
 */
internal fun filterMaterialDemandCandidatesWithoutExactExecutionAuthority(
    demand: MaterialDemand,
    provider: ExactPrescriptionAuthorizationProvider?,
    regionalTargetPlan: RegionalExperimentalTargetPlan? = null
): MaterialDemand {
    val originatedOwners = demand.candidateOrigins.flatMapTo(linkedSetOf()) { origin ->
        listOfNotNull(origin.owner, origin.authorityResolution?.finalOwner)
    }
    if (originatedOwners.isEmpty()) return demand

    val deferredOwners: Set<StimulusPrescriptionOwnerIdentity> = demand.candidates.asSequence()
        .filter { StimulusPrescriptionOwnerIdentity(it.stableKey, it.role) in originatedOwners }
        .filterNot { hasExactMaterialDemandExecutionAuthority(it, provider, regionalTargetPlan) }
        .map {
            StimulusPrescriptionOwnerIdentity(it.stableKey, it.role)
        }
        .toSortedSet(compareBy<StimulusPrescriptionOwnerIdentity>({ it.stableKey }, { it.selectionRole }))
    if (deferredOwners.isEmpty()) return demand

    val ownerKeys = deferredOwners.associate { owner ->
        "${owner.stableKey}#${owner.selectionRole}" to (
            demand.deferred["${owner.stableKey}#${owner.selectionRole}"]
                ?: MaterialDemandExecutionDeferral.NO_EXACT_EXECUTABLE_PRESCRIPTION_AUTHORITY.reasonCode)
    }
    val stillUnresolvedGaps = demand.candidateOrigins.asSequence()
        .filter { origin -> origin.owner in deferredOwners || origin.authorityResolution?.finalOwner?.let { it in deferredOwners } == true }
        .flatMap { it.gapCodes.asSequence() }
        .toSet()
    return demand.copy(
        candidates = demand.candidates.filterNot { candidate ->
            StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.role) in deferredOwners
        },
        deferred = demand.deferred + ownerKeys,
        audit = demand.audit + ownerKeys,
        unresolvedGapCodes = demand.unresolvedGapCodes + stillUnresolvedGaps
        // candidateOrigins intentionally remains, so the rejected candidate is diagnosable;
        // no origin record is copied into accepted executable-mutation provenance.
    )
}

/** Stable diagnostic attached when a B5-selected owner has no exact executable B6 authority. */
internal enum class CanonicalB5MaterialDemandDeferral(val reasonCode: String) {
    NO_EXECUTABLE_EXACT_B6_AUTHORITY("B5_SELECTED_OWNER_NO_EXECUTABLE_EXACT_B6_AUTHORITY")
}

/**
 * B5 establishes identity, not an executable prescription. In the production B5/B6 path, a
 * selected material owner without exact B6 authority must be removed before budgeting and
 * placement; it must not fall through to the generic legacy prescription resolver. Other
 * demand and the existing explicit multi-quality conflict path are left untouched.
 */
internal fun filterCanonicalB5DemandWithoutExecutableB6(
    demand: MaterialDemand,
    canonicalB5Owners: Set<StimulusPrescriptionOwnerIdentity>,
    provider: ExactPrescriptionAuthorizationProvider
): MaterialDemand {
    if (canonicalB5Owners.isEmpty()) return demand
    val deferredOwners = demand.candidates.filter { candidate ->
        if (!candidate.material) return@filter false
        val identity = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.role)
        if (identity !in canonicalB5Owners) return@filter false
        when (provider.resolveOwnerPrescription(candidate)) {
            is ExactOwnerPrescriptionResolution.Authorized,
            is ExactOwnerPrescriptionResolution.PreserveIncumbent,
            ExactOwnerPrescriptionResolution.ExcludeConflictingAddition -> false
            ExactOwnerPrescriptionResolution.NoExecutableAuthority,
            ExactOwnerPrescriptionResolution.NoExactAuthority -> true
        }
    }.mapTo(linkedSetOf()) { StimulusPrescriptionOwnerIdentity(it.stableKey, it.role) }
    if (deferredOwners.isEmpty()) return demand

    val reason = CanonicalB5MaterialDemandDeferral.NO_EXECUTABLE_EXACT_B6_AUTHORITY.reasonCode
    val ownerKeys = deferredOwners.associate { owner -> "${owner.stableKey}#${owner.selectionRole}" to reason }
    return demand.copy(
        candidates = demand.candidates.filterNot { candidate ->
            StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.role) in deferredOwners
        },
        deferred = demand.deferred + ownerKeys,
        audit = demand.audit + ownerKeys,
        ownerAllocationProvenance = demand.ownerAllocationProvenance.filterNot { it.owner in deferredOwners }
    )
}

/** Stable diagnostic attached when an exact B5 task owner has no task execution authority. */
internal enum class CanonicalB5TaskMaterialDemandDeferral(val reasonCode: String) {
    NO_EXECUTABLE_TASK_B6_AUTHORITY("B5_SELECTED_TASK_OWNER_NO_EXECUTABLE_TASK_B6_AUTHORITY")
}

/**
 * Task identity selection is not task prescription authority. Until an exact task B6 exists,
 * defer only the exact selected task-owner rows before allocation so seed/reviewed fallbacks
 * cannot turn them into executable-looking material.
 */
internal fun filterCanonicalB5TaskDemandWithoutExecutableB6(
    demand: MaterialDemand,
    canonicalB5TaskOwnersWithoutExecutableB6: Set<StimulusPrescriptionOwnerIdentity>
): MaterialDemand {
    if (canonicalB5TaskOwnersWithoutExecutableB6.isEmpty()) return demand
    val deferredOwners = demand.candidates.asSequence()
        .filter(PlannedExercise::material)
        .map { StimulusPrescriptionOwnerIdentity(it.stableKey, it.role) }
        .filter(canonicalB5TaskOwnersWithoutExecutableB6::contains)
        .toSortedSet(compareBy({ it.stableKey }, { it.selectionRole }))
    if (deferredOwners.isEmpty()) return demand

    val reason = CanonicalB5TaskMaterialDemandDeferral.NO_EXECUTABLE_TASK_B6_AUTHORITY.reasonCode
    val ownerKeys = deferredOwners.associate { owner -> "${owner.stableKey}#${owner.selectionRole}" to reason }
    return demand.copy(
        candidates = demand.candidates.filterNot { candidate ->
            candidate.material && StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.role) in deferredOwners
        },
        deferred = demand.deferred + ownerKeys,
        audit = demand.audit + ownerKeys,
        ownerAllocationProvenance = demand.ownerAllocationProvenance.filterNot { it.owner in deferredOwners }
    )
}

/** Attaches only exact C24 grants and counts targetSets as physical sets for weekly capacity. */
internal fun applyTaskProtocolAuthorizations(
    demand: MaterialDemand,
    plan: TaskProtocolAuthorizationPlan
): MaterialDemand {
    if (plan.authorizedByOwner.isEmpty()) return demand
    val candidates = demand.candidates.flatMap { candidate ->
        val owner = StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.role)
        val grant = plan.authorizedByOwner[owner] ?: return@flatMap listOf(candidate)
        (1..grant.definition.weeklyExposures).map { exposureIndex ->
            candidate.copy(
                targetSets = grant.definition.shape.setCount,
                styleVariant = "TASK_PROTOCOL_EXPOSURE_$exposureIndex",
                representedObjectives = grant.attributedTasks.mapTo(linkedSetOf()) { it.name },
                taskProtocolAuthorization = grant,
                taskProtocolExposureIndex = exposureIndex
            )
        }
    }
    return demand.copy(candidates = candidates)
}

private fun targetRpeFingerprint(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()


private fun List<PlanningSetRecord>.countLastSession(): Int {
    val date = maxOf(PlanningSetRecord::date)
    return count { it.date == date }.coerceIn(1, 5)
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

internal data class PersonalizedProgramBuildArtifacts(
    val program: GeneratedProgramSkeleton,
    val incumbentSeed: StimulusIncumbentIdentitySeed,
    val prescriptionBaseline: StimulusIncumbentPrescriptionBaseline
)

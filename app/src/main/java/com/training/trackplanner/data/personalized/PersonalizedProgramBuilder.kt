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
    val transition: AnchorTransition? = null
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
    fun select(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, gaps: List<AdaptationGap>, used: Set<String>): List<PlannedExercise> {
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
                        ?.let { key -> PlannedExercise(key, "FOUNDATIONAL_${coverage.name}", gap.reason, 80, targetSets = 2) }
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
            val candidates = selectableCandidates(snapshot, state, chosen).asSequence()
                .filter { key ->
                    when {
                        gap.code == "BADMINTON_FOUNDATIONAL_ONRAMP" -> snapshot.activityKind(key) == PlannedActivityKind.STRUCTURED_BADMINTON_DRILL && snapshot.hasSafePerformancePrescription(key)
                        objective.isNotBlank() -> objective in snapshot.badmintonDirectObjectives[key].orEmpty() &&
                            snapshot.activityKind(key) in PERFORMANCE_ACTIVITY_KINDS &&
                            snapshot.hasSafePerformancePrescription(key)
                        else -> snapshot.activityKind(key) == PlannedActivityKind.RESISTANCE && snapshot.movementCoverage(key) in target
                    }
                }
                .sortedWith(
                    compareByDescending<String> { it in historyKeys }
                        .thenByDescending { state.freeWeightWillingness != FreeWeightWillingness.PREFER_FAMILIAR || !snapshot.isFreeWeight(it) }
                        .thenByDescending { snapshot.metadata[it]?.sourceConfidenceLevel == "HIGH" }
                        .thenBy { it }
                )
                .toList()
            val key = candidates.firstOrNull() ?: return@flatMap emptyList()
            chosen += key
            val retentionPriority = when (gap.priority) {
                "HIGH" -> 90
                "MEDIUM", "MODERATE" -> 80
                else -> 70
            }
            val targetSets = snapshot.reviewedBadmintonCategory(key)?.let { ProgramIntensityResolver.badminton(it).setCount } ?: 2
            listOf(PlannedExercise(key, if (gap.code.startsWith("BADMINTON")) "BADMINTON_OBJECTIVE_$objective" else "COVERAGE_${gap.code}", "${gap.reason} canonical planning metadata로 ${snapshot.exercises.getValue(key).name}을 선택했습니다.", retentionPriority, targetSets = targetSets))
        }
    }

    private fun selectableCandidates(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, chosen: Set<String>): List<String> {
        val historyKeys = snapshot.allConfirmedSets.mapTo(mutableSetOf(), PlanningSetRecord::stableKey)
        return snapshot.exercises.keys.asSequence()
            .filter { key -> key !in chosen && snapshot.metadata[key]?.planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE") }
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

private fun badmintonObjectiveFromGap(code: String): String = when {
    code.startsWith("BADMINTON_DROP_") -> code.removePrefix("BADMINTON_DROP_")
    code.startsWith("BADMINTON_UNDERREPRESENTED_") -> code.removePrefix("BADMINTON_UNDERREPRESENTED_")
    code.startsWith("BADMINTON_DEVELOP_") -> code.removePrefix("BADMINTON_DEVELOP_")
    else -> ""
}

private fun PlanningHistorySnapshot.reviewedBadmintonCategory(stableKey: String): ProgramBadmintonCategory? =
    ProgramRuleTables.badmintonAccessories.entries.firstOrNull { (_, specs) -> specs.any { it.stableKey == stableKey } }?.key

private fun PlanningHistorySnapshot.hasSafePerformancePrescription(stableKey: String): Boolean =
    allConfirmedSets.any { it.stableKey == stableKey } || reviewedBadmintonCategory(stableKey) != null

private fun unresolvedPerformancePrescriptionCodes(snapshot: PlanningHistorySnapshot, gaps: List<AdaptationGap>): List<String> =
    gaps.mapNotNull { gap ->
        val objective = badmintonObjectiveFromGap(gap.code)
        if (objective.isBlank()) return@mapNotNull null
        val directCandidates = snapshot.exercises.keys.filter { key ->
            snapshot.metadata[key]?.planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE") &&
                objective in snapshot.badmintonDirectObjectives[key].orEmpty() &&
                snapshot.activityKind(key) in PERFORMANCE_ACTIVITY_KINDS
        }
        if (directCandidates.isNotEmpty() && directCandidates.none(snapshot::hasSafePerformancePrescription)) {
            "NO_SAFE_PRESCRIPTION_AUTHORITY_$objective"
        } else null
    }.distinct()

class WeeklyStructurePlanner {
    fun distribute(items: List<PlannedExercise>, weeklyDays: Int, sessionMinutes: Int = 60, genericCourtLoad: Double = 0.0): Map<Int, List<PlannedExercise>> {
        val buckets = (1..weeklyDays).associateWith { mutableListOf<PlannedExercise>() }
        val fixedStyle = items.filter { it.styleVariant.isNotBlank() }
        val normal = items.filter { it.styleVariant.isBlank() }
        fixedStyle.groupBy(PlannedExercise::stableKey).toSortedMap().values.forEach { anchorVariants ->
            anchorVariants.forEachIndexed { index, item ->
                val logicalDay = (index * (weeklyDays - 1) / (anchorVariants.size - 1).coerceAtLeast(1)) + 1
                buckets.getValue(logicalDay).add(item)
            }
        }
        normal.sortedWith(compareByDescending<PlannedExercise> { it.priority }.thenBy(PlannedExercise::stableKey)).forEach { item ->
            val courtCapacityPenalty = if (genericCourtLoad >= 180.0) 1 else 0
            val capacity = ((sessionMinutes / 10).coerceIn(3, 6) - courtCapacityPenalty).coerceAtLeast(3)
            val target = buckets.entries.filter { it.value.size < capacity }.minByOrNull { (_, rows) -> rows.size + rows.count { it.role.contains("LOWER") || it.role.contains("POSTERIOR") } * if (item.role.contains("LOWER") || item.role.contains("POSTERIOR")) 3 else 0 }
                ?: error("주간 세션 용량을 초과했습니다.")
            target.value.add(item)
        }
        return buckets
    }
}

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
        val history = snapshot.allConfirmedSets.filter { it.stableKey == item.stableKey }
        val latestDate = history.maxByOrNull { it.date.toEpochDay() }?.date
        val latestWeek = latestDate?.let { it.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) to it.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) }
        val last = history.filter { latestWeek == (it.date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) to it.date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)) }
            .maxWithOrNull(compareBy<PlanningSetRecord> { it.weightKg }.thenBy { it.reps })
        if (last == null) {
            if (snapshot.activityKind(item.stableKey) in PERFORMANCE_ACTIVITY_KINDS) {
                val category = requireNotNull(snapshot.reviewedBadmintonCategory(item.stableKey)) { "NO_SAFE_PRESCRIPTION_AUTHORITY:${item.stableKey}" }
                val guide = ProgramIntensityResolver.badminton(category)
                val sets = List(guide.setCount) { index -> ProgramSetPrescription(index + 1, guide.reps, 0.0, guide.seconds) }
                return PlannedPrescription(guide.text, sets, guide.restSeconds, "REVIEWED_BADMINTON_RULE_${category.name}")
            }
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
            item.stableKey in snapshot.recoverySignals.tissueRestrictedStableKeys || snapshot.recoverySignals.readinessStatus == "LIMITED" -> ProgressionDecision.REDUCE
            (anchor?.posteriorChangePercent ?: 0.0) < -2.0 -> ProgressionDecision.REVIEW
            provenTwice && (anchor?.posteriorChangePercent ?: 0.0) > 0.0 && strengthIntent in setOf(StrengthIntent.STRENGTH_PRIORITY, StrengthIntent.MIXED) -> ProgressionDecision.ADVANCE
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
        if (skeleton.items.groupBy { it.weekNumber to it.dayOfWeek }.any { it.value.size > 5 }) add("하루 운동 수가 안전한 편집 한도를 넘었습니다.")
        if (genericCourtLoad >= 180.0 && skeleton.items.groupBy { it.weekNumber to it.dayOfWeek }.any { it.value.size > 4 }) add("높은 코트 부하에 비해 하루 운동 수가 많습니다.")
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
        val itemLimit = if (genericCourtLoad >= 180.0) 4 else 5
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
                }.take(itemLimit).sortedBy(ProgramSkeletonItem::orderIndex)
            }
        return skeleton.copy(items = reduced)
    }
}

class PersonalizedProgramBuilder(
    private val continuityPlanner: ExerciseContinuityPlanner = ExerciseContinuityPlanner(),
    private val candidateSelector: GapCandidateSelector = GapCandidateSelector(),
    private val weeklyDosePlanner: WeeklyDosePlanner = WeeklyDosePlanner(),
    private val structurePlanner: WeeklyStructurePlanner = WeeklyStructurePlanner(),
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
        val systemicDoseFactor = (1.0 - .22 * transitionPlanner.systemicRecoveryPressure(state.recoverySignals)).coerceIn(.65, 1.0)
        var targetResistance = (baselineResistance * systemicDoseFactor).roundToInt()
            .coerceAtLeast(if (state.anchors.isEmpty()) 0 else 1)
        val allGapCandidates = candidateSelector.select(snapshot, state, gaps, state.anchors.mapTo(mutableSetOf(), UserAnchor::stableKey))
        val performanceCandidates = allGapCandidates.filter { snapshot.activityKind(it.stableKey) in PERFORMANCE_ACTIVITY_KINDS }
        var resistanceCandidates = allGapCandidates.filter { snapshot.activityKind(it.stableKey) == PlannedActivityKind.RESISTANCE }
        val lead = transitions.values.maxByOrNull(AnchorTransition::rotationPressure)
        val continuityReserve = if (state.anchors.isEmpty()) 0 else maxOf(
            1,
            transitions.values.count { it.structureTreatment in setOf(StructureTreatment.PRESERVE, StructureTreatment.PRESERVE_CORE_REBALANCE) }
        ).coerceAtMost(targetResistance)
        val resistanceOnramp = gaps.any { it.code == "RESISTANCE_FOUNDATIONAL_ONRAMP" }
        var gapBudget = if (resistanceOnramp) {
            resistanceCandidates.sumOf(PlannedExercise::targetSets).also { targetResistance = maxOf(targetResistance, it) }
        } else if (resistanceCandidates.isNotEmpty() && (lead?.adaptation?.gapPressure ?: 0.0) >= .55) {
            val share = when (lead?.structureTreatment) {
                StructureTreatment.PRESERVE -> 0.0
                StructureTreatment.PRESERVE_CORE_REBALANCE -> minOf(.35, .16 + .18 * lead.rotationPressure)
                StructureTreatment.PARTIAL_CONTINUITY -> minOf(.48, .28 + .20 * lead.rotationPressure)
                StructureTreatment.ROTATE_EMPHASIS -> minOf(.62, .42 + .20 * lead.rotationPressure)
                null -> 0.0
            }
            maxOf(2 * resistanceCandidates.size, (targetResistance * share).roundToInt()).coerceAtMost((targetResistance - continuityReserve).coerceAtLeast(0))
        } else 0
        val materialGap = resistanceCandidates.firstOrNull { it.priority == 90 }
        var capacityExpanded = false
        if (materialGap != null && gapBudget < 2 && baselineResistance < 4.0 && systemicDoseFactor >= .92) {
            val availableWithoutExpansion = (targetResistance - continuityReserve).coerceAtLeast(0)
            if (availableWithoutExpansion >= 2) {
                gapBudget = 2
            } else {
                targetResistance = continuityReserve + 2
                gapBudget = 2
                capacityExpanded = true
            }
        }
        val maxResistanceGapCount = gapBudget / 2
        if (resistanceCandidates.size > maxResistanceGapCount) resistanceCandidates = resistanceCandidates.take(maxResistanceGapCount)
        gapBudget = if (resistanceCandidates.isEmpty()) 0 else minOf(gapBudget, resistanceCandidates.size * maxOf(2, gapBudget / resistanceCandidates.size))
        val anchorBudget = (targetResistance - gapBudget).coerceAtLeast(if (state.anchors.isEmpty()) 0 else 1)
        val anchorWeights = state.anchors.associate { anchor ->
            val weeks = (state.styleFeaturesByAnchor[anchor.stableKey]?.weeksObserved ?: 1).coerceAtLeast(1)
            val observedWeeklySets = anchor.sets.toDouble() / weeks
            val transition = transitions.getValue(anchor.stableKey)
            anchor.stableKey to maxOf(.20, observedWeeklySets) * maxOf(.25, transition.continuityScore) * transition.localDoseFactor
        }
        val allocations = allocateAnchors(state, transitions, anchorWeights, anchorBudget)
        val days = request.weeklyTrainingDays.coerceIn(2, 5)
        val continuity = continuityPlanner.select(state, transitions, allocations, days)
        val resistanceGapAllocations = proportionalAllocation(
            resistanceCandidates.associate { it.stableKey to if (it.priority == 90) 1.4 else 1.0 },
            gapBudget,
            minimum = 2
        )
        val resistanceGapItems = resistanceCandidates.map { it.copy(targetSets = resistanceGapAllocations.getOrDefault(it.stableKey, 2)) }
        val performanceItems = performanceCandidates
        val selected = continuity + resistanceGapItems + performanceItems
        require(selected.isNotEmpty()) { "신뢰할 수 있는 연속성 운동이나 reviewed 후보를 찾지 못했습니다." }
        val plannedDays = weeklyDosePlanner.chooseDays(state, selected.size)
        val logical = structurePlanner.distribute(selected, days, request.sessionMinutes, state.genericCourtLoad)
        val schedule = ProgramDaySelector.defaultSchedule(horizon, days)
        val retentionPriorities = mutableMapOf<String, Int>()
        val items = buildList {
            (1..horizon).forEach { week ->
                val weekDays = schedule.getValue(week).sorted()
                logical.forEach { (logicalDay, rows) ->
                    rows.forEachIndexed { index, item ->
                        val exercise = snapshot.exercises.getValue(item.stableKey)
                        val meta = snapshot.metadata[item.stableKey]
                        val rx = prescriptionPlanner.prescribe(snapshot, state.strengthIntent, item, item.style)
                        val scalar = rx.sets.first()
                        val estimatedSeconds = rx.sets.sumOf { set -> if (set.seconds > 0) set.seconds else 45 } + (rx.sets.size - 1).coerceAtLeast(0) * rx.restSeconds
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
            items = items, weekDaySchedule = schedule, warnings = intent.constraints, optimizationSummary = ProgramOptimizationSummary(), templateId = "RECORD_BASED_PERSONALIZED_V0110", representativeTemplate = false,
            personalizedDecision = null
        )
        val repaired = repairPolicy.repair(rawSkeleton, validator.errors(rawSkeleton, state.genericCourtLoad), retentionPriorities, state.genericCourtLoad)
        val remaining = validator.errors(repaired, state.genericCourtLoad)
        require(remaining.isEmpty()) { remaining.joinToString(" ") }
        val firstWeek = repaired.items.filter { it.weekNumber == 1 }
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
            plannedAthleticPerformanceBouts = plannedAthleticBouts
        )
        val fingerprint = personalizedProgramFingerprint(repaired.request, repaired.items)
        val decision = PersonalizedPlanningDecision(
            decisionId = UUID.randomUUID().toString(), protocolVersion = PERSONALIZED_PLANNER_PROTOCOL, generatedAtEpochMillis = System.currentTimeMillis(), historyCutoff = snapshot.cutoff.toString(), historyWindowDays = minOf(56, state.historyDays),
            planningHorizonWeeks = horizon, adaptationIntentMinWeeks = intent.adaptationMinWeeks, adaptationIntentMaxWeeks = intent.adaptationMaxWeeks, observedTrainingBehavior = state.observedBehavior.name,
            strengthIntent = state.strengthIntent.name, strengthIntentProvenance = if (snapshot.preferences.strengthIntent != null || QUESTION_STRENGTH_INTENT in answers.values) "EXPLICIT_USER" else "INFERRED_OR_UNRESOLVED",
            badmintonIntent = state.badmintonIntent.name, badmintonIntentProvenance = if (snapshot.preferences.badmintonIntent != null || QUESTION_BADMINTON_INTENT in answers.values) "EXPLICIT_USER" else "PROFILE_OR_UNRESOLVED",
            primaryAdaptation = intent.primary, secondaryTargets = gaps.map(AdaptationGap::code), strengthStyle = state.observedStrengthStyle.name, strengthStyleProvenance = "OBSERVED_HISTORY_ONLY", weeklyFrequency = days,
            confidence = state.confidence.name, reasonCodes = intent.reasonCodes + listOf("DOSE_RECOMMENDED_${plannedDays}_REQUESTED_${days}", "WEEKLY_COURT_LOAD_NORMALIZED", "SEPARATE_RESISTANCE_DRILL_BUDGETS") + unresolvedPerformancePrescriptionCodes(snapshot, gaps) + if (capacityExpanded) listOf("MINIMAL_CAPACITY_EXPANSION") else emptyList(), reasons = intent.reasons, constraints = intent.constraints, metadataAuthorityVersion = PERSONALIZED_AUTHORITY_VERSION, priorDecisionId = priorDecisionId, userAnswers = answers.values,
            originalGenerationFingerprint = fingerprint, recoverySignalCodes = state.recoverySignals.sourceCodes.sorted(), genericCourtLoad = state.genericCourtLoad, objectiveExposure = state.objectiveExposure,
            anchorTransitions = transitions.values.sortedBy(AnchorTransition::stableKey), planningBudget = budget,
            movementRepresentations = state.movementRepresentations,
            badmintonObjectiveRepresentations = state.badmintonObjectiveRepresentations,
            adaptationGaps = gaps
        )
        return repaired.copy(personalizedDecision = decision)
    }

    private fun allocateAnchors(
        state: AthletePlanningState,
        transitions: Map<String, AnchorTransition>,
        weights: Map<String, Double>,
        total: Int
    ): Map<String, Int> {
        if (state.anchors.isEmpty() || total <= 0) return emptyMap()
        val treatmentRank = mapOf(
            StructureTreatment.PRESERVE to 0,
            StructureTreatment.PRESERVE_CORE_REBALANCE to 1,
            StructureTreatment.PARTIAL_CONTINUITY to 2,
            StructureTreatment.ROTATE_EMPHASIS to 3
        )
        val ordered = state.anchors.sortedWith(
            compareBy<UserAnchor> { treatmentRank.getValue(transitions.getValue(it.stableKey).structureTreatment) }
                .thenByDescending { transitions.getValue(it.stableKey).continuityScore }
                .thenByDescending { transitions.getValue(it.stableKey).localDoseFactor }
                .thenByDescending(UserAnchor::score)
                .thenBy(UserAnchor::stableKey)
        )
        val retained = ordered.take(minOf(total, ordered.size)).map(UserAnchor::stableKey).toSet()
        return proportionalAllocation(weights.filterKeys { it in retained }, total)
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

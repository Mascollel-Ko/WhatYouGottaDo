package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramDaySelector
import com.training.trackplanner.data.ProgramOptimizationSummary
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.round

data class PlannedExercise(
    val stableKey: String,
    val role: String,
    val reason: String,
    val priority: Int,
    val styleVariant: String = "",
    val style: StrengthProgrammingStyle = StrengthProgrammingStyle.NONE
)

class ExerciseContinuityPlanner {
    fun select(state: AthletePlanningState, style: StrengthProgrammingStyle): List<PlannedExercise> {
        val base = state.anchors.map { anchor ->
            val anchorStyle = anchor.style.takeIf { anchor.styleConfidence != PlanningConfidence.LOW } ?: StrengthProgrammingStyle.NONE
            PlannedExercise(anchor.stableKey, "CONTINUITY_${anchor.movementGroup}", "${anchor.sessions}회 세션과 ${anchor.sets}개 완료 세트가 있는 사용자 연속성 운동입니다.", 50, style = anchorStyle)
        }
        val expandable = base.firstOrNull { it.style in setOf(StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING, StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM, StrengthProgrammingStyle.DUP_LIKE_UNDULATING) }
            ?: base.firstOrNull { style in setOf(StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING, StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM, StrengthProgrammingStyle.DUP_LIKE_UNDULATING) }
                ?.copy(style = style)
            ?: return base
        val variants = if (expandable.style == StrengthProgrammingStyle.DUP_LIKE_UNDULATING) listOf("VOLUME", "STRENGTH", "MODERATE") else listOf("MEDIUM", "LIGHT", "HEAVY")
        return base.filterNot { it.stableKey == expandable.stableKey } + variants.map { expandable.copy(role = "STYLE_${it}_${expandable.role.removePrefix("CONTINUITY_")}", priority = 70, styleVariant = it) }
    }
}

class GapCandidateSelector {
    fun select(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, gaps: List<AdaptationGap>, used: Set<String>): List<PlannedExercise> {
        val chosen = used.toMutableSet()
        return gaps.mapNotNull { gap ->
            if (gap.code == "BADMINTON_FOUNDATIONAL_ONRAMP" && state.badmintonIntent != BadmintonPlanningIntent.ENABLED) return@mapNotNull null
            val historyKeys = snapshot.allConfirmedSets.mapTo(mutableSetOf(), PlanningSetRecord::stableKey)
            val objective = gap.code.substringAfter("BADMINTON_DROP_", "").ifBlank { gap.code.substringAfter("BADMINTON_DEVELOP_", "") }
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
            val candidates = snapshot.exercises.keys.asSequence()
                .filter { key -> key !in chosen && snapshot.metadata[key]?.planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE") }
                .filter { key ->
                    when {
                        gap.code == "BADMINTON_FOUNDATIONAL_ONRAMP" -> snapshot.activityKind(key) == PlannedActivityKind.STRUCTURED_BADMINTON_DRILL
                        objective.isNotBlank() -> (snapshot.badmintonObjectives[key]?.get(objective) ?: 0.0) > 0.0 && !snapshot.isSportSession(key)
                        else -> snapshot.movementCoverage(key) in target
                    }
                }
                .filter { key -> state.freeWeightWillingness != FreeWeightWillingness.AVOID || !snapshot.isFreeWeight(key) || key in historyKeys }
                .sortedWith(compareByDescending<String> { it in historyKeys }.thenByDescending { snapshot.metadata[it]?.sourceConfidenceLevel == "HIGH" }.thenBy { it })
                .toList()
            val key = candidates.firstOrNull() ?: return@mapNotNull null
            chosen += key
            PlannedExercise(key, if (gap.code.startsWith("BADMINTON")) "BADMINTON_OBJECTIVE_$objective" else "COVERAGE_${gap.code}", "${gap.reason} canonical planning metadata로 ${snapshot.exercises.getValue(key).name}을 선택했습니다.", 80)
        }
    }
}

class WeeklyStructurePlanner {
    fun distribute(items: List<PlannedExercise>, weeklyDays: Int, sessionMinutes: Int = 60, genericCourtLoad: Double = 0.0): Map<Int, List<PlannedExercise>> {
        val buckets = (1..weeklyDays).associateWith { mutableListOf<PlannedExercise>() }
        val fixedStyle = items.filter { it.styleVariant.isNotBlank() }
        val normal = items.filter { it.styleVariant.isBlank() }
        fixedStyle.forEachIndexed { index, item -> buckets.getValue((index * (weeklyDays - 1) / (fixedStyle.size - 1).coerceAtLeast(1)) + 1).add(item) }
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
    fun prescribe(snapshot: PlanningHistorySnapshot, item: PlannedExercise, style: StrengthProgrammingStyle, week: Int): PlannedPrescription {
        val history = snapshot.allConfirmedSets.filter { it.stableKey == item.stableKey }
        val lastDate = history.maxOfOrNull(PlanningSetRecord::date)
        val last = history.filter { it.date == lastDate }.maxWithOrNull(compareBy<PlanningSetRecord> { it.weightKg }.thenBy { it.reps })
        if (last == null) {
            val timed = item.role.startsWith("BADMINTON_")
            val sets = List(if (timed) 3 else 3) { index -> ProgramSetPrescription(index + 1, if (timed) 0 else 8, 0.0, if (timed) 25 else 0) }
            return PlannedPrescription(if (timed) "25초 × 3세트 · RPE 5–6.5" else "8–12회 × 3세트 · RPE 6–8 (첫 세션에서 중량 확인)", sets, if (timed) 60 else 90, "PROVISIONAL_RPE_NO_INVENTED_LOAD")
        }
        val anchor = snapshot.canonicalStrengthSignals[item.stableKey]
        val recentSessions = history.groupBy(PlanningSetRecord::date).toSortedMap().values.toList().takeLast(2)
        val provenTwice = recentSessions.size == 2 && recentSessions.all { rows ->
            rows.any { it.weightKg == last.weightKg && it.reps >= last.reps && (it.rpe ?: 10.0) <= 8.0 }
        }
        val progression = when {
            item.stableKey in snapshot.recoverySignals.tissueRestrictedStableKeys || snapshot.recoverySignals.readinessStatus == "LIMITED" -> ProgressionDecision.REDUCE
            (anchor?.posteriorChangePercent ?: 0.0) < -2.0 -> ProgressionDecision.REVIEW
            provenTwice && (anchor?.posteriorChangePercent ?: 0.0) > 0.0 -> ProgressionDecision.ADVANCE
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
            StrengthProgrammingStyle.STRAIGHT_5X5 -> PlannedPrescription("${load.clean()} kg × 5회 × 5세트 · RPE 7–8.5 · $progressionLabel", straight(5, 5), 180, "CANONICAL_POSTERIOR_$progressionLabel")
            StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS -> PlannedPrescription("${load.clean()} kg × ${reps.coerceIn(3, 6)}회 × ${history.countLastSession()}세트 · RPE 7–8.5 · $progressionLabel", straight(history.countLastSession(), reps.coerceIn(3, 6)), 180, "CANONICAL_POSTERIOR_$progressionLabel")
            StrengthProgrammingStyle.TOP_SET_BACKOFF -> {
                val backoff = round(load * .9 * 2) / 2
                val sets = listOf(ProgramSetPrescription(1, reps.coerceIn(3, 6), load, 0), ProgramSetPrescription(2, (reps + 1).coerceAtLeast(6), backoff, 0), ProgramSetPrescription(3, (reps + 1).coerceAtLeast(6), backoff, 0))
                PlannedPrescription("Top ${load.clean()} kg × ${sets[0].reps}회; Backoff ${backoff.clean()} kg × ${sets[1].reps}회 × 2 · RPE 7.5–8.5 · $progressionLabel", sets, 180, "CANONICAL_POSTERIOR_$progressionLabel")
            }
            StrengthProgrammingStyle.TOP_SET_HYPERTROPHY -> PlannedPrescription("Top set ${load.clean()} kg × ${reps.coerceIn(6, 15)}회 · RPE 8–9 · $progressionLabel", straight(1, reps.coerceIn(6, 15)), 150, "CANONICAL_POSTERIOR_$progressionLabel")
            StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING, StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM -> {
                val factor = when (item.styleVariant) { "LIGHT" -> .80; "HEAVY" -> 1.0; else -> .90 }
                val target = round(load * factor * 2) / 2
                PlannedPrescription("${item.styleVariant} ${target.clean()} kg × ${if (item.styleVariant == "HEAVY") "3–5" else "5"}회 × 3세트 · $progressionLabel", straight(3, if (item.styleVariant == "HEAVY") minOf(reps, 4) else minOf(reps, 5), target), 180, "INCUMBENT_STYLE_$progressionLabel")
            }
            StrengthProgrammingStyle.DUP_LIKE_UNDULATING -> {
                val targetReps = when (item.styleVariant) { "VOLUME" -> 8; "STRENGTH" -> 5; else -> 6 }
                val safeReps = minOf(reps, targetReps)
                PlannedPrescription("${item.styleVariant} ${safeReps}회 × 3세트 · RPE 7–8.5 · $progressionLabel", straight(3, safeReps, if (item.styleVariant == "STRENGTH") load else 0.0), 150, if (item.styleVariant == "STRENGTH") "CANONICAL_POSTERIOR_$progressionLabel" else "RPE_LOAD_FINDING")
            }
            else -> {
                val targetReps = reps
                val count = history.countLastSession().coerceIn(2, 4)
                PlannedPrescription("${if (load > 0) "${load.clean()} kg × " else ""}$targetReps 회 × $count 세트 · RPE 7–8.5 · $progressionLabel", straight(count, targetReps), 120, "CANONICAL_POSTERIOR_$progressionLabel")
            }
        }
    }
}

class ProgramProjectionValidator {
    fun errors(skeleton: GeneratedProgramSkeleton): List<String> = buildList {
        if (skeleton.items.any { it.exerciseStableKey in setOf("ex_ae9ecdbc", "ex_badminton_lesson") }) add("일반 배드민턴 세션은 프로그램 항목으로 생성할 수 없습니다.")
        if ((1..skeleton.request.durationWeeks).any { week -> skeleton.items.none { it.weekNumber == week } }) add("선택된 계획 기간이 모두 생성되지 않았습니다.")
        if (skeleton.items.groupBy { it.weekNumber to it.dayOfWeek }.any { it.value.size > 5 }) add("하루 운동 수가 안전한 편집 한도를 넘었습니다.")
        if ((skeleton.personalizedDecision?.genericCourtLoad ?: 0.0) >= 180.0 && skeleton.items.groupBy { it.weekNumber to it.dayOfWeek }.any { it.value.size > 4 }) add("높은 코트 부하에 비해 하루 운동 수가 많습니다.")
        if (skeleton.items.groupBy { it.weekNumber to it.dayOfWeek }.any { (_, rows) -> rows.sumOf { it.estimatedDurationSeconds } > skeleton.request.sessionMinutes * 60 }) add("예상 세션 시간이 사용 가능한 시간을 넘었습니다.")
        if (skeleton.request.weeklyTrainingDays !in 2..5) add("기록 기반 계획 빈도는 주 2~5일이어야 합니다.")
        if (skeleton.items.any { it.exerciseStableKey.isBlank() }) add("canonical stableKey가 없는 운동이 있습니다.")
    }
}

class ProgramRepairPolicy {
    fun repair(skeleton: GeneratedProgramSkeleton, errors: List<String>): GeneratedProgramSkeleton {
        if (errors.isEmpty()) return skeleton
        val secondsLimit = skeleton.request.sessionMinutes * 60
        val itemLimit = if ((skeleton.personalizedDecision?.genericCourtLoad ?: 0.0) >= 180.0) 4 else 5
        val reduced = skeleton.items
            .groupBy { it.weekNumber to it.dayOfWeek }
            .values
            .flatMap { day ->
                var used = 0
                day.sortedBy(ProgramSkeletonItem::orderIndex).filter { item ->
                    val fits = used + item.estimatedDurationSeconds <= secondsLimit
                    if (fits) used += item.estimatedDurationSeconds
                    fits
                }.take(itemLimit)
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
        val continuity = continuityPlanner.select(state, intent.selectedStyle)
        val selected = continuity + candidateSelector.select(snapshot, state, gaps, continuity.mapTo(mutableSetOf(), PlannedExercise::stableKey))
        require(selected.isNotEmpty()) { "신뢰할 수 있는 연속성 운동이나 reviewed 후보를 찾지 못했습니다." }
        val plannedDays = weeklyDosePlanner.chooseDays(state, selected.size)
        val days = request.weeklyTrainingDays.coerceIn(2, 5)
        val logical = structurePlanner.distribute(selected, days, request.sessionMinutes, state.genericCourtLoad)
        val schedule = ProgramDaySelector.defaultSchedule(horizon, days)
        val items = buildList {
            (1..horizon).forEach { week ->
                val weekDays = schedule.getValue(week).sorted()
                logical.forEach { (logicalDay, rows) ->
                    rows.forEachIndexed { index, item ->
                        val exercise = snapshot.exercises.getValue(item.stableKey)
                        val meta = snapshot.metadata[item.stableKey]
                        val rx = prescriptionPlanner.prescribe(snapshot, item, item.style.takeIf { it != StrengthProgrammingStyle.NONE } ?: intent.selectedStyle.takeIf { item.styleVariant.isNotBlank() } ?: StrengthProgrammingStyle.NONE, week)
                        val scalar = rx.sets.first()
                        val estimatedSeconds = rx.sets.sumOf { set -> if (set.seconds > 0) set.seconds else 45 } + (rx.sets.size - 1).coerceAtLeast(0) * rx.restSeconds
                        add(ProgramSkeletonItem(
                            localId = "personalized_${week}_${logicalDay}_${index}_${item.stableKey}", weekNumber = week, dayOfWeek = weekDays[logicalDay - 1], orderIndex = index + 1,
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
        val fingerprint = personalizedProgramFingerprint(request, items)
        val decision = PersonalizedPlanningDecision(
            decisionId = UUID.randomUUID().toString(), protocolVersion = PERSONALIZED_PLANNER_PROTOCOL, generatedAtEpochMillis = System.currentTimeMillis(), historyCutoff = snapshot.cutoff.toString(), historyWindowDays = minOf(56, state.historyDays),
            planningHorizonWeeks = horizon, adaptationIntentMinWeeks = intent.adaptationMinWeeks, adaptationIntentMaxWeeks = intent.adaptationMaxWeeks, observedTrainingBehavior = state.observedBehavior.name,
            strengthIntent = state.strengthIntent.name, strengthIntentProvenance = if (snapshot.preferences.strengthIntent != null || QUESTION_STRENGTH_INTENT in answers.values) "EXPLICIT_USER" else "INFERRED_OR_UNRESOLVED",
            badmintonIntent = state.badmintonIntent.name, badmintonIntentProvenance = if (snapshot.preferences.badmintonIntent != null || QUESTION_BADMINTON_INTENT in answers.values) "EXPLICIT_USER" else "PROFILE_OR_UNRESOLVED",
            primaryAdaptation = intent.primary, secondaryTargets = gaps.map(AdaptationGap::code), strengthStyle = intent.selectedStyle.name, strengthStyleProvenance = intent.styleProvenance, weeklyFrequency = days,
            confidence = state.confidence.name, reasonCodes = intent.reasonCodes + "DOSE_RECOMMENDED_${plannedDays}_REQUESTED_${days}", reasons = intent.reasons, constraints = intent.constraints, metadataAuthorityVersion = PERSONALIZED_AUTHORITY_VERSION, priorDecisionId = priorDecisionId, userAnswers = answers.values,
            originalGenerationFingerprint = fingerprint, recoverySignalCodes = state.recoverySignals.sourceCodes.sorted(), genericCourtLoad = state.genericCourtLoad, objectiveExposure = state.objectiveExposure
        )
        val skeleton = GeneratedProgramSkeleton(
            suggestedName = request.name, durationDays = horizon * 7, request = request, periodizationType = request.periodizationType,
            weekPlans = (1..horizon).map { ProgramWeekPlan(it, "PERSONALIZED_REVIEW", 1.0, 1.0, 2, 8.0, 2, if (state.badmintonIntent == BadmintonPlanningIntent.ENABLED) 1 else 0, false, 6.0, 8.5) },
            items = items, weekDaySchedule = schedule, warnings = intent.constraints, optimizationSummary = ProgramOptimizationSummary(), templateId = "RECORD_BASED_PERSONALIZED_V08", representativeTemplate = false,
            personalizedDecision = decision
        )
        val repaired = repairPolicy.repair(skeleton, validator.errors(skeleton))
        val remaining = validator.errors(repaired)
        require(remaining.isEmpty()) { remaining.joinToString(" ") }
        return repaired
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

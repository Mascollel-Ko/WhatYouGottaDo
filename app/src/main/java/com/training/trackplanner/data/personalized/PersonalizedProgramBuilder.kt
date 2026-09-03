package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramDaySelector
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramOptimizationSummary
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import java.time.LocalDate
import java.util.UUID
import kotlin.math.round

data class PlannedExercise(
    val stableKey: String,
    val role: String,
    val reason: String,
    val priority: Int,
    val styleVariant: String = ""
)

class ExerciseContinuityPlanner {
    fun select(state: AthletePlanningState, style: StrengthProgrammingStyle): List<PlannedExercise> {
        val base = state.anchors.map { PlannedExercise(it.stableKey, "CONTINUITY_${it.movementGroup}", "${it.sessions}회 세션과 ${it.sets}개 완료 세트가 있는 사용자 연속성 운동입니다.", 50) }
        if (style !in setOf(StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING, StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM, StrengthProgrammingStyle.DUP_LIKE_UNDULATING)) return base
        val anchor = base.firstOrNull { !it.role.contains("ISOLATION") } ?: return base
        val variants = if (style == StrengthProgrammingStyle.DUP_LIKE_UNDULATING) listOf("VOLUME", "STRENGTH", "MODERATE") else listOf("MEDIUM", "LIGHT", "HEAVY")
        return base.filterNot { it.stableKey == anchor.stableKey } + variants.map { anchor.copy(role = "STYLE_${it}_${anchor.role.removePrefix("CONTINUITY_")}", priority = 70, styleVariant = it) }
    }
}

class GapCandidateSelector {
    private val reviewed = mapOf(
        "LOWER_KNEE" to listOf("ex_ab468462", "ex_cb3c4dc2", "ex_e9e97659"),
        "POSTERIOR_CHAIN" to listOf("ex_2822ec2e", "ex_389920ab"),
        "HORIZONTAL_PUSH" to listOf("ex_1dbee10e", "ex_28902b13"),
        "UPPER_PULL" to listOf("ex_dc9e5953", "ex_33d2da8e", "ex_30a0e9aa"),
        "CORE_DIRECT" to listOf("ex_d5bdffe1", "cable_pallof_press"),
        "CALVES" to listOf("standing_calf_raise_machine", "standing_bodyweight_calf_raise"),
        "ARMS_BICEPS" to listOf("ex_281347da"),
        "ARMS_TRICEPS" to listOf("ex_d20b7487"),
        "BADMINTON_FOUNDATIONAL_ONRAMP" to listOf("ex_216351a1")
    )

    fun select(snapshot: PlanningHistorySnapshot, state: AthletePlanningState, gaps: List<AdaptationGap>, used: Set<String>): List<PlannedExercise> = gaps.mapNotNull { gap ->
        if (gap.code == "BADMINTON_FOUNDATIONAL_ONRAMP" && state.badmintonIntent != BadmintonPlanningIntent.ENABLED) return@mapNotNull null
        val historyKeys = snapshot.allConfirmedSets.mapTo(mutableSetOf(), PlanningSetRecord::stableKey)
        val key = reviewed[gap.code].orEmpty().firstOrNull { it !in used && it in snapshot.exercises && (it in historyKeys || snapshot.exercises.getValue(it).planningEligibility in setOf("PROGRAM_SELECTABLE", "SELECTABLE")) } ?: return@mapNotNull null
        PlannedExercise(key, if (gap.code.startsWith("BADMINTON")) "BADMINTON_ONRAMP_FOUNDATIONAL" else "COVERAGE_${gap.code}", gap.reason + " reviewed stableKey authority로 ${snapshot.exercises.getValue(key).name}을 선택했습니다.", 80)
    }
}

class WeeklyStructurePlanner {
    fun distribute(items: List<PlannedExercise>, weeklyDays: Int): Map<Int, List<PlannedExercise>> {
        val buckets = (1..weeklyDays).associateWith { mutableListOf<PlannedExercise>() }
        val fixedStyle = items.filter { it.styleVariant.isNotBlank() }
        val normal = items.filter { it.styleVariant.isBlank() }
        fixedStyle.forEachIndexed { index, item -> buckets.getValue((index * (weeklyDays - 1) / (fixedStyle.size - 1).coerceAtLeast(1)) + 1).add(item) }
        normal.sortedWith(compareByDescending<PlannedExercise> { it.priority }.thenBy(PlannedExercise::stableKey)).forEach { item ->
            val target = buckets.entries.filter { it.value.size < 5 }.minByOrNull { (_, rows) -> rows.size + rows.count { it.role.contains("LOWER") || it.role.contains("POSTERIOR") } * if (item.role.contains("LOWER") || item.role.contains("POSTERIOR")) 3 else 0 }
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
        val last = history.maxByOrNull(PlanningSetRecord::date)
        if (last == null) {
            val timed = item.role.startsWith("BADMINTON_")
            val sets = List(if (timed) 3 else 3) { index -> ProgramSetPrescription(index + 1, if (timed) 0 else 8, 0.0, if (timed) 25 else 0) }
            return PlannedPrescription(if (timed) "25초 × 3세트 · RPE 5–6.5" else "8–12회 × 3세트 · RPE 6–8 (첫 세션에서 중량 확인)", sets, if (timed) 60 else 90, "PROVISIONAL_RPE_NO_INVENTED_LOAD")
        }
        val load = last.weightKg
        val reps = last.reps.coerceAtLeast(1)
        fun straight(count: Int, targetReps: Int = reps, targetLoad: Double = load) = List(count) { ProgramSetPrescription(it + 1, targetReps, targetLoad, 0) }
        return when (style) {
            StrengthProgrammingStyle.STRAIGHT_5X5 -> PlannedPrescription("${load.clean()} kg × 5회 × 5세트 · RPE 7–8.5", straight(5, 5), 180, "LAST_PROVEN_LOAD")
            StrengthProgrammingStyle.STRAIGHT_STRENGTH_SETS -> PlannedPrescription("${load.clean()} kg × ${reps.coerceIn(3, 6)}회 × ${history.countLastSession()}세트 · RPE 7–8.5", straight(history.countLastSession(), reps.coerceIn(3, 6)), 180, "LAST_PROVEN_LOAD")
            StrengthProgrammingStyle.TOP_SET_BACKOFF -> {
                val backoff = round(load * .9 * 2) / 2
                val sets = listOf(ProgramSetPrescription(1, reps.coerceIn(3, 6), load, 0), ProgramSetPrescription(2, (reps + 1).coerceAtLeast(6), backoff, 0), ProgramSetPrescription(3, (reps + 1).coerceAtLeast(6), backoff, 0))
                PlannedPrescription("Top ${load.clean()} kg × ${sets[0].reps}회; Backoff ${backoff.clean()} kg × ${sets[1].reps}회 × 2 · RPE 7.5–8.5", sets, 180, "LAST_PROVEN_TOP_SET")
            }
            StrengthProgrammingStyle.TOP_SET_HYPERTROPHY -> PlannedPrescription("Top set ${load.clean()} kg × ${reps.coerceIn(6, 15)}회 · RPE 8–9", straight(1, reps.coerceIn(6, 15)), 150, "LAST_PROVEN_LOAD")
            StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING, StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM -> {
                val factor = when (item.styleVariant) { "LIGHT" -> .80; "HEAVY" -> 1.0; else -> .90 }
                val target = round(load * factor * 2) / 2
                PlannedPrescription("${item.styleVariant} ${target.clean()} kg × ${if (item.styleVariant == "HEAVY") "3–5" else "5"}회 × 3세트", straight(3, if (item.styleVariant == "HEAVY") 4 else 5, target), 180, "INCUMBENT_STYLE_LAST_LOAD")
            }
            StrengthProgrammingStyle.DUP_LIKE_UNDULATING -> {
                val targetReps = when (item.styleVariant) { "VOLUME" -> 8; "STRENGTH" -> 5; else -> 6 }
                PlannedPrescription("${item.styleVariant} ${targetReps}회 × 3세트 · RPE 7–8.5", straight(3, targetReps, if (item.styleVariant == "STRENGTH") load else 0.0), 150, if (item.styleVariant == "STRENGTH") "LAST_PROVEN_LOAD" else "RPE_LOAD_FINDING")
            }
            else -> {
                val targetReps = if (week >= 3) (reps + 1).coerceAtMost(15) else reps.coerceIn(6, 15)
                val count = history.countLastSession().coerceIn(2, 4)
                PlannedPrescription("${if (load > 0) "${load.clean()} kg × " else ""}$targetReps 회 × $count 세트 · RPE 7–8.5", straight(count, targetReps), 120, "LAST_PROVEN_LOAD_REP_PROGRESSION")
            }
        }
    }
}

class ProgramProjectionValidator {
    fun errors(skeleton: GeneratedProgramSkeleton): List<String> = buildList {
        if (skeleton.items.any { it.exerciseStableKey in setOf("ex_ae9ecdbc", "ex_badminton_lesson") }) add("일반 배드민턴 세션은 프로그램 항목으로 생성할 수 없습니다.")
        if ((1..skeleton.request.durationWeeks).any { week -> skeleton.items.none { it.weekNumber == week } }) add("선택된 계획 기간이 모두 생성되지 않았습니다.")
        if (skeleton.items.groupBy { it.weekNumber to it.dayOfWeek }.any { it.value.size > 5 }) add("하루 운동 수가 안전한 편집 한도를 넘었습니다.")
        if (skeleton.items.any { it.exerciseStableKey.isBlank() }) add("canonical stableKey가 없는 운동이 있습니다.")
    }
}

class ProgramRepairPolicy {
    fun repair(skeleton: GeneratedProgramSkeleton, errors: List<String>): GeneratedProgramSkeleton {
        if (errors.isEmpty() || skeleton.items.groupBy { it.weekNumber to it.dayOfWeek }.none { it.value.size > 5 }) return skeleton
        val reduced = skeleton.items
            .groupBy { it.weekNumber to it.dayOfWeek }
            .values
            .flatMap { day -> day.sortedBy(ProgramSkeletonItem::orderIndex).take(5) }
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
        val days = weeklyDosePlanner.chooseDays(state, selected.size)
        val logical = structurePlanner.distribute(selected, days)
        val schedule = ProgramDaySelector.defaultSchedule(horizon, days)
        val items = buildList {
            (1..horizon).forEach { week ->
                val weekDays = schedule.getValue(week).sorted()
                logical.forEach { (logicalDay, rows) ->
                    rows.forEachIndexed { index, item ->
                        val exercise = snapshot.exercises.getValue(item.stableKey)
                        val meta = snapshot.metadata[item.stableKey]
                        val rx = prescriptionPlanner.prescribe(snapshot, item, if (item.stableKey == state.anchors.firstOrNull { !it.movementGroup.contains("ISOLATION") }?.stableKey) intent.selectedStyle else StrengthProgrammingStyle.NONE, week)
                        val scalar = rx.sets.first()
                        add(ProgramSkeletonItem(
                            localId = "personalized_${week}_${logicalDay}_${index}_${item.stableKey}", weekNumber = week, dayOfWeek = weekDays[logicalDay - 1], orderIndex = index + 1,
                            exerciseStableKey = item.stableKey, exerciseName = exercise.name, category = exercise.category, restSeconds = rx.restSeconds, prescription = rx.text,
                            setCount = rx.sets.size, reps = scalar.reps, weightKg = scalar.weightKg, seconds = scalar.seconds, selectionReason = item.reason, weightSource = rx.weightSource,
                            trainingSlot = item.role, stableKey = item.stableKey, selectionRole = item.role, movementFamily = meta?.movementFamily.orEmpty(), movementSubtype = meta?.movementSubtype.orEmpty(),
                            metadataProgramSlot = meta?.programSlot.orEmpty(), redundancyGroup = meta?.redundancyGroup.orEmpty(), strengthProgressionGroup = meta?.strengthProgressionGroup.orEmpty(),
                            primaryStressProfile = meta?.primaryStressProfile.orEmpty(), stressMagnitudeHint = meta?.stressMagnitudeHint.orEmpty(), neuromuscularStressLevel = meta?.neuromuscularStressLevel.orEmpty(),
                            systemicMuscularStressLevel = meta?.systemicMuscularStressLevel.orEmpty(), localMuscularStressLevel = meta?.localMuscularStressLevel.orEmpty(), jointTendonImpactStressLevel = meta?.jointTendonImpactStressLevel.orEmpty(),
                            movementFocusDemandLevel = meta?.movementFocusDemandLevel.orEmpty(), recoveryDurationClass = meta?.recoveryDurationClass.orEmpty(), badmintonTransferLevel = meta?.badmintonTransferLevel.orEmpty(), setPrescriptions = rx.sets
                        ))
                    }
                }
            }
        }
        val decision = PersonalizedPlanningDecision(
            decisionId = UUID.randomUUID().toString(), protocolVersion = PERSONALIZED_PLANNER_PROTOCOL, generatedAtEpochMillis = System.currentTimeMillis(), historyCutoff = snapshot.cutoff.toString(), historyWindowDays = minOf(56, state.historyDays),
            planningHorizonWeeks = horizon, adaptationIntentMinWeeks = intent.adaptationMinWeeks, adaptationIntentMaxWeeks = intent.adaptationMaxWeeks, observedTrainingBehavior = state.observedBehavior.name,
            strengthIntent = state.strengthIntent.name, strengthIntentProvenance = if (snapshot.preferences.strengthIntent != null || QUESTION_STRENGTH_INTENT in answers.values) "EXPLICIT_USER" else "INFERRED_OR_UNRESOLVED",
            badmintonIntent = state.badmintonIntent.name, badmintonIntentProvenance = if (snapshot.preferences.badmintonIntent != null || QUESTION_BADMINTON_INTENT in answers.values) "EXPLICIT_USER" else "PROFILE_OR_UNRESOLVED",
            primaryAdaptation = intent.primary, secondaryTargets = gaps.map(AdaptationGap::code), strengthStyle = intent.selectedStyle.name, strengthStyleProvenance = intent.styleProvenance, weeklyFrequency = days,
            confidence = state.confidence.name, reasonCodes = intent.reasonCodes, reasons = intent.reasons, constraints = intent.constraints, metadataAuthorityVersion = PERSONALIZED_AUTHORITY_VERSION, priorDecisionId = priorDecisionId, userAnswers = answers.values
        )
        val normalizedRequest = request.copy(durationWeeks = horizon, weeklyTrainingDays = days, goal = if (state.badmintonIntent == BadmintonPlanningIntent.ENABLED) ProgramGoal.BADMINTON_SUPPORT else ProgramGoal.STRENGTH)
        val skeleton = GeneratedProgramSkeleton(
            suggestedName = request.name, durationDays = horizon * 7, request = normalizedRequest, periodizationType = ProgramPeriodizationType.AUTO,
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

private fun List<PlanningSetRecord>.countLastSession(): Int {
    val date = maxOf(PlanningSetRecord::date)
    return count { it.date == date }.coerceIn(1, 5)
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

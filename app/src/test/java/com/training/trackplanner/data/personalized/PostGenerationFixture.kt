package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import java.time.LocalDate

internal object PostGenerationFixture {
    val cutoff: LocalDate = LocalDate.of(2026, 9, 3)
    fun snapshot(): PlanningHistorySnapshot {
        val slots = linkedMapOf("press" to "HORIZONTAL_PUSH_STRENGTH_OR_ACCESSORY", "row" to "HORIZONTAL_PULL_STRENGTH",
            "squat" to "MAIN_LOWER_STRENGTH", "hinge" to "MAIN_HINGE_STRENGTH", "support" to "CORE_STABILITY_ACCESSORY",
            "direct" to "BADMINTON_FOOTWORK", "other" to "BICEPS_ACCESSORY")
        val exercises = slots.keys.associateWith { Exercise(stableKey = it, name = it, category = "운동", equipment = "BODYWEIGHT") }
        return PlanningHistorySnapshot(cutoff, listOf(PlanningSetRecord(cutoff.minusDays(10), "press", "press", "운동", 1, 8, 10.0, 0, 8.0)),
            exercises, slots.mapValues { (key, slot) -> RuntimeExerciseMetadataDefaults.forExercise(exercises.getValue(key)).copy(
                activityKind = "EXERCISE", planningEligibility = "PROGRAM_SELECTABLE", programSlot = slot, progressMetricType = "LOAD_REPS",
                analysisEligibility = MetadataTokenField.parse(if (key == "direct") "FATIGUE|BADMINTON_TRANSFER" else "FATIGUE|STRENGTH_PROGRESS"),
                badmintonTransferLevel = if (key == "direct") "DIRECT" else "NONE") },
            mapOf("direct" to mapOf("REACTION" to 1.0), "support" to mapOf("REACTION" to .4)), "MIXED", 1.0, 1.0,
            PersonalizedPlanningPreferences(strengthIntent = StrengthIntent.MIXED, badmintonIntent = BadmintonPlanningIntent.ENABLED,
                freeWeightWillingness = FreeWeightWillingness.WILLING),
            badmintonDirectObjectives = mapOf("direct" to setOf("REACTION")), badmintonSupportiveObjectives = mapOf("support" to setOf("REACTION")),
            performancePrescriptions = mapOf("direct" to PerformancePrescriptionAuthority(List(3) { ProgramSetPrescription(it + 1, 0, 0.0, 20) },
                60, "exact", "CANONICAL_TEST")))
    }
    fun state(snapshot: PlanningHistorySnapshot = snapshot()) = AthletePlanningStateBuilder().build(snapshot, PersonalizedPlanningAnswers())
    fun source(key: String, sets: Int = 2, gap: String = "", priority: Int = 90, material: Boolean = true) =
        PlannedExercise(key, "fixture", "fixture", priority, targetSets = sets, representedGapCodes = setOf(gap).filter { it.isNotBlank() }.toSet(),
            representedObjectives = if (key == "direct") setOf("REACTION") else emptySet(),
            supportiveObjectives = if (key == "support") setOf("REACTION") else emptySet(), material = material)
    fun rx(sets: Int, seconds: Int = 45, rest: Int = 0) = PlannedPrescription("exact", List(sets) { ProgramSetPrescription(it + 1, 8, 0.0, seconds) }, rest, "TEST_EXACT")
    fun row(key: String, day: Int, sets: Int = 2, seconds: Int = 45, id: String = key, order: Int = 1) =
        residualItem(snapshot(), source(key, sets), rx(sets, seconds), id, day, order)
    fun plan(rows: List<ProgramSkeletonItem>, days: List<Int> = listOf(1, 3, 5), minutes: Int = 60, horizon: Int = 3): GeneratedProgramSkeleton {
        val request = ProgramSkeletonRequest("post", ProgramGoal.BODYBUILDING, days.size, minutes, emptySet(), "", .5, "AUTO", ProgramPeriodizationType.AUTO, horizon)
        return GeneratedProgramSkeleton("post", horizon * 7, request, request.periodizationType, emptyList(),
            (1..horizon).flatMap { week -> rows.map { it.copy(localId = "w${week}_${it.localId}", weekNumber = week) } },
            (1..horizon).associateWith { days.toSet() })
    }
    fun atoms(plan: GeneratedProgramSkeleton): Map<String, String> = plan.items.groupBy { it.weekNumber }.values.flatMap { rows ->
        rows.mapIndexed { index, row -> row.localId to "atom_$index" } }.toMap()
    fun envelope(units: Int = 100, observations: Int = 4, referenceUnits: Double = 10.0, referenceSeconds: Double = 1200.0) =
        WeeklyCapacityEnvelope(3, 60, 10800, observations, observations, referenceUnits, referenceUnits,
            referenceSeconds, 0.0, 0.0, 0.0, units, units, units)
    val safe = PlanDayProjection { StandaloneDayLoad(30, listOf(30)) }
    fun authorized(snapshot: PlanningHistorySnapshot, item: PlannedExercise, continuity: Boolean = false) =
        AuthorizedPrescription("auth_${item.stableKey}", item, PersonalizedPrescriptionPlanner().prescribe(snapshot, StrengthIntent.MIXED, item, item.style), continuity)
    fun complete(plan: GeneratedProgramSkeleton, authorized: List<AuthorizedPrescription>, gaps: List<AdaptationGap>,
        snapshot: PlanningHistorySnapshot = snapshot(), envelope: WeeklyCapacityEnvelope = envelope(), fixed: Boolean = true,
        projection: PlanDayProjection? = safe, atoms: Map<String, String> = atoms(plan)): CompletionResult =
        ResidualCompletion().complete(plan, snapshot, state(snapshot), gaps, authorized, envelope, atoms,
            plan.items.filter { it.weekNumber == 1 }.associate { atoms.getValue(it.localId) to source(it.exerciseStableKey, it.setCount) }, fixed, projection)
}

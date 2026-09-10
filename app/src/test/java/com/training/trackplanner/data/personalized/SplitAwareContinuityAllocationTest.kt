package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test

class SplitAwareContinuityAllocationTest {
    private val f = PostGenerationFixture
    private val snapshot = f.snapshot()
    private fun parent(count: Int, key: String = "press", style: StrengthProgrammingStyle = StrengthProgrammingStyle.NONE) =
        AuthorizedSchedulingDemand("parent_$key", f.source(key, count).copy(style = style), f.rx(count, 45, 90), true)
    private fun improve(parent: AuthorizedSchedulingDemand, days: Int = 2, minutes: Int = 20,
        baselineCount: Int = parent.prescription.sets.size, source: PlanningHistorySnapshot = snapshot): SplitAwareAllocation {
        val baseline = TimedExecutionAllocation((1..days).associateWith { day -> if (day == 1) listOf(TimedPlannedExercise(
            parent.item.copy(targetSets = baselineCount), parent.prescription.copy(sets = parent.prescription.sets.take(baselineCount)))) else emptyList() }, emptyList())
        return SplitAwareContinuityAllocation(PersonalizedPrescriptionPlanner()).improve(source, f.state(source), listOf(parent), baseline, days, minutes)
    }
    @Test fun `canonical table through nine conserves exact weekly volume`() {
        assertEquals(listOf(listOf(1), listOf(2), listOf(3), listOf(2, 2), listOf(3, 2), listOf(3, 3), listOf(3, 4), listOf(4, 4), listOf(3, 3, 3)),
            (1..9).map { ContinuitySplitPolicy.template(it) })
        assertEquals(listOf(4, 5), ContinuitySplitPolicy.template(9, 2))
        for (days in 2..5) for (count in 1..9) assertEquals(count, ContinuitySplitPolicy.template(count, days).sum())
    }
    @Test fun `four five six split preserve every weekly prescription field except local index`() {
        for (count in 4..6) {
            val parent = parent(count)
            val result = improve(parent)
            val atoms = result.days.values.flatten()
            assertEquals(ContinuitySplitPolicy.template(count), atoms.map { it.timed.prescription.sets.size }.sortedDescending())
            assertEquals(count, atoms.sumOf { it.timed.prescription.sets.size })
            assertEquals(parent.prescription.sets.map { it.copy(setIndex = 0) }, atoms.flatMap { it.timed.prescription.sets }.map { it.copy(setIndex = 0) })
            assertTrue(atoms.all { it.timed.prescription.restSeconds == parent.prescription.restSeconds && it.timed.prescription.weightSource == parent.prescription.weightSource })
            assertEquals(setOf(parent.id), atoms.map { it.origin.authorizedDemandId }.toSet())
            assertEquals(setOf(0, 1), atoms.map { it.origin.splitChunkIndex }.toSet())
            assertTrue(result.days.values.all { rows -> rows.map { it.timed.item.stableKey }.distinct().size == rows.size })
            assertEquals(result, improve(parent))
        }
    }
    @Test fun `full unsplit infeasible but canonical chunks feasible preserves full authorized coverage`() {
        val result = improve(parent(4), minutes = 4, baselineCount = 2)
        assertEquals(4, result.days.values.flatten().sumOf { it.timed.prescription.sets.size })
        assertEquals("SPLIT_FULL_AUTHORIZED_COVERAGE", result.trace.decisions.single().decision)
    }
    @Test fun `equal full coverage chooses split only with strictly smaller max day seconds`() {
        assertEquals("SPLIT_LOWER_MAX_DAY_SECONDS", improve(parent(5)).trace.decisions.single().decision)
        val first = parent(4)
        val blocker = parent(6, "row").copy(continuity = false)
        val baseline = TimedExecutionAllocation(mapOf(1 to listOf(TimedPlannedExercise(first.item, first.prescription)),
            2 to listOf(TimedPlannedExercise(blocker.item, blocker.prescription))), emptyList())
        val result = SplitAwareContinuityAllocation(PersonalizedPrescriptionPlanner()).improve(snapshot, f.state(), listOf(first, blocker), baseline, 2, 30)
        assertEquals("UNSPLIT_PREFERRED", result.trace.decisions.single().decision)
    }
    @Test fun `structured styles never split even when all loads happen to match`() {
        val forbidden = setOf(StrengthProgrammingStyle.TOP_SET_BACKOFF, StrengthProgrammingStyle.TOP_SET_HYPERTROPHY,
            StrengthProgrammingStyle.HEAVY_LIGHT_MEDIUM, StrengthProgrammingStyle.DUP_LIKE_UNDULATING,
            StrengthProgrammingStyle.MADCOW_LIKE_HLM_RAMPING, StrengthProgrammingStyle.UNRESOLVED)
        forbidden.forEach { style -> for (count in 5..9) assertFalse(ContinuitySplitPolicy.eligible(snapshot, parent(count, style = style))) }
        assertFalse(ContinuitySplitPolicy.eligible(snapshot, parent(4).let { it.copy(item = it.item.copy(styleVariant = "HEAVY")) }))
    }
    @Test fun `nonuniform ordered prescriptions and noncontinuity are ineligible`() {
        val parent = parent(5)
        assertFalse(ContinuitySplitPolicy.eligible(snapshot, parent.copy(continuity = false)))
        assertFalse(ContinuitySplitPolicy.eligible(snapshot, parent.copy(prescription = parent.prescription.copy(sets =
            parent.prescription.sets.mapIndexed { index, set -> set.copy(weightKg = index * 10.0) }))))
        val performance = snapshot.copy(metadata = snapshot.metadata.mapValues { (_, meta) -> meta.copy(activityKind = "STRUCTURED_BADMINTON_DRILL") })
        assertFalse(ContinuitySplitPolicy.eligible(performance, parent))
    }
    @Test fun `one to three preserve existing behavior`() {
        for (count in listOf(1, 2, 3)) {
            val result = improve(parent(count))
            assertEquals(1, result.days.values.flatten().size)
            assertEquals(count, result.days.values.flatten().sumOf { it.timed.prescription.sets.size })
        }
    }
    @Test fun `hard time blocked mandatory chunks retain authorization not noncanonical reduction`() {
        val result = improve(parent(6), minutes = 3, baselineCount = 2)
        assertEquals(0, result.days.values.flatten().sumOf { it.timed.prescription.sets.size })
        assertEquals(6, result.trace.authorized.single().prescription.sets.size)
        assertEquals("CANONICAL_PARTITION_HARD_PLACEMENT_SHORTFALL", result.trace.decisions.single().decision)
    }
    @Test fun `high sets split by default on distinct days even with unlimited session time`() {
        for (days in 2..5) for (count in 6..9) {
            val parent = parent(count).copy(fundingSource = PlanningFundingSource.USER_FREQUENCY_EXPANSION, originalRank = 12)
            val result = improve(parent, days = days, minutes = 90)
            val atoms = result.days.values.flatten()
            assertEquals(ContinuitySplitPolicy.template(count, days).sorted(), atoms.map { it.timed.prescription.sets.size }.sorted())
            assertEquals(atoms.size, result.days.count { it.value.isNotEmpty() })
            assertEquals(count, atoms.sumOf { it.timed.prescription.sets.size })
            assertTrue(atoms.all { it.origin.authorizedDemandId == parent.id && it.origin.splitGroupId == parent.id })
            assertEquals(parent, result.trace.authorized.single())
            assertEquals("CANONICAL_HIGH_SET_PARTITION", result.trace.decisions.single().decision)
        }
    }
    @Test fun `one scheduled day cannot split`() {
        assertEquals(1, improve(parent(4), days = 1).days.values.flatten().size)
    }
    @Test fun `approved high set split keeps all units and reports OFI as advisory only`() {
        val source = snapshot.copy(planDayProjection = PlanDayProjection { StandaloneDayLoad(99, listOf(100), listOf("TEST_CAUTION")) })
        val result = improve(parent(9), days = 3, minutes = 90, source = source)
        assertEquals(listOf(3, 3, 3), result.days.values.flatten().map { it.timed.prescription.sets.size })
        assertTrue(result.trace.decisions.single().ofiWarnings.isNotEmpty())
        assertTrue(result.trace.decisions.single().failureReasons.isEmpty())
        // The override does not extend to the existing conditional four/five-set path.
        assertEquals(2, improve(parent(4), baselineCount = 2, source = source).days.values.flatten().sumOf { it.timed.prescription.sets.size })
    }
    @Test fun `chronological tissue failure leaves exact canonical chunk shortfall`() {
        val source = snapshot.copy(planDayProjection = f.safe, planWeekTissueProjection = PlanWeekTissueProjection { rows, _ ->
            PlannedTissueWeek(listOf(PlannedTissueDay(5, "2026-09-11",
                if (rows.sumOf { it.setCount } > 6) setOf("canonical_fixture_unit") else emptySet(), emptySet(), null, null)))
        })
        val result = improve(parent(9), days = 3, minutes = 90, source = source)
        assertEquals(listOf(3, 3), result.days.values.flatten().map { it.timed.prescription.sets.size })
        assertEquals(9, result.trace.authorized.single().prescription.sets.size)
        assertTrue(SplitPlacementFailure.TISSUE_RECOVERY_CONSTRAINT in result.trace.decisions.single().failureReasons)
    }
    @Test fun `exclusions equipment and eligibility do not authorize extra continuity`() {
        val parent = parent(4)
        val baseline = TimedExecutionAllocation(mapOf(1 to listOf(TimedPlannedExercise(parent.item.copy(targetSets = 2),
            parent.prescription.copy(sets = parent.prescription.sets.take(2)))), 2 to emptyList()), emptyList())
        val request = f.plan(listOf(f.row("press", 1))).request
        val equipmentSource = snapshot.copy(exercises = snapshot.exercises.mapValues { (_, exercise) -> exercise.copy(equipment = "BARBELL") })
        val ineligible = snapshot.copy(metadata = snapshot.metadata.mapValues { (_, metadata) -> metadata.copy(planningEligibility = "HISTORY_ONLY") })
        for ((source, constraint) in listOf(snapshot to request.copy(excludedExerciseStableKeys = setOf("press")),
            equipmentSource to request.copy(availableEquipment = setOf("DUMBBELL")), ineligible to request)) {
            val result = SplitAwareContinuityAllocation(PersonalizedPrescriptionPlanner()).improve(source, f.state(source), listOf(parent), baseline, 2, 20, constraint)
            assertEquals(2, result.days.values.flatten().sumOf { it.timed.prescription.sets.size })
        }
    }
    @Test fun `canonical OFI and tissue gate cannot be bypassed by split`() {
        for (load in listOf(StandaloneDayLoad(87, listOf(0)), StandaloneDayLoad(30, listOf(100)))) {
            val result = improve(parent(4), baselineCount = 2, source = snapshot.copy(planDayProjection = PlanDayProjection { load }))
            assertEquals(2, result.days.values.flatten().sumOf { it.timed.prescription.sets.size })
        }
        val restricted = snapshot.copy(recoverySignals = PlanningRecoverySignals(tissueRestrictedStableKeys = setOf("press")))
        assertEquals(2, improve(parent(4), baselineCount = 2, source = restricted).days.values.flatten().sumOf { it.timed.prescription.sets.size })
    }
}

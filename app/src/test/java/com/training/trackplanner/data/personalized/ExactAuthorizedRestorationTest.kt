package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import org.junit.Assert.*
import org.junit.Test

class ExactAuthorizedRestorationTest {
    private val f = PostGenerationFixture
    private val snapshot = f.snapshot()
    private fun auth(n: Int, style: StrengthProgrammingStyle = StrengthProgrammingStyle.NONE) =
        AuthorizedPrescription("parent", f.source("squat", n, priority = 100).copy(style = style), f.rx(n, 45, 10), true)
    private fun run(parent: AuthorizedPrescription, initial: List<ProgramSkeletonItem>,
        origins: Map<String, AuthorizedAtomOrigin>, minutes: Int = 60, units: Int = 100,
        projection: PlanDayProjection = f.safe, source: PlanningHistorySnapshot = snapshot,
        days: List<Int> = listOf(1, 3, 5), extra: List<AuthorizedPrescription> = emptyList(), gaps: List<AdaptationGap> = emptyList(),
        fixed: Boolean = true, excluded: Set<String> = emptySet()): CompletionResult {
        val p = f.plan(initial, days, minutes).let { it.copy(request = it.request.copy(excludedExerciseStableKeys = excluded)) }
        return ResidualCompletion().complete(p, source, f.state(source), gaps, listOf(parent) + extra,
            f.envelope(units), f.atoms(p), emptyMap(), fixed, projection, origins)
    }
    private fun origin(chunk: Int? = null) = mapOf("atom_0" to AuthorizedAtomOrigin("parent", if (chunk == null) "" else "parent", chunk))
    private fun reduced(n: Int = 2) = f.row("squat", 1, n).copy(restSeconds = 10)

    @Test fun `continuity same movement does not own gap Q but supplies semantic C`() {
        val gap = AdaptationGap("LOWER_KNEE", "HIGH", "test")
        val material = auth(2).copy(id = "legpress", continuity = false, item = f.source("squat", 2, gap.code))
        val values = AuthorizedPlanningDemand(snapshot, listOf(auth(4), material), listOf(gap), emptyList())
            .residuals(listOf(reduced(4))).associateBy { it.id }
        assertEquals(4.0, values.getValue("CONTINUITY:squat:").requested, 0.0)
        assertEquals(2.0, values.getValue(gap.code).requested, 0.0)
        assertEquals(4.0, values.getValue(gap.code).coverage, 0.0)
    }
    @Test fun `multiple explicit gap codes keep independent ownership`() {
        val gaps = listOf(AdaptationGap("LOWER_KNEE", "HIGH", "a"), AdaptationGap("HYPERTROPHY_REBALANCE_LOWER_KNEE", "HIGH", "b"))
        val owner = auth(2).copy(continuity = false, item = f.source("squat", 2).copy(representedGapCodes = gaps.map { it.code }.toSet()))
        val d = AuthorizedPlanningDemand(snapshot, listOf(auth(4), owner), gaps, emptyList())
        assertTrue(d.residuals(emptyList()).filter { it.unit == PlanningDemandUnit.RESISTANCE_SETS }.all { it.requested == 2.0 })
    }
    @Test fun `merged objective ownership retains both explicit gap sources not incidental support`() {
        val gaps = listOf(AdaptationGap("BADMINTON_DROP_REACTION", "HIGH", "a"), AdaptationGap("BADMINTON_UNDERREPRESENTED_REACTION", "HIGH", "b"))
        val a = f.authorized(snapshot, f.source("direct", 3, gaps[0].code))
        val b = f.authorized(snapshot, f.source("support", 2, gaps[1].code))
        val incidental = f.authorized(snapshot, f.source("support", 4), true)
        val r = AuthorizedPlanningDemand(snapshot, listOf(a, b, incidental), gaps, emptyList()).residuals(emptyList())
        assertEquals(3.8, r.single { it.unit == PlanningDemandUnit.OBJECTIVE_EXPOSURE }.requested, 1e-9)
        assertEquals(3.0, r.single { it.unit == PlanningDemandUnit.DIRECT_SETS }.requested, 0.0)
    }
    @Test fun `4 missing 2 canonical chunk restored with exact parent identity`() {
        val result = run(auth(4), listOf(reduced()), origin(0))
        val exact = result.trace.exactShortfalls.single()
        assertEquals(2, exact.initialMaterialized); assertEquals(4, exact.materialized); assertEquals(0, exact.shortfall)
        assertEquals(listOf(2, 2), result.week!!.items.map { it.setCount })
        assertEquals(2, result.week!!.items.map { it.dayOfWeek }.distinct().size)
        assertEquals(1, result.trace.restorations.size); assertTrue(result.trace.additions.isEmpty())
    }
    @Test fun `fully materialized 4 5 6 chunks produce no exact shortfall`() {
        for (n in 4..6) {
            val chunks = ContinuitySplitPolicy.template(n)
            val result = run(auth(n), chunks.mapIndexed { i, c -> f.row("squat", 1 + i * 2, c, id = "s$i") },
                chunks.indices.associate { "atom_$it" to AuthorizedAtomOrigin("parent", "parent", it) })
            assertEquals(0, result.trace.exactShortfalls.single().shortfall)
            assertEquals(n, result.week!!.items.sumOf { it.setCount }); assertTrue(result.trace.restorations.isEmpty())
        }
    }
    @Test fun `missing first chunk of five retains unique identity and never becomes six`() {
        val result = run(auth(5), listOf(reduced()), origin(1))
        assertEquals(listOf(2, 3), result.week!!.items.map { it.setCount }.sorted())
        assertEquals(2, result.week!!.items.map { it.localId }.distinct().size)
        assertEquals(5, result.week!!.items.sumOf { it.setCount })
    }
    @Test fun `reduced three restored whole at current day before relocation`() {
        val result = run(auth(3), listOf(reduced()), origin())
        assertEquals(listOf(3), result.week!!.items.map { it.setCount })
        assertEquals(1, result.week!!.items.single().dayOfWeek)
        assertEquals("w1_squat", result.week!!.items.single().localId)
    }
    @Test fun `reduced three relocates whole when current day cannot fit`() {
        val result = run(auth(3), listOf(reduced(), f.row("press", 1, 1, 70)), origin() + ("atom_1" to AuthorizedAtomOrigin("other")), minutes = 3,
            extra = listOf(AuthorizedPrescription("other", f.source("press", 1), f.rx(1, 70), false)))
        val squat = result.week!!.items.single { it.exerciseStableKey == "squat" }
        assertEquals(3, squat.setCount); assertNotEquals(1, squat.dayOfWeek)
    }
    @Test fun `three never restored as two plus one if whole session exceeds hard time`() {
        val result = run(auth(3), listOf(reduced()), origin(), minutes = 2)
        assertEquals(listOf(2), result.week!!.items.map { it.setCount }); assertEquals(1, result.trace.exactShortfalls.single().shortfall)
        assertTrue(result.trace.additions.isEmpty())
    }
    @Test fun `structured top set remains atomic and exact load order survives`() {
        val parent = auth(4, StrengthProgrammingStyle.TOP_SET_BACKOFF).let { it.copy(prescription = it.prescription.copy(
            sets = it.prescription.sets.mapIndexed { i, set -> set.copy(weightKg = 50.0 - i * 5) })) }
        val blocked = run(parent, listOf(reduced()), origin(), minutes = 2)
        assertTrue(blocked.trace.restorations.isEmpty())
        val result = run(parent, listOf(reduced()), origin())
        assertEquals(parent.prescription.sets, result.week!!.items.single().setPrescriptions)
    }
    @Test fun `exact weekly unit ceiling cannot be bypassed`() {
        val result = run(auth(4), listOf(reduced()), origin(), units = 2)
        assertEquals(2, result.week!!.items.sumOf { it.setCount }); assertTrue(result.trace.restorations.isEmpty())
    }
    @Test fun `OFI87 and axis100 reject exact restoration`() {
        listOf(StandaloneDayLoad(87, listOf(0)), StandaloneDayLoad(0, listOf(100))).forEach { load ->
            assertTrue(run(auth(4), listOf(reduced()), origin(), projection = PlanDayProjection { load }).trace.restorations.isEmpty())
        }
    }
    @Test fun `tissue and explicit user exclusion remain hard gates`() {
        val restricted = snapshot.copy(recoverySignals = PlanningRecoverySignals(tissueRestrictedStableKeys = setOf("squat")))
        assertTrue(run(auth(4), listOf(reduced()), origin(), source = restricted).trace.restorations.isEmpty())
        assertTrue(run(auth(4), listOf(reduced()), origin(), excluded = setOf("squat")).trace.restorations.isEmpty())
    }
    @Test fun `unknown planning eligibility cannot restore`() {
        val changed = snapshot.copy(metadata = snapshot.metadata + ("squat" to snapshot.metadata.getValue("squat").copy(planningEligibility = "HISTORY_ONLY")))
        assertTrue(run(auth(4), listOf(reduced()), origin(), source = changed).trace.restorations.isEmpty())
    }
    @Test fun `no parent inference from same stableKey`() {
        val result = run(auth(4), listOf(reduced()), mapOf("atom_0" to AuthorizedAtomOrigin("another_parent")), units = 2)
        assertEquals(0, result.trace.exactShortfalls.single().initialMaterialized)
        assertEquals(4, result.trace.exactShortfalls.single().shortfall)
    }
    @Test fun `exact before semantic action and all horizon prescriptions mirror`() {
        val gap = AdaptationGap("POSTERIOR_CHAIN", "HIGH", "test")
        val alternative = f.authorized(snapshot, f.source("hinge", 2, gap.code))
        val result = run(auth(4), listOf(reduced()), origin(0), extra = listOf(alternative), gaps = listOf(gap))
        assertEquals(listOf("parent", alternative.id), result.trace.restorations.map { it.authorizedDemandId })
        assertTrue(result.trace.additions.isEmpty())
        assertEquals(3, result.skeleton.items.groupBy { it.weekNumber }.size)
        val content = result.skeleton.items.groupBy { it.weekNumber }.values.map { rows -> rows.map {
            it.copy(localId = "", weekNumber = 1) } }
        assertEquals(content[0], content[1]); assertEquals(content[1], content[2])
    }
    @Test fun `semantic alternatives may proceed after unrestoreable exact demand`() {
        val gap = AdaptationGap("BADMINTON_UNDERREPRESENTED_REACTION", "HIGH", "test")
        val parent = f.authorized(snapshot, f.source("direct", 3, gap.code))
        val result = run(parent, listOf(f.row("press", 1, 1)), emptyMap(),
            source = snapshot.copy(recoverySignals = PlanningRecoverySignals(tissueRestrictedStableKeys = setOf("direct"))), gaps = listOf(gap),
            extra = listOf(AuthorizedPrescription("press", f.source("press", 1), f.rx(1), true)))
        assertTrue(result.trace.restorations.none { it.authorizedDemandId == parent.id })
        assertTrue(result.trace.additions.any { it.stableKey == "support" && it.exactExhausted })
    }
    @Test fun `partial canonical restoration never fabricates smaller chunks`() {
        val parent = auth(6)
        val result = run(parent, listOf(f.row("press", 1, 1, 1)), emptyMap(), units = 4, minutes = 3,
            extra = listOf(AuthorizedPrescription("press", f.source("press", 1), f.rx(1, 1), false)))
        assertEquals(listOf(3), result.week!!.items.filter { it.exerciseStableKey == "squat" }.map { it.setCount })
        assertEquals(3, result.trace.exactShortfalls.first { it.authorizedDemandId == "parent" }.shortfall)
    }
    @Test fun `deterministic exact residual actions and QCR`() {
        val a = run(auth(5), listOf(reduced()), origin(1))
        val b = run(auth(5), listOf(reduced()), origin(1))
        assertEquals(a.skeleton, b.skeleton); assertEquals(a.trace, b.trace)
    }

    @Test fun `structured gap cannot use semantic path to fragment exact session`() {
        val gap = AdaptationGap("LOWER_KNEE", "HIGH", "test")
        val parent = auth(4, StrengthProgrammingStyle.TOP_SET_BACKOFF).let { it.copy(continuity = false,
            item = it.item.copy(representedGapCodes = setOf(gap.code))) }
        val result = run(parent, listOf(reduced()), origin(), minutes = 2, gaps = listOf(gap))
        assertEquals(listOf(2), result.week!!.items.filter { it.exerciseStableKey == "squat" }.map { it.setCount })
        assertTrue(result.trace.additions.none { it.stableKey == "squat" })
    }

    @Test fun `exact existing day blockage can use only existing unfixed plus one policy`() {
        val parent = auth(4)
        val rows = listOf(reduced(), f.row("squat", 3, 2, id = "separate"))
        val other = auth(2).copy(id = "separate", continuity = false)
        val origins = origin(0) + ("atom_1" to AuthorizedAtomOrigin("separate"))
        val fixed = run(parent, rows, origins, days = listOf(1, 3), extra = listOf(other))
        assertFalse(fixed.trace.addedDay); assertEquals(2, fixed.trace.exactShortfalls.first().shortfall)
        val flexible = run(parent, rows, origins, days = listOf(1, 3), extra = listOf(other), fixed = false)
        // Continuity C is already four for this key. No positive semantic residual: existing +1 policy cannot expand it.
        assertFalse(flexible.trace.addedDay)
        val higher = parent.copy(prescription = f.rx(6, 45, 10), item = parent.item.copy(targetSets = 6))
        val eligible = run(higher, listOf(reduced(3), rows[1]), origins, days = listOf(1, 3), extra = listOf(other), fixed = false)
        assertTrue(eligible.trace.addedDay); assertEquals(3, eligible.skeleton.request.weeklyTrainingDays)
        assertEquals(0, eligible.trace.exactShortfalls.first().shortfall)
    }

    @Test fun `equipment authority cannot be bypassed by exact restoration`() {
        val changed = snapshot.copy(exercises = snapshot.exercises + ("squat" to snapshot.exercises.getValue("squat").copy(equipment = "BARBELL")))
        val p = f.plan(listOf(reduced())).let { it.copy(request = it.request.copy(availableEquipment = setOf("BODYWEIGHT"))) }
        val result = ResidualCompletion().complete(p, changed, f.state(changed), emptyList(), listOf(auth(4)), f.envelope(),
            f.atoms(p), emptyMap(), true, f.safe, origin())
        assertTrue(result.trace.restorations.isEmpty()); assertEquals(p, result.skeleton)
    }

    @Test fun `lower priority exact relocation reopens earlier exact restoration before semantics`() {
        val parent = auth(3)
        val other = auth(2).copy(id = "separate", continuity = false)
        val blocker = AuthorizedPrescription("blocker", f.source("press", 2), f.rx(2, 50), false)
        val rows = listOf(reduced(), f.row("squat", 3, 2, 1, id = "separate"), f.row("press", 1, 1, 50))
        val origins = origin() + mapOf("atom_1" to AuthorizedAtomOrigin("separate"), "atom_2" to AuthorizedAtomOrigin("blocker"))
        val result = run(parent, rows, origins, minutes = 3, days = listOf(1, 3), extra = listOf(other, blocker))
        assertEquals(listOf("blocker", "parent"), result.trace.restorations.map { it.authorizedDemandId })
        assertTrue(result.trace.exactShortfalls.all { it.shortfall == 0 }); assertTrue(result.trace.additions.isEmpty())
    }

    @Test fun `reduced split sibling may remain while another canonical chunk is restored`() {
        val blocker = AuthorizedPrescription("blocker", f.source("press", 1), f.rx(1, 40), false)
        val rows = listOf(reduced(), reduced().copy(localId = "second", dayOfWeek = 3), f.row("press", 1, 1, 40))
        val origins = origin(0) + mapOf("atom_1" to AuthorizedAtomOrigin("parent", "parent", 1), "atom_2" to AuthorizedAtomOrigin("blocker"))
        val result = run(auth(6), rows, origins, minutes = 3, days = listOf(1, 3), extra = listOf(blocker))
        assertEquals(listOf(2, 3), result.week!!.items.filter { it.exerciseStableKey == "squat" }.map { it.setCount }.sorted())
        assertEquals(1, result.trace.exactShortfalls.first().shortfall)
    }
}

package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.*
import com.training.trackplanner.analysis.fatigue.DailyFatigueCalculator
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File

class BoundedDayRebalancerTest {
    private val f = PostGenerationFixture
    private val snapshot = f.snapshot()
    private fun minutes(key: String, day: Int, minutes: Int, id: String = key, order: Int = 1) =
        f.row(key, day, 1, minutes * 60, id, order)
    private fun uneven() = listOf(minutes("press", 1, 45), minutes("row", 1, 20, order = 2),
        minutes("direct", 2, 40), minutes("other", 2, 20, order = 2), minutes("support", 4, 20), minutes("hinge", 6, 18))
    private fun completed(rows: List<ProgramSkeletonItem> = uneven(), days: List<Int> = listOf(1, 2, 4, 6),
        minutes: Int = 90, overrides: Map<String, PlannedExercise> = emptyMap(), demand: AuthorizedPlanningDemand? = null): CompletionResult {
        val plan = f.plan(rows, days, minutes)
        val atoms = f.atoms(plan)
        val sources = plan.items.filter { it.weekNumber == 1 }.mapIndexed { index, row -> atoms.getValue(row.localId) to
            (overrides[rows[index].localId] ?: f.source(row.exerciseStableKey, row.setCount, material = false, priority = 70)) }.toMap()
        val fingerprint = personalizedProgramFingerprint(plan.request, plan.items)
        return CompletionResult(plan, ResidualCompletionTrace("POST_GENERATION_RESIDUAL_COMPLETION", fingerprint, fingerprint,
            snapshot.cutoff.plusDays(1).toString(), residuals = demand?.residuals(rows).orEmpty()),
            RepresentativeWeek.derive(plan, atoms), sources, demand)
    }
    private fun run(completed: CompletionResult = completed(), projection: PlanDayProjection = f.safe,
        source: PlanningHistorySnapshot = snapshot, state: AthletePlanningState = f.state(source)) =
        BoundedDayRebalancer().rebalance(completed, source, state, projection)
    private fun frozenContent(plan: GeneratedProgramSkeleton) = plan.items.associate { it.localId to it.copy(dayOfWeek = 1, orderIndex = 0) }
    private val proportionalOfi = PlanDayProjection { rows -> StandaloneDayLoad(rows.sumOf(::plannedSeconds) / 60, listOf(0)) }

    private fun units(key: String, day: Int, seconds: Int, movable: Boolean = false, id: String = key, order: Int = 1) =
        f.row(key, day, 1, seconds, id, order).copy(progressionRole = if (movable) ProgressionRole.AUTO else ProgressionRole.MAIN)
    private fun fallbackRows(atomSeconds: Int = 10) = listOf(units("press", 1, 140 - atomSeconds),
        units("row", 1, atomSeconds, true, order = 2), units("direct", 2, 105), units("support", 4, 95), units("other", 6, 90))
    private fun multipleSources(first: Int = 150, second: Int = 140) = listOf(units("press", 1, first - 10),
        units("row", 1, 10, true, order = 2), units("direct", 2, second - 10), units("support", 2, 10, true, order = 2),
        units("other", 3, 100), units("hinge", 4, 95), units("squat", 5, 90))

    @Test fun `fallback 140 105 95 90 moves ten whole units to lowest day`() {
        val rows = fallbackRows()
        val auth = rows.map { row -> AuthorizedPrescription(row.localId, f.source(row.exerciseStableKey, row.setCount),
            PlannedPrescription(row.prescription, row.setPrescriptions, row.restSeconds, row.weightSource), true) }
        val demand = AuthorizedPlanningDemand(snapshot, auth, emptyList(), rows)
        val input = completed(rows, demand = demand)
        val result = run(input)
        assertEquals(listOf(140, 105, 95, 90), result.trace.initialDays.map { it.seconds })
        assertEquals(listOf(130, 105, 95, 100), result.trace.finalDays.map { it.seconds })
        assertEquals(100.0, result.trace.timeReference, 0.0)
        assertEquals(6, result.trace.actions.single().destinationDay)
        assertEquals(listOf("row"), result.trace.actions.single().stableKeys)
        assertEquals("MOVE", result.trace.actions.single().actionType)
        assertEquals(frozenContent(input.skeleton), frozenContent(result.skeleton))
        assertEquals(input.skeleton.items.map { it.setPrescriptions }, result.skeleton.items.map { it.setPrescriptions })
        assertEquals(demand.residuals(rows), demand.residuals(result.skeleton.items.filter { it.weekNumber == 1 }))
        assertEquals(demand.coverage(rows), demand.coverage(result.skeleton.items.filter { it.weekNumber == 1 }))
        assertEquals(result, run(input))
        println("TIME_FALLBACK seconds ${result.trace.initialDays.map { it.seconds }} -> ${result.trace.finalDays.map { it.seconds }}")
    }
    @Test fun `fallback tries next lowest day after collision blocks lowest`() {
        val rows = fallbackRows().map { if (it.exerciseStableKey == "other") it.copy(exerciseStableKey = "row") else it }
        val result = run(completed(rows))
        assertEquals(4, result.trace.actions.single().destinationDay)
        assertEquals(listOf(130, 105, 105, 90), result.trace.finalDays.map { it.seconds })
    }
    @Test fun `fallback tries next overloaded source when first has no legal atom`() {
        val rows = multipleSources().map { if (it.exerciseStableKey == "row") it.copy(progressionRole = ProgressionRole.MAIN) else it }
        val result = run(completed(rows, days = listOf(1, 2, 3, 4, 5)))
        assertEquals(2, result.trace.actions.single().sourceDay)
    }
    @Test fun `fallback larger source ratio precedes earlier logical day`() {
        val result = run(completed(multipleSources(140, 150), days = listOf(1, 2, 3, 4, 5)))
        assertEquals(2, result.trace.actions.first().sourceDay)
        assertEquals(listOf(2, 1), result.trace.actions.map { it.sourceDay })
        assertEquals(listOf(5, 4), result.trace.actions.map { it.destinationDay })
        assertEquals(100.0, result.trace.timeReference, 0.0)
        result.trace.actions.forEach { action -> action.after.forEach { day ->
            assertEquals(day.seconds / 100.0, day.timeRatio, 0.0) } }
    }
    @Test fun `fallback equal source ratios use earlier logical day`() {
        val result = run(completed(multipleSources(140, 140), days = listOf(1, 2, 3, 4, 5)))
        assertEquals(1, result.trace.actions.first().sourceDay)
    }
    @Test fun `fallback equal destination ratios use earlier logical day`() {
        val rows = fallbackRows().map { if (it.dayOfWeek == 6) it.copy(setPrescriptions = f.rx(1, 95).sets) else it }
        assertEquals(4, run(completed(rows)).trace.actions.single().destinationDay)
    }
    @Test fun `fallback in-band times never cause equalization`() {
        val rows = fallbackRows().map { if (it.exerciseStableKey == "press") it.copy(setPrescriptions = f.rx(1, 110).sets) else it }
        val input = completed(rows)
        assertEquals(input.skeleton, run(input).skeleton)
        assertEquals("ALREADY_BALANCED", run(input).trace.balanceState)
    }
    @Test fun `fallback primary underloaded path still selects global best candidate`() {
        val rows = listOf(units("press", 1, 120), units("row", 1, 20, true, order = 2), units("direct", 2, 110),
            units("support", 4, 90), units("other", 6, 60))
        val result = run(completed(rows))
        assertEquals(listOf(120, 110, 90, 80), result.trace.finalDays.map { it.seconds })
        assertEquals(6, result.trace.actions.single().destinationDay)
        // The existing primary comparator remains covered by the optional-tier tie test below.
    }
    @Test fun `fallback does not repair OFI-only overload into in-band destinations`() {
        val rows = fallbackRows().map { if (it.exerciseStableKey == "press") it.copy(setPrescriptions = f.rx(1, 110).sets) else it }
        val input = completed(rows)
        val projection = PlanDayProjection { day -> StandaloneDayLoad(if (day.any { it.exerciseStableKey == "press" }) 60 else 40, listOf(0)) }
        val result = run(input, projection)
        assertTrue(result.trace.initialDays.any { it.ofiDistance > 0 })
        assertTrue(result.trace.actions.isEmpty())
        assertFalse(result.trace.diagnostic.contains("FAILED_SAFE"))
    }
    @Test fun `fallback cannot broaden swap destinations when no whole move fits`() {
        val rows = listOf(units("press", 1, 90), units("row", 1, 50, true, order = 2), units("direct", 2, 105),
            units("support", 4, 95), units("other", 6, 40, true), units("hinge", 6, 50, order = 2))
        // Swapping 50 with 40 would balance time, but neither destination is originally underloaded.
        val result = run(completed(rows))
        assertTrue(result.trace.actions.isEmpty())
        assertEquals("UNRESOLVED_BALANCE_CONSTRAINT", result.trace.balanceState)
    }
    @Test fun `fallback rejects destination above upper band without changing non-worsening rule`() {
        val result = run(completed(fallbackRows(50)))
        assertTrue(result.trace.actions.isEmpty())
        assertFalse(result.trace.diagnostic.contains("FAILED_SAFE"))
    }
    @Test fun `fallback OFI feasibility rejects lowest and tries next destination`() {
        for (blocked in listOf(StandaloneDayLoad(87, listOf(0)), StandaloneDayLoad(30, listOf(100)))) {
            val result = run(completed(fallbackRows()), PlanDayProjection { day ->
                if (day.any { it.exerciseStableKey == "row" } && day.any { it.exerciseStableKey == "other" }) blocked else StandaloneDayLoad(30, listOf(0)) })
            assertEquals(4, result.trace.actions.single().destinationDay)
        }
    }
    @Test fun `fallback respects tissue and all existing protected atom kinds`() {
        val rows = fallbackRows()
        val restricted = snapshot.copy(recoverySignals = PlanningRecoverySignals(tissueRestrictedStableKeys = setOf("row")))
        assertTrue(run(completed(rows), source = restricted).trace.actions.isEmpty())
        val variants = listOf<(ProgramSkeletonItem) -> ProgramSkeletonItem>(
            { it.copy(progressionRole = ProgressionRole.MAIN) }, { it.copy(progressionVariant = "HEAVY") },
            { it.copy(requiredTemplateAnchor = true) })
        variants.forEach { protect -> assertTrue(run(completed(rows.map { if (it.exerciseStableKey == "row") protect(it) else it })).trace.actions.isEmpty()) }
        assertTrue(run(completed(rows, overrides = mapOf("row" to f.source("row", 1, priority = 100, material = true)))).trace.actions.isEmpty())
    }
    @Test fun `fallback lower impact concentration cannot increase`() {
        val source = snapshot.copy(metadata = snapshot.metadata.mapValues { (key, metadata) ->
            metadata.copy(jointTendonImpactStressLevel = if (key != "press") "HIGH" else "LOW") })
        val rows = fallbackRows(20)
        // Existing max lower stress =105; all destinations would reach110 or more.
        assertTrue(run(completed(rows), source = source).trace.actions.isEmpty())
    }
    @Test fun `fallback compares all atoms within first feasible pair using existing comparator`() {
        val rows = listOf(units("press", 1, 120), units("row", 1, 10, true, order = 2), units("hinge", 1, 10, true, order = 3),
            units("direct", 2, 105), units("support", 4, 95), units("other", 6, 90))
        val input = completed(rows, overrides = mapOf("row" to f.source("row", 1, priority = 90, material = true)))
        assertEquals("hinge", run(input).trace.actions.single().stableKeys.single())
    }

    @Test fun `50 45 40 35 is already within band and performs zero moves`() {
        val input = completed(listOf(minutes("press", 1, 50), minutes("row", 2, 45), minutes("support", 4, 40), minutes("other", 6, 35)))
        val result = run(input, proportionalOfi)
        assertEquals("ALREADY_BALANCED", result.trace.balanceState)
        assertEquals(42.5 * 60, result.trace.timeReference, 0.0); assertEquals(42.5, result.trace.ofiReference, 0.0)
        assertTrue(result.trace.actions.isEmpty()); assertEquals(input.skeleton, result.skeleton)
    }
    @Test fun `65 60 20 18 improves by whole single moves with frozen medians`() {
        val input = completed()
        val result = run(input, proportionalOfi)
        assertEquals("WITHIN_TARGET_BAND", result.trace.balanceState)
        assertEquals(40.0 * 60, result.trace.timeReference, 0.0); assertEquals(40.0, result.trace.ofiReference, 0.0)
        assertEquals(2, result.trace.actions.size); assertTrue(result.trace.actions.all { it.actionType == "MOVE" })
        assertEquals(listOf(45, 40, 40, 38), result.trace.finalDays.map { it.seconds / 60 })
        val firstMoveDays = result.trace.initialDays.map { day -> result.trace.actions.first().after.firstOrNull { it.day == day.day } ?: day }
        assertNotEquals(result.trace.timeReference, planningMedian(firstMoveDays.map { it.seconds.toDouble() }))
        result.trace.actions.forEach { action -> action.after.forEach { day ->
            assertEquals(day.seconds / 2400.0, day.timeRatio, 0.0) } }
        result.trace.finalDays.forEach { assertEquals(it.seconds / 2400.0, it.timeRatio, 0.0) }
        assertEquals(frozenContent(input.skeleton), frozenContent(result.skeleton))
        println("CONTROLLED_BALANCE_FIXTURE seconds/OFI before=${result.trace.initialDays.map { it.seconds to it.standaloneOfi }} after=${result.trace.finalDays.map { it.seconds to it.standaloneOfi }}")
    }
    @Test fun `time outside band alone triggers while OFI remains in band`() {
        val result = run()
        assertTrue(result.trace.initialDays.all { it.ofiRatio == 1.0 })
        assertTrue(result.trace.actions.isNotEmpty()); assertEquals(0, result.trace.finalObjective.bandViolationCount)
    }
    @Test fun `OFI outside band alone triggers without worsening time`() {
        val rows = listOf(minutes("press", 1, 18), minutes("row", 1, 18, order = 2), minutes("other", 2, 30),
            minutes("support", 4, 30), minutes("direct", 6, 30))
        val loads = mapOf("press" to 30, "row" to 40, "other" to 40, "support" to 10, "direct" to 40)
        val result = run(completed(rows), PlanDayProjection { StandaloneDayLoad(it.sumOf { row -> loads.getValue(row.exerciseStableKey) }, listOf(0)) })
        assertTrue(result.trace.initialDays.all { it.timeDistance == 0.0 })
        // No legal action may push the destination above the time band. This fixture is constrained.
        assertTrue(result.trace.initialDays.any { it.ofiDistance > 0.0 })
        assertTrue(result.trace.finalDays.all { it.timeDistance == 0.0 })
        assertEquals("UNRESOLVED_BALANCE_CONSTRAINT", result.trace.balanceState)
    }
    @Test fun `OFI-only imbalance can move a short high OFI atom`() {
        val rows = listOf(minutes("press", 1, 25), minutes("row", 1, 5, order = 2), minutes("other", 2, 30),
            minutes("support", 4, 30), minutes("direct", 6, 30))
        val loads = mapOf("press" to 35, "row" to 35, "other" to 40, "support" to 10, "direct" to 40)
        val result = run(completed(rows), PlanDayProjection { StandaloneDayLoad(it.sumOf { row -> loads.getValue(row.exerciseStableKey) }, listOf(0)) })
        assertTrue(result.trace.initialDays.all { it.timeDistance == 0.0 })
        assertEquals(1, result.trace.actions.size); assertEquals("row", result.trace.actions.single().stableKeys.single())
        assertEquals("WITHIN_TARGET_BAND", result.trace.balanceState)
    }
    @Test fun `median is not mean and never becomes moving target`() {
        assertEquals(40.0, planningMedian(listOf(65.0, 60.0, 20.0, 18.0)), 0.0)
        assertNotEquals(listOf(65.0, 60.0, 20.0, 18.0).average(), 40.0)
        val result = run(projection = proportionalOfi)
        result.trace.actions.forEach { action ->
            (action.before + action.after).forEach { assertEquals(it.seconds / result.trace.timeReference, it.timeRatio, 0.0)
                assertEquals(it.standaloneOfi / result.trace.ofiReference, it.ofiRatio!!, 0.0) }
        }
    }
    @Test fun `per-metric rule rejects time improvement that creates OFI violation`() {
        val before = listOf(BalanceDay(1, 60, 40, 1.5, 1.0), BalanceDay(2, 20, 40, .5, 1.0))
        val after = listOf(BalanceDay(1, 40, 20, 1.0, .5), BalanceDay(2, 40, 60, 1.0, 1.5))
        assertFalse(balanceNonWorsening(before, after))
    }
    @Test fun `per-metric rule rejects OFI improvement that creates time violation`() {
        val before = listOf(BalanceDay(1, 40, 60, 1.0, 1.5), BalanceDay(2, 40, 20, 1.0, .5))
        val after = listOf(BalanceDay(1, 20, 40, .5, 1.0), BalanceDay(2, 60, 40, 1.5, 1.0))
        assertFalse(balanceNonWorsening(before, after))
        assertFalse(balanceNonWorsening(before, before))
    }
    @Test fun `hard destination session time cannot be traded for balance`() {
        val input = completed(listOf(minutes("press", 1, 55), minutes("row", 1, 10, order = 2), minutes("other", 2, 65),
            minutes("support", 4, 60), minutes("direct", 6, 65)), minutes = 65)
        val loads = mapOf("press" to 35, "row" to 35, "other" to 40, "support" to 10, "direct" to 40)
        val result = run(input, PlanDayProjection { StandaloneDayLoad(it.sumOf { row -> loads.getValue(row.exerciseStableKey) }, listOf(0)) })
        // Moving the ten-minute atom improves OFI and stays inside the time band, but exceeds the hard 65-minute cap.
        assertTrue(ProgramProjectionValidator().errors(input.skeleton).isEmpty())
        assertTrue(result.trace.actions.isEmpty()); assertEquals("UNRESOLVED_BALANCE_CONSTRAINT", result.trace.balanceState)
        assertFalse(result.trace.diagnostic.contains("FAILED_SAFE"))
        assertEquals(frozenContent(input.skeleton), frozenContent(result.skeleton))
    }
    @Test fun `destination OFI 87 and axis 100 are rejected independently`() {
        val input = completed()
        for (blocked in listOf(StandaloneDayLoad(87, listOf(0)), StandaloneDayLoad(30, listOf(100)))) {
            val result = run(input, PlanDayProjection { blocked })
            assertTrue(result.trace.actions.isEmpty()); assertEquals(input.skeleton, result.skeleton)
        }
    }
    @Test fun `tissue-restricted stableKeys are never relocated`() {
        val input = completed()
        val source = snapshot.copy(recoverySignals = PlanningRecoverySignals(tissueRestrictedStableKeys = snapshot.exercises.keys))
        val result = run(input, source = source)
        assertTrue(result.trace.actions.isEmpty()); assertEquals(input.skeleton, result.skeleton)
    }
    @Test fun `CORE must do items are immovable including robust core protection`() {
        val rows = uneven()
        val overrides = rows.associate { it.localId to f.source(it.exerciseStableKey, it.setCount, priority = 100, material = true) }
        val state = f.state(snapshot)
        val assessment = requireNotNull(state.trainingStateAssessment)
        val robust = state.copy(trainingStateAssessment = assessment.copy(sustainable = assessment.sustainable.copy(robustSchedule = true)))
        val result = run(completed(rows, overrides = overrides), state = robust)
        assertTrue(result.trace.actions.isEmpty()); assertEquals("UNRESOLVED_BALANCE_CONSTRAINT", result.trace.balanceState)
    }
    @Test fun `progression MAIN items are immovable`() {
        val input = completed(uneven().map { it.copy(progressionRole = ProgressionRole.MAIN) })
        assertEquals(input.skeleton, run(input).skeleton)
    }
    @Test fun `shared session MAIN authority protects members even with AUTO row role`() {
        val rows = uneven().map { row -> row.copy(progressionRole = ProgressionRole.AUTO,
            progressionBinding = DraftProgressionBinding("session_${row.exerciseStableKey}", logicalItemId = row.localId,
                signature = ProgressionSignature(row.exerciseStableKey, row.setCount, "8", null, null, null))) }
        val input = completed(rows)
        val sessions = rows.map { row -> DraftProgressionSession(ProgramProgressionTrack(
            id = "session_${row.exerciseStableKey}", programStableKey = "post", exerciseStableKey = row.exerciseStableKey,
            label = "explicit main", role = ProgressionRole.MAIN), ProgressionAuthority.USER_EXPLICIT) }
        val withSessions = input.copy(skeleton = input.skeleton.copy(progressionSessions = sessions))
        val result = run(withSessions)
        assertTrue(result.trace.actions.isEmpty())
        assertEquals("UNRESOLVED_BALANCE_CONSTRAINT", result.trace.balanceState)
        assertEquals(withSessions.skeleton, result.skeleton)
    }
    @Test fun `style variants and canonical fixed template anchors cannot move`() {
        for (variant in listOf("HEAVY", "LIGHT", "STRENGTH", "VOLUME", "MEDIUM")) {
            val input = completed(uneven().map { it.copy(progressionVariant = variant) })
            assertTrue(run(input).trace.actions.isEmpty())
        }
        val template = completed(uneven().map { it.copy(requiredTemplateAnchor = true) })
        assertTrue(run(template).trace.actions.isEmpty())
    }
    @Test fun `same stableKey never collides at a destination`() {
        val input = completed(listOf(minutes("press", 1, 45), minutes("row", 1, 20, order = 2),
            minutes("press", 2, 60, id = "press2"), minutes("row", 4, 20, id = "row2"), minutes("row", 6, 18, id = "row3")))
        val result = run(input)
        assertTrue(result.skeleton.items.groupBy { it.weekNumber to it.dayOfWeek }.values.all { day ->
            day.size == day.map { it.exerciseStableKey }.distinct().size })
    }
    @Test fun `lower or impact concentration cannot increase`() {
        val source = snapshot.copy(metadata = snapshot.metadata.mapValues { (_, meta) -> meta.copy(jointTendonImpactStressLevel = "HIGH") })
        val result = run(source = source)
        fun maximum(plan: GeneratedProgramSkeleton) = plan.items.filter { it.weekNumber == 1 }.groupBy { it.dayOfWeek }.values.maxOf { it.sumOf(::plannedSeconds) }
        assertTrue(maximum(result.skeleton) <= maximum(completed().skeleton))
        assertTrue(result.trace.actions.all { it.lowerStressDispersion == "NON_INCREASING" })
    }
    @Test fun `moving into lower stress concentration is rejected even when total time improves`() {
        val input = completed(listOf(minutes("press", 1, 45), minutes("squat", 1, 20, order = 2),
            minutes("row", 2, 60), minutes("hinge", 4, 20), minutes("other", 6, 18)))
        val result = run(input)
        assertTrue(result.trace.actions.none { it.stableKeys.contains("squat") && it.destinationDay == 4 })
    }
    @Test fun `entire prescription and every field other than assignment order remain exact`() {
        val rows = uneven().map { it.copy(selectionReason = "original provenance", weightSource = "exact-authority") }
        val input = completed(rows)
        val result = run(input)
        assertTrue(result.trace.actions.isNotEmpty())
        assertEquals(frozenContent(input.skeleton), frozenContent(result.skeleton))
        assertEquals(input.skeleton.items.map { it.setPrescriptions }, result.skeleton.items.map { it.setPrescriptions })
        result.skeleton.items.groupBy { it.weekNumber to it.dayOfWeek }.values.forEach { day ->
            assertEquals((1..day.size).toList(), day.map { it.orderIndex }.sorted()) }
    }
    @Test fun `Q C R remain exactly equal including floating weighted exposure`() {
        val rows = uneven()
        val gaps = listOf(AdaptationGap("BADMINTON_UNDERREPRESENTED_REACTION", "HIGH", "objective"), AdaptationGap("POSTERIOR_CHAIN", "MEDIUM", "movement"))
        val auth = rows.map { row -> AuthorizedPrescription(row.localId,
            f.source(row.exerciseStableKey, row.setCount).copy(representedGapCodes = gaps.map { it.code }.toSet()),
            PlannedPrescription(row.prescription, row.setPrescriptions, row.restSeconds, row.weightSource), true) }
        val demand = AuthorizedPlanningDemand(snapshot, auth, gaps, rows)
        val input = completed(rows, demand = demand)
        val result = run(input)
        val before = input.skeleton.items.filter { it.weekNumber == 1 }; val after = result.skeleton.items.filter { it.weekNumber == 1 }
        assertTrue(result.trace.actions.isNotEmpty())
        assertEquals(demand.coverage(before), demand.coverage(after)); assertEquals(demand.residuals(before), demand.residuals(after))
        assertEquals(input.trace, input.trace.copy())
    }
    @Test fun `all improving one-way moves are considered and optional tier wins exact objective tie`() {
        val rows = listOf(minutes("press", 1, 25), minutes("row", 1, 20, order = 2), minutes("other", 1, 20, order = 3),
            minutes("direct", 2, 60), minutes("support", 4, 20), minutes("hinge", 6, 18))
        val overrides = mapOf("row" to f.source("row", 1, priority = 90, material = true))
        val result = run(completed(rows, overrides = overrides))
        assertEquals("MOVE", result.trace.actions.first().actionType)
        assertEquals("other", result.trace.actions.first().stableKeys.single())
    }
    @Test fun `swap occurs only when no single move can improve and preserves doses`() {
        val rows = listOf(minutes("press", 1, 40), minutes("row", 1, 25, order = 2), minutes("direct", 2, 60),
            minutes("support", 4, 20), minutes("other", 6, 18))
        // OFI weights force entire one-way moves to worsen OFI; swapping 40 for 20 preserves OFI.
        val weights = mapOf("press" to 20, "row" to 20, "direct" to 40, "support" to 20, "other" to 20)
        val projector = PlanDayProjection { StandaloneDayLoad(it.sumOf { row -> weights.getValue(row.exerciseStableKey) }, listOf(0)) }
        val result = run(completed(rows), projector)
        assertTrue(result.trace.actions.isNotEmpty()); assertEquals("SWAP", result.trace.actions.first().actionType)
        assertEquals(frozenContent(completed(rows).skeleton), frozenContent(result.skeleton))
    }
    @Test fun `no legal improvement preserves best current plan without filler`() {
        val input = completed(listOf(minutes("press", 1, 65), minutes("row", 2, 60), minutes("support", 4, 20), minutes("other", 6, 18)))
        val result = run(input)
        assertTrue(result.trace.actions.isEmpty()); assertEquals(input.skeleton, result.skeleton)
        assertEquals("UNRESOLVED_BALANCE_CONSTRAINT", result.trace.balanceState)
    }
    @Test fun `same representative move map is mirrored identically to every horizon week`() {
        val input = completed()
        val result = run(input)
        fun assignments(week: Int) = result.skeleton.items.filter { it.weekNumber == week }.map { Triple(it.exerciseStableKey, it.dayOfWeek, it.orderIndex) }
        assertEquals(assignments(1), assignments(2)); assertEquals(assignments(1), assignments(3))
        assertEquals(input.skeleton.items.map { it.localId }, result.skeleton.items.map { it.localId })
    }
    @Test fun `identical input is deterministic and actions strictly improve without cycles`() {
        val input = completed()
        val first = run(input, proportionalOfi); val second = run(input, proportionalOfi)
        assertEquals(first, second)
        first.trace.actions.forEach { assertTrue(it.afterObjective < it.beforeObjective); assertTrue(balanceNonWorsening(it.before, it.after)) }
    }
    @Test fun `OFI ratio disabled at zero while time balancing remains enabled`() {
        val result = run(projection = PlanDayProjection { StandaloneDayLoad(0, listOf(0)) })
        assertEquals(0.0, result.trace.ofiReference, 0.0); assertTrue(result.trace.initialDays.all { it.ofiRatio == null })
        assertTrue(result.trace.actions.isNotEmpty()); assertEquals("OFI_RATIO_BALANCING_DISABLED", result.trace.toJson().getString("ofiRatioState"))
    }
    @Test fun `empty scheduled day participates without invented exercise load`() {
        val input = completed(uneven().filter { it.dayOfWeek != 6 })
        val result = run(input, PlanDayProjection { StandaloneDayLoad(if (it.isEmpty()) 12 else 30, listOf(0)) })
        val empty = result.trace.initialDays.first { it.day == 6 }
        assertEquals(0, empty.seconds); assertEquals(0.0, empty.timeRatio, 0.0); assertEquals(12, empty.standaloneOfi)
    }
    @Test fun `rebalancing failure cannot discard completed residual additions`() {
        val gap = AdaptationGap("LOWER_KNEE", "HIGH", "fixture")
        val auth = listOf(f.authorized(snapshot, f.source("press", 8), true), f.authorized(snapshot, f.source("squat", 2, gap.code)))
        val completion = f.complete(f.plan(listOf(f.row("press", 1, 8))), auth, listOf(gap))
        assertTrue(completion.trace.additions.isNotEmpty())
        val result = run(completion, PlanDayProjection { error("unavailable") })
        assertEquals(completion.skeleton, result.skeleton)
        assertEquals("FINAL_REBALANCE_FAILED_SAFE_COMPLETED_PLAN", result.trace.diagnostic)
        assertTrue(result.skeleton.items.any { it.exerciseStableKey == "squat" })
    }
    @Test fun `trace serializes separate action before after and unchanged residual provenance`() {
        val result = run()
        val json = JSONObject(result.trace.toJson().toString())
        assertEquals(result.trace.actions.size, json.getJSONArray("actions").length())
        val action = json.getJSONArray("actions").getJSONObject(0)
        assertTrue(action.has("beforeObjective") && action.has("afterObjective") && action.has("before") && action.has("after"))
        assertEquals("PASS", action.getString("ofiGate")); assertTrue(action.has("tissueGate") && action.has("lowerStressDispersion"))
    }
    @Test fun `real canonical independent projection is used for completed day audit`() {
        val input = completed()
        val calculator = DailyFatigueCalculator(RuntimeExerciseMetadataCatalog.of(snapshot.metadata.values))
        val adapter = PlanDayOfiProjection(snapshot.cutoff, calculator, snapshot.exercises.values.toList(), emptyList(), null)
        val result = run(input, adapter)
        assertTrue(result.trace.initialDays.isNotEmpty())
        result.trace.initialDays.forEach { day -> assertEquals(adapter.evaluate(input.week!!.items.filter { it.dayOfWeek == day.day }).ofi, day.standaloneOfi) }
        result.trace.finalDays.forEach { day -> assertEquals(adapter.evaluate(result.skeleton.items.filter { it.weekNumber == 1 && it.dayOfWeek == day.day }).ofi, day.standaloneOfi) }
        println("CANONICAL_OFI_FIXTURE date=${adapter.projectionDate} before=${result.trace.initialDays.map { it.seconds to it.standaloneOfi }} after=${result.trace.finalDays.map { it.seconds to it.standaloneOfi }} state=${result.trace.balanceState}")
    }
    @Test fun `builder invokes second stage after completion without overwriting initial fingerprint`() {
        val root = generateSequence(File(System.getProperty("user.dir")), File::getParentFile).first { File(it, "settings.gradle.kts").isFile }
        val source = File(root, "app/src/main/java/com/training/trackplanner/data/personalized/PersonalizedProgramBuilder.kt").readText()
        assertTrue(source.indexOf("BoundedDayRebalancer().rebalance") > source.indexOf("ResidualCompletion(prescriptionPlanner).complete"))
        assertTrue(source.contains("residualCompletion = completion.trace")); assertTrue(source.contains("dayRebalancing = rebalanced.trace"))
        assertEquals(1, Regex("originalGenerationFingerprint =").findAll(source).count())
    }
}

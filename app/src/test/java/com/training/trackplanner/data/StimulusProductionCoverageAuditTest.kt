package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.data.personalized.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.ln

/** Unmodified real Room/service results; outcomes are measurements, never success quotas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StimulusProductionCoverageAuditTest {
    internal data class CoverageSpec(
        val label: String, val quality: TrainableQuality, val stableKey: String,
        val profileGoal: String, val goal: ProgramGoal, val intent: StrengthIntent,
        val badminton: Boolean, val history: String, val days: Int, val minutes: Int,
        val equipment: Set<String>, val isolateOwner: Boolean = false,
        val explicitWeeklyDays: Boolean = true, val explicitDuration: Boolean = true
    )

    @Test
    fun measureUnmodifiedServiceCoverage() = runBlocking {
        val histories = listOf("none", "sparse", "recent", "reviewed", "mixed")
        val equipment = listOf(setOf("BARBELL", "DUMBBELL", "BENCH", "RACK"), setOf("MACHINE", "CABLE"), emptySet())
        val specs = (0..4).flatMap { persona -> histories.mapIndexed { historyIndex, history ->
            val h = persona == 1
            CoverageSpec(
                label = "persona${persona}_${history}",
                quality = if (h) TrainableQuality.HYPERTROPHY else TrainableQuality.STRENGTH,
                stableKey = if (h) "cable_rear_delt_fly" else "barbell_back_squat",
                profileGoal = when (persona) { 0 -> "STRENGTH_GAIN"; 1 -> "HYPERTROPHY_PHYSIQUE"; else -> "MIXED" },
                goal = when (persona) { 0 -> ProgramGoal.STRENGTH; 1 -> ProgramGoal.BODYBUILDING; 3 -> ProgramGoal.BADMINTON_SUPPORT; else -> ProgramGoal.FUNCTIONAL_CONDITIONING },
                intent = when (persona) { 0 -> StrengthIntent.STRENGTH_PRIORITY; 1 -> StrengthIntent.HYPERTROPHY_PRIORITY; else -> StrengthIntent.MIXED },
                badminton = persona == 3, history = history, days = 2 + (persona + historyIndex) % 4,
                minutes = listOf(30, 60, 90)[(persona + historyIndex) % 3],
                equipment = equipment[(persona * 2 + historyIndex) % 3]
            )
        } } + listOf(
            CoverageSpec("reviewed_strength_isolated", TrainableQuality.STRENGTH, "barbell_back_squat", "STRENGTH_GAIN", ProgramGoal.STRENGTH, StrengthIntent.STRENGTH_PRIORITY, false, "reviewed", 3, 60, emptySet(), true),
            CoverageSpec("reviewed_hypertrophy_isolated", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly", "HYPERTROPHY_PHYSIQUE", ProgramGoal.BODYBUILDING, StrengthIntent.HYPERTROPHY_PRIORITY, false, "reviewed", 3, 60, emptySet(), true)
        )
        val records = specs.map { spec ->
            val result = runCase(spec)
            if (result == null) {
                assertEquals("Only the real no-history precondition may reject this corpus", "none", spec.history)
                return@map spec to null
            }
            val comparison = result.comparison
            assertEquals(spec.label, 1, result.buildCounts.controlBuilds)
            assertEquals(spec.label, 0, result.buildCounts.thirdBuilds)
            if (comparison != null) {
                assertEquals(spec.label, 1, result.buildCounts.experimentalBuilds)
                assertEquals(spec.label, 2, result.buildCounts.totalBuildInvocations)
                assertEquals("${spec.label} CONTROL and EXPERIMENTAL must use the same complete request",
                    comparison.control.request, comparison.experimental.request)
                assertSame(if (result.routeDecision.productionRoutingActive) comparison.experimental else comparison.control, result.program)
                assertEquals(StimulusProductionMaterialScopeResolver().resolve(comparison), result.diagnostics.scopeResolution?.scope)
            }
            assertEquals(!result.routeDecision.productionRoutingActive, result.diagnostics.primaryFallbackStage != null)
            spec to result
        }
        val report = render(records)
        assertEquals(report, render(records.reversed()))
        assertEquals(renderProvenance(records), renderProvenance(records.reversed()))
        val path = java.io.File("build/reports/stimulus-production-coverage.txt")
        requireNotNull(path.parentFile).mkdirs()
        path.writeText(report)
        java.io.File("build/reports/c9-provenance-census.txt").writeText(renderProvenance(records))
        println(report)
        assertC9CorpusBoundaries(records)
    }

    private fun assertC9CorpusBoundaries(records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>) {
        assertEquals(27, records.size)
        assertEquals(5, records.count { it.second == null })
        val omissions = records.mapNotNull { (spec, result) -> result?.comparison?.let { spec to it } }
            .flatMap { (spec, comparison) -> comparison.nonSelectionProvenance.map { spec.label to it } }
        assertEquals(17, omissions.size)
        assertEquals(mapOf(StimulusNonSelectionClassification.CANONICAL_REPLACEMENT to 17),
            omissions.groupingBy { it.second.classification }.eachCount())
        val omissionWeeks = omissions.sumOf { (caseId, omission) ->
            val comparison = requireNotNull(records.single { it.first.label == caseId }.second?.comparison)
            comparison.control.items.filter {
                it.exerciseStableKey == omission.omittedControlOwner.stableKey &&
                    it.selectionRole == omission.omittedControlOwner.selectionRole
            }.map { it.weekNumber }.distinct().size
        }
        assertEquals(34, omissionWeeks)
        records.forEach { (spec, result) ->
            if (result != null) assertEquals("No unreviewed route expansion: ${spec.label}",
                if (spec.label == "reviewed_strength_isolated") StimulusProductionProgramSource.B8_STRENGTH_V1
                else StimulusProductionProgramSource.CONTROL, result.routeDecision.selectedSource)
        }
        val h = requireNotNull(records.single { it.first.label == "reviewed_hypertrophy_isolated" }.second)
        val c = requireNotNull(h.comparison)
        val audit = requireNotNull(c.experimentalReadinessAudit)
        assertTrue(audit.materializationIntegrityPassed)
        assertTrue(audit.collateralRegressionFree)
        assertFalse(audit.changeProvenanceClosed)
        assertEquals(listOf("CHANGE_PROVENANCE_UNCLOSED"), audit.reasonCodes)
        val unresolved = audit.changeAttributions.single { it.source == StimulusExperimentalChangeAttributionSource.UNEXPLAINED }
        assertEquals("ex_284ecca6", unresolved.stableKey)
        assertEquals("COVERAGE_POSTERIOR_CHAIN", unresolved.selectionRole)
        assertEquals(listOf("UNEXPLAINED_PRESCRIPTION_CHANGE"), unresolved.reasonCodes)
        assertFalse(c.selectionPlan.selectedCandidates.any { it.stableKey == unresolved.stableKey && it.selectionRole == unresolved.selectionRole })
        assertFalse(c.prescriptionAuthorizationPlan!!.authorizations.any { it.owner?.stableKey == unresolved.stableKey })
        assertFalse(unresolved.stableKey in c.experimental.personalizedDecision?.planningBudget?.execution?.constrainedOwnerStableKeys.orEmpty())
        assertTrue(audit.changeAttributions.any { it.stableKey == "cable_rear_delt_fly" &&
            "B5_CANONICAL_OWNER_REPLACED_CONTROL_ROLE" in it.reasonCodes && it.targetIds == listOf("QUALITY:HYPERTROPHY") })
        val legacyFly = StimulusPrescriptionOwnerIdentity("cable_rear_delt_fly", "STYLE_MEDIUM_HORIZONTAL_PULL")
        val flyOmission = c.nonSelectionProvenance.single { it.omittedControlOwner == legacyFly }
        assertEquals(StimulusNonSelectionClassification.CANONICAL_REPLACEMENT, flyOmission.classification)
        val exactFlyDisposition = flyOmission.targetEvidence.single { it.targetId == "QUALITY:HYPERTROPHY" }.disposition
        assertEquals(StimulusCandidateDispositionStatus.SELECTED, exactFlyDisposition.status)
        assertEquals(legacyFly.stableKey, exactFlyDisposition.stableKey)
        assertEquals("CANONICAL_STIMULUS_QUALITY_HYPERTROPHY", exactFlyDisposition.canonicalSelectionRole)
        val withoutTypedOmissionEvidence = c.copy(
            selectionPlan = c.selectionPlan.copy(candidateDispositionIndex = StimulusCandidateDispositionIndex(
                c.selectionPlan.candidateDispositionIndex.entries - exactFlyDisposition
            )),
            nonSelectionProvenance = c.nonSelectionProvenance.map { omission ->
                if (omission.omittedControlOwner != legacyFly) omission else omission.copy(
                    targetEvidence = omission.targetEvidence.filterNot { it.disposition == exactFlyDisposition }
                )
            }
        )
        val withoutTypedAudit = StimulusExperimentalReadinessAuditEngine().audit(withoutTypedOmissionEvidence)
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            withoutTypedAudit.changeAttributions.single { it.stableKey == legacyFly.stableKey && it.selectionRole == legacyFly.selectionRole }.source)
    }

    @Test
    fun c10ExactPrefixReductionRequiresTypedOwnerAndCauseEdge() = runBlocking {
        // Synthetic seam test only: the unchanged real fixture above MUST remain CONTROL.
        val spec = CoverageSpec("c9_allocator_boundary", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly",
            "HYPERTROPHY_PHYSIQUE", ProgramGoal.BODYBUILDING, StrengthIntent.HYPERTROPHY_PRIORITY,
            false, "reviewed", 3, 60, emptySet(), true)
        val c = requireNotNull(requireNotNull(runCase(spec)).comparison)
        val owner = StimulusPrescriptionOwnerIdentity("ex_284ecca6", "COVERAGE_POSTERIOR_CHAIN")
        val before = c.control.items.filter { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole }
            .associateBy { Triple(it.weekNumber, it.dayOfWeek, it.orderIndex) }
        val decision = requireNotNull(c.experimental.personalizedDecision)
        val budget = requireNotNull(decision.planningBudget)
        val execution = requireNotNull(budget.execution)
        val targetId = "QUALITY:HYPERTROPHY"
        val causeCandidate = c.selectionPlan.selectedCandidates.single { candidate ->
            StimulusPrescriptionOwnerIdentity(candidate.stableKey, candidate.selectionRole) in c.addedOwnerIdentities &&
                targetId in candidate.coveredTargetIds
        }
        val causeOwner = StimulusPrescriptionOwnerIdentity(causeCandidate.stableKey, causeCandidate.selectionRole)
        val causeDemandId = requireNotNull(decision.authorizedScheduling).authorized.single {
            StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role) == causeOwner
        }.id
        fun audit(emitAllocatorEvent: Boolean, emitEdge: Boolean = emitAllocatorEvent,
            eventRole: String = owner.selectionRole, edgeDisplaced: StimulusPrescriptionOwnerIdentity = owner,
            mutateRest: Boolean = false, mutatePrescription: Boolean = false): StimulusExperimentalChangeAttribution {
            val changedRows = c.experimental.items.map { row ->
                    if (row.exerciseStableKey == owner.stableKey && row.selectionRole == owner.selectionRole) {
                        val old = before.getValue(Triple(row.weekNumber, row.dayOfWeek, row.orderIndex))
                        assertEquals(old.setPrescriptions.take(row.setCount), row.setPrescriptions)
                        row.copy(prescription = if (mutatePrescription) old.prescription + " changed" else old.prescription,
                            restSeconds = if (mutateRest) old.restSeconds + 1 else old.restSeconds)
                    } else row
                }
            val ownerRows = changedRows.filter { it.exerciseStableKey == owner.stableKey && it.selectionRole == owner.selectionRole }
            val typedEvents = if (!emitAllocatorEvent) emptyList() else ownerRows.mapNotNull { after ->
                val old = before[Triple(after.weekNumber, after.dayOfWeek, after.orderIndex)] ?: return@mapNotNull null
                if (old.setCount == after.setCount) return@mapNotNull null
                val beforeState = OwnerAllocationState(old.weekNumber, old.dayOfWeek, old.orderIndex, old.setCount,
                    old.setPrescriptions, old.prescription, eventRole)
                val afterState = OwnerAllocationState(after.weekNumber, after.dayOfWeek, after.orderIndex, after.setCount,
                    after.setPrescriptions, after.prescription, eventRole)
                OwnerAllocationProvenance(owner.copy(selectionRole = eventRole), OwnerAllocationStage.FINITE_EXECUTION_ALLOCATION,
                    OwnerAllocationAction.SET_COUNT_REDUCED, beforeState, afterState, OwnerAllocationCause.CAPACITY_LIMIT,
                    authorizedDemandIds = setOf("test-displaced-demand"), evidenceCodes = listOf("TEST_EMITTED_AT_ALLOCATOR"))
            }
            val typedEdges = if (!emitEdge) emptyList() else typedEvents.mapNotNull { event ->
                OwnerDisplacementEdge(causeOwner, edgeDisplaced, event.stage, event.cause,
                    requireNotNull(event.before).setCount - requireNotNull(event.after).setCount,
                    week = event.before.week, targetIds = setOf(targetId), qualities = setOf("HYPERTROPHY"),
                    authorizedDemandIds = setOf(causeDemandId), mutationSequence = event.mutationSequence)
            }
            val audit = StimulusExperimentalReadinessAuditEngine().audit(c.copy(
            experimental = c.experimental.copy(
                items = changedRows,
                personalizedDecision = decision.copy(planningBudget = budget.copy(execution = execution.copy(
                    constrainedOwnerStableKeys = execution.constrainedOwnerStableKeys + owner.stableKey,
                    ownerAllocationProvenance = typedEvents,
                    ownerDisplacementEdges = typedEdges
                )))
            )
        ))
            return audit.changeAttributions.single { it.stableKey == owner.stableKey && it.selectionRole == owner.selectionRole }
        }
        val exact = audit(emitAllocatorEvent = true)
        assertEquals(StimulusExperimentalChangeAttributionSource.DOWNSTREAM_CONSTRAINT_DISPLACEMENT, exact.source)
        assertEquals(listOf(targetId), exact.targetIds)
        assertTrue("OWNER_LOCAL_CONSTRAINED_SET_SUBSET" in exact.reasonCodes)
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            audit(emitAllocatorEvent = false, emitEdge = false).source) // legacy stableKey field cannot fabricate capacity proof
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            audit(emitAllocatorEvent = true, emitEdge = false).source) // exact mutation alone does not name its cause owner
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            audit(emitAllocatorEvent = true, eventRole = "OTHER_ROLE").source)
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            audit(emitAllocatorEvent = true, edgeDisplaced = owner.copy(stableKey = "other_owner")).source)
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            audit(emitAllocatorEvent = true, mutateRest = true).source)
        assertEquals(StimulusExperimentalChangeAttributionSource.UNEXPLAINED,
            audit(emitAllocatorEvent = true, mutatePrescription = true).source)
    }

    /** C9 diagnostic: actual service objects, before any attribution/golden changes. */
    private fun renderProvenance(records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>): String = buildString {
        val generated = records.mapNotNull { (spec, result) -> result?.let { spec to it } }
        appendLine("generated=${generated.size} rejected=${records.size - generated.size}")
        appendLine("routes=" + generated.groupingBy { it.second.routeDecision.selectedSource }.eachCount())
        appendLine("B7 counts=" + generated.flatMap { it.second.comparison?.experimentalReadinessAudit?.reasonCodes.orEmpty() }.groupingBy { it }.eachCount().toSortedMap())
        appendLine("B8 counts=" + generated.flatMap { it.second.comparison?.productionCutoverAuthority?.reasonCodes.orEmpty() }.groupingBy { it }.eachCount().toSortedMap())
        appendLine("C10 origin trace coverage (${renderC10TraceCoverage(records)})")
        val omissions = generated.flatMap { (spec, result) -> requireNotNull(result.comparison).nonSelectionProvenance.map { spec.label to it } }
        appendLine("C11 unique omitted CONTROL owner classifications=" + omissions.groupingBy { it.second.classification }.eachCount().toSortedMap())
        val weeklyOmissions = generated.flatMap { (spec, result) ->
            val comparison = requireNotNull(result.comparison)
            comparison.nonSelectionProvenance.flatMap { omission ->
                comparison.control.items.filter {
                    it.exerciseStableKey == omission.omittedControlOwner.stableKey &&
                        it.selectionRole == omission.omittedControlOwner.selectionRole
                }.map { it.weekNumber }.distinct().map { week -> omission.classification }
            }
        }
        appendLine("C11 removed owner-week classifications=" + weeklyOmissions.groupingBy { it }.eachCount().toSortedMap())
        omissions.sortedWith(compareBy({ it.first }, { it.second.omittedControlOwner.stableKey }, { it.second.omittedControlOwner.selectionRole }))
            .forEach { (caseId, omission) ->
                appendLine("C11 omitted case=$caseId owner=${omission.omittedControlOwner} classification=${omission.classification}")
                omission.targetEvidence.forEach { target ->
                    val d = target.disposition
                    appendLine("  target=${target.targetId} classification=${target.classification} direct=${d.directTargetCandidate} selectionRequired=${d.selectionRequired} status=${d.status} reasons=${d.reasons} selectedInstead=${d.selectedInstead} candidateTuple=${d.candidateRanking} selectedTuple=${d.selectedInsteadRanking} firstDifference=${d.firstDifferingField}")
                }
            }
        generated.singleOrNull { it.first.label == "reviewed_hypertrophy_isolated" }?.second?.comparison?.let { h ->
            listOf("ex_284ecca6", "ex_28347c1f").forEach { key ->
                appendLine("C11 H owner diagnostic stableKey=$key disposition=${h.selectionPlan.candidateDispositionIndex.forStableKey(key)}")
            }
        }
        generated.sortedBy { it.first.label }.forEach { (spec, result) ->
            val c = requireNotNull(result.comparison)
            fun identity(row: ProgramSkeletonItem) = StimulusPrescriptionOwnerIdentity(row.exerciseStableKey, row.selectionRole)
            val before = c.control.items.groupBy(::identity)
            val after = c.experimental.items.groupBy(::identity)
            fun prescriptions(rows: List<ProgramSkeletonItem>) = rows.map {
                PlannedPrescription(it.prescription, it.setPrescriptions, it.restSeconds, it.weightSource)
            }
            appendLine("CASE ${spec.label}")
            appendLine("B4=${c.targetPlan}")
            appendLine("B5=${c.selectionPlan.selectedCandidates}")
            appendLine("B6=${c.prescriptionAuthorizationPlan}")
            appendLine("CONTROL owners=${before.keys}")
            appendLine("EXPERIMENTAL owners=${after.keys}")
            appendLine("added=${after.keys - before.keys}")
            appendLine("removed=${before.keys - after.keys}")
            appendLine("shared=${before.keys intersect after.keys}")
            appendLine("prescriptionChanged=" + (before.keys intersect after.keys).filter { prescriptions(before.getValue(it)) != prescriptions(after.getValue(it)) })
            appendLine("B7=${c.experimentalReadinessAudit}")
            appendLine("B8=${c.productionCutoverAuthority}")
            appendLine("route=${result.routeDecision.selectedSource} builds=${result.buildCounts}")
            appendLine("materialization=${c.prescriptionMaterializationAudits}")
            appendLine("realization=${c.prescriptionRealizationPlan}")
            appendLine("selectionTraces=${c.selectionPlan.traces}")
            appendLine("materializationTraces=${c.materializationTraces}")
            appendLine("allocator=${c.experimental.personalizedDecision?.planningBudget?.execution}")
            listOf("CONTROL" to c.control, "EXPERIMENTAL" to c.experimental).forEach { (label, program) ->
                appendLine("$label schedule=${program.weekDaySchedule}")
                program.items.forEach { row ->
                    appendLine("$label row owner=${identity(row)} slot=${row.weekNumber}/${row.dayOfWeek}/${row.orderIndex} sets=${row.setPrescriptions} rest=${row.restSeconds} weightSource=${row.weightSource} prescription=${row.prescription} setCount=${row.setCount} reps=${row.reps} weight=${row.weightKg} seconds=${row.seconds}")
                }
            }
        }
    }

    private fun renderC10TraceCoverage(records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>): String {
        data class Delta(val caseId: String, val comparison: StimulusSelectionProgramComparison, val kind: String,
            val owner: StimulusPrescriptionOwnerIdentity, val week: Int,
            val before: List<ProgramSkeletonItem>, val after: List<ProgramSkeletonItem>)
        val totals = sortedMapOf<String, Int>()
        val exact = sortedMapOf<String, Int>()
        val deltas = mutableListOf<Delta>()
        records.sortedBy { it.first.label }.forEach { (spec, result) -> result?.comparison?.let { comparison ->
            fun byOwnerWeek(rows: List<ProgramSkeletonItem>) = rows.groupBy {
                StimulusPrescriptionOwnerIdentity(it.exerciseStableKey, it.selectionRole) to it.weekNumber
            }
            val before = byOwnerWeek(comparison.control.items)
            val after = byOwnerWeek(comparison.experimental.items)
            (before.keys + after.keys).distinct().sortedWith(compareBy({ it.first.stableKey }, { it.first.selectionRole }, { it.second }))
                .forEach { (owner, week) ->
                    val oldRows = before[owner to week].orEmpty().sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex }, { it.localId }))
                    val newRows = after[owner to week].orEmpty().sortedWith(compareBy({ it.dayOfWeek }, { it.orderIndex }, { it.localId }))
                    val kind = when {
                        oldRows.isEmpty() && newRows.isNotEmpty() -> "added_owner"
                        oldRows.isNotEmpty() && newRows.isEmpty() -> "removed_owner"
                        oldRows.sumOf { it.setCount } != newRows.sumOf { it.setCount } -> "set_change"
                        oldRows.map { it.prescription to it.setPrescriptions } != newRows.map { it.prescription to it.setPrescriptions } -> "prescription_change"
                        oldRows.map { it.dayOfWeek to it.orderIndex } != newRows.map { it.dayOfWeek to it.orderIndex } &&
                            oldRows.map { it.dayOfWeek } != newRows.map { it.dayOfWeek } -> "placement_move"
                        oldRows.map { it.orderIndex } != newRows.map { it.orderIndex } -> "order_change"
                        else -> null
                    } ?: return@forEach
                    val delta = Delta(spec.label, comparison, kind, owner, week, oldRows, newRows)
                    deltas += delta
                    totals[kind] = (totals[kind] ?: 0) + 1
                }
        } }
        fun stateMatches(state: OwnerAllocationState?, row: ProgramSkeletonItem, owner: StimulusPrescriptionOwnerIdentity): Boolean =
            state != null && state.selectionRole == owner.selectionRole && state.week == row.weekNumber &&
                (state.day == null || state.day == row.dayOfWeek) && (state.order == null || state.order == row.orderIndex) &&
                state.setCount == row.setCount && state.setPrescriptions == row.setPrescriptions && state.prescription == row.prescription
        fun materialMatches(state: OwnerAllocationState?, row: ProgramSkeletonItem, owner: StimulusPrescriptionOwnerIdentity): Boolean =
            state != null && state.selectionRole == owner.selectionRole && state.week == row.weekNumber &&
                state.setCount == row.setCount && state.setPrescriptions == row.setPrescriptions && state.prescription == row.prescription
        fun placementMatches(state: OwnerAllocationState?, row: ProgramSkeletonItem, owner: StimulusPrescriptionOwnerIdentity,
            includeOrder: Boolean): Boolean = state != null && state.selectionRole == owner.selectionRole && state.week == row.weekNumber &&
            state.day == row.dayOfWeek && (!includeOrder || state.order == row.orderIndex) && state.setCount == row.setCount &&
            state.setPrescriptions == row.setPrescriptions && state.prescription == row.prescription
        fun trace(program: GeneratedProgramSkeleton) = program.personalizedDecision?.planningBudget?.execution?.ownerAllocationProvenance.orEmpty()
        fun exactOrigin(delta: Delta, comparison: StimulusSelectionProgramComparison): Boolean {
            val beforeTrace = trace(comparison.control)
            val afterTrace = trace(comparison.experimental)
            return when (delta.kind) {
                "added_owner" -> afterTrace.any { event -> event.owner == delta.owner &&
                    event.action in setOf(OwnerAllocationAction.ADDED, OwnerAllocationAction.FREQUENCY_REPLICATED,
                        OwnerAllocationAction.PLACEMENT_ASSIGNED) && delta.after.any { row -> materialMatches(event.after, row, delta.owner) } }
                "removed_owner" -> afterTrace.any { event -> event.owner == delta.owner && event.action == OwnerAllocationAction.REMOVED &&
                    event.after == null && delta.before.any { row -> stateMatches(event.before, row, delta.owner) } }
                "set_change" -> {
                    val events = beforeTrace + afterTrace
                    events.any { event -> event.owner == delta.owner &&
                    event.action in setOf(OwnerAllocationAction.SET_COUNT_REDUCED, OwnerAllocationAction.SET_COUNT_EXPANDED) &&
                    delta.before.any { old -> stateMatches(event.before, old, delta.owner) } &&
                    delta.after.any { new -> stateMatches(event.after, new, delta.owner) } } ||
                    events.any { reverse -> reverse.owner == delta.owner &&
                        reverse.action in setOf(OwnerAllocationAction.SET_COUNT_REDUCED, OwnerAllocationAction.SET_COUNT_EXPANDED) &&
                        delta.after.any { new -> stateMatches(reverse.before, new, delta.owner) } &&
                        delta.before.any { old -> stateMatches(reverse.after, old, delta.owner) } }
                }
                "prescription_change" -> (beforeTrace + afterTrace).any { event -> event.owner == delta.owner &&
                    event.action == OwnerAllocationAction.PRESCRIPTION_CHANGED &&
                    delta.before.any { old -> stateMatches(event.before, old, delta.owner) } &&
                    delta.after.any { new -> stateMatches(event.after, new, delta.owner) } }
                "placement_move" -> beforeTrace.any { event -> event.owner == delta.owner &&
                    event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                        OwnerAllocationAction.ORDER_CHANGED) && delta.before.any { row -> placementMatches(event.after, row, delta.owner, false) } } &&
                    afterTrace.any { event -> event.owner == delta.owner &&
                        event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                            OwnerAllocationAction.ORDER_CHANGED) && delta.after.any { row -> placementMatches(event.after, row, delta.owner, false) } }
                "order_change" -> beforeTrace.any { event -> event.owner == delta.owner &&
                    event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                        OwnerAllocationAction.ORDER_CHANGED) && delta.before.any { row -> placementMatches(event.after, row, delta.owner, true) } } &&
                    afterTrace.any { event -> event.owner == delta.owner &&
                        event.action in setOf(OwnerAllocationAction.PLACEMENT_ASSIGNED, OwnerAllocationAction.PLACEMENT_MOVED,
                            OwnerAllocationAction.ORDER_CHANGED) && delta.after.any { row -> placementMatches(event.after, row, delta.owner, true) } }
                else -> false
            }
        }
        val unproven = mutableListOf<String>()
        deltas.forEach { delta ->
            if (exactOrigin(delta, delta.comparison)) exact[delta.kind] = (exact[delta.kind] ?: 0) + 1
            else {
                val decision = delta.comparison.experimental.personalizedDecision
                val selectedAtB5 = delta.owner in delta.comparison.selectionPlan.selectedCandidates.map {
                    StimulusPrescriptionOwnerIdentity(it.stableKey, it.selectionRole)
                }
                val inFiniteDemand = delta.owner in decision?.frequencyDemand?.candidates.orEmpty().map {
                    StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role)
                }
                val authorizedForScheduling = delta.owner in decision?.authorizedScheduling?.authorized.orEmpty().map {
                    StimulusPrescriptionOwnerIdentity(it.item.stableKey, it.item.role)
                }
                val boundary = when {
                    authorizedForScheduling -> "owner_reached_authorized_scheduling"
                    inFiniteDemand -> "owner_reached_finite_demand_not_authorized_for_scheduling"
                    selectedAtB5 -> "b5_selected_before_finite_demand"
                    else -> "no_exact_owner_in_exp_b5_or_finite_demand"
                }
                unproven += "${delta.caseId}:${delta.kind}:${delta.owner.stableKey}#${delta.owner.selectionRole}:w${delta.week}[$boundary]"
            }
        }
        val categories = listOf("added_owner", "removed_owner", "set_change", "prescription_change", "placement_move", "order_change")
        val counts = categories.joinToString("; ") { kind ->
            val count = totals[kind] ?: 0
            "$kind=$count/${exact[kind] ?: 0}/${count - (exact[kind] ?: 0)}"
        }
        return "$counts; unproven=${unproven.joinToString(",")}"
    }

    @Test
    fun resolvedRequestParityCoversExplicitAndInferredInputs() = runBlocking {
        val cases = listOf(
            CoverageSpec("request_strength_explicit", TrainableQuality.STRENGTH, "barbell_back_squat", "STRENGTH_GAIN",
                ProgramGoal.STRENGTH, StrengthIntent.STRENGTH_PRIORITY, false, "reviewed", 2, 30,
                setOf("BARBELL", "DUMBBELL", "BENCH", "RACK"), explicitWeeklyDays = true, explicitDuration = true),
            CoverageSpec("request_hypertrophy_inferred", TrainableQuality.HYPERTROPHY, "cable_rear_delt_fly", "HYPERTROPHY_PHYSIQUE",
                ProgramGoal.BODYBUILDING, StrengthIntent.HYPERTROPHY_PRIORITY, false, "reviewed", 3, 60,
                setOf("MACHINE", "CABLE"), explicitWeeklyDays = false, explicitDuration = false),
            CoverageSpec("request_mixed_inferred_duration", TrainableQuality.STRENGTH, "barbell_back_squat", "MIXED",
                ProgramGoal.FUNCTIONAL_CONDITIONING, StrengthIntent.MIXED, false, "mixed", 4, 90,
                emptySet(), explicitWeeklyDays = true, explicitDuration = false),
            CoverageSpec("request_badminton_inferred_days", TrainableQuality.STRENGTH, "barbell_back_squat", "MIXED",
                ProgramGoal.BADMINTON_SUPPORT, StrengthIntent.MIXED, true, "reviewed", 5, 30,
                emptySet(), explicitWeeklyDays = false, explicitDuration = true)
        )
        cases.forEach { spec ->
            val result = requireNotNull(runCase(spec) { service, preflight, answers, metadata ->
                service.generatePreparedProduction(preflight, answers, metadata)
            })
            val comparison = requireNotNull(result.comparison)
            val resolved = comparison.control.request
            assertEquals("${spec.label} complete request parity", resolved, comparison.experimental.request)
            assertEquals("${spec.label} audit horizon", resolved.durationWeeks,
                requireNotNull(comparison.experimentalAudit).planningHorizonWeeks)
            val frequency = requireNotNull(comparison.experimental.personalizedDecision?.frequencyDemand?.frequency)
            assertEquals("${spec.label} frequency request days", resolved.weeklyTrainingDays, frequency.resolvedUserDays)
            assertEquals("${spec.label} frequency source",
                if (spec.explicitWeeklyDays) PlanningFrequencySource.EXPLICIT_USER else PlanningFrequencySource.AUTO,
                frequency.source)
            assertEquals("${spec.label} session minutes", spec.minutes, resolved.sessionMinutes)
            assertTrue("${spec.label} days stay in the bounded range", resolved.weeklyTrainingDays in 2..5)
            assertTrue("${spec.label} duration stays in the bounded horizon", resolved.durationWeeks in 2..6)
            if (spec.explicitDuration) assertEquals("${spec.label} explicit duration", 2, resolved.durationWeeks)
        }
    }

    private fun render(records: List<Pair<CoverageSpec, StimulusProductionGenerationResult?>>): String = buildString {
        fun names(values: Iterable<Any?>) = values.map { it.toString() }.distinct().sorted().joinToString(",")
        val generated = records.mapNotNull { (spec, result) -> result?.let { spec to it } }
        appendLine("TOTAL CASES: ${records.size}")
        appendLine("PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY: ${records.size - generated.size}")
        appendLine("GENERATED CASES: ${generated.size}")
        StimulusProductionProgramSource.entries.forEach { source ->
            appendLine("SOURCE $source: ${generated.count { it.second.routeDecision.selectedSource == source }}")
        }
        StimulusProductionFallbackStage.entries.forEach { stage ->
            appendLine("PRIMARY $stage: ${generated.count { it.second.diagnostics.primaryFallbackStage == stage }}")
        }
        TrainableQuality.entries.forEach { quality ->
            val targets = generated.count { (_, r) -> r.comparison?.targetPlan?.qualityTargets.orEmpty().any { it.quality == quality } }
            val selected = generated.count { (_, r) -> r.comparison?.selectionPlan?.selectedCandidates.orEmpty().any { "QUALITY:${quality.name}" in it.coveredTargetIds } }
            val material = generated.count { (_, r) -> quality in r.diagnostics.scopeResolution?.materialQualities.orEmpty() }
            // Needs/targets alone are never counted as blockers. Require governed material
            // provenance and a failed resolution explicitly identifying unsupported quality.
            val blockers = generated.count { (_, r) ->
                val scope = r.diagnostics.scopeResolution
                r.routeDecision.selectedSource == StimulusProductionProgramSource.CONTROL &&
                    scope?.status == StimulusProductionScopeResolutionStatus.UNSUPPORTED_QUALITY &&
                    "UNSUPPORTED_QUALITY_${quality.name}" in scope.reasonCodes
            }
            appendLine("QUALITY $quality: targets=$targets selected=$selected material=$material unsupportedBlockers=$blockers")
        }
        records.sortedBy { it.first.label }.forEach { (spec, r) ->
            if (r == null) {
                appendLine("CASE ${spec.label}: history=none intent=${spec.intent} goal=${spec.goal} badminton=${spec.badminton} days=${spec.days} minutes=${spec.minutes} equipment=${names(spec.equipment)}")
                appendLine("FINAL PREFLIGHT_REJECTED_NO_CONFIRMED_HISTORY; targets/material/scope/B6/B7/B8/B9=NOT_REACHED; builds=0/0/0/0")
                return@forEach
            }
            val c = r.comparison
            val d = r.diagnostics
            val scope = d.scopeResolution
            val auth = c?.prescriptionAuthorizationPlan?.authorizations.orEmpty()
            appendLine("CASE ${spec.label}: intent=${spec.intent} goal=${spec.goal} badminton=${spec.badminton} history=${spec.history} days=${spec.days} minutes=${spec.minutes} equipment=${names(spec.equipment)} isolated=${spec.isolateOwner}")
            appendLine("C7 CONTROL stableKeys=${names(c?.controlStableKeys.orEmpty())}")
            appendLine("C7 B4 targets=${names(c?.targetPlan?.qualityTargets.orEmpty().map { "${it.quality}/${it.strategy}/${it.numericAuthority}" })}")
            appendLine("C7 B5 selectedOwners=${names(c?.selectionPlan?.selectedCandidates.orEmpty().map { "${it.stableKey}#${it.selectionRole}" })}")
            appendLine("C7 EXPERIMENTAL stableKeys=${names(c?.experimentalStableKeys.orEmpty())}")
            appendLine("C7 added=${names(c?.addedStableKeys.orEmpty())} removed=${names(c?.removedStableKeys.orEmpty())}")
            appendLine("C7 B6 authorizedOwners=${names(auth.filter { it.authorizedPrescription != null }.mapNotNull { row -> row.owner?.let { "${row.targetId}:${it.stableKey}#${it.selectionRole}" } })}")
            appendLine("C7 routeAfter=${r.routeDecision.selectedSource} B7=${c?.experimentalReadinessAudit?.status} B8=${c?.productionCutoverAuthority?.status} B7reasons=${c?.experimentalReadinessAudit?.reasonCodes?.sorted()} B8reasons=${c?.productionCutoverAuthority?.reasonCodes?.sorted()}")
            appendLine("C7 safety B7shadowOnly=${c?.experimentalReadinessAudit?.shadowOnly} B7productionAuthority=${c?.experimentalReadinessAudit?.productionAuthority} B7winner=${c?.experimentalReadinessAudit?.winner} B8routingActive=${c?.productionCutoverAuthority?.routingActive} B8mutationAuthority=${c?.productionCutoverAuthority?.productionMutationAuthority} B9source=${r.routeDecision.selectedSource} builds=${r.buildCounts.controlBuilds}/${r.buildCounts.experimentalBuilds}/${r.buildCounts.totalBuildInvocations}/${r.buildCounts.thirdBuilds}")
            appendLine("targets=${names(c?.targetPlan?.qualityTargets.orEmpty().map { "${it.quality}/${it.strategy}/${it.numericAuthority}" })}")
            appendLine("material=${names(scope?.materialQualities.orEmpty())} owners=${names(scope?.materialOwnerIdentities.orEmpty())}")
            appendLine("removedOwners=${names(scope?.removedOwnerIdentities.orEmpty())}")
            appendLine("scope=${scope?.scope} status=${scope?.status} reasons=${scope?.reasonCodes} unknown=${scope?.unknownTargetIds} unattributed=${names(scope?.unattributedOwnerIdentities.orEmpty())}")
            appendLine("B6 executable=${names(auth.filter { it.executionAuthority == StimulusPrescriptionExecutionAuthority.FULLY_ENCODED && it.status in setOf(StimulusPrescriptionAuthorizationStatus.AUTHORIZED_EXISTING_COMPATIBLE, StimulusPrescriptionAuthorizationStatus.AUTHORIZED_SAFE_REPAIR) }.mapNotNull { it.quality })}")
            appendLine("B6 resolutions=${names(c?.prescriptionRealizationPlan?.resolutions.orEmpty().map { "${it.quality}/${it.status}/${it.reasonCodes.sorted()}" })}")
            appendLine("B6 authorities=${names(auth.map { "${it.targetId}/${it.owner}/${it.status}/${it.executionAuthority}/${it.reasonCodes.sorted()}" })} conflicts=${names(c?.prescriptionAuthorizationPlan?.conflictingOwners.orEmpty())}")
            appendLine("B6 materialization=${names(c?.prescriptionMaterializationAudits.orEmpty().map { "${it.quality}/${it.owner}/${it.state}/${it.executionAuthority}/${it.reasonCodes.sorted()}" })}")
            appendLine("B7 ${c?.experimentalReadinessAudit?.status} ${c?.experimentalReadinessAudit?.reasonCodes?.sorted()}")
            appendLine("B8 ${c?.productionCutoverAuthority?.scope} ${c?.productionCutoverAuthority?.status} ${c?.productionCutoverAuthority?.reasonCodes?.sorted()}")
            appendLine("B9 ${r.routeDecision.selectedSource} ${r.routeDecision.reasonCodes}")
            appendLine("FINAL ${if (r.routeDecision.productionRoutingActive) "EXPERIMENTAL" else "CONTROL"} primary=${d.primaryFallbackStage} secondary=${d.secondaryReasonCodes} builds=${r.buildCounts}")
        }
    }

    internal suspend fun runCase(
        spec: CoverageSpec,
        evaluate: (suspend (PersonalizedProgramPlanningService, PersonalizedPlanningPreflight, PersonalizedPlanningAnswers,
            Map<String, RuntimeExerciseMetadata>) -> StimulusProductionGenerationResult)? = null
    ): StimulusProductionGenerationResult? {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = TrainingRepository(db, context)
            repository.seedIfNeeded()
            db.workoutDao().allEntries().forEach { db.workoutDao().deleteEntryById(it.id) }
            db.initialUserProfileDao().upsert(InitialUserProfile(
                primaryGoal = spec.profileGoal,
                strengthTrainingYears = 2.0, badmintonTrainingYears = if (spec.badminton) 2.0 else 0.0,
                strengthSessionsPerWeek = spec.days.toDouble(), strengthMinutesPerSession = spec.minutes, habitualTrainingIntensity = "NORMAL"
            ))
            val cutoff = LocalDate.of(2026, 9, 20)
            val exercise = requireNotNull(db.exerciseDao().findByStableKey(spec.stableKey)) { "B14.1 missing seeded exercise ${spec.stableKey}" }
            if (spec.history != "none") {
                val days = if (spec.quality == TrainableQuality.STRENGTH) listOf(7L, 9L, 14L, 16L, 21L, 23L, 28L, 30L, 35L, 37L, 42L, 44L, 49L, 51L) else listOf(7L, 9L, 14L, 16L, 21L, 23L, 28L, 30L, 35L, 37L, 42L, 44L)
                days.filter { when (spec.history) { "sparse" -> it == 7L; "recent" -> it <= 21L; else -> true } }.forEachIndexed { index, daysAgo ->
                    val entryId = db.workoutDao().insertEntry(WorkoutEntry(date = cutoff.minusDays(daysAgo).toString(), exerciseStableKey = exercise.stableKey, exerciseName = exercise.name, category = exercise.category, sessionStableKey = "b141-${spec.label}-$index"))
                    (1..3).forEach { setIndex ->
                        val reps = if (spec.quality == TrainableQuality.STRENGTH) if (daysAgo >= 42L) 5 else 8 else if (daysAgo <= 23L) 5 else 10
                        db.workoutDao().insertSet(WorkoutSet(entryId = entryId, setIndex = setIndex, reps = reps, weightKg = 40.0, confirmed = true, rpe = 8.0))
                    }
                }
                if (spec.quality == TrainableQuality.STRENGTH && spec.history in setOf("reviewed", "mixed")) {
                    val posteriorDao = db.strengthPosteriorDao(); val revisionKey = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
                    if (posteriorDao.revision(revisionKey) == null) posteriorDao.insertRevisionStrict(StrengthModelRevisionPolicy.current(1L, null).copy(status = StrengthModelRevisionPolicy.STATUS_ACTIVE, rebuildCompletedAt = 1L)) else posteriorDao.updateRevisionStatus(revisionKey, StrengthModelRevisionPolicy.STATUS_ACTIVE, 1L, null, null)
                    posteriorDao.insertLocalHistoryStrict(listOf(StrengthExercisePerformanceHistoryEntity(revisionKey = revisionKey, eventUuid = "b141-${spec.label}", sessionKey = "b141-${spec.label}", sessionDate = cutoff.minusDays(55).toString(), exerciseStableKey = spec.stableKey, priorLogMean = ln(50.0), priorLogVariance = 0.1, sessionLikelihoodLogMean = null, sessionLikelihoodLogVariance = null, sessionLikelihoodProper = true, innovationResidualLog = null, innovationVariance = null, posteriorLogMean = ln(50.0), posteriorLogVariance = 0.1, posteriorMeanIncrementLog = 0.0, transitionDays = 1L, baselineEstablishedBefore = true, baselineEstablishedAfter = true, proxyTransferEligible = false, proxyTransferApplied = false, modelVersion = "B6_TEST", curveVersion = "B6_TEST", rirPolicyVersion = "B6_TEST", evidenceFingerprint = "b141-${spec.label}", createdAt = 1L)))
                }
            }
            if (spec.history == "mixed") {
                val other = requireNotNull(db.exerciseDao().findByStableKey("cable_rear_delt_fly"))
                (1..8).forEach { week ->
                    val entry = db.workoutDao().insertEntry(WorkoutEntry(date = cutoff.minusDays(week * 7L).toString(), exerciseStableKey = other.stableKey, exerciseName = other.name, category = other.category, sessionStableKey = "mixed-${spec.label}-$week"))
                    (1..3).forEach { index -> db.workoutDao().insertSet(WorkoutSet(entryId = entry, setIndex = index, reps = 10, weightKg = 12.5, confirmed = true, rpe = 8.0)) }
                }
            }
            val editor = field(repository, "exerciseMetadataEditorService") as ExerciseMetadataEditorService
            val metadata = editor.resolvedRuntimeMetadataByExerciseStableKey()
            val service = field(repository, "personalizedProgramPlanningService") as PersonalizedProgramPlanningService
            val catalog = field(service, "physicalQualityCatalog") as CanonicalExercisePhysicalQualityCatalog
            val excluded = if (spec.isolateOwner) metadata.keys.filter { key -> key != spec.stableKey && catalog.relations(key).any { it.qualityId == spec.quality && it.relationLevel == StimulusCapabilityLevel.DIRECT_CAPABILITY } }.toSet() else emptySet()
            val goal = spec.goal
            val request = ProgramSkeletonRequest(
                name = "Coverage ${spec.label}", goal = goal, weeklyTrainingDays = spec.days, sessionMinutes = spec.minutes,
                availableEquipment = spec.equipment, excludedExerciseText = "",
                badmintonTransferRatio = if (spec.badminton) 0.5 else 0.0,
                sportStrengthRatio = "AUTO", periodizationType = ProgramPeriodizationType.AUTO,
                durationWeeks = 2, excludedExerciseStableKeys = excluded
            )
            val constraints = PersonalizedGenerationConstraints(
                explicitGoal = goal,
                explicitWeeklyTrainingDays = spec.days.takeIf { spec.explicitWeeklyDays },
                explicitDurationWeeks = if (spec.explicitDuration) 2 else null,
                explicitSessionMinutes = spec.minutes
            )
            val preflight = try {
                repository.preparePersonalizedProgram(request, constraints, cutoff)
            } catch (failure: IllegalArgumentException) {
                // Existing record-based planner precondition, not a CONTROL routing outcome.
                // Catch only this exact no-history boundary; all other errors must fail the test.
                if (spec.history == "none" && failure.message == "기록 기반 계획에 사용할 완료 세트가 없습니다.") return null
                throw failure
            }
            val answers = PersonalizedPlanningAnswers(preflight.questions.associate { question -> question.id to when (question.id) {
                QUESTION_STRENGTH_INTENT -> spec.intent.name
                QUESTION_BADMINTON_INTENT -> if (spec.badminton) BadmintonPlanningIntent.ENABLED.name else BadmintonPlanningIntent.DISABLED.name
                QUESTION_FREE_WEIGHT -> FreeWeightWillingness.WILLING.name
                QUESTION_INTERRUPTION_CAUSE, QUESTION_INTERRUPTION_FREQUENCY -> "UNSURE"
                else -> if (question.id.startsWith("INTERRUPTION_CAUSE_")) "UNKNOWN" else error("Unexpected personalized question: ${question.id}")
            } })
            val production = evaluate?.invoke(service, preflight, answers, metadata)
                ?: repository.generatePreparedPersonalizedProgramEvaluation(preflight, answers)
            return production
        } finally { db.close() }
    }

    private fun field(target: Any, name: String): Any = requireNotNull(target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target))
}

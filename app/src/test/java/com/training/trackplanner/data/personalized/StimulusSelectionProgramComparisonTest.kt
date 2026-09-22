package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonItem
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.ProgramSkeletonRequest
import com.training.trackplanner.data.ProgramWeekPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray

class StimulusSelectionProgramComparisonTest {
    @Test
    fun comparisonCarriesAuditsAndNeverDeclaresWinner() {
        val request = request()
        val control = skeleton(request, item("control"))
        val experimental = skeleton(request, item("experimental"))
        val plan = StimulusTargetPlan(emptyList(), emptyList(), emptyList())
        val selection = StimulusCandidateSelectionPlan(
            selectedCandidates = emptyList(),
            traces = emptyList(),
            materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap())
        )
        val audit = StimulusTargetControlProgramAudit(1, emptyList(), emptyList())

        val comparison = StimulusSelectionProgramComparisonEngine().compare(
            control, experimental, plan, selection, audit, audit
        )

        assertEquals(audit, comparison.controlAudit)
        assertEquals(audit, comparison.experimentalAudit)
        assertTrue(comparison.winner == null)
        assertFalse(comparison.differences.single().prescriptionShapeChanged)
    }

    @Test
    fun compactPayloadExposesIdentitySetsAndB5AuthorityFlags() {
        val request = request()
        val control = skeleton(request, item("control"))
        val experimental = skeleton(request, item("experimental"))
        val selection = StimulusCandidateSelectionPlan(
            selectedCandidates = emptyList(), traces = emptyList(),
            materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap())
        )
        val comparison = StimulusSelectionProgramComparisonEngine().compare(
            control, experimental, StimulusTargetPlan(emptyList(), emptyList(), emptyList()), selection, null, null
        )
        val json = comparison.toCompactJson()
        assertEquals(listOf("experimental"), json.getJSONArray("addedStableKeys").toList())
        assertEquals(listOf("control"), json.getJSONArray("removedStableKeys").toList())
        assertFalse(json.getJSONObject("selectionPlan").getBoolean("productionSelectionAuthority"))
    }

    @Test
    fun materializationTraceCountsDistinctOccurrencesAndSetUnits() {
        val request = request()
        val key = "candidate"
        val selection = selectionPlan(
            selected = StimulusSelectedCandidate(key, setOf("QUALITY:STRENGTH"), "QUALITY:STRENGTH", emptyList(), "EXISTING", 2, "B5"),
            trace = StimulusCandidateSelectionTrace(
                targetId = "QUALITY:STRENGTH", strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
                priority = TargetPriority.PRIMARY, controlDirectCapabilityIdentities = emptyList(),
                selectionRequired = true, candidatePool = listOf(key), selectedStableKey = key,
                coveredByPreviouslySelectedStableKey = null
            )
        )
        val comparison = StimulusSelectionProgramComparisonEngine().compare(
            skeleton(request, item(key, 1, 2)),
            skeleton(request, item(key, 1, day = 1), item(key, 3, day = 2)),
            StimulusTargetPlan(emptyList(), emptyList(), emptyList()), selection, null, null
        )
        val trace = comparison.materializationTraces.single()
        assertEquals(key, trace.selectedStableKey)
        assertTrue(trace.selectedAtB5)
        assertTrue(trace.presentInFinalExperimentalSkeleton)
        assertEquals(2, trace.finalWeeklyOccurrences)
        assertEquals(5, trace.finalTotalSetUnits)
        assertTrue(trace.reasonCodes.contains("SELECTION_TARGET_IDENTITY_MATERIALIZED"))
    }

    @Test
    fun materializationTraceDistinguishesAbsentAndReusedIdentitiesAndNoSelection() {
        val request = request()
        val reused = "reused"
        val selection = StimulusCandidateSelectionPlan(
            selectedCandidates = listOf(StimulusSelectedCandidate(reused, setOf("QUALITY:STRENGTH"), "QUALITY:STRENGTH", emptyList(), "EXISTING", 2, "B5")),
            traces = listOf(
                StimulusCandidateSelectionTrace("QUALITY:STRENGTH", StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, TargetPriority.PRIMARY, emptyList(), true, listOf("missing"), "missing", null),
                StimulusCandidateSelectionTrace("QUALITY:HYPERTROPHY", StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, TargetPriority.SECONDARY, emptyList(), false, emptyList(), null, reused),
                StimulusCandidateSelectionTrace("TASK:CONTROL", StimulusDoseStrategy.HOLD_PERSONAL_BASELINE, TargetPriority.MAINTENANCE, listOf("control"), false, emptyList(), null, null, reasonCodes = listOf("DIRECT_CAPABILITY_IDENTITY_ALREADY_PRESENT"))
            ),
            materialDemand = MaterialDemand(emptyList(), emptyMap(), emptyMap())
        )
        val comparison = StimulusSelectionProgramComparisonEngine().compare(
            skeleton(request, item("control")), skeleton(request, item(reused)),
            StimulusTargetPlan(emptyList(), emptyList(), emptyList()), selection, null, null
        )
        assertTrue(comparison.materializationTraces[0].reasonCodes.contains("CANDIDATE_SELECTED_BUT_NOT_MATERIALIZED"))
        assertEquals(reused, comparison.materializationTraces[1].selectedStableKey)
        assertTrue(comparison.materializationTraces[1].reasonCodes.contains("SELECTION_TARGET_IDENTITY_MATERIALIZED"))
        assertTrue(comparison.materializationTraces[2].reasonCodes.contains("CONTROL_DIRECT_IDENTITY_ALREADY_PRESENT"))
        assertTrue(comparison.materializationTraces[2].selectedStableKey == null)
        assertTrue(comparison.winner == null)
    }

    @Test
    fun materializationTraceKeepsB6CompatibilityGapExplicit() {
        val request = request()
        val key = "candidate"
        val target = StimulusQualityTarget(
            quality = com.training.trackplanner.data.TrainableQuality.STRENGTH,
            strategy = StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS,
            priority = TargetPriority.PRIMARY,
            numericAuthority = StimulusTargetNumericAuthority.DIRECTION_ONLY,
            baselineSource = null,
            baselineConfidence = null,
            weeklyDirectUnitsTarget = null,
            weeklyDirectSessionsTarget = null,
            exposureWeekDirectUnitsReference = null,
            exposureWeekDirectSessionsReference = null,
            exposureWeekFrequencyReference = null,
            reasonCodes = emptyList(), evidence = emptyList()
        )
        val selection = selectionPlan(
            selected = StimulusSelectedCandidate(key, setOf("QUALITY:STRENGTH"), "QUALITY:STRENGTH", emptyList(), "PRESCRIPTION_COMPATIBILITY_GAP_DEFERRED_TO_B6", 0, "B5"),
            trace = StimulusCandidateSelectionTrace("QUALITY:STRENGTH", StimulusDoseStrategy.INTRODUCE_DIRECT_STIMULUS, TargetPriority.PRIMARY, emptyList(), true, listOf(key), key, null)
        )
        val audit = StimulusTargetControlProgramAudit(
            planningHorizonWeeks = 1,
            qualityAudits = listOf(StimulusQualityControlProgramAudit(
                com.training.trackplanner.data.TrainableQuality.STRENGTH, target.strategy, target.numericAuthority,
                null, 0.0, StimulusTargetControlStatus.DIRECT_ABSENT, null, null,
                StimulusTargetControlStatus.DIRECT_ABSENT, null, null, null, emptyList()
            )), taskAudits = emptyList()
        )
        val comparison = StimulusSelectionProgramComparisonEngine().compare(
            skeleton(request, item("control")), skeleton(request, item(key)),
            StimulusTargetPlan(listOf(target), emptyList(), emptyList()), selection, null, audit
        )
        val reasons = comparison.materializationTraces.single().reasonCodes
        assertTrue(reasons.contains("TARGET_REALIZATION_STILL_UNMET"))
        assertTrue(reasons.contains("PRESCRIPTION_COMPATIBILITY_GAP_DEFERRED_TO_B6"))
    }

    private fun selectionPlan(selected: StimulusSelectedCandidate, trace: StimulusCandidateSelectionTrace) =
        StimulusCandidateSelectionPlan(listOf(selected), listOf(trace), MaterialDemand(emptyList(), emptyMap(), emptyMap()))

    private fun request() = ProgramSkeletonRequest(
        "comparison", ProgramGoal.STRENGTH, 3, 60, emptySet(), "", .5, "AUTO",
        ProgramPeriodizationType.AUTO, 2
    )

    private fun item(key: String, sets: Int = 2, day: Int = 1) = ProgramSkeletonItem(
        localId = "$key-$day", weekNumber = 1, dayOfWeek = day, orderIndex = 1, exerciseStableKey = key,
        exerciseName = key, category = "STRENGTH", restSeconds = 90, prescription = "8 reps",
        setCount = sets, reps = 8, weightKg = 0.0, seconds = 0, selectionReason = "test", weightSource = "TEST",
        setPrescriptions = List(sets) { index -> ProgramSetPrescription(index + 1, 8, 0.0, 0) }
    )

    private fun skeleton(request: ProgramSkeletonRequest, vararg items: ProgramSkeletonItem) = GeneratedProgramSkeleton(
        suggestedName = request.name, durationDays = request.durationWeeks * 7, request = request,
        periodizationType = request.periodizationType,
        weekPlans = listOf(ProgramWeekPlan(1, "TEST", 1.0, 1.0, 2, 8.0, 2, 0, false)),
        items = items.toList()
    )

    private fun JSONArray.toList(): List<Any> = (0 until length()).map(::get)
}

package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton
import com.training.trackplanner.data.ProgramGoal
import com.training.trackplanner.data.ProgramPeriodizationType
import com.training.trackplanner.data.ProgramSkeletonItem
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

    private fun request() = ProgramSkeletonRequest(
        "comparison", ProgramGoal.STRENGTH, 3, 60, emptySet(), "", .5, "AUTO",
        ProgramPeriodizationType.AUTO, 2
    )

    private fun item(key: String) = ProgramSkeletonItem(
        localId = key, weekNumber = 1, dayOfWeek = 1, orderIndex = 1, exerciseStableKey = key,
        exerciseName = key, category = "STRENGTH", restSeconds = 90, prescription = "8 reps",
        setCount = 2, reps = 8, weightKg = 0.0, seconds = 0, selectionReason = "test", weightSource = "TEST"
    )

    private fun skeleton(request: ProgramSkeletonRequest, item: ProgramSkeletonItem) = GeneratedProgramSkeleton(
        suggestedName = request.name, durationDays = request.durationWeeks * 7, request = request,
        periodizationType = request.periodizationType,
        weekPlans = listOf(ProgramWeekPlan(1, "TEST", 1.0, 1.0, 2, 8.0, 2, 0, false)),
        items = listOf(item)
    )

    private fun JSONArray.toList(): List<Any> = (0 until length()).map(::get)
}

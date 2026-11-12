package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPrescriptionSet
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BCoachLinkedSquatPlanPatchTest {
    private val proposal = BCoachLinkedSquatNextWeekProposal(
        status = "READY_FOR_USER_APPROVAL",
        sourceEntryId = 1L, targetEntryId = 2L,
        sourceWeek = 1, targetWeek = 2,
        proposedLoadKg = 141.5, proposedReps = 5, proposedSets = 3,
        selectedAction = "LOAD_UP_ONE_STEP", evidenceFingerprint = "evidence-hash"
    )
    private fun sets() = (1..3).map { i ->
        WorkoutSet(
            id = i.toLong(), entryId = 2L, setIndex = i,
            reps = 6, weightKg = 0.0,
            confirmed = false,
            loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED
        )
    }
    private fun targets() = (1..3).map { i ->
        ProgramPrescriptionSet(
            entryId = 2L, setIndex = i,
            originalReps = 6, originalKg = 0.0, originalSeconds = 0,
            plannedReps = 6, plannedKg = 0.0, plannedSeconds = 0,
            originalLoadState = ProgramLoadState.USER_CALIBRATION_REQUIRED,
            plannedLoadState = ProgramLoadState.USER_CALIBRATION_REQUIRED
        )
    }

    @Test fun `approved load progression creates consistent planned sets without modifying originals`() {
        val beforeSets = sets()
        val beforeTargets = targets()
        val patch = checkNotNull(BCoachLinkedSquatPlanPatch.build(proposal,beforeSets,beforeTargets))
        assertEquals(3, patch.nextSets.size)
        assertTrue(patch.nextSets.all {
            it.weightKg == 141.5 && it.reps == 5 && !it.confirmed &&
                it.loadState == ProgramLoadState.EXPLICIT_LOAD && it.manualWeight
        })
        assertTrue(patch.nextPrescriptions.all {
            it.plannedKg == 141.5 && it.plannedReps == 5 &&
                it.originalKg == 0.0 && it.originalReps == 6 &&
                it.originalLoadState == ProgramLoadState.USER_CALIBRATION_REQUIRED &&
                it.plannedLoadState == ProgramLoadState.EXPLICIT_LOAD
        })
        assertEquals(beforeSets,sets())
        assertEquals(beforeTargets,targets())
        assertTrue(beforeTargets.all { it.originalKg == 0.0 })
    }

    @Test fun `a repetition improvement can preserve load and only update reps`() {
        val reps = proposal.copy(proposedLoadKg=140.0,proposedReps=7,
            selectedAction="REPS_UP_ONE")
        val patch=checkNotNull(BCoachLinkedSquatPlanPatch.build(reps,sets(),targets()))
        assertTrue(patch.nextSets.all { it.weightKg==140.0 && it.reps==7 })
    }

    @Test fun `any confirmed target set blocks the entire patch`() {
        val started=sets().mapIndexed { index, row ->
            if (index==0) row.copy(confirmed=true) else row
        }
        assertNull(BCoachLinkedSquatPlanPatch.build(proposal,started,targets()))
    }

    @Test fun `user edited plan is not silently overwritten`() {
        val changed=targets().map { it.copy(plannedReps=7) }
        assertNull(BCoachLinkedSquatPlanPatch.build(proposal,
            sets().map { it.copy(reps=7) },changed))
        assertNull(BCoachLinkedSquatPlanPatch.build(proposal,
            sets().map { it.copy(weightKg=140.0) },
            targets().map { it.copy(plannedKg=140.0) }))
    }

    @Test fun `wrong linked entry or missing planned set index rejects the patch`() {
        assertNull(BCoachLinkedSquatPlanPatch.build(proposal,
            sets().map { it.copy(entryId=3L) },targets()))
        assertNull(BCoachLinkedSquatPlanPatch.build(proposal,sets(),
            targets().mapIndexed { i,s -> if (i==0) s.copy(plannedSetIndex=null) else s }))
    }

    @Test fun `adding or removing an actual set is not silently authorized`() {
        assertNull(BCoachLinkedSquatPlanPatch.build(proposal.copy(proposedSets=4),
            sets(),targets()))
        assertNull(BCoachLinkedSquatPlanPatch.build(proposal,
            sets().take(2),targets()))
    }

    @Test fun `a stale or nonapproved suggestion cannot create a patch`() {
        assertNull(BCoachLinkedSquatPlanPatch.build(
            proposal.copy(status="RANKING_NOT_AVAILABLE"),sets(),targets()))
        assertNull(BCoachLinkedSquatPlanPatch.build(
            proposal.copy(evidenceFingerprint=null),sets(),targets()))
    }
}

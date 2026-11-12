package com.training.trackplanner.data.v22

import com.training.trackplanner.data.ProgramApplication
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramPrescriptionSet
import com.training.trackplanner.data.ProgramWorkoutLink
import com.training.trackplanner.data.ProgressionBase
import com.training.trackplanner.data.ProgressionLinkMode
import com.training.trackplanner.data.ProgressionMode
import com.training.trackplanner.data.ProgressionRole
import com.training.trackplanner.data.ProgressionRule
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BCoachLinkedSquatWeekPlannerTest {
    private val today = LocalDate.of(2026, 11, 13)
    private val application = ProgramApplication(
        id = "app-1", programStableKey = "program-1",
        programName = "My program", startDate = "2026-11-01", appliedAt = 100L
    )
    private fun link(
        id: Long, week: Int, seq: Int,
        appId: String = "app-1", track: String = "squat-track"
    ) = ProgramWorkoutLink(
        entryId = id, applicationId = appId, sourceProgramStableKey = "program-1",
        sourceItemId = "item-$id", programName = "My program",
        weekNumber = week, dayOfWeek = 2, trackId = track,
        trackLabel = "squat", sequence = seq, role = ProgressionRole.MAIN,
        mode = ProgressionMode.APP, basePolicy = ProgressionBase.UNIFORM,
        anchorSetIndex = null, needsReview = false, rule = ProgressionRule()
    )
    private fun record(
        id: Long, day: String, confirmed: Boolean,
        kg: Double = if (confirmed) 140.0 else 0.0,
        reps: Int = if (confirmed) 5 else 6,
        rpe: Double? = if (confirmed) 8.0 else null,
        session: String = "session-$id"
    ) = WorkoutEntryWithSets(
        entry = WorkoutEntry(
            id = id, date = day, exerciseStableKey = "barbell_back_squat",
            exerciseName = "Back squat", category = "STRENGTH",
            sessionStableKey = session, createdAt = id
        ),
        sets = (1..3).map { i ->
            WorkoutSet(
                id = id * 10 + i, entryId = id, setIndex = i,
                weightKg = kg, reps = reps, confirmed = confirmed,
                rpe = rpe,
                loadState = if (confirmed) ProgramLoadState.EXPLICIT_LOAD
                    else ProgramLoadState.USER_CALIBRATION_REQUIRED
            )
        }
    )
    private fun targets(
        record: WorkoutEntryWithSets,
        plannedKg: Double = record.sets.first().weightKg,
        originalKg: Double = plannedKg,
        plannedReps: Int = record.sets.first().reps,
        originalReps: Int = plannedReps
    ) = record.sets.map { s ->
        ProgramPrescriptionSet(
            entryId = record.entry.id, setIndex = s.setIndex,
            originalReps = originalReps, originalKg = originalKg, originalSeconds = 0,
            plannedReps = plannedReps, plannedKg = plannedKg, plannedSeconds = 0,
            originalLoadState = if (s.confirmed) ProgramLoadState.EXPLICIT_LOAD
                else ProgramLoadState.USER_CALIBRATION_REQUIRED,
            plannedLoadState = if (s.confirmed) ProgramLoadState.EXPLICIT_LOAD
                else ProgramLoadState.USER_CALIBRATION_REQUIRED
        )
    }
    private val source get() = record(1L, "2026-11-12", true)
    private val target get() = record(2L, "2026-11-19", false)
    private val anchor get() = BCoachSquatAnchorSession(
        sessionStableKey = "session-1", date = LocalDate.of(2026, 11, 12),
        confirmedSets = source.sets.map {
            BCoachConfirmedSquatSet(it.id, it.setIndex, it.weightKg, it.reps, it.rpe)
        }
    )
    private fun ranking(action: String = "LOAD_UP_ONE_STEP", sets: Int = 3) =
        BCoachSquatCurveProgressionReport(
            status = "EXPERIMENTAL_RANKING_ONLY", asOf = today,
            referenceDate = LocalDate.of(2026, 11, 12),
            selectedAction = action, historicalE1rmKg = 164.0,
            targetRir = 2.0, doseTargetSets = 3,
            ranked = listOf(BCoachSquatCurveProgressionReport.Candidate(
                action = action,
                loadKg = if (action == "LOAD_UP_ONE_STEP") 141.5 else 140.0,
                reps = if (action == "REPS_UP_ONE") 6 else 5,
                sets = sets, requiredE1rmKg = 164.0,
                historicalE1rmLogGap = 0.0, worstCaseRegret = 0.0,
                weightedCost = 0.0, targetRirMixtureMass = 0.9,
                volumeLoadChange = 0.01, eligible = true, reason = "EXPERIMENTAL"
            )), notes = emptyList()
        )
    private fun inspect(
        workouts: List<WorkoutEntryWithSets> = listOf(source, target),
        links: List<ProgramWorkoutLink> = listOf(link(1,1,1), link(2,2,2)),
        prescriptions: List<ProgramPrescriptionSet> = targets(source) + targets(target),
        linkModesByLogicalItemId: Map<String, ProgressionLinkMode> =
            mapOf("item-1" to ProgressionLinkMode.AUTO, "item-2" to ProgressionLinkMode.AUTO,
                "item-3" to ProgressionLinkMode.AUTO),
        anchor: BCoachSquatAnchorSession? = this.anchor,
        ranking: BCoachSquatCurveProgressionReport? = ranking()
    ) = BCoachLinkedSquatWeekPlanner.inspect(
        asOf = today, targetEntryId = 2L, workouts = workouts,
        applications = listOf(application), links = links, prescriptions = prescriptions,
        linkModesByLogicalItemId = linkModesByLogicalItemId,
        anchor = anchor, ranking = ranking
    )

    @Test fun `linked next week receives real RPE and curve-ranked increment`() {
        val proposal = inspect()
        assertEquals("READY_FOR_USER_APPROVAL", proposal.status)
        assertTrue(proposal.canApply)
        assertEquals(1L, proposal.sourceEntryId)
        assertEquals(2L, proposal.targetEntryId)
        assertEquals(141.5, proposal.proposedLoadKg!!, 0.0)
        assertEquals(5, proposal.proposedReps)
        assertEquals(3, proposal.proposedSets)
        assertNotNull(proposal.evidenceFingerprint)
        assertEquals(proposal.evidenceFingerprint, inspect().evidenceFingerprint)
    }

    @Test fun `same day squat without explicit program link cannot anchor`() {
        val wrong = inspect(links = listOf(link(2,2,2)))
        assertEquals("NO_LINKED_PREVIOUS_WEEK", wrong.status)
        assertFalse(wrong.canApply)
    }

    @Test fun `another application cannot supply previous week even on same track`() {
        val wrong = inspect(links = listOf(link(1,1,1,appId="app-else"), link(2,2,2)))
        assertEquals("NO_LINKED_PREVIOUS_WEEK", wrong.status)
    }

    @Test fun `another track cannot supply previous week`() {
        val wrong = inspect(links = listOf(link(1,1,1,track="other-track"), link(2,2,2)))
        assertEquals("NO_LINKED_PREVIOUS_WEEK", wrong.status)
    }

    @Test fun `uncalibrated B plan uses same application and exercise despite split REVIEW tracks`() {
        val prior = link(1,1,1,track="week-one").copy(
            basePolicy=ProgressionBase.REVIEW, needsReview=true
        )
        val next = link(2,2,2,track="week-two").copy(
            basePolicy=ProgressionBase.REVIEW, needsReview=true
        )
        val proposal=inspect(links=listOf(prior,next))
        assertEquals("READY_FOR_USER_APPROVAL",proposal.status)
        assertTrue(proposal.canApply)
    }

    @Test fun `ambiguous uncalibrated previous squat occurrences block the proposal`() {
        val duplicate=record(3L,"2026-11-12",true,session="session-3")
        val previous=link(1,1,1,track="w1").copy(basePolicy=ProgressionBase.REVIEW)
        val other=link(3,1,3,track="w1b").copy(basePolicy=ProgressionBase.REVIEW)
        val next=link(2,2,4,track="w2").copy(basePolicy=ProgressionBase.REVIEW)
        val proposal=inspect(workouts=listOf(source,duplicate,target),
            links=listOf(previous,other,next),
            prescriptions=targets(source)+targets(duplicate)+targets(target))
        assertEquals("AMBIGUOUS_LINKED_PREVIOUS_WEEK",proposal.status)
        assertFalse(proposal.canApply)
    }

    @Test fun `manual separate session must not be joined by B review fallback`() {
        val prior = link(1,1,1,track="week-one").copy(basePolicy=ProgressionBase.REVIEW)
        val next = link(2,2,2,track="week-two").copy(basePolicy=ProgressionBase.REVIEW)
        val result=inspect(links=listOf(prior,next),
            linkModesByLogicalItemId=mapOf(
                "item-1" to ProgressionLinkMode.SEPARATE,
                "item-2" to ProgressionLinkMode.AUTO))
        assertEquals("NO_LINKED_PREVIOUS_WEEK",result.status)
    }

    @Test fun `unconfirmed or missed previous sets cannot cause progression`() {
        val partly = source.copy(sets = source.sets.mapIndexed { i, s ->
            if (i == 2) s.copy(confirmed=false) else s
        })
        assertEquals("PREVIOUS_WEEK_NOT_FULLY_SUCCESSFUL",
            inspect(workouts=listOf(partly,target)).status)
        val missed = source.copy(sets = source.sets.map { it.copy(reps=4) })
        assertEquals("PREVIOUS_WEEK_NOT_FULLY_SUCCESSFUL",
            inspect(workouts=listOf(missed,target)).status)
    }

    @Test fun `target started with one confirmed set is always protected`() {
        val started = target.copy(sets=target.sets.mapIndexed { i,s ->
            if (i==0) s.copy(confirmed=true) else s
        })
        assertEquals("TARGET_ALREADY_STARTED", inspect(workouts=listOf(source,started)).status)
    }

    @Test fun `manual change of future planned loads or repetitions is protected`() {
        val edited = target.copy(sets=target.sets.map { it.copy(weightKg=141.5) })
        val rows = targets(source) + targets(edited,originalKg=0.0,plannedKg=141.5)
        assertEquals("USER_EDIT_PROTECTED",
            inspect(workouts=listOf(source,edited),prescriptions=rows).status)
    }

    @Test fun `stale or unrelated canonical anchor is rejected even if date matches`() {
        val other = anchor.copy(sessionStableKey="someone-else")
        assertEquals("LATEST_CANONICAL_ANCHOR_NOT_LINKED_SOURCE",
            inspect(anchor=other).status)
        assertEquals("LATEST_CANONICAL_ANCHOR_NOT_LINKED_SOURCE",
            inspect(anchor=anchor.copy(confirmedSets=anchor.confirmedSets.drop(1))).status)
    }

    @Test fun `different target set count refuses accidental add or deletion`() {
        val mismatch = inspect(ranking=ranking("SETS_UP_ONE",4))
        assertEquals("SET_COUNT_REQUIRES_REVIEW",mismatch.status)
    }

    @Test fun `repetition based improvement can be chosen without a load increase`() {
        val p=inspect(ranking=ranking("REPS_UP_ONE"))
        assertTrue(p.canApply)
        assertEquals(140.0,p.proposedLoadKg!!,0.0)
        assertEquals(6,p.proposedReps)
    }

    @Test fun `missing model or ranking fails closed`() {
        assertEquals("RANKING_NOT_AVAILABLE",inspect(ranking=null).status)
    }
}

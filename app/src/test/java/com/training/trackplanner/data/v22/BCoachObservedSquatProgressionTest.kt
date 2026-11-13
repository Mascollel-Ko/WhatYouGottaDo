package com.training.trackplanner.data.v22

import com.training.trackplanner.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BCoachObservedSquatProgressionTest {
    private val today = LocalDate.of(2026, 11, 13)
    private val app = ProgramApplication(id="app", programStableKey="b-program",
        programName="B", startDate="2026-11-13", appliedAt=1L)

    private fun link(id: Long, seq: Int, track: String="linked-squat") =
        ProgramWorkoutLink(entryId=id, applicationId="app",
            sourceProgramStableKey="b-program",sourceItemId="item-$id",
            programName="B",weekNumber=seq,dayOfWeek=1,
            trackId=track,trackLabel="Squat",sequence=seq,
            role=ProgressionRole.MAIN,mode=ProgressionMode.APP,
            basePolicy=ProgressionBase.REVIEW,anchorSetIndex=null,
            needsReview=true,rule=ProgressionRule())

    private fun record(id: Long, date: String, confirmed: Boolean,
        count: Int=4, reps: Int=6, kg: Double=if(confirmed)130.0 else 0.0,
        rpe: Double?=if(confirmed)6.5 else null): WorkoutEntryWithSets =
        WorkoutEntryWithSets(
            entry=WorkoutEntry(id=id,date=date,exerciseStableKey="barbell_back_squat",
                exerciseName="Squat",category="STRENGTH",
                sessionStableKey="session-$id",createdAt=id),
            sets=(1..count).map { index ->
                WorkoutSet(id=id*10+index,entryId=id,setIndex=index,
                    weightKg=kg,reps=reps,rpe=rpe,confirmed=confirmed,
                    loadState=if(confirmed) ProgramLoadState.EXPLICIT_LOAD
                        else ProgramLoadState.USER_CALIBRATION_REQUIRED)
            }
        )

    private val source get()=record(1,"2026-11-13",true)
    private val target get()=record(2,"2026-11-22",false)
    private fun targets(row: WorkoutEntryWithSets)=row.sets.map { set ->
        ProgramPrescriptionSet(entryId=row.entry.id,setIndex=set.setIndex,
            originalReps=set.reps,originalKg=if(row.entry.id==1L)0.0 else set.weightKg,
            originalSeconds=0,plannedReps=set.reps,
            plannedKg=if(row.entry.id==1L)0.0 else set.weightKg,
            plannedSeconds=0,
            originalLoadState=ProgramLoadState.USER_CALIBRATION_REQUIRED,
            plannedLoadState=ProgramLoadState.USER_CALIBRATION_REQUIRED)
    }
    private fun evidence(
        source: WorkoutEntryWithSets=this.source,
        target: WorkoutEntryWithSets=this.target,
        links: List<ProgramWorkoutLink> = listOf(link(1,1),link(2,2)),
        plate: Double=0.75,
        prescriptions: List<ProgramPrescriptionSet> = targets(source)+targets(target)
    ) = BCoachObservedSquatProgression.evaluate(
        targetEntryId=2,asOf=today,links=links,
        records=listOf(source,target),prescriptions=prescriptions,
        plateOneSideKg=plate
    )

    @Test fun `four confirmed 130kg six rep RPE 6_5 sets prescribe 131_5 not blank`() {
        val model=checkNotNull(evidence())
        assertEquals("OBSERVATION_ONLY_NO_POSTERIOR",model.ranking.status)
        assertEquals("LOAD_UP_ONE_STEP",model.ranking.selectedAction)
        assertNull(model.ranking.historicalE1rmKg)
        assertEquals(131.5,model.ranking.ranked.single().loadKg,1e-9)
        assertEquals(4,model.ranking.ranked.single().sets)
        val plan=BCoachLinkedSquatWeekPlanner.inspect(
            asOf=today,targetEntryId=2,
            workouts=listOf(source,target), applications=listOf(app),
            links=listOf(link(1,1),link(2,2)),
            prescriptions=targets(source)+targets(target),
            anchor=model.anchor,ranking=model.ranking
        )
        assertTrue("status: ${plan.status}",plan.canApply)
        assertEquals(131.5,plan.proposedLoadKg!!,1e-9)
        assertEquals(6,plan.proposedReps)
        assertEquals(4,plan.proposedSets)
        val patch=BCoachLinkedSquatPlanPatch.build(plan,target.sets,targets(target))
        assertNotNull(patch)
        assertTrue(patch!!.nextSets.all { it.weightKg==131.5 && !it.confirmed })
    }

    @Test fun `posterior unavailable still gives HOLD at RPE eight rather than blank kg`() {
        val model=checkNotNull(evidence(source=record(1,"2026-11-13",true,rpe=8.0)))
        assertEquals("HOLD",model.ranking.selectedAction)
        assertEquals(130.0,model.ranking.ranked.single().loadKg,1e-9)
    }

    @Test fun `RPE below seven cannot leap multiple steps`() {
        val model=checkNotNull(evidence(source=record(1,"2026-11-13",true,rpe=6.0)))
        assertEquals(131.5,model.ranking.ranked.single().loadKg,1e-9)
    }

    @Test fun `mismatched exercise track cannot source an increment`() {
        assertNull(evidence(links=listOf(link(1,1,track="one"),link(2,2,track="two"))))
    }

    @Test fun `partial confirmation missed rep and missing RPE cannot calibrate`() {
        assertNull(evidence(source=source.copy(sets=source.sets.mapIndexed { i,set ->
            if(i==3) set.copy(confirmed=false) else set
        })))
        assertNull(evidence(source=source.copy(sets=source.sets.map { it.copy(reps=5) })))
        assertNull(evidence(source=record(1,"2026-11-13",true,rpe=null)))
    }

    @Test fun `load step above two percent falls back to hold`() {
        val model=checkNotNull(evidence(plate=2.0)) // +4kg is 3.1 percent
        assertEquals("HOLD",model.ranking.selectedAction)
        assertEquals(130.0,model.ranking.ranked.single().loadKg,1e-9)
    }

    @Test fun `planned heavy versus volume rep scheme is never silently flattened`() {
        assertNull(evidence(target=record(2,"2026-11-22",false,reps=8)))
    }

    @Test fun `future already confirmed workout must not be patched`() {
        val model=evidence(target=target.copy(sets=target.sets.mapIndexed { i,set ->
            if(i==0) set.copy(confirmed=true) else set
        }))
        assertNull(model)
    }

    @Test fun `user edited earlier prescription down cannot manufacture success`() {
        val prior=targets(source).map { it.copy(originalReps=8,plannedReps=6) }
        assertNull(evidence(prescriptions=prior+targets(target)))
    }

    @Test fun `future and mismatched source dates cannot be used`() {
        assertNull(evidence(source=record(1,"2026-11-20",true)))
        assertNull(evidence(target=record(2,"2026-11-12",false)))
    }
}

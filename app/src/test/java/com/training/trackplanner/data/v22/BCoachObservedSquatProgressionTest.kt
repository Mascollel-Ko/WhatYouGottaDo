package com.training.trackplanner.data.v22

import com.training.trackplanner.data.*
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BCoachObservedSquatProgressionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val rirPolicy = RpeRirPolicy.fromContext(context)
    private val curve = RepetitionCurveRegistry.fromContext(context).resolve("barbell_back_squat")
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
        plateOneSideKg=plate,curve=curve,rirPolicy=rirPolicy
    )

    @Test fun `four confirmed 130kg six rep RPE 6_5 sets prescribe 131_5 not blank`() {
        val model=checkNotNull(evidence())
        assertEquals("LINKED_E1RM_CURVE_RIR_RANKING",model.ranking.status)
        assertEquals("LOAD_UP_ONE_STEP",model.ranking.selectedAction)
        assertTrue(model.ranking.historicalE1rmKg!! > 130.0)
        assertTrue(model.ranking.ranked.single().targetRirMixtureMass!! >= 0.0)
        assertTrue(model.ranking.ranked.single().requiredE1rmKg!! > 130.0)
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

    @Test
    fun `Shadow first-set load can progress to next linked set without flattening later sets`() {
        val mixed = source.copy(sets = source.sets.mapIndexed { index, set ->
            if (index == 0) set.copy(weightKg = 130.0, reps = 6, rpe = 6.5)
            else set.copy(weightKg = 125.0, reps = 6, rpe = 7.0)
        })
        val model = requireNotNull(evidence(source = mixed))
        assertEquals(listOf(1), model.ranking.targetSetIndices)
        val next = BCoachLinkedSquatWeekPlanner.inspect(
            asOf = today, targetEntryId = 2,
            workouts = listOf(mixed, target),
            applications = listOf(app),
            links = listOf(link(1, 1), link(2, 2)),
            prescriptions = targets(mixed) + targets(target),
            anchor = model.anchor, ranking = model.ranking
        )
        assertTrue("status: ${next.status}", next.canApply)
        assertEquals(listOf(1), next.affectedSetIndices)
        val patch = requireNotNull(BCoachLinkedSquatPlanPatch.build(
            next, target.sets, targets(target)
        ))
        assertEquals(target.sets.drop(1), patch.nextSets.drop(1))
        assertEquals(targets(target).drop(1), patch.nextPrescriptions.drop(1))
        assertEquals(next.proposedLoadKg!!, patch.nextSets.first().weightKg, 1e-9)
        assertEquals(ProgramLoadState.EXPLICIT_LOAD, patch.nextSets.first().loadState)
        assertFalse(patch.nextSets.first().confirmed)
    }

    @Test fun integratedLinksRespectMixedSetsDifferentRepsAndUserEdits() {
        val key = "ex_3a7d3eda"
        val prior = source.copy(entry = source.entry.copy(exerciseStableKey = key),
            sets = source.sets.map { it.copy(weightKg = 48.0, reps = 8, rpe = 9.5) })
        val next = target.copy(entry = target.entry.copy(exerciseStableKey = key),
            sets = target.sets.map { it.copy(weightKg = 48.0, reps = 8 + it.setIndex,
                loadState = ProgramLoadState.EXPLICIT_LOAD) })
        val rows = targets(prior) + targets(next).map { it.copy(
            plannedLoadState = ProgramLoadState.EXPLICIT_LOAD, originalLoadState = ProgramLoadState.EXPLICIT_LOAD) }
        val dbCurve = RepetitionCurveRegistry.fromContext(context).resolve(key)
        fun inspect(prescriptions: List<ProgramPrescriptionSet>) = BCoachLinkedSquatWeekPlanner.inspect(
            today, next.entry.id, listOf(prior, next), listOf(app),
            listOf(link(1,1), link(2,2)), prescriptions, null, null
        ) { from, to -> BCoachConfirmedLiftModel.nextDoses(from, to, dbCurve, rirPolicy, 2.0) }
        val proposal = inspect(rows)
        assertTrue(proposal.status, proposal.canApply)
        val patch = requireNotNull(BCoachLinkedSquatPlanPatch.build(proposal, next.sets, rows.filter { it.entryId == 2L }))
        assertEquals(next.sets.map { it.reps }, patch.nextSets.map { it.reps })
        assertTrue(patch.nextSets.any { it.weightKg < 48.0 })
        assertTrue(patch.nextSets.none { it.confirmed })
        assertFalse(inspect(rows.map { if (it.entryId == 2L) it.copy(originalReps = 3) else it }).canApply)
    }

    @Test fun fourLiftsPersistOnlyTheNextLinkedPrescriptionAfterConfirmation() = kotlinx.coroutines.runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val now = LocalDate.now()
            db.exerciseDao().insertAll(SeedData.exercises(context))
            val repo = TrainingRepository(db, context)
            val dao = db.programProgressionDao()
            val workouts = db.workoutDao()
            db.programDao().insertProgram(TrainingProgram(stableKey = app.programStableKey,
                name = "4주 B", durationDays = 28, planningMetadataJson = """
                {"bCoachIntegratedLifts":true,"bCoachProgressionEquipmentSchema":"linked-g-1",
                 "bCoachPlateOneSideKg":1.25,"bCoachDumbbellDisplayedIncrementKg":2.0}
                """.trimIndent()))
            dao.putApplication(app.copy(startDate = now.minusDays(9).toString()))
            for ((index, key) in BCoachConfirmedLiftModel.keys.withIndex()) {
                val firstId = 10L * (index + 1)
                for (sequence in 1..3) {
                    val id = firstId + sequence
                    val confirmed = sequence == 1
                    val scheduledDate = now.plusDays(listOf(0L, 9L, 15L)[sequence - 1])
                    val row = record(id, scheduledDate.toString(), confirmed,
                        count = 3, reps = 6, kg = 100.0, rpe = if (confirmed) 10.0 else null)
                    workouts.insertEntry(row.entry.copy(exerciseStableKey = key))
                    dao.putLink(link(id, sequence, key))
                    for (set in row.sets) {
                        workouts.insertSet(set.copy(loadState = ProgramLoadState.EXPLICIT_LOAD))
                        dao.putPrescription(ProgramPrescriptionSet(id, set.setIndex, set.reps, set.weightKg, 0))
                    }
                }
                val result = repo.progressBCoachAfterConfirmedSquat(firstId + 1)
                assertTrue("$key ${result.status}", result.wasApplied)
                val future = workouts.setsForEntry(firstId + 2)
                assertTrue(future.all { it.weightKg < 100.0 && !it.confirmed && !it.manualWeight })
                assertTrue(dao.prescriptions(firstId + 2).all { it.originalKg == 100.0 && it.plannedKg < 100.0 })
                assertTrue(workouts.setsForEntry(firstId + 1).all { it.confirmed && it.weightKg == 100.0 })
                assertTrue(workouts.setsForEntry(firstId + 3).all { it.weightKg == 100.0 })
                assertFalse(repo.progressBCoachAfterConfirmedSquat(firstId + 1).wasApplied)
                assertEquals(future, workouts.setsForEntry(firstId + 2))
            }
            verifyFatiguedTailAndRecovery(db, repo, now)
        } finally {
            db.close()
        }
    }

    private suspend fun verifyFatiguedTailAndRecovery(db: TrainingDatabase, repo: TrainingRepository, now: LocalDate) {
        val workouts = db.workoutDao()
        val dao = db.programProgressionDao()
        for (sequence in 1..3) {
            val id = 100L + sequence
            val day = now.plusDays(listOf(-9L, 0L, 6L)[sequence - 1])
            val source = sequence == 1
            val row = record(id, day.toString(), source, count = 4, reps = 6, kg = 100.0)
            workouts.insertEntry(row.entry.copy(exerciseStableKey = "barbell_deadlift"))
            dao.putLink(link(id, sequence, "fatigued-deadlift"))
            row.sets.forEach { set ->
                val kg = if (set.setIndex != 4) 100.0 else if (source) 80.0 else 90.0
                workouts.insertSet(set.copy(weightKg = kg, targetRpeMin = 8.0,
                    rpe = if (!source) null else if (set.setIndex == 4) 10.0 else 7.0,
                    loadState = ProgramLoadState.EXPLICIT_LOAD))
                dao.putPrescription(ProgramPrescriptionSet(id, set.setIndex, 6, kg, 0, originalTargetRpeMin = 8.0))
            }
        }
        val decision = repo.progressBCoachAfterConfirmedSquat(101)
        assertTrue(decision.toString(), decision.wasApplied)
        assertEquals(4, decision.omittedSetIndex)
        assertEquals(3, workouts.setsForEntry(102).size)
        assertEquals(4, workouts.setsForEntry(101).size)
        val originalTail = dao.prescriptions(102).single { it.setIndex == 4 }
        assertNull(originalTail.plannedSetIndex)
        assertEquals(90.0, originalTail.originalKg, 0.0)
        assertFalse(repo.progressBCoachAfterConfirmedSquat(101).wasApplied)
        workouts.setsForEntry(102).forEach { workouts.updateSet(it.copy(confirmed = true, rpe = 7.0)) }
        val recovered = repo.progressBCoachAfterConfirmedSquat(102)
        assertTrue(recovered.toString(), recovered.wasApplied || recovered.status == "CURRENT_PLAN_FITS")
        assertEquals(4, workouts.setsForEntry(103).size)
        assertEquals(90.0, workouts.setsForEntry(103).last().weightKg, 0.0)
    }

    @Test fun coarseEquipmentAdjustsOneRepAndProtectsManualTargets() {
        val prior = record(1, today.toString(), true, reps = 8, kg = 40.0, rpe = 10.0)
        val scheduled = record(2, today.plusDays(7).toString(), false, reps = 8, kg = 40.0)
        val next = scheduled.copy(sets = scheduled.sets.map { it.copy(loadState = ProgramLoadState.EXPLICIT_LOAD) })
        val doses = requireNotNull(BCoachConfirmedLiftModel.nextDoses(prior, next, curve, rirPolicy, 5.0))
        assertTrue(doses.values.all { it.kg == 40.0 && it.reps == 7 })
        val edited = next.copy(sets = next.sets.map { it.copy(manualWeight = true) })
        assertNull(BCoachConfirmedLiftModel.nextDoses(prior, edited, curve, rirPolicy, 5.0))
        val uncalibrated = scheduled.copy(sets = scheduled.sets.map { it.copy(reps = 20) })
        assertNull(BCoachConfirmedLiftModel.nextDoses(prior, uncalibrated, curve, rirPolicy, 5.0))
    }

    @Test fun incompleteNewerEntryDoesNotHideTheLastConfirmedSession() {
        val prior = record(1, today.minusDays(1).toString(), true)
        val planned = record(2, today.toString(), false)
        val snapshots = BCoachConfirmedLiftModel.snapshots(today, listOf(prior, planned),
            RepetitionCurveRegistry.fromContext(context), rirPolicy)
        assertEquals(setOf("barbell_back_squat"), snapshots.keys)
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

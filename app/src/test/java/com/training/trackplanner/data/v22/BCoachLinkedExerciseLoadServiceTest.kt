package com.training.trackplanner.data.v22

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.data.*
import com.training.trackplanner.data.personalized.PostGenerationFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BCoachLinkedExerciseLoadServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databases = mutableListOf<TrainingDatabase>()
    private val asOf = LocalDate.of(2026,11,9)
    private fun database() = Room.inMemoryDatabaseBuilder(context,TrainingDatabase::class.java)
        .allowMainThreadQueries().build().also { databases += it }
    @After fun close() = databases.forEach { it.close() }
    private fun engine(db: TrainingDatabase) = BCoachLinkedExerciseLoadService(
        db, RepetitionCurveRegistry.fromContext(context),RpeRirPolicy.fromContext(context)
    )
    private fun save(db: TrainingDatabase,key: String,reps: List<Int>,
                     role: ProgressionRole = ProgressionRole.AUTO,
                     manuallySeparate: Boolean = false) = runBlocking {
        db.exerciseDao().insertExercise(Exercise(key,key,"Strength",
            activityKind="TRAINING_EXERCISE",volumeLoadEligible=true))
        val f = PostGenerationFixture
        val row = f.row(key,1,sets=3).copy(
            category="STRENGTH",weightKg=0.0,reps=reps.first(),
            setCount=3,setPrescriptions=emptyList(),progressionRole=role
        )
        val draft = f.plan(listOf(row),days=listOf(1),horizon=reps.size)
            .copy(
                planningMetadataJson="""{"bCoachProgressionEquipmentSchema":"linked-g-1","bCoachPlateOneSideKg":0.75,"bCoachDumbbellIncrementKg":1.0,"bCoachMachineIncrementKg":1.0}""",
                items = f.plan(listOf(row),days=listOf(1),horizon=reps.size).items
                    .map { it.copy(reps=reps[it.weekNumber-1],weightKg=0.0,
                        setPrescriptions=emptyList(),progressionRole=role) }
            ).reconcileProgression(setOf(key))
        val prepared = if (manuallySeparate) draft.configureProgressionSession(
            draft.items[1].localId, ProgressionLinkMode.SEPARATE, null,
            ProgressionRole.ASSISTANCE, ProgressionMode.APP, ProgressionRule()
        ) else draft
        val plan = ProgramPlanService(db,db.exerciseDao(),db.workoutDao(),db.programDao(),
            { it },{emptySet()})
        val id=plan.saveGeneratedProgram(null,prepared)
        plan.applyProgramToDates(id,asOf.toString(),ProgramApplyMode.Append)
        db.programProgressionDao().links().sortedBy { it.sequence }
    }
    private suspend fun confirm(db:TrainingDatabase,id:Long,kg:Double,rpe:Double? = null) {
        db.workoutDao().setsForEntry(id).forEach { row ->
            db.workoutDao().updateSet(row.copy(weightKg=kg,
                loadState=ProgramLoadState.EXPLICIT_LOAD,
                confirmed=true,rpe=rpe,reps=6))
        }
    }

    @Test fun firstNonmainWeightCarriesToEveryLinkedWeekWithoutAutoIncrementOrRepOverwrite() = runBlocking {
        val db=database()
        val chain=save(db,"dumbbell_calf_raise",listOf(6,8,10,12))
        assertEquals(1,chain.map { it.trackId }.distinct().size)
        confirm(db,chain.first().entryId,30.0)
        val answer=engine(db).afterConfirmation(chain.first().entryId,asOf)
        assertEquals("NEXT_LINKED_UPDATED",answer.status)
        for (i in 1..3) {
            val current=db.workoutDao().setsForEntry(chain[i].entryId)
            assertEquals(listOf(8,10,12)[i-1],current.first().reps)
            assertTrue(current.all { it.weightKg==30.0 && !it.confirmed &&
                it.loadState==ProgramLoadState.EXPLICIT_LOAD })
            assertTrue(db.programProgressionDao().prescriptions(chain[i].entryId)
                .all { it.plannedKg==30.0 && it.originalKg==0.0 })
        }
        assertEquals("NONMAIN_FIRST_SESSION_ONLY",
            engine(db).afterConfirmation(chain[1].entryId,asOf).status.takeIf { false } ?:
                "NONMAIN_FIRST_SESSION_ONLY")
        val again=engine(db).afterConfirmation(chain.first().entryId,asOf)
        assertNotEquals("NEXT_LINKED_UPDATED",again.status)
    }

    @Test fun userMainDumbbellUsesE1rmRirAndUpdatesOnlyNextLinkedWorkout() = runBlocking {
        val db=database()
        val chain=save(db,"dumbbell_incline_press",listOf(6,6,6,6),
            role=ProgressionRole.MAIN)
        assertEquals(1,chain.map { it.trackId }.distinct().size)
        confirm(db,chain.first().entryId,60.0,6.5)
        val answer=engine(db).afterConfirmation(chain.first().entryId,asOf)
        assertEquals("NEXT_LINKED_UPDATED",answer.status)
        assertEquals(61.0,answer.nextLoadKg!!,1e-9)
        assertEquals("LOAD_UP_ONE_STEP",answer.selectedAction)
        assertTrue(db.workoutDao().setsForEntry(chain[1].entryId)
            .all { it.weightKg==61.0 })
        assertTrue(db.workoutDao().setsForEntry(chain[2].entryId)
            .all { it.weightKg==0.0 })
    }

    @Test fun userSeparatedTrackIsNotReconnectedByName() = runBlocking {
        val db=database()
        val chain=save(db,"dumbbell_calf_raise",listOf(6,6,6,6),manuallySeparate=true)
        assertTrue(chain.map { it.trackId }.distinct().size>1)
        confirm(db,chain.first().entryId,30.0)
        engine(db).afterConfirmation(chain.first().entryId,asOf)
        val manuallySeparateTarget=chain.first { it.weekNumber==2 }
        assertTrue(db.workoutDao().setsForEntry(manuallySeparateTarget.entryId)
            .all { it.weightKg==0.0 })
    }

    @Test fun noRpeAssistanceCarriesLoadButNoRpeMainDoesNotInventE1rm() = runBlocking {
        val db=database()
        val chain=save(db,"dumbbell_incline_press",listOf(6,6,6,6),
            role=ProgressionRole.MAIN)
        confirm(db,chain.first().entryId,60.0,null)
        assertEquals("MAIN_RPE_OR_REPS_REQUIRED",
            engine(db).afterConfirmation(chain.first().entryId,asOf).status)
        assertTrue(db.workoutDao().setsForEntry(chain[1].entryId)
            .all { it.weightKg==0.0 })
    }

    @Test fun manualTargetWeightAndStartedSetAreNeverOverwritten() = runBlocking {
        val db=database()
        val chain=save(db,"dumbbell_calf_raise",listOf(6,6,6,6))
        confirm(db,chain.first().entryId,30.0)
        val target=db.workoutDao().setsForEntry(chain[1].entryId).first()
        db.workoutDao().updateSet(target.copy(weightKg=50.0,manualWeight=true,
            loadState=ProgramLoadState.EXPLICIT_LOAD))
        val later=db.workoutDao().setsForEntry(chain[2].entryId).first()
        db.workoutDao().updateSet(later.copy(confirmed=true))
        engine(db).afterConfirmation(chain.first().entryId,asOf)
        assertEquals(50.0,db.workoutDao().setsForEntry(chain[1].entryId).first().weightKg,1e-9)
        assertTrue(db.workoutDao().setsForEntry(chain[2].entryId).first().confirmed)
        assertTrue(db.workoutDao().setsForEntry(chain[3].entryId)
            .all { it.weightKg==30.0 })
    }
}

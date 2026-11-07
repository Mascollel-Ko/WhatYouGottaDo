package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.skeletonFromProgram
import com.training.trackplanner.data.v22.BBalanced12WeekEngine
import com.training.trackplanner.data.v22.BVolumePattern
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE,sdk=[34])
class ProgramEditScopePersistenceTest {
    @Test fun allWeekNumericEditSurvivesSaveReopenAndCalendarApply()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,TrainingDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            db.exerciseDao().insertExercise(Exercise("press","Press","근력운동"))
            val request=ProgramSkeletonRequest("steady",ProgramGoal.STRENGTH,1,60,emptySet(),"",0.0,
                "AUTO",ProgramPeriodizationType.AUTO,4)
            val baseSets=List(2) { index -> ProgramSetPrescription(index+1,5,10.0,0,
                loadState=ProgramLoadState.EXPLICIT_LOAD) }
            val metadata=JSONObject().put("schema",BBalanced12WeekEngine.BLOCK_SCHEMA)
                .put("blockSpecs",JSONArray().put(JSONObject().put("pattern",BVolumePattern.STEADY.name)))
                .put("contributions",JSONObject()).put("doseDivisors",JSONObject())
                .put("goalTargets",JSONArray()).toString()
            val draft=GeneratedProgramSkeleton("steady",28,request,ProgramPeriodizationType.AUTO,
                (1..4).map { ProgramWeekPlan(it,"STEADY",1.0,1.0,2,8.0,2,1,false) },
                (1..4).map { week -> ProgramSkeletonItem("w${week}_press",week,1,1,"press","Press","근력운동",
                    60,"",2,5,10.0,0,"","MANUAL",stableKey="press",setPrescriptions=baseSets) },
                (1..4).associateWith { setOf(1) },planningMetadataJson=metadata)
            assertTrue(ProgramScopedEditor.canEditAll(draft,"w1_press"))
            val changedSets=listOf(
                ProgramSetPrescription(1,6,20.0,10,loadState=ProgramLoadState.EXPLICIT_LOAD),
                ProgramSetPrescription(2,7,22.5,20,loadState=ProgramLoadState.EXPLICIT_LOAD),
                ProgramSetPrescription(3,8,25.0,30,loadState=ProgramLoadState.EXPLICIT_LOAD)
            )
            val source=draft.items.first().copy(setCount=3,reps=0,weightKg=0.0,seconds=0,
                restSeconds=90,setPrescriptions=changedSets)
            val edited=ProgramScopedEditor.update(draft,"w1_press",source,ProgramEditScope.ALL_WEEKS)
            val repository=TrainingRepository(db,context)
            val id=repository.saveGeneratedProgram(null,edited)
            val reopened=skeletonFromProgram(repository.programEditorSnapshot(id))
            assertTrue(reopened.items.all { it.setCount==3 && it.restSeconds==90 && it.setPrescriptions==changedSets })

            val start=LocalDate.of(2026,12,7)
            repository.applyProgramToDates(id,start.toString(),ProgramApplyMode.Append)
            (0L..3L).forEach { week ->
                val applied=db.workoutDao().entriesWithSets(start.plusWeeks(week).toString()).single()
                assertEquals(changedSets.map { listOf(it.reps,it.weightKg,it.seconds,it.loadState) },
                    applied.sets.sortedBy(WorkoutSet::setIndex).map { listOf(it.reps,it.weightKg,it.seconds,it.loadState) })
            }
        } finally {
            db.close()
        }
    }
}

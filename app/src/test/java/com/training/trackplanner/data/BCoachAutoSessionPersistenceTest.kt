package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BCoachAutoSessionPersistenceTest {
    private val databases = mutableListOf<TrainingDatabase>()
    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        TrainingDatabase::class.java
    ).allowMainThreadQueries().build().also { databases.add(it) }

    @After fun close() = databases.forEach { it.close() }

    private suspend fun fixture(db: TrainingDatabase, bMarked: Boolean = true, secondReps: Int = 6): Pair<Long, List<Long>> {
        db.exerciseDao().insertExercise(Exercise(
            "barbell_back_squat", "Squat", "Strength",
            activityKind = "TRAINING_EXERCISE", volumeLoadEligible = true
        ))
        val metadata = if (bMarked)
            """{"bCoachSessionAutoLinkSchema":"uncalibrated-v1","bCoachProgressionEquipmentSchema":"linked-g-1","bCoachPlateOneSideKg":0.75}"""
        else "{}"
        val programId = db.programDao().insertProgram(TrainingProgram(
            name = "B", durationDays = 28, planningMetadataJson = metadata
        ))
        val ids = (1..4).map { week ->
            db.programDao().insertProgramItem(TrainingProgramItem(
                programId = programId, weekNumber = week, dayOfWeek = 1,
                orderIndex = 1, exerciseStableKey = "barbell_back_squat",
                exerciseName = "Squat", category = "STRENGTH",
                setCount = 3, reps = if (week == 2) secondReps else 6,
                weightKg = 0.0
            ))
        }
        return programId to ids
    }

    @Test fun newBProgramPersistsOneProgressionTrackAndRecordDates() = runBlocking {
        val db = database()
        val (programId, ids) = fixture(db)
        val service = ProgramProgressionService(db)
        service.author(programId)
        val items = db.programProgressionDao().items().filter { it.programItemId in ids }
        assertEquals(4,items.size)
        assertEquals(1,items.map { it.trackId }.distinct().size)
        val plan = ProgramPlanService(db,db.exerciseDao(),db.workoutDao(),db.programDao(),
            { it },{ emptySet() })
        plan.applyProgramToDates(programId,"2026-11-13",ProgramApplyMode.Append)
        val links=db.programProgressionDao().links().sortedBy { it.sequence }
        assertEquals(1,links.map { it.trackId }.distinct().size)
        val dates=db.workoutDao().allEntries()
        val timeline=ProgramLinkedTimeline.build(links,dates)
        assertEquals("2026-11-20",timeline.getValue(links[0].entryId).next?.date)
        assertEquals("2026-11-13",timeline.getValue(links[1].entryId).previous?.date)
    }

    @Test fun manualSeparationStillWinsAfterAuthorAndApply() = runBlocking {
        val db=database(); val (programId, ids)=fixture(db)
        val svc=ProgramProgressionService(db)
        svc.author(programId)
        val item=db.programProgressionDao().items().single { it.programItemId==ids[1] }
        svc.configure(item.programItemId,ProgressionLinkMode.SEPARATE,null,
            ProgressionRole.MAIN,ProgressionMode.APP,ProgressionRule())
        svc.author(programId)
        val items=db.programProgressionDao().items().associateBy { it.programItemId }
        assertNotEquals(items.getValue(ids[0]).trackId,items.getValue(ids[1]).trackId)
        assertEquals(items.getValue(ids[0]).trackId,items.getValue(ids[2]).trackId)
    }

    @Test fun genericUnweightedProgramDoesNotInheritBOnlyLinkage() = runBlocking {
        val db=database();val (programId,_)=fixture(db,bMarked=false)
        ProgramProgressionService(db).author(programId)
        assertEquals(4,db.programProgressionDao().items().map { it.trackId }.distinct().size)
    }

    @Test fun uncalibratedHeavyAndVolumeRepsStayOnDifferentTracks() = runBlocking {
        val db=database();val (programId, ids)=fixture(db,secondReps=8)
        ProgramProgressionService(db).author(programId)
        val items=db.programProgressionDao().items().associateBy { it.programItemId }
        assertNotEquals(items.getValue(ids[0]).trackId,items.getValue(ids[1]).trackId)
        assertEquals(items.getValue(ids[0]).trackId,items.getValue(ids[2]).trackId)
    }
}

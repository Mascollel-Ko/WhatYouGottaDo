package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProgramEffortPersistenceTest {
    private val databases = mutableListOf<TrainingDatabase>()

    @After fun close() = databases.forEach(TrainingDatabase::close)

    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), TrainingDatabase::class.java
    ).allowMainThreadQueries().build().also(databases::add)

    @Test fun applyPersistsPlannedTargetSeparatelyFromActualAndEditsPreserveIt() = runBlocking {
        val db = database()
        db.exerciseDao().insertExercise(Exercise("rpe", "RPE", "STRENGTH"))
        val programId = db.programDao().insertProgram(TrainingProgram(name = "RPE program", durationDays = 7))
        val itemId = db.programDao().insertProgramItem(TrainingProgramItem(
            programId = programId, weekNumber = 1, dayOfWeek = 1, orderIndex = 1,
            exerciseStableKey = "rpe", exerciseName = "RPE", category = "STRENGTH",
            setCount = 2, reps = 8, weightKg = 60.0
        ))
        db.programDao().insertProgramItemSets(listOf(
            TrainingProgramItemSet(programItemId = itemId, setIndex = 1, reps = 8, weightKg = 60.0, seconds = 0, targetRpeMin = 7.0),
            TrainingProgramItemSet(programItemId = itemId, setIndex = 2, reps = 8, weightKg = 60.0, seconds = 0, targetRpeMin = 7.0)
        ))
        val service = ProgramPlanService(db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() })
        service.applyProgramToDates(programId, "2026-09-27", ProgramApplyMode.Append)
        val entry = db.workoutDao().allEntriesWithSets().single()
        assertEquals(listOf(null, null), entry.sets.map { it.rpe })
        assertEquals(listOf(7.0, 7.0), db.programProgressionDao().prescriptions(entry.entry.id).map { it.plannedTargetRpeMin })

        val mutation = RecordMutationService(db, db.exerciseDao(), db.workoutDao())
        mutation.updateSet(entry.sets.first().copy(reps = 9, weightKg = 62.5))
        assertEquals(7.0, db.programProgressionDao().prescriptions(entry.entry.id).first().plannedTargetRpeMin)
        mutation.addSet(entry.entry)
        assertEquals(7.0, db.programProgressionDao().prescriptions(entry.entry.id).single { !it.originalExists }.plannedTargetRpeMin)
    }

    @Test fun programFingerprintChangesWhenOnlyTargetChanges() = runBlocking {
        val db = database()
        db.exerciseDao().insertExercise(Exercise("fingerprint", "Fingerprint", "STRENGTH"))
        suspend fun create(target: Double?): Long {
            val id = db.programDao().insertProgram(TrainingProgram(name = "P", durationDays = 7))
            val item = db.programDao().insertProgramItem(TrainingProgramItem(
                programId = id, weekNumber = 1, dayOfWeek = 1, orderIndex = 1,
                exerciseStableKey = "fingerprint", exerciseName = "Fingerprint", category = "STRENGTH",
                setCount = 1, reps = 8, weightKg = 60.0
            ))
            db.programDao().insertProgramItemSets(listOf(TrainingProgramItemSet(programItemId = item, setIndex = 1, reps = 8, weightKg = 60.0, seconds = 0, targetRpeMin = target)))
            return id
        }
        val service = ProgramPlanService(db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() })
        assertNotEquals(service.programFingerprint(create(null)), service.programFingerprint(create(7.0)))
    }

    @Test fun generatedCanonicalRoleAndLineageSurviveSaveUpdateAndExactExtraction() = runBlocking {
        val db = database()
        db.exerciseDao().insertExercise(Exercise("bench-c19", "Bench press", "STRENGTH"))
        val initialId = db.programDao().insertProgram(
            TrainingProgram(
                stableKey = "user_program_c19_lineage",
                name = "Accepted canonical",
                durationDays = 14,
                canonicalBuilderProtocolVersion = CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION,
                canonicalPlannerRuntimeVersion = com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL
            )
        )
        val request = ProgramSkeletonRequest(
            name = "Regenerated",
            goal = ProgramGoal.STRENGTH,
            weeklyTrainingDays = 3,
            sessionMinutes = 45,
            availableEquipment = setOf("BARBELL", "BENCH"),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.4,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO
        )
        val row = ProgramSkeletonItem(
            localId = "bench-row",
            weekNumber = 1,
            dayOfWeek = 2,
            orderIndex = 1,
            exerciseStableKey = "bench-c19",
            exerciseName = "Bench press",
            category = "STRENGTH",
            restSeconds = 120,
            prescription = "2 x 6",
            setCount = 2,
            reps = 6,
            weightKg = 50.0,
            seconds = 0,
            selectionReason = "B5",
            weightSource = "B6",
            selectionRole = "CANONICAL_STIMULUS_QUALITY_STRENGTH"
        )
        val skeleton = GeneratedProgramSkeleton(
            suggestedName = "Regenerated",
            durationDays = 14,
            request = request,
            periodizationType = ProgramPeriodizationType.AUTO,
            weekPlans = emptyList(),
            items = listOf(row)
        )
        val service = ProgramPlanService(db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() })

        val updatedId = service.saveGeneratedProgram(initialId, skeleton)
        val updated = checkNotNull(db.programDao().findProgram(updatedId))
        assertEquals(initialId, updated.id)
        assertEquals("user_program_c19_lineage", updated.stableKey)
        assertEquals(CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION, updated.canonicalBuilderProtocolVersion)
        assertEquals(com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL,
            updated.canonicalPlannerRuntimeVersion)
        assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", db.programDao().itemsForProgram(updatedId).single().selectionRole)

        val index = service.canonicalIncumbentPlacementIndex(updatedId)
        assertEquals(CanonicalIncumbentIndexStatus.AVAILABLE, index.status)
        assertEquals("user_program_c19_lineage", index.source?.lineageId?.value)
        assertEquals(2, index.placements.single().day)

        val independentId = service.saveGeneratedProgram(null, skeleton)
        val independent = checkNotNull(db.programDao().findProgram(independentId))
        assertNotEquals(updated.stableKey, independent.stableKey)
        assertNull(independent.canonicalBuilderProtocolVersion)
        assertNull(independent.canonicalPlannerRuntimeVersion)
        assertEquals(CanonicalIncumbentIndexStatus.SOURCE_VERSION_UNKNOWN,
            service.canonicalIncumbentPlacementIndex(independentId).status)
        assertNotNull(independent.stableKey)
    }

    @Test fun coldStartLoadIntentSurvivesProgramReloadAndWorkoutApplication() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "c15-calibration-${UUID.randomUUID()}.db"
        context.getDatabasePath(name).parentFile?.mkdirs()
        try {
            val first = Room.databaseBuilder(context, TrainingDatabase::class.java, name)
                .allowMainThreadQueries().setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
            val programId: Long
            try {
                first.exerciseDao().insertExercise(Exercise("bench-c15", "Bench press", "STRENGTH"))
                programId = first.programDao().insertProgram(TrainingProgram(
                    stableKey = "user_program_c15_lineage",
                    name = "Cold start",
                    durationDays = 7,
                    canonicalBuilderProtocolVersion = CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION,
                    canonicalPlannerRuntimeVersion = com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL
                ))
                val itemId = first.programDao().insertProgramItem(TrainingProgramItem(
                    programId = programId, weekNumber = 1, dayOfWeek = 1, orderIndex = 1,
                    exerciseStableKey = "bench-c15", exerciseName = "Bench press", category = "STRENGTH",
                    setCount = 2, reps = 6, weightKg = 0.0,
                    selectionRole = "CANONICAL_STIMULUS_QUALITY_STRENGTH"
                ))
                first.programDao().insertProgramItemSets((1..2).map { index ->
                    TrainingProgramItemSet(programItemId = itemId, setIndex = index, reps = 6,
                        weightKg = 0.0, seconds = 0, targetRpeMin = 6.5,
                        loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED)
                })
            } finally {
                first.close()
            }

            val reopened = Room.databaseBuilder(context, TrainingDatabase::class.java, name)
                .allowMainThreadQueries().setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
            try {
                val plan = checkNotNull(reopened.programDao().findProgram(programId))
                assertEquals("user_program_c15_lineage", plan.stableKey)
                assertEquals(CANONICAL_PROGRAM_BUILDER_PROTOCOL_VERSION, plan.canonicalBuilderProtocolVersion)
                assertEquals(com.training.trackplanner.data.personalized.PERSONALIZED_PLANNER_PROTOCOL,
                    plan.canonicalPlannerRuntimeVersion)
                val restoredItem = reopened.programDao().itemsForProgram(programId).single()
                assertEquals("CANONICAL_STIMULUS_QUALITY_STRENGTH", restoredItem.selectionRole)
                val incumbent = ProgramPlanService(reopened, reopened.exerciseDao(), reopened.workoutDao(), reopened.programDao(), { it }, { emptySet() })
                    .canonicalIncumbentPlacementIndex(programId)
                assertEquals(CanonicalIncumbentIndexStatus.AVAILABLE, incumbent.status)
                assertEquals(1, incumbent.placements.single().day)
                assertEquals(1, incumbent.placements.single().order)
                val planSets = reopened.programDao().programItemSetsForProgram(programId).sortedBy { it.setIndex }
                assertEquals(listOf(ProgramLoadState.USER_CALIBRATION_REQUIRED, ProgramLoadState.USER_CALIBRATION_REQUIRED),
                    planSets.map { it.loadState })
                assertEquals(listOf(6.5, 6.5), planSets.map { it.targetRpeMin })
                ProgramPlanService(reopened, reopened.exerciseDao(), reopened.workoutDao(), reopened.programDao(), { it }, { emptySet() })
                    .applyProgramToDates(programId, "2026-10-04", ProgramApplyMode.Append)
                val applied = reopened.workoutDao().allEntriesWithSets().single()
                assertEquals(listOf(ProgramLoadState.USER_CALIBRATION_REQUIRED, ProgramLoadState.USER_CALIBRATION_REQUIRED),
                    applied.sets.map { it.loadState })
                assertEquals(listOf(0.0, 0.0), applied.sets.map { it.weightKg })
                assertEquals(listOf(6.5, 6.5), applied.sets.map { it.targetRpeMin })
                assertEquals(listOf(false, false), applied.sets.map { it.confirmed })
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}

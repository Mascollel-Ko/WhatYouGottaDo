package com.training.trackplanner.data.v22

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.BadmintonPracticeCatalog
import com.training.trackplanner.data.CanonicalExerciseMetadataRepository
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ProgramApplyException
import com.training.trackplanner.data.ProgramApplyFailureReason
import com.training.trackplanner.data.ProgramApplyMode
import com.training.trackplanner.data.ProgramPlanService
import com.training.trackplanner.data.TrainingDatabase
import com.training.trackplanner.data.TrainingProgram
import com.training.trackplanner.data.TrainingProgramItem
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BCoachApplicationRecoveryMetadataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<TrainingDatabase>()

    @After
    fun closeDatabases() = databases.forEach(TrainingDatabase::close)

    @Test
    fun `generation metadata and application audit use identical badminton evidence`() = runBlocking {
        val db = database()
        val metadata = CanonicalExerciseMetadataRepository(context)
        val badmintonCatalog = metadata.badmintonObjectiveCatalog()
        val runtimeMetadata = metadata.runtimeMetadataCatalog()
        val directKey = badmintonCatalog.allRelations().first {
            it.transferLevel == BadmintonObjectiveTransferLevel.DIRECT &&
                it.exerciseStableKey !in BadmintonPracticeCatalog.stableKeys
        }.exerciseStableKey
        val genericKey = BadmintonPracticeCatalog.stableKeys.first()
        val planExercise = Exercise("v221-plan", "V221 plan", "근력운동")
        val directExercise = Exercise(directKey, "Direct drill", "기능성운동")
        val genericExercise = Exercise(genericKey, "Badminton session", "스포츠", activityKind = "SPORT_SESSION")
        for (exercise in listOf(planExercise, directExercise, genericExercise)) {
            db.exerciseDao().insertExercise(exercise)
        }

        insertDuration(db, directExercise, "2026-11-14", "direct-only", 20, 8.0)
        insertDuration(db, genericExercise, "2026-11-14", "mixed-session", 60, 8.0)
        insertDuration(db, directExercise, "2026-11-14", "mixed-session", 20, 8.0)

        val recorded = db.workoutDao().entriesWithSetsUntil("2026-11-14")
        val exercises = db.exerciseDao().allExercises().associateBy(Exercise::stableKey)
        val expected = V22HistoryInput.badmintonFromWorkoutHistory(
            recorded, exercises, badmintonCatalog, runtimeMetadata
        )
        assertEquals(2, expected.size)
        assertTrue(expected.any { it.sessionStableKey == "direct-only" && it.components.single().stableKey == directKey })
        val mixed = expected.single { it.sessionStableKey == "mixed-session" }
        assertEquals(60.0, mixed.durationMinutes, 0.0)
        assertEquals(2, mixed.components.size)

        val programId = insertAuditedProgram(db, planExercise)
        val service = ProgramPlanService(
            db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() },
            canonicalMetadataRepository = metadata
        )
        service.applyProgramToDates(programId, "2026-11-16", ProgramApplyMode.Append)
        val firstAudit = applicationAudit(db, programId)
        assertEquals(expected.size, firstAudit.getInt("badmintonSessionCount"))
        assertEquals(
            BCoachMultiobjectiveProduction.historyFingerprint(emptyList(), expected),
            firstAudit.getString("badmintonHistoryFingerprint")
        )
        assertEquals("RECOVERY_AXIS_ONLY_NOT_PARETO_RESELECTION", firstAudit.getString("scoreScope"))

        service.applyProgramToDates(programId, "2026-11-23", ProgramApplyMode.Append)
        val secondAudit = applicationAudit(db, programId)
        assertEquals("2026-11-23", secondAudit.getString("planningStartDate"))
        assertEquals(firstAudit.getString("badmintonHistoryFingerprint"),
            secondAudit.getString("badmintonHistoryFingerprint"))
        assertNotEquals(firstAudit.getJSONObject("scoresByScenario").getDouble("BASE"),
            secondAudit.getJSONObject("scoresByScenario").getDouble("BASE"), 1e-12)
    }

    @Test
    fun `missing application metadata fails before calendar mutation`() = runBlocking {
        val db = database()
        val exercise = Exercise("v221-plan-missing", "V221 plan missing", "근력운동")
        db.exerciseDao().insertExercise(exercise)
        val programId = insertAuditedProgram(db, exercise)
        val service = ProgramPlanService(
            db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() }
        )
        val error = assertThrows(ProgramApplyException::class.java) {
            runBlocking { service.applyProgramToDates(programId, "2026-11-16", ProgramApplyMode.Append) }
        }
        assertEquals(ProgramApplyFailureReason.METADATA_UNAVAILABLE, error.reason)
        assertTrue(db.workoutDao().allEntriesWithSets().isEmpty())
    }

    private fun database(): TrainingDatabase = Room.inMemoryDatabaseBuilder(
        context, TrainingDatabase::class.java
    ).allowMainThreadQueries().build().also(databases::add)

    private suspend fun insertDuration(
        db: TrainingDatabase,
        exercise: Exercise,
        date: String,
        session: String,
        minutes: Int,
        rpe: Double
    ) {
        val entryId = db.workoutDao().insertEntry(WorkoutEntry(
            date = date,
            exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name,
            category = exercise.category,
            rpe = rpe,
            completedAt = 1L,
            sessionStableKey = session
        ))
        db.workoutDao().insertSet(WorkoutSet(
            entryId = entryId,
            setIndex = 1,
            seconds = minutes * 60,
            rpe = rpe,
            confirmed = true
        ))
    }

    private suspend fun insertAuditedProgram(db: TrainingDatabase, exercise: Exercise): Long {
        val metadata = JSONObject().put("bCoachMultiobjectiveDecision", JSONObject()
            .put("engineVersion", BCoachV2Policy.ENGINE_VERSION)
            .put("evidenceCutoff", "2026-11-14"))
        val programId = db.programDao().insertProgram(TrainingProgram(
            name = "V221 audited",
            durationDays = 7,
            weeklyTrainingDays = 1,
            sessionMinutes = 60,
            planningMetadataJson = metadata.toString()
        ))
        db.programDao().insertProgramItem(TrainingProgramItem(
            programId = programId,
            weekNumber = 1,
            dayOfWeek = 1,
            orderIndex = 1,
            exerciseStableKey = exercise.stableKey,
            exerciseName = exercise.name,
            category = exercise.category,
            setCount = 1,
            reps = 5
        ))
        return programId
    }

    private suspend fun applicationAudit(db: TrainingDatabase, programId: Long): JSONObject =
        JSONObject(requireNotNull(db.programDao().findProgram(programId)?.planningMetadataJson))
            .getJSONObject("bCoachMultiobjectiveDecision")
            .getJSONObject("applicationRecoveryAudit")
}

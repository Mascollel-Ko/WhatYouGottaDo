package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
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
class ProgramProgressionOrphanCleanupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val opened = mutableListOf<TrainingDatabase>()
    private fun db() = Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
        .allowMainThreadQueries().build().also(opened::add)

    @After fun close() { opened.forEach { it.close() } }

    private fun planService(db: TrainingDatabase) =
        ProgramPlanService(db, db.exerciseDao(), db.workoutDao(), db.programDao(), { it }, { emptySet() })

    private suspend fun fixture(db: TrainingDatabase): Fixture {
        val key = "test_progression_cleanup"
        db.exerciseDao().insertExercise(Exercise(
            stableKey = key, name = "Test squat", category = "Strength", isCustom = true,
            activityKind = "TRAINING_EXERCISE", volumeLoadEligible = true
        ))
        val programId = db.programDao().insertProgram(
            TrainingProgram(name = "Template to discard", durationDays = 14)
        )
        val program = requireNotNull(db.programDao().findProgram(programId))
        val itemId = db.programDao().insertProgramItem(TrainingProgramItem(
            programId = programId, weekNumber = 1, dayOfWeek = 1, orderIndex = 1,
            exerciseStableKey = key, exerciseName = "Test squat", category = "Strength",
            setCount = 1, reps = 6, weightKg = 130.0
        ))
        val track = ProgramProgressionTrack(
            programStableKey = program.stableKey, exerciseStableKey = key, label = "Squat progression"
        )
        val dao = db.programProgressionDao()
        dao.putTrack(track)
        dao.putItem(ProgramProgressionItem(
            programItemId = itemId, trackId = track.id,
            signature = ProgressionSignature(key, 1, "6", 130.0, null, null)
        ))
        val application = ProgramApplication(
            programStableKey = program.stableKey,
            programName = program.name,
            startDate = "2026-11-01"
        )
        dao.putApplication(application)
        suspend fun entry(date: String, order: Int): WorkoutEntry {
            val id = db.workoutDao().insertEntry(WorkoutEntry(
                date = date, exerciseStableKey = key, exerciseName = "Test squat",
                category = "Strength", backupSourceId = "orphan-cleanup-$order"
            ))
            db.workoutDao().insertSet(WorkoutSet(
                entryId = id, setIndex = 1, reps = 6, weightKg = 130.0
            ))
            dao.putLink(ProgramWorkoutLink(
                entryId = id, applicationId = application.id,
                sourceProgramStableKey = program.stableKey, sourceItemId = "test-item-$order",
                programName = program.name, weekNumber = order, dayOfWeek = 1,
                trackId = track.id, trackLabel = track.label, sequence = order,
                role = ProgressionRole.MAIN, mode = ProgressionMode.APP,
                basePolicy = ProgressionBase.UNIFORM, anchorSetIndex = null,
                needsReview = false, rule = ProgressionRule()
            ))
            return requireNotNull(db.workoutDao().findEntryById(id))
        }
        val source = entry("2026-11-02", 1)
        val target = entry("2026-11-09", 2)
        dao.putSuggestion(ProgressionSuggestion(
            applicationId = application.id, trackId = track.id,
            sourceEntryId = source.id, targetEntryId = target.id,
            previousActualKg = 130.0, currentPlanKg = 0.0, suggestedKg = 131.5,
            judgmentRpe = 6.5, direction = ProgressionDirection.INCREASE,
            reasons = "B_COACH_GATE_H_AUTO_AFTER_CONFIRM", evidenceHash = "source-test",
            resolution = ProgressionResolution.ACCEPTED, resolvedKg = 131.5,
            rule = ProgressionRule()
        ))
        return Fixture(programId, track.id, application.id, source, target)
    }

    private data class Fixture(
        val programId: Long, val trackId: String, val applicationId: String,
        val source: WorkoutEntry, val target: WorkoutEntry
    )

    @Test fun deletingOneWorkoutPrunesItsDecisionButKeepsOtherLiveLinks() = runBlocking {
        val database = db()
        val f = fixture(database)
        RecordMutationService(database, database.exerciseDao(), database.workoutDao())
            .deleteWorkoutEntry(f.target)
        val dao = database.programProgressionDao()
        assertTrue(dao.suggestions().isEmpty())
        assertEquals(listOf(f.source.id), dao.links().map { it.entryId })
        assertEquals(1, dao.applications().size)
        assertEquals(1, dao.tracks().size)
        assertNotNull(database.workoutDao().findEntryById(f.source.id))
    }

    @Test fun deletingBothWorkoutsAndTemplateCleansAllExecutionBookkeeping() = runBlocking {
        val database = db()
        val f = fixture(database)
        val mutation = RecordMutationService(database, database.exerciseDao(), database.workoutDao())
        mutation.deleteWorkoutEntry(f.source)
        mutation.deleteWorkoutEntry(f.target)
        val dao = database.programProgressionDao()
        assertTrue(dao.suggestions().isEmpty())
        assertTrue(dao.links().isEmpty())
        assertTrue(dao.applications().isEmpty())
        assertEquals(1, dao.tracks().size) // template binding is still alive

        planService(database).deleteProgram(f.programId)
        assertTrue(dao.items().isEmpty())
        assertTrue(dao.tracks().isEmpty())
        assertTrue(database.programDao().allPrograms().isEmpty())
    }

    @Test fun calendarDateAndRangeDeletionPruneOldLinksAndDecisions() = runBlocking {
        val database = db()
        val f = fixture(database)
        val repository = TrainingRepository(database, context)

        repository.deleteDate(f.target.date)
        assertTrue(database.programProgressionDao().suggestions().isEmpty())
        assertEquals(listOf(f.source.id), database.programProgressionDao().links().map { it.entryId })

        repository.deleteDateRange(f.source.date, f.source.date, includeConfirmed = true)
        assertTrue(database.programProgressionDao().links().isEmpty())
        assertTrue(database.programProgressionDao().applications().isEmpty())
        // The template still needs its track until the user removes it.
        assertEquals(1, database.programProgressionDao().tracks().size)
        assertNotNull(database.programDao().findProgram(f.programId))
    }

    @Test fun deletingTemplateOnlyPreservesAppliedWorkoutsAndProgressionDecisions() = runBlocking {
        val database = db()
        val f = fixture(database)
        planService(database).deleteProgram(f.programId)
        val dao = database.programProgressionDao()
        assertTrue(dao.items().isEmpty())
        assertEquals(2, dao.links().size)
        assertEquals(1, dao.suggestions().size)
        assertEquals(1, dao.tracks().size)
        assertEquals(1, dao.applications().size)
        assertNotNull(database.workoutDao().findEntryById(f.source.id))
        assertNotNull(database.workoutDao().findEntryById(f.target.id))
    }

    @Test fun startupPrunesLegacyOrphansOnceAndCloudRevisionOnlyChangesOnce() = runBlocking {
        val database = db()
        val f = fixture(database)
        // Simulate an old app deleting the workout directly. Room cascades its
        // applied link, but leaves a dangling suggestion (no FK) behind.
        database.workoutDao().deleteSetsForEntry(f.target.id)
        database.workoutDao().deleteEntryById(f.target.id)
        val repo = TrainingRepository(database, context)
        val revisionBefore = database.cloudBackupStateDao().getOrCreate().localRevision
        val first = repo.reconcileProgressionOrphans()
        assertEquals(1, first.suggestions)
        assertTrue(database.programProgressionDao().suggestions().isEmpty())
        assertEquals(revisionBefore + 1, database.cloudBackupStateDao().getOrCreate().localRevision)
        val second = repo.reconcileProgressionOrphans()
        assertEquals(0, second.removed)
        assertEquals(revisionBefore + 1, database.cloudBackupStateDao().getOrCreate().localRevision)
    }

    @Test fun canonicalBackupCleansInvalidLegacySuggestionBeforeExport() = runBlocking {
        val database = db()
        // A former program was fully removed, leaving only an invalid decision.
        val application = ProgramApplication(programStableKey = "deleted", programName = "Deleted",
            startDate = "2026-09-01")
        val track = ProgramProgressionTrack(programStableKey = "deleted",
            exerciseStableKey = "deleted_exercise", label = "Old")
        val dao = database.programProgressionDao()
        dao.putTrack(track)
        dao.putApplication(application)
        dao.putSuggestion(ProgressionSuggestion(
            applicationId = application.id, trackId = track.id,
            sourceEntryId = 1001L, targetEntryId = 1002L,
            previousActualKg = 130.0, currentPlanKg = -5.0, suggestedKg = 131.5,
            judgmentRpe = 6.5, direction = ProgressionDirection.INCREASE,
            reasons = "HISTORICAL_BROKEN_DATA", evidenceHash = "gone",
            rule = ProgressionRule()
        ))

        val repo = TrainingRepository(database, context)
        val result = repo.canonicalRecordsBackup()
        assertTrue(result.csv.isNotBlank())
        assertTrue(dao.suggestions().isEmpty())
        assertTrue(dao.applications().isEmpty())
        assertTrue(dao.tracks().isEmpty())
        assertEquals(0, repo.reconcileProgressionOrphans().removed)
    }

    @Test fun cleanupDoesNotPurgeOrModifyCompletedWorkouts() = runBlocking {
        val database = db()
        val f = fixture(database)
        val actual = database.workoutDao().setsForEntry(f.source.id).single()
        database.workoutDao().updateSet(actual.copy(confirmed = true, rpe = 8.0))
        planService(database).deleteProgram(f.programId)

        val removed = database.withTransaction {
            ProgramProgressionOrphanCleanup.pruneInTransaction(database)
        }
        assertEquals(0, removed.removed)
        assertTrue(database.workoutDao().setsForEntry(f.source.id).single().confirmed)
        assertEquals(2, database.programProgressionDao().links().size)
    }
}

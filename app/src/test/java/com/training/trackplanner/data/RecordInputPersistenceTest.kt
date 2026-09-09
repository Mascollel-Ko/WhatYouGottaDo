package com.training.trackplanner.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.RecordDerivedRefreshCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RecordInputPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val folder = Files.createTempDirectory("wgtd-record-save-").toFile()
    private val fileContext = object : ContextWrapper(context) {
        override fun getDatabasePath(name: String) = java.io.File(folder, name)
    }
    private val databases = mutableListOf<TrainingDatabase>()
    private fun database(name: String = "records.db") = Room.databaseBuilder(fileContext, TrainingDatabase::class.java, name)
        .allowMainThreadQueries().build().also { databases += it }
    @After fun close() { databases.forEach { it.close() }; folder.listFiles().orEmpty().forEach { it.delete() }; folder.delete() }
    private suspend fun seed(db: TrainingDatabase, date: String, count: Int = 2): List<WorkoutSet> {
        val exercise = Exercise("ex_a61f1e96", "Incline dumbbell press", "근력운동", activityKind = "TRAINING_EXERCISE", volumeLoadEligible = true)
        db.exerciseDao().insertExercise(exercise)
        // Separate entries prove completion is date-wide, not entry-local.
        return (1..count).map { index ->
            val performedAt = java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val entry = db.workoutDao().insertEntry(WorkoutEntry(date = date, exerciseStableKey = exercise.stableKey,
                exerciseName = exercise.name, category = exercise.category, backupSourceId = "source-$index",
                createdAt = performedAt, performedAt = performedAt))
            val set = WorkoutSet(entryId = entry, setIndex = 1, reps = 5, weightKg = 30.0)
            set.copy(id = db.workoutDao().insertSet(set))
        }
    }

    @Test fun `every field commits before notification and survives database and repository reopen`() = runBlocking {
        val first = database()
        val sets = seed(first, "2026-09-10")
        val repository = TrainingRepository(first, context)
        val changed = sets.first().copy(reps = 9, weightKg = 42.5, seconds = 37, confirmed = true,
            manualWeight = true, rpe = 8.5, restSecondsOverride = 83)
        val result = checkNotNull(repository.updateSet(changed))
        assertEquals(changed, first.workoutDao().findSetById(changed.id))
        assertEquals(StrengthSessionCompletionState(2, 0), result.beforeCompletionState)
        assertEquals(StrengthSessionCompletionState(1, 1), result.afterCompletionState)
        assertFalse(result.dateJustCompleted)
        assertTrue(result.newlyConfirmed)
        val entry = first.workoutDao().findEntryById(changed.entryId)!!
        assertNotNull(entry.firstConfirmedAt)
        assertNotNull(entry.completedAt)
        assertEquals("source-1", entry.backupSourceId)
        first.close() // No derived job was launched.
        val reopened = database()
        val recreated = TrainingRepository(reopened, context)
        assertEquals(changed, reopened.workoutDao().findSetById(changed.id))
        assertFalse(checkNotNull(recreated.updateSet(changed)).derivedAnalysisDirty)
        assertEquals(entry, reopened.workoutDao().findEntryById(changed.entryId))
    }

    @Test(timeout = 30_000) fun `blocked completion refresh does not prevent subsequent durable saves`() = runBlocking {
        val db = database()
        val sets = seed(db, "2026-09-10")
        val repository = TrainingRepository(db, context)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var progressionCalls = 0
        var analysisCalls = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val worker = RecordDerivedRefreshCoordinator(scope, {
            progressionCalls++
            started.complete(Unit)
            release.await()
            analysisCalls++
        }, { throw it })
        try {
            worker.afterCommit(checkNotNull(repository.updateSet(sets[0].copy(confirmed = true))))
            assertEquals(0, progressionCalls)
            val final = checkNotNull(repository.updateSet(sets[1].copy(confirmed = true)))
            assertTrue(final.dateJustCompleted)
            assertTrue(db.workoutDao().findSetById(sets[1].id)!!.confirmed)
            worker.afterCommit(final)
            started.await()
            assertEquals(1, progressionCalls)
            assertEquals(0, analysisCalls)
            repeat(5) { index ->
                val updated = sets[1].copy(confirmed = true, rpe = 7.0 + index * 0.5, weightKg = 31.0 + index)
                // No notification needed to make this raw save durable, even while heavy work is gated.
                repository.updateSet(updated)
                assertEquals(updated, db.workoutDao().findSetById(updated.id))
            }
            release.complete(Unit)
            assertEquals(1, analysisCalls)
        } finally { scope.cancel() }
    }

    @Test(timeout = 30_000) fun `blocked tissue dependency does not own the raw write transaction`() = runBlocking {
        val db = database()
        val set = seed(db, "2026-09-10").first()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val heavy = launch(Dispatchers.IO) {
            ProgramProgressionService(db) { started.complete(Unit); release.await(); emptySet() }.refresh()
        }
        try {
            started.await()
            val changed = set.copy(rpe = 9.0, manualWeight = true)
            withContext(Dispatchers.IO) { RecordMutationService(db, db.exerciseDao(), db.workoutDao()).updateSet(changed) }
            assertEquals(changed, db.workoutDao().findSetById(changed.id))
        } finally { release.complete(Unit); heavy.join() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `ten committed edits produce one derived refresh under virtual time`() = runBlocking {
        val db = database()
        val set = seed(db, "2026-09-10", 1).single().copy(confirmed = true)
        val repo = TrainingRepository(db, context)
        repo.updateSet(set)
        val scope = TestScope()
        var progression = 0
        var analysis = 0
        val worker = RecordDerivedRefreshCoordinator(scope, { progression++; analysis++ }, { throw it })
        try {
            repeat(10) { index ->
                val changed = set.copy(rpe = 5.0 + index * 0.5, weightKg = 40.0 + index)
                val committed = checkNotNull(repo.updateSet(changed))
                assertEquals(changed, db.workoutDao().findSetById(set.id))
                worker.afterCommit(committed)
                scope.runCurrent()
                scope.advanceTimeBy(100)
            }
            assertEquals(0, progression)
            scope.advanceTimeBy(750)
            scope.runCurrent()
            assertEquals(1, progression)
            assertEquals(1, analysis)
        } finally { scope.cancel() }
    }

    @Test fun `deferred final dataset and reopened startup match canonical analysis reads`() = runBlocking {
        val db = database()
        val date = java.time.LocalDate.now().toString()
        val analysisTime = java.time.LocalDate.parse(date).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        suspend fun tissueAtFixedTime(source: TrainingDatabase) = ConnectiveTissueAnalysisService(
            context, source.exerciseDao(), source.workoutDao(), source.dailyMetricDao(),
            source.initialUserProfileDao(), source.dailyCheckInDao()
        ).build(analysisTime)
        val sets = seed(db, date)
        val repo = TrainingRepository(db, context)
        sets.forEach { repo.updateSet(it.copy(confirmed = true, rpe = 8.0)) }
        repo.refreshRecordDerivedState()
        val fatigue = repo.fatigueAnalysisHistory()
        val tissue = tissueAtFixedTime(db)
        val readiness = repo.todayReadinessSummary()
        val performance = repo.performanceTrendSummary()
        assertTrue(fatigue.isNotEmpty())
        assertTrue(fatigue.any { it.state.confirmedTrainingLoad > 0.0 })
        val referenceDb = database("immediate-reference.db")
        val reference = TrainingRepository(referenceDb, context)
        seed(referenceDb, date).forEach {
            reference.updateSet(it.copy(confirmed = true, rpe = 8.0))
            // Prior canonical scheduling: recalculate after EACH edit, including intermediate ones.
            reference.refreshRecordDerivedState()
            reference.fatigueAnalysisHistory()
            reference.connectiveTissueState()
            reference.todayReadinessSummary()
            reference.performanceTrendSummary()
        }
        assertEquals(fatigue, reference.fatigueAnalysisHistory())
        assertEquals(tissue, tissueAtFixedTime(referenceDb))
        assertEquals(readiness, reference.todayReadinessSummary().copy(generatedAt = readiness.generatedAt))
        assertEquals(performance, reference.performanceTrendSummary())
        // The same canonical readers must be independent of the refresh scheduling/instance lifetime.
        db.close()
        val reopened = database()
        val fresh = TrainingRepository(reopened, context)
        fresh.refreshRecordDerivedState()
        assertEquals(fatigue, fresh.fatigueAnalysisHistory())
        assertEquals(tissue, tissueAtFixedTime(reopened))
        assertEquals(readiness, fresh.todayReadinessSummary().copy(generatedAt = readiness.generatedAt))
        assertEquals(performance, fresh.performanceTrendSummary())
    }
}

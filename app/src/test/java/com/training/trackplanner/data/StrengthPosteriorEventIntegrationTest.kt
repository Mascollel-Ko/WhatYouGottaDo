package com.training.trackplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrengthPosteriorEventIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun `completion transition processes exactly one immutable event`() = runBlocking {
        val db = newDatabase()
        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator(db).ensureCurrentRevision().status)
        val service = mutationService(db)
        val fixture = insertSession(db, "2026-07-20", listOf(false, false))

        service.updateSet(fixture.sets[0].copy(confirmed = true))
        assertTrue(db.strengthPosteriorDao().allEvents().isEmpty())

        service.updateSet(fixture.sets[1].copy(confirmed = true))
        val event = db.strengthPosteriorDao().allEvents().single()
        val originalHistory = db.strengthPosteriorDao().historyForEvent(event.eventUuid)
        assertEquals(StrengthPosteriorEventProcessor.STATUS_PROCESSED, event.status)
        assertTrue(originalHistory.isNotEmpty())
        assertNotNull(
            db.strengthPosteriorDao().modelState(
                StrengthModelRevisionPolicy.modelInstanceKey(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
            )
        )

        val confirmed = checkNotNull(db.workoutDao().findSetById(fixture.sets[1].id))
        service.updateSet(confirmed.copy(weightKg = 125.0))
        service.deleteWorkoutEntry(checkNotNull(db.workoutDao().findEntryById(fixture.entryId)))

        assertEquals(1, db.strengthPosteriorDao().allEvents().size)
        assertEquals(originalHistory, db.strengthPosteriorDao().historyForEvent(event.eventUuid))
    }

    @Test
    fun `deleting the final planned set processes partial completion but deleting all does not`() = runBlocking {
        val partialDb = newDatabase()
        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator(partialDb).ensureCurrentRevision().status)
        val partialService = mutationService(partialDb)
        val partial = insertSession(partialDb, "2026-07-21", listOf(true, false))

        assertTrue(partialService.deleteSet(partial.sets[1]))
        assertEquals(
            StrengthPosteriorEventProcessor.STATUS_PROCESSED,
            partialDb.strengthPosteriorDao().allEvents().single().status
        )
        partialDb.close()

        val deletedDb = newDatabase()
        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator(deletedDb).ensureCurrentRevision().status)
        val deletedService = mutationService(deletedDb)
        val deleted = insertSession(deletedDb, "2026-07-22", listOf(true, false))
        deletedService.deleteWorkoutEntry(checkNotNull(deletedDb.workoutDao().findEntryById(deleted.entryId)))

        assertTrue(deletedDb.strengthPosteriorDao().allEvents().isEmpty())
    }

    @Test
    fun `failed processing is atomic and retryable without duplicate history`() = runBlocking {
        val db = newDatabase()
        val fixture = insertSession(db, "2026-07-23", listOf(true))
        val coordinator = coordinator(db)
        val event = pendingEvent(
            date = "2026-07-23",
            fingerprint = "not-the-current-fingerprint",
            confirmedCount = fixture.sets.size
        )
        db.strengthPosteriorDao().insertPendingEvent(event)

        coordinator.retryPending()
        assertEquals(StrengthPosteriorEventProcessor.STATUS_FAILED, db.strengthPosteriorDao().eventByUuid(event.eventUuid)?.status)
        assertTrue(db.strengthPosteriorDao().historyForEvent(event.eventUuid).isEmpty())
        assertTrue(db.strengthPosteriorDao().evidenceForEvent(event.eventUuid).isEmpty())
        assertEquals(
            null,
            db.strengthPosteriorDao().modelState(
                StrengthModelRevisionPolicy.modelInstanceKey(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
            )
        )

        coordinator.retryPending()
        assertTrue(db.strengthPosteriorDao().historyForEvent(event.eventUuid).isEmpty())
    }

    @Test
    fun `derived reset rebuilds current revision in date order and stays idempotent`() = runBlocking {
        val db = newDatabase()
        insertSession(db, "2026-07-02", listOf(true))
        insertSession(db, "2026-07-01", listOf(true))
        val coordinator = coordinator(db)

        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator.ensureCurrentRevision().status)
        val originalEvents = db.strengthPosteriorDao().allEvents()
        val originalHistory = db.strengthPosteriorDao().allHistory()
        db.appMetaDao().delete(StrengthModelRevisionPolicy.REBUILD_MARKER_KEY)
        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator.ensureCurrentRevision().status)
        coordinator.retryPending()

        val events = db.strengthPosteriorDao().allEvents()
        assertEquals(listOf("2026-07-01", "2026-07-02"), events.map(StrengthPosteriorEventEntity::sessionDate))
        assertEquals(originalEvents, events)
        assertEquals(originalHistory, db.strengthPosteriorDao().allHistory())
        assertEquals(2, events.size)
        assertTrue(events.all { event -> event.status == StrengthPosteriorEventProcessor.STATUS_PROCESSED })
        assertEquals(
            StrengthModelRevisionPolicy.STATUS_ACTIVE,
            db.strengthPosteriorDao().activeRevision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)?.status
        )
        assertNotNull(db.appMetaDao().value(StrengthModelRevisionPolicy.REBUILD_MARKER_KEY))
    }

    @Test
    fun `incompatible legacy derived rows are reset and rebuilt from unchanged raw history`() = runBlocking {
        val db = newDatabase()
        val incline = Exercise(
            name = "Incline dumbbell press",
            category = "Strength",
            stableKey = "ex_a61f1e96"
        )
        insertSession(db, "2026-07-01", listOf(true), incline, weightKg = 52.0)
        insertSession(db, "2026-07-15", listOf(true), incline, weightKg = 60.0)
        val coordinator = coordinator(db)
        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator.ensureCurrentRevision().status)
        replaceCurrentDerivedWithLegacySnapshot(db)
        val rawBefore = db.workoutDao().completedWorkoutDates()
            .associateWith { date -> db.workoutDao().entriesWithSets(date) }

        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator.ensureCurrentRevision().status)

        val dao = db.strengthPosteriorDao()
        val revision = checkNotNull(dao.activeRevision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY))
        val events = dao.eventsForRevision(revision.revisionKey)
        val history = dao.historyForRevision(revision.revisionKey)
        assertEquals(rawBefore, db.workoutDao().completedWorkoutDates().associateWith { date ->
            db.workoutDao().entriesWithSets(date)
        })
        assertNull(dao.revision(StrengthModelRevisionPolicy.LEGACY_REVISION_KEY))
        assertEquals(listOf(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY), dao.allRevisions().map { it.revisionKey })
        assertEquals(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY, revision.revisionKey)
        assertEquals(StrengthPosteriorModel.MODEL_VERSION, revision.modelVersion)
        assertEquals(RpeRirPolicy.POLICY_VERSION, revision.rirPolicyVersion)
        assertEquals(RepetitionCurveRegistry.CURVE_VERSION, revision.curveVersion)
        assertEquals(2, events.size)
        assertEquals(events.size, history.size)
        assertEquals(2, dao.localStates(revision.revisionKey).single().observationCount)
        val transfer = dao.proxyHistory(revision.revisionKey).single()
        assertEquals(0.0, transfer.targetSpecificContribution, 0.0)
        assertTrue(transfer.applied)
        assertEquals(1, dao.proxyHistory(revision.revisionKey).size)
        assertTrue(dao.allModelStates().all { state ->
            state.modelInstanceKey.contains(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
        })
        assertTrue(dao.allCurvePosteriors().all { curve ->
            curve.curveSubjectKey.startsWith("${StrengthModelRevisionPolicy.CURRENT_REVISION_KEY}|")
        })
        assertNotNull(db.appMetaDao().value(StrengthModelRevisionPolicy.REBUILD_MARKER_KEY))
    }

    @Test
    fun `building correction revision resumes without duplicate history`() = runBlocking {
        val db = newDatabase()
        insertSession(db, "2026-07-01", listOf(true))
        val coordinator = coordinator(db)
        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator.ensureCurrentRevision().status)

        val dao = db.strengthPosteriorDao()
        val revisionKey = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
        val eventCount = dao.eventsForRevision(revisionKey).size
        val historyCount = dao.historyForRevision(revisionKey).size
        dao.updateRevisionStatus(
            revisionKey,
            StrengthModelRevisionPolicy.STATUS_BUILDING,
            null,
            null,
            null
        )
        db.appMetaDao().delete(StrengthModelRevisionPolicy.REBUILD_MARKER_KEY)

        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator.ensureCurrentRevision().status)
        assertEquals(StrengthModelRevisionPolicy.STATUS_ACTIVE, dao.revision(revisionKey)?.status)
        assertEquals(eventCount, dao.eventsForRevision(revisionKey).size)
        assertEquals(historyCount, dao.historyForRevision(revisionKey).size)
    }

    @Test
    fun `analysis reads frozen posterior history after source workout is deleted`() = runBlocking {
        val db = newDatabase()
        val fixture = insertSession(db, "2026-07-20", listOf(true))
        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator(db).ensureCurrentRevision().status)
        mutationService(db).deleteWorkoutEntry(checkNotNull(db.workoutDao().findEntryById(fixture.entryId)))
        assertTrue(db.workoutDao().entriesWithSets("2026-07-20").isEmpty())

        val summary = PerformanceTrendSummaryService(
            exerciseDao = db.exerciseDao(),
            workoutDao = db.workoutDao(),
            dailyMetricDao = db.dailyMetricDao(),
            initialUserProfileDao = db.initialUserProfileDao(),
            dailyCheckInDao = db.dailyCheckInDao(),
            smashSpeedDao = db.smashSpeedDao(),
            runtimeExerciseMetadataDao = db.runtimeExerciseMetadataDao(),
            canonicalRuntimeMetadataCatalog = RuntimeExerciseMetadataCatalogProvider.get(context),
            strengthPosteriorDao = db.strengthPosteriorDao(),
            strengthPerformanceRegistry = StrengthPerformanceRegistry.fromContext(context),
            appMetaDao = db.appMetaDao()
        ).build()

        val persistent = checkNotNull(summary.persistentStrengthPerformanceSummary)
        val bench = persistent.targets.single { target -> target.targetKey == StrengthPerformanceRegistry.BENCH_PRESS.value }
        assertTrue(bench.history.isNotEmpty())
    }

    @Test
    fun `later sessions and curve calibration never rewrite earlier history`() = runBlocking {
        val db = newDatabase()
        insertSession(db, "2026-07-01", listOf(true))
        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator(db).ensureCurrentRevision().status)
        val firstEvent = db.strengthPosteriorDao().allEvents().single()
        val frozenHistory = db.strengthPosteriorDao().historyForEvent(firstEvent.eventUuid)

        val later = insertSession(db, "2026-07-02", listOf(false))
        mutationService(db).updateSet(later.sets.single().copy(confirmed = true))

        assertEquals(2, db.strengthPosteriorDao().allEvents().size)
        assertEquals(frozenHistory, db.strengthPosteriorDao().historyForEvent(firstEvent.eventUuid))
    }

    @Test
    fun `failed rebuild exposes no legacy summary and retries from unchanged raw history`() = runBlocking {
        val db = newDatabase()
        insertSession(db, "2026-07-01", listOf(true))
        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator(db).ensureCurrentRevision().status)
        replaceCurrentDerivedWithLegacySnapshot(db)
        val rawBefore = db.workoutDao().entriesWithSets("2026-07-01")

        val failed = coordinator(db, failProcessing = true).ensureCurrentRevision()
        assertEquals(StrengthAnalysisLifecycleStatus.REBUILD_FAILED, failed.status)
        assertEquals("TEST_POSTERIOR_FAILURE", failed.diagnosticCode)
        assertTrue(failed.diagnosticMessage.orEmpty().contains("실패한 운동일: 2026-07-01"))
        assertTrue(failed.diagnosticMessage.orEmpty().contains("테스트 계산 실패"))
        assertEquals(
            StrengthModelRevisionPolicy.STATUS_FAILED,
            db.strengthPosteriorDao().revision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)?.status
        )
        assertNull(db.strengthPosteriorDao().revision(StrengthModelRevisionPolicy.LEGACY_REVISION_KEY))
        assertNull(db.appMetaDao().value(StrengthModelRevisionPolicy.REBUILD_MARKER_KEY))
        assertEquals(rawBefore, db.workoutDao().entriesWithSets("2026-07-01"))

        val summary = persistentSummary(db)
        assertEquals(StrengthAnalysisLifecycleStatus.REBUILD_FAILED, summary.lifecycleStatus)
        assertEquals("TEST_POSTERIOR_FAILURE", summary.lifecycleDiagnosticCode)
        assertTrue(summary.lifecycleDiagnosticMessage.orEmpty().contains("테스트 계산 실패"))
        assertTrue(summary.targets.isEmpty())
        assertNull(summary.activeRevisionKey)

        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator(db).ensureCurrentRevision().status)
        assertEquals(1, db.strengthPosteriorDao().eventsForRevision(
            StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
        ).size)
        assertEquals(rawBefore, db.workoutDao().entriesWithSets("2026-07-01"))
    }

    @Test
    fun `seven relevant squat sessions persist seven target history points`() = runBlocking {
        val db = newDatabase()
        repeat(7) { index ->
            insertSession(
                db = db,
                date = "2026-07-${(index + 1).toString().padStart(2, '0')}",
                confirmed = listOf(true),
                exercise = frontSquatExercise()
            )
        }

        assertEquals(StrengthAnalysisLifecycleStatus.CURRENT, coordinator(db).ensureCurrentRevision().status)
        val history = db.strengthPosteriorDao().historyForTarget(StrengthPerformanceRegistry.BACK_SQUAT.value)

        assertEquals(7, history.map { row -> row.eventUuid }.distinct().size)
        assertEquals(7, history.size)
    }

    @Test
    fun `completion fingerprint is independent of local Room ids`() = runBlocking {
        val first = WorkoutEntryWithSets(
            WorkoutEntry(1, "2026-07-24", "barbell_bench_press", "Bench", "Strength", createdAt = 100L),
            listOf(WorkoutSet(20, 1, 1, 5, 80.0, confirmed = true, rpe = 10.0))
        )
        val second = WorkoutEntryWithSets(
            WorkoutEntry(99, "2026-07-24", "barbell_bench_press", "Bench", "Strength", createdAt = 100L),
            listOf(WorkoutSet(77, 99, 1, 5, 80.0, confirmed = true, rpe = 10.0))
        )

        assertEquals(
            StrengthCompletionFingerprint.build(
                "2026-07-24",
                listOf(first),
                mapOf("barbell_bench_press" to benchExercise())
            ),
            StrengthCompletionFingerprint.build(
                "2026-07-24",
                listOf(second),
                mapOf("barbell_bench_press" to benchExercise())
            )
        )
    }

    @Test
    fun `completion detector accepts only the canonical finalization transition`() {
        val eligible = StrengthSessionCompletionState(unconfirmedSetCount = 1, confirmedSetCount = 1)
        assertTrue(StrengthSessionCompletionDetector.eligible(eligible, StrengthSessionCompletionState(0, 2)))
        assertFalse(StrengthSessionCompletionDetector.eligible(eligible, StrengthSessionCompletionState(0, 0)))
        assertFalse(StrengthSessionCompletionDetector.eligible(StrengthSessionCompletionState(0, 1), StrengthSessionCompletionState(0, 2)))
        assertFalse(StrengthSessionCompletionDetector.eligible(eligible, StrengthSessionCompletionState(1, 1)))
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun mutationService(db: TrainingDatabase): RecordMutationService =
        RecordMutationService(db, db.exerciseDao(), db.workoutDao(), coordinator(db))

    private fun coordinator(
        db: TrainingDatabase,
        failProcessing: Boolean = false
    ): StrengthPosteriorUpdateCoordinator {
        val processor = StrengthPosteriorEventProcessor(
            exerciseDao = db.exerciseDao(),
            workoutDao = db.workoutDao(),
            dailyMetricDao = db.dailyMetricDao(),
            dailyCheckInDao = db.dailyCheckInDao(),
            initialUserProfileDao = db.initialUserProfileDao(),
            posteriorDao = db.strengthPosteriorDao(),
            registry = StrengthPerformanceRegistry.fromContext(context),
            curves = RepetitionCurveRegistry.fromContext(context),
            rirPolicy = RpeRirPolicy.fromContext(context),
            now = { 2_000L }
        )
        return StrengthPosteriorUpdateCoordinator(
            db = db,
            exerciseDao = db.exerciseDao(),
            workoutDao = db.workoutDao(),
            appMetaDao = db.appMetaDao(),
            posteriorDao = db.strengthPosteriorDao(),
            processor = processor,
            now = { 1_000L },
            processEvent = if (failProcessing) {
                { eventUuid ->
                    db.strengthPosteriorDao().updateEventStatus(
                        eventUuid = eventUuid,
                        status = StrengthPosteriorEventProcessor.STATUS_FAILED,
                        processedAt = null,
                        evidenceFingerprint = null,
                        errorCode = "TEST_POSTERIOR_FAILURE",
                        errorMessage = "테스트 계산 실패"
                    )
                    false
                }
            } else {
                { eventUuid -> processor.process(eventUuid) }
            }
        )
    }

    private suspend fun persistentSummary(db: TrainingDatabase) = PerformanceTrendSummaryService(
        exerciseDao = db.exerciseDao(),
        workoutDao = db.workoutDao(),
        dailyMetricDao = db.dailyMetricDao(),
        initialUserProfileDao = db.initialUserProfileDao(),
        dailyCheckInDao = db.dailyCheckInDao(),
        smashSpeedDao = db.smashSpeedDao(),
        runtimeExerciseMetadataDao = db.runtimeExerciseMetadataDao(),
        canonicalRuntimeMetadataCatalog = RuntimeExerciseMetadataCatalogProvider.get(context),
        strengthPosteriorDao = db.strengthPosteriorDao(),
        strengthPerformanceRegistry = StrengthPerformanceRegistry.fromContext(context),
        appMetaDao = db.appMetaDao()
    ).build().persistentStrengthPerformanceSummary!!

    private suspend fun replaceCurrentDerivedWithLegacySnapshot(db: TrainingDatabase) {
        val dao = db.strengthPosteriorDao()
        val currentKey = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
        val legacyKey = StrengthModelRevisionPolicy.LEGACY_REVISION_KEY
        val sourceEvents = dao.eventsForRevision(currentKey)
        val eventIds = sourceEvents.associate { event -> event.eventUuid to "legacy-${event.eventUuid}" }
        val evidenceIds = dao.evidenceForRevision(currentKey).associate { row ->
            row.evidenceFingerprint to "legacy-${row.evidenceFingerprint}"
        }
        val history = dao.historyForRevision(currentKey)
        val evidence = dao.evidenceForRevision(currentKey)
        val modelStates = dao.allModelStates()
        val curves = dao.allCurvePosteriors()
        val localStates = dao.localStates(currentKey)
        val localHistory = dao.localHistory(currentKey)
        val proxyHistory = dao.proxyHistory(currentKey)

        dao.clearAllStrengthDerivedData()
        dao.insertRevisionStrict(
            StrengthModelRevisionPolicy.legacy(500L).copy(
                status = StrengthModelRevisionPolicy.STATUS_ACTIVE
            )
        )
        sourceEvents.forEach { event ->
            dao.insertEventStrict(
                event.copy(
                    eventUuid = checkNotNull(eventIds[event.eventUuid]),
                    completionFingerprint = "legacy-${event.completionFingerprint}",
                    evidenceFingerprint = event.evidenceFingerprint?.let { "legacy-$it" },
                    revisionKey = legacyKey
                )
            )
        }
        dao.insertHistoryStrict(history.map { row ->
            row.copy(
                eventUuid = checkNotNull(eventIds[row.eventUuid]),
                evidenceFingerprint = checkNotNull(evidenceIds[row.evidenceFingerprint])
            )
        })
        dao.insertEvidenceStrict(evidence.map { row ->
            row.copy(
                evidenceFingerprint = checkNotNull(evidenceIds[row.evidenceFingerprint]),
                eventUuid = checkNotNull(eventIds[row.eventUuid])
            )
        })
        modelStates.forEach { state ->
            dao.upsertModelState(
                state.copy(
                    modelInstanceKey = StrengthModelRevisionPolicy.modelInstanceKey(legacyKey),
                    lastProcessedEventUuid = state.lastProcessedEventUuid?.let { checkNotNull(eventIds[it]) },
                    stateFingerprint = "legacy-${state.stateFingerprint}"
                )
            )
        }
        dao.upsertCurvePosteriors(curves.map { curve ->
            curve.copy(
                curveSubjectKey = StrengthModelRevisionPolicy.curveSubjectKey(
                    legacyKey,
                    StrengthModelRevisionPolicy.originalCurveSubjectKey(currentKey, curve.curveSubjectKey)
                ),
                posteriorFingerprint = "legacy-${curve.posteriorFingerprint}"
            )
        })
        dao.upsertLocalStates(localStates.map { state ->
            state.copy(
                revisionKey = legacyKey,
                lastProcessedEventUuid = checkNotNull(eventIds[state.lastProcessedEventUuid]),
                stateFingerprint = "legacy-${state.stateFingerprint}"
            )
        })
        dao.insertLocalHistoryStrict(localHistory.map { row ->
            row.copy(
                revisionKey = legacyKey,
                eventUuid = checkNotNull(eventIds[row.eventUuid]),
                evidenceFingerprint = checkNotNull(evidenceIds[row.evidenceFingerprint])
            )
        })
        dao.insertProxyHistoryStrict(proxyHistory.map { row ->
            row.copy(
                revisionKey = legacyKey,
                eventUuid = checkNotNull(eventIds[row.eventUuid]),
                transferFingerprint = "legacy-${row.transferFingerprint}"
            )
        })
        db.appMetaDao().delete(StrengthModelRevisionPolicy.REBUILD_MARKER_KEY)
        db.appMetaDao().upsert(
            AppMeta(StrengthModelRevisionPolicy.OBSOLETE_REBUILD_MARKER_KEY, "legacy-complete")
        )
        db.appMetaDao().upsert(
            AppMeta(StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY, "legacy-bootstrap")
        )
        db.appMetaDao().upsert(
            AppMeta(StrengthPosteriorUpdateCoordinator.RESTORE_PROVENANCE_KEY, "legacy-restore")
        )
    }

    private suspend fun insertSession(
        db: TrainingDatabase,
        date: String,
        confirmed: List<Boolean>,
        exercise: Exercise = benchExercise(),
        weightKg: Double = 80.0
    ): SessionFixture {
        if (db.exerciseDao().findByStableKey(exercise.stableKey) == null) {
            db.exerciseDao().insertExercise(exercise)
        }
        val exerciseStableKey = exercise.stableKey
        val entryId = db.workoutDao().insertEntry(
            WorkoutEntry(
                date = date,
                exerciseStableKey = exerciseStableKey,
            exerciseName = exercise.name,
            category = exercise.category,
                createdAt = 100L,
                rpe = 10.0
            )
        )
        val sets = confirmed.mapIndexed { index, isConfirmed ->
            val source = WorkoutSet(
                entryId = entryId,
                setIndex = index + 1,
                reps = 5,
                weightKg = weightKg + index,
                confirmed = isConfirmed,
                rpe = 10.0
            )
            source.copy(id = db.workoutDao().insertSet(source))
        }
        return SessionFixture(entryId, sets)
    }

    private fun benchExercise(id: Long = 0): Exercise = Exercise(
        name = "Bench press",
        category = "Strength",
        stableKey = "barbell_bench_press"
    )

    private fun frontSquatExercise(): Exercise = Exercise(
        name = "Front squat",
        category = "Strength",
        stableKey = "ex_c5043892",
        movementPattern = "KNEE_DOMINANT_LOWER",
        strengthProgressionGroup = "FRONT_SQUAT",
        estimated1RmEligible = true
    )

    private fun pendingEvent(date: String, fingerprint: String, confirmedCount: Int) = StrengthPosteriorEventEntity(
        eventUuid = "pending-$date",
        sessionKey = StrengthPosteriorUpdateCoordinator.sessionKey(date),
        sessionDate = date,
        completionFingerprint = fingerprint,
        status = StrengthPosteriorEventProcessor.STATUS_PENDING,
        creationReason = StrengthPosteriorUpdateCoordinator.REASON_LIVE_COMPLETION,
        confirmedSetCount = confirmedCount,
        createdAt = 1_000L,
        modelVersion = StrengthPosteriorModel.MODEL_VERSION,
        curveVersion = RepetitionCurveRegistry.CURVE_VERSION,
        factorSchemaVersion = StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION,
        revisionKey = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
    )

    private data class SessionFixture(val entryId: Long, val sets: List<WorkoutSet>)
}

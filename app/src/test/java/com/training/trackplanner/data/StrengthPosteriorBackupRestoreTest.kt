package com.training.trackplanner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.StrengthExerciseLocalState
import com.training.trackplanner.analysis.strengthperformance.StrengthFactorKey
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorState
import com.training.trackplanner.analysis.strengthperformance.VersionedDoubleArrayCodec
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrengthPosteriorBackupRestoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: TrainingDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
    }

    @Test
    fun `posterior row types round trip exact values versions and future target keys`() {
        val fixture = fixture()
        val csv = fixture.csv()
        val parsed = RecordCsvBackupRestore.parse(csv) as RecordCsvImportData.Restore

        assertEquals(6, parsed.backupSchemaVersion)
        assertTrue(parsed.posteriorFormatPresent)
        assertEquals(fixture.marker, parsed.posteriorBootstrapMarker)
        assertEquals(listOf(fixture.supersededRevision, fixture.revision), parsed.posteriorRevisions)
        assertEquals(listOf(fixture.event), parsed.posteriorEvents)
        assertEquals(listOf(fixture.history), parsed.posteriorHistory)
        assertEquals(listOf(fixture.state), parsed.posteriorModelStates)
        assertEquals(listOf(fixture.curve), parsed.curvePosteriors)
        assertEquals(listOf(fixture.evidence), parsed.posteriorEvidence)
        assertEquals(listOf(fixture.localState), parsed.posteriorLocalStates)
        assertEquals(listOf(fixture.localHistory), parsed.posteriorLocalHistory)
        assertEquals(listOf(fixture.proxyHistory), parsed.posteriorProxyHistory)
        assertTrue(csv.contains("strength.future_target"))
        assertTrue(csv.contains(StrengthPosteriorModel.MODEL_VERSION))
        assertTrue(csv.contains(RepetitionCurveRegistry.CURVE_VERSION))
    }

    @Test
    fun `new backup restores exact posterior and exact duplicate import is idempotent`() = runBlocking {
        val db = newDatabase()
        val repository = TrainingRepository(db, context)
        val fixture = fixture()
        val uri = writeBackup(fixture.csv())

        val first = repository.importRecordsBackup(uri)
        assertEquals(1, first.posteriorEventCount)
        assertEquals(1, first.posteriorHistoryCount)
        assertEquals(1, first.posteriorStateCount)
        assertEquals(1, first.posteriorCurveCount)
        assertEquals(1, first.posteriorEvidenceCount)
        assertEquals(2, first.posteriorRevisionCount)
        assertEquals(1, first.posteriorLocalStateCount)
        assertEquals(1, first.posteriorLocalHistoryCount)
        assertEquals(1, first.posteriorProxyTransferCount)
        assertEquals(
            listOf(fixture.supersededRevision, fixture.revision),
            db.strengthPosteriorDao().allRevisions()
        )
        assertEquals(fixture.event, db.strengthPosteriorDao().allEvents().single())
        assertEquals(fixture.history, db.strengthPosteriorDao().allHistory().single())
        assertEquals(fixture.state, db.strengthPosteriorDao().allModelStates().single())
        assertEquals(fixture.curve, db.strengthPosteriorDao().allCurvePosteriors().single())
        assertEquals(fixture.evidence, db.strengthPosteriorDao().allEvidence().single())
        assertEquals(fixture.localState, db.strengthPosteriorDao().localStates(fixture.revision.revisionKey).single())
        assertEquals(fixture.localHistory, db.strengthPosteriorDao().localHistory(fixture.revision.revisionKey).single())
        assertEquals(fixture.proxyHistory, db.strengthPosteriorDao().proxyHistory(fixture.revision.revisionKey).single())
        assertEquals(fixture.marker, db.appMetaDao().value(StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY))
        assertTrue(
            checkNotNull(db.appMetaDao().value(StrengthPosteriorUpdateCoordinator.RESTORE_PROVENANCE_KEY))
                .startsWith("PERSISTED_POSTERIOR_BACKUP|")
        )

        val duplicate = repository.importRecordsBackup(uri)
        assertEquals(0, duplicate.posteriorEventCount)
        assertEquals(10, duplicate.skippedDuplicateCount)
        assertEquals(1, db.strengthPosteriorDao().allEvents().size)
        assertEquals(1, db.strengthPosteriorDao().allHistory().size)
    }

    @Test
    fun `repository export and import preserve the complete posterior ledger`() = runBlocking {
        val fixture = fixture()
        val source = newDatabase()
        source.strengthPosteriorDao().insertRevisionStrict(fixture.supersededRevision)
        source.strengthPosteriorDao().insertRevisionStrict(fixture.revision)
        source.strengthPosteriorDao().insertEventStrict(fixture.event)
        source.strengthPosteriorDao().insertHistoryStrict(listOf(fixture.history))
        source.strengthPosteriorDao().upsertModelState(fixture.state)
        source.strengthPosteriorDao().upsertCurvePosterior(fixture.curve)
        source.strengthPosteriorDao().insertEvidenceStrict(listOf(fixture.evidence))
        source.strengthPosteriorDao().upsertLocalStates(listOf(fixture.localState))
        source.strengthPosteriorDao().insertLocalHistoryStrict(listOf(fixture.localHistory))
        source.strengthPosteriorDao().insertProxyHistoryStrict(listOf(fixture.proxyHistory))
        source.appMetaDao().upsert(
            AppMeta(StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY, fixture.marker)
        )
        val backup = writeBackup("")
        TrainingRepository(source, context).exportRecordsBackup(backup)
        source.close()

        val target = newDatabase()
        TrainingRepository(target, context).importRecordsBackup(backup)

        assertEquals(
            listOf(fixture.supersededRevision, fixture.revision),
            target.strengthPosteriorDao().allRevisions()
        )
        assertEquals(listOf(fixture.event), target.strengthPosteriorDao().allEvents())
        assertEquals(listOf(fixture.history), target.strengthPosteriorDao().allHistory())
        assertEquals(listOf(fixture.state), target.strengthPosteriorDao().allModelStates())
        assertEquals(listOf(fixture.curve), target.strengthPosteriorDao().allCurvePosteriors())
        assertEquals(listOf(fixture.evidence), target.strengthPosteriorDao().allEvidence())
        assertEquals(listOf(fixture.localState), target.strengthPosteriorDao().localStates(fixture.revision.revisionKey))
        assertEquals(listOf(fixture.localHistory), target.strengthPosteriorDao().localHistory(fixture.revision.revisionKey))
        assertEquals(listOf(fixture.proxyHistory), target.strengthPosteriorDao().proxyHistory(fixture.revision.revisionKey))
    }

    @Test
    fun `schema five event payload defaults to legacy revision`() {
        val current = fixture().event
        val encoded = StrengthPosteriorBackupCodec.encode(current).split('|')
        val legacyPayload = (listOf("strength-posterior-backup-v1") + encoded.drop(1).dropLast(1)).joinToString("|")

        assertEquals(
            current.copy(revisionKey = StrengthModelRevisionPolicy.LEGACY_REVISION_KEY),
            StrengthPosteriorBackupCodec.decodeEvent(legacyPayload)
        )
    }

    @Test
    fun `schema five posterior backup schedules one corrected revision without fabricating parsed rows`() = runBlocking {
        val db = newDatabase()
        val repository = TrainingRepository(db, context)
        val legacyEvent = fixture().event.copy(
            modelVersion = "strength-performance-model-2.1.0",
            curveVersion = "repetition-curve-assets-1.0.0",
            revisionKey = StrengthModelRevisionPolicy.LEGACY_REVISION_KEY
        )
        val encoded = StrengthPosteriorBackupCodec.encode(legacyEvent)
        val tokens = encoded.split('|')
        val legacyPayload = (listOf("strength-posterior-backup-v1") + tokens.drop(1).dropLast(1)).joinToString("|")
        val oldCsv = RecordCsvBackupRestore.buildRestoreCsv(
            entriesWithSets = emptyList(),
            metrics = emptyList(),
            posteriorEvents = listOf(legacyEvent)
        ).lineSequence().joinToString("\n") { line ->
            line.replaceFirst("6,", "5,").replace(encoded, legacyPayload)
        }

        repository.importRecordsBackup(writeBackup(oldCsv))

        assertEquals(legacyEvent, db.strengthPosteriorDao().allEvents().single())
        assertEquals(
            StrengthModelRevisionPolicy.STATUS_SUPERSEDED,
            db.strengthPosteriorDao().revision(StrengthModelRevisionPolicy.LEGACY_REVISION_KEY)?.status
        )
        assertEquals(
            StrengthModelRevisionPolicy.STATUS_ACTIVE,
            db.strengthPosteriorDao().revision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)?.status
        )
        assertNotNull(db.appMetaDao().value(StrengthModelRevisionPolicy.REBUILD_MARKER_KEY))
    }

    @Test
    fun `event UUID and completion fingerprint conflicts fail closed`() = runBlocking {
        val db = newDatabase()
        val repository = TrainingRepository(db, context)
        val fixture = fixture()
        repository.importRecordsBackup(writeBackup(fixture.csv()))

        val uuidConflict = fixture.copy(
            event = fixture.event.copy(completionFingerprint = "different-fingerprint")
        )
        assertTrue(runCatching { repository.importRecordsBackup(writeBackup(uuidConflict.csv())) }.isFailure)
        assertEquals(fixture.event, db.strengthPosteriorDao().allEvents().single())

        val fingerprintConflict = fixture.copy(
            event = fixture.event.copy(eventUuid = "different-event")
        )
        assertTrue(runCatching { repository.importRecordsBackup(writeBackup(fingerprintConflict.csv())) }.isFailure)
        assertEquals(fixture.event, db.strengthPosteriorDao().allEvents().single())
    }

    @Test
    fun `old backup imports and runs one corrected revision rebuild`() = runBlocking {
        val db = newDatabase()
        val repository = TrainingRepository(db, context)

        repository.importRecordsBackup(writeBackup(legacyBackup()))

        val event = db.strengthPosteriorDao().allEvents().single()
        assertEquals(StrengthModelRevisionPolicy.CORRECTION_REASON, event.creationReason)
        assertEquals(StrengthPosteriorEventProcessor.STATUS_PROCESSED, event.status)
        assertTrue(db.strengthPosteriorDao().historyForEvent(event.eventUuid).isNotEmpty())
        assertEquals(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY, event.revisionKey)
        assertEquals(
            StrengthModelRevisionPolicy.STATUS_ACTIVE,
            db.strengthPosteriorDao().activeRevision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)?.status
        )
        val marker = db.appMetaDao().value(StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY)
        assertNotNull(marker)
        assertTrue(checkNotNull(marker).contains(StrengthPosteriorUpdateCoordinator.REASON_LEGACY_BACKUP_BOOTSTRAP))
        assertTrue(
            checkNotNull(db.appMetaDao().value(StrengthPosteriorUpdateCoordinator.RESTORE_PROVENANCE_KEY))
                .startsWith("LEGACY_BACKUP_FORWARD_BOOTSTRAP|")
        )
    }

    private fun newDatabase(): TrainingDatabase =
        Room.inMemoryDatabaseBuilder(context, TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun writeBackup(csv: String): Uri {
        val file = File.createTempFile("strength-posterior-backup", ".csv")
        file.writeText(csv, Charsets.UTF_8)
        file.deleteOnExit()
        return Uri.fromFile(file)
    }

    private fun fixture(): BackupFixture {
        val supersededRevision = StrengthModelRevisionPolicy.legacy(now = 50L).copy(
            status = StrengthModelRevisionPolicy.STATUS_SUPERSEDED
        )
        val revision = StrengthModelRevisionPolicy.current(
            now = 100L,
            sourceRevisionKey = supersededRevision.revisionKey
        ).copy(
            status = StrengthModelRevisionPolicy.STATUS_ACTIVE,
            rebuildCompletedAt = 200L
        )
        val event = StrengthPosteriorEventEntity(
            eventUuid = "event-uuid-1",
            sessionKey = "date:2026-07-20",
            sessionDate = "2026-07-20",
            completionFingerprint = "completion-fingerprint-1",
            status = StrengthPosteriorEventProcessor.STATUS_PROCESSED,
            creationReason = StrengthPosteriorUpdateCoordinator.REASON_LIVE_COMPLETION,
            confirmedSetCount = 2,
            createdAt = 100L,
            processedAt = 200L,
            modelVersion = StrengthPosteriorModel.MODEL_VERSION,
            curveVersion = RepetitionCurveRegistry.CURVE_VERSION,
            factorSchemaVersion = StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION,
            evidenceFingerprint = "evidence-fingerprint-1",
            revisionKey = revision.revisionKey
        )
        val history = StrengthPosteriorHistoryEntity(
            eventUuid = event.eventUuid,
            targetKey = "strength.future_target",
            sessionDate = event.sessionDate,
            priorMedian = 80.125,
            priorLow50 = 75.0,
            priorHigh50 = 85.0,
            priorLow80 = 70.0,
            priorHigh80 = 90.0,
            priorLow95 = 65.0,
            priorHigh95 = 95.0,
            posteriorMedian = 82.25,
            posteriorLow50 = 78.0,
            posteriorHigh50 = 87.0,
            posteriorLow80 = 73.0,
            posteriorHigh80 = 92.0,
            posteriorLow95 = 68.0,
            posteriorHigh95 = 97.0,
            directObservedLoad = null,
            directObservationType = "NONE",
            sessionObservationMedian = 83.0,
            sessionObservationLow80 = 75.0,
            sessionObservationHigh80 = 91.0,
            posteriorMeanChange = 2.125,
            posteriorVarianceBefore = 0.05,
            posteriorVarianceAfter = 0.04,
            intervalWidthChange80 = -3.0,
            predictivePercentile = 0.61,
            standardizedSurprise = 0.27,
            modelVersion = event.modelVersion,
            factorSchemaVersion = event.factorSchemaVersion,
            curveVersion = event.curveVersion,
            targetConfigVersion = "future-target-1.0",
            evidenceFingerprint = checkNotNull(event.evidenceFingerprint),
            sourceEvidenceStatus = "SOURCE_DELETED",
            sourceSetCountAtProcessing = 2,
            bodyWeightKgAtProcessing = null,
            rawAddedWeightKgAtProcessing = null,
            bodyWeightSource = null,
            curveProfileId = "reps_curve.general_resistance.v1",
            curveMatchLevel = "GENERAL_FALLBACK",
            curveCalibrationStatus = "CANONICAL_ONLY",
            createdAt = 200L
        )
        val state = StrengthPosteriorModel.toEntity(
            state = StrengthPosteriorState(
                orderedFactorSchema = listOf(StrengthFactorKey("strength.factor.target.future_target")),
                mean = doubleArrayOf(4.4),
                covariance = arrayOf(doubleArrayOf(0.04)),
                lastProcessedEventUuid = event.eventUuid,
                lastProcessedDate = LocalDate.parse(event.sessionDate)
            ),
            now = 200L,
            modelInstanceKey = StrengthModelRevisionPolicy.modelInstanceKey(revision.revisionKey)
        )
        val curve = StrengthCurvePosteriorEntity(
            curveSubjectKey = StrengthModelRevisionPolicy.curveSubjectKey(
                revision.revisionKey,
                "exercise:future_lift"
            ),
            canonicalProfileId = "reps_curve.general_resistance.v1",
            thetaGridEncoded = VersionedDoubleArrayCodec.encode(doubleArrayOf(-0.1, 0.0, 0.1)),
            posteriorWeightsEncoded = VersionedDoubleArrayCodec.encode(doubleArrayOf(0.2, 0.6, 0.2)),
            totalObservationCount = 2,
            strongObservationCount = 2,
            distinctRepRangeCount = 2,
            minObservedReps = 3,
            maxObservedReps = 8,
            calibrationStatus = "PERSONALIZED",
            curveVersion = event.curveVersion,
            posteriorFingerprint = "curve-fingerprint-1",
            updatedAt = 200L
        )
        val evidence = StrengthPosteriorEvidenceEntity(
            evidenceFingerprint = checkNotNull(event.evidenceFingerprint),
            eventUuid = event.eventUuid,
            sessionKey = event.sessionKey,
            sessionDate = event.sessionDate,
            exerciseStableKey = "future_lift",
            exerciseNameAtProcessing = "Future lift",
            directTargetKey = null,
            observationType = "STRONG_NRM",
            capacityMedianKg = 83.0,
            capacityLow80Kg = 75.0,
            capacityHigh80Kg = 91.0,
            lowerBoundOnly = 0,
            logVariance = 0.03,
            directObservedLoadKg = null,
            bodyWeightKg = null,
            rawAddedWeightKg = null,
            bodyWeightSource = "NOT_APPLICABLE",
            curveProfileId = curve.canonicalProfileId,
            curveMatchLevel = "GENERAL_FALLBACK",
            curveVarianceMultiplier = 1.2,
            curveSubjectKey = curve.curveSubjectKey,
            sourceSetIdsEncoded = "1|2",
            strongObservationCount = 2,
            diagnosticsEncoded = "",
            createdAt = 200L
        )
        val localState = StrengthExerciseLocalState(
            exerciseStableKey = evidence.exerciseStableKey,
            logMean = 4.4,
            logVariance = 0.04,
            lastProcessedEventUuid = event.eventUuid,
            lastProcessedSessionKey = event.sessionKey,
            lastProcessedDate = LocalDate.parse(event.sessionDate),
            baselineEstablished = true,
            observationCount = 2,
            twoSidedObservationCount = 2
        ).toEntity(revision.revisionKey, now = 200L)
        val localHistory = StrengthExercisePerformanceHistoryEntity(
            revisionKey = revision.revisionKey,
            eventUuid = event.eventUuid,
            sessionKey = event.sessionKey,
            sessionDate = event.sessionDate,
            exerciseStableKey = evidence.exerciseStableKey,
            priorLogMean = 4.3,
            priorLogVariance = 0.05,
            sessionLikelihoodLogMean = 4.45,
            sessionLikelihoodLogVariance = 0.03,
            sessionLikelihoodProper = true,
            innovationResidualLog = 0.15,
            innovationVariance = 0.08,
            posteriorLogMean = 4.4,
            posteriorLogVariance = 0.04,
            posteriorMeanIncrementLog = 0.1,
            transitionDays = 7,
            baselineEstablishedBefore = true,
            baselineEstablishedAfter = true,
            proxyTransferEligible = true,
            proxyTransferApplied = true,
            modelVersion = event.modelVersion,
            curveVersion = event.curveVersion,
            rirPolicyVersion = localState.rirPolicyVersion,
            evidenceFingerprint = evidence.evidenceFingerprint,
            createdAt = 200L
        )
        val proxyHistory = StrengthProxyTransferHistoryEntity(
            revisionKey = revision.revisionKey,
            eventUuid = event.eventUuid,
            sessionDate = event.sessionDate,
            exerciseStableKey = evidence.exerciseStableKey,
            targetKey = history.targetKey,
            innovationResidualLog = 0.15,
            innovationVariance = 0.08,
            transferCoefficient = 0.5,
            transferLogVariance = 0.32,
            orderedSharedFactorKeys = "strength.factor.shared.horizontal_press",
            sharedLoadingVectorEncoded = VersionedDoubleArrayCodec.encode(doubleArrayOf(0.5)),
            targetSpecificContribution = 0.0,
            applied = true,
            exclusionReason = null,
            proxyRegistryVersion = StrengthPerformanceRegistry.PROXY_CONFIG_VERSION,
            modelVersion = event.modelVersion,
            transferFingerprint = "proxy-transfer-fingerprint-1",
            createdAt = 200L
        )
        return BackupFixture(
            marker = "completed|INITIAL_INSTALLATION_BOOTSTRAP|200|${event.modelVersion}",
            supersededRevision = supersededRevision,
            revision = revision,
            event = event,
            history = history,
            state = state,
            curve = curve,
            evidence = evidence,
            localState = localState,
            localHistory = localHistory,
            proxyHistory = proxyHistory
        )
    }

    private fun BackupFixture.csv(): String = RecordCsvBackupRestore.buildRestoreCsv(
        entriesWithSets = emptyList(),
        metrics = emptyList(),
        posteriorBootstrapMarker = marker,
        posteriorEvents = listOf(event),
        posteriorHistory = listOf(history),
        posteriorModelStates = listOf(state),
        curvePosteriors = listOf(curve),
        posteriorEvidence = listOf(evidence),
        posteriorRevisions = listOf(supersededRevision, revision),
        posteriorLocalStates = listOf(localState),
        posteriorLocalHistory = listOf(localHistory),
        posteriorProxyHistory = listOf(proxyHistory)
    )

    private fun legacyBackup(): String = """
        schema_version,row_type,date,entry_key,entry_order,exercise_name,category,confirmed,rest_seconds,rpe,max_reps,notes,set_index,set_confirmed,reps,weight_kg,seconds,sleep_hours,body_weight_kg,stable_key
        1,exercise,,,,Bench press,Strength,,60,,,,,,,,,,,barbell_bench_press
        1,set,2026-07-01,entry-1,1,Bench press,Strength,1,60,10,,,1,1,1,100,0,,,barbell_bench_press
    """.trimIndent()

    private data class BackupFixture(
        val marker: String,
        val supersededRevision: StrengthModelRevisionEntity,
        val revision: StrengthModelRevisionEntity,
        val event: StrengthPosteriorEventEntity,
        val history: StrengthPosteriorHistoryEntity,
        val state: StrengthPosteriorModelStateEntity,
        val curve: StrengthCurvePosteriorEntity,
        val evidence: StrengthPosteriorEvidenceEntity,
        val localState: StrengthExercisePerformanceStateEntity,
        val localHistory: StrengthExercisePerformanceHistoryEntity,
        val proxyHistory: StrengthProxyTransferHistoryEntity
    )
}

package com.training.trackplanner.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.VersionedDoubleArrayCodec
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import java.io.File
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

        assertEquals(5, parsed.backupSchemaVersion)
        assertTrue(parsed.posteriorFormatPresent)
        assertEquals(fixture.marker, parsed.posteriorBootstrapMarker)
        assertEquals(listOf(fixture.event), parsed.posteriorEvents)
        assertEquals(listOf(fixture.history), parsed.posteriorHistory)
        assertEquals(listOf(fixture.state), parsed.posteriorModelStates)
        assertEquals(listOf(fixture.curve), parsed.curvePosteriors)
        assertEquals(listOf(fixture.evidence), parsed.posteriorEvidence)
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
        assertEquals(fixture.event, db.strengthPosteriorDao().allEvents().single())
        assertEquals(fixture.history, db.strengthPosteriorDao().allHistory().single())
        assertEquals(fixture.state, db.strengthPosteriorDao().allModelStates().single())
        assertEquals(fixture.curve, db.strengthPosteriorDao().allCurvePosteriors().single())
        assertEquals(fixture.evidence, db.strengthPosteriorDao().allEvidence().single())
        assertEquals(fixture.marker, db.appMetaDao().value(StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY))

        val duplicate = repository.importRecordsBackup(uri)
        assertEquals(0, duplicate.posteriorEventCount)
        assertEquals(5, duplicate.skippedDuplicateCount)
        assertEquals(1, db.strengthPosteriorDao().allEvents().size)
        assertEquals(1, db.strengthPosteriorDao().allHistory().size)
    }

    @Test
    fun `repository export and import preserve the complete posterior ledger`() = runBlocking {
        val fixture = fixture()
        val source = newDatabase()
        source.strengthPosteriorDao().insertEventStrict(fixture.event)
        source.strengthPosteriorDao().insertHistoryStrict(listOf(fixture.history))
        source.strengthPosteriorDao().upsertModelState(fixture.state)
        source.strengthPosteriorDao().upsertCurvePosterior(fixture.curve)
        source.strengthPosteriorDao().insertEvidenceStrict(listOf(fixture.evidence))
        source.appMetaDao().upsert(
            AppMeta(StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY, fixture.marker)
        )
        val backup = writeBackup("")
        TrainingRepository(source, context).exportRecordsBackup(backup)
        source.close()

        val target = newDatabase()
        TrainingRepository(target, context).importRecordsBackup(backup)

        assertEquals(listOf(fixture.event), target.strengthPosteriorDao().allEvents())
        assertEquals(listOf(fixture.history), target.strengthPosteriorDao().allHistory())
        assertEquals(listOf(fixture.state), target.strengthPosteriorDao().allModelStates())
        assertEquals(listOf(fixture.curve), target.strengthPosteriorDao().allCurvePosteriors())
        assertEquals(listOf(fixture.evidence), target.strengthPosteriorDao().allEvidence())
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
    fun `old backup imports and runs one legacy forward bootstrap`() = runBlocking {
        val db = newDatabase()
        val repository = TrainingRepository(db, context)

        repository.importRecordsBackup(writeBackup(legacyBackup()))

        val event = db.strengthPosteriorDao().allEvents().single()
        assertEquals(StrengthPosteriorUpdateCoordinator.REASON_LEGACY_BACKUP_BOOTSTRAP, event.creationReason)
        assertEquals(StrengthPosteriorEventProcessor.STATUS_PROCESSED, event.status)
        assertTrue(db.strengthPosteriorDao().historyForEvent(event.eventUuid).isNotEmpty())
        val marker = db.appMetaDao().value(StrengthPosteriorUpdateCoordinator.BOOTSTRAP_MARKER_KEY)
        assertNotNull(marker)
        assertTrue(checkNotNull(marker).contains(StrengthPosteriorUpdateCoordinator.REASON_LEGACY_BACKUP_BOOTSTRAP))
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
            evidenceFingerprint = "evidence-fingerprint-1"
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
        val state = StrengthPosteriorModelStateEntity(
            modelInstanceKey = StrengthPosteriorModel.MODEL_INSTANCE_KEY,
            orderedFactorSchema = "strength.factor.target.future_target",
            stateMeanEncoded = VersionedDoubleArrayCodec.encode(doubleArrayOf(4.4)),
            packedCovarianceEncoded = VersionedDoubleArrayCodec.encode(doubleArrayOf(0.04)),
            stateDimension = 1,
            lastProcessedEventUuid = event.eventUuid,
            lastProcessedDate = event.sessionDate,
            modelVersion = event.modelVersion,
            curveVersion = event.curveVersion,
            factorSchemaVersion = event.factorSchemaVersion,
            stateFingerprint = "state-fingerprint-1",
            updatedAt = 200L
        )
        val curve = StrengthCurvePosteriorEntity(
            curveSubjectKey = "exercise:future_lift",
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
        return BackupFixture(
            marker = "completed|INITIAL_INSTALLATION_BOOTSTRAP|200|${event.modelVersion}",
            event = event,
            history = history,
            state = state,
            curve = curve,
            evidence = evidence
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
        posteriorEvidence = listOf(evidence)
    )

    private fun legacyBackup(): String = """
        schema_version,row_type,date,entry_key,entry_order,exercise_name,category,confirmed,rest_seconds,rpe,max_reps,notes,set_index,set_confirmed,reps,weight_kg,seconds,sleep_hours,body_weight_kg,stable_key
        1,exercise,,,,Bench press,Strength,,60,,,,,,,,,,,barbell_bench_press
        1,set,2026-07-01,entry-1,1,Bench press,Strength,1,60,10,,,1,1,1,100,0,,,barbell_bench_press
    """.trimIndent()

    private data class BackupFixture(
        val marker: String,
        val event: StrengthPosteriorEventEntity,
        val history: StrengthPosteriorHistoryEntity,
        val state: StrengthPosteriorModelStateEntity,
        val curve: StrengthCurvePosteriorEntity,
        val evidence: StrengthPosteriorEvidenceEntity
    )
}

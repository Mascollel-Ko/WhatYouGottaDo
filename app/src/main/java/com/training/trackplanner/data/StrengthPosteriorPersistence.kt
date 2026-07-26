package com.training.trackplanner.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Entity(
    tableName = "strength_posterior_events",
    indices = [
        Index("sessionKey"),
        Index("sessionDate"),
        Index("status"),
        Index(value = ["completionFingerprint"], unique = true)
    ]
)
data class StrengthPosteriorEventEntity(
    @PrimaryKey val eventUuid: String,
    val sessionKey: String,
    val sessionDate: String,
    val completionFingerprint: String,
    val status: String,
    val creationReason: String,
    val confirmedSetCount: Int,
    val createdAt: Long,
    val processedAt: Long? = null,
    val modelVersion: String,
    val curveVersion: String,
    val factorSchemaVersion: String,
    val evidenceFingerprint: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val revisionKey: String = StrengthModelRevisionPolicy.LEGACY_REVISION_KEY
)

@Entity(
    tableName = "strength_posterior_history",
    primaryKeys = ["eventUuid", "targetKey"],
    indices = [Index("targetKey"), Index("eventUuid")]
)
data class StrengthPosteriorHistoryEntity(
    val eventUuid: String,
    val targetKey: String,
    val sessionDate: String,
    val priorMedian: Double?,
    val priorLow50: Double?,
    val priorHigh50: Double?,
    val priorLow80: Double?,
    val priorHigh80: Double?,
    val priorLow95: Double?,
    val priorHigh95: Double?,
    val posteriorMedian: Double?,
    val posteriorLow50: Double?,
    val posteriorHigh50: Double?,
    val posteriorLow80: Double?,
    val posteriorHigh80: Double?,
    val posteriorLow95: Double?,
    val posteriorHigh95: Double?,
    val directObservedLoad: Double?,
    val directObservationType: String,
    val sessionObservationMedian: Double?,
    val sessionObservationLow80: Double?,
    val sessionObservationHigh80: Double?,
    val posteriorMeanChange: Double?,
    val posteriorVarianceBefore: Double?,
    val posteriorVarianceAfter: Double?,
    val intervalWidthChange80: Double?,
    val predictivePercentile: Double?,
    val standardizedSurprise: Double?,
    val modelVersion: String,
    val factorSchemaVersion: String,
    val curveVersion: String,
    val targetConfigVersion: String,
    val evidenceFingerprint: String,
    val sourceEvidenceStatus: String,
    val sourceSetCountAtProcessing: Int,
    val bodyWeightKgAtProcessing: Double?,
    val rawAddedWeightKgAtProcessing: Double?,
    val bodyWeightSource: String?,
    val curveProfileId: String?,
    val curveMatchLevel: String?,
    val curveCalibrationStatus: String?,
    val createdAt: Long
)

@Entity(tableName = "strength_posterior_model_state")
data class StrengthPosteriorModelStateEntity(
    @PrimaryKey val modelInstanceKey: String,
    val orderedFactorSchema: String,
    val stateMeanEncoded: String,
    val packedCovarianceEncoded: String,
    val stateDimension: Int,
    val lastProcessedEventUuid: String?,
    val lastProcessedDate: String?,
    val modelVersion: String,
    val curveVersion: String,
    val factorSchemaVersion: String,
    val stateFingerprint: String,
    val updatedAt: Long
)

@Entity(tableName = "strength_curve_posteriors")
data class StrengthCurvePosteriorEntity(
    @PrimaryKey val curveSubjectKey: String,
    val canonicalProfileId: String,
    val thetaGridEncoded: String,
    val posteriorWeightsEncoded: String,
    val totalObservationCount: Int,
    val strongObservationCount: Int,
    val distinctRepRangeCount: Int,
    val minObservedReps: Int?,
    val maxObservedReps: Int?,
    val calibrationStatus: String,
    val curveVersion: String,
    val posteriorFingerprint: String,
    val updatedAt: Long
)

@Entity(
    tableName = "strength_posterior_evidence",
    indices = [Index("eventUuid"), Index("exerciseStableKey")]
)
data class StrengthPosteriorEvidenceEntity(
    @PrimaryKey val evidenceFingerprint: String,
    val eventUuid: String,
    val sessionKey: String,
    val sessionDate: String,
    val exerciseStableKey: String,
    val exerciseNameAtProcessing: String,
    val directTargetKey: String?,
    val observationType: String,
    val capacityMedianKg: Double,
    val capacityLow80Kg: Double,
    val capacityHigh80Kg: Double,
    val lowerBoundOnly: Int,
    val logVariance: Double,
    val directObservedLoadKg: Double?,
    val bodyWeightKg: Double?,
    val rawAddedWeightKg: Double?,
    val bodyWeightSource: String,
    val curveProfileId: String,
    val curveMatchLevel: String,
    val curveVarianceMultiplier: Double,
    val curveSubjectKey: String,
    val sourceSetIdsEncoded: String,
    val strongObservationCount: Int,
    val diagnosticsEncoded: String,
    val createdAt: Long
)

@Dao
abstract class StrengthPosteriorDao {
    @Query("SELECT * FROM strength_model_revisions WHERE status = 'ACTIVE' ORDER BY rebuildCompletedAt DESC LIMIT 1")
    abstract suspend fun activeRevision(): StrengthModelRevisionEntity?

    @Query("SELECT * FROM strength_model_revisions WHERE revisionKey = :revisionKey LIMIT 1")
    abstract suspend fun revision(revisionKey: String): StrengthModelRevisionEntity?

    @Query("SELECT * FROM strength_model_revisions ORDER BY createdAt, revisionKey")
    abstract suspend fun allRevisions(): List<StrengthModelRevisionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRevisionStrict(revision: StrengthModelRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRevisionIfAbsent(revision: StrengthModelRevisionEntity): Long

    @Query("UPDATE strength_model_revisions SET status = :status, rebuildCompletedAt = :completedAt, errorCode = :errorCode, errorMessage = :errorMessage WHERE revisionKey = :revisionKey")
    abstract suspend fun updateRevisionStatus(
        revisionKey: String,
        status: String,
        completedAt: Long?,
        errorCode: String?,
        errorMessage: String?
    )

    @Query("UPDATE strength_model_revisions SET status = 'SUPERSEDED' WHERE status = 'ACTIVE' AND revisionKey != :activeRevisionKey")
    abstract suspend fun supersedeOtherRevisions(activeRevisionKey: String)

    @Query("SELECT * FROM strength_posterior_events WHERE eventUuid = :eventUuid LIMIT 1")
    abstract suspend fun eventByUuid(eventUuid: String): StrengthPosteriorEventEntity?

    @Query("SELECT * FROM strength_posterior_events WHERE completionFingerprint = :fingerprint LIMIT 1")
    abstract suspend fun eventByCompletionFingerprint(fingerprint: String): StrengthPosteriorEventEntity?

    @Query("SELECT * FROM strength_posterior_events WHERE sessionKey = :sessionKey ORDER BY createdAt LIMIT 1")
    abstract suspend fun eventBySessionKey(sessionKey: String): StrengthPosteriorEventEntity?

    @Query("SELECT * FROM strength_posterior_events WHERE sessionKey = :sessionKey AND revisionKey = :revisionKey ORDER BY createdAt LIMIT 1")
    abstract suspend fun eventBySessionKeyAndRevision(sessionKey: String, revisionKey: String): StrengthPosteriorEventEntity?

    @Query("SELECT * FROM strength_posterior_events ORDER BY sessionDate, createdAt, eventUuid")
    abstract suspend fun allEvents(): List<StrengthPosteriorEventEntity>

    @Query("SELECT * FROM strength_posterior_events WHERE revisionKey = :revisionKey ORDER BY sessionDate, createdAt, eventUuid")
    abstract suspend fun eventsForRevision(revisionKey: String): List<StrengthPosteriorEventEntity>

    @Query("SELECT * FROM strength_posterior_events WHERE status IN ('PENDING', 'FAILED') ORDER BY sessionDate, createdAt, eventUuid")
    abstract suspend fun retryableEvents(): List<StrengthPosteriorEventEntity>

    @Query("SELECT * FROM strength_posterior_events WHERE revisionKey = :revisionKey AND status IN ('PENDING', 'FAILED') ORDER BY sessionDate, createdAt, eventUuid")
    abstract suspend fun retryableEventsForRevision(revisionKey: String): List<StrengthPosteriorEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPendingEvent(event: StrengthPosteriorEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertEventStrict(event: StrengthPosteriorEventEntity)

    @Query(
        """
        UPDATE strength_posterior_events
        SET status = :status, processedAt = :processedAt, evidenceFingerprint = :evidenceFingerprint,
            errorCode = :errorCode, errorMessage = :errorMessage
        WHERE eventUuid = :eventUuid
        """
    )
    abstract suspend fun updateEventStatus(
        eventUuid: String,
        status: String,
        processedAt: Long?,
        evidenceFingerprint: String?,
        errorCode: String?,
        errorMessage: String?
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertHistoryStrict(history: List<StrengthPosteriorHistoryEntity>)

    @Query("SELECT * FROM strength_posterior_history WHERE eventUuid = :eventUuid ORDER BY targetKey")
    abstract suspend fun historyForEvent(eventUuid: String): List<StrengthPosteriorHistoryEntity>

    @Query("SELECT * FROM strength_posterior_history WHERE targetKey = :targetKey ORDER BY sessionDate, createdAt, eventUuid")
    abstract suspend fun historyForTarget(targetKey: String): List<StrengthPosteriorHistoryEntity>

    @Query("SELECT * FROM strength_posterior_history ORDER BY sessionDate, createdAt, eventUuid, targetKey")
    abstract suspend fun allHistory(): List<StrengthPosteriorHistoryEntity>

    @Query("SELECT h.* FROM strength_posterior_history h INNER JOIN strength_posterior_events e ON e.eventUuid = h.eventUuid WHERE e.revisionKey = :revisionKey ORDER BY h.sessionDate, h.createdAt, h.eventUuid, h.targetKey")
    abstract suspend fun historyForRevision(revisionKey: String): List<StrengthPosteriorHistoryEntity>

    @Query("UPDATE strength_posterior_history SET sourceEvidenceStatus = :status WHERE eventUuid = :eventUuid")
    abstract suspend fun updateSourceEvidenceStatus(eventUuid: String, status: String)

    @Query("SELECT * FROM strength_posterior_model_state WHERE modelInstanceKey = :key LIMIT 1")
    abstract suspend fun modelState(key: String): StrengthPosteriorModelStateEntity?

    @Query("SELECT * FROM strength_posterior_model_state ORDER BY modelInstanceKey")
    abstract suspend fun allModelStates(): List<StrengthPosteriorModelStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertModelState(state: StrengthPosteriorModelStateEntity)

    @Query("SELECT * FROM strength_curve_posteriors WHERE curveSubjectKey = :key LIMIT 1")
    abstract suspend fun curvePosterior(key: String): StrengthCurvePosteriorEntity?

    @Query("SELECT * FROM strength_curve_posteriors ORDER BY curveSubjectKey")
    abstract suspend fun allCurvePosteriors(): List<StrengthCurvePosteriorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCurvePosterior(posterior: StrengthCurvePosteriorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCurvePosteriors(posteriors: List<StrengthCurvePosteriorEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertEvidenceStrict(evidence: List<StrengthPosteriorEvidenceEntity>)

    @Query("SELECT * FROM strength_posterior_evidence WHERE eventUuid = :eventUuid ORDER BY exerciseStableKey, evidenceFingerprint")
    abstract suspend fun evidenceForEvent(eventUuid: String): List<StrengthPosteriorEvidenceEntity>

    @Query("SELECT * FROM strength_posterior_evidence WHERE evidenceFingerprint = :fingerprint LIMIT 1")
    abstract suspend fun evidenceByFingerprint(fingerprint: String): StrengthPosteriorEvidenceEntity?

    @Query("SELECT * FROM strength_posterior_evidence ORDER BY sessionDate, exerciseStableKey, evidenceFingerprint")
    abstract suspend fun allEvidence(): List<StrengthPosteriorEvidenceEntity>

    @Query("SELECT x.* FROM strength_posterior_evidence x INNER JOIN strength_posterior_events e ON e.eventUuid = x.eventUuid WHERE e.revisionKey = :revisionKey ORDER BY x.sessionDate, x.exerciseStableKey, x.evidenceFingerprint")
    abstract suspend fun evidenceForRevision(revisionKey: String): List<StrengthPosteriorEvidenceEntity>

    @Query("SELECT * FROM strength_exercise_performance_state WHERE revisionKey = :revisionKey ORDER BY exerciseStableKey")
    abstract suspend fun localStates(revisionKey: String): List<StrengthExercisePerformanceStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertLocalStates(states: List<StrengthExercisePerformanceStateEntity>)

    @Query("SELECT * FROM strength_exercise_performance_history WHERE revisionKey = :revisionKey ORDER BY sessionDate, eventUuid, exerciseStableKey")
    abstract suspend fun localHistory(revisionKey: String): List<StrengthExercisePerformanceHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertLocalHistoryStrict(history: List<StrengthExercisePerformanceHistoryEntity>)

    @Query("SELECT * FROM strength_proxy_transfer_history WHERE revisionKey = :revisionKey ORDER BY sessionDate, eventUuid, exerciseStableKey, targetKey")
    abstract suspend fun proxyHistory(revisionKey: String): List<StrengthProxyTransferHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertProxyHistoryStrict(history: List<StrengthProxyTransferHistoryEntity>)

    @Transaction
    open suspend fun commitProcessedEvent(
        eventUuid: String,
        histories: List<StrengthPosteriorHistoryEntity>,
        evidence: List<StrengthPosteriorEvidenceEntity>,
        modelState: StrengthPosteriorModelStateEntity,
        curvePosteriors: List<StrengthCurvePosteriorEntity>,
        localStates: List<StrengthExercisePerformanceStateEntity> = emptyList(),
        localHistory: List<StrengthExercisePerformanceHistoryEntity> = emptyList(),
        proxyHistory: List<StrengthProxyTransferHistoryEntity> = emptyList(),
        evidenceFingerprint: String,
        processedAt: Long
    ) {
        val event = checkNotNull(eventByUuid(eventUuid))
        require(event.status == "PENDING" || event.status == "FAILED")
        require(historyForEvent(eventUuid).isEmpty()) { "Immutable posterior history already exists for $eventUuid" }
        insertHistoryStrict(histories)
        insertEvidenceStrict(evidence)
        upsertModelState(modelState)
        upsertCurvePosteriors(curvePosteriors)
        upsertLocalStates(localStates)
        insertLocalHistoryStrict(localHistory)
        insertProxyHistoryStrict(proxyHistory)
        updateEventStatus(eventUuid, "PROCESSED", processedAt, evidenceFingerprint, null, null)
    }
}

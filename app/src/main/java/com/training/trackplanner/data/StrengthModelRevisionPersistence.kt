package com.training.trackplanner.data

import androidx.room.Entity
import androidx.room.Index
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.StrengthExerciseLocalHistory
import com.training.trackplanner.analysis.strengthperformance.StrengthExerciseLocalState
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.StrengthProxyTransferRecord
import com.training.trackplanner.analysis.strengthperformance.VersionedDoubleArrayCodec
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.analysis.strengthperformance.fingerprint
import java.time.LocalDate

@Entity(tableName = "strength_model_revisions")
data class StrengthModelRevisionEntity(
    @androidx.room.PrimaryKey val revisionKey: String,
    val modelVersion: String,
    val factorSchemaVersion: String,
    val targetRegistryVersion: String,
    val proxyRegistryVersion: String,
    val rirPolicyVersion: String,
    val curveVersion: String,
    val status: String,
    val creationReason: String,
    val sourceRevisionKey: String?,
    val createdAt: Long,
    val rebuildStartedAt: Long?,
    val rebuildCompletedAt: Long?,
    val revisionFingerprint: String,
    val errorCode: String?,
    val errorMessage: String?
)

@Entity(
    tableName = "strength_exercise_performance_state",
    primaryKeys = ["revisionKey", "exerciseStableKey"],
    indices = [Index("revisionKey"), Index("exerciseStableKey")]
)
data class StrengthExercisePerformanceStateEntity(
    val revisionKey: String,
    val exerciseStableKey: String,
    val priorSource: String,
    val stateLogMean: Double,
    val stateLogVariance: Double,
    val lastProcessedEventUuid: String,
    val lastProcessedSessionKey: String,
    val lastProcessedDate: String,
    val baselineEstablished: Boolean,
    val observationCount: Int,
    val twoSidedObservationCount: Int,
    val modelVersion: String,
    val curveVersion: String,
    val rirPolicyVersion: String,
    val stateFingerprint: String,
    val updatedAt: Long
)

@Entity(
    tableName = "strength_exercise_performance_history",
    primaryKeys = ["revisionKey", "eventUuid", "exerciseStableKey"],
    indices = [Index("revisionKey"), Index("eventUuid"), Index("exerciseStableKey")]
)
data class StrengthExercisePerformanceHistoryEntity(
    val revisionKey: String,
    val eventUuid: String,
    val sessionKey: String,
    val sessionDate: String,
    val exerciseStableKey: String,
    val priorLogMean: Double,
    val priorLogVariance: Double,
    val sessionLikelihoodLogMean: Double?,
    val sessionLikelihoodLogVariance: Double?,
    val sessionLikelihoodProper: Boolean,
    val innovationResidualLog: Double?,
    val innovationVariance: Double?,
    val posteriorLogMean: Double,
    val posteriorLogVariance: Double,
    val posteriorMeanIncrementLog: Double,
    val transitionDays: Long,
    val baselineEstablishedBefore: Boolean,
    val baselineEstablishedAfter: Boolean,
    val proxyTransferEligible: Boolean,
    val proxyTransferApplied: Boolean,
    val modelVersion: String,
    val curveVersion: String,
    val rirPolicyVersion: String,
    val evidenceFingerprint: String,
    val createdAt: Long
)

@Entity(
    tableName = "strength_proxy_transfer_history",
    primaryKeys = ["revisionKey", "eventUuid", "exerciseStableKey", "targetKey"],
    indices = [Index("revisionKey"), Index("eventUuid"), Index("exerciseStableKey"), Index("targetKey")]
)
data class StrengthProxyTransferHistoryEntity(
    val revisionKey: String,
    val eventUuid: String,
    val sessionDate: String,
    val exerciseStableKey: String,
    val targetKey: String,
    val innovationResidualLog: Double,
    val innovationVariance: Double,
    val transferCoefficient: Double,
    val transferLogVariance: Double,
    val orderedSharedFactorKeys: String,
    val sharedLoadingVectorEncoded: String,
    val targetSpecificContribution: Double,
    val applied: Boolean,
    val exclusionReason: String?,
    val proxyRegistryVersion: String,
    val modelVersion: String,
    val transferFingerprint: String,
    val createdAt: Long
)

enum class StrengthAnalysisLifecycleStatus {
    CURRENT,
    REBUILDING,
    REBUILD_FAILED
}

data class StrengthAnalysisLifecycleResult(
    val status: StrengthAnalysisLifecycleStatus,
    val diagnosticCode: String? = null,
    val diagnosticMessage: String? = null
)

object StrengthModelRevisionPolicy {
    const val CURRENT_REVISION_KEY = "strength-revision-3.0.0"
    const val LEGACY_REVISION_KEY = "strength-revision-2.1.0"
    const val DERIVED_STATE_VERSION = "strength-derived-state-0.5.0.6"
    const val CORRECTION_REASON = "EXERCISE_STABLE_KEY_CANONICALIZATION_0_5_0_6"
    const val REBUILD_MARKER_KEY = "strength_derived_reset_rebuild_0_5_0_6_complete"
    const val OBSOLETE_REBUILD_MARKER_KEY = "strength_model_correction_rebuild_0_5_0_3"
    const val STATUS_BUILDING = "BUILDING"
    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_SUPERSEDED = "SUPERSEDED"
    const val STATUS_FAILED = "FAILED"

    fun current(now: Long, sourceRevisionKey: String?): StrengthModelRevisionEntity =
        StrengthModelRevisionEntity(
            revisionKey = CURRENT_REVISION_KEY,
            modelVersion = StrengthPosteriorModel.MODEL_VERSION,
            factorSchemaVersion = StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION,
            targetRegistryVersion = StrengthPerformanceRegistry.TARGET_CONFIG_VERSION,
            proxyRegistryVersion = StrengthPerformanceRegistry.PROXY_CONFIG_VERSION,
            rirPolicyVersion = RpeRirPolicy.POLICY_VERSION,
            curveVersion = RepetitionCurveRegistry.CURVE_VERSION,
            status = STATUS_BUILDING,
            creationReason = CORRECTION_REASON,
            sourceRevisionKey = sourceRevisionKey,
            createdAt = now,
            rebuildStartedAt = now,
            rebuildCompletedAt = null,
            revisionFingerprint = fingerprint(
                CURRENT_REVISION_KEY,
                StrengthPosteriorModel.MODEL_VERSION,
                StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION,
                StrengthPerformanceRegistry.TARGET_CONFIG_VERSION,
                StrengthPerformanceRegistry.PROXY_CONFIG_VERSION,
                RpeRirPolicy.POLICY_VERSION,
                RepetitionCurveRegistry.CURVE_VERSION,
                DERIVED_STATE_VERSION
            ),
            errorCode = null,
            errorMessage = null
        )

    fun legacy(now: Long): StrengthModelRevisionEntity = StrengthModelRevisionEntity(
        revisionKey = LEGACY_REVISION_KEY,
        modelVersion = "strength-performance-model-2.1.0",
        factorSchemaVersion = StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION,
        targetRegistryVersion = "strength-target-registry-1.0.0",
        proxyRegistryVersion = "strength-proxy-registry-1.1.0",
        rirPolicyVersion = "legacy-hard-rpe-gate",
        curveVersion = "repetition-curve-assets-1.0.0",
        status = STATUS_SUPERSEDED,
        creationReason = "ROOM_22_LEGACY_IMPORT",
        sourceRevisionKey = null,
        createdAt = now,
        rebuildStartedAt = null,
        rebuildCompletedAt = now,
        revisionFingerprint = fingerprint(LEGACY_REVISION_KEY, "strength-performance-model-2.1.0"),
        errorCode = null,
        errorMessage = null
    )

    fun modelInstanceKey(revisionKey: String): String = "strength-performance-current|$revisionKey"
    fun curveSubjectKey(revisionKey: String, subjectKey: String): String = "$revisionKey|$subjectKey"
    fun originalCurveSubjectKey(revisionKey: String, storedKey: String): String =
        storedKey.removePrefix("$revisionKey|")

    fun isCompatible(revision: StrengthModelRevisionEntity): Boolean =
        revision.revisionKey == CURRENT_REVISION_KEY &&
            revision.revisionFingerprint == current(now = 0L, sourceRevisionKey = null).revisionFingerprint
}

fun StrengthExerciseLocalState.toEntity(revisionKey: String, now: Long): StrengthExercisePerformanceStateEntity {
    val stateFingerprint = fingerprint(
        revisionKey,
        exerciseStableKey,
        logMean.toBits().toString(),
        logVariance.toBits().toString(),
        lastProcessedEventUuid,
        lastProcessedSessionKey,
        lastProcessedDate.toString(),
        observationCount.toString(),
        twoSidedObservationCount.toString(),
        StrengthPosteriorModel.MODEL_VERSION,
        RepetitionCurveRegistry.CURVE_VERSION,
        RpeRirPolicy.POLICY_VERSION
    )
    return StrengthExercisePerformanceStateEntity(
        revisionKey = revisionKey,
        exerciseStableKey = exerciseStableKey,
        priorSource = "FIRST_PROPER_SESSION",
        stateLogMean = logMean,
        stateLogVariance = logVariance,
        lastProcessedEventUuid = lastProcessedEventUuid,
        lastProcessedSessionKey = lastProcessedSessionKey,
        lastProcessedDate = lastProcessedDate.toString(),
        baselineEstablished = baselineEstablished,
        observationCount = observationCount,
        twoSidedObservationCount = twoSidedObservationCount,
        modelVersion = StrengthPosteriorModel.MODEL_VERSION,
        curveVersion = RepetitionCurveRegistry.CURVE_VERSION,
        rirPolicyVersion = RpeRirPolicy.POLICY_VERSION,
        stateFingerprint = stateFingerprint,
        updatedAt = now
    )
}

fun StrengthExercisePerformanceStateEntity.toLocalState(): StrengthExerciseLocalState =
    StrengthExerciseLocalState(
        exerciseStableKey = exerciseStableKey,
        logMean = stateLogMean,
        logVariance = stateLogVariance,
        lastProcessedEventUuid = lastProcessedEventUuid,
        lastProcessedSessionKey = lastProcessedSessionKey,
        lastProcessedDate = LocalDate.parse(lastProcessedDate),
        baselineEstablished = baselineEstablished,
        observationCount = observationCount,
        twoSidedObservationCount = twoSidedObservationCount
    ).also { state ->
        require(state.toEntity(revisionKey, updatedAt).stateFingerprint == stateFingerprint)
    }

fun StrengthExerciseLocalHistory.toEntity(
    revisionKey: String,
    proxyTransferApplied: Boolean,
    evidenceFingerprint: String,
    now: Long
): StrengthExercisePerformanceHistoryEntity = StrengthExercisePerformanceHistoryEntity(
    revisionKey = revisionKey,
    eventUuid = eventUuid,
    sessionKey = sessionKey,
    sessionDate = sessionDate.toString(),
    exerciseStableKey = exerciseStableKey,
    priorLogMean = priorLogMean,
    priorLogVariance = priorLogVariance,
    sessionLikelihoodLogMean = sessionLikelihoodLogMean,
    sessionLikelihoodLogVariance = sessionLikelihoodLogVariance,
    sessionLikelihoodProper = sessionLikelihoodProper,
    innovationResidualLog = innovationResidualLog,
    innovationVariance = innovationVariance,
    posteriorLogMean = posteriorLogMean,
    posteriorLogVariance = posteriorLogVariance,
    posteriorMeanIncrementLog = posteriorMeanIncrementLog,
    transitionDays = transitionDays,
    baselineEstablishedBefore = baselineEstablishedBefore,
    baselineEstablishedAfter = baselineEstablishedAfter,
    proxyTransferEligible = proxyTransferEligible,
    proxyTransferApplied = proxyTransferApplied,
    modelVersion = StrengthPosteriorModel.MODEL_VERSION,
    curveVersion = RepetitionCurveRegistry.CURVE_VERSION,
    rirPolicyVersion = RpeRirPolicy.POLICY_VERSION,
    evidenceFingerprint = evidenceFingerprint,
    createdAt = now
)

fun StrengthProxyTransferRecord.toEntity(
    revisionKey: String,
    sessionDate: LocalDate,
    schema: List<com.training.trackplanner.analysis.strengthperformance.StrengthFactorKey>,
    now: Long
): StrengthProxyTransferHistoryEntity {
    val sharedKeys = factorLoadings.keys.sortedBy { it.value }
    val loadingVector = DoubleArray(sharedKeys.size) { index ->
        factorLoadings.getValue(sharedKeys[index]) * transferCoefficient
    }
    require(schema.none { it.value.startsWith("strength.factor.target.") && it in factorLoadings })
    return StrengthProxyTransferHistoryEntity(
        revisionKey = revisionKey,
        eventUuid = eventUuid,
        sessionDate = sessionDate.toString(),
        exerciseStableKey = exerciseStableKey,
        targetKey = targetKey.value,
        innovationResidualLog = innovationResidualLog,
        innovationVariance = innovationVariance,
        transferCoefficient = transferCoefficient,
        transferLogVariance = transferLogVariance,
        orderedSharedFactorKeys = sharedKeys.joinToString("|") { it.value },
        sharedLoadingVectorEncoded = VersionedDoubleArrayCodec.encode(loadingVector),
        targetSpecificContribution = 0.0,
        applied = true,
        exclusionReason = null,
        proxyRegistryVersion = StrengthPerformanceRegistry.PROXY_CONFIG_VERSION,
        modelVersion = StrengthPosteriorModel.MODEL_VERSION,
        transferFingerprint = evidenceFingerprint,
        createdAt = now
    )
}

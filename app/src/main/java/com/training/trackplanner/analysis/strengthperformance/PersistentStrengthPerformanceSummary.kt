package com.training.trackplanner.analysis.strengthperformance

import com.training.trackplanner.data.StrengthCurvePosteriorEntity
import com.training.trackplanner.data.StrengthPosteriorEventEntity
import com.training.trackplanner.data.StrengthPosteriorEvidenceEntity
import com.training.trackplanner.data.StrengthPosteriorHistoryEntity
import com.training.trackplanner.data.StrengthPosteriorModelStateEntity
import com.training.trackplanner.data.StrengthModelRevisionEntity
import java.time.LocalDate

data class PersistentStrengthPerformanceSummary(
    val targets: List<PersistentStrengthTargetSummary>,
    val eventCount: Int,
    val pendingEventCount: Int,
    val failedEventCount: Int,
    val latestEventFingerprint: String?,
    val modelStateFingerprint: String?,
    val modelVersionBoundaries: List<String>,
    val curveVersionBoundaries: List<String>,
    val factorSchemaVersion: String?,
    val bootstrapProvenance: String?,
    val backupRestorationProvenance: String?,
    val numericalDiagnostics: List<String>,
    val activeRevisionKey: String? = null,
    val activeRevisionStatus: String? = null,
    val activeRevisionReason: String? = null,
    val rirPolicyVersion: String? = null,
    val localExerciseStateCount: Int = 0,
    val proxyTransferCount: Int = 0,
    val supersededRevisionCount: Int = 0
)

data class PersistentStrengthTargetSummary(
    val targetKey: String,
    val displayNameKo: String,
    val loadSemantics: StrengthLoadSemantics,
    val currentMedianKg: Double?,
    val currentLow80Kg: Double?,
    val currentHigh80Kg: Double?,
    val currentBodyWeightKg: Double?,
    val currentAddedWeightKg: Double?,
    val latestDirectObservationKg: Double?,
    val latestDirectObservationDate: LocalDate?,
    val relevantSessionCount: Int,
    val directObservationCount: Int,
    val strongNrmObservationCount: Int,
    val proxyObservationCount: Int,
    val failureObservationCount: Int,
    val curveProfileId: String?,
    val curveMatchLevel: String?,
    val curveVarianceMultiplier: Double?,
    val curveCalibrationStatus: String?,
    val lastProcessedSessionDate: LocalDate?,
    val modelVersion: String?,
    val curveVersion: String?,
    val history: List<PersistentStrengthHistoryPoint>
)

data class PersistentStrengthHistoryPoint(
    val eventUuid: String,
    val sessionDate: LocalDate,
    val priorMedianKg: Double?,
    val priorLow80Kg: Double?,
    val priorHigh80Kg: Double?,
    val posteriorMedianKg: Double?,
    val posteriorLow80Kg: Double?,
    val posteriorHigh80Kg: Double?,
    val directObservedLoadKg: Double?,
    val directObservationType: String,
    val sessionObservationMedianKg: Double?,
    val sessionObservationLow80Kg: Double?,
    val sessionObservationHigh80Kg: Double?,
    val posteriorMeanChangeKg: Double?,
    val intervalWidthChange80Kg: Double?,
    val predictivePercentile: Double?,
    val strongObservationType: String,
    val curveProfileId: String?,
    val curveMatchLevel: String?,
    val curveCalibrationStatus: String?,
    val bodyWeightKgAtProcessing: Double?,
    val rawAddedWeightKgAtProcessing: Double?,
    val totalLoadKgAtProcessing: Double?,
    val bodyWeightSource: String?,
    val sourceEvidenceStatus: String,
    val sourceSetCountAtProcessing: Int,
    val evidenceFingerprint: String,
    val modelVersion: String,
    val curveVersion: String,
    val factorSchemaVersion: String
)

object PersistentStrengthPerformanceSummaryBuilder {
    fun build(
        registry: StrengthPerformanceRegistry,
        modelState: StrengthPosteriorModelStateEntity?,
        history: List<StrengthPosteriorHistoryEntity>,
        events: List<StrengthPosteriorEventEntity>,
        evidence: List<StrengthPosteriorEvidenceEntity>,
        curvePosteriors: List<StrengthCurvePosteriorEntity>,
        currentBodyWeightKg: Double?,
        bootstrapProvenance: String?,
        backupRestorationProvenance: String?,
        activeRevision: StrengthModelRevisionEntity? = null,
        localExerciseStateCount: Int = 0,
        proxyTransferCount: Int = 0,
        supersededRevisionCount: Int = 0
    ): PersistentStrengthPerformanceSummary {
        val diagnostics = mutableListOf<String>()
        val decodedState = modelState?.let { entity ->
            runCatching { StrengthPosteriorModel.fromEntity(entity) }
                .onFailure { diagnostics += "MODEL_STATE_UNREADABLE:${it::class.simpleName}" }
                .getOrNull()
        }
        val evidenceByFingerprint = evidence.associateBy(StrengthPosteriorEvidenceEntity::evidenceFingerprint)
        val curvesBySubject = curvePosteriors.associateBy(StrengthCurvePosteriorEntity::curveSubjectKey)
        val targets = registry.targets().map { target ->
            val rows = history.filter { row -> row.targetKey == target.targetKey.value }
                .sortedWith(compareBy(StrengthPosteriorHistoryEntity::sessionDate, StrengthPosteriorHistoryEntity::createdAt))
            val latest = rows.lastOrNull()
            val distribution = decodedState?.let { state ->
                runCatching { StrengthPosteriorModel.distribution(state, target) }
                    .onFailure { diagnostics += "TARGET_DISTRIBUTION_UNREADABLE:${target.targetKey.value}" }
                    .getOrNull()
            }
            val latestEvidence = latest?.let { row -> evidenceByFingerprint[row.evidenceFingerprint] }
            val curve = latestEvidence?.let { row -> curvesBySubject[row.curveSubjectKey] }
            val latestDirect = rows.lastOrNull { row ->
                row.directObservationType == StrengthObservationType.DIRECT_1RM.name && row.directObservedLoad != null
            }
            val evidenceForRows = rows.mapNotNull { row -> evidenceByFingerprint[row.evidenceFingerprint] }
            val median = distribution?.median ?: latest?.posteriorMedian
            val isWeightedPullUp = target.targetKey == StrengthPerformanceRegistry.WEIGHTED_PULL_UP
            PersistentStrengthTargetSummary(
                targetKey = target.targetKey.value,
                displayNameKo = target.displayNameKo,
                loadSemantics = target.loadSemantics,
                currentMedianKg = median,
                currentLow80Kg = distribution?.low80 ?: latest?.posteriorLow80,
                currentHigh80Kg = distribution?.high80 ?: latest?.posteriorHigh80,
                currentBodyWeightKg = currentBodyWeightKg.takeIf { isWeightedPullUp },
                currentAddedWeightKg = if (isWeightedPullUp && median != null && currentBodyWeightKg != null) {
                    median - currentBodyWeightKg
                } else null,
                latestDirectObservationKg = latestDirect?.directObservedLoad,
                latestDirectObservationDate = latestDirect?.sessionDate?.let(LocalDate::parse),
                relevantSessionCount = rows.map(StrengthPosteriorHistoryEntity::eventUuid).distinct().size,
                directObservationCount = rows.count { row -> row.directObservationType == StrengthObservationType.DIRECT_1RM.name },
                strongNrmObservationCount = evidenceForRows.count { row ->
                    row.observationType == StrengthObservationType.STRONG_NRM.name
                },
                proxyObservationCount = rows.count { row ->
                    evidenceByFingerprint[row.evidenceFingerprint]?.directTargetKey != target.targetKey.value
                },
                failureObservationCount = evidenceForRows.count { row ->
                    row.observationType == StrengthObservationType.FAILURE_UPPER_CENSORED.name ||
                        row.observationType == "FAILURE_UPPER_BOUND"
                },
                curveProfileId = latestEvidence?.curveProfileId ?: latest?.curveProfileId,
                curveMatchLevel = latestEvidence?.curveMatchLevel ?: latest?.curveMatchLevel,
                curveVarianceMultiplier = latestEvidence?.curveVarianceMultiplier,
                curveCalibrationStatus = curve?.calibrationStatus ?: latest?.curveCalibrationStatus,
                lastProcessedSessionDate = latest?.sessionDate?.let(LocalDate::parse),
                modelVersion = latest?.modelVersion ?: modelState?.modelVersion,
                curveVersion = latest?.curveVersion ?: modelState?.curveVersion,
                history = rows.map { row -> row.toSummaryPoint(evidenceByFingerprint[row.evidenceFingerprint]) }
            )
        }
        val finiteHistory = history.flatMap { row ->
            listOfNotNull(row.posteriorMedian, row.posteriorLow80, row.posteriorHigh80)
        }
        if (finiteHistory.any { value -> !value.isFinite() }) diagnostics += "NON_FINITE_HISTORY_VALUE"
        return PersistentStrengthPerformanceSummary(
            targets = targets,
            eventCount = events.size,
            pendingEventCount = events.count { event -> event.status == "PENDING" },
            failedEventCount = events.count { event -> event.status == "FAILED" },
            latestEventFingerprint = events.maxWithOrNull(
                compareBy<StrengthPosteriorEventEntity>(StrengthPosteriorEventEntity::sessionDate)
                    .thenBy(StrengthPosteriorEventEntity::createdAt)
            )?.completionFingerprint,
            modelStateFingerprint = modelState?.stateFingerprint,
            modelVersionBoundaries = (events.map(StrengthPosteriorEventEntity::modelVersion) +
                history.map(StrengthPosteriorHistoryEntity::modelVersion) + listOfNotNull(modelState?.modelVersion)).distinct(),
            curveVersionBoundaries = (events.map(StrengthPosteriorEventEntity::curveVersion) +
                history.map(StrengthPosteriorHistoryEntity::curveVersion) + listOfNotNull(modelState?.curveVersion)).distinct(),
            factorSchemaVersion = modelState?.factorSchemaVersion ?: history.lastOrNull()?.factorSchemaVersion,
            bootstrapProvenance = bootstrapProvenance,
            backupRestorationProvenance = backupRestorationProvenance,
            numericalDiagnostics = diagnostics.distinct(),
            activeRevisionKey = activeRevision?.revisionKey,
            activeRevisionStatus = activeRevision?.status,
            activeRevisionReason = activeRevision?.creationReason,
            rirPolicyVersion = activeRevision?.rirPolicyVersion,
            localExerciseStateCount = localExerciseStateCount,
            proxyTransferCount = proxyTransferCount,
            supersededRevisionCount = supersededRevisionCount
        )
    }

    private fun StrengthPosteriorHistoryEntity.toSummaryPoint(
        evidence: StrengthPosteriorEvidenceEntity?
    ): PersistentStrengthHistoryPoint = PersistentStrengthHistoryPoint(
        eventUuid = eventUuid,
        sessionDate = LocalDate.parse(sessionDate),
        priorMedianKg = priorMedian,
        priorLow80Kg = priorLow80,
        priorHigh80Kg = priorHigh80,
        posteriorMedianKg = posteriorMedian,
        posteriorLow80Kg = posteriorLow80,
        posteriorHigh80Kg = posteriorHigh80,
        directObservedLoadKg = directObservedLoad,
        directObservationType = directObservationType,
        sessionObservationMedianKg = sessionObservationMedian,
        sessionObservationLow80Kg = sessionObservationLow80,
        sessionObservationHigh80Kg = sessionObservationHigh80,
        posteriorMeanChangeKg = posteriorMeanChange,
        intervalWidthChange80Kg = intervalWidthChange80,
        predictivePercentile = predictivePercentile,
        strongObservationType = evidence?.observationType ?: directObservationType,
        curveProfileId = evidence?.curveProfileId ?: curveProfileId,
        curveMatchLevel = evidence?.curveMatchLevel ?: curveMatchLevel,
        curveCalibrationStatus = curveCalibrationStatus,
        bodyWeightKgAtProcessing = bodyWeightKgAtProcessing,
        rawAddedWeightKgAtProcessing = rawAddedWeightKgAtProcessing,
        totalLoadKgAtProcessing = bodyWeightKgAtProcessing?.let { bodyWeight ->
            rawAddedWeightKgAtProcessing?.let { added -> bodyWeight + added }
        },
        bodyWeightSource = bodyWeightSource,
        sourceEvidenceStatus = sourceEvidenceStatus,
        sourceSetCountAtProcessing = sourceSetCountAtProcessing,
        evidenceFingerprint = evidenceFingerprint,
        modelVersion = modelVersion,
        curveVersion = curveVersion,
        factorSchemaVersion = factorSchemaVersion
    )
}

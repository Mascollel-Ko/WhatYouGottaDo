package com.training.trackplanner.data

import androidx.room.withTransaction
import com.training.trackplanner.analysis.strengthperformance.PersonalCurvePosterior
import com.training.trackplanner.analysis.strengthperformance.RpeRirPolicy
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceLoadResolver
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.StrengthSessionObservationBuilder
import com.training.trackplanner.analysis.strengthperformance.fingerprint
import com.training.trackplanner.analysis.strengthperformance.toEntity
import com.training.trackplanner.analysis.strengthperformance.toPosterior
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StrengthSessionCompletionState(
    val unconfirmedSetCount: Int,
    val confirmedSetCount: Int
)

object StrengthSessionCompletionDetector {
    fun eligible(before: StrengthSessionCompletionState, after: StrengthSessionCompletionState): Boolean =
        before.unconfirmedSetCount > 0 &&
            after.unconfirmedSetCount == 0 &&
            after.confirmedSetCount > 0
}

class StrengthPosteriorEventProcessor(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val dailyMetricDao: DailyMetricDao,
    private val dailyCheckInDao: DailyCheckInDao,
    private val initialUserProfileDao: InitialUserProfileDao,
    private val posteriorDao: StrengthPosteriorDao,
    private val registry: StrengthPerformanceRegistry,
    private val curves: RepetitionCurveRegistry,
    private val rirPolicy: RpeRirPolicy,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun process(eventUuid: String): Boolean {
        val event = posteriorDao.eventByUuid(eventUuid) ?: return false
        if (event.status == STATUS_PROCESSED) return true
        return runCatching {
            val date = LocalDate.parse(event.sessionDate)
            val records = workoutDao.entriesWithSets(event.sessionDate)
            val exercises = exerciseDao.allExercises().associateBy(Exercise::id)
            val currentFingerprint = StrengthCompletionFingerprint.forRevision(
                event.revisionKey,
                StrengthCompletionFingerprint.build(event.sessionDate, records, exercises)
            )
            require(currentFingerprint == event.completionFingerprint) {
                "Completion evidence changed before posterior processing."
            }
            val profile = initialUserProfileDao.profile()
            val loadResolver = StrengthPerformanceLoadResolver(
                dailyMetrics = dailyMetricDao.allMetrics(),
                dailyCheckIns = dailyCheckInDao.all(),
                initialProfile = profile
            )
            val curvePosteriors = posteriorDao.allCurvePosteriors()
                .filter { entity -> entity.curveSubjectKey.startsWith("${event.revisionKey}|") }
                .associate { entity ->
                StrengthModelRevisionPolicy.originalCurveSubjectKey(event.revisionKey, entity.curveSubjectKey) to
                    entity.copy(
                        curveSubjectKey = StrengthModelRevisionPolicy.originalCurveSubjectKey(
                            event.revisionKey,
                            entity.curveSubjectKey
                        )
                    ).toPosterior()
            }
            val observations = records.mapNotNull { record ->
                val exercise = exercises[record.entry.exerciseId] ?: return@mapNotNull null
                val theta = curvePosteriors["exercise:${exercise.stableKey}"]?.meanTheta
                    ?: curvePosteriors["global:user-strength-endurance"]?.meanTheta
                    ?: 0.0
                StrengthSessionObservationBuilder.build(
                    record = record,
                    exercise = exercise,
                    registry = registry,
                    curveRegistry = curves,
                    loadResolver = loadResolver,
                    rirPolicy = rirPolicy,
                    personalTheta = theta
                )
            }
            val currentState = posteriorDao.modelState(StrengthModelRevisionPolicy.modelInstanceKey(event.revisionKey))
                ?.let(StrengthPosteriorModel::fromEntity)
                ?: StrengthPosteriorModel.initialState(registry, profile)
            val localStates = posteriorDao.localStates(event.revisionKey).associate { entity ->
                entity.exerciseStableKey to entity.toLocalState()
            }
            val timestamp = now()
            val computation = StrengthPosteriorModel.compute(
                eventUuid = eventUuid,
                date = date,
                currentState = currentState,
                observations = observations,
                registry = registry,
                curves = curves,
                curvePosteriorBySubject = curvePosteriors,
                currentLocalStates = localStates,
                now = timestamp
            )
            val namespacedEvidence = computation.evidence.associate { evidence ->
                evidence.evidenceFingerprint to fingerprint(event.revisionKey, evidence.evidenceFingerprint)
            }
            val persistedEvidence = computation.evidence.map { evidence ->
                evidence.copy(evidenceFingerprint = checkNotNull(namespacedEvidence[evidence.evidenceFingerprint]))
            }
            val persistedHistory = computation.history.map { history ->
                history.copy(evidenceFingerprint = checkNotNull(namespacedEvidence[history.evidenceFingerprint]))
            }
            val appliedProxyExercises = computation.proxyTransfers.mapTo(mutableSetOf()) { it.exerciseStableKey }
            val persistedLocalHistory = computation.localHistory.map { history ->
                history.toEntity(
                    revisionKey = event.revisionKey,
                    proxyTransferApplied = history.exerciseStableKey in appliedProxyExercises,
                    evidenceFingerprint = namespacedEvidence[history.evidenceFingerprint]
                        ?: fingerprint(event.revisionKey, history.evidenceFingerprint),
                    now = timestamp
                )
            }
            val persistedProxyHistory = computation.proxyTransfers.map { transfer ->
                transfer.toEntity(event.revisionKey, date, computation.state.orderedFactorSchema, timestamp)
            }
            posteriorDao.commitProcessedEvent(
                eventUuid = eventUuid,
                histories = persistedHistory,
                evidence = persistedEvidence,
                modelState = StrengthPosteriorModel.toEntity(
                    computation.state,
                    timestamp,
                    StrengthModelRevisionPolicy.modelInstanceKey(event.revisionKey)
                ),
                curvePosteriors = computation.curvePosteriors.map { posterior ->
                    posterior.toEntity().copy(
                        curveSubjectKey = StrengthModelRevisionPolicy.curveSubjectKey(
                            event.revisionKey,
                            posterior.curveSubjectKey
                        )
                    )
                },
                localStates = computation.localStates.values.map { state ->
                    state.toEntity(event.revisionKey, timestamp)
                },
                localHistory = persistedLocalHistory,
                proxyHistory = persistedProxyHistory,
                evidenceFingerprint = fingerprint(event.revisionKey, computation.evidenceFingerprint),
                processedAt = timestamp
            )
            true
        }.getOrElse { error ->
            posteriorDao.updateEventStatus(
                eventUuid = eventUuid,
                status = STATUS_FAILED,
                processedAt = null,
                evidenceFingerprint = null,
                errorCode = error::class.simpleName ?: "PROCESSING_FAILED",
                errorMessage = error.message?.take(MAX_ERROR_MESSAGE_LENGTH)
            )
            false
        }
    }

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSED = "PROCESSED"
        const val STATUS_FAILED = "FAILED"
        private const val MAX_ERROR_MESSAGE_LENGTH = 500
    }
}

class StrengthPosteriorUpdateCoordinator(
    private val db: TrainingDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val appMetaDao: AppMetaDao,
    private val posteriorDao: StrengthPosteriorDao,
    private val processor: StrengthPosteriorEventProcessor,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun state(date: String): StrengthSessionCompletionState = StrengthSessionCompletionState(
        unconfirmedSetCount = workoutDao.countUnconfirmedSetsOnDate(date),
        confirmedSetCount = workoutDao.countConfirmedSetsOnDate(date)
    )

    suspend fun <T> mutateDate(
        date: String,
        reason: String = REASON_LIVE_COMPLETION,
        mutation: suspend () -> T
    ): T {
        check(ensureCorrectedRevision()) { "Corrected strength model revision is unavailable." }
        var pendingEventUuid: String? = null
        val result = db.withTransaction {
            val before = state(date)
            val value = mutation()
            pendingEventUuid = enqueueIfEligibleWithinTransaction(date, before, reason)
            value
        }
        pendingEventUuid?.let { eventUuid -> processOffUi(eventUuid) }
        return result
    }

    suspend fun <T> mutateDates(
        dates: Collection<String>,
        reason: String = REASON_LIVE_COMPLETION,
        mutation: suspend () -> T
    ): T {
        check(ensureCorrectedRevision()) { "Corrected strength model revision is unavailable." }
        val uniqueDates = dates.distinct().sorted()
        val pending = mutableListOf<String>()
        val result = db.withTransaction {
            val before = uniqueDates.associateWith { date -> state(date) }
            val value = mutation()
            uniqueDates.mapNotNullTo(pending) { date ->
                enqueueIfEligibleWithinTransaction(date, checkNotNull(before[date]), reason)
            }
            value
        }
        pending.forEach { eventUuid -> processOffUi(eventUuid) }
        return result
    }

    suspend fun retryPending() {
        posteriorDao.retryableEventsForRevision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
            .forEach { event -> processOffUi(event.eventUuid) }
    }

    suspend fun bootstrapIfNeeded(reason: String = REASON_INITIAL_BOOTSTRAP): Boolean {
        if (!ensureCorrectedRevision()) return false
        if (appMetaDao.value(BOOTSTRAP_MARKER_KEY) != null) return false
        retryPending()
        workoutDao.completedWorkoutDates().forEach { date ->
            val eventUuid = db.withTransaction {
                posteriorDao.eventBySessionKey(sessionKey(date))?.eventUuid
                    ?: enqueueCompletedDateWithinTransaction(date, reason)
            }
            if (eventUuid != null && !processOffUi(eventUuid)) return false
        }
        appMetaDao.upsert(
            AppMeta(
                key = BOOTSTRAP_MARKER_KEY,
                value = "completed|$reason|${now()}|${StrengthPosteriorModel.MODEL_VERSION}"
            )
        )
        return true
    }

    suspend fun ensureCorrectedRevision(): Boolean {
        val current = posteriorDao.revision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
        if (current?.status == StrengthModelRevisionPolicy.STATUS_ACTIVE) {
            if (!StrengthModelRevisionPolicy.isCompatible(current)) return false
            if (appMetaDao.value(StrengthModelRevisionPolicy.REBUILD_MARKER_KEY) == null) {
                appMetaDao.upsert(
                    AppMeta(
                        StrengthModelRevisionPolicy.REBUILD_MARKER_KEY,
                        "completed|${current.revisionFingerprint}|${current.rebuildCompletedAt ?: 0L}"
                    )
                )
            }
            return true
        }
        if (current?.status == StrengthModelRevisionPolicy.STATUS_FAILED) return false
        val startedAt = now()
        db.withTransaction {
            if (posteriorDao.allRevisions().isEmpty() &&
                (
                    posteriorDao.modelState(StrengthPosteriorModel.MODEL_INSTANCE_KEY) != null ||
                        posteriorDao.eventsForRevision(StrengthModelRevisionPolicy.LEGACY_REVISION_KEY).isNotEmpty()
                    )
            ) {
                posteriorDao.insertRevisionIfAbsent(StrengthModelRevisionPolicy.legacy(startedAt))
            }
            if (posteriorDao.revision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY) == null) {
                posteriorDao.insertRevisionStrict(
                    StrengthModelRevisionPolicy.current(
                        now = startedAt,
                        sourceRevisionKey = posteriorDao.activeRevision()?.revisionKey
                    )
                )
            }
        }
        return runCatching {
            workoutDao.completedWorkoutDates().forEach { date ->
                val eventUuid = db.withTransaction {
                    posteriorDao.eventBySessionKeyAndRevision(
                        sessionKey(date),
                        StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
                    )?.eventUuid ?: enqueueCompletedDateWithinTransaction(
                        date,
                        StrengthModelRevisionPolicy.CORRECTION_REASON,
                        StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
                    )
                }
                if (eventUuid != null && !processOffUi(eventUuid)) error("Correction rebuild event failed: $date")
            }
            val completedAt = now()
            db.withTransaction {
                val events = posteriorDao.eventsForRevision(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
                require(events.all { it.status == StrengthPosteriorEventProcessor.STATUS_PROCESSED })
                posteriorDao.supersedeOtherRevisions(StrengthModelRevisionPolicy.CURRENT_REVISION_KEY)
                posteriorDao.updateRevisionStatus(
                    StrengthModelRevisionPolicy.CURRENT_REVISION_KEY,
                    StrengthModelRevisionPolicy.STATUS_ACTIVE,
                    completedAt,
                    null,
                    null
                )
                appMetaDao.upsert(
                    AppMeta(
                        StrengthModelRevisionPolicy.REBUILD_MARKER_KEY,
                        "completed|${StrengthModelRevisionPolicy.CURRENT_REVISION_KEY}|$completedAt"
                    )
                )
            }
            true
        }.getOrElse { error ->
            posteriorDao.updateRevisionStatus(
                StrengthModelRevisionPolicy.CURRENT_REVISION_KEY,
                StrengthModelRevisionPolicy.STATUS_FAILED,
                null,
                error::class.simpleName ?: "REBUILD_FAILED",
                error.message?.take(500)
            )
            false
        }
    }

    suspend fun scheduleLegacyBackupBootstrap() {
        appMetaDao.delete(BOOTSTRAP_MARKER_KEY)
    }

    private suspend fun processOffUi(eventUuid: String): Boolean =
        withContext(Dispatchers.Default) { processor.process(eventUuid) }

    private suspend fun enqueueIfEligibleWithinTransaction(
        date: String,
        before: StrengthSessionCompletionState,
        reason: String
    ): String? {
        val after = state(date)
        if (!StrengthSessionCompletionDetector.eligible(before, after)) return null
        return enqueueCompletedDateWithinTransaction(date, reason)
    }

    private suspend fun enqueueCompletedDateWithinTransaction(
        date: String,
        reason: String,
        revisionKey: String = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
    ): String? {
        val key = sessionKey(date)
        posteriorDao.eventBySessionKeyAndRevision(key, revisionKey)?.let { existing -> return existing.eventUuid }
        val records = workoutDao.entriesWithSets(date)
        val exercises = exerciseDao.allExercises().associateBy(Exercise::id)
        val completionFingerprint = StrengthCompletionFingerprint.forRevision(
            revisionKey,
            StrengthCompletionFingerprint.build(date, records, exercises)
        )
        val confirmedCount = records.sumOf { record -> record.sets.count(WorkoutSet::confirmed) }
        if (confirmedCount == 0) return null
        val eventUuid = UUID.nameUUIDFromBytes(
            "strength-posterior-event-v2|$revisionKey|$completionFingerprint".toByteArray(StandardCharsets.UTF_8)
        ).toString()
        val event = StrengthPosteriorEventEntity(
            eventUuid = eventUuid,
            sessionKey = key,
            sessionDate = date,
            completionFingerprint = completionFingerprint,
            status = StrengthPosteriorEventProcessor.STATUS_PENDING,
            creationReason = reason,
            confirmedSetCount = confirmedCount,
            createdAt = now(),
            modelVersion = StrengthPosteriorModel.MODEL_VERSION,
            curveVersion = RepetitionCurveRegistry.CURVE_VERSION,
            factorSchemaVersion = StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION,
            revisionKey = revisionKey
        )
        val inserted = posteriorDao.insertPendingEvent(event)
        return if (inserted != -1L) eventUuid else posteriorDao.eventByCompletionFingerprint(completionFingerprint)?.eventUuid
    }

    companion object {
        const val BOOTSTRAP_MARKER_KEY = "strength_posterior_bootstrap_v2"
        const val RESTORE_PROVENANCE_KEY = "strength_posterior_restore_provenance_v1"
        const val REASON_LIVE_COMPLETION = "LIVE_SESSION_COMPLETION"
        const val REASON_INITIAL_BOOTSTRAP = "INITIAL_INSTALLATION_BOOTSTRAP"
        const val REASON_LEGACY_BACKUP_BOOTSTRAP = "LEGACY_BACKUP_BOOTSTRAP"
        fun sessionKey(date: String): String = "date:$date"
    }
}

object StrengthCompletionFingerprint {
    fun forRevision(revisionKey: String, rawFingerprint: String): String =
        if (revisionKey == StrengthModelRevisionPolicy.LEGACY_REVISION_KEY) rawFingerprint
        else fingerprint("strength-completion-fingerprint-v2", revisionKey, rawFingerprint)

    fun build(
        date: String,
        records: List<WorkoutEntryWithSets>,
        exercisesById: Map<Long, Exercise>
    ): String {
        val values = mutableListOf("strength-completion-fingerprint-v1", date)
        records.sortedWith(
            compareBy<WorkoutEntryWithSets> { record -> record.entry.performedAt ?: Long.MIN_VALUE }
                .thenBy { record -> record.entry.displayOrder }
                .thenBy { record -> exercisesById[record.entry.exerciseId]?.stableKey.orEmpty() }
                .thenBy { record -> record.entry.createdAt }
        ).forEachIndexed { entryIndex, record ->
            val confirmed = record.sets.filter(WorkoutSet::confirmed).sortedWith(
                compareBy(WorkoutSet::setIndex, WorkoutSet::reps, WorkoutSet::weightKg, WorkoutSet::seconds)
            )
            if (confirmed.isEmpty()) return@forEachIndexed
            values += exercisesById[record.entry.exerciseId]?.stableKey ?: "missing-exercise:${record.entry.exerciseId}"
            values += entryIndex.toString()
            values += record.entry.rpe?.toBits()?.toString().orEmpty()
            confirmed.forEachIndexed { setIndex, set ->
                values += listOf(
                    setIndex.toString(),
                    set.setIndex.toString(),
                    set.reps.toString(),
                    set.weightKg.toBits().toString(),
                    set.seconds.toString(),
                    set.rpe?.toBits()?.toString().orEmpty()
                )
            }
        }
        return fingerprint(*values.toTypedArray())
    }
}

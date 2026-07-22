package com.training.trackplanner.data

import androidx.room.withTransaction
import com.training.trackplanner.analysis.strengthperformance.PersonalCurvePosterior
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
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun process(eventUuid: String): Boolean {
        val event = posteriorDao.eventByUuid(eventUuid) ?: return false
        if (event.status == STATUS_PROCESSED) return true
        return runCatching {
            val date = LocalDate.parse(event.sessionDate)
            val records = workoutDao.entriesWithSets(event.sessionDate)
            val exercises = exerciseDao.allExercises().associateBy(Exercise::id)
            val currentFingerprint = StrengthCompletionFingerprint.build(event.sessionDate, records, exercises)
            require(currentFingerprint == event.completionFingerprint) {
                "Completion evidence changed before posterior processing."
            }
            val profile = initialUserProfileDao.profile()
            val loadResolver = StrengthPerformanceLoadResolver(
                dailyMetrics = dailyMetricDao.allMetrics(),
                dailyCheckIns = dailyCheckInDao.all(),
                initialProfile = profile
            )
            val curvePosteriors = posteriorDao.allCurvePosteriors().associate { entity ->
                entity.curveSubjectKey to entity.toPosterior()
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
                    personalTheta = theta
                )
            }
            val currentState = posteriorDao.modelState(StrengthPosteriorModel.MODEL_INSTANCE_KEY)
                ?.let(StrengthPosteriorModel::fromEntity)
                ?: StrengthPosteriorModel.initialState(registry, profile)
            val timestamp = now()
            val computation = StrengthPosteriorModel.compute(
                eventUuid = eventUuid,
                date = date,
                currentState = currentState,
                observations = observations,
                registry = registry,
                curves = curves,
                curvePosteriorBySubject = curvePosteriors,
                now = timestamp
            )
            posteriorDao.commitProcessedEvent(
                eventUuid = eventUuid,
                histories = computation.history,
                evidence = computation.evidence,
                modelState = StrengthPosteriorModel.toEntity(computation.state, timestamp),
                curvePosteriors = computation.curvePosteriors.map(PersonalCurvePosterior::toEntity),
                evidenceFingerprint = computation.evidenceFingerprint,
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
        posteriorDao.retryableEvents().forEach { event -> processOffUi(event.eventUuid) }
    }

    suspend fun bootstrapIfNeeded(reason: String = REASON_INITIAL_BOOTSTRAP): Boolean {
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

    private suspend fun enqueueCompletedDateWithinTransaction(date: String, reason: String): String? {
        val key = sessionKey(date)
        posteriorDao.eventBySessionKey(key)?.let { existing -> return existing.eventUuid }
        val records = workoutDao.entriesWithSets(date)
        val exercises = exerciseDao.allExercises().associateBy(Exercise::id)
        val completionFingerprint = StrengthCompletionFingerprint.build(date, records, exercises)
        val confirmedCount = records.sumOf { record -> record.sets.count(WorkoutSet::confirmed) }
        if (confirmedCount == 0) return null
        val eventUuid = UUID.nameUUIDFromBytes(
            "strength-posterior-event-v1|$completionFingerprint".toByteArray(StandardCharsets.UTF_8)
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
            factorSchemaVersion = StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION
        )
        val inserted = posteriorDao.insertPendingEvent(event)
        return if (inserted != -1L) eventUuid else posteriorDao.eventByCompletionFingerprint(completionFingerprint)?.eventUuid
    }

    companion object {
        const val BOOTSTRAP_MARKER_KEY = "strength_posterior_bootstrap_v2"
        const val REASON_LIVE_COMPLETION = "LIVE_SESSION_COMPLETION"
        const val REASON_INITIAL_BOOTSTRAP = "INITIAL_INSTALLATION_BOOTSTRAP"
        const val REASON_LEGACY_BACKUP_BOOTSTRAP = "LEGACY_BACKUP_BOOTSTRAP"
        fun sessionKey(date: String): String = "date:$date"
    }
}

object StrengthCompletionFingerprint {
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

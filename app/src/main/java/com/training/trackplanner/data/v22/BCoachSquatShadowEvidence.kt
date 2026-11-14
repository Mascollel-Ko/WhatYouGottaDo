package com.training.trackplanner.data.v22

import com.training.trackplanner.analysis.strengthperformance.PersonalCurvePosterior
import com.training.trackplanner.analysis.strengthperformance.StrengthPerformanceRegistry
import com.training.trackplanner.analysis.strengthperformance.StrengthPosteriorModel
import com.training.trackplanner.analysis.strengthperformance.StrengthTargetDistribution
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveRegistry
import com.training.trackplanner.analysis.strengthperformance.curve.ResolvedRepetitionCurve
import com.training.trackplanner.analysis.strengthperformance.toPosterior
import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.StrengthCurvePosteriorEntity
import com.training.trackplanner.data.StrengthModelRevisionEntity
import com.training.trackplanner.data.StrengthModelRevisionPolicy
import com.training.trackplanner.data.StrengthPosteriorDao
import com.training.trackplanner.data.StrengthPosteriorModelStateEntity
import com.training.trackplanner.data.WorkoutDao
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

/**
 * Shadow-only Gate A. Adapts the EXISTING persistent performance posterior and confirmed
 * squat sets into an immutable planning input. No model recalculation or database writes.
 *
 * Never infer a historical as-of posterior from the latest state. If the persisted model
 * has processed anything after asOf, fail closed until an event-history reconstruction exists.
 */
internal sealed interface BCoachSquatEvidenceResult {
    data class Ready(val evidence: BCoachSquatEvidence) : BCoachSquatEvidenceResult
    data class Unavailable(val reason: Reason) : BCoachSquatEvidenceResult

    enum class Reason {
        INACTIVE_OR_INCOMPATIBLE_REVISION,
        STALE_OR_MISSING_MODEL,
        FUTURE_POSTERIOR_LEAKAGE,
        UNPROCESSED_MODEL_EVENT,
        MODEL_BEHIND_CONFIRMED_SETS,
        UNREADABLE_POSTERIOR,
        UNREADABLE_CURVE
    }
}

internal data class BCoachConfirmedSquatSet(
    val setId: Long,
    val setIndex: Int,
    val loadKg: Double,
    val repetitions: Int,
    /** Null is preserved: a missing RPE is not RIR=0. */
    val observedRpe: Double?
)

internal data class BCoachSquatAnchorSession(
    val sessionStableKey: String,
    val date: LocalDate,
    val confirmedSets: List<BCoachConfirmedSquatSet>
)

internal data class BCoachSquatEvidence(
    val asOf: LocalDate,
    val posteriorAsOf: LocalDate,
    val posteriorFingerprint: String,
    val modelVersion: String,
    val capacity: StrengthTargetDistribution,
    /** Existing canonical nonlinear curve; includes the resolved personal theta. */
    val curve: ResolvedRepetitionCurve,
    /** Entire curve posterior, not just its mean, for later uncertainty scenarios. */
    val curvePosterior: PersonalCurvePosterior?,
    val latestAnchor: BCoachSquatAnchorSession?,
    /** Completed squat loads observed in the preceding 56 days, for grid-conflict audit only. */
    val recentObservedLoadsKg: List<Double> = emptyList()
)

/** A pure, deterministic boundary, independently unit-testable without Room. */
internal object BCoachSquatEvidenceAdapter {
    fun fromPersisted(
        asOf: LocalDate,
        revision: StrengthModelRevisionEntity?,
        model: StrengthPosteriorModelStateEntity?,
        exerciseCurve: StrengthCurvePosteriorEntity?,
        sharedCurve: StrengthCurvePosteriorEntity?,
        records: List<WorkoutEntryWithSets>,
        registry: StrengthPerformanceRegistry,
        curves: RepetitionCurveRegistry,
        hasUnprocessedEvents: Boolean = false
    ): BCoachSquatEvidenceResult {
        if (revision == null ||
            revision.status != StrengthModelRevisionPolicy.STATUS_ACTIVE ||
            !StrengthModelRevisionPolicy.isCompatible(revision)
        ) return BCoachSquatEvidenceResult.Unavailable(
            BCoachSquatEvidenceResult.Reason.INACTIVE_OR_INCOMPATIBLE_REVISION
        )
        if (hasUnprocessedEvents) return BCoachSquatEvidenceResult.Unavailable(
            BCoachSquatEvidenceResult.Reason.UNPROCESSED_MODEL_EVENT
        )
        if (model == null || model.modelVersion != StrengthPosteriorModel.MODEL_VERSION ||
            model.curveVersion != RepetitionCurveRegistry.CURVE_VERSION ||
            model.factorSchemaVersion != StrengthPerformanceRegistry.FACTOR_SCHEMA_VERSION
        ) return BCoachSquatEvidenceResult.Unavailable(
            BCoachSquatEvidenceResult.Reason.STALE_OR_MISSING_MODEL
        )
        val posteriorDate = model.lastProcessedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.STALE_OR_MISSING_MODEL
            )
        if (posteriorDate > asOf) return BCoachSquatEvidenceResult.Unavailable(
            BCoachSquatEvidenceResult.Reason.FUTURE_POSTERIOR_LEAKAGE
        )

        val posteriorState = runCatching { StrengthPosteriorModel.fromEntity(model) }.getOrNull()
            ?: return BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.UNREADABLE_POSTERIOR
            )
        val target = registry.target(StrengthPerformanceRegistry.BACK_SQUAT)
            ?: return BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.UNREADABLE_POSTERIOR
            )
        val capacity = runCatching { StrengthPosteriorModel.distribution(posteriorState, target) }.getOrNull()
            ?.takeIf { it.median.isFinite() && it.median > 0.0 &&
                it.low80.isFinite() && it.low80 > 0.0 &&
                it.high80.isFinite() && it.high80 >= it.low80
            } ?: return BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.UNREADABLE_POSTERIOR
            )

        val key = target.anchorStableKeys.singleOrNull()
            ?: return BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.UNREADABLE_POSTERIOR
            )
        val hasNewConfirmedSquat = records.any { workout ->
            workout.entry.exerciseStableKey == key &&
                runCatching { LocalDate.parse(workout.entry.date) > posteriorDate &&
                    LocalDate.parse(workout.entry.date) <= asOf }.getOrDefault(false) &&
                workout.sets.any { it.confirmed && it.loadState == ProgramLoadState.EXPLICIT_LOAD &&
                    it.weightKg.isFinite() && it.weightKg > 0.0 && it.reps in 1..20 }
        }
        if (hasNewConfirmedSquat) return BCoachSquatEvidenceResult.Unavailable(
            BCoachSquatEvidenceResult.Reason.MODEL_BEHIND_CONFIRMED_SETS
        )
        val storedCurve = exerciseCurve ?: sharedCurve
        val personalCurve = if (storedCurve == null) null else {
            if (storedCurve.curveVersion != RepetitionCurveRegistry.CURVE_VERSION)
                return BCoachSquatEvidenceResult.Unavailable(
                    BCoachSquatEvidenceResult.Reason.UNREADABLE_CURVE
                )
            runCatching { storedCurve.toPosterior() }.getOrNull()
                ?: return BCoachSquatEvidenceResult.Unavailable(
                    BCoachSquatEvidenceResult.Reason.UNREADABLE_CURVE
                )
        }
        val resolvedCurve = runCatching {
            curves.resolve(key, personalTheta = personalCurve?.meanTheta ?: 0.0)
        }.getOrNull() ?: return BCoachSquatEvidenceResult.Unavailable(
            BCoachSquatEvidenceResult.Reason.UNREADABLE_CURVE
        )
        if (personalCurve != null && personalCurve.canonicalProfileId != resolvedCurve.profile.id.value)
            return BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.UNREADABLE_CURVE
            )

        val latest = records.asSequence()
            .filter { it.entry.exerciseStableKey == key }
            .mapNotNull { workout ->
                val day = runCatching { LocalDate.parse(workout.entry.date) }.getOrNull()
                    ?: return@mapNotNull null
                if (day > asOf || day > posteriorDate) return@mapNotNull null
                val sets = workout.sets.asSequence()
                    .filter { it.confirmed && it.loadState == ProgramLoadState.EXPLICIT_LOAD }
                    .filter { it.weightKg.isFinite() && it.weightKg > 0.0 && it.reps in 1..20 }
                    .sortedWith(compareBy({ it.setIndex }, { it.id }))
                    .map { set ->
                        BCoachConfirmedSquatSet(
                            setId = set.id,
                            setIndex = set.setIndex,
                            loadKg = set.weightKg,
                            repetitions = set.reps,
                            observedRpe = set.rpe?.takeIf { it.isFinite() && it in 6.0..10.0 }
                                ?: workout.entry.rpe?.takeIf { it.isFinite() && it in 6.0..10.0 }
                        )
                    }.toList()
                if (sets.isEmpty()) return@mapNotNull null
                Triple(day, workout, sets)
            }
            .maxWithOrNull(compareBy<Triple<LocalDate, WorkoutEntryWithSets, List<BCoachConfirmedSquatSet>>>(
                { it.first }, { it.second.entry.performedAt ?: it.second.entry.completedAt ?: it.second.entry.createdAt },
                { it.second.entry.id }
            ))
            ?.let { (day, workout, sets) ->
                BCoachSquatAnchorSession(workout.entry.sessionStableKey, day, sets)
            }
        return BCoachSquatEvidenceResult.Ready(
            BCoachSquatEvidence(
                asOf = asOf,
                posteriorAsOf = posteriorDate,
                posteriorFingerprint = model.stateFingerprint,
                modelVersion = model.modelVersion,
                capacity = capacity,
                curve = resolvedCurve,
                curvePosterior = personalCurve,
                latestAnchor = latest,
                recentObservedLoadsKg = records.asSequence()
                    .filter { it.entry.exerciseStableKey == key }
                    .filter { workout ->
                        val day = runCatching { LocalDate.parse(workout.entry.date) }.getOrNull()
                        day != null && !day.isAfter(asOf) && !day.isAfter(posteriorDate) &&
                            !day.isBefore(asOf.minusDays(56))
                    }
                    .flatMap { it.sets.asSequence() }
                    .filter { it.confirmed && it.loadState == ProgramLoadState.EXPLICIT_LOAD &&
                        it.weightKg.isFinite() && it.weightKg > 0.0 && it.reps in 1..20 }
                    .map { it.weightKg }
                    .distinct()
                    .sorted()
                    .toList()
            )
        )
    }
}

/** Read-only Room accessor. Not wired to the production B generator in Gate A. */
internal class BCoachSquatShadowEvidenceReader(
    private val posteriorDao: StrengthPosteriorDao,
    private val workoutDao: WorkoutDao,
    private val registry: StrengthPerformanceRegistry,
    private val curves: RepetitionCurveRegistry
) {
    suspend fun read(asOf: LocalDate): BCoachSquatEvidenceResult {
        val revisionKey = StrengthModelRevisionPolicy.CURRENT_REVISION_KEY
        val revision = posteriorDao.activeRevision(revisionKey)
        if (revision == null || !StrengthModelRevisionPolicy.isCompatible(revision))
            return BCoachSquatEvidenceResult.Unavailable(
                BCoachSquatEvidenceResult.Reason.INACTIVE_OR_INCOMPATIBLE_REVISION
            )
        val pending = posteriorDao.eventsForRevision(revisionKey).any { event ->
            (event.status == "PENDING" || event.status == "FAILED") &&
                runCatching { LocalDate.parse(event.sessionDate) <= asOf }.getOrDefault(true)
        }
        val model = posteriorDao.modelState(StrengthModelRevisionPolicy.modelInstanceKey(revisionKey))
        val squatKey = registry.target(StrengthPerformanceRegistry.BACK_SQUAT)
            ?.anchorStableKeys?.singleOrNull() ?: "barbell_back_squat"
        val records = workoutDao.entriesWithSetsUntil(asOf.toString())
        return BCoachSquatEvidenceAdapter.fromPersisted(
            asOf = asOf,
            revision = revision,
            model = model,
            exerciseCurve = posteriorDao.curvePosterior(
                StrengthModelRevisionPolicy.curveSubjectKey(revisionKey, "exercise:$squatKey")
            ),
            sharedCurve = posteriorDao.curvePosterior(
                StrengthModelRevisionPolicy.curveSubjectKey(revisionKey, "global:user-strength-endurance")
            ),
            records = records,
            registry = registry,
            curves = curves,
            hasUnprocessedEvents = pending
        )
    }
}

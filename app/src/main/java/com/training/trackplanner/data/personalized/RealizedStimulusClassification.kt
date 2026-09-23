package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.analysis.strengthperformance.curve.ResolvedRepetitionCurve
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.StrengthExercisePerformanceHistoryEntity
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.exp

/** Typed authority for what a reviewed resistance set actually realized. */
enum class RealizedStimulusKind {
    STRENGTH_LIKE, HYPERTROPHY_LIKE, NONE;
    companion object {
        val STRENGTH: RealizedStimulusKind = STRENGTH_LIKE
        val HYPERTROPHY: RealizedStimulusKind = HYPERTROPHY_LIKE
    }
}

enum class RealizedStimulusStatus { REALIZED, REVIEWED_NON_REALIZATION, UNCLASSIFIED }

enum class RealizedStimulusAuthority { REVIEWED, UNCLASSIFIED }

typealias RealizedStimulusClassificationAuthority = RealizedStimulusAuthority
typealias RealizedStimulusClassificationStatus = RealizedStimulusStatus

data class RealizedStimulusClassification(
    val kind: RealizedStimulusKind,
    val status: RealizedStimulusStatus,
    val authority: RealizedStimulusAuthority,
    val resolvedLoadKg: Double? = null,
    val reference1RmKg: Double? = null,
    val relativeIntensity: Double? = null,
    val observedRpe: Double? = null,
    val impliedRir: Double? = null,
    val reasonCodes: List<String> = emptyList()
) {
    val isRealized: Boolean get() = status == RealizedStimulusStatus.REALIZED && authority == RealizedStimulusAuthority.REVIEWED
    val isUnclassified: Boolean get() = status == RealizedStimulusStatus.UNCLASSIFIED || authority == RealizedStimulusAuthority.UNCLASSIFIED

    companion object {
        val UNCLASSIFIED = RealizedStimulusClassification(
            kind = RealizedStimulusKind.NONE,
            status = RealizedStimulusStatus.UNCLASSIFIED,
            authority = RealizedStimulusAuthority.UNCLASSIFIED,
            reasonCodes = listOf("REALIZATION_MODEL_UNAVAILABLE")
        )

        fun reviewedNonRealization(reason: String = "REVIEWED_NON_REALIZATION"): RealizedStimulusClassification =
            RealizedStimulusClassification(
                kind = RealizedStimulusKind.NONE,
                status = RealizedStimulusStatus.REVIEWED_NON_REALIZATION,
                authority = RealizedStimulusAuthority.REVIEWED,
                reasonCodes = listOf(reason)
            )
    }
}

/** Input deliberately carries reviewed identity and canonical relation facts explicitly. */
data class RealizedStimulusInput(
    val stableKey: String,
    val date: LocalDate,
    val sessionStableKey: String? = null,
    val activityKind: PlannedActivityKind,
    val reps: Int,
    val resolvedLoadKg: Double?,
    val rpe: Double?,
    val directQualities: Set<TrainableQuality>,
    val reviewedIdentity: Boolean,
    val reviewedNonRealization: Boolean = false,
    val reference1RmKg: Double? = null,
    val impliedRir: Double? = null,
    val loadSemantics: StrengthLoadSemantics = StrengthLoadSemantics.EXTERNAL_LOAD,
    val curve: ResolvedRepetitionCurve? = null
)

/**
 * Bounded point-in-time strength reference lookup. Exact-session prior state wins; otherwise
 * only strictly earlier reviewed history can be used. Posterior rows are never used as a
 * same-set denominator.
 */
class CanonicalStrengthReferenceIndex(rows: List<StrengthExercisePerformanceHistoryEntity>) {
    private val ordered = rows.sortedWith(
        compareBy<StrengthExercisePerformanceHistoryEntity> { it.sessionDate }
            .thenBy { it.createdAt }
            .thenBy { it.eventUuid }
            .thenBy { it.exerciseStableKey }
    )

    fun reference1RmKg(stableKey: String, date: LocalDate, sessionStableKey: String? = null): Double? {
        val exact = sessionStableKey?.let { key ->
            ordered.lastOrNull {
                it.exerciseStableKey == stableKey && it.sessionKey == key &&
                    it.baselineEstablishedBefore && it.sessionDate == date.toString()
            }
        }
        val row = exact ?: ordered.lastOrNull {
            it.exerciseStableKey == stableKey && it.baselineEstablishedBefore &&
                LocalDate.parse(it.sessionDate).isBefore(date)
        }
        return row?.priorLogMean?.let(::exp)?.takeIf { it.isFinite() && it > 0.0 }
    }
}

object RealizedStimulusClassifier {
    fun classify(input: RealizedStimulusInput): RealizedStimulusClassification {
        if (input.reviewedNonRealization) return RealizedStimulusClassification.reviewedNonRealization()
        if (!input.reviewedIdentity || input.activityKind != PlannedActivityKind.RESISTANCE) {
            return RealizedStimulusClassification.UNCLASSIFIED.copy(reasonCodes = listOf("REVIEWED_RESISTANCE_IDENTITY_REQUIRED"))
        }
        val kind = when (input.reps) {
            in 1..6 -> RealizedStimulusKind.STRENGTH_LIKE
            in 7..15 -> RealizedStimulusKind.HYPERTROPHY_LIKE
            else -> return unclassified("REPS_OUTSIDE_REVIEWED_STIMULUS_RANGE")
        }
        val requiredQuality = when (kind) {
            RealizedStimulusKind.STRENGTH_LIKE -> TrainableQuality.STRENGTH
            RealizedStimulusKind.HYPERTROPHY_LIKE -> TrainableQuality.HYPERTROPHY
            RealizedStimulusKind.NONE -> return RealizedStimulusClassification.reviewedNonRealization()
        }
        if (requiredQuality !in input.directQualities) return unclassified("DIRECT_QUALITY_RELATION_REQUIRED")
        val load = input.resolvedLoadKg?.takeIf { it.isFinite() && it > 0.0 }
            ?: return unclassified("RESOLVED_LOAD_UNAVAILABLE")
        val reference = input.reference1RmKg?.takeIf { it.isFinite() && it > 0.0 }
            ?: return unclassified("INDEPENDENT_REFERENCE_1RM_UNAVAILABLE")
        val relative = load / reference
        if (!relative.isFinite()) return unclassified("RELATIVE_INTENSITY_UNAVAILABLE")
        if (kind == RealizedStimulusKind.STRENGTH_LIKE && relative < 0.70) {
            return unclassified("STRENGTH_LOAD_BELOW_70_PERCENT_REFERENCE_1RM")
        }
        val rir = input.impliedRir ?: input.curve?.let { curve ->
            curve.profile.invert(relative).repetitions?.let { failureReps ->
                floor((failureReps - input.reps).coerceAtLeast(0.0))
            }
        }
        val effortSatisfied = when {
            input.rpe?.isFinite() == true -> input.rpe >= if (kind == RealizedStimulusKind.STRENGTH_LIKE) 6.0 else 7.0
            rir?.isFinite() == true -> rir <= if (kind == RealizedStimulusKind.STRENGTH_LIKE) 4.0 else 3.0
            else -> false
        }
        if (!effortSatisfied) return unclassified("EFFORT_THRESHOLD_UNRESOLVED")
        return RealizedStimulusClassification(
            kind = kind,
            status = RealizedStimulusStatus.REALIZED,
            authority = RealizedStimulusAuthority.REVIEWED,
            resolvedLoadKg = load,
            reference1RmKg = reference,
            relativeIntensity = relative,
            observedRpe = input.rpe,
            impliedRir = rir,
            reasonCodes = listOf("REVIEWED_CANONICAL_REALIZED_STIMULUS")
        )
    }

    private fun unclassified(reason: String) = RealizedStimulusClassification.UNCLASSIFIED.copy(reasonCodes = listOf(reason))
}

internal fun RealizedStimulusClassification.toLegacyClass(): RealizedStimulusClass = when (kind) {
    RealizedStimulusKind.STRENGTH_LIKE -> RealizedStimulusClass.STRENGTH_LIKE
    RealizedStimulusKind.HYPERTROPHY_LIKE -> RealizedStimulusClass.HYPERTROPHY_LIKE
    RealizedStimulusKind.NONE -> RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS
}

internal fun legacyClassification(
    legacy: RealizedStimulusClass,
    authority: StimulusClassificationAuthority
): RealizedStimulusClassification = when {
    authority == StimulusClassificationAuthority.UNCLASSIFIED -> RealizedStimulusClassification.UNCLASSIFIED
    legacy == RealizedStimulusClass.STRENGTH_LIKE -> RealizedStimulusClassification(
        RealizedStimulusKind.STRENGTH_LIKE, RealizedStimulusStatus.REALIZED, RealizedStimulusAuthority.REVIEWED,
        reasonCodes = listOf("LEGACY_TEST_FIXTURE_COMPATIBILITY")
    )
    legacy == RealizedStimulusClass.HYPERTROPHY_LIKE -> RealizedStimulusClassification(
        RealizedStimulusKind.HYPERTROPHY_LIKE, RealizedStimulusStatus.REALIZED, RealizedStimulusAuthority.REVIEWED,
        reasonCodes = listOf("LEGACY_TEST_FIXTURE_COMPATIBILITY")
    )
    else -> RealizedStimulusClassification.reviewedNonRealization("LEGACY_AMBIGUOUS_FIXTURE")
}

/** Resolves a historical set's B6 classification without reconstructing a denominator. */
internal fun PlanningHistorySnapshot.reviewedRealization(row: PlanningSetRecord): RealizedStimulusClassification =
    stimulusExposureLedger.setObservations.firstOrNull {
        it.source.stableKey == row.stableKey && it.source.date == row.date && it.source.setIndex == row.setIndex
    }?.realizedStimulusClassification ?: RealizedStimulusClassification.UNCLASSIFIED

internal fun PlanningHistorySnapshot.historyRealizedKind(row: PlanningSetRecord): RealizedStimulusKind =
    if (stimulusExposureLedger.setObservations.isEmpty()) {
        when (provisionalRealizedStimulusClass(row.reps)) {
            RealizedStimulusClass.STRENGTH_LIKE -> RealizedStimulusKind.STRENGTH_LIKE
            RealizedStimulusClass.HYPERTROPHY_LIKE -> RealizedStimulusKind.HYPERTROPHY_LIKE
            RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS -> RealizedStimulusKind.NONE
        }
    } else reviewedRealization(row).kind

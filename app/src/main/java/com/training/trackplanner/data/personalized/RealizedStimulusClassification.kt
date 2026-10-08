package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.analysis.strengthperformance.curve.ResolvedRepetitionCurve
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.CanonicalStrengthExposureCapability
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

enum class RealizedStimulusStatus { REALIZED, OVERPERFORMED, REVIEWED_NON_REALIZATION, UNCLASSIFIED }

/** Exact planned purpose, kept separate from the performed set's exposure assessment. */
enum class StrengthSetIntent { PLANNED_STRENGTH, NOT_PLANNED_STRENGTH, UNKNOWN }

/** A set can retain Strength intent while its realized exposure remains uncertain. */
enum class StrengthExposureAssessment { REALIZED, OVERPERFORMED, UNCERTAIN, NOT_STRENGTH, NOT_APPLICABLE }

data class StrengthSetIntentEvidence(
    val intent: StrengthSetIntent = StrengthSetIntent.UNKNOWN,
    val plannedReps: Int? = null,
    val selectionRole: String? = null,
    val sourceProgramStableKey: String? = null,
    val sourceItemId: String? = null
)

data class StrengthSetSourceIdentity(val entryId: Long, val setIndex: Int)

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
    val reasonCodes: List<String> = emptyList(),
    val strengthSetIntent: StrengthSetIntent = StrengthSetIntent.UNKNOWN,
    val strengthExposureAssessment: StrengthExposureAssessment = StrengthExposureAssessment.NOT_APPLICABLE
) {
    val isRealized: Boolean get() = status in setOf(RealizedStimulusStatus.REALIZED, RealizedStimulusStatus.OVERPERFORMED) &&
        authority == RealizedStimulusAuthority.REVIEWED
    val isUnclassified: Boolean get() = status == RealizedStimulusStatus.UNCLASSIFIED ||
        strengthExposureAssessment == StrengthExposureAssessment.UNCERTAIN || authority == RealizedStimulusAuthority.UNCLASSIFIED

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
                strengthExposureAssessment = StrengthExposureAssessment.NOT_STRENGTH,
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
    val curve: ResolvedRepetitionCurve? = null,
    val strengthSetIntentEvidence: StrengthSetIntentEvidence = StrengthSetIntentEvidence()
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
            it.exerciseStableKey == stableKey &&
                LocalDate.parse(it.sessionDate).isBefore(date) &&
                it.baselineEstablishedAfter
        }
        return if (exact != null) {
            exact.priorLogMean.let(::exp).takeIf { it.isFinite() && it > 0.0 }
        } else {
            row?.posteriorLogMean?.let(::exp)?.takeIf { it.isFinite() && it > 0.0 }
        }
    }
}

object RealizedStimulusClassifier {
    fun classify(input: RealizedStimulusInput): RealizedStimulusClassification {
        val plannedEvidence = input.strengthSetIntentEvidence
        val exactStrengthOwnerLink = plannedEvidence.selectionRole == CANONICAL_STRENGTH_SELECTION_ROLE &&
            !plannedEvidence.sourceProgramStableKey.isNullOrBlank() && !plannedEvidence.sourceItemId.isNullOrBlank()
        val strengthIntent = if (plannedEvidence.intent == StrengthSetIntent.PLANNED_STRENGTH && !exactStrengthOwnerLink) {
            // Without the persisted exact owner link, use the unplanned-history evidence rule.
            StrengthSetIntent.UNKNOWN
        } else plannedEvidence.intent
        if (input.reviewedNonRealization) return RealizedStimulusClassification.reviewedNonRealization().copy(
            strengthSetIntent = strengthIntent,
            strengthExposureAssessment = if (strengthIntent == StrengthSetIntent.UNKNOWN) StrengthExposureAssessment.NOT_APPLICABLE
                else StrengthExposureAssessment.NOT_STRENGTH
        )
        if (!input.reviewedIdentity || input.activityKind != PlannedActivityKind.RESISTANCE) {
            return RealizedStimulusClassification.UNCLASSIFIED.copy(reasonCodes = listOf("REVIEWED_RESISTANCE_IDENTITY_REQUIRED"))
        }
        val plannedStrengthReps = input.strengthSetIntentEvidence.plannedReps
        val approvedStrengthIdentity = CanonicalStrengthExposureCapability.strengthPossible(input.stableKey)
        if (strengthIntent == StrengthSetIntent.PLANNED_STRENGTH && approvedStrengthIdentity &&
            plannedStrengthReps?.let { it in 1..6 } == true) {
            if (input.reps <= 0) return reviewedNonStrength(strengthIntent, "INVALID_REALIZED_REPS")
            val excess = input.reps - requireNotNull(plannedStrengthReps)
            if (excess <= 2) {
                return strengthRealization(input, strengthIntent, overperformed = excess > 0, reason = if (excess > 0)
                    "STRENGTH_OVERPERFORMED" else "PLANNED_STRENGTH_REALIZED")
            }
            val (relative, rir) = loadAndEffortEvidence(input)
            if (qualifyingStrengthEvidence(relative, input.rpe, rir)) {
                return strengthRealization(input, strengthIntent, overperformed = true, reason = "STRENGTH_OVERPERFORMED_WITH_LOAD_OR_EFFORT_EVIDENCE")
            }
            if (clearlyLowStrengthEvidence(relative, input.rpe, rir)) {
                return reviewedNonStrength(strengthIntent, "PLANNED_STRENGTH_OVERPERFORMANCE_NOT_STRENGTH_LIKE")
            }
            return uncertainStrength(input, strengthIntent, "PLANNED_STRENGTH_OVERPERFORMANCE_EVIDENCE_UNAVAILABLE")
        }
        if (strengthIntent == StrengthSetIntent.PLANNED_STRENGTH && !approvedStrengthIdentity) {
            return RealizedStimulusClassification.reviewedNonRealization("PLANNED_STRENGTH_IDENTITY_NOT_APPROVED")
        }
        if (strengthIntent == StrengthSetIntent.PLANNED_STRENGTH && plannedStrengthReps?.let { it in 1..6 } != true) {
            return RealizedStimulusClassification.reviewedNonRealization("PLANNED_REPS_OUTSIDE_STRENGTH_BAND")
        }
        if (strengthIntent == StrengthSetIntent.NOT_PLANNED_STRENGTH &&
            (input.reps in 1..6 || plannedEvidence.selectionRole == CANONICAL_STRENGTH_SELECTION_ROLE)) {
            return reviewedNonStrength(strengthIntent, "SET_NOT_PLANNED_AS_STRENGTH")
        }
        if (approvedStrengthIdentity && input.reps in 1..6) {
            val (relative, rir) = loadAndEffortEvidence(input)
            if (qualifyingStrengthEvidence(relative, input.rpe, rir)) {
                return strengthRealization(input, StrengthSetIntent.UNKNOWN, overperformed = false, reason = "UNPLANNED_STRENGTH_EXPOSURE_EVIDENCE_CONFIRMED")
            }
            if (clearlyLowStrengthEvidence(relative, input.rpe, rir)) {
                return RealizedStimulusClassification.reviewedNonRealization("STRENGTH_LOAD_AND_EFFORT_BELOW_EVIDENCE_THRESHOLD")
            }
            return uncertainStrength(input, StrengthSetIntent.UNKNOWN, "STRENGTH_EXPOSURE_LOAD_OR_EFFORT_EVIDENCE_UNAVAILABLE")
        }
        val kind = when {
            input.reps in 1..6 -> {
                if (input.directQualities.any { it in setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY) }) {
                    return RealizedStimulusClassification.reviewedNonRealization("STRENGTH_CAPABILITY_NOT_APPROVED")
                }
                return unclassified("DIRECT_QUALITY_RELATION_REQUIRED")
            }
            input.reps in 7..15 -> RealizedStimulusKind.HYPERTROPHY_LIKE
            else -> return RealizedStimulusClassification.reviewedNonRealization("REPS_OUTSIDE_REVIEWED_STIMULUS_RANGE")
        }
        val requiredQuality = when (kind) {
            RealizedStimulusKind.STRENGTH_LIKE -> TrainableQuality.STRENGTH
            RealizedStimulusKind.HYPERTROPHY_LIKE -> TrainableQuality.HYPERTROPHY
            RealizedStimulusKind.NONE -> return RealizedStimulusClassification.reviewedNonRealization()
        }
        if (requiredQuality !in input.directQualities) return if (input.directQualities.any { it in setOf(TrainableQuality.STRENGTH, TrainableQuality.HYPERTROPHY) }) {
            RealizedStimulusClassification.reviewedNonRealization("DIRECT_QUALITY_RELATION_REQUIRED")
        } else unclassified("DIRECT_QUALITY_RELATION_REQUIRED")
        val load = input.resolvedLoadKg?.takeIf { it.isFinite() && it > 0.0 }
            ?: return unclassified("RESOLVED_LOAD_UNAVAILABLE")
        val reference = input.reference1RmKg?.takeIf { it.isFinite() && it > 0.0 }
        val relative = reference?.let { load / it }
        if (relative != null && !relative.isFinite()) return unclassified("RELATIVE_INTENSITY_UNAVAILABLE")
        if (kind == RealizedStimulusKind.STRENGTH_LIKE && reference == null) {
            return unclassified("INDEPENDENT_REFERENCE_1RM_UNAVAILABLE")
        }
        if (kind == RealizedStimulusKind.STRENGTH_LIKE && relative != null && relative < 0.70) {
            return RealizedStimulusClassification.reviewedNonRealization("STRENGTH_LOAD_BELOW_70_PERCENT_REFERENCE_1RM")
        }
        val rir = input.impliedRir ?: relative?.let { relativeIntensity -> input.curve?.let { curve ->
            curve.profile.invert(relativeIntensity).repetitions?.let { failureReps ->
                floor((failureReps - input.reps).coerceAtLeast(0.0))
            }
        } }
        val effortSatisfied = when {
            input.rpe?.isFinite() == true -> input.rpe >= if (kind == RealizedStimulusKind.STRENGTH_LIKE) 6.0 else 7.0
            rir?.isFinite() == true -> rir <= if (kind == RealizedStimulusKind.STRENGTH_LIKE) 4.0 else 3.0
            else -> null
        }
        if (effortSatisfied == false) return RealizedStimulusClassification.reviewedNonRealization("EFFORT_THRESHOLD_NOT_MET")
        if (effortSatisfied == null) return unclassified("EFFORT_AUTHORITY_UNAVAILABLE")
        if (kind == RealizedStimulusKind.HYPERTROPHY_LIKE && reference == null && input.rpe?.isFinite() != true) {
            return unclassified("INDEPENDENT_REFERENCE_1RM_OR_RPE_REQUIRED")
        }
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

    private fun strengthRealization(
        input: RealizedStimulusInput,
        intent: StrengthSetIntent,
        overperformed: Boolean,
        reason: String
    ): RealizedStimulusClassification {
        val reference = input.reference1RmKg?.takeIf { it.isFinite() && it > 0.0 }
        val load = input.resolvedLoadKg?.takeIf { it.isFinite() && it > 0.0 }
        val relative = if (load != null && reference != null) load / reference else null
        val status = if (overperformed) RealizedStimulusStatus.OVERPERFORMED else RealizedStimulusStatus.REALIZED
        val assessment = if (overperformed) StrengthExposureAssessment.OVERPERFORMED else StrengthExposureAssessment.REALIZED
        return RealizedStimulusClassification(
            kind = RealizedStimulusKind.STRENGTH_LIKE,
            status = status,
            authority = RealizedStimulusAuthority.REVIEWED,
            resolvedLoadKg = load,
            reference1RmKg = reference,
            relativeIntensity = relative?.takeIf(Double::isFinite),
            observedRpe = reliableRpe(input.rpe),
            impliedRir = reliableRir(input.impliedRir),
            reasonCodes = listOf(reason),
            strengthSetIntent = intent,
            strengthExposureAssessment = assessment
        )
    }

    private fun uncertainStrength(
        input: RealizedStimulusInput,
        intent: StrengthSetIntent,
        reason: String
    ) = RealizedStimulusClassification(
        kind = RealizedStimulusKind.NONE,
        status = RealizedStimulusStatus.UNCLASSIFIED,
        authority = RealizedStimulusAuthority.REVIEWED,
        reasonCodes = listOf(reason),
        strengthSetIntent = intent,
        strengthExposureAssessment = StrengthExposureAssessment.UNCERTAIN
    )

    private fun loadAndEffortEvidence(input: RealizedStimulusInput): Pair<Double?, Double?> {
        val load = input.resolvedLoadKg?.takeIf { it.isFinite() && it > 0.0 }
        val reference = input.reference1RmKg?.takeIf { it.isFinite() && it > 0.0 }
        val relative = if (load != null && reference != null) (load / reference).takeIf(Double::isFinite) else null
        val rir = reliableRir(input.impliedRir) ?: relative?.let { intensity -> input.curve?.let { curve ->
            curve.profile.invert(intensity).repetitions?.let { failureReps ->
                floor((failureReps - input.reps).coerceAtLeast(0.0))
            }
        } }
        return relative to rir
    }

    private fun qualifyingStrengthEvidence(relative: Double?, rpe: Double?, rir: Double?): Boolean =
        relative?.let { it >= 0.70 } == true || reliableRpe(rpe)?.let { it >= 6.0 } == true ||
            reliableRir(rir)?.let { it <= 4.0 } == true

    private fun clearlyLowStrengthEvidence(relative: Double?, rpe: Double?, rir: Double?): Boolean =
        relative?.let { it < 0.70 } == true &&
            ((reliableRpe(rpe)?.let { it < 6.0 } == true) ||
                (reliableRir(rir)?.let { it > 4.0 } == true))

    private fun reliableRpe(value: Double?): Double? = value?.takeIf { it.isFinite() && it in 1.0..10.0 }

    private fun reliableRir(value: Double?): Double? = value?.takeIf { it.isFinite() && it >= 0.0 }

    private fun unclassified(reason: String) = RealizedStimulusClassification.UNCLASSIFIED.copy(reasonCodes = listOf(reason))

    private fun reviewedNonStrength(intent: StrengthSetIntent, reason: String) =
        RealizedStimulusClassification.reviewedNonRealization(reason).copy(
            strengthSetIntent = intent,
            strengthExposureAssessment = StrengthExposureAssessment.NOT_STRENGTH
        )
}

internal fun RealizedStimulusClassification.toLegacyClass(): RealizedStimulusClass = when (kind) {
    RealizedStimulusKind.STRENGTH_LIKE -> if (isRealized) RealizedStimulusClass.STRENGTH_LIKE else RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS
    RealizedStimulusKind.HYPERTROPHY_LIKE -> if (isRealized) RealizedStimulusClass.HYPERTROPHY_LIKE else RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS
    RealizedStimulusKind.NONE -> RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS
}

/** Planned Strength credit requires an exact Strength-owned plan, not only a low-rep approved exercise. */
internal fun plannedTargetSetStimulusClass(
    stableKey: String,
    reps: Int,
    selectionRole: String,
    targetQuality: TrainableQuality
): RealizedStimulusClass = when (targetQuality) {
    TrainableQuality.STRENGTH -> if (
        selectionRole == CANONICAL_STRENGTH_SELECTION_ROLE &&
        CanonicalStrengthExposureCapability.strengthExposureEligible(stableKey, reps)
    ) RealizedStimulusClass.STRENGTH_LIKE else RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS
    TrainableQuality.HYPERTROPHY -> if (selectionRole == CANONICAL_STRENGTH_SELECTION_ROLE) {
        RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS
    } else provisionalRealizedStimulusClass(reps)
    else -> RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS
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
        row.sourceEntryId?.let { entryId -> it.source.entryId == entryId && it.source.setIndex == row.setIndex } == true &&
            (row.sourceSetId == null || row.sourceSetId <= 0L || it.source.setId == row.sourceSetId)
    }?.realizedStimulusClassification ?: stimulusExposureLedger.setObservations.firstOrNull {
        row.sourceEntryId == null && row.sourceSetId == null &&
            it.source.stableKey == row.stableKey && it.source.date == row.date && it.source.setIndex == row.setIndex
    }?.realizedStimulusClassification ?: RealizedStimulusClassification.UNCLASSIFIED

internal fun PlanningHistorySnapshot.reviewedSourceAuthority(row: PlanningSetRecord): StimulusClassificationAuthority =
    stimulusExposureLedger.setObservations.firstOrNull {
        row.sourceEntryId?.let { entryId -> it.source.entryId == entryId && it.source.setIndex == row.setIndex } == true &&
            (row.sourceSetId == null || row.sourceSetId <= 0L || it.source.setId == row.sourceSetId)
    }?.classificationAuthority ?: stimulusExposureLedger.setObservations.firstOrNull {
        row.sourceEntryId == null && row.sourceSetId == null &&
            it.source.stableKey == row.stableKey && it.source.date == row.date && it.source.setIndex == row.setIndex
    }?.classificationAuthority ?: StimulusClassificationAuthority.UNCLASSIFIED

internal fun PlanningHistorySnapshot.historyRealizedKind(row: PlanningSetRecord): RealizedStimulusKind =
    if (stimulusExposureLedger.setObservations.isEmpty()) {
        val needsEvidenceAwareStrengthRule = row.strengthSetIntentEvidence.intent == StrengthSetIntent.PLANNED_STRENGTH ||
            row.strengthSetIntentEvidence.intent == StrengthSetIntent.NOT_PLANNED_STRENGTH ||
            CanonicalStrengthExposureCapability.strengthPossible(row.stableKey)
        val legacy = if (needsEvidenceAwareStrengthRule) classifyPlanningHistorySet(this, row).toLegacyClass()
            else provisionalRealizedStimulusClass(row)
        when (legacy) {
            RealizedStimulusClass.STRENGTH_LIKE -> RealizedStimulusKind.STRENGTH_LIKE
            RealizedStimulusClass.HYPERTROPHY_LIKE -> RealizedStimulusKind.HYPERTROPHY_LIKE
            RealizedStimulusClass.AMBIGUOUS_REALIZED_STIMULUS -> RealizedStimulusKind.NONE
        }
    } else reviewedRealization(row).kind

internal fun classifyPlanningHistorySet(snapshot: PlanningHistorySnapshot, row: PlanningSetRecord): RealizedStimulusClassification {
    val stableKey = row.stableKey
    val semantics = snapshot.strengthPerformanceRegistry?.directTarget(stableKey)?.loadSemantics ?: StrengthLoadSemantics.EXTERNAL_LOAD
    val load = row.weightKg.takeIf { it.isFinite() && it > 0.0 && semantics.rawLoadIsResolvedMechanicalLoad }
    val reference = CanonicalStrengthReferenceIndex(snapshot.strengthPerformanceHistory)
        .reference1RmKg(stableKey, row.date)
    val directQualities = if (CanonicalStrengthExposureCapability.strengthPossible(stableKey))
        setOf(TrainableQuality.STRENGTH) else emptySet()
    val intent = row.strengthSetIntentEvidence
    return RealizedStimulusClassifier.classify(
        RealizedStimulusInput(
            stableKey = stableKey,
            date = row.date,
            activityKind = snapshot.activityKind(stableKey),
            reps = row.reps,
            resolvedLoadKg = load,
            rpe = row.rpe,
            directQualities = directQualities,
            reviewedIdentity = stableKey in snapshot.exercises,
            reference1RmKg = reference,
            loadSemantics = semantics,
            strengthSetIntentEvidence = intent
        )
    )
}

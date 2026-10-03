package com.training.trackplanner.data.personalized

import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics
import com.training.trackplanner.analysis.strengthperformance.curve.CurveMatchLevel
import com.training.trackplanner.analysis.strengthperformance.curve.RepetitionCurveEvaluationStatus
import com.training.trackplanner.data.TrainableQuality
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.floor
import org.json.JSONArray
import org.json.JSONObject

internal const val CANONICAL_STRENGTH_SELECTION_ROLE = "CANONICAL_STIMULUS_QUALITY_STRENGTH"

enum class StrengthCapacityEvidenceTier {
    DIRECT_PERSONAL_STRENGTH,
    PERSONAL_CURVE_INFERRED,
    SAME_OWNER_E1RM_INFERRED,
    WEAK_INSUFFICIENT
}

enum class StrengthTrainingLoadConfidence { HIGH, MEDIUM, LOW }

enum class StrengthTrainingLoadUnavailableReason {
    WRONG_QUALITY,
    B4_DOSE_AUTHORITY_UNAVAILABLE,
    EMPTY_EXISTING_SET_DEMAND,
    OWNER_ROLE_NOT_CANONICAL_STRENGTH,
    OWNER_LOAD_SEMANTICS_UNRESOLVED,
    EXACT_OWNER_STRENGTH_SIGNAL_MISSING,
    REFERENCE_DATE_UNAVAILABLE,
    CAPACITY_MEDIAN_UNAVAILABLE,
    CAPACITY_VARIANCE_UNAVAILABLE,
    CAPACITY_REFERENCE_NOT_ESTABLISHED,
    CAPACITY_REFERENCE_STALE,
    INSUFFICIENT_TWO_SIDED_REFERENCE,
    B5_DIRECT_TARGET_SELECTION_UNAVAILABLE,
    NO_SAME_OWNER_REP_PATTERN,
    EFFORT_EVIDENCE_INSUFFICIENT,
    REPEATED_EVIDENCE_INSUFFICIENT,
    EXERCISE_CURVE_UNAVAILABLE,
    REP_RANGE_UNSUPPORTED,
    CONTRADICTORY_CAPACITY_EVIDENCE,
    NO_SAFE_LOAD_ABOVE_STRENGTH_FLOOR,
    TRAINING_LOAD_EXCEEDS_CAPACITY_CEILING
}

/** Exact canonical owner-local reference. This is a capacity estimate, never a workout load. */
data class StrengthCapacityReference(
    val owner: StimulusPrescriptionOwnerIdentity,
    val estimated1RmKg: Double,
    val lower80Kg: Double,
    val upper80Kg: Double,
    val posteriorLogVariance: Double,
    val referenceDate: java.time.LocalDate,
    val posteriorObservationCount: Int,
    val twoSidedObservationCount: Int,
    val loadSemantics: StrengthLoadSemantics,
    val curveProfileId: String,
    val curveMatchLevel: CurveMatchLevel,
    val curveAssignmentVersion: String,
    val evidenceIds: List<String>
) {
    init {
        require(estimated1RmKg.isFinite() && estimated1RmKg > 0.0)
        require(lower80Kg.isFinite() && lower80Kg > 0.0 && upper80Kg.isFinite() && upper80Kg >= lower80Kg)
        require(posteriorLogVariance.isFinite() && posteriorLogVariance > 0.0)
        require(posteriorObservationCount > 0 && twoSidedObservationCount in 0..posteriorObservationCount)
    }
}

enum class StrengthRepMaxCeilingMeaning { CAPACITY_CEILING_ONLY_NOT_TRAINING_LOAD }

/** A population/personally adjusted estimate of upper capacity at a repetition count. */
data class StrengthRepMaxCeiling(
    val owner: StimulusPrescriptionOwnerIdentity,
    val repetitions: Int,
    val medianKg: Double,
    val lower80Kg: Double,
    val upper80Kg: Double,
    val meaning: StrengthRepMaxCeilingMeaning = StrengthRepMaxCeilingMeaning.CAPACITY_CEILING_ONLY_NOT_TRAINING_LOAD
) {
    init {
        require(repetitions in 1..12)
        require(medianKg.isFinite() && medianKg > 0.0)
        require(lower80Kg.isFinite() && lower80Kg > 0.0 && upper80Kg.isFinite() && upper80Kg >= lower80Kg)
    }
}

/** Explicit representation of a completed calibration set; observed effort is not an RM label. */
data class StrengthTrainingCalibrationEvidence(
    val plannedProposal: StrengthTrainingLoadProposal,
    val evidenceId: String,
    val date: java.time.LocalDate,
    val performedRepetitions: Int,
    val performedLoadKg: Double,
    val observedRpe: Double?
) {
    init {
        require(performedRepetitions > 0)
        require(performedLoadKg.isFinite() && performedLoadKg > 0.0)
        require(observedRpe == null || observedRpe.isFinite() && observedRpe in 1.0..10.0)
    }

    val owner: StimulusPrescriptionOwnerIdentity get() = plannedProposal.owner
}

data class StrengthTrainingSourceObservation(
    val evidenceId: String,
    val date: java.time.LocalDate,
    val sessionId: String,
    val reps: Int,
    val rawLoadKg: Double,
    val resolvedLoadKg: Double?,
    val observedRpe: Double?,
    val activityKind: PlannedActivityKind,
    val classificationAuthority: StimulusClassificationAuthority,
    val realizedKind: RealizedStimulusKind,
    val realizationStatus: RealizedStimulusStatus,
    val realizationReasonCodes: List<String>
)

enum class StrengthTrainingLoadDerivation {
    SAME_REP_PERSONAL_RIR_ADJUSTMENT,
    HIGHER_REP_PERSONAL_CURVE_BRIDGE,
    EXACT_REFERENCE_CONSERVATIVE_CALIBRATION
}

data class StrengthTrainingLoadProposal(
    val owner: StimulusPrescriptionOwnerIdentity,
    val quality: TrainableQuality = TrainableQuality.STRENGTH,
    val targetRepetitions: Int,
    /** Preserved from the already-authorized B4/B5 material demand; C14 does not choose it. */
    val setCount: Int,
    val trainingLoadKg: Double,
    val targetRpe: Double,
    val targetRirMean: Double,
    val capacityReference: StrengthCapacityReference,
    val repMaxCeiling: StrengthRepMaxCeiling,
    val derivation: StrengthTrainingLoadDerivation,
    val confidence: StrengthTrainingLoadConfidence,
    val roundingIncrementKg: Double,
    val safetyCapKg: Double,
    val safetyCapReasons: List<String>,
    val evidenceIds: List<String>,
    val reasonCodes: List<String>
) {
    init {
        require(quality == TrainableQuality.STRENGTH)
        require(targetRepetitions in 1..6)
        require(setCount > 0)
        require(capacityReference.owner == owner && repMaxCeiling.owner == owner)
        require(repMaxCeiling.repetitions == targetRepetitions)
        require(trainingLoadKg.isFinite() && trainingLoadKg > 0.0)
        require(targetRpe.isFinite())
        require(targetRirMean.isFinite() && targetRirMean >= 0.0)
        require(trainingLoadKg < repMaxCeiling.medianKg)
        require(trainingLoadKg <= safetyCapKg)
    }
}

data class StrengthTrainingLoadResolution(
    val owner: StimulusPrescriptionOwnerIdentity,
    val evidenceTier: StrengthCapacityEvidenceTier,
    val ownerLoadSemantics: StrengthLoadSemantics? = null,
    val proposal: StrengthTrainingLoadProposal? = null,
    val capacityReference: StrengthCapacityReference? = null,
    val targetRepetitions: Int? = null,
    val candidateSessionCount: Int = 0,
    val candidateObservationCount: Int = 0,
    val excludedMissingRpeCount: Int = 0,
    val ownerLocalObservations: List<StrengthTrainingSourceObservation> = emptyList(),
    val unavailableReasons: List<StrengthTrainingLoadUnavailableReason> = emptyList()
) {
    val available: Boolean get() = proposal != null
}

private data class SelectedStrengthEvidence(
    val tier: StrengthCapacityEvidenceTier,
    val targetRepetitions: Int,
    val rows: List<StimulusSetObservation>,
    val derivation: StrengthTrainingLoadDerivation
)

/**
 * C14 shadow resolver. It only sees B4/B5 target data, owner-local posterior/history, and the
 * existing curve/RIR models. It has no CONTROL input and does not grant materialization authority.
 */
class StrengthTrainingLoadAuthorityResolver {
    fun resolve(
        snapshot: PlanningHistorySnapshot,
        target: StimulusQualityTarget,
        selectedCandidate: StimulusSelectedCandidate,
        requestedSetCount: Int
    ): StrengthTrainingLoadResolution {
        val owner = StimulusPrescriptionOwnerIdentity(selectedCandidate.stableKey, selectedCandidate.selectionRole)
        val registry = snapshot.strengthPerformanceRegistry
        val semantics = registry?.directTarget(owner.stableKey)?.loadSemantics
            ?: registry?.proxyLoadings(owner.stableKey)?.map { it.loadSemantics }?.distinct()?.singleOrNull()
        val ownerLocalObservations = snapshot.stimulusExposureLedger.setObservations.asSequence()
            .filter { it.source.stableKey == owner.stableKey }
            .filter { !it.source.date.isAfter(snapshot.cutoff) && !it.source.date.isBefore(snapshot.cutoff.minusDays(55)) }
            .sortedWith(compareBy({ it.source.date }, { it.source.sessionStableKey }, { it.source.setIndex ?: Int.MIN_VALUE }, { it.source.sourceObservationId }))
            .map { observation -> StrengthTrainingSourceObservation(
                evidenceId = observation.source.sourceObservationId,
                date = observation.source.date,
                sessionId = observation.source.sessionStableKey,
                reps = observation.reps,
                rawLoadKg = observation.weightKg,
                resolvedLoadKg = observation.realizedStimulusClassification.resolvedLoadKg,
                observedRpe = observation.realizedStimulusClassification.observedRpe,
                activityKind = observation.activityKind,
                classificationAuthority = observation.classificationAuthority,
                realizedKind = observation.realizedStimulusClassification.kind,
                realizationStatus = observation.realizedStimulusClassification.status,
                realizationReasonCodes = observation.realizedStimulusClassification.reasonCodes.sorted()
            ) }
            .toList()
        fun unavailable(
            reason: StrengthTrainingLoadUnavailableReason,
            reference: StrengthCapacityReference? = null,
            reps: Int? = null,
            tier: StrengthCapacityEvidenceTier = StrengthCapacityEvidenceTier.WEAK_INSUFFICIENT,
            sessions: Int = 0,
            observations: Int = 0,
            missingRpe: Int = 0,
            sourceObservations: List<StrengthTrainingSourceObservation> = ownerLocalObservations
        ) = StrengthTrainingLoadResolution(
            owner = owner,
            evidenceTier = tier,
            ownerLoadSemantics = semantics,
            capacityReference = reference,
            targetRepetitions = reps,
            candidateSessionCount = sessions,
            candidateObservationCount = observations,
            excludedMissingRpeCount = missingRpe,
            ownerLocalObservations = sourceObservations,
            unavailableReasons = listOf(reason)
        )

        if (target.quality != TrainableQuality.STRENGTH) return unavailable(StrengthTrainingLoadUnavailableReason.WRONG_QUALITY)
        if (target.numericAuthority !in setOf(
                StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE,
                StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE
            )) return unavailable(StrengthTrainingLoadUnavailableReason.B4_DOSE_AUTHORITY_UNAVAILABLE)
        if (requestedSetCount <= 0) return unavailable(StrengthTrainingLoadUnavailableReason.EMPTY_EXISTING_SET_DEMAND)
        if ("QUALITY:${target.quality.name}" !in selectedCandidate.coveredTargetIds) {
            return unavailable(StrengthTrainingLoadUnavailableReason.B5_DIRECT_TARGET_SELECTION_UNAVAILABLE)
        }
        if (owner.selectionRole != CANONICAL_STRENGTH_SELECTION_ROLE) {
            return unavailable(StrengthTrainingLoadUnavailableReason.OWNER_ROLE_NOT_CANONICAL_STRENGTH)
        }
        // B5 only emits a selected candidate after the canonical physical-quality catalog's
        // exact direct-capability test has passed for every covered target. Require that exact
        // B5 target edge here; an exposure-ledger profile is history diagnostic data and is not
        // the source of B5 direct-eligibility authority.

        if (semantics == null) return unavailable(StrengthTrainingLoadUnavailableReason.OWNER_LOAD_SEMANTICS_UNRESOLVED)

        val signal = snapshot.canonicalStrengthSignals[owner.stableKey]
            ?: return unavailable(StrengthTrainingLoadUnavailableReason.EXACT_OWNER_STRENGTH_SIGNAL_MISSING)
        val referenceDate = signal.referenceDate
            ?: return unavailable(StrengthTrainingLoadUnavailableReason.REFERENCE_DATE_UNAVAILABLE)
        val ageDays = ChronoUnit.DAYS.between(referenceDate, snapshot.cutoff)
        if (ageDays !in 0..55) return unavailable(StrengthTrainingLoadUnavailableReason.CAPACITY_REFERENCE_STALE)
        val median = signal.posteriorMedianKg?.takeIf { it.isFinite() && it > 0.0 }
            ?: return unavailable(StrengthTrainingLoadUnavailableReason.CAPACITY_MEDIAN_UNAVAILABLE)
        val logVariance = signal.posteriorLogVariance?.takeIf { it.isFinite() && it > 0.0 }
            ?: return unavailable(StrengthTrainingLoadUnavailableReason.CAPACITY_VARIANCE_UNAVAILABLE)
        if (!signal.baselineEstablished) return unavailable(StrengthTrainingLoadUnavailableReason.CAPACITY_REFERENCE_NOT_ESTABLISHED)

        val curveRegistry = snapshot.repetitionCurveRegistry
            ?: return unavailable(StrengthTrainingLoadUnavailableReason.EXERCISE_CURVE_UNAVAILABLE)
        val curveSubjectKey = "exercise:${owner.stableKey}"
        val personalTheta = snapshot.strengthPersonalCurveTheta[curveSubjectKey] ?: 0.0
        val curve = curveRegistry.resolve(
            stableKey = owner.stableKey,
            isCustom = snapshot.exercises[owner.stableKey]?.isCustom == true,
            personalTheta = personalTheta
        )
        if (curve.matchLevel == CurveMatchLevel.UNSUPPORTED) {
            return unavailable(StrengthTrainingLoadUnavailableReason.EXERCISE_CURVE_UNAVAILABLE)
        }

        val lower80 = median * exp(-1.2815515655446004 * kotlin.math.sqrt(logVariance))
        val upper80 = median * exp(1.2815515655446004 * kotlin.math.sqrt(logVariance))
        val localRows = snapshot.strengthPerformanceHistory
            .filter { it.exerciseStableKey == owner.stableKey }
            .filter { row -> runCatching { java.time.LocalDate.parse(row.sessionDate) }.getOrNull()?.let { !it.isAfter(snapshot.cutoff) && !it.isBefore(snapshot.cutoff.minusDays(55)) } == true }
            .sortedWith(compareBy({ it.sessionDate }, { it.createdAt }, { it.eventUuid }))
        val sourceEvidenceIds = localRows.map { it.eventUuid }.distinct().sorted()
        val reference = StrengthCapacityReference(
            owner = owner,
            estimated1RmKg = median,
            lower80Kg = lower80,
            upper80Kg = upper80,
            posteriorLogVariance = logVariance,
            referenceDate = referenceDate,
            posteriorObservationCount = signal.observationCount,
            twoSidedObservationCount = signal.twoSidedObservationCount,
            loadSemantics = semantics,
            curveProfileId = curve.profile.id.value,
            curveMatchLevel = curve.matchLevel,
            curveAssignmentVersion = curve.assignmentVersion,
            evidenceIds = sourceEvidenceIds
        )

        val sameOwner = snapshot.stimulusExposureLedger.setObservations.asSequence()
            .filter { it.source.stableKey == owner.stableKey }
            .filter { !it.source.date.isAfter(snapshot.cutoff) && !it.source.date.isBefore(snapshot.cutoff.minusDays(55)) }
            .filter { it.classificationAuthority == StimulusClassificationAuthority.REVIEWED_CANONICAL }
            .filter { it.realizedStimulusClassification.isRealized }
            .filter { it.activityKind == PlannedActivityKind.RESISTANCE }
            .filter { it.realizedStimulusClassification.resolvedLoadKg?.let { load -> load.isFinite() && load > 0.0 } == true }
            .filter { it.realizedStimulusClassification.reasonCodes.contains("REVIEWED_CANONICAL_REALIZED_STIMULUS") }
            .toList()
        val strengthRows = sameOwner.filter {
            it.reps in 1..6 && it.realizedStimulusClassification.kind == RealizedStimulusKind.STRENGTH_LIKE
        }
        val higherRepRows = sameOwner.filter {
            it.reps in 7..12 && it.realizedStimulusClassification.kind == RealizedStimulusKind.HYPERTROPHY_LIKE
        }
        val eligibleStrengthRows = strengthRows.filter { row ->
            row.realizedStimulusClassification.observedRpe?.let { snapshot.rpeRirPolicy?.resolve(it) != null } == true
        }
        val eligibleHigherRepRows = higherRepRows.filter { row ->
            row.realizedStimulusClassification.observedRpe?.let { snapshot.rpeRirPolicy?.resolve(it) != null } == true
        }
        val missingRpe = (strengthRows.size - eligibleStrengthRows.size) + (higherRepRows.size - eligibleHigherRepRows.size)
        val selectedEvidence = when {
            eligibleStrengthRows.isNotEmpty() -> SelectedStrengthEvidence(
                StrengthCapacityEvidenceTier.DIRECT_PERSONAL_STRENGTH,
                selectedPersonalStrengthReps(eligibleStrengthRows),
                eligibleStrengthRows.filter { it.reps == selectedPersonalStrengthReps(eligibleStrengthRows) },
                StrengthTrainingLoadDerivation.SAME_REP_PERSONAL_RIR_ADJUSTMENT
            )
            eligibleHigherRepRows.isNotEmpty() -> SelectedStrengthEvidence(
                StrengthCapacityEvidenceTier.PERSONAL_CURVE_INFERRED,
                6,
                eligibleHigherRepRows,
                StrengthTrainingLoadDerivation.HIGHER_REP_PERSONAL_CURVE_BRIDGE
            )
            higherRepRows.isNotEmpty() && signal.observationCount >= 2 && signal.twoSidedObservationCount >= 2 -> SelectedStrengthEvidence(
                StrengthCapacityEvidenceTier.SAME_OWNER_E1RM_INFERRED,
                6,
                emptyList(),
                StrengthTrainingLoadDerivation.EXACT_REFERENCE_CONSERVATIVE_CALIBRATION
            )
            else -> return unavailable(
                if (missingRpe > 0) StrengthTrainingLoadUnavailableReason.EFFORT_EVIDENCE_INSUFFICIENT
                else if (signal.observationCount < 2 || signal.twoSidedObservationCount < 2) StrengthTrainingLoadUnavailableReason.INSUFFICIENT_TWO_SIDED_REFERENCE
                else StrengthTrainingLoadUnavailableReason.NO_SAME_OWNER_REP_PATTERN,
                reference = reference,
                missingRpe = missingRpe
            )
        }
        val tier = selectedEvidence.tier
        val targetReps = selectedEvidence.targetRepetitions
        val evidenceRows = selectedEvidence.rows
        val derivation = selectedEvidence.derivation
        if (signal.observationCount < 1 || signal.twoSidedObservationCount < 1) {
            return unavailable(StrengthTrainingLoadUnavailableReason.INSUFFICIENT_TWO_SIDED_REFERENCE, reference, targetReps, tier)
        }
        if (!signal.baselineEstablished) {
            return unavailable(StrengthTrainingLoadUnavailableReason.CAPACITY_REFERENCE_NOT_ESTABLISHED, reference, targetReps, tier)
        }
        val capacityCurve = curve.evaluate(targetReps.toDouble())
        if (capacityCurve.status != RepetitionCurveEvaluationStatus.SUPPORTED) {
            return unavailable(StrengthTrainingLoadUnavailableReason.REP_RANGE_UNSUPPORTED, reference, targetReps, tier)
        }
        val capacityFraction = capacityCurve.relativeLoad ?: return unavailable(
            StrengthTrainingLoadUnavailableReason.REP_RANGE_UNSUPPORTED, reference, targetReps, tier
        )
        val ceiling = StrengthRepMaxCeiling(
            owner = owner,
            repetitions = targetReps,
            medianKg = median * capacityFraction,
            lower80Kg = lower80 * capacityFraction,
            upper80Kg = upper80 * capacityFraction
        )
        val rirPolicy = snapshot.rpeRirPolicy
            ?: return unavailable(StrengthTrainingLoadUnavailableReason.EFFORT_EVIDENCE_INSUFFICIENT, reference, targetReps, tier)
        val targetRpe = 6.5
        val targetRir = rirPolicy.resolve(targetRpe)
            ?: return unavailable(StrengthTrainingLoadUnavailableReason.EFFORT_EVIDENCE_INSUFFICIENT, reference, targetReps, tier)

        val exactCurve = curve.matchLevel in setOf(CurveMatchLevel.EXACT_EXERCISE, CurveMatchLevel.VALIDATED_VARIATION_FAMILY)
        val observedRirQuantile = if (exactCurve) 0.20 else 0.10
        val targetRirQuantile = if (exactCurve) 0.80 else 0.95
        val targetRirValue = weightedRirQuantile(targetRir, targetRirQuantile)
        val targetWorkCurve = curve.evaluate(targetReps + targetRirValue.toDouble())
        if (targetWorkCurve.status != RepetitionCurveEvaluationStatus.SUPPORTED) {
            return unavailable(StrengthTrainingLoadUnavailableReason.REP_RANGE_UNSUPPORTED, reference, targetReps, tier)
        }
        val targetWorkFraction = targetWorkCurve.relativeLoad ?: return unavailable(
            StrengthTrainingLoadUnavailableReason.REP_RANGE_UNSUPPORTED, reference, targetReps, tier
        )

        var candidateSessionCount = 0
        var candidateObservationCount = 0
        var evidenceIds = emptyList<String>()
        var directHistoryCap: Double? = null
        val candidateLoads = if (evidenceRows.isNotEmpty()) {
            val grouped = evidenceRows.groupBy { it.source.date to it.source.sessionStableKey }
            candidateSessionCount = grouped.size
            candidateObservationCount = evidenceRows.size
            if (candidateSessionCount < MIN_DIRECT_SESSIONS) {
                return unavailable(
                    StrengthTrainingLoadUnavailableReason.REPEATED_EVIDENCE_INSUFFICIENT,
                    reference, targetReps, tier, candidateSessionCount, candidateObservationCount, missingRpe
                )
            }
            evidenceIds = evidenceRows.map { it.source.sourceObservationId }.distinct().sorted()
            directHistoryCap = if (derivation == StrengthTrainingLoadDerivation.SAME_REP_PERSONAL_RIR_ADJUSTMENT) {
                evidenceRows.mapNotNull { it.realizedStimulusClassification.resolvedLoadKg }.minOrNull()
            } else null
            val sessionCapacities = grouped.toSortedMap(compareBy<Pair<java.time.LocalDate, String>>({ it.first }, { it.second }))
                .mapNotNull { (_, rows) ->
                    val loads = rows.mapNotNull { row ->
                        val observedRpe = row.realizedStimulusClassification.observedRpe ?: return@mapNotNull null
                        val observedRir = rirPolicy.resolve(observedRpe) ?: return@mapNotNull null
                        val conservativeObservedRir = weightedRirQuantile(observedRir, observedRirQuantile)
                        val observedWorkCurve = curve.evaluate(row.reps + conservativeObservedRir.toDouble())
                        val observedFraction = observedWorkCurve.relativeLoad ?: return@mapNotNull null
                        val capacityFromSet = checkNotNull(row.realizedStimulusClassification.resolvedLoadKg) / observedFraction
                        if (capacityFromSet < lower80 || capacityFromSet > upper80) return@mapNotNull null
                        capacityFromSet * targetWorkFraction
                    }
                    loads.sorted().takeIf { it.isNotEmpty() }?.let { sessionLoads -> sessionLoads[sessionLoads.size / 2] }
                }
            if (sessionCapacities.size < MIN_DIRECT_SESSIONS) {
                return unavailable(
                    StrengthTrainingLoadUnavailableReason.CONTRADICTORY_CAPACITY_EVIDENCE,
                    reference, targetReps, tier, sessionCapacities.size, candidateObservationCount, missingRpe
                )
            }
            sessionCapacities.sorted()
        } else {
            // Reference-only inference is permitted only with multiple proper observations and
            // deliberately uses the lower capacity quantile plus the high-RIR target quantile.
            candidateSessionCount = 0
            candidateObservationCount = 0
            evidenceIds = localRows.map { it.eventUuid }.distinct().sorted()
            listOf(lower80 * targetWorkFraction)
        }
        val conservativeRawLoad = if (candidateLoads.size == 1) candidateLoads.single() else {
            val lowerQuartileIndex = floor((candidateLoads.size - 1) * 0.25).toInt().coerceIn(candidateLoads.indices)
            candidateLoads[lowerQuartileIndex]
        }
        val roundingIncrement = DEFAULT_LOAD_ROUNDING_INCREMENT_KG
        val roundedDown = floor(conservativeRawLoad / roundingIncrement) * roundingIncrement
        val safetyCaps = buildList {
            add(ceiling.medianKg to "BELOW_ESTIMATED_REP_MAX_CEILING")
            directHistoryCap?.let { add(it to "NOT_ABOVE_SAME_REP_PERSONAL_WORKING_LOAD") }
        }
        val safetyCap = safetyCaps.minOf { it.first }
        val finalLoad = minOf(roundedDown, floor(safetyCap / roundingIncrement) * roundingIncrement)
        if (!finalLoad.isFinite() || finalLoad <= 0.0 || finalLoad / median < MIN_STRENGTH_REFERENCE_FRACTION) {
            return unavailable(
                StrengthTrainingLoadUnavailableReason.NO_SAFE_LOAD_ABOVE_STRENGTH_FLOOR,
                reference, targetReps, tier, candidateSessionCount, candidateObservationCount, missingRpe
            )
        }
        if (finalLoad >= ceiling.medianKg) {
            return unavailable(
                StrengthTrainingLoadUnavailableReason.TRAINING_LOAD_EXCEEDS_CAPACITY_CEILING,
                reference, targetReps, tier, candidateSessionCount, candidateObservationCount, missingRpe
            )
        }
        val confidence = when {
            signal.observationCount >= 5 && signal.twoSidedObservationCount >= 4 && logVariance <= 0.02 && exactCurve -> StrengthTrainingLoadConfidence.HIGH
            signal.observationCount >= 3 && signal.twoSidedObservationCount >= 2 && logVariance <= 0.05 && exactCurve -> StrengthTrainingLoadConfidence.MEDIUM
            else -> StrengthTrainingLoadConfidence.LOW
        }
        val proposal = StrengthTrainingLoadProposal(
            owner = owner,
            targetRepetitions = targetReps,
            setCount = requestedSetCount,
            trainingLoadKg = finalLoad,
            targetRpe = targetRpe,
            targetRirMean = targetRir.expectedRir,
            capacityReference = reference,
            repMaxCeiling = ceiling,
            derivation = derivation,
            confidence = confidence,
            roundingIncrementKg = roundingIncrement,
            safetyCapKg = safetyCap,
            safetyCapReasons = safetyCaps.filter { it.first == safetyCap }.map { it.second }.distinct().sorted(),
            evidenceIds = (evidenceIds + reference.evidenceIds).distinct().sorted(),
            reasonCodes = buildList {
                add("B5_SELECTED_EXACT_DIRECT_STRENGTH_TARGET")
                add("EXACT_STABLE_KEY_CAPACITY_REFERENCE")
                add("CAPACITY_CEILING_IS_NOT_TRAINING_LOAD")
                add("LOW_EFFORT_FIRST_EXPOSURE_RPE_6_5")
                add("CONSERVATIVE_RIR_QUANTILES")
                if (derivation == StrengthTrainingLoadDerivation.SAME_REP_PERSONAL_RIR_ADJUSTMENT) add("DIRECT_PERSONAL_STRENGTH_HISTORY")
                if (targetReps == 6 && derivation != StrengthTrainingLoadDerivation.SAME_REP_PERSONAL_RIR_ADJUSTMENT) {
                    add("BOUNDARY_6_REP_CALIBRATION")
                }
                if (!exactCurve) add("GENERAL_CURVE_PRIOR_WITH_LOW_CONFIDENCE")
                add("ROUNDED_DOWN_TO_EXISTING_HALF_KG_INCREMENT")
            }
        )
        return StrengthTrainingLoadResolution(
            owner = owner,
            evidenceTier = tier,
            ownerLoadSemantics = semantics,
            proposal = proposal,
            capacityReference = reference,
            targetRepetitions = targetReps,
            candidateSessionCount = candidateSessionCount,
            candidateObservationCount = candidateObservationCount,
            excludedMissingRpeCount = missingRpe
        )
    }

    private fun selectedPersonalStrengthReps(rows: List<StimulusSetObservation>): Int = rows
        .groupBy(StimulusSetObservation::reps)
        .map { (reps, candidates) ->
            Triple(reps, candidates.map { it.source.date to it.source.sessionStableKey }.distinct().size,
                candidates.maxOf { it.source.date })
        }
        .sortedWith(compareByDescending<Triple<Int, Int, java.time.LocalDate>> { it.second }
            .thenByDescending { group -> rows.count { it.reps == group.first } }
            .thenByDescending { it.third }
            .thenBy { it.first })
        .first().first

    private fun weightedRirQuantile(
        distribution: com.training.trackplanner.analysis.strengthperformance.ResolvedRirDistribution,
        quantile: Double
    ): Int {
        var cumulative = 0.0
        for (item in distribution.probabilities.sortedBy { it.rir }) {
            cumulative += item.probability
            if (cumulative + 1e-12 >= quantile) return item.rir
        }
        return distribution.probabilities.maxOf { it.rir }
    }

    companion object {
        const val DEFAULT_LOAD_ROUNDING_INCREMENT_KG = 0.5
        const val FIRST_EXPOSURE_TARGET_RPE = 6.5
        private const val MIN_STRENGTH_REFERENCE_FRACTION = 0.70
        private const val MIN_DIRECT_SESSIONS = 2
    }
}

internal fun StrengthTrainingLoadResolution.toCompactJson(): JSONObject = JSONObject()
    .put("owner", JSONObject().put("stableKey", owner.stableKey).put("selectionRole", owner.selectionRole))
    .put("available", available)
    .put("evidenceTier", evidenceTier.name)
    .put("ownerLoadSemantics", ownerLoadSemantics?.name)
    .put("targetRepetitions", targetRepetitions)
    .put("candidateSessionCount", candidateSessionCount)
    .put("candidateObservationCount", candidateObservationCount)
    .put("excludedMissingRpeCount", excludedMissingRpeCount)
    .put("ownerLocalObservations", JSONArray(ownerLocalObservations.map { observation -> JSONObject()
        .put("evidenceId", observation.evidenceId)
        .put("date", observation.date.toString())
        .put("sessionId", observation.sessionId)
        .put("reps", observation.reps)
        .put("rawLoadKg", observation.rawLoadKg)
        .put("resolvedLoadKg", observation.resolvedLoadKg)
        .put("observedRpe", observation.observedRpe)
        .put("activityKind", observation.activityKind.name)
        .put("classificationAuthority", observation.classificationAuthority.name)
        .put("realizedKind", observation.realizedKind.name)
        .put("realizationStatus", observation.realizationStatus.name)
        .put("realizationReasonCodes", JSONArray(observation.realizationReasonCodes))
    }))
    .put("unavailableReasons", JSONArray(unavailableReasons.map { it.name }))
    .put("capacityReference", capacityReference?.let { reference -> JSONObject()
        .put("estimated1RmKg", reference.estimated1RmKg)
        .put("lower80Kg", reference.lower80Kg)
        .put("upper80Kg", reference.upper80Kg)
        .put("posteriorLogVariance", reference.posteriorLogVariance)
        .put("referenceDate", reference.referenceDate.toString())
        .put("posteriorObservationCount", reference.posteriorObservationCount)
        .put("twoSidedObservationCount", reference.twoSidedObservationCount)
        .put("loadSemantics", reference.loadSemantics.name)
        .put("curveProfileId", reference.curveProfileId)
        .put("curveMatchLevel", reference.curveMatchLevel.name)
        .put("curveAssignmentVersion", reference.curveAssignmentVersion)
        .put("evidenceIds", JSONArray(reference.evidenceIds))
    })
    .put("proposal", proposal?.let { value -> JSONObject()
        .put("targetRepetitions", value.targetRepetitions)
        .put("setCount", value.setCount)
        .put("trainingLoadKg", value.trainingLoadKg)
        .put("targetRpe", value.targetRpe)
        .put("targetRirMean", value.targetRirMean)
        .put("capacityCeiling", JSONObject()
            .put("meaning", value.repMaxCeiling.meaning.name)
            .put("repetitions", value.repMaxCeiling.repetitions)
            .put("medianKg", value.repMaxCeiling.medianKg)
            .put("lower80Kg", value.repMaxCeiling.lower80Kg)
            .put("upper80Kg", value.repMaxCeiling.upper80Kg))
        .put("derivation", value.derivation.name)
        .put("confidence", value.confidence.name)
        .put("roundingIncrementKg", value.roundingIncrementKg)
        .put("safetyCapKg", value.safetyCapKg)
        .put("safetyCapReasons", JSONArray(value.safetyCapReasons))
        .put("evidenceIds", JSONArray(value.evidenceIds))
        .put("reasonCodes", JSONArray(value.reasonCodes))
    })

package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.CanonicalStrengthExposureCapability
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.validatedTargetRpeMin
import kotlin.math.round

/** Single governed source of truth for B6 planned Strength/Hypertrophy compatibility. */
class StimulusPlannedPrescriptionResolver {
    fun compatibility(
        quality: TrainableQuality,
        prescription: PlannedPrescription,
        snapshot: PlanningHistorySnapshot,
        stableKey: String
    ): PlannedStimulusCompatibility {
        if (prescription.sets.isEmpty()) return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.UNRESOLVED,
            reasonCodes = listOf("PLANNED_SET_PRESCRIPTION_EMPTY"), authorityRecovery = ExecutionAuthorityResolution(
                ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY,
                ExecutionAuthorityResolutionReason.UNSUPPORTED_PRESCRIPTION_AUTHORITY,
                ExecutionAuthorityReturnTarget.NONE
            ))
        if (quality == TrainableQuality.STRENGTH && !CanonicalStrengthExposureCapability.strengthPossible(stableKey)) {
            return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.INCOMPATIBLE,
                reasonCodes = listOf("STRENGTH_CAPABILITY_NOT_APPROVED"), authorityRecovery = ExecutionAuthorityResolution(
                    ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY,
                    ExecutionAuthorityResolutionReason.UNSUPPORTED_PRESCRIPTION_AUTHORITY,
                    ExecutionAuthorityReturnTarget.NONE
                ))
        }
        val repsCompatible = prescription.sets.all { set -> when (quality) {
            TrainableQuality.STRENGTH -> CanonicalStrengthExposureCapability.strengthExposureEligible(stableKey, set.reps)
            TrainableQuality.HYPERTROPHY -> set.reps in 7..15
            else -> false
        } }
        if (!repsCompatible) return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.INCOMPATIBLE,
            reasonCodes = listOf("PLANNED_REPS_OUTSIDE_${quality.name}_MODEL"), authorityRecovery = ExecutionAuthorityResolution(
                ExecutionAuthorityResolutionStatus.NO_SUPPORTED_AUTHORITY,
                ExecutionAuthorityResolutionReason.UNSUPPORTED_PRESCRIPTION_AUTHORITY,
                ExecutionAuthorityReturnTarget.NONE
            ))
        if (quality == TrainableQuality.STRENGTH && prescription.sets.all {
                it.loadState == com.training.trackplanner.data.ProgramLoadState.USER_CALIBRATION_REQUIRED &&
                    it.weightKg == 0.0 && it.seconds == 0 &&
                    it.targetRpeMin.validatedTargetRpeMin()?.let { rpe -> rpe >= 6.0 } == true
            }) return PlannedStimulusCompatibility(
            quality,
            PlannedStimulusCompatibilityStatus.COMPATIBLE_REQUIRES_USER_LOAD_INPUT,
            reasonCodes = listOf("LOAD_INTENTIONALLY_REQUIRES_USER_CALIBRATION"),
            authorityRecovery = ExecutionAuthorityResolution(
                ExecutionAuthorityResolutionStatus.USER_INPUT_REQUIRED,
                ExecutionAuthorityResolutionReason.RESISTANCE_LOAD_UNAVAILABLE,
                ExecutionAuthorityReturnTarget.EXPLICIT_USER_INPUT
            )
        )
        val canonicalReference = snapshot.canonicalStrengthSignals[stableKey]
            ?.takeIf { quality != TrainableQuality.STRENGTH || it.observationCount >= 2 }
            ?.posteriorMedianKg
            ?.takeIf { it.isFinite() && it > 0.0 }
        val reviewedHistory = if (quality == TrainableQuality.STRENGTH) {
            latestTargetCompatiblePersonalSet(quality, stableKey, snapshot, requireReviewedAuthority = true)
        } else null
        val exactReviewedHistory = reviewedHistory?.takeIf { history ->
            prescription.weightSource == "TARGET_COMPATIBLE_PERSONAL_STRENGTH_HISTORY" &&
                prescription.sets.all { set ->
                    set.reps == history.record.reps && set.weightKg == history.resolvedLoadKg && set.seconds == history.record.seconds
                }
        }
        val reference = canonicalReference ?: exactReviewedHistory?.reference1RmKg
        val loads = prescription.sets.map { it.weightKg }
        if (quality == TrainableQuality.STRENGTH) {
            if (reference == null) return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.UNRESOLVED,
                reasonCodes = listOf("CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE"), authorityRecovery = ExecutionAuthorityResolution(
                    ExecutionAuthorityResolutionStatus.NEEDS_REFERENCE_RESOLUTION,
                    ExecutionAuthorityResolutionReason.CANONICAL_REFERENCE_UNAVAILABLE,
                    ExecutionAuthorityReturnTarget.HISTORY_REFERENCE_RESOLUTION
                ))
            val relative = loads.filter { it.isFinite() && it > 0.0 }.minOrNull()?.div(reference)
                ?: return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.UNRESOLVED,
                    reference1RmKg = reference, reasonCodes = listOf("PLANNED_LOAD_UNAVAILABLE"), authorityRecovery = ExecutionAuthorityResolution(
                        ExecutionAuthorityResolutionStatus.NEEDS_LOAD_INPUT,
                        ExecutionAuthorityResolutionReason.RESISTANCE_LOAD_UNAVAILABLE,
                        ExecutionAuthorityReturnTarget.EXPLICIT_USER_INPUT
                    ))
            return PlannedStimulusCompatibility(quality,
                if (relative >= .70) PlannedStimulusCompatibilityStatus.COMPATIBLE_CONDITIONAL_ON_EFFORT
                else PlannedStimulusCompatibilityStatus.INCOMPATIBLE,
                reference1RmKg = reference, relativeIntensity = relative,
                reasonCodes = if (relative < .70) listOf("PLANNED_LOAD_BELOW_70_PERCENT_REFERENCE_1RM") else emptyList())
        }
        // A provisional zero is a planner display fallback, not an exercise-local mechanical
        // load authority.  B6.1 must keep it unresolved so B6.2 cannot fund a fabricated set.
        val validLoad = loads.all { it.isFinite() && it > 0.0 }
        if (!validLoad) return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.UNRESOLVED,
            reasonCodes = listOf("PLANNED_RESISTANCE_LOAD_UNAVAILABLE"), authorityRecovery = ExecutionAuthorityResolution(
                ExecutionAuthorityResolutionStatus.NEEDS_LOAD_INPUT,
                ExecutionAuthorityResolutionReason.RESISTANCE_LOAD_UNAVAILABLE,
                ExecutionAuthorityReturnTarget.EXPLICIT_USER_INPUT
            ))
        return PlannedStimulusCompatibility(quality,
            PlannedStimulusCompatibilityStatus.COMPATIBLE_CONDITIONAL_ON_EFFORT, reference1RmKg = reference)
    }

    fun safeProposal(
        quality: TrainableQuality,
        current: PlannedPrescription,
        snapshot: PlanningHistorySnapshot,
        stableKey: String,
        effort: StimulusEffortTarget
    ): StimulusTargetCompatiblePrescription? {
        val count = current.sets.size
        if (count == 0) return null
        return when (quality) {
            TrainableQuality.STRENGTH -> {
                if (!CanonicalStrengthExposureCapability.strengthPossible(stableKey)) return null
                val reference = snapshot.canonicalStrengthSignals[stableKey]
                    ?.takeIf { it.observationCount >= 2 }
                    ?.posteriorMedianKg
                    ?.takeIf { it.isFinite() && it > 0.0 } ?: return null
                val load = current.sets.map { it.weightKg }.filter { it.isFinite() && it > 0.0 }.minOrNull() ?: return null
                if (load / reference < .70) return null
                StimulusTargetCompatiblePrescription(
                    sets = List(count) { index -> ProgramSetPrescription(index + 1, 5, round(load * 2) / 2, 0) },
                    effortTarget = effort, numericAuthority = "SAFE_CANONICAL_STRENGTH_LOAD",
                    source = "B6_SHADOW_STRENGTH_RESOLUTION"
                )
            }
            TrainableQuality.HYPERTROPHY -> {
                val loads = current.sets.map { it.weightKg }
                if (loads.any { !it.isFinite() || it <= 0.0 }) return null
                StimulusTargetCompatiblePrescription(
                    // Keep every exercise-local load, rest and timed field. Only bring reps into
                    // the already-governed 7..15 Hypertrophy realization band.
                    sets = current.sets.map { set -> set.copy(reps = set.reps.coerceIn(7, 15), targetRpeMin = effort.minimumRpe.validatedTargetRpeMin()) },
                    effortTarget = effort, numericAuthority = "SAFE_CANONICAL_HYPERTROPHY_LOAD",
                    source = "B6_SHADOW_HYPERTROPHY_RESOLUTION"
                )
            }
            else -> null
        }
    }
}

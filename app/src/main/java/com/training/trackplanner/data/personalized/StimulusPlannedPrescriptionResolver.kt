package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
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
            reasonCodes = listOf("PLANNED_SET_PRESCRIPTION_EMPTY"))
        val repsCompatible = prescription.sets.all { set -> when (quality) {
            TrainableQuality.STRENGTH -> set.reps in 1..6
            TrainableQuality.HYPERTROPHY -> set.reps in 7..15
            else -> false
        } }
        if (!repsCompatible) return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.INCOMPATIBLE,
            reasonCodes = listOf("PLANNED_REPS_OUTSIDE_${quality.name}_MODEL"))
        val reference = snapshot.canonicalStrengthSignals[stableKey]?.posteriorMedianKg
            ?.takeIf { it.isFinite() && it > 0.0 }
        val loads = prescription.sets.map { it.weightKg }
        if (quality == TrainableQuality.STRENGTH) {
            if (reference == null) return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.UNRESOLVED,
                reasonCodes = listOf("CANONICAL_POSTERIOR_REFERENCE_UNAVAILABLE"))
            val relative = loads.filter { it.isFinite() && it > 0.0 }.minOrNull()?.div(reference)
                ?: return PlannedStimulusCompatibility(quality, PlannedStimulusCompatibilityStatus.UNRESOLVED,
                    reference1RmKg = reference, reasonCodes = listOf("PLANNED_LOAD_UNAVAILABLE"))
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
            reasonCodes = listOf("PLANNED_RESISTANCE_LOAD_UNAVAILABLE"))
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
                val reference = snapshot.canonicalStrengthSignals[stableKey]?.posteriorMedianKg
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
                    sets = current.sets.map { set -> set.copy(reps = set.reps.coerceIn(7, 15)) },
                    effortTarget = effort, numericAuthority = "SAFE_CANONICAL_HYPERTROPHY_LOAD",
                    source = "B6_SHADOW_HYPERTROPHY_RESOLUTION"
                )
            }
            else -> null
        }
    }
}

package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.data.CanonicalStrengthExposureCapability

/** One exact, exercise-local personal set selected by the shared target-quality policy. */
internal data class TargetCompatiblePersonalSet(
    val record: PlanningSetRecord,
    val resolvedLoadKg: Double?,
    val reference1RmKg: Double? = null,
    val relativeIntensity: Double? = null
)

/**
 * Shared target-compatible history policy for regional prescription resolution and canonical
 * B6. The B6 caller requires reviewed ledger evidence; the regional shadow path may run without a
 * ledger but still uses the shared evidence-aware Strength exposure classifier.
 */
internal fun latestTargetCompatiblePersonalSet(
    quality: TrainableQuality,
    stableKey: String,
    snapshot: PlanningHistorySnapshot,
    requireReviewedAuthority: Boolean
): TargetCompatiblePersonalSet? = snapshot.allConfirmedSets.asSequence()
    .filter { it.stableKey == stableKey }
    .mapNotNull { row ->
        // Hypertrophy shape history is reusable only inside the approved 7–15 range.
        // A logged low effort is contrary evidence even if the repetitions look plausible.
        if (quality == TrainableQuality.HYPERTROPHY && (
                row.reps !in RegionalColdStartDosePolicy.HYPERTROPHY_PERSONAL_REPS_MIN..
                    RegionalColdStartDosePolicy.HYPERTROPHY_PERSONAL_REPS_MAX ||
                    row.rpe?.let { !it.isFinite() || it < RegionalColdStartDosePolicy.HYPERTROPHY_MINIMUM_TARGET_RPE } == true
            )) return@mapNotNull null
        if (requireReviewedAuthority) {
            val realization = snapshot.reviewedRealization(row)
            val expectedKind = when (quality) {
                TrainableQuality.STRENGTH -> RealizedStimulusKind.STRENGTH_LIKE
                TrainableQuality.HYPERTROPHY -> RealizedStimulusKind.HYPERTROPHY_LIKE
                else -> return@mapNotNull null
            }
            val load = realization.resolvedLoadKg?.takeIf { it.isFinite() && it > 0.0 }
            if (!realization.isRealized || realization.kind != expectedKind || load == null) null
            else TargetCompatiblePersonalSet(row, load, realization.reference1RmKg, realization.relativeIntensity)
        } else {
            val compatible = when (quality) {
                TrainableQuality.STRENGTH -> snapshot.historyRealizedKind(row) == RealizedStimulusKind.STRENGTH_LIKE &&
                    row.weightKg.isFinite() && row.weightKg > 0.0
                TrainableQuality.HYPERTROPHY -> if (snapshot.stimulusExposureLedger.setObservations.isNotEmpty()) {
                    val reviewed = snapshot.reviewedRealization(row)
                    reviewed.isRealized && reviewed.kind == RealizedStimulusKind.HYPERTROPHY_LIKE
                } else {
                    snapshot.historyRealizedKind(row) == RealizedStimulusKind.HYPERTROPHY_LIKE
                }
                else -> false
            }
            when {
                !compatible -> null
                quality == TrainableQuality.HYPERTROPHY && row.weightKg.isFinite() && row.weightKg > 0.0 ->
                    TargetCompatiblePersonalSet(row, row.weightKg)
                // A reviewed successful rep pattern may still guide shape when its load
                // was not recorded. It never turns 0 kg into resistance-load authority;
                // the prescription builder keeps the user-calibration state typed.
                quality == TrainableQuality.HYPERTROPHY -> TargetCompatiblePersonalSet(row, null)
                row.weightKg.isFinite() && row.weightKg > 0.0 -> TargetCompatiblePersonalSet(row, row.weightKg)
                else -> null
            }
        }
    }
    .maxWithOrNull(compareBy<TargetCompatiblePersonalSet> { it.record.date }.thenBy { it.record.setIndex })

/** Builds the exact target-compatible prescription without exceeding the supplied B5 demand. */
internal fun targetCompatiblePersonalHistoryPrescription(
    quality: TrainableQuality,
    item: PlannedExercise,
    snapshot: PlanningHistorySnapshot,
    requestedSets: Int,
    restSeconds: Int,
    requireReviewedAuthority: Boolean,
    weightSourceOverride: String? = null
): PlannedPrescription? {
    if (requestedSets <= 0) return null
    val compatible = latestTargetCompatiblePersonalSet(quality, item.stableKey, snapshot, requireReviewedAuthority)
        ?: return null
    val row = compatible.record
    val isHypertrophy = quality == TrainableQuality.HYPERTROPHY
    val label = if (isHypertrophy) "hypertrophy" else "strength"
    val source = weightSourceOverride ?: if (isHypertrophy) "TARGET_COMPATIBLE_PERSONAL_HYPERTROPHY_HISTORY"
        else "TARGET_COMPATIBLE_PERSONAL_STRENGTH_HISTORY"
    val hasLoadAuthority = compatible.resolvedLoadKg?.let { it.isFinite() && it > 0.0 } == true
    return PlannedPrescription(
        text = "Target-compatible $label personal history",
        sets = List(requestedSets) { index ->
            ProgramSetPrescription(
                setIndex = index + 1,
                reps = row.reps,
                weightKg = compatible.resolvedLoadKg ?: 0.0,
                seconds = row.seconds,
                targetRpeMin = if (isHypertrophy) 7.0 else null,
                loadState = if (isHypertrophy && !hasLoadAuthority)
                    com.training.trackplanner.data.ProgramLoadState.USER_CALIBRATION_REQUIRED
                else com.training.trackplanner.data.ProgramLoadState.EXPLICIT_LOAD
            )
        },
        restSeconds = restSeconds,
        weightSource = if (isHypertrophy && !hasLoadAuthority)
            "PERSONAL_SUCCESSFUL_REP_SHAPE_USER_CALIBRATION_REQUIRED"
        else source
    )
}

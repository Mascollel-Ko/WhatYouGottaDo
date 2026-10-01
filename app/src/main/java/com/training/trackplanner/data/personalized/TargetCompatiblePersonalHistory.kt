package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality

/** One exact, exercise-local personal set selected by the shared target-quality policy. */
internal data class TargetCompatiblePersonalSet(
    val record: PlanningSetRecord,
    val resolvedLoadKg: Double,
    val reference1RmKg: Double? = null,
    val relativeIntensity: Double? = null
)

/**
 * Shared target-compatible history policy for regional prescription resolution and canonical
 * B6. The B6 caller requires reviewed ledger evidence; the older regional shadow path keeps its
 * documented legacy projection when a fixture has no ledger.
 */
internal fun latestTargetCompatiblePersonalSet(
    quality: TrainableQuality,
    stableKey: String,
    snapshot: PlanningHistorySnapshot,
    requireReviewedAuthority: Boolean
): TargetCompatiblePersonalSet? = snapshot.allConfirmedSets.asSequence()
    .filter { it.stableKey == stableKey }
    .mapNotNull { row ->
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
                TrainableQuality.STRENGTH -> row.reps in 1..6 && row.weightKg.isFinite() && row.weightKg > 0.0
                TrainableQuality.HYPERTROPHY -> snapshot.historyRealizedKind(row) == RealizedStimulusKind.HYPERTROPHY_LIKE
                else -> false
            }
            if (compatible && row.weightKg.isFinite() && row.weightKg >= 0.0) {
                TargetCompatiblePersonalSet(row, row.weightKg)
            } else null
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
    return PlannedPrescription(
        text = "Target-compatible $label personal history",
        sets = List(requestedSets) { index ->
            ProgramSetPrescription(
                setIndex = index + 1,
                reps = row.reps,
                weightKg = compatible.resolvedLoadKg,
                seconds = row.seconds,
                targetRpeMin = if (isHypertrophy) 7.0 else null
            )
        },
        restSeconds = restSeconds,
        weightSource = source
    )
}

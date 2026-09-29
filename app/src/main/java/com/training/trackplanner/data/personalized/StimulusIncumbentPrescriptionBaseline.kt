package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.GeneratedProgramSkeleton

/** One finalized builder row projected to the exact B6 owner and prescription fields. */
internal data class StimulusFinalizedPrescriptionRow(
    val owner: StimulusPrescriptionOwnerIdentity,
    val prescription: PlannedPrescription
)

/** Exact current-prescription table used by B6, with no dependency on a generated program. */
internal data class StimulusIncumbentPrescriptionBaseline(
    val prescriptions: Map<StimulusPrescriptionOwnerIdentity, PlannedPrescription>
) {
    companion object {
        /**
         * Preserve the legacy `distinct().toList().toMap()` behavior, including row order,
         * exact duplicate removal, and last distinct prescription winning for a repeated owner.
         */
        fun fromFinalizedRows(rows: Iterable<StimulusFinalizedPrescriptionRow>): StimulusIncumbentPrescriptionBaseline =
            StimulusIncumbentPrescriptionBaseline(
                rows.asSequence()
                    .map { it.owner to it.prescription }
                    .distinct()
                    .toList()
                    .toMap()
            )

        /** Compatibility projection for injected-control tests and migration parity only. */
        fun fromControl(control: GeneratedProgramSkeleton): StimulusIncumbentPrescriptionBaseline =
            fromFinalizedRows(control.items.map { item ->
                StimulusFinalizedPrescriptionRow(
                    StimulusPrescriptionOwnerIdentity(item.exerciseStableKey, item.selectionRole),
                    PlannedPrescription(item.prescription, item.setPrescriptions, item.restSeconds, item.weightSource)
                )
            })
    }
}

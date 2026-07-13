package com.training.trackplanner.analysis.tissue

import java.time.LocalDate

class TissueExposureShadowPipeline(
    private val profiles: List<TissueLoadProfile>,
    private val dimensionWeights: Map<String, TissueProfileDimensionWeight>,
    private val modifierRules: List<TissueModifierRule> = emptyList(),
    private val allowNonProductionFixtures: Boolean = false
) {
    fun calculate(records: List<TissueWorkoutRecord>, targetDate: LocalDate): TissueShadowResult {
        val profilesByStableKey = profiles.groupBy(TissueLoadProfile::stableKey)
        val exposures = records.flatMap { record ->
            val matching = profilesByStableKey[record.exercise.stableKey].orEmpty()
            if (matching.isEmpty()) emptyList()
            else TissueExposureCalculator.calculate(
                record,
                matching,
                dimensionWeights,
                modifierRules,
                allowNonProductionFixtures
            )
        }
        val baseSnapshot = TissueWindowedExposureCalculator.snapshot(exposures, targetDate)
        val unknownProfiles = records.filter { it.exercise.stableKey !in profilesByStableKey }.map {
            TissueMetadataGap(it.entry.id, it.exercise.stableKey, "No exact stable-key tissue profile exists.")
        }
        return TissueShadowResult(
            exposures = exposures.sortedWith(compareBy<RecordTissueExposure>({ it.date }, { it.recordId })
                .thenBy { it.tissueLoadKey.tissueClass.name }
                .thenBy { it.tissueLoadKey.tissueId }
                .thenBy { it.tissueLoadKey.loadDimension.name }),
            snapshot = baseSnapshot.copy(
                incompleteMetadata = (baseSnapshot.incompleteMetadata + unknownProfiles)
                    .sortedWith(compareBy<TissueMetadataGap>(TissueMetadataGap::recordId)
                        .thenBy(TissueMetadataGap::stableKey))
            )
        )
    }
}

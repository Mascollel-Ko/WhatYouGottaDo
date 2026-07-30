package com.training.trackplanner.analysis.contracts

object AnalysisContractShadowParity {
    fun compare(
        oldOracle: AnalysisContractRepository,
        newRelations: AnalysisContractRepository
    ): List<AnalysisContractDiff> {
        val oldByKey = oldOracle.all().associateBy(ExerciseAnalysisRelations::exerciseStableKey)
        val newByKey = newRelations.all().associateBy(ExerciseAnalysisRelations::exerciseStableKey)
        val keys = (oldByKey.keys + newByKey.keys).sorted()
        return buildList {
            keys.forEach { stableKey ->
                val old = oldByKey[stableKey]
                val new = newByKey[stableKey]
                if (old == null || new == null) {
                    add(
                        AnalysisContractDiff(
                            stableKey,
                            AnalysisTypeId.OFI,
                            "exerciseStableKey",
                            old?.exerciseStableKey.orEmpty(),
                            new?.exerciseStableKey.orEmpty()
                        )
                    )
                    return@forEach
                }
                compareField(stableKey, AnalysisTypeId.OFI, "capability", old.capability(AnalysisTypeId.OFI), new.capability(AnalysisTypeId.OFI))
                compareField(stableKey, AnalysisTypeId.OFI, "doseProfile", old.ofiDoseProfile, new.ofiDoseProfile)
                compareField(stableKey, AnalysisTypeId.OFI, "axisContributions", old.ofiAxisContributions, new.ofiAxisContributions)
                compareField(stableKey, AnalysisTypeId.OFI, "comparisonGroups", old.ofiComparisonGroups, new.ofiComparisonGroups)
                compareField(stableKey, AnalysisTypeId.OFI, "goldenSnapshot", old.ofiGoldenSnapshot, new.ofiGoldenSnapshot)
                compareField(
                    stableKey,
                    AnalysisTypeId.PROGRAM_GENERATION,
                    "capability",
                    old.capability(AnalysisTypeId.PROGRAM_GENERATION),
                    new.capability(AnalysisTypeId.PROGRAM_GENERATION)
                )
                compareField(stableKey, AnalysisTypeId.PROGRAM_GENERATION, "slotCapabilities", old.programSlotCapabilities, new.programSlotCapabilities)
                compareField(stableKey, AnalysisTypeId.PROGRAM_GENERATION, "roleEligibility", old.programRoleEligibility, new.programRoleEligibility)
                compareField(stableKey, AnalysisTypeId.PROGRAM_GENERATION, "variantGroups", old.variantGroups, new.variantGroups)
                compareField(stableKey, AnalysisTypeId.PROGRAM_GENERATION, "progressionGroups", old.progressionGroups, new.progressionGroups)
                compareField(
                    stableKey,
                    AnalysisTypeId.MUSCLE_LOAD,
                    "capability",
                    old.capability(AnalysisTypeId.MUSCLE_LOAD),
                    new.capability(AnalysisTypeId.MUSCLE_LOAD)
                )
                compareField(stableKey, AnalysisTypeId.MUSCLE_LOAD, "contributions", old.muscleContributions, new.muscleContributions)
                compareField(
                    stableKey,
                    AnalysisTypeId.BADMINTON_TRANSFER,
                    "capability",
                    old.capability(AnalysisTypeId.BADMINTON_TRANSFER),
                    new.capability(AnalysisTypeId.BADMINTON_TRANSFER)
                )
                compareField(stableKey, AnalysisTypeId.BADMINTON_TRANSFER, "transferPoints", old.badmintonTransferPoints, new.badmintonTransferPoints)
                compareField(stableKey, AnalysisTypeId.BADMINTON_TRANSFER, "fatigueCost", old.badmintonFatigueCost, new.badmintonFatigueCost)
                compareField(stableKey, AnalysisTypeId.BADMINTON_TRANSFER, "physicalQualities", old.physicalQualityPoints, new.physicalQualityPoints)
            }
        }
    }

    private fun ExerciseAnalysisRelations.capability(type: AnalysisTypeId): ExerciseAnalysisCapability? =
        capabilities.singleOrNull { it.analysisTypeId == type }

    private fun MutableList<AnalysisContractDiff>.compareField(
        stableKey: String,
        analysisTypeId: AnalysisTypeId,
        field: String,
        oldValue: Any?,
        newValue: Any?
    ) {
        if (oldValue != newValue) {
            add(AnalysisContractDiff(stableKey, analysisTypeId, field, oldValue.toString(), newValue.toString()))
        }
    }
}

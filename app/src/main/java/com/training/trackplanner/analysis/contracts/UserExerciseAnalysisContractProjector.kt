package com.training.trackplanner.analysis.contracts

import com.training.trackplanner.data.ProgramSlotId
import com.training.trackplanner.data.RuntimeBadmintonTransferLevel
import com.training.trackplanner.data.RuntimeExerciseMetadata

object UserExerciseAnalysisContractProjector {
    fun project(metadata: RuntimeExerciseMetadata): ExerciseAnalysisRelations {
        val programSlot = metadata.programSlot.toEnumOrNull<ProgramSlotId>()
        val badmintonLevel = metadata.transferLevel.toContractLevel()
        return ExerciseAnalysisRelations(
            exerciseStableKey = metadata.stableKey,
            capabilities = listOf(
                capability(metadata, AnalysisTypeId.OFI, AnalysisCapabilityStatus.INCOMPLETE),
                capability(
                    metadata,
                    AnalysisTypeId.PROGRAM_GENERATION,
                    if (programSlot == null) AnalysisCapabilityStatus.INCOMPLETE else AnalysisCapabilityStatus.ENABLED
                ),
                capability(metadata, AnalysisTypeId.MUSCLE_LOAD, AnalysisCapabilityStatus.INCOMPLETE),
                capability(
                    metadata,
                    AnalysisTypeId.BADMINTON_TRANSFER,
                    if (badmintonLevel == ContractBadmintonTransferLevel.NONE &&
                        metadata.badmintonPhysicalQualities.values.isEmpty()
                    ) {
                        AnalysisCapabilityStatus.DISABLED
                    } else {
                        AnalysisCapabilityStatus.ENABLED
                    }
                ),
                capability(metadata, AnalysisTypeId.CONNECTIVE_TISSUE, AnalysisCapabilityStatus.INCOMPLETE)
            ),
            ofiDoseProfile = null,
            ofiAxisContributions = emptyList(),
            ofiComparisonGroups = emptyList(),
            ofiGoldenSnapshot = null,
            programSlotCapabilities = programSlot?.let { slot ->
                listOf(
                    ExerciseProgramSlotCapability(
                        metadata.stableKey,
                        slot.name,
                        ProgramCapabilityRole.PRIMARY,
                        1.0,
                        1.0,
                        AnalysisSourceStatus.USER_PERSISTED_EXACT
                    )
                )
            }.orEmpty(),
            programRoleEligibility = emptyList(),
            variantGroups = emptyList(),
            progressionGroups = metadata.strengthProgressionGroup
                .takeIf { it.isCanonicalValue() }
                ?.let { listOf(ExerciseProgressionGroup(metadata.stableKey, it)) }
                .orEmpty(),
            muscleContributions = emptyList(),
            badmintonTransferPoints = metadata.badmintonTransferType.values.map { domain ->
                ExerciseBadmintonTransferPoint(
                    metadata.stableKey,
                    domain,
                    badmintonLevel,
                    1.0,
                    1.0,
                    AnalysisSourceStatus.USER_PERSISTED_EXACT
                )
            },
            badmintonFatigueCost = null,
            physicalQualityPoints = metadata.badmintonPhysicalQualities.values.map { quality ->
                ExercisePhysicalQualityPoint(
                    metadata.stableKey,
                    quality,
                    1.0,
                    1.0,
                    AnalysisSourceStatus.USER_PERSISTED_EXACT
                )
            },
            movementPatterns = metadata.movementFamily
                .takeIf { it.isCanonicalValue() }
                ?.let { listOf(ExerciseMovementPatternMembership(metadata.stableKey, it)) }
                .orEmpty(),
            jointActions = emptyList(),
            bodyRegions = emptyList(),
            modalities = metadata.activityKind
                .takeIf { it.isCanonicalValue() }
                ?.let { listOf(ExerciseModality(metadata.stableKey, it)) }
                .orEmpty(),
            trainingGoals = emptyList()
        )
    }

    private fun capability(
        metadata: RuntimeExerciseMetadata,
        analysisTypeId: AnalysisTypeId,
        status: AnalysisCapabilityStatus
    ) = ExerciseAnalysisCapability(
        exerciseStableKey = metadata.stableKey,
        analysisTypeId = analysisTypeId,
        status = status,
        confidence = if (status == AnalysisCapabilityStatus.INCOMPLETE) 0.0 else 1.0,
        sourceStatus = if (status == AnalysisCapabilityStatus.INCOMPLETE) {
            AnalysisSourceStatus.UNRESOLVED
        } else {
            AnalysisSourceStatus.USER_PERSISTED_EXACT
        },
        version = AnalysisContractAssetLoader.CONTRACT_VERSION
    )

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
        enumValues<T>().firstOrNull { candidate -> candidate.name == this }

    private fun RuntimeBadmintonTransferLevel.toContractLevel(): ContractBadmintonTransferLevel = when (this) {
        RuntimeBadmintonTransferLevel.DIRECT -> ContractBadmintonTransferLevel.DIRECT
        RuntimeBadmintonTransferLevel.SUPPORTIVE -> ContractBadmintonTransferLevel.SUPPORTIVE
        RuntimeBadmintonTransferLevel.GENERAL -> ContractBadmintonTransferLevel.GENERAL
        RuntimeBadmintonTransferLevel.NONE,
        RuntimeBadmintonTransferLevel.UNKNOWN -> ContractBadmintonTransferLevel.NONE
    }

    private fun String.isCanonicalValue(): Boolean =
        isNotBlank() && this != "NONE" && this != "NOT_APPLICABLE"
}

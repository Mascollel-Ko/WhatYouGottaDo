package com.training.trackplanner.analysis.tissue

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate

data class TissueLoadKey(
    val tissueClass: TissueClass,
    val tissueId: String,
    val loadDimension: TissueLoadDimension,
    val side: TissueSide
)

data class TissueWorkoutRecord(
    val entry: WorkoutEntry,
    val sets: List<WorkoutSet>,
    val exercise: Exercise,
    val bodyWeightKg: Double? = null,
    val performedSide: TissueSide? = null,
    val balancedAlternatingProtocol: Boolean = false,
    val movementFamily: String = exercise.movementPattern,
    val modifierInputs: Map<TissueModifierFamily, String> = emptyMap(),
    val approvedRpeMultiplier: Double? = null
) {
    val date: LocalDate get() = LocalDate.parse(entry.date)

    companion object {
        fun from(
            record: WorkoutEntryWithSets,
            exercise: Exercise,
            bodyWeightKg: Double? = null
        ) = TissueWorkoutRecord(record.entry, record.sets, exercise, bodyWeightKg)
    }
}

data class TissueDoseResolution(
    val resolvedDose: Double?,
    val status: TissueDoseResolutionStatus,
    val rpeAlreadyApplied: Boolean = false,
    val diagnostics: List<String> = emptyList()
)

data class TissueProfileDimensionWeight(
    val profileRowId: String,
    val weight: Double,
    val productionEligibility: Boolean = false,
    val explicitlyNonProductionFixture: Boolean = false
)

data class TissueModifierResolution(
    val adjustedWeight: Double?,
    val calculationStatus: TissueCalculationStatus,
    val appliedModifierIds: List<String> = emptyList(),
    val skippedModifierIds: List<String> = emptyList(),
    val diagnostics: List<String> = emptyList()
)

data class RecordTissueExposure(
    val recordId: Long,
    val date: LocalDate,
    val stableKey: String,
    val tissueLoadKey: TissueLoadKey,
    val resolvedDose: Double?,
    val rawExposure: Double?,
    val adjustedExposure: Double?,
    val calculationStatus: TissueCalculationStatus,
    val doseResolutionStatus: TissueDoseResolutionStatus,
    val sideResolutionStatus: TissueSideResolutionStatus,
    val appliedModifierIds: List<String>,
    val evidenceStatus: TissueEvidenceStatus,
    val confidenceLevel: String,
    val evidenceClaimIds: List<String>,
    val sourceRefs: List<String>,
    val diagnostics: List<String>
)

enum class TissueRecoveryCalculationMode {
    WINDOWED_EXPOSURE,
    EVIDENCE_BACKED_DECAY,
    CALIBRATED_DECAY
}

enum class TissueRecoveryKernelType {
    NONE,
    EXPONENTIAL,
    CUSTOM
}

data class TissueRecoveryProfile(
    val recoveryProfileId: String,
    val tissueClass: TissueClass,
    val tissueId: String,
    val loadDimension: TissueLoadDimension,
    val calculationMode: TissueRecoveryCalculationMode,
    val kernelType: TissueRecoveryKernelType,
    val parameterSet: String,
    val validWindowHours: Int?,
    val evidenceStatus: TissueEvidenceStatus,
    val evidenceClaimIds: List<String>,
    val sourceRefs: List<String>,
    val confidenceLevel: String,
    val productionEligibility: Boolean,
    val humanApprovedBy: String,
    val recoveryNotes: String
)

data class TissueContributionSummary(
    val recordId: Long,
    val date: LocalDate,
    val stableKey: String,
    val exposure: Double
)

enum class TissueMetadataCoverageStatus {
    COMPLETE,
    PARTIAL,
    MISSING,
    BLOCKED
}

data class TissueResidualState(
    val tissueLoadKey: TissueLoadKey,
    val targetDate: LocalDate,
    val residualExposure: Double?,
    val rolling24HourExposure: Double,
    val rolling72HourExposure: Double,
    val rolling7DayExposure: Double,
    val calculationMode: TissueRecoveryCalculationMode,
    val topContributingRecords: List<TissueContributionSummary>,
    val metadataCoverageStatus: TissueMetadataCoverageStatus,
    val confidenceLevel: String,
    val diagnostics: List<String>
)

data class TissueMetadataGap(val recordId: Long, val stableKey: String, val reason: String)
data class TissueInputGap(val recordId: Long, val stableKey: String, val reason: String)
data class TissueSideGap(val recordId: Long, val tissueLoadKey: TissueLoadKey, val reason: String)
data class TissueModifierGap(val recordId: Long, val tissueLoadKey: TissueLoadKey, val reason: String)
data class TissueEvidenceConflict(val recordId: Long, val tissueLoadKey: TissueLoadKey, val reason: String)

data class DailyTissueLoadSnapshot(
    val targetDate: LocalDate,
    val jointLoads: List<TissueResidualState>,
    val tendonLoads: List<TissueResidualState>,
    val ligamentLoads: List<TissueResidualState>,
    val fasciaLoads: List<TissueResidualState>,
    val incompleteMetadata: List<TissueMetadataGap>,
    val missingRecordInputs: List<TissueInputGap>,
    val unresolvedSides: List<TissueSideGap>,
    val unsupportedModifierCombinations: List<TissueModifierGap>,
    val conflictingEvidence: List<TissueEvidenceConflict>
)

data class TissueShadowResult(
    val exposures: List<RecordTissueExposure>,
    val snapshot: DailyTissueLoadSnapshot
)

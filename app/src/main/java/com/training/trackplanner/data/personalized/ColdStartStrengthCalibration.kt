package com.training.trackplanner.data.personalized

import com.training.trackplanner.data.ProgramLoadState
import com.training.trackplanner.data.ProgramSetPrescription
import com.training.trackplanner.data.TrainableQuality
import com.training.trackplanner.analysis.strengthperformance.StrengthLoadSemantics

enum class ColdStartStrengthCalibrationStatus { AVAILABLE, UNAVAILABLE }

enum class ColdStartStrengthCalibrationAuthoritySource { B4_B5_NUMERIC_STRENGTH_DEMAND }

enum class ColdStartStrengthOwnerHistoryStatus { EXACT_OWNER_STRENGTH_SIGNAL_MISSING }

enum class ColdStartStrengthCalibrationReason {
    WRONG_QUALITY,
    B4_DOSE_AUTHORITY_UNAVAILABLE,
    SET_DEMAND_UNAVAILABLE,
    B5_DIRECT_TARGET_SELECTION_UNAVAILABLE,
    OWNER_ROLE_NOT_CANONICAL_STRENGTH,
    OWNER_LOAD_SEMANTICS_UNRESOLVED,
    SAME_OWNER_HISTORY_NOT_ABSENT,
    C14_FAILURE_NOT_EXACT_OWNER_SIGNAL_MISSING
}

/** Authorized shape with deliberately unresolved user-selected load; it is not numeric load authority. */
data class ColdStartStrengthCalibrationProposal(
    val owner: StimulusPrescriptionOwnerIdentity,
    val quality: TrainableQuality,
    val setCount: Int,
    val repetitions: Int,
    val targetRpe: Double,
    val restSeconds: Int,
    val loadState: ProgramLoadState,
    val loadSemantics: StrengthLoadSemantics,
    val ownerHistoryStatus: ColdStartStrengthOwnerHistoryStatus,
    val authoritySource: ColdStartStrengthCalibrationAuthoritySource,
    val reasonCodes: List<String>,
    val sets: List<ProgramSetPrescription>
) {
    init {
        require(quality == TrainableQuality.STRENGTH)
        require(setCount > 0 && sets.size == setCount)
        require(repetitions == 6 && sets.all { it.reps == repetitions })
        require(targetRpe == 6.5 && sets.all { it.targetRpeMin == targetRpe })
        require(restSeconds >= 0)
        require(loadState == ProgramLoadState.USER_CALIBRATION_REQUIRED)
        require(ownerHistoryStatus == ColdStartStrengthOwnerHistoryStatus.EXACT_OWNER_STRENGTH_SIGNAL_MISSING)
        require(sets.all { it.loadState == loadState && it.weightKg == 0.0 })
        require(loadSemantics.rawLoadIsResolvedMechanicalLoad)
        require(authoritySource == ColdStartStrengthCalibrationAuthoritySource.B4_B5_NUMERIC_STRENGTH_DEMAND)
    }
}

data class ColdStartStrengthCalibrationResolution(
    val status: ColdStartStrengthCalibrationStatus,
    val owner: StimulusPrescriptionOwnerIdentity,
    val proposal: ColdStartStrengthCalibrationProposal? = null,
    val unavailableReasons: List<ColdStartStrengthCalibrationReason> = emptyList()
) {
    val available: Boolean get() = status == ColdStartStrengthCalibrationStatus.AVAILABLE && proposal != null
}

/** Creates shape authority only for an exact B5 owner with no usable owner-local C14 signal. */
class ColdStartStrengthCalibrationResolver {
    fun resolve(
        target: StimulusQualityTarget,
        selectedCandidate: StimulusSelectedCandidate,
        b4AuthorizedSetCount: Int,
        currentProbeSetCount: Int,
        restSeconds: Int,
        c14: StrengthTrainingLoadResolution
    ): ColdStartStrengthCalibrationResolution {
        val owner = StimulusPrescriptionOwnerIdentity(selectedCandidate.stableKey, selectedCandidate.selectionRole)
        val reasons = linkedSetOf<ColdStartStrengthCalibrationReason>()
        if (target.quality != TrainableQuality.STRENGTH) reasons += ColdStartStrengthCalibrationReason.WRONG_QUALITY
        if (target.numericAuthority !in setOf(
                StimulusTargetNumericAuthority.PERSONAL_RESTORE_BASELINE,
                StimulusTargetNumericAuthority.PERSONAL_SUCCESSFUL_DOSE
            )) reasons += ColdStartStrengthCalibrationReason.B4_DOSE_AUTHORITY_UNAVAILABLE
        if (b4AuthorizedSetCount <= 0 || currentProbeSetCount != b4AuthorizedSetCount) {
            reasons += ColdStartStrengthCalibrationReason.SET_DEMAND_UNAVAILABLE
        }
        if ("QUALITY:STRENGTH" !in selectedCandidate.coveredTargetIds) {
            reasons += ColdStartStrengthCalibrationReason.B5_DIRECT_TARGET_SELECTION_UNAVAILABLE
        }
        if (selectedCandidate.selectionRole != CANONICAL_STRENGTH_SELECTION_ROLE) {
            reasons += ColdStartStrengthCalibrationReason.OWNER_ROLE_NOT_CANONICAL_STRENGTH
        }
        val semantics = c14.ownerLoadSemantics
        if (semantics == null || !semantics.rawLoadIsResolvedMechanicalLoad) {
            reasons += ColdStartStrengthCalibrationReason.OWNER_LOAD_SEMANTICS_UNRESOLVED
        }
        if (c14.owner != owner || c14.ownerLocalObservations.isNotEmpty() || c14.capacityReference != null) {
            reasons += ColdStartStrengthCalibrationReason.SAME_OWNER_HISTORY_NOT_ABSENT
        }
        if (c14.unavailableReasons != listOf(StrengthTrainingLoadUnavailableReason.EXACT_OWNER_STRENGTH_SIGNAL_MISSING)) {
            reasons += ColdStartStrengthCalibrationReason.C14_FAILURE_NOT_EXACT_OWNER_SIGNAL_MISSING
        }
        if (reasons.isNotEmpty()) {
            return ColdStartStrengthCalibrationResolution(
                status = ColdStartStrengthCalibrationStatus.UNAVAILABLE,
                owner = owner,
                unavailableReasons = reasons.toList()
            )
        }
        val loadState = ProgramLoadState.USER_CALIBRATION_REQUIRED
        val sets = List(b4AuthorizedSetCount) { index ->
            ProgramSetPrescription(
                setIndex = index + 1,
                reps = 6,
                weightKg = 0.0,
                seconds = 0,
                targetRpeMin = 6.5,
                loadState = loadState
            )
        }
        val proposal = ColdStartStrengthCalibrationProposal(
            owner = owner,
            quality = TrainableQuality.STRENGTH,
            setCount = b4AuthorizedSetCount,
            repetitions = 6,
            targetRpe = 6.5,
            restSeconds = restSeconds,
            loadState = loadState,
            loadSemantics = requireNotNull(semantics),
            ownerHistoryStatus = ColdStartStrengthOwnerHistoryStatus.EXACT_OWNER_STRENGTH_SIGNAL_MISSING,
            authoritySource = ColdStartStrengthCalibrationAuthoritySource.B4_B5_NUMERIC_STRENGTH_DEMAND,
            reasonCodes = listOf(
                "COLD_START_EXACT_OWNER_HISTORY_ABSENT",
                "COLD_START_STRENGTH_6_REP_CALIBRATION",
                "COLD_START_TARGET_RPE_ENCODED",
                "COLD_START_LOAD_USER_CALIBRATION_REQUIRED"
            ),
            sets = sets
        )
        return ColdStartStrengthCalibrationResolution(
            status = ColdStartStrengthCalibrationStatus.AVAILABLE,
            owner = owner,
            proposal = proposal
        )
    }
}

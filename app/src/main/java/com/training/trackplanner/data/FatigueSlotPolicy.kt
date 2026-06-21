package com.training.trackplanner.data

import com.training.trackplanner.analysis.fatigue.DailyFatigueState

enum class FatigueSlotGroup {
    STRENGTH_ANCHOR,
    SINGLE_LEG_DECEL,
    FOOTWORK_COD_REACTIVE,
    ATHLETIC_OVERHEAD_PRESS,
    ROTATIONAL_KINETIC_CHAIN,
    TRUNK_STABILITY,
    SCAPULAR_SUPPORT,
    CALF_ANKLE,
    RECOVERY_PREHAB,
    REHAB_LIKE_ACTIVATION,
    ACCESSORY
}

enum class FatigueSlotDisposition {
    NORMAL,
    CONTROLLED,
    LOW_LOAD_ONLY,
    AVOID
}

internal class FatigueSlotPolicy {
    fun gate(state: DailyFatigueState?): ProgramFatigueGate = ProgramFatigueGate.from(state)

    fun adapt(planned: PlannedSlot, gate: ProgramFatigueGate): PlannedSlot = when (gate.band) {
        ProgramFatigueBand.RED -> planned.copy(intensity = ProgramDayIntensity.LIGHT)
        ProgramFatigueBand.ORANGE -> planned.copy(
            intensity = if (planned.intensity == ProgramDayIntensity.HARD) {
                ProgramDayIntensity.MODERATE
            } else {
                planned.intensity
            }
        )
        else -> planned
    }

    fun allows(candidate: ProgramCandidate, gate: ProgramFatigueGate): Boolean {
        if (!gate.allows(candidate)) return false
        val primarySlot = candidate.slotCapabilities.primary.firstOrNull() ?: return true
        return when (disposition(primarySlot, gate.band, candidate.isRehabLikeActivation)) {
            FatigueSlotDisposition.AVOID -> candidate.isRecovery
            FatigueSlotDisposition.LOW_LOAD_ONLY ->
                !candidate.highStress &&
                    !candidate.isHeavyLower &&
                    !candidate.isHighImpact &&
                    !candidate.isHighIntensityCod
            FatigueSlotDisposition.NORMAL,
            FatigueSlotDisposition.CONTROLLED -> true
        }
    }

    fun disposition(
        slot: ProgramSlotId,
        band: ProgramFatigueBand,
        rehabLikeActivation: Boolean = false
    ): FatigueSlotDisposition {
        val group = groupFor(slot, rehabLikeActivation)
        return when (band) {
            ProgramFatigueBand.GREEN -> FatigueSlotDisposition.NORMAL
            ProgramFatigueBand.YELLOW -> when (group) {
                FatigueSlotGroup.FOOTWORK_COD_REACTIVE,
                FatigueSlotGroup.ATHLETIC_OVERHEAD_PRESS,
                FatigueSlotGroup.ROTATIONAL_KINETIC_CHAIN,
                FatigueSlotGroup.SINGLE_LEG_DECEL -> FatigueSlotDisposition.CONTROLLED
                else -> FatigueSlotDisposition.NORMAL
            }
            ProgramFatigueBand.ORANGE -> when (group) {
                FatigueSlotGroup.FOOTWORK_COD_REACTIVE,
                FatigueSlotGroup.ATHLETIC_OVERHEAD_PRESS,
                FatigueSlotGroup.ROTATIONAL_KINETIC_CHAIN,
                FatigueSlotGroup.SINGLE_LEG_DECEL -> FatigueSlotDisposition.LOW_LOAD_ONLY
                FatigueSlotGroup.STRENGTH_ANCHOR,
                FatigueSlotGroup.TRUNK_STABILITY,
                FatigueSlotGroup.SCAPULAR_SUPPORT,
                FatigueSlotGroup.CALF_ANKLE,
                FatigueSlotGroup.ACCESSORY -> FatigueSlotDisposition.CONTROLLED
                FatigueSlotGroup.RECOVERY_PREHAB,
                FatigueSlotGroup.REHAB_LIKE_ACTIVATION -> FatigueSlotDisposition.NORMAL
            }
            ProgramFatigueBand.RED -> when (group) {
                FatigueSlotGroup.FOOTWORK_COD_REACTIVE,
                FatigueSlotGroup.ATHLETIC_OVERHEAD_PRESS,
                FatigueSlotGroup.ROTATIONAL_KINETIC_CHAIN -> FatigueSlotDisposition.AVOID
                FatigueSlotGroup.STRENGTH_ANCHOR,
                FatigueSlotGroup.SINGLE_LEG_DECEL,
                FatigueSlotGroup.TRUNK_STABILITY,
                FatigueSlotGroup.SCAPULAR_SUPPORT,
                FatigueSlotGroup.CALF_ANKLE,
                FatigueSlotGroup.ACCESSORY -> FatigueSlotDisposition.LOW_LOAD_ONLY
                FatigueSlotGroup.RECOVERY_PREHAB,
                FatigueSlotGroup.REHAB_LIKE_ACTIVATION -> FatigueSlotDisposition.NORMAL
            }
        }
    }

    fun groupFor(slot: ProgramSlotId, rehabLikeActivation: Boolean = false): FatigueSlotGroup {
        if (rehabLikeActivation) return FatigueSlotGroup.REHAB_LIKE_ACTIVATION
        return when (slot) {
            ProgramSlotId.LOWER_SQUAT_PATTERN,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN,
            ProgramSlotId.UPPER_PULL_ANCHOR -> FatigueSlotGroup.STRENGTH_ANCHOR
            ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL -> FatigueSlotGroup.SINGLE_LEG_DECEL
            ProgramSlotId.BADMINTON_DECEL_COD,
            ProgramSlotId.BADMINTON_FOOTWORK_REACTION,
            ProgramSlotId.POWER_REACTIVE_LOW_VOLUME -> FatigueSlotGroup.FOOTWORK_COD_REACTIVE
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
            ProgramSlotId.OVERHEAD_SMASH_SUPPORT -> FatigueSlotGroup.ATHLETIC_OVERHEAD_PRESS
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN -> FatigueSlotGroup.ROTATIONAL_KINETIC_CHAIN
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY -> FatigueSlotGroup.TRUNK_STABILITY
            ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT -> FatigueSlotGroup.SCAPULAR_SUPPORT
            ProgramSlotId.CALF_ANKLE_CAPACITY -> FatigueSlotGroup.CALF_ANKLE
            ProgramSlotId.RECOVERY_PREHAB_LIGHT -> FatigueSlotGroup.RECOVERY_PREHAB
            ProgramSlotId.UPPER_PUSH_SUPPORT,
            ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT,
            ProgramSlotId.ACCESSORY_ROTATION -> FatigueSlotGroup.ACCESSORY
        }
    }

    companion object {
        val DEFAULT = FatigueSlotPolicy()
    }
}

internal data class ProgramFatigueGate(
    val band: ProgramFatigueBand,
    val volumeFactor: Double,
    val rpeCap: Int,
    val allowsHeavyLower: Boolean,
    val allowsHighImpact: Boolean,
    val allowsHighIntensityCod: Boolean,
    val lowerBodyRestricted: Boolean
) {
    fun allows(candidate: ProgramCandidate): Boolean {
        if (!allowsHeavyLower && candidate.isHeavyLower) return false
        if (!allowsHighImpact && candidate.isHighImpact) return false
        if (!allowsHighIntensityCod && candidate.isHighIntensityCod) return false
        if (lowerBodyRestricted && candidate.isHeavyLower) return false
        return true
    }

    companion object {
        fun from(state: DailyFatigueState?): ProgramFatigueGate {
            val ofi = state?.overallFatigueIndex ?: 0
            val band = when (ofi) {
                in 0..44 -> ProgramFatigueBand.GREEN
                in 45..59 -> ProgramFatigueBand.YELLOW
                in 60..74 -> ProgramFatigueBand.ORANGE
                else -> ProgramFatigueBand.RED
            }
            val jointRestricted = (state?.jointTendonImpactScore ?: 0) >= 65
            val neuralRestricted = (state?.neuromuscularScore ?: 0) >= 70
            val localRestricted = (state?.localMuscularScore ?: 0) >= 70
            return ProgramFatigueGate(
                band = band,
                volumeFactor = when (band) {
                    ProgramFatigueBand.GREEN -> 1.0
                    ProgramFatigueBand.YELLOW -> 0.75
                    ProgramFatigueBand.ORANGE -> 0.50
                    ProgramFatigueBand.RED -> 0.25
                },
                rpeCap = if (neuralRestricted || band >= ProgramFatigueBand.ORANGE) 7 else 9,
                allowsHeavyLower = band < ProgramFatigueBand.ORANGE && !localRestricted,
                allowsHighImpact = band < ProgramFatigueBand.ORANGE && !jointRestricted,
                allowsHighIntensityCod = band < ProgramFatigueBand.ORANGE,
                lowerBodyRestricted = localRestricted
            )
        }
    }
}

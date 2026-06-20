package com.training.trackplanner.data

enum class ProgramSlotId {
    LOWER_SQUAT_PATTERN,
    HIP_HINGE_POSTERIOR_CHAIN,
    UPPER_PULL_ANCHOR,
    UPPER_PUSH_SUPPORT,
    SINGLE_LEG_STRENGTH_CONTROL,
    TRUNK_ANTI_ROTATION_STABILITY,
    SCAPULAR_SHOULDER_SUPPORT,
    CALF_ANKLE_CAPACITY,
    BADMINTON_DECEL_COD,
    BADMINTON_FOOTWORK_REACTION,
    ATHLETIC_OVERHEAD_PRESS_SUPPORT,
    ROTATIONAL_KINETIC_CHAIN,
    POWER_REACTIVE_LOW_VOLUME,
    RECOVERY_PREHAB_LIGHT,
    ACCESSORY_ROTATION
}

enum class ProgramSlotCategory {
    STRENGTH_ANCHOR,
    STRENGTH_SUPPORT,
    BADMINTON_TRANSFER,
    POWER_REACTIVE,
    RECOVERY_PREHAB,
    ACCESSORY
}

data class ProgramSlotDefinition(
    val id: ProgramSlotId,
    val category: ProgramSlotCategory,
    val isAnchor: Boolean = false,
    val isBadmintonTransfer: Boolean = false,
    val isRecoveryOrPrehab: Boolean = false,
    val isHighImpactOrReactive: Boolean = false,
    val isUmbrella: Boolean = false
)

object ProgramSlotDefinitions {
    private val definitions = ProgramSlotId.entries.associateWith { id ->
        when (id) {
            ProgramSlotId.LOWER_SQUAT_PATTERN,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN,
            ProgramSlotId.UPPER_PULL_ANCHOR -> ProgramSlotDefinition(
                id = id,
                category = ProgramSlotCategory.STRENGTH_ANCHOR,
                isAnchor = true
            )
            ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL,
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY,
            ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT,
            ProgramSlotId.CALF_ANKLE_CAPACITY,
            ProgramSlotId.UPPER_PUSH_SUPPORT,
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN -> ProgramSlotDefinition(
                id = id,
                category = ProgramSlotCategory.STRENGTH_SUPPORT
            )
            ProgramSlotId.BADMINTON_DECEL_COD,
            ProgramSlotId.BADMINTON_FOOTWORK_REACTION -> ProgramSlotDefinition(
                id = id,
                category = ProgramSlotCategory.BADMINTON_TRANSFER,
                isBadmintonTransfer = true,
                isHighImpactOrReactive = id == ProgramSlotId.BADMINTON_DECEL_COD
            )
            ProgramSlotId.POWER_REACTIVE_LOW_VOLUME -> ProgramSlotDefinition(
                id = id,
                category = ProgramSlotCategory.POWER_REACTIVE,
                isBadmintonTransfer = true,
                isHighImpactOrReactive = true
            )
            ProgramSlotId.RECOVERY_PREHAB_LIGHT -> ProgramSlotDefinition(
                id = id,
                category = ProgramSlotCategory.RECOVERY_PREHAB,
                isRecoveryOrPrehab = true
            )
            ProgramSlotId.ACCESSORY_ROTATION -> ProgramSlotDefinition(
                id = id,
                category = ProgramSlotCategory.ACCESSORY,
                isUmbrella = true
            )
        }
    }

    fun get(id: ProgramSlotId): ProgramSlotDefinition = definitions.getValue(id)

    fun primaryFor(slot: ProgramTrainingSlot): ProgramSlotDefinition = get(
        when (slot) {
            ProgramTrainingSlot.LOWER_STRENGTH,
            ProgramTrainingSlot.LOWER_STRENGTH_HEAVY -> ProgramSlotId.LOWER_SQUAT_PATTERN
            ProgramTrainingSlot.LOWER_TRANSFER_FULL -> ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL
            ProgramTrainingSlot.UPPER_STRENGTH,
            ProgramTrainingSlot.UPPER_STRENGTH_SCAP,
            ProgramTrainingSlot.UPPER_SCAP_CORE_FULL -> ProgramSlotId.UPPER_PULL_ANCHOR
            ProgramTrainingSlot.BADMINTON_TRANSFER -> ProgramSlotId.BADMINTON_FOOTWORK_REACTION
            ProgramTrainingSlot.BADMINTON_COD,
            ProgramTrainingSlot.BADMINTON_COD_DECEL -> ProgramSlotId.BADMINTON_DECEL_COD
            ProgramTrainingSlot.POWER_REACTIVE,
            ProgramTrainingSlot.POWER_REACTIVE_LIGHT -> ProgramSlotId.POWER_REACTIVE_LOW_VOLUME
            ProgramTrainingSlot.RECOVERY_PREHAB,
            ProgramTrainingSlot.RECOVERY_WEAKPOINT,
            ProgramTrainingSlot.MICRO_RECOVERY -> ProgramSlotId.RECOVERY_PREHAB_LIGHT
            ProgramTrainingSlot.WEAKPOINT_ACCESSORY -> ProgramSlotId.ACCESSORY_ROTATION
            ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT -> ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY
        }
    )
}

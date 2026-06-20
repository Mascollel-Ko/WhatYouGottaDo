package com.training.trackplanner.data

enum class ExposurePriority {
    HIGH,
    MODERATE_HIGH,
    MODERATE,
    LOW_MODERATE,
    LOW,
    OPTIONAL
}

data class ExposureTarget(
    val slot: ProgramSlotId,
    val priority: ExposurePriority,
    val minimumPerTwoWeeks: Int,
    val preferredPerFourWeeks: Int,
    val maximumPerTwoWeeks: Int? = null
)

/** Qualitative exposure targets. Program selection does not enforce these as hard quotas. */
object ExposureTargetTable {
    private val targets = ProgramSlotId.entries.associateWith { slot ->
        when (slot) {
            ProgramSlotId.LOWER_SQUAT_PATTERN,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN,
            ProgramSlotId.UPPER_PULL_ANCHOR,
            ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY -> ExposureTarget(slot, ExposurePriority.HIGH, 1, 2)
            ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL,
            ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT,
            ProgramSlotId.CALF_ANKLE_CAPACITY -> ExposureTarget(slot, ExposurePriority.MODERATE_HIGH, 1, 2)
            ProgramSlotId.BADMINTON_DECEL_COD,
            ProgramSlotId.BADMINTON_FOOTWORK_REACTION,
            ProgramSlotId.POWER_REACTIVE_LOW_VOLUME -> ExposureTarget(slot, ExposurePriority.MODERATE, 0, 2, 4)
            ProgramSlotId.UPPER_PUSH_SUPPORT,
            ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
            ProgramSlotId.ROTATIONAL_KINETIC_CHAIN -> ExposureTarget(slot, ExposurePriority.LOW_MODERATE, 0, 1)
            ProgramSlotId.RECOVERY_PREHAB_LIGHT -> ExposureTarget(slot, ExposurePriority.LOW, 0, 1)
            ProgramSlotId.ACCESSORY_ROTATION -> ExposureTarget(slot, ExposurePriority.OPTIONAL, 0, 1)
        }
    }

    fun get(slot: ProgramSlotId): ExposureTarget = targets.getValue(slot)

    fun all(): List<ExposureTarget> = ProgramSlotId.entries.map(::get)
}

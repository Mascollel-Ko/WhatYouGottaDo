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
    val priority: ExposurePriority
)

data class NumericExposureTarget(
    val slot: ProgramSlotId,
    val priority: ExposurePriority,
    val availableDaysPerWeek: Int,
    val windowWeeks: Int,
    val minimum: Int,
    val preferred: Int,
    val maximum: Int
)

/** Lenient defaults for validators and future templates, not hard generation quotas. */
object ExposureTargetTable {
    private val targets = ProgramSlotId.entries.associateWith { slot ->
        ExposureTarget(
            slot = slot,
            priority = when (slot) {
                ProgramSlotId.LOWER_SQUAT_PATTERN,
                ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN,
                ProgramSlotId.UPPER_PULL_ANCHOR,
                ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY -> ExposurePriority.HIGH
                ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL,
                ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT,
                ProgramSlotId.CALF_ANKLE_CAPACITY -> ExposurePriority.MODERATE_HIGH
                ProgramSlotId.BADMINTON_DECEL_COD,
                ProgramSlotId.BADMINTON_FOOTWORK_REACTION,
                ProgramSlotId.POWER_REACTIVE_LOW_VOLUME -> ExposurePriority.MODERATE
                ProgramSlotId.UPPER_PUSH_SUPPORT,
                ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
                ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
                ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT -> ExposurePriority.LOW_MODERATE
                ProgramSlotId.RECOVERY_PREHAB_LIGHT -> ExposurePriority.LOW
                ProgramSlotId.OVERHEAD_SMASH_SUPPORT,
                ProgramSlotId.ACCESSORY_ROTATION -> ExposurePriority.OPTIONAL
            }
        )
    }

    fun get(slot: ProgramSlotId): ExposureTarget = targets.getValue(slot)

    fun all(): List<ExposureTarget> = ProgramSlotId.entries.map(::get)

    fun numericTarget(
        slot: ProgramSlotId,
        availableDaysPerWeek: Int,
        windowWeeks: Int
    ): NumericExposureTarget {
        val target = get(slot)
        val days = availableDaysPerWeek.coerceIn(1, 7)
        val weeks = windowWeeks.coerceIn(1, 12)
        val base = baseTarget(target.priority, dayBand(days))
        val twoWeekUnits = (weeks + 1) / 2
        return NumericExposureTarget(
            slot = slot,
            priority = target.priority,
            availableDaysPerWeek = days,
            windowWeeks = weeks,
            minimum = base.minimum * twoWeekUnits,
            preferred = base.preferred * twoWeekUnits,
            maximum = base.maximum * twoWeekUnits
        )
    }

    private fun baseTarget(priority: ExposurePriority, dayBand: DayBand): BaseTarget = when (dayBand) {
        DayBand.LOW -> when (priority) {
            ExposurePriority.HIGH -> BaseTarget(1, 1, 2)
            ExposurePriority.MODERATE_HIGH -> BaseTarget(0, 1, 2)
            ExposurePriority.MODERATE -> BaseTarget(0, 1, 2)
            ExposurePriority.LOW_MODERATE -> BaseTarget(0, 0, 1)
            ExposurePriority.LOW -> BaseTarget(0, 0, 1)
            ExposurePriority.OPTIONAL -> BaseTarget(0, 0, 1)
        }
        DayBand.MEDIUM -> when (priority) {
            ExposurePriority.HIGH -> BaseTarget(1, 2, 4)
            ExposurePriority.MODERATE_HIGH -> BaseTarget(1, 2, 4)
            ExposurePriority.MODERATE -> BaseTarget(0, 2, 3)
            ExposurePriority.LOW_MODERATE -> BaseTarget(0, 1, 2)
            ExposurePriority.LOW -> BaseTarget(0, 1, 2)
            ExposurePriority.OPTIONAL -> BaseTarget(0, 0, 1)
        }
        DayBand.HIGH -> when (priority) {
            ExposurePriority.HIGH -> BaseTarget(2, 3, 6)
            ExposurePriority.MODERATE_HIGH -> BaseTarget(1, 3, 5)
            ExposurePriority.MODERATE -> BaseTarget(1, 2, 4)
            ExposurePriority.LOW_MODERATE -> BaseTarget(0, 2, 3)
            ExposurePriority.LOW -> BaseTarget(0, 1, 2)
            ExposurePriority.OPTIONAL -> BaseTarget(0, 0, 2)
        }
    }

    private fun dayBand(days: Int): DayBand = when (days) {
        in 1..2 -> DayBand.LOW
        in 3..4 -> DayBand.MEDIUM
        else -> DayBand.HIGH
    }

    private enum class DayBand { LOW, MEDIUM, HIGH }

    private data class BaseTarget(
        val minimum: Int,
        val preferred: Int,
        val maximum: Int
    )
}

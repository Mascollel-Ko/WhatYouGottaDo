package com.training.trackplanner.data

internal class ProgramDayIntensityPolicy {
    fun warnings(items: List<ProgramSkeletonItem>, request: ProgramSkeletonRequest): List<String> {
        if (items.isEmpty()) return emptyList()
        val intensities = items.map(ProgramSkeletonItem::dayIntensity).toSet()
        return buildList {
            if (ProgramDayIntensity.HARD.name !in intensities) add("PROGRAM_INTENSITY_NO_HARD_DAY")
            if (request.availableDaysPerWeek >= 3 && ProgramDayIntensity.LIGHT.name !in intensities) {
                add("PROGRAM_INTENSITY_NO_LIGHT_DAY")
            }
            if (hasConsecutiveHighLowerStress(items)) add("PROGRAM_HIGH_LOWER_FATIGUE_CLUSTER")
        }
    }

    private fun hasConsecutiveHighLowerStress(items: List<ProgramSkeletonItem>): Boolean =
        items.groupBy { it.weekNumber to it.dayOfWeek }
            .mapValues { (_, rows) -> rows.any(::isHighLowerStress) }
            .filterValues { it }
            .keys
            .groupBy(Pair<Int, Int>::first)
            .values
            .any { days ->
                days.map(Pair<Int, Int>::second).sorted().zipWithNext().any { (previous, next) ->
                    next - previous <= 1
                }
            }

    private fun isHighLowerStress(item: ProgramSkeletonItem): Boolean {
        val lowerOrCod = item.trainingSlot in HIGH_LOWER_TRAINING_SLOTS ||
            item.requestedTemplateSlot in HIGH_LOWER_SLOT_NAMES ||
            item.primarySlotCapabilities.any(HIGH_LOWER_SLOT_NAMES::contains) ||
            item.secondarySlotCapabilities.any(HIGH_LOWER_SLOT_NAMES::contains)
        val highStress = item.dayIntensity == ProgramDayIntensity.HARD.name ||
            item.stressMagnitudeHint in setOf("HIGH", "VERY_HIGH") ||
            item.neuromuscularStressLevel in setOf("HIGH", "VERY_HIGH") ||
            item.jointTendonImpactStressLevel in setOf("HIGH", "VERY_HIGH")
        return lowerOrCod && highStress
    }

    private companion object {
        val HIGH_LOWER_TRAINING_SLOTS = setOf(
            ProgramTrainingSlot.LOWER_STRENGTH_HEAVY.name,
            ProgramTrainingSlot.BADMINTON_COD.name,
            ProgramTrainingSlot.BADMINTON_COD_DECEL.name,
            ProgramTrainingSlot.POWER_REACTIVE.name
        )
        val HIGH_LOWER_SLOT_NAMES = setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN.name,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN.name,
            ProgramSlotId.BADMINTON_DECEL_COD.name,
            ProgramSlotId.BADMINTON_FOOTWORK_REACTION.name,
            ProgramSlotId.POWER_REACTIVE_LOW_VOLUME.name
        )
    }
}

package com.training.trackplanner.data

internal data class PlannedSlot(
    val dayOfWeek: Int,
    val slot: ProgramTrainingSlot,
    val intensity: ProgramDayIntensity,
    val definition: ProgramSlotDefinition = ProgramSlotDefinitions.primaryFor(slot)
)

internal enum class ProgramExerciseRole { ANCHOR, TRANSFER, SUPPORT, CORE, PREHAB, ACCESSORY }

/** Owns the stable hidden-template structure used by ProgramBuilder. */
internal class ProgramTemplateCatalog {
    fun slots(days: Int): List<PlannedSlot> = when (days.coerceIn(1, 7)) {
        1 -> listOf(slot(3, ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT, ProgramDayIntensity.MODERATE))
        2 -> listOf(
            slot(1, ProgramTrainingSlot.LOWER_TRANSFER_FULL, ProgramDayIntensity.HARD),
            slot(4, ProgramTrainingSlot.UPPER_SCAP_CORE_FULL, ProgramDayIntensity.MODERATE)
        )
        3 -> listOf(
            slot(1, ProgramTrainingSlot.LOWER_STRENGTH, ProgramDayIntensity.HARD),
            slot(3, ProgramTrainingSlot.UPPER_STRENGTH_SCAP, ProgramDayIntensity.MODERATE),
            slot(5, ProgramTrainingSlot.BADMINTON_TRANSFER, ProgramDayIntensity.LIGHT)
        )
        4 -> listOf(
            slot(1, ProgramTrainingSlot.LOWER_STRENGTH, ProgramDayIntensity.HARD),
            slot(3, ProgramTrainingSlot.UPPER_STRENGTH_SCAP, ProgramDayIntensity.MODERATE),
            slot(5, ProgramTrainingSlot.BADMINTON_COD_DECEL, ProgramDayIntensity.MODERATE),
            slot(7, ProgramTrainingSlot.RECOVERY_WEAKPOINT, ProgramDayIntensity.LIGHT)
        )
        5 -> listOf(
            slot(1, ProgramTrainingSlot.LOWER_STRENGTH_HEAVY, ProgramDayIntensity.HARD),
            slot(2, ProgramTrainingSlot.UPPER_STRENGTH_SCAP, ProgramDayIntensity.MODERATE),
            slot(4, ProgramTrainingSlot.BADMINTON_COD_DECEL, ProgramDayIntensity.HARD),
            slot(6, ProgramTrainingSlot.POWER_REACTIVE_LIGHT, ProgramDayIntensity.MODERATE),
            slot(7, ProgramTrainingSlot.RECOVERY_WEAKPOINT, ProgramDayIntensity.LIGHT)
        )
        6 -> listOf(
            slot(1, ProgramTrainingSlot.LOWER_STRENGTH, ProgramDayIntensity.HARD),
            slot(2, ProgramTrainingSlot.UPPER_STRENGTH, ProgramDayIntensity.MODERATE),
            slot(3, ProgramTrainingSlot.RECOVERY_PREHAB, ProgramDayIntensity.LIGHT),
            slot(4, ProgramTrainingSlot.BADMINTON_COD, ProgramDayIntensity.HARD),
            slot(6, ProgramTrainingSlot.POWER_REACTIVE, ProgramDayIntensity.MODERATE),
            slot(7, ProgramTrainingSlot.WEAKPOINT_ACCESSORY, ProgramDayIntensity.LIGHT)
        )
        else -> listOf(
            slot(1, ProgramTrainingSlot.LOWER_STRENGTH, ProgramDayIntensity.HARD),
            slot(2, ProgramTrainingSlot.MICRO_RECOVERY, ProgramDayIntensity.LIGHT),
            slot(3, ProgramTrainingSlot.UPPER_STRENGTH, ProgramDayIntensity.MODERATE),
            slot(4, ProgramTrainingSlot.BADMINTON_COD, ProgramDayIntensity.HARD),
            slot(5, ProgramTrainingSlot.RECOVERY_PREHAB, ProgramDayIntensity.LIGHT),
            slot(6, ProgramTrainingSlot.POWER_REACTIVE, ProgramDayIntensity.MODERATE),
            slot(7, ProgramTrainingSlot.WEAKPOINT_ACCESSORY, ProgramDayIntensity.LIGHT)
        )
    }

    fun weekPlans(weeks: Int, gate: ProgramFatigueGate): List<ProgramWeekPlan> {
        val flow = weekFlow(weeks).toMutableList().apply {
            if (weeks == 8) {
                this[lastIndex] = if (gate.band <= ProgramFatigueBand.YELLOW) {
                    ProgramWeekType.REALIZATION
                } else {
                    ProgramWeekType.DELOAD
                }
            }
            if (weeks == 12) {
                this[lastIndex] = if (gate.band <= ProgramFatigueBand.YELLOW) {
                    ProgramWeekType.REALIZATION
                } else {
                    ProgramWeekType.FINAL_DELOAD
                }
            }
        }
        return flow.mapIndexed { index, type ->
            val settings = settings(type)
            ProgramWeekPlan(
                weekIndex = index + 1,
                weekType = type.name,
                volumeMultiplier = settings.volume,
                intensityMultiplier = settings.intensity,
                heavyExposureLimit = if (type in setOf(ProgramWeekType.DELOAD, ProgramWeekType.FINAL_DELOAD)) 1 else 2,
                lowerBodyFatigueLimit = if (gate.band >= ProgramFatigueBand.ORANGE) 5.0 else 8.0,
                axialLoadLimit = if (gate.allowsHeavyLower) 2 else 0,
                plyometricLimit = if (gate.allowsHighImpact) 1 else 0,
                deloadFlag = type in setOf(ProgramWeekType.DELOAD, ProgramWeekType.FINAL_DELOAD),
                targetRpeMin = settings.rpeMin,
                targetRpeMax = minOf(settings.rpeMax, gate.rpeCap.toDouble())
            )
        }
    }

    fun roles(slot: ProgramTrainingSlot, count: Int): List<ProgramExerciseRole> {
        val template = when (slot) {
            ProgramTrainingSlot.LOWER_STRENGTH,
            ProgramTrainingSlot.LOWER_STRENGTH_HEAVY,
            ProgramTrainingSlot.LOWER_TRANSFER_FULL -> listOf(
                ProgramExerciseRole.ANCHOR,
                ProgramExerciseRole.SUPPORT,
                ProgramExerciseRole.TRANSFER,
                ProgramExerciseRole.CORE,
                ProgramExerciseRole.PREHAB,
                ProgramExerciseRole.ACCESSORY,
                ProgramExerciseRole.SUPPORT
            )
            ProgramTrainingSlot.UPPER_STRENGTH,
            ProgramTrainingSlot.UPPER_STRENGTH_SCAP,
            ProgramTrainingSlot.UPPER_SCAP_CORE_FULL -> listOf(
                ProgramExerciseRole.ANCHOR,
                ProgramExerciseRole.SUPPORT,
                ProgramExerciseRole.CORE,
                ProgramExerciseRole.PREHAB,
                ProgramExerciseRole.ACCESSORY,
                ProgramExerciseRole.SUPPORT,
                ProgramExerciseRole.PREHAB
            )
            ProgramTrainingSlot.BADMINTON_TRANSFER,
            ProgramTrainingSlot.BADMINTON_COD,
            ProgramTrainingSlot.BADMINTON_COD_DECEL,
            ProgramTrainingSlot.POWER_REACTIVE,
            ProgramTrainingSlot.POWER_REACTIVE_LIGHT -> listOf(
                ProgramExerciseRole.TRANSFER,
                ProgramExerciseRole.TRANSFER,
                ProgramExerciseRole.SUPPORT,
                ProgramExerciseRole.CORE,
                ProgramExerciseRole.PREHAB,
                ProgramExerciseRole.ACCESSORY,
                ProgramExerciseRole.PREHAB
            )
            ProgramTrainingSlot.RECOVERY_PREHAB,
            ProgramTrainingSlot.RECOVERY_WEAKPOINT,
            ProgramTrainingSlot.MICRO_RECOVERY -> listOf(
                ProgramExerciseRole.PREHAB,
                ProgramExerciseRole.CORE,
                ProgramExerciseRole.ACCESSORY,
                ProgramExerciseRole.PREHAB,
                ProgramExerciseRole.SUPPORT,
                ProgramExerciseRole.ACCESSORY,
                ProgramExerciseRole.PREHAB
            )
            else -> listOf(
                ProgramExerciseRole.ANCHOR,
                ProgramExerciseRole.TRANSFER,
                ProgramExerciseRole.SUPPORT,
                ProgramExerciseRole.CORE,
                ProgramExerciseRole.PREHAB,
                ProgramExerciseRole.ACCESSORY,
                ProgramExerciseRole.SUPPORT
            )
        }
        return template.take(count.coerceIn(2, 7))
    }

    private fun weekFlow(weeks: Int): List<ProgramWeekType> = listOf(
        ProgramWeekType.ADAPT,
        ProgramWeekType.BUILD,
        ProgramWeekType.HIGH,
        ProgramWeekType.DELOAD,
        ProgramWeekType.REBUILD,
        ProgramWeekType.BUILD_PLUS,
        ProgramWeekType.INTENSIFY,
        ProgramWeekType.DELOAD,
        ProgramWeekType.REBUILD,
        ProgramWeekType.BUILD_PLUS,
        ProgramWeekType.INTENSIFY,
        ProgramWeekType.REALIZATION
    ).take(weeks.coerceIn(2, 12))

    private fun settings(type: ProgramWeekType): WeekSettings = when (type) {
        ProgramWeekType.ADAPT -> WeekSettings(0.80, 0.85, 6.5, 7.5)
        ProgramWeekType.BUILD -> WeekSettings(0.95, 0.92, 7.0, 8.0)
        ProgramWeekType.HIGH -> WeekSettings(1.05, 0.96, 7.5, 8.0)
        ProgramWeekType.DELOAD -> WeekSettings(0.60, 0.72, 5.5, 6.5)
        ProgramWeekType.REBUILD -> WeekSettings(0.85, 0.86, 6.5, 7.5)
        ProgramWeekType.BUILD_PLUS -> WeekSettings(1.00, 0.94, 7.0, 8.0)
        ProgramWeekType.INTENSIFY -> WeekSettings(0.90, 1.00, 8.0, 8.5)
        ProgramWeekType.FINAL_DELOAD -> WeekSettings(0.55, 0.68, 5.5, 6.5)
        ProgramWeekType.REALIZATION -> WeekSettings(0.75, 1.00, 7.5, 8.5)
    }

    private fun slot(day: Int, slot: ProgramTrainingSlot, intensity: ProgramDayIntensity) =
        PlannedSlot(day, slot, intensity)

    private data class WeekSettings(
        val volume: Double,
        val intensity: Double,
        val rpeMin: Double,
        val rpeMax: Double
    )

    companion object {
        val DEFAULT = ProgramTemplateCatalog()
    }
}

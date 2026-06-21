package com.training.trackplanner.data

internal data class TemplateExerciseSlot(
    val targetSlot: ProgramSlotId?,
    val role: ProgramExerciseRole,
    val required: Boolean = false,
    val minimumRepeatGapDays: Int = 0
)

internal data class PlannedSlot(
    val dayOfWeek: Int,
    val slot: ProgramTrainingSlot,
    val intensity: ProgramDayIntensity,
    val definition: ProgramSlotDefinition = ProgramSlotDefinitions.primaryFor(slot),
    val exerciseSlots: List<TemplateExerciseSlot> = emptyList()
)

internal enum class ProgramExerciseRole { ANCHOR, TRANSFER, SUPPORT, CORE, PREHAB, ACCESSORY }

internal enum class HiddenTemplateFocus { STANDARD, FOOTWORK_COD, HYBRID, STRENGTH_BIASED, FALLBACK }

internal data class ProgramTemplateSelection(
    val templateId: String,
    val focus: HiddenTemplateFocus,
    val representative: Boolean,
    val sessions: List<PlannedSlot>
)

/** Owns hidden slot skeletons; exercise identity remains metadata-resolved by ProgramBuilder. */
internal class ProgramTemplateCatalog {
    fun select(request: ProgramSkeletonRequest): ProgramTemplateSelection {
        val ratio = request.badmintonSpecificityRatio
        val definition = representativeTemplates.firstOrNull { template ->
            template.days == request.availableDaysPerWeek &&
                template.weeks == request.durationWeeks &&
                template.badmintonRatio == ratio
        }
        return definition?.selection ?: ProgramTemplateSelection(
            templateId = "POLICY_FALLBACK_${request.availableDaysPerWeek}D",
            focus = HiddenTemplateFocus.FALLBACK,
            representative = false,
            sessions = slots(request.availableDaysPerWeek)
        )
    }

    /** Compatibility schedule for combinations not covered by the first hidden-template catalog. */
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

    fun exerciseSlots(planned: PlannedSlot, count: Int): List<TemplateExerciseSlot> =
        if (planned.exerciseSlots.isNotEmpty()) {
            planned.exerciseSlots.take(count.coerceIn(2, 7))
        } else {
            roles(planned.slot, count).map { role -> TemplateExerciseSlot(null, role) }
        }

    fun exposureTargets(selection: ProgramTemplateSelection, request: ProgramSkeletonRequest): List<NumericExposureTarget> =
        selection.sessions
            .flatMap(PlannedSlot::exerciseSlots)
            .mapNotNull(TemplateExerciseSlot::targetSlot)
            .distinct()
            .map { slot ->
                ExposureTargetTable.numericTarget(
                    slot = slot,
                    availableDaysPerWeek = request.availableDaysPerWeek,
                    windowWeeks = request.durationWeeks
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

    private fun slot(
        day: Int,
        slot: ProgramTrainingSlot,
        intensity: ProgramDayIntensity,
        vararg exerciseSlots: TemplateExerciseSlot
    ) = PlannedSlot(day, slot, intensity, exerciseSlots = exerciseSlots.toList())

    private fun requirement(
        slot: ProgramSlotId,
        role: ProgramExerciseRole,
        required: Boolean = false,
        minimumRepeatGapDays: Int = 0
    ) = TemplateExerciseSlot(slot, role, required, minimumRepeatGapDays)

    private fun template(
        id: String,
        focus: HiddenTemplateFocus,
        days: Int,
        weeks: Int,
        badmintonRatio: Int,
        sessions: List<PlannedSlot>
    ) = RepresentativeTemplate(
        days = days,
        weeks = weeks,
        badmintonRatio = badmintonRatio,
        selection = ProgramTemplateSelection(id, focus, true, sessions)
    )

    private data class WeekSettings(
        val volume: Double,
        val intensity: Double,
        val rpeMin: Double,
        val rpeMax: Double
    )

    private data class RepresentativeTemplate(
        val days: Int,
        val weeks: Int,
        val badmintonRatio: Int,
        val selection: ProgramTemplateSelection
    )

    private val representativeTemplates = listOf(
        template(
            id = "STANDARD_3D_4W_70_30",
            focus = HiddenTemplateFocus.STANDARD,
            days = 3,
            weeks = 4,
            badmintonRatio = 70,
            sessions = listOf(
                slot(1, ProgramTrainingSlot.LOWER_STRENGTH, ProgramDayIntensity.HARD,
                    requirement(ProgramSlotId.LOWER_SQUAT_PATTERN, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY, ProgramExerciseRole.CORE),
                    requirement(ProgramSlotId.CALF_ANKLE_CAPACITY, ProgramExerciseRole.SUPPORT)),
                slot(3, ProgramTrainingSlot.UPPER_STRENGTH_SCAP, ProgramDayIntensity.MODERATE,
                    requirement(ProgramSlotId.UPPER_PULL_ANCHOR, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.UPPER_PUSH_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT, ProgramExerciseRole.ACCESSORY)),
                slot(5, ProgramTrainingSlot.BADMINTON_TRANSFER, ProgramDayIntensity.LIGHT,
                    requirement(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.BADMINTON_FOOTWORK_REACTION, ProgramExerciseRole.TRANSFER),
                    requirement(ProgramSlotId.BADMINTON_DECEL_COD, ProgramExerciseRole.TRANSFER),
                    requirement(ProgramSlotId.POWER_REACTIVE_LOW_VOLUME, ProgramExerciseRole.TRANSFER))
            )
        ),
        template(
            id = "FOOTWORK_COD_4D_4W_80_20",
            focus = HiddenTemplateFocus.FOOTWORK_COD,
            days = 4,
            weeks = 4,
            badmintonRatio = 80,
            sessions = listOf(
                slot(1, ProgramTrainingSlot.LOWER_STRENGTH, ProgramDayIntensity.HARD,
                    requirement(ProgramSlotId.LOWER_SQUAT_PATTERN, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY, ProgramExerciseRole.CORE),
                    requirement(ProgramSlotId.CALF_ANKLE_CAPACITY, ProgramExerciseRole.SUPPORT)),
                slot(3, ProgramTrainingSlot.UPPER_STRENGTH_SCAP, ProgramDayIntensity.MODERATE,
                    requirement(ProgramSlotId.UPPER_PULL_ANCHOR, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT, ProgramExerciseRole.ACCESSORY)),
                slot(5, ProgramTrainingSlot.BADMINTON_COD_DECEL, ProgramDayIntensity.MODERATE,
                    requirement(ProgramSlotId.BADMINTON_DECEL_COD, ProgramExerciseRole.TRANSFER),
                    requirement(ProgramSlotId.BADMINTON_FOOTWORK_REACTION, ProgramExerciseRole.TRANSFER),
                    requirement(ProgramSlotId.CALF_ANKLE_CAPACITY, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY, ProgramExerciseRole.CORE)),
                slot(7, ProgramTrainingSlot.RECOVERY_WEAKPOINT, ProgramDayIntensity.LIGHT,
                    requirement(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.POWER_REACTIVE_LOW_VOLUME, ProgramExerciseRole.TRANSFER),
                    requirement(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.RECOVERY_PREHAB_LIGHT, ProgramExerciseRole.PREHAB))
            )
        ),
        template(
            id = "HYBRID_4D_4W_50_50",
            focus = HiddenTemplateFocus.HYBRID,
            days = 4,
            weeks = 4,
            badmintonRatio = 50,
            sessions = listOf(
                slot(1, ProgramTrainingSlot.LOWER_STRENGTH, ProgramDayIntensity.HARD,
                    requirement(ProgramSlotId.LOWER_SQUAT_PATTERN, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.UPPER_PULL_ANCHOR, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY, ProgramExerciseRole.CORE)),
                slot(3, ProgramTrainingSlot.UPPER_STRENGTH_SCAP, ProgramDayIntensity.MODERATE,
                    requirement(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.UPPER_PUSH_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT, ProgramExerciseRole.ACCESSORY)),
                slot(5, ProgramTrainingSlot.BADMINTON_COD_DECEL, ProgramDayIntensity.MODERATE,
                    requirement(ProgramSlotId.BADMINTON_FOOTWORK_REACTION, ProgramExerciseRole.TRANSFER),
                    requirement(ProgramSlotId.BADMINTON_DECEL_COD, ProgramExerciseRole.TRANSFER),
                    requirement(ProgramSlotId.CALF_ANKLE_CAPACITY, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY, ProgramExerciseRole.CORE)),
                slot(7, ProgramTrainingSlot.WEAKPOINT_ACCESSORY, ProgramDayIntensity.LIGHT,
                    requirement(ProgramSlotId.UPPER_PULL_ANCHOR, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.ROTATIONAL_KINETIC_CHAIN, ProgramExerciseRole.SUPPORT, minimumRepeatGapDays = 14),
                    requirement(ProgramSlotId.RECOVERY_PREHAB_LIGHT, ProgramExerciseRole.PREHAB))
            )
        ),
        template(
            id = "STANDARD_5D_8W_70_30",
            focus = HiddenTemplateFocus.STANDARD,
            days = 5,
            weeks = 8,
            badmintonRatio = 70,
            sessions = listOf(
                slot(1, ProgramTrainingSlot.LOWER_STRENGTH_HEAVY, ProgramDayIntensity.HARD,
                    requirement(ProgramSlotId.LOWER_SQUAT_PATTERN, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY, ProgramExerciseRole.CORE),
                    requirement(ProgramSlotId.CALF_ANKLE_CAPACITY, ProgramExerciseRole.SUPPORT)),
                slot(2, ProgramTrainingSlot.UPPER_STRENGTH_SCAP, ProgramDayIntensity.MODERATE,
                    requirement(ProgramSlotId.UPPER_PULL_ANCHOR, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.UPPER_PUSH_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT, ProgramExerciseRole.ACCESSORY)),
                slot(4, ProgramTrainingSlot.BADMINTON_COD_DECEL, ProgramDayIntensity.HARD,
                    requirement(ProgramSlotId.BADMINTON_DECEL_COD, ProgramExerciseRole.TRANSFER),
                    requirement(ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY, ProgramExerciseRole.CORE),
                    requirement(ProgramSlotId.CALF_ANKLE_CAPACITY, ProgramExerciseRole.SUPPORT)),
                slot(6, ProgramTrainingSlot.POWER_REACTIVE_LIGHT, ProgramDayIntensity.MODERATE,
                    requirement(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.POWER_REACTIVE_LOW_VOLUME, ProgramExerciseRole.TRANSFER),
                    requirement(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.ROTATIONAL_KINETIC_CHAIN, ProgramExerciseRole.SUPPORT, minimumRepeatGapDays = 14)),
                slot(7, ProgramTrainingSlot.RECOVERY_WEAKPOINT, ProgramDayIntensity.LIGHT,
                    requirement(ProgramSlotId.RECOVERY_PREHAB_LIGHT, ProgramExerciseRole.PREHAB),
                    requirement(ProgramSlotId.ACCESSORY_ROTATION, ProgramExerciseRole.ACCESSORY),
                    requirement(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT, ProgramExerciseRole.SUPPORT))
            )
        ),
        template(
            id = "STRENGTH_BIASED_5D_8W_30_70",
            focus = HiddenTemplateFocus.STRENGTH_BIASED,
            days = 5,
            weeks = 8,
            badmintonRatio = 30,
            sessions = listOf(
                slot(1, ProgramTrainingSlot.LOWER_STRENGTH_HEAVY, ProgramDayIntensity.HARD,
                    requirement(ProgramSlotId.LOWER_SQUAT_PATTERN, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.UPPER_PULL_ANCHOR, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY, ProgramExerciseRole.CORE),
                    requirement(ProgramSlotId.CALF_ANKLE_CAPACITY, ProgramExerciseRole.SUPPORT)),
                slot(2, ProgramTrainingSlot.UPPER_STRENGTH, ProgramDayIntensity.MODERATE,
                    requirement(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.UPPER_PULL_ANCHOR, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.UPPER_PUSH_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT, ProgramExerciseRole.ACCESSORY)),
                slot(4, ProgramTrainingSlot.LOWER_TRANSFER_FULL, ProgramDayIntensity.MODERATE,
                    requirement(ProgramSlotId.SINGLE_LEG_STRENGTH_CONTROL, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.BADMINTON_FOOTWORK_REACTION, ProgramExerciseRole.TRANSFER),
                    requirement(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY, ProgramExerciseRole.CORE),
                    requirement(ProgramSlotId.CALF_ANKLE_CAPACITY, ProgramExerciseRole.SUPPORT)),
                slot(6, ProgramTrainingSlot.UPPER_STRENGTH_SCAP, ProgramDayIntensity.MODERATE,
                    requirement(ProgramSlotId.UPPER_PULL_ANCHOR, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT, ProgramExerciseRole.SUPPORT),
                    requirement(ProgramSlotId.ROTATIONAL_KINETIC_CHAIN, ProgramExerciseRole.SUPPORT, minimumRepeatGapDays = 14),
                    requirement(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT, ProgramExerciseRole.SUPPORT)),
                slot(7, ProgramTrainingSlot.RECOVERY_WEAKPOINT, ProgramDayIntensity.LIGHT,
                    requirement(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN, ProgramExerciseRole.ANCHOR, true),
                    requirement(ProgramSlotId.ACCESSORY_ROTATION, ProgramExerciseRole.ACCESSORY),
                    requirement(ProgramSlotId.RECOVERY_PREHAB_LIGHT, ProgramExerciseRole.PREHAB))
            )
        )
    )

    companion object {
        val DEFAULT = ProgramTemplateCatalog()
    }
}

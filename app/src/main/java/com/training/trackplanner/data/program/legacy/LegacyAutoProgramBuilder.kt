package com.training.trackplanner.data.program.legacy

// Mechanically isolated from f5cc0ac7e0ba58cf21be81ec83e90d1c619921f9.
// Frozen product rules: do not generalize or route through another planner.
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.ExerciseDao
import com.training.trackplanner.data.ProgramOptimizationSummary
import com.training.trackplanner.data.ProgramUserNotice
import com.training.trackplanner.data.ProgramUserNoticeCode
import com.training.trackplanner.data.ProgramUserNoticeLevel
import com.training.trackplanner.data.ProgramSetPrescription

internal class LegacyAutoProgramBuilder(
    private val slotAllocator: LegacyAutoSlotAllocator = LegacyAutoSlotAllocator()
) {
    fun build(
        request: LegacyAutoRequest,
        exercises: List<Exercise>
    ): LegacyAutoSkeleton {
        val normalized = request.copy(
            goal = LegacyAutoGoal.BADMINTON_SUPPORT,
            durationWeeks = request.durationWeeks.coerceIn(3, 8),
            weeklyTrainingDays = request.weeklyTrainingDays.coerceIn(3, 7),
            sessionMinutes = when {
                request.sessionMinutes <= 30 -> 30
                request.sessionMinutes <= 45 -> 45
                else -> 60
            },
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = nearestSupportedRatio(request.badmintonTransferRatio),
            sportStrengthRatio = "AUTO",
            periodizationType = LegacyAutoPeriodizationType.AUTO,
            excludedExerciseStableKeys = emptySet(),
            preferredExerciseStableKeys = emptySet()
        )
        val intensityTable = LegacyAutoRuleTables.intensityTable(normalized.durationWeeks)
        val schedule = LegacyAutoDaySelector.defaultSchedule(normalized.durationWeeks, normalized.weeklyTrainingDays)
        val usage = LegacyAutoUsage()
        var globalDayIndex = 0
        val items = buildList {
            (1..normalized.durationWeeks).forEach { week ->
                val weekdays = schedule.getValue(week).sorted()
                val dayRules = LegacyAutoRuleTables.dayRules(normalized.weeklyTrainingDays, week)
                weekdays.forEachIndexed { slotIndex, dayOfWeek ->
                    globalDayIndex += 1
                    addAll(
                        slotAllocator.allocate(
                            LegacyAutoAllocationContext(
                                request = normalized,
                                exercises = exercises,
                                weekNumber = week,
                                dayOfWeek = dayOfWeek,
                                daySlotIndex = slotIndex + 1,
                                globalDayIndex = globalDayIndex,
                                dayRule = dayRules[slotIndex % dayRules.size],
                                intensityByArea = intensityTable[week - 1],
                                usage = usage
                            )
                        )
                    )
                }
            }
        }
        val missing = items
            .filter { it.exerciseStableKey.isBlank() }
            .map(LegacyAutoSkeletonItem::exerciseName)
            .distinct()
            .sorted()
        return LegacyAutoSkeleton(
            suggestedName = normalized.name.ifBlank { "배드민턴 지원 웨이트" },
            durationDays = normalized.durationWeeks * 7,
            request = normalized,
            periodizationType = resolvedPeriodization(normalized),
            weekPlans = weekPlans(intensityTable),
            items = items,
            weekDaySchedule = schedule,
            warnings = missing.map { "RULE_TABLE_EXERCISE_FALLBACK: $it" },
            optimizationSummary = ProgramOptimizationSummary(
                notices = listOf(
                    ProgramUserNotice(
                        code = ProgramUserNoticeCode.RULE_TABLE_STRUCTURE_CREATED,
                        level = ProgramUserNoticeLevel.SUCCESS
                    )
                )
            ),
            templateId = "DETERMINISTIC_BADMINTON_RULE_TABLE",
            representativeTemplate = true
        )
    }

    private fun nearestSupportedRatio(value: Double): Double =
        listOf(0.0, 0.30, 0.50, 0.70).minBy { kotlin.math.abs(it - value) }

    private fun resolvedPeriodization(request: LegacyAutoRequest): LegacyAutoPeriodizationType =
        if (request.badmintonTransferRatio > 0.0) {
            LegacyAutoPeriodizationType.BADMINTON_WAVE
        } else {
            LegacyAutoPeriodizationType.LINEAR_STRENGTH
        }

    private fun weekPlans(
        intensityTable: List<Map<LegacyAutoMainArea, LegacyAutoIntensityLabel>>
    ): List<LegacyAutoWeekPlan> =
        intensityTable.mapIndexed { index, week ->
            val deload = week.values.all { it == LegacyAutoIntensityLabel.DELOAD }
            LegacyAutoWeekPlan(
                weekIndex = index + 1,
                weekType = if (deload) LegacyAutoWeekType.DELOAD.name else LegacyAutoWeekType.BUILD.name,
                volumeMultiplier = if (deload) 0.65 else 1.0,
                intensityMultiplier = if (deload) 0.75 else 1.0,
                heavyExposureLimit = if (deload) 1 else 2,
                lowerBodyFatigueLimit = if (deload) 5.0 else 8.0,
                axialLoadLimit = if (deload) 1 else 2,
                plyometricLimit = if (deload) 0 else 1,
                deloadFlag = deload,
                targetRpeMin = if (deload) 6.0 else 6.5,
                targetRpeMax = if (deload) 7.5 else 8.5
            )
        }
}

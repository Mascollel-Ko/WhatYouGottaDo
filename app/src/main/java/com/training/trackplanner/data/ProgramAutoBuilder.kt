package com.training.trackplanner.data

internal class ProgramAutoBuilder(
    private val slotAllocator: ProgramSlotAllocator = ProgramSlotAllocator()
) {
    fun build(
        request: ProgramSkeletonRequest,
        exercises: List<Exercise>
    ): GeneratedProgramSkeleton {
        val normalized = request.copy(
            goal = ProgramGoal.BADMINTON_SUPPORT,
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
            periodizationType = ProgramPeriodizationType.AUTO,
            excludedExerciseStableKeys = emptySet(),
            preferredExerciseStableKeys = emptySet()
        )
        val intensityTable = ProgramRuleTables.intensityTable(normalized.durationWeeks)
        val schedule = ProgramDaySelector.defaultSchedule(normalized.durationWeeks, normalized.weeklyTrainingDays)
        val usage = ProgramAutoUsage()
        var globalDayIndex = 0
        val items = buildList {
            (1..normalized.durationWeeks).forEach { week ->
                val weekdays = schedule.getValue(week).sorted()
                val dayRules = ProgramRuleTables.dayRules(normalized.weeklyTrainingDays, week)
                weekdays.forEachIndexed { slotIndex, dayOfWeek ->
                    globalDayIndex += 1
                    addAll(
                        slotAllocator.allocate(
                            ProgramAllocationContext(
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
            .filter { it.exerciseId == 0L }
            .map(ProgramSkeletonItem::exerciseName)
            .distinct()
            .sorted()
        return GeneratedProgramSkeleton(
            suggestedName = normalized.name.ifBlank { "배드민턴 지원 웨이트" },
            durationDays = normalized.durationWeeks * 7,
            request = normalized,
            periodizationType = resolvedPeriodization(normalized),
            weekPlans = weekPlans(intensityTable),
            items = items,
            weekDaySchedule = schedule,
            warnings = missing.map { "RULE_TABLE_EXERCISE_FALLBACK: $it" },
            evaluation = null,
            optimizationSummary = ProgramOptimizationSummary(
                messages = listOf("규칙표 기반으로 메인 운동, 근력 보조, 배드민턴 전이 보조를 배치했습니다.")
            ),
            templateId = "DETERMINISTIC_BADMINTON_RULE_TABLE",
            representativeTemplate = true
        )
    }

    private fun nearestSupportedRatio(value: Double): Double =
        listOf(0.0, 0.30, 0.50, 0.70).minBy { kotlin.math.abs(it - value) }

    private fun resolvedPeriodization(request: ProgramSkeletonRequest): ProgramPeriodizationType =
        if (request.badmintonTransferRatio > 0.0) {
            ProgramPeriodizationType.BADMINTON_WAVE
        } else {
            ProgramPeriodizationType.LINEAR_STRENGTH
        }

    private fun weekPlans(
        intensityTable: List<Map<ProgramMainArea, ProgramIntensityLabel>>
    ): List<ProgramWeekPlan> =
        intensityTable.mapIndexed { index, week ->
            val deload = week.values.all { it == ProgramIntensityLabel.DELOAD }
            ProgramWeekPlan(
                weekIndex = index + 1,
                weekType = if (deload) ProgramWeekType.DELOAD.name else ProgramWeekType.BUILD.name,
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

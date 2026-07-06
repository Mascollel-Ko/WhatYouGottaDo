package com.training.trackplanner.data

internal fun defaultProgramWeekDaySchedule(durationWeeks: Int, daysPerWeek: Int): Map<Int, Set<Int>> {
    val days = if (daysPerWeek < 3) {
        when (daysPerWeek.coerceIn(1, 2)) {
            1 -> setOf(3)
            else -> setOf(2, 5)
        }
    } else {
        ProgramDaySelector.defaultWeekdays(daysPerWeek).toSet()
    }
    return (1..durationWeeks.coerceIn(1, 12)).associateWith { days }
}

internal fun emptyProgramSkeleton(
    request: ProgramSkeletonRequest,
    weekDaySchedule: Map<Int, Set<Int>>
): GeneratedProgramSkeleton {
    val durationWeeks = request.durationWeeks.coerceIn(1, 12)
    val weekPlans = (1..durationWeeks).map { week ->
        ProgramWeekPlan(
            weekIndex = week,
            weekType = ProgramWeekType.BUILD.name,
            volumeMultiplier = 1.0,
            intensityMultiplier = 1.0,
            heavyExposureLimit = 2,
            lowerBodyFatigueLimit = 8.0,
            axialLoadLimit = 2,
            plyometricLimit = 1,
            deloadFlag = false
        )
    }
    return GeneratedProgramSkeleton(
        suggestedName = request.name,
        durationDays = durationWeeks * 7,
        request = request,
        periodizationType = request.periodizationType.takeIf { it != ProgramPeriodizationType.AUTO }
            ?: ProgramPeriodizationType.STEP_DELOAD,
        weekPlans = weekPlans,
        items = emptyList(),
        weekDaySchedule = normalizeWeekDaySchedule(durationWeeks, weekDaySchedule, emptyList())
    )
}

internal fun GeneratedProgramSkeleton.resolvedWeekDaySchedule(): Map<Int, Set<Int>> =
    normalizeWeekDaySchedule(weekPlans.size.takeIf { it > 0 } ?: request.durationWeeks, weekDaySchedule, items)

internal fun GeneratedProgramSkeleton.withResolvedWeekDaySchedule(): GeneratedProgramSkeleton =
    copy(weekDaySchedule = resolvedWeekDaySchedule())

internal fun GeneratedProgramSkeleton.withWeekDays(weekNumber: Int, dayOfWeeks: Set<Int>): GeneratedProgramSkeleton {
    return ProgramDaySelector.replaceWeekdays(this, weekNumber, dayOfWeeks).reindexProgramDraft()
}

internal fun GeneratedProgramSkeleton.upsertDraftItem(item: ProgramSkeletonItem): GeneratedProgramSkeleton {
    val replaced = items.any { it.localId == item.localId }
    val nextItems = if (replaced) {
        items.map { if (it.localId == item.localId) item else it }
    } else {
        items + item.copy(orderIndex = nextOrder(item.weekNumber, item.dayOfWeek))
    }
    val currentSchedule = resolvedWeekDaySchedule().toMutableMap()
    currentSchedule[item.weekNumber] = (currentSchedule[item.weekNumber].orEmpty() + item.dayOfWeek).toSortedSet()
    return copy(items = nextItems, weekDaySchedule = currentSchedule).reindexProgramDraft()
}

internal fun GeneratedProgramSkeleton.deleteDraftItem(localId: String): GeneratedProgramSkeleton =
    copy(items = items.filterNot { it.localId == localId }).reindexProgramDraft()

private fun GeneratedProgramSkeleton.nextOrder(weekNumber: Int, dayOfWeek: Int): Int =
    items.filter { it.weekNumber == weekNumber && it.dayOfWeek == dayOfWeek }
        .maxOfOrNull(ProgramSkeletonItem::orderIndex)
        ?.plus(1)
        ?: 1

private fun GeneratedProgramSkeleton.reindexProgramDraft(): GeneratedProgramSkeleton =
    copy(
        items = items
            .groupBy { it.weekNumber to it.dayOfWeek }
            .flatMap { (_, rows) ->
                rows.sortedWith(compareBy<ProgramSkeletonItem> { it.orderIndex }.thenBy { it.localId })
                    .mapIndexed { index, item -> item.copy(orderIndex = index + 1) }
            }
            .sortedWith(compareBy<ProgramSkeletonItem> { it.weekNumber }.thenBy { it.dayOfWeek }.thenBy { it.orderIndex })
    )

private fun normalizeWeekDaySchedule(
    durationWeeks: Int,
    schedule: Map<Int, Set<Int>>,
    items: List<ProgramSkeletonItem>
): Map<Int, Set<Int>> {
    val itemDays = items.groupBy(ProgramSkeletonItem::weekNumber)
        .mapValues { (_, rows) -> rows.map(ProgramSkeletonItem::dayOfWeek).filter { it in 1..7 }.toSortedSet() }
    return (1..durationWeeks.coerceIn(1, 12)).associateWith { week ->
        (schedule[week].orEmpty() + itemDays[week].orEmpty())
            .filter { it in 1..7 }
            .toSortedSet()
    }
}

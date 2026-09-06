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

internal fun defaultProgramWeekDaySchedule(durationWeeks: Int, daysPerWeek: Int): Map<Int, Set<Int>> {
    val days = if (daysPerWeek < 3) {
        when (daysPerWeek.coerceIn(1, 2)) {
            1 -> setOf(3)
            else -> setOf(2, 5)
        }
    } else {
        LegacyAutoDaySelector.defaultWeekdays(daysPerWeek).toSet()
    }
    return (1..durationWeeks.coerceIn(1, 12)).associateWith { days }
}

internal fun emptyProgramSkeleton(
    request: LegacyAutoRequest,
    weekDaySchedule: Map<Int, Set<Int>>
): LegacyAutoSkeleton {
    val durationWeeks = request.durationWeeks.coerceIn(1, 12)
    val weekPlans = (1..durationWeeks).map { week ->
        LegacyAutoWeekPlan(
            weekIndex = week,
            weekType = LegacyAutoWeekType.BUILD.name,
            volumeMultiplier = 1.0,
            intensityMultiplier = 1.0,
            heavyExposureLimit = 2,
            lowerBodyFatigueLimit = 8.0,
            axialLoadLimit = 2,
            plyometricLimit = 1,
            deloadFlag = false
        )
    }
    return LegacyAutoSkeleton(
        suggestedName = request.name,
        durationDays = durationWeeks * 7,
        request = request,
        periodizationType = request.periodizationType.takeIf { it != LegacyAutoPeriodizationType.AUTO }
            ?: LegacyAutoPeriodizationType.STEP_DELOAD,
        weekPlans = weekPlans,
        items = emptyList(),
        weekDaySchedule = normalizeWeekDaySchedule(durationWeeks, weekDaySchedule, emptyList())
    )
}

internal fun LegacyAutoSkeleton.resolvedWeekDaySchedule(): Map<Int, Set<Int>> =
    normalizeWeekDaySchedule(weekPlans.size.takeIf { it > 0 } ?: request.durationWeeks, weekDaySchedule, items)

internal fun LegacyAutoSkeleton.withResolvedWeekDaySchedule(): LegacyAutoSkeleton =
    copy(weekDaySchedule = resolvedWeekDaySchedule())

internal fun LegacyAutoSkeleton.withWeekDays(weekNumber: Int, dayOfWeeks: Set<Int>): LegacyAutoSkeleton {
    return LegacyAutoDaySelector.replaceWeekdays(this, weekNumber, dayOfWeeks).reindexProgramDraft()
}

internal fun LegacyAutoSkeleton.upsertDraftItem(item: LegacyAutoSkeletonItem): LegacyAutoSkeleton {
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

internal fun LegacyAutoSkeleton.deleteDraftItem(localId: String): LegacyAutoSkeleton =
    copy(items = items.filterNot { it.localId == localId }).reindexProgramDraft()

private fun LegacyAutoSkeleton.nextOrder(weekNumber: Int, dayOfWeek: Int): Int =
    items.filter { it.weekNumber == weekNumber && it.dayOfWeek == dayOfWeek }
        .maxOfOrNull(LegacyAutoSkeletonItem::orderIndex)
        ?.plus(1)
        ?: 1

private fun LegacyAutoSkeleton.reindexProgramDraft(): LegacyAutoSkeleton =
    copy(
        items = items
            .groupBy { it.weekNumber to it.dayOfWeek }
            .flatMap { (_, rows) ->
                rows.sortedWith(compareBy<LegacyAutoSkeletonItem> { it.orderIndex }.thenBy { it.localId })
                    .mapIndexed { index, item -> item.copy(orderIndex = index + 1) }
            }
            .sortedWith(compareBy<LegacyAutoSkeletonItem> { it.weekNumber }.thenBy { it.dayOfWeek }.thenBy { it.orderIndex })
    )

private fun normalizeWeekDaySchedule(
    durationWeeks: Int,
    schedule: Map<Int, Set<Int>>,
    items: List<LegacyAutoSkeletonItem>
): Map<Int, Set<Int>> {
    val itemDays = items.groupBy(LegacyAutoSkeletonItem::weekNumber)
        .mapValues { (_, rows) -> rows.map(LegacyAutoSkeletonItem::dayOfWeek).filter { it in 1..7 }.toSortedSet() }
    return (1..durationWeeks.coerceAtLeast(1)).associateWith { week ->
        (schedule[week].orEmpty() + itemDays[week].orEmpty())
            .filter { it in 1..7 }
            .toSortedSet()
    }
}

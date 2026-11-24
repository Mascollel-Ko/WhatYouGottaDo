package com.training.trackplanner.data

import org.junit.Assert.*
import org.junit.Test

class ProgramWeekdayReorderTest {
    private fun slots(active: Set<Int>, source: Int, target: Int, mode: WeekdayDropPosition): Map<Int, Int> =
        ProgramWeekdayReorder.permutation(active, source, target, mode).filterKeys { it in active }

    @Test fun insertionShiftsRightWithoutCompressingRestDays() {
        assertEquals(mapOf(1 to 3, 2 to 2, 3 to 4, 4 to 5),
            slots(setOf(1,2,3,4),1,2,WeekdayDropPosition.AFTER))
        assertEquals(mapOf(1 to 3, 2 to 2, 3 to 4, 4 to 5, 5 to 6),
            slots(setOf(1,2,3,4,5),1,2,WeekdayDropPosition.AFTER))
    }

    @Test fun blockedWeekendUsesLeftSpaceAndNeverWraps() {
        assertEquals(mapOf(1 to 6, 2 to 2, 6 to 5, 7 to 7),
            slots(setOf(1,2,6,7),1,6,WeekdayDropPosition.AFTER))
    }

    @Test fun swapAndEmptyDayMoveAreExact() {
        assertEquals(mapOf(1 to 2, 2 to 1, 3 to 3, 4 to 4),
            slots(setOf(1,2,3,4),1,2,WeekdayDropPosition.CENTER))
        assertEquals(mapOf(1 to 5, 2 to 2, 3 to 3, 4 to 4),
            slots(setOf(1,2,3,4),1,5,WeekdayDropPosition.CENTER))
    }

    @Test fun allMasksAllDropsAreReversibleAndPreserveRequestedInsertionOrder() {
        for (mask in 1..127) {
            // The production schedule is backed by a sorted set, which rejects null.
            val active = (1..7).filter { mask and (1 shl (it - 1)) != 0 }.toSortedSet()
            for (source in active) for (target in 1..7) for (mode in WeekdayDropPosition.entries) {
                val map = ProgramWeekdayReorder.permutation(active, source, target, mode)
                assertEquals((1..7).toSet(), map.keys)
                assertEquals(map.keys, map.values.toSet())
                if (source != target && target in active && mode != WeekdayDropPosition.CENTER) {
                    val s = map.getValue(source)
                    val t = map.getValue(target)
                    assertEquals(if (mode == WeekdayDropPosition.AFTER) 1 else -1, s - t)
                    val others = (active - source).sorted().map(map::getValue)
                    assertEquals(others.sorted(), others)
                }
            }
        }
    }

    @Test fun allWeeksRetainDistinctPrescriptionsAndIdentity() {
        val request = ProgramSkeletonRequest("test", ProgramGoal.FUNCTIONAL_CONDITIONING, 4, 60,
            emptySet(), "", 0.0, "AUTO", ProgramPeriodizationType.AUTO, 4)
        val plan = emptyProgramSkeleton(request, (1..4).associateWith { setOf(1,2,3,4) }).copy(
            items = (1..4).flatMap { week -> (1..4).map { day ->
                ProgramSkeletonItem("w$week-d$day", week, day, 1, "ex$day", "exercise$day", "Strength",
                    90, "test", 2, week + 5, 40.0 + week, 0, "manual", "USER_FIXED")
            } }
        )
        val moved = ProgramWeekdayReorder.moveAllWeeks(plan, 1, 1, 2, WeekdayDropPosition.AFTER)
        val mapping = mapOf(1 to 3, 2 to 2, 3 to 4, 4 to 5)
        assertEquals(plan.items.map { it.copy(dayOfWeek = mapping.getValue(it.dayOfWeek)) }, moved.items)
        assertEquals((1..4).associateWith { setOf(2,3,4,5) }, moved.weekDaySchedule)
        assertEquals(plan.items, ProgramWeekdayReorder.remap(moved,
            ProgramWeekdayReorder.permutation(setOf(1,2,3,4),1,2,WeekdayDropPosition.AFTER)
                .entries.associate { it.value to it.key }).items)
    }
}

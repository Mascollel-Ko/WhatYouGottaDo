package com.training.trackplanner.data

import com.training.trackplanner.data.v22.BCoachMultiobjectiveProduction
import org.json.JSONObject

internal enum class WeekdayDropPosition { BEFORE, CENTER, AFTER }

/**
 * A bijection of seven weekday slots, not a sort of the active-day list.
 * Source identity stays intact; only the minimum contiguous chain is shifted.
 * A vacant slot on the preferred side wins. At the week boundary use the
 * opposite side, never a cyclic Sunday -> Monday wrap.
 */
internal object ProgramWeekdayReorder {
    fun permutation(active: Set<Int>, source: Int, target: Int, position: WeekdayDropPosition): Map<Int, Int> {
        require(active.all { it in 1..7 } && source in active && target in 1..7)
        if (source == target) return (1..7).associateWith { it }
        val slots = (1..7).map<Int, Int?> { it }.toMutableList()
        val s = source - 1
        val t = target - 1
        if (position == WeekdayDropPosition.CENTER || target !in active) {
            slots[s] = target
            slots[t] = source
        } else {
            slots[s] = null
            // TreeSet rejects null lookups. A lifted source is a real vacancy.
            fun free(index: Int): Boolean = slots[index]?.let { it !in active } ?: true
            // Place the moving session immediately after/before the target.
            val right = ((t + 1)..6).firstOrNull { free(it) }
            val left = (t - 1 downTo 0).firstOrNull { free(it) }
            val useRight = if (position == WeekdayDropPosition.AFTER) right != null else left == null
            val vacancy = requireNotNull(if (useRight) right else left)
            val displacedEmpty = slots[vacancy]
            val insertion = when {
                useRight && position == WeekdayDropPosition.AFTER -> t + 1
                useRight -> t
                position == WeekdayDropPosition.BEFORE -> t - 1
                else -> t
            }
            if (useRight) {
                for (i in vacancy downTo insertion + 1) slots[i] = slots[i - 1]
            } else {
                for (i in vacancy until insertion) slots[i] = slots[i + 1]
            }
            slots[insertion] = source
            // If another vacant day supplied the space, assign its identity to
            // the old source slot. This makes the move reversible across weeks.
            if (displacedEmpty != null) {
                val hole = slots.indexOf(null)
                require(hole >= 0)
                slots[hole] = displacedEmpty
            }
        }
        return slots.mapIndexed { index, oldDay -> requireNotNull(oldDay) to index + 1 }
            .toMap().also { require(it.keys == (1..7).toSet() && it.values.toSet() == it.keys) }
    }

    /** Pure, lossless transformation; also used by the approval inverse check. */
    fun remap(plan: GeneratedProgramSkeleton, mapping: Map<Int, Int>): GeneratedProgramSkeleton {
        require(mapping.keys == (1..7).toSet() && mapping.values.toSet() == mapping.keys)
        return plan.copy(
            items = plan.items.map { item -> item.copy(dayOfWeek = mapping.getValue(item.dayOfWeek)) },
            weekDaySchedule = plan.resolvedWeekDaySchedule().mapValues { (_, days) ->
                days.mapTo(sortedSetOf()) { mapping.getValue(it) }
            }
        )
    }

    /** Do not place a session into a recovery interval or outside its original block. */
    fun respectsBlockBoundaries(
        original: GeneratedProgramSkeleton, moved: GeneratedProgramSkeleton
    ): Boolean {
        if (moved.items.any { (it.weekNumber - 1L) * 7 + it.dayOfWeek > moved.durationDays }) return false
        val metadata = original.planningMetadataJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val specs = metadata?.optJSONArray("blockSpecs") ?: return true
        val breaks = metadata.optJSONArray("recoveryDaysBetween")
        fun block(week: Int, day: Int): Int? {
            val offset = (week - 1) * 7 + day - 1
            var start = 0
            for (i in 0 until specs.length()) {
                val end = start + specs.getJSONObject(i).getInt("weeks") * 7
                if (offset in start until end) return i
                start = end + (breaks?.optInt(i, 0) ?: 0)
            }
            return null
        }
        return original.items.zip(moved.items).all { (old, new) ->
            val before = block(old.weekNumber, old.dayOfWeek)
            before != null && before == block(new.weekNumber, new.dayOfWeek)
        }
    }

    fun moveAllWeeks(
        plan: GeneratedProgramSkeleton, week: Int, source: Int, target: Int, position: WeekdayDropPosition
    ): GeneratedProgramSkeleton {
        val active = plan.resolvedWeekDaySchedule().getValue(week)
        val mapping = permutation(active, source, target, position)
        if (mapping.all { it.key == it.value }) return plan
        val moved = remap(plan, mapping)
        require(respectsBlockBoundaries(plan, moved)) { "회복 구간 또는 프로그램 범위를 벗어나는 요일 이동입니다." }
        return BCoachMultiobjectiveProduction.recordWeekdayDraftEdit(plan, moved, mapping)
    }
}

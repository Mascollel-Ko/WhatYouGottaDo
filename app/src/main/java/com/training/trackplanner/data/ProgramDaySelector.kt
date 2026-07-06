package com.training.trackplanner.data

internal object ProgramDaySelector {
    fun defaultWeekdays(daysPerWeek: Int): List<Int> =
        when (daysPerWeek.coerceIn(3, 7)) {
            3 -> listOf(1, 3, 5)
            4 -> listOf(1, 2, 4, 6)
            5 -> listOf(1, 2, 4, 6, 7)
            6 -> listOf(1, 2, 3, 4, 6, 7)
            else -> (1..7).toList()
        }

    fun defaultSchedule(durationWeeks: Int, daysPerWeek: Int): Map<Int, Set<Int>> {
        val days = defaultWeekdays(daysPerWeek).toSet()
        return (1..durationWeeks.coerceIn(3, 8)).associateWith { days }
    }

    fun replaceWeekdays(
        skeleton: GeneratedProgramSkeleton,
        weekNumber: Int,
        newWeekdays: Set<Int>
    ): GeneratedProgramSkeleton {
        val normalized = newWeekdays.filter { it in 1..7 }.sorted()
        val current = skeleton.resolvedWeekDaySchedule()[weekNumber].orEmpty().sorted()
        if (normalized.size != current.size) {
            return skeleton.copy(
                weekDaySchedule = skeleton.resolvedWeekDaySchedule().toMutableMap().apply {
                    this[weekNumber] = normalized.toSet()
                },
                items = skeleton.items.filterNot {
                    it.weekNumber == weekNumber && it.dayOfWeek !in normalized
                }
            )
        }
        val dayMap = current.zip(normalized).toMap()
        val nextSchedule = skeleton.resolvedWeekDaySchedule().toMutableMap()
        nextSchedule[weekNumber] = normalized.toSet()
        return skeleton.copy(
            weekDaySchedule = nextSchedule,
            items = skeleton.items.map { item ->
                if (item.weekNumber == weekNumber) {
                    item.copy(dayOfWeek = dayMap[item.dayOfWeek] ?: item.dayOfWeek)
                } else {
                    item
                }
            }
        )
    }
}

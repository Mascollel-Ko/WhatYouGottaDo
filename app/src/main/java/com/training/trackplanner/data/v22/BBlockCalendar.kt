package com.training.trackplanner.data.v22

/** Day-precision placement prevents 1..7-day block breaks from shifting a workout into a rest day. */
internal data class BCalendarSlot(val week: Int, val day: Int)

internal class BBlockCalendar(private val program: BBlockProgramDefinition) {
    val durationDays: Int = program.durationDays
    val calendarWeeks: Int = (durationDays + 6) / 7

    fun slot(blockIndex: Int, localWeekIndex: Int, sessionDay: Int): BCalendarSlot {
        require(blockIndex in program.blocks.indices)
        require(localWeekIndex in 0 until program.blocks[blockIndex].weeks)
        require(sessionDay in 1..7)
        val previousTrainingDays = program.blocks.take(blockIndex).sumOf { it.weeks * 7 }
        val previousRestDays = program.recoveryDaysBetween.take(blockIndex).sum()
        val absoluteDay = previousTrainingDays + previousRestDays + localWeekIndex * 7 + sessionDay - 1
        return BCalendarSlot(week = absoluteDay / 7 + 1, day = absoluteDay % 7 + 1)
    }
}

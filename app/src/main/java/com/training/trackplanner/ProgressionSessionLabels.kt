package com.training.trackplanner

/**
 * Human-readable calendar-position labels in program editors.
 * Do not invent a real calendar date: that exists only after a start date is applied.
 */
internal object ProgressionSessionLabels {
    private val weekdays = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")

    fun occurrence(week: Int, day: Int, order: Int? = null): String {
        val dayName = weekdays.getOrNull(day - 1) ?: "${day}일"
        return "${week}주차 ${dayName}" +
            (order?.takeIf { it > 1 }?.let { " · ${it}번째 운동" } ?: "")
    }

    fun members(positions: List<Triple<Int, Int, Int>>): String =
        positions.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
            .map { occurrence(it.first, it.second, it.third) }
            .joinToString(" → ")

    fun title(exerciseName: String, positions: List<Triple<Int, Int, Int>>): String {
        val sorted = positions.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
        if (sorted.isEmpty()) return "$exerciseName · 구성 확인 필요"
        val first = sorted.first()
        return "$exerciseName · ${occurrence(first.first, first.second, first.third)} 시작 · ${sorted.size}개"
    }
}

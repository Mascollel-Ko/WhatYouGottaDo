package com.training.trackplanner.data

internal class ProgramSessionDensityPolicy {
    fun targetExerciseCount(minutes: Int): Int = when (minutes) {
        in 15..25 -> 3
        in 26..40 -> 4
        in 41..60 -> 5
        in 61..80 -> 6
        else -> 7
    }

    fun usefulMinimumExerciseCount(minutes: Int): Int = when {
        minutes <= 30 -> 3
        minutes <= 60 -> 4
        else -> 5
    }

    fun warnings(items: List<ProgramSkeletonItem>, request: ProgramSkeletonRequest): List<String> {
        val minimum = usefulMinimumExerciseCount(request.dailyAvailableMinutes)
        if (minimum <= 3) return emptyList()
        val lowDensitySessions = items
            .groupBy { it.weekNumber to it.dayOfWeek }
            .count { (_, rows) -> rows.size < minimum }
        return if (lowDensitySessions > request.durationWeeks) {
            listOf("PROGRAM_SESSION_DENSITY_UNDER_TARGET")
        } else {
            emptyList()
        }
    }
}

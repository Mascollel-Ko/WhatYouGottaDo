package com.training.trackplanner

data class SportDurationParts(
    val hours: Int,
    val minutes: Int
) {
    val isPositive: Boolean
        get() = hours > 0 || minutes > 0
}

fun normalizeHoursMinutes(hours: Int, minutes: Int): SportDurationParts {
    val totalMinutes = (hours.coerceAtLeast(0) * 60) + minutes.coerceAtLeast(0)
    return SportDurationParts(
        hours = totalMinutes / 60,
        minutes = totalMinutes % 60
    )
}

fun totalDurationToHoursMinutes(totalSeconds: Int): SportDurationParts {
    val totalMinutes = totalSeconds.coerceAtLeast(0) / 60
    return SportDurationParts(
        hours = totalMinutes / 60,
        minutes = totalMinutes % 60
    )
}

fun hoursMinutesToStoredDuration(hours: Int, minutes: Int): Int {
    val normalized = normalizeHoursMinutes(hours, minutes)
    return (normalized.hours * 60 + normalized.minutes) * 60
}

fun formatSportDurationLabel(totalSeconds: Int): String {
    val parts = totalDurationToHoursMinutes(totalSeconds)
    return "${parts.hours}시간 ${parts.minutes}분"
}

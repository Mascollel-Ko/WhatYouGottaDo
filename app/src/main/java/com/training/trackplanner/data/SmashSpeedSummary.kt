package com.training.trackplanner.data

data class SmashSpeedSummary(
    val date: String,
    val bestSpeedKmh: Double? = null,
    val averageSpeedKmh: Double? = null,
    val top3AverageSpeedKmh: Double? = null,
    val attemptCount: Int = 0
) {
    companion object {
        fun from(date: String, records: List<SmashSpeedRecord>): SmashSpeedSummary {
            val speeds = records.map { record -> record.speedKmh }
            return SmashSpeedSummary(
                date = date,
                bestSpeedKmh = speeds.maxOrNull(),
                averageSpeedKmh = speeds.takeIf { it.isNotEmpty() }?.average(),
                top3AverageSpeedKmh = speeds.sortedDescending().take(3).takeIf { it.isNotEmpty() }?.average(),
                attemptCount = speeds.size
            )
        }
    }
}

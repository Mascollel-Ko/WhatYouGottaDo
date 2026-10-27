package com.training.trackplanner.health

import java.time.Duration
import java.time.Instant

/** Pure diagnostic transformations. No data is persisted or sent to a server. */
internal object WatchDiagnosticMath {
    data class HeartSample(val at: Instant, val bpm: Long, val origin: String)

    data class HeartSummary(
        val count: Int,
        val first: Instant?,
        val last: Instant?,
        val minimum: Long?,
        val maximum: Long?,
        val mean: Double?,
        val sourceCounts: Map<String, Int>,
        val largeGaps: List<Pair<Instant, Instant>>,
        val duplicateTimestampCount: Int
    )

    fun intersects(start: Instant, end: Instant, rangeStart: Instant, rangeEnd: Instant): Boolean =
        end > start && start < rangeEnd && end > rangeStart

    fun elapsedSeconds(start: Instant, end: Instant): Long? =
        if (end > start) Duration.between(start, end).seconds else null

    fun summarizeHearts(
        input: List<HeartSample>,
        rangeStart: Instant,
        rangeEnd: Instant,
        gapThresholdMinutes: Long = 10L
    ): HeartSummary {
        require(gapThresholdMinutes > 0)
        val samples = input.asSequence()
            .filter { it.at >= rangeStart && it.at < rangeEnd && it.bpm in 1..300 }
            .sortedWith(compareBy<HeartSample> { it.at }.thenBy { it.origin }.thenBy { it.bpm })
            .toList()
        if (samples.isEmpty()) {
            return HeartSummary(0, null, null, null, null, null, emptyMap(), emptyList(), 0)
        }
        val timestamps = samples.map { it.at }.distinct()
        val gaps = timestamps.zipWithNext()
            .filter { (a, b) -> Duration.between(a, b).toMinutes() >= gapThresholdMinutes }
        return HeartSummary(
            count = samples.size,
            first = samples.first().at,
            last = samples.last().at,
            minimum = samples.minOf { it.bpm },
            maximum = samples.maxOf { it.bpm },
            mean = samples.map { it.bpm.toDouble() }.average(),
            sourceCounts = samples.groupingBy { it.origin }.eachCount(),
            largeGaps = gaps,
            duplicateTimestampCount = samples.size - timestamps.size
        )
    }
}

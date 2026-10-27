package com.training.trackplanner.health

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WatchDiagnosticMathTest {
    private val start = Instant.parse("2026-10-26T00:00:00Z")
    private val end = Instant.parse("2026-10-27T00:00:00Z")
    private fun beat(minute: Long, bpm: Long, source: String = "samsung") =
        WatchDiagnosticMath.HeartSample(start.plusSeconds(minute * 60), bpm, source)

    @Test fun emptySamplesStayEmpty() {
        val result = WatchDiagnosticMath.summarizeHearts(emptyList(), start, end)
        assertEquals(0, result.count)
        assertNull(result.mean)
        assertTrue(result.largeGaps.isEmpty())
    }

    @Test fun ordersSamplesBeforeSummarizing() {
        val r = WatchDiagnosticMath.summarizeHearts(listOf(beat(2, 160), beat(1, 80)), start, end)
        assertEquals(start.plusSeconds(60), r.first)
        assertEquals(start.plusSeconds(120), r.last)
        assertEquals(80L, r.minimum)
        assertEquals(160L, r.maximum)
        assertEquals(120.0, r.mean!!, 0.001)
    }

    @Test fun excludesOutsideDayAndInvalidBpm() {
        val r = WatchDiagnosticMath.summarizeHearts(
            listOf(beat(-1, 90), beat(0, 100), beat(1, 0), beat(2, 301), beat(1440, 75)), start, end
        )
        assertEquals(1, r.count)
    }

    @Test fun reportsSamplingGapWithoutInterpolating() {
        val r = WatchDiagnosticMath.summarizeHearts(listOf(beat(0, 80), beat(16, 150)), start, end)
        assertEquals(2, r.count)
        assertEquals(1, r.largeGaps.size)
        assertEquals(start, r.largeGaps.single().first)
        assertEquals(start.plusSeconds(960), r.largeGaps.single().second)
    }

    @Test fun exactlyTenMinuteGapIsIncluded() {
        val r = WatchDiagnosticMath.summarizeHearts(listOf(beat(0, 80), beat(10, 85)), start, end)
        assertEquals(1, r.largeGaps.size)
    }

    @Test fun nineMinuteGapIsNotReported() {
        val r = WatchDiagnosticMath.summarizeHearts(listOf(beat(0, 80), beat(9, 85)), start, end)
        assertEquals(0, r.largeGaps.size)
    }

    @Test fun duplicateTimeStaysVisibleForSourceAudit() {
        val r = WatchDiagnosticMath.summarizeHearts(
            listOf(beat(2, 90, "samsung"), beat(2, 92, "other")), start, end
        )
        assertEquals(2, r.count)
        assertEquals(1, r.duplicateTimestampCount)
        assertEquals(1, r.sourceCounts["samsung"])
        assertEquals(1, r.sourceCounts["other"])
    }

    @Test fun overlappingSessionsCountAsIntersections() {
        assertTrue(WatchDiagnosticMath.intersects(
            start.minusSeconds(3600), start.plusSeconds(60), start, end
        ))
    }

    @Test fun sessionEndingExactlyAtStartDoesNotIntersect() {
        assertFalse(WatchDiagnosticMath.intersects(
            start.minusSeconds(60), start, start, end
        ))
    }

    @Test fun sessionStartingExactlyAtEndDoesNotIntersect() {
        assertFalse(WatchDiagnosticMath.intersects(
            end, end.plusSeconds(60), start, end
        ))
    }

    @Test fun invalidSessionIsRejected() {
        assertFalse(WatchDiagnosticMath.intersects(end, start, start, end))
        assertNull(WatchDiagnosticMath.elapsedSeconds(end, start))
    }

    @Test fun validSessionUsesSecondsNotRoundedHours() {
        assertEquals(61L, WatchDiagnosticMath.elapsedSeconds(start, start.plusSeconds(61)))
    }

    @Test fun crossingMidnightIsValid() {
        assertEquals(180L, WatchDiagnosticMath.elapsedSeconds(
            end.minusSeconds(60), end.plusSeconds(120)
        ))
    }

    @Test fun diagnosticOnlyDoesNotInferActivityFromHeartRate() {
        val result = WatchDiagnosticMath.summarizeHearts(
            listOf(beat(15, 180), beat(60, 185)), start, end
        )
        assertEquals(2, result.count)
        assertEquals(1, result.largeGaps.size)
    }

    @Test fun summarySeparatesDataOrigins() {
        val result = WatchDiagnosticMath.summarizeHearts(
            listOf(beat(2, 120), beat(3, 145, "third-party")), start, end
        )
        assertEquals(setOf("samsung", "third-party"), result.sourceCounts.keys)
    }
}

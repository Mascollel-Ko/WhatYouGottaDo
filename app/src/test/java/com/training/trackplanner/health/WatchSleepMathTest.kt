package com.training.trackplanner.health

import androidx.health.connect.client.records.SleepSessionRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WatchSleepMathTest {
    private val a = Instant.parse("2026-10-31T21:00:00Z")
    private fun stage(startMin: Long, endMin: Long, type: Int) =
        WatchSleepMath.Stage(a.plusSeconds(startMin * 60), a.plusSeconds(endMin * 60), type)

    @Test fun completeNightExcludesAwakeMinutes() {
        val r = WatchSleepMath.evaluate(a, a.plusSeconds(480 * 60), listOf(
            stage(0, 240, SleepSessionRecord.STAGE_TYPE_LIGHT),
            stage(240, 300, SleepSessionRecord.STAGE_TYPE_AWAKE),
            stage(300, 480, SleepSessionRecord.STAGE_TYPE_REM)
        ))
        assertTrue(r.reliable)
        assertEquals(420, r.sleepMinutes)
    }

    @Test fun noStagesNeverAutoFillsElapsedTime() {
        val r = WatchSleepMath.evaluate(a, a.plusSeconds(480 * 60), emptyList())
        assertFalse(r.reliable)
        assertNull(r.sleepMinutes)
    }

    @Test fun lowStageCoverageDoesNotAutoFill() {
        val r = WatchSleepMath.evaluate(a, a.plusSeconds(480 * 60), listOf(
            stage(0, 90, SleepSessionRecord.STAGE_TYPE_DEEP)
        ))
        assertFalse(r.reliable)
    }

    @Test fun overlappingStagesAreRejected() {
        val r = WatchSleepMath.evaluate(a, a.plusSeconds(120 * 60), listOf(
            stage(0, 80, SleepSessionRecord.STAGE_TYPE_LIGHT),
            stage(60, 120, SleepSessionRecord.STAGE_TYPE_REM)
        ))
        assertFalse(r.reliable)
    }

    @Test fun outOfBoundsStagesAreRejected() {
        val r = WatchSleepMath.evaluate(a, a.plusSeconds(60 * 60), listOf(
            stage(-3, 60, SleepSessionRecord.STAGE_TYPE_LIGHT)
        ))
        assertFalse(r.reliable)
    }

    @Test fun allAwakeIsRejected() {
        val r = WatchSleepMath.evaluate(a, a.plusSeconds(60 * 60), listOf(
            stage(0, 60, SleepSessionRecord.STAGE_TYPE_AWAKE)
        ))
        assertFalse(r.reliable)
    }

    @Test fun moreThanTwentyFourHoursIsRejected() {
        val r = WatchSleepMath.evaluate(a, a.plusSeconds(25 * 3600L), listOf(
            stage(0, 25 * 60L, SleepSessionRecord.STAGE_TYPE_SLEEPING)
        ))
        assertFalse(r.reliable)
    }
}

class WatchSleepAggregateTest {
    private val base = Instant.parse("2026-11-07T12:00:00Z")
    private fun session(start: Long, end: Long, sleep: Int?, reliable: Boolean = true) =
        WatchSleep("id:$start", "watch", base.plusSeconds(start * 60),
            base.plusSeconds(end * 60), sleep, (end - start).toInt(), reliable)

    @Test fun splitSessionsAreSummed() {
        assertEquals(390, WatchSleepAggregate.combine(listOf(
            session(60, 270, 210), session(330, 510, 180)
        ))?.sleepMinutes)
    }

    @Test fun overlappingSessionsAreNotDoubleCounted() {
        assertNull(WatchSleepAggregate.combine(listOf(
            session(60, 270, 210), session(240, 510, 270)
        )))
    }

    @Test fun unreliableSessionsPreventAutomaticFill() {
        assertNull(WatchSleepAggregate.combine(listOf(
            session(60, 270, 210), session(330, 510, null, false)
        )))
    }
}

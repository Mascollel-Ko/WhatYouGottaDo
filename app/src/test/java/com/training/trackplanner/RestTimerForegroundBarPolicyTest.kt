package com.training.trackplanner

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerForegroundBarPolicyTest {
    @Test
    fun `idle is hidden while running and finished states remain visible`() {
        assertFalse(RestTimerForegroundBarPolicy.visible(RestTimerState.Idle, null))
        assertTrue(RestTimerForegroundBarPolicy.visible(state(runId = 1), null))
        assertTrue(
            RestTimerForegroundBarPolicy.visible(
                state(runId = 1).copy(isRunning = false, isFinished = true, remainingSeconds = 0),
                null
            )
        )
    }

    @Test
    fun `dismissal stays scoped to one run and next run appears`() {
        assertFalse(RestTimerForegroundBarPolicy.visible(state(runId = 101), dismissedRunId = 101))
        assertFalse(
            RestTimerForegroundBarPolicy.visible(
                state(runId = 101).copy(remainingSeconds = 14),
                dismissedRunId = 101
            )
        )
        assertTrue(RestTimerForegroundBarPolicy.visible(state(runId = 102), dismissedRunId = 101))
    }

    @Test
    fun `progress shrinks and last five seconds are emphasized`() {
        assertEquals(0.5f, RestTimerForegroundBarPolicy.progress(state().copy(remainingSeconds = 30)), 0.001f)
        assertEquals(0.1f, RestTimerForegroundBarPolicy.progress(state().copy(remainingSeconds = 6)), 0.001f)
        assertFalse(RestTimerForegroundBarPolicy.emphasized(state().copy(remainingSeconds = 6)))
        assertTrue(RestTimerForegroundBarPolicy.emphasized(state().copy(remainingSeconds = 5)))
        assertFalse(
            RestTimerForegroundBarPolicy.emphasized(
                state().copy(isRunning = false, isFinished = true, remainingSeconds = 0)
            )
        )
    }

    @Test
    fun `bar target retains date entry set and explicit request identity`() {
        assertEquals(
            RestTimerTarget("2026-08-22", 17L, 23L, 9L),
            RestTimerForegroundBarPolicy.target(state(), requestId = 9L)
        )
        assertEquals("01:05", formatRestTimerClock(65))
    }

    @Test
    fun `root scaffold owns bar above navigation and dismiss never stops controller`() {
        val main = source("MainActivity.kt")
        val timer = source("RestTimerSessionController.kt")

        val bottomBar = main.substringAfter("bottomBar = {").substringBefore("} \n    )")
        assertTrue(bottomBar.indexOf("RestTimerForegroundBar(") < bottomBar.indexOf("AppBottomNavigation("))
        assertTrue(main.contains("onDismiss = { dismissedTimerRunId = timerState.runId }"))
        assertFalse(main.contains("onDismiss = restTimerSessionController::stop"))
        assertTrue(timer.contains("putInt(KEY_REST_TOTAL_SECONDS, state.totalSeconds)"))
        assertTrue(timer.contains("totalSeconds = total"))
    }

    private fun state(runId: Long = 7) = RestTimerState(
        runId = runId,
        isRunning = true,
        remainingSeconds = 60,
        totalSeconds = 60,
        endAtEpochMillis = 10_000L,
        exerciseName = "스쿼트",
        nextHint = "스쿼트 3세트",
        targetRecordDate = "2026-08-22",
        targetEntryId = 17L,
        targetSetId = 23L
    )

    private fun source(name: String): String =
        sequenceOf(
            File("src/main/java/com/training/trackplanner/$name"),
            File("app/src/main/java/com/training/trackplanner/$name")
        ).first(File::exists).readText(Charsets.UTF_8).replace("\r\n", "\n")
}

package com.training.trackplanner.data.personalized

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusTrainingDayCoverageTest {
    @Test
    fun `five requested days with approved exercise on two days reports exact empty days`() {
        val scheduled = listOf(1, 2, 4, 6, 7)
        val program = PostGenerationFixture.plan(
            rows = listOf(
                PostGenerationFixture.row("press", day = 1, sets = 8),
                PostGenerationFixture.row("hinge", day = 2, sets = 1)
            ),
            days = scheduled,
            horizon = 1
        )

        val audit = StimulusTrainingDayCoverage.assess(program).single()

        assertEquals(5, audit.requestedDays)
        assertEquals(scheduled, audit.scheduledDays)
        assertEquals(listOf(1, 2), audit.actualTrainingDays)
        assertEquals(listOf(4, 6, 7), audit.emptyScheduledDays)
        assertFalse(audit.complete)
    }

    @Test
    fun `zero set rows do not count as executed training days`() {
        val emptyRow = PostGenerationFixture.row("hinge", day = 2).copy(setCount = 0, setPrescriptions = emptyList())
        val program = PostGenerationFixture.plan(
            rows = listOf(PostGenerationFixture.row("press", day = 1), emptyRow),
            days = listOf(1, 2),
            horizon = 1
        )

        val audit = StimulusTrainingDayCoverage.assess(program).single()

        assertEquals(listOf(1), audit.actualTrainingDays)
        assertEquals(listOf(2), audit.emptyScheduledDays)
        assertTrue(!audit.complete)
    }
}

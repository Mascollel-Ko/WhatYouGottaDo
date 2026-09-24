package com.training.trackplanner.data.personalized

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StimulusProductionProgressTest {
    @Test
    fun `two production passes share one monotonic user facing sequence`() {
        val updates = mutableListOf<PersonalizedPlannerProgress>()
        val mapper = ProductionGenerationProgressMapper(
            object : PersonalizedPlannerProgressReporter {
                override fun report(stage: PersonalizedPlannerStage) = Unit
                override fun report(update: PersonalizedPlannerProgress) { updates += update }
            }
        )

        val control = mapper.controlReporter()
        control.report(PersonalizedPlannerStage.INPUT)
        control.report(PersonalizedPlannerStage.DEMAND)
        control.report(PersonalizedPlannerStage.POST_SPLIT_REFLOW)
        control.report(PersonalizedPlannerStage.FINAL)
        val controlCompletionIndex = updates.lastIndex

        val experimental = mapper.experimentalReporter()
        experimental.report(PersonalizedPlannerStage.INPUT)
        experimental.report(PersonalizedPlannerStage.DEMAND)
        experimental.report(PersonalizedPlannerStage.POST_SPLIT_REFLOW)
        experimental.report(PersonalizedPlannerStage.FINAL)
        mapper.reportSelection()
        mapper.reportValidationComplete()
        mapper.reportComplete()

        val values = updates.map { it.percent }
        assertTrue(values.first() >= 5)
        assertEquals(values.sorted(), values)
        assertTrue(values.all { it in 0..100 })
        assertTrue(values[controlCompletionIndex + 1] > values[controlCompletionIndex])
        assertTrue(values[controlCompletionIndex + 1] < 100)
        assertEquals(100, values.last())
        assertEquals(1, values.count { it == 100 })
    }
}

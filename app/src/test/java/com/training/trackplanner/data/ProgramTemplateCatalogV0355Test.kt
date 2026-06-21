package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramTemplateCatalogV0355Test {
    private val catalog = ProgramTemplateCatalog.DEFAULT

    @Test
    fun selectsOnlyTheFiveApprovedRepresentativeTemplates() {
        val cases = listOf(
            Case(3, 4, 0.70, "STANDARD_3D_4W_70_30", HiddenTemplateFocus.STANDARD),
            Case(4, 4, 0.80, "FOOTWORK_COD_4D_4W_80_20", HiddenTemplateFocus.FOOTWORK_COD),
            Case(4, 4, 0.50, "HYBRID_4D_4W_50_50", HiddenTemplateFocus.HYBRID),
            Case(5, 8, 0.70, "STANDARD_5D_8W_70_30", HiddenTemplateFocus.STANDARD),
            Case(5, 8, 0.30, "STRENGTH_BIASED_5D_8W_30_70", HiddenTemplateFocus.STRENGTH_BIASED)
        )

        cases.forEach { case ->
            val selected = catalog.select(request(case.days, case.weeks, case.ratio))
            assertTrue(selected.representative)
            assertEquals(case.id, selected.templateId)
            assertEquals(case.focus, selected.focus)
            assertEquals(case.days, selected.sessions.size)
        }
    }

    @Test
    fun unsupportedCombinationUsesPolicyFallbackWithoutInventingTemplate() {
        val selected = catalog.select(request(days = 6, weeks = 8, ratio = 0.90))

        assertFalse(selected.representative)
        assertEquals("POLICY_FALLBACK_6D", selected.templateId)
        assertEquals(HiddenTemplateFocus.FALLBACK, selected.focus)
        assertTrue(selected.sessions.all { it.exerciseSlots.isEmpty() })
    }

    @Test
    fun everyRepresentativeTemplatePreservesSquatHingeAndUpperPullAnchors() {
        representativeRequests().forEach { request ->
            val required = catalog.select(request).sessions
                .flatMap(PlannedSlot::exerciseSlots)
                .filter(TemplateExerciseSlot::required)
                .mapNotNull(TemplateExerciseSlot::targetSlot)
                .toSet()

            assertTrue(ProgramSlotId.LOWER_SQUAT_PATTERN in required)
            assertTrue(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN in required)
            assertTrue(ProgramSlotId.UPPER_PULL_ANCHOR in required)
        }
    }

    @Test
    fun representativeSkeletonsStayWithinExposureTableMaximums() {
        representativeRequests().forEach { request ->
            val selection = catalog.select(request)
            val weeklyCounts = selection.sessions
                .flatMap(PlannedSlot::exerciseSlots)
                .mapNotNull(TemplateExerciseSlot::targetSlot)
                .groupingBy { it }
                .eachCount()
            val targets = catalog.exposureTargets(selection, request).associateBy(NumericExposureTarget::slot)

            weeklyCounts.forEach { (slot, weeklyCount) ->
                assertTrue(
                    "$slot exceeds its ${request.durationWeeks}-week maximum",
                    weeklyCount * request.durationWeeks <= targets.getValue(slot).maximum
                )
            }
        }
    }

    private fun representativeRequests(): List<ProgramSkeletonRequest> = listOf(
        request(3, 4, 0.70),
        request(4, 4, 0.80),
        request(4, 4, 0.50),
        request(5, 8, 0.70),
        request(5, 8, 0.30)
    )

    private fun request(days: Int, weeks: Int, ratio: Double) = ProgramSkeletonRequest(
        name = "Template Test",
        goal = ProgramGoal.BADMINTON_SUPPORT,
        weeklyTrainingDays = days,
        sessionMinutes = 60,
        availableEquipment = emptySet(),
        excludedExerciseText = "",
        badmintonTransferRatio = ratio,
        sportStrengthRatio = "AUTO",
        periodizationType = ProgramPeriodizationType.AUTO,
        durationWeeks = weeks
    )

    private data class Case(
        val days: Int,
        val weeks: Int,
        val ratio: Double,
        val id: String,
        val focus: HiddenTemplateFocus
    )
}

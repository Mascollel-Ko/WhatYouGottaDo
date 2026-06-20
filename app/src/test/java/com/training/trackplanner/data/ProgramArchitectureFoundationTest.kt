package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramArchitectureFoundationTest {
    @Test
    fun templateCatalogPreservesExistingThreeDayStructure() {
        val slots = ProgramTemplateCatalog.DEFAULT.slots(3)

        assertEquals(listOf(1, 3, 5), slots.map(PlannedSlot::dayOfWeek))
        assertEquals(
            listOf(
                ProgramTrainingSlot.LOWER_STRENGTH,
                ProgramTrainingSlot.UPPER_STRENGTH_SCAP,
                ProgramTrainingSlot.BADMINTON_TRANSFER
            ),
            slots.map(PlannedSlot::slot)
        )
        assertEquals(
            listOf(ProgramDayIntensity.HARD, ProgramDayIntensity.MODERATE, ProgramDayIntensity.LIGHT),
            slots.map(PlannedSlot::intensity)
        )
    }

    @Test
    fun fatiguePolicyKeepsGreenAndSoftensRedWithoutChangingSlots() {
        val policy = FatigueSlotPolicy.DEFAULT
        val planned = ProgramTemplateCatalog.DEFAULT.slots(5)
        val green = ProgramFatigueGate(
            ProgramFatigueBand.GREEN, 1.0, 9, true, true, true, false
        )
        val red = ProgramFatigueGate(
            ProgramFatigueBand.RED, 0.25, 7, false, false, false, true
        )

        assertEquals(planned, planned.map { policy.adapt(it, green) })
        assertTrue(planned.map { policy.adapt(it, red) }.all { it.intensity == ProgramDayIntensity.LIGHT })
    }

    @Test
    fun capabilityResolverUsesMetadataBeforeLegacyAndNeverUsesNameAlone() {
        val exercise = Exercise(
            id = 1,
            name = "Squat-like display name",
            category = "strength",
            stableKey = "test_squat"
        )
        val metadata = RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            movementFamily = "ROW_VARIANTS",
            movementSubtype = "CABLE_ROW",
            programSlot = "UPPER_PULL_STRENGTH",
            redundancyGroup = "HORIZONTAL_PULL_COMPOUND"
        )

        val canonical = SlotCapabilityResolver.DEFAULT.resolve(exercise, metadata)
        val nameOnly = SlotCapabilityResolver.DEFAULT.resolve(exercise, null)

        assertEquals(SlotCapabilitySource.RUNTIME_METADATA, canonical.source)
        assertTrue(canonical.hasAny(ProgramSlotId.UPPER_PULL_ANCHOR))
        assertFalse(canonical.hasAny(ProgramSlotId.LOWER_SQUAT_PATTERN))
        assertEquals(SlotCapabilitySource.NONE, nameOnly.source)
    }

    @Test
    fun coverageCreditsOnePrimaryAndDoesNotLetUmbrellaFullySatisfyCoverage() {
        val profile = SlotCapabilityProfile(
            primary = setOf(ProgramSlotId.ACCESSORY_ROTATION),
            secondary = setOf(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT),
            weakMatches = setOf(ProgramSlotId.UPPER_PULL_ANCHOR),
            source = SlotCapabilitySource.RUNTIME_METADATA
        )

        assertEquals(CoverageCredit.PARTIAL, CoverageAccountingPolicy.DEFAULT.credit(profile, ProgramSlotId.ACCESSORY_ROTATION))
        assertEquals(CoverageCredit.PARTIAL, CoverageAccountingPolicy.DEFAULT.credit(profile, ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT))
        assertEquals(CoverageCredit.WEAK, CoverageAccountingPolicy.DEFAULT.credit(profile, ProgramSlotId.UPPER_PULL_ANCHOR))
        assertEquals(ExposurePriority.HIGH, ExposureTargetTable.get(ProgramSlotId.UPPER_PULL_ANCHOR).priority)
    }
}

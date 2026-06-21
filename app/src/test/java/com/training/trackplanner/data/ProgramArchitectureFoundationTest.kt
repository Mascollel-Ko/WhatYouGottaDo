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
    fun capabilityResolverUsesMetadataBeforeExplicitNameFallback() {
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
    fun exposureTargetsScaleByDaysAndWindowWithoutUnlimitedDemand() {
        val slot = ProgramSlotId.UPPER_PULL_ANCHOR
        val lowDays = ExposureTargetTable.numericTarget(slot, availableDaysPerWeek = 1, windowWeeks = 2)
        val mediumDays = ExposureTargetTable.numericTarget(slot, availableDaysPerWeek = 4, windowWeeks = 2)
        val highDays = ExposureTargetTable.numericTarget(slot, availableDaysPerWeek = 7, windowWeeks = 2)
        val fourWeeks = ExposureTargetTable.numericTarget(slot, availableDaysPerWeek = 4, windowWeeks = 4)
        val optional = ExposureTargetTable.numericTarget(
            ProgramSlotId.OVERHEAD_SMASH_SUPPORT,
            availableDaysPerWeek = 7,
            windowWeeks = 4
        )

        assertEquals(1, lowDays.minimum)
        assertTrue(mediumDays.preferred > lowDays.preferred)
        assertTrue(highDays.maximum < 7 * 2)
        assertEquals(mediumDays.minimum * 2, fourWeeks.minimum)
        assertEquals(0, optional.minimum)
        assertEquals(0, optional.preferred)
    }

    @Test
    fun fatigueSlotPolicyMakesOverheadAndRotationConservativeAtOrangeAndRed() {
        val policy = FatigueSlotPolicy.DEFAULT

        assertEquals(
            FatigueSlotDisposition.LOW_LOAD_ONLY,
            policy.disposition(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT, ProgramFatigueBand.ORANGE)
        )
        assertEquals(
            FatigueSlotDisposition.LOW_LOAD_ONLY,
            policy.disposition(ProgramSlotId.ROTATIONAL_KINETIC_CHAIN, ProgramFatigueBand.ORANGE)
        )
        assertEquals(
            FatigueSlotDisposition.AVOID,
            policy.disposition(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT, ProgramFatigueBand.RED)
        )
        assertEquals(
            FatigueSlotDisposition.AVOID,
            policy.disposition(ProgramSlotId.ROTATIONAL_KINETIC_CHAIN, ProgramFatigueBand.RED)
        )
        assertEquals(
            FatigueSlotDisposition.NORMAL,
            policy.disposition(ProgramSlotId.RECOVERY_PREHAB_LIGHT, ProgramFatigueBand.RED)
        )
    }

    @Test
    fun resolverRecognizesOverheadAndRotationMetadataConservatively() {
        val landmine = exercise(2, "Half-kneeling landmine press")
        val landmineMetadata = RuntimeExerciseMetadataDefaults.forExercise(landmine).copy(
            movementFamily = "OVERHEAD_PRESS_LANDMINE_PRESS_VARIANTS",
            movementSubtype = "HALF_KNEELING_LANDMINE_PRESS",
            programSlot = "HALF_KNEELING_PRESS_STABILITY",
            secondaryStressTags = MetadataTokenField.parse("ANTI_ROTATION")
        )
        val vipr = exercise(3, "ViPR rotational lift")
        val viprMetadata = RuntimeExerciseMetadataDefaults.forExercise(vipr).copy(
            movementFamily = "ROTATIONAL_KINETIC_CHAIN",
            movementSubtype = "VIPR_ROTATIONAL_LIFT",
            programSlot = "ROTATIONAL_POWER_ACCESSORY"
        )

        val landmineProfile = SlotCapabilityResolver.DEFAULT.resolve(landmine, landmineMetadata)
        val viprProfile = SlotCapabilityResolver.DEFAULT.resolve(vipr, viprMetadata)

        assertEquals(setOf(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT), landmineProfile.primary)
        assertTrue(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY in landmineProfile.secondary)
        assertEquals(SlotCapabilityConfidence.HIGH, landmineProfile.confidence)
        assertEquals(setOf(ProgramSlotId.ROTATIONAL_KINETIC_CHAIN), viprProfile.primary)
    }

    @Test
    fun metadataFallbackIsWeakerThanRuntimeMetadata() {
        val runtimeExercise = exercise(4, "Runtime landmine")
        val runtimeMetadata = RuntimeExerciseMetadataDefaults.forExercise(runtimeExercise).copy(
            movementFamily = "OVERHEAD_PRESS_LANDMINE_PRESS_VARIANTS"
        )
        val legacyExercise = exercise(5, "Legacy overhead").copy(movementPattern = "OVERHEAD_PRESS")
        val nameOnlyExercise = exercise(6, "ViPR shovel")

        val runtime = SlotCapabilityResolver.DEFAULT.resolve(runtimeExercise, runtimeMetadata)
        val legacy = SlotCapabilityResolver.DEFAULT.resolve(legacyExercise, null)
        val nameOnly = SlotCapabilityResolver.DEFAULT.resolve(nameOnlyExercise, null)

        assertEquals(SlotCapabilityConfidence.HIGH, runtime.confidence)
        assertEquals(SlotCapabilitySource.LEGACY_METADATA, legacy.source)
        assertEquals(SlotCapabilityConfidence.LOW, legacy.confidence)
        assertEquals(SlotCapabilitySource.NAME_FALLBACK, nameOnly.source)
        assertTrue(nameOnly.primary.isEmpty())
        assertEquals(setOf(ProgramSlotId.ROTATIONAL_KINETIC_CHAIN), nameOnly.weakMatches)
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

    @Test
    fun malformedMultiPrimaryProfileCannotFullySatisfyWholeDistribution() {
        val profile = SlotCapabilityProfile(
            primary = setOf(
                ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
                ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
                ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY
            ),
            secondary = setOf(
                ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT,
                ProgramSlotId.FOREARM_GRIP_ELBOW_SUPPORT
            ),
            weakMatches = setOf(ProgramSlotId.ACCESSORY_ROTATION),
            source = SlotCapabilitySource.RUNTIME_METADATA
        )
        val policy = CoverageAccountingPolicy.DEFAULT
        val requested = profile.primary + profile.secondary + profile.weakMatches

        assertEquals(1, requested.count { policy.credit(profile, it) == CoverageCredit.FULL })
        assertEquals(CoverageCredit.PARTIAL, policy.derivedUmbrellaCredit(profile, ProgramSlotId.OVERHEAD_SMASH_SUPPORT))
        assertTrue(policy.totalCredit(profile, requested) <= CoverageAccountingPolicy.MAX_CREDIT_PER_EXERCISE)
        assertFalse(policy.derivedUmbrellaCredit(profile, ProgramSlotId.OVERHEAD_SMASH_SUPPORT) == CoverageCredit.FULL)

        val singleComponent = profile.copy(
            primary = setOf(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT),
            secondary = emptySet(),
            weakMatches = emptySet()
        )
        assertEquals(
            CoverageCredit.WEAK,
            policy.derivedUmbrellaCredit(singleComponent, ProgramSlotId.OVERHEAD_SMASH_SUPPORT)
        )
    }

    private fun exercise(id: Long, name: String): Exercise = Exercise(
        id = id,
        name = name,
        category = "strength",
        stableKey = "architecture_$id"
    )
}

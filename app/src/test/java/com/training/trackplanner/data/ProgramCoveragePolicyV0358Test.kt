package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramCoveragePolicyV0358Test {
    private val policy = CoverageAccountingPolicy.DEFAULT

    @Test
    fun overheadSmashSupportRequiresMultipleComponentsAndContributors() {
        val components = listOf(
            profile(ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT),
            profile(ProgramSlotId.ROTATIONAL_KINETIC_CHAIN),
            profile(ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT)
        )
        val coverage = policy.derivedUmbrellaCoverage(components, ProgramSlotId.OVERHEAD_SMASH_SUPPORT)

        assertTrue(coverage.satisfied)
        assertEquals(3, coverage.representedComponents.size)
        assertEquals(3, coverage.contributorCount)

        val oneExercise = profile(
            primary = ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT,
            secondary = setOf(
                ProgramSlotId.ROTATIONAL_KINETIC_CHAIN,
                ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT
            )
        )
        val oneExerciseCoverage = policy.derivedUmbrellaCoverage(
            listOf(oneExercise),
            ProgramSlotId.OVERHEAD_SMASH_SUPPORT
        )

        assertFalse(oneExerciseCoverage.satisfied)
        assertEquals(1, oneExerciseCoverage.contributorCount)
        assertFalse(policy.derivedUmbrellaCredit(oneExercise, ProgramSlotId.OVERHEAD_SMASH_SUPPORT) == CoverageCredit.FULL)

        val repeatedExercise = policy.derivedUmbrellaCoverageByContributor(
            contributors = listOf("same-key" to oneExercise, "same-key" to oneExercise),
            umbrellaSlot = ProgramSlotId.OVERHEAD_SMASH_SUPPORT
        )
        assertFalse(repeatedExercise.satisfied)
        assertEquals(1, repeatedExercise.contributorCount)
    }

    @Test
    fun diagnosticsSeparateDerivedUmbrellaFromWeakDirectCoverage() {
        val pairs = listOf(
            metadataPair(1, "overhead", "OVERHEAD_PRESS_VARIANTS", "DUMBBELL_OVERHEAD_PRESS", "OVERHEAD_PUSH_STRENGTH_OR_ACCESSORY"),
            metadataPair(2, "rotation", "ROTATIONAL_KINETIC_CHAIN", "CABLE_CHOP", "ROTATIONAL_KINETIC_CHAIN"),
            metadataPair(3, "scapular", "SCAPULAR_SHOULDER_SUPPORT", "KETTLEBELL_HALO", "SCAPULAR_SHOULDER_SUPPORT")
        )
        val diagnostic = ProgramSlotCoverageDiagnostics().analyze(
            exercises = pairs.map { it.first },
            catalog = RuntimeExerciseMetadataCatalog.of(pairs.map { it.second })
        ).first { it.slot == ProgramSlotId.OVERHEAD_SMASH_SUPPORT }

        assertEquals(0, diagnostic.candidateCount)
        assertEquals(SlotCoverageMode.DERIVED_UMBRELLA, diagnostic.coverageMode)
        assertEquals(SlotCoverageStrength.ADEQUATE, diagnostic.coverageStrength)
        assertEquals(3, diagnostic.representedDerivedComponents.size)
        assertTrue(diagnostic.derivedContributorCount >= 2)
    }

    @Test
    fun recoveryDiagnosticAcceptsMetadataBackedSecondaryCandidates() {
        val pairs = (1L..4L).map { id ->
            metadataPair(
                id = id,
                name = "recovery-$id",
                family = "SCAPULAR_CONTROL_RECOVERY_PREHAB_VARIANTS",
                subtype = "CONTROL_VARIANT_$id",
                slot = "RECOVERY_PREHAB_SCAPULAR_CONTROL"
            )
        }
        val diagnostic = ProgramSlotCoverageDiagnostics().analyze(
            exercises = pairs.map { it.first },
            catalog = RuntimeExerciseMetadataCatalog.of(pairs.map { it.second })
        ).first { it.slot == ProgramSlotId.RECOVERY_PREHAB_LIGHT }

        assertEquals(SlotCoverageMode.DIRECT, diagnostic.coverageMode)
        assertEquals(0, diagnostic.strongMetadataMatchCount)
        assertEquals(4, diagnostic.secondaryMetadataMatchCount)
        assertEquals(SlotCoverageStrength.ADEQUATE, diagnostic.coverageStrength)
        assertEquals(0, diagnostic.nameFallbackMatchCount)
    }

    @Test
    fun validatorWarnsWhenOverheadIntentHasNoDerivedComponents() {
        val missing = resultWithItems(
            listOf(item(1, requestedSlot = ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT))
        )
        assertTrue(ProgramBuilderValidator.validate(missing).any {
            it.code == "OVERHEAD_SMASH_COMPONENT_COVERAGE_MISSING"
        })

        val satisfied = resultWithItems(
            listOf(
                item(1, ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT, ProgramSlotId.ATHLETIC_OVERHEAD_PRESS_SUPPORT),
                item(2, ProgramSlotId.ROTATIONAL_KINETIC_CHAIN, ProgramSlotId.ROTATIONAL_KINETIC_CHAIN),
                item(3, ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT, ProgramSlotId.SCAPULAR_SHOULDER_SUPPORT)
            )
        )
        assertFalse(ProgramBuilderValidator.validate(satisfied).any {
            it.code.startsWith("OVERHEAD_SMASH_COMPONENT_COVERAGE_")
        })
    }

    private fun profile(
        primary: ProgramSlotId,
        secondary: Set<ProgramSlotId> = emptySet()
    ) = SlotCapabilityProfile(
        primary = setOf(primary),
        secondary = secondary,
        weakMatches = emptySet(),
        source = SlotCapabilitySource.RUNTIME_METADATA,
        confidence = SlotCapabilityConfidence.HIGH
    )

    private fun metadataPair(
        id: Long,
        name: String,
        family: String,
        subtype: String,
        slot: String
    ): Pair<Exercise, RuntimeExerciseMetadata> {
        val exercise = Exercise(id = id, name = name, category = "TRAINING", stableKey = "policy_$id")
        return exercise to RuntimeExerciseMetadataDefaults.forExercise(exercise).copy(
            movementFamily = family,
            movementSubtype = subtype,
            programSlot = slot,
            planningEligibility = "PROGRAM_SELECTABLE"
        )
    }

    private fun resultWithItems(items: List<ProgramSkeletonItem>) = GeneratedProgramSkeleton(
        suggestedName = "policy-test",
        durationDays = 28,
        request = ProgramSkeletonRequest(
            name = "policy-test",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 3,
            sessionMinutes = 60,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.7,
            sportStrengthRatio = "70:30",
            periodizationType = ProgramPeriodizationType.LINEAR_STRENGTH,
            durationWeeks = 4
        ),
        periodizationType = ProgramPeriodizationType.LINEAR_STRENGTH,
        weekPlans = (1..4).map { week ->
            ProgramWeekPlan(week, "BUILD", 1.0, 1.0, 2, 1.0, 2, 2, false)
        },
        items = items
    )

    private fun item(
        id: Int,
        primarySlot: ProgramSlotId? = null,
        requestedSlot: ProgramSlotId
    ) = ProgramSkeletonItem(
        localId = "item-$id",
        weekNumber = 1,
        dayOfWeek = id,
        orderIndex = 1,
        exerciseId = id.toLong(),
        exerciseName = "item-$id",
        category = "TRAINING",
        restSeconds = 60,
        prescription = "2 x 8",
        setCount = 2,
        reps = 8,
        weightKg = 0.0,
        seconds = 0,
        selectionReason = "test",
        weightSource = "NONE",
        primarySlotCapabilities = primarySlot?.let { listOf(it.name) }.orEmpty(),
        slotCapabilitySource = SlotCapabilitySource.RUNTIME_METADATA.name,
        slotCapabilityConfidence = SlotCapabilityConfidence.HIGH.name,
        requestedTemplateSlot = requestedSlot.name
    )
}

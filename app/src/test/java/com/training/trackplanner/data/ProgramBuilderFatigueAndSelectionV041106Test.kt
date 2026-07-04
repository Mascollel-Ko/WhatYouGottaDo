package com.training.trackplanner.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramBuilderFatigueAndSelectionV041106Test {
    @Test
    fun planningFatigueKeepsFoundationAnchorWhileTodayExecutionCanBlockIt() {
        val redGate = ProgramFatigueGate(
            band = ProgramFatigueBand.RED,
            volumeFactor = 0.25,
            rpeCap = 7,
            allowsHeavyLower = false,
            allowsHighImpact = false,
            allowsHighIntensityCod = false,
            lowerBodyRestricted = true
        )
        val hinge = candidate(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN)

        assertFalse(FatigueSlotPolicy.DEFAULT.allows(hinge, redGate, ProgramFatigueUseCase.TODAY_EXECUTION))
        assertTrue(FatigueSlotPolicy.DEFAULT.allows(hinge, redGate, ProgramFatigueUseCase.PROGRAM_PLANNING))
    }

    @Test
    fun redPlanningFatigueDownscalesFoundationAnchorPrescription() {
        val prescription = ProgramPrescriptionPolicy().prescribe(
            candidate = candidate(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN),
            role = ProgramExerciseRole.ANCHOR,
            week = week(),
            gate = ProgramFatigueGate(
                band = ProgramFatigueBand.RED,
                volumeFactor = 0.25,
                rpeCap = 7,
                allowsHeavyLower = false,
                allowsHighImpact = false,
                allowsHighIntensityCod = false,
                lowerBodyRestricted = true
            )
        )

        assertTrue("foundation anchor should be kept as a small dose", prescription.setCount in 1..2)
        assertTrue("red planning fatigue should cap heavy lower RPE", prescription.rpe <= 6)
    }

    @Test
    fun planningFatigueReasonUsesAdjustmentLanguage() {
        val reason = ProgramSelectionReasonFormatter().format(
            candidate = candidate(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN),
            role = ProgramExerciseRole.ANCHOR,
            gate = ProgramFatigueGate(
                band = ProgramFatigueBand.RED,
                volumeFactor = 0.25,
                rpeCap = 7,
                allowsHeavyLower = false,
                allowsHighImpact = false,
                allowsHighIntensityCod = false,
                lowerBodyRestricted = true
            )
        )

        assertTrue(reason.contains("계획피로조정:RED"))
    }

    @Test
    fun requestCarriesSelectedExerciseStableKeysAlongsideLegacyMemo() {
        val fieldNames = ProgramSkeletonRequest::class.java.declaredFields.map { it.name }.toSet()

        assertTrue("free-text memo field still exists", "excludedExerciseText" in fieldNames)
        assertTrue("selected excluded stableKey field is wired", "excludedExerciseStableKeys" in fieldNames)
        assertTrue("selected preferred stableKey field is wired", "preferredExerciseStableKeys" in fieldNames)
    }

    @Test
    fun requestStableKeySelectionsDefaultToEmptySets() {
        val request = ProgramSkeletonRequest(
            name = "fixture",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 5,
            sessionMinutes = 45,
            availableEquipment = emptySet(),
            excludedExerciseText = "legacy memo",
            badmintonTransferRatio = 0.60,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO
        )

        assertTrue(request.excludedExerciseStableKeys.isEmpty())
        assertTrue(request.preferredExerciseStableKeys.isEmpty())
    }

    @Test
    fun inventoryHardExcludesSelectedStableKeysOnly() {
        val kept = Exercise(
            id = 1,
            name = "Barbell squat",
            category = "strength",
            stableKey = "barbell_squat",
            equipment = "BARBELL",
            planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
        )
        val excluded = Exercise(
            id = 2,
            name = "Captain chair leg raise",
            category = "strength",
            stableKey = "captain_chair_leg_raise",
            equipment = "BODYWEIGHT",
            planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
        )

        val result = ProgramCandidateInventory().collect(
            exercises = listOf(kept, excluded),
            runtimeMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY,
            availableEquipment = setOf("BARBELL", "BODYWEIGHT"),
            excludedExerciseStableKeys = setOf("captain_chair_leg_raise")
        )

        assertTrue(result.candidates.any { it.exercise.stableKey == "barbell_squat" })
        assertTrue(result.candidates.none { it.exercise.stableKey == "captain_chair_leg_raise" })
    }

    @Test
    fun preferredStableKeyBoostsCandidateReranking() {
        val preferred = candidate(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN)
        val request = baseRequest().copy(preferredExerciseStableKeys = setOf(preferred.exercise.stableKey))
        val context = ProgramCandidateScoreContext(
            request = request,
            week = week(),
            periodizedWeek = ProgramPeriodizationWeekPlan(
                weekIndex = 1,
                role = ProgramWeekRole.FOUNDATION_LOAD,
                dayProfiles = mapOf(1 to ProgramDayProfile.HARD_FOUNDATION)
            ),
            plannedSlot = PlannedSlot(1, ProgramTrainingSlot.LOWER_STRENGTH, ProgramDayIntensity.HARD),
            templateSlot = TemplateExerciseSlot(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN, ProgramExerciseRole.ANCHOR),
            selectedInSession = emptyList(),
            generatedItems = emptyList()
        )
        val policy = ProgramCandidateRerankingPolicy()
        val classification = ProgramCandidateClassificationPolicy().classify(preferred)

        val boosted = policy.adjustment(preferred, classification, context)
        val neutral = policy.adjustment(preferred, classification, context.copy(request = request.copy(preferredExerciseStableKeys = emptySet())))

        assertTrue(boosted > neutral)
    }

    @Test
    fun exerciseConstraintSummaryReportsExcludedAndPreferredSelections() {
        val request = baseRequest().copy(
            excludedExerciseStableKeys = setOf("captain_chair_leg_raise"),
            preferredExerciseStableKeys = setOf("fixture_hinge", "missing_preferred")
        )
        val skeleton = GeneratedProgramSkeleton(
            suggestedName = "fixture",
            durationDays = 28,
            request = request,
            periodizationType = ProgramPeriodizationType.AUTO,
            weekPlans = listOf(week()),
            items = listOf(
                ProgramSkeletonItem(
                    localId = "1",
                    weekNumber = 1,
                    dayOfWeek = 1,
                    orderIndex = 1,
                    exerciseId = 1,
                    exerciseName = "Fixture hinge",
                    category = "strength",
                    restSeconds = 90,
                    prescription = "1x5",
                    setCount = 1,
                    reps = 5,
                    weightKg = 0.0,
                    seconds = 0,
                    selectionReason = "",
                    weightSource = "",
                    stableKey = "fixture_hinge"
                )
            )
        ).withExerciseConstraintSummary()

        assertTrue(skeleton.optimizationSummary.messages.any { it.contains("제외 운동 1개") })
        assertTrue(skeleton.optimizationSummary.messages.any { it.contains("우선 포함 운동 1개") })
        assertTrue(skeleton.optimizationSummary.messages.any { it.contains("반영되지 않았습니다") })
    }

    private fun candidate(slot: ProgramSlotId): ProgramCandidate =
        ProgramCandidate(
            exercise = Exercise(
                id = 1,
                name = "Fixture hinge",
                category = "strength",
                stableKey = "fixture_hinge",
                planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
            ),
            metadata = RuntimeExerciseMetadataDefaults.forExercise(
                Exercise(
                    id = 1,
                    name = "Fixture hinge",
                    category = "strength",
                    stableKey = "fixture_hinge",
                    planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
                )
            ).copy(
                movementFamily = "HEAVY_HINGE",
                movementSubtype = "DEADLIFT",
                programSlot = slot.name,
                stressMagnitudeHint = "HIGH"
            ),
            canonical = true,
            slotCapabilities = SlotCapabilityProfile(
                primary = setOf(slot),
                secondary = emptySet(),
                weakMatches = emptySet(),
                source = SlotCapabilitySource.RUNTIME_METADATA,
                confidence = SlotCapabilityConfidence.HIGH
            )
        )

    private fun week(): ProgramWeekPlan =
        ProgramWeekPlan(
            weekIndex = 1,
            weekType = ProgramWeekType.BUILD.name,
            volumeMultiplier = 1.0,
            intensityMultiplier = 1.0,
            heavyExposureLimit = 2,
            lowerBodyFatigueLimit = 8.0,
            axialLoadLimit = 2,
            plyometricLimit = 1,
            deloadFlag = false,
            targetRpeMax = 8.0
        )

    private fun baseRequest(): ProgramSkeletonRequest =
        ProgramSkeletonRequest(
            name = "fixture",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 5,
            sessionMinutes = 45,
            availableEquipment = setOf("BARBELL", "BODYWEIGHT"),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.60,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO
        )
}

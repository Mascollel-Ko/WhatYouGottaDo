package com.training.trackplanner.data

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramBuilderReservoirBeamV041105Test {
    @Test
    fun periodizationInputDerivesWeekRolesAndDayProfiles() {
        val request = request(periodizationType = ProgramPeriodizationType.AUTO)
        val catalog = ProgramTemplateCatalog.DEFAULT
        val policy = ProgramPeriodizationPlanPolicy()
        val selectedType = policy.resolveType(request)
        val baseWeeks = catalog.weekPlans(request.durationWeeks, FatigueSlotPolicy.DEFAULT.gate(null as DailyFatigueState?))
        val schedule = catalog.slots(request.availableDaysPerWeek)
        val wavePlan = policy.plan(
            request = request,
            type = selectedType,
            weekPlans = policy.weekPlans(request, selectedType, baseWeeks),
            schedule = schedule
        )
        val linearPlan = policy.plan(
            request = request.copy(periodizationType = ProgramPeriodizationType.LINEAR_STRENGTH),
            type = ProgramPeriodizationType.LINEAR_STRENGTH,
            weekPlans = policy.weekPlans(request, ProgramPeriodizationType.LINEAR_STRENGTH, baseWeeks),
            schedule = schedule
        )
        val undulatingPlan = policy.plan(
            request = request.copy(periodizationType = ProgramPeriodizationType.DAILY_UNDULATING),
            type = ProgramPeriodizationType.DAILY_UNDULATING,
            weekPlans = policy.weekPlans(request, ProgramPeriodizationType.DAILY_UNDULATING, baseWeeks),
            schedule = schedule
        )

        assertTrue(selectedType == ProgramPeriodizationType.BADMINTON_WAVE)
        assertTrue("badminton wave should not use identical week roles",
            wavePlan.map(ProgramPeriodizationWeekPlan::role).distinct().size > 1)
        assertTrue("linear strength should prioritize foundation roles",
            linearPlan.take(3).all { it.role == ProgramWeekRole.LINEAR_FOUNDATION })
        assertTrue("daily undulating should keep hard/medium/light day profile differences",
            undulatingPlan.first().dayProfiles.values.toSet().size > 1)
    }

    @Test
    fun badSkeletonWithMissingAnchorsAndRepeatedCoreCannotScorePerfect() {
        val skeleton = skeleton(
            days = listOf(1, 2, 4, 6, 7),
            itemFactory = { week, day, order ->
                repeatedCaptainChairItem(week, day, order)
            }
        )

        val evaluation = ProgramEvaluationPolicy().evaluate(skeleton)

        assertTrue("bad skeleton must not score 100", evaluation.overallScore < 100)
        assertTrue(
            "missing foundation anchors should be detected",
            evaluation.issues.any { it.type == ProgramEvaluationIssueType.LOW_STRENGTH_ANCHOR }
        )
        assertTrue(
            "loaded strength underuse should be detected when all equipment is available",
            evaluation.issues.any { it.type == ProgramEvaluationIssueType.LOADED_STRENGTH_UNDERUSED }
        )
    }

    @Test
    fun sixCornerShadowFootworkIsAllowedAsTransferWhenItIsNotDirectSportSession() {
        val item = transferItem(week = 1, day = 1, order = 1, name = "6-corner shadow footwork")

        assertFalse("6-corner footwork should not be treated as direct sport session here", item.directSportSession)
        assertTrue("6-corner footwork can remain a badminton transfer item",
            item.selectionRole == ProgramExerciseRole.TRANSFER.name)
    }

    @Test
    fun repeatedCaptainChairFixtureRepresentsCoreAccessoryNotFoundationAnchor() {
        val item = repeatedCaptainChairItem(week = 1, day = 1, order = 1)

        assertTrue(item.selectionRole == ProgramExerciseRole.CORE.name)
        assertTrue(item.requestedTemplateSlot == ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name)
        assertFalse("captain chair must not masquerade as a foundation anchor",
            item.primarySlotCapabilities.any(FOUNDATION_SLOT_NAMES::contains))
    }

    @Test
    fun candidateReservoirClassifiesFoundationLoadedAndCorePatterns() {
        val policy = ProgramCandidateClassificationPolicy()
        val squat = candidate(
            id = 1,
            name = "Barbell back squat",
            equipment = "BARBELL",
            stableKey = "barbell_back_squat",
            capabilities = SlotCapabilityProfile(
                primary = setOf(ProgramSlotId.LOWER_SQUAT_PATTERN),
                secondary = emptySet(),
                weakMatches = emptySet(),
                source = SlotCapabilitySource.RUNTIME_METADATA,
                confidence = SlotCapabilityConfidence.HIGH
            )
        )
        val captainChair = candidate(
            id = 2,
            name = "Captain chair leg raise",
            equipment = "BODYWEIGHT",
            stableKey = "captain_chair_leg_raise",
            capabilities = SlotCapabilityProfile(
                primary = setOf(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY),
                secondary = emptySet(),
                weakMatches = emptySet(),
                source = SlotCapabilitySource.RUNTIME_METADATA,
                confidence = SlotCapabilityConfidence.MODERATE
            )
        )
        val reservoir = ProgramCandidateReservoir(listOf(squat, captainChair))

        assertTrue(policy.classify(squat).tier == ProgramCandidateTier.FOUNDATION_MAIN_WORTHY)
        assertTrue(ProgramFoundationPattern.SQUAT in reservoir.classification(squat).foundationPatterns)
        assertTrue(reservoir.classification(captainChair).tier == ProgramCandidateTier.CORE_ACCESSORY_PREHAB)
        assertTrue(
            reservoir.classification(captainChair).corePattern ==
                ProgramCorePattern.TRUNK_FLEXION_HIP_FLEXION
        )
        assertTrue("reservoir should keep hard-gate-eligible candidates", reservoir.candidates.size == 2)
    }

    @Test
    fun foundationPolicyReservesAnchorsBeforeAccessorySlots() {
        val policy = ProgramFoundationAnchorPolicy()
        val request = request(periodizationType = ProgramPeriodizationType.BADMINTON_WAVE)
        val periodizedWeek = ProgramPeriodizationWeekPlan(
            weekIndex = 1,
            role = ProgramWeekRole.FOUNDATION_LOAD,
            dayProfiles = mapOf(1 to ProgramDayProfile.HARD_FOUNDATION)
        )
        val slots = policy.reserveSlots(
            slots = listOf(
                TemplateExerciseSlot(null, ProgramExerciseRole.CORE),
                TemplateExerciseSlot(null, ProgramExerciseRole.PREHAB)
            ),
            request = request,
            week = periodizedWeek,
            plannedSlot = PlannedSlot(
                dayOfWeek = 1,
                slot = ProgramTrainingSlot.LOWER_STRENGTH,
                intensity = ProgramDayIntensity.HARD
            )
        )

        assertTrue(slots.first().targetSlot == ProgramSlotId.LOWER_SQUAT_PATTERN)
        assertTrue(slots.first().role == ProgramExerciseRole.ANCHOR)
        assertTrue(slots.first().required)
    }

    @Test
    fun rerankingChangesCandidateScoreWithProgramContext() {
        val policy = ProgramCandidateRerankingPolicy()
        val request = request(periodizationType = ProgramPeriodizationType.BADMINTON_WAVE)
        val week = ProgramWeekPlan(
            weekIndex = 1,
            weekType = ProgramWeekType.BUILD.name,
            volumeMultiplier = 1.0,
            intensityMultiplier = 1.0,
            heavyExposureLimit = 2,
            lowerBodyFatigueLimit = 8.0,
            axialLoadLimit = 2,
            plyometricLimit = 1,
            deloadFlag = false
        )
        val context = ProgramCandidateScoreContext(
            request = request,
            week = week,
            periodizedWeek = ProgramPeriodizationWeekPlan(
                weekIndex = 1,
                role = ProgramWeekRole.FOUNDATION_LOAD,
                dayProfiles = mapOf(1 to ProgramDayProfile.HARD_FOUNDATION)
            ),
            plannedSlot = PlannedSlot(1, ProgramTrainingSlot.LOWER_STRENGTH, ProgramDayIntensity.HARD),
            templateSlot = TemplateExerciseSlot(ProgramSlotId.LOWER_SQUAT_PATTERN, ProgramExerciseRole.ANCHOR),
            selectedInSession = emptyList(),
            generatedItems = listOf(repeatedCaptainChairItem(week = 1, day = 1, order = 1))
        )
        val foundation = candidate(
            id = 3,
            name = "Barbell hinge",
            equipment = "BARBELL",
            stableKey = "barbell_hinge",
            capabilities = SlotCapabilityProfile(
                primary = setOf(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN),
                secondary = emptySet(),
                weakMatches = emptySet(),
                source = SlotCapabilitySource.RUNTIME_METADATA,
                confidence = SlotCapabilityConfidence.HIGH
            )
        )
        val captainChair = candidate(
            id = 4,
            name = "Captain chair leg raise",
            equipment = "BODYWEIGHT",
            stableKey = "captain_chair_leg_raise",
            capabilities = SlotCapabilityProfile(
                primary = setOf(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY),
                secondary = emptySet(),
                weakMatches = emptySet(),
                source = SlotCapabilitySource.RUNTIME_METADATA,
                confidence = SlotCapabilityConfidence.MODERATE
            )
        )
        val classifier = ProgramCandidateClassificationPolicy()

        val foundationAdjustment = policy.adjustment(foundation, classifier.classify(foundation), context)
        val repeatedCoreAdjustment = policy.adjustment(captainChair, classifier.classify(captainChair), context)

        assertTrue("foundation deficit should raise foundation candidates", foundationAdjustment > 0.0)
        assertTrue("repeated trunk flexion should be penalized", repeatedCoreAdjustment < 0.0)
    }

    @Test
    fun beamSelectionKeepsAWideSlotCandidateWindow() {
        val policy = ProgramBeamSelectionPolicy()
        val scored = (1L..10L).map { id ->
            candidate(
                id = id,
                name = "Candidate $id",
                equipment = "BARBELL",
                stableKey = "candidate_$id",
                capabilities = SlotCapabilityProfile(
                    primary = setOf(ProgramSlotId.UPPER_PULL_ANCHOR),
                    secondary = emptySet(),
                    weakMatches = emptySet(),
                    source = SlotCapabilitySource.RUNTIME_METADATA,
                    confidence = SlotCapabilityConfidence.HIGH
                )
            ) to (100.0 - id)
        }

        assertTrue("slot selection should inspect more than a fixed top three",
            policy.candidateWindow(scored, desiredExerciseCount = 4).size >= 6)
    }

    private fun skeleton(
        days: List<Int>,
        itemFactory: (week: Int, day: Int, order: Int) -> ProgramSkeletonItem
    ): GeneratedProgramSkeleton {
        val request = ProgramSkeletonRequest(
            name = "reservoir selection gap fixture",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = days.size,
            sessionMinutes = 45,
            availableEquipment = setOf("BARBELL", "DUMBBELL", "MACHINE", "CABLE", "BODYWEIGHT"),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.60,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.BADMINTON_WAVE,
            durationWeeks = 4
        )
        val weeks = (1..4).map { week ->
            ProgramWeekPlan(
                weekIndex = week,
                weekType = ProgramWeekType.BUILD.name,
                volumeMultiplier = 1.0,
                intensityMultiplier = 1.0,
                heavyExposureLimit = 2,
                lowerBodyFatigueLimit = 8.0,
                axialLoadLimit = 2,
                plyometricLimit = 1,
                deloadFlag = false
            )
        }
        return GeneratedProgramSkeleton(
            suggestedName = request.name,
            durationDays = 28,
            request = request,
            periodizationType = ProgramPeriodizationType.BADMINTON_WAVE,
            weekPlans = weeks,
            items = weeks.flatMap { week ->
                days.flatMap { day -> (1..4).map { order -> itemFactory(week.weekIndex, day, order) } }
            }
        )
    }

    private fun request(periodizationType: ProgramPeriodizationType): ProgramSkeletonRequest =
        ProgramSkeletonRequest(
            name = "reservoir selection gap fixture",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = 5,
            sessionMinutes = 45,
            availableEquipment = setOf("BARBELL", "DUMBBELL", "MACHINE", "CABLE", "BODYWEIGHT"),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.60,
            sportStrengthRatio = "AUTO",
            periodizationType = periodizationType,
            durationWeeks = 4
        )

    private fun repeatedCaptainChairItem(week: Int, day: Int, order: Int): ProgramSkeletonItem =
        ProgramSkeletonItem(
            localId = "$week-$day-$order",
            weekNumber = week,
            dayOfWeek = day,
            orderIndex = order,
            exerciseId = 10_000L + week * 100 + day * 10 + order,
            exerciseName = "Captain chair leg raise",
            category = "strength",
            restSeconds = 60,
            prescription = "2x12",
            setCount = 2,
            reps = 12,
            weightKg = 0.0,
            seconds = 0,
            selectionReason = "",
            weightSource = "",
            trainingSlot = ProgramTrainingSlot.RECOVERY_WEAKPOINT.name,
            dayIntensity = ProgramDayIntensity.MODERATE.name,
            stableKey = "captain_chair_leg_raise",
            selectionRole = ProgramExerciseRole.CORE.name,
            movementFamily = "CORE_FLEXION_ANTERIOR_CORE",
            movementSubtype = "CAPTAINS_CHAIR_LEG_RAISE",
            redundancyGroup = "CORE_FLEXION",
            badmintonTransferLevel = "GENERAL",
            primarySlotCapabilities = listOf(ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name),
            requestedTemplateSlot = ProgramSlotId.TRUNK_ANTI_ROTATION_STABILITY.name
        )

    private fun transferItem(week: Int, day: Int, order: Int, name: String): ProgramSkeletonItem =
        ProgramSkeletonItem(
            localId = "$week-$day-$order",
            weekNumber = week,
            dayOfWeek = day,
            orderIndex = order,
            exerciseId = 20_000L + week * 100 + day * 10 + order,
            exerciseName = name,
            category = "functional",
            restSeconds = 60,
            prescription = "3x20s",
            setCount = 3,
            reps = 0,
            weightKg = 0.0,
            seconds = 20,
            selectionReason = "",
            weightSource = "",
            trainingSlot = ProgramTrainingSlot.BADMINTON_TRANSFER.name,
            dayIntensity = ProgramDayIntensity.MODERATE.name,
            stableKey = "six_corner_shadow_footwork",
            selectionRole = ProgramExerciseRole.TRANSFER.name,
            movementFamily = "COURT_FOOTWORK",
            redundancyGroup = "FOOTWORK",
            badmintonTransferLevel = "DIRECT",
            primarySlotCapabilities = listOf(ProgramSlotId.BADMINTON_FOOTWORK_REACTION.name),
            requestedTemplateSlot = ProgramSlotId.BADMINTON_FOOTWORK_REACTION.name,
            directSportSession = false
        )

    private fun candidate(
        id: Long,
        name: String,
        equipment: String,
        stableKey: String,
        capabilities: SlotCapabilityProfile
    ): ProgramCandidate =
        ProgramCandidate(
            exercise = Exercise(
                id = id,
                name = name,
                category = "strength",
                stableKey = stableKey,
                equipment = equipment,
                planningEligibility = PlanningEligibility.PROGRAM_SELECTABLE.name
            ),
            metadata = null,
            canonical = false,
            slotCapabilities = capabilities
        )

    private companion object {
        val FOUNDATION_SLOT_NAMES = setOf(
            ProgramSlotId.LOWER_SQUAT_PATTERN.name,
            ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN.name,
            ProgramSlotId.UPPER_PULL_ANCHOR.name,
            ProgramSlotId.UPPER_PUSH_SUPPORT.name
        )
    }
}

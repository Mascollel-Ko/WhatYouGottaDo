package com.training.trackplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramExerciseReasonBuilderTest {
    @Test
    fun anchorSlotGetsReadableAnchorReason() {
        val labels = ProgramExerciseReasonBuilder.labels(
            candidate = candidate(
                slotCapabilities = profile(primary = setOf(ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN))
            ),
            role = ProgramExerciseRole.ANCHOR,
            requestedSlot = ProgramSlotId.HIP_HINGE_POSTERIOR_CHAIN,
            dayIntensity = ProgramDayIntensity.HARD,
            week = buildWeek(),
            fatigueGate = ProgramFatigueGate.from(null)
        )

        assertTrue(labels.contains("힌지 앵커"))
        assertTrue(labels.contains("강약 배치"))
        assertTrue(labels.size <= 4)
    }

    @Test
    fun badmintonTransferSlotGetsTransferAndDecelerationReasons() {
        val labels = ProgramExerciseReasonBuilder.labels(
            candidate = candidate(
                metadata = metadata(
                    badmintonTransferLevel = "SUPPORTIVE",
                    badmintonPhysicalQualities = "DECELERATION|CHANGE_OF_DIRECTION"
                ),
                slotCapabilities = profile(primary = setOf(ProgramSlotId.BADMINTON_DECEL_COD))
            ),
            role = ProgramExerciseRole.TRANSFER,
            requestedSlot = ProgramSlotId.BADMINTON_DECEL_COD,
            dayIntensity = ProgramDayIntensity.MODERATE,
            week = buildWeek(),
            fatigueGate = ProgramFatigueGate.from(null)
        )

        assertTrue(labels.contains("배드민턴 보조전이"))
        assertTrue(labels.contains("감속 안정성"))
        assertTrue(labels.contains("방향전환 보조"))
        assertTrue(labels.size <= 4)
    }

    @Test
    fun fallbackCandidateGetsFallbackReasonWithoutMetadata() {
        val labels = ProgramExerciseReasonBuilder.labels(
            candidate = candidate(
                metadata = null,
                canonical = false,
                slotCapabilities = profile(
                    weak = setOf(ProgramSlotId.UPPER_PULL_ANCHOR),
                    source = SlotCapabilitySource.NAME_FALLBACK,
                    confidence = SlotCapabilityConfidence.LOW
                )
            ),
            role = ProgramExerciseRole.ACCESSORY,
            requestedSlot = null,
            dayIntensity = ProgramDayIntensity.MODERATE,
            week = buildWeek(),
            fatigueGate = ProgramFatigueGate.from(null)
        )

        assertTrue(labels.contains("fallback 선택"))
        assertTrue(labels.size <= 4)
    }

    @Test
    fun redFatigueRecoveryCandidateGetsLowIntensityReason() {
        val labels = ProgramExerciseReasonBuilder.labels(
            candidate = candidate(
                metadata = metadata(
                    movementFamily = "RECOVERY_PREHAB_CONTROL",
                    programSlot = "RECOVERY_PREHAB_LIGHT",
                    strengthProgressionGroup = "NOT_APPLICABLE"
                ),
                slotCapabilities = profile(primary = setOf(ProgramSlotId.RECOVERY_PREHAB_LIGHT))
            ),
            role = ProgramExerciseRole.PREHAB,
            requestedSlot = ProgramSlotId.RECOVERY_PREHAB_LIGHT,
            dayIntensity = ProgramDayIntensity.LIGHT,
            week = buildWeek(deload = true),
            fatigueGate = ProgramFatigueGate(
                band = ProgramFatigueBand.RED,
                volumeFactor = 0.25,
                rpeCap = 7,
                allowsHeavyLower = false,
                allowsHighImpact = false,
                allowsHighIntensityCod = false,
                lowerBodyRestricted = true
            )
        )

        assertTrue(labels.contains("회복성 보조"))
        assertTrue(labels.contains("저강도 대체"))
        assertTrue(labels.contains("피로도 고려"))
        assertTrue(labels.size <= 4)
    }

    private fun candidate(
        metadata: RuntimeExerciseMetadata? = metadata(),
        canonical: Boolean = metadata != null,
        slotCapabilities: SlotCapabilityProfile = profile(primary = setOf(ProgramSlotId.LOWER_SQUAT_PATTERN))
    ): ProgramCandidate =
        ProgramCandidate(
            exercise = Exercise(
                id = 1,
                name = "test exercise",
                category = "TRAINING",
                stableKey = "test_exercise",
                defaultRestSeconds = 60
            ),
            metadata = metadata,
            canonical = canonical,
            slotCapabilities = slotCapabilities
        )

    private fun profile(
        primary: Set<ProgramSlotId> = emptySet(),
        secondary: Set<ProgramSlotId> = emptySet(),
        weak: Set<ProgramSlotId> = emptySet(),
        source: SlotCapabilitySource = SlotCapabilitySource.RUNTIME_METADATA,
        confidence: SlotCapabilityConfidence = SlotCapabilityConfidence.HIGH
    ): SlotCapabilityProfile =
        SlotCapabilityProfile(
            primary = primary,
            secondary = secondary,
            weakMatches = weak,
            source = source,
            confidence = confidence
        )

    private fun buildWeek(deload: Boolean = false): ProgramWeekPlan =
        ProgramWeekPlan(
            weekIndex = 1,
            weekType = if (deload) ProgramWeekType.DELOAD.name else ProgramWeekType.BUILD.name,
            volumeMultiplier = 1.0,
            intensityMultiplier = 1.0,
            heavyExposureLimit = 2,
            lowerBodyFatigueLimit = 8.0,
            axialLoadLimit = 2,
            plyometricLimit = 1,
            deloadFlag = deload
        )

    private fun metadata(
        movementFamily: String = "SQUAT_VARIANTS",
        movementSubtype: String = "BACK_SQUAT",
        programSlot: String = "LOWER_STRENGTH",
        strengthProgressionGroup: String = "SQUAT_HEAVY_AXIAL",
        badmintonTransferLevel: String = "GENERAL",
        badmintonPhysicalQualities: String = "NONE"
    ): RuntimeExerciseMetadata =
        RuntimeExerciseMetadata(
            stableKey = "test_exercise",
            exerciseName = "test exercise",
            activityKind = "TRAINING_EXERCISE",
            planningEligibility = "PROGRAM_SELECTABLE",
            movementFamily = movementFamily,
            movementSubtype = movementSubtype,
            programSlot = programSlot,
            redundancyGroup = movementFamily,
            progressMetricType = "LOAD_REPS",
            strengthProgressionGroup = strengthProgressionGroup,
            analysisEligibility = token("STRENGTH_VOLUME"),
            primaryStressProfile = "MODERATE_STRENGTH",
            secondaryStressTags = token("NONE"),
            tendonStressTags = token("NONE"),
            ligamentJointStabilityStressTags = token("NONE"),
            jointImpactStressTags = token("NONE"),
            cognitiveStressTags = token("NONE"),
            sportContextTags = token("NONE"),
            recoveryDecayProfile = "MEDIUM",
            stressMagnitudeHint = "MODERATE",
            badmintonTransferLevel = badmintonTransferLevel,
            badmintonTransferType = token("NONE"),
            badmintonSkillTargets = token("NONE"),
            badmintonPhysicalQualities = token(badmintonPhysicalQualities),
            transferConfidence = "LOW",
            sourceConfidenceLevel = "HEURISTIC_ACCEPTED",
            finalSourceStatus = "SOURCE_ACCEPTED",
            neuromuscularStressLevel = "MODERATE",
            systemicMuscularStressLevel = "MODERATE",
            localMuscularStressLevel = "MODERATE",
            jointTendonImpactStressLevel = "LOW",
            movementFocusDemandLevel = "LOW",
            recoveryDurationClass = "MEDIUM",
            safeForSeedMutation = false,
            appCueProfile = "NONE"
        )

    private fun token(raw: String): MetadataTokenField = MetadataTokenField.parse(raw)
}

package com.training.trackplanner.data

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.FatigueConfidence
import com.training.trackplanner.analysis.fatigue.FatigueReadinessLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgramBuilderV0352Test {
    private val fixture = ProgramFixture()

    @Test
    fun oneDayFourWeeksUsesOneCompactFullBodySession() {
        val result = fixture.generate(days = 1, weeks = 4, minutes = 45, ratio = 0.70)

        assertEquals(28, result.durationDays)
        assertEquals(1, result.items.filter { it.weekNumber == 1 }.map { it.dayOfWeek }.distinct().size)
        assertTrue(result.items.groupBy { it.weekNumber to it.dayOfWeek }.values.all { it.size <= 5 })
        assertTrue(result.items.all { it.trainingSlot == ProgramTrainingSlot.FULL_BODY_BADMINTON_SUPPORT.name })
    }

    @Test
    fun threeDayFourWeekProgramBalancesLowerUpperAndTransferSlots() {
        val result = fixture.generate(days = 3, weeks = 4, minutes = 45, ratio = 0.70)
        val weekOneSlots = result.items.filter { it.weekNumber == 1 }.map { it.trainingSlot }.toSet()

        assertEquals(3, result.items.filter { it.weekNumber == 1 }.map { it.dayOfWeek }.distinct().size)
        assertTrue(ProgramTrainingSlot.LOWER_STRENGTH.name in weekOneSlots)
        assertTrue(ProgramTrainingSlot.UPPER_STRENGTH_SCAP.name in weekOneSlots)
        assertTrue(ProgramTrainingSlot.BADMINTON_TRANSFER.name in weekOneSlots)
    }

    @Test
    fun fiveDayEightWeekProgramHasLoadWaveAndControlledVariety() {
        val result = fixture.generate(days = 5, weeks = 8, minutes = 60, ratio = 0.70)

        assertEquals(8, result.weekPlans.size)
        assertEquals(ProgramWeekType.DELOAD.name, result.weekPlans[3].weekType)
        assertEquals(ProgramWeekType.REALIZATION.name, result.weekPlans.last().weekType)
        assertTrue(result.items.groupBy { it.weekNumber }.values.all { weekItems ->
            weekItems.groupBy { it.dayOfWeek }.count { (_, rows) -> rows.first().dayIntensity == "HARD" } <= 2
        })
        val transferNamesByWeek = result.items
            .filter { it.selectionReason.contains("역할:TRANSFER") }
            .groupBy { it.weekNumber }
            .mapValues { (_, rows) -> rows.map { it.exerciseId }.toSet() }
        assertTrue(transferNamesByWeek.values.distinct().size > 1)
    }

    @Test
    fun sevenDayProgramKeepsTwoHardAndThreeLightDays() {
        val result = fixture.generate(days = 7, weeks = 8, minutes = 45, ratio = 0.80)
        val weekOneDays = result.items.filter { it.weekNumber == 1 }.groupBy { it.dayOfWeek }

        assertEquals(7, weekOneDays.size)
        assertTrue(weekOneDays.values.count { it.first().dayIntensity == "HARD" } <= 2)
        assertTrue(weekOneDays.values.count { it.first().dayIntensity == "LIGHT" } >= 3)
    }

    @Test
    fun redFatigueRemovesHardDaysHeavyLowerAndHighImpact() {
        val result = fixture.generate(
            days = 5,
            weeks = 4,
            minutes = 60,
            ratio = 0.70,
            fatigue = fatigueState(ofi = 90, local = 90, joint = 92, neural = 90)
        )

        assertTrue(result.items.all { it.dayIntensity == ProgramDayIntensity.LIGHT.name })
        assertFalse(result.items.any { it.exerciseId in fixture.heavyOrImpactIds })
        assertTrue(result.items.all { item ->
            item.prescription.substringAfterLast("RPE ").toIntOrNull()?.let { it <= 7 } ?: true
        })
    }

    @Test
    fun randomBeepCueCanBeSelectedForTransferWithoutBecomingAnalysisCategory() {
        val result = fixture.generate(days = 3, weeks = 4, minutes = 45, ratio = 0.90)
        val cueItems = result.items.filter { it.exerciseId == fixture.cueExerciseId }

        assertTrue(cueItems.isNotEmpty())
        assertTrue(cueItems.all { it.selectionReason.contains("앱 cue 가능") })
        assertTrue(cueItems.all { !it.selectionReason.contains("분석:") })
    }

    @Test
    fun accessoryExerciseIsNotFixedEveryWeek() {
        val result = fixture.generate(days = 4, weeks = 8, minutes = 60, ratio = 0.60)
        val accessoryWeeks = result.items
            .filter { it.selectionReason.contains("역할:ACCESSORY") }
            .groupBy { it.exerciseId }
            .mapValues { (_, rows) -> rows.map { it.weekNumber }.distinct().size }

        assertTrue(accessoryWeeks.isNotEmpty())
        assertTrue(accessoryWeeks.values.none { it == 8 })
    }

    @Test
    fun requestBoundsAreNormalizedAtBuilderEntry() {
        val result = fixture.generate(days = 99, weeks = 99, minutes = 5, ratio = 2.0)

        assertEquals(12, result.request.durationWeeks)
        assertEquals(7, result.request.weeklyTrainingDays)
        assertEquals(15, result.request.sessionMinutes)
        assertEquals(0.9, result.request.badmintonTransferRatio, 0.0)
    }

    @Test
    fun directSportSessionsAreExcludedEvenWhenMarkedProgramSelectable() {
        val result = fixture.generate(days = 7, weeks = 4, minutes = 120, ratio = 0.90)

        assertFalse(result.items.any { it.exerciseId == fixture.directSportExerciseId })
        assertFalse(result.validationDetails.any { it.code == "DIRECT_SPORT_SESSION_EXCLUDED" })
    }

    @Test
    fun anchorsMayRepeatWhileAccessoryRotationRemainsProtected() {
        val result = fixture.generate(days = 4, weeks = 8, minutes = 60, ratio = 0.60)
        val anchorExposure = result.items.filter { it.selectionRole == "ANCHOR" }
            .groupBy(ProgramSkeletonItem::stableKey)
            .mapValues { (_, rows) -> rows.map(ProgramSkeletonItem::weekNumber).distinct().size }

        assertTrue(anchorExposure.values.any { it >= 2 })
        assertFalse(result.validationDetails.any { issue ->
            issue.code == "NON_ANCHOR_FIXED_REPETITION" &&
                anchorExposure.keys.any { key -> issue.message.contains("비-anchor $key 항목") }
        })
    }

    @Test
    fun movementFamilyExposureIsASoftPenalty() {
        val result = fixture.generate(days = 3, weeks = 4, minutes = 60, ratio = 0.70)
        val overloaded = result.copy(items = result.items.map { item ->
            if (item.weekNumber == 1) item.copy(movementFamily = "SAME_FAMILY") else item
        })
        val issues = ProgramBuilderValidator.validate(overloaded)

        assertTrue(issues.any {
            it.code == "MOVEMENT_FAMILY_EXPOSURE" && it.severity == ProgramValidationSeverity.SOFT_PENALTY
        })
    }

    @Test
    fun threeHardDaysRequireHighWeekAndFollowingLowWeek() {
        val result = fixture.generate(days = 5, weeks = 8, minutes = 60, ratio = 0.70)
        val highWave = result.copy(items = result.items.map { item ->
            if (item.weekNumber == 3 && item.dayOfWeek == 6) {
                item.copy(dayIntensity = ProgramDayIntensity.HARD.name)
            } else {
                item
            }
        })
        assertFalse(ProgramBuilderValidator.validate(highWave).any { it.code == "THREE_HARD_DAY_WAVE" })

        val noLowFollowUp = highWave.copy(weekPlans = highWave.weekPlans.map { week ->
            if (week.weekIndex == 4) week.copy(weekType = ProgramWeekType.BUILD.name, deloadFlag = false) else week
        })
        assertTrue(ProgramBuilderValidator.validate(noLowFollowUp).any { it.code == "THREE_HARD_DAY_WAVE" })
    }

    @Test
    fun twoWeekValidatorsDetectAxisAndMovementCoverageDebt() {
        val result = fixture.generate(days = 3, weeks = 2, minutes = 60, ratio = 0.70)
        val overloaded = result.copy(items = result.items.map { item ->
            item.copy(
                setCount = 8,
                neuromuscularStressLevel = "VERY_HIGH",
                jointTendonImpactStressLevel = "VERY_HIGH",
                movementFamily = "UNCLASSIFIED",
                movementSubtype = "UNCLASSIFIED",
                metadataProgramSlot = "UNCLASSIFIED",
                redundancyGroup = "UNCLASSIFIED",
                strengthProgressionGroup = "UNCLASSIFIED",
                primaryStressProfile = "UNCLASSIFIED",
                primarySlotCapabilities = emptyList(),
                secondarySlotCapabilities = emptyList(),
                weakSlotCapabilities = emptyList(),
                slotCapabilitySource = SlotCapabilitySource.NONE.name
            )
        })
        val codes = ProgramBuilderValidator.validate(overloaded).map(ProgramValidationIssue::code).toSet()

        assertTrue("TWO_WEEK_NEUROMUSCULAR_WAVE" in codes)
        assertTrue("TWO_WEEK_JOINT_IMPACT_WAVE" in codes)
        assertTrue("TWO_WEEK_MOVEMENT_COVERAGE" in codes)
    }

    @Test
    fun fourWeekValidatorsDetectTransferAndStrengthDistributionDebt() {
        val result = fixture.generate(days = 4, weeks = 4, minutes = 60, ratio = 0.70)
        val narrow = result.copy(items = result.items.map { item ->
            item.copy(
                badmintonTransferLevel = "NONE",
                strengthProgressionGroup = "ONE_GROUP"
            )
        })
        val codes = ProgramBuilderValidator.validate(narrow).map(ProgramValidationIssue::code).toSet()

        assertTrue("FOUR_WEEK_TRANSFER_DISTRIBUTION" in codes)
        assertTrue("FOUR_WEEK_STRENGTH_DISTRIBUTION" in codes)
    }

    @Test
    fun generatedSessionsRespectPrescriptionBasedTimeBudget() {
        val result = fixture.generate(days = 7, weeks = 4, minutes = 15, ratio = 0.80)
        val timeIssues = result.validationDetails.filter { it.code == "SESSION_TIME_BUDGET" }

        assertTrue(timeIssues.isEmpty())
        assertTrue(result.items.groupBy { it.weekNumber to it.dayOfWeek }.values.all { rows ->
            rows.sumOf(ProgramSkeletonItem::estimatedDurationSeconds) + 5 * 60 <= 15 * 60
        })
    }

    private fun fatigueState(ofi: Int, local: Int, joint: Int, neural: Int): DailyFatigueState =
        DailyFatigueState(
            date = LocalDate.of(2026, 6, 20),
            neuromuscularFatigue = neural.toDouble(),
            systemicMuscularFatigue = 80.0,
            localMuscularFatigue = local.toDouble(),
            jointTendonImpactFatigue = joint.toDouble(),
            movementFocusFatigue = 70.0,
            recoveryPressure = 90.0,
            neuromuscularScore = neural,
            systemicMuscularScore = 80,
            localMuscularScore = local,
            jointTendonImpactScore = joint,
            movementFocusScore = 70,
            recoveryPressureScore = 90,
            overallFatigueIndex = ofi,
            readinessLabel = FatigueReadinessLabel.HIGH_FATIGUE,
            cautionReasons = listOf("GLOBAL_HIGH_FATIGUE"),
            confidence = FatigueConfidence.HIGH
        )
}

private class ProgramFixture {
    val cueExerciseId = 8L
    val directSportExerciseId = 21L
    val heavyOrImpactIds = setOf(1L, 2L, 9L, 10L)

    private val rows = listOf(
        row(1, "Back Squat", "SQUAT_VARIANTS", "MAIN_LOWER_STRENGTH", "SQUAT_HEAVY_AXIAL", "ESTIMATED_1RM", "GENERAL", "VERY_HIGH"),
        row(2, "Barbell Hinge", "DEADLIFT_HINGE_VARIANTS", "MAIN_HINGE_STRENGTH", "HEAVY_HINGE", "ESTIMATED_1RM", "GENERAL", "VERY_HIGH"),
        row(3, "Vertical Pull A", "PULL_UP_VARIANTS", "UPPER_PULL_STRENGTH", "VERTICAL_PULL_COMPOUND", "LOAD_REPS", "SUPPORTIVE", "MODERATE"),
        row(4, "Horizontal Row A", "ROW_VARIANTS", "UPPER_PULL_STRENGTH", "HORIZONTAL_PULL_COMPOUND", "LOAD_REPS", "SUPPORTIVE", "MODERATE"),
        row(5, "Horizontal Push A", "BENCH_PRESS_VARIANTS", "HORIZONTAL_PUSH_STRENGTH", "HORIZONTAL_PUSH_COMPOUND", "LOAD_REPS", "GENERAL", "HIGH"),
        row(6, "Scapular Control", "SCAPULAR_CONTROL", "SCAPULAR_PREHAB", "SCAPULAR_CONTROL", "QUALITY_BASED", "SUPPORTIVE", "LOW"),
        row(7, "Core Anti Rotation", "ANTI_ROTATION_CORE", "CORE_ACCESSORY", "CORE_BRACING", "REPS_OR_TIME", "SUPPORTIVE", "LOW"),
        row(8, "Cue Footwork", "FOOTWORK_SPLIT_STEP_REACTION", "BADMINTON_COD_DECEL", "BADMINTON_COURT_FOOTWORK", "REPS_OR_TIME", "DIRECT", "MODERATE", appCue = "RANDOM_BEEP_CUE", impact = "DIRECTION_CHANGE_IMPACT"),
        row(9, "Landing Plyometric", "PLYOMETRIC_LANDING", "PLYOMETRIC_POWER", "PLYOMETRIC_LANDING", "REPS_OR_TIME", "SUPPORTIVE", "HIGH", impact = "JUMP_LANDING"),
        row(10, "Lateral Bound", "LATERAL_BOUND_LANDING", "PLYOMETRIC_POWER", "PLYOMETRIC_LANDING", "REPS_OR_TIME", "DIRECT", "HIGH", impact = "JUMP_LANDING"),
        row(11, "Split Squat", "LUNGE_SPLIT_SQUAT", "UNILATERAL_LOWER_ACCESSORY", "LUNGE_UNILATERAL_LOWER", "LOAD_REPS", "SUPPORTIVE", "MODERATE"),
        row(12, "Calf Accessory", "CALF_RAISE_VARIANTS", "CALF_ACCESSORY", "CALF_RAISE_ANKLE_STIFFNESS", "LOAD_REPS", "SUPPORTIVE", "LOW"),
        row(13, "Shoulder Prehab", "ROTATOR_CUFF_PREHAB", "SHOULDER_PREHAB", "ROTATOR_CUFF_PREHAB", "QUALITY_BASED", "SUPPORTIVE", "LOW"),
        row(14, "Mobility A", "MOBILITY_CONTROL", "RECOVERY_PREHAB", "MOBILITY_CONTROL", "QUALITY_BASED", "NONE", "LOW"),
        row(15, "Curl A", "ELBOW_FLEXION_CURL", "BICEPS_ACCESSORY", "ELBOW_FLEXION_CURL", "LOAD_REPS", "GENERAL", "LOW"),
        row(16, "Curl B", "ELBOW_FLEXION_CURL", "BICEPS_ACCESSORY", "ELBOW_FLEXION_CURL", "LOAD_REPS", "GENERAL", "LOW"),
        row(17, "Triceps A", "ELBOW_EXTENSION_TRICEPS", "TRICEPS_ACCESSORY", "ELBOW_EXTENSION_TRICEPS", "LOAD_REPS", "GENERAL", "LOW"),
        row(18, "Glute Accessory", "GLUTE_ISOLATION_ACCESSORY", "GLUTE_ACCESSORY", "GLUTE_ISOLATION_ACCESSORY", "LOAD_REPS", "GENERAL", "LOW"),
        row(19, "Reaction Drill B", "FOOTWORK_SPLIT_STEP_REACTION", "BADMINTON_COD_DECEL", "BADMINTON_COURT_FOOTWORK", "REPS_OR_TIME", "DIRECT", "MODERATE", impact = "DIRECTION_CHANGE_IMPACT"),
        row(20, "Core Control B", "DEEP_CORE_CONTROL", "CORE_ACCESSORY", "CORE_BRACING", "REPS_OR_TIME", "SUPPORTIVE", "LOW"),
        row(
            21,
            "Badminton Match Session",
            "BADMINTON_SESSION_SPORT_RECORDS",
            "NOT_APPLICABLE",
            "BADMINTON_DIRECT_PLAY",
            "SESSION_DURATION",
            "DIRECT",
            "HIGH",
            activityKind = "SPORT_SESSION"
        )
    )
    private val exercises = rows.map { it.first }
    private val catalog = RuntimeExerciseMetadataCatalog.of(rows.map { it.second })

    fun generate(
        days: Int,
        weeks: Int,
        minutes: Int,
        ratio: Double,
        fatigue: DailyFatigueState? = null
    ): GeneratedProgramSkeleton = ProgramBuilder().build(
        request = ProgramSkeletonRequest(
            name = "Test",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = days,
            sessionMinutes = minutes,
            availableEquipment = setOf("BODYWEIGHT", "BARBELL"),
            excludedExerciseText = "",
            badmintonTransferRatio = ratio,
            sportStrengthRatio = "AUTO",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = weeks
        ),
        exercises = exercises,
        history = emptyList(),
        today = LocalDate.of(2026, 6, 20),
        runtimeMetadataCatalog = catalog,
        fatigueState = fatigue
    )

    private fun row(
        id: Long,
        name: String,
        family: String,
        slot: String,
        redundancy: String,
        metric: String,
        transfer: String,
        stress: String,
        appCue: String = "NONE",
        impact: String = "NONE",
        activityKind: String = "TRAINING_EXERCISE"
    ): Pair<Exercise, RuntimeExerciseMetadata> {
        val stableKey = "fixture_$id"
        val exercise = Exercise(
            id = id,
            name = name,
            category = "TRAINING",
            stableKey = stableKey,
            equipment = if (id <= 5L) "BARBELL" else "BODYWEIGHT",
            activityKind = activityKind,
            planningEligibility = "PROGRAM_SELECTABLE"
        )
        val metadata = ExerciseMetadataAdapter.fromFields(
            mapOf(
                "stableKey" to stableKey,
                "exerciseName" to name,
                "activityKind" to activityKind,
                "planningEligibility" to "PROGRAM_SELECTABLE",
                "movementFamily" to family,
                "movementSubtype" to "GENERIC_${family}",
                "programSlot" to slot,
                "redundancyGroup" to redundancy,
                "progressMetricType" to metric,
                "strengthProgressionGroup" to redundancy,
                "analysisEligibility" to "FATIGUE|STRENGTH_PROGRESS",
                "primaryStressProfile" to "LOCAL_MUSCLE",
                "secondaryStressTags" to if (impact == "NONE") "NONE" else "PLYOMETRIC|DECELERATION",
                "jointImpactStressTags" to impact,
                "stressMagnitudeHint" to stress,
                "badmintonTransferLevel" to transfer,
                "badmintonTransferType" to if (transfer == "DIRECT") "FOOTWORK|REACTION" else "NONE",
                "badmintonPhysicalQualities" to if (transfer in setOf("DIRECT", "SUPPORTIVE")) "DECELERATION|CORE_STABILITY" else "NONE",
                "sourceConfidenceLevel" to "ANATOMY_SUPPORTED",
                "finalSourceStatus" to "SOURCE_ACCEPTED",
                "recoveryDecayProfile" to "MEDIUM",
                "recoveryDurationClass" to "MEDIUM",
                "neuromuscularStressLevel" to stress,
                "systemicMuscularStressLevel" to stress,
                "localMuscularStressLevel" to stress,
                "jointTendonImpactStressLevel" to if (impact == "NONE") "LOW" else "HIGH",
                "movementFocusDemandLevel" to if (transfer == "DIRECT") "HIGH" else "MODERATE",
                "safeForSeedMutation" to "NO",
                "appCueProfile" to appCue
            )
        )
        return exercise to metadata
    }
}

package com.training.trackplanner.data

import com.training.trackplanner.analysis.fatigue.DailyFatigueState
import com.training.trackplanner.analysis.fatigue.FatigueConfidence
import com.training.trackplanner.analysis.fatigue.FatigueReadinessLabel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ProgramBuilderRehabActivationTest {
    private val fixture = RehabProgramFixture()

    @Test
    fun normalPerformanceSessionsCapRehabLikeActivationAtOne() {
        val result = fixture.generate(days = 5, weeks = 8)
        val deloadWeeks = result.weekPlans.filter(ProgramWeekPlan::deloadFlag).map(ProgramWeekPlan::weekIndex).toSet()
        val normalSessions = result.items
            .filterNot { it.weekNumber in deloadWeeks }
            .filterNot { it.trainingSlot in fixture.recoverySlots }
            .groupBy { it.weekNumber to it.dayOfWeek }

        assertTrue(normalSessions.values.all { rows -> rows.count(ProgramSkeletonItem::rehabLikeActivation) <= 1 })
        assertFalse(normalSessions.values.flatten().any {
            it.rehabLikeActivation && (it.orderIndex == 1 || it.selectionRole == "ANCHOR")
        })
        assertFalse(result.validationDetails.any { it.code == "REHAB_ACTIVATION_DOMINANCE" })
    }

    @Test
    fun bandExternalRotationDoesNotAppearEveryNormalWeek() {
        val result = fixture.generate(days = 5, weeks = 8)
        val normalWeeks = result.weekPlans.filterNot(ProgramWeekPlan::deloadFlag).map(ProgramWeekPlan::weekIndex).toSet()
        val exposureWeeks = result.items
            .filter { it.stableKey == fixture.bandExternalRotationKey && it.weekNumber in normalWeeks }
            .map(ProgramSkeletonItem::weekNumber)
            .toSet()

        assertTrue(exposureWeeks.size < normalWeeks.size)
    }

    @Test
    fun scapularStabilityKeepsPerformanceSupportingAlternatives() {
        val result = fixture.generate(days = 4, weeks = 8)
        val scapular = result.items.filter(ProgramSkeletonItem::scapularStabilityExposure)

        assertTrue(scapular.isNotEmpty())
        assertTrue(scapular.any { !it.rehabLikeActivation })
    }

    @Test
    fun redFatigueAndRecoverySlotsAllowMoreActivationWithoutHardDominance() {
        val result = fixture.generate(days = 5, weeks = 4, fatigue = fixture.redFatigue())

        assertFalse(result.validationDetails.any {
            it.code == "REHAB_ACTIVATION_DOMINANCE" && it.severity == ProgramValidationSeverity.HARD
        })
    }

    @Test
    fun strengthAndPerformanceAnchorsAreNeverClassifiedAsRehabActivation() {
        val result = fixture.generate(days = 5, weeks = 4)
        val anchors = result.items.filter { it.selectionRole == "ANCHOR" }

        assertTrue(anchors.isNotEmpty())
        assertTrue(anchors.none(ProgramSkeletonItem::rehabLikeActivation))
    }

    @Test
    fun classifierUsesMetadataRatherThanDisplayName() {
        val renamedWallSlide = fixture.candidate(
            id = 100,
            name = "Renamed neutral display value",
            subtype = "WALL_SLIDE",
            family = "SCAPULAR_CONTROL_RECOVERY_PREHAB_VARIANTS",
            slot = "RECOVERY_PREHAB_SCAPULAR_CONTROL",
            redundancy = "SCAPULAR_UPWARD_ROTATION_CONTROL",
            metric = "QUALITY_BASED",
            progression = "NOT_APPLICABLE",
            stressProfile = "LOW_LOAD_PREHAB_CONTROL_STRESS",
            equipment = "WALL"
        )
        val misleadingName = fixture.candidate(
            id = 101,
            name = "Wall slide",
            subtype = "CHEST_SUPPORTED_ROW",
            family = "ROW_VARIANTS",
            slot = "UPPER_PULL_STRENGTH",
            redundancy = "HORIZONTAL_PULL_COMPOUND",
            metric = "LOAD_REPS",
            progression = "HORIZONTAL_PULL_COMPOUND",
            stressProfile = "UPPER_PULL_STRESS",
            equipment = "MACHINE"
        )

        assertTrue(renamedWallSlide.isRehabLikeActivation)
        assertFalse(misleadingName.isRehabLikeActivation)
    }

    @Test
    fun canonicalMetadataSeparatesActivationFromPerformanceScapularWork() {
        val metadata = ExerciseMetadataAdapter.fromCsv(canonicalFile().readText(Charsets.UTF_8))
            .associateBy(RuntimeExerciseMetadata::exerciseName)
        fun classified(name: String, equipment: String, restSeconds: Int): Boolean {
            val row = checkNotNull(metadata[name])
            val exercise = Exercise(
                id = name.hashCode().toLong(),
                name = "classifier input does not use display name",
                category = "TRAINING",
                stableKey = row.stableKey,
                equipment = equipment,
                defaultRestSeconds = restSeconds,
                activityKind = "TRAINING_EXERCISE",
                planningEligibility = "PROGRAM_SELECTABLE"
            )
            return ProgramCandidate(exercise, row, canonical = true).isRehabLikeActivation
        }

        assertTrue(classified("월 슬라이드", "벽", 30))
        assertTrue(classified("스캡 푸시업", "맨몸", 30))
        assertTrue(classified("밴드 외회전", "밴드", 45))
        assertFalse(classified("스캡 풀업", "맨몸", 45))
        assertFalse(classified("페이스풀", "케이블", 60))
        assertFalse(classified("팔로프 프레스", "케이블", 45))
        assertFalse(classified("사이드 플랭크", "맨몸", 45))
    }

    @Test
    fun validatorFlagsObviousNormalSessionDominance() {
        val result = fixture.generate(days = 4, weeks = 4)
        val firstSessionKey = result.items.first().let { it.weekNumber to it.dayOfWeek }
        val dominated = result.copy(items = result.items.map { item ->
            if (item.weekNumber to item.dayOfWeek == firstSessionKey && item.orderIndex <= 3) {
                item.copy(rehabLikeActivation = true, scapularStabilityExposure = true)
            } else {
                item
            }
        })
        val issues = ProgramBuilderValidator.validate(dominated)

        assertTrue(issues.any { it.code == "REHAB_ACTIVATION_SESSION_CAP" })
        assertTrue(issues.any {
            it.code == "REHAB_ACTIVATION_DOMINANCE" && it.severity == ProgramValidationSeverity.HARD
        })
    }

    private fun canonicalFile(): File = sequenceOf(
        File("src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"),
        File("app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv")
    ).firstOrNull(File::exists) ?: error("Missing canonical metadata test asset")
}

private class RehabProgramFixture {
    val bandExternalRotationKey = "rehab_band_external_rotation"
    val recoverySlots = setOf(
        ProgramTrainingSlot.RECOVERY_PREHAB.name,
        ProgramTrainingSlot.RECOVERY_WEAKPOINT.name,
        ProgramTrainingSlot.MICRO_RECOVERY.name
    )

    private val pairs = listOf(
        candidateRow(1, "Squat anchor", "BACK_SQUAT", "SQUAT_VARIANTS", "MAIN_LOWER_STRENGTH", "SQUAT_HEAVY_AXIAL", "ESTIMATED_1RM", "SQUAT_HEAVY_AXIAL", "HEAVY_COMPOUND_STRESS", "BARBELL", "VERY_HIGH"),
        candidateRow(2, "Hinge anchor", "BARBELL_DEADLIFT", "DEADLIFT_HINGE_VARIANTS", "MAIN_HINGE_STRENGTH", "HEAVY_HINGE", "ESTIMATED_1RM", "HEAVY_HINGE", "HEAVY_COMPOUND_STRESS", "BARBELL", "VERY_HIGH"),
        candidateRow(3, "Loaded row", "CHEST_SUPPORTED_ROW", "ROW_VARIANTS", "UPPER_PULL_STRENGTH", "HORIZONTAL_PULL_COMPOUND", "LOAD_REPS", "HORIZONTAL_PULL_COMPOUND", "UPPER_PULL_STRESS", "MACHINE", "MODERATE"),
        candidateRow(4, "Vertical pull", "PULL_UP", "PULL_UP_CHIN_UP_LAT_PULLDOWN_VARIANTS", "UPPER_PULL_STRENGTH", "VERTICAL_PULL_COMPOUND", "LOAD_REPS", "VERTICAL_PULL_COMPOUND", "VERTICAL_PULL_STRESS", "BODYWEIGHT", "MODERATE"),
        candidateRow(5, "Face pull", "FACE_PULL", "SCAPULAR_RETRACTION_EXTERNAL_ROTATION_CONTROL_VARIANTS", "SCAPULAR_CONTROL_ACCESSORY", "SCAPULAR_RETRACTION_EXTERNAL_ROTATION", "QUALITY_BASED", "NOT_APPLICABLE", "LOW_LOAD_PREHAB_CONTROL_STRESS", "CABLE", "LOW", rest = 60),
        candidateRow(6, "Rear delt raise", "REAR_DELT_FLY", "REAR_DELT_FLY_HORIZONTAL_ABDUCTION_VARIANTS", "REAR_DELT_SCAPULAR_ACCESSORY", "POSTERIOR_DELTOID_HORIZONTAL_ABDUCTION", "LOAD_REPS", "REAR_DELT_SCAPULAR_ACCESSORY", "ISOLATION_ACCESSORY_STRESS", "DUMBBELL", "LOW"),
        candidateRow(7, "Pallof press", "PALLOF_PRESS", "ANTI_ROTATION_ANTI_EXTENSION_CORE_VARIANTS", "CORE_STABILITY", "TRUNK_ANTI_MOVEMENT", "QUALITY_BASED", "TRUNK_ANTI_MOVEMENT", "ISOMETRIC_CORE_BRACING_STRESS", "CABLE", "LOW"),
        candidateRow(8, "Side plank", "SIDE_PLANK", "ANTI_ROTATION_ANTI_EXTENSION_CORE", "CORE_STABILITY_ACCESSORY", "CORE_BRACING_CONTROL", "TIME_OR_REPS", "CORE_BRACING_CONTROL", "ISOMETRIC_CORE_BRACING_STRESS", "BODYWEIGHT", "LOW"),
        candidateRow(9, "Single-leg strength", "DUMBBELL_SPLIT_SQUAT", "LUNGE_SPLIT_SQUAT_VARIANTS", "UNILATERAL_LOWER_ACCESSORY", "LUNGE_UNILATERAL_LOWER", "LOAD_REPS", "LUNGE_UNILATERAL_LOWER", "UNILATERAL_LOWER_STRESS", "DUMBBELL", "MODERATE"),
        candidateRow(10, "Court footwork", "RANDOM_BEEP_FOOTWORK", "FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS", "BADMINTON_FOOTWORK", "BADMINTON_COURT_FOOTWORK", "TIME_OR_REPS", "NOT_APPLICABLE", "COURT_FOOTWORK_STRESS", "BODYWEIGHT", "MODERATE", transfer = "DIRECT"),
        candidateRow(11, "Calf strength", "STANDING_CALF_RAISE", "CALF_RAISE_ANKLE_STIFFNESS_VARIANTS", "CALF_ACCESSORY", "CALF_RAISE_ANKLE_STIFFNESS", "LOAD_REPS", "CALF_RAISE_ANKLE_STIFFNESS", "LOCAL_MUSCLE_STRESS", "MACHINE", "LOW"),
        candidateRow(12, "Activation A", "WALL_SLIDE", "SCAPULAR_CONTROL_RECOVERY_PREHAB_VARIANTS", "RECOVERY_PREHAB_SCAPULAR_CONTROL", "SCAPULAR_UPWARD_ROTATION_CONTROL", "QUALITY_BASED", "NOT_APPLICABLE", "LOW_LOAD_PREHAB_CONTROL_STRESS", "WALL", "LOW"),
        candidateRow(13, "Activation B", "SCAPULAR_PUSH_UP", "SERRATUS_SCAPULAR_PROTRACTION_CONTROL_VARIANTS", "RECOVERY_PREHAB_SCAPULAR_CONTROL", "SCAPULAR_PROTRACTION_CONTROL", "QUALITY_BASED", "NOT_APPLICABLE", "LOW_LOAD_PREHAB_CONTROL_STRESS", "BODYWEIGHT", "LOW"),
        candidateRow(14, "Activation C", "BAND_EXTERNAL_ROTATION", "EXTERNAL_ROTATION_INTERNAL_ROTATION_VARIANTS", "ROTATOR_CUFF_CARE", "ROTATOR_CUFF_PREHAB", "QUALITY_BASED", "NOT_APPLICABLE", "LOW_LOAD_PREHAB_CONTROL_STRESS", "BAND", "LOW", stableKey = bandExternalRotationKey),
        candidateRow(15, "Activation D", "BIRD_DOG", "ANTI_ROTATION_ANTI_EXTENSION_CORE", "CORE_STABILITY_ACCESSORY", "CORE_BRACING_CONTROL", "TIME_OR_REPS", "CORE_BRACING_CONTROL", "ISOMETRIC_CORE_BRACING_STRESS", "BODYWEIGHT", "LOW"),
        candidateRow(16, "Triceps accessory", "TRICEPS_PUSHDOWN", "ELBOW_EXTENSION_TRICEPS_ISOLATION_VARIANTS", "TRICEPS_ACCESSORY", "ELBOW_EXTENSION_TRICEPS", "LOAD_REPS", "ELBOW_EXTENSION_TRICEPS", "ISOLATION_ACCESSORY_STRESS", "CABLE", "LOW")
    )
    private val exercises = pairs.map { it.first }
    private val catalog = RuntimeExerciseMetadataCatalog.of(pairs.map { it.second })

    fun generate(
        days: Int,
        weeks: Int,
        fatigue: DailyFatigueState? = null
    ): GeneratedProgramSkeleton = ProgramBuilder().build(
        request = ProgramSkeletonRequest(
            name = "Rehab cap test",
            goal = ProgramGoal.BADMINTON_SUPPORT,
            weeklyTrainingDays = days,
            sessionMinutes = 60,
            availableEquipment = emptySet(),
            excludedExerciseText = "",
            badmintonTransferRatio = 0.70,
            sportStrengthRatio = "70:30",
            periodizationType = ProgramPeriodizationType.AUTO,
            durationWeeks = weeks
        ),
        exercises = exercises,
        history = emptyList(),
        today = LocalDate.of(2026, 6, 20),
        runtimeMetadataCatalog = catalog,
        fatigueState = fatigue
    )

    fun candidate(
        id: Long,
        name: String,
        subtype: String,
        family: String,
        slot: String,
        redundancy: String,
        metric: String,
        progression: String,
        stressProfile: String,
        equipment: String
    ): ProgramCandidate {
        val row = candidateRow(
            id,
            name,
            subtype,
            family,
            slot,
            redundancy,
            metric,
            progression,
            stressProfile,
            equipment,
            if (stressProfile.contains("LOW_LOAD")) "LOW" else "MODERATE"
        )
        return ProgramCandidate(row.first, row.second, canonical = true)
    }

    fun redFatigue(): DailyFatigueState = DailyFatigueState(
        date = LocalDate.of(2026, 6, 20),
        neuromuscularFatigue = 90.0,
        systemicMuscularFatigue = 90.0,
        localMuscularFatigue = 90.0,
        jointTendonImpactFatigue = 90.0,
        movementFocusFatigue = 90.0,
        recoveryPressure = 90.0,
        neuromuscularScore = 90,
        systemicMuscularScore = 90,
        localMuscularScore = 90,
        jointTendonImpactScore = 90,
        movementFocusScore = 90,
        recoveryPressureScore = 90,
        overallFatigueIndex = 90,
        readinessLabel = FatigueReadinessLabel.HIGH_FATIGUE,
        cautionReasons = listOf("GLOBAL_HIGH_FATIGUE"),
        confidence = FatigueConfidence.HIGH
    )

    private fun candidateRow(
        id: Long,
        name: String,
        subtype: String,
        family: String,
        slot: String,
        redundancy: String,
        metric: String,
        progression: String,
        stressProfile: String,
        equipment: String,
        stress: String,
        transfer: String = "SUPPORTIVE",
        rest: Int = 45,
        stableKey: String = "rehab_fixture_$id"
    ): Pair<Exercise, RuntimeExerciseMetadata> {
        val exercise = Exercise(
            id = id,
            name = name,
            category = "TRAINING",
            stableKey = stableKey,
            equipment = equipment,
            defaultRestSeconds = rest,
            activityKind = "TRAINING_EXERCISE",
            planningEligibility = "PROGRAM_SELECTABLE"
        )
        val metadata = ExerciseMetadataAdapter.fromFields(
            mapOf(
                "stableKey" to stableKey,
                "exerciseName" to name,
                "activityKind" to "TRAINING_EXERCISE",
                "planningEligibility" to "PROGRAM_SELECTABLE",
                "movementFamily" to family,
                "movementSubtype" to subtype,
                "programSlot" to slot,
                "redundancyGroup" to redundancy,
                "progressMetricType" to metric,
                "strengthProgressionGroup" to progression,
                "analysisEligibility" to "FATIGUE|STRENGTH_PROGRESS",
                "primaryStressProfile" to stressProfile,
                "secondaryStressTags" to if (stressProfile.contains("LOW_LOAD")) "LOW_LOAD_CONTROL|LOCAL_STABILIZER_LOAD" else "NONE",
                "stressMagnitudeHint" to stress,
                "badmintonTransferLevel" to transfer,
                "badmintonPhysicalQualities" to "SCAPULAR_CONTROL|CORE_STABILITY",
                "sourceConfidenceLevel" to "ANATOMY_SUPPORTED",
                "finalSourceStatus" to "SOURCE_ACCEPTED",
                "recoveryDecayProfile" to "SHORT",
                "recoveryDurationClass" to "SHORT",
                "neuromuscularStressLevel" to stress,
                "systemicMuscularStressLevel" to stress,
                "localMuscularStressLevel" to stress,
                "jointTendonImpactStressLevel" to "LOW",
                "movementFocusDemandLevel" to "MODERATE",
                "safeForSeedMutation" to "NO"
            )
        )
        return exercise to metadata
    }
}

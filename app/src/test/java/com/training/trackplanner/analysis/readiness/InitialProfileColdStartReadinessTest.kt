package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InitialProfileColdStartReadinessTest {
    private val today = LocalDate.parse("2026-06-15")

    @Test
    fun structuredProfileProducesInitialAdaptationScores() {
        val high = InitialProfileReadinessAdjuster().adaptationFor(highAdaptationProfile())!!
        val low = InitialProfileReadinessAdjuster().adaptationFor(lowAdaptationProfile())!!

        assertTrue(high.resistanceAdaptationScore > low.resistanceAdaptationScore)
        assertTrue(high.activityAdaptationScore > low.activityAdaptationScore)
        assertTrue(high.badmintonAdaptationScore > low.badmintonAdaptationScore)
        assertTrue(low.detrainingModifier < high.detrainingModifier)
        assertTrue(low.recoveryCapacityScore < high.recoveryCapacityScore)
    }

    @Test
    fun sameRecordCanProduceDifferentReadinessFromDifferentProfiles() {
        val exercise = lowerStrengthExercise()
        val record = record(
            exercise = exercise,
            confirmedSets = listOf(set(reps = 8, weightKg = 80.0, confirmed = true, rpe = 8.0))
        )
        val engine = TodayReadinessEngine()

        val high = engine.analyze(
            TodayReadinessEngineInput(
                today = today,
                exercises = listOf(exercise),
                entriesWithSets = listOf(record),
                dailyMetrics = emptyList(),
                initialProfile = highAdaptationProfile()
            )
        )
        val low = engine.analyze(
            TodayReadinessEngineInput(
                today = today,
                exercises = listOf(exercise),
                entriesWithSets = listOf(record),
                dailyMetrics = emptyList(),
                initialProfile = lowAdaptationProfile()
            )
        )

        assertNotEquals(high.status, low.status)
        assertTrue(severity(high.status) < severity(low.status))
    }

    @Test
    fun initialProfileDoesNotOnlyRunForFatiguedStatus() {
        val summary = TodayReadinessEngine().analyze(
            TodayReadinessEngineInput(
                today = today,
                exercises = emptyList(),
                entriesWithSets = emptyList(),
                dailyMetrics = emptyList(),
                initialProfile = lowAdaptationProfile()
            )
        )

        assertEquals(ReadinessStatus.CAUTION, summary.status)
        assertTrue(summary.adaptiveBaselineNotes.any { it.contains("초기 프로필") })
    }

    @Test
    fun badmintonGoalMakesCourtLoadMoreSensitiveThanStrengthGoal() {
        val exercise = courtExercise()
        val dailyLoads = DailyAnalysisLoadAggregator().aggregate(
            entriesWithSets = listOf(record(exercise, confirmedSets = listOf(set(seconds = 600, confirmed = true)))),
            exerciseMap = mapOf(exercise.id to exercise)
        )
        val strengthPressure = pressureFor(
            dailyLoads = dailyLoads,
            profile = highAdaptationProfile().copy(primaryGoal = "STRENGTH_GAIN")
        ).categoryPressures.getValue(FatigueCategoryKey.BADMINTON_COURT).pressure ?: 0.0
        val badmintonPressure = pressureFor(
            dailyLoads = dailyLoads,
            profile = highAdaptationProfile().copy(primaryGoal = "BADMINTON_PERFORMANCE")
        ).categoryPressures.getValue(FatigueCategoryKey.BADMINTON_COURT).pressure ?: 0.0

        assertTrue(badmintonPressure > strengthPressure)
    }

    @Test
    fun painAndAvoidTagsMakeMatchingStimulusMoreConservative() {
        val exercise = upperPushExercise()
        val dailyLoads = DailyAnalysisLoadAggregator().aggregate(
            entriesWithSets = listOf(record(exercise, confirmedSets = listOf(set(reps = 8, weightKg = 60.0, confirmed = true)))),
            exerciseMap = mapOf(exercise.id to exercise)
        )
        val plain = pressureFor(dailyLoads, highAdaptationProfile())
        val restricted = pressureFor(
            dailyLoads,
            highAdaptationProfile().copy(
                painAreaTags = "SHOULDER",
                avoidMovementTags = "BENCH_OR_PUSH"
            )
        )

        val plainShoulder = plain.bodyPartPressures["shoulders"]?.pressure ?: 0.0
        val restrictedShoulder = restricted.bodyPartPressures["shoulders"]?.pressure ?: 0.0
        val plainPush = plain.baselineGroupPressures["UPPER_PUSH"]?.pressure ?: 0.0
        val restrictedPush = restricted.baselineGroupPressures["UPPER_PUSH"]?.pressure ?: 0.0
        assertTrue(restrictedShoulder > plainShoulder)
        assertTrue(restrictedPush > plainPush)
    }

    private fun pressureFor(
        dailyLoads: List<DailyAnalysisLoad>,
        profile: InitialUserProfile
    ): FatiguePressureSnapshot {
        val residual = ResidualFatigueCalculator().calculate(dailyLoads, today)
        val stats = StatisticalBaselineCalculator().calculate(dailyLoads, residual, today)
        val adaptive = AdaptiveBaselineCalculator().calculate(dailyLoads, stats)
        val adjusted = InitialProfileReadinessAdjuster().adjustBaseline(
            residual = residual,
            adaptiveBaseline = adaptive,
            today = today,
            dailyLoads = dailyLoads,
            initialProfile = profile
        )
        return FatiguePressureCalculator().calculate(residual, stats, adjusted)
    }

    private fun highAdaptationProfile(): InitialUserProfile =
        InitialUserProfile(
            strengthTrainingYears = 6.0,
            badmintonTrainingYears = 2.0,
            strengthSessionsPerWeek = 4.0,
            strengthMinutesPerSession = 70,
            strengthAverageRpe = 7.0,
            badmintonSessionsPerWeek = 5.0,
            badmintonMinutesPerSession = 80,
            badmintonAverageRpe = 7.0,
            trainingBreakCategory = "NONE",
            trainingBreakReason = "NONE",
            squatKg = 120.0,
            deadliftKg = 150.0,
            benchPressKg = 90.0,
            pullUpMaxReps = 12,
            usualSleepHours = 7.5,
            sleepQuality = 4,
            currentFatigue = 2,
            currentSoreness = 2,
            currentStress = 2,
            currentCondition = 4,
            painAreaTags = "NONE",
            avoidMovementTags = "NONE",
            primaryGoal = "BADMINTON_PERFORMANCE"
        )

    private fun lowAdaptationProfile(): InitialUserProfile =
        InitialUserProfile(
            strengthTrainingYears = 0.0,
            badmintonTrainingYears = 0.0,
            strengthSessionsPerWeek = 0.0,
            strengthMinutesPerSession = 0,
            strengthAverageRpe = null,
            badmintonSessionsPerWeek = 0.0,
            badmintonMinutesPerSession = 0,
            badmintonAverageRpe = null,
            trainingBreakCategory = "MORE_THAN_EIGHT_WEEKS",
            trainingBreakReason = "FATIGUE",
            usualSleepHours = 5.0,
            sleepQuality = 2,
            currentFatigue = 5,
            currentSoreness = 4,
            currentStress = 5,
            currentCondition = 2,
            painAreaTags = "NONE",
            avoidMovementTags = "NONE",
            primaryGoal = "MIXED"
        )

    private fun lowerStrengthExercise(): Exercise =
        Exercise(
            id = 1,
            name = "Lower strength fixture",
            category = "strength",
            stableKey = "lower_strength_fixture",
            movementPattern = "SQUAT",
            movementCategory = "STRENGTH",
            primaryMuscles = "QUADS|GLUTES",
            secondaryMuscles = "HAMSTRING|ERECTOR",
            equipment = "BARBELL",
            compoundType = "COMPOUND",
            forceType = "SQUAT",
            plane = "SAGITTAL",
            laterality = "BILATERAL",
            axialLoadLevel = "HIGH",
            trainingRole = "MAIN_STRENGTH",
            fatigueCategories = "SYSTEMIC|NEURAL_HEAVY|LOCAL_MUSCLE",
            adaptiveBaselineGroups = "SYSTEMIC|HEAVY_LOWER|SQUAT_PATTERN",
            recoveryDecayProfile = "LONG",
            systemicLoadWeight = 0.8,
            neuralHeavyWeight = 0.75,
            localLoadWeight = 0.7,
            estimated1RmEligible = true,
            volumeLoadEligible = true,
            badmintonTransferStrength = "GENERAL",
            analysisEligibility = "FATIGUE|STRENGTH_PROGRESS|BALANCE",
            metadataConfidence = "HIGH"
        )

    private fun upperPushExercise(): Exercise =
        lowerStrengthExercise().copy(
            id = 2,
            name = "Upper push fixture",
            stableKey = "upper_push_fixture",
            movementPattern = "PUSH_HORIZONTAL",
            primaryMuscles = "CHEST|SHOULDER",
            secondaryMuscles = "TRICEPS",
            forceType = "PUSH",
            adaptiveBaselineGroups = "UPPER_PUSH|SHOULDER_OVERHEAD",
            systemicLoadWeight = 0.6,
            neuralHeavyWeight = 0.5,
            localLoadWeight = 0.7
        )

    private fun courtExercise(): Exercise =
        Exercise(
            id = 3,
            name = "Court fixture",
            category = "sports",
            stableKey = "court_fixture",
            movementPattern = "FOOTWORK",
            movementCategory = "REACTIVE",
            primaryMuscles = "QUADS|CALVES|GLUTES",
            secondaryMuscles = "CORE",
            compoundType = "DRILL",
            forceType = "DECELERATE",
            plane = "MULTI_PLANAR",
            laterality = "ALTERNATING",
            trainingRole = "SPEED_REACTIVE",
            fatigueCategories = "NEURAL_SPEED|DECELERATION|ELASTIC_SSC",
            adaptiveBaselineGroups = "BADMINTON_COURT|DECELERATION|ELASTIC_SSC",
            recoveryDecayProfile = "MEDIUM",
            systemicLoadWeight = 0.25,
            neuralSpeedWeight = 0.85,
            localLoadWeight = 0.5,
            decelerationWeight = 0.8,
            elasticSscWeight = 0.6,
            badmintonTransferStrength = "DIRECT",
            courtMovementTypes = "REACTION_RANDOM|DECELERATION|JUMP_LANDING",
            badmintonTransferRoles = "FOOTWORK|REACTION|DECELERATION|JUMP_LANDING",
            analysisEligibility = "FATIGUE|BADMINTON_TRANSFER|BALANCE",
            metadataConfidence = "HIGH"
        )

    private fun record(
        exercise: Exercise,
        confirmedSets: List<WorkoutSet>
    ): WorkoutEntryWithSets {
        val entry = WorkoutEntry(
            id = exercise.id * 100,
            date = today.toString(),
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            category = exercise.category
        )
        return WorkoutEntryWithSets(
            entry = entry,
            sets = confirmedSets.mapIndexed { index, set ->
                set.copy(id = entry.id * 10 + index, entryId = entry.id, setIndex = index + 1)
            }
        )
    }

    private fun set(
        reps: Int = 0,
        weightKg: Double = 0.0,
        seconds: Int = 0,
        confirmed: Boolean,
        rpe: Double? = null
    ): WorkoutSet =
        WorkoutSet(
            entryId = 0,
            setIndex = 1,
            reps = reps,
            weightKg = weightKg,
            seconds = seconds,
            confirmed = confirmed,
            rpe = rpe
        )

    private fun severity(status: ReadinessStatus): Int =
        when (status) {
            ReadinessStatus.READY -> 0
            ReadinessStatus.CAUTION -> 1
            ReadinessStatus.FATIGUED -> 2
            ReadinessStatus.LIMITED -> 3
        }
}

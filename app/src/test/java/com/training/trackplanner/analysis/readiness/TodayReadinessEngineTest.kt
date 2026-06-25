package com.training.trackplanner.analysis.readiness

import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class TodayReadinessEngineTest {
    private val today: LocalDate = LocalDate.parse("2026-06-15")

    @Test
    fun completedRecordsOnlyFeedTodayFatigue() {
        val exercise = heavyExercise()
        val entry = record(
            exercise = exercise,
            confirmedSets = listOf(set(reps = 5, weightKg = 100.0, confirmed = true)),
            plannedSets = listOf(set(reps = 5, weightKg = 200.0, confirmed = false))
        )

        val dailyLoads = DailyAnalysisLoadAggregator().aggregate(
            entriesWithSets = listOf(entry),
            exerciseMap = mapOf(exercise.id to exercise)
        )

        val systemic = dailyLoads.single().categoryLoads.getValue(FatigueCategoryKey.SYSTEMIC)
        assertEquals(400.0, systemic, 0.001)
        assertEquals(1, dailyLoads.single().completedSetCount)
    }

    @Test
    fun plannedWorkoutIsExcludedFromReadiness() {
        val exercise = heavyExercise()
        val plannedOnly = record(
            exercise = exercise,
            confirmedSets = emptyList(),
            plannedSets = listOf(set(reps = 5, weightKg = 300.0, confirmed = false))
        )

        val summary = TodayReadinessEngine().analyze(
            TodayReadinessEngineInput(
                today = today,
                exercises = listOf(exercise),
                entriesWithSets = listOf(plannedOnly),
                dailyMetrics = emptyList()
            )
        )

        assertEquals(ReadinessStatus.READY, summary.status)
        assertEquals(AnalysisConfidence.LOW, summary.confidence)
        assertNotNull(summary.fatiguePresentation)
        assertEquals(0, summary.fatiguePresentation?.overallScore)
    }

    @Test
    fun readinessOutputExposesFatiguePresentationSnapshot() {
        val exercise = heavyExercise()
        val summary = TodayReadinessEngine().analyze(
            TodayReadinessEngineInput(
                today = today,
                exercises = listOf(exercise),
                entriesWithSets = listOf(
                    record(
                        exercise = exercise,
                        confirmedSets = listOf(set(reps = 8, weightKg = 180.0, confirmed = true, rpe = 9.0))
                    )
                ),
                dailyMetrics = emptyList()
            )
        )

        val presentation = summary.fatiguePresentation
        assertNotNull(presentation)
        presentation!!
        assertTrue(presentation.overallScore in 0..100)
        assertTrue(presentation.neuralScore in 0..100)
        assertTrue(presentation.neuralScore > 0)
        assertTrue(presentation.gate.heavyLowerRestricted)
    }

    @Test
    fun readinessUsesStructuredMetadataNotExerciseName() {
        val exercise = heavyExercise(name = "No classification words")
        val renamed = exercise.copy(name = "Completely renamed")
        val entry = record(
            exercise = exercise,
            confirmedSets = listOf(set(reps = 5, weightKg = 100.0, confirmed = true))
        )
        val original = DailyAnalysisLoadAggregator().aggregate(listOf(entry), mapOf(exercise.id to exercise))
        val withRenamed = DailyAnalysisLoadAggregator().aggregate(
            listOf(entry.copy(entry = entry.entry.copy(exerciseName = renamed.name))),
            mapOf(renamed.id to renamed)
        )

        assertEquals(
            original.single().categoryLoads[FatigueCategoryKey.NEURAL_HEAVY],
            withRenamed.single().categoryLoads[FatigueCategoryKey.NEURAL_HEAVY]
        )
        assertNoExerciseNameParsingInReadinessPackage()
    }

    @Test
    fun categoryLoadsSeparateHeavySpeedDecelerationElasticAndCourt() {
        val heavy = heavyExercise()
        val court = courtExercise()
        val records = listOf(
            record(heavy, confirmedSets = listOf(set(reps = 5, weightKg = 100.0, confirmed = true))),
            record(court, confirmedSets = listOf(set(seconds = 120, confirmed = true)))
        )

        val daily = DailyAnalysisLoadAggregator().aggregate(records, mapOf(heavy.id to heavy, court.id to court)).single()

        assertTrue(daily.categoryLoads.getValue(FatigueCategoryKey.NEURAL_HEAVY) > 0.0)
        assertTrue(daily.categoryLoads.getValue(FatigueCategoryKey.NEURAL_SPEED) > 0.0)
        assertTrue(daily.categoryLoads.getValue(FatigueCategoryKey.DECELERATION) > 0.0)
        assertTrue(daily.categoryLoads.getValue(FatigueCategoryKey.ELASTIC_SSC) > 0.0)
        assertTrue(daily.categoryLoads.getValue(FatigueCategoryKey.BADMINTON_COURT) > 0.0)
    }

    @Test
    fun localBodyPartFatigueUsesPrimaryAndSecondaryMuscles() {
        val exercise = heavyExercise(primaryMuscles = "QUADS", secondaryMuscles = "HAMSTRING")
        val daily = DailyAnalysisLoadAggregator().aggregate(
            listOf(record(exercise, confirmedSets = listOf(set(reps = 10, weightKg = 50.0, confirmed = true)))),
            mapOf(exercise.id to exercise)
        ).single()

        assertTrue(daily.bodyPartLoads.getValue("quads") > daily.bodyPartLoads.getValue("hamstrings"))
    }

    @Test
    fun recoveryDecayProfileChangesResidualLoad() {
        val calculator = ResidualFatigueCalculator()

        assertTrue(calculator.decayFactor("VERY_LONG", 3) > calculator.decayFactor("SHORT", 3))
        assertTrue(calculator.decayFactor("LONG", 1) > calculator.decayFactor("SHORT", 1))
    }

    @Test
    fun statisticalBaselineCalculatesCoreStatisticsAndConfidence() {
        val dailyLoads = (0 until 42).map { offset ->
            dailyLoad(
                date = today.minusDays((41 - offset).toLong()),
                category = FatigueCategoryKey.SYSTEMIC,
                load = (offset + 1).toDouble()
            )
        }
        val residual = ResidualFatigueSnapshot(
            residualByCategory = mapOf(FatigueCategoryKey.SYSTEMIC to 60.0),
            residualByBodyPart = emptyMap(),
            residualByAdaptiveBaselineGroup = emptyMap(),
            highestResidualCategories = listOf(FatigueCategoryKey.SYSTEMIC),
            highestResidualBodyParts = emptyList()
        )

        val snapshot = StatisticalBaselineCalculator().calculate(dailyLoads, residual, today)
        val stat = snapshot.categoryStats.getValue(FatigueCategoryKey.SYSTEMIC)

        assertEquals(AnalysisConfidence.MEDIUM, snapshot.overallConfidence)
        assertTrue(stat.rollingMean > 0.0)
        assertTrue(stat.rollingStd > 0.0)
        assertTrue((stat.percentile ?: 0.0) > 0.0)
        assertTrue(stat.ewmaBaseline > 0.0)
        assertTrue((stat.pressure ?: 0.0) > 0.0)
    }

    @Test
    fun zeroStdBaselineDoesNotCrashAndUsesSafeFallback() {
        val dailyLoads = (0 until 14).map { offset ->
            dailyLoad(
                date = today.minusDays((13 - offset).toLong()),
                category = FatigueCategoryKey.SYSTEMIC,
                load = 10.0
            )
        }
        val residual = ResidualFatigueSnapshot(
            residualByCategory = mapOf(FatigueCategoryKey.SYSTEMIC to 10.0),
            residualByBodyPart = emptyMap(),
            residualByAdaptiveBaselineGroup = emptyMap(),
            highestResidualCategories = listOf(FatigueCategoryKey.SYSTEMIC),
            highestResidualBodyParts = emptyList()
        )

        val stat = StatisticalBaselineCalculator()
            .calculate(dailyLoads, residual, today)
            .categoryStats
            .getValue(FatigueCategoryKey.SYSTEMIC)

        stat.zScore?.let { zScore ->
            assertFalse(zScore.isNaN())
            assertFalse(zScore.isInfinite())
        }
        assertTrue((stat.pressure ?: 0.0) > 0.0)
    }

    @Test
    fun adaptiveBaselineRisesOnlyAfterSuccessfulExposureAndCapsChange() {
        val dailyLoads = listOf(
            dailyLoad(today.minusDays(2), FatigueCategoryKey.SYSTEMIC, 100.0),
            dailyLoad(today.minusDays(1), FatigueCategoryKey.SYSTEMIC, 110.0)
        )
        val residual = ResidualFatigueSnapshot(
            residualByCategory = mapOf(FatigueCategoryKey.SYSTEMIC to 120.0),
            residualByBodyPart = emptyMap(),
            residualByAdaptiveBaselineGroup = emptyMap(),
            highestResidualCategories = listOf(FatigueCategoryKey.SYSTEMIC),
            highestResidualBodyParts = emptyList()
        )
        val stats = StatisticalBaselineCalculator().calculate(dailyLoads, residual, today)
        val base = AdaptiveBaselineCalculator().calculate(dailyLoads, stats)
        val success = AdaptiveBaselineCalculator().calculate(
            dailyLoads = dailyLoads,
            stats = stats,
            outcomeSignals = listOf(
                AdaptiveOutcomeSignal(today.minusDays(2), FatigueCategoryKey.SYSTEMIC),
                AdaptiveOutcomeSignal(today.minusDays(1), FatigueCategoryKey.SYSTEMIC)
            )
        )
        val failed = AdaptiveBaselineCalculator().calculate(
            dailyLoads = dailyLoads,
            stats = stats,
            outcomeSignals = listOf(
                AdaptiveOutcomeSignal(
                    date = today.minusDays(2),
                    category = FatigueCategoryKey.SYSTEMIC,
                    recoveryStable = false,
                    fatigueIncrease = true
                )
            )
        )

        val baseTolerance = base.toleranceByCategory.getValue(FatigueCategoryKey.SYSTEMIC)
        val successTolerance = success.toleranceByCategory.getValue(FatigueCategoryKey.SYSTEMIC)
        val failedTolerance = failed.toleranceByCategory.getValue(FatigueCategoryKey.SYSTEMIC)
        assertTrue(successTolerance > baseTolerance)
        assertTrue(successTolerance <= baseTolerance * 1.05 + 0.001)
        assertTrue(failedTolerance <= baseTolerance)
    }

    @Test
    fun sameLoadProducesLowerPressureWithHigherAdaptiveTolerance() {
        val residual = ResidualFatigueSnapshot(
            residualByCategory = mapOf(FatigueCategoryKey.BADMINTON_COURT to 100.0),
            residualByBodyPart = emptyMap(),
            residualByAdaptiveBaselineGroup = emptyMap(),
            highestResidualCategories = listOf(FatigueCategoryKey.BADMINTON_COURT),
            highestResidualBodyParts = emptyList()
        )
        val stat = BaselineStat(
            rollingMean = 50.0,
            rollingStd = 10.0,
            zScore = 1.0,
            percentile = 80.0,
            ewmaBaseline = 50.0,
            pressure = 2.0,
            trend = BaselineTrend.STABLE,
            confidence = AnalysisConfidence.MEDIUM,
            sampleDays = 56
        )
        val stats = StatisticalBaselineSnapshot(
            categoryStats = mapOf(FatigueCategoryKey.BADMINTON_COURT to stat),
            baselineGroupStats = emptyMap(),
            bodyPartStats = emptyMap(),
            overallConfidence = AnalysisConfidence.MEDIUM
        )
        val highTolerance = AdaptiveBaselineSnapshot(
            toleranceByCategory = mapOf(FatigueCategoryKey.BADMINTON_COURT to 200.0),
            toleranceByBaselineGroup = emptyMap(),
            toleranceByBodyPart = emptyMap(),
            confidenceByCategory = mapOf(FatigueCategoryKey.BADMINTON_COURT to AnalysisConfidence.MEDIUM),
            trendByCategory = mapOf(FatigueCategoryKey.BADMINTON_COURT to BaselineTrend.RISING),
            successfulExposureCountByCategory = mapOf(FatigueCategoryKey.BADMINTON_COURT to 10),
            failedExposureCountByCategory = mapOf(FatigueCategoryKey.BADMINTON_COURT to 0),
            dataSufficiency = AnalysisConfidence.MEDIUM,
            baselineAdjustmentNotes = emptyList()
        )
        val lowTolerance = highTolerance.copy(
            toleranceByCategory = mapOf(FatigueCategoryKey.BADMINTON_COURT to 50.0),
            trendByCategory = mapOf(FatigueCategoryKey.BADMINTON_COURT to BaselineTrend.FALLING)
        )

        val highPressure = FatiguePressureCalculator().calculate(residual, stats, highTolerance)
            .categoryPressures
            .getValue(FatigueCategoryKey.BADMINTON_COURT)
            .pressure
        val lowPressure = FatiguePressureCalculator().calculate(residual, stats, lowTolerance)
            .categoryPressures
            .getValue(FatigueCategoryKey.BADMINTON_COURT)
            .pressure

        assertTrue((highPressure ?: 0.0) < (lowPressure ?: 0.0))
    }

    @Test
    fun syntheticScenariosReturnExpectedReadinessStatuses() {
        val engine = TodayReadinessEngine()
        val heavy = heavyExercise()
        val court = courtExercise()

        val ready = engine.analyze(
            TodayReadinessEngineInput(today, listOf(heavy), emptyList(), emptyList())
        )
        val fatigued = engine.analyze(
            TodayReadinessEngineInput(
                today = today,
                exercises = listOf(heavy),
                entriesWithSets = listOf(
                    record(heavy, confirmedSets = listOf(set(reps = 8, weightKg = 180.0, confirmed = true, rpe = 9.0)))
                ),
                dailyMetrics = listOf(DailyMetric(date = today.toString(), sleepHours = 4.5))
            )
        )
        val cautionOrFatigued = engine.analyze(
            TodayReadinessEngineInput(
                today = today,
                exercises = listOf(court),
                entriesWithSets = listOf(record(court, confirmedSets = listOf(set(seconds = 900, confirmed = true)))),
                dailyMetrics = emptyList()
            )
        )
        val limited = engine.analyze(
            TodayReadinessEngineInput(
                today = today,
                exercises = listOf(heavy),
                entriesWithSets = emptyList(),
                dailyMetrics = emptyList(),
                painInputs = listOf(PainInput(today, score = 7, bodyPart = "어깨"))
            )
        )

        assertEquals(ReadinessStatus.READY, ready.status)
        assertEquals(ReadinessStatus.FATIGUED, fatigued.status)
        assertTrue(cautionOrFatigued.status in setOf(ReadinessStatus.CAUTION, ReadinessStatus.FATIGUED))
        assertEquals(ReadinessStatus.LIMITED, limited.status)
    }

    @Test
    fun sentenceOutputDoesNotContainProhibitedExpressions() {
        val summary = TodayReadinessEngine().analyze(
            TodayReadinessEngineInput(
                today = today,
                exercises = listOf(heavyExercise()),
                entriesWithSets = emptyList(),
                dailyMetrics = emptyList(),
                painInputs = listOf(PainInput(today, score = 7, bodyPart = "어깨"))
            )
        )
        val text = buildString {
            append(summary.headline)
            append(summary.shortReason)
            append(summary.primaryReasons.joinToString())
            append(summary.recommendedModes.joinToString())
            append(summary.restrictedModes.joinToString())
            append(summary.detailSections.joinToString { section -> section.summary })
        }

        listOf("부상 위험", "다칠 수 있습니다", "과훈련", "위험합니다", "회복이 안 됐습니다", "진단").forEach { banned ->
            assertFalse(text.contains(banned))
        }
    }

    @Test
    fun detailSectionsIncludeRequiredReadinessBlocks() {
        val summary = TodayReadinessEngine().analyze(
            TodayReadinessEngineInput(
                today = today,
                exercises = listOf(heavyExercise()),
                entriesWithSets = listOf(
                    record(
                        heavyExercise(),
                        confirmedSets = listOf(set(reps = 8, weightKg = 120.0, confirmed = true))
                    )
                ),
                dailyMetrics = emptyList()
            )
        )

        val types = summary.detailSections.map { section -> section.type }.toSet()
        assertTrue(types.contains(FatigueDetailType.SYSTEMIC))
        assertTrue(types.contains(FatigueDetailType.NEURAL_HEAVY))
        assertTrue(types.contains(FatigueDetailType.NEURAL_SPEED))
        assertTrue(types.contains(FatigueDetailType.LOCAL_BODY_PART))
        assertTrue(types.contains(FatigueDetailType.BADMINTON_COURT))
        assertTrue(types.contains(FatigueDetailType.RECOVERY))
        assertTrue(types.contains(FatigueDetailType.PERFORMANCE))
        assertTrue(types.contains(FatigueDetailType.PAIN))
        assertTrue(types.contains(FatigueDetailType.ADAPTIVE_BASELINE))
    }

    private fun heavyExercise(
        id: Long = 1,
        name: String = "Heavy fixture",
        primaryMuscles: String = "QUADS|GLUTES",
        secondaryMuscles: String = "HAMSTRING|ERECTOR"
    ): Exercise =
        Exercise(
            id = id,
            name = name,
            category = "근력운동",
            stableKey = "heavy_fixture_$id",
            movementPattern = "SQUAT",
            movementCategory = "STRENGTH",
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles,
            equipment = "BARBELL",
            compoundType = "COMPOUND",
            forceType = "SQUAT",
            plane = "SAGITTAL",
            laterality = "BILATERAL",
            axialLoadLevel = "HIGH",
            trainingRole = "MAIN_STRENGTH",
            badmintonTransferRoles = "NONE",
            fatigueCategories = "SYSTEMIC|NEURAL_HEAVY|LOCAL_MUSCLE",
            adaptiveBaselineGroups = "SYSTEMIC|HEAVY_LOWER|SQUAT_PATTERN",
            recoveryDecayProfile = "LONG",
            systemicLoadWeight = 0.8,
            neuralHeavyWeight = 0.75,
            neuralSpeedWeight = 0.0,
            localLoadWeight = 0.7,
            decelerationWeight = 0.0,
            elasticSscWeight = 0.0,
            rotationPowerWeight = 0.0,
            antiRotationWeight = 0.0,
            overheadSwingWeight = 0.0,
            gripLoadWeight = 0.0,
            progressMetricType = "ESTIMATED_1RM",
            strengthProgressionGroup = "SQUAT",
            hypertrophyVolumeGroup = "QUADS",
            mainLiftGroup = "SQUAT",
            accessoryContributionGroup = "NONE",
            estimated1RmEligible = true,
            volumeLoadEligible = true,
            badmintonTransferStrength = "GENERAL",
            courtMovementTypes = "NONE",
            badmintonSkillTargets = "NONE",
            stabilityDemandLevel = "MODERATE",
            mobilityDemandLevel = "MODERATE",
            balanceContributionTags = "LOWER_PUSH|SQUAT_PATTERN",
            analysisEligibility = "FATIGUE|STRENGTH_PROGRESS|HYPERTROPHY_VOLUME|BALANCE",
            metadataConfidence = "HIGH"
        )

    private fun courtExercise(id: Long = 2): Exercise =
        Exercise(
            id = id,
            name = "Court fixture",
            category = "스포츠",
            stableKey = "court_fixture_$id",
            movementPattern = "FOOTWORK",
            movementCategory = "REACTIVE",
            primaryMuscles = "QUADS|CALVES|GLUTES",
            secondaryMuscles = "CORE",
            equipment = "NONE",
            compoundType = "DRILL",
            forceType = "DECELERATE",
            plane = "MULTI_PLANAR",
            laterality = "ALTERNATING",
            axialLoadLevel = "LOW",
            trainingRole = "SPEED_REACTIVE",
            badmintonTransferRoles = "FOOTWORK|REACTION|DECELERATION|JUMP_LANDING",
            fatigueCategories = "NEURAL_SPEED|DECELERATION|ELASTIC_SSC",
            adaptiveBaselineGroups = "BADMINTON_COURT|DECELERATION|ELASTIC_SSC",
            recoveryDecayProfile = "MEDIUM",
            systemicLoadWeight = 0.25,
            neuralHeavyWeight = 0.0,
            neuralSpeedWeight = 0.85,
            localLoadWeight = 0.5,
            decelerationWeight = 0.8,
            elasticSscWeight = 0.6,
            rotationPowerWeight = 0.0,
            antiRotationWeight = 0.0,
            overheadSwingWeight = 0.0,
            gripLoadWeight = 0.0,
            progressMetricType = "QUALITY_BASED",
            strengthProgressionGroup = "BADMINTON_TEST",
            hypertrophyVolumeGroup = "NONE",
            mainLiftGroup = "NONE",
            accessoryContributionGroup = "BADMINTON_SUPPORT",
            estimated1RmEligible = false,
            volumeLoadEligible = false,
            badmintonTransferStrength = "DIRECT",
            courtMovementTypes = "REACTION_RANDOM|DECELERATION|JUMP_LANDING",
            badmintonSkillTargets = "FOOTWORK_SPEED|DECELERATION_CONTROL|JUMP_LANDING_CONTROL",
            stabilityDemandLevel = "HIGH",
            mobilityDemandLevel = "MODERATE",
            balanceContributionTags = "UNILATERAL_LOWER|KNEE_CONTROL|ANKLE_STIFFNESS",
            analysisEligibility = "FATIGUE|BADMINTON_TRANSFER|BALANCE",
            metadataConfidence = "HIGH"
        )

    private fun record(
        exercise: Exercise,
        date: LocalDate = today,
        confirmedSets: List<WorkoutSet>,
        plannedSets: List<WorkoutSet> = emptyList()
    ): WorkoutEntryWithSets {
        val entry = WorkoutEntry(
            id = exercise.id * 100 + date.dayOfMonth,
            date = date.toString(),
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            category = exercise.category
        )
        val sets = (confirmedSets + plannedSets).mapIndexed { index, set ->
            set.copy(
                id = entry.id * 10 + index,
                entryId = entry.id,
                setIndex = index + 1
            )
        }
        return WorkoutEntryWithSets(entry = entry, sets = sets)
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

    private fun dailyLoad(
        date: LocalDate,
        category: FatigueCategoryKey,
        load: Double
    ): DailyAnalysisLoad =
        DailyAnalysisLoad(
            date = date,
            categoryLoads = mapOf(category to load),
            bodyPartLoads = emptyMap(),
            baselineGroupLoads = emptyMap(),
            completedEntryCount = 1,
            completedSetCount = 1,
            contributions = emptyList()
        )

    private fun assertNoExerciseNameParsingInReadinessPackage() {
        val roots = listOf(
            File("src/main/java/com/training/trackplanner/analysis/readiness"),
            File("app/src/main/java/com/training/trackplanner/analysis/readiness")
        )
        val root = roots.first { candidate -> candidate.exists() }
        val prohibited = Regex("""(exerciseName|entry\.exerciseName|name)\s*\.\s*contains""")
        val matches = root.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file -> file.readLines().mapIndexed { index, line -> "${file.name}:${index + 1}:$line" } }
            .filter { line -> prohibited.containsMatchIn(line) }
            .toList()
        assertEquals(emptyList<String>(), matches)
    }
}

package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.features.AnalysisFeatureExtractor
import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.analysis.readiness.BaselineTrend
import com.training.trackplanner.analysis.readiness.FatigueCategoryKey
import com.training.trackplanner.analysis.readiness.FatigueLevel
import com.training.trackplanner.analysis.readiness.FatiguePressure
import com.training.trackplanner.analysis.readiness.FatiguePressureSnapshot
import com.training.trackplanner.analysis.readiness.PainGateSnapshot
import com.training.trackplanner.analysis.readiness.PerformanceSignalSnapshot
import com.training.trackplanner.analysis.readiness.RecoverySignalSnapshot
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataAssetLoader
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.RuntimeExerciseMetadataDefaults
import com.training.trackplanner.data.RuntimeExerciseMetadataResolver
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PerformanceTrendEngineTest {
    private val today = LocalDate.parse("2026-06-15")

    @Test
    fun dashboardUsesThreeSingleLineChartsWithoutEmphasizedScores() {
        val strength = strengthExercise()
        val badminton = badmintonExercise()
        val entries = (0 until 8).flatMap { index ->
            val date = today.minusWeeks((7 - index).toLong())
            listOf(
                record(strength, date, listOf(set(reps = 5, weightKg = 80.0 + index, confirmed = true))),
                record(badminton, date, listOf(set(seconds = 600 + index * 30, confirmed = true)))
            )
        }

        val summary = PerformanceTrendEngine().analyze(
            today = today,
            exercises = listOf(strength, badminton),
            entriesWithSets = entries,
            dailyMetrics = emptyList()
        )

        assertEquals(3, summary.dashboardChartSpecs.size)
        assertTrue(summary.dashboardChartSpecs.all { spec -> spec.type == ChartType.LINE })
        assertTrue(summary.dashboardChartSpecs.all { spec -> spec.visibleLineCount == 1 })
        assertTrue(summary.dashboardChartSpecs.all { spec -> !spec.emphasizeValue })
        assertFalse(summary.trendSentence.length > 90)
    }

    @Test
    fun strengthPerformanceFormulaUsesCompletedSetsOnlyAndStandardizedComponents() {
        val strength = strengthExercise()
        val completedEntries = (0 until 8).map { index ->
            record(
                strength,
                today.minusWeeks((7 - index).toLong()),
                listOf(set(reps = 5, weightKg = 80.0 + index * 2, confirmed = true)),
                plannedSets = listOf(set(reps = 5, weightKg = 300.0, confirmed = false))
            )
        }
        val weeks = WeeklyAnalysisAggregator().aggregate(today, completedEntries, emptyList())
        val result = StrengthPerformanceIndexCalculator().calculate(weeks, mapOf(strength.id to strength), emptyList())
        val latest = result.last()

        val expected = TrendMath.clamp(
            0.50 * latest.intensityIndex + 0.40 * latest.volumeIndex + 0.10 * latest.efficiencyIndex,
            50.0,
            160.0
        )
        assertEquals(expected, latest.performanceIndex, 0.001)
        assertTrue(latest.rawVolume < 300.0 * 5.0)
        assertTrue(latest.intensityIndex in 50.0..160.0)
        assertTrue(latest.volumeIndex in 50.0..160.0)
    }

    @Test
    fun canonicalSquatAndDeadliftFeedStrengthWeekRawMetricsAndMovePerformanceIndex() {
        val catalog = canonicalRuntimeCatalog()
        val squat = canonicalExercise(101, catalog, "barbell_back_squat")
        val deadlift = canonicalExercise(102, catalog, "barbell_deadlift")
        val entries = (0 until 8).flatMap { index ->
            val date = today.minusWeeks((7 - index).toLong())
            listOf(
                record(
                    squat,
                    date,
                    listOf(set(reps = 5, weightKg = 90.0 + index * 5.0, confirmed = true, rpe = 8.0))
                ),
                record(
                    deadlift,
                    date,
                    listOf(set(reps = 3, weightKg = 120.0 + index * 7.5, confirmed = true, rpe = 8.5))
                )
            )
        }
        val weeks = WeeklyAnalysisAggregator().aggregate(today, entries, emptyList())

        val result = StrengthPerformanceIndexCalculator(catalog).calculate(
            weeks = weeks,
            exerciseMap = listOf(squat, deadlift).associateBy(Exercise::id),
            allDailyMetrics = emptyList()
        )
        val latest = result.last()

        assertTrue("rawVolume should include canonical squat/deadlift records", latest.rawVolume > 0.0)
        assertTrue("effectiveSets should include canonical squat/deadlift records", latest.effectiveSets > 0)
        assertTrue("raw intensity should produce a squat exercise score", squat.id in latest.exerciseScores)
        assertTrue("raw intensity should produce a deadlift exercise score", deadlift.id in latest.exerciseScores)
        assertFalse(componentReport(latest), latest.allStrengthComponentsAreFallback100())
    }

    @Test
    fun singleWeekStrengthPerformanceFallbackShowsRawDataWasPresentButBaselinesWereMissing() {
        val catalog = canonicalRuntimeCatalog()
        val squat = canonicalExercise(201, catalog, "barbell_back_squat")
        val entries = listOf(
            record(
                squat,
                today,
                listOf(set(reps = 5, weightKg = 160.0, confirmed = true, rpe = 8.0))
            )
        )
        val weeks = WeeklyAnalysisAggregator().aggregate(today, entries, emptyList())

        val latest = StrengthPerformanceIndexCalculator(catalog)
            .calculate(weeks, mapOf(squat.id to squat), emptyList())
            .last()

        assertTrue("rawVolume proves the set reached rawVolumeByWeek", latest.rawVolume > 0.0)
        assertTrue("effectiveSets proves the set reached effectiveSetsByWeek", latest.effectiveSets > 0)
        assertTrue("exerciseScores proves the set reached rawIntensityByWeek", squat.id in latest.exerciseScores)
        assertEquals(componentReport(latest), 100.0, latest.intensityIndex, 0.001)
        assertEquals(componentReport(latest), 100.0, latest.volumeIndex, 0.001)
        assertEquals(componentReport(latest), 100.0, latest.efficiencyIndex, 0.001)
        assertEquals(componentReport(latest), 100.0, latest.performanceIndex, 0.001)
    }

    @Test
    fun lostCanonicalStableKeyStillFeedsChartFacingStrengthSeriesThroughUniqueNameMetadata() {
        val analysisToday = LocalDate.parse("2026-06-26")
        val canonicalCatalog = canonicalRuntimeCatalog()
        val squat = canonicalExercise(301, canonicalCatalog, "barbell_back_squat")
            .copy(stableKey = "lost_barbell_back_squat")
        val deadlift = canonicalExercise(302, canonicalCatalog, "barbell_deadlift")
            .copy(stableKey = "lost_barbell_deadlift")
        val exercises = listOf(squat, deadlift)
        val runtimeCatalog = RuntimeExerciseMetadataResolver(
            canonicalCatalog = canonicalCatalog,
            persistedRows = emptyList()
        ).catalog(exercises)
        val entries = (0 until 8).flatMap { index ->
            val date = analysisToday.minusWeeks((7 - index).toLong())
            listOf(
                record(
                    squat,
                    date,
                    listOf(set(reps = 5, weightKg = 85.0 + index * 6.0, confirmed = true, rpe = 8.0))
                ),
                record(
                    deadlift,
                    date,
                    listOf(set(reps = 3, weightKg = 125.0 + index * 10.0, confirmed = true, rpe = 8.5))
                )
            )
        }
        val groupedWeeks = WeeklyAnalysisAggregator().aggregate(analysisToday, entries, emptyList())
        val squatFeatures = AnalysisFeatureExtractor.fromExercise(squat, runtimeCatalog.resolve(squat))
        val deadliftFeatures = AnalysisFeatureExtractor.fromExercise(deadlift, runtimeCatalog.resolve(deadlift))

        val summary = PerformanceTrendEngine(runtimeCatalog).analyze(
            today = analysisToday,
            exercises = exercises,
            entriesWithSets = entries,
            dailyMetrics = emptyList()
        )
        val chartPoints = summary.strengthPerformanceSeries.dataPoints.mapNotNull { point -> point.value }

        assertEquals("all confirmed records should enter weekly aggregation", entries.size, groupedWeeks.sumOf { week -> week.entries.size })
        assertTrue(squatFeatures.estimated1RmEligible)
        assertTrue(deadliftFeatures.estimated1RmEligible)
        assertEquals("ESTIMATED_1RM", squatFeatures.progressMetricType)
        assertEquals("ESTIMATED_1RM", deadliftFeatures.progressMetricType)
        assertTrue("STRENGTH_PROGRESS" in squatFeatures.analysisEligibility)
        assertTrue("STRENGTH_PROGRESS" in deadliftFeatures.analysisEligibility)
        assertTrue(
            "confirmed squat/deadlift records should reach raw strength inputs",
            summary.strengthWeeks.any { week -> week.rawVolume > 0.0 || week.effectiveSets > 0 }
        )
        assertTrue(
            "rawVolume should include lost-key canonical squat/deadlift records",
            summary.strengthWeeks.any { week -> week.rawVolume > 0.0 }
        )
        assertTrue(
            "effectiveSets should include lost-key canonical squat/deadlift records",
            summary.strengthWeeks.any { week -> week.effectiveSets > 0 }
        )
        assertTrue(
            "exerciseScores should include squat/deadlift intensity inputs",
            summary.strengthWeeks.any { week -> squat.id in week.exerciseScores || deadlift.id in week.exerciseScores }
        )
        assertFalse(
            "strengthPerformanceSeries should move when multi-week squat/deadlift loads change: $chartPoints",
            chartPoints.allApproximatelyEqual()
        )
        assertEquals(
            summary.strengthPerformanceSeries.dataPoints,
            summary.dashboardChartSpecs.first().lineSeries.single().points
        )
    }

    @Test
    fun stalePersistedMetadataForLostCanonicalStableKeyStillMovesStrengthSeries() {
        val analysisToday = LocalDate.parse("2026-06-26")
        val canonicalCatalog = canonicalRuntimeCatalog()
        val squat = canonicalExercise(401, canonicalCatalog, "barbell_back_squat")
            .copy(stableKey = "lost_barbell_back_squat")
        val deadlift = canonicalExercise(402, canonicalCatalog, "barbell_deadlift")
            .copy(stableKey = "lost_barbell_deadlift")
        val exercises = listOf(squat, deadlift)
        val runtimeCatalog = RuntimeExerciseMetadataResolver(
            canonicalCatalog = canonicalCatalog,
            persistedRows = exercises.map(RuntimeExerciseMetadataDefaults::forExercise)
        ).catalog(exercises)
        val entries = (0 until 8).flatMap { index ->
            val date = analysisToday.minusWeeks((7 - index).toLong())
            listOf(
                record(
                    squat,
                    date,
                    listOf(set(reps = 5, weightKg = 85.0 + index * 6.0, confirmed = true, rpe = 8.0))
                ),
                record(
                    deadlift,
                    date,
                    listOf(set(reps = 3, weightKg = 125.0 + index * 10.0, confirmed = true, rpe = 8.5))
                )
            )
        }
        val squatFeatures = AnalysisFeatureExtractor.fromExercise(squat, runtimeCatalog.resolve(squat))
        val deadliftFeatures = AnalysisFeatureExtractor.fromExercise(deadlift, runtimeCatalog.resolve(deadlift))

        val summary = PerformanceTrendEngine(runtimeCatalog).analyze(
            today = analysisToday,
            exercises = exercises,
            entriesWithSets = entries,
            dailyMetrics = emptyList()
        )
        val chartPoints = summary.strengthPerformanceSeries.dataPoints.mapNotNull { point -> point.value }

        assertTrue(squatFeatures.estimated1RmEligible)
        assertTrue(deadliftFeatures.estimated1RmEligible)
        assertEquals("ESTIMATED_1RM", squatFeatures.progressMetricType)
        assertEquals("ESTIMATED_1RM", deadliftFeatures.progressMetricType)
        assertTrue("STRENGTH_PROGRESS" in squatFeatures.analysisEligibility)
        assertTrue("STRENGTH_PROGRESS" in deadliftFeatures.analysisEligibility)
        assertTrue("rawVolume should survive stale persisted metadata", summary.strengthWeeks.any { week -> week.rawVolume > 0.0 })
        assertTrue("effectiveSets should survive stale persisted metadata", summary.strengthWeeks.any { week -> week.effectiveSets > 0 })
        assertTrue("exerciseScores should survive stale persisted metadata", summary.strengthWeeks.any { week -> week.exerciseScores.isNotEmpty() })
        assertFalse(
            "strengthPerformanceSeries should move with stale persisted metadata present: $chartPoints",
            chartPoints.allApproximatelyEqual()
        )
    }

    @Test
    fun badmintonTrainingUsesMetadataAndDoesNotClaimSkillGain() {
        val renamed = badmintonExercise(name = "Renamed fixture")
        val entries = (0 until 8).map { index ->
            record(
                renamed,
                today.minusWeeks((7 - index).toLong()),
                listOf(set(seconds = 600 + index * 60, confirmed = true))
            )
        }

        val summary = PerformanceTrendEngine().analyze(
            today = today,
            exercises = listOf(renamed),
            entriesWithSets = entries,
            dailyMetrics = emptyList()
        )

        assertTrue(summary.badmintonTrainingSeries.dataPoints.any { point -> (point.value ?: 0.0) > 0.0 })
        val text = summary.trendSentence + summary.detailSections.joinToString { section -> section.shortInterpretation }
        assertFalse(text.contains("실력 향상"))
        assertNoExerciseNameParsingInTrendPackage()
    }

    @Test
    fun badmintonRankingChartUsesResolvedExerciseNameInsteadOfFallbackIdLabel() {
        val exercise = badmintonExercise(name = "운동" + "113")
        val entries = (0 until 2).map { index ->
            record(
                exercise,
                today.minusWeeks(index.toLong()),
                listOf(set(seconds = 600, confirmed = true)),
                entryName = "랜덤 풋워크"
            )
        }

        val summary = PerformanceTrendEngine().analyze(
            today = today,
            exercises = listOf(exercise),
            entriesWithSets = entries,
            dailyMetrics = emptyList()
        )
        val ranking = PerformanceChartSpecBuilder().badmintonDetail(
            mode = DetailChartMode.RANKING,
            selectedMetrics = emptyList(),
            badmintonWeeks = summary.badmintonWeeks,
            exerciseDisplayNamesById = summary.exerciseDisplayNamesById
        )

        assertEquals("랜덤 풋워크", ranking.bars.single().label)
        assertFalse(ranking.bars.single().label.matches(Regex("""운동\s*\d+""")))
    }

    @Test
    fun fatigueCompositeUsesPressurePercentileZScoreAndRecoveryPenalty() {
        val pressure = FatiguePressureSnapshot(
            categoryPressures = mapOf(
                FatigueCategoryKey.SYSTEMIC to pressure("SYSTEMIC", pressure = 1.2, percentile = 80.0, zScore = 1.0),
                FatigueCategoryKey.NEURAL_HEAVY to pressure("NEURAL_HEAVY", pressure = 1.4, percentile = 90.0, zScore = 1.6)
            ),
            baselineGroupPressures = emptyMap(),
            bodyPartPressures = mapOf(
                "quads" to pressure("quads", pressure = 1.5, percentile = 92.0, zScore = 1.8)
            )
        )

        val result = FatigueCompositeIndexCalculator().calculate(
            weekStart = today,
            pressure = pressure,
            recovery = recovery(FatigueLevel.HIGH),
            performance = performance(hasDrop = true),
            pain = pain(false)
        )

        val average = TrendMath.mean(
            listOf(
                result.systemicGroupScore,
                result.strengthGroupScore,
                result.badmintonGroupScore,
                result.localBodyPartGroupScore
            )
        )
        val max = listOf(
            result.systemicGroupScore,
            result.strengthGroupScore,
            result.badmintonGroupScore,
            result.localBodyPartGroupScore
        ).maxOrNull() ?: 100.0
        val expected = TrendMath.clamp(
            0.60 * average + 0.25 * max + 0.15 * result.recoveryPerformancePenaltyScore,
            50.0,
            170.0
        )
        assertEquals(expected, result.compositeIndex, 0.001)
        assertTrue(result.localBodyPartGroupScore > 100.0)
    }

    @Test
    fun chartSelectorRestrictsMixedChartTypesAndResetsMultiSelection() {
        assertTrue(DetailChartSelector.canShowTogether(ChartType.LINE, ChartType.LINE))
        assertFalse(DetailChartSelector.canShowTogether(ChartType.LINE, ChartType.PIE))
        assertFalse(DetailChartSelector.canShowTogether(ChartType.LINE, ChartType.BAR))

        val sanitized = DetailChartSelector.sanitizeSelection(
            mode = DetailChartMode.COMPOSITION,
            selectedMetrics = listOf(TrendMetricId.STRENGTH_INTENSITY, TrendMetricId.STRENGTH_VOLUME),
            defaults = listOf(TrendMetricId.STRENGTH_INTENSITY)
        )
        assertEquals(1, sanitized.size)
    }

    @Test
    fun scatterRequiresSufficientPointsAndAvoidsCausalLanguage() {
        val shortSeries = mapOf(
            TrendMetricId.BADMINTON_TRAINING to (0 until 5).map {
                TrendDataPoint(today.minusWeeks(it.toLong()), 100.0 + it)
            },
            TrendMetricId.FATIGUE_COMPOSITE to (0 until 5).map {
                TrendDataPoint(today.minusWeeks(it.toLong()), 100.0 + it)
            }
        )
        val result = ScatterRelationshipAnalyzer().analyze(
            TrendMetricId.BADMINTON_TRAINING,
            TrendMetricId.FATIGUE_COMPOSITE,
            shortSeries
        )

        assertTrue(result.correlation == null)
        assertTrue(result.interpretation.contains("기록이 부족"))
        listOf("때문에", "원인입니다", "확실합니다").forEach { banned ->
            assertFalse(result.interpretation.contains(banned))
        }
    }

    private fun strengthExercise(id: Long = 1): Exercise =
        Exercise(
            id = id,
            name = "Strength fixture",
            category = "근력운동",
            stableKey = "strength_fixture_$id",
            movementPattern = "SQUAT",
            movementCategory = "STRENGTH",
            primaryMuscles = "QUADS",
            secondaryMuscles = "GLUTES",
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
            neuralHeavyWeight = 0.7,
            localLoadWeight = 0.7,
            progressMetricType = "ESTIMATED_1RM",
            strengthProgressionGroup = "SQUAT",
            hypertrophyVolumeGroup = "QUADS",
            mainLiftGroup = "SQUAT",
            accessoryContributionGroup = "NONE",
            estimated1RmEligible = true,
            volumeLoadEligible = true,
            badmintonTransferRoles = "NONE",
            badmintonTransferStrength = "NONE",
            courtMovementTypes = "NONE",
            badmintonSkillTargets = "NONE",
            stabilityDemandLevel = "MODERATE",
            mobilityDemandLevel = "MODERATE",
            balanceContributionTags = "LOWER_PUSH|SQUAT_PATTERN",
            analysisEligibility = "FATIGUE|STRENGTH_PROGRESS|HYPERTROPHY_VOLUME|BALANCE",
            metadataConfidence = "HIGH"
        )

    private fun badmintonExercise(id: Long = 2, name: String = "Badminton fixture"): Exercise =
        Exercise(
            id = id,
            name = name,
            category = "스포츠",
            stableKey = "badminton_fixture_$id",
            movementPattern = "FOOTWORK",
            movementCategory = "REACTIVE",
            primaryMuscles = "QUADS|CALVES",
            secondaryMuscles = "CORE",
            equipment = "NONE",
            compoundType = "DRILL",
            forceType = "DECELERATE",
            plane = "MULTI_PLANAR",
            laterality = "ALTERNATING",
            axialLoadLevel = "LOW",
            trainingRole = "SKILL",
            badmintonTransferRoles = "FOOTWORK|REACTION|DECELERATION",
            fatigueCategories = "NEURAL_SPEED|DECELERATION|ELASTIC_SSC",
            adaptiveBaselineGroups = "BADMINTON_COURT|DECELERATION",
            recoveryDecayProfile = "MEDIUM",
            systemicLoadWeight = 0.2,
            neuralSpeedWeight = 0.9,
            localLoadWeight = 0.5,
            decelerationWeight = 0.8,
            elasticSscWeight = 0.6,
            progressMetricType = "QUALITY_BASED",
            strengthProgressionGroup = "BADMINTON_TEST",
            hypertrophyVolumeGroup = "NONE",
            mainLiftGroup = "NONE",
            accessoryContributionGroup = "BADMINTON_SUPPORT",
            badmintonTransferStrength = "DIRECT",
            courtMovementTypes = "REACTION_RANDOM|DECELERATION",
            badmintonSkillTargets = "FOOTWORK_SPEED|DECELERATION_CONTROL",
            stabilityDemandLevel = "HIGH",
            mobilityDemandLevel = "MODERATE",
            balanceContributionTags = "UNILATERAL_LOWER|KNEE_CONTROL",
            analysisEligibility = "FATIGUE|BADMINTON_TRANSFER|BALANCE",
            metadataConfidence = "HIGH"
        )

    private fun canonicalRuntimeCatalog(): RuntimeExerciseMetadataCatalog =
        RuntimeExerciseMetadataCatalog.of(canonicalRows())

    private fun canonicalExercise(
        id: Long,
        catalog: RuntimeExerciseMetadataCatalog,
        stableKey: String
    ): Exercise {
        val metadata = catalog.resolveByStableKey(stableKey) ?: error("Missing canonical metadata for $stableKey")
        return Exercise(
            id = id,
            name = metadata.exerciseName,
            category = "근력운동",
            stableKey = metadata.stableKey
        )
    }

    private fun canonicalRows(): List<RuntimeExerciseMetadata> =
        RuntimeExerciseMetadataAssetLoader.parseCanonicalCsv(canonicalMetadataFile().readText(Charsets.UTF_8))

    private fun canonicalMetadataFile(): File = sequenceOf(
        File("src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}"),
        File("app/src/main/assets/${RuntimeExerciseMetadataAssetLoader.CANONICAL_ASSET_PATH}")
    ).firstOrNull(File::isFile) ?: error("Canonical metadata test asset not found.")

    private fun StrengthWeekIndex.allStrengthComponentsAreFallback100(): Boolean =
        listOf(intensityIndex, volumeIndex, efficiencyIndex, performanceIndex).all { value ->
            kotlin.math.abs(value - 100.0) < 0.001
        }

    private fun List<Double>.allApproximatelyEqual(): Boolean =
        size > 1 && all { value -> kotlin.math.abs(value - first()) < 0.001 }

    private fun componentReport(index: StrengthWeekIndex): String =
        "intensity=${index.intensityIndex}, volume=${index.volumeIndex}, efficiency=${index.efficiencyIndex}, " +
            "performance=${index.performanceIndex}, rawVolume=${index.rawVolume}, " +
            "effectiveSets=${index.effectiveSets}, exerciseScores=${index.exerciseScores}"

    private fun record(
        exercise: Exercise,
        date: LocalDate,
        confirmedSets: List<WorkoutSet>,
        plannedSets: List<WorkoutSet> = emptyList(),
        entryName: String = exercise.name
    ): WorkoutEntryWithSets {
        val entry = WorkoutEntry(
            id = exercise.id * 100 + date.dayOfYear,
            date = date.toString(),
            exerciseId = exercise.id,
            exerciseName = entryName,
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

    private fun pressure(
        key: String,
        pressure: Double,
        percentile: Double,
        zScore: Double?
    ): FatiguePressure =
        FatiguePressure(
            key = key,
            currentResidualLoad = pressure * 100.0,
            adaptiveTolerance = 100.0,
            rollingMean = 80.0,
            rollingStd = 10.0,
            zScore = zScore,
            percentile = percentile,
            pressure = pressure,
            level = FatigueLevel.HIGH,
            confidence = AnalysisConfidence.MEDIUM,
            baselineTrend = BaselineTrend.STABLE
        )

    private fun recovery(level: FatigueLevel): RecoverySignalSnapshot =
        RecoverySignalSnapshot(
            sleepSignal = level,
            fatigueSignal = FatigueLevel.NORMAL,
            sorenessSignal = FatigueLevel.NORMAL,
            stressSignal = FatigueLevel.NORMAL,
            moodSignal = FatigueLevel.NORMAL,
            overallRecoveryLevel = level,
            recoveryPenalty = if (level >= FatigueLevel.HIGH) 2 else 0,
            affectedBodyParts = emptyList(),
            confidence = AnalysisConfidence.MEDIUM,
            reasons = emptyList()
        )

    private fun performance(hasDrop: Boolean): PerformanceSignalSnapshot =
        PerformanceSignalSnapshot(
            sameLoadRpeIncrease = hasDrop,
            sameLoadRepsDrop = false,
            estimated1RmDrop = false,
            plannedSetFailure = false,
            testPerformanceDrop = false,
            footworkTestDrop = false,
            level = if (hasDrop) FatigueLevel.HIGH else FatigueLevel.NORMAL,
            confidence = AnalysisConfidence.MEDIUM,
            reasons = if (hasDrop) listOf("수행 저하 신호") else emptyList()
        )

    private fun pain(limited: Boolean): PainGateSnapshot =
        PainGateSnapshot(
            isLimited = limited,
            level = if (limited) FatigueLevel.LIMITED else FatigueLevel.NORMAL,
            restrictedTargets = emptyList(),
            reasons = emptyList(),
            confidence = AnalysisConfidence.MEDIUM
        )

    private fun assertNoExerciseNameParsingInTrendPackage() {
        val roots = listOf(
            File("src/main/java/com/training/trackplanner/analysis/trends"),
            File("app/src/main/java/com/training/trackplanner/analysis/trends")
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

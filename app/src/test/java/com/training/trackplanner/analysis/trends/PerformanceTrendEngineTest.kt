package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.features.AnalysisFeatureExtractor
import com.training.trackplanner.analysis.badminton.BadmintonObjective
import com.training.trackplanner.analysis.badminton.BadmintonObjectiveTransferLevel
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveCatalog
import com.training.trackplanner.analysis.badminton.CanonicalBadmintonObjectiveRelation
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
        val result = StrengthPerformanceIndexCalculator().calculate(
            weeks,
            mapOf(strength.stableKey to strength),
            emptyList()
        )
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
            exerciseMap = listOf(squat, deadlift).associateBy(Exercise::stableKey),
            allDailyMetrics = emptyList()
        )
        val latest = result.last()

        assertTrue("rawVolume should include canonical squat/deadlift records", latest.rawVolume > 0.0)
        assertTrue("effectiveSets should include canonical squat/deadlift records", latest.effectiveSets > 0)
        assertTrue("raw intensity should produce a squat exercise score", squat.stableKey in latest.exerciseScores)
        assertTrue("raw intensity should produce a deadlift exercise score", deadlift.stableKey in latest.exerciseScores)
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
            .calculate(weeks, mapOf(squat.stableKey to squat), emptyList())
            .last()

        assertTrue("rawVolume proves the set reached rawVolumeByWeek", latest.rawVolume > 0.0)
        assertTrue("effectiveSets proves the set reached effectiveSetsByWeek", latest.effectiveSets > 0)
        assertTrue("exerciseScores proves the set reached rawIntensityByWeek", squat.stableKey in latest.exerciseScores)
        assertEquals(componentReport(latest), 100.0, latest.intensityIndex, 0.001)
        assertEquals(componentReport(latest), 100.0, latest.volumeIndex, 0.001)
        assertEquals(componentReport(latest), 100.0, latest.efficiencyIndex, 0.001)
        assertEquals(componentReport(latest), 100.0, latest.performanceIndex, 0.001)
    }

    @Test
    fun lostCanonicalStableKeyDoesNotFeedStrengthSeriesThroughDisplayName() {
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
        assertEquals("all confirmed records should enter weekly aggregation", entries.size, groupedWeeks.sumOf { week -> week.entries.size })
        assertFalse(
            "display names must not restore canonical squat eligibility",
            squatFeatures.estimated1RmEligible || deadliftFeatures.estimated1RmEligible
        )
        assertEquals("NOT_APPLICABLE", squatFeatures.progressMetricType)
        assertEquals("NOT_APPLICABLE", deadliftFeatures.progressMetricType)
        assertFalse("STRENGTH_PROGRESS" in squatFeatures.analysisEligibility)
        assertFalse("STRENGTH_PROGRESS" in deadliftFeatures.analysisEligibility)
        assertTrue(
            "unknown stableKeys must not impersonate canonical strength identities",
            summary.strengthWeeks.all { week ->
                squat.stableKey !in week.exerciseScores && deadlift.stableKey !in week.exerciseScores
            }
        )
    }

    @Test
    fun stalePersistedMetadataForLostStableKeyDoesNotBorrowCanonicalIdentity() {
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
        assertFalse(
            "stale defaults must not restore canonical squat eligibility",
            squatFeatures.estimated1RmEligible || deadliftFeatures.estimated1RmEligible
        )
        assertEquals("NOT_APPLICABLE", squatFeatures.progressMetricType)
        assertEquals("NOT_APPLICABLE", deadliftFeatures.progressMetricType)
        assertTrue(
            "stale lost-key metadata must remain isolated from canonical strength identities",
            summary.strengthWeeks.all { week ->
                squat.stableKey !in week.exerciseScores && deadlift.stableKey !in week.exerciseScores
            }
        )
    }

    @Test
    fun badmintonPracticeUsesCanonicalIdentityAndDoesNotClaimSkillGain() {
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

        assertTrue(summary.badmintonPracticeSeries.dataPoints.any { point -> (point.value ?: 0.0) > 0.0 })
        val text = summary.trendSentence + summary.detailSections.joinToString { section -> section.shortInterpretation }
        assertFalse(text.contains("실력 향상"))
        assertNoExerciseNameParsingInTrendPackage()
    }

    @Test
    fun badmintonObjectiveExamplesUseResolvedExerciseNameInsteadOfFallbackIdLabel() {
        val engine = PerformanceTrendEngine(
            badmintonObjectiveCatalog = objectiveCatalog("footwork_fixture", BadmintonObjective.FOOTWORK)
        )
        val exercise = antiRotationSupportExercise(30, "운동" + "113", "footwork_fixture")
        val entries = (0 until 2).map { index ->
            record(
                exercise,
                today.minusWeeks(index.toLong()),
                listOf(set(seconds = 600, confirmed = true)),
                entryName = "랜덤 풋워크"
            )
        }

        val summary = engine.analyze(
            today = today,
            exercises = listOf(exercise),
            entriesWithSets = entries,
            dailyMetrics = emptyList()
        )
        assertTrue(summary.badmintonObjectiveExamples.values.flatten().contains("랜덤 풋워크"))
        assertFalse(summary.badmintonObjectiveExamples.values.flatten().any { it.matches(Regex("""운동\s*\d+""")) })
    }

    @Test
    fun badmintonDailyLoadsUseConfirmedSetsAndWeeklyPointsRemainWeekly() {
        val practice = badmintonExercise()
        val objectiveExercise = antiRotationSupportExercise(33, "Reaction fixture", "reaction_fixture")
        val summary = PerformanceTrendEngine(
            badmintonObjectiveCatalog = objectiveCatalog(
                objectiveExercise.stableKey,
                BadmintonObjective.REACTION,
                BadmintonObjective.DECELERATION,
                BadmintonObjective.FOOTWORK
            )
        ).analyze(
            today = today,
            exercises = listOf(practice, objectiveExercise),
            entriesWithSets = listOf(
                record(
                    practice,
                    today.minusDays(1),
                    listOf(set(seconds = 600, confirmed = true)),
                    plannedSets = listOf(set(seconds = 3600, confirmed = false))
                ),
                record(
                    objectiveExercise,
                    today.minusDays(1),
                    listOf(set(reps = 10, confirmed = true))
                )
            ),
            dailyMetrics = emptyList()
        )

        assertEquals(1, summary.badmintonPracticeDailyLoads.size)
        assertEquals(10.0, summary.badmintonPracticeDailyLoads.single().practiceLoad, 0.001)
        val daily = summary.badmintonObjectiveDailyStimulus.single()
        assertEquals(1.0, daily.objectiveStimulus.getValue("REACTION"), 0.001)
        assertEquals(1.0, daily.objectiveStimulus.getValue("DECELERATION"), 0.001)
        assertEquals(1.0, daily.objectiveStimulus.getValue("FOOTWORK"), 0.001)
        assertFalse("role/body-part keys must not leak into transfer objective chart", "GRIP_FOREARM" in daily.objectiveStimulus)
        assertFalse("movement category must not leak into transfer objective chart", "REACTIVE" in daily.objectiveStimulus)
        assertTrue(summary.badmintonObjectiveExamples["REACTION"].orEmpty().contains(objectiveExercise.name))
        assertTrue(summary.badmintonPracticeWeeks.map { it.weekStart }.distinct().size <= summary.badmintonPracticeWeeks.size)
    }

    @Test
    fun genericCoreSupportDoesNotBecomeAntiRotationTransferObjective() {
        val exercise = antiRotationSupportExercise(
            id = 31,
            name = "Dead bug fixture",
            stableKey = "dead_bug_fixture"
        )
        val summary = PerformanceTrendEngine().analyze(
            today = today,
            exercises = listOf(exercise),
            entriesWithSets = listOf(record(exercise, today.minusDays(1), listOf(set(reps = 10, confirmed = true)))),
            dailyMetrics = emptyList()
        )

        assertTrue(summary.badmintonObjectiveDailyStimulus.isEmpty())
    }

    @Test
    fun explicitAntiRotationExerciseCountsAsAntiRotationTransferObjective() {
        val exercise = antiRotationSupportExercise(
            id = 32,
            name = "Pallof press fixture",
            stableKey = "landmine_anti_rotation"
        )
        val summary = PerformanceTrendEngine(
            badmintonObjectiveCatalog = objectiveCatalog(exercise.stableKey, BadmintonObjective.ANTI_ROTATION)
        ).analyze(
            today = today,
            exercises = listOf(exercise),
            entriesWithSets = listOf(record(exercise, today.minusDays(1), listOf(set(reps = 10, confirmed = true)))),
            dailyMetrics = emptyList()
        )

        val daily = summary.badmintonObjectiveDailyStimulus.single()
        assertTrue(daily.objectiveStimulus.getValue("ANTI_ROTATION") > 0.0)
    }

    @Test
    fun repRangeSharesUseConfirmedPerformedRepsAndIncludeFiveInLowRange() {
        val strength = strengthExercise()
        val summary = PerformanceTrendEngine().analyze(
            today = today,
            exercises = listOf(strength),
            entriesWithSets = listOf(
                record(
                    strength,
                    today,
                    listOf(
                        set(reps = 5, weightKg = 100.0, confirmed = true),
                        set(reps = 6, weightKg = 90.0, confirmed = true),
                        set(reps = 10, weightKg = 60.0, confirmed = true)
                    ),
                    plannedSets = listOf(set(reps = 3, weightKg = 200.0, confirmed = false))
                )
            ),
            dailyMetrics = emptyList()
        )

        val latest = summary.repRangeWeeks.last { it.confirmedSetCount > 0 }
        assertEquals(3, latest.confirmedSetCount)
        assertEquals(100.0 / 3.0, latest.lowRepShare, 0.001)
        assertEquals(100.0 / 3.0, latest.moderateRepShare, 0.001)
        assertEquals(100.0 / 3.0, latest.highRepShare, 0.001)
    }

    @Test
    fun repRangeTrendStartsAtFirstRecordedWeekAndEndsAtLastRecordedWeek() {
        val strength = strengthExercise()
        val firstRecord = today.minusWeeks(3)
        val lastRecord = today.minusWeeks(1)
        val summary = PerformanceTrendEngine().analyze(
            today = today,
            exercises = listOf(strength),
            entriesWithSets = listOf(
                record(strength, firstRecord, listOf(set(reps = 5, weightKg = 100.0, confirmed = true))),
                record(strength, lastRecord, listOf(set(reps = 10, weightKg = 70.0, confirmed = true)))
            ),
            dailyMetrics = emptyList()
        )

        assertEquals(
            listOf(
                WeeklyAnalysisAggregator().weekStart(firstRecord),
                WeeklyAnalysisAggregator().weekStart(firstRecord).plusWeeks(1),
                WeeklyAnalysisAggregator().weekStart(lastRecord)
            ),
            summary.repRangeWeeks.map(RepRangeWeekShare::weekStart)
        )
        assertEquals(listOf(1, 0, 1), summary.repRangeWeeks.map(RepRangeWeekShare::confirmedSetCount))
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

    private fun objectiveCatalog(
        stableKey: String,
        vararg objectives: BadmintonObjective
    ): CanonicalBadmintonObjectiveCatalog = CanonicalBadmintonObjectiveCatalog.of(
        objectives.mapIndexed { index, objective ->
            CanonicalBadmintonObjectiveRelation(
                relationId = "fixture_$index",
                exerciseStableKey = stableKey,
                objective = objective,
                transferLevel = BadmintonObjectiveTransferLevel.DIRECT,
                provenance = "TEST",
                evidenceRelationKeys = setOf("TEST_$index"),
                reviewReason = "Test fixture"
            )
        }
    )

    private fun badmintonExercise(name: String = "Badminton fixture"): Exercise =
        Exercise(
            name = name,
            category = "스포츠",
            stableKey = "ex_ae9ecdbc",
            activityKind = "SPORT_SESSION",
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

    private fun antiRotationSupportExercise(id: Long, name: String, stableKey: String): Exercise =
        Exercise(
            name = name,
            category = "기능성운동",
            stableKey = stableKey,
            movementPattern = "ANTI_ROTATION",
            movementCategory = "STABILITY",
            primaryMuscles = "CORE",
            secondaryMuscles = "SHOULDERS",
            equipment = "CABLE",
            compoundType = "DRILL",
            forceType = "BRACE",
            plane = "TRANSVERSE",
            laterality = "BILATERAL",
            axialLoadLevel = "LOW",
            badmintonTransferRoles = "ANTI_ROTATION_STABILITY",
            fatigueCategories = "ANTI_ROTATION",
            adaptiveBaselineGroups = "ANTI_ROTATION",
            recoveryDecayProfile = "MEDIUM",
            antiRotationWeight = 0.75,
            localLoadWeight = 0.25,
            progressMetricType = "QUALITY_BASED",
            badmintonTransferStrength = "SUPPORTIVE",
            badmintonSkillTargets = "ANTI_ROTATION_STABILITY",
            stabilityDemandLevel = "HIGH",
            mobilityDemandLevel = "LOW",
            balanceContributionTags = "ANTI_ROTATION",
            analysisEligibility = "FATIGUE|BADMINTON_TRANSFER|BALANCE",
            metadataConfidence = "HIGH"
        )

    private fun canonicalRuntimeCatalog(): RuntimeExerciseMetadataCatalog =
        canonicalRows().let { rows ->
            RuntimeExerciseMetadataCatalog.of(
                metadata = rows,
                canonicalBadmintonAuthorityKeys = rows.map { it.stableKey }
            )
        }

    private fun canonicalExercise(
        id: Long,
        catalog: RuntimeExerciseMetadataCatalog,
        stableKey: String
    ): Exercise {
        val metadata = catalog.resolveByStableKey(stableKey) ?: error("Missing canonical metadata for $stableKey")
        return Exercise(
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
            id = exercise.stableKey.hashCode().toLong() * 100 + date.dayOfYear,
            date = date.toString(),
            exerciseStableKey = exercise.stableKey,
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

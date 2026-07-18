package com.training.trackplanner.analysis.fatigue

import com.training.trackplanner.analysis.features.BodyweightEffectiveLoadCalculator
import com.training.trackplanner.analysis.features.AnalysisExerciseDisplayNameResolver
import com.training.trackplanner.analysis.features.DurationHoldLoadCalculator
import com.training.trackplanner.data.DailyMetric
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.InitialUserProfile
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

class DailyFatigueCalculator(
    private val metadataCatalog: RuntimeExerciseMetadataCatalog
) {
    fun calculate(
        targetDate: LocalDate,
        exercises: List<Exercise>,
        entriesWithSets: List<WorkoutEntryWithSets>,
        initialProfile: InitialUserProfile?,
        dailyMetrics: List<DailyMetric> = emptyList()
    ): DailyFatigueResult =
        calculateSeries(targetDate, 1, exercises, entriesWithSets, initialProfile, dailyMetrics).single()

    fun calculateSeries(
        endDate: LocalDate,
        days: Int,
        exercises: List<Exercise>,
        entriesWithSets: List<WorkoutEntryWithSets>,
        initialProfile: InitialUserProfile?,
        dailyMetrics: List<DailyMetric> = emptyList()
    ): List<DailyFatigueResult> {
        val exerciseMap = exercises.associateBy { it.id }
        val records = entriesWithSets.mapNotNull { record ->
            val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull() ?: return@mapNotNull null
            val confirmedSets = record.sets.filter { it.confirmed }
            val exercise = exerciseMap[record.entry.exerciseId] ?: return@mapNotNull null
            if (confirmedSets.isEmpty()) return@mapNotNull null
            val metadata = ResolvedFatigueMetadata.from(exercise, metadataCatalog.resolve(exercise))
            RecordContext(
                date = date,
                record = record,
                exercise = exercise,
                metadata = metadata,
                confirmedSets = confirmedSets,
                rpe = averageRpe(record, confirmedSets) ?: defaultRpe(metadata),
                rawWorkload = calculateWorkload(
                    record = record,
                    exercise = exercise,
                    sets = confirmedSets,
                    metadata = metadata,
                    bodyWeightKg = BodyweightEffectiveLoadCalculator.bodyWeightFor(
                        date = record.entry.date,
                        dailyMetrics = dailyMetrics,
                        initialProfile = initialProfile
                    )
                )
            )
        }.filter { it.date <= endDate }

        val contributions = records.map { record -> record.toContribution(records) }
        val seed = InitialProfileBaselineSeeder.seed(initialProfile)
        val rawCache = mutableMapOf<LocalDate, RawDailyFatigue>()
        fun raw(date: LocalDate): RawDailyFatigue =
            rawCache.getOrPut(date) { aggregateRaw(date, contributions) }

        val count = days.coerceAtLeast(1)
        return (count - 1 downTo 0).map { offset ->
            val date = endDate.minusDays(offset.toLong())
            val todayRaw = raw(date)
            val observedDates = records.asSequence()
                .map { it.date }
                .filter { it in date.minusDays(28)..date.minusDays(1) }
                .distinct()
                .sorted()
                .toList()
            val recent14 = observedDates
                .filter { it >= date.minusDays(14) }
                .map { raw(it).axes }
            val previous14 = observedDates
                .filter { it < date.minusDays(14) }
                .map { raw(it).axes }
            val baseline = FatigueBaselineCalculator.effectiveBaseline(
                recent14 = recent14,
                previous14 = previous14,
                seed = seed,
                observedDayCount = observedDates.size
            )
            val scores = scoreAxes(todayRaw.axes, baseline.axes)
            val canonicalAxisScores = listOf(
                scores.highForceNeural,
                scores.systemicMuscular,
                scores.localMuscular,
                scores.highSpeed,
                scores.reactive
            ).map(Double::roundToInt)
            val recoveryPressureScore = scores.recoveryPressure.roundToInt()
            val ofi = OverallFatigueIndexCalculator.calculate(canonicalAxisScores)
            val currentGroups = aggregateGroups(date, contributions)
            val previousGroupKeys = aggregateGroups(date.minusDays(1), contributions)
                .map { it.groupType to it.groupKey }
                .toSet()
            val repeatedLocalGroup = scores.localMuscular >= FatigueThresholds.AXIS_HIGH_COUNT_START && currentGroups.any { group ->
                group.localFatigue > 0.0 && (group.groupType to group.groupKey) in previousGroupKeys
            }
            val cautionReasons = buildList {
                if (scores.highForceNeural >= FatigueThresholds.DAILY_AXIS_CAUTION_START) add("HIGH_FORCE_NEURAL_CAUTION")
                if (scores.highSpeed >= FatigueThresholds.DAILY_AXIS_CAUTION_START) add("HIGH_SPEED_CAUTION")
                if (scores.reactive >= FatigueThresholds.DAILY_AXIS_CAUTION_START) add("REACTIVE_CAUTION")
                if (scores.recoveryPressure >= FatigueThresholds.DAILY_AXIS_CAUTION_START) add("RECOVERY_DEBT_HIGH")
                if (canonicalAxisScores.count { it >= FatigueThresholds.AXIS_HIGH_COUNT_START } >= 3) add("GLOBAL_HIGH_FATIGUE")
                if (repeatedLocalGroup) add("LOCAL_GROUP_REPEAT_CAUTION")
            }
            DailyFatigueResult(
                state = DailyFatigueState(
                    date = date,
                    highForceNeuralFatigue = todayRaw.axes.highForceNeural,
                    systemicMuscularFatigue = todayRaw.axes.systemicMuscular,
                    localMuscularFatigue = todayRaw.axes.localMuscular,
                    highSpeedFatigue = todayRaw.axes.highSpeed,
                    reactiveFatigue = todayRaw.axes.reactive,
                    recoveryPressure = todayRaw.axes.recoveryPressure,
                    highForceNeuralScore = canonicalAxisScores[0],
                    systemicMuscularScore = canonicalAxisScores[1],
                    localMuscularScore = canonicalAxisScores[2],
                    highSpeedScore = canonicalAxisScores[3],
                    reactiveScore = canonicalAxisScores[4],
                    recoveryPressureScore = recoveryPressureScore,
                    overallFatigueIndex = ofi,
                    readinessLabel = FatigueLabelResolver.label(ofi),
                    cautionReasons = cautionReasons,
                    confidence = baseline.confidence,
                    confirmedTrainingLoad = todayRaw.confirmedTrainingLoad
                ),
                groupStates = currentGroups,
                recordContributions = contributions.filter { it.date == date }
            )
        }
    }

    private fun RecordContext.toContribution(allRecords: List<RecordContext>): RecordFatigueContribution {
        val prior = allRecords.filter { candidate ->
            candidate.date < date && candidate.date >= date.minusDays(28)
        }
        val baselineCandidates = sequenceOf(
            prior.filter { it.exercise.stableKey == exercise.stableKey },
            prior.filterSame(metadata.strengthProgressionGroup) { it.metadata.strengthProgressionGroup },
            prior.filterSame(metadata.redundancyGroup) { it.metadata.redundancyGroup },
            prior.filterSame(metadata.movementFamily) { it.metadata.movementFamily }
        ).firstOrNull { it.isNotEmpty() }.orEmpty()
        val (baseline, confidence) = FatigueBaselineCalculator.workloadBaseline(
            baselineCandidates.map { it.rawWorkload },
            rawWorkload
        )
        val baseLoadRatio = (rawWorkload / baseline).coerceIn(0.25, 3.0)
        val recordLoad = baseLoadRatio * FatigueRecordFactors.rpeFactor(rpe)
        val axes = calculateAxes(this, recordLoad)
        val baseDuration = metadata.recoveryDurationClass.ifBlank { "MEDIUM" }
        return RecordFatigueContribution(
            date = date,
            stableKey = exercise.stableKey,
            exerciseName = AnalysisExerciseDisplayNameResolver.resolve(record.entry, exercise, metadataCatalog),
            trainingLoad = recordLoad,
            axes = axes,
            recoveryDurationClass = baseDuration,
            strengthProgressionGroup = metadata.strengthProgressionGroup,
            redundancyGroup = metadata.redundancyGroup,
            movementFamily = metadata.movementFamily,
            programSlot = metadata.programSlot,
            confidence = confidence
        )
    }

    private fun calculateAxes(record: RecordContext, recordLoad: Double): FatigueAxisValues {
        val metadata = record.metadata
        val scale = 50.0
        val rpe = record.rpe
        val intensityModifier = when {
            rpe >= 9.0 -> 1.20
            rpe >= 8.0 -> 1.10
            else -> 1.00
        }
        val heavyModifier = if (
            metadata.progressMetricType == "ESTIMATED_1RM" ||
            metadata.programSlot.anyToken("MAIN", "HEAVY")
        ) 1.15 else 1.00
        val testModifier = if ("TEST_METRIC" in metadata.analysisEligibility) 1.20 else 1.00
        val recoveryModifier = if (
            metadata.tokens.anyToken("RECOVERY", "STRETCH", "MOBILITY")
        ) 0.50 else 1.00
        val highForceNeural = recordLoad * FatigueRecordFactors.axisLevelMultiplier(metadata.highForceNeuralStressLevel) *
            intensityModifier * heavyModifier * testModifier * recoveryModifier * scale

        val durationMinutes = record.confirmedSets.sumOf { it.seconds } / 60.0
        val durationModifier = if (durationMinutes > 0.0) {
            when {
                durationMinutes < 30.0 -> 0.85
                durationMinutes < 60.0 -> 1.00
                durationMinutes < 90.0 -> 1.15
                else -> 1.30
            }
        } else {
            when {
                record.confirmedSets.size <= 3 -> 0.85
                record.confirmedSets.size <= 8 -> 1.00
                else -> 1.15
            }
        }
        val compoundModifier = when {
            metadata.activityKind == "SPORT_SESSION" -> 1.20
            metadata.programSlot.anyToken("MAIN", "HEAVY") -> 1.15
            metadata.movementFamily.anyToken("ISOLATION") -> 0.70
            metadata.programSlot.anyToken("ACCESSORY") -> 0.85
            else -> 1.00
        }
        val systemic = recordLoad * FatigueRecordFactors.axisLevelMultiplier(metadata.systemicMuscularStressLevel) *
            durationModifier * compoundModifier * scale

        val totalReps = record.confirmedSets.sumOf { it.reps }
        val setVolumeModifier = when {
            record.confirmedSets.size <= 2 -> 0.75
            record.confirmedSets.size >= 6 -> 1.20
            else -> 1.00
        }
        val localVolumeModifier = setVolumeModifier *
            (if (totalReps >= 15) 1.10 else 1.00) *
            (if (rpe >= 9.0) 1.20 else 1.00)
        val local = recordLoad * FatigueRecordFactors.axisLevelMultiplier(metadata.localMuscularStressLevel) *
            localVolumeModifier * scale

        val velocityModifier = when {
            metadata.tokens.anyToken("NEURAL_SPEED", "SPRINT", "MAX_VELOCITY") -> 1.25
            metadata.tokens.anyToken("PLYOMETRIC", "ELASTIC_SSC", "ACCELERATION", "FIRST_STEP") -> 1.20
            metadata.tokens.anyToken("COURT_MOVEMENT", "FOOTWORK", "RUNNING") -> 1.15
            metadata.activityKind == "SPORT_SESSION" -> 1.10
            else -> 1.00
        }
        val highSpeed = recordLoad * FatigueRecordFactors.axisLevelMultiplier(metadata.highSpeedStressLevel) *
            intensityModifier * velocityModifier * recoveryModifier * scale

        val reactionModifier = when {
            metadata.cognitiveStressTags.anyToken("REACTION", "DECISION", "VISUAL_TRACKING") -> 1.25
            metadata.tokens.anyToken("RANDOM", "BEEP", "REACTION") -> 1.20
            metadata.activityKind == "SPORT_SESSION" -> 1.10
            else -> 1.00
        }
        val directionChangeModifier = when {
            metadata.tokens.anyToken("CHANGE_OF_DIRECTION", "DIRECTION_CHANGE", "REACTIVE_AGILITY") -> 1.20
            metadata.tokens.anyToken("FOOTWORK", "AGILITY", "SHUTTLE", "COURT_MOVEMENT") -> 1.15
            else -> 1.00
        }
        val reactive = recordLoad * FatigueRecordFactors.axisLevelMultiplier(metadata.reactiveStressLevel) *
            reactionModifier * directionChangeModifier * recoveryModifier * scale

        val other = listOf(highForceNeural, systemic, local, highSpeed, reactive)
        val recovery = RecoveryPressureCalculator.calculate(
            other,
            metadata.recoveryDurationClass.durationWeight()
        )
        return FatigueAxisValues(highForceNeural, systemic, local, highSpeed, reactive, recovery)
    }

    private fun aggregateRaw(
        targetDate: LocalDate,
        contributions: List<RecordFatigueContribution>
    ): RawDailyFatigue {
        var axes = FatigueAxisValues()
        contributions.forEach { contribution ->
            val days = ChronoUnit.DAYS.between(contribution.date, targetDate).toInt()
            if (days < 0) return@forEach
            val baseFactor = FatigueDecayModel.factor(contribution.recoveryDurationClass, days)
            axes += FatigueAxisValues(
                contribution.axes.highForceNeural * baseFactor,
                contribution.axes.systemicMuscular * baseFactor,
                contribution.axes.localMuscular * baseFactor,
                contribution.axes.highSpeed * baseFactor,
                contribution.axes.reactive * baseFactor,
                contribution.axes.recoveryPressure * baseFactor
            )
        }
        return RawDailyFatigue(
            axes = axes,
            confirmedTrainingLoad = contributions
                .filter { it.date == targetDate }
                .sumOf { it.trainingLoad }
        )
    }

    private fun aggregateGroups(
        targetDate: LocalDate,
        contributions: List<RecordFatigueContribution>
    ): List<GroupFatigueState> {
        data class GroupKey(val type: String, val key: String)
        val totals = mutableMapOf<GroupKey, FatigueAxisValues>()
        contributions.forEach { contribution ->
            val days = ChronoUnit.DAYS.between(contribution.date, targetDate).toInt()
            if (days < 0) return@forEach
            val baseFactor = FatigueDecayModel.factor(contribution.recoveryDurationClass, days)
            if (baseFactor <= 0.0) return@forEach
            val decayed = FatigueAxisValues(
                highForceNeural = contribution.axes.highForceNeural * baseFactor,
                systemicMuscular = contribution.axes.systemicMuscular * baseFactor,
                localMuscular = contribution.axes.localMuscular * baseFactor,
                highSpeed = contribution.axes.highSpeed * baseFactor,
                reactive = contribution.axes.reactive * baseFactor,
                recoveryPressure = contribution.axes.recoveryPressure * baseFactor
            )
            listOf(
                "strengthProgressionGroup" to contribution.strengthProgressionGroup,
                "redundancyGroup" to contribution.redundancyGroup,
                "movementFamily" to contribution.movementFamily,
                "programSlot" to contribution.programSlot,
                "exerciseName" to contribution.exerciseName
            ).filter { (_, key) -> key.isNotBlank() && key != "NOT_APPLICABLE" }
                .forEach { (type, key) ->
                    val groupKey = GroupKey(type, key)
                    totals[groupKey] = totals[groupKey].orEmpty() + decayed
                }
        }
        return totals.map { (key, axes) ->
            GroupFatigueState(
                date = targetDate,
                groupType = key.type,
                groupKey = key.key,
                highForceNeuralFatigue = axes.highForceNeural,
                systemicMuscularFatigue = axes.systemicMuscular,
                localFatigue = axes.localMuscular,
                highSpeedFatigue = axes.highSpeed,
                reactiveFatigue = axes.reactive,
                recoveryPressure = axes.recoveryPressure
            )
        }
    }

    private fun scoreAxes(today: FatigueAxisValues, baseline: FatigueAxisValues): FatigueAxisValues =
        FatigueAxisValues(
            today.highForceNeural.relativeScore(baseline.highForceNeural),
            today.systemicMuscular.relativeScore(baseline.systemicMuscular),
            today.localMuscular.relativeScore(baseline.localMuscular),
            today.highSpeed.relativeScore(baseline.highSpeed),
            today.reactive.relativeScore(baseline.reactive),
            today.recoveryPressure.relativeScore(baseline.recoveryPressure)
        )

    private fun calculateWorkload(
        record: WorkoutEntryWithSets,
        exercise: Exercise,
        sets: List<WorkoutSet>,
        metadata: ResolvedFatigueMetadata,
        bodyWeightKg: Double?
    ): Double {
        val totalReps = sets.sumOf { it.reps }
        val totalSeconds = sets.sumOf { it.seconds }
        val volumeLoad = sets.sumOf { set ->
            DurationHoldLoadCalculator.holdLoad(exercise, set, set.rpe ?: record.entry.rpe)
                ?: BodyweightEffectiveLoadCalculator.volumeLoad(exercise, set, bodyWeightKg)
        }
        val rpe = averageRpe(record, sets) ?: defaultRpe(metadata)
        return when (metadata.progressMetricType) {
            "LOAD_REPS", "VOLUME_LOAD", "LOAD_REPS_OR_REPS", "MACHINE_LOAD_REPS", "REPS_AT_LOAD" ->
                volumeLoad.takeIf { it > 0.0 } ?: totalReps.toDouble().coerceAtLeast(sets.size.toDouble())
            "ESTIMATED_1RM" -> sets.maxOfOrNull { set ->
                if (set.weightKg > 0.0 && set.reps > 0) set.weightKg * (1.0 + set.reps / 30.0) else 0.0
            }?.coerceAtLeast(1.0) ?: 1.0
            "REPS_OR_TIME", "TIME_OR_REPS", "LOAD_REPS_OR_TIME" ->
                max(totalReps.toDouble(), totalSeconds / 60.0).coerceAtLeast(sets.size.toDouble())
            "SESSION_DURATION" -> (totalSeconds / 60.0).coerceAtLeast(sets.size.toDouble()) * (rpe / 7.0)
            "TIME_DISTANCE", "TIME_OR_DISTANCE", "DISTANCE_OR_TIME_LOAD", "TIME_DISTANCE_PACE_OR_INTENSITY" ->
                (totalSeconds / 60.0).coerceAtLeast(sets.size.toDouble())
            "QUALITY_BASED", "QUALITY_LOAD_REPS" -> sets.size * (rpe / 7.0)
            "COUNT_ONLY" -> totalReps.toDouble().coerceAtLeast(sets.size.toDouble())
            else -> max(volumeLoad, max(totalReps.toDouble(), totalSeconds / 60.0)).coerceAtLeast(sets.size.toDouble())
        }
    }

    private fun averageRpe(record: WorkoutEntryWithSets, sets: List<WorkoutSet>): Double? =
        sets.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.average() ?: record.entry.rpe

    private fun defaultRpe(metadata: ResolvedFatigueMetadata): Double =
        when {
            "TEST_METRIC" in metadata.analysisEligibility -> 8.0
            metadata.activityKind == "SPORT_SESSION" -> 7.0
            metadata.tokens.anyToken("RECOVERY", "STRETCH", "MOBILITY") -> 4.0
            else -> 7.0
        }

    private fun <T> List<T>.filterSame(value: String, selector: (T) -> String): List<T> =
        if (value.isBlank() || value == "NOT_APPLICABLE") emptyList() else filter { selector(it) == value }

    private fun FatigueAxisValues?.orEmpty(): FatigueAxisValues = this ?: FatigueAxisValues()

    private fun Double.relativeScore(baseline: Double): Double =
        (this / baseline.coerceAtLeast(1.0) * 50.0).coerceIn(0.0, 100.0)

    private fun String.durationWeight(): Double =
        when (uppercase()) {
            "SHORT" -> 0.70
            "LONG" -> 1.25
            "VERY_LONG" -> 1.50
            else -> 1.00
        }

    private fun String.anyToken(vararg fragments: String): Boolean =
        fragments.any { contains(it, ignoreCase = true) }

    private fun Collection<String>.anyToken(vararg fragments: String): Boolean =
        any { value -> value.anyToken(*fragments) }

    private data class RawDailyFatigue(
        val axes: FatigueAxisValues,
        val confirmedTrainingLoad: Double
    )

    private data class RecordContext(
        val date: LocalDate,
        val record: WorkoutEntryWithSets,
        val exercise: Exercise,
        val metadata: ResolvedFatigueMetadata,
        val confirmedSets: List<WorkoutSet>,
        val rpe: Double,
        val rawWorkload: Double
    )

    private data class ResolvedFatigueMetadata(
        val activityKind: String,
        val movementFamily: String,
        val movementSubtype: String,
        val programSlot: String,
        val redundancyGroup: String,
        val progressMetricType: String,
        val strengthProgressionGroup: String,
        val analysisEligibility: Set<String>,
        val cognitiveStressTags: Set<String>,
        val highForceNeuralStressLevel: String,
        val systemicMuscularStressLevel: String,
        val localMuscularStressLevel: String,
        val highSpeedStressLevel: String,
        val reactiveStressLevel: String,
        val recoveryDurationClass: String,
        val tokens: Set<String>
    ) {
        companion object {
            fun from(exercise: Exercise, runtime: RuntimeExerciseMetadata?): ResolvedFatigueMetadata {
                if (runtime != null) {
                    val tokens = buildSet {
                        add(runtime.movementFamily)
                        add(runtime.movementSubtype)
                        add(runtime.programSlot)
                        add(runtime.primaryStressProfile)
                        addAll(runtime.secondaryStressTags.values)
                        addAll(runtime.cognitiveStressTags.values)
                        addAll(runtime.sportContextTags.values)
                        addAll(runtime.badmintonTransferType.values)
                        addAll(runtime.badmintonSkillTargets.values)
                        addAll(runtime.badmintonPhysicalQualities.values)
                    }
                    val effectiveTokens = tokens.filter(String::isNotBlank).toSet()
                    return ResolvedFatigueMetadata(
                        activityKind = runtime.activityKind,
                        movementFamily = runtime.movementFamily,
                        movementSubtype = runtime.movementSubtype,
                        programSlot = runtime.programSlot,
                        redundancyGroup = runtime.redundancyGroup,
                        progressMetricType = runtime.progressMetricType,
                        strengthProgressionGroup = runtime.strengthProgressionGroup,
                        analysisEligibility = runtime.analysisEligibility.values.toSet(),
                        cognitiveStressTags = runtime.cognitiveStressTags.values.toSet(),
                        highForceNeuralStressLevel = highForceLevel(
                            runtime.activityKind,
                            runtime.progressMetricType,
                            runtime.programSlot,
                            effectiveTokens
                        ),
                        systemicMuscularStressLevel = runtime.systemicMuscularStressLevel,
                        localMuscularStressLevel = runtime.localMuscularStressLevel,
                        highSpeedStressLevel = highSpeedLevel(runtime.activityKind, effectiveTokens),
                        reactiveStressLevel = reactiveLevel(
                            runtime.activityKind,
                            runtime.cognitiveStressTags.values.toSet(),
                            effectiveTokens
                        ),
                        recoveryDurationClass = runtime.recoveryDurationClass
                            .ifBlank { runtime.recoveryDecayProfile }
                            .ifBlank { "MEDIUM" },
                        tokens = effectiveTokens
                    )
                }
                fun level(weight: Double): String = when {
                    weight >= 0.85 -> "VERY_HIGH"
                    weight >= 0.60 -> "HIGH"
                    weight >= 0.30 -> "MODERATE"
                    else -> "LOW"
                }
                val tokens = setOf(
                    exercise.movementPattern,
                    exercise.movementCategory,
                    exercise.trainingRole,
                    exercise.fatigueCategories,
                    exercise.courtMovementTypes,
                    exercise.badmintonSkillTargets,
                    exercise.badmintonTransferRoles
                ).flatMap { it.splitTokens() }.toSet()
                return ResolvedFatigueMetadata(
                    activityKind = exercise.activityKind,
                    movementFamily = exercise.movementPattern,
                    movementSubtype = "",
                    programSlot = exercise.trainingRole,
                    redundancyGroup = exercise.movementPattern,
                    progressMetricType = exercise.progressMetricType,
                    strengthProgressionGroup = exercise.strengthProgressionGroup,
                    analysisEligibility = exercise.analysisEligibility.splitTokens(),
                    cognitiveStressTags = emptySet(),
                    highForceNeuralStressLevel = maxLevel(
                        level(exercise.neuralHeavyWeight),
                        highForceLevel(exercise.activityKind, exercise.progressMetricType, exercise.trainingRole, tokens)
                    ),
                    systemicMuscularStressLevel = level(exercise.systemicLoadWeight),
                    localMuscularStressLevel = level(exercise.localLoadWeight),
                    highSpeedStressLevel = maxLevel(
                        level(exercise.neuralSpeedWeight),
                        highSpeedLevel(exercise.activityKind, tokens)
                    ),
                    reactiveStressLevel = maxLevel(
                        level(exercise.decelerationWeight),
                        reactiveLevel(exercise.activityKind, emptySet(), tokens)
                    ),
                    recoveryDurationClass = exercise.recoveryDecayProfile.ifBlank { "MEDIUM" },
                    tokens = tokens
                )
            }

            private fun highForceLevel(
                activityKind: String,
                progressMetricType: String,
                programSlot: String,
                tokens: Set<String>
            ): String = when {
                activityKind == "SPORT_SESSION" -> "LOW"
                progressMetricType == "ESTIMATED_1RM" || programSlot.has("MAIN", "HEAVY") -> "VERY_HIGH"
                tokens.has("HEAVY_LOAD", "MAX_STRENGTH", "HEAVY_AXIAL") -> "HIGH"
                programSlot.has("STRENGTH", "COMPOUND") -> "MODERATE"
                else -> "LOW"
            }

            private fun highSpeedLevel(activityKind: String, tokens: Set<String>): String = when {
                tokens.has("NEURAL_SPEED_LOAD") && tokens.has("SPRINT", "PLYOMETRIC", "ELASTIC_SSC") -> "VERY_HIGH"
                tokens.has(
                    "NEURAL_SPEED", "SPRINT", "MAX_VELOCITY", "PLYOMETRIC", "ELASTIC_SSC",
                    "ACCELERATION", "FIRST_STEP", "COURT_MOVEMENT", "FOOTWORK", "REACTIVE_AGILITY"
                ) -> "HIGH"
                activityKind == "SPORT_SESSION" || tokens.has("BADMINTON", "RUNNING") -> "MODERATE"
                else -> "LOW"
            }

            private fun reactiveLevel(
                activityKind: String,
                cognitiveStressTags: Set<String>,
                tokens: Set<String>
            ): String = when {
                cognitiveStressTags.has("REACTION", "DECISION") &&
                    tokens.has("CHANGE_OF_DIRECTION", "FOOTWORK", "REACTIVE_AGILITY") -> "VERY_HIGH"
                cognitiveStressTags.has("REACTION", "DECISION", "VISUAL_TRACKING") ||
                    tokens.has("CHANGE_OF_DIRECTION", "DIRECTION_CHANGE", "RANDOM", "BEEP", "REACTIVE_AGILITY") -> "HIGH"
                activityKind == "SPORT_SESSION" || tokens.has("BADMINTON", "FOOTWORK", "AGILITY") -> "MODERATE"
                else -> "LOW"
            }

            private fun maxLevel(first: String, second: String): String =
                if (levelRank(first) >= levelRank(second)) first else second

            private fun levelRank(level: String): Int = when (level) {
                "VERY_HIGH" -> 3
                "HIGH" -> 2
                "MODERATE" -> 1
                else -> 0
            }

            private fun String.has(vararg fragments: String): Boolean =
                fragments.any { fragment -> contains(fragment, ignoreCase = true) }

            private fun Set<String>.has(vararg fragments: String): Boolean =
                any { token -> token.has(*fragments) }

            private fun String.splitTokens(): Set<String> =
                split('|', ',', ';').map(String::trim).filter(String::isNotBlank).toSet()
        }
    }
}

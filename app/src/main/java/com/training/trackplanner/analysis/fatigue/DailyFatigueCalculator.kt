package com.training.trackplanner.analysis.fatigue

import com.training.trackplanner.analysis.features.AnalysisExerciseDisplayNameResolver
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
        initialProfile: InitialUserProfile?
    ): DailyFatigueResult =
        calculateSeries(targetDate, 1, exercises, entriesWithSets, initialProfile).single()

    fun calculateSeries(
        endDate: LocalDate,
        days: Int,
        exercises: List<Exercise>,
        entriesWithSets: List<WorkoutEntryWithSets>,
        initialProfile: InitialUserProfile?
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
                rawWorkload = calculateWorkload(record, confirmedSets, metadata)
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
            val axisScores = scores.values().map(Double::roundToInt)
            val ofi = OverallFatigueIndexCalculator.calculate(axisScores)
            val currentGroups = aggregateGroups(date, contributions)
            val previousGroupKeys = aggregateGroups(date.minusDays(1), contributions)
                .map { it.groupType to it.groupKey }
                .toSet()
            val repeatedLocalGroup = scores.localMuscular >= FatigueThresholds.AXIS_HIGH_COUNT_START && currentGroups.any { group ->
                group.localFatigue > 0.0 && (group.groupType to group.groupKey) in previousGroupKeys
            }
            val cautionReasons = buildList {
                if (scores.jointTendonImpact >= FatigueThresholds.DAILY_AXIS_CAUTION_START) add("JOINT_TENDON_CAUTION")
                if (scores.neuromuscular >= FatigueThresholds.DAILY_AXIS_CAUTION_START) add("POWER_REACTION_CAUTION")
                if (scores.recoveryPressure >= FatigueThresholds.DAILY_AXIS_CAUTION_START) add("RECOVERY_DEBT_HIGH")
                if (axisScores.count { it >= FatigueThresholds.AXIS_HIGH_COUNT_START } >= 3) add("GLOBAL_HIGH_FATIGUE")
                if (repeatedLocalGroup) add("LOCAL_GROUP_REPEAT_CAUTION")
            }
            DailyFatigueResult(
                state = DailyFatigueState(
                    date = date,
                    neuromuscularFatigue = todayRaw.axes.neuromuscular,
                    systemicMuscularFatigue = todayRaw.axes.systemicMuscular,
                    localMuscularFatigue = todayRaw.axes.localMuscular,
                    jointTendonImpactFatigue = todayRaw.axes.jointTendonImpact,
                    movementFocusFatigue = todayRaw.axes.movementFocus,
                    recoveryPressure = todayRaw.axes.recoveryPressure,
                    neuromuscularScore = axisScores[0],
                    systemicMuscularScore = axisScores[1],
                    localMuscularScore = axisScores[2],
                    jointTendonImpactScore = axisScores[3],
                    movementFocusScore = axisScores[4],
                    recoveryPressureScore = axisScores[5],
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
        val hasImpact = metadata.jointImpactStressTags.anyToken(
            "JUMP", "HOP", "LAND", "PLYOMETRIC", "DIRECTION", "COURT", "RUNNING"
        )
        val jointDuration = when {
            hasImpact -> FatigueDecayModel.atLeast(baseDuration, "LONG")
            metadata.jointTendonImpactStressLevel in setOf("HIGH", "VERY_HIGH") ->
                FatigueDecayModel.atLeast(baseDuration, "MEDIUM")
            else -> baseDuration
        }
        return RecordFatigueContribution(
            date = date,
            stableKey = exercise.stableKey,
            exerciseName = AnalysisExerciseDisplayNameResolver.resolve(record.entry, exercise, metadataCatalog),
            trainingLoad = recordLoad,
            axes = axes,
            recoveryDurationClass = baseDuration,
            jointRecoveryDurationClass = jointDuration,
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
        val speedModifier = if (
            metadata.tokens.anyToken("FOOTWORK", "REACTION", "PLYOMETRIC", "SPRINT", "CHANGE_OF_DIRECTION")
        ) 1.15 else 1.00
        val recoveryModifier = if (
            metadata.tokens.anyToken("RECOVERY", "STRETCH", "MOBILITY")
        ) 0.50 else 1.00
        val neuromuscular = recordLoad * FatigueRecordFactors.axisLevelMultiplier(metadata.neuromuscularStressLevel) *
            intensityModifier * heavyModifier * testModifier * speedModifier * recoveryModifier * scale

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

        val impactModifier = when {
            metadata.jointImpactStressTags.anyToken("JUMP", "HOP", "LAND", "PLYOMETRIC") -> 1.25
            metadata.jointImpactStressTags.anyToken("DIRECTION", "DECELERATION") -> 1.15
            metadata.activityKind == "SPORT_SESSION" ||
                metadata.jointImpactStressTags.anyToken("RUNNING", "COURT") -> 1.15
            metadata.tokens.anyToken("HEAVY_AXIAL", "AXIAL_LOWER") -> 1.15
            else -> 1.00
        }
        val tendonModifier = (if (metadata.tendonStressTags.isNotEmpty()) 1.10 else 1.00) *
            (if (totalReps >= 15) 1.10 else 1.00) *
            (if (rpe >= 9.0) 1.10 else 1.00)
        val joint = recordLoad * FatigueRecordFactors.axisLevelMultiplier(metadata.jointTendonImpactStressLevel) *
            impactModifier * tendonModifier * scale

        val coordinationModifier = when {
            metadata.tokens.anyToken("FOOTWORK", "AGILITY", "REACTION") -> 1.25
            metadata.tokens.anyToken("SINGLE_LEG", "UNILATERAL", "BALANCE", "ANTI_ROTATION") -> 1.15
            metadata.tokens.anyToken("MACHINE", "ISOLATION") -> 0.70
            else -> 1.00
        }
        val randomnessModifier = when {
            metadata.tokens.anyToken("RANDOM", "BEEP", "REACTION", "SHUTTLE") -> 1.20
            metadata.activityKind == "SPORT_SESSION" ||
                metadata.tokens.anyToken("MATCH", "LESSON", "TECHNICAL_SESSION") -> 1.15
            else -> 1.00
        }
        val movement = recordLoad * FatigueRecordFactors.axisLevelMultiplier(metadata.movementFocusDemandLevel) *
            coordinationModifier * randomnessModifier * scale

        val other = listOf(neuromuscular, systemic, local, joint, movement)
        val recovery = RecoveryPressureCalculator.calculate(
            other,
            metadata.recoveryDurationClass.durationWeight()
        )
        return FatigueAxisValues(neuromuscular, systemic, local, joint, movement, recovery)
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
            val jointFactor = FatigueDecayModel.factor(contribution.jointRecoveryDurationClass, days)
            axes += FatigueAxisValues(
                contribution.axes.neuromuscular * baseFactor,
                contribution.axes.systemicMuscular * baseFactor,
                contribution.axes.localMuscular * baseFactor,
                contribution.axes.jointTendonImpact * jointFactor,
                contribution.axes.movementFocus * baseFactor,
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
            val jointFactor = FatigueDecayModel.factor(contribution.jointRecoveryDurationClass, days)
            if (baseFactor <= 0.0 && jointFactor <= 0.0) return@forEach
            val decayed = FatigueAxisValues(
                neuromuscular = contribution.axes.neuromuscular * baseFactor,
                systemicMuscular = contribution.axes.systemicMuscular * baseFactor,
                localMuscular = contribution.axes.localMuscular * baseFactor,
                jointTendonImpact = contribution.axes.jointTendonImpact * jointFactor,
                movementFocus = contribution.axes.movementFocus * baseFactor,
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
                neuromuscularFatigue = axes.neuromuscular,
                systemicMuscularFatigue = axes.systemicMuscular,
                localFatigue = axes.localMuscular,
                jointTendonImpactFatigue = axes.jointTendonImpact,
                movementFocusFatigue = axes.movementFocus,
                recoveryPressure = axes.recoveryPressure
            )
        }
    }

    private fun scoreAxes(today: FatigueAxisValues, baseline: FatigueAxisValues): FatigueAxisValues =
        FatigueAxisValues(
            today.neuromuscular.relativeScore(baseline.neuromuscular),
            today.systemicMuscular.relativeScore(baseline.systemicMuscular),
            today.localMuscular.relativeScore(baseline.localMuscular),
            today.jointTendonImpact.relativeScore(baseline.jointTendonImpact),
            today.movementFocus.relativeScore(baseline.movementFocus),
            today.recoveryPressure.relativeScore(baseline.recoveryPressure)
        )

    private fun calculateWorkload(
        record: WorkoutEntryWithSets,
        sets: List<WorkoutSet>,
        metadata: ResolvedFatigueMetadata
    ): Double {
        val totalReps = sets.sumOf { it.reps }
        val totalSeconds = sets.sumOf { it.seconds }
        val volumeLoad = sets.sumOf { it.weightKg * it.reps }
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
        val tendonStressTags: Set<String>,
        val jointImpactStressTags: Set<String>,
        val neuromuscularStressLevel: String,
        val systemicMuscularStressLevel: String,
        val localMuscularStressLevel: String,
        val jointTendonImpactStressLevel: String,
        val movementFocusDemandLevel: String,
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
                        addAll(runtime.tendonStressTags.values)
                        addAll(runtime.ligamentJointStabilityStressTags.values)
                        addAll(runtime.jointImpactStressTags.values)
                        addAll(runtime.cognitiveStressTags.values)
                        addAll(runtime.sportContextTags.values)
                    }
                    return ResolvedFatigueMetadata(
                        activityKind = runtime.activityKind,
                        movementFamily = runtime.movementFamily,
                        movementSubtype = runtime.movementSubtype,
                        programSlot = runtime.programSlot,
                        redundancyGroup = runtime.redundancyGroup,
                        progressMetricType = runtime.progressMetricType,
                        strengthProgressionGroup = runtime.strengthProgressionGroup,
                        analysisEligibility = runtime.analysisEligibility.values.toSet(),
                        tendonStressTags = runtime.tendonStressTags.values.toSet(),
                        jointImpactStressTags = runtime.jointImpactStressTags.values.toSet(),
                        neuromuscularStressLevel = runtime.neuromuscularStressLevel,
                        systemicMuscularStressLevel = runtime.systemicMuscularStressLevel,
                        localMuscularStressLevel = runtime.localMuscularStressLevel,
                        jointTendonImpactStressLevel = runtime.jointTendonImpactStressLevel,
                        movementFocusDemandLevel = runtime.movementFocusDemandLevel,
                        recoveryDurationClass = runtime.recoveryDurationClass
                            .ifBlank { runtime.recoveryDecayProfile }
                            .ifBlank { "MEDIUM" },
                        tokens = tokens.filter(String::isNotBlank).toSet()
                    )
                }
                fun level(weight: Double): String = when {
                    weight >= 0.85 -> "VERY_HIGH"
                    weight >= 0.60 -> "HIGH"
                    weight >= 0.30 -> "MODERATE"
                    else -> "LOW"
                }
                return ResolvedFatigueMetadata(
                    activityKind = exercise.activityKind,
                    movementFamily = exercise.movementPattern,
                    movementSubtype = "",
                    programSlot = exercise.trainingRole,
                    redundancyGroup = exercise.movementPattern,
                    progressMetricType = exercise.progressMetricType,
                    strengthProgressionGroup = exercise.strengthProgressionGroup,
                    analysisEligibility = exercise.analysisEligibility.splitTokens(),
                    tendonStressTags = exercise.jointStressTags.splitTokens(),
                    jointImpactStressTags = exercise.jointStressTags.splitTokens(),
                    neuromuscularStressLevel = level(max(exercise.neuralHeavyWeight, exercise.neuralSpeedWeight)),
                    systemicMuscularStressLevel = level(exercise.systemicLoadWeight),
                    localMuscularStressLevel = level(exercise.localLoadWeight),
                    jointTendonImpactStressLevel = level(max(exercise.decelerationWeight, exercise.elasticSscWeight)),
                    movementFocusDemandLevel = level(max(exercise.neuralSpeedWeight, exercise.antiRotationWeight)),
                    recoveryDurationClass = exercise.recoveryDecayProfile.ifBlank { "MEDIUM" },
                    tokens = setOf(
                        exercise.movementPattern,
                        exercise.movementCategory,
                        exercise.trainingRole,
                        exercise.fatigueCategories,
                        exercise.jointStressTags
                    ).filter(String::isNotBlank).toSet()
                )
            }

            private fun String.splitTokens(): Set<String> =
                split('|', ',', ';').map(String::trim).filter(String::isNotBlank).toSet()
        }
    }
}

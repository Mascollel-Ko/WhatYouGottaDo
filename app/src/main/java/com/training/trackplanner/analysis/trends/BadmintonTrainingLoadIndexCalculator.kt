package com.training.trackplanner.analysis.trends

import com.training.trackplanner.analysis.features.AnalysisFeatureExtractor
import com.training.trackplanner.analysis.features.AnalysisExerciseFeatures
import com.training.trackplanner.analysis.readiness.AnalysisConfidence
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import java.time.LocalDate

class BadmintonTrainingLoadIndexCalculator(
    private val runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY
) {
    fun calculate(
        weeks: List<WeeklyTrainingData>,
        exerciseMap: Map<Long, Exercise>
    ): List<BadmintonWeekIndex> {
        val courtRaw = weeks.map { week -> week.entries.courtVolumeRaw(exerciseMap) }
        val footworkRaw = weeks.map { week -> week.entries.footworkReactiveRaw(exerciseMap) }
        val supportRaw = weeks.map { week -> week.entries.supportRaw(exerciseMap) }

        return weeks.mapIndexed { index, week ->
            val courtIndex = standardized(courtRaw, index)
            val footworkIndex = standardized(footworkRaw, index)
            val supportIndex = standardized(supportRaw, index)
            val trainingIndex = TrendMath.clamp(
                TrendMath.weightedMean(
                    values = listOf(courtIndex.score, footworkIndex.score, supportIndex.score),
                    weights = listOf(
                        PerformanceTrendConstants.BADMINTON_COURT_WEIGHT,
                        PerformanceTrendConstants.BADMINTON_FOOTWORK_WEIGHT,
                        PerformanceTrendConstants.BADMINTON_SUPPORT_WEIGHT
                    )
                ),
                PerformanceTrendConstants.STANDARD_MIN,
                PerformanceTrendConstants.STANDARD_MAX
            )

            BadmintonWeekIndex(
                weekStart = week.weekStart,
                courtVolumeIndex = courtIndex.score,
                footworkReactiveIndex = footworkIndex.score,
                supportIndex = supportIndex.score,
                trainingIndex = trainingIndex,
                confidence = TrendMath.combineConfidence(
                    listOf(courtIndex.confidence, footworkIndex.confidence, supportIndex.confidence)
                ),
                courtRaw = courtRaw[index] ?: 0.0,
                footworkReactiveRaw = footworkRaw[index] ?: 0.0,
                supportRaw = supportRaw[index] ?: 0.0,
                itemScores = week.entries.itemScores(exerciseMap)
            )
        }
    }

    fun dailyLoads(
        entriesWithSets: List<WorkoutEntryWithSets>,
        exerciseMap: Map<Long, Exercise>
    ): List<BadmintonDailyLoadPoint> =
        entriesWithSets
            .filter { record -> record.sets.any { set -> set.confirmed } }
            .groupBy { record -> runCatching { LocalDate.parse(record.entry.date) }.getOrNull() }
            .mapNotNull { (date, records) ->
                date ?: return@mapNotNull null
                val court = records.courtVolumeRaw(exerciseMap)
                val footwork = records.footworkReactiveRaw(exerciseMap)
                val support = records.supportRaw(exerciseMap)
                if (court + footwork + support <= 0.0) return@mapNotNull null
                BadmintonDailyLoadPoint(
                    date = date,
                    courtRaw = court,
                    footworkReactiveRaw = footwork,
                    supportRaw = support,
                    methodRaw = records.methodRaw(exerciseMap)
                )
            }
            .sortedBy { point -> point.date }

    fun methodExamples(
        entriesWithSets: List<WorkoutEntryWithSets>,
        exerciseMap: Map<Long, Exercise>,
        displayNamesById: Map<Long, String>
    ): Map<String, List<String>> {
        val examples = linkedMapOf<String, MutableList<String>>()
        entriesWithSets
            .filter { record -> record.sets.any { set -> set.confirmed } }
            .forEach { record ->
                val exercise = exerciseMap[record.entry.exerciseId] ?: return@forEach
                val features = featuresFor(record, exerciseMap) ?: return@forEach
                val dose = record.badmintonDose(features)
                if (dose <= 0.0) return@forEach
                val name = displayName(record, exercise, displayNamesById)
                if (name.isBlank()) return@forEach
                features.transferObjectiveKeys().forEach { key ->
                    val list = examples.getOrPut(key) { mutableListOf() }
                    if (name !in list && list.size < 2) list += name
                }
            }
        return examples
    }

    private fun standardized(values: List<Double?>, index: Int): StandardizedComponent {
        val (baseline, confidence) = TrendMath.baselineFor(values, index)
        return StandardizedComponent(
            score = TrendMath.higherIsBetterScore(values[index], baseline),
            confidence = confidence
        )
    }

    private fun List<WorkoutEntryWithSets>.courtVolumeRaw(
        exerciseMap: Map<Long, Exercise>
    ): Double =
        sumOf { record ->
            val features = featuresFor(record, exerciseMap) ?: return@sumOf 0.0
            if (!features.isShuttlePlaySession()) return@sumOf 0.0
            record.durationMinutes() * PerformanceTrendConstants.badmintonIntensityFactor(features.averageRpe)
        }.takeIf { value -> value > 0.0 }
            ?: 0.0

    private fun List<WorkoutEntryWithSets>.footworkReactiveRaw(
        exerciseMap: Map<Long, Exercise>
    ): Double =
        sumOf { record ->
            val features = featuresFor(record, exerciseMap) ?: return@sumOf 0.0
            if (features.isShuttlePlaySession()) return@sumOf 0.0
            if (!features.isFootworkReactive()) return@sumOf 0.0
            val dose = when {
                record.durationMinutes() > 0.0 -> record.durationMinutes() *
                    PerformanceTrendConstants.DRILL_DENSITY_FACTOR *
                    features.reactiveWeight()
                else -> record.completedReps().toDouble() * features.reactiveWeight()
            }
            if ("TEST_ONLY" in features.analysisEligibility) dose * 0.35 else dose
        }.takeIf { value -> value > 0.0 }
            ?: 0.0

    private fun List<WorkoutEntryWithSets>.supportRaw(
        exerciseMap: Map<Long, Exercise>
    ): Double =
        sumOf { record ->
            val features = featuresFor(record, exerciseMap) ?: return@sumOf 0.0
            if (features.isShuttlePlaySession()) return@sumOf 0.0
            val supportWeight = PerformanceTrendConstants.badmintonSupportWeight(features.badmintonTransferStrength)
            if (supportWeight <= 0.0) return@sumOf 0.0
            val baseDose = record.baseDose()
            baseDose * supportWeight * features.supportCorrection()
        }.takeIf { value -> value > 0.0 }
            ?: 0.0

    private fun List<WorkoutEntryWithSets>.itemScores(
        exerciseMap: Map<Long, Exercise>
    ): Map<Long, Double> =
        associate { record ->
            val features = featuresFor(record, exerciseMap)
            val dose = if (features == null || features.isShuttlePlaySession()) 0.0 else {
                record.baseDose() * PerformanceTrendConstants.badmintonSupportWeight(features.badmintonTransferStrength)
            }
            record.entry.exerciseId to dose
        }.filterValues { value -> value > 0.0 }

    private fun List<WorkoutEntryWithSets>.methodRaw(
        exerciseMap: Map<Long, Exercise>
    ): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        forEach { record ->
            val features = featuresFor(record, exerciseMap) ?: return@forEach
            val dose = record.badmintonDose(features)
            if (dose <= 0.0) return@forEach
            val keys = features.transferObjectiveKeys()
            if (keys.isEmpty()) return@forEach
            // ponytail: multi-label transfer stimulus is intentionally duplicated per objective, not split 1/n.
            keys.forEach { key -> totals[key] = (totals[key] ?: 0.0) + dose }
        }
        return totals
    }

    private fun WorkoutEntryWithSets.badmintonDose(features: AnalysisExerciseFeatures): Double = when {
        features.isShuttlePlaySession() -> durationMinutes() * PerformanceTrendConstants.badmintonIntensityFactor(features.averageRpe)
        features.isFootworkReactive() -> {
            val dose = when {
                durationMinutes() > 0.0 -> durationMinutes() *
                    PerformanceTrendConstants.DRILL_DENSITY_FACTOR *
                    features.reactiveWeight()
                else -> completedReps().toDouble() * features.reactiveWeight()
            }
            if ("TEST_ONLY" in features.analysisEligibility) dose * 0.35 else dose
        }
        else -> {
            val supportWeight = PerformanceTrendConstants.badmintonSupportWeight(features.badmintonTransferStrength)
            if (supportWeight <= 0.0) 0.0 else baseDose() * supportWeight * features.supportCorrection()
        }
    }

    private fun displayName(
        record: WorkoutEntryWithSets,
        exercise: Exercise,
        displayNamesById: Map<Long, String>
    ): String =
        listOf(displayNamesById[exercise.id], record.entry.exerciseName, exercise.name)
            .filterNotNull()
            .firstOrNull { name -> !name.matches(Regex("""운동\s*\d+""")) }
            .orEmpty()

    private fun featuresFor(
        record: WorkoutEntryWithSets,
        exerciseMap: Map<Long, Exercise>
    ): AnalysisExerciseFeatures? {
        val exercise = exerciseMap[record.entry.exerciseId] ?: return null
        return AnalysisFeatureExtractor.fromRecord(
            exercise,
            record.entry,
            record.sets,
            runtimeMetadataCatalog.resolve(exercise)
        )
    }

    private fun AnalysisExerciseFeatures.isShuttlePlaySession(): Boolean =
        activityKind == "SPORT_SESSION" && stableKey in shuttlePlayStableKeys

    private fun AnalysisExerciseFeatures.isFootworkReactive(): Boolean =
        courtMovementTypes.any { type ->
            type in footworkReactiveTypes
        } ||
            badmintonTransferRoles.any { role ->
                role in setOf("FOOTWORK", "REACTION", "DECELERATION", "ACCELERATION", "JUMP_LANDING")
            }

    private fun AnalysisExerciseFeatures.reactiveWeight(): Double {
        val weights = mutableListOf<Double>()
        if ("REACTION" in badmintonTransferRoles || "REACTION_RANDOM" in courtMovementTypes) weights += 1.20
        if ("FOOTWORK" in badmintonTransferRoles || footworkOnlyTypes.any { it in courtMovementTypes }) weights += 1.00
        if ("DECELERATION" in badmintonTransferRoles || "DECELERATION" in courtMovementTypes) weights += 1.10
        if ("ACCELERATION" in badmintonTransferRoles || "FIRST_STEP" in courtMovementTypes) weights += 1.05
        return weights.maxOrNull() ?: 0.70
    }

    private fun AnalysisExerciseFeatures.supportCorrection(): Double {
        var correction = 1.0
        if ("DECELERATION" in fatigueCategories) correction *= 1.10
        if ("ELASTIC_SSC" in fatigueCategories) correction *= 1.10
        if ("ROTATION_POWER" in fatigueCategories) correction *= 1.05
        if ("OVERHEAD_REPETITION" in fatigueCategories) correction *= 1.05
        if ("GRIP_FOREARM" in fatigueCategories) correction *= 1.05
        return correction
    }

    private fun AnalysisExerciseFeatures.transferObjectiveKeys(): Set<String> =
        BadmintonTrainingMethodLabels.keysFrom(
            courtMovementTypes = courtMovementTypes,
            transferRoles = badmintonTransferRoles,
            skillTargets = badmintonSkillTargets + canonicalBadmintonSkillTargets,
            includeAntiRotation = hasExplicitAntiRotationTransferObjective()
        )

    private fun AnalysisExerciseFeatures.hasExplicitAntiRotationTransferObjective(): Boolean {
        val text = listOf(exerciseName, stableKey, movementSubtype, programSlot, redundancyGroup)
            .joinToString(" ")
            .uppercase()
        val explicitNames = listOf(
            "PALLOF",
            "SUITCASE",
            "LANDMINE_ANTI_ROTATION",
            "ANTI_ROTATION_PRESS",
            "ANTI_ROTATION_HOLD",
            "항회전",
            "회전저항",
            "팔로프",
            "수트케이스"
        )
        val standingOneArmCableRow =
            ("ONE_ARM" in text || "원암" in text) &&
                ("CABLE" in text || "케이블" in text) &&
                ("ROW" in text || "로우" in text) &&
                ("STANDING" in text || "서서" in text)
        return explicitNames.any { token -> token in text } || standingOneArmCableRow
    }

    private fun WorkoutEntryWithSets.durationMinutes(): Double =
        sets.filter { set -> set.confirmed }.sumOf { set -> set.seconds } / 60.0

    private fun WorkoutEntryWithSets.completedReps(): Int =
        sets.filter { set -> set.confirmed }.sumOf { set -> set.reps }

    private fun WorkoutEntryWithSets.baseDose(): Double {
        val completedSets = sets.filter { set -> set.confirmed }
        val volume = completedSets.sumOf { set -> set.weightKg * set.reps }
        if (volume > 0.0) return volume
        val reps = completedSets.sumOf { set -> set.reps }
        if (reps > 0) return reps * PerformanceTrendConstants.DEFAULT_BODYWEIGHT_SET_PROXY
        return durationMinutes() * PerformanceTrendConstants.TIMED_DRILL_PROXY_PER_MINUTE
    }

    private data class StandardizedComponent(
        val score: Double,
        val confidence: AnalysisConfidence
    )

    private companion object {
        val footworkReactiveTypes = setOf(
            "SPLIT_STEP",
            "FIRST_STEP",
            "LATERAL_MOVE",
            "CROSSOVER",
            "FRONT_LUNGE",
            "REAR_COURT",
            "MULTI_DIRECTION",
            "REACTION_RANDOM",
            "JUMP_LANDING",
            "DECELERATION",
            "RECOVERY_STEP"
        )
        val footworkOnlyTypes = setOf(
            "SPLIT_STEP",
            "FIRST_STEP",
            "LATERAL_MOVE",
            "CROSSOVER",
            "FRONT_LUNGE",
            "REAR_COURT",
            "MULTI_DIRECTION",
            "RECOVERY_STEP"
        )
        val shuttlePlayStableKeys = setOf(
            "ex_ae9ecdbc",
            "ex_badminton_lesson"
        )
    }
}

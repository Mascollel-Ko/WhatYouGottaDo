package com.training.trackplanner.analysis.badminton

import com.training.trackplanner.analysis.features.AnalysisFeatureExtractor
import com.training.trackplanner.analysis.features.AnalysisExerciseFeatures
import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadataCatalog
import com.training.trackplanner.data.WorkoutEntryWithSets
import com.training.trackplanner.data.WorkoutSet
import java.time.LocalDate

class BadmintonTransferScoreCalculator(
    private val runtimeMetadataCatalog: RuntimeExerciseMetadataCatalog = RuntimeExerciseMetadataCatalog.EMPTY
) {
    fun calculate(
        today: LocalDate,
        exercises: List<Exercise>,
        entriesWithSets: List<WorkoutEntryWithSets>
    ): BadmintonTransferScoreSnapshot {
        val exerciseMap = exercises.associateBy { exercise -> exercise.id }
        val start28 = today.minusDays(BadmintonTransferConstants.BASELINE_WINDOW_DAYS - 1)
        val start7 = today.minusDays(BadmintonTransferConstants.RECENT_WINDOW_DAYS - 1)
        val contributions28 = entriesWithSets.mapNotNull { record ->
            val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull() ?: return@mapNotNull null
            if (date !in start28..today) return@mapNotNull null
            contribution(record, exerciseMap[record.entry.exerciseId])
        }
        val contributions7 = contributions28.filter { contribution -> contribution.date in start7..today }

        return BadmintonTransferScoreSnapshot(
            totalTransferStimulus7d = contributions7.sumOf { contribution -> contribution.totalStimulus },
            totalTransferStimulus28d = contributions28.sumOf { contribution -> contribution.totalStimulus },
            transferRatio7dTo28dAverage = ratio7To28(contributions7, contributions28),
            axisStimulus7d = axisStimulus(contributions7),
            axisStimulus28d = axisStimulus(contributions28),
            transferTypeStimulus7d = typeStimulus(contributions7),
            topTransferExercises7d = topExercises(contributions7),
            sampleEntryCount7d = contributions7.size,
            sampleEntryCount28d = contributions28.size
        )
    }

    private fun contribution(
        record: WorkoutEntryWithSets,
        exercise: Exercise?
    ): TransferContribution? {
        if (exercise == null) return null
        val completedSets = record.sets.filter { set -> set.confirmed }
        if (completedSets.isEmpty()) return null
        val features = AnalysisFeatureExtractor.fromRecord(
            exercise,
            record.entry,
            record.sets,
            runtimeMetadataCatalog.resolve(exercise)
        )
        val transferType = BadmintonTransferMetadataMapper.transferType(features)
        val transferWeight = BadmintonTransferConstants.transferWeight(transferType)
        if (transferWeight <= 0.0) return null
        val axes = BadmintonTransferMetadataMapper.transferAxes(features)
        if (axes.isEmpty()) return null
        val exerciseLoad = exerciseLoad(completedSets, features)
        val totalStimulus = exerciseLoad * transferWeight
        if (totalStimulus <= 0.0) return null
        val date = runCatching { LocalDate.parse(record.entry.date) }.getOrNull() ?: return null

        return TransferContribution(
            date = date,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            transferType = transferType,
            axes = axes,
            totalStimulus = totalStimulus
        )
    }

    private fun exerciseLoad(
        completedSets: List<WorkoutSet>,
        features: AnalysisExerciseFeatures
    ): Double {
        val totalReps = completedSets.sumOf { set -> set.reps }
        val totalSeconds = completedSets.sumOf { set -> set.seconds }
        val averageWeight = completedSets
            .map { set -> set.weightKg }
            .filter { weight -> weight > 0.0 }
            .average()
            .takeUnless { value -> value.isNaN() }
            ?: 0.0
        val intensityFactor = if (averageWeight > 0.0) {
            BadmintonTransferConstants.intensityFactor(averageWeight)
        } else {
            1.0
        }
        val rpeFactor = BadmintonTransferConstants.rpeFactor(features.averageRpe)

        return when {
            totalReps > 0 -> totalReps * intensityFactor * rpeFactor
            totalSeconds > 0 -> (totalSeconds / BadmintonTransferConstants.DEFAULT_TIMED_DRILL_UNIT_SECONDS) * rpeFactor
            else -> completedSets.size.toDouble()
        }
    }

    private fun axisStimulus(contributions: List<TransferContribution>): Map<BadmintonTransferAxis, Double> {
        val result = BadmintonTransferAxis.entries.associateWith { 0.0 }.toMutableMap()
        contributions.forEach { contribution ->
            val perAxis = contribution.totalStimulus / contribution.axes.size
            contribution.axes.forEach { axis ->
                result[axis] = result.getValue(axis) + perAxis
            }
        }
        return result
    }

    private fun typeStimulus(contributions: List<TransferContribution>): Map<BadmintonTransferType, Double> {
        val result = BadmintonTransferType.entries.associateWith { 0.0 }.toMutableMap()
        contributions.forEach { contribution ->
            result[contribution.transferType] = result.getValue(contribution.transferType) + contribution.totalStimulus
        }
        return result
    }

    private fun topExercises(
        contributions: List<TransferContribution>
    ): List<BadmintonTransferExerciseStimulus> =
        contributions
            .groupBy { contribution -> contribution.exerciseId }
            .map { (_, grouped) ->
                val first = grouped.first()
                BadmintonTransferExerciseStimulus(
                    exerciseId = first.exerciseId,
                    exerciseName = first.exerciseName,
                    stimulus = grouped.sumOf { contribution -> contribution.totalStimulus },
                    transferType = grouped.maxByOrNull { contribution ->
                        BadmintonTransferConstants.transferWeight(contribution.transferType)
                    }?.transferType ?: first.transferType,
                    axes = grouped.flatMap { contribution -> contribution.axes }.toSet()
                )
            }
            .sortedByDescending { exercise -> exercise.stimulus }
            .take(BadmintonTransferConstants.TOP_EXERCISE_LIMIT)

    private fun ratio7To28(
        contributions7: List<TransferContribution>,
        contributions28: List<TransferContribution>
    ): Double? {
        val total7 = contributions7.sumOf { contribution -> contribution.totalStimulus }
        val average7From28 = contributions28.sumOf { contribution -> contribution.totalStimulus } /
            (BadmintonTransferConstants.BASELINE_WINDOW_DAYS / BadmintonTransferConstants.RECENT_WINDOW_DAYS).toDouble()
        if (average7From28 <= BadmintonTransferConstants.EPSILON) return null
        return total7 / average7From28
    }

    private data class TransferContribution(
        val date: LocalDate,
        val exerciseId: Long,
        val exerciseName: String,
        val transferType: BadmintonTransferType,
        val axes: Set<BadmintonTransferAxis>,
        val totalStimulus: Double
    )
}

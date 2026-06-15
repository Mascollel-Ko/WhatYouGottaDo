package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet

data class AnalysisExerciseFeatures(
    val exerciseId: Long,
    val exerciseName: String,
    val metadataConfidence: String,
    val analysisEligibility: Set<String>,
    val movementPattern: String,
    val movementCategory: String,
    val primaryMuscles: Set<String>,
    val secondaryMuscles: Set<String>,
    val equipment: Set<String>,
    val compoundType: String,
    val forceType: String,
    val plane: String,
    val laterality: String,
    val axialLoadLevel: String,
    val trainingRole: String,
    val systemicLoadWeight: Double,
    val neuralHeavyWeight: Double,
    val neuralSpeedWeight: Double,
    val localLoadWeight: Double,
    val decelerationWeight: Double,
    val elasticSscWeight: Double,
    val rotationPowerWeight: Double,
    val antiRotationWeight: Double,
    val overheadSwingWeight: Double,
    val gripLoadWeight: Double,
    val fatigueCategories: Set<String>,
    val adaptiveBaselineGroups: Set<String>,
    val recoveryDecayProfile: String,
    val progressMetricType: String,
    val strengthProgressionGroup: String,
    val hypertrophyVolumeGroup: String,
    val mainLiftGroup: String,
    val accessoryContributionGroup: String,
    val estimated1RmEligible: Boolean,
    val volumeLoadEligible: Boolean,
    val badmintonTransferRoles: Set<String>,
    val badmintonTransferStrength: String,
    val courtMovementTypes: Set<String>,
    val badmintonSkillTargets: Set<String>,
    val jointStressTags: Set<String>,
    val stabilityDemandLevel: String,
    val mobilityDemandLevel: String,
    val balanceContributionTags: Set<String>,
    val totalVolumeLoad: Double?,
    val totalReps: Int?,
    val totalSets: Int,
    val completedSets: Int,
    val averageRpe: Double?,
    val maxRpe: Double?,
    val estimated1Rm: Double?,
    val durationMinutes: Double?,
    val isCompleted: Boolean,
    val isPlannedOnly: Boolean,
    val recordDate: String?
)

object ExerciseAnalysisMapper {
    fun fromExercise(exercise: Exercise): AnalysisExerciseFeatures =
        fromRecord(
            exercise = exercise,
            entry = null,
            sets = emptyList()
        )

    fun fromRecord(
        exercise: Exercise,
        entry: WorkoutEntry?,
        sets: List<WorkoutSet>
    ): AnalysisExerciseFeatures {
        val completedSets = sets.filter { set -> set.confirmed }
        val rpeValues = completedSets.mapNotNull { set -> set.rpe }
            .ifEmpty { entry?.rpe?.let(::listOf) ?: emptyList() }
        val totalVolumeLoad = completedSets
            .takeIf { it.isNotEmpty() }
            ?.sumOf { set -> set.reps * set.weightKg }
        val totalSeconds = completedSets.sumOf { set -> set.seconds }
        val estimated1Rm = if (exercise.estimated1RmEligible) {
            completedSets
                .filter { set -> set.weightKg > 0.0 && set.reps in 1..12 }
                .maxOfOrNull { set -> set.weightKg * (1.0 + set.reps / 30.0) }
        } else {
            null
        }

        return AnalysisExerciseFeatures(
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            metadataConfidence = exercise.metadataConfidence,
            analysisEligibility = exercise.analysisEligibility.splitTokens(),
            movementPattern = exercise.movementPattern,
            movementCategory = exercise.movementCategory,
            primaryMuscles = exercise.primaryMuscles.splitTokens(),
            secondaryMuscles = exercise.secondaryMuscles.splitTokens(),
            equipment = exercise.equipment.ifBlank { exercise.equipmentTags }.splitTokens(),
            compoundType = exercise.compoundType,
            forceType = exercise.forceType,
            plane = exercise.plane,
            laterality = exercise.laterality,
            axialLoadLevel = exercise.axialLoadLevel,
            trainingRole = exercise.trainingRole,
            systemicLoadWeight = exercise.systemicLoadWeight,
            neuralHeavyWeight = exercise.neuralHeavyWeight,
            neuralSpeedWeight = exercise.neuralSpeedWeight,
            localLoadWeight = exercise.localLoadWeight,
            decelerationWeight = exercise.decelerationWeight,
            elasticSscWeight = exercise.elasticSscWeight,
            rotationPowerWeight = exercise.rotationPowerWeight,
            antiRotationWeight = exercise.antiRotationWeight,
            overheadSwingWeight = exercise.overheadSwingWeight,
            gripLoadWeight = exercise.gripLoadWeight,
            fatigueCategories = exercise.fatigueCategories.splitTokens(),
            adaptiveBaselineGroups = exercise.adaptiveBaselineGroups.splitTokens(),
            recoveryDecayProfile = exercise.recoveryDecayProfile,
            progressMetricType = exercise.progressMetricType,
            strengthProgressionGroup = exercise.strengthProgressionGroup,
            hypertrophyVolumeGroup = exercise.hypertrophyVolumeGroup,
            mainLiftGroup = exercise.mainLiftGroup,
            accessoryContributionGroup = exercise.accessoryContributionGroup,
            estimated1RmEligible = exercise.estimated1RmEligible,
            volumeLoadEligible = exercise.volumeLoadEligible,
            badmintonTransferRoles = exercise.badmintonTransferRoles.splitTokens(),
            badmintonTransferStrength = exercise.badmintonTransferStrength,
            courtMovementTypes = exercise.courtMovementTypes.splitTokens(),
            badmintonSkillTargets = exercise.badmintonSkillTargets.splitTokens(),
            jointStressTags = exercise.jointStressTags.splitTokens(),
            stabilityDemandLevel = exercise.stabilityDemandLevel,
            mobilityDemandLevel = exercise.mobilityDemandLevel,
            balanceContributionTags = exercise.balanceContributionTags.splitTokens(),
            totalVolumeLoad = totalVolumeLoad,
            totalReps = completedSets.takeIf { it.isNotEmpty() }?.sumOf { set -> set.reps },
            totalSets = sets.size,
            completedSets = completedSets.size,
            averageRpe = rpeValues.takeIf { it.isNotEmpty() }?.average(),
            maxRpe = rpeValues.maxOrNull(),
            estimated1Rm = estimated1Rm,
            durationMinutes = totalSeconds.takeIf { it > 0 }?.let { seconds -> seconds / 60.0 },
            isCompleted = completedSets.isNotEmpty(),
            isPlannedOnly = sets.isNotEmpty() && completedSets.isEmpty(),
            recordDate = entry?.date
        )
    }

    private fun String.splitTokens(): Set<String> =
        split(',', '|', '/', ';')
            .map { value -> value.trim() }
            .filter { value -> value.isNotEmpty() && value != "NONE" }
            .toSet()
}

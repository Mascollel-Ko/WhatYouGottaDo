package com.training.trackplanner.analysis.features

import com.training.trackplanner.data.Exercise
import com.training.trackplanner.data.RuntimeExerciseMetadata
import com.training.trackplanner.data.WorkoutEntry
import com.training.trackplanner.data.WorkoutSet

data class AnalysisExerciseFeatures(
    val exerciseId: Long,
    val exerciseName: String,
    val stableKey: String,
    val activityKind: String,
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
    val recordDate: String?,
    val movementFamily: String = "",
    val movementSubtype: String = "",
    val programSlot: String = "",
    val redundancyGroup: String = "",
    val primaryStressProfile: String = "",
    val secondaryStressTags: Set<String> = emptySet(),
    val tendonStressTags: Set<String> = emptySet(),
    val ligamentJointStabilityStressTags: Set<String> = emptySet(),
    val jointImpactStressTags: Set<String> = emptySet(),
    val cognitiveStressTags: Set<String> = emptySet(),
    val sportContextTags: Set<String> = emptySet(),
    val stressMagnitudeHint: String = "",
    val neuromuscularStressLevel: String = "",
    val systemicMuscularStressLevel: String = "",
    val localMuscularStressLevel: String = "",
    val jointTendonImpactStressLevel: String = "",
    val movementFocusDemandLevel: String = "",
    val recoveryDurationClass: String = "",
    val canonicalBadmintonTransferLevel: String = "",
    val canonicalBadmintonTransferTypes: Set<String> = emptySet(),
    val canonicalBadmintonSkillTargets: Set<String> = emptySet(),
    val badmintonPhysicalQualities: Set<String> = emptySet(),
    val transferConfidence: String = "",
    val sourceConfidenceLevel: String = "",
    val finalSourceStatus: String = ""
)

object ExerciseAnalysisMapper {
    fun fromExercise(
        exercise: Exercise,
        runtimeMetadata: RuntimeExerciseMetadata? = null
    ): AnalysisExerciseFeatures =
        fromRecord(
            exercise = exercise,
            entry = null,
            sets = emptyList(),
            runtimeMetadata = runtimeMetadata
        )

    fun fromRecord(
        exercise: Exercise,
        entry: WorkoutEntry?,
        sets: List<WorkoutSet>,
        runtimeMetadata: RuntimeExerciseMetadata? = null
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
            stableKey = exercise.stableKey,
            activityKind = runtimeMetadata?.activityKind
                ?.takeIf { it.isNotBlank() }
                ?: exercise.activityKind,
            metadataConfidence = exercise.metadataConfidence,
            analysisEligibility = runtimeMetadata
                ?.analysisEligibility
                ?.values
                ?.toSet()
                ?: exercise.analysisEligibility.splitTokens(),
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
            fatigueCategories = exercise.fatigueCategories.splitTokens() +
                runtimeMetadata?.broadLegacyFatigueCategories().orEmpty(),
            adaptiveBaselineGroups = exercise.adaptiveBaselineGroups.splitTokens(),
            recoveryDecayProfile = runtimeMetadata?.recoveryDecayProfile
                ?.takeIf { it.isNotBlank() }
                ?: exercise.recoveryDecayProfile,
            progressMetricType = runtimeMetadata?.progressMetricType
                ?.takeIf { it.isNotBlank() }
                ?: exercise.progressMetricType,
            strengthProgressionGroup = runtimeMetadata?.strengthProgressionGroup
                ?.takeIf { it.isNotBlank() }
                ?: exercise.strengthProgressionGroup,
            hypertrophyVolumeGroup = exercise.hypertrophyVolumeGroup,
            mainLiftGroup = exercise.mainLiftGroup,
            accessoryContributionGroup = exercise.accessoryContributionGroup,
            estimated1RmEligible = runtimeMetadata
                ?.takeIf { it.progressMetricType.isNotBlank() }
                ?.progressBehavior
                ?.let { it == com.training.trackplanner.data.ProgressMetricRuntimeBehavior.ESTIMATED_1RM }
                ?: exercise.estimated1RmEligible,
            volumeLoadEligible = runtimeMetadata
                ?.takeIf { it.progressMetricType.isNotBlank() }
                ?.progressBehavior
                ?.let { behavior ->
                    behavior in setOf(
                        com.training.trackplanner.data.ProgressMetricRuntimeBehavior.ESTIMATED_1RM,
                        com.training.trackplanner.data.ProgressMetricRuntimeBehavior.LOAD_REPS,
                        com.training.trackplanner.data.ProgressMetricRuntimeBehavior.VOLUME_LOAD
                    )
                }
                ?: exercise.volumeLoadEligible,
            badmintonTransferRoles = exercise.badmintonTransferRoles.splitTokens(),
            badmintonTransferStrength = runtimeMetadata?.badmintonTransferLevel
                ?.takeIf { it.isNotBlank() }
                ?: exercise.badmintonTransferStrength,
            courtMovementTypes = exercise.courtMovementTypes.splitTokens(),
            badmintonSkillTargets = runtimeMetadata
                ?.badmintonSkillTargets
                ?.values
                ?.toSet()
                ?: exercise.badmintonSkillTargets.splitTokens(),
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
            recordDate = entry?.date,
            movementFamily = runtimeMetadata?.movementFamily.orEmpty(),
            movementSubtype = runtimeMetadata?.movementSubtype.orEmpty(),
            programSlot = runtimeMetadata?.programSlot.orEmpty(),
            redundancyGroup = runtimeMetadata?.redundancyGroup.orEmpty(),
            primaryStressProfile = runtimeMetadata?.primaryStressProfile.orEmpty(),
            secondaryStressTags = runtimeMetadata?.secondaryStressTags?.values?.toSet().orEmpty(),
            tendonStressTags = runtimeMetadata?.tendonStressTags?.values?.toSet().orEmpty(),
            ligamentJointStabilityStressTags = runtimeMetadata
                ?.ligamentJointStabilityStressTags
                ?.values
                ?.toSet()
                .orEmpty(),
            jointImpactStressTags = runtimeMetadata?.jointImpactStressTags?.values?.toSet().orEmpty(),
            cognitiveStressTags = runtimeMetadata?.cognitiveStressTags?.values?.toSet().orEmpty(),
            sportContextTags = runtimeMetadata?.sportContextTags?.values?.toSet().orEmpty(),
            stressMagnitudeHint = runtimeMetadata?.stressMagnitudeHint.orEmpty(),
            neuromuscularStressLevel = runtimeMetadata?.neuromuscularStressLevel.orEmpty(),
            systemicMuscularStressLevel = runtimeMetadata?.systemicMuscularStressLevel.orEmpty(),
            localMuscularStressLevel = runtimeMetadata?.localMuscularStressLevel.orEmpty(),
            jointTendonImpactStressLevel = runtimeMetadata?.jointTendonImpactStressLevel.orEmpty(),
            movementFocusDemandLevel = runtimeMetadata?.movementFocusDemandLevel.orEmpty(),
            recoveryDurationClass = runtimeMetadata?.recoveryDurationClass.orEmpty(),
            canonicalBadmintonTransferLevel = runtimeMetadata?.badmintonTransferLevel.orEmpty(),
            canonicalBadmintonTransferTypes = runtimeMetadata?.badmintonTransferType?.values?.toSet().orEmpty(),
            canonicalBadmintonSkillTargets = runtimeMetadata?.badmintonSkillTargets?.values?.toSet().orEmpty(),
            badmintonPhysicalQualities = runtimeMetadata?.badmintonPhysicalQualities?.values?.toSet().orEmpty(),
            transferConfidence = runtimeMetadata?.transferConfidence.orEmpty(),
            sourceConfidenceLevel = runtimeMetadata?.sourceConfidenceLevel.orEmpty(),
            finalSourceStatus = runtimeMetadata?.finalSourceStatus.orEmpty()
        )
    }

    private fun String.splitTokens(): Set<String> =
        split(',', '|', '/', ';')
            .map { value -> value.trim() }
            .filter { value -> value.isNotEmpty() && value != "NONE" }
            .toSet()
}

package com.training.trackplanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "runtime_exercise_metadata")
data class RuntimeExerciseMetadataEntity(
    @PrimaryKey val stableKey: String,
    val exerciseName: String,
    val activityKind: String,
    val planningEligibility: String,
    val movementFamily: String,
    val movementSubtype: String,
    val programSlot: String,
    val redundancyGroup: String,
    val progressMetricType: String,
    val strengthProgressionGroup: String,
    val analysisEligibility: MetadataTokenField,
    val primaryStressProfile: String,
    val secondaryStressTags: MetadataTokenField,
    val tendonStressTags: MetadataTokenField,
    val ligamentJointStabilityStressTags: MetadataTokenField,
    val jointImpactStressTags: MetadataTokenField,
    val cognitiveStressTags: MetadataTokenField,
    val sportContextTags: MetadataTokenField,
    val recoveryDecayProfile: String,
    val stressMagnitudeHint: String,
    val badmintonTransferLevel: String,
    val badmintonTransferType: MetadataTokenField,
    val badmintonSkillTargets: MetadataTokenField,
    val badmintonPhysicalQualities: MetadataTokenField,
    val transferConfidence: String,
    val sourceConfidenceLevel: String,
    val finalSourceStatus: String,
    val neuromuscularStressLevel: String,
    val systemicMuscularStressLevel: String,
    val localMuscularStressLevel: String,
    val jointTendonImpactStressLevel: String,
    val movementFocusDemandLevel: String,
    val recoveryDurationClass: String,
    val safeForSeedMutation: Boolean
)

class RuntimeMetadataTypeConverters {
    @TypeConverter
    fun tokenFieldToString(field: MetadataTokenField): String =
        field.raw + RAW_VALUES_SEPARATOR + field.values.joinToString(VALUE_SEPARATOR)

    @TypeConverter
    fun stringToTokenField(stored: String): MetadataTokenField {
        val separatorIndex = stored.indexOf(RAW_VALUES_SEPARATOR)
        if (separatorIndex < 0) return MetadataTokenField.parse(stored)
        val raw = stored.substring(0, separatorIndex)
        val encodedValues = stored.substring(separatorIndex + RAW_VALUES_SEPARATOR.length)
        return MetadataTokenField(
            raw = raw,
            values = encodedValues.split(VALUE_SEPARATOR).filter(String::isNotEmpty)
        )
    }

    private companion object {
        const val RAW_VALUES_SEPARATOR = "\u001E"
        const val VALUE_SEPARATOR = "\u001F"
    }
}

fun RuntimeExerciseMetadata.toEntity(): RuntimeExerciseMetadataEntity =
    RuntimeExerciseMetadataEntity(
        stableKey = stableKey,
        exerciseName = exerciseName,
        activityKind = activityKind,
        planningEligibility = planningEligibility,
        movementFamily = movementFamily,
        movementSubtype = movementSubtype,
        programSlot = programSlot,
        redundancyGroup = redundancyGroup,
        progressMetricType = progressMetricType,
        strengthProgressionGroup = strengthProgressionGroup,
        analysisEligibility = analysisEligibility,
        primaryStressProfile = primaryStressProfile,
        secondaryStressTags = secondaryStressTags,
        tendonStressTags = tendonStressTags,
        ligamentJointStabilityStressTags = ligamentJointStabilityStressTags,
        jointImpactStressTags = jointImpactStressTags,
        cognitiveStressTags = cognitiveStressTags,
        sportContextTags = sportContextTags,
        recoveryDecayProfile = recoveryDecayProfile,
        stressMagnitudeHint = stressMagnitudeHint,
        badmintonTransferLevel = badmintonTransferLevel,
        badmintonTransferType = badmintonTransferType,
        badmintonSkillTargets = badmintonSkillTargets,
        badmintonPhysicalQualities = badmintonPhysicalQualities,
        transferConfidence = transferConfidence,
        sourceConfidenceLevel = sourceConfidenceLevel,
        finalSourceStatus = finalSourceStatus,
        neuromuscularStressLevel = neuromuscularStressLevel,
        systemicMuscularStressLevel = systemicMuscularStressLevel,
        localMuscularStressLevel = localMuscularStressLevel,
        jointTendonImpactStressLevel = jointTendonImpactStressLevel,
        movementFocusDemandLevel = movementFocusDemandLevel,
        recoveryDurationClass = recoveryDurationClass,
        safeForSeedMutation = safeForSeedMutation
    )

fun RuntimeExerciseMetadataEntity.toRuntimeMetadata(): RuntimeExerciseMetadata =
    RuntimeExerciseMetadata(
        stableKey = stableKey,
        exerciseName = exerciseName,
        activityKind = activityKind,
        planningEligibility = planningEligibility,
        movementFamily = movementFamily,
        movementSubtype = movementSubtype,
        programSlot = programSlot,
        redundancyGroup = redundancyGroup,
        progressMetricType = progressMetricType,
        strengthProgressionGroup = strengthProgressionGroup,
        analysisEligibility = analysisEligibility,
        primaryStressProfile = primaryStressProfile,
        secondaryStressTags = secondaryStressTags,
        tendonStressTags = tendonStressTags,
        ligamentJointStabilityStressTags = ligamentJointStabilityStressTags,
        jointImpactStressTags = jointImpactStressTags,
        cognitiveStressTags = cognitiveStressTags,
        sportContextTags = sportContextTags,
        recoveryDecayProfile = recoveryDecayProfile,
        stressMagnitudeHint = stressMagnitudeHint,
        badmintonTransferLevel = badmintonTransferLevel,
        badmintonTransferType = badmintonTransferType,
        badmintonSkillTargets = badmintonSkillTargets,
        badmintonPhysicalQualities = badmintonPhysicalQualities,
        transferConfidence = transferConfidence,
        sourceConfidenceLevel = sourceConfidenceLevel,
        finalSourceStatus = finalSourceStatus,
        neuromuscularStressLevel = neuromuscularStressLevel,
        systemicMuscularStressLevel = systemicMuscularStressLevel,
        localMuscularStressLevel = localMuscularStressLevel,
        jointTendonImpactStressLevel = jointTendonImpactStressLevel,
        movementFocusDemandLevel = movementFocusDemandLevel,
        recoveryDurationClass = recoveryDurationClass,
        safeForSeedMutation = safeForSeedMutation
    )

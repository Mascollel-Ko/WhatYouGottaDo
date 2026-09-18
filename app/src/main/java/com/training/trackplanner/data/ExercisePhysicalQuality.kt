package com.training.trackplanner.data

import java.util.Locale

/**
 * Small, canonical vocabulary for an exercise's trainable stimulus capability.
 *
 * A capability is not a realized training stimulus. Realized stimulus remains
 * prescription-dependent (load, reps, RPE, duration, and execution intent).
 */
enum class TrainableQuality {
    STRENGTH,
    HYPERTROPHY,
    POWER,
    RAPID_FORCE_PRODUCTION,
    REACTIVE_STRENGTH_SSC,
    MUSCULAR_ENDURANCE,
    CARDIORESPIRATORY_FITNESS,
    MOBILITY_ROM
}

enum class StimulusCapabilityLevel {
    DIRECT_CAPABILITY,
    SUPPORTIVE_CAPABILITY
}

/** Atomic region qualifiers. Compound movement/context labels are not allowed. */
enum class PhysicalQualityRegion {
    SYSTEMIC,
    LOWER,
    POSTERIOR_CHAIN,
    QUADS_GLUTE,
    HAMSTRING,
    ANKLE,
    UPPER_PUSH,
    UPPER_PULL,
    ROTATIONAL,
    FOREARM_GRIP,
    JOINT_ROM,
    UNILATERAL_LOWER,
    CHEST,
    SHOULDERS,
    ARMS,
    OTHER
}

/** Atomic prescription/mode qualifiers. Mechanical context remains elsewhere. */
enum class PhysicalQualityMode {
    GENERAL,
    BILATERAL,
    UNILATERAL,
    HINGE,
    SQUAT,
    HORIZONTAL_PRESS,
    VERTICAL_PRESS,
    PULL,
    ECCENTRIC,
    ISOMETRIC,
    BALLISTIC,
    PLYOMETRIC,
    SSC,
    LANDING,
    ROTATIONAL,
    WRIST_FLEXION,
    WRIST_EXTENSION,
    PRONATION_SUPINATION,
    CONDITIONING,
    MOBILITY_CONTROL,
    OTHER
}

data class ExercisePhysicalQualityRelation(
    val relationId: String,
    val exerciseStableKey: String,
    val qualityId: TrainableQuality,
    val relationLevel: StimulusCapabilityLevel,
    val regionQualifier: PhysicalQualityRegion,
    val modeQualifier: PhysicalQualityMode,
    val prescriptionDependent: Boolean,
    val provenance: String,
    val evidenceRelationKeys: Set<String>,
    val reviewStatus: String,
    val notes: String
)

/**
 * Read-only catalog for the physical-quality relation layer.
 *
 * This catalog is intentionally not consumed by planner or Needs Engine code
 * in this phase. It exposes assessment exclusions so a future consumer cannot
 * accidentally treat an analysis-only test as a training stimulus provider.
 */
class CanonicalExercisePhysicalQualityCatalog private constructor(
    relations: Collection<ExercisePhysicalQualityRelation>,
    private val assessmentOnlyKeys: Set<String>
) {
    private val all = relations.sortedWith(
        compareBy<ExercisePhysicalQualityRelation> { it.exerciseStableKey }
            .thenBy { it.qualityId.name }
            .thenBy { it.relationId }
    )
    private val byExercise = all.groupBy { it.exerciseStableKey }

    fun allRelations(): List<ExercisePhysicalQualityRelation> = all

    fun relations(stableKey: String): List<ExercisePhysicalQualityRelation> =
        byExercise[stableKey.normalizedPhysicalQualityKey()].orEmpty()

    fun relations(quality: TrainableQuality): List<ExercisePhysicalQualityRelation> =
        all.filter { it.qualityId == quality }

    fun hasGeneralQualityRelation(stableKey: String): Boolean =
        relations(stableKey).isNotEmpty()

    fun assessmentOnlyStableKeys(): Set<String> = assessmentOnlyKeys

    fun isAssessmentOnly(stableKey: String): Boolean =
        stableKey.normalizedPhysicalQualityKey() in assessmentOnlyKeys

    /** Relations safe for a future training-candidate consumer. */
    fun trainingRelations(): List<ExercisePhysicalQualityRelation> =
        all.filterNot { it.exerciseStableKey in assessmentOnlyKeys }

    companion object {
        val EMPTY = CanonicalExercisePhysicalQualityCatalog(emptyList(), emptySet())

        fun of(
            relations: Collection<ExercisePhysicalQualityRelation>,
            assessmentOnlyStableKeys: Collection<String> = emptySet()
        ): CanonicalExercisePhysicalQualityCatalog {
            val normalized = relations.map { relation ->
                relation.copy(exerciseStableKey = relation.exerciseStableKey.normalizedPhysicalQualityKey())
            }
            require(normalized.map(ExercisePhysicalQualityRelation::relationId).distinct().size == normalized.size) {
                "Duplicate physical-quality relation id."
            }
            require(normalized.map {
                listOf(it.exerciseStableKey, it.qualityId.name, it.regionQualifier.name, it.modeQualifier.name)
            }.distinct().size == normalized.size) {
                "Duplicate physical-quality exercise/quality/qualifier relation."
            }
            return CanonicalExercisePhysicalQualityCatalog(
                relations = normalized,
                assessmentOnlyKeys = assessmentOnlyStableKeys.mapTo(mutableSetOf()) {
                    it.normalizedPhysicalQualityKey()
                }
            )
        }
    }
}

private fun String.normalizedPhysicalQualityKey(): String = trim().lowercase(Locale.ROOT)

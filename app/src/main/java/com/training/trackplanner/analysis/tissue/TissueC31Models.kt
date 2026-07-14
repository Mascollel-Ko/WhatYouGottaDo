package com.training.trackplanner.analysis.tissue

enum class TissueC31MechanicalLoadMode {
    COMPRESSION, TENSION, ANTERIOR_POSTERIOR_SHEAR, MEDIAL_LATERAL_SHEAR, TORSION,
    ROTATIONAL_STRESS, INTERNAL_ROTATION_STRESS, EXTERNAL_ROTATION_STRESS, VALGUS_STRESS,
    VARUS_STRESS, ANTERIOR_TRANSLATION_STRESS, POSTERIOR_TRANSLATION_STRESS,
    INVERSION_STRESS, EVERSION_STRESS
}

enum class TissueEventContext { STRENGTH_REPETITION, ISOMETRIC_HOLD, JUMP_TAKEOFF, JUMP_LANDING, HOP_TAKEOFF, HOP_LANDING, DROP_LANDING, RUNNING_GAIT, SQUAT, LUNGE, PASSIVE_INVERSION_TEST }
enum class TissueMovementPhase { FULL_EVENT, PRE_CONTACT, WEIGHT_ACCEPTANCE, CONCENTRIC, ECCENTRIC, ISOMETRIC }
enum class TissuePositionContext { PARTIAL_ROM, FULL_ROM, END_RANGE, SPECIFIED_JOINT_ANGLE, UNKNOWN_ROM }
enum class TissueFunctionalDemand { STABILIZATION_DEMAND, IMPACT_ATTENUATION, DECELERATION_DEMAND, ENERGY_STORAGE_DEMAND, ENERGY_RELEASE_DEMAND }
enum class TissueResponseMetric { ELASTIC_ENERGY_STORAGE, ELASTIC_ENERGY_RETURN, STRAIN_ENERGY, HYSTERESIS }
enum class TissueEvidenceRelation { DIRECT_INTERNAL_MEASUREMENT, VALIDATED_INTERNAL_MODEL, VALIDATED_PROXY, UNVALIDATED_PROXY, CONTEXT_ONLY }
enum class TissueRubricKind { CONDITION_ANCHOR, INTERVAL_BAND, ORDERING_RULE }
enum class TissueExternalLoadRepresentation { ADDITIONAL_EXTERNAL_LOAD, TOTAL_SYSTEM_MASS, RELATIVE_LOAD_PERCENT_1RM, BODYWEIGHT_TASK, NO_EXTERNAL_LOAD, NOT_REPORTED }

data class TissueC31RegistryEntry(val id: String, val definition: String, val scientificBoundary: String)

data class TissueC31MechanicalLoadModeDefinition(
    val mechanicalLoadMode: TissueC31MechanicalLoadMode,
    val tissueClasses: Set<TissueClass>,
    val biomechanicalMeaning: String,
    val clinicalInterpretationBoundary: String
)

data class TissueC31MeasurementMetricDefinition(
    val measurementMetric: TissueMeasurementMetric,
    val compatibleMechanicalLoadModes: Set<TissueC31MechanicalLoadMode>,
    val compatibleTemporalMetrics: Set<TissueTemporalMetric>,
    val allowedEvidenceRelations: Set<TissueEvidenceRelation>
)

data class TissueC31LoadDimensionDefinition(
    val dimensionId: String,
    val c3DimensionId: String,
    val tissueClass: TissueClass,
    val tissueId: String,
    val mechanicalLoadMode: TissueC31MechanicalLoadMode,
    val temporalMetric: TissueTemporalMetric,
    val allowedMeasurementMetrics: Set<TissueMeasurementMetric>,
    val allowedNormalizationBases: Set<TissueNormalizationBasis>,
    val allowedEvidenceRelations: Set<TissueEvidenceRelation>,
    val allowedTissueResponseMetrics: Set<TissueResponseMetric>,
    val rubricEligible: Boolean,
    val profileEligible: Boolean,
    val correctionStatus: String
)

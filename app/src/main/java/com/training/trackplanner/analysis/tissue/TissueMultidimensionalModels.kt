package com.training.trackplanner.analysis.tissue

enum class TissueMechanicalLoadMode {
    COMPRESSION,
    TENSION,
    ANTERIOR_POSTERIOR_SHEAR,
    MEDIAL_LATERAL_SHEAR,
    TORSION,
    ROTATIONAL_STRESS,
    INTERNAL_ROTATION_STRESS,
    EXTERNAL_ROTATION_STRESS,
    VALGUS_STRESS,
    VARUS_STRESS,
    ANTERIOR_TRANSLATION_STRESS,
    POSTERIOR_TRANSLATION_STRESS,
    INVERSION_STRESS,
    EVERSION_STRESS,
    ENERGY_STORAGE_RELEASE,
    IMPACT_STABILIZATION,
    END_RANGE_STRESS
}

enum class TissueTemporalMetric {
    PEAK,
    IMPULSE_PER_EVENT,
    LOADING_RATE,
    TIME_ABOVE_THRESHOLD,
    CYCLIC_EVENT_COUNT,
    CYCLIC_EXPOSURE,
    CUMULATIVE_SESSION_IMPULSE,
    CUMULATIVE_WEEKLY_EXPOSURE,
    ECCENTRIC_PHASE_PEAK,
    ECCENTRIC_PHASE_IMPULSE,
    ISOMETRIC_HOLD_EXPOSURE
}

enum class TissueMeasurementMetric {
    MODELED_TENDON_FORCE,
    MEASURED_TENDON_STRAIN,
    MODELED_JOINT_CONTACT_FORCE,
    MODELED_LIGAMENT_FORCE,
    MEASURED_LIGAMENT_STRAIN,
    EXTERNAL_JOINT_MOMENT,
    GROUND_REACTION_FORCE,
    JOINT_CONTACT_FORCE_TIME_INTEGRAL,
    TENDON_FORCE_TIME_INTEGRAL,
    MODELED_TENDON_FORCE_LOADING_RATE,
    MODELED_JOINT_CONTACT_FORCE_LOADING_RATE,
    GROUND_REACTION_FORCE_LOADING_RATE,
    MEASURED_TENDON_ENERGY_STORAGE,
    MODELED_TENDON_ENERGY_STORAGE,
    EVENT_COUNT,
    SOURCE_DEFINED_CYCLIC_EXPOSURE,
    SOURCE_DEFINED_COMPOSITE_INDEX
}

enum class TissueNormalizationBasis {
    ABSOLUTE_FORCE_NEWTON,
    BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE,
    BODY_MASS_NORMALIZED_INTERNAL_FORCE,
    BODY_WEIGHT_NORMALIZED_IMPULSE,
    BODY_WEIGHT_TIME_NORMALIZED_INTERNAL_IMPULSE,
    BODY_WEIGHT_NORMALIZED_LOADING_RATE,
    BODY_MASS_NORMALIZED_JOINT_MOMENT,
    EXTERNAL_LOAD_KG,
    EXTERNAL_LOAD_AS_BODYWEIGHT_FRACTION,
    RELATIVE_LOAD_PERCENT_1RM,
    MEASURED_TENDON_STRAIN_PERCENT,
    SOURCE_DEFINED_NORMALIZED_INDEX,
    UNNORMALIZED_SOURCE_VALUE
}

enum class TissueMetricOrigin {
    SOURCE_OBSERVED,
    APPLICATION_DERIVED,
    SOURCE_SPECIFIC
}

enum class TissueLegacyDimensionMigrationDecision {
    EXACT_MIGRATION,
    SPLIT_INTO_MULTIPLE_DIMENSIONS,
    SOURCE_SPECIFIC_ONLY,
    DEPRECATED_AMBIGUOUS,
    BLOCKED_INSUFFICIENT_INFORMATION
}

data class TissueMechanicalLoadModeDefinition(
    val mechanicalLoadMode: TissueMechanicalLoadMode,
    val tissueClasses: Set<TissueClass>,
    val biomechanicalMeaning: String,
    val clinicalInterpretationBoundary: String
)

data class TissueTemporalMetricDefinition(
    val temporalMetric: TissueTemporalMetric,
    val metricOrigin: TissueMetricOrigin,
    val biomechanicalMeaning: String,
    val aggregationBoundary: String
)

data class TissueMeasurementMetricDefinition(
    val measurementMetric: TissueMeasurementMetric,
    val measurementFamily: String,
    val metricOrigin: TissueMetricOrigin,
    val compatibleMechanicalLoadModes: Set<TissueMechanicalLoadMode>,
    val compatibleTemporalMetrics: Set<TissueTemporalMetric>,
    val requiredModelAssumptions: String
)

data class TissueNormalizationDefinition(
    val normalizationBasis: TissueNormalizationBasis,
    val unitFamily: String,
    val biomechanicalMeaning: String,
    val compatibleMeasurementMetrics: Set<TissueMeasurementMetric>
)

data class TissueLoadDimensionDefinition(
    val dimensionId: String,
    val tissueClass: TissueClass,
    val tissueId: String,
    val mechanicalLoadMode: TissueMechanicalLoadMode,
    val temporalMetric: TissueTemporalMetric,
    val allowedMeasurementMetrics: Set<TissueMeasurementMetric>,
    val allowedNormalizationBases: Set<TissueNormalizationBasis>,
    val metricOrigin: TissueMetricOrigin,
    val derivedFormulaId: String,
    val biomechanicalMeaning: String,
    val clinicalInterpretationBoundary: String,
    val minimumEvidenceLevel: String,
    val rubricEligible: Boolean,
    val profileEligible: Boolean,
    val deprecatedLegacyDimensions: Set<TissueLoadDimension>,
    val migrationNotes: String
)

data class TissueLegacyDimensionMigration(
    val migrationId: String,
    val legacyDimension: TissueLoadDimension,
    val targetMechanicalLoadMode: TissueMechanicalLoadMode?,
    val targetTemporalMetric: TissueTemporalMetric?,
    val targetMeasurementMetric: TissueMeasurementMetric?,
    val migrationDecision: TissueLegacyDimensionMigrationDecision,
    val affectedClaimIds: Set<String>,
    val affectedRubricIds: Set<String>,
    val ambiguityReason: String,
    val requiredManualReview: Boolean,
    val migrationNotes: String
)

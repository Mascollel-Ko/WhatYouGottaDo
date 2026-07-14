param([string]$AssetDirectory = "app/src/main/assets/metadata/tissue_load_v1")

$ErrorActionPreference = "Stop"
$preparedAt = "2026-07-14T01:00:01Z"
function Row($values) { [pscustomobject]$values }
function Export-Rows([string]$name, $rows) {
    @($rows) | Export-Csv -LiteralPath (Join-Path $AssetDirectory $name) -NoTypeInformation -Encoding utf8
}

$modes = @(
    Row ([ordered]@{ mechanicalLoadMode='COMPRESSION'; tissueClasses='JOINT|TENDON|FASCIA'; biomechanicalMeaning='Force pressing tissue surfaces together'; clinicalInterpretationBoundary='Does not encode peak, rate, impulse, or cumulative dose' })
    Row ([ordered]@{ mechanicalLoadMode='TENSION'; tissueClasses='TENDON|FASCIA'; biomechanicalMeaning='Longitudinal tensile loading of collagenous tissue'; clinicalInterpretationBoundary='Force and strain remain distinct measurement families' })
    Row ([ordered]@{ mechanicalLoadMode='ANTERIOR_POSTERIOR_SHEAR'; tissueClasses='JOINT'; biomechanicalMeaning='Shear directed along the anterior-posterior axis'; clinicalInterpretationBoundary='External moment is not internal shear without a validated mapping' })
    Row ([ordered]@{ mechanicalLoadMode='MEDIAL_LATERAL_SHEAR'; tissueClasses='JOINT'; biomechanicalMeaning='Shear directed along the medial-lateral axis'; clinicalInterpretationBoundary='Direction and coordinate system must be reported' })
    Row ([ordered]@{ mechanicalLoadMode='TORSION'; tissueClasses='JOINT|LIGAMENT'; biomechanicalMeaning='Twisting load about a tissue or joint axis'; clinicalInterpretationBoundary='Not interchangeable with a directional rotation stress' })
    Row ([ordered]@{ mechanicalLoadMode='ROTATIONAL_STRESS'; tissueClasses='JOINT|LIGAMENT'; biomechanicalMeaning='Rotation-related mechanical stress when direction is unresolved'; clinicalInterpretationBoundary='Prefer internal or external rotation when the source resolves direction' })
    Row ([ordered]@{ mechanicalLoadMode='INTERNAL_ROTATION_STRESS'; tissueClasses='LIGAMENT'; biomechanicalMeaning='Stress associated with internal rotation'; clinicalInterpretationBoundary='Requires tissue-specific force, strain, or validated proxy' })
    Row ([ordered]@{ mechanicalLoadMode='EXTERNAL_ROTATION_STRESS'; tissueClasses='LIGAMENT'; biomechanicalMeaning='Stress associated with external rotation'; clinicalInterpretationBoundary='Requires tissue-specific force, strain, or validated proxy' })
    Row ([ordered]@{ mechanicalLoadMode='VALGUS_STRESS'; tissueClasses='LIGAMENT'; biomechanicalMeaning='Stress associated with valgus loading'; clinicalInterpretationBoundary='Joint moment alone is not ligament strain' })
    Row ([ordered]@{ mechanicalLoadMode='VARUS_STRESS'; tissueClasses='LIGAMENT'; biomechanicalMeaning='Stress associated with varus loading'; clinicalInterpretationBoundary='Joint moment alone is not ligament strain' })
    Row ([ordered]@{ mechanicalLoadMode='ANTERIOR_TRANSLATION_STRESS'; tissueClasses='LIGAMENT'; biomechanicalMeaning='Stress associated with anterior translation'; clinicalInterpretationBoundary='Requires a tissue-specific or validated translation proxy' })
    Row ([ordered]@{ mechanicalLoadMode='POSTERIOR_TRANSLATION_STRESS'; tissueClasses='LIGAMENT'; biomechanicalMeaning='Stress associated with posterior translation'; clinicalInterpretationBoundary='Requires a tissue-specific or validated translation proxy' })
    Row ([ordered]@{ mechanicalLoadMode='INVERSION_STRESS'; tissueClasses='LIGAMENT'; biomechanicalMeaning='Stress associated with inversion'; clinicalInterpretationBoundary='Do not generalize across talocrural, subtalar, and ligament structures' })
    Row ([ordered]@{ mechanicalLoadMode='EVERSION_STRESS'; tissueClasses='LIGAMENT'; biomechanicalMeaning='Stress associated with eversion'; clinicalInterpretationBoundary='Do not generalize across talocrural, subtalar, and ligament structures' })
    Row ([ordered]@{ mechanicalLoadMode='ENERGY_STORAGE_RELEASE'; tissueClasses='TENDON|FASCIA'; biomechanicalMeaning='Elastic energy stored and returned by tissue'; clinicalInterpretationBoundary='Must be measured or modeled directly, not inferred from peak force alone' })
    Row ([ordered]@{ mechanicalLoadMode='IMPACT_STABILIZATION'; tissueClasses='JOINT|LIGAMENT'; biomechanicalMeaning='Mechanical stabilization demand during impact'; clinicalInterpretationBoundary='External impact is a proxy unless an internal mapping is validated' })
    Row ([ordered]@{ mechanicalLoadMode='END_RANGE_STRESS'; tissueClasses='JOINT|TENDON|LIGAMENT|FASCIA'; biomechanicalMeaning='Mechanical stress near a defined end range'; clinicalInterpretationBoundary='Range, direction, and applied load must be explicit' })
)
$modes | ForEach-Object { $_ | Add-Member createdBy 'Codex'; $_ | Add-Member createdByType 'AI_AGENT'; $_ | Add-Member createdAt $preparedAt }
Export-Rows 'tissue_mechanical_load_mode_registry_v1.csv' $modes

$sourceObserved = 'SOURCE_OBSERVED'
$derived = 'APPLICATION_DERIVED'
$temporal = @(
    Row ([ordered]@{ temporalMetric='PEAK'; metricOrigin=$sourceObserved; biomechanicalMeaning='Maximum value within one defined event or phase'; aggregationBoundary='No session dose implied' })
    Row ([ordered]@{ temporalMetric='IMPULSE_PER_EVENT'; metricOrigin=$sourceObserved; biomechanicalMeaning='Force-time integral for one defined event'; aggregationBoundary='Event boundaries must match the source protocol' })
    Row ([ordered]@{ temporalMetric='LOADING_RATE'; metricOrigin=$sourceObserved; biomechanicalMeaning='Rate of load increase under the source definition'; aggregationBoundary='Not interchangeable with peak or impulse' })
    Row ([ordered]@{ temporalMetric='TIME_ABOVE_THRESHOLD'; metricOrigin=$sourceObserved; biomechanicalMeaning='Time spent above a declared threshold'; aggregationBoundary='Threshold and event window must be explicit' })
    Row ([ordered]@{ temporalMetric='CYCLIC_EVENT_COUNT'; metricOrigin=$sourceObserved; biomechanicalMeaning='Count of defined mechanical events'; aggregationBoundary='Count alone is not tissue force or impulse' })
    Row ([ordered]@{ temporalMetric='CYCLIC_EXPOSURE'; metricOrigin=$sourceObserved; biomechanicalMeaning='Source-defined repeated-event exposure'; aggregationBoundary='Protocol, cadence, and event definition must be preserved' })
    Row ([ordered]@{ temporalMetric='CUMULATIVE_SESSION_IMPULSE'; metricOrigin=$derived; biomechanicalMeaning='Per-event impulse aggregated across a valid session event count'; aggregationBoundary='Requires formula, valid count, laterality, and no double counting' })
    Row ([ordered]@{ temporalMetric='CUMULATIVE_WEEKLY_EXPOSURE'; metricOrigin=$derived; biomechanicalMeaning='Compatible session exposure aggregated across a week'; aggregationBoundary='Requires compatible session metrics and explicit missing-input policy' })
    Row ([ordered]@{ temporalMetric='ECCENTRIC_PHASE_PEAK'; metricOrigin=$sourceObserved; biomechanicalMeaning='Peak restricted to a defined eccentric phase'; aggregationBoundary='A lowering phase alone does not establish this metric' })
    Row ([ordered]@{ temporalMetric='ECCENTRIC_PHASE_IMPULSE'; metricOrigin=$sourceObserved; biomechanicalMeaning='Force-time integral restricted to a defined eccentric phase'; aggregationBoundary='Phase segmentation must be source defined' })
    Row ([ordered]@{ temporalMetric='ISOMETRIC_HOLD_EXPOSURE'; metricOrigin=$sourceObserved; biomechanicalMeaning='Load exposure during a defined isometric hold'; aggregationBoundary='Duration and load must both be known' })
)
$temporal | ForEach-Object { $_ | Add-Member createdBy 'Codex'; $_ | Add-Member createdByType 'AI_AGENT'; $_ | Add-Member createdAt $preparedAt }
Export-Rows 'tissue_temporal_metric_registry_v1.csv' $temporal

$allDirectional = 'ANTERIOR_POSTERIOR_SHEAR|MEDIAL_LATERAL_SHEAR|TORSION|ROTATIONAL_STRESS|INTERNAL_ROTATION_STRESS|EXTERNAL_ROTATION_STRESS|VALGUS_STRESS|VARUS_STRESS|ANTERIOR_TRANSLATION_STRESS|POSTERIOR_TRANSLATION_STRESS|INVERSION_STRESS|EVERSION_STRESS|IMPACT_STABILIZATION|END_RANGE_STRESS'
$measurements = @(
    Row ([ordered]@{ measurementMetric='MODELED_TENDON_FORCE'; measurementFamily='INTERNAL_TENDON_FORCE'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='TENSION|COMPRESSION'; compatibleTemporalMetrics='PEAK|ECCENTRIC_PHASE_PEAK|ISOMETRIC_HOLD_EXPOSURE'; requiredModelAssumptions='Tendon-force model and moment-arm assumptions required' })
    Row ([ordered]@{ measurementMetric='MEASURED_TENDON_STRAIN'; measurementFamily='TENDON_STRAIN'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='TENSION'; compatibleTemporalMetrics='PEAK|ECCENTRIC_PHASE_PEAK|ISOMETRIC_HOLD_EXPOSURE'; requiredModelAssumptions='Direct imaging or validated strain measurement required' })
    Row ([ordered]@{ measurementMetric='MODELED_JOINT_CONTACT_FORCE'; measurementFamily='INTERNAL_JOINT_FORCE'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='COMPRESSION|ANTERIOR_POSTERIOR_SHEAR|MEDIAL_LATERAL_SHEAR|TORSION|ROTATIONAL_STRESS'; compatibleTemporalMetrics='PEAK|ECCENTRIC_PHASE_PEAK'; requiredModelAssumptions='Joint-contact model assumptions required' })
    Row ([ordered]@{ measurementMetric='MODELED_LIGAMENT_FORCE'; measurementFamily='INTERNAL_LIGAMENT_FORCE'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes=$allDirectional; compatibleTemporalMetrics='PEAK|ECCENTRIC_PHASE_PEAK'; requiredModelAssumptions='Ligament-force model assumptions required' })
    Row ([ordered]@{ measurementMetric='MEASURED_LIGAMENT_STRAIN'; measurementFamily='LIGAMENT_STRAIN'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes=$allDirectional; compatibleTemporalMetrics='PEAK|ECCENTRIC_PHASE_PEAK'; requiredModelAssumptions='Direct or validated strain measurement required' })
    Row ([ordered]@{ measurementMetric='EXTERNAL_JOINT_MOMENT'; measurementFamily='EXTERNAL_KINETIC_PROXY'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes="COMPRESSION|$allDirectional"; compatibleTemporalMetrics='PEAK|IMPULSE_PER_EVENT|LOADING_RATE'; requiredModelAssumptions='Explicit approved proxy mapping required' })
    Row ([ordered]@{ measurementMetric='GROUND_REACTION_FORCE'; measurementFamily='EXTERNAL_FORCE_PROXY'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='COMPRESSION|IMPACT_STABILIZATION'; compatibleTemporalMetrics='PEAK|IMPULSE_PER_EVENT'; requiredModelAssumptions='Explicit approved proxy mapping required' })
    Row ([ordered]@{ measurementMetric='JOINT_CONTACT_FORCE_TIME_INTEGRAL'; measurementFamily='INTERNAL_JOINT_IMPULSE'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='COMPRESSION|ANTERIOR_POSTERIOR_SHEAR|ROTATIONAL_STRESS'; compatibleTemporalMetrics='IMPULSE_PER_EVENT|CUMULATIVE_SESSION_IMPULSE|CUMULATIVE_WEEKLY_EXPOSURE'; requiredModelAssumptions='Joint-contact force-time model required' })
    Row ([ordered]@{ measurementMetric='TENDON_FORCE_TIME_INTEGRAL'; measurementFamily='INTERNAL_TENDON_IMPULSE'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='TENSION|COMPRESSION'; compatibleTemporalMetrics='IMPULSE_PER_EVENT|ECCENTRIC_PHASE_IMPULSE|CUMULATIVE_SESSION_IMPULSE|CUMULATIVE_WEEKLY_EXPOSURE'; requiredModelAssumptions='Tendon force-time model required' })
    Row ([ordered]@{ measurementMetric='MODELED_TENDON_FORCE_LOADING_RATE'; measurementFamily='INTERNAL_TENDON_LOADING_RATE'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='TENSION|COMPRESSION'; compatibleTemporalMetrics='LOADING_RATE'; requiredModelAssumptions='Tendon force-time model and rate definition required' })
    Row ([ordered]@{ measurementMetric='MODELED_JOINT_CONTACT_FORCE_LOADING_RATE'; measurementFamily='INTERNAL_JOINT_LOADING_RATE'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='COMPRESSION|ANTERIOR_POSTERIOR_SHEAR|ROTATIONAL_STRESS'; compatibleTemporalMetrics='LOADING_RATE'; requiredModelAssumptions='Joint-contact force-time model and rate definition required' })
    Row ([ordered]@{ measurementMetric='GROUND_REACTION_FORCE_LOADING_RATE'; measurementFamily='EXTERNAL_LOADING_RATE_PROXY'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='IMPACT_STABILIZATION|COMPRESSION'; compatibleTemporalMetrics='LOADING_RATE'; requiredModelAssumptions='Explicit approved proxy mapping required' })
    Row ([ordered]@{ measurementMetric='MEASURED_TENDON_ENERGY_STORAGE'; measurementFamily='TENDON_ENERGY'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='ENERGY_STORAGE_RELEASE'; compatibleTemporalMetrics='PEAK|CYCLIC_EXPOSURE'; requiredModelAssumptions='Direct energy or force-elongation measurement required' })
    Row ([ordered]@{ measurementMetric='MODELED_TENDON_ENERGY_STORAGE'; measurementFamily='TENDON_ENERGY'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='ENERGY_STORAGE_RELEASE'; compatibleTemporalMetrics='PEAK|CYCLIC_EXPOSURE'; requiredModelAssumptions='Force-elongation model assumptions required' })
    Row ([ordered]@{ measurementMetric='EVENT_COUNT'; measurementFamily='EVENT_COUNT'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='TENSION|COMPRESSION|IMPACT_STABILIZATION|INVERSION_STRESS|ANTERIOR_TRANSLATION_STRESS'; compatibleTemporalMetrics='CYCLIC_EVENT_COUNT'; requiredModelAssumptions='Event definition and laterality required' })
    Row ([ordered]@{ measurementMetric='SOURCE_DEFINED_CYCLIC_EXPOSURE'; measurementFamily='SOURCE_DEFINED_CYCLIC'; metricOrigin=$sourceObserved; compatibleMechanicalLoadModes='TENSION|COMPRESSION|IMPACT_STABILIZATION|INVERSION_STRESS|ANTERIOR_TRANSLATION_STRESS|ENERGY_STORAGE_RELEASE'; compatibleTemporalMetrics='CYCLIC_EXPOSURE|CUMULATIVE_WEEKLY_EXPOSURE'; requiredModelAssumptions='Source protocol and aggregation definition required' })
    Row ([ordered]@{ measurementMetric='SOURCE_DEFINED_COMPOSITE_INDEX'; measurementFamily='SOURCE_SPECIFIC_COMPOSITE'; metricOrigin='SOURCE_SPECIFIC'; compatibleMechanicalLoadModes='COMPRESSION|TENSION|IMPACT_STABILIZATION'; compatibleTemporalMetrics='PEAK'; requiredModelAssumptions='Source formula required; no generic biomechanical interpretation' })
)
$measurements | ForEach-Object { $_ | Add-Member createdBy 'Codex'; $_ | Add-Member createdByType 'AI_AGENT'; $_ | Add-Member createdAt $preparedAt }
Export-Rows 'tissue_measurement_metric_registry_v1.csv' $measurements

$forceMetrics = 'MODELED_TENDON_FORCE|MODELED_JOINT_CONTACT_FORCE|MODELED_LIGAMENT_FORCE'
$impulseMetrics = 'JOINT_CONTACT_FORCE_TIME_INTEGRAL|TENDON_FORCE_TIME_INTEGRAL'
$rateMetrics = 'MODELED_TENDON_FORCE_LOADING_RATE|MODELED_JOINT_CONTACT_FORCE_LOADING_RATE|GROUND_REACTION_FORCE_LOADING_RATE'
$normalizations = @(
    Row ([ordered]@{ normalizationBasis='ABSOLUTE_FORCE_NEWTON'; unitFamily='FORCE'; biomechanicalMeaning='Absolute internal or external force in newtons'; compatibleMeasurementMetrics="$forceMetrics|GROUND_REACTION_FORCE" })
    Row ([ordered]@{ normalizationBasis='BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE'; unitFamily='INTERNAL_FORCE_PER_BODY_WEIGHT'; biomechanicalMeaning='Internal tissue or joint force divided by body weight'; compatibleMeasurementMetrics=$forceMetrics })
    Row ([ordered]@{ normalizationBasis='BODY_MASS_NORMALIZED_INTERNAL_FORCE'; unitFamily='INTERNAL_FORCE_PER_BODY_MASS'; biomechanicalMeaning='Internal force normalized to body mass'; compatibleMeasurementMetrics=$forceMetrics })
    Row ([ordered]@{ normalizationBasis='BODY_WEIGHT_NORMALIZED_IMPULSE'; unitFamily='IMPULSE_PER_BODY_WEIGHT'; biomechanicalMeaning='Force-time integral normalized to body weight'; compatibleMeasurementMetrics=$impulseMetrics })
    Row ([ordered]@{ normalizationBasis='BODY_WEIGHT_TIME_NORMALIZED_INTERNAL_IMPULSE'; unitFamily='INTERNAL_IMPULSE_PER_BODY_WEIGHT_TIME'; biomechanicalMeaning='Internal force-time integral represented in body-weight seconds'; compatibleMeasurementMetrics=$impulseMetrics })
    Row ([ordered]@{ normalizationBasis='BODY_WEIGHT_NORMALIZED_LOADING_RATE'; unitFamily='LOADING_RATE_PER_BODY_WEIGHT'; biomechanicalMeaning='Loading rate normalized to body weight'; compatibleMeasurementMetrics=$rateMetrics })
    Row ([ordered]@{ normalizationBasis='BODY_MASS_NORMALIZED_JOINT_MOMENT'; unitFamily='JOINT_MOMENT_PER_BODY_MASS'; biomechanicalMeaning='External joint moment normalized to body mass'; compatibleMeasurementMetrics='EXTERNAL_JOINT_MOMENT' })
    Row ([ordered]@{ normalizationBasis='EXTERNAL_LOAD_KG'; unitFamily='EXTERNAL_LOAD'; biomechanicalMeaning='External exercise resistance in kilograms'; compatibleMeasurementMetrics='EXTERNAL_JOINT_MOMENT' })
    Row ([ordered]@{ normalizationBasis='EXTERNAL_LOAD_AS_BODYWEIGHT_FRACTION'; unitFamily='EXTERNAL_LOAD'; biomechanicalMeaning='External resistance as a fraction of body weight'; compatibleMeasurementMetrics='EXTERNAL_JOINT_MOMENT' })
    Row ([ordered]@{ normalizationBasis='RELATIVE_LOAD_PERCENT_1RM'; unitFamily='EXTERNAL_LOAD'; biomechanicalMeaning='External resistance relative to one-repetition maximum'; compatibleMeasurementMetrics='EXTERNAL_JOINT_MOMENT' })
    Row ([ordered]@{ normalizationBasis='MEASURED_TENDON_STRAIN_PERCENT'; unitFamily='STRAIN'; biomechanicalMeaning='Measured tendon strain percentage'; compatibleMeasurementMetrics='MEASURED_TENDON_STRAIN|MEASURED_LIGAMENT_STRAIN' })
    Row ([ordered]@{ normalizationBasis='SOURCE_DEFINED_NORMALIZED_INDEX'; unitFamily='SOURCE_SPECIFIC'; biomechanicalMeaning='Normalization defined only by the source'; compatibleMeasurementMetrics='SOURCE_DEFINED_CYCLIC_EXPOSURE|SOURCE_DEFINED_COMPOSITE_INDEX|MEASURED_TENDON_ENERGY_STORAGE|MODELED_TENDON_ENERGY_STORAGE' })
    Row ([ordered]@{ normalizationBasis='UNNORMALIZED_SOURCE_VALUE'; unitFamily='SOURCE_REPORTED'; biomechanicalMeaning='Source value retained without conversion'; compatibleMeasurementMetrics="$forceMetrics|MEASURED_TENDON_STRAIN|MEASURED_LIGAMENT_STRAIN|EXTERNAL_JOINT_MOMENT|$impulseMetrics|$rateMetrics|EVENT_COUNT|SOURCE_DEFINED_CYCLIC_EXPOSURE|MEASURED_TENDON_ENERGY_STORAGE|MODELED_TENDON_ENERGY_STORAGE|GROUND_REACTION_FORCE" })
)
$normalizations | ForEach-Object { $_ | Add-Member createdBy 'Codex'; $_ | Add-Member createdByType 'AI_AGENT'; $_ | Add-Member createdAt $preparedAt }
Export-Rows 'tissue_normalization_registry_v1.csv' $normalizations

$dimensions = [Collections.Generic.List[object]]::new()
function Add-Dimension($id,$class,$tissue,$mode,$temporalMetric,$metrics,$normalizations,$origin='SOURCE_OBSERVED',$formula='',$rubric='true',$profile='false',$legacy='',$notes='C3 non-production dimension') {
    $dimensions.Add((Row ([ordered]@{ dimensionId=$id; tissueClass=$class; tissueId=$tissue; mechanicalLoadMode=$mode; temporalMetric=$temporalMetric; allowedMeasurementMetrics=$metrics; allowedNormalizationBases=$normalizations; sourceObservedOrDerived=$origin; derivedFormulaId=$formula; biomechanicalMeaning="$mode measured as $temporalMetric for $tissue"; clinicalInterpretationBoundary='Interpret only within compatible measurement, normalization, and source conditions'; minimumEvidenceLevel='VERIFIED_PRIMARY_SOURCE'; rubricEligible=$rubric; profileEligible=$profile; deprecatedLegacyDimensions=$legacy; migrationNotes=$notes; createdBy='Codex'; createdByType='AI_AGENT'; createdAt=$preparedAt })))
}
$f='ABSOLUTE_FORCE_NEWTON|BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE|BODY_MASS_NORMALIZED_INTERNAL_FORCE'
$i='BODY_WEIGHT_NORMALIZED_IMPULSE|BODY_WEIGHT_TIME_NORMALIZED_INTERNAL_IMPULSE'
$r='BODY_WEIGHT_NORMALIZED_LOADING_RATE|UNNORMALIZED_SOURCE_VALUE'
Add-Dimension 'ACH_TENSION_PEAK' 'TENDON' 'ACHILLES_TENDON' 'TENSION' 'PEAK' 'MODELED_TENDON_FORCE|MEASURED_TENDON_STRAIN' "$f|MEASURED_TENDON_STRAIN_PERCENT" 'SOURCE_OBSERVED' '' 'true' 'false' 'PEAK_TENSILE_LOAD|TENDON_STRAIN'
Add-Dimension 'ACH_TENSION_IMPULSE_EVENT' 'TENDON' 'ACHILLES_TENDON' 'TENSION' 'IMPULSE_PER_EVENT' 'TENDON_FORCE_TIME_INTEGRAL' $i
Add-Dimension 'ACH_TENSION_LOADING_RATE' 'TENDON' 'ACHILLES_TENDON' 'TENSION' 'LOADING_RATE' 'MODELED_TENDON_FORCE_LOADING_RATE' $r 'SOURCE_OBSERVED' '' 'true' 'false' 'LOADING_RATE'
Add-Dimension 'ACH_TENSION_CYCLIC' 'TENDON' 'ACHILLES_TENDON' 'TENSION' 'CYCLIC_EXPOSURE' 'SOURCE_DEFINED_CYCLIC_EXPOSURE' 'SOURCE_DEFINED_NORMALIZED_INDEX|UNNORMALIZED_SOURCE_VALUE' 'SOURCE_OBSERVED' '' 'false' 'false' 'CYCLIC_TENSILE_LOAD'
Add-Dimension 'ACH_ENERGY_PEAK' 'TENDON' 'ACHILLES_TENDON' 'ENERGY_STORAGE_RELEASE' 'PEAK' 'MEASURED_TENDON_ENERGY_STORAGE|MODELED_TENDON_ENERGY_STORAGE' 'SOURCE_DEFINED_NORMALIZED_INDEX|UNNORMALIZED_SOURCE_VALUE' 'SOURCE_OBSERVED' '' 'false' 'false' 'ENERGY_STORAGE_RELEASE'
Add-Dimension 'ACH_ENERGY_CYCLIC' 'TENDON' 'ACHILLES_TENDON' 'ENERGY_STORAGE_RELEASE' 'CYCLIC_EXPOSURE' 'MEASURED_TENDON_ENERGY_STORAGE|MODELED_TENDON_ENERGY_STORAGE|SOURCE_DEFINED_CYCLIC_EXPOSURE' 'SOURCE_DEFINED_NORMALIZED_INDEX|UNNORMALIZED_SOURCE_VALUE' 'SOURCE_OBSERVED' '' 'false'
foreach ($tissue in @('PATELLAR_TENDON','QUADRICEPS_TENDON')) {
    $prefix = if ($tissue -eq 'PATELLAR_TENDON') { 'PAT' } else { 'QUAD' }
    Add-Dimension "${prefix}_TENSION_PEAK" 'TENDON' $tissue 'TENSION' 'PEAK' 'MODELED_TENDON_FORCE|MEASURED_TENDON_STRAIN' "$f|MEASURED_TENDON_STRAIN_PERCENT" 'SOURCE_OBSERVED' '' 'true' 'false' 'PEAK_TENSILE_LOAD|TENDON_STRAIN'
    Add-Dimension "${prefix}_TENSION_IMPULSE_EVENT" 'TENDON' $tissue 'TENSION' 'IMPULSE_PER_EVENT' 'TENDON_FORCE_TIME_INTEGRAL' $i
    Add-Dimension "${prefix}_TENSION_LOADING_RATE" 'TENDON' $tissue 'TENSION' 'LOADING_RATE' 'MODELED_TENDON_FORCE_LOADING_RATE' $r 'SOURCE_OBSERVED' '' 'true' 'false' 'LOADING_RATE'
    Add-Dimension "${prefix}_TENSION_CYCLIC" 'TENDON' $tissue 'TENSION' 'CYCLIC_EXPOSURE' 'SOURCE_DEFINED_CYCLIC_EXPOSURE' 'SOURCE_DEFINED_NORMALIZED_INDEX|UNNORMALIZED_SOURCE_VALUE' 'SOURCE_OBSERVED' '' 'false' 'false' 'CYCLIC_TENSILE_LOAD'
    Add-Dimension "${prefix}_ECCENTRIC_PEAK" 'TENDON' $tissue 'TENSION' 'ECCENTRIC_PHASE_PEAK' 'MODELED_TENDON_FORCE|MEASURED_TENDON_STRAIN' "$f|MEASURED_TENDON_STRAIN_PERCENT" 'SOURCE_OBSERVED' '' 'false' 'false' 'ECCENTRIC_LOAD'
    Add-Dimension "${prefix}_ECCENTRIC_IMPULSE" 'TENDON' $tissue 'TENSION' 'ECCENTRIC_PHASE_IMPULSE' 'TENDON_FORCE_TIME_INTEGRAL' $i 'SOURCE_OBSERVED' '' 'false'
}
Add-Dimension 'PFJ_COMPRESSION_PEAK' 'JOINT' 'KNEE_PATELLOFEMORAL' 'COMPRESSION' 'PEAK' 'MODELED_JOINT_CONTACT_FORCE' $f 'SOURCE_OBSERVED' '' 'true' 'false' 'PEAK_COMPRESSION'
Add-Dimension 'PFJ_COMPRESSION_IMPULSE_EVENT' 'JOINT' 'KNEE_PATELLOFEMORAL' 'COMPRESSION' 'IMPULSE_PER_EVENT' 'JOINT_CONTACT_FORCE_TIME_INTEGRAL' $i 'SOURCE_OBSERVED' '' 'true' 'false' 'COMPRESSION_IMPULSE'
Add-Dimension 'PFJ_COMPRESSION_LOADING_RATE' 'JOINT' 'KNEE_PATELLOFEMORAL' 'COMPRESSION' 'LOADING_RATE' 'MODELED_JOINT_CONTACT_FORCE_LOADING_RATE' $r 'SOURCE_OBSERVED' '' 'true' 'false' 'COMPRESSION_LOADING_RATE'
Add-Dimension 'PFJ_COMPRESSION_SESSION_IMPULSE' 'JOINT' 'KNEE_PATELLOFEMORAL' 'COMPRESSION' 'CUMULATIVE_SESSION_IMPULSE' 'JOINT_CONTACT_FORCE_TIME_INTEGRAL' $i 'APPLICATION_DERIVED' 'SUM_COMPATIBLE_EVENT_IMPULSES_V1' 'false' 'false' '' 'Requires source per-event impulse and deterministic event count; runtime population is prohibited in C3'
Add-Dimension 'TFJ_COMPRESSION_PEAK' 'JOINT' 'KNEE_TIBIOFEMORAL' 'COMPRESSION' 'PEAK' 'MODELED_JOINT_CONTACT_FORCE' $f
Add-Dimension 'TFJ_COMPRESSION_IMPULSE_EVENT' 'JOINT' 'KNEE_TIBIOFEMORAL' 'COMPRESSION' 'IMPULSE_PER_EVENT' 'JOINT_CONTACT_FORCE_TIME_INTEGRAL' $i
Add-Dimension 'TFJ_AP_SHEAR_PEAK' 'JOINT' 'KNEE_TIBIOFEMORAL' 'ANTERIOR_POSTERIOR_SHEAR' 'PEAK' 'MODELED_JOINT_CONTACT_FORCE|EXTERNAL_JOINT_MOMENT' "$f|BODY_MASS_NORMALIZED_JOINT_MOMENT"
Add-Dimension 'TFJ_AP_SHEAR_IMPULSE_EVENT' 'JOINT' 'KNEE_TIBIOFEMORAL' 'ANTERIOR_POSTERIOR_SHEAR' 'IMPULSE_PER_EVENT' 'JOINT_CONTACT_FORCE_TIME_INTEGRAL|EXTERNAL_JOINT_MOMENT' "$i|BODY_MASS_NORMALIZED_JOINT_MOMENT"
Add-Dimension 'TFJ_ROTATIONAL_PEAK' 'JOINT' 'KNEE_TIBIOFEMORAL' 'ROTATIONAL_STRESS' 'PEAK' 'MODELED_JOINT_CONTACT_FORCE|EXTERNAL_JOINT_MOMENT' "$f|BODY_MASS_NORMALIZED_JOINT_MOMENT"
$ligNorm='ABSOLUTE_FORCE_NEWTON|MEASURED_TENDON_STRAIN_PERCENT|BODY_MASS_NORMALIZED_JOINT_MOMENT|UNNORMALIZED_SOURCE_VALUE'
Add-Dimension 'ACL_ANTERIOR_TRANSLATION_PEAK' 'LIGAMENT' 'KNEE_ACL' 'ANTERIOR_TRANSLATION_STRESS' 'PEAK' 'MODELED_LIGAMENT_FORCE|MEASURED_LIGAMENT_STRAIN|EXTERNAL_JOINT_MOMENT' $ligNorm 'SOURCE_OBSERVED' '' 'false' 'false' 'ANTERIOR_TRANSLATION'
Add-Dimension 'ACL_INTERNAL_ROTATION_PEAK' 'LIGAMENT' 'KNEE_ACL' 'INTERNAL_ROTATION_STRESS' 'PEAK' 'MODELED_LIGAMENT_FORCE|MEASURED_LIGAMENT_STRAIN|EXTERNAL_JOINT_MOMENT' $ligNorm 'SOURCE_OBSERVED' '' 'false' 'false' 'INTERNAL_ROTATION'
Add-Dimension 'ACL_VALGUS_PEAK' 'LIGAMENT' 'KNEE_ACL' 'VALGUS_STRESS' 'PEAK' 'MODELED_LIGAMENT_FORCE|MEASURED_LIGAMENT_STRAIN|EXTERNAL_JOINT_MOMENT' $ligNorm 'SOURCE_OBSERVED' '' 'false' 'false' 'VALGUS'
Add-Dimension 'ACL_IMPACT_LOADING_RATE' 'LIGAMENT' 'KNEE_ACL' 'IMPACT_STABILIZATION' 'LOADING_RATE' 'GROUND_REACTION_FORCE_LOADING_RATE' 'BODY_WEIGHT_NORMALIZED_LOADING_RATE|UNNORMALIZED_SOURCE_VALUE' 'SOURCE_OBSERVED' '' 'false' 'false' 'IMPACT_STABILIZATION'
Add-Dimension 'ACL_ANTERIOR_CYCLIC' 'LIGAMENT' 'KNEE_ACL' 'ANTERIOR_TRANSLATION_STRESS' 'CYCLIC_EXPOSURE' 'SOURCE_DEFINED_CYCLIC_EXPOSURE' 'SOURCE_DEFINED_NORMALIZED_INDEX|UNNORMALIZED_SOURCE_VALUE' 'SOURCE_OBSERVED' '' 'false'
Add-Dimension 'PCL_POSTERIOR_TRANSLATION_PEAK' 'LIGAMENT' 'KNEE_PCL' 'POSTERIOR_TRANSLATION_STRESS' 'PEAK' 'MODELED_LIGAMENT_FORCE|MEASURED_LIGAMENT_STRAIN|EXTERNAL_JOINT_MOMENT' $ligNorm 'SOURCE_OBSERVED' '' 'false' 'false' 'POSTERIOR_TRANSLATION'
Add-Dimension 'MCL_VALGUS_PEAK' 'LIGAMENT' 'KNEE_MCL' 'VALGUS_STRESS' 'PEAK' 'MODELED_LIGAMENT_FORCE|MEASURED_LIGAMENT_STRAIN|EXTERNAL_JOINT_MOMENT' $ligNorm 'SOURCE_OBSERVED' '' 'false' 'false' 'VALGUS'
Add-Dimension 'LCL_VARUS_PEAK' 'LIGAMENT' 'KNEE_LCL' 'VARUS_STRESS' 'PEAK' 'MODELED_LIGAMENT_FORCE|MEASURED_LIGAMENT_STRAIN|EXTERNAL_JOINT_MOMENT' $ligNorm 'SOURCE_OBSERVED' '' 'false' 'false' 'VARUS'
Add-Dimension 'TALOCRURAL_COMPRESSION_PEAK' 'JOINT' 'ANKLE_TALOCRURAL' 'COMPRESSION' 'PEAK' 'MODELED_JOINT_CONTACT_FORCE|GROUND_REACTION_FORCE' "$f|UNNORMALIZED_SOURCE_VALUE"
Add-Dimension 'TALOCRURAL_IMPACT_LOADING_RATE' 'JOINT' 'ANKLE_TALOCRURAL' 'IMPACT_STABILIZATION' 'LOADING_RATE' 'GROUND_REACTION_FORCE_LOADING_RATE' 'BODY_WEIGHT_NORMALIZED_LOADING_RATE|UNNORMALIZED_SOURCE_VALUE' 'SOURCE_OBSERVED' '' 'false'
Add-Dimension 'SUBTALAR_ROTATIONAL_PEAK' 'JOINT' 'ANKLE_SUBTALAR' 'ROTATIONAL_STRESS' 'PEAK' 'MODELED_JOINT_CONTACT_FORCE|EXTERNAL_JOINT_MOMENT' "$f|BODY_MASS_NORMALIZED_JOINT_MOMENT"
Add-Dimension 'ANKLE_LATERAL_INVERSION_PEAK' 'LIGAMENT' 'ANKLE_LATERAL_LIGAMENT_COMPLEX' 'INVERSION_STRESS' 'PEAK' 'MODELED_LIGAMENT_FORCE|MEASURED_LIGAMENT_STRAIN|EXTERNAL_JOINT_MOMENT' $ligNorm 'SOURCE_OBSERVED' '' 'false' 'false' 'INVERSION'
Add-Dimension 'ANKLE_LATERAL_IMPACT_LOADING_RATE' 'LIGAMENT' 'ANKLE_LATERAL_LIGAMENT_COMPLEX' 'IMPACT_STABILIZATION' 'LOADING_RATE' 'GROUND_REACTION_FORCE_LOADING_RATE' 'BODY_WEIGHT_NORMALIZED_LOADING_RATE|UNNORMALIZED_SOURCE_VALUE' 'SOURCE_OBSERVED' '' 'false' 'false' 'IMPACT_STABILIZATION'
Add-Dimension 'ANKLE_LATERAL_INVERSION_CYCLIC' 'LIGAMENT' 'ANKLE_LATERAL_LIGAMENT_COMPLEX' 'INVERSION_STRESS' 'CYCLIC_EXPOSURE' 'SOURCE_DEFINED_CYCLIC_EXPOSURE' 'SOURCE_DEFINED_NORMALIZED_INDEX|UNNORMALIZED_SOURCE_VALUE' 'SOURCE_OBSERVED' '' 'false'
Export-Rows 'tissue_load_dimension_registry_v2.csv' $dimensions

$oldCandidates = @(Import-Csv (Join-Path $AssetDirectory 'tissue_evidence_claim_candidates_v1.csv'))
$newCandidates = @(Import-Csv (Join-Path $AssetDirectory 'tissue_evidence_claim_candidates_revised_v1.csv'))
$oldRubrics = @(Import-Csv (Join-Path $AssetDirectory 'tissue_load_band_rubric_v1.csv'))
$newRubrics = @(Import-Csv (Join-Path $AssetDirectory 'tissue_load_band_rubric_revised_v1.csv'))
$exact = @{
    PEAK_COMPRESSION=@('COMPRESSION','PEAK','MODELED_JOINT_CONTACT_FORCE'); COMPRESSION_IMPULSE=@('COMPRESSION','IMPULSE_PER_EVENT','JOINT_CONTACT_FORCE_TIME_INTEGRAL'); COMPRESSION_LOADING_RATE=@('COMPRESSION','LOADING_RATE','MODELED_JOINT_CONTACT_FORCE_LOADING_RATE'); PEAK_TENSILE_LOAD=@('TENSION','PEAK','MODELED_TENDON_FORCE'); TENDON_STRAIN=@('TENSION','PEAK','MEASURED_TENDON_STRAIN'); ISOMETRIC_DURATION=@('TENSION','ISOMETRIC_HOLD_EXPOSURE','MODELED_TENDON_FORCE')
}
$sourceSpecific = @('COMPRESSION')
$migrations = @('COMPRESSION','PEAK_COMPRESSION','COMPRESSION_IMPULSE','COMPRESSION_LOADING_RATE','ANTERIOR_POSTERIOR_SHEAR','ROTATIONAL_SHEAR','IMPACT_IMPULSE','STABILITY_DEMAND','END_RANGE_STRESS','CYCLIC_MECHANICAL_EXPOSURE','PEAK_TENSILE_LOAD','TENDON_STRAIN','CYCLIC_TENSILE_LOAD','ECCENTRIC_LOAD','ENERGY_STORAGE_RELEASE','COMPRESSIVE_TENDON_LOAD','LOADING_RATE','ISOMETRIC_DURATION','STRETCH_UNDER_LOAD','ANTERIOR_TRANSLATION','POSTERIOR_TRANSLATION','VALGUS','VARUS','INTERNAL_ROTATION','EXTERNAL_ROTATION','INVERSION','EVERSION','END_RANGE_RESTRAINT','DECELERATION_STABILIZATION','IMPACT_STABILIZATION','TENSILE_LOAD','CYCLIC_LOAD','COMPRESSIVE_LOAD') | ForEach-Object {
    $legacy = $_; $target = $exact[$legacy]
    $decision = if ($target) { 'EXACT_MIGRATION' } elseif ($legacy -in $sourceSpecific) { 'SOURCE_SPECIFIC_ONLY' } elseif ($legacy -in @('IMPACT_IMPULSE','CYCLIC_TENSILE_LOAD','ECCENTRIC_LOAD','ENERGY_STORAGE_RELEASE')) { 'SPLIT_INTO_MULTIPLE_DIMENSIONS' } else { 'DEPRECATED_AMBIGUOUS' }
    $candidateIds = @($oldCandidates | Where-Object loadDimension -eq $legacy | ForEach-Object claimCandidateId) + @($newCandidates | Where-Object loadDimension -eq $legacy | ForEach-Object revisedClaimCandidateId)
    $rubricIds = @($oldRubrics | Where-Object loadDimension -eq $legacy | ForEach-Object rubricId) + @($newRubrics | Where-Object loadDimension -eq $legacy | ForEach-Object rubricId)
    Row ([ordered]@{ migrationId="MIG_C3_$legacy"; legacyDimension=$legacy; targetMechanicalLoadMode=if($target){$target[0]}elseif($legacy -eq 'COMPRESSION'){'COMPRESSION'}else{''}; targetTemporalMetric=if($target){$target[1]}else{''}; targetMeasurementMetric=if($target){$target[2]}elseif($legacy -eq 'COMPRESSION'){'SOURCE_DEFINED_COMPOSITE_INDEX'}else{''}; migrationDecision=$decision; affectedClaimIds=($candidateIds | Sort-Object -Unique) -join '|'; affectedRubricIds=($rubricIds | Sort-Object -Unique) -join '|'; ambiguityReason=if($target){''}else{'Legacy value does not encode an unambiguous mechanical mode, temporal metric, and measurement family.'}; requiredManualReview=if($target){'false'}else{'true'}; preparedBy='Codex'; preparedByType='AI_AGENT'; preparedAt=$preparedAt; migrationNotes='Historical rows remain parseable and immutable; C3 candidates require explicit remapping.' })
}
Export-Rows 'tissue_load_dimension_migration_v1.csv' $migrations

Write-Output "MECHANICAL_LOAD_MODES=$($modes.Count)"
Write-Output "TEMPORAL_METRICS=$($temporal.Count)"
Write-Output "MEASUREMENT_METRICS=$($measurements.Count)"
Write-Output "NORMALIZATIONS=$($normalizations.Count)"
Write-Output "VALID_DIMENSIONS=$($dimensions.Count)"
Write-Output "LEGACY_MIGRATIONS=$($migrations.Count)"

param([string]$AssetDirectory = "app/src/main/assets/metadata/tissue_load_v1")

$ErrorActionPreference = "Stop"
$preparedAt = "2026-07-15T00:00:01Z"

function Export-Rows([string]$name, $rows) {
    @($rows) | Export-Csv -LiteralPath (Join-Path $AssetDirectory $name) -NoTypeInformation -Encoding UTF8
}
function Registry($values) {
    @($values | ForEach-Object {
        [pscustomobject][ordered]@{ id=$_[0]; definition=$_[1]; scientificBoundary=$_[2]; createdBy='Codex'; createdByType='AI_AGENT'; createdAt=$preparedAt }
    })
}

$forbiddenModes = @('IMPACT_STABILIZATION','END_RANGE_STRESS','ENERGY_STORAGE_RELEASE')
$modes = @(Import-Csv (Join-Path $AssetDirectory 'tissue_mechanical_load_mode_registry_v1.csv') | Where-Object mechanicalLoadMode -notin $forbiddenModes | ForEach-Object {
    if ($_.mechanicalLoadMode -eq 'TENSION') {
        $_.tissueClasses = 'TENDON|LIGAMENT|FASCIA'
        $_.biomechanicalMeaning = 'Longitudinal tensile loading of collagenous tissue, including ligament force or strain'
    }
    $_.createdAt = $preparedAt
    $_
})
Export-Rows 'tissue_mechanical_load_mode_registry_c3_1_v1.csv' $modes

Export-Rows 'tissue_event_context_registry_v1.csv' (Registry @(
    @('STRENGTH_REPETITION','Resistance-training repetition','Does not establish internal tissue magnitude'),
    @('ISOMETRIC_HOLD','Static hold task','Duration and joint position remain separate'),
    @('JUMP_TAKEOFF','Jump propulsion event','Does not imply a directional ligament mechanism'),
    @('JUMP_LANDING','Landing after a jump','Does not imply impact strain or a directional mechanism'),
    @('HOP_TAKEOFF','Single-limb hop propulsion','Protocol and laterality remain explicit'),
    @('HOP_LANDING','Single-limb hop landing','Single and repeated hopping remain distinct'),
    @('DROP_LANDING','Landing from a prescribed drop','Drop height and rebound protocol remain explicit'),
    @('RUNNING_GAIT','Running gait cycle','Speed and surface remain explicit'),
    @('SQUAT','Squat task','Load, depth, and stance remain explicit'),
    @('LUNGE','Lunge task','Direction, stride, and load remain explicit'),
    @('PASSIVE_INVERSION_TEST','Externally applied passive inversion','Not an exercise-load transfer')
))
Export-Rows 'tissue_movement_phase_registry_v1.csv' (Registry @(
    @('FULL_EVENT','Whole reported event','No subphase is inferred'),
    @('PRE_CONTACT','Before initial ground contact','Timing must be source reported'),
    @('WEIGHT_ACCEPTANCE','Initial load-acceptance phase','Source phase definition controls'),
    @('CONCENTRIC','Shortening phase','Must be source identified'),
    @('ECCENTRIC','Lengthening phase','Must be source identified'),
    @('ISOMETRIC','Static phase','Hold duration remains separate')
))
Export-Rows 'tissue_position_context_registry_v1.csv' (Registry @(
    @('PARTIAL_ROM','Partial range of motion','Exact angle or protocol remains required'),
    @('FULL_ROM','Full source-defined range','Not transferable across exercise variants'),
    @('END_RANGE','Source-defined terminal position','Position is not a mechanical load mode'),
    @('SPECIFIED_JOINT_ANGLE','Explicitly reported joint angle','Angle remains in the structured condition'),
    @('UNKNOWN_ROM','Range not reported','Missing detail remains missing')
))
Export-Rows 'tissue_functional_demand_registry_v1.csv' (Registry @(
    @('STABILIZATION_DEMAND','Functional stabilization requirement','Not an internal tissue-load magnitude'),
    @('IMPACT_ATTENUATION','Functional impact-attenuation requirement','Not a mechanical direction'),
    @('DECELERATION_DEMAND','Functional braking requirement','Does not establish ligament force'),
    @('ENERGY_STORAGE_DEMAND','Functional elastic-storage requirement','Requires a measured response metric'),
    @('ENERGY_RELEASE_DEMAND','Functional elastic-return requirement','Requires a measured response metric')
))
Export-Rows 'tissue_response_metric_registry_v1.csv' (Registry @(
    @('ELASTIC_ENERGY_STORAGE','Measured or modeled elastic energy stored','Cannot be inferred from peak force alone'),
    @('ELASTIC_ENERGY_RETURN','Measured or modeled elastic energy returned','Cannot be inferred from task category'),
    @('STRAIN_ENERGY','Energy associated with tissue deformation','Requires force-deformation evidence'),
    @('HYSTERESIS','Energy loss across loading and unloading','Requires a measured loading cycle')
))
Export-Rows 'tissue_evidence_relation_registry_v1.csv' (Registry @(
    @('DIRECT_INTERNAL_MEASUREMENT','Direct tissue or instrumented internal measurement','Condition bounded'),
    @('VALIDATED_INTERNAL_MODEL','Internal quantity from a validated model','Validation scope and assumptions required'),
    @('VALIDATED_PROXY','External metric with an explicit validated internal mapping','Mapping ID and limits required'),
    @('UNVALIDATED_PROXY','External metric without an approved mapping','Never rubric or profile eligible'),
    @('CONTEXT_ONLY','Task or condition information only','Cannot establish tissue-load magnitude')
))
Export-Rows 'tissue_rubric_kind_registry_v1.csv' (Registry @(
    @('CONDITION_ANCHOR','Band label for one exact source condition','No interpolation or neighboring-value classification'),
    @('INTERVAL_BAND','Defensible numerical interval','Bounds and scientific derivation required'),
    @('ORDERING_RULE','Condition ordering without numerical thresholds','Scope remains within compatible evidence')
))
Export-Rows 'tissue_external_load_representation_registry_v1.csv' (Registry @(
    @('ADDITIONAL_EXTERNAL_LOAD','Added mass or resistance excluding body mass','Must not contain total system mass'),
    @('TOTAL_SYSTEM_MASS','Body mass plus added mass','Must not drive an added-load modifier'),
    @('RELATIVE_LOAD_PERCENT_1RM','Load relative to one-repetition maximum','Not a bodyweight fraction'),
    @('BODYWEIGHT_TASK','Task performed with body mass and no reported added load','Internal BW normalization remains separate'),
    @('NO_EXTERNAL_LOAD','Source explicitly reports no added load','Body mass can still load the tissue'),
    @('NOT_REPORTED','External load was not reported','Missing remains missing')
))

$measurements = @(Import-Csv (Join-Path $AssetDirectory 'tissue_measurement_metric_registry_v1.csv') | ForEach-Object {
    $metric = $_.measurementMetric
    $compatible = @($_.compatibleMechanicalLoadModes -split '\|' | Where-Object { $_ -and $_ -notin $forbiddenModes })
    if ($metric -in @('MEASURED_TENDON_ENERGY_STORAGE','MODELED_TENDON_ENERGY_STORAGE')) { $compatible = @('TENSION') }
    if ($metric -eq 'MEASURED_LIGAMENT_STRAIN') { $compatible = @('TENSION') }
    if ($metric -eq 'MODELED_LIGAMENT_FORCE') { $compatible = @('TENSION') + $compatible }
    $relation = if ($metric -match '^MEASURED_') { 'DIRECT_INTERNAL_MEASUREMENT' }
        elseif ($metric -match '^MODELED_|_FORCE_TIME_INTEGRAL$') { 'VALIDATED_INTERNAL_MODEL' }
        elseif ($metric -match 'EXTERNAL_JOINT_MOMENT|GROUND_REACTION|KINEMATIC_STABILITY_PROXY') { 'VALIDATED_PROXY|UNVALIDATED_PROXY' }
        else { 'CONTEXT_ONLY' }
    [pscustomobject][ordered]@{
        measurementMetric=$metric; measurementFamily=$_.measurementFamily; metricOrigin=$_.metricOrigin
        compatibleMechanicalLoadModes=(@($compatible | Sort-Object -Unique) -join '|')
        compatibleTemporalMetrics=$_.compatibleTemporalMetrics; allowedEvidenceRelations=$relation
        requiredModelAssumptions=$_.requiredModelAssumptions; createdBy='Codex'; createdByType='AI_AGENT'; createdAt=$preparedAt
    }
})
Export-Rows 'tissue_measurement_metric_registry_c3_1_v1.csv' $measurements

$blockedDimensions = @('ACL_IMPACT_LOADING_RATE','TALOCRURAL_IMPACT_LOADING_RATE','ANKLE_LATERAL_IMPACT_LOADING_RATE')
$renamedDimensions = @{
    ACH_ENERGY_PEAK='ACH_TENSION_ENERGY_RESPONSE_PEAK'
    ACH_ENERGY_CYCLIC='ACH_TENSION_ENERGY_RESPONSE_CYCLIC'
    ACL_IMPACT_PEAK='ACL_TENSION_PEAK'
}
$directionalLigament = @('ACL_ANTERIOR_TRANSLATION_PEAK','ACL_INTERNAL_ROTATION_PEAK','ACL_VALGUS_PEAK','PCL_POSTERIOR_TRANSLATION_PEAK','MCL_VALGUS_PEAK','LCL_VARUS_PEAK')
$dimensions = [Collections.Generic.List[object]]::new()
$corrections = [Collections.Generic.List[object]]::new()
foreach ($old in @(Import-Csv (Join-Path $AssetDirectory 'tissue_load_dimension_registry_v2.csv'))) {
    $blocked = $old.dimensionId -in $blockedDimensions
    $newId = if ($blocked) { '' } elseif ($renamedDimensions[$old.dimensionId]) { $renamedDimensions[$old.dimensionId] } else { $old.dimensionId }
    $newMode = if ($old.mechanicalLoadMode -eq 'ENERGY_STORAGE_RELEASE' -or $old.dimensionId -eq 'ACL_IMPACT_PEAK') { 'TENSION' } elseif ($blocked) { '' } else { $old.mechanicalLoadMode }
    $decision = if ($blocked) { 'REMOVED_UNSUPPORTED_INTERPRETATION' } elseif ($newId -ne $old.dimensionId) { 'RECLASSIFIED' } else { 'UNCHANGED_VALID' }
    $corrections.Add([pscustomobject][ordered]@{
        oldDimensionId=$old.dimensionId; newDimensionId=$newId; oldMechanicalLoadMode=$old.mechanicalLoadMode
        newMechanicalLoadMode=$newMode; correctionDecision=$decision
        movedContext=if($old.mechanicalLoadMode -eq 'IMPACT_STABILIZATION'){'IMPACT_ATTENUATION'}elseif($old.mechanicalLoadMode -eq 'END_RANGE_STRESS'){'END_RANGE'}else{''}
        movedTissueResponse=if($old.mechanicalLoadMode -eq 'ENERGY_STORAGE_RELEASE'){'ELASTIC_ENERGY_STORAGE|ELASTIC_ENERGY_RETURN'}else{''}
        correctionReason=if($blocked){'External loading context has no validated internal tissue mapping.'}elseif($old.mechanicalLoadMode -in $forbiddenModes){'Context or tissue response was separated from physical mechanical loading.'}else{'Physical dimension identity remains valid.'}
        preparedBy='Codex'; preparedByType='AI_AGENT'; preparedAt=$preparedAt
    })
    if ($blocked) { continue }

    $metrics = $old.allowedMeasurementMetrics
    $norms = $old.allowedNormalizationBases
    $relationValues = [Collections.Generic.List[string]]::new()
    if ($metrics -match 'MEASURED_') { $relationValues.Add('DIRECT_INTERNAL_MEASUREMENT') }
    if ($metrics -match 'MODELED_|_FORCE_TIME_INTEGRAL') { $relationValues.Add('VALIDATED_INTERNAL_MODEL') }
    if ($metrics -match 'EXTERNAL_JOINT_MOMENT|GROUND_REACTION|KINEMATIC_STABILITY_PROXY') { $relationValues.Add('VALIDATED_PROXY'); $relationValues.Add('UNVALIDATED_PROXY') }
    if ($metrics -match 'SOURCE_DEFINED_COMPOSITE_INDEX|SOURCE_DEFINED_CYCLIC_EXPOSURE|EVENT_COUNT') { $relationValues.Add('CONTEXT_ONLY') }
    $relations = @($relationValues | Sort-Object -Unique) -join '|'
    if ($old.dimensionId -in $directionalLigament) { $metrics='MODELED_LIGAMENT_FORCE'; $norms='ABSOLUTE_FORCE_NEWTON|UNNORMALIZED_SOURCE_VALUE'; $relations='VALIDATED_INTERNAL_MODEL' }
    if ($old.dimensionId -in @('TFJ_AP_SHEAR_PEAK','TFJ_ROTATIONAL_PEAK')) { $metrics='MODELED_JOINT_CONTACT_FORCE'; $norms='ABSOLUTE_FORCE_NEWTON|BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE|BODY_MASS_NORMALIZED_INTERNAL_FORCE'; $relations='VALIDATED_INTERNAL_MODEL' }
    if ($old.dimensionId -eq 'TFJ_AP_SHEAR_IMPULSE_EVENT') { $metrics='JOINT_CONTACT_FORCE_TIME_INTEGRAL'; $norms='BODY_WEIGHT_NORMALIZED_IMPULSE|BODY_WEIGHT_TIME_NORMALIZED_INTERNAL_IMPULSE'; $relations='VALIDATED_INTERNAL_MODEL' }
    if ($old.dimensionId -eq 'SUBTALAR_ROTATIONAL_PEAK') { $metrics='MODELED_JOINT_CONTACT_FORCE'; $norms='ABSOLUTE_FORCE_NEWTON|BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE|BODY_MASS_NORMALIZED_INTERNAL_FORCE'; $relations='VALIDATED_INTERNAL_MODEL' }
    if ($old.dimensionId -eq 'TALOCRURAL_COMPRESSION_PEAK') { $metrics='MODELED_JOINT_CONTACT_FORCE'; $norms='ABSOLUTE_FORCE_NEWTON|BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE|BODY_MASS_NORMALIZED_INTERNAL_FORCE'; $relations='VALIDATED_INTERNAL_MODEL' }
    if ($old.dimensionId -eq 'ANKLE_LATERAL_INVERSION_PEAK') { $metrics='MODELED_LIGAMENT_FORCE'; $norms='ABSOLUTE_FORCE_NEWTON|UNNORMALIZED_SOURCE_VALUE'; $relations='VALIDATED_INTERNAL_MODEL' }
    if ($old.dimensionId -eq 'ACL_IMPACT_PEAK') { $metrics='MEASURED_LIGAMENT_STRAIN'; $norms='MEASURED_LIGAMENT_STRAIN_PERCENT'; $relations='DIRECT_INTERNAL_MEASUREMENT' }

    $dimensions.Add([pscustomobject][ordered]@{
        dimensionId=$newId; c3DimensionId=$old.dimensionId; tissueClass=$old.tissueClass; tissueId=$old.tissueId
        mechanicalLoadMode=$newMode; temporalMetric=$old.temporalMetric; allowedMeasurementMetrics=$metrics
        allowedNormalizationBases=$norms; allowedEvidenceRelations=$relations
        allowedTissueResponseMetrics=if($old.mechanicalLoadMode -eq 'ENERGY_STORAGE_RELEASE'){'ELASTIC_ENERGY_STORAGE|ELASTIC_ENERGY_RETURN'}else{''}
        sourceObservedOrDerived=$old.sourceObservedOrDerived; derivedFormulaId=$old.derivedFormulaId
        biomechanicalMeaning=if($old.mechanicalLoadMode -eq 'ENERGY_STORAGE_RELEASE'){"Tensile loading with an explicitly measured or modeled energy response for $($old.tissueId)"}elseif($old.dimensionId -eq 'ACL_IMPACT_PEAK'){'Peak ACL tensile strain; landing remains event context'}else{$old.biomechanicalMeaning}
        clinicalInterpretationBoundary=$old.clinicalInterpretationBoundary; minimumEvidenceLevel=$old.minimumEvidenceLevel
        rubricEligible=$old.rubricEligible; profileEligible=$old.profileEligible; correctionStatus=$decision
        migrationNotes='C3.1 non-production dimension; event, phase, position, functional demand, and tissue response are not dimension identity.'
        createdBy='Codex'; createdByType='AI_AGENT'; createdAt=$preparedAt
    })
}
Export-Rows 'tissue_load_dimension_registry_c3_1_v1.csv' $dimensions
Export-Rows 'tissue_c3_1_dimension_correction_v1.csv' $corrections

"MECHANICAL_MODES=$($modes.Count)"
"VALID_DIMENSIONS=$($dimensions.Count)"
"DIMENSION_CORRECTIONS=$($corrections.Count)"

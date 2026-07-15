param([string]$RepoRoot = (Split-Path -Parent $PSScriptRoot))

$ErrorActionPreference = 'Stop'
$assetDir = Join-Path $RepoRoot 'app/src/main/assets/metadata/tissue_load_v1'
$catalogPath = Join-Path $assetDir '../canonical_exercise_metadata_v0_3_5_0_pass3_1.csv'
$catalog = @(Import-Csv $catalogPath)
$coefficientSetId = 'TISSUE_MTC_C4A_0_1_1'
$allComplexes = @(Import-Csv (Join-Path $assetDir 'tissue_functional_complex_registry_v1.csv') | ForEach-Object complexId)

function Write-Csv([string]$name, [object[]]$rows) {
    [IO.File]::WriteAllLines((Join-Path $assetDir $name), @($rows | ConvertTo-Csv -NoTypeInformation), [Text.UTF8Encoding]::new($false))
}
function Semantic-Hash([string]$path) {
    $rows = @(Import-Csv $path); $header = @($rows[0].PSObject.Properties.Name)
    $encoded = @($rows | ForEach-Object { $row=$_; ($header | ForEach-Object { [string]$row.$_ }) -join [char]31 } | Sort-Object)
    $sha=[Security.Cryptography.SHA256]::Create()
    try { ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($header -join [char]31)+"`n"+($encoded -join [char]30))) | ForEach-Object ToString x2) -join '' }
    finally { $sha.Dispose() }
}
function Combined-Hash([hashtable]$parts) {
    $payload = ($parts.GetEnumerator() | Sort-Object Key | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "`n"
    $sha=[Security.Cryptography.SHA256]::Create()
    try { ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($payload)) | ForEach-Object ToString x2) -join '' }
    finally { $sha.Dispose() }
}

$familyMap = @{
    'ANKLE_STIFFNESS_SSC_CONDITIONING' = 'POGO_OR_REPEATED_HOP|ANKLE_PROPULSION_IMPACT_COMPLEX|LATERAL_ANKLE_STABILITY_COMPLEX|MEDIAL_ANKLE_ARCH_COMPLEX'
    'BADMINTON_REACTIVE_LUNGE_FOOTWORK' = 'BADMINTON_FORWARD_LUNGE|KNEE_EXTENSOR_CONTACT_COMPLEX|KNEE_MENISCAL_CARTILAGE_COMPLEX|CRUCIATE_LIGAMENT_COMPLEX|CORONAL_ROTATIONAL_KNEE_COMPLEX|POSTERIOR_KNEE_HAMSTRING_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX|LATERAL_ANKLE_STABILITY_COMPLEX|SYNDESMOTIC_ROTATIONAL_COMPLEX|MEDIAL_ANKLE_ARCH_COMPLEX'
    'BALLISTIC_HINGE_POWER' = 'HINGE|POSTERIOR_KNEE_HAMSTRING_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX'
    'BODYWEIGHT_CONDITIONING_POWER' = 'VERTICAL_JUMP|KNEE_EXTENSOR_CONTACT_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX'
    'CALF_RAISE_ANKLE_STIFFNESS_VARIANTS' = 'CALF_RAISE|ANKLE_PROPULSION_IMPACT_COMPLEX|MEDIAL_ANKLE_ARCH_COMPLEX'
    'DEADLIFT_HINGE_VARIANTS' = 'HINGE|POSTERIOR_KNEE_HAMSTRING_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX'
    'FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS' = 'BADMINTON_FOOTWORK|CRUCIATE_LIGAMENT_COMPLEX|CORONAL_ROTATIONAL_KNEE_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX|LATERAL_ANKLE_STABILITY_COMPLEX|SYNDESMOTIC_ROTATIONAL_COMPLEX|MEDIAL_ANKLE_ARCH_COMPLEX'
    'HAMSTRING_CURL_VARIANTS' = 'KNEE_FLEXION|POSTERIOR_KNEE_HAMSTRING_COMPLEX'
    'HIP_THRUST_GLUTE_BRIDGE_VARIANTS' = 'HINGE|POSTERIOR_KNEE_HAMSTRING_COMPLEX'
    'JUMP_LUNGE_PLYOMETRIC' = 'JUMP_LUNGE|KNEE_EXTENSOR_CONTACT_COMPLEX|CRUCIATE_LIGAMENT_COMPLEX|CORONAL_ROTATIONAL_KNEE_COMPLEX|POSTERIOR_KNEE_HAMSTRING_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX|LATERAL_ANKLE_STABILITY_COMPLEX'
    'KNEE_DOMINANT_ISOMETRIC' = 'KNEE_EXTENSION|KNEE_EXTENSOR_CONTACT_COMPLEX|KNEE_MENISCAL_CARTILAGE_COMPLEX'
    'KNEE_EXTENSION_QUAD_ISOLATION' = 'KNEE_EXTENSION|KNEE_EXTENSOR_CONTACT_COMPLEX'
    'LATERAL_BOUND_LANDING_DECELERATION_VARIANTS' = 'LATERAL_HOP|KNEE_EXTENSOR_CONTACT_COMPLEX|CRUCIATE_LIGAMENT_COMPLEX|CORONAL_ROTATIONAL_KNEE_COMPLEX|POSTERIOR_KNEE_HAMSTRING_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX|LATERAL_ANKLE_STABILITY_COMPLEX|SYNDESMOTIC_ROTATIONAL_COMPLEX'
    'LEG_PRESS_KNEE_DOMINANT_MACHINE_VARIANTS' = 'SQUAT|KNEE_EXTENSOR_CONTACT_COMPLEX|KNEE_MENISCAL_CARTILAGE_COMPLEX|CRUCIATE_LIGAMENT_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX'
    'LUNGE_SPLIT_SQUAT_VARIANTS' = 'LUNGE|KNEE_EXTENSOR_CONTACT_COMPLEX|KNEE_MENISCAL_CARTILAGE_COMPLEX|CRUCIATE_LIGAMENT_COMPLEX|CORONAL_ROTATIONAL_KNEE_COMPLEX|POSTERIOR_KNEE_HAMSTRING_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX|LATERAL_ANKLE_STABILITY_COMPLEX|MEDIAL_ANKLE_ARCH_COMPLEX'
    'NORDIC_HAMSTRING_ECCENTRIC' = 'KNEE_FLEXION|POSTERIOR_KNEE_HAMSTRING_COMPLEX'
    'PLYOMETRIC_JUMP_VARIANTS' = 'VERTICAL_JUMP|KNEE_EXTENSOR_CONTACT_COMPLEX|CRUCIATE_LIGAMENT_COMPLEX|CORONAL_ROTATIONAL_KNEE_COMPLEX|POSTERIOR_KNEE_HAMSTRING_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX|LATERAL_ANKLE_STABILITY_COMPLEX'
    'POSTERIOR_CHAIN_LOW_LOAD_CONTROL' = 'HINGE|POSTERIOR_KNEE_HAMSTRING_COMPLEX'
    'QUAD_ECCENTRIC_KNEE_DOMINANT' = 'KNEE_EXTENSION|KNEE_EXTENSOR_CONTACT_COMPLEX|KNEE_MENISCAL_CARTILAGE_COMPLEX'
    'RUNNING_MECHANICS_SUPPORT' = 'RUNNING|KNEE_EXTENSOR_CONTACT_COMPLEX|POSTERIOR_KNEE_HAMSTRING_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX|LATERAL_ANKLE_STABILITY_COMPLEX|MEDIAL_ANKLE_ARCH_COMPLEX'
    'SPRINT_ACCELERATION_CONDITIONING' = 'RUNNING|KNEE_EXTENSOR_CONTACT_COMPLEX|POSTERIOR_KNEE_HAMSTRING_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX'
    'SQUAT_VARIANTS' = 'SQUAT|KNEE_EXTENSOR_CONTACT_COMPLEX|KNEE_MENISCAL_CARTILAGE_COMPLEX|CRUCIATE_LIGAMENT_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX'
    'STEP_UP_VARIANTS' = 'STEP_UP|KNEE_EXTENSOR_CONTACT_COMPLEX|KNEE_MENISCAL_CARTILAGE_COMPLEX|CRUCIATE_LIGAMENT_COMPLEX|ANKLE_PROPULSION_IMPACT_COMPLEX|MEDIAL_ANKLE_ARCH_COMPLEX'
}

$movementRegistry = @($catalog.movementFamily | Sort-Object -Unique | ForEach-Object {
    $canonical = $_; $parts = @($familyMap[$canonical] -split '\|')
    $mapped = $familyMap.ContainsKey($canonical)
    [pscustomobject][ordered]@{
        canonicalMovementFamily=$canonical
        biomechanicalMovementFamily=if($mapped){$parts[0]}else{'NOT_APPLICABLE'}
        applicabilityComplexIds=if($mapped){($parts | Select-Object -Skip 1) -join '|'}else{''}
        mappingStatus=if($mapped){'AUDITED_APPLICABLE'}else{'AUDITED_NOT_APPLICABLE'}
        mappingBasis='EXACT_CANONICAL_MOVEMENT_FAMILY'; version='1.0.0'
    }
})
Write-Csv 'tissue_mtc_movement_family_registry_v1.csv' $movementRegistry
$movementByCanonical = @{}; $movementRegistry | ForEach-Object { $movementByCanonical[$_.canonicalMovementFamily]=$_ }

$exerciseMappings = @($catalog | ForEach-Object {
    $family = $movementByCanonical[$_.movementFamily]
    [pscustomobject][ordered]@{
        stableKey=$_.stableKey; canonicalMovementFamily=$_.movementFamily
        biomechanicalMovementFamily=$family.biomechanicalMovementFamily; mappingStatus=$family.mappingStatus
    }
})
Write-Csv 'tissue_mtc_exercise_movement_family_mapping_v1.csv' $exerciseMappings

$applicability = @($exerciseMappings | Where-Object mappingStatus -eq 'AUDITED_APPLICABLE' | ForEach-Object {
    $mapping=$_; $family=$movementByCanonical[$mapping.canonicalMovementFamily]
    @($family.applicabilityComplexIds -split '\|') | ForEach-Object {
        [pscustomobject][ordered]@{
            relationshipId="MTC_REL_$($mapping.stableKey)_$_"; stableKey=$mapping.stableKey; complexId=$_
            applicabilityStatus='APPLICABLE'; mappingBasis="MOVEMENT_FAMILY:$($mapping.biomechanicalMovementFamily)"
            allocationPolicy='PARENT_ONLY_WHEN_COMPLEX_FALLBACK'
        }
    }
})
Write-Csv 'tissue_mtc_exercise_complex_applicability_v1.csv' $applicability

$missingComplexes = @('KNEE_EXTENSOR_CONTACT_COMPLEX','CRUCIATE_LIGAMENT_COMPLEX','CORONAL_ROTATIONAL_KNEE_COMPLEX','ANKLE_PROPULSION_IMPACT_COMPLEX','LATERAL_ANKLE_STABILITY_COMPLEX')
$bridgeAxisRules = foreach($complexId in $missingComplexes){ foreach($axis in @('M','T','C')){
    [pscustomobject][ordered]@{
        axisMetricRuleId="MTC_BRIDGE_$($complexId)_$axis"; targetType='FUNCTIONAL_COMPLEX'; targetId=$complexId
        profileKind='TISSUE_MECHANICAL_LOAD'; axis=$axis
        primaryMetricTypes=if($axis -eq 'M'){'M_COMPLEX_DEMAND_MAGNITUDE'}elseif($axis -eq 'T'){'T_COMPLEX_DEMAND_PROFILE'}else{'C_STRUCTURED_MECHANICAL_CONTEXT'}
        secondaryMetricTypes=''; forbiddenMetricTypes=''; allowedMeasurementFamilies='COMPLEX_ESTIMATE'
        allowedNormalizationBases='UNNORMALIZED_SOURCE_VALUE'; requiredContextFields='SOURCE_CONDITION_ID|MOVEMENT_FAMILY'
        comparisonFamily="${complexId}_${axis}"; rubricEligible='true'; operationalFallbackEligible='true'
        biomechanicalMeaning='Complex-level estimate used only when direct child evidence is unavailable.'
        limitations='Parent-only fallback; never duplicated across every child tissue.'; version='1.0.0'
    }
}}
Write-Csv 'tissue_mtc_bridge_complex_axis_metric_registry_v1.csv' @($bridgeAxisRules)
$bridgeScales = @($bridgeAxisRules | ForEach-Object {
    [pscustomobject][ordered]@{
        axisScaleId="MTC_SCALE_$($_.targetId)_$($_.axis)"; targetId=$_.targetId; axis=$_.axis
        physicalMetricType=$_.primaryMetricTypes; normalizationBasis='UNNORMALIZED_SOURCE_VALUE'; measurementFamily='COMPLEX_ESTIMATE'
        comparisonFamily=$_.comparisonFamily; populationScope='HEALTHY_ADULT_OPERATIONAL'
        conditionFamily='DECLARED_SOURCE_OR_INHERITED_CONDITION'; version='1.0.0'
    }
})
Write-Csv 'tissue_mtc_bridge_axis_scale_registry_v1.csv' $bridgeScales
$bridgeRubrics = foreach($scale in $bridgeScales){ foreach($kind in @('FAMILY_DEFAULT','CONSERVATIVE_FALLBACK')){
    [pscustomobject][ordered]@{
        rubricId="MTC_RUBRIC_$($scale.targetId)_$($scale.axis)_$kind"; axisScaleId=$scale.axisScaleId; rubricKind=$kind
        score=if($kind -eq 'FAMILY_DEFAULT'){'2.0'}else{'2.5'}; lowerBound=''; upperBound=''; lowerInclusive=''; upperInclusive=''; anchorValue=''
        sourceConditionIds=''; sourceIds=''; independentSourceCount='0'; distinctConditionCount='0'; externalValidationSourceIds=''; boundaryDerivation=''
        sensitivityAnalysisStatus='NOT_APPLICABLE_OPERATIONAL_FALLBACK'; researchEligible='false'; operationalOnly='true'; status='DRAFT_NON_PRODUCTION'; version='1.0.0'
    }
}}
Write-Csv 'tissue_mtc_bridge_axis_rubric_v1.csv' @($bridgeRubrics)

$bridgeFallbacks = @(
    @('MTC_BRIDGE_FALLBACK_1_EXACT_CONDITION','1','EXACT_CONDITION','DIRECT_INTERNAL_MEASUREMENT','HIGH'),
    @('MTC_BRIDGE_FALLBACK_2_STABLE_KEY_BASE','2','STABLE_KEY_BASE','CLOSE_CONDITION_ESTIMATE','MODERATE'),
    @('MTC_BRIDGE_FALLBACK_3_CLOSE_VARIANT','3','CLOSE_VARIANT','CLOSE_VARIANT_ESTIMATE','LOW'),
    @('MTC_BRIDGE_FALLBACK_4_MOVEMENT_FAMILY','4','MOVEMENT_FAMILY','MOVEMENT_FAMILY_DEFAULT','LOW'),
    @('MTC_BRIDGE_FALLBACK_5_COMPLEX','5','FUNCTIONAL_COMPLEX','COMPLEX_LEVEL_ESTIMATE','LOW'),
    @('MTC_BRIDGE_FALLBACK_6_CONSERVATIVE','6','CONSERVATIVE_FALLBACK','CONSERVATIVE_FALLBACK','VERY_LOW')
) | ForEach-Object { [pscustomobject][ordered]@{
    fallbackRuleId=$_[0]; priority=$_[1]; inheritanceLevel=$_[2]; matchRequirement=$_[2]; provenanceTier=$_[3]; confidence=$_[4]
    scorePolicy='USE_LINKED_RUBRIC'; forbiddenTransfers='UNKNOWN_TO_ZERO|CROSS_AXIS_SCALE|DISPLAY_NAME_SUBSTRING|TRAINING_ROLE_AS_BIOMECHANICS|PARENT_CHILD_DOUBLE_COUNT'
    allocationPolicy='PARENT_ONLY_WHEN_COMPLEX_FALLBACK'; coefficientSetId=$coefficientSetId; version='1.1.0'
}}
Write-Csv 'tissue_mtc_bridge_fallback_rule_v1.csv' @($bridgeFallbacks)

$familyProfiles = foreach($family in $movementRegistry | Where-Object mappingStatus -eq 'AUDITED_APPLICABLE'){
    foreach($complexId in @($family.applicabilityComplexIds -split '\|')){ foreach($axis in @('M','T','C')){
        [pscustomobject][ordered]@{ profileId="MTC_FAMILY_$($family.biomechanicalMovementFamily)_${complexId}_$axis"; biomechanicalMovementFamily=$family.biomechanicalMovementFamily; complexId=$complexId; axis=$axis; operationalScore='2.0'; provenanceTier='MOVEMENT_FAMILY_DEFAULT'; confidence='LOW'; rubricId="MTC_RUBRIC_${complexId}_${axis}_FAMILY_DEFAULT"; coefficientSetId=$coefficientSetId }
    }}
}
Write-Csv 'tissue_mtc_movement_family_profile_v1.csv' @($familyProfiles)
$complexDefaults = foreach($complexId in $allComplexes){ foreach($axis in @('M','T','C')){ [pscustomobject][ordered]@{ profileId="MTC_COMPLEX_${complexId}_$axis"; complexId=$complexId; axis=$axis; operationalScore='2.0'; provenanceTier='COMPLEX_LEVEL_ESTIMATE'; confidence='LOW'; rubricId="MTC_RUBRIC_${complexId}_${axis}_FAMILY_DEFAULT"; coefficientSetId=$coefficientSetId }}}
Write-Csv 'tissue_mtc_complex_default_profile_v1.csv' @($complexDefaults)
$conservative = foreach($complexId in $allComplexes){ foreach($axis in @('M','T','C')){ [pscustomobject][ordered]@{ profileId="MTC_CONSERVATIVE_${complexId}_$axis"; complexId=$complexId; axis=$axis; operationalScore='2.5'; provenanceTier='CONSERVATIVE_FALLBACK'; confidence='VERY_LOW'; rubricId="MTC_RUBRIC_${complexId}_${axis}_CONSERVATIVE_FALLBACK"; coefficientSetId=$coefficientSetId }}}
Write-Csv 'tissue_mtc_conservative_fallback_profile_v1.csv' @($conservative)

$traces = @($applicability | ForEach-Object { $relationship=$_; $mapping=$exerciseMappings | Where-Object stableKey -eq $relationship.stableKey | Select-Object -First 1; foreach($axis in @('M','T','C')){
    [pscustomobject][ordered]@{
        traceId="MTC_TRACE_$($relationship.relationshipId)_$axis"; relationshipId=$relationship.relationshipId; stableKey=$relationship.stableKey
        complexId=$relationship.complexId; axis=$axis; axisScaleId="MTC_SCALE_$($relationship.complexId)_$axis"; researchScore=''; operationalScore='2.0'
        provenanceTier='MOVEMENT_FAMILY_DEFAULT'; confidence='LOW'; fallbackRuleId='MTC_BRIDGE_FALLBACK_4_MOVEMENT_FAMILY'; inheritanceLevel='MOVEMENT_FAMILY'
        resolutionPath="EXACT_MISS>STABLE_KEY_MISS>VARIANT_MISS>MOVEMENT_FAMILY:$($mapping.biomechanicalMovementFamily)"
        coefficientSetId=$coefficientSetId; resolutionStatus='RESOLVED_OPERATIONAL_RESEARCH_UNKNOWN'
    }
}})
Write-Csv 'tissue_mtc_fallback_resolution_trace_v1.csv' $traces

$modifiers = @('laterality','externalLoadType','additionalExternalLoadFractionBw','totalSystemMassFractionBw','relativeLoadPercent1Rm','externalLoadPlacement','romBand','movementVelocity','impactType','landingStrategy','reboundOrStick','rearFootElevation','direction','dropHeight','surface','expectedOrUnexpectedPerturbation','kneeAngleCondition','ankleAngleCondition') | ForEach-Object {
    [pscustomobject][ordered]@{ modifierId=$_.ToUpperInvariant(); fieldName=$_; valueType='STRUCTURED_SOURCE_CONDITION'; requiredForExactMatch='false'; version='1.0.0' }
}
Write-Csv 'tissue_mtc_condition_modifier_registry_v1.csv' @($modifiers)

Write-Csv 'tissue_mtc_exact_stable_key_override_v1.csv' @([pscustomobject][ordered]@{overrideId='';stableKey='';sourceConditionId='';complexId='';axis='';operationalScore='';researchScore='';provenanceTier='';confidence='';rubricId='';coefficientSetId='';status=''})
Write-Csv 'tissue_mtc_variant_profile_v1.csv' @([pscustomobject][ordered]@{variantProfileId='';sourceStableKey='';targetStableKey='';complexId='';axis='';operationalScore='';approvalStatus='';coefficientSetId=''})
$transferRules = $bridgeFallbacks | ForEach-Object { [pscustomobject][ordered]@{ transferRuleId=$_.fallbackRuleId; priority=$_.priority; inheritanceLevel=$_.inheritanceLevel; stableKeyMatch='EXACT_ONLY'; displayNameMatching='FORBIDDEN'; trainingRoleAsBiomechanicalKey='FORBIDDEN'; coefficientSetId=$coefficientSetId } }
Write-Csv 'tissue_mtc_transfer_rule_v1.csv' @($transferRules)

$baseRubricHash=Semantic-Hash (Join-Path $assetDir 'tissue_mtc_axis_rubric_v1.csv'); $bridgeRubricHash=Semantic-Hash (Join-Path $assetDir 'tissue_mtc_bridge_axis_rubric_v1.csv')
$baseAxisHash=Semantic-Hash (Join-Path $assetDir 'tissue_mtc_axis_metric_registry_v1.csv'); $bridgeAxisHash=Semantic-Hash (Join-Path $assetDir 'tissue_mtc_bridge_complex_axis_metric_registry_v1.csv')
$manifest = [pscustomobject][ordered]@{
    coefficientSetId=$coefficientSetId; semanticVersion='0.1.1'; status='DRAFT_NON_PRODUCTION'; effectiveFrom=''; effectiveTo=''; publishedAt=''
    sourceSnapshotHash=Semantic-Hash (Join-Path $assetDir 'tissue_source_metric_extraction_c3_1_v1.csv')
    rubricSnapshotHash=Combined-Hash @{base=$baseRubricHash;bridge=$bridgeRubricHash}; fallbackPolicyVersion='1.1.0'
    exerciseCatalogSnapshotHash=Semantic-Hash $catalogPath; complexRegistrySnapshotHash=Semantic-Hash (Join-Path $assetDir 'tissue_functional_complex_registry_v1.csv')
    axisRegistrySnapshotHash=Combined-Hash @{base=$baseAxisHash;bridge=$bridgeAxisHash}; supersedesCoefficientSetId='TISSUE_MTC_C4A_0_1_0'
    changeReason='Adds complete functional-complex axis coverage and deterministic canonical-exercise inheritance.'; preparedBy='Codex'; preparedByType='AI_AGENT'
}
Write-Csv 'tissue_mtc_coefficient_set_manifest_bridge_v1.csv' @($manifest)

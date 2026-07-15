param([string]$RepoRoot = (Split-Path -Parent $PSScriptRoot))

$ErrorActionPreference = 'Stop'
$assetDir = Join-Path $RepoRoot 'app/src/main/assets/metadata/tissue_load_v1'
$axisRules = Import-Csv (Join-Path $assetDir 'tissue_mtc_axis_metric_registry_v1.csv')
$coefficientSetId = 'TISSUE_MTC_C4A_0_1_0'

function First-Token([string]$value) { ($value -split '\|')[0] }
function Write-Csv([string]$name, [object[]]$rows) {
    $lines = @($rows | ConvertTo-Csv -NoTypeInformation)
    [IO.File]::WriteAllLines((Join-Path $assetDir $name), $lines, [Text.UTF8Encoding]::new($false))
}
function Semantic-Hash([string]$path) {
    $rows = @(Import-Csv $path)
    $header = @($rows[0].PSObject.Properties.Name)
    $encodedRows = @($rows | ForEach-Object {
        $row = $_
        ($header | ForEach-Object { [string]$row.$_ }) -join [char]31
    } | Sort-Object)
    $payload = ($header -join [char]31) + "`n" + ($encodedRows -join [char]30)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($payload)) | ForEach-Object ToString x2) -join '' }
    finally { $sha.Dispose() }
}

$scales = @($axisRules | ForEach-Object {
    [pscustomobject][ordered]@{
        axisScaleId = "MTC_SCALE_$($_.targetId)_$($_.axis)"
        targetId = $_.targetId
        axis = $_.axis
        physicalMetricType = First-Token $_.primaryMetricTypes
        normalizationBasis = First-Token $_.allowedNormalizationBases
        measurementFamily = First-Token $_.allowedMeasurementFamilies
        comparisonFamily = $_.comparisonFamily
        populationScope = 'HEALTHY_ADULT_OPERATIONAL'
        conditionFamily = 'DECLARED_SOURCE_OR_INHERITED_CONDITION'
        version = '1.0.0'
    }
})
Write-Csv 'tissue_mtc_axis_scale_registry_v1.csv' $scales

$rubrics = foreach ($scale in $scales) {
    foreach ($kind in @('FAMILY_DEFAULT', 'CONSERVATIVE_FALLBACK')) {
        $score = if ($kind -eq 'FAMILY_DEFAULT') { '2.0' } else { '2.5' }
        [pscustomobject][ordered]@{
            rubricId = "MTC_RUBRIC_$($scale.targetId)_$($scale.axis)_$kind"
            axisScaleId = $scale.axisScaleId
            rubricKind = $kind
            score = $score
            lowerBound = ''; upperBound = ''; lowerInclusive = ''; upperInclusive = ''; anchorValue = ''
            sourceConditionIds = ''; sourceIds = ''; independentSourceCount = '0'; distinctConditionCount = '0'
            externalValidationSourceIds = ''; boundaryDerivation = ''
            sensitivityAnalysisStatus = 'NOT_APPLICABLE_OPERATIONAL_FALLBACK'
            researchEligible = 'false'; operationalOnly = 'true'; status = 'DRAFT_NON_PRODUCTION'; version = '1.0.0'
        }
    }
}
Write-Csv 'tissue_mtc_axis_rubric_v1.csv' @($rubrics)

$fallbacks = @(
    @('MTC_FALLBACK_1_EXACT_CONDITION','1','EXACT_CONDITION','Exact stableKey and exact source condition','DIRECT_INTERNAL_MEASUREMENT','HIGH','USE_EXACT_RESEARCH_OR_OPERATIONAL_SCORE'),
    @('MTC_FALLBACK_2_STABLE_KEY_BASE','2','STABLE_KEY_BASE','Same stableKey with compatible load or ROM condition','CLOSE_CONDITION_ESTIMATE','MODERATE','USE_COMPATIBLE_CONDITION_SCORE'),
    @('MTC_FALLBACK_3_CLOSE_VARIANT','3','CLOSE_VARIANT','Approved close exercise variant','CLOSE_VARIANT_ESTIMATE','LOW','USE_APPROVED_TRANSFER_RULE'),
    @('MTC_FALLBACK_4_COMPLEX','4','FUNCTIONAL_COMPLEX','Applicable functional-complex estimate','COMPLEX_LEVEL_ESTIMATE','LOW','USE_COMPLEX_DEFAULT'),
    @('MTC_FALLBACK_5_MOVEMENT_FAMILY','5','MOVEMENT_FAMILY','Exact registered movement-family default','MOVEMENT_FAMILY_DEFAULT','LOW','USE_FAMILY_DEFAULT_RUBRIC'),
    @('MTC_FALLBACK_6_CONSERVATIVE','6','CONSERVATIVE_FALLBACK','Final conservative non-null fallback','CONSERVATIVE_FALLBACK','VERY_LOW','USE_CONSERVATIVE_FALLBACK_RUBRIC')
) | ForEach-Object {
    [pscustomobject][ordered]@{
        fallbackRuleId=$_[0]; priority=$_[1]; inheritanceLevel=$_[2]; matchRequirement=$_[3]
        provenanceTier=$_[4]; confidence=$_[5]; scorePolicy=$_[6]
        forbiddenTransfers='UNKNOWN_TO_ZERO|CROSS_AXIS_SCALE|UNAPPROVED_METHOD_CONVERSION|PARENT_CHILD_DOUBLE_COUNT'
        allocationPolicy='PARENT_ONLY_WHEN_COMPLEX_FALLBACK'; coefficientSetId=$coefficientSetId; version='1.0.0'
    }
}
Write-Csv 'tissue_mtc_fallback_rule_v1.csv' @($fallbacks)

$provenance = @($scales | ForEach-Object {
    [pscustomobject][ordered]@{
        provenanceId="MTC_PROVENANCE_$($_.targetId)_$($_.axis)_FAMILY_DEFAULT"
        axisScaleId=$_.axisScaleId; score='2.0'; researchScore=''; operationalScore='2.0'
        provenanceTier='MOVEMENT_FAMILY_DEFAULT'; confidence='LOW'; sourceIds=''; metricExtractionIds=''
        rubricId="MTC_RUBRIC_$($_.targetId)_$($_.axis)_FAMILY_DEFAULT"
        fallbackRuleId='MTC_FALLBACK_5_MOVEMENT_FAMILY'; inheritanceLevel='MOVEMENT_FAMILY'
        limitations='Operational family default only; research score remains UNKNOWN and is not zero.'
        coefficientSetId=$coefficientSetId
    }
})
Write-Csv 'tissue_mtc_axis_provenance_v1.csv' $provenance

$manifest = [pscustomobject][ordered]@{
    coefficientSetId=$coefficientSetId; semanticVersion='0.1.0'; status='DRAFT_NON_PRODUCTION'
    effectiveFrom=''; effectiveTo=''; publishedAt=''
    sourceSnapshotHash=Semantic-Hash (Join-Path $assetDir 'tissue_source_metric_extraction_c3_1_v1.csv')
    rubricSnapshotHash=Semantic-Hash (Join-Path $assetDir 'tissue_mtc_axis_rubric_v1.csv')
    fallbackPolicyVersion='1.0.0'
    exerciseCatalogSnapshotHash=Semantic-Hash (Join-Path $assetDir '../canonical_exercise_metadata_v0_3_5_0_pass3_1.csv')
    complexRegistrySnapshotHash=Semantic-Hash (Join-Path $assetDir 'tissue_functional_complex_registry_v1.csv')
    axisRegistrySnapshotHash=Semantic-Hash (Join-Path $assetDir 'tissue_mtc_axis_metric_registry_v1.csv')
    supersedesCoefficientSetId=''; changeReason='Initial non-production C4A M/T/C scale and fallback foundation.'
    preparedBy='Codex'; preparedByType='AI_AGENT'
}
Write-Csv 'tissue_mtc_coefficient_set_manifest_v1.csv' @($manifest)

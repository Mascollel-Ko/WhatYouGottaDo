param(
    [string]$RepoRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$generator = Join-Path $RepoRoot "tools/generate_metadata_analysis_contract_audit.ps1"
$generated = @(
    "docs/audits/metadata_field_usage_matrix.csv",
    "docs/audits/metadata_field_usage_matrix.md",
    "docs/audits/metadata_parsing_inference_audit.csv",
    "docs/audits/metadata_parsing_inference_audit.md",
    "docs/audits/metadata_legacy_to_target_mapping_matrix.csv",
    "docs/audits/metadata_legacy_to_target_mapping_matrix.md",
    "docs/audits/metadata_mapping_semantic_review.csv",
    "docs/audits/metadata_mapping_semantic_review.md",
    "docs/audits/metadata_legacy_compatibility_consumers.csv",
    "docs/audits/metadata_legacy_compatibility_consumers.md",
    "docs/audits/metadata_legacy_inference_risk_paths.csv",
    "docs/audits/metadata_legacy_inference_risk_paths.md",
    "docs/audits/metadata_inference_stablekey_impact.csv",
    "docs/audits/metadata_inference_stablekey_impact.md",
    "docs/audits/confirmed_metadata_errors.csv",
    "docs/audits/confirmed_metadata_errors.md",
    "docs/audits/training_role_whitelist_reconstruction.csv",
    "docs/audits/family_id_consumer_inventory.csv",
    "docs/audits/load_profile_consumer_inventory.csv",
    "docs/audits/metadata_taxonomy_decision_matrix.csv",
    "docs/audits/metadata_taxonomy_decision_matrix.md"
)

& $generator -RepoRoot $RepoRoot
$firstHashes = @{}
foreach ($relative in $generated) {
    $path = Join-Path $RepoRoot $relative
    Assert-True (Test-Path -LiteralPath $path) "Missing generated audit: $relative"
    $firstHashes[$relative] = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
}
& $generator -RepoRoot $RepoRoot
foreach ($relative in $generated) {
    $secondHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot $relative)).Hash
    Assert-True ($secondHash -eq $firstHashes[$relative]) "Generator is not deterministic: $relative"
}

$usage = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/metadata_field_usage_matrix.csv"))
$mapping = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/metadata_legacy_to_target_mapping_matrix.csv"))
$compatibility = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/metadata_legacy_compatibility_consumers.csv"))
$risks = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/metadata_legacy_inference_risk_paths.csv"))
$impact = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/metadata_inference_stablekey_impact.csv"))
$confirmed = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/confirmed_metadata_errors.csv"))
$trainingRoles = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/training_role_whitelist_reconstruction.csv"))
$familyInventory = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/family_id_consumer_inventory.csv"))
$loadInventory = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/load_profile_consumer_inventory.csv"))
$taxonomy = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/metadata_taxonomy_decision_matrix.csv"))

$mappingColumns = @(
    "legacyField", "storageLocation", "currentProducer", "consumerFile", "consumerSymbol",
    "consumerKind", "rawOrTokenMeaning", "consumerSemanticUse", "currentDisposition",
    "eventualReplacementStrategy", "targetLayer", "targetRelation", "conversionMode",
    "derivationMode", "mappingStatus", "reviewEvidence", "linkedRiskPathIds", "notes"
)
$impactColumns = @(
    "riskPathId", "exerciseStableKey", "evaluationApplicability", "rawSourceValue", "rawValuePresent",
    "fallbackTriggered", "fallbackInput", "fallbackOutput", "currentEffectiveValueOrRelation",
    "affectedConsumer", "affectedAnalysis", "baselineObservableOutput", "counterfactualAvailable",
    "outputWithoutFallback", "actualOutputDifference", "reviewClassification", "evidence", "notes"
)
$confirmedColumns = @(
    "issueId", "riskPathId", "exerciseStableKey", "legacyField", "currentValueOrRelation",
    "expectedValueOrRelation", "authorityOrEvidence", "affectedConsumers", "affectedAnalyses",
    "programImpact", "ofiImpact", "muscleImpact", "badmintonImpact", "tissueImpact",
    "strengthPerformanceImpact", "parityImpact", "severity", "approvalStatus",
    "proposedResolution", "targetVersion"
)
Assert-True ($usage.Count -eq 102) "Expected 102 field/storage rows, found $($usage.Count)."
Assert-True ($mapping[0].PSObject.Properties.Name.Count -eq $mappingColumns.Count) "Unexpected mapping column count."
Assert-True (@($mappingColumns | Where-Object { $_ -notin $mapping[0].PSObject.Properties.Name }).Count -eq 0) "Missing required mapping columns."
Assert-True (@($impactColumns | Where-Object { $_ -notin $impact[0].PSObject.Properties.Name }).Count -eq 0) "Missing required impact columns."
$confirmedHeader = (Get-Content -LiteralPath (Join-Path $RepoRoot "docs/audits/confirmed_metadata_errors.csv") -TotalCount 1) -replace '"', '' -split ','
Assert-True (@($confirmedColumns | Where-Object { $_ -notin $confirmedHeader }).Count -eq 0) "Missing required confirmed-error columns."

foreach ($row in $usage) {
    Assert-True (@($mapping | Where-Object { $_.legacyField -eq $row.fieldName -and $_.storageLocation -eq $row.storageLocation }).Count -gt 0) `
        "Missing mapping for $($row.storageLocation).$($row.fieldName)."
}

$semanticUses = @(
    "FIXED_EXERCISE_CLASSIFICATION", "FIXED_EXERCISE_RELATION", "FIXED_PROGRAM_PARAMETER",
    "FIXED_ANALYSIS_PARAMETER", "PROGRAM_POLICY", "RECORD_OR_INPUT_PROTOCOL", "PRESENTATION_ONLY",
    "PROVENANCE_ONLY", "LEGACY_COMPATIBILITY", "UNRESOLVED"
)
$mappingStatuses = @("AUTO_CANDIDATE", "SEMANTICALLY_REVIEWED", "APPROVED", "REJECTED", "UNRESOLVED")
Assert-True (@($mapping | Where-Object { $_.consumerSemanticUse -notin $semanticUses }).Count -eq 0) "Invalid semantic-use category."
Assert-True (@($mapping | Where-Object { $_.mappingStatus -notin $mappingStatuses }).Count -eq 0) "Invalid mapping status."
Assert-True (@($mapping | Where-Object mappingStatus -eq "APPROVED").Count -eq 0) `
    "Phase 2A must not promote candidate or unresolved mappings to APPROVED."

$defaultRest = @($mapping | Where-Object legacyField -eq "defaultRestSeconds")
Assert-True ($defaultRest.Count -gt 0) "Missing defaultRestSeconds mappings."
Assert-True (@($defaultRest | Where-Object {
    $_.consumerSemanticUse -ne "FIXED_PROGRAM_PARAMETER" -or
    $_.targetLayer -ne "PROGRAM_GENERATION" -or
    $_.targetRelation -ne "ExerciseProgramTimingProfile" -or
    $_.conversionMode -ne "DIRECT_COPY" -or
    $_.mappingStatus -ne "SEMANTICALLY_REVIEWED"
}).Count -eq 0) "defaultRestSeconds mapping is not the reviewed timing profile."

$activity = @($mapping | Where-Object legacyField -eq "activityKind")
Assert-True ($activity.Count -gt 0) "Missing activityKind mappings."
Assert-True (@($activity | Where-Object {
    $_.currentDisposition -ne "LEGACY_COMPATIBILITY_READONLY" -or
    $_.targetLayer -ne "NON_METADATA_LEGACY_COMPATIBILITY" -or
    $_.targetRelation -ne "NONE" -or
    $_.consumerSemanticUse -ne "LEGACY_COMPATIBILITY"
}).Count -eq 0) "activityKind was promoted into target movement/anatomy metadata."

$progress = @($mapping | Where-Object legacyField -eq "progressMetricType")
Assert-True ($progress.Count -gt 0) "Missing progressMetricType mappings."
Assert-True (@($progress | Where-Object {
    $_.targetRelation -ne "NONE" -or
    $_.currentDisposition -ne "LEGACY_COMPATIBILITY_READONLY" -or
    $_.eventualReplacementStrategy -ne "REPLACE_OUTSIDE_CANONICAL_METADATA_AFTER_PARITY"
}).Count -eq 0) "progressMetricType was mapped into target canonical metadata."
Assert-True (@($compatibility | Where-Object legacyField -eq "progressMetricType").Count -gt 0) "Missing progressMetricType compatibility consumers."

$analysisEligibility = @($mapping | Where-Object legacyField -eq "analysisEligibility")
Assert-True (@($analysisEligibility.targetRelation | Sort-Object -Unique).Count -gt 2) "analysisEligibility still has a field-wide target."
Assert-True (@($analysisEligibility | Where-Object mappingStatus -eq "APPROVED").Count -eq 0) "analysisEligibility candidates were silently approved."

Assert-True ($trainingRoles.Count -eq 26) "Expected 26 approved legacy training-role rows."
Assert-True (@($trainingRoles | Where-Object reconstructionStatus -ne "CONFIRMED_EXPLICIT").Count -eq 0) "Training roles contain unapproved inference."
Assert-True (@($familyInventory | Where-Object { $_.targetLayer -ne "DERIVED_NONCANONICAL" -or $_.targetRelation -ne "NONE" }).Count -eq 0) `
    "familyId was promoted into target canonical metadata."
Assert-True (@($loadInventory | Where-Object { $_.targetLayer -ne "LEGACY_COMPOSITE_TO_BE_DECOMPOSED" -or $_.targetRelation -ne "NONE" }).Count -eq 0) `
    "loadProfile was promoted into target canonical metadata."
$closedWorldImpact = @($impact | Where-Object riskPathId -in @("META-SEED-TRAINING-ROLE", "META-SEED-SPORT-TRANSFER"))
Assert-True (@($closedWorldImpact | Where-Object reviewClassification -eq "MISSING_AUTHORITY").Count -eq 0) `
    "Closed-world relation absence was treated as missing authority."
Assert-True (@($closedWorldImpact | Where-Object { $_.rawValuePresent -eq "FALSE" -and $_.outputWithoutFallback -ne "AUTHORITATIVE_NONE" }).Count -eq 0) `
    "Closed-world blank relation is not authoritative NONE."
$usageConcepts = @($usage.fieldName | Sort-Object -Unique)
Assert-True ($taxonomy.Count -eq $usageConcepts.Count) "Taxonomy matrix does not cover every distinct current concept."
Assert-True (@($usageConcepts | Where-Object { $_ -notin $taxonomy.currentConcept }).Count -eq 0) "Taxonomy matrix is missing current concepts."

Assert-True ($risks.Count -eq 20) "Expected 20 risk paths, found $($risks.Count)."
Assert-True ($impact.Count -eq 4480) "Expected 4,480 impact rows, found $($impact.Count)."
Assert-True (@($impact.exerciseStableKey | Sort-Object -Unique).Count -eq 224) "Expected 224 impacted stableKeys."
Assert-True (@($impact | Group-Object riskPathId, exerciseStableKey | Where-Object Count -ne 1).Count -eq 0) "Risk/stableKey pairs are incomplete or duplicated."
$applicability = @("APPLICABLE", "NOT_APPLICABLE", "USER_EXERCISE_ONLY", "UNOBSERVABLE_WITHOUT_PRODUCTION_CHANGE")
$classifications = @("NOT_TRIGGERED_FOR_BUILT_INS", "VALID_RESULT_BUT_HEURISTIC_IMPLEMENTATION", "CONFIRMED_CLASSIFICATION_ERROR", "STRUCTURAL_AMBIGUITY", "MISSING_AUTHORITY", "USER_EXERCISE_ONLY_RISK", "UNRESOLVED")
Assert-True (@($impact | Where-Object evaluationApplicability -notin $applicability).Count -eq 0) "Invalid impact applicability."
Assert-True (@($impact | Where-Object reviewClassification -notin $classifications).Count -eq 0) "Invalid impact classification."

$riskIds = @{}; foreach ($risk in $risks) { $riskIds[$risk.riskPathId] = $true }
foreach ($error in $confirmed) {
    Assert-True ($riskIds.ContainsKey($error.riskPathId)) "Confirmed error lacks a risk path: $($error.issueId)."
    Assert-True (-not [string]::IsNullOrWhiteSpace($error.exerciseStableKey)) "Confirmed error lacks exact stableKey evidence."
    Assert-True (@($impact | Where-Object { $_.riskPathId -eq $error.riskPathId -and $_.exerciseStableKey -eq $error.exerciseStableKey -and $_.reviewClassification -eq "CONFIRMED_CLASSIFICATION_ERROR" }).Count -eq 1) `
        "Confirmed error is not backed by one exact impact row: $($error.issueId)."
}
Assert-True ($confirmed.Count -eq @($impact | Where-Object reviewClassification -eq "CONFIRMED_CLASSIFICATION_ERROR").Count) `
    "Risk paths and confirmed errors were conflated."

$baseline = Join-Path $RepoRoot "app/src/main/assets/metadata/analysis_contract_baseline_v1.csv"
Assert-True ((Get-FileHash -Algorithm SHA256 -LiteralPath $baseline).Hash -eq "6B0CBDEC60A38FCAFA1AA957BD8335EF9D3930175CF6E723E1A9D8265F384E52") `
    "ANALYSIS_CONTRACT_BASELINE_V1 changed."
$baselineRows = @(Import-Csv -LiteralPath $baseline)
Assert-True ((Get-Item -LiteralPath $baseline).Length -eq 1092904) "ANALYSIS_CONTRACT_BASELINE_V1 byte size changed."
Assert-True ($baselineRows.Count -eq 9781) "Expected 9,781 baseline relation rows, found $($baselineRows.Count)."
Assert-True (@($baselineRows.exerciseStableKey | Sort-Object -Unique).Count -eq 224) "Expected 224 baseline stableKeys."

$contractHashes = @{
    "AnalysisContractAssetLoader.kt" = "7D6762652BADC3A240DD53719A63C873DCA1E50E84A47A3A58087CA10A05FC85"
    "AnalysisContractModels.kt" = "835E15E87ECABA9B1FEDE4514F8E845584FF688AFCB8E76BF60B03A0BA01413E"
    "AnalysisContractShadowParity.kt" = "672FF99DA41415E309E093DA19C6D175FA9D157A8349F10A18E33E2B55610BEB"
    "UserExerciseAnalysisContractProjector.kt" = "82CD167EE83C74952AB8953B1BAFE82F74CF753AFA1007E455A0A28740318510"
}
$contractRoot = Join-Path $RepoRoot "app/src/main/java/com/training/trackplanner/analysis/contracts"
foreach ($entry in $contractHashes.GetEnumerator()) {
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $contractRoot $entry.Key)).Hash
    Assert-True ($actual -eq $entry.Value) "Phase 0/1 contract Kotlin changed: $($entry.Key)."
}

Write-Host "Metadata v2.3 Phase 2A.1 audit gates passed: $($usage.Count) fields, $($mapping.Count) mappings, $($risks.Count) risks, $($impact.Count) impact rows, $($confirmed.Count) confirmed errors."

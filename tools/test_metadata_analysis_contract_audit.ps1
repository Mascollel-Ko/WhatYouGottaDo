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
    "docs/audits/family_id_consumer_inventory.csv",
    "docs/audits/load_profile_consumer_inventory.csv",
    "docs/audits/metadata_taxonomy_decision_matrix.csv",
    "docs/audits/metadata_taxonomy_decision_matrix.md",
    "docs/audits/metadata_existing_owner_capability_audit.csv",
    "docs/audits/metadata_existing_owner_capability_audit.md",
    "docs/audits/force_type_token_audit.csv",
    "docs/audits/force_type_token_audit.md",
    "docs/audits/trunk_brace_decomposition_audit.csv",
    "docs/audits/trunk_brace_decomposition_audit.md",
    "docs/audits/metadata_information_preservation_audit.csv",
    "docs/audits/metadata_information_preservation_audit.md",
    "docs/audits/metadata_normalization_shadow_parity_report.md"
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
$owners = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/metadata_existing_owner_capability_audit.csv"))
$forceTokens = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/force_type_token_audit.csv"))
$trunk = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/trunk_brace_decomposition_audit.csv"))
$information = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/metadata_information_preservation_audit.csv"))
$normalizationParity = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/metadata_normalization_shadow_parity_241.csv"))

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
Assert-True ($usage.Count -eq 101) "Expected 101 current field/storage rows, found $($usage.Count)."
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

Assert-True ($trainingRoles.Count -eq 26) `
    "Expected the frozen 26-row historical trainingRole reconstruction baseline."
$canonicalTrainingRoles = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "app/src/main/assets/metadata/canonical_v1/training_roles.csv"))
$historyTrainingRoles = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "app/src/main/assets/metadata/canonical_v1/history_training_roles.csv"))
Assert-True ($canonicalTrainingRoles.Count -eq 18) "Expected 18 canonical training-role relations."
Assert-True ($historyTrainingRoles.Count -eq 1) "Expected one history-only training-role relation."
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

Assert-True ($owners.Count -eq 26) "Expected 26 existing-owner audit rows, found $($owners.Count)."
Assert-True (@($owners | Where-Object recommendedDisposition -eq "NEW_OWNER_REQUIRED").Count -eq 0) `
    "Normalization introduced a new canonical owner without passing the owner gate."
Assert-True (@($owners | Where-Object {
    [string]::IsNullOrWhiteSpace($_.existingCandidateOwner1) -or
    [string]::IsNullOrWhiteSpace($_.recommendedDisposition)
}).Count -eq 0) "Existing-owner audit contains incomplete decisions."

Assert-True ($forceTokens.Count -eq 20) "Expected the 20-token forceType registry/runtime union."
Assert-True (@($forceTokens | Where-Object classification -eq "CURRENT_CANONICAL").Count -eq 12) `
    "Expected 12 registered current forceType values."
Assert-True (@($forceTokens | Where-Object classification -eq "CURRENT_NONCANONICAL_RUNTIME").Count -eq 8) `
    "Expected eight current runtime compatibility values."
Assert-True (@($forceTokens | Where-Object classification -in @("UNKNOWN", "DEAD_CODE_ONLY")).Count -eq 0) `
    "forceType audit left unknown or dead-only values."
Assert-True (@($forceTokens | Where-Object acceptedByRestore -ne "YES_FIELD_PRESERVED").Count -eq 0) `
    "Historical forceType restore compatibility is incomplete."

Assert-True ($trunk.Count -eq 21) "Expected 21 reviewed trunk normalization rows."
Assert-True (@($trunk | Where-Object badmintonAntiRotationImplied -ne "NO").Count -eq 0) `
    "Intrinsic bracing still implies badminton anti-rotation."
Assert-True (@($trunk | Where-Object informationStatus -ne "LOSSLESS_WITH_EXISTING_OWNER_EXTENSION").Count -eq 0) `
    "Trunk normalization is not lossless."
Assert-True (@($trunk | Where-Object { $_.normalizedRelations -split '\|' -contains 'AXIAL_BRACING' }).Count -eq 10) `
    "Unexpected AXIAL_BRACING count."
Assert-True (@($trunk | Where-Object { $_.normalizedRelations -split '\|' -contains 'ANTI_ROTATION' }).Count -eq 5) `
    "Unexpected ANTI_ROTATION count."
Assert-True (@($trunk | Where-Object { $_.normalizedRelations -split '\|' -contains 'ANTI_LATERAL_FLEXION' }).Count -eq 2) `
    "Unexpected ANTI_LATERAL_FLEXION count."
Assert-True (@($trunk | Where-Object { $_.normalizedRelations -split '\|' -contains 'ANTI_EXTENSION' }).Count -eq 4) `
    "Unexpected ANTI_EXTENSION count."
Assert-True (@($trunk | Where-Object { $_.normalizedRelations -split '\|' -contains 'DYNAMIC_TRUNK_STABILIZATION' }).Count -eq 3) `
    "Unexpected DYNAMIC_TRUNK_STABILIZATION count."
Assert-True (@($trunk | Where-Object multiLabel -eq "YES").Count -eq 3) "Expected three multi-label trunk rows."
$movementRelations = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "app/src/main/assets/metadata/canonical_v1/movement_relations.csv"))
Assert-True (@($movementRelations | Where-Object relationValue -eq "TRUNK_BRACE").Count -eq 0) `
    "TRUNK_BRACE remains a canonical movement relation."

Assert-True ($normalizationParity.Count -eq 241) "Expected 241 canonical identity parity rows."
Assert-True (@($normalizationParity | Where-Object decision -in @("CANONICAL_GAP", "INFORMATION_LOSS", "AMBIGUOUS")).Count -eq 0) `
    "Blocking normalization parity rows remain."
Assert-True (@($normalizationParity | Where-Object { $_.currentFatigueCost -ne $_.normalizedFatigueCost }).Count -eq 0) `
    "Badminton fatigueCost changed during normalization."
Assert-True (@($normalizationParity | Where-Object {
    $_.currentRelevantOfiSignals -ne $_.normalizedRelevantOfiSignals -or
    $_.currentRelevantProgramClassification -ne $_.normalizedRelevantProgramClassification -or
    $_.currentStrengthClassification -ne $_.normalizedStrengthClassification
}).Count -eq 0) "An unrelated normalized consumer changed."

Assert-True ($information.Count -eq 262) "Expected 262 information-preservation rows."
Assert-True (@($information | Where-Object informationStatus -in @("INFORMATION_LOSS", "SEMANTIC_EXPANSION", "AMBIGUOUS")).Count -eq 0) `
    "Blocking information-preservation rows remain."
Assert-True (@($information | Where-Object unsupportedNewFacts -ne "NONE").Count -eq 0) `
    "Normalization added unsupported semantic facts."

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

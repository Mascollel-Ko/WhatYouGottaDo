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
    "docs/audits/metadata_legacy_compatibility_consumers.csv",
    "docs/audits/metadata_legacy_compatibility_consumers.md",
    "docs/audits/metadata_migration_issue_ledger.csv",
    "docs/audits/metadata_migration_issue_ledger.md"
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
$issues = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "docs/audits/metadata_migration_issue_ledger.csv"))

Assert-True ($usage.Count -eq 102) "Expected 102 field/storage rows, found $($usage.Count)."
foreach ($row in $usage) {
    Assert-True (@($mapping | Where-Object { $_.legacyField -eq $row.fieldName -and $_.storageOwner -eq $row.storageLocation }).Count -gt 0) `
        "Missing mapping for $($row.storageLocation).$($row.fieldName)."
}

$progressUsage = @($usage | Where-Object fieldName -eq "progressMetricType")
Assert-True ($progressUsage.Count -eq 2) "Expected both progressMetricType storage owners."
Assert-True (@($progressUsage | Where-Object currentDisposition -ne "LEGACY_COMPATIBILITY_READONLY").Count -eq 0) `
    "progressMetricType must remain LEGACY_COMPATIBILITY_READONLY."
Assert-True (@($progressUsage | Where-Object eventualReplacementStrategy -ne "REPLACE_OUTSIDE_CANONICAL_METADATA_AFTER_PARITY").Count -eq 0) `
    "progressMetricType eventual replacement is outside canonical metadata."
Assert-True (@($progressUsage | Where-Object recommendedDisposition -eq "SPLIT_INTO_RELATIONS").Count -eq 0) `
    "Deprecated disposition must not classify progressMetricType as canonical relations."

$progressMappings = @($mapping | Where-Object legacyField -eq "progressMetricType")
Assert-True ($progressMappings.Count -gt 0) "Missing progressMetricType mappings."
Assert-True (@($progressMappings | Where-Object {
    $_.targetLayer -ne "NON_METADATA_COMPATIBILITY_OR_ANALYSIS_PROTOCOL" -or
    $_.targetRelation -ne "NONE" -or
    $_.conversionMode -ne "LEGACY_COMPATIBILITY_READONLY"
}).Count -eq 0) "progressMetricType was mapped into target canonical metadata."
Assert-True (@($compatibility | Where-Object legacyField -eq "progressMetricType").Count -gt 0) `
    "progressMetricType compatibility consumers were not inventoried."

$issueIds = @{};
foreach ($issue in $issues) { $issueIds[$issue.issueId] = $true }
foreach ($row in ($mapping | Where-Object derivationMode -eq "LEGACY_HEURISTIC_FALLBACK")) {
    Assert-True (-not [string]::IsNullOrWhiteSpace($row.knownIssueIds)) "Heuristic mapping lacks issue IDs: $($row.legacyField)."
    foreach ($issueId in ($row.knownIssueIds -split ';')) {
        Assert-True ($issueIds.ContainsKey($issueId)) "Unknown issue ID $issueId in $($row.legacyField)."
    }
}

$baseline = Join-Path $RepoRoot "app/src/main/assets/metadata/analysis_contract_baseline_v1.csv"
Assert-True ((Get-FileHash -Algorithm SHA256 -LiteralPath $baseline).Hash -eq "6B0CBDEC60A38FCAFA1AA957BD8335EF9D3930175CF6E723E1A9D8265F384E52") `
    "ANALYSIS_CONTRACT_BASELINE_V1 changed."
$baselineRows = @(Import-Csv -LiteralPath $baseline)
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

Write-Host "Metadata v2.1 audit gates passed: $($usage.Count) fields, $($mapping.Count) mappings, $($compatibility.Count) compatibility rows, $($issues.Count) issues."

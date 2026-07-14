param(
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [string]$MetadataDirectory = "app/src/main/assets/metadata/tissue_load_v1"
)

$ErrorActionPreference = "Stop"
$forbiddenNames = @(
    "v0.3.5.10_fallback_role_slot_cleanup_report.md",
    "v0.3.5.10_generated_program_quality_audit_report.md",
    "v0.3.5.10_generated_program_quality_samples.json",
    "v0.3.5.8_metadata_slot_coverage_diagnostic.csv",
    "v0.3.5.8_overhead_smash_recovery_policy_report.md",
    "v0.3.5.8_representative_template_output_audit_report.md"
)
if ([IO.Path]::GetFileName($OutputPath) -in $forbiddenNames) {
    throw "Refusing to overwrite a protected pre-existing output."
}

$sources = @{}
Import-Csv -LiteralPath (Join-Path $MetadataDirectory "tissue_load_evidence_registry_v1.csv") | ForEach-Object {
    $sources[$_.sourceId] = $_
}
$claims = @(Import-Csv -LiteralPath (Join-Path $MetadataDirectory "tissue_evidence_claims_draft_v1.csv") | Sort-Object draftClaimId)
$rows = foreach ($claim in $claims) {
    $source = $sources[$claim.sourceId]
    if ($null -eq $source) { throw "Unknown sourceId in draft claim: $($claim.sourceId)" }
    [pscustomobject][ordered]@{
        reviewBatchId = "TISSUE_RUBRIC_B1_LOWER_KNEE_ANKLE"
        blindItemId = "BLIND_$($claim.draftClaimId)"
        sourceId = $claim.sourceId
        pmid = $source.pmid
        doi = $source.doi
        title = $source.title
        stableKey = $claim.stableKey
        tissueId = $claim.tissueId
        loadDimension = $claim.loadDimension
        exerciseConditionTarget = $claim.exerciseCondition
        evidenceLocatorType = $claim.evidenceLocatorType
        evidenceLocator = $claim.evidenceLocator
        sourceAccessInstruction = "Retrieve the primary source by PMID or DOI and assess the stated locator independently. Do not access Phase B1 draft claims, proposed bands, values, or rationale."
    }
}

$parent = Split-Path -Parent $OutputPath
if ($parent) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
$rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding utf8
$sha = [Security.Cryptography.SHA256]::Create()
try { $hash = $sha.ComputeHash([IO.File]::ReadAllBytes((Resolve-Path $OutputPath))) } finally { $sha.Dispose() }
$hex = (([BitConverter]::ToString($hash)) -replace '-', '').ToLowerInvariant()
Write-Output "BLIND_PACKAGE_ITEM_COUNT=$($rows.Count)"
Write-Output "BLIND_PACKAGE_SHA256=$hex"

param(
    [string]$InputPath = "outputs/v0.3.5.0_pass3_1_semantic_recalibrated_sidecar.csv",
    [string]$OutputDirectory = "app/src/main/assets/metadata"
)

$ErrorActionPreference = "Stop"

$fields = @(
    "stableKey",
    "exerciseName",
    "currentActivityKind",
    "currentPlanningEligibility",
    "movementFamily",
    "movementSubtype",
    "programSlot",
    "redundancyGroup",
    "progressMetricType",
    "strengthProgressionGroup",
    "analysisEligibility",
    "primaryStressProfile",
    "secondaryStressTags",
    "tendonStressTags",
    "ligamentJointStabilityStressTags",
    "jointImpactStressTags",
    "cognitiveStressTags",
    "sportContextTags",
    "recoveryDecayProfile",
    "stressMagnitudeHint",
    "badmintonTransferLevel",
    "badmintonTransferType",
    "badmintonSkillTargets",
    "badmintonPhysicalQualities",
    "transferConfidence",
    "sourceConfidenceLevel",
    "finalSourceStatus",
    "neuromuscularStressLevel",
    "systemicMuscularStressLevel",
    "localMuscularStressLevel",
    "jointTendonImpactStressLevel",
    "movementFocusDemandLevel",
    "recoveryDurationClass"
)

$rows = @(Import-Csv -LiteralPath $InputPath)
if ($rows.Count -ne 215) {
    throw "Expected 215 Pass 3.1 rows, found $($rows.Count)."
}

$missingFields = $fields | Where-Object { $_ -notin $rows[0].PSObject.Properties.Name }
if ($missingFields.Count -gt 0) {
    throw "Missing required fields: $($missingFields -join ', ')"
}

$duplicateKeys = @($rows | Group-Object stableKey | Where-Object { $_.Name -eq "" -or $_.Count -ne 1 })
if ($duplicateKeys.Count -gt 0) {
    throw "Blank or duplicate stableKey values found."
}

$axisFields = @(
    "neuromuscularStressLevel",
    "systemicMuscularStressLevel",
    "localMuscularStressLevel",
    "jointTendonImpactStressLevel",
    "movementFocusDemandLevel",
    "recoveryDurationClass"
)
foreach ($field in $axisFields) {
    if (@($rows | Where-Object { [string]::IsNullOrWhiteSpace($_.$field) }).Count -gt 0) {
        throw "Blank canonical stress axis: $field"
    }
}

$transferCounts = @{}
$rows | Group-Object badmintonTransferLevel | ForEach-Object { $transferCounts[$_.Name] = $_.Count }
$expectedTransfer = @{ DIRECT = 18; SUPPORTIVE = 75; GENERAL = 93; NONE = 29 }
foreach ($key in $expectedTransfer.Keys) {
    if ($transferCounts[$key] -ne $expectedTransfer[$key]) {
        throw "Unexpected $key transfer count: $($transferCounts[$key])"
    }
}

$stressCounts = @{}
$rows | Group-Object stressMagnitudeHint | ForEach-Object { $stressCounts[$_.Name] = $_.Count }
$expectedStress = @{ VERY_HIGH = 6; HIGH = 36; MODERATE = 107; LOW = 66 }
foreach ($key in $expectedStress.Keys) {
    if ($stressCounts[$key] -ne $expectedStress[$key]) {
        throw "Unexpected $key stress count: $($stressCounts[$key])"
    }
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$assetName = "canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"
$assetPath = Join-Path $OutputDirectory $assetName
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$csvText = (($rows | Select-Object -Property $fields | ConvertTo-Csv -NoTypeInformation) -join [Environment]::NewLine) +
    [Environment]::NewLine
[System.IO.File]::WriteAllText($assetPath, $csvText, $utf8NoBom)

$assetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $assetPath).Hash.ToLowerInvariant()
$sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $InputPath).Hash.ToLowerInvariant()
$manifest = [ordered]@{
    schemaVersion = 1
    metadataVersion = "v0.3.5.0-pass3.1"
    appVersion = "v0.3.5.0"
    createdForAppVersion = "v0.3.5.0"
    taxonomyRevision = "pass3.1-semantic-recalibrated"
    asset = $assetName
    rowCount = $rows.Count
    stableKeyUnique = $true
    safeForSeedMutation = "NO"
    sourceArtifact = "v0.3.5.0_pass3_1_semantic_recalibrated_sidecar.csv"
    sourceSha256 = $sourceHash
    assetSha256 = $assetHash
    transferCounts = $expectedTransfer
    stressMagnitudeCounts = $expectedStress
    fields = $fields
}
$manifestText = $manifest | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText(
    (Join-Path $OutputDirectory "canonical_exercise_metadata_manifest.json"),
    $manifestText,
    $utf8NoBom
)

Write-Output "asset=$assetPath"
Write-Output "rows=$($rows.Count)"
Write-Output "sha256=$assetHash"

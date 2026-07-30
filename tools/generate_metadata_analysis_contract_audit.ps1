param(
    [string]$RepoRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"
$mainRoot = Join-Path $RepoRoot "app/src/main/java"
$docsRoot = Join-Path $RepoRoot "docs/audits"
New-Item -ItemType Directory -Force -Path $docsRoot | Out-Null

function Relative-Path([string]$Path) {
    $prefix = $RepoRoot.TrimEnd("\") + "\"
    if (-not $Path.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside repository root: $Path"
    }
    $Path.Substring($prefix.Length).Replace("\", "/")
}

function Data-Class-Fields([string]$Path, [string]$ClassName, [string]$Storage) {
    $text = Get-Content -LiteralPath $Path -Raw
    $start = $text.IndexOf("data class $ClassName(")
    if ($start -lt 0) { throw "Missing data class $ClassName in $Path" }
    $cursor = $start + "data class $ClassName(".Length
    $depth = 1
    while ($cursor -lt $text.Length -and $depth -gt 0) {
        if ($text[$cursor] -eq '(') { $depth++ }
        if ($text[$cursor] -eq ')') { $depth-- }
        $cursor++
    }
    $body = $text.Substring($start, $cursor - $start)
    [regex]::Matches($body, 'val\s+([A-Za-z][A-Za-z0-9_]*)\s*:\s*([^,\r\n\)]+)') | ForEach-Object {
        [pscustomobject]@{
            FieldName = $_.Groups[1].Value
            KotlinType = $_.Groups[2].Value.Trim()
            StorageLocation = $Storage
        }
    }
}

$sourceFiles = Get-ChildItem -LiteralPath $mainRoot -Recurse -Filter *.kt
$exerciseFields = Data-Class-Fields `
    (Join-Path $mainRoot "com/training/trackplanner/data/Entities.kt") `
    "Exercise" `
    "Room exercises"
$runtimeFields = Data-Class-Fields `
    (Join-Path $mainRoot "com/training/trackplanner/data/ExerciseMetadataAdapter.kt") `
    "RuntimeExerciseMetadata" `
    "canonical asset plus Room runtime_exercise_metadata override"

$multiFields = @(
    "primaryMuscles", "secondaryMuscles", "equipmentTags", "stabilityRoles",
    "sportTransferDirect", "sportTransferSupportive", "badmintonTransferRoles",
    "fatigueCategories", "adaptiveBaselineGroups", "accessoryRoles", "courtMovementTypes",
    "badmintonSkillTargets", "jointStressTags", "balanceContributionTags", "analysisEligibility",
    "secondaryStressTags", "tendonStressTags", "ligamentJointStabilityStressTags",
    "jointImpactStressTags", "cognitiveStressTags", "sportContextTags",
    "badmintonTransferType", "badmintonPhysicalQualities"
)
$relationFields = @(
    "movementPattern", "movementCategory", "movementFamily", "movementSubtype",
    "forceType", "bodyRegion", "trainingRole", "primaryMuscles", "secondaryMuscles",
    "stabilityRoles", "fatigueCategories", "adaptiveBaselineGroups", "badmintonTransferRoles",
    "courtMovementTypes", "jointStressTags", "analysisEligibility", "progressMetricType",
    "programSlot", "redundancyGroup", "strengthProgressionGroup", "primaryStressProfile",
    "secondaryStressTags", "tendonStressTags", "ligamentJointStabilityStressTags",
    "jointImpactStressTags", "cognitiveStressTags", "sportContextTags",
    "badmintonTransferType", "badmintonSkillTargets", "badmintonPhysicalQualities",
    "neuromuscularStressLevel", "systemicMuscularStressLevel", "localMuscularStressLevel",
    "jointTendonImpactStressLevel", "movementFocusDemandLevel"
)
$provenanceFields = @(
    "metadataConfidence", "transferConfidence", "sourceConfidenceLevel", "finalSourceStatus",
    "safeForSeedMutation"
)
$displayFields = @(
    "name", "exerciseName", "category", "detail1", "detail2", "description",
    "familyName", "imageAssetName"
)
$identityFields = @("stableKey", "isActive", "archivedAt", "isCustom", "needsReview")

function Disposition([string]$Field) {
    if ($identityFields -contains $Field) { return "KEEP_TYPED_AUTHORITY" }
    if ($provenanceFields -contains $Field) { return "PROVENANCE_ONLY" }
    if ($displayFields -contains $Field) { return "DISPLAY_ONLY" }
    if ($relationFields -contains $Field) { return "SPLIT_INTO_RELATIONS" }
    if ($Field -match 'Weight$|Eligible$') { return "DEPRECATE_AFTER_PARITY" }
    if ($Field -match '^(familyId|mainLiftGroup|hypertrophyVolumeGroup|accessoryContributionGroup)$') {
        return "DEPRECATE_AFTER_PARITY"
    }
    return "REVIEW_REQUIRED"
}

function Module-Usage([string[]]$Consumers, [string]$Pattern) {
    if ($Consumers | Where-Object { $_ -match $Pattern }) { "YES" } else { "NO" }
}

$usageRows = foreach ($field in @($exerciseFields) + @($runtimeFields)) {
    $escaped = [regex]::Escape($field.FieldName)
    $producers = @()
    $consumers = @()
    $parsing = $false
    foreach ($file in $sourceFiles) {
        $text = Get-Content -LiteralPath $file.FullName -Raw
        $relative = Relative-Path $file.FullName
        if ($text -match "(?m)\b$escaped\s*=") { $producers += $relative }
        if ($text -match "\.$escaped\b|::$escaped\b") { $consumers += $relative }
        if ($text -match "(?s)$escaped.{0,240}(split\s*\(|contains\s*\()" -or
            $text -match "(?s)(split\s*\(|contains\s*\().{0,240}$escaped") {
            $parsing = $true
        }
    }
    $consumerList = $consumers | Sort-Object -Unique
    [pscustomobject]@{
        fieldName = $field.FieldName
        storageLocation = $field.StorageLocation
        cardinality = if ($multiFields -contains $field.FieldName -or
            $field.KotlinType -match 'MetadataTokenField|List|Set') { "MULTI" } else { "SINGLE" }
        currentProducers = (($producers | Sort-Object -Unique) -join ";")
        currentConsumers = ($consumerList -join ";")
        ofiUsage = Module-Usage $consumerList 'analysis/fatigue|HomeSummary|Readiness'
        programGenerationUsage = Module-Usage $consumerList 'Program'
        muscleAnalysisUsage = Module-Usage $consumerList 'analysis/lab|StrengthAndMuscle'
        badmintonAnalysisUsage = Module-Usage $consumerList 'analysis/badminton|BadmintonTransfer'
        connectiveTissueUsage = Module-Usage $consumerList 'analysis/tissue|ConnectiveTissue'
        uiUsage = Module-Usage $consumerList 'Screen|Ui|Editor|Presentation|MainActivity'
        backupRestoreUsage = Module-Usage $consumerList 'Backup|Restore|RecordCsv|Import|Export'
        parsingOrSubstringInference = if ($parsing) { "YES" } else { "NO" }
        recommendedDisposition = Disposition $field.FieldName
    }
}

$usageCsv = Join-Path $docsRoot "metadata_field_usage_matrix.csv"
$usageRows | Sort-Object storageLocation, fieldName |
    Export-Csv -LiteralPath $usageCsv -NoTypeInformation -Encoding utf8

$usageMd = Join-Path $docsRoot "metadata_field_usage_matrix.md"
$usageLines = @(
    "# Metadata field usage matrix",
    "",
    '- Baseline: `47f93eadaff64a49f6dc886a9319191c7388029c`',
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    '- Scope: every field in `Exercise` and `RuntimeExerciseMetadata`',
    '- Machine-readable companion: `metadata_field_usage_matrix.csv`',
    "",
    "| Field | Storage | Cardinality | OFI | Program | Muscle | Badminton | Tissue | UI | Backup | Parsing/inference | Disposition |",
    "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|"
)
foreach ($row in ($usageRows | Sort-Object storageLocation, fieldName)) {
    $usageLines += "| ``$($row.fieldName)`` | $($row.storageLocation) | $($row.cardinality) | $($row.ofiUsage) | $($row.programGenerationUsage) | $($row.muscleAnalysisUsage) | $($row.badmintonAnalysisUsage) | $($row.connectiveTissueUsage) | $($row.uiUsage) | $($row.backupRestoreUsage) | $($row.parsingOrSubstringInference) | ``$($row.recommendedDisposition)`` |"
}
$usageLines += @(
    "",
    "## Reading the matrix",
    "",
    'The producer and consumer file lists are retained in the CSV. A `SPLIT_INTO_RELATIONS` disposition is a Phase 1 target, not permission to delete the compatibility field. No field is removed before shadow parity and a later cutover approval.'
)
Set-Content -LiteralPath $usageMd -Value $usageLines -Encoding utf8

function Module-Name([string]$RelativePath) {
    if ($RelativePath -match 'analysis/fatigue|Readiness|HomeSummary') { return "OFI/readiness" }
    if ($RelativePath -match 'Program') { return "program generation" }
    if ($RelativePath -match 'analysis/lab|StrengthAndMuscle') { return "muscle/strength analysis" }
    if ($RelativePath -match 'analysis/badminton|BadmintonTransfer') { return "badminton analysis" }
    if ($RelativePath -match 'analysis/tissue|ConnectiveTissue') { return "connective tissue" }
    return "shared metadata/data"
}

function Replacement-Relation([string]$Text) {
    if ($Text -match 'muscle|Muscle|FOREARM|QUAD|GLUTE') { return "ExerciseMuscleContribution" }
    if ($Text -match 'badminton|Badminton|TRANSFER|FOOTWORK|DECEL') { return "ExerciseBadmintonTransferPoint / ExercisePhysicalQualityPoint" }
    if ($Text -match 'slot|Slot|Program|ROLE|ANCHOR') { return "ExerciseProgramSlotCapability / ExerciseProgramRoleEligibility" }
    if ($Text -match 'fatigue|Fatigue|Stress|HEAVY|SPEED|REACTIVE') { return "ExerciseOfiAxisContribution / ExerciseOfiDoseProfile" }
    return "movement/anatomy typed relation or explicit compatibility mapping"
}

$inferenceRows = @()
foreach ($file in $sourceFiles) {
    $relative = Relative-Path $file.FullName
    if ($relative -notmatch '/analysis/|/data/') { continue }
    if ($relative -match '/analysis/contracts/') { continue }
    $functionName = "<file>"
    $lines = Get-Content -LiteralPath $file.FullName
    for ($index = 0; $index -lt $lines.Count; $index++) {
        $line = $lines[$index]
        if ($line -match '\bfun\s+([A-Za-z][A-Za-z0-9_]*)') { $functionName = $Matches[1] }
        if ($line -notmatch '\.split\s*\(|\.contains\s*\(|Name.*contains|stableKey.*contains|fallback|Fallback') {
            continue
        }
        $inputField = ($usageRows.fieldName | Where-Object { $line -match "\b$([regex]::Escape($_))\b" } | Select-Object -First 1)
        if (-not $inputField) { $inputField = "context-dependent string/token collection" }
        $inferenceRows += [pscustomobject]@{
            sourceFile = $relative
            line = $index + 1
            function = $functionName
            analysisModule = Module-Name $relative
            inputField = $inputField
            inferredOutput = $line.Trim()
            replacementRelationRequired = Replacement-Relation $line
            riskLevel = if ($line -match 'stableKey|exerciseName|name\.|Name\.') { "HIGH" } elseif ($line -match '\.split') { "MEDIUM" } else { "MEDIUM" }
        }
    }
}

$inferenceCsv = Join-Path $docsRoot "metadata_parsing_inference_audit.csv"
$inferenceRows | Export-Csv -LiteralPath $inferenceCsv -NoTypeInformation -Encoding utf8

$inferenceMd = Join-Path $docsRoot "metadata_parsing_inference_audit.md"
$highCount = @($inferenceRows | Where-Object riskLevel -eq "HIGH").Count
$mediumCount = @($inferenceRows | Where-Object riskLevel -eq "MEDIUM").Count
$inferenceLines = @(
    "# Metadata parsing and inference audit",
    "",
    '- Baseline: `47f93eadaff64a49f6dc886a9319191c7388029c`',
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Production matches inventoried: $($inferenceRows.Count)",
    "- High risk: $highCount",
    "- Medium risk: $mediumCount",
    '- Machine-readable companion: `metadata_parsing_inference_audit.csv`',
    "",
    'The report intentionally includes legacy oracle code. Phase 0 does not remove or rewrite these paths. The new `analysis/contracts` package is separately guarded by `AnalysisContractArchitectureTest` and contains no semantic name/key/delimiter inference.',
    "",
    "| Source | Line | Function | Module | Input | Risk | Replacement |",
    "|---|---:|---|---|---|---:|---|"
)
foreach ($row in $inferenceRows) {
    $inferenceLines += "| ``$($row.sourceFile)`` | $($row.line) | ``$($row.function)`` | $($row.analysisModule) | ``$($row.inputField)`` | $($row.riskLevel) | $($row.replacementRelationRequired) |"
}
Set-Content -LiteralPath $inferenceMd -Value $inferenceLines -Encoding utf8

Write-Host "Wrote $($usageRows.Count) field rows to $usageCsv"
Write-Host "Wrote $($inferenceRows.Count) parsing/inference rows to $inferenceCsv"

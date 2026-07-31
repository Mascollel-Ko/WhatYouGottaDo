param(
    [string]$RepoRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"
$mainRoot = Join-Path $RepoRoot "app/src/main/java"
$testRoot = Join-Path $RepoRoot "app/src/test/java"
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

$sourceFiles = @(Get-ChildItem -LiteralPath $mainRoot -Recurse -Filter *.kt)
$testFiles = @(Get-ChildItem -LiteralPath $testRoot -Recurse -Filter *.kt)
$allKotlinFiles = @($sourceFiles) + @($testFiles)
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
$recordProtocolFields = @("mode", "detail1", "detail2", "loadProfile")
$progressProtocolFields = @("progressMetricType", "familyE1rmMultiplier")
$compatibilityFields = @("progressMetricType")

function Current-Disposition([string]$Field) {
    if ($compatibilityFields -contains $Field) { return "LEGACY_COMPATIBILITY_READONLY" }
    if ($identityFields -contains $Field) { return "KEEP_TYPED_AUTHORITY" }
    if ($provenanceFields -contains $Field) { return "PROVENANCE_ONLY" }
    if ($displayFields -contains $Field) { return "DISPLAY_ONLY" }
    return "KEEP_CURRENT_BEHAVIOR"
}

function Eventual-Replacement([string]$Field) {
    if ($compatibilityFields -contains $Field) { return "REPLACE_OUTSIDE_CANONICAL_METADATA_AFTER_PARITY" }
    if ($recordProtocolFields -contains $Field -or $progressProtocolFields -contains $Field) {
        return "REPLACE_OUTSIDE_CANONICAL_METADATA_AFTER_PARITY"
    }
    if ($Field -in @("jointTendonImpactStressLevel", "movementFocusDemandLevel")) {
        return "DEPRECATE_AFTER_PARITY"
    }
    if ($identityFields -contains $Field) { return "KEEP_TYPED_AUTHORITY" }
    if ($provenanceFields -contains $Field) { return "CONSOLIDATE_PROVENANCE_AFTER_PARITY" }
    if ($displayFields -contains $Field) { return "KEEP_PRESENTATION_ONLY" }
    if ($relationFields -contains $Field) { return "SPLIT_INTO_RELATIONS_AFTER_PARITY" }
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
        currentDisposition = Current-Disposition $field.FieldName
        eventualReplacementStrategy = Eventual-Replacement $field.FieldName
        recommendedDisposition = Current-Disposition $field.FieldName
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
    "| Field | Storage | Cardinality | OFI | Program | Muscle | Badminton | Tissue | UI | Backup | Parsing/inference | Current disposition | Eventual replacement |",
    "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|"
)
foreach ($row in ($usageRows | Sort-Object storageLocation, fieldName)) {
    $usageLines += "| ``$($row.fieldName)`` | $($row.storageLocation) | $($row.cardinality) | $($row.ofiUsage) | $($row.programGenerationUsage) | $($row.muscleAnalysisUsage) | $($row.badmintonAnalysisUsage) | $($row.connectiveTissueUsage) | $($row.uiUsage) | $($row.backupRestoreUsage) | $($row.parsingOrSubstringInference) | ``$($row.currentDisposition)`` | ``$($row.eventualReplacementStrategy)`` |"
}
$usageLines += @(
    "",
    "## Reading the matrix",
    "",
    'The producer and consumer file lists are retained in the CSV. `currentDisposition` governs the checked-out production code. `eventualReplacementStrategy` is only a future destination after parity, compatibility, rollback, and approval gates pass. The deprecated `recommendedDisposition` CSV column mirrors `currentDisposition` for older tooling.'
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
    $lines = Get-Content -LiteralPath $file.FullName -Encoding utf8
    for ($index = 0; $index -lt $lines.Count; $index++) {
        $line = $lines[$index]
        if ($line -match '\bfun\s+([A-Za-z][A-Za-z0-9_]*)') { $functionName = $Matches[1] }
        $explicitInference = $line -match '\.split\s*\(|\.contains\s*\(|Name.*contains|stableKey.*contains|fallback|Fallback|ifBlank\s*\{|ifEmpty\s*\{'
        $semanticFunction = $functionName -match 'map|resolve|classif|infer|derive|fallback|role|slot|type|category|profile|eligib|stress|transfer|movement|metric|mode|kind'
        $catchAllInference = $semanticFunction -and $line -match 'else\s*->\s*("|[A-Z])|\?:\s*("|[A-Z])'
        if (-not $explicitInference -and -not $catchAllInference) {
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

function Escape-Markdown([string]$Value) {
    if ($null -eq $Value) { return "" }
    $Value.Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

$issueDefinitions = @(
    [pscustomobject]@{ Id = "META-SEED-CSV-FALLBACK"; File = "app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Function = "exerciseFromCsv"; Field = "multiple Exercise CSV fields"; Module = "shared metadata/data"; Severity = "HIGH"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-SEED-MOVEMENT-PATTERN"; File = "app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Function = "movementPatternFor"; Field = "movementPattern"; Module = "movement/anatomy"; Severity = "HIGH"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-SEED-FAMILY"; File = "app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Function = "familyIdFor"; Field = "familyId"; Module = "movement/anatomy"; Severity = "HIGH"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-SEED-PRIMARY-MUSCLES"; File = "app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Function = "musclesFor"; Field = "primaryMuscles"; Module = "muscle/strength analysis"; Severity = "HIGH"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-SEED-SECONDARY-MUSCLES"; File = "app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Function = "fallbackSecondaryMuscles"; Field = "secondaryMuscles"; Module = "muscle/strength analysis"; Severity = "HIGH"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-SEED-FORCE-TYPE"; File = "app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Function = "forceTypeFor"; Field = "forceType"; Module = "movement/anatomy"; Severity = "MEDIUM"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-SEED-TRAINING-ROLE"; File = "app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Function = "trainingRoleFor"; Field = "trainingRole"; Module = "program generation"; Severity = "HIGH"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-SEED-SPORT-TRANSFER"; File = "app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Function = "sportTransferDirectFor"; Field = "sportTransferDirect"; Module = "badminton analysis"; Severity = "HIGH"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-SEED-LOAD-PROFILE"; File = "app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Function = "loadProfileFor"; Field = "loadProfile"; Module = "shared metadata/data"; Severity = "MEDIUM"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-OFI-BROAD-FATIGUE"; File = "app/src/main/java/com/training/trackplanner/data/ExerciseMetadataAdapter.kt"; Function = "broadLegacyFatigueCategories"; Field = "fatigueCategories"; Module = "OFI/readiness"; Severity = "HIGH"; Class = "STRUCTURAL_AMBIGUITY" },
    [pscustomobject]@{ Id = "META-ACTIVITY-NAME-FALLBACK"; File = "app/src/main/java/com/training/trackplanner/data/ExercisePlanning.kt"; Function = "Exercise"; Field = "activityKind"; Module = "program generation"; Severity = "HIGH"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-PROGRAM-SLOT-FALLBACK"; File = "app/src/main/java/com/training/trackplanner/data/SlotCapabilityResolver.kt"; Function = "resolve"; Field = "programSlot"; Module = "program generation"; Severity = "HIGH"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-PROGRAM-SLOT-NAME"; File = "app/src/main/java/com/training/trackplanner/data/SlotCapabilityResolver.kt"; Function = "explicitNameFallback"; Field = "programSlot"; Module = "program generation"; Severity = "HIGH"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-MUSCLE-FALLBACK"; File = "app/src/main/java/com/training/trackplanner/analysis/lab/MuscleLoadInputBuilder.kt"; Function = "fallbackContributions"; Field = "primaryMuscles"; Module = "muscle/strength analysis"; Severity = "HIGH"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-PROGRAM-LOADED-NAME"; File = "app/src/main/java/com/training/trackplanner/data/ProgramEvaluationPolicy.kt"; Function = "isLoadedStrength"; Field = "equipment and exerciseName"; Module = "program generation"; Severity = "HIGH"; Class = "STRUCTURAL_AMBIGUITY" },
    [pscustomobject]@{ Id = "META-PROGRAM-RERANK-LOADED-NAME"; File = "app/src/main/java/com/training/trackplanner/data/ProgramCandidateRerankingPolicy.kt"; Function = "needsLoadedStrength"; Field = "exerciseName"; Module = "program generation"; Severity = "HIGH"; Class = "STRUCTURAL_AMBIGUITY" },
    [pscustomobject]@{ Id = "META-BADMINTON-LEVEL-FALLBACK"; File = "app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferMetadataMapper.kt"; Function = "transferType"; Field = "badmintonTransferLevel"; Module = "badminton analysis"; Severity = "MEDIUM"; Class = "CURRENT_BUG_PRESERVED" },
    [pscustomobject]@{ Id = "META-OFI-RECOVERY-DEFAULT"; File = "app/src/main/java/com/training/trackplanner/analysis/fatigue/DailyFatigueCalculator.kt"; Function = "RecordContext"; Field = "recoveryDurationClass"; Module = "OFI/readiness"; Severity = "MEDIUM"; Class = "MISSING_AUTHORITY" },
    [pscustomobject]@{ Id = "META-BADMINTON-MUSCLE-INFERENCE"; File = "app/src/main/java/com/training/trackplanner/data/ExerciseMetadataMapper.kt"; Function = "MetadataSource"; Field = "badminton and balance relations"; Module = "badminton analysis"; Severity = "HIGH"; Class = "STRUCTURAL_AMBIGUITY" },
    [pscustomobject]@{ Id = "META-STRENGTH-PROXY-FALLBACK"; File = "app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthPerformanceRegistry.kt"; Function = "proxyLoadings"; Field = "strength proxy relation"; Module = "muscle/strength analysis"; Severity = "HIGH"; Class = "MISSING_AUTHORITY" }
)

$issueRows = foreach ($definition in $issueDefinitions) {
    $evidence = @($inferenceRows | Where-Object {
        $_.sourceFile -eq $definition.File -and $_.function -eq $definition.Function
    } | Sort-Object line)
    if ($evidence.Count -eq 0) {
        throw "Configured metadata issue has no current evidence: $($definition.Id)"
    }
    [pscustomobject]@{
        issueId = $definition.Id
        scopeType = "LEGACY_INFERENCE_PATH"
        exerciseStableKey = "ALL_OR_CONTEXT_DEPENDENT"
        legacyField = $definition.Field
        affectedModule = $definition.Module
        affectedConsumer = "$($definition.File)#$($definition.Function)"
        observedCurrentBehavior = "Current classifier manufactures or substitutes metadata from legacy fallback input; matching audit lines: $($evidence.Count)"
        expectedOrTargetBehavior = "Use an explicit stableKey relation; otherwise remain UNRESOLVED in REVIEWED_V1"
        derivationMode = "LEGACY_HEURISTIC_FALLBACK"
        severity = $definition.Severity
        parityImpact = "BASELINE_V1 behavior must remain reproducible until reviewed correction approval"
        resolutionClass = $definition.Class
        status = "OPEN"
        proposedResolution = "Review the exact consumer and approve a stableKey-level relation before any production cutover"
        targetVersion = "REVIEWED_V1_FOLLOW_UP"
        evidenceLocations = (($evidence | ForEach-Object { "$($_.sourceFile):$($_.line)" }) -join ';')
    }
}

$issueCsv = Join-Path $docsRoot "metadata_migration_issue_ledger.csv"
$issueRows | Export-Csv -LiteralPath $issueCsv -NoTypeInformation -Encoding utf8
$issueMd = Join-Path $docsRoot "metadata_migration_issue_ledger.md"
$issueLines = @(
    "# Metadata migration issue ledger",
    "",
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Open issues: $($issueRows.Count)",
    "- Current behavior preserved: $(@($issueRows | Where-Object resolutionClass -eq 'CURRENT_BUG_PRESERVED').Count)",
    "- Structural ambiguity: $(@($issueRows | Where-Object resolutionClass -eq 'STRUCTURAL_AMBIGUITY').Count)",
    "- Missing authority: $(@($issueRows | Where-Object resolutionClass -eq 'MISSING_AUTHORITY').Count)",
    "- Reviewed correction candidate: $(@($issueRows | Where-Object resolutionClass -eq 'REVIEWED_CORRECTION_CANDIDATE').Count)",
    "- Intentional change approved: $(@($issueRows | Where-Object resolutionClass -eq 'INTENTIONAL_CHANGE_APPROVED').Count)",
    "- Migration implementation error: $(@($issueRows | Where-Object resolutionClass -eq 'MIGRATION_IMPLEMENTATION_ERROR').Count)",
    "- High severity: $(@($issueRows | Where-Object severity -eq 'HIGH').Count)",
    "- Medium severity: $(@($issueRows | Where-Object severity -eq 'MEDIUM').Count)",
    '- Machine-readable companion: `metadata_migration_issue_ledger.csv`',
    "",
    "No row in this ledger authorizes a behavior correction. BASELINE_V1 keeps current behavior; REVIEWED_V1 remains unresolved until stableKey-level human review.",
    "",
    "| Issue | Field | Module | Consumer | Derivation | Severity | Resolution class | Evidence |",
    "|---|---|---|---|---|---|---|---|"
)
foreach ($row in $issueRows) {
    $issueLines += "| ``$($row.issueId)`` | ``$(Escape-Markdown $row.legacyField)`` | $(Escape-Markdown $row.affectedModule) | ``$(Escape-Markdown $row.affectedConsumer)`` | ``$($row.derivationMode)`` | $($row.severity) | ``$($row.resolutionClass)`` | ``$(Escape-Markdown $row.evidenceLocations)`` |"
}
Set-Content -LiteralPath $issueMd -Value $issueLines -Encoding utf8

$movementFields = @(
    "activityKind", "bodyRegion", "category", "compoundType", "forceType", "laterality",
    "movementCategory", "movementFamily", "movementPattern", "movementSubtype", "plane",
    "stabilityDemandLevel", "mobilityDemandLevel"
)
$programFields = @(
    "accessoryRoles", "analysisEligibility", "defaultRestSeconds", "equipment", "equipmentTags",
    "familyId", "familyName", "familyRole", "mainLiftGroup", "planningEligibility", "programSlot",
    "redundancyGroup", "strengthProgressionGroup", "trainingRole"
)
$muscleFields = @(
    "accessoryContributionGroup", "hypertrophyVolumeGroup", "primaryMuscles", "secondaryMuscles"
)
$badmintonFields = @(
    "antiRotationWeight", "badmintonPhysicalQualities", "badmintonSkillTargets", "badmintonTransferLevel",
    "badmintonTransferRoles", "badmintonTransferStrength", "badmintonTransferType", "balanceContributionTags",
    "courtMovementTypes", "decelerationWeight", "elasticSscWeight", "gripLoadWeight", "overheadSwingWeight",
    "rotationPowerWeight", "sportTransferDirect", "sportTransferSupportive"
)
$tissueFields = @(
    "axialLoadLevel", "jointImpactStressTags", "jointStressTags", "ligamentJointStabilityStressTags",
    "tendonStressTags"
)
$ofiFields = @(
    "adaptiveBaselineGroups", "cognitiveStressTags", "fatigueCategories", "jointTendonImpactStressLevel",
    "localLoadWeight", "localMuscularStressLevel", "neuralHeavyWeight",
    "neuralSpeedWeight", "neuromuscularStressLevel", "primaryStressProfile", "recoveryDecayProfile",
    "recoveryDurationClass", "secondaryStressTags", "sportContextTags", "stressMagnitudeHint",
    "systemicLoadWeight", "systemicMuscularStressLevel"
)
$analysisCapabilityFields = @("estimated1RmEligible", "volumeLoadEligible")

function Target-Classification([string]$Field) {
    if ($Field -in @("jointTendonImpactStressLevel", "movementFocusDemandLevel")) {
        return [pscustomobject]@{ Layer = "NON_METADATA_LEGACY_AXIS_COMPATIBILITY"; Relation = "NONE" }
    }
    if ($recordProtocolFields -contains $Field) {
        return [pscustomobject]@{ Layer = "NON_METADATA_RECORD_INPUT_PROTOCOL"; Relation = "NONE" }
    }
    if ($progressProtocolFields -contains $Field) {
        return [pscustomobject]@{ Layer = "NON_METADATA_COMPATIBILITY_OR_ANALYSIS_PROTOCOL"; Relation = "NONE" }
    }
    if ($identityFields -contains $Field) {
        return [pscustomobject]@{ Layer = "EXERCISE_IDENTITY"; Relation = "ExerciseIdentity" }
    }
    if ($displayFields -contains $Field -or $Field -eq "appCueProfile") {
        return [pscustomobject]@{ Layer = "EXERCISE_IDENTITY"; Relation = "ExercisePresentation" }
    }
    if ($provenanceFields -contains $Field) {
        return [pscustomobject]@{ Layer = "PROVENANCE_REVIEW"; Relation = "ExerciseRelationProvenance" }
    }
    if ($analysisCapabilityFields -contains $Field) {
        return [pscustomobject]@{ Layer = "PROVENANCE_REVIEW"; Relation = "ExerciseAnalysisCapability" }
    }
    if ($tissueFields -contains $Field) {
        return [pscustomobject]@{ Layer = "CONNECTIVE_TISSUE"; Relation = "ExerciseTissueLoadRelation" }
    }
    if ($badmintonFields -contains $Field) {
        return [pscustomobject]@{ Layer = "BADMINTON"; Relation = "ExerciseBadmintonTransferPoint_OR_ExercisePhysicalQualityPoint" }
    }
    if ($muscleFields -contains $Field) {
        return [pscustomobject]@{ Layer = "MUSCLE"; Relation = "ExerciseMuscleContribution" }
    }
    if ($ofiFields -contains $Field) {
        return [pscustomobject]@{ Layer = "OFI"; Relation = "ExerciseOfiAxisContribution_OR_ExerciseOfiDoseProfile" }
    }
    if ($programFields -contains $Field) {
        $relation = if ($Field -match '^equipment') { "ExerciseEquipmentRequirement" } else { "ExerciseProgramCapability" }
        return [pscustomobject]@{ Layer = "PROGRAM_GENERATION"; Relation = $relation }
    }
    if ($movementFields -contains $Field) {
        return [pscustomobject]@{ Layer = "MOVEMENT_ANATOMY"; Relation = "ExerciseMovementAnatomyRelation" }
    }
    return [pscustomobject]@{ Layer = "UNRESOLVED"; Relation = "NONE" }
}

function Runtime-Meaning([string]$Field, [string]$Module) {
    if ($Field -eq "progressMetricType") { return "Legacy progress-analysis and prescription compatibility input" }
    if ($recordProtocolFields -contains $Field) { return "Legacy workout-record input and presentation compatibility input" }
    if ($Module -eq "shared metadata/data") { return "Persisted or resolved exercise property used by shared metadata/data code" }
    return "Exercise property consumed by $Module"
}

$mappingRows = @()
foreach ($usage in ($usageRows | Sort-Object storageLocation, fieldName)) {
    $target = Target-Classification $usage.fieldName
    $locations = @($usage.currentConsumers -split ';' | Where-Object { $_ })
    if ($locations.Count -eq 0) { $locations = @("<no-current-consumer>") }
    foreach ($location in ($locations | Sort-Object -Unique)) {
        $module = if ($location -eq "<no-current-consumer>") { "none" } else { Module-Name $location }
        $conversion = if ($compatibilityFields -contains $usage.fieldName) {
            "LEGACY_COMPATIBILITY_READONLY"
        } elseif ($recordProtocolFields -contains $usage.fieldName -or
            $progressProtocolFields -contains $usage.fieldName -or
            $usage.fieldName -in @("jointTendonImpactStressLevel", "movementFocusDemandLevel")) {
            "UNRESOLVED"
        } elseif ($target.Layer -eq "UNRESOLVED") {
            "UNRESOLVED"
        } elseif ($usage.cardinality -eq "MULTI") {
            "SPLIT_EXACT_TOKENS"
        } else {
            "DIRECT_COPY"
        }
        $derivation = if ($compatibilityFields -contains $usage.fieldName) {
            "NOT_APPLICABLE"
        } elseif ($usage.cardinality -eq "MULTI") {
            "EXACT_TOKEN_EXPANSION"
        } elseif ($usage.storageLocation -match 'canonical asset') {
            "LEGACY_RESOLVER_EXPLICIT"
        } else {
            "RAW_EXPLICIT_VALUE"
        }
        $mappingRows += [pscustomobject]@{
            legacyField = $usage.fieldName
            storageOwner = $usage.storageLocation
            cardinality = $usage.cardinality
            currentConsumerModule = $module
            consumerLocation = $location
            currentRuntimeMeaning = Runtime-Meaning $usage.fieldName $module
            targetLayer = $target.Layer
            targetRelation = $target.Relation
            conversionMode = $conversion
            derivationMode = $derivation
            rawSourcePresenceRule = if ($usage.cardinality -eq "MULTI") { "Nonblank source tokens remain explicit; blank or unknown tokens do not manufacture reviewed relations" } else { "Nonblank stored value is explicit; missing value remains unresolved unless current fallback is preserved in BASELINE_V1" }
            compatibilityStatus = if ($compatibilityFields -contains $usage.fieldName) { "PROTECTED_READONLY" } else { "CURRENT_PRODUCTION_INPUT" }
            removalGate = if ($compatibilityFields -contains $usage.fieldName) { "production consumers zero AND parity AND backup compatibility AND rollback AND explicit approval" } else { "No removal before replacement parity and explicit approval" }
            parityRisk = if ($usage.currentConsumers) { "HIGH" } else { "MEDIUM" }
            knownIssueIds = ""
            notes = if ($usage.fieldName -eq "progressMetricType") { "Not target canonical exercise metadata; responsibilities move to analysis/prescription protocols only after the removal gate" } else { "Explicit/raw compatibility path; no production cutover in this task" }
        }
    }
}

foreach ($issue in ($issueRows | Where-Object derivationMode -eq "LEGACY_HEURISTIC_FALLBACK")) {
    $matchingUsage = @($usageRows | Where-Object fieldName -eq $issue.legacyField)
    foreach ($usage in $matchingUsage) {
        $target = Target-Classification $usage.fieldName
        $mappingRows += [pscustomobject]@{
            legacyField = $usage.fieldName
            storageOwner = $usage.storageLocation
            cardinality = $usage.cardinality
            currentConsumerModule = $issue.affectedModule
            consumerLocation = $issue.affectedConsumer
            currentRuntimeMeaning = "Legacy fallback path that manufactures or substitutes a classification"
            targetLayer = $target.Layer
            targetRelation = $target.Relation
            conversionMode = "CURRENT_RESOLVER_OUTPUT"
            derivationMode = "LEGACY_HEURISTIC_FALLBACK"
            rawSourcePresenceRule = "Fallback is used when explicit source is blank, unknown, or unsupported"
            compatibilityStatus = "BASELINE_V1_ONLY"
            removalGate = "Reviewed stableKey relation AND parity AND rollback AND explicit approval"
            parityRisk = $issue.severity
            knownIssueIds = $issue.issueId
            notes = "BASELINE_V1 preserves this current fallback; REVIEWED_V1 remains UNRESOLVED until human review"
        }
    }
}
$mappingRows = @($mappingRows | Sort-Object legacyField, storageOwner, consumerLocation, derivationMode, knownIssueIds)

$mappingCsv = Join-Path $docsRoot "metadata_legacy_to_target_mapping_matrix.csv"
$mappingRows | Export-Csv -LiteralPath $mappingCsv -NoTypeInformation -Encoding utf8
$mappingMd = Join-Path $docsRoot "metadata_legacy_to_target_mapping_matrix.md"
$mappingLines = @(
    "# Metadata legacy-to-target mapping matrix",
    "",
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Source field/storage rows: $($usageRows.Count)",
    "- Consumer-specific mapping rows: $($mappingRows.Count)",
    '- Machine-readable companion: `metadata_legacy_to_target_mapping_matrix.csv`',
    "",
    "The matrix maps current compatibility storage to the future v2.1 target boundary. It does not change production behavior or implement the future Kotlin provenance model.",
    "",
    "| Field | Storage | Consumer | Target layer | Target relation | Conversion | Derivation | Issues |",
    "|---|---|---|---|---|---|---|---|"
)
foreach ($row in $mappingRows) {
    $mappingLines += "| ``$($row.legacyField)`` | $(Escape-Markdown $row.storageOwner) | ``$(Escape-Markdown $row.consumerLocation)`` | ``$($row.targetLayer)`` | ``$($row.targetRelation)`` | ``$($row.conversionMode)`` | ``$($row.derivationMode)`` | ``$(Escape-Markdown $row.knownIssueIds)`` |"
}
Set-Content -LiteralPath $mappingMd -Value $mappingLines -Encoding utf8

function Enclosing-Symbol([string[]]$Lines, [int]$Index) {
    for ($cursor = $Index; $cursor -ge 0; $cursor--) {
        if ($Lines[$cursor] -match '\bfun\s+([A-Za-z][A-Za-z0-9_]*)') { return $Matches[1] }
        if ($Lines[$cursor] -match '\b(class|object|interface)\s+([A-Za-z][A-Za-z0-9_]*)') { return $Matches[2] }
    }
    return "<file>"
}

function Consumer-Type([string]$Path, [string]$Symbol, [string]$Line) {
    if ($Path -match 'Backup|Restore|RecordCsv|Import|Export') { return "BACKUP_RESTORE" }
    if ($Path -match 'Screen|Ui|Dialog|Activity|Preview|Presentation|Subcategory') { return "UI" }
    if ($Symbol -match 'parse|fromMap|read|decode' -or $Line -match 'parse|split\s*\(') { return "PARSER" }
    if ($Path -match '/analysis/') { return "ANALYSIS" }
    if ($Path -match 'Program') { return "PROGRAM" }
    if ($Path -match 'Entities|MetadataAdapter') { return "SCHEMA_MODEL" }
    return "DATA"
}

$compatibilityRows = @()
foreach ($field in ($compatibilityFields | Sort-Object -Unique)) {
    $escaped = [regex]::Escape($field)
    foreach ($file in $allKotlinFiles) {
        $relative = Relative-Path $file.FullName
        if ($relative -eq "app/src/test/java/com/training/trackplanner/analysis/contracts/AnalysisContractAuditArtifactsTest.kt") { continue }
        $lines = Get-Content -LiteralPath $file.FullName -Encoding utf8
        for ($index = 0; $index -lt $lines.Count; $index++) {
            $line = $lines[$index]
            if ($line -notmatch "\b$escaped\b") { continue }
            $read = $line -match "\.$escaped\b|::$escaped\b"
            $write = $line -match "\b$escaped\s*="
            $access = if ($read -and $write) { "READ_WRITE" } elseif ($write) { "WRITE" } elseif ($read) { "READ" } else { "REFERENCE" }
            $symbol = Enclosing-Symbol $lines $index
            $compatibilityRows += [pscustomobject]@{
                legacyField = $field
                filePath = $relative
                symbolOrFunction = $symbol
                consumerType = Consumer-Type $relative $symbol $line
                readOrWrite = $access
                runtimeOrTest = if ($relative -match '^app/src/test/') { "TEST" } else { "PRODUCTION" }
                replacementOwner = if ($field -eq "progressMetricType" -or $field -eq "familyE1rmMultiplier") { "NON_METADATA_ANALYSIS_OR_PRESCRIPTION_PROTOCOL" } else { "NON_METADATA_RECORD_INPUT_PROTOCOL" }
                replacementStatus = "NOT_IMPLEMENTED"
                removalBlockedReason = "Current consumers remain; parity, backup compatibility, rollback, and explicit approval gates have not passed"
            }
        }
    }
}
$compatibilityRows = @($compatibilityRows | Sort-Object legacyField, filePath, symbolOrFunction, consumerType, readOrWrite, runtimeOrTest -Unique)

$compatibilityCsv = Join-Path $docsRoot "metadata_legacy_compatibility_consumers.csv"
$compatibilityRows | Export-Csv -LiteralPath $compatibilityCsv -NoTypeInformation -Encoding utf8
$compatibilityMd = Join-Path $docsRoot "metadata_legacy_compatibility_consumers.md"
$compatibilityLines = @(
    "# Metadata legacy compatibility consumers",
    "",
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Protected compatibility fields: $((@($compatibilityFields | Sort-Object -Unique)).Count)",
    "- Inventory rows: $($compatibilityRows.Count)",
    '- Machine-readable companion: `metadata_legacy_compatibility_consumers.csv`',
    "",
    "A compatibility field cannot be removed until production consumers are zero, parity passes, backup/restore compatibility and rollback are verified, and removal is explicitly approved.",
    ""
)
foreach ($field in ($compatibilityFields | Sort-Object -Unique)) {
    $rows = @($compatibilityRows | Where-Object legacyField -eq $field)
    $groups = @($rows | Group-Object filePath, symbolOrFunction | ForEach-Object { $_.Group[0] })
    $symbolCount = $groups.Count
    $compatibilityLines += @(
        "## ``$field``",
        "",
        "- Files: $((@($rows.filePath | Sort-Object -Unique)).Count)",
        "- Symbols: $symbolCount",
        "- Production consumers: $(@($groups | Where-Object runtimeOrTest -eq 'PRODUCTION').Count)",
        "- Test consumers: $(@($groups | Where-Object runtimeOrTest -eq 'TEST').Count)",
        "- UI consumers: $(@($groups | Where-Object consumerType -eq 'UI').Count)",
        "- Backup/restore consumers: $(@($groups | Where-Object consumerType -eq 'BACKUP_RESTORE').Count)",
        "- Parser consumers: $(@($groups | Where-Object consumerType -eq 'PARSER').Count)",
        "- Writers: $(@($rows | Where-Object readOrWrite -match 'WRITE' | Group-Object filePath, symbolOrFunction).Count)",
        ""
    )
}
$compatibilityLines += @(
    "| Field | File | Symbol | Type | Access | Scope | Replacement owner |",
    "|---|---|---|---|---|---|---|"
)
foreach ($row in $compatibilityRows) {
    $compatibilityLines += "| ``$($row.legacyField)`` | ``$(Escape-Markdown $row.filePath)`` | ``$(Escape-Markdown $row.symbolOrFunction)`` | $($row.consumerType) | $($row.readOrWrite) | $($row.runtimeOrTest) | ``$($row.replacementOwner)`` |"
}
Set-Content -LiteralPath $compatibilityMd -Value $compatibilityLines -Encoding utf8

Write-Host "Wrote $($usageRows.Count) field rows to $usageCsv"
Write-Host "Wrote $($inferenceRows.Count) parsing/inference rows to $inferenceCsv"
Write-Host "Wrote $($mappingRows.Count) legacy mapping rows to $mappingCsv"
Write-Host "Wrote $($compatibilityRows.Count) compatibility consumer rows to $compatibilityCsv"
Write-Host "Wrote $($issueRows.Count) migration issues to $issueCsv"

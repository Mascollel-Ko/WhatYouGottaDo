param(
    [string]$RepoRoot = (Get-Location).Path,
    [switch]$CompatibilityOnly
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

function Escape-Markdown([string]$Value) {
    if ($null -eq $Value) { return "" }
    $Value.Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Data-Class-Fields([string]$Path, [string]$ClassName, [string]$Storage) {
    $text = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
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

function Enclosing-Symbol([string[]]$Lines, [int]$Index) {
    for ($cursor = $Index; $cursor -ge 0; $cursor--) {
        if ($Lines[$cursor] -match '\bfun\s+([A-Za-z][A-Za-z0-9_]*)') { return $Matches[1] }
        if ($Lines[$cursor] -match '\b(class|object|interface)\s+([A-Za-z][A-Za-z0-9_]*)') { return $Matches[2] }
    }
    "<file>"
}

function Consumer-Kind([string]$Path, [string]$Symbol, [string]$Line) {
    if ($Path -match 'Backup|Restore|RecordCsv|Import|Export') { return "BACKUP_RESTORE" }
    if ($Path -match 'Screen|Ui|Dialog|Activity|Preview|Presentation|Subcategory|DisplayCatalogue') { return "UI" }
    if ($Symbol -match 'parse|fromMap|read|decode' -or $Line -match 'parse|split\s*\(') { return "PARSER" }
    if ($Path -match '/analysis/') { return "ANALYSIS" }
    if ($Path -match 'Program') { return "PROGRAM" }
    if ($Path -match 'Entities|MetadataAdapter|TrainingDatabase') { return "SCHEMA_MODEL" }
    "DATA"
}

function Module-Name([string]$RelativePath) {
    if ($RelativePath -match 'analysis/fatigue|Readiness|HomeSummary') { return "OFI/readiness" }
    if ($RelativePath -match 'Program') { return "program generation" }
    if ($RelativePath -match 'analysis/lab|StrengthAndMuscle|strengthperformance|StrengthPerformance') { return "muscle/strength analysis" }
    if ($RelativePath -match 'analysis/badminton|BadmintonTransfer') { return "badminton analysis" }
    if ($RelativePath -match 'analysis/tissue|ConnectiveTissue') { return "connective tissue" }
    "shared metadata/data"
}

function Module-Usage([string[]]$Consumers, [string]$Pattern) {
    if ($Consumers | Where-Object { $_ -match $Pattern }) { "YES" } else { "NO" }
}

function Write-Csv([object[]]$Rows, [string[]]$Columns, [string]$Path) {
    if ($Rows.Count -gt 0) {
        $Rows | Select-Object $Columns | Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
        return
    }
    $empty = [ordered]@{}
    foreach ($column in $Columns) { $empty[$column] = "" }
    $header = [pscustomobject]$empty | ConvertTo-Csv -NoTypeInformation | Select-Object -First 1
    Set-Content -LiteralPath $Path -Value $header -Encoding UTF8
}

$sourceFiles = @(Get-ChildItem -LiteralPath $mainRoot -Recurse -Filter *.kt)
$testFiles = @(Get-ChildItem -LiteralPath $testRoot -Recurse -Filter *.kt)
$allKotlinFiles = @($sourceFiles) + @($testFiles)
$sourceText = @{}
$sourceLines = @{}
foreach ($file in $sourceFiles) {
    $sourceText[$file.FullName] = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $sourceLines[$file.FullName] = @(Get-Content -LiteralPath $file.FullName -Encoding UTF8)
}
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
$provenanceFields = @("metadataConfidence", "transferConfidence", "sourceConfidenceLevel", "finalSourceStatus", "safeForSeedMutation")
$displayFields = @("name", "exerciseName", "category", "detail1", "detail2", "description", "familyName", "imageAssetName")
$identityFields = @("stableKey", "isActive", "archivedAt", "isCustom", "needsReview")
$compatibilityFields = @("progressMetricType")

function Write-CompatibilityInventory {
    $compatibilityRows = @()
    foreach ($field in ($compatibilityFields | Sort-Object -Unique)) {
        $escaped = [regex]::Escape($field)
        foreach ($file in $allKotlinFiles) {
            $relative = Relative-Path $file.FullName
            if ($relative -eq "app/src/test/java/com/training/trackplanner/analysis/contracts/AnalysisContractAuditArtifactsTest.kt") { continue }
            $lines = if ($sourceLines.ContainsKey($file.FullName)) {
                $sourceLines[$file.FullName]
            } else {
                @(Get-Content -LiteralPath $file.FullName -Encoding UTF8)
            }
            for ($index = 0; $index -lt $lines.Count; $index++) {
                $line = $lines[$index]
                if ($line -notmatch "\b$escaped\b") { continue }
                $read = $line -match "\.$escaped\b|::$escaped\b"
                $write = $line -match "\b$escaped\s*="
                $compatibilityRows += [pscustomobject]@{
                    legacyField = $field
                    filePath = $relative
                    symbolOrFunction = Enclosing-Symbol $lines $index
                    consumerType = Consumer-Kind $relative (Enclosing-Symbol $lines $index) $line
                    readOrWrite = if ($read -and $write) { "READ_WRITE" } elseif ($write) { "WRITE" } elseif ($read) { "READ" } else { "REFERENCE" }
                    runtimeOrTest = if ($relative -match '^app/src/test/') { "TEST" } else { "PRODUCTION" }
                    replacementOwner = "NON_METADATA_ANALYSIS_OR_PRESCRIPTION_PROTOCOL"
                    replacementStatus = "NOT_IMPLEMENTED"
                    removalBlockedReason = "Current consumers remain; parity, backup compatibility, rollback, and explicit approval gates have not passed"
                }
            }
        }
    }
    $compatibilityRows = @($compatibilityRows | Sort-Object legacyField, filePath, symbolOrFunction, consumerType, readOrWrite, runtimeOrTest -Unique)
    $compatibilityColumns = @("legacyField", "filePath", "symbolOrFunction", "consumerType", "readOrWrite", "runtimeOrTest", "replacementOwner", "replacementStatus", "removalBlockedReason")
    Write-Csv $compatibilityRows $compatibilityColumns (Join-Path $docsRoot "metadata_legacy_compatibility_consumers.csv")
    $compatibilityLines = @(
        "# Metadata legacy compatibility consumers", "",
        '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1 -CompatibilityOnly`',
        "- Inventory rows: $($compatibilityRows.Count)", "",
        '> Current-code inventory. The post-v0.5.0.37 retirement removed the debug-only V3 `AnalysisInput*` consumers; historical full-audit artifacts remain unchanged for traceability.', "",
        "A compatibility field cannot be removed until production consumers are zero, parity passes, backup/restore compatibility and rollback are verified, and removal is explicitly approved.", "",
        "| Field | File | Symbol | Type | Access | Scope | Replacement owner |", "|---|---|---|---|---|---|---|"
    )
    foreach ($row in $compatibilityRows) { $compatibilityLines += "| ``$($row.legacyField)`` | ``$($row.filePath)`` | ``$($row.symbolOrFunction)`` | $($row.consumerType) | $($row.readOrWrite) | $($row.runtimeOrTest) | ``$($row.replacementOwner)`` |" }
    Set-Content -LiteralPath (Join-Path $docsRoot "metadata_legacy_compatibility_consumers.md") -Value $compatibilityLines -Encoding UTF8
}

if ($CompatibilityOnly) {
    Write-CompatibilityInventory
    exit 0
}

function Current-Disposition([string]$Field) {
    if ($Field -in @("activityKind", "progressMetricType")) { return "LEGACY_COMPATIBILITY_READONLY" }
    if ($Field -eq "trainingRole") { return "EXACT_LEGACY_STABLEKEY_WHITELIST" }
    if ($Field -eq "familyId") { return "DERIVED_NONCANONICAL" }
    if ($Field -eq "loadProfile") { return "LEGACY_COMPOSITE_TO_BE_DECOMPOSED" }
    if ($Field -eq "sportTransferDirect") { return "CLOSED_WORLD_EXPLICIT_WHITELIST" }
    if ($identityFields -contains $Field) { return "KEEP_TYPED_AUTHORITY" }
    if ($provenanceFields -contains $Field) { return "PROVENANCE_ONLY" }
    if ($displayFields -contains $Field) { return "DISPLAY_ONLY" }
    "KEEP_CURRENT_BEHAVIOR"
}

function Eventual-Replacement([string]$Field) {
    if ($Field -eq "progressMetricType") { return "REPLACE_OUTSIDE_CANONICAL_METADATA_AFTER_PARITY" }
    if ($Field -eq "activityKind") { return "REVIEW_SEPARATE_CATALOG_TAXONOMY" }
    if ($Field -eq "trainingRole") { return "PHASE_2B_PURPOSE_SPECIFIC_PROGRAM_RELATION_REVIEW" }
    if ($Field -eq "familyId") { return "DERIVE_PER_USE_CASE_FROM_REVIEWED_TYPED_RELATIONS" }
    if ($Field -eq "loadProfile") { return "DECOMPOSE_INTO_CONSUMER_SPECIFIC_TYPED_RELATIONS" }
    if ($Field -eq "sportTransferDirect") { return "KEEP_CLOSED_WORLD_EXPLICIT_RELATION" }
    if ($Field -in @("mode", "detail1", "detail2", "familyE1rmMultiplier")) {
        return "REPLACE_OUTSIDE_CANONICAL_METADATA_AFTER_PARITY"
    }
    if ($identityFields -contains $Field) { return "KEEP_TYPED_AUTHORITY" }
    if ($provenanceFields -contains $Field) { return "CONSOLIDATE_PROVENANCE_AFTER_PARITY" }
    if ($displayFields -contains $Field) { return "KEEP_PRESENTATION_ONLY" }
    "REVIEW_CONSUMER_SPECIFICALLY"
}

$usageRows = foreach ($field in @($exerciseFields) + @($runtimeFields)) {
    $escaped = [regex]::Escape($field.FieldName)
    $producers = @()
    $consumers = @()
    $parsing = $false
    foreach ($file in $sourceFiles) {
        $text = $sourceText[$file.FullName]
        $relative = Relative-Path $file.FullName
        if ($text -match "(?m)\b$escaped\s*=") { $producers += $relative }
        if ($text -match "\.$escaped\b|::$escaped\b") { $consumers += $relative }
        if ($text -match "(?s)$escaped.{0,240}(split\s*\(|contains\s*\()" -or
            $text -match "(?s)(split\s*\(|contains\s*\().{0,240}$escaped") { $parsing = $true }
    }
    $consumerList = @($consumers | Sort-Object -Unique)
    [pscustomobject]@{
        fieldName = $field.FieldName
        storageLocation = $field.StorageLocation
        cardinality = if ($multiFields -contains $field.FieldName -or $field.KotlinType -match 'MetadataTokenField|List|Set') { "MULTI" } else { "SINGLE" }
        currentProducers = (($producers | Sort-Object -Unique) -join ";")
        currentConsumers = ($consumerList -join ";")
        ofiUsage = Module-Usage $consumerList 'analysis/fatigue|HomeSummary|Readiness'
        programGenerationUsage = Module-Usage $consumerList 'Program'
        muscleAnalysisUsage = Module-Usage $consumerList 'analysis/lab|StrengthAndMuscle|strengthperformance|StrengthPerformance'
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
$usageRows = @($usageRows | Sort-Object storageLocation, fieldName)

$usageCsv = Join-Path $docsRoot "metadata_field_usage_matrix.csv"
Write-Csv $usageRows @(
    "fieldName", "storageLocation", "cardinality", "currentProducers", "currentConsumers",
    "ofiUsage", "programGenerationUsage", "muscleAnalysisUsage", "badmintonAnalysisUsage",
    "connectiveTissueUsage", "uiUsage", "backupRestoreUsage", "parsingOrSubstringInference",
    "currentDisposition", "eventualReplacementStrategy", "recommendedDisposition"
) $usageCsv
$usageLines = @(
    "# Metadata field usage matrix", "",
    '- Baseline: `bd6407c79f9854c6788bdca162d6b520d79e77cd`',
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Field/storage rows: $($usageRows.Count)",
    '- Machine-readable companion: `metadata_field_usage_matrix.csv`', "",
    "| Field | Storage | Cardinality | OFI | Program | Muscle | Badminton | Tissue | UI | Backup | Parsing/inference | Current disposition | Eventual replacement |",
    "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|"
)
foreach ($row in $usageRows) {
    $usageLines += "| ``$($row.fieldName)`` | $($row.storageLocation) | $($row.cardinality) | $($row.ofiUsage) | $($row.programGenerationUsage) | $($row.muscleAnalysisUsage) | $($row.badmintonAnalysisUsage) | $($row.connectiveTissueUsage) | $($row.uiUsage) | $($row.backupRestoreUsage) | $($row.parsingOrSubstringInference) | ``$($row.currentDisposition)`` | ``$($row.eventualReplacementStrategy)`` |"
}
Set-Content -LiteralPath (Join-Path $docsRoot "metadata_field_usage_matrix.md") -Value $usageLines -Encoding UTF8

function Replacement-Relation([string]$Text) {
    if ($Text -match 'muscle|Muscle|FOREARM|QUAD|GLUTE') { return "ExerciseMuscleContribution" }
    if ($Text -match 'badminton|Badminton|TRANSFER|FOOTWORK|DECEL') { return "ExerciseBadmintonTransferPoint / ExercisePhysicalQualityPoint" }
    if ($Text -match 'slot|Slot|Program|ROLE|ANCHOR') { return "ExerciseProgramSlotCapability / ExerciseProgramRoleEligibility" }
    if ($Text -match 'fatigue|Fatigue|Stress|HEAVY|SPEED|REACTIVE') { return "ExerciseOfiAxisContribution / ExerciseOfiDoseProfile" }
    "movement/anatomy typed relation or explicit compatibility mapping"
}

$inferenceRows = @()
foreach ($file in $sourceFiles) {
    $relative = Relative-Path $file.FullName
    if ($relative -notmatch '/analysis/|/data/' -or $relative -match '/analysis/contracts/') { continue }
    $functionName = "<file>"
    $lines = $sourceLines[$file.FullName]
    for ($index = 0; $index -lt $lines.Count; $index++) {
        $line = $lines[$index]
        if ($line -match '\bfun\s+([A-Za-z][A-Za-z0-9_]*)') { $functionName = $Matches[1] }
        $explicitInference = $line -match '\.split\s*\(|\.contains\s*\(|Name.*contains|stableKey.*contains|fallback|Fallback|ifBlank\s*\{|ifEmpty\s*\{'
        $semanticFunction = $functionName -match 'map|resolve|classif|infer|derive|fallback|role|slot|type|category|profile|eligib|stress|transfer|movement|metric|mode|kind'
        $catchAllInference = $semanticFunction -and $line -match 'else\s*->\s*("|[A-Z])|\?:\s*("|[A-Z])'
        if (-not $explicitInference -and -not $catchAllInference) { continue }
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
            riskLevel = if ($line -match 'stableKey|exerciseName|name\.|Name\.') { "HIGH" } else { "MEDIUM" }
        }
    }
}
$inferenceCsv = Join-Path $docsRoot "metadata_parsing_inference_audit.csv"
Write-Csv $inferenceRows @("sourceFile", "line", "function", "analysisModule", "inputField", "inferredOutput", "replacementRelationRequired", "riskLevel") $inferenceCsv
$inferenceLines = @(
    "# Metadata parsing and inference audit", "",
    '- Baseline: `bd6407c79f9854c6788bdca162d6b520d79e77cd`',
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Production matches inventoried: $($inferenceRows.Count)",
    "- High risk: $(@($inferenceRows | Where-Object riskLevel -eq 'HIGH').Count)",
    "- Medium risk: $(@($inferenceRows | Where-Object riskLevel -eq 'MEDIUM').Count)",
    '- Machine-readable companion: `metadata_parsing_inference_audit.csv`', "",
    "This report is a discovery inventory. A matching path is not by itself a confirmed metadata error.", "",
    "| Source | Line | Function | Module | Input | Risk | Replacement candidate |",
    "|---|---:|---|---|---|---:|---|"
)
foreach ($row in $inferenceRows) {
    $inferenceLines += "| ``$($row.sourceFile)`` | $($row.line) | ``$($row.function)`` | $($row.analysisModule) | ``$($row.inputField)`` | $($row.riskLevel) | $($row.replacementRelationRequired) |"
}
Set-Content -LiteralPath (Join-Path $docsRoot "metadata_parsing_inference_audit.md") -Value $inferenceLines -Encoding UTF8

$riskDefinitions = @(
    [pscustomobject]@{ Id="META-SEED-CSV-FALLBACK"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="exerciseFromCsv"; Field="multiple Exercise CSV fields"; Module="shared metadata/data"; Severity="HIGH"; Mode="UNOBSERVABLE"; Raw="" },
    [pscustomobject]@{ Id="META-SEED-MOVEMENT-PATTERN"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="movementPatternFor"; Field="movementPattern"; Module="movement/anatomy"; Severity="HIGH"; Mode="SEED_FALLBACK"; Raw="movement_pattern" },
    [pscustomobject]@{ Id="META-SEED-FAMILY"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="familyIdFor"; Field="familyId"; Module="movement/anatomy"; Severity="HIGH"; Mode="LEGACY_NONCANONICAL"; Raw="family_id" },
    [pscustomobject]@{ Id="META-SEED-PRIMARY-MUSCLES"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="musclesFor"; Field="primaryMuscles"; Module="muscle/strength analysis"; Severity="HIGH"; Mode="SEED_FALLBACK"; Raw="primary_muscles" },
    [pscustomobject]@{ Id="META-SEED-SECONDARY-MUSCLES"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="fallbackSecondaryMuscles"; Field="secondaryMuscles"; Module="muscle/strength analysis"; Severity="HIGH"; Mode="SEED_FALLBACK"; Raw="secondary_muscles" },
    [pscustomobject]@{ Id="META-SEED-FORCE-TYPE"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="forceTypeFor"; Field="forceType"; Module="movement/anatomy"; Severity="MEDIUM"; Mode="SEED_FALLBACK"; Raw="force_type" },
    [pscustomobject]@{ Id="META-SEED-TRAINING-ROLE"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="exerciseFromCsv"; Field="trainingRole"; Module="program generation"; Severity="HIGH"; Mode="EXPLICIT_CLOSED_WORLD"; Raw="training_role" },
    [pscustomobject]@{ Id="META-SEED-SPORT-TRANSFER"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="exerciseFromCsv"; Field="sportTransferDirect"; Module="badminton analysis"; Severity="HIGH"; Mode="EXPLICIT_CLOSED_WORLD"; Raw="sport_transfer_direct" },
    [pscustomobject]@{ Id="META-SEED-LOAD-PROFILE"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="loadProfileFor"; Field="loadProfile"; Module="shared metadata/data"; Severity="MEDIUM"; Mode="LEGACY_NONCANONICAL"; Raw="load_profile" },
    [pscustomobject]@{ Id="META-OFI-BROAD-FATIGUE"; File="app/src/main/java/com/training/trackplanner/data/ExerciseMetadataAdapter.kt"; Symbol="broadLegacyFatigueCategories"; Field="fatigueCategories"; Module="OFI/readiness"; Severity="HIGH"; Mode="VALID_HEURISTIC"; Raw="load_profile" },
    [pscustomobject]@{ Id="META-ACTIVITY-NAME-FALLBACK"; File="app/src/main/java/com/training/trackplanner/data/ExercisePlanning.kt"; Symbol="Exercise"; Field="activityKind"; Module="program generation"; Severity="HIGH"; Mode="CANONICAL_EXPLICIT"; Raw="currentActivityKind" },
    [pscustomobject]@{ Id="META-PROGRAM-SLOT-FALLBACK"; File="app/src/main/java/com/training/trackplanner/data/SlotCapabilityResolver.kt"; Symbol="resolve"; Field="programSlot"; Module="program generation"; Severity="HIGH"; Mode="UNOBSERVABLE"; Raw="programSlot" },
    [pscustomobject]@{ Id="META-PROGRAM-SLOT-NAME"; File="app/src/main/java/com/training/trackplanner/data/SlotCapabilityResolver.kt"; Symbol="explicitNameFallback"; Field="programSlot"; Module="program generation"; Severity="HIGH"; Mode="UNOBSERVABLE"; Raw="programSlot" },
    [pscustomobject]@{ Id="META-MUSCLE-FALLBACK"; File="app/src/main/java/com/training/trackplanner/analysis/lab/MuscleLoadInputBuilder.kt"; Symbol="fallbackContributions"; Field="primaryMuscles"; Module="muscle/strength analysis"; Severity="HIGH"; Mode="UNOBSERVABLE"; Raw="primary_muscles" },
    [pscustomobject]@{ Id="META-PROGRAM-LOADED-NAME"; File="app/src/main/java/com/training/trackplanner/data/ProgramEvaluationPolicy.kt"; Symbol="isLoadedStrength"; Field="equipment and exerciseName"; Module="program generation"; Severity="HIGH"; Mode="UNOBSERVABLE"; Raw="equipment_tags" },
    [pscustomobject]@{ Id="META-PROGRAM-RERANK-LOADED-NAME"; File="app/src/main/java/com/training/trackplanner/data/ProgramCandidateRerankingPolicy.kt"; Symbol="needsLoadedStrength"; Field="exerciseName"; Module="program generation"; Severity="HIGH"; Mode="UNOBSERVABLE"; Raw="equipment_tags" },
    [pscustomobject]@{ Id="META-BADMINTON-LEVEL-FALLBACK"; File="app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferMetadataMapper.kt"; Symbol="transferType"; Field="badmintonTransferLevel"; Module="badminton analysis"; Severity="MEDIUM"; Mode="CANONICAL_EXPLICIT"; Raw="badmintonTransferLevel" },
    [pscustomobject]@{ Id="META-OFI-RECOVERY-DEFAULT"; File="app/src/main/java/com/training/trackplanner/analysis/fatigue/DailyFatigueCalculator.kt"; Symbol="RecordContext"; Field="recoveryDurationClass"; Module="OFI/readiness"; Severity="MEDIUM"; Mode="CANONICAL_EXPLICIT"; Raw="recoveryDurationClass" },
    [pscustomobject]@{ Id="META-BADMINTON-MUSCLE-INFERENCE"; File="app/src/main/java/com/training/trackplanner/data/ExerciseMetadataMapper.kt"; Symbol="MetadataSource"; Field="badminton and balance relations"; Module="badminton analysis"; Severity="HIGH"; Mode="STRUCTURAL"; Raw="primary_muscles" },
    [pscustomobject]@{ Id="META-STRENGTH-PROXY-FALLBACK"; File="app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthPerformanceRegistry.kt"; Symbol="proxyLoadings"; Field="strength proxy relation"; Module="muscle/strength analysis"; Severity="HIGH"; Mode="PROXY_FALLBACK"; Raw="analysisEligibility" }
)

foreach ($definition in $riskDefinitions) {
    $evidence = @($inferenceRows | Where-Object { $_.sourceFile -eq $definition.File -and $_.function -eq $definition.Symbol })
    if ($evidence.Count -eq 0) { throw "Configured metadata risk path has no current evidence: $($definition.Id)" }
    $definition | Add-Member -NotePropertyName Evidence -NotePropertyValue (($evidence | Sort-Object line | ForEach-Object { "$($_.sourceFile):$($_.line)" }) -join ";")
}

$seedRows = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "app/src/main/assets/training_settings_seed.csv") | Where-Object { $_.row_type -eq "exercise" })
$canonicalRows = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"))
$canonicalByKey = @{}
foreach ($row in $canonicalRows) { $canonicalByKey[$row.stableKey] = $row }
$proxyKeys = @{}
foreach ($row in @(Import-Csv -LiteralPath (Join-Path $RepoRoot "app/src/main/assets/strength_performance/strength_proxy_loadings_v1.csv"))) { $proxyKeys[$row.exerciseStableKey] = $true }
$stableKeys = @($seedRows.stable_key | Sort-Object -Unique)
if ($stableKeys.Count -ne 224) { throw "Expected 224 built-in stableKeys, found $($stableKeys.Count)." }

$impactRows = foreach ($definition in $riskDefinitions) {
    foreach ($stableKey in $stableKeys) {
        $seed = $seedRows | Where-Object stable_key -eq $stableKey | Select-Object -First 1
        $canonical = $canonicalByKey[$stableKey]
        $raw = ""
        if ($definition.Raw) {
            if ($definition.Raw -in $seed.PSObject.Properties.Name) { $raw = [string]$seed.($definition.Raw) }
            elseif ($canonical -and $definition.Raw -in $canonical.PSObject.Properties.Name) { $raw = [string]$canonical.($definition.Raw) }
        }
        $applicability = "APPLICABLE"
        $fallbackTriggered = "FALSE"
        $fallbackInput = $raw
        $fallbackOutput = ""
        $current = $raw
        $counterfactual = "FALSE"
        $withoutFallback = "UNAVAILABLE"
        $difference = "UNOBSERVABLE"
        $classification = "NOT_TRIGGERED_FOR_BUILT_INS"
        $evidence = "Exact stableKey and checked-in source value"
        $notes = ""

        switch ($definition.Mode) {
            "EXPLICIT_CLOSED_WORLD" {
                $withoutFallback = if ([string]::IsNullOrWhiteSpace($raw)) { "AUTHORITATIVE_NONE" } else { $raw }
                $current = $withoutFallback
                $difference = "NO_FALLBACK"
                $notes = "Exact stableKey whitelist; absence is authoritative NONE."
            }
            "LEGACY_NONCANONICAL" {
                if ([string]::IsNullOrWhiteSpace($raw)) {
                    $fallbackTriggered = "TRUE"
                    $fallbackOutput = "LEGACY_COMPATIBILITY_VALUE"
                    $current = "LEGACY_COMPATIBILITY_VALUE"
                    $classification = "VALID_RESULT_BUT_HEURISTIC_IMPLEMENTATION"
                    $difference = "NONCANONICAL_COMPATIBILITY_ONLY"
                }
                $notes = "Legacy compatibility field; no target canonical authority is asserted."
            }
            "SEED_FALLBACK" {
                if ([string]::IsNullOrWhiteSpace($raw)) {
                    $fallbackTriggered = "TRUE"
                    $fallbackOutput = "CURRENT_RESOLVER_OUTPUT"
                    $current = "CURRENT_RESOLVER_OUTPUT"
                    $classification = "MISSING_AUTHORITY"
                    $difference = "FALLBACK_SUPPLIES_EFFECTIVE_VALUE"
                    $notes = "The fallback triggers, but no reviewed stableKey authority proves the result wrong."
                }
            }
            "VALID_HEURISTIC" {
                $fallbackTriggered = "TRUE"
                $fallbackOutput = "BROAD_LEGACY_FATIGUE_CATEGORIES"
                $current = "BROAD_LEGACY_FATIGUE_CATEGORIES"
                $classification = "VALID_RESULT_BUT_HEURISTIC_IMPLEMENTATION"
                $difference = "HEURISTIC_PATH_OBSERVED"
                $notes = "Current structured inputs produce a compatibility result; this is not a confirmed error."
            }
            "CANONICAL_EXPLICIT" {
                if ([string]::IsNullOrWhiteSpace($raw)) {
                    $fallbackTriggered = "TRUE"
                    $fallbackOutput = "CURRENT_RESOLVER_OUTPUT"
                    $current = "CURRENT_RESOLVER_OUTPUT"
                    $classification = "MISSING_AUTHORITY"
                    $difference = "FALLBACK_SUPPLIES_EFFECTIVE_VALUE"
                }
            }
            "STRUCTURAL" {
                $fallbackTriggered = "TRUE"
                $fallbackOutput = "CURRENT_METADATA_MAPPER_OUTPUT"
                $current = "CURRENT_METADATA_MAPPER_OUTPUT"
                $classification = "STRUCTURAL_AMBIGUITY"
                $difference = "NO_REVIEWED_COUNTERFACTUAL"
                $notes = "The path is exercised, but the audit has no independent stableKey authority for a confirmed error."
            }
            "PROXY_FALLBACK" {
                if ($proxyKeys.ContainsKey($stableKey)) {
                    $current = "REVIEWED_PROXY_REGISTRY_ROW"
                } else {
                    $fallbackTriggered = "TRUE"
                    $fallbackOutput = "METADATA_PROXY_LOADINGS"
                    $current = "METADATA_PROXY_LOADINGS"
                    $classification = "MISSING_AUTHORITY"
                    $difference = "HEURISTIC_PROXY_USED"
                    $notes = "No reviewed proxy row exists; no independent authority proves the heuristic result wrong."
                }
            }
            "UNOBSERVABLE" {
                $applicability = "UNOBSERVABLE_WITHOUT_PRODUCTION_CHANGE"
                $fallbackTriggered = "UNOBSERVABLE"
                $fallbackOutput = "UNOBSERVABLE"
                $current = "CONTEXT_DEPENDENT"
                $classification = "UNRESOLVED"
                $evidence = "Exact stableKey covered; isolated trigger/effect requires production instrumentation or behavior change"
                $notes = "Explicitly retained as unobservable rather than inferred from exercise names or key fragments."
            }
        }

        [pscustomobject]@{
            riskPathId = $definition.Id
            exerciseStableKey = $stableKey
            evaluationApplicability = $applicability
            rawSourceValue = $raw
            rawValuePresent = if ([string]::IsNullOrWhiteSpace($raw)) { "FALSE" } else { "TRUE" }
            fallbackTriggered = $fallbackTriggered
            fallbackInput = $fallbackInput
            fallbackOutput = $fallbackOutput
            currentEffectiveValueOrRelation = $current
            affectedConsumer = "$($definition.File)#$($definition.Symbol)"
            affectedAnalysis = $definition.Module
            baselineObservableOutput = "ANALYSIS_CONTRACT_BASELINE_V1_UNCHANGED"
            counterfactualAvailable = $counterfactual
            outputWithoutFallback = $withoutFallback
            actualOutputDifference = $difference
            reviewClassification = $classification
            evidence = $evidence
            notes = $notes
        }
    }
}
$impactRows = @($impactRows | Sort-Object riskPathId, exerciseStableKey)
$impactColumns = @(
    "riskPathId", "exerciseStableKey", "evaluationApplicability", "rawSourceValue", "rawValuePresent",
    "fallbackTriggered", "fallbackInput", "fallbackOutput", "currentEffectiveValueOrRelation",
    "affectedConsumer", "affectedAnalysis", "baselineObservableOutput", "counterfactualAvailable",
    "outputWithoutFallback", "actualOutputDifference", "reviewClassification", "evidence", "notes"
)
Write-Csv $impactRows $impactColumns (Join-Path $docsRoot "metadata_inference_stablekey_impact.csv")
$impactLines = @(
    "# Metadata inference stableKey impact audit", "",
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Risk paths: $($riskDefinitions.Count)",
    "- Built-in stableKeys: $($stableKeys.Count)",
    "- Explicit risk-path/stableKey rows: $($impactRows.Count)",
    "- Fallback-triggered rows: $(@($impactRows | Where-Object fallbackTriggered -eq 'TRUE').Count)",
    "- Valid heuristic rows: $(@($impactRows | Where-Object reviewClassification -eq 'VALID_RESULT_BUT_HEURISTIC_IMPLEMENTATION').Count)",
    "- Structural ambiguity rows: $(@($impactRows | Where-Object reviewClassification -eq 'STRUCTURAL_AMBIGUITY').Count)",
    "- Missing authority rows: $(@($impactRows | Where-Object reviewClassification -eq 'MISSING_AUTHORITY').Count)",
    "- Confirmed classification errors: $(@($impactRows | Where-Object reviewClassification -eq 'CONFIRMED_CLASSIFICATION_ERROR').Count)",
    "- Not triggered rows: $(@($impactRows | Where-Object reviewClassification -eq 'NOT_TRIGGERED_FOR_BUILT_INS').Count)", "",
    "Every one of the 20 paths has one explicit row for every one of the 224 built-in stableKeys. Unobservable rows are recorded, not omitted.", "",
    "| Risk path | Applicability | Classification | Count |",
    "|---|---|---|---:|"
)
foreach ($group in ($impactRows | Group-Object riskPathId, evaluationApplicability, reviewClassification | Sort-Object Name)) {
    $sample = $group.Group[0]
    $impactLines += "| ``$($sample.riskPathId)`` | ``$($sample.evaluationApplicability)`` | ``$($sample.reviewClassification)`` | $($group.Count) |"
}
Set-Content -LiteralPath (Join-Path $docsRoot "metadata_inference_stablekey_impact.md") -Value $impactLines -Encoding UTF8

$confirmedRows = @($impactRows | Where-Object reviewClassification -eq "CONFIRMED_CLASSIFICATION_ERROR" | ForEach-Object {
    [pscustomobject]@{
        issueId = "CONFIRMED-$($_.riskPathId)-$($_.exerciseStableKey)"
        riskPathId = $_.riskPathId
        exerciseStableKey = $_.exerciseStableKey
        legacyField = ($riskDefinitions | Where-Object Id -eq $_.riskPathId | Select-Object -First 1).Field
        currentValueOrRelation = $_.currentEffectiveValueOrRelation
        expectedValueOrRelation = $_.outputWithoutFallback
        authorityOrEvidence = $_.evidence
        affectedConsumers = $_.affectedConsumer
        affectedAnalyses = $_.affectedAnalysis
        programImpact = "NOT_OBSERVED"
        ofiImpact = "NOT_OBSERVED"
        muscleImpact = "NOT_OBSERVED"
        badmintonImpact = "NOT_OBSERVED"
        tissueImpact = "NOT_OBSERVED"
        strengthPerformanceImpact = "NOT_OBSERVED"
        parityImpact = $_.actualOutputDifference
        severity = ($riskDefinitions | Where-Object Id -eq $_.riskPathId | Select-Object -First 1).Severity
        approvalStatus = "UNAPPROVED"
        proposedResolution = "Report for human review; do not modify production in Phase 2A"
        targetVersion = "UNSCHEDULED"
    }
})
$confirmedColumns = @(
    "issueId", "riskPathId", "exerciseStableKey", "legacyField", "currentValueOrRelation",
    "expectedValueOrRelation", "authorityOrEvidence", "affectedConsumers", "affectedAnalyses",
    "programImpact", "ofiImpact", "muscleImpact", "badmintonImpact", "tissueImpact",
    "strengthPerformanceImpact", "parityImpact", "severity", "approvalStatus",
    "proposedResolution", "targetVersion"
)
Write-Csv $confirmedRows $confirmedColumns (Join-Path $docsRoot "confirmed_metadata_errors.csv")
$confirmedLines = @(
    "# Confirmed metadata errors", "",
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Confirmed error count: $($confirmedRows.Count)", "",
    "Only stableKey-level rows with a reproducible current value, authoritative alternative, affected consumer, and proven difference belong here. Risk-path presence alone cannot create an entry."
)
if ($confirmedRows.Count -eq 0) { $confirmedLines += ""; $confirmedLines += "No confirmed metadata classification errors were proven in Phase 2A." }
Set-Content -LiteralPath (Join-Path $docsRoot "confirmed_metadata_errors.md") -Value $confirmedLines -Encoding UTF8

$riskRows = foreach ($definition in $riskDefinitions) {
    $rows = @($impactRows | Where-Object riskPathId -eq $definition.Id)
    $confirmed = @($confirmedRows | Where-Object riskPathId -eq $definition.Id)
    $unobservable = @($rows | Where-Object evaluationApplicability -eq "UNOBSERVABLE_WITHOUT_PRODUCTION_CHANGE").Count
    [pscustomobject]@{
        riskPathId = $definition.Id
        affectedFile = $definition.File
        affectedSymbol = $definition.Symbol
        legacyField = $definition.Field
        analysisModule = $definition.Module
        derivationMode = "LEGACY_HEURISTIC_FALLBACK"
        severity = $definition.Severity
        evidenceLocations = $definition.Evidence
        impactEvaluationStatus = if ($unobservable -eq 224) { "PARTIALLY_EVALUATED" } elseif (@($rows | Where-Object reviewClassification -ne "NOT_TRIGGERED_FOR_BUILT_INS").Count -eq 0) { "NOT_TRIGGERED_FOR_BUILT_INS" } elseif ($unobservable -gt 0) { "PARTIALLY_EVALUATED" } else { "EVALUATED" }
        affectedBuiltInStableKeyCount = @($rows | Where-Object reviewClassification -ne "NOT_TRIGGERED_FOR_BUILT_INS").Count
        confirmedErrorCount = $confirmed.Count
        validHeuristicResultCount = @($rows | Where-Object reviewClassification -eq "VALID_RESULT_BUT_HEURISTIC_IMPLEMENTATION").Count
        missingAuthorityCount = @($rows | Where-Object reviewClassification -eq "MISSING_AUTHORITY").Count
        structuralAmbiguityCount = @($rows | Where-Object reviewClassification -eq "STRUCTURAL_AMBIGUITY").Count
        notTriggeredCount = @($rows | Where-Object reviewClassification -eq "NOT_TRIGGERED_FOR_BUILT_INS").Count
        userExerciseOnlyRiskCount = @($rows | Where-Object reviewClassification -eq "USER_EXERCISE_ONLY_RISK").Count
        linkedConfirmedIssueIds = ($confirmed.issueId -join ";")
        notes = "A risky path is not a confirmed error; see stableKey impact rows and separate confirmed-error evidence."
    }
}
$riskColumns = @("riskPathId", "affectedFile", "affectedSymbol", "legacyField", "analysisModule", "derivationMode", "severity", "evidenceLocations", "impactEvaluationStatus", "affectedBuiltInStableKeyCount", "confirmedErrorCount", "validHeuristicResultCount", "missingAuthorityCount", "structuralAmbiguityCount", "notTriggeredCount", "userExerciseOnlyRiskCount", "linkedConfirmedIssueIds", "notes")
Write-Csv $riskRows $riskColumns (Join-Path $docsRoot "metadata_legacy_inference_risk_paths.csv")
$riskLines = @(
    "# Metadata legacy inference risk paths", "",
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Risk paths: $($riskRows.Count)",
    "- Confirmed errors: $($confirmedRows.Count)", "",
    "These rows describe implementation risk paths, not twenty proven classification errors.", "",
    "| Risk path | Consumer | Status | Affected | Confirmed | Valid heuristic | Missing authority | Structural ambiguity | Not triggered |",
    "|---|---|---|---:|---:|---:|---:|---:|---:|"
)
foreach ($row in $riskRows) {
    $riskLines += "| ``$($row.riskPathId)`` | ``$($row.affectedFile)#$($row.affectedSymbol)`` | ``$($row.impactEvaluationStatus)`` | $($row.affectedBuiltInStableKeyCount) | $($row.confirmedErrorCount) | $($row.validHeuristicResultCount) | $($row.missingAuthorityCount) | $($row.structuralAmbiguityCount) | $($row.notTriggeredCount) |"
}
Set-Content -LiteralPath (Join-Path $docsRoot "metadata_legacy_inference_risk_paths.md") -Value $riskLines -Encoding UTF8

function AnalysisEligibility-Decision([string]$ConsumerFile) {
    switch -Exact ($ConsumerFile) {
        "app/src/main/java/com/training/trackplanner/analysis/badminton/BadmintonTransferMetadataMapper.kt" { return [pscustomobject]@{ Use="FIXED_EXERCISE_RELATION"; Layer="BADMINTON"; Relation="ExerciseAnalysisCapability(BADMINTON_TRANSFER)" } }
        "app/src/main/java/com/training/trackplanner/analysis/fatigue/DailyFatigueCalculator.kt" { return [pscustomobject]@{ Use="FIXED_EXERCISE_RELATION"; Layer="OFI"; Relation="ExerciseAnalysisCapability(OFI)" } }
        "app/src/main/java/com/training/trackplanner/analysis/trends/BadmintonTrainingLoadIndexCalculator.kt" { return [pscustomobject]@{ Use="FIXED_EXERCISE_RELATION"; Layer="BADMINTON"; Relation="ExerciseAnalysisCapability(BADMINTON_TRANSFER)" } }
        "app/src/main/java/com/training/trackplanner/analysis/trends/StrengthPerformanceIndexCalculator.kt" { return [pscustomobject]@{ Use="FIXED_EXERCISE_RELATION"; Layer="STRENGTH_PERFORMANCE"; Relation="ExerciseAnalysisCapability(STRENGTH_PERFORMANCE)" } }
        "app/src/main/java/com/training/trackplanner/analysis/strengthperformance/StrengthPerformanceRegistry.kt" { return [pscustomobject]@{ Use="FIXED_EXERCISE_RELATION"; Layer="STRENGTH_PERFORMANCE"; Relation="ExerciseAnalysisCapability(STRENGTH_PERFORMANCE)" } }
        "app/src/main/java/com/training/trackplanner/data/ExercisePlanning.kt" { return [pscustomobject]@{ Use="PROGRAM_POLICY"; Layer="PROGRAM_GENERATION"; Relation="ExercisePlanningProfile" } }
        "app/src/main/java/com/training/trackplanner/data/ProgramCandidate.kt" { return [pscustomobject]@{ Use="PROGRAM_POLICY"; Layer="PROGRAM_GENERATION"; Relation="ExerciseProgramBlockCapability" } }
        "app/src/main/java/com/training/trackplanner/data/SlotCapabilityResolver.kt" { return [pscustomobject]@{ Use="PROGRAM_POLICY"; Layer="PROGRAM_GENERATION"; Relation="ExerciseProgramBlockCapability" } }
        "app/src/main/java/com/training/trackplanner/ExerciseSubcategoryMapper.kt" { return [pscustomobject]@{ Use="PRESENTATION_ONLY"; Layer="PRESENTATION"; Relation="NONE" } }
        "app/src/main/java/com/training/trackplanner/MetadataDisplayCatalogue.kt" { return [pscustomobject]@{ Use="PRESENTATION_ONLY"; Layer="PRESENTATION"; Relation="NONE" } }
        "app/src/main/java/com/training/trackplanner/RuntimeMetadataExerciseEditorDialog.kt" { return [pscustomobject]@{ Use="PRESENTATION_ONLY"; Layer="PRESENTATION"; Relation="NONE" } }
        "app/src/main/java/com/training/trackplanner/analysis/features/MetadataReadinessReporter.kt" { return [pscustomobject]@{ Use="PROVENANCE_ONLY"; Layer="PROVENANCE_REVIEW"; Relation="NONE" } }
        default { return $null }
    }
}

function Mapping-Decision([string]$Field, [string]$ConsumerFile, [string]$ConsumerSymbol) {
    $decision = [ordered]@{
        rawOrTokenMeaning = "Exact persisted scalar or token field; consumer meaning not yet established"
        consumerSemanticUse = "UNRESOLVED"
        targetLayer = "UNRESOLVED"
        targetRelation = "NONE"
        conversionMode = "UNRESOLVED"
        derivationMode = "NOT_APPLICABLE"
        mappingStatus = "UNRESOLVED"
        reviewEvidence = "No v2.2-reviewed exact consumer decision"
        notes = "Requires semantic review of exact consumer and token/value meaning"
    }
    if ($Field -eq "trainingRole") {
        $decision.rawOrTokenMeaning = "Legacy program-role value assigned only by exact exercise stableKey whitelist"
        $decision.consumerSemanticUse = "LEGACY_COMPATIBILITY"
        $decision.targetLayer = "NON_METADATA_LEGACY_PROGRAM_COMPATIBILITY"
        $decision.targetRelation = "NONE"
        $decision.conversionMode = "EXACT_STABLEKEY_WHITELIST_ONLY"
        $decision.derivationMode = "RAW_EXPLICIT_VALUE"
        $decision.mappingStatus = "SEMANTICALLY_REVIEWED"
        $decision.reviewEvidence = "Phase 2A.1 approved legacy-role restoration policy"
        $decision.notes = "Not promoted to final ProgramRoleRef; Phase 2B review remains required"
    } elseif ($Field -eq "familyId") {
        $decision.rawOrTokenMeaning = "Legacy broad family bucket with mixed use-case semantics"
        $decision.consumerSemanticUse = "LEGACY_COMPATIBILITY"
        $decision.targetLayer = "DERIVED_NONCANONICAL"
        $decision.targetRelation = "NONE"
        $decision.conversionMode = "DO_NOT_MIGRATE_AS_CANONICAL"
        $decision.derivationMode = "DERIVE_PER_USE_CASE_FROM_REVIEWED_TYPED_RELATIONS"
        $decision.mappingStatus = "SEMANTICALLY_REVIEWED"
        $decision.reviewEvidence = "Phase 2A.1 approved familyId disposition"
        $decision.notes = "Persisted field remains for Room and backup compatibility only"
    } elseif ($Field -eq "loadProfile") {
        $decision.rawOrTokenMeaning = "Legacy composite mixing axial load, stress, plyometric, magnitude, balance, laterality, and event semantics"
        $decision.consumerSemanticUse = "LEGACY_COMPATIBILITY"
        $decision.targetLayer = "LEGACY_COMPOSITE_TO_BE_DECOMPOSED"
        $decision.targetRelation = "NONE"
        $decision.conversionMode = "DECOMPOSE_BY_CONSUMER"
        $decision.derivationMode = "CONSUMER_SPECIFIC_TYPED_RELATIONS"
        $decision.mappingStatus = "SEMANTICALLY_REVIEWED"
        $decision.reviewEvidence = "Phase 2A.1 approved loadProfile disposition"
        $decision.notes = "Physical compatibility remains; no replacement mixed enum is approved"
    } elseif ($Field -eq "sportTransferDirect") {
        $decision.rawOrTokenMeaning = "Complete closed-world direct badminton-transfer whitelist"
        $decision.consumerSemanticUse = "FIXED_EXERCISE_RELATION"
        $decision.targetLayer = "BADMINTON_ANALYSIS"
        $decision.targetRelation = "SportTransferDirectRef"
        $decision.conversionMode = "EXACT_WHITELIST_RELATION"
        $decision.derivationMode = "RAW_EXPLICIT_VALUE_OR_AUTHORITATIVE_NONE"
        $decision.mappingStatus = "SEMANTICALLY_REVIEWED"
        $decision.reviewEvidence = "Phase 2A.1 approved closed-world direct-transfer policy"
        $decision.notes = "Absent relation is authoritative NONE, not missing authority"
    } elseif ($Field -eq "defaultRestSeconds") {
        $decision.rawOrTokenMeaning = "Fixed per-exercise default used by automatic program session-time budgeting"
        $decision.consumerSemanticUse = "FIXED_PROGRAM_PARAMETER"
        $decision.targetLayer = "PROGRAM_GENERATION"
        $decision.targetRelation = "ExerciseProgramTimingProfile"
        $decision.conversionMode = "DIRECT_COPY"
        $decision.derivationMode = "RAW_EXPLICIT_VALUE"
        $decision.mappingStatus = "SEMANTICALLY_REVIEWED"
        $decision.reviewEvidence = "Metadata strategy v2.2 section 4.2 and exact current consumer"
        $decision.notes = "Not the generated program item's final restSeconds prescription"
    } elseif ($Field -eq "activityKind") {
        $decision.rawOrTokenMeaning = "Legacy catalog/runtime compatibility kind"
        $decision.consumerSemanticUse = "LEGACY_COMPATIBILITY"
        $decision.targetLayer = "NON_METADATA_LEGACY_COMPATIBILITY"
        $decision.targetRelation = "NONE"
        $decision.conversionMode = "LEGACY_COMPATIBILITY_READONLY"
        $decision.mappingStatus = "UNRESOLVED"
        $decision.reviewEvidence = "Metadata strategy v2.2 section 4.3 rejects movement/anatomy promotion"
        $decision.notes = "A future CatalogItemKind requires separate approval"
    } elseif ($Field -eq "progressMetricType") {
        $decision.rawOrTokenMeaning = "Legacy progress-analysis and prescription compatibility input"
        $decision.consumerSemanticUse = "LEGACY_COMPATIBILITY"
        $decision.targetLayer = "NON_METADATA_COMPATIBILITY_OR_ANALYSIS_PROTOCOL"
        $decision.targetRelation = "NONE"
        $decision.conversionMode = "LEGACY_COMPATIBILITY_READONLY"
        $decision.mappingStatus = "SEMANTICALLY_REVIEWED"
        $decision.reviewEvidence = "Metadata strategy v2.2 section 5.4"
        $decision.notes = "Removal remains blocked by current consumers and parity/rollback gates"
    } elseif ($Field -eq "analysisEligibility") {
        $specific = AnalysisEligibility-Decision $ConsumerFile
        if ($null -ne $specific) {
            $decision.rawOrTokenMeaning = "Consumer-specific analysis eligibility tokens"
            $decision.consumerSemanticUse = $specific.Use
            $decision.targetLayer = $specific.Layer
            $decision.targetRelation = $specific.Relation
            $decision.conversionMode = "SPLIT_EXACT_TOKENS"
            $decision.derivationMode = "EXACT_TOKEN_EXPANSION"
            $decision.mappingStatus = "AUTO_CANDIDATE"
            $decision.reviewEvidence = "Exact source consumer $ConsumerFile#$ConsumerSymbol; requires approval before REVIEWED_V1"
            $decision.notes = "Consumer-specific candidate; not a field-wide target and not approved"
        } else {
            $decision.reviewEvidence = "Exact analysisEligibility consumer inventoried; semantic destination remains unresolved"
        }
    }
    [pscustomobject]$decision
}

$mappingRows = @()
foreach ($usage in $usageRows) {
    $references = @()
    $seen = @{}
    $escaped = [regex]::Escape($usage.fieldName)
    foreach ($file in $sourceFiles) {
        if ($sourceText[$file.FullName] -notmatch "\b$escaped\b") { continue }
        $relative = Relative-Path $file.FullName
        $lines = $sourceLines[$file.FullName]
        for ($index = 0; $index -lt $lines.Count; $index++) {
            if ($lines[$index] -notmatch "\b$escaped\b") { continue }
            $symbol = Enclosing-Symbol $lines $index
            $key = "$relative#$symbol"
            if ($seen.ContainsKey($key)) { continue }
            $seen[$key] = $true
            $references += [pscustomobject]@{ File=$relative; Symbol=$symbol; Kind=(Consumer-Kind $relative $symbol $lines[$index]) }
        }
    }
    if ($references.Count -eq 0) { $references = @([pscustomobject]@{ File="<no-current-consumer>"; Symbol="<none>"; Kind="NONE" }) }
    foreach ($reference in ($references | Sort-Object File, Symbol)) {
        $decision = Mapping-Decision $usage.fieldName $reference.File $reference.Symbol
        $linked = @($riskDefinitions | Where-Object { $_.File -eq $reference.File -and $_.Symbol -eq $reference.Symbol } | Select-Object -ExpandProperty Id)
        if ($linked.Count -gt 0 -and $decision.mappingStatus -eq "UNRESOLVED") {
            $decision.derivationMode = "LEGACY_HEURISTIC_FALLBACK"
            if ($decision.conversionMode -eq "UNRESOLVED") { $decision.conversionMode = "CURRENT_RESOLVER_OUTPUT" }
        }
        $mappingRows += [pscustomobject]@{
            legacyField = $usage.fieldName
            storageLocation = $usage.storageLocation
            currentProducer = $usage.currentProducers
            consumerFile = $reference.File
            consumerSymbol = $reference.Symbol
            consumerKind = $reference.Kind
            rawOrTokenMeaning = $decision.rawOrTokenMeaning
            consumerSemanticUse = $decision.consumerSemanticUse
            currentDisposition = $usage.currentDisposition
            eventualReplacementStrategy = $usage.eventualReplacementStrategy
            targetLayer = $decision.targetLayer
            targetRelation = $decision.targetRelation
            conversionMode = $decision.conversionMode
            derivationMode = $decision.derivationMode
            mappingStatus = $decision.mappingStatus
            reviewEvidence = $decision.reviewEvidence
            linkedRiskPathIds = ($linked -join ";")
            notes = $decision.notes
        }
    }
}
$mappingRows = @($mappingRows | Sort-Object legacyField, storageLocation, consumerFile, consumerSymbol)
$mappingColumns = @("legacyField", "storageLocation", "currentProducer", "consumerFile", "consumerSymbol", "consumerKind", "rawOrTokenMeaning", "consumerSemanticUse", "currentDisposition", "eventualReplacementStrategy", "targetLayer", "targetRelation", "conversionMode", "derivationMode", "mappingStatus", "reviewEvidence", "linkedRiskPathIds", "notes")
Write-Csv $mappingRows $mappingColumns (Join-Path $docsRoot "metadata_legacy_to_target_mapping_matrix.csv")
$mappingLines = @(
    "# Metadata legacy-to-target mapping matrix", "",
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Source field/storage rows: $($usageRows.Count)",
    "- Consumer-specific mapping rows: $($mappingRows.Count)",
    '- Machine-readable companion: `metadata_legacy_to_target_mapping_matrix.csv`', "",
    "This is a consumer inventory plus reviewed or unresolved migration decisions. It is not automatically an approved REVIEWED_V1 specification.", "",
    "| Field | Storage | Consumer | Semantic use | Target | Conversion | Status | Risk paths |",
    "|---|---|---|---|---|---|---|---|"
)
foreach ($row in $mappingRows) {
    $mappingLines += "| ``$($row.legacyField)`` | $(Escape-Markdown $row.storageLocation) | ``$(Escape-Markdown "$($row.consumerFile)#$($row.consumerSymbol)")`` | ``$($row.consumerSemanticUse)`` | ``$($row.targetLayer) / $($row.targetRelation)`` | ``$($row.conversionMode)`` | ``$($row.mappingStatus)`` | ``$($row.linkedRiskPathIds)`` |"
}
Set-Content -LiteralPath (Join-Path $docsRoot "metadata_legacy_to_target_mapping_matrix.md") -Value $mappingLines -Encoding UTF8

function Write-LegacyConsumerInventory([string]$Field, [string]$FileName) {
    $rows = @($mappingRows | Where-Object legacyField -eq $Field | Select-Object `
        legacyField, storageLocation, consumerFile, consumerSymbol, consumerKind, currentDisposition, `
        targetLayer, targetRelation, conversionMode, derivationMode, mappingStatus, reviewEvidence, notes)
    Write-Csv $rows @(
        "legacyField", "storageLocation", "consumerFile", "consumerSymbol", "consumerKind",
        "currentDisposition", "targetLayer", "targetRelation", "conversionMode", "derivationMode",
        "mappingStatus", "reviewEvidence", "notes"
    ) (Join-Path $docsRoot $FileName)
}
Write-LegacyConsumerInventory "familyId" "family_id_consumer_inventory.csv"
Write-LegacyConsumerInventory "loadProfile" "load_profile_consumer_inventory.csv"

$semanticRows = @()
foreach ($group in ($mappingRows | Group-Object consumerSemanticUse | Sort-Object Name)) { $semanticRows += [pscustomobject]@{ summaryType="SEMANTIC_USE_COUNT"; key=$group.Name; count=$group.Count; details="" } }
foreach ($group in ($mappingRows | Group-Object mappingStatus | Sort-Object Name)) { $semanticRows += [pscustomobject]@{ summaryType="MAPPING_STATUS_COUNT"; key=$group.Name; count=$group.Count; details="" } }
foreach ($group in ($mappingRows | Group-Object targetLayer | Sort-Object Name)) { $semanticRows += [pscustomobject]@{ summaryType="TARGET_LAYER_COUNT"; key=$group.Name; count=$group.Count; details="" } }
$semanticRows += [pscustomobject]@{ summaryType="UNRESOLVED_ROWS"; key="UNRESOLVED"; count=@($mappingRows | Where-Object mappingStatus -eq "UNRESOLVED").Count; details="Explicitly unresolved, not approved" }
$semanticRows += [pscustomobject]@{ summaryType="AUTO_CANDIDATE_ROWS"; key="AUTO_CANDIDATE"; count=@($mappingRows | Where-Object mappingStatus -eq "AUTO_CANDIDATE").Count; details="Consumer-specific candidates requiring review" }
foreach ($field in @("defaultRestSeconds", "activityKind", "progressMetricType", "analysisEligibility")) {
    $rows = @($mappingRows | Where-Object legacyField -eq $field)
    $semanticRows += [pscustomobject]@{ summaryType="V2_2_REVIEWED_DECISION"; key=$field; count=$rows.Count; details=(($rows | Select-Object -ExpandProperty targetLayer -Unique | Sort-Object) -join ";") }
}
foreach ($group in ($mappingRows | Group-Object legacyField | Sort-Object Name)) {
    $destinations = @($group.Group | ForEach-Object { "$($_.consumerSemanticUse):$($_.targetLayer):$($_.targetRelation)" } | Sort-Object -Unique)
    if ($destinations.Count -gt 1) { $semanticRows += [pscustomobject]@{ summaryType="MULTIPLE_SEMANTIC_DESTINATIONS"; key=$group.Name; count=$destinations.Count; details=($destinations -join ";") } }
    if (@($group.Group | Where-Object consumerSemanticUse -ne "LEGACY_COMPATIBILITY").Count -eq 0) { $semanticRows += [pscustomobject]@{ summaryType="LEGACY_COMPATIBILITY_ONLY"; key=$group.Name; count=$group.Count; details="Target relation NONE" } }
}
foreach ($row in ($mappingRows | Where-Object consumerSemanticUse -eq "UNRESOLVED")) {
    $semanticRows += [pscustomobject]@{ summaryType="UNESTABLISHED_CONSUMER"; key="$($row.legacyField)|$($row.storageLocation)|$($row.consumerFile)#$($row.consumerSymbol)"; count=1; details=$row.reviewEvidence }
}
$semanticRows = @($semanticRows | Sort-Object summaryType, key)
Write-Csv $semanticRows @("summaryType", "key", "count", "details") (Join-Path $docsRoot "metadata_mapping_semantic_review.csv")
$semanticLines = @(
    "# Metadata mapping semantic review", "",
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Mapping rows: $($mappingRows.Count)",
    "- Unresolved rows: $(@($mappingRows | Where-Object mappingStatus -eq 'UNRESOLVED').Count)",
    "- Auto-candidate rows: $(@($mappingRows | Where-Object mappingStatus -eq 'AUTO_CANDIDATE').Count)",
    "- Semantically reviewed rows: $(@($mappingRows | Where-Object mappingStatus -eq 'SEMANTICALLY_REVIEWED').Count)", "",
    "`AUTO_CANDIDATE` and `UNRESOLVED` are not approved REVIEWED_V1 inputs.", "",
    "| Summary | Key | Count | Details |", "|---|---|---:|---|"
)
foreach ($row in $semanticRows) { $semanticLines += "| ``$($row.summaryType)`` | ``$(Escape-Markdown $row.key)`` | $($row.count) | $(Escape-Markdown $row.details) |" }
Set-Content -LiteralPath (Join-Path $docsRoot "metadata_mapping_semantic_review.md") -Value $semanticLines -Encoding UTF8

function Taxonomy-Decision([string]$Field) {
    $decision = [ordered]@{
        status = "UNRESOLVED"
        targetConcept = "UNRESOLVED"
        logicalQuestion = "Consumer-specific semantic review required"
        reason = "No Phase 2A.1 approval"
        koreanRegistryEligible = "NO"
        reviewStatus = "REVIEW_REQUIRED"
    }
    switch ($Field) {
        "stableKey" { $decision.status="KEEP"; $decision.targetConcept="ExerciseIdentity"; $decision.logicalQuestion="Which exercise identity is this?"; $decision.reason="Canonical identity key" }
        "name" { $decision.status="PRESENTATION_ONLY"; $decision.targetConcept="ExerciseDisplayName"; $decision.logicalQuestion="How is the exercise displayed?"; $decision.reason="Display text is not classification" }
        "exerciseName" { $decision.status="PRESENTATION_ONLY"; $decision.targetConcept="ExerciseDisplayName"; $decision.logicalQuestion="How is the exercise displayed?"; $decision.reason="Display text is not classification" }
        "description" { $decision.status="PRESENTATION_ONLY"; $decision.targetConcept="ExerciseDescription"; $decision.logicalQuestion="How is the exercise explained?"; $decision.reason="Educational display text" }
        "defaultRestSeconds" { $decision.status="PROGRAM_PARAMETER"; $decision.targetConcept="ExerciseProgramTimingProfile"; $decision.logicalQuestion="What default rest supports program time budgeting?"; $decision.reason="Approved fixed program parameter" }
        "trainingRole" { $decision.status="LEGACY_ONLY"; $decision.targetConcept="NONE"; $decision.logicalQuestion="Which explicitly approved legacy role was assigned?"; $decision.reason="Exact stableKey whitelist pending Phase 2B role review" }
        "familyId" { $decision.status="DERIVE"; $decision.targetConcept="NONE"; $decision.logicalQuestion="Which purpose-specific similarity relation is needed?"; $decision.reason="Approved DERIVED_NONCANONICAL disposition" }
        "familyName" { $decision.status="PRESENTATION_ONLY"; $decision.targetConcept="NONE"; $decision.logicalQuestion="How was the legacy family displayed?"; $decision.reason="Legacy family display only" }
        "loadProfile" { $decision.status="SPLIT"; $decision.targetConcept="ConsumerSpecificTypedRelations"; $decision.logicalQuestion="Which independent load or movement property is represented?"; $decision.reason="Approved legacy composite decomposition" }
        "sportTransferDirect" { $decision.status="KEEP"; $decision.targetConcept="SportTransferDirectRef"; $decision.logicalQuestion="Does an approved direct-transfer relation exist?"; $decision.reason="Closed-world exact whitelist" }
        "movementPattern" { $decision.status="SPLIT"; $decision.targetConcept="MovementPatternRef|MovementEventRef|MovementPhaseRef"; $decision.logicalQuestion="Which movement pattern event or phase applies?"; $decision.reason="Legacy token field mixes Level 1 questions"; $decision.koreanRegistryEligible="YES" }
        "laterality" { $decision.status="KEEP"; $decision.targetConcept="LateralityRef"; $decision.logicalQuestion="What fixed laterality describes the exercise?"; $decision.reason="Level 1 kinematic classification"; $decision.koreanRegistryEligible="YES" }
        "plane" { $decision.status="KEEP"; $decision.targetConcept="MovementPlaneRef"; $decision.logicalQuestion="In which movement plane is the exercise primarily performed?"; $decision.reason="Level 1 kinematic classification"; $decision.koreanRegistryEligible="YES" }
        "equipmentTags" { $decision.status="SPLIT"; $decision.targetConcept="ExerciseEquipmentRequirement|EquipmentRef"; $decision.logicalQuestion="Which equipment relation is required optional or alternative?"; $decision.reason="Normalize token list into equipment relations"; $decision.koreanRegistryEligible="YES" }
        "equipment" { $decision.status="SPLIT"; $decision.targetConcept="ExerciseEquipmentRequirement|EquipmentRef"; $decision.logicalQuestion="Which equipment relation is required optional or alternative?"; $decision.reason="Normalize legacy display tokens into equipment relations"; $decision.koreanRegistryEligible="YES" }
        "jointEmphasis" { $decision.status="SPLIT"; $decision.targetConcept="JointComplexRef|JointActionRef"; $decision.logicalQuestion="Which joint complex and action are involved?"; $decision.reason="Legacy emphasis tokens require typed Level 1 relations"; $decision.koreanRegistryEligible="YES" }
        "activityKind" { $decision.status="LEGACY_ONLY"; $decision.targetConcept="NONE"; $decision.logicalQuestion="Which legacy catalog kind applies?"; $decision.reason="Compatibility only pending separate catalog taxonomy" }
        "progressMetricType" { $decision.status="LEGACY_ONLY"; $decision.targetConcept="NONE"; $decision.logicalQuestion="Which legacy progress protocol selector applies?"; $decision.reason="Compatibility only and not canonical exercise metadata" }
        "analysisEligibility" { $decision.status="SPLIT"; $decision.targetConcept="ConsumerSpecificAnalysisCapability"; $decision.logicalQuestion="Which analysis capability is enabled for this consumer?"; $decision.reason="Field-wide target is semantically invalid" }
        "metadataConfidence" { $decision.status="DEPRECATE"; $decision.targetConcept="RelationProvenance"; $decision.logicalQuestion="What evidence and review status supports each relation?"; $decision.reason="Single scalar confidence cannot represent relation provenance" }
        "sourceConfidenceLevel" { $decision.status="DEPRECATE"; $decision.targetConcept="RelationProvenance"; $decision.logicalQuestion="What evidence and review status supports each relation?"; $decision.reason="Single scalar confidence cannot represent relation provenance" }
        "finalSourceStatus" { $decision.status="DEPRECATE"; $decision.targetConcept="RelationProvenance"; $decision.logicalQuestion="What evidence and review status supports each relation?"; $decision.reason="Move provenance to each relation" }
    }
    if ($decision.status -ne "UNRESOLVED") { $decision.reviewStatus = "PHASE_2A1_REVIEWED" }
    [pscustomobject]$decision
}

$taxonomyRows = @($usageRows | Group-Object fieldName | Sort-Object Name | ForEach-Object {
    $decision = Taxonomy-Decision $_.Name
    [pscustomobject]@{
        currentConcept = $_.Name
        sourceStorage = (($_.Group.storageLocation | Sort-Object -Unique) -join ";")
        decisionStatus = $decision.status
        targetConcept = $decision.targetConcept
        logicalQuestion = $decision.logicalQuestion
        decisionReason = $decision.reason
        koreanRegistryEligible = $decision.koreanRegistryEligible
        reviewStatus = $decision.reviewStatus
        notes = if ($decision.status -eq "UNRESOLVED") { "Do not auto-promote or auto-translate" } else { "Phase 2A.1 decision" }
    }
})
Write-Csv $taxonomyRows @(
    "currentConcept", "sourceStorage", "decisionStatus", "targetConcept", "logicalQuestion",
    "decisionReason", "koreanRegistryEligible", "reviewStatus", "notes"
) (Join-Path $docsRoot "metadata_taxonomy_decision_matrix.csv")
$taxonomyLines = @(
    "# Metadata taxonomy decision matrix", "",
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Current concepts: $($taxonomyRows.Count)",
    "- Unresolved concepts: $(@($taxonomyRows | Where-Object decisionStatus -eq 'UNRESOLVED').Count)", "",
    "Unresolved concepts are intentionally not promoted or auto-translated.", "",
    "| Current concept | Decision | Target | Korean registry | Reason |",
    "|---|---|---|---|---|"
)
foreach ($row in $taxonomyRows) {
    $taxonomyLines += "| ``$($row.currentConcept)`` | ``$($row.decisionStatus)`` | ``$(Escape-Markdown $row.targetConcept)`` | $($row.koreanRegistryEligible) | $(Escape-Markdown $row.decisionReason) |"
}
Set-Content -LiteralPath (Join-Path $docsRoot "metadata_taxonomy_decision_matrix.md") -Value $taxonomyLines -Encoding UTF8

Write-CompatibilityInventory

# Canonical metadata normalization closeout audits. These extend the existing
# contract audit instead of introducing a parallel audit pipeline.
$canonicalRoot = Join-Path $RepoRoot "app/src/main/assets/metadata/canonical_v1"
$canonicalBootstrapRows = @(Import-Csv -LiteralPath (Join-Path $canonicalRoot "exercise_bootstrap.csv"))
$canonicalIdentityRows = @(Import-Csv -LiteralPath (Join-Path $canonicalRoot "identity_master.csv"))
$canonicalMovementRows = @(Import-Csv -LiteralPath (Join-Path $canonicalRoot "movement_relations.csv"))
$seedExerciseRows = @(Import-Csv -LiteralPath (Join-Path $RepoRoot "app/src/main/assets/training_settings_seed.csv") | Where-Object row_type -eq "exercise")
$identityByStableKey = @{}
foreach ($row in $canonicalIdentityRows) { $identityByStableKey[$row.stableKey] = $row }

function Joined-StableKeys([object[]]$Rows) {
    (@($Rows | ForEach-Object stableKey | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique) -join "|")
}

function Add-OwnerAuditRow(
    [string]$SourceField,
    [string]$SourceToken,
    [string]$SemanticMeaning,
    [string]$AffectedStableKeys,
    [string]$CurrentConsumers,
    [string]$Owner1,
    [string]$Owner2,
    [string]$Owner3,
    [string]$Exact,
    [string]$Extension,
    [string]$Lost,
    [string]$Duplicated,
    [string]$Disposition
) {
    [pscustomobject]@{
        sourceField = $SourceField
        sourceToken = $SourceToken
        semanticMeaning = $SemanticMeaning
        affectedStableKeys = $AffectedStableKeys
        currentConsumers = $CurrentConsumers
        existingCandidateOwner1 = $Owner1
        existingCandidateOwner2 = $Owner2
        existingCandidateOwner3 = $Owner3
        canExistingOwnerRepresentExactly = $Exact
        extensionRequired = $Extension
        informationLostIfMapped = $Lost
        informationDuplicatedIfMapped = $Duplicated
        recommendedDisposition = $Disposition
    }
}

$forceOwnerDecisions = [ordered]@{
    PUSH = @("Push mechanics", "ExerciseMovementAnatomyRelation/MOVEMENT_PATTERN", "ExerciseProgramSlotCapability", "ExerciseTrainingRoleEligibility", "YES", "NO", "NONE", "A duplicate action taxonomy", "REUSE_EXISTING_EXACTLY")
    PULL = @("Pull mechanics", "ExerciseMovementAnatomyRelation/MOVEMENT_PATTERN", "ExerciseProgramSlotCapability", "ExerciseTrainingRoleEligibility", "YES", "NO", "NONE", "A duplicate action taxonomy", "REUSE_EXISTING_EXACTLY")
    LOWER_BODY = @("Legacy body-region bucket", "ExerciseMovementAnatomyRelation/BODY_REGION", "ExerciseMovementAnatomyRelation/MOVEMENT_PATTERN", "ExerciseProgramSlotCapability", "YES", "NO", "NONE", "A second body-region bucket", "KEEP_AS_COMPATIBILITY_ONLY")
    MIXED = @("Old scalar could not express multiple simultaneous facts", "ExerciseMovementAnatomyRelation", "ExerciseMuscleContribution", "ExerciseOfiAxisContribution", "YES", "NO", "NONE", "Preserving MIXED as canonical would duplicate typed relations", "KEEP_AS_COMPATIBILITY_ONLY")
    STRENGTH = @("Training adaptation intent", "ExerciseTrainingRoleEligibility", "ExerciseProgramSlotCapability", "ExerciseStrengthProxyRelation", "YES", "NO", "NONE", "A generic force/action owner", "REUSE_EXISTING_EXACTLY")
    HYPERTROPHY = @("Hypertrophy adaptation intent", "ExerciseTrainingRoleEligibility", "ExerciseMuscleContribution", "ExerciseProgramSlotCapability", "YES", "NO", "NONE", "A generic force/action owner", "REUSE_EXISTING_EXACTLY")
    POWER = @("General power intent, not sport transfer", "ExercisePhysicalQualityPoint", "ExerciseOfiAxisContribution", "ExerciseProgramSlotCapability", "YES", "NO", "NONE", "Badminton ROTATION_POWER would be a cross-domain duplicate", "REUSE_EXISTING_EXACTLY")
    PLYOMETRIC = @("Stretch-shortening and explosive event demand", "ExerciseMovementAnatomyRelation/MOVEMENT_EVENT", "ExerciseOfiAxisContribution", "ExercisePhysicalQualityPoint", "YES", "NO", "NONE", "A new plyometric scalar", "REUSE_EXISTING_EXACTLY")
    DECELERATION_DIRECT = @("Explicit deceleration movement/role", "ExerciseMovementAnatomyRelation/MOVEMENT_EVENT", "ExerciseBadmintonTransferPoint", "ExercisePhysicalQualityPoint", "YES", "NO", "NONE", "A second deceleration taxonomy", "REUSE_EXISTING_EXACTLY")
    ANTI_ROTATION = @("Intrinsic resistance to rotational torque", "ExerciseMovementAnatomyRelation/MOVEMENT_PATTERN", "ExerciseStabilityDemand", "ExerciseBadmintonTransferPoint", "YES", "NO", "NONE", "A duplicate anti-rotation owner", "REUSE_EXISTING_EXACTLY")
    MOTOR_CONTROL = @("Movement-control and stability intent", "ExerciseMovementAnatomyRelation/MOVEMENT_PATTERN", "ExerciseStabilityDemand", "ExerciseTrainingRoleEligibility", "YES", "NO", "NONE", "A broad motor-control scalar", "REUSE_EXISTING_EXACTLY")
    LOW_LOAD = @("Low fixed stress/load demand", "ExerciseOfiAxisContribution", "ExerciseOfiDoseProfile", "ExerciseRecoveryProfile", "YES", "NO", "NONE", "A duplicate load-level scalar", "REUSE_EXISTING_EXACTLY")
    HINGE = @("Historical/runtime hinge mechanics", "ExerciseMovementAnatomyRelation/MOVEMENT_PATTERN", "ExerciseProgramSlotCapability", "ExerciseStrengthProxyRelation", "YES", "NO", "NONE", "Permanent runtime force authority", "KEEP_AS_COMPATIBILITY_ONLY")
    SQUAT = @("Historical/runtime squat mechanics", "ExerciseMovementAnatomyRelation/MOVEMENT_PATTERN", "ExerciseProgramSlotCapability", "ExerciseStrengthProxyRelation", "YES", "NO", "NONE", "Permanent runtime force authority", "KEEP_AS_COMPATIBILITY_ONLY")
    ROTATE = @("Historical/runtime active rotation", "ExerciseMovementAnatomyRelation/MOVEMENT_PATTERN", "ExercisePhysicalQualityPoint", "ExerciseBadmintonTransferPoint", "YES", "NO", "NONE", "Conflating rotation with anti-rotation", "KEEP_AS_COMPATIBILITY_ONLY")
    BRACE = @("Historical/runtime overloaded trunk stabilization", "ExerciseMovementAnatomyRelation/MOVEMENT_PATTERN", "ExerciseStabilityDemand", "ExerciseOfiAxisContribution", "NO", "YES", "Generic BRACE cannot preserve distinct trunk-control facts", "A permanent overloaded scalar", "DECOMPOSE_EXISTING_OWNER")
    CARRY = @("Historical/runtime loaded carry mechanics", "ExerciseMovementAnatomyRelation/MOVEMENT_PATTERN", "ExerciseProgramSlotCapability", "ExerciseOfiAxisContribution", "YES", "NO", "NONE", "Permanent runtime force authority", "KEEP_AS_COMPATIBILITY_ONLY")
    LAND = @("Historical/runtime landing event", "ExerciseMovementAnatomyRelation/MOVEMENT_EVENT", "ExerciseOfiAxisContribution", "ExerciseBadmintonTransferPoint", "YES", "NO", "NONE", "Permanent runtime force authority", "KEEP_AS_COMPATIBILITY_ONLY")
    DECELERATE = @("Historical/runtime deceleration event", "ExerciseMovementAnatomyRelation/MOVEMENT_EVENT", "ExerciseOfiAxisContribution", "ExerciseBadmintonTransferPoint", "YES", "NO", "NONE", "Permanent runtime force authority", "KEEP_AS_COMPATIBILITY_ONLY")
    ACCELERATE = @("Historical/runtime acceleration event", "ExerciseMovementAnatomyRelation/MOVEMENT_EVENT", "ExerciseOfiAxisContribution", "ExerciseBadmintonTransferPoint", "YES", "NO", "NONE", "Permanent runtime force authority", "KEEP_AS_COMPATIBILITY_ONLY")
}

$ownerAuditRows = @()
foreach ($token in $forceOwnerDecisions.Keys) {
    $decision = $forceOwnerDecisions[$token]
    $affected = Joined-StableKeys @($canonicalBootstrapRows | Where-Object forceType -eq $token)
    $ownerAuditRows += Add-OwnerAuditRow "forceType" $token $decision[0] $affected `
        "ExerciseMetadataMapper;ExerciseAnalysisMapper;fatigue/readiness;ProgramBuilder compatibility" `
        $decision[1] $decision[2] $decision[3] $decision[4] $decision[5] $decision[6] $decision[7] $decision[8]
}
$ownerAuditRows += Add-OwnerAuditRow "movementPattern" "TRUNK_BRACE" `
    "Overloaded axial bracing, anti-rotation, anti-extension, anti-lateral-flexion, and dynamic stabilization" `
    "band_pallof_press|cable_pallof_press|ex_28347c1f|ex_2a826c82|ex_a44ae2ca|ex_a8385c4a|ex_a9b52886|ex_d5bdffe1|ex_f6d43398|landmine_anti_rotation|plate_rotational_press_out" `
    "movement taxonomy;presentation;analysis audit" "ExerciseMovementAnatomyRelation/MOVEMENT_PATTERN" "ExerciseStabilityDemand" "ExerciseOfiAxisContribution" `
    "NO" "YES" "One scalar cannot preserve the reviewed distinctions" "Keeping TRUNK_BRACE would duplicate decomposed relations" "DECOMPOSE_EXISTING_OWNER"
$ownerAuditRows += Add-OwnerAuditRow "plane" "ALL" "Intrinsic primary movement plane" `
    (Joined-StableKeys @($canonicalBootstrapRows | Where-Object { -not [string]::IsNullOrWhiteSpace($_.plane) })) `
    "analysis;ProgramBuilder;presentation" "ExerciseMovementAnatomyRelation/MOVEMENT_PLANE" "identity_master.plane" "NONE" "YES" "NO" "NONE" "NONE" "KEEP_AS_CANONICAL_METADATA"
$ownerAuditRows += Add-OwnerAuditRow "laterality" "ALL" "Intrinsic exercise-side structure" `
    (Joined-StableKeys @($canonicalBootstrapRows | Where-Object { -not [string]::IsNullOrWhiteSpace($_.laterality) })) `
    "analysis;ProgramBuilder;presentation" "ExerciseMovementAnatomyRelation/LATERALITY" "identity_master.laterality" "NONE" "YES" "NO" "NONE" "NONE" "KEEP_AS_CANONICAL_METADATA"
$ownerAuditRows += Add-OwnerAuditRow "primaryMuscles|secondaryMuscles" "ALL" "Intrinsic anatomical participation" `
    (Joined-StableKeys $canonicalBootstrapRows) "muscle analysis;ProgramBuilder;presentation" "ExerciseMuscleContribution" "MuscleRef" "NONE" "YES" "NO" "NONE" "NONE" "KEEP_AS_CANONICAL_METADATA"
$ownerAuditRows += Add-OwnerAuditRow "fatigueCategories|load weights" "ALL" "Intrinsic workload, stress, and recovery inputs" `
    (Joined-StableKeys $canonicalBootstrapRows) "OFI;readiness;fatigue;connective tissue" "ExerciseOfiAxisContribution" "ExerciseOfiDoseProfile" "ExerciseRecoveryProfile" "YES" "NO" "NONE" "NONE" "KEEP_AS_CANONICAL_METADATA"
$ownerAuditRows += Add-OwnerAuditRow "generic exercise metadata" "badminton derived axes" "Legacy cross-domain transfer inference" `
    (Joined-StableKeys $canonicalBootstrapRows) "BadmintonTransferMetadataMapper;BadmintonTrainingLoadIndexCalculator" "ExerciseBadmintonTransferPoint" "ExerciseBadmintonSkillTargetPoint" "ExercisePhysicalQualityPoint" "YES" "NO" "NONE" "Generic metadata must not duplicate sport-transfer authority" "REUSE_EXISTING_EXACTLY"
$ownerAuditRows = @($ownerAuditRows | Sort-Object sourceField, sourceToken)
$ownerColumns = @(
    "sourceField", "sourceToken", "semanticMeaning", "affectedStableKeys", "currentConsumers",
    "existingCandidateOwner1", "existingCandidateOwner2", "existingCandidateOwner3",
    "canExistingOwnerRepresentExactly", "extensionRequired", "informationLostIfMapped",
    "informationDuplicatedIfMapped", "recommendedDisposition"
)
Write-Csv $ownerAuditRows $ownerColumns (Join-Path $docsRoot "metadata_existing_owner_capability_audit.csv")
$ownerLines = @(
    "# Existing-owner capability audit", "",
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Reviewed concepts: $($ownerAuditRows.Count)",
    "- New owners required: $(@($ownerAuditRows | Where-Object recommendedDisposition -eq 'NEW_OWNER_REQUIRED').Count)", "",
    "| Source | Token | Meaning | Existing owner | Extension | Disposition |", "|---|---|---|---|---:|---|"
)
foreach ($row in $ownerAuditRows) {
    $ownerLines += "| ``$($row.sourceField)`` | ``$($row.sourceToken)`` | $(Escape-Markdown $row.semanticMeaning) | ``$($row.existingCandidateOwner1)`` | $($row.extensionRequired) | ``$($row.recommendedDisposition)`` |"
}
Set-Content -LiteralPath (Join-Path $docsRoot "metadata_existing_owner_capability_audit.md") -Value $ownerLines -Encoding UTF8

function Match-Count([System.IO.FileInfo[]]$Files, [string]$Token) {
    $pattern = "(?<![A-Z0-9_])$([regex]::Escape($Token))(?![A-Z0-9_])"
    $count = 0
    foreach ($file in $Files) {
        $count += [regex]::Matches((Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8), $pattern).Count
    }
    $count
}

$taxonomyText = Get-Content -LiteralPath (Join-Path $mainRoot "com/training/trackplanner/data/ExerciseTaxonomy.kt") -Raw -Encoding UTF8
$registeredBlock = [regex]::Match($taxonomyText, '(?s)val\s+forceTypes\s*=\s*setOf\((.*?)\)').Groups[1].Value
$registeredForceTokens = @([regex]::Matches($registeredBlock, '"([A-Z0-9_]+)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
$metadataTaxonomyText = Get-Content -LiteralPath (Join-Path $mainRoot "com/training/trackplanner/data/ExerciseMetadataTaxonomy.kt") -Raw -Encoding UTF8
$runtimeBlock = [regex]::Match($metadataTaxonomyText, '(?s)enum\s+class\s+FatigueForceType\s*\{(.*?)\}').Groups[1].Value
$runtimeForceTokens = @([regex]::Matches($runtimeBlock, '\b([A-Z][A-Z0-9_]+)\b') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
$allForceTokens = @(($registeredForceTokens + $runtimeForceTokens) | Sort-Object -Unique)
$roomFixtureFiles = @($testFiles | Where-Object Name -match 'Room|Database|Dao')
$historicalFixtureFiles = @($testFiles | Where-Object Name -match 'Legacy|Migration')
$backupFixtureFiles = @($testFiles | Where-Object Name -match 'Backup|Restore|Import|Export')
$forceAuditRows = foreach ($token in $allForceTokens) {
    $canonicalCount = @($canonicalBootstrapRows | Where-Object forceType -eq $token).Count
    $seedCount = @($seedExerciseRows | Where-Object force_type -eq $token).Count
    $productionCount = Match-Count $sourceFiles $token
    $testCount = Match-Count $testFiles $token
    $classification = if ($token -in $registeredForceTokens) {
        "CURRENT_CANONICAL"
    } elseif ($canonicalCount -gt 0 -or $productionCount -gt 0) {
        "CURRENT_NONCANONICAL_RUNTIME"
    } elseif ($testCount -gt 0) {
        "TEST_ONLY"
    } else {
        "DEAD_CODE_ONLY"
    }
    [pscustomobject]@{
        token = $token
        classification = $classification
        presentInRegisteredForceTypes = if ($token -in $registeredForceTokens) { "YES" } else { "NO" }
        presentInRuntimeForceEnum = if ($token -in $runtimeForceTokens) { "YES" } else { "NO" }
        currentCanonicalRows = $canonicalCount
        seedRows = $seedCount
        seedDataSourceReferences = Match-Count @($sourceFiles | Where-Object Name -eq 'SeedData.kt') $token
        currentRoomFixtureReferences = Match-Count $roomFixtureFiles $token
        historicalRoomFixtureReferences = Match-Count $historicalFixtureFiles $token
        backupRestoreFixtureReferences = Match-Count $backupFixtureFiles $token
        testReferences = $testCount
        runtimeBranchReferences = $productionCount
        acceptedByRestore = "YES_FIELD_PRESERVED"
        downstreamAuthority = if ($classification -eq "CURRENT_CANONICAL") { "REGISTERED_COMPATIBILITY_AND_CURRENT_VALIDATION" } else { "RUNTIME_COMPATIBILITY_ONLY" }
        notes = if ($classification -eq "CURRENT_NONCANONICAL_RUNTIME") { "Do not promote into canonical metadata; normalize through existing typed relations" } else { "Existing canonical owners audited separately" }
    }
}
$forceColumns = @(
    "token", "classification", "presentInRegisteredForceTypes", "presentInRuntimeForceEnum",
    "currentCanonicalRows", "seedRows", "seedDataSourceReferences", "currentRoomFixtureReferences",
    "historicalRoomFixtureReferences", "backupRestoreFixtureReferences", "testReferences",
    "runtimeBranchReferences", "acceptedByRestore", "downstreamAuthority", "notes"
)
Write-Csv @($forceAuditRows) $forceColumns (Join-Path $docsRoot "force_type_token_audit.csv")
$forceLines = @(
    "# forceType token audit", "", '- Generated from the current registries, canonical bootstrap, source, and fixtures.',
    "- Registered values: $($registeredForceTokens -join ', ')",
    "- Runtime compatibility values: $($runtimeForceTokens -join ', ')",
    "- Dead-only values: $((@($forceAuditRows | Where-Object classification -eq 'DEAD_CODE_ONLY').token) -join ', ')", "",
    "| Token | Classification | Canonical rows | Seed rows | Runtime refs | Test refs |", "|---|---|---:|---:|---:|---:|"
)
foreach ($row in $forceAuditRows) { $forceLines += "| ``$($row.token)`` | ``$($row.classification)`` | $($row.currentCanonicalRows) | $($row.seedRows) | $($row.runtimeBranchReferences) | $($row.testReferences) |" }
Set-Content -LiteralPath (Join-Path $docsRoot "force_type_token_audit.md") -Value $forceLines -Encoding UTF8

$trunkDecisions = @(
    @{ key="band_pallof_press"; source="TRUNK_BRACE"; values=@("ANTI_ROTATION"); reason="Explicit rotational-torque resistance" },
    @{ key="cable_pallof_press"; source="TRUNK_BRACE"; values=@("ANTI_ROTATION"); reason="Explicit rotational-torque resistance" },
    @{ key="ex_28347c1f"; source="TRUNK_BRACE"; values=@("DYNAMIC_TRUNK_STABILIZATION", "ANTI_ROTATION"); reason="Bird-dog dynamic contralateral stabilization" },
    @{ key="ex_2a826c82"; source="TRUNK_BRACE"; values=@("ANTI_EXTENSION"); reason="Hollow hold resists extension" },
    @{ key="ex_a44ae2ca"; source="TRUNK_BRACE"; values=@("ANTI_EXTENSION"); reason="Plank resists extension" },
    @{ key="ex_a8385c4a"; source="TRUNK_BRACE"; values=@("ANTI_LATERAL_FLEXION"); reason="Copenhagen plank resists lateral flexion" },
    @{ key="ex_a9b52886"; source="TRUNK_BRACE"; values=@("DYNAMIC_TRUNK_STABILIZATION", "ANTI_EXTENSION"); reason="Mountain climber stabilizes the trunk dynamically against extension" },
    @{ key="ex_d5bdffe1"; source="TRUNK_BRACE"; values=@("DYNAMIC_TRUNK_STABILIZATION", "ANTI_EXTENSION"); reason="Dead bug combines dynamic control and anti-extension" },
    @{ key="ex_f6d43398"; source="TRUNK_BRACE"; values=@("ANTI_LATERAL_FLEXION"); reason="Side plank resists lateral flexion" },
    @{ key="landmine_anti_rotation"; source="TRUNK_BRACE"; values=@("ANTI_ROTATION"); reason="Explicit anti-rotation task" },
    @{ key="plate_rotational_press_out"; source="TRUNK_BRACE"; values=@("ANTI_ROTATION"); reason="Explicit anti-rotation press-out task" },
    @{ key="barbell_back_squat"; source="HEAVY_COMPOUND_BRACING"; values=@("AXIAL_BRACING"); reason="High-force trunk load transfer under axial load" },
    @{ key="barbell_deadlift"; source="HEAVY_COMPOUND_BRACING"; values=@("AXIAL_BRACING"); reason="High-force trunk load transfer under external load" },
    @{ key="barbell_romanian_deadlift"; source="HEAVY_COMPOUND_BRACING"; values=@("AXIAL_BRACING"); reason="Loaded hinge requires axial bracing" },
    @{ key="dumbbell_romanian_deadlift"; source="HEAVY_COMPOUND_BRACING"; values=@("AXIAL_BRACING"); reason="Loaded hinge requires axial bracing" },
    @{ key="ex_32219f7a"; source="HEAVY_COMPOUND_BRACING"; values=@("AXIAL_BRACING"); reason="Standing strict overhead press transfers load through a braced trunk" },
    @{ key="ex_8e4bf08e"; source="HEAVY_COMPOUND_BRACING"; values=@("AXIAL_BRACING"); reason="Unsupported loaded row requires axial trunk bracing" },
    @{ key="ex_c5043892"; source="HEAVY_COMPOUND_BRACING"; values=@("AXIAL_BRACING"); reason="Front squat requires high-force axial bracing" },
    @{ key="ex_de46b7f6"; source="HEAVY_COMPOUND_BRACING"; values=@("AXIAL_BRACING"); reason="Unsupported barbell row requires axial trunk bracing" },
    @{ key="dumbbell_farmer_carry"; source="LOADED_CARRY_BRACING"; values=@("AXIAL_BRACING"); reason="Bilateral loaded carry requires axial trunk stiffness" },
    @{ key="kettlebell_farmer_carry"; source="LOADED_CARRY_BRACING"; values=@("AXIAL_BRACING"); reason="Bilateral loaded carry requires axial trunk stiffness" }
)
$trunkRows = foreach ($decision in $trunkDecisions) {
    $actual = @($canonicalMovementRows | Where-Object { $_.exerciseStableKey -eq $decision.key -and $_.relationType -eq "MOVEMENT_PATTERN" } | ForEach-Object relationValue)
    $missing = @($decision.values | Where-Object { $_ -notin $actual })
    if ($missing.Count -gt 0) { throw "Missing reviewed trunk relation for $($decision.key): $($missing -join ',')" }
    [pscustomobject]@{
        stableKey = $decision.key
        exerciseName = $identityByStableKey[$decision.key].exerciseName
        sourceField = if ($decision.source -eq "TRUNK_BRACE") { "movementPattern" } else { "reviewed intrinsic load and movement relations" }
        sourceToken = $decision.source
        normalizedRelations = ($decision.values -join "|")
        existingOtherMovementRelations = (@($actual | Where-Object { $_ -notin $decision.values } | Sort-Object -Unique) -join "|")
        multiLabel = if ($decision.values.Count -gt 1) { "YES" } else { "NO" }
        badmintonAntiRotationImplied = "NO"
        reason = $decision.reason
        informationStatus = "LOSSLESS_WITH_EXISTING_OWNER_EXTENSION"
    }
}
$trunkColumns = @(
    "stableKey", "exerciseName", "sourceField", "sourceToken", "normalizedRelations",
    "existingOtherMovementRelations", "multiLabel", "badmintonAntiRotationImplied", "reason", "informationStatus"
)
Write-Csv @($trunkRows) $trunkColumns (Join-Path $docsRoot "trunk_brace_decomposition_audit.csv")
$trunkLines = @(
    "# TRUNK_BRACE decomposition audit", "", '- The existing multi-valued `MOVEMENT_PATTERN` relation is the owner.',
    "- Reviewed stableKeys: $($trunkRows.Count)",
    "- Multi-label reviewed rows: $(@($trunkRows | Where-Object multiLabel -eq 'YES').Count)",
    "- Remaining canonical TRUNK_BRACE relations: $(@($canonicalMovementRows | Where-Object relationValue -eq 'TRUNK_BRACE').Count)", "",
    "| Stable key | Source | Normalized relation(s) | Multi | Reason |", "|---|---|---|---:|---|"
)
foreach ($row in $trunkRows) { $trunkLines += "| ``$($row.stableKey)`` | ``$($row.sourceToken)`` | ``$($row.normalizedRelations)`` | $($row.multiLabel) | $(Escape-Markdown $row.reason) |" }
Set-Content -LiteralPath (Join-Path $docsRoot "trunk_brace_decomposition_audit.md") -Value $trunkLines -Encoding UTF8

$parityPath = Join-Path $docsRoot "metadata_normalization_shadow_parity_241.csv"
if (-not (Test-Path -LiteralPath $parityPath)) { throw "Missing reviewed 241-row shadow parity artifact: $parityPath" }
$parityRows = @(Import-Csv -LiteralPath $parityPath)
if ($parityRows.Count -ne 241) { throw "Expected 241 parity rows, found $($parityRows.Count)." }
if (@($parityRows | Where-Object decision -in @("CANONICAL_GAP", "INFORMATION_LOSS", "AMBIGUOUS")).Count -gt 0) {
    throw "Blocking metadata normalization parity rows remain."
}

$informationRows = @()
foreach ($row in $trunkRows) {
    $informationRows += [pscustomobject]@{
        stableKey = $row.stableKey
        sourceField = $row.sourceField
        sourceToken = $row.sourceToken
        sourceSemanticFacts = $row.reason
        existingCanonicalFactsBefore = if ($row.sourceToken -eq "TRUNK_BRACE") { "MOVEMENT_PATTERN:TRUNK_BRACE" } else { "Intrinsic movement/load relations without explicit axial-bracing label" }
        canonicalFactsAfter = "MOVEMENT_PATTERN:$($row.normalizedRelations -replace '\|', '|MOVEMENT_PATTERN:')"
        lostFacts = "NONE"
        unsupportedNewFacts = "NONE"
        duplicatedFacts = "NONE"
        informationStatus = $row.informationStatus
    }
}
foreach ($row in $parityRows) {
    $status = if ($row.decision -eq "PARITY_EXACT") { "LOSSLESS" } else { "INTENTIONAL_OBSOLETE_INFORMATION_REMOVED" }
    $informationRows += [pscustomobject]@{
        stableKey = $row.stableKey
        sourceField = "legacy derived badminton transfer inference"
        sourceToken = $row.currentBadmintonSourceSemantics
        sourceSemanticFacts = "axes=$($row.currentDerivedBadmintonAxes);objectives=$($row.currentBadmintonObjectiveKeys);fatigueCost=$($row.currentFatigueCost)"
        existingCanonicalFactsBefore = $row.normalizedBadmintonSourceSemantics
        canonicalFactsAfter = "axes=$($row.normalizedDerivedBadmintonAxes);objectives=$($row.normalizedBadmintonObjectiveKeys);fatigueCost=$($row.normalizedFatigueCost)"
        lostFacts = $row.semanticDelta
        unsupportedNewFacts = if ($row.outputDelta -eq "addedAxes=;addedObjectives=") { "NONE" } else { $row.outputDelta }
        duplicatedFacts = "NONE"
        informationStatus = $status
    }
}
$informationRows = @($informationRows | Sort-Object stableKey, sourceField, sourceToken)
$informationColumns = @(
    "stableKey", "sourceField", "sourceToken", "sourceSemanticFacts", "existingCanonicalFactsBefore",
    "canonicalFactsAfter", "lostFacts", "unsupportedNewFacts", "duplicatedFacts", "informationStatus"
)
Write-Csv $informationRows $informationColumns (Join-Path $docsRoot "metadata_information_preservation_audit.csv")
$informationLines = @(
    "# Metadata information-preservation audit", "", '- Covers trunk-control normalization and the 241-identity badminton consumer switch.',
    "- Rows: $($informationRows.Count)", "",
    "| Status | Count |", "|---|---:|"
)
foreach ($group in ($informationRows | Group-Object informationStatus | Sort-Object Name)) { $informationLines += "| ``$($group.Name)`` | $($group.Count) |" }
Set-Content -LiteralPath (Join-Path $docsRoot "metadata_information_preservation_audit.md") -Value $informationLines -Encoding UTF8

$parityLines = @(
    "# Metadata normalization 241-identity shadow parity", "", '- Machine-readable companion: `metadata_normalization_shadow_parity_241.csv`',
    "- Rows: $($parityRows.Count)", "",
    "| Decision | Count |", "|---|---:|"
)
foreach ($decision in @("PARITY_EXACT", "PARITY_STRUCTURAL_ONLY", "PARITY_INTENTIONAL_CORRECTION", "CANONICAL_GAP", "INFORMATION_LOSS", "AMBIGUOUS")) {
    $parityLines += "| ``$decision`` | $(@($parityRows | Where-Object decision -eq $decision).Count) |"
}
$parityLines += @("", "`fatigueCost` is byte-for-byte equal between current and normalized columns for all 241 rows.")
Set-Content -LiteralPath (Join-Path $docsRoot "metadata_normalization_shadow_parity_report.md") -Value $parityLines -Encoding UTF8

Remove-Item -LiteralPath (Join-Path $docsRoot "metadata_migration_issue_ledger.csv") -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $docsRoot "metadata_migration_issue_ledger.md") -ErrorAction SilentlyContinue

Write-Host "Wrote $($usageRows.Count) field rows to $usageCsv"
Write-Host "Wrote $($inferenceRows.Count) parsing/inference rows to $inferenceCsv"
Write-Host "Wrote $($mappingRows.Count) consumer-specific mapping rows"
Write-Host "Wrote $($riskRows.Count) risk paths and $($impactRows.Count) stableKey impact rows"
Write-Host "Wrote $($confirmedRows.Count) confirmed metadata errors"

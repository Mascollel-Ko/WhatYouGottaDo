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

function Current-Disposition([string]$Field) {
    if ($Field -in @("activityKind", "progressMetricType")) { return "LEGACY_COMPATIBILITY_READONLY" }
    if ($identityFields -contains $Field) { return "KEEP_TYPED_AUTHORITY" }
    if ($provenanceFields -contains $Field) { return "PROVENANCE_ONLY" }
    if ($displayFields -contains $Field) { return "DISPLAY_ONLY" }
    "KEEP_CURRENT_BEHAVIOR"
}

function Eventual-Replacement([string]$Field) {
    if ($Field -eq "progressMetricType") { return "REPLACE_OUTSIDE_CANONICAL_METADATA_AFTER_PARITY" }
    if ($Field -eq "activityKind") { return "REVIEW_SEPARATE_CATALOG_TAXONOMY" }
    if ($Field -in @("mode", "detail1", "detail2", "loadProfile", "familyE1rmMultiplier")) {
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
    [pscustomobject]@{ Id="META-SEED-FAMILY"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="familyIdFor"; Field="familyId"; Module="movement/anatomy"; Severity="HIGH"; Mode="SEED_FALLBACK"; Raw="family_id" },
    [pscustomobject]@{ Id="META-SEED-PRIMARY-MUSCLES"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="musclesFor"; Field="primaryMuscles"; Module="muscle/strength analysis"; Severity="HIGH"; Mode="SEED_FALLBACK"; Raw="primary_muscles" },
    [pscustomobject]@{ Id="META-SEED-SECONDARY-MUSCLES"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="fallbackSecondaryMuscles"; Field="secondaryMuscles"; Module="muscle/strength analysis"; Severity="HIGH"; Mode="SEED_FALLBACK"; Raw="secondary_muscles" },
    [pscustomobject]@{ Id="META-SEED-FORCE-TYPE"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="forceTypeFor"; Field="forceType"; Module="movement/anatomy"; Severity="MEDIUM"; Mode="SEED_FALLBACK"; Raw="force_type" },
    [pscustomobject]@{ Id="META-SEED-TRAINING-ROLE"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="trainingRoleFor"; Field="trainingRole"; Module="program generation"; Severity="HIGH"; Mode="SEED_FALLBACK"; Raw="training_role" },
    [pscustomobject]@{ Id="META-SEED-SPORT-TRANSFER"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="sportTransferDirectFor"; Field="sportTransferDirect"; Module="badminton analysis"; Severity="HIGH"; Mode="SEED_FALLBACK"; Raw="sport_transfer_direct" },
    [pscustomobject]@{ Id="META-SEED-LOAD-PROFILE"; File="app/src/main/java/com/training/trackplanner/data/SeedData.kt"; Symbol="loadProfileFor"; Field="loadProfile"; Module="shared metadata/data"; Severity="MEDIUM"; Mode="SEED_FALLBACK"; Raw="load_profile" },
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
    if ($Field -eq "defaultRestSeconds") {
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
        if ($linked.Count -gt 0) {
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
    '- Generated by: `tools/generate_metadata_analysis_contract_audit.ps1`',
    "- Inventory rows: $($compatibilityRows.Count)", "",
    "A compatibility field cannot be removed until production consumers are zero, parity passes, backup/restore compatibility and rollback are verified, and removal is explicitly approved.", "",
    "| Field | File | Symbol | Type | Access | Scope | Replacement owner |", "|---|---|---|---|---|---|---|"
)
foreach ($row in $compatibilityRows) { $compatibilityLines += "| ``$($row.legacyField)`` | ``$($row.filePath)`` | ``$($row.symbolOrFunction)`` | $($row.consumerType) | $($row.readOrWrite) | $($row.runtimeOrTest) | ``$($row.replacementOwner)`` |" }
Set-Content -LiteralPath (Join-Path $docsRoot "metadata_legacy_compatibility_consumers.md") -Value $compatibilityLines -Encoding UTF8

Remove-Item -LiteralPath (Join-Path $docsRoot "metadata_migration_issue_ledger.csv") -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $docsRoot "metadata_migration_issue_ledger.md") -ErrorAction SilentlyContinue

Write-Host "Wrote $($usageRows.Count) field rows to $usageCsv"
Write-Host "Wrote $($inferenceRows.Count) parsing/inference rows to $inferenceCsv"
Write-Host "Wrote $($mappingRows.Count) consumer-specific mapping rows"
Write-Host "Wrote $($riskRows.Count) risk paths and $($impactRows.Count) stableKey impact rows"
Write-Host "Wrote $($confirmedRows.Count) confirmed metadata errors"

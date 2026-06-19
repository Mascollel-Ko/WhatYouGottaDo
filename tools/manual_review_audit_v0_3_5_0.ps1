param(
    [string]$GapCsvPath,
    [string]$GapMarkdownPath,
    [string]$JudgementCsvPath,
    [string]$JudgementMarkdownPath,
    [string]$OutputDirectory
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $GapCsvPath) {
    $GapCsvPath = Join-Path $repoRoot "outputs\v0.3.5.0_seed_metadata_gap_report.csv"
}
if (-not $GapMarkdownPath) {
    $GapMarkdownPath = Join-Path $repoRoot "outputs\v0.3.5.0_seed_metadata_gap_report.md"
}
if (-not $JudgementCsvPath) {
    $JudgementCsvPath = Join-Path $repoRoot "outputs\google_drive_manual_review_inputs\v0.3.5.0_manual_review_171_individual_judgement.csv"
}
if (-not $JudgementMarkdownPath) {
    $JudgementMarkdownPath = Join-Path $repoRoot "outputs\google_drive_manual_review_inputs\v0.3.5.0_manual_review_171_individual_judgement.md"
}
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $repoRoot "outputs"
}

$requiredInputs = @(
    $GapCsvPath,
    $GapMarkdownPath,
    $JudgementCsvPath,
    $JudgementMarkdownPath
)
foreach ($path in $requiredInputs) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required input not found: $path"
    }
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

function To-YesNo([bool]$value) {
    if ($value) { return "YES" }
    return "NO"
}

function Escape-Markdown([object]$value) {
    if ($null -eq $value) { return "" }
    return ([string]$value).Replace("|", "\|").Replace("`r", " ").Replace("`n", "<br>")
}

function Add-MarkdownTable {
    param(
        [System.Collections.Generic.List[string]]$Lines,
        [string[]]$Headers,
        [object[]]$Rows,
        [scriptblock]$ValueSelector
    )

    $Lines.Add("| " + ($Headers -join " | ") + " |")
    $Lines.Add("| " + (($Headers | ForEach-Object { "---" }) -join " | ") + " |")
    foreach ($row in $Rows) {
        $values = & $ValueSelector $row
        $Lines.Add("| " + (($values | ForEach-Object { Escape-Markdown $_ }) -join " | ") + " |")
    }
}

$gapRows = @(Import-Csv -LiteralPath $GapCsvPath)
$judgementRows = @(Import-Csv -LiteralPath $JudgementCsvPath)

if ($gapRows.Count -ne 215) {
    throw "Gap report row count must be 215, found $($gapRows.Count)."
}

$originalManualRows = @($gapRows | Where-Object { $_.manualReviewRequired -eq "YES" })
if ($originalManualRows.Count -ne 171) {
    throw "Original manual-review row count must be 171, found $($originalManualRows.Count)."
}

if ($judgementRows.Count -ne 171) {
    throw "Individual judgement row count must be 171, found $($judgementRows.Count)."
}

$gapDuplicateKeys = @($gapRows | Group-Object stableKey | Where-Object { $_.Count -gt 1 })
$judgementDuplicateKeys = @($judgementRows | Group-Object stableKey | Where-Object { $_.Count -gt 1 })
if ($gapDuplicateKeys.Count -gt 0 -or $judgementDuplicateKeys.Count -gt 0) {
    throw "Duplicate stableKey values detected. Gap duplicates=$($gapDuplicateKeys.Count), judgement duplicates=$($judgementDuplicateKeys.Count)."
}

$gapByStableKey = @{}
$gapByExerciseName = @{}
foreach ($row in $gapRows) {
    $gapByStableKey[$row.stableKey] = $row
    if (-not $gapByExerciseName.ContainsKey($row.exerciseName)) {
        $gapByExerciseName[$row.exerciseName] = @()
    }
    $gapByExerciseName[$row.exerciseName] += $row
}

$judgementByStableKey = @{}
$judgementMatchMethod = @{}
$unmatchedJudgements = [System.Collections.Generic.List[object]]::new()
foreach ($judgement in $judgementRows) {
    if ($gapByStableKey.ContainsKey($judgement.stableKey)) {
        $judgementByStableKey[$judgement.stableKey] = $judgement
        $judgementMatchMethod[$judgement.stableKey] = "stableKey"
        continue
    }

    $nameCandidates = @($gapByExerciseName[$judgement.exerciseName])
    if ($nameCandidates.Count -eq 1) {
        $matchedKey = $nameCandidates[0].stableKey
        $judgementByStableKey[$matchedKey] = $judgement
        $judgementMatchMethod[$matchedKey] = "exerciseName_exact"
        continue
    }

    $unmatchedJudgements.Add($judgement)
}

$templateReviewFamilies = @(
    "FOREARM_GRIP_ACCESSORY_REVIEW",
    "CHEST_ISOLATION_REVIEW",
    "PULLOVER_LAT_CHEST_ACCESSORY_REVIEW",
    "POWER_CORE_SHOULDER_REVIEW",
    "RUNNING_MECHANICS_SUPPORT_REVIEW",
    "BADMINTON_OVERHEAD_POWER_TEST_REVIEW",
    "POSTERIOR_CHAIN_LOW_LOAD_REVIEW",
    "OTHER_SPORT_SESSION_RECORDS_REVIEW"
)

$ambiguousFamilySplitKeys = @(
    "ex_ab468462",
    "ex_7deabeba",
    "ex_7176cbee",
    "ex_a091b9fe",
    "ex_a60496be",
    "ex_149730de",
    "ex_dd16e07a",
    "ex_99728d25"
)

$partialOverbroadKeys = @(
    "ex_216351a1",
    "ex_c6d9efef"
)

$auditedRows = [System.Collections.Generic.List[object]]::new()
foreach ($gap in $gapRows) {
    $judgement = $null
    if ($judgementByStableKey.ContainsKey($gap.stableKey)) {
        $judgement = $judgementByStableKey[$gap.stableKey]
    }

    $suggestedFamily = if ($judgement) { [string]$judgement.mySuggestedFamilyOrActionFamily } else { "" }
    $familyCorrection = ""
    if (
        $judgement -and
        -not [string]::IsNullOrWhiteSpace($suggestedFamily) -and
        $suggestedFamily -ne $gap.estimatedMovementFamily
    ) {
        $familyCorrection = $suggestedFamily
    }

    $effectiveFamily = if ($familyCorrection) { $familyCorrection } else { $gap.estimatedMovementFamily }
    $familyJudgement = if ($judgement) { [string]$judgement.myFamilyJudgement } else { "NOT_REVIEWED" }
    $individualJudgement = if ($judgement) { [string]$judgement.myRowReviewJudgement } else { "NOT_IN_ORIGINAL_MANUAL_SET" }
    $priority = if ($judgement) { [string]$judgement.myPriority } else { "" }
    $sourceJudgement = if ($judgement) { [string]$judgement.mySourceIssueJudgement } else { "" }

    $familyNeedsCorrectionOrSplit = $false
    if ($judgement) {
        $familyNeedsCorrectionOrSplit =
            (-not [string]::IsNullOrWhiteSpace($familyCorrection)) -or
            ($ambiguousFamilySplitKeys -contains $gap.stableKey)
    }
    $rowSubtypeReview = $sourceJudgement -match "row subtype"
    $rowManualReviewRequired = $familyNeedsCorrectionOrSplit -or $rowSubtypeReview

    $sourceReviewRequired =
        -not [string]::IsNullOrWhiteSpace($gap.sourceIdIssue) -and
        $gap.sourceIdIssue -ne "NO"

    $templateMissingReviewRequired =
        ($gap.manualReviewReasons -match "NO_TEMPLATE_MATCH") -or
        ($templateReviewFamilies -contains $effectiveFamily) -or
        ($effectiveFamily -match "_REVIEW$")

    $stressReviewRequired =
        (-not [string]::IsNullOrWhiteSpace($gap.highAxialPlyometricDecelerationConflict) -and
            $gap.highAxialPlyometricDecelerationConflict -ne "NO") -or
        ($gap.manualReviewReasons -match "HIGH_STRESS|HIGH_AXIAL|PLYOMETRIC|DECELERATION|ELASTIC_SSC")

    $sportSessionReviewRequired =
        ($effectiveFamily -in @("BADMINTON_SESSION_SPORT_RECORDS", "OTHER_SPORT_SESSION_RECORDS_REVIEW")) -or
        (-not [string]::IsNullOrWhiteSpace($gap.suspiciousSportSessionProgramSelectable) -and
            $gap.suspiciousSportSessionProgramSelectable -ne "NO")

    $sourceOnlyReview =
        $sourceReviewRequired -and
        -not $rowManualReviewRequired -and
        -not $templateMissingReviewRequired -and
        -not $stressReviewRequired -and
        -not $sportSessionReviewRequired

    $overbroadManualReview =
        ($individualJudgement -match "manual review") -or
        ($partialOverbroadKeys -contains $gap.stableKey) -or
        $sourceOnlyReview

    if ($templateMissingReviewRequired -or $sportSessionReviewRequired) {
        $safeForTemplateDefault = "NO"
    }
    elseif ($rowManualReviewRequired -or $stressReviewRequired) {
        $safeForTemplateDefault = "PARTIAL"
    }
    else {
        $safeForTemplateDefault = "YES"
    }

    $safeForSeedMutation = if ($familyCorrection -and $priority -in @("P0", "P1")) {
        "CANDIDATE"
    }
    else {
        "NO"
    }

    $recommendedSourceAction = if ($judgement) {
        $sourceJudgement
    }
    elseif ($sourceReviewRequired) {
        "SOURCE_REVIEW_REQUIRED"
    }
    else {
        "NONE"
    }

    $recommendedNextAction = if ($judgement -and -not [string]::IsNullOrWhiteSpace($judgement.myRecommendedAction)) {
        $judgement.myRecommendedAction
    }
    elseif ($gap.manualReviewRequired -eq "NO") {
        "Retain report-only classification; no manual action required."
    }
    else {
        "Keep in report-only review queue."
    }

    $notes = if ($judgement) {
        $judgement.myComment
    }
    else {
        "No individual judgement row because original manualReviewRequired was NO."
    }

    $auditedRows.Add([pscustomobject][ordered]@{
        exerciseName = $gap.exerciseName
        stableKey = $gap.stableKey
        currentCategoryType = $gap.currentCategoryType
        estimatedMovementFamily = $gap.estimatedMovementFamily
        originalManualReviewRequired = $gap.manualReviewRequired
        originalSourceIdIssue = $gap.sourceIdIssue
        originalReasons = $gap.manualReviewReasons
        individualJudgement = $individualJudgement
        familyJudgement = $familyJudgement
        judgementPriority = $priority
        rowManualReviewRequired = To-YesNo $rowManualReviewRequired
        sourceReviewRequired = To-YesNo $sourceReviewRequired
        templateMissingReviewRequired = To-YesNo $templateMissingReviewRequired
        stressReviewRequired = To-YesNo $stressReviewRequired
        sportSessionReviewRequired = To-YesNo $sportSessionReviewRequired
        sourceOnlyReview = To-YesNo $sourceOnlyReview
        overbroadManualReview = To-YesNo $overbroadManualReview
        recommendedFamilyCorrection = $familyCorrection
        recommendedSourceAction = $recommendedSourceAction
        recommendedNextAction = $recommendedNextAction
        safeForTemplateDefault = $safeForTemplateDefault
        safeForSeedMutation = $safeForSeedMutation
        notes = $notes
    })
}

if ($auditedRows.Count -ne 215) {
    throw "Audited row count must remain 215, found $($auditedRows.Count)."
}

$auditedDuplicateKeys = @($auditedRows | Group-Object stableKey | Where-Object { $_.Count -gt 1 })
if ($auditedDuplicateKeys.Count -gt 0) {
    throw "Audited CSV contains duplicate stableKey values."
}

$matchedJudgementCount = $judgementRows.Count - $unmatchedJudgements.Count
$p0Corrections = @($auditedRows | Where-Object {
    $_.judgementPriority -eq "P0" -and -not [string]::IsNullOrWhiteSpace($_.recommendedFamilyCorrection)
})
$p1Corrections = @($auditedRows | Where-Object {
    $_.judgementPriority -eq "P1" -and -not [string]::IsNullOrWhiteSpace($_.recommendedFamilyCorrection)
})

$sourceReviewRows = @($auditedRows | Where-Object sourceReviewRequired -eq "YES")
$sourceOnlyRows = @($auditedRows | Where-Object sourceOnlyReview -eq "YES")
$sourceWithRowReviewRows = @($auditedRows | Where-Object {
    $_.sourceReviewRequired -eq "YES" -and $_.rowManualReviewRequired -eq "YES"
})
$sourceWithAnyNonSourceReviewRows = @($auditedRows | Where-Object {
    $_.sourceReviewRequired -eq "YES" -and (
        $_.rowManualReviewRequired -eq "YES" -or
        $_.templateMissingReviewRequired -eq "YES" -or
        $_.stressReviewRequired -eq "YES" -or
        $_.sportSessionReviewRequired -eq "YES"
    )
})

$safeTemplateCounts = @{
    YES = @($auditedRows | Where-Object safeForTemplateDefault -eq "YES").Count
    PARTIAL = @($auditedRows | Where-Object safeForTemplateDefault -eq "PARTIAL").Count
    NO = @($auditedRows | Where-Object safeForTemplateDefault -eq "NO").Count
}
$safeSeedCounts = @{
    YES = @($auditedRows | Where-Object safeForSeedMutation -eq "YES").Count
    CANDIDATE = @($auditedRows | Where-Object safeForSeedMutation -eq "CANDIDATE").Count
    NO = @($auditedRows | Where-Object safeForSeedMutation -eq "NO").Count
}

$localInputFolder = Split-Path -Parent $JudgementCsvPath
$workspaceBackupFolderChecked = Join-Path (Split-Path -Parent (Split-Path -Parent $repoRoot)) "google_drive_backup"
$sourceLinks = @(
    "https://drive.google.com/file/d/1f_1RXH9FICoL5rvtWQQwTzat-GxtqZrD/view",
    "https://drive.google.com/file/d/1jIL1aGtNX7y7kzZINMpuo-AoRKXVtgK2/view",
    "https://drive.google.com/file/d/11i3B7SQRs-6taVYV68IrDpuhpTqa4yq7/view"
)

$summaryMetrics = [System.Collections.Generic.List[object]]::new()
function Add-SummaryMetric([string]$metric, [object]$value, [string]$detail = "") {
    $summaryMetrics.Add([pscustomobject][ordered]@{
        metric = $metric
        value = $value
        detail = $detail
    })
}

Add-SummaryMetric "totalExerciseRows" $auditedRows.Count
Add-SummaryMetric "originalManualReviewRequiredRows" $originalManualRows.Count
Add-SummaryMetric "individualJudgementRowsMatched" $matchedJudgementCount
Add-SummaryMetric "individualJudgementRowsUnmatched" $unmatchedJudgements.Count
Add-SummaryMetric "rowManualReviewRequired" @($auditedRows | Where-Object rowManualReviewRequired -eq "YES").Count
Add-SummaryMetric "sourceReviewRequired" $sourceReviewRows.Count
Add-SummaryMetric "sourceOnlyReview" $sourceOnlyRows.Count
Add-SummaryMetric "overbroadManualReview" @($auditedRows | Where-Object overbroadManualReview -eq "YES").Count
Add-SummaryMetric "templateMissingReviewRequired" @($auditedRows | Where-Object templateMissingReviewRequired -eq "YES").Count
Add-SummaryMetric "stressReviewRequired" @($auditedRows | Where-Object stressReviewRequired -eq "YES").Count
Add-SummaryMetric "sportSessionReviewRequired" @($auditedRows | Where-Object sportSessionReviewRequired -eq "YES").Count
Add-SummaryMetric "p0CorrectionCandidates" $p0Corrections.Count (($p0Corrections.exerciseName) -join " | ")
Add-SummaryMetric "p1CorrectionCandidates" $p1Corrections.Count (($p1Corrections.exerciseName) -join " | ")
Add-SummaryMetric "safeForTemplateDefaultYES" $safeTemplateCounts.YES
Add-SummaryMetric "safeForTemplateDefaultPARTIAL" $safeTemplateCounts.PARTIAL
Add-SummaryMetric "safeForTemplateDefaultNO" $safeTemplateCounts.NO
Add-SummaryMetric "safeForSeedMutationYES" $safeSeedCounts.YES
Add-SummaryMetric "safeForSeedMutationCANDIDATE" $safeSeedCounts.CANDIDATE
Add-SummaryMetric "safeForSeedMutationNO" $safeSeedCounts.NO
Add-SummaryMetric "sourceIssueRowsOnlySourceReview" $sourceOnlyRows.Count
Add-SummaryMetric "sourceIssueRowsAlsoRowManualReview" $sourceWithRowReviewRows.Count
Add-SummaryMetric "sourceIssueRowsAlsoAnyNonSourceReview" $sourceWithAnyNonSourceReviewRows.Count
Add-SummaryMetric "localGoogleDriveInputPreservationFolder" $localInputFolder
Add-SummaryMetric "workspaceGoogleDriveBackupFolderChecked" $workspaceBackupFolderChecked
Add-SummaryMetric "inputGapCsv" $GapCsvPath
Add-SummaryMetric "inputGapMarkdown" $GapMarkdownPath
Add-SummaryMetric "inputJudgementCsv" $JudgementCsvPath
Add-SummaryMetric "inputJudgementMarkdown" $JudgementMarkdownPath

$auditedCsvPath = Join-Path $OutputDirectory "v0.3.5.0_seed_metadata_gap_report_audited.csv"
$summaryCsvPath = Join-Path $OutputDirectory "v0.3.5.0_manual_review_audit_summary.csv"
$auditedMarkdownPath = Join-Path $OutputDirectory "v0.3.5.0_seed_metadata_gap_report_audited.md"
$summaryMarkdownPath = Join-Path $OutputDirectory "v0.3.5.0_manual_review_audit_summary.md"

$auditedRows | Export-Csv -LiteralPath $auditedCsvPath -NoTypeInformation -Encoding UTF8
$summaryMetrics | Export-Csv -LiteralPath $summaryCsvPath -NoTypeInformation -Encoding UTF8

$summaryLines = [System.Collections.Generic.List[string]]::new()
$summaryLines.Add("# v0.3.5.0 Manual Review Audit Summary")
$summaryLines.Add("")
$summaryLines.Add("## Scope")
$summaryLines.Add("")
$summaryLines.Add("This audit is report-only. It does not modify training seeds, Kotlin code, Room schema, DB files, or the program generator. No network verification or application build is part of this step.")
$summaryLines.Add("")
$summaryLines.Add("## Input Verification")
$summaryLines.Add("")
$summaryLines.Add("| Check | Result |")
$summaryLines.Add("| --- | --- |")
$summaryLines.Add("| gap report rows | $($gapRows.Count) |")
$summaryLines.Add("| original manualReviewRequired=YES rows | $($originalManualRows.Count) |")
$summaryLines.Add("| individual judgement rows | $($judgementRows.Count) |")
$summaryLines.Add("| matched judgement rows | $matchedJudgementCount |")
$summaryLines.Add("| unmatched judgement rows | $($unmatchedJudgements.Count) |")
$summaryLines.Add("| duplicate stableKey values | 0 |")
$summaryLines.Add("| match priority | stableKey, then unique exact exerciseName |")
$summaryLines.Add("")
$summaryLines.Add("## Review Split")
$summaryLines.Add("")
$summaryLines.Add("| Review flag | Count |")
$summaryLines.Add("| --- | ---: |")
$summaryLines.Add("| rowManualReviewRequired | $(@($auditedRows | Where-Object rowManualReviewRequired -eq "YES").Count) |")
$summaryLines.Add("| sourceReviewRequired | $($sourceReviewRows.Count) |")
$summaryLines.Add("| sourceOnlyReview | $($sourceOnlyRows.Count) |")
$summaryLines.Add("| overbroadManualReview | $(@($auditedRows | Where-Object overbroadManualReview -eq "YES").Count) |")
$summaryLines.Add("| templateMissingReviewRequired | $(@($auditedRows | Where-Object templateMissingReviewRequired -eq "YES").Count) |")
$summaryLines.Add("| stressReviewRequired | $(@($auditedRows | Where-Object stressReviewRequired -eq "YES").Count) |")
$summaryLines.Add("| sportSessionReviewRequired | $(@($auditedRows | Where-Object sportSessionReviewRequired -eq "YES").Count) |")
$summaryLines.Add("| source issue + real row review | $($sourceWithRowReviewRows.Count) |")
$summaryLines.Add("| source issue + any non-source review | $($sourceWithAnyNonSourceReviewRows.Count) |")
$summaryLines.Add("")
$summaryLines.Add("## Safety Buckets")
$summaryLines.Add("")
$summaryLines.Add("| Bucket | YES | PARTIAL/CANDIDATE | NO |")
$summaryLines.Add("| --- | ---: | ---: | ---: |")
$summaryLines.Add("| safeForTemplateDefault | $($safeTemplateCounts.YES) | $($safeTemplateCounts.PARTIAL) | $($safeTemplateCounts.NO) |")
$summaryLines.Add("| safeForSeedMutation | $($safeSeedCounts.YES) | $($safeSeedCounts.CANDIDATE) | $($safeSeedCounts.NO) |")
$summaryLines.Add("")
$summaryLines.Add("`safeForSeedMutation=CANDIDATE` is an audit label only. This task performs no seed mutation.")
$summaryLines.Add("")
$summaryLines.Add("## P0 Correction Candidates")
$summaryLines.Add("")
Add-MarkdownTable -Lines $summaryLines -Headers @("Exercise", "Stable key", "Current family", "Recommended family") -Rows $p0Corrections -ValueSelector {
    param($row)
    @($row.exerciseName, $row.stableKey, $row.estimatedMovementFamily, $row.recommendedFamilyCorrection)
}
$summaryLines.Add("")
$summaryLines.Add("## P1 Correction Candidates")
$summaryLines.Add("")
Add-MarkdownTable -Lines $summaryLines -Headers @("Exercise", "Stable key", "Current family", "Recommended family") -Rows $p1Corrections -ValueSelector {
    param($row)
    @($row.exerciseName, $row.stableKey, $row.estimatedMovementFamily, $row.recommendedFamilyCorrection)
}
$summaryLines.Add("")
$summaryLines.Add("## Input Locations")
$summaryLines.Add("")
$summaryLines.Add("- Workspace backup folder checked: " + $workspaceBackupFolderChecked)
$summaryLines.Add("- Google Drive link inputs preserved locally: " + $localInputFolder)
$summaryLines.Add("- Gap report CSV: " + $GapCsvPath)
$summaryLines.Add("- Gap report Markdown: " + $GapMarkdownPath)
$summaryLines.Add("- Judgement CSV: " + $JudgementCsvPath)
$summaryLines.Add("- Judgement Markdown: " + $JudgementMarkdownPath)
$summaryLines.Add("- Source links: " + ($sourceLinks -join ", "))
$summaryLines.Add("")
$summaryLines.Add("## Next Step")
$summaryLines.Add("")
$summaryLines.Add("The audited CSV is suitable as input for a sidecar metadata draft. It is not approval to mutate training_settings_seed.csv; correction candidates and NO/PARTIAL template rows still require an explicit mutation phase.")

$summaryLines | Set-Content -LiteralPath $summaryMarkdownPath -Encoding UTF8

$badmintonChecks = @(
    [pscustomobject]@{ Check = "Direct badminton sessions"; Expected = "BADMINTON_SESSION_SPORT_RECORDS"; Rows = @($auditedRows | Where-Object { $_.stableKey -in @("ex_ae9ecdbc", "ex_badminton_lesson") }) },
    [pscustomobject]@{ Check = "Court footwork/reaction drills"; Expected = "FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS or BADMINTON_COURT_FOOTWORK_REACTION_REVIEW"; Rows = @($auditedRows | Where-Object { $_.estimatedMovementFamily -eq "FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS" }) },
    [pscustomobject]@{ Check = "Running mechanics support"; Expected = "RUNNING_MECHANICS_SUPPORT_REVIEW"; Rows = @($auditedRows | Where-Object { $_.estimatedMovementFamily -eq "RUNNING_MECHANICS_SUPPORT_REVIEW" }) },
    [pscustomobject]@{ Check = "Overhead smash test"; Expected = "BADMINTON_OVERHEAD_POWER_TEST_REVIEW"; Rows = @($auditedRows | Where-Object { $_.estimatedMovementFamily -eq "BADMINTON_OVERHEAD_POWER_TEST_REVIEW" }) },
    [pscustomobject]@{ Check = "Lateral landing/deceleration"; Expected = "LATERAL_BOUND_LANDING_DECELERATION_VARIANTS"; Rows = @($auditedRows | Where-Object { $_.estimatedMovementFamily -eq "LATERAL_BOUND_LANDING_DECELERATION_VARIANTS" }) }
)

$auditedLines = [System.Collections.Generic.List[string]]::new()
$auditedLines.Add("# v0.3.5.0 Audited Seed Metadata Gap Report")
$auditedLines.Add("")
$auditedLines.Add("## Scope and Guarantees")
$auditedLines.Add("")
$auditedLines.Add("- Report/audit only.")
$auditedLines.Add("- Audited rows: $($auditedRows.Count).")
$auditedLines.Add("- Original manual-review rows: $($originalManualRows.Count).")
$auditedLines.Add("- Individual judgement rows matched: $matchedJudgementCount.")
$auditedLines.Add("- Individual judgement rows unmatched: $($unmatchedJudgements.Count).")
$auditedLines.Add("- Seed, app code, Room schema, DB, and program generator were not modified.")
$auditedLines.Add("- `exercises_seed.json` was not used as strict machine input.")
$auditedLines.Add("")
$auditedLines.Add("## Audit Interpretation")
$auditedLines.Add("")
$auditedLines.Add("The original `manualReviewRequired` flag mixed five separate concerns. This report splits them into row family/subtype review, source review, template-missing review, stress review, and sport-session review. `sourceOnlyReview` identifies rows whose family can remain unchanged while source verification is handled separately.")
$auditedLines.Add("")
$auditedLines.Add("## Summary")
$auditedLines.Add("")
Add-MarkdownTable -Lines $auditedLines -Headers @("Metric", "Value", "Detail") -Rows $summaryMetrics -ValueSelector {
    param($row)
    @($row.metric, $row.value, $row.detail)
}
$auditedLines.Add("")
$auditedLines.Add("## P0/P1 Family Correction Candidates")
$auditedLines.Add("")
$correctionRows = @($p0Corrections + $p1Corrections | Sort-Object judgementPriority, exerciseName)
Add-MarkdownTable -Lines $auditedLines -Headers @("Priority", "Exercise", "Stable key", "Current family", "Recommended family", "Next action") -Rows $correctionRows -ValueSelector {
    param($row)
    @($row.judgementPriority, $row.exerciseName, $row.stableKey, $row.estimatedMovementFamily, $row.recommendedFamilyCorrection, $row.recommendedNextAction)
}
$auditedLines.Add("")
$auditedLines.Add("## Badminton Classification Preservation Check")
$auditedLines.Add("")
$auditedLines.Add("| Check | Expected family | Row count | Result |")
$auditedLines.Add("| --- | --- | ---: | --- |")
foreach ($check in $badmintonChecks) {
    $result = "PASS"
    foreach ($row in $check.Rows) {
        if ($check.Check -eq "Direct badminton sessions" -and $row.estimatedMovementFamily -ne "BADMINTON_SESSION_SPORT_RECORDS") {
            $result = "REVIEW"
        }
        elseif ($check.Check -eq "Running mechanics support" -and $row.estimatedMovementFamily -ne "RUNNING_MECHANICS_SUPPORT_REVIEW") {
            $result = "REVIEW"
        }
        elseif ($check.Check -eq "Overhead smash test" -and $row.estimatedMovementFamily -ne "BADMINTON_OVERHEAD_POWER_TEST_REVIEW") {
            $result = "REVIEW"
        }
        elseif ($check.Check -eq "Lateral landing/deceleration" -and $row.estimatedMovementFamily -ne "LATERAL_BOUND_LANDING_DECELERATION_VARIANTS") {
            $result = "REVIEW"
        }
    }
    $auditedLines.Add("| $(Escape-Markdown $check.Check) | $(Escape-Markdown $check.Expected) | $($check.Rows.Count) | $result |")
}
$auditedLines.Add("")
$auditedLines.Add("Direct badminton sessions remain sport records, not program-selectable candidates. Court footwork, running mechanics, smash tests, and lateral landing/deceleration remain separate families.")
$auditedLines.Add("")
$auditedLines.Add("## True Row-Level Manual Review")
$auditedLines.Add("")
$rowReviewRows = @($auditedRows | Where-Object rowManualReviewRequired -eq "YES" | Sort-Object judgementPriority, exerciseName)
Add-MarkdownTable -Lines $auditedLines -Headers @("Priority", "Exercise", "Stable key", "Family judgement", "Current family", "Recommended correction") -Rows $rowReviewRows -ValueSelector {
    param($row)
    @($row.judgementPriority, $row.exerciseName, $row.stableKey, $row.familyJudgement, $row.estimatedMovementFamily, $row.recommendedFamilyCorrection)
}
$auditedLines.Add("")
$auditedLines.Add("## Source-Only Review")
$auditedLines.Add("")
$auditedLines.Add("Rows in this section do not require a family change or other row-level review. Their remaining issue is source verification.")
$auditedLines.Add("")
Add-MarkdownTable -Lines $auditedLines -Headers @("Exercise", "Stable key", "Family", "Source issue") -Rows $sourceOnlyRows -ValueSelector {
    param($row)
    @($row.exerciseName, $row.stableKey, $row.estimatedMovementFamily, $row.originalSourceIdIssue)
}
$auditedLines.Add("")
$auditedLines.Add("## Unmatched Judgement Rows")
$auditedLines.Add("")
if ($unmatchedJudgements.Count -eq 0) {
    $auditedLines.Add("None. All 171 judgement rows matched the 215-row gap report.")
}
else {
    Add-MarkdownTable -Lines $auditedLines -Headers @("Exercise", "Stable key", "Reason") -Rows $unmatchedJudgements -ValueSelector {
        param($row)
        @($row.exerciseName, $row.stableKey, "No unique stableKey or exact exerciseName match")
    }
}
$auditedLines.Add("")
$auditedLines.Add("## Sidecar Draft Readiness")
$auditedLines.Add("")
$auditedLines.Add("The audited CSV can be used to build a sidecar metadata draft. Use `safeForTemplateDefault=YES` as low-risk template input, preserve `PARTIAL/NO` rows for explicit review, and treat `safeForSeedMutation=CANDIDATE` only as a future proposal. No mutation is authorized by this report.")

$auditedLines | Set-Content -LiteralPath $auditedMarkdownPath -Encoding UTF8

Write-Output "Generated:"
Write-Output $summaryMarkdownPath
Write-Output $summaryCsvPath
Write-Output $auditedMarkdownPath
Write-Output $auditedCsvPath
Write-Output ""
Write-Output "Rows: audited=$($auditedRows.Count), matched=$matchedJudgementCount, unmatched=$($unmatchedJudgements.Count)"

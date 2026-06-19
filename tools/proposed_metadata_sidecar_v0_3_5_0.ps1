param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

function Require-File([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required input missing: $Path"
    }
}

function Read-Field([string]$Body, [string]$Name) {
    $match = [regex]::Match($Body, "(?m)^$([regex]::Escape($Name)):\s*(.+)$")
    if ($match.Success) { return $match.Groups[1].Value.Trim() }
    return ''
}

function Split-Keys([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -match '^NONE') { return @() }
    return @($Value -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function To-YesNo([bool]$Value) {
    if ($Value) { return 'YES' }
    return 'NO'
}

function Is-Yes([object]$Value) {
    return [string]$Value -eq 'YES'
}

function Convert-Contribution([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return 'REVIEW' }
    if ($Value -match '^NONE') { return 'NONE' }

    $pairs = [regex]::Matches($Value, '(?<key>[A-Z][A-Z0-9_]*)\s+(?<value>(?:0|1)\.\d+)')
    if ($pairs.Count -eq 0) { return 'REVIEW' }

    $result = [ordered]@{}
    $sum = 0.0
    foreach ($pair in $pairs) {
        $number = [double]::Parse($pair.Groups['value'].Value, [Globalization.CultureInfo]::InvariantCulture)
        $result[$pair.Groups['key'].Value] = $number
        $sum += $number
    }
    if ($sum -lt 0.90 -or $sum -gt 1.10) { return 'REVIEW' }
    return ($result | ConvertTo-Json -Compress)
}

function Select-ProgramSlot([string]$TemplateValue, [string]$MovementCategory) {
    if ([string]::IsNullOrWhiteSpace($TemplateValue)) { return 'REVIEW' }
    $choices = @($TemplateValue -split '\s+or\s+' | ForEach-Object { $_.Trim() })
    if ($choices.Count -eq 1) { return $choices[0] }
    if ($MovementCategory -eq 'ACCESSORY') { return $choices[-1] }
    return $choices[0]
}

function Select-ProgressMetric([string]$Family, [pscustomobject]$SeedRow, [string]$Subtype) {
    if ($Family -eq 'BADMINTON_SESSION_SPORT_RECORDS') { return 'TIME_OR_DISTANCE' }
    if ($Family -eq 'CARDIO_RECORDS') { return 'TIME_OR_DISTANCE' }
    if ($Family -eq 'FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS') { return 'TIME_OR_DISTANCE' }
    if ($Family -eq 'BADMINTON_OVERHEAD_POWER_TEST_REVIEW') { return 'TEST_REVIEW' }
    if ($Family -eq 'MOBILITY_WARMUP_RECOVERY_EXERCISES') { return 'QUALITY_BASED' }
    if ($Family -match 'PLYOMETRIC|LATERAL_BOUND|RUNNING_MECHANICS|POWER_CORE|TURKISH') { return 'QUALITY_BASED' }
    if ($Family -match 'ANTI_ROTATION|FACE_PULL|EXTERNAL_ROTATION') { return 'QUALITY_BASED' }
    if ($Family -match 'OTHER_SPORT_SESSION') { return 'TIME_OR_DISTANCE' }
    if ($Family -match '_REVIEW$') { return 'REVIEW' }

    $mainE1RmSubtypes = @(
        'BACK_SQUAT', 'FRONT_SQUAT', 'CONVENTIONAL_DEADLIFT', 'ROMANIAN_DEADLIFT',
        'BARBELL_BENCH_PRESS', 'PULL_UP', 'WEIGHTED_PULL_UP', 'BARBELL_ROW'
    )
    if ($Subtype -in $mainE1RmSubtypes -and $SeedRow.movement_category -eq 'STRENGTH') {
        return 'ESTIMATED_1RM'
    }
    if ($SeedRow.movement_category -eq 'ACCESSORY') { return 'VOLUME_LOAD' }
    if ($SeedRow.equipment_tags -match 'BODYWEIGHT' -and $SeedRow.movement_category -eq 'STRENGTH') {
        return 'REPS_AT_LOAD'
    }
    return 'VOLUME_LOAD'
}

function Select-EvidenceLevel([string[]]$SourceIds, [hashtable]$Sources) {
    if ($SourceIds.Count -eq 0) { return 'heuristic_only' }
    $rank = @{
        heuristic_only = 0; coaching_consensus = 1; governing_body_resource = 2
        clinical_reference = 3; anatomy_textbook = 4; emg_study = 5
        biomechanics_review = 6; position_stand = 7; systematic_review = 8
        meta_analysis = 9; umbrella_review = 10
    }
    $levels = @($SourceIds | ForEach-Object { if ($Sources.ContainsKey($_)) { $Sources[$_].evidenceLevel } })
    if ($levels.Count -eq 0) { return 'heuristic_only' }
    return ($levels | Sort-Object { if ($rank.ContainsKey($_)) { $rank[$_] } else { -1 } } | Select-Object -First 1)
}

function Get-ProposalStatus(
    [bool]$Matched,
    [bool]$RowReview,
    [bool]$SourceReview,
    [bool]$TemplateReview,
    [bool]$StressReview,
    [bool]$SportReview,
    [string]$SafeTemplate,
    [bool]$HasConcreteFamily
) {
    if (-not $Matched) { return 'UNMATCHED_INPUT' }
    if ($RowReview) { return 'NEEDS_ROW_REVIEW' }
    if ($SportReview) { return 'SPORT_SESSION_REVIEW' }
    if ($TemplateReview) { return 'NEEDS_TEMPLATE_DEFINITION' }
    if ($StressReview) { return 'NEEDS_STRESS_REVIEW' }
    if ($SourceReview) { return 'NEEDS_SOURCE_REVIEW' }
    if ($SafeTemplate -eq 'YES') { return 'READY_TEMPLATE_CANDIDATE' }
    if (-not $HasConcreteFamily) { return 'DO_NOT_MUTATE_SEED' }
    return 'DO_NOT_MUTATE_SEED'
}

function Get-StatusReason([string]$Status) {
    switch ($Status) {
        'READY_TEMPLATE_CANDIDATE' { return 'Audited family template is complete and no review flag remains.' }
        'NEEDS_SOURCE_REVIEW' { return 'Family is usable as a draft, but evidence source verification remains.' }
        'NEEDS_ROW_REVIEW' { return 'Row family or subtype requires explicit human judgement before mutation.' }
        'NEEDS_TEMPLATE_DEFINITION' { return 'The proposed review family has no approved family template.' }
        'NEEDS_STRESS_REVIEW' { return 'Axial, plyometric, deceleration, SSC, or session stress mapping needs review.' }
        'SPORT_SESSION_REVIEW' { return 'Sport-session planning eligibility and load separation require review.' }
        'UNMATCHED_INPUT' { return 'Audited report and seed row did not match exactly.' }
        default { return 'Concrete mutation is not safe from the current report-only evidence.' }
    }
}

function Get-NextAction([string]$Status, [string]$AuditAction) {
    switch ($Status) {
        'READY_TEMPLATE_CANDIDATE' { return 'HUMAN_APPROVE_TEMPLATE_BEFORE_SEED_PATCH' }
        'NEEDS_SOURCE_REVIEW' { return 'VERIFY_EXISTING_SOURCE_IDS' }
        'NEEDS_ROW_REVIEW' { return 'CONFIRM_FAMILY_AND_SUBTYPE' }
        'NEEDS_TEMPLATE_DEFINITION' { return 'DEFINE_AND_REVIEW_FAMILY_TEMPLATE' }
        'NEEDS_STRESS_REVIEW' { return 'REVIEW_STRESS_AND_RECOVERY_MAPPING' }
        'SPORT_SESSION_REVIEW' { return 'CONFIRM_NOT_PROGRAM_SELECTABLE_AND_SESSION_LOAD_PATH' }
        'UNMATCHED_INPUT' { return 'RESOLVE_STABLE_KEY_MISMATCH' }
        default {
            if ($AuditAction) { return $AuditAction }
            return 'KEEP_DRAFT_ONLY'
        }
    }
}

$auditPath = Join-Path $RepoRoot 'outputs\v0.3.5.0_seed_metadata_gap_report_audited.csv'
$auditMdPath = Join-Path $RepoRoot 'outputs\v0.3.5.0_seed_metadata_gap_report_audited.md'
$summaryCsvPath = Join-Path $RepoRoot 'outputs\v0.3.5.0_manual_review_audit_summary.csv'
$summaryMdPath = Join-Path $RepoRoot 'outputs\v0.3.5.0_manual_review_audit_summary.md'
$sourcePath = Join-Path $RepoRoot 'docs\metadata_evidence_sources_v0.3.5.0.md'
$planPath = Join-Path $RepoRoot 'docs\exercise_metadata_reclassification_plan_v0.3.5.0.md'
$taxonomyPath = Join-Path $RepoRoot 'docs\metadata_taxonomy_delta_v0.3.5.0.md'
$validatorPath = Join-Path $RepoRoot 'docs\metadata_validator_requirements_v0.3.5.0.md'
$seedPath = Join-Path $RepoRoot 'app\src\main\assets\training_settings_seed.csv'
$outputDir = Join-Path $RepoRoot 'outputs'

$requiredInputs = @(
    $auditPath, $auditMdPath, $summaryCsvPath, $summaryMdPath, $sourcePath,
    $planPath, $taxonomyPath, $validatorPath, $seedPath
)
$requiredInputs | ForEach-Object { Require-File $_ }

$seedHashBefore = (Get-FileHash -Algorithm SHA256 -LiteralPath $seedPath).Hash
$auditRows = @(Import-Csv -LiteralPath $auditPath)
$seedRows = @(Import-Csv -LiteralPath $seedPath | Where-Object { $_.row_type -eq 'exercise' })
if ($auditRows.Count -ne 215) { throw "Audited report must contain 215 rows; found $($auditRows.Count)." }
if ($seedRows.Count -ne 215) { throw "Seed must contain 215 exercise rows; found $($seedRows.Count)." }

$seedByKey = @{}
foreach ($seedRow in $seedRows) {
    if ($seedByKey.ContainsKey($seedRow.stable_key)) { throw "Duplicate seed stableKey: $($seedRow.stable_key)" }
    $seedByKey[$seedRow.stable_key] = $seedRow
}

$sourceText = Get-Content -Raw -LiteralPath $sourcePath
$sources = @{}
$sourceBlocks = [regex]::Matches($sourceText, '(?ms)^sourceId:\s*(?<id>[A-Z0-9_]+)\r?\n(?<body>.*?)(?=^```$)')
foreach ($block in $sourceBlocks) {
    $id = $block.Groups['id'].Value.Trim()
    $sources[$id] = [pscustomobject]@{
        evidenceLevel = Read-Field $block.Groups['body'].Value 'evidenceLevel'
        verificationStatus = Read-Field $block.Groups['body'].Value 'verificationStatus'
    }
}

$planText = Get-Content -Raw -LiteralPath $planPath
$templates = @{}
$templateBlocks = [regex]::Matches($planText, '(?ms)^exerciseFamily:\s*(?<family>[A-Z][A-Z0-9_]+)\r?\n(?<body>.*?)(?=^```$)')
foreach ($block in $templateBlocks) {
    $family = $block.Groups['family'].Value.Trim()
    if ($family -cnotmatch '^[A-Z][A-Z0-9_]+$') { continue }
    $body = $block.Groups['body'].Value
    $primary = Read-Field $body 'typicalPrimaryMuscles'
    $secondary = Read-Field $body 'typicalSecondaryMuscles'
    $stabilizers = Read-Field $body 'typicalStabilizers'
    $tagText = @($primary, $secondary, $stabilizers) -join ', '
    $tagMatches = [regex]::Matches($tagText, '[A-Z][A-Z0-9_]+') | ForEach-Object { $_.Value }
    $tags = @($tagMatches | Where-Object { $_ -ne 'NONE' } | Select-Object -Unique)
    $sourcesForTemplate = @(Split-Keys (Read-Field $body 'sourceIds'))
    $templates[$family] = [pscustomobject]@{
        detailedTags = if ($primary -match '^NONE') { 'NONE' } else { $tags -join '|' }
        contribution = Convert-Contribution (Read-Field $body 'defaultMuscleContributionTemplate')
        programSlot = Read-Field $body 'typicalProgramSlot'
        redundancyGroup = Read-Field $body 'typicalRedundancyGroup'
        progressMetric = Read-Field $body 'typicalProgressMetricType'
        fatigueProfile = Read-Field $body 'typicalFatigueProfile'
        direct = Read-Field $body 'typicalBadmintonTransferDirect'
        supportive = Read-Field $body 'typicalBadmintonTransferSupportive'
        analysisEligibility = Read-Field $body 'typicalAnalysisEligibility'
        sourceIds = $sourcesForTemplate
    }
}
if ($templates.Count -ne 26) { throw "Expected 26 approved family templates; found $($templates.Count)." }

$subtypeByStableKey = @{
    barbell_back_squat = 'BACK_SQUAT'; ex_c5043892 = 'FRONT_SQUAT'; ex_ac7df636 = 'GOBLET_SQUAT'
    barbell_deadlift = 'CONVENTIONAL_DEADLIFT'; ex_d2bb7946 = 'ROMANIAN_DEADLIFT'; ex_9523db82 = 'ROMANIAN_DEADLIFT'
    pull_up = 'PULL_UP'; ex_e41f4c2b = 'WEIGHTED_PULL_UP'; ex_dc9e5953 = 'LAT_PULLDOWN'
    barbell_bench_press = 'BARBELL_BENCH_PRESS'; ex_de46b7f6 = 'BARBELL_ROW'; ex_30a0e9aa = 'ONE_ARM_ROW'
    face_pull = 'FACE_PULL'; ex_e8ff8cfa = 'BAND_EXTERNAL_ROTATION'; ex_99728d25 = 'PALLOF_PRESS'
    ex_e2efd0fe = 'BULGARIAN_SPLIT_SQUAT'; ex_34e7d21 = 'LATERAL_BOUND_TO_STICK'; ex_9b132c23 = 'LATERAL_DECELERATION_STOP'
    ex_ae9ecdbc = 'BADMINTON_SESSION'; ex_badminton_lesson = 'BADMINTON_LESSON'
    ex_33841b88 = 'SIX_CORNER_FOOTWORK'; ex_c5f4c242 = 'RANDOM_BEEP_FOOTWORK'; ex_91d8430b = 'BADMINTON_OVERHEAD_POWER_TEST'
    ex_ca5cce66 = 'STRAIGHT_ARM_PULLDOWN'; ex_c365e5e2 = 'ONE_ARM_STRAIGHT_ARM_PULLDOWN'
    ex_eaea872c = 'HIP_ADDUCTION'; ex_728da646 = 'HIP_ABDUCTION'; ex_4773b6ea = 'TRACK_SPRINT'
    ex_91f6958d = 'TURKISH_GETUP'; ex_16b298db = 'MINIBAND_LATERAL_WALK'; ex_3f184ef9 = 'BURPEE'
    ex_644e0f9d = 'V_UP'; ex_6463edad = 'DIP'; ex_a9b52886 = 'MOUNTAIN_CLIMBER'
    ex_64661229 = 'WALL_SIT'; ex_a345e30b = 'CAPTAINS_CHAIR_LEG_RAISE'; ex_fa34ddfe = 'CABLE_GLUTE_KICKBACK'
    ex_b3f447be = 'HANGING_LEG_RAISE'
}

$reviewDefaults = @{
    RUNNING_MECHANICS_SUPPORT_REVIEW = [pscustomobject]@{ direct='NONE'; supportive='ACCELERATION_SUPPORT,ANKLE_STIFFNESS_SUPPORT,NEURAL_SPEED_SUPPORT'; eligibility='FATIGUE,BADMINTON_TRANSFER,BALANCE'; fatigue='NEURAL_SPEED_SUPPORT,ELASTIC_SSC_REVIEW'; slot='RUNNING_MECHANICS_SUPPORT_REVIEW'; redundancy='RUNNING_MECHANICS_SUPPORT_REVIEW' }
    BADMINTON_OVERHEAD_POWER_TEST_REVIEW = [pscustomobject]@{ direct='OVERHEAD_REPETITION,OVERHEAD_POWER_TEST_REVIEW,GRIP_FOREARM_REVIEW'; supportive='ROTATION_POWER_SUPPORT'; eligibility='FATIGUE,BADMINTON_TRANSFER,TEST_ONLY'; fatigue='OVERHEAD_REPETITION,ROTATION_POWER,GRIP_FOREARM'; slot='NOT_PROGRAM_SELECTABLE'; redundancy='BADMINTON_OVERHEAD_POWER_TEST_REVIEW' }
    FOREARM_GRIP_ACCESSORY_REVIEW = [pscustomobject]@{ direct='NONE'; supportive='GRIP_FOREARM_SUPPORT'; eligibility='FATIGUE,HYPERTROPHY_VOLUME,BADMINTON_TRANSFER,BALANCE'; fatigue='LOCAL_MUSCLE,GRIP_FOREARM'; slot='FOREARM_GRIP_ACCESSORY_REVIEW'; redundancy='FOREARM_GRIP_ACCESSORY_REVIEW' }
    CHEST_ISOLATION_REVIEW = [pscustomobject]@{ direct='NONE'; supportive='UPPER_PUSH_SUPPORT'; eligibility='FATIGUE,HYPERTROPHY_VOLUME,BALANCE'; fatigue='LOCAL_MUSCLE'; slot='CHEST_ISOLATION_REVIEW'; redundancy='CHEST_ISOLATION_REVIEW' }
    PULLOVER_LAT_CHEST_ACCESSORY_REVIEW = [pscustomobject]@{ direct='NONE'; supportive='UPPER_PULL_SUPPORT,OVERHEAD_SUPPORT'; eligibility='FATIGUE,HYPERTROPHY_VOLUME,BALANCE'; fatigue='LOCAL_MUSCLE,OVERHEAD_REPETITION_REVIEW'; slot='PULLOVER_ACCESSORY_REVIEW'; redundancy='PULLOVER_LAT_CHEST_ACCESSORY_REVIEW' }
    POWER_CORE_SHOULDER_REVIEW = [pscustomobject]@{ direct='NONE'; supportive='ROTATION_POWER_SUPPORT,OVERHEAD_POWER_SUPPORT'; eligibility='FATIGUE,BADMINTON_TRANSFER,BALANCE'; fatigue='ROTATION_POWER,OVERHEAD_REPETITION_REVIEW'; slot='POWER_CORE_SHOULDER_REVIEW'; redundancy='POWER_CORE_SHOULDER_REVIEW' }
    POSTERIOR_CHAIN_LOW_LOAD_REVIEW = [pscustomobject]@{ direct='NONE'; supportive='POSTERIOR_CHAIN_CAPACITY'; eligibility='FATIGUE,BALANCE,RECOVERY_ONLY'; fatigue='LOCAL_MUSCLE,LOW_FATIGUE_REHAB_REVIEW'; slot='POSTERIOR_CHAIN_LOW_LOAD_REVIEW'; redundancy='POSTERIOR_CHAIN_LOW_LOAD_REVIEW' }
    OTHER_SPORT_SESSION_RECORDS_REVIEW = [pscustomobject]@{ direct='NONE'; supportive='SPORT_SPECIFIC_REVIEW'; eligibility='FATIGUE,TEST_ONLY'; fatigue='SPORT_SESSION_LOAD_REVIEW'; slot='NOT_PROGRAM_SELECTABLE'; redundancy='OTHER_SPORT_SESSION_RECORDS_REVIEW' }
}

$rows = New-Object System.Collections.Generic.List[object]
$unmatched = New-Object System.Collections.Generic.List[string]
foreach ($audit in $auditRows) {
    $matched = $seedByKey.ContainsKey($audit.stableKey)
    if (-not $matched) {
        $unmatched.Add($audit.stableKey)
        $seed = [pscustomobject]@{ exercise_name=$audit.exerciseName; category=''; movement_pattern=''; movement_category=''; force_type=''; equipment_tags='' }
    } else {
        $seed = $seedByKey[$audit.stableKey]
    }

    $family = if ($audit.recommendedFamilyCorrection) { $audit.recommendedFamilyCorrection } else { $audit.estimatedMovementFamily }
    $template = if ($templates.ContainsKey($family)) { $templates[$family] } else { $null }
    $reviewDefault = if ($reviewDefaults.ContainsKey($family)) { $reviewDefaults[$family] } else { $null }
    $subtype = if ($subtypeByStableKey.ContainsKey($audit.stableKey)) { $subtypeByStableKey[$audit.stableKey] } else { 'NEEDS_SUBTYPE_REVIEW' }

    $sourceIds = if ($template) { @($template.sourceIds) } else { @() }
    if ($family -eq 'ROW_VARIANTS') {
        $sourceIds = @($sourceIds | Where-Object { $_ -ne 'C_HORIZONTAL_ROW_EMG_NEEDS_SOURCE' })
        if ('C_INVERTED_ROW_EMG_SNARR_ESCO_2013' -notin $sourceIds) { $sourceIds += 'C_INVERTED_ROW_EMG_SNARR_ESCO_2013' }
    }
    $invalidSources = @($sourceIds | Where-Object { -not $sources.ContainsKey($_) })
    $networkSources = @($sourceIds | Where-Object { $sources.ContainsKey($_) -and $sources[$_].verificationStatus -match 'needs_network_recheck|unknown|deprecated' })

    $rowReview = Is-Yes $audit.rowManualReviewRequired
    $templateReview = Is-Yes $audit.templateMissingReviewRequired
    $stressReview = Is-Yes $audit.stressReviewRequired
    $sportReview = Is-Yes $audit.sportSessionReviewRequired
    $sourceReview = (Is-Yes $audit.sourceReviewRequired) -or $invalidSources.Count -gt 0 -or $networkSources.Count -gt 0 -or $sourceIds.Count -eq 0

    $programSlot = if ($template) { Select-ProgramSlot $template.programSlot $seed.movement_category } elseif ($reviewDefault) { $reviewDefault.slot } else { 'REVIEW' }
    $redundancy = if ($template) { $template.redundancyGroup } elseif ($reviewDefault) { $reviewDefault.redundancy } else { 'REVIEW' }
    $direct = if ($template) { $template.direct } elseif ($reviewDefault) { $reviewDefault.direct } else { 'REVIEW' }
    $supportive = if ($template) { $template.supportive } elseif ($reviewDefault) { $reviewDefault.supportive } else { 'REVIEW' }
    $eligibility = if ($template) { $template.analysisEligibility } elseif ($reviewDefault) { $reviewDefault.eligibility } else { 'REVIEW' }
    $fatigue = if ($template) { $template.fatigueProfile } elseif ($reviewDefault) { $reviewDefault.fatigue } else { 'REVIEW' }
    $tags = if ($template) { $template.detailedTags } else { 'REVIEW' }
    $contribution = if ($template) { $template.contribution } else { 'REVIEW' }

    if ($family -eq 'BADMINTON_SESSION_SPORT_RECORDS') {
        $programSlot = 'NOT_PROGRAM_SELECTABLE'
        $direct = 'DIRECT_SPORT_PARTICIPATION,BADMINTON_DIRECT_PLAY'
        $supportive = 'NONE'
    }
    if ($family -eq 'FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS') {
        $direct = 'FOOTWORK,REACTION,DECELERATION,LUNGE_REACH'
        $supportive = 'ACCELERATION_SUPPORT,JUMP_LANDING_SUPPORT'
        $fatigue = 'NEURAL_SPEED,DECELERATION,ELASTIC_SSC,BADMINTON_COURT'
    }

    $status = Get-ProposalStatus $matched $rowReview $sourceReview $templateReview $stressReview $sportReview $audit.safeForTemplateDefault (-not [string]::IsNullOrWhiteSpace($family))
    $confidence = if ($rowReview) { 'NEEDS_REVIEW' } elseif ($templateReview -or $sportReview -or $stressReview) { 'LOW' } elseif ($sourceReview) { 'MEDIUM' } else { 'HIGH' }
    # The seed has no activityKind column, so derive only from structured movement metadata.
    $activityKind = if ($seed.movement_pattern -match '(^|\|)SPORT_SESSION(\||$)') { 'SPORT_SESSION' } else { 'EXERCISE' }
    $planning = if ($sportReview) { 'NOT_PRESENT_IN_SEED;PROGRAM_SELECTABLE_CONFLICT_REVIEW' } else { 'NOT_PRESENT_IN_SEED' }

    $noteParts = New-Object System.Collections.Generic.List[string]
    $noteParts.Add('Draft only; do not mutate seed without human approval.')
    if ($contribution -notin @('NONE','REVIEW')) { $noteParts.Add('Muscle contribution is an app-internal heuristic, not a physiological measurement.') }
    if ($subtype -eq 'NEEDS_SUBTYPE_REVIEW') { $noteParts.Add('Subtype intentionally left for review; no broad exercise-name parsing was used.') }
    if ($family -eq 'ROW_VARIANTS' -and $subtype -notmatch 'INVERTED') { $noteParts.Add('Inverted-row evidence is retained, but non-inverted row subtype needs manual source review.') }
    if ($invalidSources.Count -gt 0) { $noteParts.Add("Invalid source IDs: $($invalidSources -join '|')") }
    if ($networkSources.Count -gt 0) { $noteParts.Add("Sources need network recheck: $($networkSources -join '|')") }
    if (-not $template) { $noteParts.Add('No approved family template; concrete muscle values were not invented.') }

    $rows.Add([pscustomobject][ordered]@{
        exerciseName = $audit.exerciseName
        stableKey = $audit.stableKey
        currentCategoryType = $audit.currentCategoryType
        currentMovementPattern = $seed.movement_pattern
        currentMovementCategory = $seed.movement_category
        currentForceType = $seed.force_type
        currentActivityKind = $activityKind
        currentPlanningEligibility = $planning
        proposedMovementFamily = $family
        proposedMovementSubtype = $subtype
        proposedDetailedMuscleTags = $tags
        proposedMuscleContribution = $contribution
        proposedProgramSlot = $programSlot
        proposedRedundancyGroup = $redundancy
        proposedProgressMetricType = Select-ProgressMetric $family $seed $subtype
        proposedFatigueProfile = $fatigue
        proposedBadmintonTransferDirect = $direct
        proposedBadmintonTransferSupportive = $supportive
        proposedAnalysisEligibility = $eligibility
        proposedEvidenceSourceGroup = if ($sourceIds.Count -gt 0) { $sourceIds -join '|' } else { 'NEEDS_SOURCE_TEMPLATE' }
        proposedEvidenceLevel = Select-EvidenceLevel $sourceIds $sources
        metadataConfidenceCandidate = $confidence
        sourceReviewRequired = To-YesNo $sourceReview
        rowManualReviewRequired = $audit.rowManualReviewRequired
        templateMissingReviewRequired = $audit.templateMissingReviewRequired
        stressReviewRequired = $audit.stressReviewRequired
        sportSessionReviewRequired = $audit.sportSessionReviewRequired
        sourceOnlyReview = $audit.sourceOnlyReview
        overbroadManualReview = $audit.overbroadManualReview
        safeForTemplateDefault = $audit.safeForTemplateDefault
        safeForSeedMutation = $audit.safeForSeedMutation
        proposalStatus = $status
        proposalReason = Get-StatusReason $status
        recommendedNextAction = Get-NextAction $status $audit.recommendedNextAction
        notes = $noteParts -join ' '
    })
}

if ($rows.Count -ne 215) { throw "Sidecar must contain 215 rows; found $($rows.Count)." }
$duplicateKeys = @($rows | Group-Object stableKey | Where-Object Count -gt 1)
if ($duplicateKeys.Count -gt 0) { throw "Duplicate sidecar stableKeys: $($duplicateKeys.Name -join ', ')" }
if ($unmatched.Count -gt 0) { throw "Unmatched audited rows: $($unmatched -join ', ')" }

$forbiddenSourceRows = @($rows | Where-Object { $_.proposedEvidenceSourceGroup -match 'C_HORIZONTAL_ROW_EMG_NEEDS_SOURCE' })
if ($forbiddenSourceRows.Count -gt 0) { throw 'Deprecated horizontal-row source leaked into sidecar.' }

$csvPath = Join-Path $outputDir 'v0.3.5.0_proposed_exercise_metadata_sidecar.csv'
$mdPath = Join-Path $outputDir 'v0.3.5.0_proposed_exercise_metadata_sidecar.md'
$jsonPath = Join-Path $outputDir 'v0.3.5.0_proposed_exercise_metadata_sidecar.json'
$generationSummaryPath = Join-Path $outputDir 'v0.3.5.0_sidecar_generation_summary.md'

$rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8

$jsonDocument = [ordered]@{
    schemaVersion = 'v0.3.5.0-proposed-sidecar-draft-1'
    draftOnly = $true
    generatedAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ssK')
    seedSha256 = $seedHashBefore
    rowCount = $rows.Count
    rows = $rows
}
$jsonDocument | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

$statusCounts = @{}
foreach ($statusName in @('READY_TEMPLATE_CANDIDATE','NEEDS_SOURCE_REVIEW','NEEDS_ROW_REVIEW','NEEDS_TEMPLATE_DEFINITION','NEEDS_STRESS_REVIEW','SPORT_SESSION_REVIEW','DO_NOT_MUTATE_SEED','UNMATCHED_INPUT')) {
    $statusCounts[$statusName] = @($rows | Where-Object proposalStatus -eq $statusName).Count
}
$p0CorrectionKeys = @($auditRows | Where-Object { $_.judgementPriority -eq 'P0' -and $_.recommendedFamilyCorrection } | ForEach-Object stableKey)
$p1CorrectionKeys = @($auditRows | Where-Object { $_.judgementPriority -eq 'P1' -and $_.recommendedFamilyCorrection } | ForEach-Object stableKey)
$p0 = @($rows | Where-Object { $_.stableKey -in $p0CorrectionKeys })
$p1 = @($rows | Where-Object { $_.stableKey -in $p1CorrectionKeys })
$badmintonDirect = @($rows | Where-Object proposedMovementFamily -eq 'BADMINTON_SESSION_SPORT_RECORDS')
$badmintonFootwork = @($rows | Where-Object proposedMovementFamily -eq 'FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS')
$runningSupport = @($rows | Where-Object proposedMovementFamily -eq 'RUNNING_MECHANICS_SUPPORT_REVIEW')
$badmintonTests = @($rows | Where-Object proposedMovementFamily -eq 'BADMINTON_OVERHEAD_POWER_TEST_REVIEW')

$safeTemplateCounts = @($rows | Group-Object safeForTemplateDefault | Sort-Object Name)
$safeMutationCounts = @($rows | Group-Object safeForSeedMutation | Sort-Object Name)

$md = New-Object System.Collections.Generic.List[string]
$md.Add('# Proposed Exercise Metadata Sidecar v0.3.5.0')
$md.Add('')
$md.Add('Draft-only metadata candidates generated from the audited report and approved family templates. This file is not a seed migration.')
$md.Add('')
$md.Add('## Proposal Status Summary')
$md.Add('')
$md.Add('| proposalStatus | count |')
$md.Add('| --- | ---: |')
foreach ($name in $statusCounts.Keys | Sort-Object) { $md.Add("| $name | $($statusCounts[$name]) |") }
$md.Add('')
$md.Add('## P0 Correction Candidates')
$md.Add('')
$md.Add('| exerciseName | stableKey | current estimated family | proposed corrected family | reason | seed mutation |')
$md.Add('| --- | --- | --- | --- | --- | --- |')
foreach ($row in $p0) {
    $audit = $auditRows | Where-Object stableKey -eq $row.stableKey | Select-Object -First 1
    $reason = ($audit.individualJudgement -replace '\|','/')
    $md.Add("| $($row.exerciseName) | $($row.stableKey) | $($audit.estimatedMovementFamily) | $($row.proposedMovementFamily) | $reason | PROHIBITED_DRAFT_ONLY |")
}
$md.Add('')
$md.Add('## Badminton Classification Checks')
$md.Add('')
$md.Add("- Direct session records: $($badmintonDirect.Count); all remain `BADMINTON_SESSION_SPORT_RECORDS` and `NOT_PROGRAM_SELECTABLE` candidates.")
$md.Add("- Court footwork/reaction drills: $($badmintonFootwork.Count); all remain separate from direct sport participation.")
$md.Add("- Running mechanics support drills: $($runningSupport.Count); all remain supportive rather than direct badminton play.")
$md.Add("- Overhead power skill test review rows: $($badmintonTests.Count); not reclassified as footwork.")
$md.Add('')
$md.Add('## Row Draft')
$md.Add('')
$md.Add('| exerciseName | stableKey | proposedMovementFamily | proposedMovementSubtype | proposalStatus | next action |')
$md.Add('| --- | --- | --- | --- | --- | --- |')
foreach ($row in $rows) {
    $md.Add("| $($row.exerciseName) | $($row.stableKey) | $($row.proposedMovementFamily) | $($row.proposedMovementSubtype) | $($row.proposalStatus) | $($row.recommendedNextAction -replace '\|','/') |")
}
$md.Add('')
$md.Add('## Safety Boundary')
$md.Add('')
$md.Add('- No seed, app code, Room schema, database, or program-generator mutation was performed.')
$md.Add('- Muscle contribution objects are app-internal heuristics and are not physiological measurements.')
$md.Add('- Review families without an approved template keep concrete muscle/source fields as `REVIEW` or `NEEDS_SOURCE_TEMPLATE`.')
$md | Set-Content -LiteralPath $mdPath -Encoding UTF8

$summary = New-Object System.Collections.Generic.List[string]
$summary.Add('# Sidecar Generation Summary v0.3.5.0')
$summary.Add('')
$summary.Add('## Counts')
$summary.Add('')
$summary.Add("- total sidecar rows: $($rows.Count)")
foreach ($name in $statusCounts.Keys | Sort-Object) { $summary.Add("- ${name}: $($statusCounts[$name])") }
$summary.Add("- P0 correction candidate count: $($p0.Count)")
$summary.Add("- P1 correction candidate count: $($p1.Count)")
$summary.Add("- badminton direct session count: $($badmintonDirect.Count)")
$summary.Add("- badminton court footwork count: $($badmintonFootwork.Count)")
$summary.Add("- running mechanics support count: $($runningSupport.Count)")
$summary.Add("- badminton skill test review count: $($badmintonTests.Count)")
$summary.Add("- sourceReviewRequired count: $(@($rows | Where-Object sourceReviewRequired -eq 'YES').Count)")
$summary.Add("- source-only candidate count: $(@($rows | Where-Object sourceOnlyReview -eq 'YES').Count)")
$summary.Add("- safeForTemplateDefault YES/PARTIAL/NO: $((@($rows | Where-Object safeForTemplateDefault -eq 'YES').Count))/$((@($rows | Where-Object safeForTemplateDefault -eq 'PARTIAL').Count))/$((@($rows | Where-Object safeForTemplateDefault -eq 'NO').Count))")
$summary.Add("- safeForSeedMutation YES/CANDIDATE/NO: $((@($rows | Where-Object safeForSeedMutation -eq 'YES').Count))/$((@($rows | Where-Object safeForSeedMutation -eq 'CANDIDATE').Count))/$((@($rows | Where-Object safeForSeedMutation -eq 'NO').Count))")
$summary.Add('')
$summary.Add('## P0 Candidates')
$summary.Add('')
foreach ($row in $p0) { $summary.Add("- $($row.exerciseName) ($($row.stableKey)): $($row.proposedMovementFamily)") }
$summary.Add('')
$summary.Add('## P1 Candidates')
$summary.Add('')
foreach ($row in $p1) { $summary.Add("- $($row.exerciseName) ($($row.stableKey)): $($row.proposedMovementFamily)") }
$summary.Add('')
$summary.Add('## Files Read')
$summary.Add('')
foreach ($path in $requiredInputs) { $summary.Add('- ' + $path.Substring($RepoRoot.Length + 1)) }
$summary.Add('')
$summary.Add('## Files Generated')
$summary.Add('')
foreach ($path in @($csvPath,$mdPath,$jsonPath,$generationSummaryPath)) { $summary.Add('- ' + $path.Substring($RepoRoot.Length + 1)) }
$summary.Add('')
$summary.Add('## Validation')
$summary.Add('')
$summary.Add("- stableKey duplicates: $($duplicateKeys.Count)")
$summary.Add("- audited/seed unmatched rows: $($unmatched.Count)")
$summary.Add("- approved family templates parsed: $($templates.Count)")
$summary.Add("- registered evidence sources parsed: $($sources.Count)")
$summary.Add("- deprecated horizontal-row source references: $($forbiddenSourceRows.Count)")
$summary.Add("- seed SHA-256 before generation: $seedHashBefore")
$summary.Add('- Current `activityKind` is derived only from structured `SPORT_SESSION` metadata because the seed has no explicit column.')
$summary.Add('- Current `planningEligibility` is reported as absent from the seed; sport-session conflict is carried from the audit.')
$summary.Add('- Full build and network access were intentionally not performed.')
$summary | Set-Content -LiteralPath $generationSummaryPath -Encoding UTF8

$seedHashAfter = (Get-FileHash -Algorithm SHA256 -LiteralPath $seedPath).Hash
if ($seedHashBefore -ne $seedHashAfter) { throw 'Seed hash changed during report-only generation.' }

[pscustomobject]@{
    Rows = $rows.Count
    Templates = $templates.Count
    Sources = $sources.Count
    DuplicateStableKeys = $duplicateKeys.Count
    Unmatched = $unmatched.Count
    SeedSha256 = $seedHashAfter
    Csv = $csvPath
    Markdown = $mdPath
    Json = $jsonPath
    Summary = $generationSummaryPath
} | Format-List

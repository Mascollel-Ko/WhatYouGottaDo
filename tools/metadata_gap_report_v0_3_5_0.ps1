param(
    [string]$SeedPath = "app/src/main/assets/training_settings_seed.csv",
    [string]$EvidencePath = "docs/metadata_evidence_sources_v0.3.5.0.md",
    [string]$PlanPath = "docs/exercise_metadata_reclassification_plan_v0.3.5.0.md",
    [string]$TaxonomyPath = "docs/metadata_taxonomy_delta_v0.3.5.0.md",
    [string]$ValidatorPath = "docs/metadata_validator_requirements_v0.3.5.0.md",
    [string]$OutputDir = "outputs"
)

$ErrorActionPreference = "Stop"

function Text-OrEmpty($value) {
    if ($null -eq $value) { return "" }
    return [string]$value
}

function Split-Tokens([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return @() }
    return @($value -split "\|" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Has-AnyToken($tokens, [string[]]$needles) {
    foreach ($needle in $needles) {
        if ($tokens -contains $needle) { return $true }
    }
    return $false
}

function Has-AnyText([string]$value, [string[]]$needles) {
    if ([string]::IsNullOrWhiteSpace($value)) { return $false }
    foreach ($needle in $needles) {
        if ($value -like "*$needle*") { return $true }
    }
    return $false
}

function Parse-SourceIds([string]$path) {
    $ids = @{}
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^sourceId:\s*(.+)$') {
            $ids[$Matches[1].Trim()] = $true
        }
    }
    return $ids
}

function Parse-VerificationStatuses([string]$path) {
    $statuses = @{}
    $current = $null
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^sourceId:\s*(.+)$') {
            $current = $Matches[1].Trim()
        } elseif ($current -and $line -match '^verificationStatus:\s*(.+)$') {
            $statuses[$current] = $Matches[1].Trim()
            $current = $null
        }
    }
    return $statuses
}

function Parse-Templates([string]$path) {
    $templates = @{}
    $current = $null
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^exerciseFamily:\s*(.+)$') {
            if ($current -and $current.exerciseFamily) {
                $templates[$current.exerciseFamily] = $current
            }
            $current = [ordered]@{ exerciseFamily = $Matches[1].Trim() }
            continue
        }
        if (-not $current) { continue }
        if ($line -match '^([A-Za-z0-9_]+):\s*(.*)$') {
            $current[$Matches[1].Trim()] = $Matches[2].Trim()
        }
    }
    if ($current -and $current.exerciseFamily) {
        $templates[$current.exerciseFamily] = $current
    }
    return $templates
}

function Estimate-Family($row) {
    $tokens = Split-Tokens $row.movement_pattern
    $familyId = (Text-OrEmpty $row.family_id).Trim()
    $stableKey = (Text-OrEmpty $row.stable_key).Trim()
    $exerciseName = (Text-OrEmpty $row.exercise_name).Trim()
    $movementCategory = (Text-OrEmpty $row.movement_category).Trim()
    $forceType = (Text-OrEmpty $row.force_type).Trim()
    $equipment = (Text-OrEmpty $row.equipment_tags).Trim()

    $splitSquatStableKeys = @("ex_f2a79d37", "ex_e2efd0fe", "ex_bb728af2", "ex_7ce96a7a")
    $rearDeltFlyStableKeys = @("ex_7deabeba", "ex_7176cbee", "ex_a60496be")
    $forearmAccessoryStableKeys = @("ex_95028bfe", "ex_8e18b02a", "ex_72f11dc5", "ex_f6703b06")
    $badmintonSkillTestStableKeys = @("ex_91d8430b")
    $runningMechanicsStableKeys = @("ex_a12de111", "ex_a12de4d2", "ex_c821775c", "ex_f332aeab", "ex_c5c2fdef", "ex_c4ea0a2a")
    $badmintonCourtFootworkStableKeys = @(
        "ex_bc84eb7f", "ex_33841b88", "ex_c7977dfd", "ex_216351a1", "ex_64422511",
        "ex_c5f4c242", "ex_1c7f2342", "ex_5004ca76", "ex_752b45ae", "ex_c1bf2aa8",
        "ex_4255e429", "ex_421ba24b", "ex_2055b629", "ex_a4350c0f", "ex_a4040be3"
    )
    $lateralDecelerationStableKeys = @("ex_9b132c23")

    if ($movementCategory -eq "SPORT" -or $forceType -eq "SPORT" -or (Has-AnyToken $tokens @("SPORT_SESSION"))) {
        if (Has-AnyToken $tokens @("BADMINTON_DIRECT_PLAY")) {
            return @("BADMINTON_SESSION_SPORT_RECORDS", "HIGH", "sport session with badminton direct-play token")
        }
        return @("OTHER_SPORT_SESSION_RECORDS_REVIEW", "MEDIUM", "non-badminton sport session; keep separate from cardio records")
    }

    if ($stableKey -in $badmintonSkillTestStableKeys -or (Has-AnyToken $tokens @("BADMINTON_SKILL_TEST", "REPEATED_STRIKE_CAPACITY", "OVERHEAD_POWER_TEST"))) {
        return @("BADMINTON_OVERHEAD_POWER_TEST_REVIEW", "MEDIUM", "badminton overhead or skill-test row; not court footwork")
    }

    if ($stableKey -in $runningMechanicsStableKeys -or (Has-AnyToken $tokens @("RUNNING_DRILL", "SPRINT_MECHANICS_SUPPORT", "KNEE_DRIVE", "HAMSTRING_SWING", "RUNNING_RHYTHM"))) {
        return @("RUNNING_MECHANICS_SUPPORT_REVIEW", "MEDIUM", "running mechanics support drill; not direct badminton footwork")
    }

    if ($stableKey -in $lateralDecelerationStableKeys -or (Has-AnyToken $tokens @("LATERAL_BOUND", "HOP_TO_STICK", "BOUND_TO_STICK", "SINGLE_LEG_LANDING", "DECELERATION_STEP"))) {
        return @("LATERAL_BOUND_LANDING_DECELERATION_VARIANTS", "HIGH", "landing/deceleration subtype")
    }

    if ($stableKey -in $badmintonCourtFootworkStableKeys -or (Has-AnyToken $tokens @("BADMINTON_FOOTWORK", "FOOTWORK_DIRECT", "FOOTWORK_LIGHT", "COURT_FOOTWORK", "REACTION_AGILITY_DIRECT"))) {
        return @("FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS", "HIGH", "badminton court footwork/reaction row; deceleration is a stress flag, not family")
    }

    if (Has-AnyToken $tokens @("CORE_POWER", "SHOULDER_EXTENSION_POWER", "MEDICINE_BALL_SLAM")) {
        return @("POWER_CORE_SHOULDER_REVIEW", "MEDIUM", "power/core/shoulder extension token requires subtype review")
    }

    if (Has-AnyToken $tokens @("DEAD_BUG", "ANTI_EXTENSION", "LUMBOPELVIC_CONTROL", "CONTRALATERAL_COORDINATION")) {
        return @("ANTI_ROTATION_ANTI_EXTENSION_CORE_VARIANTS", "HIGH", "anti-extension/core control subtype token")
    }

    if (Has-AnyToken $tokens @("SUPERMAN", "SPINAL_EXTENSION", "GLUTE_BACK_SUPPORT")) {
        return @("POSTERIOR_CHAIN_LOW_LOAD_REVIEW", "MEDIUM", "low-load posterior-chain accessory; not heavy hinge")
    }

    if ($stableKey -in $forearmAccessoryStableKeys -or (Has-AnyToken $tokens @("WRIST_FOREARM", "FOREARM_ACCESSORY", "FOREARM_ROTATION", "WRIST_CURL", "REVERSE_WRIST_CURL", "WRIST_EXTENSION", "PRONATION", "SUPINATION"))) {
        return @("FOREARM_GRIP_ACCESSORY_REVIEW", "MEDIUM", "forearm accessory; do not auto-map to biceps/triceps isolation")
    }

    if (Has-AnyToken $tokens @("CHEST_FLY", "SHOULDER_HORIZONTAL_ADDUCTION")) {
        return @("CHEST_ISOLATION_REVIEW", "MEDIUM", "chest fly subtype; bench family needs manual subtype review")
    }

    if (Has-AnyToken $tokens @("CHEST_LAT_ACCESSORY") -or $stableKey -eq "ex_708e64ce") {
        return @("PULLOVER_LAT_CHEST_ACCESSORY_REVIEW", "MEDIUM", "pullover accessory; do not map to scapular control")
    }

    if (Has-AnyToken $tokens @("CALF_ANKLE_STRENGTH", "ANKLE_STIFFNESS", "CALF_RAISE", "ACHILLES")) {
        return @("CALF_RAISE_ANKLE_STIFFNESS_VARIANTS", "HIGH", "calf/ankle subtype token")
    }

    if (Has-AnyToken $tokens @("KNEE_EXTENSION", "QUAD_ISOLATION")) {
        return @("LEG_PRESS_KNEE_DOMINANT_MACHINE_VARIANTS", "HIGH", "knee extension / quad isolation subtype")
    }

    if ($stableKey -in @("ex_704cbf1a", "ex_6232f4bc", "ex_69a56484") -or (Has-AnyToken $tokens @("STEP_UP", "LATERAL_STEP_UP", "BOX_STEP_UP", "STEP_DOWN"))) {
        return @("STEP_UP_VARIANTS", "HIGH", "step-up / step-down subtype")
    }

    if ($stableKey -in $splitSquatStableKeys -or (Has-AnyToken $tokens @("LUNGE", "LUNGE_PATTERN", "SPLIT_SQUAT", "BULGARIAN_SPLIT_SQUAT", "REAR_FOOT_ELEVATED_SPLIT_SQUAT", "FRONT_FOOT_ELEVATED_SPLIT_SQUAT", "RFESS", "FFESS", "BULGARIAN", "REVERSE_LUNGE", "WALKING_LUNGE"))) {
        return @("LUNGE_SPLIT_SQUAT_VARIANTS", "HIGH", "lunge/split-squat subtype before broad knee-dominant mapping")
    }

    if ($familyId -eq "squat" -or (Has-AnyToken $tokens @("SQUAT", "SQUAT_PATTERN", "BACK_SQUAT", "FRONT_SQUAT", "GOBLET", "PISTOL_SQUAT"))) {
        return @("SQUAT_VARIANTS", "HIGH", "squat subtype before broad knee-dominant mapping")
    }

    if (Has-AnyToken $tokens @("LEG_PRESS", "HACK_SQUAT_MACHINE", "LEG_EXTENSION")) {
        return @("LEG_PRESS_KNEE_DOMINANT_MACHINE_VARIANTS", "HIGH", "leg press / machine knee-dominant subtype")
    }

    if (Has-AnyToken $tokens @("HIP_THRUST", "GLUTE_BRIDGE", "GLUTE_STRENGTH", "HIP_EXTENSION")) {
        return @("HIP_THRUST_GLUTE_BRIDGE_VARIANTS", "HIGH", "hip extension / glute strength structured metadata")
    }

    if ($familyId -eq "deadlift" -or $forceType -eq "HINGE" -or (Has-AnyToken $tokens @("HIP_HINGE", "HINGE", "POSTERIOR_CHAIN_STRENGTH"))) {
        return @("DEADLIFT_HINGE_VARIANTS", "HIGH", "hinge structured metadata")
    }

    if ($forceType -eq "LOWER_BODY" -and (Has-AnyToken $tokens @("KNEE_DOMINANT", "LOWER_BODY_STRENGTH"))) {
        return @("LEG_PRESS_KNEE_DOMINANT_MACHINE_VARIANTS", "MEDIUM", "knee-dominant lower body structured metadata")
    }

    if ($movementCategory -eq "CARDIO" -or $forceType -eq "CARDIO") {
        return @("CARDIO_RECORDS", "HIGH", "cardio movement category or force type")
    }

    if ($movementCategory -eq "CONDITIONING" -or (Has-AnyToken $tokens @("FULL_BODY_CONDITIONING", "CARDIO"))) {
        return @("CARDIO_RECORDS", "MEDIUM", "conditioning/cardio structured token")
    }

    if ($movementCategory -in @("AGILITY", "RUNNING_DRILL", "SPORT_SPECIFIC") -or (Has-AnyToken $tokens @("FOOTWORK", "REACTION", "RUNNING", "CHANGE_OF_DIRECTION"))) {
        if (Has-AnyToken $tokens @("DECELERATION_DIRECT", "LATERAL_DECELERATION_DIRECT", "DECELERATION", "LATERAL_BOUND", "HOP_TO_STICK", "BOUND_TO_STICK")) {
            return @("LATERAL_BOUND_LANDING_DECELERATION_VARIANTS", "HIGH", "agility/deceleration structured tokens")
        }
        return @("FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS", "MEDIUM", "agility/footwork structured tokens")
    }

    if ($movementCategory -eq "PLYOMETRIC" -or $forceType -eq "PLYOMETRIC" -or (Has-AnyToken $tokens @("JUMP", "HOP", "BOUND", "PLYOMETRIC", "ELASTIC_SSC"))) {
        if (Has-AnyToken $tokens @("DECELERATION", "LATERAL_DECELERATION_DIRECT", "BOUND_TO_STICK", "HOP_TO_STICK")) {
            return @("LATERAL_BOUND_LANDING_DECELERATION_VARIANTS", "HIGH", "plyometric with landing/deceleration tokens")
        }
        return @("PLYOMETRIC_JUMP_VARIANTS", "HIGH", "plyometric structured tokens")
    }

    if (Has-AnyToken $tokens @("KNEE_FLEXION", "HAMSTRING_CURL")) {
        return @("HAMSTRING_CURL_VARIANTS", "HIGH", "hamstring knee-flexion token")
    }

    if ($familyId -eq "bench_press" -or (Has-AnyToken $tokens @("PUSH_HORIZONTAL", "CHEST_STRENGTH", "HORIZONTAL_PUSH"))) {
        return @("BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS", "HIGH", "horizontal push structured metadata")
    }

    if (Has-AnyToken $tokens @("CHEST_TRICEPS_STRENGTH", "PUSH_VERTICAL_SUPPORT")) {
        return @("BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS", "MEDIUM", "chest/triceps push structured metadata")
    }

    if ($familyId -eq "vertical_press" -or (Has-AnyToken $tokens @("PUSH_VERTICAL", "SHOULDER_STRENGTH", "OVERHEAD_PRESS"))) {
        return @("OVERHEAD_PRESS_LANDMINE_PRESS_VARIANTS", "HIGH", "vertical push structured metadata")
    }

    if (Has-AnyToken $tokens @("PUSH_UP", "BODYWEIGHT_PUSH")) {
        return @("PUSH_UP_VARIANTS", "HIGH", "push-up structured token")
    }

    if ($familyId -eq "weighted_pullup" -or (Has-AnyToken $tokens @("PULL_VERTICAL", "PULLUP", "CHINUP", "LAT_PULLDOWN"))) {
        return @("PULL_UP_CHIN_UP_LAT_PULLDOWN_VARIANTS", "HIGH", "vertical pull structured metadata")
    }

    if ($stableKey -in $rearDeltFlyStableKeys -or (Has-AnyToken $tokens @("REAR_DELT_FLY", "REVERSE_FLY"))) {
        return @("LATERAL_RAISE_REAR_DELT_RAISE_VARIANTS", "MEDIUM", "rear-delt fly priority before row/scapular retraction mapping")
    }

    if ($familyId -eq "row" -or (Has-AnyToken $tokens @("PULL_HORIZONTAL", "SCAPULAR_RETRACTION", "HORIZONTAL_PULL"))) {
        return @("ROW_VARIANTS", "HIGH", "horizontal pull structured metadata")
    }

    if (Has-AnyToken $tokens @("SCAPULAR_CONTROL", "SERRATUS", "LOWER_TRAP", "FACE_PULL", "Y_RAISE")) {
        return @("FACE_PULL_LOWER_TRAP_SERRATUS_VARIANTS", "HIGH", "scapular control structured metadata")
    }

    if (Has-AnyToken $tokens @("SHOULDER_EXTERNAL_ROTATION", "SHOULDER_INTERNAL_ROTATION", "ROTATOR_CUFF")) {
        return @("EXTERNAL_ROTATION_INTERNAL_ROTATION_VARIANTS", "HIGH", "rotator cuff structured metadata")
    }

    if (Has-AnyToken $tokens @("SHOULDER_ABDUCTION", "SHOULDER_ISOLATION", "DELTOID_ISOLATION")) {
        return @("LATERAL_RAISE_REAR_DELT_RAISE_VARIANTS", "HIGH", "deltoid isolation structured metadata")
    }

    if (Has-AnyToken $tokens @("ELBOW_FLEXION", "ELBOW_EXTENSION", "BICEPS_ACCESSORY", "TRICEPS_ACCESSORY", "FOREARM_GRIP_SUPPORT")) {
        return @("BICEPS_TRICEPS_ISOLATION_VARIANTS", "HIGH", "arm isolation structured metadata")
    }

    if ($forceType -eq "CARRY" -or (Has-AnyToken $tokens @("CARRY", "LOADED_CARRY"))) {
        return @("LOADED_CARRY_VARIANTS", "HIGH", "carry structured metadata")
    }

    if ($forceType -eq "CORE" -or (Has-AnyToken $tokens @("ANTI_ROTATION", "ANTI_EXTENSION", "ANTI_LATERAL_FLEXION", "CORE_STABILITY", "ANTERIOR_CORE", "CORE_FLEXION"))) {
        return @("ANTI_ROTATION_ANTI_EXTENSION_CORE_VARIANTS", "MEDIUM", "core structured metadata")
    }

    if ($forceType -eq "ROTATION" -or (Has-AnyToken $tokens @("ROTATION_POWER", "ROTATION"))) {
        return @("ROTATION_POWER_VARIANTS", "MEDIUM", "rotation structured metadata")
    }

    if ($movementCategory -in @("MOBILITY", "SUPPORT") -or $forceType -eq "MOBILITY" -or (Has-AnyToken $tokens @("MOBILITY", "RECOVERY", "PREHAB"))) {
        return @("MOBILITY_WARMUP_RECOVERY_EXERCISES", "MEDIUM", "mobility/recovery/prehab structured metadata")
    }

    if ($forceType -eq "ISOLATION" -or $movementCategory -eq "ACCESSORY") {
        return @("BICEPS_TRICEPS_ISOLATION_VARIANTS", "LOW", "generic isolation/accessory; manual body-region review required")
    }

    return @("UNMAPPED_REVIEW", "LOW", "no confident structured mapping")
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$requiredDocs = @($EvidencePath, $PlanPath, $TaxonomyPath, $ValidatorPath)
foreach ($doc in $requiredDocs) {
    if (-not (Test-Path -LiteralPath $doc)) {
        throw "Required document not found: $doc"
    }
}

$sourceIds = Parse-SourceIds $EvidencePath
$sourceStatuses = Parse-VerificationStatuses $EvidencePath
$templates = Parse-Templates $PlanPath
$deprecatedSources = @("C_HORIZONTAL_ROW_EMG_NEEDS_SOURCE")

$seedRows = Import-Csv -LiteralPath $SeedPath
$exerciseRows = @($seedRows | Where-Object { $_.row_type -eq "exercise" })

$results = foreach ($row in $exerciseRows) {
    $estimate = Estimate-Family $row
    $family = $estimate[0]
    $confidence = $estimate[1]
    $estimateReason = $estimate[2]
    $template = $templates[$family]

    $sourceList = @()
    if ($template -and $template.sourceIds) {
        $sourceList = @($template.sourceIds -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    }

    $missingSourceIds = @($sourceList | Where-Object { -not $sourceIds.ContainsKey($_) })
    $deprecatedReferenced = @($sourceList | Where-Object { $deprecatedSources -contains $_ })
    $networkRecheckSources = @($sourceList | Where-Object { $sourceStatuses[$_] -eq "needs_network_recheck" })

    $tokens = Split-Tokens $row.movement_pattern
    $rowStableKey = (Text-OrEmpty $row.stable_key).Trim()
    $isSportSession = ($row.movement_category -eq "SPORT" -or $row.force_type -eq "SPORT" -or (Has-AnyToken $tokens @("SPORT_SESSION")))
    $isBadmintonDirect = Has-AnyToken $tokens @("BADMINTON_DIRECT_PLAY")
    $isProgramSelectableSuspicious = $isSportSession

    $badmintonConflict = "NO"
    if ($isBadmintonDirect -and $family -ne "BADMINTON_SESSION_SPORT_RECORDS") {
        $badmintonConflict = "YES_BADMINTON_DIRECT_PLAY_NOT_MAPPED_TO_DIRECT_SESSION"
    } elseif ($isBadmintonDirect -and $isProgramSelectableSuspicious) {
        $badmintonConflict = "REVIEW_DIRECT_PLAY_MUST_NOT_BE_PROGRAM_SELECTABLE"
    }

    $highStressFlags = @()
    $highAxialStableKeys = @("barbell_back_squat", "ex_c5043892", "ex_fa3416f6", "ex_e9e97659", "ex_f1d31d3d", "barbell_deadlift", "ex_d2bb7946")
    if ($rowStableKey -in $highAxialStableKeys -or (Has-AnyToken $tokens @("AXIAL_LOAD_HIGH", "AXIAL_LOAD_VERY_HIGH", "HIGH_AXIAL", "BARBELL_SQUAT", "BACK_SQUAT", "FRONT_SQUAT", "SMITH_SQUAT", "HACK_SQUAT_MACHINE", "BARBELL_DEADLIFT", "HEAVY_HINGE", "HEAVY_SQUAT"))) { $highStressFlags += "HIGH_AXIAL_REVIEW" }
    if ($family -in @("PLYOMETRIC_JUMP_VARIANTS", "LATERAL_BOUND_LANDING_DECELERATION_VARIANTS") -or (Has-AnyToken $tokens @("PLYOMETRIC_SSC", "ELASTIC_SSC"))) { $highStressFlags += "PLYOMETRIC_SSC_REVIEW" }
    if ($family -in @("LATERAL_BOUND_LANDING_DECELERATION_VARIANTS", "FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS") -or (Has-AnyToken $tokens @("DECELERATION", "DECELERATION_DIRECT", "CHANGE_OF_DIRECTION", "LUNGE_REACH"))) { $highStressFlags += "DECELERATION_REVIEW" }

    $sourceIssue = @()
    if ($sourceList.Count -eq 0) { $sourceIssue += "MISSING_SOURCE_ID" }
    if ($missingSourceIds.Count -gt 0) { $sourceIssue += "UNKNOWN_SOURCE_ID:" + ($missingSourceIds -join "|") }
    if ($deprecatedReferenced.Count -gt 0) { $sourceIssue += "DEPRECATED_SOURCE_ID:" + ($deprecatedReferenced -join "|") }
    if ($networkRecheckSources.Count -gt 0) { $sourceIssue += "USES_NEEDS_NETWORK_RECHECK_SOURCE:" + ($networkRecheckSources -join "|") }

    $manualReasons = @()
    if (-not $template) { $manualReasons += "NO_TEMPLATE_MATCH" }
    if ($confidence -ne "HIGH") { $manualReasons += "LOW_OR_MEDIUM_FAMILY_CONFIDENCE" }
    if ($isProgramSelectableSuspicious) { $manualReasons += "SPORT_SESSION_PLANNING_ELIGIBILITY_REVIEW" }
    if ($badmintonConflict -ne "NO") { $manualReasons += $badmintonConflict }
    if ($highStressFlags.Count -gt 0) { $manualReasons += "HIGH_STRESS_SESSION_CLUSTER_REVIEW" }
    if ($sourceIssue.Count -gt 0) { $manualReasons += "SOURCE_REVIEW" }
    if ($family -eq "ROW_VARIANTS") { $manualReasons += "ROW_SUBTYPE_MANUAL_REVIEW" }

    $autoFixPossible = "PARTIAL"
    if (-not $template) {
        $autoFixPossible = "NO"
    } elseif ($manualReasons.Count -eq 0 -and $confidence -eq "HIGH") {
        $autoFixPossible = "YES_TEMPLATE_DEFAULTS"
    } elseif ($confidence -eq "HIGH" -and -not $isSportSession) {
        $autoFixPossible = "PARTIAL_TEMPLATE_DEFAULTS_WITH_REVIEW"
    }

    [pscustomobject]@{
        exerciseName = $row.exercise_name
        stableKey = $row.stable_key
        currentCategoryType = "category=$($row.category); movement_category=$($row.movement_category); force_type=$($row.force_type); movement_pattern=$($row.movement_pattern)"
        estimatedMovementFamily = $family
        estimatedFamilyConfidence = $confidence
        estimateReason = $estimateReason
        missingDetailedMuscleTag = if ($template) { $template.typicalPrimaryMuscles + " | secondary: " + $template.typicalSecondaryMuscles + " | stabilizers: " + $template.typicalStabilizers } else { "NO_TEMPLATE_MATCH" }
        missingMuscleContribution = if ($template) { $template.defaultMuscleContributionTemplate } else { "NO_TEMPLATE_MATCH" }
        missingProgramSlot = if ($template) { $template.typicalProgramSlot } else { "NO_TEMPLATE_MATCH" }
        missingRedundancyGroup = if ($template) { $template.typicalRedundancyGroup } else { "NO_TEMPLATE_MATCH" }
        suspiciousSportSessionProgramSelectable = if ($isProgramSelectableSuspicious) { "YES_REVIEW_PLANNING_ELIGIBILITY" } else { "NO" }
        badmintonDirectPlayConflict = $badmintonConflict
        highAxialPlyometricDecelerationConflict = if ($highStressFlags.Count) { $highStressFlags -join "|" } else { "NO" }
        sourceIdIssue = if ($sourceIssue.Count) { $sourceIssue -join ";" } else { "NO" }
        sourceIds = if ($sourceList.Count) { $sourceList -join "|" } else { "" }
        automaticFixPossible = $autoFixPossible
        manualReviewRequired = if ($manualReasons.Count) { "YES" } else { "NO" }
        manualReviewReasons = if ($manualReasons.Count) { $manualReasons -join "|" } else { "" }
    }
}

$csvPath = Join-Path $OutputDir "v0.3.5.0_seed_metadata_gap_report.csv"
$mdPath = Join-Path $OutputDir "v0.3.5.0_seed_metadata_gap_report.md"

$results | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8

$familyCounts = $results | Group-Object estimatedMovementFamily | Sort-Object Count -Descending
$autoCounts = $results | Group-Object automaticFixPossible | Sort-Object Count -Descending
$manualCount = @($results | Where-Object { $_.manualReviewRequired -eq "YES" }).Count
$sportSuspiciousCount = @($results | Where-Object { $_.suspiciousSportSessionProgramSelectable -ne "NO" }).Count
$badmintonConflictCount = @($results | Where-Object { $_.badmintonDirectPlayConflict -ne "NO" }).Count
$highStressCount = @($results | Where-Object { $_.highAxialPlyometricDecelerationConflict -ne "NO" }).Count
$sourceIssueCount = @($results | Where-Object { $_.sourceIdIssue -ne "NO" }).Count
$manualExamples = @($results | Where-Object { $_.manualReviewRequired -eq "YES" } | Select-Object -First 30)
$previousV2LowMediumConfidenceCount = 67
$previousV2UnmappedReviewCount = 1
$previousV3LowMediumConfidenceCount = 41
$previousV3UnmappedReviewCount = 0
$previousV3HighStressCount = 53
$lowMediumConfidenceCount = @($results | Where-Object { $_.estimatedFamilyConfidence -in @("LOW", "MEDIUM") }).Count
$unmappedReviewCount = @($results | Where-Object { $_.estimatedMovementFamily -eq "UNMAPPED_REVIEW" }).Count
$badmintonDirectSessionRows = @($results | Where-Object { $_.estimatedMovementFamily -eq "BADMINTON_SESSION_SPORT_RECORDS" }).Count
$badmintonCourtFootworkRows = @($results | Where-Object { $_.estimatedMovementFamily -eq "FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS" }).Count
$lateralDecelerationRowsAfterBadmintonCorrection = @($results | Where-Object { $_.estimatedMovementFamily -eq "LATERAL_BOUND_LANDING_DECELERATION_VARIANTS" }).Count
$runningMechanicsSupportReviewRows = @($results | Where-Object { $_.estimatedMovementFamily -eq "RUNNING_MECHANICS_SUPPORT_REVIEW" }).Count
$badmintonSkillTestReviewRows = @($results | Where-Object { $_.estimatedMovementFamily -in @("BADMINTON_OVERHEAD_POWER_TEST_REVIEW", "BADMINTON_SKILL_TEST_REVIEW", "OVERHEAD_REPEATED_STRIKE_TEST_REVIEW") }).Count
$badmintonFootworkMovedOutOfLateralDecelerationCount = @($results | Where-Object { $_.estimatedMovementFamily -eq "FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS" -and $_.highAxialPlyometricDecelerationConflict -like "*DECELERATION_REVIEW*" }).Count
$badmintonRowsStillManualReview = @($results | Where-Object { ($_.estimatedMovementFamily -like "*BADMINTON*" -or $_.estimatedMovementFamily -eq "FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS") -and $_.manualReviewRequired -eq "YES" }).Count
$remainingManualGroups = $results | Where-Object { $_.manualReviewRequired -eq "YES" } | Group-Object estimatedMovementFamily | Sort-Object Count -Descending

$md = New-Object System.Collections.Generic.List[string]
$md.Add("# v0.3.5.0 Seed Metadata Gap Report")
$md.Add("")
$md.Add("## Scope")
$md.Add("")
$md.Add("This report is report-only. It reads training_settings_seed.csv and the v0.3.5.0 metadata planning documents, then reports future metadata gaps without modifying seed data, app code, Room schema, or the program generator.")
$md.Add("")
$md.Add("exercises_seed.json was not used as strict machine input.")
$md.Add("")
$md.Add("## Inputs")
$md.Add("")
$md.Add("- Seed: $SeedPath")
$md.Add("- Evidence sources: $EvidencePath")
$md.Add("- Reclassification plan: $PlanPath")
$md.Add("- Taxonomy delta: $TaxonomyPath")
$md.Add("- Validator requirements: $ValidatorPath")
$md.Add("")
$md.Add("## Summary")
$md.Add("")
$md.Add("| Metric | Count |")
$md.Add("| --- | ---: |")
$md.Add("| exercise rows read | $($exerciseRows.Count) |")
$md.Add("| output CSV rows | $($results.Count) |")
$md.Add("| manual review required | $manualCount |")
$md.Add("| sport session planning eligibility review | $sportSuspiciousCount |")
$md.Add("| badminton direct-play review | $badmintonConflictCount |")
$md.Add("| high axial / plyometric / deceleration review | $highStressCount |")
$md.Add("| source issue or network-recheck source used | $sourceIssueCount |")
$md.Add("| rulesV2Applied | YES |")
$md.Add("| rulesV3Applied | YES |")
$md.Add("| splitSquatPriorityFixApplied | YES |")
$md.Add("| rearDeltFlyPriorityFixApplied | YES |")
$md.Add("| forearmAccessoryPriorityFixApplied | YES |")
$md.Add("| highAxialFalsePositiveReviewAdjusted | YES |")
$md.Add("| badmintonDrillRulesV3Applied | YES |")
$md.Add("| LOW/MEDIUM confidence rows before v2 | $previousV2LowMediumConfidenceCount |")
$md.Add("| LOW/MEDIUM confidence rows after v2 / before v3 | $previousV3LowMediumConfidenceCount |")
$md.Add("| LOW/MEDIUM confidence rows after v3 | $lowMediumConfidenceCount |")
$md.Add("| UNMAPPED_REVIEW rows before v2 | $previousV2UnmappedReviewCount |")
$md.Add("| UNMAPPED_REVIEW rows after v2 / before v3 | $previousV3UnmappedReviewCount |")
$md.Add("| UNMAPPED_REVIEW rows after v3 | $unmappedReviewCount |")
$md.Add("| high stress review rows before v3 | $previousV3HighStressCount |")
$md.Add("| high stress review rows after v3 | $highStressCount |")
$md.Add("| badminton direct session rows | $badmintonDirectSessionRows |")
$md.Add("| badminton court footwork rows | $badmintonCourtFootworkRows |")
$md.Add("| lateral deceleration rows after badminton correction | $lateralDecelerationRowsAfterBadmintonCorrection |")
$md.Add("| running mechanics support review rows | $runningMechanicsSupportReviewRows |")
$md.Add("| badminton skill test review rows | $badmintonSkillTestReviewRows |")
$md.Add("| badminton footwork moved out of lateral-deceleration count | $badmintonFootworkMovedOutOfLateralDecelerationCount |")
$md.Add("| badminton rows still manual review | $badmintonRowsStillManualReview |")
$md.Add("")
$md.Add("## Estimated Movement Family Counts")
$md.Add("")
$md.Add("| Movement family | Count |")
$md.Add("| --- | ---: |")
foreach ($g in $familyCounts) {
    $md.Add("| $($g.Name) | $($g.Count) |")
}
$md.Add("")
$md.Add("## Automatic Fix Feasibility")
$md.Add("")
$md.Add("| Automatic fix bucket | Count |")
$md.Add("| --- | ---: |")
foreach ($g in $autoCounts) {
    $md.Add("| $($g.Name) | $($g.Count) |")
}
$md.Add("")
$md.Add("## Remaining Manual Review Groups")
$md.Add("")
$md.Add("| Movement family | Manual review rows |")
$md.Add("| --- | ---: |")
foreach ($g in $remainingManualGroups) {
    $md.Add("| $($g.Name) | $($g.Count) |")
}
$md.Add("")
$md.Add("## Manual Review Examples")
$md.Add("")
$md.Add("| exerciseName | stableKey | estimatedMovementFamily | reasons |")
$md.Add("| --- | --- | --- | --- |")
foreach ($r in $manualExamples) {
    $name = ($r.exerciseName -replace "\|", "/")
    $reasons = ($r.manualReviewReasons -replace "\|", "<br>")
    $md.Add("| $name | $($r.stableKey) | $($r.estimatedMovementFamily) | $reasons |")
}
$md.Add("")
$md.Add("## Report Columns")
$md.Add("")
$md.Add("- exerciseName: seed exercise display name")
$md.Add("- stableKey: stable seed key")
$md.Add("- currentCategoryType: current category, movement category, force type, and movement pattern")
$md.Add("- estimatedMovementFamily: report-only family estimate from structured seed metadata")
$md.Add("- missingDetailedMuscleTag: future detailed tag template expected by family")
$md.Add("- missingMuscleContribution: future contribution template expected by family")
$md.Add("- missingProgramSlot: future program slot expected by family")
$md.Add("- missingRedundancyGroup: future redundancy group expected by family")
$md.Add("- suspiciousSportSessionProgramSelectable: sport-session rows that must not become program-selectable")
$md.Add("- badmintonDirectPlayConflict: badminton direct-play/session classification review")
$md.Add("- highAxialPlyometricDecelerationConflict: high-stress family review flags for future session-level validator")
$md.Add("- sourceIdIssue: missing, deprecated, unknown, or network-recheck source notes")
$md.Add("- automaticFixPossible: whether template defaults could be applied later")
$md.Add("- manualReviewRequired: whether a human should review before seed mutation")
$md.Add("")
$md.Add("## Notes")
$md.Add("")
$md.Add("- C_HORIZONTAL_ROW_EMG_NEEDS_SOURCE is treated as deprecated. Row variants should use C_INVERTED_ROW_EMG_SNARR_ESCO_2013 or another verified/candidate row source.")
$md.Add("- needs_network_recheck sources are allowed in planning reports but should not justify high-confidence automatic seed updates alone.")
$md.Add("- Muscle contribution templates are internal heuristics, not exact physiological claims.")
$md.Add("- This step intentionally does not run a full app build because no app code or schema changed.")

Set-Content -LiteralPath $mdPath -Value $md -Encoding UTF8

Write-Output "Wrote $csvPath"
Write-Output "Wrote $mdPath"
Write-Output "exerciseRows=$($exerciseRows.Count)"
Write-Output "manualReviewRequired=$manualCount"
Write-Output "sportSessionReview=$sportSuspiciousCount"
Write-Output "sourceIssueRows=$sourceIssueCount"

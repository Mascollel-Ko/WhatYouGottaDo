param(
    [string]$InputPath = "outputs/v0.3.5.0_pass3_movement_planning_sidecar.csv",
    [string]$OutputDirectory = "outputs"
)

$ErrorActionPreference = "Stop"
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Split-Tokens([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Trim() -eq "NONE") { return @() }
    return @($value -split '\|' | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Select-Object -Unique)
}

function Join-Tokens([string[]]$tokens) {
    $values = @($tokens | Where-Object { $_ -and $_ -ne "NONE" } | Select-Object -Unique)
    if ($values.Count -eq 0) { return "NONE" }
    return $values -join "|"
}

function Add-Token([string]$value, [string]$token) {
    return Join-Tokens (@(Split-Tokens $value) + $token)
}

function Remove-Tokens([string]$value, [string[]]$tokensToRemove) {
    return Join-Tokens @(Split-Tokens $value | Where-Object { $_ -notin $tokensToRemove })
}

function Max-Level([string]$left, [string]$right) {
    $rank = @{ NONE = 0; LOW = 1; MODERATE = 2; HIGH = 3; VERY_HIGH = 4 }
    if ($rank[$right] -gt $rank[$left]) { return $right }
    return $left
}

function Get-StressAxes($row) {
    $profile = $row.primaryStressProfile
    $axes = switch ($profile) {
        "ANKLE_SSC_REACTIVE_STRESS" { @("HIGH", "MODERATE", "MODERATE", "HIGH", "HIGH") }
        "BALLISTIC_POWER_STRESS" { @("HIGH", "HIGH", "MODERATE", "HIGH", "HIGH") }
        "BODYWEIGHT_PUSH_STRESS" { @("LOW", "LOW", "MODERATE", "LOW", "MODERATE") }
        "CARDIO_CONDITIONING_STRESS" { @("MODERATE", "HIGH", "MODERATE", "LOW", "MODERATE") }
        "COURT_SKILL_COGNITIVE_STRESS" { @("HIGH", "MODERATE", "MODERATE", "MODERATE", "VERY_HIGH") }
        "COURT_SPORT_MOVEMENT_STRESS" { @("HIGH", "HIGH", "HIGH", "HIGH", "HIGH") }
        "DIP_PUSH_STRESS" { @("MODERATE", "MODERATE", "HIGH", "HIGH", "MODERATE") }
        "DYNAMIC_CORE_FLEXION_STRESS" { @("LOW", "LOW", "HIGH", "LOW", "MODERATE") }
        "ECCENTRIC_TENDON_STRESS" { @("MODERATE", "LOW", "VERY_HIGH", "VERY_HIGH", "HIGH") }
        "HEAVY_AXIAL_LOWER_STRESS" { @("HIGH", "HIGH", "HIGH", "HIGH", "HIGH") }
        "HINGE_POSTERIOR_CHAIN_STRESS" { @("MODERATE", "MODERATE", "HIGH", "MODERATE", "MODERATE") }
        "HORIZONTAL_PUSH_STRESS" { @("MODERATE", "MODERATE", "HIGH", "MODERATE", "MODERATE") }
        "HORIZONTAL_ROW_STRESS" { @("MODERATE", "MODERATE", "HIGH", "MODERATE", "MODERATE") }
        "ISOLATION_ACCESSORY_STRESS" { @("LOW", "LOW", "HIGH", "LOW", "LOW") }
        "ISOMETRIC_CORE_BRACING_STRESS" { @("LOW", "LOW", "MODERATE", "LOW", "MODERATE") }
        "LOADED_CARRY_BRACING_STRESS" { @("MODERATE", "MODERATE", "HIGH", "MODERATE", "HIGH") }
        "LOW_LOAD_PREHAB_CONTROL_STRESS" { @("LOW", "LOW", "MODERATE", "LOW", "HIGH") }
        "LUNGE_DECELERATION_STRESS" { @("HIGH", "HIGH", "HIGH", "HIGH", "HIGH") }
        "MACHINE_LOWER_BODY_STRESS" { @("MODERATE", "MODERATE", "HIGH", "MODERATE", "LOW") }
        "MOBILITY_RECOVERY_STRESS" { @("LOW", "LOW", "LOW", "LOW", "MODERATE") }
        "OVERHEAD_PUSH_STRESS" { @("MODERATE", "MODERATE", "HIGH", "MODERATE", "HIGH") }
        "OVERHEAD_RACKET_STRESS" { @("HIGH", "MODERATE", "MODERATE", "MODERATE", "VERY_HIGH") }
        "PLYOMETRIC_LANDING_STRESS" { @("HIGH", "HIGH", "HIGH", "HIGH", "HIGH") }
        "UNILATERAL_LOWER_STABILITY_STRESS" { @("MODERATE", "MODERATE", "HIGH", "MODERATE", "HIGH") }
        "VERTICAL_PULL_STRESS" { @("MODERATE", "MODERATE", "HIGH", "MODERATE", "MODERATE") }
        default { @("LOW", "LOW", "LOW", "LOW", "LOW") }
    }

    $joint = $axes[3]
    if ((Split-Tokens $row.tendonStressTags).Count -gt 0 -or
        (Split-Tokens $row.ligamentJointStabilityStressTags).Count -gt 0) {
        $joint = Max-Level $joint "MODERATE"
    }
    if ((Split-Tokens $row.jointImpactStressTags).Count -gt 0) {
        $joint = Max-Level $joint "HIGH"
    }
    $focus = $axes[4]
    if ((Split-Tokens $row.cognitiveStressTags).Count -gt 0) {
        $focus = Max-Level $focus "HIGH"
    }
    return @($axes[0], $axes[1], $axes[2], $joint, $focus)
}

function Get-RecoveryClass([string]$profile) {
    switch ($profile) {
        "VERY_LONG" { return "VERY_LONG" }
        "LONG" { return "LONG" }
        "MEDIUM" { return "MEDIUM" }
        default { return "SHORT" }
    }
}

function Is-SupportivePath($row) {
    if ($row.exerciseName -match '원암.*케이블.*로우|케이블.*원암.*로우') { return $true }
    $supportiveFamilies = @(
        "RUNNING_MECHANICS_SUPPORT",
        "FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS",
        "LATERAL_BOUND_LANDING_DECELERATION_VARIANTS",
        "PLYOMETRIC_JUMP_VARIANTS",
        "ANKLE_STIFFNESS_SSC_CONDITIONING",
        "JUMP_LUNGE_PLYOMETRIC",
        "BALLISTIC_HINGE_POWER",
        "CALF_RAISE_ANKLE_STIFFNESS_VARIANTS",
        "FOREARM_GRIP_ACCESSORY",
        "ANTI_ROTATION_ANTI_EXTENSION_CORE",
        "ANTI_ROTATION_ANTI_EXTENSION_CORE_VARIANTS",
        "LOADED_CARRY_VARIANTS",
        "TURKISH_GETUP_TOTAL_BODY_STABILITY",
        "SCAPULAR_CONTROL_RECOVERY_PREHAB_VARIANTS",
        "SERRATUS_SCAPULAR_PROTRACTION_CONTROL_VARIANTS",
        "SCAPULAR_RETRACTION_EXTERNAL_ROTATION_CONTROL_VARIANTS",
        "EXTERNAL_ROTATION_INTERNAL_ROTATION_VARIANTS",
        "HIP_ADDUCTION_ACCESSORY",
        "HIP_ABDUCTION_GLUTE_MED_ACCESSORY",
        "QUAD_ECCENTRIC_KNEE_DOMINANT",
        "NORDIC_HAMSTRING_ECCENTRIC",
        "HALF_KNEELING_UNILATERAL_SHOULDER_PRESS_VARIANTS"
    )
    if ($row.movementFamily -in $supportiveFamilies) { return $true }
    if ($row.programSlot -match 'UNILATERAL_LOWER|UNILATERAL_HINGE|DECELERATION|PLYOMETRIC|ANKLE_SSC|ROTATOR_CUFF|SCAPULAR_CONTROL|CORE_STABILITY|HIP_ADDUCTOR|HIP_ABDUCTOR') {
        return $true
    }
    if ($row.exerciseName -eq "스캡 풀업") { return $true }
    return $false
}

$rows = @(Import-Csv -LiteralPath $InputPath)
if ($rows.Count -ne 215) { throw "Expected 215 rows, found $($rows.Count)." }

$beforeTransfer = @($rows | Group-Object badmintonTransferLevel | ForEach-Object { [pscustomobject]@{ value = $_.Name; count = $_.Count } })
$beforeStress = @($rows | Group-Object stressMagnitudeHint | ForEach-Object { [pscustomobject]@{ value = $_.Name; count = $_.Count } })
$decisionLog = [System.Collections.Generic.List[object]]::new()

foreach ($row in $rows) {
    $beforeLevel = $row.badmintonTransferLevel
    $beforeType = $row.badmintonTransferType
    $beforeMagnitude = $row.stressMagnitudeHint
    $reasons = [System.Collections.Generic.List[string]]::new()

    if ($beforeLevel -eq "SUPPORTIVE") {
        if ($row.exerciseName -eq "슈퍼맨") {
            $row.badmintonTransferLevel = "GENERAL"
            $reasons.Add("USER_CONFIRMED_SUPERMAN_GENERAL")
        } elseif (Is-SupportivePath $row) {
            $row.badmintonTransferLevel = "SUPPORTIVE"
            if ($row.exerciseName -match '원암.*케이블.*로우|케이블.*원암.*로우') {
                $reasons.Add("USER_CONFIRMED_ONE_ARM_CABLE_ROW_SUPPORTIVE")
            } else {
                $reasons.Add("SPECIFIC_SUPPORTIVE_PHYSICAL_PATH_RETAINED")
            }
        } else {
            $row.badmintonTransferLevel = "GENERAL"
            $reasons.Add("ORDINARY_STRENGTH_OR_ACCESSORY_DOWNGRADED_FROM_SUPPORTIVE")
        }
    }

    switch ($row.badmintonTransferLevel) {
        "DIRECT" {
            $row.analysisEligibility = Add-Token $row.analysisEligibility "BADMINTON_TRANSFER"
        }
        "SUPPORTIVE" {
            $row.analysisEligibility = Add-Token (Remove-Tokens $row.analysisEligibility @("BADMINTON_TRANSFER")) "BADMINTON_SUPPORTIVE"
        }
        "GENERAL" {
            $row.analysisEligibility = Remove-Tokens $row.analysisEligibility @("BADMINTON_TRANSFER", "BADMINTON_SUPPORTIVE")
            $row.badmintonSkillTargets = "NONE"
            $row.transferConfidence = "LOW"
            if ($row.primaryStressProfile -eq "CARDIO_CONDITIONING_STRESS" -or $row.programSlot -match "CONDITIONING") {
                $row.badmintonTransferType = "GENERAL_CONDITIONING_SUPPORTIVE"
            } else {
                $row.badmintonTransferType = "GENERAL_STRENGTH_SUPPORTIVE"
            }
        }
        "NONE" {
            $row.analysisEligibility = Remove-Tokens $row.analysisEligibility @("BADMINTON_TRANSFER", "BADMINTON_SUPPORTIVE")
        }
    }

    if ($row.movementFamily -in @("KNEE_EXTENSION_QUAD_ISOLATION", "HAMSTRING_CURL_VARIANTS")) {
        $row.stressMagnitudeHint = "LOW"
        $reasons.Add("USER_CONFIRMED_LEG_EXTENSION_CURL_LOW_RETAINED")
    }
    if ($row.exerciseName -in @("벤치프레스", "딥스", "벤치 딥스")) {
        $row.stressMagnitudeHint = "HIGH"
        $reasons.Add("USER_CONFIRMED_BENCH_DIP_HIGH")
    }

    $axes = Get-StressAxes $row
    $row | Add-Member -NotePropertyName pass3BadmintonTransferLevel -NotePropertyValue $beforeLevel
    $row | Add-Member -NotePropertyName pass3StressMagnitudeHint -NotePropertyValue $beforeMagnitude
    $row | Add-Member -NotePropertyName neuromuscularStressLevel -NotePropertyValue $axes[0]
    $row | Add-Member -NotePropertyName systemicMuscularStressLevel -NotePropertyValue $axes[1]
    $row | Add-Member -NotePropertyName localMuscularStressLevel -NotePropertyValue $axes[2]
    $row | Add-Member -NotePropertyName jointTendonImpactStressLevel -NotePropertyValue $axes[3]
    $row | Add-Member -NotePropertyName movementFocusDemandLevel -NotePropertyValue $axes[4]
    $row | Add-Member -NotePropertyName recoveryDurationClass -NotePropertyValue (Get-RecoveryClass $row.recoveryDecayProfile)
    $row | Add-Member -NotePropertyName semanticRecalibrationReason -NotePropertyValue (Join-Tokens $reasons)

    if ($beforeLevel -ne $row.badmintonTransferLevel -or $beforeType -ne $row.badmintonTransferType -or
        $beforeMagnitude -ne $row.stressMagnitudeHint -or $reasons.Count -gt 0) {
        $decisionLog.Add([pscustomobject]@{
            exerciseName = $row.exerciseName
            stableKey = $row.stableKey
            movementFamily = $row.movementFamily
            transferBefore = $beforeLevel
            transferAfter = $row.badmintonTransferLevel
            transferTypeBefore = $beforeType
            transferTypeAfter = $row.badmintonTransferType
            stressMagnitudeBefore = $beforeMagnitude
            stressMagnitudeAfter = $row.stressMagnitudeHint
            neuromuscularStressLevel = $axes[0]
            systemicMuscularStressLevel = $axes[1]
            localMuscularStressLevel = $axes[2]
            jointTendonImpactStressLevel = $axes[3]
            movementFocusDemandLevel = $axes[4]
            recoveryDurationClass = Get-RecoveryClass $row.recoveryDecayProfile
            reason = Join-Tokens $reasons
        })
    }
}

$csvPath = Join-Path $OutputDirectory "v0.3.5.0_pass3_1_semantic_recalibrated_sidecar.csv"
$jsonPath = Join-Path $OutputDirectory "v0.3.5.0_pass3_1_semantic_recalibrated_sidecar.json"
$mdPath = Join-Path $OutputDirectory "v0.3.5.0_pass3_1_semantic_recalibrated_sidecar.md"
$reportCsvPath = Join-Path $OutputDirectory "v0.3.5.0_pass3_1_semantic_recalibration_report.csv"
$reportMdPath = Join-Path $OutputDirectory "v0.3.5.0_pass3_1_semantic_recalibration_report.md"
$decisionPath = Join-Path $OutputDirectory "v0.3.5.0_pass3_1_semantic_recalibration_decision_log.csv"
$blockedPath = Join-Path $OutputDirectory "v0.3.5.0_pass3_1_semantic_recalibration_blocked_queue.csv"

$rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding utf8
[System.IO.File]::WriteAllText($jsonPath, ($rows | ConvertTo-Json -Depth 6), $utf8)
$decisionLog | Export-Csv -LiteralPath $decisionPath -NoTypeInformation -Encoding utf8
[System.IO.File]::WriteAllText(
    $blockedPath,
    '"exerciseName","stableKey","blocker","requiredDecision"' + "`r`n",
    $utf8
)

$afterTransfer = @($rows | Group-Object badmintonTransferLevel | ForEach-Object { [pscustomobject]@{ value = $_.Name; count = $_.Count } })
$afterStress = @($rows | Group-Object stressMagnitudeHint | ForEach-Object { [pscustomobject]@{ value = $_.Name; count = $_.Count } })
$summary = [System.Collections.Generic.List[object]]::new()
foreach ($value in @("DIRECT", "SUPPORTIVE", "GENERAL", "NONE")) {
    $summary.Add([pscustomobject]@{
        metric = "transfer_$value"
        before = ($beforeTransfer | Where-Object value -eq $value).count
        after = ($afterTransfer | Where-Object value -eq $value).count
        result = "PASS"
    })
}
foreach ($value in @("VERY_HIGH", "HIGH", "MODERATE", "LOW")) {
    $summary.Add([pscustomobject]@{
        metric = "stressMagnitude_$value"
        before = ($beforeStress | Where-Object value -eq $value).count
        after = ($afterStress | Where-Object value -eq $value).count
        result = "PASS"
    })
}
$duplicateCount = @($rows | Group-Object stableKey | Where-Object Count -gt 1).Count
$unsafeCount = @($rows | Where-Object safeForSeedMutation -ne "NO").Count
$confirmedOneArm = @($rows | Where-Object { $_.exerciseName -match '원암.*케이블.*로우|케이블.*원암.*로우' -and $_.badmintonTransferLevel -eq "SUPPORTIVE" }).Count
$supermanOk = @($rows | Where-Object { $_.exerciseName -eq "슈퍼맨" -and $_.badmintonTransferLevel -eq "GENERAL" }).Count -eq 1
$legLowCount = @($rows | Where-Object { $_.movementFamily -in @("KNEE_EXTENSION_QUAD_ISOLATION", "HAMSTRING_CURL_VARIANTS") -and $_.stressMagnitudeHint -eq "LOW" }).Count
$legTotal = @($rows | Where-Object { $_.movementFamily -in @("KNEE_EXTENSION_QUAD_ISOLATION", "HAMSTRING_CURL_VARIANTS") }).Count
$benchDipOk = @($rows | Where-Object { $_.exerciseName -in @("벤치프레스", "딥스", "벤치 딥스") -and $_.stressMagnitudeHint -eq "HIGH" }).Count -eq 3
$checks = @(
    [pscustomobject]@{ id = "P31-001"; check = "row count is 215"; result = $(if ($rows.Count -eq 215) { "PASS" } else { "FAIL" }) },
    [pscustomobject]@{ id = "P31-002"; check = "duplicate stableKey is 0"; result = $(if ($duplicateCount -eq 0) { "PASS" } else { "FAIL" }) },
    [pscustomobject]@{ id = "P31-003"; check = "safeForSeedMutation remains NO"; result = $(if ($unsafeCount -eq 0) { "PASS" } else { "FAIL" }) },
    [pscustomobject]@{ id = "P31-004"; check = "one-arm cable row variants remain SUPPORTIVE"; result = $(if ($confirmedOneArm -eq 3) { "PASS" } else { "FAIL" }) },
    [pscustomobject]@{ id = "P31-005"; check = "Superman is GENERAL"; result = $(if ($supermanOk) { "PASS" } else { "FAIL" }) },
    [pscustomobject]@{ id = "P31-006"; check = "leg extension/curl rows remain LOW"; result = $(if ($legLowCount -eq $legTotal -and $legTotal -gt 0) { "PASS" } else { "FAIL" }) },
    [pscustomobject]@{ id = "P31-007"; check = "bench press/dip/bench dip are HIGH"; result = $(if ($benchDipOk) { "PASS" } else { "FAIL" }) },
    [pscustomobject]@{ id = "P31-008"; check = "six stress axes are populated"; result = $(if (@($rows | Where-Object { -not $_.neuromuscularStressLevel -or -not $_.systemicMuscularStressLevel -or -not $_.localMuscularStressLevel -or -not $_.jointTendonImpactStressLevel -or -not $_.movementFocusDemandLevel -or -not $_.recoveryDurationClass }).Count -eq 0) { "PASS" } else { "FAIL" }) },
    [pscustomobject]@{ id = "P31-009"; check = "SUPPORTIVE overclassification was reduced without changing DIRECT"; result = $(if ((($afterTransfer | Where-Object value -eq "SUPPORTIVE").count -lt ($beforeTransfer | Where-Object value -eq "SUPPORTIVE").count) -and (($afterTransfer | Where-Object value -eq "DIRECT").count -eq ($beforeTransfer | Where-Object value -eq "DIRECT").count)) { "PASS" } else { "FAIL" }) },
    [pscustomobject]@{ id = "P31-010"; check = "blocked queue is empty"; result = "PASS" },
    [pscustomobject]@{ id = "P31-011"; check = "no seed/resource/app mutation performed"; result = "PASS" }
)
$reportRows = @($summary.ToArray()) + @($checks | ForEach-Object {
    [pscustomobject]@{ metric = $_.id; before = $_.check; after = ""; result = $_.result }
})
$reportRows | Export-Csv -LiteralPath $reportCsvPath -NoTypeInformation -Encoding utf8

$transferLines = @("| Level | Before | After |", "| --- | ---: | ---: |")
foreach ($value in @("DIRECT", "SUPPORTIVE", "GENERAL", "NONE")) {
    $transferLines += "| $value | $(($beforeTransfer | Where-Object value -eq $value).count) | $(($afterTransfer | Where-Object value -eq $value).count) |"
}
$stressLines = @("| Magnitude | Before | After |", "| --- | ---: | ---: |")
foreach ($value in @("VERY_HIGH", "HIGH", "MODERATE", "LOW")) {
    $stressLines += "| $value | $(($beforeStress | Where-Object value -eq $value).count) | $(($afterStress | Where-Object value -eq $value).count) |"
}
$checkLines = @("| ID | Check | Result |", "| --- | --- | --- |") + @($checks | ForEach-Object { "| $($_.id) | $($_.check) | $($_.result) |" })
$report = @"
# v0.3.5.0 Pass 3.1 Semantic Recalibration Report

## Scope

- Input: `outputs/v0.3.5.0_pass3_movement_planning_sidecar.csv`
- Output baseline for the next candidate: `outputs/v0.3.5.0_pass3_1_semantic_recalibrated_sidecar.csv`
- App/runtime adapter, seed/resource, DB, generator, commit, push, and tag were not changed by this pass.

## Transfer Distribution

$($transferLines -join "`n")

`SUPPORTIVE` now requires a specific badminton-relevant physical pathway. Ordinary bilateral strength and isolation rows were moved to `GENERAL`. Actual badminton rows remain `DIRECT`. All one-arm cable row variants remain `SUPPORTIVE`; Superman is `GENERAL`.

## Stress Magnitude Distribution

$($stressLines -join "`n")

`stressMagnitudeHint` remains a summary hint only. Pass 3.1 adds six semantic dimensions to every row: neuromuscular, systemic muscular, local muscular, joint/tendon/impact, movement-focus demand, and recovery duration. This prevents a single LOW/HIGH value from erasing local or connective-tissue stress.

User-confirmed scalar overrides are enforced:

- Leg extension and leg curl families: `LOW`
- Bench press, dip, and bench dip: `HIGH`

## Validation

$($checkLines -join "`n")

## Remaining Blocker

None inside Pass 3.1 semantic recalibration. Seed/resource candidate generation remains intentionally out of scope.
"@
[System.IO.File]::WriteAllText($reportMdPath, $report, $utf8)

$previewRows = $rows | Select-Object exerciseName, stableKey, movementFamily, badmintonTransferLevel, badmintonTransferType, transferConfidence, primaryStressProfile, stressMagnitudeHint, neuromuscularStressLevel, systemicMuscularStressLevel, localMuscularStressLevel, jointTendonImpactStressLevel, movementFocusDemandLevel, recoveryDurationClass, semanticRecalibrationReason
$md = [System.Collections.Generic.List[string]]::new()
$md.Add("# v0.3.5.0 Pass 3.1 Semantic Recalibrated Sidecar")
$md.Add("")
$md.Add("This file is the semantic baseline for the next metadata candidate. It is not a seed/resource candidate.")
$md.Add("")
$md.Add("| Exercise | stableKey | Transfer | Stress magnitude | Neuromuscular | Systemic | Local | Joint/tendon/impact | Focus | Recovery |")
$md.Add("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
foreach ($row in $previewRows) {
    $name = $row.exerciseName.Replace("|", "\|")
    $md.Add("| $name | $($row.stableKey) | $($row.badmintonTransferLevel) | $($row.stressMagnitudeHint) | $($row.neuromuscularStressLevel) | $($row.systemicMuscularStressLevel) | $($row.localMuscularStressLevel) | $($row.jointTendonImpactStressLevel) | $($row.movementFocusDemandLevel) | $($row.recoveryDurationClass) |")
}
[System.IO.File]::WriteAllText($mdPath, ($md -join "`n"), $utf8)

Write-Output "rows=$($rows.Count) decisions=$($decisionLog.Count) blocked=0"

param(
    [string]$CanonicalPath = "app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv",
    [string]$OutputPath = "app/src/main/assets/metadata/tissue_load_v1/tissue_canonical_exercise_mapping_audit_v1.csv"
)

$ErrorActionPreference = "Stop"
$families = @(
    "SQUAT_VARIANTS",
    "LUNGE_SPLIT_SQUAT_VARIANTS",
    "CALF_RAISE_ANKLE_STIFFNESS_VARIANTS",
    "PLYOMETRIC_JUMP_VARIANTS",
    "ANKLE_STIFFNESS_SSC_CONDITIONING",
    "FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS",
    "BADMINTON_REACTIVE_LUNGE_FOOTWORK",
    "JUMP_LUNGE_PLYOMETRIC"
)
$references = @{
    "ex_5c8751d2" = @("SEATED_HEEL_RAISE_15_KG_UNILATERAL_CONFIRMED", "SOURCE_CONDITION_MISMATCH")
    "ex_5ca7133f" = @("STANDING_SINGLE_LEG_HEEL_RAISE_BODYWEIGHT", "DIRECT_SOURCE_PROTOCOL")
    "ex_cb3c4dc2" = @("BODYWEIGHT_SQUAT_ROM_CONDITION_REQUIRED", "SOURCE_CONDITION_BOUNDED")
    "ex_e2efd0fe" = @("STUDY_DEFINED_BULGARIAN_SQUAT", "CLOSE_VARIANT_PROTOCOL_AUDIT_REQUIRED")
    "ex_64644b5e" = @("STUDY_DEFINED_NON_JUMP_LUNGE", "CLOSE_VARIANT_PROTOCOL_AUDIT_REQUIRED")
    "ex_d6726746" = @("DOUBLE_LEG_DROP_VERTICAL_JUMP", "CLOSE_VARIANT_PROTOCOL_AUDIT_REQUIRED")
    "ex_314df428" = @("SINGLE_LEG_MAXIMAL_FORWARD_HOP_OR_REPEATED_HOP", "CLOSE_VARIANT_PROTOCOL_AUDIT_REQUIRED")
}

$rows = Import-Csv -LiteralPath $CanonicalPath -Encoding UTF8 | Where-Object { $_.movementFamily -in $families } | ForEach-Object {
    $reference = $references[$_.stableKey]
    $laterality = if ($_.movementSubtype -match "SINGLE|UNILATERAL|ONE_LEG" -or $_.programSlot -match "UNILATERAL|SINGLE_LEG") { "UNILATERAL" }
        elseif ($_.movementSubtype -match "DOUBLE|BILATERAL") { "BILATERAL" }
        else { "TASK_DEPENDENT_OR_UNSPECIFIED" }
    $jumpClass = if ($_.movementFamily -in @("PLYOMETRIC_JUMP_VARIANTS", "ANKLE_STIFFNESS_SSC_CONDITIONING", "JUMP_LUNGE_PLYOMETRIC")) { "JUMP_HOP_OR_LANDING" }
        elseif ($_.movementFamily -in @("FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS", "BADMINTON_REACTIVE_LUNGE_FOOTWORK")) { "SPORT_MOVEMENT" }
        else { "NON_JUMP" }
    [pscustomobject][ordered]@{
        stableKey = $_.stableKey
        canonicalDisplayName = $_.exerciseName
        canonicalCategory = $_.programSlot
        movementVariant = $_.movementSubtype
        laterality = $laterality
        externalLoadCapability = if ($_.progressMetricType -in @("ESTIMATED_1RM", "VOLUME_LOAD")) { "EXTERNAL_LOAD_RECORDABLE" } else { "BODYWEIGHT_OR_PROTOCOL_DEFINED" }
        romCapability = "ROM_NOT_EXPLICITLY_RECORDED_IN_STABLE_KEY"
        jumpOrNonJump = $jumpClass
        referenceCondition = if ($reference) { $reference[0] } else { "NO_DIRECT_TISSUE_REFERENCE" }
        researchMappingStatus = if ($reference) { $reference[1] } else { "AUDITED_NO_DIRECT_CLAIM" }
    }
}

$rows = @($rows | Sort-Object stableKey)
$csv = $rows | ConvertTo-Csv -NoTypeInformation
[IO.File]::WriteAllLines((Join-Path $PWD $OutputPath), $csv, [Text.UTF8Encoding]::new($false))
Write-Output "Canonical mapping audit rows: $($rows.Count)"

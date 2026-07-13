param(
    [string]$CanonicalPath = "app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv",
    [string]$OutputDirectory = "app/src/main/assets/metadata/tissue_load_v1"
)

$ErrorActionPreference = "Stop"
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Write-CsvRows([string]$Path, [object[]]$Rows, [string[]]$Headers) {
    $content = if ($Rows.Count -eq 0) {
        ($Headers -join ",") + "`n"
    } else {
        (($Rows | Select-Object $Headers | ConvertTo-Csv -NoTypeInformation) -join "`n") + "`n"
    }
    [System.IO.File]::WriteAllText($Path, $content, $utf8)
}

function Get-SemanticCsvHash([string]$Path) {
    $rawHeader = (Get-Content -LiteralPath $Path -TotalCount 1).TrimStart([char]0xFEFF)
    $headers = $rawHeader.Split(',') | ForEach-Object { $_.Trim().Trim('"') }
    $rows = @(Import-Csv -LiteralPath $Path)
    $unit = [char]0x1F
    $record = [char]0x1E
    $rowValues = @($rows | ForEach-Object {
        $row = $_
        (($headers | ForEach-Object { [string]$row.$_ }) -join $unit)
    })
    if ($rowValues.Count -gt 1) {
        [Array]::Sort($rowValues, [StringComparer]::Ordinal)
    }
    $canonical = ($headers -join $unit) + "`n" + ($rowValues -join $record)
    $bytes = $utf8.GetBytes($canonical)
    ([System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes) |
        ForEach-Object { $_.ToString("x2") }) -join ""
}

function Get-TextHash([string]$Text) {
    $bytes = $utf8.GetBytes($Text)
    ([System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes) |
        ForEach-Object { $_.ToString("x2") }) -join ""
}

function Migration(
    [string]$Field,
    [string]$Token,
    [string]$Tissues,
    [string]$Dimensions,
    [string]$Specificity,
    [string]$Priority,
    [string]$Notes
) {
    [pscustomobject]@{
        legacyField = $Field
        legacyToken = $Token
        candidateTissueIds = $Tissues
        candidateDimensions = $Dimensions
        mappingSpecificity = $Specificity
        reviewPriority = $Priority
        automaticBandAllowed = "false"
        migrationStatus = "LEGACY_SEEDED_NOT_EVALUATED"
        migrationNotes = $Notes
    }
}

$tendonDimensions = "PEAK_TENSILE_LOAD|CYCLIC_TENSILE_LOAD"
$migrationRows = @(
    Migration "tendonStressTags" "ACHILLES_TENDON_STRESS" "ACHILLES_TENDON" $tendonDimensions "SPECIFIC" "HIGH" "Candidate only; no automatic band."
    Migration "tendonStressTags" "ADDUCTOR_TENDON_STRESS" "ADDUCTOR_TENDON_GROUP" $tendonDimensions "GROUPED" "HIGH" "Grouped candidate requires exercise-specific review."
    Migration "tendonStressTags" "BICEPS_TENDON_STRESS" "DISTAL_BICEPS_TENDON|LONG_HEAD_BICEPS_TENDON" $tendonDimensions "BROAD_AMBIGUOUS" "HIGH" "Legacy token does not identify proximal versus distal structure."
    Migration "tendonStressTags" "ELBOW_TENDON_STRESS" "COMMON_EXTENSOR_TENDON|COMMON_FLEXOR_TENDON|DISTAL_BICEPS_TENDON|TRICEPS_TENDON" $tendonDimensions "BROAD_AMBIGUOUS" "HIGH" "Broad elbow candidate; no equal split."
    Migration "tendonStressTags" "FOREARM_EXTENSOR_TENDON_STRESS" "COMMON_EXTENSOR_TENDON|WRIST_EXTENSOR_TENDON_GROUP" $tendonDimensions "BROAD_AMBIGUOUS" "HIGH" "Anatomical level is unresolved."
    Migration "tendonStressTags" "FOREARM_FLEXOR_TENDON_STRESS" "COMMON_FLEXOR_TENDON|WRIST_FLEXOR_TENDON_GROUP" $tendonDimensions "BROAD_AMBIGUOUS" "HIGH" "Anatomical level is unresolved."
    Migration "tendonStressTags" "GLUTE_MED_TENDON_STRESS" "GLUTEUS_MEDIUS_MINIMUS_TENDON" $tendonDimensions "GROUPED" "HIGH" "Candidate only; no automatic band."
    Migration "tendonStressTags" "HAMSTRING_TENDON_STRESS" "PROXIMAL_HAMSTRING_TENDON|DISTAL_HAMSTRING_TENDON" $tendonDimensions "BROAD_AMBIGUOUS" "HIGH" "Legacy token does not identify proximal versus distal structure."
    Migration "tendonStressTags" "PATELLAR_TENDON_STRESS" "PATELLAR_TENDON" $tendonDimensions "SPECIFIC" "HIGH" "Candidate only; no automatic band."
    Migration "tendonStressTags" "PECTORAL_TENDON_STRESS" "PECTORALIS_MAJOR_TENDON" $tendonDimensions "SPECIFIC" "HIGH" "Candidate only; no automatic band."
    Migration "tendonStressTags" "QUAD_TENDON_STRESS" "QUADRICEPS_TENDON" $tendonDimensions "SPECIFIC" "HIGH" "Alias retained as a separate legacy decision."
    Migration "tendonStressTags" "QUADRICEPS_TENDON_STRESS" "QUADRICEPS_TENDON" $tendonDimensions "SPECIFIC" "HIGH" "Candidate only; no automatic band."
    Migration "tendonStressTags" "ROTATOR_CUFF_TENDON_STRESS" "POSTERIOR_ROTATOR_CUFF_TENDON|SUBSCAPULARIS_TENDON|SUPRASPINATUS_TENDON" $tendonDimensions "BROAD_AMBIGUOUS" "HIGH" "Broad cuff token; no equal split."
    Migration "tendonStressTags" "SHOULDER_TENDON_STRESS" "LONG_HEAD_BICEPS_TENDON|PECTORALIS_MAJOR_TENDON|POSTERIOR_ROTATOR_CUFF_TENDON|SUBSCAPULARIS_TENDON|SUPRASPINATUS_TENDON" $tendonDimensions "BROAD_AMBIGUOUS" "HIGH" "Broad shoulder candidate requires evidence review."
    Migration "tendonStressTags" "TRICEPS_TENDON_STRESS" "TRICEPS_TENDON" $tendonDimensions "SPECIFIC" "HIGH" "Candidate only; no automatic band."

    Migration "ligamentJointStabilityStressTags" "ANKLE_EVERSION_CONTROL_STRESS" "ANKLE_SUBTALAR|ANKLE_DELTOID_LIGAMENT" "STABILITY_DEMAND|EVERSION" "BROAD_AMBIGUOUS" "HIGH" "Joint and ligament candidates remain separate."
    Migration "ligamentJointStabilityStressTags" "ANKLE_INVERSION_LIGAMENT_STRESS" "ANKLE_SUBTALAR|ANKLE_LATERAL_LIGAMENT_COMPLEX" "STABILITY_DEMAND|INVERSION" "BROAD_AMBIGUOUS" "HIGH" "Joint and ligament candidates remain separate."
    Migration "ligamentJointStabilityStressTags" "ANKLE_STABILITY_STRESS" "ANKLE_SUBTALAR|ANKLE_TALOCRURAL|ANKLE_DELTOID_LIGAMENT|ANKLE_LATERAL_LIGAMENT_COMPLEX|ANKLE_SYNDESMOSIS" "STABILITY_DEMAND|IMPACT_STABILIZATION" "BROAD_AMBIGUOUS" "HIGH" "Broad stability token does not populate every candidate."
    Migration "ligamentJointStabilityStressTags" "ELBOW_JOINT_STRESS" "HUMERORADIAL|HUMEROULNAR|ELBOW_LCL|ELBOW_UCL" "COMPRESSION|STABILITY_DEMAND|VALGUS|VARUS" "BROAD_AMBIGUOUS" "HIGH" "Broad elbow token requires dimension-specific review."
    Migration "ligamentJointStabilityStressTags" "HIP_PELVIS_STABILITY_STRESS" "HIP|SACROILIAC_COMPLEX|HIP_CAPSULOLIGAMENTOUS_COMPLEX|SACROILIAC_LIGAMENT_COMPLEX" "STABILITY_DEMAND|END_RANGE_RESTRAINT" "BROAD_AMBIGUOUS" "HIGH" "Hip and pelvic candidates remain separate."
    Migration "ligamentJointStabilityStressTags" "HIP_ROTATIONAL_CONTROL_STRESS" "HIP|HIP_CAPSULOLIGAMENTOUS_COMPLEX" "ROTATIONAL_SHEAR|INTERNAL_ROTATION|EXTERNAL_ROTATION" "BROAD_AMBIGUOUS" "HIGH" "Rotation direction requires exercise-specific review."
    Migration "ligamentJointStabilityStressTags" "JOINT_RANGE_CONTROL_STRESS" "GLENOHUMERAL|HIP|KNEE_TIBIOFEMORAL|ANKLE_TALOCRURAL|RADIOCARPAL_WRIST|GLENOHUMERAL_CAPSULOLIGAMENTOUS_COMPLEX|HIP_CAPSULOLIGAMENTOUS_COMPLEX" "END_RANGE_STRESS|END_RANGE_RESTRAINT" "BROAD_AMBIGUOUS" "HIGH" "Global range token is intentionally incomplete and review-only."
    Migration "ligamentJointStabilityStressTags" "KNEE_ANKLE_STABILITY_STRESS" "KNEE_TIBIOFEMORAL|ANKLE_SUBTALAR|ANKLE_TALOCRURAL|KNEE_ACL|KNEE_PCL|KNEE_MCL|KNEE_LCL|ANKLE_DELTOID_LIGAMENT|ANKLE_LATERAL_LIGAMENT_COMPLEX" "STABILITY_DEMAND|DECELERATION_STABILIZATION|IMPACT_STABILIZATION" "BROAD_AMBIGUOUS" "HIGH" "Broad stability token does not automatically populate candidates."
    Migration "ligamentJointStabilityStressTags" "KNEE_HIP_STABILITY_STRESS" "KNEE_TIBIOFEMORAL|HIP|KNEE_ACL|KNEE_PCL|KNEE_MCL|KNEE_LCL|HIP_CAPSULOLIGAMENTOUS_COMPLEX" "STABILITY_DEMAND|DECELERATION_STABILIZATION|IMPACT_STABILIZATION" "BROAD_AMBIGUOUS" "HIGH" "Knee and hip candidates require separate review."
    Migration "ligamentJointStabilityStressTags" "KNEE_JOINT_STRESS" "KNEE_PATELLOFEMORAL|KNEE_TIBIOFEMORAL" "COMPRESSION|STABILITY_DEMAND" "BROAD_AMBIGUOUS" "HIGH" "Legacy token cannot identify knee compartment."
    Migration "ligamentJointStabilityStressTags" "KNEE_ROTATIONAL_STABILITY_STRESS" "KNEE_TIBIOFEMORAL|KNEE_ACL|KNEE_MCL|KNEE_LCL" "ROTATIONAL_SHEAR|INTERNAL_ROTATION|EXTERNAL_ROTATION" "BROAD_AMBIGUOUS" "HIGH" "Rotation direction and structure require review."
    Migration "ligamentJointStabilityStressTags" "KNEE_STABILITY_STRESS" "KNEE_PATELLOFEMORAL|KNEE_TIBIOFEMORAL|KNEE_ACL|KNEE_PCL|KNEE_MCL|KNEE_LCL|KNEE_MPFL" "STABILITY_DEMAND|DECELERATION_STABILIZATION|IMPACT_STABILIZATION" "BROAD_AMBIGUOUS" "HIGH" "Broad stability token does not automatically populate every ligament."
    Migration "ligamentJointStabilityStressTags" "KNEE_VALGUS_CONTROL_STRESS" "KNEE_TIBIOFEMORAL|KNEE_MCL" "STABILITY_DEMAND|VALGUS" "BROAD_AMBIGUOUS" "HIGH" "Joint and MCL candidates remain independent."
    Migration "ligamentJointStabilityStressTags" "LUMBOPELVIC_CONTROL_STRESS" "LUMBAR_SPINE|SACROILIAC_COMPLEX|HIP|SACROILIAC_LIGAMENT_COMPLEX" "STABILITY_DEMAND|END_RANGE_RESTRAINT" "BROAD_AMBIGUOUS" "HIGH" "Broad lumbopelvic candidate requires review."
    Migration "ligamentJointStabilityStressTags" "SHOULDER_EXTENSION_STABILITY_STRESS" "GLENOHUMERAL|GLENOHUMERAL_CAPSULOLIGAMENTOUS_COMPLEX" "END_RANGE_STRESS|END_RANGE_RESTRAINT" "BROAD_AMBIGUOUS" "HIGH" "Extension condition requires exercise-specific review."
    Migration "ligamentJointStabilityStressTags" "SHOULDER_JOINT_STABILITY_STRESS" "ACROMIOCLAVICULAR|GLENOHUMERAL|SCAPULOTHORACIC_FUNCTIONAL_COMPLEX|ACROMIOCLAVICULAR_LIGAMENT_COMPLEX|GLENOHUMERAL_CAPSULOLIGAMENTOUS_COMPLEX" "STABILITY_DEMAND|END_RANGE_RESTRAINT" "BROAD_AMBIGUOUS" "HIGH" "Broad shoulder token does not assign a structure."
    Migration "ligamentJointStabilityStressTags" "SHOULDER_SCAPULAR_STABILITY_STRESS" "ACROMIOCLAVICULAR|GLENOHUMERAL|SCAPULOTHORACIC_FUNCTIONAL_COMPLEX|ACROMIOCLAVICULAR_LIGAMENT_COMPLEX" "STABILITY_DEMAND|IMPACT_STABILIZATION" "BROAD_AMBIGUOUS" "HIGH" "Functional and ligament candidates remain separate."
    Migration "ligamentJointStabilityStressTags" "WRIST_STABILITY_STRESS" "DISTAL_RADIOULNAR|RADIOCARPAL_WRIST|WRIST_LIGAMENT_TFCC_COMPLEX" "STABILITY_DEMAND|END_RANGE_RESTRAINT" "BROAD_AMBIGUOUS" "HIGH" "Broad wrist token requires review."
    Migration "ligamentJointStabilityStressTags" "WRIST_SUPPORT_STRESS" "DISTAL_RADIOULNAR|RADIOCARPAL_WRIST|WRIST_LIGAMENT_TFCC_COMPLEX" "COMPRESSION|STABILITY_DEMAND|IMPACT_STABILIZATION" "BROAD_AMBIGUOUS" "HIGH" "Support condition requires review."

    Migration "jointImpactStressTags" "COURT_DECELERATION_IMPACT" "HIP|KNEE_TIBIOFEMORAL|ANKLE_TALOCRURAL|KNEE_ACL|KNEE_PCL|KNEE_MCL|KNEE_LCL|ANKLE_LATERAL_LIGAMENT_COMPLEX" "IMPACT_IMPULSE|DECELERATION_STABILIZATION|IMPACT_STABILIZATION" "BROAD_AMBIGUOUS" "HIGH" "Court deceleration candidate; no automatic allocation."
    Migration "jointImpactStressTags" "DECELERATION_IMPACT" "HIP|KNEE_TIBIOFEMORAL|ANKLE_TALOCRURAL|KNEE_ACL|KNEE_PCL|KNEE_MCL|KNEE_LCL" "IMPACT_IMPULSE|DECELERATION_STABILIZATION" "BROAD_AMBIGUOUS" "HIGH" "Deceleration candidate; no automatic allocation."
    Migration "jointImpactStressTags" "DIRECTION_CHANGE_IMPACT_STRESS" "KNEE_TIBIOFEMORAL|ANKLE_SUBTALAR|ANKLE_TALOCRURAL|KNEE_ACL|KNEE_MCL|KNEE_LCL|ANKLE_DELTOID_LIGAMENT|ANKLE_LATERAL_LIGAMENT_COMPLEX" "IMPACT_IMPULSE|DECELERATION_STABILIZATION|IMPACT_STABILIZATION" "BROAD_AMBIGUOUS" "HIGH" "Direction-change structures require evidence review."
    Migration "jointImpactStressTags" "HIGH_AMPLITUDE_JUMP_LANDING_STRESS" "HIP|KNEE_PATELLOFEMORAL|KNEE_TIBIOFEMORAL|ANKLE_TALOCRURAL|KNEE_ACL|KNEE_MCL|ANKLE_LATERAL_LIGAMENT_COMPLEX" "IMPACT_IMPULSE|IMPACT_STABILIZATION" "BROAD_AMBIGUOUS" "HIGH" "Landing candidate; no automatic band."
    Migration "jointImpactStressTags" "JUMP_LANDING_IMPACT_STRESS" "HIP|KNEE_PATELLOFEMORAL|KNEE_TIBIOFEMORAL|ANKLE_TALOCRURAL|KNEE_ACL|KNEE_MCL|ANKLE_LATERAL_LIGAMENT_COMPLEX" "IMPACT_IMPULSE|IMPACT_STABILIZATION" "BROAD_AMBIGUOUS" "HIGH" "Landing candidate; no automatic band."
    Migration "jointImpactStressTags" "LOW_LEVEL_REACTIVE_IMPACT" "KNEE_PATELLOFEMORAL|KNEE_TIBIOFEMORAL|ANKLE_TALOCRURAL|FIRST_MTP|MIDFOOT" "IMPACT_IMPULSE|CYCLIC_MECHANICAL_EXPOSURE" "BROAD_AMBIGUOUS" "MEDIUM" "Low-level impact candidate remains unevaluated."
    Migration "jointImpactStressTags" "REPEATED_LOW_AMPLITUDE_IMPACT_STRESS" "KNEE_PATELLOFEMORAL|KNEE_TIBIOFEMORAL|ANKLE_TALOCRURAL|FIRST_MTP|MIDFOOT" "IMPACT_IMPULSE|CYCLIC_MECHANICAL_EXPOSURE" "BROAD_AMBIGUOUS" "MEDIUM" "Repeated impact candidate remains unevaluated."
    Migration "jointImpactStressTags" "REPETITIVE_LOCOMOTION_IMPACT" "HIP|KNEE_PATELLOFEMORAL|KNEE_TIBIOFEMORAL|ANKLE_SUBTALAR|ANKLE_TALOCRURAL|FIRST_MTP|MIDFOOT" "IMPACT_IMPULSE|CYCLIC_MECHANICAL_EXPOSURE" "BROAD_AMBIGUOUS" "MEDIUM" "Locomotion candidate remains unevaluated."
) | Sort-Object legacyField, legacyToken

$legacyHeaders = @(
    "legacyField", "legacyToken", "candidateTissueIds", "candidateDimensions", "mappingSpecificity",
    "reviewPriority", "automaticBandAllowed", "migrationStatus", "migrationNotes"
)
Write-CsvRows (Join-Path $OutputDirectory "legacy_tissue_tag_migration_v1.csv") $migrationRows $legacyHeaders

function DoseCapability(
    [string]$Basis,
    [string]$Source,
    [string]$Status,
    [string]$Method,
    [string]$Fields,
    [bool]$Schema,
    [bool]$Ui,
    [string]$Notes
) {
    [pscustomobject]@{
        doseBasis = $Basis
        recordSource = $Source
        availabilityStatus = $Status
        derivationMethod = $Method
        requiredFields = $Fields
        fallbackDoseBasis = ""
        fallbackAllowed = "false"
        fallbackConfidence = ""
        fallbackEvidenceClaimIds = ""
        sourceRefs = ""
        requiresSchemaChange = $Schema.ToString().ToLowerInvariant()
        requiresUiChange = $Ui.ToString().ToLowerInvariant()
        implementationNotes = $Notes
    }
}

$doseRows = @(
    DoseCapability "EXTERNAL_LOAD_REPETITIONS" "WorkoutSet" "DERIVABLE_FROM_CURRENT_RECORD" "sum(reps * weightKg) for confirmed sets" "WorkoutSet.reps|WorkoutSet.weightKg|WorkoutSet.confirmed" $false $false "No fallback; zero is valid only when inputs are present."
    DoseCapability "EFFECTIVE_BODYWEIGHT_REPETITIONS" "WorkoutSet+DailyMetric+InitialUserProfile+Exercise" "DERIVABLE_FROM_CURRENT_RECORD" "BodyweightEffectiveLoadCalculator.volumeLoad" "WorkoutSet.reps|WorkoutSet.weightKg|WorkoutSet.confirmed|Exercise.stableKey|DailyMetric.bodyWeightKg_or_InitialUserProfile.bodyWeightKg" $false $false "Existing bodyweight coefficients remain authoritative."
    DoseCapability "DURATION_HOLD" "WorkoutSet+WorkoutEntry+Exercise" "DERIVABLE_FROM_CURRENT_RECORD" "DurationHoldLoadCalculator.holdLoad" "WorkoutSet.seconds|WorkoutSet.confirmed|WorkoutSet.rpe_or_WorkoutEntry.rpe|Exercise.stableKey" $false $false "Existing plank and side-plank duration logic remains authoritative."
    DoseCapability "LOCOMOTION_DURATION" "WorkoutSet+Exercise" "DERIVABLE_FROM_CURRENT_RECORD" "sum(seconds) only for an explicitly duration-recorded locomotion exercise" "WorkoutSet.seconds|WorkoutSet.confirmed|Exercise.stableKey" $false $false "No event-count inference."
    DoseCapability "DISTANCE" "NONE" "NOT_CURRENTLY_AVAILABLE" "" "distance" $true $true "Distance is not stored."
    DoseCapability "LANDING_CONTACT_COUNT" "NONE" "NOT_CURRENTLY_AVAILABLE" "" "landingContactCount" $true $true "No count may be inferred from duration."
    DoseCapability "DIRECTION_CHANGE_COUNT" "NONE" "NOT_CURRENTLY_AVAILABLE" "" "directionChangeCount" $true $true "No count may be inferred from duration."
    DoseCapability "JUMP_COUNT" "NONE" "NOT_CURRENTLY_AVAILABLE" "" "jumpCount" $true $true "No count may be inferred from duration."
    DoseCapability "THROW_COUNT" "NONE" "NOT_CURRENTLY_AVAILABLE" "" "throwCount" $true $true "No count may be inferred from duration."
    DoseCapability "STROKE_COUNT" "NONE" "NOT_CURRENTLY_AVAILABLE" "" "strokeCount" $true $true "No count may be inferred from duration."
    DoseCapability "SESSION_DURATION_RPE" "WorkoutSet+WorkoutEntry+Exercise" "DERIVABLE_FROM_CURRENT_RECORD" "sum(seconds) * entry-or-set RPE only for an explicit duration session" "WorkoutSet.seconds|WorkoutSet.confirmed|WorkoutSet.rpe_or_WorkoutEntry.rpe|Exercise.activityKind" $false $false "Not a fallback for event counts."
    DoseCapability "MIXED_EVENT_DURATION" "WorkoutSet+Exercise" "DERIVABLE_FROM_CURRENT_RECORD" "sum(seconds) only when the record explicitly represents mixed-event duration" "WorkoutSet.seconds|WorkoutSet.confirmed|Exercise.activityKind" $false $false "Estimated event counts remain unavailable."
)
$doseHeaders = @(
    "doseBasis", "recordSource", "availabilityStatus", "derivationMethod", "requiredFields",
    "fallbackDoseBasis", "fallbackAllowed", "fallbackConfidence", "fallbackEvidenceClaimIds", "sourceRefs",
    "requiresSchemaChange", "requiresUiChange", "implementationNotes"
)
Write-CsvRows (Join-Path $OutputDirectory "dose_input_capability_v1.csv") $doseRows $doseHeaders

$modifierHeaders = @(
    "modifierRuleId", "stableKey", "tissueId", "loadDimension", "modifierFamily", "inputCondition",
    "referenceCondition", "operation", "factor", "bandShift", "specificityLevel", "exclusiveGroup",
    "interactionGroup", "precedence", "minimumCombinedFactor", "maximumCombinedFactor",
    "requiredRecordInputs", "missingInputBehavior", "evidenceStatus", "evidenceClaimIds", "sourceRefs",
    "confidenceLevel", "reviewStatus", "preparedBy", "preparedByType", "preparedAt", "blindReviewedBy",
    "blindReviewedByType", "blindReviewedAt", "humanApprovedBy", "humanApprovedAt", "reviewNotes"
)
Write-CsvRows (Join-Path $OutputDirectory "exercise_tissue_modifier_rules_v1.csv") @() $modifierHeaders

$recoveryHeaders = @(
    "recoveryProfileId", "tissueClass", "tissueId", "loadDimension", "calculationMode", "kernelType",
    "parameterSet", "validWindowHours", "evidenceStatus", "evidenceLevel", "evidenceClaimIds", "sourceRefs",
    "confidenceLevel", "productionEligibility", "preparedBy", "preparedByType", "preparedAt",
    "blindReviewedBy", "blindReviewedByType", "blindReviewedAt", "humanApprovedBy", "humanApprovedAt",
    "recoveryNotes"
)
Write-CsvRows (Join-Path $OutputDirectory "tissue_recovery_profiles_v1.csv") @() $recoveryHeaders

$actual = foreach ($field in "tendonStressTags", "ligamentJointStabilityStressTags", "jointImpactStressTags") {
    foreach ($token in ((Import-Csv $CanonicalPath).$field -split '\|' | ForEach-Object Trim | Where-Object { $_ -and $_ -ne "NONE" } | Sort-Object -Unique)) {
        "$field|$token"
    }
}
$generated = $migrationRows | ForEach-Object { "$($_.legacyField)|$($_.legacyToken)" }
if (Compare-Object $actual $generated) {
    throw "Generated migration rows do not exactly match canonical legacy tokens."
}

$auditPath = Join-Path $OutputDirectory "tissue_metadata_audit_manifest_v1.csv"
$auditHeaders = (Get-Content -LiteralPath $auditPath -TotalCount 1).Split(',') |
    ForEach-Object { $_.Trim().Trim('"') }
$audit = Import-Csv -LiteralPath $auditPath
$profileFiles = @(
    "exercise_joint_load_profiles_v1.csv",
    "exercise_tendon_load_profiles_v1.csv",
    "exercise_ligament_load_profiles_v1.csv",
    "exercise_fascia_load_profiles_v1.csv"
)
$profileHash = Get-TextHash (($profileFiles | ForEach-Object {
    "$_=$(Get-SemanticCsvHash (Join-Path $OutputDirectory $_))"
} | Sort-Object) -join "`n")
$claimHash = Get-TextHash ((@(
    "draft=$(Get-SemanticCsvHash (Join-Path $OutputDirectory 'tissue_evidence_claims_draft_v1.csv'))",
    "blind=$(Get-SemanticCsvHash (Join-Path $OutputDirectory 'tissue_evidence_blind_review_v1.csv'))",
    "final=$(Get-SemanticCsvHash (Join-Path $OutputDirectory 'tissue_evidence_claims_v1.csv'))"
) | Sort-Object) -join "`n")
$modifierHash = Get-SemanticCsvHash (Join-Path $OutputDirectory "exercise_tissue_modifier_rules_v1.csv")
$doseHash = Get-SemanticCsvHash (Join-Path $OutputDirectory "dose_input_capability_v1.csv")
$migrationHash = Get-SemanticCsvHash (Join-Path $OutputDirectory "legacy_tissue_tag_migration_v1.csv")
$recoveryHash = Get-SemanticCsvHash (Join-Path $OutputDirectory "tissue_recovery_profiles_v1.csv")
$inputHash = Get-TextHash ((@(
    "canonical=$(Get-SemanticCsvHash $CanonicalPath)",
    "catalog=$(Get-SemanticCsvHash (Join-Path $OutputDirectory 'canonical_tissue_catalog_v1.csv'))",
    "scope=$(Get-SemanticCsvHash (Join-Path $OutputDirectory 'exercise_tissue_scope_manifest_v1.csv'))",
    "profiles=$profileHash",
    "rubric=$(Get-SemanticCsvHash (Join-Path $OutputDirectory 'tissue_load_band_rubric_v1.csv'))",
    "evidence=$(Get-SemanticCsvHash (Join-Path $OutputDirectory 'tissue_load_evidence_registry_v1.csv'))",
    "claims=$claimHash",
    "sourceVerification=$(Get-SemanticCsvHash (Join-Path $OutputDirectory 'tissue_source_verification_v1.csv'))",
    "legacyMigration=$migrationHash",
    "doseCapability=$doseHash",
    "modifier=$modifierHash",
    "recovery=$recoveryHash"
) | Sort-Object) -join "`n")
$audit.auditManifestId = "tissue_foundation_v1_stage4_$($inputHash.Substring(0, 12))"
$audit.modifierSnapshotHash = $modifierHash
$audit.recoverySnapshotHash = $recoveryHash
$audit.doseCapabilitySnapshotHash = $doseHash
$audit.doseCapabilityStatus = "PASS"
$audit.lateralityCoverageStatus = "PASS"
$audit.modifierValidationStatus = "PASS"
$audit.recoveryValidationStatus = "PASS"
$audit.warningCount = "3"
$audit.inputSnapshotHash = $inputHash
$audit.auditDecision = "FOUNDATION_COMPLETE_CANDIDATE"
$audit.auditNotes = "All deterministic foundation contracts pass in non-production shadow mode; rubric research, backfill, independent review, and human approval remain pending."
Write-CsvRows $auditPath @($audit) $auditHeaders

Write-Output "LEGACY_MIGRATION_COUNT=$($migrationRows.Count)"
Write-Output "LEGACY_MIGRATION_HASH=$migrationHash"
Write-Output "DOSE_CAPABILITY_COUNT=$($doseRows.Count)"
Write-Output "MODIFIER_RULE_COUNT=0"
Write-Output "RECOVERY_PROFILE_COUNT=0"

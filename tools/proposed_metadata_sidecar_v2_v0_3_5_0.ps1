param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

function Require-File([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Required input missing: $Path" }
}

function Get-Row([object[]]$Rows, [string]$StableKey) {
    $row = $Rows | Where-Object stableKey -eq $StableKey | Select-Object -First 1
    if (-not $row) { throw "Stable key not found in v1 sidecar: $StableKey" }
    return $row
}

function Set-Value([pscustomobject]$Row, [string]$Name, [object]$Value) {
    $Row.$Name = $Value
}

function Set-ReviewState(
    [pscustomobject]$Row,
    [string]$Status,
    [string]$RowReview,
    [string]$SourceReview,
    [string]$TemplateReview,
    [string]$StressReview,
    [string]$SafeTemplate,
    [string]$Reason,
    [string]$NextAction
) {
    Set-Value $Row 'proposalStatus' $Status
    Set-Value $Row 'rowManualReviewRequired' $RowReview
    Set-Value $Row 'sourceReviewRequired' $SourceReview
    Set-Value $Row 'templateMissingReviewRequired' $TemplateReview
    Set-Value $Row 'stressReviewRequired' $StressReview
    Set-Value $Row 'sourceOnlyReview' $(if ($SourceReview -eq 'YES' -and $RowReview -eq 'NO' -and $TemplateReview -eq 'NO' -and $StressReview -eq 'NO' -and $Row.sportSessionReviewRequired -eq 'NO') { 'YES' } else { 'NO' })
    Set-Value $Row 'safeForTemplateDefault' $SafeTemplate
    Set-Value $Row 'safeForSeedMutation' 'NO'
    Set-Value $Row 'proposalReason' $Reason
    Set-Value $Row 'recommendedNextAction' $NextAction
    Set-Value $Row 'metadataConfidenceCandidate' $(if ($RowReview -eq 'YES') { 'NEEDS_REVIEW' } elseif ($TemplateReview -eq 'YES' -or $StressReview -eq 'YES') { 'LOW' } elseif ($SourceReview -eq 'YES') { 'MEDIUM' } else { 'HIGH' })
}

function Set-ProposedTemplate(
    [pscustomobject]$Row,
    [string]$Family,
    [string]$Subtype,
    [string]$Tags,
    [string]$Contribution,
    [string]$Slot,
    [string]$Redundancy,
    [string]$Progress,
    [string]$Fatigue,
    [string]$Direct,
    [string]$Supportive,
    [string]$Eligibility,
    [string]$Sources,
    [string]$EvidenceLevel
) {
    Set-Value $Row 'proposedMovementFamily' $Family
    Set-Value $Row 'proposedMovementSubtype' $Subtype
    Set-Value $Row 'proposedDetailedMuscleTags' $Tags
    Set-Value $Row 'proposedMuscleContribution' $Contribution
    Set-Value $Row 'proposedProgramSlot' $Slot
    Set-Value $Row 'proposedRedundancyGroup' $Redundancy
    Set-Value $Row 'proposedProgressMetricType' $Progress
    Set-Value $Row 'proposedFatigueProfile' $Fatigue
    Set-Value $Row 'proposedBadmintonTransferDirect' $Direct
    Set-Value $Row 'proposedBadmintonTransferSupportive' $Supportive
    Set-Value $Row 'proposedAnalysisEligibility' $Eligibility
    Set-Value $Row 'proposedEvidenceSourceGroup' $Sources
    Set-Value $Row 'proposedEvidenceLevel' $EvidenceLevel
    Set-Value $Row 'notes' 'V2 taxonomy correction draft only. Muscle contribution is an app-internal heuristic, not a physiological measurement. Do not mutate seed without human approval.'
}

function Copy-FamilyTemplate([pscustomobject]$Target, [pscustomobject]$Reference, [string]$Family, [string]$Subtype) {
    foreach ($field in @(
        'proposedDetailedMuscleTags','proposedMuscleContribution','proposedProgramSlot','proposedRedundancyGroup',
        'proposedProgressMetricType','proposedFatigueProfile','proposedBadmintonTransferDirect',
        'proposedBadmintonTransferSupportive','proposedAnalysisEligibility','proposedEvidenceSourceGroup','proposedEvidenceLevel'
    )) {
        Set-Value $Target $field $Reference.$field
    }
    Set-Value $Target 'proposedMovementFamily' $Family
    Set-Value $Target 'proposedMovementSubtype' $Subtype
    Set-Value $Target 'notes' 'V2 blocker correction draft only. Source support and subtype must be reviewed before seed mutation.'
}

function Add-ValidationCheck(
    [System.Collections.Generic.List[object]]$Checks,
    [string]$Id,
    [string]$Description,
    [object]$Expected,
    [object]$Actual,
    [bool]$Passed,
    [string]$Details = ''
) {
    $Checks.Add([pscustomobject][ordered]@{
        checkId = $Id
        description = $Description
        expected = [string]$Expected
        actual = [string]$Actual
        status = if ($Passed) { 'PASS' } else { 'FAIL' }
        details = $Details
    })
}

$v1Csv = Join-Path $RepoRoot 'outputs\v0.3.5.0_proposed_exercise_metadata_sidecar.csv'
$v1Md = Join-Path $RepoRoot 'outputs\v0.3.5.0_proposed_exercise_metadata_sidecar.md'
$v1Json = Join-Path $RepoRoot 'outputs\v0.3.5.0_proposed_exercise_metadata_sidecar.json'
$v1Summary = Join-Path $RepoRoot 'outputs\v0.3.5.0_sidecar_generation_summary.md'
$auditCsv = Join-Path $RepoRoot 'outputs\v0.3.5.0_seed_metadata_gap_report_audited.csv'
$manualAuditCsv = Join-Path $RepoRoot 'outputs\v0.3.5.0_manual_review_audit_summary.csv'
$taxonomyDoc = Join-Path $RepoRoot 'docs\metadata_taxonomy_delta_v0.3.5.0.md'
$planDoc = Join-Path $RepoRoot 'docs\exercise_metadata_reclassification_plan_v0.3.5.0.md'
$validatorDoc = Join-Path $RepoRoot 'docs\metadata_validator_requirements_v0.3.5.0.md'
$sourceDoc = Join-Path $RepoRoot 'docs\metadata_evidence_sources_v0.3.5.0.md'
$seedPath = Join-Path $RepoRoot 'app\src\main\assets\training_settings_seed.csv'

$requiredInputs = @($v1Csv,$v1Md,$v1Json,$v1Summary,$auditCsv,$manualAuditCsv,$taxonomyDoc,$planDoc,$validatorDoc,$sourceDoc,$seedPath)
$requiredInputs | ForEach-Object { Require-File $_ }

$optionalNames = @(
    'v0.3.5.0_sidecar_individual_error_reaudit_full.csv',
    'v0.3.5.0_sidecar_individual_error_reaudit_issues_only.csv',
    'v0.3.5.0_sidecar_individual_error_reaudit_issues_only.md'
)
$optionalFound = @($optionalNames | Where-Object { Test-Path -LiteralPath (Join-Path $RepoRoot "outputs\$_") })
$optionalMissing = @($optionalNames | Where-Object { $_ -notin $optionalFound })

$seedHashBefore = (Get-FileHash -Algorithm SHA256 -LiteralPath $seedPath).Hash
$rows = @(Import-Csv -LiteralPath $v1Csv)
if ($rows.Count -ne 215) { throw "V1 sidecar must have 215 rows; found $($rows.Count)." }

$armSources = 'A_OPENSTAX_11_5|E_BADMINTON_INJURY_PMC7205924'
$bicepsTags = 'BICEPS_BRACHII|BRACHIALIS|BRACHIORADIALIS|FOREARM_FLEXORS|GRIP_FLEXORS|DEEP_CORE'
$bicepsContribution = '{"BICEPS_BRACHII":0.45,"BRACHIALIS":0.18,"BRACHIORADIALIS":0.12,"FOREARM_FLEXORS":0.10,"GRIP_FLEXORS":0.08,"DEEP_CORE":0.07}'
$neutralTags = 'BRACHIALIS|BRACHIORADIALIS|BICEPS_BRACHII|FOREARM_EXTENSORS|FOREARM_FLEXORS|GRIP_FLEXORS|DEEP_CORE'
$neutralContribution = '{"BRACHIALIS":0.28,"BRACHIORADIALIS":0.25,"BICEPS_BRACHII":0.18,"FOREARM_EXTENSORS":0.10,"FOREARM_FLEXORS":0.08,"GRIP_FLEXORS":0.07,"DEEP_CORE":0.04}'
$tricepsTags = 'TRICEPS_BRACHII|TRICEPS_LONG_HEAD|TRICEPS_LATERAL_MEDIAL|FOREARM_STABILIZERS|DEEP_CORE'
$tricepsContribution = '{"TRICEPS_BRACHII":0.45,"TRICEPS_LONG_HEAD":0.20,"TRICEPS_LATERAL_MEDIAL":0.20,"FOREARM_STABILIZERS":0.07,"DEEP_CORE":0.08}'
$overheadTags = 'TRICEPS_LONG_HEAD|TRICEPS_BRACHII|TRICEPS_LATERAL_MEDIAL|SHOULDER_STABILIZERS|ROTATOR_CUFF_EXTERNAL|DEEP_CORE'
$forearmTags = 'FOREARM_FLEXORS|FOREARM_EXTENSORS|PRONATOR_SUPINATOR_GROUP|GRIP_FLEXORS|WRIST_STABILIZERS'

$bicepsMap = [ordered]@{
    ex_8633d8db='EZ_BAR_CURL'; ex_281347da='DUMBBELL_CURL'; ex_85b2ac56='ONE_ARM_DUMBBELL_CURL'
    ex_3d5719de='ONE_ARM_CABLE_CURL'; ex_e709eeda='CABLE_CURL'; ex_d5048362='INCLINE_DUMBBELL_CURL'
    ex_e994008a='PREACHER_CURL'; ex_c62c0716='CONCENTRATION_CURL'; ex_516f4456='SPIDER_CURL'
}
$neutralMap = [ordered]@{
    ex_2892da5a='HAMMER_CURL'; ex_c1a81cb2='ROPE_HAMMER_CURL'; ex_b69ab702='CROSS_BODY_HAMMER_CURL'
    ex_86323ed6='ONE_ARM_HAMMER_CURL'; ex_dd2f732e='REVERSE_CURL'
}
$tricepsMap = [ordered]@{
    ex_fa570291='TRICEPS_PUSHDOWN'; ex_5322f2d1='ONE_ARM_TRICEPS_PUSHDOWN'; ex_da56a4fe='DUMBBELL_KICKBACK'
    ex_eb636bac='CABLE_ONE_ARM_TRICEPS_EXTENSION'; ex_d20b7487='LYING_TRICEPS_EXTENSION'
}
$overheadMap = [ordered]@{
    ex_a9e8859c='OVERHEAD_TRICEPS_EXTENSION'; ex_fdbdf454='ONE_ARM_OVERHEAD_TRICEPS_EXTENSION'
}
$forearmMap = [ordered]@{
    ex_95028bfe='WRIST_CURL'; ex_8e18b02a='REVERSE_WRIST_CURL'; ex_72f11dc5='WRIST_EXTENSION'; ex_f6703b06='PRONATION_SUPINATION'
}

foreach ($entry in $bicepsMap.GetEnumerator()) {
    $row = Get-Row $rows $entry.Key
    Set-ProposedTemplate $row 'ELBOW_FLEXION_BICEPS_CURL_VARIANTS' $entry.Value $bicepsTags $bicepsContribution 'BICEPS_ACCESSORY' 'ELBOW_FLEXION_CURL' 'VOLUME_LOAD' 'LOCAL_MUSCLE,ELBOW_FLEXION,GRIP_FOREARM_LOW_TO_MODERATE,LOW_SYSTEMIC' 'NONE' 'GRIP_FOREARM_SUPPORT_WHEN_MEANINGFUL' 'FATIGUE,HYPERTROPHY_VOLUME,BADMINTON_TRANSFER,BALANCE' $armSources 'anatomy_textbook'
    Set-ReviewState $row 'NEEDS_SOURCE_REVIEW' 'NO' 'YES' 'NO' 'NO' 'YES' 'Arm family is corrected; badminton-support source still needs network recheck.' 'VERIFY_EXISTING_SOURCE_IDS'
}
foreach ($entry in $neutralMap.GetEnumerator()) {
    $row = Get-Row $rows $entry.Key
    Set-ProposedTemplate $row 'ELBOW_FLEXION_BRACHIALIS_BRACHIORADIALIS_VARIANTS' $entry.Value $neutralTags $neutralContribution 'BRACHIALIS_FOREARM_ACCESSORY' 'ELBOW_FLEXION_NEUTRAL_PRONATED_CURL' 'VOLUME_LOAD' 'LOCAL_MUSCLE,ELBOW_FLEXION,GRIP_FOREARM_MODERATE,LOW_SYSTEMIC' 'NONE' 'GRIP_FOREARM_SUPPORT' 'FATIGUE,HYPERTROPHY_VOLUME,BADMINTON_TRANSFER,BALANCE' $armSources 'anatomy_textbook'
    Set-ReviewState $row 'NEEDS_SOURCE_REVIEW' 'NO' 'YES' 'NO' 'NO' 'YES' 'Neutral/pronated curl family is corrected; support source still needs review.' 'VERIFY_EXISTING_SOURCE_IDS'
}
foreach ($entry in $tricepsMap.GetEnumerator()) {
    $row = Get-Row $rows $entry.Key
    Set-ProposedTemplate $row 'ELBOW_EXTENSION_TRICEPS_ISOLATION_VARIANTS' $entry.Value $tricepsTags $tricepsContribution 'TRICEPS_ACCESSORY' 'ELBOW_EXTENSION_TRICEPS' 'VOLUME_LOAD' 'LOCAL_MUSCLE,ELBOW_EXTENSION,LOW_SYSTEMIC' 'NONE' 'UPPER_PUSH_SUPPORT_IF_NEEDED' 'FATIGUE,HYPERTROPHY_VOLUME,BALANCE' 'A_OPENSTAX_11_5' 'anatomy_textbook'
    Set-ReviewState $row 'READY_TEMPLATE_CANDIDATE' 'NO' 'NO' 'NO' 'NO' 'YES' 'Stable elbow-extension template is separated from biceps and forearm rows.' 'HUMAN_APPROVE_TEMPLATE_BEFORE_SEED_PATCH'
}
foreach ($entry in $overheadMap.GetEnumerator()) {
    $row = Get-Row $rows $entry.Key
    Set-ProposedTemplate $row 'OVERHEAD_TRICEPS_LONG_HEAD_REVIEW' $entry.Value $overheadTags 'REVIEW' 'TRICEPS_ACCESSORY_REVIEW' 'OVERHEAD_TRICEPS_LONG_HEAD' 'VOLUME_LOAD' 'LOCAL_MUSCLE,ELBOW_EXTENSION,OVERHEAD_REPETITION_REVIEW,SHOULDER_STRESS_REVIEW' 'NONE' 'OVERHEAD_TOLERANCE_REVIEW' 'FATIGUE,HYPERTROPHY_VOLUME,BALANCE,BADMINTON_TRANSFER_REVIEW' 'A_OPENSTAX_11_5|D_SCAP_ROTATOR_CUFF_PMC2857390' 'anatomy_textbook'
    Set-ReviewState $row 'NEEDS_TEMPLATE_DEFINITION' 'NO' 'NO' 'YES' 'YES' 'NO' 'Overhead triceps remains a review family; concrete contribution is intentionally withheld.' 'DEFINE_AND_REVIEW_FAMILY_TEMPLATE'
}
foreach ($entry in $forearmMap.GetEnumerator()) {
    $row = Get-Row $rows $entry.Key
    Set-ProposedTemplate $row 'FOREARM_GRIP_ACCESSORY_REVIEW' $entry.Value $forearmTags 'REVIEW' 'FOREARM_GRIP_ACCESSORY_REVIEW' 'FOREARM_WRIST_GRIP' 'QUALITY_BASED' 'LOCAL_MUSCLE,GRIP_FOREARM,WRIST_FOREARM_REVIEW,LOW_SYSTEMIC' 'NONE' 'GRIP_FOREARM_SUPPORT' 'FATIGUE,HYPERTROPHY_VOLUME,BADMINTON_TRANSFER,BALANCE' $armSources 'anatomy_textbook'
    Set-ReviewState $row 'NEEDS_TEMPLATE_DEFINITION' 'NO' 'YES' 'YES' 'NO' 'NO' 'Forearm/grip rows are separated from arm isolation; subtype contribution remains under review.' 'DEFINE_AND_REVIEW_FAMILY_TEMPLATE'
}

$rowReference = $rows | Where-Object proposedMovementFamily -eq 'ROW_VARIANTS' | Select-Object -First 1
$footworkReference = $rows | Where-Object proposedMovementFamily -eq 'FOOTWORK_SPLIT_STEP_REACTION_DRILL_VARIANTS' | Select-Object -First 1
$chestSupportedMap = [ordered]@{
    ex_8e51640a='CHEST_SUPPORTED_ROW'; ex_6a966452='CHEST_SUPPORTED_ONE_ARM_DUMBBELL_ROW'; ex_462c760e='CHEST_SUPPORTED_ONE_ARM_ROW'
}
foreach ($entry in $chestSupportedMap.GetEnumerator()) {
    $row = Get-Row $rows $entry.Key
    Copy-FamilyTemplate $row $rowReference 'ROW_VARIANTS' $entry.Value
    Set-ReviewState $row 'NEEDS_ROW_REVIEW' 'YES' 'YES' 'NO' 'NO' 'PARTIAL' 'Chest-supported row is removed from bench/push; non-inverted row evidence needs subtype review.' 'CONFIRM_ROW_SUBTYPE_AND_SOURCE'
}

$scapPushup = Get-Row $rows 'ex_c4535de3'
Set-ProposedTemplate $scapPushup 'SERRATUS_SCAPULAR_PROTRACTION_CONTROL_REVIEW' 'SCAPULAR_PUSH_UP' 'SERRATUS_ANTERIOR|TRAPEZIUS_LOWER|TRAPEZIUS_MIDDLE|DEEP_CORE|ROTATOR_CUFF_EXTERNAL' 'REVIEW' 'SHOULDER_CARE_REVIEW' 'SCAPULAR_PROTRACTION_CONTROL' 'QUALITY_BASED' 'LOW_FATIGUE_REHAB,LOCAL_MUSCLE,SCAPULAR_CONTROL' 'NONE' 'SHOULDER_DURABILITY,OVERHEAD_CONTROL' 'RECOVERY_ONLY,BALANCE,BADMINTON_TRANSFER' 'A_OPENSTAX_11_5|D_SCAP_ROTATOR_CUFF_PMC2857390' 'anatomy_textbook'
Set-ReviewState $scapPushup 'NEEDS_ROW_REVIEW' 'YES' 'NO' 'YES' 'NO' 'NO' 'Scapular push-up is removed from bench press and requires a dedicated serratus template.' 'DEFINE_SCAPULAR_PROTRACTION_TEMPLATE'

$benchDip = Get-Row $rows 'ex_deca2b61'
Set-ProposedTemplate $benchDip 'DIP_TRICEPS_PUSH_REVIEW' 'BENCH_DIP' 'TRICEPS_BRACHII|TRICEPS_LONG_HEAD|ANTERIOR_DELTOID|PECTORALIS_MAJOR_STERNAL|DEEP_CORE' 'REVIEW' 'TRICEPS_ACCESSORY_REVIEW' 'DIP_TRICEPS_PUSH_REVIEW' 'VOLUME_LOAD' 'LOCAL_MUSCLE,ELBOW_EXTENSION,SHOULDER_STRESS_REVIEW' 'NONE' 'UPPER_PUSH_SUPPORT' 'FATIGUE,HYPERTROPHY_VOLUME,BALANCE' 'A_OPENSTAX_11_5|D_SCAP_ROTATOR_CUFF_PMC2857390' 'anatomy_textbook'
Set-ReviewState $benchDip 'NEEDS_ROW_REVIEW' 'YES' 'NO' 'YES' 'YES' 'NO' 'Bench dip is removed from bench press and requires shoulder-extension stress review.' 'DEFINE_DIP_TRICEPS_PUSH_TEMPLATE'

$reverseNordic = Get-Row $rows 'ex_8a3800f1'
Set-ProposedTemplate $reverseNordic 'QUAD_ECCENTRIC_KNEE_DOMINANT_REVIEW' 'REVERSE_NORDIC_CURL' 'QUADRICEPS|RECTUS_FEMORIS|HIP_FLEXORS|DEEP_CORE' 'REVIEW' 'KNEE_DOMINANT_ACCESSORY_REVIEW' 'QUAD_ECCENTRIC_KNEE_DOMINANT_REVIEW' 'QUALITY_BASED' 'LOCAL_MUSCLE,ECCENTRIC_QUADRICEPS,KNEE_STRESS_REVIEW' 'NONE' 'KNEE_CONTROL_SUPPORT' 'FATIGUE,BALANCE' 'A_OPENSTAX_11_6' 'anatomy_textbook'
Set-ReviewState $reverseNordic 'NEEDS_ROW_REVIEW' 'YES' 'NO' 'YES' 'YES' 'NO' 'Reverse Nordic is removed from hamstring curl; eccentric knee-extension mapping needs review.' 'DEFINE_QUAD_ECCENTRIC_TEMPLATE'

$legExtensionMap = [ordered]@{ ex_b78a8f95='LEG_EXTENSION'; ex_d60745b4='SINGLE_LEG_LEG_EXTENSION' }
foreach ($entry in $legExtensionMap.GetEnumerator()) {
    $row = Get-Row $rows $entry.Key
    Set-ProposedTemplate $row 'KNEE_EXTENSION_QUAD_ISOLATION_REVIEW' $entry.Value 'QUADRICEPS|RECTUS_FEMORIS|DEEP_CORE' 'REVIEW' 'KNEE_EXTENSION_ACCESSORY_REVIEW' 'KNEE_EXTENSION_QUAD_ISOLATION_REVIEW' 'VOLUME_LOAD' 'LOCAL_MUSCLE,KNEE_EXTENSION,LOW_SYSTEMIC' 'NONE' 'KNEE_CONTROL_SUPPORT' 'FATIGUE,HYPERTROPHY_VOLUME,BALANCE' 'A_OPENSTAX_11_6' 'anatomy_textbook'
    Set-ReviewState $row 'NEEDS_ROW_REVIEW' 'YES' 'NO' 'YES' 'NO' 'NO' 'Leg extension is separated from the leg-press compound template.' 'DEFINE_KNEE_EXTENSION_ISOLATION_TEMPLATE'
}

$reactiveLunge = Get-Row $rows 'ex_377448a9'
Copy-FamilyTemplate $reactiveLunge $footworkReference 'BADMINTON_REACTIVE_LUNGE_FOOTWORK_REVIEW' 'SPLIT_STEP_LATERAL_REACTIVE_LUNGE'
Set-Value $reactiveLunge 'proposedProgramSlot' 'BADMINTON_FOOTWORK_REVIEW'
Set-Value $reactiveLunge 'proposedRedundancyGroup' 'BADMINTON_REACTIVE_LUNGE_FOOTWORK'
Set-Value $reactiveLunge 'proposedBadmintonTransferDirect' 'FOOTWORK,REACTION,DECELERATION,LUNGE_REACH'
Set-ReviewState $reactiveLunge 'NEEDS_ROW_REVIEW' 'YES' 'YES' 'YES' 'YES' 'NO' 'Reactive split-step lunge is removed from generic strength-lunge programming.' 'DEFINE_BADMINTON_REACTIVE_LUNGE_TEMPLATE'

$squatAccessory = [ordered]@{
    ex_cb3c4dc2=@('BODYWEIGHT_SQUAT','BODYWEIGHT_PATTERN_REVIEW','SQUAT_PATTERN_BODYWEIGHT')
    ex_ac7df636=@('GOBLET_SQUAT','KNEE_DOMINANT_ACCESSORY','SQUAT_PATTERN_ACCESSORY')
    ex_ee378da7=@('SKATER_SQUAT','UNILATERAL_LOWER_ACCESSORY','SINGLE_LEG_SQUAT_STABILITY')
    ex_85f12271=@('SINGLE_LEG_BOX_SQUAT','UNILATERAL_LOWER_ACCESSORY','SINGLE_LEG_SQUAT_STABILITY')
    ex_a611010d=@('COSSACK_SQUAT','UNILATERAL_LOWER_ACCESSORY','FRONTAL_PLANE_SQUAT_ACCESSORY')
    ex_d67e5761=@('PISTOL_SQUAT','UNILATERAL_LOWER_ACCESSORY','SINGLE_LEG_SQUAT_STABILITY')
}
foreach ($entry in $squatAccessory.GetEnumerator()) {
    $row = Get-Row $rows $entry.Key
    Set-Value $row 'proposedMovementSubtype' $entry.Value[0]
    Set-Value $row 'proposedProgramSlot' $entry.Value[1]
    Set-Value $row 'proposedRedundancyGroup' $entry.Value[2]
    Set-Value $row 'proposedProgressMetricType' $(if ($entry.Key -eq 'ex_cb3c4dc2') { 'REPS_AT_LOAD' } else { 'VOLUME_LOAD' })
    Set-Value $row 'proposedFatigueProfile' 'LOCAL_MUSCLE,KNEE_CONTROL,HIP_STABILITY,LOW_TO_MODERATE_SYSTEMIC'
    Set-ReviewState $row 'NEEDS_ROW_REVIEW' 'YES' $row.sourceReviewRequired 'NO' 'NO' 'PARTIAL' 'Accessory squat variant must not inherit heavy barbell slot or redundancy.' 'CONFIRM_ACCESSORY_SQUAT_SLOT'
}

$unilateralHinge = [ordered]@{
    ex_201f6426='B_STANCE_RDL'; ex_e0b1e364='ONE_ARM_DUMBBELL_RDL'; ex_885b629='KICKSTAND_RDL'; single_leg_rdl='SINGLE_LEG_RDL'
}
foreach ($entry in $unilateralHinge.GetEnumerator()) {
    $row = Get-Row $rows $entry.Key
    Set-Value $row 'proposedMovementSubtype' $entry.Value
    Set-Value $row 'proposedProgramSlot' 'UNILATERAL_HINGE_ACCESSORY'
    Set-Value $row 'proposedRedundancyGroup' 'SINGLE_LEG_HINGE_STABILITY'
    Set-Value $row 'proposedProgressMetricType' 'VOLUME_LOAD'
    Set-Value $row 'proposedFatigueProfile' 'LOCAL_MUSCLE,HIP_STABILITY,LOW_TO_MODERATE_SYSTEMIC'
    Set-ReviewState $row 'NEEDS_ROW_REVIEW' 'YES' $row.sourceReviewRequired 'NO' 'NO' 'PARTIAL' 'Unilateral hinge must not inherit heavy hinge slot or redundancy.' 'CONFIRM_UNILATERAL_HINGE_TEMPLATE'
}

$pullThrough = Get-Row $rows 'ex_4c76dbb2'
Set-Value $pullThrough 'proposedMovementSubtype' 'CABLE_PULL_THROUGH'
Set-Value $pullThrough 'proposedProgramSlot' 'POSTERIOR_CHAIN_ACCESSORY'
Set-Value $pullThrough 'proposedRedundancyGroup' 'HIP_EXTENSION_ACCESSORY'
Set-Value $pullThrough 'proposedProgressMetricType' 'VOLUME_LOAD'
Set-Value $pullThrough 'proposedFatigueProfile' 'LOCAL_MUSCLE,POSTERIOR_CHAIN,LOW_TO_MODERATE_SYSTEMIC'
Set-ReviewState $pullThrough 'NEEDS_ROW_REVIEW' 'YES' $pullThrough.sourceReviewRequired 'NO' 'NO' 'PARTIAL' 'Cable pull-through is an accessory, not a main heavy hinge.' 'CONFIRM_POSTERIOR_CHAIN_ACCESSORY_TEMPLATE'

$kettlebellSwing = Get-Row $rows 'ex_7404067c'
Set-ProposedTemplate $kettlebellSwing 'BALLISTIC_HINGE_POWER_REVIEW' 'KETTLEBELL_SWING' 'REVIEW' 'REVIEW' 'BALLISTIC_HINGE_POWER_REVIEW' 'BALLISTIC_HINGE_POWER_REVIEW' 'QUALITY_BASED' 'NEURAL_SPEED,HIP_POWER,LOCAL_MUSCLE,SYSTEMIC_REVIEW' 'NONE' 'POSTERIOR_CHAIN_POWER_SUPPORT' 'FATIGUE,BALANCE,BADMINTON_TRANSFER' 'A_OPENSTAX_11_6|B_NSCA_ESSENTIALS_5E' 'anatomy_textbook'
Set-ReviewState $kettlebellSwing 'NEEDS_ROW_REVIEW' 'YES' 'NO' 'YES' 'YES' 'NO' 'Kettlebell swing is separated from the deadlift main-strength template.' 'DEFINE_BALLISTIC_HINGE_POWER_TEMPLATE'

$ankleSscMap = [ordered]@{ ex_a3ddd8ac='LINE_HOP'; ex_e465d1e9='POGO_JUMP'; ex_9bd6dddb='JUMP_ROPE' }
foreach ($entry in $ankleSscMap.GetEnumerator()) {
    $row = Get-Row $rows $entry.Key
    Set-ProposedTemplate $row 'ANKLE_STIFFNESS_SSC_CONDITIONING_REVIEW' $entry.Value 'GASTROCNEMIUS|SOLEUS|TIBIALIS_ANTERIOR|FOOT_INTRINSICS|DEEP_CORE' 'REVIEW' 'ANKLE_SSC_CONDITIONING_REVIEW' 'ANKLE_STIFFNESS_SSC_CONDITIONING_REVIEW' 'TIME_OR_REPS' 'ELASTIC_SSC,NEURAL_SPEED,LOW_LEVEL_PLYOMETRIC,ANKLE_STIFFNESS' 'NONE' 'ANKLE_STIFFNESS_SUPPORT,NEURAL_SPEED_SUPPORT' 'FATIGUE,BADMINTON_TRANSFER,BALANCE' 'A_OPENSTAX_11_6|B_NSCA_ESSENTIALS_5E' 'anatomy_textbook'
    Set-ReviewState $row 'NEEDS_ROW_REVIEW' 'YES' 'NO' 'YES' 'YES' 'NO' 'Low-level ankle SSC conditioning is separated from calf-raise strength.' 'DEFINE_ANKLE_SSC_CONDITIONING_TEMPLATE'
}

$outputDir = Join-Path $RepoRoot 'outputs'
$csvPath = Join-Path $outputDir 'v0.3.5.0_proposed_exercise_metadata_sidecar_v2.csv'
$mdPath = Join-Path $outputDir 'v0.3.5.0_proposed_exercise_metadata_sidecar_v2.md'
$jsonPath = Join-Path $outputDir 'v0.3.5.0_proposed_exercise_metadata_sidecar_v2.json'
$validationMdPath = Join-Path $outputDir 'v0.3.5.0_sidecar_v2_validation_report.md'
$validationCsvPath = Join-Path $outputDir 'v0.3.5.0_sidecar_v2_validation_report.csv'

$rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8
$jsonDocument = [ordered]@{
    schemaVersion = 'v0.3.5.0-proposed-sidecar-v2-draft-1'
    draftOnly = $true
    generatedAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ssK')
    sourceSidecar = 'v0.3.5.0_proposed_exercise_metadata_sidecar.csv'
    seedSha256 = $seedHashBefore
    rowCount = $rows.Count
    rows = $rows
}
$jsonDocument | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

$checks = New-Object 'System.Collections.Generic.List[object]'
$duplicates = @($rows | Group-Object stableKey | Where-Object Count -gt 1)
$deprecatedFamily = @($rows | Where-Object proposedMovementFamily -match 'BICEPS_TRICEPS_ISOLATION_VARIANTS')
$deprecatedSlot = @($rows | Where-Object proposedProgramSlot -match '(^|\|)ARM_ACCESSORY(\||$)')
$deprecatedGroup = @($rows | Where-Object proposedRedundancyGroup -match '(^|\|)ARM_ISOLATION(\||$)')
$curlWithTriceps = @($rows | Where-Object { $_.proposedMovementFamily -eq 'ELBOW_FLEXION_BICEPS_CURL_VARIANTS' -and ($_.proposedDetailedMuscleTags -match 'TRICEPS_BRACHII' -or $_.proposedMuscleContribution -match 'TRICEPS_BRACHII') })
$tricepsWithBiceps = @($rows | Where-Object { $_.proposedMovementFamily -eq 'ELBOW_EXTENSION_TRICEPS_ISOLATION_VARIANTS' -and ($_.proposedDetailedMuscleTags -match 'BICEPS_BRACHII' -or $_.proposedMuscleContribution -match 'BICEPS_BRACHII') })
$forearmIntegrated = @($rows | Where-Object { $_.proposedMovementFamily -eq 'FOREARM_GRIP_ACCESSORY_REVIEW' -and ($_.proposedDetailedMuscleTags -match 'BICEPS_BRACHII|TRICEPS_BRACHII' -or $_.proposedRedundancyGroup -match 'ARM_ISOLATION') })

Add-ValidationCheck $checks 'V2-001' 'Sidecar row count' 215 $rows.Count ($rows.Count -eq 215)
Add-ValidationCheck $checks 'V2-002' 'Duplicate stableKey count' 0 $duplicates.Count ($duplicates.Count -eq 0) ($duplicates.Name -join '|')
Add-ValidationCheck $checks 'V2-003' 'UNMATCHED_INPUT count' 0 @($rows | Where-Object proposalStatus -eq 'UNMATCHED_INPUT').Count (@($rows | Where-Object proposalStatus -eq 'UNMATCHED_INPUT').Count -eq 0)
Add-ValidationCheck $checks 'V2-004' 'Deprecated integrated arm family count' 0 $deprecatedFamily.Count ($deprecatedFamily.Count -eq 0)
Add-ValidationCheck $checks 'V2-005' 'Deprecated ARM_ACCESSORY slot count' 0 $deprecatedSlot.Count ($deprecatedSlot.Count -eq 0)
Add-ValidationCheck $checks 'V2-006' 'Deprecated ARM_ISOLATION redundancy count' 0 $deprecatedGroup.Count ($deprecatedGroup.Count -eq 0)
Add-ValidationCheck $checks 'V2-007' 'Biceps curl rows with triceps major tag' 0 $curlWithTriceps.Count ($curlWithTriceps.Count -eq 0)
Add-ValidationCheck $checks 'V2-008' 'Triceps isolation rows with biceps major tag' 0 $tricepsWithBiceps.Count ($tricepsWithBiceps.Count -eq 0)
Add-ValidationCheck $checks 'V2-009' 'Forearm rows using integrated arm template' 0 $forearmIntegrated.Count ($forearmIntegrated.Count -eq 0)

$blockerRules = [ordered]@{
    ex_516f4456='BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS'
    ex_8e51640a='BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS'
    ex_6a966452='BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS'
    ex_462c760e='BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS'
    ex_c4535de3='BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS'
    ex_deca2b61='BENCH_PRESS_DUMBBELL_PRESS_MACHINE_PRESS_VARIANTS'
    ex_8a3800f1='HAMSTRING_CURL_VARIANTS'
    ex_b78a8f95='LEG_PRESS_KNEE_DOMINANT_MACHINE_VARIANTS'
    ex_d60745b4='LEG_PRESS_KNEE_DOMINANT_MACHINE_VARIANTS'
    ex_377448a9='LUNGE_SPLIT_SQUAT_VARIANTS'
}
$remainingBlockers = New-Object System.Collections.Generic.List[string]
foreach ($entry in $blockerRules.GetEnumerator()) {
    $row = Get-Row $rows $entry.Key
    if ($row.proposedMovementFamily -eq $entry.Value) { $remainingBlockers.Add($entry.Key) }
}
Add-ValidationCheck $checks 'V2-010' 'Known blocker rows remaining in forbidden v1 families' 0 $remainingBlockers.Count ($remainingBlockers.Count -eq 0) ($remainingBlockers -join '|')

$heavySquatKeys = @('ex_cb3c4dc2','ex_ac7df636','ex_ee378da7','ex_85f12271','ex_a611010d','ex_d67e5761')
$heavySquatFailures = @($rows | Where-Object { $_.stableKey -in $heavySquatKeys -and ($_.proposedProgramSlot -eq 'MAIN_LOWER_STRENGTH' -or $_.proposedRedundancyGroup -eq 'SQUAT_PATTERN_HEAVY_LOWER') })
Add-ValidationCheck $checks 'V2-011' 'Bodyweight/goblet/unilateral squat heavy-slot failures' 0 $heavySquatFailures.Count ($heavySquatFailures.Count -eq 0) ($heavySquatFailures.stableKey -join '|')

$unilateralHingeKeys = @('ex_201f6426','ex_e0b1e364','ex_885b629','single_leg_rdl')
$heavyHingeFailures = @($rows | Where-Object { $_.stableKey -in $unilateralHingeKeys -and ($_.proposedProgramSlot -eq 'MAIN_HINGE_STRENGTH' -or $_.proposedRedundancyGroup -eq 'HEAVY_HINGE') })
Add-ValidationCheck $checks 'V2-012' 'Unilateral RDL heavy-hinge failures' 0 $heavyHingeFailures.Count ($heavyHingeFailures.Count -eq 0) ($heavyHingeFailures.stableKey -join '|')

$swing = Get-Row $rows 'ex_7404067c'
Add-ValidationCheck $checks 'V2-013' 'Kettlebell swing remains in deadlift main template' 0 $(if ($swing.proposedMovementFamily -eq 'DEADLIFT_HINGE_VARIANTS' -or $swing.proposedProgramSlot -eq 'MAIN_HINGE_STRENGTH') { 1 } else { 0 }) ($swing.proposedMovementFamily -ne 'DEADLIFT_HINGE_VARIANTS' -and $swing.proposedProgramSlot -ne 'MAIN_HINGE_STRENGTH') $swing.proposedMovementFamily

$ankleKeys = @('ex_a3ddd8ac','ex_e465d1e9','ex_9bd6dddb')
$ankleFailures = @($rows | Where-Object { $_.stableKey -in $ankleKeys -and ($_.proposedMovementFamily -eq 'CALF_RAISE_ANKLE_STIFFNESS_VARIANTS' -or $_.proposedProgramSlot -eq 'ANKLE_CALF_SUPPORT') })
Add-ValidationCheck $checks 'V2-014' 'Line hop/pogo/jump rope calf-strength template failures' 0 $ankleFailures.Count ($ankleFailures.Count -eq 0) ($ankleFailures.stableKey -join '|')

$deprecatedRowSources = @($rows | Where-Object proposedEvidenceSourceGroup -match 'C_HORIZONTAL_ROW_EMG_NEEDS_SOURCE')
Add-ValidationCheck $checks 'V2-015' 'Deprecated horizontal-row source references' 0 $deprecatedRowSources.Count ($deprecatedRowSources.Count -eq 0)

$seedHashAfter = (Get-FileHash -Algorithm SHA256 -LiteralPath $seedPath).Hash
Add-ValidationCheck $checks 'V2-016' 'Seed SHA-256 changed' $seedHashBefore $seedHashAfter ($seedHashBefore -eq $seedHashAfter)

$checks | Export-Csv -LiteralPath $validationCsvPath -NoTypeInformation -Encoding UTF8
$failedChecks = @($checks | Where-Object status -eq 'FAIL')

$statusNames = @('READY_TEMPLATE_CANDIDATE','NEEDS_SOURCE_REVIEW','NEEDS_ROW_REVIEW','NEEDS_TEMPLATE_DEFINITION','NEEDS_STRESS_REVIEW','SPORT_SESSION_REVIEW','DO_NOT_MUTATE_SEED','UNMATCHED_INPUT')
$statusCounts = [ordered]@{}
foreach ($name in $statusNames) { $statusCounts[$name] = @($rows | Where-Object proposalStatus -eq $name).Count }
$armFamilyNames = @('ELBOW_FLEXION_BICEPS_CURL_VARIANTS','ELBOW_FLEXION_BRACHIALIS_BRACHIORADIALIS_VARIANTS','ELBOW_EXTENSION_TRICEPS_ISOLATION_VARIANTS','OVERHEAD_TRICEPS_LONG_HEAD_REVIEW','FOREARM_GRIP_ACCESSORY_REVIEW')

$md = New-Object System.Collections.Generic.List[string]
$md.Add('# Proposed Exercise Metadata Sidecar v2')
$md.Add('')
$md.Add('Draft-only taxonomy correction. V1 files remain preserved; this output must not be used as a seed migration without human approval.')
$md.Add('')
$md.Add('## Status Summary')
$md.Add('')
$md.Add('| proposalStatus | count |')
$md.Add('| --- | ---: |')
foreach ($name in $statusCounts.Keys) { $md.Add("| $name | $($statusCounts[$name]) |") }
$md.Add('')
$md.Add('## Arm Family Split')
$md.Add('')
$md.Add('| family | count |')
$md.Add('| --- | ---: |')
foreach ($name in $armFamilyNames) { $md.Add("| $name | $(@($rows | Where-Object proposedMovementFamily -eq $name).Count) |") }
$md.Add('')
$md.Add('## Corrected Rows')
$md.Add('')
$md.Add('| exerciseName | stableKey | proposedMovementFamily | proposedProgramSlot | proposedRedundancyGroup | proposalStatus |')
$md.Add('| --- | --- | --- | --- | --- | --- |')
$changedKeys = @($bicepsMap.Keys + $neutralMap.Keys + $tricepsMap.Keys + $overheadMap.Keys + $forearmMap.Keys + $blockerRules.Keys + $squatAccessory.Keys + $unilateralHinge.Keys + @('ex_4c76dbb2','ex_7404067c') + $ankleSscMap.Keys | Select-Object -Unique)
foreach ($row in $rows | Where-Object stableKey -in $changedKeys) {
    $md.Add("| $($row.exerciseName) | $($row.stableKey) | $($row.proposedMovementFamily) | $($row.proposedProgramSlot) | $($row.proposedRedundancyGroup) | $($row.proposalStatus) |")
}
$md.Add('')
$md.Add('## All Rows')
$md.Add('')
$md.Add('| exerciseName | stableKey | family | subtype | status |')
$md.Add('| --- | --- | --- | --- | --- |')
foreach ($row in $rows) { $md.Add("| $($row.exerciseName) | $($row.stableKey) | $($row.proposedMovementFamily) | $($row.proposedMovementSubtype) | $($row.proposalStatus) |") }
$md | Set-Content -LiteralPath $mdPath -Encoding UTF8

$validation = New-Object System.Collections.Generic.List[string]
$validation.Add('# Sidecar v2 Validation Report')
$validation.Add('')
$validation.Add("- total sidecar v2 rows: $($rows.Count)")
foreach ($name in $statusCounts.Keys) { $validation.Add("- ${name}: $($statusCounts[$name])") }
$validation.Add("- deprecated family count: $($deprecatedFamily.Count)")
$validation.Add("- BICEPS_TRICEPS_ISOLATION_VARIANTS count: $($deprecatedFamily.Count)")
$validation.Add("- ARM_ISOLATION count: $($deprecatedGroup.Count)")
$validation.Add("- ARM_ACCESSORY count: $($deprecatedSlot.Count)")
foreach ($name in $armFamilyNames) { $validation.Add("- ${name}: $(@($rows | Where-Object proposedMovementFamily -eq $name).Count)") }
$validation.Add("- blocker fixes applied count: $($blockerRules.Count)")
$validation.Add("- remaining blocker count: $($remainingBlockers.Count)")
$validation.Add("- sourceReviewRequired count: $(@($rows | Where-Object sourceReviewRequired -eq 'YES').Count)")
$validation.Add("- safeForSeedMutation YES/CANDIDATE/NO: $(@($rows | Where-Object safeForSeedMutation -eq 'YES').Count)/$(@($rows | Where-Object safeForSeedMutation -eq 'CANDIDATE').Count)/$(@($rows | Where-Object safeForSeedMutation -eq 'NO').Count)")
$validation.Add("- seed SHA-256 before: $seedHashBefore")
$validation.Add("- seed SHA-256 after: $seedHashAfter")
$validation.Add("- optional reaudit inputs found: $($optionalFound.Count)")
$validation.Add("- optional reaudit inputs missing: $($optionalMissing -join ', ')")
$validation.Add('')
$validation.Add('## Checks')
$validation.Add('')
$validation.Add('| checkId | description | expected | actual | status | details |')
$validation.Add('| --- | --- | --- | --- | --- | --- |')
foreach ($check in $checks) { $validation.Add("| $($check.checkId) | $($check.description) | $($check.expected) | $($check.actual) | $($check.status) | $($check.details -replace '\|','/') |") }
$validation.Add('')
$validation.Add('## Scope Guard')
$validation.Add('')
$validation.Add('- Seed, exercises JSON, Kotlin app code, Room schema, user DB, and program generator were not modified.')
$validation.Add('- Full build, network access, and seed migration were not run.')
$validation | Set-Content -LiteralPath $validationMdPath -Encoding UTF8

if ($failedChecks.Count -gt 0) { throw "Sidecar v2 validation failed: $($failedChecks.checkId -join ', ')" }

[pscustomobject]@{
    Rows = $rows.Count
    FailedChecks = $failedChecks.Count
    DeprecatedFamily = $deprecatedFamily.Count
    DeprecatedSlot = $deprecatedSlot.Count
    DeprecatedGroup = $deprecatedGroup.Count
    BlockerFixes = $blockerRules.Count
    RemainingBlockers = $remainingBlockers.Count
    SeedSha256 = $seedHashAfter
    Csv = $csvPath
    Markdown = $mdPath
    Json = $jsonPath
    ValidationMarkdown = $validationMdPath
    ValidationCsv = $validationCsvPath
} | Format-List

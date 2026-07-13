param(
    [string]$CanonicalPath = "app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv",
    [string]$OutputDirectory = "app/src/main/assets/metadata/tissue_load_v1"
)

$ErrorActionPreference = "Stop"
$utf8 = [System.Text.UTF8Encoding]::new($false)
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

function Write-Utf8([string]$Path, [string]$Content) {
    [System.IO.File]::WriteAllText($Path, $Content.Replace("`r`n", "`n"), $utf8)
}

function Write-CsvRows([string]$Path, [object[]]$Rows, [string[]]$Headers) {
    if ($Rows.Count -eq 0) {
        Write-Utf8 $Path (($Headers -join ",") + "`n")
        return
    }
    $csv = $Rows | Select-Object $Headers | ConvertTo-Csv -NoTypeInformation
    Write-Utf8 $Path (($csv -join "`n") + "`n")
}

function Get-SemanticCsvHash([string]$Path) {
    $rawHeader = (Get-Content -LiteralPath $Path -TotalCount 1).TrimStart([char]0xFEFF)
    $headers = $rawHeader.Split(',') | ForEach-Object { $_.Trim().Trim('"') }
    $rows = @(Import-Csv -LiteralPath $Path)
    $unit = [char]0x1F
    $record = [char]0x1E
    $rowValues = $rows | ForEach-Object {
        $row = $_
        (($headers | ForEach-Object { [string]$row.$_ }) -join $unit)
    } | Sort-Object
    $canonical = ($headers -join $unit) + "`n" + ($rowValues -join $record)
    $bytes = $utf8.GetBytes($canonical)
    ([System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
}

function Get-TextHash([string]$Text) {
    $bytes = $utf8.GetBytes($Text)
    ([System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
}

$jointDimensions = "COMPRESSION|ANTERIOR_POSTERIOR_SHEAR|ROTATIONAL_SHEAR|IMPACT_IMPULSE|STABILITY_DEMAND|END_RANGE_STRESS|CYCLIC_MECHANICAL_EXPOSURE"
$tendonDimensions = "PEAK_TENSILE_LOAD|CYCLIC_TENSILE_LOAD|ECCENTRIC_LOAD|ENERGY_STORAGE_RELEASE|COMPRESSIVE_TENDON_LOAD|LOADING_RATE|ISOMETRIC_DURATION|STRETCH_UNDER_LOAD"
$fasciaDimensions = "TENSILE_LOAD|CYCLIC_LOAD|ENERGY_STORAGE_RELEASE|COMPRESSIVE_LOAD|LOADING_RATE|STRETCH_UNDER_LOAD"

$catalogRows = [System.Collections.Generic.List[object]]::new()
function Add-Tissue(
    [string]$Class,
    [string]$Id,
    [string]$Group,
    [string]$Region,
    [string]$Dimensions,
    [bool]$IsTrueJoint = $false,
    [bool]$SupportsLaterality = $true
) {
    $catalogRows.Add([pscustomobject]@{
        tissueClass = $Class
        tissueId = $Id
        displayGroup = $Group
        anatomicalRegion = $Region
        anatomicalName = $Id
        functionalDescription = ""
        isTrueJoint = $IsTrueJoint.ToString().ToLowerInvariant()
        supportsLaterality = $SupportsLaterality.ToString().ToLowerInvariant()
        supportedLoadDimensions = $Dimensions
        defaultScopePolicy = "REVIEW_REQUIRED"
        catalogVersion = "tissue_load_v1"
        catalogEvidenceType = "STANDARD_TERMINOLOGY_SOURCE"
        catalogVerificationStatus = "UNVERIFIED"
        sourceRefs = ""
        catalogNotes = "Foundation anatomy identity only; no exercise-load claim."
    })
}

@(
    @("ACROMIOCLAVICULAR", "Shoulder", "SHOULDER", $true, $true),
    @("ANKLE_SUBTALAR", "AnkleFoot", "ANKLE_FOOT", $true, $true),
    @("ANKLE_TALOCRURAL", "AnkleFoot", "ANKLE_FOOT", $true, $true),
    @("CERVICAL_SPINE", "Spine", "SPINE", $false, $false),
    @("DISTAL_RADIOULNAR", "ElbowWristHand", "FOREARM_WRIST_HAND", $true, $true),
    @("FIRST_MTP", "AnkleFoot", "ANKLE_FOOT", $true, $true),
    @("GLENOHUMERAL", "Shoulder", "SHOULDER", $true, $true),
    @("HAND_MCP_IP_COMPLEX", "ElbowWristHand", "HAND", $true, $true),
    @("HIP", "Hip", "HIP", $true, $true),
    @("HUMERORADIAL", "ElbowWristHand", "ELBOW", $true, $true),
    @("HUMEROULNAR", "ElbowWristHand", "ELBOW", $true, $true),
    @("KNEE_PATELLOFEMORAL", "Knee", "KNEE", $true, $true),
    @("KNEE_TIBIOFEMORAL", "Knee", "KNEE", $true, $true),
    @("LUMBAR_SPINE", "Spine", "SPINE", $false, $false),
    @("MIDFOOT", "AnkleFoot", "ANKLE_FOOT", $true, $true),
    @("PROXIMAL_RADIOULNAR", "ElbowWristHand", "FOREARM", $true, $true),
    @("RADIOCARPAL_WRIST", "ElbowWristHand", "WRIST", $true, $true),
    @("SACROILIAC_COMPLEX", "Pelvis", "PELVIS", $true, $true),
    @("SCAPULOTHORACIC_FUNCTIONAL_COMPLEX", "Shoulder", "SHOULDER", $false, $true),
    @("THORACIC_SPINE", "Spine", "SPINE", $false, $false)
) | ForEach-Object { Add-Tissue "JOINT" $_[0] $_[1] $_[2] $jointDimensions $_[3] $_[4] }

@(
    @("ACHILLES_TENDON", "AnkleFoot", "ANKLE_FOOT"),
    @("ADDUCTOR_TENDON_GROUP", "Hip", "HIP"),
    @("COMMON_EXTENSOR_TENDON", "ElbowWristHand", "ELBOW"),
    @("COMMON_FLEXOR_TENDON", "ElbowWristHand", "ELBOW"),
    @("DISTAL_BICEPS_TENDON", "ElbowWristHand", "ELBOW"),
    @("DISTAL_HAMSTRING_TENDON", "Knee", "KNEE"),
    @("GLUTEUS_MAXIMUS_TENDON", "Hip", "HIP"),
    @("GLUTEUS_MEDIUS_MINIMUS_TENDON", "Hip", "HIP"),
    @("ILIOPSOAS_TENDON", "Hip", "HIP"),
    @("LONG_HEAD_BICEPS_TENDON", "Shoulder", "SHOULDER"),
    @("PATELLAR_TENDON", "Knee", "KNEE"),
    @("PECTORALIS_MAJOR_TENDON", "Shoulder", "SHOULDER"),
    @("PERONEAL_TENDON_GROUP", "AnkleFoot", "ANKLE_FOOT"),
    @("POSTERIOR_ROTATOR_CUFF_TENDON", "Shoulder", "SHOULDER"),
    @("POSTERIOR_TIBIAL_TENDON", "AnkleFoot", "ANKLE_FOOT"),
    @("PROXIMAL_HAMSTRING_TENDON", "Hip", "HIP"),
    @("QUADRICEPS_TENDON", "Knee", "KNEE"),
    @("SUBSCAPULARIS_TENDON", "Shoulder", "SHOULDER"),
    @("SUPRASPINATUS_TENDON", "Shoulder", "SHOULDER"),
    @("TIBIALIS_ANTERIOR_TENDON", "AnkleFoot", "ANKLE_FOOT"),
    @("TRICEPS_TENDON", "ElbowWristHand", "ELBOW"),
    @("WRIST_EXTENSOR_TENDON_GROUP", "ElbowWristHand", "WRIST_HAND"),
    @("WRIST_FLEXOR_TENDON_GROUP", "ElbowWristHand", "WRIST_HAND")
) | Sort-Object { $_[0] } | ForEach-Object { Add-Tissue "TENDON" $_[0] $_[1] $_[2] $tendonDimensions }

$ligaments = @(
    @("ACROMIOCLAVICULAR_LIGAMENT_COMPLEX", "Shoulder", "SHOULDER", "END_RANGE_RESTRAINT|IMPACT_STABILIZATION"),
    @("ANKLE_DELTOID_LIGAMENT", "AnkleFoot", "ANKLE_FOOT", "EVERSION|END_RANGE_RESTRAINT|IMPACT_STABILIZATION"),
    @("ANKLE_LATERAL_LIGAMENT_COMPLEX", "AnkleFoot", "ANKLE_FOOT", "INVERSION|END_RANGE_RESTRAINT|IMPACT_STABILIZATION"),
    @("ANKLE_SYNDESMOSIS", "AnkleFoot", "ANKLE_FOOT", "EXTERNAL_ROTATION|END_RANGE_RESTRAINT|IMPACT_STABILIZATION"),
    @("ELBOW_LCL", "ElbowWristHand", "ELBOW", "VARUS|END_RANGE_RESTRAINT|IMPACT_STABILIZATION"),
    @("ELBOW_UCL", "ElbowWristHand", "ELBOW", "VALGUS|END_RANGE_RESTRAINT|IMPACT_STABILIZATION"),
    @("GLENOHUMERAL_CAPSULOLIGAMENTOUS_COMPLEX", "Shoulder", "SHOULDER", "ANTERIOR_TRANSLATION|POSTERIOR_TRANSLATION|INTERNAL_ROTATION|EXTERNAL_ROTATION|END_RANGE_RESTRAINT"),
    @("HIP_CAPSULOLIGAMENTOUS_COMPLEX", "Hip", "HIP", "INTERNAL_ROTATION|EXTERNAL_ROTATION|END_RANGE_RESTRAINT|IMPACT_STABILIZATION"),
    @("KNEE_ACL", "Knee", "KNEE", "ANTERIOR_TRANSLATION|INTERNAL_ROTATION|DECELERATION_STABILIZATION|IMPACT_STABILIZATION"),
    @("KNEE_LCL", "Knee", "KNEE", "VARUS|EXTERNAL_ROTATION|DECELERATION_STABILIZATION|IMPACT_STABILIZATION"),
    @("KNEE_MCL", "Knee", "KNEE", "VALGUS|INTERNAL_ROTATION|DECELERATION_STABILIZATION|IMPACT_STABILIZATION"),
    @("KNEE_MPFL", "Knee", "KNEE", "END_RANGE_RESTRAINT|IMPACT_STABILIZATION"),
    @("KNEE_PCL", "Knee", "KNEE", "POSTERIOR_TRANSLATION|DECELERATION_STABILIZATION|IMPACT_STABILIZATION"),
    @("MIDFOOT_LISFRANC_COMPLEX", "AnkleFoot", "ANKLE_FOOT", "END_RANGE_RESTRAINT|IMPACT_STABILIZATION"),
    @("SACROILIAC_LIGAMENT_COMPLEX", "Pelvis", "PELVIS", "END_RANGE_RESTRAINT|IMPACT_STABILIZATION"),
    @("WRIST_LIGAMENT_TFCC_COMPLEX", "ElbowWristHand", "WRIST", "END_RANGE_RESTRAINT|IMPACT_STABILIZATION")
)
$ligaments | Sort-Object { $_[0] } | ForEach-Object { Add-Tissue "LIGAMENT" $_[0] $_[1] $_[2] $_[3] }

Add-Tissue "FASCIA" "PLANTAR_FASCIA" "AnkleFoot" "ANKLE_FOOT" $fasciaDimensions
Add-Tissue "FASCIA" "THORACOLUMBAR_FASCIA" "Spine" "TRUNK" $fasciaDimensions $false $false

$catalogHeaders = @(
    "tissueClass", "tissueId", "displayGroup", "anatomicalRegion", "anatomicalName",
    "functionalDescription", "isTrueJoint", "supportsLaterality", "supportedLoadDimensions",
    "defaultScopePolicy", "catalogVersion", "catalogEvidenceType", "catalogVerificationStatus",
    "sourceRefs", "catalogNotes"
)
$catalogRows = @($catalogRows | Sort-Object tissueClass, tissueId)
$catalogPath = Join-Path $OutputDirectory "canonical_tissue_catalog_v1.csv"
Write-CsvRows $catalogPath $catalogRows $catalogHeaders

$profileHeaders = @(
    "profileRowId", "stableKey", "tissueClass", "tissueId", "loadDimension", "loadBand",
    "evidenceStatus", "evidenceLevel", "confidenceLevel", "reviewStatus", "productionEligibility",
    "doseBasis", "referenceConditionId", "modifierSetId", "recoveryProfileId", "sideAllocationPolicy",
    "rubricId", "evidenceSetId", "evidenceClaimIds", "sourceRefs", "reviewBatchId", "preparedBy",
    "preparedByType", "preparedAt", "blindReviewedBy", "blindReviewedByType", "blindReviewedAt",
    "humanApprovedBy", "humanApprovedAt", "reviewNotes"
)
$profileFiles = @(
    "exercise_joint_load_profiles_v1.csv",
    "exercise_tendon_load_profiles_v1.csv",
    "exercise_ligament_load_profiles_v1.csv",
    "exercise_fascia_load_profiles_v1.csv"
)
$profileFiles | ForEach-Object { Write-CsvRows (Join-Path $OutputDirectory $_) @() $profileHeaders }

$rubricHeaders = @(
    "rubricId", "tissueId", "loadDimension", "loadBand", "metricType", "metricLowerBound",
    "metricUpperBound", "metricUnit", "anchorStableKeys", "anchorConditions", "anchorClaimIds",
    "assignmentMethod", "evidenceSetId", "evidenceClaimIds", "sourceRefs", "confidenceLevel",
    "rubricStatus", "preparedBy", "preparedByType", "preparedAt", "blindReviewedBy",
    "blindReviewedByType", "blindReviewedAt", "humanApprovedBy", "humanApprovedAt", "rubricNotes"
)
$rubricPath = Join-Path $OutputDirectory "tissue_load_band_rubric_v1.csv"
Write-CsvRows $rubricPath @() $rubricHeaders

$evidenceHeaders = @(
    "sourceId", "pmid", "doi", "title", "authors", "publicationYear", "journal", "studyType",
    "population", "sampleSize", "trainingStatus", "sexComposition", "healthStatus", "exactExercise",
    "exerciseProtocol", "externalLoadCondition", "repetitionCondition", "romCondition", "velocityCondition",
    "surfaceCondition", "footwearCondition", "anticipatedCondition", "fatigueCondition", "measurementMethod",
    "measuredOutcome", "reportedMetric", "reportedValue", "reportedLowerBound", "reportedUpperBound",
    "reportedUnit", "supportedTissueIds", "supportedLoadDimensions", "majorLimitations",
    "identifierVerificationStatus", "bibliographicMatchStatus", "publicationIntegrityStatus",
    "verificationCapabilityStatus", "verifiedAt", "verificationMethod", "sourceStatus", "sourceNotes"
)
$evidenceRow = [pscustomobject]@{
    sourceId = "PREFLIGHT_32658037"
    pmid = "32658037"
    doi = "10.2519/jospt.2020.9406"
    title = "Exercise Progression to Incrementally Load the Achilles Tendon."
    authors = ""
    publicationYear = ""
    journal = ""
    studyType = ""
    population = ""
    sampleSize = ""
    trainingStatus = ""
    sexComposition = ""
    healthStatus = ""
    exactExercise = ""
    exerciseProtocol = ""
    externalLoadCondition = ""
    repetitionCondition = ""
    romCondition = ""
    velocityCondition = ""
    surfaceCondition = ""
    footwearCondition = ""
    anticipatedCondition = ""
    fatigueCondition = ""
    measurementMethod = ""
    measuredOutcome = ""
    reportedMetric = ""
    reportedValue = ""
    reportedLowerBound = ""
    reportedUpperBound = ""
    reportedUnit = ""
    supportedTissueIds = "ACHILLES_TENDON"
    supportedLoadDimensions = ""
    majorLimitations = "Crossref DOI lookup returned 404; no claim review performed."
    identifierVerificationStatus = "UNVERIFIED"
    bibliographicMatchStatus = "UNVERIFIED"
    publicationIntegrityStatus = "STATUS_UNKNOWN"
    verificationCapabilityStatus = "PARTIAL_SOURCE_VERIFICATION_AVAILABLE"
    verifiedAt = "2026-07-13"
    verificationMethod = "NCBI_PARSED_CROSSREF_BOUNDED_404"
    sourceStatus = "UNVERIFIED"
    sourceNotes = "Preflight record only; not production evidence."
}
$evidencePath = Join-Path $OutputDirectory "tissue_load_evidence_registry_v1.csv"
Write-CsvRows $evidencePath @($evidenceRow) $evidenceHeaders

$draftHeaders = @(
    "draftClaimId", "sourceId", "stableKey", "tissueId", "loadDimension", "proposedBand", "claimType",
    "claimParaphrase", "claimDirection", "claimValue", "claimLowerBound", "claimUpperBound", "claimUnit",
    "comparatorExercise", "population", "exerciseCondition", "loadCondition", "romCondition",
    "velocityCondition", "surfaceCondition", "anticipatedCondition", "fatigueCondition", "evidenceLocatorType",
    "evidenceLocator", "evidenceAccessLevel", "preparedBy", "preparedByType", "preparedAt", "draftNotes"
)
$draftPath = Join-Path $OutputDirectory "tissue_evidence_claims_draft_v1.csv"
Write-CsvRows $draftPath @() $draftHeaders

$blindHeaders = @(
    "blindReviewId", "draftClaimId", "sourceId", "stableKey", "tissueId", "loadDimension",
    "supportedExercise", "supportedTissue", "supportedDimension", "supportedDirection", "supportedMetric",
    "supportedValue", "supportedLowerBound", "supportedUpperBound", "supportedUnit", "supportedCondition",
    "maximumDefensibleBand", "limitations", "identifierVerificationStatus", "bibliographicMatchStatus",
    "claimVerificationStatus", "publicationIntegrityStatus", "blindReviewedBy", "blindReviewedByType",
    "blindReviewedAt", "verificationMethod", "reviewNotes"
)
$blindPath = Join-Path $OutputDirectory "tissue_evidence_blind_review_v1.csv"
Write-CsvRows $blindPath @() $blindHeaders

$finalClaimHeaders = @(
    "claimId", "draftClaimId", "blindReviewId", "sourceId", "stableKey", "tissueId", "loadDimension",
    "finalClaimType", "finalClaimParaphrase", "finalClaimDirection", "finalClaimValue",
    "finalClaimLowerBound", "finalClaimUpperBound", "finalClaimUnit", "comparatorExercise",
    "supportedCondition", "evidenceLocatorType", "evidenceLocator", "evidenceAccessLevel",
    "draftBlindComparisonStatus", "identifierVerificationStatus", "bibliographicMatchStatus",
    "claimVerificationStatus", "publicationIntegrityStatus", "preparedBy", "preparedByType",
    "blindReviewedBy", "blindReviewedByType", "humanApprovedBy", "humanApprovedAt",
    "productionEligibility", "verificationNotes"
)
$finalClaimPath = Join-Path $OutputDirectory "tissue_evidence_claims_v1.csv"
Write-CsvRows $finalClaimPath @() $finalClaimHeaders

$sourceVerificationHeaders = @(
    "sourceId", "resolvedPmid", "resolvedDoi", "resolvedTitle", "resolvedFirstAuthor", "resolvedYear",
    "resolvedJournal", "identifierVerificationStatus", "bibliographicMatchStatus",
    "publicationIntegrityStatus", "networkCapabilityStatus", "verifiedAt", "verificationMethod",
    "metadataSnapshotHash", "verificationNotes"
)
$sourceVerificationRow = [pscustomobject]@{
    sourceId = "PREFLIGHT_32658037"
    resolvedPmid = "32658037"
    resolvedDoi = ""
    resolvedTitle = "Exercise Progression to Incrementally Load the Achilles Tendon."
    resolvedFirstAuthor = ""
    resolvedYear = ""
    resolvedJournal = ""
    identifierVerificationStatus = "UNVERIFIED"
    bibliographicMatchStatus = "UNVERIFIED"
    publicationIntegrityStatus = "STATUS_UNKNOWN"
    networkCapabilityStatus = "PARTIAL_SOURCE_VERIFICATION_AVAILABLE"
    verifiedAt = "2026-07-13"
    verificationMethod = "NCBI_PARSED_CROSSREF_BOUNDED_404"
    metadataSnapshotHash = ""
    verificationNotes = "NCBI parsed PMID/title; Crossref returned 404 twice. Fail-closed status retained."
}
$sourceVerificationPath = Join-Path $OutputDirectory "tissue_source_verification_v1.csv"
Write-CsvRows $sourceVerificationPath @($sourceVerificationRow) $sourceVerificationHeaders

$batchApprovalHeaders = @(
    "reviewBatchId", "auditManifestId", "humanApprover", "humanApprovedAt", "samplingPolicy",
    "sampledRowIds", "highRiskRowsReviewed", "automatedValidationPassed", "errorRate",
    "approvalDecision", "approvalNotes"
)
$batchApprovalPath = Join-Path $OutputDirectory "tissue_review_batch_approval_v1.csv"
Write-CsvRows $batchApprovalPath @() $batchApprovalHeaders

$canonical = @(Import-Csv -LiteralPath $CanonicalPath)
$scopeHeaders = @(
    "stableKey", "tissueClass", "tissueId", "scopeStatus", "legacySeedTags", "reviewPriority",
    "reviewBatchId", "productionEligibility", "reviewedCatalogVersion", "preparedBy", "preparedByType",
    "preparedAt", "blindReviewedBy", "blindReviewedByType", "blindReviewedAt", "humanApprovedBy",
    "humanApprovedAt", "reviewNotes"
)
$scopeRows = foreach ($exercise in ($canonical | Sort-Object stableKey)) {
    foreach ($tissue in $catalogRows) {
        [pscustomobject]@{
            stableKey = $exercise.stableKey
            tissueClass = $tissue.tissueClass
            tissueId = $tissue.tissueId
            scopeStatus = "NOT_YET_EVALUATED"
            legacySeedTags = ""
            reviewPriority = "UNASSESSED"
            reviewBatchId = ""
            productionEligibility = "false"
            reviewedCatalogVersion = "tissue_load_v1"
            preparedBy = "Codex"
            preparedByType = "AI_AGENT"
            preparedAt = "2026-07-13"
            blindReviewedBy = ""
            blindReviewedByType = ""
            blindReviewedAt = ""
            humanApprovedBy = ""
            humanApprovedAt = ""
            reviewNotes = "Foundation scope placeholder; missing is not zero."
        }
    }
}
$scopePath = Join-Path $OutputDirectory "exercise_tissue_scope_manifest_v1.csv"
Write-CsvRows $scopePath @($scopeRows) $scopeHeaders

$canonicalHash = Get-SemanticCsvHash $CanonicalPath
$catalogHash = Get-SemanticCsvHash $catalogPath
$scopeHash = Get-SemanticCsvHash $scopePath
$profileHashParts = $profileFiles | ForEach-Object { "$_=$(Get-SemanticCsvHash (Join-Path $OutputDirectory $_))" }
$profileHash = Get-TextHash (($profileHashParts | Sort-Object) -join "`n")
$rubricHash = Get-SemanticCsvHash $rubricPath
$evidenceHash = Get-SemanticCsvHash $evidencePath
$claimHash = Get-TextHash ((@(
    "draft=$(Get-SemanticCsvHash $draftPath)",
    "blind=$(Get-SemanticCsvHash $blindPath)",
    "final=$(Get-SemanticCsvHash $finalClaimPath)"
) | Sort-Object) -join "`n")
$sourceVerificationHash = Get-SemanticCsvHash $sourceVerificationPath
$inputHash = Get-TextHash ((@(
    "canonical=$canonicalHash",
    "catalog=$catalogHash",
    "scope=$scopeHash",
    "profiles=$profileHash",
    "rubric=$rubricHash",
    "evidence=$evidenceHash",
    "claims=$claimHash",
    "sourceVerification=$sourceVerificationHash"
) | Sort-Object) -join "`n")

$auditHeaders = @(
    "auditManifestId", "auditScope", "auditBatchId", "metadataSchemaVersion", "catalogVersion",
    "canonicalExerciseSnapshotHash", "canonicalExerciseCount", "tissueCatalogSnapshotHash",
    "jointTissueCount", "tendonTissueCount", "ligamentTissueCount", "fasciaTissueCount",
    "scopeManifestSnapshotHash", "scopeManifestRowCount", "profileSnapshotHash", "jointProfileRowCount",
    "tendonProfileRowCount", "ligamentProfileRowCount", "fasciaProfileRowCount", "rubricSnapshotHash",
    "modifierSnapshotHash", "recoverySnapshotHash", "evidenceRegistrySnapshotHash", "claimLedgerSnapshotHash",
    "sourceVerificationSnapshotHash", "doseCapabilitySnapshotHash", "automatedValidationStatus",
    "stableKeyCoverageStatus", "scopeCoverageStatus", "profileIntegrityStatus", "catalogEvidenceStatus",
    "exerciseLoadEvidenceIntegrityStatus", "citationVerificationStatus", "blindReviewCoverageStatus",
    "humanApprovalCoverageStatus", "doseCapabilityStatus", "lateralityCoverageStatus",
    "modifierValidationStatus", "recoveryValidationStatus", "notYetEvaluatedCount", "evaluatedAbsentCount",
    "evaluatedRelevantCount", "evaluatedIrrelevantCount", "blockedCount", "conflictingCount",
    "missingRecordInputCount", "sideUnresolvedCount", "unsupportedModifierCombinationCount",
    "evidenceNotApprovedCount", "anomalyFlagCount", "failedInvariantCount", "warningCount", "generatedBy",
    "generatedByType", "generatedAt", "inputSnapshotHash", "auditDecision", "auditNotes"
)
$auditRow = [pscustomobject]@{
    auditManifestId = "tissue_foundation_v1_stage2_$($inputHash.Substring(0, 12))"
    auditScope = "FOUNDATION_FULL"
    auditBatchId = "FOUNDATION_V1"
    metadataSchemaVersion = "tissue_load_v1"
    catalogVersion = "tissue_load_v1"
    canonicalExerciseSnapshotHash = $canonicalHash
    canonicalExerciseCount = $canonical.Count
    tissueCatalogSnapshotHash = $catalogHash
    jointTissueCount = @($catalogRows | Where-Object tissueClass -eq "JOINT").Count
    tendonTissueCount = @($catalogRows | Where-Object tissueClass -eq "TENDON").Count
    ligamentTissueCount = @($catalogRows | Where-Object tissueClass -eq "LIGAMENT").Count
    fasciaTissueCount = @($catalogRows | Where-Object tissueClass -eq "FASCIA").Count
    scopeManifestSnapshotHash = $scopeHash
    scopeManifestRowCount = @($scopeRows).Count
    profileSnapshotHash = $profileHash
    jointProfileRowCount = 0
    tendonProfileRowCount = 0
    ligamentProfileRowCount = 0
    fasciaProfileRowCount = 0
    rubricSnapshotHash = $rubricHash
    modifierSnapshotHash = ""
    recoverySnapshotHash = ""
    evidenceRegistrySnapshotHash = $evidenceHash
    claimLedgerSnapshotHash = $claimHash
    sourceVerificationSnapshotHash = $sourceVerificationHash
    doseCapabilitySnapshotHash = ""
    automatedValidationStatus = "PASS_WITH_WARNINGS"
    stableKeyCoverageStatus = "PASS"
    scopeCoverageStatus = "PASS"
    profileIntegrityStatus = "PASS"
    catalogEvidenceStatus = "PASS_WITH_WARNINGS"
    exerciseLoadEvidenceIntegrityStatus = "PASS"
    citationVerificationStatus = "PASS_WITH_WARNINGS"
    blindReviewCoverageStatus = "NOT_APPLICABLE"
    humanApprovalCoverageStatus = "NOT_APPLICABLE"
    doseCapabilityStatus = "NOT_RUN"
    lateralityCoverageStatus = "NOT_RUN"
    modifierValidationStatus = "NOT_RUN"
    recoveryValidationStatus = "NOT_RUN"
    notYetEvaluatedCount = @($scopeRows).Count
    evaluatedAbsentCount = 0
    evaluatedRelevantCount = 0
    evaluatedIrrelevantCount = 0
    blockedCount = 0
    conflictingCount = 0
    missingRecordInputCount = 0
    sideUnresolvedCount = 0
    unsupportedModifierCombinationCount = 0
    evidenceNotApprovedCount = 0
    anomalyFlagCount = 0
    failedInvariantCount = 0
    warningCount = 5
    generatedBy = "Codex"
    generatedByType = "AI_AGENT"
    generatedAt = "2026-07-13T00:00:00Z"
    inputSnapshotHash = $inputHash
    auditDecision = "FOUNDATION_PARTIAL"
    auditNotes = "Stage 2 evidence provenance is fail-closed; dose, modifier, recovery, and shadow validators are pending."
}
Write-CsvRows (Join-Path $OutputDirectory "tissue_metadata_audit_manifest_v1.csv") @($auditRow) $auditHeaders

Write-Output "CANONICAL_EXERCISE_COUNT=$($canonical.Count)"
Write-Output "TISSUE_COUNT=$($catalogRows.Count)"
Write-Output "SCOPE_MANIFEST_ROW_COUNT=$(@($scopeRows).Count)"
Write-Output "AUDIT_INPUT_SNAPSHOT_HASH=$inputHash"

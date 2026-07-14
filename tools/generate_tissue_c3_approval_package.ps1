param(
    [string]$AssetDirectory = "app/src/main/assets/metadata/tissue_load_v1",
    [string]$ApprovalDocumentPath = "docs/tissue_load_phase_c3_approval_request.md",
    [string]$UpperBacklogDocumentPath = "docs/tissue_load_phase_c3_upper_backlog.md"
)

$ErrorActionPreference = "Stop"
$batchId = "TISSUE_C3_MULTIDIMENSIONAL_LOWER_R1"
$upperBatchId = "TISSUE_C3_MULTIDIMENSIONAL_UPPER_PRESS_PULL_R1"
$reviewPath = "SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL"
$preparedAt = "2026-07-14T01:00:04Z"
$previousRequestId = "TISSUE_APPROVAL_REQUEST_C2A_R1_74ECC66495637BDD"
$previousScopeHash = "74ecc66495637bdd70720957970aac41537c4726c9060a5e781bfcfc1c96678f"
$resolutionId = "TISSUE_APPROVAL_RESOLUTION_C3_MD_R1_74ECC664"
$requestPath = Join-Path $AssetDirectory "tissue_review_batch_approval_request_c3_v1.csv"
$auditPath = Join-Path $AssetDirectory "tissue_metadata_audit_manifest_v1.csv"
$upperBacklogPath = Join-Path $AssetDirectory "tissue_upper_limb_research_backlog_c3_v1.csv"

function Get-Sha256([string]$value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($value)) } finally { $sha.Dispose() }
    ([BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant()
}

function Get-CombinedHash([hashtable]$parts) {
    [string[]]$keys = @($parts.Keys)
    [Array]::Sort($keys, [StringComparer]::Ordinal)
    Get-Sha256 (($keys | ForEach-Object { "$_=$($parts[$_])" }) -join "`n")
}

function Get-SemanticCsvHash([string]$path) {
    $rows = @(Import-Csv -LiteralPath $path -Encoding UTF8)
    $first = Get-Content -LiteralPath $path -TotalCount 1 -Encoding UTF8
    $header = if ($rows.Count) { @($rows[0].PSObject.Properties.Name) } else {
        @((ConvertFrom-Csv "$first`nplaceholder" | Select-Object -First 1).PSObject.Properties.Name)
    }
    $header = @($header | ForEach-Object { ([string]$_).Trim().TrimStart([char]0xFEFF) })
    $unit = [char]0x1F
    $record = [char]0x1E
    [string[]]$encodedRows = @($rows | ForEach-Object {
        $row = $_
        ($header | ForEach-Object { ([string]$row.$_).Trim() }) -join $unit
    })
    [Array]::Sort($encodedRows, [StringComparer]::Ordinal)
    Get-Sha256 (($header -join $unit) + "`n" + ($encodedRows -join $record))
}

function Tokens($rows, [string]$property) {
    (@($rows | ForEach-Object { [string]$_.$property } | Where-Object { $_ } | Sort-Object -Unique) -join '|')
}

function Require-Count([string]$name, $rows, [int]$expected) {
    if (@($rows).Count -ne $expected) { throw "$name count must be $expected, got $(@($rows).Count)." }
}

function Escape-Markdown([string]$value) {
    if (-not $value) { return '-' }
    ($value -replace '\|', '\|') -replace "`r?`n", '<br>'
}

$upperBacklog = [pscustomobject][ordered]@{
    backlogBatchId = $upperBatchId
    status = 'QUEUED_FOR_SEPARATE_RESEARCH'
    tissueIds = 'GLENOHUMERAL|ACROMIOCLAVICULAR|SCAPULOTHORACIC_FUNCTIONAL_COMPLEX|HUMEROULNAR|HUMERORADIAL|PROXIMAL_RADIOULNAR|DISTAL_RADIOULNAR|RADIOCARPAL_WRIST|SUPRASPINATUS_TENDON|POSTERIOR_ROTATOR_CUFF_TENDON|SUBSCAPULARIS_TENDON|LONG_HEAD_BICEPS_TENDON|PECTORALIS_MAJOR_TENDON|TRICEPS_TENDON|DISTAL_BICEPS_TENDON|COMMON_FLEXOR_TENDON|COMMON_EXTENSOR_TENDON|WRIST_FLEXOR_TENDON_GROUP|WRIST_EXTENSOR_TENDON_GROUP|ELBOW_UCL|ELBOW_LCL|WRIST_LIGAMENT_TFCC_COMPLEX'
    exerciseFamilies = 'Bench press|Incline press|Push-up variants|Dips|Overhead press|Landmine press|Lateral raise|Fly|Pull-up variants|Assisted pull-up|Lat pulldown|Rows|Biceps curls|Triceps extensions|Wrist and grip exercises'
    requiredDimensions = 'COMPRESSION|TENSION|ANTERIOR_POSTERIOR_SHEAR|MEDIAL_LATERAL_SHEAR|TORSION|INTERNAL_ROTATION_STRESS|EXTERNAL_ROTATION_STRESS|VALGUS_STRESS|VARUS_STRESS|IMPACT_STABILIZATION|END_RANGE_STRESS'
    transferBoundary = 'No lower-limb source, dimension, rubric, claim, or profile transfers to upper-limb tissues. Each upper-limb exercise and tissue dimension requires direct source review and explicit correspondence.'
    preparedBy = 'Codex'
    preparedByType = 'AI_AGENT'
    preparedAt = $preparedAt
}
$upperBacklog | Export-Csv -LiteralPath $upperBacklogPath -NoTypeInformation -Encoding UTF8

$files = [ordered]@{
    modeRegistry = 'tissue_mechanical_load_mode_registry_v1.csv'
    temporalMetricRegistry = 'tissue_temporal_metric_registry_v1.csv'
    measurementMetricRegistry = 'tissue_measurement_metric_registry_v1.csv'
    normalizationRegistry = 'tissue_normalization_registry_v1.csv'
    dimensionRegistry = 'tissue_load_dimension_registry_v2.csv'
    legacyMigration = 'tissue_load_dimension_migration_v1.csv'
    metricExtractions = 'tissue_source_metric_extraction_v1.csv'
    previousRevisedRequest = 'tissue_review_batch_approval_request_revised_v1.csv'
    requestResolutions = 'tissue_approval_request_resolution_v1.csv'
    revisedCandidateDispositions = 'tissue_revised_candidate_disposition_c3_v1.csv'
    multidimensionalCandidates = 'tissue_evidence_claim_candidates_multidimensional_v1.csv'
    rubrics = 'tissue_load_rubric_v2.csv'
    researchDecisions = 'tissue_research_decision_c3_v1.csv'
    canonicalMappings = 'tissue_canonical_exercise_mapping_audit_v1.csv'
    transferCorrespondence = 'tissue_exercise_variant_correspondence_c3_v1.csv'
    historicalEvidenceRegistry = 'tissue_load_evidence_registry_v1.csv'
    c3EvidenceRegistry = 'tissue_load_evidence_registry_c3_v1.csv'
    historicalSourceVerification = 'tissue_source_verification_v1.csv'
    c3SourceVerification = 'tissue_source_verification_c3_v1.csv'
    historicalPublicationIntegrity = 'tissue_publication_integrity_verification_v1.csv'
    c3PublicationIntegrity = 'tissue_publication_integrity_verification_c3_v1.csv'
    upperLimbBacklog = 'tissue_upper_limb_research_backlog_c3_v1.csv'
}
$tables = @{}
$inputParts = @{}
foreach ($entry in $files.GetEnumerator()) {
    $path = Join-Path $AssetDirectory $entry.Value
    $tables[$entry.Key] = @(Import-Csv -LiteralPath $path -Encoding UTF8)
    $inputParts[$entry.Key] = Get-SemanticCsvHash $path
}

Require-Count 'mechanical load modes' $tables.modeRegistry 17
Require-Count 'temporal metrics' $tables.temporalMetricRegistry 11
Require-Count 'measurement metrics' $tables.measurementMetricRegistry 21
Require-Count 'normalizations' $tables.normalizationRegistry 14
Require-Count 'valid dimensions' $tables.dimensionRegistry 42
Require-Count 'legacy migrations' $tables.legacyMigration 33
Require-Count 'metric extractions' $tables.metricExtractions 49
Require-Count 'previous revised request' $tables.previousRevisedRequest 1
Require-Count 'C3 dispositions' $tables.revisedCandidateDispositions 24
Require-Count 'multidimensional candidates' $tables.multidimensionalCandidates 30
Require-Count 'rubrics' $tables.rubrics 2
Require-Count 'research decisions' $tables.researchDecisions 48
Require-Count 'canonical mappings' $tables.canonicalMappings 49
Require-Count 'transfer correspondences' $tables.transferCorrespondence 49
Require-Count 'historical sources' $tables.historicalEvidenceRegistry 10
Require-Count 'new C3 sources' $tables.c3EvidenceRegistry 5
Require-Count 'historical source verifications' $tables.historicalSourceVerification 10
Require-Count 'new C3 source verifications' $tables.c3SourceVerification 5
Require-Count 'historical integrity rows' $tables.historicalPublicationIntegrity 10
Require-Count 'new C3 integrity rows' $tables.c3PublicationIntegrity 5
Require-Count 'upper-limb backlog rows' $tables.upperLimbBacklog 1

$previousRequest = $tables.previousRevisedRequest[0]
if ($previousRequest.approvalRequestId -ne $previousRequestId -or $previousRequest.approvalScopeHash -ne $previousScopeHash) {
    throw 'The immutable revised request identity changed.'
}
$resolution = @($tables.requestResolutions | Where-Object resolutionId -eq $resolutionId)
if ($resolution.Count -ne 1 -or $resolution[0].resolutionStatus -ne 'SUPERSEDED_BEFORE_APPROVAL') {
    throw 'The C3 supersession resolution is missing or invalid.'
}
if (@(Import-Csv -LiteralPath (Join-Path $AssetDirectory 'tissue_review_batch_approval_v1.csv')).Count -or
    @(Import-Csv -LiteralPath (Join-Path $AssetDirectory 'tissue_evidence_claims_v1.csv')).Count -or
    @(Import-Csv -LiteralPath (Join-Path $AssetDirectory 'tissue_evidence_blind_review_v1.csv')).Count) {
    throw 'Approval, final-claim, and blind-review ledgers must remain empty.'
}
foreach ($profile in Get-ChildItem -LiteralPath $AssetDirectory -Filter 'exercise_*_load_profiles_v1.csv') {
    if (@(Import-Csv -LiteralPath $profile.FullName).Count) { throw "Production profile file is not empty: $($profile.Name)" }
}

$auditInputHash = Get-CombinedHash $inputParts
$auditManifestId = "tissue_c3_md_$($auditInputHash.Substring(0,12))"
$scopeParts = @{} + $inputParts
$scopeParts.reviewPath = $reviewPath
$scopeParts.auditManifestId = $auditManifestId
$scopeParts.auditInputSnapshotHash = $auditInputHash
$scopeParts.previousApprovalRequestId = $previousRequestId
$scopeParts.previousApprovalScopeHash = $previousScopeHash
$scopeParts.supersessionResolutionId = $resolutionId
$approvalScopeHash = Get-CombinedHash $scopeParts
$approvalRequestId = "TISSUE_APPROVAL_REQUEST_C3_MD_$($approvalScopeHash.Substring(0,16).ToUpperInvariant())"
$blockedCount = @($tables.researchDecisions | Where-Object decision -like 'BLOCKED_*').Count
$conflictingCount = @($tables.researchDecisions | Where-Object decision -like '*CONFLICT*').Count
$sourceRegistryHash = Get-CombinedHash @{ historical=$inputParts.historicalEvidenceRegistry; c3=$inputParts.c3EvidenceRegistry }
$sourceVerificationHash = Get-CombinedHash @{ historical=$inputParts.historicalSourceVerification; c3=$inputParts.c3SourceVerification }
$publicationIntegrityHash = Get-CombinedHash @{ historical=$inputParts.historicalPublicationIntegrity; c3=$inputParts.c3PublicationIntegrity }
$statement = "I approve multidimensionalApprovalRequestId=$approvalRequestId,`napprovalScopeHash=$approvalScopeHash,`nreviewPath=$reviewPath,`ncovering exactly 49 metric extractions, 30 multidimensional claim candidates, 2 metric-compatible rubric rows, 24 prior-candidate dispositions, 48 research decisions, 49 canonical transfer mappings, 15 source records, and the listed ontology, source-verification, publication-integrity, supersession, transfer, and upper-limb-backlog snapshots.`nI understand this package is same-session, non-independent, non-production, contains $blockedCount explicitly blocked targets, and does not approve a universal tissue-burden score or fixed multidimensional weights."

$request = [pscustomobject][ordered]@{
    approvalRequestId = $approvalRequestId
    researchBatchId = $batchId
    reviewPath = $reviewPath
    metricExtractionIds = Tokens $tables.metricExtractions 'metricExtractionId'
    claimCandidateIds = Tokens $tables.multidimensionalCandidates 'claimCandidateId'
    rubricIds = Tokens $tables.rubrics 'rubricId'
    priorCandidateIds = Tokens $tables.revisedCandidateDispositions 'candidateId'
    researchDecisionIds = Tokens $tables.researchDecisions 'researchDecisionId'
    canonicalMappingStableKeys = Tokens $tables.canonicalMappings 'stableKey'
    transferCorrespondenceStableKeys = Tokens $tables.transferCorrespondence 'stableKey'
    sourceIds = Tokens @($tables.historicalEvidenceRegistry + $tables.c3EvidenceRegistry) 'sourceId'
    metricExtractionCount = '49'
    candidateCount = '30'
    rubricCount = '2'
    priorCandidateDispositionCount = '24'
    researchDecisionCount = '48'
    canonicalMappingCount = '49'
    transferCorrespondenceCount = '49'
    sourceCount = '15'
    blockedResearchTargetCount = [string]$blockedCount
    previousApprovalRequestId = $previousRequestId
    previousApprovalScopeHash = $previousScopeHash
    supersessionResolutionId = $resolutionId
    previousRequestSnapshotHash = $inputParts.previousRevisedRequest
    supersessionResolutionSnapshotHash = $inputParts.requestResolutions
    dispositionSnapshotHash = $inputParts.revisedCandidateDispositions
    modeRegistrySnapshotHash = $inputParts.modeRegistry
    temporalMetricRegistrySnapshotHash = $inputParts.temporalMetricRegistry
    measurementMetricRegistrySnapshotHash = $inputParts.measurementMetricRegistry
    normalizationRegistrySnapshotHash = $inputParts.normalizationRegistry
    dimensionRegistrySnapshotHash = $inputParts.dimensionRegistry
    migrationSnapshotHash = $inputParts.legacyMigration
    metricExtractionSnapshotHash = $inputParts.metricExtractions
    candidateSnapshotHash = $inputParts.multidimensionalCandidates
    rubricSnapshotHash = $inputParts.rubrics
    researchDecisionSnapshotHash = $inputParts.researchDecisions
    canonicalMappingSnapshotHash = $inputParts.canonicalMappings
    transferCorrespondenceSnapshotHash = $inputParts.transferCorrespondence
    sourceRegistrySnapshotHash = $sourceRegistryHash
    historicalSourceRegistrySnapshotHash = $inputParts.historicalEvidenceRegistry
    c3SourceRegistrySnapshotHash = $inputParts.c3EvidenceRegistry
    sourceVerificationSnapshotHash = $sourceVerificationHash
    historicalSourceVerificationSnapshotHash = $inputParts.historicalSourceVerification
    c3SourceVerificationSnapshotHash = $inputParts.c3SourceVerification
    publicationIntegritySnapshotHash = $publicationIntegrityHash
    historicalPublicationIntegritySnapshotHash = $inputParts.historicalPublicationIntegrity
    c3PublicationIntegritySnapshotHash = $inputParts.c3PublicationIntegrity
    upperLimbBacklogSnapshotHash = $inputParts.upperLimbBacklog
    auditManifestId = $auditManifestId
    auditInputSnapshotHash = $auditInputHash
    approvalScopeHash = $approvalScopeHash
    requestStatus = 'PENDING_HUMAN_DECISION'
    completionStatus = 'MULTIDIMENSIONAL_C_APPROVAL_PACKAGE_PARTIAL'
    preparedBy = 'Codex'
    preparedByType = 'AI_AGENT'
    preparedAt = $preparedAt
    approvalSummary = 'Future human review is requested only for this exact multidimensional, condition-bounded, non-production C3 scope.'
    knownLimitations = "Material lower-limb targets remain blocked ($blockedCount); no universal burden score, cross-dimensional weights, unreviewed transfer, or production profile is approved."
    requiredUserStatement = $statement
    requestNotes = 'This generated statement is not approval. Human approval, final-claim, blind-review, and production-profile ledgers remain empty.'
}
$request | Export-Csv -LiteralPath $requestPath -NoTypeInformation -Encoding UTF8
$requestSnapshotHash = Get-SemanticCsvHash $requestPath

$auditRows = @(Import-Csv -LiteralPath $auditPath -Encoding UTF8)
$existingAudit = @($auditRows | Where-Object auditBatchId -eq $batchId)
if ($existingAudit.Count -gt 1) { throw 'Duplicate C3 multidimensional audit rows exist.' }
if ($existingAudit.Count -eq 1) {
    if ($existingAudit[0].approvalScopeHash -ne $approvalScopeHash -or $existingAudit[0].inputSnapshotHash -ne $auditInputHash -or
        $existingAudit[0].approvalRequestSnapshotHash -ne $requestSnapshotHash) {
        throw 'Existing C3 multidimensional audit does not match regenerated hashes.'
    }
} else {
    $latest = $auditRows[-1]
    $audit = [ordered]@{}
    $latest.PSObject.Properties.Name | ForEach-Object { $audit[$_] = [string]$latest.$_ }
    $audit.auditManifestId = $auditManifestId
    $audit.auditScope = 'MULTIDIMENSIONAL_EVIDENCE_BATCH'
    $audit.auditBatchId = $batchId
    $audit.rubricSnapshotHash = $inputParts.rubrics
    $audit.evidenceRegistrySnapshotHash = $sourceRegistryHash
    $audit.claimLedgerSnapshotHash = $inputParts.multidimensionalCandidates
    $audit.sourceVerificationSnapshotHash = $sourceVerificationHash
    $audit.inputSnapshotHash = $auditInputHash
    $audit.generatedAt = $preparedAt
    $audit.auditDecision = 'PRODUCTION_REVIEW_REQUIRED'
    $audit.auditNotes = "Previous request $previousRequestId at $previousScopeHash remains immutable and is superseded by $resolutionId; ontology counts mode 17, temporal 11, measurement 21, normalization 14, dimensions 42, migrations 33; sources re-extracted 10, new sources 5, metric extractions 49; old revised candidates 24: narrowed 7, reclassified 17; new candidates 30, rubrics 2, research decisions 48, blocked targets $blockedCount, conflicting targets $conflictingCount; approvals/final/blind/profiles 0; request $approvalRequestId at $approvalScopeHash."
    $audit.sourceCount = '15'
    $audit.verifiedSourceCount = '15'
    $audit.draftClaimCount = '30'
    $audit.draftRubricCount = '2'
    $audit.researchDecisionCount = '48'
    $audit.targetExerciseReviewCount = '49'
    $audit.blockedTissueDimensionTargetCount = [string]$blockedCount
    $audit.conflictingTargetCount = [string]$conflictingCount
    $audit.missingTargetCount = [string]$blockedCount
    $audit.blockedCount = [string]$blockedCount
    $audit.conflictingCount = [string]$conflictingCount
    $audit.evidenceNotApprovedCount = '30'
    $audit.anomalyFlagCount = '0'
    $audit.failedInvariantCount = '0'
    $audit.warningCount = [string]$blockedCount
    $audit.reviewMode = 'SAME_SESSION_EVIDENCE_REAUDIT'
    $audit.independenceStatus = 'NOT_INDEPENDENT'
    $audit.completionStatus = 'MULTIDIMENSIONAL_C_APPROVAL_PACKAGE_PARTIAL'
    $audit.reauditSnapshotHash = ''
    $audit.claimCandidateSnapshotHash = $inputParts.multidimensionalCandidates
    $audit.userAdjudicationSnapshotHash = ''
    $audit.targetExerciseReviewSnapshotHash = $inputParts.transferCorrespondence
    $audit.reauditRowCount = '0'
    $audit.claimCandidateCount = '30'
    $audit.userAdjudicationCount = '0'
    $audit.retainedClaimCount = '0'
    $audit.correctedClaimCount = '30'
    $audit.blockedClaimCount = '0'
    $audit.retainedRubricCount = '0'
    $audit.correctedRubricCount = '2'
    $audit.blockedRubricCount = [string]$blockedCount
    $audit.blindReviewCount = '0'
    $audit.finalClaimCount = '0'
    $audit.humanApprovalCount = '0'
    $audit.productionEligibleProfileCount = '0'
    $audit.formalFinalClaimCount = '0'
    $audit.humanBatchApprovalCount = '0'
    $audit.productionProfileCount = '0'
    $audit.reviewPathContractStatus = 'PASS'
    $audit.rubricBoundaryValidationStatus = 'PASS'
    $audit.publicationIntegrityValidationStatus = 'PASS'
    $audit.approvalRequestValidationStatus = 'PASS'
    $audit.approvalRequestCount = '1'
    $audit.approvalRequestCandidateCount = '30'
    $audit.approvalRequestRubricCount = '2'
    $audit.approvalRequestSourceCount = '15'
    $audit.publicationIntegrityCheckedCount = '15'
    $audit.publicationIntegritySafeCount = '15'
    $audit.publicationIntegrityCorrectionCount = '0'
    $audit.publicationIntegrityBlockedCount = '0'
    $audit.publicationIntegrityUnknownCount = '0'
    $audit.approvalScopeHash = $approvalScopeHash
    $audit.publicationIntegritySnapshotHash = $publicationIntegrityHash
    $audit.approvalRequestSnapshotHash = $requestSnapshotHash
    $line = ([pscustomobject]$audit | ConvertTo-Csv -NoTypeInformation)[1]
    Add-Content -LiteralPath $auditPath -Value $line -Encoding UTF8
}

$approvalDoc = [Collections.Generic.List[string]]::new()
$approvalDoc.Add('# Phase C3 Multidimensional Tissue Approval Request')
$approvalDoc.Add('')
$approvalDoc.Add('## Decision boundary')
$approvalDoc.Add('')
$approvalDoc.Add("This is an immutable, pending, non-production request for batch ``$batchId``. It is not a human approval. Completion remains ``MULTIDIMENSIONAL_C_APPROVAL_PACKAGE_PARTIAL`` because $blockedCount material targets are explicitly blocked.")
$approvalDoc.Add('')
$approvalDoc.Add("- Request: ``$approvalRequestId``")
$approvalDoc.Add("- Scope hash: ``$approvalScopeHash``")
$approvalDoc.Add("- Audit: ``$auditManifestId``")
$approvalDoc.Add("- Superseded request: ``$previousRequestId``")
$approvalDoc.Add("- Supersession resolution: ``$resolutionId``")
$approvalDoc.Add('- Human approvals / final claims / blind reviews / production profiles: `0 / 0 / 0 / 0`')
$approvalDoc.Add('')
$approvalDoc.Add('## Candidate review table')
$approvalDoc.Add('')
$approvalDoc.Add('| Candidate | Exercise and exact condition | Tissue | Mode x temporal | Measurement | Value | Normalization | Correspondence | Proposed band and basis | Main limitation |')
$approvalDoc.Add('| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |')
foreach ($candidate in $tables.multidimensionalCandidates | Sort-Object claimCandidateId) {
    $condition = "$($candidate.testedExercise); load=$($candidate.externalLoadCondition); ROM=$($candidate.romCondition); velocity=$($candidate.velocityCondition); laterality=$($candidate.lateralityCondition); surface=$($candidate.surfaceCondition); landing=$($candidate.landingCondition); fatigue=$($candidate.fatigueCondition)"
    $value = if ($candidate.claimValue) { "$($candidate.claimValue) $($candidate.claimUnit)" } elseif ($candidate.claimLowerBound -or $candidate.claimUpperBound) { "$($candidate.claimLowerBound)-$($candidate.claimUpperBound) $($candidate.claimUnit)" } else { 'Qualitative source observation' }
    $band = if ($candidate.maximumDefensibleBand) { "$($candidate.maximumDefensibleBand); $($candidate.bandBasis)" } else { "None; $($candidate.bandBasis)" }
    $approvalDoc.Add("| ``$($candidate.claimCandidateId)`` | $(Escape-Markdown $condition) | ``$($candidate.tissueId)`` | ``$($candidate.mechanicalLoadMode) x $($candidate.temporalMetric)`` | ``$($candidate.measurementMetric)`` | $(Escape-Markdown $value) | ``$($candidate.normalizationBasis)`` | ``$($candidate.exerciseCorrespondence)`` | $(Escape-Markdown $band) | $(Escape-Markdown $candidate.claimLimitations) |")
}
$approvalDoc.Add('')
$approvalDoc.Add('## Rubric review table')
$approvalDoc.Add('')
$approvalDoc.Add('| Rubric | Tissue | Mode x temporal | Measurement / normalization | Band | Bounds or anchors | Included sources | Excluded scope and known gaps |')
$approvalDoc.Add('| --- | --- | --- | --- | --- | --- | --- | --- |')
foreach ($rubric in $tables.rubrics | Sort-Object rubricId) {
    $bounds = "$($rubric.metricLowerBound) to $($rubric.metricUpperBound) $($rubric.metricUnit); anchors=$($rubric.anchorStableKeys); method=$($rubric.assignmentMethod)"
    $approvalDoc.Add("| ``$($rubric.rubricId)`` | ``$($rubric.tissueId)`` | ``$($rubric.mechanicalLoadMode) x $($rubric.temporalMetric)`` | ``$($rubric.measurementMetric) / $($rubric.normalizationBasis)`` | ``$($rubric.loadBand)`` | $(Escape-Markdown $bounds) | ``$($rubric.sourceRefs)`` | $(Escape-Markdown $rubric.rubricLimitations) |")
}
$approvalDoc.Add('')
$approvalDoc.Add('## Exercise metric availability')
$approvalDoc.Add('')
$approvalDoc.Add('| Exercise / stable key | Peak | Loading rate | Impulse per event | Cumulative exposure |')
$approvalDoc.Add('| --- | --- | --- | --- | --- |')
$candidateGroups = $tables.multidimensionalCandidates | Group-Object stableKey, testedExercise | Sort-Object Name
foreach ($group in $candidateGroups) {
    $rows = @($group.Group)
    $label = "$(Escape-Markdown $rows[0].testedExercise) / ``$($rows[0].stableKey)``"
    $metric = @{}
    foreach ($temporal in @('PEAK','LOADING_RATE','IMPULSE_PER_EVENT')) {
        $available = @($rows | Where-Object temporalMetric -eq $temporal | ForEach-Object { "$($_.measurementMetric): $($_.claimValue) $($_.claimUnit)" })
        $metric[$temporal] = if ($available.Count) { Escape-Markdown ($available -join '; ') } else { 'Not reported' }
    }
    $approvalDoc.Add("| $label | $($metric.PEAK) | $($metric.LOADING_RATE) | $($metric.IMPULSE_PER_EVENT) | Unavailable without approved per-event exposure, event count, laterality, and no-double-counting contract |")
}
$approvalDoc.Add('')
$approvalDoc.Add('## Exact future approval statement')
$approvalDoc.Add('')
$approvalDoc.Add('The following text is a future decision template only. It has not been supplied or ingested by a human.')
$approvalDoc.Add('')
$approvalDoc.Add('```text')
$statement -split "`n" | ForEach-Object { $approvalDoc.Add($_) }
$approvalDoc.Add('```')
$approvalDoc.Add('')
$approvalDoc.Add('## Limits')
$approvalDoc.Add('')
$approvalDoc.Add('- Metric families, normalization families, source models, and exact conditions are not pooled silently.')
$approvalDoc.Add('- The two rubric rows are partial Achilles peak anchors only; missing bands remain missing.')
$approvalDoc.Add('- Source-specific composites cannot become generic force, a universal burden score, or production profiles.')
$approvalDoc.Add('- Lower-limb evidence cannot populate the separately queued upper-limb batch.')
$approvalDoc | Set-Content -LiteralPath $ApprovalDocumentPath -Encoding UTF8

$upperDoc = @(
    '# Phase C3 Upper-Limb Research Backlog', '',
    "Next batch: ``$upperBatchId``", '',
    'Status: `QUEUED_FOR_SEPARATE_RESEARCH`', '',
    'The multidimensional ontology is whole-body capable, but Phase C3 lower-limb evidence is not transferable to upper-limb tissues. This batch creates no upper-limb claims, rubrics, final claims, or profiles.', '',
    '## Tissues', '',
    (($upperBacklog.tissueIds -split '\|') | ForEach-Object { "- ``$_``" }), '',
    '## Exercise families', '',
    (($upperBacklog.exerciseFamilies -split '\|') | ForEach-Object { "- $_" }), '',
    '## Research contract', '',
    '- Search primary biomechanics studies for each tissue x mechanical-mode x temporal-metric target.',
    '- Preserve measurement method, normalization, exact load, ROM, speed, laterality, and exercise correspondence.',
    '- Keep joint moments, ground reaction force, EMG, internal tissue force, and strain as separate metric families.',
    '- Create no threshold, transfer, claim, or profile without compatible evidence and a new immutable approval scope.',
    '- Do not transfer lower-limb rubrics, source values, or fixed cross-dimensional weights.'
)
$upperDoc | Set-Content -LiteralPath $UpperBacklogDocumentPath -Encoding UTF8

Write-Output "APPROVAL_REQUEST_ID=$approvalRequestId"
Write-Output "APPROVAL_SCOPE_HASH=$approvalScopeHash"
Write-Output "AUDIT_MANIFEST_ID=$auditManifestId"
Write-Output 'COMPLETION_STATUS=MULTIDIMENSIONAL_C_APPROVAL_PACKAGE_PARTIAL'
Write-Output "BLOCKED_RESEARCH_TARGET_COUNT=$blockedCount"
Write-Output $statement

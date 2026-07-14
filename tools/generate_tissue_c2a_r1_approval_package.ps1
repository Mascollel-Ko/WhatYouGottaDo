param(
    [string]$AssetDirectory = "app/src/main/assets/metadata/tissue_load_v1",
    [string]$PreparedAt = "2026-07-14T00:00:03Z"
)

$ErrorActionPreference = "Stop"
$reviewBatchId = "TISSUE_RESEARCH_C2A_R1_LOWER_REVISED"
$reviewPath = "SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL"
$requestPath = Join-Path $AssetDirectory "tissue_review_batch_approval_request_revised_v1.csv"
$auditPath = Join-Path $AssetDirectory "tissue_metadata_audit_manifest_v1.csv"

function Get-Sha256([string]$value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($value)) } finally { $sha.Dispose() }
    return ([BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant()
}

function Get-CombinedHash([hashtable]$parts) {
    return Get-Sha256 (($parts.Keys | Sort-Object | ForEach-Object { "$_=$($parts[$_])" }) -join "`n")
}

function Get-SemanticCsvHash([string]$path) {
    $rows = @(Import-Csv -LiteralPath $path -Encoding UTF8)
    $header = if ($rows.Count) { @($rows[0].PSObject.Properties.Name) } else {
        $first = Get-Content -LiteralPath $path -TotalCount 1 -Encoding UTF8
        @((ConvertFrom-Csv "$first`nplaceholder" | Select-Object -First 1).PSObject.Properties.Name)
    }
    $unit = [char]0x1F
    $record = [char]0x1E
    $encodedRows = @($rows | ForEach-Object {
        $row = $_
        ($header | ForEach-Object { [string]$row.$_ }) -join $unit
    } | Sort-Object)
    return Get-Sha256 (($header -join $unit) + "`n" + ($encodedRows -join $record))
}

function Tokens($rows, [string]$property) {
    return (@($rows | ForEach-Object { [string]$_.$property } | Where-Object { $_ } | Sort-Object -Unique) -join '|')
}

function Require-Count([string]$name, $rows, [int]$expected) {
    if (@($rows).Count -ne $expected) { throw "$name count must be $expected, got $(@($rows).Count)." }
}

$files = [ordered]@{
    oldRequest = "tissue_review_batch_approval_request_v1.csv"
    requestResolution = "tissue_approval_request_resolution_v1.csv"
    claimDispositions = "tissue_claim_candidate_disposition_v1.csv"
    rubricDispositions = "tissue_rubric_disposition_v1.csv"
    humanDirectives = "tissue_human_research_directive_v1.csv"
    revisedCandidates = "tissue_evidence_claim_candidates_revised_v1.csv"
    revisedRubrics = "tissue_load_band_rubric_revised_v1.csv"
    researchDecisions = "tissue_rubric_research_log_revised_v1.csv"
    canonicalMappings = "tissue_canonical_exercise_mapping_audit_v1.csv"
    sourceSpecificMetrics = "tissue_source_specific_metric_v1.csv"
    evidenceRegistry = "tissue_load_evidence_registry_v1.csv"
    sourceVerification = "tissue_source_verification_v1.csv"
    publicationIntegrity = "tissue_publication_integrity_verification_v1.csv"
}
$tables = @{}
$inputParts = @{}
foreach ($entry in $files.GetEnumerator()) {
    $path = Join-Path $AssetDirectory $entry.Value
    $tables[$entry.Key] = @(Import-Csv -LiteralPath $path -Encoding UTF8)
    $inputParts[$entry.Key] = Get-SemanticCsvHash $path
}

Require-Count "old request" $tables.oldRequest 1
Require-Count "request resolution" $tables.requestResolution 1
Require-Count "claim disposition" $tables.claimDispositions 12
Require-Count "rubric disposition" $tables.rubricDispositions 5
Require-Count "human directive" $tables.humanDirectives 12
Require-Count "revised candidate" $tables.revisedCandidates 24
Require-Count "revised rubric" $tables.revisedRubrics 2
Require-Count "research decision" $tables.researchDecisions 13
Require-Count "canonical mapping" $tables.canonicalMappings 49
Require-Count "source-specific metric" $tables.sourceSpecificMetrics 1
Require-Count "evidence source" $tables.evidenceRegistry 10
Require-Count "source verification" $tables.sourceVerification 10
Require-Count "publication integrity" $tables.publicationIntegrity 10

if ($tables.requestResolution[0].resolutionStatus -ne 'SUPERSEDED_BEFORE_APPROVAL') {
    throw "Old approval request is not superseded."
}
if (@($tables.revisedCandidates | Where-Object productionEligibility -ne 'false').Count) {
    throw "Revised claim candidates must remain non-production."
}
if (@($tables.revisedRubrics | Where-Object productionEligibility -ne 'false').Count) {
    throw "Revised rubrics must remain non-production."
}
if (@(Import-Csv -LiteralPath (Join-Path $AssetDirectory 'tissue_review_batch_approval_v1.csv')).Count -or
    @(Import-Csv -LiteralPath (Join-Path $AssetDirectory 'tissue_evidence_claims_v1.csv')).Count -or
    @(Import-Csv -LiteralPath (Join-Path $AssetDirectory 'tissue_evidence_blind_review_v1.csv')).Count) {
    throw "Approval, final-claim, and blind-review ledgers must remain empty."
}
foreach ($profile in Get-ChildItem -LiteralPath $AssetDirectory -Filter 'exercise_*_load_profiles_v1.csv') {
    if (@(Import-Csv -LiteralPath $profile.FullName).Count) { throw "Production profile file is not empty: $($profile.Name)" }
}

$auditInputHash = Get-CombinedHash $inputParts
$auditManifestId = "tissue_research_c2a_r1_$($auditInputHash.Substring(0,12))"
$scope = @{} + $inputParts
$scope.reviewPath = $reviewPath
$scope.auditManifestId = $auditManifestId
$scope.auditInputSnapshotHash = $auditInputHash
$approvalScopeHash = Get-CombinedHash $scope
$approvalRequestId = "TISSUE_APPROVAL_REQUEST_C2A_R1_$($approvalScopeHash.Substring(0,16).ToUpperInvariant())"

$blockedResearchCount = @($tables.researchDecisions | Where-Object researchDecision -like 'BLOCKED_*').Count
$statement = "I approve revisedApprovalRequestId=$approvalRequestId,`napprovalScopeHash=$approvalScopeHash,`nreviewPath=$reviewPath,`ncovering exactly 24 revised claim candidates, 2 revised rubric rows, 12 old-candidate dispositions, 5 old-rubric dispositions, 12 human research directives, 13 research decisions, 49 canonical mappings, and the listed source and publication-integrity snapshots.`nI understand this revised package is same-session, non-independent, and non-production until formal promotion."

$request = [pscustomobject][ordered]@{
    approvalRequestId = $approvalRequestId
    reviewBatchId = $reviewBatchId
    reviewPath = $reviewPath
    revisedClaimCandidateIds = Tokens $tables.revisedCandidates 'revisedClaimCandidateId'
    revisedRubricIds = Tokens $tables.revisedRubrics 'rubricId'
    priorClaimCandidateIds = Tokens $tables.claimDispositions 'priorClaimCandidateId'
    priorRubricIds = Tokens $tables.rubricDispositions 'priorRubricId'
    humanResearchDirectiveIds = Tokens $tables.humanDirectives 'directiveId'
    researchDecisionIds = Tokens $tables.researchDecisions 'researchDecisionId'
    canonicalMappingStableKeys = Tokens $tables.canonicalMappings 'stableKey'
    sourceIds = Tokens $tables.evidenceRegistry 'sourceId'
    sourceSpecificMetricIds = Tokens $tables.sourceSpecificMetrics 'sourceMetricId'
    candidateCount = '24'; rubricCount = '2'; oldCandidateDispositionCount = '12'; oldRubricDispositionCount = '5'
    humanDirectiveCount = '12'; researchDecisionCount = '13'; canonicalMappingCount = '49'; sourceCount = '10'
    claimsPreservedUnchangedCount = '2'; claimsNarrowedCount = '4'; claimsReclassifiedCount = '5'
    claimsRemovedCount = '1'; claimsBlockedCount = '0'; rubricsRetainedCount = '2'; rubricsRemovedCount = '3'
    rubricsAddedCount = '2'; rubricsBlockedCount = '0'; blockedResearchTargetCount = [string]$blockedResearchCount
    auditManifestId = $auditManifestId; auditInputSnapshotHash = $auditInputHash
    sourceVerificationSnapshotHash = $inputParts.sourceVerification
    publicationIntegritySnapshotHash = $inputParts.publicationIntegrity
    approvalScopeHash = $approvalScopeHash; requestStatus = 'PENDING_HUMAN_DECISION'
    preparedBy = 'Codex'; preparedByType = 'AI_AGENT'; preparedAt = $PreparedAt
    approvalSummary = 'Future human approval is requested only for the exact revised non-production C2A-R1 scope.'
    knownLimitations = 'Six material research targets remain explicitly blocked; weighted transfer and generic PFJ rubrics are not approved.'
    requiredUserStatement = $statement
    requestNotes = 'This generated statement is not approval. Approval, final-claim, blind-review, and production-profile ledgers remain empty.'
}
$request | Export-Csv -LiteralPath $requestPath -NoTypeInformation -Encoding UTF8
$requestSnapshotHash = Get-SemanticCsvHash $requestPath

$auditRows = @(Import-Csv -LiteralPath $auditPath -Encoding UTF8)
$existingAudit = @($auditRows | Where-Object auditBatchId -eq $reviewBatchId)
if ($existingAudit.Count -gt 1) { throw "Duplicate C2A-R1 audit rows exist." }
if ($existingAudit.Count -eq 1) {
    if ($existingAudit[0].approvalScopeHash -ne $approvalScopeHash -or $existingAudit[0].inputSnapshotHash -ne $auditInputHash) {
        throw "Existing C2A-R1 audit does not match regenerated hashes."
    }
} else {
    $latest = $auditRows[-1]
    $audit = [ordered]@{}
    $latest.PSObject.Properties.Name | ForEach-Object { $audit[$_] = [string]$latest.$_ }
    $audit.auditManifestId = $auditManifestId; $audit.auditScope = 'EVIDENCE_BATCH'; $audit.auditBatchId = $reviewBatchId
    $audit.rubricSnapshotHash = $inputParts.revisedRubrics; $audit.evidenceRegistrySnapshotHash = $inputParts.evidenceRegistry
    $audit.claimCandidateSnapshotHash = $inputParts.revisedCandidates; $audit.sourceVerificationSnapshotHash = $inputParts.sourceVerification
    $audit.inputSnapshotHash = $auditInputHash; $audit.generatedAt = $PreparedAt; $audit.auditDecision = 'PRODUCTION_REVIEW_REQUIRED'
    $audit.auditNotes = 'Old request superseded; old claims 12: unchanged 2, narrowed 4, reclassified 5, generic anchor removed 1, blocked 0; old rubrics 5: retained 2, removed 3; revised rubrics added 2; weighted squat/lunge/split-squat/calf and jump-hop-landing reviewed with unsupported transfer blocked; new sources 0; approval/final/blind/profile rows 0.'
    $audit.sourceCount = '10'; $audit.verifiedSourceCount = '10'; $audit.draftClaimCount = '24'; $audit.draftRubricCount = '2'
    $audit.researchDecisionCount = '13'; $audit.targetExerciseReviewCount = '49'; $audit.blockedTissueDimensionTargetCount = [string]$blockedResearchCount
    $audit.reviewMode = 'SAME_SESSION_EVIDENCE_REAUDIT'; $audit.independenceStatus = 'NOT_INDEPENDENT'
    $audit.completionStatus = 'REVISED_APPROVAL_PACKAGE_PARTIAL'; $audit.claimCandidateCount = '24'
    $audit.retainedClaimCount = '2'; $audit.correctedClaimCount = '10'; $audit.blockedClaimCount = '0'
    $audit.retainedRubricCount = '2'; $audit.correctedRubricCount = '0'; $audit.blockedRubricCount = '3'
    $audit.approvalRequestValidationStatus = 'PASS'; $audit.approvalRequestCount = '1'; $audit.approvalRequestCandidateCount = '24'
    $audit.approvalRequestRubricCount = '2'; $audit.approvalRequestSourceCount = '10'; $audit.approvalScopeHash = $approvalScopeHash
    $audit.publicationIntegritySnapshotHash = $inputParts.publicationIntegrity; $audit.approvalRequestSnapshotHash = $requestSnapshotHash
    $audit.humanBatchApprovalCount = '0'; $audit.humanApprovalCount = '0'; $audit.formalFinalClaimCount = '0'
    $audit.finalClaimCount = '0'; $audit.productionProfileCount = '0'; $audit.productionEligibleProfileCount = '0'
    $audit.warningCount = [string]$blockedResearchCount; $audit.blockedCount = [string]$blockedResearchCount
    $line = ([pscustomobject]$audit | ConvertTo-Csv -NoTypeInformation)[1]
    Add-Content -LiteralPath $auditPath -Value $line -Encoding UTF8
}

Write-Output "APPROVAL_REQUEST_ID=$approvalRequestId"
Write-Output "APPROVAL_SCOPE_HASH=$approvalScopeHash"
Write-Output "AUDIT_MANIFEST_ID=$auditManifestId"
Write-Output "COMPLETION_STATUS=REVISED_APPROVAL_PACKAGE_PARTIAL"
Write-Output "BLOCKED_RESEARCH_TARGET_COUNT=$blockedResearchCount"
Write-Output $statement

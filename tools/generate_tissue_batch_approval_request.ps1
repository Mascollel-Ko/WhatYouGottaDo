param(
    [string]$AssetDirectory = "app/src/main/assets/metadata/tissue_load_v1",
    [string]$CanonicalPath = "app/src/main/assets/metadata/canonical_exercise_metadata_v0_3_5_0_pass3_1.csv",
    [string]$ReportPath = "docs/tissue_load_phase_c2a_approval_request.md",
    [string]$PreparedAt = "2026-07-14T00:00:00Z"
)

$ErrorActionPreference = "Stop"
$reviewBatchId = "TISSUE_REAUDIT_C_LOWER_KNEE_ANKLE"
$auditBatchId = "TISSUE_APPROVAL_PREP_C2A_LOWER_KNEE_ANKLE"
$reviewPath = "SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL"
$requestPath = Join-Path $AssetDirectory "tissue_review_batch_approval_request_v1.csv"
$auditPath = Join-Path $AssetDirectory "tissue_metadata_audit_manifest_v1.csv"

function Get-Sha256([string]$Value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)) } finally { $sha.Dispose() }
    return ([BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant()
}

function Get-CombinedHash([hashtable]$Parts) {
    return Get-Sha256 (($Parts.Keys | Sort-Object | ForEach-Object { "$_=$($Parts[$_])" }) -join "`n")
}

function Get-SemanticCsvHash([string]$Path) {
    $rows = @(Import-Csv -LiteralPath $Path)
    $header = if ($rows.Count) { @($rows[0].PSObject.Properties.Name) } else {
        $first = Get-Content -LiteralPath $Path -TotalCount 1
        @((ConvertFrom-Csv "$first`nplaceholder" | Select-Object -First 1).PSObject.Properties.Name)
    }
    $unit = [char]0x1F
    $record = [char]0x1E
    $encodedRows = @($rows | ForEach-Object { $row = $_; ($header | ForEach-Object { [string]$row.$_ }) -join $unit } | Sort-Object)
    return Get-Sha256 (($header -join $unit) + "`n" + ($encodedRows -join $record))
}

function Join-Tokens([string]$Value) {
    return (@($Value -split '\|' | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -ne 'NONE' } | Sort-Object -Unique) -join '|')
}

function Get-Payload($Values) { return (@($Values | ForEach-Object { if ($null -eq $_) { "" } else { [string]$_ } }) -join [char]0x1F) }

function Candidate-Payload($row) {
    return Get-Payload @(
        $row.claimCandidateId,$row.reviewBatchId,$row.draftClaimId,$row.reauditId,$row.sourceId,$row.stableKey,$row.tissueId,$row.loadDimension,
        $row.candidateClaimType,$row.candidateClaimParaphrase,$row.candidateClaimDirection,$row.candidateValue,$row.candidateLowerBound,
        $row.candidateUpperBound,$row.candidateUnit,$row.normalizationBasis,$row.supportedCondition,$row.measurementMethod,
        $row.evidenceLocatorType,$row.evidenceLocator,$row.evidenceAccessLevel,$row.exerciseCorrespondence,$row.tissueCorrespondence,
        $row.dimensionCorrespondence,$row.crossStudyComparability,$row.maximumDefensibleBand,$row.bandBasis,$row.claimSupportStatus,
        $row.confidenceLevel,(Join-Tokens $row.userAdjudicationIds),$row.reviewMode,$row.independenceStatus,$row.technicalVerificationStatus
    )
}

function Rubric-Payload($row) {
    return Get-Payload @(
        $row.rubricId,$row.tissueId,$row.loadDimension,$row.loadBand,$row.metricType,$row.metricLowerBound,$row.lowerBoundInclusive,
        $row.metricUpperBound,$row.upperBoundInclusive,$row.boundarySemanticsVersion,$row.metricUnit,(Join-Tokens $row.anchorStableKeys),
        $row.anchorConditions,(Join-Tokens $row.anchorClaimIds),$row.researchDecisionId,(Join-Tokens $row.draftClaimIds),
        $row.assignmentMethod,$row.evidenceSetId,(Join-Tokens $row.evidenceClaimIds),(Join-Tokens $row.sourceRefs),$row.confidenceLevel,
        $row.rubricStatus,$row.reauditAction,(Join-Tokens $row.reauditIds),(Join-Tokens $row.claimCandidateIds),
        (Join-Tokens $row.userAdjudicationIds),$row.reviewMode,$row.independenceStatus
    )
}

function Source-Payload($row) {
    return Get-Payload @($row.sourceId,$row.pmid,$row.doi.ToLowerInvariant(),$row.title,$row.authors,$row.publicationYear,$row.journal,
        $row.identifierVerificationStatus,$row.bibliographicMatchStatus,$row.publicationIntegrityStatus)
}

function Source-Verification-Payload($row) {
    return Get-Payload @($row.sourceId,$row.resolvedPmid,$row.resolvedDoi.ToLowerInvariant(),$row.resolvedTitle,$row.resolvedFirstAuthor,
        $row.resolvedYear,$row.resolvedJournal,$row.identifierVerificationStatus,$row.bibliographicMatchStatus,
        $row.publicationIntegrityStatus,$row.networkCapabilityStatus,$row.verificationMethod,$row.metadataSnapshotHash)
}

function Integrity-Payload($row) {
    return Get-Payload @($row.sourceId,$row.pmid,$row.doi.ToLowerInvariant(),(Join-Tokens $row.pubmedPublicationTypes),
        (Join-Tokens $row.pubmedCommentsCorrections),(Join-Tokens $row.pubmedLinkedNotices),(Join-Tokens $row.crossrefRelationTypes),
        (Join-Tokens $row.crossrefUpdateTo),(Join-Tokens $row.crossrefUpdatedBy),$row.publisherNoticeStatus,$row.publisherNoticeReference,
        $row.integrityCheckStatus,$row.verificationMethod,$row.metadataSnapshotHash)
}

$candidates = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory "tissue_evidence_claim_candidates_v1.csv") | Sort-Object claimCandidateId)
$rubrics = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory "tissue_load_band_rubric_v1.csv") | Sort-Object rubricId)
$sources = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory "tissue_load_evidence_registry_v1.csv") | Sort-Object sourceId)
$sourceVerifications = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory "tissue_source_verification_v1.csv") | Sort-Object sourceId)
$integrityRows = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory "tissue_publication_integrity_verification_v1.csv") | Sort-Object sourceId)
$adjudications = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory "tissue_user_adjudication_v1.csv") | Sort-Object adjudicationId)
$canonical = @(Import-Csv -LiteralPath $CanonicalPath)
$approvals = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory "tissue_review_batch_approval_v1.csv"))
$finalClaims = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory "tissue_evidence_claims_v1.csv"))

if ($candidates.Count -ne 12 -or $rubrics.Count -ne 5 -or $sources.Count -ne 10 -or $adjudications.Count -ne 2) {
    throw "Approval package counts differ from the audited Phase C scope."
}
if ($sourceVerifications.Count -ne $sources.Count -or $integrityRows.Count -ne $sources.Count) {
    throw "Source verification coverage is incomplete."
}
if (@($integrityRows | Where-Object integrityCheckStatus -ne 'NO_ADVERSE_NOTICE_FOUND').Count -gt 0) {
    throw "Approval package contains a publication-integrity blocker."
}
if (@($candidates | Where-Object { $_.productionEligibility -ne 'false' -or $_.technicalVerificationStatus -eq 'BLOCKED' }).Count -gt 0) {
    throw "Approval package contains an invalid claim candidate."
}
if (@($rubrics | Where-Object { $_.rubricStatus -eq 'BLOCKED_AFTER_REAUDIT' -or -not $_.boundarySemanticsVersion }).Count -gt 0) {
    throw "Approval package contains an invalid rubric."
}
if ($approvals.Count -or $finalClaims.Count) { throw "Generator refuses to overwrite an existing approval or final claim." }

$inputParts = @{
    claimCandidates = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_evidence_claim_candidates_v1.csv")
    rubrics = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_load_band_rubric_v1.csv")
    evidenceRegistry = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_load_evidence_registry_v1.csv")
    sourceVerification = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_source_verification_v1.csv")
    publicationIntegrity = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_publication_integrity_verification_v1.csv")
    userAdjudications = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_user_adjudication_v1.csv")
    reaudits = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_evidence_reaudit_v1.csv")
    draftClaims = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_evidence_claims_draft_v1.csv")
}
$auditInputHash = Get-CombinedHash $inputParts
$auditManifestId = "tissue_approval_c2a_$($auditInputHash.Substring(0,12))"

$sourcePayloads = @{}; $sourceVerifications | ForEach-Object { $sourcePayloads[$_.sourceId] = Source-Verification-Payload $_ }
$sourceVerificationHash = Get-CombinedHash $sourcePayloads
$integrityPayloads = @{}; $integrityRows | ForEach-Object { $integrityPayloads[$_.sourceId] = Integrity-Payload $_ }
$publicationIntegrityHash = Get-CombinedHash $integrityPayloads

$scope = @{ reviewPath=$reviewPath; auditManifestId=$auditManifestId; auditInputSnapshotHash=$auditInputHash }
$candidates | ForEach-Object { $scope["candidate:$($_.claimCandidateId)"] = Candidate-Payload $_ }
$rubrics | ForEach-Object { $scope["rubric:$($_.rubricId)"] = Rubric-Payload $_ }
$sources | ForEach-Object { $scope["source:$($_.sourceId)"] = Source-Payload $_ }
$sourceVerifications | ForEach-Object { $scope["sourceVerification:$($_.sourceId)"] = Source-Verification-Payload $_ }
$integrityRows | ForEach-Object { $scope["publicationIntegrity:$($_.sourceId)"] = Integrity-Payload $_ }
$adjudications | ForEach-Object { $scope["userAdjudication:$($_.adjudicationId)"] = $_.adjudicationId }
$approvalScopeHash = Get-CombinedHash $scope
$approvalRequestId = "TISSUE_APPROVAL_REQUEST_C2A_$($approvalScopeHash.Substring(0,16).ToUpperInvariant())"
$statement = "I approve approvalRequestId=$approvalRequestId,`napprovalScopeHash=$approvalScopeHash,`nreviewPath=$reviewPath,`ncovering exactly the listed $($candidates.Count) claim candidates and $($rubrics.Count) rubric rows.`nI understand this was a same-session, non-independent technical re-audit."

$request = [pscustomobject][ordered]@{
    approvalRequestId=$approvalRequestId; reviewBatchId=$reviewBatchId; reviewPath=$reviewPath
    claimCandidateIds=($candidates.claimCandidateId -join '|'); rubricIds=($rubrics.rubricId -join '|'); sourceIds=($sources.sourceId -join '|')
    userAdjudicationIds=($adjudications.adjudicationId -join '|'); candidateCount=$candidates.Count; rubricCount=$rubrics.Count; sourceCount=$sources.Count
    auditManifestId=$auditManifestId; auditInputSnapshotHash=$auditInputHash; sourceVerificationSnapshotHash=$sourceVerificationHash
    publicationIntegritySnapshotHash=$publicationIntegrityHash; approvalScopeHash=$approvalScopeHash; requestStatus='PENDING_HUMAN_DECISION'
    preparedBy='Codex'; preparedByType='AI_AGENT'; preparedAt=$PreparedAt
    approvalSummary='Approval requested for the exact validated Phase C same-session claim-candidate and rubric scope.'
    knownLimitations='The technical re-audit was same-session and non-independent; close-variant and explicit-proxy limitations remain disclosed per row.'
    requiredUserStatement=$statement
    requestNotes='This request is not approval. The approval ledger, final-claim ledger, and production profiles remain empty.'
}
$request | Export-Csv -LiteralPath $requestPath -NoTypeInformation -Encoding utf8

$auditRows = @(Import-Csv -LiteralPath $auditPath | Where-Object auditBatchId -ne $auditBatchId)
$auditColumns = [Collections.Generic.List[string]]::new()
$auditRows[0].PSObject.Properties.Name | ForEach-Object { $auditColumns.Add($_) }
@('reviewPathContractStatus','rubricBoundaryValidationStatus','publicationIntegrityValidationStatus','approvalRequestValidationStatus',
  'approvalRequestCount','approvalRequestCandidateCount','approvalRequestRubricCount','approvalRequestSourceCount',
  'publicationIntegrityCheckedCount','publicationIntegritySafeCount','publicationIntegrityCorrectionCount',
  'publicationIntegrityBlockedCount','publicationIntegrityUnknownCount','approvalScopeHash','publicationIntegritySnapshotHash',
  'approvalRequestSnapshotHash') | ForEach-Object { if ($_ -notin $auditColumns) { $auditColumns.Add($_) } }
$latest = $auditRows | Select-Object -Last 1
$newAudit = [ordered]@{}; $auditColumns | ForEach-Object { $newAudit[$_] = [string]$latest.$_ }
$newAudit.auditManifestId=$auditManifestId; $newAudit.auditScope='EVIDENCE_BATCH'; $newAudit.auditBatchId=$auditBatchId
$newAudit.rubricSnapshotHash=$inputParts.rubrics; $newAudit.evidenceRegistrySnapshotHash=$inputParts.evidenceRegistry
$newAudit.sourceVerificationSnapshotHash=$sourceVerificationHash; $newAudit.inputSnapshotHash=$auditInputHash
$newAudit.generatedAt=$PreparedAt; $newAudit.auditDecision='PRODUCTION_REVIEW_REQUIRED'; $newAudit.completionStatus='APPROVAL_PACKAGE_READY'
$newAudit.auditNotes='Dual review paths, exact PFJ boundaries, publication-integrity checks, and one immutable human approval request are ready. No approval, final claim, or production profile was created.'
$newAudit.reviewPathContractStatus='PASS'; $newAudit.rubricBoundaryValidationStatus='PASS'; $newAudit.publicationIntegrityValidationStatus='PASS'
$newAudit.approvalRequestValidationStatus='PASS'; $newAudit.approvalRequestCount='1'; $newAudit.approvalRequestCandidateCount=[string]$candidates.Count
$newAudit.approvalRequestRubricCount=[string]$rubrics.Count; $newAudit.approvalRequestSourceCount=[string]$sources.Count
$newAudit.publicationIntegrityCheckedCount=[string]$integrityRows.Count
$newAudit.publicationIntegritySafeCount=[string](@($integrityRows | Where-Object integrityCheckStatus -in @('NO_ADVERSE_NOTICE_FOUND','CORRECTION_REVIEWED_ACCEPTABLE')).Count)
$newAudit.publicationIntegrityCorrectionCount=[string](@($integrityRows | Where-Object integrityCheckStatus -match 'CORRECTION').Count)
$newAudit.publicationIntegrityBlockedCount=[string](@($integrityRows | Where-Object integrityCheckStatus -in @('RETRACTED','EXPRESSION_OF_CONCERN')).Count)
$newAudit.publicationIntegrityUnknownCount=[string](@($integrityRows | Where-Object integrityCheckStatus -eq 'UNABLE_TO_VERIFY').Count)
$newAudit.approvalScopeHash=$approvalScopeHash; $newAudit.publicationIntegritySnapshotHash=$publicationIntegrityHash
$newAudit.approvalRequestSnapshotHash=Get-SemanticCsvHash $requestPath
$newAudit.humanBatchApprovalCount='0'; $newAudit.humanApprovalCount='0'; $newAudit.formalFinalClaimCount='0'; $newAudit.finalClaimCount='0'
$newAudit.productionProfileCount='0'; $newAudit.productionEligibleProfileCount='0'
$outputAudits = @($auditRows | ForEach-Object { $row=$_; $ordered=[ordered]@{}; $auditColumns | ForEach-Object { $ordered[$_]=[string]$row.$_ }; [pscustomobject]$ordered })
$outputAudits += [pscustomobject]$newAudit
$outputAudits | Export-Csv -LiteralPath $auditPath -NoTypeInformation -Encoding utf8

$exerciseNames = @{}; $canonical | ForEach-Object { $exerciseNames[$_.stableKey] = $_.exerciseName }
$report = [Collections.Generic.List[string]]::new()
$report.Add('# Phase C2A Human Approval Request')
$report.Add('')
$report.Add('This package is pending a human decision. It is not an approval, final claim, or production-profile promotion.')
$report.Add('')
$report.Add("- Approval request: ``$approvalRequestId``")
$report.Add("- Approval scope hash: ``$approvalScopeHash``")
$report.Add("- Review path: ``$reviewPath``")
$report.Add("- Scope: $($candidates.Count) candidates, $($rubrics.Count) rubrics, $($sources.Count) sources, $($adjudications.Count) adjudications")
$report.Add("- Audit manifest: ``$auditManifestId``")
$report.Add('')
$report.Add('## Claim Candidates')
foreach ($candidate in $candidates) {
    $value = if ($candidate.candidateValue) { "$($candidate.candidateValue) $($candidate.candidateUnit)" } else { "$($candidate.candidateLowerBound)-$($candidate.candidateUpperBound) $($candidate.candidateUnit)" }
    $report.Add('')
    $report.Add("### ``$($candidate.claimCandidateId)``")
    $report.Add("- Exercise: ``$($candidate.stableKey)`` - $($exerciseNames[$candidate.stableKey])")
    $report.Add("- Tissue/dimension: ``$($candidate.tissueId) / $($candidate.loadDimension)``")
    $report.Add("- Value/range: $value")
    $report.Add("- Maximum band: ``$($candidate.maximumDefensibleBand)``")
    $report.Add("- Correspondence: ``$($candidate.exerciseCorrespondence) / $($candidate.tissueCorrespondence) / $($candidate.dimensionCorrespondence)``")
    $report.Add("- Main limitation: $($candidate.candidateNotes)")
}
$report.Add('')
$report.Add('## Rubrics')
foreach ($rubric in $rubrics) {
    $leftBracket = if ($rubric.lowerBoundInclusive -eq 'true') { '[' } else { '(' }
    $rightBracket = if ($rubric.upperBoundInclusive -eq 'true') { ']' } else { ')' }
    $lower = if ($rubric.metricLowerBound) { "$leftBracket$($rubric.metricLowerBound)" } else { '(-infinity' }
    $upper = if ($rubric.metricUpperBound) { "$($rubric.metricUpperBound)$rightBracket" } else { 'infinity)' }
    $interval = if (-not $rubric.metricLowerBound -and -not $rubric.metricUpperBound) { 'Anchor-only; no numeric interval' } else { "$lower, $upper $($rubric.metricUnit)" }
    $report.Add('')
    $report.Add("- ``$($rubric.rubricId)``: ``$($rubric.tissueId) / $($rubric.loadDimension) / $($rubric.loadBand)``; $interval; anchor ``$($rubric.anchorStableKeys)``")
}
$report.Add('')
$report.Add('## Sources and Publication Integrity')
foreach ($source in $sources) {
    $integrity = $integrityRows | Where-Object sourceId -eq $source.sourceId
    $report.Add("- ``$($source.sourceId)`` (PMID $($source.pmid), DOI $($source.doi)): ``$($integrity.integrityCheckStatus)``; publisher check ``$($integrity.publisherNoticeStatus)``")
}
$report.Add('')
$report.Add('## User Adjudications')
foreach ($adjudication in $adjudications) { $report.Add("- ``$($adjudication.adjudicationId)``: $($adjudication.decision)") }
$report.Add('')
$report.Add('## Exact Required Approval Statement')
$report.Add('')
$report.Add('```text')
$statement -split "`n" | ForEach-Object { $report.Add($_) }
$report.Add('```')
$report.Add('')
$report.Add('The two existing adjudications are interpretation decisions only and do not approve this batch.')
$report | Set-Content -LiteralPath $ReportPath -Encoding utf8

Write-Output "APPROVAL_REQUEST_ID=$approvalRequestId"
Write-Output "APPROVAL_SCOPE_HASH=$approvalScopeHash"
Write-Output "AUDIT_MANIFEST_ID=$auditManifestId"
Write-Output "CANDIDATE_COUNT=$($candidates.Count)"
Write-Output "RUBRIC_COUNT=$($rubrics.Count)"
Write-Output "SOURCE_COUNT=$($sources.Count)"
Write-Output "HUMAN_BATCH_APPROVAL_COUNT=$($approvals.Count)"
Write-Output "FORMAL_FINAL_CLAIM_COUNT=$($finalClaims.Count)"
Write-Output $statement

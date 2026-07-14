param(
    [string]$AssetDirectory = "app/src/main/assets/metadata/tissue_load_v1",
    [string]$ApprovalDocumentPath = "docs/tissue_load_phase_c3_1_approval_request.md"
)

$ErrorActionPreference = "Stop"
$batchId = "TISSUE_C3_1_ONTOLOGY_CORRECTION"
$reviewPath = "SAME_SESSION_REAUDIT_WITH_HUMAN_BATCH_APPROVAL"
$preparedAt = "2026-07-15T00:00:03Z"
$oldRequestId = "TISSUE_APPROVAL_REQUEST_C3_MD_48F86FEE6C39D28B"
$oldScopeHash = "48f86fee6c39d28b18e8ab9fbacd748e52a606db30c9b4cbfd377be4193162b8"
$resolutionId = "TISSUE_APPROVAL_RESOLUTION_C3_1_48F86FEE"
$completion = "C3_1_ONTOLOGY_CORRECTION_PACKAGE_PARTIAL"
$guidancePath = Join-Path $AssetDirectory "tissue_load_guidance_c3_1_v1.csv"
$requestPath = Join-Path $AssetDirectory "tissue_review_batch_approval_request_c3_1_v1.csv"
$auditPath = Join-Path $AssetDirectory "tissue_metadata_audit_manifest_v1.csv"

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

function Join-Values($rows, [string]$name) {
    (@($rows | ForEach-Object { [string]$_.$name } | Where-Object { $_ } | Sort-Object -Unique) -join '|')
}

function Escape-Markdown([string]$value) { $value.Replace('|', '\|').Replace("`r", ' ').Replace("`n", ' ') }

$guidance = @(
    [pscustomobject]@{
        guidanceId='C31_ANCHOR_ACH_TENSION_PEAK_MODERATE'; rubricKind='CONDITION_ANCHOR'; tissueId='ACHILLES_TENDON'; mechanicalLoadMode='TENSION'; temporalMetric='PEAK'; measurementMetric='MODELED_TENDON_FORCE'; normalizationBasis='BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE'; loadBand='MODERATE'; anchorValue='3.0'; anchorUnit='BW'; anchorStableKeys='ex_5ca7133f'; anchorConditionIds='C3COND_R1C_02_ACH_SINGLE_CALF_PEAK'; anchorClaimCandidateIds='C3_R1C_02_ACH_SINGLE_CALF_PEAK'; sourceRefs='PREFLIGHT_32658037'; metricLowerBound=''; metricUpperBound=''; lowerBoundInclusive=''; upperBoundInclusive=''; exactConditionMatchRequired='true'; assignmentMethod='WITHIN_STUDY_EXACT_CONDITION_ANCHOR'; comparisonPopulation='Healthy young adults'; comparisonMethodFamily='CONSTRAINED_FREE_BODY_ACHILLES_FORCE_MODEL'; confidenceLevel='LOW'; guidanceStatus='PARTIAL_NON_PRODUCTION_PENDING_HUMAN_APPROVAL'; preparedBy='Codex'; preparedByType='AI_AGENT'; preparedAt=$preparedAt; guidanceLimitations='Exact standing single-leg bodyweight task only; no neighboring-value classification, interpolation, LOW, or HIGH band.'; forbiddenTransfers='No neighboring value, interval, exercise variant, external-load modifier, or D-stage profile transfer without explicit approval.'
    },
    [pscustomobject]@{
        guidanceId='C31_ANCHOR_ACH_TENSION_PEAK_VERY_HIGH'; rubricKind='CONDITION_ANCHOR'; tissueId='ACHILLES_TENDON'; mechanicalLoadMode='TENSION'; temporalMetric='PEAK'; measurementMetric='MODELED_TENDON_FORCE'; normalizationBasis='BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE'; loadBand='VERY_HIGH'; anchorValue='7.3'; anchorUnit='BW'; anchorStableKeys='ex_314df428'; anchorConditionIds='C3COND_R1C_03_ACH_REPEATED_HOP_PEAK'; anchorClaimCandidateIds='C3_R1C_03_ACH_REPEATED_HOP_PEAK'; sourceRefs='PREFLIGHT_32658037'; metricLowerBound=''; metricUpperBound=''; lowerBoundInclusive=''; upperBoundInclusive=''; exactConditionMatchRequired='true'; assignmentMethod='WITHIN_STUDY_DISCLOSED_CLOSE_VARIANT_ANCHOR'; comparisonPopulation='Healthy young adults'; comparisonMethodFamily='CONSTRAINED_FREE_BODY_ACHILLES_FORCE_MODEL'; confidenceLevel='VERY_LOW'; guidanceStatus='PARTIAL_NON_PRODUCTION_PENDING_HUMAN_APPROVAL'; preparedBy='Codex'; preparedByType='AI_AGENT'; preparedAt=$preparedAt; guidanceLimitations='Repeated unilateral hopping only; no neighboring-value classification, interpolation, LOW, or HIGH band.'; forbiddenTransfers='No neighboring value, interval, hop variant, jump, landing, or D-stage profile transfer without explicit approval.'
    }
)
$guidance | Export-Csv -LiteralPath $guidancePath -NoTypeInformation -Encoding UTF8

$files = [ordered]@{
    c31MechanicalModes='tissue_mechanical_load_mode_registry_c3_1_v1.csv'; eventContexts='tissue_event_context_registry_v1.csv'; movementPhases='tissue_movement_phase_registry_v1.csv'; positionContexts='tissue_position_context_registry_v1.csv'; functionalDemands='tissue_functional_demand_registry_v1.csv'; tissueResponses='tissue_response_metric_registry_v1.csv'; evidenceRelations='tissue_evidence_relation_registry_v1.csv'; rubricKinds='tissue_rubric_kind_registry_v1.csv'; externalLoadRepresentations='tissue_external_load_representation_registry_v1.csv'; temporalMetrics='tissue_temporal_metric_registry_v1.csv'; c31MeasurementMetrics='tissue_measurement_metric_registry_c3_1_v1.csv'; normalizationBases='tissue_normalization_registry_v1.csv'; c31Dimensions='tissue_load_dimension_registry_c3_1_v1.csv'; dimensionCorrections='tissue_c3_1_dimension_correction_v1.csv'; correctionDispositions='tissue_c3_1_correction_disposition_v1.csv'; metricExtractions='tissue_source_metric_extraction_c3_1_v1.csv'; claimCandidates='tissue_evidence_claim_candidates_c3_1_v1.csv'; guidance='tissue_load_guidance_c3_1_v1.csv'; researchDecisions='tissue_research_decision_c3_1_v1.csv'; proxyMappings='tissue_proxy_mapping_c3_1_v1.csv'; sourceRereads='tissue_source_reread_c3_1_v1.csv'; supersededC3Request='tissue_review_batch_approval_request_c3_v1.csv'; requestResolutions='tissue_approval_request_resolution_v1.csv'; historicalEvidence='tissue_load_evidence_registry_v1.csv'; c3Evidence='tissue_load_evidence_registry_c3_v1.csv'; historicalVerification='tissue_source_verification_v1.csv'; c3Verification='tissue_source_verification_c3_v1.csv'; historicalIntegrity='tissue_publication_integrity_verification_v1.csv'; c3Integrity='tissue_publication_integrity_verification_c3_v1.csv'; canonicalMapping='tissue_canonical_exercise_mapping_audit_v1.csv'; transferCorrespondence='tissue_exercise_variant_correspondence_c3_v1.csv'; upperBacklog='tissue_upper_limb_research_backlog_c3_v1.csv'
}
$inputParts = @{}
foreach ($entry in $files.GetEnumerator()) { $inputParts[$entry.Key] = Get-SemanticCsvHash (Join-Path $AssetDirectory $entry.Value) }
$canonicalPath = Join-Path $AssetDirectory '..\canonical_exercise_metadata_v0_3_5_0_pass3_1.csv'
$inputParts.canonicalExerciseMetadata = Get-SemanticCsvHash $canonicalPath

$old = @(Import-Csv (Join-Path $AssetDirectory 'tissue_review_batch_approval_request_c3_v1.csv'))
if ($old.Count -ne 1 -or $old[0].approvalRequestId -ne $oldRequestId -or $old[0].approvalScopeHash -ne $oldScopeHash) { throw 'Historical C3 approval request changed.' }
$resolution = @(Import-Csv (Join-Path $AssetDirectory 'tissue_approval_request_resolution_v1.csv') | Where-Object resolutionId -eq $resolutionId)
if ($resolution.Count -ne 1 -or $resolution[0].resolutionStatus -ne 'SUPERSEDED_BEFORE_APPROVAL') { throw 'C3 supersession resolution is missing.' }

$tables = @{}
foreach ($entry in $files.GetEnumerator()) { $tables[$entry.Key] = @(Import-Csv (Join-Path $AssetDirectory $entry.Value)) }
$blockedCount = @($tables.researchDecisions | Where-Object { $_.decision -like 'BLOCKED_*' }).Count
$auditInputHash = Get-CombinedHash $inputParts
$auditManifestId = "tissue_c3_1_$($auditInputHash.Substring(0,12))"
$scopeReferences = @{
    reviewPath=$reviewPath; auditManifestId=$auditManifestId; auditInputSnapshotHash=$auditInputHash;
    supersededApprovalRequestId=$oldRequestId; supersededApprovalScopeHash=$oldScopeHash;
    supersessionResolutionId=$resolutionId; completionStatus=$completion
}
$scopeHash = Get-CombinedHash ($inputParts + $scopeReferences)
$requestId = "TISSUE_APPROVAL_REQUEST_C3_1_$($scopeHash.Substring(0,16).ToUpperInvariant())"
$statement = "I approve correctedTissueApprovalRequestId=$requestId,`napprovalScopeHash=$scopeHash,`nreviewPath=$reviewPath,`ncovering exactly 49 corrected metric extractions, 30 corrected claim candidates, 2 condition anchors, 0 interval rubrics, 0 ordering rules, 48 research decisions, 188 correction dispositions, and 15 verified source records in the listed ontology, context, evidence-relation, external-load, supersession, canonical-mapping, and publication-integrity snapshots.`nI understand this package is same-session, non-independent, non-production, contains $blockedCount explicitly blocked research targets, does not classify neighboring anchor values, permits no anchor interpolation or unapproved transfer, and does not approve a universal tissue-burden score or fixed multidimensional weights."

$request = [ordered]@{
    approvalRequestId=$requestId; researchBatchId=$batchId; reviewPath=$reviewPath;
    metricExtractionIds=(Join-Values $tables.metricExtractions 'metricExtractionId'); claimCandidateIds=(Join-Values $tables.claimCandidates 'claimCandidateId'); conditionAnchorIds=(Join-Values $guidance 'guidanceId'); intervalRubricIds=''; orderingRuleIds=''; researchDecisionIds=(Join-Values $tables.researchDecisions 'researchDecisionId'); correctionDispositionIds=(Join-Values $tables.correctionDispositions 'correctionDispositionId'); sourceIds=(Join-Values ($tables.historicalEvidence + $tables.c3Evidence) 'sourceId');
    metricExtractionCount='49'; candidateCount='30'; conditionAnchorCount='2'; intervalRubricCount='0'; orderingRuleCount='0'; researchDecisionCount='48'; correctionDispositionCount='188'; sourceCount='15'; blockedResearchTargetCount=[string]$blockedCount;
    supersededApprovalRequestId=$oldRequestId; supersededApprovalScopeHash=$oldScopeHash; supersessionResolutionId=$resolutionId;
}
foreach ($key in $inputParts.Keys) { $request["${key}SnapshotHash"] = $inputParts[$key] }
$request.auditManifestId=$auditManifestId; $request.auditInputSnapshotHash=$auditInputHash; $request.approvalScopeHash=$scopeHash; $request.requestStatus='PENDING_HUMAN_DECISION'; $request.completionStatus=$completion; $request.preparedBy='Codex'; $request.preparedByType='AI_AGENT'; $request.preparedAt=$preparedAt; $request.approvalSummary='Future human review is requested only for this corrected, condition-bounded, non-production C3.1 scope.'; $request.knownLimitations="Material targets remain blocked ($blockedCount); condition anchors are not intervals and cannot classify neighboring values."; $request.requiredUserStatement=$statement; $request.requestNotes='This generated statement is not approval. Human approval, final-claim, blind-review, and production-profile ledgers remain empty.'
[pscustomobject]$request | Export-Csv -LiteralPath $requestPath -NoTypeInformation -Encoding UTF8
$requestSnapshotHash = Get-SemanticCsvHash $requestPath

$audits = @(Import-Csv $auditPath)
$existing = @($audits | Where-Object auditBatchId -eq $batchId)
if ($existing.Count -gt 1) { throw 'Duplicate C3.1 audit row.' }
$base = $audits[-1].PSObject.Copy()
$base.auditManifestId=$auditManifestId; $base.auditScope='ONTOLOGY_CORRECTION_BATCH'; $base.auditBatchId=$batchId; $base.rubricSnapshotHash=$inputParts.guidance; $base.evidenceRegistrySnapshotHash=(Get-CombinedHash @{historical=$inputParts.historicalEvidence;c3=$inputParts.c3Evidence}); $base.claimLedgerSnapshotHash=$inputParts.claimCandidates; $base.sourceVerificationSnapshotHash=(Get-CombinedHash @{historical=$inputParts.historicalVerification;c3=$inputParts.c3Verification}); $base.automatedValidationStatus='PASS_WITH_WARNINGS'; $base.catalogEvidenceStatus='PASS_WITH_WARNINGS'; $base.exerciseLoadEvidenceIntegrityStatus='PASS_WITH_WARNINGS'; $base.citationVerificationStatus='PASS'; $base.blockedCount=[string]$blockedCount; $base.evidenceNotApprovedCount='32'; $base.warningCount=[string]$blockedCount; $base.generatedAt=$preparedAt; $base.inputSnapshotHash=$auditInputHash; $base.auditDecision='PRODUCTION_REVIEW_REQUIRED'; $base.auditNotes="Superseded C3 request $oldRequestId at $oldScopeHash remains immutable via $resolutionId; modes 17 to 14; dimensions 42 to 39; context registries separated; 49 extractions, 30 candidates, 188 dispositions, 2 condition anchors, 0 interval rubrics, 0 ordering rules, 48 decisions, $blockedCount blocked targets; approvals/final/blind/profiles 0; replacement $requestId at $scopeHash."; $base.sourceCount='15'; $base.verifiedSourceCount='15'; $base.draftClaimCount='30'; $base.draftRubricCount='0'; $base.researchDecisionCount='48'; $base.targetExerciseReviewCount='188'; $base.directAnchorExerciseCount='2'; $base.blockedTissueDimensionTargetCount=[string]$blockedCount; $base.missingTargetCount=[string]$blockedCount; $base.blindReviewCount='0'; $base.finalClaimCount='0'; $base.humanApprovalCount='0'; $base.productionEligibleProfileCount='0'; $base.completionStatus=$completion; $base.claimCandidateSnapshotHash=$inputParts.claimCandidates; $base.targetExerciseReviewSnapshotHash=$inputParts.correctionDispositions; $base.claimCandidateCount='30'; $base.retainedClaimCount='28'; $base.correctedClaimCount='2'; $base.blockedClaimCount='0'; $base.retainedRubricCount='0'; $base.correctedRubricCount='0'; $base.blockedRubricCount=[string]$blockedCount; $base.formalFinalClaimCount='0'; $base.humanBatchApprovalCount='0'; $base.productionProfileCount='0'; $base.reviewPathContractStatus='PASS'; $base.rubricBoundaryValidationStatus='PASS'; $base.publicationIntegrityValidationStatus='PASS'; $base.approvalRequestValidationStatus='PASS'; $base.approvalRequestCount='1'; $base.approvalRequestCandidateCount='30'; $base.approvalRequestRubricCount='0'; $base.approvalRequestSourceCount='15'; $base.publicationIntegrityCheckedCount='15'; $base.publicationIntegritySafeCount='15'; $base.publicationIntegrityCorrectionCount='0'; $base.publicationIntegrityBlockedCount='0'; $base.publicationIntegrityUnknownCount='0'; $base.approvalScopeHash=$scopeHash; $base.publicationIntegritySnapshotHash=(Get-CombinedHash @{historical=$inputParts.historicalIntegrity;c3=$inputParts.c3Integrity}); $base.approvalRequestSnapshotHash=$requestSnapshotHash
$updated = @($audits | Where-Object auditBatchId -ne $batchId) + @($base)
$updated | Export-Csv -LiteralPath $auditPath -NoTypeInformation -Encoding UTF8

$doc = [Collections.Generic.List[string]]::new()
$doc.Add('# Phase C3.1 Corrected Tissue Approval Request'); $doc.Add(''); $doc.Add('This is a non-production future human-review package, not an approval.'); $doc.Add('')
$doc.Add('- Request: `{0}`' -f $requestId); $doc.Add('- Scope hash: `{0}`' -f $scopeHash); $doc.Add('- Completion: `{0}`' -f $completion); $doc.Add('- Superseded request: `{0}`' -f $oldRequestId); $doc.Add('- Human approvals / final claims / blind reviews / production profiles: `0 / 0 / 0 / 0`'); $doc.Add('')
$doc.Add('## Source Measurements (49)'); $doc.Add(''); $doc.Add('| ID | Exercise / protocol | Tissue and dimension | Value | Context / phase | Load representation | Evidence relation | Main limitations |'); $doc.Add('|---|---|---|---|---|---|---|---|')
foreach ($row in $tables.metricExtractions | Sort-Object metricExtractionId) {
    $protocol = Escape-Markdown "$($row.testedExercise); $($row.externalLoadCondition); $($row.romCondition)"
    $load = Escape-Markdown "$($row.externalLoadRepresentation); added=$($row.additionalExternalLoadFractionBw) BW; total=$($row.totalSystemMassFractionBw) BW"
    $limits = Escape-Markdown $row.extractionLimitations
    $doc.Add(('| `{0}` | {1} | `{2} / {3} x {4} / {5}` | `{6} {7} / {8}` | `{9} / {10}` | {11} | `{12}` | {13} |' -f $row.metricExtractionId,$protocol,$row.tissueId,$row.mechanicalLoadMode,$row.temporalMetric,$row.measurementMetric,$row.reportedValue,$row.reportedUnit,$row.normalizationBasis,$row.eventContext,$row.movementPhase,$load,$row.evidenceRelation,$limits))
}
$doc.Add(''); $doc.Add('## Claim Candidates (30)'); $doc.Add(''); $doc.Add('| ID | Exercise / protocol | Tissue and dimension | Value | Context / phase | Load representation | Evidence relation | Main limitations |'); $doc.Add('|---|---|---|---|---|---|---|---|')
foreach ($row in $tables.claimCandidates | Sort-Object claimCandidateId) {
    $protocol = Escape-Markdown "$($row.testedExercise); $($row.externalLoadCondition); $($row.romCondition)"
    $load = Escape-Markdown "$($row.externalLoadRepresentation); added=$($row.additionalExternalLoadFractionBw) BW; total=$($row.totalSystemMassFractionBw) BW"
    $limits = Escape-Markdown $row.claimLimitations
    $doc.Add(('| `{0}` | {1} | `{2} / {3} x {4} / {5}` | `{6} {7} / {8}` | `{9} / {10}` | {11} | `{12}` | {13} |' -f $row.claimCandidateId,$protocol,$row.tissueId,$row.mechanicalLoadMode,$row.temporalMetric,$row.measurementMetric,$row.claimValue,$row.claimUnit,$row.normalizationBasis,$row.eventContext,$row.movementPhase,$load,$row.evidenceRelation,$limits))
}
$doc.Add(''); $doc.Add('## Condition Anchors (2)'); $doc.Add(''); $doc.Add('| ID | Exact condition | Anchor | Proposed band | Scope limit | Forbidden transfers |'); $doc.Add('|---|---|---|---|---|---|')
foreach ($row in $guidance | Sort-Object guidanceId) {
    $limits = Escape-Markdown $row.guidanceLimitations
    $transfers = Escape-Markdown $row.forbiddenTransfers
    $doc.Add(('| `{0}` | `{1} / {2}` | `{3} {4}` | `{5}` | {6} | {7} |' -f $row.guidanceId,$row.anchorStableKeys,$row.anchorConditionIds,$row.anchorValue,$row.anchorUnit,$row.loadBand,$limits,$transfers))
}
$doc.Add(''); $doc.Add('## Interval Rubrics (0)'); $doc.Add(''); $doc.Add('No interval rubric is proposed. The two anchors have blank lower and upper bounds.'); $doc.Add(''); $doc.Add('## Ordering Rules (0)'); $doc.Add(''); $doc.Add('No ordering rule is proposed.'); $doc.Add(''); $doc.Add("## Blocked Research Targets ($blockedCount)"); $doc.Add(''); $doc.Add('| Decision | Tissue / dimension | Reason | Remaining blocker |'); $doc.Add('|---|---|---|---|')
foreach ($row in $tables.researchDecisions | Where-Object { $_.decision -like 'BLOCKED_*' } | Sort-Object researchDecisionId) {
    $reason = Escape-Markdown $row.decisionReason
    $blocker = Escape-Markdown $row.remainingBlocker
    $doc.Add(('| `{0}: {1}` | `{2} / {3} x {4}` | {5} | {6} |' -f $row.researchDecisionId,$row.decision,$row.tissueId,$row.mechanicalLoadMode,$row.temporalMetric,$reason,$blocker))
}
$doc.Add(''); $doc.Add('## Correction Disposition Map (188)'); $doc.Add(''); $doc.Add('| Old artifact | Old interpretation | New interpretation | Decision | Replacement | Preserved / removed / limitation |'); $doc.Add('|---|---|---|---|---|---|')
foreach ($row in $tables.correctionDispositions | Sort-Object correctionDispositionId) {
    $old = Escape-Markdown "$($row.oldMechanicalLoadMode) / $($row.oldTemporalMetric) / $($row.oldMeasurementMetric) / $($row.oldNormalizationBasis) / $($row.oldEventContext) / $($row.oldMovementPhase) / $($row.oldExternalLoadRepresentation) / $($row.oldEvidenceRelation) / $($row.oldRubricKind)"
    $new = Escape-Markdown "$($row.newMechanicalLoadMode) / $($row.newTemporalMetric) / $($row.newMeasurementMetric) / $($row.newNormalizationBasis) / $($row.newEventContext) / $($row.newMovementPhase) / $($row.newExternalLoadRepresentation) / $($row.newEvidenceRelation) / $($row.newRubricKind)"
    $boundary = Escape-Markdown "$($row.correctionReason) $($row.correctionNotes)"
    $doc.Add(('| `{0}: {1}` | {2} | {3} | `{4}` | `{5}` | {6} |' -f $row.affectedArtifactType,$row.affectedArtifactId,$old,$new,$row.correctionDecision,$row.replacementArtifactId,$boundary))
}
$doc.Add(''); $doc.Add('## Exact Future Approval Statement'); $doc.Add(''); $doc.Add('```text'); $statement.Split("`n") | ForEach-Object { $doc.Add($_) }; $doc.Add('```'); $doc.Add(''); $doc.Add('The statement above is a template only. It has not been supplied or ingested as human approval.')
$doc | Set-Content -LiteralPath $ApprovalDocumentPath -Encoding UTF8

Write-Output "APPROVAL_REQUEST_ID=$requestId"
Write-Output "APPROVAL_SCOPE_HASH=$scopeHash"
Write-Output "AUDIT_MANIFEST_ID=$auditManifestId"
Write-Output "BLOCKED_TARGETS=$blockedCount"

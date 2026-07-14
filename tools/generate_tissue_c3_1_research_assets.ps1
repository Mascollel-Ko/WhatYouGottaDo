param([string]$AssetDirectory = "app/src/main/assets/metadata/tissue_load_v1")

$ErrorActionPreference = "Stop"
$preparedAt = "2026-07-15T00:00:02Z"
$batchId = "TISSUE_C3_1_ONTOLOGY_CORRECTION"

function Export-Rows([string]$name, $rows) { @($rows) | Export-Csv -LiteralPath (Join-Path $AssetDirectory $name) -NoTypeInformation -Encoding UTF8 }
function Copy-Row($row) { $copy=[ordered]@{}; $row.PSObject.Properties | ForEach-Object { $copy[$_.Name]=[string]$_.Value }; $copy }
function Event-Context($row) {
    $name=[string]$row.testedExercise
    if ($name -match 'passive ankle inversion|Machine-applied ankle inversion') { return 'PASSIVE_INVERSION_TEST' }
    if ($name -match 'drop vertical jump') { return 'DROP_LANDING' }
    if ($name -match 'hop.*landing|hopping|maximal forward hop') { return 'HOP_LANDING' }
    if ($row.tissueId -eq 'KNEE_ACL' -or $name -match 'jump landing|Single-legged jump') { return 'JUMP_LANDING' }
    if ($name -match 'heel|calf') { return 'STRENGTH_REPETITION' }
    if ($name -match 'squat') { return 'SQUAT' }
    if ($name -match 'lunge') { return 'LUNGE' }
    if ($name -match 'running') { return 'RUNNING_GAIT' }
    return ''
}
function Movement-Phase($row) {
    if ($row.tissueId -eq 'KNEE_ACL' -and $row.measurementMetric -eq 'MEASURED_LIGAMENT_STRAIN') { return 'PRE_CONTACT' }
    if ($row.temporalMetric -match 'ECCENTRIC') { return 'ECCENTRIC' }
    if ($row.temporalMetric -eq 'ISOMETRIC_HOLD_EXPOSURE') { return 'ISOMETRIC' }
    return 'FULL_EVENT'
}
function Position-Context($row) {
    $rom=[string]$row.romCondition
    if ($rom -match '60 degree|60 degrees') { return 'SPECIFIED_JOINT_ANGLE' }
    if ($rom -match 'Full depth|full heel-raise ROM') { return 'FULL_ROM' }
    if ($rom -match 'end range') { return 'END_RANGE' }
    if ($rom -match 'Not reported') { return 'UNKNOWN_ROM' }
    return ''
}
function Evidence-Relation($measurement) {
    if ($measurement -match '^MEASURED_') { return 'DIRECT_INTERNAL_MEASUREMENT' }
    if ($measurement -match '^MODELED_|_FORCE_TIME_INTEGRAL$') { return 'VALIDATED_INTERNAL_MODEL' }
    if ($measurement -match 'EXTERNAL_JOINT_MOMENT|GROUND_REACTION|KINEMATIC_STABILITY_PROXY') { return 'UNVALIDATED_PROXY' }
    return 'CONTEXT_ONLY'
}
function Load-Fields($row) {
    $text=[string]$row.externalLoadCondition
    $result=[ordered]@{ additionalExternalLoadKg=''; additionalExternalLoadFractionBw=''; totalSystemMassKg=''; totalSystemMassFractionBw=''; relativeLoadPercent1Rm=''; externalLoadPlacement=''; externalLoadDescription=$text; externalLoadRepresentation='NOT_REPORTED'; loadFieldSource='SOURCE_PROTOCOL' }
    if ($row.sourceId -eq 'SRC_PMID_35142563' -and $row.testedExerciseConditionId -eq 'C3COND_35142563_HEEL12BW') {
        $result.additionalExternalLoadFractionBw='0.20'; $result.totalSystemMassFractionBw='1.20'; $result.externalLoadPlacement='Weighted vest attached firmly to torso'; $result.externalLoadDescription='Additional mass equal to 20% of bodyweight'; $result.externalLoadRepresentation='ADDITIONAL_EXTERNAL_LOAD'
    } elseif ($text -match '90 percent 1RM') { $result.relativeLoadPercent1Rm='90'; $result.externalLoadPlacement='Barbell'; $result.externalLoadRepresentation='RELATIVE_LOAD_PERCENT_1RM'
    } elseif ($text -match '^15 kg') { $result.additionalExternalLoadKg='15'; $result.externalLoadPlacement='Thigh'; $result.externalLoadRepresentation='ADDITIONAL_EXTERNAL_LOAD'
    } elseif ($text -match '^No external load') { $result.externalLoadRepresentation='NO_EXTERNAL_LOAD'
    } elseif ($text -match '^Bodyweight') { $result.externalLoadRepresentation='BODYWEIGHT_TASK'
    } elseif ($text -notmatch 'Not reported' -and $text) { $result.externalLoadRepresentation='ADDITIONAL_EXTERNAL_LOAD' }
    return $result
}
function Correct-Id($id) {
    switch ($id) {
        'C3METRIC_21092960_ACL_STRAIN' { 'C31METRIC_21092960_ACL_TENSION_STRAIN' }
        'C3METRIC_31593498_ACL_STRAIN' { 'C31METRIC_31593498_ACL_TENSION_STRAIN' }
        'C3METRIC_35142563_ACH_HEEL12BW' { 'C31METRIC_35142563_ACH_HEEL_ADDED20BW' }
        default { $id }
    }
}

$oldExtractions=@(Import-Csv (Join-Path $AssetDirectory 'tissue_source_metric_extraction_v1.csv'))
$extractions=@($oldExtractions | ForEach-Object {
    $old=$_; $row=Copy-Row $old; $row.metricExtractionId=Correct-Id $old.metricExtractionId
    if ($old.tissueId -eq 'KNEE_ACL' -and $old.measurementMetric -eq 'MEASURED_LIGAMENT_STRAIN') { $row.mechanicalLoadMode='TENSION' }
    $load=Load-Fields $old
    if ($load.externalLoadDescription -eq 'Additional mass equal to 20% of bodyweight') { $row.externalLoadCondition=$load.externalLoadDescription; $row.relativeLoadCondition='Added 0.20 BW; total system mass 1.20 BW' }
    $row.eventContext=Event-Context $old; $row.movementPhase=Movement-Phase $old; $row.positionContext=Position-Context $old
    $row.functionalDemand=if($old.tissueId -eq 'KNEE_ACL'){'IMPACT_ATTENUATION'}else{''}; $row.tissueResponseMetric=''
    $row.evidenceRelation=Evidence-Relation $old.measurementMetric; $row.proxyMappingId=''; $row.proxyTargetDimensionId=''
    $row.proxyValidationStatus=if($row.evidenceRelation -eq 'UNVALIDATED_PROXY'){'NO_VALIDATED_MAPPING'}else{'NOT_APPLICABLE'}
    $row.proxyValidationSourceIds=''; $row.proxyLimitations=if($row.evidenceRelation -eq 'UNVALIDATED_PROXY'){'External metric cannot populate an internal tissue dimension without a validated mapping.'}else{''}
    foreach($key in $load.Keys){$row[$key]=$load[$key]}
    $row.peakTimingRelativeToContactMs=if($old.sourceId -in @('SRC_PMID_21092960','SRC_PMID_31593498')){'-55'}else{''}
    $row.peakTimingDispersionMs=if($old.sourceId -eq 'SRC_PMID_21092960'){'14'}elseif($old.sourceId -eq 'SRC_PMID_31593498'){'35'}else{''}
    $row.peakTimingDispersionType=if($old.sourceId -eq 'SRC_PMID_21092960'){'95_PERCENT_CONFIDENCE_INTERVAL'}elseif($old.sourceId -eq 'SRC_PMID_31593498'){'SOURCE_REPORTED_UNSPECIFIED'}else{''}
    $row.peakTimingReference=if($old.sourceId -eq 'SRC_PMID_21092960'){'BEFORE_GROUND_IMPACT'}elseif($old.sourceId -eq 'SRC_PMID_31593498'){'BEFORE_INITIAL_GROUND_CONTACT'}else{''}
    [pscustomobject]$row
})
Export-Rows 'tissue_source_metric_extraction_c3_1_v1.csv' $extractions

$oldCandidates=@(Import-Csv (Join-Path $AssetDirectory 'tissue_evidence_claim_candidates_multidimensional_v1.csv'))
$candidateIdMap=@{ C3_ACL_JUMP_LANDING_STRAIN='C31_ACL_JUMP_LANDING_TENSION_STRAIN'; C3_ACH_WEIGHTED_HEEL_STRAIN='C31_ACH_WEIGHTED_HEEL_ADDED20BW_STRAIN' }
$candidates=@($oldCandidates | ForEach-Object {
    $old=$_; $row=Copy-Row $old; $row.claimCandidateId=if($candidateIdMap[$old.claimCandidateId]){$candidateIdMap[$old.claimCandidateId]}else{$old.claimCandidateId}
    $row.researchBatchId=$batchId; $row.metricExtractionId=Correct-Id $old.metricExtractionId
    if ($old.tissueId -eq 'KNEE_ACL' -and $old.measurementMetric -eq 'MEASURED_LIGAMENT_STRAIN') { $row.mechanicalLoadMode='TENSION' }
    $load=Load-Fields $old
    if ($old.sourceId -eq 'SRC_PMID_35142563') { $load.additionalExternalLoadFractionBw='0.20'; $load.totalSystemMassFractionBw='1.20'; $load.externalLoadPlacement='Weighted vest attached firmly to torso'; $load.externalLoadDescription='Additional mass equal to 20% of bodyweight'; $load.externalLoadRepresentation='ADDITIONAL_EXTERNAL_LOAD'; $row.externalLoadCondition=$load.externalLoadDescription; $row.relativeLoadCondition='Added 0.20 BW; total system mass 1.20 BW' }
    $row.eventContext=Event-Context $old; $row.movementPhase=Movement-Phase $old; $row.positionContext=Position-Context $old
    $row.functionalDemand=if($old.tissueId -eq 'KNEE_ACL'){'IMPACT_ATTENUATION'}else{''}; $row.tissueResponseMetric=''
    $row.evidenceRelation=Evidence-Relation $old.measurementMetric; $row.proxyMappingId=''; $row.proxyTargetDimensionId=''; $row.proxyValidationStatus='NOT_APPLICABLE'; $row.proxyValidationSourceIds=''; $row.proxyLimitations=''
    foreach($key in $load.Keys){$row[$key]=$load[$key]}
    $row.peakTimingRelativeToContactMs=if($old.sourceId -eq 'SRC_PMID_21092960'){'-55'}else{''}; $row.peakTimingDispersionMs=if($old.sourceId -eq 'SRC_PMID_21092960'){'14'}else{''}; $row.peakTimingDispersionType=if($old.sourceId -eq 'SRC_PMID_21092960'){'95_PERCENT_CONFIDENCE_INTERVAL'}else{''}; $row.peakTimingReference=if($old.sourceId -eq 'SRC_PMID_21092960'){'BEFORE_GROUND_IMPACT'}else{''}
    [pscustomobject]$row
})
Export-Rows 'tissue_evidence_claim_candidates_c3_1_v1.csv' $candidates

$oldDecisions=@(Import-Csv (Join-Path $AssetDirectory 'tissue_research_decision_c3_v1.csv'))
$decisions=@($oldDecisions | ForEach-Object {
    $old=$_; $row=Copy-Row $old; $row.c3ResearchDecisionId=$old.researchDecisionId; $row.researchDecisionId=$old.researchDecisionId -replace '^C3DEC_','C31DEC_'; $row.researchBatchId=$batchId
    $row.eventContext=''; $row.movementPhase=''; $row.positionContext=''; $row.functionalDemand=''; $row.tissueResponseMetric=''; $row.evidenceRelation=Evidence-Relation $old.measurementMetric
    if ($old.mechanicalLoadMode -eq 'ENERGY_STORAGE_RELEASE') { $row.mechanicalLoadMode='TENSION'; $row.functionalDemand='ENERGY_STORAGE_DEMAND'; $row.tissueResponseMetric='ELASTIC_ENERGY_STORAGE' }
    if ($old.researchDecisionId -eq 'C3DEC_ACL_IMPACT_PEAK') { $row.mechanicalLoadMode='TENSION'; $row.eventContext='JUMP_LANDING'; $row.movementPhase='PRE_CONTACT'; $row.functionalDemand='IMPACT_ATTENUATION'; $row.evidenceRelation='DIRECT_INTERNAL_MEASUREMENT' }
    if ($old.mechanicalLoadMode -eq 'IMPACT_STABILIZATION' -and $old.researchDecisionId -ne 'C3DEC_ACL_IMPACT_PEAK') { $row.mechanicalLoadMode=''; $row.functionalDemand='IMPACT_ATTENUATION'; $row.evidenceRelation='UNVALIDATED_PROXY'; $row.decision='BLOCKED_NO_VALIDATED_PROXY' }
    if ($old.decision -eq 'DRAFT_RUBRIC_CREATED') { $row.decision='CONDITION_ANCHOR_CREATED'; $row.decisionReason='Two exact-condition Achilles point anchors were created; no interval or interpolation is approved.' }
    [pscustomobject]$row
})
Export-Rows 'tissue_research_decision_c3_1_v1.csv' $decisions

$rereads=@(
    [pscustomobject][ordered]@{sourceRereadId='C31_READ_35142563';sourceId='SRC_PMID_35142563';researchQuestion='Weighted heel-rise load representation';primarySourceLocator='Journal of Applied Physiology full text, methods and results';verifiedFinding='A weighted vest attached to the torso added mass equal to 20 percent of bodyweight; the reported 1.2 BW denotes total system mass, not added external mass.';correction='Store added fraction 0.20 and total-system fraction 1.20 separately.';newSourceAdded='false';publicationIntegrityStatus='NO_ADVERSE_NOTICE_FOUND';reviewedAt=$preparedAt},
    [pscustomobject][ordered]@{sourceRereadId='C31_READ_21092960';sourceId='SRC_PMID_21092960';researchQuestion='ACL strain timing and mechanism';primarySourceLocator='Primary full text and PubMed record';verifiedFinding='Peak ACL strain was 12 plus or minus 7 percent and occurred 55 plus or minus 14 ms before ground impact at maximal extension.';correction='Represent TENSION x PEAK strain with JUMP_LANDING and PRE_CONTACT context; no directional mechanism.';newSourceAdded='false';publicationIntegrityStatus='NO_ADVERSE_NOTICE_FOUND';reviewedAt=$preparedAt},
    [pscustomobject][ordered]@{sourceRereadId='C31_READ_31593498';sourceId='SRC_PMID_31593498';researchQuestion='ACL strain timing and mechanism';primarySourceLocator='Primary abstract and source methods/results';verifiedFinding='Peak ACL strain occurred 55 plus or minus 35 ms before initial contact during a single-legged jump; no exact directional cause was measured.';correction='Represent TENSION x PEAK strain with JUMP_LANDING and PRE_CONTACT context; retain missing magnitude.';newSourceAdded='false';publicationIntegrityStatus='NO_ADVERSE_NOTICE_FOUND';reviewedAt=$preparedAt},
    [pscustomobject][ordered]@{sourceRereadId='C31_READ_32658037';sourceId='PREFLIGHT_32658037';researchQuestion='Achilles point-anchor boundaries';primarySourceLocator='Primary source exercise table';verifiedFinding='Standing single-leg heel rise 3.0 BW and repeated unilateral hop 7.3 BW are distinct tested conditions.';correction='Treat both as condition anchors; no interval or HIGH band.';newSourceAdded='false';publicationIntegrityStatus='NO_ADVERSE_NOTICE_FOUND';reviewedAt=$preparedAt},
    [pscustomobject][ordered]@{sourceRereadId='C31_READ_25059338';sourceId='SRC_PMID_25059338';researchQuestion='Passive inversion transfer';primarySourceLocator='Primary abstract and methods';verifiedFinding='Device-applied passive inversion force is boundary evidence, not landing or exercise exposure.';correction='Retain source context and prohibit app transfer.';newSourceAdded='false';publicationIntegrityStatus='NO_ADVERSE_NOTICE_FOUND';reviewedAt=$preparedAt},
    [pscustomobject][ordered]@{sourceRereadId='C31_READ_30923576';sourceId='SRC_PMID_30923576';researchQuestion='Kinematic proxy validation';primarySourceLocator='Primary abstract and methods';verifiedFinding='In-vitro kinematic stability is not a validated internal ligament-force mapping.';correction='Classify as UNVALIDATED_PROXY and block rubric/profile use.';newSourceAdded='false';publicationIntegrityStatus='NO_ADVERSE_NOTICE_FOUND';reviewedAt=$preparedAt}
)
Export-Rows 'tissue_source_reread_c3_1_v1.csv' $rereads

$proxyHeader='proxyMappingId,sourceMetric,targetDimensionId,populationScope,taskScope,validationSourceIds,validationStatus,proxyLimitations,preparedBy,preparedByType,preparedAt'
[IO.File]::WriteAllText((Join-Path $AssetDirectory 'tissue_proxy_mapping_c3_1_v1.csv'), $proxyHeader + "`r`n", [Text.UTF8Encoding]::new($true))

$corrections=[Collections.Generic.List[object]]::new()
function Add-Correction($type,$oldId,$newId,$oldMode,$newMode,$decision,$reason,$oldEvidence='',$newEvidence='',$oldRubric='',$newRubric='') {
    $corrections.Add([pscustomobject][ordered]@{correctionDispositionId="C31CORR_$($corrections.Count+1)";affectedArtifactType=$type;affectedArtifactId=$oldId;replacementArtifactId=$newId;oldMechanicalLoadMode=$oldMode;newMechanicalLoadMode=$newMode;oldTemporalMetric='';newTemporalMetric='';oldMeasurementMetric='';newMeasurementMetric='';oldNormalizationBasis='';newNormalizationBasis='';oldEventContext='';newEventContext='';oldMovementPhase='';newMovementPhase='';oldExternalLoadRepresentation='';newExternalLoadRepresentation='';oldEvidenceRelation=$oldEvidence;newEvidenceRelation=$newEvidence;oldRubricKind=$oldRubric;newRubricKind=$newRubric;correctionDecision=$decision;correctionReason=$reason;sourceIds='';requiresAdditionalResearch='false';preparedBy='Codex';preparedByType='AI_AGENT';preparedAt=$preparedAt;correctionNotes='Historical C3 row remains immutable.'})
}
Import-Csv (Join-Path $AssetDirectory 'tissue_mechanical_load_mode_registry_v1.csv') | ForEach-Object { $m=$_.mechanicalLoadMode; if($m -eq 'IMPACT_STABILIZATION'){Add-Correction 'MECHANICAL_MODE' $m '' $m '' 'CONTEXT_SEPARATED' 'Moved to event and functional-demand context.'}elseif($m -eq 'END_RANGE_STRESS'){Add-Correction 'MECHANICAL_MODE' $m '' $m '' 'CONTEXT_SEPARATED' 'Moved to position context.'}elseif($m -eq 'ENERGY_STORAGE_RELEASE'){Add-Correction 'MECHANICAL_MODE' $m 'TENSION' $m 'TENSION' 'CONTEXT_SEPARATED' 'Moved to tissue response under tensile mechanics.'}else{Add-Correction 'MECHANICAL_MODE' $m $m $m $m 'UNCHANGED_VALID' 'Physical mechanical mode remains valid.'} }
Import-Csv (Join-Path $AssetDirectory 'tissue_c3_1_dimension_correction_v1.csv') | ForEach-Object { Add-Correction 'VALID_DIMENSION' $_.oldDimensionId $_.newDimensionId $_.oldMechanicalLoadMode $_.newMechanicalLoadMode $_.correctionDecision $_.correctionReason }
for($i=0;$i -lt $oldExtractions.Count;$i++){ $o=$oldExtractions[$i];$n=$extractions[$i];$changed=$o.metricExtractionId -ne $n.metricExtractionId; Add-Correction 'METRIC_EXTRACTION' $o.metricExtractionId $n.metricExtractionId $o.mechanicalLoadMode $n.mechanicalLoadMode $(if($changed){if($o.sourceId -eq 'SRC_PMID_35142563'){'LOAD_CONDITION_CORRECTED'}else{'RECLASSIFIED'}}else{'UNCHANGED_VALID'}) $(if($changed){'Scientific identity or structured load condition corrected.'}else{'Source metric remains valid.'}) '' $n.evidenceRelation }
for($i=0;$i -lt $oldCandidates.Count;$i++){ $o=$oldCandidates[$i];$n=$candidates[$i];$changed=$o.claimCandidateId -ne $n.claimCandidateId; Add-Correction 'CLAIM_CANDIDATE' $o.claimCandidateId $n.claimCandidateId $o.mechanicalLoadMode $n.mechanicalLoadMode $(if($changed){if($o.sourceId -eq 'SRC_PMID_35142563'){'LOAD_CONDITION_CORRECTED'}else{'RECLASSIFIED'}}else{'UNCHANGED_VALID'}) $(if($changed){'Candidate identity corrected with explicit context or load semantics.'}else{'Condition-bounded candidate remains valid.'}) '' $n.evidenceRelation }
Import-Csv (Join-Path $AssetDirectory 'tissue_load_rubric_v2.csv') | ForEach-Object { Add-Correction 'RUBRIC_OR_ANCHOR' $_.rubricId ($_.rubricId -replace '^C3_RUBRIC_','C31_ANCHOR_') $_.mechanicalLoadMode $_.mechanicalLoadMode 'ANCHOR_RECLASSIFIED' 'Point observation is an exact condition anchor, not an interval.' '' '' 'INTERVAL_BAND' 'CONDITION_ANCHOR' }
for($i=0;$i -lt $oldDecisions.Count;$i++){ $o=$oldDecisions[$i];$n=$decisions[$i];$changed=$o.mechanicalLoadMode -ne $n.mechanicalLoadMode -or $o.decision -ne $n.decision; Add-Correction 'RESEARCH_DECISION' $o.researchDecisionId $n.researchDecisionId $o.mechanicalLoadMode $n.mechanicalLoadMode $(if($changed){'RECLASSIFIED'}else{'UNCHANGED_VALID'}) $(if($changed){'Decision updated for context, response, proxy, or anchor semantics.'}else{'Research decision remains valid.'}) '' $n.evidenceRelation }
Export-Rows 'tissue_c3_1_correction_disposition_v1.csv' $corrections

"EXTRACTIONS=$($extractions.Count)"
"CANDIDATES=$($candidates.Count)"
"DECISIONS=$($decisions.Count)"
"CORRECTIONS=$($corrections.Count)"

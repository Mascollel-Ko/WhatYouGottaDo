param(
    [string]$AssetDirectory = "app/src/main/assets/metadata/tissue_load_v1"
)

$ErrorActionPreference = "Stop"
$batch = "TISSUE_REAUDIT_C_LOWER_KNEE_ANKLE"
$reviewedAt = "2026-07-14T00:00:00Z"
$utf8 = [Text.UTF8Encoding]::new($false)

function Get-TextHash([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = $sha.ComputeHash($utf8.GetBytes($Text)) } finally { $sha.Dispose() }
    return (([BitConverter]::ToString($hash)) -replace '-', '').ToLowerInvariant()
}

function Get-SemanticCsvHash([string]$Path) {
    $rawHeader = (Get-Content -LiteralPath $Path -TotalCount 1).TrimStart([char]0xFEFF)
    $headers = $rawHeader.Split(',') | ForEach-Object { $_.Trim().Trim('"') }
    [string[]]$rows = @(Import-Csv -LiteralPath $Path | ForEach-Object {
        $row = $_
        (($headers | ForEach-Object { [string]$row.$_ }) -join [char]0x1F)
    })
    [Array]::Sort($rows, [StringComparer]::Ordinal)
    return Get-TextHash (($headers -join [char]0x1F) + "`n" + ($rows -join [char]0x1E))
}

function Get-CombinedHash([hashtable]$Parts) {
    $text = ($Parts.Keys | Sort-Object | ForEach-Object { "$_=$($Parts[$_])" }) -join "`n"
    return Get-TextHash $text
}

function Get-Id([string]$Prefix, [string]$Value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)) } finally { $sha.Dispose() }
    return "$Prefix$(([BitConverter]::ToString($hash[0..7]) -replace '-', '').ToUpperInvariant())"
}

function Write-Table([string]$Name, [string[]]$Headers, [object[]]$Rows) {
    $path = Join-Path $AssetDirectory $Name
    $Rows | Select-Object $Headers | Export-Csv -LiteralPath $path -NoTypeInformation -Encoding UTF8
}

$sources = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory "tissue_load_evidence_registry_v1.csv"))
$drafts = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory "tissue_evidence_claims_draft_v1.csv"))
$sourceById = @{}
$sources | ForEach-Object { $sourceById[$_.sourceId] = $_ }

$achillesMethod = "3D motion capture and ground reaction force; plantarflexion moment divided by a fixed 5 cm moment arm; modeled Achilles tendon force normalized to participant bodyweight."
$patellarMethod = "3D motion capture and force plates; knee extension moment divided by an angle-specific patellar tendon moment arm; modeled tendon force normalized to participant bodyweight."
$pfjMethod = "3D motion capture and force plates; model-estimated quadriceps and patellofemoral joint force; loading index uses 50% normalized peak and 50% normalized impulse."

$specs = @{
    DCLM_ACH_PEAK_SEATED_CALF = @{
        exercise="Bilateral and unilateral seated heel raises with 15 kg placed on the thigh"; metric="Modeled peak Achilles tendon force range across two seated heel-raise conditions"
        value=""; lower="0.5"; upper="0.7"; unit="BW"; valueType="REPORTED_RANGE"; condition="Tested bilateral and unilateral seated heel raises with 15 kg placed on the thigh; laboratory footwear and force plates; non-fatigued cohort."
        exerciseMatch="CLOSE_VARIANT"; dimensionMatch="DIMENSION_SUPPORTED_BY_VALIDATED_MODEL"; band="LOW"; bandBasis="WITHIN_STUDY_RELATIVE_ORDER"; support="PARTIALLY_SUPPORTED"
        actions="CORRECT_VALUE|CORRECT_CONDITION|RETAIN_WITH_LIMITATION"; adjudications=""; method=$achillesMethod; claimType="MODELED_ACHILLES_TENDON_PEAK_FORCE_RANGE"
        paraphrase="Across the tested bilateral and unilateral seated heel raises with 15 kg on the thigh, modeled peak Achilles tendon force group means ranged from 0.5 to 0.7 BW."
        locator="Baxter et al. 2021, Table 1, seated heel raise (2-leg and 1-leg) rows"; confidence="LOW"
        limitations="The 0.5 to 0.7 BW result is a range across two reported group means, not one 0.6 BW group mean; the source used 15 kg thigh loading and a modeled fixed moment arm."
    }
    DCLM_ACH_PEAK_SINGLE_CALF = @{
        exercise="Standing single-leg heel raise"; metric="Modeled peak Achilles tendon force"; value="3.0"; lower="2.7"; upper="3.3"; unit="BW"; valueType="GROUP_MEAN"
        condition="Tested standing single-leg heel raise at study cadence; bodyweight; laboratory footwear and force plates; non-fatigued cohort."
        exerciseMatch="EXACT_PROTOCOL_MATCH"; dimensionMatch="DIMENSION_SUPPORTED_BY_VALIDATED_MODEL"; band="MODERATE"; bandBasis="WITHIN_STUDY_RELATIVE_ORDER"; support="SUPPORTED_AS_DIRECT"
        actions="RETAIN_WITH_LIMITATION"; adjudications=""; method=$achillesMethod; claimType="MODELED_ACHILLES_TENDON_PEAK_FORCE"
        paraphrase="During the tested standing single-leg heel raise, modeled peak Achilles tendon force was 3.0 plus or minus 0.3 BW."
        locator="Baxter et al. 2021, Table 1, standing heel raise (1-leg) row"; confidence="MODERATE"
        limitations="Healthy sample of eight; force was modeled with a fixed 5 cm moment arm and study cadence may differ from app prescriptions."
    }
    DCLM_ACH_PEAK_SINGLE_HOP = @{
        exercise="Repeated single-leg forward and lateral hopping"; metric="Modeled peak Achilles tendon force"; value="7.3"; lower=""; upper=""; unit="BW"; valueType="GROUP_MEAN"
        condition="Tested repeated single-leg forward and lateral hopping; bodyweight; laboratory footwear and force plates; app target adds a stick landing."
        exerciseMatch="CLOSE_VARIANT"; dimensionMatch="DIMENSION_SUPPORTED_BY_VALIDATED_MODEL"; band="VERY_HIGH"; bandBasis="CLOSE_VARIANT_TRANSFER"; support="SUPPORTED_AS_CLOSE_VARIANT"
        actions="CORRECT_METRIC|RETAIN_WITH_LIMITATION"; adjudications="USER_ADJUDICATION_ACHILLES_HOP_TRANSFER_V1"; method=$achillesMethod; claimType="MODELED_ACHILLES_TENDON_PEAK_FORCE"
        paraphrase="Repeated single-leg forward and lateral hopping produced modeled peak Achilles tendon force group means of 7.3 BW; transfer to the app hop-and-stick exercise is a close variant."
        locator="Baxter et al. 2021, Table 1, lateral and forward hopping (1-leg) rows"; confidence="LOW"
        limitations="The source tested repeated hopping rather than the exact hop-and-stick protocol; transfer is explicitly limited to a close variant."
    }
    DCLM_ACH_RATE_SINGLE_CALF = @{
        exercise="Standing single-leg heel raise"; metric="Modeled Achilles tendon peak loading rate over a 5% moving window"; value="13.1"; lower="9.7"; upper="16.5"; unit="BW/s"; valueType="GROUP_MEAN"
        condition="Tested standing single-leg heel raise at study cadence; bodyweight; laboratory footwear and force plates; non-fatigued cohort."
        exerciseMatch="EXACT_PROTOCOL_MATCH"; dimensionMatch="DIMENSION_SUPPORTED_BY_VALIDATED_MODEL"; band=""; bandBasis="INSUFFICIENT_BASIS"; support="SUPPORTED_AS_DIRECT"
        actions="RETAIN_WITH_LIMITATION|REMOVE_BAND"; adjudications=""; method=$achillesMethod; claimType="MODELED_ACHILLES_TENDON_LOADING_RATE"
        paraphrase="During the tested standing single-leg heel raise, modeled Achilles tendon loading rate was 13.1 plus or minus 3.4 BW per second."
        locator="Baxter et al. 2021, Table 1, standing heel raise (1-leg) row and loading-rate method"; confidence="MODERATE"
        limitations="Loading rate is the peak change over a 5% moving window; no independent app-wide loading-rate band threshold is supported."
    }
    DCLM_PAT_PEAK_FULL_SQUAT = @{
        exercise="Double-leg full-depth squat"; metric="Modeled peak patellar tendon force"; value="3.4"; lower="2.7"; upper="4.1"; unit="BW"; valueType="GROUP_MEAN"
        condition="Tested bodyweight double-leg full-depth squat at study-defined speed and range; laboratory force plates; non-fatigued cohort."
        exerciseMatch="EXACT_PROTOCOL_MATCH"; dimensionMatch="DIMENSION_SUPPORTED_BY_VALIDATED_MODEL"; band="MODERATE"; bandBasis="WITHIN_STUDY_RELATIVE_ORDER"; support="SUPPORTED_AS_DIRECT"
        actions="RETAIN_WITH_LIMITATION"; adjudications=""; method=$patellarMethod; claimType="MODELED_PATELLAR_TENDON_PEAK_FORCE"
        paraphrase="During the tested double-leg full-depth squat, modeled peak patellar tendon force was 3.4 plus or minus 0.7 BW."
        locator="Scattone Silva et al. 2024, Table 1, 2-leg squat (full) row"; confidence="MODERATE"
        limitations="Healthy cohort; sagittal-plane constrained model omitted hamstring coactivation and the source tier is a within-study composite index."
    }
    DCLM_PAT_PEAK_BULGARIAN = @{
        exercise="Bulgarian squat"; metric="Modeled peak patellar tendon force"; value="3.0"; lower="2.5"; upper="3.5"; unit="BW"; valueType="GROUP_MEAN"
        condition="Tested bodyweight Bulgarian squat at study-defined speed and range; laboratory force plates; non-fatigued cohort."
        exerciseMatch="CLOSE_VARIANT"; dimensionMatch="DIMENSION_SUPPORTED_BY_VALIDATED_MODEL"; band="MODERATE"; bandBasis="WITHIN_STUDY_RELATIVE_ORDER"; support="SUPPORTED_AS_CLOSE_VARIANT"
        actions="RETAIN_WITH_LIMITATION"; adjudications=""; method=$patellarMethod; claimType="MODELED_PATELLAR_TENDON_PEAK_FORCE"
        paraphrase="During the tested Bulgarian squat, modeled peak patellar tendon force was 3.0 plus or minus 0.5 BW."
        locator="Scattone Silva et al. 2024, Table 1, Bulgarian squat row"; confidence="LOW"
        limitations="The canonical rear-foot-elevated split squat lacks a fully specified protocol match; healthy-cohort force was model-estimated."
    }
}

$pfjSpecs = @{
    DCLM_PFJ_COMP_60_SQUAT = @("Double-leg squat to 60 degrees", "2.5", "1.6", "3.4", "LOW", "CLOSE_VARIANT", "Song et al. 2023, Table 1, double-leg squat (60 degrees) row")
    DCLM_PFJ_COMP_FULL_SQUAT = @("Double-leg full-depth squat", "4.5", "3.7", "5.3", "MODERATE", "EXACT_PROTOCOL_MATCH", "Song et al. 2023, Table 1, double-leg squat (full depth) row")
    DCLM_PFJ_COMP_BULGARIAN = @("Bulgarian squat", "4.7", "4.0", "5.4", "MODERATE", "CLOSE_VARIANT", "Song et al. 2023, Table 1 and Results, Bulgarian squat row")
    DCLM_PFJ_COMP_LUNGE = @("Study-defined lunge", "5.1", "4.3", "5.9", "MODERATE", "CLOSE_VARIANT", "Song et al. 2023, Table 1, lunge row")
    DCLM_PFJ_COMP_DROP_JUMP = @("Double-leg drop vertical jump", "6.8", "5.4", "8.2", "MODERATE", "CLOSE_VARIANT", "Song et al. 2023, Table 1, double-leg drop vertical jump row")
    DCLM_PFJ_COMP_SINGLE_HOP = @("Single-leg maximal forward hop", "6.3", "5.1", "7.5", "MODERATE", "CLOSE_VARIANT", "Song et al. 2023, Table 1, single-leg maximal forward hop row")
}

foreach ($id in $pfjSpecs.Keys) {
    $v = $pfjSpecs[$id]
    $specs[$id] = @{
        exercise=$v[0]; metric="Modeled peak patellofemoral joint force with source composite loading-index tier"; value=$v[1]; lower=$v[2]; upper=$v[3]; unit="BW"; valueType="GROUP_MEAN"
        condition="Tested $($v[0]); bodyweight; study-defined speed and range; laboratory force plates; non-fatigued healthy cohort."
        exerciseMatch=$v[5]; dimensionMatch="DIMENSION_SUPPORTED_BY_EXPLICIT_PROXY"; band=$v[4]; bandBasis="WITHIN_STUDY_RELATIVE_ORDER"; support="SUPPORTED_AS_EXPLICIT_PROXY"
        actions="CORRECT_METRIC|RETAIN_WITH_LIMITATION"; adjudications="USER_ADJUDICATION_PFJ_COMPOSITE_COMPRESSION_V1"; method=$pfjMethod; claimType="MODELED_PFJ_FORCE_WITH_COMPOSITE_LOADING_INDEX_PROXY"
        paraphrase="For the tested $($v[0]), modeled peak patellofemoral joint force was $($v[1]) BW; the candidate band uses the source composite loading index of 50% normalized peak plus 50% normalized impulse, not a pure peak-force threshold."
        locator=$v[6]; confidence="LOW"
        limitations="The compression rubric uses a source-defined composite loading index as an explicit proxy; peak force is model-estimated in healthy adults and is not itself the band threshold."
    }
}

$reaudits = foreach ($draft in $drafts) {
    $spec = $specs[$draft.draftClaimId]
    if ($null -eq $spec) { throw "No Phase C re-audit specification for $($draft.draftClaimId)." }
    $source = $sourceById[$draft.sourceId]
    if ($null -eq $source) { throw "Unknown source $($draft.sourceId)." }
    $reauditId = Get-Id "REAUDIT_C_" "$batch|$($draft.draftClaimId)|$($draft.sourceId)"
    [pscustomobject][ordered]@{
        reauditId=$reauditId; reviewBatchId=$batch; draftClaimId=$draft.draftClaimId; sourceId=$draft.sourceId; stableKey=$draft.stableKey; tissueId=$draft.tissueId; loadDimension=$draft.loadDimension
        reviewMode="SAME_SESSION_EVIDENCE_REAUDIT"; independenceStatus="NOT_INDEPENDENT"; identifierVerificationStatus=$source.identifierVerificationStatus
        bibliographicMatchStatus=$source.bibliographicMatchStatus; publicationIntegrityStatus=$source.publicationIntegrityStatus; evidenceAccessLevel="TABLE"; evidenceLocatorType="TABLE"
        evidenceLocator=$spec.locator; evidenceLocatorVerified="true"; exerciseCorrespondence=$spec.exerciseMatch; tissueCorrespondence="TISSUE_SUPPORTED_BY_VALIDATED_MODEL"
        dimensionCorrespondence=$spec.dimensionMatch; verifiedExercise=$spec.exercise; verifiedTissue=$draft.tissueId; verifiedDimension=$draft.loadDimension
        verifiedDirection="CONDITION_SPECIFIC_MAGNITUDE"; verifiedMetric=$spec.metric; verifiedValue=$spec.value; verifiedLowerBound=$spec.lower; verifiedUpperBound=$spec.upper
        verifiedUnit=$spec.unit; valueType=$spec.valueType; normalizationBasis="Normalized to participant bodyweight"; crossStudyComparability="NOT_CROSS_STUDY_COMPARABLE"
        verifiedCondition=$spec.condition; maximumDefensibleBand=$spec.band; bandBasis=$spec.bandBasis; claimSupportStatus=$spec.support; recommendedAction=$spec.actions
        userAdjudicationIds=$spec.adjudications; reviewedBy="Codex"; reviewedByType="AI_AGENT"; reviewedAt=$reviewedAt; limitations=$spec.limitations
        reauditNotes="Source identity and bibliographic metadata were revalidated live; the cited table and method text were reopened in the same session. This is not independent blind review."
    }
}

$candidates = foreach ($reaudit in $reaudits) {
    $spec = $specs[$reaudit.draftClaimId]
    [pscustomobject][ordered]@{
        claimCandidateId=(Get-Id "CLAIM_C_" "$batch|$($reaudit.draftClaimId)|$($reaudit.reauditId)"); reviewBatchId=$batch; draftClaimId=$reaudit.draftClaimId
        reauditId=$reaudit.reauditId; sourceId=$reaudit.sourceId; stableKey=$reaudit.stableKey; tissueId=$reaudit.tissueId; loadDimension=$reaudit.loadDimension
        candidateClaimType=$spec.claimType; candidateClaimParaphrase=$spec.paraphrase; candidateClaimDirection="CONDITION_SPECIFIC_MAGNITUDE"; candidateValue=$reaudit.verifiedValue
        candidateLowerBound=$reaudit.verifiedLowerBound; candidateUpperBound=$reaudit.verifiedUpperBound; candidateUnit=$reaudit.verifiedUnit
        normalizationBasis=$reaudit.normalizationBasis; supportedCondition=$reaudit.verifiedCondition; measurementMethod=$spec.method; evidenceLocatorType=$reaudit.evidenceLocatorType
        evidenceLocator=$reaudit.evidenceLocator; evidenceAccessLevel=$reaudit.evidenceAccessLevel; exerciseCorrespondence=$reaudit.exerciseCorrespondence
        tissueCorrespondence=$reaudit.tissueCorrespondence; dimensionCorrespondence=$reaudit.dimensionCorrespondence; crossStudyComparability=$reaudit.crossStudyComparability
        maximumDefensibleBand=$reaudit.maximumDefensibleBand; bandBasis=$reaudit.bandBasis; claimSupportStatus=$reaudit.claimSupportStatus; confidenceLevel=$spec.confidence
        userAdjudicationIds=$reaudit.userAdjudicationIds; reviewMode=$reaudit.reviewMode; independenceStatus=$reaudit.independenceStatus
        technicalVerificationStatus="TECHNICALLY_REAUDITED_WITH_LIMITATIONS"; productionEligibility="false"; preparedBy="Codex"; preparedByType="AI_AGENT"; preparedAt=$reviewedAt
        reviewedBy="Codex"; reviewedByType="AI_AGENT"; reviewedAt=$reviewedAt; humanApprovedBy=""; humanApprovedAt=""
        candidateNotes="Technically re-audited claim candidate only; explicit human batch approval and formal final-claim promotion remain required. $($spec.limitations)"
    }
}

$reauditHeaders = @("reauditId","reviewBatchId","draftClaimId","sourceId","stableKey","tissueId","loadDimension","reviewMode","independenceStatus","identifierVerificationStatus","bibliographicMatchStatus","publicationIntegrityStatus","evidenceAccessLevel","evidenceLocatorType","evidenceLocator","evidenceLocatorVerified","exerciseCorrespondence","tissueCorrespondence","dimensionCorrespondence","verifiedExercise","verifiedTissue","verifiedDimension","verifiedDirection","verifiedMetric","verifiedValue","verifiedLowerBound","verifiedUpperBound","verifiedUnit","valueType","normalizationBasis","crossStudyComparability","verifiedCondition","maximumDefensibleBand","bandBasis","claimSupportStatus","recommendedAction","userAdjudicationIds","reviewedBy","reviewedByType","reviewedAt","limitations","reauditNotes")
$candidateHeaders = @("claimCandidateId","reviewBatchId","draftClaimId","reauditId","sourceId","stableKey","tissueId","loadDimension","candidateClaimType","candidateClaimParaphrase","candidateClaimDirection","candidateValue","candidateLowerBound","candidateUpperBound","candidateUnit","normalizationBasis","supportedCondition","measurementMethod","evidenceLocatorType","evidenceLocator","evidenceAccessLevel","exerciseCorrespondence","tissueCorrespondence","dimensionCorrespondence","crossStudyComparability","maximumDefensibleBand","bandBasis","claimSupportStatus","confidenceLevel","userAdjudicationIds","reviewMode","independenceStatus","technicalVerificationStatus","productionEligibility","preparedBy","preparedByType","preparedAt","reviewedBy","reviewedByType","reviewedAt","humanApprovedBy","humanApprovedAt","candidateNotes")

Write-Table "tissue_evidence_reaudit_v1.csv" $reauditHeaders @($reaudits)
Write-Table "tissue_evidence_claim_candidates_v1.csv" $candidateHeaders @($candidates)

$adjudicationHeaders = @("adjudicationId","reviewBatchId","adjudicationScope","stableKeys","tissueIds","loadDimensions","rubricIds","draftClaimIds","decision","decisionRationale","requiredDisclosure","decisionEffect","decisionActorType","decisionSource","decisionRecordedBy","decisionRecordedAt","isBatchApproval","productionEligibilityEffect","adjudicationNotes")
$adjudications = @(
    [pscustomobject][ordered]@{
        adjudicationId="USER_ADJUDICATION_ACHILLES_HOP_TRANSFER_V1"; reviewBatchId=$batch; adjudicationScope="CLAIM_AND_RUBRIC_INTERPRETATION"
        stableKeys="ex_314df428"; tissueIds="ACHILLES_TENDON"; loadDimensions="PEAK_TENSILE_LOAD"; rubricIds="RUBRIC_ACH_PEAK_VERY_HIGH"; draftClaimIds="DCLM_ACH_PEAK_SINGLE_HOP"
        decision="Single-leg hopping evidence may be transferred to the canonical exercise single-leg hop and stick for Achilles PEAK_TENSILE_LOAD. The hop-and-stick requirement does not constitute a sufficiently material biomechanical difference to prevent a VERY_HIGH classification of peak Achilles tendon loading. The transfer remains a close-variant transfer rather than an exact protocol match."
        decisionRationale="The explicit user instruction accepts transfer of the source repeated single-leg hopping result to the app hop-and-stick exercise while preserving the protocol difference."
        requiredDisclosure="exerciseCorrespondence = CLOSE_VARIANT; bandBasis = CLOSE_VARIANT_TRANSFER; maximumDefensibleBand = VERY_HIGH"
        decisionEffect="PERMITS_CLOSE_VARIANT_TRANSFER_AND_VERY_HIGH_CANDIDATE_BAND_WITH_DISCLOSURE"
        decisionActorType="HUMAN_USER"; decisionSource="EXPLICIT_USER_INSTRUCTION"; decisionRecordedBy="Codex"; decisionRecordedAt=$reviewedAt
        isBatchApproval="false"; productionEligibilityEffect="NONE"
        adjudicationNotes="This interpretation decision does not validate an incorrect numeric value, waive source verification, approve the evidence batch, or create production eligibility."
    },
    [pscustomobject][ordered]@{
        adjudicationId="USER_ADJUDICATION_PFJ_COMPOSITE_COMPRESSION_V1"; reviewBatchId=$batch; adjudicationScope="CLAIM_AND_RUBRIC_INTERPRETATION"
        stableKeys="ex_cb3c4dc2|ex_bb728af2|ex_64644b5e|ex_d6726746|ex_314df428"; tissueIds="KNEE_PATELLOFEMORAL"; loadDimensions="COMPRESSION"; rubricIds="RUBRIC_PFJ_COMP_LOW|RUBRIC_PFJ_COMP_MODERATE"
        draftClaimIds="DCLM_PFJ_COMP_60_SQUAT|DCLM_PFJ_COMP_FULL_SQUAT|DCLM_PFJ_COMP_BULGARIAN|DCLM_PFJ_COMP_LUNGE|DCLM_PFJ_COMP_DROP_JUMP|DCLM_PFJ_COMP_SINGLE_HOP"
        decision="The studied patellofemoral composite loading index is accepted as sufficient evidence for the KNEE_PATELLOFEMORAL COMPRESSION rubric. Its composite nature remains visible and the metric is not represented as pure peak compression force."
        decisionRationale="The explicit user instruction accepts the source-defined peak-plus-impulse loading index as compression evidence only when its proxy and composite construction remain disclosed."
        requiredDisclosure="verifiedMetric = source-defined composite patellofemoral loading index; dimensionCorrespondence = DIMENSION_SUPPORTED_BY_EXPLICIT_PROXY; metric must remain composite and must not be described as pure peak compression force"
        decisionEffect="PERMITS_COMPOSITE_INDEX_AS_EXPLICIT_COMPRESSION_PROXY_WITH_DISCLOSURE"
        decisionActorType="HUMAN_USER"; decisionSource="EXPLICIT_USER_INSTRUCTION"; decisionRecordedBy="Codex"; decisionRecordedAt=$reviewedAt
        isBatchApproval="false"; productionEligibilityEffect="NONE"
        adjudicationNotes="This interpretation decision does not waive value, unit, condition, or source verification and does not create batch approval or production eligibility."
    }
)
Write-Table "tissue_user_adjudication_v1.csv" $adjudicationHeaders $adjudications

$reauditByDraft = @{}
$reaudits | ForEach-Object { $reauditByDraft[$_.draftClaimId] = $_ }
$candidateByDraft = @{}
$candidates | ForEach-Object { $candidateByDraft[$_.draftClaimId] = $_ }
$rubricSpecs = @{
    RUBRIC_ACH_PEAK_LOW = @{
        action="CORRECT_ANCHOR"; drafts=@("DCLM_ACH_PEAK_SEATED_CALF"); adjudications=""
        metric="MODELED_PEAK_ACHILLES_TENDON_FORCE_BW_WITHIN_STUDY_ORDER"
        conditions="Tested bilateral and unilateral seated heel raises with 15 kg placed on the thigh; healthy adults; laboratory force plates; modeled Achilles tendon force normalized to bodyweight; 0.5 to 0.7 BW is a range across two reported group means, not one group mean."
        notes="Same-session re-audit corrected the anchor representation and condition. The LOW band remains a within-study relative-order candidate with modeled-force and small-sample limitations; independent review and human batch approval remain pending."
    }
    RUBRIC_ACH_PEAK_MODERATE = @{
        action="RETAIN_WITH_LIMITATIONS"; drafts=@("DCLM_ACH_PEAK_SINGLE_CALF"); adjudications=""
        metric="MODELED_PEAK_ACHILLES_TENDON_FORCE_BW_WITHIN_STUDY_ORDER"
        conditions="Tested standing single-leg heel raise at study cadence; healthy adults; bodyweight; laboratory force plates; modeled Achilles tendon force normalized to bodyweight using a fixed 5 cm moment arm."
        notes="Same-session re-audit retained the MODERATE within-study anchor with explicit model, cadence, and healthy-cohort limitations; independent review and human batch approval remain pending."
    }
    RUBRIC_ACH_PEAK_VERY_HIGH = @{
        action="RETAIN_WITH_LIMITATIONS"; drafts=@("DCLM_ACH_PEAK_SINGLE_HOP"); adjudications="USER_ADJUDICATION_ACHILLES_HOP_TRANSFER_V1"
        metric="MODELED_PEAK_ACHILLES_TENDON_FORCE_BW_WITHIN_STUDY_ORDER"
        conditions="Tested repeated unilateral forward and lateral hopping; app stableKey ex_314df428 is a close hop-and-stick variant; healthy adults; bodyweight; laboratory force plates; modeled Achilles tendon force normalized to bodyweight."
        notes="The VERY_HIGH candidate is retained only as a CLOSE_VARIANT transfer under the linked user adjudication. The source did not test the exact app hop-and-stick protocol; independent review and human batch approval remain pending."
    }
    RUBRIC_PFJ_COMP_LOW = @{
        action="CORRECT_METRIC"; drafts=@("DCLM_PFJ_COMP_60_SQUAT"); adjudications="USER_ADJUDICATION_PFJ_COMPOSITE_COMPRESSION_V1"
        metric="COMPOSITE_PATELLOFEMORAL_JOINT_LOADING_INDEX_50_PERCENT_PEAK_50_PERCENT_IMPULSE"
        conditions="Tested 60-degree bilateral bodyweight squat; healthy adults; laboratory force plates; model-estimated patellofemoral force; source-defined index combines 50% normalized peak and 50% normalized impulse; full-ROM use is not implied."
        notes="Same-session re-audit corrected the metric disclosure. LOW is a source-defined composite-index tier used as an explicit compression proxy, not a pure peak-force threshold; independent review and human batch approval remain pending."
    }
    RUBRIC_PFJ_COMP_MODERATE = @{
        action="CORRECT_METRIC"; drafts=@("DCLM_PFJ_COMP_FULL_SQUAT","DCLM_PFJ_COMP_BULGARIAN","DCLM_PFJ_COMP_LUNGE","DCLM_PFJ_COMP_DROP_JUMP","DCLM_PFJ_COMP_SINGLE_HOP"); adjudications="USER_ADJUDICATION_PFJ_COMPOSITE_COMPRESSION_V1"
        metric="COMPOSITE_PATELLOFEMORAL_JOINT_LOADING_INDEX_50_PERCENT_PEAK_50_PERCENT_IMPULSE"
        conditions="Tested full bilateral squat, Bulgarian squat, study-defined lunge, double-leg drop vertical jump, and single-leg maximal forward hop; healthy adults; laboratory force plates; model-estimated patellofemoral force; source-defined index combines 50% normalized peak and 50% normalized impulse."
        notes="Same-session re-audit corrected the metric disclosure. MODERATE is a source-defined composite-index tier used as an explicit compression proxy; protocol transfers and model assumptions remain limitations, and independent review and human batch approval remain pending."
    }
}

$rubricHeaders = @("rubricId","tissueId","loadDimension","loadBand","metricType","metricLowerBound","lowerBoundInclusive","metricUpperBound","upperBoundInclusive","boundarySemanticsVersion","metricUnit","anchorStableKeys","anchorConditions","anchorClaimIds","researchDecisionId","draftClaimIds","assignmentMethod","evidenceSetId","evidenceClaimIds","sourceRefs","confidenceLevel","rubricStatus","preparedBy","preparedByType","preparedAt","blindReviewedBy","blindReviewedByType","blindReviewedAt","humanApprovedBy","humanApprovedAt","reauditAction","reauditIds","claimCandidateIds","userAdjudicationIds","reviewMode","independenceStatus","rubricNotes")
$rubrics = foreach ($row in @(Import-Csv -LiteralPath (Join-Path $AssetDirectory "tissue_load_band_rubric_v1.csv"))) {
    $spec = $rubricSpecs[$row.rubricId]
    if ($null -eq $spec) { throw "No Phase C rubric specification for $($row.rubricId)." }
    [pscustomobject][ordered]@{
        rubricId=$row.rubricId; tissueId=$row.tissueId; loadDimension=$row.loadDimension; loadBand=$row.loadBand; metricType=$spec.metric
        metricLowerBound=$row.metricLowerBound; lowerBoundInclusive=$row.lowerBoundInclusive; metricUpperBound=$row.metricUpperBound
        upperBoundInclusive=$row.upperBoundInclusive; boundarySemanticsVersion=$row.boundarySemanticsVersion; metricUnit=$row.metricUnit; anchorStableKeys=$row.anchorStableKeys
        anchorConditions=$spec.conditions; anchorClaimIds=$row.anchorClaimIds; researchDecisionId=$row.researchDecisionId; draftClaimIds=$row.draftClaimIds
        assignmentMethod=$row.assignmentMethod; evidenceSetId=$row.evidenceSetId; evidenceClaimIds=$row.evidenceClaimIds; sourceRefs=$row.sourceRefs
        confidenceLevel=$row.confidenceLevel; rubricStatus="REAUDITED_WITH_LIMITATIONS"; preparedBy=$row.preparedBy; preparedByType=$row.preparedByType; preparedAt=$row.preparedAt
        blindReviewedBy=""; blindReviewedByType=""; blindReviewedAt=""; humanApprovedBy=""; humanApprovedAt=""; reauditAction=$spec.action
        reauditIds=(($spec.drafts | ForEach-Object { $reauditByDraft[$_].reauditId }) -join "|")
        claimCandidateIds=(($spec.drafts | ForEach-Object { $candidateByDraft[$_].claimCandidateId }) -join "|")
        userAdjudicationIds=$spec.adjudications; reviewMode="SAME_SESSION_EVIDENCE_REAUDIT"; independenceStatus="NOT_INDEPENDENT"; rubricNotes=$spec.notes
    }
}
Write-Table "tissue_load_band_rubric_v1.csv" $rubricHeaders @($rubrics)

$hashes = @{
    evidenceRegistry = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_load_evidence_registry_v1.csv")
    sourceVerification = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_source_verification_v1.csv")
    draftClaims = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_evidence_claims_draft_v1.csv")
    reaudits = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_evidence_reaudit_v1.csv")
    claimCandidates = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_evidence_claim_candidates_v1.csv")
    userAdjudications = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_user_adjudication_v1.csv")
    rubrics = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_load_band_rubric_v1.csv")
    targetReviews = Get-SemanticCsvHash (Join-Path $AssetDirectory "tissue_rubric_target_exercise_review_v1.csv")
}
$inputHash = Get-CombinedHash $hashes
$auditPath = Join-Path $AssetDirectory "tissue_metadata_audit_manifest_v1.csv"
$existingAudits = @(Import-Csv -LiteralPath $auditPath | Where-Object { $_.auditBatchId -ne $batch })
$baselineAudit = $existingAudits | Where-Object { $_.auditBatchId -eq "TISSUE_RUBRIC_B1_LOWER_KNEE_ANKLE" } | Select-Object -Last 1
if ($null -eq $baselineAudit) { throw "Phase B1 audit baseline is missing." }
$existingAuditHeaders = @($baselineAudit.PSObject.Properties.Name)
$phaseCHeaders = @("reviewMode","independenceStatus","completionStatus","reauditSnapshotHash","claimCandidateSnapshotHash","userAdjudicationSnapshotHash","targetExerciseReviewSnapshotHash","reauditRowCount","claimCandidateCount","userAdjudicationCount","retainedClaimCount","correctedClaimCount","blockedClaimCount","retainedRubricCount","correctedRubricCount","blockedRubricCount","formalFinalClaimCount","humanBatchApprovalCount","productionProfileCount")
$auditHeaders = @($existingAuditHeaders + $phaseCHeaders | Select-Object -Unique)
$auditRows = foreach ($sourceRow in $existingAudits) {
    $values = [ordered]@{}
    foreach ($header in $auditHeaders) { $values[$header] = if ($sourceRow.PSObject.Properties.Name -contains $header) { $sourceRow.$header } else { "" } }
    [pscustomobject]$values
}
$phaseCValues = [ordered]@{}
foreach ($header in $auditHeaders) { $phaseCValues[$header] = if ($baselineAudit.PSObject.Properties.Name -contains $header) { $baselineAudit.$header } else { "" } }
$phaseCValues.auditManifestId = "tissue_reaudit_c_$($inputHash.Substring(0, 12))"
$phaseCValues.auditScope = "EVIDENCE_BATCH"
$phaseCValues.auditBatchId = $batch
$phaseCValues.rubricSnapshotHash = $hashes.rubrics
$phaseCValues.evidenceRegistrySnapshotHash = $hashes.evidenceRegistry
$phaseCValues.sourceVerificationSnapshotHash = $hashes.sourceVerification
$phaseCValues.automatedValidationStatus = "PASS_WITH_WARNINGS"
$phaseCValues.catalogEvidenceStatus = "PASS_WITH_WARNINGS"
$phaseCValues.exerciseLoadEvidenceIntegrityStatus = "PASS_WITH_WARNINGS"
$phaseCValues.citationVerificationStatus = "PASS_WITH_WARNINGS"
$phaseCValues.blindReviewCoverageStatus = "NOT_APPLICABLE"
$phaseCValues.humanApprovalCoverageStatus = "NOT_APPLICABLE"
$phaseCValues.evidenceNotApprovedCount = "17"
$phaseCValues.anomalyFlagCount = "1"
$phaseCValues.failedInvariantCount = "0"
$phaseCValues.warningCount = "3"
$phaseCValues.generatedBy = "Codex"
$phaseCValues.generatedByType = "AI_AGENT"
$phaseCValues.generatedAt = $reviewedAt
$phaseCValues.inputSnapshotHash = $inputHash
$phaseCValues.auditDecision = "PRODUCTION_REVIEW_REQUIRED"
$phaseCValues.auditNotes = "Same-session technical evidence re-audit completed; independent review was not performed, explicit human batch approval remains pending, and production promotion is blocked."
$phaseCValues.sourceCount = "10"
$phaseCValues.verifiedSourceCount = "10"
$phaseCValues.draftClaimCount = "12"
$phaseCValues.draftRubricCount = "5"
$phaseCValues.blindReviewCount = "0"
$phaseCValues.finalClaimCount = "0"
$phaseCValues.humanApprovalCount = "0"
$phaseCValues.productionEligibleProfileCount = "0"
$phaseCValues.reviewMode = "SAME_SESSION_EVIDENCE_REAUDIT"
$phaseCValues.independenceStatus = "NOT_INDEPENDENT"
$phaseCValues.completionStatus = "EVIDENCE_REAUDIT_COMPLETE_PENDING_BATCH_APPROVAL"
$phaseCValues.reauditSnapshotHash = $hashes.reaudits
$phaseCValues.claimCandidateSnapshotHash = $hashes.claimCandidates
$phaseCValues.userAdjudicationSnapshotHash = $hashes.userAdjudications
$phaseCValues.targetExerciseReviewSnapshotHash = $hashes.targetReviews
$phaseCValues.reauditRowCount = "12"
$phaseCValues.claimCandidateCount = "12"
$phaseCValues.userAdjudicationCount = "2"
$phaseCValues.retainedClaimCount = "4"
$phaseCValues.correctedClaimCount = "8"
$phaseCValues.blockedClaimCount = "0"
$phaseCValues.retainedRubricCount = "2"
$phaseCValues.correctedRubricCount = "3"
$phaseCValues.blockedRubricCount = "0"
$phaseCValues.formalFinalClaimCount = "0"
$phaseCValues.humanBatchApprovalCount = "0"
$phaseCValues.productionProfileCount = "0"
Write-Table "tissue_metadata_audit_manifest_v1.csv" $auditHeaders @($auditRows + [pscustomobject]$phaseCValues)

Write-Output "REAUDIT_ROWS=$($reaudits.Count)"
Write-Output "CLAIM_CANDIDATES=$($candidates.Count)"
Write-Output "USER_ADJUDICATIONS=$($adjudications.Count)"
Write-Output "RUBRICS=$($rubrics.Count)"
Write-Output "AUDIT_ID=$($phaseCValues.auditManifestId)"
Write-Output "INPUT_HASH=$inputHash"
$hashes.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Output "$($_.Name.ToUpperInvariant())_SEMANTIC_SHA256=$($_.Value)" }
Write-Output "AUDIT_MANIFEST_FILE_SHA256=$((Get-FileHash -LiteralPath $auditPath -Algorithm SHA256).Hash.ToLowerInvariant())"

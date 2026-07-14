param(
    [string]$AssetDirectory = "app/src/main/assets/metadata/tissue_load_v1"
)

$ErrorActionPreference = "Stop"
$batch = "TISSUE_REAUDIT_C_LOWER_KNEE_ANKLE"
$reviewedAt = "2026-07-14T00:00:00Z"

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
Write-Output "REAUDIT_ROWS=$($reaudits.Count)"
Write-Output "CLAIM_CANDIDATES=$($candidates.Count)"

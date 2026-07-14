param(
    [string]$OutputDirectory = "app/src/main/assets/metadata/tissue_load_v1"
)

$ErrorActionPreference = "Stop"
$batch = "TISSUE_RUBRIC_B1_LOWER_KNEE_ANKLE"
$preparedAt = "2026-07-14T00:00:00Z"
$utf8 = [Text.UTF8Encoding]::new($false)

function Get-TextHash([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = $sha.ComputeHash($utf8.GetBytes($Text)) } finally { $sha.Dispose() }
    return (([BitConverter]::ToString($hash)) -replace '-', '').ToLowerInvariant()
}

function Join-Ordinal([string[]]$Values, [string]$Separator) {
    [Array]::Sort($Values, [StringComparer]::Ordinal)
    return $Values -join $Separator
}

function Get-SemanticCsvHash([string]$Path) {
    $rawHeader = (Get-Content -LiteralPath $Path -TotalCount 1).TrimStart([char]0xFEFF)
    $headers = $rawHeader.Split(',') | ForEach-Object { $_.Trim().Trim('"') }
    $rows = @(Import-Csv -LiteralPath $Path)
    $unit = [char]0x1F
    $record = [char]0x1E
    [string[]]$values = @($rows | ForEach-Object {
        $row = $_
        (($headers | ForEach-Object { [string]$row.$_ }) -join $unit)
    })
    return Get-TextHash (($headers -join $unit) + "`n" + (Join-Ordinal $values $record))
}

function Write-Table([string]$Name, [string[]]$Headers, [hashtable[]]$Rows) {
    $objects = foreach ($row in $Rows) {
        $ordered = [ordered]@{}
        foreach ($header in $Headers) { $ordered[$header] = if ($null -eq $row[$header]) { "" } else { $row[$header] } }
        [pscustomobject]$ordered
    }
    $path = Join-Path $OutputDirectory $Name
    if ($objects.Count -eq 0) { ($Headers -join ',') | Set-Content -LiteralPath $path -Encoding utf8 }
    else { $objects | Export-Csv -LiteralPath $path -NoTypeInformation -Encoding utf8 }
}

$sourceHeaders = @(
    "sourceId", "pmid", "doi", "title", "authors", "publicationYear", "journal", "studyType",
    "population", "sampleSize", "trainingStatus", "sexComposition", "healthStatus", "exactExercise",
    "exerciseProtocol", "externalLoadCondition", "repetitionCondition", "romCondition", "velocityCondition",
    "surfaceCondition", "footwearCondition", "anticipatedCondition", "fatigueCondition", "measurementMethod",
    "measuredOutcome", "reportedMetric", "reportedValue", "reportedLowerBound", "reportedUpperBound",
    "reportedUnit", "supportedTissueIds", "supportedLoadDimensions", "majorLimitations",
    "identifierVerificationStatus", "bibliographicMatchStatus", "publicationIntegrityStatus",
    "verificationCapabilityStatus", "verifiedAt", "verificationMethod", "sourceStatus", "sourceNotes"
)

function Source([hashtable]$Data) {
    $common = @{
        trainingStatus = "Not reported"; healthStatus = "Healthy"; footwearCondition = "Not fully reported"
        anticipatedCondition = "Anticipated"; fatigueCondition = "Non-fatigued"
        identifierVerificationStatus = "PMID_AND_DOI_VERIFIED"; bibliographicMatchStatus = "MATCHED"
        publicationIntegrityStatus = "STATUS_UNKNOWN"; verificationCapabilityStatus = "LIVE_SOURCE_VERIFICATION_AVAILABLE"
        verifiedAt = $preparedAt; verificationMethod = "NCBI_ESUMMARY_PARSED_AND_CROSSREF_PARSED_BOUNDED_RETRY"
        sourceStatus = "INCLUDED_DRAFT_EVIDENCE"
        sourceNotes = "Source identity verified; claims remain draft and non-production pending blind review and human approval."
    }
    foreach ($key in $Data.Keys) { $common[$key] = $Data[$key] }
    return $common
}

$sources = @(
    (Source @{
        sourceId="PREFLIGHT_32658037"; pmid="32658037"; doi="10.1249/MSS.0000000000002459"
        title="Exercise Progression to Incrementally Load the Achilles Tendon."
        authors="Baxter JR|Corrigan P|Hullfish TJ|O'Rourke P|Silbernagel KG"; publicationYear="2021"
        journal="Medicine and Science in Sports and Exercise"; studyType="Comparative laboratory study"
        population="Healthy young adults"; sampleSize="8"; sexComposition="Mixed"
        exactExercise="Seated heel raise|Bilateral heel raise|Single-leg heel raise|Single-leg forward hop|Single-leg lateral hop|Lunge"
        exerciseProtocol="Progressive heel-raise, hopping, and lunge tasks"; externalLoadCondition="Bodyweight or task-specific resistance"
        repetitionCondition="Analyzed repetitions per study protocol"; romCondition="Task-defined"; velocityCondition="Self-selected/task-defined"
        surfaceCondition="Laboratory force plates"; measurementMethod="3D motion analysis, ground reaction force, and constrained free-body Achilles model"
        measuredOutcome="Modeled Achilles tendon peak force, impulse, and loading rate"; reportedMetric="Peak force|Impulse|Loading rate|Composite loading index"
        reportedValue="Condition-specific"; reportedUnit="BW|BW*s|BW/s|index"; supportedTissueIds="ACHILLES_TENDON"
        supportedLoadDimensions="PEAK_TENSILE_LOAD|CYCLIC_TENSILE_LOAD|LOADING_RATE"
        majorLimitations="Small healthy sample; modeled tendon force; fixed moment arm; condition-specific ranking; no global band thresholds."
        sourceNotes="Immutable preflight source ID preserved. Previous committed DOI was a bibliographic-identity mismatch that failed closed."
    }),
    (Source @{
        sourceId="SRC_PMID_28145739"; pmid="28145739"; doi="10.4085/1062-6050-52.1.04"
        title="Achilles Tendon Loading During Heel-Raising and -Lowering Exercises."
        authors="Revak A|Diers K|Kernozek TW|Gheidi N|Olbrantz C"; publicationYear="2017"; journal="Journal of Athletic Training"
        studyType="Cross-sectional laboratory study"; population="Healthy men"; sampleSize="21"; sexComposition="Men"
        exactExercise="Seated heel raise|Bilateral standing heel raise|Bilateral raise unilateral lower|Unilateral heel raise"
        exerciseProtocol="Heel-raising and lowering variants"; externalLoadCondition="Bodyweight"; repetitionCondition="Task repetitions"
        romCondition="Task-defined ankle ROM"; velocityCondition="Controlled"; surfaceCondition="Laboratory"
        measurementMethod="Motion analysis and inverse dynamics with tendon stress/strain estimation"
        measuredOutcome="Estimated Achilles tendon force, stress, and strain"; reportedMetric="Relative condition comparison"
        reportedValue="Condition-specific"; reportedUnit="N|MPa|percent"; supportedTissueIds="ACHILLES_TENDON"
        supportedLoadDimensions="PEAK_TENSILE_LOAD|ECCENTRIC_LOAD"
        majorLimitations="Healthy male sample; estimated internal load; protocol differs from app prescriptions."
    }),
    (Source @{
        sourceId="SRC_PMID_37847102"; pmid="37847102"; doi="10.1249/MSS.0000000000003323"
        title="Patellar Tendon Load Progression during Rehabilitation Exercises: Implications for the Treatment of Patellar Tendon Injuries."
        authors="Scattone Silva R|Song KE|Hullfish TJ|Sprague A|Silbernagel KG|Baxter JR"; publicationYear="2024"
        journal="Medicine and Science in Sports and Exercise"; studyType="Cross-sectional laboratory modeling study"
        population="Healthy adults"; sampleSize="20"; sexComposition="Mixed"
        exactExercise="Thirty-five weightbearing rehabilitation exercises including squats, Bulgarian squat, lunge, and jumps"
        exerciseProtocol="Standardized rehabilitation exercise trials"; externalLoadCondition="Bodyweight exercise conditions"
        repetitionCondition="Analyzed exercise trials"; romCondition="Exercise-defined"; velocityCondition="Exercise-defined"
        surfaceCondition="Laboratory force plates"; measurementMethod="3D motion analysis, force plates, and musculoskeletal patellar tendon model"
        measuredOutcome="Modeled patellar tendon peak load, impulse, and loading rate"; reportedMetric="Peak force|Impulse|Loading rate|Composite loading index"
        reportedValue="Condition-specific"; reportedUnit="BW|BW*s|BW/s|index"; supportedTissueIds="PATELLAR_TENDON"
        supportedLoadDimensions="PEAK_TENSILE_LOAD|CYCLIC_TENSILE_LOAD|LOADING_RATE"
        majorLimitations="Healthy sample; modeled force; source rankings do not define universal app load bands."
    }),
    (Source @{
        sourceId="SRC_PMID_37272685"; pmid="37272685"; doi="10.1177/03635465231175160"
        title="Patellofemoral Joint Loading Progression Across 35 Weightbearing Rehabilitation Exercises and Activities of Daily Living."
        authors="Song K|Scattone Silva R|Hullfish TJ|Silbernagel KG|Baxter JR"; publicationYear="2023"
        journal="The American Journal of Sports Medicine"; studyType="Cross-sectional laboratory modeling study"
        population="Healthy adults"; sampleSize="20"; sexComposition="Mixed"
        exactExercise="Thirty-five weightbearing exercises including squats, Bulgarian squat, lunge, drop jump, and single-leg hop"
        exerciseProtocol="Standardized rehabilitation exercise trials"; externalLoadCondition="Bodyweight exercise conditions"
        repetitionCondition="Analyzed exercise trials"; romCondition="Exercise-defined"; velocityCondition="Exercise-defined"
        surfaceCondition="Laboratory force plates"; measurementMethod="3D motion analysis, force plates, and patellofemoral joint model"
        measuredOutcome="Modeled patellofemoral peak force, impulse, loading rate, and composite loading index"
        reportedMetric="Peak force|Impulse|Loading rate|Source-defined loading index"
        reportedValue="Condition-specific"; reportedUnit="BW|BW*s|BW/s|index"; supportedTissueIds="KNEE_PATELLOFEMORAL"
        supportedLoadDimensions="COMPRESSION|IMPACT_IMPULSE"
        majorLimitations="Healthy sample; model-derived contact force; source-defined index is valid only within the study conditions."
    }),
    (Source @{
        sourceId="SRC_PMID_11949662"; pmid="11949662"; doi="10.2519/jospt.2002.32.4.141"
        title="Patellofemoral joint kinetics while squatting with and without an external load."
        authors="Wallace DA|Salem GJ|Salinas R|Powers CM"; publicationYear="2002"; journal="Journal of Orthopaedic and Sports Physical Therapy"
        studyType="Repeated-measures laboratory study"; population="Healthy adults"; sampleSize="10"; sexComposition="Mixed"
        exactExercise="Squat with and without external load"; exerciseProtocol="Squat through increasing knee flexion"
        externalLoadCondition="No load and 35 percent bodyweight external load"; repetitionCondition="Squat trials"
        romCondition="Knee flexion range"; velocityCondition="Controlled"; surfaceCondition="Laboratory force platform"
        measurementMethod="Motion analysis, force platform, and patellofemoral joint model"
        measuredOutcome="Estimated patellofemoral reaction force and stress"; reportedMetric="Force and stress by knee angle/load"
        reportedValue="Condition-specific"; reportedUnit="BW|MPa"; supportedTissueIds="KNEE_PATELLOFEMORAL"
        supportedLoadDimensions="COMPRESSION"; majorLimitations="Different model and metric from source-defined loading index; loaded protocol does not exactly match canonical squat."
        sourceStatus="REVIEWED_NOT_USED"
    }),
    (Source @{
        sourceId="SRC_PMID_18632195"; pmid="18632195"; doi="10.1016/j.clinbiomech.2008.05.002"
        title="Patellofemoral compressive force and stress during the forward and side lunges with and without a stride."
        authors="Escamilla RF|Zheng N|MacLeod TD|Edwards WB|Hreljac A|Fleisig GS|Wilk KE|Moorman CT 3rd|Imamura R"; publicationYear="2008"
        journal="Clinical Biomechanics"; studyType="Repeated-measures laboratory study"; population="Healthy adults"; sampleSize="18"; sexComposition="Mixed"
        exactExercise="Forward and side lunge with and without stride"; exerciseProtocol="Lunge variants"
        externalLoadCondition="Bodyweight"; repetitionCondition="Exercise trials"; romCondition="Task-defined"; velocityCondition="Controlled"
        surfaceCondition="Laboratory force platform"; measurementMethod="Motion analysis, force platform, and sagittal-plane patellofemoral model"
        measuredOutcome="Estimated patellofemoral compressive force and stress"; reportedMetric="Peak force and stress"
        reportedValue="Condition-specific"; reportedUnit="N|MPa"; supportedTissueIds="KNEE_PATELLOFEMORAL"
        supportedLoadDimensions="COMPRESSION"; majorLimitations="Model and normalization differ from the selected loading-index rubric source."
        sourceStatus="REVIEWED_NOT_USED"
    }),
    (Source @{
        sourceId="SRC_PMID_8947402"; pmid="8947402"; doi="10.1177/036354659602400615"
        title="Comparison of intersegmental tibiofemoral joint forces and muscle activity during various closed kinetic chain exercises."
        authors="Stuart MJ|Meglan DA|Lutz GE|Growney ES|An KN"; publicationYear="1996"; journal="The American Journal of Sports Medicine"
        studyType="Laboratory inverse-dynamics study"; population="Healthy adults"; sampleSize="10"; sexComposition="Mixed"
        exactExercise="Power squat|Front squat|Lunge"; exerciseProtocol="Closed kinetic chain exercises"
        externalLoadCondition="50 pound external load"; repetitionCondition="Exercise trials"; romCondition="Task-defined"
        velocityCondition="Controlled"; surfaceCondition="Laboratory"; measurementMethod="Motion analysis, force platform, inverse dynamics, and EMG"
        measuredOutcome="Intersegmental tibiofemoral shear and compressive resultant"; reportedMetric="Joint resultant force"
        reportedValue="Condition-specific"; reportedUnit="N|BW"; supportedTissueIds="KNEE_TIBIOFEMORAL"
        supportedLoadDimensions="ANTERIOR_POSTERIOR_SHEAR|COMPRESSION"
        majorLimitations="Intersegmental resultant is not internal compartment contact force; older small sample and fixed external load."
        sourceStatus="REVIEWED_NOT_USED"
    }),
    (Source @{
        sourceId="SRC_PMID_31593498"; pmid="31593498"; doi="10.1177/0363546519876074"
        title="In Vivo Anterior Cruciate Ligament Deformation During a Single-Legged Jump Measured by Magnetic Resonance Imaging and High-Speed Biplanar Radiography."
        authors="Englander ZA|Baldwin EL 3rd|Smith WAR|Garrett WE|Spritzer CE|DeFrate LE"; publicationYear="2019"
        journal="The American Journal of Sports Medicine"; studyType="In-vivo imaging study"; population="Healthy adults"; sampleSize="10"; sexComposition="Mixed"
        exactExercise="Single-legged jump"; exerciseProtocol="Jump with landing imaging"; externalLoadCondition="Bodyweight"
        repetitionCondition="Single-leg jump trials"; romCondition="Task-defined"; velocityCondition="Self-selected"
        surfaceCondition="Laboratory"; measurementMethod="MRI and high-speed biplanar radiography"
        measuredOutcome="ACL deformation/strain"; reportedMetric="Ligament strain"; reportedValue="Condition-specific"; reportedUnit="percent"
        supportedTissueIds="KNEE_ACL"; supportedLoadDimensions="IMPACT_STABILIZATION"
        majorLimitations="Measures strain rather than anterior translation; single task cannot calibrate valgus, rotation, or app-wide bands."
        sourceStatus="REVIEWED_NOT_USED"
    }),
    (Source @{
        sourceId="SRC_PMID_10656979"; pmid="10656979"; doi="10.1016/S0268-0033(99)00063-7"
        title="Cruciate ligament forces in the human knee during rehabilitation exercises."
        authors="Toutoungi DE|Lu TW|Leardini A|Catani F|O'Connor JJ"; publicationYear="2000"; journal="Clinical Biomechanics"
        studyType="Musculoskeletal modeling study"; population="Healthy adults"; sampleSize="Not reported in abstract"; sexComposition="Not reported"
        exactExercise="Squat and rehabilitation exercises"; exerciseProtocol="Modeled rehabilitation movements"
        externalLoadCondition="Task-defined"; repetitionCondition="Modeled trials"; romCondition="Exercise-defined"
        velocityCondition="Exercise-defined"; surfaceCondition="Laboratory"; measurementMethod="Kinematic/kinetic musculoskeletal model"
        measuredOutcome="Modeled ACL and PCL forces"; reportedMetric="Ligament force"; reportedValue="Condition-specific"; reportedUnit="BW"
        supportedTissueIds="KNEE_ACL|KNEE_PCL"; supportedLoadDimensions="ANTERIOR_TRANSLATION|POSTERIOR_TRANSLATION"
        majorLimitations="Force is not translation; model assumptions and rehabilitation protocols are not directly comparable to target dimensions."
        sourceStatus="REVIEWED_NOT_USED"
    }),
    (Source @{
        sourceId="SRC_PMID_30923576"; pmid="30923576"; doi="10.1186/s13047-019-0330-5"
        title="Function of ankle ligaments for subtalar and talocrural joint stability during an inversion movement - an in vitro study."
        authors="Li L|Gollhofer A|Lohrer H|Dorn-Lange N|Bonsignore G|Gehring D"; publicationYear="2019"; journal="Journal of Foot and Ankle Research"
        studyType="Cadaveric biomechanical study"; population="Cadaver specimens"; sampleSize="Nine specimens"; sexComposition="Not applicable"; healthStatus="Cadaveric"
        exactExercise="Machine-applied ankle inversion"; exerciseProtocol="Controlled inversion before and after ligament sectioning"
        externalLoadCondition="Mechanical inversion load"; repetitionCondition="In-vitro trials"; romCondition="Inversion range"
        velocityCondition="Controlled"; surfaceCondition="Test apparatus"; footwearCondition="None"; anticipatedCondition="Not applicable"; fatigueCondition="Not applicable"
        measurementMethod="In-vitro robotic/mechanical testing"; measuredOutcome="Talocrural/subtalar stability contribution"
        reportedMetric="Joint motion/stability after ligament sectioning"; reportedValue="Condition-specific"; reportedUnit="degrees|millimeters"
        supportedTissueIds="ANKLE_SUBTALAR|ANKLE_TALOCRURAL|ANKLE_LATERAL_LIGAMENT_COMPLEX"
        supportedLoadDimensions="STABILITY_DEMAND|INVERSION"
        majorLimitations="Cadaveric inversion test is not an exercise-specific in-vivo load measurement or validated app transfer."
        sourceStatus="REVIEWED_NOT_USED"
    })
)
Write-Table "tissue_load_evidence_registry_v1.csv" $sourceHeaders $sources

$claimHeaders = @(
    "draftClaimId", "sourceId", "stableKey", "tissueId", "loadDimension", "proposedBand", "claimType",
    "claimParaphrase", "claimDirection", "claimValue", "claimLowerBound", "claimUpperBound", "claimUnit",
    "comparatorExercise", "population", "exerciseCondition", "loadCondition", "romCondition",
    "velocityCondition", "surfaceCondition", "anticipatedCondition", "fatigueCondition", "evidenceLocatorType",
    "evidenceLocator", "evidenceAccessLevel", "preparedBy", "preparedByType", "preparedAt", "draftNotes"
)
function Claim([hashtable]$Data) {
    $common = @{
        claimType="CONDITION_BOUNDED_MECHANICAL_ESTIMATE"; claimDirection="REPORTED_CONDITION_VALUE"
        population="Healthy adults"; loadCondition="Bodyweight condition"; romCondition="Task-defined"
        velocityCondition="Study protocol"; surfaceCondition="Laboratory force plate"; anticipatedCondition="Anticipated"
        fatigueCondition="Non-fatigued"; evidenceLocatorType="TABLE"; evidenceAccessLevel="FULL_TEXT"
        preparedBy="Codex"; preparedByType="AI_AGENT"; preparedAt=$preparedAt
        draftNotes="Draft only; pending independent blind review and human approval."
    }
    foreach ($key in $Data.Keys) { $common[$key] = $Data[$key] }
    return $common
}
$claims = @(
    (Claim @{draftClaimId="DCLM_ACH_PEAK_SEATED_CALF";sourceId="PREFLIGHT_32658037";stableKey="ex_5c8751d2";tissueId="ACHILLES_TENDON";loadDimension="PEAK_TENSILE_LOAD";proposedBand="LOW";claimParaphrase="Under the tested seated heel-raise conditions, modeled peak Achilles tendon force was reported in the 0.5 to 0.7 BW range.";claimValue="0.6";claimLowerBound="0.5";claimUpperBound="0.7";claimUnit="BW";exerciseCondition="Tested seated heel raises";evidenceLocator="Results text and Figure 4 condition summary"}),
    (Claim @{draftClaimId="DCLM_ACH_PEAK_SINGLE_CALF";sourceId="PREFLIGHT_32658037";stableKey="ex_5ca7133f";tissueId="ACHILLES_TENDON";loadDimension="PEAK_TENSILE_LOAD";proposedBand="MODERATE";claimParaphrase="Under the tested single-leg heel-raise condition, modeled peak Achilles tendon force was 3.0 plus or minus 0.3 BW.";claimValue="3.0";claimLowerBound="2.7";claimUpperBound="3.3";claimUnit="BW";exerciseCondition="Tested single-leg heel raise";evidenceLocator="Results text and Figure 4"}),
    (Claim @{draftClaimId="DCLM_ACH_PEAK_SINGLE_HOP";sourceId="PREFLIGHT_32658037";stableKey="ex_314df428";tissueId="ACHILLES_TENDON";loadDimension="PEAK_TENSILE_LOAD";proposedBand="VERY_HIGH";claimParaphrase="The studied single-leg forward and lateral hopping tasks produced modeled peak Achilles tendon force above 7.3 BW; transfer to hop-and-stick is limited by landing protocol.";claimDirection="GREATER_THAN_REPORTED_VALUE";claimValue="7.3";claimUnit="BW";exerciseCondition="Tested repeated single-leg forward/lateral hopping; app target adds a stick landing";evidenceLocator="Results text and Figure 4"}),
    (Claim @{draftClaimId="DCLM_ACH_RATE_SINGLE_CALF";sourceId="PREFLIGHT_32658037";stableKey="ex_5ca7133f";tissueId="ACHILLES_TENDON";loadDimension="LOADING_RATE";claimParaphrase="Under the tested single-leg heel-raise condition, modeled Achilles tendon loading rate was 13.1 plus or minus 3.4 BW per second.";claimValue="13.1";claimLowerBound="9.7";claimUpperBound="16.5";claimUnit="BW/s";exerciseCondition="Tested single-leg heel raise";evidenceLocator="Results text and Figure 4"}),
    (Claim @{draftClaimId="DCLM_PAT_PEAK_FULL_SQUAT";sourceId="SRC_PMID_37847102";stableKey="ex_cb3c4dc2";tissueId="PATELLAR_TENDON";loadDimension="PEAK_TENSILE_LOAD";proposedBand="MODERATE";claimParaphrase="Under the tested bodyweight full double-leg squat condition, modeled peak patellar tendon load was 3.4 plus or minus 0.7 BW.";claimValue="3.4";claimLowerBound="2.7";claimUpperBound="4.1";claimUnit="BW";exerciseCondition="Full double-leg squat";evidenceLocator="Table 1"}),
    (Claim @{draftClaimId="DCLM_PAT_PEAK_BULGARIAN";sourceId="SRC_PMID_37847102";stableKey="ex_bb728af2";tissueId="PATELLAR_TENDON";loadDimension="PEAK_TENSILE_LOAD";proposedBand="MODERATE";claimParaphrase="Under the tested bodyweight Bulgarian squat condition, modeled peak patellar tendon load was 3.0 plus or minus 0.5 BW.";claimValue="3.0";claimLowerBound="2.5";claimUpperBound="3.5";claimUnit="BW";exerciseCondition="Bulgarian squat";evidenceLocator="Table 1"}),
    (Claim @{draftClaimId="DCLM_PFJ_COMP_60_SQUAT";sourceId="SRC_PMID_37272685";stableKey="ex_cb3c4dc2";tissueId="KNEE_PATELLOFEMORAL";loadDimension="COMPRESSION";proposedBand="LOW";claimParaphrase="Under the tested 60-degree double-leg squat condition, modeled peak patellofemoral joint force was 2.5 plus or minus 0.9 BW and the source classified the composite loading index in tier 1.";claimValue="2.5";claimLowerBound="1.6";claimUpperBound="3.4";claimUnit="BW";exerciseCondition="60-degree double-leg squat; partial-ROM transfer to canonical bodyweight squat";evidenceLocator="Table 1 and source loading-tier definition"}),
    (Claim @{draftClaimId="DCLM_PFJ_COMP_FULL_SQUAT";sourceId="SRC_PMID_37272685";stableKey="ex_cb3c4dc2";tissueId="KNEE_PATELLOFEMORAL";loadDimension="COMPRESSION";proposedBand="MODERATE";claimParaphrase="Under the tested full double-leg squat condition, modeled peak patellofemoral joint force was 4.5 plus or minus 0.8 BW and the source classified the composite loading index in tier 2.";claimValue="4.5";claimLowerBound="3.7";claimUpperBound="5.3";claimUnit="BW";exerciseCondition="Full double-leg squat";evidenceLocator="Table 1 and source loading-tier definition"}),
    (Claim @{draftClaimId="DCLM_PFJ_COMP_BULGARIAN";sourceId="SRC_PMID_37272685";stableKey="ex_bb728af2";tissueId="KNEE_PATELLOFEMORAL";loadDimension="COMPRESSION";proposedBand="MODERATE";claimParaphrase="Under the tested Bulgarian squat condition, modeled peak patellofemoral joint force was 4.7 plus or minus 0.7 BW and the source loading index was 0.460.";claimValue="4.7";claimLowerBound="4.0";claimUpperBound="5.4";claimUnit="BW";exerciseCondition="Bulgarian squat";evidenceLocator="Table 1 and Results"}),
    (Claim @{draftClaimId="DCLM_PFJ_COMP_LUNGE";sourceId="SRC_PMID_37272685";stableKey="ex_64644b5e";tissueId="KNEE_PATELLOFEMORAL";loadDimension="COMPRESSION";proposedBand="MODERATE";claimParaphrase="Under the tested lunge condition, modeled peak patellofemoral joint force was 5.1 plus or minus 0.8 BW and the source classified the composite loading index in tier 2.";claimValue="5.1";claimLowerBound="4.3";claimUpperBound="5.9";claimUnit="BW";exerciseCondition="Study-defined lunge";evidenceLocator="Table 1 and source loading-tier definition"}),
    (Claim @{draftClaimId="DCLM_PFJ_COMP_DROP_JUMP";sourceId="SRC_PMID_37272685";stableKey="ex_d6726746";tissueId="KNEE_PATELLOFEMORAL";loadDimension="COMPRESSION";proposedBand="MODERATE";claimParaphrase="Under the tested double-leg drop vertical jump condition, modeled peak patellofemoral joint force was 6.8 plus or minus 1.4 BW and the source classified the composite loading index in tier 2.";claimValue="6.8";claimLowerBound="5.4";claimUpperBound="8.2";claimUnit="BW";exerciseCondition="Double-leg drop vertical jump";evidenceLocator="Table 1 and source loading-tier definition"}),
    (Claim @{draftClaimId="DCLM_PFJ_COMP_SINGLE_HOP";sourceId="SRC_PMID_37272685";stableKey="ex_314df428";tissueId="KNEE_PATELLOFEMORAL";loadDimension="COMPRESSION";proposedBand="MODERATE";claimParaphrase="Under the tested single-leg maximum forward hop condition, modeled peak patellofemoral joint force was 6.3 plus or minus 1.2 BW; transfer to hop-and-stick is limited by the app landing protocol.";claimValue="6.3";claimLowerBound="5.1";claimUpperBound="7.5";claimUnit="BW";exerciseCondition="Single-leg maximum forward hop; app target adds a stick landing";evidenceLocator="Table 1"})
)
Write-Table "tissue_evidence_claims_draft_v1.csv" $claimHeaders $claims

$rubricHeaders = @(
    "rubricId", "tissueId", "loadDimension", "loadBand", "metricType", "metricLowerBound", "lowerBoundInclusive", "metricUpperBound",
    "upperBoundInclusive", "boundarySemanticsVersion", "metricUnit", "anchorStableKeys", "anchorConditions", "anchorClaimIds", "researchDecisionId", "draftClaimIds",
    "assignmentMethod", "evidenceSetId", "evidenceClaimIds", "sourceRefs", "confidenceLevel", "rubricStatus",
    "preparedBy", "preparedByType", "preparedAt", "blindReviewedBy", "blindReviewedByType", "blindReviewedAt",
    "humanApprovedBy", "humanApprovedAt", "rubricNotes"
)
function Rubric([hashtable]$Data) {
    $common = @{
        anchorClaimIds="";evidenceSetId=$batch;evidenceClaimIds="";confidenceLevel="LOW";boundarySemanticsVersion="DECIMAL_INTERVAL_V1"
        rubricStatus="DRAFT_RESEARCHED_PENDING_BLIND_REVIEW";preparedBy="Codex";preparedByType="AI_AGENT";preparedAt=$preparedAt
        blindReviewedBy="";blindReviewedByType="";blindReviewedAt="";humanApprovedBy="";humanApprovedAt=""
        rubricNotes="Partial condition-bounded draft only; missing bands are intentional pending independent review."
    }
    foreach ($key in $Data.Keys) { $common[$key] = $Data[$key] }
    return $common
}
$rubrics = @(
    (Rubric @{rubricId="RUBRIC_ACH_PEAK_LOW";tissueId="ACHILLES_TENDON";loadDimension="PEAK_TENSILE_LOAD";loadBand="LOW";metricType="WITHIN_STUDY_MODELED_PEAK_FORCE_ORDER";metricUnit="BW";anchorStableKeys="ex_5c8751d2";anchorConditions="Tested seated heel raise; app stableKey ex_5c8751d2; healthy young adults; study-defined bodyweight/resistance condition; seated ankle ROM and study cadence; source-protocol laterality; laboratory force plates; anticipated non-fatigued execution; constrained free-body Achilles model; peak force normalized to bodyweight; no transfer beyond the tested condition.";researchDecisionId="RDEC_ACH_PEAK";draftClaimIds="DCLM_ACH_PEAK_SEATED_CALF";assignmentMethod="WITHIN_STUDY_RELATIVE_ORDER";sourceRefs="PREFLIGHT_32658037|SRC_PMID_28145739"}),
    (Rubric @{rubricId="RUBRIC_ACH_PEAK_MODERATE";tissueId="ACHILLES_TENDON";loadDimension="PEAK_TENSILE_LOAD";loadBand="MODERATE";metricType="WITHIN_STUDY_MODELED_PEAK_FORCE_ORDER";metricUnit="BW";anchorStableKeys="ex_5ca7133f";anchorConditions="Tested unilateral single-leg heel raise; app stableKey ex_5ca7133f; healthy adults; bodyweight condition; task-defined ankle ROM; controlled/study cadence; unilateral; laboratory surface; anticipated non-fatigued execution; modeled Achilles peak force normalized to bodyweight; app loading and cadence remain transfer limitations.";researchDecisionId="RDEC_ACH_PEAK";draftClaimIds="DCLM_ACH_PEAK_SINGLE_CALF";assignmentMethod="WITHIN_STUDY_RELATIVE_ORDER";sourceRefs="PREFLIGHT_32658037|SRC_PMID_28145739"}),
    (Rubric @{rubricId="RUBRIC_ACH_PEAK_VERY_HIGH";tissueId="ACHILLES_TENDON";loadDimension="PEAK_TENSILE_LOAD";loadBand="VERY_HIGH";metricType="WITHIN_STUDY_MODELED_PEAK_FORCE_ORDER";metricUnit="BW";anchorStableKeys="ex_314df428";anchorConditions="Tested repeated unilateral forward/lateral hopping; app stableKey ex_314df428 is a close hop-and-stick variant; healthy young adults; bodyweight; task-defined ROM and hopping speed; unilateral; laboratory force plates; anticipated non-fatigued execution; constrained free-body Achilles model; peak force normalized to bodyweight; stick landing, cadence, and direction limit transfer.";researchDecisionId="RDEC_ACH_PEAK";draftClaimIds="DCLM_ACH_PEAK_SINGLE_HOP";assignmentMethod="CLOSE_VARIANT_TRANSFER";sourceRefs="PREFLIGHT_32658037";confidenceLevel="VERY_LOW"}),
    (Rubric @{rubricId="RUBRIC_PFJ_COMP_LOW";tissueId="KNEE_PATELLOFEMORAL";loadDimension="COMPRESSION";loadBand="LOW";metricType="SOURCE_DEFINED_PFJ_LOADING_INDEX";metricUpperBound="0.333";upperBoundInclusive="true";metricUnit="INDEX";anchorStableKeys="ex_cb3c4dc2";anchorConditions="Tested 60-degree bilateral bodyweight squat; app stableKey ex_cb3c4dc2 is a partial-ROM transfer; healthy adults; bodyweight; 60-degree knee ROM; study-defined speed; bilateral; laboratory force plates; anticipated non-fatigued execution; patellofemoral contact model; source loading index combines normalized peak force and impulse; full-ROM use is not implied.";researchDecisionId="RDEC_PFJ_COMP";draftClaimIds="DCLM_PFJ_COMP_60_SQUAT";assignmentMethod="CLOSE_VARIANT_TRANSFER";sourceRefs="SRC_PMID_37272685";confidenceLevel="VERY_LOW"}),
    (Rubric @{rubricId="RUBRIC_PFJ_COMP_MODERATE";tissueId="KNEE_PATELLOFEMORAL";loadDimension="COMPRESSION";loadBand="MODERATE";metricType="SOURCE_DEFINED_PFJ_LOADING_INDEX";metricLowerBound="0.333";lowerBoundInclusive="false";metricUpperBound="0.667";upperBoundInclusive="true";metricUnit="INDEX";anchorStableKeys="ex_cb3c4dc2|ex_bb728af2|ex_64644b5e|ex_d6726746|ex_314df428";anchorConditions="Tested full bilateral squat, unilateral Bulgarian squat, study-defined lunge, bilateral drop vertical jump, and unilateral maximum forward hop; mapped app stableKeys listed; healthy adults; bodyweight; exercise-defined ROM and velocity; stated bilateral/unilateral conditions; laboratory force plates; anticipated non-fatigued execution; patellofemoral contact model; source loading index combines normalized peak force and impulse; box height, stride, hop-and-stick, and app prescription details limit transfer.";researchDecisionId="RDEC_PFJ_COMP";draftClaimIds="DCLM_PFJ_COMP_FULL_SQUAT|DCLM_PFJ_COMP_BULGARIAN|DCLM_PFJ_COMP_LUNGE|DCLM_PFJ_COMP_DROP_JUMP|DCLM_PFJ_COMP_SINGLE_HOP";assignmentMethod="WITHIN_STUDY_RELATIVE_ORDER";sourceRefs="SRC_PMID_37272685"})
)
Write-Table "tissue_load_band_rubric_v1.csv" $rubricHeaders $rubrics

$decisionHeaders = @(
    "researchDecisionId", "reviewBatchId", "tissueId", "loadDimension", "targetStableKeys", "database",
    "searchQuery", "searchDate", "candidateSourceIds", "includedSourceIds", "excludedSourceIds", "exclusionReasons",
    "populationScope", "exerciseConditionScope", "measurementScope", "evidenceSufficiency", "researchDecision",
    "decisionReason", "preparedBy", "preparedByType", "preparedAt", "researchNotes"
)
function Decision([string]$Id, [string]$Tissue, [string]$Dimension, [string]$Keys, [string]$Candidates,
    [string]$Included, [string]$Excluded, [string]$ExclusionReasons, [string]$Sufficiency,
    [string]$Result, [string]$Reason) {
    return @{
        researchDecisionId=$Id;reviewBatchId=$batch;tissueId=$Tissue;loadDimension=$Dimension;targetStableKeys=$Keys
        database="PubMed|Crossref|PubMed Central";searchQuery="($Tissue) AND ($Dimension) AND (squat OR lunge OR heel raise OR jump OR hop) AND (force OR strain OR contact OR load)"
        searchDate="2026-07-14";candidateSourceIds=$Candidates;includedSourceIds=$Included;excludedSourceIds=$Excluded;exclusionReasons=$ExclusionReasons
        populationScope="Human healthy exercise evidence preferred; patient/cadaver evidence requires explicit transfer justification"
        exerciseConditionScope="Target canonical exercises and close protocol variants only"
        measurementScope="Internal tissue force, strain, contact model, or explicit mechanical proxy"
        evidenceSufficiency=$Sufficiency;researchDecision=$Result;decisionReason=$Reason
        preparedBy="Codex";preparedByType="AI_AGENT";preparedAt=$preparedAt
        researchNotes="No production profile, final claim, blind review, or human approval is implied."
    }
}
$decisions = @(
    Decision "RDEC_ACH_PEAK" "ACHILLES_TENDON" "PEAK_TENSILE_LOAD" "ex_5c8751d2|ex_5ca7133f|ex_bd072cd|ex_314df428|ex_e465d1e9|ex_a3ddd8ac|ex_64644b5e" "PREFLIGHT_32658037|SRC_PMID_28145739" "PREFLIGHT_32658037|SRC_PMID_28145739" "" "" "MODELED_CONDITION_BOUNDED_COMPARABLE" "DRAFT_RUBRIC_CREATED" "Within-study modeled peak-force ordering supports a partial draft rubric for matched heel-raise and hopping conditions."
    Decision "RDEC_ACH_CYCLIC" "ACHILLES_TENDON" "CYCLIC_TENSILE_LOAD" "ex_5c8751d2|ex_5ca7133f|ex_bd072cd" "PREFLIGHT_32658037|SRC_PMID_28145739" "" "PREFLIGHT_32658037|SRC_PMID_28145739" "WRONG_LOAD_DIMENSION" "PROXY_NOT_COMPARABLE" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "Impulse and repeated-task protocols do not define a comparable cyclic tensile exposure band."
    Decision "RDEC_ACH_ENERGY" "ACHILLES_TENDON" "ENERGY_STORAGE_RELEASE" "ex_e465d1e9|ex_a3ddd8ac|ex_314df428" "PREFLIGHT_32658037" "" "PREFLIGHT_32658037" "NO_MECHANICAL_OUTCOME" "OUTCOME_NOT_MEASURED" "BLOCKED_INSUFFICIENT_EVIDENCE" "The source modeled force and rate but did not directly quantify tendon energy storage and release."
    Decision "RDEC_ACH_RATE" "ACHILLES_TENDON" "LOADING_RATE" "ex_5c8751d2|ex_5ca7133f|ex_e465d1e9|ex_a3ddd8ac|ex_314df428" "PREFLIGHT_32658037" "PREFLIGHT_32658037" "" "" "MODELED_VALUES_WITHOUT_GLOBAL_BOUNDARIES" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "Condition-specific rates were found, but no defensible app-wide rate band boundaries were established."
    Decision "RDEC_PAT_PEAK" "PATELLAR_TENDON" "PEAK_TENSILE_LOAD" "ex_cb3c4dc2|ex_bb728af2|ex_64644b5e|ex_d6726746|ex_a3ddd8ac" "SRC_PMID_37847102" "SRC_PMID_37847102" "" "" "MODELED_VALUES_WITHOUT_GLOBAL_BOUNDARIES" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "Comparable condition values support draft claims, but not defensible universal band boundaries."
    Decision "RDEC_PAT_CYCLIC" "PATELLAR_TENDON" "CYCLIC_TENSILE_LOAD" "ex_cb3c4dc2|ex_bb728af2|ex_64644b5e" "SRC_PMID_37847102" "" "SRC_PMID_37847102" "WRONG_LOAD_DIMENSION" "PROXY_NOT_COMPARABLE" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "Impulse is not a validated cyclic exposure band."
    Decision "RDEC_PAT_ECC" "PATELLAR_TENDON" "ECCENTRIC_LOAD" "ex_d6726746|ex_a3ddd8ac|ex_314df428" "SRC_PMID_37847102" "" "SRC_PMID_37847102" "NO_MECHANICAL_OUTCOME" "OUTCOME_NOT_SEPARATED" "BLOCKED_INSUFFICIENT_EVIDENCE" "The modeled result did not isolate eccentric tendon load."
    Decision "RDEC_PAT_ENERGY" "PATELLAR_TENDON" "ENERGY_STORAGE_RELEASE" "ex_d6726746|ex_a3ddd8ac|ex_314df428" "SRC_PMID_37847102" "" "SRC_PMID_37847102" "NO_MECHANICAL_OUTCOME" "OUTCOME_NOT_MEASURED" "BLOCKED_INSUFFICIENT_EVIDENCE" "Energy storage and release was not directly quantified."
    Decision "RDEC_PAT_RATE" "PATELLAR_TENDON" "LOADING_RATE" "ex_cb3c4dc2|ex_bb728af2|ex_a3ddd8ac|ex_d6726746" "SRC_PMID_37847102" "SRC_PMID_37847102" "" "" "MODELED_VALUES_WITHOUT_GLOBAL_BOUNDARIES" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "Condition-specific rates were reported without transferable app-wide boundaries."
    Decision "RDEC_PFJ_COMP" "KNEE_PATELLOFEMORAL" "COMPRESSION" "ex_cb3c4dc2|ex_bb728af2|ex_64644b5e|ex_d6726746|ex_314df428" "SRC_PMID_37272685|SRC_PMID_11949662|SRC_PMID_18632195" "SRC_PMID_37272685" "SRC_PMID_11949662|SRC_PMID_18632195" "UNVALIDATED_MODEL" "SOURCE_DEFINED_COMPARABLE_INDEX" "DRAFT_RUBRIC_CREATED" "One internally consistent 35-exercise model supplies source-defined loading tiers; other models are retained as non-pooled context."
    Decision "RDEC_TF_COMP" "KNEE_TIBIOFEMORAL" "COMPRESSION" "barbell_back_squat|ex_c5043892|ex_64644b5e" "SRC_PMID_8947402" "" "SRC_PMID_8947402" "WRONG_LOAD_DIMENSION" "INTERSEGMENTAL_RESULTANT_NOT_CONTACT_FORCE" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "Intersegmental resultant compression is not internal tibiofemoral contact force."
    Decision "RDEC_TF_SHEAR" "KNEE_TIBIOFEMORAL" "ANTERIOR_POSTERIOR_SHEAR" "barbell_back_squat|ex_c5043892|ex_64644b5e" "SRC_PMID_8947402" "SRC_PMID_8947402" "" "" "KINETIC_PROXY_WITH_PROTOCOL_LIMITS" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "Inverse-dynamics shear is protocol-specific and insufficient for app-wide band thresholds."
    Decision "RDEC_ACL_TRANSLATION" "KNEE_ACL" "ANTERIOR_TRANSLATION" "ex_314df428|ex_d6726746" "SRC_PMID_31593498|SRC_PMID_10656979" "" "SRC_PMID_31593498|SRC_PMID_10656979" "WRONG_LOAD_DIMENSION" "STRAIN_OR_FORCE_NOT_TRANSLATION" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "The sources measured ACL strain or modeled force, not anterior translation."
    Decision "RDEC_PCL_TRANSLATION" "KNEE_PCL" "POSTERIOR_TRANSLATION" "barbell_back_squat|ex_c5043892|ex_64644b5e" "SRC_PMID_10656979" "" "SRC_PMID_10656979" "WRONG_LOAD_DIMENSION" "FORCE_NOT_TRANSLATION" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "Modeled PCL force cannot be relabeled as posterior translation."
    Decision "RDEC_QT_PEAK" "QUADRICEPS_TENDON" "PEAK_TENSILE_LOAD" "ex_cb3c4dc2|ex_b78a8f95|ex_d60745b4" "SRC_PMID_37847102" "" "SRC_PMID_37847102" "WRONG_TISSUE" "NO_TARGET_TISSUE_OUTCOME" "BLOCKED_INSUFFICIENT_EVIDENCE" "Patellar tendon load cannot be transferred to quadriceps tendon without a validated relation."
    Decision "RDEC_QT_CYCLIC" "QUADRICEPS_TENDON" "CYCLIC_TENSILE_LOAD" "ex_cb3c4dc2|ex_b78a8f95|ex_d60745b4" "SRC_PMID_37847102" "" "SRC_PMID_37847102" "WRONG_TISSUE" "NO_TARGET_TISSUE_OUTCOME" "BLOCKED_INSUFFICIENT_EVIDENCE" "No comparable quadriceps tendon cyclic-load source was found."
    Decision "RDEC_QT_ECC" "QUADRICEPS_TENDON" "ECCENTRIC_LOAD" "ex_b78a8f95|ex_d60745b4|ex_d6726746" "SRC_PMID_37847102" "" "SRC_PMID_37847102" "WRONG_TISSUE" "NO_TARGET_TISSUE_OUTCOME" "BLOCKED_INSUFFICIENT_EVIDENCE" "No comparable quadriceps tendon eccentric-load source was found."
    Decision "RDEC_TALO_COMP" "ANKLE_TALOCRURAL" "COMPRESSION" "ex_e465d1e9|ex_a3ddd8ac|ex_314df428|ex_d6726746" "SRC_PMID_30923576" "" "SRC_PMID_30923576" "NO_MECHANICAL_OUTCOME" "NO_IN_VIVO_EXERCISE_CONTACT_OUTCOME" "BLOCKED_INSUFFICIENT_EVIDENCE" "Cadaveric stability testing did not measure exercise-specific talocrural compression."
    Decision "RDEC_TALO_IMPACT" "ANKLE_TALOCRURAL" "IMPACT_IMPULSE" "ex_e465d1e9|ex_a3ddd8ac|ex_314df428|ex_d6726746" "SRC_PMID_30923576" "" "SRC_PMID_30923576" "NO_MECHANICAL_OUTCOME" "NO_INTERNAL_IMPULSE_OUTCOME" "BLOCKED_INSUFFICIENT_EVIDENCE" "No validated internal talocrural impact-impulse mapping was found."
    Decision "RDEC_TALO_END" "ANKLE_TALOCRURAL" "END_RANGE_STRESS" "ex_5ca7133f|ex_e465d1e9|ex_d6726746" "SRC_PMID_30923576" "" "SRC_PMID_30923576" "WRONG_EXERCISE" "CADAVERIC_INVERSION_NOT_EXERCISE_END_RANGE" "BLOCKED_INSUFFICIENT_EVIDENCE" "The in-vitro inversion protocol is not a transferable exercise end-range stress anchor."
    Decision "RDEC_SUB_ROT" "ANKLE_SUBTALAR" "ROTATIONAL_SHEAR" "ex_e465d1e9|ex_a3ddd8ac|ex_314df428|ex_d6726746" "SRC_PMID_30923576" "" "SRC_PMID_30923576" "WRONG_LOAD_DIMENSION" "STABILITY_NOT_ROTATIONAL_SHEAR" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "Sectioning-based stability outcomes do not quantify rotational shear."
    Decision "RDEC_SUB_STAB" "ANKLE_SUBTALAR" "STABILITY_DEMAND" "ex_e465d1e9|ex_a3ddd8ac|ex_314df428|ex_d6726746" "SRC_PMID_30923576" "" "SRC_PMID_30923576" "PATIENT_ONLY_WITHOUT_TRANSFER_JUSTIFICATION" "CADAVERIC_TRANSFER_UNVALIDATED" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "Cadaveric ligament contribution is relevant context but not an exercise stability-demand band."
    Decision "RDEC_SUB_IMPACT" "ANKLE_SUBTALAR" "IMPACT_IMPULSE" "ex_e465d1e9|ex_a3ddd8ac|ex_314df428|ex_d6726746" "SRC_PMID_30923576" "" "SRC_PMID_30923576" "NO_MECHANICAL_OUTCOME" "NO_INTERNAL_IMPULSE_OUTCOME" "BLOCKED_INSUFFICIENT_EVIDENCE" "No subtalar impact impulse was measured."
    Decision "RDEC_ACL_VALGUS" "KNEE_ACL" "VALGUS" "ex_64644b5e|ex_314df428|ex_d6726746|ex_377448a9" "SRC_PMID_31593498" "" "SRC_PMID_31593498" "WRONG_LOAD_DIMENSION" "CATALOG_DIMENSION_OUT_OF_SCOPE" "OUT_OF_SCOPE_AFTER_AUDIT" "The current canonical ACL catalog does not support VALGUS as an ACL load dimension; no catalog expansion is allowed in Phase B1."
    Decision "RDEC_ACL_ROT" "KNEE_ACL" "INTERNAL_ROTATION" "ex_314df428|ex_d6726746|ex_377448a9" "SRC_PMID_31593498" "" "SRC_PMID_31593498" "WRONG_LOAD_DIMENSION" "STRAIN_NOT_ROTATIONAL_LOAD" "BLOCKED_INSUFFICIENT_EVIDENCE" "ACL strain during a jump does not calibrate internal rotation."
    Decision "RDEC_ACL_DECEL" "KNEE_ACL" "DECELERATION_STABILIZATION" "ex_314df428|ex_d6726746|ex_377448a9" "SRC_PMID_31593498" "" "SRC_PMID_31593498" "WRONG_LOAD_DIMENSION" "JUMP_STRAIN_NOT_DECELERATION_BAND" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "A single-leg jump strain result is not a validated deceleration-stabilization scale."
    Decision "RDEC_ACL_IMPACT" "KNEE_ACL" "IMPACT_STABILIZATION" "ex_314df428|ex_d6726746|ex_e465d1e9" "SRC_PMID_31593498" "" "SRC_PMID_31593498" "WRONG_LOAD_DIMENSION" "JUMP_STRAIN_NOT_IMPACT_STABILIZATION_BAND" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "In-vivo strain is relevant but insufficient to create an impact-stabilization band."
    Decision "RDEC_MCL_VALGUS" "KNEE_MCL" "VALGUS" "ex_64644b5e|ex_d6726746|ex_377448a9" "SRC_PMID_31593498" "" "SRC_PMID_31593498" "WRONG_TISSUE" "NO_MCL_MECHANICAL_OUTCOME" "BLOCKED_INSUFFICIENT_EVIDENCE" "No comparable MCL valgus mechanical outcome was found."
    Decision "RDEC_MCL_DECEL" "KNEE_MCL" "DECELERATION_STABILIZATION" "ex_314df428|ex_d6726746|ex_377448a9" "SRC_PMID_31593498" "" "SRC_PMID_31593498" "WRONG_TISSUE" "NO_MCL_MECHANICAL_OUTCOME" "BLOCKED_INSUFFICIENT_EVIDENCE" "No comparable MCL deceleration mechanical outcome was found."
    Decision "RDEC_LAT_INVERSION" "ANKLE_LATERAL_LIGAMENT_COMPLEX" "INVERSION" "ex_e465d1e9|ex_a3ddd8ac|ex_314df428|ex_d6726746" "SRC_PMID_30923576" "" "SRC_PMID_30923576" "PATIENT_ONLY_WITHOUT_TRANSFER_JUSTIFICATION" "CADAVERIC_TRANSFER_UNVALIDATED" "EVIDENCE_FOUND_BUT_NOT_COMPARABLE" "Cadaveric inversion stability cannot calibrate an exercise ligament-load band."
    Decision "RDEC_LAT_IMPACT" "ANKLE_LATERAL_LIGAMENT_COMPLEX" "IMPACT_STABILIZATION" "ex_e465d1e9|ex_a3ddd8ac|ex_314df428|ex_d6726746" "SRC_PMID_30923576" "" "SRC_PMID_30923576" "NO_MECHANICAL_OUTCOME" "NO_IMPACT_STABILIZATION_OUTCOME" "BLOCKED_INSUFFICIENT_EVIDENCE" "No exercise-specific lateral ligament impact-stabilization outcome was found."
)
Write-Table "tissue_rubric_research_log_v1.csv" $decisionHeaders $decisions

$reviewHeaders = @(
    "targetExerciseReviewId", "reviewBatchId", "stableKey", "canonicalDisplayName", "researchUseStatus",
    "supportedTissueDimensions", "researchDecisionIds", "sourceIds", "draftClaimIds", "draftRubricIds",
    "directProtocolMatch", "transferDistance", "nonUseReasons", "preparedBy", "preparedByType", "preparedAt", "reviewNotes"
)
$canonicalNames = @{}
$canonicalPath = Join-Path (Split-Path $OutputDirectory -Parent) "canonical_exercise_metadata_v0_3_5_0_pass3_1.csv"
Import-Csv -LiteralPath $canonicalPath -Encoding utf8 | ForEach-Object { $canonicalNames[$_.stableKey] = $_.exerciseName }
$directRubricsByStableKey = @{
    "ex_cb3c4dc2"="RUBRIC_PFJ_COMP_MODERATE"
    "ex_64644b5e"="RUBRIC_PFJ_COMP_MODERATE"
    "ex_bb728af2"="RUBRIC_PFJ_COMP_MODERATE"
    "ex_5c8751d2"="RUBRIC_ACH_PEAK_LOW"
    "ex_5ca7133f"="RUBRIC_ACH_PEAK_MODERATE"
    "ex_d6726746"="RUBRIC_PFJ_COMP_MODERATE"
}

function Review([string]$Id,[string]$Key,[string]$Status,[string]$Dimensions,[string]$DecisionIds,
    [string]$SourceIds,[string]$ClaimIds,[bool]$Direct,[string]$Transfer,[string]$Notes,[string]$Reasons) {
    $rubricId = $directRubricsByStableKey[$Key]
    $directAnchor = -not [string]::IsNullOrWhiteSpace($rubricId)
    $effectiveStatus = if($directAnchor){"USED_AS_DIRECT_ANCHOR"}else{$Status}
    $effectiveDirect = if($directAnchor){"TRUE"}else{$Direct.ToString().ToUpperInvariant()}
    $effectiveTransfer = if($directAnchor){""}else{$Transfer}
    $effectiveReasons = if($directAnchor){""}else{$Reasons}
    return @{targetExerciseReviewId=$Id;reviewBatchId=$batch;stableKey=$Key;canonicalDisplayName=$canonicalNames[$Key];researchUseStatus=$effectiveStatus
        supportedTissueDimensions=$Dimensions;researchDecisionIds=$DecisionIds;sourceIds=$SourceIds;draftClaimIds=$ClaimIds;draftRubricIds=$rubricId
        directProtocolMatch=$effectiveDirect;transferDistance=$effectiveTransfer;nonUseReasons=$effectiveReasons
        preparedBy="Codex";preparedByType="AI_AGENT";preparedAt=$preparedAt;reviewNotes=$Notes}
}
$reviews = @(
    Review "TREV_BACK_SQUAT" "barbell_back_squat" "USED_AS_TRANSFER_REFERENCE" "KNEE_PATELLOFEMORAL:COMPRESSION|KNEE_TIBIOFEMORAL:ANTERIOR_POSTERIOR_SHEAR" "RDEC_PFJ_COMP|RDEC_TF_SHEAR" "SRC_PMID_11949662|SRC_PMID_8947402" "" $false "Loaded squat protocols differ in load, model, and canonical naming." "Retained only as a bounded loaded-squat transfer reference." "TESTED_PROTOCOL_MISMATCH|LOAD_CONDITION_INCOMPATIBLE"
    Review "TREV_BW_SQUAT" "ex_cb3c4dc2" "USED_AS_TRANSFER_REFERENCE" "PATELLAR_TENDON:PEAK_TENSILE_LOAD|KNEE_PATELLOFEMORAL:COMPRESSION" "RDEC_PAT_PEAK|RDEC_PFJ_COMP" "SRC_PMID_37847102|SRC_PMID_37272685" "DCLM_PAT_PEAK_FULL_SQUAT|DCLM_PFJ_COMP_60_SQUAT|DCLM_PFJ_COMP_FULL_SQUAT" $true "Direct study exercise; rubric linkage is added only in the anchor commit." "Direct protocol evidence is present, but this intermediate evidence commit does not yet grant anchor status." "FULL_TEXT_INSUFFICIENT"
    Review "TREV_FRONT_SQUAT" "ex_c5043892" "USED_AS_TRANSFER_REFERENCE" "KNEE_TIBIOFEMORAL:ANTERIOR_POSTERIOR_SHEAR" "RDEC_TF_SHEAR" "SRC_PMID_8947402" "" $true "Study used a front squat with fixed 50-pound load; app prescription can differ." "No load band created from the intersegmental resultant." "LOAD_CONDITION_INCOMPATIBLE"
    Review "TREV_LEG_EXTENSION" "ex_b78a8f95" "NO_COMPARABLE_SOURCE_FOUND" "" "RDEC_QT_PEAK|RDEC_QT_CYCLIC|RDEC_QT_ECC" "" "" $false "" "The reviewed weightbearing sources did not test machine leg extension with a tissue-specific outcome." "NO_COMPARABLE_SOURCE"
    Review "TREV_SINGLE_LEG_EXTENSION" "ex_d60745b4" "NO_COMPARABLE_SOURCE_FOUND" "" "RDEC_QT_PEAK|RDEC_QT_CYCLIC|RDEC_QT_ECC" "" "" $false "" "No comparable single-leg machine-extension tissue mechanics source was found." "NO_COMPARABLE_SOURCE"
    Review "TREV_LUNGE" "ex_64644b5e" "USED_AS_TRANSFER_REFERENCE" "KNEE_PATELLOFEMORAL:COMPRESSION|KNEE_TIBIOFEMORAL:ANTERIOR_POSTERIOR_SHEAR" "RDEC_PFJ_COMP|RDEC_TF_SHEAR" "SRC_PMID_37272685|SRC_PMID_8947402" "DCLM_PFJ_COMP_LUNGE" $true "Study-defined lunge is close, but stride, load, and app execution details can differ." "Draft PFJ claim retained; incompatible tibiofemoral metric is not pooled." "TESTED_PROTOCOL_MISMATCH"
    Review "TREV_RFESS" "ex_bb728af2" "USED_AS_TRANSFER_REFERENCE" "PATELLAR_TENDON:PEAK_TENSILE_LOAD|KNEE_PATELLOFEMORAL:COMPRESSION" "RDEC_PAT_PEAK|RDEC_PFJ_COMP" "SRC_PMID_37847102|SRC_PMID_37272685" "DCLM_PAT_PEAK_BULGARIAN|DCLM_PFJ_COMP_BULGARIAN" $true "Source Bulgarian squat is the close canonical protocol; exact setup remains prescription-dependent." "Direct protocol evidence is present, but anchor status waits for rubric linkage." "TESTED_PROTOCOL_MISMATCH"
    Review "TREV_STANDING_CALF" "ex_bd072cd" "USED_AS_TRANSFER_REFERENCE" "ACHILLES_TENDON:PEAK_TENSILE_LOAD" "RDEC_ACH_PEAK" "PREFLIGHT_32658037|SRC_PMID_28145739" "" $false "Standing bilateral protocols are close, but app load and bilateral/unilateral execution are not fixed." "Used as a bounded heel-raise transfer only." "LOAD_CONDITION_INCOMPATIBLE|BILATERAL_UNILATERAL_MISMATCH"
    Review "TREV_SEATED_CALF" "ex_5c8751d2" "USED_AS_TRANSFER_REFERENCE" "ACHILLES_TENDON:PEAK_TENSILE_LOAD" "RDEC_ACH_PEAK" "PREFLIGHT_32658037|SRC_PMID_28145739" "DCLM_ACH_PEAK_SEATED_CALF" $true "Direct seated heel-raise condition; anchor status waits for rubric linkage." "Condition-bounded draft claim retained." "FULL_TEXT_INSUFFICIENT"
    Review "TREV_SINGLE_CALF" "ex_5ca7133f" "USED_AS_TRANSFER_REFERENCE" "ACHILLES_TENDON:PEAK_TENSILE_LOAD|ACHILLES_TENDON:LOADING_RATE" "RDEC_ACH_PEAK|RDEC_ACH_RATE" "PREFLIGHT_32658037|SRC_PMID_28145739" "DCLM_ACH_PEAK_SINGLE_CALF|DCLM_ACH_RATE_SINGLE_CALF" $true "Direct single-leg heel-raise condition; anchor status waits for rubric linkage." "Peak and rate draft claims remain non-production." "FULL_TEXT_INSUFFICIENT"
    Review "TREV_POGO" "ex_e465d1e9" "USED_AS_TRANSFER_REFERENCE" "ACHILLES_TENDON:PEAK_TENSILE_LOAD" "RDEC_ACH_PEAK" "PREFLIGHT_32658037" "DCLM_ACH_PEAK_SINGLE_HOP" $false "Repeated single-leg forward/lateral hops differ from bilateral pogo execution." "Hop-family transfer only." "TESTED_PROTOCOL_MISMATCH|BILATERAL_UNILATERAL_MISMATCH"
    Review "TREV_LINE_HOP" "ex_a3ddd8ac" "USED_AS_TRANSFER_REFERENCE" "ACHILLES_TENDON:PEAK_TENSILE_LOAD|KNEE_PATELLOFEMORAL:COMPRESSION" "RDEC_ACH_PEAK|RDEC_PFJ_COMP" "PREFLIGHT_32658037|SRC_PMID_37272685" "DCLM_ACH_PEAK_SINGLE_HOP" $false "Study hopping/jumping conditions differ in direction, leg count, and cadence." "Lateral hop-family transfer only." "TESTED_PROTOCOL_MISMATCH|BILATERAL_UNILATERAL_MISMATCH"
    Review "TREV_HOP_STICK" "ex_314df428" "USED_AS_TRANSFER_REFERENCE" "ACHILLES_TENDON:PEAK_TENSILE_LOAD|KNEE_PATELLOFEMORAL:COMPRESSION" "RDEC_ACH_PEAK|RDEC_PFJ_COMP" "PREFLIGHT_32658037|SRC_PMID_37272685" "DCLM_ACH_PEAK_SINGLE_HOP|DCLM_PFJ_COMP_SINGLE_HOP" $false "Source used repeated/max forward hop; app target adds a controlled stick landing." "Single-leg hop transfer with explicit landing limitation." "TESTED_PROTOCOL_MISMATCH"
    Review "TREV_DROP_JUMP" "ex_d6726746" "USED_AS_TRANSFER_REFERENCE" "KNEE_PATELLOFEMORAL:COMPRESSION" "RDEC_PFJ_COMP" "SRC_PMID_37272685" "DCLM_PFJ_COMP_DROP_JUMP" $true "Source double-leg drop vertical jump is close; box height and technique remain condition-specific." "Direct protocol evidence is present, but anchor status waits for rubric linkage." "TESTED_PROTOCOL_MISMATCH"
    Review "TREV_SPLIT_LUNGE" "ex_377448a9" "USED_AS_TRANSFER_REFERENCE" "KNEE_PATELLOFEMORAL:COMPRESSION|KNEE_ACL:DECELERATION_STABILIZATION" "RDEC_PFJ_COMP|RDEC_ACL_DECEL" "SRC_PMID_37272685|SRC_PMID_31593498" "DCLM_PFJ_COMP_LUNGE" $false "Composite split-step/lateral-lunge task was not directly tested; lunge and jump studies cover separate components." "Movement-component transfer only; no ACL band created." "TESTED_PROTOCOL_MISMATCH|NO_TISSUE_SPECIFIC_MECHANICAL_OUTCOME"
)
Write-Table "tissue_rubric_target_exercise_review_v1.csv" $reviewHeaders $reviews

$auditPath = Join-Path $OutputDirectory "tissue_metadata_audit_manifest_v1.csv"
$auditHeaders = @(
    "auditManifestId", "auditScope", "auditBatchId", "metadataSchemaVersion", "catalogVersion",
    "canonicalExerciseSnapshotHash", "canonicalExerciseCount", "tissueCatalogSnapshotHash", "jointTissueCount",
    "tendonTissueCount", "ligamentTissueCount", "fasciaTissueCount", "scopeManifestSnapshotHash", "scopeManifestRowCount",
    "profileSnapshotHash", "jointProfileRowCount", "tendonProfileRowCount", "ligamentProfileRowCount", "fasciaProfileRowCount",
    "rubricSnapshotHash", "modifierSnapshotHash", "recoverySnapshotHash", "evidenceRegistrySnapshotHash",
    "claimLedgerSnapshotHash", "sourceVerificationSnapshotHash", "doseCapabilitySnapshotHash", "automatedValidationStatus",
    "stableKeyCoverageStatus", "scopeCoverageStatus", "profileIntegrityStatus", "catalogEvidenceStatus",
    "exerciseLoadEvidenceIntegrityStatus", "citationVerificationStatus", "blindReviewCoverageStatus",
    "humanApprovalCoverageStatus", "doseCapabilityStatus", "lateralityCoverageStatus", "modifierValidationStatus",
    "recoveryValidationStatus", "notYetEvaluatedCount", "evaluatedAbsentCount", "evaluatedRelevantCount",
    "evaluatedIrrelevantCount", "blockedCount", "conflictingCount", "missingRecordInputCount", "sideUnresolvedCount",
    "unsupportedModifierCombinationCount", "evidenceNotApprovedCount", "anomalyFlagCount", "failedInvariantCount",
    "warningCount", "generatedBy", "generatedByType", "generatedAt", "inputSnapshotHash", "auditDecision", "auditNotes",
    "sourceCount", "verifiedSourceCount", "draftClaimCount", "draftRubricCount", "researchDecisionCount",
    "targetExerciseReviewCount", "directAnchorExerciseCount", "transferReferenceExerciseCount",
    "reviewedNotUsedExerciseCount", "blockedExerciseCount", "noComparableSourceExerciseCount",
    "blockedTissueDimensionTargetCount", "conflictingTargetCount", "missingTargetCount", "blindReviewCount",
    "finalClaimCount", "humanApprovalCount", "productionEligibleProfileCount"
)
$canonicalHash = Get-SemanticCsvHash $canonicalPath
$catalogPath = Join-Path $OutputDirectory "canonical_tissue_catalog_v1.csv"
$scopePath = Join-Path $OutputDirectory "exercise_tissue_scope_manifest_v1.csv"
$profileFiles = @("exercise_joint_load_profiles_v1.csv", "exercise_tendon_load_profiles_v1.csv", "exercise_ligament_load_profiles_v1.csv", "exercise_fascia_load_profiles_v1.csv")
$profileParts = [string[]]@($profileFiles | ForEach-Object { "$_=$(Get-SemanticCsvHash (Join-Path $OutputDirectory $_))" })
$profileHash = Get-TextHash (Join-Ordinal $profileParts "`n")
$claimParts = [string[]]@(
    "blind=$(Get-SemanticCsvHash (Join-Path $OutputDirectory 'tissue_evidence_blind_review_v1.csv'))"
    "draft=$(Get-SemanticCsvHash (Join-Path $OutputDirectory 'tissue_evidence_claims_draft_v1.csv'))"
    "final=$(Get-SemanticCsvHash (Join-Path $OutputDirectory 'tissue_evidence_claims_v1.csv'))"
)
$claimHash = Get-TextHash (Join-Ordinal $claimParts "`n")
$hashParts = @{
    canonical=$canonicalHash
    catalog=(Get-SemanticCsvHash $catalogPath)
    scope=(Get-SemanticCsvHash $scopePath)
    profiles=$profileHash
    rubric=(Get-SemanticCsvHash (Join-Path $OutputDirectory "tissue_load_band_rubric_v1.csv"))
    evidence=(Get-SemanticCsvHash (Join-Path $OutputDirectory "tissue_load_evidence_registry_v1.csv"))
    claims=$claimHash
    sourceVerification=(Get-SemanticCsvHash (Join-Path $OutputDirectory "tissue_source_verification_v1.csv"))
    research=(Get-SemanticCsvHash (Join-Path $OutputDirectory "tissue_rubric_research_log_v1.csv"))
    targetReviews=(Get-SemanticCsvHash (Join-Path $OutputDirectory "tissue_rubric_target_exercise_review_v1.csv"))
    legacyMigration=(Get-SemanticCsvHash (Join-Path $OutputDirectory "legacy_tissue_tag_migration_v1.csv"))
    doseCapability=(Get-SemanticCsvHash (Join-Path $OutputDirectory "dose_input_capability_v1.csv"))
    modifier=(Get-SemanticCsvHash (Join-Path $OutputDirectory "exercise_tissue_modifier_rules_v1.csv"))
    recovery=(Get-SemanticCsvHash (Join-Path $OutputDirectory "tissue_recovery_profiles_v1.csv"))
}
$inputParts = [string[]]@($hashParts.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" })
$inputHash = Get-TextHash (Join-Ordinal $inputParts "`n")
$auditRows = [Collections.Generic.List[hashtable]]::new()
foreach ($existing in @(Import-Csv -LiteralPath $auditPath | Where-Object auditBatchId -ne $batch)) {
    $copy = @{}
    foreach ($property in $existing.PSObject.Properties) { $copy[$property.Name] = $property.Value }
    $auditRows.Add($copy)
}
$auditRows.Add(@{
    auditManifestId="tissue_rubric_b1_$($inputHash.Substring(0,12))";auditScope="EVIDENCE_BATCH";auditBatchId=$batch
    metadataSchemaVersion="tissue_load_v1";catalogVersion="tissue_load_v1";canonicalExerciseSnapshotHash=$canonicalHash;canonicalExerciseCount="239"
    tissueCatalogSnapshotHash=$hashParts.catalog;jointTissueCount="20";tendonTissueCount="23";ligamentTissueCount="16";fasciaTissueCount="2"
    scopeManifestSnapshotHash=$hashParts.scope;scopeManifestRowCount="14579";profileSnapshotHash=$profileHash
    jointProfileRowCount="0";tendonProfileRowCount="0";ligamentProfileRowCount="0";fasciaProfileRowCount="0"
    rubricSnapshotHash=$hashParts.rubric;modifierSnapshotHash=$hashParts.modifier;recoverySnapshotHash=$hashParts.recovery
    evidenceRegistrySnapshotHash=$hashParts.evidence;claimLedgerSnapshotHash=$claimHash;sourceVerificationSnapshotHash=$hashParts.sourceVerification
    doseCapabilitySnapshotHash=$hashParts.doseCapability;automatedValidationStatus="PASS_WITH_WARNINGS";stableKeyCoverageStatus="PASS"
    scopeCoverageStatus="PASS";profileIntegrityStatus="PASS";catalogEvidenceStatus="PASS_WITH_WARNINGS"
    exerciseLoadEvidenceIntegrityStatus="PASS_WITH_WARNINGS";citationVerificationStatus="PASS";blindReviewCoverageStatus="NOT_APPLICABLE"
    humanApprovalCoverageStatus="NOT_APPLICABLE";doseCapabilityStatus="PASS";lateralityCoverageStatus="PASS"
    modifierValidationStatus="PASS";recoveryValidationStatus="PASS";notYetEvaluatedCount="14579";evaluatedAbsentCount="0"
    evaluatedRelevantCount="0";evaluatedIrrelevantCount="0";blockedCount="$(@($decisions | Where-Object researchDecision -eq 'BLOCKED_INSUFFICIENT_EVIDENCE').Count)"
    conflictingCount="0";missingRecordInputCount="0";sideUnresolvedCount="0";unsupportedModifierCombinationCount="0"
    evidenceNotApprovedCount="$($claims.Count + $rubrics.Count)";anomalyFlagCount="0";failedInvariantCount="0"
    warningCount="$(@($decisions | Where-Object researchDecision -ne 'DRAFT_RUBRIC_CREATED').Count)";generatedBy="Codex";generatedByType="AI_AGENT"
    generatedAt=$preparedAt;inputSnapshotHash=$inputHash;auditDecision="PRODUCTION_REVIEW_REQUIRED"
    auditNotes="Phase B1 draft rubric batch is complete pending independent blind review, final claims, and human approval; no production profiles were created."
    sourceCount="$($sources.Count)";verifiedSourceCount="$($sources.Count)";draftClaimCount="$($claims.Count)";draftRubricCount="$($rubrics.Count)"
    researchDecisionCount="$($decisions.Count)";targetExerciseReviewCount="$($reviews.Count)"
    directAnchorExerciseCount="$(@($reviews | Where-Object stableKey -in $directRubricsByStableKey.Keys).Count)"
    transferReferenceExerciseCount="$(@($reviews | Where-Object { $_.stableKey -notin $directRubricsByStableKey.Keys -and $_.researchUseStatus -eq 'USED_AS_TRANSFER_REFERENCE' }).Count)"
    reviewedNotUsedExerciseCount="0";blockedExerciseCount="0";noComparableSourceExerciseCount="2"
    blockedTissueDimensionTargetCount="$(@($decisions | Where-Object researchDecision -eq 'BLOCKED_INSUFFICIENT_EVIDENCE').Count)"
    conflictingTargetCount="0";missingTargetCount="0";blindReviewCount="0";finalClaimCount="0";humanApprovalCount="0";productionEligibleProfileCount="0"
})
Write-Table "tissue_metadata_audit_manifest_v1.csv" $auditHeaders $auditRows.ToArray()

Write-Output "SOURCE_COUNT=$($sources.Count)"
Write-Output "DRAFT_CLAIM_COUNT=$($claims.Count)"
Write-Output "DRAFT_RUBRIC_COUNT=$($rubrics.Count)"
Write-Output "RESEARCH_DECISION_COUNT=$($decisions.Count)"
Write-Output "TARGET_EXERCISE_REVIEW_COUNT=$($reviews.Count)"

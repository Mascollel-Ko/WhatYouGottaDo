param(
    [string]$AssetDirectory = "app/src/main/assets/metadata/tissue_load_v1",
    [string]$ResearchDocumentPath = "docs/tissue_load_phase_c3_lower_research.md"
)

$ErrorActionPreference = "Stop"
$preparedAt = "2026-07-14T01:00:03Z"
$batchId = "TISSUE_C3_MULTIDIMENSIONAL_LOWER_R1"
function Row($values) { [pscustomobject]$values }
function Export-Rows([string]$name, $rows) {
    @($rows | Sort-Object { [string]$_.PSObject.Properties.Value[0] }) |
        Export-Csv -LiteralPath (Join-Path $AssetDirectory $name) -NoTypeInformation -Encoding utf8
}
function Complete-Row($defaults, $values) {
    foreach ($key in $values.Keys) { $defaults[$key] = $values[$key] }
    Row $defaults
}
function New-Extraction($values) {
    Complete-Row ([ordered]@{
        metricExtractionId=''; sourceId=''; testedExercise=''; testedExerciseConditionId=''; appStableKeys=''
        tissueId=''; mechanicalLoadMode=''; temporalMetric=''; measurementMetric=''; normalizationBasis=''
        reportedValue=''; reportedLowerBound=''; reportedUpperBound=''; reportedDispersionType='NOT_REPORTED'
        reportedDispersionValue=''; reportedUnit=''; sampleSize=''; population=''; trainingStatus=''; healthStatus=''
        sexComposition=''; externalLoadCondition='Not reported'; relativeLoadCondition='Not reported'
        romCondition='Not reported'; velocityCondition='Not reported'; lateralityCondition='Not reported'
        surfaceCondition='Not reported'; landingCondition='Not applicable'; fatigueCondition='Not reported'
        measurementMethod=''; modelAssumptions=''; evidenceLocatorType='SOURCE_METHODS_OR_RESULTS'
        evidenceLocator=''; sourceAccessLevel='ABSTRACT_OR_FULL_TEXT'; extractionConfidence='LOW'
        extractionLimitations=''; preparedBy='Codex'; preparedByType='AI_AGENT'; preparedAt=$preparedAt
    }) $values
}
function New-Candidate($values) {
    Complete-Row ([ordered]@{
        claimCandidateId=''; researchBatchId=$batchId; metricExtractionId=''; sourceId=''; stableKey=''
        testedExercise=''; exerciseCorrespondence=''; tissueId=''; mechanicalLoadMode=''; temporalMetric=''
        measurementMetric=''; normalizationBasis=''; claimType='SOURCE_OBSERVED_MEASUREMENT'; claimParaphrase=''
        claimDirection='CONDITION_SPECIFIC'; claimValue=''; claimLowerBound=''; claimUpperBound=''
        claimDispersionType='NOT_REPORTED'; claimDispersionValue=''; claimUnit=''; externalLoadCondition='Not reported'
        relativeLoadCondition='Not reported'; romCondition='Not reported'; velocityCondition='Not reported'
        lateralityCondition='Not reported'; surfaceCondition='Not reported'; landingCondition='Not applicable'
        fatigueCondition='Not reported'; measurementMethod=''; modelAssumptions=''; evidenceLocatorType=''
        evidenceLocator=''; evidenceAccessLevel=''; maximumDefensibleBand=''; bandBasis='NO_GENERIC_BAND'
        claimSupportStatus='DRAFT_NON_PRODUCTION'; confidenceLevel='LOW'
        sourceVerificationStatus='PMID_AND_DOI_VERIFIED'; bibliographicMatchStatus='MATCHED'
        publicationIntegrityStatus='NO_ADVERSE_NOTICE_FOUND'; preparedBy='Codex'; preparedByType='AI_AGENT'
        preparedAt=$preparedAt; claimLimitations=''
    }) $values
}
function Dimension-For([string]$legacy) {
    switch ($legacy) {
        'PEAK_TENSILE_LOAD' { @('TENSION','PEAK','MODELED_TENDON_FORCE','BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE') }
        'LOADING_RATE' { @('TENSION','LOADING_RATE','MODELED_TENDON_FORCE_LOADING_RATE','BODY_WEIGHT_NORMALIZED_LOADING_RATE') }
        'PEAK_COMPRESSION' { @('COMPRESSION','PEAK','MODELED_JOINT_CONTACT_FORCE','BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE') }
        'COMPRESSION_IMPULSE' { @('COMPRESSION','IMPULSE_PER_EVENT','JOINT_CONTACT_FORCE_TIME_INTEGRAL','BODY_WEIGHT_TIME_NORMALIZED_INTERNAL_IMPULSE') }
        'COMPRESSION_LOADING_RATE' { @('COMPRESSION','LOADING_RATE','MODELED_JOINT_CONTACT_FORCE_LOADING_RATE','BODY_WEIGHT_NORMALIZED_LOADING_RATE') }
        default { throw "No C3 dimension mapping for $legacy" }
    }
}
function Correspondence-For([string]$legacy) {
    switch ($legacy) {
        'EXACT_PROTOCOL_MATCH' { 'EXACT_PROTOCOL' }
        'CLOSE_VARIANT' { 'CLOSE_VARIANT' }
        default { 'EXACT_EXERCISE_DIFFERENT_LOAD' }
    }
}

$existingSources = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory 'tissue_load_evidence_registry_v1.csv'))
$newSources = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory 'tissue_load_evidence_registry_c3_v1.csv'))
$sourceById = @{}
@($existingSources + $newSources) | ForEach-Object { $sourceById[$_.sourceId] = $_ }
$revised = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory 'tissue_evidence_claim_candidates_revised_v1.csv'))
$extractions = [Collections.Generic.List[object]]::new()
$candidates = [Collections.Generic.List[object]]::new()
$dispositions = [Collections.Generic.List[object]]::new()

foreach ($claim in $revised) {
    $dimension = Dimension-For $claim.loadDimension
    $source = $sourceById[$claim.sourceId]
    $conditionId = "C3COND_$($claim.revisedClaimCandidateId)"
    $extractionId = "C3METRIC_$($claim.revisedClaimCandidateId)"
    $extractions.Add((New-Extraction ([ordered]@{
        metricExtractionId=$extractionId; sourceId=$claim.sourceId; testedExercise=$claim.testedExercise
        testedExerciseConditionId=$conditionId; appStableKeys=$claim.stableKey; tissueId=$claim.tissueId
        mechanicalLoadMode=$dimension[0]; temporalMetric=$dimension[1]; measurementMetric=$dimension[2]
        normalizationBasis=$dimension[3]; reportedValue=$claim.value; reportedLowerBound=$claim.lowerBound
        reportedUpperBound=$claim.upperBound; reportedDispersionType=if($claim.lowerBound -or $claim.upperBound){'SOURCE_REPORTED_BOUND'}else{'POINT_ESTIMATE'}
        reportedUnit=$claim.unit; sampleSize=$source.sampleSize; population=$source.population
        trainingStatus=$source.trainingStatus; healthStatus=$source.healthStatus; sexComposition=$source.sexComposition
        externalLoadCondition=$claim.externalLoadCondition; relativeLoadCondition=$claim.relativeLoadCondition
        romCondition=$claim.romCondition; velocityCondition=$claim.velocityCondition
        lateralityCondition=$claim.bilateralOrUnilateral; surfaceCondition=$claim.surfaceCondition
        landingCondition=$claim.landingCondition; fatigueCondition=$source.fatigueCondition
        measurementMethod=$claim.measurementMethod; modelAssumptions=$claim.claimLimitations
        evidenceLocatorType='SOURCE_TABLE_OR_SUPPLEMENT'; evidenceLocator=$claim.evidenceLocator
        sourceAccessLevel='TABLE_OR_SUPPLEMENT'; extractionConfidence='MODERATE'
        extractionLimitations=$claim.claimLimitations
    })))
    $candidateId = "C3_$($claim.revisedClaimCandidateId)"
    $candidates.Add((New-Candidate ([ordered]@{
        claimCandidateId=$candidateId; metricExtractionId=$extractionId; sourceId=$claim.sourceId
        stableKey=$claim.stableKey; testedExercise=$claim.testedExercise
        exerciseCorrespondence=(Correspondence-For $claim.appExerciseCorrespondence)
        tissueId=$claim.tissueId; mechanicalLoadMode=$dimension[0]; temporalMetric=$dimension[1]
        measurementMetric=$dimension[2]; normalizationBasis=$dimension[3]
        claimParaphrase="$($claim.metric) under the exact source condition"; claimDirection=$claim.claimDirection
        claimValue=$claim.value; claimLowerBound=$claim.lowerBound; claimUpperBound=$claim.upperBound
        claimDispersionType=if($claim.lowerBound -or $claim.upperBound){'SOURCE_REPORTED_BOUND'}else{'POINT_ESTIMATE'}
        claimUnit=$claim.unit; externalLoadCondition=$claim.externalLoadCondition
        relativeLoadCondition=$claim.relativeLoadCondition; romCondition=$claim.romCondition
        velocityCondition=$claim.velocityCondition; lateralityCondition=$claim.bilateralOrUnilateral
        surfaceCondition=$claim.surfaceCondition; landingCondition=$claim.landingCondition
        fatigueCondition=$source.fatigueCondition; measurementMethod=$claim.measurementMethod
        modelAssumptions=$claim.claimLimitations; evidenceLocatorType='SOURCE_TABLE_OR_SUPPLEMENT'
        evidenceLocator=$claim.evidenceLocator; evidenceAccessLevel='TABLE_OR_SUPPLEMENT'
        maximumDefensibleBand=$claim.maximumDefensibleBand
        bandBasis=if($claim.maximumDefensibleBand){'WITHIN_STUDY_EXACT_OR_DISCLOSED_CLOSE_VARIANT'}else{'NO_GENERIC_BAND'}
        confidenceLevel=if($claim.maximumDefensibleBand){'LOW'}else{'MODERATE'}
        claimLimitations=$claim.claimLimitations
    })))
    $narrowed = $claim.revisedClaimCandidateId -in @(
        'R1C_01_ACH_SEATED_PEAK','R1C_03_ACH_REPEATED_HOP_PEAK','R1C_05_PAT_BW_SQUAT_PEAK',
        'R1C_06_PAT_BULGARIAN_PEAK','R1C_07A_PFJ_60_SQUAT_PEAK','R1C_07B_PFJ_60_SQUAT_IMPULSE',
        'R1C_07C_PFJ_60_SQUAT_RATE'
    )
    $dispositions.Add((Row ([ordered]@{
        candidateId=$claim.revisedClaimCandidateId; oldDimension=$claim.loadDimension
        newMechanicalLoadMode=$dimension[0]; newTemporalMetric=$dimension[1]; newMeasurementMetric=$dimension[2]
        disposition=if($narrowed){'RETAIN_WITH_NARROWER_CONDITION'}else{'RECLASSIFY_EXACTLY'}
        replacementCandidateIds=$candidateId
        preservedScientificPayload='Source value, unit, tested exercise, method, and exact source conditions are preserved.'
        removedInterpretation=if($claim.maximumDefensibleBand){'No interpretation beyond the retained condition-specific partial Achilles rubric.'}else{'Generic band and cross-condition interpretation remain absent.'}
        blockingReason=''; preparedBy='Codex'; preparedByType='AI_AGENT'; preparedAt=$preparedAt
    })))
}

function Add-Extraction($values) { $extractions.Add((New-Extraction $values)) }
Add-Extraction ([ordered]@{metricExtractionId='C3METRIC_28145739_ACH_FORCE';sourceId='SRC_PMID_28145739';testedExercise='Heel-raising and lowering variants';testedExerciseConditionId='C3COND_28145739_HEEL_RAISE';appStableKeys='ex_5c8751d2|ex_bd072cd|ex_5ca7133f';tissueId='ACHILLES_TENDON';mechanicalLoadMode='TENSION';temporalMetric='PEAK';measurementMetric='MODELED_TENDON_FORCE';normalizationBasis='ABSOLUTE_FORCE_NEWTON';reportedUnit='N';measurementMethod=$sourceById['SRC_PMID_28145739'].measurementMethod;modelAssumptions='Estimated tendon force and stress depend on inverse-dynamics and tendon-geometry assumptions.';evidenceLocator='PubMed Central full text; condition comparisons';extractionLimitations=$sourceById['SRC_PMID_28145739'].majorLimitations})
Add-Extraction ([ordered]@{metricExtractionId='C3METRIC_11949662_PFJ_PEAK';sourceId='SRC_PMID_11949662';testedExercise='Squat with and without 35 percent bodyweight external load';testedExerciseConditionId='C3COND_11949662_SQUAT';appStableKeys='ex_cb3c4dc2|barbell_back_squat';tissueId='KNEE_PATELLOFEMORAL';mechanicalLoadMode='COMPRESSION';temporalMetric='PEAK';measurementMetric='MODELED_JOINT_CONTACT_FORCE';normalizationBasis='BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE';reportedUnit='BW';measurementMethod=$sourceById['SRC_PMID_11949662'].measurementMethod;modelAssumptions='Patellofemoral model and knee-angle assumptions remain source specific.';evidenceLocator='PubMed abstract and source methods/results';extractionLimitations=$sourceById['SRC_PMID_11949662'].majorLimitations})
Add-Extraction ([ordered]@{metricExtractionId='C3METRIC_18632195_PFJ_PEAK';sourceId='SRC_PMID_18632195';testedExercise='Forward and side lunge variants';testedExerciseConditionId='C3COND_18632195_LUNGE';appStableKeys='ex_64644b5e|ex_b4b198de';tissueId='KNEE_PATELLOFEMORAL';mechanicalLoadMode='COMPRESSION';temporalMetric='PEAK';measurementMetric='MODELED_JOINT_CONTACT_FORCE';normalizationBasis='UNNORMALIZED_SOURCE_VALUE';reportedUnit='N';measurementMethod=$sourceById['SRC_PMID_18632195'].measurementMethod;modelAssumptions='Sagittal-plane model; stride and lunge direction remain explicit.';evidenceLocator='PubMed abstract and source methods/results';extractionLimitations=$sourceById['SRC_PMID_18632195'].majorLimitations})
Add-Extraction ([ordered]@{metricExtractionId='C3METRIC_8947402_TFJ_SHEAR';sourceId='SRC_PMID_8947402';testedExercise='Power squat, front squat, and lunge';testedExerciseConditionId='C3COND_8947402_CLOSED_CHAIN';appStableKeys='barbell_back_squat|ex_c5043892|ex_64644b5e';tissueId='KNEE_TIBIOFEMORAL';mechanicalLoadMode='ANTERIOR_POSTERIOR_SHEAR';temporalMetric='PEAK';measurementMetric='MODELED_JOINT_CONTACT_FORCE';normalizationBasis='UNNORMALIZED_SOURCE_VALUE';reportedUnit='N';measurementMethod=$sourceById['SRC_PMID_8947402'].measurementMethod;modelAssumptions='Intersegmental resultant is not a ligament strain measurement.';evidenceLocator='PubMed abstract and source methods/results';extractionLimitations=$sourceById['SRC_PMID_8947402'].majorLimitations})
Add-Extraction ([ordered]@{metricExtractionId='C3METRIC_31593498_ACL_STRAIN';sourceId='SRC_PMID_31593498';testedExercise='Single-legged jump';testedExerciseConditionId='C3COND_31593498_SINGLE_JUMP';appStableKeys='ex_d6726746|ex_314df428';tissueId='KNEE_ACL';mechanicalLoadMode='IMPACT_STABILIZATION';temporalMetric='PEAK';measurementMetric='MEASURED_LIGAMENT_STRAIN';normalizationBasis='MEASURED_LIGAMENT_STRAIN_PERCENT';reportedUnit='percent';measurementMethod=$sourceById['SRC_PMID_31593498'].measurementMethod;modelAssumptions='Protocol-specific ACL deformation; no generic landing transfer.';evidenceLocator='PubMed abstract and source methods/results';extractionLimitations=$sourceById['SRC_PMID_31593498'].majorLimitations})
Add-Extraction ([ordered]@{metricExtractionId='C3METRIC_10656979_PCL_FORCE';sourceId='SRC_PMID_10656979';testedExercise='Squat and rehabilitation exercises';testedExerciseConditionId='C3COND_10656979_SQUAT';appStableKeys='ex_cb3c4dc2|barbell_back_squat';tissueId='KNEE_PCL';mechanicalLoadMode='POSTERIOR_TRANSLATION_STRESS';temporalMetric='PEAK';measurementMetric='MODELED_LIGAMENT_FORCE';normalizationBasis='UNNORMALIZED_SOURCE_VALUE';reportedUnit='BW';measurementMethod=$sourceById['SRC_PMID_10656979'].measurementMethod;modelAssumptions='Musculoskeletal-model ligament force is condition and model specific.';evidenceLocator='PubMed abstract and source methods/results';extractionLimitations=$sourceById['SRC_PMID_10656979'].majorLimitations})
Add-Extraction ([ordered]@{metricExtractionId='C3METRIC_30923576_ANKLE_PROXY';sourceId='SRC_PMID_30923576';testedExercise='Machine-applied ankle inversion';testedExerciseConditionId='C3COND_30923576_INVERSION';appStableKeys='';tissueId='ANKLE_LATERAL_LIGAMENT_COMPLEX';mechanicalLoadMode='INVERSION_STRESS';temporalMetric='PEAK';measurementMetric='SOURCE_DEFINED_KINEMATIC_STABILITY_PROXY';normalizationBasis='UNNORMALIZED_SOURCE_VALUE';reportedUnit='degrees';measurementMethod=$sourceById['SRC_PMID_30923576'].measurementMethod;modelAssumptions='In-vitro kinematic stability is not internal ligament force.';evidenceLocator='PubMed abstract and source methods/results';extractionConfidence='LOW';extractionLimitations=$sourceById['SRC_PMID_30923576'].majorLimitations})
Add-Extraction ([ordered]@{metricExtractionId='C3METRIC_32658037_ACH_COMPOSITE';sourceId='PREFLIGHT_32658037';testedExercise='Study exercise progression';testedExerciseConditionId='C3COND_32658037_COMPOSITE';appStableKeys='';tissueId='ACHILLES_TENDON';mechanicalLoadMode='TENSION';temporalMetric='PEAK';measurementMetric='SOURCE_DEFINED_COMPOSITE_INDEX';normalizationBasis='SOURCE_DEFINED_NORMALIZED_INDEX';reportedUnit='index';measurementMethod=$sourceById['PREFLIGHT_32658037'].measurementMethod;modelAssumptions='Source formula only; no generic biomechanical interpretation.';evidenceLocator='Source-defined composite loading index';extractionConfidence='LOW';extractionLimitations='Source-specific only; rubric and profile use are prohibited.'})
Add-Extraction ([ordered]@{metricExtractionId='C3METRIC_37272685_PFJ_COMPOSITE';sourceId='SRC_PMID_37272685';testedExercise='Thirty-five weightbearing exercises';testedExerciseConditionId='C3COND_37272685_COMPOSITE';appStableKeys='';tissueId='KNEE_PATELLOFEMORAL';mechanicalLoadMode='COMPRESSION';temporalMetric='PEAK';measurementMetric='SOURCE_DEFINED_COMPOSITE_INDEX';normalizationBasis='SOURCE_DEFINED_NORMALIZED_INDEX';reportedUnit='index';measurementMethod=$sourceById['SRC_PMID_37272685'].measurementMethod;modelAssumptions='Source formula only; peak, impulse, and loading rate remain separate.';evidenceLocator='Source-defined loading index';extractionConfidence='LOW';extractionLimitations='Source-specific only; rubric and profile use are prohibited.'})

$achStrain = @(
    @('WALK','Walking','ex_681d9ed2','3.1','0.8','Bodyweight walking','Task-defined','Bilateral gait','Not applicable'),
    @('RUN3','Running at 3 m/s','ex_681d9ed2','6.5','1.6','Bodyweight running','3 m/s','Bilateral gait','Running stance'),
    @('RUN5','Running at 5 m/s','ex_681d9ed2','7.9','1.7','Bodyweight running','5 m/s','Bilateral gait','Running stance'),
    @('HOPLAND','Maximum single-leg hop landing','ex_314df428','8.8','1.6','Bodyweight','Maximum effort','Unilateral','Single landing'),
    @('HEELBW','Single-leg heel rise','ex_5ca7133f','5.8','1.3','Bodyweight','Study-defined','Unilateral','Not applicable'),
    @('HEEL12BW','Loaded single-leg heel rise','ex_5ca7133f','6.9','1.7','1.2 bodyweight external-load condition','Study-defined','Unilateral','Not applicable')
)
foreach ($spec in $achStrain) {
    Add-Extraction ([ordered]@{metricExtractionId="C3METRIC_35142563_ACH_$($spec[0])";sourceId='SRC_PMID_35142563';testedExercise=$spec[1];testedExerciseConditionId="C3COND_35142563_$($spec[0])";appStableKeys=$spec[2];tissueId='ACHILLES_TENDON';mechanicalLoadMode='TENSION';temporalMetric='PEAK';measurementMetric='MEASURED_TENDON_STRAIN';normalizationBasis='MEASURED_TENDON_STRAIN_PERCENT';reportedValue=$spec[3];reportedDispersionType='SD';reportedDispersionValue=$spec[4];reportedUnit='percent';sampleSize='16';population='Healthy trained runners';trainingStatus='Trained runners';healthStatus='Healthy';sexComposition='Mixed';externalLoadCondition=$spec[5];relativeLoadCondition=$spec[5];romCondition='Task-defined';velocityCondition=$spec[6];lateralityCondition=$spec[7];surfaceCondition='Laboratory';landingCondition=$spec[8];fatigueCondition='Non-fatigued';measurementMethod='Freehand three-dimensional ultrasound with motion analysis';modelAssumptions='Measured free Achilles tendon strain; force and energy storage are not inferred.';evidenceLocatorType='ABSTRACT_RESULTS';evidenceLocator='PubMed abstract result values';sourceAccessLevel='ABSTRACT';extractionConfidence='MODERATE';extractionLimitations='Healthy trained runners; task-specific strain; no force conversion.'})
}

foreach ($metric in @(
    @('PEAK','PEAK','MODELED_TENDON_STRESS','MPa'),
    @('IMPULSE','IMPULSE_PER_EVENT','MODELED_TENDON_STRESS_TIME_INTEGRAL','MPa*s'),
    @('RATE','LOADING_RATE','MODELED_TENDON_STRESS_LOADING_RATE','MPa/s')
)) {
    Add-Extraction ([ordered]@{metricExtractionId="C3METRIC_31193251_PAT_$($metric[0])";sourceId='SRC_PMID_31193251';testedExercise='Forward step lunge with knee in front of or behind toes';testedExerciseConditionId="C3COND_31193251_$($metric[0])";appStableKeys='ex_64644b5e';tissueId='PATELLAR_TENDON';mechanicalLoadMode='TENSION';temporalMetric=$metric[1];measurementMetric=$metric[2];normalizationBasis='UNNORMALIZED_SOURCE_VALUE';reportedUnit=$metric[3];sampleSize='25';population='Healthy adults';healthStatus='Healthy';externalLoadCondition='Bodyweight';relativeLoadCondition='Bodyweight task';romCondition='Knee travel condition defined by protocol';velocityCondition='Controlled';lateralityCondition='Unilateral lead limb';surfaceCondition='Laboratory';landingCondition='Not applicable';fatigueCondition='Non-fatigued';measurementMethod='Motion analysis and inverse dynamics with patellar tendon stress estimation';modelAssumptions='Patellar tendon cross-sectional area and inverse-dynamics assumptions.';evidenceLocator='PubMed Central methods/results; exact numeric tables not transcribed without direct table extraction';extractionConfidence='LOW';extractionLimitations='Qualitative condition comparison only in this package; no threshold or band.'})
}
Add-Extraction ([ordered]@{metricExtractionId='C3METRIC_21092960_ACL_STRAIN';sourceId='SRC_PMID_21092960';testedExercise='Dynamic single-leg jump landing';testedExerciseConditionId='C3COND_21092960_JUMP_LANDING';appStableKeys='ex_d6726746';tissueId='KNEE_ACL';mechanicalLoadMode='IMPACT_STABILIZATION';temporalMetric='PEAK';measurementMetric='MEASURED_LIGAMENT_STRAIN';normalizationBasis='MEASURED_LIGAMENT_STRAIN_PERCENT';reportedValue='12';reportedDispersionType='SD';reportedDispersionValue='7';reportedUnit='percent';sampleSize='8';population='Healthy adults';healthStatus='Healthy';externalLoadCondition='Bodyweight';relativeLoadCondition='Bodyweight task';romCondition='Task-defined';velocityCondition='Dynamic landing';lateralityCondition='Unilateral';surfaceCondition='Laboratory';landingCondition='Single-leg jump landing';fatigueCondition='Non-fatigued';measurementMethod='MRI, motion analysis, and biplanar fluoroscopy';modelAssumptions='Peak strain timing is protocol specific and occurred 55 plus or minus 14 ms before ground impact.';evidenceLocatorType='ABSTRACT_RESULTS';evidenceLocator='PubMed abstract result values';sourceAccessLevel='ABSTRACT';extractionConfidence='MODERATE';extractionLimitations='Small sample; close-variant app mapping only; no generic landing band.'})

$cfl = @(@('5N','5 N','31.9','14.0'),@('10N','10 N','51.0','15.8'),@('15N','15 N','75.4','21.3'))
foreach ($spec in $cfl) {
    Add-Extraction ([ordered]@{metricExtractionId="C3METRIC_25059338_CFL_$($spec[0])";sourceId='SRC_PMID_25059338';testedExercise='Standardized passive ankle inversion';testedExerciseConditionId="C3COND_25059338_$($spec[0])";appStableKeys='';tissueId='ANKLE_LATERAL_LIGAMENT_COMPLEX';mechanicalLoadMode='INVERSION_STRESS';temporalMetric='PEAK';measurementMetric='MODELED_LIGAMENT_FORCE';normalizationBasis='ABSOLUTE_FORCE_NEWTON';reportedValue=$spec[2];reportedDispersionType='SD';reportedDispersionValue=$spec[3];reportedUnit='N';sampleSize='24';population='Healthy ankles and cadaver specimens';healthStatus='Healthy and cadaver';externalLoadCondition=$spec[1]+' tester inversion load';relativeLoadCondition='Not applicable';romCondition='Device-defined inversion';velocityCondition='Controlled';lateralityCondition='Tested ankle';surfaceCondition='Instrumented device';landingCondition='Not applicable';fatigueCondition='Non-fatigued';measurementMethod='Instrumented inversion testing with ligament-force estimation';modelAssumptions='Passive test-device force is not an exercise or landing load.';evidenceLocatorType='ABSTRACT_RESULTS';evidenceLocator='PubMed abstract result values';sourceAccessLevel='ABSTRACT';extractionConfidence='LOW';extractionLimitations='Boundary evidence only; excluded from app claims and rubrics.'})
}

$power = @(
    @('PFJ','KNEE_PATELLOFEMORAL','26.7','4.3'),
    @('TFJ','KNEE_TIBIOFEMORAL','23.2','3.9'),
    @('ANKLE','ANKLE_TALOCRURAL','11.5','2.2')
)
foreach ($spec in $power) {
    Add-Extraction ([ordered]@{metricExtractionId="C3METRIC_40705768_$($spec[0])_PEAK";sourceId='SRC_PMID_40705768';testedExercise='Competition-depth barbell back squat at 90 percent 1RM';testedExerciseConditionId="C3COND_40705768_$($spec[0])_90RM";appStableKeys='barbell_back_squat';tissueId=$spec[1];mechanicalLoadMode='COMPRESSION';temporalMetric='PEAK';measurementMetric='MODELED_JOINT_CONTACT_FORCE';normalizationBasis='BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE';reportedValue=$spec[2];reportedDispersionType='SD';reportedDispersionValue=$spec[3];reportedUnit='BW';sampleSize='29';population='Elite powerlifters';trainingStatus='Elite powerlifters';healthStatus='Healthy';sexComposition='Mixed';externalLoadCondition='90 percent 1RM barbell back squat';relativeLoadCondition='90 percent 1RM';romCondition='Competition depth';velocityCondition='Self-selected competition squat';lateralityCondition='Bilateral';surfaceCondition='Laboratory force plates';landingCondition='Not applicable';fatigueCondition='Non-fatigued';measurementMethod='Three-dimensional motion capture, force plates, and musculoskeletal modeling';modelAssumptions='Model-specific joint contact force; elite cohort and high external load.';evidenceLocatorType='ABSTRACT_RESULTS';evidenceLocator='PubMed abstract result values';sourceAccessLevel='ABSTRACT';extractionConfidence='MODERATE';extractionLimitations='Do not merge with bodyweight-task models or create a cross-model rubric.'})
}

$newCandidateSpecs = @(
    @('C3_ACH_WEIGHTED_HEEL_STRAIN','C3METRIC_35142563_ACH_HEEL12BW','ex_5ca7133f','EXACT_EXERCISE_DIFFERENT_LOAD','MODERATE'),
    @('C3_ACH_MAX_HOP_LANDING_STRAIN','C3METRIC_35142563_ACH_HOPLAND','ex_314df428','CLOSE_VARIANT','LOW'),
    @('C3_ACL_JUMP_LANDING_STRAIN','C3METRIC_21092960_ACL_STRAIN','ex_d6726746','CLOSE_VARIANT','LOW'),
    @('C3_PFJ_POWER_SQUAT_PEAK','C3METRIC_40705768_PFJ_PEAK','barbell_back_squat','EXACT_EXERCISE_DIFFERENT_LOAD','MODERATE'),
    @('C3_TFJ_POWER_SQUAT_PEAK','C3METRIC_40705768_TFJ_PEAK','barbell_back_squat','EXACT_EXERCISE_DIFFERENT_LOAD','MODERATE'),
    @('C3_ANKLE_POWER_SQUAT_PEAK','C3METRIC_40705768_ANKLE_PEAK','barbell_back_squat','EXACT_EXERCISE_DIFFERENT_LOAD','MODERATE')
)
foreach ($spec in $newCandidateSpecs) {
    $x = $extractions | Where-Object metricExtractionId -eq $spec[1] | Select-Object -First 1
    $candidates.Add((New-Candidate ([ordered]@{
        claimCandidateId=$spec[0];metricExtractionId=$x.metricExtractionId;sourceId=$x.sourceId;stableKey=$spec[2]
        testedExercise=$x.testedExercise;exerciseCorrespondence=$spec[3];tissueId=$x.tissueId
        mechanicalLoadMode=$x.mechanicalLoadMode;temporalMetric=$x.temporalMetric;measurementMetric=$x.measurementMetric
        normalizationBasis=$x.normalizationBasis;claimParaphrase='Condition-specific source measurement under the reported protocol.'
        claimValue=$x.reportedValue;claimLowerBound=$x.reportedLowerBound;claimUpperBound=$x.reportedUpperBound
        claimDispersionType=$x.reportedDispersionType;claimDispersionValue=$x.reportedDispersionValue;claimUnit=$x.reportedUnit
        externalLoadCondition=$x.externalLoadCondition;relativeLoadCondition=$x.relativeLoadCondition;romCondition=$x.romCondition
        velocityCondition=$x.velocityCondition;lateralityCondition=$x.lateralityCondition;surfaceCondition=$x.surfaceCondition
        landingCondition=$x.landingCondition;fatigueCondition=$x.fatigueCondition;measurementMethod=$x.measurementMethod
        modelAssumptions=$x.modelAssumptions;evidenceLocatorType=$x.evidenceLocatorType;evidenceLocator=$x.evidenceLocator
        evidenceAccessLevel=$x.sourceAccessLevel;confidenceLevel=$spec[4];claimLimitations=$x.extractionLimitations
    })))
}

$rubrics = [Collections.Generic.List[object]]::new()
$rubrics.Add((Row ([ordered]@{rubricId='C3_RUBRIC_ACH_TENSION_PEAK_MODERATE';tissueId='ACHILLES_TENDON';mechanicalLoadMode='TENSION';temporalMetric='PEAK';measurementMetric='MODELED_TENDON_FORCE';normalizationBasis='BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE';loadBand='MODERATE';metricLowerBound='3.0';metricUpperBound='3.0';lowerBoundInclusive='true';upperBoundInclusive='true';metricUnit='BW';anchorStableKeys='ex_5ca7133f';anchorConditionIds='C3COND_R1C_02_ACH_SINGLE_CALF_PEAK';anchorClaimCandidateIds='C3_R1C_02_ACH_SINGLE_CALF_PEAK';sourceRefs='PREFLIGHT_32658037';assignmentMethod='WITHIN_STUDY_EXACT_CONDITION_ANCHOR';comparisonPopulation='Healthy young adults';comparisonMethodFamily='CONSTRAINED_FREE_BODY_ACHILLES_FORCE_MODEL';confidenceLevel='LOW';rubricStatus='PARTIAL_NON_PRODUCTION_PENDING_HUMAN_APPROVAL';preparedBy='Codex';preparedByType='AI_AGENT';preparedAt=$preparedAt;rubricLimitations='Exact standing single-leg bodyweight task only; no LOW interval and no external-load multiplier.'})))
$rubrics.Add((Row ([ordered]@{rubricId='C3_RUBRIC_ACH_TENSION_PEAK_VERY_HIGH';tissueId='ACHILLES_TENDON';mechanicalLoadMode='TENSION';temporalMetric='PEAK';measurementMetric='MODELED_TENDON_FORCE';normalizationBasis='BODY_WEIGHT_NORMALIZED_INTERNAL_FORCE';loadBand='VERY_HIGH';metricLowerBound='7.3';metricUpperBound='7.3';lowerBoundInclusive='true';upperBoundInclusive='true';metricUnit='BW';anchorStableKeys='ex_314df428';anchorConditionIds='C3COND_R1C_03_ACH_REPEATED_HOP_PEAK';anchorClaimCandidateIds='C3_R1C_03_ACH_REPEATED_HOP_PEAK';sourceRefs='PREFLIGHT_32658037';assignmentMethod='WITHIN_STUDY_DISCLOSED_CLOSE_VARIANT_ANCHOR';comparisonPopulation='Healthy young adults';comparisonMethodFamily='CONSTRAINED_FREE_BODY_ACHILLES_FORCE_MODEL';confidenceLevel='VERY_LOW';rubricStatus='PARTIAL_NON_PRODUCTION_PENDING_HUMAN_APPROVAL';preparedBy='Codex';preparedByType='AI_AGENT';preparedAt=$preparedAt;rubricLimitations='Repeated unilateral hopping only; does not apply to every hop, jump, or landing.'})))

$decisionSpecs = [Collections.Generic.List[object]]::new()
function Add-Decision([string]$id,[string]$tissue,[string]$mode,[string]$temporal,[string]$metric,[string]$decision,[string]$included,[string]$excluded,[string]$reason,[string]$blocker='') {
    $decisionSpecs.Add((Row ([ordered]@{researchDecisionId="C3DEC_$id";researchBatchId=$batchId;tissueId=$tissue;mechanicalLoadMode=$mode;temporalMetric=$temporal;measurementMetric=$metric;targetStableKeys='';database='PubMed|PubMed Central|Crossref';searchQuery="$tissue $mode $temporal exercise external load ROM $metric";searchDate='2026-07-14';includedSourceIds=$included;excludedSourceIds=$excluded;decision=$decision;decisionReason=$reason;remainingBlocker=$blocker;preparedBy='Codex';preparedByType='AI_AGENT';preparedAt=$preparedAt})))
}
Add-Decision 'ACH_FORCE_PEAK' 'ACHILLES_TENDON' 'TENSION' 'PEAK' 'MODELED_TENDON_FORCE' 'DRAFT_RUBRIC_CREATED' 'PREFLIGHT_32658037|SRC_PMID_28145739' '' 'Condition-specific force anchors support only two partial bands.'
Add-Decision 'ACH_STRAIN_PEAK' 'ACHILLES_TENDON' 'TENSION' 'PEAK' 'MEASURED_TENDON_STRAIN' 'SOURCE_CLAIMS_CREATED_NO_RUBRIC' 'SRC_PMID_35142563' '' 'Direct task-specific strain values are retained separately from force.'
Add-Decision 'ACH_IMPULSE' 'ACHILLES_TENDON' 'TENSION' 'IMPULSE_PER_EVENT' 'TENDON_FORCE_TIME_INTEGRAL' 'EVIDENCE_FOUND_BUT_NOT_COMPARABLE' 'PREFLIGHT_32658037' '' 'Per-event impulse exists but no generic compatible boundaries were established.' 'Condition-specific impulse rubric remains missing.'
Add-Decision 'ACH_RATE' 'ACHILLES_TENDON' 'TENSION' 'LOADING_RATE' 'MODELED_TENDON_FORCE_LOADING_RATE' 'EVIDENCE_FOUND_BUT_NOT_COMPARABLE' 'PREFLIGHT_32658037' '' 'Loading rate remains separate from peak force.' 'No loading-rate rubric.'
Add-Decision 'ACH_CYCLIC' 'ACHILLES_TENDON' 'TENSION' 'CYCLIC_EXPOSURE' 'SOURCE_DEFINED_CYCLIC_EXPOSURE' 'BLOCKED_MISSING_DOSE_INPUT' '' 'PREFLIGHT_32658037' 'Repeated tasks do not provide an app-wide event-count contract.' 'Valid event count, laterality, and no-double-counting contract required.'
Add-Decision 'ACH_ENERGY_PEAK' 'ACHILLES_TENDON' 'ENERGY_STORAGE_RELEASE' 'PEAK' 'MEASURED_TENDON_ENERGY_STORAGE' 'BLOCKED_INSUFFICIENT_EVIDENCE' '' 'SRC_PMID_35142563' 'Strain is not direct energy storage.' 'Direct force-elongation energy measurement required.'
Add-Decision 'ACH_ENERGY_CYCLIC' 'ACHILLES_TENDON' 'ENERGY_STORAGE_RELEASE' 'CYCLIC_EXPOSURE' 'SOURCE_DEFINED_CYCLIC_EXPOSURE' 'BLOCKED_MISSING_DOSE_INPUT' '' '' 'No compatible cyclic energy source and dose model were established.' 'Energy per event and valid event count required.'

Add-Decision 'PAT_FORCE_PEAK' 'PATELLAR_TENDON' 'TENSION' 'PEAK' 'MODELED_TENDON_FORCE' 'SOURCE_CLAIMS_CREATED_NO_RUBRIC' 'SRC_PMID_37847102' '' 'Exact bodyweight squat and Bulgarian values are retained without generic bands.'
Add-Decision 'PAT_STRESS_PEAK' 'PATELLAR_TENDON' 'TENSION' 'PEAK' 'MODELED_TENDON_STRESS' 'EVIDENCE_FOUND_BUT_NOT_COMPARABLE' 'SRC_PMID_31193251' '' 'Forward-lunge knee-travel comparison is condition specific.' 'Exact numeric table extraction and cross-model comparison remain blocked.'
Add-Decision 'PAT_IMPULSE' 'PATELLAR_TENDON' 'TENSION' 'IMPULSE_PER_EVENT' 'TENDON_FORCE_TIME_INTEGRAL' 'EVIDENCE_FOUND_BUT_NOT_COMPARABLE' 'SRC_PMID_37847102' '' 'Impulse is reported separately but has no generic rubric.'
Add-Decision 'PAT_STRESS_IMPULSE' 'PATELLAR_TENDON' 'TENSION' 'IMPULSE_PER_EVENT' 'MODELED_TENDON_STRESS_TIME_INTEGRAL' 'EVIDENCE_FOUND_BUT_NOT_COMPARABLE' 'SRC_PMID_31193251' '' 'Stress impulse is not tendon force impulse.' 'No compatible rubric.'
Add-Decision 'PAT_RATE' 'PATELLAR_TENDON' 'TENSION' 'LOADING_RATE' 'MODELED_TENDON_FORCE_LOADING_RATE' 'EVIDENCE_FOUND_BUT_NOT_COMPARABLE' 'SRC_PMID_37847102' '' 'Condition-specific rates exist without generic boundaries.'
Add-Decision 'PAT_STRESS_RATE' 'PATELLAR_TENDON' 'TENSION' 'LOADING_RATE' 'MODELED_TENDON_STRESS_LOADING_RATE' 'EVIDENCE_FOUND_BUT_NOT_COMPARABLE' 'SRC_PMID_31193251' '' 'Stress rate remains distinct from force rate.'
Add-Decision 'PAT_CYCLIC' 'PATELLAR_TENDON' 'TENSION' 'CYCLIC_EXPOSURE' 'SOURCE_DEFINED_CYCLIC_EXPOSURE' 'BLOCKED_MISSING_DOSE_INPUT' '' 'SRC_PMID_37847102' 'No approved cumulative tendon dose model.' 'Valid event count required.'
Add-Decision 'PAT_STRAIN' 'PATELLAR_TENDON' 'TENSION' 'PEAK' 'MEASURED_TENDON_STRAIN' 'BLOCKED_INSUFFICIENT_EVIDENCE' '' '' 'Reviewed isometric strain evidence is not transferable to requested dynamic tasks.' 'Dynamic exercise-specific direct strain evidence required.'
Add-Decision 'PAT_ECCENTRIC' 'PATELLAR_TENDON' 'TENSION' 'ECCENTRIC_PHASE_PEAK' 'MODELED_TENDON_FORCE' 'BLOCKED_INSUFFICIENT_EVIDENCE' '' 'SRC_PMID_37847102' 'Exercise names do not resolve phase-specific force.' 'Phase-resolved source values required.'

foreach ($metric in @(
    @('QUAD_PEAK','PEAK','MODELED_TENDON_FORCE'),@('QUAD_IMPULSE','IMPULSE_PER_EVENT','TENDON_FORCE_TIME_INTEGRAL'),
    @('QUAD_RATE','LOADING_RATE','MODELED_TENDON_FORCE_LOADING_RATE'),@('QUAD_CYCLIC','CYCLIC_EXPOSURE','SOURCE_DEFINED_CYCLIC_EXPOSURE'),
    @('QUAD_ECC_PEAK','ECCENTRIC_PHASE_PEAK','MODELED_TENDON_FORCE'),@('QUAD_ECC_IMPULSE','ECCENTRIC_PHASE_IMPULSE','TENDON_FORCE_TIME_INTEGRAL'),
    @('QUAD_STRAIN','PEAK','MEASURED_TENDON_STRAIN')
)) { Add-Decision $metric[0] 'QUADRICEPS_TENDON' 'TENSION' $metric[1] $metric[2] 'BLOCKED_INSUFFICIENT_EVIDENCE' '' '' 'No compatible exercise-specific primary source was verified in this batch.' 'Dedicated quadriceps-tendon research required.' }

Add-Decision 'PFJ_PEAK' 'KNEE_PATELLOFEMORAL' 'COMPRESSION' 'PEAK' 'MODELED_JOINT_CONTACT_FORCE' 'SOURCE_CLAIMS_CREATED_NO_RUBRIC' 'SRC_PMID_37272685|SRC_PMID_11949662|SRC_PMID_18632195|SRC_PMID_40705768' '' 'Values are preserved by exact source condition and model.'
Add-Decision 'PFJ_IMPULSE' 'KNEE_PATELLOFEMORAL' 'COMPRESSION' 'IMPULSE_PER_EVENT' 'JOINT_CONTACT_FORCE_TIME_INTEGRAL' 'EVIDENCE_FOUND_BUT_NOT_COMPARABLE' 'SRC_PMID_37272685' '' 'Same-study impulse values remain separate from peak and rate.'
Add-Decision 'PFJ_RATE' 'KNEE_PATELLOFEMORAL' 'COMPRESSION' 'LOADING_RATE' 'MODELED_JOINT_CONTACT_FORCE_LOADING_RATE' 'EVIDENCE_FOUND_BUT_NOT_COMPARABLE' 'SRC_PMID_37272685' '' 'Same-study rates exist without generic boundaries.'
Add-Decision 'PFJ_SESSION' 'KNEE_PATELLOFEMORAL' 'COMPRESSION' 'CUMULATIVE_SESSION_IMPULSE' 'JOINT_CONTACT_FORCE_TIME_INTEGRAL' 'BLOCKED_MISSING_DOSE_INPUT' 'SRC_PMID_37272685' '' 'Per-event impulse exists but runtime event counts are not activated.' 'Valid repetitions or event counts and laterality required.'

Add-Decision 'TFJ_PEAK' 'KNEE_TIBIOFEMORAL' 'COMPRESSION' 'PEAK' 'MODELED_JOINT_CONTACT_FORCE' 'SOURCE_CLAIMS_CREATED_NO_RUBRIC' 'SRC_PMID_8947402|SRC_PMID_40705768' '' 'Condition-bound modeled joint-force evidence is retained without pooling.'
Add-Decision 'TFJ_IMPULSE' 'KNEE_TIBIOFEMORAL' 'COMPRESSION' 'IMPULSE_PER_EVENT' 'JOINT_CONTACT_FORCE_TIME_INTEGRAL' 'BLOCKED_INSUFFICIENT_EVIDENCE' '' 'SRC_PMID_8947402' 'No compatible per-event integral was extracted.' 'Direct force-time integral required.'
Add-Decision 'TFJ_SHEAR_PEAK' 'KNEE_TIBIOFEMORAL' 'ANTERIOR_POSTERIOR_SHEAR' 'PEAK' 'MODELED_JOINT_CONTACT_FORCE' 'SOURCE_CLAIMS_CREATED_NO_RUBRIC' 'SRC_PMID_8947402' '' 'Closed-chain source supports condition-specific resultant shear only.'
Add-Decision 'TFJ_SHEAR_IMPULSE' 'KNEE_TIBIOFEMORAL' 'ANTERIOR_POSTERIOR_SHEAR' 'IMPULSE_PER_EVENT' 'JOINT_CONTACT_FORCE_TIME_INTEGRAL' 'BLOCKED_INSUFFICIENT_EVIDENCE' '' 'SRC_PMID_8947402' 'No compatible shear impulse was extracted.' 'Force-time integral required.'
Add-Decision 'TFJ_ROTATION' 'KNEE_TIBIOFEMORAL' 'ROTATIONAL_STRESS' 'PEAK' 'EXTERNAL_JOINT_MOMENT' 'BLOCKED_NO_VALIDATED_PROXY' '' '' 'No validated mapping from external rotation moment to internal tissue load.' 'Validated proxy mapping required.'

Add-Decision 'ACL_ANTERIOR' 'KNEE_ACL' 'ANTERIOR_TRANSLATION_STRESS' 'PEAK' 'MODELED_LIGAMENT_FORCE' 'EVIDENCE_FOUND_BUT_NOT_COMPARABLE' 'SRC_PMID_10656979' '' 'Model-specific rehabilitation force evidence is retained without a generic claim.'
Add-Decision 'ACL_IMPACT_PEAK' 'KNEE_ACL' 'IMPACT_STABILIZATION' 'PEAK' 'MEASURED_LIGAMENT_STRAIN' 'SOURCE_CLAIMS_CREATED_NO_RUBRIC' 'SRC_PMID_21092960|SRC_PMID_31593498' '' 'Direct protocol-specific strain evidence is retained without a landing-wide band.'
Add-Decision 'ACL_VALGUS' 'KNEE_ACL' 'VALGUS_STRESS' 'PEAK' 'EXTERNAL_JOINT_MOMENT' 'BLOCKED_NO_VALIDATED_PROXY' '' '' 'Generic valgus moment is not ACL strain.' 'Validated tissue-specific mapping required.'
Add-Decision 'ACL_ROTATION' 'KNEE_ACL' 'INTERNAL_ROTATION_STRESS' 'PEAK' 'EXTERNAL_JOINT_MOMENT' 'BLOCKED_NO_VALIDATED_PROXY' '' '' 'Generic internal-rotation moment is not ACL strain.' 'Validated tissue-specific mapping required.'
Add-Decision 'ACL_IMPACT_RATE' 'KNEE_ACL' 'IMPACT_STABILIZATION' 'LOADING_RATE' 'GROUND_REACTION_FORCE_LOADING_RATE' 'BLOCKED_NO_VALIDATED_PROXY' '' 'SRC_PMID_21092960' 'ACL strain peak is not ground-reaction loading rate.' 'Validated internal mapping required.'
Add-Decision 'ACL_CYCLIC' 'KNEE_ACL' 'ANTERIOR_TRANSLATION_STRESS' 'CYCLIC_EXPOSURE' 'SOURCE_DEFINED_CYCLIC_EXPOSURE' 'BLOCKED_MISSING_DOSE_INPUT' '' '' 'No compatible cyclic ACL dose model.' 'Event count and tissue-specific per-event metric required.'
Add-Decision 'PCL_POSTERIOR' 'KNEE_PCL' 'POSTERIOR_TRANSLATION_STRESS' 'PEAK' 'MODELED_LIGAMENT_FORCE' 'SOURCE_CLAIMS_CREATED_NO_RUBRIC' 'SRC_PMID_10656979' '' 'Condition-specific modeled PCL force is retained without generic bands.'
Add-Decision 'MCL_VALGUS' 'KNEE_MCL' 'VALGUS_STRESS' 'PEAK' 'MODELED_LIGAMENT_FORCE' 'BLOCKED_INSUFFICIENT_EVIDENCE' '' '' 'No compatible exercise-specific MCL source was verified.' 'Primary exercise mechanics required.'

Add-Decision 'TALOCRURAL_PEAK' 'ANKLE_TALOCRURAL' 'COMPRESSION' 'PEAK' 'MODELED_JOINT_CONTACT_FORCE' 'SOURCE_CLAIMS_CREATED_NO_RUBRIC' 'SRC_PMID_40705768' '' 'Elite powerlifting squat ankle contact force is condition-bound.'
Add-Decision 'TALOCRURAL_RATE' 'ANKLE_TALOCRURAL' 'IMPACT_STABILIZATION' 'LOADING_RATE' 'GROUND_REACTION_FORCE_LOADING_RATE' 'BLOCKED_NO_VALIDATED_PROXY' '' '' 'External loading rate is not internal talocrural load.' 'Validated mapping required.'
Add-Decision 'SUBTALAR_ROTATION' 'ANKLE_SUBTALAR' 'ROTATIONAL_STRESS' 'PEAK' 'SOURCE_DEFINED_KINEMATIC_STABILITY_PROXY' 'EVIDENCE_FOUND_BUT_NOT_COMPARABLE' 'SRC_PMID_30923576' '' 'In-vitro stability evidence is a source-specific proxy only.'
Add-Decision 'SUBTALAR_INVERSION' 'ANKLE_SUBTALAR' 'INVERSION_STRESS' 'PEAK' 'SOURCE_DEFINED_KINEMATIC_STABILITY_PROXY' 'BLOCKED_NO_VALIDATED_PROXY' 'SRC_PMID_30923576' '' 'Kinematic stability cannot be converted to tissue force.' 'Validated internal-load mapping required.'
Add-Decision 'ANKLE_LAT_INV' 'ANKLE_LATERAL_LIGAMENT_COMPLEX' 'INVERSION_STRESS' 'PEAK' 'MODELED_LIGAMENT_FORCE' 'EVIDENCE_FOUND_BUT_NOT_COMPARABLE' 'SRC_PMID_25059338|SRC_PMID_30923576' '' 'Passive inversion boundary evidence is not an exercise claim.'
Add-Decision 'ANKLE_LAT_RATE' 'ANKLE_LATERAL_LIGAMENT_COMPLEX' 'IMPACT_STABILIZATION' 'LOADING_RATE' 'GROUND_REACTION_FORCE_LOADING_RATE' 'BLOCKED_NO_VALIDATED_PROXY' '' '' 'No validated external-to-ligament loading-rate mapping.' 'Validated mapping required.'
Add-Decision 'ANKLE_LAT_CYCLIC' 'ANKLE_LATERAL_LIGAMENT_COMPLEX' 'INVERSION_STRESS' 'CYCLIC_EXPOSURE' 'SOURCE_DEFINED_CYCLIC_EXPOSURE' 'BLOCKED_MISSING_DOSE_INPUT' '' '' 'No compatible per-event exposure and count contract.' 'Per-event tissue metric and count required.'
Add-Decision 'ANKLE_DELTOID_EVERSION' 'ANKLE_DELTOID_LIGAMENT' 'EVERSION_STRESS' 'PEAK' 'MODELED_LIGAMENT_FORCE' 'BLOCKED_INSUFFICIENT_EVIDENCE' '' '' 'No exercise-specific primary source was verified in this batch.' 'Dedicated eversion-load research required.'

$mapping = @(Import-Csv -LiteralPath (Join-Path $AssetDirectory 'tissue_canonical_exercise_mapping_audit_v1.csv'))
$familySources = @{
    'MAIN_LOWER_STRENGTH'='SRC_PMID_37272685|SRC_PMID_37847102|SRC_PMID_11949662|SRC_PMID_40705768'
    'BODYWEIGHT_LOWER_PATTERN'='SRC_PMID_37272685|SRC_PMID_37847102|SRC_PMID_11949662'
    'KNEE_DOMINANT_ACCESSORY'='SRC_PMID_37272685|SRC_PMID_37847102|SRC_PMID_11949662'
    'UNILATERAL_LOWER_STRENGTH'='SRC_PMID_37272685|SRC_PMID_37847102|SRC_PMID_18632195|SRC_PMID_31193251'
    'UNILATERAL_LOWER_ACCESSORY'='SRC_PMID_37272685|SRC_PMID_18632195|SRC_PMID_31193251'
    'ANKLE_CALF_SUPPORT'='PREFLIGHT_32658037|SRC_PMID_28145739|SRC_PMID_35142563'
    'PLYOMETRIC_POWER'='SRC_PMID_37272685|SRC_PMID_35142563|SRC_PMID_21092960'
    'ANKLE_SSC_CONDITIONING'='SRC_PMID_37272685|SRC_PMID_35142563'
}
$exactDifferent = @('ex_5c8751d2','ex_5ca7133f','ex_cb3c4dc2','barbell_back_squat','ex_e2efd0fe','ex_64644b5e')
$close = @('ex_d6726746','ex_314df428')
$transferVariables = 'EXTERNAL_LOAD|RELATIVE_LOAD|ROM|MOVEMENT_SPEED|LATERALITY|REAR_FOOT_ELEVATION|STEP_LENGTH|TRUNK_ANGLE|KNEE_TRAVEL|JUMP_DIRECTION|CONTACT_DURATION|DROP_HEIGHT|REBOUND_VERSUS_STICK|REPEATED_VERSUS_SINGLE_EVENT|SURFACE'
$correspondences = foreach ($row in $mapping) {
    $sources = [string]$familySources[$row.canonicalCategory]
    $correspondence = if($row.stableKey -in $exactDifferent){'EXACT_EXERCISE_DIFFERENT_LOAD'}elseif($row.stableKey -in $close){'CLOSE_VARIANT'}elseif($sources){'MOVEMENT_FAMILY_TRANSFER'}else{'NO_SUPPORTED_CORRESPONDENCE'}
    Row ([ordered]@{stableKey=$row.stableKey;canonicalDisplayName=$row.canonicalDisplayName;movementVariant=$row.movementVariant;correspondence=$correspondence;sourceIds=$sources;transferVariables=$transferVariables;transferBoundary='No source result propagates unless every relevant transfer variable is reviewed; display-name substring matching is prohibited.';preparedBy='Codex';preparedByType='AI_AGENT';preparedAt=$preparedAt})
}

Export-Rows 'tissue_source_metric_extraction_v1.csv' $extractions
Export-Rows 'tissue_revised_candidate_disposition_c3_v1.csv' $dispositions
Export-Rows 'tissue_evidence_claim_candidates_multidimensional_v1.csv' $candidates
Export-Rows 'tissue_load_rubric_v2.csv' $rubrics
Export-Rows 'tissue_research_decision_c3_v1.csv' $decisionSpecs
Export-Rows 'tissue_exercise_variant_correspondence_c3_v1.csv' $correspondences

function MarkdownCell([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return "-" }
    return ($value -replace '\|', '<br>' -replace '\r?\n', ' ')
}
$document = [Collections.Generic.List[string]]::new()
$document.Add('# Phase C3 Lower-Limb Multidimensional Tissue-Load Research')
$document.Add('')
$document.Add('Status: MULTIDIMENSIONAL_C_APPROVAL_PACKAGE_PARTIAL')
$document.Add('')
$document.Add('This batch re-extracts source measurements into separate mechanical-load, temporal, measurement, and normalization identities. It does not approve claims, create production profiles, or activate runtime tissue-load calculations.')
$document.Add('')
$document.Add('## Search and source boundary')
$document.Add('')
$document.Add('- Search date: 2026-07-14.')
$document.Add('- Databases: PubMed, PubMed Central, Crossref, and official publisher metadata.')
$document.Add('- Queries were separated by tissue, load mode, temporal metric, exercise, external load, ROM, and measurement method.')
$document.Add('- Primary studies were preferred. Search snippets, rehabilitation websites, blogs, vendor pages, and unsourced tables were not used as evidence.')
$document.Add('- The ten existing verified sources were re-extracted. Five additional primary studies were PMID/DOI verified and publication-integrity checked.')
$document.Add('')
$document.Add('| Source | Included use | Exclusion or comparison boundary |')
$document.Add('|---|---|---|')
$document.Add('| PMID 35142563 / DOI 10.1152/japplphysiol.00662.2021 | Direct Achilles strain for locomotor, heel-rise, hop, and landing conditions | Strain is not force or energy storage; no generic band |')
$document.Add('| PMID 31193251 / DOI 10.1016/j.jshs.2016.12.005 | Patellar tendon stress, stress impulse, and stress rate metric separation | Numeric tables were not transcribed without direct table extraction; no threshold |')
$document.Add('| PMID 21092960 / DOI 10.1016/j.jbiomech.2010.10.028 | Protocol-specific ACL strain during dynamic jump landing | Small sample and close-variant mapping; no landing-wide band |')
$document.Add('| PMID 25059338 / DOI 10.1007/s00167-014-3190-3 | Passive inversion boundary evidence | Not an exercise or landing protocol; excluded from app claims and rubrics |')
$document.Add('| PMID 40705768 / DOI 10.1371/journal.pone.0327973 | High-load squat PFJ, TFJ, and ankle modeled peak contact force | Elite cohort and model cannot be pooled with bodyweight-task models |')
$document.Add('')
$document.Add('## Metric availability')
$document.Add('')
$document.Add("- Metric extraction rows: $($extractions.Count).")
$document.Add("- New multidimensional claim candidates: $($candidates.Count).")
$document.Add("- Source-specific composite rows: $(@($extractions | Where-Object measurementMetric -eq 'SOURCE_DEFINED_COMPOSITE_INDEX').Count); these remain non-rubric and non-profile.")
$document.Add('- PFJ peak, impulse per event, and loading rate are independent rows. Opposite peak/impulse rankings are not treated as contradictions.')
$document.Add('- Achilles and patellar tendon force, stress, strain, impulse, and loading rate remain distinct measurement families.')
$document.Add('- Cumulative session exposure is only defined as a future contract: compatible per-event impulse times a valid event count. Missing count remains missing.')
$document.Add('')
$document.Add('## Research decision matrix')
$document.Add('')
$document.Add('| Tissue | Mechanical mode x temporal metric | Measurement | Included sources | Excluded sources | Decision | Remaining blocker |')
$document.Add('|---|---|---|---|---|---|---|')
foreach ($row in ($decisionSpecs | Sort-Object tissueId,mechanicalLoadMode,temporalMetric,measurementMetric)) {
    $document.Add("| $(MarkdownCell $row.tissueId) | $(MarkdownCell ($row.mechanicalLoadMode + ' x ' + $row.temporalMetric)) | $(MarkdownCell $row.measurementMetric) | $(MarkdownCell $row.includedSourceIds) | $(MarkdownCell $row.excludedSourceIds) | $(MarkdownCell $row.decision) | $(MarkdownCell $row.remainingBlocker) |")
}
$document.Add('')
$document.Add('The tissue_research_decision_c3_v1.csv file is the authoritative row-level record and also preserves each exact search query, search date, reason, and provenance.')
$document.Add('')
$document.Add('## Existing 24-candidate migration')
$document.Add('')
$document.Add('| Old candidate | Old dimension | New identity | Disposition | Preserved measurement | Removed interpretation |')
$document.Add('|---|---|---|---|---|---|')
foreach ($row in ($dispositions | Sort-Object candidateId)) {
    $identity = "$($row.newMechanicalLoadMode) x $($row.newTemporalMetric) / $($row.newMeasurementMetric)"
    $document.Add("| $(MarkdownCell $row.candidateId) | $(MarkdownCell $row.oldDimension) | $(MarkdownCell $identity) | $(MarkdownCell $row.disposition) | $(MarkdownCell $row.preservedScientificPayload) | $(MarkdownCell $row.removedInterpretation) |")
}
$document.Add('')
$document.Add('No previous candidate disappears. The seated-calf generic LOW interpretation remains removed; Achilles peak and loading rate remain separate; the 60-degree squat remains condition-specific; Bulgarian split squat, RFESS, and split squat remain distinct canonical variants.')
$document.Add('')
$document.Add('## Remaining blockers')
$document.Add('')
$document.Add('- No approved external-load dose-response model exists for weighted calf transfer.')
$document.Add('- Quadriceps-tendon exercise mechanics remain materially under-researched in this batch.')
$document.Add('- Patellar tendon dynamic strain and phase-specific eccentric metrics remain blocked.')
$document.Add('- PFJ and TFJ cross-model pooling remains prohibited.')
$document.Add('- ACL/MCL/ankle external proxies remain blocked without validated tissue-specific mappings.')
$document.Add('- Cumulative and cyclic exposure remain blocked when event count, laterality, or per-event exposure is missing.')
$document.Add('- Upper-limb tissues remain a separate future batch.')
$document | Set-Content -LiteralPath $ResearchDocumentPath -Encoding utf8

Write-Output "METRIC_EXTRACTION_COUNT=$($extractions.Count)"
Write-Output "OLD_CANDIDATE_DISPOSITION_COUNT=$($dispositions.Count)"
Write-Output "MULTIDIMENSIONAL_CANDIDATE_COUNT=$($candidates.Count)"
Write-Output "RUBRIC_V2_COUNT=$($rubrics.Count)"
Write-Output "RESEARCH_DECISION_COUNT=$($decisionSpecs.Count)"
Write-Output "EXERCISE_CORRESPONDENCE_COUNT=$(@($correspondences).Count)"
Write-Output "RESEARCH_DOCUMENT=$ResearchDocumentPath"

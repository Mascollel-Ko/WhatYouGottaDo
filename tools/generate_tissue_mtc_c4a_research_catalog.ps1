param(
    [Parameter(Mandatory = $true)][string]$PromptPath,
    [string]$RepoRoot = '',
    [switch]$VerifyPubMed
)

$ErrorActionPreference = 'Stop'
if (!$RepoRoot) { $RepoRoot = Split-Path -Parent $PSScriptRoot }
$assetDir = Join-Path $RepoRoot 'app/src/main/assets/metadata/tissue_load_v1'
$lines = [IO.File]::ReadAllLines($PromptPath, [Text.Encoding]::UTF8)

function Write-Csv([string]$name, [object[]]$rows) {
    [IO.File]::WriteAllLines(
        (Join-Path $assetDir $name),
        @($rows | ConvertTo-Csv -NoTypeInformation),
        [Text.UTF8Encoding]::new($false)
    )
}

function Semantic-Hash([string]$path) {
    $rows = @(Import-Csv $path)
    $header = @($rows[0].PSObject.Properties.Name)
    $encoded = @($rows | ForEach-Object { $row = $_; ($header | ForEach-Object { [string]$row.$_ }) -join [char]31 } | Sort-Object)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($header -join [char]31) + "`n" + ($encoded -join [char]30))) | ForEach-Object ToString x2) -join '' }
    finally { $sha.Dispose() }
}

function Match-Value([string]$text, [string]$pattern) {
    $match = [regex]::Match($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [Text.RegularExpressions.RegexOptions]::Multiline)
    if ($match.Success) { return $match.Groups[1].Value.Trim().TrimEnd('.') }
    return ''
}

function Section-Complex([string]$section) {
    switch ($section) {
        '19.2' { 'KNEE_EXTENSOR_CONTACT_COMPLEX' }
        '19.3' { 'KNEE_MENISCAL_CARTILAGE_COMPLEX' }
        '19.4' { 'CRUCIATE_LIGAMENT_COMPLEX' }
        '19.5' { 'CORONAL_ROTATIONAL_KNEE_COMPLEX' }
        '19.6' { 'POSTERIOR_KNEE_HAMSTRING_COMPLEX' }
        '19.7' { 'ANKLE_PROPULSION_IMPACT_COMPLEX' }
        '19.8' { 'LATERAL_ANKLE_STABILITY_COMPLEX' }
        '19.9' { 'SYNDESMOTIC_ROTATIONAL_COMPLEX' }
        '19.10' { 'MEDIAL_ANKLE_ARCH_COMPLEX' }
        '19.11' { 'LOWER_LIMB_CONTEXT_ONLY' }
        default { '' }
    }
}

function Infer-Complex([string]$section, [string]$text) {
    $explicit = Match-Value $text '(?im)^\s*-\s*Target:\s*([A-Z][A-Z0-9_]+)'
    if ($explicit) { return $explicit }
    $sectionComplex = Section-Complex $section
    if ($sectionComplex) { return $sectionComplex }
    if ($text -match '(?i)patellar|patellofemoral|knee joint contact') { return 'KNEE_EXTENSOR_CONTACT_COMPLEX' }
    if ($text -match '(?i)achilles|ankle|heel') { return 'ANKLE_PROPULSION_IMPACT_COMPLEX' }
    return 'LOWER_LIMB_CROSS_COMPLEX_CALIBRATION'
}

function Infer-Tissues([string]$text) {
    $ids = [Collections.Generic.List[string]]::new()
    $rules = [ordered]@{
        'ACHILLES'='ACHILLES_TENDON'; 'PATELLAR TENDON'='PATELLAR_TENDON'; 'PATELLOFEMORAL'='KNEE_PATELLOFEMORAL'
        'TIBIOFEMORAL'='KNEE_TIBIOFEMORAL'; '\bACL\b'='KNEE_ACL'; '\bPCL\b'='KNEE_PCL'; '\bMCL\b'='KNEE_MCL'
        '\bLCL\b'='KNEE_LCL'; '\bATFL\b'='ATFL'; '\bCFL\b'='CFL'; '\bAITFL\b'='AITFL'; '\bPITFL\b'='PITFL'
        'POSTERIOR TIBIAL'='POSTERIOR_TIBIAL_TENDON'; 'SPRING.LIGAMENT'='SPRING_LIGAMENT_COMPLEX'
    }
    foreach ($entry in $rules.GetEnumerator()) { if ($text -match "(?i)$($entry.Key)") { $ids.Add($entry.Value) } }
    return ($ids | Select-Object -Unique) -join '|'
}

function Infer-Relation([string]$text) {
    if ($text -match '(?i)finite.element|\bFE\b') { return 'FINITE_ELEMENT_MECHANISM' }
    if ($text -match '(?i)cadaver|in vitro') { return 'CADAVERIC_MECHANISM' }
    if ($text -match '(?i)directly measured|in vivo strain|in vivo deformation|ligament strain|tendon strain') { return 'DIRECT_INTERNAL_MEASUREMENT' }
    if ($text -match '(?i)model|predicted|calculated|joint contact force|muscle force') { return 'VALIDATED_INTERNAL_MODEL' }
    return 'CONTEXT_ONLY'
}

function Retry-Json([string]$uri) {
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try { return Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 45 }
        catch { if ($attempt -eq 3) { throw }; Start-Sleep -Seconds (2 * $attempt) }
    }
}

$rawSeeds = [Collections.Generic.List[object]]::new()
$section = ''
for ($index = 0; $index -lt $lines.Count; $index++) {
    if ($lines[$index] -match '^##\s+(19\.\d+)\s+') { $section = $Matches[1]; continue }
    if ($section -and $lines[$index] -match '^(\d+)\.\s+(.+)$' -and [int]$Matches[1] -eq $rawSeeds.Count + 1) {
        $number = [int]$Matches[1]
        if ($number -lt 1 -or $number -gt 130) { continue }
        $title = $Matches[2].Trim()
        $bodyLines = [Collections.Generic.List[string]]::new()
        $cursor = $index + 1
        while ($cursor -lt $lines.Count -and $lines[$cursor] -notmatch "^$($number + 1)\.\s+" -and $lines[$cursor] -notmatch '^##\s+19\.\d+') {
            if ($lines[$cursor] -match '^#\s+20\.') { break }
            if ($lines[$cursor].Trim()) { $bodyLines.Add($lines[$cursor].Trim()) }
            $cursor++
        }
        $body = $bodyLines -join "`n"
        $combined = "$title`n$body"
        $axes = Match-Value $body '(?im)^\s*-\s*Axes:\s*([^\r\n]+)'
        if (!$axes) { $axes = 'M/T/C' }
        $metrics = Match-Value $body '(?im)^\s*-\s*Metrics:\s*([^\r\n]+)'
        if (!$metrics) { $metrics = 'RESEARCH_LEAD_NOT_YET_EXTRACTED' }
        $conditions = Match-Value $body '(?im)^\s*-\s*Conditions?:\s*([^\r\n]+)'
        if (!$conditions) { $conditions = 'NOT_YET_EXTRACTED' }
        $limitations = @($bodyLines | Where-Object { $_ -match '(?i)limitation|verify|discover|do not|not direct|separate' }) -join ' '
        $intendedUse = Match-Value $body '(?im)^\s*-\s*Use:\s*([^\r\n]+)'
        if (!$intendedUse) { $intendedUse = 'LOWER_LIMB_MTC_RESEARCH_TRIAGE' }
        $rawSeeds.Add([pscustomobject][ordered]@{
            seedId=('TISSUE_MTC_SEED_{0:D3}' -f $number); title=$title; authorsOrLeadAuthor=''; year=(Match-Value $body '(?im)^\s*-\s*Year:\s*(\d{4})')
            journal=''; pmid=(Match-Value $body '(?im)^\s*-\s*PMID(?:\s+candidate)?:\s*(\d+)'); pmcid=(Match-Value $body '(?im)^\s*-\s*PMCID(?:\s+candidate)?:\s*(PMC\d+)')
            doi=(Match-Value $body '(?im)^\s*-\s*DOI(?:\s+candidate)?:\s*([^\s]+)'); kciArticleId=(Match-Value $body '(?im)^\s*-\s*KCI(?:\s+candidate)?:\s*(ART\d+)')
            language=$(if ($section -eq '19.11') { 'ko' } else { 'en' }); targetComplexIds=(Infer-Complex $section $combined); targetTissueIds=(Infer-Tissues $combined)
            candidateAxes=($axes -replace '\s','' -replace '/','|'); reportedMetrics=$metrics; exerciseConditions=$conditions
            evidenceRelationCandidate=(Infer-Relation $combined); sourcePriority=$(if ($combined -match '(?i)PMID|PMCID|DOI|KCI') { 'HIGH' } else { 'DISCOVERY' })
            accessStatus=$(if ($combined -match '(?i)PMCID') { 'OPEN_ACCESS_CANDIDATE' } elseif ($combined -match '(?i)PMID') { 'INDEXED_ABSTRACT_CANDIDATE' } elseif ($section -eq '19.11') { 'KCI_INDEX_CANDIDATE' } else { 'IDENTIFIER_NOT_REPORTED' })
            verificationStatus='UNVERIFIED_SEED'; publicationIntegrityStatus='NOT_CHECKED'; intendedUse=$intendedUse
            knownLimitations=$(if ($limitations) { $limitations } else { 'Independent source and protocol verification required before extraction.' })
            notes="Prompt section $section; research lead only; no claim or approval."
        })
        $index = $cursor - 1
    }
}

if ($rawSeeds.Count -ne 130) { throw "Expected 130 research seeds, found $($rawSeeds.Count)." }
$rawSeeds[28].pmid = '8947402'

if ($VerifyPubMed) {
    $pmcidSeeds = @($rawSeeds | Where-Object { $_.pmcid -and !$_.pmid })
    if ($pmcidSeeds) {
        $pmcIds = $pmcidSeeds.pmcid -join ','
        $converted = Retry-Json "https://www.ncbi.nlm.nih.gov/pmc/utils/idconv/v1.0/?ids=$pmcIds&format=json&tool=WhatYouGottaDo"
        foreach ($record in @($converted.records)) {
            foreach ($seed in @($pmcidSeeds | Where-Object pmcid -eq $record.pmcid)) {
                if ($record.pmid) { $seed.pmid = [string]$record.pmid }
                if ($record.doi) { $seed.doi = [string]$record.doi }
            }
        }
    }
    $pmids = @($rawSeeds.pmid | Where-Object { $_ } | Sort-Object -Unique)
    for ($start = 0; $start -lt $pmids.Count; $start += 80) {
        $last = [Math]::Min($start + 79, $pmids.Count - 1)
        $batch = $pmids[$start..$last] -join ','
        $summary = Retry-Json "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&id=$batch&retmode=json"
        foreach ($uid in @($summary.result.uids)) {
            $record = $summary.result.PSObject.Properties[$uid].Value
            $articleIds = @($record.articleids)
            $doi = ($articleIds | Where-Object idtype -eq 'doi' | Select-Object -First 1).value
            $pmcid = ($articleIds | Where-Object idtype -eq 'pmc' | Select-Object -First 1).value
            foreach ($seed in @($rawSeeds | Where-Object pmid -eq $uid)) {
                $seed.title = ([string]$record.title).Trim().TrimEnd('.')
                $seed.authorsOrLeadAuthor = (@($record.authors | ForEach-Object name) -join '|')
                $seed.year = Match-Value ([string]$record.pubdate) '(\d{4})'
                $seed.journal = if ($record.fulljournalname) { [string]$record.fulljournalname } else { [string]$record.source }
                if ($doi) { $seed.doi = [string]$doi }
                if ($pmcid) { $seed.pmcid = [string]$pmcid; $seed.accessStatus = 'PUBMED_AND_PMC_INDEXED' } else { $seed.accessStatus = 'PUBMED_INDEXED' }
                $seed.verificationStatus = if ($uid -in @('35142563','10656979','8947402')) { 'METRIC_EXTRACTED' } else { 'BIBLIOGRAPHICALLY_VERIFIED' }
                $adverse = (@($record.pubtype) -join '|') -match '(?i)retract|expression of concern'
                $seed.publicationIntegrityStatus = if ($adverse) { 'ADVERSE_NOTICE_IN_NCBI_METADATA' } else { 'NO_ADVERSE_NOTICE_IN_NCBI_METADATA_AS_OF_2026_07_15' }
                $seed.notes = "NCBI ESummary bibliographic verification 2026-07-15; research lead only; no claim or approval."
            }
        }
    }
}
Write-Csv 'tissue_mtc_research_seed_catalog_v1.csv' @($rawSeeds)

$extractionPath = Join-Path $assetDir 'tissue_source_metric_extraction_c3_1_v1.csv'
$candidatePath = Join-Path $assetDir 'tissue_evidence_claim_candidates_c3_1_v1.csv'
$c4Extractions = @(Import-Csv $extractionPath | ForEach-Object {
    $source = $_
    $copy = [ordered]@{}
    foreach ($property in $source.PSObject.Properties) {
        $copy[$property.Name] = [string]$property.Value
        if ($property.Name -eq 'testedExerciseConditionId') { $copy['sourceConditionId'] = [string]$property.Value }
    }
    if ($copy.sourceId -eq 'SRC_PMID_35142563' -and $copy.reportedValue) { $copy.temporalMetric = 'EVENT_AVERAGE' }
    if ($copy.metricExtractionId -eq 'C3METRIC_10656979_PCL_FORCE') { $copy.mechanicalLoadMode = 'TENSION' }
    if ($copy.metricExtractionId -eq 'C3METRIC_8947402_TFJ_SHEAR') { $copy.measurementMetric = 'INTERSEGMENTAL_JOINT_FORCE_RESULTANT' }
    [pscustomobject]$copy
})
Write-Csv 'tissue_source_metric_extraction_c4a_v1.csv' $c4Extractions

$extractionById = @{}; $c4Extractions | ForEach-Object { $extractionById[$_.metricExtractionId] = $_ }
$c4Candidates = @(Import-Csv $candidatePath | ForEach-Object {
    $candidate = $_; $extraction = $extractionById[$candidate.metricExtractionId]
    if (!$extraction) { throw "Candidate $($candidate.claimCandidateId) has no extraction." }
    $copy = [ordered]@{}
    foreach ($property in $candidate.PSObject.Properties) {
        $copy[$property.Name] = [string]$property.Value
        if ($property.Name -eq 'metricExtractionId') { $copy['sourceConditionId'] = [string]$extraction.sourceConditionId }
    }
    $direct = @('sourceId','tissueId','mechanicalLoadMode','temporalMetric','measurementMetric','normalizationBasis','externalLoadCondition','relativeLoadCondition','romCondition','velocityCondition','lateralityCondition','surfaceCondition','landingCondition','fatigueCondition','measurementMethod','modelAssumptions','eventContext','movementPhase','positionContext','functionalDemand','tissueResponseMetric','evidenceRelation','proxyMappingId','proxyTargetDimensionId','proxyValidationStatus','proxyValidationSourceIds','proxyLimitations','additionalExternalLoadKg','additionalExternalLoadFractionBw','totalSystemMassKg','totalSystemMassFractionBw','relativeLoadPercent1Rm','externalLoadPlacement','externalLoadDescription','externalLoadRepresentation','loadFieldSource','peakTimingRelativeToContactMs','peakTimingDispersionMs','peakTimingDispersionType','peakTimingReference')
    foreach ($name in $direct) { $copy[$name] = [string]$extraction.$name }
    $valueMap = [ordered]@{ claimValue='reportedValue'; claimLowerBound='reportedLowerBound'; claimUpperBound='reportedUpperBound'; claimDispersionType='reportedDispersionType'; claimDispersionValue='reportedDispersionValue'; claimUnit='reportedUnit' }
    foreach ($entry in $valueMap.GetEnumerator()) { $copy[$entry.Key] = [string]$extraction.($entry.Value) }
    [pscustomobject]$copy
})
Write-Csv 'tissue_evidence_claim_candidates_c4a_v1.csv' $c4Candidates

$bridgeManifest = Import-Csv (Join-Path $assetDir 'tissue_mtc_coefficient_set_manifest_bridge_v1.csv') | Select-Object -First 1
$researchManifest = [pscustomobject][ordered]@{
    coefficientSetId='TISSUE_MTC_C4A_0_1_2'; semanticVersion='0.1.2'; status='DRAFT_NON_PRODUCTION'; effectiveFrom=''; effectiveTo=''; publishedAt=''
    sourceSnapshotHash=Semantic-Hash (Join-Path $assetDir 'tissue_source_metric_extraction_c4a_v1.csv')
    rubricSnapshotHash=$bridgeManifest.rubricSnapshotHash; fallbackPolicyVersion=$bridgeManifest.fallbackPolicyVersion
    exerciseCatalogSnapshotHash=$bridgeManifest.exerciseCatalogSnapshotHash; complexRegistrySnapshotHash=$bridgeManifest.complexRegistrySnapshotHash
    axisRegistrySnapshotHash=$bridgeManifest.axisRegistrySnapshotHash; supersedesCoefficientSetId=$bridgeManifest.coefficientSetId
    changeReason='Corrects C3.1 source-condition, temporal, PCL-force, and tibiofemoral-resultant research semantics without changing operational traces.'
    preparedBy='Codex'; preparedByType='AI_AGENT'
}
Write-Csv 'tissue_mtc_coefficient_set_manifest_research_v1.csv' @($researchManifest)

$conditions = @($c4Extractions | ForEach-Object {
    [pscustomobject][ordered]@{
        sourceConditionId=$_.sourceConditionId; sourceId=$_.sourceId; protocolLabel=$_.testedExerciseConditionId; testedExercise=$_.testedExercise
        candidateStableKeys=$_.appStableKeys; exerciseCorrespondence='SOURCE_PROTOCOL_TO_CANONICAL_STABLE_KEY_CANDIDATE'
        eventContext=$_.eventContext; movementPhase=$_.movementPhase; direction='NOT_REPORTED'; laterality=$_.lateralityCondition
        additionalExternalLoadKg=$_.additionalExternalLoadKg; additionalExternalLoadFractionBw=$_.additionalExternalLoadFractionBw
        totalSystemMassKg=$_.totalSystemMassKg; totalSystemMassFractionBw=$_.totalSystemMassFractionBw; relativeLoadPercent1Rm=$_.relativeLoadPercent1Rm
        externalLoadPlacement=$_.externalLoadPlacement; romCondition=$_.romCondition; jointAngleCondition=$_.positionContext; velocityCondition=$_.velocityCondition
        surfaceCondition=$_.surfaceCondition; landingCondition=$_.landingCondition; reboundOrStick=$(if ($_.landingCondition -match '(?i)stick') {'STICK'} else {'NOT_REPORTED'})
        singleOrRepeated=$(if ($_.testedExercise -match '(?i)single') {'SINGLE'} else {'NOT_REPORTED'}); expectedOrUnexpected='NOT_REPORTED'; fatigueCondition=$_.fatigueCondition
        population=$_.population; healthStatus=$_.healthStatus; trainingStatus=$_.trainingStatus; sexComposition=$_.sexComposition; sampleSize=$_.sampleSize
        measurementMethod=$_.measurementMethod; modelFamily=$_.measurementMetric; notes=$_.extractionLimitations
    }
})
Write-Csv 'tissue_source_condition_registry_v1.csv' $conditions

$complexes = @(Import-Csv (Join-Path $assetDir 'tissue_functional_complex_registry_v1.csv'))
$componentToComplex = @{}
foreach ($complex in $complexes) { foreach ($component in @($complex.componentIds -split '\|')) { $componentToComplex[$component] = $complex.complexId } }
$gapRows = foreach ($complex in $complexes) { foreach ($axis in @('M','T','C')) {
    $seedRows = @($rawSeeds | Where-Object { @($_.targetComplexIds -split '\|') -contains $complex.complexId })
    $verified = @($seedRows | Where-Object verificationStatus -ne 'UNVERIFIED_SEED')
    $metricSeeds = @($seedRows | Where-Object verificationStatus -eq 'METRIC_EXTRACTED')
    $extractionCount = @($c4Extractions | Where-Object { $componentToComplex[$_.tissueId] -eq $complex.complexId }).Count
    [pscustomobject][ordered]@{
        gapId="MTC_GAP_$($complex.complexId)_$axis"; complexId=$complex.complexId; axis=$axis; seedCount=$seedRows.Count
        bibliographicallyVerifiedOrHigherCount=$verified.Count; metricExtractedSeedCount=$metricSeeds.Count; existingExtractionCount=$extractionCount
        gapStatus=$(if ($metricSeeds.Count -gt 0 -or $extractionCount -gt 0) {'PARTIAL_EVIDENCE_REQUIRES_AXIS_REVIEW'} else {'RESEARCH_GAP'})
        limitations='Seed identity or extraction presence does not establish a calibrated M/T/C rubric.'
        nextAction='Independently verify protocols, split exact source conditions, classify evidence relation, and extract compatible axis metrics.'
    }
}}
Write-Csv 'tissue_mtc_research_gap_matrix_v1.csv' @($gapRows)

$verifiedCount = @($rawSeeds | Where-Object verificationStatus -ne 'UNVERIFIED_SEED').Count
Write-Output "Generated 130 seeds ($verifiedCount verified or extracted), $($conditions.Count) source conditions, $($c4Candidates.Count) parity candidates, and $($gapRows.Count) gap rows."

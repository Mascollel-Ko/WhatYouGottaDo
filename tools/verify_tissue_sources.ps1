param(
    [string]$RegistryPath = "app/src/main/assets/metadata/tissue_load_v1/tissue_load_evidence_registry_v1.csv",
    [string]$OutputPath = "",
    [string]$VerifiedAt = "2026-07-14T00:00:00Z"
)

$ErrorActionPreference = "Stop"

function Invoke-WithRetry([scriptblock]$Request) {
    $lastError = $null
    foreach ($attempt in 1..2) {
        try { return & $Request } catch { $lastError = $_; if ($attempt -lt 2) { Start-Sleep -Milliseconds 300 } }
    }
    throw $lastError
}

function Normalize-Text([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return "" }
    $decoded = [Net.WebUtility]::HtmlDecode($Value)
    return (($decoded.ToLowerInvariant() -replace '&', 'and') -replace '[^\p{L}\p{Nd}]', '')
}

function First-Author([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return "" }
    return (($Value -split '[,;|]')[0].Trim() -split '\s+')[0]
}

function Publication-Year([string]$Value) {
    $match = [regex]::Match($Value, '(19|20)\d{2}')
    if ($match.Success) { return $match.Value }
    return ""
}

function Metadata-Hash([string[]]$Values) {
    $payload = ($Values | ForEach-Object { Normalize-Text $_ }) -join '|'
    $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = $sha.ComputeHash($bytes) } finally { $sha.Dispose() }
    return ([BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant()
}

function Values-Match([string[]]$Values) {
    $normalized = @($Values | ForEach-Object { Normalize-Text $_ } | Where-Object { $_ })
    return $normalized.Count -gt 0 -and @($normalized | Select-Object -Unique).Count -eq 1
}

function Journals-Match([string[]]$Values) {
    $normalized = @($Values | ForEach-Object {
        $journal = ([Net.WebUtility]::HtmlDecode($_) -replace '\s*\([^)]*\)\s*$', '') -replace '^\s*the\s+', ''
        Normalize-Text $journal
    } | Where-Object { $_ })
    return $normalized.Count -gt 0 -and @($normalized | Select-Object -Unique).Count -eq 1
}

$registry = @(Import-Csv -LiteralPath $RegistryPath)
$duplicateIdentity = $registry | Group-Object {
    if ($_.pmid) { "PMID:$($_.pmid.Trim())" }
    elseif ($_.doi) { "DOI:$($_.doi.Trim().ToLowerInvariant())" }
    else { "TITLE:$(Normalize-Text $_.title)" }
} | Where-Object Count -gt 1
if ($duplicateIdentity) { throw "Duplicate bibliographic identity exists under multiple source IDs." }

$rows = foreach ($source in ($registry | Sort-Object sourceId)) {
    $ncbi = $null
    $crossref = $null
    $ncbiDoi = ""
    $ncbiPassed = $false
    $crossrefPassed = $false
    $notes = [Collections.Generic.List[string]]::new()

    if ($source.pmid) {
        try {
            $response = Invoke-WithRetry {
                Invoke-RestMethod -Uri "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&id=$($source.pmid)&retmode=json" -Method Get -TimeoutSec 30
            }
            $ncbi = $response.result."$($source.pmid)"
            if ($null -eq $ncbi -or [string]::IsNullOrWhiteSpace($ncbi.title)) { throw "NCBI returned no parsed title." }
            $ncbiDoi = @($ncbi.articleids | Where-Object idtype -eq 'doi' | Select-Object -First 1 -ExpandProperty value)
            $ncbiPassed = $true
        } catch { $notes.Add("NCBI verification failed after bounded retry: $($_.Exception.Message)") }
    }

    $doiToVerify = if ($ncbiDoi) { $ncbiDoi } else { $source.doi }
    if ($doiToVerify) {
        try {
            $escapedDoi = [Uri]::EscapeDataString($doiToVerify)
            $response = Invoke-WithRetry {
                Invoke-RestMethod -Uri "https://api.crossref.org/works/$escapedDoi" -Method Get -Headers @{ 'User-Agent' = 'WhatYouGottaDo-metadata-verifier/1.0' } -TimeoutSec 30
            }
            $crossref = $response.message
            if ($null -eq $crossref -or [string]::IsNullOrWhiteSpace($crossref.DOI) -or @($crossref.title).Count -eq 0) {
                throw "Crossref returned no parsed DOI/title."
            }
            $crossrefPassed = (Normalize-Text $crossref.DOI) -eq (Normalize-Text $doiToVerify)
            if (-not $crossrefPassed) { $notes.Add("Crossref DOI did not match the requested DOI.") }
        } catch { $notes.Add("Crossref verification failed after bounded retry: $($_.Exception.Message)") }
    }

    $identifierConflict = $false
    if ($source.doi -and $ncbiDoi -and (Normalize-Text $source.doi) -ne (Normalize-Text $ncbiDoi)) {
        $identifierConflict = $true
        $notes.Add("Registry DOI conflicts with the DOI parsed from PubMed ArticleId metadata.")
    }
    $identifierStatus = if ($identifierConflict) { 'IDENTIFIER_CONFLICT' }
        elseif ($ncbiPassed -and $crossrefPassed) { 'PMID_AND_DOI_VERIFIED' }
        elseif ($ncbiPassed) { 'PMID_VERIFIED' }
        elseif (-not $source.pmid -and $crossrefPassed) { 'DOI_VERIFIED' }
        else { 'UNVERIFIED' }

    $ncbiTitle = if ($ncbiPassed) { [string]$ncbi.title } else { "" }
    $crossrefTitle = if ($crossrefPassed) { [string]@($crossref.title)[0] } else { "" }
    $ncbiAuthor = if ($ncbiPassed) { [string]@($ncbi.authors)[0].name } else { "" }
    $crossrefAuthor = if ($crossrefPassed) { [string]@($crossref.author)[0].family } else { "" }
    $registryAuthor = First-Author $source.authors
    $ncbiYear = if ($ncbiPassed) { Publication-Year $ncbi.pubdate } else { "" }
    $crossrefYear = if ($crossrefPassed -and $crossref.'published-print') {
        [string]@($crossref.'published-print'.'date-parts')[0][0]
    } elseif ($crossrefPassed) {
        [string]@($crossref.published.'date-parts')[0][0]
    } else { "" }
    $ncbiJournal = if ($ncbiPassed) { [string]$ncbi.fulljournalname } else { "" }
    $crossrefJournal = if ($crossrefPassed) { [string]@($crossref.'container-title')[0] } else { "" }

    $bibliographyMatched = -not $identifierConflict -and
        (Values-Match @($source.title, $ncbiTitle, $crossrefTitle)) -and
        (Values-Match @($registryAuthor, (First-Author $ncbiAuthor), (First-Author $crossrefAuthor))) -and
        (Values-Match @($source.publicationYear, $ncbiYear, $crossrefYear)) -and
        (Journals-Match @($source.journal, $ncbiJournal, $crossrefJournal))
    $bibliographicStatus = if ($bibliographyMatched) { 'MATCHED' }
        elseif ($identifierConflict) { 'MISMATCHED' }
        elseif ($ncbiPassed -or $crossrefPassed) { 'PARTIAL_MATCH' }
        else { 'UNVERIFIED' }
    $capability = if ($ncbiPassed -and ($crossrefPassed -or -not $doiToVerify)) { 'LIVE_SOURCE_VERIFICATION_AVAILABLE' }
        elseif ($ncbiPassed -or $crossrefPassed) { 'PARTIAL_SOURCE_VERIFICATION_AVAILABLE' }
        else { 'LIVE_SOURCE_VERIFICATION_UNAVAILABLE' }

    $resolvedPmid = if ($ncbiPassed) { [string]$source.pmid } else { "" }
    $resolvedDoi = if ($crossrefPassed) { [string]$crossref.DOI } elseif ($ncbiPassed) { [string]$ncbiDoi } else { "" }
    $resolvedTitle = if ($ncbiTitle) { $ncbiTitle } else { $crossrefTitle }
    $resolvedFirstAuthor = if ($ncbiAuthor) { $ncbiAuthor } else { $crossrefAuthor }
    $resolvedYear = if ($ncbiYear) { $ncbiYear } else { $crossrefYear }
    $resolvedJournal = if ($ncbiJournal) { $ncbiJournal } else { $crossrefJournal }
    [pscustomobject][ordered]@{
        sourceId = $source.sourceId
        resolvedPmid = $resolvedPmid
        resolvedDoi = $resolvedDoi
        resolvedTitle = $resolvedTitle
        resolvedFirstAuthor = $resolvedFirstAuthor
        resolvedYear = $resolvedYear
        resolvedJournal = $resolvedJournal
        identifierVerificationStatus = $identifierStatus
        bibliographicMatchStatus = $bibliographicStatus
        publicationIntegrityStatus = $source.publicationIntegrityStatus
        networkCapabilityStatus = $capability
        verifiedAt = $VerifiedAt
        verificationMethod = 'NCBI_ESUMMARY_PARSED_AND_CROSSREF_PARSED_BOUNDED_RETRY'
        metadataSnapshotHash = Metadata-Hash @($resolvedPmid, $resolvedDoi, $resolvedTitle, $resolvedFirstAuthor, $resolvedYear, $resolvedJournal)
        verificationNotes = (($notes + 'Source identity only; publication integrity is maintained by verify_tissue_publication_integrity.ps1.') -join ' ')
    }
}

$rows = @($rows)
$verified = @($rows | Where-Object identifierVerificationStatus -in @('PMID_VERIFIED', 'DOI_VERIFIED', 'PMID_AND_DOI_VERIFIED')).Count
Write-Output "SOURCE_COUNT=$($rows.Count)"
Write-Output "VERIFIED_SOURCE_COUNT=$verified"
if ($OutputPath) {
    $rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding utf8
    Write-Output "VERIFICATION_ARTIFACT=$OutputPath"
}

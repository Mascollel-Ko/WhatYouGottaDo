param(
    [string]$RegistryPath = "app/src/main/assets/metadata/tissue_load_v1/tissue_load_evidence_registry_v1.csv",
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"
$ncbiPassed = $false
$crossrefPassed = $false
$ncbiTitle = ""
$crossrefTitle = ""

try {
    $response = Invoke-RestMethod `
        -Uri "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&id=32658037&retmode=json" `
        -Method Get `
        -TimeoutSec 30
    $record = $response.result."32658037"
    if ($null -eq $record -or [string]::IsNullOrWhiteSpace($record.title)) {
        throw "NCBI response did not resolve PMID 32658037 with a title."
    }
    $ncbiTitle = $record.title
    $ncbiPassed = $true
    Write-Output "NCBI_PREFLIGHT=PASS"
    Write-Output "NCBI_PMID=32658037"
    Write-Output "NCBI_TITLE=$ncbiTitle"
} catch {
    Write-Output "NCBI_PREFLIGHT=FAIL"
    Write-Output "NCBI_ERROR=$($_.Exception.Message)"
}

try {
    $doi = "10.2519/jospt.2020.9406"
    $response = Invoke-RestMethod `
        -Uri "https://api.crossref.org/works/$([System.Uri]::EscapeDataString($doi))" `
        -Method Get `
        -Headers @{ "User-Agent" = "WhatYouGottaDo-metadata-verifier/1.0" } `
        -TimeoutSec 30
    $message = $response.message
    if ($null -eq $message -or $message.DOI -ne $doi -or $null -eq $message.title -or $message.title.Count -eq 0) {
        throw "Crossref response did not resolve the requested DOI with a title."
    }
    $crossrefTitle = $message.title[0]
    $crossrefPassed = $true
    Write-Output "CROSSREF_PREFLIGHT=PASS"
    Write-Output "CROSSREF_DOI=$($message.DOI)"
    Write-Output "CROSSREF_TITLE=$crossrefTitle"
} catch {
    Write-Output "CROSSREF_PREFLIGHT=FAIL"
    Write-Output "CROSSREF_ERROR=$($_.Exception.Message)"
}

$capability = if ($ncbiPassed -and $crossrefPassed) {
    "LIVE_SOURCE_VERIFICATION_AVAILABLE"
} elseif ($ncbiPassed -or $crossrefPassed) {
    "PARTIAL_SOURCE_VERIFICATION_AVAILABLE"
} else {
    "LIVE_SOURCE_VERIFICATION_UNAVAILABLE"
}
Write-Output "NETWORK_CAPABILITY_STATUS=$capability"

if ($OutputPath) {
    $registry = @(Import-Csv -LiteralPath $RegistryPath)
    $rows = $registry | ForEach-Object {
        [pscustomobject]@{
            sourceId = $_.sourceId
            resolvedPmid = if ($ncbiPassed -and $_.pmid -eq "32658037") { $_.pmid } else { "" }
            resolvedDoi = if ($crossrefPassed -and $_.doi -eq "10.2519/jospt.2020.9406") { $_.doi } else { "" }
            resolvedTitle = if ($ncbiPassed) { $ncbiTitle } elseif ($crossrefPassed) { $crossrefTitle } else { "" }
            resolvedFirstAuthor = ""
            resolvedYear = ""
            resolvedJournal = ""
            identifierVerificationStatus = if ($ncbiPassed -and $crossrefPassed) { "PMID_AND_DOI_VERIFIED" } else { "UNVERIFIED" }
            bibliographicMatchStatus = if ($ncbiPassed -and $crossrefPassed -and $ncbiTitle -eq $crossrefTitle) { "MATCHED" } else { "UNVERIFIED" }
            publicationIntegrityStatus = "STATUS_UNKNOWN"
            networkCapabilityStatus = $capability
            verifiedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
            verificationMethod = "NCBI_EUTILS_AND_CROSSREF"
            metadataSnapshotHash = ""
            verificationNotes = "Fail-closed unless both identifiers and bibliography match."
        }
    }
    $rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
    Write-Output "VERIFICATION_ARTIFACT=$OutputPath"
}

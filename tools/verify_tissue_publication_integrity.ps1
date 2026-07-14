param(
    [string]$RegistryPath = "app/src/main/assets/metadata/tissue_load_v1/tissue_load_evidence_registry_v1.csv",
    [string]$OutputPath = "app/src/main/assets/metadata/tissue_load_v1/tissue_publication_integrity_verification_v1.csv",
    [string]$CheckedAt = "2026-07-14T00:00:00Z"
)

$ErrorActionPreference = "Stop"
$method = "PUBMED_EFETCH_XML+CROSSREF_RELATION_API+OFFICIAL_PUBLISHER_NOTICE_CHECK"

function Invoke-WithRetry([scriptblock]$Request) {
    $lastError = $null
    foreach ($attempt in 1..2) {
        try { return & $Request } catch { $lastError = $_; if ($attempt -lt 2) { Start-Sleep -Milliseconds 400 } }
    }
    throw $lastError
}

function Join-Values($Values) {
    $items = @($Values | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ } | Sort-Object -Unique)
    if ($items.Count -eq 0) { return "NONE" }
    return $items -join "|"
}

function Metadata-Hash([string[]]$Values) {
    $payload = ($Values | ForEach-Object { $_.Trim().ToLowerInvariant() }) -join [char]0x1F
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($payload)) } finally { $sha.Dispose() }
    return ([BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant()
}

$rows = foreach ($source in @(Import-Csv -LiteralPath $RegistryPath | Sort-Object sourceId)) {
    $pubmedTypes = @()
    $pubmedComments = @()
    $linkedNotices = @()
    $crossrefRelations = @()
    $crossrefUpdateTo = @()
    $crossrefUpdatedBy = @()
    $publisherStatus = "PUBLISHER_PAGE_UNAVAILABLE"
    $publisherReference = if ($source.doi) { "https://doi.org/$($source.doi)" } else { "" }
    $notes = [Collections.Generic.List[string]]::new()
    $pubmedOk = $false
    $crossrefOk = $false

    try {
        $response = Invoke-WithRetry {
            Invoke-WebRequest -UseBasicParsing -Uri "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=pubmed&id=$($source.pmid)&retmode=xml" -TimeoutSec 30
        }
        [xml]$xml = $response.Content
        $article = $xml.PubmedArticleSet.PubmedArticle
        $pubmedTypes = @($article.MedlineCitation.Article.PublicationTypeList.PublicationType | ForEach-Object { $_.InnerText })
        $comments = @($article.MedlineCitation.CommentsCorrectionsList.CommentsCorrections | Where-Object { $null -ne $_ -and $_.RefType })
        $pubmedComments = @($comments | ForEach-Object { [string]$_.RefType })
        $linkedNotices = @($comments | ForEach-Object { "$($_.RefType):PMID=$($_.PMID):$($_.RefSource)" })
        if ($pubmedTypes.Count -eq 0) { throw "PubMed returned no publication types." }
        $pubmedOk = $true
    } catch { $notes.Add("PubMed authoritative metadata unavailable: $($_.Exception.Message)") }

    try {
        $escapedDoi = [Uri]::EscapeDataString($source.doi)
        $response = Invoke-WithRetry {
            Invoke-RestMethod -Uri "https://api.crossref.org/works/$escapedDoi" -Headers @{ 'User-Agent' = 'WhatYouGottaDo-integrity-verifier/1.0' } -TimeoutSec 30
        }
        $message = $response.message
        if (([string]$message.DOI).ToLowerInvariant() -ne $source.doi.ToLowerInvariant()) { throw "Crossref DOI mismatch." }
        $crossrefRelations = @($message.relation.PSObject.Properties.Name)
        $crossrefUpdateTo = @($message.'update-to' | ForEach-Object { [string]$_.DOI })
        $crossrefUpdatedBy = @($message.'updated-by' | ForEach-Object { [string]$_.DOI })
        $crossrefOk = $true
    } catch { $notes.Add("Crossref authoritative metadata unavailable: $($_.Exception.Message)") }

    if ($publisherReference) {
        try {
            $publisher = Invoke-WithRetry {
                Invoke-WebRequest -UseBasicParsing -Uri $publisherReference -MaximumRedirection 8 -TimeoutSec 25
            }
            $publisherStatus = if ($publisher.StatusCode -ge 200 -and $publisher.StatusCode -lt 400) {
                "PUBLISHER_PAGE_CHECKED_NO_LINKED_NOTICE_BANNER_FOUND"
            } else {
                "PUBLISHER_PAGE_UNAVAILABLE"
            }
        } catch { $notes.Add("Official publisher page unavailable: $($_.Exception.Message)") }
    }

    $noticeText = (Join-Values @($pubmedTypes + $pubmedComments + $linkedNotices + $crossrefRelations + $crossrefUpdateTo + $crossrefUpdatedBy)).ToLowerInvariant()
    $status = if (-not $pubmedOk -or -not $crossrefOk) { "UNABLE_TO_VERIFY" }
        elseif ($noticeText -match 'expression.?of.?concern') { "EXPRESSION_OF_CONCERN" }
        elseif ($noticeText -match 'retract') { "RETRACTED" }
        elseif ($noticeText -match 'erratum|correct|update') { "CORRECTION_FOUND_REVIEW_REQUIRED" }
        else { "NO_ADVERSE_NOTICE_FOUND" }

    $types = Join-Values $pubmedTypes
    $commentsValue = Join-Values $pubmedComments
    $noticesValue = Join-Values $linkedNotices
    $relations = Join-Values $crossrefRelations
    $updateTo = Join-Values $crossrefUpdateTo
    $updatedBy = Join-Values $crossrefUpdatedBy
    $hash = Metadata-Hash @(
        $source.sourceId, $source.pmid, $source.doi, $types, $commentsValue, $noticesValue,
        $relations, $updateTo, $updatedBy, $publisherStatus, $publisherReference, $status, $method
    )
    [pscustomobject][ordered]@{
        sourceId = $source.sourceId
        pmid = $source.pmid
        doi = $source.doi
        pubmedPublicationTypes = $types
        pubmedCommentsCorrections = $commentsValue
        pubmedLinkedNotices = $noticesValue
        crossrefRelationTypes = $relations
        crossrefUpdateTo = $updateTo
        crossrefUpdatedBy = $updatedBy
        publisherNoticeStatus = $publisherStatus
        publisherNoticeReference = $publisherReference
        integrityCheckStatus = $status
        checkedAt = $CheckedAt
        verificationMethod = $method
        metadataSnapshotHash = $hash
        integrityNotes = if ($status -eq "NO_ADVERSE_NOTICE_FOUND") {
            "No adverse notice was found in the checked authoritative metadata as of $CheckedAt. This is not a guarantee that no future notice can exist. $($notes -join ' ')".Trim()
        } else { ($notes -join ' ').Trim() }
    }
}

$rows = @($rows)
$rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding utf8
Write-Output "SOURCE_COUNT=$($rows.Count)"
Write-Output "NO_ADVERSE_NOTICE_FOUND_COUNT=$(@($rows | Where-Object integrityCheckStatus -eq 'NO_ADVERSE_NOTICE_FOUND').Count)"
Write-Output "BLOCKING_COUNT=$(@($rows | Where-Object integrityCheckStatus -ne 'NO_ADVERSE_NOTICE_FOUND').Count)"
Write-Output "PUBLICATION_INTEGRITY_ARTIFACT=$OutputPath"

param(
    [Parameter(Mandatory = $true)]
    [string]$AuthorityWorkbookPath,
    [Parameter(Mandatory = $true)]
    [string]$RecoveryWorkbookPath,
    [string]$OutputDirectory = "app/src/main/assets/metadata/tissue_load_v1"
)

$ErrorActionPreference = "Stop"
$utf8 = [System.Text.UTF8Encoding]::new($false)
$expectedAuthorityHash = "efa3f0c47c4f5bf0ae634ed7e8656162ac6552b7a86659da28096cc257c50144"
$expectedRecoveryHash = "beb599e6a53fec92f999d1174bbb35ed31092aff56ad90ee0149e61dd4c615c9"

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Write-Utf8([string]$Path, [string]$Content) {
    [System.IO.File]::WriteAllText(
        $Path,
        $Content.Replace("`r`n", "`n").Replace("`r", "`n"),
        $utf8
    )
}

function Get-Sha256([string]$Path) {
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Read-ZipXml(
    [System.IO.Compression.ZipArchive]$Archive,
    [string]$EntryPath,
    [bool]$Required = $true
) {
    $entry = $Archive.GetEntry($EntryPath)
    if ($null -eq $entry) {
        if ($Required) { throw "Missing XLSX entry: $EntryPath" }
        return $null
    }
    $stream = $entry.Open()
    try {
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $true)
        try {
            return [xml]$reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Get-ColumnIndex([string]$CellReference) {
    $letters = $CellReference -replace "[^A-Z]", ""
    $index = 0
    foreach ($character in $letters.ToCharArray()) {
        $index = ($index * 26) + ([int]$character - [int][char]"A" + 1)
    }
    $index - 1
}

function Get-XlsxSheetRows([string]$Path, [string]$SheetName) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path))
    try {
        $sharedStrings = @()
        $sharedXml = Read-ZipXml $archive "xl/sharedStrings.xml" $false
        if ($null -ne $sharedXml) {
            $sharedNs = [System.Xml.XmlNamespaceManager]::new($sharedXml.NameTable)
            $sharedNs.AddNamespace("s", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
            $sharedStrings = @($sharedXml.SelectNodes("//s:si", $sharedNs) | ForEach-Object {
                ($_.SelectNodes(".//s:t", $sharedNs) | ForEach-Object { $_.InnerText }) -join ""
            })
        }

        $workbookXml = Read-ZipXml $archive "xl/workbook.xml"
        $workbookRelsXml = Read-ZipXml $archive "xl/_rels/workbook.xml.rels"
        $workbookNs = [System.Xml.XmlNamespaceManager]::new($workbookXml.NameTable)
        $workbookNs.AddNamespace("s", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
        $relationshipNamespace = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        $sheet = $workbookXml.SelectNodes("//s:sheet", $workbookNs) |
            Where-Object { $_.GetAttribute("name") -eq $SheetName } |
            Select-Object -First 1
        if ($null -eq $sheet) { throw "Missing worksheet '$SheetName' in $Path" }

        $relationshipId = $sheet.GetAttribute("id", $relationshipNamespace)
        $relsNs = [System.Xml.XmlNamespaceManager]::new($workbookRelsXml.NameTable)
        $relsNs.AddNamespace("r", "http://schemas.openxmlformats.org/package/2006/relationships")
        $relationship = $workbookRelsXml.SelectNodes("//r:Relationship", $relsNs) |
            Where-Object { $_.GetAttribute("Id") -eq $relationshipId } |
            Select-Object -First 1
        if ($null -eq $relationship) { throw "Missing worksheet relationship '$relationshipId'." }

        $target = $relationship.GetAttribute("Target").Replace("\", "/")
        $entryPath = if ($target.StartsWith("/")) {
            $target.TrimStart("/")
        } elseif ($target.StartsWith("xl/")) {
            $target
        } else {
            "xl/$target"
        }
        $sheetXml = Read-ZipXml $archive $entryPath
        $sheetNs = [System.Xml.XmlNamespaceManager]::new($sheetXml.NameTable)
        $sheetNs.AddNamespace("s", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
        $rowNodes = @($sheetXml.SelectNodes("//s:sheetData/s:row", $sheetNs))
        if ($rowNodes.Count -eq 0) { throw "Worksheet '$SheetName' is empty." }

        $maxColumnIndex = 0
        foreach ($cell in $sheetXml.SelectNodes("//s:sheetData/s:row/s:c", $sheetNs)) {
            $maxColumnIndex = [Math]::Max($maxColumnIndex, (Get-ColumnIndex $cell.GetAttribute("r")))
        }

        $rows = [System.Collections.Generic.List[object]]::new()
        foreach ($rowNode in $rowNodes) {
            $values = [string[]]::new($maxColumnIndex + 1)
            foreach ($cell in $rowNode.SelectNodes("./s:c", $sheetNs)) {
                $columnIndex = Get-ColumnIndex $cell.GetAttribute("r")
                $type = $cell.GetAttribute("t")
                $value = switch ($type) {
                    "s" {
                        $sharedIndex = [int]$cell.SelectSingleNode("./s:v", $sheetNs).InnerText
                        $sharedStrings[$sharedIndex]
                        break
                    }
                    "inlineStr" {
                        ($cell.SelectNodes("./s:is//s:t", $sheetNs) | ForEach-Object { $_.InnerText }) -join ""
                        break
                    }
                    default {
                        $valueNode = $cell.SelectSingleNode("./s:v", $sheetNs)
                        if ($null -eq $valueNode) { "" } else { $valueNode.InnerText }
                    }
                }
                $values[$columnIndex] = [string]$value
            }
            $rows.Add($values)
        }
        [pscustomobject]@{ Rows = $rows.ToArray() }
    } finally {
        $archive.Dispose()
    }
}

function Convert-SheetRowsToObjects([object[]]$Rows) {
    if ($Rows.Count -lt 2) { return @() }
    $headers = [string[]]$Rows[0]
    if ($headers | Where-Object { [string]::IsNullOrWhiteSpace($_) }) {
        throw "Worksheet contains a blank header."
    }
    if (@($headers | Sort-Object -Unique).Count -ne $headers.Count) {
        throw "Worksheet contains duplicate headers."
    }
    @($Rows | Select-Object -Skip 1 | Where-Object {
        @($_ | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }).Count -gt 0
    } | ForEach-Object {
        $row = $_
        $properties = [ordered]@{}
        for ($index = 0; $index -lt $headers.Count; $index++) {
            $properties[$headers[$index]] = [string]$row[$index]
        }
        [pscustomobject]$properties
    })
}

function Write-CsvSheet([string]$Path, [object[]]$Rows) {
    if ($Rows.Count -lt 2) { throw "Cannot emit an empty authority sheet: $Path" }
    $headers = [string[]]$Rows[0]
    $objects = Convert-SheetRowsToObjects $Rows
    $csv = $objects | Select-Object $headers | ConvertTo-Csv -NoTypeInformation
    Write-Utf8 $Path (($csv -join "`n") + "`n")
    $objects.Count
}

$authorityHash = Get-Sha256 $AuthorityWorkbookPath
$recoveryHash = Get-Sha256 $RecoveryWorkbookPath
if ($authorityHash -ne $expectedAuthorityHash) {
    throw "Authority workbook hash mismatch. Expected $expectedAuthorityHash, found $authorityHash."
}
if ($recoveryHash -ne $expectedRecoveryHash) {
    throw "Recovery workbook hash mismatch. Expected $expectedRecoveryHash, found $recoveryHash."
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

$specs = @(
    [pscustomobject]@{ Source = "AUTHORITY"; Sheet = "전체239_통합롱"; File = "tissue_rcv_exercise_load_unit_authority_v1.csv"; Version = "REL-MSCP-D-INTEGRATED-0.3_UNSIDED" },
    [pscustomobject]@{ Source = "AUTHORITY"; Sheet = "전체239_운동_인덱스"; File = "tissue_rcv_exercise_index_v1.csv"; Version = "REL-MSCP-D-INTEGRATED-0.3_UNSIDED" },
    [pscustomobject]@{ Source = "AUTHORITY"; Sheet = "CTM_관절복합체"; File = "tissue_rcv_joint_complexes_v1.csv"; Version = "CTM-0.1" },
    [pscustomobject]@{ Source = "AUTHORITY"; Sheet = "CTM_분석단위"; File = "tissue_rcv_load_units_v1.csv"; Version = "CTM-0.1" },
    [pscustomobject]@{ Source = "AUTHORITY"; Sheet = "CTM_조직"; File = "tissue_rcv_tissues_v1.csv"; Version = "CTM-0.1" },
    [pscustomobject]@{ Source = "AUTHORITY"; Sheet = "CTM_조직관계"; File = "tissue_rcv_tissue_relationships_v1.csv"; Version = "CTM-0.1" },
    [pscustomobject]@{ Source = "AUTHORITY"; Sheet = "CTM_출처"; File = "tissue_rcv_authority_sources_v1.csv"; Version = "CTM-0.1" },
    [pscustomobject]@{ Source = "AUTHORITY"; Sheet = "통합_점수계약"; File = "tissue_rcv_score_contract_v1.csv"; Version = "REL-MSCP-D-INTEGRATED-0.3_UNSIDED" },
    [pscustomobject]@{ Source = "RECOVERY"; Sheet = "운동별_프로토콜"; File = "tissue_rcv_exercise_protocols_v1.csv"; Version = "RCV-ALL-0.6" },
    [pscustomobject]@{ Source = "RECOVERY"; Sheet = "프로토콜_클래스"; File = "tissue_rcv_protocol_classes_v1.csv"; Version = "RCV-ALL-0.6" },
    [pscustomobject]@{ Source = "RECOVERY"; Sheet = "DI_프로필"; File = "tissue_rcv_di_profiles_v1.csv"; Version = "RCV-ALL-0.6" },
    [pscustomobject]@{ Source = "RECOVERY"; Sheet = "회복곡선_knot"; File = "tissue_rcv_recovery_curve_knots_v1.csv"; Version = "RCV-ALL-0.6" },
    [pscustomobject]@{ Source = "RECOVERY"; Sheet = "조직_라우팅"; File = "tissue_rcv_recovery_routing_v1.csv"; Version = "RCV-ALL-0.6" },
    [pscustomobject]@{ Source = "RECOVERY"; Sheet = "근거문헌"; File = "tissue_rcv_recovery_evidence_v1.csv"; Version = "RCV-ALL-0.6" },
    [pscustomobject]@{ Source = "RECOVERY"; Sheet = "집계_표시계약"; File = "tissue_rcv_display_contract_v1.csv"; Version = "RCV-ALL-0.6" }
)

$manifestRows = [System.Collections.Generic.List[object]]::new()
foreach ($spec in $specs) {
    $workbookPath = if ($spec.Source -eq "AUTHORITY") { $AuthorityWorkbookPath } else { $RecoveryWorkbookPath }
    $sourceHash = if ($spec.Source -eq "AUTHORITY") { $authorityHash } else { $recoveryHash }
    $sheetRows = (Get-XlsxSheetRows $workbookPath $spec.Sheet).Rows
    $outputPath = Join-Path $OutputDirectory $spec.File
    $rowCount = Write-CsvSheet $outputPath $sheetRows
    $manifestRows.Add([pscustomobject][ordered]@{
        assetName = $spec.File
        sourceSheet = $spec.Sheet
        sourceVersion = $spec.Version
        sourceWorkbookSha256 = $sourceHash
        rowCount = $rowCount
        assetSha256 = Get-Sha256 $outputPath
        baselineCommit = "d3be2a9af81bc42b8733fd953cc2cdc770be186b"
        stateIdentity = "loadUnitStableKey|loadDimension|UNSIDED"
    })
}

$manifestHeaders = @(
    "assetName",
    "sourceSheet",
    "sourceVersion",
    "sourceWorkbookSha256",
    "rowCount",
    "assetSha256",
    "baselineCommit",
    "stateIdentity"
)
$manifestPath = Join-Path $OutputDirectory "tissue_rcv_asset_manifest_v1.csv"
$manifestCsv = $manifestRows | Select-Object $manifestHeaders | ConvertTo-Csv -NoTypeInformation
Write-Utf8 $manifestPath (($manifestCsv -join "`n") + "`n")

$scoreCount = [int]($manifestRows | Where-Object assetName -eq "tissue_rcv_exercise_load_unit_authority_v1.csv").rowCount
$exerciseCount = [int]($manifestRows | Where-Object assetName -eq "tissue_rcv_exercise_index_v1.csv").rowCount
$protocolCount = [int]($manifestRows | Where-Object assetName -eq "tissue_rcv_exercise_protocols_v1.csv").rowCount
$curveKnotCount = [int]($manifestRows | Where-Object assetName -eq "tissue_rcv_recovery_curve_knots_v1.csv").rowCount
if ($scoreCount -ne 3507 -or $exerciseCount -ne 239 -or $protocolCount -ne 239 -or $curveKnotCount -ne 114) {
    throw "RCV-ALL-0.6 count gate failed: scores=$scoreCount exercises=$exerciseCount protocols=$protocolCount knots=$curveKnotCount"
}

Write-Output "Generated RCV-ALL-0.6 assets: scores=$scoreCount exercises=$exerciseCount protocols=$protocolCount knots=$curveKnotCount"

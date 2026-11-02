<#
.SYNOPSIS
  Measure rendered frame times for a manual fling session on a connected Android phone.
.DESCRIPTION
  No application records, health data or backups are read or modified.
  Requires Android platform-tools adb; Windows PowerShell 5.1+.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools/perf/measure_scroll_frames.ps1 -Label record-debug
#>
param(
    [string]$Package = "com.whatyougottado.app",
    [string]$Label = "record"
)

$ErrorActionPreference = "Stop"
$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCommand) {
    $adb = $adbCommand.Source
} else {
    $adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (-not (Test-Path $adb)) {
        throw "adb was not found. Install Android SDK platform-tools or add adb to PATH."
    }
}

$devices = & $adb devices
if ($LASTEXITCODE -ne 0 -or (($devices | Select-String "device$").Count -ne 1)) {
    throw "Connect exactly one Android device with USB debugging enabled."
}

$packageList = & $adb shell pm path $Package
if ($LASTEXITCODE -ne 0 -or -not ($packageList -match "package:")) {
    throw "Package $Package is not installed."
}

$refresh = (& $adb shell settings get system peak_refresh_rate | Out-String).Trim()
Write-Host "Package: $Package; configured peak refresh rate: $refresh"
Write-Host "Navigate to the same Record or Plan screen and keep it open."
& $adb shell dumpsys gfxinfo $Package reset | Out-Null
Read-Host "Fling up and down 10-15 times. Press Enter when finished"

$raw = (& $adb shell dumpsys gfxinfo $Package framestats | Out-String)
if ($LASTEXITCODE -ne 0) { throw "adb gfxinfo failed" }
$folder = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Path $folder -Force | Out-Null
$safeLabel = $Label -replace "[^a-zA-Z0-9_-]", "_"
$file = Join-Path $folder ("scroll-" + $safeLabel + "-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".txt")
$raw | Out-File -FilePath $file -Encoding utf8

$frames = New-Object System.Collections.Generic.List[double]
foreach ($line in ($raw -split "`r?`n")) {
    if (-not ($line -match "^\s*0,\d+,\d+,")) { continue }
    $cells = $line.Trim().Split(",")
    if ($cells.Count -lt 14) { continue }
    $start = [long]0
    $finish = [long]0
    if (-not [long]::TryParse($cells[1], [ref]$start)) { continue }
    if (-not [long]::TryParse($cells[13], [ref]$finish)) { continue }
    $duration = ($finish - $start) / 1000000.0
    if ($duration -gt 0 -and $duration -lt 5000) { $frames.Add($duration) }
}
if ($frames.Count -eq 0) {
    Write-Host "No complete frame statistics were reported. Raw output: $file"
    exit 0
}
$values = @($frames | Sort-Object)
function Percentile([double]$p) {
    $index = [Math]::Min($values.Count - 1, [Math]::Max(0, [int][Math]::Ceiling($values.Count * $p) - 1))
    return [Math]::Round($values[$index], 1)
}
$over8 = @($values | Where-Object { $_ -gt 8.33 }).Count
$over16 = @($values | Where-Object { $_ -gt 16.67 }).Count
Write-Host "Frames=$($values.Count); p50=$(Percentile 0.5) ms; p90=$(Percentile 0.9) ms; p95=$(Percentile 0.95) ms; p99=$(Percentile 0.99) ms"
Write-Host "Frames over 8.33 ms: $over8; over 16.67 ms: $over16"
Write-Host "The 8.33ms target corresponds to 120Hz; 16.67ms to 60Hz. Values are diagnostics, not a direct skipped-frame rate."
Write-Host "Raw diagnostics: $file"

param(
    [string]$EpgToolPath = "",
    [string]$Revision = "a96fc01e5c93cb57abc229d77e83e240dcdd20dc",
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$toolPath = if ($EpgToolPath) {
    [System.IO.Path]::GetFullPath($EpgToolPath)
} else {
    Join-Path $projectRoot "work\iptv-org-epg"
}
$channelsPath = Join-Path $projectRoot "epg\sky-uk.channels.xml"
$assetDirectory = Join-Path $projectRoot "app\src\main\assets"
$outputPath = Join-Path $assetDirectory "sky-guide.xml"

if (-not (Test-Path -LiteralPath (Join-Path $toolPath "package.json"))) {
    New-Item -ItemType Directory -Path $toolPath -Force | Out-Null
    git -C $toolPath init
    git -C $toolPath remote add origin https://github.com/iptv-org/epg.git
    git -C $toolPath fetch --depth 1 origin $Revision
    git -C $toolPath checkout --detach FETCH_HEAD
}

if (-not $SkipInstall -or -not (Test-Path -LiteralPath (Join-Path $toolPath "node_modules"))) {
    npm --prefix $toolPath ci
}

New-Item -ItemType Directory -Path $assetDirectory -Force | Out-Null
Push-Location $toolPath
try {
    npm run grab --- --channels=$channelsPath --output=$outputPath --days=2 --maxConnections=4
} finally {
    Pop-Location
}

Write-Output "Updated Android asset: $outputPath"

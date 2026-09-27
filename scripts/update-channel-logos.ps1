param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing.Common -ErrorAction SilentlyContinue

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$catalogPath = Join-Path $projectRoot "app\src\main\java\io\github\lozza\tellygrid\data\ChannelLogoCatalog.kt"
$outputDirectory = Join-Path $projectRoot "app\src\main\assets\channel-logos"
$catalog = Get-Content -Raw -LiteralPath $catalogPath
$matches = [regex]::Matches($catalog, '"([^"]+)"\s+to\s+"(https://[^"]+)"')

New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

foreach ($match in $matches) {
    $channelId = $match.Groups[1].Value
    $sourceUrl = $match.Groups[2].Value
    $outputPath = Join-Path $outputDirectory "$channelId.png"
    if ((Test-Path -LiteralPath $outputPath) -and -not $Force) {
        continue
    }

    $temporaryPath = Join-Path ([System.IO.Path]::GetTempPath()) "tellygrid-logo-$([guid]::NewGuid().ToString('N')).img"
    try {
        $downloaded = $false
        foreach ($attempt in 1..4) {
            try {
                Invoke-WebRequest `
                    -Uri $sourceUrl `
                    -OutFile $temporaryPath `
                    -TimeoutSec 20 `
                    -Headers @{ "User-Agent" = "TellyGrid-logo-bundler/1.0"; "Accept" = "image/*" }
                $downloaded = $true
                break
            } catch {
                if ($attempt -eq 4) { throw }
                Start-Sleep -Milliseconds (500 * $attempt)
            }
        }
        if (-not $downloaded) { throw "Logo download failed: $channelId" }

        $sourceImage = [System.Drawing.Image]::FromFile($temporaryPath)
        try {
            $canvasWidth = 256
            $canvasHeight = 128
            $padding = 8
            $availableWidth = $canvasWidth - (2 * $padding)
            $availableHeight = $canvasHeight - (2 * $padding)
            $scale = [Math]::Min($availableWidth / $sourceImage.Width, $availableHeight / $sourceImage.Height)
            $drawWidth = [Math]::Max(1, [int][Math]::Round($sourceImage.Width * $scale))
            $drawHeight = [Math]::Max(1, [int][Math]::Round($sourceImage.Height * $scale))
            $drawX = [int](($canvasWidth - $drawWidth) / 2)
            $drawY = [int](($canvasHeight - $drawHeight) / 2)

            $canvas = [System.Drawing.Bitmap]::new(
                $canvasWidth,
                $canvasHeight,
                [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
            )
            try {
                $graphics = [System.Drawing.Graphics]::FromImage($canvas)
                try {
                    $graphics.Clear([System.Drawing.Color]::Transparent)
                    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
                    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                    $graphics.DrawImage($sourceImage, $drawX, $drawY, $drawWidth, $drawHeight)
                } finally {
                    $graphics.Dispose()
                }
                $canvas.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
            } finally {
                $canvas.Dispose()
            }
        } finally {
            $sourceImage.Dispose()
        }
        Write-Output "Bundled $channelId"
    } finally {
        Remove-Item -LiteralPath $temporaryPath -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Milliseconds 180
}

Write-Output "Bundled $($matches.Count) channel logos in $outputDirectory"

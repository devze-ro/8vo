param(
  [string]$BookPath = "local\slice1_host_smoke\eightvo_slice1.epub",
  [string]$OutDir = "local\slice1_visual_smoke"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Exe = Join-Path $Root "build\win32\8vo.exe"
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing eightvo executable: $Exe"
}

$ResolvedBook = if ([System.IO.Path]::IsPathRooted($BookPath)) {
  (Resolve-Path -LiteralPath $BookPath).Path
} else {
  (Resolve-Path -LiteralPath (Join-Path $Root $BookPath)).Path
}
$Out = Join-Path $Root $OutDir
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$Bmp = Join-Path $Out "eightvo_visual.bmp"

Add-Type -AssemblyName System.Drawing

function Invoke-EightvoRender {
  $output = & $Exe --render-smoke $ResolvedBook $Bmp 2>&1
  if ($LASTEXITCODE -ne 0) {
    $output | Write-Host
    throw "eightvo visual smoke failed with exit code $LASTEXITCODE"
  }
  $line = $output | Where-Object { $_ -match '^eightvo_visual_smoke result=pass ' } |
    Select-Object -Last 1
  if (!$line -or !(Test-Path -LiteralPath $Bmp -PathType Leaf)) {
    $output | Write-Host
    throw "eightvo visual smoke did not produce evidence"
  }
  if ($line -notmatch ' hash=([0-9a-fA-F]{16}) ') {
    $line | Write-Host
    throw "eightvo visual smoke did not report a pixel hash"
  }
  $pixelHash = $Matches[1].ToLowerInvariant()
  if ($line -notmatch ' presentation=([0-9a-fA-F]{16}) ' -or
      $Matches[1] -eq '0000000000000000') {
    $line | Write-Host
    throw "eightvo visual smoke did not report Presentation Engine geometry"
  }
  $presentationHash = $Matches[1].ToLowerInvariant()

  $image = [System.Drawing.Image]::FromFile($Bmp)
  try {
    if ($image.Width -ne 1100 -or $image.Height -ne 760) {
      throw "eightvo visual smoke dimensions are invalid: $($image.Width)x$($image.Height)"
    }
  } finally {
    $image.Dispose()
  }

  [pscustomobject]@{
    Line = [string]$line
    PixelHash = $pixelHash
    PresentationHash = $presentationHash
    FileHash = (Get-FileHash -LiteralPath $Bmp -Algorithm SHA256).Hash
  }
}

$first = Invoke-EightvoRender
$second = Invoke-EightvoRender
if ($first.PixelHash -ne $second.PixelHash -or
    $first.PresentationHash -ne $second.PresentationHash -or
    $first.FileHash -ne $second.FileHash) {
  throw "eightvo visual smoke is not repeatable: $($first.PixelHash)/$($second.PixelHash)"
}

Write-Host $second.Line
Write-Host "win32_eightvo_visual_smoke result=pass repeat=2 hash=$($second.PixelHash) bmp=$Bmp"

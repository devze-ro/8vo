param(
  [string]$OutDir = "local\validation\reader-selection-menu-slice3",
  [string]$BookPath = "C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub",
  [string]$ExePath = "build\win32\lectern0.exe",
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ExpectedBookSha256 =
  "D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9"
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
if (!(Test-Path -LiteralPath $BookPath -PathType Leaf)) {
  throw "missing GOTM EPUB: $BookPath"
}
$Book = (Resolve-Path -LiteralPath $BookPath).Path
$BookHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Book).Hash
if ($BookHash -ne $ExpectedBookSha256) {
  throw "GOTM EPUB hash mismatch: expected=$ExpectedBookSha256 actual=$BookHash path=$Book"
}
$Exe = if ([System.IO.Path]::IsPathRooted($ExePath)) {
  $ExePath
} else { Join-Path $Root $ExePath }
$Out = if ([System.IO.Path]::IsPathRooted($OutDir)) {
  $OutDir
} else { Join-Path $Root $OutDir }
New-Item -ItemType Directory -Force -Path $Out | Out-Null

if (!$SkipBuild) {
  Push-Location $Root
  try {
    & cmd /c build\win32_build.bat no_run
    if ($LASTEXITCODE -ne 0) {
      throw "strict Lectern0 build failed with exit code $LASTEXITCODE"
    }
  } finally { Pop-Location }
}
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing Lectern0 executable: $Exe"
}

function Invoke-SelectionMenuRun {
  param([string]$Name)
  $Prefix = Join-Path $Out "gotm_selection_$Name"
  $Log = Join-Path $Out "$Name.log"
  & $Exe --reader-view-selection-menu-smoke $Book $Prefix *> $Log
  if ($LASTEXITCODE -ne 0) {
    $Tail = (Get-Content -LiteralPath $Log -Tail 50) -join "`n"
    throw "Lectern0 selection-menu recovery smoke failed: $Name`n$Tail"
  }
  $PassLine = Get-Content -LiteralPath $Log | Where-Object {
    $_ -match '^lectern0_reader_view_selection_menu result=pass '
  } | Select-Object -Last 1
  if (!$PassLine) { throw "Lectern0 selection-menu pass line is missing: $Name" }
  $Match = [regex]::Match(
    $PassLine,
    '^lectern0_reader_view_selection_menu result=pass checkpoint=6 rows=(\d+),(\d+) range=(\d+)\.\.(\d+) geometry=glyph_stops release=popup_safe menu=compact_clamped mouse=set_pink keyboard=remove_pink escape=concrete_selection output=(.+)$')
  if (!$Match.Success -or
      [UInt64]$Match.Groups[4].Value -le [UInt64]$Match.Groups[3].Value) {
    throw "Lectern0 selection-menu result is incomplete: $PassLine"
  }
  $Hashes = [ordered]@{}
  foreach ($EvidenceName in @("multiline_light", "selected_dark_focus")) {
    $Bmp = "${Prefix}_${EvidenceName}.bmp"
    if (!(Test-Path -LiteralPath $Bmp -PathType Leaf) -or
        (Get-Item -LiteralPath $Bmp).Length -le 54) {
      throw "missing rendered Lectern0 evidence: $Bmp"
    }
    $Hashes[$EvidenceName] =
      (Get-FileHash -Algorithm SHA256 -LiteralPath $Bmp).Hash
  }
  [pscustomobject]@{
    Name = $Name
    Prefix = $Prefix
    Log = $Log
    PassLine = [string]$PassLine
    Match = $Match
    Hashes = $Hashes
  }
}

$First = Invoke-SelectionMenuRun "first"
$Second = Invoke-SelectionMenuRun "second"
foreach ($Name in $Second.Hashes.Keys) {
  if ($First.Hashes[$Name] -ne $Second.Hashes[$Name]) {
    throw "rendered selection-menu evidence is not deterministic: $Name"
  }
}

$BmpPaths = [ordered]@{
  multiline_light = "$($Second.Prefix)_multiline_light.bmp"
  selected_dark_focus = "$($Second.Prefix)_selected_dark_focus.bmp"
}
$BmpHashes = $Second.Hashes
$PngPaths = [ordered]@{}
Add-Type -AssemblyName System.Drawing
foreach ($Name in $BmpPaths.Keys) {
  $Bmp = $BmpPaths[$Name]
  if (!(Test-Path -LiteralPath $Bmp -PathType Leaf) -or
      (Get-Item -LiteralPath $Bmp).Length -le 54) {
    throw "missing rendered Lectern0 evidence: $Bmp"
  }
  $Png = Join-Path $Out "$Name.png"
  $Image = [System.Drawing.Image]::FromFile($Bmp)
  try { $Image.Save($Png, [System.Drawing.Imaging.ImageFormat]::Png) }
  finally { $Image.Dispose() }
  $PngPaths[$Name] = $Png
}

$ReaderViewCommitPath =
  Join-Path $Root "vendor\readerview0_dependency\COMMIT"
$ReaderViewCommit =
  (Get-Content -Raw -LiteralPath $ReaderViewCommitPath).Trim()
$Summary = [pscustomobject]@{
  generated_at = (Get-Date).ToString("o")
  status = "pass"
  git_head = (& git -C $Root rev-parse HEAD).Trim()
  git_status = @(& git -C $Root status --porcelain --untracked-files=all)
  readerview0_commit = $ReaderViewCommit
  executable = @{
    path = $Exe
    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $Exe).Hash
  }
  book = @{
    path = $Book
    sha256 = $BookHash
    size = (Get-Item -LiteralPath $Book).Length
  }
  viewport = @{ width = 1100; height = 760 }
  repeat = 2
  rows = @([int]$Second.Match.Groups[1].Value,
           [int]$Second.Match.Groups[2].Value)
  selection = @{
    start_byte = [UInt64]$Second.Match.Groups[3].Value
    end_byte = [UInt64]$Second.Match.Groups[4].Value
  }
  coverage = @(
    "pointer press-drag-release",
    "two-row glyph-stop selection rectangles",
    "selection-release popup suppression",
    "compact viewport-clamped menu",
    "pointer highlight activation",
    "keyboard selected-swatch removal",
    "Escape concrete-selection cleanup"
  )
  result_line = $Second.PassLine
  bmp_sha256 = $BmpHashes
  screenshot = $PngPaths
  log = $Second.Log
}
$SummaryPath = Join-Path $Out "summary.json"
$Summary | ConvertTo-Json -Depth 8 |
  Set-Content -Encoding ASCII -LiteralPath $SummaryPath

Write-Host $Second.PassLine
Write-Host "win32_lectern0_selection_menu_recovery_smoke result=pass repeat=2 summary=$SummaryPath"

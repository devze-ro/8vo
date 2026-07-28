param(
  [string]$OutDir = "local\validation\rv-focus1a",
  [string]$BookPath = "C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub",
  [string]$ExePath = "build\win32\8vo.exe",
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ExpectedBookSha256 =
  "D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9"
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$Exe = if ([System.IO.Path]::IsPathRooted($ExePath)) {
  $ExePath
} else {
  Join-Path $Root $ExePath
}
$Book = (Resolve-Path -LiteralPath $BookPath).Path
$BookHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Book).Hash
if ($BookHash -ne $ExpectedBookSha256) {
  throw "GOTM EPUB hash mismatch: expected=$ExpectedBookSha256 actual=$BookHash path=$Book"
}

if (!$SkipBuild) {
  Push-Location $Root
  try {
    & cmd /c build\win32_build.bat no_run
    if ($LASTEXITCODE -ne 0) {
      throw "strict Octavo build failed with exit code $LASTEXITCODE"
    }
  } finally {
    Pop-Location
  }
}
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing Octavo executable: $Exe"
}

$Out = if ([System.IO.Path]::IsPathRooted($OutDir)) {
  $OutDir
} else {
  Join-Path $Root $OutDir
}
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$Names = @(
  "bookmark_before", "bookmark_right", "bookmark_left",
  "note_before", "note_right", "note_left",
  "font_before", "font_right", "font_left",
  "find_input_left"
)

function Invoke-ExactRoutingRun {
  param([string]$RunName)
  $Prefix = Join-Path $Out $RunName
  $Output = & $Exe --reader-view-post-action-arrow-smoke $Book $Prefix 2>&1
  if ($LASTEXITCODE -ne 0) {
    $Output | Write-Host
    throw "Octavo exact-book routing run failed: $RunName"
  }
  $Line = $Output | Where-Object {
    $_ -match '^octavo_reader_view_post_action_arrow result=pass '
  } | Select-Object -Last 1
  if (!$Line -or $Line -notmatch 'checkpoint=7 ' -or
      $Line -notmatch 'find=ParaXn ') {
    $Output | Write-Host
    throw "Octavo exact-book routing evidence is incomplete: $RunName"
  }
  $Hashes = [ordered]@{}
  foreach ($Name in $Names) {
    $Path = "${Prefix}_${Name}.bmp"
    if (!(Test-Path -LiteralPath $Path -PathType Leaf) -or
        (Get-Item -LiteralPath $Path).Length -le 54) {
      throw "missing rendered routing evidence: $Path"
    }
    $Hashes[$Name] = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash
  }
  [pscustomobject]@{
    name = $RunName
    prefix = $Prefix
    line = [string]$Line
    hashes = $Hashes
  }
}

$First = Invoke-ExactRoutingRun "first"
$Second = Invoke-ExactRoutingRun "second"
foreach ($Name in $Names) {
  if ($First.hashes[$Name] -ne $Second.hashes[$Name]) {
    throw "rendered routing evidence is not repeatable: $Name"
  }
}

Add-Type -AssemblyName System.Drawing
$Pngs = [ordered]@{}
foreach ($Name in $Names) {
  $Bmp = "$($Second.prefix)_${Name}.bmp"
  $Png = Join-Path $Out "${Name}.png"
  $Image = [System.Drawing.Image]::FromFile($Bmp)
  try {
    $Image.Save($Png, [System.Drawing.Imaging.ImageFormat]::Png)
  } finally {
    $Image.Dispose()
  }
  $Pngs[$Name] = $Png
}

$Summary = [pscustomobject]@{
  generated_at = (Get-Date).ToString("o")
  status = "pass"
  git_head = (& git -C $Root rev-parse HEAD).Trim()
  git_status = @(& git -C $Root status --porcelain --untracked-files=all)
  executable = @{
    path = $Exe
    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $Exe).Hash
  }
  book = @{
    path = $Book
    sha256 = $BookHash
    size = (Get-Item -LiteralPath $Book).Length
  }
  viewport = @{ width = 1400; height = 780 }
  theme = "light"
  typography = @{ text_size_index = 3; line_spacing_index = 0; font_family = "Georgia" }
  repeat = 2
  result_line = $Second.line
  bmp_sha256 = $Second.hashes
  png = $Pngs
}
$SummaryPath = Join-Path $Out "summary.json"
$Summary | ConvertTo-Json -Depth 8 |
  Set-Content -Encoding ASCII -LiteralPath $SummaryPath

Write-Host $Second.line
Write-Host "win32_octavo_post_action_arrow_routing_smoke result=pass repeat=2 summary=$SummaryPath"

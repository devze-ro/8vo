param(
  [string]$OutDir = "local\validation\publisher-typography-spacing-slice4",
  [string]$BookPath = "C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub",
  [string]$ExePath = "build\win32\8vo.exe",
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
      throw "strict Eightvo build failed with exit code $LASTEXITCODE"
    }
  } finally { Pop-Location }
}
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing Eightvo executable: $Exe"
}

$Prefix = Join-Path $Out "gotm"
$Log = Join-Path $Out "run.log"
& $Exe --publisher-typography-spacing-smoke $Book $Prefix *> $Log
if ($LASTEXITCODE -ne 0) {
  $Tail = (Get-Content -LiteralPath $Log -Tail 80) -join "`n"
  throw "Eightvo publisher typography/spacing smoke failed`n$Tail"
}
$PassLine = Get-Content -LiteralPath $Log | Where-Object {
  $_ -match '^eightvo_publisher_typography_spacing result=pass '
} | Select-Object -Last 1
if (!$PassLine) { throw "publisher typography/spacing pass line is missing" }
foreach ($Token in @(
  "book=gotm_new", "options=3", "action=select_setting",
  "font_override=explicit", "embedded_fonts=disabled",
  "italics=3", "justification=3", "navigation=persistent",
  "restart=persistent", "legacy_v2=override")) {
  if ($PassLine -notmatch [regex]::Escape($Token)) {
    throw "publisher typography/spacing result is missing ${Token}: $PassLine"
  }
}

$LineMatch = [regex]::Match($PassLine, ' line_heights=(\d+),(\d+),(\d+)')
$MarginUnitMatch = [regex]::Match($PassLine, ' margin_units=(\d+),(\d+),(\d+)')
$PublisherMarginMatch = [regex]::Match($PassLine, ' publisher_margin=(\d+),(\d+),(\d+)')
$FamilyMatch = [regex]::Match(
  $PassLine,
  ' family_available=(\d+) family_gaps=(-?\d+),(-?\d+),(-?\d+),(-?\d+),(-?\d+) family_line_heights=(\d+),(\d+),(\d+),(\d+),(\d+) family_rows=(\d+),(\d+),(\d+),(\d+),(\d+)')
$RangeMatch = [regex]::Match(
  $PassLine,
  ' family_ranges=(\d+)\.\.(\d+),(\d+)\.\.(\d+),(\d+)\.\.(\d+),(\d+)\.\.(\d+),(\d+)\.\.(\d+)')
$ContentMatch = [regex]::Match(
  $PassLine, ' parity_content=(-?\d+),(-?\d+),(\d+),(\d+)')
$HashMatch = [regex]::Match(
  $PassLine, ' hashes=([0-9a-f]+),([0-9a-f]+),([0-9a-f]+) output=(.+)$')
if (!$LineMatch.Success -or !$MarginUnitMatch.Success -or
    !$PublisherMarginMatch.Success -or !$FamilyMatch.Success -or
    !$RangeMatch.Success -or !$ContentMatch.Success -or !$HashMatch.Success) {
  throw "publisher typography/spacing result is incomplete: $PassLine"
}
$LineHeights = @(
  [int]$LineMatch.Groups[1].Value,
  [int]$LineMatch.Groups[2].Value,
  [int]$LineMatch.Groups[3].Value
)
$MarginUnits = @(1..3 | ForEach-Object { [int]$MarginUnitMatch.Groups[$_].Value })
$PublisherMargins = @(1..3 | ForEach-Object { [int]$PublisherMarginMatch.Groups[$_].Value })
$FamilyAvailable = [int]$FamilyMatch.Groups[1].Value
$FamilyGaps = @(2..6 | ForEach-Object { [int]$FamilyMatch.Groups[$_].Value })
$FamilyLineHeights = @(7..11 | ForEach-Object { [int]$FamilyMatch.Groups[$_].Value })
$FamilyRows = @(12..16 | ForEach-Object { [int]$FamilyMatch.Groups[$_].Value })
$FamilyRanges = @()
for ($Index = 0; $Index -lt 5; $Index += 1) {
  $FamilyRanges += [pscustomobject]@{
    start=[long]$RangeMatch.Groups[1 + $Index * 2].Value
    end=[long]$RangeMatch.Groups[2 + $Index * 2].Value
  }
}
$ParityContent = [pscustomobject]@{
  x=[int]$ContentMatch.Groups[1].Value
  y=[int]$ContentMatch.Groups[2].Value
  width=[int]$ContentMatch.Groups[3].Value
  height=[int]$ContentMatch.Groups[4].Value
}
$Hashes = @(1..3 | ForEach-Object { $HashMatch.Groups[$_].Value })
if ($LineHeights[1] -ne $LineHeights[0] + 5 -or
    $LineHeights[2] -ne $LineHeights[1] + 5 -or
    $MarginUnits[0] -ne 1000 -or
    $MarginUnits[1] -ge $MarginUnits[0] -or
    $MarginUnits[2] -ge $MarginUnits[1] -or
    ($PublisherMargins | Select-Object -Unique).Count -ne 1 -or
    $FamilyAvailable -lt 3 -or
    $FamilyLineHeights[2] -ne 31 -or $FamilyRows[2] -ne 18 -or
    $FamilyRanges[2].start -ne 0 -or $FamilyRanges[2].end -ne 873 -or
    $ParityContent.x -ne 490 -or $ParityContent.y -ne 124 -or
    $ParityContent.width -ne 556 -or $ParityContent.height -ne 682) {
  throw "unexpected spacing geometry: $($LineHeights -join ',')"
}

Add-Type -AssemblyName System.Drawing
$Evidence = @()
for ($Index = 0; $Index -lt 3; $Index += 1) {
  $Bmp = "${Prefix}_spacing_${Index}.bmp"
  $Png = "${Prefix}_spacing_${Index}.png"
  if (!(Test-Path -LiteralPath $Bmp -PathType Leaf) -or
      (Get-Item -LiteralPath $Bmp).Length -le 54) {
    throw "missing rendered spacing evidence: $Bmp"
  }
  $Image = [System.Drawing.Image]::FromFile($Bmp)
  try { $Image.Save($Png, [System.Drawing.Imaging.ImageFormat]::Png) }
  finally { $Image.Dispose() }
  $Evidence += [pscustomobject]@{
    index=$Index
    line_height=$LineHeights[$Index]
    presentation_hash=$Hashes[$Index]
    bmp=$Bmp
    bmp_sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Bmp).Hash
    png=$Png
    png_sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Png).Hash
  }
}
$FamilyEvidence = @()
for ($Index = 0; $Index -lt 5; $Index += 1) {
  if ($FamilyGaps[$Index] -lt 0) { continue }
  $Bmp = "${Prefix}_family_${Index}.bmp"
  $Png = "${Prefix}_family_${Index}.png"
  if (!(Test-Path -LiteralPath $Bmp -PathType Leaf) -or
      (Get-Item -LiteralPath $Bmp).Length -le 54) {
    throw "missing rendered family evidence: $Bmp"
  }
  $Image = [System.Drawing.Image]::FromFile($Bmp)
  try { $Image.Save($Png, [System.Drawing.Imaging.ImageFormat]::Png) }
  finally { $Image.Dispose() }
  $FamilyEvidence += [pscustomobject]@{
    family_index=$Index
    line_height=$FamilyLineHeights[$Index]
    row_count=$FamilyRows[$Index]
    bottom_gap=$FamilyGaps[$Index]
    page_range=$FamilyRanges[$Index]
    bmp=$Bmp
    bmp_sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Bmp).Hash
    png=$Png
    png_sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Png).Hash
  }
}

$Dependencies = @{}
foreach ($Name in @("reader0", "ui0", "readerview0", "ground0")) {
  $CommitPath = Join-Path $Root "vendor\${Name}_dependency\COMMIT"
  $Dependencies[$Name] = (Get-Content -Raw -LiteralPath $CommitPath).Trim()
}
$Summary = [pscustomobject]@{
  generated_at=(Get-Date).ToString("o")
  status="pass"
  git_head=(& git -C $Root rev-parse HEAD).Trim()
  git_status=@(& git -C $Root status --porcelain --untracked-files=all)
  dependencies=$Dependencies
  executable=@{
    path=$Exe
    sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Exe).Hash
  }
  book=@{
    path=$Book
    sha256=$BookHash
    size=(Get-Item -LiteralPath $Book).Length
  }
  viewport=@{ width=1536; height=912 }
  theme="light"
  font_family="Georgia"
  text_size_index=0
  line_spacing_options=@("Compact", "Comfortable", "Spacious")
  line_heights=$LineHeights
  margin_units=$MarginUnits
  publisher_margin_pixels=$PublisherMargins
  font_family_user_override=$true
  embedded_fonts_enabled=$false
  parity_content=$ParityContent
  family_evidence=$FamilyEvidence
  action="ReaderViewAction_SelectSetting"
  navigation_persistence=$true
  restart_persistence=$true
  italic_fragment_rendered=$true
  justified_rows_rendered=$true
  evidence=$Evidence
  log=$Log
}
$SummaryPath = Join-Path $Out "summary.json"
$Summary | ConvertTo-Json -Depth 8 |
  Set-Content -Encoding ASCII -LiteralPath $SummaryPath
Write-Host "win32_eightvo_publisher_typography_spacing_smoke result=pass summary=$SummaryPath"

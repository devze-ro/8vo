param(
  [string]$OutDir = "local\validation\reader-image-fit-slice6",
  [Parameter(Mandatory = $true)][string]$BookPath,
  [string]$ExePath = "build\win32\8vo.exe",
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ExpectedBookSha256 =
  "D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9"
$ExpectedBookSize = 955125
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
if (!(Test-Path -LiteralPath $BookPath -PathType Leaf)) {
  throw "missing GOTM EPUB: $BookPath"
}
$Book = (Resolve-Path -LiteralPath $BookPath).Path
$BookItem = Get-Item -LiteralPath $Book
$BookHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Book).Hash
if ($BookHash -ne $ExpectedBookSha256 -or $BookItem.Length -ne $ExpectedBookSize) {
  throw "GOTM EPUB identity mismatch: expected=$ExpectedBookSha256/$ExpectedBookSize actual=$BookHash/$($BookItem.Length) path=$Book"
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
      throw "strict Octavo build failed with exit code $LASTEXITCODE"
    }
  } finally { Pop-Location }
}
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing Octavo executable: $Exe"
}

function Invoke-ImageFitSmoke {
  param([string]$Prefix, [string]$Log)
  & $Exe --reader-image-fit-smoke $Book $Prefix *> $Log
  if ($LASTEXITCODE -ne 0) {
    $Tail = (Get-Content -LiteralPath $Log -Tail 80) -join "`n"
    throw "Octavo reader image-fit smoke failed`n$Tail"
  }
  $PassLine = Get-Content -LiteralPath $Log | Where-Object {
    $_ -match '^octavo_reader_image_fit result=pass '
  } | Select-Object -Last 1
  $PassPattern =
    '^octavo_reader_image_fit result=pass book=gotm_new cases=4 viewport=1182x713 image_only=4 canonical_units=reader0 cap320=absent sampling=area_prepared hashes=([0-9a-f]{16}),([0-9a-f]{16}),([0-9a-f]{16}),([0-9a-f]{16}) output=(.+)$'
  $PassMatch = [regex]::Match([string]$PassLine, $PassPattern)
  if (!$PassLine -or !$PassMatch.Success) {
    throw "reader image-fit result is incomplete: $PassLine"
  }
  $CaseLines = @(Get-Content -LiteralPath $Log | Where-Object {
    $_ -match '^octavo_reader_image_fit_case result=pass '
  } | ForEach-Object { [string]$_ })
  if ($CaseLines.Count -ne 4) {
    throw "expected four passing image-fit cases, got $($CaseLines.Count)"
  }
  [pscustomobject]@{
    PassLine = [string]$PassLine
    CaseLines = $CaseLines
    Hashes = @(
      $PassMatch.Groups[1].Value,
      $PassMatch.Groups[2].Value,
      $PassMatch.Groups[3].Value,
      $PassMatch.Groups[4].Value
    )
  }
}

$CaseNames = @("cover", "maps_1", "maps_2", "maps_3")
$Prefix = Join-Path $Out "gotm"
$First = Invoke-ImageFitSmoke $Prefix (Join-Path $Out "run_1.log")
$FirstFiles = @{}
foreach ($CaseName in $CaseNames) {
  $Bmp = "${Prefix}_${CaseName}.bmp"
  if (!(Test-Path -LiteralPath $Bmp -PathType Leaf) -or
      (Get-Item -LiteralPath $Bmp).Length -le 54) {
    throw "missing rendered image-fit evidence: $Bmp"
  }
  $FirstFiles[$CaseName] = (Get-FileHash -Algorithm SHA256 -LiteralPath $Bmp).Hash
}
$Second = Invoke-ImageFitSmoke $Prefix (Join-Path $Out "run_2.log")
if ($First.PassLine -ne $Second.PassLine -or
    ($First.CaseLines -join "`n") -ne ($Second.CaseLines -join "`n")) {
  throw "reader image-fit result is not repeatable"
}

Add-Type -AssemblyName System.Drawing
$Evidence = @()
for ($Index = 0; $Index -lt $CaseNames.Count; $Index += 1) {
  $CaseName = $CaseNames[$Index]
  $Bmp = "${Prefix}_${CaseName}.bmp"
  $BmpHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Bmp).Hash
  if ($BmpHash -ne $FirstFiles[$CaseName]) {
    throw "reader image-fit BMP is not repeatable: $CaseName"
  }
  $Png = "${Prefix}_${CaseName}.png"
  $Image = [System.Drawing.Image]::FromFile($Bmp)
  try { $Image.Save($Png, [System.Drawing.Imaging.ImageFormat]::Png) }
  finally { $Image.Dispose() }
  $Evidence += [pscustomobject]@{
    case=$CaseName
    case_result=$Second.CaseLines[$Index]
    pixel_hash=$Second.Hashes[$Index]
    bmp=$Bmp
    bmp_sha256=$BmpHash
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
    size=$BookItem.Length
  }
  viewport=@{ width=1182; height=713 }
  theme="dark"
  font_family="Georgia"
  text_size_index=3
  line_spacing="Compact"
  ownership=@{
    classification="reader0"
    canonical_visual_units="reader0"
    media_geometry="octavo_host_presentation"
    aspect_fit="octavo_host_rendering"
    sampling="ground0_area_prepared"
  }
  result=$Second.PassLine
  repeat=2
  evidence=$Evidence
}
$SummaryPath = Join-Path $Out "summary.json"
$Summary | ConvertTo-Json -Depth 8 |
  Set-Content -Encoding ASCII -LiteralPath $SummaryPath
Write-Host "win32_octavo_reader_image_fit_smoke result=pass summary=$SummaryPath"

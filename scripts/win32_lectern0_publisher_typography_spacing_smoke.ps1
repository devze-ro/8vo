param(
  [string]$OutDir = "local\validation\publisher-typography-spacing-slice4",
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

$Prefix = Join-Path $Out "gotm"
$Log = Join-Path $Out "run.log"
& $Exe --publisher-typography-spacing-smoke $Book $Prefix *> $Log
if ($LASTEXITCODE -ne 0) {
  $Tail = (Get-Content -LiteralPath $Log -Tail 80) -join "`n"
  throw "Lectern0 publisher typography/spacing smoke failed`n$Tail"
}
$PassLine = Get-Content -LiteralPath $Log | Where-Object {
  $_ -match '^lectern0_publisher_typography_spacing result=pass '
} | Select-Object -Last 1
if (!$PassLine) { throw "publisher typography/spacing pass line is missing" }
$Match = [regex]::Match(
  $PassLine,
  '^lectern0_publisher_typography_spacing result=pass book=gotm_new options=3 action=select_setting italics=3 justification=3 line_heights=(\d+),(\d+),(\d+) navigation=persistent restart=persistent hashes=([0-9a-f]+),([0-9a-f]+),([0-9a-f]+) output=(.+)$')
if (!$Match.Success) {
  throw "publisher typography/spacing result is incomplete: $PassLine"
}

$LineHeights = @(
  [int]$Match.Groups[1].Value,
  [int]$Match.Groups[2].Value,
  [int]$Match.Groups[3].Value
)
if ($LineHeights[1] -ne $LineHeights[0] + 5 -or
    $LineHeights[2] -ne $LineHeights[1] + 5) {
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
    presentation_hash=$Match.Groups[4 + $Index].Value
    bmp=$Bmp
    bmp_sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Bmp).Hash
    png=$Png
    png_sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Png).Hash
  }
}

$Dependencies = @{}
foreach ($Name in @("reader0", "ui0", "readerview0", "zero_foundation")) {
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
Write-Host "win32_lectern0_publisher_typography_spacing_smoke result=pass summary=$SummaryPath"

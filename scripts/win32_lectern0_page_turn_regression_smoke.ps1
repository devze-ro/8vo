param(
  [string]$OutDir = "C:\Temp\lectern0_page_turn_regression",
  [string]$BookPath = "C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub",
  [string]$ExePath = "build\win32\lectern0.exe",
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ExpectedBookSha256 =
  "D5365766478A7D853821299B72432D15583F8DD10F94C2C2CF20D52E783E77F9"
$ExpectedBookSize = 955125
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$Book = (Resolve-Path -LiteralPath $BookPath).Path
$BookItem = Get-Item -LiteralPath $Book
$BookHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Book).Hash
if ($BookHash -ne $ExpectedBookSha256 -or
    $BookItem.Length -ne $ExpectedBookSize) {
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
      throw "strict Lectern0 build failed with exit code $LASTEXITCODE"
    }
  } finally { Pop-Location }
}
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing Lectern0 executable: $Exe"
}

function Invoke-PageTurnSmoke {
  param([string]$Name)
  $RunDir = Join-Path $Out $Name
  New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
  $Prefix = Join-Path $RunDir "page_turn"
  $Log = Join-Path $RunDir "run.log"
  & $Exe --page-turn-regression-smoke $Book $Prefix *> $Log
  if ($LASTEXITCODE -ne 0) {
    $Tail = (Get-Content -LiteralPath $Log -Tail 100) -join "`n"
    throw "Lectern0 page-turn regression smoke failed`n$Tail"
  }
  $PassLine = Get-Content -LiteralPath $Log | Where-Object {
    $_ -match '^lectern0_page_turn_regression_smoke result=pass '
  } | Select-Object -Last 1
  foreach ($Token in @(
    "forward=64", "backward=64", "pixel_exact=16/16",
    "warmed_cache_hits=16/16",
    "repeat=initial24_interval3_coalesced", "repeat_moves=2",
    "draw_overflow=0", "raster_overflow=0", "run_overflow=0")) {
    if (!$PassLine -or $PassLine -notmatch [regex]::Escape($Token)) {
      throw "page-turn result is incomplete: missing $Token in $PassLine"
    }
  }
  $WarmMatch = [regex]::Match($PassLine, 'warmed_render_avg_ms=([0-9.]+)')
  $WarmMaxMatch = [regex]::Match(
    $PassLine, 'warmed_render_max_ms=([0-9.]+)')
  $ColdMatch = [regex]::Match($PassLine, 'cold_render_avg_ms=([0-9.]+)')
  $PreparedMoveMatch = [regex]::Match(
    $PassLine, 'prepared_move_max_ms=([0-9.]+)')
  if (!$WarmMatch.Success -or !$WarmMaxMatch.Success -or
      !$ColdMatch.Success -or
      !$PreparedMoveMatch.Success) {
    throw "page-turn performance fields are missing: $PassLine"
  }
  $PreparedMatch = [regex]::Match(
    $PassLine, 'prepared_warm_pages=(\d+)')
  if (!$PreparedMatch.Success -or
      [int]$PreparedMatch.Groups[1].Value -lt 16) {
    throw "bounded forward warming evidence is missing: $PassLine"
  }
  $WarmMs = [double]::Parse(
    $WarmMatch.Groups[1].Value,
    [Globalization.CultureInfo]::InvariantCulture)
  $ColdMs = [double]::Parse(
    $ColdMatch.Groups[1].Value,
    [Globalization.CultureInfo]::InvariantCulture)
  $PreparedMoveMaxMs = [double]::Parse(
    $PreparedMoveMatch.Groups[1].Value,
    [Globalization.CultureInfo]::InvariantCulture)
  if ($WarmMs -ge $ColdMs) {
    throw "prepared page render did not improve navigation: warm=$WarmMs cold=$ColdMs"
  }
  $WarmMaxMs = [double]::Parse(
    $WarmMaxMatch.Groups[1].Value,
    [Globalization.CultureInfo]::InvariantCulture)
  if ($WarmMaxMs -ge 16.667 -or $PreparedMoveMaxMs -ge 16.667) {
    throw "prepared page turn exceeded the 60 FPS budget: move_max=$PreparedMoveMaxMs render_max=$WarmMaxMs"
  }
  [pscustomobject]@{
    pass_line=[string]$PassLine
    prepared_move_max_ms=$PreparedMoveMaxMs
    warmed_render_avg_ms=$WarmMs
    warmed_render_max_ms=$WarmMaxMs
    cold_render_avg_ms=$ColdMs
    prepared_warm_pages=[int]$PreparedMatch.Groups[1].Value
    log=$Log
  }
}

$First = Invoke-PageTurnSmoke "run_1"
$Second = Invoke-PageTurnSmoke "run_2"
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
  book=@{ path=$Book; sha256=$BookHash; size=$BookItem.Length }
  repeat=2
  runs=@($First, $Second)
}
$SummaryPath = Join-Path $Out "summary.json"
$Summary | ConvertTo-Json -Depth 7 |
  Set-Content -Encoding ASCII -LiteralPath $SummaryPath
Write-Host "win32_lectern0_page_turn_regression_smoke result=pass repeat=2 summary=$SummaryPath"

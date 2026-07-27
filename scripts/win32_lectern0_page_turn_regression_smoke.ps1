param(
  [string]$OutDir = "C:\Temp\lectern0_page_turn_regression",
  [string]$BookPath = "C:\Users\ankur\workspace\projects\devze-ro\gotm_new.epub",
  [string]$ExePath = "build\win32\8vo.exe",
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
if (Test-Path -LiteralPath $Out) {
  throw "refusing to reuse an existing evidence directory: $Out"
}
$InitialGitStatus = @(& git -C $Root status --porcelain --untracked-files=all)
if ($InitialGitStatus.Count -ne 0) {
  throw "final evidence requires a clean Lectern0 worktree: $($InitialGitStatus -join '; ')"
}
New-Item -ItemType Directory -Path $Out | Out-Null

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

$QueueOut = Join-Path $Out "win32_queue"
$QueueScript = Join-Path $Root "scripts\win32_lectern0_page_repeat_queue_smoke.ps1"
& $QueueScript -OutDir $QueueOut -BookPath $Book -ExePath $Exe -SkipBuild
if ($LASTEXITCODE -ne 0) {
  throw "Win32 page-repeat queue regression failed with exit code $LASTEXITCODE"
}
$QueueSummary = Get-Content -Raw -LiteralPath (Join-Path $QueueOut "summary.json") |
  ConvertFrom-Json

function Invoke-PageTurnSmoke {
  param([string]$Name)
  $RunDir = Join-Path $Out $Name
  New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
  $Prefix = Join-Path $RunDir "page_turn"
  $Log = Join-Path $RunDir "run.log"
  $NativeExitCode = 0
  $PreviousErrorActionPreference = $ErrorActionPreference
  try {
    # Preserve strict process-exit checking under Windows PowerShell, where
    # expected native stderr diagnostics otherwise become a terminating
    # NativeCommandError under the script-wide Stop policy.
    $ErrorActionPreference = "Continue"
    & $Exe --page-turn-regression-smoke $Book $Prefix *> $Log
    $NativeExitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $PreviousErrorActionPreference
  }
  if ($NativeExitCode -ne 0) {
    $Tail = (Get-Content -LiteralPath $Log -Tail 100) -join "`n"
    throw "Lectern0 page-turn regression smoke failed`n$Tail"
  }
  $PassLine = Get-Content -LiteralPath $Log | Where-Object {
    $_ -match '^lectern0_page_turn_regression_smoke result=pass '
  } | Select-Object -Last 1
  foreach ($Token in @(
    "forward=64", "backward=64", "direct_traversal=64+64_exact",
    "forward_endpoint_valid=1/1", "reverse_range_exact=64/64",
    "returned_to_start=1/1",
    "canonical_nonempty_frames=128/128",
    "zero_pages_or_frames=0/0", "orphan_text_pages=0/0",
    "invalid_word_start_pages=0/0",
    "boundary_oracle=raw_spine_utf8_word_start",
    "gotm_prose_scope=active_spine_text_ge_128",
    "boundary_oracle_self_test=1/1", "row_coverage=128/128",
    "gotm_minimum_text_bytes", "gotm_minimum_text_rows",
    "deferred_reversal_keyup=1/1", "pixel_exact=16/16",
    "warmed_cache_hits=16/16",
    "repeat=wall_clock24_interval3_coalesced_no_catch_up", "repeat_moves=4",
    "held_repeat=action_first_render_gated_no_speculative",
    "held_viewport=1917x1137",
    "held_forward=2",
    "held_backward=2", "held_cache_hits=0", "held_pixel_exact=2/2",
    "held_native_repeats_coalesced=2/2", "held_render_gate_blocks=2/2",
    "held_warm_on_action=0", "held_warm_steps=0",
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
  $HeldMoveMatch = [regex]::Match(
    $PassLine, 'held_move_max_ms=([0-9.]+)')
  $HeldRenderMatch = [regex]::Match(
    $PassLine, 'held_render_max_ms=([0-9.]+)')
  $HeldActionTotalMatch = [regex]::Match(
    $PassLine, 'held_action_total_max_ms=([0-9.]+)')
  $HeldWarmMatch = [regex]::Match(
    $PassLine, 'held_warm_steps=(\d+)')
  $HeldWarmMaxMatch = [regex]::Match(
    $PassLine, 'held_warm_max_ms=([0-9.]+)')
  $MinimumTextBytesMatch = [regex]::Match(
    $PassLine, 'gotm_minimum_text_bytes=(\d+)')
  $MinimumTextRowsMatch = [regex]::Match(
    $PassLine, 'gotm_minimum_text_rows=(\d+)')
  if (!$WarmMatch.Success -or !$WarmMaxMatch.Success -or
      !$ColdMatch.Success -or
      !$PreparedMoveMatch.Success -or !$HeldMoveMatch.Success -or
      !$HeldRenderMatch.Success -or !$HeldActionTotalMatch.Success -or
      !$HeldWarmMatch.Success -or
      !$HeldWarmMaxMatch.Success -or !$MinimumTextBytesMatch.Success -or
      !$MinimumTextRowsMatch.Success) {
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
  $HeldMoveMaxMs = [double]::Parse(
    $HeldMoveMatch.Groups[1].Value,
    [Globalization.CultureInfo]::InvariantCulture)
  $HeldRenderMaxMs = [double]::Parse(
    $HeldRenderMatch.Groups[1].Value,
    [Globalization.CultureInfo]::InvariantCulture)
  $HeldActionTotalMaxMs = [double]::Parse(
    $HeldActionTotalMatch.Groups[1].Value,
    [Globalization.CultureInfo]::InvariantCulture)
  $HeldWarmMaxMs = [double]::Parse(
    $HeldWarmMaxMatch.Groups[1].Value,
    [Globalization.CultureInfo]::InvariantCulture)
  $MinimumTextBytes = [int64]$MinimumTextBytesMatch.Groups[1].Value
  $MinimumTextRows = [int]$MinimumTextRowsMatch.Groups[1].Value
  if ($WarmMs -ge $ColdMs) {
    throw "prepared page render did not improve navigation: warm=$WarmMs cold=$ColdMs"
  }
  $WarmMaxMs = [double]::Parse(
    $WarmMaxMatch.Groups[1].Value,
    [Globalization.CultureInfo]::InvariantCulture)
  $HeldRepeatBudgetMs = 48.0
  if ($WarmMaxMs -ge 16.667 -or $PreparedMoveMaxMs -ge 16.667 -or
      $HeldWarmMaxMs -ge 16.667 -or $HeldMoveMaxMs -ge 16.667 -or
      $HeldRenderMaxMs -ge $HeldRepeatBudgetMs -or
      $HeldActionTotalMaxMs -ge $HeldRepeatBudgetMs -or
      $MinimumTextBytes -lt 8 -or $MinimumTextRows -lt 1) {
    throw "prepared page turn exceeded its bounded budget: move_max=$PreparedMoveMaxMs render_max=$WarmMaxMs held_warm_max=$HeldWarmMaxMs held_move_max=$HeldMoveMaxMs held_render_max=$HeldRenderMaxMs held_action_total_max=$HeldActionTotalMaxMs held_repeat_budget=$HeldRepeatBudgetMs"
  }
  [pscustomobject]@{
    pass_line=[string]$PassLine
    prepared_move_max_ms=$PreparedMoveMaxMs
    held_move_max_ms=$HeldMoveMaxMs
    held_render_max_ms=$HeldRenderMaxMs
    held_action_total_max_ms=$HeldActionTotalMaxMs
    held_warm_steps=[int]$HeldWarmMatch.Groups[1].Value
    held_warm_max_ms=$HeldWarmMaxMs
    warmed_render_avg_ms=$WarmMs
    warmed_render_max_ms=$WarmMaxMs
    cold_render_avg_ms=$ColdMs
    prepared_warm_pages=[int]$PreparedMatch.Groups[1].Value
    gotm_minimum_text_bytes=$MinimumTextBytes
    gotm_minimum_text_rows=$MinimumTextRows
    boundary_oracle="raw_spine_utf8_word_start"
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
  win32_queue=$QueueSummary
  contract=@{
    direct_traversal="64+64_exact"
    forward_pages=64
    backward_pages=64
    canonical_nonempty_frames=128
    independent_range_oracle="external_frozen_re10_required_not_evaluated"
  }
  repeat=2
  runs=@($First, $Second)
}
$SummaryPath = Join-Path $Out "summary.json"
$Summary | ConvertTo-Json -Depth 7 |
  Set-Content -Encoding ASCII -LiteralPath $SummaryPath
Write-Host "win32_lectern0_page_turn_regression_smoke result=pass repeat=2 summary=$SummaryPath"

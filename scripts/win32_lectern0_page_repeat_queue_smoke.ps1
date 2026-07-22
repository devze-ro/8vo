param(
  [string]$OutDir = "C:\Temp\lectern0_page_repeat_queue",
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

function Read-Metric {
  param([string]$Line, [string]$Name)
  $Match = [regex]::Match(
    $Line, '(?:^|\s)' + [regex]::Escape($Name) + '=([0-9.]+)')
  if (!$Match.Success) { throw "missing $Name in $Line" }
  return [double]::Parse(
    $Match.Groups[1].Value,
    [Globalization.CultureInfo]::InvariantCulture)
}

function Read-PairMetric {
  param([string]$Line, [string]$Name)
  $Match = [regex]::Match(
    $Line, '(?:^|\s)' + [regex]::Escape($Name) + '=(\d+)\+(\d+)')
  if (!$Match.Success) { throw "missing paired $Name in $Line" }
  return @([int64]$Match.Groups[1].Value, [int64]$Match.Groups[2].Value)
}

function Invoke-QueueSmoke {
  param([string]$Name)
  $RunDir = Join-Path $Out $Name
  New-Item -ItemType Directory -Path $RunDir | Out-Null
  $Log = Join-Path $RunDir "run.log"
  $NativeExitCode = 0
  $PreviousErrorActionPreference = $ErrorActionPreference
  try {
    # Windows PowerShell wraps native stderr as a non-terminating
    # NativeCommandError. Capture that diagnostic stream without allowing the
    # script-wide Stop policy to preempt the authoritative process exit code.
    $ErrorActionPreference = "Continue"
    & $Exe --page-repeat-win32-smoke $Book *> $Log
    $NativeExitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $PreviousErrorActionPreference
  }
  if ($NativeExitCode -ne 0) {
    $Tail = (Get-Content -LiteralPath $Log -Tail 100) -join "`n"
    throw "Lectern0 Win32 page-repeat queue smoke failed`n$Tail"
  }
  $PassLine = Get-Content -LiteralPath $Log | Where-Object {
    $_ -match '^lectern0_page_repeat_win32_smoke result=pass '
  } | Select-Object -Last 1
  foreach ($Token in @(
    "viewport=1917x1137", "directions=2/2",
    "reversal=forward_then_backward", "returned_to_anchor=1/1",
    "cross_spine_directions=2/2", "cross_spine_transitions=",
    "schedule=wall_clock_rebased_no_catch_up", "frame_rate=60",
    "initial_frames=24", "interval_frames=3", "repeat_moves=12+12",
    "presented_frames=13+13", "expected_presented_frames=13+13",
    "idle_presentations=0/0", "canonical_pages=26/26",
    "canonical_nonempty_frames=26/26", "zero_pages_or_frames=0/0",
    "orphan_text_pages=0/0", "invalid_word_start_pages=0/0",
    "boundary_oracle=raw_spine_utf8_word_start",
    "gotm_prose_scope=active_spine_text_ge_128",
    "queue_range_oracle=self_derived_order_only",
    "independent_range_oracle=external_frozen_re10_required_not_evaluated",
    "gotm_minimum_text_bytes", "gotm_minimum_text_rows",
    "action_presentations=26/26",
    "action_overlap=0/0", "native_repeats=208/208",
    "navigation_prepare_calls=12+12", "navigation_prepare_builds=",
    "navigation_prepare_ready=",
    "navigation_prepare_cross_spine_ready=0+2",
    "navigation_prepare_failures=0+0",
    "prepared_window_moves=", "synchronous_window_rebuild_moves=0+0",
    "synchronous_adjacent_measured_moves=0+0",
    "interval_samples=22/22", "action_timing_samples=24/24",
    "stable_presentations=26/26", "visible_interval_samples=22/22",
    "keyup_cancel=2/2",
    "focus_cancel=1/1", "control_cancel=1/1",
    "shift_cancel=1/1", "system_modifier_cancel=1/1",
    "modifier_cancel=3/3", "deactivate_cancel=1/1",
    "cancelled_repeats=5/5",
    "mutation_gate_drops=7/7", "mutation_cancel=1/1",
    "capture_failure_page_recovery=1/1",
    "capture_failure_same_page_freshness=1/1",
    "capture_failure_open_catalog_recovery=1/1",
    "capture_failure_retry_timer_recovery=1/1",
    "capture_failure_persistence_recovery=1/1",
    "image_page_gate=4/4",
    "persistence_deferred=24/24", "persistence_rescheduled=8/8",
    "persistence_transactions=8/8",
    "persistence_hold_state_catalog_unchanged=8/8",
    "persistence_post_stop_state_catalog_advanced=8/8",
    "persistence=sandboxed_paired_save_individually_atomic_files",
    "persistence_cleanup=1/1", "auxiliary_paint_dispatched=2/2",
    "main_null_paint_dispatched=2/2", "paint=real_queue_dispatch",
    "nominal_first_move_ms=400.000",
    "nominal_move_interval_ms=50.000", "nominal_elapsed_ms=950.000",
    "forward_immediate_visible_ms", "forward_visible_first_delay_ms",
    "forward_visible_interval_min_ms", "forward_visible_interval_avg_ms",
    "forward_visible_interval_max_ms", "forward_visible_elapsed_ms",
    "backward_immediate_visible_ms", "backward_visible_first_delay_ms",
    "backward_visible_interval_min_ms", "backward_visible_interval_avg_ms",
    "backward_visible_interval_max_ms", "backward_visible_elapsed_ms",
    "interval_tolerance_ms=34", "timing_tolerance_ms=200",
    "frame_budget_ms=64", "move_prepare_budget_ms=16.667",
    "render_budget_ms=48.000", "present_budget_ms=16.667",
    "forward_cold_render_ms",
    "forward_cold_present_ms", "forward_repeat_prepare_max_ms",
    "forward_repeat_render_max_ms", "forward_repeat_present_max_ms",
    "backward_cold_render_ms", "backward_cold_present_ms",
    "backward_repeat_prepare_max_ms", "backward_repeat_render_max_ms",
    "backward_repeat_present_max_ms")) {
    if (!$PassLine -or $PassLine -notmatch [regex]::Escape($Token)) {
      throw "Win32 queue result is incomplete: missing $Token in $PassLine"
    }
  }
  $ForwardFirstMs = Read-Metric $PassLine "forward_first_move_ms"
  $BackwardFirstMs = Read-Metric $PassLine "backward_first_move_ms"
  $ForwardIntervalMinMs = Read-Metric $PassLine "forward_interval_min_ms"
  $ForwardIntervalAvgMs = Read-Metric $PassLine "forward_interval_avg_ms"
  $ForwardIntervalMaxMs = Read-Metric $PassLine "forward_interval_max_ms"
  $BackwardIntervalMinMs = Read-Metric $PassLine "backward_interval_min_ms"
  $BackwardIntervalAvgMs = Read-Metric $PassLine "backward_interval_avg_ms"
  $BackwardIntervalMaxMs = Read-Metric $PassLine "backward_interval_max_ms"
  $ForwardElapsedMs = Read-Metric $PassLine "forward_elapsed_ms"
  $BackwardElapsedMs = Read-Metric $PassLine "backward_elapsed_ms"
  $ForwardImmediateVisibleMs =
    Read-Metric $PassLine "forward_immediate_visible_ms"
  $ForwardVisibleFirstDelayMs =
    Read-Metric $PassLine "forward_visible_first_delay_ms"
  $ForwardVisibleIntervalMinMs =
    Read-Metric $PassLine "forward_visible_interval_min_ms"
  $ForwardVisibleIntervalAvgMs =
    Read-Metric $PassLine "forward_visible_interval_avg_ms"
  $ForwardVisibleIntervalMaxMs =
    Read-Metric $PassLine "forward_visible_interval_max_ms"
  $ForwardVisibleElapsedMs =
    Read-Metric $PassLine "forward_visible_elapsed_ms"
  $BackwardImmediateVisibleMs =
    Read-Metric $PassLine "backward_immediate_visible_ms"
  $BackwardVisibleFirstDelayMs =
    Read-Metric $PassLine "backward_visible_first_delay_ms"
  $BackwardVisibleIntervalMinMs =
    Read-Metric $PassLine "backward_visible_interval_min_ms"
  $BackwardVisibleIntervalAvgMs =
    Read-Metric $PassLine "backward_visible_interval_avg_ms"
  $BackwardVisibleIntervalMaxMs =
    Read-Metric $PassLine "backward_visible_interval_max_ms"
  $BackwardVisibleElapsedMs =
    Read-Metric $PassLine "backward_visible_elapsed_ms"
  $ForwardFrameMaxMs = Read-Metric $PassLine "forward_frame_max_ms"
  $BackwardFrameMaxMs = Read-Metric $PassLine "backward_frame_max_ms"
  $ForwardColdRenderMs = Read-Metric $PassLine "forward_cold_render_ms"
  $ForwardColdPresentMs = Read-Metric $PassLine "forward_cold_present_ms"
  $ForwardPrepareMaxMs = Read-Metric $PassLine "forward_repeat_prepare_max_ms"
  $ForwardRenderMaxMs = Read-Metric $PassLine "forward_repeat_render_max_ms"
  $ForwardPresentMaxMs = Read-Metric $PassLine "forward_repeat_present_max_ms"
  $BackwardColdRenderMs = Read-Metric $PassLine "backward_cold_render_ms"
  $BackwardColdPresentMs = Read-Metric $PassLine "backward_cold_present_ms"
  $BackwardPrepareMaxMs = Read-Metric $PassLine "backward_repeat_prepare_max_ms"
  $BackwardRenderMaxMs = Read-Metric $PassLine "backward_repeat_render_max_ms"
  $BackwardPresentMaxMs = Read-Metric $PassLine "backward_repeat_present_max_ms"
  $QueueDrainBatchMax = Read-Metric $PassLine "queue_drain_batch_max"
  $CrossSpineTransitions = Read-Metric $PassLine "cross_spine_transitions"
  $NavigationPrepareBuilds =
    Read-PairMetric $PassLine "navigation_prepare_builds"
  $NavigationPrepareCrossSpineReady =
    Read-PairMetric $PassLine "navigation_prepare_cross_spine_ready"
  $PreparedWindowMoves = Read-PairMetric $PassLine "prepared_window_moves"
  $MinimumTextBytes = Read-Metric $PassLine "gotm_minimum_text_bytes"
  $MinimumTextRows = Read-Metric $PassLine "gotm_minimum_text_rows"
  $MovePrepareBudgetMs = 16.667
  $RenderBudgetMs = 48.0
  $PresentBudgetMs = 16.667
  if ($ForwardFirstMs -lt 300.0 -or $ForwardFirstMs -gt 600.0 -or
      $BackwardFirstMs -lt 300.0 -or $BackwardFirstMs -gt 600.0 -or
      $ForwardIntervalMinMs -lt 25.0 -or
      $ForwardIntervalAvgMs -lt 25.0 -or
      $ForwardIntervalMaxMs -gt 84.0 -or
      $BackwardIntervalMinMs -lt 25.0 -or
      $BackwardIntervalAvgMs -lt 25.0 -or
      $BackwardIntervalMaxMs -gt 84.0 -or
      $ForwardElapsedMs -lt 712.5 -or $ForwardElapsedMs -gt 1150.0 -or
      $BackwardElapsedMs -lt 712.5 -or $BackwardElapsedMs -gt 1150.0 -or
      $ForwardImmediateVisibleMs -ge 64.0 -or
      $BackwardImmediateVisibleMs -ge 64.0 -or
      $ForwardVisibleFirstDelayMs -lt 300.0 -or
      $ForwardVisibleFirstDelayMs -gt 600.0 -or
      $BackwardVisibleFirstDelayMs -lt 300.0 -or
      $BackwardVisibleFirstDelayMs -gt 600.0 -or
      $ForwardVisibleIntervalMinMs -lt 25.0 -or
      $ForwardVisibleIntervalAvgMs -lt 25.0 -or
      $ForwardVisibleIntervalMaxMs -gt 84.0 -or
      $BackwardVisibleIntervalMinMs -lt 25.0 -or
      $BackwardVisibleIntervalAvgMs -lt 25.0 -or
      $BackwardVisibleIntervalMaxMs -gt 84.0 -or
      $ForwardVisibleElapsedMs -lt 712.5 -or
      $ForwardVisibleElapsedMs -gt 1150.0 -or
      $BackwardVisibleElapsedMs -lt 712.5 -or
      $BackwardVisibleElapsedMs -gt 1150.0 -or
      $ForwardFrameMaxMs -ge 64.0 -or $BackwardFrameMaxMs -ge 64.0 -or
      $ForwardPrepareMaxMs -ge $MovePrepareBudgetMs -or
      $BackwardPrepareMaxMs -ge $MovePrepareBudgetMs -or
      $ForwardRenderMaxMs -ge $RenderBudgetMs -or
      $BackwardRenderMaxMs -ge $RenderBudgetMs -or
      $ForwardPresentMaxMs -ge $PresentBudgetMs -or
      $BackwardPresentMaxMs -ge $PresentBudgetMs -or
      $CrossSpineTransitions -lt 2.0 -or
      $NavigationPrepareBuilds[0] -lt 1 -or
      $NavigationPrepareBuilds[1] -lt 1 -or
      $NavigationPrepareCrossSpineReady[0] -ne 0 -or
      $NavigationPrepareCrossSpineReady[1] -ne 2 -or
      $PreparedWindowMoves[0] -lt 1 -or
      $PreparedWindowMoves[1] -lt 1 -or
      $QueueDrainBatchMax -lt 8.0 -or $QueueDrainBatchMax -gt 32.0 -or
      $MinimumTextBytes -lt 8.0 -or $MinimumTextRows -lt 1.0) {
    throw "Win32 queue cadence exceeded its nominal 24/3 tolerance: $PassLine"
  }
  [pscustomobject]@{
    pass_line=[string]$PassLine
    forward_first_move_ms=$ForwardFirstMs
    backward_first_move_ms=$BackwardFirstMs
    forward_interval_min_ms=$ForwardIntervalMinMs
    forward_interval_avg_ms=$ForwardIntervalAvgMs
    forward_interval_max_ms=$ForwardIntervalMaxMs
    backward_interval_min_ms=$BackwardIntervalMinMs
    backward_interval_avg_ms=$BackwardIntervalAvgMs
    backward_interval_max_ms=$BackwardIntervalMaxMs
    forward_elapsed_ms=$ForwardElapsedMs
    backward_elapsed_ms=$BackwardElapsedMs
    forward_immediate_visible_ms=$ForwardImmediateVisibleMs
    forward_visible_first_delay_ms=$ForwardVisibleFirstDelayMs
    forward_visible_interval_min_ms=$ForwardVisibleIntervalMinMs
    forward_visible_interval_avg_ms=$ForwardVisibleIntervalAvgMs
    forward_visible_interval_max_ms=$ForwardVisibleIntervalMaxMs
    forward_visible_elapsed_ms=$ForwardVisibleElapsedMs
    backward_immediate_visible_ms=$BackwardImmediateVisibleMs
    backward_visible_first_delay_ms=$BackwardVisibleFirstDelayMs
    backward_visible_interval_min_ms=$BackwardVisibleIntervalMinMs
    backward_visible_interval_avg_ms=$BackwardVisibleIntervalAvgMs
    backward_visible_interval_max_ms=$BackwardVisibleIntervalMaxMs
    backward_visible_elapsed_ms=$BackwardVisibleElapsedMs
    forward_frame_max_ms=$ForwardFrameMaxMs
    backward_frame_max_ms=$BackwardFrameMaxMs
    forward_cold_render_ms=$ForwardColdRenderMs
    forward_cold_present_ms=$ForwardColdPresentMs
    forward_repeat_prepare_max_ms=$ForwardPrepareMaxMs
    forward_repeat_render_max_ms=$ForwardRenderMaxMs
    forward_repeat_present_max_ms=$ForwardPresentMaxMs
    backward_cold_render_ms=$BackwardColdRenderMs
    backward_cold_present_ms=$BackwardColdPresentMs
    backward_repeat_prepare_max_ms=$BackwardPrepareMaxMs
    backward_repeat_render_max_ms=$BackwardRenderMaxMs
    backward_repeat_present_max_ms=$BackwardPresentMaxMs
    cross_spine_transitions=[int]$CrossSpineTransitions
    navigation_prepare_builds=@($NavigationPrepareBuilds)
    navigation_prepare_cross_spine_ready=@($NavigationPrepareCrossSpineReady)
    prepared_window_moves=@($PreparedWindowMoves)
    queue_drain_batch_max=[int]$QueueDrainBatchMax
    gotm_minimum_text_bytes=[int64]$MinimumTextBytes
    gotm_minimum_text_rows=[int]$MinimumTextRows
    boundary_oracle="raw_spine_utf8_word_start"
    queue_range_oracle="self_derived_order_only"
    independent_range_oracle="external_frozen_re10_required_not_evaluated"
    mutation_gate_drops=7
    mutation_cancel=1
    capture_failure_page_recovery=$true
    capture_failure_same_page_freshness=$true
    capture_failure_open_catalog_recovery=$true
    image_page_gate_count=4
    log=$Log
  }
}

$First = Invoke-QueueSmoke "run_1"
$Second = Invoke-QueueSmoke "run_2"
$Summary = [pscustomobject]@{
  generated_at=(Get-Date).ToString("o")
  status="pass"
  git_head=(& git -C $Root rev-parse HEAD).Trim()
  git_status=@(& git -C $Root status --porcelain --untracked-files=all)
  executable=@{
    path=$Exe
    sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $Exe).Hash
  }
  book=@{ path=$Book; sha256=$BookHash; size=$BookItem.Length }
  contract=@{
    schedule="wall_clock_rebased_no_catch_up"
    frame_rate=60
    initial_frames=24
    interval_frames=3
    reversal="forward_then_backward"
    cross_spine_directions=2
    cross_spine_transition_minimum=2
    returned_to_anchor=$true
    repeat_moves_per_direction=12
    presented_frames_per_direction=13
    idle_presentations=0
    canonical_nonempty_frames=26
    zero_pages_or_frames=0
    orphan_text_pages=0
    invalid_word_start_pages=0
    minimum_prose_text_bytes=8
    minimum_prose_text_rows=1
    boundary_oracle="raw_spine_utf8_word_start"
    queue_range_oracle="self_derived_order_only"
    independent_range_oracle="external_frozen_re10_required_not_evaluated"
    action_presentations=26
    action_overlaps=0
    stable_presentations=26
    visible_interval_samples=22
    native_repeats=208
    nominal_first_move_ms=400.0
    nominal_move_interval_ms=50.0
    interval_tolerance_ms=34.0
    interval_samples=22
    action_timing_samples=24
    nominal_elapsed_ms=950.0
    timing_tolerance_ms=200.0
    frame_budget_ms=64.0
    move_prepare_budget_ms=16.667
    render_budget_ms=48.0
    present_budget_ms=16.667
    navigation_prepare_calls_per_direction=12
    navigation_prepare_cross_spine_ready=@(0, 2)
    terminal_navigation_prepare_suppressed=$true
    queue_drain_batch_cap=32
    mutation_gate_drops=7
    mutation_cancel=1
    capture_failure_page_recovery=$true
    capture_failure_same_page_freshness=$true
    capture_failure_open_catalog_recovery=$true
    image_page_gate_count=4
    persistence_transactions=8
  }
  repeat=2
  runs=@($First, $Second)
}
$SummaryPath = Join-Path $Out "summary.json"
$Summary | ConvertTo-Json -Depth 7 |
  Set-Content -Encoding ASCII -LiteralPath $SummaryPath
Write-Host "win32_lectern0_page_repeat_queue_smoke result=pass repeat=2 summary=$SummaryPath"

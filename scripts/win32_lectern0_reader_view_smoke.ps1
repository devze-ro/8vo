param(
  [string]$BookPath = "local\slice1_host_smoke\lectern0_slice1.epub",
  [string]$OutDir = "local\slice5b_reader_view_smoke"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Exe = Join-Path $Root "build\win32\lectern0.exe"
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing lectern0 executable: $Exe"
}

$ResolvedBook = if ([System.IO.Path]::IsPathRooted($BookPath)) {
  (Resolve-Path -LiteralPath $BookPath).Path
} else {
  $candidate = Join-Path $Root $BookPath
  if (!(Test-Path -LiteralPath $candidate -PathType Leaf)) {
    & (Join-Path $PSScriptRoot "win32_lectern0_host_smoke.ps1") | Write-Host
  }
  (Resolve-Path -LiteralPath $candidate).Path
}

$Out = Join-Path $Root $OutDir
New-Item -ItemType Directory -Force -Path $Out | Out-Null

function Invoke-Lectern0ReaderViewSmoke {
  param([string]$Name)

  $export = Join-Path $Out "$Name.txt"
  $output = & $Exe --reader-view-smoke $ResolvedBook $export 2>&1
  if ($LASTEXITCODE -ne 0) {
    $output | Write-Host
    throw "lectern0 Reader View smoke failed with exit code $LASTEXITCODE"
  }
  $line = $output | Where-Object { $_ -match '^lectern0_reader_view_smoke result=pass ' } |
    Select-Object -Last 1
  if (!$line -or
      $line -notmatch 'focus=reference13' -or
      $line -notmatch 'panel_focus=toc_find_annotations_progress_boundary' -or
      $line -notmatch 'keyboard_routing=focused_edit_or_activate' -or
      $line -notmatch 'find_shortcut=focused_input' -or
      $line -notmatch 'navigation_panels=space_toc_find' -or
      $line -notmatch 'gutters=boundary_roundtrip' -or
      $line -notmatch 'gutter_input=keyboard_pointer' -or
      $line -notmatch 'carets=frozen18x32' -or
      $line -notmatch 'toc_identity=noncontiguous' -or
      $line -notmatch 'find_execution=commit_only' -or
      $line -notmatch 'find_clear=immediate' -or
      $line -notmatch 'find_metrics=bounded_values' -or
      $line -notmatch 'find_match=measured' -or
      $line -notmatch 'note_metrics=bounded_values_18px' -or
      $line -notmatch 'note_raster=text_engine_editable_row_caret' -or
      $line -notmatch 'annotations=reference_metadata' -or
      $line -notmatch 'bookmark_star=projected_remove_once' -or
      $line -notmatch 'annotations_interaction=close_filter_edit_menu' -or
      $line -notmatch 'annotations_pointer=open_filter_escape_select_row_star_menu_note_lifecycle_close' -or
      $line -notmatch 'note_lifecycle=acknowledged' -or
      $line -notmatch 'annotation_note_selection=preserved_unrelated' -or
      $line -notmatch 'selection_note_selection=released_owned' -or
      $line -notmatch 'annotation_identity=v3_migrate_demote_restart' -or
      $line -notmatch 'note_persistence=atomic_rollback_open' -or
      $line -notmatch 'bookmark_persistence=rollback' -or
      $line -notmatch 'star_persistence=rollback' -or
      $line -notmatch ' hash=([0-9a-fA-F]{16}) ') {
    $output | Write-Host
    throw "lectern0 Reader View smoke did not report deterministic evidence"
  }
  foreach ($path in @($export, "$export.settings", "$export.annotations")) {
    if (!(Test-Path -LiteralPath $path -PathType Leaf) -or
        (Get-Item -LiteralPath $path).Length -eq 0) {
      throw "lectern0 Reader View smoke did not persist evidence: $path"
    }
  }

  [pscustomobject]@{
    Line = [string]$line
    Hash = $Matches[1].ToLowerInvariant()
    ExportHash = (Get-FileHash -LiteralPath $export -Algorithm SHA256).Hash
    SettingsHash = (Get-FileHash -LiteralPath "$export.settings" -Algorithm SHA256).Hash
    AnnotationsHash = (Get-FileHash -LiteralPath "$export.annotations" -Algorithm SHA256).Hash
  }
}

$first = Invoke-Lectern0ReaderViewSmoke "first"
$second = Invoke-Lectern0ReaderViewSmoke "second"
if ($first.Hash -ne $second.Hash -or
    $first.ExportHash -ne $second.ExportHash -or
    $first.SettingsHash -ne $second.SettingsHash -or
    $first.AnnotationsHash -ne $second.AnnotationsHash) {
  throw "lectern0 Reader View smoke is not repeatable"
}

Write-Host $second.Line
Write-Host "win32_lectern0_reader_view_smoke result=pass repeat=2 hash=$($second.Hash) out=$Out"

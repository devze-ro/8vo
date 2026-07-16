param(
  [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$failures = [System.Collections.Generic.List[string]]::new()
$buildPath = Join-Path $RepoRoot "code\build.c"
$appPath = Join-Path $RepoRoot "code\lectern0.c"
$accessibilityPath = Join-Path $RepoRoot "code\platform\win32\lectern0_accessibility_win32.c"
if (!(Test-Path -LiteralPath $buildPath)) { $failures.Add("missing code/build.c") }
if (!(Test-Path -LiteralPath $appPath)) { $failures.Add("missing code/lectern0.c") }
if (!(Test-Path -LiteralPath $accessibilityPath)) {
  $failures.Add("missing host-owned Win32 accessibility adapter")
}

if ($failures.Count -eq 0) {
  $build = [System.IO.File]::ReadAllText($buildPath)
  $app = [System.IO.File]::ReadAllText($appPath)
  $accessibility = [System.IO.File]::ReadAllText($accessibilityPath)
  if ([regex]::Matches($build, '#include\s+"reader0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile reader0.c exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"ui0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile ui0.c exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"readerview0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile readerview0.c exactly once")
  }
  if ([regex]::Matches(
        $build,
        '#include\s+"platform/win32/lectern0_accessibility_win32\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile the host accessibility adapter exactly once")
  }
  if ([regex]::Matches($build, '#\s*include\s+"os/os_image\.c"').Count -ne 1 -or
      [regex]::Matches($build, '#\s*include\s+"platform/win32/os_image_win32\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile the shared os_image API and Win32 backend exactly once")
  }
  if ([regex]::Matches($build, '#\s*include\s+"presentation_engine/presentation_engine\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile Presentation Engine API 1 exactly once")
  }
  if ($app.IndexOf('#include "reader0.h"') -lt 0) {
    $failures.Add("lectern0 must consume the reader0 umbrella")
  }
  if ($app.IndexOf('#include "ui0.h"') -lt 0) {
    $failures.Add("lectern0 must consume the UI0 umbrella")
  }
  if ($app.IndexOf('#include "readerview0.h"') -lt 0 -or
      $app.IndexOf('READERVIEW0_API_VERSION != 2') -lt 0) {
    $failures.Add("lectern0 must consume Reader View API 2 through its umbrella")
  }
  if ($app.IndexOf('reader_view_build') -lt 0 -or
      $app.IndexOf('lectern0_prepare_reader_view_projection') -lt 0 -or
      $app.IndexOf('lectern0_apply_reader_view_actions') -lt 0) {
    $failures.Add("lectern0 must project host data into Reader View API 2 and execute returned actions")
  }
  if ($app.IndexOf('reader_view_resolve_content_geometry') -lt 0 -or
      $app.IndexOf('ReaderViewContentGeometry reader_content_geometry') -lt 0 -or
      $app.IndexOf('host_toolbar_trailing_width = Lectern0HostToolbarTrailingWidth') -lt 0) {
    $failures.Add("lectern0 must adopt Reader View API 2 content geometry and retain the host toolbar slot")
  }
  if ($app.IndexOf('ui0_theme_profile_for_kind') -lt 0 -or
      $app.IndexOf('Lectern0Theme_Count == 6') -lt 0 -or
      $app.IndexOf('file.version == 1') -lt 0 -or
      $app.IndexOf('.version = 2') -lt 0) {
    $failures.Add("lectern0 must expose all six shared themes with explicit legacy settings migration")
  }
  if ($app.IndexOf('lectern0_draw_adapter_covers_all_ops') -lt 0 -or
      $app.IndexOf('unsupported_count') -lt 0 -or
      $app.IndexOf('lectern0_draw_ui0_icon') -lt 0 -or
      $app.IndexOf('lectern0_draw_ui0_text') -lt 0) {
    $failures.Add("lectern0 must render and test the complete UI0 draw-operation surface")
  }
  if ($app -match 'Lectern0Theme_Sepia' -or
      $app -match 'reader_view_text_is\(text,\s*"Previous page"\)' -or
      $app -match 'reader_view_text_is\(text,\s*"Next page"\)') {
    $failures.Add("lectern0 must not retain the old three-theme enum or ASCII reader-control substitutions")
  }
  if ($app.IndexOf('lectern0_save_settings') -lt 0 -or
      $app.IndexOf('lectern0_save_annotations') -lt 0 -or
      $app.IndexOf('lectern0_export_annotations') -lt 0) {
    $failures.Add("lectern0 must retain settings and annotation persistence ownership")
  }
  if ($app.IndexOf('--reader-view-smoke') -lt 0) {
    $failures.Add("lectern0 must retain deterministic Reader View API 2 action evidence")
  }
  if ($app.IndexOf('--accessibility-smoke') -lt 0 -or
      $app.IndexOf('WM_GETOBJECT') -lt 0 -or
      $accessibility.IndexOf('IAccessible') -lt 0 -or
      $accessibility.IndexOf('reader_view_accessibility_invoke') -lt 0) {
    $failures.Add("lectern0 must retain its native adapter over shared semantic/action records")
  }
  if ($app.IndexOf('epub_reader_move_page') -lt 0 -or
      $app.IndexOf('epub_reader_navigate_to_nav_point') -lt 0 -or
      $app.IndexOf('epub_reader_navigate_to_search_match') -lt 0) {
    $failures.Add("lectern0 must consume the concrete reader0 API 3 page and semantic navigation surface")
  }
  if ($app.IndexOf('font_cache_tag_from_provider') -lt 0 -or
      $app.IndexOf('lectern0_push_reader_text_chunks') -lt 0 -or
      $app.IndexOf('DrawTextFlag_Shaped') -lt 0) {
    $failures.Add("lectern0 must chunk text through the reader-resolved provider and shaped text mode")
  }
  if ($app.IndexOf('lectern0_frame_text_rows_are_complete') -lt 0 -or
      $app.IndexOf('--render-smoke') -lt 0) {
    $failures.Add("lectern0 must retain deterministic canonical-frame visual evidence")
  }
  if ($app.IndexOf('os_image_decode') -lt 0 -or
      $app.IndexOf('Lectern0ImageCache') -lt 0 -or
      $app.IndexOf('draw_push_sprite_clipped') -lt 0) {
    $failures.Add("lectern0 must own cache policy while using shared decode and sprite presentation")
  }
  if ($app.IndexOf('#include "presentation_engine/presentation_engine.h"') -lt 0 -or
      $app.IndexOf('PRESENTATION_ENGINE_API_VERSION != 1') -lt 0 -or
      $app.IndexOf('lectern0_build_reader_presentation') -lt 0 -or
      $app.IndexOf('presentation_engine_block_flow_build') -lt 0) {
    $failures.Add("lectern0 must route canonical-row vertical geometry through Presentation Engine API 1")
  }
  if ($app -match 'IWIC|CLSID_WIC|wincodec\.h') {
    $failures.Add("lectern0 must not duplicate the zero_foundation WIC backend")
  }
  if ($build -match '(?i)re10' -or $app -match '(?i)re10') {
    $failures.Add("lectern0 source closure must not depend on re10")
  }
}

if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Error $_ }
  exit 1
}

Write-Host "lectern0 architecture audit: pass"
Write-Host "boundary: zero_foundation presentation/image/render + readerview0/UI0 chrome + reader0 concrete EPUB core"

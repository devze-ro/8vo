param(
  [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$failures = [System.Collections.Generic.List[string]]::new()
$buildPath = Join-Path $RepoRoot "code\build.c"
$appPath = Join-Path $RepoRoot "code\lectern0.c"
$libraryHeaderPath = Join-Path $RepoRoot "code\lectern0_library.h"
$libraryPath = Join-Path $RepoRoot "code\lectern0_library.c"
$accessibilityPath = Join-Path $RepoRoot "code\platform\win32\lectern0_accessibility_win32.c"
$parityPath = Join-Path $RepoRoot "scripts\win32_reader_view_stage2b0_parity.ps1"
if (!(Test-Path -LiteralPath $buildPath)) { $failures.Add("missing code/build.c") }
if (!(Test-Path -LiteralPath $appPath)) { $failures.Add("missing code/lectern0.c") }
if (!(Test-Path -LiteralPath $libraryHeaderPath)) {
  $failures.Add("missing bounded Lectern0 library records")
}
if (!(Test-Path -LiteralPath $libraryPath)) {
  $failures.Add("missing Lectern0 library persistence implementation")
}
if (!(Test-Path -LiteralPath $accessibilityPath)) {
  $failures.Add("missing host-owned Win32 accessibility adapter")
}
if (!(Test-Path -LiteralPath $parityPath)) {
  $failures.Add("missing two-host Reader View parity runner")
}

if ($failures.Count -eq 0) {
  $build = [System.IO.File]::ReadAllText($buildPath)
  $app = [System.IO.File]::ReadAllText($appPath)
  $libraryHeader = [System.IO.File]::ReadAllText($libraryHeaderPath)
  $library = [System.IO.File]::ReadAllText($libraryPath)
  $accessibility = [System.IO.File]::ReadAllText($accessibilityPath)
  $parity = [System.IO.File]::ReadAllText($parityPath)
  if ([regex]::Matches($build, '#include\s+"reader0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile reader0.c exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"ui0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile ui0.c exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"readerview0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile readerview0.c exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"lectern0_library\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile the host library implementation exactly once")
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
  if ($app.IndexOf('READER0_API_VERSION != 5') -lt 0 -or
      $app.IndexOf('doc_engine_get_author') -lt 0) {
    $failures.Add("lectern0 must consume Reader0 API 5 including author metadata")
  }
  if ($app.IndexOf('#include "ui0.h"') -lt 0) {
    $failures.Add("lectern0 must consume the UI0 umbrella")
  }
  if ($app.IndexOf('UI0_API_VERSION != 91') -lt 0 -or
      $app.IndexOf('ui0_icon_rasterize_rgb32') -lt 0) {
    $failures.Add("lectern0 must consume UI0 API 91 canonical reader icons")
  }
  if ($app.IndexOf('#include "readerview0.h"') -lt 0 -or
      $app.IndexOf('READERVIEW0_API_VERSION != 3') -lt 0) {
    $failures.Add("lectern0 must consume Reader View API 3 through its umbrella")
  }
  if ($app.IndexOf('reader_view_build') -lt 0 -or
      $app.IndexOf('lectern0_prepare_reader_view_projection') -lt 0 -or
      $app.IndexOf('lectern0_apply_reader_view_actions') -lt 0) {
    $failures.Add("lectern0 must project host data into Reader View API 3 and execute returned actions")
  }
  if ($app.IndexOf('.page_surface_rect = app->reader_view_layout.page_surface_rect') -lt 0 -or
      $app.IndexOf('.content_rect = app->reader_view_layout.content_rect') -lt 0 -or
      $app.IndexOf('ReaderViewContentGeometry reader_content_geometry') -lt 0 -or
      $app.IndexOf('host_toolbar_trailing_width = Lectern0HostToolbarTrailingWidth') -lt 0) {
    $failures.Add("lectern0 must adopt Reader View API 3 atomic layout geometry and retain the host toolbar slot")
  }
  if ($app.IndexOf('projection.chrome_title = lectern0_reader_view_text("EPUB Reader")') -lt 0 -or
      $app.IndexOf('Lectern0ToolbarHeight = 48') -ge 0 -or
      $app.IndexOf('Lectern0FooterHeight = 36') -ge 0) {
    $failures.Add("lectern0 must project the accepted title and must not retain independent 48/36 reader chrome")
  }
  if ($app.IndexOf('ReaderViewTextStyle_ChromeTitle') -lt 0 -or
      $app.IndexOf('ReaderViewTextStyle_ChromeMetadata') -lt 0 -or
      $app.IndexOf('ReaderViewTextStyle_MenuItem') -lt 0 -or
      $app.IndexOf('binding->style == ReaderViewTextStyle_ChromeTitle ? 2 : 1') -lt 0 -or
      $app.IndexOf('draw_push_text_box') -lt 0) {
    $failures.Add("lectern0 must map every finite Reader View text binding through its frozen host text-box raster")
  }
  if ($app.IndexOf('lectern0_reader_filter_rasterize_rgb32') -lt 0 -or
      $app.IndexOf('768785035519145851ull') -lt 0) {
    $failures.Add("lectern0 must preserve the frozen re10 SlidersVertical raster for the Reader View Filter intent")
  }
  if ($app.IndexOf('"%llu%%   Location %llu of %llu"') -lt 0 -or
      $app.IndexOf('"Page %llu of %llu"') -lt 0) {
    $failures.Add("lectern0 must preserve the accepted Reader View progress-label policy")
  }
  if ($app.IndexOf('.kind = UI0ControlKind_IconButton') -lt 0 -or
      $app.IndexOf('ui0_draw_push_icon(&draw') -lt 0 -or
      $app.IndexOf('UI0IconKind_Close') -lt 0 -or
      $app.IndexOf('UI0Control_Quiet') -ge 0) {
    $failures.Add("lectern0 Close Book must retain host interaction with the nonquiet UI0 IconButton shell and canonical Close icon")
  }
  if ($app.IndexOf('Lectern0HostControl_CloseBook') -lt 0 -or
      $app.IndexOf('host_exit_pointer_armed') -lt 0 -or
      $app.IndexOf('lectern0_host_keyboard_tab') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_Find') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_Fullscreen') -lt 0 -or
      $app.IndexOf('Close Book') -lt 0 -or
      $accessibility.IndexOf('lectern0_accessibility_host_identity') -lt 0 -or
      $accessibility.IndexOf('lectern0_accessibility_host_insertion_shared_count') -lt 0 -or
      $app.IndexOf('order=find_close_fullscreen') -lt 0 -or
      $app.IndexOf('UI0DrawCommand commands[5]') -lt 0 -or
      $app.IndexOf('.clip_rect = app->reader_view_layout.host_toolbar_trailing_rect') -lt 0 -or
      $accessibility.IndexOf('lectern0_host_control_invoke') -lt 0) {
    $failures.Add("lectern0 must expose and fully draw one host Close Book record between Find and Fullscreen for pointer, keyboard, focus-visible chrome, and native accessibility routing")
  }
  if ($app.IndexOf('focus=reference13') -lt 0 -or
      $app.IndexOf('panel_focus=toc_find_annotations_progress_boundary') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_panel_focus_regression') -lt 0 -or
      $app.IndexOf('keyboard_routing=focused_edit_or_activate') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_keyboard_input_routing_regression') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_text_editing') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_space_activates_focus') -lt 0 -or
      $app.IndexOf('find_shortcut=focused_input') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_open_find_from_shortcut') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_find_shortcut_focus_regression') -lt 0 -or
      $app.IndexOf('navigation_panels=space_toc_find') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_navigation_panel_interaction_regression') -lt 0 -or
      $app.IndexOf('gutters=boundary_roundtrip') -lt 0 -or
      $app.IndexOf('gutter_input=keyboard_pointer') -lt 0 -or
      $app.IndexOf('carets=frozen18x32') -lt 0 -or
      $app.IndexOf('UI0IconKind_PageCaretLeft') -lt 0 -or
      $app.IndexOf('UI0IconKind_PageCaretRight') -lt 0 -or
      $app.IndexOf('Lectern0UI0IconRasterMaxWidth = UI0_ICON_RASTER_MAX_WIDTH') -lt 0 -or
      $app.IndexOf('UI0DrawOp_FocusRing') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_PreviousPage') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_NextPage') -lt 0) {
    $failures.Add("lectern0 must lock the a6b combined focus order and exact keyboard/pointer page-gutter behavior")
  }
  if ($app.IndexOf('toc_identity=noncontiguous') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_covers_noncontiguous_toc_identity') -lt 0 -or
      $app.IndexOf('find_execution=commit_only') -lt 0 -or
      $app.IndexOf('find_clear=immediate') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_set_find_query') -lt 0 -or
      $app.IndexOf('find_edit_executed') -lt 0 -or
      $app.IndexOf('find_result_action') -lt 0) {
    $failures.Add("lectern0 must preserve source TOC identity and frozen edit/commit/clear/result Find behavior")
  }
  if ($app.IndexOf('"%s - re10 loc %llu"') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_right_secondary') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_register_right_source') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_sort_right_candidates') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_covers_mixed_right_order') -lt 0 -or
      $app.IndexOf('ReaderViewRow_AttachedToPrevious') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_find_text_metrics') -lt 0 -or
      $app.IndexOf('find_metrics=bounded_values') -lt 0 -or
      $app.IndexOf('find_match=measured') -lt 0 -or
      $app.IndexOf('lectern0_draw_adapter_covers_measured_find_match') -lt 0 -or
      $app.IndexOf('lectern0_draw_adapter_covers_find_status_and_metadata') -lt 0 -or
      $app.IndexOf('font_text_baseline_y_in_rect') -lt 0 -or
      $app.IndexOf('ReaderViewNoteTextMetrics') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_note_text_metrics') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_covers_note_text_metrics') -lt 0 -or
      $app.IndexOf('ReaderViewTextStyle_NoteEditor') -lt 0 -or
      $app.IndexOf('Lectern0ReaderViewNotePixelHeight = 18') -lt 0 -or
      $app.IndexOf('command->typography_line_height') -lt 0 -or
      $app.IndexOf('note_metrics=bounded_values_18px') -lt 0 -or
      $app.IndexOf('annotations=reference_metadata') -lt 0 -or
      $app.IndexOf('bookmark_star=projected_remove_once') -lt 0 -or
      $app.IndexOf('note ? highlight->note : highlight->text') -lt 0 -or
      $app.IndexOf('.secondary = lectern0_reader_view_text(highlight->note)') -ge 0) {
    $failures.Add("lectern0 must project frozen Highlight excerpts, Note bodies, kind/location metadata, and caller-measured 18px Note TextArea values while retaining host ownership")
  }
  if ($app.IndexOf('ReaderViewSemanticControl_RightFilter') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_RightFilterOption') -lt 0 -or
      $app.IndexOf('annotations-filter') -lt 0 -or
      $app.IndexOf('annotations_interaction=close_filter_edit_menu') -lt 0 -or
      $app.IndexOf('annotations_pointer=open_filter_escape_select_row_star_menu_note_lifecycle_close') -lt 0 -or
      $app.IndexOf('reader_view_close_note_editor') -lt 0 -or
      $app.IndexOf('ReaderViewAction_CancelNote') -lt 0 -or
      $app.IndexOf('note_lifecycle=acknowledged') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_annotation_interaction_regression') -lt 0 -or
      $app.IndexOf('lectern0_reader_view_annotation_pointer_regression') -lt 0) {
    $failures.Add("lectern0 must retain semantic annotation close/filter/row-menu parity evidence")
  }
  if ($app.IndexOf('B32 is_highlight;') -lt 0 -or
      $app.IndexOf('Lectern0AnnotationFileV2') -lt 0 -or
      $app.IndexOf('lectern0_migrate_highlight_v2') -lt 0 -or
      $app.IndexOf('file.version = 3') -lt 0 -or
      $app.IndexOf('lectern0_commit_annotations') -lt 0 -or
      $app.IndexOf('lectern0_remove_highlight_identity_at') -lt 0 -or
      $app.IndexOf('lectern0_delete_note_at_index') -lt 0 -or
      $app.IndexOf('lectern0_save_selection_note') -lt 0 -or
      $app.IndexOf('lectern0_toggle_highlight_star_at') -lt 0 -or
      $app.IndexOf('annotation_identity=v3_migrate_demote_restart') -lt 0 -or
      $app.IndexOf('note_persistence=atomic_rollback_open') -lt 0 -or
      $app.IndexOf('bookmark_persistence=rollback') -lt 0 -or
      $app.IndexOf('star_persistence=rollback') -lt 0) {
    $failures.Add("lectern0 must retain V1/V2-to-V3 annotation identity migration, Note-only demotion, atomic mutation rollback, and restart evidence")
  }
  if ($parity.IndexOf('wide_light_contents_panel') -lt 0 -or
      $parity.IndexOf('wide_light_annotations_highlight_note') -lt 0 -or
      $parity.IndexOf('wide_light_annotations_row_actions') -lt 0 -or
      $parity.IndexOf('wide_light_annotations_note_editor') -lt 0 -or
      $parity.IndexOf('wide_light_exit_keyboard_focus') -lt 0 -or
      $parity.IndexOf('wide_light_previous_gutter_keyboard_focus') -lt 0 -or
      $parity.IndexOf('wide_light_previous_enabled_gutter_keyboard_focus') -lt 0 -or
      $parity.IndexOf('wide_light_next_gutter_keyboard_focus') -lt 0) {
    $failures.Add("the two-host matrix must retain same-theme Contents, real Annotations list/menu/editor, and focused Exit plus disabled/enabled Previous and enabled Next gutter cases")
  }
  if ($parity.IndexOf('F7D9F95A174E0CA776E2BA808A6798D2DAF8B178CFF5D77270D225EC5DDA14D8') -lt 0 -or
      $parity.IndexOf('[string]$Re10Reader0Root') -lt 0 -or
      $parity.IndexOf('re10_reader0_head') -lt 0 -or
      $parity.IndexOf('Require-CleanTree') -lt 0 -or
      $parity.IndexOf('[switch]$AllowDirty') -lt 0 -or
      $parity.IndexOf('-SkipBuild is diagnostic-only') -lt 0 -or
      $parity.IndexOf('parity output root already exists') -lt 0 -or
      $parity.IndexOf('diagnostic_dirty_visual_parity') -lt 0 -or
      $parity.IndexOf('acceptance_eligible = !$AllowDirty -and !$SkipBuild') -lt 0) {
    $failures.Add("final two-host evidence must lock the canonical fixture, require a fresh output root and in-run builds, and reject dirty trees unless an explicit diagnostic-only override is supplied")
  }
  if ($app.IndexOf('ui0_theme_profile_for_kind') -lt 0 -or
      $app.IndexOf('Lectern0Theme_Count == 6') -lt 0 -or
      $app.IndexOf('file.version == 1') -lt 0 -or
      $app.IndexOf('.version = 2') -lt 0) {
    $failures.Add("lectern0 must expose all six shared themes with explicit legacy settings migration")
  }
  if ($app.IndexOf('lectern0_draw_adapter_covers_all_ops') -lt 0 -or
      $app.IndexOf('lectern0_draw_adapter_covers_reference_edges') -lt 0 -or
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
    $failures.Add("lectern0 must retain deterministic Reader View API 3 action evidence")
  }
  if ($app.IndexOf('--reader-view-startup-interaction-smoke') -lt 0 -or
      $app.IndexOf('lectern0_host_pointer_press') -lt 0 -or
      $app.IndexOf('lectern0_host_pointer_release') -lt 0 -or
      $app.IndexOf('surface=library catalog=empty') -lt 0) {
    $failures.Add("lectern0 must retain the empty-library native press/release Add EPUBs regression")
  }
  if ($app -match '(?s)draw_push_text_in_rect\([^;]+Open an EPUB to begin reading\.') {
    $failures.Add("Reader View must be the single owner of empty/loading/error status painting")
  }
  if ($app.IndexOf('--accessibility-smoke') -lt 0 -or
      $app.IndexOf('WM_GETOBJECT') -lt 0 -or
      $accessibility.IndexOf('IAccessible') -lt 0 -or
      $accessibility.IndexOf('reader_view_accessibility_invoke') -lt 0) {
    $failures.Add("lectern0 must retain its native adapter over shared semantic/action records")
  }
  if ($app.IndexOf('epub_reader_move_page') -lt 0 -or
      $app.IndexOf('doc_engine_get_author') -lt 0 -or
      $app.IndexOf('epub_reader_navigate_to_nav_point') -lt 0 -or
      $app.IndexOf('epub_reader_navigate_to_search_match') -lt 0) {
    $failures.Add("lectern0 must consume the concrete Reader0 page, metadata, and semantic navigation surface")
  }
  if ($app.IndexOf('epub_reader_prepare_navigation') -lt 0 -or
      $app.IndexOf('epub_reader_forward_page_range') -lt 0 -or
      $app.IndexOf('epub_reader_build_page_frame') -lt 0 -or
      $app.IndexOf('Lectern0AdjacentWarmPageCap = 4') -lt 0 -or
      $app.IndexOf('Lectern0PageRepeatInitialFrames = 24') -lt 0 -or
      $app.IndexOf('Lectern0PageRepeatIntervalFrames = 3') -lt 0 -or
      $app.IndexOf('if (app->page_repeat_active)') -lt 0) {
    $failures.Add("lectern0 must consume Reader0 API 5 preparation with four-page host warming, held-input deferral, and 24/3 coalesced repeat pacing")
  }
  if ($app.IndexOf('Lectern0AdjacentPagePixelCap = 4096 * 4096') -lt 0 -or
      $app.IndexOf('adjacent_page_pixels') -lt 0 -or
      $app.IndexOf('lectern0_build_adjacent_page_raster') -lt 0 -or
      $app.IndexOf('app->adjacent_warm_distance == 1') -lt 0) {
    $failures.Add("lectern0 must retain one bounded host-owned next-page raster while Reader0 owns navigation preparation")
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
  if ($app.IndexOf('--reader-image-fit-smoke') -lt 0 -or
      $app.IndexOf('row->visual_units ? row->visual_units : 18') -lt 0 -or
      $app.IndexOf('SourceReaderLayoutImagePlacement_ImageOnly') -lt 0 -or
      $app.IndexOf('draw_push_sprite_clipped_sampled') -lt 0 -or
      $app.IndexOf('DrawSpriteSampleKind_Nearest') -lt 0) {
    $failures.Add("lectern0 image-only pages must consume reader0 visual units and retain explicit frozen-nearest sampling evidence")
  }
  if ($app.IndexOf('#include "presentation_engine/presentation_engine.h"') -lt 0 -or
      $app.IndexOf('PRESENTATION_ENGINE_API_VERSION != 1') -lt 0 -or
      $app.IndexOf('lectern0_build_reader_presentation') -lt 0 -or
      $app.IndexOf('presentation_engine_block_flow_build') -lt 0) {
    $failures.Add("lectern0 must route canonical-row vertical geometry through Presentation Engine API 1")
  }
  if ($libraryHeader.IndexOf('Lectern0LibraryEntryCap = 512') -lt 0 -or
      $libraryHeader.IndexOf('Lectern0LibraryCatalogFileCap = 2 * 1024 * 1024') -lt 0 -or
      $libraryHeader.IndexOf('Lectern0LibraryDigest_None') -lt 0 -or
      $libraryHeader.IndexOf('Lectern0LibraryDigest_SHA256') -lt 0 -or
      $library.IndexOf('os_write_entire_file_atomic') -lt 0 -or
      $library.IndexOf('GetFullPathNameW') -lt 0 -or
      $library.IndexOf('lectern0_library_catalog_sort') -lt 0) {
    $failures.Add("Lectern0 must retain bounded atomic local catalog records, normalized paths, MRU ordering, and an algorithm-tagged future digest seam")
  }
  if ($app.IndexOf('lectern0_draw_library') -lt 0 -or
      $app.IndexOf('OFN_ALLOWMULTISELECT') -lt 0 -or
      $app.IndexOf('Lectern0HostControlAction_LocateBook') -lt 0 -or
      $app.IndexOf('Removed from library; source file was not deleted') -lt 0 -or
      $app.IndexOf('lectern0_close_book') -lt 0 -or
      $app.IndexOf('--library-smoke') -lt 0 -or
      $app.IndexOf('responsive=wide_and_compact') -lt 0) {
    $failures.Add("Lectern0 must retain its library-first shell, native import, missing-file recovery, source-preserving removal, Close Book return, and bounded evidence")
  }
  if (($libraryHeader + $library) -match '(?i)sqlite|event\s*bus|vtable|provider\s*table|dependency\s*injection') {
    $failures.Add("the local library core must not introduce a database or indirect provider/event architecture")
  }
  if ($app -match 'IWIC|CLSID_WIC|wincodec\.h') {
    $failures.Add("lectern0 must not duplicate the zero_foundation WIC backend")
  }
  if ($build -match '(?i)re10' -or
      $app -match '(?im)^\s*#\s*include[^\r\n]*re10' -or
      $app -match '(?i)\bre10_[A-Za-z0-9_]+\b') {
    $failures.Add("lectern0 source closure must not depend on re10")
  }
}

if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Error $_ }
  exit 1
}

Write-Host "lectern0 architecture audit: pass"
Write-Host "boundary: Lectern0 library shell/persistence + zero_foundation presentation/image/render + readerview0/UI0 open-book chrome + reader0 concrete EPUB core"

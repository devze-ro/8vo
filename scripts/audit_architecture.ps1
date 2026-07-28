param(
  [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$failures = [System.Collections.Generic.List[string]]::new()
$buildPath = Join-Path $RepoRoot "code\build.c"
$appPath = Join-Path $RepoRoot "code\eightvo.c"
$libraryHeaderPath = Join-Path $RepoRoot "code\eightvo_library.h"
$libraryPath = Join-Path $RepoRoot "code\eightvo_library.c"
$accessibilityPath = Join-Path $RepoRoot "code\platform\win32\eightvo_accessibility_win32.c"
$parityPath = Join-Path $RepoRoot "scripts\win32_reader_view_stage2b0_parity.ps1"
$repeatQueuePath = Join-Path $RepoRoot "scripts\win32_eightvo_page_repeat_queue_smoke.ps1"
$pageTurnPath = Join-Path $RepoRoot "scripts\win32_eightvo_page_turn_regression_smoke.ps1"
if (!(Test-Path -LiteralPath $buildPath)) { $failures.Add("missing code/build.c") }
if (!(Test-Path -LiteralPath $appPath)) { $failures.Add("missing code/eightvo.c") }
if (!(Test-Path -LiteralPath $libraryHeaderPath)) {
  $failures.Add("missing bounded Eightvo library records")
}
if (!(Test-Path -LiteralPath $libraryPath)) {
  $failures.Add("missing Eightvo library persistence implementation")
}
if (!(Test-Path -LiteralPath $accessibilityPath)) {
  $failures.Add("missing host-owned Win32 accessibility adapter")
}
if (!(Test-Path -LiteralPath $parityPath)) {
  $failures.Add("missing two-host Reader View parity runner")
}
if (!(Test-Path -LiteralPath $repeatQueuePath)) {
  $failures.Add("missing real Win32 page-repeat queue regression")
}
if (!(Test-Path -LiteralPath $pageTurnPath)) {
  $failures.Add("missing exact-book page-turn regression")
}

if ($failures.Count -eq 0) {
  $build = [System.IO.File]::ReadAllText($buildPath)
  $app = [System.IO.File]::ReadAllText($appPath)
  $libraryHeader = [System.IO.File]::ReadAllText($libraryHeaderPath)
  $library = [System.IO.File]::ReadAllText($libraryPath)
  $accessibility = [System.IO.File]::ReadAllText($accessibilityPath)
  $parity = [System.IO.File]::ReadAllText($parityPath)
  $repeatQueue = [System.IO.File]::ReadAllText($repeatQueuePath)
  $pageTurn = [System.IO.File]::ReadAllText($pageTurnPath)
  if ([regex]::Matches($build, '#include\s+"reader0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile reader0.c exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"ui0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile ui0.c exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"readerview0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile readerview0.c exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"eightvo_library\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile the host library implementation exactly once")
  }
  if ([regex]::Matches(
        $build,
        '#include\s+"platform/win32/eightvo_accessibility_win32\.c"').Count -ne 1) {
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
    $failures.Add("eightvo must consume the reader0 umbrella")
  }
  if ($app.IndexOf('READER0_API_VERSION != 5') -lt 0 -or
      $app.IndexOf('doc_engine_get_author') -lt 0) {
    $failures.Add("eightvo must consume Reader0 API 5 including author metadata")
  }
  if ($app.IndexOf('#include "ui0.h"') -lt 0) {
    $failures.Add("eightvo must consume the UI0 umbrella")
  }
  if ($app.IndexOf('UI0_API_VERSION != 91') -lt 0 -or
      $app.IndexOf('ui0_icon_rasterize_rgb32') -lt 0) {
    $failures.Add("eightvo must consume UI0 API 91 canonical reader icons")
  }
  if ($app.IndexOf('#include "readerview0.h"') -lt 0 -or
      $app.IndexOf('READERVIEW0_API_VERSION != 3') -lt 0) {
    $failures.Add("eightvo must consume Reader View API 3 through its umbrella")
  }
  if ($app.IndexOf('reader_view_build') -lt 0 -or
      $app.IndexOf('eightvo_prepare_reader_view_projection') -lt 0 -or
      $app.IndexOf('eightvo_apply_reader_view_actions') -lt 0) {
    $failures.Add("eightvo must project host data into Reader View API 3 and execute returned actions")
  }
  if ($app.IndexOf('.page_surface_rect = resolved_layout.page_surface_rect') -lt 0 -or
      $app.IndexOf('.content_rect = resolved_layout.content_rect') -lt 0 -or
      $app.IndexOf('ReaderViewContentGeometry reader_content_geometry') -lt 0 -or
      $app.IndexOf('host_toolbar_trailing_width = EightvoHostToolbarTrailingWidth') -lt 0) {
    $failures.Add("eightvo must adopt Reader View API 3 atomic layout geometry and retain the host toolbar slot")
  }
  if ($app.IndexOf('projection.chrome_title = eightvo_reader_view_text("Reader")') -lt 0 -or
      $app.IndexOf('EightvoToolbarHeight = 48') -ge 0 -or
      $app.IndexOf('EightvoFooterHeight = 36') -ge 0) {
    $failures.Add("eightvo must project the accepted title and must not retain independent 48/36 reader chrome")
  }
  if ($app.IndexOf('ReaderViewTextStyle_ChromeTitle') -lt 0 -or
      $app.IndexOf('ReaderViewTextStyle_ChromeMetadata') -lt 0 -or
      $app.IndexOf('ReaderViewTextStyle_MenuItem') -lt 0 -or
      $app.IndexOf('binding->style == ReaderViewTextStyle_ChromeTitle ? 2 : 1') -lt 0 -or
      $app.IndexOf('draw_push_text_box') -lt 0) {
    $failures.Add("eightvo must map every finite Reader View text binding through its frozen host text-box raster")
  }
  if ($app.IndexOf('eightvo_reader_filter_rasterize_rgb32') -lt 0 -or
      $app.IndexOf('768785035519145851ull') -lt 0) {
    $failures.Add("eightvo must preserve the frozen re10 SlidersVertical raster for the Reader View Filter intent")
  }
  if ($app.IndexOf('"%llu%%   Location %llu of %llu"') -lt 0 -or
      $app.IndexOf('"Page %llu of %llu"') -lt 0) {
    $failures.Add("eightvo must preserve the accepted Reader View progress-label policy")
  }
  if ($app.IndexOf('.kind = UI0ControlKind_IconButton') -lt 0 -or
      $app.IndexOf('ui0_draw_push_icon(&draw') -lt 0 -or
      $app.IndexOf('UI0IconKind_Close') -lt 0 -or
      $app.IndexOf('UI0Control_Quiet') -ge 0) {
    $failures.Add("eightvo Close Book must retain host interaction with the nonquiet UI0 IconButton shell and canonical Close icon")
  }
  if ($app.IndexOf('EightvoHostControl_CloseBook') -lt 0 -or
      $app.IndexOf('host_exit_pointer_armed') -lt 0 -or
      $app.IndexOf('eightvo_host_keyboard_tab') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_Find') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_Fullscreen') -lt 0 -or
      $app.IndexOf('Close Book') -lt 0 -or
      $accessibility.IndexOf('eightvo_accessibility_host_identity') -lt 0 -or
      $accessibility.IndexOf('eightvo_accessibility_host_insertion_shared_count') -lt 0 -or
      $app.IndexOf('order=find_close_fullscreen') -lt 0 -or
      $app.IndexOf('UI0DrawCommand commands[5]') -lt 0 -or
      $app.IndexOf('.clip_rect = app->reader_view_layout.host_toolbar_trailing_rect') -lt 0 -or
      $accessibility.IndexOf('eightvo_host_control_invoke') -lt 0) {
    $failures.Add("eightvo must expose and fully draw one host Close Book record between Find and Fullscreen for pointer, keyboard, focus-visible chrome, and native accessibility routing")
  }
  if ($app.IndexOf('focus=reference13') -lt 0 -or
      $app.IndexOf('panel_focus=toc_find_annotations_progress_boundary') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_panel_focus_regression') -lt 0 -or
      $app.IndexOf('keyboard_routing=focused_edit_or_activate') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_keyboard_input_routing_regression') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_text_editing') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_space_activates_focus') -lt 0 -or
      $app.IndexOf('find_shortcut=focused_input') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_open_find_from_shortcut') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_find_shortcut_focus_regression') -lt 0 -or
      $app.IndexOf('navigation_panels=space_toc_find') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_navigation_panel_interaction_regression') -lt 0 -or
      $app.IndexOf('gutters=boundary_roundtrip') -lt 0 -or
      $app.IndexOf('gutter_input=keyboard_pointer') -lt 0 -or
      $app.IndexOf('carets=frozen18x32') -lt 0 -or
      $app.IndexOf('UI0IconKind_PageCaretLeft') -lt 0 -or
      $app.IndexOf('UI0IconKind_PageCaretRight') -lt 0 -or
      $app.IndexOf('EightvoUI0IconRasterMaxWidth = UI0_ICON_RASTER_MAX_WIDTH') -lt 0 -or
      $app.IndexOf('UI0DrawOp_FocusRing') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_PreviousPage') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_NextPage') -lt 0) {
    $failures.Add("eightvo must lock the a6b combined focus order and exact keyboard/pointer page-gutter behavior")
  }
  if ($app.IndexOf('toc_identity=noncontiguous') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_covers_noncontiguous_toc_identity') -lt 0 -or
      $app.IndexOf('find_execution=commit_only') -lt 0 -or
      $app.IndexOf('find_clear=immediate') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_set_find_query') -lt 0 -or
      $app.IndexOf('find_edit_executed') -lt 0 -or
      $app.IndexOf('find_result_action') -lt 0) {
    $failures.Add("eightvo must preserve source TOC identity and frozen edit/commit/clear/result Find behavior")
  }
  if ($app.IndexOf('"%s - re10 loc %llu"') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_right_secondary') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_register_right_source') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_sort_right_candidates') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_covers_mixed_right_order') -lt 0 -or
      $app.IndexOf('ReaderViewRow_AttachedToPrevious') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_find_text_metrics') -lt 0 -or
      $app.IndexOf('find_metrics=bounded_values') -lt 0 -or
      $app.IndexOf('find_match=measured') -lt 0 -or
      $app.IndexOf('eightvo_draw_adapter_covers_measured_find_match') -lt 0 -or
      $app.IndexOf('eightvo_draw_adapter_covers_find_status_and_metadata') -lt 0 -or
      $app.IndexOf('font_text_baseline_y_in_rect') -lt 0 -or
      $app.IndexOf('ReaderViewNoteTextMetrics') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_note_text_metrics') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_covers_note_text_metrics') -lt 0 -or
      $app.IndexOf('ReaderViewTextStyle_NoteEditor') -lt 0 -or
      $app.IndexOf('EightvoReaderViewNotePixelHeight = 18') -lt 0 -or
      $app.IndexOf('command->typography_line_height') -lt 0 -or
      $app.IndexOf('note_metrics=bounded_values_18px') -lt 0 -or
      $app.IndexOf('annotations=reference_metadata') -lt 0 -or
      $app.IndexOf('bookmark_star=projected_remove_once') -lt 0 -or
      $app.IndexOf('note ? highlight->note : highlight->text') -lt 0 -or
      $app.IndexOf('.secondary = eightvo_reader_view_text(highlight->note)') -ge 0) {
    $failures.Add("eightvo must project frozen Highlight excerpts, Note bodies, kind/location metadata, and caller-measured 18px Note TextArea values while retaining host ownership")
  }
  if ($app.IndexOf('ReaderViewSemanticControl_RightFilter') -lt 0 -or
      $app.IndexOf('ReaderViewSemanticControl_RightFilterOption') -lt 0 -or
      $app.IndexOf('annotations-filter') -lt 0 -or
      $app.IndexOf('annotations_interaction=close_filter_edit_menu') -lt 0 -or
      $app.IndexOf('annotations_pointer=open_filter_escape_select_row_star_menu_note_lifecycle_close') -lt 0 -or
      $app.IndexOf('reader_view_close_note_editor') -lt 0 -or
      $app.IndexOf('ReaderViewAction_CancelNote') -lt 0 -or
      $app.IndexOf('note_lifecycle=acknowledged') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_annotation_interaction_regression') -lt 0 -or
      $app.IndexOf('eightvo_reader_view_annotation_pointer_regression') -lt 0) {
    $failures.Add("eightvo must retain semantic annotation close/filter/row-menu parity evidence")
  }
  if ($app.IndexOf('B32 is_highlight;') -lt 0 -or
      $app.IndexOf('EightvoAnnotationFileV2') -lt 0 -or
      $app.IndexOf('eightvo_migrate_highlight_v2') -lt 0 -or
      $app.IndexOf('file.version = 3') -lt 0 -or
      $app.IndexOf('eightvo_commit_annotations') -lt 0 -or
      $app.IndexOf('eightvo_remove_highlight_identity_at') -lt 0 -or
      $app.IndexOf('eightvo_delete_note_at_index') -lt 0 -or
      $app.IndexOf('eightvo_save_selection_note') -lt 0 -or
      $app.IndexOf('eightvo_toggle_highlight_star_at') -lt 0 -or
      $app.IndexOf('annotation_identity=v3_migrate_demote_restart') -lt 0 -or
      $app.IndexOf('note_persistence=atomic_rollback_open') -lt 0 -or
      $app.IndexOf('bookmark_persistence=rollback') -lt 0 -or
      $app.IndexOf('star_persistence=rollback') -lt 0) {
    $failures.Add("eightvo must retain V1/V2-to-V3 annotation identity migration, Note-only demotion, atomic mutation rollback, and restart evidence")
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
      $app.IndexOf('EightvoTheme_Count == 6') -lt 0 -or
      $app.IndexOf('file.version == 1') -lt 0 -or
      $app.IndexOf('.version = 3') -lt 0 -or
      $app.IndexOf('file.version < 3 ?') -lt 0) {
    $failures.Add("eightvo must expose all six shared themes with explicit legacy settings migration")
  }
  if ($app.IndexOf('eightvo_draw_adapter_covers_all_ops') -lt 0 -or
      $app.IndexOf('eightvo_draw_adapter_covers_reference_edges') -lt 0 -or
      $app.IndexOf('unsupported_count') -lt 0 -or
      $app.IndexOf('eightvo_draw_ui0_icon') -lt 0 -or
      $app.IndexOf('eightvo_draw_ui0_text') -lt 0) {
    $failures.Add("eightvo must render and test the complete UI0 draw-operation surface")
  }
  if ($app -match 'EightvoTheme_Sepia' -or
      $app -match 'reader_view_text_is\(text,\s*"Previous page"\)' -or
      $app -match 'reader_view_text_is\(text,\s*"Next page"\)') {
    $failures.Add("eightvo must not retain the old three-theme enum or ASCII reader-control substitutions")
  }
  if ($app.IndexOf('eightvo_save_settings') -lt 0 -or
      $app.IndexOf('eightvo_save_annotations') -lt 0 -or
      $app.IndexOf('eightvo_export_annotations') -lt 0) {
    $failures.Add("eightvo must retain settings and annotation persistence ownership")
  }
  if ($app.IndexOf('--reader-view-smoke') -lt 0) {
    $failures.Add("eightvo must retain deterministic Reader View API 3 action evidence")
  }
  if ($app.IndexOf('--reader-view-startup-interaction-smoke') -lt 0 -or
      $app.IndexOf('eightvo_host_pointer_press') -lt 0 -or
      $app.IndexOf('eightvo_host_pointer_release') -lt 0 -or
      $app.IndexOf('surface=library catalog=empty') -lt 0) {
    $failures.Add("eightvo must retain the empty-library native press/release Add EPUBs regression")
  }
  if ($app -match '(?s)draw_push_text_in_rect\([^;]+Open a book to begin reading\.') {
    $failures.Add("Reader View must be the single owner of empty/loading/error status painting")
  }
  if ($app.IndexOf('--accessibility-smoke') -lt 0 -or
      $app.IndexOf('WM_GETOBJECT') -lt 0 -or
      $accessibility.IndexOf('IAccessible') -lt 0 -or
      $accessibility.IndexOf('reader_view_accessibility_invoke') -lt 0) {
    $failures.Add("eightvo must retain its native adapter over shared semantic/action records")
  }
  if ($app.IndexOf('epub_reader_move_page') -lt 0 -or
      $app.IndexOf('doc_engine_get_author') -lt 0 -or
      $app.IndexOf('epub_reader_navigate_to_nav_point') -lt 0 -or
      $app.IndexOf('epub_reader_navigate_to_search_match') -lt 0) {
    $failures.Add("eightvo must consume the concrete Reader0 page, metadata, and semantic navigation surface")
  }
  if ($app.IndexOf('epub_reader_prepare_navigation') -lt 0 -or
      $app.IndexOf('epub_reader_forward_page_range') -lt 0 -or
      $app.IndexOf('epub_reader_build_page_frame') -lt 0 -or
      $app.IndexOf('EightvoAdjacentWarmPageCap = 4') -lt 0 -or
      $app.IndexOf('adjacent_warm_direction') -lt 0) {
    $failures.Add("eightvo must consume Reader0 API 5 navigation preparation with bounded four-page host warming")
  }
  $timerResolutionBegins =
    [regex]::Matches($app, 'timeBeginPeriod\s*\(\s*1\s*\)').Count
  $timerResolutionEnds =
    [regex]::Matches($app, 'timeEndPeriod\s*\(\s*1\s*\)').Count
  if ($app.IndexOf('EightvoPageRepeatFrameRate = 60') -lt 0 -or
      $app.IndexOf('EightvoPageRepeatInitialFrames = 24') -lt 0 -or
      $app.IndexOf('EightvoPageRepeatIntervalFrames = 3') -lt 0 -or
      $app.IndexOf('eightvo_page_repeat_delay_ticks') -lt 0 -or
      $app.IndexOf('page_repeat_next_move_ticks') -lt 0 -or
      $app.IndexOf('page_repeat_last_action_emitted_ticks') -lt 0 -or
      $app.IndexOf('page_repeat_next_frame_count') -ge 0 -or
      $timerResolutionBegins -ne 1 -or
      $timerResolutionEnds -ne 2 -or
      $app.IndexOf('else if (!win32->app.page_repeat_active && timer_resolution_active)') -lt 0 -or
      $app.IndexOf('EightvoPageRepeatTimerId') -ge 0) {
    $failures.Add("eightvo held navigation must retain bounded wall-clock 60 Hz 24/3 policy, hold-scoped timer resolution with transition and exit cleanup, no presentation-frame clock, and no WM_TIMER repeat driver")
  }
  if ($app.IndexOf('EightvoAdjacentPagePixelCap = 4096 * 4096') -lt 0 -or
      $app.IndexOf('adjacent_page_pixels') -lt 0 -or
      $app.IndexOf('eightvo_build_adjacent_page_raster') -lt 0 -or
      $app.IndexOf('app->adjacent_warm_distance == 1') -lt 0) {
    $failures.Add("eightvo must retain one bounded host-owned next-page raster while Reader0 owns navigation preparation")
  }
  if ($repeatQueue.IndexOf('--page-repeat-win32-smoke') -lt 0 -or
      $repeatQueue.IndexOf('directions=2/2') -lt 0 -or
      $repeatQueue.IndexOf('reversal=forward_then_backward') -lt 0 -or
      $repeatQueue.IndexOf('returned_to_anchor=1/1') -lt 0 -or
      $repeatQueue.IndexOf('cross_spine_directions=2/2') -lt 0 -or
      $repeatQueue.IndexOf('cross_spine_transitions=') -lt 0 -or
      $repeatQueue.IndexOf('schedule=wall_clock_rebased_no_catch_up') -lt 0 -or
      $repeatQueue.IndexOf('repeat_moves=12+12') -lt 0 -or
      $repeatQueue.IndexOf('presented_frames=13+13') -lt 0 -or
      $repeatQueue.IndexOf('idle_presentations=0/0') -lt 0 -or
      $repeatQueue.IndexOf('canonical_pages=26/26') -lt 0 -or
      $repeatQueue.IndexOf('canonical_nonempty_frames=26/26') -lt 0 -or
      $repeatQueue.IndexOf('zero_pages_or_frames=0/0') -lt 0 -or
      $repeatQueue.IndexOf('orphan_text_pages=0/0') -lt 0 -or
      $repeatQueue.IndexOf('invalid_word_start_pages=0/0') -lt 0 -or
      $repeatQueue.IndexOf('boundary_oracle=raw_spine_utf8_word_start') -lt 0 -or
      $repeatQueue.IndexOf('gotm_prose_scope=active_spine_text_ge_128') -lt 0 -or
      $repeatQueue.IndexOf('queue_range_oracle=self_derived_order_only') -lt 0 -or
      $repeatQueue.IndexOf('independent_range_oracle=external_frozen_re10_required_not_evaluated') -lt 0 -or
      $repeatQueue.IndexOf('gotm_minimum_text_bytes') -lt 0 -or
      $repeatQueue.IndexOf('gotm_minimum_text_rows') -lt 0 -or
      $repeatQueue.IndexOf('action_presentations=26/26') -lt 0 -or
      $repeatQueue.IndexOf('action_overlap=0/0') -lt 0 -or
      $repeatQueue.IndexOf('native_repeats=208/208') -lt 0 -or
      $repeatQueue.IndexOf('navigation_prepare_cross_spine_ready=1+2') -lt 0 -or
      $repeatQueue.IndexOf('navigation_prepare_failures=0+0') -lt 0 -or
      $app.IndexOf('forward.navigation_prepare_cross_spine_ready_count == 1') -lt 0 -or
      $app.IndexOf('backward.navigation_prepare_cross_spine_ready_count == 2') -lt 0 -or
      $repeatQueue.IndexOf('prepared_window_moves=') -lt 0 -or
      $repeatQueue.IndexOf('synchronous_window_rebuild_moves=0+0') -lt 0 -or
      $repeatQueue.IndexOf('synchronous_adjacent_measured_moves=0+0') -lt 0 -or
      $repeatQueue.IndexOf('interval_samples=22/22') -lt 0 -or
      $repeatQueue.IndexOf('action_timing_samples=24/24') -lt 0 -or
      $repeatQueue.IndexOf('stable_presentations=26/26') -lt 0 -or
      $repeatQueue.IndexOf('visible_interval_samples=22/22') -lt 0 -or
      $repeatQueue.IndexOf('queue_drain_batch_max') -lt 0 -or
      $repeatQueue.IndexOf('control_cancel=1/1') -lt 0 -or
      $repeatQueue.IndexOf('shift_cancel=1/1') -lt 0 -or
      $repeatQueue.IndexOf('system_modifier_cancel=1/1') -lt 0 -or
      $repeatQueue.IndexOf('modifier_cancel=3/3') -lt 0 -or
      $repeatQueue.IndexOf('cancelled_repeats=5/5') -lt 0 -or
      $repeatQueue.IndexOf('mutation_gate_drops=7/7') -lt 0 -or
      $repeatQueue.IndexOf('mutation_cancel=1/1') -lt 0 -or
      $repeatQueue.IndexOf('capture_failure_page_recovery=1/1') -lt 0 -or
      $repeatQueue.IndexOf('capture_failure_same_page_freshness=1/1') -lt 0 -or
      $repeatQueue.IndexOf('capture_failure_open_catalog_recovery=1/1') -lt 0 -or
      $repeatQueue.IndexOf('image_page_gate=4/4') -lt 0 -or
      $repeatQueue.IndexOf('persistence_deferred=24/24') -lt 0 -or
      $repeatQueue.IndexOf('persistence_rescheduled=8/8') -lt 0 -or
      $repeatQueue.IndexOf('persistence_transactions=8/8') -lt 0 -or
      $repeatQueue.IndexOf('persistence_hold_state_catalog_unchanged=8/8') -lt 0 -or
      $repeatQueue.IndexOf('persistence_post_stop_state_catalog_advanced=8/8') -lt 0 -or
      $repeatQueue.IndexOf('persistence_cleanup=1/1') -lt 0 -or
      $repeatQueue.IndexOf('auxiliary_paint_dispatched=2/2') -lt 0 -or
      $repeatQueue.IndexOf('main_null_paint_dispatched=2/2') -lt 0 -or
      $repeatQueue.IndexOf('paint=real_queue_dispatch') -lt 0 -or
      $repeatQueue.IndexOf('nominal_first_move_ms=400.000') -lt 0 -or
      $repeatQueue.IndexOf('nominal_move_interval_ms=50.000') -lt 0 -or
      $repeatQueue.IndexOf('nominal_elapsed_ms=950.000') -lt 0 -or
      $repeatQueue.IndexOf('forward_interval_min_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_interval_avg_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_interval_max_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_interval_min_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_interval_avg_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_interval_max_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_immediate_visible_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_visible_first_delay_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_visible_interval_min_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_visible_interval_avg_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_visible_interval_max_ms') -lt 0 -or
      $repeatQueue.IndexOf('forward_visible_elapsed_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_immediate_visible_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_visible_first_delay_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_visible_interval_min_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_visible_interval_avg_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_visible_interval_max_ms') -lt 0 -or
      $repeatQueue.IndexOf('backward_visible_elapsed_ms') -lt 0 -or
      $repeatQueue.IndexOf('interval_tolerance_ms=34') -lt 0 -or
      $repeatQueue.IndexOf('timing_tolerance_ms=200') -lt 0 -or
      $repeatQueue.IndexOf('frame_budget_ms=64') -lt 0 -or
      $repeatQueue.IndexOf('move_prepare_budget_ms=16.667') -lt 0 -or
      $repeatQueue.IndexOf('render_budget_ms=48.000') -lt 0 -or
      $repeatQueue.IndexOf('present_budget_ms=16.667') -lt 0 -or
      $repeatQueue.IndexOf('$First = Invoke-QueueSmoke "run_1"') -lt 0 -or
      $repeatQueue.IndexOf('$Second = Invoke-QueueSmoke "run_2"') -lt 0) {
    $failures.Add("Eightvo must lock the real Win32 60 Hz 24/3 queue cadence twice")
  }
  if ($app.IndexOf('EightvoPageRepeatProbeMoveCount = 12') -lt 0 -or
      $app.IndexOf('EightvoGotmMinimumProseSpineBytes = 128') -lt 0 -or
      $app.IndexOf('EightvoGotmMinimumProseTextBytes = 8') -lt 0 -or
      $app.IndexOf('EightvoGotmMinimumProseTextRows = 1') -lt 0 -or
      $app.IndexOf('EightvoPresentationRetryTimerId = 3') -lt 0 -or
      $app.IndexOf('eightvo_page_action_schedule_presentation_retry') -lt 0 -or
      $app.IndexOf('page_action_waiting_for_present') -lt 0 -or
      $app.IndexOf('page_action_pending') -lt 0 -or
      $app.IndexOf('EightvoPageRepeatWin32Probe_ShiftModifier') -lt 0 -or
      $app.IndexOf('eightvo_gotm_navigation_frame_is_canonical_nonempty') -lt 0 -or
      $app.IndexOf('eightvo_gotm_page_start_is_word_boundary') -lt 0 -or
      $app.IndexOf('EightvoPageRepeatQueueDrainCap = 32') -lt 0 -or
      $app.IndexOf('EightvoPageRepeatProbeQueueDrainCap = EightvoPageRepeatQueueDrainCap') -lt 0 -or
      $app.IndexOf('drained_message_count >= EightvoPageRepeatQueueDrainCap') -lt 0 -or
      $app.IndexOf('state_save_transaction_success_count') -lt 0 -or
      $app.IndexOf('eightvo_page_repeat_probe_file_snapshot') -lt 0 -or
      $app.IndexOf('eightvo_page_repeat_probe_paint_proc') -lt 0) {
    $failures.Add("Eightvo page-repeat proof must cover sustained moves, bounded queue drain, a paired host save with individually atomic state/catalog files, and real paint dispatch")
  }
  if ($pageTurn.IndexOf('invalid_word_start_pages=0/0') -lt 0 -or
      $pageTurn.IndexOf('direct_traversal=64+64_exact') -lt 0 -or
      $pageTurn.IndexOf('boundary_oracle=raw_spine_utf8_word_start') -lt 0 -or
      $pageTurn.IndexOf('gotm_prose_scope=active_spine_text_ge_128') -lt 0 -or
      $pageTurn.IndexOf('boundary_oracle_self_test=1/1') -lt 0 -or
      $pageTurn.IndexOf('deferred_reversal_keyup=1/1') -lt 0 -or
      $pageTurn.IndexOf('gotm_minimum_text_bytes') -lt 0 -or
      $pageTurn.IndexOf('gotm_minimum_text_rows') -lt 0) {
    $failures.Add("Eightvo exact-book page-turn proof must lock non-orphan prose, raw UTF-8 word starts, and deferred reversal key-up semantics")
  }
  if ($app.IndexOf('font_cache_tag_from_provider') -lt 0 -or
      $app.IndexOf('eightvo_push_reader_text_chunks') -lt 0 -or
      $app.IndexOf('DrawTextFlag_Shaped') -lt 0) {
    $failures.Add("eightvo must chunk text through the reader-resolved provider and shaped text mode")
  }
  if ($app.IndexOf('eightvo_frame_text_rows_are_complete') -lt 0 -or
      $app.IndexOf('--render-smoke') -lt 0) {
    $failures.Add("eightvo must retain deterministic canonical-frame visual evidence")
  }
  if ($app.IndexOf('os_image_decode') -lt 0 -or
      $app.IndexOf('EightvoImageCache') -lt 0 -or
      $app.IndexOf('draw_push_sprite_clipped') -lt 0) {
    $failures.Add("eightvo must own cache policy while using shared decode and sprite presentation")
  }
  if ($app.IndexOf('--reader-image-fit-smoke') -lt 0 -or
      $app.IndexOf('row->visual_units ? row->visual_units : 18') -lt 0 -or
      $app.IndexOf('SourceReaderLayoutImagePlacement_ImageOnly') -lt 0 -or
      $app.IndexOf('draw_push_sprite_clipped_sampled') -lt 0 -or
      $app.IndexOf('eightvo_image_cache_prepare') -lt 0 -or
      $app.IndexOf('render_resample_bgra8') -lt 0 -or
      $app.IndexOf('DrawSpriteSampleKind_Area') -lt 0) {
    $failures.Add("eightvo image pages must consume reader0 visual units and retain bounded ground0 area-prepared sampling evidence")
  }
  if ($app.IndexOf('#include "presentation_engine/presentation_engine.h"') -lt 0 -or
      $app.IndexOf('PRESENTATION_ENGINE_API_VERSION != 1') -lt 0 -or
      $app.IndexOf('eightvo_build_reader_presentation') -lt 0 -or
      $app.IndexOf('presentation_engine_block_flow_build') -lt 0) {
    $failures.Add("eightvo must route canonical-row vertical geometry through Presentation Engine API 1")
  }
  if ($libraryHeader.IndexOf('EightvoLibraryEntryCap = 512') -lt 0 -or
      $libraryHeader.IndexOf('EightvoLibraryCatalogFileCap = 2 * 1024 * 1024') -lt 0 -or
      $libraryHeader.IndexOf('EightvoLibraryDigest_None') -lt 0 -or
      $libraryHeader.IndexOf('EightvoLibraryDigest_SHA256') -lt 0 -or
      $library.IndexOf('os_write_entire_file_atomic') -lt 0 -or
      $library.IndexOf('GetFullPathNameW') -lt 0 -or
      $library.IndexOf('eightvo_library_catalog_sort') -lt 0) {
    $failures.Add("Eightvo must retain bounded atomic local catalog records, normalized paths, MRU ordering, and an algorithm-tagged future digest seam")
  }
  if ($app.IndexOf('eightvo_draw_library') -lt 0 -or
      $app.IndexOf('OFN_ALLOWMULTISELECT') -lt 0 -or
      $app.IndexOf('EightvoHostControlAction_LocateBook') -lt 0 -or
      $app.IndexOf('Removed from library; source file was not deleted') -lt 0 -or
      $app.IndexOf('eightvo_close_book') -lt 0 -or
      $app.IndexOf('--library-smoke') -lt 0 -or
      $app.IndexOf('responsive=wide_and_compact') -lt 0) {
    $failures.Add("Eightvo must retain its library-first shell, native import, missing-file recovery, source-preserving removal, Close Book return, and bounded evidence")
  }
  if (($libraryHeader + $library) -match '(?i)sqlite|event\s*bus|vtable|provider\s*table|dependency\s*injection') {
    $failures.Add("the local library core must not introduce a database or indirect provider/event architecture")
  }
  if ($app -match 'IWIC|CLSID_WIC|wincodec\.h') {
    $failures.Add("eightvo must not duplicate the ground0 WIC backend")
  }
  if ($build -match '(?i)re10' -or
      $app -match '(?im)^\s*#\s*include[^\r\n]*re10' -or
      $app -match '(?i)\bre10_[A-Za-z0-9_]+\b') {
    $failures.Add("eightvo source closure must not depend on re10")
  }
}

if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Error $_ }
  exit 1
}

Write-Host "eightvo architecture audit: pass"
Write-Host "boundary: Eightvo library shell/persistence + ground0 presentation/image/render + readerview0/UI0 open-book chrome + reader0 concrete EPUB core"

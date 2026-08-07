#include "octavo_version.h"
#include "octavo_reader_justification.h"
#include "foundation/version.h"
#include "reader0.h"
#include "readerview0.h"
#include "ui0.h"

#include <android/input.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define OCTAVO_ANDROID_PATH_CAPACITY 4096u
#define OCTAVO_ANDROID_TITLE_CAPACITY 256u
#define OCTAVO_ANDROID_PROGRESS_CAPACITY 128u
#define OCTAVO_ANDROID_STATE_FIELD_COUNT 96
#define OCTAVO_ANDROID_TYPOGRAPHY_MAGIC 0x4F545950
#define OCTAVO_ANDROID_TYPOGRAPHY_VERSION 2
#define OCTAVO_ANDROID_TYPOGRAPHY_FIRST_CODEPOINT 32
#define OCTAVO_ANDROID_TYPOGRAPHY_ASCII_LAST 126
#define OCTAVO_ANDROID_TYPOGRAPHY_LATIN1_FIRST 160
#define OCTAVO_ANDROID_TYPOGRAPHY_LATIN1_LAST 255
#define OCTAVO_ANDROID_TYPOGRAPHY_ASCII_COUNT 95
#define OCTAVO_ANDROID_TYPOGRAPHY_GLYPH_CAP 256
#define OCTAVO_ANDROID_TYPOGRAPHY_STYLE_COUNT 4
#define OCTAVO_ANDROID_TYPOGRAPHY_HEADER_COUNT 18
#define OCTAVO_ANDROID_APPEARANCE_MAGIC 0x4F375041
#define OCTAVO_ANDROID_APPEARANCE_VERSION 1
#define OCTAVO_ANDROID_APPEARANCE_CONFIG_COUNT 11
/* Port 7 appearance state remains owned by the Android host. */
#define OCTAVO_ANDROID_SEMANTIC_SNAPSHOT_VERSION 1
#define OCTAVO_ANDROID_SEMANTIC_SNAPSHOT_HEADER_COUNT 3
#define OCTAVO_ANDROID_SEMANTIC_SNAPSHOT_STRIDE 11
#define OCTAVO_ANDROID_FRAME_IMAGE_PACKET_VERSION 1
#define OCTAVO_ANDROID_FRAME_IMAGE_PACKET_HEADER_COUNT 2
#define OCTAVO_ANDROID_FRAME_IMAGE_PACKET_ROW_STRIDE 4
#define OCTAVO_ANDROID_PREPARED_FRAME_STATE_VERSION 1
#define OCTAVO_ANDROID_PREPARED_FRAME_STATE_FIELD_COUNT 26
#define OCTAVO_ANDROID_UI0_SNAPSHOT_MAGIC 0x4F553941
#define OCTAVO_ANDROID_UI0_SNAPSHOT_VERSION 1
#define OCTAVO_ANDROID_NAVIGATION_KIND_SEARCH 7
#if UI0_API_VERSION != 91
#error Android_Port_8_UI0_snapshot_requires_API_91
#endif
typedef char OctavoAndroidUi0ColorRoleCountGuard[
  UI0ColorRole_Count == 26 ? 1 : -1];
typedef char OctavoAndroidUi0SpacingRoleCountGuard[
  UI0SpacingRole_Count == 8 ? 1 : -1];
typedef char OctavoAndroidUi0RadiusRoleCountGuard[
  UI0RadiusRole_Count == 7 ? 1 : -1];
typedef char OctavoAndroidUi0TypographyRoleCountGuard[
  UI0TypographyRole_Count == 8 ? 1 : -1];
typedef char OctavoAndroidUi0DensityRoleCountGuard[
  UI0DensityRole_Count == 6 ? 1 : -1];
typedef char OctavoAndroidUi0StateRoleCountGuard[
  UI0StateRole_Count == 7 ? 1 : -1];
typedef char OctavoAndroidUi0DrawStateCountGuard[
  UI0DrawState_Count == 7 ? 1 : -1];
typedef char OctavoAndroidUi0ColorWidthGuard[
  sizeof(jint) == sizeof(UI0Color) ? 1 : -1];

enum
{
  OCTAVO_ANDROID_UI0_SNAPSHOT_HEADER_COUNT = 20,
  OCTAVO_ANDROID_UI0_SNAPSHOT_TYPOGRAPHY_STRIDE = 2,
  OCTAVO_ANDROID_UI0_SNAPSHOT_STATE_STRIDE = 3,
  OCTAVO_ANDROID_UI0_SNAPSHOT_DRAW_STRIDE = 3,
  OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_COUNT = 10,
  OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_COUNT = 9,
  OCTAVO_ANDROID_UI0_SNAPSHOT_SUMMARY_COUNT = 4,
  OCTAVO_ANDROID_UI0_SNAPSHOT_TEXT_INPUT_COUNT = 6,
  OCTAVO_ANDROID_UI0_SNAPSHOT_COLOR_OFFSET =
    OCTAVO_ANDROID_UI0_SNAPSHOT_HEADER_COUNT,
  OCTAVO_ANDROID_UI0_SNAPSHOT_SPACING_OFFSET =
    OCTAVO_ANDROID_UI0_SNAPSHOT_COLOR_OFFSET + UI0ColorRole_Count,
  OCTAVO_ANDROID_UI0_SNAPSHOT_RADIUS_OFFSET =
    OCTAVO_ANDROID_UI0_SNAPSHOT_SPACING_OFFSET + UI0SpacingRole_Count,
  OCTAVO_ANDROID_UI0_SNAPSHOT_TYPOGRAPHY_OFFSET =
    OCTAVO_ANDROID_UI0_SNAPSHOT_RADIUS_OFFSET + UI0RadiusRole_Count,
  OCTAVO_ANDROID_UI0_SNAPSHOT_DENSITY_OFFSET =
    OCTAVO_ANDROID_UI0_SNAPSHOT_TYPOGRAPHY_OFFSET +
    UI0TypographyRole_Count * OCTAVO_ANDROID_UI0_SNAPSHOT_TYPOGRAPHY_STRIDE,
  OCTAVO_ANDROID_UI0_SNAPSHOT_STATE_OFFSET =
    OCTAVO_ANDROID_UI0_SNAPSHOT_DENSITY_OFFSET + UI0DensityRole_Count,
  OCTAVO_ANDROID_UI0_SNAPSHOT_DRAW_OFFSET =
    OCTAVO_ANDROID_UI0_SNAPSHOT_STATE_OFFSET +
    UI0StateRole_Count * OCTAVO_ANDROID_UI0_SNAPSHOT_STATE_STRIDE,
  OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_OFFSET =
    OCTAVO_ANDROID_UI0_SNAPSHOT_DRAW_OFFSET +
    UI0DrawState_Count * OCTAVO_ANDROID_UI0_SNAPSHOT_DRAW_STRIDE,
  OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_OFFSET =
    OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_OFFSET +
    OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_COUNT,
  OCTAVO_ANDROID_UI0_SNAPSHOT_SUMMARY_OFFSET =
    OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_OFFSET +
    OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_COUNT,
  OCTAVO_ANDROID_UI0_SNAPSHOT_TEXT_INPUT_OFFSET =
    OCTAVO_ANDROID_UI0_SNAPSHOT_SUMMARY_OFFSET +
    OCTAVO_ANDROID_UI0_SNAPSHOT_SUMMARY_COUNT,
  OCTAVO_ANDROID_UI0_SNAPSHOT_PACKET_COUNT =
    OCTAVO_ANDROID_UI0_SNAPSHOT_TEXT_INPUT_OFFSET +
    OCTAVO_ANDROID_UI0_SNAPSHOT_TEXT_INPUT_COUNT,
};
typedef char OctavoAndroidUi0PacketCountGuard[
  OCTAVO_ANDROID_UI0_SNAPSHOT_PACKET_COUNT == 154 ? 1 : -1];

enum
{
  OCTAVO_ANDROID_IMAGE_CACHE_CAP = 32,
  OCTAVO_ANDROID_IMAGE_MAX_DIMENSION = 4096,
  OCTAVO_ANDROID_IMAGE_MAX_PIXEL_COUNT = 8 * 1024 * 1024,
  OCTAVO_ANDROID_IMAGE_PIXEL_BUDGET = 32 * 1024 * 1024,
  OCTAVO_ANDROID_IMAGE_ENCODED_BUDGET = 16 * 1024 * 1024,
};


enum
{
  OCTAVO_ANDROID_TEXT_SCALE = 3,
  OCTAVO_ANDROID_DEFAULT_BACKGROUND = 0xFFF7F3EAu,
  OCTAVO_ANDROID_TAP_MAX_DURATION_MILLIS = 500,
  OCTAVO_ANDROID_TAP_SLOP_PIXELS = 24,
  OCTAVO_ANDROID_TOUCH_HANDLED = 1u << 0,
  OCTAVO_ANDROID_TOUCH_PRESENT_REQUESTED = 1u << 1,
  OCTAVO_ANDROID_TOUCH_CHROME_REQUESTED = 1u << 2,
  OCTAVO_ANDROID_TOUCH_ZONE_INVALID = 2,
  OCTAVO_ANDROID_HOST_OWNS_READER_CHROME = 1,
};

enum
{
  OCTAVO_ANDROID_THEME_PAPER = 0,
  OCTAVO_ANDROID_THEME_SEPIA = 1,
  OCTAVO_ANDROID_THEME_DUSK = 2,
  OCTAVO_ANDROID_THEME_WARM_DARK = 3,
  OCTAVO_ANDROID_THEME_OLED = 4,
  OCTAVO_ANDROID_THEME_HIGH_CONTRAST = 5,
  OCTAVO_ANDROID_THEME_COUNT = 6,
};

typedef struct OctavoAndroidAppearance
{
  int32_t theme;
  int32_t font_family;
  int32_t font_size_sp;
  int32_t line_spacing_permille;
  int32_t margin;
  int32_t content_width_permille;
  int32_t alignment;
  int32_t publisher_colors;
  int32_t reduced_motion;
  UI0Color colors[UI0ColorRole_Count];
  uint64_t color_hash;
} OctavoAndroidAppearance;

typedef struct OctavoAndroidPixels
{
  uint8_t *data;
  int32_t width;
  int32_t height;
  int32_t stride;
} OctavoAndroidPixels;

typedef struct OctavoAndroidTypography
{
  int32_t first_codepoint;
  int32_t glyph_count;
  int32_t style_count;
  int32_t column_count;
  int32_t rows_per_style;
  int32_t cell_width;
  int32_t cell_height;
  int32_t atlas_width;
  int32_t atlas_height;
  int32_t atlas_stride;
  int32_t text_px;
  int32_t ascent_px;
  int32_t descent_px;
  int32_t line_advance_px;
  int32_t origin_x;
  int32_t baseline_y;
  uint32_t codepoints[OCTAVO_ANDROID_TYPOGRAPHY_GLYPH_CAP];
  int32_t advances[OCTAVO_ANDROID_TYPOGRAPHY_STYLE_COUNT]
                  [OCTAVO_ANDROID_TYPOGRAPHY_GLYPH_CAP];
  uint8_t *alpha;
  size_t alpha_size;
  uint64_t rasterized_glyph_count;
  uint64_t missing_glyph_count;
  uint64_t rasterized_style_count[OCTAVO_ANDROID_TYPOGRAPHY_STYLE_COUNT];
  int ready;
} OctavoAndroidTypography;

typedef struct OctavoAndroidJustificationEvidence
{
  uint64_t plan_count;
  uint64_t active_row_count;
  uint64_t applied_extra_px;
  uint64_t semantic_hash;
} OctavoAndroidJustificationEvidence;
typedef struct OctavoAndroidImageCacheEntry
{
  U32 resource_index;
  EpubReaderFrameImageStatus status;
  U32 *pixels;
  S32 width;
  S32 height;
  S32 stride_pixels;
  U64 pixel_bytes;
  U64 last_use_serial;
} OctavoAndroidImageCacheEntry;

typedef struct OctavoAndroidPreparedStaticFrame
{
  B32 valid;
  ANativeWindow *window;
  U64 mutation_generation;
  U64 build_serial;
  U64 lifecycle_generation;
  U64 surface_generation;
  S32 width;
  S32 height;
  EpubReaderLayoutKey layout_key;
  DocDocumentId document_id;
  U32 active_spine_index;
  U64 view_byte_offset;
  B32 has_current_page;
  U32 current_page_spine_index;
  U64 current_page_first_byte;
  U64 current_page_one_past_last_byte;
  U32 frame_spine_index;
  U64 frame_view_byte_offset;
  DocDocumentId location_document_id;
  U32 location_spine_count;
  U32 location_next_spine_index;
  U64 location_total_text_bytes;
  B32 location_cache_complete;
  B32 location_cache_valid;
} OctavoAndroidPreparedStaticFrame;

typedef struct OctavoAndroidSearchState
{
  char query[EPUB_READER_SEARCH_QUERY_CAP];
  U64 query_size;
  EpubReaderSearchMatch matches[EPUB_READER_SEARCH_MATCH_CAP];
  U32 match_count;
  U32 total_count;
  B32 has_more;
  U32 active_index;
  B32 has_active;
} OctavoAndroidSearchState;

typedef struct OctavoAndroidSelectionRowGeometry
{
  const EpubReaderFrameStyleRow *row;
  U32 start;
  U32 end;
  S32 x;
  S32 y;
  S32 height;
  S32 next_y;
  OctavoReaderJustificationPlan justification;
  B32 valid;
} OctavoAndroidSelectionRowGeometry;

typedef struct OctavoAndroidApp
{
  ANativeWindow *window;
  char files_path[OCTAVO_ANDROID_PATH_CAPACITY];
  char cache_path[OCTAVO_ANDROID_PATH_CAPACITY];
  char document_path[OCTAVO_ANDROID_PATH_CAPACITY];
  char document_title[OCTAVO_ANDROID_TITLE_CAPACITY];
  char progress_label[OCTAVO_ANDROID_PROGRESS_CAPACITY];
  OctavoAndroidTypography typography;
  OctavoAndroidAppearance appearance;

  OctavoAndroidImageCacheEntry
    image_cache[OCTAVO_ANDROID_IMAGE_CACHE_CAP];
  U32 image_cache_count;
  U64 image_cache_pixel_bytes;
  U64 image_cache_use_serial;
  OctavoAndroidPreparedStaticFrame prepared_static_frame;
  U64 prepared_static_frame_mutation_generation;
  U64 prepared_static_frame_build_serial;
  U64 prepared_static_frame_invalidation_count;
  U64 prepared_static_frame_build_attempt_count;
  U64 prepared_static_frame_build_success_count;
  U64 prepared_static_frame_snapshot_reuse_count;
  U64 prepared_static_frame_present_reuse_count;
  U64 prepared_static_frame_stale_reject_count;
  U64 prepared_static_frame_consume_count;
  Arena *arena;
  EpubReader reader;
  EpubReaderFrameStorage reader_frame_storage;
  EpubReaderFrame reader_frame;
  EpubReaderLayoutKey layout_key;
  SourceReaderLayoutConfig layout_config;
  B32 reader_initialized;
  U32 resume_spine_index;
  U64 resume_byte_offset;
  B32 restore_requested;
  B32 restore_attempted;
  B32 restore_succeeded;
  uint64_t restore_failure_count;
  uint64_t document_open_success_count;
  uint64_t document_open_failure_count;

  ReaderViewState reader_view_state;
  ReaderViewLayout reader_view_layout;
  ReaderViewProjection reader_view_projection;
  ReaderViewFrameStorage reader_view_storage;
  ReaderViewFrame reader_view_frame;
  UI0ResolvedTheme reader_view_theme;
  UI0ResolvedTheme presented_reader_view_theme;
  B32 presented_reader_view_theme_valid;
  UI0U64 reader_view_frame_index;
  B32 reader_view_ready;
  S32 pagination_content_width;
  S32 pagination_content_height;
  B32 pagination_dirty;

  U32 presented_anchor_spine_index;
  U64 presented_anchor_byte_offset;
  B32 presented_anchor_valid;
  U32 reflow_anchor_spine_index;
  U64 reflow_anchor_byte_offset;
  B32 reflow_waiting_for_present;
  B32 appearance_waiting_for_present;
  B32 host_frame_waiting_for_present;
  DocSelection selection_mutation_previous;
  B32 selection_mutation_previous_valid;
  B32 selection_mutation_waiting_for_present;
  SourceReaderPageRange selection_page_turn_previous_page;
  U32 selection_page_turn_previous_spine_index;
  U64 selection_page_turn_previous_byte_offset;
  S32 selection_page_turn_direction;
  B32 selection_page_turn_waiting_for_present;
  UI0U64 selection_generation;
  UI0U64 selection_presented_generation;
  U64 selection_failure_count;
  S32 selection_handle_radius_px;
  OctavoAndroidSearchState search_mutation_previous;
  UI0U64 search_mutation_generation;
  UI0U64 search_mutation_presented_generation;
  B32 search_mutation_waiting_for_present;
  UI0U64 appearance_generation;
  UI0U64 appearance_presented_generation;
  uint64_t appearance_apply_count;
  uint64_t appearance_gate_block_count;
  uint64_t appearance_failure_count;
  uint64_t reflow_request_count;
  uint64_t reflow_success_count;
  uint64_t reflow_failure_count;
  uint64_t accessibility_action_count;
  uint64_t chrome_toggle_count;
  OctavoAndroidJustificationEvidence justification_evidence;
  int chrome_visible;

  int32_t format;
  int32_t width;
  int32_t height;
  int32_t inset_left;
  int32_t inset_top;
  int32_t inset_right;
  int32_t inset_bottom;
  int32_t reader_chrome_inset_top;
  int32_t reader_chrome_inset_bottom;
  int resumed;
  uint64_t surface_generation;
  uint64_t surface_destroy_count;
  uint64_t resume_count;
  uint64_t pause_count;
  uint64_t frame_count;
  uint64_t render_failure_count;
  int32_t forced_present_failures_for_testing;
  int32_t forced_pre_present_failures_for_testing;
  int32_t forced_location_warm_failures_for_testing;
  int32_t forced_surface_acquisition_failures_for_testing;
  uint64_t touch_count;
  uint64_t lifecycle_generation;
  uint64_t tap_intent_count;
  uint64_t page_move_success_count;
  uint64_t page_move_presented_count;
  uint64_t page_move_boundary_count;
  uint64_t page_move_gate_block_count;
  uint64_t navigation_failure_count;
  uint64_t location_warm_step_count;
  uint64_t location_warm_progress_count;
  uint64_t location_warm_defer_count;
  uint64_t location_warm_failure_count;
  uint64_t location_warm_first_frame_count;
  uint64_t reader_entry_started_millis;
  uint64_t first_frame_elapsed_millis;
  int first_frame_timing_finished;
  uint64_t page_move_expected_byte_offset;
  uint32_t page_move_expected_spine_index;
  uint64_t semantic_navigation_expected_byte_offset;
  uint64_t semantic_navigation_lifecycle_generation;
  uint64_t semantic_navigation_surface_generation;
  uint64_t semantic_navigation_generation;
  uint64_t semantic_navigation_presented_generation;
  uint64_t semantic_navigation_request_count;
  uint64_t semantic_navigation_presented_count;
  uint64_t semantic_navigation_failure_count;
  uint64_t semantic_navigation_gate_block_count;
  uint32_t semantic_navigation_expected_spine_index;
  uint32_t presented_history_back_count;
  uint32_t presented_history_forward_count;
  EpubReaderNavigationReason semantic_navigation_reason;
  EpubReaderNavigationEntry semantic_navigation_origin;
  EpubReaderNavigationEntry semantic_navigation_history_current;
  EpubReaderNavigationEntry semantic_navigation_history_target;
  int32_t semantic_navigation_kind;
  int semantic_navigation_waiting_for_present;
  int semantic_navigation_history_traversal;
  int semantic_navigation_history_forward;
  uint32_t search_navigation_previous_active_index;
  int search_navigation_previous_has_active;
  uint64_t progress_display_generation;
  uint64_t progress_display_presented_generation;
  int progress_display_mode;
  int progress_display_presented_mode;
  int progress_display_waiting_for_present;
  uint64_t touch_down_time_millis;
  float touch_down_x;
  float touch_down_y;
  int32_t touch_direction;
  int page_move_waiting_for_present;
  int touch_active;
} OctavoAndroidApp;

static B32 octavo_android_abort_structural_navigation(
  OctavoAndroidApp *app);
static S32 octavo_android_cancel_pending_navigation(
  OctavoAndroidApp *app);
static B32 octavo_android_commit_structural_navigation(
  OctavoAndroidApp *app);
static void octavo_android_format_progress_label(OctavoAndroidApp *app);
static B32 octavo_android_abort_search_mutation(OctavoAndroidApp *app);
static B32 octavo_android_abort_selection_page_turn(
  OctavoAndroidApp *app);
static B32 octavo_android_abort_selection_mutation(OctavoAndroidApp *app);
static void octavo_android_selection_page_turn_state_clear(
  OctavoAndroidApp *app);
static void octavo_android_discard_selection(OctavoAndroidApp *app);
static B32 octavo_android_selection_highlight_for_range(
  const OctavoAndroidApp *app, U64 start, U64 end);
static void octavo_android_draw_selection_handles(
  OctavoAndroidApp *app, OctavoAndroidPixels pixels);

static OctavoAndroidApp *
octavo_android_from_handle(jlong handle)
{
  return (OctavoAndroidApp *)(uintptr_t)handle;
}

static void
octavo_android_invalidate_prepared_static_frame(OctavoAndroidApp *app)
{
  if (!app) { return; }
  app->prepared_static_frame_mutation_generation += 1u;
  if (app->prepared_static_frame.valid)
  {
    app->prepared_static_frame_invalidation_count += 1u;
  }
  memset(&app->prepared_static_frame, 0,
         sizeof(app->prepared_static_frame));
}

static B32
octavo_android_prepared_static_frame_matches(
  const OctavoAndroidApp *app,
  ANativeWindow *window,
  S32 width,
  S32 height)
{
  if (!app || !window || width <= 0 || height <= 0)
  {
    return 0;
  }
  const OctavoAndroidPreparedStaticFrame *prepared =
    &app->prepared_static_frame;
  if (!prepared->valid || !app->resumed || app->window != window ||
      prepared->window != window || prepared->width != width ||
      prepared->height != height ||
      prepared->mutation_generation !=
        app->prepared_static_frame_mutation_generation ||
      prepared->build_serial != app->prepared_static_frame_build_serial ||
      prepared->lifecycle_generation != app->lifecycle_generation ||
      prepared->surface_generation != app->surface_generation ||
      app->pagination_dirty || !app->reader_frame.ready ||
      !app->reader_frame.document_open || !app->reader_view_ready ||
      !epub_reader_layout_keys_match(prepared->layout_key,
                                     app->layout_key) ||
      prepared->document_id != app->reader_frame.document_id ||
      prepared->active_spine_index != app->reader.active_spine_index ||
      prepared->view_byte_offset != app->reader.view_byte_offset ||
      prepared->has_current_page != app->reader.has_current_page ||
      prepared->frame_spine_index != app->reader_frame.spine_index ||
      prepared->frame_view_byte_offset !=
        app->reader_frame.view_byte_offset ||
      prepared->location_document_id !=
        app->reader.location_document_id ||
      prepared->location_spine_count !=
        app->reader.location_spine_count ||
      prepared->location_next_spine_index !=
        app->reader.location_next_spine_index ||
      prepared->location_total_text_bytes !=
        app->reader.location_total_text_bytes ||
      prepared->location_cache_complete !=
        app->reader.location_cache_complete ||
      prepared->location_cache_valid != app->reader.location_cache_valid)
  {
    return 0;
  }
  if (prepared->has_current_page &&
      (prepared->current_page_spine_index !=
         app->reader.current_page.spine_index ||
       prepared->current_page_first_byte !=
         app->reader.current_page.first_byte ||
       prepared->current_page_one_past_last_byte !=
         app->reader.current_page.one_past_last_byte))
  {
    return 0;
  }
  return 1;
}

static B32
octavo_android_record_prepared_static_frame(OctavoAndroidApp *app,
                                            S32 width,
                                            S32 height)
{
  if (!app || !app->window || !app->resumed || width <= 0 || height <= 0 ||
      app->pagination_dirty || !app->reader_frame.ready ||
      !app->reader_frame.document_open || !app->reader_view_ready)
  {
    return 0;
  }
  OctavoAndroidPreparedStaticFrame prepared = {
    .valid = 1,
    .window = app->window,
    .mutation_generation =
      app->prepared_static_frame_mutation_generation,
    .build_serial = app->prepared_static_frame_build_serial,
    .lifecycle_generation = app->lifecycle_generation,
    .surface_generation = app->surface_generation,
    .width = width,
    .height = height,
    .layout_key = app->layout_key,
    .document_id = app->reader_frame.document_id,
    .active_spine_index = app->reader.active_spine_index,
    .view_byte_offset = app->reader.view_byte_offset,
    .has_current_page = app->reader.has_current_page,
    .current_page_spine_index = app->reader.current_page.spine_index,
    .current_page_first_byte = app->reader.current_page.first_byte,
    .current_page_one_past_last_byte =
      app->reader.current_page.one_past_last_byte,
    .frame_spine_index = app->reader_frame.spine_index,
    .frame_view_byte_offset = app->reader_frame.view_byte_offset,
    .location_document_id = app->reader.location_document_id,
    .location_spine_count = app->reader.location_spine_count,
    .location_next_spine_index = app->reader.location_next_spine_index,
    .location_total_text_bytes = app->reader.location_total_text_bytes,
    .location_cache_complete = app->reader.location_cache_complete,
    .location_cache_valid = app->reader.location_cache_valid,
  };
  app->prepared_static_frame = prepared;
  return 1;
}

static void
octavo_android_reject_prepared_static_frame(OctavoAndroidApp *app)
{
  if (!app) { return; }
  app->prepared_static_frame_stale_reject_count += 1u;
  octavo_android_invalidate_prepared_static_frame(app);
}

static void
octavo_android_consume_prepared_static_frame(OctavoAndroidApp *app)
{
  if (!app || !app->prepared_static_frame.valid) { return; }
  memset(&app->prepared_static_frame, 0,
         sizeof(app->prepared_static_frame));
  app->prepared_static_frame_consume_count += 1u;
}

static OctavoAndroidImageCacheEntry *
octavo_android_image_cache_find(OctavoAndroidApp *app, U32 resource_index)
{
  if (!app) { return 0; }
  for (U32 index = 0; index < app->image_cache_count; ++index)
  {
    OctavoAndroidImageCacheEntry *entry = app->image_cache + index;
    if (entry->resource_index == resource_index)
    {
      return entry;
    }
  }
  return 0;
}

static void
octavo_android_image_cache_touch(OctavoAndroidApp *app,
                                 OctavoAndroidImageCacheEntry *entry)
{
  if (!app || !entry) { return; }
  if (app->image_cache_use_serial < UINT64_MAX)
  {
    app->image_cache_use_serial += 1u;
  }
  entry->last_use_serial = app->image_cache_use_serial;
}

static B32
octavo_android_frame_resource_is_pinned(const OctavoAndroidApp *app,
                                        U32 resource_index)
{
  if (!app || !app->reader_frame.ready) { return 0; }
  U32 image_count = MIN(app->reader_frame.image_count,
                        (U32)EPUB_READER_FRAME_IMAGE_CAP);
  for (U32 index = 0; index < image_count; ++index)
  {
    const EpubReaderFrameImage *image = app->reader_frame.images + index;
    if (image->has_resource && image->resource_index == resource_index)
    {
      return 1;
    }
  }
  return 0;
}

static void
octavo_android_image_cache_remove(OctavoAndroidApp *app, U32 index)
{
  if (!app || index >= app->image_cache_count) { return; }
  OctavoAndroidImageCacheEntry *entry = app->image_cache + index;
  free(entry->pixels);
  app->image_cache_pixel_bytes =
    entry->pixel_bytes <= app->image_cache_pixel_bytes ?
      app->image_cache_pixel_bytes - entry->pixel_bytes : 0;
  U32 move_count = app->image_cache_count - index - 1u;
  if (move_count > 0)
  {
    memmove(entry, entry + 1, sizeof(*entry) * move_count);
  }
  app->image_cache_count -= 1u;
  memset(app->image_cache + app->image_cache_count,
         0,
         sizeof(app->image_cache[0]));
}

static B32
octavo_android_image_cache_evict_unpinned(OctavoAndroidApp *app)
{
  if (!app) { return 0; }
  U32 victim_index = UINT32_MAX;
  U64 victim_serial = UINT64_MAX;
  for (U32 index = 0; index < app->image_cache_count; ++index)
  {
    const OctavoAndroidImageCacheEntry *entry = app->image_cache + index;
    if (octavo_android_frame_resource_is_pinned(app, entry->resource_index))
    {
      continue;
    }
    if (victim_index == UINT32_MAX || entry->last_use_serial < victim_serial)
    {
      victim_index = index;
      victim_serial = entry->last_use_serial;
    }
  }
  if (victim_index == UINT32_MAX) { return 0; }
  octavo_android_image_cache_remove(app, victim_index);
  return 1;
}

static B32
octavo_android_image_cache_make_entry_room(OctavoAndroidApp *app)
{
  if (!app) { return 0; }
  while (app->image_cache_count >= OCTAVO_ANDROID_IMAGE_CACHE_CAP)
  {
    if (!octavo_android_image_cache_evict_unpinned(app)) { return 0; }
  }
  return 1;
}

static B32
octavo_android_image_cache_make_pixel_room(OctavoAndroidApp *app,
                                           U64 pixel_bytes)
{
  if (!app || pixel_bytes > OCTAVO_ANDROID_IMAGE_PIXEL_BUDGET)
  {
    return 0;
  }
  while (app->image_cache_pixel_bytes > OCTAVO_ANDROID_IMAGE_PIXEL_BUDGET ||
         pixel_bytes > OCTAVO_ANDROID_IMAGE_PIXEL_BUDGET -
                         app->image_cache_pixel_bytes)
  {
    if (!octavo_android_image_cache_evict_unpinned(app)) { return 0; }
  }
  return 1;
}

static void
octavo_android_image_cache_release(OctavoAndroidApp *app)
{
  if (!app) { return; }
  for (U32 index = 0; index < app->image_cache_count; ++index)
  {
    free(app->image_cache[index].pixels);
    app->image_cache[index].pixels = 0;
  }
  memset(app->image_cache, 0, sizeof(app->image_cache));
  app->image_cache_count = 0;
  app->image_cache_pixel_bytes = 0;
  app->image_cache_use_serial = 0;
}

static void
octavo_android_attach_frame_images(OctavoAndroidApp *app)
{
  if (!app || !app->reader_frame.ready) { return; }
  U32 image_count = MIN(app->reader_frame.image_count,
                        (U32)EPUB_READER_FRAME_IMAGE_CAP);
  for (U32 index = 0; index < image_count; ++index)
  {
    EpubReaderFrameImage *image = app->reader_frame.images + index;
    image->pixels = 0;
    image->src_w = 0;
    image->src_h = 0;
    image->src_stride_pixels = 0;
    if (!image->has_resource)
    {
      image->status = EpubReaderFrameImageStatus_MissingResource;
      continue;
    }
    OctavoAndroidImageCacheEntry *entry =
      octavo_android_image_cache_find(app, image->resource_index);
    if (!entry)
    {
      image->status = EpubReaderFrameImageStatus_Unavailable;
      continue;
    }
    octavo_android_image_cache_touch(app, entry);
    image->status = entry->status;
    if (entry->status == EpubReaderFrameImageStatus_Loaded &&
        entry->pixels && entry->width > 0 && entry->height > 0 &&
        entry->stride_pixels >= entry->width)
    {
      image->pixels = entry->pixels;
      image->src_w = entry->width;
      image->src_h = entry->height;
      image->src_stride_pixels = entry->stride_pixels;
    }
  }
}

static B32
octavo_android_frame_images_prepared(const OctavoAndroidApp *app)
{
  if (!app || !app->reader_frame.ready ||
      app->reader_frame.image_count > EPUB_READER_FRAME_IMAGE_CAP)
  {
    return 0;
  }
  for (U32 index = 0; index < app->reader_frame.image_count; ++index)
  {
    const EpubReaderFrameImage *image = app->reader_frame.images + index;
    if (image->has_resource &&
        image->status == EpubReaderFrameImageStatus_Unavailable)
    {
      return 0;
    }
  }
  return 1;
}
static uint64_t
octavo_android_uptime_millis(void)
{
  struct timespec value = {0};
  if (clock_gettime(CLOCK_MONOTONIC, &value) != 0)
  {
    return 0;
  }
  return (uint64_t)value.tv_sec * UINT64_C(1000) +
    (uint64_t)value.tv_nsec / UINT64_C(1000000);
}

static int
octavo_android_copy_path(JNIEnv *environment,
                         jstring source,
                         char *destination,
                         size_t destination_capacity)
{
  if (!source || !destination || destination_capacity == 0u)
  {
    return 0;
  }

  const char *source_utf8 =
    (*environment)->GetStringUTFChars(environment, source, 0);
  if (!source_utf8)
  {
    return 0;
  }

  size_t source_length = strlen(source_utf8);
  int copied = source_length > 0u && source_length < destination_capacity;
  if (copied)
  {
    memcpy(destination, source_utf8, source_length + 1u);
  }
  (*environment)->ReleaseStringUTFChars(environment, source, source_utf8);
  return copied;
}

static void
octavo_android_release_typography(OctavoAndroidTypography *typography)
{
  if (!typography)
  {
    return;
  }
  free(typography->alpha);
  memset(typography, 0, sizeof(*typography));
}

static int
octavo_android_import_typography(JNIEnv *environment,
                                  jintArray metrics_array,
                                  jbyteArray alpha_array,
                                  OctavoAndroidTypography *typography)
{
  if (!environment || !metrics_array || !alpha_array || !typography)
  {
    return 0;
  }
  jsize metrics_count = (*environment)->GetArrayLength(
    environment, metrics_array);
  jsize alpha_count = (*environment)->GetArrayLength(
    environment, alpha_array);
  if (metrics_count < OCTAVO_ANDROID_TYPOGRAPHY_HEADER_COUNT ||
      alpha_count <= 0)
  {
    return 0;
  }

  jint header[OCTAVO_ANDROID_TYPOGRAPHY_HEADER_COUNT] = {0};
  (*environment)->GetIntArrayRegion(
    environment, metrics_array, 0,
    OCTAVO_ANDROID_TYPOGRAPHY_HEADER_COUNT, header);
  if ((*environment)->ExceptionCheck(environment) ||
      header[0] != OCTAVO_ANDROID_TYPOGRAPHY_MAGIC ||
      header[1] != OCTAVO_ANDROID_TYPOGRAPHY_VERSION ||
      header[2] != OCTAVO_ANDROID_TYPOGRAPHY_FIRST_CODEPOINT ||
      header[3] <= 0 ||
      header[3] > OCTAVO_ANDROID_TYPOGRAPHY_GLYPH_CAP ||
      header[4] != OCTAVO_ANDROID_TYPOGRAPHY_STYLE_COUNT ||
      header[5] <= 0 || header[6] <= 0 ||
      header[7] <= 0 || header[8] <= 0 ||
      header[9] <= 0 || header[10] <= 0 ||
      header[11] < header[9] || header[12] < 12 ||
      header[13] <= 0 || header[14] < 0 || header[15] <= 0 ||
      header[16] < 0 || header[16] >= header[7] ||
      header[17] < 0 || header[17] >= header[8] ||
      (int64_t)header[9] != (int64_t)header[7] * header[5] ||
      (int64_t)header[10] !=
        (int64_t)header[8] * header[6] * header[4] ||
      (int64_t)header[6] * header[5] < header[3] ||
      header[9] > 16384 || header[10] > 16384)
  {
    return 0;
  }
  jsize glyph_count = (jsize)header[3];
  jsize expected_metrics =
    OCTAVO_ANDROID_TYPOGRAPHY_HEADER_COUNT + glyph_count +
    OCTAVO_ANDROID_TYPOGRAPHY_STYLE_COUNT * glyph_count;
  if (metrics_count != expected_metrics)
  {
    return 0;
  }

  jint raw_codepoints[OCTAVO_ANDROID_TYPOGRAPHY_GLYPH_CAP] = {0};
  (*environment)->GetIntArrayRegion(
    environment, metrics_array,
    OCTAVO_ANDROID_TYPOGRAPHY_HEADER_COUNT,
    glyph_count, raw_codepoints);
  if ((*environment)->ExceptionCheck(environment))
  {
    return 0;
  }
  int has_fallback = 0;
  int has_space = 0;
  int has_n = 0;
  int has_i = 0;
  int has_w = 0;
  for (jsize glyph = 0; glyph < glyph_count; ++glyph)
  {
    jint codepoint = raw_codepoints[glyph];
    if (codepoint < OCTAVO_ANDROID_TYPOGRAPHY_FIRST_CODEPOINT ||
        codepoint > 0x10FFFF ||
        (codepoint >= 0xD800 && codepoint <= 0xDFFF) ||
        (glyph > 0 && codepoint <= raw_codepoints[glyph - 1]))
    {
      return 0;
    }
    has_fallback |= codepoint == '?';
    has_space |= codepoint == ' ';
    has_n |= codepoint == 'n';
    has_i |= codepoint == 'i';
    has_w |= codepoint == 'W';
    typography->codepoints[glyph] = (uint32_t)codepoint;
  }
  if (!has_fallback || !has_space || !has_n || !has_i || !has_w)
  {
    return 0;
  }

  jint raw_advances[
    OCTAVO_ANDROID_TYPOGRAPHY_STYLE_COUNT *
      OCTAVO_ANDROID_TYPOGRAPHY_GLYPH_CAP] = {0};
  (*environment)->GetIntArrayRegion(
    environment,
    metrics_array,
    OCTAVO_ANDROID_TYPOGRAPHY_HEADER_COUNT + glyph_count,
    OCTAVO_ANDROID_TYPOGRAPHY_STYLE_COUNT * glyph_count,
    raw_advances);
  if ((*environment)->ExceptionCheck(environment))
  {
    return 0;
  }
  for (int32_t style = 0;
       style < OCTAVO_ANDROID_TYPOGRAPHY_STYLE_COUNT;
       ++style)
  {
    for (jsize glyph = 0; glyph < glyph_count; ++glyph)
    {
      jint advance = raw_advances[style * glyph_count + glyph];
      if (advance <= 0 || advance > header[7] * 2)
      {
        return 0;
      }
      typography->advances[style][glyph] = (int32_t)advance;
    }
  }

  size_t alpha_size = (size_t)header[11] * (size_t)header[10];
  if (alpha_size == 0 || alpha_size > 64u * 1024u * 1024u ||
      alpha_size > (size_t)alpha_count)
  {
    return 0;
  }
  uint8_t *alpha = (uint8_t *)malloc(alpha_size);
  if (!alpha)
  {
    return 0;
  }
  (*environment)->GetByteArrayRegion(
    environment, alpha_array, 0, (jsize)alpha_size, (jbyte *)alpha);
  if ((*environment)->ExceptionCheck(environment))
  {
    free(alpha);
    return 0;
  }
  typography->first_codepoint = header[2];
  typography->glyph_count = header[3];
  typography->style_count = header[4];
  typography->column_count = header[5];
  typography->rows_per_style = header[6];
  typography->cell_width = header[7];
  typography->cell_height = header[8];
  typography->atlas_width = header[9];
  typography->atlas_height = header[10];
  typography->atlas_stride = header[11];
  typography->text_px = header[12];
  typography->ascent_px = header[13];
  typography->descent_px = header[14];
  typography->line_advance_px = header[15];
  typography->origin_x = header[16];
  typography->baseline_y = header[17];
  typography->alpha = alpha;
  typography->alpha_size = alpha_size;
  typography->ready = 1;
  return 1;
}

static int
octavo_android_import_appearance(JNIEnv *environment,
                                 jintArray config_array,
                                 jintArray color_array,
                                 OctavoAndroidAppearance *appearance)
{
  if (!environment || !config_array || !color_array || !appearance ||
      (*environment)->GetArrayLength(environment, config_array) !=
        OCTAVO_ANDROID_APPEARANCE_CONFIG_COUNT ||
      (*environment)->GetArrayLength(environment, color_array) !=
        UI0ColorRole_Count)
  {
    return 0;
  }

  jint config[OCTAVO_ANDROID_APPEARANCE_CONFIG_COUNT] = {0};
  jint colors[UI0ColorRole_Count] = {0};
  (*environment)->GetIntArrayRegion(
    environment, config_array, 0,
    OCTAVO_ANDROID_APPEARANCE_CONFIG_COUNT, config);
  (*environment)->GetIntArrayRegion(
    environment, color_array, 0, UI0ColorRole_Count, colors);
  if ((*environment)->ExceptionCheck(environment) ||
      config[0] != OCTAVO_ANDROID_APPEARANCE_MAGIC ||
      config[1] != OCTAVO_ANDROID_APPEARANCE_VERSION ||
      config[2] < 0 || config[2] >= OCTAVO_ANDROID_THEME_COUNT ||
      config[3] < 0 || config[3] > 1 ||
      (config[4] != 14 && config[4] != 16 && config[4] != 18 &&
       config[4] != 21 && config[4] != 24 && config[4] != 28) ||
      (config[5] != 1150 && config[5] != 1250 &&
       config[5] != 1300 && config[5] != 1500) ||
      config[6] < 0 || config[6] > 2 ||
      (config[7] != 720 && config[7] != 860 && config[7] != 960) ||
      config[7] != (config[6] == 0 ? 720 :
                    config[6] == 1 ? 860 : 960) ||
      config[8] < 0 || config[8] > 1 ||
      config[9] < 0 || config[9] > 1 ||
      config[10] < 0 || config[10] > 1)
  {
    return 0;
  }

  memset(appearance, 0, sizeof(*appearance));
  appearance->theme = config[2];
  appearance->font_family = config[3];
  appearance->font_size_sp = config[4];
  appearance->line_spacing_permille = config[5];
  appearance->margin = config[6];
  appearance->content_width_permille = config[7];
  appearance->alignment = config[8];
  appearance->publisher_colors = config[9];
  appearance->reduced_motion = config[10];
  uint64_t hash = 14695981039346656037ULL;
  for (int32_t index = 0; index < UI0ColorRole_Count; ++index)
  {
    appearance->colors[index] = (UI0Color)(uint32_t)colors[index];
    hash = (hash ^ (uint64_t)(uint32_t)colors[index]) *
      1099511628211ULL;
  }
  appearance->color_hash = hash;
  return 1;
}

static void
octavo_android_resolve_appearance_theme(OctavoAndroidApp *app)
{
  if (!app)
  {
    return;
  }
  int dark = app->appearance.theme == OCTAVO_ANDROID_THEME_DUSK ||
    app->appearance.theme == OCTAVO_ANDROID_THEME_WARM_DARK ||
    app->appearance.theme == OCTAVO_ANDROID_THEME_OLED;
  UI0ThemeProfile profile = ui0_theme_profile_for_kind(
    dark ? UI0ThemeProfile_Dark : UI0ThemeProfile_Light);
  app->reader_view_theme = profile.resolved;
  for (int32_t index = 0; index < UI0ColorRole_Count; ++index)
  {
    app->reader_view_theme.colors[index] =
      app->appearance.colors[index];
  }
}

static int
octavo_android_appearance_layout_equal(
  const OctavoAndroidAppearance *a,
  const OctavoAndroidAppearance *b)
{
  return a && b &&
    a->font_family == b->font_family &&
    a->font_size_sp == b->font_size_sp &&
    a->line_spacing_permille == b->line_spacing_permille &&
    a->margin == b->margin &&
    a->content_width_permille == b->content_width_permille;
}

static int
octavo_android_presentation_pending(const OctavoAndroidApp *app)
{
  return app &&
    (app->page_move_waiting_for_present ||
     app->semantic_navigation_waiting_for_present ||
     app->progress_display_waiting_for_present ||
     app->reflow_waiting_for_present ||
     app->appearance_waiting_for_present ||
     app->selection_mutation_waiting_for_present ||
     app->search_mutation_waiting_for_present ||
     app->host_frame_waiting_for_present);
}

static int
octavo_android_navigation_availability(const OctavoAndroidApp *app)
{
  if (!app || !app->reader_view_ready ||
      octavo_android_presentation_pending(app))
  {
    return 0;
  }

  int result = 1;
  for (S32 index = 0;
       index < app->reader_view_frame.semantic_node_count;
       ++index)
  {
    const ReaderViewSemanticNode *node =
      app->reader_view_frame.semantic_nodes + index;
    if (!(node->flags & ReaderViewSemantic_Enabled))
    {
      continue;
    }
    if (node->control == ReaderViewSemanticControl_PreviousPage)
    {
      result |= 2;
    }
    else if (node->control == ReaderViewSemanticControl_NextPage)
    {
      result |= 4;
    }
  }
  return result;
}

static ReaderViewText
octavo_android_reader_view_text(const char *text)
{
  ReaderViewText result = {0};
  if (text)
  {
    size_t size = strlen(text);
    if (size <= (size_t)INT32_MAX)
    {
      result.data = text;
      result.size = (UI0S32)size;
    }
  }
  return result;
}

static jstring
octavo_android_new_utf8_string(JNIEnv *environment, String8 text)
{
  if (!environment)
  {
    return 0;
  }
  if ((text.size > 0 && !text.str) || text.size > 1024u)
  {
    return (*environment)->NewStringUTF(environment, "");
  }

  jchar utf16[1025] = {0};
  jsize written = 0;
  for (U64 at = 0; at < text.size; )
  {
    BaseUnicodeDecode decode = base_unicode_utf8_decode(text, at);
    if (!decode.valid || decode.advance == 0 ||
        decode.advance > text.size - at)
    {
      return (*environment)->NewStringUTF(environment, "");
    }
    U32 scalar = decode.scalar;
    if (scalar <= 0xffffu)
    {
      if ((scalar >= 0xd800u && scalar <= 0xdfffu) ||
          written >= (jsize)ARRAY_COUNT(utf16))
      {
        return (*environment)->NewStringUTF(environment, "");
      }
      utf16[written++] = (jchar)scalar;
    }
    else if (scalar <= 0x10ffffu)
    {
      if (written + 2 > (jsize)ARRAY_COUNT(utf16))
      {
        return (*environment)->NewStringUTF(environment, "");
      }
      scalar -= 0x10000u;
      utf16[written++] = (jchar)(0xd800u + (scalar >> 10));
      utf16[written++] = (jchar)(0xdc00u + (scalar & 0x3ffu));
    }
    else
    {
      return (*environment)->NewStringUTF(environment, "");
    }
    at += decode.advance;
  }
  return (*environment)->NewString(environment, utf16, written);
}

static jstring
octavo_android_new_reader_view_text(JNIEnv *environment,
                                    ReaderViewText text)
{
  char buffer[1025];
  if (!environment || !text.data || text.size <= 0)
  {
    return environment ?
      (*environment)->NewStringUTF(environment, "") : 0;
  }
  size_t size = MIN((size_t)text.size, sizeof(buffer) - 1u);
  memcpy(buffer, text.data, size);
  buffer[size] = 0;
  return (*environment)->NewStringUTF(environment, buffer);
}

static uint8_t
octavo_android_color_channel(UI0Color color, uint32_t shift)
{
  return (uint8_t)((color >> shift) & 0xFFu);
}

static void
octavo_android_write_pixel(OctavoAndroidPixels pixels,
                           int32_t x,
                           int32_t y,
                           UI0Color color)
{
  if (!pixels.data || x < 0 || y < 0 ||
      x >= pixels.width || y >= pixels.height)
  {
    return;
  }
  uint8_t *pixel = pixels.data +
    ((size_t)y * (size_t)pixels.stride + (size_t)x) * 4u;
  pixel[0] = octavo_android_color_channel(color, 16u);
  pixel[1] = octavo_android_color_channel(color, 8u);
  pixel[2] = octavo_android_color_channel(color, 0u);
  pixel[3] = octavo_android_color_channel(color, 24u);
}

static void
octavo_android_blend_pixel(OctavoAndroidPixels pixels,
                           int32_t x,
                           int32_t y,
                           UI0Color color,
                           uint8_t coverage)
{
  if (!pixels.data || coverage == 0 || x < 0 || y < 0 ||
      x >= pixels.width || y >= pixels.height)
  {
    return;
  }
  uint8_t color_alpha = octavo_android_color_channel(color, 24u);
  uint32_t alpha = ((uint32_t)coverage * color_alpha + 127u) / 255u;
  uint8_t *pixel = pixels.data +
    ((size_t)y * (size_t)pixels.stride + (size_t)x) * 4u;
  uint32_t inverse = 255u - alpha;
  pixel[0] = (uint8_t)((octavo_android_color_channel(color, 16u) * alpha +
                        pixel[0] * inverse + 127u) / 255u);
  pixel[1] = (uint8_t)((octavo_android_color_channel(color, 8u) * alpha +
                        pixel[1] * inverse + 127u) / 255u);
  pixel[2] = (uint8_t)((octavo_android_color_channel(color, 0u) * alpha +
                        pixel[2] * inverse + 127u) / 255u);
  pixel[3] = 255u;
}

static int32_t
octavo_android_scaled_px(int32_t value, uint32_t scale_permille)
{
  uint32_t scale = scale_permille ? scale_permille : 1000u;
  return (int32_t)(((int64_t)value * (int64_t)scale + 500) / 1000);
}

static int32_t
octavo_android_typography_style(DocTextStyleFlags flags)
{
  int32_t result = 0;
  if ((flags & DocTextStyleFlag_Bold) != 0)
  {
    result |= 1;
  }
  if ((flags & DocTextStyleFlag_Italic) != 0)
  {
    result |= 2;
  }
  return result;
}

static uint32_t
octavo_android_next_codepoint(const uint8_t *text,
                              uint64_t size,
                              uint64_t *at)
{
  if (!text || !at || *at >= size)
  {
    return 0;
  }
  uint64_t index = *at;
  uint8_t first = text[index++];
  uint32_t codepoint = first;
  uint32_t extra = 0;
  if ((first & 0xE0u) == 0xC0u)
  {
    codepoint = first & 0x1Fu;
    extra = 1;
  }
  else if ((first & 0xF0u) == 0xE0u)
  {
    codepoint = first & 0x0Fu;
    extra = 2;
  }
  else if ((first & 0xF8u) == 0xF0u)
  {
    codepoint = first & 0x07u;
    extra = 3;
  }
  else if (first >= 0x80u)
  {
    codepoint = '?';
  }
  for (uint32_t continuation = 0; continuation < extra; ++continuation)
  {
    if (index >= size || (text[index] & 0xC0u) != 0x80u)
    {
      codepoint = '?';
      break;
    }
    codepoint = (codepoint << 6u) | (text[index++] & 0x3Fu);
  }
  *at = index;
  return codepoint;
}

static int32_t
octavo_android_typography_find_glyph(
  const OctavoAndroidTypography *typography,
  uint32_t codepoint)
{
  if (!typography || !typography->ready || typography->glyph_count <= 0)
  {
    return -1;
  }
  int32_t direct = -1;
  if (codepoint >= OCTAVO_ANDROID_TYPOGRAPHY_FIRST_CODEPOINT &&
      codepoint <= OCTAVO_ANDROID_TYPOGRAPHY_ASCII_LAST)
  {
    direct = (int32_t)codepoint -
      OCTAVO_ANDROID_TYPOGRAPHY_FIRST_CODEPOINT;
  }
  else if (codepoint >= OCTAVO_ANDROID_TYPOGRAPHY_LATIN1_FIRST &&
           codepoint <= OCTAVO_ANDROID_TYPOGRAPHY_LATIN1_LAST)
  {
    direct = OCTAVO_ANDROID_TYPOGRAPHY_ASCII_COUNT +
      (int32_t)codepoint - OCTAVO_ANDROID_TYPOGRAPHY_LATIN1_FIRST;
  }
  if (direct >= 0 && direct < typography->glyph_count &&
      typography->codepoints[direct] == codepoint)
  {
    return direct;
  }
  int32_t lower = 0;
  int32_t upper = typography->glyph_count;
  while (lower < upper)
  {
    int32_t middle = lower + (upper - lower) / 2;
    uint32_t candidate = typography->codepoints[middle];
    if (candidate < codepoint)
    {
      lower = middle + 1;
    }
    else
    {
      upper = middle;
    }
  }
  return lower < typography->glyph_count &&
    typography->codepoints[lower] == codepoint ? lower : -1;
}

static int32_t
octavo_android_typography_glyph_index(
  const OctavoAndroidTypography *typography,
  uint32_t codepoint)
{
  int32_t glyph =
    octavo_android_typography_find_glyph(typography, codepoint);
  return glyph >= 0 ? glyph :
    octavo_android_typography_find_glyph(typography, '?');
}

static int32_t
octavo_android_typography_advance(
  const OctavoAndroidTypography *typography,
  uint32_t codepoint,
  DocTextStyleFlags flags,
  uint32_t scale_permille)
{
  if (!typography || !typography->ready || codepoint == '\r' ||
      codepoint == '\n')
  {
    return 0;
  }
  if (codepoint == '\t')
  {
    int32_t space = octavo_android_typography_glyph_index(
      typography, ' ');
    int32_t style = octavo_android_typography_style(flags);
    if (space < 0 || style < 0 || style >= typography->style_count)
    {
      return 0;
    }
    return MAX(octavo_android_scaled_px(
                 typography->advances[style][space] * 4,
                 scale_permille),
               1);
  }
  int32_t glyph = octavo_android_typography_glyph_index(
    typography, codepoint);
  int32_t style = octavo_android_typography_style(flags);
  if (glyph < 0 || glyph >= typography->glyph_count ||
      style < 0 || style >= typography->style_count)
  {
    return 0;
  }
  return MAX(octavo_android_scaled_px(
               typography->advances[style][glyph], scale_permille),
             1);
}

static void
octavo_android_draw_typography_glyph(OctavoAndroidApp *app,
                                     OctavoAndroidPixels pixels,
                                     uint32_t codepoint,
                                     S32 pen_x,
                                     S32 line_top,
                                     DocTextStyleFlags flags,
                                     U32 scale_permille,
                                     UI0Color color,
                                     UI0Rect clip)
{
  if (!app || !app->typography.ready || codepoint == ' ' ||
      codepoint == '\t' || codepoint == '\r' || codepoint == '\n')
  {
    return;
  }
  OctavoAndroidTypography *typography = &app->typography;
  int32_t glyph = octavo_android_typography_find_glyph(
    typography, codepoint);
  if (glyph < 0)
  {
    if (typography->missing_glyph_count < UINT64_MAX)
    {
      typography->missing_glyph_count += 1u;
    }
    glyph = octavo_android_typography_find_glyph(typography, '?');
  }
  int32_t style = octavo_android_typography_style(flags);
  if (glyph < 0 || glyph >= typography->glyph_count ||
      style < 0 || style >= typography->style_count)
  {
    return;
  }
  int32_t cell_column = glyph % typography->column_count;
  int32_t cell_row = glyph / typography->column_count +
    style * typography->rows_per_style;
  int32_t source_x = cell_column * typography->cell_width;
  int32_t source_y = cell_row * typography->cell_height;
  int32_t destination_width = MAX(
    octavo_android_scaled_px(typography->cell_width, scale_permille), 1);
  int32_t destination_height = MAX(
    octavo_android_scaled_px(typography->cell_height, scale_permille), 1);
  int32_t destination_x = pen_x -
    octavo_android_scaled_px(typography->origin_x, scale_permille);
  for (int32_t y = 0; y < destination_height; ++y)
  {
    int32_t target_y = line_top + y;
    if (target_y < clip.y || target_y >= clip.y + clip.h)
    {
      continue;
    }
    int32_t sample_y = source_y +
      (int32_t)(((int64_t)y * typography->cell_height) /
                destination_height);
    for (int32_t x = 0; x < destination_width; ++x)
    {
      int32_t target_x = destination_x + x;
      if (target_x < clip.x || target_x >= clip.x + clip.w)
      {
        continue;
      }
      int32_t sample_x = source_x +
        (int32_t)(((int64_t)x * typography->cell_width) /
                  destination_width);
      uint8_t coverage = typography->alpha[
        (size_t)sample_y * (size_t)typography->atlas_stride +
        (size_t)sample_x];
      octavo_android_blend_pixel(
        pixels, target_x, target_y, color, coverage);
    }
  }
  typography->rasterized_glyph_count += 1u;
  typography->rasterized_style_count[style] += 1u;
}
static UI0Rect
octavo_android_intersect_rect(UI0Rect a, UI0Rect b)
{
  UI0Rect result = {0};
  int64_t a_right = (int64_t)a.x + a.w;
  int64_t a_bottom = (int64_t)a.y + a.h;
  int64_t b_right = (int64_t)b.x + b.w;
  int64_t b_bottom = (int64_t)b.y + b.h;
  int64_t left = a.x > b.x ? a.x : b.x;
  int64_t top = a.y > b.y ? a.y : b.y;
  int64_t right = a_right < b_right ? a_right : b_right;
  int64_t bottom = a_bottom < b_bottom ? a_bottom : b_bottom;
  if (a.w > 0 && a.h > 0 && b.w > 0 && b.h > 0 &&
      left < right && top < bottom)
  {
    result.x = (UI0S32)left;
    result.y = (UI0S32)top;
    result.w = (UI0S32)(right - left);
    result.h = (UI0S32)(bottom - top);
  }
  return result;
}

static UI0Rect
octavo_android_pixel_bounds(OctavoAndroidPixels pixels)
{
  return ui0_rect(0, 0, pixels.width, pixels.height);
}

static void
octavo_android_fill_rect(OctavoAndroidPixels pixels,
                         UI0Rect rect,
                         UI0Rect clip,
                         UI0Color color)
{
  UI0Rect visible = octavo_android_intersect_rect(
    octavo_android_intersect_rect(rect, clip),
    octavo_android_pixel_bounds(pixels));
  if (!pixels.data || visible.w <= 0 || visible.h <= 0)
  {
    return;
  }
  uint8_t red = octavo_android_color_channel(color, 16u);
  uint8_t green = octavo_android_color_channel(color, 8u);
  uint8_t blue = octavo_android_color_channel(color, 0u);
  uint8_t alpha = octavo_android_color_channel(color, 24u);
  for (int32_t y = visible.y; y < visible.y + visible.h; ++y)
  {
    uint8_t *pixel = pixels.data +
      ((size_t)y * (size_t)pixels.stride + (size_t)visible.x) * 4u;
    for (int32_t x = 0; x < visible.w; ++x)
    {
      pixel[0] = red;
      pixel[1] = green;
      pixel[2] = blue;
      pixel[3] = alpha;
      pixel += 4;
    }
  }
}

static void
octavo_android_stroke_rect(OctavoAndroidPixels pixels,
                           UI0Rect rect,
                           UI0Rect clip,
                           UI0Color color,
                           UI0S32 width)
{
  UI0S32 stroke = width > 0 ? width : 1;
  octavo_android_fill_rect(
    pixels, ui0_rect(rect.x, rect.y, rect.w, stroke), clip, color);
  octavo_android_fill_rect(
    pixels,
    ui0_rect(rect.x, rect.y + rect.h - stroke, rect.w, stroke),
    clip,
    color);
  octavo_android_fill_rect(
    pixels, ui0_rect(rect.x, rect.y, stroke, rect.h), clip, color);
  octavo_android_fill_rect(
    pixels,
    ui0_rect(rect.x + rect.w - stroke, rect.y, stroke, rect.h),
    clip,
    color);
}

static uint32_t
octavo_android_image_lerp_channel(uint32_t a,
                                  uint32_t b,
                                  uint32_t fraction)
{
  return (a * (UINT32_C(65536) - fraction) + b * fraction +
          UINT32_C(32768)) >> 16;
}

static UI0Color
octavo_android_sample_frame_image(const EpubReaderFrameImage *image,
                                  uint64_t source_x,
                                  uint64_t source_y)
{
  U32 x0 = (U32)(source_x >> 16);
  U32 y0 = (U32)(source_y >> 16);
  U32 x1 = MIN(x0 + 1u, (U32)image->src_w - 1u);
  U32 y1 = MIN(y0 + 1u, (U32)image->src_h - 1u);
  U32 fx = (U32)(source_x & UINT64_C(0xFFFF));
  U32 fy = (U32)(source_y & UINT64_C(0xFFFF));
  U32 samples[4] = {
    image->pixels[(U64)y0 * (U64)image->src_stride_pixels + x0],
    image->pixels[(U64)y0 * (U64)image->src_stride_pixels + x1],
    image->pixels[(U64)y1 * (U64)image->src_stride_pixels + x0],
    image->pixels[(U64)y1 * (U64)image->src_stride_pixels + x1],
  };
  UI0Color result = 0;
  static const U32 shifts[] = {0u, 8u, 16u, 24u};
  for (U32 channel = 0; channel < ARRAY_COUNT(shifts); ++channel)
  {
    U32 shift = shifts[channel];
    U32 top = octavo_android_image_lerp_channel(
      (samples[0] >> shift) & 0xFFu,
      (samples[1] >> shift) & 0xFFu,
      fx);
    U32 bottom = octavo_android_image_lerp_channel(
      (samples[2] >> shift) & 0xFFu,
      (samples[3] >> shift) & 0xFFu,
      fx);
    U32 value = octavo_android_image_lerp_channel(top, bottom, fy);
    result |= value << shift;
  }
  return result;
}

static B32
octavo_android_fit_frame_image(const EpubReaderFrameImage *image,
                               UI0Rect bounds,
                               UI0Rect *out_rect)
{
  if (out_rect) { *out_rect = (UI0Rect){0}; }
  if (!image || !out_rect || image->src_w <= 0 || image->src_h <= 0 ||
      bounds.w <= 0 || bounds.h <= 0)
  {
    return 0;
  }
  S32 width = bounds.w;
  S32 height = (S32)(((S64)image->src_h * width) / image->src_w);
  if (height > bounds.h)
  {
    height = bounds.h;
    width = (S32)(((S64)image->src_w * height) / image->src_h);
  }
  if (width <= 0 || height <= 0) { return 0; }
  *out_rect = ui0_rect(
    bounds.x + (bounds.w - width) / 2,
    bounds.y + (bounds.h - height) / 2,
    width,
    height);
  return 1;
}

static void
octavo_android_draw_scaled_frame_image(OctavoAndroidPixels pixels,
                                       const EpubReaderFrameImage *image,
                                       UI0Rect destination,
                                       UI0Rect clip)
{
  if (!pixels.data || !image || !image->pixels || image->src_w <= 0 ||
      image->src_h <= 0 || image->src_stride_pixels < image->src_w ||
      destination.w <= 0 || destination.h <= 0)
  {
    return;
  }
  UI0Rect visible = octavo_android_intersect_rect(
    octavo_android_intersect_rect(destination, clip),
    octavo_android_pixel_bounds(pixels));
  if (visible.w <= 0 || visible.h <= 0) { return; }
  U64 x_step = destination.w > 1 ?
    (((U64)(image->src_w - 1) << 16) / (U64)(destination.w - 1)) : 0;
  U64 y_step = destination.h > 1 ?
    (((U64)(image->src_h - 1) << 16) / (U64)(destination.h - 1)) : 0;
  for (S32 y = visible.y; y < visible.y + visible.h; ++y)
  {
    U64 source_y = (U64)(y - destination.y) * y_step;
    for (S32 x = visible.x; x < visible.x + visible.w; ++x)
    {
      U64 source_x = (U64)(x - destination.x) * x_step;
      UI0Color color = octavo_android_sample_frame_image(
        image, source_x, source_y);
      octavo_android_blend_pixel(pixels, x, y, color, 255u);
    }
  }
}

static const EpubReaderFrameStyleRow *
octavo_android_style_row_for_image(const EpubReaderFrame *frame,
                                   const EpubReaderFrameImage *image)
{
  if (!frame || !image) { return 0; }
  for (U32 index = 0; index < frame->style_row_count; ++index)
  {
    if (frame->style_rows[index].row == image->row)
    {
      return frame->style_rows + index;
    }
  }
  return 0;
}

static S32
octavo_android_frame_style_row_height(
  const OctavoAndroidApp *app,
  const EpubReaderFrameStyleRow *row)
{
  if (!app || !row) { return 0; }
  S32 line_height = MAX(app->typography.line_advance_px, 1);
  if (row->visual_units > 0)
  {
    /* Reader0 expresses an image row's canonical pagination height in
       visual line units. Every Android vertical traversal uses this helper
       so image placement and following text cannot drift. */
    S64 image_height = (S64)row->visual_units * (S64)line_height;
    return (S32)MIN(MAX(image_height, (S64)1), (S64)INT32_MAX);
  }
  U32 line_scale = row->line_height_permille;
  if (line_scale == 0)
  {
    line_scale = MAX(row->font_scale_permille, 1000u);
  }
  return MAX(octavo_android_scaled_px(line_height, line_scale), 1);
}

static UI0Rect
octavo_android_frame_image_box(OctavoAndroidApp *app,
                               const EpubReaderFrameImage *image)
{
  UI0Rect content = app->reader_view_layout.content_rect;
  if (image->image_placement == SourceReaderLayoutImagePlacement_ImageOnly)
  {
    return content;
  }
  const EpubReaderFrameStyleRow *target =
    octavo_android_style_row_for_image(&app->reader_frame, image);
  if (!target) { return (UI0Rect){0}; }
  S32 line_height = MAX(app->typography.line_advance_px, 1);
  S32 char_advance = MAX(
    octavo_android_typography_advance(
      &app->typography, 'n', 0, 1000u), 1);
  S32 y = content.y;
  for (U32 index = 0; index < app->reader_frame.style_row_count; ++index)
  {
    const EpubReaderFrameStyleRow *row =
      app->reader_frame.style_rows + index;
    if (row->line_row == 0 && row->margin_top_rows > 0)
    {
      y += row->margin_top_rows * line_height;
    }
    S32 row_height = octavo_android_frame_style_row_height(app, row);
    if (row == target)
    {
      S32 left = MAX(row->margin_left_cols, 0) * char_advance;
      S32 right = MAX(row->margin_right_cols, 0) * char_advance;
      S32 width = MAX(content.w - left - right, 1);
      if (image->width_permille > 0 && image->width_permille < 1000u)
      {
        width = MAX((S32)(((S64)width * image->width_permille) / 1000), 1);
      }
      S32 remaining_height = MAX(content.y + content.h - y, 0);
      S32 height = (S32)MIN(
        (S64)row_height, (S64)remaining_height);
      return height > 0 ?
        ui0_rect(content.x + left +
                   (content.w - left - right - width) / 2,
                 y, width, height) :
        (UI0Rect){0};
    }
    y += row_height;
    if (row->block_last_row && row->margin_bottom_rows > 0)
    {
      y += row->margin_bottom_rows * line_height;
    }
  }
  return (UI0Rect){0};
}

static B32
octavo_android_draw_reader_images(OctavoAndroidApp *app,
                                  OctavoAndroidPixels pixels)
{
  if (!app || !app->reader_frame.ready ||
      app->reader_frame.image_count > EPUB_READER_FRAME_IMAGE_CAP)
  {
    return app && app->reader_frame.ready;
  }
  UI0Rect page = app->reader_view_layout.page_surface_rect;
  UI0Rect clip = octavo_android_intersect_rect(
    page, octavo_android_pixel_bounds(pixels));
  for (U32 index = 0; index < app->reader_frame.image_count; ++index)
  {
    EpubReaderFrameImage *image = app->reader_frame.images + index;
    UI0Rect box = octavo_android_frame_image_box(app, image);
    if (box.w <= 0 || box.h <= 0) { return 0; }
    UI0Rect fitted = {0};
    if (image->status == EpubReaderFrameImageStatus_Loaded &&
        image->pixels && octavo_android_fit_frame_image(image, box, &fitted))
    {
      octavo_android_draw_scaled_frame_image(pixels, image, fitted, clip);
      continue;
    }
    UI0Color surface =
      app->reader_view_theme.colors[UI0ColorRole_SurfaceElevated];
    UI0Color border =
      app->reader_view_theme.colors[UI0ColorRole_BorderMuted];
    octavo_android_fill_rect(pixels, box, clip, surface);
    octavo_android_stroke_rect(pixels, box, clip, border, 2);
    if (box.w > 24 && box.h > 24)
    {
      octavo_android_stroke_rect(
        pixels,
        ui0_rect(box.x + 12, box.y + 12, box.w - 24, box.h - 24),
        clip,
        border,
        1);
    }
  }
  return 1;
}

static void
octavo_android_draw_glyph(OctavoAndroidPixels pixels,
                          char codepoint,
                          S32 x,
                          S32 y,
                          S32 scale,
                          UI0Color color,
                          UI0Rect clip)
{
  FontGlyphBitmap glyph =
    font_provider_resolve_glyph(font_provider_system_ui(), codepoint);
  if (scale <= 0 || glyph.width_px == 0 || glyph.height_px == 0)
  {
    return;
  }
  for (U32 row = 0; row < glyph.height_px; ++row)
  {
    for (U32 column = 0; column < glyph.width_px; ++column)
    {
      U32 shift = (U32)glyph.width_px - column - 1u;
      if ((glyph.rows[row] & (U8)(1u << shift)) != 0)
      {
        octavo_android_fill_rect(
          pixels,
          ui0_rect(x + (S32)column * scale,
                   y + (S32)row * scale,
                   scale,
                   scale),
          clip,
          color);
      }
    }
  }
}

static void
octavo_android_draw_text(OctavoAndroidPixels pixels,
                         ReaderViewText text,
                         UI0Rect rect,
                         UI0Rect clip,
                         UI0Color color,
                         S32 scale,
                         UI0TextAlignX align_x,
                         UI0TextAlignY align_y)
{
  if (!text.data || text.size <= 0 || rect.w <= 0 || rect.h <= 0 ||
      scale <= 0)
  {
    return;
  }
  FontTextMetrics metrics =
    font_metrics_for_size(font_provider_system_ui(), scale);
  S32 text_width = font_measure_text_width_s8(
    font_provider_system_ui(),
    str8((U8 *)(uintptr_t)text.data, (U64)text.size),
    scale);
  S32 x = rect.x;
  S32 y = rect.y;
  if (align_x == UI0TextAlignX_Center)
  {
    x += (rect.w - text_width) / 2;
  }
  else if (align_x == UI0TextAlignX_End)
  {
    x += rect.w - text_width;
  }
  if (align_y == UI0TextAlignY_Center)
  {
    y += (rect.h - metrics.glyph_height_px) / 2;
  }
  else if (align_y == UI0TextAlignY_Bottom)
  {
    y += rect.h - metrics.glyph_height_px;
  }
  UI0Rect text_clip = octavo_android_intersect_rect(rect, clip);
  for (UI0S32 index = 0; index < text.size; ++index)
  {
    char codepoint = text.data[index];
    if (codepoint == '\n' || codepoint == '\r')
    {
      break;
    }
    octavo_android_draw_glyph(
      pixels, codepoint, x, y, scale, color, text_clip);
    x += metrics.glyph_advance_px;
    if (x >= rect.x + rect.w)
    {
      break;
    }
  }
}

static const ReaderViewTextBinding *
octavo_android_text_binding(const ReaderViewFrame *frame, UI0ID source_id)
{
  if (!frame || !frame->text_bindings)
  {
    return 0;
  }
  for (UI0S32 index = 0; index < frame->text_binding_count; ++index)
  {
    const ReaderViewTextBinding *binding = frame->text_bindings + index;
    if (binding->source_id == source_id)
    {
      return binding;
    }
  }
  return 0;
}

static void
octavo_android_draw_icon(OctavoAndroidPixels pixels,
                         const UI0DrawCommand *command)
{
  if (!command || command->rect.w <= 0 || command->rect.h <= 0 ||
      command->rect.w > UI0_ICON_RASTER_MAX_WIDTH ||
      command->rect.h > UI0_ICON_RASTER_MAX_HEIGHT)
  {
    return;
  }
  UI0U32 raster[UI0_ICON_RASTER_MAX_WIDTH * UI0_ICON_RASTER_MAX_HEIGHT] = {0};
  UI0Color background = command->stroke_color != 0 ?
    command->stroke_color : OCTAVO_ANDROID_DEFAULT_BACKGROUND;
  if (!ui0_icon_rasterize_rgb32(command->icon_kind,
                                command->rect.w,
                                command->rect.h,
                                command->color,
                                background,
                                raster,
                                UI0_ICON_RASTER_MAX_WIDTH))
  {
    return;
  }
  UI0Rect visible = octavo_android_intersect_rect(
    command->rect, command->clip_rect);
  for (UI0S32 y = 0; y < command->rect.h; ++y)
  {
    for (UI0S32 x = 0; x < command->rect.w; ++x)
    {
      UI0S32 target_x = command->rect.x + x;
      UI0S32 target_y = command->rect.y + y;
      if (target_x >= visible.x && target_y >= visible.y &&
          target_x < visible.x + visible.w &&
          target_y < visible.y + visible.h)
      {
        UI0Color color =
          0xFF000000u |
          raster[(U32)y * UI0_ICON_RASTER_MAX_WIDTH + (U32)x];
        octavo_android_write_pixel(pixels, target_x, target_y, color);
      }
    }
  }
}

static void
octavo_android_draw_reader_view(OctavoAndroidApp *app,
                                OctavoAndroidPixels pixels)
{
  if (!app || !app->reader_view_ready)
  {
    return;
  }
  const ReaderViewFrame *frame = &app->reader_view_frame;
  for (UI0S32 index = 0; index < frame->draw_command_count; ++index)
  {
    const UI0DrawCommand *command = frame->draw_commands + index;
    switch (command->op)
    {
      case UI0DrawOp_ControlFill:
      case UI0DrawOp_IndicatorFill:
      case UI0DrawOp_ToggleTrack:
      case UI0DrawOp_ToggleKnob:
      case UI0DrawOp_SegmentJoin:
      case UI0DrawOp_TextSelection:
      case UI0DrawOp_TextCaret:
      case UI0DrawOp_ScrollTrack:
      case UI0DrawOp_ScrollThumb:
      case UI0DrawOp_SliderTrack:
      case UI0DrawOp_SliderFill:
      case UI0DrawOp_SliderThumb:
      {
        octavo_android_fill_rect(
          pixels, command->rect, command->clip_rect, command->color);
      } break;

      case UI0DrawOp_ControlBorder:
      case UI0DrawOp_IndicatorBorder:
      case UI0DrawOp_FocusRing:
      {
        octavo_android_stroke_rect(
          pixels,
          command->rect,
          command->clip_rect,
          command->color,
          command->stroke_width);
      } break;

      case UI0DrawOp_Text:
      {
        const ReaderViewTextBinding *binding =
          octavo_android_text_binding(frame, command->source_id);
        if (binding)
        {
          UI0TextAlignX align_x = command->has_text_alignment ?
            command->text_align_x : UI0TextAlignX_Start;
          UI0TextAlignY align_y = command->has_text_alignment ?
            command->text_align_y : UI0TextAlignY_Center;
          S32 scale = 2;
          if (command->has_typography_role &&
              command->typography_line_height >= 18)
          {
            scale = 3;
          }
          octavo_android_draw_text(
            pixels,
            binding->text,
            command->rect,
            command->clip_rect,
            command->color,
            scale,
            align_x,
            align_y);
        }
      } break;

      case UI0DrawOp_Icon:
      {
        octavo_android_draw_icon(pixels, command);
      } break;

      case UI0DrawOp_CheckMark:
      {
        UI0Rect mark = command->rect;
        mark.x += mark.w / 4;
        mark.y += mark.h / 2;
        mark.w = mark.w / 2;
        mark.h = command->stroke_width > 0 ? command->stroke_width : 2;
        octavo_android_fill_rect(
          pixels, mark, command->clip_rect, command->color);
      } break;

      case UI0DrawOp_Count:
      default:
        break;
    }
  }
}

static DocTextStyleFlags
octavo_android_reader_block_style_flags(
  const EpubReaderFrameStyleRow *row)
{
  DocTextStyleFlags result = row ? row->block_style_flags : 0;
  if (row &&
      (row->block_kind == DocTextBlockKind_Heading ||
       row->block_kind == DocTextBlockKind_DefinitionTerm ||
       row->block_kind == DocTextBlockKind_ListItem))
  {
    result |= DocTextStyleFlag_Bold;
  }
  return result;
}

static DocTextStyleFlags
octavo_android_apply_text_style_flags(DocTextStyleFlags base,
                                      DocTextStyleFlags incoming)
{
  DocTextStyleFlags result =
    base | (incoming & ~(DocTextStyleFlag_Underline |
                         DocTextStyleFlag_NoUnderline |
                         DocTextStyleFlag_SmallCaps |
                         DocTextStyleFlag_NormalCaps));
  if ((incoming & DocTextStyleFlag_NoUnderline) != 0)
  {
    result &= ~DocTextStyleFlag_Underline;
    result |= DocTextStyleFlag_NoUnderline;
  }
  if ((incoming & DocTextStyleFlag_Underline) != 0)
  {
    result &= ~DocTextStyleFlag_NoUnderline;
    result |= DocTextStyleFlag_Underline;
  }
  if ((incoming & DocTextStyleFlag_NormalCaps) != 0)
  {
    result &= ~DocTextStyleFlag_SmallCaps;
    result |= DocTextStyleFlag_NormalCaps;
  }
  if ((incoming & DocTextStyleFlag_SmallCaps) != 0)
  {
    result &= ~DocTextStyleFlag_NormalCaps;
    result |= DocTextStyleFlag_SmallCaps;
  }
  return result;
}

static void
octavo_android_reader_segment_style(
  const OctavoAndroidApp *app,
  const EpubReaderFrameStyleRow *row,
  U32 row_size,
  U32 segment_start,
  U32 *out_segment_end,
  DocTextStyleFlags *out_flags,
  U32 *out_scale_permille,
  U32 *out_color_rgb,
  B32 *out_has_color)
{
  U32 segment_end = row_size;
  DocTextStyleFlags flags = octavo_android_reader_block_style_flags(row);
  U32 inline_scale_permille = 1000u;
  U32 color_rgb = row ? row->text_color_rgb : 0u;
  B32 has_color = row ? row->has_text_color : 0;
  if (app && row && row->style_fragment_count > 0 &&
      row->first_style_fragment_index < app->reader_frame.style_fragment_count)
  {
    U32 first = row->first_style_fragment_index;
    U32 end = MIN(first + row->style_fragment_count,
                  app->reader_frame.style_fragment_count);
    for (U32 index = first; index < end; ++index)
    {
      const EpubReaderFrameStyleFragment *fragment =
        app->reader_frame.style_fragments + index;
      if (fragment->row != row->row ||
          fragment->byte_end <= fragment->byte_start)
      {
        continue;
      }
      U32 fragment_start = MIN(fragment->byte_start, row_size);
      U32 fragment_end = MIN(fragment->byte_end, row_size);
      if (fragment_end <= fragment_start)
      {
        continue;
      }
      if (fragment_start <= segment_start && segment_start < fragment_end)
      {
        flags = octavo_android_apply_text_style_flags(flags, fragment->flags);
        if (fragment->font_scale_permille != 0)
        {
          inline_scale_permille = fragment->font_scale_permille;
        }
        if (fragment->has_text_color)
        {
          color_rgb = fragment->text_color_rgb;
          has_color = 1;
        }
        segment_end = MIN(segment_end, fragment_end);
      }
      else if (fragment_start > segment_start)
      {
        segment_end = MIN(segment_end, fragment_start);
      }
    }
  }
  if (segment_end <= segment_start)
  {
    segment_end = MIN(segment_start + 1u, row_size);
  }
  if (out_segment_end) { *out_segment_end = segment_end; }
  if (out_flags) { *out_flags = flags; }
  if (out_scale_permille) { *out_scale_permille = inline_scale_permille; }
  if (out_color_rgb) { *out_color_rgb = color_rgb; }
  if (out_has_color) { *out_has_color = has_color; }
}

static S32
octavo_android_measure_reader_text(void *user,
                                   String8 text,
                                   DocTextStyleFlags flags,
                                   U32 font_scale_permille,
                                   U32 font_family_hint,
                                   U32 font_face_index)
{
  (void)font_family_hint;
  (void)font_face_index;
  OctavoAndroidApp *app = (OctavoAndroidApp *)user;
  if (!app || !app->typography.ready || !text.str)
  {
    return 0;
  }
  S64 width = 0;
  U64 at = 0;
  while (at < text.size)
  {
    U32 codepoint = octavo_android_next_codepoint(text.str, text.size, &at);
    width += octavo_android_typography_advance(
      &app->typography, codepoint, flags, font_scale_permille);
    if (width > INT32_MAX)
    {
      return INT32_MAX;
    }
  }
  return (S32)width;
}

static U32
octavo_android_combined_scale(U32 block_scale_permille,
                              U32 inline_scale_permille)
{
  U64 block = MIN(block_scale_permille ? block_scale_permille : 1000u,
                  4000u);
  U64 inline_scale = MIN(
    inline_scale_permille ? inline_scale_permille : 1000u, 4000u);
  return (U32)MIN((block * inline_scale + 500u) / 1000u, 4000u);
}

static S32
octavo_android_measure_reader_row(const OctavoAndroidApp *app,
                                  const EpubReaderFrameStyleRow *row,
                                  U32 start,
                                  U32 end)
{
  if (!app || !row || end <= start ||
      end > app->reader_frame.visible_text.size)
  {
    return 0;
  }
  U32 row_size = end - start;
  U32 segment_start = 0;
  S64 width = 0;
  while (segment_start < row_size)
  {
    U32 segment_end = row_size;
    DocTextStyleFlags flags = 0;
    U32 inline_scale = 1000u;
    octavo_android_reader_segment_style(
      app, row, row_size, segment_start, &segment_end, &flags,
      &inline_scale, 0, 0);
    String8 segment = str8(
      app->reader_frame.visible_text.str + start + segment_start,
      segment_end - segment_start);
    width += octavo_android_measure_reader_text(
      (void *)app,
      segment,
      flags,
      octavo_android_combined_scale(row->font_scale_permille,
                                    inline_scale),
      row->font_family_hint,
      row->font_face_index);
    if (width > INT32_MAX)
    {
      return INT32_MAX;
    }
    segment_start = segment_end;
  }
  return (S32)width;
}

static B32
octavo_android_reader_row_has_safe_justification_styles(
  const OctavoAndroidApp *app,
  const EpubReaderFrameStyleRow *row)
{
  if (!app || !row)
  {
    return 0;
  }
  DocTextStyleFlags safe = DocTextStyleFlag_Italic |
                           DocTextStyleFlag_Bold |
                           DocTextStyleFlag_Underline |
                           DocTextStyleFlag_SmallCaps |
                           DocTextStyleFlag_NormalCaps |
                           DocTextStyleFlag_NoUnderline;
  if ((octavo_android_reader_block_style_flags(row) & ~safe) != 0)
  {
    return 0;
  }
  U32 first = row->first_style_fragment_index;
  if (row->style_fragment_count > 0 &&
      (first >= app->reader_frame.style_fragment_count ||
       row->style_fragment_count >
         app->reader_frame.style_fragment_count - first))
  {
    return 0;
  }
  U32 end = first + row->style_fragment_count;
  for (U32 index = first; index < end; ++index)
  {
    const EpubReaderFrameStyleFragment *fragment =
      app->reader_frame.style_fragments + index;
    if (fragment->row == row->row && (fragment->flags & ~safe) != 0)
    {
      return 0;
    }
  }
  return 1;
}

static OctavoReaderJustificationBlockRole
octavo_android_reader_justification_block_role(DocTextBlockKind block_kind)
{
  if (block_kind == DocTextBlockKind_Paragraph)
  {
    return OctavoReaderJustificationBlockRole_Paragraph;
  }
  if (block_kind == DocTextBlockKind_Blockquote)
  {
    return OctavoReaderJustificationBlockRole_Blockquote;
  }
  return OctavoReaderJustificationBlockRole_Other;
}

static B32
octavo_android_reader_justification_plan_for_row(
  OctavoAndroidApp *app,
  const EpubReaderFrameStyleRow *row,
  U32 start,
  U32 end,
  S32 natural_width,
  S32 available_width,
  OctavoReaderJustificationPlan *out_plan)
{
  if (!app || !row || !out_plan || end <= start ||
      end > app->reader_frame.visible_text.size)
  {
    return 0;
  }
  U32 row_size = end - start;
  U32 space_count = 0;
  for (U32 index = 1; index + 1u < row_size; ++index)
  {
    if (app->reader_frame.visible_text.str[start + index] == ' ')
    {
      space_count += 1u;
    }
  }
  OctavoReaderJustificationInput input = {
    .block_role =
      octavo_android_reader_justification_block_role(row->block_kind),
    .publisher_justified =
      app->appearance.alignment == 0 &&
      row->text_align == DocTextAlign_Justify,
    .block_last_row = row->block_last_row,
    .soft_wrapped = row->soft_wrapped,
    .safe_styles =
      octavo_android_reader_row_has_safe_justification_styles(app, row),
    .heading_level = row->heading_level,
    .line_row = row->line_row,
    .row_byte_count = row_size,
    .margin_left_cols = row->margin_left_cols,
    .text_indent_cols = row->text_indent_cols,
    .internal_space_count = space_count,
    .natural_width_px = natural_width,
    .available_width_px = available_width,
  };
  if (space_count > 0 &&
      octavo_reader_justification_input_is_eligible(&input) &&
      available_width > natural_width)
  {
    U32 segment_end = row_size;
    DocTextStyleFlags flags = 0;
    U32 inline_scale = 1000u;
    octavo_android_reader_segment_style(
      app, row, row_size, 0, &segment_end, &flags, &inline_scale, 0, 0);
    input.natural_space_width_px = octavo_android_typography_advance(
      &app->typography,
      ' ',
      flags,
      octavo_android_combined_scale(
        row->font_scale_permille, inline_scale));
  }
  return octavo_reader_justification_plan_resolve(&input, out_plan);
}

static uint64_t
octavo_android_justification_hash_mix(uint64_t hash, uint64_t value)
{
  hash ^= value + UINT64_C(0x9E3779B97F4A7C15) +
          (hash << 6u) + (hash >> 2u);
  return hash;
}

static void
octavo_android_record_justification_evidence(
  OctavoAndroidJustificationEvidence *evidence,
  U32 row_index,
  const OctavoReaderJustificationPlan *plan)
{
  if (!evidence || !plan)
  {
    return;
  }
  evidence->plan_count += 1u;
  evidence->active_row_count += plan->active ? 1u : 0u;
  evidence->applied_extra_px += (uint64_t)MAX(plan->applied_extra_px, 0);
  evidence->semantic_hash =
    octavo_android_justification_hash_mix(evidence->semantic_hash, row_index);
  evidence->semantic_hash = octavo_android_justification_hash_mix(
    evidence->semantic_hash, plan->space_count);
  evidence->semantic_hash = octavo_android_justification_hash_mix(
    evidence->semantic_hash, (uint64_t)(uint32_t)plan->natural_width_px);
  evidence->semantic_hash = octavo_android_justification_hash_mix(
    evidence->semantic_hash, (uint64_t)(uint32_t)plan->available_width_px);
  evidence->semantic_hash = octavo_android_justification_hash_mix(
    evidence->semantic_hash, (uint64_t)(uint32_t)plan->applied_extra_px);
}

static B32
octavo_android_search_highlight_for_range(
  const OctavoAndroidApp *app,
  U64 start,
  U64 end,
  B32 *out_active)
{
  if (out_active) { *out_active = 0; }
  if (!app || !out_active || end <= start ||
      !app->reader_frame.ready ||
      app->reader_frame.search_highlight_count >
        EPUB_READER_FRAME_HIGHLIGHT_CAP)
  {
    return 0;
  }
  B32 found = 0;
  for (U32 index = 0;
       index < app->reader_frame.search_highlight_count;
       ++index)
  {
    const EpubReaderFrameSearchHighlightRange *range =
      app->reader_frame.search_highlights + index;
    if (end > range->start && start < range->end)
    {
      found = 1;
      if (range->active) { *out_active = 1; }
    }
  }
  return found;
}

static B32
octavo_android_draw_reader_row(OctavoAndroidApp *app,
                               OctavoAndroidPixels pixels,
                               const EpubReaderFrameStyleRow *row,
                               U32 start,
                               U32 end,
                               S32 x,
                               S32 row_top,
                               S32 row_height,
                               UI0Color default_color,
                               UI0Rect clip,
                               const OctavoReaderJustificationPlan *justification)
{
  if (!app || !row || !justification || end <= start)
  {
    return 0;
  }
  U32 row_size = end - start;
  U32 segment_start = 0;
  U32 space_index = 0;
  S32 pen_x = x;
  while (segment_start < row_size)
  {
    U32 segment_end = row_size;
    DocTextStyleFlags flags = 0;
    U32 inline_scale = 1000u;
    U32 color_rgb = 0;
    B32 has_color = 0;
    octavo_android_reader_segment_style(
      app, row, row_size, segment_start, &segment_end, &flags,
      &inline_scale, &color_rgb, &has_color);
    U32 scale = octavo_android_combined_scale(
      row->font_scale_permille, inline_scale);
    S32 glyph_height = MAX(
      octavo_android_scaled_px(app->typography.cell_height, scale), 1);
    S32 glyph_top = row_top + (row_height - glyph_height) / 2;
    B32 publisher_color_allowed =
      app->appearance.publisher_colors &&
      app->appearance.theme != OCTAVO_ANDROID_THEME_HIGH_CONTRAST;
    UI0Color color = has_color && publisher_color_allowed ?
      0xFF000000u | color_rgb : default_color;
    String8 segment = str8(
      app->reader_frame.visible_text.str + start + segment_start,
      segment_end - segment_start);
    U64 at = 0;
    while (at < segment.size)
    {
      U32 row_byte_index = segment_start + (U32)at;
      U32 codepoint = octavo_android_next_codepoint(
        segment.str, segment.size, &at);
      S32 advance = octavo_android_typography_advance(
        &app->typography, codepoint, flags, scale);
      S32 space_extra = 0;
      if (codepoint == ' ' && row_byte_index > 0 &&
          row_byte_index + 1u < row_size &&
          space_index < justification->space_count)
      {
        space_extra = octavo_reader_justification_space_extra_px(
          justification, space_index);
      }
      B32 selected = octavo_android_selection_highlight_for_range(
        app,
        (U64)start + row_byte_index,
        (U64)start + segment_start + at);
      B32 active_search = 0;
      if (selected ||
          octavo_android_search_highlight_for_range(
            app, (U64)start + row_byte_index,
            (U64)start + segment_start + at, &active_search))
      {
        UI0Color search_color = app->reader_view_theme.colors[
          selected || active_search ?
            UI0ColorRole_Selection : UI0ColorRole_Badge];
        octavo_android_fill_rect(
          pixels,
          ui0_rect(pen_x,
                   glyph_top,
                   MAX(advance + space_extra, 1),
                   glyph_height),
          clip,
          search_color);
      }
      octavo_android_draw_typography_glyph(
        app, pixels, codepoint, pen_x, glyph_top, flags, scale, color, clip);
      pen_x += advance;
      if (codepoint == ' ' && row_byte_index > 0 &&
          row_byte_index + 1u < row_size &&
          space_index < justification->space_count)
      {
        pen_x += space_extra;
        space_index += 1u;
      }
    }
    segment_start = segment_end;
  }
  return space_index == justification->space_count;
}

static B32
octavo_android_draw_reader_text(OctavoAndroidApp *app,
                                 OctavoAndroidPixels pixels,
                                 OctavoAndroidJustificationEvidence *out_evidence)
{
  if (out_evidence)
  {
    memset(out_evidence, 0, sizeof(*out_evidence));
    out_evidence->semantic_hash = UINT64_C(0xCBF29CE484222325);
  }
  if (!app || !app->typography.ready || !app->reader_frame.ready ||
      !app->reader_frame.visible_text.str ||
      app->reader_frame.visible_text.size == 0)
  {
    return 1;
  }
  UI0Rect content = app->reader_view_layout.content_rect;
  UI0Rect page = app->reader_view_layout.page_surface_rect;
  UI0Rect text_clip = ui0_rect(
    page.x, content.y, page.w, content.h);
  UI0Rect clip = octavo_android_intersect_rect(
    text_clip, octavo_android_pixel_bounds(pixels));
  S32 base_line_height = MAX(app->typography.line_advance_px, 1);
  S32 char_advance = MAX(
    octavo_android_typography_advance(
      &app->typography, 'n', 0, 1000u),
    1);
  S32 y = content.y;
  S32 bottom = content.y + content.h;
  UI0Color ink = app->reader_view_theme.colors[UI0ColorRole_TextPrimary];
  for (U32 row_index = 0;
       row_index < app->reader_frame.style_row_count;
       ++row_index)
  {
    const EpubReaderFrameStyleRow *row =
      app->reader_frame.style_rows + row_index;
    U32 start = MIN(row->byte_start,
                    (U32)app->reader_frame.visible_text.size);
    U32 end = MIN(row->byte_end,
                  (U32)app->reader_frame.visible_text.size);
    end = octavo_reader_visible_row_end(
      app->reader_frame.visible_text.str,
      start,
      end,
      row->soft_wrapped);
    if (row->line_row == 0 && row->margin_top_rows > 0)
    {
      y += row->margin_top_rows * base_line_height;
    }
    S32 row_height = octavo_android_frame_style_row_height(app, row);
    if (y + row_height > bottom)
    {
      break;
    }
    S32 left = MAX(row->margin_left_cols, 0) * char_advance;
    S32 right = MAX(row->margin_right_cols, 0) * char_advance;
    if (row->line_row == 0)
    {
      left += MAX(row->text_indent_cols, 0) * char_advance;
    }
    S32 available = MAX(content.w - left - right, 1);
    S32 row_width = octavo_android_measure_reader_row(app, row, start, end);
    OctavoReaderJustificationPlan justification = {0};
    if (end > start &&
        !octavo_android_reader_justification_plan_for_row(
          app, row, start, end, row_width, available, &justification))
    {
      return 0;
    }
    S32 x = content.x + left;
    if (app->appearance.alignment == 0 &&
        row->text_align == DocTextAlign_Center &&
        row_width < available)
    {
      x += (available - row_width) / 2;
    }
    else if (app->appearance.alignment == 0 &&
             row->text_align == DocTextAlign_Right &&
             row_width < available)
    {
      x += available - row_width;
    }
    if (end > start)
    {
      if (!octavo_android_draw_reader_row(
            app, pixels, row, start, end, x, y, row_height, ink, clip,
            &justification))
      {
        return 0;
      }
      octavo_android_record_justification_evidence(
        out_evidence, row_index, &justification);
    }
    y += row_height;
    if (row->block_last_row && row->margin_bottom_rows > 0)
    {
      y += row->margin_bottom_rows * base_line_height;
    }
  }
  return 1;
}

static int
octavo_android_copy_document_title(OctavoAndroidApp *app)
{
  if (!app)
  {
    return 0;
  }
  String8 title = {0};
  if (doc_engine_get_title(epub_reader_engine(&app->reader),
                           epub_reader_document_id(&app->reader),
                           &title) != DocError_Ok ||
      !title.str || title.size == 0)
  {
    title = str8_from_cstr("8vo reader");
  }
  U64 copy_size = MIN(
    title.size, (U64)sizeof(app->document_title) - 1u);
  memcpy(app->document_title, title.str, (size_t)copy_size);
  app->document_title[copy_size] = 0;
  return copy_size > 0;
}

static int
octavo_android_initialize_reader(OctavoAndroidApp *app)
{
  if (!app)
  {
    return 0;
  }
  os_init();
  os_time_init();
  tctx_init_and_set();

  app->arena = arena_alloc(0);
  if (!app->arena)
  {
    return 0;
  }
  EpubReaderConfig reader_config = {
    .typography = {
      .private_font_directory = str8_from_cstr(app->cache_path),
      .instance_key = (U64)(uintptr_t)&app->reader,
    },
  };
  if (epub_reader_init(&app->reader, app->arena, reader_config) !=
      EpubReaderResult_Ok)
  {
    return 0;
  }
  app->reader_initialized = 1;
  app->selection_handle_radius_px = MAX(app->typography.text_px / 6, 2);

  EpubReaderOpenTransition transition = {0};
  EpubReaderResult open_result = epub_reader_open(
    &app->reader,
    str8_from_cstr(app->document_path),
    DocSourceKind_EPUB,
    &transition);
  if (open_result != EpubReaderResult_Ok ||
      !transition.changed ||
      !epub_reader_refresh_active_spine(&app->reader) ||
      !octavo_android_copy_document_title(app))
  {
    app->document_open_failure_count += 1u;
    return 0;
  }
  app->document_open_success_count += 1u;

  reader_view_state_init(&app->reader_view_state);
  reader_view_frame_storage_init(&app->reader_view_storage);
  octavo_android_resolve_appearance_theme(app);
  return 1;
}

static int
octavo_android_resolve_reader_layout(OctavoAndroidApp *app,
                                     S32 width,
                                     S32 height)
{
  S32 bounds_width =
    width - app->inset_left - app->inset_right;
  S32 bounds_height =
    height - app->inset_top - app->inset_bottom;
  if (bounds_width <= 0 || bounds_height <= 0)
  {
    return 0;
  }
  ReaderViewLayoutInput input = {
    .bounds = ui0_rect(app->inset_left,
                       app->inset_top,
                       bounds_width,
                       bounds_height),
    .features = ReaderViewFeature_Paging | ReaderViewFeature_Progress,
    /*
     * Android owns transient chrome. Resolve the canonical ReaderView page in
     * its platform-neutral distraction-free mode; host chrome is composed
     * over a scaled visual without changing this pagination geometry.
     */
    .document_flags =
      ReaderViewDocument_Open | ReaderViewDocument_DistractionFree,
  };
  if (!reader_view_resolve_layout(
        &app->reader_view_state, &input, &app->reader_view_layout))
  {
    return 0;
  }

  UI0Rect viewport = app->reader_view_layout.viewport_rect;
  S32 horizontal_inset = MAX(viewport.w / 64, 8);
  S32 content_inset_x = MAX(viewport.w / 40, 20);
  S32 page_width_ratio = 5;
  S32 page_width_divisor = 3;
  if (app->appearance.margin == 0)
  {
    page_width_ratio = 4;
    page_width_divisor = 3;
  }
  else if (app->appearance.margin == 2)
  {
    page_width_ratio = 2;
    page_width_divisor = 1;
  }
  S32 content_inset_y = MAX(viewport.h / 60, 24);
  S32 available_page_width = MAX(viewport.w - horizontal_inset * 2, 1);
  S32 requested_content_width = MAX(
    (viewport.w * app->appearance.content_width_permille) / 1000, 1);
  S32 requested_page_width = MAX(
    requested_content_width + content_inset_x * 2, 1);
  S32 comfortable_page_width = MAX(
    (viewport.h * page_width_ratio) / page_width_divisor, 1);
  ReaderViewContentGeometryStyle geometry_style = {
    .page_horizontal_inset = horizontal_inset,
    .page_max_width = MIN(
      available_page_width,
      MIN(requested_page_width, comfortable_page_width)),
    .page_min_width = 1,
    .content_inset_x = content_inset_x,
    .content_inset_y = content_inset_y,
    .content_min_width = 1,
    .content_min_height = 1,
  };
  ReaderViewContentGeometry geometry = {0};
  if (!reader_view_resolve_content_geometry(
        viewport, &geometry_style, &geometry))
  {
    return 0;
  }
  /*
   * Add one full base inset above the first row while retaining the resolved
   * bottom reserve. Shrinking the canonical content height by the same amount
   * lets Reader0 reflow normally and prevents dense pages from crowding the
   * bottom edge.
   */
  S32 top_bias = MIN(content_inset_y, geometry.content_rect.h - 1);
  geometry.content_rect.y += top_bias;
  geometry.content_rect.h -= top_bias;
  app->reader_view_layout.page_surface_rect = geometry.page_surface_rect;
  app->reader_view_layout.content_rect = geometry.content_rect;
  if (app->reader_view_layout.progress_visible)
  {
    app->reader_view_layout.progress_rect.x = geometry.page_surface_rect.x;
    app->reader_view_layout.progress_rect.w = geometry.page_surface_rect.w;
  }
  return 1;
}

static int
octavo_android_build_reader_frame(OctavoAndroidApp *app)
{
  if (!app || !app->reader_initialized)
  {
    return 0;
  }
  UI0Rect content = app->reader_view_layout.content_rect;
  if (content.w <= 0 || content.h <= 0)
  {
    return 0;
  }

  B32 had_published_frame =
    app->reader_frame.ready && app->reader_frame.document_open;
  if (!app->reader_frame.ready || app->pagination_dirty ||
      app->pagination_content_width != content.w ||
      app->pagination_content_height != content.h)
  {
    U32 spine_count = 0;
    if (doc_engine_get_page_count(epub_reader_engine(&app->reader),
                                  epub_reader_document_id(&app->reader),
                                  &spine_count) != DocError_Ok ||
        spine_count == 0)
    {
      return 0;
    }
    if (!app->typography.ready)
    {
      return 0;
    }
    S32 char_advance = MAX(
      octavo_android_typography_advance(
      &app->typography, 'n', 0, 1000u),
      1);
    S32 line_height = MAX(app->typography.line_advance_px, 1);
    U32 wrap_columns = (U32)MAX(content.w / char_advance, 1);
    U32 page_rows = (U32)MAX(content.h / line_height, 1);
    wrap_columns = MIN(wrap_columns, 512u);
    page_rows = MIN(page_rows, 512u);
    String8 canonical_uri = epub_reader_canonical_uri(&app->reader);
    app->layout_key = (EpubReaderLayoutKey){
      .document_id = epub_reader_document_id(&app->reader),
      .source_uri_hash = u64_hash_str8(canonical_uri),
      .source_uri_size = canonical_uri.size,
      .spine_count = spine_count,
      .wrap_cols = wrap_columns,
      .page_rows = page_rows,
      .text_w = content.w,
      .char_advance = char_advance,
      .line_height = line_height,
      .text_scale = app->typography.text_px,
      .margin_unit_permille = 1000,
      .font_family_index = (U32)app->appearance.font_family,
      .embedded_fonts_enabled = 0,
      .text_mode = EpubReaderTextMode_LegacyCodepoint,
    };
    app->layout_config = (SourceReaderLayoutConfig){
      .wrap_column_count = wrap_columns,
      .page_row_count = page_rows,
      .margin_unit_permille = 1000,
      .text_width_px = content.w,
      .char_advance_px = char_advance,
      .text_scale = app->typography.text_px,
      .focused_spine_index = app->reader.active_spine_index,
      .measure_text = octavo_android_measure_reader_text,
      .measure_user = app,
    };
    B32 pagination_ready = 0;
    if (app->restore_requested && !app->restore_attempted)
    {
      app->restore_attempted = 1;
      app->layout_config.focused_spine_index = app->resume_spine_index;
      EpubReaderNavigationResult navigation = {0};
      EpubReaderResult restore_result = epub_reader_navigate_to_location(
        &app->reader,
        app->resume_spine_index,
        app->resume_byte_offset,
        EpubReaderNavigationReason_Location,
        app->layout_key,
        app->layout_config,
        (EpubReaderNavigationOptions){.suppress_history = 1},
        &navigation);
      U64 target_byte = navigation.target_byte_offset;
      if (restore_result == EpubReaderResult_Ok &&
          app->reader.has_current_page &&
          app->reader.current_page.spine_index == app->resume_spine_index &&
          app->reader.current_page.first_byte <= target_byte &&
          app->reader.current_page.one_past_last_byte > target_byte)
      {
        app->resume_byte_offset = target_byte;
        app->restore_succeeded = 1;
        pagination_ready = 1;
      }
      else
      {
        app->restore_failure_count += 1u;
        __android_log_print(
          ANDROID_LOG_WARN,
          "8vo",
          "Android Port 7 saved-location restore failed result=%d target=%u:%llu",
          (int)restore_result,
          (unsigned)app->resume_spine_index,
          (unsigned long long)app->resume_byte_offset);
      }
    }
    if (!pagination_ready && had_published_frame &&
        (app->page_move_waiting_for_present ||
         app->presented_anchor_valid))
    {
      if (!app->reflow_waiting_for_present)
      {
        app->reflow_anchor_spine_index =
          app->semantic_navigation_waiting_for_present ?
            app->semantic_navigation_expected_spine_index :
          (app->page_move_waiting_for_present ?
            app->page_move_expected_spine_index :
            app->presented_anchor_spine_index);
        app->reflow_anchor_byte_offset =
          app->semantic_navigation_waiting_for_present ?
            app->semantic_navigation_expected_byte_offset :
          (app->page_move_waiting_for_present ?
            app->page_move_expected_byte_offset :
            app->presented_anchor_byte_offset);
        app->reflow_waiting_for_present = 1;
        app->reflow_request_count += 1u;
      }
      app->layout_config.focused_spine_index =
        app->reflow_anchor_spine_index;
      EpubReaderNavigationResult navigation = {0};
      EpubReaderResult reflow_result = epub_reader_navigate_to_location(
        &app->reader,
        app->reflow_anchor_spine_index,
        app->reflow_anchor_byte_offset,
        EpubReaderNavigationReason_Location,
        app->layout_key,
        app->layout_config,
        (EpubReaderNavigationOptions){.suppress_history = 1},
        &navigation);
      U64 target_byte = app->reflow_anchor_byte_offset;
      if (reflow_result != EpubReaderResult_Ok ||
          !app->reader.has_current_page ||
          app->reader.current_page.spine_index !=
            app->reflow_anchor_spine_index ||
          app->reader.current_page.first_byte > target_byte ||
          app->reader.current_page.one_past_last_byte <= target_byte)
      {
        app->reflow_failure_count += 1u;
        return 0;
      }
      pagination_ready = 1;
    }
    if (!pagination_ready)
    {
      app->layout_config.focused_spine_index =
        app->reader.active_spine_index;
    }
    if (!pagination_ready &&
        !epub_reader_rebuild_pagination(
          &app->reader, app->layout_key, app->layout_config, 0))
    {
      return 0;
    }
    app->layout_config.focused_spine_index = app->reader.active_spine_index;
    app->pagination_content_width = content.w;
    app->pagination_content_height = content.h;
    app->pagination_dirty = 0;
  }

  return epub_reader_build_frame(
    &app->reader, &app->reader_frame_storage, &app->reader_frame);
}

static int
octavo_android_build_reader_view(OctavoAndroidApp *app)
{
  if (!app || !app->reader_frame.ready ||
      !app->reader_frame.document_open)
  {
    return 0;
  }

  ReaderViewProjection projection = {0};
  projection.document_key = (UI0U64)app->reader_frame.document_id;
  projection.features =
    ReaderViewFeature_Paging | ReaderViewFeature_Progress;
  projection.document_flags =
    ReaderViewDocument_Open | ReaderViewDocument_DistractionFree;
  if (app->reader_frame.spine_index > 0 ||
      app->reader_frame.page_index > 1)
  {
    projection.document_flags |= ReaderViewDocument_CanGoPreviousPage;
  }
  if (app->reader_frame.spine_index + 1u <
        app->reader_frame.section_count ||
      app->reader_frame.page_count == 0 ||
      app->reader_frame.page_index < app->reader_frame.page_count)
  {
    projection.document_flags |= ReaderViewDocument_CanGoNextPage;
  }
  projection.content.state = ReaderViewLoad_Ready;
  projection.chrome_title =
    octavo_android_reader_view_text(app->document_title);
  projection.document_title =
    octavo_android_reader_view_text(app->document_title);
  projection.labels = reader_view_default_english_labels();
  projection.find.active_index = -1;
  projection.progress.status.state = ReaderViewLoad_Ready;
  projection.progress.chapter =
    octavo_android_reader_view_text(app->document_title);
  if (app->reader_frame.page_count > 0 &&
      app->reader_frame.page_index > 0)
  {
    projection.progress.page_index = app->reader_frame.page_index - 1u;
    projection.progress.page_count = app->reader_frame.page_count;
  }
  if (app->reader_frame.location.available &&
      app->reader_frame.location.location_index > 0 &&
      app->reader_frame.location.location_count > 0)
  {
    projection.progress.location_index =
      app->reader_frame.location.location_index - 1u;
    projection.progress.location_count =
      app->reader_frame.location.location_count;
  }
  else
  {
    projection.progress.location_index = projection.progress.page_index;
    projection.progress.location_count = projection.progress.page_count;
  }

  octavo_android_format_progress_label(app);
  projection.progress.label =
    octavo_android_reader_view_text(app->progress_label);
  app->reader_view_projection = projection;

  ReaderViewInput reader_input = {0};
  ReaderViewBuildInput build_input = {
    .frame_index = ++app->reader_view_frame_index,
    .state = &app->reader_view_state,
    .layout = &app->reader_view_layout,
    .projection = &app->reader_view_projection,
    .input = &reader_input,
    .theme = &app->reader_view_theme,
  };
  app->reader_view_ready = reader_view_build(
    &build_input, &app->reader_view_storage, &app->reader_view_frame);
  if (!app->reader_view_ready)
  {
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Readerview0 rejected Port 7 projection errors=0x%x",
      app->reader_view_frame.error_flags);
  }
  return app->reader_view_ready;
}

static int
octavo_android_build_static_page(OctavoAndroidApp *app,
                                 S32 width,
                                 S32 height)
{
  return octavo_android_resolve_reader_layout(app, width, height) &&
         octavo_android_build_reader_frame(app) &&
         octavo_android_build_reader_view(app);
}

static S32
octavo_android_touch_zone(const OctavoAndroidApp *app, float x, float y)
{
  if (!app || app->width <= 0 || app->height <= 0)
  {
    return OCTAVO_ANDROID_TOUCH_ZONE_INVALID;
  }

  S32 left = MAX(app->inset_left, 0);
  S32 top = MAX(app->inset_top, 0);
  S32 right = app->width - MAX(app->inset_right, 0);
  S32 bottom = app->height - MAX(app->inset_bottom, 0);
  if (left >= right || top >= bottom ||
      x < (float)left || x >= (float)right ||
      y < (float)top || y >= (float)bottom)
  {
    return OCTAVO_ANDROID_TOUCH_ZONE_INVALID;
  }

  float third = (float)(right - left) / 3.0f;
  if (x < (float)left + third)
  {
    return -1;
  }
  if (x >= (float)right - third)
  {
    return 1;
  }
  return 0;
}

static int
octavo_android_move_page(OctavoAndroidApp *app, S32 direction)
{
  if (!app || (direction != -1 && direction != 1))
  {
    return 0;
  }
  if (!app->resumed || !app->window ||
      !app->reader_frame.ready || !app->reader_view_ready)
  {
    return 0;
  }
  if (octavo_android_presentation_pending(app))
  {
    if (app->page_move_waiting_for_present)
    {
      app->page_move_gate_block_count += 1u;
    }
    else
    {
      app->appearance_gate_block_count += 1u;
    }
    return 0;
  }

  app->layout_config.focused_spine_index = app->reader.active_spine_index;
  EpubReaderChange change = {0};
  EpubReaderResult result = epub_reader_move_page(
    &app->reader,
    direction,
    app->layout_key,
    app->layout_config,
    (EpubReaderPageMoveOptions){0},
    &change);
  if (result == EpubReaderResult_Boundary)
  {
    app->page_move_boundary_count += 1u;
    return 0;
  }
  if (result != EpubReaderResult_Ok || !change.changed ||
      !change.after.has_page ||
      change.after.global_page_index == UINT64_MAX)
  {
    app->navigation_failure_count += 1u;
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Android Port 7 page move failed result=%d diagnostic=%d",
      (int)result,
      (int)change.diagnostic);
    return 0;
  }

  octavo_android_invalidate_prepared_static_frame(app);
  app->page_move_success_count += 1u;
  app->page_move_waiting_for_present = 1;
  app->page_move_expected_spine_index = change.after.spine_index;
  app->page_move_expected_byte_offset = change.after.byte_offset;
  return 1;
}

static int
octavo_android_pending_frame_matches(OctavoAndroidApp *app)
{
  if (!app)
  {
    return 0;
  }
  B32 page_move_mismatch = 0;
  if (app->page_move_waiting_for_present)
  {
    U64 expected = app->page_move_expected_byte_offset;
    page_move_mismatch =
      !app->reader_frame.ready || !app->reader.has_current_page ||
      app->reader_frame.spine_index !=
        app->page_move_expected_spine_index;
    if (!page_move_mismatch)
    {
      page_move_mismatch = app->reflow_waiting_for_present ?
        app->reader.current_page.first_byte > expected ||
          app->reader.current_page.one_past_last_byte <= expected :
        app->reader_frame.view_byte_offset != expected;
    }
  }
  if (page_move_mismatch)
  {
    app->render_failure_count += 1u;
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Android Port 7 refused page mismatch "
      "expected=%u:%llu actual=%u:%llu",
      (unsigned)app->page_move_expected_spine_index,
      (unsigned long long)app->page_move_expected_byte_offset,
      (unsigned)app->reader_frame.spine_index,
      (unsigned long long)app->reader_frame.view_byte_offset);
    return 0;
  }
  if (app->semantic_navigation_waiting_for_present)
  {
    U64 expected = app->semantic_navigation_expected_byte_offset;
    if (!app->reader_frame.ready || !app->reader.has_current_page ||
        app->reader_frame.spine_index !=
          app->semantic_navigation_expected_spine_index ||
        app->reader.current_page.first_byte > expected ||
        app->reader.current_page.one_past_last_byte <= expected)
    {
      app->render_failure_count += 1u;
      app->semantic_navigation_failure_count += 1u;
      __android_log_print(
        ANDROID_LOG_ERROR,
        "8vo",
        "Android Port 8 refused structural target %u:%llu",
        (unsigned)app->semantic_navigation_expected_spine_index,
        (unsigned long long)expected);
      return 0;
    }
  }
  if (app->reflow_waiting_for_present)
  {
    U64 anchor = app->reflow_anchor_byte_offset;
    if (!app->reader_frame.ready || !app->reader.has_current_page ||
        app->reader_frame.spine_index !=
          app->reflow_anchor_spine_index ||
        app->reader.current_page.first_byte > anchor ||
        app->reader.current_page.one_past_last_byte <= anchor)
    {
      app->render_failure_count += 1u;
      app->reflow_failure_count += 1u;
      __android_log_print(
        ANDROID_LOG_ERROR,
        "8vo",
        "Android Port 7 refused reflow outside anchor %u:%llu",
        (unsigned)app->reflow_anchor_spine_index,
        (unsigned long long)anchor);
      return 0;
    }
  }
  return 1;
}

static void
octavo_android_draw_failure_frame(OctavoAndroidApp *app,
                                  OctavoAndroidPixels pixels)
{
  if (!app || !pixels.data || pixels.width <= 0 || pixels.height <= 0)
  {
    return;
  }
  UI0Rect bounds = octavo_android_pixel_bounds(pixels);
  const UI0ResolvedTheme *theme =
    app->presented_reader_view_theme_valid ?
      &app->presented_reader_view_theme : &app->reader_view_theme;
  UI0Color background = theme->colors[UI0ColorRole_AppBackground];
  UI0Color surface = theme->colors[UI0ColorRole_SurfaceElevated];
  UI0Color danger = theme->colors[UI0ColorRole_Danger];
  octavo_android_fill_rect(pixels, bounds, bounds, background);
  S32 panel_width = MIN(MAX((bounds.w * 3) / 5, 64), 480);
  S32 panel_height = MIN(MAX(bounds.h / 20, 24), 96);
  UI0Rect panel = ui0_rect(
    bounds.x + (bounds.w - panel_width) / 2,
    bounds.y + (bounds.h - panel_height) / 2,
    panel_width,
    panel_height);
  octavo_android_fill_rect(pixels, panel, bounds, surface);
  octavo_android_stroke_rect(pixels, panel, bounds, danger, 3);
}

static int
octavo_android_present_frame(OctavoAndroidApp *app)
{
  if (!app || !app->window || !app->resumed)
  {
    return 0;
  }
  if (app->semantic_navigation_waiting_for_present &&
      (app->semantic_navigation_lifecycle_generation !=
         app->lifecycle_generation ||
       app->semantic_navigation_surface_generation !=
         app->surface_generation))
  {
    app->semantic_navigation_failure_count += 1u;
    if (!octavo_android_abort_structural_navigation(app))
    {
      __android_log_print(
        ANDROID_LOG_ERROR,
        "8vo",
        "Unable to restore the presented reader after a stale structural "
        "navigation transaction");
    }
    return 0;
  }
  S32 prepared_width = ANativeWindow_getWidth(app->window);
  S32 prepared_height = ANativeWindow_getHeight(app->window);
  if (prepared_width <= 0) { prepared_width = app->width; }
  if (prepared_height <= 0) { prepared_height = app->height; }
  if (!octavo_android_prepared_static_frame_matches(
        app, app->window, prepared_width, prepared_height))
  {
    app->render_failure_count += 1u;
    octavo_android_reject_prepared_static_frame(app);
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Refusing to present a missing or stale prepared reader frame");
    return 0;
  }
  app->prepared_static_frame_present_reuse_count += 1u;

  int first_frame_timing = !app->first_frame_timing_finished;
  uint64_t present_started_millis = first_frame_timing ?
    octavo_android_uptime_millis() : 0u;
  uint64_t geometry_set_millis = 0;
  uint64_t buffer_locked_millis = 0;
  uint64_t page_built_millis = 0;
  uint64_t page_filled_millis = 0;
  uint64_t text_drawn_millis = 0;
  uint64_t posted_millis = 0;

  if (app->forced_pre_present_failures_for_testing > 0)
  {
    app->forced_pre_present_failures_for_testing -= 1;
    app->render_failure_count += 1u;
    __android_log_print(
      ANDROID_LOG_WARN,
      "8vo",
      "Forcing a pre-publication failure for the Port 7 gate probe");
    return 0;
  }

  if (ANativeWindow_setBuffersGeometry(app->window,
                                       0,
                                       0,
                                       WINDOW_FORMAT_RGBA_8888) != 0)
  {
    app->render_failure_count += 1u;
    __android_log_print(
      ANDROID_LOG_ERROR, "8vo", "Unable to configure the Android frame buffer");
    return 0;
  }

  if (first_frame_timing)
  {
    geometry_set_millis = octavo_android_uptime_millis();
  }
  ANativeWindow_Buffer buffer;
  memset(&buffer, 0, sizeof(buffer));
  if (ANativeWindow_lock(app->window, &buffer, 0) != 0)
  {
    app->render_failure_count += 1u;
    __android_log_print(
      ANDROID_LOG_ERROR, "8vo", "Unable to lock the Android frame buffer");
    return 0;
  }

  if (!buffer.bits || buffer.width <= 0 || buffer.height <= 0 ||
      buffer.stride < buffer.width ||
      buffer.format != WINDOW_FORMAT_RGBA_8888)
  {
    app->render_failure_count += 1u;
    (void)ANativeWindow_unlockAndPost(app->window);
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Unexpected Android frame buffer geometry or format");
    return 0;
  }

  if (first_frame_timing)
  {
    buffer_locked_millis = octavo_android_uptime_millis();
  }
  OctavoAndroidPixels pixels = {
    .data = (uint8_t *)buffer.bits,
    .width = buffer.width,
    .height = buffer.height,
    .stride = buffer.stride,
  };
  if (!octavo_android_prepared_static_frame_matches(
        app, app->window, (S32)buffer.width, (S32)buffer.height))
  {
    app->render_failure_count += 1u;
    octavo_android_draw_failure_frame(app, pixels);
    (void)ANativeWindow_unlockAndPost(app->window);
    octavo_android_reject_prepared_static_frame(app);
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Prepared reader frame did not match the locked Android buffer");
    return 0;
  }
  if (first_frame_timing)
  {
    /* Static preparation happened before the Java image gate. The accepted
       first-frame total still includes it through reader_entry_started. */
    page_built_millis = buffer_locked_millis;
  }
  if (!octavo_android_pending_frame_matches(app))
  {
    octavo_android_draw_failure_frame(app, pixels);
    (void)ANativeWindow_unlockAndPost(app->window);
    return 0;
  }

  UI0Rect bounds = octavo_android_pixel_bounds(pixels);
  UI0Color page_color =
    app->reader_view_theme.colors[UI0ColorRole_Surface];
  /*
   * The reader is one continuous page, not a card placed on an app surface.
   * Keeping the full native layer page-colored also makes the host's uniform
   * chrome composition seamless at its exposed edges.
   */
  octavo_android_fill_rect(pixels, bounds, bounds, page_color);
  if (first_frame_timing)
  {
    page_filled_millis = octavo_android_uptime_millis();
  }

  octavo_android_attach_frame_images(app);
  if (!octavo_android_frame_images_prepared(app))
  {
    app->render_failure_count += 1u;
    octavo_android_draw_failure_frame(app, pixels);
    (void)ANativeWindow_unlockAndPost(app->window);
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Refusing to present an unprepared Android reader image");
    return 0;
  }
  if (!octavo_android_draw_reader_images(app, pixels))
  {
    app->render_failure_count += 1u;
    octavo_android_draw_failure_frame(app, pixels);
    (void)ANativeWindow_unlockAndPost(app->window);
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Unable to present bounded Android reader media");
    return 0;
  }

  OctavoAndroidJustificationEvidence justification_evidence = {0};
  if (!octavo_android_draw_reader_text(
        app, pixels, &justification_evidence))
  {
    app->render_failure_count += 1u;
    octavo_android_draw_failure_frame(app, pixels);
    (void)ANativeWindow_unlockAndPost(app->window);
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Unable to resolve the bounded Android reader justification plan");
    return 0;
  }
  octavo_android_draw_selection_handles(app, pixels);
  if (first_frame_timing)
  {
    text_drawn_millis = octavo_android_uptime_millis();
  }
  if (app->chrome_visible && !OCTAVO_ANDROID_HOST_OWNS_READER_CHROME)
  {
    octavo_android_draw_reader_view(app, pixels);
  }

  if (ANativeWindow_unlockAndPost(app->window) != 0)
  {
    app->render_failure_count += 1u;
    __android_log_print(
      ANDROID_LOG_ERROR, "8vo", "Unable to present the Android frame buffer");
    return 0;
  }

  posted_millis = first_frame_timing ?
    octavo_android_uptime_millis() : 0u;
  if (app->forced_present_failures_for_testing > 0)
  {
    app->forced_present_failures_for_testing -= 1;
    app->render_failure_count += 1u;
    __android_log_print(
      ANDROID_LOG_WARN,
      "8vo",
      "Forcing a post-presentation failure for the Port 7 gate probe");
    return 0;
  }

  app->format = buffer.format;
  app->width = buffer.width;
  app->height = buffer.height;
  app->justification_evidence = justification_evidence;
  app->frame_count += 1u;
  app->host_frame_waiting_for_present = 0;
  if (app->selection_mutation_waiting_for_present)
  {
    app->selection_mutation_waiting_for_present = 0;
    app->selection_presented_generation = app->selection_generation;
    app->selection_mutation_previous = (DocSelection){0};
    app->selection_mutation_previous_valid = 0;
  }
  if (app->search_mutation_waiting_for_present)
  {
    app->search_mutation_waiting_for_present = 0;
    app->search_mutation_presented_generation =
      app->search_mutation_generation;
    app->search_mutation_previous =
      (OctavoAndroidSearchState){0};
  }
  if (app->page_move_waiting_for_present)
  {
    app->presented_anchor_spine_index =
      app->page_move_expected_spine_index;
    app->presented_anchor_byte_offset =
      app->page_move_expected_byte_offset;
    app->presented_anchor_valid = 1;
    app->page_move_waiting_for_present = 0;
    app->page_move_expected_spine_index = 0;
    app->page_move_expected_byte_offset = 0;
    app->page_move_presented_count += 1u;
  }
  if (app->selection_page_turn_waiting_for_present)
  {
    octavo_android_selection_page_turn_state_clear(app);
  }
  if (app->semantic_navigation_waiting_for_present)
  {
    if (!octavo_android_commit_structural_navigation(app))
    {
      return 0;
    }
  }
  if (app->reflow_waiting_for_present)
  {
    U64 anchor = app->reflow_anchor_byte_offset;
    if (!app->reader_frame.ready || !app->reader.has_current_page ||
        app->reader_frame.spine_index !=
          app->reflow_anchor_spine_index ||
        app->reader.current_page.first_byte > anchor ||
        app->reader.current_page.one_past_last_byte <= anchor)
    {
      app->render_failure_count += 1u;
      app->reflow_failure_count += 1u;
      return 0;
    }
    app->reflow_waiting_for_present = 0;
    app->reflow_success_count += 1u;
  }
  if (!app->presented_anchor_valid)
  {
    app->presented_anchor_spine_index = app->restore_succeeded ?
      app->resume_spine_index : app->reader_frame.spine_index;
    app->presented_anchor_byte_offset = app->restore_succeeded ?
      app->resume_byte_offset : app->reader_frame.view_byte_offset;
    app->presented_anchor_valid = 1;
  }
  app->presented_history_back_count = app->reader.back_stack_count;
  app->presented_history_forward_count = app->reader.forward_stack_count;
  if (app->appearance_waiting_for_present)
  {
    app->appearance_presented_generation =
      app->appearance_generation;
    app->appearance_waiting_for_present = 0;
  }
  if (app->progress_display_waiting_for_present)
  {
    app->progress_display_presented_mode = app->progress_display_mode;
    app->progress_display_presented_generation =
      app->progress_display_generation;
    app->progress_display_waiting_for_present = 0;
  }
  app->presented_reader_view_theme = app->reader_view_theme;
  app->presented_reader_view_theme_valid = 1;
  if (first_frame_timing)
  {
    int timing_chain_valid =
      app->reader_entry_started_millis > 0 &&
      present_started_millis > 0 &&
      geometry_set_millis >= present_started_millis &&
      buffer_locked_millis >= geometry_set_millis &&
      page_built_millis >= buffer_locked_millis &&
      page_filled_millis >= page_built_millis &&
      text_drawn_millis >= page_filled_millis &&
      posted_millis >= text_drawn_millis &&
      posted_millis >= app->reader_entry_started_millis;
    app->first_frame_timing_finished = 1;
    if (timing_chain_valid)
    {
      app->first_frame_elapsed_millis =
        MAX(posted_millis - app->reader_entry_started_millis, UINT64_C(1));
      __android_log_print(
        ANDROID_LOG_INFO,
        "8vo",
        "Android Port 7 accepted first-frame stages geometry=%llu lock=%llu "
        "build=%llu fill=%llu draw=%llu post=%llu total=%llu",
        (unsigned long long)(geometry_set_millis - present_started_millis),
        (unsigned long long)(buffer_locked_millis - geometry_set_millis),
        (unsigned long long)(page_built_millis - buffer_locked_millis),
        (unsigned long long)(page_filled_millis - page_built_millis),
        (unsigned long long)(text_drawn_millis - page_filled_millis),
        (unsigned long long)(posted_millis - text_drawn_millis),
        (unsigned long long)(posted_millis - present_started_millis));
    }
    else
    {
      __android_log_print(
        ANDROID_LOG_WARN,
        "8vo",
        "Android Port 7 accepted first-frame timing unavailable");
    }
  }
  __android_log_print(
    ANDROID_LOG_INFO,
    "8vo",
    "Android Port 7 frame=%llu surface=%llu size=%dx%d page=%llu/%llu "
    "reader_bytes=%llu reader_hash=%016llx view_draws=%d first_ms=%llu "
    "missing_glyphs=%llu",
    (unsigned long long)app->frame_count,
    (unsigned long long)app->surface_generation,
    app->width,
    app->height,
    (unsigned long long)app->reader_frame.page_index,
    (unsigned long long)app->reader_frame.page_count,
    (unsigned long long)app->reader_frame.visible_text.size,
    (unsigned long long)u64_hash_str8(app->reader_frame.visible_text),
    app->reader_view_frame.draw_command_count,
    (unsigned long long)app->first_frame_elapsed_millis,
    (unsigned long long)app->typography.missing_glyph_count);
  octavo_android_consume_prepared_static_frame(app);
  return 1;
}

static UI0Color
octavo_android_ui0_tree_fill(UI0DrawTheme theme, UI0TreeStateFlags state)
{
  UI0DrawCommand commands[8];
  UI0DrawContext draw;
  UI0TreeRecord record;
  memset(commands, 0, sizeof(commands));
  memset(&record, 0, sizeof(record));
  ui0_draw_context_init(&draw);
  ui0_draw_begin_frame(&draw, commands, 8, theme);
  record.id = 1;
  record.tree_id = 1;
  record.state = state;
  record.rect = ui0_rect(0, 0, 48, 48);
  record.hit_rect = record.rect;
  record.clip_rect = record.rect;
  record.text_rect = record.rect;
  record.expander_rect = ui0_rect(0, 0, 1, 1);
  record.current_rect = ui0_rect(0, 0, 2, 48);
  record.label_hash = 1;
  record.label_len = 1;
  (void)ui0_tree_draw_record(&draw, &record);
  for (UI0S32 index = 0; index < draw.command_count; index += 1)
  {
    if (commands[index].op == UI0DrawOp_ControlFill)
    {
      return commands[index].color;
    }
  }
  return 0;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_version(JNIEnv *environment, jclass type)
{
  (void)type;
  return (*environment)->NewStringUTF(environment, OCTAVO_VERSION_STRING);
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_platform(JNIEnv *environment, jclass type)
{
  (void)type;
  return (*environment)->NewStringUTF(environment, "android");
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_groundVersion(JNIEnv *environment,
                                                 jclass type)
{
  (void)type;
  return (*environment)->NewStringUTF(
    environment, ZERO_FOUNDATION_VERSION_STRING);
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_readerVersion(JNIEnv *environment,
                                                 jclass type)
{
  (void)type;
  return (*environment)->NewStringUTF(environment, READER0_VERSION_STRING);
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_uiVersion(JNIEnv *environment, jclass type)
{
  (void)type;
  return (*environment)->NewStringUTF(environment, UI0_VERSION_STRING);
}

JNIEXPORT jintArray JNICALL
Java_ro_devze_octavo_OctavoNative_ui0AndroidThemeSnapshot(
  JNIEnv *environment,
  jclass type,
  jboolean dark_appearance,
  jintArray appearance_colors)
{
  (void)type;
  if (!environment || !appearance_colors ||
      (*environment)->GetArrayLength(environment, appearance_colors) !=
        UI0ColorRole_Count)
  {
    return 0;
  }
  jint colors[UI0ColorRole_Count];
  (*environment)->GetIntArrayRegion(
    environment, appearance_colors, 0, UI0ColorRole_Count, colors);
  if ((*environment)->ExceptionCheck(environment))
  {
    return 0;
  }

  UI0ThemeProfile profile = ui0_theme_profile_for_kind(
    dark_appearance ? UI0ThemeProfile_Dark : UI0ThemeProfile_Light);
  UI0TokenPatch patch = ui0_token_patch(profile.resolved.kind);
  for (UI0S32 role = 0; role < UI0ColorRole_Count; role += 1)
  {
    ui0_token_patch_set_color(
      &patch, (UI0ColorRole)role, (UI0Color)(uint32_t)colors[role]);
  }
  const UI0DensityRole interactive_density[] =
  {
    UI0DensityRole_ControlHeight,
    UI0DensityRole_IconButtonSize,
    UI0DensityRole_RowMinHeight,
    UI0DensityRole_MenuItemHeight,
  };
  for (UI0U32 index = 0;
       index < sizeof(interactive_density) / sizeof(interactive_density[0]);
       index += 1)
  {
    UI0DensityRole role = interactive_density[index];
    UI0S32 value = profile.resolved.density[role];
    ui0_token_patch_set_density(&patch, role, value < 48 ? 48 : value);
  }

  UI0ResolvedTheme resolved =
    ui0_resolve_token_patch(&profile.resolved, &patch);
  UI0DrawTheme draw = ui0_draw_theme_from_resolved(&resolved);
  UI0TreeStyle tree = ui0_tree_style_from_resolved(&resolved);
  UI0ControlStyle control = ui0_control_style_from_resolved(&resolved);
  UI0TextInputStyle text_input =
    ui0_text_input_style_from_resolved(&resolved);
  jint packet[OCTAVO_ANDROID_UI0_SNAPSHOT_PACKET_COUNT];
  memset(packet, 0, sizeof(packet));
  packet[0] = OCTAVO_ANDROID_UI0_SNAPSHOT_MAGIC;
  packet[1] = OCTAVO_ANDROID_UI0_SNAPSHOT_VERSION;
  packet[2] = UI0_API_VERSION;
  packet[3] = OCTAVO_ANDROID_UI0_SNAPSHOT_PACKET_COUNT;
  packet[4] = (jint)resolved.kind;
  packet[5] = UI0ColorRole_Count;
  packet[6] = UI0SpacingRole_Count;
  packet[7] = UI0RadiusRole_Count;
  packet[8] = UI0TypographyRole_Count;
  packet[9] = OCTAVO_ANDROID_UI0_SNAPSHOT_TYPOGRAPHY_STRIDE;
  packet[10] = UI0DensityRole_Count;
  packet[11] = UI0StateRole_Count;
  packet[12] = OCTAVO_ANDROID_UI0_SNAPSHOT_STATE_STRIDE;
  packet[13] = UI0DrawState_Count;
  packet[14] = OCTAVO_ANDROID_UI0_SNAPSHOT_DRAW_STRIDE;
  packet[15] = OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_COUNT;
  packet[16] = OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_COUNT;
  packet[17] = OCTAVO_ANDROID_UI0_SNAPSHOT_SUMMARY_COUNT;
  packet[18] = OCTAVO_ANDROID_UI0_SNAPSHOT_TEXT_INPUT_COUNT;
  packet[19] = 0;

  for (UI0S32 role = 0; role < UI0ColorRole_Count; role += 1)
  {
    packet[OCTAVO_ANDROID_UI0_SNAPSHOT_COLOR_OFFSET + role] =
      (jint)resolved.colors[role];
  }
  for (UI0S32 role = 0; role < UI0SpacingRole_Count; role += 1)
  {
    packet[OCTAVO_ANDROID_UI0_SNAPSHOT_SPACING_OFFSET + role] =
      (jint)resolved.spacing[role];
  }
  for (UI0S32 role = 0; role < UI0RadiusRole_Count; role += 1)
  {
    packet[OCTAVO_ANDROID_UI0_SNAPSHOT_RADIUS_OFFSET + role] =
      (jint)resolved.radius[role];
  }
  for (UI0S32 role = 0; role < UI0TypographyRole_Count; role += 1)
  {
    UI0S32 offset = OCTAVO_ANDROID_UI0_SNAPSHOT_TYPOGRAPHY_OFFSET +
      role * OCTAVO_ANDROID_UI0_SNAPSHOT_TYPOGRAPHY_STRIDE;
    packet[offset] = (jint)resolved.typography[role].char_width;
    packet[offset + 1] = (jint)resolved.typography[role].line_height;
  }
  for (UI0S32 role = 0; role < UI0DensityRole_Count; role += 1)
  {
    packet[OCTAVO_ANDROID_UI0_SNAPSHOT_DENSITY_OFFSET + role] =
      (jint)resolved.density[role];
  }
  for (UI0S32 role = 0; role < UI0StateRole_Count; role += 1)
  {
    UI0S32 offset = OCTAVO_ANDROID_UI0_SNAPSHOT_STATE_OFFSET +
      role * OCTAVO_ANDROID_UI0_SNAPSHOT_STATE_STRIDE;
    packet[offset] = (jint)resolved.state[role].fill_role;
    packet[offset + 1] = (jint)resolved.state[role].text_role;
    packet[offset + 2] = (jint)resolved.state[role].border_role;
  }
  for (UI0S32 state = 0; state < UI0DrawState_Count; state += 1)
  {
    UI0S32 offset = OCTAVO_ANDROID_UI0_SNAPSHOT_DRAW_OFFSET +
      state * OCTAVO_ANDROID_UI0_SNAPSHOT_DRAW_STRIDE;
    packet[offset] = (jint)draw.state[state].fill;
    packet[offset + 1] = (jint)draw.state[state].text;
    packet[offset + 2] = (jint)draw.state[state].border;
  }

  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_OFFSET] = (jint)tree.row_height;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_OFFSET + 1] = (jint)tree.row_gap;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_OFFSET + 2] = (jint)tree.padding_x;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_OFFSET + 3] = (jint)tree.text_height;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_OFFSET + 4] = (jint)tree.indent_width;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_OFFSET + 5] = (jint)tree.expander_size;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_OFFSET + 6] = (jint)tree.expander_gap;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_OFFSET + 7] =
    (jint)tree.current_bar_width;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_OFFSET + 8] =
    (jint)tree.current_bar_gap;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TREE_OFFSET + 9] = (jint)tree.char_width;

  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_OFFSET] = (jint)control.padding_x;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_OFFSET + 1] =
    (jint)control.padding_y;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_OFFSET + 2] =
    (jint)control.segment_padding_x;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_OFFSET + 3] =
    (jint)control.indicator_size;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_OFFSET + 4] =
    (jint)control.indicator_gap;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_OFFSET + 5] =
    (jint)control.toggle_width;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_OFFSET + 6] =
    (jint)control.toggle_height;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_OFFSET + 7] =
    (jint)control.char_width;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_CONTROL_OFFSET + 8] =
    (jint)control.text_height;

  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_SUMMARY_OFFSET] =
    (jint)octavo_android_ui0_tree_fill(
      draw, UI0TreeState_Focused | UI0TreeState_FocusVisible);
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_SUMMARY_OFFSET + 1] =
    (jint)octavo_android_ui0_tree_fill(draw, UI0TreeState_Selected);
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_SUMMARY_OFFSET + 2] =
    (jint)draw.focus_color;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_SUMMARY_OFFSET + 3] =
    (jint)draw.focus_color;

  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TEXT_INPUT_OFFSET] =
    (jint)text_input.padding_x;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TEXT_INPUT_OFFSET + 1] =
    (jint)text_input.padding_y;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TEXT_INPUT_OFFSET + 2] =
    (jint)text_input.text_height;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TEXT_INPUT_OFFSET + 3] =
    (jint)text_input.caret_width;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TEXT_INPUT_OFFSET + 4] =
    (jint)text_input.min_selection_width;
  packet[OCTAVO_ANDROID_UI0_SNAPSHOT_TEXT_INPUT_OFFSET + 5] =
    (jint)text_input.measure.fallback_char_width;

  jintArray result = (*environment)->NewIntArray(
    environment, OCTAVO_ANDROID_UI0_SNAPSHOT_PACKET_COUNT);
  if (!result)
  {
    return 0;
  }
  (*environment)->SetIntArrayRegion(
    environment,
    result,
    0,
    OCTAVO_ANDROID_UI0_SNAPSHOT_PACKET_COUNT,
    packet);
  if ((*environment)->ExceptionCheck(environment))
  {
    return 0;
  }
  return result;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_readerViewVersion(JNIEnv *environment,
                                                     jclass type)
{
  (void)type;
  return (*environment)->NewStringUTF(environment, READERVIEW0_VERSION_STRING);
}

JNIEXPORT jlong JNICALL
Java_ro_devze_octavo_OctavoNative_create(JNIEnv *environment,
                                         jclass type,
                                         jstring files_path,
                                         jstring cache_path,
                                         jstring document_path,
                                         jlong resume_spine_index,
                                         jlong resume_byte_offset,
                                         jboolean resume_requested,
                                         jboolean chrome_visible,
                                         jintArray appearance_config,
                                         jintArray appearance_colors,
                                         jintArray typography_metrics,
                                         jbyteArray typography_alpha,
                                         jlong reader_entry_started_millis)
{
  (void)type;
  uint64_t created_millis = octavo_android_uptime_millis();
  OctavoAndroidApp *app = (OctavoAndroidApp *)calloc(1, sizeof(*app));
  if (!app)
  {
    return 0;
  }
  if (reader_entry_started_millis > 0 &&
      created_millis > 0 &&
      (uint64_t)reader_entry_started_millis <= created_millis)
  {
    app->reader_entry_started_millis =
      (uint64_t)reader_entry_started_millis;
  }
  else
  {
    __android_log_print(
      ANDROID_LOG_WARN,
      "8vo",
      "Android Port 7 reader-entry timing unavailable");
  }

  if (resume_requested &&
      (resume_spine_index < 0 ||
       (uint64_t)resume_spine_index > UINT32_MAX ||
       resume_byte_offset < 0))
  {
    free(app);
    return 0;
  }
  app->restore_requested = resume_requested ? 1 : 0;
  app->resume_spine_index = (U32)resume_spine_index;
  app->resume_byte_offset = (U64)resume_byte_offset;
  app->chrome_visible = chrome_visible ? 1 : 0;
  app->progress_display_mode = 3;
  app->progress_display_presented_mode = 3;
  app->progress_display_generation = 1u;
  app->progress_display_presented_generation = 1u;
  app->appearance_generation = 1;
  app->appearance_waiting_for_present = 1;
  app->pagination_dirty = 1;

  if (!octavo_android_copy_path(environment,
                                files_path,
                                app->files_path,
                                sizeof(app->files_path)) ||
      !octavo_android_copy_path(environment,
                                cache_path,
                                app->cache_path,
                                sizeof(app->cache_path)) ||
      !octavo_android_copy_path(environment,
                                document_path,
                                app->document_path,
                                sizeof(app->document_path)) ||
      !octavo_android_import_appearance(environment,
                                        appearance_config,
                                        appearance_colors,
                                        &app->appearance) ||
      !octavo_android_import_typography(environment,
                                        typography_metrics,
                                        typography_alpha,
                                        &app->typography) ||
      !octavo_android_initialize_reader(app))
  {
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Unable to create the Android Port 7 reader state");
    epub_reader_release(&app->reader);
    if (app->arena)
    {
      arena_release(app->arena);
    }
    octavo_android_release_typography(&app->typography);
    octavo_android_image_cache_release(app);
    free(app);
    return 0;
  }

  __android_log_print(
    ANDROID_LOG_INFO,
    "8vo",
    "Android Port 7 state created document=%s title=%s restore=%d:%u:%llu",
    app->document_path,
    app->document_title,
    app->restore_requested,
    (unsigned)app->resume_spine_index,
    (unsigned long long)app->resume_byte_offset);
  return (jlong)(uintptr_t)app;
}

static B32
octavo_android_prepare_frame_images_with_java(
  JNIEnv *environment,
  OctavoAndroidApp *app)
{
  if (!environment || !app)
  {
    return 0;
  }
  jclass bridge = (*environment)->FindClass(
    environment, "ro/devze/octavo/OctavoReaderImageBridge");
  if (!bridge)
  {
    if ((*environment)->ExceptionCheck(environment))
    {
      (*environment)->ExceptionClear(environment);
    }
    __android_log_print(
      ANDROID_LOG_ERROR, "8vo", "Unable to resolve the reader image bridge");
    return 0;
  }
  jmethodID prepare = (*environment)->GetStaticMethodID(
    environment, bridge, "prepareFrameImages", "(J)Z");
  if (!prepare)
  {
    if ((*environment)->ExceptionCheck(environment))
    {
      (*environment)->ExceptionClear(environment);
    }
    (*environment)->DeleteLocalRef(environment, bridge);
    __android_log_print(
      ANDROID_LOG_ERROR, "8vo", "Unable to resolve reader image preparation");
    return 0;
  }
  jboolean prepared = (*environment)->CallStaticBooleanMethod(
    environment, bridge, prepare, (jlong)(uintptr_t)app);
  B32 callback_failed = (*environment)->ExceptionCheck(environment);
  if (callback_failed)
  {
    (*environment)->ExceptionClear(environment);
  }
  (*environment)->DeleteLocalRef(environment, bridge);
  if (callback_failed || prepared != JNI_TRUE)
  {
    __android_log_print(
      ANDROID_LOG_ERROR, "8vo", "Reader image preparation was rejected");
    return 0;
  }
  octavo_android_attach_frame_images(app);
  return octavo_android_frame_images_prepared(app);
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_applyAppearance(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jintArray appearance_config,
  jintArray appearance_colors,
  jintArray typography_metrics,
  jbyteArray typography_alpha)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || octavo_android_presentation_pending(app))
  {
    if (app)
    {
      app->appearance_gate_block_count += 1u;
    }
    return 2;
  }

  OctavoAndroidAppearance next_appearance = {0};
  OctavoAndroidTypography next_typography = {0};
  if (!octavo_android_import_appearance(
        environment, appearance_config, appearance_colors,
        &next_appearance) ||
      !octavo_android_import_typography(
        environment, typography_metrics, typography_alpha,
        &next_typography))
  {
    octavo_android_release_typography(&next_typography);
    app->appearance_failure_count += 1u;
    return 0;
  }

  int layout_changed = !octavo_android_appearance_layout_equal(
      &app->appearance, &next_appearance) ||
    app->typography.text_px != next_typography.text_px ||
    app->typography.line_advance_px !=
      next_typography.line_advance_px ||
    octavo_android_typography_advance(
      &app->typography, 'n', 0, 1000u) !=
      octavo_android_typography_advance(
      &next_typography, 'n', 0, 1000u);
  OctavoAndroidAppearance old_appearance = app->appearance;
  OctavoAndroidTypography old_typography = app->typography;
  octavo_android_invalidate_prepared_static_frame(app);
  app->typography = next_typography;
  app->selection_handle_radius_px = MAX(app->typography.text_px / 6, 2);
  app->appearance = next_appearance;
  octavo_android_resolve_appearance_theme(app);
  app->appearance_generation += 1u;
  app->appearance_apply_count += 1u;
  app->appearance_waiting_for_present = 1;
  if (layout_changed)
  {
    app->pagination_dirty = 1;
  }

  B32 candidate_prepared =
    octavo_android_prepare_frame_images_with_java(environment, app);
  if (candidate_prepared && octavo_android_present_frame(app))
  {
    octavo_android_release_typography(&old_typography);
    return 1;
  }
  if (app->window && app->resumed)
  {
    OctavoAndroidTypography failed_typography = app->typography;
    octavo_android_invalidate_prepared_static_frame(app);
    app->typography = old_typography;
    app->appearance = old_appearance;
    octavo_android_release_typography(&failed_typography);
    octavo_android_resolve_appearance_theme(app);
    app->appearance_generation += 1u;
    app->appearance_waiting_for_present = 1;
    app->pagination_dirty = 1;
    if (octavo_android_prepare_frame_images_with_java(environment, app))
    {
      (void)octavo_android_present_frame(app);
    }
    else
    {
      __android_log_print(
        ANDROID_LOG_ERROR, "8vo", "Unable to prepare appearance rollback");
    }
    app->appearance_failure_count += 1u;
    return 0;
  }
  octavo_android_release_typography(&old_typography);
  return 2;
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_destroy(JNIEnv *environment,
                                          jclass type,
                                          jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return;
  }
  (void)octavo_android_cancel_pending_navigation(app);
  if (app->window)
  {
    ANativeWindow_release(app->window);
    app->window = 0;
  }
  epub_reader_release(&app->reader);
  if (app->arena)
  {
    arena_release(app->arena);
    app->arena = 0;
  }
  octavo_android_release_typography(&app->typography);
  octavo_android_image_cache_release(app);
  free(app);
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_hostResumed(JNIEnv *environment,
                                              jclass type,
                                              jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || app->resumed)
  {
    return;
  }
  octavo_android_invalidate_prepared_static_frame(app);
  app->resumed = 1;
  app->resume_count += 1u;
  app->lifecycle_generation += 1u;
  app->host_frame_waiting_for_present = 1;
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_hostPaused(JNIEnv *environment,
                                             jclass type,
                                             jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->resumed)
  {
    return;
  }
  octavo_android_invalidate_prepared_static_frame(app);
  S32 cancellation = octavo_android_cancel_pending_navigation(app);
  if (cancellation < 0)
  {
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Unable to restore the presented reader before host pause");
  }
  octavo_android_discard_selection(app);
  app->resumed = 0;
  app->pause_count += 1u;
  app->lifecycle_generation += 1u;
  app->touch_active = 0;
  app->touch_direction = 0;
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_surfaceCreated(JNIEnv *environment,
                                                 jclass type,
                                                 jlong handle,
                                                 jobject surface)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !surface)
  {
    return JNI_FALSE;
  }

  if (app->forced_surface_acquisition_failures_for_testing > 0)
  {
    app->forced_surface_acquisition_failures_for_testing -= 1;
    app->render_failure_count += 1u;
    app->host_frame_waiting_for_present = 1;
    __android_log_print(
      ANDROID_LOG_WARN,
      "8vo",
      "Forcing an Android surface acquisition failure for the Port 7 probe");
    return JNI_FALSE;
  }

  ANativeWindow *window = ANativeWindow_fromSurface(environment, surface);
  if (!window)
  {
    app->render_failure_count += 1u;
    app->host_frame_waiting_for_present = 1;
    __android_log_print(
      ANDROID_LOG_ERROR, "8vo", "Unable to acquire the Android surface");
    return JNI_FALSE;
  }
  if (app->window)
  {
    S32 cancellation = octavo_android_cancel_pending_navigation(app);
    if (cancellation < 0)
    {
      ANativeWindow_release(window);
      __android_log_print(
        ANDROID_LOG_ERROR,
        "8vo",
        "Unable to restore the presented reader before surface "
        "replacement");
      return JNI_FALSE;
    }
    ANativeWindow_release(app->window);
  }
  octavo_android_invalidate_prepared_static_frame(app);
  app->window = window;
  app->width = ANativeWindow_getWidth(window);
  app->height = ANativeWindow_getHeight(window);
  app->format = ANativeWindow_getFormat(window);
  app->surface_generation += 1u;
  app->host_frame_waiting_for_present = 1;

  __android_log_print(
    ANDROID_LOG_INFO,
    "8vo",
    "Android surface generation=%llu size=%dx%d",
    (unsigned long long)app->surface_generation,
    app->width,
    app->height);
  return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_surfaceChanged(JNIEnv *environment,
                                                 jclass type,
                                                 jlong handle,
                                                 jint format,
                                                 jint width,
                                                 jint height)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return;
  }
  if (app->format != (int32_t)format ||
      app->width != (int32_t)width ||
      app->height != (int32_t)height)
  {
    octavo_android_invalidate_prepared_static_frame(app);
    S32 cancellation = octavo_android_cancel_pending_navigation(app);
    if (cancellation < 0)
    {
      __android_log_print(
        ANDROID_LOG_ERROR,
        "8vo",
        "Unable to restore the presented reader before a surface resize");
    }
    else
    {
      octavo_android_discard_selection(app);
    }
  }
  app->format = (int32_t)format;
  app->width = (int32_t)width;
  app->height = (int32_t)height;
  app->host_frame_waiting_for_present = 1;
  app->touch_active = 0;
  app->touch_direction = 0;
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_surfaceDestroyed(JNIEnv *environment,
                                                   jclass type,
                                                   jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return;
  }
  octavo_android_invalidate_prepared_static_frame(app);
  S32 cancellation = octavo_android_cancel_pending_navigation(app);
  if (cancellation < 0)
  {
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Unable to restore the presented reader before surface destruction");
  }
  octavo_android_discard_selection(app);
  if (app->window)
  {
    ANativeWindow_release(app->window);
    app->window = 0;
  }
  app->format = 0;
  app->width = 0;
  app->height = 0;
  app->host_frame_waiting_for_present = 1;
  app->surface_destroy_count += 1u;
  app->touch_active = 0;
  app->touch_direction = 0;
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_windowInsets(JNIEnv *environment,
                                               jclass type,
                                               jlong handle,
                                               jint left,
                                               jint top,
                                               jint right,
                                               jint bottom)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || left < 0 || top < 0 || right < 0 || bottom < 0)
  {
    return;
  }
  if (app->inset_left != (int32_t)left ||
      app->inset_top != (int32_t)top ||
      app->inset_right != (int32_t)right ||
      app->inset_bottom != (int32_t)bottom)
  {
    octavo_android_invalidate_prepared_static_frame(app);
  }
  app->inset_left = (int32_t)left;
  app->inset_top = (int32_t)top;
  app->inset_right = (int32_t)right;
  app->inset_bottom = (int32_t)bottom;
  app->host_frame_waiting_for_present = 1;
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_readerChromeInsets(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint top,
  jint bottom)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || top < 0 || bottom < 0)
  {
    return JNI_FALSE;
  }
  if (app->reader_chrome_inset_top == (int32_t)top &&
      app->reader_chrome_inset_bottom == (int32_t)bottom)
  {
    return JNI_TRUE;
  }

  octavo_android_invalidate_prepared_static_frame(app);
  app->reader_chrome_inset_top = (int32_t)top;
  app->reader_chrome_inset_bottom = (int32_t)bottom;
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_forcePresentFailuresForTesting(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint count)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || count < 0 || count > 8)
  {
    return JNI_FALSE;
  }
  app->forced_present_failures_for_testing = (int32_t)count;
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_forcePrePresentFailuresForTesting(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint count)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || count < 0 || count > 8)
  {
    return JNI_FALSE;
  }
  app->forced_pre_present_failures_for_testing = (int32_t)count;
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_forceLocationWarmFailuresForTesting(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint count)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || count < 0 || count > 4)
  {
    return JNI_FALSE;
  }
  app->forced_location_warm_failures_for_testing = (int32_t)count;
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_forceSurfaceAcquisitionFailuresForTesting(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint count)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || count < 0 || count > 4)
  {
    return JNI_FALSE;
  }
  app->forced_surface_acquisition_failures_for_testing = (int32_t)count;
  return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_utf8RoundTripForTesting(
  JNIEnv *environment,
  jclass type)
{
  (void)type;
  static U8 text_bytes[] =
  {
    0x52, 0xc3, 0xa9, 0x73, 0x75, 0x6d, 0xc3, 0xa9,
    0x20, 0xe2, 0x80, 0x99, 0x20, 0xf0, 0x9f, 0x8c,
    0x99,
  };
  String8 text = {0};
  text.str = text_bytes;
  text.size = ARRAY_COUNT(text_bytes);
  return octavo_android_new_utf8_string(environment, text);
}

JNIEXPORT jlongArray JNICALL
Java_ro_devze_octavo_OctavoNative_frameImagesSnapshot(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->window || !app->resumed)
  {
    return 0;
  }
  S32 width = ANativeWindow_getWidth(app->window);
  S32 height = ANativeWindow_getHeight(app->window);
  if (width <= 0) { width = app->width; }
  if (height <= 0) { height = app->height; }
  if (width <= 0 || height <= 0)
  {
    return 0;
  }
  if (octavo_android_prepared_static_frame_matches(
        app, app->window, width, height))
  {
    app->prepared_static_frame_snapshot_reuse_count += 1u;
  }
  else
  {
    if (app->prepared_static_frame.valid)
    {
      octavo_android_invalidate_prepared_static_frame(app);
    }
    app->prepared_static_frame_build_attempt_count += 1u;
    if (!octavo_android_build_static_page(app, width, height))
    {
      return 0;
    }
    app->prepared_static_frame_build_success_count += 1u;
    app->prepared_static_frame_build_serial += 1u;
    if (!octavo_android_record_prepared_static_frame(app, width, height))
    {
      return 0;
    }
  }
  octavo_android_attach_frame_images(app);
  U32 image_count = MIN(app->reader_frame.image_count,
                        (U32)EPUB_READER_FRAME_IMAGE_CAP);
  jsize value_count =
    OCTAVO_ANDROID_FRAME_IMAGE_PACKET_HEADER_COUNT +
    (jsize)image_count * OCTAVO_ANDROID_FRAME_IMAGE_PACKET_ROW_STRIDE;
  jlong values[
    OCTAVO_ANDROID_FRAME_IMAGE_PACKET_HEADER_COUNT +
    EPUB_READER_FRAME_IMAGE_CAP *
      OCTAVO_ANDROID_FRAME_IMAGE_PACKET_ROW_STRIDE] = {0};
  values[0] = OCTAVO_ANDROID_FRAME_IMAGE_PACKET_VERSION;
  values[1] = (jlong)image_count;
  for (U32 index = 0; index < image_count; ++index)
  {
    EpubReaderFrameImage *image = app->reader_frame.images + index;
    jsize at = OCTAVO_ANDROID_FRAME_IMAGE_PACKET_HEADER_COUNT +
      (jsize)index * OCTAVO_ANDROID_FRAME_IMAGE_PACKET_ROW_STRIDE;
    values[at + 0] = (jlong)image->resource_index;
    values[at + 1] = (jlong)image->status;
    values[at + 2] = image->has_resource ? 1 : 0;
    values[at + 3] = (jlong)image->row;
  }
  jlongArray result = (*environment)->NewLongArray(environment, value_count);
  if (result)
  {
    (*environment)->SetLongArrayRegion(
      environment, result, 0, value_count, values);
  }
  return result;
}

JNIEXPORT jbyteArray JNICALL
Java_ro_devze_octavo_OctavoNative_frameImageEncodedBytes(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint image_index,
  jlong byte_limit)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->reader_frame.ready || image_index < 0 ||
      (U32)image_index >= app->reader_frame.image_count ||
      (U32)image_index >= EPUB_READER_FRAME_IMAGE_CAP ||
      byte_limit <= 0 ||
      (U64)byte_limit > OCTAVO_ANDROID_IMAGE_ENCODED_BUDGET)
  {
    return 0;
  }
  EpubReaderFrameImage *image =
    app->reader_frame.images + (U32)image_index;
  if (!image->has_resource ||
      octavo_android_image_cache_find(app, image->resource_index))
  {
    return 0;
  }
  ArenaParams parameters = {
    .reserve_size = (U64)byte_limit + 1u,
    .commit_size = MIN((U64)byte_limit + 1u, KILOBYTES(64)),
  };
  Arena *encoded_arena = arena_alloc(&parameters);
  if (!encoded_arena) { return 0; }
  String8 encoded = {0};
  U64 required_size = 0;
  DocError resource_result = doc_engine_get_resource_data_bounded(
    encoded_arena,
    epub_reader_engine(&app->reader),
    epub_reader_document_id(&app->reader),
    image->resource_index,
    (U64)byte_limit,
    &encoded,
    &required_size);
  jbyteArray result = 0;
  if (resource_result == DocError_LimitExceeded &&
      required_size > (U64)byte_limit)
  {
    /* A non-null empty array is the explicit aggregate-budget sentinel.
       Reader0 rejected the entry before output allocation/decompression. */
    result = (*environment)->NewByteArray(environment, 0);
  }
  else if (resource_result == DocError_Ok && encoded.str && encoded.size > 0)
  {
    if (encoded.size <= (U64)byte_limit &&
        encoded.size <= OCTAVO_ANDROID_IMAGE_ENCODED_BUDGET &&
        encoded.size <= INT32_MAX)
    {
      result = (*environment)->NewByteArray(
        environment, (jsize)encoded.size);
    }
    if (result)
    {
      jsize result_size = (*environment)->GetArrayLength(environment, result);
      if (result_size > 0)
      {
        (*environment)->SetByteArrayRegion(
          environment,
          result,
          0,
          result_size,
          (const jbyte *)encoded.str);
      }
    }
  }
  arena_release(encoded_arena);
  return result;
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_setFrameImageDecodeResult(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint image_index,
  jint status,
  jint width,
  jint height,
  jintArray argb_pixels)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->reader_frame.ready || image_index < 0 ||
      (U32)image_index >= app->reader_frame.image_count ||
      (U32)image_index >= EPUB_READER_FRAME_IMAGE_CAP)
  {
    return JNI_FALSE;
  }
  EpubReaderFrameImage *image =
    app->reader_frame.images + (U32)image_index;
  if (!image->has_resource) { return JNI_FALSE; }
  OctavoAndroidImageCacheEntry *existing =
    octavo_android_image_cache_find(app, image->resource_index);
  if (existing)
  {
    octavo_android_image_cache_touch(app, existing);
    octavo_android_attach_frame_images(app);
    return JNI_TRUE;
  }
  EpubReaderFrameImageStatus decoded_status =
    (EpubReaderFrameImageStatus)status;
  if (decoded_status != EpubReaderFrameImageStatus_Loaded &&
      decoded_status != EpubReaderFrameImageStatus_UnsupportedFormat &&
      decoded_status != EpubReaderFrameImageStatus_DimensionCap &&
      decoded_status != EpubReaderFrameImageStatus_DecodeFailed &&
      decoded_status != EpubReaderFrameImageStatus_CacheFull)
  {
    return JNI_FALSE;
  }

  U64 pixel_count = 0;
  U64 pixel_bytes = 0;
  U32 *pixels = 0;
  if (decoded_status == EpubReaderFrameImageStatus_Loaded)
  {
    if (!argb_pixels || width <= 0 || height <= 0 ||
        width > OCTAVO_ANDROID_IMAGE_MAX_DIMENSION ||
        height > OCTAVO_ANDROID_IMAGE_MAX_DIMENSION)
    {
      return JNI_FALSE;
    }
    pixel_count = (U64)(U32)width * (U64)(U32)height;
    pixel_bytes = pixel_count * sizeof(U32);
    if (pixel_count == 0 ||
        pixel_count > OCTAVO_ANDROID_IMAGE_MAX_PIXEL_COUNT ||
        (*environment)->GetArrayLength(environment, argb_pixels) !=
          (jsize)pixel_count)
    {
      return JNI_FALSE;
    }
    if (!octavo_android_image_cache_make_pixel_room(app, pixel_bytes))
    {
      /* Current-frame pixels stay pinned; this resource alone gets a
         visible bounded failure instead of disabling all later images. */
      decoded_status = EpubReaderFrameImageStatus_CacheFull;
    }
    else
    {
      pixels = (U32 *)malloc((size_t)pixel_bytes);
      if (!pixels)
      {
        decoded_status = EpubReaderFrameImageStatus_CacheFull;
      }
      else
      {
        (*environment)->GetIntArrayRegion(
          environment,
          argb_pixels,
          0,
          (jsize)pixel_count,
          (jint *)pixels);
        if ((*environment)->ExceptionCheck(environment))
        {
          free(pixels);
          return JNI_FALSE;
        }
      }
    }
  }

  if (!octavo_android_image_cache_make_entry_room(app))
  {
    free(pixels);
    image->status = EpubReaderFrameImageStatus_CacheFull;
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "No unpinned Android reader-image cache entry was available");
    return JNI_FALSE;
  }

  OctavoAndroidImageCacheEntry *entry =
    app->image_cache + app->image_cache_count;
  *entry = (OctavoAndroidImageCacheEntry){
    .resource_index = image->resource_index,
    .status = decoded_status,
    .pixels = pixels,
    .width = pixels ? width : 0,
    .height = pixels ? height : 0,
    .stride_pixels = pixels ? width : 0,
    .pixel_bytes = pixels ? pixel_bytes : 0,
    .last_use_serial = 0,
  };
  app->image_cache_count += 1u;
  app->image_cache_pixel_bytes += entry->pixel_bytes;
  octavo_android_image_cache_touch(app, entry);
  octavo_android_attach_frame_images(app);
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_clearFrameImageCacheForTesting(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app) { return JNI_FALSE; }
  octavo_android_image_cache_release(app);
  octavo_android_attach_frame_images(app);
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_frameImageResourceCachedForTesting(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jlong resource_index)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || resource_index < 0 || (U64)resource_index > UINT32_MAX)
  {
    return JNI_FALSE;
  }
  return octavo_android_image_cache_find(
    app, (U32)resource_index) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_frameImageCurrentFramePinningForTesting(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jlong resource_index)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || resource_index < 0 || (U64)resource_index > UINT32_MAX)
  {
    return JNI_FALSE;
  }
  U32 pinned_resource = (U32)resource_index;
  OctavoAndroidImageCacheEntry *pinned =
    octavo_android_image_cache_find(app, pinned_resource);
  if (!pinned ||
      !octavo_android_frame_resource_is_pinned(app, pinned_resource))
  {
    return JNI_FALSE;
  }

  app->image_cache_use_serial = 0;
  for (U32 index = 0; index < app->image_cache_count; ++index)
  {
    OctavoAndroidImageCacheEntry *entry = app->image_cache + index;
    if (entry->resource_index == pinned_resource)
    {
      entry->last_use_serial = 0;
    }
    else
    {
      app->image_cache_use_serial += 1u;
      entry->last_use_serial = app->image_cache_use_serial;
    }
  }

  U32 candidate = 0xf0000000u;
  while (app->image_cache_count < OCTAVO_ANDROID_IMAGE_CACHE_CAP)
  {
    while (candidate > 0 &&
           (octavo_android_image_cache_find(app, candidate) ||
            octavo_android_frame_resource_is_pinned(app, candidate)))
    {
      candidate -= 1u;
    }
    if (candidate == 0) { return JNI_FALSE; }
    OctavoAndroidImageCacheEntry *entry =
      app->image_cache + app->image_cache_count;
    *entry = (OctavoAndroidImageCacheEntry){
      .resource_index = candidate,
      .status = EpubReaderFrameImageStatus_DecodeFailed,
    };
    app->image_cache_count += 1u;
    octavo_android_image_cache_touch(app, entry);
    candidate -= 1u;
  }

  U32 victim_resource = 0;
  U64 victim_serial = UINT64_MAX;
  for (U32 index = 0; index < app->image_cache_count; ++index)
  {
    const OctavoAndroidImageCacheEntry *entry = app->image_cache + index;
    if (!octavo_android_frame_resource_is_pinned(app,
                                                  entry->resource_index) &&
        entry->last_use_serial < victim_serial)
    {
      victim_resource = entry->resource_index;
      victim_serial = entry->last_use_serial;
    }
  }
  U32 count_before = app->image_cache_count;
  if (victim_serial == UINT64_MAX ||
      !octavo_android_image_cache_make_entry_room(app))
  {
    return JNI_FALSE;
  }
  return app->image_cache_count + 1u == count_before &&
    octavo_android_image_cache_find(app, pinned_resource) &&
    !octavo_android_image_cache_find(app, victim_resource) ?
      JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_present(JNIEnv *environment,
                                          jclass type,
                                          jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return JNI_FALSE;
  }
  return octavo_android_present_frame(app) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_navigationAvailability(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)environment;
  (void)type;
  return octavo_android_navigation_availability(
    octavo_android_from_handle(handle));
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_setChromeVisible(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jboolean visible)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return JNI_FALSE;
  }
  int next = visible ? 1 : 0;
  if (app->chrome_visible == next)
  {
    return JNI_TRUE;
  }
  octavo_android_invalidate_prepared_static_frame(app);
  app->chrome_visible = next;
  app->chrome_toggle_count += 1u;
  return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_movePage(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint direction)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || (direction != -1 && direction != 1))
  {
    return 0;
  }
  jint result = OCTAVO_ANDROID_TOUCH_HANDLED;
  if (octavo_android_move_page(app, direction))
  {
    result |= OCTAVO_ANDROID_TOUCH_PRESENT_REQUESTED;
  }
  return result;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_accessibilityMovePage(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint direction)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || (direction != -1 && direction != 1) ||
      !octavo_android_move_page(app, direction))
  {
    return 0;
  }
  app->accessibility_action_count += 1u;
  return OCTAVO_ANDROID_TOUCH_HANDLED |
    OCTAVO_ANDROID_TOUCH_PRESENT_REQUESTED;
}

/*
 * Extract at most one remaining spine per host-scheduled idle step. Reader0's
 * public frame builder may establish one bounded summary step for the visible
 * frame; the Java host does not call this whole-book continuation until that
 * frame has been successfully posted.
 *
 * Return values: -1 terminal failure, 0 already complete, 1 progressed with
 * more work remaining, 2 temporarily presentation-gated, 3 just completed
 * and the host should request one same-page metadata refresh.
 */
JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_warmLocationCacheStep(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->reader_initialized ||
      !epub_reader_is_open(&app->reader))
  {
    return -1;
  }
  if (app->reader.location_cache_complete &&
      app->reader.location_cache_valid)
  {
    return 0;
  }
  if (app->frame_count == 0 || !app->resumed ||
      octavo_android_presentation_pending(app))
  {
    app->location_warm_defer_count += 1u;
    return 2;
  }

  if (app->location_warm_step_count == 0)
  {
    app->location_warm_first_frame_count = app->frame_count;
  }
  app->location_warm_step_count += 1u;
  if (app->forced_location_warm_failures_for_testing > 0)
  {
    app->forced_location_warm_failures_for_testing -= 1;
    app->location_warm_failure_count += 1u;
    return -1;
  }
  DocDocumentId before_document = app->reader.location_document_id;
  U32 before = app->reader.location_next_spine_index;
  U32 before_spine_count = app->reader.location_spine_count;
  U64 before_total = app->reader.location_total_text_bytes;
  B32 before_complete = app->reader.location_cache_complete;
  B32 before_valid = app->reader.location_cache_valid;
  (void)epub_reader_location_cache_ensure(&app->reader);
  B32 complete = app->reader.location_cache_complete &&
    app->reader.location_cache_valid;
  B32 progressed = app->reader.location_next_spine_index > before;
  if (before_document != app->reader.location_document_id ||
      before != app->reader.location_next_spine_index ||
      before_spine_count != app->reader.location_spine_count ||
      before_total != app->reader.location_total_text_bytes ||
      before_complete != app->reader.location_cache_complete ||
      before_valid != app->reader.location_cache_valid)
  {
    octavo_android_invalidate_prepared_static_frame(app);
  }
  if (progressed)
  {
    app->location_warm_progress_count += 1u;
  }
  if (complete)
  {
    return 3;
  }
  if (progressed)
  {
    return 1;
  }
  app->location_warm_failure_count += 1u;
  return -1;
}

JNIEXPORT jlongArray JNICALL
Java_ro_devze_octavo_OctavoNative_locationCacheState(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return 0;
  }
  jlong values[10] = {
    app->reader.location_cache_complete ? 1 : 0,
    app->reader.location_cache_valid ? 1 : 0,
    (jlong)app->reader.location_next_spine_index,
    (jlong)app->reader.location_spine_count,
    (jlong)app->reader.location_total_text_bytes,
    (jlong)app->location_warm_step_count,
    (jlong)app->location_warm_progress_count,
    (jlong)app->location_warm_defer_count,
    (jlong)app->location_warm_failure_count,
    (jlong)app->location_warm_first_frame_count,
  };
  jlongArray result = (*environment)->NewLongArray(environment, 10);
  if (!result)
  {
    return 0;
  }
  (*environment)->SetLongArrayRegion(environment, result, 0, 10, values);
  return result;
}

JNIEXPORT jlongArray JNICALL
Java_ro_devze_octavo_OctavoNative_preparedStaticFrameStateForTesting(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return 0;
  }
  S32 window_width = app->window ? ANativeWindow_getWidth(app->window) : 0;
  S32 window_height = app->window ?
    ANativeWindow_getHeight(app->window) : 0;
  const OctavoAndroidPreparedStaticFrame *prepared =
    &app->prepared_static_frame;
  jlong values[OCTAVO_ANDROID_PREPARED_FRAME_STATE_FIELD_COUNT] = {
    OCTAVO_ANDROID_PREPARED_FRAME_STATE_VERSION,
    prepared->valid ? 1 : 0,
    (jlong)app->prepared_static_frame_mutation_generation,
    (jlong)app->prepared_static_frame_invalidation_count,
    (jlong)app->prepared_static_frame_build_attempt_count,
    (jlong)app->prepared_static_frame_build_success_count,
    (jlong)app->prepared_static_frame_snapshot_reuse_count,
    (jlong)app->prepared_static_frame_present_reuse_count,
    (jlong)app->prepared_static_frame_stale_reject_count,
    (jlong)app->prepared_static_frame_consume_count,
    (jlong)prepared->build_serial,
    (jlong)app->prepared_static_frame_build_serial,
    (jlong)prepared->lifecycle_generation,
    (jlong)app->lifecycle_generation,
    (jlong)prepared->surface_generation,
    (jlong)app->surface_generation,
    prepared->width,
    prepared->height,
    window_width,
    window_height,
    prepared->active_spine_index,
    app->reader.active_spine_index,
    (jlong)prepared->view_byte_offset,
    (jlong)app->reader.view_byte_offset,
    prepared->location_next_spine_index,
    app->reader.location_next_spine_index,
  };
  jlongArray result = (*environment)->NewLongArray(
    environment, OCTAVO_ANDROID_PREPARED_FRAME_STATE_FIELD_COUNT);
  if (result)
  {
    (*environment)->SetLongArrayRegion(
      environment,
      result,
      0,
      OCTAVO_ANDROID_PREPARED_FRAME_STATE_FIELD_COUNT,
      values);
  }
  return result;
}

JNIEXPORT jlongArray JNICALL
Java_ro_devze_octavo_OctavoNative_state(JNIEnv *environment,
                                        jclass type,
                                        jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return 0;
  }

  jlong values[OCTAVO_ANDROID_STATE_FIELD_COUNT];
  values[0] = app->resumed ? 1 : 0;
  values[1] = app->window ? 1 : 0;
  values[2] = app->width;
  values[3] = app->height;
  values[4] = (jlong)app->surface_generation;
  values[5] = (jlong)app->surface_destroy_count;
  values[6] = (jlong)app->resume_count;
  values[7] = (jlong)app->pause_count;
  values[8] = (jlong)app->frame_count;
  values[9] = (jlong)app->render_failure_count;
  values[10] = (jlong)app->touch_count;
  values[11] = (jlong)app->lifecycle_generation;
  values[12] = app->reader_initialized ? 1 : 0;
  values[13] = app->reader_frame.document_open ? 1 : 0;
  values[14] = app->reader_frame.ready ? 1 : 0;
  values[15] = (jlong)app->reader_frame.visible_text.size;
  values[16] = (jlong)u64_hash_str8(app->reader_frame.visible_text);
  values[17] = (jlong)app->reader_frame.page_index;
  values[18] = (jlong)app->reader_frame.page_count;
  values[19] = app->reader_view_ready ? 1 : 0;
  values[20] = (jlong)app->reader_view_frame.error_flags;
  values[21] = (jlong)app->reader_view_frame.draw_command_count;
  values[22] = app->reader_view_layout.page_surface_rect.x;
  values[23] = app->reader_view_layout.page_surface_rect.y;
  values[24] = app->reader_view_layout.page_surface_rect.w;
  values[25] = app->reader_view_layout.page_surface_rect.h;
  values[26] = (jlong)app->reader_view_projection.progress.page_index;
  values[27] = (jlong)app->reader_view_projection.progress.page_count;
  values[28] = (jlong)app->tap_intent_count;
  values[29] = (jlong)app->page_move_success_count;
  values[30] = (jlong)app->page_move_presented_count;
  values[31] = (jlong)app->page_move_boundary_count;
  values[32] = (jlong)app->page_move_gate_block_count;
  values[33] = app->page_move_waiting_for_present ? 1 : 0;
  values[34] = (jlong)app->navigation_failure_count;
  values[35] = (jlong)app->reader_frame.spine_index;
  values[36] = (jlong)app->reader_frame.section_count;
  values[37] = (jlong)app->reader_view_projection.progress.location_index;
  values[38] = (jlong)app->reader_view_projection.progress.location_count;
  values[39] = app->typography.ready ? 1 : 0;
  values[40] = app->typography.text_px;
  values[41] = app->typography.line_advance_px;
  values[42] = octavo_android_typography_advance(
    &app->typography, 'i', 0, 1000u);
  values[43] = octavo_android_typography_advance(
    &app->typography, 'W', 0, 1000u);
  values[44] = app->typography.style_count;
  values[45] = (jlong)app->typography.rasterized_glyph_count;
  values[46] = (jlong)app->typography.rasterized_style_count[0];
  values[47] = (jlong)app->typography.rasterized_style_count[1];
  values[48] = (jlong)app->typography.rasterized_style_count[2];
  values[49] = (jlong)app->typography.rasterized_style_count[3];
  values[50] = app->restore_requested ? 1 : 0;
  values[51] = app->restore_attempted ? 1 : 0;
  values[52] = app->restore_succeeded ? 1 : 0;
  values[53] = (jlong)app->restore_failure_count;
  values[54] = app->presented_anchor_valid ?
    (jlong)app->presented_anchor_spine_index : 0;
  values[55] = app->presented_anchor_valid ?
    (jlong)app->presented_anchor_byte_offset : 0;
  values[56] = (jlong)app->document_open_success_count;
  values[57] = (jlong)app->document_open_failure_count;
  values[58] = (jlong)app->reader.document_generation;
  values[59] = (jlong)app->appearance_generation;
  values[60] = (jlong)app->appearance_presented_generation;
  values[61] = (jlong)app->appearance_apply_count;
  values[62] = (jlong)app->appearance_gate_block_count;
  values[63] = (jlong)app->appearance_failure_count;
  values[64] = (jlong)app->reflow_request_count;
  values[65] = (jlong)app->reflow_success_count;
  values[66] = (jlong)app->reflow_failure_count;
  values[67] = (jlong)app->accessibility_action_count;
  values[68] = app->appearance.theme;
  values[69] = app->appearance.font_family;
  values[70] = app->appearance.font_size_sp;
  values[71] = app->appearance.line_spacing_permille;
  values[72] = app->appearance.margin;
  values[73] = app->appearance.alignment;
  values[74] = app->appearance.publisher_colors;
  values[75] = (jlong)app->appearance.color_hash;
  values[76] = app->reader.has_current_page ?
    (jlong)app->reader.current_page.first_byte : 0;
  values[77] = app->reader.has_current_page ?
    (jlong)app->reader.current_page.one_past_last_byte : 0;
  values[78] = app->chrome_visible ? 1 : 0;
  values[79] = (jlong)app->chrome_toggle_count;
  values[80] = app->reflow_waiting_for_present ? 1 : 0;
  values[81] = app->reader_view_layout.content_rect.x;
  values[82] = app->reader_view_layout.content_rect.y;
  values[83] = app->reader_view_layout.content_rect.w;
  values[84] = app->reader_view_layout.content_rect.h;
  values[85] = app->reader_chrome_inset_top;
  values[86] = app->reader_chrome_inset_bottom;
  values[87] = app->appearance.reduced_motion ? 1 : 0;
  values[88] = app->host_frame_waiting_for_present ? 1 : 0;
  values[89] = (jlong)app->justification_evidence.plan_count;
  values[90] = (jlong)app->justification_evidence.active_row_count;
  values[91] = (jlong)app->justification_evidence.applied_extra_px;
  values[92] = (jlong)app->justification_evidence.semantic_hash;
  values[93] = (jlong)app->reader_entry_started_millis;
  values[94] = (jlong)app->first_frame_elapsed_millis;
  values[95] = (jlong)app->typography.missing_glyph_count;
  jlongArray result =
    (*environment)->NewLongArray(environment, OCTAVO_ANDROID_STATE_FIELD_COUNT);
  if (!result)
  {
    return 0;
  }
  (*environment)->SetLongArrayRegion(environment,
                                     result,
                                     0,
                                     OCTAVO_ANDROID_STATE_FIELD_COUNT,
                                     values);
  return result;
}

JNIEXPORT jlongArray JNICALL
Java_ro_devze_octavo_OctavoNative_accessibilitySemanticSnapshot(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  S32 count = app && app->reader_view_ready &&
    !octavo_android_presentation_pending(app) ?
    app->reader_view_frame.semantic_node_count : 0;
  if (count < 0 || count > READER_VIEW_SEMANTIC_NODE_CAP)
  {
    count = 0;
  }
  jsize total = OCTAVO_ANDROID_SEMANTIC_SNAPSHOT_HEADER_COUNT +
    count * OCTAVO_ANDROID_SEMANTIC_SNAPSHOT_STRIDE;
  jlong values[
    OCTAVO_ANDROID_SEMANTIC_SNAPSHOT_HEADER_COUNT +
    READER_VIEW_SEMANTIC_NODE_CAP *
      OCTAVO_ANDROID_SEMANTIC_SNAPSHOT_STRIDE] = {0};
  values[0] = OCTAVO_ANDROID_SEMANTIC_SNAPSHOT_VERSION;
  values[1] = count;
  values[2] = OCTAVO_ANDROID_SEMANTIC_SNAPSHOT_STRIDE;
  for (S32 index = 0; index < count; ++index)
  {
    const ReaderViewSemanticNode *node =
      &app->reader_view_frame.semantic_nodes[index];
    S32 at = OCTAVO_ANDROID_SEMANTIC_SNAPSHOT_HEADER_COUNT +
      index * OCTAVO_ANDROID_SEMANTIC_SNAPSHOT_STRIDE;
    values[at + 0] = (jlong)node->id;
    values[at + 1] = node->control;
    values[at + 2] = node->role;
    values[at + 3] = node->flags;
    values[at + 4] = node->rect.x;
    values[at + 5] = node->rect.y;
    values[at + 6] = node->rect.w;
    values[at + 7] = node->rect.h;
    values[at + 8] = (jlong)node->range_value;
    values[at + 9] = (jlong)node->range_min;
    values[at + 10] = (jlong)node->range_max;
  }
  jlongArray result = (*environment)->NewLongArray(environment, total);
  if (result)
  {
    (*environment)->SetLongArrayRegion(
      environment, result, 0, total, values);
  }
  return result;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_accessibilitySemanticName(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint record_index)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->reader_view_ready ||
      octavo_android_presentation_pending(app) || record_index < 0 ||
      record_index >= app->reader_view_frame.semantic_node_count)
  {
    return (*environment)->NewStringUTF(environment, "");
  }
  return octavo_android_new_reader_view_text(
    environment,
    app->reader_view_frame.semantic_nodes[record_index].name);
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_accessibilitySemanticValue(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint record_index)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->reader_view_ready ||
      octavo_android_presentation_pending(app) || record_index < 0 ||
      record_index >= app->reader_view_frame.semantic_node_count)
  {
    return (*environment)->NewStringUTF(environment, "");
  }
  return octavo_android_new_reader_view_text(
    environment,
    app->reader_view_frame.semantic_nodes[record_index].value);
}

JNIEXPORT jlongArray JNICALL
Java_ro_devze_octavo_OctavoNative_readingPosition(JNIEnv *environment,
                                                   jclass type,
                                                   jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return 0;
  }
  B32 valid = app->frame_count > 0 &&
    app->reader_frame.ready &&
    app->reader_frame.document_open &&
    app->reader.has_current_page &&
    app->presented_anchor_valid &&
    !octavo_android_presentation_pending(app);
  jlong values[3] = {
    valid ? 1 : 0,
    valid ? (jlong)app->presented_anchor_spine_index : 0,
    valid ? (jlong)app->presented_anchor_byte_offset : 0,
  };
  jlongArray result = (*environment)->NewLongArray(environment, 3);
  if (!result)
  {
    return 0;
  }
  (*environment)->SetLongArrayRegion(environment, result, 0, 3, values);
  return result;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_filesPath(JNIEnv *environment,
                                            jclass type,
                                            jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  return app ? (*environment)->NewStringUTF(environment, app->files_path) : 0;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_cachePath(JNIEnv *environment,
                                            jclass type,
                                            jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  return app ? (*environment)->NewStringUTF(environment, app->cache_path) : 0;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_documentPath(JNIEnv *environment,
                                               jclass type,
                                               jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  return app ?
    (*environment)->NewStringUTF(environment, app->document_path) : 0;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_documentTitle(JNIEnv *environment,
                                                jclass type,
                                                jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  return app ?
    (*environment)->NewStringUTF(environment, app->document_title) : 0;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_visibleText(JNIEnv *environment,
                                              jclass type,
                                              jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->reader_frame.ready ||
      app->reader_frame.visible_text.size >=
        EPUB_READER_FRAME_VISIBLE_TEXT_CAP)
  {
    return 0;
  }
  char text[EPUB_READER_FRAME_VISIBLE_TEXT_CAP];
  size_t size = (size_t)app->reader_frame.visible_text.size;
  memcpy(text, app->reader_frame.visible_text.str, size);
  text[size] = 0;
  return (*environment)->NewStringUTF(environment, text);
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_progressLabel(JNIEnv *environment,
                                                jclass type,
                                                jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  return app ?
    octavo_android_new_utf8_string(
      environment,
      str8_from_cstr(app->progress_label)) : 0;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_clearColorArgb(JNIEnv *environment,
                                                 jclass type)
{
  (void)environment;
  (void)type;
  return (jint)OCTAVO_ANDROID_DEFAULT_BACKGROUND;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_touch(JNIEnv *environment,
                                        jclass type,
                                        jlong handle,
                                        jint action,
                                        jfloat x,
                                        jfloat y,
                                        jlong event_time_millis)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return 0;
  }
  app->touch_count += 1u;

  jint touch_result = OCTAVO_ANDROID_TOUCH_HANDLED;
  if (action == AMOTION_EVENT_ACTION_DOWN)
  {
    app->touch_active = 0;
    app->touch_direction = 0;
    if (octavo_android_presentation_pending(app))
    {
      if (app->page_move_waiting_for_present)
      {
        app->page_move_gate_block_count += 1u;
      }
      else
      {
        app->appearance_gate_block_count += 1u;
      }
      return touch_result;
    }
    if (!app->resumed || !app->window ||
        !app->reader_frame.ready || !app->reader_view_ready)
    {
      return touch_result;
    }

    S32 direction = octavo_android_touch_zone(app, x, y);
    if (direction != OCTAVO_ANDROID_TOUCH_ZONE_INVALID &&
        event_time_millis >= 0)
    {
      app->touch_active = 1;
      app->touch_direction = direction;
      app->touch_down_x = x;
      app->touch_down_y = y;
      app->touch_down_time_millis = (uint64_t)event_time_millis;
    }
    return touch_result;
  }

  if (action == AMOTION_EVENT_ACTION_CANCEL)
  {
    app->touch_active = 0;
    app->touch_direction = 0;
    return touch_result;
  }

  if (action == AMOTION_EVENT_ACTION_UP)
  {
    int was_active = app->touch_active;
    S32 direction = app->touch_direction;
    uint64_t down_time_millis = app->touch_down_time_millis;
    float delta_x = x - app->touch_down_x;
    float delta_y = y - app->touch_down_y;
    app->touch_active = 0;
    app->touch_direction = 0;

    if (was_active && event_time_millis >= 0 &&
        (uint64_t)event_time_millis >= down_time_millis &&
        (uint64_t)event_time_millis - down_time_millis <=
          OCTAVO_ANDROID_TAP_MAX_DURATION_MILLIS &&
        delta_x * delta_x + delta_y * delta_y <=
          (float)(OCTAVO_ANDROID_TAP_SLOP_PIXELS *
                  OCTAVO_ANDROID_TAP_SLOP_PIXELS) &&
        octavo_android_touch_zone(app, x, y) == direction)
    {
      app->tap_intent_count += 1u;
      if (direction == 0)
      {
        touch_result |= OCTAVO_ANDROID_TOUCH_CHROME_REQUESTED;
      }
      else if (octavo_android_move_page(app, direction))
      {
        touch_result |= OCTAVO_ANDROID_TOUCH_PRESENT_REQUESTED;
      }
    }
  }
  return touch_result;
}

#include "octavo_android_port10_selection.inc"
#include "octavo_android_port8_navigation.inc"
#include "octavo_android_port8_navigation_state.inc"
#include "octavo_android_port9_search.inc"

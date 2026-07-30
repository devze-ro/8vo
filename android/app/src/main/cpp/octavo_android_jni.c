#include "octavo_version.h"
#include "foundation/version.h"
#include "reader0.h"
#include "readerview0.h"
#include "ui0.h"

#include <android/input.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define OCTAVO_ANDROID_PATH_CAPACITY 4096u
#define OCTAVO_ANDROID_TITLE_CAPACITY 256u
#define OCTAVO_ANDROID_PROGRESS_CAPACITY 128u
#define OCTAVO_ANDROID_STATE_FIELD_COUNT 39

enum
{
  OCTAVO_ANDROID_TEXT_SCALE = 3,
  OCTAVO_ANDROID_APP_BACKGROUND = 0xFFF7F3EAu,
  OCTAVO_ANDROID_TAP_MAX_DURATION_MILLIS = 500,
  OCTAVO_ANDROID_TAP_SLOP_PIXELS = 24,
  OCTAVO_ANDROID_TOUCH_HANDLED = 1u << 0,
  OCTAVO_ANDROID_TOUCH_PRESENT_REQUESTED = 1u << 1,
};

typedef struct OctavoAndroidPixels
{
  uint8_t *data;
  int32_t width;
  int32_t height;
  int32_t stride;
} OctavoAndroidPixels;

typedef struct OctavoAndroidApp
{
  ANativeWindow *window;
  char files_path[OCTAVO_ANDROID_PATH_CAPACITY];
  char cache_path[OCTAVO_ANDROID_PATH_CAPACITY];
  char fixture_path[OCTAVO_ANDROID_PATH_CAPACITY];
  char document_title[OCTAVO_ANDROID_TITLE_CAPACITY];
  char progress_label[OCTAVO_ANDROID_PROGRESS_CAPACITY];

  Arena *arena;
  EpubReader reader;
  EpubReaderFrameStorage reader_frame_storage;
  EpubReaderFrame reader_frame;
  EpubReaderLayoutKey layout_key;
  SourceReaderLayoutConfig layout_config;
  B32 reader_initialized;

  ReaderViewState reader_view_state;
  ReaderViewLayout reader_view_layout;
  ReaderViewProjection reader_view_projection;
  ReaderViewFrameStorage reader_view_storage;
  ReaderViewFrame reader_view_frame;
  UI0ResolvedTheme reader_view_theme;
  UI0U64 reader_view_frame_index;
  B32 reader_view_ready;
  S32 pagination_content_width;
  S32 pagination_content_height;

  int32_t format;
  int32_t width;
  int32_t height;
  int32_t inset_left;
  int32_t inset_top;
  int32_t inset_right;
  int32_t inset_bottom;
  int resumed;
  uint64_t surface_generation;
  uint64_t surface_destroy_count;
  uint64_t resume_count;
  uint64_t pause_count;
  uint64_t frame_count;
  uint64_t render_failure_count;
  uint64_t touch_count;
  uint64_t lifecycle_generation;
  uint64_t tap_intent_count;
  uint64_t page_move_success_count;
  uint64_t page_move_presented_count;
  uint64_t page_move_boundary_count;
  uint64_t page_move_gate_block_count;
  uint64_t navigation_failure_count;
  uint64_t page_move_expected_byte_offset;
  uint32_t page_move_expected_spine_index;
  uint64_t touch_down_time_millis;
  float touch_down_x;
  float touch_down_y;
  int32_t touch_direction;
  int page_move_waiting_for_present;
  int touch_active;
} OctavoAndroidApp;

static OctavoAndroidApp *
octavo_android_from_handle(jlong handle)
{
  return (OctavoAndroidApp *)(uintptr_t)handle;
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
  for (int32_t y = visible.y; y < visible.y + visible.h; ++y)
  {
    for (int32_t x = visible.x; x < visible.x + visible.w; ++x)
    {
      octavo_android_write_pixel(pixels, x, y, color);
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
    command->stroke_color : OCTAVO_ANDROID_APP_BACKGROUND;
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

static void
octavo_android_draw_reader_text(OctavoAndroidApp *app,
                                OctavoAndroidPixels pixels)
{
  if (!app || !app->reader_frame.ready ||
      !app->reader_frame.visible_text.str ||
      app->reader_frame.visible_text.size == 0)
  {
    return;
  }
  UI0Rect content = app->reader_view_layout.content_rect;
  UI0Rect clip = octavo_android_intersect_rect(
    content, octavo_android_pixel_bounds(pixels));
  FontTextMetrics metrics = font_metrics_for_size(
    font_provider_system_ui(), OCTAVO_ANDROID_TEXT_SCALE);
  S32 advance = metrics.glyph_advance_px;
  S32 line_advance = metrics.line_advance_px;
  S32 x = content.x;
  S32 y = content.y;
  S32 right = content.x + content.w;
  S32 bottom = content.y + content.h;
  UI0Color ink =
    app->reader_view_theme.colors[UI0ColorRole_TextPrimary];
  const char *text = (const char *)app->reader_frame.visible_text.str;
  U64 text_size = app->reader_frame.visible_text.size;

  for (U64 index = 0; index < text_size && y + line_advance <= bottom; ++index)
  {
    char codepoint = text[index];
    if (codepoint == '\r')
    {
      continue;
    }
    if (codepoint == '\n')
    {
      x = content.x;
      y += line_advance;
      continue;
    }
    if (codepoint == ' ')
    {
      U64 word_size = 0;
      while (index + 1u + word_size < text_size)
      {
        char next = text[index + 1u + word_size];
        if (next == ' ' || next == '\r' || next == '\n')
        {
          break;
        }
        word_size += 1u;
      }
      if (word_size > 0 &&
          x > content.x &&
          (U64)(right - x) < (word_size + 1u) * (U64)advance)
      {
        x = content.x;
        y += line_advance;
        continue;
      }
    }
    if (x + advance > right)
    {
      x = content.x;
      y += line_advance;
      if (y + line_advance > bottom)
      {
        break;
      }
      if (codepoint == ' ')
      {
        continue;
      }
    }
    octavo_android_draw_glyph(
      pixels,
      codepoint,
      x,
      y,
      OCTAVO_ANDROID_TEXT_SCALE,
      ink,
      clip);
    x += advance;
  }
}

static S32
octavo_android_measure_reader_text(void *user,
                                   String8 text,
                                   DocTextStyleFlags flags,
                                   U32 font_scale_permille,
                                   U32 font_family_hint,
                                   U32 font_face_index)
{
  (void)user;
  (void)font_family_hint;
  (void)font_face_index;
  S32 width = font_measure_text_width_s8(
    font_provider_system_ui(), text, OCTAVO_ANDROID_TEXT_SCALE);
  if ((flags & DocTextStyleFlag_Bold) != 0)
  {
    width += (S32)text.size;
  }
  if (font_scale_permille != 0u && font_scale_permille != 1000u)
  {
    width = (S32)(((S64)width * (S64)font_scale_permille) / 1000);
  }
  return MAX(width, 0);
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

  EpubReaderOpenTransition transition = {0};
  if (epub_reader_open(&app->reader,
                       str8_from_cstr(app->fixture_path),
                       DocSourceKind_EPUB,
                       &transition) != EpubReaderResult_Ok ||
      !transition.changed ||
      !epub_reader_refresh_active_spine(&app->reader) ||
      !octavo_android_copy_document_title(app))
  {
    return 0;
  }

  reader_view_state_init(&app->reader_view_state);
  reader_view_frame_storage_init(&app->reader_view_storage);
  UI0ThemeProfile profile =
    ui0_theme_profile_for_kind(UI0ThemeProfile_Light);
  app->reader_view_theme = profile.resolved;
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
    .document_flags = ReaderViewDocument_Open,
  };
  return reader_view_resolve_layout(
    &app->reader_view_state, &input, &app->reader_view_layout);
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

  if (!app->reader_frame.ready ||
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
    FontTextMetrics metrics = font_metrics_for_size(
      font_provider_system_ui(), OCTAVO_ANDROID_TEXT_SCALE);
    S32 char_advance = MAX(metrics.glyph_advance_px, 1);
    S32 line_height = MAX(metrics.line_advance_px, 1);
    U32 wrap_columns = (U32)MAX(content.w / char_advance, 8);
    U32 page_rows = (U32)MAX(content.h / line_height, 4);
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
      .text_scale = 18,
      .margin_unit_permille = 1000,
    };
    app->layout_config = (SourceReaderLayoutConfig){
      .wrap_column_count = wrap_columns,
      .page_row_count = page_rows,
      .margin_unit_permille = 1000,
      .text_width_px = content.w,
      .char_advance_px = char_advance,
      .text_scale = 18,
      .focused_spine_index = app->reader.active_spine_index,
      .measure_text = octavo_android_measure_reader_text,
    };
    if (!epub_reader_rebuild_pagination(
          &app->reader, app->layout_key, app->layout_config, 0))
    {
      return 0;
    }
    for (U32 spine_index = 0;
         spine_index < spine_count &&
           !epub_reader_location_cache_ensure(&app->reader);
         spine_index += 1)
    {
    }
    app->pagination_content_width = content.w;
    app->pagination_content_height = content.h;
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
  projection.document_flags = ReaderViewDocument_Open;
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

  U32 section_index = app->reader_frame.spine_index + 1u;
  U32 section_count = app->reader_frame.section_count;
  U64 progress_percent = app->reader_frame.location.available ?
    app->reader_frame.location.percent : 0u;
  if (app->reader_frame.page_count > 0 &&
      app->reader_frame.page_index > 0)
  {
    (void)snprintf(
      app->progress_label,
      sizeof(app->progress_label),
      "Section %u of %u | Page %llu of %llu | %llu%%",
      (unsigned)section_index,
      (unsigned)section_count,
      (unsigned long long)app->reader_frame.page_index,
      (unsigned long long)app->reader_frame.page_count,
      (unsigned long long)progress_percent);
  }
  else if (app->reader_frame.page_index > 0)
  {
    (void)snprintf(
      app->progress_label,
      sizeof(app->progress_label),
      "Section %u of %u | Page %llu | %llu%%",
      (unsigned)section_index,
      (unsigned)section_count,
      (unsigned long long)app->reader_frame.page_index,
      (unsigned long long)progress_percent);
  }
  else
  {
    (void)snprintf(
      app->progress_label,
      sizeof(app->progress_label),
      "Section %u of %u | %llu%%",
      (unsigned)section_index,
      (unsigned)section_count,
      (unsigned long long)progress_percent);
  }
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
      "Readerview0 rejected Port 3 projection errors=0x%x",
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
    return 0;
  }

  S32 left = MAX(app->inset_left, 0);
  S32 top = MAX(app->inset_top, 0);
  S32 right = app->width - MAX(app->inset_right, 0);
  S32 bottom = app->height - MAX(app->inset_bottom, 0);
  if (left >= right || top >= bottom ||
      x < (float)left || x >= (float)right ||
      y < (float)top || y >= (float)bottom)
  {
    return 0;
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
  if (app->page_move_waiting_for_present)
  {
    app->page_move_gate_block_count += 1u;
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
      "Android Port 3 page move failed result=%d diagnostic=%d",
      (int)result,
      (int)change.diagnostic);
    return 0;
  }

  app->page_move_success_count += 1u;
  app->page_move_waiting_for_present = 1;
  app->page_move_expected_spine_index = change.after.spine_index;
  app->page_move_expected_byte_offset = change.after.byte_offset;
  return 1;
}

static int
octavo_android_present_frame(OctavoAndroidApp *app)
{
  if (!app || !app->window || !app->resumed)
  {
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

  OctavoAndroidPixels pixels = {
    .data = (uint8_t *)buffer.bits,
    .width = buffer.width,
    .height = buffer.height,
    .stride = buffer.stride,
  };
  if (!octavo_android_build_static_page(
        app, (S32)buffer.width, (S32)buffer.height))
  {
    app->render_failure_count += 1u;
    octavo_android_fill_rect(
      pixels,
      octavo_android_pixel_bounds(pixels),
      octavo_android_pixel_bounds(pixels),
      0xFF451F1Fu);
    (void)ANativeWindow_unlockAndPost(app->window);
    __android_log_print(
      ANDROID_LOG_ERROR, "8vo", "Unable to build the Android Port 3 page");
    return 0;
  }

  UI0Rect bounds = octavo_android_pixel_bounds(pixels);
  UI0Color app_background =
    app->reader_view_theme.colors[UI0ColorRole_AppBackground];
  UI0Color page_color =
    app->reader_view_theme.colors[UI0ColorRole_Surface];
  UI0Color border_color =
    app->reader_view_theme.colors[UI0ColorRole_BorderMuted];
  octavo_android_fill_rect(pixels, bounds, bounds, app_background);
  octavo_android_fill_rect(
    pixels, app->reader_view_layout.page_surface_rect, bounds, page_color);
  octavo_android_stroke_rect(
    pixels,
    app->reader_view_layout.page_surface_rect,
    bounds,
    border_color,
    1);
  octavo_android_draw_reader_text(app, pixels);
  octavo_android_draw_reader_view(app, pixels);

  if (ANativeWindow_unlockAndPost(app->window) != 0)
  {
    app->render_failure_count += 1u;
    __android_log_print(
      ANDROID_LOG_ERROR, "8vo", "Unable to present the Android frame buffer");
    return 0;
  }

  app->format = buffer.format;
  app->width = buffer.width;
  app->height = buffer.height;
  app->frame_count += 1u;
  if (app->page_move_waiting_for_present)
  {
    if (!app->reader_frame.ready || !app->reader.has_current_page ||
        app->reader_frame.spine_index !=
          app->page_move_expected_spine_index ||
        app->reader_frame.view_byte_offset !=
          app->page_move_expected_byte_offset)
    {
      app->render_failure_count += 1u;
      __android_log_print(
        ANDROID_LOG_ERROR,
        "8vo",
        "Android Port 3 presented page mismatch "
        "expected=%u:%llu actual=%u:%llu",
        (unsigned)app->page_move_expected_spine_index,
        (unsigned long long)app->page_move_expected_byte_offset,
        (unsigned)app->reader_frame.spine_index,
        (unsigned long long)app->reader_frame.view_byte_offset);
      return 0;
    }
    app->page_move_waiting_for_present = 0;
    app->page_move_expected_spine_index = 0;
    app->page_move_expected_byte_offset = 0;
    app->page_move_presented_count += 1u;
  }
  __android_log_print(
    ANDROID_LOG_INFO,
    "8vo",
    "Android Port 3 frame=%llu surface=%llu size=%dx%d page=%llu/%llu "
    "reader_bytes=%llu reader_hash=%016llx view_draws=%d",
    (unsigned long long)app->frame_count,
    (unsigned long long)app->surface_generation,
    app->width,
    app->height,
    (unsigned long long)app->reader_frame.page_index,
    (unsigned long long)app->reader_frame.page_count,
    (unsigned long long)app->reader_frame.visible_text.size,
    (unsigned long long)u64_hash_str8(app->reader_frame.visible_text),
    app->reader_view_frame.draw_command_count);
  return 1;
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
                                         jstring fixture_path)
{
  (void)type;
  OctavoAndroidApp *app = (OctavoAndroidApp *)calloc(1, sizeof(*app));
  if (!app)
  {
    return 0;
  }

  if (!octavo_android_copy_path(environment,
                                files_path,
                                app->files_path,
                                sizeof(app->files_path)) ||
      !octavo_android_copy_path(environment,
                                cache_path,
                                app->cache_path,
                                sizeof(app->cache_path)) ||
      !octavo_android_copy_path(environment,
                                fixture_path,
                                app->fixture_path,
                                sizeof(app->fixture_path)) ||
      !octavo_android_initialize_reader(app))
  {
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Unable to create the Android Port 3 reader state");
    epub_reader_release(&app->reader);
    if (app->arena)
    {
      arena_release(app->arena);
    }
    free(app);
    return 0;
  }

  __android_log_print(
    ANDROID_LOG_INFO,
    "8vo",
    "Android Port 3 state created fixture=%s title=%s",
    app->fixture_path,
    app->document_title);
  return (jlong)(uintptr_t)app;
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
  app->resumed = 1;
  app->resume_count += 1u;
  app->lifecycle_generation += 1u;
  (void)octavo_android_present_frame(app);
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
  app->resumed = 0;
  app->pause_count += 1u;
  app->lifecycle_generation += 1u;
  app->touch_active = 0;
  app->touch_direction = 0;
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_surfaceCreated(JNIEnv *environment,
                                                 jclass type,
                                                 jlong handle,
                                                 jobject surface)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !surface)
  {
    return;
  }

  ANativeWindow *window = ANativeWindow_fromSurface(environment, surface);
  if (!window)
  {
    return;
  }
  if (app->window)
  {
    ANativeWindow_release(app->window);
  }
  app->window = window;
  app->width = ANativeWindow_getWidth(window);
  app->height = ANativeWindow_getHeight(window);
  app->format = ANativeWindow_getFormat(window);
  app->surface_generation += 1u;

  __android_log_print(
    ANDROID_LOG_INFO,
    "8vo",
    "Android surface generation=%llu size=%dx%d",
    (unsigned long long)app->surface_generation,
    app->width,
    app->height);
  (void)octavo_android_present_frame(app);
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
  app->format = (int32_t)format;
  app->width = (int32_t)width;
  app->height = (int32_t)height;
  (void)octavo_android_present_frame(app);
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
  if (app->window)
  {
    ANativeWindow_release(app->window);
    app->window = 0;
  }
  app->format = 0;
  app->width = 0;
  app->height = 0;
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
  app->inset_left = (int32_t)left;
  app->inset_top = (int32_t)top;
  app->inset_right = (int32_t)right;
  app->inset_bottom = (int32_t)bottom;
  (void)octavo_android_present_frame(app);
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
Java_ro_devze_octavo_OctavoNative_fixturePath(JNIEnv *environment,
                                              jclass type,
                                              jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  return app ? (*environment)->NewStringUTF(environment, app->fixture_path) : 0;
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
    (*environment)->NewStringUTF(environment, app->progress_label) : 0;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_clearColorArgb(JNIEnv *environment,
                                                 jclass type)
{
  (void)environment;
  (void)type;
  return (jint)OCTAVO_ANDROID_APP_BACKGROUND;
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
    if (app->page_move_waiting_for_present)
    {
      app->page_move_gate_block_count += 1u;
      return touch_result;
    }
    if (!app->resumed || !app->window ||
        !app->reader_frame.ready || !app->reader_view_ready)
    {
      return touch_result;
    }

    S32 direction = octavo_android_touch_zone(app, x, y);
    if (direction != 0 && event_time_millis >= 0)
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
      if (octavo_android_move_page(app, direction))
      {
        touch_result |= OCTAVO_ANDROID_TOUCH_PRESENT_REQUESTED;
      }
    }
  }
  return touch_result;
}

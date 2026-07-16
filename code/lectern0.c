#include "lectern0_version.h"
#include "reader0.h"
#include "ui0.h"

#include "base/base_arena.h"
#include "base/base_format.h"
#include "base/base_hash.h"
#include "base/base_strings.h"
#include "base/base_thread_context.h"
#include "base/base_unicode.h"
#include "draw/draw.h"
#include "os/os_file.h"
#include "os/os_gfx.h"
#include "os/os_image.h"
#include "os/os_time.h"
#include "presentation_engine/presentation_engine.h"
#include "render/render.h"

#if !defined(PRESENTATION_ENGINE_API_VERSION)
#  error "zero_foundation presentation_engine.h must define PRESENTATION_ENGINE_API_VERSION"
#endif
#if PRESENTATION_ENGINE_API_VERSION != 1
#  error "lectern0 requires Presentation Engine API 1"
#endif

#include <commdlg.h>
#include <objbase.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <windows.h>
#include <windowsx.h>

enum
{
  Lectern0PathCap = 1024,
  Lectern0StatusCap = 256,
  Lectern0StateFileCap = 2304,
  Lectern0UIBoxCap = 8,
  Lectern0UIRecordCap = 8,
  Lectern0UIDrawCap = 48,
  Lectern0ToolbarHeight = 48,
  Lectern0FooterHeight = 36,
  Lectern0ImageCacheCap = 64,
  Lectern0PresentationRowCap = EPUB_READER_FRAME_STYLE_ROW_CAP,
  Lectern0PresentationMediaCap = EPUB_READER_FRAME_IMAGE_CAP,
};

typedef struct Lectern0ImageCacheEntry
{
  DocDocumentId document_id;
  U32 resource_index;
  EpubReaderFrameImageStatus status;
  U32 *pixels;
  S32 width;
  S32 height;
  S32 stride_pixels;
} Lectern0ImageCacheEntry;

typedef struct Lectern0ImageCache
{
  OS_ImageDecoder decoder;
  Arena *pixel_arena;
  Lectern0ImageCacheEntry entries[Lectern0ImageCacheCap];
  U32 entry_count;
  U64 lookup_count;
  U64 hit_count;
  U64 miss_count;
  U64 cache_full_count;
  B32 decoder_ready;
} Lectern0ImageCache;

typedef struct Lectern0SavedState
{
  B32 valid;
  U32 spine_index;
  U64 byte_offset;
  char path[Lectern0PathCap];
} Lectern0SavedState;

typedef struct Lectern0Input
{
  S32 pointer_x;
  S32 pointer_y;
  B32 pointer_down;
  B32 pointer_pressed;
  B32 pointer_released;
  B32 activate_pressed;
  B32 focus_next_pressed;
  B32 focus_prev_pressed;
} Lectern0Input;

typedef enum Lectern0Action
{
  Lectern0Action_None,
  Lectern0Action_Open,
  Lectern0Action_Previous,
  Lectern0Action_Next,
} Lectern0Action;

typedef struct Lectern0App
{
  Arena *arena;
  EpubReader reader;
  EpubReaderFrameStorage frame_storage;
  EpubReaderFrame frame;
  EpubReaderLayoutKey layout_key;
  SourceReaderLayoutConfig layout_config;
  Lectern0ImageCache image_cache;
  PresentationEngineBlockFlowRowSpec presentation_row_specs[Lectern0PresentationRowCap];
  PresentationEngineBlockFlowMediaSpec presentation_media_specs[Lectern0PresentationMediaCap];
  PresentationEngineBlockFlowRow presentation_rows[Lectern0PresentationRowCap];
  PresentationEngineBlockFlowMedia presentation_media[Lectern0PresentationMediaCap];
  PresentationEngineBlockFlowFrame presentation_frame;
  U64 presentation_hash;

  RenderState render_state;
  DrawCommandBuffer draw_commands;
  OS_GfxContext gfx;
  B32 render_ready;
  B32 presentation_complete;
  B32 gfx_ready;
  HWND window;
  S32 width;
  S32 height;

  UI0LayoutContext ui_layout;
  UI0LayoutBox ui_boxes[Lectern0UIBoxCap];
  UI0SignalContext ui_signals;
  UI0SignalRecord ui_signal_records[Lectern0UIRecordCap];
  UI0ControlContext ui_controls;
  UI0ControlRecord ui_control_records[Lectern0UIRecordCap];
  UI0DrawContext ui_draw;
  UI0DrawCommand ui_draw_commands[Lectern0UIDrawCap];
  UI0ID open_id;
  UI0ID previous_id;
  UI0ID next_id;
  UI0ID status_id;
  UI0ID toolbar_id;
  Lectern0Input input;
  Lectern0Action pending_action;

  B32 persistence_enabled;
  Lectern0SavedState saved;
  char app_directory[Lectern0PathCap];
  char state_path[Lectern0PathCap];
  char current_path[Lectern0PathCap];
  char status[Lectern0StatusCap];
} Lectern0App;

typedef struct Lectern0Win32
{
  Lectern0App app;
  HWND window;
} Lectern0Win32;

FUNCTION void
lectern0_copy_cstr(char *dst, U64 cap, const char *src)
{
  if (!dst || cap == 0) { return; }
  U64 size = 0;
  if (src)
  {
    while (src[size] && size + 1 < cap)
    {
      dst[size] = src[size];
      size += 1;
    }
  }
  dst[size] = 0;
}

FUNCTION void
lectern0_set_statusf(Lectern0App *app, const char *fmt, ...)
{
  if (!app || !fmt) { return; }
  va_list args;
  va_start(args, fmt);
  (void)cstr_formatv(app->status, ARRAY_COUNT(app->status), fmt, args);
  va_end(args);
}

FUNCTION B32
lectern0_state_paths(Lectern0App *app)
{
  if (!app) { return 0; }
  char local_app_data[Lectern0PathCap] = {0};
  DWORD size = GetEnvironmentVariableA("LOCALAPPDATA",
                                      local_app_data,
                                      (DWORD)ARRAY_COUNT(local_app_data));
  if (size == 0 || size >= ARRAY_COUNT(local_app_data)) { return 0; }
  (void)cstr_format(app->app_directory,
                    ARRAY_COUNT(app->app_directory),
                    "%s\\lectern0",
                    local_app_data);
  (void)cstr_format(app->state_path,
                    ARRAY_COUNT(app->state_path),
                    "%s\\state.v1",
                    app->app_directory);
  return os_make_directory_chain(app->app_directory);
}

FUNCTION void
lectern0_load_state(Lectern0App *app)
{
  if (!app || !app->persistence_enabled || !app->state_path[0]) { return; }
  char data[Lectern0StateFileCap] = {0};
  U64 size = 0;
  if (!os_read_entire_file(app->state_path, data, sizeof(data) - 1, &size) ||
      size == 0 || size >= sizeof(data))
  {
    return;
  }
  data[size] = 0;

  char *context = 0;
  char *version = strtok_s(data, "\r\n", &context);
  char *spine = strtok_s(0, "\r\n", &context);
  char *byte = strtok_s(0, "\r\n", &context);
  char *path = strtok_s(0, "\r\n", &context);
  if (!version || !spine || !byte || !path || strcmp(version, "LECTERN0_STATE_V1") != 0)
  {
    return;
  }

  unsigned long parsed_spine = strtoul(spine, 0, 10);
  unsigned long long parsed_byte = _strtoui64(byte, 0, 10);
  app->saved.valid = 1;
  app->saved.spine_index = (U32)parsed_spine;
  app->saved.byte_offset = (U64)parsed_byte;
  lectern0_copy_cstr(app->saved.path, ARRAY_COUNT(app->saved.path), path);
}

FUNCTION B32
lectern0_save_state(Lectern0App *app)
{
  if (!app || !app->persistence_enabled || !app->state_path[0] ||
      !epub_reader_is_open(&app->reader) || !app->current_path[0])
  {
    return 0;
  }
  char data[Lectern0StateFileCap] = {0};
  U64 size = cstr_format(data,
                         ARRAY_COUNT(data),
                         "LECTERN0_STATE_V1\n%u\n%llu\n%s\n",
                         (unsigned)app->reader.active_spine_index,
                         (unsigned long long)app->reader.view_byte_offset,
                         app->current_path);
  if (size == 0 || size >= ARRAY_COUNT(data)) { return 0; }
  return os_write_entire_file_atomic(app->state_path, data, size);
}

FUNCTION B32
lectern0_image_cache_init(Lectern0ImageCache *cache)
{
  if (!cache) { return 0; }
  MemoryZeroStruct(cache);
  cache->pixel_arena = arena_alloc(0);
  OS_ImageDecoderInitParams params = {
    .backend = OS_ImageDecoderBackendKind_Win32WIC,
  };
  cache->decoder_ready = os_image_decoder_init(&cache->decoder, &params);
  if (!cache->pixel_arena || !cache->decoder_ready)
  {
    if (cache->decoder_ready) { os_image_decoder_release(&cache->decoder); }
    if (cache->pixel_arena) { arena_release(cache->pixel_arena); }
    MemoryZeroStruct(cache);
    return 0;
  }
  return 1;
}

FUNCTION void
lectern0_image_cache_release(Lectern0ImageCache *cache)
{
  if (!cache) { return; }
  if (cache->decoder_ready) { os_image_decoder_release(&cache->decoder); }
  if (cache->pixel_arena) { arena_release(cache->pixel_arena); }
  MemoryZeroStruct(cache);
}

FUNCTION void
lectern0_image_cache_reset(Lectern0ImageCache *cache)
{
  if (!cache || !cache->pixel_arena) { return; }
  arena_clear(cache->pixel_arena);
  MemoryZeroArray(cache->entries);
  cache->entry_count = 0;
  cache->lookup_count = 0;
  cache->hit_count = 0;
  cache->miss_count = 0;
  cache->cache_full_count = 0;
}

FUNCTION B32
lectern0_image_media_type_is(const char *media_type, const char *expected)
{
  return media_type && expected && _stricmp(media_type, expected) == 0;
}

FUNCTION Lectern0ImageCacheEntry *
lectern0_image_cache_find(Lectern0ImageCache *cache,
                          DocDocumentId document_id,
                          U32 resource_index)
{
  if (!cache) { return 0; }
  for (U32 index = 0; index < cache->entry_count; index += 1)
  {
    Lectern0ImageCacheEntry *entry = cache->entries + index;
    if (entry->document_id == document_id && entry->resource_index == resource_index)
    {
      return entry;
    }
  }
  return 0;
}

FUNCTION EpubReaderFrameImageStatus
lectern0_image_status_from_decode(OS_ImageDecodeStatus status)
{
  switch (status)
  {
    case OS_ImageDecodeStatus_Ok: return EpubReaderFrameImageStatus_Loaded;
    case OS_ImageDecodeStatus_DimensionLimit: return EpubReaderFrameImageStatus_DimensionCap;
    case OS_ImageDecodeStatus_DecodeFailed: return EpubReaderFrameImageStatus_DecodeFailed;
    case OS_ImageDecodeStatus_InvalidInput:
    case OS_ImageDecodeStatus_BackendUnavailable:
    case OS_ImageDecodeStatus_AllocationFailed:
    default: return EpubReaderFrameImageStatus_Unavailable;
  }
}

FUNCTION Lectern0ImageCacheEntry *
lectern0_image_cache_get(Lectern0ImageCache *cache,
                         DocEngine *engine,
                         DocDocumentId document_id,
                         U32 resource_index,
                         const char *media_type)
{
  if (!cache) { return 0; }
  cache->lookup_count += 1;
  Lectern0ImageCacheEntry *entry =
    lectern0_image_cache_find(cache, document_id, resource_index);
  if (entry)
  {
    cache->hit_count += 1;
    return entry;
  }
  if (!cache->decoder_ready || !cache->pixel_arena || !engine || document_id == 0)
  {
    return 0;
  }
  cache->miss_count += 1;
  if (cache->entry_count >= ARRAY_COUNT(cache->entries))
  {
    cache->cache_full_count += 1;
    return 0;
  }

  entry = cache->entries + cache->entry_count;
  *entry = (Lectern0ImageCacheEntry){
    .document_id = document_id,
    .resource_index = resource_index,
    .status = EpubReaderFrameImageStatus_Unavailable,
  };
  cache->entry_count += 1;

  if (lectern0_image_media_type_is(media_type, "image/svg+xml") ||
      lectern0_image_media_type_is(media_type, "image/webp"))
  {
    entry->status = EpubReaderFrameImageStatus_UnsupportedFormat;
    return entry;
  }

  ArenaParams arena_params = {
    .reserve_size = MEGABYTES(32),
    .commit_size = KILOBYTES(64),
  };
  Arena *encoded_arena = arena_alloc(&arena_params);
  if (!encoded_arena) { return entry; }

  String8 encoded_bytes = {0};
  DocError resource_result =
    doc_engine_get_resource_data(encoded_arena,
                                 engine,
                                 document_id,
                                 resource_index,
                                 &encoded_bytes);
  if (resource_result == DocError_Ok)
  {
    OS_DecodedImage decoded = {0};
    OS_ImageDecodeStatus decode_status =
      os_image_decode(&cache->decoder,
                      cache->pixel_arena,
                      encoded_bytes,
                      os_image_decode_limits_default(),
                      &decoded);
    entry->status = lectern0_image_status_from_decode(decode_status);
    if (decode_status == OS_ImageDecodeStatus_Ok)
    {
      entry->pixels = decoded.pixels;
      entry->width = (S32)decoded.width;
      entry->height = (S32)decoded.height;
      entry->stride_pixels = (S32)decoded.stride_pixels;
    }
  }
  arena_release(encoded_arena);
  return entry;
}

FUNCTION void
lectern0_attach_frame_images(Lectern0App *app)
{
  if (!app || !app->frame.ready || !app->frame.document_open) { return; }
  DocEngine *engine = epub_reader_engine(&app->reader);
  DocDocumentId document_id = epub_reader_document_id(&app->reader);
  for (U32 index = 0; index < app->frame.image_count; index += 1)
  {
    EpubReaderFrameImage *image = app->frame.images + index;
    image->status = image->has_resource ?
      EpubReaderFrameImageStatus_Unavailable :
      EpubReaderFrameImageStatus_MissingResource;
    if (!image->has_resource) { continue; }

    Lectern0ImageCacheEntry *entry =
      lectern0_image_cache_get(&app->image_cache,
                               engine,
                               document_id,
                               image->resource_index,
                               image->media_type);
    if (!entry)
    {
      image->status = app->image_cache.entry_count >= Lectern0ImageCacheCap ?
        EpubReaderFrameImageStatus_CacheFull :
        EpubReaderFrameImageStatus_Unavailable;
      continue;
    }
    image->status = entry->status;
    if (entry->status == EpubReaderFrameImageStatus_Loaded && entry->pixels)
    {
      image->pixels = entry->pixels;
      image->src_w = entry->width;
      image->src_h = entry->height;
      image->src_stride_pixels = entry->stride_pixels;
    }
  }
}

FUNCTION B32
lectern0_update_layout_inputs(Lectern0App *app)
{
  if (!app || !epub_reader_is_open(&app->reader)) { return 0; }
  U32 spine_count = 0;
  if (doc_engine_get_page_count(epub_reader_engine(&app->reader),
                                epub_reader_document_id(&app->reader),
                                &spine_count) != DocError_Ok ||
      spine_count == 0)
  {
    return 0;
  }

  S32 content_width = MAX(app->width - 112, 240);
  S32 content_height = MAX(app->height - Lectern0ToolbarHeight - Lectern0FooterHeight - 48, 120);
  S32 text_scale = 18;
  S32 char_advance = 10;
  S32 line_height = 24;
  String8 uri = epub_reader_canonical_uri(&app->reader);

  (void)epub_reader_typography_set_view(&app->reader.typography,
                                        text_scale,
                                        FontProviderBookContentFamily_Georgia,
                                        1);
  epub_reader_typography_set_text_mode(&app->reader.typography,
                                       EpubReaderTextMode_ShapedV1);

  app->layout_key = (EpubReaderLayoutKey){
    .document_id = epub_reader_document_id(&app->reader),
    .source_uri_hash = u64_hash_str8(uri),
    .source_uri_size = uri.size,
    .spine_count = spine_count,
    .wrap_cols = (U32)MAX(content_width / char_advance, 20),
    .page_rows = (U32)MAX(content_height / line_height, 4),
    .text_w = content_width,
    .char_advance = char_advance,
    .line_height = line_height,
    .text_scale = text_scale,
    .margin_unit_permille = 1000,
    .font_family_index = FontProviderBookContentFamily_Georgia,
    .embedded_fonts_enabled = 1,
    .text_mode = EpubReaderTextMode_ShapedV1,
  };
  app->layout_config = (SourceReaderLayoutConfig){
    .wrap_column_count = app->layout_key.wrap_cols,
    .page_row_count = app->layout_key.page_rows,
    .margin_unit_permille = app->layout_key.margin_unit_permille,
    .text_width_px = app->layout_key.text_w,
    .char_advance_px = app->layout_key.char_advance,
    .text_scale = app->layout_key.text_scale,
    .measure_focused_spine_only = 1,
    .load_focused_spine_only = 1,
    .measure_text = epub_reader_typography_measure_text,
    .measure_user = &app->reader.typography,
  };
  return 1;
}

FUNCTION B32
lectern0_capture_frame(Lectern0App *app)
{
  if (!app || !epub_reader_build_frame(&app->reader,
                                       &app->frame_storage,
                                       &app->frame))
  {
    return 0;
  }
  lectern0_attach_frame_images(app);
  return 1;
}

FUNCTION B32
lectern0_frame_text_rows_are_complete(const EpubReaderFrame *frame,
                                      U32 *out_gap_start,
                                      U32 *out_gap_end)
{
  if (out_gap_start) { *out_gap_start = 0; }
  if (out_gap_end) { *out_gap_end = 0; }
  if (!frame || !frame->ready || !frame->document_open) { return 0; }
  U32 covered_end = 0;
  for (U32 row_index = 0; row_index < frame->style_row_count; row_index += 1)
  {
    EpubReaderFrameStyleRow row = frame->style_rows[row_index];
    U32 row_start = MIN(row.byte_start, (U32)frame->visible_text.size);
    U32 row_end = MIN(row.byte_end, (U32)frame->visible_text.size);
    if (row_start < covered_end || row_end < row_start)
    {
      if (out_gap_start) { *out_gap_start = row_start; }
      if (out_gap_end) { *out_gap_end = row_end; }
      return 0;
    }
    for (U32 byte_index = covered_end; byte_index < row_start; byte_index += 1)
    {
      U8 byte = frame->visible_text.str[byte_index];
      if (byte != ' ' && byte != '\t' && byte != '\r' && byte != '\n')
      {
        if (out_gap_start) { *out_gap_start = covered_end; }
        if (out_gap_end) { *out_gap_end = row_start; }
        return 0;
      }
    }
    covered_end = row_end;
  }
  for (U32 byte_index = covered_end;
       byte_index < (U32)frame->visible_text.size;
       byte_index += 1)
  {
    U8 byte = frame->visible_text.str[byte_index];
    if (byte != ' ' && byte != '\t' && byte != '\r' && byte != '\n')
    {
      if (out_gap_start) { *out_gap_start = covered_end; }
      if (out_gap_end) { *out_gap_end = (U32)frame->visible_text.size; }
      return 0;
    }
  }
  return 1;
}

FUNCTION B32
lectern0_repaginate(Lectern0App *app)
{
  if (!lectern0_update_layout_inputs(app)) { return 0; }
  B32 reused = 0;
  if (!epub_reader_rebuild_pagination(&app->reader,
                                      app->layout_key,
                                      app->layout_config,
                                      &reused))
  {
    return 0;
  }
  (void)reused;
  if (!lectern0_capture_frame(app)) { return 0; }
  lectern0_set_statusf(app,
                       "Page %llu/%llu | section %u/%u",
                       (unsigned long long)app->frame.page_index,
                       (unsigned long long)app->frame.page_count,
                       (unsigned)(app->frame.spine_index + 1),
                       (unsigned)app->frame.section_count);
  return 1;
}

FUNCTION B32
lectern0_open_path(Lectern0App *app, const char *path)
{
  if (!app || !path || !path[0]) { return 0; }
  EpubReaderOpenTransition transition = {0};
  EpubReaderResult open_result =
    epub_reader_open(&app->reader,
                     str8_from_cstr(path),
                     DocSourceKind_EPUB,
                     &transition);
  if (open_result != EpubReaderResult_Ok ||
      !epub_reader_refresh_active_spine(&app->reader) ||
      !lectern0_update_layout_inputs(app))
  {
    lectern0_set_statusf(app, "Open failed: %s", epub_reader_result_code(open_result));
    return 0;
  }

  lectern0_copy_cstr(app->current_path, ARRAY_COUNT(app->current_path), path);
  B32 restored = 0;
  if (app->saved.valid && strcmp(app->saved.path, path) == 0)
  {
    restored = epub_reader_set_page_containing_byte(&app->reader,
                                                    app->saved.spine_index,
                                                    app->saved.byte_offset,
                                                    app->layout_key,
                                                    app->layout_config);
  }
  if (!restored)
  {
    B32 reused = 0;
    if (!epub_reader_rebuild_pagination(&app->reader,
                                        app->layout_key,
                                        app->layout_config,
                                        &reused))
    {
      lectern0_set_statusf(app, "Open failed: pagination");
      return 0;
    }
  }
  lectern0_image_cache_reset(&app->image_cache);
  if (!lectern0_capture_frame(app))
  {
    lectern0_set_statusf(app, "Open failed: frame");
    return 0;
  }
  lectern0_set_statusf(app,
                       "%s | page %llu/%llu | section %u/%u",
                       restored ? "Restored" : "Opened",
                       (unsigned long long)app->frame.page_index,
                       (unsigned long long)app->frame.page_count,
                       (unsigned)(app->frame.spine_index + 1),
                       (unsigned)app->frame.section_count);
  (void)transition;
  (void)lectern0_save_state(app);
  return 1;
}

FUNCTION EpubReaderResult
lectern0_move_page(Lectern0App *app, S32 direction)
{
  if (!app || !epub_reader_is_open(&app->reader))
  {
    if (app) { lectern0_set_statusf(app, "Open an EPUB first"); }
    return EpubReaderResult_NotOpen;
  }
  if (!lectern0_update_layout_inputs(app))
  {
    lectern0_set_statusf(app, "Page move failed: layout");
    return EpubReaderResult_DocError;
  }

  EpubReaderChange change = {0};
  EpubReaderResult result = epub_reader_move_page(&app->reader,
                                                   direction,
                                                   app->layout_key,
                                                   app->layout_config,
                                                   (EpubReaderPageMoveOptions){0},
                                                   &change);
  if (result == EpubReaderResult_Boundary)
  {
    lectern0_set_statusf(app, direction < 0 ? "Beginning of book" : "End of book");
    return result;
  }
  if (result != EpubReaderResult_Ok || !change.changed || !lectern0_capture_frame(app))
  {
    lectern0_set_statusf(app,
                         "Page move failed: %s (%d)",
                         epub_reader_result_code(result),
                         (int)change.diagnostic);
    return result == EpubReaderResult_Ok ? EpubReaderResult_DocError : result;
  }

  lectern0_set_statusf(app,
                       "Page %llu/%llu | section %u/%u",
                       (unsigned long long)app->frame.page_index,
                       (unsigned long long)app->frame.page_count,
                       (unsigned)(app->frame.spine_index + 1),
                       (unsigned)app->frame.section_count);
  (void)lectern0_save_state(app);
  return EpubReaderResult_Ok;
}

FUNCTION EpubReaderResult
lectern0_finish_semantic_navigation(Lectern0App *app,
                                    EpubReaderResult result,
                                    const char *operation)
{
  if (!app || !operation) { return EpubReaderResult_InvalidInput; }
  if (result != EpubReaderResult_Ok)
  {
    lectern0_set_statusf(app,
                         "%s failed: %s",
                         operation,
                         epub_reader_result_code(result));
    return result;
  }
  if (!lectern0_capture_frame(app))
  {
    lectern0_set_statusf(app, "%s failed: frame", operation);
    return EpubReaderResult_DocError;
  }
  lectern0_set_statusf(app,
                       "%s | page %llu/%llu | section %u/%u",
                       operation,
                       (unsigned long long)app->frame.page_index,
                       (unsigned long long)app->frame.page_count,
                       (unsigned)(app->frame.spine_index + 1),
                       (unsigned)app->frame.section_count);
  (void)lectern0_save_state(app);
  return EpubReaderResult_Ok;
}

FUNCTION EpubReaderResult
lectern0_navigate_to_nav_point(Lectern0App *app,
                               U32 nav_index,
                               EpubReaderNavPointResult *out_navigation)
{
  if (out_navigation) { *out_navigation = (EpubReaderNavPointResult){0}; }
  if (!app || !out_navigation) { return EpubReaderResult_InvalidInput; }
  if (!epub_reader_is_open(&app->reader))
  {
    lectern0_set_statusf(app, "Open an EPUB first");
    return EpubReaderResult_NotOpen;
  }
  if (!lectern0_update_layout_inputs(app))
  {
    lectern0_set_statusf(app, "Contents failed: layout");
    return EpubReaderResult_DocError;
  }
  EpubReaderResult result =
    epub_reader_navigate_to_nav_point(&app->reader,
                                      nav_index,
                                      app->layout_key,
                                      app->layout_config,
                                      (EpubReaderNavigationOptions){0},
                                      out_navigation);
  return lectern0_finish_semantic_navigation(app, result, "Contents");
}

FUNCTION EpubReaderResult
lectern0_navigate_to_search_match(Lectern0App *app,
                                  U32 match_index,
                                  EpubReaderSearchNavigationResult *out_navigation)
{
  if (out_navigation) { *out_navigation = (EpubReaderSearchNavigationResult){0}; }
  if (!app || !out_navigation) { return EpubReaderResult_InvalidInput; }
  if (!epub_reader_is_open(&app->reader))
  {
    lectern0_set_statusf(app, "Open an EPUB first");
    return EpubReaderResult_NotOpen;
  }
  if (!lectern0_update_layout_inputs(app))
  {
    lectern0_set_statusf(app, "Find failed: layout");
    return EpubReaderResult_DocError;
  }
  EpubReaderResult result =
    epub_reader_navigate_to_search_match(&app->reader,
                                         match_index,
                                         app->layout_key,
                                         app->layout_config,
                                         (EpubReaderNavigationOptions){0},
                                         out_navigation);
  return lectern0_finish_semantic_navigation(app, result, "Find");
}

FUNCTION B32
lectern0_app_init(Lectern0App *app,
                  S32 width,
                  S32 height,
                  B32 graphical,
                  B32 persistence_enabled)
{
  if (!app) { return 0; }
  MemoryZeroStruct(app);
  app->width = width;
  app->height = height;
  app->persistence_enabled = persistence_enabled;
  app->arena = arena_alloc(0);
  if (!app->arena) { return 0; }

  if (persistence_enabled && lectern0_state_paths(app))
  {
    lectern0_load_state(app);
  }
  String8 private_font_directory = app->app_directory[0] ?
    str8_from_cstr(app->app_directory) : str8_from_cstr(".");
  EpubReaderConfig config = {
    .typography = {
      .private_font_directory = private_font_directory,
      .instance_key = (U64)(size_t)&app->reader,
    },
  };
  if (epub_reader_init(&app->reader, app->arena, config) != EpubReaderResult_Ok)
  {
    arena_release(app->arena);
    app->arena = 0;
    return 0;
  }
  if (!lectern0_image_cache_init(&app->image_cache))
  {
    epub_reader_release(&app->reader);
    arena_release(app->arena);
    app->arena = 0;
    return 0;
  }

  if (graphical)
  {
    render_state_init(&app->render_state, 0);
    app->render_ready = 1;
    ui0_signal_context_init(&app->ui_signals);
    ui0_control_context_init(&app->ui_controls);
    ui0_draw_context_init(&app->ui_draw);
    app->toolbar_id = ui0_id_from_string("lectern0.toolbar");
    app->open_id = ui0_id_from_string("lectern0.open");
    app->previous_id = ui0_id_from_string("lectern0.previous");
    app->next_id = ui0_id_from_string("lectern0.next");
    app->status_id = ui0_id_from_string("lectern0.status");
  }
  lectern0_set_statusf(app, "Open an EPUB | Ctrl+O");
  return 1;
}

FUNCTION void
lectern0_app_release(Lectern0App *app)
{
  if (!app) { return; }
  (void)lectern0_save_state(app);
  if (app->gfx_ready) { os_gfx_release(&app->gfx); }
  if (app->render_ready) { render_state_release(&app->render_state); }
  lectern0_image_cache_release(&app->image_cache);
  epub_reader_release(&app->reader);
  if (app->arena) { arena_release(app->arena); }
  MemoryZeroStruct(app);
}

FUNCTION const char *
lectern0_ui_label(const Lectern0App *app, UI0ID id)
{
  if (!app) { return ""; }
  if (id == app->open_id) { return "Open"; }
  if (id == app->previous_id) { return "Previous"; }
  if (id == app->next_id) { return "Next"; }
  if (id == app->status_id) { return app->status; }
  return "";
}

FUNCTION U32
lectern0_draw_color(UI0Color color)
{
  return color & 0x00FFFFFFU;
}

FUNCTION DrawTextHAlign
lectern0_draw_align_x(UI0TextAlignX align)
{
  switch (align)
  {
    case UI0TextAlignX_Center: return DrawTextHAlign_Center;
    case UI0TextAlignX_End: return DrawTextHAlign_Right;
    default: return DrawTextHAlign_Left;
  }
}

FUNCTION DrawTextVAlign
lectern0_draw_align_y(UI0TextAlignY align)
{
  switch (align)
  {
    case UI0TextAlignY_Top: return DrawTextVAlign_Top;
    case UI0TextAlignY_Bottom: return DrawTextVAlign_Bottom;
    default: return DrawTextVAlign_Center;
  }
}

FUNCTION void
lectern0_adapt_ui0_draw(Lectern0App *app)
{
  if (!app) { return; }
  for (UI0S32 index = 0; index < app->ui_draw.command_count; index += 1)
  {
    UI0DrawCommand command = app->ui_draw.commands[index];
    U32 color = lectern0_draw_color(command.color);
    switch (command.op)
    {
      case UI0DrawOp_ControlFill:
      case UI0DrawOp_IndicatorFill:
      case UI0DrawOp_ToggleTrack:
      case UI0DrawOp_ToggleKnob:
      case UI0DrawOp_ScrollTrack:
      case UI0DrawOp_ScrollThumb:
      case UI0DrawOp_SliderTrack:
      case UI0DrawOp_SliderFill:
      case UI0DrawOp_SliderThumb:
      case UI0DrawOp_TextSelection:
      {
        (void)draw_push_rounded_rect_clipped(&app->draw_commands,
                                             DrawLayer_UI,
                                             command.rect.x,
                                             command.rect.y,
                                             command.rect.w,
                                             command.rect.h,
                                             MAX(command.corner_radius, 0),
                                             color,
                                             color,
                                             command.clip_rect.x,
                                             command.clip_rect.y,
                                             command.clip_rect.w,
                                             command.clip_rect.h);
      } break;

      case UI0DrawOp_ControlBorder:
      case UI0DrawOp_IndicatorBorder:
      case UI0DrawOp_FocusRing:
      {
        (void)draw_push_rounded_rect_stroke_clipped(&app->draw_commands,
                                                    DrawLayer_UI,
                                                    command.rect.x,
                                                    command.rect.y,
                                                    command.rect.w,
                                                    command.rect.h,
                                                    MAX(command.corner_radius, 0),
                                                    MAX(command.stroke_width, 1),
                                                    color,
                                                    command.clip_rect.x,
                                                    command.clip_rect.y,
                                                    command.clip_rect.w,
                                                    command.clip_rect.h);
      } break;

      case UI0DrawOp_Text:
      {
        const char *label = lectern0_ui_label(app, command.source_id);
        (void)draw_push_text_in_rect(&app->draw_commands,
                                     DrawLayer_UI,
                                     app->render_state.text_provider,
                                     label,
                                     command.rect.x,
                                     command.rect.y,
                                     command.rect.w,
                                     command.rect.h,
                                     8,
                                     MAX(command.typography_line_height, 14),
                                     lectern0_draw_align_x(command.text_align_x),
                                     lectern0_draw_align_y(command.text_align_y),
                                     color);
      } break;

      default: break;
    }
  }
}

FUNCTION B32
lectern0_pick_epub(Lectern0App *app)
{
  if (!app || !app->window) { return 0; }
  wchar_t path[Lectern0PathCap] = {0};
  OPENFILENAMEW dialog = {0};
  dialog.lStructSize = sizeof(dialog);
  dialog.hwndOwner = app->window;
  dialog.lpstrFilter = L"EPUB Books\0*.epub\0All Files\0*.*\0";
  dialog.lpstrFile = path;
  dialog.nMaxFile = ARRAY_COUNT(path);
  dialog.Flags = OFN_FILEMUSTEXIST | OFN_PATHMUSTEXIST;
  if (!GetOpenFileNameW(&dialog)) { return 0; }
  char utf8_path[Lectern0PathCap] = {0};
  if (WideCharToMultiByte(CP_UTF8,
                          0,
                          path,
                          -1,
                          utf8_path,
                          ARRAY_COUNT(utf8_path),
                          0,
                          0) <= 0)
  {
    lectern0_set_statusf(app, "Open failed: path encoding");
    return 0;
  }
  return lectern0_open_path(app, utf8_path);
}

FUNCTION void
lectern0_build_toolbar(Lectern0App *app)
{
  ui0_layout_begin(&app->ui_layout, app->ui_boxes, Lectern0UIBoxCap);
  UI0BoxDesc root_desc = ui0_box_desc("toolbar",
                                      UI0LayoutInvalidIndex,
                                      UI0Axis_X,
                                      ui0_size_fill(1, 0, UI0LayoutNoMax),
                                      ui0_size_fixed(Lectern0ToolbarHeight));
  root_desc.padding = ui0_insets_xy(8, 6);
  root_desc.gap = 6;
  root_desc.flags = UI0BoxFlag_Clip;
  UI0S32 root = ui0_layout_add_box(&app->ui_layout, &root_desc);

  UI0BoxDesc open_desc = ui0_box_desc("open", root, UI0Axis_X,
                                      ui0_size_fixed(84), ui0_size_fill(1, 0, UI0LayoutNoMax));
  UI0BoxDesc previous_desc = ui0_box_desc("previous", root, UI0Axis_X,
                                          ui0_size_fixed(96), ui0_size_fill(1, 0, UI0LayoutNoMax));
  UI0BoxDesc next_desc = ui0_box_desc("next", root, UI0Axis_X,
                                      ui0_size_fixed(84), ui0_size_fill(1, 0, UI0LayoutNoMax));
  UI0BoxDesc status_desc = ui0_box_desc("status", root, UI0Axis_X,
                                        ui0_size_fill(1, 160, UI0LayoutNoMax),
                                        ui0_size_fill(1, 0, UI0LayoutNoMax));
  UI0S32 open_box = ui0_layout_add_box(&app->ui_layout, &open_desc);
  UI0S32 previous_box = ui0_layout_add_box(&app->ui_layout, &previous_desc);
  UI0S32 next_box = ui0_layout_add_box(&app->ui_layout, &next_desc);
  UI0S32 status_box = ui0_layout_add_box(&app->ui_layout, &status_desc);
  (void)ui0_layout_solve(&app->ui_layout,
                         root,
                         ui0_rect(0, 0, app->width, Lectern0ToolbarHeight));

  UI0InputState input = ui0_input_pointer(app->input.pointer_x,
                                           app->input.pointer_y,
                                           app->input.pointer_down,
                                           app->input.pointer_pressed,
                                           app->input.pointer_released);
  if (app->input.activate_pressed) { input.flags |= UI0Input_ActivatePressed; }
  if (app->input.focus_next_pressed) { input.flags |= UI0Input_FocusNextPressed; }
  if (app->input.focus_prev_pressed) { input.flags |= UI0Input_FocusPrevPressed; }
  ui0_signal_begin_frame(&app->ui_signals,
                         input,
                         app->ui_signal_records,
                         Lectern0UIRecordCap);
  ui0_signal_set_root(&app->ui_signals,
                      UI0RootKind_Normal,
                      ui0_rect(0, 0, app->width, Lectern0ToolbarHeight),
                      1);
  ui0_signal_resolve_roots(&app->ui_signals);
  ui0_control_begin_frame(&app->ui_controls,
                          app->ui_control_records,
                          Lectern0UIRecordCap);
  (void)ui0_toolbar_row(&app->ui_controls,
                        &app->ui_layout,
                        (UI0ControlSpec){app->toolbar_id, root, UI0RootKind_Normal, "", 0});
  UI0ControlResult open = ui0_text_button(&app->ui_controls,
                                           &app->ui_signals,
                                           &app->ui_layout,
                                           (UI0ControlSpec){app->open_id, open_box, UI0RootKind_Normal, "Open", UI0Control_Primary});
  UI0ControlFlags navigation_flags = epub_reader_is_open(&app->reader) ? 0 : UI0Control_Disabled;
  UI0ControlResult previous = ui0_text_button(&app->ui_controls,
                                               &app->ui_signals,
                                               &app->ui_layout,
                                               (UI0ControlSpec){app->previous_id, previous_box, UI0RootKind_Normal, "Previous", navigation_flags});
  UI0ControlResult next = ui0_text_button(&app->ui_controls,
                                           &app->ui_signals,
                                           &app->ui_layout,
                                           (UI0ControlSpec){app->next_id, next_box, UI0RootKind_Normal, "Next", navigation_flags});
  (void)ui0_label(&app->ui_controls,
                  &app->ui_layout,
                  (UI0ControlSpec){app->status_id, status_box, UI0RootKind_Normal, app->status, 0});
  ui0_signal_end_frame(&app->ui_signals);

  if (open.clicked) { app->pending_action = Lectern0Action_Open; }
  else if (previous.clicked) { app->pending_action = Lectern0Action_Previous; }
  else if (next.clicked) { app->pending_action = Lectern0Action_Next; }

  ui0_draw_begin_frame(&app->ui_draw,
                       app->ui_draw_commands,
                       Lectern0UIDrawCap,
                       ui0_draw_light_theme());
  (void)ui0_draw_controls(&app->ui_draw,
                          app->ui_controls.records,
                          app->ui_controls.record_count);
  lectern0_adapt_ui0_draw(app);
}

FUNCTION void
lectern0_apply_pending_action(Lectern0App *app)
{
  if (!app || app->pending_action == Lectern0Action_None) { return; }
  Lectern0Action action = app->pending_action;
  app->pending_action = Lectern0Action_None;
  switch (action)
  {
    case Lectern0Action_Open: (void)lectern0_pick_epub(app); break;
    case Lectern0Action_Previous: (void)lectern0_move_page(app, -1); break;
    case Lectern0Action_Next: (void)lectern0_move_page(app, 1); break;
    default: break;
  }
}

FUNCTION EpubReaderFrameImage *
lectern0_image_for_row(EpubReaderFrame *frame, U32 row)
{
  if (!frame) { return 0; }
  for (U32 index = 0; index < frame->image_count; index += 1)
  {
    if (frame->images[index].row == row) { return frame->images + index; }
  }
  return 0;
}

FUNCTION U64
lectern0_text_chunk_end(String8 text, U64 start)
{
  if (!text.str || start >= text.size) { return start; }
  U64 limit = MIN(start + (U64)ZF_DRAW_TEXT_CAP - 1, text.size);
  U64 end = start;
  while (end < limit)
  {
    U64 next = base_unicode_utf8_next_grapheme_boundary(text, end);
    if (next <= end || next > limit) { break; }
    end = next;
  }
  if (end <= start) { return start; }
  if (end < text.size)
  {
    for (U64 candidate = end; candidate > start; candidate -= 1)
    {
      U8 byte = text.str[candidate - 1];
      if (byte == ' ' || byte == '\t' || byte == '\r' || byte == '\n')
      {
        return candidate;
      }
    }
  }
  return end;
}

FUNCTION B32
lectern0_push_reader_text_chunks(Lectern0App *app,
                                 String8 text,
                                 const EpubReaderFrameStyleRow *row,
                                 const TextEngineResolvedStyle *style,
                                 S32 x,
                                 S32 baseline,
                                 S32 scale,
                                 U32 color,
                                 S32 clip_x,
                                 S32 clip_y,
                                 S32 clip_w,
                                 S32 clip_h)
{
  if (!app || !row || !style || !text.str) { return 0; }
  FontTag render_tag =
    font_cache_tag_from_provider(&app->render_state.text_cache, style->provider);
  U64 at = 0;
  while (at < text.size)
  {
    U64 end = lectern0_text_chunk_end(text, at);
    if (end <= at) { return 0; }
    String8 chunk = str8(text.str + at, end - at);
    B32 pushed = draw_push_text_clipped_baseline_tag_s8(&app->draw_commands,
                                                        DrawLayer_World,
                                                        chunk,
                                                        x,
                                                        baseline,
                                                        scale,
                                                        color,
                                                        clip_x,
                                                        clip_y,
                                                        clip_w,
                                                        clip_h,
                                                        render_tag,
                                                        style->raster_flags);
    if (!pushed) { return 0; }
    if (app->reader.typography.text_mode == EpubReaderTextMode_ShapedV1)
    {
      U16 command_count = app->draw_commands.command_count[DrawLayer_World];
      if (command_count == 0) { return 0; }
      DrawCommand *command =
        &app->draw_commands.commands[DrawLayer_World][command_count - 1];
      if (command->type != DrawCommandType_Text) { return 0; }
      command->v.text.flags |= DrawTextFlag_Shaped;
    }
    x += epub_reader_typography_measure_text(&app->reader.typography,
                                             chunk,
                                             row->block_style_flags,
                                             row->font_scale_permille,
                                             row->font_family_hint,
                                             row->font_face_index);
    at = end;
  }
  return 1;
}

FUNCTION B32
lectern0_fit_image_rect(S32 src_w,
                        S32 src_h,
                        S32 rect_x,
                        S32 rect_y,
                        S32 rect_w,
                        S32 rect_h,
                        S32 *out_x,
                        S32 *out_y,
                        S32 *out_w,
                        S32 *out_h)
{
  if (src_w <= 0 || src_h <= 0 || rect_w <= 0 || rect_h <= 0 ||
      !out_x || !out_y || !out_w || !out_h)
  {
    return 0;
  }
  S64 fit_w = rect_w;
  S64 fit_h = ((S64)src_h * fit_w) / src_w;
  if (fit_h > rect_h)
  {
    fit_h = rect_h;
    fit_w = ((S64)src_w * fit_h) / src_h;
  }
  if (fit_w <= 0 || fit_h <= 0) { return 0; }
  *out_w = (S32)fit_w;
  *out_h = (S32)fit_h;
  *out_x = rect_x + MAX((rect_w - *out_w) / 2, 0);
  *out_y = rect_y + MAX((rect_h - *out_h) / 2, 0);
  return 1;
}

typedef struct Lectern0PresentationRowMetrics
{
  S32 scale_px;
  S32 line_height_px;
  S32 margin_before_px;
  S32 margin_after_px;
  S32 content_left_px;
  S32 content_right_px;
} Lectern0PresentationRowMetrics;

typedef struct Lectern0PresentationImageBox
{
  S32 x_offset_px;
  S32 width_px;
  S32 height_px;
} Lectern0PresentationImageBox;

FUNCTION B32
lectern0_resolve_scaled_px(S32 base_px,
                           U32 permille,
                           S32 minimum_px,
                           S32 *out_px)
{
  if (!out_px || base_px <= 0 || minimum_px < 0) { return 0; }
  S64 resolved = ((S64)base_px * (S64)MAX(permille, 1U)) / 1000;
  resolved = MAX(resolved, (S64)minimum_px);
  if (resolved > (S64)INT32_MAX) { return 0; }
  *out_px = (S32)resolved;
  return 1;
}

FUNCTION B32
lectern0_resolve_nonnegative_product(S32 value,
                                     S32 unit_px,
                                     S32 *out_px)
{
  if (!out_px || unit_px <= 0) { return 0; }
  S64 resolved = (S64)MAX(value, 0) * (S64)unit_px;
  if (resolved > (S64)INT32_MAX) { return 0; }
  *out_px = (S32)resolved;
  return 1;
}

FUNCTION B32
lectern0_resolve_presentation_row_metrics(const Lectern0App *app,
                                          const EpubReaderFrameStyleRow *row,
                                          Lectern0PresentationRowMetrics *out_metrics)
{
  if (out_metrics) { MemoryZeroStruct(out_metrics); }
  if (!app || !row || !out_metrics || app->layout_key.char_advance <= 0)
  {
    return 0;
  }

  Lectern0PresentationRowMetrics metrics = {0};
  if (!lectern0_resolve_scaled_px(app->layout_key.text_scale,
                                  row->font_scale_permille,
                                  12,
                                  &metrics.scale_px))
  {
    return 0;
  }
  if (metrics.scale_px > INT32_MAX - 4 ||
      !lectern0_resolve_scaled_px(app->layout_key.line_height,
                                  row->line_height_permille,
                                  metrics.scale_px + 4,
                                  &metrics.line_height_px) ||
      !lectern0_resolve_nonnegative_product(row->margin_top_rows,
                                            app->layout_key.line_height,
                                            &metrics.margin_before_px) ||
      !lectern0_resolve_nonnegative_product(row->margin_bottom_rows,
                                            app->layout_key.line_height,
                                            &metrics.margin_after_px) ||
      !lectern0_resolve_nonnegative_product(row->margin_left_cols,
                                            app->layout_key.char_advance,
                                            &metrics.content_left_px) ||
      !lectern0_resolve_nonnegative_product(row->margin_right_cols,
                                            app->layout_key.char_advance,
                                            &metrics.content_right_px))
  {
    return 0;
  }

  if (row->line_row != 0)
  {
    metrics.margin_before_px = 0;
  }
  else
  {
    S32 indent_px = 0;
    if (!lectern0_resolve_nonnegative_product(row->text_indent_cols,
                                              app->layout_key.char_advance,
                                              &indent_px))
    {
      return 0;
    }
    S64 content_left = (S64)metrics.content_left_px + (S64)indent_px;
    if (content_left > (S64)INT32_MAX) { return 0; }
    metrics.content_left_px = (S32)content_left;
  }

  *out_metrics = metrics;
  return 1;
}

FUNCTION PresentationEngineBlockRole
lectern0_presentation_block_role(DocTextBlockKind kind)
{
  switch (kind)
  {
    case DocTextBlockKind_Heading: return PresentationEngineBlockRole_Heading;
    case DocTextBlockKind_Paragraph: return PresentationEngineBlockRole_Paragraph;
    case DocTextBlockKind_Blockquote: return PresentationEngineBlockRole_Blockquote;
    case DocTextBlockKind_ListItem: return PresentationEngineBlockRole_ListItem;
    case DocTextBlockKind_Preformatted: return PresentationEngineBlockRole_Preformatted;
    case DocTextBlockKind_FigureCaption: return PresentationEngineBlockRole_FigureCaption;
    case DocTextBlockKind_TableRow: return PresentationEngineBlockRole_TableRow;
    case DocTextBlockKind_DefinitionTerm: return PresentationEngineBlockRole_DefinitionTerm;
    case DocTextBlockKind_DefinitionDescription: return PresentationEngineBlockRole_DefinitionDescription;
    case DocTextBlockKind_Separator: return PresentationEngineBlockRole_Separator;
    case DocTextBlockKind_Metadata: return PresentationEngineBlockRole_Metadata;
    default: return PresentationEngineBlockRole_None;
  }
}

FUNCTION PresentationEngineTextAlign
lectern0_presentation_text_align(DocTextAlign align)
{
  switch (align)
  {
    case DocTextAlign_Center: return PresentationEngineTextAlign_Center;
    case DocTextAlign_Right: return PresentationEngineTextAlign_Right;
    case DocTextAlign_Justify: return PresentationEngineTextAlign_Justify;
    default: return PresentationEngineTextAlign_Left;
  }
}

FUNCTION PresentationEngineMediaStatus
lectern0_presentation_media_status(EpubReaderFrameImageStatus status)
{
  if (status == EpubReaderFrameImageStatus_Loaded)
  {
    return PresentationEngineMediaStatus_Available;
  }
  if (status == EpubReaderFrameImageStatus_UnsupportedFormat)
  {
    return PresentationEngineMediaStatus_Unsupported;
  }
  return PresentationEngineMediaStatus_Missing;
}

FUNCTION B32
lectern0_resolve_presentation_image_box(const EpubReaderFrameImage *image,
                                        S32 body_width_px,
                                        S32 content_left_px,
                                        Lectern0PresentationImageBox *out_box)
{
  if (out_box) { MemoryZeroStruct(out_box); }
  if (!image || !out_box || body_width_px <= 0 || content_left_px < 0)
  {
    return 0;
  }

  S64 available_width = (S64)body_width_px -
                        (S64)content_left_px;
  S32 image_width = (S32)MAX(available_width, 1);
  image_width = MAX(image_width, 80);
  if (image->display_w_px > 0)
  {
    image_width = MIN(image_width, image->display_w_px);
  }
  if (image_width <= 0) { return 0; }

  S64 desired_height = image->display_h_px;
  if (desired_height <= 0 && image->src_w > 0 && image->src_h > 0)
  {
    desired_height = ((S64)image->src_h * (S64)image_width) / (S64)image->src_w;
  }
  desired_height = MIN(MAX(desired_height, 72), 320);

  S32 x_offset = content_left_px;
  if (image->image_placement == SourceReaderLayoutImagePlacement_ImageOnly)
  {
    x_offset = MAX((body_width_px - image_width) / 2, 0);
  }
  *out_box = (Lectern0PresentationImageBox){
    .x_offset_px = x_offset,
    .width_px = image_width,
    .height_px = (S32)desired_height,
  };
  return 1;
}

FUNCTION U64
lectern0_presentation_hash_mix(U64 state, U64 value)
{
  return (state ^ value) * 1099511628211ULL;
}

FUNCTION U64
lectern0_presentation_frame_hash(const PresentationEngineBlockFlowFrame *frame)
{
  if (!frame || !frame->valid) { return 0; }
  U64 hash = 1469598103934665603ULL;
  hash = lectern0_presentation_hash_mix(hash, frame->row_count);
  hash = lectern0_presentation_hash_mix(hash, frame->media_count);
  hash = lectern0_presentation_hash_mix(hash, (U64)(S64)frame->content_height_px);
  for (U32 index = 0; index < frame->row_count; index += 1)
  {
    const PresentationEngineBlockFlowRow *row = frame->rows + index;
    hash = lectern0_presentation_hash_mix(hash, row->role);
    hash = lectern0_presentation_hash_mix(hash, row->source_row);
    hash = lectern0_presentation_hash_mix(hash, row->source_start);
    hash = lectern0_presentation_hash_mix(hash, row->source_end);
    hash = lectern0_presentation_hash_mix(hash, (U64)(S64)row->row_rect.x);
    hash = lectern0_presentation_hash_mix(hash, (U64)(S64)row->row_rect.y);
    hash = lectern0_presentation_hash_mix(hash, (U64)(S64)row->row_rect.h);
    hash = lectern0_presentation_hash_mix(hash, (U64)(S64)row->content_rect.x);
    hash = lectern0_presentation_hash_mix(hash, (U64)(S64)row->content_rect.w);
    hash = lectern0_presentation_hash_mix(hash, row->first_media_index);
  }
  for (U32 index = 0; index < frame->media_count; index += 1)
  {
    const PresentationEngineBlockFlowMedia *media = frame->media + index;
    hash = lectern0_presentation_hash_mix(hash, media->row_index);
    hash = lectern0_presentation_hash_mix(hash, media->status);
    hash = lectern0_presentation_hash_mix(hash, (U64)(S64)media->rect.x);
    hash = lectern0_presentation_hash_mix(hash, (U64)(S64)media->rect.y);
    hash = lectern0_presentation_hash_mix(hash, (U64)(S64)media->rect.w);
    hash = lectern0_presentation_hash_mix(hash, (U64)(S64)media->rect.h);
  }
  return hash;
}

FUNCTION B32
lectern0_build_reader_presentation(Lectern0App *app,
                                   S32 body_x,
                                   S32 body_y,
                                   S32 body_w,
                                   S32 body_h)
{
  if (!app || body_w <= 0 || body_h <= 0 ||
      app->frame.style_row_count > Lectern0PresentationRowCap ||
      app->frame.image_count > Lectern0PresentationMediaCap)
  {
    return 0;
  }

  app->presentation_hash = 0;
  MemoryZero(&app->presentation_frame, sizeof(app->presentation_frame));
  MemoryZero(app->presentation_row_specs, sizeof(app->presentation_row_specs));
  MemoryZero(app->presentation_media_specs, sizeof(app->presentation_media_specs));
  MemoryZero(app->presentation_rows, sizeof(app->presentation_rows));
  MemoryZero(app->presentation_media, sizeof(app->presentation_media));

  U32 media_count = 0;
  for (U32 row_index = 0; row_index < app->frame.style_row_count; row_index += 1)
  {
    const EpubReaderFrameStyleRow *row = app->frame.style_rows + row_index;
    Lectern0PresentationRowMetrics metrics = {0};
    if (!lectern0_resolve_presentation_row_metrics(app, row, &metrics) ||
        row->byte_end < row->byte_start)
    {
      return 0;
    }

    EpubReaderFrameImage *image = lectern0_image_for_row(&app->frame, row->row);
    S32 row_height_px = metrics.line_height_px;
    PresentationEngineBlockRole role = lectern0_presentation_block_role(row->block_kind);
    if (image)
    {
      Lectern0PresentationImageBox box = {0};
      if (media_count >= Lectern0PresentationMediaCap ||
          image->text_byte_end < image->text_byte_start ||
          !lectern0_resolve_presentation_image_box(image,
                                                   body_w,
                                                   metrics.content_left_px,
                                                   &box))
      {
        return 0;
      }
      row_height_px = box.height_px + 8;
      role = PresentationEngineBlockRole_Media;
      app->presentation_media_specs[media_count] =
        (PresentationEngineBlockFlowMediaSpec){
          .row_index = row_index,
          .status = lectern0_presentation_media_status(image->status),
          .source_start = image->text_byte_start,
          .source_end = image->text_byte_end,
          .x_offset_px = box.x_offset_px,
          .width_px = box.width_px,
          .height_px = box.height_px,
          .resource_index = image->has_resource ?
            image->resource_index : PRESENTATION_ENGINE_INDEX_NONE,
          .alt_text_hash = (U32)u64_hash_str8(
            str8((U8 *)image->alt_text, image->alt_text_size)),
        };
      media_count += 1;
    }

    PresentationEngineBlockRowFlags flags =
      PresentationEngineBlockRowFlag_SourceOwned;
    if (row->block_flags & DocTextBlockFlag_PageBreakBefore)
    {
      flags |= PresentationEngineBlockRowFlag_PageBreakBefore;
    }
    if (row->block_flags & DocTextBlockFlag_PageBreakAfter)
    {
      flags |= PresentationEngineBlockRowFlag_PageBreakAfter;
    }
    app->presentation_row_specs[row_index] =
      (PresentationEngineBlockFlowRowSpec){
        .role = role,
        .flags = flags,
        .source_row = row->row,
        .source_start = row->byte_start,
        .source_end = row->byte_end,
        .visible_start = row->byte_start,
        .visible_end = row->byte_end,
        .source_anchor = row->byte_start,
        .block_key = row->row,
        .line_row = row->line_row,
        .heading_level = row->heading_level,
        .text_align = lectern0_presentation_text_align(row->text_align),
        .style_index = row_index,
        .text_index = row_index,
        .table_index = row->table_index,
        .table_row_index = row->table_row_index,
        .height_px = row_height_px,
        .margin_before_px = metrics.margin_before_px,
        .margin_after_px = image ? 0 : metrics.margin_after_px,
        .content_left_px = metrics.content_left_px,
        .content_right_px = metrics.content_right_px,
      };
  }

  PresentationEngineBuildResult build_result =
    presentation_engine_block_flow_build(
      &(PresentationEngineBlockFlowBuildParams){
        .rows = app->presentation_row_specs,
        .row_count = app->frame.style_row_count,
        .media = app->presentation_media_specs,
        .media_count = media_count,
        .viewport_rect = {body_x, body_y, body_w, body_h},
      },
      &(PresentationEngineBlockFlowStorage){
        .rows = app->presentation_rows,
        .row_capacity = Lectern0PresentationRowCap,
        .media = app->presentation_media,
        .media_capacity = Lectern0PresentationMediaCap,
      },
      &app->presentation_frame);
  if (build_result != PresentationEngineBuildResult_Complete ||
      !app->presentation_frame.valid ||
      app->presentation_frame.row_count != app->frame.style_row_count ||
      app->presentation_frame.media_count != media_count ||
      app->presentation_frame.content_height_px > body_h)
  {
    return 0;
  }

  app->presentation_hash =
    lectern0_presentation_frame_hash(&app->presentation_frame);
  return 1;
}

FUNCTION void
lectern0_draw_reader_page(Lectern0App *app)
{
  S32 body_x = 48;
  S32 body_y = Lectern0ToolbarHeight + 22;
  S32 body_w = MAX(app->width - 96, 1);
  S32 body_h = MAX(app->height - body_y - Lectern0FooterHeight - 12, 1);
  (void)draw_push_rounded_rect(&app->draw_commands,
                               DrawLayer_World,
                               body_x - 16,
                               body_y - 12,
                               body_w + 32,
                               body_h + 20,
                               8,
                               0x00FFFDF8U,
                               0x00D8D2C8U);

  if (!app->frame.ready || !app->frame.document_open)
  {
    MemoryZeroStruct(&app->presentation_frame);
    app->presentation_hash = 0;
    (void)draw_push_text_in_rect(&app->draw_commands,
                                 DrawLayer_World,
                                 app->render_state.text_provider,
                                 "Open an EPUB to begin reading.",
                                 body_x,
                                 body_y,
                                 body_w,
                                 body_h,
                                 12,
                                 22,
                                 DrawTextHAlign_Center,
                                 DrawTextVAlign_Center,
                                 0x004C4A46U);
    return;
  }

  if (!lectern0_build_reader_presentation(app,
                                          body_x,
                                          body_y,
                                          body_w,
                                          body_h))
  {
    app->presentation_complete = 0;
  }

  U32 row_index = 0;
  for (;
       app->presentation_frame.valid &&
       row_index < app->presentation_frame.row_count;
       row_index += 1)
  {
    const PresentationEngineBlockFlowRow *presentation_row =
      app->presentation_frame.rows + row_index;
    if (presentation_row->style_index >= app->frame.style_row_count)
    {
      app->presentation_complete = 0;
      break;
    }
    const EpubReaderFrameStyleRow *row =
      app->frame.style_rows + presentation_row->style_index;
    U32 start = MIN(row->byte_start, (U32)app->frame.visible_text.size);
    U32 end = MIN(row->byte_end, (U32)app->frame.visible_text.size);
    while (end > start &&
           (app->frame.visible_text.str[end - 1] == '\n' ||
            app->frame.visible_text.str[end - 1] == '\r'))
    {
      end -= 1;
    }
    Lectern0PresentationRowMetrics metrics = {0};
    S64 row_bottom = (S64)presentation_row->row_rect.y +
                     (S64)presentation_row->row_rect.h;
    if (!lectern0_resolve_presentation_row_metrics(app, row, &metrics) ||
        presentation_row->row_rect.y < body_y ||
        row_bottom > (S64)body_y + (S64)body_h)
    {
      app->presentation_complete = 0;
      break;
    }
    S32 x = presentation_row->content_rect.x;
    S32 y = presentation_row->row_rect.y;

    EpubReaderFrameImage *image = lectern0_image_for_row(&app->frame, row->row);
    if (image)
    {
      if (presentation_row->first_media_index == PRESENTATION_ENGINE_INDEX_NONE ||
          presentation_row->media_count != 1 ||
          presentation_row->first_media_index >= app->presentation_frame.media_count)
      {
        app->presentation_complete = 0;
        break;
      }
      const PresentationEngineBlockFlowMedia *media =
        app->presentation_frame.media + presentation_row->first_media_index;
      S32 image_x = media->rect.x;
      S32 image_y = media->rect.y;
      S32 image_w = media->rect.w;
      S32 image_h = media->rect.h;
      S64 image_right = (S64)image_x + (S64)image_w;
      S64 image_bottom = (S64)image_y + (S64)image_h;
      if (media->row_index != row_index || image_x < body_x || image_y < body_y ||
          image_right > (S64)body_x + (S64)body_w ||
          image_bottom > (S64)body_y + (S64)body_h)
      {
        app->presentation_complete = 0;
        break;
      }
      (void)draw_push_rounded_rect(&app->draw_commands,
                                   DrawLayer_World,
                                   image_x,
                                   image_y,
                                   image_w,
                                   image_h,
                                   6,
                                   0x00F1EEE8U,
                                   0x00B8B1A6U);
      if (image->status == EpubReaderFrameImageStatus_Loaded && image->pixels)
      {
        S32 fit_x = 0;
        S32 fit_y = 0;
        S32 fit_w = 0;
        S32 fit_h = 0;
        if (!lectern0_fit_image_rect(image->src_w,
                                     image->src_h,
                                     image_x,
                                     image_y,
                                     image_w,
                                     image_h,
                                     &fit_x,
                                     &fit_y,
                                     &fit_w,
                                     &fit_h) ||
            !draw_push_sprite_clipped(&app->draw_commands,
                                      DrawLayer_World,
                                      image->pixels,
                                      image->src_w,
                                      image->src_h,
                                      image->src_stride_pixels,
                                      fit_x,
                                      fit_y,
                                      fit_w,
                                      fit_h,
                                      body_x,
                                      body_y,
                                      body_w,
                                      body_h))
        {
          app->presentation_complete = 0;
        }
      }
      else
      {
        char placeholder[224] = {0};
        if (image->alt_text_size > 0)
        {
          (void)cstr_format(placeholder,
                            ARRAY_COUNT(placeholder),
                            "Image: %s",
                            image->alt_text);
        }
        else
        {
          lectern0_copy_cstr(placeholder, ARRAY_COUNT(placeholder), "Image unavailable");
        }
        (void)draw_push_text_in_rect(&app->draw_commands,
                                     DrawLayer_World,
                                     app->render_state.text_provider,
                                     placeholder,
                                     image_x,
                                     image_y,
                                     image_w,
                                     image_h,
                                     12,
                                     16,
                                     DrawTextHAlign_Center,
                                     DrawTextVAlign_Center,
                                     0x00666058U);
      }
      continue;
    }

    if (end > start)
    {
      String8 text = str8(app->frame.visible_text.str + start, end - start);
      U32 color = row->has_text_color ? row->text_color_rgb : 0x002A2927U;
      TextEngineResolvedStyle style =
        epub_reader_typography_style_for_doc_style(&app->reader.typography,
                                                    metrics.scale_px,
                                                    color,
                                                    row->font_family_hint,
                                                    row->font_face_index,
                                                    row->block_style_flags,
                                                    FontRasterFlag_Smooth);
      S32 baseline = y + MAX(style.baseline_offset_px, metrics.scale_px);
      if (!lectern0_push_reader_text_chunks(app,
                                            text,
                                            row,
                                            &style,
                                            x,
                                            baseline,
                                            metrics.scale_px,
                                            color,
                                            body_x,
                                            body_y,
                                            body_w,
                                            body_h))
      {
        app->presentation_complete = 0;
      }
    }
  }
  if (row_index < app->presentation_frame.row_count)
  {
    app->presentation_complete = 0;
  }

  char footer[160] = {0};
  (void)cstr_format(footer,
                    ARRAY_COUNT(footer),
                    "Page %llu/%llu | Section %u/%u",
                    (unsigned long long)app->frame.page_index,
                    (unsigned long long)app->frame.page_count,
                    (unsigned)(app->frame.spine_index + 1),
                    (unsigned)app->frame.section_count);
  (void)draw_push_text_in_rect(&app->draw_commands,
                               DrawLayer_Overlay,
                               app->render_state.text_provider,
                               footer,
                               32,
                               app->height - Lectern0FooterHeight,
                               MAX(app->width - 64, 1),
                               Lectern0FooterHeight,
                               8,
                               14,
                               DrawTextHAlign_Center,
                               DrawTextVAlign_Center,
                               0x00635E56U);
}

FUNCTION void
lectern0_reset_input(Lectern0App *app)
{
  if (!app) { return; }
  app->input.pointer_pressed = 0;
  app->input.pointer_released = 0;
  app->input.activate_pressed = 0;
  app->input.focus_next_pressed = 0;
  app->input.focus_prev_pressed = 0;
}

FUNCTION void
lectern0_render_to_buffer(Lectern0App *app, RenderBuffer *buffer)
{
  if (!app || !buffer || !buffer->pixels || !app->render_ready) { return; }
  app->presentation_complete = 1;
  render_buffer_clear(buffer, 0x00E9E5DEU);
  draw_command_buffer_begin(&app->draw_commands);
  lectern0_build_toolbar(app);
  lectern0_draw_reader_page(app);
  render_execute_draw_commands(&app->render_state, buffer, &app->draw_commands);
  lectern0_reset_input(app);
}

FUNCTION void
lectern0_render(Lectern0App *app)
{
  if (!app || !app->gfx_ready || !app->render_ready) { return; }
  OS_GfxSurface surface = {0};
  if (!os_gfx_acquire_surface(&app->gfx, &surface)) { return; }
  app->width = surface.width;
  app->height = surface.height;
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer,
                     surface.pixels,
                     surface.width,
                     surface.height,
                     surface.stride_pixels);
  lectern0_render_to_buffer(app, &buffer);
  (void)os_gfx_present_surface(&app->gfx, &surface);
}

FUNCTION B32
lectern0_write_bmp(const char *path, const U32 *pixels, S32 width, S32 height)
{
  if (!path || !path[0] || !pixels || width <= 0 || height <= 0) { return 0; }
  FILE *file = fopen(path, "wb");
  if (!file) { return 0; }

  U64 pixel_bytes_u64 = (U64)(U32)width * (U64)(U32)height * sizeof(U32);
  if (pixel_bytes_u64 > UINT32_MAX)
  {
    fclose(file);
    return 0;
  }
  U32 pixel_bytes = (U32)pixel_bytes_u64;
  BITMAPFILEHEADER file_header = {0};
  BITMAPINFOHEADER info_header = {0};
  file_header.bfType = 0x4D42;
  file_header.bfOffBits = sizeof(file_header) + sizeof(info_header);
  file_header.bfSize = file_header.bfOffBits + pixel_bytes;
  info_header.biSize = sizeof(info_header);
  info_header.biWidth = width;
  info_header.biHeight = -height;
  info_header.biPlanes = 1;
  info_header.biBitCount = 32;
  info_header.biCompression = BI_RGB;
  info_header.biSizeImage = pixel_bytes;

  B32 result =
    fwrite(&file_header, sizeof(file_header), 1, file) == 1 &&
    fwrite(&info_header, sizeof(info_header), 1, file) == 1 &&
    fwrite(pixels, pixel_bytes, 1, file) == 1;
  fclose(file);
  return result;
}

FUNCTION LRESULT CALLBACK
lectern0_win32_proc(HWND window, UINT message, WPARAM w_param, LPARAM l_param)
{
  Lectern0Win32 *win32 = (Lectern0Win32 *)GetWindowLongPtrW(window, GWLP_USERDATA);
  Lectern0App *app = win32 ? &win32->app : 0;
  switch (message)
  {
    case WM_NCCREATE:
    {
      CREATESTRUCTW *create = (CREATESTRUCTW *)l_param;
      SetWindowLongPtrW(window, GWLP_USERDATA, (LONG_PTR)create->lpCreateParams);
      return TRUE;
    } break;

    case WM_SIZE:
    {
      if (app)
      {
        app->width = (S32)LOWORD(l_param);
        app->height = (S32)HIWORD(l_param);
        if (app->gfx_ready && app->width > 0 && app->height > 0)
        {
          (void)os_gfx_resize(&app->gfx, app->width, app->height);
          if (epub_reader_is_open(&app->reader)) { (void)lectern0_repaginate(app); }
          InvalidateRect(window, 0, FALSE);
        }
      }
      return 0;
    } break;

    case WM_MOUSEMOVE:
    {
      if (app)
      {
        app->input.pointer_x = GET_X_LPARAM(l_param);
        app->input.pointer_y = GET_Y_LPARAM(l_param);
        InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_LBUTTONDOWN:
    {
      if (app)
      {
        SetCapture(window);
        app->input.pointer_x = GET_X_LPARAM(l_param);
        app->input.pointer_y = GET_Y_LPARAM(l_param);
        app->input.pointer_down = 1;
        app->input.pointer_pressed = 1;
        InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_LBUTTONUP:
    {
      if (app)
      {
        ReleaseCapture();
        app->input.pointer_x = GET_X_LPARAM(l_param);
        app->input.pointer_y = GET_Y_LPARAM(l_param);
        app->input.pointer_down = 0;
        app->input.pointer_released = 1;
        InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_KEYDOWN:
    {
      if (app)
      {
        B32 control = (GetKeyState(VK_CONTROL) & 0x8000) != 0;
        B32 shift = (GetKeyState(VK_SHIFT) & 0x8000) != 0;
        if (w_param == 'O' && control)
        {
          (void)lectern0_pick_epub(app);
        }
        else if (w_param == VK_LEFT || w_param == VK_PRIOR)
        {
          (void)lectern0_move_page(app, -1);
        }
        else if (w_param == VK_RIGHT || w_param == VK_NEXT || w_param == VK_SPACE)
        {
          (void)lectern0_move_page(app, 1);
        }
        else if (w_param == VK_TAB)
        {
          if (shift) { app->input.focus_prev_pressed = 1; }
          else { app->input.focus_next_pressed = 1; }
        }
        else if (w_param == VK_RETURN)
        {
          app->input.activate_pressed = 1;
        }
        InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_PAINT:
    {
      PAINTSTRUCT paint = {0};
      BeginPaint(window, &paint);
      if (app) { lectern0_render(app); }
      EndPaint(window, &paint);
      if (app && app->pending_action != Lectern0Action_None)
      {
        lectern0_apply_pending_action(app);
        InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_DESTROY:
    {
      PostQuitMessage(0);
      return 0;
    } break;
  }
  return DefWindowProcW(window, message, w_param, l_param);
}

FUNCTION int
lectern0_run_headless(const char *path)
{
  Lectern0App app = {0};
  if (!lectern0_app_init(&app, 1000, 720, 0, 0) || !lectern0_open_path(&app, path))
  {
    fprintf(stderr, "lectern0_host_smoke result=fail reason=open\n");
    lectern0_app_release(&app);
    return 1;
  }

  U32 nav_count = 0;
  EpubReaderNavPointResult nav_navigation = {0};
  if (doc_engine_get_nav_point_count(epub_reader_engine(&app.reader),
                                     epub_reader_document_id(&app.reader),
                                     &nav_count) != DocError_Ok ||
      nav_count < 2 ||
      lectern0_navigate_to_nav_point(&app,
                                     1,
                                     &nav_navigation) != EpubReaderResult_Ok ||
      !nav_navigation.had_fragment ||
      !nav_navigation.fragment_resolved ||
      nav_navigation.fragment_fallback ||
      app.reader.active_spine_index != 1)
  {
    fprintf(stderr, "lectern0_host_smoke result=fail reason=contents_navigation\n");
    lectern0_app_release(&app);
    return 1;
  }

  if (!epub_reader_rebuild_search(&app.reader,
                                  str8_from_cstr("standalone host proof")) ||
      app.reader.search_match_count == 0)
  {
    fprintf(stderr, "lectern0_host_smoke result=fail reason=find_query\n");
    lectern0_app_release(&app);
    return 1;
  }
  EpubReaderSearchNavigationResult search_navigation = {0};
  if (lectern0_navigate_to_search_match(&app,
                                        0,
                                        &search_navigation) != EpubReaderResult_Ok ||
      search_navigation.match.spine_index != 0 ||
      app.reader.active_spine_index != 0 ||
      app.reader.back_stack_count < 2)
  {
    fprintf(stderr, "lectern0_host_smoke result=fail reason=find_navigation\n");
    lectern0_app_release(&app);
    return 1;
  }

  U32 start_spine = app.reader.active_spine_index;
  SourceReaderPageRange cross_page = {0};
  B32 crossed = 0;
  for (U32 attempt = 0; attempt < 256 && !crossed; attempt += 1)
  {
    if (lectern0_move_page(&app, 1) != EpubReaderResult_Ok)
    {
      fprintf(stderr, "lectern0_host_smoke result=fail reason=forward\n");
      lectern0_app_release(&app);
      return 1;
    }
    crossed = app.reader.active_spine_index != start_spine;
    if (crossed) { cross_page = app.reader.current_page; }
  }
  U32 frame_gap_start = 0;
  U32 frame_gap_end = 0;
  if (crossed &&
      !lectern0_frame_text_rows_are_complete(&app.frame,
                                             &frame_gap_start,
                                             &frame_gap_end))
  {
    fprintf(stderr,
            "lectern0_host_smoke result=fail reason=frame_text_gap gap=%u..%u text=%llu rows=%u\n",
            frame_gap_start,
            frame_gap_end,
            (unsigned long long)app.frame.visible_text.size,
            app.frame.style_row_count);
    lectern0_app_release(&app);
    return 1;
  }

  if (!crossed || lectern0_move_page(&app, -1) != EpubReaderResult_Ok ||
      app.reader.active_spine_index != start_spine ||
      lectern0_move_page(&app, 1) != EpubReaderResult_Ok ||
      app.reader.current_page.spine_index != cross_page.spine_index ||
      app.reader.current_page.first_byte != cross_page.first_byte ||
      !lectern0_capture_frame(&app))
  {
    fprintf(stderr, "lectern0_host_smoke result=fail reason=cross_spine\n");
    lectern0_app_release(&app);
    return 1;
  }

  U32 resize_spine = app.reader.active_spine_index;
  U64 resize_byte = app.reader.view_byte_offset;
  app.width = 760;
  app.height = 560;
  if (!lectern0_repaginate(&app) ||
      app.reader.active_spine_index != resize_spine ||
      app.reader.current_page.first_byte > resize_byte ||
      app.reader.current_page.one_past_last_byte <= resize_byte ||
      !app.frame.ready || !app.frame.document_open)
  {
    fprintf(stderr, "lectern0_host_smoke result=fail reason=resize\n");
    lectern0_app_release(&app);
    return 1;
  }

  U64 hash = u64_hash_str8(app.frame.visible_text);
  fprintf(stdout,
          "lectern0_host_smoke result=pass spine=%u page=%llu/%llu text=%llu toc=1 find=1 hash=%016llx\n",
          app.frame.spine_index,
          (unsigned long long)app.frame.page_index,
          (unsigned long long)app.frame.page_count,
          (unsigned long long)app.frame.visible_text.size,
          (unsigned long long)hash);
  lectern0_app_release(&app);
  return 0;
}

FUNCTION int
lectern0_run_render_smoke(const char *path, const char *bmp_path)
{
  enum { RenderWidth = 1100, RenderHeight = 760 };
  Lectern0App app = {0};
  if (!lectern0_app_init(&app, RenderWidth, RenderHeight, 1, 0) ||
      !lectern0_open_path(&app, path))
  {
    fprintf(stderr, "lectern0_visual_smoke result=fail reason=open\n");
    lectern0_app_release(&app);
    return 1;
  }

  U32 start_spine = app.reader.active_spine_index;
  B32 crossed = 0;
  for (U32 attempt = 0; attempt < 256 && !crossed; attempt += 1)
  {
    EpubReaderResult move = lectern0_move_page(&app, 1);
    if (move != EpubReaderResult_Ok)
    {
      fprintf(stderr, "lectern0_visual_smoke result=fail reason=forward\n");
      lectern0_app_release(&app);
      return 1;
    }
    crossed = app.reader.active_spine_index != start_spine;
  }

  U32 gap_start = 0;
  U32 gap_end = 0;
  if (!crossed ||
      !lectern0_frame_text_rows_are_complete(&app.frame, &gap_start, &gap_end))
  {
    fprintf(stderr,
            "lectern0_visual_smoke result=fail reason=frame gap=%u..%u\n",
            gap_start,
            gap_end);
    lectern0_app_release(&app);
    return 1;
  }

  U64 pixel_count = (U64)RenderWidth * (U64)RenderHeight;
  U32 *pixels = (U32 *)calloc((size_t)pixel_count, sizeof(U32));
  if (!pixels)
  {
    fprintf(stderr, "lectern0_visual_smoke result=fail reason=memory\n");
    lectern0_app_release(&app);
    return 1;
  }
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, RenderWidth, RenderHeight, RenderWidth);
  lectern0_render_to_buffer(&app, &buffer);
  if (!app.presentation_complete)
  {
    fprintf(stderr, "lectern0_visual_smoke result=fail reason=presentation\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  U64 pixel_hash = u64_hash_bytes(pixels, pixel_count * sizeof(U32));
  B32 wrote = lectern0_write_bmp(bmp_path, pixels, RenderWidth, RenderHeight);
  free(pixels);
  if (!wrote)
  {
    fprintf(stderr, "lectern0_visual_smoke result=fail reason=write\n");
    lectern0_app_release(&app);
    return 1;
  }

  fprintf(stdout,
          "lectern0_visual_smoke result=pass spine=%u page=%llu/%llu rows=%u pixels=%dx%d hash=%016llx presentation=%016llx bmp=%s\n",
          app.frame.spine_index,
          (unsigned long long)app.frame.page_index,
          (unsigned long long)app.frame.page_count,
          app.frame.style_row_count,
          RenderWidth,
          RenderHeight,
          (unsigned long long)pixel_hash,
          (unsigned long long)app.presentation_hash,
          bmp_path);
  lectern0_app_release(&app);
  return 0;
}

FUNCTION U32
lectern0_loaded_image_count(const EpubReaderFrame *frame)
{
  if (!frame) { return 0; }
  U32 result = 0;
  for (U32 index = 0; index < frame->image_count; index += 1)
  {
    const EpubReaderFrameImage *image = frame->images + index;
    if (image->status == EpubReaderFrameImageStatus_Loaded &&
        image->pixels && image->src_w > 0 && image->src_h > 0)
    {
      result += 1;
    }
  }
  return result;
}

FUNCTION B32
lectern0_write_frame_evidence(Lectern0App *app,
                              const char *bmp_path,
                              U64 *out_hash)
{
  enum { RenderWidth = 1100, RenderHeight = 760 };
  if (out_hash) { *out_hash = 0; }
  if (!app || !bmp_path) { return 0; }
  U64 pixel_count = (U64)RenderWidth * (U64)RenderHeight;
  U32 *pixels = (U32 *)calloc((size_t)pixel_count, sizeof(U32));
  if (!pixels) { return 0; }
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, RenderWidth, RenderHeight, RenderWidth);
  lectern0_render_to_buffer(app, &buffer);
  B32 result = app->presentation_complete &&
    lectern0_write_bmp(bmp_path, pixels, RenderWidth, RenderHeight);
  if (result && out_hash)
  {
    *out_hash = u64_hash_bytes(pixels, pixel_count * sizeof(U32));
  }
  free(pixels);
  return result;
}

FUNCTION int
lectern0_run_image_smoke(const char *path,
                         const char *cover_bmp_path,
                         const char *inline_bmp_path)
{
  enum { RenderWidth = 1100, RenderHeight = 760 };
  Lectern0App app = {0};
  if (!lectern0_app_init(&app, RenderWidth, RenderHeight, 1, 0) ||
      !lectern0_open_path(&app, path))
  {
    fprintf(stderr, "lectern0_image_smoke result=fail reason=open\n");
    lectern0_app_release(&app);
    return 1;
  }

  U32 cover_loaded = lectern0_loaded_image_count(&app.frame);
  U64 cover_hash = 0;
  if (app.frame.image_count == 0 || cover_loaded == 0 ||
      !lectern0_capture_frame(&app) ||
      lectern0_loaded_image_count(&app.frame) == 0 ||
      !lectern0_write_frame_evidence(&app, cover_bmp_path, &cover_hash))
  {
    fprintf(stderr,
            "lectern0_image_smoke result=fail reason=cover images=%u loaded=%u\n",
            app.frame.image_count,
            cover_loaded);
    lectern0_app_release(&app);
    return 1;
  }

  U32 cover_spine = app.reader.active_spine_index;
  B32 crossed = 0;
  for (U32 attempt = 0; attempt < 256 && !crossed; attempt += 1)
  {
    if (lectern0_move_page(&app, 1) != EpubReaderResult_Ok)
    {
      break;
    }
    crossed = app.reader.active_spine_index != cover_spine;
  }

  U32 inline_loaded = lectern0_loaded_image_count(&app.frame);
  U64 inline_hash = 0;
  if (!crossed || app.frame.image_count == 0 || inline_loaded == 0 ||
      !lectern0_capture_frame(&app) ||
      lectern0_loaded_image_count(&app.frame) == 0 ||
      !lectern0_write_frame_evidence(&app, inline_bmp_path, &inline_hash))
  {
    fprintf(stderr,
            "lectern0_image_smoke result=fail reason=inline crossed=%d images=%u loaded=%u\n",
            crossed,
            app.frame.image_count,
            inline_loaded);
    lectern0_app_release(&app);
    return 1;
  }

  fprintf(stdout,
          "lectern0_image_smoke result=pass cover_loaded=%u inline_loaded=%u entries=%u lookups=%llu hits=%llu misses=%llu cover_hash=%016llx inline_hash=%016llx\n",
          cover_loaded,
          inline_loaded,
          app.image_cache.entry_count,
          (unsigned long long)app.image_cache.lookup_count,
          (unsigned long long)app.image_cache.hit_count,
          (unsigned long long)app.image_cache.miss_count,
          (unsigned long long)cover_hash,
          (unsigned long long)inline_hash);
  lectern0_app_release(&app);
  return 0;
}

FUNCTION int
lectern0_run_window(const char *initial_path)
{
  Lectern0Win32 win32 = {0};
  if (!lectern0_app_init(&win32.app, 1100, 760, 1, 1)) { return 1; }

  (void)SetProcessDPIAware();
  HINSTANCE instance = GetModuleHandleW(0);
  WNDCLASSW window_class = {0};
  window_class.lpfnWndProc = lectern0_win32_proc;
  window_class.hInstance = instance;
  window_class.lpszClassName = L"Lectern0Window";
  window_class.hCursor = LoadCursorW(0, IDC_ARROW);
  window_class.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
  if (!RegisterClassW(&window_class))
  {
    lectern0_app_release(&win32.app);
    return 1;
  }

  RECT rect = {0, 0, win32.app.width, win32.app.height};
  AdjustWindowRect(&rect, WS_OVERLAPPEDWINDOW, FALSE);
  win32.window = CreateWindowExW(0,
                                 window_class.lpszClassName,
                                 L"lectern0 - EPUB reader",
                                 WS_OVERLAPPEDWINDOW | WS_VISIBLE,
                                 CW_USEDEFAULT,
                                 CW_USEDEFAULT,
                                 rect.right - rect.left,
                                 rect.bottom - rect.top,
                                 0,
                                 0,
                                 instance,
                                 &win32);
  if (!win32.window)
  {
    lectern0_app_release(&win32.app);
    return 1;
  }
  win32.app.window = win32.window;

  OS_GfxInitParams gfx_params = {0};
  gfx_params.backend = OS_GfxBackendKind_Win32DIB;
  gfx_params.native[0] = (U64)(uintptr_t)win32.window;
  gfx_params.width = win32.app.width;
  gfx_params.height = win32.app.height;
  if (!os_gfx_init(&win32.app.gfx, &gfx_params))
  {
    DestroyWindow(win32.window);
    lectern0_app_release(&win32.app);
    return 1;
  }
  win32.app.gfx_ready = 1;
  if (initial_path && initial_path[0]) { (void)lectern0_open_path(&win32.app, initial_path); }
  InvalidateRect(win32.window, 0, FALSE);

  MSG message = {0};
  while (GetMessageW(&message, 0, 0, 0) > 0)
  {
    TranslateMessage(&message);
    DispatchMessageW(&message);
  }
  lectern0_app_release(&win32.app);
  return 0;
}

int
main(int argc, char **argv)
{
  os_init();
  os_time_init();
  tctx_init_and_set();
  HRESULT com_result = CoInitializeEx(0, COINIT_APARTMENTTHREADED);
  B32 release_com = SUCCEEDED(com_result);

  int result = 0;
  if (argc == 3 && strcmp(argv[1], "--headless") == 0)
  {
    result = lectern0_run_headless(argv[2]);
  }
  else if (argc == 4 && strcmp(argv[1], "--render-smoke") == 0)
  {
    result = lectern0_run_render_smoke(argv[2], argv[3]);
  }
  else if (argc == 5 && strcmp(argv[1], "--image-smoke") == 0)
  {
    result = lectern0_run_image_smoke(argv[2], argv[3], argv[4]);
  }
  else if (argc == 2 && strcmp(argv[1], "--version") == 0)
  {
    fprintf(stdout,
            "lectern0 %s reader0_api=%d ui0_api=%d\n",
            LECTERN0_VERSION_STRING,
            READER0_API_VERSION,
            UI0_API_VERSION);
  }
  else if (argc <= 2)
  {
    result = lectern0_run_window(argc == 2 ? argv[1] : 0);
  }
  else
  {
    fprintf(stderr,
            "usage: lectern0.exe [epub-path | --headless epub-path | --render-smoke epub-path bmp-path | --image-smoke epub-path cover-bmp inline-bmp | --version]\n");
    result = 2;
  }

  if (release_com) { CoUninitialize(); }
  return result;
}

#include "lectern0_version.h"
#include "reader0.h"
#include "ui0.h"
#include "readerview0.h"

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
#if READERVIEW0_API_VERSION != 1
#  error "lectern0 requires Reader View API 1"
#endif

#include <commdlg.h>
#include <objbase.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <windows.h>
#include <windowsx.h>
#include <shellapi.h>
#include <oleacc.h>

#include "platform/win32/lectern0_accessibility_win32.h"

enum
{
  Lectern0PathCap = 1024,
  Lectern0StatusCap = 256,
  Lectern0StateFileCap = 2304,
  Lectern0ToolbarHeight = 48,
  Lectern0FooterHeight = 36,
  Lectern0ImageCacheCap = 64,
  Lectern0BookmarkCap = 128,
  Lectern0HighlightCap = 128,
  Lectern0RecordLabelCap = 160,
  Lectern0NoteCap = READER_VIEW_NOTE_DRAFT_CAP,
  Lectern0SelectionTextCap = 1024,
  Lectern0InputTextCap = 128,
  Lectern0ClipboardCap = 2048,
  Lectern0UrlCap = 4096,
  Lectern0PresentationRowCap = EPUB_READER_FRAME_STYLE_ROW_CAP,
  Lectern0PresentationMediaCap = EPUB_READER_FRAME_IMAGE_CAP,
};

#define LECTERN0_SETTINGS_MAGIC 0x4C30534554543231ull
#define LECTERN0_ANNOTATION_MAGIC 0x4C30414E4E4F5431ull

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

typedef enum Lectern0Theme
{
  Lectern0Theme_Light,
  Lectern0Theme_Sepia,
  Lectern0Theme_Dark,
  Lectern0Theme_Count,
} Lectern0Theme;

typedef struct Lectern0Bookmark
{
  U64 id;
  U32 spine_index;
  U64 byte_offset;
  B32 starred;
  char label[Lectern0RecordLabelCap];
} Lectern0Bookmark;

typedef struct Lectern0Highlight
{
  U64 id;
  U32 spine_index;
  U64 start_byte;
  U64 end_byte;
  U32 color_index;
  B32 starred;
  B32 note_starred;
  char section[Lectern0RecordLabelCap];
  char text[Lectern0RecordLabelCap];
  char note[Lectern0NoteCap];
} Lectern0Highlight;

typedef struct Lectern0SettingsFile
{
  U64 magic;
  U32 version;
  U32 font_family;
  U32 text_size_index;
  U32 line_spacing_index;
  U32 theme;
} Lectern0SettingsFile;

typedef struct Lectern0AnnotationFile
{
  U64 magic;
  U32 version;
  U32 bookmark_count;
  U32 highlight_count;
  U32 reserved;
  U64 path_hash;
  U64 next_record_id;
  Lectern0Bookmark bookmarks[Lectern0BookmarkCap];
  Lectern0Highlight highlights[Lectern0HighlightCap];
} Lectern0AnnotationFile;

typedef struct Lectern0Fullscreen
{
  B32 active;
  DWORD style;
  DWORD ex_style;
  WINDOWPLACEMENT placement;
} Lectern0Fullscreen;

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
  S32 wheel_delta_y;
  B32 escape_pressed;
  B32 backspace_pressed;
  B32 delete_pressed;
  B32 commit_pressed;
  B32 select_all_pressed;
  B32 copy_pressed;
  B32 cut_pressed;
  B32 paste_pressed;
  B32 undo_pressed;
  B32 redo_pressed;
  S32 move_delta;
  S32 move_vertical_delta;
  ReaderViewRangeMove range_move;
  B32 extend_selection;
  char text[Lectern0InputTextCap];
  S32 text_length;
} Lectern0Input;

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
  Lectern0Accessibility *accessibility;
  S32 width;
  S32 height;

  ReaderViewState reader_view_state;
  ReaderViewFrameStorage reader_view_storage;
  ReaderViewLayout reader_view_layout;
  ReaderViewFrame reader_view_frame;
  ReaderViewProjection reader_view_projection;
  ReaderViewSettingControl reader_view_settings[READER_VIEW_SETTING_CAP];
  ReaderViewChoice reader_view_font_choices[8];
  ReaderViewChoice reader_view_size_choices[4];
  ReaderViewChoice reader_view_spacing_choices[3];
  ReaderViewChoice reader_view_theme_choices[Lectern0Theme_Count];
  ReaderViewChoice reader_view_color_choices[READER_VIEW_HIGHLIGHT_COLOR_CAP];
  ReaderViewTocRow reader_view_toc_rows[READER_VIEW_TOC_ROW_CAP];
  ReaderViewFindRow reader_view_find_rows[READER_VIEW_FIND_ROW_CAP];
  ReaderViewRightRow reader_view_right_rows[READER_VIEW_RIGHT_ROW_CAP];
  char document_title[Lectern0RecordLabelCap];
  char progress_label[64];
  char selected_text[Lectern0SelectionTextCap];
  ReaderViewLoadState document_state;
  UI0ResolvedTheme reader_view_theme;
  U64 reader_view_frame_index;
  B32 reader_view_ready;
  S32 pagination_viewport_width;
  S32 pagination_viewport_height;

  Lectern0Bookmark bookmarks[Lectern0BookmarkCap];
  U32 bookmark_count;
  Lectern0Highlight highlights[Lectern0HighlightCap];
  U32 highlight_count;
  U64 next_record_id;
  U64 annotation_revision;
  U32 font_family;
  U32 text_size_index;
  U32 line_spacing_index;
  Lectern0Theme theme;
  B32 distraction_free;
  Lectern0Fullscreen fullscreen;
  B32 selection_dragging;
  U64 selection_anchor_byte;
  UI0Rect selection_anchor_rect;
  char clipboard_text[Lectern0ClipboardCap];
  S32 clipboard_length;
  UI0TextInputTransferBuffer clipboard_transfer;
  wchar_t pending_high_surrogate;
  Lectern0Input input;

  B32 persistence_enabled;
  Lectern0SavedState saved;
  char app_directory[Lectern0PathCap];
  char state_path[Lectern0PathCap];
  char settings_path[Lectern0PathCap];
  char annotations_path[Lectern0PathCap];
  char export_path[Lectern0PathCap];
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
  (void)cstr_format(app->settings_path,
                    ARRAY_COUNT(app->settings_path),
                    "%s\\settings.v1",
                    app->app_directory);
  (void)cstr_format(app->export_path,
                    ARRAY_COUNT(app->export_path),
                    "%s\\reader_annotations.txt",
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

FUNCTION void
lectern0_load_settings(Lectern0App *app)
{
  if (!app || !app->persistence_enabled || !app->settings_path[0]) { return; }
  Lectern0SettingsFile file = {0};
  U64 size = 0;
  if (!os_read_entire_file(app->settings_path, &file, sizeof(file), &size) ||
      size != sizeof(file) || file.magic != LECTERN0_SETTINGS_MAGIC ||
      file.version != 1)
  {
    return;
  }
  if (file.font_family < FontProviderBookContentFamily_InternalCount)
    app->font_family = file.font_family;
  if (file.text_size_index < 4) app->text_size_index = file.text_size_index;
  if (file.line_spacing_index < 3) app->line_spacing_index = file.line_spacing_index;
  if (file.theme < Lectern0Theme_Count) app->theme = (Lectern0Theme)file.theme;
}

FUNCTION B32
lectern0_save_settings(Lectern0App *app)
{
  if (!app || !app->persistence_enabled || !app->settings_path[0]) { return 0; }
  Lectern0SettingsFile file = {
    .magic = LECTERN0_SETTINGS_MAGIC,
    .version = 1,
    .font_family = app->font_family,
    .text_size_index = app->text_size_index,
    .line_spacing_index = app->line_spacing_index,
    .theme = (U32)app->theme,
  };
  return os_write_entire_file_atomic(app->settings_path, &file, sizeof(file));
}

FUNCTION void
lectern0_set_annotations_path(Lectern0App *app, const char *path)
{
  if (!app) { return; }
  app->annotations_path[0] = 0;
  if (!path || !path[0] || !app->app_directory[0]) { return; }
  U64 path_hash = u64_hash_str8(str8_from_cstr(path));
  (void)cstr_format(app->annotations_path,
                    ARRAY_COUNT(app->annotations_path),
                    "%s\\annotations_%016llx.v1",
                    app->app_directory,
                    (unsigned long long)path_hash);
}

FUNCTION void
lectern0_clear_annotations(Lectern0App *app)
{
  if (!app) { return; }
  MemoryZeroArray(app->bookmarks);
  MemoryZeroArray(app->highlights);
  app->bookmark_count = 0;
  app->highlight_count = 0;
  app->next_record_id = 1;
  app->annotation_revision += 1;
}

FUNCTION void
lectern0_load_annotations(Lectern0App *app)
{
  if (!app) { return; }
  lectern0_clear_annotations(app);
  if (!app->persistence_enabled || !app->annotations_path[0] ||
      !app->current_path[0])
  {
    return;
  }
  Lectern0AnnotationFile file = {0};
  U64 size = 0;
  U64 expected_hash = u64_hash_str8(str8_from_cstr(app->current_path));
  if (!os_read_entire_file(app->annotations_path, &file, sizeof(file), &size) ||
      size != sizeof(file) || file.magic != LECTERN0_ANNOTATION_MAGIC ||
      file.version != 1 || file.path_hash != expected_hash ||
      file.bookmark_count > Lectern0BookmarkCap ||
      file.highlight_count > Lectern0HighlightCap)
  {
    return;
  }
  app->bookmark_count = file.bookmark_count;
  app->highlight_count = file.highlight_count;
  app->next_record_id = MAX(file.next_record_id, 1ull);
  MemoryCopy(app->bookmarks, file.bookmarks,
             sizeof(file.bookmarks[0]) * file.bookmark_count);
  MemoryCopy(app->highlights, file.highlights,
             sizeof(file.highlights[0]) * file.highlight_count);
  app->annotation_revision += 1;
}

FUNCTION B32
lectern0_save_annotations(Lectern0App *app)
{
  if (!app || !app->persistence_enabled || !app->annotations_path[0] ||
      !app->current_path[0])
  {
    return 0;
  }
  Lectern0AnnotationFile file = {0};
  file.magic = LECTERN0_ANNOTATION_MAGIC;
  file.version = 1;
  file.bookmark_count = app->bookmark_count;
  file.highlight_count = app->highlight_count;
  file.path_hash = u64_hash_str8(str8_from_cstr(app->current_path));
  file.next_record_id = app->next_record_id;
  MemoryCopy(file.bookmarks, app->bookmarks,
             sizeof(file.bookmarks[0]) * app->bookmark_count);
  MemoryCopy(file.highlights, app->highlights,
             sizeof(file.highlights[0]) * app->highlight_count);
  return os_write_entire_file_atomic(app->annotations_path, &file, sizeof(file));
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

  static const S32 text_scales[] = {18, 20, 21, 22};
  static const S32 char_advances[] = {10, 10, 11, 11};
  static const S32 line_heights[] = {24, 26, 28, 30};
  static const S32 line_spacing_extra[] = {0, 5, 10};
  UI0Rect viewport = app->reader_view_ready ? app->reader_view_layout.viewport_rect :
    ui0_rect(0, Lectern0ToolbarHeight, app->width,
             MAX(app->height - Lectern0ToolbarHeight - Lectern0FooterHeight, 1));
  U32 size_index = app->text_size_index % ARRAY_COUNT(text_scales);
  U32 spacing_index = app->line_spacing_index % ARRAY_COUNT(line_spacing_extra);
  S32 content_width = MAX(viewport.w - 112, 240);
  S32 content_height = MAX(viewport.h - Lectern0FooterHeight - 20, 120);
  S32 text_scale = text_scales[size_index];
  S32 char_advance = char_advances[size_index];
  S32 line_height = line_heights[size_index] + line_spacing_extra[spacing_index];
  String8 uri = epub_reader_canonical_uri(&app->reader);

  (void)epub_reader_typography_set_view(&app->reader.typography,
                                        text_scale,
                                        app->font_family,
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
    .font_family_index = app->font_family,
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
  app->pagination_viewport_width = viewport.w;
  app->pagination_viewport_height = viewport.h;
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
  app->document_state = ReaderViewLoad_Loading;
  lectern0_set_statusf(app, "Opening EPUB...");
  if (app->current_path[0]) { (void)lectern0_save_annotations(app); }
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
    app->document_state = ReaderViewLoad_Error;
    return 0;
  }

  lectern0_copy_cstr(app->current_path, ARRAY_COUNT(app->current_path), path);
  lectern0_set_annotations_path(app, path);
  lectern0_load_annotations(app);
  reader_view_state_reset_document(&app->reader_view_state,
                                   (UI0U64)epub_reader_document_id(&app->reader));
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
      app->document_state = ReaderViewLoad_Error;
      return 0;
    }
  }
  lectern0_image_cache_reset(&app->image_cache);
  if (!lectern0_capture_frame(app))
  {
    lectern0_set_statusf(app, "Open failed: frame");
    app->document_state = ReaderViewLoad_Error;
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
  app->document_state = ReaderViewLoad_Ready;
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

FUNCTION ReaderViewText
lectern0_reader_view_text(const char *text)
{
  ReaderViewText result = {0};
  if (text)
  {
    result.data = text;
    result.size = (UI0S32)strlen(text);
  }
  return result;
}

FUNCTION ReaderViewText
lectern0_reader_view_bytes(const char *text, U64 size)
{
  ReaderViewText result = {0};
  if (text && size <= (U64)INT32_MAX)
  {
    result.data = text;
    result.size = (UI0S32)size;
  }
  return result;
}

FUNCTION void
lectern0_copy_bytes(char *dst, U64 cap, const U8 *src, U64 size)
{
  if (!dst || cap == 0) { return; }
  U64 copy_size = MIN(size, cap - 1);
  if (src && copy_size) { MemoryCopy(dst, src, copy_size); }
  dst[copy_size] = 0;
}

FUNCTION const char *
lectern0_current_section_label(const Lectern0App *app)
{
  if (!app) { return ""; }
  for (U32 index = 0; index < app->frame.section_item_count; index += 1)
  {
    if (app->frame.section_items[index].active)
      return app->frame.section_items[index].label;
  }
  return "";
}

FUNCTION S32
lectern0_bookmark_index(const Lectern0App *app, U64 id)
{
  if (!app || id == 0) { return -1; }
  for (U32 index = 0; index < app->bookmark_count; index += 1)
    if (app->bookmarks[index].id == id) return (S32)index;
  return -1;
}

FUNCTION S32
lectern0_current_bookmark_index(const Lectern0App *app)
{
  if (!app || !epub_reader_is_open(&app->reader)) { return -1; }
  for (U32 index = 0; index < app->bookmark_count; index += 1)
  {
    const Lectern0Bookmark *bookmark = app->bookmarks + index;
    if (bookmark->spine_index == app->reader.active_spine_index &&
        bookmark->byte_offset == app->reader.view_byte_offset)
      return (S32)index;
  }
  return -1;
}

FUNCTION S32
lectern0_highlight_index(const Lectern0App *app, U64 id)
{
  if (!app || id == 0) { return -1; }
  for (U32 index = 0; index < app->highlight_count; index += 1)
    if (app->highlights[index].id == id) return (S32)index;
  return -1;
}

FUNCTION S32
lectern0_selection_highlight_index(const Lectern0App *app)
{
  if (!app || !app->reader.has_active_selection) { return -1; }
  DocSelection selection = app->reader.active_selection;
  for (U32 index = 0; index < app->highlight_count; index += 1)
  {
    const Lectern0Highlight *highlight = app->highlights + index;
    if (highlight->spine_index == selection.spine_index &&
        highlight->start_byte == selection.text_byte_start &&
        highlight->end_byte == selection.text_byte_end)
      return (S32)index;
  }
  return -1;
}

FUNCTION void
lectern0_remove_bookmark_at(Lectern0App *app, U32 index)
{
  if (!app || index >= app->bookmark_count) { return; }
  for (U32 at = index + 1; at < app->bookmark_count; at += 1)
    app->bookmarks[at - 1] = app->bookmarks[at];
  app->bookmark_count -= 1;
  MemoryZeroStruct(app->bookmarks + app->bookmark_count);
  app->annotation_revision += 1;
  (void)lectern0_save_annotations(app);
}

FUNCTION void
lectern0_remove_highlight_at(Lectern0App *app, U32 index)
{
  if (!app || index >= app->highlight_count) { return; }
  for (U32 at = index + 1; at < app->highlight_count; at += 1)
    app->highlights[at - 1] = app->highlights[at];
  app->highlight_count -= 1;
  MemoryZeroStruct(app->highlights + app->highlight_count);
  app->annotation_revision += 1;
  (void)lectern0_save_annotations(app);
}

FUNCTION B32
lectern0_toggle_current_bookmark(Lectern0App *app)
{
  if (!app || !epub_reader_is_open(&app->reader)) { return 0; }
  S32 existing = lectern0_current_bookmark_index(app);
  if (existing >= 0)
  {
    lectern0_remove_bookmark_at(app, (U32)existing);
    lectern0_set_statusf(app, "Bookmark removed");
    return 1;
  }
  if (app->bookmark_count >= Lectern0BookmarkCap) return 0;
  Lectern0Bookmark *bookmark = app->bookmarks + app->bookmark_count++;
  MemoryZeroStruct(bookmark);
  bookmark->id = app->next_record_id++;
  bookmark->spine_index = app->reader.active_spine_index;
  bookmark->byte_offset = app->reader.view_byte_offset;
  const char *section = lectern0_current_section_label(app);
  if (section && section[0])
    lectern0_copy_cstr(bookmark->label, ARRAY_COUNT(bookmark->label), section);
  else
    (void)cstr_format(bookmark->label, ARRAY_COUNT(bookmark->label),
                      "Page %llu", (unsigned long long)app->frame.page_index);
  app->annotation_revision += 1;
  (void)lectern0_save_annotations(app);
  lectern0_set_statusf(app, "Bookmark added");
  return 1;
}

FUNCTION EpubReaderResult
lectern0_navigate_to_location(Lectern0App *app,
                              U32 spine_index,
                              U64 byte_offset,
                              EpubReaderNavigationReason reason)
{
  if (!app || !epub_reader_is_open(&app->reader)) return EpubReaderResult_NotOpen;
  if (!lectern0_update_layout_inputs(app)) return EpubReaderResult_DocError;
  EpubReaderNavigationResult navigation = {0};
  EpubReaderResult result = epub_reader_navigate_to_location(
    &app->reader, spine_index, byte_offset, reason,
    app->layout_key, app->layout_config,
    (EpubReaderNavigationOptions){0}, &navigation);
  return lectern0_finish_semantic_navigation(app, result, "Navigate");
}

FUNCTION EpubReaderResult
lectern0_move_history(Lectern0App *app, B32 forward)
{
  if (!app || !epub_reader_is_open(&app->reader)) return EpubReaderResult_NotOpen;
  if (!lectern0_update_layout_inputs(app)) return EpubReaderResult_DocError;
  EpubReaderNavigationEntry current = {0};
  EpubReaderNavigationEntry target = {0};
  if (!epub_reader_history_begin(&app->reader, forward, &current, &target))
    return EpubReaderResult_Boundary;
  EpubReaderNavigationReason reason = target.reason;
  if (reason == EpubReaderNavigationReason_None ||
      reason == EpubReaderNavigationReason_Page)
    reason = EpubReaderNavigationReason_Location;
  EpubReaderNavigationResult navigation = {0};
  EpubReaderResult result = epub_reader_navigate_to_location(
    &app->reader, target.spine_index, target.byte_offset, reason,
    app->layout_key, app->layout_config,
    (EpubReaderNavigationOptions){.suppress_history = 1}, &navigation);
  epub_reader_history_finish(&app->reader, forward, current, target,
                             result == EpubReaderResult_Ok);
  return lectern0_finish_semantic_navigation(app, result,
                                             forward ? "Forward" : "Back");
}

FUNCTION EpubReaderResult
lectern0_seek_location(Lectern0App *app, U64 location_index)
{
  if (!app || !epub_reader_is_open(&app->reader)) return EpubReaderResult_NotOpen;
  U32 spine_index = 0;
  U64 byte_offset = 0;
  U64 location_count = 0;
  if (!epub_reader_location_target(&app->reader, location_index,
                                   &spine_index, &byte_offset,
                                   &location_count))
    return EpubReaderResult_Boundary;
  (void)location_count;
  return lectern0_navigate_to_location(app, spine_index, byte_offset,
                                       EpubReaderNavigationReason_Location);
}

FUNCTION B32
lectern0_set_highlight_color(Lectern0App *app, U32 color_index)
{
  if (!app || !app->reader.has_active_selection) { return 0; }
  S32 existing = lectern0_selection_highlight_index(app);
  Lectern0Highlight *highlight = 0;
  if (existing >= 0)
    highlight = app->highlights + existing;
  else
  {
    if (app->highlight_count >= Lectern0HighlightCap) return 0;
    highlight = app->highlights + app->highlight_count++;
    MemoryZeroStruct(highlight);
    highlight->id = app->next_record_id++;
    highlight->spine_index = app->reader.active_selection.spine_index;
    highlight->start_byte = app->reader.active_selection.text_byte_start;
    highlight->end_byte = app->reader.active_selection.text_byte_end;
    lectern0_copy_cstr(highlight->section, ARRAY_COUNT(highlight->section),
                       lectern0_current_section_label(app));
    lectern0_copy_cstr(highlight->text, ARRAY_COUNT(highlight->text),
                       app->selected_text);
  }
  highlight->color_index = color_index % READER_VIEW_HIGHLIGHT_COLOR_CAP;
  app->annotation_revision += 1;
  (void)lectern0_save_annotations(app);
  lectern0_set_statusf(app, "Highlight saved");
  return 1;
}

FUNCTION B32
lectern0_save_selection_note(Lectern0App *app, ReaderViewText note)
{
  if (!app || !app->reader.has_active_selection || note.size < 0) { return 0; }
  S32 index = lectern0_selection_highlight_index(app);
  if (index < 0)
  {
    if (!lectern0_set_highlight_color(app, 0)) return 0;
    index = lectern0_selection_highlight_index(app);
  }
  if (index < 0) return 0;
  Lectern0Highlight *highlight = app->highlights + index;
  lectern0_copy_bytes(highlight->note, ARRAY_COUNT(highlight->note),
                      (const U8 *)note.data, (U64)note.size);
  app->annotation_revision += 1;
  (void)lectern0_save_annotations(app);
  lectern0_set_statusf(app, "Note saved");
  return 1;
}

FUNCTION ReaderViewSurfaceStatus
lectern0_reader_view_status(ReaderViewLoadState state, const char *message)
{
  ReaderViewSurfaceStatus result = {0};
  result.state = state;
  result.message = lectern0_reader_view_text(message);
  return result;
}

FUNCTION void
lectern0_prepare_selected_text(Lectern0App *app)
{
  if (!app) { return; }
  app->selected_text[0] = 0;
  if (!app->reader.has_active_selection ||
      app->reader.active_selection.spine_index != app->reader.active_spine_index)
    return;
  U64 start = MIN(app->reader.active_selection.text_byte_start,
                  app->reader.spine_text.size);
  U64 end = MIN(app->reader.active_selection.text_byte_end,
                app->reader.spine_text.size);
  if (end <= start) return;
  lectern0_copy_bytes(app->selected_text, ARRAY_COUNT(app->selected_text),
                      app->reader.spine_text.str + start, end - start);
}

FUNCTION void
lectern0_prepare_reader_view_settings(Lectern0App *app)
{
  static const U32 families[] = {
    FontProviderBookContentFamily_Georgia,
    FontProviderBookContentFamily_NotoSerif,
    FontProviderBookContentFamily_PalatinoLinotype,
    FontProviderBookContentFamily_BookAntiqua,
    FontProviderBookContentFamily_TimesNewRoman,
    FontProviderBookContentFamily_PublisherSansSerif,
    FontProviderBookContentFamily_PublisherMonospace,
  };
  static const char *size_labels[] = {"Default", "Large", "Larger", "Largest"};
  static const char *spacing_labels[] = {"Compact", "Comfortable", "Spacious"};
  static const char *theme_labels[] = {"Light", "Sepia", "Dark"};
  U32 font_count = 0;
  for (U32 index = 0; index < ARRAY_COUNT(families); index += 1)
  {
    U32 family = families[index];
    if (!epub_reader_typography_family_available(&app->reader.typography, family))
      continue;
    ReaderViewChoice *choice = app->reader_view_font_choices + font_count++;
    *choice = (ReaderViewChoice){
      .key = 1000ull + family,
      .label = lectern0_reader_view_text(
        epub_reader_typography_family_label(&app->reader.typography, family)),
      .flags = ReaderViewChoice_Enabled |
        (family == app->font_family ? ReaderViewChoice_Selected : 0),
    };
  }
  if (font_count == 0)
  {
    app->font_family = FontProviderBookContentFamily_Georgia;
    app->reader_view_font_choices[0] = (ReaderViewChoice){
      .key = 1000ull + FontProviderBookContentFamily_Georgia,
      .label = lectern0_reader_view_text("Georgia"),
      .flags = ReaderViewChoice_Enabled | ReaderViewChoice_Selected,
    };
    font_count = 1;
  }
  for (U32 index = 0; index < ARRAY_COUNT(size_labels); index += 1)
  {
    app->reader_view_size_choices[index] = (ReaderViewChoice){
      .key = 2000ull + index,
      .label = lectern0_reader_view_text(size_labels[index]),
      .flags = ReaderViewChoice_Enabled |
        (index == app->text_size_index ? ReaderViewChoice_Selected : 0),
    };
  }
  for (U32 index = 0; index < ARRAY_COUNT(spacing_labels); index += 1)
  {
    app->reader_view_spacing_choices[index] = (ReaderViewChoice){
      .key = 3000ull + index,
      .label = lectern0_reader_view_text(spacing_labels[index]),
      .flags = ReaderViewChoice_Enabled |
        (index == app->line_spacing_index ? ReaderViewChoice_Selected : 0),
    };
  }
  for (U32 index = 0; index < ARRAY_COUNT(theme_labels); index += 1)
  {
    app->reader_view_theme_choices[index] = (ReaderViewChoice){
      .key = 4000ull + index,
      .label = lectern0_reader_view_text(theme_labels[index]),
      .flags = ReaderViewChoice_Enabled |
        (index == (U32)app->theme ? ReaderViewChoice_Selected : 0),
    };
  }
  app->reader_view_settings[0] = (ReaderViewSettingControl){
    .kind = ReaderViewSetting_FontFamily,
    .label = lectern0_reader_view_text("Font"),
    .status = lectern0_reader_view_status(ReaderViewLoad_Ready, 0),
    .choices = {
      .items = app->reader_view_font_choices,
      .count = (UI0S32)font_count,
      .presentation = ReaderViewChoicePresentation_Menu,
    },
  };
  app->reader_view_settings[1] = (ReaderViewSettingControl){
    .kind = ReaderViewSetting_FontSize,
    .label = lectern0_reader_view_text("Size"),
    .status = lectern0_reader_view_status(ReaderViewLoad_Ready, 0),
    .choices = {
      .items = app->reader_view_size_choices,
      .count = (UI0S32)ARRAY_COUNT(app->reader_view_size_choices),
      .presentation = ReaderViewChoicePresentation_Stepper,
    },
  };
  app->reader_view_settings[2] = (ReaderViewSettingControl){
    .kind = ReaderViewSetting_LineSpacing,
    .label = lectern0_reader_view_text("Spacing"),
    .status = lectern0_reader_view_status(ReaderViewLoad_Ready, 0),
    .choices = {
      .items = app->reader_view_spacing_choices,
      .count = (UI0S32)ARRAY_COUNT(app->reader_view_spacing_choices),
      .presentation = ReaderViewChoicePresentation_Stepper,
    },
  };
  app->reader_view_settings[3] = (ReaderViewSettingControl){
    .kind = ReaderViewSetting_Theme,
    .label = lectern0_reader_view_text("Theme"),
    .status = lectern0_reader_view_status(ReaderViewLoad_Ready, 0),
    .choices = {
      .items = app->reader_view_theme_choices,
      .count = (UI0S32)ARRAY_COUNT(app->reader_view_theme_choices),
      .presentation = ReaderViewChoicePresentation_Segments,
    },
  };
}

FUNCTION void
lectern0_prepare_reader_view_toc(Lectern0App *app)
{
  U32 count = MIN(app->frame.section_item_count,
                  (U32)READER_VIEW_TOC_ROW_CAP);
  for (U32 index = 0; index < count; index += 1)
  {
    const EpubReaderFrameSectionItem *source = app->frame.section_items + index;
    app->reader_view_toc_rows[index] = (ReaderViewTocRow){
      .key = (ReaderViewKey)source->nav_index + 1,
      .depth = source->depth,
      .label = lectern0_reader_view_bytes(source->label, source->label_length),
      .detail = lectern0_reader_view_bytes(source->detail, source->detail_length),
      .flags = ReaderViewRow_Enabled |
        (source->active ? ReaderViewRow_Current | ReaderViewRow_Selected : 0),
    };
  }
  app->reader_view_projection.toc = (ReaderViewTocProjection){
    .status = lectern0_reader_view_status(ReaderViewLoad_Ready, 0),
    .rows = app->reader_view_toc_rows,
    .row_count = (UI0S32)count,
    .total_count = app->frame.section_item_total_count,
  };
}

FUNCTION void
lectern0_prepare_reader_view_find(Lectern0App *app)
{
  U32 count = MIN(app->frame.search_item_count,
                  (U32)READER_VIEW_FIND_ROW_CAP);
  for (U32 index = 0; index < count; index += 1)
  {
    const EpubReaderFrameSearchItem *source = app->frame.search_items + index;
    app->reader_view_find_rows[index] = (ReaderViewFindRow){
      .key = 0x100000ull + index,
      .section = lectern0_reader_view_bytes(source->section_label,
                                             source->section_label_length),
      .excerpt = lectern0_reader_view_bytes(source->snippet,
                                             source->snippet_length),
      .match_start = source->match_start_in_snippet,
      .match_size = source->match_size_in_snippet,
      .flags = ReaderViewRow_Enabled |
        (app->frame.search_has_active &&
         app->frame.search_active_index == index ? ReaderViewRow_Selected : 0),
    };
  }
  app->reader_view_projection.find = (ReaderViewFindProjection){
    .status = lectern0_reader_view_status(ReaderViewLoad_Ready, 0),
    .committed_query = lectern0_reader_view_bytes(
      (const char *)app->frame.search_query.str, app->frame.search_query.size),
    .rows = app->reader_view_find_rows,
    .row_count = (UI0S32)count,
    .total_count = app->frame.search_total_count,
    .active_index = app->frame.search_has_active &&
                    app->frame.search_active_index < count ?
      (UI0S32)app->frame.search_active_index : -1,
    .has_more = app->frame.search_has_more,
    .can_step_previous = app->frame.search_total_count > 0,
    .can_step_next = app->frame.search_total_count > 0,
  };
}

FUNCTION void
lectern0_prepare_reader_view_right_rows(Lectern0App *app)
{
  U32 count = 0;
  for (U32 index = 0;
       index < app->bookmark_count && count < READER_VIEW_RIGHT_ROW_CAP;
       index += 1)
  {
    if (app->reader_view_state.right_filter != ReaderViewRightFilter_All &&
        app->reader_view_state.right_filter != ReaderViewRightFilter_Bookmarks)
      continue;
    const Lectern0Bookmark *bookmark = app->bookmarks + index;
    app->reader_view_right_rows[count++] = (ReaderViewRightRow){
      .key = bookmark->id,
      .kind = ReaderViewRightRow_Bookmark,
      .section = lectern0_reader_view_text(bookmark->label),
      .primary = lectern0_reader_view_text(bookmark->label),
      .flags = ReaderViewRow_Enabled |
        (bookmark->starred ? ReaderViewRow_Starred : 0),
      .actions = ReaderViewRightAction_Activate |
                 ReaderViewRightAction_ToggleStar |
                 ReaderViewRightAction_Delete,
    };
  }
  for (U32 index = 0;
       index < app->highlight_count && count < READER_VIEW_RIGHT_ROW_CAP;
       index += 1)
  {
    const Lectern0Highlight *highlight = app->highlights + index;
    B32 has_note = highlight->note[0] != 0;
    if (app->reader_view_state.right_filter == ReaderViewRightFilter_Bookmarks ||
        (app->reader_view_state.right_filter == ReaderViewRightFilter_Notes && !has_note))
      continue;
    app->reader_view_right_rows[count++] = (ReaderViewRightRow){
      .key = highlight->id,
      .kind = has_note ? ReaderViewRightRow_Note : ReaderViewRightRow_Highlight,
      .section = lectern0_reader_view_text(highlight->section),
      .primary = lectern0_reader_view_text(highlight->text),
      .secondary = lectern0_reader_view_text(highlight->note),
      .color_key = 5000ull + highlight->color_index,
      .flags = ReaderViewRow_Enabled |
        ((highlight->starred || highlight->note_starred) ?
          ReaderViewRow_Starred : 0),
      .actions = ReaderViewRightAction_Activate |
                 ReaderViewRightAction_ToggleStar |
                 ReaderViewRightAction_EditNote |
                 ReaderViewRightAction_Delete,
    };
  }
  app->reader_view_projection.right = (ReaderViewRightProjection){
    .status = lectern0_reader_view_status(ReaderViewLoad_Ready, 0),
    .rows = app->reader_view_right_rows,
    .row_count = (UI0S32)count,
    .total_count = count,
    .available_filters = ReaderViewRightFilterFlag_All |
                         ReaderViewRightFilterFlag_Bookmarks |
                         ReaderViewRightFilterFlag_Highlights |
                         ReaderViewRightFilterFlag_Notes,
  };
}

FUNCTION void
lectern0_prepare_reader_view_selection(Lectern0App *app)
{
  static const char *color_labels[] = {"Yellow", "Green", "Blue", "Pink"};
  ReaderViewSelectionProjection selection = {
    .status = lectern0_reader_view_status(ReaderViewLoad_Ready, 0),
  };
  lectern0_prepare_selected_text(app);
  if (app->reader.has_active_selection && app->selected_text[0])
  {
    DocSelection range = app->reader.active_selection;
    U64 key_parts[3] = {range.spine_index, range.text_byte_start, range.text_byte_end};
    selection.selection_key = u64_hash_bytes(key_parts, sizeof(key_parts));
    if (selection.selection_key == 0) selection.selection_key = 1;
    selection.revision = app->annotation_revision;
    selection.selected_text = lectern0_reader_view_text(app->selected_text);
    selection.flags = ReaderViewSelection_Active |
                      ReaderViewSelection_CanCopy |
                      ReaderViewSelection_CanHighlight |
                      ReaderViewSelection_CanAddNote |
                      ReaderViewSelection_CanDictionary |
                      ReaderViewSelection_CanWebLookup |
                      ReaderViewSelection_CanTranslate;
    selection.anchor_rect = app->selection_anchor_rect;
    S32 highlight_index = lectern0_selection_highlight_index(app);
    if (highlight_index >= 0)
    {
      const Lectern0Highlight *highlight = app->highlights + highlight_index;
      selection.current_color_key = 5000ull + highlight->color_index;
      selection.note_text = lectern0_reader_view_text(highlight->note);
      selection.flags |= ReaderViewSelection_CanRemoveHighlight |
                         ReaderViewSelection_CanEditNote;
      if (highlight->note[0]) selection.flags |= ReaderViewSelection_CanDeleteNote;
    }
    for (U32 index = 0; index < ARRAY_COUNT(color_labels); index += 1)
    {
      app->reader_view_color_choices[index] = (ReaderViewChoice){
        .key = 5000ull + index,
        .label = lectern0_reader_view_text(color_labels[index]),
        .flags = ReaderViewChoice_Enabled |
          (selection.current_color_key == 5000ull + index ?
            ReaderViewChoice_Selected : 0),
      };
    }
    selection.highlight_colors = (ReaderViewChoiceControl){
      .items = app->reader_view_color_choices,
      .count = (UI0S32)ARRAY_COUNT(color_labels),
      .presentation = ReaderViewChoicePresentation_Segments,
    };
  }
  app->reader_view_projection.selection = selection;
}

FUNCTION void
lectern0_prepare_reader_view_projection(Lectern0App *app)
{
  if (!app) { return; }
  ReaderViewProjection projection = {0};
  B32 open = app->document_state == ReaderViewLoad_Ready &&
             epub_reader_is_open(&app->reader) && app->frame.ready &&
             app->frame.document_open;
  projection.document_key = open ? (UI0U64)app->frame.document_id : 0;
  projection.features = ReaderViewFeature_Open |
                        ReaderViewFeature_Paging |
                        ReaderViewFeature_History |
                        ReaderViewFeature_Contents |
                        ReaderViewFeature_Find |
                        ReaderViewFeature_Progress |
                        ReaderViewFeature_ReadingSettings |
                        ReaderViewFeature_Bookmark |
                        ReaderViewFeature_Annotations |
                        ReaderViewFeature_SelectionTools |
                        ReaderViewFeature_Fullscreen |
                        ReaderViewFeature_DistractionFree |
                        ReaderViewFeature_Lookup |
                        ReaderViewFeature_Export;
  projection.document_flags = ReaderViewDocument_CanOpen |
                              ReaderViewDocument_CanToggleFullscreen |
                              ReaderViewDocument_CanToggleDistraction;
  if (open)
  {
    projection.document_flags |= ReaderViewDocument_Open;
    if (app->frame.page_index > 1)
      projection.document_flags |= ReaderViewDocument_CanGoPreviousPage;
    if (app->frame.page_count > 0 &&
        app->frame.page_index < app->frame.page_count)
      projection.document_flags |= ReaderViewDocument_CanGoNextPage;
    if (app->frame.history_back_count > 0)
      projection.document_flags |= ReaderViewDocument_CanGoBack;
    if (app->frame.history_forward_count > 0)
      projection.document_flags |= ReaderViewDocument_CanGoForward;
    S32 bookmark_index = lectern0_current_bookmark_index(app);
    if (bookmark_index >= 0)
    {
      projection.document_flags |= ReaderViewDocument_CurrentBookmarked;
      projection.current_bookmark_key = app->bookmarks[bookmark_index].id;
    }
  }
  if (app->fullscreen.active)
    projection.document_flags |= ReaderViewDocument_Fullscreen;
  if (app->distraction_free)
    projection.document_flags |= ReaderViewDocument_DistractionFree;

  if (open)
    projection.content = lectern0_reader_view_status(ReaderViewLoad_Ready, 0);
  else if (app->document_state == ReaderViewLoad_Loading)
    projection.content = lectern0_reader_view_status(ReaderViewLoad_Loading,
                                                      app->status);
  else if (app->document_state == ReaderViewLoad_Error)
    projection.content = lectern0_reader_view_status(ReaderViewLoad_Error,
                                                      app->status);
  else
    projection.content = lectern0_reader_view_status(
      ReaderViewLoad_Empty, "Open an EPUB to begin reading.");
  projection.labels = reader_view_default_english_labels();
  projection.labels.annotations = lectern0_reader_view_text("Notes");
  projection.labels.distraction_free = lectern0_reader_view_text("Focus");
  projection.labels.fullscreen = lectern0_reader_view_text("Full");
  projection.labels.exit_fullscreen = lectern0_reader_view_text("Window");
  app->document_title[0] = 0;
  if (open)
  {
    String8 title = {0};
    if (doc_engine_get_title(epub_reader_engine(&app->reader),
                             epub_reader_document_id(&app->reader),
                             &title) == DocError_Ok)
      lectern0_copy_bytes(app->document_title, ARRAY_COUNT(app->document_title),
                          title.str, title.size);
  }
  projection.document_title = lectern0_reader_view_text(app->document_title);
  app->reader_view_projection = projection;

  lectern0_prepare_reader_view_settings(app);
  app->reader_view_projection.settings = (ReaderViewReadingSettingsProjection){
    .status = lectern0_reader_view_status(ReaderViewLoad_Ready, 0),
    .items = app->reader_view_settings,
    .count = READER_VIEW_SETTING_CAP,
  };

  if (open)
  {
    EpubReaderLocationSummary location = epub_reader_location_summary(&app->reader);
    (void)cstr_format(app->progress_label, ARRAY_COUNT(app->progress_label),
                      "%llu of %llu",
                      (unsigned long long)app->frame.page_index,
                      (unsigned long long)app->frame.page_count);
    app->reader_view_projection.progress = (ReaderViewProgressProjection){
      .status = lectern0_reader_view_status(ReaderViewLoad_Ready, 0),
      .location_index = location.available ? location.location_index :
                        (app->frame.page_index > 0 ? app->frame.page_index - 1 : 0),
      .location_count = location.available ? location.location_count :
                        app->frame.page_count,
      .page_index = app->frame.page_index > 0 ? app->frame.page_index - 1 : 0,
      .page_count = app->frame.page_count,
      .chapter = lectern0_reader_view_text(lectern0_current_section_label(app)),
      .label = lectern0_reader_view_text(app->progress_label),
      .can_seek = location.available,
    };
    lectern0_prepare_reader_view_toc(app);
    lectern0_prepare_reader_view_find(app);
    lectern0_prepare_reader_view_right_rows(app);
    lectern0_prepare_reader_view_selection(app);
  }
  else
  {
    ReaderViewSurfaceStatus unavailable =
      lectern0_reader_view_status(ReaderViewLoad_Unavailable,
                                  "Open an EPUB first");
    app->reader_view_projection.progress.status = unavailable;
    app->reader_view_projection.toc.status = unavailable;
    app->reader_view_projection.find.status = unavailable;
    app->reader_view_projection.find.active_index = -1;
    app->reader_view_projection.right.status = unavailable;
    app->reader_view_projection.right.available_filters =
      ReaderViewRightFilterFlag_All |
      ReaderViewRightFilterFlag_Bookmarks |
      ReaderViewRightFilterFlag_Highlights |
      ReaderViewRightFilterFlag_Notes;
    app->reader_view_projection.selection.status = unavailable;
  }

  UI0TokenSet tokens = ui0_default_tokens(
    app->theme == Lectern0Theme_Dark ? UI0ThemeKind_Dark : UI0ThemeKind_Light);
  app->reader_view_theme = ui0_resolve_tokens(&tokens);
}

FUNCTION B32
lectern0_reader_view_focus_is(const Lectern0App *app,
                              ReaderViewSemanticRole role)
{
  if (!app || !app->reader_view_frame.semantic_nodes ||
      app->reader_view_state.focus_id == 0)
    return 0;
  for (UI0S32 index = 0;
       index < app->reader_view_frame.semantic_node_count;
       index += 1)
  {
    const ReaderViewSemanticNode *node =
      app->reader_view_frame.semantic_nodes + index;
    if (node->id == app->reader_view_state.focus_id && node->role == role)
      return 1;
  }
  return 0;
}

FUNCTION ReaderViewInput
lectern0_reader_view_input(Lectern0App *app)
{
  ReaderViewInput result = {0};
  result.ui = ui0_input_pointer_wheel(app->input.pointer_x,
                                      app->input.pointer_y,
                                      app->input.pointer_down,
                                      app->input.pointer_pressed,
                                      app->input.pointer_released,
                                      0,
                                      app->input.wheel_delta_y);
  if (app->input.activate_pressed) result.ui.flags |= UI0Input_ActivatePressed;
  if (app->input.focus_next_pressed) result.ui.flags |= UI0Input_FocusNextPressed;
  if (app->input.focus_prev_pressed) result.ui.flags |= UI0Input_FocusPrevPressed;
  result.escape_pressed = app->input.escape_pressed;
  B32 editing = app->reader_view_state.left_panel == ReaderViewLeftPanel_Find ||
                app->reader_view_state.popup == ReaderViewPopup_NoteEditor;
  if (!editing)
  {
    result.move_horizontal_delta = app->input.move_delta;
    result.move_vertical_delta = app->input.move_vertical_delta;
    result.range_move = app->input.range_move;
  }

  UI0TextInputFrameInput text = {0};
  text.text = app->input.text;
  text.text_len = app->input.text_length;
  text.move_delta = app->input.move_delta;
  text.extend_selection = app->input.extend_selection;
  text.select_all = app->input.select_all_pressed;
  text.copy_pressed = app->input.copy_pressed;
  text.cut_pressed = app->input.cut_pressed;
  text.paste_pressed = app->input.paste_pressed;
  text.undo_pressed = app->input.undo_pressed;
  text.redo_pressed = app->input.redo_pressed;
  text.backspace_pressed = app->input.backspace_pressed;
  text.delete_pressed = app->input.delete_pressed;
  text.commit_pressed = app->input.commit_pressed;
  text.transfer_buffer = &app->clipboard_transfer;
  result.find_text = text;
  result.note_text = (UI0TextAreaFrameInput){
    .text = text.text,
    .text_len = text.text_len,
    .move_delta = text.move_delta,
    .move_vertical_delta = app->input.move_vertical_delta,
    .extend_selection = text.extend_selection,
    .select_all = text.select_all,
    .transfer_buffer = text.transfer_buffer,
    .copy_pressed = text.copy_pressed,
    .cut_pressed = text.cut_pressed,
    .paste_pressed = text.paste_pressed,
    .undo_pressed = text.undo_pressed,
    .redo_pressed = text.redo_pressed,
    .backspace_pressed = text.backspace_pressed,
    .delete_pressed = text.delete_pressed,
  };
  return result;
}

FUNCTION B32
lectern0_build_reader_view(Lectern0App *app)
{
  if (!app) { return 0; }
  lectern0_prepare_reader_view_projection(app);
  ReaderViewLayoutInput layout_input = {
    .bounds = ui0_rect(0, 0, app->width, app->height),
    .features = app->reader_view_projection.features,
    .document_flags = app->reader_view_projection.document_flags,
  };
  if (!reader_view_resolve_layout(&app->reader_view_state,
                                  &layout_input,
                                  &app->reader_view_layout))
  {
    app->reader_view_ready = 0;
    return 0;
  }
  if (epub_reader_is_open(&app->reader) &&
      (app->pagination_viewport_width != app->reader_view_layout.viewport_rect.w ||
       app->pagination_viewport_height != app->reader_view_layout.viewport_rect.h))
  {
    if (!lectern0_repaginate(app))
    {
      app->reader_view_ready = 0;
      return 0;
    }
    lectern0_prepare_reader_view_projection(app);
  }
  ReaderViewInput input = lectern0_reader_view_input(app);
  ReaderViewBuildInput build = {
    .frame_index = ++app->reader_view_frame_index,
    .state = &app->reader_view_state,
    .layout = &app->reader_view_layout,
    .projection = &app->reader_view_projection,
    .input = &input,
    .theme = &app->reader_view_theme,
  };
  app->reader_view_ready = reader_view_build(&build,
                                             &app->reader_view_storage,
                                             &app->reader_view_frame);
  return app->reader_view_ready;
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
  app->font_family = FontProviderBookContentFamily_Georgia;
  app->text_size_index = 0;
  app->line_spacing_index = 0;
  app->theme = Lectern0Theme_Light;
  app->document_state = ReaderViewLoad_Empty;
  app->next_record_id = 1;
  app->clipboard_transfer.data = app->clipboard_text;
  app->clipboard_transfer.length = &app->clipboard_length;
  app->clipboard_transfer.cap = Lectern0ClipboardCap;
  reader_view_state_init(&app->reader_view_state);
  app->arena = arena_alloc(0);
  if (!app->arena) { return 0; }

  if (persistence_enabled && lectern0_state_paths(app))
  {
    lectern0_load_state(app);
    lectern0_load_settings(app);
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
  }
  lectern0_set_statusf(app, "Open an EPUB | Ctrl+O");
  return 1;
}

FUNCTION void
lectern0_app_release(Lectern0App *app)
{
  if (!app) { return; }
  if (app->accessibility) lectern0_accessibility_destroy(app->accessibility);
  (void)lectern0_save_state(app);
  (void)lectern0_save_settings(app);
  (void)lectern0_save_annotations(app);
  if (app->gfx_ready) { os_gfx_release(&app->gfx); }
  if (app->render_ready) { render_state_release(&app->render_state); }
  lectern0_image_cache_release(&app->image_cache);
  epub_reader_release(&app->reader);
  if (app->arena) { arena_release(app->arena); }
  MemoryZeroStruct(app);
}

FUNCTION ReaderViewText
lectern0_reader_view_binding(const Lectern0App *app, UI0ID id)
{
  if (!app || !app->reader_view_frame.text_bindings) return (ReaderViewText){0};
  for (UI0S32 index = 0;
       index < app->reader_view_frame.text_binding_count;
       index += 1)
  {
    const ReaderViewTextBinding *binding =
      app->reader_view_frame.text_bindings + index;
    if (binding->source_id == id) return binding->text;
  }
  return (ReaderViewText){0};
}

FUNCTION B32
lectern0_reader_view_text_is(ReaderViewText text, const char *expected)
{
  if (!expected || text.size < 0) return 0;
  U64 size = strlen(expected);
  return size == (U64)text.size &&
         (size == 0 || (text.data && memcmp(text.data, expected, size) == 0));
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
  for (UI0S32 index = 0;
       index < app->reader_view_frame.draw_command_count;
       index += 1)
  {
    UI0DrawCommand command = app->reader_view_frame.draw_commands[index];
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
        ReaderViewText text = lectern0_reader_view_binding(app, command.source_id);
        char label[READER_VIEW_NOTE_DRAFT_CAP] = {0};
        lectern0_copy_bytes(label, ARRAY_COUNT(label),
                            (const U8 *)text.data, (U64)MAX(text.size, 0));
        if (command.source_kind == UI0ControlKind_IconButton && command.rect.w <= 40)
        {
          if (lectern0_reader_view_text_is(text, "Previous page") ||
              lectern0_reader_view_text_is(text, "Previous match"))
            lectern0_copy_cstr(label, ARRAY_COUNT(label), "<");
          else if (lectern0_reader_view_text_is(text, "Next page") ||
                   lectern0_reader_view_text_is(text, "Next match"))
            lectern0_copy_cstr(label, ARRAY_COUNT(label), ">");
          else if (lectern0_reader_view_text_is(text, "Close"))
            lectern0_copy_cstr(label, ARRAY_COUNT(label), "X");
          else if (lectern0_reader_view_text_is(text, "More"))
            lectern0_copy_cstr(label, ARRAY_COUNT(label), "...");
          else if (lectern0_reader_view_text_is(text, "Star") ||
                   lectern0_reader_view_text_is(text, "Unstar"))
            lectern0_copy_cstr(label, ARRAY_COUNT(label), "*");
        }
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

FUNCTION B32
lectern0_set_clipboard_text(Lectern0App *app, ReaderViewText text)
{
  if (!app || text.size < 0 || (text.size > 0 && !text.data) ||
      !OpenClipboard(app->window))
    return 0;
  B32 result = 0;
  if (EmptyClipboard())
  {
    int wide_count = MultiByteToWideChar(CP_UTF8, 0, text.data, text.size, 0, 0);
    if (wide_count >= 0)
    {
      HGLOBAL memory = GlobalAlloc(GMEM_MOVEABLE,
                                   ((SIZE_T)wide_count + 1) * sizeof(wchar_t));
      wchar_t *wide = memory ? (wchar_t *)GlobalLock(memory) : 0;
      if (wide)
      {
        if (wide_count > 0)
          (void)MultiByteToWideChar(CP_UTF8, 0, text.data, text.size,
                                    wide, wide_count);
        wide[wide_count] = 0;
        GlobalUnlock(memory);
        if (SetClipboardData(CF_UNICODETEXT, memory)) result = 1;
      }
      if (memory && !result) GlobalFree(memory);
    }
  }
  CloseClipboard();
  return result;
}

FUNCTION B32
lectern0_get_clipboard_text(Lectern0App *app)
{
  if (!app || !OpenClipboard(app->window)) return 0;
  app->clipboard_length = 0;
  app->clipboard_text[0] = 0;
  HANDLE memory = GetClipboardData(CF_UNICODETEXT);
  const wchar_t *wide = memory ? (const wchar_t *)GlobalLock(memory) : 0;
  B32 result = 0;
  if (wide)
  {
    int length = WideCharToMultiByte(CP_UTF8, 0, wide, -1,
                                     app->clipboard_text,
                                     ARRAY_COUNT(app->clipboard_text),
                                     0, 0);
    if (length > 0)
    {
      app->clipboard_length = length - 1;
      result = 1;
    }
    GlobalUnlock(memory);
  }
  CloseClipboard();
  return result;
}

FUNCTION void
lectern0_append_input_wchar(Lectern0App *app, wchar_t value)
{
  if (!app) return;
  wchar_t units[2] = {0};
  int unit_count = 1;
  if (value >= 0xD800 && value <= 0xDBFF)
  {
    app->pending_high_surrogate = value;
    return;
  }
  if (value >= 0xDC00 && value <= 0xDFFF && app->pending_high_surrogate)
  {
    units[0] = app->pending_high_surrogate;
    units[1] = value;
    unit_count = 2;
  }
  else
  {
    units[0] = value;
  }
  app->pending_high_surrogate = 0;
  char encoded[8] = {0};
  int size = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS,
                                 units, unit_count, encoded,
                                 ARRAY_COUNT(encoded), 0, 0);
  if (size > 0 && app->input.text_length + size < Lectern0InputTextCap)
  {
    MemoryCopy(app->input.text + app->input.text_length, encoded, (U64)size);
    app->input.text_length += size;
    app->input.text[app->input.text_length] = 0;
  }
}

FUNCTION B32
lectern0_launch_lookup(Lectern0App *app,
                       const char *prefix,
                       ReaderViewText text)
{
  if (!app || !prefix || text.size <= 0 || !text.data) return 0;
  char url[Lectern0UrlCap] = {0};
  U64 at = cstr_format(url, ARRAY_COUNT(url), "%s", prefix);
  static const char hex[] = "0123456789ABCDEF";
  for (UI0S32 index = 0; index < text.size && at + 4 < ARRAY_COUNT(url); index += 1)
  {
    U8 byte = (U8)text.data[index];
    B32 plain = (byte >= 'a' && byte <= 'z') ||
                (byte >= 'A' && byte <= 'Z') ||
                (byte >= '0' && byte <= '9') ||
                byte == '-' || byte == '_' || byte == '.' || byte == '~';
    if (plain)
      url[at++] = (char)byte;
    else
    {
      url[at++] = '%';
      url[at++] = hex[(byte >> 4) & 15];
      url[at++] = hex[byte & 15];
    }
  }
  url[at] = 0;
  HINSTANCE opened = ShellExecuteA(app->window, "open", url, 0, 0, SW_SHOWNORMAL);
  return (INT_PTR)opened > 32;
}

FUNCTION B32
lectern0_export_annotations(Lectern0App *app)
{
  if (!app || !app->export_path[0]) return 0;
  char data[65536] = {0};
  U64 size = cstr_format(data, ARRAY_COUNT(data),
                         "lectern0 reader annotations\nBook: %s\n\n",
                         app->document_title[0] ? app->document_title : app->current_path);
  for (U32 index = 0; index < app->bookmark_count && size < ARRAY_COUNT(data); index += 1)
  {
    const Lectern0Bookmark *item = app->bookmarks + index;
    size += cstr_format(data + size, ARRAY_COUNT(data) - size,
                        "Bookmark %llu | %s | spine %u byte %llu%s\n",
                        (unsigned long long)item->id, item->label,
                        item->spine_index, (unsigned long long)item->byte_offset,
                        item->starred ? " | starred" : "");
  }
  for (U32 index = 0; index < app->highlight_count && size < ARRAY_COUNT(data); index += 1)
  {
    const Lectern0Highlight *item = app->highlights + index;
    size += cstr_format(data + size, ARRAY_COUNT(data) - size,
                        "Highlight %llu | %s | %s%s%s\n",
                        (unsigned long long)item->id,
                        item->section, item->text,
                        item->note[0] ? " | Note: " : "",
                        item->note);
  }
  B32 result = size < ARRAY_COUNT(data) &&
    os_write_entire_file_atomic(app->export_path, data, size);
  lectern0_set_statusf(app, result ? "Annotations exported: %s" :
                                     "Annotation export failed",
                       result ? app->export_path : "");
  return result;
}

FUNCTION B32
lectern0_set_fullscreen(Lectern0App *app, B32 active)
{
  if (!app || !app->window || app->fullscreen.active == active) return app != 0;
  if (active)
  {
    app->fullscreen.style = (DWORD)GetWindowLongPtrW(app->window, GWL_STYLE);
    app->fullscreen.ex_style = (DWORD)GetWindowLongPtrW(app->window, GWL_EXSTYLE);
    app->fullscreen.placement.length = sizeof(app->fullscreen.placement);
    if (!GetWindowPlacement(app->window, &app->fullscreen.placement)) return 0;
    MONITORINFO monitor = {.cbSize = sizeof(monitor)};
    if (!GetMonitorInfoW(MonitorFromWindow(app->window, MONITOR_DEFAULTTONEAREST),
                         &monitor)) return 0;
    SetWindowLongPtrW(app->window, GWL_STYLE,
                      (LONG_PTR)(app->fullscreen.style & ~WS_OVERLAPPEDWINDOW));
    SetWindowPos(app->window, HWND_TOP,
                 monitor.rcMonitor.left, monitor.rcMonitor.top,
                 monitor.rcMonitor.right - monitor.rcMonitor.left,
                 monitor.rcMonitor.bottom - monitor.rcMonitor.top,
                 SWP_FRAMECHANGED | SWP_NOOWNERZORDER);
  }
  else
  {
    SetWindowLongPtrW(app->window, GWL_STYLE, (LONG_PTR)app->fullscreen.style);
    SetWindowLongPtrW(app->window, GWL_EXSTYLE, (LONG_PTR)app->fullscreen.ex_style);
    SetWindowPlacement(app->window, &app->fullscreen.placement);
    SetWindowPos(app->window, 0, 0, 0, 0, 0,
                 SWP_FRAMECHANGED | SWP_NOMOVE | SWP_NOSIZE |
                 SWP_NOZORDER | SWP_NOOWNERZORDER);
  }
  app->fullscreen.active = active;
  return 1;
}

FUNCTION B32
lectern0_apply_setting(Lectern0App *app,
                       ReaderViewSettingKind kind,
                       ReaderViewKey key)
{
  if (!app) return 0;
  B32 repaginate = 0;
  switch (kind)
  {
    case ReaderViewSetting_FontFamily:
    {
      if (key < 1000) return 0;
      U32 family = (U32)(key - 1000);
      if (!epub_reader_typography_family_available(&app->reader.typography, family))
        return 0;
      app->font_family = family;
      repaginate = 1;
    } break;
    case ReaderViewSetting_FontSize:
      if (key < 2000 || key >= 2004) return 0;
      app->text_size_index = (U32)(key - 2000);
      repaginate = 1;
      break;
    case ReaderViewSetting_LineSpacing:
      if (key < 3000 || key >= 3003) return 0;
      app->line_spacing_index = (U32)(key - 3000);
      repaginate = 1;
      break;
    case ReaderViewSetting_Theme:
      if (key < 4000 || key >= 4000 + Lectern0Theme_Count) return 0;
      app->theme = (Lectern0Theme)(key - 4000);
      break;
    default: return 0;
  }
  (void)lectern0_save_settings(app);
  if (repaginate && epub_reader_is_open(&app->reader))
    return lectern0_repaginate(app);
  return 1;
}

FUNCTION void
lectern0_select_highlight(Lectern0App *app, const Lectern0Highlight *highlight)
{
  if (!app || !highlight) return;
  DocSelection selection = {
    .spine_index = highlight->spine_index,
    .text_byte_start = highlight->start_byte,
    .text_byte_end = highlight->end_byte,
  };
  (void)epub_reader_set_selection(&app->reader, selection);
}

FUNCTION void
lectern0_apply_reader_view_action(Lectern0App *app,
                                  const ReaderViewAction *action)
{
  if (!app || !action) return;
  switch (action->kind)
  {
    case ReaderViewAction_Open: (void)lectern0_pick_epub(app); break;
    case ReaderViewAction_PreviousPage: (void)lectern0_move_page(app, -1); break;
    case ReaderViewAction_NextPage: (void)lectern0_move_page(app, 1); break;
    case ReaderViewAction_HistoryBack: (void)lectern0_move_history(app, 0); break;
    case ReaderViewAction_HistoryForward: (void)lectern0_move_history(app, 1); break;
    case ReaderViewAction_SeekLocation: (void)lectern0_seek_location(app, action->value); break;
    case ReaderViewAction_SelectSetting:
      (void)lectern0_apply_setting(app, action->setting_kind, action->key);
      break;
    case ReaderViewAction_ToggleBookmark:
      (void)lectern0_toggle_current_bookmark(app);
      break;
    case ReaderViewAction_FindChanged:
      if (action->text.size > 0)
        (void)epub_reader_rebuild_search(&app->reader,
          str8((U8 *)action->text.data, (U64)action->text.size));
      else
        epub_reader_clear_search(&app->reader);
      (void)lectern0_capture_frame(app);
      break;
    case ReaderViewAction_FindCommitted:
      if (app->reader.search_match_count > 0)
        (void)lectern0_navigate_to_search_match(
          app, app->reader.search_has_active ? app->reader.search_active_index : 0,
          &(EpubReaderSearchNavigationResult){0});
      break;
    case ReaderViewAction_FindPrevious:
    case ReaderViewAction_FindNext:
      if (epub_reader_search_step(&app->reader,
            action->kind == ReaderViewAction_FindPrevious ? -1 : 1))
        (void)lectern0_navigate_to_search_match(
          app, app->reader.search_active_index,
          &(EpubReaderSearchNavigationResult){0});
      break;
    case ReaderViewAction_ActivateTocRow:
      if (action->key > 0)
        (void)lectern0_navigate_to_nav_point(
          app, (U32)(action->key - 1), &(EpubReaderNavPointResult){0});
      break;
    case ReaderViewAction_ActivateFindRow:
      if (action->key >= 0x100000ull)
        (void)lectern0_navigate_to_search_match(
          app, (U32)(action->key - 0x100000ull),
          &(EpubReaderSearchNavigationResult){0});
      break;
    case ReaderViewAction_ActivateRightRow:
    {
      S32 bookmark = lectern0_bookmark_index(app, action->key);
      S32 highlight = lectern0_highlight_index(app, action->key);
      if (bookmark >= 0)
        (void)lectern0_navigate_to_location(app,
          app->bookmarks[bookmark].spine_index,
          app->bookmarks[bookmark].byte_offset,
          EpubReaderNavigationReason_Bookmark);
      else if (highlight >= 0)
        (void)lectern0_navigate_to_location(app,
          app->highlights[highlight].spine_index,
          app->highlights[highlight].start_byte,
          EpubReaderNavigationReason_Annotation);
    } break;
    case ReaderViewAction_ToggleRightRowStar:
    {
      S32 bookmark = lectern0_bookmark_index(app, action->key);
      S32 highlight = lectern0_highlight_index(app, action->key);
      if (bookmark >= 0) app->bookmarks[bookmark].starred = !app->bookmarks[bookmark].starred;
      else if (highlight >= 0 && action->right_row_kind == ReaderViewRightRow_Note)
        app->highlights[highlight].note_starred = !app->highlights[highlight].note_starred;
      else if (highlight >= 0)
        app->highlights[highlight].starred = !app->highlights[highlight].starred;
      app->annotation_revision += 1;
      (void)lectern0_save_annotations(app);
    } break;
    case ReaderViewAction_EditRightRowNote:
    {
      S32 highlight = lectern0_highlight_index(app, action->key);
      if (highlight >= 0)
      {
        Lectern0Highlight value = app->highlights[highlight];
        if (lectern0_navigate_to_location(app, value.spine_index,
                                          value.start_byte,
                                          EpubReaderNavigationReason_Annotation) ==
            EpubReaderResult_Ok)
        {
          lectern0_select_highlight(app, &value);
          lectern0_prepare_reader_view_projection(app);
          if (!reader_view_open_note_editor(
                &app->reader_view_state,
                &app->reader_view_projection.selection))
            app->reader_view_state.popup = ReaderViewPopup_SelectionTools;
        }
      }
    } break;
    case ReaderViewAction_DeleteRightRow:
    {
      S32 bookmark = lectern0_bookmark_index(app, action->key);
      S32 highlight = lectern0_highlight_index(app, action->key);
      if (bookmark >= 0) lectern0_remove_bookmark_at(app, (U32)bookmark);
      else if (highlight >= 0) lectern0_remove_highlight_at(app, (U32)highlight);
    } break;
    case ReaderViewAction_ExportRightRows: (void)lectern0_export_annotations(app); break;
    case ReaderViewAction_SetHighlightColor:
      if (action->auxiliary_key >= 5000)
        (void)lectern0_set_highlight_color(app,
                                           (U32)(action->auxiliary_key - 5000));
      break;
    case ReaderViewAction_RemoveHighlight:
    {
      S32 index = lectern0_selection_highlight_index(app);
      if (index >= 0) lectern0_remove_highlight_at(app, (U32)index);
    } break;
    case ReaderViewAction_CopySelection:
      (void)lectern0_set_clipboard_text(app, action->text);
      break;
    case ReaderViewAction_DictionarySelection:
      (void)lectern0_launch_lookup(app,
        "https://www.google.com/search?q=define%3A", action->text);
      break;
    case ReaderViewAction_WebLookupSelection:
      (void)lectern0_launch_lookup(app,
        "https://www.google.com/search?q=", action->text);
      break;
    case ReaderViewAction_TranslateSelection:
      (void)lectern0_launch_lookup(app,
        "https://translate.google.com/?sl=auto&tl=en&text=", action->text);
      break;
    case ReaderViewAction_SaveNote:
      if (action->value == app->annotation_revision)
      {
        if (lectern0_save_selection_note(app, action->text))
        {
          app->reader_view_state.popup = ReaderViewPopup_None;
          app->reader_view_state.note_dirty = 0;
        }
      }
      break;
    case ReaderViewAction_DeleteNote:
    {
      S32 index = lectern0_selection_highlight_index(app);
      if (index >= 0)
      {
        app->highlights[index].note[0] = 0;
        app->annotation_revision += 1;
        (void)lectern0_save_annotations(app);
        app->reader_view_state.popup = ReaderViewPopup_None;
        app->reader_view_state.note_dirty = 0;
      }
    } break;
    case ReaderViewAction_ToggleFullscreen:
      (void)lectern0_set_fullscreen(app, !app->fullscreen.active);
      break;
    case ReaderViewAction_ToggleDistractionFree:
      app->distraction_free = !app->distraction_free;
      break;
    case ReaderViewAction_RightFilterChanged:
    case ReaderViewAction_None:
    default: break;
  }
}

FUNCTION void
lectern0_apply_reader_view_actions(Lectern0App *app)
{
  if (!app || !app->reader_view_frame.actions) return;
  ReaderViewAction actions[READER_VIEW_ACTION_CAP];
  UI0S32 count = MIN(app->reader_view_frame.action_count,
                     (UI0S32)ARRAY_COUNT(actions));
  if (count > 0)
    MemoryCopy(actions, app->reader_view_frame.actions,
               (U64)count * sizeof(actions[0]));
  app->reader_view_frame.action_count = 0;
  for (UI0S32 index = 0; index < count; index += 1)
    lectern0_apply_reader_view_action(app, actions + index);
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

FUNCTION B32
lectern0_reader_point_to_byte(Lectern0App *app,
                              S32 x,
                              S32 y,
                              U64 *out_byte,
                              UI0Rect *out_anchor_rect)
{
  if (out_byte) *out_byte = 0;
  if (out_anchor_rect) *out_anchor_rect = ui0_rect(0, 0, 0, 0);
  if (!app || !out_byte || !out_anchor_rect || !app->reader_view_ready ||
      !app->presentation_frame.valid || !epub_reader_is_open(&app->reader) ||
      app->reader_view_state.popup != ReaderViewPopup_None)
    return 0;
  UI0Rect viewport = app->reader_view_layout.viewport_rect;
  if (!ui0_rect_contains_point(viewport, x, y) ||
      (app->reader_view_layout.left_panel_visible &&
       ui0_rect_contains_point(app->reader_view_layout.left_panel_rect, x, y)) ||
      (app->reader_view_layout.right_panel_visible &&
       ui0_rect_contains_point(app->reader_view_layout.right_panel_rect, x, y)) ||
      ui0_rect_contains_point(app->reader_view_layout.previous_gutter_rect, x, y) ||
      ui0_rect_contains_point(app->reader_view_layout.next_gutter_rect, x, y))
    return 0;
  for (U32 index = 0; index < app->presentation_frame.row_count; index += 1)
  {
    const PresentationEngineBlockFlowRow *row =
      app->presentation_frame.rows + index;
    UI0Rect rect = ui0_rect(row->content_rect.x, row->row_rect.y,
                            row->content_rect.w, row->row_rect.h);
    if (!ui0_rect_contains_point(rect, x, y) || row->source_end <= row->source_start)
      continue;
    U64 source_size = row->source_end - row->source_start;
    S32 local_x = MIN(MAX(x - rect.x, 0), MAX(rect.w, 1));
    U64 relative = row->source_start +
      ((U64)local_x * source_size) / (U64)MAX(rect.w, 1);
    relative = MIN(relative, app->frame.visible_text.size);
    relative = base_unicode_utf8_previous_grapheme_boundary(
      app->frame.visible_text, relative);
    *out_byte = app->frame.view_byte_offset + relative;
    *out_anchor_rect = ui0_rect(x - 2, rect.y, 4, rect.h);
    return 1;
  }
  return 0;
}

FUNCTION void
lectern0_update_pointer_selection(Lectern0App *app, S32 x, S32 y, B32 begin)
{
  if (!app) return;
  U64 byte = 0;
  UI0Rect anchor_rect = {0};
  if (!lectern0_reader_point_to_byte(app, x, y, &byte, &anchor_rect))
  {
    if (begin) app->selection_dragging = 0;
    return;
  }
  if (begin)
  {
    app->selection_dragging = 1;
    app->selection_anchor_byte = byte;
  }
  if (!app->selection_dragging) return;
  U64 start = MIN(app->selection_anchor_byte, byte);
  U64 end = MAX(app->selection_anchor_byte, byte);
  if (end == start)
  {
    U64 relative = start >= app->frame.view_byte_offset ?
      start - app->frame.view_byte_offset : 0;
    if (relative >= app->frame.visible_text.size && relative > 0)
    {
      U64 previous = base_unicode_utf8_previous_grapheme_boundary(
        app->frame.visible_text, relative);
      start = app->frame.view_byte_offset + previous;
      end = app->frame.view_byte_offset + relative;
    }
    else
    {
      U64 next = base_unicode_utf8_next_grapheme_boundary(app->frame.visible_text,
                                                          relative);
      end = app->frame.view_byte_offset + MIN(next, app->frame.visible_text.size);
    }
  }
  if (end <= start) return;
  DocSelection selection = {
    .spine_index = app->reader.active_spine_index,
    .text_byte_start = start,
    .text_byte_end = end,
  };
  if (epub_reader_set_selection(&app->reader, selection) == EpubReaderResult_Ok)
  {
    app->selection_anchor_rect = anchor_rect;
    lectern0_prepare_selected_text(app);
  }
}

FUNCTION U32
lectern0_reader_page_color(const Lectern0App *app)
{
  if (!app) return 0x00FFFDF8U;
  if (app->theme == Lectern0Theme_Dark) return 0x00201E1BU;
  if (app->theme == Lectern0Theme_Sepia) return 0x00F5ECD8U;
  return 0x00FFFDF8U;
}

FUNCTION U32
lectern0_reader_ink_color(const Lectern0App *app)
{
  if (app && app->theme == Lectern0Theme_Dark) return 0x00E8E1D5U;
  if (app && app->theme == Lectern0Theme_Sepia) return 0x004A3828U;
  return 0x002A2927U;
}

FUNCTION U32
lectern0_reader_highlight_color(const Lectern0App *app, U32 color_index)
{
  static const U32 light[] = {0x00FFE58AU, 0x00BFE7B7U, 0x00ADD8F4U, 0x00F3B6D2U};
  static const U32 dark[] = {0x00786521U, 0x003A693EU, 0x00375F7AU, 0x00724662U};
  color_index %= ARRAY_COUNT(light);
  return app && app->theme == Lectern0Theme_Dark ? dark[color_index] : light[color_index];
}

FUNCTION void
lectern0_draw_row_highlights(Lectern0App *app,
                             const EpubReaderFrameStyleRow *row,
                             const PresentationEngineBlockFlowRow *presentation_row)
{
  if (!app || !row || !presentation_row || row->byte_end <= row->byte_start)
    return;
  U64 row_start = app->frame.view_byte_offset + row->byte_start;
  U64 row_end = app->frame.view_byte_offset + row->byte_end;
  U64 row_size = row_end - row_start;
  for (U32 index = 0; index < app->highlight_count; index += 1)
  {
    const Lectern0Highlight *highlight = app->highlights + index;
    if (highlight->spine_index != app->frame.spine_index ||
        highlight->end_byte <= row_start || highlight->start_byte >= row_end)
      continue;
    U64 start = MAX(highlight->start_byte, row_start) - row_start;
    U64 end = MIN(highlight->end_byte, row_end) - row_start;
    S32 x0 = presentation_row->content_rect.x +
      (S32)((start * (U64)MAX(presentation_row->content_rect.w, 1)) / row_size);
    S32 x1 = presentation_row->content_rect.x +
      (S32)((end * (U64)MAX(presentation_row->content_rect.w, 1)) / row_size);
    (void)draw_push_rounded_rect(&app->draw_commands, DrawLayer_World,
                                 x0, presentation_row->row_rect.y,
                                 MAX(x1 - x0, 3), presentation_row->row_rect.h,
                                 2,
                                 lectern0_reader_highlight_color(app,
                                                                  highlight->color_index),
                                 lectern0_reader_highlight_color(app,
                                                                  highlight->color_index));
  }
  if (app->reader.has_active_selection &&
      app->reader.active_selection.spine_index == app->frame.spine_index &&
      app->reader.active_selection.text_byte_end > row_start &&
      app->reader.active_selection.text_byte_start < row_end)
  {
    U64 start = MAX(app->reader.active_selection.text_byte_start, row_start) - row_start;
    U64 end = MIN(app->reader.active_selection.text_byte_end, row_end) - row_start;
    S32 x0 = presentation_row->content_rect.x +
      (S32)((start * (U64)MAX(presentation_row->content_rect.w, 1)) / row_size);
    S32 x1 = presentation_row->content_rect.x +
      (S32)((end * (U64)MAX(presentation_row->content_rect.w, 1)) / row_size);
    U32 color = app->theme == Lectern0Theme_Dark ? 0x00604786U : 0x00BFD7FFU;
    (void)draw_push_rounded_rect(&app->draw_commands, DrawLayer_World,
                                 x0, presentation_row->row_rect.y,
                                 MAX(x1 - x0, 3), presentation_row->row_rect.h,
                                 2, color, color);
  }
}

FUNCTION void
lectern0_draw_reader_page(Lectern0App *app)
{
  UI0Rect viewport = app->reader_view_ready ? app->reader_view_layout.viewport_rect :
    ui0_rect(0, Lectern0ToolbarHeight, app->width,
             MAX(app->height - Lectern0ToolbarHeight - Lectern0FooterHeight, 1));
  S32 body_x = viewport.x + 48;
  S32 body_y = viewport.y + 22;
  S32 body_w = MAX(viewport.w - 96, 1);
  S32 body_h = MAX(viewport.h - 34, 1);
  U32 page_color = lectern0_reader_page_color(app);
  (void)draw_push_rounded_rect(&app->draw_commands,
                               DrawLayer_World,
                               body_x - 16,
                               body_y - 12,
                               body_w + 32,
                               body_h + 20,
                               8,
                               page_color,
                               app->theme == Lectern0Theme_Dark ?
                                 0x004B4740U : 0x00D8D2C8U);

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
                                 lectern0_reader_ink_color(app));
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
      lectern0_draw_row_highlights(app, row, presentation_row);
      String8 text = str8(app->frame.visible_text.str + start, end - start);
      U32 color = app->theme == Lectern0Theme_Dark ?
        lectern0_reader_ink_color(app) :
        (row->has_text_color ? row->text_color_rgb : lectern0_reader_ink_color(app));
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
  app->input.wheel_delta_y = 0;
  app->input.escape_pressed = 0;
  app->input.backspace_pressed = 0;
  app->input.delete_pressed = 0;
  app->input.commit_pressed = 0;
  app->input.select_all_pressed = 0;
  app->input.copy_pressed = 0;
  app->input.cut_pressed = 0;
  app->input.paste_pressed = 0;
  app->input.undo_pressed = 0;
  app->input.redo_pressed = 0;
  app->input.move_delta = 0;
  app->input.move_vertical_delta = 0;
  app->input.range_move = ReaderViewRangeMove_None;
  app->input.extend_selection = 0;
  app->input.text_length = 0;
  app->input.text[0] = 0;
}

FUNCTION void
lectern0_render_to_buffer(Lectern0App *app, RenderBuffer *buffer)
{
  if (!app || !buffer || !buffer->pixels || !app->render_ready) { return; }
  app->presentation_complete = 1;
  U32 canvas_color = app->theme == Lectern0Theme_Dark ? 0x00171412U :
                     app->theme == Lectern0Theme_Sepia ? 0x00DED2BAU :
                     0x00E9E5DEU;
  render_buffer_clear(buffer, canvas_color);
  draw_command_buffer_begin(&app->draw_commands);
  (void)lectern0_build_reader_view(app);
  lectern0_draw_reader_page(app);
  if (app->reader_view_ready) lectern0_adapt_ui0_draw(app);
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
  if (app->accessibility)
    lectern0_accessibility_publish_frame(app->accessibility,
                                         &app->reader_view_frame);
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

FUNCTION B32
lectern0_write_reader_view_parity(const char *path,
                                  const Lectern0App *app)
{
  ReaderViewDebugSnapshot snapshot = {0};
  if (!path || !path[0] || !app || !app->reader_view_ready ||
      !reader_view_debug_snapshot(&app->reader_view_projection,
                                  &app->reader_view_storage,
                                  &app->reader_view_frame,
                                  &snapshot))
    return 0;
  FILE *file = fopen(path, "wb");
  if (!file) return 0;
  const ReaderViewLayout *layout = &app->reader_view_frame.layout;
  int wrote = fprintf(
    file,
    "schema=reader_view_stage2b0_v1\n"
    "host=lectern0\n"
    "bounds=%d,%d\n"
    "layout_mode=%d\n"
    "toolbar_density=%d\n"
    "viewport=%d,%d,%d,%d\n"
    "projection_hash=%016llx\n"
    "layout_hash=%016llx\n"
    "control_hash=%016llx\n"
    "draw_hash=%016llx\n"
    "semantic_hash=%016llx\n"
    "action_hash=%016llx\n"
    "control_count=%d\n"
    "draw_count=%d\n"
    "semantic_count=%d\n"
    "action_count=%d\n",
    layout->bounds.w, layout->bounds.h,
    (int)layout->mode,
    (int)layout->toolbar_density,
    layout->viewport_rect.x - layout->bounds.x,
    layout->viewport_rect.y - layout->bounds.y,
    layout->viewport_rect.w,
    layout->viewport_rect.h,
    (unsigned long long)snapshot.projection_hash,
    (unsigned long long)snapshot.layout_hash,
    (unsigned long long)snapshot.control_hash,
    (unsigned long long)snapshot.draw_hash,
    (unsigned long long)snapshot.semantic_hash,
    (unsigned long long)snapshot.action_hash,
    snapshot.control_record_count,
    snapshot.draw_command_count,
    snapshot.semantic_node_count,
    snapshot.action_count);
  B32 result = wrote > 0 && fclose(file) == 0;
  return result;
}

FUNCTION B32
lectern0_configure_reader_view_parity(Lectern0App *app,
                                      const char *theme,
                                      const char *left,
                                      const char *right,
                                      const char *popup,
                                      const char *query)
{
  if (!app || !theme || !left || !right || !popup || !query) return 0;
  if (strcmp(theme, "light") == 0) app->theme = Lectern0Theme_Light;
  else if (strcmp(theme, "dark") == 0) app->theme = Lectern0Theme_Dark;
  else return 0;

  if (strcmp(left, "none") == 0)
    app->reader_view_state.left_panel = ReaderViewLeftPanel_None;
  else if (strcmp(left, "contents") == 0)
    app->reader_view_state.left_panel = ReaderViewLeftPanel_Contents;
  else if (strcmp(left, "find") == 0)
    app->reader_view_state.left_panel = ReaderViewLeftPanel_Find;
  else return 0;

  app->reader_view_state.right_panel_open = 0;
  if (strcmp(right, "open") == 0)
    app->reader_view_state.right_panel_open = 1;
  else if (strcmp(right, "bookmark") == 0)
  {
    if (!lectern0_toggle_current_bookmark(app)) return 0;
    app->reader_view_state.right_panel_open = 1;
  }
  else if (strcmp(right, "closed") != 0) return 0;

  app->reader_view_state.popup = ReaderViewPopup_None;
  if (strcmp(popup, "font") == 0)
  {
    app->reader_view_state.popup = ReaderViewPopup_SettingMenu;
    app->reader_view_state.active_setting_kind = ReaderViewSetting_FontFamily;
  }
  else if (strcmp(popup, "none") != 0) return 0;

  if (strcmp(query, "-") != 0)
  {
    size_t query_size = strlen(query);
    if (query_size >= READER_VIEW_FIND_QUERY_CAP) return 0;
    MemoryCopy(app->reader_view_state.find_query, query, (U64)query_size);
    app->reader_view_state.find_query[query_size] = 0;
    app->reader_view_state.find_query_length = (UI0S32)query_size;
    lectern0_apply_reader_view_action(app, &(ReaderViewAction){
      .kind = ReaderViewAction_FindChanged,
      .text = {(char *)query, (UI0S32)query_size},
    });
  }
  return 1;
}

FUNCTION int
lectern0_run_reader_view_parity_capture(const char *epub_path,
                                        const char *width_text,
                                        const char *height_text,
                                        const char *theme,
                                        const char *left,
                                        const char *right,
                                        const char *popup,
                                        const char *query,
                                        const char *evidence_path,
                                        const char *bmp_path)
{
  S32 width = (S32)atoi(width_text);
  S32 height = (S32)atoi(height_text);
  U64 pixel_count = (U64)(U32)width * (U64)(U32)height;
  if (width < 320 || height < 240 || width > 4096 || height > 4096 ||
      pixel_count > (U64)4096 * 4096)
    return 2;
  Lectern0App app = {0};
  U32 *pixels = (U32 *)calloc((size_t)pixel_count, sizeof(U32));
  if (!pixels || !lectern0_app_init(&app, width, height, 1, 0) ||
      !lectern0_open_path(&app, epub_path) ||
      !lectern0_configure_reader_view_parity(&app, theme, left, right,
                                             popup, query))
  {
    fprintf(stderr, "lectern0_reader_view_parity result=fail reason=setup\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, width, height, width);
  lectern0_render_to_buffer(&app, &buffer);
  lectern0_render_to_buffer(&app, &buffer);
  B32 wrote_evidence = lectern0_write_reader_view_parity(evidence_path, &app);
  B32 wrote_bmp = lectern0_write_bmp(bmp_path, pixels, width, height);
  if (!wrote_evidence || !wrote_bmp)
  {
    fprintf(stderr,
            "lectern0_reader_view_parity result=fail reason=write evidence=%d bmp=%d\n",
            wrote_evidence, wrote_bmp);
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  fprintf(stdout,
          "lectern0_reader_view_parity result=pass size=%dx%d theme=%s left=%s right=%s popup=%s query=%s\n",
          width, height, theme, left, right, popup, query);
  free(pixels);
  lectern0_app_release(&app);
  return 0;
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
        if (app->selection_dragging && app->input.pointer_down)
          lectern0_update_pointer_selection(app,
                                            app->input.pointer_x,
                                            app->input.pointer_y,
                                            0);
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
        lectern0_update_pointer_selection(app,
                                          app->input.pointer_x,
                                          app->input.pointer_y,
                                          1);
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
        if (app->selection_dragging)
        {
          lectern0_update_pointer_selection(app,
                                            app->input.pointer_x,
                                            app->input.pointer_y,
                                            0);
          app->selection_dragging = 0;
        }
        InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_GETOBJECT:
    {
      if (app && (LONG)l_param == OBJID_CLIENT)
      {
        if (!app->accessibility)
          (void)lectern0_accessibility_create(window,
                                               app,
                                               &app->accessibility);
        if (app->accessibility)
          return lectern0_accessibility_get_object(app->accessibility,
                                                   w_param,
                                                   l_param);
      }
    } break;

    case WM_MOUSEWHEEL:
    {
      if (app)
      {
        POINT point = {GET_X_LPARAM(l_param), GET_Y_LPARAM(l_param)};
        ScreenToClient(window, &point);
        app->input.pointer_x = point.x;
        app->input.pointer_y = point.y;
        app->input.wheel_delta_y += GET_WHEEL_DELTA_WPARAM(w_param) / WHEEL_DELTA;
        InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_CHAR:
    {
      if (app && w_param >= 32 && w_param != 127 &&
          (app->reader_view_state.left_panel == ReaderViewLeftPanel_Find ||
           app->reader_view_state.popup == ReaderViewPopup_NoteEditor))
      {
        lectern0_append_input_wchar(app, (wchar_t)w_param);
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
        B32 alt = (GetKeyState(VK_MENU) & 0x8000) != 0;
        B32 editing = app->reader_view_state.left_panel == ReaderViewLeftPanel_Find ||
                      app->reader_view_state.popup == ReaderViewPopup_NoteEditor;
        if (w_param == 'O' && control)
        {
          (void)lectern0_pick_epub(app);
        }
        else if (w_param == 'F' && control)
        {
          app->reader_view_state.left_panel = ReaderViewLeftPanel_Find;
          app->reader_view_state.most_recent_panel = ReaderViewPanel_Left;
        }
        else if (w_param == 'B' && control && epub_reader_is_open(&app->reader))
        {
          (void)lectern0_toggle_current_bookmark(app);
        }
        else if (w_param == VK_F11)
        {
          (void)lectern0_set_fullscreen(app, !app->fullscreen.active);
        }
        else if (w_param == VK_ESCAPE)
        {
          app->input.escape_pressed = 1;
        }
        else if (editing && control && w_param == 'A') app->input.select_all_pressed = 1;
        else if (editing && control && w_param == 'C') app->input.copy_pressed = 1;
        else if (editing && control && w_param == 'X') app->input.cut_pressed = 1;
        else if (editing && control && w_param == 'V')
        {
          app->input.paste_pressed = lectern0_get_clipboard_text(app);
        }
        else if (editing && control && w_param == 'Z') app->input.undo_pressed = 1;
        else if (editing && control && w_param == 'Y') app->input.redo_pressed = 1;
        else if (!editing && control && w_param == 'C' && app->selected_text[0])
        {
          (void)lectern0_set_clipboard_text(
            app, lectern0_reader_view_text(app->selected_text));
        }
        else if (editing && w_param == VK_BACK) app->input.backspace_pressed = 1;
        else if (editing && w_param == VK_DELETE) app->input.delete_pressed = 1;
        else if (editing && w_param == VK_RETURN)
        {
          if (app->reader_view_state.popup == ReaderViewPopup_NoteEditor)
            lectern0_append_input_wchar(app, L'\n');
          else
            app->input.commit_pressed = 1;
        }
        else if (editing && (w_param == VK_LEFT || w_param == VK_RIGHT))
        {
          app->input.move_delta = w_param == VK_LEFT ? -1 : 1;
          app->input.extend_selection = shift;
        }
        else if (editing && (w_param == VK_UP || w_param == VK_DOWN))
        {
          app->input.move_vertical_delta = w_param == VK_UP ? -1 : 1;
          app->input.extend_selection = shift;
        }
        else if (!editing &&
                 lectern0_reader_view_focus_is(app, ReaderViewSemantic_Slider) &&
                 (w_param == VK_LEFT || w_param == VK_RIGHT))
        {
          app->input.move_delta = w_param == VK_LEFT ? -1 : 1;
        }
        else if (!editing && app->reader_view_state.focus_id != 0 &&
                 (w_param == VK_UP || w_param == VK_DOWN))
        {
          app->input.move_vertical_delta = w_param == VK_UP ? -1 : 1;
        }
        else if (!editing && app->reader_view_state.focus_id != 0 &&
                 (w_param == VK_HOME || w_param == VK_END))
        {
          app->input.range_move = w_param == VK_HOME ?
            ReaderViewRangeMove_First : ReaderViewRangeMove_Last;
        }
        else if (alt && w_param == VK_LEFT)
        {
          (void)lectern0_move_history(app, 0);
        }
        else if (alt && w_param == VK_RIGHT)
        {
          (void)lectern0_move_history(app, 1);
        }
        else if (!editing && (w_param == VK_LEFT || w_param == VK_PRIOR))
        {
          (void)lectern0_move_page(app, -1);
        }
        else if (!editing &&
                 (w_param == VK_RIGHT || w_param == VK_NEXT || w_param == VK_SPACE))
        {
          (void)lectern0_move_page(app, 1);
        }
        else if (w_param == VK_TAB)
        {
          if (shift) { app->input.focus_prev_pressed = 1; }
          else { app->input.focus_next_pressed = 1; }
        }
        else if (!editing && w_param == VK_RETURN)
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
      if (app)
      {
        B32 needs_frame = app->reader_view_frame.action_count > 0 ||
          app->reader_view_frame.change_flags != ReaderViewFrameChange_None;
        lectern0_apply_reader_view_actions(app);
        if (needs_frame) InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_DESTROY:
    {
      if (app && app->accessibility)
        lectern0_accessibility_destroy(app->accessibility);
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

FUNCTION U64
lectern0_reader_view_hash_mix(U64 hash, U64 value)
{
  hash ^= value;
  hash *= 1099511628211ull;
  return hash;
}

FUNCTION U64
lectern0_reader_view_contract_hash(const ReaderViewFrame *frame)
{
  U64 hash = 1469598103934665603ull;
  if (!frame) return hash;
  hash = lectern0_reader_view_hash_mix(hash, (U64)frame->layout.mode);
  hash = lectern0_reader_view_hash_mix(hash, (U64)frame->layout.toolbar_density);
  hash = lectern0_reader_view_hash_mix(hash, (U64)(S64)frame->layout.viewport_rect.x);
  hash = lectern0_reader_view_hash_mix(hash, (U64)(S64)frame->layout.viewport_rect.y);
  hash = lectern0_reader_view_hash_mix(hash, (U64)(S64)frame->layout.viewport_rect.w);
  hash = lectern0_reader_view_hash_mix(hash, (U64)(S64)frame->layout.viewport_rect.h);
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
  {
    const ReaderViewSemanticNode *node = frame->semantic_nodes + index;
    hash = lectern0_reader_view_hash_mix(hash, node->id);
    hash = lectern0_reader_view_hash_mix(hash, node->parent_id);
    hash = lectern0_reader_view_hash_mix(hash, (U64)node->role);
    hash = lectern0_reader_view_hash_mix(hash, node->flags);
    hash = lectern0_reader_view_hash_mix(hash, node->source_key);
    if (node->name.data && node->name.size > 0)
      hash = lectern0_reader_view_hash_mix(
        hash, u64_hash_bytes(node->name.data, (U64)node->name.size));
  }
  return hash;
}

FUNCTION B32
lectern0_reader_view_has_semantic(const ReaderViewFrame *frame,
                                  const char *name)
{
  if (!frame || !name) return 0;
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
    if (lectern0_reader_view_text_is(frame->semantic_nodes[index].name, name))
      return 1;
  return 0;
}

FUNCTION B32
lectern0_reader_view_has_action(const ReaderViewFrame *frame,
                                ReaderViewActionKind kind)
{
  if (!frame || !frame->actions) return 0;
  for (UI0S32 index = 0; index < frame->action_count; index += 1)
    if (frame->actions[index].kind == kind) return 1;
  return 0;
}

FUNCTION int
lectern0_run_reader_view_smoke(const char *path, const char *export_path)
{
  enum { Width = 1100, Height = 760 };
  Lectern0App app = {0};
  U64 pixel_count = (U64)Width * Height;
  U32 *pixels = (U32 *)calloc((size_t)pixel_count, sizeof(U32));
  if (!pixels || !lectern0_app_init(&app, Width, Height, 1, 0) ||
      !lectern0_open_path(&app, path))
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=open\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  lectern0_render_to_buffer(&app, &buffer);
  if (!app.reader_view_ready ||
      app.reader_view_projection.settings.count != READER_VIEW_SETTING_CAP ||
      app.reader_view_projection.toc.row_count < 2 ||
      !lectern0_reader_view_has_semantic(&app.reader_view_frame, "Contents") ||
      !lectern0_reader_view_has_semantic(&app.reader_view_frame, "Find") ||
      !lectern0_reader_view_has_semantic(&app.reader_view_frame, "Bookmark") ||
      !lectern0_reader_view_has_semantic(&app.reader_view_frame, "Notes"))
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=chrome\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }

  U32 initial_spine = app.reader.active_spine_index;
  lectern0_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_ActivateTocRow,
    .key = 2,
  });
  if (app.reader.active_spine_index == initial_spine)
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=toc_action\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  lectern0_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_HistoryBack,
  });
  B32 history_back_ok = app.reader.active_spine_index == initial_spine;
  lectern0_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_HistoryForward,
  });
  B32 history_forward_ok = app.reader.active_spine_index != initial_spine;
  lectern0_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_HistoryBack,
  });
  if (!history_back_ok || !history_forward_ok ||
      app.reader.active_spine_index != initial_spine)
  {
    fprintf(stderr,
            "lectern0_reader_view_smoke result=fail reason=history_actions initial=%u current=%u back_ok=%d forward_ok=%d back_count=%u forward_count=%u\n",
            initial_spine,
            app.reader.active_spine_index,
            history_back_ok,
            history_forward_ok,
            app.reader.back_stack_count,
            app.reader.forward_stack_count);
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }

  lectern0_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SelectSetting,
    .setting_kind = ReaderViewSetting_FontSize,
    .key = 2001,
  });
  if (app.text_size_index != 1)
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=setting\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  lectern0_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SelectSetting,
    .setting_kind = ReaderViewSetting_LineSpacing,
    .key = 3001,
  });
  lectern0_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SelectSetting,
    .setting_kind = ReaderViewSetting_Theme,
    .key = 4002,
  });
  if (app.line_spacing_index != 1 || app.theme != Lectern0Theme_Dark)
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=settings\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  lectern0_prepare_reader_view_projection(&app);
  for (UI0S32 index = 0;
       index < app.reader_view_settings[0].choices.count;
       index += 1)
  {
    ReaderViewChoice choice = app.reader_view_settings[0].choices.items[index];
    if ((choice.flags & ReaderViewChoice_Selected) == 0)
    {
      U32 old_family = app.font_family;
      lectern0_apply_reader_view_action(&app, &(ReaderViewAction){
        .kind = ReaderViewAction_SelectSetting,
        .setting_kind = ReaderViewSetting_FontFamily,
        .key = choice.key,
      });
      if (app.font_family == old_family)
      {
        fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=font_setting\n");
        free(pixels);
        lectern0_app_release(&app);
        return 1;
      }
      break;
    }
  }
  lectern0_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_FindChanged,
    .text = {.data = "standalone", .size = 10},
  });
  if (app.reader.search_match_count == 0)
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=find\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  lectern0_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_ToggleBookmark,
  });
  if (app.bookmark_count != 1)
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=bookmark\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  lectern0_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_ToggleRightRowStar,
    .key = app.bookmarks[0].id,
    .right_row_kind = ReaderViewRightRow_Bookmark,
  });
  if (!app.bookmarks[0].starred)
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=bookmark_star\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }

  U64 relative_start = 0;
  while (relative_start < app.frame.visible_text.size &&
         app.frame.visible_text.str[relative_start] <= ' ')
    relative_start += 1;
  U64 relative_end = relative_start;
  for (U32 count = 0; count < 12 && relative_end < app.frame.visible_text.size; count += 1)
    relative_end = base_unicode_utf8_next_grapheme_boundary(app.frame.visible_text,
                                                            relative_end);
  DocSelection selection = {
    .spine_index = app.reader.active_spine_index,
    .text_byte_start = app.frame.view_byte_offset + relative_start,
    .text_byte_end = app.frame.view_byte_offset + relative_end,
  };
  if (relative_end <= relative_start ||
      epub_reader_set_selection(&app.reader, selection) != EpubReaderResult_Ok)
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=selection\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  app.selection_anchor_rect = ui0_rect(400, 180, 4, 24);
  lectern0_prepare_selected_text(&app);
  if (!lectern0_set_highlight_color(&app, 2))
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=highlight\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  U64 note_revision = app.annotation_revision;
  if (!lectern0_save_selection_note(&app,
                                    (ReaderViewText){"Smoke note", 10}))
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=note\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  (void)note_revision;
  lectern0_prepare_reader_view_projection(&app);
  if (app.reader_view_projection.right.row_count != 2 ||
      app.reader_view_projection.selection.current_color_key != 5002)
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=projection\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  lectern0_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_EditRightRowNote,
    .key = app.highlights[0].id,
    .right_row_kind = ReaderViewRightRow_Note,
  });
  if (app.reader_view_state.popup != ReaderViewPopup_NoteEditor ||
      !lectern0_reader_view_text_is(reader_view_note_draft(&app.reader_view_state),
                                    "Smoke note"))
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=direct_note_edit\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  app.reader_view_state.popup = ReaderViewPopup_None;
  app.reader_view_state.note_dirty = 0;
  app.reader_view_state.restore_focus_id = 0;
  epub_reader_clear_selection(&app.reader);

  app.persistence_enabled = 1;
  lectern0_copy_cstr(app.export_path, ARRAY_COUNT(app.export_path), export_path);
  (void)cstr_format(app.settings_path, ARRAY_COUNT(app.settings_path),
                    "%s.settings", export_path);
  (void)cstr_format(app.annotations_path, ARRAY_COUNT(app.annotations_path),
                    "%s.annotations", export_path);
  if (!lectern0_save_settings(&app) || !lectern0_save_annotations(&app) ||
      !lectern0_export_annotations(&app))
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=persistence\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  app.bookmark_count = 0;
  app.highlight_count = 0;
  lectern0_load_annotations(&app);
  if (app.bookmark_count != 1 || app.highlight_count != 1 ||
      strcmp(app.highlights[0].note, "Smoke note") != 0)
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=reload\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }

  Lectern0App failed_app = {0};
  char missing_path[Lectern0PathCap] = {0};
  (void)cstr_format(missing_path, ARRAY_COUNT(missing_path),
                    "%s.missing.epub", export_path);
  (void)DeleteFileA(missing_path);
  if (!lectern0_app_init(&failed_app, Width, Height, 1, 0) ||
      lectern0_open_path(&failed_app, missing_path))
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=error_setup\n");
    lectern0_app_release(&failed_app);
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  lectern0_render_to_buffer(&failed_app, &buffer);
  if (!failed_app.reader_view_ready ||
      failed_app.reader_view_projection.content.state != ReaderViewLoad_Error)
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=error_state\n");
    lectern0_app_release(&failed_app);
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  lectern0_app_release(&failed_app);

  app.width = 680;
  app.height = 620;
  lectern0_render_to_buffer(&app, &buffer);
  if (!app.reader_view_ready ||
      app.reader_view_layout.toolbar_density != ReaderViewToolbar_Overflow ||
      app.reader_view_layout.mode != ReaderViewLayout_Overlay)
  {
    fprintf(stderr, "lectern0_reader_view_smoke result=fail reason=responsive\n");
    free(pixels);
    lectern0_app_release(&app);
    return 1;
  }
  U64 hash = lectern0_reader_view_contract_hash(&app.reader_view_frame);
  fprintf(stdout,
          "lectern0_reader_view_smoke result=pass api=%d settings=4 toc=%d find=%u bookmarks=%u highlights=%u responsive=overflow hash=%016llx export=%s\n",
          READERVIEW0_API_VERSION,
          app.reader_view_projection.toc.row_count,
          app.reader.search_match_count,
          app.bookmark_count,
          app.highlight_count,
          (unsigned long long)hash,
          export_path);
  free(pixels);
  lectern0_app_release(&app);
  return 0;
}

FUNCTION int
lectern0_run_accessibility_smoke(const char *path)
{
  enum { Width = 1100, Height = 760 };
  static const wchar_t *ClassName = L"Lectern0AccessibilitySmokeWindow";
  Lectern0Win32 win32 = {0};
  U32 *pixels = (U32 *)calloc((size_t)Width * Height, sizeof(U32));
  if (!pixels || !lectern0_app_init(&win32.app, Width, Height, 1, 0) ||
      !lectern0_open_path(&win32.app, path))
  {
    fprintf(stderr, "lectern0_accessibility_smoke result=fail reason=open\n");
    free(pixels);
    lectern0_app_release(&win32.app);
    return 1;
  }
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  lectern0_render_to_buffer(&win32.app, &buffer);

  HINSTANCE instance = GetModuleHandleW(0);
  WNDCLASSW window_class = {0};
  window_class.lpfnWndProc = lectern0_win32_proc;
  window_class.hInstance = instance;
  window_class.lpszClassName = ClassName;
  if (!RegisterClassW(&window_class) && GetLastError() != ERROR_CLASS_ALREADY_EXISTS)
  {
    fprintf(stderr, "lectern0_accessibility_smoke result=fail reason=register\n");
    free(pixels);
    lectern0_app_release(&win32.app);
    return 1;
  }
  win32.window = CreateWindowExW(0, ClassName, L"lectern0 accessibility smoke",
                                  WS_OVERLAPPEDWINDOW,
                                  0, 0, Width, Height,
                                  0, 0, instance, &win32);
  if (!win32.window)
  {
    fprintf(stderr, "lectern0_accessibility_smoke result=fail reason=window\n");
    (void)UnregisterClassW(ClassName, instance);
    free(pixels);
    lectern0_app_release(&win32.app);
    return 1;
  }
  win32.app.window = win32.window;

  IAccessible *accessible = 0;
  HRESULT access_result = AccessibleObjectFromWindow(win32.window,
                                                       (DWORD)OBJID_CLIENT,
                                                       &IID_IAccessible,
                                                       (void **)&accessible);
  long child_count = 0;
  UI0S32 contents_index = -1;
  for (UI0S32 index = 0;
       index < win32.app.reader_view_frame.semantic_node_count;
       index += 1)
  {
    if (lectern0_reader_view_text_is(
          win32.app.reader_view_frame.semantic_nodes[index].name,
          "Contents"))
    {
      contents_index = index;
      break;
    }
  }

  VARIANT contents_child;
  VariantInit(&contents_child);
  contents_child.vt = VT_I4;
  contents_child.lVal = contents_index + 1;
  BSTR root_name = 0;
  BSTR contents_name = 0;
  VARIANT root_child;
  VariantInit(&root_child);
  root_child.vt = VT_I4;
  root_child.lVal = CHILDID_SELF;
  VARIANT role;
  VariantInit(&role);
  long left = 0;
  long top = 0;
  long width = 0;
  long height = 0;

  B32 valid = SUCCEEDED(access_result) && accessible && contents_index >= 0 &&
    SUCCEEDED(accessible->lpVtbl->get_accChildCount(accessible, &child_count)) &&
    child_count == win32.app.reader_view_frame.semantic_node_count &&
    SUCCEEDED(accessible->lpVtbl->get_accName(accessible, root_child, &root_name)) &&
    root_name && wcscmp(root_name, L"lectern0 EPUB reader") == 0 &&
    SUCCEEDED(accessible->lpVtbl->get_accName(accessible, contents_child,
                                              &contents_name)) &&
    contents_name && wcscmp(contents_name, L"Contents") == 0 &&
    SUCCEEDED(accessible->lpVtbl->get_accRole(accessible, contents_child, &role)) &&
    role.vt == VT_I4 &&
    (role.lVal == ROLE_SYSTEM_PUSHBUTTON || role.lVal == ROLE_SYSTEM_CHECKBUTTON) &&
    SUCCEEDED(accessible->lpVtbl->accLocation(accessible,
                                              &left, &top, &width, &height,
                                              contents_child)) &&
    width > 0 && height > 0 &&
    SUCCEEDED(accessible->lpVtbl->accSelect(accessible,
                                            SELFLAG_TAKEFOCUS,
                                            contents_child)) &&
    SUCCEEDED(accessible->lpVtbl->accDoDefaultAction(accessible,
                                                     contents_child));
  if (valid)
  {
    lectern0_render_to_buffer(&win32.app, &buffer);
    valid = win32.app.reader_view_state.left_panel == ReaderViewLeftPanel_Contents;
  }

  UI0S32 slider_index = -1;
  for (UI0S32 index = 0;
       valid && index < win32.app.reader_view_frame.semantic_node_count;
       index += 1)
  {
    if (win32.app.reader_view_frame.semantic_nodes[index].role ==
        ReaderViewSemantic_Slider)
    {
      slider_index = index;
      break;
    }
  }
  if (valid && slider_index >= 0)
  {
    VARIANT slider_child;
    VariantInit(&slider_child);
    slider_child.vt = VT_I4;
    slider_child.lVal = slider_index + 1;
    U64 before_offset = win32.app.reader.view_byte_offset;
    valid = SUCCEEDED(accessible->lpVtbl->accDoDefaultAction(accessible,
                                                             slider_child));
    if (valid)
    {
      lectern0_render_to_buffer(&win32.app, &buffer);
      valid = lectern0_reader_view_focus_is(&win32.app,
                                            ReaderViewSemantic_Slider);
    }
    if (valid)
    {
      win32.app.input.move_delta = 10;
      lectern0_render_to_buffer(&win32.app, &buffer);
      valid = lectern0_reader_view_has_action(&win32.app.reader_view_frame,
                                              ReaderViewAction_SeekLocation);
      lectern0_apply_reader_view_actions(&win32.app);
      valid = valid && win32.app.reader.view_byte_offset != before_offset;
    }
  }
  else
  {
    valid = 0;
  }

  if (valid)
  {
    lectern0_apply_reader_view_action(&win32.app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleDistractionFree,
    });
    valid = win32.app.distraction_free;
    lectern0_apply_reader_view_action(&win32.app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleDistractionFree,
    });
    valid = valid && !win32.app.distraction_free;
  }
  if (valid)
  {
    lectern0_apply_reader_view_action(&win32.app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleFullscreen,
    });
    valid = win32.app.fullscreen.active;
    lectern0_apply_reader_view_action(&win32.app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleFullscreen,
    });
    valid = valid && !win32.app.fullscreen.active;
  }

  long role_value = role.vt == VT_I4 ? role.lVal : 0;

  if (root_name) SysFreeString(root_name);
  if (contents_name) SysFreeString(contents_name);
  VariantClear(&role);
  if (accessible) (void)accessible->lpVtbl->Release(accessible);
  (void)DestroyWindow(win32.window);
  (void)UnregisterClassW(ClassName, instance);

  if (!valid)
  {
    fprintf(stderr,
            "lectern0_accessibility_smoke result=fail reason=contract hr=%08lx nodes=%ld contents=%d slider=%d role=%ld rect=%ldx%ld\n",
            (unsigned long)access_result,
            child_count,
            contents_index,
            slider_index,
            role_value,
            width,
            height);
    free(pixels);
    lectern0_app_release(&win32.app);
    return 1;
  }

  fprintf(stdout,
          "lectern0_accessibility_smoke result=pass adapter=msaa nodes=%ld contents_child=%d progress_child=%d role=%ld focus=shared action=shared progress=keyboard fullscreen=native distraction=shared\n",
          child_count,
          contents_index + 1,
          slider_index + 1,
          role_value);
  free(pixels);
  lectern0_app_release(&win32.app);
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
  else if (argc == 4 && strcmp(argv[1], "--reader-view-smoke") == 0)
  {
    result = lectern0_run_reader_view_smoke(argv[2], argv[3]);
  }
  else if (argc == 12 &&
           strcmp(argv[1], "--reader-view-parity-capture") == 0)
  {
    result = lectern0_run_reader_view_parity_capture(
      argv[2], argv[3], argv[4], argv[5], argv[6], argv[7], argv[8],
      argv[9], argv[10], argv[11]);
  }
  else if (argc == 3 && strcmp(argv[1], "--accessibility-smoke") == 0)
  {
    result = lectern0_run_accessibility_smoke(argv[2]);
  }
  else if (argc == 2 && strcmp(argv[1], "--version") == 0)
  {
    fprintf(stdout,
            "lectern0 %s reader0_api=%d ui0_api=%d readerview0_api=%d\n",
            LECTERN0_VERSION_STRING,
            READER0_API_VERSION,
            UI0_API_VERSION,
            READERVIEW0_API_VERSION);
  }
  else if (argc <= 2)
  {
    result = lectern0_run_window(argc == 2 ? argv[1] : 0);
  }
  else
  {
    fprintf(stderr,
            "usage: lectern0.exe [epub-path | --headless epub-path | --render-smoke epub-path bmp-path | --image-smoke epub-path cover-bmp inline-bmp | --reader-view-smoke epub-path export-path | --reader-view-parity-capture epub width height theme left right popup query evidence bmp | --accessibility-smoke epub-path | --version]\n");
    result = 2;
  }

  if (release_com) { CoUninitialize(); }
  return result;
}

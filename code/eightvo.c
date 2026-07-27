#include "eightvo_version.h"
#include "eightvo_library.h"
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
#include "text_engine/text_engine_display_row.h"
#include "text_engine/text_engine_display_span_row.h"

#if !defined(PRESENTATION_ENGINE_API_VERSION)
#  error "zero_foundation presentation_engine.h must define PRESENTATION_ENGINE_API_VERSION"
#endif
#if PRESENTATION_ENGINE_API_VERSION != 1
#  error "eightvo requires Presentation Engine API 1"
#endif
#if READER0_API_VERSION != 5
#  error "eightvo requires Reader0 API 5"
#endif
#if UI0_API_VERSION != 91
#  error "eightvo requires UI0 API 91"
#endif
#if READERVIEW0_API_VERSION != 3
#  error "eightvo requires Reader View API 3"
#endif

#include <commdlg.h>
#include <objbase.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <windows.h>
#include <windowsx.h>
#include <mmsystem.h>
#include <shellapi.h>
#include <oleacc.h>

#include "platform/win32/eightvo_accessibility_win32.h"

enum
{
  EightvoPathCap = 1024,
  EightvoStatusCap = 256,
  EightvoStateFileCap = 2304,
  EightvoImageCacheCap = 64,
  EightvoBookmarkCap = 128,
  EightvoHighlightCap = 128,
  EightvoReaderViewRightCandidateCap =
    EightvoBookmarkCap + EightvoHighlightCap * 2,
  EightvoRecordLabelCap = 160,
  EightvoNoteCap = READER_VIEW_NOTE_DRAFT_CAP,
  EightvoSelectionTextCap = 1024,
  EightvoInputTextCap = 128,
  EightvoClipboardCap = 2048,
  EightvoUrlCap = 4096,
  EightvoPresentationRowCap = EPUB_READER_FRAME_STYLE_ROW_CAP,
  EightvoPresentationMediaCap = EPUB_READER_FRAME_IMAGE_CAP,
  EightvoHostToolbarTrailingWidth = 38,
  EightvoHostToolbarSlotWidth = 30,
  EightvoReaderViewFindPriority_History = 2,
  EightvoReaderViewFindPriority_Committed = 4,
  EightvoReaderViewFindPriority_Placeholder = 5,
  EightvoReaderViewFindPriority_Current = 6,
  EightvoReaderViewFindPriority_Pinned = 255,
  EightvoReaderViewNotePixelHeight = 18,
  EightvoReaderViewNoteLineHeightFallback = 25,
  EightvoReaderViewNoteAdvanceFallback = 9,
  EightvoReaderViewNoteTerminalCaretGap = 1,
  EightvoDisplayRowStopCap = 1024,
  EightvoDisplaySpanCap = EPUB_READER_FRAME_STYLE_FRAGMENT_CAP * 2 + 1,
  EightvoLibraryVisibleCardCap = 64,
  EightvoLibraryHostControlCap = 1 + EightvoLibraryVisibleCardCap * 3,
  EightvoLibraryThumbnailCap = 48,
  EightvoLibraryThumbnailWidth = 256,
  EightvoLibraryThumbnailHeight = 384,
  EightvoLibraryThumbnailBudget = 24 * 1024 * 1024,
  EightvoLibraryThumbnailVersion = 2,
  EightvoPreparedImageCap = 16,
  EightvoPreparedImageBudget = 64 * 1024 * 1024,
  EightvoStateSaveTimerId = 1,
  EightvoStateSaveDelayMs = 250,
  EightvoAdjacentWarmTimerId = 2,
  EightvoAdjacentWarmDelayMs = 16,
  EightvoPresentationRetryTimerId = 3,
  EightvoPresentationRetryBaseDelayMs = 16,
  EightvoPresentationRetryMaxDelayMs = 256,
  EightvoLocationWarmTimerId = 4,
  EightvoLocationWarmDelayMs = 32,
  EightvoAdjacentWarmTextBudget = 4,
  EightvoAdjacentWarmPageCap = 4,
  EightvoAdjacentPagePixelCap = 4096 * 4096,
  EightvoAdjacentWarmIdleBudgetUs = 8000,
  EightvoAdjacentWarmFirstOpenBudgetUs = 12000,
  EightvoPageRepeatFrameRate = 60,
  EightvoPageRepeatInitialFrames = 24,
  EightvoPageRepeatIntervalFrames = 3,
  /* Frozen Re10 Win32 reader-key cadence at the accepted extracted baseline. */
  EightvoRe10PageRepeatFrameRate = 60,
  EightvoRe10PageRepeatInitialFrames = 24,
  EightvoRe10PageRepeatIntervalFrames = 3,
  EightvoPageRepeatProbeWidth = 1917,
  EightvoPageRepeatProbeHeight = 1137,
  EightvoPageRepeatProbeTimingToleranceMs = 200,
  EightvoPageRepeatProbeFrameBudgetMs = 64,
  EightvoPageRepeatProbeIntervalToleranceMs = 34,
  EightvoPageRepeatProbePersistenceWaitMs = 2000,
  EightvoPageRepeatProbeMoveCount = 12,
  EightvoPageRepeatProbePageCount = EightvoPageRepeatProbeMoveCount + 1,
  EightvoPageRepeatProbeBoundaryLeadPageCount = 6,
  EightvoPageRepeatProbeBoundarySearchCap = 64,
  EightvoGotmMinimumProseSpineBytes = 128,
  EightvoGotmMinimumProseTextBytes = 8,
  EightvoGotmMinimumProseTextRows = 1,
  EightvoPageRepeatQueueDrainCap = 32,
  EightvoPageRepeatProbeQueueDrainCap = EightvoPageRepeatQueueDrainCap,
  EightvoPageRepeatProbeMutationMessage = WM_APP + 41,
  EightvoPageRepeatProbeMutationCount = 7,
};

_Static_assert(EightvoPageRepeatFrameRate ==
                 EightvoRe10PageRepeatFrameRate,
               "Eightvo reader repeat frame rate must match frozen Re10");
_Static_assert(EightvoPageRepeatInitialFrames ==
                 EightvoRe10PageRepeatInitialFrames,
               "Eightvo reader repeat first delay must match frozen Re10");
_Static_assert(EightvoPageRepeatIntervalFrames ==
                 EightvoRe10PageRepeatIntervalFrames,
               "Eightvo reader repeat interval must match frozen Re10");

#define EIGHTVO_SETTINGS_MAGIC 0x4C30534554543231ull
#define EIGHTVO_ANNOTATION_MAGIC 0x4C30414E4E4F5431ull
#define EIGHTVO_LIBRARY_THUMBNAIL_MAGIC 0x4C305448554D4231ull

typedef struct EightvoImageCacheEntry
{
  DocDocumentId document_id;
  U32 resource_index;
  EpubReaderFrameImageStatus status;
  U32 *pixels;
  S32 width;
  S32 height;
  S32 stride_pixels;
} EightvoImageCacheEntry;

typedef struct EightvoPreparedImage
{
  const U32 *source_pixels;
  U32 *pixels;
  S32 source_width;
  S32 source_height;
  S32 source_stride_pixels;
  S32 width;
  S32 height;
  DrawSpriteSampleKind sample_kind;
  U64 pixel_bytes;
} EightvoPreparedImage;

typedef struct EightvoImageCache
{
  OS_ImageDecoder decoder;
  Arena *pixel_arena;
  Arena *prepared_arena;
  EightvoImageCacheEntry entries[EightvoImageCacheCap];
  EightvoPreparedImage prepared_images[EightvoPreparedImageCap];
  U32 entry_count;
  U32 prepared_image_count;
  U64 lookup_count;
  U64 hit_count;
  U64 miss_count;
  U64 cache_full_count;
  U64 prepared_cache_key;
  U64 prepared_pixel_bytes;
  U64 prepared_lookup_count;
  U64 prepared_hit_count;
  U64 prepared_build_count;
  U64 prepared_reset_count;
  U64 prepared_fallback_count;
  B32 decoder_ready;
} EightvoImageCache;

typedef struct EightvoLibraryThumbnailFile
{
  U64 magic;
  U32 version;
  U32 width;
  U32 height;
  U32 reserved;
  U64 entry_id;
  U64 file_size;
  U64 file_modified_time;
} EightvoLibraryThumbnailFile;

typedef struct EightvoLibraryThumbnail
{
  U64 entry_id;
  U64 file_size;
  U64 file_modified_time;
  U32 *pixels;
  S32 width;
  S32 height;
  S32 stride_pixels;
} EightvoLibraryThumbnail;

typedef struct EightvoLibraryThumbnailCache
{
  Arena *arena;
  EightvoLibraryThumbnail entries[EightvoLibraryThumbnailCap];
  U32 entry_count;
  U64 pixel_bytes;
} EightvoLibraryThumbnailCache;

typedef struct EightvoSavedState
{
  B32 valid;
  U32 spine_index;
  U64 byte_offset;
  char path[EightvoPathCap];
} EightvoSavedState;

typedef enum EightvoTheme
{
  EightvoTheme_Dark,
  EightvoTheme_Light,
  EightvoTheme_CoralDark,
  EightvoTheme_CoralLight,
  EightvoTheme_BlueDark,
  EightvoTheme_BlueLight,
  EightvoTheme_Count,
} EightvoTheme;

_Static_assert(EightvoTheme_Count == 6,
               "eightvo theme persistence catalog changed");
_Static_assert(UI0ThemeProfile_Count == 6,
               "eightvo must cover every shared UI0 profile");
_Static_assert((U32)EightvoReaderViewRightCandidateCap >=
                 (U32)READER_VIEW_RIGHT_ROW_CAP,
               "eightvo annotation candidates must cover Reader View rows");
_Static_assert(ReaderViewRightRow_Bookmark < ReaderViewRightRow_Highlight &&
                 ReaderViewRightRow_Highlight < ReaderViewRightRow_Note,
               "eightvo annotation sort requires frozen row-kind order");

typedef struct EightvoReaderContentTheme
{
  U32 page_background;
  U32 ink;
  U32 ink_secondary;
  U32 ink_muted;
  U32 link;
  U32 selection;
  U32 search_hit;
  U32 search_match;
  U32 user_highlight;
  U32 note_marker;
} EightvoReaderContentTheme;

typedef struct EightvoReaderSpanStyle
{
  DocTextStyleFlags flags;
  U32 scale_permille;
  S32 scale_px;
  U32 font_family_hint;
  U32 font_face_index;
  U32 color;
  TextEngineResolvedStyle resolved;
} EightvoReaderSpanStyle;

typedef struct EightvoReaderStyledRow
{
  TextEngineDisplaySpanRow display;
  U32 local_start;
  U32 local_end;
  U32 span_count;
  U32 stop_count;
  U32 justify_space_count;
  S32 justify_extra_px;
  U32 justify_extra_remainder;
  S32 natural_width;
  S32 fill_h;
} EightvoReaderStyledRow;

typedef struct EightvoDrawAdapterStats
{
  U32 op_count[UI0DrawOp_Count];
  U32 unsupported_count;
  U32 note_editable_row_count;
  U32 note_caret_remap_count;
} EightvoDrawAdapterStats;

typedef struct EightvoBookmarkV1
{
  U64 id;
  U32 spine_index;
  U64 byte_offset;
  B32 starred;
  char label[EightvoRecordLabelCap];
} EightvoBookmarkV1;

typedef struct EightvoBookmark
{
  U64 id;
  U32 spine_index;
  U64 byte_offset;
  B32 starred;
  char label[EightvoRecordLabelCap];
  char excerpt[EightvoRecordLabelCap];
} EightvoBookmark;

typedef struct EightvoHighlightV2
{
  U64 id;
  U32 spine_index;
  U64 start_byte;
  U64 end_byte;
  U32 color_index;
  B32 starred;
  B32 note_starred;
  char section[EightvoRecordLabelCap];
  char text[EightvoRecordLabelCap];
  char note[EightvoNoteCap];
} EightvoHighlightV2;

typedef struct EightvoHighlight
{
  U64 id;
  U32 spine_index;
  U64 start_byte;
  U64 end_byte;
  U32 color_index;
  B32 is_highlight;
  B32 starred;
  B32 note_starred;
  char section[EightvoRecordLabelCap];
  char text[EightvoRecordLabelCap];
  char note[EightvoNoteCap];
} EightvoHighlight;

typedef struct EightvoSettingsFileV2
{
  U64 magic;
  U32 version;
  U32 font_family;
  U32 text_size_index;
  U32 line_spacing_index;
  U32 theme;
} EightvoSettingsFileV2;

typedef struct EightvoSettingsFile
{
  U64 magic;
  U32 version;
  U32 font_family;
  U32 text_size_index;
  U32 line_spacing_index;
  U32 theme;
  U32 font_family_user_override;
} EightvoSettingsFile;

typedef struct EightvoAnnotationFile
{
  U64 magic;
  U32 version;
  U32 bookmark_count;
  U32 highlight_count;
  U32 reserved;
  U64 path_hash;
  U64 next_record_id;
  EightvoBookmark bookmarks[EightvoBookmarkCap];
  EightvoHighlight highlights[EightvoHighlightCap];
} EightvoAnnotationFile;

typedef struct EightvoAnnotationFileV1
{
  U64 magic;
  U32 version;
  U32 bookmark_count;
  U32 highlight_count;
  U32 reserved;
  U64 path_hash;
  U64 next_record_id;
  EightvoBookmarkV1 bookmarks[EightvoBookmarkCap];
  EightvoHighlightV2 highlights[EightvoHighlightCap];
} EightvoAnnotationFileV1;

typedef struct EightvoAnnotationFileV2
{
  U64 magic;
  U32 version;
  U32 bookmark_count;
  U32 highlight_count;
  U32 reserved;
  U64 path_hash;
  U64 next_record_id;
  EightvoBookmark bookmarks[EightvoBookmarkCap];
  EightvoHighlightV2 highlights[EightvoHighlightCap];
} EightvoAnnotationFileV2;

/* Tail padding makes the V2 and V3 record sizes equal; the file version and
   explicit field migration, not a size accident, distinguish their layouts. */
_Static_assert(sizeof(EightvoHighlightV2) == sizeof(EightvoHighlight),
               "eightvo annotation V2/V3 layout discriminator changed");

typedef struct EightvoFullscreen
{
  B32 active;
  DWORD style;
  DWORD ex_style;
  WINDOWPLACEMENT placement;
} EightvoFullscreen;

typedef struct EightvoInput
{
  S32 pointer_x;
  S32 pointer_y;
  B32 pointer_down;
  B32 pointer_pressed;
  B32 pointer_released;
  B32 pointer_selection_release;
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
  char text[EightvoInputTextCap];
  S32 text_length;
} EightvoInput;

enum
{
  EightvoUI0IconRasterCacheCap = 32,
  EightvoUI0IconRasterMaxWidth = UI0_ICON_RASTER_MAX_WIDTH,
  EightvoUI0IconRasterMaxHeight = UI0_ICON_RASTER_MAX_HEIGHT,
};

typedef struct EightvoUI0IconRasterCacheEntry
{
  UI0IconKind icon_kind;
  S32 width;
  S32 height;
  UI0Color foreground;
  UI0Color background;
  U32 pixels[EightvoUI0IconRasterMaxWidth *
             EightvoUI0IconRasterMaxHeight];
} EightvoUI0IconRasterCacheEntry;

typedef enum EightvoHostControlIdentity
{
  EightvoHostControl_None,
  EightvoHostControl_CloseBook,
  EightvoHostControl_ExitReader = EightvoHostControl_CloseBook,
  EightvoHostControl_LibraryAdd,
  EightvoHostControl_LibraryBookBase = 1024,
  EightvoHostControl_LibraryLocateBase =
    EightvoHostControl_LibraryBookBase + EightvoLibraryVisibleCardCap,
  EightvoHostControl_LibraryRemoveBase =
    EightvoHostControl_LibraryLocateBase + EightvoLibraryVisibleCardCap,
  EightvoHostControl_LibraryLimit =
    EightvoHostControl_LibraryRemoveBase + EightvoLibraryVisibleCardCap,
} EightvoHostControlIdentity;

typedef enum EightvoHostControlAction
{
  EightvoHostControlAction_None,
  EightvoHostControlAction_CloseBook,
  EightvoHostControlAction_AddBooks,
  EightvoHostControlAction_OpenBook,
  EightvoHostControlAction_LocateBook,
  EightvoHostControlAction_RemoveBook,
} EightvoHostControlAction;

typedef struct EightvoLibraryCardLayout
{
  U64 entry_id;
  UI0Rect card_rect;
  UI0Rect cover_rect;
  UI0Rect locate_rect;
  UI0Rect remove_rect;
  char accessibility_name[EightvoLibraryMetadataCap * 2];
} EightvoLibraryCardLayout;

typedef struct EightvoHostControlRecord
{
  EightvoHostControlIdentity identity;
  EightvoHostControlAction action;
  U64 entry_id;
  ReaderViewSemanticNode semantic;
} EightvoHostControlRecord;

typedef struct EightvoReaderViewRightSource
{
  ReaderViewKey key;
  ReaderViewRightRowKind row_kind;
  U64 record_id;
} EightvoReaderViewRightSource;

typedef struct EightvoReaderViewRightCandidate
{
  ReaderViewRightRowKind row_kind;
  U32 source_index;
  U32 spine_index;
  U64 byte_offset;
  U64 record_id;
} EightvoReaderViewRightCandidate;

typedef enum EightvoPresentationIdentityKind
{
  EightvoPresentationIdentity_None,
  EightvoPresentationIdentity_Library,
  EightvoPresentationIdentity_Page,
} EightvoPresentationIdentityKind;

/* Pointer-free subset of the canonical Reader0 page range retained for
   presentation identity and frame validation. */
typedef struct EightvoCanonicalPageIdentity
{
  U32 spine_index;
  U64 spine_page_index;
  U64 spine_page_count;
  U64 first_byte;
  U64 one_past_last_byte;
} EightvoCanonicalPageIdentity;

typedef struct EightvoPresentationIdentity
{
  EightvoPresentationIdentityKind kind;
  DocDocumentId document_id;
  U64 document_generation;
  U64 layout_generation;
  U64 frame_generation;
  U64 frame_capture_generation;
  U64 reader_frame_generation;
  U64 image_visual_identity;
  U32 image_count;
  EightvoCanonicalPageIdentity page;
} EightvoPresentationIdentity;

typedef struct EightvoApp
{
  Arena *arena;
  EpubReader reader;
  EpubReaderFrameStorage frame_storage;
  EpubReaderFrame frame;
  EpubReaderFrameStorage *adjacent_frame_storage;
  EpubReaderFrame adjacent_frame;
  EpubReaderLayoutKey layout_key;
  SourceReaderLayoutConfig layout_config;
  EightvoImageCache image_cache;
  EightvoLibraryThumbnailCache library_thumbnail_cache;
  PresentationEngineBlockFlowRowSpec presentation_row_specs[EightvoPresentationRowCap];
  PresentationEngineBlockFlowMediaSpec presentation_media_specs[EightvoPresentationMediaCap];
  PresentationEngineBlockFlowRow presentation_rows[EightvoPresentationRowCap];
  PresentationEngineBlockFlowMedia presentation_media[EightvoPresentationMediaCap];
  PresentationEngineBlockFlowFrame presentation_frame;
  U64 presentation_hash;

  RenderState render_state;
  DrawCommandBuffer draw_commands;
  OS_GfxContext gfx;
  B32 render_ready;
  B32 presentation_complete;
  B32 gfx_ready;
  B32 last_present_complete;
  U64 complete_present_sequence;
  HWND window;
  EightvoAccessibility *accessibility;
  S32 width;
  S32 height;

  ReaderViewState reader_view_state;
  ReaderViewFrameStorage reader_view_storage;
  ReaderViewLayout reader_view_layout;
  ReaderViewContentGeometry reader_content_geometry;
  ReaderViewFrame reader_view_frame;
  ReaderViewProjection reader_view_projection;
  ReaderViewSettingControl reader_view_settings[READER_VIEW_SETTING_CAP];
  ReaderViewChoice reader_view_font_choices[8];
  ReaderViewChoice reader_view_size_choices[4];
  ReaderViewChoice reader_view_spacing_choices[3];
  ReaderViewChoice reader_view_theme_choices[EightvoTheme_Count];
  ReaderViewChoice reader_view_color_choices[READER_VIEW_HIGHLIGHT_COLOR_CAP];
  ReaderViewTocRow reader_view_toc_rows[READER_VIEW_TOC_ROW_CAP];
  ReaderViewFindRow reader_view_find_rows[READER_VIEW_FIND_ROW_CAP];
  ReaderViewRightRow reader_view_right_rows[READER_VIEW_RIGHT_ROW_CAP];
  char reader_view_right_secondary[READER_VIEW_RIGHT_ROW_CAP]
                                  [EightvoRecordLabelCap];
  EightvoReaderViewRightSource
    reader_view_right_sources[READER_VIEW_RIGHT_ROW_CAP];
  U32 reader_view_right_source_count;
  EightvoReaderViewRightCandidate
    reader_view_right_candidates[EightvoReaderViewRightCandidateCap];
  U32 reader_view_right_candidate_count;
  /* Exact host-owned target for a note editor opened from Annotations. */
  ReaderViewKey annotation_note_selection_key;
  DocDocumentId annotation_note_document_id;
  U64 annotation_note_highlight_id;
  U64 annotation_note_start_byte;
  U64 annotation_note_end_byte;
  U32 annotation_note_spine_index;
  /* Bounded caller-measured values consumed by Reader View Find. */
  ReaderViewCodepointAdvance
    find_text_advances[READER_VIEW_FIND_CODEPOINT_ADVANCE_CAP];
  U64 find_text_advance_last_seen[READER_VIEW_FIND_CODEPOINT_ADVANCE_CAP];
  U8 find_text_advance_priority[READER_VIEW_FIND_CODEPOINT_ADVANCE_CAP];
  U32 find_text_advance_count;
  U64 find_text_metrics_generation;
  S32 find_text_fallback_advance;
  B32 find_text_metrics_initialized;
  /* One-build values for the proportional shared Note TextArea. */
  ReaderViewCodepointAdvance
    note_text_advances[READER_VIEW_NOTE_CODEPOINT_ADVANCE_CAP];
  U32 note_text_advance_count;
  char document_title[EightvoRecordLabelCap];
  char progress_label[64];
  char reader_view_find_status[64];
  char selected_text[EightvoSelectionTextCap];
  ReaderViewLoadState document_state;
  UI0ResolvedTheme reader_view_theme;
  EightvoReaderContentTheme reader_content_theme;
  EightvoDrawAdapterStats draw_adapter_stats;
  EightvoUI0IconRasterCacheEntry
    ui0_icon_rasters[EightvoUI0IconRasterCacheCap];
  U32 ui0_icon_raster_count;
  U64 reader_view_frame_index;
  B32 reader_view_ready;
  S32 pagination_viewport_width;
  S32 pagination_viewport_height;
  S32 reader_margin_line_height;
  EightvoHostControlRecord
    host_controls[EightvoLibraryHostControlCap];
  U32 host_control_count;
  EightvoHostControlIdentity host_focus_control;
  B32 host_focus_visible;
  B32 host_exit_pointer_armed;
  EightvoHostControlIdentity host_pointer_armed;

  EightvoLibraryCatalog library;
  EightvoLibraryCardLayout library_cards[EightvoLibraryVisibleCardCap];
  U32 library_card_count;
  U64 library_selected_entry_id;
  U64 library_locate_entry_id;
  S32 library_scroll_y;
  S32 library_scroll_max;
  S32 library_column_count;
  B32 library_import_in_progress;
  B32 suppress_native_picker;
  U32 native_picker_request_count;
  U32 library_metadata_refresh_count;

  EightvoBookmark bookmarks[EightvoBookmarkCap];
  U32 bookmark_count;
  EightvoHighlight highlights[EightvoHighlightCap];
  U32 highlight_count;
  U64 next_record_id;
  U64 annotation_revision;
  U32 font_family;
  B32 font_family_user_override;
  U32 text_size_index;
  U32 line_spacing_index;
  EightvoTheme theme;
  B32 distraction_free;
  EightvoFullscreen fullscreen;
  TextEngineDisplaySpan reader_display_spans[EightvoDisplaySpanCap];
  EightvoReaderSpanStyle reader_span_styles[EightvoDisplaySpanCap];
  TextEngineDisplayRowStop reader_display_stops[EightvoDisplayRowStopCap];
  B32 selection_dragging;
  U64 selection_anchor_byte;
  UI0Rect selection_anchor_rect;
  char clipboard_text[EightvoClipboardCap];
  S32 clipboard_length;
  UI0TextInputTransferBuffer clipboard_transfer;
  wchar_t pending_high_surrogate;
  EightvoInput input;

  B32 persistence_enabled;
  B32 state_save_pending;
  B32 first_reader_present_pending;
  B32 location_warm_pending;
  B32 adjacent_warm_pending;
  B32 adjacent_warm_frame_ready;
  U32 adjacent_warm_next_text_command;
  U32 adjacent_warm_distance;
  U32 adjacent_warm_completed_page_count;
  S32 adjacent_warm_direction;
  U32 adjacent_warm_source_spine_index;
  U64 adjacent_warm_source_first_byte;
  U32 *adjacent_page_pixels;
  U64 adjacent_page_pixel_cap;
  U64 adjacent_page_visual_key;
  U64 adjacent_page_annotation_revision;
  UI0Rect adjacent_page_rect;
  S32 adjacent_page_buffer_width;
  S32 adjacent_page_buffer_height;
  U32 adjacent_page_font_family;
  B32 adjacent_page_embedded_fonts_enabled;
  U32 adjacent_page_text_size_index;
  U32 adjacent_page_line_spacing_index;
  EightvoTheme adjacent_page_theme;
  B32 adjacent_page_ready;
  B32 page_repeat_active;
  S32 page_repeat_direction;
  WPARAM page_repeat_key;
  U64 page_repeat_next_move_ticks;
  U64 page_repeat_last_action_emitted_ticks;
  B32 page_action_waiting_for_present;
  B32 page_action_internal_dispatch;
  B32 page_action_reflow_deferred;
  B32 page_action_pending;
  B32 page_action_pending_arm_repeat;
  S32 page_action_pending_direction;
  WPARAM page_action_pending_key;
  U64 page_action_emitted_count;
  U64 page_action_presented_count;
  U64 page_action_frame_generation;
  U64 page_action_last_stable_present_ticks;
  EightvoPresentationIdentity page_action_expected_identity;
  EightvoPresentationIdentity last_surface_identity;
  U32 page_action_overlap_count;
  U32 page_action_identity_mismatch_count;
  U32 page_action_presentation_retry_attempt;
  U32 page_action_presentation_retry_scheduled_count;
  U32 page_action_presentation_retry_fired_count;
  U32 page_action_mutation_drop_count;
  U32 capture_frame_fail_count;
  B32 capture_frame_failed_since_mutation;
  U32 capture_frame_recovery_count;
  U64 frame_capture_generation;
  U32 page_repeat_frame_move_count;
  U32 page_repeat_native_coalesced_count;
  U32 page_repeat_presented_frame_count;
  B32 page_repeat_navigation_prepare_pending;
  S32 page_repeat_navigation_prepare_direction;
  U32 page_repeat_navigation_prepare_source_spine_index;
  U64 page_repeat_navigation_prepare_source_first_byte;
  U64 page_repeat_navigation_prepare_call_count;
  U64 page_repeat_navigation_prepare_build_count;
  U64 page_repeat_navigation_prepare_ready_count;
  U64 page_repeat_navigation_prepare_cross_spine_ready_count;
  U64 page_repeat_navigation_prepare_fail_count;
  U64 page_repeat_navigation_prepare_total_ticks;
  U64 page_repeat_navigation_prepare_max_ticks;
  WPARAM page_repeat_cancelled_key;
  U32 page_repeat_cancelled_repeat_consumed_count;
  U32 page_repeat_modifier_cancel_count;
  U32 page_repeat_focus_cancel_count;
  U32 page_repeat_deactivate_cancel_count;
  U32 page_repeat_keyup_cancel_count;
  U32 page_repeat_mutation_cancel_count;
  U32 page_repeat_persistence_deferred_count;
  U32 page_repeat_persistence_rescheduled_count;
  U64 last_render_acquire_ticks;
  U64 last_render_buffer_ticks;
  U64 last_render_accessibility_ticks;
  U64 last_render_present_ticks;
  U64 last_render_reader_view_ticks;
  U64 last_render_reader_page_ticks;
  U64 last_render_ui_adapt_ticks;
  U64 last_render_execute_ticks;
  U32 state_save_transaction_success_count;
  B32 adjacent_page_cache_used_last_render;
  EightvoSavedState saved;
  char app_directory[EightvoPathCap];
  char state_path[EightvoPathCap];
  char catalog_path[EightvoPathCap];
  char settings_path[EightvoPathCap];
  char annotations_path[EightvoPathCap];
  char export_path[EightvoPathCap];
  char current_path[EightvoPathCap];
  char status[EightvoStatusCap];
} EightvoApp;

typedef struct EightvoPageRepeatFrameResult
{
  B32 action_due;
  B32 action_emitted;
  B32 action_waiting_for_render;
} EightvoPageRepeatFrameResult;

typedef struct EightvoPageRepeatFrameTiming
{
  U64 action_prepare_ticks;
  U64 action_emitted_ticks;
  U64 render_acquire_ticks;
  U64 render_buffer_ticks;
  U64 render_accessibility_ticks;
  U64 render_present_ticks;
} EightvoPageRepeatFrameTiming;

typedef struct EightvoWin32
{
  EightvoApp app;
  HWND window;
  HWND page_repeat_probe_paint_window;
  U32 page_repeat_probe_aux_paint_pending_count;
  U32 page_repeat_probe_aux_paint_dispatch_count;
  U32 page_repeat_probe_main_null_paint_pending_count;
  U32 page_repeat_probe_main_null_paint_dispatch_count;
  B32 page_repeat_probe_track_paints;
} EightvoWin32;

typedef enum EightvoPageRepeatWin32ProbeKind
{
  EightvoPageRepeatWin32Probe_Direction,
  EightvoPageRepeatWin32Probe_FocusLoss,
  EightvoPageRepeatWin32Probe_ControlModifier,
  EightvoPageRepeatWin32Probe_ShiftModifier,
  EightvoPageRepeatWin32Probe_SystemModifier,
  EightvoPageRepeatWin32Probe_Deactivation,
  EightvoPageRepeatWin32Probe_MutationGate,
} EightvoPageRepeatWin32ProbeKind;

typedef struct EightvoPageRepeatWin32Probe
{
  B32 enabled;
  B32 started;
  B32 stop_posted;
  B32 completed;
  B32 failed;
  EightvoPageRepeatWin32ProbeKind kind;
  S32 direction;
  WPARAM key;
  U32 target_frame_move_count;
  U32 frame_cap;
  U32 frame_count;
  U32 native_repeats_per_frame;
  U32 native_repeat_posted_count;
  U32 cancelled_repeat_posted_count;
  U32 logical_frame_count;
  U64 start_ticks;
  U64 elapsed_ticks;
  U64 frame_max_ticks;
  U64 cold_render_ticks;
  U64 cold_present_ticks;
  U64 repeat_prepare_total_ticks;
  U64 repeat_prepare_max_ticks;
  U64 repeat_render_total_ticks;
  U64 repeat_render_max_ticks;
  U64 repeat_present_total_ticks;
  U64 repeat_present_max_ticks;
  U32 repeat_timing_count;
  U64 navigation_prepare_call_count;
  U64 navigation_prepare_build_count;
  U64 navigation_prepare_ready_count;
  U64 navigation_prepare_cross_spine_ready_count;
  U64 navigation_prepare_fail_count;
  U64 navigation_prepare_total_ticks;
  U64 navigation_prepare_max_ticks;
  U32 prepared_window_move_count;
  U32 synchronous_window_rebuild_move_count;
  U32 synchronous_adjacent_measured_move_count;
  U32 cross_spine_boundary_ring_move_count;
  U64 first_move_elapsed_ticks;
  U64 last_move_elapsed_ticks;
  U64 move_interval_min_ticks;
  U64 move_interval_max_ticks;
  U64 move_interval_total_ticks;
  U32 move_interval_count;
  U32 observed_move_count;
  U64 stable_present_elapsed_ticks[EightvoPageRepeatProbePageCount];
  U64 stable_present_transition_ticks[EightvoPageRepeatProbeMoveCount];
  B32 stable_present_transition_cross_spine[
    EightvoPageRepeatProbeMoveCount];
  U32 stable_present_transition_count;
  U32 stable_present_count;
  U64 stable_present_first_delay_ticks;
  U64 stable_present_interval_min_ticks;
  U64 stable_present_interval_max_ticks;
  U64 stable_present_interval_total_ticks;
  U32 stable_present_interval_count;
  SourceReaderPageRange start_page;
  SourceReaderPageRange expected_pages[EightvoPageRepeatProbePageCount];
  SourceReaderPageRange actual_pages[EightvoPageRepeatProbePageCount];
  U32 expected_page_count;
  U32 actual_page_count;
  U32 canonical_match_count;
  U32 cross_spine_transition_count;
  U32 valid_frame_count;
  U32 zero_page_or_frame_count;
  U32 orphan_text_page_count;
  U32 invalid_word_start_page_count;
  U32 text_frame_count;
  U64 minimum_text_bytes;
  U32 minimum_text_rows;
  U64 page_action_emitted_count;
  U64 page_action_presented_count;
  U32 page_action_overlap_count;
  U64 page_action_emitted_before;
  U64 page_action_presented_before;
  U32 page_action_overlap_before;
  U32 page_action_identity_mismatch_before;
  U32 page_action_identity_mismatch_count;
  U32 page_action_mutation_drop_before;
  U32 page_action_mutation_drop_count;
  U32 queue_drain_batch_max_count;
  U32 queue_drain_message_count;
  U32 auxiliary_paint_dispatch_before;
  U32 auxiliary_paint_dispatch_count;
  U32 main_null_paint_dispatch_before;
  U32 main_null_paint_dispatch_count;
  B32 paint_messages_posted;
  U32 native_repeat_coalesced_count;
  U32 cancelled_repeat_consumed_count;
  U32 modifier_cancel_count;
  U32 focus_cancel_count;
  U32 deactivate_cancel_count;
  U32 keyup_cancel_count;
  U32 mutation_cancel_count;
  U32 persistence_deferred_count;
  U32 persistence_rescheduled_count;
  U32 persistence_transaction_success_before;
  U32 persistence_transaction_success_during_hold;
  U32 persistence_transaction_success_after;
  U64 persistence_state_modified_time_before;
  U64 persistence_state_modified_time_during_hold;
  U64 persistence_state_modified_time_after;
  U64 persistence_state_content_hash_before;
  U64 persistence_state_content_hash_during_hold;
  U64 persistence_state_content_hash_after;
  U64 persistence_catalog_modified_time_before;
  U64 persistence_catalog_modified_time_during_hold;
  U64 persistence_catalog_modified_time_after;
  U64 persistence_catalog_content_hash_before;
  U64 persistence_catalog_content_hash_during_hold;
  U64 persistence_catalog_content_hash_after;
  U64 persistence_wait_deadline_ticks;
  B32 persistence_baseline_ready;
  B32 persistence_hold_checked;
  B32 persistence_hold_unchanged;
  B32 persistence_post_stop_advanced;
} EightvoPageRepeatWin32Probe;

FUNCTION void eightvo_library_update_progress(EightvoApp *app);
FUNCTION B32 eightvo_close_book(EightvoApp *app);
FUNCTION B32 eightvo_pick_epub(EightvoApp *app);
FUNCTION B32 eightvo_locate_library_entry(EightvoApp *app, U64 entry_id);
FUNCTION void eightvo_schedule_adjacent_warm(EightvoApp *app);
FUNCTION void eightvo_cancel_adjacent_warm(EightvoApp *app);
FUNCTION B32 eightvo_adjacent_warm_step(EightvoApp *app);
FUNCTION void eightvo_schedule_location_warm(EightvoApp *app);
FUNCTION void eightvo_cancel_location_warm(EightvoApp *app);
FUNCTION B32 eightvo_location_warm_step(EightvoApp *app);
FUNCTION void eightvo_invalidate_adjacent_page(EightvoApp *app);
FUNCTION void eightvo_stop_page_repeat(EightvoApp *app);
FUNCTION void eightvo_cancel_page_repeat_for_modifier(EightvoApp *app);
FUNCTION void eightvo_cancel_page_repeat_for_focus(EightvoApp *app);
FUNCTION void eightvo_cancel_page_repeat_for_deactivation(EightvoApp *app);
FUNCTION void eightvo_cancel_page_repeat_for_mutation(EightvoApp *app);
FUNCTION void eightvo_page_action_note_emitted(EightvoApp *app);
FUNCTION B32 eightvo_begin_document_mutation(EightvoApp *app);
FUNCTION void eightvo_complete_document_mutation(EightvoApp *app,
                                                   B32 changed);
FUNCTION void eightvo_page_action_clear_pending(EightvoApp *app);
FUNCTION void eightvo_page_action_defer(EightvoApp *app,
                                          WPARAM key,
                                          S32 direction,
                                          B32 arm_repeat);
FUNCTION void eightvo_page_action_release_key(EightvoApp *app, WPARAM key);
FUNCTION void eightvo_start_page_repeat(EightvoApp *app,
                                         WPARAM key,
                                         S32 direction);
FUNCTION B32 eightvo_page_repeat_step(EightvoApp *app,
                                        U64 now_ticks,
                                        B32 *out_action_due);
FUNCTION EightvoPageRepeatFrameResult
eightvo_page_repeat_frame_step(EightvoApp *app, U64 now_ticks);
FUNCTION void eightvo_page_repeat_note_presented_frame(EightvoApp *app,
                                                         B32 complete);
FUNCTION B32 eightvo_page_repeat_prepare_navigation_tail(EightvoApp *app);
FUNCTION B32 eightvo_fit_image_rect(S32 src_w, S32 src_h,
                                     S32 rect_x, S32 rect_y,
                                     S32 rect_w, S32 rect_h,
                                     S32 *out_x, S32 *out_y,
                                     S32 *out_w, S32 *out_h);

FUNCTION void
eightvo_copy_cstr(char *dst, U64 cap, const char *src)
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
eightvo_set_statusf(EightvoApp *app, const char *fmt, ...)
{
  if (!app || !fmt) { return; }
  va_list args;
  va_start(args, fmt);
  (void)cstr_formatv(app->status, ARRAY_COUNT(app->status), fmt, args);
  va_end(args);
}

FUNCTION B32
eightvo_legacy_data_file_name_is_supported(const char *name)
{
  if (!name || !name[0]) return 0;
  if (_stricmp(name, "state.v1") == 0 ||
      _stricmp(name, "library.v1") == 0 ||
      _stricmp(name, "settings.v1") == 0 ||
      _stricmp(name, "reader_annotations.txt") == 0)
  {
    return 1;
  }
  U64 length = strlen(name);
  const char *suffix = ".v1";
  U64 suffix_length = strlen(suffix);
  if (length <= suffix_length ||
      _stricmp(name + length - suffix_length, suffix) != 0)
  {
    return 0;
  }
  return _strnicmp(name, "annotations_", strlen("annotations_")) == 0 ||
         _strnicmp(name, "thumbnail_", strlen("thumbnail_")) == 0;
}

FUNCTION B32
eightvo_migrate_legacy_data(const char *legacy_directory,
                             const char *app_directory)
{
  if (!legacy_directory || !legacy_directory[0] ||
      !app_directory || !app_directory[0])
  {
    return 0;
  }

  char marker_path[EightvoPathCap] = {0};
  if (cstr_format(marker_path, ARRAY_COUNT(marker_path),
                  "%s\\migration_from_lectern0.v1", app_directory) == 0)
  {
    return 0;
  }
  DWORD marker_attributes = GetFileAttributesA(marker_path);
  if (marker_attributes != INVALID_FILE_ATTRIBUTES &&
      !(marker_attributes & FILE_ATTRIBUTE_DIRECTORY))
  {
    return 1;
  }

  DWORD legacy_attributes = GetFileAttributesA(legacy_directory);
  if (legacy_attributes == INVALID_FILE_ATTRIBUTES)
  {
    return GetLastError() == ERROR_FILE_NOT_FOUND ||
           GetLastError() == ERROR_PATH_NOT_FOUND;
  }
  if (!(legacy_attributes & FILE_ATTRIBUTE_DIRECTORY)) return 0;

  char pattern[EightvoPathCap] = {0};
  if (cstr_format(pattern, ARRAY_COUNT(pattern),
                  "%s\\*", legacy_directory) == 0)
  {
    return 0;
  }

  WIN32_FIND_DATAA find_data = {0};
  HANDLE find = FindFirstFileA(pattern, &find_data);
  if (find == INVALID_HANDLE_VALUE)
  {
    return GetLastError() == ERROR_FILE_NOT_FOUND;
  }

  B32 migrated = 1;
  do
  {
    if ((find_data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) ||
        (find_data.dwFileAttributes & FILE_ATTRIBUTE_REPARSE_POINT) ||
        !eightvo_legacy_data_file_name_is_supported(find_data.cFileName))
    {
      continue;
    }

    char source_path[EightvoPathCap] = {0};
    char destination_path[EightvoPathCap] = {0};
    char temporary_path[EightvoPathCap] = {0};
    if (cstr_format(source_path, ARRAY_COUNT(source_path),
                    "%s\\%s", legacy_directory, find_data.cFileName) == 0 ||
        cstr_format(destination_path, ARRAY_COUNT(destination_path),
                    "%s\\%s", app_directory, find_data.cFileName) == 0 ||
        cstr_format(temporary_path, ARRAY_COUNT(temporary_path),
                    "%s.migrating", destination_path) == 0)
    {
      migrated = 0;
      break;
    }

    DWORD destination_attributes = GetFileAttributesA(destination_path);
    if (destination_attributes != INVALID_FILE_ATTRIBUTES)
    {
      if (destination_attributes & FILE_ATTRIBUTE_DIRECTORY)
      {
        migrated = 0;
        break;
      }
      continue;
    }

    (void)DeleteFileA(temporary_path);
    if (!CopyFileA(source_path, temporary_path, TRUE) ||
        !MoveFileExA(temporary_path, destination_path, MOVEFILE_WRITE_THROUGH))
    {
      (void)DeleteFileA(temporary_path);
      migrated = 0;
      break;
    }
  } while (FindNextFileA(find, &find_data));

  if (migrated && GetLastError() != ERROR_NO_MORE_FILES)
    migrated = 0;
  (void)FindClose(find);
  if (!migrated) return 0;

  static const char marker[] = "8vo legacy data migration v1\n";
  return os_write_entire_file_atomic(marker_path, marker, sizeof(marker) - 1);
}

FUNCTION B32
eightvo_state_paths(EightvoApp *app)
{
  if (!app) { return 0; }
  char local_app_data[EightvoPathCap] = {0};
  DWORD size = GetEnvironmentVariableA("LOCALAPPDATA",
                                       local_app_data,
                                       (DWORD)ARRAY_COUNT(local_app_data));
  if (size == 0 || size >= ARRAY_COUNT(local_app_data)) { return 0; }
  char app_directory[EightvoPathCap] = {0};
  char legacy_directory[EightvoPathCap] = {0};
  if (cstr_format(app_directory, ARRAY_COUNT(app_directory),
                  "%s\\8vo", local_app_data) == 0 ||
      cstr_format(legacy_directory, ARRAY_COUNT(legacy_directory),
                  "%s\\lectern0", local_app_data) == 0 ||
      !os_make_directory_chain(app_directory) ||
      !eightvo_migrate_legacy_data(legacy_directory, app_directory) ||
      cstr_format(app->app_directory, ARRAY_COUNT(app->app_directory),
                  "%s", app_directory) == 0)
  {
    return 0;
  }
  (void)cstr_format(app->state_path,
                    ARRAY_COUNT(app->state_path),
                    "%s\\state.v1",
                    app->app_directory);
  (void)cstr_format(app->catalog_path,
                    ARRAY_COUNT(app->catalog_path),
                    "%s\\library.v1",
                    app->app_directory);
  (void)cstr_format(app->settings_path,
                    ARRAY_COUNT(app->settings_path),
                    "%s\\settings.v1",
                    app->app_directory);
  (void)cstr_format(app->export_path,
                    ARRAY_COUNT(app->export_path),
                    "%s\\reader_annotations.txt",
                    app->app_directory);
  return 1;
}

FUNCTION B32
eightvo_save_library(EightvoApp *app)
{
  return app && (!app->persistence_enabled ||
                 (app->catalog_path[0] &&
                  eightvo_library_catalog_save(&app->library,
                                                app->catalog_path)));
}

FUNCTION void
eightvo_library_set_summary_status(EightvoApp *app)
{
  if (!app) return;
  if (app->library.entry_count == 0)
  {
    eightvo_set_statusf(app, "Your library is empty | Add books");
    return;
  }
  U32 missing_count = 0;
  for (U32 index = 0; index < app->library.entry_count; index += 1)
    missing_count += app->library.entries[index].runtime_missing ? 1u : 0u;
  if (missing_count > 0)
  {
    eightvo_set_statusf(app, "%u book%s | %u source file%s missing",
                         app->library.entry_count,
                         app->library.entry_count == 1 ? "" : "s",
                         missing_count, missing_count == 1 ? "" : "s");
  }
  else
  {
    eightvo_set_statusf(app, "%u book%s | most recently opened first",
                         app->library.entry_count,
                         app->library.entry_count == 1 ? "" : "s");
  }
}

FUNCTION void
eightvo_migrate_saved_state_to_library(EightvoApp *app)
{
  if (!app || app->library.entry_count != 0 || !app->saved.valid ||
      !app->saved.path[0])
    return;
  char normalized[EightvoLibraryPathCap] = {0};
  if (!eightvo_library_normalize_path(app->saved.path,
                                       normalized,
                                       ARRAY_COUNT(normalized)))
    return;
  EightvoLibraryEntry *entry = app->library.entries;
  MemoryZeroStruct(entry);
  entry->entry_id = 1;
  entry->added_time = eightvo_library_now();
  entry->last_opened_time = entry->added_time;
  entry->progress_spine_index = app->saved.spine_index;
  entry->progress_byte_offset = app->saved.byte_offset;
  entry->cover_resource_index = DOC_RESOURCE_INDEX_NONE;
  eightvo_copy_cstr(entry->source_path,
                     ARRAY_COUNT(entry->source_path),
                     normalized);
  eightvo_library_fallback_title(normalized,
                                  entry->title,
                                  ARRAY_COUNT(entry->title));
  OS_FileProperties properties = os_file_properties(normalized);
  entry->runtime_missing = !properties.exists || properties.is_directory;
  if (!entry->runtime_missing)
  {
    entry->file_size = properties.size;
    entry->file_modified_time = properties.modified_time;
  }
  app->library.entry_count = 1;
  app->library.next_entry_id = 2;
  app->library.revision += 1;
  app->library_selected_entry_id = entry->entry_id;
  (void)eightvo_save_library(app);
}

FUNCTION void
eightvo_load_state(EightvoApp *app)
{
  if (!app || !app->persistence_enabled || !app->state_path[0]) { return; }
  char data[EightvoStateFileCap] = {0};
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
  if (!version || !spine || !byte || !path || strcmp(version, "EIGHTVO_STATE_V1") != 0)
  {
    return;
  }

  unsigned long parsed_spine = strtoul(spine, 0, 10);
  unsigned long long parsed_byte = _strtoui64(byte, 0, 10);
  app->saved.valid = 1;
  app->saved.spine_index = (U32)parsed_spine;
  app->saved.byte_offset = (U64)parsed_byte;
  eightvo_copy_cstr(app->saved.path, ARRAY_COUNT(app->saved.path), path);
}

FUNCTION void
eightvo_load_settings(EightvoApp *app)
{
  if (!app || !app->persistence_enabled || !app->settings_path[0]) { return; }
  EightvoSettingsFile file = {0};
  U64 size = 0;
  if (!os_read_entire_file(app->settings_path, &file, sizeof(file), &size) ||
      file.magic != EIGHTVO_SETTINGS_MAGIC ||
      !(((file.version == 1 || file.version == 2) &&
         size == sizeof(EightvoSettingsFileV2)) ||
        (file.version == 3 && size == sizeof(file))))
  {
    return;
  }
  if (file.font_family < FontProviderBookContentFamily_InternalCount)
    app->font_family = file.font_family;
  app->font_family_user_override = file.version < 3 ?
    1 : !!file.font_family_user_override;
  if (file.text_size_index < 4) app->text_size_index = file.text_size_index;
  if (file.line_spacing_index < 3) app->line_spacing_index = file.line_spacing_index;
  if (file.version == 1)
  {
    /* Version 1 persisted Light, Sepia, Dark as 0, 1, 2. */
    static const EightvoTheme legacy_theme_map[] = {
      EightvoTheme_Light,
      EightvoTheme_CoralLight,
      EightvoTheme_Dark,
    };
    if (file.theme < ARRAY_COUNT(legacy_theme_map))
      app->theme = legacy_theme_map[file.theme];
  }
  else if (file.theme < EightvoTheme_Count)
  {
    app->theme = (EightvoTheme)file.theme;
  }
}

FUNCTION B32
eightvo_save_settings(EightvoApp *app)
{
  if (!app || !app->persistence_enabled || !app->settings_path[0]) { return 0; }
  EightvoSettingsFile file = {
    .magic = EIGHTVO_SETTINGS_MAGIC,
    .version = 3,
    .font_family = app->font_family,
    .text_size_index = app->text_size_index,
    .line_spacing_index = app->line_spacing_index,
    .theme = (U32)app->theme,
    .font_family_user_override = !!app->font_family_user_override,
  };
  return os_write_entire_file_atomic(app->settings_path, &file, sizeof(file));
}

FUNCTION void
eightvo_set_annotations_path(EightvoApp *app, const char *path)
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
eightvo_clear_annotations(EightvoApp *app)
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
eightvo_migrate_highlight_v2(EightvoHighlight *target,
                              const EightvoHighlightV2 *source)
{
  if (!target || !source) return;
  *target = (EightvoHighlight){
    .id = source->id,
    .spine_index = source->spine_index,
    .start_byte = source->start_byte,
    .end_byte = source->end_byte,
    .color_index = source->color_index,
    .is_highlight = 1,
    .starred = source->starred,
    .note_starred = source->note_starred,
  };
  eightvo_copy_cstr(target->section, ARRAY_COUNT(target->section),
                     source->section);
  eightvo_copy_cstr(target->text, ARRAY_COUNT(target->text), source->text);
  eightvo_copy_cstr(target->note, ARRAY_COUNT(target->note), source->note);
}

FUNCTION void
eightvo_load_annotations(EightvoApp *app)
{
  if (!app) { return; }
  eightvo_clear_annotations(app);
  if (!app->persistence_enabled || !app->annotations_path[0] ||
      !app->current_path[0])
  {
    return;
  }
  union
  {
    EightvoAnnotationFile current;
    EightvoAnnotationFileV2 v2;
    EightvoAnnotationFileV1 legacy;
  } file = {0};
  U64 size = 0;
  U64 expected_hash = u64_hash_str8(str8_from_cstr(app->current_path));
  if (!os_read_entire_file(app->annotations_path, &file, sizeof(file), &size))
  {
    return;
  }
  if (size == sizeof(file.current) &&
      file.current.magic == EIGHTVO_ANNOTATION_MAGIC &&
      file.current.version == 3 && file.current.path_hash == expected_hash &&
      file.current.bookmark_count <= EightvoBookmarkCap &&
      file.current.highlight_count <= EightvoHighlightCap)
  {
    app->bookmark_count = file.current.bookmark_count;
    app->highlight_count = file.current.highlight_count;
    app->next_record_id = MAX(file.current.next_record_id, 1ull);
    MemoryCopy(app->bookmarks, file.current.bookmarks,
               sizeof(file.current.bookmarks[0]) * file.current.bookmark_count);
    MemoryCopy(app->highlights, file.current.highlights,
               sizeof(file.current.highlights[0]) * file.current.highlight_count);
    for (U32 index = 0; index < app->highlight_count; index += 1)
      app->highlights[index].is_highlight =
        app->highlights[index].is_highlight ? 1 : 0;
  }
  else if (size == sizeof(file.v2) &&
           file.v2.magic == EIGHTVO_ANNOTATION_MAGIC &&
           file.v2.version == 2 && file.v2.path_hash == expected_hash &&
           file.v2.bookmark_count <= EightvoBookmarkCap &&
           file.v2.highlight_count <= EightvoHighlightCap)
  {
    app->bookmark_count = file.v2.bookmark_count;
    app->highlight_count = file.v2.highlight_count;
    app->next_record_id = MAX(file.v2.next_record_id, 1ull);
    MemoryCopy(app->bookmarks, file.v2.bookmarks,
               sizeof(file.v2.bookmarks[0]) * file.v2.bookmark_count);
    for (U32 index = 0; index < app->highlight_count; index += 1)
      eightvo_migrate_highlight_v2(app->highlights + index,
                                    file.v2.highlights + index);
  }
  else if (size == sizeof(file.legacy) &&
           file.legacy.magic == EIGHTVO_ANNOTATION_MAGIC &&
           file.legacy.version == 1 && file.legacy.path_hash == expected_hash &&
           file.legacy.bookmark_count <= EightvoBookmarkCap &&
           file.legacy.highlight_count <= EightvoHighlightCap)
  {
    app->bookmark_count = file.legacy.bookmark_count;
    app->highlight_count = file.legacy.highlight_count;
    app->next_record_id = MAX(file.legacy.next_record_id, 1ull);
    for (U32 index = 0; index < app->bookmark_count; index += 1)
    {
      const EightvoBookmarkV1 *source = file.legacy.bookmarks + index;
      EightvoBookmark *target = app->bookmarks + index;
      target->id = source->id;
      target->spine_index = source->spine_index;
      target->byte_offset = source->byte_offset;
      target->starred = source->starred;
      eightvo_copy_cstr(target->label, ARRAY_COUNT(target->label),
                         source->label);
      eightvo_copy_cstr(target->excerpt, ARRAY_COUNT(target->excerpt),
                         "Bookmark");
    }
    for (U32 index = 0; index < app->highlight_count; index += 1)
      eightvo_migrate_highlight_v2(app->highlights + index,
                                    file.legacy.highlights + index);
  }
  else
  {
    return;
  }
  app->annotation_revision += 1;
}

FUNCTION B32
eightvo_save_annotations(EightvoApp *app)
{
  if (!app || !app->persistence_enabled || !app->annotations_path[0] ||
      !app->current_path[0])
  {
    return 0;
  }
  EightvoAnnotationFile file = {0};
  file.magic = EIGHTVO_ANNOTATION_MAGIC;
  file.version = 3;
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
eightvo_commit_annotations(EightvoApp *app)
{
  return app && (!app->persistence_enabled || eightvo_save_annotations(app));
}

FUNCTION B32
eightvo_save_state(EightvoApp *app)
{
  if (app)
  {
    if (app->window && app->state_save_pending)
      (void)KillTimer(app->window, EightvoStateSaveTimerId);
    app->state_save_pending = 0;
  }
  if (!app || !app->persistence_enabled || !app->state_path[0] ||
      !epub_reader_is_open(&app->reader) || !app->current_path[0])
  {
    return 0;
  }
  eightvo_library_update_progress(app);
  char data[EightvoStateFileCap] = {0};
  U64 size = cstr_format(data,
                         ARRAY_COUNT(data),
                         "EIGHTVO_STATE_V1\n%u\n%llu\n%s\n",
                         (unsigned)app->reader.active_spine_index,
                         (unsigned long long)app->reader.view_byte_offset,
                         app->current_path);
  if (size == 0 || size >= ARRAY_COUNT(data)) { return 0; }
  B32 state_saved = os_write_entire_file_atomic(app->state_path, data, size);
  B32 library_saved = eightvo_save_library(app);
  B32 transaction_saved = state_saved && library_saved;
  if (transaction_saved &&
      app->state_save_transaction_success_count < UINT32_MAX)
  {
    app->state_save_transaction_success_count += 1;
  }
  return transaction_saved;
}

FUNCTION void
eightvo_schedule_state_save(EightvoApp *app)
{
  if (!app || !epub_reader_is_open(&app->reader) || !app->current_path[0])
    return;
  eightvo_library_update_progress(app);
  if (!app->persistence_enabled) return;
  if (app->page_repeat_active || app->page_action_waiting_for_present ||
      app->page_action_pending)
  {
    if (app->window && app->state_save_pending)
      (void)KillTimer(app->window, EightvoStateSaveTimerId);
    app->state_save_pending = 1;
    if (app->page_repeat_persistence_deferred_count < UINT32_MAX)
      app->page_repeat_persistence_deferred_count += 1;
    return;
  }
  if (app->window &&
      SetTimer(app->window, EightvoStateSaveTimerId,
               EightvoStateSaveDelayMs, 0) != 0)
  {
    app->state_save_pending = 1;
    return;
  }
  (void)eightvo_save_state(app);
}

FUNCTION B32
eightvo_image_cache_init(EightvoImageCache *cache)
{
  if (!cache) { return 0; }
  MemoryZeroStruct(cache);
  cache->pixel_arena = arena_alloc(0);
  ArenaParams prepared_params = {
    .reserve_size = EightvoPreparedImageBudget + KILOBYTES(64),
    .commit_size = KILOBYTES(64),
  };
  cache->prepared_arena = arena_alloc(&prepared_params);
  OS_ImageDecoderInitParams params = {
    .backend = OS_ImageDecoderBackendKind_Win32WIC,
  };
  cache->decoder_ready = os_image_decoder_init(&cache->decoder, &params);
  if (!cache->pixel_arena || !cache->prepared_arena || !cache->decoder_ready)
  {
    if (cache->decoder_ready) { os_image_decoder_release(&cache->decoder); }
    if (cache->prepared_arena) { arena_release(cache->prepared_arena); }
    if (cache->pixel_arena) { arena_release(cache->pixel_arena); }
    MemoryZeroStruct(cache);
    return 0;
  }
  return 1;
}

FUNCTION void
eightvo_image_cache_release(EightvoImageCache *cache)
{
  if (!cache) { return; }
  if (cache->decoder_ready) { os_image_decoder_release(&cache->decoder); }
  if (cache->prepared_arena) { arena_release(cache->prepared_arena); }
  if (cache->pixel_arena) { arena_release(cache->pixel_arena); }
  MemoryZeroStruct(cache);
}

FUNCTION void
eightvo_image_cache_reset(EightvoImageCache *cache)
{
  if (!cache || !cache->pixel_arena || !cache->prepared_arena) { return; }
  arena_clear(cache->pixel_arena);
  arena_clear(cache->prepared_arena);
  MemoryZeroArray(cache->entries);
  MemoryZeroArray(cache->prepared_images);
  cache->entry_count = 0;
  cache->prepared_image_count = 0;
  cache->lookup_count = 0;
  cache->hit_count = 0;
  cache->miss_count = 0;
  cache->cache_full_count = 0;
  cache->prepared_cache_key = 0;
  cache->prepared_pixel_bytes = 0;
  cache->prepared_lookup_count = 0;
  cache->prepared_hit_count = 0;
  cache->prepared_build_count = 0;
  cache->prepared_reset_count = 0;
  cache->prepared_fallback_count = 0;
}

FUNCTION DrawSpriteSampleKind
eightvo_image_sample_kind(S32 source_width,
                           S32 source_height,
                           S32 target_width,
                           S32 target_height)
{
  if (source_width == target_width && source_height == target_height)
  {
    return DrawSpriteSampleKind_Nearest;
  }
  if (target_width < source_width || target_height < source_height)
  {
    return DrawSpriteSampleKind_Area;
  }
  return DrawSpriteSampleKind_Linear;
}

FUNCTION void
eightvo_image_cache_begin_prepared(EightvoImageCache *cache, U64 key)
{
  if (!cache || !cache->prepared_arena || key == 0 ||
      cache->prepared_cache_key == key)
  {
    return;
  }
  if (cache->prepared_cache_key != 0)
  {
    cache->prepared_reset_count += 1;
  }
  arena_clear(cache->prepared_arena);
  MemoryZeroArray(cache->prepared_images);
  cache->prepared_image_count = 0;
  cache->prepared_pixel_bytes = 0;
  cache->prepared_cache_key = key;
}

FUNCTION EightvoPreparedImage *
eightvo_image_cache_prepare(EightvoImageCache *cache,
                             const U32 *source_pixels,
                             S32 source_width,
                             S32 source_height,
                             S32 source_stride_pixels,
                             S32 target_width,
                             S32 target_height)
{
  if (!cache || !cache->prepared_arena || !source_pixels ||
      source_width <= 0 || source_height <= 0 ||
      source_stride_pixels < source_width ||
      target_width <= 0 || target_height <= 0)
  {
    return 0;
  }

  DrawSpriteSampleKind sample_kind =
    eightvo_image_sample_kind(source_width,
                               source_height,
                               target_width,
                               target_height);
  cache->prepared_lookup_count += 1;
  for (U32 index = 0; index < cache->prepared_image_count; index += 1)
  {
    EightvoPreparedImage *candidate = cache->prepared_images + index;
    if (candidate->source_pixels == source_pixels &&
        candidate->source_width == source_width &&
        candidate->source_height == source_height &&
        candidate->source_stride_pixels == source_stride_pixels &&
        candidate->width == target_width &&
        candidate->height == target_height &&
        candidate->sample_kind == sample_kind)
    {
      cache->prepared_hit_count += 1;
      return candidate;
    }
  }

  U64 pixel_count = (U64)(U32)target_width * (U64)(U32)target_height;
  U64 pixel_bytes = pixel_count * sizeof(U32);
  if (pixel_count == 0 ||
      pixel_bytes / sizeof(U32) != pixel_count ||
      pixel_bytes > EightvoPreparedImageBudget ||
      cache->prepared_pixel_bytes >
        EightvoPreparedImageBudget - pixel_bytes ||
      cache->prepared_image_count >= ARRAY_COUNT(cache->prepared_images))
  {
    cache->prepared_fallback_count += 1;
    return 0;
  }

  U64 arena_start = arena_pos(cache->prepared_arena);
  U32 *pixels = PUSH_ARRAY(cache->prepared_arena, U32, pixel_count);
  if (!pixels)
  {
    cache->prepared_fallback_count += 1;
    return 0;
  }
  RenderBuffer destination = {0};
  render_buffer_init(&destination,
                     pixels,
                     target_width,
                     target_height,
                     target_width);
  if (!render_resample_bgra8(&destination,
                             source_pixels,
                             source_width,
                             source_height,
                             source_stride_pixels,
                             sample_kind))
  {
    arena_pop_to(cache->prepared_arena, arena_start);
    cache->prepared_fallback_count += 1;
    return 0;
  }

  EightvoPreparedImage *prepared =
    cache->prepared_images + cache->prepared_image_count;
  *prepared = (EightvoPreparedImage){
    .source_pixels = source_pixels,
    .pixels = pixels,
    .source_width = source_width,
    .source_height = source_height,
    .source_stride_pixels = source_stride_pixels,
    .width = target_width,
    .height = target_height,
    .sample_kind = sample_kind,
    .pixel_bytes = pixel_bytes,
  };
  cache->prepared_image_count += 1;
  cache->prepared_pixel_bytes += pixel_bytes;
  cache->prepared_build_count += 1;
  return prepared;
}

FUNCTION B32
eightvo_image_media_type_is(const char *media_type, const char *expected)
{
  return media_type && expected && _stricmp(media_type, expected) == 0;
}

FUNCTION EightvoImageCacheEntry *
eightvo_image_cache_find(EightvoImageCache *cache,
                          DocDocumentId document_id,
                          U32 resource_index)
{
  if (!cache) { return 0; }
  for (U32 index = 0; index < cache->entry_count; index += 1)
  {
    EightvoImageCacheEntry *entry = cache->entries + index;
    if (entry->document_id == document_id && entry->resource_index == resource_index)
    {
      return entry;
    }
  }
  return 0;
}

FUNCTION EpubReaderFrameImageStatus
eightvo_image_status_from_decode(OS_ImageDecodeStatus status)
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

FUNCTION EightvoImageCacheEntry *
eightvo_image_cache_get(EightvoImageCache *cache,
                         DocEngine *engine,
                         DocDocumentId document_id,
                         U32 resource_index,
                         const char *media_type)
{
  if (!cache) { return 0; }
  cache->lookup_count += 1;
  EightvoImageCacheEntry *entry =
    eightvo_image_cache_find(cache, document_id, resource_index);
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
  *entry = (EightvoImageCacheEntry){
    .document_id = document_id,
    .resource_index = resource_index,
    .status = EpubReaderFrameImageStatus_Unavailable,
  };
  cache->entry_count += 1;

  if (eightvo_image_media_type_is(media_type, "image/svg+xml") ||
      eightvo_image_media_type_is(media_type, "image/webp"))
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
    entry->status = eightvo_image_status_from_decode(decode_status);
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

FUNCTION B32
eightvo_library_thumbnail_cache_init(EightvoLibraryThumbnailCache *cache)
{
  if (!cache) return 0;
  MemoryZeroStruct(cache);
  ArenaParams params = {
    .reserve_size = MEGABYTES(32),
    .commit_size = KILOBYTES(64),
  };
  cache->arena = arena_alloc(&params);
  return cache->arena != 0;
}

FUNCTION void
eightvo_library_thumbnail_cache_reset(EightvoLibraryThumbnailCache *cache)
{
  if (!cache || !cache->arena) return;
  arena_clear(cache->arena);
  MemoryZeroArray(cache->entries);
  cache->entry_count = 0;
  cache->pixel_bytes = 0;
}

FUNCTION void
eightvo_library_thumbnail_cache_release(EightvoLibraryThumbnailCache *cache)
{
  if (!cache) return;
  if (cache->arena) arena_release(cache->arena);
  MemoryZeroStruct(cache);
}

FUNCTION B32
eightvo_library_thumbnail_path(const EightvoApp *app,
                                U64 entry_id,
                                char *out_path,
                                U64 out_path_cap)
{
  return app && app->app_directory[0] && entry_id != 0 && out_path &&
    cstr_format(out_path, out_path_cap, "%s\\thumbnail_%016llx.v1",
                app->app_directory, (unsigned long long)entry_id) > 0;
}

FUNCTION EightvoLibraryThumbnail *
eightvo_library_thumbnail_find(EightvoLibraryThumbnailCache *cache,
                                U64 entry_id,
                                U64 file_size,
                                U64 file_modified_time)
{
  if (!cache) return 0;
  for (U32 index = 0; index < cache->entry_count; index += 1)
  {
    EightvoLibraryThumbnail *entry = cache->entries + index;
    if (entry->entry_id == entry_id && entry->file_size == file_size &&
        entry->file_modified_time == file_modified_time)
      return entry;
  }
  return 0;
}

FUNCTION EightvoLibraryThumbnail *
eightvo_library_thumbnail_load(EightvoApp *app,
                                const EightvoLibraryEntry *book)
{
  if (!app || !book ||
      !(book->metadata_flags & EightvoLibraryMetadata_Cover))
    return 0;
  EightvoLibraryThumbnail *cached = eightvo_library_thumbnail_find(
    &app->library_thumbnail_cache, book->entry_id, book->file_size,
    book->file_modified_time);
  if (cached) return cached;
  EightvoLibraryThumbnailCache *cache = &app->library_thumbnail_cache;
  if (!cache->arena || cache->entry_count >= ARRAY_COUNT(cache->entries))
    return 0;

  char path[EightvoPathCap] = {0};
  if (!eightvo_library_thumbnail_path(app, book->entry_id,
                                       path, ARRAY_COUNT(path)))
    return 0;
  OS_FileProperties properties = os_file_properties(path);
  if (!properties.exists || properties.is_directory ||
      properties.size < sizeof(EightvoLibraryThumbnailFile) ||
      properties.size > sizeof(EightvoLibraryThumbnailFile) +
        (U64)EightvoLibraryThumbnailWidth *
        (U64)EightvoLibraryThumbnailHeight * sizeof(U32))
    return 0;
  Arena *temp = arena_alloc(0);
  U8 *file_data = temp ? PUSH_ARRAY(temp, U8, properties.size) : 0;
  U64 size = 0;
  if (!file_data ||
      !os_read_entire_file(path, file_data, properties.size, &size) ||
      size != properties.size)
  {
    if (temp) arena_release(temp);
    return 0;
  }
  EightvoLibraryThumbnailFile *header =
    (EightvoLibraryThumbnailFile *)file_data;
  if (header->magic != EIGHTVO_LIBRARY_THUMBNAIL_MAGIC ||
      header->version != EightvoLibraryThumbnailVersion ||
      header->entry_id != book->entry_id ||
      header->file_size != book->file_size ||
      header->file_modified_time != book->file_modified_time ||
      header->width == 0 || header->height == 0 ||
      header->width > EightvoLibraryThumbnailWidth ||
      header->height > EightvoLibraryThumbnailHeight)
  {
    arena_release(temp);
    return 0;
  }
  U64 pixel_count = (U64)header->width * (U64)header->height;
  U64 pixel_bytes = pixel_count * sizeof(U32);
  if (sizeof(*header) + pixel_bytes != size ||
      cache->pixel_bytes + pixel_bytes > EightvoLibraryThumbnailBudget)
  {
    arena_release(temp);
    return 0;
  }
  U32 *pixels = PUSH_ARRAY(cache->arena, U32, pixel_count);
  if (!pixels)
  {
    arena_release(temp);
    return 0;
  }
  MemoryCopy(pixels, header + 1, pixel_bytes);
  EightvoLibraryThumbnail *result = cache->entries + cache->entry_count;
  *result = (EightvoLibraryThumbnail){
    .entry_id = book->entry_id,
    .file_size = book->file_size,
    .file_modified_time = book->file_modified_time,
    .pixels = pixels,
    .width = (S32)header->width,
    .height = (S32)header->height,
    .stride_pixels = (S32)header->width,
  };
  cache->entry_count += 1;
  cache->pixel_bytes += pixel_bytes;
  arena_release(temp);
  return result;
}

FUNCTION B32
eightvo_library_thumbnail_write(EightvoApp *app,
                                 const EightvoLibraryEntry *book,
                                 const EightvoImageCacheEntry *source)
{
  if (!app || !book || !source || !source->pixels || source->width <= 0 ||
      source->height <= 0 || source->stride_pixels < source->width)
    return 0;
  S32 width = source->width;
  S32 height = source->height;
  if (width > EightvoLibraryThumbnailWidth)
  {
    height = MAX(1, (S32)(((S64)height * EightvoLibraryThumbnailWidth) / width));
    width = EightvoLibraryThumbnailWidth;
  }
  if (height > EightvoLibraryThumbnailHeight)
  {
    width = MAX(1, (S32)(((S64)width * EightvoLibraryThumbnailHeight) / height));
    height = EightvoLibraryThumbnailHeight;
  }
  U64 pixel_count = (U64)width * (U64)height;
  U64 size = sizeof(EightvoLibraryThumbnailFile) + pixel_count * sizeof(U32);
  Arena *arena = arena_alloc(0);
  U8 *data = arena ? PUSH_ARRAY(arena, U8, size) : 0;
  if (!data)
  {
    if (arena) arena_release(arena);
    return 0;
  }
  EightvoLibraryThumbnailFile *file = (EightvoLibraryThumbnailFile *)data;
  *file = (EightvoLibraryThumbnailFile){
    .magic = EIGHTVO_LIBRARY_THUMBNAIL_MAGIC,
    .version = EightvoLibraryThumbnailVersion,
    .width = (U32)width,
    .height = (U32)height,
    .entry_id = book->entry_id,
    .file_size = book->file_size,
    .file_modified_time = book->file_modified_time,
  };
  U32 *pixels = (U32 *)(file + 1);
  RenderBuffer destination = {0};
  render_buffer_init(&destination, pixels, width, height, width);
  if (!render_resample_bgra8(
        &destination,
        source->pixels,
        source->width,
        source->height,
        source->stride_pixels,
        eightvo_image_sample_kind(source->width,
                                   source->height,
                                   width,
                                   height)))
  {
    arena_release(arena);
    return 0;
  }
  char path[EightvoPathCap] = {0};
  B32 result = eightvo_library_thumbnail_path(app, book->entry_id,
                                               path, ARRAY_COUNT(path)) &&
    os_write_entire_file_atomic(path, data, size);
  arena_release(arena);
  if (result) eightvo_library_thumbnail_cache_reset(&app->library_thumbnail_cache);
  return result;
}

FUNCTION void
eightvo_library_copy_string8(char *dst, U64 cap, String8 source)
{
  if (!dst || cap == 0) return;
  U64 size = MIN(source.size, cap - 1);
  if (size > 0 && source.str) MemoryCopy(dst, source.str, size);
  dst[size] = 0;
}

FUNCTION void
eightvo_library_update_progress(EightvoApp *app)
{
  if (!app || !epub_reader_is_open(&app->reader) || !app->current_path[0])
    return;
  EightvoLibraryEntry *entry =
    eightvo_library_catalog_find_path(&app->library, app->current_path);
  if (!entry) return;
  entry->progress_spine_index = app->reader.active_spine_index;
  entry->progress_byte_offset = app->reader.view_byte_offset;
  entry->progress_spine_href[0] = 0;
  String8 href = {0};
  if (doc_engine_get_spine_item(epub_reader_engine(&app->reader),
                                epub_reader_document_id(&app->reader),
                                entry->progress_spine_index,
                                &href) == DocError_Ok)
    eightvo_library_copy_string8(entry->progress_spine_href,
                                  ARRAY_COUNT(entry->progress_spine_href), href);
  EpubReaderLocationSummary location = epub_reader_location_summary(&app->reader);
  entry->progress_percent = location.available ? (U32)MIN(location.percent, 100ull) : 0;
  app->library.revision += 1;
}

FUNCTION B32
eightvo_library_refresh_open_document_metadata(EightvoApp *app,
                                                 EightvoLibraryEntry *entry)
{
  if (!app || !entry || !entry->source_path[0] ||
      !epub_reader_is_open(&app->reader)) return 0;
  if (app->library_metadata_refresh_count < UINT32_MAX)
    app->library_metadata_refresh_count += 1;

  String8 title = {0};
  String8 author = {0};
  entry->metadata_flags = EightvoLibraryMetadata_Inspected;
  if (doc_engine_get_title(epub_reader_engine(&app->reader),
                           epub_reader_document_id(&app->reader),
                           &title) == DocError_Ok && title.size > 0)
  {
    eightvo_library_copy_string8(entry->title, ARRAY_COUNT(entry->title), title);
    entry->metadata_flags |= EightvoLibraryMetadata_Title;
  }
  else
  {
    eightvo_library_fallback_title(entry->source_path,
                                    entry->title,
                                    ARRAY_COUNT(entry->title));
  }
  if (doc_engine_get_author(epub_reader_engine(&app->reader),
                            epub_reader_document_id(&app->reader),
                            &author) == DocError_Ok && author.size > 0)
  {
    eightvo_library_copy_string8(entry->author, ARRAY_COUNT(entry->author), author);
    entry->metadata_flags |= EightvoLibraryMetadata_Author;
  }
  else
  {
    entry->author[0] = 0;
  }

  entry->cover_resource_index = DOC_RESOURCE_INDEX_NONE;
  U32 resource_count = 0;
  DocEngine *engine = epub_reader_engine(&app->reader);
  DocDocumentId document_id = epub_reader_document_id(&app->reader);
  if (doc_engine_get_resource_count(engine, document_id,
                                    &resource_count) == DocError_Ok)
  {
    for (U32 index = 0; index < resource_count; index += 1)
    {
      DocResource resource = {0};
      if (doc_engine_get_resource(engine, document_id, index, &resource) == DocError_Ok &&
          resource.kind == DocResourceKind_Image &&
          (resource.flags & DocResourceFlag_Cover))
      {
        char media_type[64] = {0};
        eightvo_library_copy_string8(media_type, ARRAY_COUNT(media_type),
                                      resource.media_type);
        EightvoImageCacheEntry *decoded = eightvo_image_cache_get(
          &app->image_cache, engine, document_id, resource.resource_index,
          media_type);
        if (decoded && decoded->status == EpubReaderFrameImageStatus_Loaded &&
            eightvo_library_thumbnail_write(app, entry, decoded))
        {
          entry->cover_resource_index = resource.resource_index;
          entry->metadata_flags |= EightvoLibraryMetadata_Cover;
        }
        break;
      }
    }
  }
  if (!(entry->metadata_flags & EightvoLibraryMetadata_Cover))
  {
    char thumbnail_path[EightvoPathCap] = {0};
    if (eightvo_library_thumbnail_path(app, entry->entry_id, thumbnail_path,
                                        ARRAY_COUNT(thumbnail_path)))
      (void)os_file_delete(thumbnail_path);
  }
  app->library.revision += 1;
  return 1;
}

FUNCTION EightvoLibraryEntry *
eightvo_library_record_open_document(EightvoApp *app,
                                      const char *normalized_path)
{
  if (!app || !normalized_path || !normalized_path[0] ||
      !epub_reader_is_open(&app->reader))
    return 0;
  OS_FileProperties properties = os_file_properties(normalized_path);
  EightvoLibraryEntry *existing = app->library_locate_entry_id ?
    eightvo_library_catalog_find_id(&app->library,
                                     app->library_locate_entry_id) :
    eightvo_library_catalog_find_path(&app->library, normalized_path);
  B32 source_changed = existing &&
    (existing->file_size != properties.size ||
     existing->file_modified_time != properties.modified_time);
  B32 created = 0;
  EightvoLibraryEntry *entry = eightvo_library_catalog_upsert(
    &app->library, normalized_path, properties, eightvo_library_now(),
    app->library_locate_entry_id, &created);
  app->library_locate_entry_id = 0;
  if (!entry) return 0;
  B32 metadata_current =
    (entry->metadata_flags & EightvoLibraryMetadata_Inspected) != 0;
  B32 thumbnail_current =
    !(entry->metadata_flags & EightvoLibraryMetadata_Cover) ||
    eightvo_library_thumbnail_load(app, entry) != 0;
  if (created || source_changed || !metadata_current || !thumbnail_current)
    (void)eightvo_library_refresh_open_document_metadata(app, entry);
  app->library_selected_entry_id = entry->entry_id;
  eightvo_library_catalog_sort(&app->library);
  return eightvo_library_catalog_find_id(&app->library,
                                          app->library_selected_entry_id);
}

FUNCTION void
eightvo_library_hydrate_startup_entry(EightvoApp *app)
{
  if (!app || !app->persistence_enabled || app->library.entry_count == 0)
    return;
  EightvoLibraryEntry *entry = eightvo_library_catalog_find_id(
    &app->library, app->library_selected_entry_id);
  if (!entry || entry->runtime_missing)
    return;
  B32 metadata_inspected =
    (entry->metadata_flags & EightvoLibraryMetadata_Inspected) != 0;
  B32 thumbnail_current =
    !(entry->metadata_flags & EightvoLibraryMetadata_Cover) ||
    eightvo_library_thumbnail_load(app, entry) != 0;
  if (metadata_inspected && thumbnail_current) return;

  EpubReaderOpenTransition transition = {0};
  EpubReaderResult open_result = epub_reader_open(
    &app->reader, str8_from_cstr(entry->source_path), DocSourceKind_EPUB,
    &transition);
  if (open_result == EpubReaderResult_Ok)
  {
    (void)eightvo_library_refresh_open_document_metadata(app, entry);
    B32 changed = 0;
    (void)epub_reader_close(&app->reader, &changed);
    eightvo_image_cache_reset(&app->image_cache);
    (void)eightvo_save_library(app);
  }
  (void)transition;
}

FUNCTION void
eightvo_attach_frame_images(EightvoApp *app)
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

    EightvoImageCacheEntry *entry =
      eightvo_image_cache_get(&app->image_cache,
                               engine,
                               document_id,
                               image->resource_index,
                               image->media_type);
    if (!entry)
    {
      image->status = app->image_cache.entry_count >= EightvoImageCacheCap ?
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

FUNCTION U32
eightvo_reader_margin_unit_permille(S32 line_height,
                                     S32 margin_line_height)
{
  if (line_height <= 0) { return 1000; }
  if (margin_line_height <= 0) { margin_line_height = line_height; }
  U64 unit = ((U64)margin_line_height * 1000ULL +
              (U64)(line_height / 2)) / (U64)line_height;
  return (U32)MIN(MAX(unit, 1ULL), 1000ULL);
}

FUNCTION B32
eightvo_update_layout_inputs(EightvoApp *app)
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
  static const S32 fallback_char_advances[] = {8, 10, 11, 11};
  static const S32 fallback_line_heights[] = {18, 26, 28, 30};
  static const S32 line_spacing_extra[] = {0, 5, 10};
  UI0Rect content = app->reader_content_geometry.content_rect;
  if (content.w <= 0 || content.h <= 0)
  {
    ReaderViewLayout initial_layout = {0};
    ReaderViewLayoutInput initial_layout_input = {
      .bounds = ui0_rect(0, 0, app->width, app->height),
      .features = ReaderViewFeature_Paging | ReaderViewFeature_Progress,
      .document_flags = ReaderViewDocument_Open,
      .host_toolbar_trailing_width = EightvoHostToolbarTrailingWidth,
    };
    if (!reader_view_resolve_layout(&app->reader_view_state,
                                    &initial_layout_input,
                                    &initial_layout))
    {
      return 0;
    }
    content = initial_layout.content_rect;
  }
  U32 size_index = app->text_size_index % ARRAY_COUNT(text_scales);
  U32 spacing_index = app->line_spacing_index % ARRAY_COUNT(line_spacing_extra);
  S32 content_width = MAX(content.w, 80);
  S32 content_height = MAX(content.h, 48);
  S32 text_scale = text_scales[size_index];
  String8 uri = epub_reader_canonical_uri(&app->reader);
  B32 embedded_fonts_enabled = !app->font_family_user_override;

  (void)epub_reader_typography_set_view(&app->reader.typography,
                                        text_scale,
                                        app->font_family,
                                        embedded_fonts_enabled);
  epub_reader_typography_set_text_mode(&app->reader.typography,
                                       EpubReaderTextMode_ShapedV1);
  String8 measure_sample = str8_from_cstr(
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
  S32 sample_width = epub_reader_typography_measure_text(
    &app->reader.typography,
    measure_sample,
    0,
    1000,
    0,
    DOC_EMBEDDED_FONT_FACE_INDEX_NONE);
  S32 char_advance = fallback_char_advances[size_index];
  if (sample_width > 0 && measure_sample.size > 0)
  {
    char_advance = MAX(sample_width / (S32)measure_sample.size, 1);
  }
  const FontProvider *provider =
    epub_reader_typography_provider_for_family_style(&app->reader.typography,
                                                     app->font_family,
                                                     0);
  FontTextMetrics font_metrics = font_metrics_for_size(provider, text_scale);
  S32 base_line_height = fallback_line_heights[size_index];
  if (font_metrics.line_advance_px > 0)
  {
    base_line_height = font_metrics.line_advance_px;
  }
  S32 line_height = base_line_height + line_spacing_extra[spacing_index];
  U32 margin_unit_permille =
    eightvo_reader_margin_unit_permille(line_height, base_line_height);
  app->reader_margin_line_height = base_line_height;

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
    .margin_unit_permille = margin_unit_permille,
    .font_family_index = app->font_family,
    .embedded_fonts_enabled = embedded_fonts_enabled,
    .text_mode = EpubReaderTextMode_ShapedV1,
  };
  app->layout_config = (SourceReaderLayoutConfig){
    .wrap_column_count = app->layout_key.wrap_cols,
    .page_row_count = app->layout_key.page_rows,
    .margin_unit_permille = app->layout_key.margin_unit_permille,
    .text_width_px = app->layout_key.text_w,
    .char_advance_px = app->layout_key.char_advance,
    .text_scale = app->layout_key.text_scale,
    .focused_spine_index = app->reader.active_spine_index,
    .measure_focused_spine_only = 1,
    .load_focused_spine_only = 1,
    .measure_generation = epub_reader_layout_key_generation(app->layout_key),
    .measure_cache = epub_reader_measure_cache_for_key(&app->reader,
                                                       app->layout_key),
    .measure_text = epub_reader_typography_measure_text,
    .measure_user = &app->reader.typography,
  };
  app->pagination_viewport_width = content.w;
  app->pagination_viewport_height = content.h;
  return 1;
}

FUNCTION B32
eightvo_capture_frame(EightvoApp *app)
{
  if (!app) return 0;
  if (app->capture_frame_fail_count > 0)
  {
    app->capture_frame_fail_count -= 1;
    app->capture_frame_failed_since_mutation = 1;
    return 0;
  }
  if (!epub_reader_build_frame(&app->reader,
                                       &app->frame_storage,
                                       &app->frame))
  {
    app->capture_frame_failed_since_mutation = 1;
    return 0;
  }
  eightvo_attach_frame_images(app);
  app->frame_capture_generation += 1;
  if (app->frame_capture_generation == 0)
    app->frame_capture_generation = 1;
  app->capture_frame_failed_since_mutation = 0;
  return 1;
}

FUNCTION B32
eightvo_frame_text_rows_are_complete(const EpubReaderFrame *frame,
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
eightvo_repaginate(EightvoApp *app)
{
  if (!eightvo_begin_document_mutation(app)) return 0;
  if (!eightvo_update_layout_inputs(app)) { return 0; }
  eightvo_cancel_adjacent_warm(app);
  eightvo_invalidate_adjacent_page(app);
  B32 reused = 0;
  if (!epub_reader_rebuild_pagination(&app->reader,
                                      app->layout_key,
                                      app->layout_config,
                                      &reused))
  {
    return 0;
  }
  (void)reused;
  B32 frame_captured = eightvo_capture_frame(app);
  eightvo_complete_document_mutation(app, 1);
  if (!frame_captured) { return 0; }
  eightvo_set_statusf(app,
                       "Page %llu/%llu | section %u/%u",
                       (unsigned long long)app->frame.page_index,
                       (unsigned long long)app->frame.page_count,
                       (unsigned)(app->frame.spine_index + 1),
                       (unsigned)app->frame.section_count);
  eightvo_schedule_adjacent_warm(app);
  return 1;
}

FUNCTION B32
eightvo_recover_failed_open_to_library(EightvoApp *app)
{
  if (!app) return 0;
  eightvo_cancel_location_warm(app);
  B32 changed = 0;
  if (epub_reader_is_open(&app->reader))
    (void)epub_reader_close(&app->reader, &changed);
  app->current_path[0] = 0;
  app->annotations_path[0] = 0;
  app->document_state = ReaderViewLoad_Error;
  MemoryZeroStruct(&app->frame);
  MemoryZeroStruct(&app->presentation_frame);
  app->presentation_hash = 0;
  app->pagination_viewport_width = 0;
  app->pagination_viewport_height = 0;
  app->first_reader_present_pending = 0;
  app->selection_dragging = 0;
  app->selected_text[0] = 0;
  eightvo_image_cache_reset(&app->image_cache);
  reader_view_state_init(&app->reader_view_state);
  app->reader_view_ready = 0;
  app->host_focus_control = app->library.entry_count > 0 ?
    (EightvoHostControlIdentity)EightvoHostControl_LibraryBookBase :
    EightvoHostControl_LibraryAdd;
  app->host_focus_visible = 1;
  eightvo_complete_document_mutation(app, 1);
  return !epub_reader_is_open(&app->reader);
}

FUNCTION B32
eightvo_open_path(EightvoApp *app, const char *path)
{
  if (!app || !path || !path[0]) { return 0; }
  if (!eightvo_begin_document_mutation(app)) return 0;
  eightvo_cancel_location_warm(app);
  eightvo_cancel_adjacent_warm(app);
  eightvo_invalidate_adjacent_page(app);
  char normalized_path[EightvoLibraryPathCap] = {0};
  if (!eightvo_library_normalize_path(path,
                                       normalized_path,
                                       ARRAY_COUNT(normalized_path)))
  {
    eightvo_set_statusf(app, "Open failed: path encoding");
    app->library_locate_entry_id = 0;
    return 0;
  }
  EightvoLibraryEntry *restore_entry = app->library_locate_entry_id ?
    eightvo_library_catalog_find_id(&app->library,
                                     app->library_locate_entry_id) :
    eightvo_library_catalog_find_path(&app->library, normalized_path);
  U32 restore_spine = restore_entry ? restore_entry->progress_spine_index : 0;
  U64 restore_byte = restore_entry ? restore_entry->progress_byte_offset : 0;
  B32 has_catalog_position = restore_entry != 0;
  app->document_state = ReaderViewLoad_Loading;
  eightvo_set_statusf(app, "Opening EPUB...");
  if (app->current_path[0])
  {
    (void)eightvo_save_state(app);
    (void)eightvo_save_annotations(app);
  }
  B32 reader_was_open = epub_reader_is_open(&app->reader);
  DocDocumentId previous_document_id = epub_reader_document_id(&app->reader);
  U64 previous_document_generation = app->reader.document_generation;
  EpubReaderOpenTransition transition = {0};
  EpubReaderResult open_result =
    epub_reader_open(&app->reader,
                     str8_from_cstr(normalized_path),
                     DocSourceKind_EPUB,
                     &transition);
  if (open_result != EpubReaderResult_Ok)
  {
    eightvo_set_statusf(app, "Open failed: %s", epub_reader_result_code(open_result));
    B32 reader_changed = reader_was_open != epub_reader_is_open(&app->reader) ||
      previous_document_id != epub_reader_document_id(&app->reader) ||
      previous_document_generation != app->reader.document_generation;
    if (reader_changed)
      (void)eightvo_recover_failed_open_to_library(app);
    else
      app->document_state = reader_was_open ?
        ReaderViewLoad_Ready : ReaderViewLoad_Error;
    app->library_locate_entry_id = 0;
    return 0;
  }
  if (!epub_reader_refresh_active_spine(&app->reader) ||
      !eightvo_update_layout_inputs(app))
  {
    (void)eightvo_recover_failed_open_to_library(app);
    eightvo_set_statusf(app, "Open failed: layout");
    app->library_locate_entry_id = 0;
    return 0;
  }

  eightvo_image_cache_reset(&app->image_cache);
  eightvo_copy_cstr(app->current_path,
                     ARRAY_COUNT(app->current_path),
                     normalized_path);
  eightvo_set_annotations_path(app, normalized_path);
  eightvo_load_annotations(app);
  reader_view_state_reset_document(&app->reader_view_state,
                                   (UI0U64)epub_reader_document_id(&app->reader));
  B32 restored = 0;
  if (has_catalog_position)
  {
    epub_reader_request_window_pagination(&app->reader);
    restored = epub_reader_set_page_containing_byte(&app->reader,
                                                    restore_spine,
                                                    restore_byte,
                                                    app->layout_key,
                                                    app->layout_config);
  }
  else if (app->saved.valid && _stricmp(app->saved.path, normalized_path) == 0)
  {
    epub_reader_request_window_pagination(&app->reader);
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
      (void)eightvo_recover_failed_open_to_library(app);
      eightvo_set_statusf(app, "Open failed: pagination");
      app->library_locate_entry_id = 0;
      return 0;
    }
  }
  app->document_state = ReaderViewLoad_Ready;
  B32 frame_captured = eightvo_capture_frame(app);
  eightvo_complete_document_mutation(app, 1);
  EightvoLibraryEntry *catalog_entry =
    eightvo_library_record_open_document(app, normalized_path);
  if (frame_captured)
  {
    eightvo_set_statusf(app,
                         "%s | page %llu/%llu | section %u/%u",
                         restored ? "Restored" : "Opened",
                         (unsigned long long)app->frame.page_index,
                         (unsigned long long)app->frame.page_count,
                         (unsigned)(app->frame.spine_index + 1),
                         (unsigned)app->frame.section_count);
  }
  else
  {
    eightvo_set_statusf(app, "Opened; frame recovery pending");
  }
  (void)transition;
  app->host_focus_control = EightvoHostControl_None;
  app->host_focus_visible = 0;
  app->host_pointer_armed = EightvoHostControl_None;
  app->host_exit_pointer_armed = 0;
  if (!catalog_entry)
    eightvo_set_statusf(app, "Opened, but the library is full");
  eightvo_schedule_state_save(app);
  app->first_reader_present_pending = app->window != 0;
  if (frame_captured) eightvo_schedule_adjacent_warm(app);
  return 1;
}

FUNCTION B32
eightvo_close_book(EightvoApp *app)
{
  if (!app || !epub_reader_is_open(&app->reader)) return 0;
  if (!eightvo_begin_document_mutation(app)) return 0;
  eightvo_stop_page_repeat(app);
  eightvo_cancel_location_warm(app);
  eightvo_cancel_adjacent_warm(app);
  eightvo_invalidate_adjacent_page(app);
  (void)eightvo_save_state(app);
  (void)eightvo_save_annotations(app);
  B32 changed = 0;
  if (epub_reader_close(&app->reader, &changed) != EpubReaderResult_Ok || !changed)
    return 0;
  app->current_path[0] = 0;
  app->annotations_path[0] = 0;
  app->document_state = ReaderViewLoad_Empty;
  eightvo_library_catalog_refresh_missing(&app->library);
  MemoryZeroStruct(&app->frame);
  MemoryZeroStruct(&app->presentation_frame);
  app->presentation_hash = 0;
  app->pagination_viewport_width = 0;
  app->pagination_viewport_height = 0;
  app->first_reader_present_pending = 0;
  app->selection_dragging = 0;
  app->selected_text[0] = 0;
  eightvo_image_cache_reset(&app->image_cache);
  eightvo_clear_annotations(app);
  reader_view_state_init(&app->reader_view_state);
  app->reader_view_ready = 0;
  app->host_focus_control = app->library.entry_count > 0 ?
    (EightvoHostControlIdentity)EightvoHostControl_LibraryBookBase :
    EightvoHostControl_LibraryAdd;
  app->host_focus_visible = 1;
  app->host_pointer_armed = EightvoHostControl_None;
  eightvo_library_set_summary_status(app);
  (void)eightvo_save_library(app);
  eightvo_complete_document_mutation(app, 1);
  return 1;
}

FUNCTION EpubReaderResult
eightvo_move_page(EightvoApp *app, S32 direction)
{
  if (!app || !epub_reader_is_open(&app->reader))
  {
    if (app) { eightvo_set_statusf(app, "Open a book first"); }
    return EpubReaderResult_NotOpen;
  }
  if (!eightvo_begin_document_mutation(app))
    return EpubReaderResult_InvalidInput;
  if (!eightvo_update_layout_inputs(app))
  {
    eightvo_set_statusf(app, "Page move failed: layout");
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
    eightvo_set_statusf(app, direction < 0 ? "Beginning of book" : "End of book");
    return result;
  }
  B32 reader_changed = result == EpubReaderResult_Ok && change.changed;
  if (reader_changed) eightvo_schedule_state_save(app);
  B32 frame_captured = result == EpubReaderResult_Ok && change.changed &&
    eightvo_capture_frame(app);
  eightvo_complete_document_mutation(app, reader_changed);
  B32 canonical_page_valid = frame_captured &&
    app->reader.has_current_page &&
    app->reader.current_page.spine_page_count > 0 &&
    app->frame.ready && app->frame.document_open;
  if (!canonical_page_valid)
  {
    eightvo_set_statusf(app,
                         "Page move failed: %s (%d)",
                         epub_reader_result_code(result),
                         (int)change.diagnostic);
    return result == EpubReaderResult_Ok ? EpubReaderResult_DocError : result;
  }

  eightvo_set_statusf(app,
                       "Page %llu/%llu | section %u/%u",
                       (unsigned long long)(
                         app->reader.current_page.spine_page_index + 1),
                       (unsigned long long)
                         app->reader.current_page.spine_page_count,
                       (unsigned)(app->frame.spine_index + 1),
                       (unsigned)app->frame.section_count);
  eightvo_schedule_adjacent_warm(app);
  return EpubReaderResult_Ok;
}

FUNCTION EpubReaderResult
eightvo_finish_semantic_navigation(EightvoApp *app,
                                    EpubReaderResult result,
                                    const char *operation)
{
  if (!app || !operation) { return EpubReaderResult_InvalidInput; }
  if (result != EpubReaderResult_Ok)
  {
    eightvo_set_statusf(app,
                         "%s failed: %s",
                         operation,
                         epub_reader_result_code(result));
    return result;
  }
  eightvo_schedule_state_save(app);
  B32 frame_captured = eightvo_capture_frame(app);
  eightvo_complete_document_mutation(app, 1);
  if (!frame_captured)
  {
    eightvo_set_statusf(app, "%s failed: frame", operation);
    return EpubReaderResult_DocError;
  }
  eightvo_set_statusf(app,
                       "%s | page %llu/%llu | section %u/%u",
                       operation,
                       (unsigned long long)app->frame.page_index,
                       (unsigned long long)app->frame.page_count,
                       (unsigned)(app->frame.spine_index + 1),
                       (unsigned)app->frame.section_count);
  return EpubReaderResult_Ok;
}

FUNCTION EpubReaderResult
eightvo_navigate_to_nav_point(EightvoApp *app,
                               U32 nav_index,
                               EpubReaderNavPointResult *out_navigation)
{
  if (out_navigation) { *out_navigation = (EpubReaderNavPointResult){0}; }
  if (!app || !out_navigation) { return EpubReaderResult_InvalidInput; }
  if (!epub_reader_is_open(&app->reader))
  {
    eightvo_set_statusf(app, "Open a book first");
    return EpubReaderResult_NotOpen;
  }
  if (!eightvo_begin_document_mutation(app))
    return EpubReaderResult_InvalidInput;
  if (!eightvo_update_layout_inputs(app))
  {
    eightvo_set_statusf(app, "Contents failed: layout");
    return EpubReaderResult_DocError;
  }
  EpubReaderResult result =
    epub_reader_navigate_to_nav_point(&app->reader,
                                      nav_index,
                                      app->layout_key,
                                      app->layout_config,
                                      (EpubReaderNavigationOptions){0},
                                      out_navigation);
  return eightvo_finish_semantic_navigation(app, result, "Contents");
}

FUNCTION EpubReaderResult
eightvo_navigate_to_search_match(EightvoApp *app,
                                  U32 match_index,
                                  EpubReaderSearchNavigationResult *out_navigation)
{
  if (out_navigation) { *out_navigation = (EpubReaderSearchNavigationResult){0}; }
  if (!app || !out_navigation) { return EpubReaderResult_InvalidInput; }
  if (!epub_reader_is_open(&app->reader))
  {
    eightvo_set_statusf(app, "Open a book first");
    return EpubReaderResult_NotOpen;
  }
  if (!eightvo_begin_document_mutation(app))
    return EpubReaderResult_InvalidInput;
  if (!eightvo_update_layout_inputs(app))
  {
    eightvo_set_statusf(app, "Find failed: layout");
    return EpubReaderResult_DocError;
  }
  EpubReaderResult result =
    epub_reader_navigate_to_search_match(&app->reader,
                                         match_index,
                                         app->layout_key,
                                         app->layout_config,
                                         (EpubReaderNavigationOptions){0},
                                         out_navigation);
  return eightvo_finish_semantic_navigation(app, result, "Find");
}

FUNCTION ReaderViewText
eightvo_reader_view_text(const char *text)
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
eightvo_reader_view_bytes(const char *text, U64 size)
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
eightvo_copy_bytes(char *dst, U64 cap, const U8 *src, U64 size)
{
  if (!dst || cap == 0) { return; }
  U64 copy_size = MIN(size, cap - 1);
  if (src && copy_size) { MemoryCopy(dst, src, copy_size); }
  dst[copy_size] = 0;
}

FUNCTION const char *
eightvo_current_section_label(const EightvoApp *app)
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
eightvo_bookmark_index(const EightvoApp *app, U64 id)
{
  if (!app || id == 0) { return -1; }
  for (U32 index = 0; index < app->bookmark_count; index += 1)
    if (app->bookmarks[index].id == id) return (S32)index;
  return -1;
}

FUNCTION S32
eightvo_current_bookmark_index(const EightvoApp *app)
{
  if (!app || !epub_reader_is_open(&app->reader)) { return -1; }
  for (U32 index = 0; index < app->bookmark_count; index += 1)
  {
    const EightvoBookmark *bookmark = app->bookmarks + index;
    if (bookmark->spine_index == app->reader.active_spine_index &&
        bookmark->byte_offset == app->reader.view_byte_offset)
      return (S32)index;
  }
  return -1;
}

FUNCTION S32
eightvo_highlight_index(const EightvoApp *app, U64 id)
{
  if (!app || id == 0) { return -1; }
  for (U32 index = 0; index < app->highlight_count; index += 1)
    if (app->highlights[index].id == id) return (S32)index;
  return -1;
}

FUNCTION S32
eightvo_selection_highlight_index(const EightvoApp *app)
{
  if (!app || !app->reader.has_active_selection) { return -1; }
  DocSelection selection = app->reader.active_selection;
  S32 best_index = -1;
  U64 best_size = UINT64_MAX;
  for (U32 index = 0; index < app->highlight_count; index += 1)
  {
    const EightvoHighlight *highlight = app->highlights + index;
    if (highlight->spine_index == selection.spine_index &&
        highlight->start_byte <= selection.text_byte_start &&
        highlight->end_byte >= selection.text_byte_end &&
        highlight->end_byte > highlight->start_byte)
    {
      U64 size = highlight->end_byte - highlight->start_byte;
      if (size < best_size)
      {
        best_index = (S32)index;
        best_size = size;
      }
    }
  }
  return best_index;
}

FUNCTION B32
eightvo_remove_bookmark_at(EightvoApp *app, U32 index)
{
  if (!app || index >= app->bookmark_count) return 0;
  EightvoBookmark removed = app->bookmarks[index];
  U64 saved_revision = app->annotation_revision;
  for (U32 at = index + 1; at < app->bookmark_count; at += 1)
    app->bookmarks[at - 1] = app->bookmarks[at];
  app->bookmark_count -= 1;
  MemoryZeroStruct(app->bookmarks + app->bookmark_count);
  app->annotation_revision += 1;
  if (eightvo_commit_annotations(app)) return 1;
  for (U32 at = app->bookmark_count; at > index; at -= 1)
    app->bookmarks[at] = app->bookmarks[at - 1];
  app->bookmarks[index] = removed;
  app->bookmark_count += 1;
  app->annotation_revision = saved_revision;
  eightvo_set_statusf(app, "Bookmark remove failed");
  return 0;
}

FUNCTION B32
eightvo_remove_highlight_record_at(EightvoApp *app, U32 index)
{
  if (!app || index >= app->highlight_count) return 0;
  EightvoHighlight removed = app->highlights[index];
  U64 saved_revision = app->annotation_revision;
  for (U32 at = index + 1; at < app->highlight_count; at += 1)
    app->highlights[at - 1] = app->highlights[at];
  app->highlight_count -= 1;
  MemoryZeroStruct(app->highlights + app->highlight_count);
  app->annotation_revision += 1;
  if (eightvo_commit_annotations(app)) return 1;
  for (U32 at = app->highlight_count; at > index; at -= 1)
    app->highlights[at] = app->highlights[at - 1];
  app->highlights[index] = removed;
  app->highlight_count += 1;
  app->annotation_revision = saved_revision;
  eightvo_set_statusf(app, "Annotation delete failed");
  return 0;
}

FUNCTION B32
eightvo_remove_highlight_identity_at(EightvoApp *app, U32 index)
{
  if (!app || index >= app->highlight_count ||
      !app->highlights[index].is_highlight)
    return 0;
  if (app->highlights[index].note[0] == 0)
    return eightvo_remove_highlight_record_at(app, index);
  EightvoHighlight saved = app->highlights[index];
  U64 saved_revision = app->annotation_revision;
  app->highlights[index].is_highlight = 0;
  app->highlights[index].starred = 0;
  app->annotation_revision += 1;
  if (eightvo_commit_annotations(app)) return 1;
  app->highlights[index] = saved;
  app->annotation_revision = saved_revision;
  eightvo_set_statusf(app, "Highlight remove failed");
  return 0;
}

FUNCTION B32
eightvo_delete_note_at_index(EightvoApp *app, U32 index)
{
  if (!app || index >= app->highlight_count ||
      app->highlights[index].note[0] == 0)
    return 0;
  if (!app->highlights[index].is_highlight)
    return eightvo_remove_highlight_record_at(app, index);
  EightvoHighlight saved = app->highlights[index];
  U64 saved_revision = app->annotation_revision;
  app->highlights[index].note[0] = 0;
  app->highlights[index].note_starred = 0;
  app->annotation_revision += 1;
  if (eightvo_commit_annotations(app)) return 1;
  app->highlights[index] = saved;
  app->annotation_revision = saved_revision;
  eightvo_set_statusf(app, "Note delete failed");
  return 0;
}

FUNCTION void
eightvo_prepare_bookmark_excerpt(const EightvoApp *app,
                                  char *out_excerpt,
                                  U64 excerpt_cap)
{
  if (!out_excerpt || excerpt_cap == 0) return;
  out_excerpt[0] = 0;
  if (!app || !app->frame.visible_text.str || app->frame.visible_text.size == 0)
  {
    eightvo_copy_cstr(out_excerpt, excerpt_cap, "Bookmark");
    return;
  }
  const U8 *text = app->frame.visible_text.str;
  U64 size = app->frame.visible_text.size;
  U64 at = 0;
  while (at < size && text[at] <= ' ') at += 1;
  U64 out_size = 0;
  B32 pending_space = 0;
  for (; at < size && out_size + 1 < excerpt_cap; at += 1)
  {
    U8 byte = text[at];
    if (byte <= ' ')
    {
      pending_space = out_size > 0;
      continue;
    }
    if (pending_space && out_size + 1 < excerpt_cap)
      out_excerpt[out_size++] = ' ';
    pending_space = 0;
    out_excerpt[out_size++] = (char)byte;
  }
  out_excerpt[out_size] = 0;
  if (out_size == 0)
    eightvo_copy_cstr(out_excerpt, excerpt_cap, "Bookmark");
}

FUNCTION B32
eightvo_toggle_current_bookmark(EightvoApp *app)
{
  if (!app || !epub_reader_is_open(&app->reader)) { return 0; }
  S32 existing = eightvo_current_bookmark_index(app);
  if (existing >= 0)
  {
    if (!eightvo_remove_bookmark_at(app, (U32)existing)) return 0;
    eightvo_set_statusf(app, "Bookmark removed");
    return 1;
  }
  if (app->bookmark_count >= EightvoBookmarkCap) return 0;
  U32 saved_count = app->bookmark_count;
  U64 saved_next_record_id = app->next_record_id;
  U64 saved_revision = app->annotation_revision;
  EightvoBookmark *bookmark = app->bookmarks + app->bookmark_count++;
  MemoryZeroStruct(bookmark);
  bookmark->id = app->next_record_id++;
  bookmark->spine_index = app->reader.active_spine_index;
  bookmark->byte_offset = app->reader.view_byte_offset;
  const char *section = eightvo_current_section_label(app);
  if (section && section[0])
    eightvo_copy_cstr(bookmark->label, ARRAY_COUNT(bookmark->label), section);
  else
    (void)cstr_format(bookmark->label, ARRAY_COUNT(bookmark->label),
                      "Page %llu", (unsigned long long)app->frame.page_index);
  eightvo_prepare_bookmark_excerpt(app, bookmark->excerpt,
                                    ARRAY_COUNT(bookmark->excerpt));
  app->annotation_revision += 1;
  if (!eightvo_commit_annotations(app))
  {
    app->bookmark_count = saved_count;
    app->next_record_id = saved_next_record_id;
    app->annotation_revision = saved_revision;
    MemoryZeroStruct(app->bookmarks + saved_count);
    eightvo_set_statusf(app, "Bookmark add failed");
    return 0;
  }
  eightvo_set_statusf(app, "Bookmark added");
  return 1;
}

FUNCTION B32
eightvo_toggle_highlight_star_at(EightvoApp *app, U32 index, B32 note)
{
  if (!app || index >= app->highlight_count ||
      (note && app->highlights[index].note[0] == 0) ||
      (!note && !app->highlights[index].is_highlight))
    return 0;
  EightvoHighlight saved = app->highlights[index];
  U64 saved_revision = app->annotation_revision;
  if (note)
    app->highlights[index].note_starred =
      !app->highlights[index].note_starred;
  else
    app->highlights[index].starred = !app->highlights[index].starred;
  app->annotation_revision += 1;
  if (eightvo_commit_annotations(app)) return 1;
  app->highlights[index] = saved;
  app->annotation_revision = saved_revision;
  eightvo_set_statusf(app, note ? "Note star failed" :
                                   "Highlight star failed");
  return 0;
}

FUNCTION EpubReaderResult
eightvo_navigate_to_location(EightvoApp *app,
                              U32 spine_index,
                              U64 byte_offset,
                              EpubReaderNavigationReason reason)
{
  if (!app || !epub_reader_is_open(&app->reader)) return EpubReaderResult_NotOpen;
  if (!eightvo_begin_document_mutation(app))
    return EpubReaderResult_InvalidInput;
  if (!eightvo_update_layout_inputs(app)) return EpubReaderResult_DocError;
  EpubReaderNavigationResult navigation = {0};
  EpubReaderResult result = epub_reader_navigate_to_location(
    &app->reader, spine_index, byte_offset, reason,
    app->layout_key, app->layout_config,
    (EpubReaderNavigationOptions){0}, &navigation);
  return eightvo_finish_semantic_navigation(app, result, "Navigate");
}

FUNCTION EpubReaderResult
eightvo_move_history(EightvoApp *app, B32 forward)
{
  if (!app || !epub_reader_is_open(&app->reader)) return EpubReaderResult_NotOpen;
  if (!eightvo_begin_document_mutation(app))
    return EpubReaderResult_InvalidInput;
  if (!eightvo_update_layout_inputs(app)) return EpubReaderResult_DocError;
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
  return eightvo_finish_semantic_navigation(app, result,
                                             forward ? "Forward" : "Back");
}

FUNCTION EpubReaderResult
eightvo_seek_location(EightvoApp *app, U64 location_index)
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
  return eightvo_navigate_to_location(app, spine_index, byte_offset,
                                       EpubReaderNavigationReason_Location);
}

FUNCTION B32
eightvo_set_highlight_color(EightvoApp *app, U32 color_index)
{
  if (!app || !app->reader.has_active_selection) { return 0; }
  S32 existing = eightvo_selection_highlight_index(app);
  U32 saved_count = app->highlight_count;
  U64 saved_next_record_id = app->next_record_id;
  U64 saved_revision = app->annotation_revision;
  EightvoHighlight saved = {0};
  EightvoHighlight *highlight = 0;
  if (existing >= 0)
  {
    highlight = app->highlights + existing;
    saved = *highlight;
  }
  else
  {
    if (app->highlight_count >= EightvoHighlightCap) return 0;
    highlight = app->highlights + app->highlight_count++;
    MemoryZeroStruct(highlight);
    highlight->id = app->next_record_id++;
    highlight->spine_index = app->reader.active_selection.spine_index;
    highlight->start_byte = app->reader.active_selection.text_byte_start;
    highlight->end_byte = app->reader.active_selection.text_byte_end;
    eightvo_copy_cstr(highlight->section, ARRAY_COUNT(highlight->section),
                       eightvo_current_section_label(app));
    eightvo_copy_cstr(highlight->text, ARRAY_COUNT(highlight->text),
                       app->selected_text);
  }
  highlight->is_highlight = 1;
  highlight->color_index = color_index % READER_VIEW_HIGHLIGHT_COLOR_CAP;
  app->annotation_revision += 1;
  if (!eightvo_commit_annotations(app))
  {
    if (existing >= 0) *highlight = saved;
    else
    {
      app->highlight_count = saved_count;
      app->next_record_id = saved_next_record_id;
      MemoryZeroStruct(app->highlights + saved_count);
    }
    app->annotation_revision = saved_revision;
    eightvo_set_statusf(app, "Highlight save failed");
    return 0;
  }
  eightvo_set_statusf(app, "Highlight saved");
  return 1;
}

FUNCTION B32
eightvo_save_note_at_index(EightvoApp *app,
                            U32 index,
                            ReaderViewText note)
{
  if (!app || index >= app->highlight_count || note.size < 0 ||
      note.size >= (UI0S32)ARRAY_COUNT(app->highlights[index].note) ||
      (note.size > 0 && !note.data))
    return 0;
  EightvoHighlight *highlight = app->highlights + index;
  EightvoHighlight saved = *highlight;
  U64 saved_revision = app->annotation_revision;
  eightvo_copy_bytes(highlight->note, ARRAY_COUNT(highlight->note),
                      (const U8 *)note.data, (U64)note.size);
  app->annotation_revision += 1;
  if (!eightvo_commit_annotations(app))
  {
    *highlight = saved;
    app->annotation_revision = saved_revision;
    eightvo_set_statusf(app, "Note save failed");
    return 0;
  }
  eightvo_set_statusf(app, "Note saved");
  return 1;
}

FUNCTION B32
eightvo_save_selection_note(EightvoApp *app, ReaderViewText note)
{
  if (!app || !app->reader.has_active_selection || note.size < 0 ||
      note.size >= EightvoNoteCap || (note.size > 0 && !note.data))
    return 0;
  S32 index = eightvo_selection_highlight_index(app);
  if (index >= 0)
    return eightvo_save_note_at_index(app, (U32)index, note);
  if (app->highlight_count >= EightvoHighlightCap) return 0;

  U32 saved_count = app->highlight_count;
  U64 saved_next_record_id = app->next_record_id;
  U64 saved_revision = app->annotation_revision;
  EightvoHighlight *highlight = app->highlights + app->highlight_count++;
  MemoryZeroStruct(highlight);
  highlight->id = app->next_record_id++;
  highlight->spine_index = app->reader.active_selection.spine_index;
  highlight->start_byte = app->reader.active_selection.text_byte_start;
  highlight->end_byte = app->reader.active_selection.text_byte_end;
  highlight->color_index = 0;
  highlight->is_highlight = 1;
  eightvo_copy_cstr(highlight->section, ARRAY_COUNT(highlight->section),
                     eightvo_current_section_label(app));
  eightvo_copy_cstr(highlight->text, ARRAY_COUNT(highlight->text),
                     app->selected_text);
  eightvo_copy_bytes(highlight->note, ARRAY_COUNT(highlight->note),
                      (const U8 *)note.data, (U64)note.size);
  app->annotation_revision += 1;
  if (!eightvo_commit_annotations(app))
  {
    app->highlight_count = saved_count;
    app->next_record_id = saved_next_record_id;
    app->annotation_revision = saved_revision;
    MemoryZeroStruct(app->highlights + saved_count);
    eightvo_set_statusf(app, "Note save failed");
    return 0;
  }
  eightvo_set_statusf(app, "Note saved");
  return 1;
}

FUNCTION ReaderViewSurfaceStatus
eightvo_reader_view_status(ReaderViewLoadState state, const char *message)
{
  ReaderViewSurfaceStatus result = {0};
  result.state = state;
  result.message = eightvo_reader_view_text(message);
  return result;
}

FUNCTION UI0ThemeProfile
eightvo_theme_profile(EightvoTheme theme)
{
  if (theme < 0 || theme >= EightvoTheme_Count)
    theme = EightvoTheme_Dark;
  return ui0_theme_profile_for_kind((UI0ThemeProfileKind)theme);
}

FUNCTION EightvoReaderContentTheme
eightvo_reader_content_theme(EightvoTheme theme)
{
  EightvoReaderContentTheme result = {0};
  switch (theme)
  {
    case EightvoTheme_Light:
      result = (EightvoReaderContentTheme){
        0x00FFFDF9U, 0x001B1A18U, 0x0047423BU, 0x007A7368U,
        0x00D95618U, 0x00FFE7D4U, 0x00D8D7D4U, 0x00F6B36FU,
        0x00FFF2A6U, 0x00D95618U,
      };
      break;
    case EightvoTheme_CoralDark:
      result = (EightvoReaderContentTheme){
        0x00464644U, 0x00F5EBDDU, 0x00DED4C8U, 0x00C2B6ACU,
        0x00E85D56U, 0x0063423EU, 0x0062605EU, 0x009A3034U,
        0x00524A25U, 0x00E85D56U,
      };
      break;
    case EightvoTheme_CoralLight:
      result = (EightvoReaderContentTheme){
        0x00F3E8DBU, 0x00333230U, 0x0053514FU, 0x006F6D68U,
        0x00E85D56U, 0x00F3C2B9U, 0x00D4D0CCU, 0x00EE9B94U,
        0x00F4DFA3U, 0x00E85D56U,
      };
      break;
    case EightvoTheme_BlueDark:
      result = (EightvoReaderContentTheme){
        0x000D1824U, 0x00EAF0F7U, 0x00B8C7D8U, 0x007E8FA3U,
        0x007C93FFU, 0x00345F91U, 0x003D454EU, 0x004F64BFU,
        0x004D4A16U, 0x007C93FFU,
      };
      break;
    case EightvoTheme_BlueLight:
      result = (EightvoReaderContentTheme){
        0x00FFFDF9U, 0x00121A22U, 0x00334252U, 0x006E7680U,
        0x00365CE7U, 0x00E6EEFFU, 0x00D6DADFU, 0x008BAEFFU,
        0x00FFF2A6U, 0x00365CE7U,
      };
      break;
    case EightvoTheme_Dark:
    default:
      result = (EightvoReaderContentTheme){
        0x00181716U, 0x00F2F0EAU, 0x00C9C4BAU, 0x008D877BU,
        0x00F26A1BU, 0x004D3424U, 0x004A4947U, 0x008F430FU,
        0x004D4A16U, 0x00F26A1BU,
      };
      break;
  }
  return result;
}

FUNCTION void
eightvo_prepare_selected_text(EightvoApp *app)
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
  eightvo_copy_bytes(app->selected_text, ARRAY_COUNT(app->selected_text),
                      app->reader.spine_text.str + start, end - start);
}

FUNCTION void
eightvo_prepare_reader_view_settings(EightvoApp *app)
{
  static const U32 families[] = {
    FontProviderBookContentFamily_Georgia,
    FontProviderBookContentFamily_NotoSerif,
    FontProviderBookContentFamily_PalatinoLinotype,
    FontProviderBookContentFamily_BookAntiqua,
    FontProviderBookContentFamily_TimesNewRoman,
  };
  static const char *size_labels[] = {"Default", "Large", "Larger", "Largest"};
  static const char *spacing_labels[] = {"Compact", "Comfortable", "Spacious"};
  U32 font_count = 0;
  for (U32 index = 0; index < ARRAY_COUNT(families); index += 1)
  {
    U32 family = families[index];
    if (!epub_reader_typography_family_available(&app->reader.typography, family))
      continue;
    ReaderViewChoice *choice = app->reader_view_font_choices + font_count++;
    *choice = (ReaderViewChoice){
      .key = 1000ull + family,
      .label = eightvo_reader_view_text(
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
      .label = eightvo_reader_view_text("Georgia"),
      .flags = ReaderViewChoice_Enabled | ReaderViewChoice_Selected,
    };
    font_count = 1;
  }
  for (U32 index = 0; index < ARRAY_COUNT(size_labels); index += 1)
  {
    app->reader_view_size_choices[index] = (ReaderViewChoice){
      .key = 2000ull + index,
      .label = eightvo_reader_view_text(size_labels[index]),
      .flags = ReaderViewChoice_Enabled |
        (index == app->text_size_index ? ReaderViewChoice_Selected : 0),
    };
  }
  for (U32 index = 0; index < ARRAY_COUNT(spacing_labels); index += 1)
  {
    app->reader_view_spacing_choices[index] = (ReaderViewChoice){
      .key = 3000ull + index,
      .label = eightvo_reader_view_text(spacing_labels[index]),
      .flags = ReaderViewChoice_Enabled |
        (index == app->line_spacing_index ? ReaderViewChoice_Selected : 0),
    };
  }
  for (U32 index = 0; index < EightvoTheme_Count; index += 1)
  {
    UI0ThemeProfile profile =
      ui0_theme_profile_for_kind((UI0ThemeProfileKind)index);
    app->reader_view_theme_choices[index] = (ReaderViewChoice){
      .key = 4000ull + index,
      .label = eightvo_reader_view_text(profile.label),
      .flags = ReaderViewChoice_Enabled |
        (index == (U32)app->theme ? ReaderViewChoice_Selected : 0),
    };
  }
  app->reader_view_settings[0] = (ReaderViewSettingControl){
    .kind = ReaderViewSetting_FontFamily,
    .label = eightvo_reader_view_text("Font"),
    .status = eightvo_reader_view_status(ReaderViewLoad_Ready, 0),
    .choices = {
      .items = app->reader_view_font_choices,
      .count = (UI0S32)font_count,
      .presentation = ReaderViewChoicePresentation_Menu,
    },
  };
  app->reader_view_settings[1] = (ReaderViewSettingControl){
    .kind = ReaderViewSetting_FontSize,
    .label = eightvo_reader_view_text("Size"),
    .status = eightvo_reader_view_status(ReaderViewLoad_Ready, 0),
    .choices = {
      .items = app->reader_view_size_choices,
      .count = (UI0S32)ARRAY_COUNT(app->reader_view_size_choices),
      .presentation = ReaderViewChoicePresentation_Stepper,
    },
  };
  app->reader_view_settings[2] = (ReaderViewSettingControl){
    .kind = ReaderViewSetting_LineSpacing,
    .label = eightvo_reader_view_text("Spacing"),
    .status = eightvo_reader_view_status(ReaderViewLoad_Ready, 0),
    .choices = {
      .items = app->reader_view_spacing_choices,
      .count = (UI0S32)ARRAY_COUNT(app->reader_view_spacing_choices),
      .presentation = ReaderViewChoicePresentation_Stepper,
    },
  };
  app->reader_view_settings[3] = (ReaderViewSettingControl){
    .kind = ReaderViewSetting_Theme,
    .label = eightvo_reader_view_text("Theme"),
    .status = eightvo_reader_view_status(ReaderViewLoad_Ready, 0),
    .choices = {
      .items = app->reader_view_theme_choices,
      .count = (UI0S32)ARRAY_COUNT(app->reader_view_theme_choices),
      .presentation = ReaderViewChoicePresentation_Segments,
    },
  };
}

FUNCTION void
eightvo_prepare_reader_view_toc(EightvoApp *app)
{
  U32 count = MIN(app->frame.section_item_count,
                  (U32)READER_VIEW_TOC_ROW_CAP);
  for (U32 index = 0; index < count; index += 1)
  {
    const EpubReaderFrameSectionItem *source = app->frame.section_items + index;
    app->reader_view_toc_rows[index] = (ReaderViewTocRow){
      .key = (ReaderViewKey)source->nav_index + 1,
      .depth = source->depth,
      .label = eightvo_reader_view_bytes(source->label, source->label_length),
      .detail = eightvo_reader_view_bytes(source->detail, source->detail_length),
      .flags = ReaderViewRow_Enabled |
        (source->active ? ReaderViewRow_Current | ReaderViewRow_Selected : 0),
    };
  }
  app->reader_view_projection.toc = (ReaderViewTocProjection){
    .status = eightvo_reader_view_status(ReaderViewLoad_Ready, 0),
    .rows = app->reader_view_toc_rows,
    .row_count = (UI0S32)count,
    .total_count = app->frame.section_item_total_count,
  };
}

FUNCTION void
eightvo_prepare_reader_view_find(EightvoApp *app)
{
  U32 count = MIN(app->frame.search_item_count,
                  (U32)READER_VIEW_FIND_ROW_CAP);
  if (app->frame.search_query.size == 0)
  {
    cstr_format(app->reader_view_find_status,
                sizeof(app->reader_view_find_status),
                "Type and press Enter");
  }
  else if (app->frame.search_total_count == 0)
  {
    cstr_format(app->reader_view_find_status,
                sizeof(app->reader_view_find_status),
                "No matches");
  }
  else
  {
    cstr_format(app->reader_view_find_status,
                sizeof(app->reader_view_find_status),
                "%u matches",
                (unsigned)app->frame.search_total_count);
  }
  for (U32 index = 0; index < count; index += 1)
  {
    const EpubReaderFrameSearchItem *source = app->frame.search_items + index;
    app->reader_view_find_rows[index] = (ReaderViewFindRow){
      .key = 0x100000ull + index,
      .section = eightvo_reader_view_bytes(source->section_label,
                                             source->section_label_length),
      .excerpt = eightvo_reader_view_bytes(source->snippet,
                                             source->snippet_length),
      .match_start = source->match_start_in_snippet,
      .match_size = source->match_size_in_snippet,
      .flags = ReaderViewRow_Enabled |
        (app->frame.search_has_active &&
         app->frame.search_active_index == index ? ReaderViewRow_Selected : 0),
    };
  }
  app->reader_view_projection.find = (ReaderViewFindProjection){
    .status = eightvo_reader_view_status(ReaderViewLoad_Ready,
                                          app->reader_view_find_status),
    .committed_query = eightvo_reader_view_bytes(
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

FUNCTION U64
eightvo_reader_location_for_position(EightvoApp *app,
                                      U32 spine_index,
                                      U64 byte_offset)
{
  if (!app || !epub_reader_is_open(&app->reader)) return 1;
  EpubReader *reader = &app->reader;
  U32 pump_cap = reader->location_spine_count ?
                 reader->location_spine_count : 1;
  for (U32 pump = 0;
       pump < pump_cap &&
       !(reader->location_cache_complete && reader->location_cache_valid);
       pump += 1)
  {
    (void)epub_reader_location_cache_ensure(reader);
    if (reader->location_spine_count > pump_cap)
      pump_cap = reader->location_spine_count;
  }
  if (!reader->location_cache_complete || !reader->location_cache_valid ||
      !reader->location_spine_text_sizes ||
      spine_index >= reader->location_spine_count ||
      reader->location_total_text_bytes == 0)
    return 1;

  U64 global_byte = 0;
  for (U32 index = 0; index < spine_index; index += 1)
    global_byte += reader->location_spine_text_sizes[index];
  global_byte += byte_offset;
  enum { EightvoLocationBytes = 128 };
  return global_byte / EightvoLocationBytes + 1;
}

FUNCTION ReaderViewText
eightvo_reader_view_right_secondary(EightvoApp *app,
                                     U32 row_index,
                                     const char *kind,
                                     U32 spine_index,
                                     U64 byte_offset)
{
  if (!app || !kind || row_index >= READER_VIEW_RIGHT_ROW_CAP)
    return (ReaderViewText){0};
  char *storage = app->reader_view_right_secondary[row_index];
  /*
  Hidden annotation rows must not synchronously extract every EPUB spine just
  to format their global location labels. The Win32 host warms that bounded
  reader0 cache after the first stable presentation. Headless contract smokes
  still build exact rows immediately, and an explicitly opened panel may
  complete any remaining cache work before showing its metadata.
  */
  if ((app->window || app->first_reader_present_pending) &&
      !app->reader_view_state.right_panel_open &&
      !(app->reader.location_cache_complete &&
        app->reader.location_cache_valid))
  {
    storage[0] = 0;
    return eightvo_reader_view_text(storage);
  }
  (void)cstr_format(storage,
                    EightvoRecordLabelCap,
                    "%s - re10 loc %llu",
                    kind,
                    (unsigned long long)eightvo_reader_location_for_position(
                      app, spine_index, byte_offset));
  return eightvo_reader_view_text(storage);
}

FUNCTION UI0Color
eightvo_reader_view_rail_color(const EightvoApp *app, U32 color_index)
{
  B32 dark = app && eightvo_theme_profile(app->theme).appearance ==
                      UI0AppearanceMode_Dark;
  switch (color_index)
  {
    case 1:
      return dark ? UI0_COLOR_RGB(0x47, 0x35, 0x5c) :
                    UI0_COLOR_RGB(0xff, 0xd4, 0xec);
    case 2:
      return dark ? UI0_COLOR_RGB(0x2a, 0x46, 0x62) :
                    UI0_COLOR_RGB(0xcd, 0xe7, 0xff);
    case 3:
      return dark ? UI0_COLOR_RGB(0x52, 0x3f, 0x1c) :
                    UI0_COLOR_RGB(0xff, 0xdc, 0xa8);
    default:
      return dark ? UI0_COLOR_RGB(0x4d, 0x4a, 0x16) :
                    UI0_COLOR_RGB(0xff, 0xf2, 0xa6);
  }
}

FUNCTION ReaderViewKey
eightvo_reader_view_right_key(EightvoApp *app,
                               ReaderViewRightRowKind row_kind,
                               U64 record_id)
{
  if (!app || record_id == 0) return 0;
  U64 values[3] = {
    (U64)row_kind,
    row_kind == ReaderViewRightRow_Bookmark ? 0 : record_id,
    row_kind == ReaderViewRightRow_Bookmark ? record_id : 0,
  };
  ReaderViewKey key = u64_hash_bytes(values, sizeof(values));
  if (key == 0) key = 1;
  for (U32 attempt = 0;
       attempt <= app->reader_view_right_source_count;
       attempt += 1)
  {
    B32 collision = 0;
    for (U32 prior = 0;
         prior < app->reader_view_right_source_count;
         prior += 1)
    {
      if (app->reader_view_right_sources[prior].key == key)
      {
        collision = 1;
        break;
      }
    }
    if (!collision) return key;
    key ^= 0x9e3779b97f4a7c15ull +
           app->reader_view_right_source_count + attempt;
    if (key == 0) key = 1;
  }
  return 0;
}

FUNCTION ReaderViewKey
eightvo_reader_view_register_right_source(EightvoApp *app,
                                            ReaderViewRightRowKind row_kind,
                                            U64 record_id)
{
  if (!app || app->reader_view_right_source_count >=
              READER_VIEW_RIGHT_ROW_CAP)
    return 0;
  ReaderViewKey key =
    eightvo_reader_view_right_key(app, row_kind, record_id);
  if (key == 0) return 0;
  app->reader_view_right_sources[app->reader_view_right_source_count++] =
    (EightvoReaderViewRightSource){
      .key = key,
      .row_kind = row_kind,
      .record_id = record_id,
    };
  return key;
}

FUNCTION const EightvoReaderViewRightSource *
eightvo_reader_view_right_source(const EightvoApp *app,
                                  ReaderViewKey key,
                                  ReaderViewRightRowKind row_kind)
{
  if (!app || key == 0) return 0;
  for (U32 index = 0;
       index < app->reader_view_right_source_count;
       index += 1)
  {
    const EightvoReaderViewRightSource *source =
      app->reader_view_right_sources + index;
    if (source->key == key && source->row_kind == row_kind)
      return source;
  }
  return 0;
}

FUNCTION S32
eightvo_reader_view_right_candidate_compare(
  const EightvoReaderViewRightCandidate *left,
  const EightvoReaderViewRightCandidate *right)
{
  if (!left || !right) return 0;
  if (left->spine_index != right->spine_index)
    return left->spine_index < right->spine_index ? -1 : 1;
  if (left->byte_offset != right->byte_offset)
    return left->byte_offset < right->byte_offset ? -1 : 1;
  if (left->row_kind != right->row_kind)
    return left->row_kind < right->row_kind ? -1 : 1;
  if (left->record_id != right->record_id)
    return left->record_id < right->record_id ? -1 : 1;
  if (left->source_index != right->source_index)
    return left->source_index < right->source_index ? -1 : 1;
  return 0;
}

FUNCTION B32
eightvo_reader_view_push_right_candidate(
  EightvoApp *app,
  ReaderViewRightRowKind row_kind,
  U32 source_index,
  U32 spine_index,
  U64 byte_offset,
  U64 record_id)
{
  if (!app || record_id == 0 ||
      app->reader_view_right_candidate_count >=
        EightvoReaderViewRightCandidateCap)
    return 0;
  app->reader_view_right_candidates[app->reader_view_right_candidate_count++] =
    (EightvoReaderViewRightCandidate){
      .row_kind = row_kind,
      .source_index = source_index,
      .spine_index = spine_index,
      .byte_offset = byte_offset,
      .record_id = record_id,
    };
  return 1;
}

FUNCTION void
eightvo_reader_view_sort_right_candidates(EightvoApp *app)
{
  if (!app) return;
  for (U32 index = 1;
       index < app->reader_view_right_candidate_count;
       index += 1)
  {
    EightvoReaderViewRightCandidate candidate =
      app->reader_view_right_candidates[index];
    U32 insert = index;
    while (insert > 0 &&
           eightvo_reader_view_right_candidate_compare(
             &candidate,
             app->reader_view_right_candidates + insert - 1) < 0)
    {
      app->reader_view_right_candidates[insert] =
        app->reader_view_right_candidates[insert - 1];
      insert -= 1;
    }
    app->reader_view_right_candidates[insert] = candidate;
  }
}

FUNCTION void
eightvo_prepare_reader_view_right_rows(EightvoApp *app)
{
  if (!app) return;
  U32 count = 0;
  UI0U64 bookmark_total = app->bookmark_count;
  UI0U64 highlight_total = 0;
  UI0U64 note_total = 0;
  for (U32 index = 0; index < app->highlight_count; index += 1)
  {
    if (app->highlights[index].is_highlight) highlight_total += 1;
    if (app->highlights[index].note[0] != 0) note_total += 1;
  }
  UI0U64 all_total = bookmark_total + highlight_total + note_total;
  app->reader_view_right_source_count = 0;
  app->reader_view_right_candidate_count = 0;
  B32 show_bookmarks =
    app->reader_view_state.right_filter == ReaderViewRightFilter_All ||
    app->reader_view_state.right_filter == ReaderViewRightFilter_Bookmarks;
  B32 show_highlights =
    app->reader_view_state.right_filter == ReaderViewRightFilter_All ||
    app->reader_view_state.right_filter == ReaderViewRightFilter_Highlights;
  B32 show_notes =
    app->reader_view_state.right_filter == ReaderViewRightFilter_All ||
    app->reader_view_state.right_filter == ReaderViewRightFilter_Notes;
  for (U32 index = 0; index < app->bookmark_count; index += 1)
  {
    if (!show_bookmarks) continue;
    const EightvoBookmark *bookmark = app->bookmarks + index;
    (void)eightvo_reader_view_push_right_candidate(
      app, ReaderViewRightRow_Bookmark, index,
      bookmark->spine_index, bookmark->byte_offset, bookmark->id);
  }
  for (U32 index = 0; index < app->highlight_count; index += 1)
  {
    const EightvoHighlight *highlight = app->highlights + index;
    B32 has_note = highlight->note[0] != 0;
    if (highlight->is_highlight && show_highlights)
      (void)eightvo_reader_view_push_right_candidate(
        app, ReaderViewRightRow_Highlight, index,
        highlight->spine_index, highlight->start_byte, highlight->id);
    if (has_note && show_notes)
      (void)eightvo_reader_view_push_right_candidate(
        app, ReaderViewRightRow_Note, index,
        highlight->spine_index, highlight->start_byte, highlight->id);
  }
  eightvo_reader_view_sort_right_candidates(app);
  for (U32 candidate_index = 0;
       candidate_index < app->reader_view_right_candidate_count &&
       count < READER_VIEW_RIGHT_ROW_CAP;
       candidate_index += 1)
  {
    const EightvoReaderViewRightCandidate *candidate =
      app->reader_view_right_candidates + candidate_index;
    U32 row_index = count++;
    ReaderViewKey key = eightvo_reader_view_register_right_source(
      app, candidate->row_kind, candidate->record_id);
    if (candidate->row_kind == ReaderViewRightRow_Bookmark)
    {
      const EightvoBookmark *bookmark =
        app->bookmarks + candidate->source_index;
      app->reader_view_right_rows[row_index] = (ReaderViewRightRow){
        .key = key,
        .kind = ReaderViewRightRow_Bookmark,
        .section = eightvo_reader_view_text(bookmark->label),
        .primary = eightvo_reader_view_text(bookmark->excerpt),
        .secondary = eightvo_reader_view_right_secondary(
          app, row_index, "Bookmark", bookmark->spine_index,
          bookmark->byte_offset),
        .rail_color = 0,
        /* A bookmark is itself the starred state in the frozen re10 model;
           invoking its inline star removes the bookmark. */
        .flags = ReaderViewRow_Enabled | ReaderViewRow_Starred |
          (eightvo_current_bookmark_index(app) ==
             (S32)candidate->source_index ? ReaderViewRow_Current : 0),
        .actions = ReaderViewRightAction_Activate |
                   ReaderViewRightAction_ToggleStar |
                   ReaderViewRightAction_Delete,
      };
      continue;
    }
    const EightvoHighlight *highlight =
      app->highlights + candidate->source_index;
    B32 note = candidate->row_kind == ReaderViewRightRow_Note;
    B32 attached = note && row_index > 0 &&
      app->reader_view_right_sources[row_index - 1].record_id ==
        candidate->record_id &&
      app->reader_view_right_sources[row_index - 1].row_kind ==
        ReaderViewRightRow_Highlight;
    app->reader_view_right_rows[row_index] = (ReaderViewRightRow){
      .key = key,
      .kind = candidate->row_kind,
      .section = eightvo_reader_view_text(highlight->section),
      .primary = eightvo_reader_view_text(
        note ? highlight->note : highlight->text),
      .secondary = eightvo_reader_view_right_secondary(
        app, row_index, note ? "Note" : "Highlight",
        highlight->spine_index, highlight->start_byte),
      .color_key = 5000ull + highlight->color_index,
      .rail_color = eightvo_reader_view_rail_color(
        app, highlight->color_index),
      .flags = ReaderViewRow_Enabled |
        (note ? (highlight->note_starred ? ReaderViewRow_Starred : 0) :
                (highlight->starred ? ReaderViewRow_Starred : 0)) |
        (attached ? ReaderViewRow_AttachedToPrevious : 0),
      .actions = ReaderViewRightAction_Activate |
                 ReaderViewRightAction_ToggleStar |
                 ReaderViewRightAction_Delete |
                 (note ? ReaderViewRightAction_EditNote : 0),
    };
  }
  app->reader_view_projection.right = (ReaderViewRightProjection){
    .status = eightvo_reader_view_status(ReaderViewLoad_Ready, 0),
    .rows = app->reader_view_right_rows,
    .row_count = (UI0S32)count,
    .total_count = app->reader_view_right_candidate_count,
    .has_more = app->reader_view_right_candidate_count > count,
    .all_count = all_total,
    .bookmark_count = bookmark_total,
    .highlight_count = highlight_total,
    .note_count = note_total,
    .available_filters = ReaderViewRightFilterFlag_All |
                         ReaderViewRightFilterFlag_Bookmarks |
                         ReaderViewRightFilterFlag_Highlights |
                         ReaderViewRightFilterFlag_Notes,
  };
}

FUNCTION void
eightvo_reader_view_clear_annotation_note_target(EightvoApp *app)
{
  if (!app) return;
  app->annotation_note_selection_key = 0;
  app->annotation_note_document_id = 0;
  app->annotation_note_highlight_id = 0;
  app->annotation_note_start_byte = 0;
  app->annotation_note_end_byte = 0;
  app->annotation_note_spine_index = 0;
}

FUNCTION B32
eightvo_reader_view_prepare_annotation_note_selection(
  EightvoApp *app,
  ReaderViewSelectionProjection *out_selection)
{
  if (!app || !out_selection) return 0;
  if (app->reader_view_state.popup != ReaderViewPopup_NoteEditor)
  {
    eightvo_reader_view_clear_annotation_note_target(app);
    return 0;
  }
  if (app->annotation_note_selection_key == 0 ||
      app->annotation_note_document_id != app->frame.document_id ||
      app->annotation_note_selection_key !=
        app->reader_view_state.note_selection_key)
  {
    return 0;
  }
  S32 index = eightvo_highlight_index(
    app, app->annotation_note_highlight_id);
  if (index < 0) return 0;
  const EightvoHighlight *highlight = app->highlights + index;
  if (highlight->spine_index != app->annotation_note_spine_index ||
      highlight->start_byte != app->annotation_note_start_byte ||
      highlight->end_byte != app->annotation_note_end_byte ||
      highlight->note[0] == 0)
  {
    return 0;
  }
  *out_selection = (ReaderViewSelectionProjection){
    .status = eightvo_reader_view_status(ReaderViewLoad_Ready, 0),
    .selection_key = app->annotation_note_selection_key,
    .revision = app->annotation_revision,
    .selected_text = eightvo_reader_view_text(highlight->text),
    .note_text = reader_view_note_draft(&app->reader_view_state),
    .flags = ReaderViewSelection_Active |
             ReaderViewSelection_CanEditNote |
             ReaderViewSelection_CanDeleteNote,
  };
  return 1;
}

FUNCTION void
eightvo_prepare_reader_view_selection(EightvoApp *app)
{
  static const char *color_labels[] = {"Yellow", "Pink", "Blue", "Orange"};
  ReaderViewSelectionProjection selection = {
    .status = eightvo_reader_view_status(ReaderViewLoad_Ready, 0),
  };
  if (eightvo_reader_view_prepare_annotation_note_selection(
        app, &selection))
  {
    app->reader_view_projection.selection = selection;
    return;
  }
  eightvo_prepare_selected_text(app);
  /* The concrete selection may update on every drag move, but Reader View
     must not publish/open its action surface until the host owns the release. */
  if (app->reader.has_active_selection && app->selected_text[0] &&
      !app->selection_dragging)
  {
    DocSelection range = app->reader.active_selection;
    U64 key_parts[3] = {range.spine_index, range.text_byte_start, range.text_byte_end};
    selection.selection_key = u64_hash_bytes(key_parts, sizeof(key_parts));
    if (selection.selection_key == 0) selection.selection_key = 1;
    selection.revision = app->annotation_revision;
    selection.selected_text = eightvo_reader_view_text(app->selected_text);
    selection.flags = ReaderViewSelection_Active |
                      ReaderViewSelection_CanCopy |
                      ReaderViewSelection_CanHighlight |
                      ReaderViewSelection_CanAddNote |
                      ReaderViewSelection_CanDictionary |
                      ReaderViewSelection_CanWebLookup |
                      ReaderViewSelection_CanTranslate;
    selection.anchor_rect = app->selection_anchor_rect;
    S32 highlight_index = eightvo_selection_highlight_index(app);
    if (highlight_index >= 0)
    {
      const EightvoHighlight *highlight = app->highlights + highlight_index;
      selection.note_text = eightvo_reader_view_text(highlight->note);
      selection.flags |= ReaderViewSelection_CanEditNote;
      if (highlight->is_highlight)
      {
        selection.current_color_key = 5000ull + highlight->color_index;
        selection.flags |= ReaderViewSelection_CanRemoveHighlight;
      }
      if (highlight->note[0]) selection.flags |= ReaderViewSelection_CanDeleteNote;
    }
    for (U32 index = 0; index < ARRAY_COUNT(color_labels); index += 1)
    {
      app->reader_view_color_choices[index] = (ReaderViewChoice){
        .key = 5000ull + index,
        .label = eightvo_reader_view_text(color_labels[index]),
        .flags = ReaderViewChoice_Enabled |
          (selection.current_color_key == 5000ull + index ?
            ReaderViewChoice_Selected : 0),
        .visual_color = eightvo_reader_view_rail_color(app, index),
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
eightvo_prepare_reader_view_projection(EightvoApp *app)
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
                        ReaderViewFeature_Lookup |
                        ReaderViewFeature_Export;
  projection.document_flags = ReaderViewDocument_CanOpen |
                              ReaderViewDocument_CanToggleFullscreen;
  if (open)
  {
    projection.document_flags |= ReaderViewDocument_Open;
    if (app->reader.active_spine_index > 0 || app->frame.page_index > 1)
      projection.document_flags |= ReaderViewDocument_CanGoPreviousPage;
    if (app->reader.active_spine_index + 1 < app->layout_key.spine_count ||
        app->frame.page_count == 0 ||
        app->frame.page_index < app->frame.page_count)
      projection.document_flags |= ReaderViewDocument_CanGoNextPage;
    if (app->frame.history_back_count > 0)
      projection.document_flags |= ReaderViewDocument_CanGoBack;
    if (app->frame.history_forward_count > 0)
      projection.document_flags |= ReaderViewDocument_CanGoForward;
    S32 bookmark_index = eightvo_current_bookmark_index(app);
    if (bookmark_index >= 0)
    {
      projection.document_flags |= ReaderViewDocument_CurrentBookmarked;
      projection.current_bookmark_key = app->bookmarks[bookmark_index].id;
    }
  }
  if (app->fullscreen.active)
    projection.document_flags |= ReaderViewDocument_Fullscreen;
  if (open)
    projection.content = eightvo_reader_view_status(ReaderViewLoad_Ready, 0);
  else if (app->document_state == ReaderViewLoad_Loading)
    projection.content = eightvo_reader_view_status(ReaderViewLoad_Loading,
                                                      app->status);
  else if (app->document_state == ReaderViewLoad_Error)
    projection.content = eightvo_reader_view_status(ReaderViewLoad_Error,
                                                      app->status);
  else
    projection.content = eightvo_reader_view_status(
      ReaderViewLoad_Empty, "Open a book to begin reading.");
  projection.labels = reader_view_default_english_labels();
  projection.labels.annotations = eightvo_reader_view_text("Annotations");
  projection.labels.highlights =
    eightvo_reader_view_text("All Highlight Colors");
  projection.chrome_title = eightvo_reader_view_text("Reader");
  app->document_title[0] = 0;
  if (open)
  {
    eightvo_copy_cstr(app->document_title,
                      ARRAY_COUNT(app->document_title),
                      eightvo_current_section_label(app));
  }
  projection.document_title = eightvo_reader_view_text(app->document_title);
  app->reader_view_projection = projection;

  eightvo_prepare_reader_view_settings(app);
  app->reader_view_projection.settings = (ReaderViewReadingSettingsProjection){
    .status = eightvo_reader_view_status(ReaderViewLoad_Ready, 0),
    .items = app->reader_view_settings,
    .count = READER_VIEW_SETTING_CAP,
  };

  if (open)
  {
    EpubReaderLocationSummary location = epub_reader_location_summary(&app->reader);
    if (location.available && location.location_count > 0)
      (void)cstr_format(app->progress_label, ARRAY_COUNT(app->progress_label),
                        "%llu%%   Location %llu of %llu",
                        (unsigned long long)location.percent,
                        (unsigned long long)location.location_index,
                        (unsigned long long)location.location_count);
    else if (app->frame.page_count > 0)
      (void)cstr_format(app->progress_label, ARRAY_COUNT(app->progress_label),
                        "Page %llu of %llu",
                        (unsigned long long)app->frame.page_index,
                        (unsigned long long)app->frame.page_count);
    else
      (void)cstr_format(app->progress_label, ARRAY_COUNT(app->progress_label),
                        "Page %llu",
                        (unsigned long long)app->frame.page_index);
    app->reader_view_projection.progress = (ReaderViewProgressProjection){
      .status = eightvo_reader_view_status(ReaderViewLoad_Ready, 0),
      .location_index = location.available ?
                        (location.location_index > 0 ?
                          location.location_index - 1 : 0) :
                        (app->frame.page_count > 0 && app->frame.page_index > 0 ?
                          app->frame.page_index - 1 : 0),
      .location_count = location.available ? location.location_count :
                        app->frame.page_count,
      .page_index = app->frame.page_count > 0 && app->frame.page_index > 0 ?
                    app->frame.page_index - 1 : 0,
      .page_count = app->frame.page_count,
      .chapter = eightvo_reader_view_text(eightvo_current_section_label(app)),
      .label = eightvo_reader_view_text(app->progress_label),
      .can_seek = location.available,
    };
    eightvo_prepare_reader_view_toc(app);
    eightvo_prepare_reader_view_find(app);
    eightvo_prepare_reader_view_right_rows(app);
    eightvo_prepare_reader_view_selection(app);
  }
  else
  {
    ReaderViewSurfaceStatus unavailable =
      eightvo_reader_view_status(ReaderViewLoad_Unavailable,
                                  "Open a book first");
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

  UI0ThemeProfile profile = eightvo_theme_profile(app->theme);
  app->reader_view_theme = profile.resolved;
  app->reader_content_theme = eightvo_reader_content_theme(app->theme);
}

FUNCTION B32
eightvo_reader_view_focus_is(const EightvoApp *app,
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

FUNCTION B32
eightvo_reader_view_focus_control_is(const EightvoApp *app,
                                      ReaderViewSemanticControl control)
{
  if (!app || !app->reader_view_frame.semantic_nodes ||
      app->reader_view_state.focus_id == 0 ||
      control == ReaderViewSemanticControl_None)
    return 0;
  for (UI0S32 index = 0;
       index < app->reader_view_frame.semantic_node_count;
       index += 1)
  {
    const ReaderViewSemanticNode *node =
      app->reader_view_frame.semantic_nodes + index;
    if (node->id == app->reader_view_state.focus_id &&
        node->control == control)
      return 1;
  }
  return 0;
}

FUNCTION B32
eightvo_reader_view_text_editing(const EightvoApp *app)
{
  if (!app) return 0;
  return (app->reader_view_state.left_panel == ReaderViewLeftPanel_Find &&
          (app->reader_view_state.pending_left_panel_focus ==
             ReaderViewLeftPanel_Find ||
           eightvo_reader_view_focus_control_is(
             app, ReaderViewSemanticControl_FindInput))) ||
         (app->reader_view_state.popup == ReaderViewPopup_NoteEditor &&
          eightvo_reader_view_focus_is(app, ReaderViewSemantic_TextArea));
}

FUNCTION B32
eightvo_reader_view_space_activates_focus(const EightvoApp *app)
{
  return app && app->reader_view_state.focus_id != 0 &&
         !eightvo_reader_view_text_editing(app);
}

FUNCTION B32
eightvo_reader_view_horizontal_move_is_shared(const EightvoApp *app)
{
  return eightvo_reader_view_text_editing(app) ||
         eightvo_reader_view_focus_control_is(
           app, ReaderViewSemanticControl_Progress);
}

FUNCTION void
eightvo_reader_view_open_find_from_shortcut(EightvoApp *app)
{
  if (!app) return;
  app->reader_view_state.left_panel = ReaderViewLeftPanel_Find;
  app->reader_view_state.most_recent_panel = ReaderViewPanel_Left;
  app->reader_view_state.pending_left_panel_focus = ReaderViewLeftPanel_Find;
  app->host_focus_control = EightvoHostControl_None;
  app->host_focus_visible = 0;
}

FUNCTION ReaderViewInput
eightvo_reader_view_input(EightvoApp *app)
{
  ReaderViewInput result = {0};
  /* Escape is a keyboard transition. The frozen filter popup returns to a
     visibly focused toolbar trigger even when pointer activation opened it. */
  if (app->input.escape_pressed &&
      app->reader_view_state.popup == ReaderViewPopup_RightFilter)
    app->reader_view_state.focus_visible = 1;
  result.ui = ui0_input_pointer_wheel(app->input.pointer_x,
                                      app->input.pointer_y,
                                      app->input.pointer_down,
                                      app->input.pointer_pressed,
                                      app->input.pointer_released &&
                                        !app->input.pointer_selection_release,
                                      0,
                                      app->input.wheel_delta_y);
  if (app->input.activate_pressed) result.ui.flags |= UI0Input_ActivatePressed;
  if (app->input.focus_next_pressed) result.ui.flags |= UI0Input_FocusNextPressed;
  if (app->input.focus_prev_pressed) result.ui.flags |= UI0Input_FocusPrevPressed;
  result.escape_pressed = app->input.escape_pressed;
  B32 editing = eightvo_reader_view_text_editing(app);
  if (!editing)
  {
    result.move_horizontal_delta = app->input.move_delta;
    result.move_vertical_delta = app->input.move_vertical_delta;
  }
  result.range_move = app->input.range_move;

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

FUNCTION void
eightvo_reader_view_escape(EightvoApp *app)
{
  if (!app) return;
  if (app->reader_view_state.popup == ReaderViewPopup_SelectionTools)
  {
    epub_reader_clear_selection(&app->reader);
    app->selected_text[0] = 0;
    app->selection_anchor_rect = (UI0Rect){0};
  }
  app->input.escape_pressed = 1;
}

FUNCTION S32
eightvo_reader_view_measure_find_text(const char *text, U64 size)
{
  if (!text || size == 0) return 0;
  S32 advance = font_measure_text_width_s8(
    font_provider_system_ui(), str8((U8 *)text, size), 1);
  return MIN(MAX(advance, 0), 0x100000);
}

FUNCTION void
eightvo_reader_view_remember_find_scalar(EightvoApp *app,
                                           U32 scalar,
                                           const char *text,
                                           U32 size,
                                           U64 generation,
                                           U8 priority)
{
  if (!app || !text || size == 0 || scalar == 0 ||
      scalar > 0x10ffffu ||
      (scalar >= 0xd800u && scalar <= 0xdfffu))
  {
    return;
  }
  for (U32 index = 0; index < app->find_text_advance_count; index += 1)
  {
    if (app->find_text_advances[index].codepoint == scalar)
    {
      if (app->find_text_advance_priority[index] !=
          EightvoReaderViewFindPriority_Pinned)
      {
        if (app->find_text_advance_last_seen[index] != generation)
        {
          /* Priority belongs to the scalar's source in this build. */
          app->find_text_advance_priority[index] = priority;
        }
        app->find_text_advance_last_seen[index] = generation;
        if (app->find_text_advance_priority[index] < priority)
          app->find_text_advance_priority[index] = priority;
      }
      return;
    }
  }

  U32 entry_index = app->find_text_advance_count;
  if (app->find_text_advance_count >=
      READER_VIEW_FIND_CODEPOINT_ADVANCE_CAP)
  {
    U32 candidate = UINT32_MAX;
    U64 oldest_generation = UINT64_MAX;
    for (U32 index = 0;
         index < app->find_text_advance_count;
         index += 1)
    {
      U8 candidate_priority = app->find_text_advance_priority[index];
      U64 last_seen = app->find_text_advance_last_seen[index];
      if (candidate_priority == EightvoReaderViewFindPriority_Pinned)
        continue;
      if (last_seen != generation &&
          (candidate == UINT32_MAX || last_seen < oldest_generation))
      {
        candidate = index;
        oldest_generation = last_seen;
      }
    }
    if (candidate == UINT32_MAX)
    {
      U8 lowest_priority = UINT8_MAX;
      for (U32 index = 0;
           index < app->find_text_advance_count;
           index += 1)
      {
        U8 candidate_priority = app->find_text_advance_priority[index];
        if (candidate_priority != EightvoReaderViewFindPriority_Pinned &&
            candidate_priority < priority &&
            candidate_priority < lowest_priority)
        {
          candidate = index;
          lowest_priority = candidate_priority;
        }
      }
    }
    if (candidate == UINT32_MAX) return;
    entry_index = candidate;
  }

  app->find_text_advances[entry_index] = (ReaderViewCodepointAdvance){
    .codepoint = scalar,
    .advance = eightvo_reader_view_measure_find_text(text, size),
  };
  app->find_text_advance_last_seen[entry_index] = generation;
  app->find_text_advance_priority[entry_index] = priority;
  if (entry_index == app->find_text_advance_count)
    app->find_text_advance_count += 1;
}

FUNCTION void
eightvo_reader_view_remember_find_text(EightvoApp *app,
                                         const char *text,
                                         U64 size,
                                         U64 generation,
                                         U8 priority)
{
  if (!app || !text || size == 0) return;
  String8 value = str8((U8 *)text, size);
  for (U64 at = 0; at < value.size; )
  {
    BaseUnicodeDecode decode = base_unicode_utf8_decode(value, at);
    U32 advance = decode.advance > 0 ? decode.advance : 1;
    if (decode.valid && decode.scalar != (U32)'\r' &&
        decode.scalar != (U32)'\n')
    {
      eightvo_reader_view_remember_find_scalar(
        app, decode.scalar, text + at, advance, generation, priority);
    }
    at += advance;
  }
}

FUNCTION void
eightvo_reader_view_initialize_find_text_metrics(EightvoApp *app)
{
  if (!app || app->find_text_metrics_initialized) return;
  const FontProvider *provider = font_provider_system_ui();
  char fallback[2] = {'?', 0};
  if (provider && provider->fallback_codepoint != 0)
    fallback[0] = (char)provider->fallback_codepoint;
  app->find_text_fallback_advance =
    eightvo_reader_view_measure_find_text(fallback, 1);
  if (app->find_text_fallback_advance <= 0)
  {
    FontTextMetrics metrics = font_metrics_for_size(provider, 1);
    app->find_text_fallback_advance = MAX(metrics.glyph_advance_px, 1);
  }
  for (U32 scalar = 0x20u; scalar <= 0x7eu; scalar += 1)
  {
    char byte = (char)scalar;
    eightvo_reader_view_remember_find_scalar(
      app, scalar, &byte, 1, 0, EightvoReaderViewFindPriority_Pinned);
  }
  app->find_text_metrics_initialized = 1;
}

FUNCTION ReaderViewFindTextMetrics
eightvo_reader_view_find_text_metrics(EightvoApp *app,
                                        const ReaderViewInput *input)
{
  ReaderViewFindTextMetrics result = {0};
  if (!app) return result;
  eightvo_reader_view_initialize_find_text_metrics(app);
  app->find_text_metrics_generation += 1;
  if (app->find_text_metrics_generation == 0)
  {
    app->find_text_metrics_generation = 1;
    for (U32 index = 0; index < app->find_text_advance_count; index += 1)
    {
      if (app->find_text_advance_priority[index] !=
          EightvoReaderViewFindPriority_Pinned)
        app->find_text_advance_last_seen[index] = 0;
    }
  }
  U64 generation = app->find_text_metrics_generation;
  ReaderViewState *state = &app->reader_view_state;

  if (state->find_query_length > 0 &&
      state->find_query_length < READER_VIEW_FIND_QUERY_CAP)
  {
    eightvo_reader_view_remember_find_text(
      app, state->find_query, (U64)state->find_query_length,
      generation, EightvoReaderViewFindPriority_Current);
  }
  if (app->reader_view_projection.labels.find_placeholder.size > 0)
  {
    ReaderViewText placeholder =
      app->reader_view_projection.labels.find_placeholder;
    eightvo_reader_view_remember_find_text(
      app, placeholder.data, (U64)placeholder.size,
      generation, EightvoReaderViewFindPriority_Placeholder);
  }
  if (input)
  {
    if (input->find_text.text && input->find_text.text_len > 0)
    {
      eightvo_reader_view_remember_find_text(
        app, input->find_text.text, (U64)input->find_text.text_len,
        generation, EightvoReaderViewFindPriority_Current);
    }
    const UI0TextInputTransferBuffer *buffers[2] = {
      input->find_text.transfer_buffer,
      input->find_text.commit_buffer,
    };
    for (U32 index = 0; index < ARRAY_COUNT(buffers); index += 1)
    {
      const UI0TextInputTransferBuffer *buffer = buffers[index];
      if (buffer && buffer->data && buffer->length &&
          *buffer->length > 0 && *buffer->length < buffer->cap)
      {
        eightvo_reader_view_remember_find_text(
          app, buffer->data, (U64)*buffer->length,
          generation, EightvoReaderViewFindPriority_Current);
      }
    }
  }
  if (app->reader_view_projection.find.committed_query.size > 0)
  {
    ReaderViewText committed =
      app->reader_view_projection.find.committed_query;
    eightvo_reader_view_remember_find_text(
      app, committed.data, (U64)committed.size,
      generation, EightvoReaderViewFindPriority_Committed);
  }
  if (state->find_history.text_size > 0 &&
      state->find_history.text_size <= ARRAY_COUNT(state->find_history_text))
  {
    eightvo_reader_view_remember_find_text(
      app, state->find_history_text, state->find_history.text_size,
      generation, EightvoReaderViewFindPriority_History);
  }
  if (state->find_history.scratch_size > 0 &&
      state->find_history.scratch_size <=
        ARRAY_COUNT(state->find_history_scratch))
  {
    eightvo_reader_view_remember_find_text(
      app, state->find_history_scratch, state->find_history.scratch_size,
      generation, EightvoReaderViewFindPriority_History);
  }

  result.advances = app->find_text_advances;
  result.advance_count = (UI0S32)app->find_text_advance_count;
  result.fallback_advance = app->find_text_fallback_advance;
  return result;
}

FUNCTION S32
eightvo_reader_view_measure_note_text(const char *text, U64 size)
{
  if (!text || size == 0) return 0;
  S32 advance = font_measure_text_width_s8(
    font_provider_system_ui(), str8((U8 *)text, size),
    EightvoReaderViewNotePixelHeight);
  return MIN(MAX(advance, 0), 0x100000);
}

FUNCTION void
eightvo_reader_view_remember_note_scalar(EightvoApp *app,
                                           U32 scalar,
                                           const char *text,
                                           U32 size)
{
  if (!app || !text || size == 0 || scalar == 0 ||
      scalar > 0x10ffffu ||
      (scalar >= 0xd800u && scalar <= 0xdfffu))
  {
    return;
  }
  for (U32 index = 0; index < app->note_text_advance_count; index += 1)
    if (app->note_text_advances[index].codepoint == scalar) return;
  if (app->note_text_advance_count >=
      READER_VIEW_NOTE_CODEPOINT_ADVANCE_CAP)
  {
    return;
  }
  app->note_text_advances[app->note_text_advance_count++] =
    (ReaderViewCodepointAdvance){
      .codepoint = scalar,
      .advance = eightvo_reader_view_measure_note_text(text, size),
    };
}

FUNCTION void
eightvo_reader_view_remember_note_text(EightvoApp *app,
                                         ReaderViewText text)
{
  if (!app || !text.data || text.size <= 0) return;
  String8 value = str8((U8 *)text.data, (U64)text.size);
  for (U64 at = 0; at < value.size; )
  {
    BaseUnicodeDecode decode = base_unicode_utf8_decode(value, at);
    U32 advance = decode.advance > 0 ? decode.advance : 1;
    if (decode.valid && decode.scalar != (U32)'\r' &&
        decode.scalar != (U32)'\n')
    {
      eightvo_reader_view_remember_note_scalar(
        app, decode.scalar, text.data + at, advance);
    }
    at += advance;
  }
}

FUNCTION ReaderViewNoteTextMetrics
eightvo_reader_view_note_text_metrics(EightvoApp *app,
                                        const ReaderViewInput *input)
{
  ReaderViewNoteTextMetrics result = {0};
  if (!app || app->reader_view_state.popup != ReaderViewPopup_NoteEditor)
    return result;

  const FontProvider *provider = font_provider_system_ui();
  FontTextMetrics font_metrics = font_metrics_for_size(
    provider, EightvoReaderViewNotePixelHeight);
  S32 fallback_advance = eightvo_reader_view_measure_note_text("?", 1);
  result.advances = app->note_text_advances;
  result.fallback_advance = fallback_advance > 0 ?
    fallback_advance : EightvoReaderViewNoteAdvanceFallback;
  result.pixel_height = EightvoReaderViewNotePixelHeight;
  result.line_height = font_metrics.line_advance_px > 0 ?
    MAX(font_metrics.line_advance_px, result.pixel_height) :
    EightvoReaderViewNoteLineHeightFallback;

  MemoryZeroArray(app->note_text_advances);
  app->note_text_advance_count = 0;
  if (provider && fallback_advance > 0)
  {
    /* Current state first, then text arriving in this build, then transfer
       text, and finally the empty-editor placeholder. Missing values use the
       explicit fallback when the bounded table is full. */
    eightvo_reader_view_remember_note_text(
      app, reader_view_note_draft(&app->reader_view_state));
    if (input)
    {
      if (input->note_text.text && input->note_text.text_len > 0)
      {
        eightvo_reader_view_remember_note_text(
          app, (ReaderViewText){input->note_text.text,
                                input->note_text.text_len});
      }
      const UI0TextInputTransferBuffer *transfer =
        input->note_text.transfer_buffer;
      if (transfer && transfer->data && transfer->length &&
          *transfer->length > 0 && *transfer->length < transfer->cap)
      {
        eightvo_reader_view_remember_note_text(
          app, (ReaderViewText){transfer->data, *transfer->length});
      }
    }
    eightvo_reader_view_remember_note_text(
      app, app->reader_view_projection.labels.note_placeholder);
  }
  result.advance_count = (UI0S32)app->note_text_advance_count;
  return result;
}

FUNCTION B32
eightvo_build_reader_view(EightvoApp *app)
{
  if (!app) { return 0; }
  eightvo_prepare_reader_view_projection(app);
  ReaderViewLayoutInput layout_input = {
    .bounds = ui0_rect(0, 0, app->width, app->height),
    .features = app->reader_view_projection.features,
    .document_flags = app->reader_view_projection.document_flags,
    .host_toolbar_trailing_width = EightvoHostToolbarTrailingWidth,
  };
  ReaderViewLayout resolved_layout = {0};
  if (!reader_view_resolve_layout(&app->reader_view_state,
                                  &layout_input,
                                  &resolved_layout))
  {
    app->reader_view_ready = 0;
    return 0;
  }
  ReaderViewContentGeometry resolved_geometry = {
    .viewport_rect = resolved_layout.viewport_rect,
    .page_surface_rect = resolved_layout.page_surface_rect,
    .content_rect = resolved_layout.content_rect,
  };
  B32 viewport_changed = epub_reader_is_open(&app->reader) &&
    (app->pagination_viewport_width != resolved_geometry.content_rect.w ||
     app->pagination_viewport_height != resolved_geometry.content_rect.h);
  if (viewport_changed && app->page_action_waiting_for_present)
  {
    app->page_action_reflow_deferred = 1;
  }
  else
  {
    app->reader_view_layout = resolved_layout;
    app->reader_content_geometry = resolved_geometry;
    if (viewport_changed && !eightvo_repaginate(app))
    {
      app->reader_view_ready = 0;
      return 0;
    }
    if (viewport_changed) eightvo_prepare_reader_view_projection(app);
  }
  ReaderViewInput input = eightvo_reader_view_input(app);
  ReaderViewBuildInput build = {
    .frame_index = ++app->reader_view_frame_index,
    .state = &app->reader_view_state,
    .layout = &app->reader_view_layout,
    .projection = &app->reader_view_projection,
    .input = &input,
    .theme = &app->reader_view_theme,
    .find_text_metrics =
      eightvo_reader_view_find_text_metrics(app, &input),
    .note_text_metrics =
      eightvo_reader_view_note_text_metrics(app, &input),
  };
  app->reader_view_ready = reader_view_build(&build,
                                             &app->reader_view_storage,
                                             &app->reader_view_frame);
  return app->reader_view_ready;
}

FUNCTION void
eightvo_prewarm_reader_text_pipeline(EightvoApp *app)
{
  if (!app || !app->render_ready) return;
  enum { PrewarmW = 960, PrewarmH = 256 };
  U32 *pixels = (U32 *)malloc(
    (size_t)PrewarmW * (size_t)PrewarmH * sizeof(*pixels));
  if (!pixels) return;

  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, PrewarmW, PrewarmH, PrewarmW);
  render_buffer_clear(&buffer, 0x00000000U);
  (void)epub_reader_typography_set_view(&app->reader.typography,
                                        22,
                                        app->font_family,
                                        0);
  epub_reader_typography_set_text_mode(&app->reader.typography,
                                       EpubReaderTextMode_ShapedV1);
  draw_command_buffer_begin(&app->draw_commands);

  struct EightvoReaderTextPrewarmLine
  {
    const char *text;
    S32 scale;
    DocTextStyleFlags flags;
  };
  struct EightvoReaderTextPrewarmLine lines[] =
  {
    {"Reader warmup heading", 24, DocTextStyleFlag_Bold},
    {"The quick reader panel should remain responsive while search results are visible.", 22, 0},
    {"8vo reader paragraph repeats reader words for first-open frame coverage.", 22, 0},
    {"Alpha Beta Gamma Delta Epsilon Zeta Eta Theta Iota Kappa Lambda.", 22, 0},
    {"Italic prose and bold prose warm the same DirectWrite layout path.", 22, DocTextStyleFlag_Italic},
    {"Bold reader text warms the title and section chrome path.", 22, DocTextStyleFlag_Bold},
    {"Small punctuation, numerals 0123456789, and wraps stay inside the native renderer.", 22, 0},
    {"Final reader warmup line keeps first real EPUB paint out of cold-start work.", 22, 0},
  };

  S32 y = 10;
  for (U32 index = 0; index < ARRAY_COUNT(lines); index += 1)
  {
    struct EightvoReaderTextPrewarmLine line = lines[index];
    TextEngineResolvedStyle style =
      epub_reader_typography_style_for_doc_style(
        &app->reader.typography,
        line.scale,
        0xff1f2937U,
        0,
        DOC_EMBEDDED_FONT_FACE_INDEX_NONE,
        line.flags,
        FontRasterFlag_Smooth | FontRasterFlag_Hinted);
    FontTag render_tag =
      font_cache_tag_from_provider(&app->render_state.text_cache,
                                   style.provider);
    if (draw_push_text_ex_s8(&app->draw_commands,
                             DrawLayer_UI,
                             str8_from_cstr(line.text),
                             12,
                             y,
                             line.scale,
                             0xff1f2937U,
                             DrawTextOrigin_TopLeft,
                             0,
                             0,
                             0,
                             0,
                             0,
                             render_tag,
                             style.raster_flags) &&
        app->draw_commands.command_count[DrawLayer_UI] > 0)
    {
      DrawCommand *command =
        &app->draw_commands.commands[DrawLayer_UI]
          [app->draw_commands.command_count[DrawLayer_UI] - 1];
      if (command->type == DrawCommandType_Text)
        command->v.text.flags |= DrawTextFlag_Shaped;
    }
    y += 30;
  }
  render_execute_draw_commands(&app->render_state,
                               &buffer,
                               &app->draw_commands);
  draw_command_buffer_begin(&app->draw_commands);
  free(pixels);
}




FUNCTION B32
eightvo_app_init(EightvoApp *app,
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
  app->text_size_index = 3;
  app->line_spacing_index = 0;
  app->theme = EightvoTheme_Light;
  app->document_state = ReaderViewLoad_Empty;
  app->next_record_id = 1;
  app->clipboard_transfer.data = app->clipboard_text;
  app->clipboard_transfer.length = &app->clipboard_length;
  app->clipboard_transfer.cap = EightvoClipboardCap;
  reader_view_state_init(&app->reader_view_state);
  app->arena = arena_alloc(0);
  if (!app->arena) { return 0; }
  app->adjacent_frame_storage =
    PUSH_ARRAY_ZERO(app->arena, EpubReaderFrameStorage, 1);
  if (!app->adjacent_frame_storage)
  {
    arena_release(app->arena);
    app->arena = 0;
    return 0;
  }

  if (persistence_enabled && eightvo_state_paths(app))
  {
    eightvo_load_state(app);
    eightvo_load_settings(app);
    (void)eightvo_library_catalog_load(&app->library, app->catalog_path);
    eightvo_migrate_saved_state_to_library(app);
  }
  if (app->library.entry_count > 0)
    app->library_selected_entry_id = app->library.entries[0].entry_id;
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
  if (!eightvo_image_cache_init(&app->image_cache))
  {
    epub_reader_release(&app->reader);
    arena_release(app->arena);
    app->arena = 0;
    return 0;
  }
  if (!eightvo_library_thumbnail_cache_init(&app->library_thumbnail_cache))
  {
    eightvo_image_cache_release(&app->image_cache);
    epub_reader_release(&app->reader);
    arena_release(app->arena);
    app->arena = 0;
    return 0;
  }
  eightvo_library_hydrate_startup_entry(app);

  if (graphical)
  {
    render_state_init(&app->render_state, 0);
    app->render_ready = 1;
    /*
    Re10 performs the same hidden startup warmup before its window loop. Keep
    DirectWrite shaping and the common book glyph path out of 8vo's first
    visible EPUB frame as well.
    */
    eightvo_prewarm_reader_text_pipeline(app);
  }
  eightvo_library_set_summary_status(app);
  return 1;
}

FUNCTION void
eightvo_app_release(EightvoApp *app)
{
  if (!app) { return; }
  eightvo_cancel_location_warm(app);
  eightvo_cancel_adjacent_warm(app);
  if (app->accessibility) eightvo_accessibility_destroy(app->accessibility);
  (void)eightvo_save_state(app);
  (void)eightvo_save_library(app);
  (void)eightvo_save_settings(app);
  (void)eightvo_save_annotations(app);
  if (app->gfx_ready) { os_gfx_release(&app->gfx); }
  if (app->render_ready) { render_state_release(&app->render_state); }
  free(app->adjacent_page_pixels);
  app->adjacent_page_pixels = 0;
  eightvo_library_thumbnail_cache_release(&app->library_thumbnail_cache);
  eightvo_image_cache_release(&app->image_cache);
  epub_reader_release(&app->reader);
  if (app->arena) { arena_release(app->arena); }
  MemoryZeroStruct(app);
}

FUNCTION int
eightvo_run_data_migration_smoke(void)
{
  EightvoApp app = {0};
  if (!eightvo_app_init(&app, 1000, 720, 0, 1) ||
      !app.app_directory[0])
  {
    fprintf(stderr,
            "eightvo_data_migration_smoke result=fail reason=init\n");
    app.persistence_enabled = 0;
    eightvo_app_release(&app);
    return 1;
  }
  fprintf(stdout,
          "eightvo_data_migration_smoke result=pass directory=%s\n",
          app.app_directory);
  app.persistence_enabled = 0;
  eightvo_app_release(&app);
  return 0;
}

FUNCTION const ReaderViewTextBinding *
eightvo_reader_view_binding(const EightvoApp *app, UI0ID id)
{
  if (!app || !app->reader_view_frame.text_bindings) return 0;
  for (UI0S32 index = 0;
       index < app->reader_view_frame.text_binding_count;
       index += 1)
  {
    const ReaderViewTextBinding *binding =
      app->reader_view_frame.text_bindings + index;
    if (binding->source_id == id) return binding;
  }
  return 0;
}

FUNCTION B32
eightvo_reader_view_text_is(ReaderViewText text, const char *expected)
{
  if (!expected || text.size < 0) return 0;
  U64 size = strlen(expected);
  return size == (U64)text.size &&
         (size == 0 || (text.data && memcmp(text.data, expected, size) == 0));
}

FUNCTION U32
eightvo_draw_color(UI0Color color)
{
  return color & 0x00FFFFFFU;
}

FUNCTION B32
eightvo_ui0_rect_visible(UI0Rect rect, UI0Rect clip)
{
  return rect.w > 0 && rect.h > 0 && clip.w > 0 && clip.h > 0 &&
         MAX(rect.x, clip.x) < MIN(rect.x + rect.w, clip.x + clip.w) &&
         MAX(rect.y, clip.y) < MIN(rect.y + rect.h, clip.y + clip.h);
}

FUNCTION UI0Rect
eightvo_ui0_rect_intersect(UI0Rect a, UI0Rect b)
{
  UI0Rect result = {0};
  S32 x0 = MAX(a.x, b.x);
  S32 y0 = MAX(a.y, b.y);
  S32 x1 = MIN(a.x + a.w, b.x + b.w);
  S32 y1 = MIN(a.y + a.h, b.y + b.h);
  if (a.w > 0 && a.h > 0 && b.w > 0 && b.h > 0 && x0 < x1 && y0 < y1)
    result = ui0_rect(x0, y0, x1 - x0, y1 - y0);
  return result;
}

FUNCTION void
eightvo_draw_ui0_rect_border_clipped(EightvoApp *app,
                                      UI0Rect rect,
                                      UI0Rect clip,
                                      UI0Color color)
{
  if (!app || rect.w <= 1 || rect.h <= 1 ||
      !eightvo_ui0_rect_visible(rect, clip))
    return;
  U32 resolved = eightvo_draw_color(color);
  (void)draw_push_line_clipped(&app->draw_commands, DrawLayer_UI,
                               rect.x, rect.y,
                               rect.x + rect.w - 1, rect.y,
                               resolved,
                               clip.x, clip.y, clip.w, clip.h);
  (void)draw_push_line_clipped(&app->draw_commands, DrawLayer_UI,
                               rect.x, rect.y + rect.h - 1,
                               rect.x + rect.w - 1, rect.y + rect.h - 1,
                               resolved,
                               clip.x, clip.y, clip.w, clip.h);
  (void)draw_push_line_clipped(&app->draw_commands, DrawLayer_UI,
                               rect.x, rect.y,
                               rect.x, rect.y + rect.h - 1,
                               resolved,
                               clip.x, clip.y, clip.w, clip.h);
  (void)draw_push_line_clipped(&app->draw_commands, DrawLayer_UI,
                               rect.x + rect.w - 1, rect.y,
                               rect.x + rect.w - 1, rect.y + rect.h - 1,
                               resolved,
                               clip.x, clip.y, clip.w, clip.h);
}

FUNCTION B32
eightvo_ui0_border_matches_fill(const UI0DrawCommand *border,
                                  const UI0DrawCommand *fill)
{
  return border && fill && border->op == UI0DrawOp_ControlBorder &&
         border->source_id == fill->source_id &&
         border->source_kind == fill->source_kind &&
         border->source_index == fill->source_index &&
         border->rect.x == fill->rect.x && border->rect.y == fill->rect.y &&
         border->rect.w == fill->rect.w && border->rect.h == fill->rect.h;
}

FUNCTION U32
eightvo_ui0_border_color_for_fill(const ReaderViewFrame *frame,
                                   const UI0DrawCommand *fill)
{
  if (!frame || !frame->draw_commands || !fill) return 0;
  if (fill->stroke_color) return eightvo_draw_color(fill->stroke_color);
  for (UI0S32 index = 0; index < frame->draw_command_count; index += 1)
  {
    const UI0DrawCommand *candidate = frame->draw_commands + index;
    if (eightvo_ui0_border_matches_fill(candidate, fill))
      return eightvo_draw_color(candidate->color);
  }
  return eightvo_draw_color(fill->color);
}

FUNCTION void
eightvo_draw_ui0_control_fill(EightvoApp *app,
                               const UI0DrawCommand *command)
{
  if (!app || !command ||
      !eightvo_ui0_rect_visible(command->rect, command->clip_rect)) return;
  UI0Rect rect = command->rect;
  UI0Rect clip = command->clip_rect;
  S32 radius = MAX(command->corner_radius, 0);
  if (command->flags & UI0DrawFlag_CornerMask)
  {
    clip = eightvo_ui0_rect_intersect(command->rect, command->clip_rect);
    B32 round_top = (command->flags & UI0DrawFlag_RoundTop) != 0;
    B32 round_bottom = (command->flags & UI0DrawFlag_RoundBottom) != 0;
    if (!round_top && !round_bottom)
    {
      (void)draw_push_rect_clipped(&app->draw_commands, DrawLayer_UI,
                                   rect.x, rect.y, rect.w, rect.h,
                                   eightvo_draw_color(command->color),
                                   clip.x, clip.y, clip.w, clip.h);
      U32 border = eightvo_ui0_border_color_for_fill(&app->reader_view_frame,
                                                       command);
      if (border && border != eightvo_draw_color(command->color))
        eightvo_draw_ui0_rect_border_clipped(
          app, rect, clip, (UI0Color)border);
      return;
    }
    if (round_top && !round_bottom) rect.h += radius;
    else if (round_bottom && !round_top)
    {
      rect.y -= radius;
      rect.h += radius;
    }
  }
  U32 fill = eightvo_draw_color(command->color);
  U32 border = eightvo_ui0_border_color_for_fill(&app->reader_view_frame,
                                                   command);
  (void)draw_push_rounded_rect_clipped(&app->draw_commands, DrawLayer_UI,
                                       rect.x, rect.y, rect.w, rect.h, radius,
                                       fill, border,
                                       clip.x, clip.y, clip.w, clip.h);
}

FUNCTION void
eightvo_set_last_text_style(DrawCommandBuffer *buffer,
                             FontFaceStyleFlags style_flags)
{
  if (!buffer || buffer->command_count[DrawLayer_UI] == 0) return;
  U16 index = (U16)(buffer->command_count[DrawLayer_UI] - 1);
  DrawCommand *command = buffer->commands[DrawLayer_UI] + index;
  if (command->type == DrawCommandType_Text)
    command->v.text.font_style_flags = style_flags;
}

FUNCTION U32
eightvo_reader_view_utf8_next_byte(const char *text,
                                    U32 text_length,
                                    U32 at)
{
  if (!text || at >= text_length) return text_length;
  U8 lead = (U8)text[at];
  U32 size = 1;
  if ((lead & 0x80) == 0) size = 1;
  else if ((lead & 0xe0) == 0xc0) size = 2;
  else if ((lead & 0xf0) == 0xe0) size = 3;
  else if ((lead & 0xf8) == 0xf0) size = 4;
  if (at + size > text_length) size = 1;
  for (U32 index = 1; index < size; index += 1)
  {
    if (((U8)text[at + index] & 0xc0) != 0x80)
    {
      size = 1;
      break;
    }
  }
  return at + size;
}

FUNCTION U32
eightvo_reader_view_find_line_end(const char *text,
                                   U32 text_length,
                                   U32 start,
                                   S32 max_width)
{
  if (!text || start >= text_length || max_width <= 0) return start;
  U32 boundaries[EPUB_READER_FRAME_NAV_DETAIL_CAP + 1] = {0};
  U32 boundary_count = 0;
  boundaries[boundary_count++] = start;
  for (U32 at = start;
       at < text_length && boundary_count < ARRAY_COUNT(boundaries); )
  {
    U32 next = eightvo_reader_view_utf8_next_byte(text, text_length, at);
    if (next <= at || next > text_length) next = at + 1;
    boundaries[boundary_count++] = next;
    at = next;
  }
  U32 best = boundaries[boundary_count > 1 ? 1 : 0];
  U32 low = 1;
  U32 high = boundary_count > 0 ? boundary_count - 1 : 0;
  while (low <= high)
  {
    U32 middle = low + (high - low) / 2;
    U32 end = boundaries[middle];
    S32 width = font_measure_text_width_s8(
      font_provider_system_ui(), str8((U8 *)text + start, end - start), 1);
    if (width > max_width && end > start)
    {
      if (middle == 0) break;
      high = middle - 1;
    }
    else
    {
      best = end;
      low = middle + 1;
    }
  }
  if (best < text_length)
  {
    U32 last_space = start;
    for (U32 at = start; at < best; at += 1)
      if (text[at] == ' ') last_space = at;
    if (last_space > start) best = last_space;
  }
  return best > start ? best : MIN(start + 1, text_length);
}

FUNCTION B32
eightvo_draw_reader_view_find_excerpt(
  EightvoApp *app,
  const UI0DrawCommand *command,
  const ReaderViewTextBinding *binding)
{
  if (!app || !command || !binding || command->op != UI0DrawOp_Text ||
      !binding->text.data || binding->text.size <= 0 ||
      binding->match_size == 0 ||
      binding->match_start > (UI0U32)binding->text.size ||
      binding->match_size >
        (UI0U32)binding->text.size - binding->match_start)
    return 0;
  UI0Rect highlight_clip = command->clip_rect;
  if (highlight_clip.y > 0)
  {
    highlight_clip.y -= 1;
    highlight_clip.h += 1;
  }
  UI0Rect text_rect =
    eightvo_ui0_rect_intersect(command->rect, highlight_clip);
  if (text_rect.w <= 0 || text_rect.h <= 0) return 1;

  const char *text = binding->text.data;
  U32 text_length = (U32)binding->text.size;
  U32 cursor = 0;
  while (cursor < text_length && text[cursor] == ' ') cursor += 1;
  U32 line_end = eightvo_reader_view_find_line_end(
    text, text_length, cursor, text_rect.w);
  U32 draw_end = line_end;
  while (draw_end > cursor && text[draw_end - 1] == ' ') draw_end -= 1;
  if (draw_end <= cursor) return 1;

  U32 match_start = MAX(cursor, binding->match_start);
  U32 match_end = MIN(draw_end,
                      binding->match_start + binding->match_size);
  FontTextMetrics metrics =
    font_metrics_for_size(font_provider_system_ui(), 1);
  S32 baseline = text_rect.y + metrics.ascent_px;
  if (match_end > match_start)
  {
    S32 pre_width = font_measure_text_width_s8(
      font_provider_system_ui(),
      str8((U8 *)text + cursor, match_start - cursor), 1);
    S32 match_width = font_measure_text_width_s8(
      font_provider_system_ui(),
      str8((U8 *)text + match_start, match_end - match_start), 1);
    UI0Rect highlight = eightvo_ui0_rect_intersect(
      ui0_rect(text_rect.x + pre_width,
               baseline - metrics.ascent_px - 1,
               MAX(match_width, 1),
               MAX(metrics.ascent_px + metrics.descent_px + 2, 1)),
      highlight_clip);
    if (highlight.w > 0 && highlight.h > 0)
    {
      EightvoReaderContentTheme theme =
        eightvo_reader_content_theme(app->theme);
      (void)draw_push_rect(&app->draw_commands, DrawLayer_UI,
                           highlight.x, highlight.y,
                           highlight.w, highlight.h,
                           theme.user_highlight);
    }
  }
  EightvoReaderContentTheme theme =
    eightvo_reader_content_theme(app->theme);
  (void)draw_push_text_clipped_baseline_s8(
    &app->draw_commands, DrawLayer_UI,
    str8((U8 *)text + cursor, draw_end - cursor),
    text_rect.x, baseline, 1, theme.ink,
    text_rect.x, text_rect.y, text_rect.w, text_rect.h);
  return 1;
}

FUNCTION B32
eightvo_reader_view_note_binding_source_range(
  const EightvoApp *app,
  const ReaderViewTextBinding *binding,
  U64 *out_start,
  U64 *out_end)
{
  if (out_start) *out_start = 0;
  if (out_end) *out_end = 0;
  if (!app || !binding || !binding->text.data || binding->text.size <= 0 ||
      !out_start || !out_end)
  {
    return 0;
  }
  const char *draft = app->reader_view_state.note_draft;
  U64 draft_size = (U64)MAX(app->reader_view_state.note_draft_length, 0);
  U64 draft_address = (U64)(uintptr_t)draft;
  U64 text_address = (U64)(uintptr_t)binding->text.data;
  if (text_address < draft_address || text_address > draft_address + draft_size)
    return 0;
  *out_start = text_address - draft_address;
  if ((U64)binding->text.size > draft_size - *out_start) return 0;
  *out_end = *out_start + (U64)binding->text.size;
  return 1;
}

FUNCTION B32
eightvo_draw_reader_view_note_editable_row(
  EightvoApp *app,
  const UI0DrawCommand *command,
  const UI0DrawCommand *caret_command,
  const ReaderViewTextBinding *binding,
  S32 *out_caret_x,
  B32 *out_caret_x_valid)
{
  U64 source_start = 0;
  U64 source_end = 0;
  if (!app || !command || !binding || command->op != UI0DrawOp_Text ||
      binding->style != ReaderViewTextStyle_NoteEditor ||
      command->rect.w <= 0 || command->rect.h <= 0 ||
      command->clip_rect.w <= 0 || command->clip_rect.h <= 0 ||
      !eightvo_reader_view_note_binding_source_range(
        app, binding, &source_start, &source_end))
  {
    return 0;
  }

  S32 pixel_height = command->has_typography_role &&
                     command->typography_line_height > 0 ?
    command->typography_line_height : EightvoReaderViewNotePixelHeight;
  Scratch scratch = scratch_begin(0, 0);
  TextEngineResolvedStyle style = text_engine_resolved_style_make(
    font_provider_system_ui(),
    (FontTag){0},
    pixel_height,
    eightvo_draw_color(command->color),
    0,
    FontRasterFlag_Smooth | FontRasterFlag_Hinted);
  TextEngineSourceRange source_range =
    text_engine_source_range_from_bytes(source_start, source_end);
  TextEngineEditableRow row = {0};
  B32 drew_text = text_engine_editable_row_make(
    &row,
    scratch.arena,
    str8((U8 *)binding->text.data, (U64)binding->text.size),
    &style,
    source_range,
    0,
    command->rect.x,
    command->rect.y,
    command->rect.h,
    EightvoReaderViewNoteTerminalCaretGap) &&
    text_engine_editable_row_push_text_span_s8(
      &app->draw_commands,
      DrawLayer_UI,
      &row,
      0,
      row.text.size,
      (DrawClipRect){command->clip_rect.x,
                     command->clip_rect.y,
                     command->clip_rect.w,
                     command->clip_rect.h});
  if (drew_text) app->draw_adapter_stats.note_editable_row_count += 1;
  if (drew_text && caret_command && out_caret_x && out_caret_x_valid)
  {
    U64 caret = (U64)MAX(app->reader_view_state.note_input.caret, 0);
    B32 caret_in_source = caret >= source_start && caret <= source_end;
    B32 caret_in_row = caret_command->rect.y >= command->rect.y &&
      caret_command->rect.y < command->rect.y + command->rect.h;
    if (caret_in_source && caret_in_row &&
        text_engine_editable_row_caret_x_for_source_byte(
          &row, caret, out_caret_x))
    {
      *out_caret_x_valid = 1;
    }
  }
  scratch_end(scratch);
  return drew_text;
}

FUNCTION const ReaderViewSemanticNode *
eightvo_reader_view_semantic_node(const EightvoApp *app, UI0ID id)
{
  if (!app || !id || !app->reader_view_frame.semantic_nodes) return 0;
  for (UI0S32 index = 0;
       index < app->reader_view_frame.semantic_node_count;
       index += 1)
  {
    const ReaderViewSemanticNode *node =
      app->reader_view_frame.semantic_nodes + index;
    if (node->id == id) return node;
  }
  return 0;
}

FUNCTION void
eightvo_draw_ui0_text(EightvoApp *app, const UI0DrawCommand *command)
{
  if (!app || !command || command->rect.w <= 0 || command->rect.h <= 0 ||
      command->clip_rect.w <= 0 || command->clip_rect.h <= 0) return;
  const ReaderViewTextBinding *binding =
    eightvo_reader_view_binding(app, command->source_id);
  if (!binding || !binding->text.data || binding->text.size <= 0) return;
  if (eightvo_draw_reader_view_find_excerpt(app, command, binding)) return;
  char label[READER_VIEW_NOTE_DRAFT_CAP] = {0};
  eightvo_copy_bytes(label, ARRAY_COUNT(label),
                      (const U8 *)binding->text.data, (U64)binding->text.size);
  String8 text = str8_from_cstr(label);
  const FontProvider *provider = font_provider_system_ui();
  S32 scale = binding->style == ReaderViewTextStyle_ChromeTitle ? 2 : 1;
  if (binding->style == ReaderViewTextStyle_NoteEditor)
  {
    scale = command->has_typography_role &&
            command->typography_role == UI0TypographyRole_Body &&
            command->typography_line_height > 0 ?
      command->typography_line_height : EightvoReaderViewNotePixelHeight;
  }
  FontTextMetrics metrics = font_metrics_for_size(provider, scale);
  const ReaderViewSemanticNode *node =
    eightvo_reader_view_semantic_node(app, command->source_id);
  const ReaderViewSemanticNode *parent = node ?
    eightvo_reader_view_semantic_node(app, node->parent_id) : 0;
  if (app->reader_view_state.left_panel == ReaderViewLeftPanel_Find &&
      parent && parent->control == ReaderViewSemanticControl_FindRow)
  {
    S32 text_width = font_measure_text_width_s8(provider, text, scale);
    S32 text_x = command->rect.x + MAX(command->rect.w - text_width, 0);
    (void)draw_push_text_clipped_baseline_s8(
      &app->draw_commands, DrawLayer_UI, text,
      text_x, command->rect.y + metrics.ascent_px,
      scale, eightvo_draw_color(command->color),
      command->clip_rect.x, command->clip_rect.y,
      command->clip_rect.w, command->clip_rect.h);
    return;
  }
  S32 text_h = MAX(metrics.ascent_px + metrics.descent_px,
                   MAX(metrics.glyph_height_px, 1));
  S32 line_h = MIN(text_h, MAX(command->rect.h, 1));
  S32 line_y = command->rect.y;
  UI0TextAlignY align_y = command->has_text_alignment ?
    command->text_align_y : UI0TextAlignY_Center;
  if (align_y == UI0TextAlignY_Center) line_h = command->rect.h;
  else if (align_y == UI0TextAlignY_Bottom)
    line_y += MAX(command->rect.h - line_h, 0);
  S32 baseline = line_y + MAX((line_h - text_h) / 2, 0) + metrics.ascent_px;
  if (line_h > 0 && metrics.descent_px > 0)
    baseline = MIN(baseline, line_y + line_h - metrics.descent_px);
  if (node && node->role == ReaderViewSemantic_Status &&
      app->reader_view_state.left_panel == ReaderViewLeftPanel_Find)
  {
    UI0Rect panel = app->reader_view_layout.left_panel_rect;
    B32 belongs_to_left_panel =
      command->rect.x >= panel.x && command->rect.y >= panel.y &&
      command->rect.x + command->rect.w <= panel.x + panel.w &&
      command->rect.y + command->rect.h <= panel.y + panel.h;
    if (belongs_to_left_panel)
    {
      S32 status_baseline = font_text_baseline_y_in_rect(
        provider, command->rect.y, command->rect.h, scale);
      for (U32 pass = 0; pass < 2; pass += 1)
      {
        (void)draw_push_text_clipped_baseline_s8(
          &app->draw_commands, DrawLayer_UI, text,
          command->rect.x, status_baseline,
          scale, eightvo_draw_color(command->color),
          command->clip_rect.x, command->clip_rect.y,
          command->clip_rect.w, command->clip_rect.h);
      }
      return;
    }
  }
  S32 x = command->rect.x;
  UI0TextAlignX align_x = command->has_text_alignment ?
    command->text_align_x : UI0TextAlignX_Start;
  if (align_x != UI0TextAlignX_Start)
  {
    S32 text_w = font_measure_text_width_s8(provider, text, scale);
    if (text_w > 0 && text_w < command->rect.w)
      x = align_x == UI0TextAlignX_End ?
        command->rect.x + command->rect.w - text_w :
        command->rect.x + (command->rect.w - text_w) / 2;
  }
  UI0Rect horizontal = ui0_rect(command->rect.x, command->clip_rect.y,
                                command->rect.w, command->clip_rect.h);
  UI0Rect clip = eightvo_ui0_rect_intersect(horizontal, command->clip_rect);
  if (clip.w <= 0 || clip.h <= 0) return;
  {
    DrawTextVAlign v_align = DrawTextVAlign_Center;
    if (align_y == UI0TextAlignY_Top) v_align = DrawTextVAlign_Top;
    else if (align_y == UI0TextAlignY_Bottom) v_align = DrawTextVAlign_Bottom;
    U16 first_command_index =
      app->draw_commands.command_count[DrawLayer_UI];
    (void)draw_push_text_box(&app->draw_commands,
                             DrawLayer_UI,
                             provider,
                             label,
                             command->rect.x,
                             command->rect.y,
                             command->rect.w,
                             command->rect.h,
                             0,
                             0,
                             scale,
                             1,
                             DrawTextHAlign_Left,
                             v_align,
                             DrawTextBoxOverflow_Truncate,
                             eightvo_draw_color(command->color),
                             0);
    for (U16 command_index = first_command_index;
         command_index < app->draw_commands.command_count[DrawLayer_UI];
         command_index += 1)
    {
      DrawCommand *text_command =
        app->draw_commands.commands[DrawLayer_UI] + command_index;
      if (text_command->type != DrawCommandType_Text ||
          !(text_command->v.text.flags & DrawTextFlag_Clip))
        continue;
      text_command->v.text.x = x;
      text_command->v.text.y = baseline;
      UI0Rect text_horizontal = ui0_rect(text_command->v.text.clip_x,
                                         command->clip_rect.y,
                                         text_command->v.text.clip_w,
                                         command->clip_rect.h);
      UI0Rect resolved_clip =
        eightvo_ui0_rect_intersect(text_horizontal, command->clip_rect);
      text_command->v.text.clip_x = resolved_clip.x;
      text_command->v.text.clip_y = resolved_clip.y;
      text_command->v.text.clip_w = resolved_clip.w;
      text_command->v.text.clip_h = resolved_clip.h;
    }
    return;
  }
}

FUNCTION void
eightvo_draw_ui0_line(EightvoApp *app, UI0Rect clip,
                       S32 x0, S32 y0, S32 x1, S32 y1,
                       S32 width, U32 color)
{
  (void)draw_push_line_width_clipped(&app->draw_commands, DrawLayer_UI,
                                     x0, y0, x1, y1, MAX(width, 1), color,
                                     clip.x, clip.y, clip.w, clip.h);
}

typedef struct EightvoReaderFilterSegment
{
  F32 x0;
  F32 y0;
  F32 x1;
  F32 y1;
} EightvoReaderFilterSegment;

FUNCTION F64
eightvo_reader_filter_distance_sq(F64 px, F64 py,
                                   const EightvoReaderFilterSegment *segment)
{
  F64 vx = (F64)segment->x1 - (F64)segment->x0;
  F64 vy = (F64)segment->y1 - (F64)segment->y0;
  F64 len_sq = vx * vx + vy * vy;
  F64 t = 0.0;
  if (len_sq > 0.000001)
  {
    t = ((px - (F64)segment->x0) * vx +
         (py - (F64)segment->y0) * vy) / len_sq;
    if (t < 0.0) t = 0.0;
    if (t > 1.0) t = 1.0;
  }
  F64 dx = px - ((F64)segment->x0 + t * vx);
  F64 dy = py - ((F64)segment->y0 + t * vy);
  return dx * dx + dy * dy;
}

FUNCTION B32
eightvo_reader_filter_sample_covered(F64 x, F64 y)
{
  static const EightvoReaderFilterSegment segments[] = {
    {4.0f, 21.0f, 4.0f, 14.0f},
    {4.0f, 10.0f, 4.0f, 3.0f},
    {12.0f, 21.0f, 12.0f, 12.0f},
    {12.0f, 8.0f, 12.0f, 3.0f},
    {20.0f, 21.0f, 20.0f, 16.0f},
    {20.0f, 12.0f, 20.0f, 3.0f},
    {2.0f, 14.0f, 6.0f, 14.0f},
    {10.0f, 8.0f, 14.0f, 8.0f},
    {18.0f, 16.0f, 22.0f, 16.0f},
  };
  for (U32 index = 0; index < ARRAY_COUNT(segments); index += 1)
    if (eightvo_reader_filter_distance_sq(x, y, segments + index) <= 1.0)
      return 1;
  return 0;
}

FUNCTION U32
eightvo_reader_filter_blend(U32 foreground, U32 background, U32 alpha)
{
  alpha = MIN(alpha, 255u);
  U32 inverse = 255u - alpha;
  U32 foreground_r = (foreground >> 16) & 0xffu;
  U32 foreground_g = (foreground >> 8) & 0xffu;
  U32 foreground_b = foreground & 0xffu;
  U32 background_r = (background >> 16) & 0xffu;
  U32 background_g = (background >> 8) & 0xffu;
  U32 background_b = background & 0xffu;
  U32 r = (foreground_r * alpha + background_r * inverse + 127u) / 255u;
  U32 g = (foreground_g * alpha + background_g * inverse + 127u) / 255u;
  U32 b = (foreground_b * alpha + background_b * inverse + 127u) / 255u;
  return (r << 16) | (g << 8) | b;
}

FUNCTION B32
eightvo_reader_filter_rasterize_rgb32(S32 width, S32 height,
                                       U32 foreground, U32 background,
                                       U32 *pixels, S32 stride_pixels)
{
  enum { supersample = 4 };
  if (!pixels || width <= 0 || height <= 0 || stride_pixels < width)
    return 0;
  F64 side = (F64)MIN(width, height);
  F64 x_pad = ((F64)width - side) * 0.5;
  F64 y_pad = ((F64)height - side) * 0.5;
  U32 sample_count = supersample * supersample;
  for (S32 y = 0; y < height; y += 1)
  {
    for (S32 x = 0; x < width; x += 1)
    {
      U32 covered = 0;
      for (S32 sample_y = 0; sample_y < supersample; sample_y += 1)
      {
        for (S32 sample_x = 0; sample_x < supersample; sample_x += 1)
        {
          F64 px = (F64)x + (((F64)sample_x + 0.5) / supersample);
          F64 py = (F64)y + (((F64)sample_y + 0.5) / supersample);
          if (px >= x_pad && py >= y_pad &&
              px < x_pad + side && py < y_pad + side)
          {
            F64 source_x = ((px - x_pad) / side) * 24.0;
            F64 source_y = ((py - y_pad) / side) * 24.0;
            covered += eightvo_reader_filter_sample_covered(source_x,
                                                              source_y) ? 1u : 0u;
          }
        }
      }
      U32 alpha = (covered * 255u + sample_count / 2u) / sample_count;
      pixels[(U64)y * (U64)stride_pixels + (U64)x] =
        eightvo_reader_filter_blend(foreground, background, alpha);
    }
  }
  return 1;
}

FUNCTION const U32 *
eightvo_ui0_icon_raster(EightvoApp *app,
                         const UI0DrawCommand *command)
{
  EightvoUI0IconRasterCacheEntry *entry = 0;
  if (!app || !command || command->op != UI0DrawOp_Icon ||
      command->rect.w <= 0 || command->rect.h <= 0 ||
      command->rect.w > EightvoUI0IconRasterMaxWidth ||
      command->rect.h > EightvoUI0IconRasterMaxHeight)
  {
    return 0;
  }
  for (U32 index = 0; index < app->ui0_icon_raster_count; index += 1)
  {
    EightvoUI0IconRasterCacheEntry *candidate =
      app->ui0_icon_rasters + index;
    if (candidate->icon_kind == command->icon_kind &&
        candidate->width == command->rect.w &&
        candidate->height == command->rect.h &&
        candidate->foreground == command->color &&
        candidate->background == command->stroke_color)
    {
      return candidate->pixels;
    }
  }
  if (app->ui0_icon_raster_count >= EightvoUI0IconRasterCacheCap) { return 0; }
  entry = app->ui0_icon_rasters + app->ui0_icon_raster_count;
  /* The frozen re10 benchmark renders this generic intent with its established
     24x24 SlidersVertical geometry. Keep that exact host-boundary raster while
     all other icons continue through UI0's portable rasterizer. */
  B32 rasterized = command->icon_kind == UI0IconKind_Filter ?
    eightvo_reader_filter_rasterize_rgb32(
      command->rect.w, command->rect.h,
      command->color, command->stroke_color,
      entry->pixels, EightvoUI0IconRasterMaxWidth) :
    ui0_icon_rasterize_rgb32(command->icon_kind,
                             command->rect.w,
                             command->rect.h,
                             command->color,
                             command->stroke_color,
                             entry->pixels,
                             EightvoUI0IconRasterMaxWidth);
  if (!rasterized)
  {
    return 0;
  }
  entry->icon_kind = command->icon_kind;
  entry->width = command->rect.w;
  entry->height = command->rect.h;
  entry->foreground = command->color;
  entry->background = command->stroke_color;
  app->ui0_icon_raster_count += 1;
  return entry->pixels;
}

FUNCTION void
eightvo_draw_ui0_icon(EightvoApp *app, const UI0DrawCommand *command)
{
  if (!app || !command ||
      !eightvo_ui0_rect_visible(command->rect, command->clip_rect)) return;
  const U32 *pixels = eightvo_ui0_icon_raster(app, command);
  if (!pixels)
  {
    app->draw_adapter_stats.unsupported_count += 1;
    return;
  }
  (void)draw_push_sprite_clipped(&app->draw_commands,
                                 DrawLayer_UI,
                                 pixels,
                                 command->rect.w,
                                 command->rect.h,
                                 EightvoUI0IconRasterMaxWidth,
                                 command->rect.x,
                                 command->rect.y,
                                 command->rect.w,
                                 command->rect.h,
                                 command->clip_rect.x,
                                 command->clip_rect.y,
                                 command->clip_rect.w,
                                 command->clip_rect.h);
}

FUNCTION void
eightvo_draw_ui0_check_mark(EightvoApp *app,
                             const UI0DrawCommand *command)
{
  if (!app || !command ||
      !eightvo_ui0_rect_visible(command->rect, command->clip_rect)) return;
  UI0Rect r = command->rect;
  S32 x0 = r.x + MAX(1, r.w / 10);
  S32 y0 = r.y + (r.h * 5) / 10;
  S32 x1 = r.x + (r.w * 4) / 10;
  S32 y1 = r.y + r.h - MAX(1, r.h / 5);
  S32 x2 = r.x + r.w - MAX(1, r.w / 10);
  S32 y2 = r.y + MAX(1, r.h / 5);
  S32 width = MAX(2, (r.h + 2) / 4);
  U32 color = eightvo_draw_color(command->color);
  eightvo_draw_ui0_line(app, command->clip_rect, x0, y0, x1, y1,
                         width, color);
  eightvo_draw_ui0_line(app, command->clip_rect, x1, y1, x2, y2,
                         width, color);
}

FUNCTION void
eightvo_draw_ui0_focus_ring(EightvoApp *app,
                             const UI0DrawCommand *command)
{
  if (!app || !command ||
      !eightvo_ui0_rect_visible(command->rect, command->clip_rect))
    return;
  UI0Rect rect = command->rect;
  UI0Rect clip = command->clip_rect;
  S32 radius = MAX(command->corner_radius, 0);
  U32 color = eightvo_draw_color(command->color);
  if ((command->flags & UI0DrawFlag_CornerMask) != 0)
  {
    clip = eightvo_ui0_rect_intersect(rect, clip);
    B32 round_top = (command->flags & UI0DrawFlag_RoundTop) != 0;
    B32 round_bottom = (command->flags & UI0DrawFlag_RoundBottom) != 0;
    if (!round_top && !round_bottom)
    {
      eightvo_draw_ui0_rect_border_clipped(app, rect, clip, command->color);
      return;
    }
    if (round_top && !round_bottom) rect.h += radius;
    else if (round_bottom && !round_top)
    {
      rect.y -= radius;
      rect.h += radius;
    }
  }
  (void)draw_push_rounded_rect_stroke_clipped(
    &app->draw_commands, DrawLayer_UI,
    rect.x, rect.y, rect.w, rect.h, radius, 1, color,
    clip.x, clip.y, clip.w, clip.h);
}

FUNCTION void
eightvo_draw_ui0_indicator_border(EightvoApp *app,
                                   const UI0DrawCommand *command)
{
  if (!app || !command ||
      !eightvo_ui0_rect_visible(command->rect, command->clip_rect))
    return;
  if (command->source_kind == UI0ControlKind_Checkbox)
  {
    (void)draw_push_rounded_rect_stroke_clipped(
      &app->draw_commands, DrawLayer_UI,
      command->rect.x, command->rect.y,
      command->rect.w, command->rect.h,
      MAX(command->corner_radius, 0), 1,
      eightvo_draw_color(command->color),
      command->clip_rect.x, command->clip_rect.y,
      command->clip_rect.w, command->clip_rect.h);
  }
  else
  {
    eightvo_draw_ui0_rect_border_clipped(
      app, command->rect, command->clip_rect, command->color);
  }
}

FUNCTION void
eightvo_adapt_ui0_draw(EightvoApp *app)
{
  if (!app) { return; }
  const UI0DrawCommand *note_caret_command = 0;
  S32 note_caret_x = 0;
  B32 note_caret_x_valid = 0;
  for (UI0S32 index = 0;
       index < app->reader_view_frame.draw_command_count;
       index += 1)
  {
    const UI0DrawCommand *candidate =
      app->reader_view_frame.draw_commands + index;
    if (candidate->op != UI0DrawOp_TextCaret) continue;
    const ReaderViewTextBinding *binding =
      eightvo_reader_view_binding(app, candidate->source_id);
    if (binding && binding->style == ReaderViewTextStyle_NoteEditor)
    {
      note_caret_command = candidate;
      break;
    }
  }
  MemoryZeroStruct(&app->draw_adapter_stats);
  app->ui0_icon_raster_count = 0;
  for (UI0S32 index = 0;
       index < app->reader_view_frame.draw_command_count;
       index += 1)
  {
    const UI0DrawCommand *source_command =
      app->reader_view_frame.draw_commands + index;
    UI0DrawCommand command = *source_command;
    U32 color = eightvo_draw_color(command.color);
    if (command.op >= 0 && command.op < UI0DrawOp_Count)
      app->draw_adapter_stats.op_count[command.op] += 1;
    switch (command.op)
    {
      case UI0DrawOp_ControlFill:
        eightvo_draw_ui0_control_fill(app, &command);
        break;

      case UI0DrawOp_ControlBorder:
        /* Its paired fill owns the rounded fill and border as one shell. */
        break;

      case UI0DrawOp_IndicatorFill:
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

      case UI0DrawOp_ToggleTrack:
      case UI0DrawOp_ToggleKnob:
      case UI0DrawOp_ScrollTrack:
      case UI0DrawOp_ScrollThumb:
      case UI0DrawOp_SliderTrack:
      case UI0DrawOp_SliderFill:
      case UI0DrawOp_SliderThumb:
      {
        (void)draw_push_rounded_rect_clipped(&app->draw_commands,
                                             DrawLayer_UI,
                                             command.rect.x,
                                             command.rect.y,
                                             command.rect.w,
                                             command.rect.h,
                                             MAX(command.corner_radius, 0),
                                             color,
                                             eightvo_draw_color(
                                               command.stroke_color ?
                                                 command.stroke_color :
                                                 command.color),
                                             command.clip_rect.x,
                                             command.clip_rect.y,
                                             command.clip_rect.w,
                                             command.clip_rect.h);
      } break;

      case UI0DrawOp_IndicatorBorder:
        eightvo_draw_ui0_indicator_border(app, &command);
        break;

      case UI0DrawOp_FocusRing:
        eightvo_draw_ui0_focus_ring(app, &command);
        break;

      case UI0DrawOp_Text:
      {
        const ReaderViewTextBinding *binding =
          eightvo_reader_view_binding(app, command.source_id);
        if (!eightvo_draw_reader_view_note_editable_row(
              app, &command, note_caret_command, binding,
              &note_caret_x, &note_caret_x_valid))
        {
          eightvo_draw_ui0_text(app, &command);
        }
      }
        break;

      case UI0DrawOp_Icon:
        eightvo_draw_ui0_icon(app, &command);
        break;

      case UI0DrawOp_CheckMark:
        eightvo_draw_ui0_check_mark(app, &command);
        break;

      case UI0DrawOp_SegmentJoin:
      case UI0DrawOp_TextSelection:
      case UI0DrawOp_TextCaret:
        if (source_command == note_caret_command && note_caret_x_valid)
        {
          command.rect.x = note_caret_x;
          app->draw_adapter_stats.note_caret_remap_count += 1;
        }
        if (eightvo_ui0_rect_visible(command.rect, command.clip_rect))
          (void)draw_push_rect_clipped(&app->draw_commands, DrawLayer_UI,
                                       command.rect.x, command.rect.y,
                                       command.rect.w, command.rect.h, color,
                                       command.clip_rect.x, command.clip_rect.y,
                                       command.clip_rect.w, command.clip_rect.h);
        break;

      case UI0DrawOp_Count:
      default:
        app->draw_adapter_stats.unsupported_count += 1;
        break;
    }
  }
}

FUNCTION B32
eightvo_library_active(const EightvoApp *app)
{
  return app && !epub_reader_is_open(&app->reader);
}

FUNCTION UI0Rect
eightvo_library_add_rect(const EightvoApp *app)
{
  if (!app || app->width <= 0) return (UI0Rect){0};
  S32 width = app->width < 520 ? 132 : 148;
  return ui0_rect(MAX(app->width - width - 32, 16), 22, width, 40);
}

FUNCTION UI0Rect
eightvo_library_empty_add_rect(const EightvoApp *app)
{
  if (!app) return (UI0Rect){0};
  S32 panel_width = MIN(MAX(app->width - 48, 260), 560);
  S32 panel_height = 260;
  S32 x = (app->width - panel_width) / 2;
  S32 y = MAX(112, (app->height - panel_height) / 2);
  return ui0_rect(x + (panel_width - 168) / 2, y + 184, 168, 42);
}

FUNCTION void
eightvo_library_resolve_layout(EightvoApp *app)
{
  if (!app) return;
  app->library_card_count = 0;
  S32 margin = app->width < 620 ? 20 : 32;
  S32 gap = app->width < 620 ? 16 : 24;
  S32 available = MAX(app->width - margin * 2, 120);
  S32 desired = app->width < 620 ? 148 : 180;
  S32 columns = MAX(1, (available + gap) / (desired + gap));
  columns = MIN(columns, 8);
  S32 card_width = MAX(120, (available - gap * (columns - 1)) / columns);
  card_width = MIN(card_width, 208);
  S32 grid_width = card_width * columns + gap * (columns - 1);
  S32 grid_x = margin + MAX((available - grid_width) / 2, 0);
  S32 cover_height = MIN((card_width * 3) / 2, 264);
  S32 card_height = cover_height + 112;
  S32 row_gap = 28;
  S32 row_count = app->library.entry_count == 0 ? 0 :
    (S32)((app->library.entry_count + (U32)columns - 1) / (U32)columns);
  S32 content_height = row_count > 0 ?
    row_count * card_height + (row_count - 1) * row_gap : 0;
  S32 viewport_top = 104;
  S32 viewport_height = MAX(app->height - viewport_top - 24, 0);
  app->library_scroll_max = MAX(content_height - viewport_height, 0);
  if (app->input.wheel_delta_y != 0)
    app->library_scroll_y -= app->input.wheel_delta_y * 56;
  app->library_scroll_y = MAX(0, MIN(app->library_scroll_y,
                                     app->library_scroll_max));
  app->library_column_count = columns;

  for (U32 index = 0; index < app->library.entry_count; index += 1)
  {
    S32 column = (S32)(index % (U32)columns);
    S32 row = (S32)(index / (U32)columns);
    S32 x = grid_x + column * (card_width + gap);
    S32 y = viewport_top + row * (card_height + row_gap) -
            app->library_scroll_y;
    if (y + card_height < viewport_top || y > app->height) continue;
    if (app->library_card_count >= ARRAY_COUNT(app->library_cards)) break;
    EightvoLibraryEntry *entry = app->library.entries + index;
    EightvoLibraryCardLayout *card =
      app->library_cards + app->library_card_count;
    *card = (EightvoLibraryCardLayout){
      .entry_id = entry->entry_id,
      .card_rect = ui0_rect(x, y, card_width, card_height),
      .cover_rect = ui0_rect(x, y, card_width, cover_height),
    };
    if (entry->runtime_missing)
    {
      S32 action_y = y + card_height - 32;
      S32 action_width = (card_width - 8) / 2;
      card->locate_rect = ui0_rect(x, action_y, action_width, 30);
      card->remove_rect = ui0_rect(x + action_width + 8,
                                   action_y,
                                   card_width - action_width - 8,
                                   30);
    }
    const char *author = entry->author[0] ? entry->author : "Unknown author";
    char last_opened[64] = {0};
    (void)eightvo_library_format_last_opened(entry->last_opened_time,
                                              last_opened,
                                              ARRAY_COUNT(last_opened));
    (void)cstr_format(card->accessibility_name,
                      ARRAY_COUNT(card->accessibility_name),
                      "%s, %s, %u percent, %s%s",
                      entry->title, author, entry->progress_percent,
                      last_opened,
                      entry->runtime_missing ? ", file missing" : "");
    app->library_card_count += 1;
  }
}

FUNCTION EightvoLibraryCardLayout *
eightvo_library_card_for_entry(EightvoApp *app, U64 entry_id)
{
  if (!app || entry_id == 0) return 0;
  for (U32 index = 0; index < app->library_card_count; index += 1)
    if (app->library_cards[index].entry_id == entry_id)
      return app->library_cards + index;
  return 0;
}

FUNCTION void
eightvo_library_draw_button(EightvoApp *app,
                             UI0Rect rect,
                             const char *label,
                             B32 primary,
                             B32 focused)
{
  if (!app || rect.w <= 0 || rect.h <= 0) return;
  B32 dark = eightvo_theme_profile(app->theme).appearance == UI0AppearanceMode_Dark;
  U32 accent = dark ? 0x00D9B98CU : 0x006F4B2EU;
  U32 fill = primary ? accent : (dark ? 0x00312E2AU : 0x00FFFDF9U);
  U32 border = primary ? accent : (dark ? 0x006A6258U : 0x00B8AFA4U);
  U32 ink = primary ? 0x00FFFFFFU : (dark ? 0x00F4EEE5U : 0x00241D18U);
  (void)draw_push_rounded_rect(&app->draw_commands, DrawLayer_UI,
                               rect.x, rect.y, rect.w, rect.h, 8, fill, border);
  (void)draw_push_text_in_rect(&app->draw_commands, DrawLayer_UI,
                               app->render_state.text_provider, label,
                               rect.x, rect.y, rect.w, rect.h,
                               8, 14, DrawTextHAlign_Center,
                               DrawTextVAlign_Center, ink);
  if (focused)
    (void)draw_push_rounded_rect_stroke(&app->draw_commands, DrawLayer_UI,
                                        rect.x - 3, rect.y - 3,
                                        rect.w + 6, rect.h + 6,
                                        10, 2, accent);
}

FUNCTION void
eightvo_draw_library(EightvoApp *app)
{
  if (!app) return;
  B32 dark = eightvo_theme_profile(app->theme).appearance == UI0AppearanceMode_Dark;
  U32 background = dark ? 0x001A1917U : 0x00F7F3EDU;
  U32 surface = dark ? 0x00252220U : 0x00FFFDF9U;
  U32 ink = dark ? 0x00F4EEE5U : 0x00241D18U;
  U32 secondary = dark ? 0x00BEB4A8U : 0x006C6258U;
  U32 muted = dark ? 0x008D847AU : 0x00908478U;
  U32 accent = dark ? 0x00D9B98CU : 0x006F4B2EU;
  U32 danger = dark ? 0x00E39B91U : 0x0097332AU;
  (void)draw_push_rect(&app->draw_commands, DrawLayer_World,
                       0, 0, app->width, app->height, background);
  (void)draw_push_text_in_rect(&app->draw_commands, DrawLayer_UI,
                               app->render_state.text_provider, "Library",
                               app->width < 620 ? 20 : 32, 14,
                               MAX(app->width - 240, 100), 56,
                               0, 30, DrawTextHAlign_Left,
                               DrawTextVAlign_Center, ink);
  UI0Rect add_rect = eightvo_library_add_rect(app);
  eightvo_library_draw_button(app, add_rect, "+  Add books", 1,
                               app->host_focus_control ==
                                 EightvoHostControl_LibraryAdd &&
                               app->host_focus_visible);

  if (app->library_import_in_progress)
  {
    (void)draw_push_text_in_rect(&app->draw_commands, DrawLayer_UI,
                                 app->render_state.text_provider,
                                 "Adding books...", 0, 74,
                                 app->width, 32, 0, 14,
                                 DrawTextHAlign_Center,
                                 DrawTextVAlign_Center, secondary);
  }
  else if (app->status[0])
  {
    (void)draw_push_text_in_rect(&app->draw_commands, DrawLayer_UI,
                                 app->render_state.text_provider,
                                 app->status, 24, 72,
                                 MAX(app->width - 48, 1), 28, 0, 13,
                                 DrawTextHAlign_Left,
                                 DrawTextVAlign_Center, secondary);
  }
  if (app->library.entry_count == 0)
  {
    S32 panel_width = MIN(MAX(app->width - 48, 260), 560);
    S32 panel_height = 260;
    S32 x = (app->width - panel_width) / 2;
    S32 y = MAX(112, (app->height - panel_height) / 2);
    (void)draw_push_rounded_rect(&app->draw_commands, DrawLayer_World,
                                 x, y, panel_width, panel_height,
                                 16, surface, dark ? 0x00433E38U : 0x00DED6CBU);
    (void)draw_push_text_in_rect(&app->draw_commands, DrawLayer_UI,
                                 app->render_state.text_provider,
                                 "Your library is empty", x + 24, y + 38,
                                 panel_width - 48, 44, 0, 24,
                                 DrawTextHAlign_Center,
                                 DrawTextVAlign_Center, ink);
    (void)draw_push_text_in_rect(&app->draw_commands, DrawLayer_UI,
                                 app->render_state.text_provider,
                                 "Add books to start reading.\nEPUB is supported; files stay in place.",
                                 x + 36, y + 88, panel_width - 72, 72,
                                 4, 15, DrawTextHAlign_Center,
                                 DrawTextVAlign_Center, secondary);
    UI0Rect empty_add = eightvo_library_empty_add_rect(app);
    eightvo_library_draw_button(app, empty_add, "Add books", 1, 0);
    return;
  }

  for (U32 card_index = 0; card_index < app->library_card_count; card_index += 1)
  {
    EightvoLibraryCardLayout *card = app->library_cards + card_index;
    EightvoLibraryEntry *entry =
      eightvo_library_catalog_find_id(&app->library, card->entry_id);
    if (!entry) continue;
    B32 selected = app->library_selected_entry_id == entry->entry_id;
    B32 focused = app->host_focus_control ==
      (EightvoHostControlIdentity)(EightvoHostControl_LibraryBookBase +
                                     card_index) &&
      app->host_focus_visible;
    EightvoHostControlIdentity card_identity =
      (EightvoHostControlIdentity)(EightvoHostControl_LibraryBookBase +
                                     card_index);
    B32 hovered = ui0_rect_contains_point(card->card_rect,
                                           app->input.pointer_x,
                                           app->input.pointer_y);
    if (entry->runtime_missing &&
        (ui0_rect_contains_point(card->locate_rect,
                                 app->input.pointer_x,
                                 app->input.pointer_y) ||
         ui0_rect_contains_point(card->remove_rect,
                                 app->input.pointer_x,
                                 app->input.pointer_y)))
      hovered = 0;
    B32 pressed = hovered && app->input.pointer_down &&
                  app->host_pointer_armed == card_identity;
    U32 card_fill = surface;
    U32 card_border = selected ? accent :
      (dark ? 0x00433E38U : 0x00DED6CBU);
    if (pressed)
    {
      card_fill = dark ? 0x0051453AU : 0x00E1CDB6U;
      card_border = accent;
    }
    else if (hovered)
    {
      card_fill = dark ? 0x00443B32U : 0x00F5EBDDU;
      card_border = dark ? 0x00C8A478U : 0x0087603FU;
    }
    if (hovered && !pressed)
      (void)draw_push_rounded_rect(&app->draw_commands, DrawLayer_World,
                                   card->card_rect.x + 3,
                                   card->card_rect.y + 4,
                                   card->card_rect.w,
                                   card->card_rect.h,
                                   10,
                                   dark ? 0x001C1916U : 0x00D8C8B5U,
                                   dark ? 0x001C1916U : 0x00D8C8B5U);
    (void)draw_push_rounded_rect(&app->draw_commands, DrawLayer_World,
                                 card->card_rect.x, card->card_rect.y,
                                 card->card_rect.w, card->card_rect.h,
                                 10, card_fill, card_border);
    EightvoLibraryThumbnail *thumbnail =
      eightvo_library_thumbnail_load(app, entry);
    if (thumbnail)
    {
      S32 fit_x = 0;
      S32 fit_y = 0;
      S32 fit_w = 0;
      S32 fit_h = 0;
      if (eightvo_fit_image_rect(thumbnail->width, thumbnail->height,
                                  card->cover_rect.x + 8,
                                  card->cover_rect.y + 8,
                                  card->cover_rect.w - 16,
                                  card->cover_rect.h - 16,
                                  &fit_x, &fit_y, &fit_w, &fit_h))
        (void)draw_push_sprite_clipped_sampled(
          &app->draw_commands,
          DrawLayer_World,
          thumbnail->pixels,
          thumbnail->width,
          thumbnail->height,
          thumbnail->stride_pixels,
          eightvo_image_sample_kind(thumbnail->width,
                                     thumbnail->height,
                                     fit_w,
                                     fit_h),
          fit_x,
          fit_y,
          fit_w,
          fit_h,
          card->cover_rect.x,
          card->cover_rect.y,
          card->cover_rect.w,
          card->cover_rect.h);
    }
    else
    {
      (void)draw_push_rounded_rect(&app->draw_commands, DrawLayer_World,
                                   card->cover_rect.x + 8,
                                   card->cover_rect.y + 8,
                                   card->cover_rect.w - 16,
                                   card->cover_rect.h - 16,
                                   7, dark ? 0x00312E2AU : 0x00E9E1D6U,
                                   dark ? 0x00534D45U : 0x00D2C7BAU);
      (void)draw_push_text_in_rect(&app->draw_commands, DrawLayer_UI,
                                   app->render_state.text_provider,
                                   entry->runtime_missing ? "File missing" :
                                     "Cover unavailable",
                                   card->cover_rect.x + 16,
                                   card->cover_rect.y + 16,
                                   card->cover_rect.w - 32,
                                   card->cover_rect.h - 32,
                                   4, 15, DrawTextHAlign_Center,
                                   DrawTextVAlign_Center,
                                   entry->runtime_missing ? danger : muted);
    }
    S32 text_y = card->cover_rect.y + card->cover_rect.h + 8;
    (void)draw_push_text_box(&app->draw_commands, DrawLayer_UI,
                             app->render_state.text_provider,
                             entry->title, card->card_rect.x + 8, text_y,
                             card->card_rect.w - 16, 24, 0, 0, 16, 1,
                             DrawTextHAlign_Left, DrawTextVAlign_Center,
                             DrawTextBoxOverflow_Truncate, ink, 0);
    (void)draw_push_text_box(&app->draw_commands, DrawLayer_UI,
                             app->render_state.text_provider,
                             entry->author[0] ? entry->author : "Unknown author",
                             card->card_rect.x + 8, text_y + 23,
                             card->card_rect.w - 16, 20, 0, 0, 13, 1,
                             DrawTextHAlign_Left, DrawTextVAlign_Center,
                             DrawTextBoxOverflow_Truncate, secondary, 0);
    char info[96] = {0};
    if (entry->runtime_missing)
      eightvo_copy_cstr(info, ARRAY_COUNT(info), "Source file missing");
    else
      (void)cstr_format(info, ARRAY_COUNT(info), "%u%% read",
                        entry->progress_percent);
    (void)draw_push_text_in_rect(&app->draw_commands, DrawLayer_UI,
                                 app->render_state.text_provider, info,
                                 card->card_rect.x + 8, text_y + 45,
                                 card->card_rect.w - 16, 20, 0, 12,
                                 DrawTextHAlign_Left, DrawTextVAlign_Center,
                                 entry->runtime_missing ? danger : muted);
    if (!entry->runtime_missing)
    {
      char last_opened[64] = {0};
      if (eightvo_library_format_last_opened(entry->last_opened_time,
                                              last_opened,
                                              ARRAY_COUNT(last_opened)))
        (void)draw_push_text_in_rect(&app->draw_commands, DrawLayer_UI,
                                     app->render_state.text_provider,
                                     last_opened,
                                     card->card_rect.x + 8, text_y + 65,
                                     card->card_rect.w - 16, 20, 0, 12,
                                     DrawTextHAlign_Left,
                                     DrawTextVAlign_Center, muted);
    }
    if (entry->runtime_missing)
    {
      eightvo_library_draw_button(app, card->locate_rect, "Locate", 0,
        app->host_focus_control ==
          (EightvoHostControlIdentity)(EightvoHostControl_LibraryLocateBase +
                                         card_index) && app->host_focus_visible);
      eightvo_library_draw_button(app, card->remove_rect, "Remove", 0,
        app->host_focus_control ==
          (EightvoHostControlIdentity)(EightvoHostControl_LibraryRemoveBase +
                                         card_index) && app->host_focus_visible);
    }
    if (focused)
      (void)draw_push_rounded_rect_stroke(&app->draw_commands, DrawLayer_UI,
                                          card->card_rect.x - 3,
                                          card->card_rect.y - 3,
                                          card->card_rect.w + 6,
                                          card->card_rect.h + 6,
                                          12, 2, accent);
  }
}

FUNCTION UI0Rect
eightvo_host_exit_rect(const EightvoApp *app)
{
  if (!app || !app->reader_view_ready) return (UI0Rect){0};
  UI0Rect host = app->reader_view_layout.host_toolbar_trailing_rect;
  S32 width = MIN(30, host.w);
  S32 height = MIN(28, host.h);
  return ui0_rect(host.x + MAX((host.w - width) / 2, 0),
                  host.y + MAX((host.h - height) / 2, 0), width, height);
}

FUNCTION EightvoHostControlRecord *
eightvo_host_control_record(EightvoApp *app,
                             EightvoHostControlIdentity identity)
{
  if (!app || identity <= EightvoHostControl_None)
    return 0;
  for (U32 index = 0; index < app->host_control_count; index += 1)
    if (app->host_controls[index].identity == identity)
      return app->host_controls + index;
  return 0;
}

FUNCTION void
eightvo_update_host_control_records(EightvoApp *app)
{
  if (!app) return;
  app->host_control_count = 0;
  if (eightvo_library_active(app))
  {
    UI0Rect add_rect = eightvo_library_add_rect(app);
    ReaderViewSemanticFlags add_flags =
      ReaderViewSemantic_Enabled | ReaderViewSemantic_Focusable;
    if (app->host_focus_control == EightvoHostControl_LibraryAdd)
      add_flags |= ReaderViewSemantic_Focused;
    app->host_controls[app->host_control_count++] = (EightvoHostControlRecord){
      .identity = EightvoHostControl_LibraryAdd,
      .action = EightvoHostControlAction_AddBooks,
      .semantic = {
        .id = ui0_id_from_string("eightvo.library.add"),
        .role = ReaderViewSemantic_Button,
        .flags = add_flags,
        .rect = add_rect,
        .name = {.data = "Add books", .size = 9},
        .source_key = EightvoHostControl_LibraryAdd,
      },
    };
    for (U32 card_index = 0;
         card_index < app->library_card_count &&
           app->host_control_count < ARRAY_COUNT(app->host_controls);
         card_index += 1)
    {
      EightvoLibraryCardLayout *card = app->library_cards + card_index;
      EightvoLibraryEntry *entry =
        eightvo_library_catalog_find_id(&app->library, card->entry_id);
      if (!entry) continue;
      EightvoHostControlIdentity book_identity =
        (EightvoHostControlIdentity)(EightvoHostControl_LibraryBookBase +
                                       card_index);
      ReaderViewSemanticFlags book_flags =
        ReaderViewSemantic_Enabled | ReaderViewSemantic_Focusable;
      if (app->host_focus_control == book_identity)
        book_flags |= ReaderViewSemantic_Focused;
      app->host_controls[app->host_control_count++] =
        (EightvoHostControlRecord){
          .identity = book_identity,
          .action = EightvoHostControlAction_OpenBook,
          .entry_id = entry->entry_id,
          .semantic = {
            .id = ui0_id_from_u64(entry->entry_id),
            .role = ReaderViewSemantic_Button,
            .flags = book_flags,
            .rect = card->card_rect,
            .name = {.data = card->accessibility_name,
                     .size = (S32)strlen(card->accessibility_name)},
            .source_key = entry->entry_id,
          },
        };
      if (!entry->runtime_missing) continue;
      EightvoHostControlIdentity locate_identity =
        (EightvoHostControlIdentity)(EightvoHostControl_LibraryLocateBase +
                                       card_index);
      EightvoHostControlIdentity remove_identity =
        (EightvoHostControlIdentity)(EightvoHostControl_LibraryRemoveBase +
                                       card_index);
      ReaderViewSemanticFlags locate_flags =
        ReaderViewSemantic_Enabled | ReaderViewSemantic_Focusable;
      ReaderViewSemanticFlags remove_flags = locate_flags;
      if (app->host_focus_control == locate_identity)
        locate_flags |= ReaderViewSemantic_Focused;
      if (app->host_focus_control == remove_identity)
        remove_flags |= ReaderViewSemantic_Focused;
      app->host_controls[app->host_control_count++] =
        (EightvoHostControlRecord){
          .identity = locate_identity,
          .action = EightvoHostControlAction_LocateBook,
          .entry_id = entry->entry_id,
          .semantic = {
            .id = ui0_id_from_u64(entry->entry_id ^ 0x4C4F43415445ull),
            .role = ReaderViewSemantic_Button,
            .flags = locate_flags,
            .rect = card->locate_rect,
            .name = {.data = "Locate missing file", .size = 19},
            .source_key = entry->entry_id,
          },
        };
      app->host_controls[app->host_control_count++] =
        (EightvoHostControlRecord){
          .identity = remove_identity,
          .action = EightvoHostControlAction_RemoveBook,
          .entry_id = entry->entry_id,
          .semantic = {
            .id = ui0_id_from_u64(entry->entry_id ^ 0x52454D4F5645ull),
            .role = ReaderViewSemantic_Button,
            .flags = remove_flags,
            .rect = card->remove_rect,
            .name = {.data = "Remove from library", .size = 19},
            .source_key = entry->entry_id,
          },
        };
    }
    if (app->host_focus_control != EightvoHostControl_None &&
        !eightvo_host_control_record(app, app->host_focus_control))
    {
      app->host_focus_control = app->library_card_count > 0 ?
        EightvoHostControl_LibraryBookBase : EightvoHostControl_LibraryAdd;
      app->host_focus_visible = 1;
      eightvo_update_host_control_records(app);
    }
    return;
  }
  UI0Rect exit_rect = eightvo_host_exit_rect(app);
  if (exit_rect.w <= 0 || exit_rect.h <= 0)
  {
    app->host_focus_control = EightvoHostControl_None;
    app->host_focus_visible = 0;
    app->host_exit_pointer_armed = 0;
    return;
  }

  UI0ID toolbar_id = 0;
  for (UI0S32 index = 0;
       index < app->reader_view_frame.semantic_node_count;
       index += 1)
  {
    const ReaderViewSemanticNode *node =
      app->reader_view_frame.semantic_nodes + index;
    if (node->role == ReaderViewSemantic_Toolbar)
    {
      toolbar_id = node->id;
      break;
    }
  }
  ReaderViewSemanticFlags flags =
    ReaderViewSemantic_Enabled | ReaderViewSemantic_Focusable;
  if (app->host_focus_control == EightvoHostControl_ExitReader)
    flags |= ReaderViewSemantic_Focused;
  app->host_controls[0] = (EightvoHostControlRecord){
    .identity = EightvoHostControl_ExitReader,
    .action = EightvoHostControlAction_CloseBook,
    .semantic = {
      .id = ui0_id_from_string("eightvo.reader.close_book"),
      .parent_id = toolbar_id,
      .role = ReaderViewSemantic_Button,
      .flags = flags,
      .rect = exit_rect,
      .name = {.data = "Close Book", .size = 10},
      .control = ReaderViewSemanticControl_None,
      .source_key = EightvoHostControl_ExitReader,
    },
  };
  app->host_control_count = 1;
}

FUNCTION B32
eightvo_host_focus_set(EightvoApp *app,
                        EightvoHostControlIdentity identity,
                        B32 visible)
{
  if (!app) return 0;
  if (identity != EightvoHostControl_None &&
      !eightvo_host_control_record(app, identity))
    return 0;
  app->host_focus_control = identity;
  app->host_focus_visible = identity != EightvoHostControl_None && visible;
  if (identity != EightvoHostControl_None)
  {
    app->reader_view_state.focus_id = 0;
    app->reader_view_state.focus_visible = 0;
    app->reader_view_state.pending_accessibility_focus_id = 0;
  }
  eightvo_update_host_control_records(app);
  return 1;
}

FUNCTION B32
eightvo_library_remove_entry(EightvoApp *app, U64 entry_id)
{
  if (!app || entry_id == 0) return 0;
  char thumbnail_path[EightvoPathCap] = {0};
  if (eightvo_library_thumbnail_path(app, entry_id, thumbnail_path,
                                      ARRAY_COUNT(thumbnail_path)))
    (void)os_file_delete(thumbnail_path);
  if (!eightvo_library_catalog_remove(&app->library, entry_id)) return 0;
  eightvo_library_thumbnail_cache_reset(&app->library_thumbnail_cache);
  app->library_selected_entry_id = app->library.entry_count > 0 ?
    app->library.entries[0].entry_id : 0;
  app->host_focus_control = app->library.entry_count > 0 ?
    EightvoHostControl_LibraryBookBase : EightvoHostControl_LibraryAdd;
  app->host_focus_visible = 1;
  eightvo_set_statusf(app, "Removed from library; source file was not deleted");
  return eightvo_save_library(app);
}

FUNCTION B32
eightvo_host_control_invoke(EightvoApp *app,
                             EightvoHostControlIdentity identity)
{
  EightvoHostControlRecord *record =
    eightvo_host_control_record(app, identity);
  if (!app || !record) return 0;
  switch (record->action)
  {
    case EightvoHostControlAction_CloseBook:
      return eightvo_close_book(app);
    case EightvoHostControlAction_AddBooks:
      return eightvo_pick_epub(app);
    case EightvoHostControlAction_OpenBook:
    {
      EightvoLibraryEntry *entry =
        eightvo_library_catalog_find_id(&app->library, record->entry_id);
      if (!entry) return 0;
      app->library_selected_entry_id = entry->entry_id;
      if (entry->runtime_missing)
      {
        eightvo_set_statusf(app, "Source file missing; use Locate or Remove");
        return 1;
      }
      return eightvo_open_path(app, entry->source_path);
    }
    case EightvoHostControlAction_LocateBook:
      return eightvo_locate_library_entry(app, record->entry_id);
    case EightvoHostControlAction_RemoveBook:
      return eightvo_library_remove_entry(app, record->entry_id);
    case EightvoHostControlAction_None:
    default:
      return 0;
  }
}

FUNCTION B32
eightvo_host_focus_neighbors(const EightvoApp *app,
                              UI0ID *out_before,
                              UI0ID *out_after)
{
  if (out_before) *out_before = 0;
  if (out_after) *out_after = 0;
  if (!app || !app->reader_view_frame.semantic_nodes ||
      app->reader_view_state.popup != ReaderViewPopup_None)
    return 0;
  UI0ID before_id = 0;
  UI0ID after_id = 0;
  UI0ID open_id = 0;
  for (UI0S32 index = 0;
       index < app->reader_view_frame.semantic_node_count;
       index += 1)
  {
    const ReaderViewSemanticNode *node =
      app->reader_view_frame.semantic_nodes + index;
    if (node->control == ReaderViewSemanticControl_Open)
      open_id = node->id;
    else if (node->control == ReaderViewSemanticControl_Find)
      before_id = node->id;
    else if (node->control == ReaderViewSemanticControl_Fullscreen)
      after_id = node->id;
  }
  if ((!before_id || !after_id) && open_id)
  {
    before_id = open_id;
    after_id = open_id;
  }
  if (!before_id || !after_id) return 0;
  if (out_before) *out_before = before_id;
  if (out_after) *out_after = after_id;
  return 1;
}

FUNCTION B32
eightvo_host_keyboard_tab(EightvoApp *app, B32 reverse)
{
  enum { OpenOrderCount = 2, ReferenceOrderCount = 13 };
  static const ReaderViewSemanticControl open_order[OpenOrderCount] = {
    ReaderViewSemanticControl_Open,
    ReaderViewSemanticControl_None,
  };
  static const ReaderViewSemanticControl
    reference_order[ReferenceOrderCount] = {
      ReaderViewSemanticControl_Contents,
      ReaderViewSemanticControl_Find,
      ReaderViewSemanticControl_None,
      ReaderViewSemanticControl_Fullscreen,
      ReaderViewSemanticControl_Annotations,
      ReaderViewSemanticControl_FontSize,
      ReaderViewSemanticControl_LineSpacing,
      ReaderViewSemanticControl_FontFamily,
      ReaderViewSemanticControl_Theme,
      ReaderViewSemanticControl_Bookmark,
      ReaderViewSemanticControl_PreviousPage,
      ReaderViewSemanticControl_NextPage,
      ReaderViewSemanticControl_Progress,
    };
  if (!app || app->reader_view_state.popup != ReaderViewPopup_None) return 0;
  if (eightvo_library_active(app))
  {
    if (app->host_control_count == 0) return 0;
    S32 current = -1;
    for (U32 index = 0; index < app->host_control_count; index += 1)
      if (app->host_controls[index].identity == app->host_focus_control)
        current = (S32)index;
    S32 target = current < 0 ? (reverse ? (S32)app->host_control_count - 1 : 0) :
      (reverse ? current - 1 : current + 1);
    while (target < 0) target += (S32)app->host_control_count;
    target %= (S32)app->host_control_count;
    EightvoHostControlRecord *record = app->host_controls + target;
    app->library_selected_entry_id = record->entry_id ?
      record->entry_id : app->library_selected_entry_id;
    return eightvo_host_focus_set(app, record->identity, 1);
  }

  ReaderViewSemanticControl current_shared = ReaderViewSemanticControl_None;
  B32 open_present = 0;
  for (UI0S32 index = 0;
       index < app->reader_view_frame.semantic_node_count;
       index += 1)
  {
    const ReaderViewSemanticNode *node =
      app->reader_view_frame.semantic_nodes + index;
    if (node->control == ReaderViewSemanticControl_Open) open_present = 1;
    if (node->id == app->reader_view_state.focus_id)
      current_shared = node->control;
  }

  const ReaderViewSemanticControl *order =
    open_present ? open_order : reference_order;
  S32 order_count = open_present ? OpenOrderCount : ReferenceOrderCount;
  S32 current_index = -1;
  for (S32 index = 0; index < order_count; index += 1)
  {
    if ((order[index] == ReaderViewSemanticControl_None &&
         app->host_focus_control == EightvoHostControl_ExitReader) ||
        (order[index] != ReaderViewSemanticControl_None &&
         app->host_focus_control == EightvoHostControl_None &&
         order[index] == current_shared))
    {
      current_index = index;
      break;
    }
  }
  if (current_index < 0) return 0;

  B32 panel_open = app->reader_view_state.left_panel !=
                     ReaderViewLeftPanel_None ||
                   app->reader_view_state.right_panel_open;
  for (S32 offset = 1; offset <= order_count; offset += 1)
  {
    S32 target_index = reverse ? current_index - offset :
                                 current_index + offset;
    /* Shared panel controls are published after Progress. Preserve the
       frozen toolbar order, but let Reader View traverse that panel tail
       instead of wrapping across it. */
    if (panel_open &&
        ((reverse && target_index < 0) ||
         (!reverse && target_index >= order_count)))
      return 0;
    while (target_index < 0) target_index += order_count;
    target_index %= order_count;
    ReaderViewSemanticControl target = order[target_index];
    if (target == ReaderViewSemanticControl_None)
      return eightvo_host_focus_set(app, EightvoHostControl_ExitReader, 1);
    for (UI0S32 index = 0;
         index < app->reader_view_frame.semantic_node_count;
         index += 1)
    {
      const ReaderViewSemanticNode *node =
        app->reader_view_frame.semantic_nodes + index;
      if (node->control == target &&
          (node->flags & ReaderViewSemantic_Focusable) != 0)
      {
        (void)eightvo_host_focus_set(app, EightvoHostControl_None, 0);
        return reader_view_accessibility_focus(&app->reader_view_state,
                                               node->id);
      }
    }
  }
  return 0;
}

FUNCTION B32
eightvo_host_keyboard_activate(EightvoApp *app)
{
  if (!app || app->host_focus_control == EightvoHostControl_None) return 0;
  return eightvo_host_control_invoke(app, app->host_focus_control);
}

FUNCTION void
eightvo_host_pointer_move(EightvoApp *app, S32 x, S32 y)
{
  if (!app) return;
  app->input.pointer_x = x;
  app->input.pointer_y = y;
  if (eightvo_library_active(app) &&
      app->host_pointer_armed != EightvoHostControl_None)
  {
    EightvoHostControlRecord *record =
      eightvo_host_control_record(app, app->host_pointer_armed);
    UI0Rect rect = record ? record->semantic.rect : (UI0Rect){0};
    if (app->library.entry_count == 0 &&
        app->host_pointer_armed == EightvoHostControl_LibraryAdd)
    {
      UI0Rect empty = eightvo_library_empty_add_rect(app);
      if (ui0_rect_contains_point(empty, x, y)) return;
    }
    if (!record || !ui0_rect_contains_point(rect, x, y))
      app->host_pointer_armed = EightvoHostControl_None;
    return;
  }
  UI0Rect exit_rect = eightvo_host_exit_rect(app);
  if (app->host_exit_pointer_armed &&
      (exit_rect.w <= 0 || !ui0_rect_contains_point(exit_rect, x, y)))
    app->host_exit_pointer_armed = 0;
}

FUNCTION void
eightvo_host_pointer_cancel(EightvoApp *app)
{
  if (!app) return;
  B32 cancel_active = app->input.pointer_down ||
                      app->host_pointer_armed != EightvoHostControl_None ||
                      app->host_exit_pointer_armed ||
                      app->selection_dragging;
  app->host_exit_pointer_armed = 0;
  app->host_pointer_armed = EightvoHostControl_None;
  app->input.pointer_down = 0;
  app->input.pointer_pressed = 0;
  app->input.pointer_selection_release = 0;
  if (cancel_active)
  {
    app->input.pointer_released = 0;
    app->reader_view_state.active_id = 0;
    app->selection_dragging = 0;
  }
}

FUNCTION void
eightvo_draw_host_exit_slot(EightvoApp *app)
{
  UI0Rect rect = eightvo_host_exit_rect(app);
  if (!app || rect.w <= 0 || rect.h <= 0) return;
  B32 hovered = ui0_rect_contains_point(rect,
                                        app->input.pointer_x,
                                        app->input.pointer_y);
  UI0ControlStateFlags state = hovered ? UI0ControlState_Hovered : 0;
  if (hovered && app->input.pointer_down && app->host_exit_pointer_armed)
    state |= UI0ControlState_Pressed | UI0ControlState_Active;
  if (app->host_focus_control == EightvoHostControl_ExitReader)
  {
    state |= UI0ControlState_Focused;
    if (app->host_focus_visible) state |= UI0ControlState_FocusVisible;
  }
  UI0ControlRecord control = {
    .id = ui0_id_from_string("eightvo.reader.close_book"),
    .kind = UI0ControlKind_IconButton,
    .root = UI0RootKind_Normal,
    .state = state,
    .rect = rect,
    .clip_rect = app->reader_view_layout.host_toolbar_trailing_rect,
    .text_rect = rect,
    .label_hash = ui0_id_from_string("Close Book"),
    .label_len = 10,
  };
  /*
  A focused labeled IconButton emits fill, border, text, and focus-ring
  commands before the explicit Close icon. Keep all five bounded records so
  keyboard focus cannot silently displace either the ring or the glyph.
  */
  UI0DrawCommand commands[5] = {0};
  UI0DrawContext draw = {0};
  ui0_draw_begin_frame(&draw,
                       commands,
                       ARRAY_COUNT(commands),
                       ui0_draw_theme_from_resolved(&app->reader_view_theme));
  (void)ui0_draw_control_record(&draw, &control);
  S32 icon_size = MIN(18, MIN(rect.w, rect.h));
  (void)ui0_draw_push_icon(&draw,
                           &control,
                           UI0IconKind_Close,
                           ui0_rect(rect.x + (rect.w - icon_size) / 2,
                                    rect.y + (rect.h - icon_size) / 2,
                                    icon_size,
                                    icon_size));
  for (UI0S32 index = 0; index < draw.command_count; index += 1)
  {
    UI0DrawCommand command = draw.commands[index];
    if (command.op == UI0DrawOp_ControlFill)
    {
      for (UI0S32 candidate_index = 0;
           candidate_index < draw.command_count;
           candidate_index += 1)
      {
        const UI0DrawCommand *candidate = draw.commands + candidate_index;
        if (eightvo_ui0_border_matches_fill(candidate, &command))
        {
          command.stroke_color = candidate->color;
          break;
        }
      }
      eightvo_draw_ui0_control_fill(app, &command);
    }
    else if (command.op == UI0DrawOp_Icon)
    {
      eightvo_draw_ui0_icon(app, &command);
    }
    else if (command.op == UI0DrawOp_FocusRing)
    {
      eightvo_draw_ui0_focus_ring(app, &command);
    }
  }
}

FUNCTION B32
eightvo_pick_epub_paths(EightvoApp *app,
                         B32 allow_multiple,
                         char paths[EightvoLibraryImportPathCap]
                                   [EightvoLibraryPathCap],
                         U32 *out_count)
{
  if (out_count) *out_count = 0;
  if (!app || !app->window || !paths || !out_count) return 0;
  wchar_t selection[65536] = {0};
  OPENFILENAMEW dialog = {0};
  dialog.lStructSize = sizeof(dialog);
  dialog.hwndOwner = app->window;
  dialog.lpstrFilter = L"EPUB Books\0*.epub\0All Files\0*.*\0";
  dialog.lpstrFile = selection;
  dialog.nMaxFile = ARRAY_COUNT(selection);
  dialog.Flags = OFN_FILEMUSTEXIST | OFN_PATHMUSTEXIST | OFN_EXPLORER |
                 (allow_multiple ? OFN_ALLOWMULTISELECT : 0);
  if (!GetOpenFileNameW(&dialog)) { return 0; }

  const wchar_t *first = selection;
  const wchar_t *next = first + wcslen(first) + 1;
  if (!next[0])
  {
    if (WideCharToMultiByte(CP_UTF8, 0, first, -1, paths[0],
                            EightvoLibraryPathCap, 0, 0) <= 0)
      return 0;
    *out_count = 1;
    return 1;
  }
  U32 count = 0;
  while (next[0] && count < EightvoLibraryImportPathCap)
  {
    wchar_t joined[EightvoLibraryPathCap] = {0};
    if (swprintf_s(joined, ARRAY_COUNT(joined), L"%ls\\%ls", first, next) <= 0 ||
        WideCharToMultiByte(CP_UTF8, 0, joined, -1, paths[count],
                            EightvoLibraryPathCap, 0, 0) <= 0)
      return 0;
    count += 1;
    next += wcslen(next) + 1;
  }
  *out_count = count;
  return count > 0;
}

FUNCTION B32
eightvo_pick_epub_impl(EightvoApp *app)
{
  if (!app) return 0;
  app->native_picker_request_count += 1;
  if (app->suppress_native_picker)
  {
    eightvo_set_statusf(app, "Add books picker requested");
    return 1;
  }
  if (!app->window) return 0;
  char paths[EightvoLibraryImportPathCap][EightvoLibraryPathCap] = {0};
  U32 path_count = 0;
  B32 library_was_active = eightvo_library_active(app);
  if (!eightvo_pick_epub_paths(app, 1, paths, &path_count)) return 0;
  app->library_import_in_progress = 1;
  if (app->window)
  {
    InvalidateRect(app->window, 0, FALSE);
    UpdateWindow(app->window);
  }
  U32 imported = 0;
  for (U32 index = 0; index < path_count; index += 1)
    if (eightvo_open_path(app, paths[index])) imported += 1;
  if (library_was_active && epub_reader_is_open(&app->reader))
    (void)eightvo_close_book(app);
  else if (!library_was_active && imported > 1)
    (void)eightvo_open_path(app, paths[0]);
  app->library_import_in_progress = 0;
  eightvo_library_catalog_refresh_missing(&app->library);
  if (library_was_active)
    eightvo_set_statusf(app, imported == 1 ? "Added 1 book" :
                         "Added %u books", imported);
  return imported > 0;
}

FUNCTION B32
eightvo_pick_epub(EightvoApp *app)
{
  if (!eightvo_begin_document_mutation(app)) return 0;
  B32 was_open = epub_reader_is_open(&app->reader);
  DocDocumentId document_id = epub_reader_document_id(&app->reader);
  U64 document_generation = app->reader.document_generation;
  app->page_action_internal_dispatch = 1;
  B32 result = eightvo_pick_epub_impl(app);
  app->page_action_internal_dispatch = 0;
  B32 reader_changed = was_open != epub_reader_is_open(&app->reader) ||
    document_id != epub_reader_document_id(&app->reader) ||
    document_generation != app->reader.document_generation;
  eightvo_complete_document_mutation(
    app, !app->suppress_native_picker && reader_changed);
  return result;
}

FUNCTION B32
eightvo_locate_library_entry_impl(EightvoApp *app, U64 entry_id)
{
  EightvoLibraryEntry *entry =
    eightvo_library_catalog_find_id(app ? &app->library : 0, entry_id);
  if (!entry || !app->window) return 0;
  char paths[EightvoLibraryImportPathCap][EightvoLibraryPathCap] = {0};
  U32 count = 0;
  if (!eightvo_pick_epub_paths(app, 0, paths, &count) || count != 1) return 0;
  char normalized[EightvoLibraryPathCap] = {0};
  if (!eightvo_library_normalize_path(paths[0], normalized,
                                       ARRAY_COUNT(normalized)))
    return 0;
  EightvoLibraryEntry *duplicate =
    eightvo_library_catalog_find_path(&app->library, normalized);
  U64 duplicate_id = duplicate && duplicate->entry_id != entry_id ?
    duplicate->entry_id : 0;
  app->library_locate_entry_id = entry_id;
  if (!eightvo_open_path(app, normalized))
  {
    app->library_locate_entry_id = 0;
    return 0;
  }
  if (duplicate_id)
  {
    char thumbnail_path[EightvoPathCap] = {0};
    if (eightvo_library_thumbnail_path(app, duplicate_id, thumbnail_path,
                                        ARRAY_COUNT(thumbnail_path)))
      (void)os_file_delete(thumbnail_path);
    (void)eightvo_library_catalog_remove(&app->library, duplicate_id);
  }
  app->library_selected_entry_id = entry_id;
  eightvo_library_catalog_sort(&app->library);
  (void)eightvo_save_library(app);
  return 1;
}

FUNCTION B32
eightvo_locate_library_entry(EightvoApp *app, U64 entry_id)
{
  if (!eightvo_begin_document_mutation(app)) return 0;
  B32 was_open = epub_reader_is_open(&app->reader);
  DocDocumentId document_id = epub_reader_document_id(&app->reader);
  U64 document_generation = app->reader.document_generation;
  app->page_action_internal_dispatch = 1;
  B32 result = eightvo_locate_library_entry_impl(app, entry_id);
  app->page_action_internal_dispatch = 0;
  B32 reader_changed = was_open != epub_reader_is_open(&app->reader) ||
    document_id != epub_reader_document_id(&app->reader) ||
    document_generation != app->reader.document_generation;
  eightvo_complete_document_mutation(app, reader_changed);
  return result;
}

FUNCTION B32
eightvo_set_clipboard_text(EightvoApp *app, ReaderViewText text)
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
eightvo_get_clipboard_text(EightvoApp *app)
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
eightvo_append_input_wchar(EightvoApp *app, wchar_t value)
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
  if (size > 0 && app->input.text_length + size < EightvoInputTextCap)
  {
    MemoryCopy(app->input.text + app->input.text_length, encoded, (U64)size);
    app->input.text_length += size;
    app->input.text[app->input.text_length] = 0;
  }
}

typedef enum EightvoReaderKeyRoute
{
  EightvoReaderKeyRoute_None,
  EightvoReaderKeyRoute_Handled,
  EightvoReaderKeyRoute_CloseRequested,
} EightvoReaderKeyRoute;

FUNCTION B32
eightvo_page_direction_for_key(EightvoApp *app,
                                WPARAM key,
                                S32 *out_direction)
{
  if (out_direction) { *out_direction = 0; }
  if (!app || !out_direction || !epub_reader_is_open(&app->reader) ||
      eightvo_reader_view_text_editing(app))
  {
    return 0;
  }
  if (eightvo_reader_view_horizontal_move_is_shared(app) &&
      (key == VK_LEFT || key == VK_RIGHT))
  {
    return 0;
  }
  if (app->reader_view_state.focus_id != 0 &&
      (key == VK_LEFT || key == VK_RIGHT))
  {
    *out_direction = key == VK_LEFT ? -1 : 1;
    return 1;
  }
  if (app->reader_view_state.focus_id == 0 &&
      app->host_focus_control == EightvoHostControl_None)
  {
    if (key == VK_LEFT || key == VK_PRIOR)
    {
      *out_direction = -1;
      return 1;
    }
    if ((key == VK_RIGHT || key == VK_NEXT || key == VK_SPACE) &&
        !eightvo_reader_view_space_activates_focus(app))
    {
      *out_direction = 1;
      return 1;
    }
  }
  return 0;
}

FUNCTION B32
eightvo_page_repeat_should_coalesce_keydown(EightvoApp *app,
                                              WPARAM key,
                                              LPARAM l_param,
                                              S32 *out_direction)
{
  S32 direction = 0;
  if (out_direction) { *out_direction = 0; }
  if ((l_param & (1LL << 30)) == 0 ||
      !eightvo_page_direction_for_key(app, key, &direction) ||
      !app->page_repeat_active || app->page_repeat_key != key ||
      app->page_repeat_direction != direction)
  {
    return 0;
  }
  if (out_direction) { *out_direction = direction; }
  return 1;
}

FUNCTION EightvoCanonicalPageIdentity
eightvo_canonical_page_identity(SourceReaderPageRange page)
{
  return (EightvoCanonicalPageIdentity){
    .spine_index = page.spine_index,
    .spine_page_index = page.spine_page_index,
    .spine_page_count = page.spine_page_count,
    .first_byte = page.first_byte,
    .one_past_last_byte = page.one_past_last_byte,
  };
}

FUNCTION B32
eightvo_canonical_page_identity_equal(EightvoCanonicalPageIdentity a,
                                        EightvoCanonicalPageIdentity b)
{
  /* Ordinals, totals, and row indexes belong to the bounded pagination owner
     that produced the range. They can change when the same physical page is
     republished from a sliding window. Reader0's stable page identity is the
     spine plus exact byte boundaries. */
  return a.spine_index == b.spine_index &&
    a.first_byte == b.first_byte &&
    a.one_past_last_byte == b.one_past_last_byte;
}

FUNCTION U64
eightvo_frame_image_visual_identity(const EpubReaderFrame *frame,
                                      B32 *out_valid)
{
  if (out_valid) *out_valid = 0;
  if (!frame || frame->image_count == 0 || !frame->images) return 0;
  U64 identity = 1469598103934665603ull;
  for (U32 image_index = 0; image_index < frame->image_count; image_index += 1)
  {
    EpubReaderFrameImage image = frame->images[image_index];
    U32 visual_units = 0;
    for (U32 row_index = 0; row_index < frame->style_row_count; row_index += 1)
    {
      if (frame->style_rows[row_index].row == image.row)
      {
        visual_units = frame->style_rows[row_index].visual_units;
        break;
      }
    }
    if (visual_units == 0) return 0;
    U64 values[] = {
      image.row,
      image.resource_index,
      image.image_placement,
      image.text_byte_start,
      image.text_byte_end,
      visual_units,
    };
    identity ^= u64_hash_bytes(values, sizeof(values));
    identity *= 1099511628211ull;
  }
  if (identity == 0) identity = 1;
  if (out_valid) *out_valid = 1;
  return identity;
}

FUNCTION B32
eightvo_frame_matches_canonical_page(
  const EightvoApp *app,
  EightvoCanonicalPageIdentity page,
  U64 document_id,
  U64 document_generation)
{
  if (!app || !app->frame.ready || !app->frame.document_open ||
      app->frame.document_id != document_id ||
      app->frame.document_generation != document_generation ||
      app->frame.spine_index != page.spine_index ||
      (app->frame.page_index != 0 &&
       app->frame.page_index != page.spine_page_index + 1) ||
      (app->frame.page_count != 0 &&
       app->frame.page_count != page.spine_page_count))
  {
    return 0;
  }
  U64 page_size = page.one_past_last_byte - page.first_byte;
  if (app->frame.view_byte_offset == page.first_byte &&
      app->frame.visible_text.size == page_size)
  {
    return 1;
  }
  /* Image-only frames intentionally have no visible UTF-8 payload. Their
     exact canonical range stays in Reader0 current_page; the captured frame
     is bound to it by document/spine/page identity plus the visual-unit and
     image-placement signature below. */
  B32 image_identity_valid = 0;
  (void)eightvo_frame_image_visual_identity(&app->frame,
                                              &image_identity_valid);
  return app->frame.visible_text.size == 0 &&
    app->frame.image_count > 0 && image_identity_valid;
}

FUNCTION B32
eightvo_capture_presentation_identity(const EightvoApp *app,
                                        EightvoPresentationIdentity *out)
{
  if (out) MemoryZeroStruct(out);
  if (!app || !out) return 0;
  out->frame_generation = app->page_action_frame_generation;
  if (!epub_reader_is_open(&app->reader))
  {
    out->kind = EightvoPresentationIdentity_Library;
    return 1;
  }
  if (!app->reader.has_current_page || app->reader.document_id == 0 ||
      app->reader.document_generation == 0 ||
      app->reader.current_page.spine_page_count == 0 ||
      app->reader.current_page.first_byte >=
        app->reader.current_page.one_past_last_byte)
  {
    return 0;
  }
  out->kind = EightvoPresentationIdentity_Page;
  out->document_id = epub_reader_document_id(&app->reader);
  out->document_generation = app->reader.document_generation;
  out->layout_generation = epub_reader_layout_key_generation(app->layout_key);
  out->page = eightvo_canonical_page_identity(app->reader.current_page);
  if (eightvo_frame_matches_canonical_page(
        app, out->page, out->document_id, out->document_generation))
  {
    out->frame_capture_generation = app->frame_capture_generation;
    out->reader_frame_generation = app->frame.generation;
    out->image_count = app->frame.image_count;
    if (out->image_count > 0)
    {
      B32 image_identity_valid = 0;
      out->image_visual_identity = eightvo_frame_image_visual_identity(
        &app->frame, &image_identity_valid);
      if (!image_identity_valid) return 0;
    }
  }
  return out->document_id != 0 && out->document_generation != 0 &&
    out->layout_generation != 0;
}

FUNCTION B32
eightvo_capture_rendered_presentation_identity(
  const EightvoApp *app,
  EightvoPresentationIdentity *out)
{
  if (!eightvo_capture_presentation_identity(app, out)) return 0;
  if (out->kind == EightvoPresentationIdentity_Library) return 1;
  return out->frame_capture_generation > 0 &&
    out->reader_frame_generation != 0 &&
    eightvo_frame_matches_canonical_page(
      app, out->page, out->document_id, out->document_generation) &&
    (out->image_count == 0 || out->image_visual_identity != 0);
}

FUNCTION B32
eightvo_presentation_identity_equal(EightvoPresentationIdentity a,
                                      EightvoPresentationIdentity b)
{
  if (a.kind == EightvoPresentationIdentity_None || a.kind != b.kind ||
      a.frame_generation == 0 || a.frame_generation != b.frame_generation)
  {
    return 0;
  }
  if (a.kind == EightvoPresentationIdentity_Library) return 1;
  return a.document_id == b.document_id &&
    a.document_generation == b.document_generation &&
    a.layout_generation == b.layout_generation &&
    b.frame_capture_generation >= a.frame_capture_generation &&
    (a.reader_frame_generation == 0 ||
     a.reader_frame_generation == b.reader_frame_generation) &&
    (a.image_count == 0 ||
     (a.image_count == b.image_count &&
      a.image_visual_identity == b.image_visual_identity)) &&
    eightvo_canonical_page_identity_equal(a.page, b.page);
}

/* External document mutations never pass an outstanding presentation. One
   directional keyboard tap alone may occupy the explicit pending slot. */
FUNCTION B32
eightvo_begin_document_mutation(EightvoApp *app)
{
  if (!app) return 0;
  if (!app->window || app->page_action_internal_dispatch)
  {
    app->capture_frame_failed_since_mutation = 0;
    return 1;
  }
  if (app->page_action_waiting_for_present)
  {
    if (app->page_action_mutation_drop_count < UINT32_MAX)
      app->page_action_mutation_drop_count += 1;
    eightvo_cancel_page_repeat_for_mutation(app);
    return 0;
  }
  if (app->page_repeat_active || app->page_action_pending)
    eightvo_cancel_page_repeat_for_mutation(app);
  app->capture_frame_failed_since_mutation = 0;
  return 1;
}

FUNCTION void
eightvo_complete_document_mutation(EightvoApp *app, B32 changed)
{
  if (!app || !changed || !app->window || app->page_action_internal_dispatch)
    return;
  eightvo_page_action_note_emitted(app);
  (void)InvalidateRect(app->window, 0, FALSE);
}

FUNCTION U64
eightvo_frame_non_whitespace_byte_count(const EpubReaderFrame *frame)
{
  if (!frame || !frame->visible_text.str) return 0;
  U64 count = 0;
  for (U64 index = 0; index < frame->visible_text.size; index += 1)
  {
    U8 byte = frame->visible_text.str[index];
    if (byte != ' ' && byte != '\t' && byte != '\r' && byte != '\n')
      count += 1;
  }
  return count;
}

FUNCTION B32
eightvo_gotm_word_scalar(U32 scalar)
{
  if ((scalar >= (U32)'a' && scalar <= (U32)'z') ||
      (scalar >= (U32)'A' && scalar <= (U32)'Z') ||
      (scalar >= (U32)'0' && scalar <= (U32)'9') || scalar == (U32)'_')
  {
    return 1;
  }
  if (scalar < 0x80u || scalar == 0x00a0u || scalar == 0x1680u ||
      (scalar >= 0x2000u && scalar <= 0x206fu) ||
      (scalar >= 0x2e00u && scalar <= 0x2e7fu) ||
      (scalar >= 0x3000u && scalar <= 0x303fu))
  {
    return 0;
  }
  /* The exact English-language fixture uses non-ASCII letters as word
     characters and General Punctuation for its typographic punctuation. */
  return 1;
}

FUNCTION B32
eightvo_gotm_word_connector(U32 scalar)
{
  return scalar == (U32)'\'' || scalar == (U32)'-' ||
    scalar == 0x2010u || scalar == 0x2011u || scalar == 0x2019u;
}

FUNCTION B32
eightvo_utf8_previous_scalar(String8 text,
                              U64 byte_offset,
                              U64 *out_byte_offset,
                              U32 *out_scalar)
{
  if (out_byte_offset) *out_byte_offset = 0;
  if (out_scalar) *out_scalar = 0;
  if (!text.str || byte_offset == 0 || byte_offset > text.size) return 0;
  U64 at = byte_offset - 1;
  U32 continuation_count = 0;
  while (at > 0 && (text.str[at] & 0xc0u) == 0x80u &&
         continuation_count < 3)
  {
    at -= 1;
    continuation_count += 1;
  }
  BaseUnicodeDecode decode = base_unicode_utf8_decode(text, at);
  if (!decode.valid || decode.advance == 0 || at + decode.advance != byte_offset)
    return 0;
  if (out_byte_offset) *out_byte_offset = at;
  if (out_scalar) *out_scalar = decode.scalar;
  return 1;
}

/* Independent from adjacent-page replay: validate the raw UTF-8 source bytes
   around a canonical range start. A page may start at a word, but never after
   another word scalar or after an in-word apostrophe/hyphen. */
FUNCTION B32
eightvo_gotm_page_start_is_word_boundary(String8 spine_text,
                                           U64 first_byte)
{
  if (!spine_text.str || first_byte > spine_text.size) return 0;
  if (first_byte == 0 || first_byte == spine_text.size) return 1;
  if ((spine_text.str[first_byte] & 0xc0u) == 0x80u) return 0;
  BaseUnicodeDecode current = base_unicode_utf8_decode(spine_text, first_byte);
  if (!current.valid || current.advance == 0) return 0;
  if (eightvo_gotm_word_connector(current.scalar))
  {
    U32 previous_scalar = 0;
    BaseUnicodeDecode next = base_unicode_utf8_decode(
      spine_text, first_byte + current.advance);
    if (eightvo_utf8_previous_scalar(spine_text,
                                      first_byte,
                                      0,
                                      &previous_scalar) &&
        eightvo_gotm_word_scalar(previous_scalar) && next.valid &&
        next.advance > 0 && eightvo_gotm_word_scalar(next.scalar))
    {
      return 0;
    }
    return 1;
  }
  if (!eightvo_gotm_word_scalar(current.scalar)) return 1;

  U64 previous_at = 0;
  U32 previous_scalar = 0;
  if (!eightvo_utf8_previous_scalar(spine_text,
                                     first_byte,
                                     &previous_at,
                                     &previous_scalar))
  {
    return 0;
  }
  if (eightvo_gotm_word_scalar(previous_scalar)) return 0;
  if (eightvo_gotm_word_connector(previous_scalar))
  {
    U32 before_connector = 0;
    if (eightvo_utf8_previous_scalar(spine_text,
                                      previous_at,
                                      0,
                                      &before_connector) &&
        eightvo_gotm_word_scalar(before_connector))
    {
      return 0;
    }
  }
  return 1;
}

/* Exact gotm_new.epub navigation-reference assertion. The invoking PowerShell
   smokes lock the fixture size and SHA-256; this is not production policy for
   arbitrary publisher pages, where a one-character page can be legitimate. */
FUNCTION B32
eightvo_gotm_navigation_frame_is_canonical_nonempty(const EightvoApp *app,
                                                      B32 *out_orphan_text,
                                                      B32 *out_invalid_word_start,
                                                      U64 *out_text_bytes,
                                                      U32 *out_text_rows)
{
  if (out_orphan_text) *out_orphan_text = 0;
  if (out_invalid_word_start) *out_invalid_word_start = 0;
  if (out_text_bytes) *out_text_bytes = 0;
  if (out_text_rows) *out_text_rows = 0;
  if (!app || !epub_reader_is_open(&app->reader) ||
      !app->reader.has_current_page ||
      app->reader.current_page.spine_page_count == 0 ||
      app->reader.current_page.first_byte >=
      app->reader.current_page.one_past_last_byte ||
      app->reader.current_page.spine_index != app->reader.active_spine_index ||
      !app->frame.ready || !app->frame.document_open ||
      !eightvo_frame_text_rows_are_complete(&app->frame, 0, 0))
  {
    return 0;
  }
  EightvoCanonicalPageIdentity canonical_page =
    eightvo_canonical_page_identity(app->reader.current_page);
  if (!eightvo_frame_matches_canonical_page(
        app,
        canonical_page,
        epub_reader_document_id(&app->reader),
        app->reader.document_generation))
  {
    return 0;
  }
  U64 text_bytes = eightvo_frame_non_whitespace_byte_count(&app->frame);
  U32 text_rows = app->frame.style_row_count;
  B32 long_form_text = app->frame.image_count == 0 &&
    app->reader.spine_text.size >= EightvoGotmMinimumProseSpineBytes;
  B32 orphan_text = long_form_text &&
    (text_bytes < EightvoGotmMinimumProseTextBytes ||
     text_rows < EightvoGotmMinimumProseTextRows);
  B32 invalid_word_start = long_form_text &&
    !eightvo_gotm_page_start_is_word_boundary(
      app->reader.spine_text, app->reader.current_page.first_byte);
  if (out_orphan_text) *out_orphan_text = orphan_text;
  if (out_invalid_word_start) *out_invalid_word_start = invalid_word_start;
  if (out_text_bytes) *out_text_bytes = text_bytes;
  if (out_text_rows) *out_text_rows = text_rows;
  return !orphan_text && !invalid_word_start;
}

FUNCTION B32
eightvo_page_repeat_modifier_key(WPARAM key)
{
  return key == VK_CONTROL || key == VK_LCONTROL || key == VK_RCONTROL ||
    key == VK_SHIFT || key == VK_LSHIFT || key == VK_RSHIFT ||
    key == VK_MENU || key == VK_LMENU || key == VK_RMENU;
}

FUNCTION B32
eightvo_page_repeat_consume_cancelled_keydown(EightvoApp *app,
                                                WPARAM key,
                                                LPARAM l_param)
{
  if (!app || app->page_repeat_cancelled_key != key ||
      (l_param & (1LL << 30)) == 0)
  {
    return 0;
  }
  if (app->page_repeat_cancelled_repeat_consumed_count < UINT32_MAX)
    app->page_repeat_cancelled_repeat_consumed_count += 1;
  return 1;
}

FUNCTION void
eightvo_page_action_cancel_presentation_retry(EightvoApp *app)
{
  if (!app) return;
  if (app->window)
    (void)KillTimer(app->window, EightvoPresentationRetryTimerId);
  app->page_action_presentation_retry_attempt = 0;
}

FUNCTION void
eightvo_page_action_schedule_presentation_retry(EightvoApp *app)
{
  if (!app || !app->window || !app->page_action_waiting_for_present) return;
  U32 shift = MIN(app->page_action_presentation_retry_attempt, 4u);
  U32 delay_ms = EightvoPresentationRetryBaseDelayMs << shift;
  delay_ms = MIN(delay_ms, (U32)EightvoPresentationRetryMaxDelayMs);
  (void)KillTimer(app->window, EightvoPresentationRetryTimerId);
  if (SetTimer(app->window,
               EightvoPresentationRetryTimerId,
               delay_ms,
               0) != 0)
  {
    if (app->page_action_presentation_retry_attempt < UINT32_MAX)
      app->page_action_presentation_retry_attempt += 1;
    if (app->page_action_presentation_retry_scheduled_count < UINT32_MAX)
      app->page_action_presentation_retry_scheduled_count += 1;
  }
  else
  {
    /* Match the persistence timer's failure policy: never strand an accepted
       page action merely because Win32 could not allocate a timer. */
    (void)InvalidateRect(app->window, 0, FALSE);
  }
}

FUNCTION void
eightvo_page_action_note_emitted(EightvoApp *app)
{
  if (!app) return;
  if (app->page_action_waiting_for_present)
  {
    if (app->page_action_overlap_count < UINT32_MAX)
      app->page_action_overlap_count += 1;
    return;
  }
  eightvo_page_action_cancel_presentation_retry(app);
  app->page_action_frame_generation += 1;
  if (app->page_action_frame_generation == 0)
    app->page_action_frame_generation = 1;
  MemoryZeroStruct(&app->page_action_expected_identity);
  (void)eightvo_capture_presentation_identity(
    app, &app->page_action_expected_identity);
  if (app->page_action_expected_identity.kind ==
        EightvoPresentationIdentity_Page &&
      app->capture_frame_failed_since_mutation)
  {
    app->page_action_expected_identity.frame_capture_generation =
      app->frame_capture_generation + 1;
    if (app->page_action_expected_identity.frame_capture_generation == 0)
      app->page_action_expected_identity.frame_capture_generation = UINT64_MAX;
    app->page_action_expected_identity.reader_frame_generation = 0;
    app->page_action_expected_identity.image_count = 0;
    app->page_action_expected_identity.image_visual_identity = 0;
  }
  app->page_action_waiting_for_present = 1;
  if (app->page_action_emitted_count < UINT64_MAX)
    app->page_action_emitted_count += 1;
  if (app->window && app->state_save_pending)
    (void)KillTimer(app->window, EightvoStateSaveTimerId);
  eightvo_cancel_adjacent_warm(app);
}

FUNCTION void
eightvo_page_action_clear_pending(EightvoApp *app)
{
  if (!app) return;
  app->page_action_pending = 0;
  app->page_action_pending_arm_repeat = 0;
  app->page_action_pending_direction = 0;
  app->page_action_pending_key = 0;
}

FUNCTION void
eightvo_page_action_defer(EightvoApp *app,
                           WPARAM key,
                           S32 direction,
                           B32 arm_repeat)
{
  if (!app || direction == 0) return;
  app->page_action_pending = 1;
  app->page_action_pending_arm_repeat = arm_repeat;
  app->page_action_pending_direction = direction < 0 ? -1 : 1;
  app->page_action_pending_key = key;
  if (app->page_repeat_active)
  {
    WPARAM cancelled_key = app->page_repeat_key;
    eightvo_stop_page_repeat(app);
    app->page_repeat_cancelled_key = cancelled_key;
  }
}

FUNCTION void
eightvo_page_action_release_key(EightvoApp *app, WPARAM key)
{
  if (!app) return;
  if (app->page_action_pending && key == app->page_action_pending_key)
    app->page_action_pending_arm_repeat = 0;
  if (app->page_repeat_active && key == app->page_repeat_key)
  {
    if (app->page_repeat_keyup_cancel_count < UINT32_MAX)
      app->page_repeat_keyup_cancel_count += 1;
    eightvo_stop_page_repeat(app);
  }
  if (key == app->page_repeat_cancelled_key)
    app->page_repeat_cancelled_key = 0;
}

FUNCTION void
eightvo_stop_page_repeat(EightvoApp *app)
{
  if (!app) return;
  B32 was_active = app->page_repeat_active;
  app->page_repeat_active = 0;
  app->page_repeat_direction = 0;
  app->page_repeat_key = 0;
  app->page_repeat_next_move_ticks = 0;
  app->page_repeat_last_action_emitted_ticks = 0;
  app->page_repeat_navigation_prepare_pending = 0;
  app->page_repeat_navigation_prepare_direction = 0;
  app->page_repeat_navigation_prepare_source_spine_index = 0;
  app->page_repeat_navigation_prepare_source_first_byte = 0;
  if (was_active && app->state_save_pending &&
      !app->page_action_waiting_for_present && !app->page_action_pending)
  {
    if (app->page_repeat_persistence_rescheduled_count < UINT32_MAX)
      app->page_repeat_persistence_rescheduled_count += 1;
    eightvo_schedule_state_save(app);
  }
  if (was_active && !app->page_action_waiting_for_present &&
      !app->page_action_pending && epub_reader_is_open(&app->reader))
    eightvo_schedule_adjacent_warm(app);
}

FUNCTION void
eightvo_cancel_page_repeat(EightvoApp *app, U32 *counter)
{
  if (!app) return;
  eightvo_page_action_clear_pending(app);
  if (!app->page_repeat_active) return;
  WPARAM cancelled_key = app->page_repeat_key;
  eightvo_stop_page_repeat(app);
  app->page_repeat_cancelled_key = cancelled_key;
  if (counter && *counter < UINT32_MAX) *counter += 1;
}

FUNCTION void
eightvo_cancel_page_repeat_for_modifier(EightvoApp *app)
{
  if (!app) return;
  eightvo_cancel_page_repeat(app, &app->page_repeat_modifier_cancel_count);
}

FUNCTION void
eightvo_cancel_page_repeat_for_focus(EightvoApp *app)
{
  if (!app) return;
  eightvo_cancel_page_repeat(app, &app->page_repeat_focus_cancel_count);
}

FUNCTION void
eightvo_cancel_page_repeat_for_deactivation(EightvoApp *app)
{
  if (!app) return;
  eightvo_cancel_page_repeat(app,
                              &app->page_repeat_deactivate_cancel_count);
}

FUNCTION void
eightvo_cancel_page_repeat_for_mutation(EightvoApp *app)
{
  if (!app) return;
  eightvo_cancel_page_repeat(app, &app->page_repeat_mutation_cancel_count);
}

FUNCTION U64
eightvo_page_repeat_delay_ticks(U32 frame_count)
{
  U64 frequency = os_time_frequency();
  U64 whole_ticks = frequency / EightvoPageRepeatFrameRate;
  U64 remainder_ticks = frequency % EightvoPageRepeatFrameRate;
  U64 delay_ticks = whole_ticks * frame_count +
    (remainder_ticks * frame_count + EightvoPageRepeatFrameRate - 1) /
      EightvoPageRepeatFrameRate;
  return delay_ticks ? delay_ticks : 1;
}

FUNCTION void
eightvo_begin_page_repeat(EightvoApp *app,
                           WPARAM key,
                           S32 direction)
{
  if (!app || direction == 0 || !app->page_action_waiting_for_present) return;
  U64 now_ticks = os_time_ticks();
  U64 initial_delay_ticks =
    eightvo_page_repeat_delay_ticks(EightvoPageRepeatInitialFrames);
  app->page_repeat_active = 1;
  app->page_repeat_direction = direction < 0 ? -1 : 1;
  app->page_repeat_key = key;
  app->page_repeat_next_move_ticks =
    now_ticks > UINT64_MAX - initial_delay_ticks ?
      UINT64_MAX : now_ticks + initial_delay_ticks;
  app->page_repeat_last_action_emitted_ticks = 0;
  app->page_repeat_frame_move_count = 0;
  app->page_repeat_native_coalesced_count = 0;
  app->page_repeat_presented_frame_count = 0;
  app->page_repeat_navigation_prepare_pending = 0;
  app->page_repeat_navigation_prepare_direction = 0;
  app->page_repeat_navigation_prepare_source_spine_index = 0;
  app->page_repeat_navigation_prepare_source_first_byte = 0;
  app->page_repeat_navigation_prepare_call_count = 0;
  app->page_repeat_navigation_prepare_build_count = 0;
  app->page_repeat_navigation_prepare_ready_count = 0;
  app->page_repeat_navigation_prepare_cross_spine_ready_count = 0;
  app->page_repeat_navigation_prepare_fail_count = 0;
  app->page_repeat_navigation_prepare_total_ticks = 0;
  app->page_repeat_navigation_prepare_max_ticks = 0;
  app->page_repeat_cancelled_repeat_consumed_count = 0;
  app->page_repeat_modifier_cancel_count = 0;
  app->page_repeat_focus_cancel_count = 0;
  app->page_repeat_deactivate_cancel_count = 0;
  app->page_repeat_keyup_cancel_count = 0;
  app->page_repeat_mutation_cancel_count = 0;
  app->page_repeat_persistence_deferred_count = 0;
  app->page_repeat_persistence_rescheduled_count = 0;
  /* The accepted immediate action already owns the independent presentation
     gate. Releasing or cancelling repeat must not clear that action gate. */
  if (app->window && app->state_save_pending)
    (void)KillTimer(app->window, EightvoStateSaveTimerId);
  eightvo_cancel_adjacent_warm(app);
}

FUNCTION B32
eightvo_page_repeat_prepare_navigation_tail(EightvoApp *app)
{
  if (!app || !app->page_repeat_navigation_prepare_pending) return 0;

  S32 expected_direction = app->page_repeat_navigation_prepare_direction;
  U32 expected_spine =
    app->page_repeat_navigation_prepare_source_spine_index;
  U64 expected_first_byte =
    app->page_repeat_navigation_prepare_source_first_byte;
  app->page_repeat_navigation_prepare_pending = 0;

  if (!app->page_repeat_active || app->page_action_waiting_for_present ||
      app->page_action_pending || expected_direction == 0 ||
      expected_direction != app->page_repeat_direction ||
      !epub_reader_is_open(&app->reader) || !app->reader.has_current_page ||
      app->reader.current_page.spine_index != expected_spine ||
      app->reader.current_page.first_byte != expected_first_byte ||
      !epub_reader_layout_keys_match(app->reader.layout_key, app->layout_key))
  {
    if (app->page_repeat_navigation_prepare_fail_count < UINT64_MAX)
      app->page_repeat_navigation_prepare_fail_count += 1;
    return 0;
  }

  SourceReaderPageRange before_page = app->reader.current_page;
  U64 build_before = app->reader.navigation_stats.prepared_window_build_count;
  SourceReaderLayoutConfig prepare_config = app->layout_config;
  prepare_config.focused_spine_index = app->reader.active_spine_index;
  prepare_config.measure_generation =
    epub_reader_layout_key_generation(app->layout_key);
  prepare_config.measure_cache =
    epub_reader_measure_cache_for_key(&app->reader, app->layout_key);
  U64 prepare_start = os_time_ticks();
  EpubReaderNavigationPrepareResult prepare_result = {0};
  B32 prepared = epub_reader_prepare_navigation(
    &app->reader,
    app->layout_key,
    prepare_config,
    (EpubReaderNavigationPrepareOptions){ .require_page_move = 1 },
    &prepare_result);
  U64 prepare_ticks = os_time_ticks() - prepare_start;

  if (app->page_repeat_navigation_prepare_call_count < UINT64_MAX)
    app->page_repeat_navigation_prepare_call_count += 1;
  app->page_repeat_navigation_prepare_total_ticks =
    app->page_repeat_navigation_prepare_total_ticks >
        UINT64_MAX - prepare_ticks ?
      UINT64_MAX :
      app->page_repeat_navigation_prepare_total_ticks + prepare_ticks;
  app->page_repeat_navigation_prepare_max_ticks = MAX(
    app->page_repeat_navigation_prepare_max_ticks, prepare_ticks);
  if (app->reader.navigation_stats.prepared_window_build_count > build_before &&
      app->page_repeat_navigation_prepare_build_count < UINT64_MAX)
  {
    app->page_repeat_navigation_prepare_build_count +=
      app->reader.navigation_stats.prepared_window_build_count - build_before;
  }
  if (prepare_result.kind == EpubReaderNavigationPrepareKind_AlreadyReady)
  {
    if (app->page_repeat_navigation_prepare_ready_count < UINT64_MAX)
      app->page_repeat_navigation_prepare_ready_count += 1;
    if (prepare_result.page.spine_index != before_page.spine_index &&
        app->page_repeat_navigation_prepare_cross_spine_ready_count <
          UINT64_MAX)
    {
      app->page_repeat_navigation_prepare_cross_spine_ready_count += 1;
    }
  }

  B32 page_unchanged = !prepare_result.current_page_refreshed &&
    app->reader.has_current_page &&
    app->reader.current_page.spine_index == before_page.spine_index &&
    app->reader.current_page.first_byte == before_page.first_byte &&
    app->reader.current_page.one_past_last_byte ==
      before_page.one_past_last_byte;
  B32 direction_unchanged = prepare_result.preferred_direction == 0 ||
    prepare_result.preferred_direction == expected_direction;
  B32 prepared_page_nonempty =
    prepare_result.page.first_byte < prepare_result.page.one_past_last_byte;
  B32 prepared_page_same_spine =
    prepare_result.page.spine_index == before_page.spine_index;
  B32 prepared_cross_spine_kind =
    prepare_result.kind == EpubReaderNavigationPrepareKind_AdjacentSpine ||
    prepare_result.kind == EpubReaderNavigationPrepareKind_AlreadyReady;
  B32 prepared_cross_spine_direction = expected_direction > 0 ?
    prepare_result.page.spine_index > before_page.spine_index :
    prepare_result.page.spine_index < before_page.spine_index;
  /* Reader0's public same-spine validator can prove exact byte adjacency from
     the active pagination. A returned cross-spine preparation is instead
     owned by Reader0's bounded prepared ring, so the host verifies its public
     result kind, nonempty range, and document direction without trying to
     re-prove private prepared ownership through the active pagination. */
  B32 adjacent_valid = !prepare_result.prepared ||
    (prepared_page_nonempty &&
     (prepared_page_same_spine ?
        epub_reader_adjacent_page_candidate_is_valid(
          &app->reader, before_page, expected_direction, prepare_result.page) :
        (prepared_cross_spine_kind && prepared_cross_spine_direction)));
  if (!page_unchanged || !direction_unchanged || !adjacent_valid ||
      !prepared || !prepare_result.prepared ||
      prepare_result.kind == EpubReaderNavigationPrepareKind_None)
  {
    if (app->page_repeat_navigation_prepare_fail_count < UINT64_MAX)
      app->page_repeat_navigation_prepare_fail_count += 1;
    return 0;
  }
  return 1;
}

FUNCTION void
eightvo_start_page_repeat(EightvoApp *app,
                           WPARAM key,
                           S32 direction)
{
  if (!app || !app->window || direction == 0) return;
  eightvo_stop_page_repeat(app);
  eightvo_begin_page_repeat(app, key, direction);
}

FUNCTION B32
eightvo_page_repeat_step(EightvoApp *app,
                           U64 now_ticks,
                           B32 *out_action_due)
{
  if (out_action_due) { *out_action_due = 0; }
  if (!app || !app->page_repeat_active ||
      app->page_repeat_direction == 0)
  {
    return 0;
  }
  S32 current_direction = 0;
  if (!eightvo_page_direction_for_key(app,
                                       app->page_repeat_key,
                                       &current_direction) ||
      current_direction != app->page_repeat_direction)
  {
    eightvo_stop_page_repeat(app);
    return 0;
  }

  if (now_ticks < app->page_repeat_next_move_ticks)
  {
    return 0;
  }
  if (out_action_due) { *out_action_due = 1; }
  if (app->page_action_waiting_for_present)
  {
    return 0;
  }

  U64 action_emitted_ticks = os_time_ticks();
  B32 had_page = app->reader.has_current_page;
  EightvoCanonicalPageIdentity before_page =
    eightvo_canonical_page_identity(app->reader.current_page);
  app->page_action_internal_dispatch = 1;
  EpubReaderResult result = eightvo_move_page(
    app, app->page_repeat_direction);
  app->page_action_internal_dispatch = 0;
  B32 reader_changed = app->reader.has_current_page &&
    (!had_page || !eightvo_canonical_page_identity_equal(
      before_page, eightvo_canonical_page_identity(app->reader.current_page)));
  if (result != EpubReaderResult_Ok)
  {
    eightvo_stop_page_repeat(app);
    if (reader_changed)
    {
      eightvo_page_action_note_emitted(app);
      if (app->window) (void)InvalidateRect(app->window, 0, FALSE);
    }
    return reader_changed;
  }
  app->page_repeat_last_action_emitted_ticks = action_emitted_ticks;
  eightvo_page_action_note_emitted(app);
  U64 interval_ticks =
    eightvo_page_repeat_delay_ticks(EightvoPageRepeatIntervalFrames);
  app->page_repeat_next_move_ticks =
    action_emitted_ticks > UINT64_MAX - interval_ticks ?
      UINT64_MAX : action_emitted_ticks + interval_ticks;
  if (app->page_repeat_frame_move_count < UINT32_MAX)
    app->page_repeat_frame_move_count += 1;
  if (app->window) { InvalidateRect(app->window, 0, FALSE); }
  return 1;
}

FUNCTION EightvoPageRepeatFrameResult
eightvo_page_repeat_frame_step(EightvoApp *app, U64 now_ticks)
{
  EightvoPageRepeatFrameResult result = {0};
  result.action_emitted =
    eightvo_page_repeat_step(app, now_ticks, &result.action_due);
  result.action_waiting_for_render =
    result.action_due && !result.action_emitted && app &&
    app->page_action_waiting_for_present;
  return result;
}

FUNCTION void
eightvo_page_repeat_note_presented_frame(EightvoApp *app, B32 complete)
{
  if (!app || !app->page_action_waiting_for_present) return;
  if (!complete)
  {
    eightvo_page_action_schedule_presentation_retry(app);
    return;
  }
  if (!eightvo_presentation_identity_equal(
        app->page_action_expected_identity, app->last_surface_identity))
  {
    if (app->page_action_identity_mismatch_count < UINT32_MAX)
      app->page_action_identity_mismatch_count += 1;
    eightvo_page_action_schedule_presentation_retry(app);
    return;
  }
  eightvo_page_action_cancel_presentation_retry(app);
  app->page_action_waiting_for_present = 0;
  MemoryZeroStruct(&app->page_action_expected_identity);
  app->page_action_last_stable_present_ticks = os_time_ticks();
  if (app->page_action_presented_count < UINT64_MAX)
    app->page_action_presented_count += 1;
  if (app->page_repeat_active &&
      app->page_repeat_presented_frame_count < UINT32_MAX)
  {
    app->page_repeat_presented_frame_count += 1;
  }

  if (app->page_action_pending)
  {
    WPARAM key = app->page_action_pending_key;
    S32 direction = app->page_action_pending_direction;
    B32 arm_repeat = app->page_action_pending_arm_repeat;
    B32 had_page = app->reader.has_current_page;
    EightvoCanonicalPageIdentity before_page =
      eightvo_canonical_page_identity(app->reader.current_page);
    app->page_action_internal_dispatch = 1;
    EpubReaderResult result = eightvo_move_page(app, direction);
    app->page_action_internal_dispatch = 0;
    B32 reader_changed = app->reader.has_current_page &&
      (!had_page || !eightvo_canonical_page_identity_equal(
        before_page,
        eightvo_canonical_page_identity(app->reader.current_page)));
    eightvo_page_action_clear_pending(app);
    if (result == EpubReaderResult_Ok)
    {
      eightvo_page_action_note_emitted(app);
      if (arm_repeat) eightvo_start_page_repeat(app, key, direction);
      if (app->window) (void)InvalidateRect(app->window, 0, FALSE);
      return;
    }
    if (reader_changed)
    {
      eightvo_page_action_note_emitted(app);
      if (app->window) (void)InvalidateRect(app->window, 0, FALSE);
      return;
    }
  }

  if (app->page_action_reflow_deferred)
  {
    app->page_action_reflow_deferred = 0;
    if (app->window) (void)InvalidateRect(app->window, 0, FALSE);
    return;
  }

  if (app->page_repeat_active && app->reader.has_current_page)
  {
    /* Re10 keeps this Reader0 model preparation separate from host frame and
       raster warming. Queue it only after the accepted page is visibly stable,
       then run it as measured tail work outside the page-action frame. */
    app->page_repeat_navigation_prepare_pending = 1;
    app->page_repeat_navigation_prepare_direction =
      app->page_repeat_direction;
    app->page_repeat_navigation_prepare_source_spine_index =
      app->reader.current_page.spine_index;
    app->page_repeat_navigation_prepare_source_first_byte =
      app->reader.current_page.first_byte;
  }

  if (!app->page_repeat_active)
  {
    if (app->state_save_pending)
    {
      if (app->page_repeat_persistence_rescheduled_count < UINT32_MAX)
        app->page_repeat_persistence_rescheduled_count += 1;
      eightvo_schedule_state_save(app);
    }
    if (epub_reader_is_open(&app->reader))
      eightvo_schedule_adjacent_warm(app);
  }
}

FUNCTION EightvoReaderKeyRoute
eightvo_reader_view_route_keydown_ex(EightvoApp *app,
                                      WPARAM key,
                                      B32 shift,
                                      B32 *out_page_move_succeeded)
{
  if (out_page_move_succeeded) *out_page_move_succeeded = 0;
  if (!app) return EightvoReaderKeyRoute_None;
  B32 editing = eightvo_reader_view_text_editing(app);
  if (editing && key == VK_BACK)
    app->input.backspace_pressed = 1;
  else if (editing && key == VK_DELETE)
    app->input.delete_pressed = 1;
  else if (editing && key == VK_RETURN)
  {
    if (app->reader_view_state.popup == ReaderViewPopup_NoteEditor)
      eightvo_append_input_wchar(app, L'\n');
    else
      app->input.commit_pressed = 1;
  }
  else if (editing && (key == VK_LEFT || key == VK_RIGHT))
  {
    app->input.move_delta = key == VK_LEFT ? -1 : 1;
    app->input.extend_selection = shift;
  }
  else if (editing && (key == VK_UP || key == VK_DOWN))
  {
    app->input.move_vertical_delta = key == VK_UP ? -1 : 1;
    app->input.extend_selection = shift;
  }
  else if (!editing &&
           eightvo_reader_view_horizontal_move_is_shared(app) &&
           (key == VK_LEFT || key == VK_RIGHT))
  {
    app->input.move_delta = key == VK_LEFT ? -1 : 1;
  }
  else if (!editing && app->reader_view_state.focus_id != 0 &&
           (key == VK_LEFT || key == VK_RIGHT))
  {
    EpubReaderResult move =
      eightvo_move_page(app, key == VK_LEFT ? -1 : 1);
    if (out_page_move_succeeded && move == EpubReaderResult_Ok)
      *out_page_move_succeeded = 1;
  }
  else if (!editing && app->reader_view_state.focus_id != 0 &&
           (key == VK_UP || key == VK_DOWN))
  {
    app->input.move_vertical_delta = key == VK_UP ? -1 : 1;
  }
  else if (app->reader_view_state.focus_id != 0 &&
           (key == VK_PRIOR || key == VK_NEXT))
  {
    app->input.range_move = key == VK_PRIOR ?
      ReaderViewRangeMove_PreviousPage : ReaderViewRangeMove_NextPage;
  }
  else if (app->reader_view_state.focus_id != 0 &&
           (key == VK_HOME || key == VK_END))
  {
    app->input.range_move = key == VK_HOME ?
      ReaderViewRangeMove_First : ReaderViewRangeMove_Last;
  }
  else if (!editing &&
           app->host_focus_control == EightvoHostControl_ExitReader &&
           (key == VK_RETURN || key == VK_SPACE))
  {
    return eightvo_host_keyboard_activate(app) ?
      EightvoReaderKeyRoute_CloseRequested :
      EightvoReaderKeyRoute_Handled;
  }
  else if (!editing && app->reader_view_state.focus_id == 0 &&
           app->host_focus_control == EightvoHostControl_None &&
           (key == VK_LEFT || key == VK_PRIOR))
  {
    EpubReaderResult move = eightvo_move_page(app, -1);
    if (out_page_move_succeeded && move == EpubReaderResult_Ok)
      *out_page_move_succeeded = 1;
  }
  else if (key == VK_SPACE &&
           eightvo_reader_view_space_activates_focus(app))
  {
    app->input.activate_pressed = 1;
  }
  else if (!editing && app->reader_view_state.focus_id == 0 &&
           app->host_focus_control == EightvoHostControl_None &&
           (key == VK_RIGHT || key == VK_NEXT || key == VK_SPACE))
  {
    EpubReaderResult move = eightvo_move_page(app, 1);
    if (out_page_move_succeeded && move == EpubReaderResult_Ok)
      *out_page_move_succeeded = 1;
  }
  else if (key == VK_TAB)
  {
    if (!eightvo_host_keyboard_tab(app, shift))
    {
      if (shift) app->input.focus_prev_pressed = 1;
      else app->input.focus_next_pressed = 1;
    }
  }
  else if (!editing && app->reader_view_state.focus_id != 0 &&
           key == VK_RETURN)
  {
    app->input.activate_pressed = 1;
  }
  else
  {
    return EightvoReaderKeyRoute_None;
  }
  return EightvoReaderKeyRoute_Handled;
}

FUNCTION EightvoReaderKeyRoute
eightvo_reader_view_route_keydown(EightvoApp *app,
                                   WPARAM key,
                                   B32 shift)
{
  return eightvo_reader_view_route_keydown_ex(app, key, shift, 0);
}

FUNCTION B32
eightvo_launch_lookup(EightvoApp *app,
                       const char *prefix,
                       ReaderViewText text)
{
  if (!app || !prefix || text.size <= 0 || !text.data) return 0;
  char url[EightvoUrlCap] = {0};
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
eightvo_export_annotations(EightvoApp *app)
{
  if (!app || !app->export_path[0]) return 0;
  char data[65536] = {0};
  U64 size = cstr_format(data, ARRAY_COUNT(data),
                         "eightvo reader annotations\nBook: %s\n\n",
                         app->document_title[0] ? app->document_title : app->current_path);
  for (U32 index = 0; index < app->bookmark_count && size < ARRAY_COUNT(data); index += 1)
  {
    const EightvoBookmark *item = app->bookmarks + index;
    size += cstr_format(data + size, ARRAY_COUNT(data) - size,
                        "Bookmark %llu | %s | spine %u byte %llu%s\n",
                        (unsigned long long)item->id, item->label,
                        item->spine_index, (unsigned long long)item->byte_offset,
                        item->starred ? " | starred" : "");
  }
  for (U32 index = 0; index < app->highlight_count && size < ARRAY_COUNT(data); index += 1)
  {
    const EightvoHighlight *item = app->highlights + index;
    if (item->is_highlight)
      size += cstr_format(data + size, ARRAY_COUNT(data) - size,
                          "Highlight %llu | %s | %s\n",
                          (unsigned long long)item->id,
                          item->section, item->text);
    if (item->note[0] && size < ARRAY_COUNT(data))
      size += cstr_format(data + size, ARRAY_COUNT(data) - size,
                          "Note %llu | %s | %s\n",
                          (unsigned long long)item->id,
                          item->section, item->note);
  }
  B32 result = size < ARRAY_COUNT(data) &&
    os_write_entire_file_atomic(app->export_path, data, size);
  eightvo_set_statusf(app, result ? "Annotations exported: %s" :
                                     "Annotation export failed",
                       result ? app->export_path : "");
  return result;
}

FUNCTION B32
eightvo_set_fullscreen(EightvoApp *app, B32 active)
{
  if (!app || !app->window || app->fullscreen.active == active) return app != 0;
  if (!eightvo_begin_document_mutation(app)) return 0;
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
  eightvo_complete_document_mutation(app, 1);
  return 1;
}

FUNCTION B32
eightvo_apply_setting(EightvoApp *app,
                       ReaderViewSettingKind kind,
                       ReaderViewKey key)
{
  if (!app) return 0;
  if (!eightvo_begin_document_mutation(app)) return 0;
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
      app->font_family_user_override = 1;
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
      if (key < 4000 || key >= 4000 + EightvoTheme_Count) return 0;
      app->theme = (EightvoTheme)(key - 4000);
      break;
    default: return 0;
  }
  (void)eightvo_save_settings(app);
  if (repaginate && epub_reader_is_open(&app->reader))
  {
    app->page_action_internal_dispatch = 1;
    B32 result = eightvo_repaginate(app);
    app->page_action_internal_dispatch = 0;
    eightvo_complete_document_mutation(app, 1);
    return result;
  }
  eightvo_complete_document_mutation(app, 1);
  return 1;
}

FUNCTION void
eightvo_select_highlight(EightvoApp *app, const EightvoHighlight *highlight)
{
  if (!app || !highlight) return;
  DocSelection selection = {
    .spine_index = highlight->spine_index,
    .text_byte_start = highlight->start_byte,
    .text_byte_end = highlight->end_byte,
  };
  (void)epub_reader_set_selection(&app->reader, selection);
}

FUNCTION B32
eightvo_reader_view_open_annotation_note(EightvoApp *app,
                                           const EightvoHighlight *highlight)
{
  if (!app || !highlight || highlight->id == 0 ||
      highlight->end_byte <= highlight->start_byte ||
      highlight->note[0] == 0)
  {
    return 0;
  }
  U64 values[4] = {
    (U64)app->frame.document_id,
    highlight->start_byte,
    highlight->end_byte,
    highlight->id,
  };
  ReaderViewSelectionProjection selection = {
    .status = eightvo_reader_view_status(ReaderViewLoad_Ready, 0),
    .selection_key = u64_hash_bytes(values, sizeof(values)),
    .revision = app->annotation_revision,
    .selected_text = eightvo_reader_view_text(highlight->text),
    .note_text = eightvo_reader_view_text(highlight->note),
    .flags = ReaderViewSelection_Active |
             ReaderViewSelection_CanEditNote |
             ReaderViewSelection_CanDeleteNote,
  };
  if (selection.selection_key == 0) selection.selection_key = 1;
  if (!reader_view_open_note_editor(&app->reader_view_state, &selection))
    return 0;

  app->annotation_note_selection_key = selection.selection_key;
  app->annotation_note_document_id = app->frame.document_id;
  app->annotation_note_highlight_id = highlight->id;
  app->annotation_note_start_byte = highlight->start_byte;
  app->annotation_note_end_byte = highlight->end_byte;
  app->annotation_note_spine_index = highlight->spine_index;
  /* Retain this host record without selecting or navigating the document. */
  return app->reader_view_state.note_selection_key == selection.selection_key;
}

FUNCTION S32
eightvo_reader_view_note_editor_highlight_index(const EightvoApp *app)
{
  if (!app) return -1;
  if (app->annotation_note_selection_key != 0)
  {
    if (app->reader_view_state.popup != ReaderViewPopup_NoteEditor ||
        app->annotation_note_selection_key !=
          app->reader_view_state.note_selection_key ||
        app->annotation_note_document_id != app->frame.document_id)
    {
      return -1;
    }
    S32 index = eightvo_highlight_index(
      app, app->annotation_note_highlight_id);
    if (index < 0) return -1;
    const EightvoHighlight *highlight = app->highlights + index;
    if (highlight->spine_index != app->annotation_note_spine_index ||
        highlight->start_byte != app->annotation_note_start_byte ||
        highlight->end_byte != app->annotation_note_end_byte)
    {
      return -1;
    }
    return index;
  }
  return eightvo_selection_highlight_index(app);
}

FUNCTION void
eightvo_reader_view_finish_note_editor(EightvoApp *app)
{
  if (!app) return;
  B32 annotation_origin = app->annotation_note_selection_key != 0;
  eightvo_reader_view_clear_annotation_note_target(app);
  if (!annotation_origin)
  {
    epub_reader_clear_selection(&app->reader);
    app->selected_text[0] = 0;
    app->selection_anchor_rect = (UI0Rect){0};
  }
  (void)eightvo_capture_frame(app);
}

FUNCTION B32
eightvo_reader_view_set_find_query(EightvoApp *app, ReaderViewText query)
{
  if (!app || query.size < 0 || (query.size > 0 && !query.data) ||
      (U64)query.size >= ARRAY_COUNT(app->reader_view_state.find_query))
    return 0;
  if (query.size > 0 && query.data != app->reader_view_state.find_query)
    MemoryCopy(app->reader_view_state.find_query,
               query.data,
               (U64)query.size);
  app->reader_view_state.find_query[query.size] = 0;
  app->reader_view_state.find_query_length = query.size;
  app->reader_view_state.find_input.caret =
    MIN(app->reader_view_state.find_input.caret, query.size);
  app->reader_view_state.find_input.selection_anchor =
    MIN(app->reader_view_state.find_input.selection_anchor, query.size);
  return 1;
}

FUNCTION void
eightvo_apply_reader_view_action(EightvoApp *app,
                                  const ReaderViewAction *action)
{
  if (!app || !action) return;
  switch (action->kind)
  {
    case ReaderViewAction_Open: (void)eightvo_pick_epub(app); break;
    case ReaderViewAction_PreviousPage: (void)eightvo_move_page(app, -1); break;
    case ReaderViewAction_NextPage: (void)eightvo_move_page(app, 1); break;
    case ReaderViewAction_HistoryBack: (void)eightvo_move_history(app, 0); break;
    case ReaderViewAction_HistoryForward: (void)eightvo_move_history(app, 1); break;
    case ReaderViewAction_SeekLocation: (void)eightvo_seek_location(app, action->value); break;
    case ReaderViewAction_SelectSetting:
      (void)eightvo_apply_setting(app, action->setting_kind, action->key);
      break;
    case ReaderViewAction_ToggleBookmark:
      (void)eightvo_toggle_current_bookmark(app);
      break;
    case ReaderViewAction_FindChanged:
    {
      if (!eightvo_begin_document_mutation(app)) break;
      if (!eightvo_reader_view_set_find_query(app, action->text)) break;
      app->page_action_internal_dispatch = 1;
      if (action->text.size == 0)
      {
        epub_reader_clear_search(&app->reader);
        (void)eightvo_capture_frame(app);
      }
      else
      {
        (void)eightvo_capture_frame(app);
      }
      app->page_action_internal_dispatch = 0;
      eightvo_complete_document_mutation(app, 1);
    } break;
    case ReaderViewAction_FindCommitted:
    {
      if (!eightvo_begin_document_mutation(app)) break;
      if (!eightvo_reader_view_set_find_query(app, action->text)) break;
      app->page_action_internal_dispatch = 1;
      if (action->text.size == 0)
      {
        epub_reader_clear_search(&app->reader);
        (void)eightvo_capture_frame(app);
      }
      else if (epub_reader_rebuild_search(
                 &app->reader,
                 str8((U8 *)action->text.data, (U64)action->text.size)))
      {
        if (app->reader.search_match_count > 0)
          (void)eightvo_navigate_to_search_match(
            app,
            app->reader.search_has_active ? app->reader.search_active_index : 0,
            &(EpubReaderSearchNavigationResult){0});
        else
          (void)eightvo_capture_frame(app);
      }
      else
      {
        (void)eightvo_capture_frame(app);
      }
      app->page_action_internal_dispatch = 0;
      eightvo_complete_document_mutation(app, 1);
    } break;
    case ReaderViewAction_FindPrevious:
    case ReaderViewAction_FindNext:
    {
      if (!eightvo_begin_document_mutation(app)) break;
      app->page_action_internal_dispatch = 1;
      if (epub_reader_search_step(&app->reader,
            action->kind == ReaderViewAction_FindPrevious ? -1 : 1))
        (void)eightvo_navigate_to_search_match(
          app, app->reader.search_active_index,
          &(EpubReaderSearchNavigationResult){0});
      else
        (void)eightvo_capture_frame(app);
      app->page_action_internal_dispatch = 0;
      eightvo_complete_document_mutation(app, 1);
    } break;
    case ReaderViewAction_ActivateTocRow:
      if (action->key > 0 && action->key - 1 <= 0xffffffffull)
        (void)eightvo_navigate_to_nav_point(
          app, (U32)(action->key - 1), &(EpubReaderNavPointResult){0});
      break;
    case ReaderViewAction_ActivateFindRow:
      if (action->key >= 0x100000ull &&
          action->key - 0x100000ull <= 0xffffffffull)
        (void)eightvo_navigate_to_search_match(
          app, (U32)(action->key - 0x100000ull),
          &(EpubReaderSearchNavigationResult){0});
      break;
    case ReaderViewAction_ActivateRightRow:
    {
      const EightvoReaderViewRightSource *source =
        eightvo_reader_view_right_source(
          app, action->key, action->right_row_kind);
      S32 bookmark = source && source->row_kind == ReaderViewRightRow_Bookmark ?
        eightvo_bookmark_index(app, source->record_id) : -1;
      S32 highlight = source && source->row_kind != ReaderViewRightRow_Bookmark ?
        eightvo_highlight_index(app, source->record_id) : -1;
      if (bookmark >= 0)
        (void)eightvo_navigate_to_location(app,
          app->bookmarks[bookmark].spine_index,
          app->bookmarks[bookmark].byte_offset,
          EpubReaderNavigationReason_Bookmark);
      else if (highlight >= 0)
        (void)eightvo_navigate_to_location(app,
          app->highlights[highlight].spine_index,
          app->highlights[highlight].start_byte,
          EpubReaderNavigationReason_Annotation);
    } break;
    case ReaderViewAction_ToggleRightRowStar:
    {
      const EightvoReaderViewRightSource *source =
        eightvo_reader_view_right_source(
          app, action->key, action->right_row_kind);
      S32 bookmark = source && source->row_kind == ReaderViewRightRow_Bookmark ?
        eightvo_bookmark_index(app, source->record_id) : -1;
      S32 highlight = source && source->row_kind != ReaderViewRightRow_Bookmark ?
        eightvo_highlight_index(app, source->record_id) : -1;
      if (bookmark >= 0)
        (void)eightvo_remove_bookmark_at(app, (U32)bookmark);
      else if (highlight >= 0 && source->row_kind == ReaderViewRightRow_Note)
        (void)eightvo_toggle_highlight_star_at(
          app, (U32)highlight, 1);
      else if (highlight >= 0)
        (void)eightvo_toggle_highlight_star_at(
          app, (U32)highlight, 0);
    } break;
    case ReaderViewAction_EditRightRowNote:
    {
      const EightvoReaderViewRightSource *source =
        eightvo_reader_view_right_source(
          app, action->key, action->right_row_kind);
      S32 highlight = source && source->row_kind == ReaderViewRightRow_Note ?
        eightvo_highlight_index(app, source->record_id) : -1;
      if (highlight >= 0)
        (void)eightvo_reader_view_open_annotation_note(
          app, app->highlights + highlight);
    } break;
    case ReaderViewAction_DeleteRightRow:
    {
      const EightvoReaderViewRightSource *source =
        eightvo_reader_view_right_source(
          app, action->key, action->right_row_kind);
      S32 bookmark = source && source->row_kind == ReaderViewRightRow_Bookmark ?
        eightvo_bookmark_index(app, source->record_id) : -1;
      S32 highlight = source && source->row_kind != ReaderViewRightRow_Bookmark ?
        eightvo_highlight_index(app, source->record_id) : -1;
      if (bookmark >= 0)
        (void)eightvo_remove_bookmark_at(app, (U32)bookmark);
      else if (highlight >= 0 && source->row_kind == ReaderViewRightRow_Note)
        (void)eightvo_delete_note_at_index(app, (U32)highlight);
      else if (highlight >= 0)
        (void)eightvo_remove_highlight_identity_at(app, (U32)highlight);
    } break;
    case ReaderViewAction_ExportRightRows: (void)eightvo_export_annotations(app); break;
    case ReaderViewAction_SetHighlightColor:
      if (action->auxiliary_key >= 5000)
        (void)eightvo_set_highlight_color(app,
                                           (U32)(action->auxiliary_key - 5000));
      break;
    case ReaderViewAction_RemoveHighlight:
    {
      S32 index = eightvo_selection_highlight_index(app);
      if (index >= 0)
        (void)eightvo_remove_highlight_identity_at(app, (U32)index);
    } break;
    case ReaderViewAction_CopySelection:
      (void)eightvo_set_clipboard_text(app, action->text);
      break;
    case ReaderViewAction_DictionarySelection:
      (void)eightvo_launch_lookup(app,
        "https://www.google.com/search?q=define%3A", action->text);
      break;
    case ReaderViewAction_WebLookupSelection:
      (void)eightvo_launch_lookup(app,
        "https://www.google.com/search?q=", action->text);
      break;
    case ReaderViewAction_TranslateSelection:
      (void)eightvo_launch_lookup(app,
        "https://translate.google.com/?sl=auto&tl=en&text=", action->text);
      break;
    case ReaderViewAction_SaveNote:
      if (app->reader_view_state.popup == ReaderViewPopup_NoteEditor &&
          action->key == app->reader_view_state.note_selection_key &&
          action->value == app->reader_view_state.note_source_revision &&
          action->value == app->annotation_revision)
      {
        S32 index = eightvo_reader_view_note_editor_highlight_index(app);
        B32 saved = index >= 0 ?
          eightvo_save_note_at_index(app, (U32)index, action->text) :
          (app->annotation_note_selection_key == 0 &&
           eightvo_save_selection_note(app, action->text));
        if (saved &&
            reader_view_close_note_editor(&app->reader_view_state))
        {
          eightvo_reader_view_finish_note_editor(app);
        }
      }
      break;
    case ReaderViewAction_DeleteNote:
    {
      S32 index = eightvo_reader_view_note_editor_highlight_index(app);
      if (app->reader_view_state.popup == ReaderViewPopup_NoteEditor &&
          action->key == app->reader_view_state.note_selection_key &&
          action->value == app->reader_view_state.note_source_revision &&
          action->value == app->annotation_revision && index >= 0)
      {
        if (eightvo_delete_note_at_index(app, (U32)index) &&
            reader_view_close_note_editor(&app->reader_view_state))
          eightvo_reader_view_finish_note_editor(app);
      }
    } break;
    case ReaderViewAction_CancelNote:
      /* Shared Cancel already closes the modal. The host releases the
         annotation target and only a selection-origin editor's selection. */
      if (action->key == app->reader_view_state.note_selection_key &&
          action->value == app->reader_view_state.note_source_revision)
        eightvo_reader_view_finish_note_editor(app);
      break;
    case ReaderViewAction_ToggleFullscreen:
      (void)eightvo_set_fullscreen(app, !app->fullscreen.active);
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
eightvo_apply_reader_view_actions(EightvoApp *app)
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
    eightvo_apply_reader_view_action(app, actions + index);
}

FUNCTION EpubReaderFrameImage *
eightvo_image_for_row(EpubReaderFrame *frame, U32 row)
{
  if (!frame) { return 0; }
  for (U32 index = 0; index < frame->image_count; index += 1)
  {
    if (frame->images[index].row == row) { return frame->images + index; }
  }
  return 0;
}

FUNCTION U64
eightvo_text_chunk_end(String8 text, U64 start)
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

FUNCTION DocTextStyleFlags
eightvo_reader_block_style_flags(const EpubReaderFrameStyleRow *row)
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

FUNCTION DocTextStyleFlags
eightvo_reader_apply_text_style_flags(DocTextStyleFlags base,
                                       DocTextStyleFlags incoming)
{
  DocTextStyleFlags result =
    base | (incoming & ~(DocTextStyleFlag_Underline |
                         DocTextStyleFlag_NoUnderline |
                         DocTextStyleFlag_SmallCaps |
                         DocTextStyleFlag_NormalCaps));
  if (incoming & DocTextStyleFlag_NoUnderline)
  {
    result &= ~DocTextStyleFlag_Underline;
    result |= DocTextStyleFlag_NoUnderline;
  }
  if (incoming & DocTextStyleFlag_Underline)
  {
    result &= ~DocTextStyleFlag_NoUnderline;
    result |= DocTextStyleFlag_Underline;
  }
  if (incoming & DocTextStyleFlag_NormalCaps)
  {
    result &= ~DocTextStyleFlag_SmallCaps;
    result |= DocTextStyleFlag_NormalCaps;
  }
  if (incoming & DocTextStyleFlag_SmallCaps)
  {
    result &= ~DocTextStyleFlag_NormalCaps;
    result |= DocTextStyleFlag_SmallCaps;
  }
  return result;
}

FUNCTION void
eightvo_reader_segment_style(const EightvoApp *app,
                              const EpubReaderFrameStyleRow *row,
                              U32 row_size,
                              U32 segment_start,
                              U32 *out_segment_end,
                              DocTextStyleFlags *out_flags,
                              U32 *out_font_scale_permille,
                              U32 *out_font_family_hint,
                              U32 *out_font_face_index,
                              U32 *out_text_color_rgb,
                              B32 *out_has_text_color)
{
  U32 segment_end = row_size;
  DocTextStyleFlags flags = eightvo_reader_block_style_flags(row);
  U32 font_scale_permille = 1000;
  U32 font_family_hint = row ? row->font_family_hint : 0;
  U32 font_face_index = row ? row->font_face_index :
    DOC_EMBEDDED_FONT_FACE_INDEX_NONE;
  U32 text_color_rgb = row ? row->text_color_rgb : 0;
  B32 has_text_color = row ? row->has_text_color : 0;

  if (app && row && row->style_fragment_count > 0 &&
      row->first_style_fragment_index < app->frame.style_fragment_count)
  {
    U32 first = row->first_style_fragment_index;
    U32 end = MIN(first + row->style_fragment_count,
                  app->frame.style_fragment_count);
    for (U32 index = first; index < end; index += 1)
    {
      const EpubReaderFrameStyleFragment *fragment =
        app->frame.style_fragments + index;
      if (fragment->row != row->row ||
          fragment->byte_end <= fragment->byte_start)
      {
        continue;
      }
      U32 fragment_start = MIN(fragment->byte_start, row_size);
      U32 fragment_end = MIN(fragment->byte_end, row_size);
      if (fragment_end <= fragment_start) { continue; }

      if (fragment_start <= segment_start && segment_start < fragment_end)
      {
        flags = eightvo_reader_apply_text_style_flags(flags, fragment->flags);
        if (fragment->font_scale_permille != 0)
          font_scale_permille = fragment->font_scale_permille;
        if (fragment->font_family_hint != 0)
          font_family_hint = fragment->font_family_hint;
        if (fragment->font_face_index != DOC_EMBEDDED_FONT_FACE_INDEX_NONE)
          font_face_index = fragment->font_face_index;
        if (fragment->has_text_color)
        {
          text_color_rgb = fragment->text_color_rgb;
          has_text_color = 1;
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
    segment_end = MIN(segment_start + 1, row_size);

  if (out_segment_end) *out_segment_end = segment_end;
  if (out_flags) *out_flags = flags;
  if (out_font_scale_permille) *out_font_scale_permille = font_scale_permille;
  if (out_font_family_hint) *out_font_family_hint = font_family_hint;
  if (out_font_face_index) *out_font_face_index = font_face_index;
  if (out_text_color_rgb) *out_text_color_rgb = text_color_rgb;
  if (out_has_text_color) *out_has_text_color = has_text_color;
}

FUNCTION B32
eightvo_push_reader_text_chunks(EightvoApp *app,
                                 String8 text,
                                 DocTextStyleFlags style_flags,
                                 U32 font_family_hint,
                                 U32 font_face_index,
                                 const TextEngineResolvedStyle *style,
                                 S32 x,
                                 S32 top_y,
                                 S32 scale,
                                 U32 color,
                                 S32 clip_x,
                                 S32 clip_y,
                                 S32 clip_w,
                                 S32 clip_h)
{
  if (!app || !style || !text.str) { return 0; }
  U32 measure_scale_permille = (U32)MAX(
    ((S64)scale * 1000 + MAX(app->layout_key.text_scale, 1) / 2) /
      MAX(app->layout_key.text_scale, 1),
    1);
  FontTag render_tag =
    font_cache_tag_from_provider(&app->render_state.text_cache, style->provider);
  U64 at = 0;
  while (at < text.size)
  {
    U64 end = eightvo_text_chunk_end(text, at);
    if (end <= at) { return 0; }
    String8 chunk = str8(text.str + at, end - at);
    B32 pushed = draw_push_text_ex_s8(&app->draw_commands,
                                      DrawLayer_World,
                                      chunk,
                                      x,
                                      top_y,
                                      scale,
                                      color,
                                      DrawTextOrigin_TopLeft,
                                      0,
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
    at = end;
    if (at < text.size)
    {
      x += epub_reader_typography_measure_text(&app->reader.typography,
                                               chunk,
                                               style_flags,
                                               measure_scale_permille,
                                               font_family_hint,
                                               font_face_index);
    }
  }
  return 1;
}

FUNCTION B32
eightvo_fit_image_rect(S32 src_w,
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

typedef struct EightvoPresentationRowMetrics
{
  S32 scale_px;
  S32 line_height_px;
  S32 margin_before_px;
  S32 margin_after_px;
  S32 content_left_px;
  S32 content_right_px;
} EightvoPresentationRowMetrics;

typedef struct EightvoPresentationImageBox
{
  S32 x_offset_px;
  S32 width_px;
  S32 height_px;
} EightvoPresentationImageBox;

FUNCTION B32
eightvo_resolve_scaled_px(S32 base_px,
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
eightvo_resolve_nonnegative_product(S32 value,
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
eightvo_resolve_presentation_row_metrics(const EightvoApp *app,
                                          const EpubReaderFrameStyleRow *row,
                                          EightvoPresentationRowMetrics *out_metrics)
{
  if (out_metrics) { MemoryZeroStruct(out_metrics); }
  if (!app || !row || !out_metrics || app->layout_key.char_advance <= 0 ||
      app->reader_margin_line_height <= 0)
  {
    return 0;
  }

  EightvoPresentationRowMetrics metrics = {0};
  if (row->block_kind == DocTextBlockKind_Heading &&
      (row->font_scale_permille == 0 || row->font_scale_permille == 1000))
  {
    metrics.scale_px = app->layout_key.text_scale +
      (row->heading_level <= 1 ? 4 : 2);
  }
  else if (!eightvo_resolve_scaled_px(app->layout_key.text_scale,
                                       row->font_scale_permille,
                                       12,
                                       &metrics.scale_px))
  {
    return 0;
  }
  if (metrics.scale_px > INT32_MAX - 4 ||
      !eightvo_resolve_scaled_px(app->layout_key.line_height,
                                  row->line_height_permille ?
                                    row->line_height_permille : 1000,
                                  metrics.scale_px + 4,
                                  &metrics.line_height_px) ||
      !eightvo_resolve_nonnegative_product(row->margin_top_rows,
                                            app->reader_margin_line_height,
                                            &metrics.margin_before_px) ||
      !eightvo_resolve_nonnegative_product(row->margin_bottom_rows,
                                            app->reader_margin_line_height,
                                            &metrics.margin_after_px) ||
      !eightvo_resolve_nonnegative_product(row->margin_left_cols,
                                            app->layout_key.char_advance,
                                            &metrics.content_left_px) ||
      !eightvo_resolve_nonnegative_product(row->margin_right_cols,
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
    if (!eightvo_resolve_nonnegative_product(row->text_indent_cols,
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

FUNCTION B32
eightvo_reader_row_has_safe_justification_styles(
  const EightvoApp *app,
  const EpubReaderFrameStyleRow *row)
{
  if (!app || !row) { return 0; }
  DocTextStyleFlags safe = DocTextStyleFlag_Italic |
                           DocTextStyleFlag_Bold |
                           DocTextStyleFlag_Underline |
                           DocTextStyleFlag_SmallCaps |
                           DocTextStyleFlag_NormalCaps |
                           DocTextStyleFlag_NoUnderline;
  if ((eightvo_reader_block_style_flags(row) & ~safe) != 0) { return 0; }
  U32 first = row->first_style_fragment_index;
  if (row->style_fragment_count > 0 &&
      (first >= app->frame.style_fragment_count ||
       row->style_fragment_count > app->frame.style_fragment_count - first))
  {
    return 0;
  }
  U32 end = first + row->style_fragment_count;
  for (U32 index = first; index < end; index += 1)
  {
    const EpubReaderFrameStyleFragment *fragment =
      app->frame.style_fragments + index;
    if (fragment->row == row->row && (fragment->flags & ~safe) != 0)
      return 0;
  }
  return 1;
}

FUNCTION B32
eightvo_reader_row_is_soft_wrapped(const EightvoApp *app,
                                    const EpubReaderFrameStyleRow *row)
{
  if (!app || !row) { return 0; }
  U32 end = MIN(row->byte_end, (U32)app->frame.visible_text.size);
  return ((end < app->frame.visible_text.size &&
           app->frame.visible_text.str[end] == ' ') ||
          (end > row->byte_start && app->frame.visible_text.str[end - 1] == ' '));
}

FUNCTION B32
eightvo_reader_row_allows_justification(const EightvoApp *app,
                                         const EpubReaderFrameStyleRow *row,
                                         U32 row_size)
{
  B32 kind_allows = row && row->text_align == DocTextAlign_Justify &&
    (row->block_kind == DocTextBlockKind_Paragraph ||
     (row->block_kind == DocTextBlockKind_Blockquote &&
      eightvo_reader_row_is_soft_wrapped(app, row)));
  B32 margin_allows = row &&
    (row->margin_left_cols == 0 ||
     (row->block_kind == DocTextBlockKind_Blockquote &&
      row->margin_left_cols <= 8));
  return (kind_allows && !row->block_last_row && row->heading_level == 0 &&
          row_size >= 8 && margin_allows &&
          eightvo_reader_row_has_safe_justification_styles(app, row));
}

FUNCTION S32
eightvo_reader_justification_extra_cap_per_space(U32 space_count,
                                                  S32 natural_space_w)
{
  if (space_count == 0) { return 0; }
  natural_space_w = MAX(natural_space_w, 1);
  S32 ratio_cap = 4;
  S32 absolute_cap = 4;
  if (space_count >= 7)
  {
    ratio_cap = natural_space_w * 2;
    absolute_cap = 12;
  }
  else if (space_count >= 5)
  {
    ratio_cap = natural_space_w * 3;
    absolute_cap = 14;
  }
  else if (space_count >= 3)
  {
    ratio_cap = natural_space_w;
    absolute_cap = 6;
  }
  return MAX(MIN(ratio_cap, absolute_cap), 0);
}

FUNCTION S32
eightvo_reader_row_justification_extra_cap_per_space(
  const EpubReaderFrameStyleRow *row,
  U32 space_count,
  S32 natural_space_w,
  S32 natural_w,
  S32 available_w)
{
  S32 result = eightvo_reader_justification_extra_cap_per_space(
    space_count, natural_space_w);
  if (row && row->block_kind == DocTextBlockKind_Paragraph &&
      row->heading_level == 0 && row->text_align == DocTextAlign_Justify &&
      row->margin_left_cols == 0 && row->text_indent_cols <= 4 &&
      (row->text_indent_cols <= 2 || row->line_row > 0 || space_count <= 4) &&
      space_count >= 3 && natural_w > 0 && available_w > 0 &&
      natural_w * 10 >= available_w * 7)
  {
    S32 ratio_cap = natural_space_w *
      ((row->text_indent_cols > 0 && space_count <= 4) ? 6 : 5);
    S32 absolute_cap = row->text_indent_cols == 0 ? 24 :
      (space_count <= 4 ? 34 : 28);
    result = MAX(result, MIN(ratio_cap, absolute_cap));
  }
  if (row && row->block_kind == DocTextBlockKind_Blockquote)
  {
    S32 ratio_cap = natural_space_w * (space_count <= 4 ? 6 : 5);
    S32 absolute_cap = space_count <= 4 ? 28 : 24;
    result = MAX(result, MIN(ratio_cap, absolute_cap));
  }
  if (row && row->line_row == 0 && row->text_indent_cols > 0 &&
      space_count >= 4)
  {
    result = MAX(result, MIN(natural_space_w * 4, 20));
  }
  B32 low_indent_fill = row &&
    row->block_kind == DocTextBlockKind_Paragraph &&
    row->heading_level == 0 && row->text_align == DocTextAlign_Justify &&
    row->margin_left_cols == 0 && row->text_indent_cols <= 4 &&
    (row->text_indent_cols <= 2 || row->line_row == 0) &&
    space_count >= 5 && natural_w > 0 && available_w > natural_w &&
    natural_w * 20 >= available_w * 13;
  B32 indented_fill = row &&
    row->block_kind == DocTextBlockKind_Paragraph &&
    row->heading_level == 0 && row->text_align == DocTextAlign_Justify &&
    row->margin_left_cols == 0 && row->text_indent_cols > 4 &&
    row->text_indent_cols <= 8 && row->line_row > 0 && space_count >= 5 &&
    natural_w > 0 && available_w > natural_w &&
    natural_w * 4 >= available_w * 3;
  if (low_indent_fill || indented_fill)
  {
    S32 slack = available_w - natural_w;
    S32 needed = (slack + (S32)space_count - 1) / (S32)space_count;
    S32 fill_cap = MAX(natural_space_w * 8, 40);
    if (indented_fill) { fill_cap = MIN(natural_space_w * 4, 24); }
    if (needed <= fill_cap) { result = MAX(result, needed); }
  }
  return MAX(result, 0);
}

FUNCTION S32
eightvo_reader_justification_remainder_for_space(U32 space_index,
                                                  U32 space_count,
                                                  U32 remainder)
{
  if (space_count == 0 || remainder == 0 || space_index >= space_count)
    return 0;
  U32 before = (space_index * remainder) / space_count;
  U32 after = ((space_index + 1) * remainder) / space_count;
  return after > before ? 1 : 0;
}

FUNCTION B32
eightvo_reader_styled_row_build(
  EightvoApp *app,
  const EpubReaderFrameStyleRow *row,
  const PresentationEngineBlockFlowRow *presentation_row,
  U32 local_start,
  U32 local_end,
  EightvoReaderStyledRow *out_row)
{
  if (out_row) { MemoryZeroStruct(out_row); }
  if (!app || !row || !presentation_row || !out_row ||
      local_end <= local_start || local_start < row->byte_start)
  {
    return 0;
  }
  EightvoPresentationRowMetrics metrics = {0};
  if (!eightvo_resolve_presentation_row_metrics(app, row, &metrics))
    return 0;

  U32 row_size = local_end - local_start;
  U32 span_count = 0;
  U32 segment_start = 0;
  S32 natural_width = 0;
  S32 fill_h = 1;
  B32 dark = eightvo_theme_profile(app->theme).appearance ==
             UI0AppearanceMode_Dark;
  while (segment_start < row_size)
  {
    if (span_count >= EightvoDisplaySpanCap) { return 0; }
    U32 segment_end = row_size;
    DocTextStyleFlags flags = 0;
    U32 inline_scale_permille = 1000;
    U32 family = row->font_family_hint;
    U32 face = row->font_face_index;
    U32 text_color_rgb = row->text_color_rgb;
    B32 has_text_color = row->has_text_color;
    eightvo_reader_segment_style(app, row, row_size, segment_start,
                                  &segment_end, &flags,
                                  &inline_scale_permille, &family, &face,
                                  &text_color_rgb, &has_text_color);
    if (segment_end <= segment_start) { return 0; }
    S32 scale = (S32)MAX(
      ((S64)metrics.scale_px * (S64)MAX(inline_scale_permille, 1U) + 500) /
        1000,
      8);
    U32 scale_permille = (U32)MAX(
      ((S64)scale * 1000 + MAX(app->layout_key.text_scale, 1) / 2) /
        MAX(app->layout_key.text_scale, 1),
      1);
    U32 color = dark ? app->reader_content_theme.ink :
      (has_text_color ? text_color_rgb : app->reader_content_theme.ink);
    String8 segment_text = str8(app->frame.visible_text.str + local_start +
                                segment_start,
                                segment_end - segment_start);
    S32 segment_width = epub_reader_typography_measure_text(
      &app->reader.typography, segment_text, flags, scale_permille,
      family, face);
    EightvoReaderSpanStyle *span_style =
      app->reader_span_styles + span_count;
    *span_style = (EightvoReaderSpanStyle){
      .flags = flags,
      .scale_permille = scale_permille,
      .scale_px = scale,
      .font_family_hint = family,
      .font_face_index = face,
      .color = color,
      .resolved = epub_reader_typography_style_for_doc_style(
        &app->reader.typography, scale, color, family, face, flags, 0),
    };
    FontTextMetrics font_metrics = font_metrics_for_size(
      span_style->resolved.provider, scale);
    fill_h = MAX(fill_h, MAX(font_metrics.ascent_px + font_metrics.descent_px,
                             MAX(font_metrics.glyph_height_px, 1)));
    app->reader_display_spans[span_count] = (TextEngineDisplaySpan){
      .source_range = {
        .byte_start = app->frame.view_byte_offset + local_start + segment_start,
        .byte_end = app->frame.view_byte_offset + local_start + segment_end,
      },
      .x = natural_width,
      .w = MAX(segment_width, 0),
      .style_index = span_count,
    };
    natural_width += MAX(segment_width, 0);
    span_count += 1;
    segment_start = segment_end;
  }
  if (span_count == 0) { return 0; }

  U32 space_count = 0;
  for (U32 index = 1; index + 1 < row_size; index += 1)
  {
    if (app->frame.visible_text.str[local_start + index] == ' ')
      space_count += 1;
  }
  S32 extra_px = 0;
  U32 extra_remainder = 0;
  if (space_count > 0 &&
      eightvo_reader_row_allows_justification(app, row, row_size) &&
      presentation_row->content_rect.w > natural_width)
  {
    EightvoReaderSpanStyle base_style = app->reader_span_styles[0];
    S32 natural_space_w = epub_reader_typography_measure_text(
      &app->reader.typography, str8_from_cstr(" "), base_style.flags,
      base_style.scale_permille, base_style.font_family_hint,
      base_style.font_face_index);
    S32 slack = presentation_row->content_rect.w - natural_width;
    S32 max_slack = (S32)space_count *
      eightvo_reader_row_justification_extra_cap_per_space(
        row, space_count, natural_space_w, natural_width,
        presentation_row->content_rect.w);
    S32 applied = MIN(slack, max_slack);
    if (applied > 0)
    {
      extra_px = applied / (S32)space_count;
      extra_remainder = (U32)(applied % (S32)space_count);
    }
  }

  U32 stop_count = 0;
  U32 space_index = 0;
  S32 visual_x = 0;
  for (U32 span_index = 0; span_index < span_count; span_index += 1)
  {
    TextEngineDisplaySpan *span = app->reader_display_spans + span_index;
    EightvoReaderSpanStyle *style = app->reader_span_styles + span_index;
    U32 span_local_start = (U32)(span->source_range.byte_start -
                                 app->frame.view_byte_offset);
    U32 span_local_end = (U32)(span->source_range.byte_end -
                               app->frame.view_byte_offset);
    U32 first_stop = stop_count;
    if (stop_count >= EightvoDisplayRowStopCap) { return 0; }
    app->reader_display_stops[stop_count++] = (TextEngineDisplayRowStop){
      .source_byte = span->source_range.byte_start,
      .x = 0,
    };
    String8 span_text = str8(
      app->frame.visible_text.str + span_local_start,
      span_local_end - span_local_start);
    Scratch shape_scratch = scratch_begin(0, 0);
    TextEngineShapeResult span_shape = {0};
    B32 small_caps =
      (style->flags & DocTextStyleFlag_SmallCaps) != 0 &&
      (style->flags & DocTextStyleFlag_NormalCaps) == 0;
    B32 segmented_stops = extra_px != 0 || extra_remainder != 0;
    B32 use_shaped_stops =
      app->reader.typography.text_mode == EpubReaderTextMode_ShapedV1 &&
      !small_caps;
    if (use_shaped_stops && !segmented_stops)
    {
      use_shaped_stops =
        text_engine_shape_s8(shape_scratch.arena,
                             &style->resolved,
                             span_text,
                             &span_shape) &&
        span_shape.direction == FontTextDirection_Ltr &&
        span_shape.clusters && span_shape.cluster_count > 0;
    }
    TextEngineShapeResult token_shape = {0};
    U64 token_start = span_local_start;
    U64 token_end = span_local_start;
    S32 token_base_width = 0;
    U32 cluster_index = 0;
    S32 cluster_advance = 0;
    S32 span_extra = 0;
    U64 at = span_local_start;
    while (at < span_local_end)
    {
      if (stop_count >= EightvoDisplayRowStopCap)
      {
        scratch_end(shape_scratch);
        return 0;
      }
      U64 next = base_unicode_utf8_next_grapheme_boundary(
        app->frame.visible_text, at);
      if (next <= at || next > span_local_end)
      {
        scratch_end(shape_scratch);
        return 0;
      }
      if (next == at + 1 && app->frame.visible_text.str[at] == ' ' &&
          at > local_start && at + 1 < local_end &&
          space_index < space_count)
      {
        span_extra += extra_px +
          eightvo_reader_justification_remainder_for_space(
            space_index, space_count, extra_remainder);
        space_index += 1;
      }
      if (use_shaped_stops && segmented_stops && at >= token_end)
      {
        token_start = at;
        token_end = at;
        B32 space_token = app->frame.visible_text.str[at] == ' ';
        while (token_end < span_local_end)
        {
          U64 token_next = base_unicode_utf8_next_grapheme_boundary(
            app->frame.visible_text, token_end);
          if (token_next <= token_end || token_next > span_local_end)
          {
            use_shaped_stops = 0;
            break;
          }
          token_end = token_next;
          if (space_token || token_end >= span_local_end ||
              app->frame.visible_text.str[token_end] == ' ')
          {
            break;
          }
        }
        String8 token_text = str8(
          app->frame.visible_text.str + token_start,
          token_end - token_start);
        token_shape = (TextEngineShapeResult){0};
        use_shaped_stops = use_shaped_stops &&
          text_engine_shape_s8(shape_scratch.arena,
                               &style->resolved,
                               token_text,
                               &token_shape) &&
          token_shape.direction == FontTextDirection_Ltr &&
          token_shape.clusters && token_shape.cluster_count > 0;
        cluster_index = 0;
        cluster_advance = 0;
      }
      S32 prefix_width = 0;
      if (use_shaped_stops)
      {
        TextEngineShapeResult *shape = segmented_stops ?
          &token_shape : &span_shape;
        U64 shape_start = segmented_stops ? token_start : span_local_start;
        U64 relative_next = next - shape_start;
        while (cluster_index < shape->cluster_count &&
               shape->clusters[cluster_index].byte_end <= relative_next)
        {
          cluster_advance += MAX(
            shape->clusters[cluster_index].advance_px, 0);
          cluster_index += 1;
        }
        S32 shaped_width = (next == (segmented_stops ?
          token_end : span_local_end)) ?
          MAX(shape->width_px, cluster_advance) : cluster_advance;
        prefix_width = token_base_width + shaped_width + span_extra;
        if (segmented_stops && next == token_end)
        {
          token_base_width += MAX(shape->width_px, cluster_advance);
        }
      }
      else
      {
        String8 prefix = str8(
          app->frame.visible_text.str + span_local_start,
          next - span_local_start);
        prefix_width = epub_reader_typography_measure_text(
          &app->reader.typography, prefix, style->flags,
          style->scale_permille, style->font_family_hint,
          style->font_face_index) + span_extra;
      }
      prefix_width = MAX(prefix_width,
                         app->reader_display_stops[stop_count - 1].x);
      app->reader_display_stops[stop_count++] = (TextEngineDisplayRowStop){
        .source_byte = app->frame.view_byte_offset + next,
        .x = prefix_width,
      };
      at = next;
    }
    scratch_end(shape_scratch);
    span->stops = app->reader_display_stops + first_stop;
    span->stop_count = stop_count - first_stop;
    span->x = visual_x;
    span->w = app->reader_display_stops[stop_count - 1].x;
    visual_x += span->w;
  }

  S32 text_x = presentation_row->content_rect.x;
  if (row->block_kind == DocTextBlockKind_Heading ||
      row->text_align == DocTextAlign_Center)
  {
    text_x += MAX((presentation_row->content_rect.w - visual_x) / 2, 0);
  }
  else if (row->text_align == DocTextAlign_Right)
  {
    text_x += MAX(presentation_row->content_rect.w - visual_x, 0);
  }
  fill_h = MIN(fill_h, presentation_row->row_rect.h);
  if (!text_engine_display_span_row_make(
        &out_row->display,
        app->reader_display_spans,
        span_count,
        (TextEngineSourceRange){
          .byte_start = app->frame.view_byte_offset + local_start,
          .byte_end = app->frame.view_byte_offset + local_end,
        },
        text_x,
        presentation_row->row_rect.y,
        fill_h))
  {
    return 0;
  }
  out_row->local_start = local_start;
  out_row->local_end = local_end;
  out_row->span_count = span_count;
  out_row->stop_count = stop_count;
  out_row->justify_space_count = space_count;
  out_row->justify_extra_px = extra_px;
  out_row->justify_extra_remainder = extra_remainder;
  out_row->natural_width = natural_width;
  out_row->fill_h = fill_h;
  return 1;
}

FUNCTION PresentationEngineBlockRole
eightvo_presentation_block_role(DocTextBlockKind kind)
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
eightvo_presentation_text_align(DocTextAlign align)
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
eightvo_presentation_media_status(EpubReaderFrameImageStatus status)
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
eightvo_resolve_presentation_image_box(const EpubReaderFrameStyleRow *row,
                                        const EpubReaderFrameImage *image,
                                        S32 body_width_px,
                                        S32 body_height_px,
                                        S32 line_height_px,
                                        S32 content_left_px,
                                        EightvoPresentationImageBox *out_box)
{
  if (out_box) { MemoryZeroStruct(out_box); }
  if (!row || !image || !out_box || body_width_px <= 0 ||
      body_height_px <= 0 || line_height_px <= 0 || content_left_px < 0)
  {
    return 0;
  }

  if (image->image_placement == SourceReaderLayoutImagePlacement_ImageOnly)
  {
    U32 visual_units = row->visual_units ? row->visual_units : 18;
    S64 canonical_height = (S64)visual_units * (S64)line_height_px;
    if (canonical_height <= 0 || canonical_height > body_height_px)
    {
      return 0;
    }
    *out_box = (EightvoPresentationImageBox){
      .x_offset_px = 0,
      .width_px = body_width_px,
      .height_px = (S32)canonical_height,
    };
    return 1;
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

  *out_box = (EightvoPresentationImageBox){
    .x_offset_px = content_left_px,
    .width_px = image_width,
    .height_px = (S32)desired_height,
  };
  return 1;
}

FUNCTION U64
eightvo_presentation_hash_mix(U64 state, U64 value)
{
  return (state ^ value) * 1099511628211ULL;
}

FUNCTION U64
eightvo_presentation_frame_hash(const PresentationEngineBlockFlowFrame *frame)
{
  if (!frame || !frame->valid) { return 0; }
  U64 hash = 1469598103934665603ULL;
  hash = eightvo_presentation_hash_mix(hash, frame->row_count);
  hash = eightvo_presentation_hash_mix(hash, frame->media_count);
  hash = eightvo_presentation_hash_mix(hash, (U64)(S64)frame->content_height_px);
  for (U32 index = 0; index < frame->row_count; index += 1)
  {
    const PresentationEngineBlockFlowRow *row = frame->rows + index;
    hash = eightvo_presentation_hash_mix(hash, row->role);
    hash = eightvo_presentation_hash_mix(hash, row->source_row);
    hash = eightvo_presentation_hash_mix(hash, row->source_start);
    hash = eightvo_presentation_hash_mix(hash, row->source_end);
    hash = eightvo_presentation_hash_mix(hash, (U64)(S64)row->row_rect.x);
    hash = eightvo_presentation_hash_mix(hash, (U64)(S64)row->row_rect.y);
    hash = eightvo_presentation_hash_mix(hash, (U64)(S64)row->row_rect.h);
    hash = eightvo_presentation_hash_mix(hash, (U64)(S64)row->content_rect.x);
    hash = eightvo_presentation_hash_mix(hash, (U64)(S64)row->content_rect.w);
    hash = eightvo_presentation_hash_mix(hash, row->first_media_index);
  }
  for (U32 index = 0; index < frame->media_count; index += 1)
  {
    const PresentationEngineBlockFlowMedia *media = frame->media + index;
    hash = eightvo_presentation_hash_mix(hash, media->row_index);
    hash = eightvo_presentation_hash_mix(hash, media->status);
    hash = eightvo_presentation_hash_mix(hash, (U64)(S64)media->rect.x);
    hash = eightvo_presentation_hash_mix(hash, (U64)(S64)media->rect.y);
    hash = eightvo_presentation_hash_mix(hash, (U64)(S64)media->rect.w);
    hash = eightvo_presentation_hash_mix(hash, (U64)(S64)media->rect.h);
  }
  return hash;
}

FUNCTION B32
eightvo_build_reader_presentation(EightvoApp *app,
                                   S32 body_x,
                                   S32 body_y,
                                   S32 body_w,
                                   S32 body_h)
{
  if (!app || body_w <= 0 || body_h <= 0 ||
      app->frame.style_row_count > EightvoPresentationRowCap ||
      app->frame.image_count > EightvoPresentationMediaCap)
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
    EightvoPresentationRowMetrics metrics = {0};
    if (!eightvo_resolve_presentation_row_metrics(app, row, &metrics) ||
        row->byte_end < row->byte_start)
    {
      return 0;
    }

    EpubReaderFrameImage *image = eightvo_image_for_row(&app->frame, row->row);
    S32 row_height_px = metrics.line_height_px;
    PresentationEngineBlockRole role = eightvo_presentation_block_role(row->block_kind);
    if (image)
    {
      EightvoPresentationImageBox box = {0};
      if (media_count >= EightvoPresentationMediaCap ||
          image->text_byte_end < image->text_byte_start ||
          !eightvo_resolve_presentation_image_box(row,
                                                   image,
                                                   body_w,
                                                   body_h,
                                                   app->layout_key.line_height,
                                                   metrics.content_left_px,
                                                   &box))
      {
        return 0;
      }
      row_height_px = box.height_px +
        (image->image_placement ==
           SourceReaderLayoutImagePlacement_ImageOnly ? 0 : 8);
      role = PresentationEngineBlockRole_Media;
      app->presentation_media_specs[media_count] =
        (PresentationEngineBlockFlowMediaSpec){
          .row_index = row_index,
          .status = eightvo_presentation_media_status(image->status),
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
        .text_align = eightvo_presentation_text_align(row->text_align),
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
        .row_capacity = EightvoPresentationRowCap,
        .media = app->presentation_media,
        .media_capacity = EightvoPresentationMediaCap,
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
    eightvo_presentation_frame_hash(&app->presentation_frame);
  return 1;
}

FUNCTION B32
eightvo_reader_display_span_row(
  EightvoApp *app,
  const EpubReaderFrameStyleRow *row,
  const PresentationEngineBlockFlowRow *presentation_row,
  TextEngineDisplaySpanRow *out_display_row);

FUNCTION B32
eightvo_reader_point_to_byte(EightvoApp *app,
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
    if (row->style_index >= app->frame.style_row_count) return 0;
    const EpubReaderFrameStyleRow *style_row =
      app->frame.style_rows + row->style_index;
    TextEngineDisplaySpanRow display_row = {0};
    U64 source_byte = 0;
    S32 caret_x = 0;
    if (!eightvo_reader_display_span_row(app, style_row, row, &display_row) ||
        !text_engine_display_span_row_source_byte_at_x(&display_row, x,
                                                       &source_byte) ||
        !text_engine_display_span_row_x_for_source_byte(&display_row,
                                                        source_byte,
                                                        &caret_x))
      return 0;
    *out_byte = source_byte;
    *out_anchor_rect = ui0_rect(caret_x - 2, rect.y, 4, rect.h);
    return 1;
  }
  return 0;
}

FUNCTION void
eightvo_update_pointer_selection(EightvoApp *app, S32 x, S32 y, B32 begin)
{
  if (!app) return;
  U64 byte = 0;
  UI0Rect anchor_rect = {0};
  if (!eightvo_reader_point_to_byte(app, x, y, &byte, &anchor_rect))
  {
    if (begin) app->selection_dragging = 0;
    return;
  }
  if (begin)
  {
    app->selection_dragging = 1;
    app->selection_anchor_byte = byte;
    epub_reader_clear_selection(&app->reader);
    app->selected_text[0] = 0;
    return;
  }
  if (!app->selection_dragging) return;
  U64 start = MIN(app->selection_anchor_byte, byte);
  U64 end = MAX(app->selection_anchor_byte, byte);
  if (end <= start) return;
  DocSelection selection = {
    .spine_index = app->reader.active_spine_index,
    .text_byte_start = start,
    .text_byte_end = end,
  };
  if (epub_reader_set_selection(&app->reader, selection) == EpubReaderResult_Ok)
  {
    app->selection_anchor_rect = anchor_rect;
    eightvo_prepare_selected_text(app);
  }
}

FUNCTION B32
eightvo_reader_selection_contains_point(EightvoApp *app, S32 x, S32 y)
{
  if (!app || !app->reader.has_active_selection ||
      app->reader.active_selection.spine_index != app->frame.spine_index ||
      !app->presentation_frame.valid)
  {
    return 0;
  }
  U64 selection_start = app->reader.active_selection.text_byte_start;
  U64 selection_end = app->reader.active_selection.text_byte_end;
  if (selection_end <= selection_start) return 0;
  for (U32 index = 0;
       index < app->presentation_frame.row_count;
       index += 1)
  {
    const PresentationEngineBlockFlowRow *presentation_row =
      app->presentation_frame.rows + index;
    if (presentation_row->style_index >= app->frame.style_row_count)
      continue;
    const EpubReaderFrameStyleRow *style_row =
      app->frame.style_rows + presentation_row->style_index;
    U64 row_start = app->frame.view_byte_offset + style_row->byte_start;
    U64 row_end = app->frame.view_byte_offset + style_row->byte_end;
    if (selection_end <= row_start || selection_start >= row_end) continue;
    TextEngineDisplaySpanRow display_row = {0};
    TextEngineRowRect range_rect = {0};
    if (!eightvo_reader_display_span_row(app, style_row, presentation_row,
                                          &display_row) ||
        !text_engine_display_span_row_range_rect_from_source_range(
          &display_row,
          MAX(selection_start, row_start),
          MIN(selection_end, row_end),
          &range_rect))
    {
      continue;
    }
    UI0Rect rect = ui0_rect(range_rect.x,
                            presentation_row->row_rect.y,
                            MAX(range_rect.w, 3),
                            presentation_row->row_rect.h);
    if (ui0_rect_contains_point(rect, x, y)) return 1;
  }
  return 0;
}

FUNCTION B32
eightvo_reader_selection_popup_contains_point(const EightvoApp *app,
                                                S32 x,
                                                S32 y)
{
  if (!app ||
      app->reader_view_state.popup != ReaderViewPopup_SelectionTools ||
      !app->reader_view_frame.semantic_nodes)
  {
    return 0;
  }
  for (UI0S32 index = 0;
       index < app->reader_view_frame.semantic_node_count;
       index += 1)
  {
    const ReaderViewSemanticNode *node =
      app->reader_view_frame.semantic_nodes + index;
    if (node->role == ReaderViewSemantic_Group &&
        node->name.size == 4 && node->name.data &&
        memcmp(node->name.data, "More", 4) == 0)
    {
      return ui0_rect_contains_point(node->rect, x, y);
    }
  }
  return 0;
}

FUNCTION void
eightvo_host_pointer_press(EightvoApp *app, S32 x, S32 y)
{
  if (!app) return;
  app->input.pointer_x = x;
  app->input.pointer_y = y;
  app->input.pointer_down = 1;
  app->input.pointer_pressed = 1;
  app->input.pointer_selection_release = 0;
  if (eightvo_library_active(app))
  {
    app->host_pointer_armed = EightvoHostControl_None;
    if (app->library.entry_count == 0 &&
        ui0_rect_contains_point(eightvo_library_empty_add_rect(app), x, y))
    {
      app->host_pointer_armed = EightvoHostControl_LibraryAdd;
    }
    else
    {
      for (U32 index = app->host_control_count; index > 0; index -= 1)
      {
        EightvoHostControlRecord *record = app->host_controls + index - 1;
        if (ui0_rect_contains_point(record->semantic.rect, x, y))
        {
          app->host_pointer_armed = record->identity;
          if (record->entry_id)
            app->library_selected_entry_id = record->entry_id;
          break;
        }
      }
    }
    (void)eightvo_host_focus_set(app, app->host_pointer_armed, 0);
    return;
  }
  if (app->reader_view_state.popup == ReaderViewPopup_SelectionTools &&
      !eightvo_reader_selection_popup_contains_point(app, x, y) &&
      !eightvo_reader_selection_contains_point(app, x, y))
  {
    /*
    Match the pre-extraction reader interaction: one press outside both the
    selected glyphs and their action surface clears the concrete selection
    while the same frame dismisses the transient popup.
    */
    eightvo_reader_view_escape(app);
  }
  UI0Rect exit_rect = eightvo_host_exit_rect(app);
  app->host_exit_pointer_armed =
    exit_rect.w > 0 && ui0_rect_contains_point(exit_rect, x, y);
  if (app->host_exit_pointer_armed)
  {
    (void)eightvo_host_focus_set(app, EightvoHostControl_ExitReader, 0);
  }
  else
  {
    (void)eightvo_host_focus_set(app, EightvoHostControl_None, 0);
    eightvo_update_pointer_selection(app, x, y, 1);
  }
}

FUNCTION B32
eightvo_host_pointer_release(EightvoApp *app, S32 x, S32 y)
{
  if (!app) return 0;
  app->input.pointer_x = x;
  app->input.pointer_y = y;
  app->input.pointer_down = 0;
  app->input.pointer_released = 1;
  app->input.pointer_selection_release = 0;
  if (eightvo_library_active(app))
  {
    EightvoHostControlIdentity armed = app->host_pointer_armed;
    app->host_pointer_armed = EightvoHostControl_None;
    if (armed == EightvoHostControl_None) return 0;
    EightvoHostControlRecord *record = eightvo_host_control_record(app, armed);
    B32 inside = record && ui0_rect_contains_point(record->semantic.rect, x, y);
    if (app->library.entry_count == 0 &&
        armed == EightvoHostControl_LibraryAdd)
      inside = inside || ui0_rect_contains_point(
        eightvo_library_empty_add_rect(app), x, y);
    if (!inside) return 0;
    app->input.pointer_released = 0;
    return eightvo_host_control_invoke(app, armed);
  }
  UI0Rect exit_rect = eightvo_host_exit_rect(app);
  B32 activate_exit = app->host_exit_pointer_armed && exit_rect.w > 0 &&
                      ui0_rect_contains_point(exit_rect, x, y);
  app->host_exit_pointer_armed = 0;
  if (activate_exit)
  {
    app->input.pointer_released = 0;
    return eightvo_host_control_invoke(app, EightvoHostControl_ExitReader);
  }
  if (app->selection_dragging)
  {
    eightvo_update_pointer_selection(app, x, y, 0);
    app->selection_dragging = 0;
    if (app->reader.has_active_selection)
      app->input.pointer_selection_release = 1;
  }
  return 0;
}

FUNCTION U32
eightvo_reader_page_color(const EightvoApp *app)
{
  return app ? app->reader_content_theme.page_background : 0x00FFFDF9U;
}

FUNCTION U32
eightvo_reader_ink_color(const EightvoApp *app)
{
  return app ? app->reader_content_theme.ink : 0x001B1A18U;
}

FUNCTION U32
eightvo_reader_highlight_color(const EightvoApp *app, U32 color_index)
{
  B32 dark = app && eightvo_theme_profile(app->theme).appearance ==
                      UI0AppearanceMode_Dark;
  switch (color_index)
  {
    case 1: return dark ? 0x0047355CU : 0x00FFD4ECU;
    case 2: return dark ? 0x002A4662U : 0x00CDE7FFU;
    case 3: return dark ? 0x00523F1CU : 0x00FFDCA8U;
    default: return app ? app->reader_content_theme.user_highlight : 0x00FFF2A6U;
  }
}

typedef struct EightvoReaderRowMeasure
{
  B32 valid;
  EightvoReaderStyledRow styled;
} EightvoReaderRowMeasure;

FUNCTION B32
eightvo_reader_row_measure(EightvoApp *app,
                            const EpubReaderFrameStyleRow *row,
                            const PresentationEngineBlockFlowRow *presentation_row,
                            U32 local_start,
                            U32 local_end,
                            EightvoReaderRowMeasure *out_measure)
{
  if (out_measure) *out_measure = (EightvoReaderRowMeasure){0};
  if (!app || !row || !presentation_row || !out_measure ||
      local_end <= local_start)
    return 0;

  out_measure->valid = eightvo_reader_styled_row_build(
    app, row, presentation_row, local_start, local_end,
    &out_measure->styled);
  return out_measure->valid;
}

FUNCTION S32
eightvo_reader_row_x_for_local_byte(
  EightvoApp *app,
  const EpubReaderFrameStyleRow *row,
  const EightvoReaderRowMeasure *measure,
  U32 local_byte)
{
  if (!app || !row || !measure || !measure->valid)
    return 0;
  local_byte = MIN(MAX(local_byte, measure->styled.local_start),
                   measure->styled.local_end);
  S32 result = measure->styled.display.x;
  (void)text_engine_display_span_row_x_for_source_byte(
    &measure->styled.display,
    app->frame.view_byte_offset + local_byte,
    &result);
  return result;
}

FUNCTION B32
eightvo_reader_display_span_row(
  EightvoApp *app,
  const EpubReaderFrameStyleRow *row,
  const PresentationEngineBlockFlowRow *presentation_row,
  TextEngineDisplaySpanRow *out_display_row)
{
  if (out_display_row) *out_display_row = (TextEngineDisplaySpanRow){0};
  if (!app || !row || !presentation_row || !out_display_row)
    return 0;

  U32 local_start = MIN(row->byte_start, (U32)app->frame.visible_text.size);
  U32 local_end = MIN(row->byte_end, (U32)app->frame.visible_text.size);
  while (local_end > local_start &&
         (app->frame.visible_text.str[local_end - 1] == '\n' ||
          app->frame.visible_text.str[local_end - 1] == '\r'))
  {
    local_end -= 1;
  }
  if (local_end <= local_start) return 0;

  EightvoReaderStyledRow styled = {0};
  if (!eightvo_reader_styled_row_build(app, row, presentation_row,
                                        local_start, local_end, &styled))
    return 0;
  *out_display_row = styled.display;
  return 1;
}

FUNCTION void
eightvo_reader_row_range_x(EightvoApp *app,
                            const EpubReaderFrameStyleRow *row,
                            const PresentationEngineBlockFlowRow *presentation_row,
                            const EightvoReaderRowMeasure *measure,
                            U64 row_size,
                            U64 start,
                            U64 end,
                            S32 *out_x0,
                            S32 *out_x1)
{
  if (!out_x0 || !out_x1) return;
  TextEngineDisplaySpanRow display_row = {0};
  TextEngineRowRect range_rect = {0};
  U64 source_base = app ? app->frame.view_byte_offset : 0;
  U64 source_start = source_base + (U64)row->byte_start + start;
  U64 source_end = source_base + (U64)row->byte_start + end;
  if (eightvo_reader_display_span_row(app, row, presentation_row,
                                       &display_row))
  {
    if (source_start == source_end)
    {
      S32 caret_x = 0;
      if (text_engine_display_span_row_x_for_source_byte(
            &display_row, source_start, &caret_x))
      {
        *out_x0 = caret_x;
        *out_x1 = caret_x;
        return;
      }
    }
    else if (text_engine_display_span_row_range_rect_from_source_range(
               &display_row, source_start, source_end, &range_rect))
    {
      *out_x0 = range_rect.x;
      *out_x1 = range_rect.x + range_rect.w;
      return;
    }
  }
  if (measure && measure->valid)
  {
    U64 local_start = (U64)row->byte_start + start;
    U64 local_end = (U64)row->byte_start + end;
    *out_x0 = eightvo_reader_row_x_for_local_byte(
      app, row, measure, (U32)MIN(local_start, (U64)UINT32_MAX));
    *out_x1 = eightvo_reader_row_x_for_local_byte(
      app, row, measure, (U32)MIN(local_end, (U64)UINT32_MAX));
    return;
  }
  *out_x0 = presentation_row->content_rect.x +
    (S32)((start * (U64)MAX(presentation_row->content_rect.w, 1)) /
          MAX(row_size, 1));
  *out_x1 = presentation_row->content_rect.x +
    (S32)((end * (U64)MAX(presentation_row->content_rect.w, 1)) /
          MAX(row_size, 1));
}

FUNCTION void
eightvo_draw_reader_note_marker(EightvoApp *app, S32 anchor_x, S32 row_y,
                                 S32 row_h)
{
  if (!app) return;
  S32 x = anchor_x + 2;
  S32 y = row_y - MAX(2, row_h / 5);
  (void)draw_push_rect(&app->draw_commands, DrawLayer_UI,
                       x, y, 7, 8, app->reader_content_theme.note_marker);
  (void)draw_push_rect(&app->draw_commands, DrawLayer_UI,
                       x + 2, y + 2, 3, 1,
                       app->reader_content_theme.page_background);
  (void)draw_push_rect(&app->draw_commands, DrawLayer_UI,
                       x + 2, y + 4, 3, 1,
                       app->reader_content_theme.page_background);
}

FUNCTION void
eightvo_draw_row_highlights(EightvoApp *app,
                             const EpubReaderFrameStyleRow *row,
                             const PresentationEngineBlockFlowRow *presentation_row)
{
  if (!app || !row || !presentation_row || row->byte_end <= row->byte_start)
    return;
  B32 find_overlay =
    app->reader_view_state.left_panel == ReaderViewLeftPanel_Find &&
    app->frame.search_highlight_count > 0;
  B32 selection_overlay = app->reader.has_active_selection &&
    app->reader.active_selection.spine_index == app->frame.spine_index;
  if (app->highlight_count == 0 && !find_overlay && !selection_overlay)
    return;
  U64 row_start = app->frame.view_byte_offset + row->byte_start;
  U64 row_end = app->frame.view_byte_offset + row->byte_end;
  U64 row_size = row_end - row_start;
  U32 local_row_start = MIN(row->byte_start, (U32)app->frame.visible_text.size);
  U32 local_row_end = MIN(row->byte_end, (U32)app->frame.visible_text.size);
  while (local_row_end > local_row_start &&
         (app->frame.visible_text.str[local_row_end - 1] == '\n' ||
          app->frame.visible_text.str[local_row_end - 1] == '\r'))
  {
    local_row_end -= 1;
  }
  EightvoReaderRowMeasure row_measure = {0};
  (void)eightvo_reader_row_measure(app, row, presentation_row,
                                    local_row_start, local_row_end,
                                    &row_measure);
  for (U32 index = 0; index < app->highlight_count; index += 1)
  {
    const EightvoHighlight *highlight = app->highlights + index;
    if (highlight->spine_index != app->frame.spine_index)
      continue;
    if (highlight->is_highlight &&
        highlight->end_byte > row_start && highlight->start_byte < row_end)
    {
      U64 start = MAX(highlight->start_byte, row_start) - row_start;
      U64 end = MIN(highlight->end_byte, row_end) - row_start;
      S32 x0 = 0;
      S32 x1 = 0;
      eightvo_reader_row_range_x(app, row, presentation_row, &row_measure,
                                  row_size, start, end, &x0, &x1);
      U32 color = eightvo_reader_highlight_color(app,
                                                  highlight->color_index);
      (void)draw_push_rect(&app->draw_commands, DrawLayer_World,
                           x0, presentation_row->row_rect.y,
                           MAX(x1 - x0, 3), presentation_row->row_rect.h,
                           color);
    }
    if (highlight->note[0] && highlight->end_byte >= row_start &&
        highlight->end_byte <= row_end)
    {
      U64 marker = highlight->end_byte - row_start;
      S32 marker_x0 = 0;
      S32 marker_x1 = 0;
      eightvo_reader_row_range_x(app, row, presentation_row, &row_measure,
                                  row_size, marker, marker,
                                  &marker_x0, &marker_x1);
      eightvo_draw_reader_note_marker(app, marker_x1,
                                      presentation_row->row_rect.y,
                                      presentation_row->row_rect.h);
    }
  }
  if (app->reader_view_state.left_panel == ReaderViewLeftPanel_Find &&
      row_measure.valid)
  {
    for (U32 search_pass = 0; search_pass < 2; search_pass += 1)
    {
      B32 draw_active = search_pass == 1;
      for (U32 index = 0; index < app->frame.search_highlight_count; index += 1)
      {
        const EpubReaderFrameSearchHighlightRange *range =
          app->frame.search_highlights + index;
        if ((range->active != 0) != draw_active ||
            range->end <= local_row_start || range->start >= local_row_end)
          continue;
        U32 overlap_start = (U32)MAX(range->start, (U64)local_row_start);
        U32 overlap_end = (U32)MIN(range->end, (U64)local_row_end);
        S32 x0 = eightvo_reader_row_x_for_local_byte(
          app, row, &row_measure, overlap_start);
        S32 x1 = eightvo_reader_row_x_for_local_byte(
          app, row, &row_measure, overlap_end);
        U32 color = draw_active ? app->reader_content_theme.search_match :
                                  app->reader_content_theme.search_hit;
        (void)draw_push_rect(&app->draw_commands,
                             DrawLayer_World,
                             x0,
                             presentation_row->row_rect.y,
                             MAX(x1 - x0, 1),
                             MAX(row_measure.styled.fill_h, 1),
                             color);
      }
    }
  }
  if (app->reader.has_active_selection &&
      app->reader.active_selection.spine_index == app->frame.spine_index &&
      app->reader.active_selection.text_byte_end > row_start &&
      app->reader.active_selection.text_byte_start < row_end)
  {
    U64 start = MAX(app->reader.active_selection.text_byte_start, row_start) - row_start;
    U64 end = MIN(app->reader.active_selection.text_byte_end, row_end) - row_start;
    S32 x0 = 0;
    S32 x1 = 0;
    eightvo_reader_row_range_x(app, row, presentation_row, &row_measure,
                                row_size, start, end, &x0, &x1);
    U32 color = app->reader_content_theme.selection;
    (void)draw_push_rounded_rect(&app->draw_commands, DrawLayer_World,
                                 x0, presentation_row->row_rect.y,
                                 MAX(x1 - x0, 3), presentation_row->row_rect.h,
                                 2, color, color);
  }
}

FUNCTION B32
eightvo_draw_reader_styled_row(
  EightvoApp *app,
  const EpubReaderFrameStyleRow *row,
  const PresentationEngineBlockFlowRow *presentation_row,
  U32 local_start,
  U32 local_end,
  S32 clip_x,
  S32 clip_y,
  S32 clip_w,
  S32 clip_h)
{
  if (!app || !row || !presentation_row || local_end <= local_start)
    return 0;
  EightvoReaderStyledRow styled = {0};
  if (!eightvo_reader_styled_row_build(app, row, presentation_row,
                                        local_start, local_end, &styled))
    return 0;

  for (U32 span_index = 0; span_index < styled.span_count; span_index += 1)
  {
    const TextEngineDisplaySpan *span = styled.display.spans + span_index;
    if (span->style_index >= styled.span_count) { return 0; }
    const EightvoReaderSpanStyle *style =
      app->reader_span_styles + span->style_index;
    if (styled.justify_extra_px == 0 &&
        styled.justify_extra_remainder == 0)
    {
      U64 local_span_start =
        span->source_range.byte_start - app->frame.view_byte_offset;
      String8 span_text = str8(
        app->frame.visible_text.str + local_span_start,
        span->source_range.byte_end - span->source_range.byte_start);
      if (!eightvo_push_reader_text_chunks(
            app, span_text, style->flags, style->font_family_hint,
            style->font_face_index, &style->resolved,
            styled.display.x + span->x,
            presentation_row->row_rect.y, style->scale_px, style->color,
            clip_x, clip_y, clip_w, clip_h))
      {
        return 0;
      }
      continue;
    }
    U64 source_at = span->source_range.byte_start;
    while (source_at < span->source_range.byte_end)
    {
      U64 run_end = source_at;
      while (run_end < span->source_range.byte_end)
      {
        U64 local_at = run_end - app->frame.view_byte_offset;
        U64 next = base_unicode_utf8_next_grapheme_boundary(
          app->frame.visible_text, local_at);
        if (next <= local_at ||
            app->frame.view_byte_offset + next > span->source_range.byte_end)
        {
          return 0;
        }
        run_end = app->frame.view_byte_offset + next;
        if (next == local_at + 1 &&
            app->frame.visible_text.str[local_at] == ' ')
        {
          break;
        }
      }
      if (run_end <= source_at) { return 0; }
      S32 run_x = styled.display.x;
      if (!text_engine_display_span_row_x_for_source_byte(
            &styled.display, source_at, &run_x))
      {
        return 0;
      }
      U64 local_run_start = source_at - app->frame.view_byte_offset;
      String8 text = str8(app->frame.visible_text.str + local_run_start,
                          run_end - source_at);
      if (!eightvo_push_reader_text_chunks(
            app, text, style->flags, style->font_family_hint,
            style->font_face_index, &style->resolved, run_x,
            presentation_row->row_rect.y, style->scale_px, style->color,
            clip_x, clip_y, clip_w, clip_h))
      {
        return 0;
      }
      source_at = run_end;
    }
  }
  return 1;
}

FUNCTION U64
eightvo_reader_page_visual_key(const EpubReaderFrame *frame)
{
  if (!frame || !frame->ready || !frame->document_open) return 0;
  U64 values[] = {
    u64_hash_bytes(&frame->document_id, sizeof(frame->document_id)),
    frame->document_generation,
    frame->spine_index,
    frame->view_byte_offset,
    u64_hash_str8(frame->visible_text),
    u64_hash_str8(frame->decorative_leadin),
    frame->text_mode,
    frame->has_selection,
    frame->selection_start,
    frame->selection_end,
    frame->selection_committed,
    frame->style_row_count,
    frame->style_row_count ?
      u64_hash_bytes(frame->style_rows,
                     sizeof(*frame->style_rows) * frame->style_row_count) : 0,
    frame->style_fragment_count,
    frame->style_fragment_count ?
      u64_hash_bytes(frame->style_fragments,
                     sizeof(*frame->style_fragments) *
                       frame->style_fragment_count) : 0,
    frame->table_count,
    frame->table_count ?
      u64_hash_bytes(frame->tables,
                     sizeof(*frame->tables) * frame->table_count) : 0,
    frame->table_row_count,
    frame->table_row_count ?
      u64_hash_bytes(frame->table_rows,
                     sizeof(*frame->table_rows) * frame->table_row_count) : 0,
    frame->table_cell_count,
    frame->table_cell_count ?
      u64_hash_bytes(frame->table_cells,
                     sizeof(*frame->table_cells) * frame->table_cell_count) : 0,
    frame->list_sidecar_count,
    frame->list_sidecar_count ?
      u64_hash_bytes(frame->list_sidecars,
                     sizeof(*frame->list_sidecars) *
                       frame->list_sidecar_count) : 0,
    frame->highlight_count,
    frame->highlight_count ?
      u64_hash_bytes(frame->highlights,
                     sizeof(*frame->highlights) * frame->highlight_count) : 0,
    frame->search_highlight_count,
    frame->search_highlight_count ?
      u64_hash_bytes(frame->search_highlights,
                     sizeof(*frame->search_highlights) *
                       frame->search_highlight_count) : 0,
    frame->note_marker_count,
    frame->note_marker_count ?
      u64_hash_bytes(frame->note_markers,
                     sizeof(*frame->note_markers) * frame->note_marker_count) : 0,
  };
  U64 key = u64_hash_bytes(values, sizeof(values));
  return key ? key : 1;
}

FUNCTION B32
eightvo_draw_cached_adjacent_page(EightvoApp *app, UI0Rect page)
{
  if (!app || !app->presentation_complete || !app->adjacent_page_ready ||
      app->frame.image_count != 0 ||
      app->adjacent_page_visual_key !=
        eightvo_reader_page_visual_key(&app->frame) ||
      app->adjacent_page_annotation_revision != app->annotation_revision ||
      app->adjacent_page_buffer_width != app->width ||
      app->adjacent_page_buffer_height != app->height ||
      app->adjacent_page_rect.x != page.x ||
      app->adjacent_page_rect.y != page.y ||
      app->adjacent_page_rect.w != page.w ||
      app->adjacent_page_rect.h != page.h ||
      app->adjacent_page_font_family != app->font_family ||
      app->adjacent_page_embedded_fonts_enabled !=
        app->layout_key.embedded_fonts_enabled ||
      app->adjacent_page_text_size_index != app->text_size_index ||
      app->adjacent_page_line_spacing_index != app->line_spacing_index ||
      app->adjacent_page_theme != app->theme ||
      page.x < 0 || page.y < 0 || page.w <= 0 || page.h <= 0 ||
      page.x + page.w > app->adjacent_page_buffer_width ||
      page.y + page.h > app->adjacent_page_buffer_height)
  {
    return 0;
  }

  const U32 *page_pixels = app->adjacent_page_pixels +
    (U64)page.y * (U64)app->adjacent_page_buffer_width + (U64)page.x;
  if (draw_push_sprite_clipped(&app->draw_commands,
                               DrawLayer_World,
                               page_pixels,
                               page.w,
                               page.h,
                               app->adjacent_page_buffer_width,
                               page.x,
                               page.y,
                               page.w,
                               page.h,
                               page.x,
                               page.y,
                               page.w,
                               page.h))
  {
    app->adjacent_page_cache_used_last_render = 1;
    return 1;
  }
  app->presentation_complete = 0;
  return 0;
}

FUNCTION void
eightvo_draw_reader_page(EightvoApp *app)
{
  UI0Rect page = app->reader_content_geometry.page_surface_rect;
  UI0Rect content = app->reader_content_geometry.content_rect;
  if (page.w <= 0 || page.h <= 0 || content.w <= 0 || content.h <= 0)
  {
    MemoryZeroStruct(&app->presentation_frame);
    app->presentation_hash = 0;
    app->presentation_complete = 0;
    return;
  }
  S32 body_x = content.x;
  S32 body_y = content.y;
  S32 body_w = MAX(content.w, 1);
  S32 body_h = MAX(content.h, 1);
  U32 page_color = eightvo_reader_page_color(app);
  (void)draw_push_rect(&app->draw_commands,
                       DrawLayer_World,
                       page.x,
                       page.y,
                       page.w,
                       page.h,
                       page_color);

  if (!app->frame.ready || !app->frame.document_open)
  {
    MemoryZeroStruct(&app->presentation_frame);
    app->presentation_hash = 0;
    /* Reader View owns projected empty/loading/error status presentation. */
    return;
  }

  if (eightvo_draw_cached_adjacent_page(app, page)) return;

  if (app->frame.image_count != 0)
  {
    U64 prepared_key_values[] = {
      eightvo_reader_page_visual_key(&app->frame),
      (U64)(U32)body_x,
      (U64)(U32)body_y,
      (U64)(U32)body_w,
      (U64)(U32)body_h,
      (U64)(U32)app->layout_key.line_height,
    };
    U64 prepared_key =
      u64_hash_bytes(prepared_key_values, sizeof(prepared_key_values));
    eightvo_image_cache_begin_prepared(&app->image_cache,
                                        prepared_key ? prepared_key : 1);
  }

  if (!eightvo_build_reader_presentation(app,
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
    EightvoPresentationRowMetrics metrics = {0};
    S64 row_bottom = (S64)presentation_row->row_rect.y +
                     (S64)presentation_row->row_rect.h;
    if (!eightvo_resolve_presentation_row_metrics(app, row, &metrics) ||
        presentation_row->row_rect.y < body_y ||
        row_bottom > (S64)body_y + (S64)body_h)
    {
      app->presentation_complete = 0;
      break;
    }
    EpubReaderFrameImage *image = eightvo_image_for_row(&app->frame, row->row);
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
      B32 loaded_image_only =
        image->image_placement ==
          SourceReaderLayoutImagePlacement_ImageOnly &&
        image->status == EpubReaderFrameImageStatus_Loaded &&
        image->pixels;
      if (!loaded_image_only)
      {
        (void)draw_push_rounded_rect(&app->draw_commands,
                                     DrawLayer_World,
                                     image_x,
                                     image_y,
                                     image_w,
                                     image_h,
                                     6,
                                     0x00F1EEE8U,
                                     0x00B8B1A6U);
      }
      if (image->status == EpubReaderFrameImageStatus_Loaded && image->pixels)
      {
        S32 fit_x = 0;
        S32 fit_y = 0;
        S32 fit_w = 0;
        S32 fit_h = 0;
        B32 fitted = eightvo_fit_image_rect(image->src_w,
                                             image->src_h,
                                             image_x,
                                             image_y,
                                             image_w,
                                             image_h,
                                             &fit_x,
                                             &fit_y,
                                             &fit_w,
                                             &fit_h);
        B32 pushed = 0;
        if (fitted)
        {
          EightvoPreparedImage *prepared = 0;
          if (fit_w != image->src_w || fit_h != image->src_h)
          {
            prepared = eightvo_image_cache_prepare(
              &app->image_cache,
              image->pixels,
              image->src_w,
              image->src_h,
              image->src_stride_pixels,
              fit_w,
              fit_h);
          }
          if (prepared)
          {
            pushed = draw_push_sprite_clipped_sampled(
              &app->draw_commands,
              DrawLayer_World,
              prepared->pixels,
              prepared->width,
              prepared->height,
              prepared->width,
              DrawSpriteSampleKind_Nearest,
              fit_x,
              fit_y,
              fit_w,
              fit_h,
              body_x,
              body_y,
              body_w,
              body_h);
          }
          else
          {
            pushed = draw_push_sprite_clipped_sampled(
              &app->draw_commands,
              DrawLayer_World,
              image->pixels,
              image->src_w,
              image->src_h,
              image->src_stride_pixels,
              eightvo_image_sample_kind(image->src_w,
                                         image->src_h,
                                         fit_w,
                                         fit_h),
              fit_x,
              fit_y,
              fit_w,
              fit_h,
              body_x,
              body_y,
              body_w,
              body_h);
          }
        }
        if (!fitted || !pushed)
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
          eightvo_copy_cstr(placeholder, ARRAY_COUNT(placeholder), "Image unavailable");
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
                                     app->reader_content_theme.ink_muted);
      }
      continue;
    }

    if (end > start)
    {
      eightvo_draw_row_highlights(app, row, presentation_row);
      if (!eightvo_draw_reader_styled_row(app,
                                           row,
                                           presentation_row,
                                           start,
                                           end,
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

FUNCTION B32
eightvo_frame_uses_embedded_font_face(const EpubReaderFrame *frame)
{
  if (!frame) return 0;
  for (U32 index = 0; index < frame->style_row_count; index += 1)
  {
    if (frame->style_rows[index].font_face_index !=
        DOC_EMBEDDED_FONT_FACE_INDEX_NONE)
      return 1;
  }
  for (U32 index = 0; index < frame->style_fragment_count; index += 1)
  {
    if (frame->style_fragments[index].font_face_index !=
        DOC_EMBEDDED_FONT_FACE_INDEX_NONE)
      return 1;
  }
  return 0;
}

FUNCTION void
eightvo_invalidate_adjacent_page(EightvoApp *app)
{
  if (!app) return;
  app->adjacent_page_ready = 0;
  app->adjacent_page_visual_key = 0;
  app->adjacent_page_annotation_revision = 0;
  app->adjacent_page_rect = (UI0Rect){0};
}

FUNCTION B32
eightvo_build_adjacent_page_raster(EightvoApp *app)
{
  if (!app || !app->render_ready || !app->adjacent_warm_frame_ready ||
      app->adjacent_frame.image_count != 0 || app->width <= 0 || app->height <= 0)
    return 0;
  U64 pixel_count = (U64)(U32)app->width * (U64)(U32)app->height;
  if (pixel_count == 0 || pixel_count > EightvoAdjacentPagePixelCap)
    return 0;
  if (pixel_count > app->adjacent_page_pixel_cap)
  {
    U32 *pixels = (U32 *)realloc(
      app->adjacent_page_pixels, (size_t)(pixel_count * sizeof(U32)));
    if (!pixels) return 0;
    app->adjacent_page_pixels = pixels;
    app->adjacent_page_pixel_cap = pixel_count;
  }
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, app->adjacent_page_pixels,
                     app->width, app->height, app->width);
  render_buffer_clear(&buffer,
                      eightvo_reader_content_theme(app->theme).page_background);
  render_execute_draw_commands(&app->render_state, &buffer, &app->draw_commands);
  app->adjacent_page_visual_key =
    eightvo_reader_page_visual_key(&app->adjacent_frame);
  app->adjacent_page_annotation_revision = app->annotation_revision;
  app->adjacent_page_rect = app->reader_content_geometry.page_surface_rect;
  app->adjacent_page_buffer_width = app->width;
  app->adjacent_page_buffer_height = app->height;
  app->adjacent_page_font_family = app->font_family;
  app->adjacent_page_embedded_fonts_enabled =
    app->layout_key.embedded_fonts_enabled;
  app->adjacent_page_text_size_index = app->text_size_index;
  app->adjacent_page_line_spacing_index = app->line_spacing_index;
  app->adjacent_page_theme = app->theme;
  app->adjacent_page_ready = 1;
  return 1;
}

FUNCTION void
eightvo_cancel_location_warm(EightvoApp *app)
{
  if (!app) return;
  if (app->window && app->location_warm_pending)
    (void)KillTimer(app->window, EightvoLocationWarmTimerId);
  app->location_warm_pending = 0;
}

FUNCTION void
eightvo_schedule_location_warm(EightvoApp *app)
{
  if (!app || !app->window || !epub_reader_is_open(&app->reader) ||
      (app->reader.location_cache_complete &&
       app->reader.location_cache_valid))
  {
    eightvo_cancel_location_warm(app);
    return;
  }
  app->location_warm_pending = 1;
  if (SetTimer(app->window, EightvoLocationWarmTimerId,
               EightvoLocationWarmDelayMs, 0) == 0)
  {
    app->location_warm_pending = 0;
  }
}

FUNCTION B32
eightvo_location_warm_step(EightvoApp *app)
{
  if (app && app->first_reader_present_pending) return 0;
  if (!app || !app->location_warm_pending ||
      !epub_reader_is_open(&app->reader))
  {
    eightvo_cancel_location_warm(app);
    return 0;
  }
  U32 before = app->reader.location_next_spine_index;
  (void)epub_reader_location_cache_ensure(&app->reader);
  B32 complete = app->reader.location_cache_complete &&
                 app->reader.location_cache_valid;
  B32 progressed = app->reader.location_next_spine_index > before;
  if (complete || !progressed)
    eightvo_cancel_location_warm(app);
  return progressed;
}

FUNCTION void
eightvo_cancel_adjacent_warm(EightvoApp *app)
{
  if (!app) return;
  if (app->window && app->adjacent_warm_pending)
    (void)KillTimer(app->window, EightvoAdjacentWarmTimerId);
  app->adjacent_warm_pending = 0;
  app->adjacent_warm_frame_ready = 0;
  app->adjacent_warm_next_text_command = 0;
  app->adjacent_warm_distance = 0;
  app->adjacent_warm_completed_page_count = 0;
  app->adjacent_warm_direction = 0;
  app->adjacent_warm_source_spine_index = 0;
  app->adjacent_warm_source_first_byte = 0;
  MemoryZeroStruct(&app->adjacent_frame);
}

FUNCTION void
eightvo_schedule_adjacent_warm(EightvoApp *app)
{
  if (!app || !app->render_ready || !app->adjacent_frame_storage ||
      !epub_reader_is_open(&app->reader) || !app->reader.has_current_page)
  {
    eightvo_cancel_adjacent_warm(app);
    return;
  }
  if (app->page_repeat_active || app->page_action_waiting_for_present ||
      app->page_action_pending)
  {
    eightvo_cancel_adjacent_warm(app);
    return;
  }
  if (app->window && app->adjacent_warm_pending)
    (void)KillTimer(app->window, EightvoAdjacentWarmTimerId);
  app->adjacent_warm_pending = 1;
  app->adjacent_warm_frame_ready = 0;
  app->adjacent_warm_next_text_command = 0;
  app->adjacent_warm_distance = 1;
  app->adjacent_warm_completed_page_count = 0;
  app->adjacent_warm_direction = 1;
  app->adjacent_warm_source_spine_index = app->reader.current_page.spine_index;
  app->adjacent_warm_source_first_byte = app->reader.current_page.first_byte;
  MemoryZeroStruct(&app->adjacent_frame);
  if (app->window && !app->page_repeat_active &&
      !app->first_reader_present_pending)
  {
    if (SetTimer(app->window, EightvoAdjacentWarmTimerId,
                 EightvoAdjacentWarmDelayMs, 0) == 0)
      eightvo_cancel_adjacent_warm(app);
  }
}

FUNCTION B32
eightvo_adjacent_warm_step(EightvoApp *app)
{
  /*
  A due timer must never let speculative adjacent-page work get ahead of the
  first visible reader frame. The normal path does not arm that timer until
  the first stable presentation, and this guard also rejects a stale queued
  timer message.
  */
  if (app && app->first_reader_present_pending) return 0;
  if (!app || !app->adjacent_warm_pending || !app->render_ready ||
      !app->adjacent_frame_storage || !epub_reader_is_open(&app->reader) ||
      !app->reader.has_current_page ||
      app->reader.current_page.spine_index !=
        app->adjacent_warm_source_spine_index ||
      app->reader.current_page.first_byte !=
        app->adjacent_warm_source_first_byte)
  {
    eightvo_cancel_adjacent_warm(app);
    return 0;
  }
  if (app->reader_content_geometry.content_rect.w <= 0 ||
      app->reader_content_geometry.content_rect.h <= 0)
    return 0;
  if (!app->adjacent_warm_frame_ready)
  {
    SourceReaderPageRange target = {0};
    B32 has_target = 0;
    if (app->adjacent_warm_direction > 0)
    {
      has_target = epub_reader_forward_page_range(
        &app->reader, app->adjacent_warm_distance, &target);
    }
    if (!has_target && app->adjacent_warm_distance == 1)
    {
      EpubReaderNavigationPrepareResult prepare_result = {0};
      (void)epub_reader_prepare_navigation(
        &app->reader,
        app->layout_key,
        app->layout_config,
        (EpubReaderNavigationPrepareOptions){
          .require_page_move = app->reader.navigation_stats.page_move_count != 0,
        },
        &prepare_result);
      if (app->adjacent_warm_direction < 0 && prepare_result.prepared)
      {
        SourceReaderPageRange current = app->reader.current_page;
        target = prepare_result.page;
        has_target =
          target.first_byte < target.one_past_last_byte &&
          (target.spine_index < current.spine_index ||
           (target.spine_index == current.spine_index &&
            target.first_byte < current.first_byte &&
            target.one_past_last_byte <= current.first_byte));
      }
      else if (app->adjacent_warm_direction > 0)
      {
        has_target = epub_reader_forward_page_range(&app->reader, 1, &target);
      }
    }
    if (!has_target ||
        (app->adjacent_warm_direction < 0 &&
         app->adjacent_warm_distance > 1) ||
        (target.spine_index != app->reader.active_spine_index &&
         app->adjacent_warm_distance > 1) ||
        target.first_byte >= target.one_past_last_byte)
    {
      if (app->window)
        (void)KillTimer(app->window, EightvoAdjacentWarmTimerId);
      app->adjacent_warm_pending = 0;
      return 0;
    }

    B32 built = epub_reader_build_page_frame(&app->reader,
                                             target,
                                             app->adjacent_frame_storage,
                                             &app->adjacent_frame);
    if (!built || !app->adjacent_frame.ready ||
        !app->adjacent_frame.document_open)
    {
      eightvo_cancel_adjacent_warm(app);
      return 0;
    }
    app->adjacent_warm_frame_ready = 1;
    if (app->adjacent_frame.spine_index !=
          app->adjacent_warm_source_spine_index &&
        eightvo_frame_uses_embedded_font_face(&app->adjacent_frame))
    {
      app->adjacent_warm_completed_page_count += 1;
      if (app->window)
        (void)KillTimer(app->window, EightvoAdjacentWarmTimerId);
      app->adjacent_warm_pending = 0;
      app->adjacent_warm_frame_ready = 0;
      app->adjacent_warm_next_text_command = 0;
      MemoryZeroStruct(&app->adjacent_frame);
      return 0;
    }
  }

  EpubReaderFrame saved_frame = app->frame;
  B32 saved_adjacent_page_ready = app->adjacent_page_ready;
  B32 saved_presentation_complete = app->presentation_complete;
  U64 saved_presentation_hash = app->presentation_hash;
  app->frame = app->adjacent_frame;
  app->adjacent_page_ready = 0;
  draw_command_buffer_begin(&app->draw_commands);
  app->presentation_hash = 0;
  app->presentation_complete = 1;
  eightvo_draw_reader_page(app);
  B32 adjacent_presentation_complete = app->presentation_complete;
  app->frame = saved_frame;
  app->adjacent_page_ready = saved_adjacent_page_ready;
  UI0Rect current_content = app->reader_content_geometry.content_rect;
  B32 current_presentation_restored =
    eightvo_build_reader_presentation(app,
                                       current_content.x,
                                       current_content.y,
                                       current_content.w,
                                       current_content.h);
  B32 current_presentation_matches =
    current_presentation_restored &&
    (saved_presentation_hash == 0 ||
     app->presentation_hash == saved_presentation_hash);
  app->presentation_complete =
    saved_presentation_complete && current_presentation_matches;
  if (!current_presentation_matches)
  {
    eightvo_cancel_adjacent_warm(app);
    return 0;
  }
  if (app->draw_commands.overflow_count != 0)
  {
    eightvo_cancel_adjacent_warm(app);
    return 0;
  }

  U32 shaped_count = 0;
  for (U32 layer = 0; layer < DrawLayer_Count; layer += 1)
  {
    for (U32 index = 0;
         index < app->draw_commands.command_count[layer];
         index += 1)
    {
      const DrawCommand *command = app->draw_commands.commands[layer] + index;
      if (command->type == DrawCommandType_Text &&
          (command->v.text.flags & DrawTextFlag_Shaped))
        shaped_count += 1;
    }
  }

  U32 shaped_index = 0;
  U32 warmed_count = 0;
  U32 warm_text_budget = EightvoAdjacentWarmTextBudget;
  B32 warm_budget_exhausted = 0;
  U64 warm_start_ticks = os_time_ticks();
  U64 warm_frequency = os_time_frequency();
  U64 warm_budget_us = app->reader.navigation_stats.page_move_count == 0 ?
    EightvoAdjacentWarmFirstOpenBudgetUs : EightvoAdjacentWarmIdleBudgetUs;
  for (U32 layer = 0; layer < DrawLayer_Count; layer += 1)
  {
    for (U32 index = 0;
         index < app->draw_commands.command_count[layer];
         index += 1)
    {
      const DrawCommand *command = app->draw_commands.commands[layer] + index;
      if (command->type != DrawCommandType_Text ||
          !(command->v.text.flags & DrawTextFlag_Shaped))
        continue;
      if (shaped_index++ < app->adjacent_warm_next_text_command) continue;
      if (warmed_count >= warm_text_budget)
      {
        warm_budget_exhausted = 1;
        break;
      }
      if (warmed_count > 0 && warm_frequency > 0)
      {
        U64 elapsed_ticks = os_time_ticks() - warm_start_ticks;
        U64 elapsed_us = elapsed_ticks > UINT64_MAX / 1000000ULL ?
          UINT64_MAX : elapsed_ticks * 1000000ULL / warm_frequency;
        if (elapsed_us >= warm_budget_us)
        {
          warm_budget_exhausted = 1;
          break;
        }
      }
      const DrawTextCommand *text = &command->v.text;
      FontTag tag = text->font_tag;
      const FontProvider *provider = font_cache_provider_from_tag(
        &app->render_state.text_cache, tag);
      if (!provider) provider = app->render_state.text_provider;
      if (provider && provider->raster_text)
      {
        S32 pixel_height = text->scale;
        if (pixel_height < 8)
          pixel_height = provider->default_pixel_height ?
            provider->default_pixel_height : 14;
        FontRasterFlags flags = text->raster_flags ? text->raster_flags :
          (FontRasterFlag_Smooth | FontRasterFlag_Hinted);
        const FontGlyphAlphaBitmap *bitmap = 0;
        const FontShapedText *shape = 0;
        (void)font_cache_raster_shaped_text(
          &app->render_state.text_cache, tag, pixel_height, flags,
          str8_from_cstr(text->text), &bitmap, &shape, 0);
      }
      app->adjacent_warm_next_text_command += 1;
      warmed_count += 1;
    }
    if (warm_budget_exhausted) break;
  }

  if (app->adjacent_warm_next_text_command >= shaped_count)
  {
    B32 crossed_spine =
      app->adjacent_frame.spine_index != app->adjacent_warm_source_spine_index;
    if (app->adjacent_warm_distance == 1 &&
        adjacent_presentation_complete)
    {
      (void)eightvo_build_adjacent_page_raster(app);
    }
    app->adjacent_warm_completed_page_count += 1;
    app->adjacent_warm_distance += 1;
    app->adjacent_warm_frame_ready = 0;
    app->adjacent_warm_next_text_command = 0;
    MemoryZeroStruct(&app->adjacent_frame);
    if (app->page_repeat_active || crossed_spine ||
        app->adjacent_warm_distance > EightvoAdjacentWarmPageCap)
    {
      if (app->window)
        (void)KillTimer(app->window, EightvoAdjacentWarmTimerId);
      app->adjacent_warm_pending = 0;
    }
  }
  return warmed_count > 0 || shaped_count == 0;
}

FUNCTION void
eightvo_reset_input(EightvoApp *app)
{
  if (!app) { return; }
  app->input.pointer_pressed = 0;
  app->input.pointer_released = 0;
  app->input.pointer_selection_release = 0;
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
eightvo_render_to_buffer(EightvoApp *app, RenderBuffer *buffer)
{
  if (!app || !buffer || !buffer->pixels || !app->render_ready) { return; }
  app->last_render_reader_view_ticks = 0;
  app->last_render_reader_page_ticks = 0;
  app->last_render_ui_adapt_ticks = 0;
  app->last_render_execute_ticks = 0;
  app->presentation_complete = 1;
  app->adjacent_page_cache_used_last_render = 0;
  U32 canvas_color = eightvo_reader_content_theme(app->theme).page_background;
  render_buffer_clear(buffer, canvas_color);
  draw_command_buffer_begin(&app->draw_commands);
  if (eightvo_library_active(app))
  {
    app->reader_view_ready = 0;
    app->reader_view_frame.semantic_node_count = 0;
    app->reader_view_frame.action_count = 0;
    app->reader_view_frame.change_flags = ReaderViewFrameChange_None;
    eightvo_library_resolve_layout(app);
    eightvo_update_host_control_records(app);
    eightvo_draw_library(app);
    U64 execute_start = os_time_ticks();
    render_execute_draw_commands(&app->render_state, buffer,
                                  &app->draw_commands);
    app->last_render_execute_ticks = os_time_ticks() - execute_start;
    eightvo_reset_input(app);
    return;
  }
  U64 reader_view_start = os_time_ticks();
  (void)eightvo_build_reader_view(app);
  eightvo_update_host_control_records(app);
  app->last_render_reader_view_ticks =
    os_time_ticks() - reader_view_start;
  U64 reader_page_start = os_time_ticks();
  eightvo_draw_reader_page(app);
  app->last_render_reader_page_ticks =
    os_time_ticks() - reader_page_start;
  U64 ui_adapt_start = os_time_ticks();
  if (app->reader_view_ready)
  {
    eightvo_adapt_ui0_draw(app);
    eightvo_draw_host_exit_slot(app);
  }
  app->last_render_ui_adapt_ticks = os_time_ticks() - ui_adapt_start;
  U64 execute_start = os_time_ticks();
  render_execute_draw_commands(&app->render_state, buffer, &app->draw_commands);
  app->last_render_execute_ticks = os_time_ticks() - execute_start;
  eightvo_reset_input(app);
}

FUNCTION B32
eightvo_frame_presentation_is_complete(const EightvoApp *app)
{
  if (!app || !app->presentation_complete ||
      app->draw_commands.overflow_count != 0)
  {
    return 0;
  }
  if (epub_reader_is_open(&app->reader) &&
      (!app->reader_view_ready ||
       app->reader_view_frame.error_flags != ReaderViewFrameError_None))
  {
    return 0;
  }
  return 1;
}

FUNCTION B32
eightvo_render(EightvoApp *app)
{
  if (!app || !app->gfx_ready || !app->render_ready) { return 0; }
  app->last_present_complete = 0;
  app->last_render_acquire_ticks = 0;
  app->last_render_buffer_ticks = 0;
  app->last_render_accessibility_ticks = 0;
  app->last_render_present_ticks = 0;
  if (app->page_action_waiting_for_present &&
      app->page_action_expected_identity.kind ==
        EightvoPresentationIdentity_Page)
  {
    EightvoPresentationIdentity current_frame_identity = {0};
    if (!eightvo_capture_rendered_presentation_identity(
          app, &current_frame_identity) ||
        !eightvo_presentation_identity_equal(
          app->page_action_expected_identity, current_frame_identity))
    {
      if (eightvo_capture_frame(app) &&
          app->capture_frame_recovery_count < UINT32_MAX)
        app->capture_frame_recovery_count += 1;
    }
  }
  OS_GfxSurface surface = {0};
  U64 acquire_start = os_time_ticks();
  B32 acquired = os_gfx_acquire_surface(&app->gfx, &surface);
  app->last_render_acquire_ticks = os_time_ticks() - acquire_start;
  if (!acquired) { return 0; }
  app->width = surface.width;
  app->height = surface.height;
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer,
                     surface.pixels,
                     surface.width,
                     surface.height,
                     surface.stride_pixels);
  U64 render_start = os_time_ticks();
  eightvo_render_to_buffer(app, &buffer);
  app->last_render_buffer_ticks = os_time_ticks() - render_start;
  EightvoPresentationIdentity rendered_identity = {0};
  B32 rendered_identity_valid =
    eightvo_capture_rendered_presentation_identity(app,
                                                     &rendered_identity);
  U64 accessibility_start = os_time_ticks();
  if (app->accessibility)
    eightvo_accessibility_publish_frame(app->accessibility,
                                         &app->reader_view_frame);
  app->last_render_accessibility_ticks =
    os_time_ticks() - accessibility_start;
  B32 frame_complete = eightvo_frame_presentation_is_complete(app);
  U64 present_start = os_time_ticks();
  B32 presented = os_gfx_present_surface(&app->gfx, &surface);
  app->last_render_present_ticks = os_time_ticks() - present_start;
  app->last_present_complete = frame_complete && presented;
  if (app->last_present_complete && rendered_identity_valid)
    app->last_surface_identity = rendered_identity;
  return app->last_present_complete;
}

FUNCTION void
eightvo_note_stable_presentation(EightvoApp *app,
                                  B32 presented_complete,
                                  B32 followup_frame_required)
{
  if (!app) return;
  app->last_present_complete =
    presented_complete && !followup_frame_required;
  if (app->last_present_complete &&
      app->complete_present_sequence < UINT64_MAX)
  {
    app->complete_present_sequence += 1;
  }
  if (app->last_present_complete && app->first_reader_present_pending &&
      epub_reader_is_open(&app->reader))
  {
    app->first_reader_present_pending = 0;
    eightvo_schedule_location_warm(app);
    if (app->window && app->adjacent_warm_pending &&
        !app->page_repeat_active &&
        SetTimer(app->window, EightvoAdjacentWarmTimerId,
                 EightvoAdjacentWarmDelayMs, 0) == 0)
    {
      eightvo_cancel_adjacent_warm(app);
    }
  }
}

FUNCTION B32
eightvo_win32_page_repeat_frame(EightvoApp *app,
                                  U64 now_ticks,
                                  EightvoPageRepeatFrameTiming *out_timing)
{
  if (out_timing) MemoryZeroStruct(out_timing);
  if (!app || !app->window || !app->page_repeat_active) return 0;
  U64 present_sequence_before = app->complete_present_sequence;
  U64 action_prepare_start = os_time_ticks();
  EightvoPageRepeatFrameResult frame_result =
    eightvo_page_repeat_frame_step(app, now_ticks);
  U64 action_prepare_end = os_time_ticks();
  if (out_timing)
  {
    out_timing->action_prepare_ticks =
      action_prepare_end - action_prepare_start;
    if (frame_result.action_emitted)
      out_timing->action_emitted_ticks =
        app->page_repeat_last_action_emitted_ticks;
  }
  if (!app->page_repeat_active) return 0;

  B32 update_pending = GetUpdateRect(app->window, 0, FALSE) != 0;
  if (app->page_action_waiting_for_present &&
      app->page_action_presentation_retry_attempt > 0 && !update_pending)
  {
    return 0;
  }
  if (app->page_action_waiting_for_present || update_pending ||
      !app->last_present_complete)
  {
    if (!update_pending)
      (void)InvalidateRect(app->window, 0, FALSE);
    (void)UpdateWindow(app->window);
  }
  B32 complete =
    app->complete_present_sequence > present_sequence_before;
  if (out_timing)
  {
    out_timing->render_acquire_ticks = app->last_render_acquire_ticks;
    out_timing->render_buffer_ticks = app->last_render_buffer_ticks;
    out_timing->render_accessibility_ticks =
      app->last_render_accessibility_ticks;
    out_timing->render_present_ticks = app->last_render_present_ticks;
  }
  return complete;
}

FUNCTION B32
eightvo_write_bmp(const char *path, const U32 *pixels, S32 width, S32 height)
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

FUNCTION int
eightvo_run_library_smoke(const char *epub_path, const char *output_prefix)
{
  enum { Width = 1100, Height = 760, CompactWidth = 520, CompactHeight = 720 };
  if (!epub_path || !epub_path[0] || !output_prefix || !output_prefix[0])
    return 2;
  char output_directory[EightvoPathCap] = {0};
  char catalog_path[EightvoPathCap] = {0};
  char state_path[EightvoPathCap] = {0};
  char settings_path[EightvoPathCap] = {0};
  char empty_bmp[EightvoPathCap] = {0};
  char populated_bmp[EightvoPathCap] = {0};
  char restart_bmp[EightvoPathCap] = {0};
  char restart_repeat_bmp[EightvoPathCap] = {0};
  char compact_bmp[EightvoPathCap] = {0};
  char missing_bmp[EightvoPathCap] = {0};
  char hover_bmp[EightvoPathCap] = {0};
  char pressed_bmp[EightvoPathCap] = {0};
  char missing_path[EightvoPathCap] = {0};
  eightvo_copy_cstr(output_directory, ARRAY_COUNT(output_directory),
                     output_prefix);
  char *slash = strrchr(output_directory, '\\');
  char *forward = strrchr(output_directory, '/');
  if (!slash || (forward && forward > slash)) slash = forward;
  if (slash) *slash = 0;
  else eightvo_copy_cstr(output_directory,
                          ARRAY_COUNT(output_directory), ".");
  if (!os_make_directory_chain(output_directory) ||
      cstr_format(catalog_path, ARRAY_COUNT(catalog_path),
                  "%s_catalog.v1", output_prefix) == 0 ||
      cstr_format(state_path, ARRAY_COUNT(state_path),
                  "%s_state.v1", output_prefix) == 0 ||
      cstr_format(settings_path, ARRAY_COUNT(settings_path),
                  "%s_settings.v1", output_prefix) == 0 ||
      cstr_format(empty_bmp, ARRAY_COUNT(empty_bmp),
                  "%s_empty.bmp", output_prefix) == 0 ||
      cstr_format(populated_bmp, ARRAY_COUNT(populated_bmp),
                  "%s_populated.bmp", output_prefix) == 0 ||
      cstr_format(restart_bmp, ARRAY_COUNT(restart_bmp),
                  "%s_restart.bmp", output_prefix) == 0 ||
      cstr_format(restart_repeat_bmp, ARRAY_COUNT(restart_repeat_bmp),
                  "%s_restart_repeat.bmp", output_prefix) == 0 ||
      cstr_format(compact_bmp, ARRAY_COUNT(compact_bmp),
                  "%s_compact.bmp", output_prefix) == 0 ||
      cstr_format(missing_bmp, ARRAY_COUNT(missing_bmp),
                   "%s_missing.bmp", output_prefix) == 0 ||
      cstr_format(hover_bmp, ARRAY_COUNT(hover_bmp),
                  "%s_hover.bmp", output_prefix) == 0 ||
      cstr_format(pressed_bmp, ARRAY_COUNT(pressed_bmp),
                  "%s_pressed.bmp", output_prefix) == 0 ||
      cstr_format(missing_path, ARRAY_COUNT(missing_path),
                  "%s_missing_source.epub", output_prefix) == 0)
    return 2;
  (void)os_file_delete(catalog_path);
  (void)os_file_delete(state_path);
  (void)os_file_delete(settings_path);

  EightvoLibraryCatalog *ordering =
    (EightvoLibraryCatalog *)calloc(1, sizeof(*ordering));
  EightvoApp *migration = (EightvoApp *)calloc(1, sizeof(*migration));
  if (!ordering || !migration)
  {
    free(ordering);
    free(migration);
    return 1;
  }
  ordering->entry_count = 2;
  ordering->entries[0].entry_id = 1;
  ordering->entries[0].last_opened_time = 10;
  eightvo_copy_cstr(ordering->entries[0].title,
                     ARRAY_COUNT(ordering->entries[0].title), "Older");
  ordering->entries[1].entry_id = 2;
  ordering->entries[1].last_opened_time = 20;
  eightvo_copy_cstr(ordering->entries[1].title,
                     ARRAY_COUNT(ordering->entries[1].title), "Newer");
  eightvo_library_catalog_sort(ordering);
  eightvo_library_catalog_init(&migration->library);
  migration->saved.valid = 1;
  migration->saved.spine_index = 3;
  migration->saved.byte_offset = 47;
  eightvo_copy_cstr(migration->saved.path,
                     ARRAY_COUNT(migration->saved.path), epub_path);
  eightvo_migrate_saved_state_to_library(migration);
  B32 ordering_valid = ordering->entries[0].entry_id == 2 &&
                       ordering->entries[1].entry_id == 1;
  B32 migration_valid = migration->library.entry_count == 1 &&
    migration->library.entries[0].progress_spine_index == 3 &&
    migration->library.entries[0].progress_byte_offset == 47;
  free(ordering);
  free(migration);
  if (!ordering_valid || !migration_valid) return 1;

  U32 *pixels = (U32 *)calloc((size_t)Width * Height, sizeof(U32));
  EightvoApp app = {0};
  RenderBuffer buffer = {0};
  if (!pixels || !eightvo_app_init(&app, Width, Height, 1, 0))
    goto fail;
  app.persistence_enabled = 1;
  eightvo_copy_cstr(app.app_directory, ARRAY_COUNT(app.app_directory),
                     output_directory);
  eightvo_copy_cstr(app.catalog_path, ARRAY_COUNT(app.catalog_path),
                     catalog_path);
  eightvo_copy_cstr(app.state_path, ARRAY_COUNT(app.state_path), state_path);
  eightvo_copy_cstr(app.settings_path, ARRAY_COUNT(app.settings_path),
                     settings_path);
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);
  if (!eightvo_library_active(&app) || app.library.entry_count != 0 ||
      app.host_control_count != 1 ||
      app.host_controls[0].action != EightvoHostControlAction_AddBooks ||
      !eightvo_write_bmp(empty_bmp, pixels, Width, Height))
    goto fail;

  app.saved.valid = 1;
  app.saved.spine_index = 3;
  app.saved.byte_offset = 47;
  eightvo_copy_cstr(app.saved.path, ARRAY_COUNT(app.saved.path), epub_path);
  eightvo_migrate_saved_state_to_library(&app);
  app.library_selected_entry_id = app.library.entries[0].entry_id;
  eightvo_library_hydrate_startup_entry(&app);
  EightvoLibraryEntry *hydrated = app.library.entries;
  EightvoLibraryThumbnail *hydrated_thumbnail =
    eightvo_library_thumbnail_load(&app, hydrated);
  if (epub_reader_is_open(&app.reader) || !hydrated_thumbnail ||
      app.library_metadata_refresh_count != 1 ||
      !(hydrated->metadata_flags & EightvoLibraryMetadata_Inspected) ||
      !(hydrated->metadata_flags & EightvoLibraryMetadata_Title) ||
      !(hydrated->metadata_flags & EightvoLibraryMetadata_Author) ||
      !(hydrated->metadata_flags & EightvoLibraryMetadata_Cover))
    goto fail;

  if (!eightvo_open_path(&app, epub_path) || app.library.entry_count != 1 ||
      app.library_metadata_refresh_count != 1 ||
      !epub_reader_is_open(&app.reader))
    goto fail;
  EightvoLibraryEntry *opened = app.library.entries;
  U64 entry_id = opened->entry_id;
  if (!opened->title[0] || !opened->author[0] ||
      !(opened->metadata_flags & EightvoLibraryMetadata_Title) ||
      !(opened->metadata_flags & EightvoLibraryMetadata_Author) ||
      !(opened->metadata_flags & EightvoLibraryMetadata_Cover) ||
      opened->digest_algorithm != EightvoLibraryDigest_None)
    goto fail;
  (void)eightvo_move_page(&app, 1);
  if (!eightvo_close_book(&app) || !eightvo_library_active(&app) ||
      app.library.entry_count != 1)
    goto fail;
  if (buffer.pixels != pixels || buffer.width != Width ||
      buffer.height != Height || buffer.stride_pixels != Width)
    goto fail;
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);
  if (!eightvo_write_bmp(populated_bmp, pixels, Width, Height))
    goto fail;
  EightvoLibraryEntry *closed =
    eightvo_library_catalog_find_id(&app.library, entry_id);
  EightvoLibraryThumbnail *thumbnail = closed ?
    eightvo_library_thumbnail_find(&app.library_thumbnail_cache,
                                    closed->entry_id,
                                    closed->file_size,
                                    closed->file_modified_time) : 0;
  if (!closed || !thumbnail || closed->runtime_missing ||
      app.host_control_count < 2 ||
      !eightvo_save_library(&app))
    goto fail;
  eightvo_app_release(&app);

  if (!eightvo_app_init(&app, Width, Height, 1, 0)) goto fail;
  app.persistence_enabled = 1;
  eightvo_copy_cstr(app.app_directory, ARRAY_COUNT(app.app_directory),
                     output_directory);
  eightvo_copy_cstr(app.catalog_path, ARRAY_COUNT(app.catalog_path),
                     catalog_path);
  eightvo_copy_cstr(app.state_path, ARRAY_COUNT(app.state_path), state_path);
  eightvo_copy_cstr(app.settings_path, ARRAY_COUNT(app.settings_path),
                     settings_path);
  if (!eightvo_library_catalog_load(&app.library, catalog_path) ||
      app.library.entry_count != 1)
    goto fail;
  app.library_selected_entry_id = app.library.entries[0].entry_id;
  eightvo_library_set_summary_status(&app);
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);
  U64 restart_hash = u64_hash_bytes(pixels,
                                    (U64)Width * Height * sizeof(U32));
  if (!eightvo_library_active(&app) || !app.library.entries[0].title[0] ||
      app.library_metadata_refresh_count != 0 ||
      !eightvo_write_bmp(restart_bmp, pixels, Width, Height))
    goto fail;
  eightvo_render_to_buffer(&app, &buffer);
  U64 restart_repeat_hash = u64_hash_bytes(
    pixels, (U64)Width * Height * sizeof(U32));
  if (restart_repeat_hash != restart_hash ||
      !eightvo_write_bmp(restart_repeat_bmp, pixels, Width, Height))
    goto fail;

  app.width = CompactWidth;
  app.height = CompactHeight;
  render_buffer_init(&buffer, pixels,
                     CompactWidth, CompactHeight, CompactWidth);
  eightvo_render_to_buffer(&app, &buffer);
  if (app.library_column_count != 3 || app.library_card_count != 1 ||
      !eightvo_write_bmp(compact_bmp, pixels, CompactWidth, CompactHeight))
    goto fail;
  app.width = Width;
  app.height = Height;
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);
  EightvoHostControlRecord *book_control =
    eightvo_host_control_record(&app, EightvoHostControl_LibraryBookBase);
  if (!book_control) goto fail;
  S32 book_x = book_control->semantic.rect.x + book_control->semantic.rect.w / 2;
  S32 book_y = book_control->semantic.rect.y + book_control->semantic.rect.h / 2;
  U64 idle_hash = u64_hash_bytes(pixels,
                                 (U64)Width * Height * sizeof(U32));
  eightvo_host_pointer_move(&app, book_x, book_y);
  eightvo_render_to_buffer(&app, &buffer);
  U64 hover_hash = u64_hash_bytes(pixels,
                                  (U64)Width * Height * sizeof(U32));
  if (!eightvo_write_bmp(hover_bmp, pixels, Width, Height)) goto fail;
  eightvo_host_pointer_press(&app, book_x, book_y);
  eightvo_render_to_buffer(&app, &buffer);
  U64 pressed_hash = u64_hash_bytes(pixels,
                                    (U64)Width * Height * sizeof(U32));
  if (!eightvo_write_bmp(pressed_bmp, pixels, Width, Height)) goto fail;
  if (idle_hash == hover_hash || hover_hash == pressed_hash ||
      idle_hash == pressed_hash)
    goto fail;
  eightvo_host_pointer_move(&app, Width - 1, Height - 1);
  if (eightvo_host_pointer_release(&app, book_x, book_y) ||
      !eightvo_library_active(&app))
    goto fail;
  eightvo_host_pointer_press(&app, book_x, book_y);
  if (!eightvo_host_pointer_release(&app, book_x, book_y) ||
      !epub_reader_is_open(&app.reader) ||
      app.library_metadata_refresh_count != 0 ||
      app.host_focus_control != EightvoHostControl_None)
    goto fail;
  U32 card_open_spine = app.reader.active_spine_index;
  U64 card_open_byte = app.reader.view_byte_offset;
  if (eightvo_reader_view_route_keydown(&app, VK_RIGHT, 0) !=
        EightvoReaderKeyRoute_Handled ||
      (app.reader.active_spine_index == card_open_spine &&
       app.reader.view_byte_offset == card_open_byte) ||
      eightvo_reader_view_route_keydown(&app, VK_LEFT, 0) !=
        EightvoReaderKeyRoute_Handled ||
      app.reader.active_spine_index != card_open_spine ||
      app.reader.view_byte_offset != card_open_byte)
    goto fail;
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);
  if (!eightvo_host_control_invoke(&app, EightvoHostControl_CloseBook))
    goto fail;
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);
  if (!eightvo_host_focus_set(&app, EightvoHostControl_LibraryBookBase, 1) ||
      !eightvo_host_keyboard_activate(&app) ||
      app.library_metadata_refresh_count != 0 ||
      !epub_reader_is_open(&app.reader))
    goto fail;
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);
  if (!eightvo_host_control_invoke(&app, EightvoHostControl_CloseBook) ||
      !eightvo_library_active(&app) || app.library.entry_count != 1)
    goto fail;
  render_buffer_init(&buffer, pixels, Width, Height, Width);

  eightvo_copy_cstr(app.library.entries[0].source_path,
                     ARRAY_COUNT(app.library.entries[0].source_path),
                     missing_path);
  eightvo_library_catalog_refresh_missing(&app.library);
  eightvo_library_set_summary_status(&app);
  eightvo_render_to_buffer(&app, &buffer);
  B32 has_locate = 0;
  B32 has_remove = 0;
  for (U32 index = 0; index < app.host_control_count; index += 1)
  {
    has_locate |= app.host_controls[index].action ==
                  EightvoHostControlAction_LocateBook;
    has_remove |= app.host_controls[index].action ==
                  EightvoHostControlAction_RemoveBook;
  }
  B32 source_before_remove = os_file_exists(epub_path);
  if (!app.library.entries[0].runtime_missing || !has_locate || !has_remove ||
      !eightvo_write_bmp(missing_bmp, pixels, Width, Height) ||
      !eightvo_library_remove_entry(&app, entry_id) ||
      app.library.entry_count != 0 || !source_before_remove ||
      !os_file_exists(epub_path))
    goto fail;

  fprintf(stdout,
          "eightvo_library_smoke result=pass catalog=bounded_atomic_v1 entries=1 ordering=mru migration=legacy_state metadata=title_author cover=first_library_frame_reused_on_open thumbnail=area_v2 progress=canonical close=library restart=persisted repeat_hash=%016llx interaction=pointer_keyboard_card_open_arrow states=idle_hover_pressed missing=locate_remove remove=source_preserved responsive=wide_and_compact accessibility=host_semantics digest=reserved_none empty_bmp=%s populated_bmp=%s restart_bmp=%s restart_repeat_bmp=%s compact_bmp=%s missing_bmp=%s hover_bmp=%s pressed_bmp=%s\n",
          (unsigned long long)restart_hash, empty_bmp, populated_bmp,
          restart_bmp, restart_repeat_bmp, compact_bmp, missing_bmp,
          hover_bmp, pressed_bmp);
  eightvo_app_release(&app);
  free(pixels);
  return 0;

fail:
  fprintf(stderr,
          "eightvo_library_smoke result=fail entries=%u open=%d library=%d status=%s\n",
          app.library.entry_count, epub_reader_is_open(&app.reader),
          eightvo_library_active(&app), app.status);
  eightvo_app_release(&app);
  free(pixels);
  return 1;
}

FUNCTION B32
eightvo_write_reader_view_parity(const char *path,
                                  const EightvoApp *app)
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
    "host=eightvo\n"
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
eightvo_configure_reader_view_parity(EightvoApp *app,
                                      const char *theme,
                                      const char *left,
                                      const char *right,
                                      const char *popup,
                                      const char *query,
                                      const char *focus,
                                      const char *annotation_case)
{
  if (!app || !theme || !left || !right || !popup || !query || !focus ||
      !annotation_case)
    return 0;
  B32 theme_found = 0;
  for (U32 index = 0; index < EightvoTheme_Count; index += 1)
  {
    UI0ThemeProfile profile =
      ui0_theme_profile_for_kind((UI0ThemeProfileKind)index);
    if (profile.code && strcmp(theme, profile.code) == 0)
    {
      app->theme = (EightvoTheme)index;
      theme_found = 1;
      break;
    }
  }
  if (!theme_found) return 0;

  return (strcmp(left, "none") == 0 || strcmp(left, "contents") == 0 ||
          strcmp(left, "find") == 0) &&
         (strcmp(right, "closed") == 0 || strcmp(right, "open") == 0 ||
          strcmp(right, "bookmark") == 0) &&
          (strcmp(popup, "none") == 0 || strcmp(popup, "font") == 0 ||
           strcmp(popup, "annotations-filter") == 0 ||
           strcmp(popup, "row-actions") == 0 ||
           strcmp(popup, "note-editor") == 0) &&
         (strcmp(query, "-") == 0 ||
          strlen(query) < READER_VIEW_FIND_QUERY_CAP) &&
          (strcmp(focus, "none") == 0 || strcmp(focus, "exit") == 0 ||
           strcmp(focus, "previous") == 0 ||
           strcmp(focus, "previous-enabled") == 0 ||
           strcmp(focus, "next") == 0) &&
          (strcmp(annotation_case, "none") == 0 ||
           strcmp(annotation_case, "highlight-note") == 0 ||
           strcmp(annotation_case, "row-actions") == 0 ||
           strcmp(annotation_case, "note-editor") == 0) &&
          ((strcmp(annotation_case, "none") == 0 &&
            strcmp(popup, "row-actions") != 0 &&
            strcmp(popup, "note-editor") != 0) ||
           (strcmp(annotation_case, "highlight-note") == 0 &&
            strcmp(right, "open") == 0 && strcmp(popup, "none") == 0) ||
           (strcmp(annotation_case, "row-actions") == 0 &&
            strcmp(right, "open") == 0 &&
            strcmp(popup, "row-actions") == 0) ||
           (strcmp(annotation_case, "note-editor") == 0 &&
            strcmp(right, "open") == 0 &&
            strcmp(popup, "note-editor") == 0));
}

FUNCTION B32
eightvo_seed_reader_view_parity_annotations(EightvoApp *app,
                                              const char *annotation_case)
{
  if (!app || !annotation_case) return 0;
  if (strcmp(annotation_case, "none") == 0) return 1;
  if (app->bookmark_count != 0 || app->highlight_count != 0) return 0;
  DocSelection selection = {
    .spine_index = 0,
    .text_byte_start = 82,
    .text_byte_end = 110,
  };
  if (epub_reader_set_selection(&app->reader, selection) != EpubReaderResult_Ok)
    return 0;
  eightvo_prepare_selected_text(app);
  if (strcmp(app->selected_text, "Alpha reader parity sentence") != 0 ||
      !eightvo_set_highlight_color(app, 2) ||
      !eightvo_save_selection_note(
        app, (ReaderViewText){"Attached parity note", 20}))
    return 0;
  epub_reader_clear_selection(&app->reader);
  app->selection_anchor_rect = (UI0Rect){0};
  eightvo_prepare_reader_view_projection(app);
  return app->highlight_count == 1 && app->highlights[0].id == 1 &&
         app->highlights[0].is_highlight &&
         app->highlights[0].color_index == 2 &&
         !app->highlights[0].starred && !app->highlights[0].note_starred &&
         !app->reader.has_active_selection &&
         strcmp(app->highlights[0].section, "First Light") == 0 &&
         strcmp(app->highlights[0].text,
                "Alpha reader parity sentence") == 0 &&
         strcmp(app->highlights[0].note, "Attached parity note") == 0;
}

FUNCTION B32
eightvo_reader_view_text_equals(ReaderViewText text, const char *literal)
{
  U64 size = literal ? strlen(literal) : 0;
  return literal && text.size >= 0 && (U64)text.size == size &&
         (size == 0 || (text.data && memcmp(text.data, literal, size) == 0));
}

FUNCTION const ReaderViewSemanticNode *
eightvo_reader_view_parity_semantic(const EightvoApp *app,
                                     ReaderViewSemanticRole role,
                                     const char *name)
{
  if (!app || !app->reader_view_frame.semantic_nodes) return 0;
  for (UI0S32 index = 0;
       index < app->reader_view_frame.semantic_node_count;
       index += 1)
  {
    const ReaderViewSemanticNode *node =
      app->reader_view_frame.semantic_nodes + index;
    if (node->role == role &&
        (!name || eightvo_reader_view_text_equals(node->name, name)))
      return node;
  }
  return 0;
}

FUNCTION const ReaderViewSemanticNode *
eightvo_reader_view_parity_control(const EightvoApp *app,
                                    ReaderViewSemanticControl control)
{
  if (!app || !app->reader_view_frame.semantic_nodes ||
      control == ReaderViewSemanticControl_None)
    return 0;
  for (UI0S32 index = 0;
       index < app->reader_view_frame.semantic_node_count;
       index += 1)
  {
    const ReaderViewSemanticNode *node =
      app->reader_view_frame.semantic_nodes + index;
    if (node->control == control) return node;
  }
  return 0;
}

FUNCTION const ReaderViewSemanticNode *
eightvo_reader_view_parity_control_source(
  const EightvoApp *app,
  ReaderViewSemanticControl control,
  ReaderViewKey source_key)
{
  if (!app || !app->reader_view_frame.semantic_nodes ||
      control == ReaderViewSemanticControl_None || source_key == 0)
    return 0;
  for (UI0S32 index = 0;
       index < app->reader_view_frame.semantic_node_count;
       index += 1)
  {
    const ReaderViewSemanticNode *node =
      app->reader_view_frame.semantic_nodes + index;
    if (node->control == control && node->source_key == source_key)
      return node;
  }
  return 0;
}

FUNCTION ReaderViewKey
eightvo_reader_view_parity_right_key(const EightvoApp *app,
                                      ReaderViewRightRowKind kind)
{
  if (!app) return 0;
  for (UI0S32 index = 0;
       index < app->reader_view_projection.right.row_count;
       index += 1)
    if (app->reader_view_projection.right.rows[index].kind == kind)
      return app->reader_view_projection.right.rows[index].key;
  return 0;
}

FUNCTION B32
eightvo_reader_view_parity_click_node(
  EightvoApp *app,
  RenderBuffer *buffer,
  const ReaderViewSemanticNode *node)
{
  if (!node || node->rect.w <= 0 || node->rect.h <= 0) return 0;
  S32 x = node->rect.x + node->rect.w / 2;
  S32 y = node->rect.y + node->rect.h / 2;
  eightvo_host_pointer_press(app, x, y);
  eightvo_render_to_buffer(app, buffer);
  eightvo_apply_reader_view_actions(app);
  if (eightvo_host_pointer_release(app, x, y)) return 0;
  eightvo_render_to_buffer(app, buffer);
  eightvo_apply_reader_view_actions(app);
  app->input.pointer_x = -32768;
  app->input.pointer_y = -32768;
  eightvo_render_to_buffer(app, buffer);
  eightvo_apply_reader_view_actions(app);
  return 1;
}

FUNCTION B32
eightvo_reader_view_parity_space_node(
  EightvoApp *app,
  RenderBuffer *buffer,
  const ReaderViewSemanticNode *node)
{
  if (!app || !buffer || !node || node->id == 0 ||
      !reader_view_accessibility_focus(&app->reader_view_state, node->id))
    return 0;
  (void)eightvo_host_focus_set(app, EightvoHostControl_None, 0);
  eightvo_render_to_buffer(app, buffer);
  if (app->reader_view_state.focus_id != node->id ||
      !eightvo_reader_view_space_activates_focus(app))
    return 0;
  if (eightvo_reader_view_route_keydown(app, VK_SPACE, 0) !=
      EightvoReaderKeyRoute_Handled)
    return 0;
  eightvo_render_to_buffer(app, buffer);
  eightvo_apply_reader_view_actions(app);
  eightvo_render_to_buffer(app, buffer);
  eightvo_apply_reader_view_actions(app);
  return 1;
}

FUNCTION B32
eightvo_reader_view_parity_click(EightvoApp *app,
                                  RenderBuffer *buffer,
                                  ReaderViewSemanticRole role,
                                  const char *name)
{
  return eightvo_reader_view_parity_click_node(
    app, buffer, eightvo_reader_view_parity_semantic(app, role, name));
}

FUNCTION B32
eightvo_reader_view_parity_click_control(
  EightvoApp *app,
  RenderBuffer *buffer,
  ReaderViewSemanticControl control)
{
  return eightvo_reader_view_parity_click_node(
    app, buffer, eightvo_reader_view_parity_control(app, control));
}

FUNCTION B32
eightvo_reader_view_parity_focus(EightvoApp *app,
                                  RenderBuffer *buffer,
                                  const char *focus)
{
  if (!app || !buffer || !focus) return 0;
  if (strcmp(focus, "none") == 0) return 1;
  if (strcmp(focus, "previous-enabled") == 0)
  {
    U32 before_spine = app->reader.active_spine_index;
    U64 before_byte = app->reader.view_byte_offset;
    if (eightvo_move_page(app, 1) != EpubReaderResult_Ok) return 0;
    eightvo_render_to_buffer(app, buffer);
    eightvo_apply_reader_view_actions(app);
    if (app->reader.active_spine_index == before_spine &&
        app->reader.view_byte_offset == before_byte)
      return 0;
  }
  U32 tab_count = strcmp(focus, "exit") == 0 ? 3 :
                  (strcmp(focus, "previous") == 0 ||
                   strcmp(focus, "previous-enabled") == 0) ? 11 :
                  strcmp(focus, "next") == 0 ? 12 : 0;
  if (tab_count == 0) return 0;
  for (U32 tab = 0; tab < tab_count; tab += 1)
  {
    if (!eightvo_host_keyboard_tab(app, 0))
      app->input.focus_next_pressed = 1;
    eightvo_render_to_buffer(app, buffer);
    eightvo_apply_reader_view_actions(app);
  }
  if (strcmp(focus, "exit") == 0)
  {
    EightvoHostControlRecord *exit = eightvo_host_control_record(
      app, EightvoHostControl_ExitReader);
    return exit && app->host_focus_control == EightvoHostControl_ExitReader &&
           app->host_focus_visible &&
           (exit->semantic.flags & ReaderViewSemantic_Focused) != 0;
  }
  ReaderViewSemanticControl expected =
    (strcmp(focus, "previous") == 0 ||
     strcmp(focus, "previous-enabled") == 0) ?
    ReaderViewSemanticControl_PreviousPage :
    ReaderViewSemanticControl_NextPage;
  const ReaderViewSemanticNode *node =
    eightvo_reader_view_parity_control(app, expected);
  return node && (node->flags & ReaderViewSemantic_Focused) != 0 &&
         (strcmp(focus, "previous-enabled") != 0 ||
          (node->flags & ReaderViewSemantic_Enabled) != 0) &&
         app->reader_view_state.focus_id == node->id &&
         app->reader_view_state.focus_visible;
}

FUNCTION B32
eightvo_apply_reader_view_parity_scenario(EightvoApp *app,
                                           RenderBuffer *buffer,
                                           const char *left,
                                           const char *right,
                                           const char *popup,
                                           const char *query,
                                           const char *focus,
                                           const char *annotation_case)
{
  if (!app || !buffer || !left || !right || !popup || !query || !focus ||
      !annotation_case)
    return 0;
  UI0ID filter_restore_id = 0;
  if (strcmp(left, "contents") == 0 &&
      !eightvo_reader_view_parity_click(app, buffer,
                                        ReaderViewSemantic_Button,
                                        "Contents"))
    return 0;
  if (strcmp(left, "find") == 0 &&
      !eightvo_reader_view_parity_click(app, buffer,
                                        ReaderViewSemantic_Button,
                                        "Find"))
    return 0;

  if (strcmp(right, "bookmark") == 0)
  {
    if (!eightvo_reader_view_parity_click(app, buffer,
                                          ReaderViewSemantic_Button,
                                          "Bookmark") ||
        !eightvo_reader_view_parity_click(app, buffer,
                                          ReaderViewSemantic_Button,
                                          "Annotations"))
      return 0;
  }
  else if (strcmp(right, "open") == 0 &&
           !eightvo_reader_view_parity_click(app, buffer,
                                             ReaderViewSemantic_Button,
                                             "Annotations"))
    return 0;

  if (strcmp(popup, "font") == 0 &&
      !eightvo_reader_view_parity_click(app, buffer,
                                        ReaderViewSemantic_Button,
                                        "Font"))
    return 0;

  ReaderViewKey highlight_key = 0;
  ReaderViewKey note_key = 0;
  if (strcmp(annotation_case, "none") != 0)
  {
    highlight_key = eightvo_reader_view_parity_right_key(
      app, ReaderViewRightRow_Highlight);
    note_key = eightvo_reader_view_parity_right_key(
      app, ReaderViewRightRow_Note);
    if (highlight_key == 0 || note_key == 0) return 0;
  }
  if (strcmp(annotation_case, "highlight-note") == 0)
  {
    const ReaderViewSemanticNode *star =
      eightvo_reader_view_parity_control_source(
        app, ReaderViewSemanticControl_RightRowStar, highlight_key);
    UI0Rect star_rect = star ? star->rect : (UI0Rect){0};
    if (!eightvo_reader_view_parity_click_node(app, buffer, star) ||
        !app->highlights[0].starred || app->highlights[0].note_starred)
      return 0;
    /* re10's automation leaves the pointer over the activated Star. Preserve
       that final hover state for the deterministic visual comparison. */
    app->input.pointer_x = star_rect.x + star_rect.w / 2;
    app->input.pointer_y = star_rect.y + star_rect.h / 2;
    eightvo_render_to_buffer(app, buffer);
    eightvo_apply_reader_view_actions(app);
  }
  else if (strcmp(annotation_case, "row-actions") == 0)
  {
    const ReaderViewSemanticNode *menu =
      eightvo_reader_view_parity_control_source(
        app, ReaderViewSemanticControl_RightRowMenu, highlight_key);
    if (!eightvo_reader_view_parity_click_node(app, buffer, menu) ||
        app->reader_view_state.popup != ReaderViewPopup_RightRowActions)
      return 0;
  }
  else if (strcmp(annotation_case, "note-editor") == 0)
  {
    const ReaderViewSemanticNode *menu =
      eightvo_reader_view_parity_control_source(
        app, ReaderViewSemanticControl_RightRowMenu, note_key);
    if (!eightvo_reader_view_parity_click_node(app, buffer, menu)) return 0;
    const ReaderViewSemanticNode *edit =
      eightvo_reader_view_parity_control_source(
        app, ReaderViewSemanticControl_RightActionEditNote, note_key);
    if (!eightvo_reader_view_parity_click_node(app, buffer, edit) ||
        app->reader_view_state.popup != ReaderViewPopup_NoteEditor ||
        !eightvo_reader_view_text_equals(
          reader_view_note_draft(&app->reader_view_state),
          "Attached parity note"))
      return 0;
  }
  if (strcmp(popup, "annotations-filter") == 0)
  {
    const ReaderViewSemanticNode *filter =
      eightvo_reader_view_parity_control(
        app, ReaderViewSemanticControl_RightFilter);
    if (!filter) return 0;
    filter_restore_id = filter->id;
    if (!eightvo_reader_view_parity_click_control(
          app, buffer, ReaderViewSemanticControl_RightFilter))
      return 0;
  }

  if (strcmp(query, "-") != 0)
  {
    size_t query_size = strlen(query);
    if (app->reader_view_state.left_panel != ReaderViewLeftPanel_Find ||
        query_size >= ARRAY_COUNT(app->input.text) ||
        !eightvo_reader_view_parity_click(app, buffer,
                                          ReaderViewSemantic_SearchBox,
                                          0))
      return 0;
    MemoryCopy(app->input.text, query, (U64)query_size);
    app->input.text[query_size] = 0;
    app->input.text_length = (S32)query_size;
    eightvo_render_to_buffer(app, buffer);
    eightvo_apply_reader_view_actions(app);
    app->input.commit_pressed = 1;
    eightvo_render_to_buffer(app, buffer);
    eightvo_apply_reader_view_actions(app);
    for (U32 frame = 0; frame < 6; frame += 1)
    {
      eightvo_render_to_buffer(app, buffer);
      eightvo_apply_reader_view_actions(app);
    }
  }

  if (!eightvo_reader_view_parity_focus(app, buffer, focus)) return 0;

  ReaderViewLeftPanelMode expected_left = ReaderViewLeftPanel_None;
  if (strcmp(left, "contents") == 0)
    expected_left = ReaderViewLeftPanel_Contents;
  else if (strcmp(left, "find") == 0)
    expected_left = ReaderViewLeftPanel_Find;
  B32 expect_right_open = strcmp(right, "closed") != 0;
  ReaderViewPopupKind expected_popup = strcmp(popup, "font") == 0 ?
    ReaderViewPopup_SettingMenu :
    strcmp(popup, "annotations-filter") == 0 ?
      ReaderViewPopup_RightFilter :
    strcmp(popup, "row-actions") == 0 ?
      ReaderViewPopup_RightRowActions :
    strcmp(popup, "note-editor") == 0 ?
      ReaderViewPopup_NoteEditor : ReaderViewPopup_None;
  B32 query_matches = strcmp(query, "-") == 0 ||
    (eightvo_reader_view_text_equals(reader_view_find_query(
       &app->reader_view_state), query) && app->reader.search_match_count > 0 &&
     eightvo_reader_view_text_equals(
       app->reader_view_projection.find.status.message, "18 matches"));
  B32 bookmark_matches = strcmp(right, "bookmark") != 0 ||
                         app->bookmark_count == 1;
  B32 filter_focus_matches = 1;
  if (expected_popup == ReaderViewPopup_RightFilter)
  {
    const ReaderViewSemanticNode *option =
      eightvo_reader_view_parity_control(
        app, ReaderViewSemanticControl_RightFilterOption);
    filter_focus_matches = option &&
      option->source_key == ReaderViewRightFilter_All &&
      (option->flags & ReaderViewSemantic_Focused) != 0 &&
      app->reader_view_state.focus_id == option->id &&
      !app->reader_view_state.focus_visible &&
      app->reader_view_state.restore_focus_id == filter_restore_id;
  }
  B32 annotation_matches = strcmp(annotation_case, "none") == 0 ||
    (app->highlight_count == 1 && app->highlights[0].is_highlight &&
     app->highlights[0].color_index == 2 &&
     strcmp(app->highlights[0].text,
            "Alpha reader parity sentence") == 0 &&
     strcmp(app->highlights[0].note, "Attached parity note") == 0 &&
     app->reader_view_projection.right.highlight_count == 1 &&
     app->reader_view_projection.right.note_count == 1 &&
     (strcmp(annotation_case, "highlight-note") != 0 ||
      (app->highlights[0].starred && !app->highlights[0].note_starred)));
  return app->reader_view_state.left_panel == expected_left &&
         app->reader_view_state.right_panel_open == expect_right_open &&
         app->reader_view_state.popup == expected_popup &&
         (expected_popup != ReaderViewPopup_SettingMenu ||
          app->reader_view_state.active_setting_kind ==
            ReaderViewSetting_FontFamily) &&
         query_matches && bookmark_matches && filter_focus_matches &&
         annotation_matches;
}

FUNCTION int
eightvo_run_reader_view_parity_capture(const char *epub_path,
                                        const char *width_text,
                                        const char *height_text,
                                        const char *theme,
                                        const char *left,
                                        const char *right,
                                        const char *popup,
                                        const char *query,
                                        const char *evidence_path,
                                        const char *bmp_path,
                                        const char *focus,
                                        const char *annotation_case)
{
  S32 width = (S32)atoi(width_text);
  S32 height = (S32)atoi(height_text);
  U64 pixel_count = (U64)(U32)width * (U64)(U32)height;
  if (width < 320 || height < 240 || width > 4096 || height > 4096 ||
      pixel_count > (U64)4096 * 4096)
    return 2;
  EightvoApp app = {0};
  U32 *pixels = (U32 *)calloc((size_t)pixel_count, sizeof(U32));
  if (!pixels || !eightvo_app_init(&app, width, height, 1, 0) ||
      !eightvo_open_path(&app, epub_path) ||
      !eightvo_configure_reader_view_parity(&app, theme, left, right,
                                             popup, query, focus,
                                             annotation_case) ||
      !eightvo_seed_reader_view_parity_annotations(&app, annotation_case))
  {
    fprintf(stderr, "eightvo_reader_view_parity result=fail reason=setup\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, width, height, width);
  eightvo_render_to_buffer(&app, &buffer);
  if (!eightvo_apply_reader_view_parity_scenario(&app, &buffer,
                                                   left, right, popup, query,
                                                   focus, annotation_case))
  {
    fprintf(stderr,
            "eightvo_reader_view_parity result=fail reason=scenario expected_left=%s actual_left=%d expected_right=%s actual_right=%d expected_popup=%s actual_popup=%d query=%s focus=%s annotation=%s matches=%u bookmarks=%u highlights=%u\n",
            left,
            (int)app.reader_view_state.left_panel,
            right,
            app.reader_view_state.right_panel_open,
            popup,
            (int)app.reader_view_state.popup,
            query,
            focus,
            annotation_case,
            app.reader.search_match_count,
            app.bookmark_count,
            app.highlight_count);
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  if (strcmp(query, "-") != 0)
  {
    /* The re10 automation capture lands on the first hidden caret frame.
       Keep this headless comparison at the same deterministic blink phase;
       interactive eightvo continues to use its monotonically increasing
       host frame counter. */
    app.reader_view_frame_index = 30;
  }
  eightvo_render_to_buffer(&app, &buffer);
  B32 wrote_evidence = eightvo_write_reader_view_parity(evidence_path, &app);
  B32 wrote_bmp = eightvo_write_bmp(bmp_path, pixels, width, height);
  if (!wrote_evidence || !wrote_bmp)
  {
    fprintf(stderr,
            "eightvo_reader_view_parity result=fail reason=write evidence=%d bmp=%d\n",
            wrote_evidence, wrote_bmp);
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  fprintf(stdout,
          "eightvo_reader_view_parity result=pass size=%dx%d theme=%s left=%s right=%s popup=%s query=%s focus=%s annotation=%s\n",
          width, height, theme, left, right, popup, query, focus,
          annotation_case);
  free(pixels);
  eightvo_app_release(&app);
  return 0;
}

FUNCTION void
eightvo_library_focus_selected(EightvoApp *app)
{
  if (!app || app->library.entry_count == 0) return;
  S32 selected = eightvo_library_catalog_index_for_id(
    &app->library, app->library_selected_entry_id);
  if (selected < 0)
  {
    selected = 0;
    app->library_selected_entry_id = app->library.entries[0].entry_id;
  }
  eightvo_library_resolve_layout(app);
  EightvoLibraryCardLayout *card = eightvo_library_card_for_entry(
    app, app->library_selected_entry_id);
  if (!card)
  {
    S32 row = selected / MAX(app->library_column_count, 1);
    app->library_scroll_y = MAX(0, row * 380);
    eightvo_library_resolve_layout(app);
    card = eightvo_library_card_for_entry(app,
                                           app->library_selected_entry_id);
  }
  if (card)
  {
    if (card->card_rect.y < 104)
      app->library_scroll_y = MAX(0, app->library_scroll_y -
        (104 - card->card_rect.y));
    else if (card->card_rect.y + card->card_rect.h > app->height - 16)
      app->library_scroll_y = MIN(app->library_scroll_max,
        app->library_scroll_y + card->card_rect.y + card->card_rect.h -
          (app->height - 16));
    eightvo_library_resolve_layout(app);
  }
  eightvo_update_host_control_records(app);
  for (U32 card_index = 0; card_index < app->library_card_count; card_index += 1)
  {
    if (app->library_cards[card_index].entry_id ==
        app->library_selected_entry_id)
    {
      app->host_focus_control =
        (EightvoHostControlIdentity)(EightvoHostControl_LibraryBookBase +
                                       card_index);
      app->host_focus_visible = 1;
      eightvo_update_host_control_records(app);
      break;
    }
  }
}

FUNCTION B32
eightvo_library_route_keydown(EightvoApp *app, WPARAM key, B32 shift)
{
  if (!app || !eightvo_library_active(app)) return 0;
  if (key == VK_TAB) return eightvo_host_keyboard_tab(app, shift);
  if (key == VK_RETURN || key == VK_SPACE)
    return eightvo_host_keyboard_activate(app);
  if (app->library.entry_count == 0)
  {
    if (key == VK_HOME || key == VK_END)
      return eightvo_host_focus_set(app, EightvoHostControl_LibraryAdd, 1);
    return 0;
  }
  S32 index = eightvo_library_catalog_index_for_id(
    &app->library, app->library_selected_entry_id);
  if (index < 0) index = 0;
  S32 target = index;
  S32 columns = MAX(app->library_column_count, 1);
  if (key == VK_LEFT) target -= 1;
  else if (key == VK_RIGHT) target += 1;
  else if (key == VK_UP) target -= columns;
  else if (key == VK_DOWN) target += columns;
  else if (key == VK_HOME) target = 0;
  else if (key == VK_END) target = (S32)app->library.entry_count - 1;
  else if (key == VK_DELETE)
    return eightvo_library_remove_entry(app,
                                         app->library_selected_entry_id);
  else if (key == 'L')
  {
    EightvoLibraryEntry *entry = eightvo_library_catalog_find_id(
      &app->library, app->library_selected_entry_id);
    return entry && entry->runtime_missing ?
      eightvo_locate_library_entry(app, entry->entry_id) : 0;
  }
  else return 0;
  target = MAX(0, MIN(target, (S32)app->library.entry_count - 1));
  app->library_selected_entry_id = app->library.entries[target].entry_id;
  eightvo_library_focus_selected(app);
  return 1;
}

FUNCTION LRESULT CALLBACK
eightvo_page_repeat_probe_paint_proc(HWND window,
                                      UINT message,
                                      WPARAM w_param,
                                      LPARAM l_param)
{
  EightvoWin32 *win32 =
    (EightvoWin32 *)GetWindowLongPtrW(window, GWLP_USERDATA);
  switch (message)
  {
    case WM_NCCREATE:
    {
      CREATESTRUCTW *create = (CREATESTRUCTW *)l_param;
      SetWindowLongPtrW(window, GWLP_USERDATA, (LONG_PTR)create->lpCreateParams);
      return TRUE;
    } break;

    case WM_PAINT:
    {
      B32 had_update_region = GetUpdateRect(window, 0, FALSE) != 0;
      PAINTSTRUCT paint = {0};
      BeginPaint(window, &paint);
      EndPaint(window, &paint);
      if (win32 && win32->page_repeat_probe_track_paints &&
          had_update_region &&
          win32->page_repeat_probe_aux_paint_pending_count > 0)
      {
        win32->page_repeat_probe_aux_paint_pending_count -= 1;
        win32->page_repeat_probe_aux_paint_dispatch_count += 1;
      }
      return 0;
    } break;
  }
  return DefWindowProcW(window, message, w_param, l_param);
}

FUNCTION LRESULT CALLBACK
eightvo_win32_proc(HWND window, UINT message, WPARAM w_param, LPARAM l_param)
{
  EightvoWin32 *win32 = (EightvoWin32 *)GetWindowLongPtrW(window, GWLP_USERDATA);
  EightvoApp *app = win32 ? &win32->app : 0;
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
          app->last_present_complete = 0;
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
        eightvo_host_pointer_move(app,
                                   GET_X_LPARAM(l_param),
                                   GET_Y_LPARAM(l_param));
        if (app->selection_dragging && app->input.pointer_down)
          eightvo_update_pointer_selection(app,
                                            app->input.pointer_x,
                                            app->input.pointer_y,
                                            0);
        InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_CANCELMODE:
    case WM_CAPTURECHANGED:
    {
      if (app)
      {
        eightvo_host_pointer_cancel(app);
        if (message == WM_CANCELMODE)
          eightvo_cancel_page_repeat_for_deactivation(app);
      }
      return 0;
    } break;

    case WM_ACTIVATEAPP:
    {
      if (app && !w_param)
        eightvo_cancel_page_repeat_for_deactivation(app);
    } break;

    case WM_ACTIVATE:
    {
      if (app && LOWORD(w_param) == WA_INACTIVE)
        eightvo_cancel_page_repeat_for_deactivation(app);
    } break;

    case WM_LBUTTONDOWN:
    {
      if (app)
      {
        SetCapture(window);
        eightvo_host_pointer_press(app,
                                    GET_X_LPARAM(l_param),
                                    GET_Y_LPARAM(l_param));
        InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_LBUTTONUP:
    {
      if (app)
      {
        B32 exit_requested = eightvo_host_pointer_release(
          app, GET_X_LPARAM(l_param), GET_Y_LPARAM(l_param));
        ReleaseCapture();
        (void)exit_requested;
        InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_GETOBJECT:
    {
      if (app && (LONG)l_param == OBJID_CLIENT)
      {
        if (!app->accessibility)
          (void)eightvo_accessibility_create(window,
                                               app,
                                               &app->accessibility);
        if (app->accessibility)
          return eightvo_accessibility_get_object(app->accessibility,
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
          eightvo_reader_view_text_editing(app))
      {
        eightvo_append_input_wchar(app, (wchar_t)w_param);
        InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_SYSKEYDOWN:
    {
      if (app)
      {
        B32 alt = (GetKeyState(VK_MENU) & 0x8000) != 0;
        B32 system_repeat = (l_param & (1LL << 30)) != 0;
        if (eightvo_page_repeat_consume_cancelled_keydown(app,
                                                           w_param,
                                                           l_param))
        {
          return 0;
        }
        if (app->page_repeat_active || app->page_action_pending)
        {
          B32 active_key = w_param == app->page_repeat_key;
          eightvo_cancel_page_repeat_for_modifier(app);
          if (active_key && system_repeat &&
              eightvo_page_repeat_consume_cancelled_keydown(app,
                                                              w_param,
                                                              l_param))
          {
            return 0;
          }
        }
        if (alt && w_param == VK_LEFT)
        {
          (void)eightvo_move_history(app, 0);
          InvalidateRect(window, 0, FALSE);
          return 0;
        }
        if (alt && w_param == VK_RIGHT)
        {
          (void)eightvo_move_history(app, 1);
          InvalidateRect(window, 0, FALSE);
          return 0;
        }
      }
    } break;

    case WM_KEYDOWN:
    {
      if (app)
      {
        B32 control = (GetKeyState(VK_CONTROL) & 0x8000) != 0;
        B32 shift = (GetKeyState(VK_SHIFT) & 0x8000) != 0;
        B32 alt = (GetKeyState(VK_MENU) & 0x8000) != 0;
        B32 system_repeat = (l_param & (1LL << 30)) != 0;
        B32 editing = eightvo_reader_view_text_editing(app);
        S32 repeat_direction = 0;
        B32 page_action_candidate = !control && !alt &&
          eightvo_page_direction_for_key(app, w_param, &repeat_direction);
        B32 repeat_candidate = page_action_candidate && !shift;
        B32 repeat_handled = 0;
        if (eightvo_page_repeat_consume_cancelled_keydown(app,
                                                           w_param,
                                                           l_param))
        {
          return 0;
        }
        if ((app->page_repeat_active || app->page_action_pending) &&
            (control || shift || alt ||
             eightvo_page_repeat_modifier_key(w_param)))
        {
          B32 active_key = w_param == app->page_repeat_key;
          eightvo_cancel_page_repeat_for_modifier(app);
          if (active_key && system_repeat &&
              eightvo_page_repeat_consume_cancelled_keydown(app,
                                                              w_param,
                                                              l_param))
          {
            return 0;
          }
        }
        if (!control && !alt &&
            eightvo_page_repeat_should_coalesce_keydown(app,
                                                         w_param,
                                                         l_param,
                                                         0))
        {
          if (app->page_repeat_native_coalesced_count < UINT32_MAX)
            app->page_repeat_native_coalesced_count += 1;
          return 0;
        }
        if (page_action_candidate && system_repeat) return 0;
        if (page_action_candidate && app->page_action_waiting_for_present)
        {
          if (system_repeat) return 0;
          eightvo_page_action_defer(app,
                                     w_param,
                                     repeat_direction,
                                     repeat_candidate);
          return 0;
        }
        if (page_action_candidate && app->page_repeat_active &&
            w_param != app->page_repeat_key)
        {
          WPARAM cancelled_key = app->page_repeat_key;
          eightvo_stop_page_repeat(app);
          app->page_repeat_cancelled_key = cancelled_key;
        }
        if (w_param == 'O' && control)
        {
          (void)eightvo_pick_epub(app);
        }
        else if (w_param == 'F' && control &&
                 epub_reader_is_open(&app->reader))
        {
          eightvo_reader_view_open_find_from_shortcut(app);
        }
        else if (w_param == 'B' && control && epub_reader_is_open(&app->reader))
        {
          (void)eightvo_toggle_current_bookmark(app);
        }
        else if (w_param == VK_F11)
        {
          (void)eightvo_set_fullscreen(app, !app->fullscreen.active);
        }
        else if (w_param == VK_ESCAPE && epub_reader_is_open(&app->reader))
        {
          eightvo_reader_view_escape(app);
        }
        else if (editing && control && w_param == 'A') app->input.select_all_pressed = 1;
        else if (editing && control && w_param == 'C') app->input.copy_pressed = 1;
        else if (editing && control && w_param == 'X') app->input.cut_pressed = 1;
        else if (editing && control && w_param == 'V')
        {
          app->input.paste_pressed = eightvo_get_clipboard_text(app);
        }
        else if (editing && control && w_param == 'Z') app->input.undo_pressed = 1;
        else if (editing && control && w_param == 'Y') app->input.redo_pressed = 1;
        else if (!editing && control && w_param == 'C' && app->selected_text[0])
        {
          (void)eightvo_set_clipboard_text(
            app, eightvo_reader_view_text(app->selected_text));
        }
        else if (alt && w_param == VK_LEFT)
        {
          (void)eightvo_move_history(app, 0);
        }
        else if (alt && w_param == VK_RIGHT)
        {
          (void)eightvo_move_history(app, 1);
        }
        else if (eightvo_library_active(app))
        {
          (void)eightvo_library_route_keydown(app, w_param, shift);
        }
        else
        {
          B32 page_move_succeeded = 0;
          EightvoReaderKeyRoute route =
            eightvo_reader_view_route_keydown_ex(app,
                                                  w_param,
                                                  shift,
                                                  &page_move_succeeded);
          if (route == EightvoReaderKeyRoute_CloseRequested)
          {
            InvalidateRect(window, 0, FALSE);
            return 0;
          }
          repeat_handled = repeat_candidate && page_move_succeeded &&
            route == EightvoReaderKeyRoute_Handled;
        }
        if (repeat_handled)
        {
          eightvo_start_page_repeat(app, w_param, repeat_direction);
        }
        InvalidateRect(window, 0, FALSE);
      }
      return 0;
    } break;

    case WM_KEYUP:
    {
      if (app)
      {
        if ((app->page_repeat_active || app->page_action_pending) &&
            eightvo_page_repeat_modifier_key(w_param))
        {
          eightvo_cancel_page_repeat_for_modifier(app);
        }
        eightvo_page_action_release_key(app, w_param);
      }
      return 0;
    } break;

    case WM_SYSKEYUP:
    {
      if (app)
      {
        if (app->page_repeat_active || app->page_action_pending)
          eightvo_cancel_page_repeat_for_modifier(app);
        if (w_param == app->page_repeat_cancelled_key)
          app->page_repeat_cancelled_key = 0;
      }
    } break;

    case WM_KILLFOCUS:
    {
      if (app) eightvo_cancel_page_repeat_for_focus(app);
    } break;

    case WM_TIMER:
    {
      if (app && w_param == EightvoPresentationRetryTimerId)
      {
        (void)KillTimer(window, EightvoPresentationRetryTimerId);
        if (app->page_action_waiting_for_present)
        {
          if (app->page_action_presentation_retry_fired_count < UINT32_MAX)
            app->page_action_presentation_retry_fired_count += 1;
          (void)InvalidateRect(window, 0, FALSE);
        }
        return 0;
      }
      if (app && w_param == EightvoStateSaveTimerId)
      {
        if (app->page_repeat_active || app->page_action_waiting_for_present ||
            app->page_action_pending)
        {
          (void)KillTimer(window, EightvoStateSaveTimerId);
          app->state_save_pending = 1;
          return 0;
        }
        (void)eightvo_save_state(app);
        return 0;
      }
      if (app && w_param == EightvoAdjacentWarmTimerId)
      {
        (void)eightvo_adjacent_warm_step(app);
        return 0;
      }
      if (app && w_param == EightvoLocationWarmTimerId)
      {
        (void)eightvo_location_warm_step(app);
        return 0;
      }
    } break;

    case EightvoPageRepeatProbeMutationMessage:
    {
      if (app && win32 && win32->page_repeat_probe_track_paints &&
          w_param < EightvoPageRepeatProbeMutationCount)
      {
        switch ((U32)w_param)
        {
          case 0:
            app->suppress_native_picker = 1;
            (void)eightvo_pick_epub(app);
            app->suppress_native_picker = 0;
            break;
          case 1: (void)eightvo_move_history(app, 0); break;
          case 2:
            (void)eightvo_apply_setting(
              app, ReaderViewSetting_Theme, (ReaderViewKey)4001);
            break;
          case 3: (void)eightvo_seek_location(app, 0); break;
          case 4:
            eightvo_apply_reader_view_action(app, &(ReaderViewAction){
              .kind = ReaderViewAction_NextPage,
            });
            break;
          case 5: (void)eightvo_repaginate(app); break;
          case 6: (void)eightvo_close_book(app); break;
        }
      }
      return 0;
    } break;

    case WM_PAINT:
    {
      B32 had_update_region = GetUpdateRect(window, 0, FALSE) != 0;
      PAINTSTRUCT paint = {0};
      BeginPaint(window, &paint);
      B32 presented_complete = app ? eightvo_render(app) : 0;
      EndPaint(window, &paint);
      if (win32 && win32->page_repeat_probe_track_paints &&
          !had_update_region &&
          win32->page_repeat_probe_main_null_paint_pending_count > 0)
      {
        win32->page_repeat_probe_main_null_paint_pending_count -= 1;
        win32->page_repeat_probe_main_null_paint_dispatch_count += 1;
      }
      if (app)
      {
        B32 needs_frame = app->reader_view_frame.action_count > 0 ||
          app->reader_view_frame.change_flags != ReaderViewFrameChange_None;
        eightvo_apply_reader_view_actions(app);
        if (needs_frame) InvalidateRect(window, 0, FALSE);
        eightvo_note_stable_presentation(app,
                                          presented_complete,
                                          needs_frame);
        eightvo_page_repeat_note_presented_frame(
          app, app->last_present_complete);
      }
      return 0;
    } break;

    case WM_DESTROY:
    {
      if (app) { eightvo_stop_page_repeat(app); }
      if (app && app->accessibility)
        eightvo_accessibility_destroy(app->accessibility);
      PostQuitMessage(0);
      return 0;
    } break;
  }
  return DefWindowProcW(window, message, w_param, l_param);
}

FUNCTION B32
eightvo_reader_view_host_icon_raster_regression(EightvoApp *app);

FUNCTION int
eightvo_run_saved_position_first_load_smoke(const char *path,
                                             U32 spine_index,
                                             U64 byte_offset)
{
  enum
  {
    SmokeWidth = 1280,
    SmokeHeight = 900,
    MaxOpenMilliseconds = 750,
    MaxFirstVisibleMilliseconds = 150,
    MaxRowsBuilt = 384,
  };
  EightvoApp app = {0};
  U32 *pixels = (U32 *)calloc(
    (size_t)SmokeWidth * SmokeHeight, sizeof(U32));
  RenderBuffer buffer = {0};
  U64 frequency = os_time_frequency();
  U64 init_start = os_time_ticks();
  if (!pixels || !path || !path[0] || frequency == 0 ||
      !eightvo_app_init(&app, SmokeWidth, SmokeHeight, 1, 0) ||
      !eightvo_library_normalize_path(path,
                                       app.saved.path,
                                       ARRAY_COUNT(app.saved.path)))
  {
    fprintf(stderr,
            "eightvo_saved_position_first_load_smoke result=fail reason=init\n");
    eightvo_app_release(&app);
    free(pixels);
    return 1;
  }
  render_buffer_init(&buffer, pixels, SmokeWidth, SmokeHeight, SmokeWidth);
  eightvo_render_to_buffer(&app, &buffer);
  app.saved.valid = 1;
  app.saved.spine_index = spine_index;
  app.saved.byte_offset = byte_offset;

  U64 open_start = os_time_ticks();
  B32 opened = eightvo_open_path(&app, path);
  U64 open_ticks = os_time_ticks() - open_start;
  app.first_reader_present_pending = 1;
  B32 adjacent_warm_blocked_before_present =
    app.adjacent_warm_pending &&
    !eightvo_adjacent_warm_step(&app) &&
    app.adjacent_warm_pending &&
    !app.adjacent_warm_frame_ready &&
    app.adjacent_warm_next_text_command == 0;
  app.highlight_count = 1;
  app.highlights[0] = (EightvoHighlight){
    .id = 1,
    .spine_index = app.layout_key.spine_count ?
      app.layout_key.spine_count - 1 : app.reader.active_spine_index,
    .start_byte = 0,
    .end_byte = 1,
    .color_index = 0,
    .is_highlight = 1,
  };
  eightvo_copy_cstr(app.highlights[0].section,
                     ARRAY_COUNT(app.highlights[0].section),
                     "Deferred annotation location");
  eightvo_copy_cstr(app.highlights[0].text,
                     ARRAY_COUNT(app.highlights[0].text),
                     "Deferred");
  app.annotation_revision += 1;
  U64 first_render_start = os_time_ticks();
  eightvo_render_to_buffer(&app, &buffer);
  U64 first_render_ticks = os_time_ticks() - first_render_start;
  B32 annotation_locations_deferred =
    app.reader_view_projection.right.row_count == 1 &&
    app.reader_view_right_secondary[0][0] == 0 &&
    !app.reader.location_cache_complete;
  U64 first_reader_view_ticks = app.last_render_reader_view_ticks;
  U64 first_reader_page_ticks = app.last_render_reader_page_ticks;
  U64 first_ui_adapt_ticks = app.last_render_ui_adapt_ticks;
  U64 first_execute_ticks = app.last_render_execute_ticks;
  app.first_reader_present_pending = 0;
  app.highlight_count = 0;
  app.annotation_revision += 1;
  U64 second_render_start = os_time_ticks();
  eightvo_render_to_buffer(&app, &buffer);
  U64 second_render_ticks = os_time_ticks() - second_render_start;
  U64 total_ticks = os_time_ticks() - init_start;
  double open_ms = 1000.0 * (double)open_ticks / (double)frequency;
  double first_render_ms =
    1000.0 * (double)first_render_ticks / (double)frequency;
  double first_visible_ms = open_ms + first_render_ms;
  double second_render_ms =
    1000.0 * (double)second_render_ticks / (double)frequency;
  double first_reader_view_ms =
    1000.0 * (double)first_reader_view_ticks / (double)frequency;
  double first_reader_page_ms =
    1000.0 * (double)first_reader_page_ticks / (double)frequency;
  double first_ui_adapt_ms =
    1000.0 * (double)first_ui_adapt_ticks / (double)frequency;
  double first_execute_ms =
    1000.0 * (double)first_execute_ticks / (double)frequency;
  double total_ms = 1000.0 * (double)total_ticks / (double)frequency;
  SourceReaderLayoutSpine *active_spine =
    epub_reader_layout_spine(&app.reader, app.reader.active_spine_index);
  U64 clamped_byte = app.reader.spine_text_size > 0 ?
    MIN(byte_offset, app.reader.spine_text_size - 1) : 0;
  B32 target_owned = app.reader.has_current_page &&
    app.reader.current_page.spine_index == spine_index &&
    app.reader.current_page.first_byte <= clamped_byte &&
    app.reader.current_page.one_past_last_byte > clamped_byte;
  B32 bounded = active_spine && app.reader.pagination.page_count > 0 &&
    app.reader.pagination.page_count <= EPUB_READER_WINDOW_PAGE_COUNT &&
    active_spine->page_count > 0 &&
    active_spine->page_count <= EPUB_READER_WINDOW_PAGE_COUNT;
  B32 passed = opened && app.frame.ready && app.frame.document_open &&
    app.reader.active_spine_index == spine_index && target_owned && bounded &&
    app.reader.navigation_stats.window_pagination_rebuild_count >= 1 &&
    app.reader.navigation_stats.full_pagination_rebuild_count == 0 &&
    app.reader.layout_stats_total.rows_built <= MaxRowsBuilt &&
    open_ms <= MaxOpenMilliseconds &&
    first_visible_ms <= MaxFirstVisibleMilliseconds &&
    adjacent_warm_blocked_before_present &&
    annotation_locations_deferred &&
    eightvo_frame_presentation_is_complete(&app);

  fprintf(passed ? stdout : stderr,
          "eightvo_saved_position_first_load_smoke result=%s open_ms=%.3f first_render_ms=%.3f first_visible_ms=%.3f second_render_ms=%.3f first_reader_view_ms=%.3f first_reader_page_ms=%.3f first_ui_adapt_ms=%.3f first_execute_ms=%.3f total_ms=%.3f warm_before_present=%s annotation_locations=%s spine=%u byte=%llu page=%llu/%llu active_pages=%llu total_pages=%llu active_rows=%llu rows_built=%llu bytes_laid_out=%llu window_rebuilds=%llu full_rebuilds=%llu active_complete=%d\n",
          passed ? "pass" : "fail",
          open_ms,
          first_render_ms,
          first_visible_ms,
          second_render_ms,
          first_reader_view_ms,
          first_reader_page_ms,
          first_ui_adapt_ms,
          first_execute_ms,
          total_ms,
          adjacent_warm_blocked_before_present ? "blocked" : "ran",
          annotation_locations_deferred ? "deferred" : "synchronous",
          app.reader.active_spine_index,
          (unsigned long long)clamped_byte,
          (unsigned long long)app.frame.page_index,
          (unsigned long long)app.frame.page_count,
          (unsigned long long)(active_spine ? active_spine->page_count : 0),
          (unsigned long long)app.reader.pagination.page_count,
          (unsigned long long)(active_spine ? active_spine->row_count : 0),
          (unsigned long long)app.reader.layout_stats_total.rows_built,
          (unsigned long long)app.reader.layout_stats_total.bytes_laid_out,
          (unsigned long long)
            app.reader.navigation_stats.window_pagination_rebuild_count,
          (unsigned long long)
            app.reader.navigation_stats.full_pagination_rebuild_count,
          active_spine ? active_spine->rows_complete : 0);
  eightvo_app_release(&app);
  free(pixels);
  return passed ? 0 : 1;
}

FUNCTION int
eightvo_run_headless(const char *path)
{
  EightvoApp app = {0};
  if (!eightvo_app_init(&app, 1000, 720, 0, 0) || !eightvo_open_path(&app, path))
  {
    fprintf(stderr, "eightvo_host_smoke result=fail reason=open\n");
    eightvo_app_release(&app);
    return 1;
  }
  if (!eightvo_reader_view_host_icon_raster_regression(&app))
  {
    fprintf(stderr,
            "eightvo_host_smoke result=fail reason=host_icon_raster\n");
    eightvo_app_release(&app);
    return 1;
  }

  U32 nav_count = 0;
  EpubReaderNavPointResult nav_navigation = {0};
  if (doc_engine_get_nav_point_count(epub_reader_engine(&app.reader),
                                     epub_reader_document_id(&app.reader),
                                     &nav_count) != DocError_Ok ||
      nav_count < 2 ||
      eightvo_navigate_to_nav_point(&app,
                                     1,
                                     &nav_navigation) != EpubReaderResult_Ok ||
      !nav_navigation.had_fragment ||
      !nav_navigation.fragment_resolved ||
      nav_navigation.fragment_fallback ||
      app.reader.active_spine_index != 1)
  {
    fprintf(stderr, "eightvo_host_smoke result=fail reason=contents_navigation\n");
    eightvo_app_release(&app);
    return 1;
  }

  if (!epub_reader_rebuild_search(&app.reader,
                                  str8_from_cstr("standalone host proof")) ||
      app.reader.search_match_count == 0)
  {
    fprintf(stderr, "eightvo_host_smoke result=fail reason=find_query\n");
    eightvo_app_release(&app);
    return 1;
  }
  EpubReaderSearchNavigationResult search_navigation = {0};
  if (eightvo_navigate_to_search_match(&app,
                                        0,
                                        &search_navigation) != EpubReaderResult_Ok ||
      search_navigation.match.spine_index != 0 ||
      app.reader.active_spine_index != 0 ||
      app.reader.back_stack_count < 2)
  {
    fprintf(stderr, "eightvo_host_smoke result=fail reason=find_navigation\n");
    eightvo_app_release(&app);
    return 1;
  }

  U32 start_spine = app.reader.active_spine_index;
  SourceReaderPageRange cross_page = {0};
  B32 crossed = 0;
  for (U32 attempt = 0; attempt < 256 && !crossed; attempt += 1)
  {
    if (eightvo_move_page(&app, 1) != EpubReaderResult_Ok)
    {
      fprintf(stderr, "eightvo_host_smoke result=fail reason=forward\n");
      eightvo_app_release(&app);
      return 1;
    }
    crossed = app.reader.active_spine_index != start_spine;
    if (crossed) { cross_page = app.reader.current_page; }
  }
  U32 frame_gap_start = 0;
  U32 frame_gap_end = 0;
  if (crossed &&
      !eightvo_frame_text_rows_are_complete(&app.frame,
                                             &frame_gap_start,
                                             &frame_gap_end))
  {
    fprintf(stderr,
            "eightvo_host_smoke result=fail reason=frame_text_gap gap=%u..%u text=%llu rows=%u\n",
            frame_gap_start,
            frame_gap_end,
            (unsigned long long)app.frame.visible_text.size,
            app.frame.style_row_count);
    eightvo_app_release(&app);
    return 1;
  }

  if (!crossed || eightvo_move_page(&app, -1) != EpubReaderResult_Ok ||
      app.reader.active_spine_index != start_spine ||
      eightvo_move_page(&app, 1) != EpubReaderResult_Ok ||
      app.reader.current_page.spine_index != cross_page.spine_index ||
      app.reader.current_page.first_byte != cross_page.first_byte ||
      !eightvo_capture_frame(&app))
  {
    fprintf(stderr, "eightvo_host_smoke result=fail reason=cross_spine\n");
    eightvo_app_release(&app);
    return 1;
  }

  U32 resize_spine = app.reader.active_spine_index;
  U64 resize_byte = app.reader.view_byte_offset;
  app.width = 760;
  app.height = 560;
  if (!eightvo_repaginate(&app) ||
      app.reader.active_spine_index != resize_spine ||
      app.reader.current_page.first_byte > resize_byte ||
      app.reader.current_page.one_past_last_byte <= resize_byte ||
      !app.frame.ready || !app.frame.document_open)
  {
    fprintf(stderr, "eightvo_host_smoke result=fail reason=resize\n");
    eightvo_app_release(&app);
    return 1;
  }

  U64 hash = u64_hash_str8(app.frame.visible_text);
  fprintf(stdout,
          "eightvo_host_smoke result=pass spine=%u page=%llu/%llu text=%llu toc=1 find=1 carets=frozen18x32 hash=%016llx\n",
          app.frame.spine_index,
          (unsigned long long)app.frame.page_index,
          (unsigned long long)app.frame.page_count,
          (unsigned long long)app.frame.visible_text.size,
          (unsigned long long)hash);
  eightvo_app_release(&app);
  return 0;
}

FUNCTION int
eightvo_run_render_smoke(const char *path, const char *bmp_path)
{
  enum { RenderWidth = 1100, RenderHeight = 760 };
  EightvoApp app = {0};
  if (!eightvo_app_init(&app, RenderWidth, RenderHeight, 1, 0) ||
      !eightvo_open_path(&app, path))
  {
    fprintf(stderr, "eightvo_visual_smoke result=fail reason=open\n");
    eightvo_app_release(&app);
    return 1;
  }

  U32 start_spine = app.reader.active_spine_index;
  B32 crossed = 0;
  for (U32 attempt = 0; attempt < 256 && !crossed; attempt += 1)
  {
    EpubReaderResult move = eightvo_move_page(&app, 1);
    if (move != EpubReaderResult_Ok)
    {
      fprintf(stderr, "eightvo_visual_smoke result=fail reason=forward\n");
      eightvo_app_release(&app);
      return 1;
    }
    crossed = app.reader.active_spine_index != start_spine;
  }

  U32 gap_start = 0;
  U32 gap_end = 0;
  if (!crossed ||
      !eightvo_frame_text_rows_are_complete(&app.frame, &gap_start, &gap_end))
  {
    fprintf(stderr,
            "eightvo_visual_smoke result=fail reason=frame gap=%u..%u\n",
            gap_start,
            gap_end);
    eightvo_app_release(&app);
    return 1;
  }

  U64 pixel_count = (U64)RenderWidth * (U64)RenderHeight;
  U32 *pixels = (U32 *)calloc((size_t)pixel_count, sizeof(U32));
  if (!pixels)
  {
    fprintf(stderr, "eightvo_visual_smoke result=fail reason=memory\n");
    eightvo_app_release(&app);
    return 1;
  }
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, RenderWidth, RenderHeight, RenderWidth);
  eightvo_render_to_buffer(&app, &buffer);
  if (!app.presentation_complete)
  {
    fprintf(stderr, "eightvo_visual_smoke result=fail reason=presentation\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  U64 pixel_hash = u64_hash_bytes(pixels, pixel_count * sizeof(U32));
  B32 wrote = eightvo_write_bmp(bmp_path, pixels, RenderWidth, RenderHeight);
  free(pixels);
  if (!wrote)
  {
    fprintf(stderr, "eightvo_visual_smoke result=fail reason=write\n");
    eightvo_app_release(&app);
    return 1;
  }

  fprintf(stdout,
          "eightvo_visual_smoke result=pass spine=%u page=%llu/%llu rows=%u pixels=%dx%d hash=%016llx presentation=%016llx bmp=%s\n",
          app.frame.spine_index,
          (unsigned long long)app.frame.page_index,
          (unsigned long long)app.frame.page_count,
          app.frame.style_row_count,
          RenderWidth,
          RenderHeight,
          (unsigned long long)pixel_hash,
          (unsigned long long)app.presentation_hash,
          bmp_path);
  eightvo_app_release(&app);
  return 0;
}

FUNCTION U32
eightvo_loaded_image_count(const EpubReaderFrame *frame)
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
eightvo_write_frame_evidence(EightvoApp *app,
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
  eightvo_render_to_buffer(app, &buffer);
  B32 result = app->presentation_complete &&
    eightvo_write_bmp(bmp_path, pixels, RenderWidth, RenderHeight);
  if (result && out_hash)
  {
    *out_hash = u64_hash_bytes(pixels, pixel_count * sizeof(U32));
  }
  free(pixels);
  return result;
}

FUNCTION int
eightvo_run_image_smoke(const char *path,
                         const char *cover_bmp_path,
                         const char *inline_bmp_path)
{
  enum { RenderWidth = 1100, RenderHeight = 760 };
  EightvoApp app = {0};
  if (!eightvo_app_init(&app, RenderWidth, RenderHeight, 1, 0) ||
      !eightvo_open_path(&app, path))
  {
    fprintf(stderr, "eightvo_image_smoke result=fail reason=open\n");
    eightvo_app_release(&app);
    return 1;
  }

  U32 cover_loaded = eightvo_loaded_image_count(&app.frame);
  U64 cover_hash = 0;
  if (app.frame.image_count == 0 || cover_loaded == 0 ||
      !eightvo_capture_frame(&app) ||
      eightvo_loaded_image_count(&app.frame) == 0 ||
      !eightvo_write_frame_evidence(&app, cover_bmp_path, &cover_hash))
  {
    fprintf(stderr,
            "eightvo_image_smoke result=fail reason=cover images=%u loaded=%u\n",
            app.frame.image_count,
            cover_loaded);
    eightvo_app_release(&app);
    return 1;
  }

  U32 cover_spine = app.reader.active_spine_index;
  B32 crossed = 0;
  for (U32 attempt = 0; attempt < 256 && !crossed; attempt += 1)
  {
    if (eightvo_move_page(&app, 1) != EpubReaderResult_Ok)
    {
      break;
    }
    crossed = app.reader.active_spine_index != cover_spine;
  }

  U32 inline_loaded = eightvo_loaded_image_count(&app.frame);
  U64 inline_hash = 0;
  if (!crossed || app.frame.image_count == 0 || inline_loaded == 0 ||
      !eightvo_capture_frame(&app) ||
      eightvo_loaded_image_count(&app.frame) == 0 ||
      !eightvo_write_frame_evidence(&app, inline_bmp_path, &inline_hash))
  {
    fprintf(stderr,
            "eightvo_image_smoke result=fail reason=inline crossed=%d images=%u loaded=%u\n",
            crossed,
            app.frame.image_count,
            inline_loaded);
    eightvo_app_release(&app);
    return 1;
  }

  fprintf(stdout,
          "eightvo_image_smoke result=pass cover_loaded=%u inline_loaded=%u entries=%u lookups=%llu hits=%llu misses=%llu cover_hash=%016llx inline_hash=%016llx\n",
          cover_loaded,
          inline_loaded,
          app.image_cache.entry_count,
          (unsigned long long)app.image_cache.lookup_count,
          (unsigned long long)app.image_cache.hit_count,
          (unsigned long long)app.image_cache.miss_count,
          (unsigned long long)cover_hash,
          (unsigned long long)inline_hash);
  eightvo_app_release(&app);
  return 0;
}

typedef struct EightvoReaderImageFitEvidence
{
  U32 spine_index;
  U64 page_index;
  U32 visual_units;
  S32 line_height_px;
  S32 body_width_px;
  S32 body_height_px;
  S32 media_width_px;
  S32 media_height_px;
  S32 fit_width_px;
  S32 fit_height_px;
  S32 source_width_px;
  S32 source_height_px;
  U64 pixel_hash;
  U64 presentation_hash;
} EightvoReaderImageFitEvidence;

FUNCTION B32
eightvo_capture_reader_image_fit_evidence(
  EightvoApp *app,
  RenderBuffer *buffer,
  const char *case_name,
  const char *bmp_path,
  EightvoReaderImageFitEvidence *out_evidence)
{
  if (out_evidence) { MemoryZeroStruct(out_evidence); }
  if (!app || !buffer || !buffer->pixels || !case_name || !bmp_path ||
      !out_evidence)
  {
    return 0;
  }

  U64 prepared_build_before = app->image_cache.prepared_build_count;
  U64 prepared_hit_before = app->image_cache.prepared_hit_count;
  U64 prepared_fallback_before = app->image_cache.prepared_fallback_count;
  eightvo_render_to_buffer(app, buffer);
  eightvo_render_to_buffer(app, buffer);
  U64 pixel_count = (U64)buffer->width * (U64)buffer->height;
  U64 pixel_hash = u64_hash_bytes(buffer->pixels,
                                  pixel_count * sizeof(U32));
  if (!eightvo_write_bmp(bmp_path,
                          buffer->pixels,
                          buffer->width,
                          buffer->height) ||
      !app->presentation_complete ||
      !app->presentation_frame.valid ||
      app->frame.style_row_count != 1 ||
      app->presentation_frame.row_count != 1 ||
      app->presentation_frame.media_count != 1)
  {
    fprintf(stderr,
            "eightvo_reader_image_fit_case result=fail case=%s reason=frame rows=%u presentation_rows=%u media=%u complete=%d bmp=%s\n",
            case_name,
            app->frame.style_row_count,
            app->presentation_frame.row_count,
            app->presentation_frame.media_count,
            app->presentation_complete,
            bmp_path);
    return 0;
  }

  const EpubReaderFrameStyleRow *row = app->frame.style_rows;
  EpubReaderFrameImage *image = eightvo_image_for_row(&app->frame, row->row);
  const PresentationEngineBlockFlowRow *presentation_row =
    app->presentation_frame.rows;
  if (!image ||
      image->image_placement != SourceReaderLayoutImagePlacement_ImageOnly ||
      image->status != EpubReaderFrameImageStatus_Loaded ||
      !image->pixels || image->src_w <= 0 || image->src_h <= 0 ||
      presentation_row->media_count != 1 ||
      presentation_row->first_media_index >=
        app->presentation_frame.media_count)
  {
    fprintf(stderr,
            "eightvo_reader_image_fit_case result=fail case=%s reason=image placement=%u status=%u src=%dx%d bmp=%s\n",
            case_name,
            image ? image->image_placement : 0,
            image ? image->status : 0,
            image ? image->src_w : 0,
            image ? image->src_h : 0,
            bmp_path);
    return 0;
  }

  UI0Rect body = app->reader_content_geometry.content_rect;
  const PresentationEngineBlockFlowMedia *media =
    app->presentation_frame.media + presentation_row->first_media_index;
  U32 visual_units = row->visual_units ? row->visual_units : 18;
  S64 canonical_height = (S64)visual_units * (S64)app->layout_key.line_height;
  S32 fit_x = 0;
  S32 fit_y = 0;
  S32 fit_w = 0;
  S32 fit_h = 0;
  if (canonical_height <= 0 || canonical_height > INT32_MAX ||
      !eightvo_fit_image_rect(image->src_w,
                               image->src_h,
                               media->rect.x,
                               media->rect.y,
                               media->rect.w,
                               media->rect.h,
                               &fit_x,
                               &fit_y,
                               &fit_w,
                               &fit_h))
  {
    fprintf(stderr,
            "eightvo_reader_image_fit_case result=fail case=%s reason=fit units=%u line_height=%d media=%dx%d bmp=%s\n",
            case_name, visual_units, app->layout_key.line_height,
            media->rect.w, media->rect.h, bmp_path);
    return 0;
  }

  U32 matching_sprites = 0;
  U32 matching_media_backgrounds = 0;
  B32 area_prepared_sampling = 0;
  for (U16 command_index = 0;
       command_index < app->draw_commands.command_count[DrawLayer_World];
       command_index += 1)
  {
    const DrawCommand *command =
      app->draw_commands.commands[DrawLayer_World] + command_index;
    if (command->type == DrawCommandType_Sprite &&
        command->v.sprite.dst_x == fit_x &&
        command->v.sprite.dst_y == fit_y &&
        command->v.sprite.dst_w == fit_w &&
        command->v.sprite.dst_h == fit_h)
    {
      matching_sprites += 1;
      for (U32 prepared_index = 0;
           prepared_index < app->image_cache.prepared_image_count;
           prepared_index += 1)
      {
        const EightvoPreparedImage *prepared =
          app->image_cache.prepared_images + prepared_index;
        if (command->v.sprite.pixels == prepared->pixels &&
            command->v.sprite.pixels != image->pixels &&
            command->v.sprite.src_w == fit_w &&
            command->v.sprite.src_h == fit_h &&
            command->v.sprite.src_stride_pixels == fit_w &&
            command->v.sprite.sample_kind == DrawSpriteSampleKind_Nearest &&
            prepared->source_pixels == image->pixels &&
            prepared->source_width == image->src_w &&
            prepared->source_height == image->src_h &&
            prepared->width == fit_w &&
            prepared->height == fit_h &&
            prepared->sample_kind == DrawSpriteSampleKind_Area)
        {
          area_prepared_sampling = 1;
          break;
        }
      }
    }
    if (command->type == DrawCommandType_RoundedRect &&
        command->v.rounded_rect.x == media->rect.x &&
        command->v.rounded_rect.y == media->rect.y &&
        command->v.rounded_rect.w == media->rect.w &&
        command->v.rounded_rect.h == media->rect.h)
    {
      matching_media_backgrounds += 1;
    }
  }

  B32 canonical_geometry =
    media->rect.x == body.x &&
    media->rect.y == body.y &&
    media->rect.w == body.w &&
    media->rect.h == (S32)canonical_height &&
    presentation_row->row_rect.h == (S32)canonical_height &&
    media->rect.h <= body.h &&
    media->rect.h > 320;
  S64 aspect_delta = (S64)fit_w * (S64)image->src_h -
                     (S64)fit_h * (S64)image->src_w;
  if (aspect_delta < 0) { aspect_delta = -aspect_delta; }
  B32 aspect_preserved =
    aspect_delta <= MAX(image->src_w, image->src_h);
  if (!canonical_geometry || !aspect_preserved ||
      matching_sprites != 1 || matching_media_backgrounds != 0 ||
      !area_prepared_sampling ||
      app->image_cache.prepared_build_count != prepared_build_before + 1 ||
      app->image_cache.prepared_hit_count < prepared_hit_before + 1 ||
      app->image_cache.prepared_fallback_count != prepared_fallback_before)
  {
    fprintf(stderr,
            "eightvo_reader_image_fit_case result=fail case=%s reason=contract spine=%u page=%llu units=%u line_height=%d body=%dx%d media=%dx%d fit=%dx%d src=%dx%d canonical=%lld sprites=%u backgrounds=%u area_prepared=%d builds=%llu hits=%llu fallbacks=%llu aspect=%d bmp=%s\n",
            case_name,
            app->frame.spine_index,
            (unsigned long long)app->frame.page_index,
            visual_units,
            app->layout_key.line_height,
            body.w, body.h,
            media->rect.w, media->rect.h,
            fit_w, fit_h,
            image->src_w, image->src_h,
            (long long)canonical_height,
            matching_sprites,
            matching_media_backgrounds,
            area_prepared_sampling,
            (unsigned long long)(app->image_cache.prepared_build_count -
                                 prepared_build_before),
            (unsigned long long)(app->image_cache.prepared_hit_count -
                                 prepared_hit_before),
            (unsigned long long)(app->image_cache.prepared_fallback_count -
                                 prepared_fallback_before),
            aspect_preserved,
            bmp_path);
    return 0;
  }

  *out_evidence = (EightvoReaderImageFitEvidence){
    .spine_index = app->frame.spine_index,
    .page_index = app->frame.page_index,
    .visual_units = visual_units,
    .line_height_px = app->layout_key.line_height,
    .body_width_px = body.w,
    .body_height_px = body.h,
    .media_width_px = media->rect.w,
    .media_height_px = media->rect.h,
    .fit_width_px = fit_w,
    .fit_height_px = fit_h,
    .source_width_px = image->src_w,
    .source_height_px = image->src_h,
    .pixel_hash = pixel_hash,
    .presentation_hash = app->presentation_hash,
  };
  fprintf(stdout,
          "eightvo_reader_image_fit_case result=pass case=%s spine=%u page=%llu units=%u line_height=%d body=%dx%d media=%dx%d fit=%dx%d src=%dx%d sampling=area_prepared builds=1 hits=1 fallbacks=0 pixel=%016llx presentation=%016llx bmp=%s\n",
          case_name,
          out_evidence->spine_index,
          (unsigned long long)out_evidence->page_index,
          out_evidence->visual_units,
          out_evidence->line_height_px,
          out_evidence->body_width_px,
          out_evidence->body_height_px,
          out_evidence->media_width_px,
          out_evidence->media_height_px,
          out_evidence->fit_width_px,
          out_evidence->fit_height_px,
          out_evidence->source_width_px,
          out_evidence->source_height_px,
          (unsigned long long)out_evidence->pixel_hash,
          (unsigned long long)out_evidence->presentation_hash,
          bmp_path);
  return 1;
}

FUNCTION int
eightvo_run_reader_image_fit_smoke(const char *epub_path,
                                    const char *output_prefix)
{
  enum { Width = 1182, Height = 713, CaseCount = 4 };
  static const char *case_names[CaseCount] = {
    "cover", "maps_1", "maps_2", "maps_3",
  };
  EightvoApp app = {0};
  U32 *pixels = (U32 *)calloc((size_t)Width * Height, sizeof(U32));
  EightvoReaderImageFitEvidence evidence[CaseCount] = {0};
  RenderBuffer buffer = {0};
  char bmp_path[EightvoPathCap] = {0};
  int result = 1;
  if (!pixels || !epub_path || !epub_path[0] ||
      !output_prefix || !output_prefix[0] ||
      !eightvo_app_init(&app, Width, Height, 1, 0))
  {
    fprintf(stderr,
            "eightvo_reader_image_fit result=fail reason=setup\n");
    goto cleanup;
  }
  app.theme = EightvoTheme_Dark;
  if (!eightvo_open_path(&app, epub_path))
  {
    fprintf(stderr,
            "eightvo_reader_image_fit result=fail reason=open\n");
    goto cleanup;
  }
  render_buffer_init(&buffer, pixels, Width, Height, Width);

  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_%s.bmp", output_prefix, case_names[0]);
  if (!eightvo_capture_reader_image_fit_evidence(
        &app, &buffer, case_names[0], bmp_path, evidence + 0))
  {
    goto cleanup;
  }

  U32 nav_count = 0;
  EpubReaderNavPointResult navigation = {0};
  if (doc_engine_get_nav_point_count(epub_reader_engine(&app.reader),
                                     epub_reader_document_id(&app.reader),
                                     &nav_count) != DocError_Ok ||
      nav_count == 0 ||
      eightvo_navigate_to_nav_point(&app, 0, &navigation) !=
        EpubReaderResult_Ok ||
      app.reader.active_spine_index != 9)
  {
    fprintf(stderr,
            "eightvo_reader_image_fit result=fail reason=maps_navigation nav_count=%u spine=%u\n",
            nav_count, app.reader.active_spine_index);
    goto cleanup;
  }

  for (U32 case_index = 1; case_index < CaseCount; case_index += 1)
  {
    if (case_index > 1 &&
        eightvo_move_page(&app, 1) != EpubReaderResult_Ok)
    {
      fprintf(stderr,
              "eightvo_reader_image_fit result=fail reason=maps_page case=%u\n",
              case_index);
      goto cleanup;
    }
    (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                      "%s_%s.bmp", output_prefix, case_names[case_index]);
    if (!eightvo_capture_reader_image_fit_evidence(
          &app, &buffer, case_names[case_index], bmp_path,
          evidence + case_index))
    {
      goto cleanup;
    }
  }

  fprintf(stdout,
          "eightvo_reader_image_fit result=pass book=gotm_new cases=4 viewport=%dx%d image_only=4 canonical_units=reader0 cap320=absent sampling=area_prepared hashes=%016llx,%016llx,%016llx,%016llx output=%s\n",
          Width, Height,
          (unsigned long long)evidence[0].pixel_hash,
          (unsigned long long)evidence[1].pixel_hash,
          (unsigned long long)evidence[2].pixel_hash,
          (unsigned long long)evidence[3].pixel_hash,
          output_prefix);
  result = 0;

cleanup:
  if (pixels) { free(pixels); }
  eightvo_app_release(&app);
  return result;
}

FUNCTION U64
eightvo_reader_view_hash_mix(U64 hash, U64 value)
{
  hash ^= value;
  hash *= 1099511628211ull;
  return hash;
}

FUNCTION U64
eightvo_reader_view_icon_hash_u32(U64 hash, U32 value)
{
  U64 result = hash;
  for (U32 shift = 0; shift < 32; shift += 8)
  {
    result ^= (U64)((value >> shift) & 0xffu);
    result *= 1099511628211ull;
  }
  return result;
}

FUNCTION U64
eightvo_reader_view_icon_raster_hash(const U32 *pixels,
                                      S32 width,
                                      S32 height,
                                      S32 stride_pixels)
{
  U64 hash = 1469598103934665603ull;
  if (!pixels || width <= 0 || height <= 0 || stride_pixels < width)
    return 0;
  hash = eightvo_reader_view_icon_hash_u32(hash, (U32)width);
  hash = eightvo_reader_view_icon_hash_u32(hash, (U32)height);
  for (S32 y = 0; y < height; y += 1)
    for (S32 x = 0; x < width; x += 1)
      hash = eightvo_reader_view_icon_hash_u32(
        hash, pixels[(size_t)y * (size_t)stride_pixels + (size_t)x]);
  return hash;
}

FUNCTION U64
eightvo_reader_view_contract_hash(const ReaderViewFrame *frame)
{
  U64 hash = 1469598103934665603ull;
  if (!frame) return hash;
  hash = eightvo_reader_view_hash_mix(hash, (U64)frame->layout.mode);
  hash = eightvo_reader_view_hash_mix(hash, (U64)frame->layout.toolbar_density);
  hash = eightvo_reader_view_hash_mix(hash, (U64)(S64)frame->layout.viewport_rect.x);
  hash = eightvo_reader_view_hash_mix(hash, (U64)(S64)frame->layout.viewport_rect.y);
  hash = eightvo_reader_view_hash_mix(hash, (U64)(S64)frame->layout.viewport_rect.w);
  hash = eightvo_reader_view_hash_mix(hash, (U64)(S64)frame->layout.viewport_rect.h);
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
  {
    const ReaderViewSemanticNode *node = frame->semantic_nodes + index;
    hash = eightvo_reader_view_hash_mix(hash, node->id);
    hash = eightvo_reader_view_hash_mix(hash, node->parent_id);
    hash = eightvo_reader_view_hash_mix(hash, (U64)node->role);
    hash = eightvo_reader_view_hash_mix(hash, node->flags);
    hash = eightvo_reader_view_hash_mix(hash, node->source_key);
    if (node->name.data && node->name.size > 0)
      hash = eightvo_reader_view_hash_mix(
        hash, u64_hash_bytes(node->name.data, (U64)node->name.size));
  }
  return hash;
}

FUNCTION B32
eightvo_reader_view_has_semantic(const ReaderViewFrame *frame,
                                  const char *name)
{
  if (!frame || !name) return 0;
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
    if (eightvo_reader_view_text_is(frame->semantic_nodes[index].name, name))
      return 1;
  return 0;
}

FUNCTION B32
eightvo_reader_view_has_action(const ReaderViewFrame *frame,
                                ReaderViewActionKind kind);

FUNCTION const ReaderViewSemanticNode *
eightvo_reader_view_semantic_control(const ReaderViewFrame *frame,
                                      ReaderViewSemanticControl control)
{
  if (!frame || !frame->semantic_nodes ||
      control == ReaderViewSemanticControl_None)
    return 0;
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
    if (frame->semantic_nodes[index].control == control)
      return frame->semantic_nodes + index;
  return 0;
}

FUNCTION const ReaderViewSemanticNode *
eightvo_reader_view_semantic_control_source(
  const ReaderViewFrame *frame,
  ReaderViewSemanticControl control,
  ReaderViewKey source_key)
{
  if (!frame || !frame->semantic_nodes ||
      control == ReaderViewSemanticControl_None)
    return 0;
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
  {
    const ReaderViewSemanticNode *node = frame->semantic_nodes + index;
    if (node->control == control && node->source_key == source_key)
      return node;
  }
  return 0;
}

FUNCTION B32
eightvo_reader_view_has_draw_for_source(const ReaderViewFrame *frame,
                                         UI0DrawOpKind op,
                                         UI0ID source_id)
{
  if (!frame || !frame->draw_commands || source_id == 0) return 0;
  for (UI0S32 index = 0; index < frame->draw_command_count; index += 1)
    if (frame->draw_commands[index].op == op &&
        frame->draw_commands[index].source_id == source_id)
      return 1;
  return 0;
}

FUNCTION const UI0DrawCommand *
eightvo_reader_view_draw_for_source(const ReaderViewFrame *frame,
                                     UI0DrawOpKind op,
                                     UI0ID source_id)
{
  if (!frame || !frame->draw_commands || source_id == 0) return 0;
  for (UI0S32 index = 0; index < frame->draw_command_count; index += 1)
    if (frame->draw_commands[index].op == op &&
        frame->draw_commands[index].source_id == source_id)
      return frame->draw_commands + index;
  return 0;
}

FUNCTION B32
eightvo_reader_view_host_icon_raster_regression(EightvoApp *app)
{
  UI0DrawCommand command = {0};
  const U32 *left_first;
  const U32 *left_repeat;
  const U32 *right;
  const U32 *filter;
  if (!app || EightvoUI0IconRasterMaxWidth != 32 ||
      EightvoUI0IconRasterMaxHeight != 32)
    return 0;

  app->ui0_icon_raster_count = 0;
  command.op = UI0DrawOp_Icon;
  command.icon_kind = UI0IconKind_PageCaretLeft;
  command.rect = ui0_rect(0, 0, 18, 32);
  command.color = UI0_COLOR_RGB(31, 41, 55);
  command.stroke_color = UI0_COLOR_RGB(253, 251, 247);
  left_first = eightvo_ui0_icon_raster(app, &command);
  left_repeat = eightvo_ui0_icon_raster(app, &command);
  if (!left_first || left_repeat != left_first ||
      app->ui0_icon_raster_count != 1)
    return 0;

  command.icon_kind = UI0IconKind_PageCaretRight;
  right = eightvo_ui0_icon_raster(app, &command);
  if (!right || right == left_first || app->ui0_icon_raster_count != 2)
    return 0;

  if (eightvo_reader_view_icon_raster_hash(
        left_first, 18, 32, EightvoUI0IconRasterMaxWidth) !=
        2165135300752429591ull ||
      eightvo_reader_view_icon_raster_hash(
        right, 18, 32, EightvoUI0IconRasterMaxWidth) !=
        7851032404797536851ull)
    return 0;

  command.icon_kind = UI0IconKind_Filter;
  command.rect = ui0_rect(120, 72, 18, 18);
  command.clip_rect = ui0_rect(112, 64, 34, 34);
  command.color = UI0_COLOR_RGB(35, 42, 53);
  command.stroke_color = UI0_COLOR_RGB(246, 241, 232);
  filter = eightvo_ui0_icon_raster(app, &command);
  if (!filter || app->ui0_icon_raster_count != 3 ||
      eightvo_reader_view_icon_raster_hash(
        filter, 18, 18, EightvoUI0IconRasterMaxWidth) !=
        768785035519145851ull)
    return 0;

  command.rect.h = EightvoUI0IconRasterMaxHeight + 1;
  return eightvo_ui0_icon_raster(app, &command) == 0 &&
         app->ui0_icon_raster_count == 3;
}

typedef struct EightvoExpectedFocus
{
  EightvoHostControlIdentity host;
  ReaderViewSemanticControl shared;
} EightvoExpectedFocus;

FUNCTION B32
eightvo_reader_view_keyboard_input_routing_regression(EightvoApp *app)
{
  if (!app) return 0;
  enum
  {
    FindInput = 9801,
    FindClear = 9802,
    NoteInput = 9803,
    NoteSave = 9804,
    NextPage = 9805,
    Progress = 9806,
  };
  ReaderViewSemanticNode nodes[] = {
    {
      .id = FindInput,
      .role = ReaderViewSemantic_SearchBox,
      .control = ReaderViewSemanticControl_FindInput,
    },
    {
      .id = FindClear,
      .role = ReaderViewSemantic_Button,
      .control = ReaderViewSemanticControl_FindClear,
    },
    {
      .id = NoteInput,
      .role = ReaderViewSemantic_TextArea,
    },
    {
      .id = NoteSave,
      .role = ReaderViewSemantic_Button,
      .control = ReaderViewSemanticControl_RightActionEditNote,
    },
    {
      .id = NextPage,
      .role = ReaderViewSemantic_Button,
      .control = ReaderViewSemanticControl_NextPage,
    },
    {
      .id = Progress,
      .role = ReaderViewSemantic_Slider,
      .control = ReaderViewSemanticControl_Progress,
    },
  };
  ReaderViewFrame saved_frame = app->reader_view_frame;
  ReaderViewState saved_state = app->reader_view_state;
  EightvoInput saved_input = app->input;
  app->reader_view_frame.semantic_nodes = nodes;
  app->reader_view_frame.semantic_node_count = ARRAY_COUNT(nodes);

  MemoryZeroStruct(&app->input);
  app->reader_view_state.left_panel = ReaderViewLeftPanel_Find;
  app->reader_view_state.popup = ReaderViewPopup_None;
  app->reader_view_state.pending_left_panel_focus = ReaderViewLeftPanel_None;
  app->reader_view_state.focus_id = FindInput;
  B32 result = eightvo_reader_view_text_editing(app) &&
    !eightvo_reader_view_space_activates_focus(app) &&
    eightvo_reader_view_route_keydown(app, VK_SPACE, 0) ==
      EightvoReaderKeyRoute_None &&
    eightvo_reader_view_route_keydown(app, VK_RETURN, 0) ==
      EightvoReaderKeyRoute_Handled &&
    eightvo_reader_view_route_keydown(app, VK_RIGHT, 1) ==
      EightvoReaderKeyRoute_Handled &&
    eightvo_reader_view_route_keydown(app, VK_NEXT, 0) ==
      EightvoReaderKeyRoute_Handled;
  ReaderViewInput input = eightvo_reader_view_input(app);
  result = result &&
    input.move_horizontal_delta == 0 && input.move_vertical_delta == 0 &&
    input.range_move == ReaderViewRangeMove_NextPage &&
    input.find_text.move_delta == 1 && input.find_text.extend_selection &&
    input.find_text.commit_pressed &&
    eightvo_reader_view_horizontal_move_is_shared(app);

  MemoryZeroStruct(&app->input);
  app->reader_view_state.focus_id = FindClear;
  result = result &&
    eightvo_reader_view_route_keydown(app, VK_SPACE, 0) ==
      EightvoReaderKeyRoute_Handled &&
    eightvo_reader_view_route_keydown(app, VK_PRIOR, 0) ==
      EightvoReaderKeyRoute_Handled;
  input = eightvo_reader_view_input(app);
  result = result && !eightvo_reader_view_text_editing(app) &&
    eightvo_reader_view_space_activates_focus(app) &&
    (input.ui.flags & UI0Input_ActivatePressed) != 0 &&
    input.move_horizontal_delta == 0 &&
    input.range_move == ReaderViewRangeMove_PreviousPage &&
    !eightvo_reader_view_horizontal_move_is_shared(app);

  MemoryZeroStruct(&app->input);
  app->reader_view_state.left_panel = ReaderViewLeftPanel_None;
  app->reader_view_state.popup = ReaderViewPopup_NoteEditor;
  app->reader_view_state.focus_id = NoteInput;
  result = result &&
    eightvo_reader_view_route_keydown(app, VK_SPACE, 0) ==
      EightvoReaderKeyRoute_None &&
    eightvo_reader_view_route_keydown(app, VK_RETURN, 0) ==
      EightvoReaderKeyRoute_Handled &&
    eightvo_reader_view_route_keydown(app, VK_DOWN, 1) ==
      EightvoReaderKeyRoute_Handled;
  input = eightvo_reader_view_input(app);
  result = result && eightvo_reader_view_text_editing(app) &&
    !eightvo_reader_view_space_activates_focus(app) &&
    input.move_horizontal_delta == 0 && input.move_vertical_delta == 0 &&
    input.note_text.text_len == 1 && input.note_text.text[0] == '\n' &&
    input.note_text.move_vertical_delta == 1 &&
    input.note_text.extend_selection &&
    eightvo_reader_view_horizontal_move_is_shared(app);

  MemoryZeroStruct(&app->input);
  app->reader_view_state.focus_id = NoteSave;
  result = result &&
    eightvo_reader_view_route_keydown(app, VK_RETURN, 0) ==
      EightvoReaderKeyRoute_Handled;
  input = eightvo_reader_view_input(app);
  result = result && !eightvo_reader_view_text_editing(app) &&
    eightvo_reader_view_space_activates_focus(app) &&
    (input.ui.flags & UI0Input_ActivatePressed) != 0 &&
    !eightvo_reader_view_horizontal_move_is_shared(app);

  MemoryZeroStruct(&app->input);
  app->reader_view_state.popup = ReaderViewPopup_None;
  app->reader_view_state.focus_id = NextPage;
  result = result &&
    eightvo_reader_view_route_keydown(app, VK_NEXT, 0) ==
      EightvoReaderKeyRoute_Handled &&
    eightvo_reader_view_route_keydown(app, VK_SPACE, 0) ==
      EightvoReaderKeyRoute_Handled;
  input = eightvo_reader_view_input(app);
  result = result && !eightvo_reader_view_text_editing(app) &&
    eightvo_reader_view_space_activates_focus(app) &&
    (input.ui.flags & UI0Input_ActivatePressed) != 0 &&
    input.move_horizontal_delta == 0 &&
    input.range_move == ReaderViewRangeMove_NextPage &&
    !eightvo_reader_view_horizontal_move_is_shared(app);

  MemoryZeroStruct(&app->input);
  app->reader_view_state.focus_id = Progress;
  U32 progress_spine = app->reader.active_spine_index;
  U64 progress_byte = app->reader.view_byte_offset;
  result = result &&
    eightvo_reader_view_route_keydown(app, VK_RIGHT, 0) ==
      EightvoReaderKeyRoute_Handled;
  input = eightvo_reader_view_input(app);
  result = result && input.move_horizontal_delta == 1 &&
    app->reader.active_spine_index == progress_spine &&
    app->reader.view_byte_offset == progress_byte &&
    eightvo_reader_view_horizontal_move_is_shared(app);
  app->reader_view_state.focus_id = 0;
  result = result && !eightvo_reader_view_text_editing(app) &&
    !eightvo_reader_view_space_activates_focus(app);

  app->input = saved_input;
  app->reader_view_state = saved_state;
  app->reader_view_frame = saved_frame;
  return result;
}

FUNCTION B32
eightvo_reader_view_find_shortcut_focus_regression(EightvoApp *app,
                                                     RenderBuffer *buffer)
{
  if (!app || !buffer) return 0;
  ReaderViewState saved_state = app->reader_view_state;
  EightvoInput saved_input = app->input;
  EightvoHostControlIdentity saved_host_focus = app->host_focus_control;
  B32 saved_host_visible = app->host_focus_visible;

  app->reader_view_state.left_panel = ReaderViewLeftPanel_None;
  app->reader_view_state.popup = ReaderViewPopup_None;
  app->reader_view_state.focus_id = 0;
  app->host_focus_control = EightvoHostControl_ExitReader;
  app->host_focus_visible = 1;
  eightvo_reader_view_open_find_from_shortcut(app);
  B32 result =
    app->reader_view_state.pending_left_panel_focus == ReaderViewLeftPanel_Find &&
    app->host_focus_control == EightvoHostControl_None &&
    eightvo_reader_view_text_editing(app) &&
    !eightvo_reader_view_space_activates_focus(app);
  if (result)
  {
    app->input.text[0] = 'q';
    app->input.text[1] = 0;
    app->input.text_length = 1;
    eightvo_render_to_buffer(app, buffer);
    eightvo_apply_reader_view_actions(app);
    const ReaderViewSemanticNode *input =
      eightvo_reader_view_semantic_control(
        &app->reader_view_frame, ReaderViewSemanticControl_FindInput);
    result = input && app->reader_view_state.focus_id == input->id &&
      app->reader_view_state.pending_left_panel_focus ==
        ReaderViewLeftPanel_None &&
      eightvo_reader_view_text_editing(app) &&
      !eightvo_reader_view_space_activates_focus(app) &&
      eightvo_reader_view_text_equals(
        reader_view_find_query(&app->reader_view_state), "q");
  }

  app->reader_view_state = saved_state;
  app->input = saved_input;
  app->host_focus_control = saved_host_focus;
  app->host_focus_visible = saved_host_visible;
  eightvo_render_to_buffer(app, buffer);
  return result;
}

FUNCTION B32
eightvo_reader_view_reference_focus_order(EightvoApp *app,
                                           RenderBuffer *buffer)
{
  static const EightvoExpectedFocus expected[] = {
    {.shared = ReaderViewSemanticControl_Contents},
    {.shared = ReaderViewSemanticControl_Find},
    {.host = EightvoHostControl_ExitReader},
    {.shared = ReaderViewSemanticControl_Fullscreen},
    {.shared = ReaderViewSemanticControl_Annotations},
    {.shared = ReaderViewSemanticControl_FontSize},
    {.shared = ReaderViewSemanticControl_LineSpacing},
    {.shared = ReaderViewSemanticControl_FontFamily},
    {.shared = ReaderViewSemanticControl_Theme},
    {.shared = ReaderViewSemanticControl_Bookmark},
    {.shared = ReaderViewSemanticControl_PreviousPage},
    {.shared = ReaderViewSemanticControl_NextPage},
    {.shared = ReaderViewSemanticControl_Progress},
  };
  if (!app || !buffer) return 0;
  const ReaderViewSemanticNode *contents =
    eightvo_reader_view_semantic_control(&app->reader_view_frame,
                                          ReaderViewSemanticControl_Contents);
  if (!contents ||
      !reader_view_accessibility_focus(&app->reader_view_state, contents->id))
    return 0;
  (void)eightvo_host_focus_set(app, EightvoHostControl_None, 0);
  eightvo_render_to_buffer(app, buffer);

  for (U32 index = 0; index < ARRAY_COUNT(expected); index += 1)
  {
    if (expected[index].host != EightvoHostControl_None)
    {
      if (app->host_focus_control != expected[index].host) return 0;
    }
    else
    {
      const ReaderViewSemanticNode *node =
        eightvo_reader_view_semantic_control(&app->reader_view_frame,
                                              expected[index].shared);
      if (!node || app->host_focus_control != EightvoHostControl_None ||
          app->reader_view_state.focus_id != node->id)
        return 0;
    }

    if (!eightvo_host_keyboard_tab(app, 0))
      app->input.focus_next_pressed = 1;
    eightvo_render_to_buffer(app, buffer);
  }

  const ReaderViewSemanticNode *wrapped =
    eightvo_reader_view_semantic_control(&app->reader_view_frame,
                                          ReaderViewSemanticControl_Contents);
  if (!wrapped || app->host_focus_control != EightvoHostControl_None ||
      app->reader_view_state.focus_id != wrapped->id)
    return 0;

  for (U32 offset = 0; offset < ARRAY_COUNT(expected); offset += 1)
  {
    U32 index = (U32)ARRAY_COUNT(expected) - 1 - offset;
    if (!eightvo_host_keyboard_tab(app, 1))
      app->input.focus_prev_pressed = 1;
    eightvo_render_to_buffer(app, buffer);
    if (expected[index].host != EightvoHostControl_None)
    {
      if (app->host_focus_control != expected[index].host) return 0;
    }
    else
    {
      const ReaderViewSemanticNode *node =
        eightvo_reader_view_semantic_control(&app->reader_view_frame,
                                              expected[index].shared);
      if (!node || app->host_focus_control != EightvoHostControl_None ||
          app->reader_view_state.focus_id != node->id)
        return 0;
    }
  }
  return 1;
}

FUNCTION const ReaderViewSemanticNode *
eightvo_reader_view_focused_semantic(const ReaderViewFrame *frame)
{
  if (!frame || !frame->semantic_nodes) return 0;
  for (UI0S32 index = 0; index < frame->semantic_node_count; index += 1)
  {
    const ReaderViewSemanticNode *node = frame->semantic_nodes + index;
    if ((node->flags & ReaderViewSemantic_Focused) != 0) return node;
  }
  return 0;
}

FUNCTION B32
eightvo_reader_view_semantic_center_in_rect(
  const ReaderViewSemanticNode *node,
  UI0Rect rect)
{
  return node && node->rect.w > 0 && node->rect.h > 0 &&
    rect.w > 0 && rect.h > 0 &&
    ui0_rect_contains_point(rect,
                            node->rect.x + node->rect.w / 2,
                            node->rect.y + node->rect.h / 2);
}

FUNCTION B32
eightvo_reader_view_panel_focus_regression(EightvoApp *app,
                                             RenderBuffer *buffer)
{
  typedef struct EightvoPanelFocusCase
  {
    ReaderViewLeftPanelMode left_panel;
    B32 right_panel_open;
  } EightvoPanelFocusCase;
  static const EightvoPanelFocusCase cases[] = {
    {ReaderViewLeftPanel_Contents, 0},
    {ReaderViewLeftPanel_Find, 0},
    {ReaderViewLeftPanel_None, 1},
  };
  if (!app || !buffer || !epub_reader_is_open(&app->reader)) return 0;

  ReaderViewState saved_state = app->reader_view_state;
  EightvoHostControlIdentity saved_host_focus = app->host_focus_control;
  B32 saved_host_visible = app->host_focus_visible;
  U32 saved_spine = app->reader.active_spine_index;
  U64 saved_byte = app->reader.view_byte_offset;
  U32 saved_back_count = app->reader.back_stack_count;
  U32 saved_forward_count = app->reader.forward_stack_count;
  B32 result = 1;
  U32 failed_case = 0;
  U32 checkpoint = 0;

  for (U32 case_index = 0;
       result && case_index < ARRAY_COUNT(cases);
       case_index += 1)
  {
    checkpoint = 0;
    const EightvoPanelFocusCase *test = cases + case_index;
    app->reader_view_state = saved_state;
    app->reader_view_state.left_panel = test->left_panel;
    app->reader_view_state.right_panel_open = test->right_panel_open;
    app->reader_view_state.popup = ReaderViewPopup_None;
    app->reader_view_state.focus_id = 0;
    app->reader_view_state.focus_visible = 0;
    app->reader_view_state.hot_id = 0;
    app->reader_view_state.active_id = 0;
    app->reader_view_state.restore_focus_id = 0;
    app->reader_view_state.pending_accessibility_focus_id = 0;
    app->reader_view_state.pending_accessibility_invoke_id = 0;
    app->host_focus_control = EightvoHostControl_None;
    app->host_focus_visible = 0;
    eightvo_render_to_buffer(app, buffer);

    UI0Rect panel = test->right_panel_open ?
      app->reader_view_layout.right_panel_rect :
      app->reader_view_layout.left_panel_rect;
    const ReaderViewSemanticNode *progress =
      eightvo_reader_view_semantic_control(
        &app->reader_view_frame, ReaderViewSemanticControl_Progress);
    result = progress &&
      (progress->flags & ReaderViewSemantic_Focusable) != 0 &&
      reader_view_accessibility_focus(&app->reader_view_state, progress->id);
    if (result)
    {
      eightvo_render_to_buffer(app, buffer);
      progress = eightvo_reader_view_semantic_control(
        &app->reader_view_frame, ReaderViewSemanticControl_Progress);
      result = progress &&
        app->reader_view_state.focus_id == progress->id &&
        app->reader_view_state.focus_visible &&
        (progress->flags & ReaderViewSemantic_Focused) != 0 &&
        app->reader_view_frame.action_count == 0;
    }
    if (result) checkpoint = 1;

    B32 host_handled = result ? eightvo_host_keyboard_tab(app, 0) : 0;
    result = result && !host_handled;
    if (result)
    {
      app->input.focus_next_pressed = 1;
      eightvo_render_to_buffer(app, buffer);
      const ReaderViewSemanticNode *focused =
        eightvo_reader_view_focused_semantic(&app->reader_view_frame);
      result = focused && app->reader_view_state.focus_visible &&
        app->reader_view_state.focus_id == focused->id &&
        (focused->flags & ReaderViewSemantic_Focusable) != 0 &&
        eightvo_reader_view_semantic_center_in_rect(focused, panel) &&
        app->reader_view_frame.action_count == 0;
    }
    if (result) checkpoint = 2;

    host_handled = result ? eightvo_host_keyboard_tab(app, 1) : 0;
    result = result && !host_handled;
    if (result)
    {
      app->input.focus_prev_pressed = 1;
      eightvo_render_to_buffer(app, buffer);
      progress = eightvo_reader_view_semantic_control(
        &app->reader_view_frame, ReaderViewSemanticControl_Progress);
      result = progress &&
        app->reader_view_state.focus_id == progress->id &&
        app->reader_view_state.focus_visible &&
        (progress->flags & ReaderViewSemantic_Focused) != 0 &&
        app->reader_view_frame.action_count == 0;
    }
    if (result) checkpoint = 3;

    const ReaderViewSemanticNode *contents = result ?
      eightvo_reader_view_semantic_control(
        &app->reader_view_frame, ReaderViewSemanticControl_Contents) : 0;
    result = result && contents &&
      reader_view_accessibility_focus(&app->reader_view_state, contents->id);
    if (result)
    {
      eightvo_render_to_buffer(app, buffer);
      contents = eightvo_reader_view_semantic_control(
        &app->reader_view_frame, ReaderViewSemanticControl_Contents);
      result = contents &&
        app->reader_view_state.focus_id == contents->id &&
        app->reader_view_state.focus_visible &&
        (contents->flags & ReaderViewSemantic_Focused) != 0 &&
        app->reader_view_frame.action_count == 0;
    }

    host_handled = result ? eightvo_host_keyboard_tab(app, 1) : 0;
    result = result && !host_handled;
    if (result)
    {
      app->input.focus_prev_pressed = 1;
      eightvo_render_to_buffer(app, buffer);
      const ReaderViewSemanticNode *focused =
        eightvo_reader_view_focused_semantic(&app->reader_view_frame);
      result = focused && app->reader_view_state.focus_visible &&
        app->reader_view_state.focus_id == focused->id &&
        (focused->flags & ReaderViewSemantic_Focusable) != 0 &&
        eightvo_reader_view_semantic_center_in_rect(focused, panel) &&
        app->reader_view_frame.action_count == 0;
    }
    if (result) checkpoint = 4;

    host_handled = result ? eightvo_host_keyboard_tab(app, 0) : 0;
    result = result && !host_handled;
    if (result)
    {
      app->input.focus_next_pressed = 1;
      eightvo_render_to_buffer(app, buffer);
      contents = eightvo_reader_view_semantic_control(
        &app->reader_view_frame, ReaderViewSemanticControl_Contents);
      result = contents &&
        app->reader_view_state.focus_id == contents->id &&
        app->reader_view_state.focus_visible &&
        (contents->flags & ReaderViewSemantic_Focused) != 0 &&
        app->reader_view_frame.action_count == 0;
    }
    if (result) checkpoint = 5;

    result = result &&
      app->reader_view_state.left_panel == test->left_panel &&
      app->reader_view_state.right_panel_open == test->right_panel_open &&
      app->reader.active_spine_index == saved_spine &&
      app->reader.view_byte_offset == saved_byte &&
      app->reader.back_stack_count == saved_back_count &&
      app->reader.forward_stack_count == saved_forward_count;
    if (!result) failed_case = case_index + 1;
  }

  app->reader_view_state = saved_state;
  app->host_focus_control = saved_host_focus;
  app->host_focus_visible = saved_host_visible;
  eightvo_render_to_buffer(app, buffer);
  if (!result)
  {
    fprintf(stderr,
            "eightvo panel focus regression case=%u checkpoint=%u focus=%llu left=%d right=%d actions=%d\n",
            failed_case,
            checkpoint,
            (unsigned long long)app->reader_view_state.focus_id,
            (int)app->reader_view_state.left_panel,
            (int)app->reader_view_state.right_panel_open,
            (int)app->reader_view_frame.action_count);
  }
  return result && app->reader.active_spine_index == saved_spine &&
    app->reader.view_byte_offset == saved_byte &&
    app->reader.back_stack_count == saved_back_count &&
    app->reader.forward_stack_count == saved_forward_count;
}

FUNCTION B32
eightvo_reader_view_gutter_keyboard_regression(EightvoApp *app,
                                                RenderBuffer *buffer)
{
  if (!app || !buffer || !epub_reader_is_open(&app->reader)) return 0;
  U32 start_spine = app->reader.active_spine_index;
  U64 start_byte = app->reader.view_byte_offset;
  const ReaderViewSemanticNode *previous =
    eightvo_reader_view_semantic_control(&app->reader_view_frame,
                                          ReaderViewSemanticControl_PreviousPage);
  if (!previous || (previous->flags & ReaderViewSemantic_Enabled) != 0 ||
      (previous->flags & ReaderViewSemantic_Focusable) == 0)
    return 0;

  (void)eightvo_host_focus_set(app, EightvoHostControl_None, 0);
  if (!reader_view_accessibility_focus(&app->reader_view_state, previous->id))
    return 0;
  eightvo_render_to_buffer(app, buffer);
  previous = eightvo_reader_view_semantic_control(
    &app->reader_view_frame, ReaderViewSemanticControl_PreviousPage);
  const UI0DrawCommand *previous_icon = previous ?
    eightvo_reader_view_draw_for_source(
      &app->reader_view_frame, UI0DrawOp_Icon, previous->id) : 0;
  if (!previous ||
      (previous->flags & ReaderViewSemantic_Focused) == 0 ||
      !previous_icon ||
      previous_icon->icon_kind != UI0IconKind_PageCaretLeft ||
      previous_icon->rect.w != 18 || previous_icon->rect.h != 32 ||
      !eightvo_reader_view_space_activates_focus(app) ||
      !eightvo_reader_view_has_draw_for_source(
        &app->reader_view_frame, UI0DrawOp_FocusRing, previous->id))
    return 0;
  if (eightvo_reader_view_route_keydown(app, VK_SPACE, 0) !=
      EightvoReaderKeyRoute_Handled)
    return 0;
  eightvo_render_to_buffer(app, buffer);
  B32 disabled_previous_emitted = eightvo_reader_view_has_action(
    &app->reader_view_frame, ReaderViewAction_PreviousPage);
  eightvo_apply_reader_view_actions(app);
  if (disabled_previous_emitted || app->reader.active_spine_index != start_spine ||
      app->reader.view_byte_offset != start_byte)
    return 0;

  eightvo_render_to_buffer(app, buffer);
  const ReaderViewSemanticNode *next =
    eightvo_reader_view_semantic_control(&app->reader_view_frame,
                                          ReaderViewSemanticControl_NextPage);
  if (!next || (next->flags & ReaderViewSemantic_Enabled) == 0 ||
      !reader_view_accessibility_focus(&app->reader_view_state, next->id))
    return 0;
  eightvo_render_to_buffer(app, buffer);
  next = eightvo_reader_view_semantic_control(
    &app->reader_view_frame, ReaderViewSemanticControl_NextPage);
  const UI0DrawCommand *next_icon = next ?
    eightvo_reader_view_draw_for_source(
      &app->reader_view_frame, UI0DrawOp_Icon, next->id) : 0;
  if (!next || !next_icon ||
      next_icon->icon_kind != UI0IconKind_PageCaretRight ||
      next_icon->rect.w != 18 || next_icon->rect.h != 32 ||
      !eightvo_reader_view_space_activates_focus(app))
    return 0;
  if (eightvo_reader_view_route_keydown(app, VK_SPACE, 0) !=
      EightvoReaderKeyRoute_Handled)
    return 0;
  eightvo_render_to_buffer(app, buffer);
  B32 next_emitted = eightvo_reader_view_has_action(
    &app->reader_view_frame, ReaderViewAction_NextPage);
  eightvo_apply_reader_view_actions(app);
  B32 moved_next = app->reader.active_spine_index != start_spine ||
                   app->reader.view_byte_offset != start_byte;
  if (!next_emitted || !moved_next) return 0;

  eightvo_render_to_buffer(app, buffer);
  previous = eightvo_reader_view_semantic_control(
    &app->reader_view_frame, ReaderViewSemanticControl_PreviousPage);
  if (!previous || (previous->flags & ReaderViewSemantic_Enabled) == 0 ||
      !reader_view_accessibility_focus(&app->reader_view_state, previous->id))
    return 0;
  eightvo_render_to_buffer(app, buffer);
  if (!eightvo_reader_view_space_activates_focus(app)) return 0;
  if (eightvo_reader_view_route_keydown(app, VK_SPACE, 0) !=
      EightvoReaderKeyRoute_Handled)
    return 0;
  eightvo_render_to_buffer(app, buffer);
  B32 previous_emitted = eightvo_reader_view_has_action(
    &app->reader_view_frame, ReaderViewAction_PreviousPage);
  eightvo_apply_reader_view_actions(app);
  if (!previous_emitted || app->reader.active_spine_index != start_spine ||
      app->reader.view_byte_offset != start_byte)
    return 0;

  eightvo_render_to_buffer(app, buffer);
  next = eightvo_reader_view_semantic_control(
    &app->reader_view_frame, ReaderViewSemanticControl_NextPage);
  if (!next || !eightvo_reader_view_parity_click_node(app, buffer, next) ||
      (app->reader.active_spine_index == start_spine &&
       app->reader.view_byte_offset == start_byte))
    return 0;
  eightvo_render_to_buffer(app, buffer);
  previous = eightvo_reader_view_semantic_control(
    &app->reader_view_frame, ReaderViewSemanticControl_PreviousPage);
  return previous &&
    eightvo_reader_view_parity_click_node(app, buffer, previous) &&
    app->reader.active_spine_index == start_spine &&
    app->reader.view_byte_offset == start_byte;
}

FUNCTION B32
eightvo_reader_view_navigation_panel_interaction_regression(
  EightvoApp *app,
  RenderBuffer *buffer,
  ReaderViewKey find_key)
{
  if (!app || !buffer || find_key < 0x100000ull ||
      find_key - 0x100000ull > 0xffffffffull ||
      !epub_reader_is_open(&app->reader))
    return 0;
  U32 start_spine = app->reader.active_spine_index;
  U64 start_byte = app->reader.view_byte_offset;
  U32 start_back_count = app->reader.back_stack_count;
  U32 checkpoint = 0;

  app->reader_view_state.left_panel = ReaderViewLeftPanel_None;
  app->reader_view_state.popup = ReaderViewPopup_None;
  eightvo_render_to_buffer(app, buffer);
  const ReaderViewSemanticNode *contents =
    eightvo_reader_view_semantic_control(
      &app->reader_view_frame, ReaderViewSemanticControl_Contents);
  B32 result = contents &&
    eightvo_reader_view_parity_space_node(app, buffer, contents) &&
    app->reader_view_state.left_panel == ReaderViewLeftPanel_Contents &&
    app->draw_adapter_stats.unsupported_count == 0;
  if (result) checkpoint = 1;

  const ReaderViewSemanticNode *toc_row = result ?
    eightvo_reader_view_semantic_control_source(
      &app->reader_view_frame, ReaderViewSemanticControl_TocRow, 2) : 0;
  result = result && toc_row &&
    eightvo_reader_view_parity_space_node(app, buffer, toc_row) &&
    (app->reader.active_spine_index != start_spine ||
     app->reader.view_byte_offset != start_byte) &&
    app->reader.back_stack_count > start_back_count;
  if (result) checkpoint = 2;

  if (result)
  {
    eightvo_apply_reader_view_action(app, &(ReaderViewAction){
      .kind = ReaderViewAction_HistoryBack,
    });
    eightvo_render_to_buffer(app, buffer);
    result = app->reader.active_spine_index == start_spine &&
      app->reader.view_byte_offset == start_byte;
  }
  if (result) checkpoint = 3;

  const ReaderViewSemanticNode *find = result ?
    eightvo_reader_view_semantic_control(
      &app->reader_view_frame, ReaderViewSemanticControl_Find) : 0;
  result = result && find &&
    eightvo_reader_view_parity_space_node(app, buffer, find) &&
    app->reader_view_state.left_panel == ReaderViewLeftPanel_Find &&
    app->draw_adapter_stats.unsupported_count == 0;
  if (result) checkpoint = 4;

  const ReaderViewSemanticNode *find_row = result ?
    eightvo_reader_view_semantic_control_source(
      &app->reader_view_frame, ReaderViewSemanticControl_FindRow, find_key) : 0;
  U32 expected_find_index = (U32)(find_key - 0x100000ull);
  result = result && find_row &&
    eightvo_reader_view_parity_space_node(app, buffer, find_row) &&
    app->reader.search_has_active &&
    app->reader.search_active_index == expected_find_index &&
    app->reader_view_state.left_panel == ReaderViewLeftPanel_Find;
  if (result) checkpoint = 5;

  const ReaderViewSemanticNode *close = result ?
    eightvo_reader_view_semantic_control(
      &app->reader_view_frame, ReaderViewSemanticControl_LeftPanelClose) : 0;
  result = result && close &&
    eightvo_reader_view_parity_click_node(app, buffer, close) &&
    app->reader_view_state.left_panel == ReaderViewLeftPanel_None &&
    app->reader_view_state.popup == ReaderViewPopup_None &&
    app->reader_view_frame.action_count == 0 &&
    app->draw_adapter_stats.unsupported_count == 0;
  if (result) checkpoint = 6;

  if (!result)
  {
    fprintf(stderr,
            "eightvo navigation panel interaction checkpoint=%u left=%d popup=%d spine=%u byte=%llu active=%u/%u\n",
            checkpoint,
            (int)app->reader_view_state.left_panel,
            (int)app->reader_view_state.popup,
            app->reader.active_spine_index,
            (unsigned long long)app->reader.view_byte_offset,
            app->reader.search_has_active,
            app->reader.search_active_index);
  }
  return result;
}

FUNCTION B32
eightvo_reader_view_has_action(const ReaderViewFrame *frame,
                                ReaderViewActionKind kind)
{
  if (!frame || !frame->actions) return 0;
  for (UI0S32 index = 0; index < frame->action_count; index += 1)
    if (frame->actions[index].kind == kind) return 1;
  return 0;
}

FUNCTION B32
eightvo_reader_view_document_selection_is(
  const EightvoApp *app,
  DocSelection selection,
  const char *selected_text,
  UI0Rect anchor)
{
  if (!app || !selected_text || !app->reader.has_active_selection) return 0;
  return app->reader.active_selection.spine_index == selection.spine_index &&
    app->reader.active_selection.text_byte_start == selection.text_byte_start &&
    app->reader.active_selection.text_byte_end == selection.text_byte_end &&
    strcmp(app->selected_text, selected_text) == 0 &&
    app->selection_anchor_rect.x == anchor.x &&
    app->selection_anchor_rect.y == anchor.y &&
    app->selection_anchor_rect.w == anchor.w &&
    app->selection_anchor_rect.h == anchor.h;
}

FUNCTION B32
eightvo_reader_view_annotation_interaction_regression(
  EightvoApp *app,
  RenderBuffer *buffer,
  ReaderViewKey note_key)
{
  if (!app || !buffer || note_key == 0) return 0;
  const EightvoReaderViewRightSource *source =
    eightvo_reader_view_right_source(
      app, note_key, ReaderViewRightRow_Note);
  S32 highlight_index = source ?
    eightvo_highlight_index(app, source->record_id) : -1;
  if (highlight_index < 0) return 0;

  ReaderViewState saved_state = app->reader_view_state;
  U64 saved_revision = app->annotation_revision;
  B32 saved_has_selection = app->reader.has_active_selection;
  DocSelection saved_selection = app->reader.active_selection;
  char saved_selected_text[EightvoSelectionTextCap] = {0};
  MemoryCopy(saved_selected_text, app->selected_text,
             sizeof(saved_selected_text));
  UI0Rect saved_selection_anchor = app->selection_anchor_rect;
  B32 result = 1;
  U32 checkpoint = 0;
  UI0ID annotations_id = 0;

  app->reader_view_state.left_panel = ReaderViewLeftPanel_None;
  app->reader_view_state.right_panel_open = 0;
  app->reader_view_state.popup = ReaderViewPopup_None;
  app->reader_view_state.focus_id = 0;
  app->reader_view_state.restore_focus_id = 0;
  epub_reader_clear_selection(&app->reader);
  app->selected_text[0] = 0;
  app->selection_anchor_rect = (UI0Rect){0};
  (void)eightvo_capture_frame(app);
  eightvo_prepare_reader_view_projection(app);
  eightvo_render_to_buffer(app, buffer);

  const ReaderViewSemanticNode *annotations =
    eightvo_reader_view_semantic_control(
      &app->reader_view_frame, ReaderViewSemanticControl_Annotations);
  annotations_id = annotations ? annotations->id : 0;
  ReaderViewSemanticFlags annotations_flags = annotations ?
    annotations->flags : ReaderViewSemantic_None;
  B32 queued_focus = annotations &&
    reader_view_accessibility_focus(&app->reader_view_state, annotations->id);
  B32 queued_invoke = 0;
  result = annotations && queued_focus;
  if (result)
  {
    eightvo_render_to_buffer(app, buffer);
    annotations = eightvo_reader_view_semantic_control(
      &app->reader_view_frame, ReaderViewSemanticControl_Annotations);
    queued_invoke = annotations &&
      app->reader_view_state.focus_id == annotations->id &&
      eightvo_reader_view_space_activates_focus(app) &&
      reader_view_accessibility_invoke(
        &app->reader_view_state, annotations->id);
    result = queued_invoke;
  }
  if (result)
  {
    eightvo_render_to_buffer(app, buffer);
    eightvo_apply_reader_view_actions(app);
    result = app->reader_view_state.right_panel_open &&
      app->reader_view_state.popup == ReaderViewPopup_None &&
      app->draw_adapter_stats.unsupported_count == 0;
    if (result)
      eightvo_render_to_buffer(app, buffer);
  }
  if (result) checkpoint = 1;

  const ReaderViewSemanticNode *close = result ?
    eightvo_reader_view_semantic_control(
      &app->reader_view_frame, ReaderViewSemanticControl_RightPanelClose) : 0;
  result = result && close &&
    reader_view_accessibility_focus(&app->reader_view_state, close->id) &&
    reader_view_accessibility_invoke(&app->reader_view_state, close->id);
  if (result)
  {
    eightvo_render_to_buffer(app, buffer);
    eightvo_apply_reader_view_actions(app);
    result = !app->reader_view_state.right_panel_open &&
      app->reader_view_state.popup == ReaderViewPopup_None &&
      app->reader_view_state.focus_id == annotations_id;
  }
  if (result) checkpoint = 2;

  annotations = result ? eightvo_reader_view_semantic_control(
    &app->reader_view_frame, ReaderViewSemanticControl_Annotations) : 0;
  result = result && annotations &&
    reader_view_accessibility_invoke(&app->reader_view_state, annotations->id);
  if (result)
  {
    eightvo_render_to_buffer(app, buffer);
    eightvo_apply_reader_view_actions(app);
    result = app->reader_view_state.right_panel_open;
    if (result)
      eightvo_render_to_buffer(app, buffer);
  }
  if (result) checkpoint = 3;

  const ReaderViewSemanticNode *filter = result ?
    eightvo_reader_view_semantic_control(
      &app->reader_view_frame, ReaderViewSemanticControl_RightFilter) : 0;
  result = result && filter &&
    reader_view_accessibility_focus(&app->reader_view_state, filter->id) &&
    reader_view_accessibility_invoke(&app->reader_view_state, filter->id);
  if (result)
  {
    eightvo_render_to_buffer(app, buffer);
    result = app->reader_view_state.popup == ReaderViewPopup_RightFilter;
    if (result)
      eightvo_render_to_buffer(app, buffer);
  }
  if (result) checkpoint = 4;

  const ReaderViewSemanticNode *bookmarks_option = result ?
    eightvo_reader_view_semantic_control_source(
      &app->reader_view_frame,
      ReaderViewSemanticControl_RightFilterOption,
      (ReaderViewKey)ReaderViewRightFilter_Bookmarks) : 0;
  result = result && bookmarks_option &&
    reader_view_accessibility_focus(
      &app->reader_view_state, bookmarks_option->id) &&
    reader_view_accessibility_invoke(
      &app->reader_view_state, bookmarks_option->id);
  if (result)
  {
    eightvo_render_to_buffer(app, buffer);
    result = app->reader_view_state.popup == ReaderViewPopup_None &&
      app->reader_view_state.right_filter == ReaderViewRightFilter_Bookmarks &&
      eightvo_reader_view_has_action(
        &app->reader_view_frame, ReaderViewAction_RightFilterChanged);
    eightvo_apply_reader_view_actions(app);
  }
  if (result) checkpoint = 5;

  if (result)
  {
    app->reader_view_state.right_filter = ReaderViewRightFilter_All;
    app->reader_view_state.popup = ReaderViewPopup_None;
    eightvo_prepare_reader_view_projection(app);
    eightvo_render_to_buffer(app, buffer);
  }
  const ReaderViewSemanticNode *menu = result ?
    eightvo_reader_view_semantic_control_source(
      &app->reader_view_frame,
      ReaderViewSemanticControl_RightRowMenu,
      note_key) : 0;
  result = result && menu &&
    reader_view_accessibility_focus(&app->reader_view_state, menu->id) &&
    reader_view_accessibility_invoke(&app->reader_view_state, menu->id);
  if (result)
  {
    eightvo_render_to_buffer(app, buffer);
    result = app->reader_view_state.popup == ReaderViewPopup_RightRowActions;
    if (result)
      eightvo_render_to_buffer(app, buffer);
  }
  if (result) checkpoint = 6;

  const ReaderViewSemanticNode *edit_note = result ?
    eightvo_reader_view_semantic_control_source(
      &app->reader_view_frame,
      ReaderViewSemanticControl_RightActionEditNote,
      note_key) : 0;
  U32 before_spine = app->reader.active_spine_index;
  U64 before_byte = app->reader.view_byte_offset;
  U32 before_back_count = app->reader.back_stack_count;
  U32 before_forward_count = app->reader.forward_stack_count;
  result = result && edit_note &&
    reader_view_accessibility_focus(
      &app->reader_view_state, edit_note->id) &&
    reader_view_accessibility_invoke(
      &app->reader_view_state, edit_note->id);
  if (result)
  {
    eightvo_render_to_buffer(app, buffer);
    result = app->reader_view_state.popup == ReaderViewPopup_None &&
      eightvo_reader_view_has_action(
        &app->reader_view_frame, ReaderViewAction_EditRightRowNote);
    eightvo_apply_reader_view_actions(app);
    result = result &&
      app->reader_view_state.popup == ReaderViewPopup_NoteEditor &&
      app->annotation_note_highlight_id ==
        app->highlights[highlight_index].id &&
      app->reader.active_spine_index == before_spine &&
      app->reader.view_byte_offset == before_byte &&
      app->reader.back_stack_count == before_back_count &&
      app->reader.forward_stack_count == before_forward_count &&
      reader_view_close_note_editor(&app->reader_view_state);
    eightvo_reader_view_clear_annotation_note_target(app);
  }
  if (result) checkpoint = 7;

  if (!result)
  {
    fprintf(stderr,
            "eightvo annotation interaction checkpoint=%u ready=%d errors=%u popup=%d right=%d filter=%d focus=%llu annotation=%llu flags=%u queued=%d/%d pending=%llu/%llu\n",
            checkpoint,
            (int)app->reader_view_ready,
            (unsigned)app->reader_view_frame.error_flags,
            (int)app->reader_view_state.popup,
            (int)app->reader_view_state.right_panel_open,
            (int)app->reader_view_state.right_filter,
            (unsigned long long)app->reader_view_state.focus_id,
            (unsigned long long)annotations_id,
            (unsigned)annotations_flags,
            (int)queued_focus,
            (int)queued_invoke,
            (unsigned long long)
              app->reader_view_state.pending_accessibility_focus_id,
            (unsigned long long)
              app->reader_view_state.pending_accessibility_invoke_id);
  }

  app->annotation_revision = saved_revision;
  app->reader_view_state = saved_state;
  if (saved_has_selection)
    (void)epub_reader_set_selection(&app->reader, saved_selection);
  MemoryCopy(app->selected_text, saved_selected_text,
             sizeof(saved_selected_text));
  app->selection_anchor_rect = saved_selection_anchor;
  (void)eightvo_capture_frame(app);
  eightvo_prepare_reader_view_projection(app);
  eightvo_render_to_buffer(app, buffer);
  return result && app->draw_adapter_stats.unsupported_count == 0;
}

FUNCTION B32
eightvo_reader_view_pointer_open_annotation_note(
  EightvoApp *app,
  RenderBuffer *buffer,
  ReaderViewKey note_key)
{
  if (!app || !buffer || note_key == 0) return 0;
  const ReaderViewSemanticNode *menu =
    eightvo_reader_view_semantic_control_source(
      &app->reader_view_frame,
      ReaderViewSemanticControl_RightRowMenu,
      note_key);
  U32 before_spine = app->reader.active_spine_index;
  U64 before_byte = app->reader.view_byte_offset;
  U32 before_back_count = app->reader.back_stack_count;
  U32 before_forward_count = app->reader.forward_stack_count;
  if (!menu || !eightvo_reader_view_parity_click_node(app, buffer, menu) ||
      app->reader_view_state.popup != ReaderViewPopup_RightRowActions ||
      app->reader.active_spine_index != before_spine ||
      app->reader.view_byte_offset != before_byte ||
      app->reader.back_stack_count != before_back_count ||
      app->reader.forward_stack_count != before_forward_count)
    return 0;
  const ReaderViewSemanticNode *edit =
    eightvo_reader_view_semantic_control_source(
      &app->reader_view_frame,
      ReaderViewSemanticControl_RightActionEditNote,
      note_key);
  return edit &&
    eightvo_reader_view_parity_click_node(app, buffer, edit) &&
    app->reader_view_state.popup == ReaderViewPopup_NoteEditor &&
    app->reader.active_spine_index == before_spine &&
    app->reader.view_byte_offset == before_byte &&
    app->reader.back_stack_count == before_back_count &&
    app->reader.forward_stack_count == before_forward_count;
}

FUNCTION B32
eightvo_reader_view_pointer_replace_note_draft(
  EightvoApp *app,
  RenderBuffer *buffer,
  const char *text)
{
  if (!app || !buffer || !text ||
      app->reader_view_state.popup != ReaderViewPopup_NoteEditor)
    return 0;
  size_t size = strlen(text);
  if (size == 0 || size >= ARRAY_COUNT(app->input.text)) return 0;
  const ReaderViewSemanticNode *editor =
    eightvo_reader_view_parity_semantic(
      app, ReaderViewSemantic_TextArea, 0);
  if (!editor || !eightvo_reader_view_parity_click_node(app, buffer, editor))
    return 0;
  app->input.select_all_pressed = 1;
  MemoryCopy(app->input.text, text, (U64)size);
  app->input.text[size] = 0;
  app->input.text_length = (S32)size;
  eightvo_render_to_buffer(app, buffer);
  eightvo_apply_reader_view_actions(app);
  return app->reader_view_state.popup == ReaderViewPopup_NoteEditor &&
    app->reader_view_state.note_dirty &&
    eightvo_reader_view_text_is(reader_view_note_draft(
      &app->reader_view_state), text);
}

FUNCTION B32
eightvo_reader_view_annotation_pointer_regression(
  EightvoApp *app,
  RenderBuffer *buffer,
  ReaderViewKey note_key)
{
  if (!app || !buffer || note_key == 0) return 0;
  const EightvoReaderViewRightSource *source =
    eightvo_reader_view_right_source(
      app, note_key, ReaderViewRightRow_Note);
  S32 highlight_index = source ?
    eightvo_highlight_index(app, source->record_id) : -1;
  if (highlight_index < 0 || app->highlights[highlight_index].note[0] == 0)
    return 0;

  ReaderViewState saved_state = app->reader_view_state;
  EightvoHighlight saved_highlight = app->highlights[highlight_index];
  U64 saved_revision = app->annotation_revision;
  B32 saved_has_selection = app->reader.has_active_selection;
  DocSelection saved_selection = app->reader.active_selection;
  char saved_selected_text[EightvoSelectionTextCap] = {0};
  MemoryCopy(saved_selected_text, app->selected_text,
             sizeof(saved_selected_text));
  UI0Rect saved_selection_anchor = app->selection_anchor_rect;
  B32 result = 1;
  U32 checkpoint = 0;
  U32 save_semantic_flags = 0;
  U64 save_source_revision = 0;
  U64 save_annotation_revision = 0;
  U64 save_projection_revision = 0;

  app->reader_view_state.left_panel = ReaderViewLeftPanel_None;
  app->reader_view_state.right_panel_open = 0;
  app->reader_view_state.right_filter = ReaderViewRightFilter_All;
  app->reader_view_state.popup = ReaderViewPopup_None;
  app->reader_view_state.focus_id = 0;
  app->reader_view_state.restore_focus_id = 0;
  epub_reader_clear_selection(&app->reader);
  app->selected_text[0] = 0;
  app->selection_anchor_rect = (UI0Rect){0};
  (void)eightvo_capture_frame(app);
  eightvo_prepare_reader_view_projection(app);
  eightvo_render_to_buffer(app, buffer);

  const ReaderViewSemanticNode *annotations =
    eightvo_reader_view_semantic_control(
      &app->reader_view_frame, ReaderViewSemanticControl_Annotations);
  result = annotations &&
    eightvo_reader_view_parity_click_node(app, buffer, annotations) &&
    app->reader_view_state.right_panel_open &&
    app->reader_view_state.popup == ReaderViewPopup_None;
  if (result) checkpoint = 1;

  const ReaderViewSemanticNode *filter = result ?
    eightvo_reader_view_semantic_control(
      &app->reader_view_frame, ReaderViewSemanticControl_RightFilter) : 0;
  UI0ID filter_id = filter ? filter->id : 0;
  result = result && filter &&
    eightvo_reader_view_parity_click_node(app, buffer, filter) &&
    app->reader_view_state.popup == ReaderViewPopup_RightFilter;
  if (result) checkpoint = 2;

  if (result)
  {
    app->input.escape_pressed = 1;
    eightvo_render_to_buffer(app, buffer);
    eightvo_apply_reader_view_actions(app);
    filter = eightvo_reader_view_semantic_control(
      &app->reader_view_frame, ReaderViewSemanticControl_RightFilter);
    result = app->reader_view_state.popup == ReaderViewPopup_None &&
      app->reader_view_state.right_panel_open && filter &&
      app->reader_view_state.focus_id == filter_id &&
      app->reader_view_state.focus_visible &&
      (filter->flags & ReaderViewSemantic_Focused) != 0;
  }
  if (result) checkpoint = 3;

  filter = result ? eightvo_reader_view_semantic_control(
    &app->reader_view_frame, ReaderViewSemanticControl_RightFilter) : 0;
  result = result && filter &&
    eightvo_reader_view_parity_click_node(app, buffer, filter);
  const ReaderViewSemanticNode *bookmarks_option = result ?
    eightvo_reader_view_semantic_control_source(
      &app->reader_view_frame,
      ReaderViewSemanticControl_RightFilterOption,
      (ReaderViewKey)ReaderViewRightFilter_Bookmarks) : 0;
  result = result && bookmarks_option &&
    eightvo_reader_view_parity_click_node(
      app, buffer, bookmarks_option) &&
    app->reader_view_state.popup == ReaderViewPopup_None &&
    app->reader_view_state.right_filter == ReaderViewRightFilter_Bookmarks;
  if (result) checkpoint = 4;

  filter = result ? eightvo_reader_view_semantic_control(
    &app->reader_view_frame, ReaderViewSemanticControl_RightFilter) : 0;
  result = result && filter &&
    eightvo_reader_view_parity_click_node(app, buffer, filter);
  const ReaderViewSemanticNode *all_option = result ?
    eightvo_reader_view_semantic_control_source(
      &app->reader_view_frame,
      ReaderViewSemanticControl_RightFilterOption,
      (ReaderViewKey)ReaderViewRightFilter_All) : 0;
  result = result && all_option &&
    eightvo_reader_view_parity_click_node(app, buffer, all_option) &&
    app->reader_view_state.popup == ReaderViewPopup_None &&
    app->reader_view_state.right_filter == ReaderViewRightFilter_All;
  if (result) checkpoint = 5;

  const ReaderViewSemanticNode *row = result ?
    eightvo_reader_view_semantic_control_source(
      &app->reader_view_frame, ReaderViewSemanticControl_RightRow,
      note_key) : 0;
  result = result && row &&
    eightvo_reader_view_parity_click_node(app, buffer, row) &&
    app->reader.active_spine_index == saved_highlight.spine_index;
  if (result) checkpoint = 6;

  B32 initial_star = saved_highlight.note_starred;
  U32 star_spine = app->reader.active_spine_index;
  U64 star_byte = app->reader.view_byte_offset;
  U32 star_back_count = app->reader.back_stack_count;
  U32 star_forward_count = app->reader.forward_stack_count;
  const ReaderViewSemanticNode *star = result ?
    eightvo_reader_view_semantic_control_source(
      &app->reader_view_frame, ReaderViewSemanticControl_RightRowStar,
      note_key) : 0;
  result = result && star &&
    eightvo_reader_view_parity_click_node(app, buffer, star) &&
    app->highlights[highlight_index].note_starred != initial_star &&
    app->reader.active_spine_index == star_spine &&
    app->reader.view_byte_offset == star_byte &&
    app->reader.back_stack_count == star_back_count &&
    app->reader.forward_stack_count == star_forward_count;
  star = result ? eightvo_reader_view_semantic_control_source(
    &app->reader_view_frame, ReaderViewSemanticControl_RightRowStar,
    note_key) : 0;
  result = result && star &&
    eightvo_reader_view_parity_click_node(app, buffer, star) &&
    app->highlights[highlight_index].note_starred == initial_star &&
    app->reader.active_spine_index == star_spine &&
    app->reader.view_byte_offset == star_byte &&
    app->reader.back_stack_count == star_back_count &&
    app->reader.forward_stack_count == star_forward_count;
  if (result) checkpoint = 7;

  result = result && eightvo_reader_view_pointer_open_annotation_note(
    app, buffer, note_key) &&
    eightvo_reader_view_pointer_replace_note_draft(
      app, buffer, "Pointer saved note");
  const ReaderViewSemanticNode *save = result ?
    eightvo_reader_view_parity_semantic(
      app, ReaderViewSemantic_Button, "Save note") : 0;
  if (save)
  {
    save_semantic_flags = save->flags;
    save_source_revision = app->reader_view_state.note_source_revision;
    save_annotation_revision = app->annotation_revision;
    save_projection_revision = app->reader_view_projection.selection.revision;
  }
  result = result && save &&
    (save->flags & (ReaderViewSemantic_Enabled |
                    ReaderViewSemantic_Focusable)) ==
      (ReaderViewSemantic_Enabled | ReaderViewSemantic_Focusable) &&
    eightvo_reader_view_parity_click_node(app, buffer, save) &&
    app->reader_view_state.popup == ReaderViewPopup_None &&
    strcmp(app->highlights[highlight_index].note, "Pointer saved note") == 0 &&
    app->annotation_note_selection_key == 0;
  if (result) checkpoint = 8;

  result = result && eightvo_save_note_at_index(
    app, (U32)highlight_index,
    eightvo_reader_view_text(saved_highlight.note));
  eightvo_prepare_reader_view_projection(app);
  eightvo_render_to_buffer(app, buffer);
  result = result && eightvo_reader_view_pointer_open_annotation_note(
    app, buffer, note_key) &&
    eightvo_reader_view_pointer_replace_note_draft(
      app, buffer, "Pointer cancelled note");
  const ReaderViewSemanticNode *cancel = result ?
    eightvo_reader_view_parity_semantic(
      app, ReaderViewSemantic_Button, "Cancel note") : 0;
  result = result && cancel &&
    eightvo_reader_view_parity_click_node(app, buffer, cancel) &&
    app->reader_view_state.popup == ReaderViewPopup_None &&
    strcmp(app->highlights[highlight_index].note, saved_highlight.note) == 0 &&
    app->annotation_note_selection_key == 0;
  if (result) checkpoint = 9;

  result = result && eightvo_reader_view_pointer_open_annotation_note(
    app, buffer, note_key);
  const ReaderViewSemanticNode *delete_note = result ?
    eightvo_reader_view_parity_semantic(
      app, ReaderViewSemantic_Button, "Delete note") : 0;
  result = result && delete_note &&
    eightvo_reader_view_parity_click_node(app, buffer, delete_note) &&
    app->reader_view_state.popup == ReaderViewPopup_None &&
    app->highlights[highlight_index].note[0] == 0 &&
    app->annotation_note_selection_key == 0;
  if (result) checkpoint = 10;

  app->highlights[highlight_index] = saved_highlight;
  app->annotation_revision = saved_revision;
  B32 restore_persisted = !app->persistence_enabled ||
                          eightvo_save_annotations(app);
  result = result && restore_persisted;
  eightvo_prepare_reader_view_projection(app);
  eightvo_render_to_buffer(app, buffer);
  const ReaderViewSemanticNode *close = result ?
    eightvo_reader_view_semantic_control(
      &app->reader_view_frame, ReaderViewSemanticControl_RightPanelClose) : 0;
  B32 close_present = close != 0;
  B32 close_clicked = result && close &&
    eightvo_reader_view_parity_click_node(app, buffer, close);
  result = result && close_present && close_clicked &&
    !app->reader_view_state.right_panel_open &&
    app->reader_view_state.popup == ReaderViewPopup_None &&
    app->draw_adapter_stats.unsupported_count == 0;
  if (result) checkpoint = 11;

  if (!result)
  {
    fprintf(stderr,
            "eightvo annotation pointer regression checkpoint=%u popup=%d right=%d filter=%d note=%s star=%d target=%llu save_flags=%u revisions=%llu/%llu/%llu persisted=%d close=%d/%d\n",
            checkpoint,
            (int)app->reader_view_state.popup,
            (int)app->reader_view_state.right_panel_open,
            (int)app->reader_view_state.right_filter,
            app->highlights[highlight_index].note,
            app->highlights[highlight_index].note_starred,
            (unsigned long long)app->annotation_note_selection_key,
            (unsigned)save_semantic_flags,
            (unsigned long long)save_source_revision,
            (unsigned long long)save_annotation_revision,
            (unsigned long long)save_projection_revision,
            restore_persisted,
            close_present,
            close_clicked);
  }

  app->highlights[highlight_index] = saved_highlight;
  app->annotation_revision = saved_revision;
  (void)eightvo_save_annotations(app);
  app->reader_view_state = saved_state;
  if (saved_has_selection)
    (void)epub_reader_set_selection(&app->reader, saved_selection);
  else
    epub_reader_clear_selection(&app->reader);
  MemoryCopy(app->selected_text, saved_selected_text,
             sizeof(saved_selected_text));
  app->selection_anchor_rect = saved_selection_anchor;
  (void)eightvo_capture_frame(app);
  eightvo_prepare_reader_view_projection(app);
  eightvo_render_to_buffer(app, buffer);
  return result && app->draw_adapter_stats.unsupported_count == 0;
}

FUNCTION U32
eightvo_draw_text_command_count(const EightvoApp *app,
                                 const char *text)
{
  if (!app || !text) return 0;
  U32 result = 0;
  for (DrawLayer layer = DrawLayer_Background;
       layer < DrawLayer_Count;
       layer = (DrawLayer)(layer + 1))
  {
    U16 count = app->draw_commands.command_count[layer];
    for (U16 index = 0; index < count; index += 1)
    {
      const DrawCommand *command = app->draw_commands.commands[layer] + index;
      if (command->type == DrawCommandType_Text &&
          strcmp(command->v.text.text, text) == 0)
        result += 1;
    }
  }
  return result;
}

FUNCTION int
eightvo_run_reader_view_startup_interaction_smoke(void)
{
  enum { Width = 1100, Height = 760 };
  EightvoApp app = {0};
  U32 *pixels = (U32 *)calloc((size_t)Width * Height, sizeof(U32));
  if (!pixels || !eightvo_app_init(&app, Width, Height, 1, 0))
  {
    fprintf(stderr,
            "eightvo_reader_view_startup_interaction result=fail reason=setup\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  app.suppress_native_picker = 1;
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);
  EightvoHostControlRecord *add = eightvo_host_control_record(
    &app, EightvoHostControl_LibraryAdd);
  U32 empty_message_count =
    eightvo_draw_text_command_count(&app, "Your library is empty");
  B32 initial = eightvo_library_active(&app) &&
                app.library.entry_count == 0 && !app.reader_view_ready &&
                add && add->semantic.rect.w > 0 &&
                empty_message_count == 1;
  UI0Rect cta = eightvo_library_empty_add_rect(&app);
  S32 x = cta.x + cta.w / 2;
  S32 y = cta.y + cta.h / 2;
  eightvo_host_pointer_press(&app, x, y);
  B32 armed = app.host_pointer_armed == EightvoHostControl_LibraryAdd;
  eightvo_host_pointer_move(&app, 0, Height - 1);
  B32 cancel_suppressed =
    !eightvo_host_pointer_release(&app, x, y) &&
    app.native_picker_request_count == 0;
  eightvo_host_pointer_press(&app, x, y);
  B32 pointer_add = eightvo_host_pointer_release(&app, x, y) &&
                    app.native_picker_request_count == 1;
  eightvo_render_to_buffer(&app, &buffer);
  B32 tab_focus = eightvo_host_focus_set(
                    &app, EightvoHostControl_LibraryAdd, 1) &&
                  eightvo_host_keyboard_tab(&app, 0) &&
                  app.host_focus_control == EightvoHostControl_LibraryAdd &&
                  app.host_focus_visible;
  B32 keyboard_add = eightvo_host_keyboard_activate(&app) &&
                     app.native_picker_request_count == 2;
  if (!initial || !armed || !cancel_suppressed || !pointer_add ||
      !tab_focus || !keyboard_add)
  {
    fprintf(stderr,
            "eightvo_reader_view_startup_interaction result=fail reason=library initial=%d armed=%d cancel=%d pointer=%d tab=%d keyboard=%d requests=%u\n",
            initial, armed, cancel_suppressed, pointer_add, tab_focus,
            keyboard_add, app.native_picker_request_count);
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  fprintf(stdout,
          "eightvo_reader_view_startup_interaction result=pass surface=library catalog=empty lifecycle=press_release capture=cancel_and_release action=add_books picker=suppressed focus=pointer_keyboard accessibility=host_semantics\n");
  free(pixels);
  eightvo_app_release(&app);
  return 0;
}

FUNCTION int
eightvo_run_reader_view_selection_menu_smoke(const char *epub_path,
                                               const char *output_prefix)
{
  enum { Width = 1100, Height = 760 };
  EightvoApp app = {0};
  U32 *pixels = (U32 *)calloc((size_t)Width * Height, sizeof(U32));
  U32 checkpoint = 0;
  int result = 1;
  char bmp_path[EightvoPathCap] = {0};
  U32 selection_rows[2] = {0};
  U32 selection_row_count = 0;
  U64 expected_start = 0;
  U64 expected_end = 0;
  S32 end_x = 0;
  S32 end_y = 0;
  if (!pixels || !epub_path || !output_prefix ||
      !eightvo_app_init(&app, Width, Height, 1, 0) ||
      !eightvo_open_path(&app, epub_path) ||
      !epub_reader_rebuild_search(&app.reader, str8_from_cstr("Vane")) ||
      app.reader.search_match_count == 0 ||
      eightvo_navigate_to_search_match(
        &app, 0, &(EpubReaderSearchNavigationResult){0}) != EpubReaderResult_Ok)
  {
    fprintf(stderr,
            "eightvo_reader_view_selection_menu result=fail checkpoint=0 reason=setup\n");
    goto cleanup;
  }

  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);
  U64 current_presentation_hash = app.presentation_hash;
  U32 current_presentation_row_count = app.presentation_frame.row_count;
  eightvo_schedule_adjacent_warm(&app);
  B32 adjacent_warm_ran = eightvo_adjacent_warm_step(&app);
  B32 current_presentation_preserved =
    adjacent_warm_ran &&
    current_presentation_hash != 0 &&
    app.presentation_frame.valid &&
    app.presentation_hash == current_presentation_hash &&
    app.presentation_frame.row_count == current_presentation_row_count &&
    eightvo_frame_presentation_is_complete(&app);
  if (!current_presentation_preserved)
  {
    fprintf(stderr,
            "eightvo_reader_view_selection_menu result=fail checkpoint=0 reason=adjacent_presentation ran=%d hash=%llu/%llu rows=%u/%u complete=%d\n",
            adjacent_warm_ran,
            (unsigned long long)app.presentation_hash,
            (unsigned long long)current_presentation_hash,
            app.presentation_frame.row_count,
            current_presentation_row_count,
            eightvo_frame_presentation_is_complete(&app));
    goto cleanup;
  }
  for (U32 index = 0;
       index < app.presentation_frame.row_count;
       index += 1)
  {
    const PresentationEngineBlockFlowRow *presentation_row =
      app.presentation_frame.rows + index;
    if (presentation_row->style_index >= app.frame.style_row_count)
      continue;
    TextEngineDisplaySpanRow display_row = {0};
    if (!eightvo_reader_display_span_row(&app,
                                          app.frame.style_rows +
                                            presentation_row->style_index,
                                          presentation_row,
                                          &display_row) ||
        display_row.span_count == 0 || display_row.w <= 0)
      continue;
    if (selection_row_count < 2)
      selection_rows[selection_row_count++] = index;
    else
    {
      selection_rows[0] = selection_rows[1];
      selection_rows[1] = index;
    }
  }
  if (selection_row_count != 2 || selection_rows[0] == selection_rows[1])
  {
    fprintf(stderr,
            "eightvo_reader_view_selection_menu result=fail checkpoint=0 reason=rows count=%u\n",
            selection_row_count);
    goto cleanup;
  }

  TextEngineDisplaySpanRow start_row = {0};
  const PresentationEngineBlockFlowRow *start_presentation_row =
    app.presentation_frame.rows + selection_rows[0];
  if (!eightvo_reader_display_span_row(
        &app,
        app.frame.style_rows + start_presentation_row->style_index,
        start_presentation_row,
        &start_row) || start_row.span_count == 0 ||
      start_row.spans[0].stop_count < 2)
    goto cleanup;
  TextEngineDisplayRowStop start_stop = start_row.spans[0].stops[1];
  expected_start = start_stop.source_byte;
  S32 start_x = start_row.x + start_row.spans[0].x + start_stop.x;
  S32 start_y = app.presentation_frame.rows[selection_rows[0]].row_rect.y +
                app.presentation_frame.rows[selection_rows[0]].row_rect.h / 2;

  TextEngineDisplaySpanRow end_row = {0};
  const PresentationEngineBlockFlowRow *end_presentation_row =
    app.presentation_frame.rows + selection_rows[1];
  if (!eightvo_reader_display_span_row(
        &app,
        app.frame.style_rows + end_presentation_row->style_index,
        end_presentation_row,
        &end_row) || end_row.span_count == 0)
    goto cleanup;
  const TextEngineDisplaySpan *first_end_span = end_row.spans;
  if (first_end_span->stop_count < 2) goto cleanup;
  TextEngineDisplayRowStop end_stop = first_end_span->stops[1];
  expected_end = end_stop.source_byte;
  end_x = end_row.x + first_end_span->x + end_stop.x;
  end_y = app.presentation_frame.rows[selection_rows[1]].row_rect.y +
          app.presentation_frame.rows[selection_rows[1]].row_rect.h / 2;
  if (expected_end <= expected_start)
    goto cleanup;
  checkpoint = 1;

  eightvo_host_pointer_press(&app, start_x, start_y);
  eightvo_render_to_buffer(&app, &buffer);
  if (!app.selection_dragging ||
      app.reader_view_state.popup != ReaderViewPopup_None)
    goto cleanup;
  checkpoint = 11;
  eightvo_host_pointer_move(&app, end_x, end_y);
  eightvo_update_pointer_selection(&app, end_x, end_y, 0);
  eightvo_render_to_buffer(&app, &buffer);
  if (!app.selection_dragging ||
      app.reader_view_state.popup != ReaderViewPopup_None)
    goto cleanup;
  checkpoint = 12;
  if (eightvo_host_pointer_release(&app, end_x, end_y) ||
      !app.input.pointer_selection_release)
    goto cleanup;
  checkpoint = 13;
  eightvo_render_to_buffer(&app, &buffer);
  if (!app.reader.has_active_selection ||
      app.reader.active_selection.text_byte_start != expected_start ||
      app.reader.active_selection.text_byte_end != expected_end ||
      app.reader_view_state.popup != ReaderViewPopup_SelectionTools)
    goto cleanup;
  checkpoint = 2;

  U32 exact_selection_rows = 0;
  for (U32 index = 0; index < ARRAY_COUNT(selection_rows); index += 1)
  {
    U32 row_index = selection_rows[index];
    const PresentationEngineBlockFlowRow *presentation_row =
      app.presentation_frame.rows + row_index;
    if (presentation_row->style_index >= app.frame.style_row_count)
      goto cleanup;
    const EpubReaderFrameStyleRow *style_row =
      app.frame.style_rows + presentation_row->style_index;
    TextEngineDisplaySpanRow display_row = {0};
    TextEngineRowRect expected_rect = {0};
    if (!eightvo_reader_display_span_row(&app, style_row, presentation_row,
                                          &display_row) ||
        !text_engine_display_span_row_range_rect_from_source_range(
          &display_row, expected_start, expected_end, &expected_rect))
      goto cleanup;
    for (U16 command_index = 0;
         command_index < app.draw_commands.command_count[DrawLayer_World];
         command_index += 1)
    {
      const DrawCommand *command =
        app.draw_commands.commands[DrawLayer_World] + command_index;
      if (command->type == DrawCommandType_RoundedRect &&
          command->v.rounded_rect.fill_color ==
            app.reader_content_theme.selection &&
          command->v.rounded_rect.x == expected_rect.x &&
          command->v.rounded_rect.y == presentation_row->row_rect.y &&
          command->v.rounded_rect.w == MAX(expected_rect.w, 3) &&
          command->v.rounded_rect.h == presentation_row->row_rect.h)
      {
        exact_selection_rows += 1;
        break;
      }
    }
  }
  if (exact_selection_rows != 2)
    goto cleanup;
  checkpoint = 3;

  const ReaderViewSemanticNode *popup =
    eightvo_reader_view_parity_semantic(
      &app, ReaderViewSemantic_Group, "More");
  const ReaderViewSemanticNode *yellow =
    eightvo_reader_view_parity_semantic(
      &app, ReaderViewSemantic_MenuItem, "Yellow");
  const ReaderViewSemanticNode *pink =
    eightvo_reader_view_parity_semantic(
      &app, ReaderViewSemantic_MenuItem, "Pink");
  const ReaderViewSemanticNode *copy =
    eightvo_reader_view_parity_semantic(
      &app, ReaderViewSemantic_MenuItem, "Copy");
  const ReaderViewSemanticNode *delete_row =
    eightvo_reader_view_parity_semantic(
      &app, ReaderViewSemantic_MenuItem, "Delete");
  UI0Rect viewport = app.reader_view_layout.viewport_rect;
  if (!popup || !yellow || !pink || !copy || delete_row ||
      popup->rect.w != 224 ||
      yellow->rect.x != popup->rect.x + 18 ||
      yellow->rect.y != popup->rect.y + 14 ||
      yellow->rect.w != 20 || yellow->rect.h != 20 ||
      pink->rect.x != popup->rect.x + 66 ||
      popup->rect.x < viewport.x + 8 || popup->rect.y < viewport.y + 8 ||
      popup->rect.x + popup->rect.w > viewport.x + viewport.w - 8 ||
      popup->rect.y + popup->rect.h > viewport.y + viewport.h - 8 ||
      popup->rect.y >= app.selection_anchor_rect.y)
    goto cleanup;
  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_multiline_light.bmp", output_prefix);
  if (!eightvo_write_bmp(bmp_path, pixels, Width, Height))
    goto cleanup;
  checkpoint = 4;

  if (!eightvo_reader_view_parity_click_node(&app, &buffer, pink) ||
      app.highlight_count != 1 || app.highlights[0].color_index != 1 ||
      !app.reader.has_active_selection)
    goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);

  TextEngineDisplaySpanRow interaction_row = {0};
  if (!eightvo_reader_display_span_row(
        &app,
        app.frame.style_rows + start_presentation_row->style_index,
        start_presentation_row,
        &interaction_row) || interaction_row.span_count == 0)
    goto cleanup;
  checkpoint = 41;
  S32 outside_x = 0;
  S32 partial_start_x = 0;
  S32 partial_end_x = 0;
  U64 partial_start = 0;
  U64 partial_end = 0;
  B32 found_partial_start = 0;
  B32 found_partial_end = 0;
  for (U32 span_index = 0;
       span_index < interaction_row.span_count;
       span_index += 1)
  {
    const TextEngineDisplaySpan *span = interaction_row.spans + span_index;
    for (U32 stop_index = 0; stop_index < span->stop_count; stop_index += 1)
    {
      TextEngineDisplayRowStop stop = span->stops[stop_index];
      S32 stop_x = interaction_row.x + span->x + stop.x;
      if (!found_partial_start &&
          stop.source_byte >= expected_start &&
          stop.source_byte < expected_end)
      {
        partial_start = stop.source_byte;
        partial_start_x = stop_x;
        found_partial_start = 1;
      }
      else if (found_partial_start && !found_partial_end &&
               stop.source_byte >= partial_start + 8 &&
               stop.source_byte < expected_end)
      {
        partial_end = stop.source_byte;
        partial_end_x = stop_x;
        found_partial_end = 1;
      }
    }
  }
  if (!found_partial_start || !found_partial_end ||
      selection_rows[0] == 0)
    goto cleanup;
  checkpoint = 42;
  S32 interaction_y =
    app.presentation_frame.rows[selection_rows[0]].row_rect.y +
    app.presentation_frame.rows[selection_rows[0]].row_rect.h / 2;
  checkpoint = 43;
  S32 outside_y = 0;
  B32 found_outside_glyph = 0;
  for (U32 row_index = 0;
       !found_outside_glyph &&
       row_index < app.presentation_frame.row_count;
       row_index += 1)
  {
    const PresentationEngineBlockFlowRow *presentation_row =
      app.presentation_frame.rows + row_index;
    if (presentation_row->style_index >= app.frame.style_row_count)
      continue;
    TextEngineDisplaySpanRow outside_row = {0};
    if (!eightvo_reader_display_span_row(
          &app,
          app.frame.style_rows + presentation_row->style_index,
          presentation_row,
          &outside_row))
    {
      continue;
    }
    S32 candidate_y =
      app.presentation_frame.rows[row_index].row_rect.y +
      app.presentation_frame.rows[row_index].row_rect.h / 2;
    for (U32 span_index = 0;
         !found_outside_glyph && span_index < outside_row.span_count;
         span_index += 1)
    {
      const TextEngineDisplaySpan *span = outside_row.spans + span_index;
      for (U32 stop_index = 0;
           !found_outside_glyph && stop_index < span->stop_count;
           stop_index += 1)
      {
        S32 candidate_x =
          outside_row.x + span->x + span->stops[stop_index].x + 2;
        if (!eightvo_reader_selection_popup_contains_point(
              &app, candidate_x, candidate_y) &&
            !eightvo_reader_selection_contains_point(
              &app, candidate_x, candidate_y))
        {
          outside_x = candidate_x;
          outside_y = candidate_y;
          found_outside_glyph = 1;
        }
      }
    }
  }
  /* A single outside click must dismiss the popup and clear its concrete
     selection without materializing the clicked glyph as a new selection. */
  if (!found_outside_glyph ||
      app.reader_view_state.popup != ReaderViewPopup_SelectionTools ||
      eightvo_reader_selection_popup_contains_point(
        &app, outside_x, outside_y) ||
      eightvo_reader_selection_contains_point(&app, outside_x, outside_y))
    goto cleanup;

  eightvo_host_pointer_press(&app, outside_x, outside_y);
  if (app.selection_dragging || app.reader.has_active_selection ||
      app.selected_text[0] || !app.input.escape_pressed)
    goto cleanup;
  checkpoint = 44;
  if (eightvo_host_pointer_release(&app, outside_x, outside_y) ||
      app.input.pointer_selection_release)
    goto cleanup;
  checkpoint = 45;
  eightvo_render_to_buffer(&app, &buffer);
  if (app.reader.has_active_selection || app.selected_text[0] ||
      app.reader_view_state.popup != ReaderViewPopup_None)
    goto cleanup;
  checkpoint = 46;

  eightvo_host_pointer_press(&app, partial_start_x, interaction_y);
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_host_pointer_move(&app, partial_end_x, interaction_y);
  eightvo_update_pointer_selection(&app, partial_end_x, interaction_y, 0);
  eightvo_render_to_buffer(&app, &buffer);
  if (eightvo_host_pointer_release(&app, partial_end_x, interaction_y) ||
      !app.input.pointer_selection_release)
    goto cleanup;
  checkpoint = 47;
  eightvo_render_to_buffer(&app, &buffer);
  pink = eightvo_reader_view_parity_semantic(
    &app, ReaderViewSemantic_MenuItem, "Pink");
  if (!app.reader.has_active_selection ||
      app.reader.active_selection.text_byte_start != partial_start ||
      app.reader.active_selection.text_byte_end != partial_end ||
      app.reader_view_state.popup != ReaderViewPopup_SelectionTools ||
      !pink || (pink->flags & ReaderViewSemantic_Selected) == 0 ||
      eightvo_selection_highlight_index(&app) != 0)
    goto cleanup;

  app.theme = EightvoTheme_Dark;
  eightvo_render_to_buffer(&app, &buffer);
  pink = eightvo_reader_view_parity_semantic(
    &app, ReaderViewSemantic_MenuItem, "Pink");
  if (!pink || (pink->flags & ReaderViewSemantic_Selected) == 0 ||
      !reader_view_accessibility_focus(&app.reader_view_state, pink->id))
    goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_selected_dark_focus.bmp", output_prefix);
  if (!eightvo_write_bmp(bmp_path, pixels, Width, Height))
    goto cleanup;
  app.input.activate_pressed = 1;
  eightvo_render_to_buffer(&app, &buffer);
  if (!eightvo_reader_view_has_action(
        &app.reader_view_frame, ReaderViewAction_RemoveHighlight))
    goto cleanup;
  eightvo_apply_reader_view_actions(&app);
  if (app.highlight_count != 0 || !app.reader.has_active_selection)
    goto cleanup;
  checkpoint = 5;

  eightvo_render_to_buffer(&app, &buffer);
  if (app.reader_view_state.popup != ReaderViewPopup_SelectionTools)
    goto cleanup;
  eightvo_reader_view_escape(&app);
  eightvo_render_to_buffer(&app, &buffer);
  if (app.reader.has_active_selection || app.selected_text[0] ||
      app.reader_view_state.popup != ReaderViewPopup_None)
    goto cleanup;
  checkpoint = 6;
  result = 0;

cleanup:
  if (result == 0)
  {
    fprintf(stdout,
            "eightvo_reader_view_selection_menu result=pass checkpoint=%u rows=%u,%u range=%llu..%llu geometry=glyph_stops first_drag=adjacent_presentation_restored release=popup_safe click=single_dismiss_clear_without_glyph substring=remove_containing_highlight menu=compact_clamped mouse=set_pink keyboard=remove_pink escape=concrete_selection output=%s\n",
            checkpoint,
            selection_rows[0], selection_rows[1],
            (unsigned long long)expected_start,
            (unsigned long long)expected_end,
            output_prefix);
  }
  else
  {
    U64 mapped_end = 0;
    UI0Rect mapped_rect = {0};
    B32 mapped_end_valid =
      eightvo_reader_point_to_byte(&app, end_x, end_y,
                                    &mapped_end, &mapped_rect);
    fprintf(stderr,
            "eightvo_reader_view_selection_menu result=fail checkpoint=%u popup=%d selection=%d range=%llu..%llu rows=%u,%u highlights=%u dragging=%d anchor=%llu mapped_end=%d:%llu frame_rows=%u presentation_rows=%u content=%d,%d,%d,%d end_point=%d,%d selected=\"%s\" projection_key=%llu projection_flags=%llu\n",
            checkpoint,
            (int)app.reader_view_state.popup,
            app.reader.has_active_selection,
            (unsigned long long)app.reader.active_selection.text_byte_start,
            (unsigned long long)app.reader.active_selection.text_byte_end,
            selection_rows[0], selection_rows[1],
            app.highlight_count,
            app.selection_dragging,
            (unsigned long long)app.selection_anchor_byte,
            mapped_end_valid,
            (unsigned long long)mapped_end,
            app.frame.style_row_count,
            app.presentation_frame.row_count,
            app.reader_content_geometry.content_rect.x,
            app.reader_content_geometry.content_rect.y,
            app.reader_content_geometry.content_rect.w,
            app.reader_content_geometry.content_rect.h,
            end_x,
            end_y,
            app.selected_text,
            (unsigned long long)
              app.reader_view_projection.selection.selection_key,
            (unsigned long long)
              app.reader_view_projection.selection.flags);
  }
  free(pixels);
  eightvo_app_release(&app);
  return result;
}

FUNCTION B32
eightvo_draw_adapter_covers_all_ops(EightvoApp *app)
{
  if (!app) return 0;
  UI0DrawCommand commands[UI0DrawOp_Count] = {0};
  for (UI0S32 index = 0; index < UI0DrawOp_Count; index += 1)
  {
    commands[index] = (UI0DrawCommand){
      .op = (UI0DrawOpKind)index,
      .source_id = index <= UI0DrawOp_ControlBorder ? 1 : (UI0ID)(index + 1),
      .source_kind = UI0ControlKind_TextButton,
      .rect = ui0_rect(8 + index * 2, 8 + index * 2, 18, 18),
      .clip_rect = ui0_rect(0, 0, 128, 128),
      .color = UI0_COLOR_RGB(0x44, 0x66, 0x88),
      .stroke_color = UI0_COLOR_RGB(0x22, 0x33, 0x44),
      .icon_kind = UI0IconKind_Search,
      .stroke_width = 2,
      .corner_radius = 4,
      .has_text_alignment = 1,
      .text_align_x = UI0TextAlignX_Center,
      .text_align_y = UI0TextAlignY_Center,
      .has_typography_role = 1,
      .typography_role = UI0TypographyRole_Body,
      .typography_line_height = 15,
    };
  }
  ReaderViewFrame saved = app->reader_view_frame;
  app->reader_view_frame.draw_commands = commands;
  app->reader_view_frame.draw_command_count = UI0DrawOp_Count;
  draw_command_buffer_begin(&app->draw_commands);
  eightvo_adapt_ui0_draw(app);
  B32 result = app->draw_adapter_stats.unsupported_count == 0 &&
               app->draw_commands.overflow_count == 0;
  for (UI0S32 index = 0; index < UI0DrawOp_Count; index += 1)
    result = result && app->draw_adapter_stats.op_count[index] == 1;
  app->reader_view_frame = saved;
  return result;
}

FUNCTION B32
eightvo_draw_clip_matches(DrawClipRect actual, UI0Rect expected)
{
  return actual.x == expected.x && actual.y == expected.y &&
         actual.w == expected.w && actual.h == expected.h;
}

FUNCTION B32
eightvo_draw_adapter_rect_border_matches(const DrawCommand *commands,
                                          UI0Rect rect,
                                          UI0Rect clip,
                                          U32 color)
{
  if (!commands) return 0;
  const S32 endpoints[4][4] = {
    {rect.x, rect.y, rect.x + rect.w - 1, rect.y},
    {rect.x, rect.y + rect.h - 1,
     rect.x + rect.w - 1, rect.y + rect.h - 1},
    {rect.x, rect.y, rect.x, rect.y + rect.h - 1},
    {rect.x + rect.w - 1, rect.y,
     rect.x + rect.w - 1, rect.y + rect.h - 1},
  };
  for (U32 index = 0; index < ARRAY_COUNT(endpoints); index += 1)
  {
    const DrawCommand *command = commands + index;
    if (command->type != DrawCommandType_Line ||
        command->v.line.x0 != endpoints[index][0] ||
        command->v.line.y0 != endpoints[index][1] ||
        command->v.line.x1 != endpoints[index][2] ||
        command->v.line.y1 != endpoints[index][3] ||
        command->v.line.stroke_width != 1 ||
        command->v.line.color != color ||
        !eightvo_draw_clip_matches(command->v.line.clip, clip))
      return 0;
  }
  return 1;
}

FUNCTION B32
eightvo_draw_adapter_covers_reference_edges(EightvoApp *app)
{
  if (!app) return 0;
  const UI0Color focus_color = UI0_COLOR_RGB(0x11, 0x22, 0x33);
  const UI0Color border_color = UI0_COLOR_RGB(0x44, 0x55, 0x66);
  const UI0Color fill_color = UI0_COLOR_RGB(0x77, 0x88, 0x99);
  const UI0Color stroke_color = UI0_COLOR_RGB(0x12, 0x34, 0x56);
  const UI0Rect clip = {0, 0, 256, 256};
  UI0DrawCommand commands[] = {
    {
      .op = UI0DrawOp_FocusRing,
      .source_kind = UI0ControlKind_TextButton,
      .rect = {10, 10, 20, 20},
      .clip_rect = clip,
      .color = focus_color,
      .corner_radius = 4,
      .flags = UI0DrawFlag_CornerMask,
    },
    {
      .op = UI0DrawOp_FocusRing,
      .source_kind = UI0ControlKind_TextButton,
      .rect = {40, 10, 20, 20},
      .clip_rect = clip,
      .color = focus_color,
      .corner_radius = 4,
      .flags = UI0DrawFlag_CornerMask | UI0DrawFlag_RoundTop,
    },
    {
      .op = UI0DrawOp_FocusRing,
      .source_kind = UI0ControlKind_TextButton,
      .rect = {70, 10, 20, 20},
      .clip_rect = clip,
      .color = focus_color,
      .corner_radius = 4,
      .flags = UI0DrawFlag_CornerMask | UI0DrawFlag_RoundBottom,
    },
    {
      .op = UI0DrawOp_FocusRing,
      .source_kind = UI0ControlKind_TextButton,
      .rect = {100, 10, 20, 20},
      .clip_rect = clip,
      .color = focus_color,
      .corner_radius = 4,
      .flags = UI0DrawFlag_CornerMask | UI0DrawFlag_RoundTop |
               UI0DrawFlag_RoundBottom,
    },
    {
      .op = UI0DrawOp_FocusRing,
      .source_kind = UI0ControlKind_TextButton,
      .rect = {130, 10, 20, 20},
      .clip_rect = clip,
      .color = focus_color,
      .corner_radius = 4,
    },
    {
      .op = UI0DrawOp_IndicatorBorder,
      .source_kind = UI0ControlKind_TextButton,
      .rect = {10, 40, 20, 20},
      .clip_rect = clip,
      .color = border_color,
      .corner_radius = 5,
    },
    {
      .op = UI0DrawOp_IndicatorBorder,
      .source_kind = UI0ControlKind_Checkbox,
      .rect = {40, 40, 20, 20},
      .clip_rect = clip,
      .color = border_color,
      .corner_radius = 5,
    },
    {
      .op = UI0DrawOp_IndicatorFill,
      .source_kind = UI0ControlKind_Checkbox,
      .rect = {70, 40, 20, 20},
      .clip_rect = clip,
      .color = fill_color,
      .stroke_color = stroke_color,
      .corner_radius = 5,
    },
    {
      .op = UI0DrawOp_ToggleTrack,
      .source_kind = UI0ControlKind_Toggle,
      .rect = {100, 40, 20, 20},
      .clip_rect = clip,
      .color = fill_color,
      .stroke_color = stroke_color,
      .corner_radius = 5,
    },
    {
      .op = UI0DrawOp_ToggleKnob,
      .source_kind = UI0ControlKind_Toggle,
      .rect = {130, 40, 20, 20},
      .clip_rect = clip,
      .color = fill_color,
      .stroke_color = stroke_color,
      .corner_radius = 5,
    },
    {
      .op = UI0DrawOp_ScrollTrack,
      .source_kind = UI0ControlKind_ScrollRegion,
      .rect = {160, 40, 20, 20},
      .clip_rect = clip,
      .color = fill_color,
      .stroke_color = stroke_color,
      .corner_radius = 5,
    },
    {
      .op = UI0DrawOp_ScrollThumb,
      .source_kind = UI0ControlKind_ScrollRegion,
      .rect = {190, 40, 20, 20},
      .clip_rect = clip,
      .color = fill_color,
      .stroke_color = stroke_color,
      .corner_radius = 5,
    },
    {
      .op = UI0DrawOp_SliderTrack,
      .source_kind = UI0ControlKind_Slider,
      .rect = {10, 70, 20, 20},
      .clip_rect = clip,
      .color = fill_color,
      .stroke_color = stroke_color,
      .corner_radius = 5,
    },
    {
      .op = UI0DrawOp_SliderFill,
      .source_kind = UI0ControlKind_Slider,
      .rect = {40, 70, 20, 20},
      .clip_rect = clip,
      .color = fill_color,
      .stroke_color = stroke_color,
      .corner_radius = 5,
    },
    {
      .op = UI0DrawOp_SliderThumb,
      .source_kind = UI0ControlKind_Slider,
      .rect = {70, 70, 20, 20},
      .clip_rect = clip,
      .color = fill_color,
      .stroke_color = stroke_color,
      .corner_radius = 5,
    },
    {
      .op = UI0DrawOp_ControlFill,
      .source_id = 98,
      .source_kind = UI0ControlKind_TextButton,
      .source_index = 6,
      .rect = {100, 70, 20, 20},
      .clip_rect = clip,
      .color = fill_color,
      .stroke_color = stroke_color,
      .corner_radius = 5,
    },
    {
      .op = UI0DrawOp_ControlFill,
      .source_id = 99,
      .source_kind = UI0ControlKind_TextButton,
      .source_index = 7,
      .rect = {130, 70, 20, 20},
      .clip_rect = clip,
      .color = fill_color,
      .corner_radius = 5,
      .flags = UI0DrawFlag_CornerMask,
    },
    {
      .op = UI0DrawOp_ControlBorder,
      .source_id = 99,
      .source_kind = UI0ControlKind_TextButton,
      .source_index = 7,
      .rect = {130, 70, 20, 20},
      .clip_rect = clip,
      .color = border_color,
      .corner_radius = 5,
      .flags = UI0DrawFlag_CornerMask,
    },
  };

  ReaderViewFrame saved = app->reader_view_frame;
  app->reader_view_frame.draw_commands = commands;
  app->reader_view_frame.draw_command_count = ARRAY_COUNT(commands);
  draw_command_buffer_begin(&app->draw_commands);
  eightvo_adapt_ui0_draw(app);

  const DrawCommand *draw = app->draw_commands.commands[DrawLayer_UI];
  B32 result = app->draw_adapter_stats.unsupported_count == 0 &&
               app->draw_commands.overflow_count == 0 &&
               app->draw_commands.command_count[DrawLayer_UI] == 27 &&
               eightvo_draw_adapter_rect_border_matches(
                 draw, commands[0].rect, commands[0].rect,
                 eightvo_draw_color(focus_color));
  if (result)
  {
    const DrawRoundedRectStrokeCommand *round_top =
      &draw[4].v.rounded_rect_stroke;
    const DrawRoundedRectStrokeCommand *round_bottom =
      &draw[5].v.rounded_rect_stroke;
    const DrawRoundedRectStrokeCommand *round_both =
      &draw[6].v.rounded_rect_stroke;
    const DrawRoundedRectStrokeCommand *round_all =
      &draw[7].v.rounded_rect_stroke;
    const DrawRoundedRectStrokeCommand *checkbox =
      &draw[12].v.rounded_rect_stroke;
    result = draw[4].type == DrawCommandType_RoundedRectStroke &&
      round_top->x == 40 && round_top->y == 10 &&
      round_top->w == 20 && round_top->h == 24 &&
      round_top->radius == 4 && round_top->stroke_width == 1 &&
      round_top->color == eightvo_draw_color(focus_color) &&
      eightvo_draw_clip_matches(round_top->clip, commands[1].rect) &&
      draw[5].type == DrawCommandType_RoundedRectStroke &&
      round_bottom->x == 70 && round_bottom->y == 6 &&
      round_bottom->w == 20 && round_bottom->h == 24 &&
      round_bottom->radius == 4 && round_bottom->stroke_width == 1 &&
      round_bottom->color == eightvo_draw_color(focus_color) &&
      eightvo_draw_clip_matches(round_bottom->clip, commands[2].rect) &&
      draw[6].type == DrawCommandType_RoundedRectStroke &&
      round_both->x == 100 && round_both->y == 10 &&
      round_both->w == 20 && round_both->h == 20 &&
      round_both->radius == 4 && round_both->stroke_width == 1 &&
      round_both->color == eightvo_draw_color(focus_color) &&
      eightvo_draw_clip_matches(round_both->clip, commands[3].rect) &&
      draw[7].type == DrawCommandType_RoundedRectStroke &&
      round_all->x == 130 && round_all->y == 10 &&
      round_all->w == 20 && round_all->h == 20 &&
      round_all->radius == 4 && round_all->stroke_width == 1 &&
      round_all->color == eightvo_draw_color(focus_color) &&
      eightvo_draw_clip_matches(round_all->clip, clip) &&
      eightvo_draw_adapter_rect_border_matches(
        draw + 8, commands[5].rect, clip,
        eightvo_draw_color(border_color)) &&
      draw[12].type == DrawCommandType_RoundedRectStroke &&
      checkbox->radius == 5 && checkbox->stroke_width == 1 &&
      checkbox->color == eightvo_draw_color(border_color) &&
      eightvo_draw_clip_matches(checkbox->clip, clip);
  }
  result = result && draw[13].type == DrawCommandType_RoundedRect &&
    draw[13].v.rounded_rect.radius == 5 &&
    draw[13].v.rounded_rect.fill_color == eightvo_draw_color(fill_color) &&
    draw[13].v.rounded_rect.border_color == eightvo_draw_color(fill_color) &&
    eightvo_draw_clip_matches(draw[13].v.rounded_rect.clip, clip);
  for (U32 index = 14; result && index <= 20; index += 1)
  {
    const DrawCommand *visual = draw + index;
    result = visual->type == DrawCommandType_RoundedRect &&
      visual->v.rounded_rect.radius == 5 &&
      visual->v.rounded_rect.fill_color ==
        eightvo_draw_color(fill_color) &&
      visual->v.rounded_rect.border_color ==
        eightvo_draw_color(stroke_color) &&
      eightvo_draw_clip_matches(visual->v.rounded_rect.clip, clip);
  }
  result = result && draw[21].type == DrawCommandType_RoundedRect &&
    draw[21].v.rounded_rect.x == 100 &&
    draw[21].v.rounded_rect.y == 70 &&
    draw[21].v.rounded_rect.w == 20 &&
    draw[21].v.rounded_rect.h == 20 &&
    draw[21].v.rounded_rect.radius == 5 &&
    draw[21].v.rounded_rect.fill_color == eightvo_draw_color(fill_color) &&
    draw[21].v.rounded_rect.border_color == eightvo_draw_color(stroke_color) &&
    eightvo_draw_clip_matches(draw[21].v.rounded_rect.clip, clip) &&
    draw[22].type == DrawCommandType_Rect &&
    draw[22].v.rect.x == 130 && draw[22].v.rect.y == 70 &&
    draw[22].v.rect.w == 20 && draw[22].v.rect.h == 20 &&
    draw[22].v.rect.color == eightvo_draw_color(fill_color) &&
    eightvo_draw_clip_matches(draw[22].v.rect.clip, commands[16].rect) &&
    eightvo_draw_adapter_rect_border_matches(
      draw + 23, commands[16].rect, commands[16].rect,
      eightvo_draw_color(border_color));

  app->reader_view_frame = saved;
  return result;
}

FUNCTION B32
eightvo_draw_adapter_covers_reference_text_styles(EightvoApp *app)
{
  if (!app) return 0;
  static const char *labels[] = {
    "Reference title",
    "Reference metadata",
    "Reference menu item",
    "Reference default",
    "Reference note editor",
  };
  static const ReaderViewTextStyle styles[] = {
    ReaderViewTextStyle_ChromeTitle,
    ReaderViewTextStyle_ChromeMetadata,
    ReaderViewTextStyle_MenuItem,
    ReaderViewTextStyle_Default,
    ReaderViewTextStyle_NoteEditor,
  };
  static const S32 expected_scales[] = {2, 1, 1, 1, 18};
  ReaderViewTextBinding bindings[ARRAY_COUNT(labels)] = {0};
  UI0DrawCommand commands[ARRAY_COUNT(labels)] = {0};
  for (U32 index = 0; index < ARRAY_COUNT(labels); index += 1)
  {
    bindings[index] = (ReaderViewTextBinding){
      .source_id = 9001 + index,
      .text = {
        .data = labels[index],
        .size = (S32)strlen(labels[index]),
      },
      .style = styles[index],
    };
    commands[index] = (UI0DrawCommand){
      .op = UI0DrawOp_Text,
      .source_id = bindings[index].source_id,
      .source_kind = UI0ControlKind_Label,
      .source_index = (UI0S32)index,
      .rect = ui0_rect(8, 8 + (S32)index * 28, 220, 24),
      .clip_rect = ui0_rect(0, 0, 256, 160),
      .color = UI0_COLOR_RGB(0x24, 0x35, 0x46),
      .has_text_alignment = 1,
      .text_align_x = UI0TextAlignX_Start,
      .text_align_y = UI0TextAlignY_Center,
      .has_typography_role = 1,
      .typography_role = UI0TypographyRole_Body,
      .typography_line_height =
        styles[index] == ReaderViewTextStyle_NoteEditor ? 18 : 17,
    };
  }

  ReaderViewFrame saved = app->reader_view_frame;
  app->reader_view_frame.text_bindings = bindings;
  app->reader_view_frame.text_binding_count = ARRAY_COUNT(bindings);
  app->reader_view_frame.draw_commands = commands;
  app->reader_view_frame.draw_command_count = ARRAY_COUNT(commands);
  draw_command_buffer_begin(&app->draw_commands);
  eightvo_adapt_ui0_draw(app);

  B32 result = app->draw_adapter_stats.unsupported_count == 0;
  for (U32 label_index = 0;
       label_index < ARRAY_COUNT(labels);
       label_index += 1)
  {
    U32 matches = 0;
    U16 count = app->draw_commands.command_count[DrawLayer_UI];
    for (U16 command_index = 0; command_index < count; command_index += 1)
    {
      const DrawCommand *command =
        app->draw_commands.commands[DrawLayer_UI] + command_index;
      if (command->type == DrawCommandType_Text &&
          strcmp(command->v.text.text, labels[label_index]) == 0)
      {
        matches += 1;
        result = result &&
                 command->v.text.scale == expected_scales[label_index] &&
                 command->v.text.color == 0x00243546U;
      }
    }
    result = result && matches == 1;
  }
  app->reader_view_frame = saved;
  return result;
}

FUNCTION B32
eightvo_draw_adapter_covers_note_editable_row(EightvoApp *app)
{
  if (!app) return 0;
  static const char note[] = "Attached parity note";
  static const char placeholder[] = "Type a note";
  enum { RowSource = 9301, EditorSource = 9302 };
  ReaderViewFrame saved_frame = app->reader_view_frame;
  ReaderViewState saved_state = app->reader_view_state;
  eightvo_copy_cstr(app->reader_view_state.note_draft,
                     ARRAY_COUNT(app->reader_view_state.note_draft), note);
  app->reader_view_state.note_draft_length = (UI0S32)strlen(note);
  app->reader_view_state.note_input.caret =
    app->reader_view_state.note_draft_length;
  app->reader_view_state.popup = ReaderViewPopup_NoteEditor;

  ReaderViewTextBinding bindings[] = {
    {
      .source_id = RowSource,
      .text = {
        .data = app->reader_view_state.note_draft,
        .size = app->reader_view_state.note_draft_length,
      },
      .style = ReaderViewTextStyle_NoteEditor,
    },
    {
      .source_id = EditorSource,
      .text = {.data = placeholder, .size = (UI0S32)sizeof(placeholder) - 1},
      .style = ReaderViewTextStyle_NoteEditor,
    },
  };
  UI0Rect clip = ui0_rect(400, 200, 260, 25);
  UI0DrawCommand commands[] = {
    {
      .op = UI0DrawOp_Text,
      .source_id = RowSource,
      .source_kind = UI0ControlKind_TextArea,
      .rect = clip,
      .clip_rect = clip,
      .color = UI0_COLOR_RGB(0x24, 0x35, 0x46),
      .has_typography_role = 1,
      .typography_role = UI0TypographyRole_Body,
      .typography_line_height = EightvoReaderViewNotePixelHeight,
    },
    {
      .op = UI0DrawOp_TextCaret,
      .source_id = EditorSource,
      .source_kind = UI0ControlKind_TextArea,
      .rect = {499, 201, 1, 23},
      .clip_rect = clip,
      .color = UI0_COLOR_RGB(0x11, 0x52, 0x93),
    },
  };
  app->reader_view_frame.text_bindings = bindings;
  app->reader_view_frame.text_binding_count = ARRAY_COUNT(bindings);
  app->reader_view_frame.draw_commands = commands;
  app->reader_view_frame.draw_command_count = ARRAY_COUNT(commands);
  draw_command_buffer_begin(&app->draw_commands);
  eightvo_adapt_ui0_draw(app);

  Scratch scratch = scratch_begin(0, 0);
  TextEngineResolvedStyle style = text_engine_resolved_style_make(
    font_provider_system_ui(),
    (FontTag){0},
    EightvoReaderViewNotePixelHeight,
    0x00243546U,
    0,
    FontRasterFlag_Smooth | FontRasterFlag_Hinted);
  TextEngineEditableRow expected_row = {0};
  S32 expected_caret_x = 0;
  B32 expected = text_engine_editable_row_make(
    &expected_row,
    scratch.arena,
    str8((U8 *)note, sizeof(note) - 1),
    &style,
    text_engine_source_range_from_bytes(0, sizeof(note) - 1),
    0,
    clip.x,
    clip.y,
    clip.h,
    EightvoReaderViewNoteTerminalCaretGap) &&
    text_engine_editable_row_caret_x_for_source_byte(
      &expected_row, sizeof(note) - 1, &expected_caret_x);

  U32 text_matches = 0;
  U32 caret_matches = 0;
  U16 draw_count = app->draw_commands.command_count[DrawLayer_UI];
  for (U16 index = 0; index < draw_count; index += 1)
  {
    const DrawCommand *draw =
      app->draw_commands.commands[DrawLayer_UI] + index;
    if (draw->type == DrawCommandType_Text &&
        strcmp(draw->v.text.text, note) == 0)
    {
      text_matches += 1;
      expected = expected && draw->v.text.x == clip.x &&
        draw->v.text.scale == EightvoReaderViewNotePixelHeight &&
        draw->v.text.color == 0x00243546U &&
        (draw->v.text.flags & DrawTextFlag_Shaped) != 0;
    }
    else if (draw->type == DrawCommandType_Rect &&
             draw->v.rect.color == 0x00115293U)
    {
      caret_matches += 1;
      expected = expected && draw->v.rect.x == expected_caret_x &&
        draw->v.rect.x != commands[1].rect.x &&
        draw->v.rect.y == commands[1].rect.y &&
        draw->v.rect.w == 1 && draw->v.rect.h == 23;
    }
  }
  B32 result = expected && text_matches == 1 && caret_matches == 1 &&
    app->draw_adapter_stats.unsupported_count == 0 &&
    app->draw_adapter_stats.note_editable_row_count == 1 &&
    app->draw_adapter_stats.note_caret_remap_count == 1;
  scratch_end(scratch);
  app->reader_view_state = saved_state;
  app->reader_view_frame = saved_frame;
  return result;
}

FUNCTION B32
eightvo_draw_adapter_covers_measured_find_match(EightvoApp *app)
{
  if (!app) return 0;
  static const char text[] = "prefix alpha suffix";
  enum { MatchStart = 7, MatchSize = 5 };
  ReaderViewTextBinding binding = {
    .source_id = 9101,
    .text = {.data = text, .size = (S32)sizeof(text) - 1},
    .style = ReaderViewTextStyle_Default,
    .match_start = MatchStart,
    .match_size = MatchSize,
  };
  UI0DrawCommand command = {
    .op = UI0DrawOp_Text,
    .source_id = binding.source_id,
    .source_kind = UI0ControlKind_Label,
    .rect = {8, 8, 180, 18},
    .clip_rect = {0, 0, 220, 40},
    .color = UI0_COLOR_RGB(0x24, 0x35, 0x46),
    .has_typography_role = 1,
    .typography_role = UI0TypographyRole_Body,
    .typography_line_height = 17,
  };
  ReaderViewFrame saved = app->reader_view_frame;
  app->reader_view_frame.text_bindings = &binding;
  app->reader_view_frame.text_binding_count = 1;
  app->reader_view_frame.draw_commands = &command;
  app->reader_view_frame.draw_command_count = 1;
  draw_command_buffer_begin(&app->draw_commands);
  eightvo_adapt_ui0_draw(app);

  FontTextMetrics metrics =
    font_metrics_for_size(font_provider_system_ui(), 1);
  S32 prefix_width = font_measure_text_width_s8(
    font_provider_system_ui(), str8((U8 *)text, MatchStart), 1);
  S32 match_width = font_measure_text_width_s8(
    font_provider_system_ui(),
    str8((U8 *)text + MatchStart, MatchSize), 1);
  EightvoReaderContentTheme theme =
    eightvo_reader_content_theme(app->theme);
  B32 result = app->draw_adapter_stats.unsupported_count == 0 &&
               app->draw_commands.overflow_count == 0 &&
               app->draw_commands.command_count[DrawLayer_UI] == 2;
  if (result)
  {
    const DrawCommand *highlight =
      app->draw_commands.commands[DrawLayer_UI];
    const DrawCommand *excerpt = highlight + 1;
    result = highlight->type == DrawCommandType_Rect &&
      highlight->v.rect.x == command.rect.x + prefix_width &&
      highlight->v.rect.y == command.rect.y - 1 &&
      highlight->v.rect.w == MAX(match_width, 1) &&
      highlight->v.rect.h ==
        MAX(metrics.ascent_px + metrics.descent_px + 2, 1) &&
      highlight->v.rect.color == theme.user_highlight &&
      excerpt->type == DrawCommandType_Text &&
      strcmp(excerpt->v.text.text, text) == 0 &&
      excerpt->v.text.x == command.rect.x &&
      excerpt->v.text.y == command.rect.y + metrics.ascent_px &&
      excerpt->v.text.scale == 1 &&
      excerpt->v.text.color == theme.ink &&
      excerpt->v.text.origin == DrawTextOrigin_Baseline &&
      excerpt->v.text.clip_x == command.rect.x &&
      excerpt->v.text.clip_y == command.rect.y &&
      excerpt->v.text.clip_w == command.rect.w &&
      excerpt->v.text.clip_h == command.rect.h;
  }
  app->reader_view_frame = saved;
  return result;
}

FUNCTION B32
eightvo_draw_adapter_covers_find_status_and_metadata(EightvoApp *app)
{
  if (!app) return 0;
  static const char metadata[] = "First Light";
  static const char status[] = "18 matches";
  enum
  {
    FindRowSource = 9201,
    MetadataSource = 9202,
    StatusSource = 9203,
  };
  ReaderViewSemanticNode nodes[] = {
    {
      .id = FindRowSource,
      .control = ReaderViewSemanticControl_FindRow,
    },
    {
      .id = MetadataSource,
      .parent_id = FindRowSource,
    },
    {
      .id = StatusSource,
      .role = ReaderViewSemantic_Status,
    },
  };
  ReaderViewTextBinding bindings[] = {
    {
      .source_id = MetadataSource,
      .text = {.data = metadata, .size = (S32)sizeof(metadata) - 1},
      .style = ReaderViewTextStyle_ChromeMetadata,
    },
    {
      .source_id = StatusSource,
      .text = {.data = status, .size = (S32)sizeof(status) - 1},
      .style = ReaderViewTextStyle_Default,
    },
  };
  UI0DrawCommand commands[] = {
    {
      .op = UI0DrawOp_Text,
      .source_id = MetadataSource,
      .source_kind = UI0ControlKind_Label,
      .rect = {12, 10, 180, 18},
      .clip_rect = {0, 0, 240, 80},
      .color = UI0_COLOR_RGB(0x24, 0x35, 0x46),
    },
    {
      .op = UI0DrawOp_Text,
      .source_id = StatusSource,
      .source_kind = UI0ControlKind_Label,
      .rect = {12, 36, 180, 20},
      .clip_rect = {0, 0, 240, 80},
      .color = UI0_COLOR_RGB(0x24, 0x35, 0x46),
      .has_text_alignment = 1,
      .text_align_x = UI0TextAlignX_Start,
      .text_align_y = UI0TextAlignY_Center,
    },
  };
  ReaderViewFrame saved_frame = app->reader_view_frame;
  ReaderViewState saved_state = app->reader_view_state;
  ReaderViewLayout saved_layout = app->reader_view_layout;
  app->reader_view_frame.semantic_nodes = nodes;
  app->reader_view_frame.semantic_node_count = ARRAY_COUNT(nodes);
  app->reader_view_frame.text_bindings = bindings;
  app->reader_view_frame.text_binding_count = ARRAY_COUNT(bindings);
  app->reader_view_frame.draw_commands = commands;
  app->reader_view_frame.draw_command_count = ARRAY_COUNT(commands);
  app->reader_view_state.left_panel = ReaderViewLeftPanel_Find;
  app->reader_view_layout.left_panel_rect = (UI0Rect){0, 0, 240, 80};
  draw_command_buffer_begin(&app->draw_commands);
  eightvo_adapt_ui0_draw(app);

  const FontProvider *provider = font_provider_system_ui();
  FontTextMetrics metrics = font_metrics_for_size(provider, 1);
  S32 metadata_width = font_measure_text_width_s8(
    provider, str8((U8 *)metadata, sizeof(metadata) - 1), 1);
  S32 status_baseline = font_text_baseline_y_in_rect(
    provider, commands[1].rect.y, commands[1].rect.h, 1);
  U32 metadata_matches = 0;
  U32 status_matches = 0;
  B32 result = app->draw_adapter_stats.unsupported_count == 0 &&
               app->draw_commands.overflow_count == 0;
  U16 draw_count = app->draw_commands.command_count[DrawLayer_UI];
  for (U16 index = 0; index < draw_count; index += 1)
  {
    const DrawCommand *draw =
      app->draw_commands.commands[DrawLayer_UI] + index;
    if (draw->type != DrawCommandType_Text) continue;
    if (strcmp(draw->v.text.text, metadata) == 0)
    {
      metadata_matches += 1;
      result = result &&
        draw->v.text.x == commands[0].rect.x +
          commands[0].rect.w - metadata_width &&
        draw->v.text.y == commands[0].rect.y + metrics.ascent_px;
    }
    else if (strcmp(draw->v.text.text, status) == 0)
    {
      status_matches += 1;
      result = result && draw->v.text.x == commands[1].rect.x &&
        draw->v.text.y == status_baseline;
    }
  }
  result = result && metadata_matches == 1 && status_matches == 2 &&
           draw_count == 3;
  app->reader_view_layout = saved_layout;
  app->reader_view_state = saved_state;
  app->reader_view_frame = saved_frame;
  return result;
}

FUNCTION B32
eightvo_reader_view_covers_find_text_metrics(EightvoApp *app)
{
  if (!app) return 0;
  ReaderViewState saved_state = app->reader_view_state;
  ReaderViewProjection saved_projection = app->reader_view_projection;
  EightvoInput saved_input = app->input;
  ReaderViewCodepointAdvance
    saved_advances[READER_VIEW_FIND_CODEPOINT_ADVANCE_CAP];
  U64 saved_last_seen[READER_VIEW_FIND_CODEPOINT_ADVANCE_CAP];
  U8 saved_priority[READER_VIEW_FIND_CODEPOINT_ADVANCE_CAP];
  MemoryCopy(saved_advances, app->find_text_advances,
             sizeof(saved_advances));
  MemoryCopy(saved_last_seen, app->find_text_advance_last_seen,
             sizeof(saved_last_seen));
  MemoryCopy(saved_priority, app->find_text_advance_priority,
             sizeof(saved_priority));
  U32 saved_count = app->find_text_advance_count;
  U64 saved_generation = app->find_text_metrics_generation;
  S32 saved_fallback = app->find_text_fallback_advance;
  B32 saved_initialized = app->find_text_metrics_initialized;

  MemoryZeroArray(app->find_text_advances);
  MemoryZeroArray(app->find_text_advance_last_seen);
  MemoryZeroArray(app->find_text_advance_priority);
  app->find_text_advance_count = 0;
  app->find_text_metrics_generation = 0;
  app->find_text_fallback_advance = 0;
  app->find_text_metrics_initialized = 0;

  static const char omega[] = "\xce\xa9";
  static const char beta[] = "\xce\xb2";
  static const char e_acute[] = "\xc3\xa9";
  static const char snowman[] = "\xe2\x98\x83";
  static const char zhe[] = "\xd0\x96";
  static const char boundary[] = "\xe7\x95\x8c";
  static const char grin[] = "\xf0\x9f\x98\x80";
  static const char smile[] = "\xf0\x9f\x99\x82";
  char transfer_text[sizeof(beta)] = {0};
  char commit_text[sizeof(snowman)] = {0};
  S32 transfer_length = (S32)sizeof(beta) - 1;
  S32 commit_length = (S32)sizeof(snowman) - 1;
  UI0TextInputTransferBuffer transfer = {
    .data = transfer_text,
    .length = &transfer_length,
    .cap = (S32)sizeof(transfer_text),
  };
  UI0TextInputTransferBuffer commit = {
    .data = commit_text,
    .length = &commit_length,
    .cap = (S32)sizeof(commit_text),
  };
  ReaderViewInput input = {0};
  MemoryCopy(transfer_text, beta, sizeof(beta) - 1);
  MemoryCopy(commit_text, snowman, sizeof(snowman) - 1);
  MemoryCopy(app->reader_view_state.find_query, omega, sizeof(omega) - 1);
  app->reader_view_state.find_query_length = (S32)sizeof(omega) - 1;
  MemoryCopy(app->reader_view_state.find_history_text,
             zhe, sizeof(zhe) - 1);
  app->reader_view_state.find_history.text_size = sizeof(zhe) - 1;
  MemoryCopy(app->reader_view_state.find_history_scratch,
             boundary, sizeof(boundary) - 1);
  app->reader_view_state.find_history.scratch_size = sizeof(boundary) - 1;
  app->reader_view_projection.labels = reader_view_default_english_labels();
  app->reader_view_projection.find.committed_query =
    (ReaderViewText){.data = beta, .size = (S32)sizeof(beta) - 1};
  input.find_text.text = e_acute;
  input.find_text.text_len = (S32)sizeof(e_acute) - 1;
  input.find_text.transfer_buffer = &transfer;
  input.find_text.commit_buffer = &commit;

  ReaderViewFindTextMetrics metrics =
    eightvo_reader_view_find_text_metrics(app, &input);
  static const U32 expected_scalars[] = {
    0x03a9u, 0x03b2u, 0x00e9u, 0x2603u, 0x0416u, 0x754cu,
  };
  static const char *expected_text[] = {
    omega, beta, e_acute, snowman, zhe, boundary,
  };
  static const U32 expected_sizes[] = {
    sizeof(omega) - 1, sizeof(beta) - 1, sizeof(e_acute) - 1,
    sizeof(snowman) - 1, sizeof(zhe) - 1, sizeof(boundary) - 1,
  };
  S32 expected_fallback = font_measure_text_width_s8(
    font_provider_system_ui(), str8_from_cstr("?"), 1);
  if (expected_fallback <= 0)
  {
    expected_fallback = MAX(
      font_metrics_for_size(font_provider_system_ui(), 1).glyph_advance_px,
      1);
  }
  B32 result = metrics.advances == app->find_text_advances &&
    metrics.advance_count == 95 + (S32)ARRAY_COUNT(expected_scalars) &&
    metrics.fallback_advance == expected_fallback;
  for (U32 scalar_index = 0;
       result && scalar_index < ARRAY_COUNT(expected_scalars);
       scalar_index += 1)
  {
    const ReaderViewCodepointAdvance *found = 0;
    for (S32 metric_index = 0;
         metric_index < metrics.advance_count;
         metric_index += 1)
    {
      if (metrics.advances[metric_index].codepoint ==
          expected_scalars[scalar_index])
      {
        found = metrics.advances + metric_index;
        break;
      }
    }
    result = found && found->advance == font_measure_text_width_s8(
      font_provider_system_ui(),
      str8((U8 *)expected_text[scalar_index], expected_sizes[scalar_index]),
      1);
  }

  U32 retained_count = app->find_text_advance_count;
  input = (ReaderViewInput){0};
  app->reader_view_state.find_query_length = 0;
  app->reader_view_projection.find.committed_query = (ReaderViewText){0};
  app->reader_view_state.find_history.text_size = 0;
  app->reader_view_state.find_history.scratch_size = 0;
  metrics = eightvo_reader_view_find_text_metrics(app, &input);
  result = result && (U32)metrics.advance_count == retained_count;

  MemoryCopy(app->reader_view_state.find_history_text,
             omega, sizeof(omega) - 1);
  app->reader_view_state.find_history.text_size = sizeof(omega) - 1;
  metrics = eightvo_reader_view_find_text_metrics(app, &input);
  U32 omega_index = UINT32_MAX;
  for (U32 index = 0; index < app->find_text_advance_count; index += 1)
  {
    if (app->find_text_advances[index].codepoint == 0x03a9u)
    {
      omega_index = index;
      break;
    }
  }
  result = result && omega_index != UINT32_MAX &&
    app->find_text_advance_priority[omega_index] ==
      EightvoReaderViewFindPriority_History;

  app->reader_view_state.find_history.text_size = 0;
  input.find_text.text = grin;
  input.find_text.text_len = (S32)sizeof(grin) - 1;
  metrics = eightvo_reader_view_find_text_metrics(app, &input);
  result = result && (U32)metrics.advance_count == retained_count + 1 &&
    metrics.advances[metrics.advance_count - 1].codepoint == 0x1f600u &&
    metrics.advances[metrics.advance_count - 1].advance ==
      font_measure_text_width_s8(
        font_provider_system_ui(), str8((U8 *)grin, sizeof(grin) - 1), 1);

  while (app->find_text_advance_count <
         READER_VIEW_FIND_CODEPOINT_ADVANCE_CAP)
  {
    U32 index = app->find_text_advance_count;
    app->find_text_advances[index] = (ReaderViewCodepointAdvance){
      .codepoint = 0x10000u + index,
      .advance = 1,
    };
    app->find_text_advance_last_seen[index] = 1;
    app->find_text_advance_priority[index] =
      EightvoReaderViewFindPriority_History;
    app->find_text_advance_count += 1;
  }
  U64 next_generation = app->find_text_metrics_generation + 1;
  for (U32 index = 95;
       index < app->find_text_advance_count;
       index += 1)
  {
    app->find_text_advance_last_seen[index] = next_generation;
    app->find_text_advance_priority[index] =
      EightvoReaderViewFindPriority_History;
  }
  input.find_text.text = smile;
  input.find_text.text_len = (S32)sizeof(smile) - 1;
  metrics = eightvo_reader_view_find_text_metrics(app, &input);
  const ReaderViewCodepointAdvance *smile_metric = 0;
  for (S32 index = 0; index < metrics.advance_count; index += 1)
  {
    if (metrics.advances[index].codepoint == 0x1f642u)
    {
      smile_metric = metrics.advances + index;
      break;
    }
  }
  result = result &&
    metrics.advance_count == READER_VIEW_FIND_CODEPOINT_ADVANCE_CAP &&
    smile_metric && smile_metric->advance == font_measure_text_width_s8(
      font_provider_system_ui(), str8((U8 *)smile, sizeof(smile) - 1), 1) &&
    app->find_text_advances[0x41u - 0x20u].codepoint == 0x41u &&
    app->find_text_advance_priority[0x41u - 0x20u] ==
      EightvoReaderViewFindPriority_Pinned;

  app->find_text_metrics_generation = UINT64_MAX;
  for (U32 index = 95;
       index < app->find_text_advance_count;
       index += 1)
  {
    app->find_text_advance_last_seen[index] = UINT64_MAX;
  }
  input = (ReaderViewInput){0};
  metrics = eightvo_reader_view_find_text_metrics(app, &input);
  B32 wrap_reset = app->find_text_metrics_generation == 1;
  for (U32 index = 95;
       wrap_reset && index < app->find_text_advance_count;
       index += 1)
  {
    if (app->find_text_advance_last_seen[index] != 0) wrap_reset = 0;
  }
  result = result && wrap_reset;

  app->reader_view_state = saved_state;
  app->reader_view_projection = saved_projection;
  app->input = saved_input;
  MemoryCopy(app->find_text_advances, saved_advances,
             sizeof(saved_advances));
  MemoryCopy(app->find_text_advance_last_seen, saved_last_seen,
             sizeof(saved_last_seen));
  MemoryCopy(app->find_text_advance_priority, saved_priority,
             sizeof(saved_priority));
  app->find_text_advance_count = saved_count;
  app->find_text_metrics_generation = saved_generation;
  app->find_text_fallback_advance = saved_fallback;
  app->find_text_metrics_initialized = saved_initialized;
  return result;
}

FUNCTION B32
eightvo_reader_view_covers_note_text_metrics(EightvoApp *app)
{
  if (!app) return 0;
  ReaderViewState saved_state = app->reader_view_state;
  ReaderViewProjection saved_projection = app->reader_view_projection;
  ReaderViewCodepointAdvance
    saved_advances[READER_VIEW_NOTE_CODEPOINT_ADVANCE_CAP];
  MemoryCopy(saved_advances, app->note_text_advances,
             sizeof(saved_advances));
  U32 saved_count = app->note_text_advance_count;

  static const char draft[] = "Draft \xce\xa9";
  static const char incoming[] = "\xce\xb2";
  static const char transfer_text[] = "\xe2\x98\x83";
  char transfer_storage[sizeof(transfer_text)] = {0};
  S32 transfer_length = (S32)sizeof(transfer_text) - 1;
  UI0TextInputTransferBuffer transfer = {
    .data = transfer_storage,
    .length = &transfer_length,
    .cap = (S32)sizeof(transfer_storage),
  };
  MemoryCopy(transfer_storage, transfer_text, sizeof(transfer_text) - 1);
  app->reader_view_state.popup = ReaderViewPopup_NoteEditor;
  MemoryZeroArray(app->reader_view_state.note_draft);
  MemoryCopy(app->reader_view_state.note_draft, draft, sizeof(draft) - 1);
  app->reader_view_state.note_draft_length = (S32)sizeof(draft) - 1;
  app->reader_view_projection.labels = reader_view_default_english_labels();
  ReaderViewInput input = {0};
  input.note_text.text = incoming;
  input.note_text.text_len = (S32)sizeof(incoming) - 1;
  input.note_text.transfer_buffer = &transfer;

  ReaderViewNoteTextMetrics metrics =
    eightvo_reader_view_note_text_metrics(app, &input);
  S32 expected_fallback = eightvo_reader_view_measure_note_text("?", 1);
  if (expected_fallback <= 0)
    expected_fallback = EightvoReaderViewNoteAdvanceFallback;
  B32 result = metrics.advances == app->note_text_advances &&
    metrics.advance_count > 0 &&
    metrics.advance_count <= READER_VIEW_NOTE_CODEPOINT_ADVANCE_CAP &&
    metrics.fallback_advance == expected_fallback &&
    metrics.pixel_height == EightvoReaderViewNotePixelHeight &&
    metrics.line_height == EightvoReaderViewNoteLineHeightFallback;
  for (S32 index = 0; result && index < metrics.advance_count; index += 1)
  {
    const ReaderViewCodepointAdvance *item = metrics.advances + index;
    result = item->codepoint != 0 && item->codepoint <= 0x10ffffu &&
      !(item->codepoint >= 0xd800u && item->codepoint <= 0xdfffu) &&
      item->advance >= 0;
    for (S32 earlier = 0; result && earlier < index; earlier += 1)
      result = metrics.advances[earlier].codepoint != item->codepoint;
  }

  static const U32 expected_scalars[] = {
    (U32)'D', 0x03a9u, 0x03b2u, 0x2603u, (U32)'T',
  };
  static const char *expected_text[] = {
    "D", draft + sizeof(draft) - 3, incoming, transfer_text, "T",
  };
  static const U32 expected_sizes[] = {
    1, 2, sizeof(incoming) - 1, sizeof(transfer_text) - 1, 1,
  };
  S32 previous_index = -1;
  for (U32 scalar_index = 0;
       result && scalar_index < ARRAY_COUNT(expected_scalars);
       scalar_index += 1)
  {
    S32 found_index = -1;
    for (S32 index = 0; index < metrics.advance_count; index += 1)
    {
      if (metrics.advances[index].codepoint == expected_scalars[scalar_index])
      {
        found_index = index;
        break;
      }
    }
    result = found_index > previous_index &&
      metrics.advances[found_index].advance ==
        eightvo_reader_view_measure_note_text(
          expected_text[scalar_index], expected_sizes[scalar_index]);
    previous_index = found_index;
  }

  app->reader_view_state.popup = ReaderViewPopup_None;
  ReaderViewNoteTextMetrics closed =
    eightvo_reader_view_note_text_metrics(app, &input);
  result = result && !closed.advances && closed.advance_count == 0 &&
    closed.fallback_advance == 0 && closed.pixel_height == 0 &&
    closed.line_height == 0;

  if (!result)
  {
    fprintf(stderr,
            "eightvo note metrics regression count=%d fallback=%d pixel=%d line=%d provider_line=%d placeholder=%.*s\n",
            metrics.advance_count, metrics.fallback_advance,
            metrics.pixel_height, metrics.line_height,
            font_metrics_for_size(font_provider_system_ui(),
                                  EightvoReaderViewNotePixelHeight)
              .line_advance_px,
            app->reader_view_projection.labels.note_placeholder.size,
            app->reader_view_projection.labels.note_placeholder.data);
  }

  app->reader_view_state = saved_state;
  app->reader_view_projection = saved_projection;
  MemoryCopy(app->note_text_advances, saved_advances,
             sizeof(saved_advances));
  app->note_text_advance_count = saved_count;
  return result;
}

FUNCTION B32
eightvo_reader_view_covers_noncontiguous_toc_identity(EightvoApp *app)
{
  if (!app || !app->frame.section_items || app->frame.section_item_count == 0)
    return 0;
  EpubReaderFrameSectionItem rows[2] = {0};
  rows[0].nav_index = 7;
  rows[0].depth = 3;
  rows[0].active = 1;
  eightvo_copy_cstr(rows[0].label, ARRAY_COUNT(rows[0].label), "Nested A");
  rows[0].label_length = 8;
  rows[1].nav_index = 42;
  rows[1].depth = 1;
  eightvo_copy_cstr(rows[1].label, ARRAY_COUNT(rows[1].label), "Nested B");
  rows[1].label_length = 8;

  EpubReaderFrameSectionItem *saved_rows = app->frame.section_items;
  U32 saved_count = app->frame.section_item_count;
  U32 saved_total = app->frame.section_item_total_count;
  app->frame.section_items = rows;
  app->frame.section_item_count = ARRAY_COUNT(rows);
  app->frame.section_item_total_count = 73;
  eightvo_prepare_reader_view_toc(app);
  B32 result = app->reader_view_projection.toc.row_count == 2 &&
    app->reader_view_projection.toc.total_count == 73 &&
    app->reader_view_projection.toc.rows[0].key == 8 &&
    app->reader_view_projection.toc.rows[1].key == 43 &&
    app->reader_view_projection.toc.rows[0].depth == 3 &&
    app->reader_view_projection.toc.rows[1].depth == 1 &&
    (app->reader_view_projection.toc.rows[0].flags &
     (ReaderViewRow_Current | ReaderViewRow_Selected)) ==
      (ReaderViewRow_Current | ReaderViewRow_Selected);

  app->frame.section_items = saved_rows;
  app->frame.section_item_count = saved_count;
  app->frame.section_item_total_count = saved_total;
  eightvo_prepare_reader_view_toc(app);
  return result;
}

FUNCTION B32
eightvo_reader_view_covers_mixed_right_order(EightvoApp *app)
{
  if (!app || app->persistence_enabled || app->bookmark_count != 0 ||
      app->highlight_count != 0)
    return 0;
  ReaderViewRightFilter saved_filter = app->reader_view_state.right_filter;
  U64 saved_revision = app->annotation_revision;
  app->bookmark_count = 2;
  app->bookmarks[0] = (EightvoBookmark){
    .id = 11, .spine_index = 1, .byte_offset = 10,
  };
  app->bookmarks[1] = (EightvoBookmark){
    .id = 12, .spine_index = 0, .byte_offset = 90,
  };
  eightvo_copy_cstr(app->bookmarks[0].label,
                     ARRAY_COUNT(app->bookmarks[0].label), "Second");
  eightvo_copy_cstr(app->bookmarks[0].excerpt,
                     ARRAY_COUNT(app->bookmarks[0].excerpt), "Later bookmark");
  eightvo_copy_cstr(app->bookmarks[1].label,
                     ARRAY_COUNT(app->bookmarks[1].label), "First");
  eightvo_copy_cstr(app->bookmarks[1].excerpt,
                     ARRAY_COUNT(app->bookmarks[1].excerpt), "Earlier bookmark");
  app->highlight_count = 2;
  app->highlights[0] = (EightvoHighlight){
    .id = 21, .spine_index = 0, .start_byte = 90, .end_byte = 100,
    .color_index = 2, .is_highlight = 1,
  };
  app->highlights[1] = (EightvoHighlight){
    .id = 22, .spine_index = 0, .start_byte = 20, .end_byte = 30,
    .color_index = 1, .is_highlight = 1,
  };
  eightvo_copy_cstr(app->highlights[0].section,
                     ARRAY_COUNT(app->highlights[0].section), "First");
  eightvo_copy_cstr(app->highlights[0].text,
                     ARRAY_COUNT(app->highlights[0].text), "Attached excerpt");
  eightvo_copy_cstr(app->highlights[0].note,
                     ARRAY_COUNT(app->highlights[0].note), "Attached note");
  eightvo_copy_cstr(app->highlights[1].section,
                     ARRAY_COUNT(app->highlights[1].section), "First");
  eightvo_copy_cstr(app->highlights[1].text,
                     ARRAY_COUNT(app->highlights[1].text), "Leading excerpt");
  app->reader_view_state.right_filter = ReaderViewRightFilter_All;
  eightvo_prepare_reader_view_projection(app);

  static const ReaderViewRightRowKind expected_kinds[] = {
    ReaderViewRightRow_Highlight,
    ReaderViewRightRow_Bookmark,
    ReaderViewRightRow_Highlight,
    ReaderViewRightRow_Note,
    ReaderViewRightRow_Bookmark,
  };
  static const U64 expected_ids[] = {22, 12, 21, 21, 11};
  B32 result = app->reader_view_projection.right.row_count ==
                 (UI0S32)ARRAY_COUNT(expected_kinds) &&
               app->reader_view_right_source_count ==
                 ARRAY_COUNT(expected_kinds);
  for (U32 index = 0; result && index < ARRAY_COUNT(expected_kinds); index += 1)
  {
    const ReaderViewRightRow *row =
      app->reader_view_projection.right.rows + index;
    const EightvoReaderViewRightSource *source =
      app->reader_view_right_sources + index;
    result = row->kind == expected_kinds[index] &&
             source->key == row->key &&
             source->row_kind == expected_kinds[index] &&
             source->record_id == expected_ids[index];
  }
  if (result)
  {
    const ReaderViewRightRow *note =
      app->reader_view_projection.right.rows + 3;
    result = note->color_key == 5002 &&
             (note->flags & ReaderViewRow_AttachedToPrevious) != 0;
  }
  if (result)
  {
    const ReaderViewRightRow *highlight =
      app->reader_view_projection.right.rows + 2;
    const ReaderViewRightRow *note = highlight + 1;
    eightvo_apply_reader_view_action(app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleRightRowStar,
      .key = highlight->key,
      .right_row_kind = ReaderViewRightRow_Highlight,
    });
    B32 highlight_routed = app->highlights[0].starred &&
                           !app->highlights[0].note_starred &&
                           !app->highlights[1].starred;
    eightvo_apply_reader_view_action(app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleRightRowStar,
      .key = note->key,
      .right_row_kind = ReaderViewRightRow_Note,
    });
    result = highlight_routed && app->highlights[0].starred &&
             app->highlights[0].note_starred &&
             !app->highlights[1].starred;
    ReaderViewKey bookmark_key =
      app->reader_view_projection.right.rows[1].key;
    eightvo_apply_reader_view_action(app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleRightRowStar,
      .key = bookmark_key,
      .right_row_kind = ReaderViewRightRow_Bookmark,
    });
    result = result && app->bookmark_count == 1 &&
             app->bookmarks[0].id == 11 &&
             app->annotation_revision == saved_revision + 3;
  }

  app->bookmark_count = 0;
  app->highlight_count = 0;
  MemoryZeroArray(app->bookmarks);
  MemoryZeroArray(app->highlights);
  app->annotation_revision = saved_revision;
  app->reader_view_state.right_filter = saved_filter;
  eightvo_prepare_reader_view_projection(app);
  return result;
}

FUNCTION B32
eightvo_reader_view_covers_right_projection_contract(EightvoApp *app)
{
  if (!app || app->persistence_enabled || app->bookmark_count != 0 ||
      app->highlight_count != 0)
    return 0;
  static const ReaderViewRightFilter filters[] = {
    ReaderViewRightFilter_All,
    ReaderViewRightFilter_Bookmarks,
    ReaderViewRightFilter_Highlights,
    ReaderViewRightFilter_Notes,
  };
  static const UI0U64 filter_totals[] = {9, 1, 4, 4};
  static const U32 filter_bookmark_rows[] = {1, 1, 0, 0};
  static const U32 filter_highlight_rows[] = {4, 0, 4, 0};
  static const U32 filter_note_rows[] = {4, 0, 0, 4};
  static const UI0Color light_colors[] = {
    UI0_COLOR_RGB(0xff, 0xf2, 0xa6),
    UI0_COLOR_RGB(0xff, 0xd4, 0xec),
    UI0_COLOR_RGB(0xcd, 0xe7, 0xff),
    UI0_COLOR_RGB(0xff, 0xdc, 0xa8),
  };
  static const UI0Color dark_colors[] = {
    UI0_COLOR_RGB(0x4d, 0x4a, 0x16),
    UI0_COLOR_RGB(0x47, 0x35, 0x5c),
    UI0_COLOR_RGB(0x2a, 0x46, 0x62),
    UI0_COLOR_RGB(0x52, 0x3f, 0x1c),
  };

  ReaderViewRightFilter saved_filter = app->reader_view_state.right_filter;
  EightvoTheme saved_theme = app->theme;
  U64 saved_revision = app->annotation_revision;
  app->bookmark_count = 1;
  app->bookmarks[0] = (EightvoBookmark){
    .id = 51, .spine_index = 0, .byte_offset = 50,
  };
  eightvo_copy_cstr(app->bookmarks[0].label,
                     ARRAY_COUNT(app->bookmarks[0].label), "Section");
  eightvo_copy_cstr(app->bookmarks[0].excerpt,
                     ARRAY_COUNT(app->bookmarks[0].excerpt), "Bookmark");
  app->highlight_count = 4;
  for (U32 color_index = 0; color_index < app->highlight_count; color_index += 1)
  {
    EightvoHighlight *highlight = app->highlights + color_index;
    *highlight = (EightvoHighlight){
      .id = 101 + color_index,
      .spine_index = 0,
      .start_byte = 100 + 100 * color_index,
      .end_byte = 150 + 100 * color_index,
      .color_index = color_index,
      .is_highlight = 1,
    };
    eightvo_copy_cstr(highlight->section,
                       ARRAY_COUNT(highlight->section), "Section");
    eightvo_copy_cstr(highlight->text,
                       ARRAY_COUNT(highlight->text), "Highlight");
    eightvo_copy_cstr(highlight->note,
                       ARRAY_COUNT(highlight->note), "Attached note");
  }

  B32 result = 1;
  for (U32 appearance = 0; appearance < 2; appearance += 1)
  {
    const UI0Color *expected_colors = appearance ? dark_colors : light_colors;
    app->theme = appearance ? EightvoTheme_Dark : EightvoTheme_Light;
    for (U32 filter_index = 0;
         filter_index < ARRAY_COUNT(filters);
         filter_index += 1)
    {
      app->reader_view_state.right_filter = filters[filter_index];
      eightvo_prepare_reader_view_projection(app);
      const ReaderViewRightProjection *right =
        &app->reader_view_projection.right;
      result = result &&
        app->reader_view_state.right_filter == filters[filter_index] &&
        right->status.state == ReaderViewLoad_Ready &&
        right->row_count == (UI0S32)filter_totals[filter_index] &&
        right->total_count == filter_totals[filter_index] &&
        right->all_count == 9 &&
        right->bookmark_count == 1 &&
        right->highlight_count == 4 &&
        right->note_count == 4 &&
        !right->has_more &&
        right->available_filters ==
          (ReaderViewRightFilterFlag_All |
           ReaderViewRightFilterFlag_Bookmarks |
           ReaderViewRightFilterFlag_Highlights |
           ReaderViewRightFilterFlag_Notes) &&
        eightvo_reader_view_text_is(app->reader_view_projection.labels.all,
                                     "All") &&
        eightvo_reader_view_text_is(
          app->reader_view_projection.labels.bookmarks, "Bookmarks") &&
        eightvo_reader_view_text_is(
          app->reader_view_projection.labels.highlights,
          "All Highlight Colors") &&
        eightvo_reader_view_text_is(app->reader_view_projection.labels.notes,
                                     "Notes");

      U32 bookmark_rows = 0;
      U32 highlight_rows = 0;
      U32 note_rows = 0;
      for (UI0S32 row_index = 0;
           row_index < right->row_count;
           row_index += 1)
      {
        const ReaderViewRightRow *row = right->rows + row_index;
        const EightvoReaderViewRightSource *source =
          eightvo_reader_view_right_source(app, row->key, row->kind);
        result = result && source != 0;
        if (!source) continue;
        if (row->kind == ReaderViewRightRow_Bookmark)
        {
          bookmark_rows += 1;
          result = result && source->record_id == 51 &&
                   row->color_key == 0 && row->rail_color == 0 &&
                   (row->flags & ReaderViewRow_Starred) != 0 &&
                   (row->flags & ReaderViewRow_AttachedToPrevious) == 0;
          continue;
        }

        U32 color_index = source->record_id >= 101 ?
                          (U32)(source->record_id - 101) :
                          ARRAY_COUNT(light_colors);
        result = result && color_index < ARRAY_COUNT(light_colors);
        if (color_index >= ARRAY_COUNT(light_colors)) continue;
        result = result && row->color_key == 5000ull + color_index &&
                 row->rail_color == expected_colors[color_index];
        if (row->kind == ReaderViewRightRow_Highlight)
        {
          highlight_rows += 1;
          result = result &&
            (row->flags & ReaderViewRow_AttachedToPrevious) == 0;
        }
        else if (row->kind == ReaderViewRightRow_Note)
        {
          note_rows += 1;
          /* Notes-only filtering changes adjacency, never the colored rail. */
          B32 attached =
            (row->flags & ReaderViewRow_AttachedToPrevious) != 0;
          B32 expected_attached =
            filters[filter_index] == ReaderViewRightFilter_All;
          result = result && attached == expected_attached;
          if (expected_attached)
          {
            result = result && row_index > 0;
            if (row_index > 0)
            {
              const ReaderViewRightRow *previous = row - 1;
              const EightvoReaderViewRightSource *previous_source =
                eightvo_reader_view_right_source(
                  app, previous->key, previous->kind);
              result = result &&
                previous->kind == ReaderViewRightRow_Highlight &&
                previous_source &&
                previous_source->record_id == source->record_id;
            }
          }
        }
        else result = 0;
      }
      result = result &&
        bookmark_rows == filter_bookmark_rows[filter_index] &&
        highlight_rows == filter_highlight_rows[filter_index] &&
        note_rows == filter_note_rows[filter_index];
    }
  }

  app->bookmark_count = 0;
  app->highlight_count = 0;
  MemoryZeroArray(app->bookmarks);
  MemoryZeroArray(app->highlights);
  app->annotation_revision = saved_revision;
  app->reader_view_state.right_filter = saved_filter;
  app->theme = saved_theme;
  eightvo_prepare_reader_view_projection(app);
  return result;
}

FUNCTION int
eightvo_run_reader_view_find_active_contrast_smoke(const char *path,
                                                     const char *output_prefix)
{
  enum { Width = 1400, Height = 780 };
  static const EightvoTheme themes[] = {
    EightvoTheme_Dark,
    EightvoTheme_Light,
    EightvoTheme_CoralDark,
    EightvoTheme_CoralLight,
    EightvoTheme_BlueDark,
    EightvoTheme_BlueLight,
  };
  static const char *theme_names[] = {
    "dark", "light", "coral-dark", "coral-light", "blue-dark", "blue-light",
  };
  static const U32 expected_active[] = {
    0x008F430FU, 0x00F6B36FU, 0x009A3034U,
    0x00EE9B94U, 0x004F64BFU, 0x008BAEFFU,
  };
  static const U32 expected_inactive[] = {
    0x004A4947U, 0x00D8D7D4U, 0x0062605EU,
    0x00D4D0CCU, 0x003D454EU, 0x00D6DADFU,
  };
  static const U32 expected_selection[] = {
    0x004D3424U, 0x00FFE7D4U, 0x0063423EU,
    0x00F3C2B9U, 0x00345F91U, 0x00E6EEFFU,
  };
  EightvoApp app = {0};
  U64 pixel_count = (U64)Width * Height;
  U32 *pixels = (U32 *)calloc((size_t)pixel_count, sizeof(U32));
  RenderBuffer buffer = {0};
  U32 checkpoint = 0;
  int result = 1;
  char bmp_path[EightvoPathCap] = {0};

  if (!pixels || !path || !path[0] || !output_prefix || !output_prefix[0] ||
      !eightvo_app_init(&app, Width, Height, 1, 0) ||
      !eightvo_open_path(&app, path))
    goto cleanup;
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);

  const ReaderViewSemanticNode *find =
    eightvo_reader_view_semantic_control(
      &app.reader_view_frame, ReaderViewSemanticControl_Find);
  if (!find || !eightvo_reader_view_parity_space_node(&app, &buffer, find) ||
      app.reader_view_state.left_panel != ReaderViewLeftPanel_Find ||
      !eightvo_reader_view_parity_click(
        &app, &buffer, ReaderViewSemantic_SearchBox, 0))
    goto cleanup;
  checkpoint = 1;

  MemoryCopy(app.input.text, "Paran", 5);
  app.input.text[5] = 0;
  app.input.text_length = 5;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  app.input.commit_pressed = 1;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  eightvo_render_to_buffer(&app, &buffer);
  if (app.reader.search_match_count <= 2 ||
      app.reader_view_projection.find.row_count <= 2)
    goto cleanup;
  checkpoint = 2;

  ReaderViewKey active_key = app.reader_view_projection.find.rows[2].key;
  const ReaderViewSemanticNode *active_row =
    eightvo_reader_view_semantic_control_source(
      &app.reader_view_frame, ReaderViewSemanticControl_FindRow, active_key);
  if (!active_row ||
      !eightvo_reader_view_parity_space_node(&app, &buffer, active_row) ||
      !app.reader.search_has_active || app.reader.search_active_index != 2 ||
      app.reader_view_state.left_panel != ReaderViewLeftPanel_Find)
    goto cleanup;
  checkpoint = 3;

  U32 active_ranges = 0;
  U32 inactive_ranges = 0;
  for (U32 index = 0; index < app.frame.search_highlight_count; index += 1)
  {
    if (app.frame.search_highlights[index].active) active_ranges += 1;
    else inactive_ranges += 1;
  }
  if (active_ranges != 1 || inactive_ranges == 0) goto cleanup;
  checkpoint = 4;

  for (U32 theme_index = 0; theme_index < ARRAY_COUNT(themes); theme_index += 1)
  {
    app.theme = themes[theme_index];
    eightvo_render_to_buffer(&app, &buffer);
    EightvoReaderContentTheme content = app.reader_content_theme;
    if (content.search_match != expected_active[theme_index] ||
        content.search_hit != expected_inactive[theme_index] ||
        content.selection != expected_selection[theme_index] ||
        content.search_hit == content.selection ||
        content.search_match == content.search_hit)
      goto cleanup;

    U32 active_draws = 0;
    U32 inactive_draws = 0;
    U32 selection_draws = 0;
    S32 first_active = -1;
    S32 first_inactive = -1;
    for (U32 command_index = 0;
         command_index < app.draw_commands.command_count[DrawLayer_World];
         command_index += 1)
    {
      const DrawCommand *command =
        app.draw_commands.commands[DrawLayer_World] + command_index;
      if (command->type != DrawCommandType_Rect) continue;
      if (command->v.rect.color == content.search_match)
      {
        if (first_active < 0) first_active = (S32)command_index;
        active_draws += 1;
      }
      if (command->v.rect.color == content.search_hit)
      {
        if (first_inactive < 0) first_inactive = (S32)command_index;
        inactive_draws += 1;
      }
      if (command->v.rect.color == content.selection) selection_draws += 1;
    }

    U64 active_pixels = 0;
    U64 inactive_pixels = 0;
    U64 selection_pixels = 0;
    for (U64 pixel_index = 0; pixel_index < pixel_count; pixel_index += 1)
    {
      U32 color = pixels[pixel_index] & 0x00FFFFFFU;
      if (color == content.search_match) active_pixels += 1;
      if (color == content.search_hit) inactive_pixels += 1;
      if (color == content.selection) selection_pixels += 1;
    }
    if (active_draws == 0 || inactive_draws == 0 ||
        first_active <= first_inactive || active_pixels == 0 ||
        inactive_pixels == 0 || selection_draws != 0 || selection_pixels != 0)
      goto cleanup;

    (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                      "%s_%s.bmp", output_prefix, theme_names[theme_index]);
    if (!eightvo_write_bmp(bmp_path, pixels, Width, Height)) goto cleanup;
    fprintf(stdout,
            "eightvo_reader_view_find_active_contrast theme=%s active=%06X inactive=%06X active_ranges=%u inactive_ranges=%u active_draws=%u inactive_draws=%u active_pixels=%llu inactive_pixels=%llu bmp=%s\n",
            theme_names[theme_index],
            content.search_match, content.search_hit,
            active_ranges, inactive_ranges, active_draws, inactive_draws,
            (unsigned long long)active_pixels,
            (unsigned long long)inactive_pixels,
            bmp_path);
  }
  checkpoint = 5;
  result = 0;

cleanup:
  if (result == 0)
  {
    fprintf(stdout,
            "eightvo_reader_view_find_active_contrast result=pass checkpoint=%u query=Paran active_index=2 themes=%u output=%s\n",
            checkpoint, (unsigned)ARRAY_COUNT(themes), output_prefix);
  }
  else
  {
    fprintf(stderr,
            "eightvo_reader_view_find_active_contrast result=fail checkpoint=%u matches=%u active=%u index=%u ranges=%u output=%s\n",
            checkpoint, app.reader.search_match_count,
            app.reader.search_has_active, app.reader.search_active_index,
            app.frame.search_highlight_count,
            output_prefix ? output_prefix : "-");
  }
  free(pixels);
  eightvo_app_release(&app);
  return result;
}

FUNCTION int
eightvo_run_reader_view_find_snippet_context_smoke(const char *path,
                                                     const char *bmp_path)
{
  enum { Width = 1400, Height = 780 };
  static const char expected_excerpt[] =
    "IMPERIAL COMMAND Ganoes Stabro Paran, a noble-born officer in the "
    "Malazan Empire";
  EightvoApp app = {0};
  U64 pixel_count = (U64)Width * Height;
  U32 *pixels = (U32 *)calloc((size_t)pixel_count, sizeof(U32));
  RenderBuffer buffer = {0};
  const ReaderViewSemanticNode *active_row = 0;
  const ReaderViewTextBinding *excerpt_binding = 0;
  U32 checkpoint = 0;
  U32 highlight_draws = 0;
  U64 highlight_pixels = 0;
  int result = 1;

  if (!pixels || !path || !path[0] || !bmp_path || !bmp_path[0] ||
      !eightvo_app_init(&app, Width, Height, 1, 0) ||
      !eightvo_open_path(&app, path))
    goto cleanup;
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);
  checkpoint = 1;

  const ReaderViewSemanticNode *find =
    eightvo_reader_view_semantic_control(
      &app.reader_view_frame, ReaderViewSemanticControl_Find);
  if (!find || !eightvo_reader_view_parity_space_node(&app, &buffer, find) ||
      app.reader_view_state.left_panel != ReaderViewLeftPanel_Find ||
      !eightvo_reader_view_parity_click(
        &app, &buffer, ReaderViewSemantic_SearchBox, 0))
    goto cleanup;

  MemoryCopy(app.input.text, "Paran", 5);
  app.input.text[5] = 0;
  app.input.text_length = 5;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  app.input.commit_pressed = 1;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  eightvo_render_to_buffer(&app, &buffer);
  if (app.reader.search_match_count == 0 ||
      app.reader_view_projection.find.row_count == 0 ||
      !eightvo_reader_view_text_is(
        app.reader_view_projection.find.rows[0].excerpt, expected_excerpt))
    goto cleanup;
  checkpoint = 2;

  ReaderViewKey first_key = app.reader_view_projection.find.rows[0].key;
  active_row = eightvo_reader_view_semantic_control_source(
    &app.reader_view_frame, ReaderViewSemanticControl_FindRow, first_key);
  if (!active_row ||
      !eightvo_reader_view_text_is(active_row->name, expected_excerpt) ||
      !eightvo_reader_view_parity_space_node(&app, &buffer, active_row) ||
      !app.reader.search_has_active || app.reader.search_active_index != 0 ||
      app.reader_view_state.left_panel != ReaderViewLeftPanel_Find)
    goto cleanup;
  checkpoint = 3;

  active_row = eightvo_reader_view_semantic_control_source(
    &app.reader_view_frame, ReaderViewSemanticControl_FindRow, first_key);
  if (!active_row || !eightvo_reader_view_text_is(active_row->name,
                                                    expected_excerpt))
    goto cleanup;
  for (UI0S32 index = 0;
       index < app.reader_view_frame.semantic_node_count;
       index += 1)
  {
    const ReaderViewSemanticNode *candidate =
      app.reader_view_frame.semantic_nodes + index;
    if (candidate->parent_id != active_row->id) continue;
    const ReaderViewTextBinding *binding =
      eightvo_reader_view_binding(&app, candidate->id);
    if (binding && binding->match_size > 0)
    {
      excerpt_binding = binding;
      break;
    }
  }
  ReaderViewText source_excerpt =
    app.reader_view_projection.find.rows[0].excerpt;
  if (!excerpt_binding || excerpt_binding->text.size <= 5 ||
      excerpt_binding->text.data <= source_excerpt.data ||
      excerpt_binding->text.data + excerpt_binding->text.size >
        source_excerpt.data + source_excerpt.size ||
      excerpt_binding->text.data[-1] != ' ' ||
      (((unsigned char)excerpt_binding->text.data[0] & 0xc0u) == 0x80u) ||
      excerpt_binding->match_size != 5 ||
      excerpt_binding->match_start + excerpt_binding->match_size >
        (UI0U32)excerpt_binding->text.size ||
      memcmp(excerpt_binding->text.data + excerpt_binding->match_start,
             "Paran", 5) != 0)
    goto cleanup;
  checkpoint = 4;

  EightvoReaderContentTheme theme =
    eightvo_reader_content_theme(app.theme);
  for (U32 index = 0;
       index < app.draw_commands.command_count[DrawLayer_UI];
       index += 1)
  {
    const DrawCommand *command =
      app.draw_commands.commands[DrawLayer_UI] + index;
    if (command->type == DrawCommandType_Rect &&
        command->v.rect.color == theme.user_highlight &&
        command->v.rect.x >= active_row->rect.x &&
        command->v.rect.x + command->v.rect.w <=
          active_row->rect.x + active_row->rect.w &&
        command->v.rect.y >= active_row->rect.y &&
        command->v.rect.y + command->v.rect.h <=
          active_row->rect.y + active_row->rect.h)
      highlight_draws += 1;
  }
  for (S32 y = active_row->rect.y;
       y < active_row->rect.y + active_row->rect.h;
       y += 1)
  {
    for (S32 x = active_row->rect.x;
         x < active_row->rect.x + active_row->rect.w;
         x += 1)
    {
      U32 color = pixels[(U64)y * Width + (U64)x] & 0x00FFFFFFU;
      if (color == theme.user_highlight) highlight_pixels += 1;
    }
  }
  if (highlight_draws == 0 || highlight_pixels == 0 ||
      !eightvo_write_bmp(bmp_path, pixels, Width, Height))
    goto cleanup;
  checkpoint = 5;
  result = 0;

cleanup:
  if (result == 0)
  {
    fprintf(stdout,
            "eightvo_reader_view_find_snippet_context result=pass checkpoint=%u query=Paran active_index=0 visible_bytes=%d match_start=%u match_size=%u highlight_draws=%u highlight_pixels=%llu bmp=%s\n",
            checkpoint,
            excerpt_binding ? excerpt_binding->text.size : 0,
            excerpt_binding ? excerpt_binding->match_start : 0,
            excerpt_binding ? excerpt_binding->match_size : 0,
            highlight_draws, (unsigned long long)highlight_pixels, bmp_path);
  }
  else
  {
    fprintf(stderr,
            "eightvo_reader_view_find_snippet_context result=fail checkpoint=%u matches=%u rows=%d active=%u index=%u visible=%d match_start=%u match_size=%u highlight_draws=%u highlight_pixels=%llu bmp=%s\n",
            checkpoint, app.reader.search_match_count,
            app.reader_view_projection.find.row_count,
            app.reader.search_has_active, app.reader.search_active_index,
            excerpt_binding ? excerpt_binding->text.size : 0,
            excerpt_binding ? excerpt_binding->match_start : 0,
            excerpt_binding ? excerpt_binding->match_size : 0,
            highlight_draws, (unsigned long long)highlight_pixels,
            bmp_path ? bmp_path : "-");
  }
  free(pixels);
  eightvo_app_release(&app);
  return result;
}

FUNCTION int
eightvo_run_reader_view_post_action_arrow_smoke(const char *path,
                                                  const char *output_prefix)
{
  enum { Width = 1400, Height = 780 };
  EightvoApp app = {0};
  U64 pixel_count = (U64)Width * Height;
  U32 *pixels = (U32 *)calloc((size_t)pixel_count, sizeof(U32));
  RenderBuffer buffer = {0};
  U32 checkpoint = 0;
  int result = 1;
  U32 bookmark_before_spine = 0;
  U32 bookmark_right_spine = 0;
  U32 note_before_spine = 0;
  U32 note_right_spine = 0;
  U32 font_before_spine = 0;
  U32 font_right_spine = 0;
  U64 bookmark_before_byte = 0;
  U64 bookmark_right_byte = 0;
  U64 note_before_byte = 0;
  U64 note_right_byte = 0;
  U64 font_before_byte = 0;
  U64 font_right_byte = 0;
  char bmp_path[EightvoPathCap] = {0};

  if (!pixels || !path || !path[0] || !output_prefix || !output_prefix[0] ||
      !eightvo_app_init(&app, Width, Height, 1, 0) ||
      !eightvo_open_path(&app, path))
    goto cleanup;
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);

  for (U32 page = 0; page < 8 && app.frame.visible_text.size < 32; page += 1)
  {
    if (eightvo_move_page(&app, 1) != EpubReaderResult_Ok) goto cleanup;
    eightvo_render_to_buffer(&app, &buffer);
  }
  if (app.frame.visible_text.size < 32) goto cleanup;
  checkpoint = 1;

  const ReaderViewSemanticNode *bookmark_control =
    eightvo_reader_view_semantic_control(
      &app.reader_view_frame, ReaderViewSemanticControl_Bookmark);
  if (!bookmark_control ||
      !eightvo_reader_view_parity_space_node(&app, &buffer, bookmark_control) ||
      app.bookmark_count != 1)
    goto cleanup;

  U64 selection_start = 0;
  while (selection_start < app.frame.visible_text.size &&
         app.frame.visible_text.str[selection_start] <= ' ')
    selection_start += 1;
  U64 selection_end = selection_start;
  for (U32 grapheme = 0;
       grapheme < 12 && selection_end < app.frame.visible_text.size;
       grapheme += 1)
  {
    selection_end = base_unicode_utf8_next_grapheme_boundary(
      app.frame.visible_text, selection_end);
  }
  DocSelection selection = {
    .spine_index = app.reader.active_spine_index,
    .text_byte_start = app.frame.view_byte_offset + selection_start,
    .text_byte_end = app.frame.view_byte_offset + selection_end,
  };
  if (selection_end <= selection_start ||
      epub_reader_set_selection(&app.reader, selection) != EpubReaderResult_Ok)
    goto cleanup;
  app.selection_anchor_rect = ui0_rect(500, 260, 4, 24);
  eightvo_prepare_selected_text(&app);
  if (!eightvo_set_highlight_color(&app, 2) ||
      !eightvo_save_selection_note(
        &app, (ReaderViewText){"Focus routing note", 18}))
    goto cleanup;
  epub_reader_clear_selection(&app.reader);
  app.selected_text[0] = 0;
  app.selection_anchor_rect = (UI0Rect){0};
  if (!eightvo_capture_frame(&app)) goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  checkpoint = 2;

  for (U32 page = 0; page < 2; page += 1)
    if (eightvo_move_page(&app, 1) != EpubReaderResult_Ok) goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  const ReaderViewSemanticNode *annotations =
    eightvo_reader_view_semantic_control(
      &app.reader_view_frame, ReaderViewSemanticControl_Annotations);
  if (!annotations ||
      !eightvo_reader_view_parity_space_node(&app, &buffer, annotations) ||
      !app.reader_view_state.right_panel_open)
    goto cleanup;
  checkpoint = 3;

  ReaderViewKey bookmark_key =
    eightvo_reader_view_parity_right_key(&app, ReaderViewRightRow_Bookmark);
  const ReaderViewSemanticNode *bookmark_row =
    eightvo_reader_view_semantic_control_source(
      &app.reader_view_frame, ReaderViewSemanticControl_RightRow, bookmark_key);
  if (!bookmark_key || !bookmark_row ||
      !eightvo_reader_view_parity_space_node(&app, &buffer, bookmark_row))
    goto cleanup;
  bookmark_row = eightvo_reader_view_semantic_control_source(
    &app.reader_view_frame, ReaderViewSemanticControl_RightRow, bookmark_key);
  if (!bookmark_row || app.reader_view_state.focus_id != bookmark_row->id ||
      eightvo_reader_view_horizontal_move_is_shared(&app))
    goto cleanup;
  bookmark_before_spine = app.reader.active_spine_index;
  bookmark_before_byte = app.reader.view_byte_offset;
  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_bookmark_before.bmp", output_prefix);
  if (!eightvo_write_bmp(bmp_path, pixels, Width, Height) ||
      eightvo_reader_view_route_keydown(&app, VK_RIGHT, 0) !=
        EightvoReaderKeyRoute_Handled)
    goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  bookmark_right_spine = app.reader.active_spine_index;
  bookmark_right_byte = app.reader.view_byte_offset;
  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_bookmark_right.bmp", output_prefix);
  if ((bookmark_right_spine == bookmark_before_spine &&
       bookmark_right_byte == bookmark_before_byte) ||
      !eightvo_write_bmp(bmp_path, pixels, Width, Height) ||
      eightvo_reader_view_route_keydown(&app, VK_LEFT, 0) !=
        EightvoReaderKeyRoute_Handled)
    goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_bookmark_left.bmp", output_prefix);
  if (app.reader.active_spine_index != bookmark_before_spine ||
      app.reader.view_byte_offset != bookmark_before_byte ||
      !eightvo_write_bmp(bmp_path, pixels, Width, Height))
    goto cleanup;
  checkpoint = 4;

  for (U32 page = 0; page < 2; page += 1)
    if (eightvo_move_page(&app, 1) != EpubReaderResult_Ok) goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  ReaderViewKey note_key =
    eightvo_reader_view_parity_right_key(&app, ReaderViewRightRow_Note);
  const ReaderViewSemanticNode *note_row =
    eightvo_reader_view_semantic_control_source(
      &app.reader_view_frame, ReaderViewSemanticControl_RightRow, note_key);
  if (!note_key || !note_row ||
      !eightvo_reader_view_parity_space_node(&app, &buffer, note_row))
    goto cleanup;
  note_row = eightvo_reader_view_semantic_control_source(
    &app.reader_view_frame, ReaderViewSemanticControl_RightRow, note_key);
  if (!note_row || app.reader_view_state.focus_id != note_row->id ||
      eightvo_reader_view_horizontal_move_is_shared(&app))
    goto cleanup;
  note_before_spine = app.reader.active_spine_index;
  note_before_byte = app.reader.view_byte_offset;
  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_note_before.bmp", output_prefix);
  if (!eightvo_write_bmp(bmp_path, pixels, Width, Height) ||
      eightvo_reader_view_route_keydown(&app, VK_RIGHT, 0) !=
        EightvoReaderKeyRoute_Handled)
    goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  note_right_spine = app.reader.active_spine_index;
  note_right_byte = app.reader.view_byte_offset;
  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_note_right.bmp", output_prefix);
  if ((note_right_spine == note_before_spine &&
       note_right_byte == note_before_byte) ||
      !eightvo_write_bmp(bmp_path, pixels, Width, Height) ||
      eightvo_reader_view_route_keydown(&app, VK_LEFT, 0) !=
        EightvoReaderKeyRoute_Handled)
    goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_note_left.bmp", output_prefix);
  if (app.reader.active_spine_index != note_before_spine ||
      app.reader.view_byte_offset != note_before_byte ||
      !eightvo_write_bmp(bmp_path, pixels, Width, Height))
    goto cleanup;
  checkpoint = 5;

  annotations = eightvo_reader_view_semantic_control(
    &app.reader_view_frame, ReaderViewSemanticControl_Annotations);
  if (!annotations ||
      !eightvo_reader_view_parity_space_node(&app, &buffer, annotations) ||
      app.reader_view_state.right_panel_open)
    goto cleanup;
  checkpoint = 51;
  const ReaderViewSemanticNode *font =
    eightvo_reader_view_semantic_control(
      &app.reader_view_frame, ReaderViewSemanticControl_FontFamily);
  if (!font || !eightvo_reader_view_parity_space_node(&app, &buffer, font) ||
      app.reader_view_state.popup != ReaderViewPopup_SettingMenu ||
      app.reader_view_state.active_setting_kind != ReaderViewSetting_FontFamily)
    goto cleanup;
  checkpoint = 52;
  ReaderViewKey font_key = 0;
  for (UI0S32 index = 0;
       index < app.reader_view_settings[0].choices.count;
       index += 1)
  {
    const ReaderViewChoice *choice =
      app.reader_view_settings[0].choices.items + index;
    if ((choice->flags & ReaderViewChoice_Enabled) != 0 &&
        (choice->flags & ReaderViewChoice_Selected) == 0)
    {
      font_key = choice->key;
      break;
    }
  }
  if (!font_key) goto cleanup;
  checkpoint = 53;
  const ReaderViewSemanticNode *font_option = 0;
  for (UI0S32 index = 0;
       font_key != 0 && index < app.reader_view_frame.semantic_node_count;
       index += 1)
  {
    const ReaderViewSemanticNode *node =
      app.reader_view_frame.semantic_nodes + index;
    if (node->role == ReaderViewSemantic_MenuItem &&
        node->source_key == font_key)
    {
      font_option = node;
      break;
    }
  }
  if (!font_option) goto cleanup;
  checkpoint = 54;
  if (!font_option ||
      !eightvo_reader_view_parity_space_node(&app, &buffer, font_option) ||
      app.reader_view_state.popup != ReaderViewPopup_None ||
      !eightvo_reader_view_focus_control_is(
        &app, ReaderViewSemanticControl_FontFamily) ||
      eightvo_reader_view_horizontal_move_is_shared(&app))
    goto cleanup;
  checkpoint = 55;
  B32 stable_font_page = 0;
  for (U32 probe = 0; probe < 64 && !stable_font_page; probe += 1)
  {
    U32 probe_spine = app.reader.active_spine_index;
    U64 probe_byte = app.reader.view_byte_offset;
    if (eightvo_move_page(&app, 1) != EpubReaderResult_Ok) break;
    B32 moved = app.reader.active_spine_index != probe_spine ||
                app.reader.view_byte_offset != probe_byte;
    if (eightvo_move_page(&app, -1) != EpubReaderResult_Ok) break;
    stable_font_page = moved &&
      app.reader.active_spine_index == probe_spine &&
      app.reader.view_byte_offset == probe_byte;
    if (!stable_font_page &&
        eightvo_move_page(&app, 1) != EpubReaderResult_Ok)
      break;
  }
  if (!stable_font_page ||
      !eightvo_reader_view_focus_control_is(
        &app, ReaderViewSemanticControl_FontFamily))
    goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  font_before_spine = app.reader.active_spine_index;
  font_before_byte = app.reader.view_byte_offset;
  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_font_before.bmp", output_prefix);
  if (!eightvo_write_bmp(bmp_path, pixels, Width, Height))
    goto cleanup;
  checkpoint = 56;
  if (eightvo_reader_view_route_keydown(&app, VK_RIGHT, 0) !=
      EightvoReaderKeyRoute_Handled)
    goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  font_right_spine = app.reader.active_spine_index;
  font_right_byte = app.reader.view_byte_offset;
  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_font_right.bmp", output_prefix);
  if ((font_right_spine == font_before_spine &&
       font_right_byte == font_before_byte) ||
      !eightvo_write_bmp(bmp_path, pixels, Width, Height))
    goto cleanup;
  checkpoint = 57;
  if (eightvo_reader_view_route_keydown(&app, VK_LEFT, 0) !=
      EightvoReaderKeyRoute_Handled)
    goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  checkpoint = 58;
  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_font_left.bmp", output_prefix);
  if (app.reader.active_spine_index != font_before_spine ||
      app.reader.view_byte_offset != font_before_byte ||
      !eightvo_write_bmp(bmp_path, pixels, Width, Height))
    goto cleanup;
  checkpoint = 6;

  const ReaderViewSemanticNode *find =
    eightvo_reader_view_semantic_control(
      &app.reader_view_frame, ReaderViewSemanticControl_Find);
  if (!find || !eightvo_reader_view_parity_space_node(&app, &buffer, find) ||
      app.reader_view_state.left_panel != ReaderViewLeftPanel_Find ||
      !eightvo_reader_view_parity_click(
        &app, &buffer, ReaderViewSemantic_SearchBox, 0))
    goto cleanup;
  MemoryCopy(app.input.text, "Paran", 5);
  app.input.text[5] = 0;
  app.input.text_length = 5;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  app.input.commit_pressed = 1;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  eightvo_render_to_buffer(&app, &buffer);
  U32 find_spine = app.reader.active_spine_index;
  U64 find_byte = app.reader.view_byte_offset;
  if (!eightvo_reader_view_text_is(
        reader_view_find_query(&app.reader_view_state), "Paran") ||
      !eightvo_reader_view_horizontal_move_is_shared(&app) ||
      eightvo_reader_view_route_keydown(&app, VK_LEFT, 0) !=
        EightvoReaderKeyRoute_Handled)
    goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  app.input.text[0] = 'X';
  app.input.text[1] = 0;
  app.input.text_length = 1;
  eightvo_render_to_buffer(&app, &buffer);
  eightvo_apply_reader_view_actions(&app);
  (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                    "%s_find_input_left.bmp", output_prefix);
  if (app.reader.active_spine_index != find_spine ||
      app.reader.view_byte_offset != find_byte ||
      !eightvo_reader_view_text_is(
        reader_view_find_query(&app.reader_view_state), "ParaXn") ||
      !eightvo_write_bmp(bmp_path, pixels, Width, Height))
    goto cleanup;
  checkpoint = 7;
  result = 0;

cleanup:
  if (result == 0)
  {
    fprintf(stdout,
            "eightvo_reader_view_post_action_arrow result=pass checkpoint=%u bookmark=%u:%llu>%u:%llu note=%u:%llu>%u:%llu font=%u:%llu>%u:%llu find=ParaXn output=%s\n",
            checkpoint,
            bookmark_before_spine, (unsigned long long)bookmark_before_byte,
            bookmark_right_spine, (unsigned long long)bookmark_right_byte,
            note_before_spine, (unsigned long long)note_before_byte,
            note_right_spine, (unsigned long long)note_right_byte,
            font_before_spine, (unsigned long long)font_before_byte,
            font_right_spine, (unsigned long long)font_right_byte,
            output_prefix);
  }
  else
  {
    fprintf(stderr,
            "eightvo_reader_view_post_action_arrow result=fail checkpoint=%u font=%u:%llu>%u:%llu current=%u:%llu focus=%llu popup=%d\n",
            checkpoint,
            font_before_spine, (unsigned long long)font_before_byte,
            font_right_spine, (unsigned long long)font_right_byte,
            app.reader.active_spine_index,
            (unsigned long long)app.reader.view_byte_offset,
            (unsigned long long)app.reader_view_state.focus_id,
            (int)app.reader_view_state.popup);
  }
  free(pixels);
  eightvo_app_release(&app);
  return result;
}

FUNCTION int
eightvo_run_publisher_typography_spacing_smoke(const char *epub_path,
                                                const char *output_prefix)
{
  enum { Width = 1536, Height = 912 };
  EightvoApp app = {0};
  EightvoApp reload = {0};
  U32 *pixels = (U32 *)calloc((size_t)Width * Height, sizeof(U32));
  U32 line_heights[3] = {0};
  U32 margin_units[3] = {0};
  S32 publisher_margin_pixels[3] = {0};
  U64 presentation_hashes[3] = {0};
  static const U32 parity_families[] = {
    FontProviderBookContentFamily_Georgia,
    FontProviderBookContentFamily_NotoSerif,
    FontProviderBookContentFamily_PalatinoLinotype,
    FontProviderBookContentFamily_BookAntiqua,
    FontProviderBookContentFamily_TimesNewRoman,
  };
  S32 family_bottom_gaps[ARRAY_COUNT(parity_families)] = {-1, -1, -1, -1, -1};
  S32 family_line_heights[ARRAY_COUNT(parity_families)] = {0};
  U32 family_row_counts[ARRAY_COUNT(parity_families)] = {0};
  U64 family_page_starts[ARRAY_COUNT(parity_families)] = {0};
  U64 family_page_ends[ARRAY_COUNT(parity_families)] = {0};
  char bmp_path[EightvoPathCap] = {0};
  char settings_path[EightvoPathCap] = {0};
  U32 italic_options = 0;
  U32 justified_options = 0;
  int result = 1;
  if (!pixels || !epub_path || !epub_path[0] ||
      !output_prefix || !output_prefix[0] ||
      !eightvo_app_init(&app, Width, Height, 1, 0) ||
      !eightvo_open_path(&app, epub_path))
  {
    fprintf(stderr,
            "eightvo_publisher_typography_spacing result=fail reason=setup\n");
    goto cleanup;
  }

  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SelectSetting,
    .setting_kind = ReaderViewSetting_FontFamily,
    .key = 1000 + FontProviderBookContentFamily_Georgia,
  });
  if (!app.font_family_user_override ||
      app.layout_key.embedded_fonts_enabled ||
      app.reader.typography.embedded_fonts_enabled)
  {
    fprintf(stderr,
            "eightvo_publisher_typography_spacing result=fail reason=font_override override=%d layout_embedded=%d typography_embedded=%d\n",
            app.font_family_user_override,
            app.layout_key.embedded_fonts_enabled,
            app.reader.typography.embedded_fonts_enabled);
    goto cleanup;
  }
  for (U32 spacing_index = 0; spacing_index < 3; spacing_index += 1)
  {
    eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
      .kind = ReaderViewAction_SelectSetting,
      .setting_kind = ReaderViewSetting_LineSpacing,
      .key = 3000 + spacing_index,
    });
    if (app.line_spacing_index != spacing_index ||
        !epub_reader_rebuild_search(&app.reader,
                                    str8_from_cstr("1154th Year")) ||
        app.reader.search_match_count == 0 ||
        eightvo_navigate_to_search_match(
          &app, 0, &(EpubReaderSearchNavigationResult){0}) !=
            EpubReaderResult_Ok)
    {
      fprintf(stderr,
              "eightvo_publisher_typography_spacing result=fail reason=setting_or_navigation index=%u\n",
              spacing_index);
      goto cleanup;
    }
    eightvo_render_to_buffer(&app, &buffer);
    if (!app.presentation_complete || !app.presentation_frame.valid ||
        app.presentation_frame.row_count != app.frame.style_row_count)
    {
      fprintf(stderr,
              "eightvo_publisher_typography_spacing result=fail reason=presentation index=%u\n",
              spacing_index);
      goto cleanup;
    }

    B32 found_date = 0;
    B32 date_italic = 0;
    U32 justified_rows = 0;
    S32 publisher_margin_px = -1;
    for (U32 row_index = 0;
         row_index < app.presentation_frame.row_count;
         row_index += 1)
    {
      const PresentationEngineBlockFlowRow *presentation_row =
        app.presentation_frame.rows + row_index;
      if (presentation_row->style_index >= app.frame.style_row_count)
        continue;
      const EpubReaderFrameStyleRow *row =
        app.frame.style_rows + presentation_row->style_index;
      if (publisher_margin_px < 0 && row->line_row == 0 &&
          row->margin_top_rows > 0)
      {
        EightvoPresentationRowMetrics metrics = {0};
        if (!eightvo_resolve_presentation_row_metrics(&app, row, &metrics))
        {
          fprintf(stderr,
                  "eightvo_publisher_typography_spacing result=fail reason=margin_metrics index=%u row=%u\n",
                  spacing_index, row_index);
          goto cleanup;
        }
        publisher_margin_px = metrics.margin_before_px;
      }
      U32 start = MIN(row->byte_start, (U32)app.frame.visible_text.size);
      U32 end = MIN(row->byte_end, (U32)app.frame.visible_text.size);
      while (end > start &&
             (app.frame.visible_text.str[end - 1] == '\n' ||
              app.frame.visible_text.str[end - 1] == '\r'))
      {
        end -= 1;
      }
      if (end <= start) { continue; }
      String8 text = str8(app.frame.visible_text.str + start, end - start);
      const char date_prefix[] = "1154th Year";
      B32 contains_date = 0;
      for (U64 at = 0;
           at + sizeof(date_prefix) - 1 <= text.size && !contains_date;
           at += 1)
      {
        contains_date = memcmp(text.str + at,
                               date_prefix,
                               sizeof(date_prefix) - 1) == 0;
      }
      EightvoReaderStyledRow styled = {0};
      if (!eightvo_reader_styled_row_build(&app, row, presentation_row,
                                            start, end, &styled))
      {
        fprintf(stderr,
                "eightvo_publisher_typography_spacing result=fail reason=styled_row index=%u row=%u\n",
                spacing_index, row_index);
        goto cleanup;
      }
      if (contains_date)
      {
        found_date = 1;
        for (U32 span_index = 0;
             span_index < styled.span_count;
             span_index += 1)
        {
          if (app.reader_span_styles[span_index].flags &
              DocTextStyleFlag_Italic)
          {
            date_italic = 1;
          }
        }
      }
      if (styled.justify_space_count > 0 &&
          (styled.justify_extra_px > 0 ||
           styled.justify_extra_remainder > 0) &&
          styled.display.w > styled.natural_width)
      {
        justified_rows += 1;
      }
    }
    if (!found_date || !date_italic || justified_rows < 4)
    {
      fprintf(stderr,
              "eightvo_publisher_typography_spacing result=fail reason=typography index=%u date=%d italic=%d justified=%u\n",
              spacing_index, found_date, date_italic, justified_rows);
      goto cleanup;
    }
    italic_options += 1;
    justified_options += justified_rows >= 4;
    line_heights[spacing_index] = (U32)app.layout_key.line_height;
    margin_units[spacing_index] = app.layout_key.margin_unit_permille;
    publisher_margin_pixels[spacing_index] = publisher_margin_px;
    presentation_hashes[spacing_index] = app.presentation_hash;
    (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                      "%s_spacing_%u.bmp", output_prefix, spacing_index);
    if (!eightvo_write_bmp(bmp_path, pixels, Width, Height))
    {
      fprintf(stderr,
              "eightvo_publisher_typography_spacing result=fail reason=evidence index=%u\n",
              spacing_index);
      goto cleanup;
    }
    eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
      .kind = ReaderViewAction_NextPage,
    });
    eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
      .kind = ReaderViewAction_PreviousPage,
    });
    if (app.line_spacing_index != spacing_index)
    {
      fprintf(stderr,
              "eightvo_publisher_typography_spacing result=fail reason=navigation_persistence index=%u\n",
              spacing_index);
      goto cleanup;
    }
  }
  if (line_heights[1] != line_heights[0] + 5 ||
      line_heights[2] != line_heights[1] + 5 ||
      margin_units[0] != 1000 ||
      margin_units[1] >= margin_units[0] ||
      margin_units[2] >= margin_units[1] ||
      publisher_margin_pixels[0] <= 0 ||
      publisher_margin_pixels[0] != publisher_margin_pixels[1] ||
      publisher_margin_pixels[1] != publisher_margin_pixels[2] ||
      presentation_hashes[0] == presentation_hashes[1] ||
      presentation_hashes[1] == presentation_hashes[2])
  {
    fprintf(stderr,
            "eightvo_publisher_typography_spacing result=fail reason=geometry heights=%u,%u,%u margin_units=%u,%u,%u publisher_margin=%d,%d,%d hashes=%016llx,%016llx,%016llx\n",
            line_heights[0], line_heights[1], line_heights[2],
            margin_units[0], margin_units[1], margin_units[2],
            publisher_margin_pixels[0], publisher_margin_pixels[1],
            publisher_margin_pixels[2],
            (unsigned long long)presentation_hashes[0],
            (unsigned long long)presentation_hashes[1],
            (unsigned long long)presentation_hashes[2]);
    goto cleanup;
  }

  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SelectSetting,
    .setting_kind = ReaderViewSetting_LineSpacing,
    .key = 3000,
  });
  U32 available_family_count = 0;
  for (U32 family_index = 0;
       family_index < ARRAY_COUNT(parity_families);
       family_index += 1)
  {
    U32 family = parity_families[family_index];
    if (!epub_reader_typography_family_available(&app.reader.typography,
                                                  family))
    {
      continue;
    }
    available_family_count += 1;
    eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
      .kind = ReaderViewAction_SelectSetting,
      .setting_kind = ReaderViewSetting_FontFamily,
      .key = 1000 + family,
    });
    if (!app.font_family_user_override ||
        app.layout_key.font_family_index != family ||
        app.layout_key.embedded_fonts_enabled ||
        app.reader.typography.embedded_fonts_enabled ||
        app.layout_key.margin_unit_permille != 1000 ||
        app.reader_margin_line_height != app.layout_key.line_height ||
        !epub_reader_rebuild_search(&app.reader,
                                    str8_from_cstr("1161st Year")) ||
        app.reader.search_match_count == 0 ||
        eightvo_navigate_to_search_match(
          &app, 0, &(EpubReaderSearchNavigationResult){0}) !=
            EpubReaderResult_Ok)
    {
      fprintf(stderr,
              "eightvo_publisher_typography_spacing result=fail reason=family_layout index=%u family=%u override=%d layout_family=%u layout_embedded=%d typography_embedded=%d margin_unit=%u margin_line=%d line=%d\n",
              family_index, family, app.font_family_user_override,
              app.layout_key.font_family_index,
              app.layout_key.embedded_fonts_enabled,
              app.reader.typography.embedded_fonts_enabled,
              app.layout_key.margin_unit_permille,
              app.reader_margin_line_height,
              app.layout_key.line_height);
      goto cleanup;
    }
    eightvo_render_to_buffer(&app, &buffer);
    if (!app.presentation_complete || !app.presentation_frame.valid ||
        app.presentation_frame.row_count == 0 || !app.reader.has_current_page)
    {
      fprintf(stderr,
              "eightvo_publisher_typography_spacing result=fail reason=family_presentation index=%u family=%u\n",
              family_index, family);
      goto cleanup;
    }
    const PresentationEngineBlockFlowRow *last_row =
      app.presentation_frame.rows + app.presentation_frame.row_count - 1;
    S32 content_bottom = app.reader_content_geometry.content_rect.y +
                         app.reader_content_geometry.content_rect.h;
    S32 last_bottom = last_row->row_rect.y + last_row->row_rect.h;
    S32 bottom_gap = content_bottom - last_bottom;
    S32 maximum_gap = app.layout_key.line_height * 2;
    if (bottom_gap < 0 || bottom_gap > maximum_gap)
    {
      fprintf(stderr,
              "eightvo_publisher_typography_spacing result=fail reason=family_bottom_gap index=%u family=%u gap=%d maximum=%d rows=%u\n",
              family_index, family, bottom_gap, maximum_gap,
              app.presentation_frame.row_count);
      goto cleanup;
    }
    family_bottom_gaps[family_index] = bottom_gap;
    family_line_heights[family_index] = app.layout_key.line_height;
    family_row_counts[family_index] = app.presentation_frame.row_count;
    family_page_starts[family_index] = app.reader.current_page.first_byte;
    family_page_ends[family_index] = app.reader.current_page.one_past_last_byte;
    (void)cstr_format(bmp_path, ARRAY_COUNT(bmp_path),
                      "%s_family_%u.bmp", output_prefix, family_index);
    if (!eightvo_write_bmp(bmp_path, pixels, Width, Height))
    {
      fprintf(stderr,
              "eightvo_publisher_typography_spacing result=fail reason=family_evidence index=%u\n",
              family_index);
      goto cleanup;
    }
  }
  UI0Rect parity_content = app.reader_content_geometry.content_rect;
  if (available_family_count < 3 ||
      parity_content.x != 490 || parity_content.y != 124 ||
      parity_content.w != 556 || parity_content.h != 682 ||
      family_line_heights[FontProviderBookContentFamily_PalatinoLinotype] != 31 ||
      family_row_counts[FontProviderBookContentFamily_PalatinoLinotype] != 18 ||
      family_page_starts[FontProviderBookContentFamily_PalatinoLinotype] != 0 ||
      family_page_ends[FontProviderBookContentFamily_PalatinoLinotype] != 873)
  {
    fprintf(stderr,
            "eightvo_publisher_typography_spacing result=fail reason=family_parity available=%u gaps=%d,%d,%d,%d,%d content=%d,%d,%d,%d palatino_line=%d rows=%u range=%llu..%llu\n",
            available_family_count,
            family_bottom_gaps[0], family_bottom_gaps[1],
            family_bottom_gaps[2], family_bottom_gaps[3],
            family_bottom_gaps[4],
            parity_content.x, parity_content.y,
            parity_content.w, parity_content.h,
            family_line_heights[FontProviderBookContentFamily_PalatinoLinotype],
            family_row_counts[FontProviderBookContentFamily_PalatinoLinotype],
            (unsigned long long)
              family_page_starts[FontProviderBookContentFamily_PalatinoLinotype],
            (unsigned long long)
              family_page_ends[FontProviderBookContentFamily_PalatinoLinotype]);
    goto cleanup;
  }

  (void)cstr_format(settings_path, ARRAY_COUNT(settings_path),
                    "%s_settings.bin", output_prefix);
  app.persistence_enabled = 1;
  eightvo_copy_cstr(app.settings_path, ARRAY_COUNT(app.settings_path),
                     settings_path);
  if (!eightvo_save_settings(&app) ||
      !eightvo_app_init(&reload, Width, Height, 1, 0))
  {
    fprintf(stderr,
            "eightvo_publisher_typography_spacing result=fail reason=persistence_write\n");
    goto cleanup;
  }
  reload.persistence_enabled = 1;
  eightvo_copy_cstr(reload.settings_path, ARRAY_COUNT(reload.settings_path),
                     settings_path);
  eightvo_load_settings(&reload);
  if (reload.line_spacing_index != 0 ||
      !reload.font_family_user_override)
  {
    fprintf(stderr,
            "eightvo_publisher_typography_spacing result=fail reason=restart_persistence spacing=%u override=%d\n",
            reload.line_spacing_index,
            reload.font_family_user_override);
    goto cleanup;
  }

  EightvoSettingsFileV2 legacy_settings = {
    .magic = EIGHTVO_SETTINGS_MAGIC,
    .version = 2,
    .font_family = FontProviderBookContentFamily_PalatinoLinotype,
    .text_size_index = 1,
    .line_spacing_index = 1,
    .theme = EightvoTheme_Light,
  };
  reload.font_family_user_override = 0;
  if (!os_write_entire_file_atomic(settings_path,
                                   &legacy_settings,
                                   sizeof(legacy_settings)))
  {
    fprintf(stderr,
            "eightvo_publisher_typography_spacing result=fail reason=legacy_write\n");
    goto cleanup;
  }
  eightvo_load_settings(&reload);
  if (!reload.font_family_user_override ||
      reload.font_family != FontProviderBookContentFamily_PalatinoLinotype)
  {
    fprintf(stderr,
            "eightvo_publisher_typography_spacing result=fail reason=legacy_migration family=%u override=%d\n",
            reload.font_family,
            reload.font_family_user_override);
    goto cleanup;
  }

  fprintf(stdout,
          "eightvo_publisher_typography_spacing result=pass book=gotm_new options=3 action=select_setting font_override=explicit embedded_fonts=disabled italics=%u justification=%u line_heights=%u,%u,%u margin_units=%u,%u,%u publisher_margin=%d,%d,%d family_available=%u family_gaps=%d,%d,%d,%d,%d family_line_heights=%d,%d,%d,%d,%d family_rows=%u,%u,%u,%u,%u family_ranges=%llu..%llu,%llu..%llu,%llu..%llu,%llu..%llu,%llu..%llu parity_content=%d,%d,%d,%d navigation=persistent restart=persistent legacy_v2=override hashes=%016llx,%016llx,%016llx output=%s\n",
          italic_options, justified_options,
          line_heights[0], line_heights[1], line_heights[2],
          margin_units[0], margin_units[1], margin_units[2],
          publisher_margin_pixels[0], publisher_margin_pixels[1],
          publisher_margin_pixels[2],
          available_family_count,
          family_bottom_gaps[0], family_bottom_gaps[1],
          family_bottom_gaps[2], family_bottom_gaps[3],
          family_bottom_gaps[4],
          family_line_heights[0], family_line_heights[1],
          family_line_heights[2], family_line_heights[3],
          family_line_heights[4],
          family_row_counts[0], family_row_counts[1],
          family_row_counts[2], family_row_counts[3],
          family_row_counts[4],
          (unsigned long long)family_page_starts[0],
          (unsigned long long)family_page_ends[0],
          (unsigned long long)family_page_starts[1],
          (unsigned long long)family_page_ends[1],
          (unsigned long long)family_page_starts[2],
          (unsigned long long)family_page_ends[2],
          (unsigned long long)family_page_starts[3],
          (unsigned long long)family_page_ends[3],
          (unsigned long long)family_page_starts[4],
          (unsigned long long)family_page_ends[4],
          parity_content.x, parity_content.y,
          parity_content.w, parity_content.h,
          (unsigned long long)presentation_hashes[0],
          (unsigned long long)presentation_hashes[1],
          (unsigned long long)presentation_hashes[2],
          output_prefix);
  result = 0;

cleanup:
  if (pixels) { free(pixels); }
  eightvo_app_release(&reload);
  eightvo_app_release(&app);
  return result;
}

FUNCTION int
eightvo_run_reader_view_smoke(const char *path, const char *export_path)
{
  enum { Width = 1100, Height = 760 };
  EightvoApp app = {0};
  U64 pixel_count = (U64)Width * Height;
  U32 *pixels = (U32 *)calloc((size_t)pixel_count, sizeof(U32));
  if (!pixels || !eightvo_app_init(&app, Width, Height, 1, 0) ||
      !eightvo_open_path(&app, path))
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=open\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&app, &buffer);
  B32 adapter_all_ops = eightvo_draw_adapter_covers_all_ops(&app);
  B32 adapter_edges = eightvo_draw_adapter_covers_reference_edges(&app);
  B32 adapter_text =
    eightvo_draw_adapter_covers_reference_text_styles(&app);
  B32 adapter_find =
    eightvo_draw_adapter_covers_measured_find_match(&app);
  B32 adapter_find_labels =
    eightvo_draw_adapter_covers_find_status_and_metadata(&app);
  B32 adapter_note =
    eightvo_draw_adapter_covers_note_editable_row(&app);
  B32 find_metrics = eightvo_reader_view_covers_find_text_metrics(&app);
  B32 note_metrics = eightvo_reader_view_covers_note_text_metrics(&app);
  B32 toc_identity =
    eightvo_reader_view_covers_noncontiguous_toc_identity(&app);
  B32 right_order = eightvo_reader_view_covers_mixed_right_order(&app);
  B32 right_projection =
    eightvo_reader_view_covers_right_projection_contract(&app);
  B32 keyboard_routing =
    eightvo_reader_view_keyboard_input_routing_regression(&app);
  B32 find_shortcut =
    eightvo_reader_view_find_shortcut_focus_regression(&app, &buffer);
  if (!app.reader_view_ready ||
      app.reader_view_projection.settings.count != READER_VIEW_SETTING_CAP ||
      app.reader_view_projection.toc.row_count < 2 ||
      !eightvo_reader_view_has_semantic(&app.reader_view_frame, "Contents") ||
      !eightvo_reader_view_has_semantic(&app.reader_view_frame, "Find") ||
      !eightvo_reader_view_has_semantic(&app.reader_view_frame, "Bookmark") ||
      !eightvo_reader_view_has_semantic(&app.reader_view_frame, "Annotations") ||
      app.reader_view_layout.host_toolbar_trailing_rect.w !=
        EightvoHostToolbarSlotWidth ||
      app.reader_content_geometry.page_surface_rect.w >
        READER_VIEW_DEFAULT_PAGE_MAX_WIDTH ||
      app.reader_content_geometry.content_rect.x -
        app.reader_content_geometry.page_surface_rect.x !=
        READER_VIEW_DEFAULT_CONTENT_INSET_X ||
      app.reader_content_geometry.content_rect.y -
        app.reader_content_geometry.page_surface_rect.y !=
        READER_VIEW_DEFAULT_CONTENT_INSET_Y ||
      app.draw_adapter_stats.unsupported_count != 0 ||
      !adapter_all_ops || !adapter_edges || !adapter_text || !adapter_find ||
      !adapter_find_labels || !adapter_note ||
      !find_metrics || !note_metrics || !toc_identity || !right_order ||
      !right_projection || !keyboard_routing || !find_shortcut)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=chrome ready=%d settings=%d toc=%d contents=%d find=%d bookmark=%d annotations=%d trailing=%d page=%d content_dx=%d content_dy=%d unsupported=%u adapters=%d/%d/%d/%d/%d/%d metrics=%d/%d identities=%d/%d/%d keyboard=%d shortcut=%d\n",
            app.reader_view_ready,
            app.reader_view_projection.settings.count,
            app.reader_view_projection.toc.row_count,
            eightvo_reader_view_has_semantic(&app.reader_view_frame, "Contents"),
            eightvo_reader_view_has_semantic(&app.reader_view_frame, "Find"),
            eightvo_reader_view_has_semantic(&app.reader_view_frame, "Bookmark"),
            eightvo_reader_view_has_semantic(&app.reader_view_frame, "Annotations"),
            app.reader_view_layout.host_toolbar_trailing_rect.w,
            app.reader_content_geometry.page_surface_rect.w,
            app.reader_content_geometry.content_rect.x -
              app.reader_content_geometry.page_surface_rect.x,
            app.reader_content_geometry.content_rect.y -
              app.reader_content_geometry.page_surface_rect.y,
            app.draw_adapter_stats.unsupported_count,
            adapter_all_ops, adapter_edges, adapter_text, adapter_find,
            adapter_find_labels, adapter_note,
            find_metrics, note_metrics, toc_identity, right_order,
            right_projection, keyboard_routing, find_shortcut);
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }

  B32 reference_focus =
    eightvo_reader_view_reference_focus_order(&app, &buffer);
  B32 panel_focus = reference_focus &&
    eightvo_reader_view_panel_focus_regression(&app, &buffer);
  B32 gutter_keyboard = panel_focus &&
    eightvo_reader_view_gutter_keyboard_regression(&app, &buffer);
  if (!reference_focus || !panel_focus || !gutter_keyboard)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=reference_panel_focus_or_gutter focus=%d panel=%d gutter=%d\n",
            reference_focus,
            panel_focus,
            gutter_keyboard);
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }

  U32 initial_spine = app.reader.active_spine_index;
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_ActivateTocRow,
    .key = 2,
  });
  if (app.reader.active_spine_index == initial_spine)
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=toc_action\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_HistoryBack,
  });
  B32 history_back_ok = app.reader.active_spine_index == initial_spine;
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_HistoryForward,
  });
  B32 history_forward_ok = app.reader.active_spine_index != initial_spine;
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_HistoryBack,
  });
  if (!history_back_ok || !history_forward_ok ||
      app.reader.active_spine_index != initial_spine)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=history_actions initial=%u current=%u back_ok=%d forward_ok=%d back_count=%u forward_count=%u\n",
            initial_spine,
            app.reader.active_spine_index,
            history_back_ok,
            history_forward_ok,
            app.reader.back_stack_count,
            app.reader.forward_stack_count);
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }

  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SelectSetting,
    .setting_kind = ReaderViewSetting_FontSize,
    .key = 2001,
  });
  if (app.text_size_index != 1)
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=setting\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SelectSetting,
    .setting_kind = ReaderViewSetting_LineSpacing,
    .key = 3001,
  });
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SelectSetting,
    .setting_kind = ReaderViewSetting_Theme,
    .key = 4000,
  });
  if (app.line_spacing_index != 1 || app.theme != EightvoTheme_Dark)
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=settings\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_prepare_reader_view_projection(&app);
  for (UI0S32 index = 0;
       index < app.reader_view_settings[0].choices.count;
       index += 1)
  {
    ReaderViewChoice choice = app.reader_view_settings[0].choices.items[index];
    if ((choice.flags & ReaderViewChoice_Selected) == 0)
    {
      U32 old_family = app.font_family;
      eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
        .kind = ReaderViewAction_SelectSetting,
        .setting_kind = ReaderViewSetting_FontFamily,
        .key = choice.key,
      });
      if (app.font_family == old_family)
      {
        fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=font_setting\n");
        free(pixels);
        eightvo_app_release(&app);
        return 1;
      }
      break;
    }
  }
  U32 find_edit_match_count = app.reader.search_match_count;
  U32 find_edit_back_count = app.reader.back_stack_count;
  U32 find_edit_spine = app.reader.active_spine_index;
  U64 find_edit_byte = app.reader.view_byte_offset;
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_FindChanged,
    .text = {.data = "standalone", .size = 10},
  });
  if (!eightvo_reader_view_text_is(
        reader_view_find_query(&app.reader_view_state), "standalone") ||
      app.reader.search_match_count != find_edit_match_count ||
      app.reader.search_query_size != 0 ||
      app.reader.back_stack_count != find_edit_back_count ||
      app.reader.active_spine_index != find_edit_spine ||
      app.reader.view_byte_offset != find_edit_byte)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=find_edit_executed\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_FindCommitted,
    .text = {.data = "standalone", .size = 10},
  });
  eightvo_prepare_reader_view_projection(&app);
  if (app.reader.search_match_count < 2 ||
      app.reader.search_query_size != 10 ||
      memcmp(app.reader.search_query_storage, "standalone", 10) != 0 ||
      !app.reader.search_has_active || app.reader.search_active_index != 0 ||
      app.reader_view_projection.find.row_count < 2 ||
      !eightvo_reader_view_text_is(
        app.reader_view_projection.find.committed_query, "standalone"))
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=find_commit\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  ReaderViewKey second_find_key =
    app.reader_view_projection.find.rows[1].key;
  if (!eightvo_reader_view_navigation_panel_interaction_regression(
        &app, &buffer, second_find_key))
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=navigation_panel_interactions\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_ActivateFindRow,
    .key = second_find_key,
  });
  if (!app.reader.search_has_active || app.reader.search_active_index != 1)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=find_result_action\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  U32 find_clear_spine = app.reader.active_spine_index;
  U64 find_clear_byte = app.reader.view_byte_offset;
  U32 find_clear_back_count = app.reader.back_stack_count;
  U32 find_clear_forward_count = app.reader.forward_stack_count;
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_FindChanged,
    .text = {.data = "", .size = 0},
  });
  eightvo_prepare_reader_view_projection(&app);
  if (!eightvo_reader_view_text_is(
        reader_view_find_query(&app.reader_view_state), "") ||
      app.reader.search_match_count != 0 ||
      app.reader.search_query_size != 0 || app.reader.search_has_active ||
      app.reader_view_projection.find.row_count != 0 ||
      app.reader.active_spine_index != find_clear_spine ||
      app.reader.view_byte_offset != find_clear_byte ||
      app.reader.back_stack_count != find_clear_back_count ||
      app.reader.forward_stack_count != find_clear_forward_count)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=find_clear\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  {
    char rollback_path[EightvoPathCap] = {0};
    eightvo_copy_cstr(rollback_path, ARRAY_COUNT(rollback_path),
                       app.annotations_path);
    B32 rollback_persistence = app.persistence_enabled;
    U32 rollback_count = app.bookmark_count;
    U64 rollback_next_id = app.next_record_id;
    U64 rollback_revision = app.annotation_revision;
    app.persistence_enabled = 1;
    eightvo_copy_cstr(app.annotations_path,
                       ARRAY_COUNT(app.annotations_path),
                       "?:\\eightvo_reader_view_bookmark_add_failure.annotations");
    eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleBookmark,
    });
    app.persistence_enabled = rollback_persistence;
    eightvo_copy_cstr(app.annotations_path,
                       ARRAY_COUNT(app.annotations_path), rollback_path);
    if (app.bookmark_count != rollback_count ||
        app.next_record_id != rollback_next_id ||
        app.annotation_revision != rollback_revision)
    {
      fprintf(stderr,
              "eightvo_reader_view_smoke result=fail reason=bookmark_add_rollback\n");
      free(pixels);
      eightvo_app_release(&app);
      return 1;
    }
  }
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_ToggleBookmark,
  });
  if (app.bookmark_count != 1)
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=bookmark\n");
    free(pixels);
    eightvo_app_release(&app);
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
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=selection\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  app.selection_anchor_rect = ui0_rect(400, 180, 4, 24);
  eightvo_prepare_selected_text(&app);
  char selection_failure_annotations_path[EightvoPathCap] = {0};
  eightvo_copy_cstr(selection_failure_annotations_path,
                     ARRAY_COUNT(selection_failure_annotations_path),
                     app.annotations_path);
  B32 selection_failure_persistence_enabled = app.persistence_enabled;
  U32 selection_failure_count = app.highlight_count;
  U64 selection_failure_next_id = app.next_record_id;
  U64 selection_failure_revision = app.annotation_revision;
  app.persistence_enabled = 1;
  eightvo_copy_cstr(app.annotations_path,
                     ARRAY_COUNT(app.annotations_path),
                     "?:\\eightvo_reader_view_selection_note_failure.annotations");
  B32 selection_note_failure_rejected = !eightvo_save_selection_note(
    &app, (ReaderViewText){"Must not persist", 16});
  app.persistence_enabled = selection_failure_persistence_enabled;
  eightvo_copy_cstr(app.annotations_path,
                     ARRAY_COUNT(app.annotations_path),
                     selection_failure_annotations_path);
  if (!selection_note_failure_rejected ||
      app.highlight_count != selection_failure_count ||
      app.next_record_id != selection_failure_next_id ||
      app.annotation_revision != selection_failure_revision ||
      app.highlights[selection_failure_count].id != 0)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=selection_note_atomic_rollback\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  {
    char rollback_path[EightvoPathCap] = {0};
    eightvo_copy_cstr(rollback_path, ARRAY_COUNT(rollback_path),
                       app.annotations_path);
    B32 rollback_persistence = app.persistence_enabled;
    EightvoBookmark rollback_bookmark = app.bookmarks[0];
    U64 rollback_revision = app.annotation_revision;
    app.persistence_enabled = 1;
    eightvo_copy_cstr(app.annotations_path,
                       ARRAY_COUNT(app.annotations_path),
                       "?:\\eightvo_reader_view_bookmark_remove_failure.annotations");
    eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleBookmark,
    });
    app.persistence_enabled = rollback_persistence;
    eightvo_copy_cstr(app.annotations_path,
                       ARRAY_COUNT(app.annotations_path), rollback_path);
    if (app.bookmark_count != 1 ||
        app.bookmarks[0].id != rollback_bookmark.id ||
        app.annotation_revision != rollback_revision)
    {
      fprintf(stderr,
              "eightvo_reader_view_smoke result=fail reason=bookmark_remove_rollback\n");
      free(pixels);
      eightvo_app_release(&app);
      return 1;
    }
  }
  if (!eightvo_set_highlight_color(&app, 2))
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=highlight\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_prepare_reader_view_projection(&app);
  const ReaderViewRightRow *highlight_row = 0;
  for (UI0S32 index = 0;
       index < app.reader_view_projection.right.row_count;
       index += 1)
  {
    const ReaderViewRightRow *row =
      app.reader_view_projection.right.rows + index;
    if (row->kind == ReaderViewRightRow_Highlight)
    {
      highlight_row = row;
      break;
    }
  }
  char expected_highlight_secondary[EightvoRecordLabelCap] = {0};
  (void)cstr_format(
    expected_highlight_secondary,
    ARRAY_COUNT(expected_highlight_secondary),
    "Highlight - re10 loc %llu",
    (unsigned long long)eightvo_reader_location_for_position(
      &app, app.highlights[0].spine_index, app.highlights[0].start_byte));
  if (!highlight_row ||
      !eightvo_reader_view_text_is(highlight_row->secondary,
                                    expected_highlight_secondary))
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=highlight_metadata\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  U64 note_revision = app.annotation_revision;
  B32 note_saved = eightvo_save_selection_note(
    &app, (ReaderViewText){"Smoke note", 10});
  if (!note_saved || app.annotation_revision != note_revision + 1)
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=note\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_prepare_reader_view_projection(&app);
  const ReaderViewRightRow *bookmark_row = 0;
  const ReaderViewRightRow *attached_highlight_row = 0;
  const ReaderViewRightRow *note_row = 0;
  for (UI0S32 index = 0;
       index < app.reader_view_projection.right.row_count;
       index += 1)
  {
    const ReaderViewRightRow *row =
      app.reader_view_projection.right.rows + index;
    if (row->kind == ReaderViewRightRow_Bookmark) bookmark_row = row;
    else if (row->kind == ReaderViewRightRow_Highlight)
      attached_highlight_row = row;
    else if (row->kind == ReaderViewRightRow_Note) note_row = row;
  }
  char expected_bookmark_secondary[EightvoRecordLabelCap] = {0};
  char expected_note_secondary[EightvoRecordLabelCap] = {0};
  (void)cstr_format(
    expected_bookmark_secondary,
    ARRAY_COUNT(expected_bookmark_secondary),
    "Bookmark - re10 loc %llu",
    (unsigned long long)eightvo_reader_location_for_position(
      &app, app.bookmarks[0].spine_index, app.bookmarks[0].byte_offset));
  (void)cstr_format(
    expected_note_secondary,
    ARRAY_COUNT(expected_note_secondary),
    "Note - re10 loc %llu",
    (unsigned long long)eightvo_reader_location_for_position(
      &app, app.highlights[0].spine_index, app.highlights[0].start_byte));
  if (app.reader_view_projection.right.row_count != 3 ||
      app.reader_view_projection.selection.current_color_key != 5002 ||
      !bookmark_row || !attached_highlight_row || !note_row ||
      bookmark_row->key == attached_highlight_row->key ||
      bookmark_row->key == note_row->key ||
      attached_highlight_row->key == note_row->key ||
      bookmark_row->color_key != 0 ||
      (bookmark_row->flags & ReaderViewRow_Starred) == 0 ||
      attached_highlight_row->color_key != 5002 ||
      note_row->color_key != 5002 ||
      (bookmark_row->flags & ReaderViewRow_AttachedToPrevious) != 0 ||
      (attached_highlight_row->flags &
       ReaderViewRow_AttachedToPrevious) != 0 ||
      (note_row->flags & ReaderViewRow_AttachedToPrevious) == 0 ||
      !eightvo_reader_view_text_is(note_row->primary, "Smoke note") ||
      !eightvo_reader_view_text_is(bookmark_row->secondary,
                                    expected_bookmark_secondary) ||
      !eightvo_reader_view_text_is(note_row->secondary,
                                    expected_note_secondary))
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=projection\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  app.reader_view_state.right_filter = ReaderViewRightFilter_Notes;
  eightvo_prepare_reader_view_projection(&app);
  const ReaderViewRightRow *notes_only_row =
    app.reader_view_projection.right.row_count == 1 ?
      app.reader_view_projection.right.rows : 0;
  if (!notes_only_row || notes_only_row->kind != ReaderViewRightRow_Note ||
      notes_only_row->color_key != 5002 ||
      !eightvo_reader_view_text_is(notes_only_row->primary, "Smoke note") ||
      (notes_only_row->flags & ReaderViewRow_AttachedToPrevious) != 0)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=right_note_filter_attachment\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  app.reader_view_state.right_filter = ReaderViewRightFilter_All;
  eightvo_prepare_reader_view_projection(&app);
  attached_highlight_row = 0;
  note_row = 0;
  for (UI0S32 index = 0;
       index < app.reader_view_projection.right.row_count;
       index += 1)
  {
    const ReaderViewRightRow *row =
      app.reader_view_projection.right.rows + index;
    if (row->kind == ReaderViewRightRow_Highlight)
      attached_highlight_row = row;
    else if (row->kind == ReaderViewRightRow_Note)
      note_row = row;
  }
  if (!attached_highlight_row || !note_row)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=right_attachment_restore\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  ReaderViewKey bookmark_row_key = bookmark_row->key;
  ReaderViewKey highlight_row_key = attached_highlight_row->key;
  ReaderViewKey note_row_key = note_row->key;
  const EightvoReaderViewRightSource *highlight_source =
    eightvo_reader_view_right_source(
      &app, highlight_row_key, ReaderViewRightRow_Highlight);
  const EightvoReaderViewRightSource *note_source =
    eightvo_reader_view_right_source(
      &app, note_row_key, ReaderViewRightRow_Note);
  if (!highlight_source || !note_source ||
      highlight_source->record_id != app.highlights[0].id ||
      note_source->record_id != app.highlights[0].id)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=right_source_mapping\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  if (!eightvo_reader_view_annotation_interaction_regression(
        &app, &buffer, note_row_key))
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=annotation_interactions\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  if (!eightvo_reader_view_annotation_pointer_regression(
        &app, &buffer, note_row_key))
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=annotation_pointer_interactions\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  {
    char rollback_path[EightvoPathCap] = {0};
    eightvo_copy_cstr(rollback_path, ARRAY_COUNT(rollback_path),
                       app.annotations_path);
    B32 rollback_persistence = app.persistence_enabled;
    U64 rollback_revision = app.annotation_revision;
    U64 rollback_bookmark_id = app.bookmarks[0].id;
    B32 rollback_highlight_star = app.highlights[0].starred;
    B32 rollback_note_star = app.highlights[0].note_starred;
    app.persistence_enabled = 1;
    eightvo_copy_cstr(app.annotations_path,
                       ARRAY_COUNT(app.annotations_path),
                       "?:\\eightvo_reader_view_row_star_failure.annotations");
    eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleRightRowStar,
      .key = bookmark_row_key,
      .right_row_kind = ReaderViewRightRow_Bookmark,
    });
    eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleRightRowStar,
      .key = highlight_row_key,
      .right_row_kind = ReaderViewRightRow_Highlight,
    });
    eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleRightRowStar,
      .key = note_row_key,
      .right_row_kind = ReaderViewRightRow_Note,
    });
    app.persistence_enabled = rollback_persistence;
    eightvo_copy_cstr(app.annotations_path,
                       ARRAY_COUNT(app.annotations_path), rollback_path);
    if (app.bookmark_count != 1 ||
        app.bookmarks[0].id != rollback_bookmark_id ||
        app.highlights[0].starred != rollback_highlight_star ||
        app.highlights[0].note_starred != rollback_note_star ||
        app.annotation_revision != rollback_revision)
    {
      fprintf(stderr,
              "eightvo_reader_view_smoke result=fail reason=right_star_rollback\n");
      free(pixels);
      eightvo_app_release(&app);
      return 1;
    }
  }
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_ToggleRightRowStar,
    .key = highlight_row_key,
    .right_row_kind = ReaderViewRightRow_Highlight,
  });
  B32 highlight_star_only = app.highlights[0].starred &&
                            !app.highlights[0].note_starred;
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_ToggleRightRowStar,
    .key = note_row_key,
    .right_row_kind = ReaderViewRightRow_Note,
  });
  B32 distinct_stars = highlight_star_only && app.highlights[0].starred &&
                       app.highlights[0].note_starred;
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_ToggleRightRowStar,
    .key = highlight_row_key,
    .right_row_kind = ReaderViewRightRow_Highlight,
  });
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_ToggleRightRowStar,
    .key = note_row_key,
    .right_row_kind = ReaderViewRightRow_Note,
  });
  if (!distinct_stars || app.highlights[0].starred ||
      app.highlights[0].note_starred)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=right_star_mapping\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_ActivateTocRow,
    .key = 2,
  });
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_ActivateRightRow,
    .key = note_row_key,
    .right_row_kind = ReaderViewRightRow_Note,
  });
  if (app.reader.active_spine_index != app.highlights[0].spine_index)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=right_activate_mapping\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  DocSelection unrelated_note_selection = {0};
  B32 unrelated_note_selection_found = 0;
  for (U64 cursor = 0; cursor < app.frame.visible_text.size;)
  {
    U64 next = base_unicode_utf8_next_grapheme_boundary(
      app.frame.visible_text, cursor);
    if (next <= cursor) break;
    U64 start = app.frame.view_byte_offset + cursor;
    U64 end = app.frame.view_byte_offset + next;
    if (app.frame.visible_text.str[cursor] > ' ' &&
        (end <= app.highlights[0].start_byte ||
         start >= app.highlights[0].end_byte))
    {
      unrelated_note_selection = (DocSelection){
        .spine_index = app.reader.active_spine_index,
        .text_byte_start = start,
        .text_byte_end = end,
      };
      unrelated_note_selection_found = 1;
      break;
    }
    cursor = next;
  }
  B32 unrelated_note_selection_set = unrelated_note_selection_found &&
    epub_reader_set_selection(&app.reader, unrelated_note_selection) ==
      EpubReaderResult_Ok;
  app.selection_anchor_rect = ui0_rect(377, 211, 5, 23);
  if (unrelated_note_selection_set) eightvo_prepare_selected_text(&app);
  char unrelated_note_selected_text[EightvoSelectionTextCap] = {0};
  eightvo_copy_cstr(unrelated_note_selected_text,
                     ARRAY_COUNT(unrelated_note_selected_text),
                     app.selected_text);
  UI0Rect unrelated_note_anchor = app.selection_anchor_rect;
  U32 note_edit_spine = app.reader.active_spine_index;
  U64 note_edit_byte = app.reader.view_byte_offset;
  U32 note_edit_back_count = app.reader.back_stack_count;
  U32 note_edit_forward_count = app.reader.forward_stack_count;
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_EditRightRowNote,
    .key = note_row_key,
    .right_row_kind = ReaderViewRightRow_Note,
  });
  if (app.reader_view_state.popup != ReaderViewPopup_NoteEditor ||
      !eightvo_reader_view_text_is(reader_view_note_draft(&app.reader_view_state),
                                    "Smoke note") ||
      !unrelated_note_selection_set ||
      unrelated_note_selected_text[0] == 0 ||
      !eightvo_reader_view_document_selection_is(
        &app, unrelated_note_selection, unrelated_note_selected_text,
        unrelated_note_anchor) ||
      app.reader.active_spine_index != note_edit_spine ||
      app.reader.view_byte_offset != note_edit_byte ||
      app.reader.back_stack_count != note_edit_back_count ||
      app.reader.forward_stack_count != note_edit_forward_count)
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=direct_note_edit\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  U64 note_lifecycle_revision = app.annotation_revision;
  app.reader_view_state.note_dirty = 1;
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SaveNote,
    .key = app.reader_view_state.note_selection_key,
    .value = note_lifecycle_revision + 1,
    .text = {.data = "Stale note", .size = 10},
  });
  B32 stale_save_guarded =
    app.reader_view_state.popup == ReaderViewPopup_NoteEditor &&
    app.reader_view_state.note_dirty &&
    strcmp(app.highlights[0].note, "Smoke note") == 0 &&
    eightvo_reader_view_document_selection_is(
      &app, unrelated_note_selection, unrelated_note_selected_text,
      unrelated_note_anchor);
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SaveNote,
    .key = app.reader_view_state.note_selection_key + 1,
    .value = note_lifecycle_revision,
    .text = {.data = "Wrong target", .size = 12},
  });
  B32 stale_identity_guarded =
    app.reader_view_state.popup == ReaderViewPopup_NoteEditor &&
    app.reader_view_state.note_dirty &&
    strcmp(app.highlights[0].note, "Smoke note") == 0 &&
    eightvo_reader_view_document_selection_is(
      &app, unrelated_note_selection, unrelated_note_selected_text,
      unrelated_note_anchor);
  app.annotation_revision += 1;
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SaveNote,
    .key = app.reader_view_state.note_selection_key,
    .value = app.annotation_revision,
    .text = {.data = "Rebased stale", .size = 13},
  });
  B32 source_revision_guarded =
    app.reader_view_state.popup == ReaderViewPopup_NoteEditor &&
    app.reader_view_state.note_dirty &&
    strcmp(app.highlights[0].note, "Smoke note") == 0 &&
    eightvo_reader_view_document_selection_is(
      &app, unrelated_note_selection, unrelated_note_selected_text,
      unrelated_note_anchor);
  app.annotation_revision = note_lifecycle_revision;
  char saved_annotations_path[EightvoPathCap] = {0};
  eightvo_copy_cstr(saved_annotations_path,
                     ARRAY_COUNT(saved_annotations_path),
                     app.annotations_path);
  B32 saved_persistence_enabled = app.persistence_enabled;
  app.persistence_enabled = 1;
  eightvo_copy_cstr(app.annotations_path,
                     ARRAY_COUNT(app.annotations_path),
                     "?:\\eightvo_reader_view_forced_failure.annotations");
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SaveNote,
    .key = app.reader_view_state.note_selection_key,
    .value = note_lifecycle_revision,
    .text = {.data = "Failed save", .size = 11},
  });
  B32 failed_save_rolled_back =
    app.annotation_revision == note_lifecycle_revision &&
    strcmp(app.highlights[0].note, "Smoke note") == 0 &&
    app.reader_view_state.popup == ReaderViewPopup_NoteEditor &&
    app.reader_view_state.note_dirty &&
    eightvo_reader_view_document_selection_is(
      &app, unrelated_note_selection, unrelated_note_selected_text,
      unrelated_note_anchor);
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_DeleteNote,
    .key = app.reader_view_state.note_selection_key,
    .value = note_lifecycle_revision,
  });
  B32 failed_delete_rolled_back =
    app.annotation_revision == note_lifecycle_revision &&
    strcmp(app.highlights[0].note, "Smoke note") == 0 &&
    app.reader_view_state.popup == ReaderViewPopup_NoteEditor &&
    app.reader_view_state.note_dirty &&
    eightvo_reader_view_document_selection_is(
      &app, unrelated_note_selection, unrelated_note_selected_text,
      unrelated_note_anchor);
  app.persistence_enabled = saved_persistence_enabled;
  eightvo_copy_cstr(app.annotations_path,
                     ARRAY_COUNT(app.annotations_path),
                     saved_annotations_path);
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_SaveNote,
    .key = app.reader_view_state.note_selection_key,
    .value = note_lifecycle_revision,
    .text = {.data = "Updated note", .size = 12},
  });
  B32 save_acknowledged =
    strcmp(app.highlights[0].note, "Updated note") == 0 &&
    app.reader_view_state.popup == ReaderViewPopup_None &&
    !app.reader_view_state.note_dirty &&
    app.annotation_note_selection_key == 0 &&
    eightvo_reader_view_document_selection_is(
      &app, unrelated_note_selection, unrelated_note_selected_text,
      unrelated_note_anchor) &&
    app.reader.active_spine_index == note_edit_spine &&
    app.reader.view_byte_offset == note_edit_byte &&
    app.reader.back_stack_count == note_edit_back_count &&
    app.reader.forward_stack_count == note_edit_forward_count;
  B32 restored_after_save = eightvo_save_note_at_index(
    &app, 0, (ReaderViewText){"Smoke note", 10});
  eightvo_prepare_reader_view_projection(&app);
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_EditRightRowNote,
    .key = note_row_key,
    .right_row_kind = ReaderViewRightRow_Note,
  });
  U64 delete_revision = app.annotation_revision;
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_DeleteNote,
    .key = app.reader_view_state.note_selection_key,
    .value = delete_revision,
  });
  B32 delete_acknowledged = app.highlights[0].note[0] == 0 &&
    app.reader_view_state.popup == ReaderViewPopup_None &&
    app.annotation_note_selection_key == 0 &&
    eightvo_reader_view_document_selection_is(
      &app, unrelated_note_selection, unrelated_note_selected_text,
      unrelated_note_anchor) &&
    app.reader.active_spine_index == note_edit_spine &&
    app.reader.view_byte_offset == note_edit_byte &&
    app.reader.back_stack_count == note_edit_back_count &&
    app.reader.forward_stack_count == note_edit_forward_count;
  B32 restored_after_delete = eightvo_save_note_at_index(
    &app, 0, (ReaderViewText){"Smoke note", 10});
  B32 cancel_selection_set = eightvo_reader_view_document_selection_is(
    &app, unrelated_note_selection, unrelated_note_selected_text,
    unrelated_note_anchor);
  eightvo_prepare_reader_view_projection(&app);
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_EditRightRowNote,
    .key = note_row_key,
    .right_row_kind = ReaderViewRightRow_Note,
  });
  B32 cancel_opened =
    app.reader_view_state.popup == ReaderViewPopup_NoteEditor;
  B32 shared_cancel_closed =
    reader_view_close_note_editor(&app.reader_view_state);
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_CancelNote,
    .key = app.reader_view_state.note_selection_key,
    .value = app.annotation_revision,
  });
  B32 cancel_acknowledged =
    app.reader_view_state.popup == ReaderViewPopup_None &&
    app.annotation_note_selection_key == 0 &&
    eightvo_reader_view_document_selection_is(
      &app, unrelated_note_selection, unrelated_note_selected_text,
      unrelated_note_anchor) &&
    app.reader.active_spine_index == note_edit_spine &&
    app.reader.view_byte_offset == note_edit_byte &&
    app.reader.back_stack_count == note_edit_back_count &&
    app.reader.forward_stack_count == note_edit_forward_count;
  eightvo_prepare_reader_view_projection(&app);
  B32 selection_origin_opened = reader_view_open_note_editor(
    &app.reader_view_state, &app.reader_view_projection.selection);
  B32 selection_origin_shared_closed = selection_origin_opened &&
    reader_view_close_note_editor(&app.reader_view_state);
  if (selection_origin_shared_closed)
  {
    eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
      .kind = ReaderViewAction_CancelNote,
      .key = app.reader_view_state.note_selection_key,
      .value = app.reader_view_state.note_source_revision,
    });
  }
  B32 selection_origin_released = selection_origin_shared_closed &&
    !app.reader.has_active_selection && app.selected_text[0] == 0 &&
    app.selection_anchor_rect.x == 0 && app.selection_anchor_rect.y == 0 &&
    app.selection_anchor_rect.w == 0 && app.selection_anchor_rect.h == 0 &&
    app.reader.active_spine_index == note_edit_spine &&
    app.reader.view_byte_offset == note_edit_byte &&
    app.reader.back_stack_count == note_edit_back_count &&
    app.reader.forward_stack_count == note_edit_forward_count;
  if (!stale_save_guarded || !stale_identity_guarded ||
      !source_revision_guarded ||
      !failed_save_rolled_back || !failed_delete_rolled_back ||
      !save_acknowledged || !restored_after_save ||
      !delete_acknowledged || !restored_after_delete ||
      !cancel_selection_set || !cancel_opened || !shared_cancel_closed ||
      !cancel_acknowledged || !selection_origin_opened ||
      !selection_origin_shared_closed || !selection_origin_released)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=note_lifecycle stale=%d identity=%d source_revision=%d failed_save=%d failed_delete=%d save=%d restore_save=%d delete=%d restore_delete=%d selection=%d open=%d shared_close=%d cancel=%d selection_origin=%d/%d/%d\n",
            stale_save_guarded,
            stale_identity_guarded,
            source_revision_guarded,
            failed_save_rolled_back,
            failed_delete_rolled_back,
            save_acknowledged,
            restored_after_save,
            delete_acknowledged,
            restored_after_delete,
            cancel_selection_set,
            cancel_opened,
            shared_cancel_closed,
            cancel_acknowledged,
            selection_origin_opened,
            selection_origin_shared_closed,
            selection_origin_released);
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_DeleteRightRow,
    .key = note_row_key,
    .right_row_kind = ReaderViewRightRow_Note,
  });
  if (app.highlight_count != 1 || app.highlights[0].note[0] != 0 ||
      !eightvo_save_note_at_index(&app, 0,
                                   (ReaderViewText){"Smoke note", 10}))
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=right_note_delete_mapping\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_prepare_reader_view_projection(&app);
  const ReaderViewRightRow *restored_highlight_row = 0;
  for (UI0S32 index = 0;
       index < app.reader_view_projection.right.row_count;
       index += 1)
  {
    const ReaderViewRightRow *row =
      app.reader_view_projection.right.rows + index;
    if (row->kind == ReaderViewRightRow_Highlight)
    {
      restored_highlight_row = row;
      break;
    }
  }
  if (!restored_highlight_row)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=right_highlight_restore\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  EightvoHighlight saved_highlight = app.highlights[0];
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_DeleteRightRow,
    .key = restored_highlight_row->key,
    .right_row_kind = ReaderViewRightRow_Highlight,
  });
  eightvo_prepare_reader_view_projection(&app);
  ReaderViewKey demoted_note_key = 0;
  U32 demoted_highlight_rows = 0;
  U32 demoted_note_rows = 0;
  for (UI0S32 index = 0;
       index < app.reader_view_projection.right.row_count;
       index += 1)
  {
    const ReaderViewRightRow *row =
      app.reader_view_projection.right.rows + index;
    if (row->kind == ReaderViewRightRow_Highlight)
      demoted_highlight_rows += 1;
    else if (row->kind == ReaderViewRightRow_Note)
    {
      demoted_note_rows += 1;
      demoted_note_key = row->key;
    }
  }
  DocSelection demoted_selection = {
    .spine_index = saved_highlight.spine_index,
    .text_byte_start = saved_highlight.start_byte,
    .text_byte_end = saved_highlight.end_byte,
  };
  B32 demoted_selection_set =
    epub_reader_set_selection(&app.reader, demoted_selection) ==
      EpubReaderResult_Ok;
  eightvo_prepare_reader_view_projection(&app);
  B32 demoted_selection_identity = demoted_selection_set &&
    app.reader_view_projection.selection.current_color_key == 0 &&
    (app.reader_view_projection.selection.flags &
       ReaderViewSelection_CanRemoveHighlight) == 0 &&
    (app.reader_view_projection.selection.flags &
       (ReaderViewSelection_CanEditNote |
        ReaderViewSelection_CanDeleteNote)) ==
      (ReaderViewSelection_CanEditNote |
       ReaderViewSelection_CanDeleteNote);
  epub_reader_clear_selection(&app.reader);
  if (app.highlight_count != 1 || app.bookmark_count != 1 ||
      app.highlights[0].is_highlight ||
      strcmp(app.highlights[0].note, "Smoke note") != 0 ||
      app.reader_view_projection.right.highlight_count != 0 ||
      app.reader_view_projection.right.note_count != 1 ||
      demoted_highlight_rows != 0 || demoted_note_rows != 1 ||
      demoted_note_key == 0 || !demoted_selection_identity)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=right_highlight_demotion count=%u identity=%d highlight_rows=%u note_rows=%u note_key=%llu selection=%d\n",
            app.highlight_count,
            app.highlight_count ? app.highlights[0].is_highlight : -1,
            demoted_highlight_rows,
            demoted_note_rows,
            (unsigned long long)demoted_note_key,
            demoted_selection_identity);
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }

  app.persistence_enabled = 1;
  eightvo_copy_cstr(app.export_path, ARRAY_COUNT(app.export_path), export_path);
  (void)cstr_format(app.settings_path, ARRAY_COUNT(app.settings_path),
                    "%s.settings", export_path);
  (void)cstr_format(app.annotations_path, ARRAY_COUNT(app.annotations_path),
                    "%s.annotations", export_path);
  if (!eightvo_save_annotations(&app))
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=demoted_persistence\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_load_annotations(&app);
  if (app.bookmark_count != 1 || app.highlight_count != 1 ||
      app.highlights[0].is_highlight ||
      strcmp(app.highlights[0].note, "Smoke note") != 0)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=demoted_reload\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_prepare_reader_view_projection(&app);
  ReaderViewKey reloaded_note_key = 0;
  for (UI0S32 index = 0;
       index < app.reader_view_projection.right.row_count;
       index += 1)
  {
    const ReaderViewRightRow *row =
      app.reader_view_projection.right.rows + index;
    if (row->kind == ReaderViewRightRow_Note)
      reloaded_note_key = row->key;
  }
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_DeleteRightRow,
    .key = reloaded_note_key,
    .right_row_kind = ReaderViewRightRow_Note,
  });
  eightvo_load_annotations(&app);
  if (reloaded_note_key == 0 || app.bookmark_count != 1 ||
      app.highlight_count != 0)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=note_only_final_delete_reload\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }

  EightvoAnnotationFileV1 *legacy_v1 =
    (EightvoAnnotationFileV1 *)calloc(1, sizeof(*legacy_v1));
  if (!legacy_v1)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=legacy_v1_allocate\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  legacy_v1->magic = EIGHTVO_ANNOTATION_MAGIC;
  legacy_v1->version = 1;
  legacy_v1->bookmark_count = 1;
  legacy_v1->highlight_count = 1;
  legacy_v1->path_hash = u64_hash_str8(str8_from_cstr(app.current_path));
  legacy_v1->next_record_id = app.next_record_id;
  legacy_v1->bookmarks[0] = (EightvoBookmarkV1){
    .id = app.bookmarks[0].id,
    .spine_index = app.bookmarks[0].spine_index,
    .byte_offset = app.bookmarks[0].byte_offset,
    .starred = 1,
  };
  eightvo_copy_cstr(legacy_v1->bookmarks[0].label,
                     ARRAY_COUNT(legacy_v1->bookmarks[0].label),
                     app.bookmarks[0].label);
  legacy_v1->highlights[0] = (EightvoHighlightV2){
    .id = saved_highlight.id,
    .spine_index = saved_highlight.spine_index,
    .start_byte = saved_highlight.start_byte,
    .end_byte = saved_highlight.end_byte,
    .color_index = saved_highlight.color_index,
    .starred = 1,
    .note_starred = 1,
  };
  eightvo_copy_cstr(legacy_v1->highlights[0].section,
                     ARRAY_COUNT(legacy_v1->highlights[0].section),
                     saved_highlight.section);
  eightvo_copy_cstr(legacy_v1->highlights[0].text,
                     ARRAY_COUNT(legacy_v1->highlights[0].text),
                     saved_highlight.text);
  eightvo_copy_cstr(legacy_v1->highlights[0].note,
                     ARRAY_COUNT(legacy_v1->highlights[0].note),
                     saved_highlight.note);
  B32 wrote_legacy_v1 = os_write_entire_file_atomic(
    app.annotations_path, legacy_v1, sizeof(*legacy_v1));
  free(legacy_v1);
  if (!wrote_legacy_v1)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=legacy_v1_write\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_load_annotations(&app);
  if (app.bookmark_count != 1 || app.highlight_count != 1 ||
      !app.bookmarks[0].starred ||
      strcmp(app.bookmarks[0].excerpt, "Bookmark") != 0 ||
      !app.highlights[0].is_highlight || !app.highlights[0].starred ||
      !app.highlights[0].note_starred ||
      strcmp(app.highlights[0].note, "Smoke note") != 0)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=legacy_v1_migration\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }

  EightvoAnnotationFileV2 *legacy_v2 =
    (EightvoAnnotationFileV2 *)calloc(1, sizeof(*legacy_v2));
  if (!legacy_v2)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=legacy_v2_allocate\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  legacy_v2->magic = EIGHTVO_ANNOTATION_MAGIC;
  legacy_v2->version = 2;
  legacy_v2->bookmark_count = 1;
  legacy_v2->highlight_count = 1;
  legacy_v2->path_hash = u64_hash_str8(str8_from_cstr(app.current_path));
  legacy_v2->next_record_id = app.next_record_id;
  legacy_v2->bookmarks[0] = app.bookmarks[0];
  legacy_v2->highlights[0] = (EightvoHighlightV2){
    .id = saved_highlight.id,
    .spine_index = saved_highlight.spine_index,
    .start_byte = saved_highlight.start_byte,
    .end_byte = saved_highlight.end_byte,
    .color_index = saved_highlight.color_index,
    .starred = 1,
    .note_starred = 0,
  };
  eightvo_copy_cstr(legacy_v2->highlights[0].section,
                     ARRAY_COUNT(legacy_v2->highlights[0].section),
                     saved_highlight.section);
  eightvo_copy_cstr(legacy_v2->highlights[0].text,
                     ARRAY_COUNT(legacy_v2->highlights[0].text),
                     saved_highlight.text);
  eightvo_copy_cstr(legacy_v2->highlights[0].note,
                     ARRAY_COUNT(legacy_v2->highlights[0].note),
                     saved_highlight.note);
  B32 wrote_legacy_v2 = os_write_entire_file_atomic(
    app.annotations_path, legacy_v2, sizeof(*legacy_v2));
  free(legacy_v2);
  if (!wrote_legacy_v2)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=legacy_v2_write\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_load_annotations(&app);
  if (app.bookmark_count != 1 || app.highlight_count != 1 ||
      !app.highlights[0].is_highlight ||
      !app.highlights[0].starred || app.highlights[0].note_starred ||
      strcmp(app.highlights[0].note, "Smoke note") != 0)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=legacy_v2_migration\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_prepare_reader_view_projection(&app);
  ReaderViewKey migrated_highlight_key = 0;
  for (UI0S32 index = 0;
       index < app.reader_view_projection.right.row_count;
       index += 1)
  {
    const ReaderViewRightRow *row =
      app.reader_view_projection.right.rows + index;
    if (row->kind == ReaderViewRightRow_Highlight)
      migrated_highlight_key = row->key;
  }
  eightvo_apply_reader_view_action(&app, &(ReaderViewAction){
    .kind = ReaderViewAction_DeleteRightRow,
    .key = migrated_highlight_key,
    .right_row_kind = ReaderViewRightRow_Highlight,
  });
  eightvo_load_annotations(&app);
  if (migrated_highlight_key == 0 || app.bookmark_count != 1 ||
      app.highlight_count != 1 || app.highlights[0].is_highlight ||
      strcmp(app.highlights[0].note, "Smoke note") != 0)
  {
    fprintf(stderr,
            "eightvo_reader_view_smoke result=fail reason=legacy_demotion_reload\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  app.highlights[0] = saved_highlight;
  app.highlight_count = 1;
  app.annotation_revision += 1;
  if (!eightvo_save_settings(&app) || !eightvo_save_annotations(&app) ||
      !eightvo_export_annotations(&app))
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=persistence\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_load_annotations(&app);
  if (app.bookmark_count != 1 || app.highlight_count != 1 ||
      !app.bookmarks[0].excerpt[0] || !app.highlights[0].is_highlight ||
      strcmp(app.highlights[0].note, "Smoke note") != 0)
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=reload\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }

  EightvoApp failed_app = {0};
  char missing_path[EightvoPathCap] = {0};
  (void)cstr_format(missing_path, ARRAY_COUNT(missing_path),
                    "%s.missing.epub", export_path);
  (void)DeleteFileA(missing_path);
  if (!eightvo_app_init(&failed_app, Width, Height, 1, 0) ||
      eightvo_open_path(&failed_app, missing_path))
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=error_setup\n");
    eightvo_app_release(&failed_app);
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_render_to_buffer(&failed_app, &buffer);
  if (!eightvo_library_active(&failed_app) ||
      failed_app.reader_view_ready || failed_app.host_control_count == 0 ||
      strstr(failed_app.status, "Open failed") == 0)
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=error_state\n");
    eightvo_app_release(&failed_app);
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  eightvo_app_release(&failed_app);

  app.width = 680;
  app.height = 620;
  eightvo_render_to_buffer(&app, &buffer);
  if (!app.reader_view_ready ||
      app.reader_view_layout.toolbar_density != ReaderViewToolbar_Compact ||
      app.reader_view_layout.mode != ReaderViewLayout_Overlay ||
      app.reader_view_layout.shared_toolbar_rect.x != 202 ||
      app.reader_view_layout.shared_toolbar_rect.w != 420 ||
      app.reader_view_layout.host_toolbar_trailing_rect.x != 630 ||
      app.reader_view_layout.host_toolbar_trailing_rect.w !=
        EightvoHostToolbarSlotWidth)
  {
    fprintf(stderr, "eightvo_reader_view_smoke result=fail reason=responsive\n");
    free(pixels);
    eightvo_app_release(&app);
    return 1;
  }
  U64 hash = eightvo_reader_view_contract_hash(&app.reader_view_frame);
  fprintf(stdout,
          "eightvo_reader_view_smoke result=pass api=%d settings=4 toc=%d find=%u bookmarks=%u highlights=%u responsive=fixed_compact focus=reference13 panel_focus=toc_find_annotations_progress_boundary keyboard_routing=focused_edit_or_activate horizontal_routing=text_progress_or_page find_shortcut=focused_input navigation_panels=space_toc_find gutters=boundary_roundtrip gutter_input=keyboard_pointer carets=frozen18x32 toc_identity=noncontiguous find_execution=commit_only find_clear=immediate find_metrics=bounded_values find_match=measured note_metrics=bounded_values_18px note_raster=text_engine_editable_row_caret annotations=reference_metadata bookmark_star=projected_remove_once annotations_interaction=close_filter_edit_menu annotations_pointer=open_filter_escape_select_row_star_menu_note_lifecycle_close note_lifecycle=acknowledged annotation_note_selection=preserved_unrelated selection_note_selection=released_owned annotation_identity=v3_migrate_demote_restart note_persistence=atomic_rollback_open bookmark_persistence=rollback star_persistence=rollback hash=%016llx export=%s\n",
          READERVIEW0_API_VERSION,
          app.reader_view_projection.toc.row_count,
          app.reader.search_match_count,
          app.bookmark_count,
          app.highlight_count,
          (unsigned long long)hash,
          export_path);
  free(pixels);
  eightvo_app_release(&app);
  return 0;
}

FUNCTION int
eightvo_run_accessibility_smoke(const char *path)
{
  enum { Width = 1100, Height = 760 };
  static const wchar_t *ClassName = L"EightvoAccessibilitySmokeWindow";
  EightvoWin32 win32 = {0};
  U32 *pixels = (U32 *)calloc((size_t)Width * Height, sizeof(U32));
  if (!pixels || !eightvo_app_init(&win32.app, Width, Height, 1, 0) ||
      !eightvo_open_path(&win32.app, path))
  {
    fprintf(stderr, "eightvo_accessibility_smoke result=fail reason=open\n");
    free(pixels);
    eightvo_app_release(&win32.app);
    return 1;
  }
  RenderBuffer buffer = {0};
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  eightvo_render_to_buffer(&win32.app, &buffer);

  HINSTANCE instance = GetModuleHandleW(0);
  WNDCLASSW window_class = {0};
  window_class.lpfnWndProc = eightvo_win32_proc;
  window_class.hInstance = instance;
  window_class.lpszClassName = ClassName;
  if (!RegisterClassW(&window_class) && GetLastError() != ERROR_CLASS_ALREADY_EXISTS)
  {
    fprintf(stderr, "eightvo_accessibility_smoke result=fail reason=register\n");
    free(pixels);
    eightvo_app_release(&win32.app);
    return 1;
  }
  win32.window = CreateWindowExW(0, ClassName, L"8vo accessibility smoke",
                                  WS_OVERLAPPEDWINDOW,
                                  0, 0, Width, Height,
                                  0, 0, instance, &win32);
  if (!win32.window)
  {
    fprintf(stderr, "eightvo_accessibility_smoke result=fail reason=window\n");
    (void)UnregisterClassW(ClassName, instance);
    free(pixels);
    eightvo_app_release(&win32.app);
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
  UI0S32 find_index = -1;
  UI0S32 fullscreen_index = -1;
  UI0S32 previous_index = -1;
  for (UI0S32 index = 0;
       index < win32.app.reader_view_frame.semantic_node_count;
       index += 1)
  {
    if (eightvo_reader_view_text_is(
          win32.app.reader_view_frame.semantic_nodes[index].name,
          "Contents"))
    {
      contents_index = index;
    }
    if (win32.app.reader_view_frame.semantic_nodes[index].control ==
        ReaderViewSemanticControl_PreviousPage)
      previous_index = index;
    if (win32.app.reader_view_frame.semantic_nodes[index].control ==
        ReaderViewSemanticControl_Find)
      find_index = index;
    if (win32.app.reader_view_frame.semantic_nodes[index].control ==
        ReaderViewSemanticControl_Fullscreen)
      fullscreen_index = index;
  }

  VARIANT contents_child;
  VariantInit(&contents_child);
  contents_child.vt = VT_I4;
  contents_child.lVal = eightvo_accessibility_shared_child_id(
    win32.app.accessibility, contents_index);
  long contents_child_id = contents_child.lVal;
  long previous_child_id = 0;
  long progress_child_id = 0;
  BSTR root_name = 0;
  BSTR contents_name = 0;
  BSTR exit_name = 0;
  VARIANT root_child;
  VariantInit(&root_child);
  root_child.vt = VT_I4;
  root_child.lVal = CHILDID_SELF;
  VARIANT role;
  VariantInit(&role);
  VARIANT exit_role;
  VariantInit(&exit_role);
  VARIANT exit_state;
  VariantInit(&exit_state);
  VARIANT exit_focus;
  VariantInit(&exit_focus);
  VARIANT previous_focus;
  VariantInit(&previous_focus);
  long left = 0;
  long top = 0;
  long width = 0;
  long height = 0;
  U32 accessibility_checkpoint = 0;

  B32 valid = SUCCEEDED(access_result) && accessible && contents_index >= 0 &&
    find_index >= 0 && fullscreen_index >= 0 && previous_index >= 0 &&
    contents_child.lVal > 0 &&
    SUCCEEDED(accessible->lpVtbl->get_accChildCount(accessible, &child_count)) &&
    child_count == win32.app.reader_view_frame.semantic_node_count +
                   (long)win32.app.host_control_count &&
    SUCCEEDED(accessible->lpVtbl->get_accName(accessible, root_child, &root_name)) &&
    root_name && wcscmp(root_name, L"8vo reader") == 0 &&
    SUCCEEDED(accessible->lpVtbl->get_accName(accessible, contents_child,
                                              &contents_name)) &&
    contents_name && wcscmp(contents_name, L"Contents") == 0 &&
    SUCCEEDED(accessible->lpVtbl->get_accRole(accessible, contents_child, &role)) &&
    role.vt == VT_I4 &&
    (role.lVal == ROLE_SYSTEM_PUSHBUTTON || role.lVal == ROLE_SYSTEM_CHECKBUTTON) &&
    SUCCEEDED(accessible->lpVtbl->accLocation(accessible,
                                              &left, &top, &width, &height,
                                              contents_child)) &&
    width > 0 && height > 0;
  if (valid) accessibility_checkpoint = 1;
  if (valid)
  {
    VARIANT previous_child;
    VariantInit(&previous_child);
    previous_child.vt = VT_I4;
    previous_child.lVal = eightvo_accessibility_shared_child_id(
      win32.app.accessibility, previous_index);
    previous_child_id = previous_child.lVal;
    const ReaderViewSemanticNode *previous =
      win32.app.reader_view_frame.semantic_nodes + previous_index;
    U32 before_spine = win32.app.reader.active_spine_index;
    U64 before_byte = win32.app.reader.view_byte_offset;
    valid = (previous->flags & ReaderViewSemantic_Focusable) != 0 &&
      (previous->flags & ReaderViewSemantic_Enabled) == 0 &&
      SUCCEEDED(accessible->lpVtbl->accSelect(accessible,
                                               SELFLAG_TAKEFOCUS,
                                               previous_child));
    if (valid)
    {
      eightvo_render_to_buffer(&win32.app, &buffer);
      valid = SUCCEEDED(accessible->lpVtbl->get_accFocus(accessible,
                                                         &previous_focus)) &&
              previous_focus.vt == VT_I4 &&
              previous_focus.lVal == previous_child.lVal &&
              FAILED(accessible->lpVtbl->accDoDefaultAction(accessible,
                                                              previous_child)) &&
              win32.app.reader.active_spine_index == before_spine &&
              win32.app.reader.view_byte_offset == before_byte;
    }
  }
  if (valid) accessibility_checkpoint = 2;
  if (valid)
  {
    valid = SUCCEEDED(accessible->lpVtbl->accSelect(accessible,
                                                    SELFLAG_TAKEFOCUS,
                                                    contents_child)) &&
            SUCCEEDED(accessible->lpVtbl->accDoDefaultAction(accessible,
                                                             contents_child));
  }
  if (valid) accessibility_checkpoint = 3;
  if (valid)
  {
    eightvo_render_to_buffer(&win32.app, &buffer);
    valid = win32.app.reader_view_state.left_panel == ReaderViewLeftPanel_Contents;
    if (valid)
      eightvo_render_to_buffer(&win32.app, &buffer);
  }
  if (valid) accessibility_checkpoint = 4;

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
    slider_child.lVal = eightvo_accessibility_shared_child_id(
      win32.app.accessibility, slider_index);
    progress_child_id = slider_child.lVal;
    U64 before_offset = win32.app.reader.view_byte_offset;
    valid = SUCCEEDED(accessible->lpVtbl->accDoDefaultAction(accessible,
                                                             slider_child));
    if (valid)
    {
      eightvo_render_to_buffer(&win32.app, &buffer);
      valid = eightvo_reader_view_focus_is(&win32.app,
                                            ReaderViewSemantic_Slider);
    }
    if (valid) accessibility_checkpoint = 5;
    if (valid)
    {
      win32.app.input.move_delta = 10;
      eightvo_render_to_buffer(&win32.app, &buffer);
      valid = eightvo_reader_view_has_action(&win32.app.reader_view_frame,
                                              ReaderViewAction_SeekLocation);
      eightvo_apply_reader_view_actions(&win32.app);
      valid = valid && win32.app.reader.view_byte_offset != before_offset;
    }
    if (valid) accessibility_checkpoint = 6;
  }
  else
  {
    valid = 0;
  }

  if (valid)
  {
    valid = !eightvo_reader_view_has_semantic(&win32.app.reader_view_frame,
                                                "Focus");
  }
  if (valid) accessibility_checkpoint = 7;
  if (valid)
  {
    eightvo_apply_reader_view_action(&win32.app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleFullscreen,
    });
    valid = win32.app.fullscreen.active;
    eightvo_apply_reader_view_action(&win32.app, &(ReaderViewAction){
      .kind = ReaderViewAction_ToggleFullscreen,
    });
    valid = valid && !win32.app.fullscreen.active;
  }
  if (valid) accessibility_checkpoint = 8;

  long exit_child_id = 0;
  if (valid)
  {
    long current_child_count = 0;
    find_index = -1;
    fullscreen_index = -1;
    for (UI0S32 index = 0;
         index < win32.app.reader_view_frame.semantic_node_count;
         index += 1)
    {
      ReaderViewSemanticControl control =
        win32.app.reader_view_frame.semantic_nodes[index].control;
      if (control == ReaderViewSemanticControl_Find) find_index = index;
      else if (control == ReaderViewSemanticControl_Fullscreen)
        fullscreen_index = index;
    }
    VARIANT find_child;
    VariantInit(&find_child);
    find_child.vt = VT_I4;
    find_child.lVal = eightvo_accessibility_shared_child_id(
      win32.app.accessibility, find_index);
    VARIANT fullscreen_child;
    VariantInit(&fullscreen_child);
    fullscreen_child.vt = VT_I4;
    fullscreen_child.lVal = eightvo_accessibility_shared_child_id(
      win32.app.accessibility, fullscreen_index);
    VARIANT exit_child;
    VariantInit(&exit_child);
    exit_child.vt = VT_I4;
    exit_child.lVal = eightvo_accessibility_host_child_id(
      win32.app.accessibility, 0);
    exit_child_id = exit_child.lVal;
    VARIANT next_from_find;
    VariantInit(&next_from_find);
    VARIANT next_from_exit;
    VariantInit(&next_from_exit);
    VARIANT previous_from_exit;
    VariantInit(&previous_from_exit);
    VARIANT previous_from_fullscreen;
    VariantInit(&previous_from_fullscreen);
    valid = find_index >= 0 && fullscreen_index >= 0 &&
      win32.app.host_control_count == 1 &&
      SUCCEEDED(accessible->lpVtbl->get_accChildCount(accessible,
                                                       &current_child_count)) &&
      current_child_count == win32.app.reader_view_frame.semantic_node_count + 1 &&
      SUCCEEDED(accessible->lpVtbl->get_accName(accessible,
                                                 exit_child,
                                                 &exit_name)) &&
      exit_name && wcscmp(exit_name, L"Close Book") == 0 &&
      SUCCEEDED(accessible->lpVtbl->get_accRole(accessible,
                                                 exit_child,
                                                 &exit_role)) &&
      exit_role.vt == VT_I4 && exit_role.lVal == ROLE_SYSTEM_PUSHBUTTON &&
      SUCCEEDED(accessible->lpVtbl->get_accState(accessible,
                                                  exit_child,
                                                  &exit_state)) &&
      exit_state.vt == VT_I4 &&
      (exit_state.lVal & STATE_SYSTEM_FOCUSABLE) != 0 &&
      SUCCEEDED(accessible->lpVtbl->accNavigate(
        accessible, NAVDIR_NEXT, find_child, &next_from_find)) &&
      next_from_find.vt == VT_I4 &&
      next_from_find.lVal == exit_child.lVal &&
      SUCCEEDED(accessible->lpVtbl->accNavigate(
        accessible, NAVDIR_PREVIOUS, exit_child, &previous_from_exit)) &&
      previous_from_exit.vt == VT_I4 &&
      previous_from_exit.lVal == find_child.lVal &&
      SUCCEEDED(accessible->lpVtbl->accNavigate(
        accessible, NAVDIR_NEXT, exit_child, &next_from_exit)) &&
      next_from_exit.vt == VT_I4 &&
      next_from_exit.lVal == fullscreen_child.lVal &&
      SUCCEEDED(accessible->lpVtbl->accNavigate(
        accessible, NAVDIR_PREVIOUS, fullscreen_child,
        &previous_from_fullscreen)) &&
      previous_from_fullscreen.vt == VT_I4 &&
      previous_from_fullscreen.lVal == exit_child.lVal &&
      SUCCEEDED(accessible->lpVtbl->accSelect(accessible,
                                               SELFLAG_TAKEFOCUS,
                                               exit_child));
    if (valid)
    {
      eightvo_render_to_buffer(&win32.app, &buffer);
      valid = SUCCEEDED(accessible->lpVtbl->get_accFocus(accessible,
                                                         &exit_focus)) &&
              exit_focus.vt == VT_I4 &&
              exit_focus.lVal == exit_child.lVal &&
              win32.app.host_focus_control == EightvoHostControl_ExitReader;
    }
    if (valid)
    {
      valid = SUCCEEDED(accessible->lpVtbl->accDoDefaultAction(accessible,
                                                               contents_child));
      eightvo_render_to_buffer(&win32.app, &buffer);
      const ReaderViewSemanticNode *shared_contents =
        eightvo_reader_view_semantic_control(
          &win32.app.reader_view_frame, ReaderViewSemanticControl_Contents);
      valid = valid &&
              shared_contents &&
              win32.app.host_focus_control == EightvoHostControl_None &&
              win32.app.reader_view_state.focus_id == shared_contents->id &&
              SUCCEEDED(accessible->lpVtbl->accSelect(accessible,
                                                       SELFLAG_TAKEFOCUS,
                                                       exit_child));
    }
    if (valid)
    {
      eightvo_render_to_buffer(&win32.app, &buffer);
      /* Closing Contents republishes a shorter shared tree. Recompute the
         logical native child ID while the host identity/order remains stable. */
      exit_child.lVal = eightvo_accessibility_host_child_id(
        win32.app.accessibility, 0);
      exit_child_id = exit_child.lVal;
      VariantClear(&exit_focus);
      valid = SUCCEEDED(accessible->lpVtbl->get_accFocus(accessible,
                                                         &exit_focus)) &&
              exit_focus.vt == VT_I4 &&
              exit_focus.lVal == exit_child.lVal &&
              win32.app.host_focus_control == EightvoHostControl_ExitReader &&
              SUCCEEDED(accessible->lpVtbl->accDoDefaultAction(accessible,
                                                                exit_child)) &&
              eightvo_library_active(&win32.app);
    }
    VariantClear(&next_from_find);
    VariantClear(&next_from_exit);
    VariantClear(&previous_from_exit);
    VariantClear(&previous_from_fullscreen);
  }

  long role_value = role.vt == VT_I4 ? role.lVal : 0;

  if (root_name) SysFreeString(root_name);
  if (contents_name) SysFreeString(contents_name);
  if (exit_name) SysFreeString(exit_name);
  VariantClear(&role);
  VariantClear(&exit_role);
  VariantClear(&exit_state);
  VariantClear(&exit_focus);
  VariantClear(&previous_focus);
  if (accessible) (void)accessible->lpVtbl->Release(accessible);
  (void)DestroyWindow(win32.window);
  (void)UnregisterClassW(ClassName, instance);

  if (!valid)
  {
    fprintf(stderr,
            "eightvo_accessibility_smoke result=fail reason=contract checkpoint=%u hr=%08lx nodes=%ld contents=%d previous=%d slider=%d exit=%ld role=%ld rect=%ldx%ld\n",
            accessibility_checkpoint,
            (unsigned long)access_result,
            child_count,
            contents_index,
            previous_index,
            slider_index,
            exit_child_id,
            role_value,
            width,
            height);
    free(pixels);
    eightvo_app_release(&win32.app);
    return 1;
  }

  fprintf(stdout,
          "eightvo_accessibility_smoke result=pass adapter=msaa nodes=%ld contents_child=%ld previous_child=%ld progress_child=%ld close_child=%ld role=%ld order=find_close_fullscreen focus=shared_disabled_host action=disabled_guard_shared_host_close_to_library progress=keyboard fullscreen=native distraction=dormant\n",
          child_count,
          contents_child_id,
          previous_child_id,
          progress_child_id,
          exit_child_id,
          role_value);
  free(pixels);
  eightvo_app_release(&win32.app);
  return 0;
}

FUNCTION int
eightvo_run_page_turn_regression_smoke(const char *path,
                                        const char *output_prefix)
{
  enum { Width = 1100, Height = 760, ScanPageCount = 64,
         PerformancePairCount = 16, HeldWidth = 1917, HeldHeight = 1137 };
  EightvoApp app = {0};
  U64 pixel_count = (U64)Width * Height;
  U64 held_pixel_count = (U64)HeldWidth * HeldHeight;
  U32 *pixels = (U32 *)calloc((size_t)pixel_count, sizeof(U32));
  U32 *held_pixels =
    (U32 *)calloc((size_t)held_pixel_count, sizeof(U32));
  RenderBuffer buffer = {0};
  RenderBuffer held_buffer = {0};
  U32 forward_count = 0;
  U32 backward_count = 0;
  SourceReaderPageRange forward_path[ScanPageCount + 1] = {0};
  U32 reverse_exact_match_count = 0;
  B32 forward_endpoint_valid = 0;
  B32 returned_to_forward_start = 0;
  U32 failure_step = 0;
  B32 failure_backward = 0;
  U64 move_total_ticks = 0;
  U64 move_max_ticks = 0;
  U64 render_total_ticks = 0;
  U64 render_max_ticks = 0;
  U32 adjacent_warm_page_count = 0;
  U32 adjacent_warm_step_count = 0;
  U32 performance_pair_count = 0;
  U32 pixel_exact_count = 0;
  U32 repeat_move_count = 0;
  U32 repeat_forward_move_count = 0;
  U32 repeat_backward_move_count = 0;
  U32 held_cache_hit_count = 0;
  U32 held_pixel_exact_count = 0;
  U32 held_render_gate_block_count = 0;
  U32 held_native_repeat_coalesced_count = 0;
  U64 held_move_total_ticks = 0;
  U64 held_move_max_ticks = 0;
  U64 held_render_total_ticks = 0;
  U64 held_render_max_ticks = 0;
  U64 held_action_total_ticks = 0;
  U64 held_action_max_ticks = 0;
  U64 warmed_render_total_ticks = 0;
  U64 warmed_render_max_ticks = 0;
  U64 cold_render_total_ticks = 0;
  U64 prepared_move_total_ticks = 0;
  U64 prepared_move_max_ticks = 0;
  U32 warmed_cache_hit_count = 0;
  U32 canonical_nonempty_frame_count = 0;
  U32 zero_page_or_frame_count = 0;
  U32 orphan_text_page_count = 0;
  U32 invalid_word_start_page_count = 0;
  U32 gotm_text_frame_count = 0;
  U64 gotm_minimum_text_bytes = UINT64_MAX;
  U32 gotm_minimum_text_rows = UINT32_MAX;
  B32 deferred_reversal_keyup_passed = 0;
  String8 boundary_ascii = str8_from_cstr("Moon's lord");
  String8 boundary_utf8 = str8_from_cstr("Moon\xe2\x80\x99s lord");
  String8 boundary_hyphen = str8_from_cstr("Moon-s lord");
  B32 boundary_oracle_self_test =
    !eightvo_gotm_page_start_is_word_boundary(boundary_ascii, 4) &&
    !eightvo_gotm_page_start_is_word_boundary(boundary_ascii, 5) &&
    eightvo_gotm_page_start_is_word_boundary(boundary_ascii, 7) &&
    !eightvo_gotm_page_start_is_word_boundary(boundary_utf8, 4) &&
    !eightvo_gotm_page_start_is_word_boundary(boundary_utf8, 5) &&
    !eightvo_gotm_page_start_is_word_boundary(boundary_utf8, 7) &&
    eightvo_gotm_page_start_is_word_boundary(boundary_utf8, 9) &&
    !eightvo_gotm_page_start_is_word_boundary(boundary_hyphen, 4) &&
    !eightvo_gotm_page_start_is_word_boundary(boundary_hyphen, 5) &&
    eightvo_gotm_page_start_is_word_boundary(boundary_hyphen, 7);
  RenderTextCacheStats cache_stats = {0};
  U32 first_zero_page_count_step = ScanPageCount;
  SourceReaderPageRange first_zero_before_page = {0};
  SourceReaderPageRange first_zero_after_page = {0};
  EpubReaderNavigationStats first_zero_nav_before = {0};
  EpubReaderNavigationStats first_zero_nav_after = {0};
  U32 first_zero_prepared_ring_before = 0;
  U32 first_zero_prepared_ring_after = 0;
  B32 first_zero_warm_pending_before = 0;
  B32 first_zero_warm_ready_before = 0;
  SourceReaderPageRange failure_before_page = {0};
  SourceReaderPageRange failure_after_page = {0};
  EpubReaderNavigationStats failure_nav_before = {0};
  EpubReaderNavigationStats failure_nav_after = {0};
  EpubReaderResult failure_move_result = EpubReaderResult_Ok;
  U32 checkpoint = 0;
  int result = 1;

  checkpoint = 1;
  if (!pixels || !held_pixels || !path || !path[0] ||
      !output_prefix || !output_prefix[0] ||
      !eightvo_app_init(&app, Width, Height, 1, 0) ||
      !eightvo_open_path(&app, path))
    goto cleanup;
  render_buffer_init(&buffer, pixels, Width, Height, Width);
  render_buffer_init(&held_buffer, held_pixels,
                     HeldWidth, HeldHeight, HeldWidth);
  forward_path[0] = app.reader.current_page;

  checkpoint = 10;
  for (U32 step = 0; step < ScanPageCount; step += 1)
  {
    U64 render_start = os_time_ticks();
    eightvo_render_to_buffer(&app, &buffer);
    U64 render_ticks = os_time_ticks() - render_start;
    render_total_ticks += render_ticks;
    render_max_ticks = MAX(render_max_ticks, render_ticks);
    B32 orphan_text = 0;
    B32 invalid_word_start = 0;
    U64 text_bytes = 0;
    U32 text_rows = 0;
    if (app.reader.current_page.spine_page_count == 0)
      zero_page_or_frame_count += 1;
    B32 canonical_nonempty =
      eightvo_gotm_navigation_frame_is_canonical_nonempty(
        &app, &orphan_text, &invalid_word_start, &text_bytes, &text_rows);
    if (app.frame.image_count == 0 &&
        app.reader.spine_text.size >= EightvoGotmMinimumProseSpineBytes)
    {
      gotm_text_frame_count += 1;
      gotm_minimum_text_bytes = MIN(gotm_minimum_text_bytes, text_bytes);
      gotm_minimum_text_rows = MIN(gotm_minimum_text_rows, text_rows);
    }
    if (!canonical_nonempty)
    {
      if (orphan_text) orphan_text_page_count += 1;
      if (invalid_word_start) invalid_word_start_page_count += 1;
      failure_step = step;
      goto cleanup;
    }
    canonical_nonempty_frame_count += 1;
    if (!app.reader_view_ready || !app.reader_view_layout.toolbar_visible ||
        app.reader_view_frame.error_flags != ReaderViewFrameError_None ||
        app.draw_commands.overflow_count != 0)
    {
      failure_step = step;
      goto cleanup;
    }
    SourceReaderPageRange move_before_page = app.reader.current_page;
    EpubReaderNavigationStats move_nav_before = app.reader.navigation_stats;
    U32 move_prepared_ring_before = app.reader.prepared_window_ring.count;
    B32 move_warm_pending_before = app.adjacent_warm_pending;
    B32 move_warm_ready_before = app.adjacent_warm_frame_ready;
    U64 move_start = os_time_ticks();
    EpubReaderResult move = eightvo_move_page(&app, 1);
    U64 move_ticks = os_time_ticks() - move_start;
    SourceReaderPageRange move_after_page = app.reader.current_page;
    EpubReaderNavigationStats move_nav_after = app.reader.navigation_stats;
    if (first_zero_page_count_step == ScanPageCount &&
        move_before_page.spine_page_count > 0 &&
        move_after_page.spine_page_count == 0)
    {
      first_zero_page_count_step = step;
      first_zero_before_page = move_before_page;
      first_zero_after_page = move_after_page;
      first_zero_nav_before = move_nav_before;
      first_zero_nav_after = move_nav_after;
      first_zero_prepared_ring_before = move_prepared_ring_before;
      first_zero_prepared_ring_after = app.reader.prepared_window_ring.count;
      first_zero_warm_pending_before = move_warm_pending_before;
      first_zero_warm_ready_before = move_warm_ready_before;
    }
    if (move != EpubReaderResult_Ok)
    {
      failure_step = step;
      failure_before_page = move_before_page;
      failure_after_page = move_after_page;
      failure_nav_before = move_nav_before;
      failure_nav_after = move_nav_after;
      failure_move_result = move;
      goto cleanup;
    }
    move_total_ticks += move_ticks;
    move_max_ticks = MAX(move_max_ticks, move_ticks);
    forward_count += 1;
    forward_path[forward_count] = move_after_page;
  }
  if (forward_count != ScanPageCount) goto cleanup;
  eightvo_render_to_buffer(&app, &buffer);
  forward_endpoint_valid =
    eightvo_gotm_navigation_frame_is_canonical_nonempty(
      &app, 0, 0, 0, 0) &&
    app.reader_view_ready && app.reader_view_layout.toolbar_visible &&
    app.reader_view_frame.error_flags == ReaderViewFrameError_None &&
    app.draw_commands.overflow_count == 0 &&
    eightvo_canonical_page_identity_equal(
      eightvo_canonical_page_identity(app.reader.current_page),
      eightvo_canonical_page_identity(forward_path[ScanPageCount]));
  if (!forward_endpoint_valid) goto cleanup;

  checkpoint = 20;
  for (U32 step = 0; step < forward_count; step += 1)
  {
    SourceReaderPageRange move_before_page = app.reader.current_page;
    EpubReaderNavigationStats move_nav_before = app.reader.navigation_stats;
    U64 move_start = os_time_ticks();
    EpubReaderResult move = eightvo_move_page(&app, -1);
    U64 move_ticks = os_time_ticks() - move_start;
    SourceReaderPageRange move_after_page = app.reader.current_page;
    EpubReaderNavigationStats move_nav_after = app.reader.navigation_stats;
    if (move == EpubReaderResult_Boundary) break;
    if (move != EpubReaderResult_Ok)
    {
      failure_step = step;
      failure_backward = 1;
      failure_before_page = move_before_page;
      failure_after_page = move_after_page;
      failure_nav_before = move_nav_before;
      failure_nav_after = move_nav_after;
      failure_move_result = move;
      goto cleanup;
    }
    SourceReaderPageRange expected_reverse_page =
      forward_path[forward_count - step - 1];
    if (!eightvo_canonical_page_identity_equal(
          eightvo_canonical_page_identity(move_after_page),
          eightvo_canonical_page_identity(expected_reverse_page)))
    {
      fprintf(stderr,
              "eightvo_page_turn_reverse_mismatch step=%u expected=%u:%llu-%llu:%llu/%llu actual=%u:%llu-%llu:%llu/%llu\n",
              step,
              expected_reverse_page.spine_index,
              (unsigned long long)expected_reverse_page.first_byte,
              (unsigned long long)expected_reverse_page.one_past_last_byte,
              (unsigned long long)expected_reverse_page.spine_page_index,
              (unsigned long long)expected_reverse_page.spine_page_count,
              move_after_page.spine_index,
              (unsigned long long)move_after_page.first_byte,
              (unsigned long long)move_after_page.one_past_last_byte,
              (unsigned long long)move_after_page.spine_page_index,
              (unsigned long long)move_after_page.spine_page_count);
      failure_step = step;
      failure_backward = 1;
      failure_before_page = move_before_page;
      failure_after_page = move_after_page;
      failure_nav_before = move_nav_before;
      failure_nav_after = move_nav_after;
      failure_move_result = move;
      goto cleanup;
    }
    reverse_exact_match_count += 1;
    move_total_ticks += move_ticks;
    move_max_ticks = MAX(move_max_ticks, move_ticks);
    backward_count += 1;
    U64 render_start = os_time_ticks();
    eightvo_render_to_buffer(&app, &buffer);
    U64 render_ticks = os_time_ticks() - render_start;
    render_total_ticks += render_ticks;
    render_max_ticks = MAX(render_max_ticks, render_ticks);
    B32 orphan_text = 0;
    B32 invalid_word_start = 0;
    U64 text_bytes = 0;
    U32 text_rows = 0;
    if (app.reader.current_page.spine_page_count == 0)
      zero_page_or_frame_count += 1;
    B32 canonical_nonempty =
      eightvo_gotm_navigation_frame_is_canonical_nonempty(
        &app, &orphan_text, &invalid_word_start, &text_bytes, &text_rows);
    if (app.frame.image_count == 0 &&
        app.reader.spine_text.size >= EightvoGotmMinimumProseSpineBytes)
    {
      gotm_text_frame_count += 1;
      gotm_minimum_text_bytes = MIN(gotm_minimum_text_bytes, text_bytes);
      gotm_minimum_text_rows = MIN(gotm_minimum_text_rows, text_rows);
    }
    if (!canonical_nonempty)
    {
      if (orphan_text) orphan_text_page_count += 1;
      if (invalid_word_start) invalid_word_start_page_count += 1;
      failure_step = step;
      failure_backward = 1;
      goto cleanup;
    }
    canonical_nonempty_frame_count += 1;
    if (!app.reader_view_ready || !app.reader_view_layout.toolbar_visible ||
        app.reader_view_frame.error_flags != ReaderViewFrameError_None ||
        app.draw_commands.overflow_count != 0)
    {
      failure_step = step;
      failure_backward = 1;
      goto cleanup;
    }
  }
  if (backward_count != ScanPageCount) goto cleanup;
  returned_to_forward_start = eightvo_canonical_page_identity_equal(
    eightvo_canonical_page_identity(app.reader.current_page),
    eightvo_canonical_page_identity(forward_path[0]));
  if (!returned_to_forward_start ||
      reverse_exact_match_count != ScanPageCount)
    goto cleanup;

  checkpoint = 30;
  if (!epub_reader_rebuild_search(&app.reader,
                                  str8_from_cstr("1161st Year")) ||
      app.reader.search_match_count == 0 ||
      eightvo_navigate_to_search_match(
        &app, 0, &(EpubReaderSearchNavigationResult){0}) != EpubReaderResult_Ok)
    goto cleanup;
  reader_view_state_init(&app.reader_view_state);
  app.host_focus_control = EightvoHostControl_None;
  app.width = HeldWidth;
  app.height = HeldHeight;
  eightvo_cancel_adjacent_warm(&app);
  eightvo_invalidate_adjacent_page(&app);
  eightvo_render_to_buffer(&app, &held_buffer);
  if (!app.reader_view_ready || !app.reader_view_layout.toolbar_visible ||
      app.reader_view_frame.error_flags != ReaderViewFrameError_None)
    goto cleanup;
  checkpoint = 31;
  SourceReaderPageRange deferred_reversal_origin = app.reader.current_page;
  U64 deferred_emitted_before = app.page_action_emitted_count;
  U64 deferred_presented_before = app.page_action_presented_count;
  U32 deferred_overlap_before = app.page_action_overlap_count;
  if (eightvo_move_page(&app, 1) != EpubReaderResult_Ok) goto cleanup;
  eightvo_page_action_note_emitted(&app);
  eightvo_begin_page_repeat(&app, VK_RIGHT, 1);
  eightvo_page_action_defer(&app, VK_LEFT, -1, 1);
  eightvo_page_action_release_key(&app, VK_LEFT);
  if (app.page_repeat_active || !app.page_action_waiting_for_present ||
      !app.page_action_pending || app.page_action_pending_arm_repeat)
    goto cleanup;
  eightvo_render_to_buffer(&app, &held_buffer);
  B32 deferred_forward_presented =
    eightvo_frame_presentation_is_complete(&app) &&
    eightvo_capture_rendered_presentation_identity(
      &app, &app.last_surface_identity);
  eightvo_page_repeat_note_presented_frame(&app,
                                             deferred_forward_presented);
  SourceReaderPageRange deferred_reversal_return = app.reader.current_page;
  if (!deferred_forward_presented || app.page_repeat_active ||
      !app.page_action_waiting_for_present || app.page_action_pending ||
      deferred_reversal_return.spine_index !=
        deferred_reversal_origin.spine_index ||
      deferred_reversal_return.first_byte != deferred_reversal_origin.first_byte ||
      deferred_reversal_return.one_past_last_byte !=
        deferred_reversal_origin.one_past_last_byte)
    goto cleanup;
  eightvo_render_to_buffer(&app, &held_buffer);
  B32 deferred_return_presented =
    eightvo_frame_presentation_is_complete(&app) &&
    eightvo_capture_rendered_presentation_identity(
      &app, &app.last_surface_identity);
  eightvo_page_repeat_note_presented_frame(&app,
                                             deferred_return_presented);
  eightvo_page_action_release_key(&app, VK_RIGHT);
  if (!deferred_return_presented || app.page_action_waiting_for_present ||
      app.page_action_pending || app.page_repeat_active ||
      app.page_action_emitted_count != deferred_emitted_before + 2 ||
      app.page_action_presented_count != deferred_presented_before + 2 ||
      app.page_action_overlap_count != deferred_overlap_before)
    goto cleanup;
  deferred_reversal_keyup_passed = 1;
  for (U32 repeat_pass = 0; repeat_pass < 2; repeat_pass += 1)
  {
    S32 repeat_direction = repeat_pass == 0 ? 1 : -1;
    WPARAM repeat_key = repeat_direction > 0 ? VK_RIGHT : VK_LEFT;
    checkpoint = 32 + repeat_pass;
    eightvo_stop_page_repeat(&app);
    eightvo_cancel_adjacent_warm(&app);
    eightvo_invalidate_adjacent_page(&app);
    if (!font_cache_clear_shaped_text(&app.render_state.text_cache) ||
        eightvo_move_page(&app, repeat_direction) != EpubReaderResult_Ok)
      goto cleanup;
    eightvo_page_action_note_emitted(&app);

    /* A production keydown moves immediately and then arms repeat. Lock the
       same wall-clock due time, complete-presentation gate, active/same-key
       native coalescing, and no-catch-up ordering used by the Win32 loop. */
    eightvo_begin_page_repeat(&app, repeat_key, repeat_direction);
    S32 coalesced_direction = 0;
    WPARAM alternate_key = repeat_direction > 0 ? VK_NEXT : VK_PRIOR;
    WPARAM opposite_key = repeat_direction > 0 ? VK_LEFT : VK_RIGHT;
    if (!eightvo_page_repeat_should_coalesce_keydown(
          &app, repeat_key, (LPARAM)0x40000001, &coalesced_direction) ||
        coalesced_direction != repeat_direction ||
        eightvo_page_repeat_should_coalesce_keydown(
          &app, repeat_key, (LPARAM)1, 0) ||
        eightvo_page_repeat_should_coalesce_keydown(
          &app, alternate_key, (LPARAM)0x40000001, 0) ||
        eightvo_page_repeat_should_coalesce_keydown(
          &app, opposite_key, (LPARAM)0x40000001, 0))
      goto cleanup;
    held_native_repeat_coalesced_count += 1;

    U64 initial_due_ticks = app.page_repeat_next_move_ticks;
    EightvoPageRepeatFrameResult blocked_tick =
      eightvo_page_repeat_frame_step(&app, initial_due_ticks);
    if (!blocked_tick.action_due || blocked_tick.action_emitted ||
        !blocked_tick.action_waiting_for_render)
      goto cleanup;
    eightvo_page_repeat_note_presented_frame(&app, 0);
    if (!app.page_action_waiting_for_present)
      goto cleanup;
    held_render_gate_block_count += 1;
    eightvo_render_to_buffer(&app, &held_buffer);
    B32 initial_present_complete =
      eightvo_frame_presentation_is_complete(&app) &&
      eightvo_capture_rendered_presentation_identity(
        &app, &app.last_surface_identity);
    eightvo_page_repeat_note_presented_frame(&app,
                                               initial_present_complete);
    if (!initial_present_complete || app.page_action_waiting_for_present)
      goto cleanup;
    EightvoPageRepeatFrameResult early_tick =
      eightvo_page_repeat_frame_step(
        &app, initial_due_ticks > 0 ? initial_due_ticks - 1 : 0);
    if (early_tick.action_due || early_tick.action_emitted ||
        early_tick.action_waiting_for_render)
      goto cleanup;

    U32 pass_move_count = 0;
    U64 pass_warmed_hash = 0;
    for (U32 repeat_step = 0; repeat_step < 2; repeat_step += 1)
    {
      U64 repeat_now_ticks = app.page_repeat_next_move_ticks;
      U64 held_step_start = os_time_ticks();
      EightvoPageRepeatFrameResult tick =
        eightvo_page_repeat_frame_step(&app, repeat_now_ticks);
      U64 held_step_ticks = os_time_ticks() - held_step_start;
      if (!tick.action_due || !tick.action_emitted ||
          tick.action_waiting_for_render)
        goto cleanup;
      held_move_total_ticks += held_step_ticks;
      held_move_max_ticks = MAX(held_move_max_ticks, held_step_ticks);
      repeat_move_count += 1;
      pass_move_count += 1;
      if (repeat_direction > 0) repeat_forward_move_count += 1;
      else repeat_backward_move_count += 1;

      U64 held_render_start = os_time_ticks();
      eightvo_render_to_buffer(&app, &held_buffer);
      U64 held_render_ticks = os_time_ticks() - held_render_start;
      held_render_total_ticks += held_render_ticks;
      held_render_max_ticks = MAX(held_render_max_ticks, held_render_ticks);
      U64 held_action_ticks = held_step_ticks + held_render_ticks;
      held_action_total_ticks += held_action_ticks;
      held_action_max_ticks = MAX(held_action_max_ticks, held_action_ticks);
      B32 held_present_complete =
        eightvo_frame_presentation_is_complete(&app) &&
        eightvo_capture_rendered_presentation_identity(
          &app, &app.last_surface_identity);
      eightvo_page_repeat_note_presented_frame(&app,
                                                 held_present_complete);
      if (!held_present_complete || app.page_action_waiting_for_present)
        goto cleanup;
      if (repeat_step + 1 < 2)
      {
        U64 next_due_ticks = app.page_repeat_next_move_ticks;
        EightvoPageRepeatFrameResult before_due_tick =
          eightvo_page_repeat_frame_step(
            &app, next_due_ticks > 0 ? next_due_ticks - 1 : 0);
        if (before_due_tick.action_due || before_due_tick.action_emitted ||
            before_due_tick.action_waiting_for_render)
          goto cleanup;
      }
      if (app.adjacent_page_cache_used_last_render)
        held_cache_hit_count += 1;
      pass_warmed_hash = u64_hash_bytes(
        held_pixels, held_pixel_count * sizeof(*held_pixels));
    }
    if (pass_move_count != 2 || pass_warmed_hash == 0) goto cleanup;
    eightvo_stop_page_repeat(&app);
    if (eightvo_page_repeat_should_coalesce_keydown(
          &app, repeat_key, (LPARAM)0x40000001, 0))
      goto cleanup;
    eightvo_cancel_adjacent_warm(&app);
    eightvo_invalidate_adjacent_page(&app);
    if (!font_cache_clear_shaped_text(&app.render_state.text_cache))
      goto cleanup;
    eightvo_render_to_buffer(&app, &held_buffer);
    U64 cold_hash = u64_hash_bytes(
      held_pixels, held_pixel_count * sizeof(*held_pixels));
    if (pass_warmed_hash != cold_hash) goto cleanup;
    held_pixel_exact_count += 1;
  }
  eightvo_stop_page_repeat(&app);
  eightvo_cancel_adjacent_warm(&app);
  app.width = Width;
  app.height = Height;
  eightvo_invalidate_adjacent_page(&app);
  eightvo_render_to_buffer(&app, &buffer);

  checkpoint = 40;
  for (U32 attempt = 0;
       performance_pair_count < PerformancePairCount && attempt < 64;
       attempt += 1)
  {
    checkpoint = 41;
    if (!font_cache_clear_shaped_text(&app.render_state.text_cache)) goto cleanup;
    eightvo_schedule_adjacent_warm(&app);
    for (U32 warm_step = 0;
         app.adjacent_warm_pending && warm_step < 1024;
         warm_step += 1)
    {
      (void)eightvo_adjacent_warm_step(&app);
      adjacent_warm_step_count += 1;
    }
    checkpoint = 42;
    if (app.adjacent_warm_pending) goto cleanup;
    adjacent_warm_page_count += app.adjacent_warm_completed_page_count;
    if (app.adjacent_warm_completed_page_count == 0 ||
        !app.adjacent_page_ready)
    {
      checkpoint = 43;
      if (eightvo_move_page(&app, 1) != EpubReaderResult_Ok) goto cleanup;
      eightvo_render_to_buffer(&app, &buffer);
      continue;
    }
    checkpoint = 44;
    U64 prepared_move_start = os_time_ticks();
    EpubReaderResult prepared_move = eightvo_move_page(&app, 1);
    U64 prepared_move_ticks = os_time_ticks() - prepared_move_start;
    if (prepared_move != EpubReaderResult_Ok) goto cleanup;
    prepared_move_total_ticks += prepared_move_ticks;
    prepared_move_max_ticks = MAX(prepared_move_max_ticks, prepared_move_ticks);
    U64 warmed_start = os_time_ticks();
    eightvo_render_to_buffer(&app, &buffer);
    U64 warmed_render_ticks = os_time_ticks() - warmed_start;
    warmed_render_total_ticks += warmed_render_ticks;
    warmed_render_max_ticks = MAX(warmed_render_max_ticks, warmed_render_ticks);
    if (!app.adjacent_page_cache_used_last_render) goto cleanup;
    warmed_cache_hit_count += 1;
    U64 warmed_hash = u64_hash_bytes(
      pixels, pixel_count * sizeof(*pixels));

    eightvo_cancel_adjacent_warm(&app);
    eightvo_invalidate_adjacent_page(&app);
    checkpoint = 45;
    if (!font_cache_clear_shaped_text(&app.render_state.text_cache))
      goto cleanup;
    U64 cold_start = os_time_ticks();
    eightvo_render_to_buffer(&app, &buffer);
    cold_render_total_ticks += os_time_ticks() - cold_start;
    U64 cold_hash = u64_hash_bytes(
      pixels, pixel_count * sizeof(*pixels));
    checkpoint = 46;
    if (warmed_hash != cold_hash) goto cleanup;
    pixel_exact_count += 1;
    performance_pair_count += 1;
    if (performance_pair_count < PerformancePairCount &&
        eightvo_move_page(&app, 1) != EpubReaderResult_Ok)
      goto cleanup;
  }
  checkpoint = 50;
  if (performance_pair_count != PerformancePairCount ||
      pixel_exact_count != PerformancePairCount ||
      warmed_cache_hit_count != PerformancePairCount ||
      repeat_move_count != 4 || repeat_forward_move_count != 2 ||
      repeat_backward_move_count != 2 || held_cache_hit_count != 0 ||
      held_pixel_exact_count != 2 ||
      held_render_gate_block_count != 2 ||
      held_native_repeat_coalesced_count != 2 ||
      !forward_endpoint_valid || reverse_exact_match_count != ScanPageCount ||
      !returned_to_forward_start ||
      !deferred_reversal_keyup_passed || !boundary_oracle_self_test ||
      gotm_text_frame_count == 0 ||
      gotm_minimum_text_bytes < EightvoGotmMinimumProseTextBytes ||
      gotm_minimum_text_rows < EightvoGotmMinimumProseTextRows ||
      canonical_nonempty_frame_count != ScanPageCount * 2 ||
      zero_page_or_frame_count != 0 || orphan_text_page_count != 0 ||
      invalid_word_start_page_count != 0 ||
      first_zero_page_count_step != ScanPageCount ||
      warmed_render_total_ticks >= cold_render_total_ticks)
    goto cleanup;
  if (!render_text_cache_stats(&app.render_state, &cache_stats)) goto cleanup;
  result = 0;

cleanup:
  if (result == 0)
  {
    fprintf(stdout,
            "eightvo_page_turn_regression_smoke result=pass forward=%u backward=%u direct_traversal=64+64_exact forward_endpoint_valid=%d/1 reverse_range_exact=%u/%u returned_to_start=%d/1 canonical_nonempty_frames=%u/%u zero_pages_or_frames=%u/0 orphan_text_pages=%u/0 invalid_word_start_pages=%u/0 boundary_oracle=raw_spine_utf8_word_start gotm_prose_scope=active_spine_text_ge_128 boundary_oracle_self_test=%d/1 row_coverage=%u/%u gotm_minimum_text_bytes=%llu/%d gotm_minimum_text_rows=%u/%d deferred_reversal_keyup=%d/1 prepared_warm_pages=%u warm_steps=%u pixel_exact=%u/%u warmed_cache_hits=%u/%u repeat=wall_clock24_interval3_coalesced_no_catch_up repeat_moves=%u held_repeat=action_first_render_gated_no_speculative held_viewport=%ux%u held_forward=%u held_backward=%u held_cache_hits=%u held_pixel_exact=%u/2 held_native_repeats_coalesced=%u/2 held_render_gate_blocks=%u/2 held_warm_on_action=%u held_warm_steps=%u held_warm_avg_ms=%.3f held_warm_max_ms=%.3f held_move_avg_ms=%.3f held_move_max_ms=%.3f held_render_avg_ms=%.3f held_render_max_ms=%.3f held_action_total_avg_ms=%.3f held_action_total_max_ms=%.3f prepared_move_avg_ms=%.3f prepared_move_max_ms=%.3f warmed_render_avg_ms=%.3f warmed_render_max_ms=%.3f cold_render_avg_ms=%.3f move_avg_ms=%.3f move_max_ms=%.3f scan_render_avg_ms=%.3f scan_render_max_ms=%.3f draw_overflow=%u shaped_overflow=%u raster_overflow=%u run_overflow=%u output=%s\n",
            forward_count, backward_count,
            forward_endpoint_valid,
            reverse_exact_match_count, ScanPageCount,
            returned_to_forward_start,
            canonical_nonempty_frame_count, ScanPageCount * 2,
            zero_page_or_frame_count, orphan_text_page_count,
            invalid_word_start_page_count, boundary_oracle_self_test,
            canonical_nonempty_frame_count, ScanPageCount * 2,
            (unsigned long long)gotm_minimum_text_bytes,
            EightvoGotmMinimumProseTextBytes,
            gotm_minimum_text_rows,
            EightvoGotmMinimumProseTextRows,
            deferred_reversal_keyup_passed,
            adjacent_warm_page_count, adjacent_warm_step_count,
            pixel_exact_count, performance_pair_count,
            warmed_cache_hit_count, performance_pair_count,
            repeat_move_count,
            HeldWidth, HeldHeight,
            repeat_forward_move_count, repeat_backward_move_count,
            held_cache_hit_count, held_pixel_exact_count,
            held_native_repeat_coalesced_count,
            held_render_gate_block_count,
            0U,
            0U,
            0.0,
            0.0,
            1000.0 * (double)held_move_total_ticks /
              (double)os_time_frequency() /
              (double)MAX(repeat_move_count, 1),
            1000.0 * (double)held_move_max_ticks /
              (double)os_time_frequency(),
            1000.0 * (double)held_render_total_ticks /
              (double)os_time_frequency() /
              (double)MAX(repeat_move_count, 1),
            1000.0 * (double)held_render_max_ticks /
              (double)os_time_frequency(),
            1000.0 * (double)held_action_total_ticks /
              (double)os_time_frequency() /
              (double)MAX(repeat_move_count, 1),
            1000.0 * (double)held_action_max_ticks /
              (double)os_time_frequency(),
            1000.0 * (double)prepared_move_total_ticks /
              (double)os_time_frequency() /
              (double)MAX(performance_pair_count, 1),
            1000.0 * (double)prepared_move_max_ticks /
              (double)os_time_frequency(),
            1000.0 * (double)warmed_render_total_ticks /
              (double)os_time_frequency() /
              (double)MAX(performance_pair_count, 1),
            1000.0 * (double)warmed_render_max_ticks /
              (double)os_time_frequency(),
            1000.0 * (double)cold_render_total_ticks /
              (double)os_time_frequency() /
              (double)MAX(performance_pair_count, 1),
            (forward_count + backward_count) ?
              1000.0 * (double)move_total_ticks /
                (double)os_time_frequency() /
                (double)(forward_count + backward_count) : 0.0,
            1000.0 * (double)move_max_ticks / (double)os_time_frequency(),
            (forward_count + backward_count + 1) ?
              1000.0 * (double)render_total_ticks /
                (double)os_time_frequency() /
                (double)(forward_count + backward_count + 1) : 0.0,
            1000.0 * (double)render_max_ticks / (double)os_time_frequency(),
            app.draw_commands.overflow_count,
            cache_stats.shaped_text_overflow_count,
            cache_stats.raster_overflow_count,
            cache_stats.run_overflow_count,
            output_prefix);
  }
  else
  {
    EpubReaderLocationSummary location = epub_reader_location_summary(&app.reader);
    U32 failure_shaped_count = 0;
    for (U32 layer = 0; layer < DrawLayer_Count; layer += 1)
    {
      for (U32 index = 0;
           index < app.draw_commands.command_count[layer];
           index += 1)
      {
        const DrawCommand *command = app.draw_commands.commands[layer] + index;
        if (command->type == DrawCommandType_Text &&
            (command->v.text.flags & DrawTextFlag_Shaped))
          failure_shaped_count += 1;
      }
    }
    fprintf(stderr,
            "eightvo_page_turn_regression_smoke result=fail checkpoint=%u direction=%s step=%u forward=%u backward=%u prepared_warm_pages=%u warm_steps=%u warm_next=%u warm_shaped=%u warm_pending=%d warm_frame_ready=%d warm_direction=%d pixel_exact=%u/%u repeat_moves=%u held_forward=%u held_backward=%u held_cache_hits=%u held_pixel_exact=%u held_native_repeats_coalesced=%u held_render_gate_blocks=%u held_warm_on_action=%u held_warm_steps=%u held_warm_max_ms=%.3f held_move_max_ms=%.3f held_render_max_ms=%.3f held_action_total_max_ms=%.3f ready=%d toolbar=%d error_flags=%u change_flags=%u draw_overflow=%u spine=%u byte=%llu frame_page_summary=%llu/%llu canonical_page=%u:%llu-%llu:%llu/%llu frame_visible=%llu+%llu frame_images=%u frame_rows=%u frame_rows_complete=%d frame_non_whitespace=%llu active_spine_text_bytes=%llu prose_scope=%d location=%llu/%llu canonical_zero_step=%u zero_before=%u:%llu-%llu:%llu/%llu zero_after=%u:%llu-%llu:%llu/%llu zero_producer=%d zero_diagnostic=%d zero_resolved=%u:%llu-%llu zero_prepared_hit=%d zero_adjacent_kind=%d zero_prepared_build=%llu>%llu zero_prepared_hits=%llu>%llu zero_prepared_misses=%llu>%llu zero_ring=%u>%u zero_host_warm=%d/%d failure_result=%d failure_before=%u:%llu-%llu:%llu/%llu failure_after=%u:%llu-%llu:%llu/%llu failure_producer=%d failure_diagnostic=%d failure_resolved=%u:%llu-%llu failure_prepared_hit=%d failure_adjacent_kind=%d failure_prepared_build=%llu>%llu failure_prepared_hits=%llu>%llu failure_prepared_misses=%llu>%llu status=\"%s\"\n",
            checkpoint, failure_backward ? "backward" : "forward", failure_step,
            forward_count, backward_count, adjacent_warm_page_count,
            adjacent_warm_step_count, app.adjacent_warm_next_text_command,
            failure_shaped_count, app.adjacent_warm_pending,
            app.adjacent_warm_frame_ready, app.adjacent_warm_direction,
            pixel_exact_count, performance_pair_count, repeat_move_count,
            repeat_forward_move_count, repeat_backward_move_count,
            held_cache_hit_count, held_pixel_exact_count,
            held_native_repeat_coalesced_count,
            held_render_gate_block_count,
            0U,
            0U,
            0.0,
            1000.0 * (double)held_move_max_ticks /
              (double)os_time_frequency(),
            1000.0 * (double)held_render_max_ticks /
              (double)os_time_frequency(),
            1000.0 * (double)held_action_max_ticks /
              (double)os_time_frequency(),
            app.reader_view_ready,
            app.reader_view_layout.toolbar_visible,
            app.reader_view_frame.error_flags,
            app.reader_view_frame.change_flags,
            app.draw_commands.overflow_count,
            app.reader.active_spine_index,
            (unsigned long long)app.reader.view_byte_offset,
            (unsigned long long)app.frame.page_index,
            (unsigned long long)app.frame.page_count,
            app.reader.current_page.spine_index,
            (unsigned long long)app.reader.current_page.first_byte,
            (unsigned long long)
              app.reader.current_page.one_past_last_byte,
            (unsigned long long)app.reader.current_page.spine_page_index,
            (unsigned long long)app.reader.current_page.spine_page_count,
            (unsigned long long)app.frame.view_byte_offset,
            (unsigned long long)app.frame.visible_text.size,
            app.frame.image_count,
            app.frame.style_row_count,
            eightvo_frame_text_rows_are_complete(&app.frame, 0, 0),
            (unsigned long long)
              eightvo_frame_non_whitespace_byte_count(&app.frame),
            (unsigned long long)app.reader.spine_text.size,
            app.reader.spine_text.size >= EightvoGotmMinimumProseSpineBytes,
            (unsigned long long)location.location_index,
            (unsigned long long)location.location_count,
            first_zero_page_count_step,
            first_zero_before_page.spine_index,
            (unsigned long long)first_zero_before_page.first_byte,
            (unsigned long long)first_zero_before_page.one_past_last_byte,
            (unsigned long long)first_zero_before_page.spine_page_index,
            (unsigned long long)first_zero_before_page.spine_page_count,
            first_zero_after_page.spine_index,
            (unsigned long long)first_zero_after_page.first_byte,
            (unsigned long long)first_zero_after_page.one_past_last_byte,
            (unsigned long long)first_zero_after_page.spine_page_index,
            (unsigned long long)first_zero_after_page.spine_page_count,
            first_zero_nav_after.page_move_last_producer_code,
            first_zero_nav_after.page_move_last_diagnostic_code,
            first_zero_nav_after.page_move_last_resolved_spine,
            (unsigned long long)
              first_zero_nav_after.page_move_last_resolved_first_byte,
            (unsigned long long)
              first_zero_nav_after.page_move_last_resolved_one_past_last_byte,
            first_zero_nav_after.page_move_last_prepared_hit,
            first_zero_nav_after.adjacent_resolve_last_kind,
            (unsigned long long)first_zero_nav_before.prepared_window_build_count,
            (unsigned long long)first_zero_nav_after.prepared_window_build_count,
            (unsigned long long)first_zero_nav_before.prepared_window_hit_count,
            (unsigned long long)first_zero_nav_after.prepared_window_hit_count,
            (unsigned long long)first_zero_nav_before.prepared_window_miss_count,
            (unsigned long long)first_zero_nav_after.prepared_window_miss_count,
            first_zero_prepared_ring_before,
            first_zero_prepared_ring_after,
            first_zero_warm_pending_before,
            first_zero_warm_ready_before,
            (int)failure_move_result,
            failure_before_page.spine_index,
            (unsigned long long)failure_before_page.first_byte,
            (unsigned long long)failure_before_page.one_past_last_byte,
            (unsigned long long)failure_before_page.spine_page_index,
            (unsigned long long)failure_before_page.spine_page_count,
            failure_after_page.spine_index,
            (unsigned long long)failure_after_page.first_byte,
            (unsigned long long)failure_after_page.one_past_last_byte,
            (unsigned long long)failure_after_page.spine_page_index,
            (unsigned long long)failure_after_page.spine_page_count,
            failure_nav_after.page_move_last_producer_code,
            failure_nav_after.page_move_last_diagnostic_code,
            failure_nav_after.page_move_last_resolved_spine,
            (unsigned long long)
              failure_nav_after.page_move_last_resolved_first_byte,
            (unsigned long long)
              failure_nav_after.page_move_last_resolved_one_past_last_byte,
            failure_nav_after.page_move_last_prepared_hit,
            failure_nav_after.adjacent_resolve_last_kind,
            (unsigned long long)failure_nav_before.prepared_window_build_count,
            (unsigned long long)failure_nav_after.prepared_window_build_count,
            (unsigned long long)failure_nav_before.prepared_window_hit_count,
            (unsigned long long)failure_nav_after.prepared_window_hit_count,
            (unsigned long long)failure_nav_before.prepared_window_miss_count,
            (unsigned long long)failure_nav_after.prepared_window_miss_count,
            app.status);
  }
  free(pixels);
  free(held_pixels);
  eightvo_app_release(&app);
  return result;
}

FUNCTION B32
eightvo_page_repeat_probe_set_sandbox_paths(EightvoApp *app)
{
  if (!app || app->app_directory[0] || app->state_path[0] ||
      app->catalog_path[0] || app->settings_path[0] ||
      app->annotations_path[0])
  {
    return 0;
  }
  char temp_root[EightvoPathCap] = {0};
  DWORD temp_root_size = GetTempPathA((DWORD)ARRAY_COUNT(temp_root),
                                      temp_root);
  if (temp_root_size == 0 || temp_root_size >= ARRAY_COUNT(temp_root))
    return 0;
  U64 nonce = os_time_ticks();
  if (cstr_format(app->app_directory,
                  ARRAY_COUNT(app->app_directory),
                  "%seightvo_page_repeat_%u_%llu",
                  temp_root,
                  (U32)GetCurrentProcessId(),
                  (unsigned long long)nonce) == 0 ||
      cstr_format(app->state_path,
                  ARRAY_COUNT(app->state_path),
                  "%s\\state.v1",
                  app->app_directory) == 0 ||
      cstr_format(app->catalog_path,
                  ARRAY_COUNT(app->catalog_path),
                  "%s\\library.v1",
                  app->app_directory) == 0 ||
      cstr_format(app->settings_path,
                  ARRAY_COUNT(app->settings_path),
                  "%s\\settings.v1",
                  app->app_directory) == 0 ||
      cstr_format(app->annotations_path,
                  ARRAY_COUNT(app->annotations_path),
                  "%s\\annotations.v1",
                  app->app_directory) == 0 ||
      cstr_format(app->export_path,
                  ARRAY_COUNT(app->export_path),
                  "%s\\annotations.txt",
                  app->app_directory) == 0)
  {
    return 0;
  }
  return os_make_directory_chain(app->app_directory);
}

FUNCTION void
eightvo_page_repeat_probe_delete_atomic_path(const char *path)
{
  if (!path || !path[0]) return;
  char temp_path[EightvoPathCap] = {0};
  (void)os_file_delete(path);
  if (os_make_temp_path(temp_path, ARRAY_COUNT(temp_path), path))
    (void)os_file_delete(temp_path);
}

FUNCTION void
eightvo_page_repeat_probe_cleanup_sandbox_files(EightvoApp *app)
{
  if (!app || !app->app_directory[0]) return;
  for (U32 entry_index = 0;
       entry_index < app->library.entry_count;
       entry_index += 1)
  {
    char thumbnail_path[EightvoPathCap] = {0};
    if (eightvo_library_thumbnail_path(
          app, app->library.entries[entry_index].entry_id,
          thumbnail_path, ARRAY_COUNT(thumbnail_path)))
    {
      eightvo_page_repeat_probe_delete_atomic_path(thumbnail_path);
    }
  }
  eightvo_page_repeat_probe_delete_atomic_path(app->state_path);
  eightvo_page_repeat_probe_delete_atomic_path(app->catalog_path);
  eightvo_page_repeat_probe_delete_atomic_path(app->settings_path);
  eightvo_page_repeat_probe_delete_atomic_path(app->annotations_path);
  eightvo_page_repeat_probe_delete_atomic_path(app->export_path);
}

FUNCTION B32
eightvo_page_repeat_probe_remove_sandbox_directory(const char *directory)
{
  if (!directory || !directory[0]) return 0;
  const char *names[] = {
    "state.v1", "library.v1", "settings.v1", "annotations.v1",
    "annotations.txt",
  };
  for (U32 attempt = 0; attempt < 8; attempt += 1)
  {
    for (U32 name_index = 0;
         name_index < ARRAY_COUNT(names);
         name_index += 1)
    {
      char path[EightvoPathCap] = {0};
      if (cstr_format(path, ARRAY_COUNT(path), "%s\\%s",
                      directory, names[name_index]) > 0)
      {
        eightvo_page_repeat_probe_delete_atomic_path(path);
      }
    }
    if (RemoveDirectoryA(directory)) return 1;
    DWORD error = GetLastError();
    if (error == ERROR_FILE_NOT_FOUND || error == ERROR_PATH_NOT_FOUND)
      return 1;
    if (error != ERROR_DIR_NOT_EMPTY && error != ERROR_SHARING_VIOLATION &&
        error != ERROR_ACCESS_DENIED)
      return 0;
    Sleep(1);
  }
  return 0;
}

FUNCTION B32
eightvo_page_repeat_probe_cleanup_sandbox(EightvoApp *app)
{
  if (!app || !app->app_directory[0]) return 0;
  eightvo_page_repeat_probe_cleanup_sandbox_files(app);
  return eightvo_page_repeat_probe_remove_sandbox_directory(
    app->app_directory);
}

FUNCTION B32
eightvo_page_repeat_probe_file_snapshot(const char *path,
                                          U64 size_cap,
                                          OS_FileProperties *out_properties,
                                          U64 *out_content_hash)
{
  if (out_properties) *out_properties = (OS_FileProperties){0};
  if (out_content_hash) *out_content_hash = 0;
  if (!path || !path[0] || size_cap == 0 || !out_properties ||
      !out_content_hash)
  {
    return 0;
  }
  OS_FileProperties properties = os_file_properties(path);
  if (!properties.exists || properties.is_directory || properties.size == 0 ||
      properties.size > size_cap)
  {
    return 0;
  }
  U8 *data = (U8 *)malloc((size_t)properties.size);
  U64 size = 0;
  if (!data || !os_read_entire_file(path, data, properties.size, &size) ||
      size != properties.size)
  {
    free(data);
    return 0;
  }
  *out_properties = properties;
  *out_content_hash = u64_hash_bytes(data, size);
  free(data);
  return 1;
}

FUNCTION B32
eightvo_page_repeat_probe_prepare_persistence(
  EightvoApp *app,
  EightvoPageRepeatWin32Probe *probe)
{
  if (!app || !probe || !app->persistence_enabled ||
      !app->state_path[0] || !app->catalog_path[0] ||
      !eightvo_save_state(app))
  {
    return 0;
  }
  OS_FileProperties state_properties = {0};
  OS_FileProperties catalog_properties = {0};
  probe->persistence_transaction_success_before =
    app->state_save_transaction_success_count;
  probe->persistence_baseline_ready =
    eightvo_page_repeat_probe_file_snapshot(
      app->state_path,
      EightvoStateFileCap,
      &state_properties,
      &probe->persistence_state_content_hash_before) &&
    eightvo_page_repeat_probe_file_snapshot(
      app->catalog_path,
      EightvoLibraryCatalogFileCap,
      &catalog_properties,
      &probe->persistence_catalog_content_hash_before) &&
    state_properties.modified_time > 0 &&
    catalog_properties.modified_time > 0 &&
    probe->persistence_transaction_success_before > 0;
  probe->persistence_state_modified_time_before =
    state_properties.modified_time;
  probe->persistence_catalog_modified_time_before =
    catalog_properties.modified_time;
  return probe->persistence_baseline_ready;
}

FUNCTION void
eightvo_page_repeat_probe_capture_hold_persistence(
  EightvoApp *app,
  EightvoPageRepeatWin32Probe *probe)
{
  if (!app || !probe || probe->persistence_hold_checked) return;
  U64 now = os_time_ticks();
  if (probe->elapsed_ticks == 0)
  {
    probe->elapsed_ticks = now >= probe->start_ticks ?
      now - probe->start_ticks : 0;
  }
  OS_FileProperties state_properties = {0};
  OS_FileProperties catalog_properties = {0};
  B32 state_snapshot = eightvo_page_repeat_probe_file_snapshot(
    app->state_path,
    EightvoStateFileCap,
    &state_properties,
    &probe->persistence_state_content_hash_during_hold);
  B32 catalog_snapshot = eightvo_page_repeat_probe_file_snapshot(
    app->catalog_path,
    EightvoLibraryCatalogFileCap,
    &catalog_properties,
    &probe->persistence_catalog_content_hash_during_hold);
  probe->persistence_transaction_success_during_hold =
    app->state_save_transaction_success_count;
  probe->persistence_state_modified_time_during_hold =
    state_properties.modified_time;
  probe->persistence_catalog_modified_time_during_hold =
    catalog_properties.modified_time;
  probe->persistence_hold_checked = 1;
  probe->persistence_hold_unchanged = probe->persistence_baseline_ready &&
    state_snapshot && catalog_snapshot &&
    probe->persistence_transaction_success_during_hold ==
      probe->persistence_transaction_success_before &&
    probe->persistence_state_modified_time_during_hold ==
      probe->persistence_state_modified_time_before &&
    probe->persistence_state_content_hash_during_hold ==
      probe->persistence_state_content_hash_before &&
    probe->persistence_catalog_modified_time_during_hold ==
      probe->persistence_catalog_modified_time_before &&
    probe->persistence_catalog_content_hash_during_hold ==
      probe->persistence_catalog_content_hash_before;
  U64 frequency = os_time_frequency();
  U64 wait_ticks = frequency > UINT64_MAX /
      EightvoPageRepeatProbePersistenceWaitMs ?
    UINT64_MAX :
    frequency * EightvoPageRepeatProbePersistenceWaitMs / 1000;
  probe->persistence_wait_deadline_ticks =
    now > UINT64_MAX - wait_ticks ? UINT64_MAX : now + wait_ticks;
  if (!probe->persistence_hold_unchanged) probe->failed = 1;
}

FUNCTION B32
eightvo_page_repeat_probe_page_equal(SourceReaderPageRange a,
                                       SourceReaderPageRange b)
{
  return eightvo_canonical_page_identity_equal(
    eightvo_canonical_page_identity(a),
    eightvo_canonical_page_identity(b));
}

FUNCTION B32
eightvo_page_repeat_probe_seek_anchor(EightvoApp *app)
{
  B32 rebuilt = app && epub_reader_rebuild_search(
    &app->reader, str8_from_cstr("1161st Year"));
  EpubReaderResult navigation_result = rebuilt &&
    app->reader.search_match_count > 0 ?
      eightvo_navigate_to_search_match(
        app, 0, &(EpubReaderSearchNavigationResult){0}) :
      EpubReaderResult_DocError;
  if (!app || !rebuilt || app->reader.search_match_count == 0 ||
      navigation_result != EpubReaderResult_Ok)
  {
    fprintf(stderr,
            "eightvo_page_repeat_anchor result=fail rebuilt=%d matches=%u navigation=%d status=%s\n",
            rebuilt,
            app ? app->reader.search_match_count : 0,
            (int)navigation_result,
            app ? app->status : "null");
    return 0;
  }
  U32 target_spine = app->reader.current_page.spine_index;
  B32 crossed_boundary = 0;
  /* Build the boundary anchor through the same connected forward history
     used by the product workflow. A cold semantic jump into Chapter 2 has
     no authoritative predecessor after its bounded retained history is
     cleared, so probing backward from that jump tests the deliberate
     fail-closed contract rather than held-key reversal. */
  for (U32 index = 0;
       index < EightvoPageRepeatProbeBoundarySearchCap;
       index += 1)
  {
    EpubReaderResult move = eightvo_move_page(app, 1);
    if (move != EpubReaderResult_Ok)
    {
      fprintf(stderr,
              "eightvo_page_repeat_anchor result=fail step=%u move=%d status=%s\n",
              index,
              (int)move,
              app->status);
      return 0;
    }
    if (app->reader.current_page.spine_index != target_spine)
    {
      crossed_boundary = 1;
      break;
    }
  }
  if (!crossed_boundary) return 0;
  for (U32 index = 0;
       index < EightvoPageRepeatProbeBoundaryLeadPageCount;
       index += 1)
  {
    if (eightvo_move_page(app, -1) != EpubReaderResult_Ok) return 0;
  }
  eightvo_cancel_adjacent_warm(app);
  eightvo_invalidate_adjacent_page(app);
  return 1;
}

FUNCTION B32
eightvo_page_repeat_probe_build_expected(const char *path,
  SourceReaderPageRange forward[EightvoPageRepeatProbePageCount],
  SourceReaderPageRange backward[EightvoPageRepeatProbePageCount],
  SourceReaderPageRange *out_anchor,
  SourceReaderPageRange *out_endpoint)
{
  EightvoApp app = {0};
  B32 inputs_ok = path && path[0] && forward && backward && out_anchor &&
    out_endpoint;
  B32 init_ok = inputs_ok && eightvo_app_init(
    &app,
    EightvoPageRepeatProbeWidth,
    EightvoPageRepeatProbeHeight,
    0,
    0);
  B32 open_ok = init_ok && eightvo_open_path(&app, path);
  B32 anchor_ok = open_ok && eightvo_page_repeat_probe_seek_anchor(&app);
  B32 result = inputs_ok && init_ok && open_ok && anchor_ok;
  if (!result)
  {
    fprintf(stderr,
            "eightvo_page_repeat_reference_setup inputs=%d init=%d open=%d anchor=%d status=%s\n",
            inputs_ok,
            init_ok,
            open_ok,
            anchor_ok,
            app.status);
  }
  if (result) *out_anchor = app.reader.current_page;
  U32 forward_crossings = 0;
  U32 forward_moves = 0;
  U32 backward_moves = 0;
  EpubReaderResult last_move_result = EpubReaderResult_Ok;
  for (U32 index = 0;
       result && index < EightvoPageRepeatProbePageCount;
       index += 1)
  {
    SourceReaderPageRange before = app.reader.current_page;
    last_move_result = eightvo_move_page(&app, 1);
    result = last_move_result == EpubReaderResult_Ok;
    if (result)
    {
      forward_moves += 1;
      forward[index] = app.reader.current_page;
      if (before.spine_index != app.reader.current_page.spine_index)
        forward_crossings += 1;
    }
  }
  if (result) *out_endpoint = app.reader.current_page;
  U32 backward_crossings = 0;
  for (U32 index = 0;
       result && index < EightvoPageRepeatProbePageCount;
       index += 1)
  {
    SourceReaderPageRange before = app.reader.current_page;
    last_move_result = eightvo_move_page(&app, -1);
    result = last_move_result == EpubReaderResult_Ok;
    if (result)
    {
      backward_moves += 1;
      backward[index] = app.reader.current_page;
      if (before.spine_index != app.reader.current_page.spine_index)
        backward_crossings += 1;
    }
  }
  result = result && forward_crossings > 0 && backward_crossings > 0 &&
    eightvo_page_repeat_probe_page_equal(app.reader.current_page,
                                           *out_anchor);
  if (!result)
  {
    fprintf(stderr,
            "eightvo_page_repeat_reference result=fail forward=%u/%u backward=%u/%u cross_spine=%u+%u move_result=%d anchor=%u:%llu-%llu endpoint=%u:%llu-%llu final=%u:%llu-%llu\n",
            forward_moves,
            EightvoPageRepeatProbePageCount,
            backward_moves,
            EightvoPageRepeatProbePageCount,
            forward_crossings,
            backward_crossings,
            (int)last_move_result,
            out_anchor->spine_index,
            (unsigned long long)out_anchor->first_byte,
            (unsigned long long)out_anchor->one_past_last_byte,
            out_endpoint->spine_index,
            (unsigned long long)out_endpoint->first_byte,
            (unsigned long long)out_endpoint->one_past_last_byte,
            app.reader.current_page.spine_index,
            (unsigned long long)app.reader.current_page.first_byte,
            (unsigned long long)app.reader.current_page.one_past_last_byte);
  }
  eightvo_app_release(&app);
  return result;
}

FUNCTION void
eightvo_page_repeat_win32_probe_note_page(
  EightvoApp *app,
  EightvoPageRepeatWin32Probe *probe)
{
  if (!app || !probe ||
      probe->kind != EightvoPageRepeatWin32Probe_Direction ||
      probe->actual_page_count >= ARRAY_COUNT(probe->actual_pages))
  {
    return;
  }
  U32 index = probe->actual_page_count;
  probe->actual_pages[index] = app->reader.current_page;
  SourceReaderPageRange previous =
    index == 0 ? probe->start_page : probe->actual_pages[index - 1];
  if (previous.spine_index != app->reader.current_page.spine_index)
    probe->cross_spine_transition_count += 1;
  B32 orphan_text = 0;
  B32 invalid_word_start = 0;
  U64 text_bytes = 0;
  U32 text_rows = 0;
  if (app->reader.current_page.spine_page_count == 0 ||
      !app->frame.ready || !app->frame.document_open)
    probe->zero_page_or_frame_count += 1;
  if (eightvo_gotm_navigation_frame_is_canonical_nonempty(
        app, &orphan_text, &invalid_word_start, &text_bytes, &text_rows))
    probe->valid_frame_count += 1;
  else
  {
    if (orphan_text) probe->orphan_text_page_count += 1;
    if (invalid_word_start) probe->invalid_word_start_page_count += 1;
    probe->failed = 1;
  }
  if (app->frame.image_count == 0 &&
      app->reader.spine_text.size >= EightvoGotmMinimumProseSpineBytes)
  {
    probe->text_frame_count += 1;
    probe->minimum_text_bytes = MIN(probe->minimum_text_bytes, text_bytes);
    probe->minimum_text_rows = MIN(probe->minimum_text_rows, text_rows);
  }
  if (index < probe->expected_page_count &&
      eightvo_page_repeat_probe_page_equal(probe->actual_pages[index],
                                             probe->expected_pages[index]))
  {
    probe->canonical_match_count += 1;
  }
  else
  {
    probe->failed = 1;
  }
  probe->actual_page_count += 1;
}

FUNCTION void
eightvo_page_repeat_win32_probe_note_stable_presentation(
  EightvoApp *app,
  EightvoPageRepeatWin32Probe *probe)
{
  if (!app || !probe ||
      probe->kind != EightvoPageRepeatWin32Probe_Direction ||
      probe->stable_present_count >= EightvoPageRepeatProbePageCount ||
      app->page_action_presented_count <=
        probe->page_action_presented_before + probe->stable_present_count)
  {
    return;
  }
  if (app->page_action_presented_count !=
        probe->page_action_presented_before + probe->stable_present_count + 1 ||
      app->page_action_last_stable_present_ticks < probe->start_ticks)
  {
    probe->failed = 1;
    return;
  }
  U32 index = probe->stable_present_count;
  U64 elapsed = app->page_action_last_stable_present_ticks -
    probe->start_ticks;
  probe->stable_present_elapsed_ticks[index] = elapsed;
  if (index >= 1 &&
      probe->stable_present_transition_count <
        ARRAY_COUNT(probe->stable_present_transition_ticks))
  {
    U32 transition_index = probe->stable_present_transition_count;
    SourceReaderPageRange previous_page =
      probe->actual_pages[index - 1];
    U64 interval = elapsed - probe->stable_present_elapsed_ticks[index - 1];
    B32 cross_spine = previous_page.spine_index !=
      app->reader.current_page.spine_index;
    probe->stable_present_transition_ticks[transition_index] = interval;
    probe->stable_present_transition_cross_spine[transition_index] =
      cross_spine;
    probe->stable_present_transition_count += 1;
  }
  if (index == 1)
  {
    probe->stable_present_first_delay_ticks =
      elapsed - probe->stable_present_elapsed_ticks[0];
  }
  else if (index >= 2)
  {
    U64 interval = elapsed -
      probe->stable_present_elapsed_ticks[index - 1];
    if (probe->stable_present_interval_count == 0)
      probe->stable_present_interval_min_ticks = interval;
    else
      probe->stable_present_interval_min_ticks = MIN(
        probe->stable_present_interval_min_ticks, interval);
    probe->stable_present_interval_max_ticks = MAX(
      probe->stable_present_interval_max_ticks, interval);
    probe->stable_present_interval_total_ticks += interval;
    probe->stable_present_interval_count += 1;
  }
  eightvo_page_repeat_win32_probe_note_page(app, probe);
  probe->stable_present_count += 1;
}

FUNCTION B32
eightvo_page_repeat_win32_probe_post_native(
  EightvoApp *app,
  EightvoPageRepeatWin32Probe *probe)
{
  if (!app || !app->window || !probe || !probe->enabled ||
      probe->native_repeats_per_frame == 0)
  {
    return 0;
  }
  for (U32 index = 0; index < probe->native_repeats_per_frame; index += 1)
  {
    if (!PostMessageW(app->window, WM_KEYDOWN, probe->key,
                      (LPARAM)0x40000001))
    {
      return 0;
    }
    probe->native_repeat_posted_count += 1;
  }
  return 1;
}

FUNCTION B32
eightvo_page_repeat_win32_probe_post_mutations(
  EightvoApp *app)
{
  if (!app || !app->window) return 0;
  for (U32 index = 0; index < EightvoPageRepeatProbeMutationCount;
       index += 1)
  {
    if (!PostMessageW(app->window,
                      EightvoPageRepeatProbeMutationMessage,
                      index,
                      0))
    {
      return 0;
    }
  }
  return 1;
}

FUNCTION B32
eightvo_page_repeat_win32_probe_post_cancelled_repeat(
  EightvoApp *app,
  EightvoPageRepeatWin32Probe *probe,
  UINT message)
{
  if (!app || !app->window || !probe ||
      !PostMessageW(app->window, message, probe->key,
                    (LPARAM)0x40000001))
  {
    return 0;
  }
  probe->cancelled_repeat_posted_count += 1;
  return 1;
}

FUNCTION void
eightvo_page_repeat_win32_probe_finish(EightvoWin32 *win32,
                                         EightvoPageRepeatWin32Probe *probe)
{
  EightvoApp *app = win32 ? &win32->app : 0;
  if (!app || !probe || !probe->enabled) return;
  U64 now = os_time_ticks();
  if (probe->elapsed_ticks == 0)
  {
    probe->elapsed_ticks = now >= probe->start_ticks ?
      now - probe->start_ticks : 0;
  }
  probe->logical_frame_count = app->page_repeat_presented_frame_count;
  probe->navigation_prepare_call_count =
    app->page_repeat_navigation_prepare_call_count;
  probe->navigation_prepare_build_count =
    app->page_repeat_navigation_prepare_build_count;
  probe->navigation_prepare_ready_count =
    app->page_repeat_navigation_prepare_ready_count;
  probe->navigation_prepare_cross_spine_ready_count =
    app->page_repeat_navigation_prepare_cross_spine_ready_count;
  probe->navigation_prepare_fail_count =
    app->page_repeat_navigation_prepare_fail_count;
  probe->navigation_prepare_total_ticks =
    app->page_repeat_navigation_prepare_total_ticks;
  probe->navigation_prepare_max_ticks =
    app->page_repeat_navigation_prepare_max_ticks;
  probe->native_repeat_coalesced_count =
    app->page_repeat_native_coalesced_count;
  probe->cancelled_repeat_consumed_count =
    app->page_repeat_cancelled_repeat_consumed_count;
  probe->modifier_cancel_count = app->page_repeat_modifier_cancel_count;
  probe->focus_cancel_count = app->page_repeat_focus_cancel_count;
  probe->deactivate_cancel_count = app->page_repeat_deactivate_cancel_count;
  probe->keyup_cancel_count = app->page_repeat_keyup_cancel_count;
  probe->mutation_cancel_count = app->page_repeat_mutation_cancel_count;
  probe->persistence_deferred_count =
    app->page_repeat_persistence_deferred_count;
  probe->persistence_rescheduled_count =
    app->page_repeat_persistence_rescheduled_count;
  if (app->page_action_emitted_count < probe->page_action_emitted_before ||
      app->page_action_presented_count < probe->page_action_presented_before ||
      app->page_action_overlap_count < probe->page_action_overlap_before ||
      app->page_action_identity_mismatch_count <
        probe->page_action_identity_mismatch_before ||
      app->page_action_mutation_drop_count <
        probe->page_action_mutation_drop_before)
  {
    probe->failed = 1;
  }
  else
  {
    probe->page_action_emitted_count = app->page_action_emitted_count -
      probe->page_action_emitted_before;
    probe->page_action_presented_count = app->page_action_presented_count -
      probe->page_action_presented_before;
    probe->page_action_overlap_count = app->page_action_overlap_count -
      probe->page_action_overlap_before;
    probe->page_action_identity_mismatch_count =
      app->page_action_identity_mismatch_count -
        probe->page_action_identity_mismatch_before;
    probe->page_action_mutation_drop_count =
      app->page_action_mutation_drop_count -
        probe->page_action_mutation_drop_before;
  }
  OS_FileProperties state_properties = {0};
  OS_FileProperties catalog_properties = {0};
  B32 state_snapshot = eightvo_page_repeat_probe_file_snapshot(
    app->state_path,
    EightvoStateFileCap,
    &state_properties,
    &probe->persistence_state_content_hash_after);
  B32 catalog_snapshot = eightvo_page_repeat_probe_file_snapshot(
    app->catalog_path,
    EightvoLibraryCatalogFileCap,
    &catalog_properties,
    &probe->persistence_catalog_content_hash_after);
  probe->persistence_transaction_success_after =
    app->state_save_transaction_success_count;
  probe->persistence_state_modified_time_after =
    state_properties.modified_time;
  probe->persistence_catalog_modified_time_after =
    catalog_properties.modified_time;
  probe->auxiliary_paint_dispatch_count =
    win32->page_repeat_probe_aux_paint_dispatch_count -
      probe->auxiliary_paint_dispatch_before;
  probe->main_null_paint_dispatch_count =
    win32->page_repeat_probe_main_null_paint_dispatch_count -
      probe->main_null_paint_dispatch_before;
  probe->persistence_post_stop_advanced =
    probe->persistence_hold_unchanged && state_snapshot && catalog_snapshot &&
    probe->persistence_transaction_success_before < UINT32_MAX &&
    probe->persistence_transaction_success_after ==
      probe->persistence_transaction_success_before + 1 &&
    probe->persistence_state_modified_time_after >
      probe->persistence_state_modified_time_during_hold &&
    probe->persistence_state_content_hash_after !=
      probe->persistence_state_content_hash_during_hold &&
    probe->persistence_catalog_modified_time_after >
      probe->persistence_catalog_modified_time_during_hold &&
    probe->persistence_catalog_content_hash_after !=
      probe->persistence_catalog_content_hash_during_hold;

  B32 common = !probe->failed && probe->started && probe->stop_posted &&
    !app->page_repeat_active && app->page_repeat_cancelled_key == 0 &&
    !app->page_action_waiting_for_present && !app->page_action_pending &&
    probe->page_action_emitted_count == probe->page_action_presented_count &&
    probe->page_action_overlap_count == 0 &&
    probe->page_action_identity_mismatch_count == 0 &&
    win32->page_repeat_probe_aux_paint_pending_count == 0 &&
    win32->page_repeat_probe_main_null_paint_pending_count == 0 &&
    !app->state_save_pending && probe->persistence_baseline_ready &&
    probe->persistence_hold_checked && probe->persistence_hold_unchanged &&
    probe->persistence_post_stop_advanced &&
    probe->queue_drain_batch_max_count <=
      EightvoPageRepeatProbeQueueDrainCap &&
    probe->native_repeat_coalesced_count ==
      probe->native_repeat_posted_count;
  if (probe->kind == EightvoPageRepeatWin32Probe_Direction)
  {
    U32 expected_presented_frames = probe->target_frame_move_count + 1;
    probe->completed = common &&
      app->page_repeat_frame_move_count == probe->target_frame_move_count &&
      probe->observed_move_count == probe->target_frame_move_count &&
      probe->logical_frame_count == expected_presented_frames &&
      probe->native_repeat_posted_count ==
        expected_presented_frames * probe->native_repeats_per_frame &&
      probe->repeat_timing_count == probe->target_frame_move_count &&
      probe->move_interval_count == probe->target_frame_move_count - 1 &&
      probe->expected_page_count == EightvoPageRepeatProbePageCount &&
      probe->actual_page_count == EightvoPageRepeatProbePageCount &&
      probe->stable_present_count == EightvoPageRepeatProbePageCount &&
      probe->stable_present_interval_count ==
        EightvoPageRepeatProbeMoveCount - 1 &&
      probe->canonical_match_count == EightvoPageRepeatProbePageCount &&
      probe->cross_spine_transition_count > 0 &&
      probe->valid_frame_count == EightvoPageRepeatProbePageCount &&
      probe->zero_page_or_frame_count == 0 &&
      probe->orphan_text_page_count == 0 &&
      probe->invalid_word_start_page_count == 0 &&
      probe->text_frame_count > 0 &&
      probe->minimum_text_bytes >= EightvoGotmMinimumProseTextBytes &&
      probe->minimum_text_rows >= EightvoGotmMinimumProseTextRows &&
      probe->page_action_emitted_count == EightvoPageRepeatProbePageCount &&
      probe->paint_messages_posted &&
      probe->auxiliary_paint_dispatch_count == 1 &&
      probe->main_null_paint_dispatch_count == 1 &&
      probe->cancelled_repeat_posted_count == 0 &&
      probe->cancelled_repeat_consumed_count == 0 &&
      probe->keyup_cancel_count == 1 &&
      probe->modifier_cancel_count == 0 && probe->focus_cancel_count == 0 &&
      probe->deactivate_cancel_count == 0 &&
      probe->persistence_deferred_count == probe->target_frame_move_count &&
      probe->persistence_rescheduled_count == 1;
  }
  else if (probe->kind == EightvoPageRepeatWin32Probe_MutationGate)
  {
    probe->completed = common && app->page_repeat_frame_move_count == 0 &&
      probe->observed_move_count == 0 &&
      probe->native_repeat_posted_count == probe->native_repeats_per_frame &&
      probe->native_repeat_coalesced_count == probe->native_repeat_posted_count &&
      probe->page_action_emitted_count == 1 &&
      probe->page_action_presented_count == 1 &&
      probe->page_action_mutation_drop_count ==
        EightvoPageRepeatProbeMutationCount &&
      probe->mutation_cancel_count == 1 &&
      probe->cancelled_repeat_posted_count == 0 &&
      probe->cancelled_repeat_consumed_count == 0 &&
      probe->keyup_cancel_count == 0 &&
      probe->persistence_deferred_count == 0 &&
      probe->persistence_rescheduled_count == 1 &&
      probe->expected_page_count == 1 &&
      eightvo_page_repeat_probe_page_equal(
        app->reader.current_page, probe->expected_pages[0]);
  }
  else
  {
    U32 expected_modifier =
      (probe->kind == EightvoPageRepeatWin32Probe_ControlModifier ||
       probe->kind == EightvoPageRepeatWin32Probe_ShiftModifier ||
       probe->kind == EightvoPageRepeatWin32Probe_SystemModifier) ? 1 : 0;
    U32 expected_focus =
      probe->kind == EightvoPageRepeatWin32Probe_FocusLoss ? 1 : 0;
    U32 expected_deactivate =
      probe->kind == EightvoPageRepeatWin32Probe_Deactivation ? 1 : 0;
    probe->completed = common && app->page_repeat_frame_move_count == 0 &&
      probe->observed_move_count == 0 &&
      probe->native_repeat_posted_count ==
        probe->native_repeats_per_frame &&
      probe->cancelled_repeat_posted_count == 1 &&
      probe->cancelled_repeat_consumed_count == 1 &&
      probe->modifier_cancel_count == expected_modifier &&
      probe->focus_cancel_count == expected_focus &&
      probe->deactivate_cancel_count == expected_deactivate &&
      probe->page_action_emitted_count == 1 &&
      probe->keyup_cancel_count == 0 &&
      probe->persistence_deferred_count == 0 &&
      probe->persistence_rescheduled_count == 1;
  }
}

FUNCTION B32
eightvo_win32_repeat_owns_pending_paint(const EightvoWin32 *win32,
                                         const MSG *message)
{
  return win32 && message && message->message == WM_PAINT &&
    message->hwnd == win32->window &&
    GetUpdateRect(win32->window, 0, FALSE) != 0;
}

FUNCTION B32
eightvo_page_repeat_win32_probe_post_paints(
  EightvoWin32 *win32,
  EightvoPageRepeatWin32Probe *probe)
{
  if (!win32 || !probe || !probe->enabled || probe->paint_messages_posted ||
      !win32->window || !win32->page_repeat_probe_paint_window ||
      GetUpdateRect(win32->window, 0, FALSE) != 0)
  {
    return 0;
  }
  win32->page_repeat_probe_main_null_paint_pending_count += 1;
  if (!PostMessageW(win32->window, WM_PAINT, 0, 0))
  {
    win32->page_repeat_probe_main_null_paint_pending_count -= 1;
    return 0;
  }
  win32->page_repeat_probe_aux_paint_pending_count += 1;
  if (!InvalidateRect(win32->page_repeat_probe_paint_window, 0, FALSE))
  {
    win32->page_repeat_probe_aux_paint_pending_count -= 1;
    return 0;
  }
  probe->paint_messages_posted = 1;
  return 1;
}

FUNCTION void
eightvo_win32_run_message_loop(EightvoWin32 *win32,
                                 EightvoPageRepeatWin32Probe *probe)
{
  if (!win32) return;
  MSG message = {0};
  B32 running = 1;
  U64 time_frequency = os_time_frequency();
  B32 timer_resolution_active = 0;
  while (running)
  {
    if (win32->app.page_repeat_active && !timer_resolution_active)
    {
      timer_resolution_active = timeBeginPeriod(1) == TIMERR_NOERROR;
    }
    else if (!win32->app.page_repeat_active && timer_resolution_active)
    {
      (void)timeEndPeriod(1);
      timer_resolution_active = 0;
    }
    if (!win32->app.page_repeat_active)
    {
      if (probe && probe->enabled && probe->stop_posted)
      {
        U64 now = os_time_ticks();
        if (win32->app.state_save_transaction_success_count >
              probe->persistence_transaction_success_before)
        {
          eightvo_page_repeat_win32_probe_finish(win32, probe);
          break;
        }
        if (probe->persistence_wait_deadline_ticks == 0 ||
            now >= probe->persistence_wait_deadline_ticks)
        {
          probe->failed = 1;
          eightvo_page_repeat_win32_probe_finish(win32, probe);
          break;
        }
        DWORD wait_result = MsgWaitForMultipleObjectsEx(
          0, 0, 50, QS_ALLINPUT, MWMO_INPUTAVAILABLE);
        if (wait_result == WAIT_FAILED)
        {
          probe->failed = 1;
          eightvo_page_repeat_win32_probe_finish(win32, probe);
          break;
        }
        while (PeekMessageW(&message, 0, 0, 0, PM_REMOVE))
        {
          if (message.message == WM_QUIT)
          {
            running = 0;
            probe->failed = 1;
            break;
          }
          TranslateMessage(&message);
          DispatchMessageW(&message);
        }
        if (!running) break;
        continue;
      }
      BOOL message_result = GetMessageW(&message, 0, 0, 0);
      if (message_result <= 0)
      {
        if (probe && probe->enabled) probe->failed = 1;
        break;
      }
      TranslateMessage(&message);
      DispatchMessageW(&message);
      if (probe && probe->enabled && !probe->started &&
          message.hwnd == win32->window && message.message == WM_KEYDOWN &&
          message.wParam == probe->key &&
          (message.lParam & (1LL << 30)) == 0)
      {
        if (win32->app.page_repeat_active)
        {
          probe->started = 1;
        }
        else
        {
          probe->failed = 1;
          probe->stop_posted = 1;
          eightvo_page_repeat_win32_probe_finish(win32, probe);
          break;
        }
      }
      if (probe && probe->enabled && probe->started &&
          probe->kind == EightvoPageRepeatWin32Probe_MutationGate &&
          !probe->stop_posted && !win32->app.page_repeat_active &&
          !win32->app.page_action_waiting_for_present &&
          win32->app.page_action_mutation_drop_count ==
            probe->page_action_mutation_drop_before +
              EightvoPageRepeatProbeMutationCount)
      {
        if (!eightvo_page_repeat_probe_page_equal(
              win32->app.reader.current_page,
              probe->expected_pages[0]))
          probe->failed = 1;
        eightvo_page_repeat_probe_capture_hold_persistence(
          &win32->app, probe);
        if (!PostMessageW(win32->app.window,
                          WM_KEYUP,
                          probe->key,
                          (LPARAM)0xC0000001))
          probe->failed = 1;
        probe->stop_posted = 1;
      }
      continue;
    }

    U32 drained_message_count = 0;
    while (PeekMessageW(&message, 0, 0, 0, PM_NOREMOVE))
    {
      if (eightvo_win32_repeat_owns_pending_paint(win32, &message)) break;
      if (!PeekMessageW(&message, 0, 0, 0, PM_REMOVE)) break;
      drained_message_count += 1;
      if (message.message == WM_QUIT)
      {
        running = 0;
        if (probe && probe->enabled) probe->failed = 1;
        break;
      }
      TranslateMessage(&message);
      DispatchMessageW(&message);
      if (drained_message_count >= EightvoPageRepeatQueueDrainCap)
      {
        break;
      }
    }
    if (probe && probe->enabled)
    {
      probe->queue_drain_batch_max_count = MAX(
        probe->queue_drain_batch_max_count, drained_message_count);
      probe->queue_drain_message_count += drained_message_count;
      if (drained_message_count > EightvoPageRepeatProbeQueueDrainCap)
        probe->failed = 1;
    }
    if (!running) break;
    if (!win32->app.page_repeat_active)
    {
      continue;
    }

    U64 now = os_time_ticks();
    B32 repeat_due = !win32->app.page_action_waiting_for_present &&
      now >= win32->app.page_repeat_next_move_ticks;
    B32 update_pending = GetUpdateRect(win32->window, 0, FALSE) != 0;
    B32 presentation_retry_waiting =
      win32->app.page_action_waiting_for_present &&
      win32->app.page_action_presentation_retry_attempt > 0 &&
      !update_pending;
    if (!presentation_retry_waiting &&
        (win32->app.page_action_waiting_for_present || update_pending ||
         !win32->app.last_present_complete || repeat_due))
    {
      U64 repeat_frame_start = now;
      U32 frame_move_count_before =
        win32->app.page_repeat_frame_move_count;
      EightvoPageRepeatFrameTiming frame_timing = {0};
      B32 frame_presented =
        eightvo_win32_page_repeat_frame(&win32->app,
                                          now,
                                          &frame_timing);
      U64 repeat_frame_end = os_time_ticks();

      if (probe && probe->enabled)
      {
        if (win32->app.page_repeat_frame_move_count !=
              frame_move_count_before)
        {
          U64 move_emitted_ticks = frame_timing.action_emitted_ticks ?
            frame_timing.action_emitted_ticks : repeat_frame_end;
          U64 move_elapsed_ticks =
            move_emitted_ticks >= probe->start_ticks ?
              move_emitted_ticks - probe->start_ticks : 0;
          if (win32->app.page_repeat_frame_move_count !=
                frame_move_count_before + 1)
          {
            probe->failed = 1;
          }
          if (probe->observed_move_count == 0)
          {
            probe->first_move_elapsed_ticks = move_elapsed_ticks;
          }
          else
          {
            U64 move_interval_ticks =
              move_elapsed_ticks >= probe->last_move_elapsed_ticks ?
                move_elapsed_ticks - probe->last_move_elapsed_ticks : 0;
            if (probe->move_interval_count == 0)
              probe->move_interval_min_ticks = move_interval_ticks;
            else
              probe->move_interval_min_ticks = MIN(
                probe->move_interval_min_ticks, move_interval_ticks);
            probe->move_interval_max_ticks = MAX(
              probe->move_interval_max_ticks, move_interval_ticks);
            probe->move_interval_total_ticks += move_interval_ticks;
            probe->move_interval_count += 1;
          }
          probe->last_move_elapsed_ticks = move_elapsed_ticks;
          probe->observed_move_count += 1;
          S32 move_producer =
            win32->app.reader.navigation_stats.page_move_last_producer_code;
          if (move_producer == EpubReaderPageMoveProducer_PreparedWindow)
            probe->prepared_window_move_count += 1;
          else if (move_producer == EpubReaderPageMoveProducer_WindowRebuild)
            probe->synchronous_window_rebuild_move_count += 1;
          else if (move_producer == EpubReaderPageMoveProducer_AdjacentMeasured)
            probe->synchronous_adjacent_measured_move_count += 1;
          else if (move_producer ==
                     EpubReaderPageMoveProducer_CrossSpineBoundaryRing)
            probe->cross_spine_boundary_ring_move_count += 1;
          U64 render_ticks = frame_timing.render_acquire_ticks +
            frame_timing.render_buffer_ticks +
            frame_timing.render_accessibility_ticks;
          probe->repeat_prepare_total_ticks +=
            frame_timing.action_prepare_ticks;
          probe->repeat_prepare_max_ticks = MAX(
            probe->repeat_prepare_max_ticks,
            frame_timing.action_prepare_ticks);
          probe->repeat_render_total_ticks += render_ticks;
          probe->repeat_render_max_ticks = MAX(
            probe->repeat_render_max_ticks, render_ticks);
          probe->repeat_present_total_ticks +=
            frame_timing.render_present_ticks;
          probe->repeat_present_max_ticks = MAX(
            probe->repeat_present_max_ticks,
            frame_timing.render_present_ticks);
          probe->repeat_timing_count += 1;
        }
        else if (probe->kind == EightvoPageRepeatWin32Probe_Direction &&
                 frame_presented && probe->frame_count == 0)
        {
          probe->cold_render_ticks = frame_timing.render_acquire_ticks +
            frame_timing.render_buffer_ticks +
            frame_timing.render_accessibility_ticks;
          probe->cold_present_ticks = frame_timing.render_present_ticks;
        }
        U64 frame_ticks = repeat_frame_end >= repeat_frame_start ?
          repeat_frame_end - repeat_frame_start : 0;
        eightvo_page_repeat_win32_probe_note_stable_presentation(
          &win32->app, probe);
        probe->frame_max_ticks = MAX(probe->frame_max_ticks, frame_ticks);
        probe->frame_count += 1;
        if (probe->kind == EightvoPageRepeatWin32Probe_Direction &&
            frame_presented && !probe->paint_messages_posted &&
            !eightvo_page_repeat_win32_probe_post_paints(win32, probe))
        {
          probe->failed = 1;
        }
        if (!win32->app.page_repeat_active)
        {
          probe->failed = 1;
          probe->stop_posted = 1;
        }
        else if (probe->kind == EightvoPageRepeatWin32Probe_Direction &&
                 win32->app.page_repeat_frame_move_count >=
                   probe->target_frame_move_count)
        {
          probe->elapsed_ticks = probe->stable_present_count > 0 ?
            probe->stable_present_elapsed_ticks[
              probe->stable_present_count - 1] : 0;
          eightvo_page_repeat_probe_capture_hold_persistence(&win32->app,
                                                               probe);
          if (!PostMessageW(win32->app.window,
                            WM_KEYUP,
                            probe->key,
                            (LPARAM)0xC0000001))
            probe->failed = 1;
          probe->stop_posted = 1;
        }
        else if (probe->frame_count >= probe->frame_cap)
        {
          probe->failed = 1;
          eightvo_page_repeat_probe_capture_hold_persistence(&win32->app,
                                                               probe);
          (void)PostMessageW(win32->app.window,
                             WM_KEYUP,
                             probe->key,
                             (LPARAM)0xC0000001);
          probe->stop_posted = 1;
        }
        else if (probe->kind != EightvoPageRepeatWin32Probe_Direction &&
                 !probe->stop_posted && frame_presented)
        {
          B32 posted = 0;
          eightvo_page_repeat_probe_capture_hold_persistence(&win32->app,
                                                               probe);
          if (probe->kind == EightvoPageRepeatWin32Probe_FocusLoss)
          {
            posted = PostMessageW(win32->app.window, WM_KILLFOCUS, 0, 0) &&
              eightvo_page_repeat_win32_probe_post_cancelled_repeat(
                &win32->app, probe, WM_KEYDOWN) &&
              PostMessageW(win32->app.window,
                           WM_KEYUP,
                           probe->key,
                           (LPARAM)0xC0000001);
          }
          else if (probe->kind ==
                     EightvoPageRepeatWin32Probe_ControlModifier)
          {
            posted = PostMessageW(win32->app.window,
                                  WM_KEYDOWN,
                                  VK_CONTROL,
                                  1) &&
              eightvo_page_repeat_win32_probe_post_cancelled_repeat(
                &win32->app, probe, WM_KEYDOWN) &&
              PostMessageW(win32->app.window,
                           WM_KEYUP,
                           probe->key,
                           (LPARAM)0xC0000001) &&
              PostMessageW(win32->app.window,
                           WM_KEYUP,
                           VK_CONTROL,
                           (LPARAM)0xC0000001);
          }
          else if (probe->kind ==
                     EightvoPageRepeatWin32Probe_ShiftModifier)
          {
            posted = PostMessageW(win32->app.window,
                                  WM_KEYDOWN,
                                  VK_SHIFT,
                                  1) &&
              eightvo_page_repeat_win32_probe_post_cancelled_repeat(
                &win32->app, probe, WM_KEYDOWN) &&
              PostMessageW(win32->app.window,
                           WM_KEYUP,
                           probe->key,
                           (LPARAM)0xC0000001) &&
              PostMessageW(win32->app.window,
                           WM_KEYUP,
                           VK_SHIFT,
                           (LPARAM)0xC0000001);
          }
          else if (probe->kind ==
                     EightvoPageRepeatWin32Probe_SystemModifier)
          {
            posted = PostMessageW(win32->app.window,
                                  WM_SYSKEYDOWN,
                                  VK_MENU,
                                  1) &&
              eightvo_page_repeat_win32_probe_post_cancelled_repeat(
                &win32->app, probe, WM_SYSKEYDOWN) &&
              PostMessageW(win32->app.window,
                           WM_SYSKEYUP,
                           probe->key,
                           (LPARAM)0xC0000001) &&
              PostMessageW(win32->app.window,
                           WM_SYSKEYUP,
                           VK_MENU,
                           (LPARAM)0xC0000001);
          }
          else if (probe->kind ==
                     EightvoPageRepeatWin32Probe_Deactivation)
          {
            posted = PostMessageW(win32->app.window,
                                  WM_ACTIVATEAPP,
                                  FALSE,
                                  0) &&
              eightvo_page_repeat_win32_probe_post_cancelled_repeat(
                &win32->app, probe, WM_KEYDOWN) &&
              PostMessageW(win32->app.window,
                           WM_KEYUP,
                           probe->key,
                           (LPARAM)0xC0000001);
          }
          if (!posted) probe->failed = 1;
          probe->stop_posted = 1;
        }
        else if (probe->kind == EightvoPageRepeatWin32Probe_Direction &&
                 frame_presented &&
                 !eightvo_page_repeat_win32_probe_post_native(
                   &win32->app, probe))
        {
          probe->failed = 1;
          eightvo_page_repeat_probe_capture_hold_persistence(&win32->app,
                                                               probe);
          (void)PostMessageW(win32->app.window,
                             WM_KEYUP,
                             probe->key,
                             (LPARAM)0xC0000001);
          probe->stop_posted = 1;
        }
      }
    }
    if (!win32->app.page_repeat_active) continue;

    now = os_time_ticks();
    update_pending = GetUpdateRect(win32->window, 0, FALSE) != 0;
    if (win32->app.page_action_waiting_for_present &&
        win32->app.page_action_presentation_retry_attempt > 0 &&
        !update_pending)
    {
      DWORD wait_result = MsgWaitForMultipleObjectsEx(
        0, 0, EightvoPresentationRetryMaxDelayMs,
        QS_ALLINPUT, MWMO_INPUTAVAILABLE);
      if (wait_result == WAIT_FAILED && probe && probe->enabled)
        probe->failed = 1;
      continue;
    }
    if (win32->app.page_repeat_navigation_prepare_pending &&
        !win32->app.page_action_waiting_for_present &&
        !win32->app.page_action_pending && !update_pending &&
        now < win32->app.page_repeat_next_move_ticks && time_frequency > 0)
    {
      U64 remaining_ticks =
        win32->app.page_repeat_next_move_ticks - now;
      U64 reserve_ticks =
        (time_frequency + 999ULL) / 1000ULL;
      if (remaining_ticks > reserve_ticks)
      {
        /* Input/cancellation already in the queue owns priority over optional
           logical preparation. Return to the existing bounded FIFO drain so
           its paint reservation, dispatch ordering, and probe accounting stay
           authoritative; key-up/cancellation will clear this pending tail. */
        if (PeekMessageW(&message, 0, 0, 0, PM_NOREMOVE)) continue;
        /* Match Re10's proven split: due actions and completed presentation
           come first. Reader0-only logical preparation may consume remaining
           deadline slack; host frame/raster warming stays disabled. */
        (void)eightvo_page_repeat_prepare_navigation_tail(&win32->app);
        continue;
      }
    }
    if (!win32->app.page_action_waiting_for_present && !update_pending &&
        now < win32->app.page_repeat_next_move_ticks && time_frequency > 0)
    {
      U64 remaining_ticks = win32->app.page_repeat_next_move_ticks - now;
      U64 whole_seconds = remaining_ticks / time_frequency;
      U64 fractional_ticks = remaining_ticks % time_frequency;
      U64 wait_ms = whole_seconds > UINT64_MAX / 1000 ?
        UINT64_MAX : whole_seconds * 1000;
      if (wait_ms < UINT64_MAX)
      {
        U64 fractional_ms =
          (fractional_ticks * 1000 + time_frequency - 1) / time_frequency;
        wait_ms = wait_ms > UINT64_MAX - fractional_ms ?
          UINT64_MAX : wait_ms + fractional_ms;
      }
      DWORD timeout_ms = (DWORD)MIN(wait_ms, (U64)MAXDWORD);
      DWORD wait_result = MsgWaitForMultipleObjectsEx(
        0, 0, timeout_ms, QS_ALLINPUT, MWMO_INPUTAVAILABLE);
      if (wait_result == WAIT_FAILED && probe && probe->enabled)
      {
        probe->failed = 1;
      }
    }
  }
  if (timer_resolution_active) (void)timeEndPeriod(1);
}

FUNCTION B32
eightvo_page_repeat_win32_probe_present_anchor(EightvoWin32 *win32,
                                                 B32 seek_anchor)
{
  if (!win32 || !win32->window) return 0;
  win32->app.persistence_enabled = 0;
  eightvo_stop_page_repeat(&win32->app);
  for (U32 pass = 0;
       pass < 16 && (win32->app.page_action_waiting_for_present ||
                     GetUpdateRect(win32->window, 0, FALSE));
       pass += 1)
  {
    (void)InvalidateRect(win32->window, 0, FALSE);
    (void)UpdateWindow(win32->window);
  }
  if (win32->app.page_action_waiting_for_present ||
      win32->app.page_action_pending)
  {
    fprintf(stderr,
            "eightvo_page_repeat_present_anchor result=fail setup=preexisting_gate gate=%d pending=%d status=%s\n",
            win32->app.page_action_waiting_for_present,
            win32->app.page_action_pending,
            win32->app.status);
    return 0;
  }
  (void)eightvo_save_state(&win32->app);
  win32->app.page_repeat_cancelled_key = 0;
  if (seek_anchor)
  {
    /* Anchor construction is one probe-only setup transaction. The search
       jump and bounded reverse walk intentionally precede the single frame
       that becomes observable to the Win32 queue. */
    B32 prior_internal_dispatch = win32->app.page_action_internal_dispatch;
    win32->app.page_action_internal_dispatch = 1;
    B32 anchor_ok = eightvo_page_repeat_probe_seek_anchor(&win32->app);
    win32->app.page_action_internal_dispatch = prior_internal_dispatch;
    if (!anchor_ok)
    {
      fprintf(stderr,
              "eightvo_page_repeat_present_anchor result=fail setup=anchor status=%s\n",
              win32->app.status);
      return 0;
    }
    eightvo_page_action_note_emitted(&win32->app);
  }
  reader_view_state_init(&win32->app.reader_view_state);
  win32->app.host_focus_control = EightvoHostControl_None;
  win32->app.host_focus_visible = 0;
  (void)InvalidateRect(win32->window, 0, FALSE);
  for (U32 pass = 0;
       pass < 16 && GetUpdateRect(win32->window, 0, FALSE);
       pass += 1)
  {
    (void)UpdateWindow(win32->window);
  }
  B32 result = win32->app.last_present_complete &&
    !win32->app.page_action_waiting_for_present &&
    !win32->app.page_action_pending &&
    eightvo_gotm_navigation_frame_is_canonical_nonempty(
      &win32->app, 0, 0, 0, 0) &&
    !GetUpdateRect(win32->window, 0, FALSE);
  if (!result)
  {
    fprintf(stderr,
            "eightvo_page_repeat_present_anchor result=fail present=%d gate=%d pending=%d update=%d identity_mismatch=%u frame=%llu:%llu expected=%llu:%llu frame_doc=%llu:%llu reader_doc=%llu:%llu frame_page=%u:%llu/%llu:%llu+%llu reader_page=%u:%llu/%llu:%llu-%llu\n",
            win32->app.last_present_complete,
            win32->app.page_action_waiting_for_present,
            win32->app.page_action_pending,
            GetUpdateRect(win32->window, 0, FALSE) != 0,
            win32->app.page_action_identity_mismatch_count,
            (unsigned long long)win32->app.last_surface_identity.frame_generation,
            (unsigned long long)win32->app.last_surface_identity.frame_capture_generation,
            (unsigned long long)win32->app.page_action_expected_identity.frame_generation,
            (unsigned long long)win32->app.page_action_expected_identity.frame_capture_generation,
            (unsigned long long)win32->app.frame.document_id,
            (unsigned long long)win32->app.frame.document_generation,
            (unsigned long long)epub_reader_document_id(&win32->app.reader),
            (unsigned long long)win32->app.reader.document_generation,
            win32->app.frame.spine_index,
            (unsigned long long)win32->app.frame.page_index,
            (unsigned long long)win32->app.frame.page_count,
            (unsigned long long)win32->app.frame.view_byte_offset,
            (unsigned long long)win32->app.frame.visible_text.size,
            win32->app.reader.current_page.spine_index,
            (unsigned long long)win32->app.reader.current_page.spine_page_index,
            (unsigned long long)win32->app.reader.current_page.spine_page_count,
            (unsigned long long)win32->app.reader.current_page.first_byte,
            (unsigned long long)
              win32->app.reader.current_page.one_past_last_byte);
  }
  return result;
}

FUNCTION B32
eightvo_win32_present_until_gate_clear(EightvoWin32 *win32,
                                         U32 pass_cap)
{
  if (!win32 || !win32->window || pass_cap == 0) return 0;
  for (U32 pass = 0; pass < pass_cap; pass += 1)
  {
    if (win32->app.page_action_waiting_for_present ||
        GetUpdateRect(win32->window, 0, FALSE))
    {
      (void)InvalidateRect(win32->window, 0, FALSE);
      (void)UpdateWindow(win32->window);
    }
    if (!win32->app.page_action_waiting_for_present &&
        !win32->app.page_action_pending &&
        !GetUpdateRect(win32->window, 0, FALSE) &&
        win32->app.last_present_complete)
    {
      return 1;
    }
  }
  return 0;
}

FUNCTION B32
eightvo_win32_pump_capture_retry_and_persistence(
  EightvoWin32 *win32,
  U32 retry_scheduled_before,
  U32 retry_fired_before,
  U32 persistence_transaction_before,
  U32 timeout_ms,
  U32 *out_retry_recovered,
  U32 *out_persistence_recovered)
{
  if (out_retry_recovered) *out_retry_recovered = 0;
  if (out_persistence_recovered) *out_persistence_recovered = 0;
  if (!win32 || !win32->window || timeout_ms == 0) return 0;
  U64 frequency = os_time_frequency();
  if (frequency == 0) return 0;
  U64 timeout_ticks = frequency > UINT64_MAX / timeout_ms ?
    UINT64_MAX : (frequency * timeout_ms + 999) / 1000;
  U64 start_ticks = os_time_ticks();
  U64 deadline_ticks = start_ticks > UINT64_MAX - timeout_ticks ?
    UINT64_MAX : start_ticks + timeout_ticks;
  B32 retry_fired_while_gated = 0;
  MSG message = {0};
  while (os_time_ticks() < deadline_ticks)
  {
    DWORD wait_result = MsgWaitForMultipleObjectsEx(
      0, 0, 50, QS_ALLINPUT, MWMO_INPUTAVAILABLE);
    if (wait_result == WAIT_FAILED) return 0;
    while (PeekMessageW(&message, 0, 0, 0, PM_REMOVE))
    {
      if (message.message == WM_QUIT) return 0;
      TranslateMessage(&message);
      DispatchMessageW(&message);
      if (message.hwnd == win32->window && message.message == WM_TIMER &&
          message.wParam == EightvoPresentationRetryTimerId &&
          win32->app.page_action_presentation_retry_fired_count >
            retry_fired_before &&
          win32->app.page_action_waiting_for_present)
      {
        if (win32->app.state_save_transaction_success_count !=
              persistence_transaction_before)
          return 0;
        retry_fired_while_gated = 1;
      }
    }
    B32 stable_page =
      !win32->app.page_action_waiting_for_present &&
      !win32->app.page_action_pending &&
      win32->app.last_present_complete &&
      win32->app.last_surface_identity.kind ==
        EightvoPresentationIdentity_Page &&
      eightvo_canonical_page_identity_equal(
        win32->app.last_surface_identity.page,
        eightvo_canonical_page_identity(win32->app.reader.current_page));
    B32 retry_recovered = retry_fired_while_gated && stable_page &&
      win32->app.page_action_presentation_retry_scheduled_count >
        retry_scheduled_before &&
      win32->app.page_action_presentation_retry_fired_count > retry_fired_before;
    B32 persistence_recovered = retry_recovered &&
      !win32->app.state_save_pending &&
      win32->app.state_save_transaction_success_count ==
        persistence_transaction_before + 1;
    if (persistence_recovered)
    {
      MemoryZeroStruct(&win32->app.saved);
      eightvo_load_state(&win32->app);
      EightvoLibraryEntry *entry = eightvo_library_catalog_find_path(
        &win32->app.library, win32->app.current_path);
      persistence_recovered = win32->app.saved.valid && entry &&
        win32->app.saved.spine_index == win32->app.reader.active_spine_index &&
        win32->app.saved.byte_offset == win32->app.reader.view_byte_offset &&
        entry->progress_spine_index == win32->app.reader.active_spine_index &&
        entry->progress_byte_offset == win32->app.reader.view_byte_offset;
    }
    if (retry_recovered && persistence_recovered)
    {
      if (out_retry_recovered) *out_retry_recovered = 1;
      if (out_persistence_recovered) *out_persistence_recovered = 1;
      return 1;
    }
  }
  return 0;
}

FUNCTION B32
eightvo_win32_capture_failure_gate_regression(
  EightvoWin32 *win32,
  const char *path,
  U32 *out_recovered,
  U32 *out_same_page_recovered,
  U32 *out_open_catalog_recovered,
  U32 *out_retry_recovered,
  U32 *out_persistence_recovered)
{
  if (out_recovered) *out_recovered = 0;
  if (out_same_page_recovered) *out_same_page_recovered = 0;
  if (out_open_catalog_recovered) *out_open_catalog_recovered = 0;
  if (out_retry_recovered) *out_retry_recovered = 0;
  if (out_persistence_recovered) *out_persistence_recovered = 0;
  if (!win32 || !path || !path[0] ||
      !eightvo_page_repeat_win32_probe_present_anchor(win32, 1))
    return 0;

  U32 recovery_before = win32->app.capture_frame_recovery_count;
  U32 mismatch_before = win32->app.page_action_identity_mismatch_count;
  EightvoCanonicalPageIdentity page_before =
    eightvo_canonical_page_identity(win32->app.reader.current_page);
  win32->app.persistence_enabled = 1;
  if (!eightvo_save_state(&win32->app)) return 0;
  U32 persistence_transaction_before =
    win32->app.state_save_transaction_success_count;
  U32 retry_scheduled_before =
    win32->app.page_action_presentation_retry_scheduled_count;
  U32 retry_fired_before =
    win32->app.page_action_presentation_retry_fired_count;
  win32->app.capture_frame_fail_count = 2;
  if (!PostMessageW(win32->window, WM_KEYDOWN, VK_RIGHT, 1) ||
      !PostMessageW(win32->window, WM_KEYUP, VK_RIGHT, (LPARAM)0xC0000001))
    return 0;
  U32 retry_recovered = 0;
  U32 persistence_recovered = 0;
  B32 pumped = eightvo_win32_pump_capture_retry_and_persistence(
    win32,
    retry_scheduled_before,
    retry_fired_before,
    persistence_transaction_before,
    2000,
    &retry_recovered,
    &persistence_recovered);
  B32 page_changed = win32->app.reader.has_current_page &&
    !eightvo_canonical_page_identity_equal(
      page_before,
      eightvo_canonical_page_identity(win32->app.reader.current_page));
  B32 page_recovered = pumped && page_changed && retry_recovered &&
    persistence_recovered && !win32->app.page_action_waiting_for_present &&
    win32->app.capture_frame_recovery_count == recovery_before + 1 &&
    win32->app.page_action_identity_mismatch_count == mismatch_before + 1;
  if (!page_recovered) return 0;
  if (out_recovered) *out_recovered = 1;
  if (out_retry_recovered) *out_retry_recovered = retry_recovered;
  if (out_persistence_recovered) *out_persistence_recovered =
    persistence_recovered;

  U64 same_page_capture_before = win32->app.frame_capture_generation;
  mismatch_before = win32->app.page_action_identity_mismatch_count;
  SourceReaderPageRange same_page = win32->app.reader.current_page;
  win32->app.capture_frame_fail_count = 2;
  eightvo_apply_reader_view_action(&win32->app, &(ReaderViewAction){
    .kind = ReaderViewAction_FindChanged,
    .text = eightvo_reader_view_text("same-page capture recovery"),
  });
  B32 same_page_recovered =
    win32->app.page_action_waiting_for_present &&
    eightvo_win32_present_until_gate_clear(win32, 16) &&
    eightvo_page_repeat_probe_page_equal(
      same_page, win32->app.reader.current_page) &&
    win32->app.frame_capture_generation > same_page_capture_before &&
    win32->app.page_action_identity_mismatch_count == mismatch_before + 1;
  if (!same_page_recovered) return 0;
  if (out_same_page_recovered) *out_same_page_recovered = 1;

  U32 catalog_count_before_open = win32->app.library.entry_count;
  if (!eightvo_close_book(&win32->app) ||
      !eightvo_win32_present_until_gate_clear(win32, 16) ||
      epub_reader_is_open(&win32->app.reader))
  {
    return 0;
  }
  U64 closed_document_generation = win32->app.reader.document_generation;
  U64 open_capture_before = win32->app.frame_capture_generation;
  U32 open_recovery_before = win32->app.capture_frame_recovery_count;
  mismatch_before = win32->app.page_action_identity_mismatch_count;
  win32->app.capture_frame_fail_count = 2;
  B32 open_result = eightvo_open_path(&win32->app, path);
  EightvoLibraryEntry *catalog_entry =
    eightvo_library_catalog_find_path(&win32->app.library,
                                        win32->app.current_path);
  B32 open_catalog_recovered = open_result && catalog_entry &&
    epub_reader_is_open(&win32->app.reader) &&
    win32->app.reader.document_generation != closed_document_generation &&
    win32->app.library.entry_count == catalog_count_before_open &&
    win32->app.page_action_waiting_for_present &&
    eightvo_win32_present_until_gate_clear(win32, 16) &&
    win32->app.frame_capture_generation > open_capture_before &&
    win32->app.capture_frame_recovery_count == open_recovery_before + 1 &&
    win32->app.last_surface_identity.kind ==
      EightvoPresentationIdentity_Page &&
    win32->app.last_surface_identity.document_generation ==
      win32->app.reader.document_generation &&
    eightvo_canonical_page_identity_equal(
      win32->app.last_surface_identity.page,
      eightvo_canonical_page_identity(win32->app.reader.current_page)) &&
    win32->app.capture_frame_fail_count == 0;
  if (!open_catalog_recovered)
  {
    fprintf(stderr,
            "eightvo_capture_failure_open result=fail open=%d catalog=%d reader_open=%d generation=%llu/%llu catalog_count=%u/%u gate=%d mismatch=%u/%u capture=%llu/%llu recovery=%u/%u failures_left=%u status=%s\n",
            open_result,
            catalog_entry != 0,
            epub_reader_is_open(&win32->app.reader),
            (unsigned long long)win32->app.reader.document_generation,
            (unsigned long long)closed_document_generation,
            win32->app.library.entry_count,
            catalog_count_before_open,
            win32->app.page_action_waiting_for_present,
            win32->app.page_action_identity_mismatch_count,
            mismatch_before,
            (unsigned long long)win32->app.frame_capture_generation,
            (unsigned long long)open_capture_before,
            win32->app.capture_frame_recovery_count,
            open_recovery_before,
            win32->app.capture_frame_fail_count,
            win32->app.status);
  }
  if (out_open_catalog_recovered) *out_open_catalog_recovered =
    open_catalog_recovered;
  return open_catalog_recovered;
}

FUNCTION B32
eightvo_win32_current_image_page_gate_is_exact(EightvoWin32 *win32)
{
  if (!win32 || !epub_reader_is_open(&win32->app.reader) ||
      win32->app.frame.image_count == 0)
    return 0;
  B32 image_identity_valid = 0;
  U64 image_identity = eightvo_frame_image_visual_identity(
    &win32->app.frame, &image_identity_valid);
  EightvoPresentationIdentity visible = win32->app.last_surface_identity;
  return image_identity_valid && image_identity != 0 &&
    visible.kind == EightvoPresentationIdentity_Page &&
    visible.frame_generation == win32->app.page_action_frame_generation &&
    visible.frame_capture_generation == win32->app.frame_capture_generation &&
    visible.reader_frame_generation == win32->app.frame.generation &&
    visible.document_id == epub_reader_document_id(&win32->app.reader) &&
    visible.document_generation == win32->app.reader.document_generation &&
    visible.layout_generation ==
      epub_reader_layout_key_generation(win32->app.layout_key) &&
    visible.image_count == win32->app.frame.image_count &&
    visible.image_visual_identity == image_identity &&
    eightvo_canonical_page_identity_equal(
      visible.page,
      eightvo_canonical_page_identity(win32->app.reader.current_page));
}

FUNCTION B32
eightvo_win32_image_page_gate_regression(EightvoWin32 *win32,
                                           U32 *out_image_pages)
{
  if (out_image_pages) *out_image_pages = 0;
  if (!win32 || !epub_reader_is_open(&win32->app.reader)) return 0;

  EpubReaderResult cover_navigation = eightvo_navigate_to_location(
    &win32->app, 0, 0, EpubReaderNavigationReason_Location);
  if (cover_navigation != EpubReaderResult_Ok ||
      !eightvo_win32_present_until_gate_clear(win32, 16) ||
      !eightvo_win32_current_image_page_gate_is_exact(win32))
  {
    fprintf(stderr,
            "eightvo_image_page_gate result=fail case=cover navigation=%d spine=%u images=%u expected_kind=%d expected_images=%u expected_identity=%llu expected_frame=%llu:%llu visible_kind=%d visible_images=%u visible_identity=%llu visible_frame=%llu:%llu gate=%d present=%d mismatch=%u frame_doc=%llu:%llu reader_doc=%llu:%llu frame_page=%u:%llu/%llu:%llu+%llu reader_page=%u:%llu/%llu:%llu-%llu\n",
            (int)cover_navigation,
            win32->app.reader.active_spine_index,
            win32->app.frame.image_count,
            (int)win32->app.page_action_expected_identity.kind,
            win32->app.page_action_expected_identity.image_count,
            (unsigned long long)
              win32->app.page_action_expected_identity.image_visual_identity,
            (unsigned long long)
              win32->app.page_action_expected_identity.frame_generation,
            (unsigned long long)
              win32->app.page_action_expected_identity.frame_capture_generation,
            (int)win32->app.last_surface_identity.kind,
            win32->app.last_surface_identity.image_count,
            (unsigned long long)
              win32->app.last_surface_identity.image_visual_identity,
            (unsigned long long)
              win32->app.last_surface_identity.frame_generation,
            (unsigned long long)
              win32->app.last_surface_identity.frame_capture_generation,
            win32->app.page_action_waiting_for_present,
            win32->app.last_present_complete,
            win32->app.page_action_identity_mismatch_count,
            (unsigned long long)win32->app.frame.document_id,
            (unsigned long long)win32->app.frame.document_generation,
            (unsigned long long)epub_reader_document_id(&win32->app.reader),
            (unsigned long long)win32->app.reader.document_generation,
            win32->app.frame.spine_index,
            (unsigned long long)win32->app.frame.page_index,
            (unsigned long long)win32->app.frame.page_count,
            (unsigned long long)win32->app.frame.view_byte_offset,
            (unsigned long long)win32->app.frame.visible_text.size,
            win32->app.reader.current_page.spine_index,
            (unsigned long long)win32->app.reader.current_page.spine_page_index,
            (unsigned long long)win32->app.reader.current_page.spine_page_count,
            (unsigned long long)win32->app.reader.current_page.first_byte,
            (unsigned long long)
              win32->app.reader.current_page.one_past_last_byte);
    return 0;
  }
  U32 image_pages = 1;

  U32 nav_count = 0;
  EpubReaderNavPointResult navigation = {0};
  EpubReaderResult maps_navigation = EpubReaderResult_DocError;
  if (doc_engine_get_nav_point_count(epub_reader_engine(&win32->app.reader),
                                     epub_reader_document_id(&win32->app.reader),
                                     &nav_count) == DocError_Ok &&
      nav_count > 0)
  {
    maps_navigation = eightvo_navigate_to_nav_point(
      &win32->app, 0, &navigation);
  }
  if (maps_navigation != EpubReaderResult_Ok ||
      win32->app.reader.active_spine_index != 9 ||
      !eightvo_win32_present_until_gate_clear(win32, 16) ||
      !eightvo_win32_current_image_page_gate_is_exact(win32))
  {
    fprintf(stderr,
            "eightvo_image_page_gate result=fail case=maps_1 navigation=%d nav_count=%u spine=%u images=%u visible_images=%u visible_identity=%llu gate=%d\n",
            (int)maps_navigation,
            nav_count,
            win32->app.reader.active_spine_index,
            win32->app.frame.image_count,
            win32->app.last_surface_identity.image_count,
            (unsigned long long)
              win32->app.last_surface_identity.image_visual_identity,
            win32->app.page_action_waiting_for_present);
    return 0;
  }
  image_pages += 1;

  for (U32 map_index = 2; map_index <= 3; map_index += 1)
  {
    EpubReaderResult move = eightvo_move_page(&win32->app, 1);
    if (move != EpubReaderResult_Ok ||
        !eightvo_win32_present_until_gate_clear(win32, 16) ||
        !eightvo_win32_current_image_page_gate_is_exact(win32))
    {
      fprintf(stderr,
              "eightvo_image_page_gate result=fail case=maps_%u move=%d spine=%u images=%u visible_images=%u visible_identity=%llu gate=%d\n",
              map_index,
              (int)move,
              win32->app.reader.active_spine_index,
              win32->app.frame.image_count,
              win32->app.last_surface_identity.image_count,
              (unsigned long long)
                win32->app.last_surface_identity.image_visual_identity,
              win32->app.page_action_waiting_for_present);
      return 0;
    }
    image_pages += 1;
  }
  if (out_image_pages) *out_image_pages = image_pages;
  return image_pages == 4;
}

FUNCTION B32
eightvo_page_repeat_win32_run_probe(
  EightvoWin32 *win32,
  const char *path,
  EightvoPageRepeatWin32ProbeKind kind,
  S32 direction,
  const SourceReaderPageRange *expected_pages,
  U32 expected_page_count,
  B32 seek_anchor,
  EightvoPageRepeatWin32Probe *out_probe)
{
  if (out_probe) MemoryZeroStruct(out_probe);
  if (!win32 || !win32->window || !path || !path[0] ||
      !out_probe || direction == 0)
  {
    return 0;
  }

  EightvoPageRepeatWin32Probe probe = {0};
  probe.minimum_text_bytes = UINT64_MAX;
  probe.minimum_text_rows = UINT32_MAX;
  probe.enabled = 1;
  probe.kind = kind;
  probe.direction = direction < 0 ? -1 : 1;
  probe.key = probe.direction < 0 ? VK_LEFT : VK_RIGHT;
  probe.target_frame_move_count =
    kind == EightvoPageRepeatWin32Probe_Direction ?
      EightvoPageRepeatProbeMoveCount : 0;
  probe.frame_cap =
    kind == EightvoPageRepeatWin32Probe_Direction ? 200 : 16;
  probe.native_repeats_per_frame = 8;
  if (kind == EightvoPageRepeatWin32Probe_Direction)
  {
    if (!expected_pages ||
        expected_page_count != ARRAY_COUNT(probe.expected_pages))
    {
      *out_probe = probe;
      return 0;
    }
    probe.expected_page_count = expected_page_count;
    for (U32 index = 0; index < expected_page_count; index += 1)
      probe.expected_pages[index] = expected_pages[index];
  }
  else if (kind == EightvoPageRepeatWin32Probe_MutationGate)
  {
    if (!expected_pages || expected_page_count != 1)
    {
      *out_probe = probe;
      return 0;
    }
    probe.expected_page_count = 1;
    probe.expected_pages[0] = expected_pages[0];
  }
  if (!eightvo_page_repeat_win32_probe_present_anchor(win32, seek_anchor))
  {
    *out_probe = probe;
    return 0;
  }
  probe.start_page = win32->app.reader.current_page;

  win32->app.persistence_enabled = 1;
  if (!eightvo_page_repeat_probe_prepare_persistence(&win32->app, &probe))
  {
    probe.failed = 1;
    win32->app.persistence_enabled = 0;
    *out_probe = probe;
    return 0;
  }
  if (win32->page_repeat_probe_aux_paint_pending_count != 0 ||
      win32->page_repeat_probe_main_null_paint_pending_count != 0)
  {
    probe.failed = 1;
    win32->app.persistence_enabled = 0;
    (void)eightvo_save_state(&win32->app);
    *out_probe = probe;
    return 0;
  }
  probe.auxiliary_paint_dispatch_before =
    win32->page_repeat_probe_aux_paint_dispatch_count;
  probe.main_null_paint_dispatch_before =
    win32->page_repeat_probe_main_null_paint_dispatch_count;
  probe.page_action_emitted_before = win32->app.page_action_emitted_count;
  probe.page_action_presented_before = win32->app.page_action_presented_count;
  probe.page_action_overlap_before = win32->app.page_action_overlap_count;
  probe.page_action_identity_mismatch_before =
    win32->app.page_action_identity_mismatch_count;
  probe.page_action_mutation_drop_before =
    win32->app.page_action_mutation_drop_count;
  probe.start_ticks = os_time_ticks();
  if (!PostMessageW(win32->window, WM_KEYDOWN, probe.key, 1) ||
      !eightvo_page_repeat_win32_probe_post_native(&win32->app, &probe) ||
      (kind == EightvoPageRepeatWin32Probe_MutationGate &&
       !eightvo_page_repeat_win32_probe_post_mutations(&win32->app)))
  {
    probe.failed = 1;
    win32->app.persistence_enabled = 0;
    (void)eightvo_save_state(&win32->app);
    *out_probe = probe;
    return 0;
  }

  eightvo_win32_run_message_loop(win32, &probe);
  win32->app.persistence_enabled = 0;
  (void)eightvo_save_state(&win32->app);

  if (probe.kind == EightvoPageRepeatWin32Probe_Direction)
  {
    U64 frequency = os_time_frequency();
    for (U32 index = 0;
         index < probe.stable_present_transition_count;
         index += 1)
    {
      SourceReaderPageRange from_page = probe.actual_pages[index];
      SourceReaderPageRange to_page = probe.actual_pages[index + 1];
      double interval_ms = frequency > 0 ?
        1000.0 * (double)probe.stable_present_transition_ticks[index] /
          (double)frequency : 0.0;
      fprintf(stderr,
              "eightvo_page_repeat_transition direction=%d index=%u "
              "from=%u:%llu-%llu to=%u:%llu-%llu cross_spine=%d "
              "initial_delay=%d interval_ms=%.3f\n",
              probe.direction,
              index,
              from_page.spine_index,
              (unsigned long long)from_page.first_byte,
              (unsigned long long)from_page.one_past_last_byte,
              to_page.spine_index,
              (unsigned long long)to_page.first_byte,
              (unsigned long long)to_page.one_past_last_byte,
              probe.stable_present_transition_cross_spine[index],
              index == 0,
              interval_ms);
    }
    double elapsed_ms = frequency > 0 ?
      1000.0 * (double)probe.elapsed_ticks / (double)frequency : 0.0;
    double frame_max_ms = frequency > 0 ?
      1000.0 * (double)probe.frame_max_ticks / (double)frequency : 0.0;
    double first_move_ms = frequency > 0 ?
      1000.0 * (double)probe.first_move_elapsed_ticks /
        (double)frequency : 0.0;
    double move_interval_min_ms = frequency > 0 ?
      1000.0 * (double)probe.move_interval_min_ticks /
        (double)frequency : 0.0;
    double move_interval_max_ms = frequency > 0 ?
      1000.0 * (double)probe.move_interval_max_ticks /
        (double)frequency : 0.0;
    double immediate_visible_ms = frequency > 0 &&
      probe.stable_present_count > 0 ?
        1000.0 * (double)probe.stable_present_elapsed_ticks[0] /
          (double)frequency : 0.0;
    double visible_first_delay_ms = frequency > 0 ?
      1000.0 * (double)probe.stable_present_first_delay_ticks /
        (double)frequency : 0.0;
    double visible_interval_min_ms = frequency > 0 ?
      1000.0 * (double)probe.stable_present_interval_min_ticks /
        (double)frequency : 0.0;
    double visible_interval_max_ms = frequency > 0 ?
      1000.0 * (double)probe.stable_present_interval_max_ticks /
        (double)frequency : 0.0;
    double visible_elapsed_ms = frequency > 0 &&
      probe.stable_present_count == EightvoPageRepeatProbePageCount ?
        1000.0 * (double)(
          probe.stable_present_elapsed_ticks[
            EightvoPageRepeatProbePageCount - 1] -
          probe.stable_present_elapsed_ticks[0]) / (double)frequency : 0.0;
    double nominal_first_move_ms =
      1000.0 * EightvoPageRepeatInitialFrames /
        EightvoPageRepeatFrameRate;
    double nominal_move_interval_ms =
      1000.0 * EightvoPageRepeatIntervalFrames /
        EightvoPageRepeatFrameRate;
    double nominal_elapsed_ms = nominal_first_move_ms +
      (probe.target_frame_move_count - 1) * nominal_move_interval_ms;
    double repeat_prepare_max_ms = frequency > 0 ?
      1000.0 * (double)probe.repeat_prepare_max_ticks /
        (double)frequency : 0.0;
    double repeat_render_max_ms = frequency > 0 ?
      1000.0 * (double)probe.repeat_render_max_ticks /
        (double)frequency : 0.0;
    double repeat_present_max_ms = frequency > 0 ?
      1000.0 * (double)probe.repeat_present_max_ticks /
        (double)frequency : 0.0;
    double move_prepare_budget_ms = 1000.0 / 60.0;
    double render_budget_ms = 48.0;
    double present_budget_ms = 1000.0 / 60.0;
    B32 timing_ok = frequency > 0 &&
      elapsed_ms >= nominal_elapsed_ms * 0.75 &&
      elapsed_ms <= nominal_elapsed_ms +
        EightvoPageRepeatProbeTimingToleranceMs &&
      first_move_ms >= nominal_first_move_ms * 0.75 &&
      first_move_ms <= nominal_first_move_ms +
        EightvoPageRepeatProbeTimingToleranceMs &&
      probe.move_interval_count == probe.target_frame_move_count - 1 &&
      move_interval_min_ms >= nominal_move_interval_ms * 0.5 &&
      move_interval_max_ms <= nominal_move_interval_ms +
        EightvoPageRepeatProbeIntervalToleranceMs &&
      probe.stable_present_count == EightvoPageRepeatProbePageCount &&
      immediate_visible_ms < EightvoPageRepeatProbeFrameBudgetMs &&
      visible_first_delay_ms >= nominal_first_move_ms * 0.75 &&
      visible_first_delay_ms <= nominal_first_move_ms +
        EightvoPageRepeatProbeTimingToleranceMs &&
      probe.stable_present_interval_count ==
        EightvoPageRepeatProbeMoveCount - 1 &&
      visible_interval_min_ms >= nominal_move_interval_ms * 0.5 &&
      visible_interval_max_ms <= nominal_move_interval_ms +
        EightvoPageRepeatProbeIntervalToleranceMs &&
      visible_elapsed_ms >= nominal_elapsed_ms * 0.75 &&
      visible_elapsed_ms <= nominal_elapsed_ms +
        EightvoPageRepeatProbeTimingToleranceMs &&
      repeat_prepare_max_ms < move_prepare_budget_ms &&
      repeat_render_max_ms < render_budget_ms &&
      repeat_present_max_ms < present_budget_ms &&
      frame_max_ms < EightvoPageRepeatProbeFrameBudgetMs;
    probe.completed = probe.completed && timing_ok;
  }
  *out_probe = probe;
  return probe.completed;
}

FUNCTION int
eightvo_run_window_internal(const char *initial_path,
                             B32 page_repeat_probe_enabled)
{
  EightvoWin32 win32 = {0};
  B32 page_repeat_sandbox_ready = 0;
  S32 initial_width = page_repeat_probe_enabled ?
    EightvoPageRepeatProbeWidth : 1100;
  S32 initial_height = page_repeat_probe_enabled ?
    EightvoPageRepeatProbeHeight : 760;
  if (!eightvo_app_init(&win32.app,
                         initial_width,
                         initial_height,
                         1,
                         !page_repeat_probe_enabled))
  {
    return 1;
  }

  (void)SetProcessDPIAware();
  HINSTANCE instance = GetModuleHandleW(0);
  WNDCLASSW window_class = {0};
  window_class.lpfnWndProc = eightvo_win32_proc;
  window_class.hInstance = instance;
  window_class.lpszClassName = L"EightvoWindow";
  window_class.hCursor = LoadCursorW(0, IDC_ARROW);
  window_class.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
  if (!RegisterClassW(&window_class))
  {
    eightvo_app_release(&win32.app);
    return 1;
  }

  DWORD window_style = WS_OVERLAPPEDWINDOW | WS_VISIBLE;
  DWORD window_ex_style = page_repeat_probe_enabled ?
    WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE : 0;
  RECT rect = {0, 0, win32.app.width, win32.app.height};
  AdjustWindowRect(&rect, WS_OVERLAPPEDWINDOW, FALSE);
  win32.window = CreateWindowExW(window_ex_style,
                                 window_class.lpszClassName,
                                 L"8vo",
                                 window_style,
                                 page_repeat_probe_enabled ? -32000 :
                                   CW_USEDEFAULT,
                                 page_repeat_probe_enabled ? -32000 :
                                   CW_USEDEFAULT,
                                 rect.right - rect.left,
                                 rect.bottom - rect.top,
                                 0,
                                 0,
                                 instance,
                                 &win32);
  if (!win32.window)
  {
    eightvo_app_release(&win32.app);
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
    eightvo_app_release(&win32.app);
    return 1;
  }
  win32.app.gfx_ready = 1;
  if (initial_path && initial_path[0] &&
      !eightvo_open_path(&win32.app, initial_path))
  {
    if (page_repeat_probe_enabled)
      fprintf(stderr,
              "eightvo_page_repeat_win32_smoke result=fail reason=open\n");
    DestroyWindow(win32.window);
    eightvo_app_release(&win32.app);
    return 1;
  }
  if (page_repeat_probe_enabled)
  {
    page_repeat_sandbox_ready =
      eightvo_page_repeat_probe_set_sandbox_paths(&win32.app);
    if (!page_repeat_sandbox_ready)
    {
      fprintf(stderr,
              "eightvo_page_repeat_win32_smoke result=fail reason=sandbox_persistence\n");
      (void)eightvo_page_repeat_probe_cleanup_sandbox(&win32.app);
      DestroyWindow(win32.window);
      eightvo_app_release(&win32.app);
      return 1;
    }
    WNDCLASSW paint_probe_class = {0};
    paint_probe_class.lpfnWndProc = eightvo_page_repeat_probe_paint_proc;
    paint_probe_class.hInstance = instance;
    paint_probe_class.lpszClassName = L"EightvoPageRepeatPaintProbeWindow";
    if (!RegisterClassW(&paint_probe_class))
    {
      fprintf(stderr,
              "eightvo_page_repeat_win32_smoke result=fail reason=paint_probe_register\n");
      (void)eightvo_page_repeat_probe_cleanup_sandbox(&win32.app);
      DestroyWindow(win32.window);
      eightvo_app_release(&win32.app);
      return 1;
    }
    win32.page_repeat_probe_paint_window = CreateWindowExW(
      WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE,
      paint_probe_class.lpszClassName,
      L"eightvo page-repeat paint probe",
      WS_OVERLAPPEDWINDOW | WS_VISIBLE,
      -32000,
      -32000,
      32,
      32,
      0,
      0,
      instance,
      &win32);
    if (!win32.page_repeat_probe_paint_window)
    {
      fprintf(stderr,
              "eightvo_page_repeat_win32_smoke result=fail reason=paint_probe_window\n");
      (void)UnregisterClassW(paint_probe_class.lpszClassName, instance);
      (void)eightvo_page_repeat_probe_cleanup_sandbox(&win32.app);
      DestroyWindow(win32.window);
      eightvo_app_release(&win32.app);
      return 1;
    }
    (void)UpdateWindow(win32.page_repeat_probe_paint_window);
    win32.page_repeat_probe_track_paints = 1;
  }

  if (page_repeat_probe_enabled)
  {
    U64 frequency = os_time_frequency();
    EightvoPageRepeatWin32Probe forward = {0};
    EightvoPageRepeatWin32Probe backward = {0};
    EightvoPageRepeatWin32Probe focus = {0};
    EightvoPageRepeatWin32Probe control_modifier = {0};
    EightvoPageRepeatWin32Probe shift_modifier = {0};
    EightvoPageRepeatWin32Probe system_modifier = {0};
    EightvoPageRepeatWin32Probe deactivate = {0};
    EightvoPageRepeatWin32Probe mutation_gate = {0};
    SourceReaderPageRange expected_forward[EightvoPageRepeatProbePageCount] =
      {0};
    SourceReaderPageRange expected_backward[EightvoPageRepeatProbePageCount] =
      {0};
    SourceReaderPageRange reversal_anchor = {0};
    SourceReaderPageRange reversal_endpoint = {0};
    B32 expected_ok = eightvo_page_repeat_probe_build_expected(
      initial_path,
      expected_forward,
      expected_backward,
      &reversal_anchor,
      &reversal_endpoint);

    B32 forward_ok = expected_ok && eightvo_page_repeat_win32_run_probe(
      &win32,
      initial_path,
      EightvoPageRepeatWin32Probe_Direction,
      1,
      expected_forward,
      ARRAY_COUNT(expected_forward),
      1,
      &forward);
    B32 backward_ok = forward_ok &&
      eightvo_page_repeat_probe_page_equal(win32.app.reader.current_page,
                                             reversal_endpoint) &&
      eightvo_page_repeat_win32_run_probe(
      &win32,
      initial_path,
      EightvoPageRepeatWin32Probe_Direction,
      -1,
      expected_backward,
      ARRAY_COUNT(expected_backward),
      0,
      &backward);
    B32 returned_to_anchor = backward_ok &&
      eightvo_page_repeat_probe_page_equal(win32.app.reader.current_page,
                                             reversal_anchor);
    B32 focus_ok = eightvo_page_repeat_win32_run_probe(
      &win32,
      initial_path,
      EightvoPageRepeatWin32Probe_FocusLoss,
      1,
      0,
      0,
      1,
      &focus);
    B32 control_modifier_ok = eightvo_page_repeat_win32_run_probe(
      &win32,
      initial_path,
      EightvoPageRepeatWin32Probe_ControlModifier,
      1,
      0,
      0,
      1,
      &control_modifier);
    B32 shift_modifier_ok = eightvo_page_repeat_win32_run_probe(
      &win32,
      initial_path,
      EightvoPageRepeatWin32Probe_ShiftModifier,
      1,
      0,
      0,
      1,
      &shift_modifier);
    B32 system_modifier_ok = eightvo_page_repeat_win32_run_probe(
      &win32,
      initial_path,
      EightvoPageRepeatWin32Probe_SystemModifier,
      1,
      0,
      0,
      1,
      &system_modifier);
    B32 deactivate_ok = eightvo_page_repeat_win32_run_probe(
      &win32,
      initial_path,
      EightvoPageRepeatWin32Probe_Deactivation,
      1,
      0,
      0,
      1,
      &deactivate);
    B32 mutation_gate_ok = eightvo_page_repeat_win32_run_probe(
      &win32,
      initial_path,
      EightvoPageRepeatWin32Probe_MutationGate,
      1,
      expected_forward,
      1,
      1,
      &mutation_gate);
    U32 capture_page_recovered = 0;
    U32 capture_same_page_recovered = 0;
    U32 capture_open_catalog_recovered = 0;
    U32 capture_retry_recovered = 0;
    U32 capture_persistence_recovered = 0;
    B32 capture_failure_ok = mutation_gate_ok &&
      eightvo_win32_capture_failure_gate_regression(
        &win32,
        initial_path,
        &capture_page_recovered,
        &capture_same_page_recovered,
        &capture_open_catalog_recovered,
        &capture_retry_recovered,
        &capture_persistence_recovered);
    U32 image_gate_pages = 0;
    B32 image_gate_ok = capture_failure_ok &&
      eightvo_win32_image_page_gate_regression(&win32,
                                                 &image_gate_pages);

    double forward_first_move_ms = frequency > 0 ?
      1000.0 * (double)forward.first_move_elapsed_ticks /
        (double)frequency : 0.0;
    double forward_interval_min_ms = frequency > 0 ?
      1000.0 * (double)forward.move_interval_min_ticks /
        (double)frequency : 0.0;
    double forward_interval_max_ms = frequency > 0 ?
      1000.0 * (double)forward.move_interval_max_ticks /
        (double)frequency : 0.0;
    double forward_interval_avg_ms =
      frequency > 0 && forward.move_interval_count > 0 ?
        1000.0 * (double)forward.move_interval_total_ticks /
          ((double)frequency * forward.move_interval_count) : 0.0;
    double forward_elapsed_ms = frequency > 0 ?
      1000.0 * (double)forward.elapsed_ticks /
        (double)frequency : 0.0;
    double forward_immediate_visible_ms = frequency > 0 &&
      forward.stable_present_count > 0 ?
        1000.0 * (double)forward.stable_present_elapsed_ticks[0] /
          (double)frequency : 0.0;
    double forward_visible_first_delay_ms = frequency > 0 ?
      1000.0 * (double)forward.stable_present_first_delay_ticks /
        (double)frequency : 0.0;
    double forward_visible_interval_min_ms = frequency > 0 ?
      1000.0 * (double)forward.stable_present_interval_min_ticks /
        (double)frequency : 0.0;
    double forward_visible_interval_avg_ms =
      frequency > 0 && forward.stable_present_interval_count > 0 ?
        1000.0 * (double)forward.stable_present_interval_total_ticks /
          ((double)frequency * forward.stable_present_interval_count) : 0.0;
    double forward_visible_interval_max_ms = frequency > 0 ?
      1000.0 * (double)forward.stable_present_interval_max_ticks /
        (double)frequency : 0.0;
    double forward_visible_elapsed_ms = frequency > 0 &&
      forward.stable_present_count == EightvoPageRepeatProbePageCount ?
        1000.0 * (double)(forward.stable_present_elapsed_ticks[
          EightvoPageRepeatProbePageCount - 1] -
          forward.stable_present_elapsed_ticks[0]) /
          (double)frequency : 0.0;
    double forward_frame_max_ms = frequency > 0 ?
      1000.0 * (double)forward.frame_max_ticks /
        (double)frequency : 0.0;
    double forward_cold_render_ms = frequency > 0 ?
      1000.0 * (double)forward.cold_render_ticks /
        (double)frequency : 0.0;
    double forward_cold_present_ms = frequency > 0 ?
      1000.0 * (double)forward.cold_present_ticks /
        (double)frequency : 0.0;
    double forward_repeat_prepare_avg_ms =
      frequency > 0 && forward.repeat_timing_count > 0 ?
        1000.0 * (double)forward.repeat_prepare_total_ticks /
          ((double)frequency * forward.repeat_timing_count) : 0.0;
    double forward_repeat_prepare_max_ms = frequency > 0 ?
      1000.0 * (double)forward.repeat_prepare_max_ticks /
        (double)frequency : 0.0;
    double forward_repeat_render_avg_ms =
      frequency > 0 && forward.repeat_timing_count > 0 ?
        1000.0 * (double)forward.repeat_render_total_ticks /
          ((double)frequency * forward.repeat_timing_count) : 0.0;
    double forward_repeat_render_max_ms = frequency > 0 ?
      1000.0 * (double)forward.repeat_render_max_ticks /
        (double)frequency : 0.0;
    double forward_repeat_present_avg_ms =
      frequency > 0 && forward.repeat_timing_count > 0 ?
        1000.0 * (double)forward.repeat_present_total_ticks /
          ((double)frequency * forward.repeat_timing_count) : 0.0;
    double forward_repeat_present_max_ms = frequency > 0 ?
      1000.0 * (double)forward.repeat_present_max_ticks /
        (double)frequency : 0.0;
    double forward_navigation_prepare_avg_ms =
      frequency > 0 && forward.navigation_prepare_call_count > 0 ?
        1000.0 * (double)forward.navigation_prepare_total_ticks /
          ((double)frequency * forward.navigation_prepare_call_count) : 0.0;
    double forward_navigation_prepare_max_ms = frequency > 0 ?
      1000.0 * (double)forward.navigation_prepare_max_ticks /
        (double)frequency : 0.0;
    double backward_first_move_ms = frequency > 0 ?
      1000.0 * (double)backward.first_move_elapsed_ticks /
        (double)frequency : 0.0;
    double backward_interval_min_ms = frequency > 0 ?
      1000.0 * (double)backward.move_interval_min_ticks /
        (double)frequency : 0.0;
    double backward_interval_max_ms = frequency > 0 ?
      1000.0 * (double)backward.move_interval_max_ticks /
        (double)frequency : 0.0;
    double backward_interval_avg_ms =
      frequency > 0 && backward.move_interval_count > 0 ?
        1000.0 * (double)backward.move_interval_total_ticks /
          ((double)frequency * backward.move_interval_count) : 0.0;
    double backward_elapsed_ms = frequency > 0 ?
      1000.0 * (double)backward.elapsed_ticks /
        (double)frequency : 0.0;
    double backward_immediate_visible_ms = frequency > 0 &&
      backward.stable_present_count > 0 ?
        1000.0 * (double)backward.stable_present_elapsed_ticks[0] /
          (double)frequency : 0.0;
    double backward_visible_first_delay_ms = frequency > 0 ?
      1000.0 * (double)backward.stable_present_first_delay_ticks /
        (double)frequency : 0.0;
    double backward_visible_interval_min_ms = frequency > 0 ?
      1000.0 * (double)backward.stable_present_interval_min_ticks /
        (double)frequency : 0.0;
    double backward_visible_interval_avg_ms =
      frequency > 0 && backward.stable_present_interval_count > 0 ?
        1000.0 * (double)backward.stable_present_interval_total_ticks /
          ((double)frequency * backward.stable_present_interval_count) : 0.0;
    double backward_visible_interval_max_ms = frequency > 0 ?
      1000.0 * (double)backward.stable_present_interval_max_ticks /
        (double)frequency : 0.0;
    double backward_visible_elapsed_ms = frequency > 0 &&
      backward.stable_present_count == EightvoPageRepeatProbePageCount ?
        1000.0 * (double)(backward.stable_present_elapsed_ticks[
          EightvoPageRepeatProbePageCount - 1] -
          backward.stable_present_elapsed_ticks[0]) /
          (double)frequency : 0.0;
    double backward_frame_max_ms = frequency > 0 ?
      1000.0 * (double)backward.frame_max_ticks /
        (double)frequency : 0.0;
    double backward_cold_render_ms = frequency > 0 ?
      1000.0 * (double)backward.cold_render_ticks /
        (double)frequency : 0.0;
    double backward_cold_present_ms = frequency > 0 ?
      1000.0 * (double)backward.cold_present_ticks /
        (double)frequency : 0.0;
    double backward_repeat_prepare_avg_ms =
      frequency > 0 && backward.repeat_timing_count > 0 ?
        1000.0 * (double)backward.repeat_prepare_total_ticks /
          ((double)frequency * backward.repeat_timing_count) : 0.0;
    double backward_repeat_prepare_max_ms = frequency > 0 ?
      1000.0 * (double)backward.repeat_prepare_max_ticks /
        (double)frequency : 0.0;
    double backward_repeat_render_avg_ms =
      frequency > 0 && backward.repeat_timing_count > 0 ?
        1000.0 * (double)backward.repeat_render_total_ticks /
          ((double)frequency * backward.repeat_timing_count) : 0.0;
    double backward_repeat_render_max_ms = frequency > 0 ?
      1000.0 * (double)backward.repeat_render_max_ticks /
        (double)frequency : 0.0;
    double backward_repeat_present_avg_ms =
      frequency > 0 && backward.repeat_timing_count > 0 ?
        1000.0 * (double)backward.repeat_present_total_ticks /
          ((double)frequency * backward.repeat_timing_count) : 0.0;
    double backward_repeat_present_max_ms = frequency > 0 ?
      1000.0 * (double)backward.repeat_present_max_ticks /
        (double)frequency : 0.0;
    double backward_navigation_prepare_avg_ms =
      frequency > 0 && backward.navigation_prepare_call_count > 0 ?
        1000.0 * (double)backward.navigation_prepare_total_ticks /
          ((double)frequency * backward.navigation_prepare_call_count) : 0.0;
    double backward_navigation_prepare_max_ms = frequency > 0 ?
      1000.0 * (double)backward.navigation_prepare_max_ticks /
        (double)frequency : 0.0;
    double nominal_first_move_ms =
      1000.0 * EightvoPageRepeatInitialFrames /
        EightvoPageRepeatFrameRate;
    double nominal_move_interval_ms =
      1000.0 * EightvoPageRepeatIntervalFrames /
        EightvoPageRepeatFrameRate;
    double nominal_elapsed_ms = nominal_first_move_ms +
      (EightvoPageRepeatProbeMoveCount - 1) * nominal_move_interval_ms;
    U32 native_posted = forward.native_repeat_posted_count +
      backward.native_repeat_posted_count;
    U32 native_coalesced = forward.native_repeat_coalesced_count +
      backward.native_repeat_coalesced_count;
    U32 canonical_matches = forward.canonical_match_count +
      backward.canonical_match_count;
    U32 canonical_valid_frames = forward.valid_frame_count +
      backward.valid_frame_count;
    U32 cross_spine_transitions = forward.cross_spine_transition_count +
      backward.cross_spine_transition_count;
    U32 cross_spine_directions =
      (forward.cross_spine_transition_count > 0 ? 1u : 0u) +
      (backward.cross_spine_transition_count > 0 ? 1u : 0u);
    U32 zero_page_or_frame_count = forward.zero_page_or_frame_count +
      backward.zero_page_or_frame_count;
    U32 orphan_text_page_count = forward.orphan_text_page_count +
      backward.orphan_text_page_count;
    U32 invalid_word_start_page_count =
      forward.invalid_word_start_page_count +
      backward.invalid_word_start_page_count;
    U32 gotm_text_frame_count = forward.text_frame_count +
      backward.text_frame_count;
    U64 gotm_minimum_text_bytes = MIN(forward.minimum_text_bytes,
                                       backward.minimum_text_bytes);
    U32 gotm_minimum_text_rows = MIN(forward.minimum_text_rows,
                                      backward.minimum_text_rows);
    U64 page_actions_emitted = forward.page_action_emitted_count +
      backward.page_action_emitted_count;
    U64 page_actions_presented = forward.page_action_presented_count +
      backward.page_action_presented_count;
    U32 page_action_overlaps = forward.page_action_overlap_count +
      backward.page_action_overlap_count;
    U32 persistence_deferred = forward.persistence_deferred_count +
      backward.persistence_deferred_count;
    U32 persistence_rescheduled = forward.persistence_rescheduled_count +
      backward.persistence_rescheduled_count +
      focus.persistence_rescheduled_count +
      control_modifier.persistence_rescheduled_count +
      shift_modifier.persistence_rescheduled_count +
      system_modifier.persistence_rescheduled_count +
      deactivate.persistence_rescheduled_count +
      mutation_gate.persistence_rescheduled_count;
    U32 cancelled_repeat_posted = focus.cancelled_repeat_posted_count +
      control_modifier.cancelled_repeat_posted_count +
      shift_modifier.cancelled_repeat_posted_count +
      system_modifier.cancelled_repeat_posted_count +
      deactivate.cancelled_repeat_posted_count;
    U32 cancelled_repeat_consumed = focus.cancelled_repeat_consumed_count +
      control_modifier.cancelled_repeat_consumed_count +
      shift_modifier.cancelled_repeat_consumed_count +
      system_modifier.cancelled_repeat_consumed_count +
      deactivate.cancelled_repeat_consumed_count;
    U32 persistence_transactions =
      forward.persistence_transaction_success_after -
        forward.persistence_transaction_success_before +
      backward.persistence_transaction_success_after -
        backward.persistence_transaction_success_before +
      focus.persistence_transaction_success_after -
        focus.persistence_transaction_success_before +
      control_modifier.persistence_transaction_success_after -
        control_modifier.persistence_transaction_success_before +
      shift_modifier.persistence_transaction_success_after -
        shift_modifier.persistence_transaction_success_before +
      system_modifier.persistence_transaction_success_after -
        system_modifier.persistence_transaction_success_before +
      deactivate.persistence_transaction_success_after -
        deactivate.persistence_transaction_success_before +
      mutation_gate.persistence_transaction_success_after -
        mutation_gate.persistence_transaction_success_before;
    U32 persistence_hold_unchanged = forward.persistence_hold_unchanged +
      backward.persistence_hold_unchanged + focus.persistence_hold_unchanged +
      control_modifier.persistence_hold_unchanged +
      shift_modifier.persistence_hold_unchanged +
      system_modifier.persistence_hold_unchanged +
      deactivate.persistence_hold_unchanged +
      mutation_gate.persistence_hold_unchanged;
    U32 persistence_post_stop_advanced =
      forward.persistence_post_stop_advanced +
      backward.persistence_post_stop_advanced +
      focus.persistence_post_stop_advanced +
      control_modifier.persistence_post_stop_advanced +
      shift_modifier.persistence_post_stop_advanced +
      system_modifier.persistence_post_stop_advanced +
      deactivate.persistence_post_stop_advanced +
      mutation_gate.persistence_post_stop_advanced;
    U32 auxiliary_paint_dispatches = forward.auxiliary_paint_dispatch_count +
      backward.auxiliary_paint_dispatch_count;
    U32 main_null_paint_dispatches = forward.main_null_paint_dispatch_count +
      backward.main_null_paint_dispatch_count;
    U32 interval_sample_count = forward.move_interval_count +
      backward.move_interval_count;
    U32 repeat_timing_sample_count = forward.repeat_timing_count +
      backward.repeat_timing_count;
    U32 stable_presentation_count = forward.stable_present_count +
      backward.stable_present_count;
    U32 visible_interval_sample_count =
      forward.stable_present_interval_count +
      backward.stable_present_interval_count;
    U32 queue_drain_batch_max = MAX(
      MAX(forward.queue_drain_batch_max_count,
          backward.queue_drain_batch_max_count),
      MAX(MAX(focus.queue_drain_batch_max_count,
              control_modifier.queue_drain_batch_max_count),
          MAX(MAX(shift_modifier.queue_drain_batch_max_count,
                  system_modifier.queue_drain_batch_max_count),
              MAX(deactivate.queue_drain_batch_max_count,
                  mutation_gate.queue_drain_batch_max_count))));
    U32 queue_drained_message_count = forward.queue_drain_message_count +
      backward.queue_drain_message_count + focus.queue_drain_message_count +
      control_modifier.queue_drain_message_count +
      shift_modifier.queue_drain_message_count +
      system_modifier.queue_drain_message_count +
      deactivate.queue_drain_message_count +
      mutation_gate.queue_drain_message_count;
    U32 expected_presented_frames = EightvoPageRepeatProbeMoveCount + 1;
    U32 idle_presentations =
      (forward.logical_frame_count > expected_presented_frames ?
         forward.logical_frame_count - expected_presented_frames : 0) +
      (backward.logical_frame_count > expected_presented_frames ?
         backward.logical_frame_count - expected_presented_frames : 0);
    U32 expected_native_repeats =
      expected_presented_frames * 2 * forward.native_repeats_per_frame;
    win32.app.persistence_enabled = 0;
    char sandbox_directory[EightvoPathCap] = {0};
    eightvo_copy_cstr(sandbox_directory,
                       ARRAY_COUNT(sandbox_directory),
                       win32.app.app_directory);
    eightvo_page_repeat_probe_cleanup_sandbox_files(&win32.app);
    win32.page_repeat_probe_track_paints = 0;
    DestroyWindow(win32.page_repeat_probe_paint_window);
    win32.page_repeat_probe_paint_window = 0;
    (void)UnregisterClassW(L"EightvoPageRepeatPaintProbeWindow", instance);
    DestroyWindow(win32.window);
    win32.window = 0;
    eightvo_app_release(&win32.app);
    B32 persistence_cleanup_ok =
      eightvo_page_repeat_probe_remove_sandbox_directory(sandbox_directory);
    B32 passed = frequency > 0 && page_repeat_sandbox_ready &&
      persistence_cleanup_ok &&
      expected_ok && forward_ok && backward_ok && returned_to_anchor &&
      focus_ok && control_modifier_ok && shift_modifier_ok &&
      system_modifier_ok && deactivate_ok && mutation_gate_ok &&
      capture_failure_ok && image_gate_ok &&
      native_posted == expected_native_repeats &&
      native_coalesced == expected_native_repeats &&
      idle_presentations == 0 && canonical_matches == 26 &&
      canonical_valid_frames == 26 && cross_spine_directions == 2 &&
      zero_page_or_frame_count == 0 && orphan_text_page_count == 0 &&
      invalid_word_start_page_count == 0 && gotm_text_frame_count > 0 &&
      gotm_minimum_text_bytes >= EightvoGotmMinimumProseTextBytes &&
      gotm_minimum_text_rows >= EightvoGotmMinimumProseTextRows &&
      page_actions_emitted == 26 && page_actions_presented == 26 &&
      page_action_overlaps == 0 &&
      interval_sample_count == 22 && repeat_timing_sample_count == 24 &&
      stable_presentation_count == 26 &&
      visible_interval_sample_count == 22 &&
      forward.navigation_prepare_fail_count == 0 &&
      backward.navigation_prepare_fail_count == 0 &&
      forward.navigation_prepare_cross_spine_ready_count == 1 &&
      backward.navigation_prepare_cross_spine_ready_count == 2 &&
      forward.navigation_prepare_build_count > 0 &&
      backward.navigation_prepare_build_count > 0 &&
      forward.prepared_window_move_count > 0 &&
      backward.prepared_window_move_count > 0 &&
      forward.synchronous_window_rebuild_move_count == 0 &&
      backward.synchronous_window_rebuild_move_count == 0 &&
      forward.synchronous_adjacent_measured_move_count == 0 &&
      backward.synchronous_adjacent_measured_move_count == 0 &&
      queue_drain_batch_max <= EightvoPageRepeatProbeQueueDrainCap &&
      auxiliary_paint_dispatches == 2 && main_null_paint_dispatches == 2 &&
      mutation_gate.page_action_mutation_drop_count ==
        EightvoPageRepeatProbeMutationCount &&
      mutation_gate.mutation_cancel_count == 1 &&
      capture_page_recovered == 1 && capture_same_page_recovered == 1 &&
      capture_open_catalog_recovered == 1 && capture_retry_recovered == 1 &&
      capture_persistence_recovered == 1 && image_gate_pages == 4 &&
      persistence_deferred == 24 && persistence_rescheduled == 8 &&
      persistence_transactions == 8 && persistence_hold_unchanged == 8 &&
      persistence_post_stop_advanced == 8 &&
      cancelled_repeat_posted == 5 && cancelled_repeat_consumed == 5;
    fprintf(passed ? stdout : stderr,
            "eightvo_page_repeat_win32_smoke result=%s viewport=%dx%d "
            "directions=%d/2 reversal=forward_then_backward "
            "returned_to_anchor=%d/1 cross_spine_directions=%u/2 "
            "cross_spine_transitions=%u "
            "schedule=wall_clock_rebased_no_catch_up frame_rate=%d "
            "initial_frames=%d interval_frames=%d repeat_moves=%d+%d "
            "presented_frames=%u+%u expected_presented_frames=%u+%u "
            "idle_presentations=%u/0 canonical_pages=%u/26 "
            "canonical_nonempty_frames=%u/26 zero_pages_or_frames=%u/0 "
            "orphan_text_pages=%u/0 invalid_word_start_pages=%u/0 "
            "boundary_oracle=raw_spine_utf8_word_start "
            "gotm_prose_scope=active_spine_text_ge_128 "
            "queue_range_oracle=self_derived_order_only "
            "independent_range_oracle=external_frozen_re10_required_not_evaluated "
            "gotm_minimum_text_bytes=%llu/%d "
            "gotm_minimum_text_rows=%u/%d "
             "action_presentations=%llu/%llu "
             "action_overlap=%u/0 native_repeats=%u/%u "
             "navigation_prepare_calls=%llu+%llu "
             "navigation_prepare_builds=%llu+%llu "
             "navigation_prepare_ready=%llu+%llu "
             "navigation_prepare_cross_spine_ready=%llu+%llu "
             "navigation_prepare_failures=%llu+%llu "
             "prepared_window_moves=%u+%u "
             "synchronous_window_rebuild_moves=%u+%u "
             "synchronous_adjacent_measured_moves=%u+%u "
             "cross_spine_boundary_ring_moves=%u+%u "
            "interval_samples=%u/22 action_timing_samples=%u/24 "
            "stable_presentations=%u/26 visible_interval_samples=%u/22 "
            "queue_drain_batch_max=%u/%d queue_drained_messages=%u "
            "keyup_cancel=%u/2 focus_cancel=%u/1 control_cancel=%u/1 "
            "shift_cancel=%u/1 system_modifier_cancel=%u/1 "
            "modifier_cancel=%u/3 deactivate_cancel=%u/1 "
            "cancelled_repeats=%u/%u mutation_gate_drops=%u/%d "
            "mutation_cancel=%u/1 capture_failure_page_recovery=%u/1 "
            "capture_failure_same_page_freshness=%u/1 "
            "capture_failure_open_catalog_recovery=%u/1 "
            "capture_failure_retry_timer_recovery=%u/1 "
            "capture_failure_persistence_recovery=%u/1 "
            "image_page_gate=%u/4 persistence_deferred=%u/24 "
            "persistence_rescheduled=%u/8 persistence_transactions=%u/8 "
            "persistence_hold_state_catalog_unchanged=%u/8 "
            "persistence_post_stop_state_catalog_advanced=%u/8 "
            "persistence=sandboxed_paired_save_individually_atomic_files "
            "persistence_cleanup=%d/1 auxiliary_paint_dispatched=%u/2 "
            "main_null_paint_dispatched=%u/2 paint=real_queue_dispatch "
            "nominal_first_move_ms=%.3f forward_first_move_ms=%.3f "
            "backward_first_move_ms=%.3f nominal_move_interval_ms=%.3f "
            "interval_tolerance_ms=%d forward_interval_min_ms=%.3f "
            "forward_interval_avg_ms=%.3f forward_interval_max_ms=%.3f "
            "backward_interval_min_ms=%.3f backward_interval_avg_ms=%.3f "
            "backward_interval_max_ms=%.3f nominal_elapsed_ms=%.3f "
            "timing_tolerance_ms=%d forward_elapsed_ms=%.3f "
            "backward_elapsed_ms=%.3f "
            "forward_immediate_visible_ms=%.3f "
            "forward_visible_first_delay_ms=%.3f "
            "forward_visible_interval_min_ms=%.3f "
            "forward_visible_interval_avg_ms=%.3f "
            "forward_visible_interval_max_ms=%.3f "
            "forward_visible_elapsed_ms=%.3f "
            "backward_immediate_visible_ms=%.3f "
            "backward_visible_first_delay_ms=%.3f "
            "backward_visible_interval_min_ms=%.3f "
            "backward_visible_interval_avg_ms=%.3f "
            "backward_visible_interval_max_ms=%.3f "
            "backward_visible_elapsed_ms=%.3f frame_budget_ms=%d "
            "move_prepare_budget_ms=16.667 render_budget_ms=48.000 "
            "present_budget_ms=16.667 forward_frame_max_ms=%.3f "
            "backward_frame_max_ms=%.3f forward_cold_render_ms=%.3f "
            "forward_cold_present_ms=%.3f "
            "forward_repeat_prepare_avg_ms=%.3f "
            "forward_repeat_prepare_max_ms=%.3f "
            "forward_repeat_render_avg_ms=%.3f "
            "forward_repeat_render_max_ms=%.3f "
             "forward_repeat_present_avg_ms=%.3f "
             "forward_repeat_present_max_ms=%.3f "
             "forward_navigation_prepare_avg_ms=%.3f "
             "forward_navigation_prepare_max_ms=%.3f "
             "backward_cold_render_ms=%.3f backward_cold_present_ms=%.3f "
            "backward_repeat_prepare_avg_ms=%.3f "
            "backward_repeat_prepare_max_ms=%.3f "
            "backward_repeat_render_avg_ms=%.3f "
            "backward_repeat_render_max_ms=%.3f "
             "backward_repeat_present_avg_ms=%.3f "
             "backward_repeat_present_max_ms=%.3f "
             "backward_navigation_prepare_avg_ms=%.3f "
             "backward_navigation_prepare_max_ms=%.3f\n",
            passed ? "pass" : "fail",
            EightvoPageRepeatProbeWidth,
            EightvoPageRepeatProbeHeight,
            forward_ok + backward_ok,
            returned_to_anchor,
            cross_spine_directions,
            cross_spine_transitions,
            EightvoPageRepeatFrameRate,
            EightvoPageRepeatInitialFrames,
            EightvoPageRepeatIntervalFrames,
            EightvoPageRepeatProbeMoveCount,
            EightvoPageRepeatProbeMoveCount,
            forward.logical_frame_count,
            backward.logical_frame_count,
            expected_presented_frames,
            expected_presented_frames,
            idle_presentations,
            canonical_matches,
            canonical_valid_frames,
            zero_page_or_frame_count,
            orphan_text_page_count,
            invalid_word_start_page_count,
            (unsigned long long)gotm_minimum_text_bytes,
            EightvoGotmMinimumProseTextBytes,
            gotm_minimum_text_rows,
            EightvoGotmMinimumProseTextRows,
            (unsigned long long)page_actions_emitted,
            (unsigned long long)page_actions_presented,
            page_action_overlaps,
             native_coalesced,
             native_posted,
             (unsigned long long)forward.navigation_prepare_call_count,
             (unsigned long long)backward.navigation_prepare_call_count,
             (unsigned long long)forward.navigation_prepare_build_count,
             (unsigned long long)backward.navigation_prepare_build_count,
             (unsigned long long)forward.navigation_prepare_ready_count,
             (unsigned long long)backward.navigation_prepare_ready_count,
             (unsigned long long)
               forward.navigation_prepare_cross_spine_ready_count,
             (unsigned long long)
               backward.navigation_prepare_cross_spine_ready_count,
             (unsigned long long)forward.navigation_prepare_fail_count,
             (unsigned long long)backward.navigation_prepare_fail_count,
             forward.prepared_window_move_count,
             backward.prepared_window_move_count,
             forward.synchronous_window_rebuild_move_count,
             backward.synchronous_window_rebuild_move_count,
             forward.synchronous_adjacent_measured_move_count,
             backward.synchronous_adjacent_measured_move_count,
             forward.cross_spine_boundary_ring_move_count,
             backward.cross_spine_boundary_ring_move_count,
            interval_sample_count,
            repeat_timing_sample_count,
            stable_presentation_count,
            visible_interval_sample_count,
            queue_drain_batch_max,
            EightvoPageRepeatProbeQueueDrainCap,
            queue_drained_message_count,
            forward.keyup_cancel_count + backward.keyup_cancel_count,
            focus.focus_cancel_count,
            control_modifier.modifier_cancel_count,
            shift_modifier.modifier_cancel_count,
            system_modifier.modifier_cancel_count,
            control_modifier.modifier_cancel_count +
              shift_modifier.modifier_cancel_count +
              system_modifier.modifier_cancel_count,
            deactivate.deactivate_cancel_count,
            cancelled_repeat_consumed,
            cancelled_repeat_posted,
            mutation_gate.page_action_mutation_drop_count,
            EightvoPageRepeatProbeMutationCount,
            mutation_gate.mutation_cancel_count,
            capture_page_recovered,
            capture_same_page_recovered,
            capture_open_catalog_recovered,
            capture_retry_recovered,
            capture_persistence_recovered,
            image_gate_pages,
            persistence_deferred,
            persistence_rescheduled,
            persistence_transactions,
            persistence_hold_unchanged,
            persistence_post_stop_advanced,
            persistence_cleanup_ok,
            auxiliary_paint_dispatches,
            main_null_paint_dispatches,
            nominal_first_move_ms,
            forward_first_move_ms,
            backward_first_move_ms,
            nominal_move_interval_ms,
            EightvoPageRepeatProbeIntervalToleranceMs,
            forward_interval_min_ms,
            forward_interval_avg_ms,
            forward_interval_max_ms,
            backward_interval_min_ms,
            backward_interval_avg_ms,
            backward_interval_max_ms,
            nominal_elapsed_ms,
            EightvoPageRepeatProbeTimingToleranceMs,
            forward_elapsed_ms,
            backward_elapsed_ms,
            forward_immediate_visible_ms,
            forward_visible_first_delay_ms,
            forward_visible_interval_min_ms,
            forward_visible_interval_avg_ms,
            forward_visible_interval_max_ms,
            forward_visible_elapsed_ms,
            backward_immediate_visible_ms,
            backward_visible_first_delay_ms,
            backward_visible_interval_min_ms,
            backward_visible_interval_avg_ms,
            backward_visible_interval_max_ms,
            backward_visible_elapsed_ms,
            EightvoPageRepeatProbeFrameBudgetMs,
            forward_frame_max_ms,
            backward_frame_max_ms,
            forward_cold_render_ms,
            forward_cold_present_ms,
            forward_repeat_prepare_avg_ms,
            forward_repeat_prepare_max_ms,
            forward_repeat_render_avg_ms,
            forward_repeat_render_max_ms,
             forward_repeat_present_avg_ms,
             forward_repeat_present_max_ms,
             forward_navigation_prepare_avg_ms,
             forward_navigation_prepare_max_ms,
             backward_cold_render_ms,
            backward_cold_present_ms,
            backward_repeat_prepare_avg_ms,
            backward_repeat_prepare_max_ms,
            backward_repeat_render_avg_ms,
            backward_repeat_render_max_ms,
             backward_repeat_present_avg_ms,
             backward_repeat_present_max_ms,
             backward_navigation_prepare_avg_ms,
             backward_navigation_prepare_max_ms);
    return passed ? 0 : 1;
  }

  InvalidateRect(win32.window, 0, FALSE);

  eightvo_win32_run_message_loop(&win32, 0);
  eightvo_app_release(&win32.app);
  return 0;
}

FUNCTION int
eightvo_run_window(const char *initial_path)
{
  return eightvo_run_window_internal(initial_path, 0);
}

FUNCTION int
eightvo_run_page_repeat_win32_smoke(const char *path)
{
  return eightvo_run_window_internal(path, 1);
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
  if (argc == 5 &&
      strcmp(argv[1], "--saved-position-first-load-smoke") == 0)
  {
    char *spine_end = 0;
    char *byte_end = 0;
    unsigned long parsed_spine = strtoul(argv[3], &spine_end, 10);
    unsigned long long parsed_byte = _strtoui64(argv[4], &byte_end, 10);
    if (!spine_end || *spine_end != 0 || parsed_spine > UINT32_MAX ||
        !byte_end || *byte_end != 0)
    {
      fprintf(stderr,
              "eightvo_saved_position_first_load_smoke result=fail reason=args\n");
      result = 2;
    }
    else
    {
      result = eightvo_run_saved_position_first_load_smoke(
        argv[2], (U32)parsed_spine, (U64)parsed_byte);
    }
  }
  else if (argc == 3 && strcmp(argv[1], "--headless") == 0)
  {
    result = eightvo_run_headless(argv[2]);
  }
  else if (argc == 4 && strcmp(argv[1], "--render-smoke") == 0)
  {
    result = eightvo_run_render_smoke(argv[2], argv[3]);
  }
  else if (argc == 5 && strcmp(argv[1], "--image-smoke") == 0)
  {
    result = eightvo_run_image_smoke(argv[2], argv[3], argv[4]);
  }
  else if (argc == 4 && strcmp(argv[1], "--reader-image-fit-smoke") == 0)
  {
    result = eightvo_run_reader_image_fit_smoke(argv[2], argv[3]);
  }
  else if (argc == 4 && strcmp(argv[1], "--page-turn-regression-smoke") == 0)
  {
    result = eightvo_run_page_turn_regression_smoke(argv[2], argv[3]);
  }
  else if (argc == 3 &&
           strcmp(argv[1], "--page-repeat-win32-smoke") == 0)
  {
    result = eightvo_run_page_repeat_win32_smoke(argv[2]);
  }
  else if (argc == 4 && strcmp(argv[1], "--library-smoke") == 0)
  {
    result = eightvo_run_library_smoke(argv[2], argv[3]);
  }
  else if (argc == 4 && strcmp(argv[1], "--reader-view-smoke") == 0)
  {
    result = eightvo_run_reader_view_smoke(argv[2], argv[3]);
  }
  else if (argc == 4 &&
           strcmp(argv[1], "--publisher-typography-spacing-smoke") == 0)
  {
    result = eightvo_run_publisher_typography_spacing_smoke(
      argv[2], argv[3]);
  }
  else if (argc == 4 &&
           strcmp(argv[1], "--reader-view-post-action-arrow-smoke") == 0)
  {
    result = eightvo_run_reader_view_post_action_arrow_smoke(
      argv[2], argv[3]);
  }
  else if (argc == 4 &&
           strcmp(argv[1], "--reader-view-find-active-contrast-smoke") == 0)
  {
    result = eightvo_run_reader_view_find_active_contrast_smoke(
      argv[2], argv[3]);
  }
  else if (argc == 4 &&
           strcmp(argv[1], "--reader-view-find-snippet-context-smoke") == 0)
  {
    result = eightvo_run_reader_view_find_snippet_context_smoke(
      argv[2], argv[3]);
  }
  else if (argc == 2 &&
           strcmp(argv[1], "--reader-view-startup-interaction-smoke") == 0)
  {
    result = eightvo_run_reader_view_startup_interaction_smoke();
  }
  else if (argc == 4 &&
           strcmp(argv[1], "--reader-view-selection-menu-smoke") == 0)
  {
    result = eightvo_run_reader_view_selection_menu_smoke(
      argv[2], argv[3]);
  }
  else if ((argc == 12 || argc == 13 || argc == 14) &&
           strcmp(argv[1], "--reader-view-parity-capture") == 0)
  {
    result = eightvo_run_reader_view_parity_capture(
      argv[2], argv[3], argv[4], argv[5], argv[6], argv[7], argv[8],
      argv[9], argv[10], argv[11], argc >= 13 ? argv[12] : "none",
      argc == 14 ? argv[13] : "none");
  }
  else if (argc == 3 && strcmp(argv[1], "--accessibility-smoke") == 0)
  {
    result = eightvo_run_accessibility_smoke(argv[2]);
  }
  else if (argc == 2 && strcmp(argv[1], "--data-migration-smoke") == 0)
  {
    result = eightvo_run_data_migration_smoke();
  }
  else if (argc == 2 && strcmp(argv[1], "--version") == 0)
  {
    fprintf(stdout,
            "8vo %s reader0_api=%d ui0_api=%d readerview0_api=%d\n",
            EIGHTVO_VERSION_STRING,
            READER0_API_VERSION,
            UI0_API_VERSION,
            READERVIEW0_API_VERSION);
  }
  else if (argc <= 2)
  {
    result = eightvo_run_window(argc == 2 ? argv[1] : 0);
  }
  else
  {
    fprintf(stderr,
            "usage: 8vo.exe [epub-path | --saved-position-first-load-smoke epub-path spine byte | --headless epub-path | --render-smoke epub-path bmp-path | --image-smoke epub-path cover-bmp inline-bmp | --reader-image-fit-smoke epub-path output-prefix | --page-turn-regression-smoke epub-path output-prefix | --page-repeat-win32-smoke epub-path | --library-smoke epub-path output-prefix | --reader-view-smoke epub-path export-path | --publisher-typography-spacing-smoke epub-path output-prefix | --reader-view-post-action-arrow-smoke epub-path output-prefix | --reader-view-find-active-contrast-smoke epub-path output-prefix | --reader-view-find-snippet-context-smoke epub-path bmp-path | --reader-view-startup-interaction-smoke | --reader-view-parity-capture epub width height theme left right popup query evidence bmp [focus [annotation-case]] | --accessibility-smoke epub-path | --data-migration-smoke | --version]\n");
    result = 2;
  }

  if (release_com) { CoUninitialize(); }
  return result;
}

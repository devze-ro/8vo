#ifndef OCTAVO_PDF_CONTENT_H
#define OCTAVO_PDF_CONTENT_H

#include "base/base_arena.h"
#include "reader0.h"

#include <stddef.h>

enum
{
  OCTAVO_PDF_CONTENT_OUTLINE_ITEM_CAP = 4096,
  OCTAVO_PDF_CONTENT_OUTLINE_STRING_CAP = 1024 * 1024,
  OCTAVO_PDF_CONTENT_PAGE_LABEL_CAP = 1024,
  OCTAVO_PDF_CONTENT_PAGE_LINK_CAP = 1024,
  OCTAVO_PDF_CONTENT_PAGE_LINK_URI_CAP = 256 * 1024,
  OCTAVO_PDF_CONTENT_SEARCH_QUERY_CAP = 127,
  OCTAVO_PDF_CONTENT_SEARCH_PAGE_HIT_CAP = 4096,
  OCTAVO_PDF_CONTENT_SEARCH_PAGE_QUAD_CAP = 16384,
  OCTAVO_PDF_CONTENT_SEARCH_ROW_CAP = 64,
  OCTAVO_PDF_CONTENT_EXTERNAL_URI_CAP = 2048,

  OCTAVO_PDF_CONTENT_OUTLINE_ARENA_CAP = 2 * 1024 * 1024,
  OCTAVO_PDF_CONTENT_LABEL_ARENA_CAP = 64 * 1024,
  OCTAVO_PDF_CONTENT_LINK_ARENA_CAP = 1024 * 1024,
  OCTAVO_PDF_CONTENT_SEARCH_ARENA_CAP = 512 * 1024,
  OCTAVO_PDF_CONTENT_SEARCH_SCRATCH_ARENA_CAP = 2 * 1024 * 1024,
  OCTAVO_PDF_CONTENT_LAUNCH_ARENA_CAP = 64 * 1024,
};

typedef enum OctavoPdfContentLoadState
{
  OctavoPdfContentLoad_Unavailable,
  OctavoPdfContentLoad_Loading,
  OctavoPdfContentLoad_Empty,
  OctavoPdfContentLoad_Ready,
  OctavoPdfContentLoad_Error,
  OctavoPdfContentLoad_LimitExceeded,
} OctavoPdfContentLoadState;

typedef struct OctavoPdfContentSpan
{
  const U8 *str;
  U64 size;
} OctavoPdfContentSpan;

typedef struct OctavoPdfContentSearchRow
{
  U64 key;
  U32 page_index;
  U32 match_index_on_page;
  OctavoPdfContentSpan page_label;
  OctavoPdfContentSpan excerpt;
} OctavoPdfContentSearchRow;

typedef struct OctavoPdfContentActivation
{
  PdfReaderLinkTargetKind target_kind;
  PdfReaderDestination destination;
  OctavoPdfContentSpan external_uri;
} OctavoPdfContentActivation;

typedef struct OctavoPdfContent
{
  Arena *outline_arena;
  Arena *label_arena;
  Arena *link_arena;
  Arena *search_arena;
  Arena *search_scratch_arena;
  Arena *launch_arena;

  U64 document_generation;
  U32 page_count;

  OctavoPdfContentLoadState outline_state;
  PdfReaderResult outline_result;
  PdfReaderOutlineItem *outline_items;
  U8 *outline_strings;
  U32 outline_item_count;
  U64 outline_string_bytes;

  OctavoPdfContentLoadState label_state;
  PdfReaderResult label_result;
  U64 label_document_generation;
  U32 label_page_index;
  U8 *page_label;
  U64 page_label_size;

  OctavoPdfContentLoadState link_state;
  PdfReaderResult link_result;
  U64 link_document_generation;
  U32 link_page_index;
  PdfReaderLink *page_links;
  U8 *page_link_uri_bytes;
  U32 page_link_count;
  U64 page_link_uri_size;

  OctavoPdfContentLoadState search_state;
  PdfReaderResult search_result;
  U64 search_document_generation;
  U64 search_serial;
  U8 *search_query;
  U64 search_query_size;
  OctavoPdfContentSearchRow *search_rows;
  U32 search_row_count;
  U64 search_total_count;
  U32 search_next_page;
  S32 search_active_index;
  B32 search_complete;
  B32 search_has_more;
} OctavoPdfContent;

FUNCTION B32 octavo_pdf_content_init(OctavoPdfContent *content);
FUNCTION void octavo_pdf_content_release(OctavoPdfContent *content);
FUNCTION void octavo_pdf_content_reset(OctavoPdfContent *content);
FUNCTION B32 octavo_pdf_content_invariants_hold(
  const OctavoPdfContent *content);
FUNCTION B32 octavo_pdf_content_bind_error_document(
  OctavoPdfContent *content,
  U64 document_generation,
  U32 page_count);
/* Fabricated Reader0 result-shape regression used by the focused product
   smoke. It performs no I/O and allocates no memory. */
FUNCTION B32 octavo_pdf_content_result_contract_smoke(void);

/* Starts a new published document identity. Content-surface failures are
   retained per surface and do not close an otherwise valid PDF. */
FUNCTION B32 octavo_pdf_content_begin_document(
  OctavoPdfContent *content,
  PdfReader *reader,
  U64 document_generation,
  U32 page_index,
  U32 page_count);
FUNCTION void octavo_pdf_content_refresh_current_page(
  OctavoPdfContent *content,
  PdfReader *reader,
  U64 document_generation,
  U32 page_index,
  U32 page_count);

FUNCTION const PdfReaderOutlineItem *octavo_pdf_content_outline_item(
  const OctavoPdfContent *content,
  U64 document_generation,
  U32 index,
  OctavoPdfContentSpan *out_title);
FUNCTION U64 octavo_pdf_content_outline_key(U32 index);
FUNCTION B32 octavo_pdf_content_resolve_outline_key(
  const OctavoPdfContent *content,
  U64 document_generation,
  U64 key,
  OctavoPdfContentActivation *out_activation);

FUNCTION OctavoPdfContentSpan octavo_pdf_content_page_label(
  const OctavoPdfContent *content,
  U64 document_generation,
  U32 page_index);

FUNCTION B32 octavo_pdf_content_begin_search(
  OctavoPdfContent *content,
  PdfReader *reader,
  U64 document_generation,
  U32 page_count,
  String8 query);
FUNCTION void octavo_pdf_content_cancel_search(OctavoPdfContent *content);
/* Performs zero or one Reader0 page search. out_did_work is true only when
   exactly one page was queried/copied during this call. */
FUNCTION B32 octavo_pdf_content_search_pump(
  OctavoPdfContent *content,
  PdfReader *reader,
  U64 document_generation,
  U32 page_count,
  B32 *out_did_work);
FUNCTION U64 octavo_pdf_content_search_key(U32 index);
FUNCTION B32 octavo_pdf_content_resolve_search_key(
  const OctavoPdfContent *content,
  U64 document_generation,
  U64 key,
  U32 *out_page_index,
  S32 *out_row_index);
FUNCTION B32 octavo_pdf_content_step_search(
  OctavoPdfContent *content,
  U64 document_generation,
  S32 direction,
  U32 *out_page_index);

FUNCTION B32 octavo_pdf_content_hit_test_link(
  const OctavoPdfContent *content,
  U64 document_generation,
  U32 page_index,
  PdfReaderPoint point,
  OctavoPdfContentActivation *out_activation);
FUNCTION B32 octavo_pdf_content_external_uri_is_allowed(
  OctavoPdfContentSpan uri);
/* The returned NUL-terminated UTF-16 string is borrowed from launch_arena and
   remains valid until the next conversion/reset/release. */
FUNCTION B32 octavo_pdf_content_external_uri_wide(
  OctavoPdfContent *content,
  OctavoPdfContentSpan uri,
  const wchar_t **out_uri);

#endif /* OCTAVO_PDF_CONTENT_H */

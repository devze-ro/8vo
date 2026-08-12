#include "octavo_pdf_content.h"

#include "base/base_format.h"
#include "base/base_unicode.h"

#include <math.h>
#include <stddef.h>
#include <windows.h>

#define OCTAVO_PDF_CONTENT_OUTLINE_KEY_PREFIX 0x5044465400000000ull
#define OCTAVO_PDF_CONTENT_SEARCH_KEY_PREFIX  0x5044465300000000ull
#define OCTAVO_PDF_CONTENT_KEY_PREFIX_MASK    0xffffffff00000000ull
#define OCTAVO_PDF_CONTENT_KEY_INDEX_MASK     0x00000000ffffffffull

FUNCTION Arena *
octavo_pdf_content_arena(U64 cap)
{
  ArenaParams params = {
    .reserve_size = cap,
    .commit_size = 64 * 1024,
  };
  return arena_alloc(&params);
}

FUNCTION void *
octavo_pdf_content_arena_push(Arena *arena,
                              U64 cap,
                              U64 size,
                              U64 alignment)
{
  if (!arena || alignment == 0 ||
      (alignment & (alignment - 1)) != 0)
  {
    return 0;
  }
  U64 position = arena_pos(arena);
  if (position > UINT64_MAX - (alignment - 1)) return 0;
  U64 aligned = ALIGN_POW2(position, alignment);
  if (aligned > cap || size > cap - aligned) return 0;
  return arena_push(arena, size, alignment);
}

FUNCTION B32
octavo_pdf_content_identity_matches(const OctavoPdfContent *content,
                                    const PdfReader *reader,
                                    U64 document_generation,
                                    U32 page_count)
{
  return content && reader && document_generation != 0 && page_count != 0 &&
    content->document_generation == document_generation &&
    content->page_count == page_count && reader->initialized &&
    reader->document_open &&
    reader->document_generation == document_generation &&
    reader->page_count == page_count;
}

FUNCTION B32
octavo_pdf_content_query_result_is_exact(PdfReaderResult result,
                                         B32 has_required_output)
{
  return has_required_output ? result == PdfReaderResult_BufferTooSmall :
                               result == PdfReaderResult_Ok;
}

FUNCTION B32
octavo_pdf_content_outline_query_shape_is_valid(
  const PdfReaderOutlineResult *result)
{
  return result && result->written_item_count == 0 &&
    result->written_string_bytes == 0 &&
    (result->required_item_count != 0 ||
     result->required_string_bytes == 0);
}

FUNCTION B32
octavo_pdf_content_outline_requirements_fit(
  const PdfReaderOutlineResult *result)
{
  return result &&
    result->required_item_count <= OCTAVO_PDF_CONTENT_OUTLINE_ITEM_CAP &&
    result->required_string_bytes <=
      OCTAVO_PDF_CONTENT_OUTLINE_STRING_CAP;
}

FUNCTION B32
octavo_pdf_content_outline_copy_is_exact(
  const PdfReaderOutlineResult *query,
  const PdfReaderOutlineResult *copied)
{
  return octavo_pdf_content_outline_query_shape_is_valid(query) &&
    octavo_pdf_content_outline_requirements_fit(query) && copied &&
    copied->document_generation == query->document_generation &&
    copied->required_item_count == query->required_item_count &&
    copied->written_item_count == query->required_item_count &&
    copied->required_string_bytes == query->required_string_bytes &&
    copied->written_string_bytes == query->required_string_bytes &&
    (copied->required_item_count != 0 ||
     copied->required_string_bytes == 0);
}

FUNCTION B32
octavo_pdf_content_link_query_shape_is_valid(
  const PdfReaderPageLinksResult *result)
{
  return result && result->written_link_count == 0 &&
    result->written_uri_bytes == 0 &&
    (result->required_link_count != 0 ||
     result->required_uri_bytes == 0);
}

FUNCTION B32
octavo_pdf_content_link_requirements_fit(
  const PdfReaderPageLinksResult *result)
{
  return result &&
    result->required_link_count <= OCTAVO_PDF_CONTENT_PAGE_LINK_CAP &&
    result->required_uri_bytes <= OCTAVO_PDF_CONTENT_PAGE_LINK_URI_CAP;
}

FUNCTION B32
octavo_pdf_content_link_copy_is_exact(
  const PdfReaderPageLinksResult *query,
  const PdfReaderPageLinksResult *copied)
{
  return octavo_pdf_content_link_query_shape_is_valid(query) &&
    octavo_pdf_content_link_requirements_fit(query) && copied &&
    copied->document_generation == query->document_generation &&
    copied->page_index == query->page_index &&
    copied->required_link_count == query->required_link_count &&
    copied->written_link_count == query->required_link_count &&
    copied->required_uri_bytes == query->required_uri_bytes &&
    copied->written_uri_bytes == query->required_uri_bytes &&
    (copied->required_link_count != 0 ||
     copied->required_uri_bytes == 0);
}

FUNCTION B32
octavo_pdf_content_search_query_shape_is_valid(
  const PdfReaderSearchResult *result)
{
  return result && result->written_hit_count == 0 &&
    result->written_quad_count == 0 &&
    ((result->required_hit_count == 0) ==
     (result->required_quad_count == 0)) &&
    result->required_quad_count >= result->required_hit_count;
}

FUNCTION B32
octavo_pdf_content_search_requirements_fit(
  const PdfReaderSearchResult *result)
{
  return result &&
    result->required_hit_count <=
      OCTAVO_PDF_CONTENT_SEARCH_PAGE_HIT_CAP &&
    result->required_quad_count <=
      OCTAVO_PDF_CONTENT_SEARCH_PAGE_QUAD_CAP;
}

FUNCTION B32
octavo_pdf_content_search_copy_is_exact(
  const PdfReaderSearchResult *query,
  const PdfReaderSearchResult *copied,
  const PdfReaderSearchHit *hits)
{
  if (!octavo_pdf_content_search_query_shape_is_valid(query) ||
      !octavo_pdf_content_search_requirements_fit(query) || !copied ||
      copied->document_generation != query->document_generation ||
      copied->page_index != query->page_index ||
      copied->required_hit_count != query->required_hit_count ||
      copied->written_hit_count != query->required_hit_count ||
      copied->required_quad_count != query->required_quad_count ||
      copied->written_quad_count != query->required_quad_count ||
      ((copied->written_hit_count == 0) !=
       (copied->written_quad_count == 0)) ||
      (copied->written_hit_count != 0 && !hits))
  {
    return 0;
  }
  U32 terminal_quad = 0;
  for (U32 index = 0; index < copied->written_hit_count; index += 1)
  {
    const PdfReaderSearchHit *hit = hits + index;
    if (hit->page_index != copied->page_index || hit->quad_count == 0 ||
        hit->first_quad != terminal_quad ||
        hit->quad_count > copied->written_quad_count - terminal_quad)
    {
      return 0;
    }
    terminal_quad += hit->quad_count;
  }
  return terminal_quad == copied->written_quad_count;
}

FUNCTION B32
octavo_pdf_content_result_contract_smoke(void)
{
  PdfReaderOutlineResult outline_query = {
    .document_generation = 7,
    .required_item_count = 1,
  };
  PdfReaderOutlineResult outline_copy = {
    .document_generation = 7,
    .required_item_count = 1,
    .written_item_count = 1,
  };
  PdfReaderOutlineResult outline_impossible = outline_query;
  outline_impossible.required_item_count = 0;
  outline_impossible.required_string_bytes = 1;
  PdfReaderOutlineResult outline_over_cap = outline_query;
  outline_over_cap.required_item_count =
    OCTAVO_PDF_CONTENT_OUTLINE_ITEM_CAP + 1;
  PdfReaderOutlineResult outline_string_over_cap = outline_query;
  outline_string_over_cap.required_string_bytes =
    OCTAVO_PDF_CONTENT_OUTLINE_STRING_CAP + 1;
  PdfReaderOutlineResult outline_bad_copy = outline_copy;
  outline_bad_copy.required_item_count = 2;

  PdfReaderPageLinksResult link_query = {
    .document_generation = 8,
    .page_index = 2,
    .required_link_count = 1,
  };
  PdfReaderPageLinksResult link_copy = {
    .document_generation = 8,
    .page_index = 2,
    .required_link_count = 1,
    .written_link_count = 1,
  };
  PdfReaderPageLinksResult link_impossible = link_query;
  link_impossible.required_link_count = 0;
  link_impossible.required_uri_bytes = 1;
  PdfReaderPageLinksResult link_over_cap = link_query;
  link_over_cap.required_uri_bytes =
    OCTAVO_PDF_CONTENT_PAGE_LINK_URI_CAP + 1;
  PdfReaderPageLinksResult link_count_over_cap = link_query;
  link_count_over_cap.required_link_count =
    OCTAVO_PDF_CONTENT_PAGE_LINK_CAP + 1;
  PdfReaderPageLinksResult link_bad_copy = link_copy;
  link_bad_copy.written_link_count = 0;

  PdfReaderSearchResult search_query = {
    .document_generation = 9,
    .page_index = 3,
    .required_hit_count = 2,
    .required_quad_count = 3,
  };
  PdfReaderSearchResult search_copy = {
    .document_generation = 9,
    .page_index = 3,
    .required_hit_count = 2,
    .written_hit_count = 2,
    .required_quad_count = 3,
    .written_quad_count = 3,
  };
  PdfReaderSearchHit ordered_hits[2] = {
    {.page_index = 3, .first_quad = 0, .quad_count = 1},
    {.page_index = 3, .first_quad = 1, .quad_count = 2},
  };
  PdfReaderSearchResult search_impossible = search_query;
  search_impossible.required_hit_count = 0;
  PdfReaderSearchResult search_over_cap = search_query;
  search_over_cap.required_hit_count =
    OCTAVO_PDF_CONTENT_SEARCH_PAGE_HIT_CAP + 1;
  search_over_cap.required_quad_count = search_over_cap.required_hit_count;
  PdfReaderSearchResult search_quad_over_cap = search_query;
  search_quad_over_cap.required_quad_count =
    OCTAVO_PDF_CONTENT_SEARCH_PAGE_QUAD_CAP + 1;
  PdfReaderSearchResult search_bad_copy = search_copy;
  search_bad_copy.required_quad_count += 1;
  PdfReaderSearchHit zero_hit[2] = {
    {.page_index = 3, .first_quad = 0, .quad_count = 0},
    {.page_index = 3, .first_quad = 0, .quad_count = 3},
  };
  PdfReaderSearchHit gapped_hits[2] = {
    {.page_index = 3, .first_quad = 0, .quad_count = 1},
    {.page_index = 3, .first_quad = 2, .quad_count = 1},
  };

  return octavo_pdf_content_outline_query_shape_is_valid(&outline_query) &&
    octavo_pdf_content_outline_requirements_fit(&outline_query) &&
    octavo_pdf_content_outline_copy_is_exact(
      &outline_query, &outline_copy) &&
    !octavo_pdf_content_outline_query_shape_is_valid(
      &outline_impossible) &&
    !octavo_pdf_content_outline_requirements_fit(&outline_over_cap) &&
    !octavo_pdf_content_outline_requirements_fit(
      &outline_string_over_cap) &&
    !octavo_pdf_content_outline_copy_is_exact(
      &outline_query, &outline_bad_copy) &&
    octavo_pdf_content_link_query_shape_is_valid(&link_query) &&
    octavo_pdf_content_link_requirements_fit(&link_query) &&
    octavo_pdf_content_link_copy_is_exact(&link_query, &link_copy) &&
    !octavo_pdf_content_link_query_shape_is_valid(&link_impossible) &&
    !octavo_pdf_content_link_requirements_fit(&link_over_cap) &&
    !octavo_pdf_content_link_requirements_fit(&link_count_over_cap) &&
    !octavo_pdf_content_link_copy_is_exact(&link_query, &link_bad_copy) &&
    octavo_pdf_content_search_query_shape_is_valid(&search_query) &&
    octavo_pdf_content_search_requirements_fit(&search_query) &&
    octavo_pdf_content_search_copy_is_exact(
      &search_query, &search_copy, ordered_hits) &&
    !octavo_pdf_content_search_query_shape_is_valid(&search_impossible) &&
    !octavo_pdf_content_search_requirements_fit(&search_over_cap) &&
    !octavo_pdf_content_search_requirements_fit(&search_quad_over_cap) &&
    !octavo_pdf_content_search_copy_is_exact(
      &search_query, &search_bad_copy, ordered_hits) &&
    !octavo_pdf_content_search_copy_is_exact(
      &search_query, &search_copy, zero_hit) &&
    !octavo_pdf_content_search_copy_is_exact(
      &search_query, &search_copy, gapped_hits);
}

FUNCTION B32
octavo_pdf_content_span_in_buffer(U64 offset,
                                  U64 size,
                                  U64 buffer_size)
{
  return offset <= buffer_size && size <= buffer_size - offset;
}

FUNCTION void
octavo_pdf_content_withdraw_outline(OctavoPdfContent *content,
                                    OctavoPdfContentLoadState state,
                                    PdfReaderResult result)
{
  if (!content) return;
  arena_clear(content->outline_arena);
  content->outline_items = 0;
  content->outline_strings = 0;
  content->outline_item_count = 0;
  content->outline_string_bytes = 0;
  content->outline_state = state;
  content->outline_result = result;
}

FUNCTION void
octavo_pdf_content_withdraw_label(OctavoPdfContent *content,
                                  OctavoPdfContentLoadState state,
                                  PdfReaderResult result)
{
  if (!content) return;
  arena_clear(content->label_arena);
  content->label_document_generation = 0;
  content->label_page_index = 0;
  content->page_label = 0;
  content->page_label_size = 0;
  content->label_state = state;
  content->label_result = result;
}

FUNCTION void
octavo_pdf_content_withdraw_links(OctavoPdfContent *content,
                                  OctavoPdfContentLoadState state,
                                  PdfReaderResult result)
{
  if (!content) return;
  arena_clear(content->link_arena);
  content->link_document_generation = 0;
  content->link_page_index = 0;
  content->page_links = 0;
  content->page_link_uri_bytes = 0;
  content->page_link_count = 0;
  content->page_link_uri_size = 0;
  content->link_state = state;
  content->link_result = result;
}

FUNCTION void
octavo_pdf_content_withdraw_search(OctavoPdfContent *content,
                                   OctavoPdfContentLoadState state,
                                   PdfReaderResult result)
{
  if (!content) return;
  arena_clear(content->search_arena);
  arena_clear(content->search_scratch_arena);
  content->search_query = 0;
  content->search_query_size = 0;
  content->search_rows = 0;
  content->search_row_count = 0;
  content->search_total_count = 0;
  content->search_next_page = 0;
  content->search_active_index = -1;
  content->search_complete = state != OctavoPdfContentLoad_Loading;
  content->search_has_more = 0;
  content->search_state = state;
  content->search_result = result;
}

FUNCTION B32
octavo_pdf_content_init(OctavoPdfContent *content)
{
  if (!content) return 0;
  MemoryZeroStruct(content);
  content->outline_arena = octavo_pdf_content_arena(
    OCTAVO_PDF_CONTENT_OUTLINE_ARENA_CAP);
  content->label_arena = octavo_pdf_content_arena(
    OCTAVO_PDF_CONTENT_LABEL_ARENA_CAP);
  content->link_arena = octavo_pdf_content_arena(
    OCTAVO_PDF_CONTENT_LINK_ARENA_CAP);
  content->search_arena = octavo_pdf_content_arena(
    OCTAVO_PDF_CONTENT_SEARCH_ARENA_CAP);
  content->search_scratch_arena = octavo_pdf_content_arena(
    OCTAVO_PDF_CONTENT_SEARCH_SCRATCH_ARENA_CAP);
  content->launch_arena = octavo_pdf_content_arena(
    OCTAVO_PDF_CONTENT_LAUNCH_ARENA_CAP);
  if (!content->outline_arena || !content->label_arena ||
      !content->link_arena || !content->search_arena ||
      !content->search_scratch_arena || !content->launch_arena)
  {
    octavo_pdf_content_release(content);
    return 0;
  }
  octavo_pdf_content_reset(content);
  return 1;
}

FUNCTION void
octavo_pdf_content_release(OctavoPdfContent *content)
{
  if (!content) return;
  if (content->outline_arena) arena_release(content->outline_arena);
  if (content->label_arena) arena_release(content->label_arena);
  if (content->link_arena) arena_release(content->link_arena);
  if (content->search_arena) arena_release(content->search_arena);
  if (content->search_scratch_arena)
    arena_release(content->search_scratch_arena);
  if (content->launch_arena) arena_release(content->launch_arena);
  MemoryZeroStruct(content);
}

FUNCTION void
octavo_pdf_content_reset(OctavoPdfContent *content)
{
  if (!content) return;
  Arena *outline_arena = content->outline_arena;
  Arena *label_arena = content->label_arena;
  Arena *link_arena = content->link_arena;
  Arena *search_arena = content->search_arena;
  Arena *search_scratch_arena = content->search_scratch_arena;
  Arena *launch_arena = content->launch_arena;
  if (outline_arena) arena_clear(outline_arena);
  if (label_arena) arena_clear(label_arena);
  if (link_arena) arena_clear(link_arena);
  if (search_arena) arena_clear(search_arena);
  if (search_scratch_arena) arena_clear(search_scratch_arena);
  if (launch_arena) arena_clear(launch_arena);
  MemoryZeroStruct(content);
  content->outline_arena = outline_arena;
  content->label_arena = label_arena;
  content->link_arena = link_arena;
  content->search_arena = search_arena;
  content->search_scratch_arena = search_scratch_arena;
  content->launch_arena = launch_arena;
  content->outline_state = OctavoPdfContentLoad_Unavailable;
  content->outline_result = PdfReaderResult_NotOpen;
  content->label_state = OctavoPdfContentLoad_Unavailable;
  content->label_result = PdfReaderResult_NotOpen;
  content->link_state = OctavoPdfContentLoad_Unavailable;
  content->link_result = PdfReaderResult_NotOpen;
  content->search_state = OctavoPdfContentLoad_Unavailable;
  content->search_result = PdfReaderResult_NotOpen;
  content->search_active_index = -1;
  content->search_complete = 1;
}

FUNCTION B32
octavo_pdf_content_invariants_hold(const OctavoPdfContent *content)
{
  if (!content || !content->outline_arena || !content->label_arena ||
      !content->link_arena || !content->search_arena ||
      !content->search_scratch_arena || !content->launch_arena)
  {
    return 0;
  }
  if (arena_pos(content->outline_arena) >
        OCTAVO_PDF_CONTENT_OUTLINE_ARENA_CAP ||
      arena_pos(content->label_arena) >
        OCTAVO_PDF_CONTENT_LABEL_ARENA_CAP ||
      arena_pos(content->link_arena) >
        OCTAVO_PDF_CONTENT_LINK_ARENA_CAP ||
      arena_pos(content->search_arena) >
        OCTAVO_PDF_CONTENT_SEARCH_ARENA_CAP ||
      arena_pos(content->search_scratch_arena) >
        OCTAVO_PDF_CONTENT_SEARCH_SCRATCH_ARENA_CAP ||
      arena_pos(content->launch_arena) >
        OCTAVO_PDF_CONTENT_LAUNCH_ARENA_CAP)
  {
    return 0;
  }
  if ((content->document_generation == 0) != (content->page_count == 0))
    return 0;
  if (content->outline_item_count > OCTAVO_PDF_CONTENT_OUTLINE_ITEM_CAP ||
      content->outline_string_bytes >
        OCTAVO_PDF_CONTENT_OUTLINE_STRING_CAP ||
      content->page_label_size > OCTAVO_PDF_CONTENT_PAGE_LABEL_CAP ||
      content->page_link_count > OCTAVO_PDF_CONTENT_PAGE_LINK_CAP ||
      content->page_link_uri_size >
        OCTAVO_PDF_CONTENT_PAGE_LINK_URI_CAP ||
      content->search_query_size > OCTAVO_PDF_CONTENT_SEARCH_QUERY_CAP ||
      content->search_row_count > OCTAVO_PDF_CONTENT_SEARCH_ROW_CAP ||
      content->search_next_page > content->page_count)
  {
    return 0;
  }
  if (content->outline_state == OctavoPdfContentLoad_Ready &&
      (content->document_generation == 0 ||
       (content->outline_item_count != 0 && !content->outline_items) ||
       (content->outline_string_bytes != 0 && !content->outline_strings)))
  {
    return 0;
  }
  if (content->label_state == OctavoPdfContentLoad_Ready &&
      (content->label_document_generation != content->document_generation ||
       content->label_page_index >= content->page_count ||
       content->page_label_size == 0 || !content->page_label))
  {
    return 0;
  }
  if ((content->link_state == OctavoPdfContentLoad_Ready ||
       content->link_state == OctavoPdfContentLoad_Empty) &&
      (content->link_document_generation != content->document_generation ||
       content->link_page_index >= content->page_count ||
       (content->page_link_count != 0 && !content->page_links) ||
       (content->page_link_uri_size != 0 && !content->page_link_uri_bytes)))
  {
    return 0;
  }
  if ((content->search_state == OctavoPdfContentLoad_Loading ||
       content->search_state == OctavoPdfContentLoad_Ready ||
       content->search_state == OctavoPdfContentLoad_Empty) &&
      content->search_document_generation != content->document_generation)
  {
    return 0;
  }
  if ((content->search_state == OctavoPdfContentLoad_Loading ||
       content->search_state == OctavoPdfContentLoad_Ready) &&
      (content->search_query_size == 0 || !content->search_query ||
       !content->search_rows))
  {
    return 0;
  }
  return 1;
}

FUNCTION B32
octavo_pdf_content_bind_error_document(OctavoPdfContent *content,
                                       U64 document_generation,
                                       U32 page_count)
{
  if (!content || document_generation == 0 || page_count == 0)
    return 0;
  octavo_pdf_content_reset(content);
  content->document_generation = document_generation;
  content->page_count = page_count;
  content->outline_state = OctavoPdfContentLoad_Error;
  content->outline_result = PdfReaderResult_BackendError;
  content->label_state = OctavoPdfContentLoad_Error;
  content->label_result = PdfReaderResult_BackendError;
  content->link_state = OctavoPdfContentLoad_Error;
  content->link_result = PdfReaderResult_BackendError;
  content->search_document_generation = document_generation;
  content->search_state = OctavoPdfContentLoad_Error;
  content->search_result = PdfReaderResult_BackendError;
  content->search_complete = 1;
  return octavo_pdf_content_invariants_hold(content);
}

FUNCTION void
octavo_pdf_content_load_outline(OctavoPdfContent *content,
                                PdfReader *reader,
                                U64 document_generation,
                                U32 page_count)
{
  octavo_pdf_content_withdraw_outline(
    content, OctavoPdfContentLoad_Loading, PdfReaderResult_Ok);
  PdfReaderOutlineResult query = {0};
  PdfReaderResult result = pdf_reader_outline(
    reader, 0, (PdfReaderOutlineStorage){0}, &query);
  B32 has_output = query.required_item_count != 0 ||
                   query.required_string_bytes != 0;
  if (!octavo_pdf_content_outline_query_shape_is_valid(&query) ||
      !octavo_pdf_content_query_result_is_exact(result, has_output) ||
      query.document_generation != document_generation ||
      !octavo_pdf_content_identity_matches(
        content, reader, document_generation, page_count))
  {
    octavo_pdf_content_withdraw_outline(
      content, OctavoPdfContentLoad_Error,
      result == PdfReaderResult_Ok ? PdfReaderResult_BackendError : result);
    return;
  }
  if (!octavo_pdf_content_outline_requirements_fit(&query))
  {
    octavo_pdf_content_withdraw_outline(
      content, OctavoPdfContentLoad_LimitExceeded,
      PdfReaderResult_LimitExceeded);
    return;
  }
  if (!has_output)
  {
    content->outline_state = OctavoPdfContentLoad_Empty;
    content->outline_result = PdfReaderResult_Ok;
    return;
  }

  PdfReaderOutlineItem *items = query.required_item_count ?
    (PdfReaderOutlineItem *)octavo_pdf_content_arena_push(
      content->outline_arena, OCTAVO_PDF_CONTENT_OUTLINE_ARENA_CAP,
      (U64)query.required_item_count * sizeof(PdfReaderOutlineItem),
      ALIGN_OF(PdfReaderOutlineItem)) : 0;
  U8 *strings = query.required_string_bytes ?
    (U8 *)octavo_pdf_content_arena_push(
      content->outline_arena, OCTAVO_PDF_CONTENT_OUTLINE_ARENA_CAP,
      query.required_string_bytes, 1) : 0;
  if ((query.required_item_count && !items) ||
      (query.required_string_bytes && !strings))
  {
    octavo_pdf_content_withdraw_outline(
      content, OctavoPdfContentLoad_LimitExceeded,
      PdfReaderResult_LimitExceeded);
    return;
  }
  PdfReaderOutlineResult copied = {0};
  result = pdf_reader_outline(
    reader, 0,
    (PdfReaderOutlineStorage){
      .items = items,
      .item_capacity = query.required_item_count,
      .string_bytes = strings,
      .string_capacity = query.required_string_bytes,
    },
    &copied);
  if (result != PdfReaderResult_Ok ||
      !octavo_pdf_content_outline_copy_is_exact(&query, &copied) ||
      !octavo_pdf_content_identity_matches(
        content, reader, document_generation, page_count))
  {
    octavo_pdf_content_withdraw_outline(
      content, OctavoPdfContentLoad_Error,
      result == PdfReaderResult_Ok ? PdfReaderResult_BackendError : result);
    return;
  }
  content->outline_items = items;
  content->outline_strings = strings;
  content->outline_item_count = copied.written_item_count;
  content->outline_string_bytes = copied.written_string_bytes;
  content->outline_state = content->outline_item_count ?
    OctavoPdfContentLoad_Ready : OctavoPdfContentLoad_Empty;
  content->outline_result = PdfReaderResult_Ok;
}

FUNCTION void
octavo_pdf_content_load_label(OctavoPdfContent *content,
                              PdfReader *reader,
                              U64 document_generation,
                              U32 page_index,
                              U32 page_count)
{
  octavo_pdf_content_withdraw_label(
    content, OctavoPdfContentLoad_Loading, PdfReaderResult_Ok);
  PdfReaderPageLabelResult query = {0};
  PdfReaderResult result = pdf_reader_page_label(
    reader, page_index, 0, (PdfReaderByteBuffer){0}, &query);
  B32 has_output = query.required_bytes != 0;
  if (query.written_bytes != 0 ||
      !octavo_pdf_content_query_result_is_exact(result, has_output) ||
      query.document_generation != document_generation ||
      query.page_index != page_index ||
      !octavo_pdf_content_identity_matches(
        content, reader, document_generation, page_count))
  {
    octavo_pdf_content_withdraw_label(
      content, OctavoPdfContentLoad_Error,
      result == PdfReaderResult_Ok ? PdfReaderResult_BackendError : result);
    return;
  }
  if (query.required_bytes > OCTAVO_PDF_CONTENT_PAGE_LABEL_CAP)
  {
    octavo_pdf_content_withdraw_label(
      content, OctavoPdfContentLoad_LimitExceeded,
      PdfReaderResult_LimitExceeded);
    return;
  }
  if (!has_output)
  {
    content->label_document_generation = document_generation;
    content->label_page_index = page_index;
    content->label_state = OctavoPdfContentLoad_Empty;
    content->label_result = PdfReaderResult_Ok;
    return;
  }
  U8 *label = (U8 *)octavo_pdf_content_arena_push(
    content->label_arena, OCTAVO_PDF_CONTENT_LABEL_ARENA_CAP,
    query.required_bytes, 1);
  if (!label)
  {
    octavo_pdf_content_withdraw_label(
      content, OctavoPdfContentLoad_LimitExceeded,
      PdfReaderResult_LimitExceeded);
    return;
  }
  PdfReaderPageLabelResult copied = {0};
  result = pdf_reader_page_label(
    reader, page_index, 0,
    (PdfReaderByteBuffer){
      .bytes = label,
      .capacity = query.required_bytes,
    },
    &copied);
  if (result != PdfReaderResult_Ok ||
      copied.document_generation != document_generation ||
      copied.page_index != page_index ||
      copied.required_bytes != query.required_bytes ||
      copied.written_bytes != query.required_bytes ||
      !octavo_pdf_content_identity_matches(
        content, reader, document_generation, page_count))
  {
    octavo_pdf_content_withdraw_label(
      content, OctavoPdfContentLoad_Error,
      result == PdfReaderResult_Ok ? PdfReaderResult_BackendError : result);
    return;
  }
  content->label_document_generation = document_generation;
  content->label_page_index = page_index;
  content->page_label = label;
  content->page_label_size = copied.written_bytes;
  content->label_state = OctavoPdfContentLoad_Ready;
  content->label_result = PdfReaderResult_Ok;
}

FUNCTION void
octavo_pdf_content_load_links(OctavoPdfContent *content,
                              PdfReader *reader,
                              U64 document_generation,
                              U32 page_index,
                              U32 page_count)
{
  octavo_pdf_content_withdraw_links(
    content, OctavoPdfContentLoad_Loading, PdfReaderResult_Ok);
  PdfReaderPageLinksResult query = {0};
  PdfReaderResult result = pdf_reader_page_links(
    reader, page_index, 0, (PdfReaderLinkStorage){0}, &query);
  B32 has_output = query.required_link_count != 0 ||
                   query.required_uri_bytes != 0;
  if (!octavo_pdf_content_link_query_shape_is_valid(&query) ||
      !octavo_pdf_content_query_result_is_exact(result, has_output) ||
      query.document_generation != document_generation ||
      query.page_index != page_index ||
      !octavo_pdf_content_identity_matches(
        content, reader, document_generation, page_count))
  {
    octavo_pdf_content_withdraw_links(
      content, OctavoPdfContentLoad_Error,
      result == PdfReaderResult_Ok ? PdfReaderResult_BackendError : result);
    return;
  }
  if (!octavo_pdf_content_link_requirements_fit(&query))
  {
    octavo_pdf_content_withdraw_links(
      content, OctavoPdfContentLoad_LimitExceeded,
      PdfReaderResult_LimitExceeded);
    return;
  }
  if (!has_output)
  {
    content->link_document_generation = document_generation;
    content->link_page_index = page_index;
    content->link_state = OctavoPdfContentLoad_Empty;
    content->link_result = PdfReaderResult_Ok;
    return;
  }
  PdfReaderLink *links = query.required_link_count ?
    (PdfReaderLink *)octavo_pdf_content_arena_push(
      content->link_arena, OCTAVO_PDF_CONTENT_LINK_ARENA_CAP,
      (U64)query.required_link_count * sizeof(PdfReaderLink),
      ALIGN_OF(PdfReaderLink)) : 0;
  U8 *uris = query.required_uri_bytes ?
    (U8 *)octavo_pdf_content_arena_push(
      content->link_arena, OCTAVO_PDF_CONTENT_LINK_ARENA_CAP,
      query.required_uri_bytes, 1) : 0;
  if ((query.required_link_count && !links) ||
      (query.required_uri_bytes && !uris))
  {
    octavo_pdf_content_withdraw_links(
      content, OctavoPdfContentLoad_LimitExceeded,
      PdfReaderResult_LimitExceeded);
    return;
  }
  PdfReaderPageLinksResult copied = {0};
  result = pdf_reader_page_links(
    reader, page_index, 0,
    (PdfReaderLinkStorage){
      .links = links,
      .link_capacity = query.required_link_count,
      .uri_bytes = uris,
      .uri_capacity = query.required_uri_bytes,
    },
    &copied);
  if (result != PdfReaderResult_Ok ||
      !octavo_pdf_content_link_copy_is_exact(&query, &copied) ||
      !octavo_pdf_content_identity_matches(
        content, reader, document_generation, page_count))
  {
    octavo_pdf_content_withdraw_links(
      content, OctavoPdfContentLoad_Error,
      result == PdfReaderResult_Ok ? PdfReaderResult_BackendError : result);
    return;
  }
  content->link_document_generation = document_generation;
  content->link_page_index = page_index;
  content->page_links = links;
  content->page_link_uri_bytes = uris;
  content->page_link_count = copied.written_link_count;
  content->page_link_uri_size = copied.written_uri_bytes;
  content->link_state = content->page_link_count ?
    OctavoPdfContentLoad_Ready : OctavoPdfContentLoad_Empty;
  content->link_result = PdfReaderResult_Ok;
}

FUNCTION B32
octavo_pdf_content_begin_document(OctavoPdfContent *content,
                                  PdfReader *reader,
                                  U64 document_generation,
                                  U32 page_index,
                                  U32 page_count)
{
  if (!content || !reader || document_generation == 0 || page_count == 0 ||
      page_index >= page_count || !reader->initialized ||
      !reader->document_open ||
      reader->document_generation != document_generation ||
      reader->page_count != page_count)
  {
    return 0;
  }
  octavo_pdf_content_reset(content);
  content->document_generation = document_generation;
  content->page_count = page_count;
  content->search_document_generation = document_generation;
  content->search_state = OctavoPdfContentLoad_Empty;
  content->search_result = PdfReaderResult_Ok;
  octavo_pdf_content_load_outline(
    content, reader, document_generation, page_count);
  octavo_pdf_content_refresh_current_page(
    content, reader, document_generation, page_index, page_count);
  return octavo_pdf_content_invariants_hold(content);
}

FUNCTION void
octavo_pdf_content_refresh_current_page(OctavoPdfContent *content,
                                        PdfReader *reader,
                                        U64 document_generation,
                                        U32 page_index,
                                        U32 page_count)
{
  if (!octavo_pdf_content_identity_matches(
        content, reader, document_generation, page_count) ||
      page_index >= page_count || reader->current_page_index != page_index)
  {
    octavo_pdf_content_withdraw_label(
      content, OctavoPdfContentLoad_Error, PdfReaderResult_InvalidInput);
    octavo_pdf_content_withdraw_links(
      content, OctavoPdfContentLoad_Error, PdfReaderResult_InvalidInput);
    return;
  }
  octavo_pdf_content_load_label(
    content, reader, document_generation, page_index, page_count);
  octavo_pdf_content_load_links(
    content, reader, document_generation, page_index, page_count);
}

FUNCTION U64
octavo_pdf_content_outline_key(U32 index)
{
  return index < OCTAVO_PDF_CONTENT_OUTLINE_ITEM_CAP ?
    OCTAVO_PDF_CONTENT_OUTLINE_KEY_PREFIX | ((U64)index + 1) : 0;
}

FUNCTION const PdfReaderOutlineItem *
octavo_pdf_content_outline_item(const OctavoPdfContent *content,
                                U64 document_generation,
                                U32 index,
                                OctavoPdfContentSpan *out_title)
{
  if (out_title) *out_title = (OctavoPdfContentSpan){0};
  if (!content || !out_title ||
      content->outline_state != OctavoPdfContentLoad_Ready ||
      content->document_generation != document_generation ||
      index >= content->outline_item_count || !content->outline_items)
  {
    return 0;
  }
  const PdfReaderOutlineItem *item = content->outline_items + index;
  if (!octavo_pdf_content_span_in_buffer(
        item->title_offset, item->title_size,
        content->outline_string_bytes))
  {
    return 0;
  }
  out_title->str = item->title_size ?
    content->outline_strings + item->title_offset : 0;
  out_title->size = item->title_size;
  return item;
}

FUNCTION B32
octavo_pdf_content_activation_from_target(
  PdfReaderLinkTargetKind kind,
  PdfReaderDestination destination,
  OctavoPdfContentSpan uri,
  U32 page_count,
  OctavoPdfContentActivation *out_activation)
{
  if (out_activation) *out_activation = (OctavoPdfContentActivation){0};
  if (!out_activation) return 0;
  if (kind == PdfReaderLinkTargetKind_Internal)
  {
    if (!destination.valid || destination.page_index >= page_count) return 0;
  }
  else if (kind == PdfReaderLinkTargetKind_External)
  {
    if (!octavo_pdf_content_external_uri_is_allowed(uri)) return 0;
  }
  else
  {
    return 0;
  }
  out_activation->target_kind = kind;
  out_activation->destination = destination;
  out_activation->external_uri = uri;
  return 1;
}

FUNCTION B32
octavo_pdf_content_resolve_outline_key(
  const OctavoPdfContent *content,
  U64 document_generation,
  U64 key,
  OctavoPdfContentActivation *out_activation)
{
  if (out_activation) *out_activation = (OctavoPdfContentActivation){0};
  if (!content || !out_activation ||
      (key & OCTAVO_PDF_CONTENT_KEY_PREFIX_MASK) !=
        OCTAVO_PDF_CONTENT_OUTLINE_KEY_PREFIX)
  {
    return 0;
  }
  U64 encoded = key & OCTAVO_PDF_CONTENT_KEY_INDEX_MASK;
  if (encoded == 0 || encoded - 1 > UINT32_MAX) return 0;
  U32 index = (U32)(encoded - 1);
  OctavoPdfContentSpan title = {0};
  const PdfReaderOutlineItem *item = octavo_pdf_content_outline_item(
    content, document_generation, index, &title);
  (void)title;
  if (!item) return 0;
  OctavoPdfContentSpan uri = {0};
  if (item->uri_size)
  {
    if (!octavo_pdf_content_span_in_buffer(
          item->uri_offset, item->uri_size,
          content->outline_string_bytes))
    {
      return 0;
    }
    uri.str = content->outline_strings + item->uri_offset;
    uri.size = item->uri_size;
  }
  return octavo_pdf_content_activation_from_target(
    item->target_kind, item->destination, uri, content->page_count,
    out_activation);
}

FUNCTION OctavoPdfContentSpan
octavo_pdf_content_page_label(const OctavoPdfContent *content,
                              U64 document_generation,
                              U32 page_index)
{
  OctavoPdfContentSpan result = {0};
  if (content && content->label_state == OctavoPdfContentLoad_Ready &&
      content->label_document_generation == document_generation &&
      content->label_page_index == page_index && content->page_label &&
      content->page_label_size)
  {
    result.str = content->page_label;
    result.size = content->page_label_size;
  }
  return result;
}

FUNCTION void
octavo_pdf_content_cancel_search(OctavoPdfContent *content)
{
  if (!content) return;
  U64 serial = content->search_serial;
  U64 generation = content->document_generation;
  octavo_pdf_content_withdraw_search(
    content, OctavoPdfContentLoad_Empty, PdfReaderResult_Ok);
  content->search_serial = serial;
  content->search_document_generation = generation;
}

FUNCTION B32
octavo_pdf_content_begin_search(OctavoPdfContent *content,
                                PdfReader *reader,
                                U64 document_generation,
                                U32 page_count,
                                String8 query)
{
  if (!octavo_pdf_content_identity_matches(
        content, reader, document_generation, page_count) ||
      query.size > OCTAVO_PDF_CONTENT_SEARCH_QUERY_CAP ||
      (query.size != 0 && !query.str) ||
      !base_unicode_utf8_validate(query))
  {
    return 0;
  }
  for (U64 index = 0; index < query.size; index += 1)
    if (query.str[index] == 0) return 0;
  U64 next_serial = content->search_serial + 1;
  if (next_serial == 0) next_serial = 1;
  octavo_pdf_content_withdraw_search(
    content, query.size ? OctavoPdfContentLoad_Loading :
                          OctavoPdfContentLoad_Empty,
    PdfReaderResult_Ok);
  content->search_serial = next_serial;
  content->search_document_generation = document_generation;
  if (query.size == 0) return octavo_pdf_content_invariants_hold(content);

  OctavoPdfContentSearchRow *rows =
    (OctavoPdfContentSearchRow *)octavo_pdf_content_arena_push(
      content->search_arena, OCTAVO_PDF_CONTENT_SEARCH_ARENA_CAP,
      sizeof(OctavoPdfContentSearchRow) *
        OCTAVO_PDF_CONTENT_SEARCH_ROW_CAP,
      ALIGN_OF(OctavoPdfContentSearchRow));
  U8 *query_copy = (U8 *)octavo_pdf_content_arena_push(
    content->search_arena, OCTAVO_PDF_CONTENT_SEARCH_ARENA_CAP,
    query.size, 1);
  if (!rows || !query_copy)
  {
    octavo_pdf_content_withdraw_search(
      content, OctavoPdfContentLoad_LimitExceeded,
      PdfReaderResult_LimitExceeded);
    content->search_document_generation = document_generation;
    content->search_serial = next_serial;
    return 0;
  }
  MemoryZero(rows, sizeof(OctavoPdfContentSearchRow) *
                   OCTAVO_PDF_CONTENT_SEARCH_ROW_CAP);
  MemoryCopy(query_copy, query.str, query.size);
  content->search_query = query_copy;
  content->search_query_size = query.size;
  content->search_rows = rows;
  content->search_next_page = 0;
  content->search_active_index = -1;
  content->search_complete = 0;
  content->search_has_more = 1;
  return octavo_pdf_content_invariants_hold(content);
}

FUNCTION U64
octavo_pdf_content_search_key(U32 index)
{
  return index < OCTAVO_PDF_CONTENT_SEARCH_ROW_CAP ?
    OCTAVO_PDF_CONTENT_SEARCH_KEY_PREFIX | ((U64)index + 1) : 0;
}

FUNCTION B32
octavo_pdf_content_search_pump(OctavoPdfContent *content,
                               PdfReader *reader,
                               U64 document_generation,
                               U32 page_count,
                               B32 *out_did_work)
{
  if (out_did_work) *out_did_work = 0;
  if (!content || !out_did_work ||
      content->search_state != OctavoPdfContentLoad_Loading)
  {
    return content && out_did_work;
  }
  if (!octavo_pdf_content_identity_matches(
        content, reader, document_generation, page_count) ||
      content->search_document_generation != document_generation ||
      content->search_next_page >= page_count ||
      !content->search_query || content->search_query_size == 0 ||
      !content->search_rows)
  {
    octavo_pdf_content_withdraw_search(
      content, OctavoPdfContentLoad_Error, PdfReaderResult_InvalidInput);
    content->search_document_generation = document_generation;
    return 0;
  }

  U32 page_index = content->search_next_page;
  arena_clear(content->search_scratch_arena);
  PdfReaderSearchRequest request = {
    .page_index = page_index,
    .query = str8(content->search_query, content->search_query_size),
    .flags = PdfReaderSearchFlag_IgnoreCase,
  };
  PdfReaderSearchResult query = {0};
  PdfReaderResult result = pdf_reader_search_page(
    reader, request, (PdfReaderSearchStorage){0}, &query);
  *out_did_work = 1;
  B32 has_output = query.required_hit_count != 0 ||
                   query.required_quad_count != 0;
  if (!octavo_pdf_content_search_query_shape_is_valid(&query) ||
      !octavo_pdf_content_query_result_is_exact(result, has_output) ||
      query.document_generation != document_generation ||
      query.page_index != page_index ||
      !octavo_pdf_content_identity_matches(
        content, reader, document_generation, page_count))
  {
    octavo_pdf_content_withdraw_search(
      content, OctavoPdfContentLoad_Error,
      result == PdfReaderResult_Ok ? PdfReaderResult_BackendError : result);
    content->search_document_generation = document_generation;
    return 0;
  }
  if (!octavo_pdf_content_search_requirements_fit(&query))
  {
    octavo_pdf_content_withdraw_search(
      content, OctavoPdfContentLoad_LimitExceeded,
      PdfReaderResult_LimitExceeded);
    content->search_document_generation = document_generation;
    return 0;
  }

  PdfReaderSearchHit *hits = query.required_hit_count ?
    (PdfReaderSearchHit *)octavo_pdf_content_arena_push(
      content->search_scratch_arena,
      OCTAVO_PDF_CONTENT_SEARCH_SCRATCH_ARENA_CAP,
      (U64)query.required_hit_count * sizeof(PdfReaderSearchHit),
      ALIGN_OF(PdfReaderSearchHit)) : 0;
  PdfReaderQuad *quads = query.required_quad_count ?
    (PdfReaderQuad *)octavo_pdf_content_arena_push(
      content->search_scratch_arena,
      OCTAVO_PDF_CONTENT_SEARCH_SCRATCH_ARENA_CAP,
      (U64)query.required_quad_count * sizeof(PdfReaderQuad),
      ALIGN_OF(PdfReaderQuad)) : 0;
  if ((query.required_hit_count && !hits) ||
      (query.required_quad_count && !quads))
  {
    octavo_pdf_content_withdraw_search(
      content, OctavoPdfContentLoad_LimitExceeded,
      PdfReaderResult_LimitExceeded);
    content->search_document_generation = document_generation;
    return 0;
  }
  PdfReaderSearchResult copied = query;
  if (has_output)
  {
    MemoryZeroStruct(&copied);
    result = pdf_reader_search_page(
      reader, request,
      (PdfReaderSearchStorage){
        .hits = hits,
        .hit_capacity = query.required_hit_count,
        .quads = quads,
        .quad_capacity = query.required_quad_count,
      },
      &copied);
    if (result != PdfReaderResult_Ok ||
        !octavo_pdf_content_search_copy_is_exact(
          &query, &copied, hits) ||
        !octavo_pdf_content_identity_matches(
          content, reader, document_generation, page_count))
    {
      octavo_pdf_content_withdraw_search(
        content, OctavoPdfContentLoad_Error,
        result == PdfReaderResult_Ok ? PdfReaderResult_BackendError : result);
      content->search_document_generation = document_generation;
      return 0;
    }
  }

  if (content->search_total_count > UINT64_MAX - copied.written_hit_count)
  {
    octavo_pdf_content_withdraw_search(
      content, OctavoPdfContentLoad_LimitExceeded,
      PdfReaderResult_LimitExceeded);
    content->search_document_generation = document_generation;
    return 0;
  }
  U32 available = OCTAVO_PDF_CONTENT_SEARCH_ROW_CAP -
                  content->search_row_count;
  U32 publish_count = MIN(available, copied.written_hit_count);
  for (U32 index = 0; index < publish_count; index += 1)
  {
    U32 row_index = content->search_row_count + index;
    char *page_label = (char *)octavo_pdf_content_arena_push(
      content->search_arena, OCTAVO_PDF_CONTENT_SEARCH_ARENA_CAP,
      32, 1);
    if (!page_label)
    {
      octavo_pdf_content_withdraw_search(
        content, OctavoPdfContentLoad_LimitExceeded,
        PdfReaderResult_LimitExceeded);
      content->search_document_generation = document_generation;
      return 0;
    }
    U64 page_label_size = cstr_format(
      page_label, 32, "Page %u", page_index + 1);
    if (page_label_size == 0 || page_label_size >= 32)
    {
      octavo_pdf_content_withdraw_search(
        content, OctavoPdfContentLoad_Error,
        PdfReaderResult_BackendError);
      content->search_document_generation = document_generation;
      return 0;
    }
    content->search_rows[row_index] = (OctavoPdfContentSearchRow){
      .key = octavo_pdf_content_search_key(row_index),
      .page_index = page_index,
      .match_index_on_page = index,
      .page_label = {
        .str = (const U8 *)page_label,
        .size = page_label_size,
      },
      .excerpt = {
        .str = content->search_query,
        .size = content->search_query_size,
      },
    };
  }
  content->search_row_count += publish_count;
  content->search_total_count += copied.written_hit_count;
  content->search_next_page += 1;
  if (content->search_active_index < 0 && content->search_row_count != 0)
    content->search_active_index = 0;
  content->search_complete = content->search_next_page == page_count;
  content->search_has_more = !content->search_complete ||
    content->search_total_count > content->search_row_count;
  if (content->search_complete)
  {
    content->search_state = content->search_total_count ?
      OctavoPdfContentLoad_Ready : OctavoPdfContentLoad_Empty;
    content->search_result = PdfReaderResult_Ok;
  }
  arena_clear(content->search_scratch_arena);
  return octavo_pdf_content_invariants_hold(content);
}

FUNCTION B32
octavo_pdf_content_resolve_search_key(const OctavoPdfContent *content,
                                      U64 document_generation,
                                      U64 key,
                                      U32 *out_page_index,
                                      S32 *out_row_index)
{
  if (out_page_index) *out_page_index = 0;
  if (out_row_index) *out_row_index = -1;
  if (!content || !out_page_index || !out_row_index ||
      content->search_document_generation != document_generation ||
      (content->search_state != OctavoPdfContentLoad_Loading &&
       content->search_state != OctavoPdfContentLoad_Ready) ||
      (key & OCTAVO_PDF_CONTENT_KEY_PREFIX_MASK) !=
        OCTAVO_PDF_CONTENT_SEARCH_KEY_PREFIX)
  {
    return 0;
  }
  U64 encoded = key & OCTAVO_PDF_CONTENT_KEY_INDEX_MASK;
  if (encoded == 0 || encoded - 1 >= content->search_row_count) return 0;
  U32 index = (U32)(encoded - 1);
  const OctavoPdfContentSearchRow *row = content->search_rows + index;
  if (row->key != key || row->page_index >= content->page_count) return 0;
  *out_page_index = row->page_index;
  *out_row_index = (S32)index;
  return 1;
}

FUNCTION B32
octavo_pdf_content_step_search(OctavoPdfContent *content,
                               U64 document_generation,
                               S32 direction,
                               U32 *out_page_index)
{
  if (out_page_index) *out_page_index = 0;
  if (!content || !out_page_index || direction == 0 ||
      content->search_document_generation != document_generation ||
      content->search_row_count == 0 || !content->search_rows)
  {
    return 0;
  }
  S32 count = (S32)content->search_row_count;
  S32 index = content->search_active_index;
  if (index < 0 || index >= count) index = direction < 0 ? 0 : -1;
  index += direction < 0 ? -1 : 1;
  if (index < 0) index = count - 1;
  if (index >= count) index = 0;
  content->search_active_index = index;
  *out_page_index = content->search_rows[index].page_index;
  return *out_page_index < content->page_count;
}

FUNCTION B32
octavo_pdf_content_hex_nibble(U8 byte, U8 *out_value)
{
  if (out_value) *out_value = 0;
  if (!out_value) return 0;
  if (byte >= '0' && byte <= '9') *out_value = byte - '0';
  else if (byte >= 'a' && byte <= 'f') *out_value = byte - 'a' + 10;
  else if (byte >= 'A' && byte <= 'F') *out_value = byte - 'A' + 10;
  else return 0;
  return 1;
}

FUNCTION B32
octavo_pdf_content_ascii_prefix_case_insensitive(
  OctavoPdfContentSpan text,
  const char *prefix,
  U64 prefix_size)
{
  if (!text.str || !prefix || text.size < prefix_size) return 0;
  for (U64 index = 0; index < prefix_size; index += 1)
  {
    U8 a = text.str[index];
    U8 b = (U8)prefix[index];
    if (a >= 'A' && a <= 'Z') a = (U8)(a - 'A' + 'a');
    if (b >= 'A' && b <= 'Z') b = (U8)(b - 'A' + 'a');
    if (a != b) return 0;
  }
  return 1;
}

FUNCTION B32
octavo_pdf_content_network_authority_has_host(
  OctavoPdfContentSpan uri,
  U64 authority_start,
  U64 authority_end)
{
  if (!uri.str || authority_start >= authority_end ||
      authority_end > uri.size)
  {
    return 0;
  }
  U64 host_start = authority_start;
  for (U64 index = authority_start; index < authority_end; index += 1)
    if (uri.str[index] == '@') host_start = index + 1;
  if (host_start >= authority_end) return 0;
  if (uri.str[host_start] == '[')
  {
    U64 close = host_start + 1;
    while (close < authority_end && uri.str[close] != ']') close += 1;
    return close > host_start + 1 && close < authority_end;
  }
  U64 host_end = authority_end;
  for (U64 index = host_start; index < authority_end; index += 1)
  {
    if (uri.str[index] == ':')
    {
      host_end = index;
      break;
    }
  }
  return host_end > host_start;
}

FUNCTION B32
octavo_pdf_content_external_uri_is_allowed(OctavoPdfContentSpan uri)
{
  if (!uri.str || uri.size == 0 ||
      uri.size > OCTAVO_PDF_CONTENT_EXTERNAL_URI_CAP ||
      !base_unicode_utf8_validate(str8((U8 *)uri.str, uri.size)))
  {
    return 0;
  }
  for (U64 at = 0; at < uri.size;)
  {
    BaseUnicodeDecode decode = base_unicode_utf8_decode(
      str8((U8 *)uri.str, uri.size), at);
    if (!decode.valid || decode.advance == 0 ||
        decode.scalar <= 0x20u ||
        (decode.scalar >= 0x7fu && decode.scalar <= 0x9fu))
    {
      return 0;
    }
    at += decode.advance;
  }
  U64 payload = 0;
  B32 network = 0;
  if (octavo_pdf_content_ascii_prefix_case_insensitive(
        uri, "https://", 8))
  {
    payload = 8;
    network = 1;
  }
  else if (octavo_pdf_content_ascii_prefix_case_insensitive(
             uri, "http://", 7))
  {
    payload = 7;
    network = 1;
  }
  else if (octavo_pdf_content_ascii_prefix_case_insensitive(
             uri, "mailto:", 7))
  {
    payload = 7;
  }
  else
  {
    return 0;
  }
  if (payload >= uri.size) return 0;
  if (uri.str[payload] == '/' || uri.str[payload] == '\\' ||
      uri.str[payload] == '.' || uri.str[payload] == '~')
  {
    return 0;
  }
  if (network)
  {
    U64 authority_end = payload;
    while (authority_end < uri.size &&
           uri.str[authority_end] != '/' &&
           uri.str[authority_end] != '?' &&
           uri.str[authority_end] != '#')
    {
      authority_end += 1;
    }
    if (!octavo_pdf_content_network_authority_has_host(
          uri, payload, authority_end))
    {
      return 0;
    }
  }
  for (U64 index = payload; index < uri.size; index += 1)
  {
    U8 byte = uri.str[index];
    if (byte <= 0x20u || byte == 0x7fu || byte == '\\' || byte == '"')
      return 0;
    if (byte == '%' && index + 2 < uri.size)
    {
      U8 high = 0;
      U8 low = 0;
      if (!octavo_pdf_content_hex_nibble(uri.str[index + 1], &high) ||
          !octavo_pdf_content_hex_nibble(uri.str[index + 2], &low))
      {
        return 0;
      }
      U8 decoded = (U8)((high << 4) | low);
      if (decoded <= 0x20u || decoded == 0x7fu || decoded == '\\')
        return 0;
      index += 2;
    }
    else if (byte == '%')
    {
      return 0;
    }
  }
  return 1;
}

FUNCTION B32
octavo_pdf_content_external_uri_wide(OctavoPdfContent *content,
                                     OctavoPdfContentSpan uri,
                                     const wchar_t **out_uri)
{
  if (out_uri) *out_uri = 0;
  if (!content || !out_uri || !content->launch_arena ||
      !octavo_pdf_content_external_uri_is_allowed(uri) ||
      uri.size > INT32_MAX)
  {
    return 0;
  }
  arena_clear(content->launch_arena);
  int wide_count = MultiByteToWideChar(
    CP_UTF8, MB_ERR_INVALID_CHARS, (const char *)uri.str, (int)uri.size,
    0, 0);
  if (wide_count <= 0) return 0;
  U64 wide_bytes = ((U64)wide_count + 1) * sizeof(wchar_t);
  wchar_t *wide = (wchar_t *)octavo_pdf_content_arena_push(
    content->launch_arena, OCTAVO_PDF_CONTENT_LAUNCH_ARENA_CAP,
    wide_bytes, ALIGN_OF(wchar_t));
  if (!wide) return 0;
  int converted = MultiByteToWideChar(
    CP_UTF8, MB_ERR_INVALID_CHARS, (const char *)uri.str, (int)uri.size,
    wide, wide_count);
  if (converted != wide_count)
  {
    arena_clear(content->launch_arena);
    return 0;
  }
  wide[wide_count] = 0;
  *out_uri = wide;
  return 1;
}

FUNCTION B32
octavo_pdf_content_hit_test_link(
  const OctavoPdfContent *content,
  U64 document_generation,
  U32 page_index,
  PdfReaderPoint point,
  OctavoPdfContentActivation *out_activation)
{
  if (out_activation) *out_activation = (OctavoPdfContentActivation){0};
  if (!content || !out_activation || !isfinite(point.x) ||
      !isfinite(point.y) ||
      content->link_state != OctavoPdfContentLoad_Ready ||
      content->link_document_generation != document_generation ||
      content->link_page_index != page_index ||
      !content->page_links)
  {
    return 0;
  }
  B32 found = 0;
  F64 best_area = 0.0;
  OctavoPdfContentActivation best = {0};
  for (U32 index = 0; index < content->page_link_count; index += 1)
  {
    const PdfReaderLink *link = content->page_links + index;
    PdfReaderRect bounds = link->bounds;
    if (!isfinite(bounds.x0) || !isfinite(bounds.x1) ||
        !isfinite(bounds.y0) || !isfinite(bounds.y1) ||
        bounds.x1 <= bounds.x0 || bounds.y1 <= bounds.y0 ||
        point.x < bounds.x0 || point.x > bounds.x1 ||
        point.y < bounds.y0 || point.y > bounds.y1)
    {
      continue;
    }
    OctavoPdfContentSpan uri = {0};
    if (link->uri_size)
    {
      if (!octavo_pdf_content_span_in_buffer(
            link->uri_offset, link->uri_size,
            content->page_link_uri_size))
      {
        continue;
      }
      uri.str = content->page_link_uri_bytes + link->uri_offset;
      uri.size = link->uri_size;
    }
    OctavoPdfContentActivation candidate = {0};
    if (!octavo_pdf_content_activation_from_target(
          link->target_kind, link->destination, uri, content->page_count,
          &candidate))
    {
      continue;
    }
    F64 area = (F64)(bounds.x1 - bounds.x0) *
               (F64)(bounds.y1 - bounds.y0);
    if (!found || area < best_area)
    {
      found = 1;
      best_area = area;
      best = candidate;
    }
  }
  if (found) *out_activation = best;
  return found;
}

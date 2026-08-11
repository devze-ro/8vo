#include "octavo_pdf_selection.h"

#include "base/base_unicode.h"

#include <math.h>

_Static_assert((U64)OCTAVO_PDF_SELECTION_TEXT_BYTE_CAP <=
                 (U64)PDF_READER_MAX_SELECTION_SNAPSHOT_TEXT_BYTES,
               "8vo PDF selection byte cap must not exceed Reader0");
_Static_assert((U64)OCTAVO_PDF_SELECTION_SCALAR_CAP <=
                 (U64)PDF_READER_MAX_SELECTION_SNAPSHOT_SCALARS,
               "8vo PDF selection scalar cap must not exceed Reader0");
_Static_assert((U32)OCTAVO_PDF_SELECTION_QUAD_CAP <=
                 (U32)PDF_READER_MAX_SELECTION_SNAPSHOT_QUADS,
               "8vo PDF selection quad cap must not exceed Reader0");
_Static_assert(OCTAVO_PDF_SELECTION_ARENA_CAP >=
                 OCTAVO_PDF_SELECTION_TEXT_BYTE_CAP +
                 OCTAVO_PDF_SELECTION_QUAD_CAP * sizeof(PdfReaderQuad) + 64,
               "8vo PDF selection Arena must contain one capped snapshot");

FUNCTION Arena *
octavo_pdf_selection_arena(void)
{
  ArenaParams params = {
    .reserve_size = OCTAVO_PDF_SELECTION_ARENA_CAP,
    .commit_size = 64 * 1024,
  };
  return arena_alloc(&params);
}

FUNCTION U64
octavo_pdf_selection_next_generation(U64 generation)
{
  generation += 1;
  if (generation == 0) generation = 1;
  return generation;
}

FUNCTION B32
octavo_pdf_selection_point_is_finite(PdfReaderPoint point)
{
  return isfinite(point.x) && isfinite(point.y);
}

FUNCTION B32
octavo_pdf_selection_quad_is_publishable(PdfReaderQuad quad)
{
  if (!octavo_pdf_selection_point_is_finite(quad.upper_left) ||
      !octavo_pdf_selection_point_is_finite(quad.upper_right) ||
      !octavo_pdf_selection_point_is_finite(quad.lower_left) ||
      !octavo_pdf_selection_point_is_finite(quad.lower_right))
  {
    return 0;
  }

  F64 min_x = MIN(MIN((F64)quad.upper_left.x,
                      (F64)quad.upper_right.x),
                  MIN((F64)quad.lower_left.x,
                      (F64)quad.lower_right.x));
  F64 min_y = MIN(MIN((F64)quad.upper_left.y,
                      (F64)quad.upper_right.y),
                  MIN((F64)quad.lower_left.y,
                      (F64)quad.lower_right.y));
  F64 max_x = MAX(MAX((F64)quad.upper_left.x,
                      (F64)quad.upper_right.x),
                  MAX((F64)quad.lower_left.x,
                      (F64)quad.lower_right.x));
  F64 max_y = MAX(MAX((F64)quad.upper_left.y,
                      (F64)quad.upper_right.y),
                  MAX((F64)quad.lower_left.y,
                      (F64)quad.lower_right.y));
  /* MuPDF quads may be rotated. Measure canonical ul->ur->lr->ll order
   * without imposing horizontal-text coordinate ordering. */
  F64 area_twice =
    (F64)quad.upper_left.x * (F64)quad.upper_right.y -
      (F64)quad.upper_right.x * (F64)quad.upper_left.y +
    (F64)quad.upper_right.x * (F64)quad.lower_right.y -
      (F64)quad.lower_right.x * (F64)quad.upper_right.y +
    (F64)quad.lower_right.x * (F64)quad.lower_left.y -
      (F64)quad.lower_left.x * (F64)quad.lower_right.y +
    (F64)quad.lower_left.x * (F64)quad.upper_left.y -
      (F64)quad.upper_left.x * (F64)quad.lower_left.y;
  return isfinite(min_x) && isfinite(min_y) &&
    isfinite(max_x) && isfinite(max_y) && isfinite(area_twice) &&
    max_x > min_x && max_y > min_y && area_twice != 0.0;
}

FUNCTION B32
octavo_pdf_selection_utf8_scalar_count(String8 text, U64 *out_count)
{
  if (out_count) *out_count = 0;
  if (!out_count || (text.size != 0 && !text.str) ||
      !base_unicode_utf8_validate(text))
  {
    return 0;
  }
  U64 count = 0;
  for (U64 at = 0; at < text.size;)
  {
    BaseUnicodeDecode decoded = base_unicode_utf8_decode(text, at);
    if (!decoded.valid || decoded.advance == 0 ||
        decoded.advance > text.size - at || count == UINT64_MAX)
    {
      return 0;
    }
    at += decoded.advance;
    count += 1;
  }
  *out_count = count;
  return 1;
}

FUNCTION B32
octavo_pdf_selection_snapshot_shape_is_valid(
  const PdfReaderSelectionSnapshot *snapshot,
  U64 expected_document_generation,
  U64 expected_publication_generation,
  U64 expected_selection_generation,
  U32 expected_page_index)
{
  if (!snapshot || expected_document_generation == 0 ||
      expected_publication_generation == 0 ||
      expected_selection_generation == 0 ||
      snapshot->document_generation != expected_document_generation ||
      snapshot->publication_generation != expected_publication_generation ||
      snapshot->selection_generation != expected_selection_generation ||
      snapshot->page_index != expected_page_index ||
      snapshot->text.size > OCTAVO_PDF_SELECTION_TEXT_BYTE_CAP ||
      snapshot->scalar_count > OCTAVO_PDF_SELECTION_SCALAR_CAP ||
      snapshot->quad_count > OCTAVO_PDF_SELECTION_QUAD_CAP ||
      (snapshot->text.size != 0 && !snapshot->text.str) ||
      (snapshot->quad_count != 0 && !snapshot->quads) ||
      ((snapshot->text.size == 0) != (snapshot->scalar_count == 0)) ||
      ((snapshot->text.size == 0) != (snapshot->quad_count == 0)) ||
      !octavo_pdf_selection_point_is_finite(snapshot->snapped_anchor) ||
      !octavo_pdf_selection_point_is_finite(snapshot->snapped_focus))
  {
    return 0;
  }

  U64 scalar_count = 0;
  if (!octavo_pdf_selection_utf8_scalar_count(snapshot->text,
                                               &scalar_count) ||
      scalar_count != snapshot->scalar_count)
  {
    return 0;
  }
  for (U32 index = 0; index < snapshot->quad_count; index += 1)
  {
    if (!octavo_pdf_selection_quad_is_publishable(snapshot->quads[index]))
      return 0;
  }
  return 1;
}

FUNCTION void
octavo_pdf_selection_withdraw_publication(OctavoPdfSelection *selection)
{
  if (!selection) return;
  if (selection->published_arena) arena_clear(selection->published_arena);
  if (selection->candidate_arena) arena_clear(selection->candidate_arena);
  MemoryZeroStruct(&selection->snapshot);
  selection->ready = 0;
}

FUNCTION void
octavo_pdf_selection_retire(OctavoPdfSelection *selection,
                            PdfReaderResult result)
{
  if (!selection) return;
  Arena *published_arena = selection->published_arena;
  Arena *candidate_arena = selection->candidate_arena;
  U64 generation = octavo_pdf_selection_next_generation(
    selection->gesture_generation);
  if (published_arena) arena_clear(published_arena);
  if (candidate_arena) arena_clear(candidate_arena);
  MemoryZeroStruct(selection);
  selection->published_arena = published_arena;
  selection->candidate_arena = candidate_arena;
  selection->gesture_generation = generation;
  selection->last_result = result;
}

FUNCTION B32
octavo_pdf_selection_init(OctavoPdfSelection *selection)
{
  if (!selection) return 0;
  MemoryZeroStruct(selection);
  selection->published_arena = octavo_pdf_selection_arena();
  selection->candidate_arena = octavo_pdf_selection_arena();
  if (!selection->published_arena || !selection->candidate_arena)
  {
    octavo_pdf_selection_release(selection);
    return 0;
  }
  selection->gesture_generation = 1;
  selection->last_result = PdfReaderResult_NotOpen;
  return 1;
}

FUNCTION void
octavo_pdf_selection_release(OctavoPdfSelection *selection)
{
  if (!selection) return;
  if (selection->published_arena) arena_release(selection->published_arena);
  if (selection->candidate_arena) arena_release(selection->candidate_arena);
  MemoryZeroStruct(selection);
}

FUNCTION void
octavo_pdf_selection_reset(OctavoPdfSelection *selection)
{
  octavo_pdf_selection_retire(selection, PdfReaderResult_NotOpen);
}

FUNCTION B32
octavo_pdf_selection_invariants_hold(const OctavoPdfSelection *selection)
{
  if (!selection || !selection->published_arena ||
      !selection->candidate_arena || selection->gesture_generation == 0 ||
      arena_pos(selection->published_arena) >
        OCTAVO_PDF_SELECTION_ARENA_CAP ||
      arena_pos(selection->candidate_arena) >
        OCTAVO_PDF_SELECTION_ARENA_CAP ||
      (selection->pointer_promoted && !selection->pointer_armed))
  {
    return 0;
  }
  if (!selection->pointer_armed &&
      (selection->gesture_document_generation != 0 ||
       selection->gesture_publication_generation != 0 ||
       selection->gesture_page_index != 0))
  {
    return 0;
  }
  if (selection->pointer_armed &&
      (selection->gesture_document_generation == 0 ||
       selection->gesture_publication_generation == 0 ||
       !octavo_pdf_selection_point_is_finite(selection->gesture_anchor)))
  {
    return 0;
  }
  if (!selection->ready)
  {
    return selection->snapshot.text.str == 0 &&
      selection->snapshot.text.size == 0 &&
      selection->snapshot.scalar_count == 0 &&
      selection->snapshot.quads == 0 &&
      selection->snapshot.quad_count == 0;
  }
  return octavo_pdf_selection_snapshot_shape_is_valid(
    &selection->snapshot,
    selection->snapshot.document_generation,
    selection->snapshot.publication_generation,
    selection->gesture_generation,
    selection->snapshot.page_index) &&
    arena_pos(selection->candidate_arena) == 0;
}

FUNCTION B32
octavo_pdf_selection_result_contract_smoke(void)
{
  static U8 valid_text[] = {'A', 0xc3, 0xa9};
  static U8 invalid_text[] = {0xc3, 0x28};
  PdfReaderQuad quad = {
    .upper_left = {1.0f, 2.0f},
    .upper_right = {4.0f, 2.0f},
    .lower_left = {1.0f, 5.0f},
    .lower_right = {4.0f, 5.0f},
  };
  PdfReaderSelectionSnapshot valid = {
    .document_generation = 7,
    .publication_generation = 11,
    .selection_generation = 13,
    .page_index = 2,
    .snapped_anchor = {1.0f, 2.0f},
    .snapped_focus = {4.0f, 5.0f},
    .text = {valid_text, sizeof(valid_text)},
    .scalar_count = 2,
    .quads = &quad,
    .quad_count = 1,
  };
  PdfReaderSelectionSnapshot invalid_utf8 = valid;
  invalid_utf8.text = (String8){invalid_text, sizeof(invalid_text)};
  PdfReaderSelectionSnapshot invalid_scalar_count = valid;
  invalid_scalar_count.scalar_count = 3;
  PdfReaderSelectionSnapshot invalid_quad = valid;
  PdfReaderQuad nonfinite_quad = quad;
  nonfinite_quad.lower_right.x = (F32)NAN;
  invalid_quad.quads = &nonfinite_quad;
  PdfReaderSelectionSnapshot zero_area = valid;
  PdfReaderQuad zero_area_quad = quad;
  zero_area_quad.lower_left = zero_area_quad.upper_left;
  zero_area_quad.lower_right = zero_area_quad.upper_right;
  zero_area.quads = &zero_area_quad;
  PdfReaderSelectionSnapshot over_byte_cap = valid;
  over_byte_cap.text.size = OCTAVO_PDF_SELECTION_TEXT_BYTE_CAP + 1ull;
  PdfReaderSelectionSnapshot over_scalar_cap = valid;
  over_scalar_cap.scalar_count = OCTAVO_PDF_SELECTION_SCALAR_CAP + 1ull;
  PdfReaderSelectionSnapshot over_quad_cap = valid;
  over_quad_cap.quad_count = OCTAVO_PDF_SELECTION_QUAD_CAP + 1u;
  return octavo_pdf_selection_snapshot_shape_is_valid(
           &valid, 7, 11, 13, 2) &&
    !octavo_pdf_selection_snapshot_shape_is_valid(
      &valid, 7, 12, 13, 2) &&
    !octavo_pdf_selection_snapshot_shape_is_valid(
      &invalid_utf8, 7, 11, 13, 2) &&
    !octavo_pdf_selection_snapshot_shape_is_valid(
      &invalid_scalar_count, 7, 11, 13, 2) &&
    !octavo_pdf_selection_snapshot_shape_is_valid(
      &invalid_quad, 7, 11, 13, 2) &&
    !octavo_pdf_selection_snapshot_shape_is_valid(
      &zero_area, 7, 11, 13, 2) &&
    !octavo_pdf_selection_snapshot_shape_is_valid(
      &over_byte_cap, 7, 11, 13, 2) &&
    !octavo_pdf_selection_snapshot_shape_is_valid(
      &over_scalar_cap, 7, 11, 13, 2) &&
    !octavo_pdf_selection_snapshot_shape_is_valid(
      &over_quad_cap, 7, 11, 13, 2);
}

FUNCTION B32
octavo_pdf_selection_frame_is_current(const PdfReader *reader,
                                      const PdfReaderFrame *frame,
                                      U64 document_generation,
                                      U64 publication_generation,
                                      U32 page_index)
{
  return reader && frame && reader->initialized && reader->document_open &&
    frame->initialized && frame->document_open &&
    document_generation != 0 && publication_generation != 0 &&
    reader->document_generation == document_generation &&
    reader->generation == publication_generation &&
    reader->current_page_index == page_index &&
    frame->document_generation == document_generation &&
    frame->generation == publication_generation &&
    frame->page_index == page_index && page_index < frame->page_count;
}

FUNCTION B32
octavo_pdf_selection_begin_pointer(OctavoPdfSelection *selection,
                                   const PdfReader *reader,
                                   const PdfReaderFrame *frame,
                                   PdfReaderPoint anchor,
                                   S32 screen_x,
                                   S32 screen_y)
{
  if (!selection || !octavo_pdf_selection_point_is_finite(anchor) ||
      !octavo_pdf_selection_frame_is_current(
        reader, frame, frame ? frame->document_generation : 0,
        frame ? frame->generation : 0, frame ? frame->page_index : 0))
  {
    if (selection)
      octavo_pdf_selection_retire(selection, PdfReaderResult_Stale);
    return 0;
  }
  octavo_pdf_selection_retire(selection, PdfReaderResult_Ok);
  selection->gesture_document_generation = frame->document_generation;
  selection->gesture_publication_generation = frame->generation;
  selection->gesture_page_index = frame->page_index;
  selection->gesture_anchor = anchor;
  selection->gesture_screen_x = screen_x;
  selection->gesture_screen_y = screen_y;
  selection->pointer_armed = 1;
  return 1;
}

FUNCTION B32
octavo_pdf_selection_gesture_is_current(const OctavoPdfSelection *selection,
                                        const PdfReader *reader,
                                        const PdfReaderFrame *frame)
{
  return selection && selection->pointer_armed &&
    octavo_pdf_selection_frame_is_current(
      reader, frame, selection->gesture_document_generation,
      selection->gesture_publication_generation,
      selection->gesture_page_index);
}

FUNCTION B32
octavo_pdf_selection_should_promote(const OctavoPdfSelection *selection,
                                    const PdfReader *reader,
                                    const PdfReaderFrame *frame,
                                    S32 screen_x,
                                    S32 screen_y)
{
  if (!octavo_pdf_selection_gesture_is_current(selection, reader, frame))
    return 0;
  if (selection->pointer_promoted) return 1;
  S64 dx = (S64)screen_x - (S64)selection->gesture_screen_x;
  S64 dy = (S64)screen_y - (S64)selection->gesture_screen_y;
  S64 threshold = OCTAVO_PDF_SELECTION_DRAG_THRESHOLD_PX;
  if (dx <= -threshold || dx >= threshold ||
      dy <= -threshold || dy >= threshold)
  {
    return 1;
  }
  return dx * dx + dy * dy >= threshold * threshold;
}

FUNCTION PdfReaderResult
octavo_pdf_selection_update_pointer(OctavoPdfSelection *selection,
                                    PdfReader *reader,
                                    const PdfReaderFrame *frame,
                                    PdfReaderPoint focus,
                                    PdfReaderCancelToken *cancel_token)
{
  if (!selection || !reader || !frame ||
      !octavo_pdf_selection_point_is_finite(focus))
  {
    if (selection)
      octavo_pdf_selection_retire(selection, PdfReaderResult_InvalidInput);
    return PdfReaderResult_InvalidInput;
  }
  if (!octavo_pdf_selection_gesture_is_current(selection, reader, frame))
  {
    octavo_pdf_selection_retire(selection, PdfReaderResult_Stale);
    return PdfReaderResult_Stale;
  }
  selection->pointer_promoted = 1;
  arena_clear(selection->candidate_arena);
  PdfReaderSelectionSnapshot candidate = {0};
  PdfReaderResult result = pdf_reader_select_text_snapshot(
    reader, selection->candidate_arena,
    (PdfReaderSelectionSnapshotRequest){
      .expected_document_generation =
        selection->gesture_document_generation,
      .expected_publication_generation =
        selection->gesture_publication_generation,
      .selection_generation = selection->gesture_generation,
      .page_index = selection->gesture_page_index,
      .anchor = selection->gesture_anchor,
      .focus = focus,
      .granularity = PdfReaderSelectionGranularity_Character,
      .cancel_token = cancel_token,
    },
    &candidate);
  if (result != PdfReaderResult_Ok)
  {
    octavo_pdf_selection_retire(selection, result);
    return result;
  }
  B32 over_cap = candidate.text.size >
                   OCTAVO_PDF_SELECTION_TEXT_BYTE_CAP ||
                 candidate.scalar_count >
                   OCTAVO_PDF_SELECTION_SCALAR_CAP ||
                 candidate.quad_count > OCTAVO_PDF_SELECTION_QUAD_CAP ||
                 arena_pos(selection->candidate_arena) >
                   OCTAVO_PDF_SELECTION_ARENA_CAP;
  if (over_cap)
  {
    result = PdfReaderResult_LimitExceeded;
    octavo_pdf_selection_retire(selection, result);
    return result;
  }
  if (!octavo_pdf_selection_snapshot_shape_is_valid(
        &candidate, selection->gesture_document_generation,
        selection->gesture_publication_generation,
        selection->gesture_generation, selection->gesture_page_index))
  {
    result = PdfReaderResult_BackendError;
    octavo_pdf_selection_retire(selection, result);
    return result;
  }
  if (!pdf_reader_selection_snapshot_is_current(
        reader, &candidate, selection->gesture_generation) ||
      !octavo_pdf_selection_frame_is_current(
        reader, frame, selection->gesture_document_generation,
        selection->gesture_publication_generation,
        selection->gesture_page_index))
  {
    result = PdfReaderResult_Stale;
    octavo_pdf_selection_retire(selection, result);
    return result;
  }
  if (candidate.text.size == 0)
  {
    octavo_pdf_selection_withdraw_publication(selection);
    selection->last_result = PdfReaderResult_Ok;
    return PdfReaderResult_Ok;
  }

  Arena *old_published = selection->published_arena;
  selection->published_arena = selection->candidate_arena;
  selection->candidate_arena = old_published;
  arena_clear(selection->candidate_arena);
  selection->snapshot = candidate;
  selection->ready = 1;
  selection->last_result = PdfReaderResult_Ok;
  return PdfReaderResult_Ok;
}

FUNCTION B32
octavo_pdf_selection_is_current(const OctavoPdfSelection *selection,
                                const PdfReader *reader,
                                const PdfReaderFrame *frame)
{
  return selection && selection->ready && reader && frame &&
    selection->snapshot.page_index == frame->page_index &&
    selection->snapshot.document_generation == frame->document_generation &&
    selection->snapshot.publication_generation == frame->generation &&
    selection->snapshot.selection_generation ==
      selection->gesture_generation &&
    octavo_pdf_selection_snapshot_shape_is_valid(
      &selection->snapshot, frame->document_generation, frame->generation,
      selection->gesture_generation, frame->page_index) &&
    pdf_reader_selection_snapshot_is_current(
      reader, &selection->snapshot, selection->gesture_generation);
}

FUNCTION B32
octavo_pdf_selection_finish_pointer(OctavoPdfSelection *selection,
                                    const PdfReader *reader,
                                    const PdfReaderFrame *frame)
{
  if (!selection || !selection->pointer_armed) return 0;
  B32 ready = selection->pointer_promoted &&
    octavo_pdf_selection_is_current(selection, reader, frame);
  if (!ready)
  {
    octavo_pdf_selection_retire(selection, selection->last_result);
    return 0;
  }
  selection->pointer_armed = 0;
  selection->pointer_promoted = 0;
  selection->gesture_document_generation = 0;
  selection->gesture_publication_generation = 0;
  selection->gesture_page_index = 0;
  return octavo_pdf_selection_invariants_hold(selection);
}

FUNCTION void
octavo_pdf_selection_cancel_pointer(OctavoPdfSelection *selection)
{
  octavo_pdf_selection_retire(selection, PdfReaderResult_Cancelled);
}

FUNCTION F64
octavo_pdf_selection_cross(PdfReaderPoint a,
                           PdfReaderPoint b,
                           PdfReaderPoint point)
{
  return ((F64)b.x - (F64)a.x) * ((F64)point.y - (F64)a.y) -
         ((F64)b.y - (F64)a.y) * ((F64)point.x - (F64)a.x);
}

FUNCTION B32
octavo_pdf_selection_point_in_quad(PdfReaderQuad quad,
                                   PdfReaderPoint point)
{
  PdfReaderPoint vertices[4] = {
    quad.upper_left,
    quad.upper_right,
    quad.lower_right,
    quad.lower_left,
  };
  B32 positive = 0;
  B32 negative = 0;
  F64 area_magnitude = 0.0;
  for (U32 index = 0; index < ARRAY_COUNT(vertices); index += 1)
  {
    F64 cross = octavo_pdf_selection_cross(
      vertices[index], vertices[(index + 1) % ARRAY_COUNT(vertices)], point);
    if (cross > 0.000001) positive = 1;
    if (cross < -0.000001) negative = 1;
    area_magnitude += fabs(cross);
  }
  return area_magnitude > 0.000001 && !(positive && negative);
}

FUNCTION B32
octavo_pdf_selection_contains_page_point(
  const OctavoPdfSelection *selection,
  const PdfReader *reader,
  const PdfReaderFrame *frame,
  PdfReaderPoint point)
{
  if (!octavo_pdf_selection_point_is_finite(point) ||
      !octavo_pdf_selection_is_current(selection, reader, frame))
  {
    return 0;
  }
  for (U32 index = 0; index < selection->snapshot.quad_count; index += 1)
  {
    if (octavo_pdf_selection_point_in_quad(
          selection->snapshot.quads[index], point))
    {
      return 1;
    }
  }
  return 0;
}

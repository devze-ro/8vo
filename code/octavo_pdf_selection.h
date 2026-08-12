#ifndef OCTAVO_PDF_SELECTION_H
#define OCTAVO_PDF_SELECTION_H

#include "base/base_arena.h"
#include "reader0.h"

enum
{
  OCTAVO_PDF_SELECTION_TEXT_BYTE_CAP = 1024 * 1024,
  OCTAVO_PDF_SELECTION_SCALAR_CAP = 256 * 1024,
  OCTAVO_PDF_SELECTION_QUAD_CAP = 384,
  OCTAVO_PDF_SELECTION_ARENA_CAP = 2 * 1024 * 1024,
  OCTAVO_PDF_SELECTION_DRAG_THRESHOLD_PX = 4,
};

typedef struct OctavoPdfSelection
{
  Arena *published_arena;
  Arena *candidate_arena;
  PdfReaderSelectionSnapshot snapshot;
  PdfReaderPoint gesture_anchor;
  U64 gesture_document_generation;
  U64 gesture_publication_generation;
  U64 gesture_generation;
  U32 gesture_page_index;
  S32 gesture_screen_x;
  S32 gesture_screen_y;
  PdfReaderResult last_result;
  B32 pointer_armed;
  B32 pointer_promoted;
  B32 ready;
} OctavoPdfSelection;

FUNCTION B32 octavo_pdf_selection_init(OctavoPdfSelection *selection);
FUNCTION void octavo_pdf_selection_release(OctavoPdfSelection *selection);
FUNCTION void octavo_pdf_selection_reset(OctavoPdfSelection *selection);
FUNCTION B32 octavo_pdf_selection_invariants_hold(
  const OctavoPdfSelection *selection);
FUNCTION B32 octavo_pdf_selection_result_contract_smoke(void);

FUNCTION B32 octavo_pdf_selection_begin_pointer(
  OctavoPdfSelection *selection,
  const PdfReader *reader,
  const PdfReaderFrame *frame,
  PdfReaderPoint anchor,
  S32 screen_x,
  S32 screen_y);
FUNCTION B32 octavo_pdf_selection_should_promote(
  const OctavoPdfSelection *selection,
  const PdfReader *reader,
  const PdfReaderFrame *frame,
  S32 screen_x,
  S32 screen_y);
FUNCTION PdfReaderResult octavo_pdf_selection_update_pointer(
  OctavoPdfSelection *selection,
  PdfReader *reader,
  const PdfReaderFrame *frame,
  PdfReaderPoint focus,
  PdfReaderCancelToken *cancel_token);
FUNCTION B32 octavo_pdf_selection_finish_pointer(
  OctavoPdfSelection *selection,
  const PdfReader *reader,
  const PdfReaderFrame *frame);
FUNCTION void octavo_pdf_selection_cancel_pointer(
  OctavoPdfSelection *selection);

FUNCTION B32 octavo_pdf_selection_is_current(
  const OctavoPdfSelection *selection,
  const PdfReader *reader,
  const PdfReaderFrame *frame);
FUNCTION B32 octavo_pdf_selection_contains_page_point(
  const OctavoPdfSelection *selection,
  const PdfReader *reader,
  const PdfReaderFrame *frame,
  PdfReaderPoint point);

#endif /* OCTAVO_PDF_SELECTION_H */

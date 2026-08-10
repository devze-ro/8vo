#ifndef OCTAVO_PDF_H
#define OCTAVO_PDF_H

#include "base/base_arena.h"
#include "reader0.h"

enum
{
  /* One four-byte full-page raster, converted from RGBA to BGRA in place. */
  OCTAVO_PDF_RASTER_MEMORY_CAP = 64 * 1024 * 1024,
};

typedef struct OctavoPdf
{
  PdfReader reader;
  PdfReaderFrame frame;
  Arena *raster_arena;
  U8 *rgba_pixels;
  U32 *bgra_pixels;
  U64 raster_capacity_bytes;
  U64 arena_allocated_bytes;
  U64 render_generation;
  U64 rendered_reader_generation;
  U32 raster_width;
  U32 raster_height;
  U32 raster_stride_bytes;
  S32 rendered_content_width;
  S32 rendered_content_height;
  F32 rendered_scale;
  PdfReaderResult last_result;
} OctavoPdf;

FUNCTION B32 octavo_pdf_init(OctavoPdf *pdf);
FUNCTION B32 octavo_pdf_release(OctavoPdf *pdf);
FUNCTION B32 octavo_pdf_is_open(const OctavoPdf *pdf);
FUNCTION B32 octavo_pdf_invariants_hold(const OctavoPdf *pdf);
FUNCTION PdfReaderResult octavo_pdf_open(OctavoPdf *pdf, String8 path);
FUNCTION PdfReaderResult octavo_pdf_close(OctavoPdf *pdf);
FUNCTION PdfReaderResult octavo_pdf_move_page(OctavoPdf *pdf, S32 direction);
FUNCTION PdfReaderResult octavo_pdf_move_history(OctavoPdf *pdf, B32 forward);
FUNCTION PdfReaderResult octavo_pdf_seek_page(OctavoPdf *pdf, U64 page_index);
FUNCTION PdfReaderResult octavo_pdf_render_fit(OctavoPdf *pdf,
                                                S32 content_width,
                                                S32 content_height);

#endif /* OCTAVO_PDF_H */

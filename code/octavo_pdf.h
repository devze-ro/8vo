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

typedef struct OctavoPdfScreenGeometry
{
  PdfReaderRect page_bounds;
  F32 scale;
  S32 content_x;
  S32 content_y;
  S32 content_width;
  S32 content_height;
  S32 raster_x;
  S32 raster_y;
  S32 raster_width;
  S32 raster_height;
  U64 document_generation;
  U64 publication_generation;
  U64 render_generation;
  U32 page_index;
} OctavoPdfScreenGeometry;

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
FUNCTION B32 octavo_pdf_screen_geometry(
  const OctavoPdf *pdf,
  S32 content_x,
  S32 content_y,
  S32 content_width,
  S32 content_height,
  OctavoPdfScreenGeometry *out_geometry);
FUNCTION B32 octavo_pdf_screen_to_page(
  const OctavoPdfScreenGeometry *geometry,
  S32 screen_x,
  S32 screen_y,
  PdfReaderPoint *out_point);
FUNCTION B32 octavo_pdf_page_to_screen(
  const OctavoPdfScreenGeometry *geometry,
  PdfReaderPoint point,
  F32 *out_screen_x,
  F32 *out_screen_y);

#endif /* OCTAVO_PDF_H */

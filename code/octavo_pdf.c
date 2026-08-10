#include "octavo_pdf.h"

#include <math.h>

FUNCTION void
octavo_pdf_clear_raster(OctavoPdf *pdf)
{
  if (!pdf) return;
  if (pdf->raster_arena) arena_clear(pdf->raster_arena);
  pdf->rgba_pixels = 0;
  pdf->bgra_pixels = 0;
  pdf->raster_capacity_bytes = 0;
  pdf->arena_allocated_bytes = 0;
  pdf->rendered_reader_generation = 0;
  pdf->raster_width = 0;
  pdf->raster_height = 0;
  pdf->raster_stride_bytes = 0;
  pdf->rendered_content_width = 0;
  pdf->rendered_content_height = 0;
  pdf->rendered_scale = 0.0f;
}

FUNCTION void
octavo_pdf_invalidate_published_raster(OctavoPdf *pdf)
{
  if (!pdf) return;
  /* The allocation may be reused, but stale or partial pixels are never
     visible to the sprite path. */
  pdf->bgra_pixels = 0;
  pdf->rendered_reader_generation = 0;
}

FUNCTION PdfReaderResult
octavo_pdf_refresh_frame(OctavoPdf *pdf)
{
  if (!pdf) return PdfReaderResult_InvalidInput;
  PdfReaderFrame frame = {0};
  PdfReaderResult result = pdf_reader_build_frame(&pdf->reader, &frame);
  if (result == PdfReaderResult_Ok) pdf->frame = frame;
  pdf->last_result = result;
  return result;
}

FUNCTION B32
octavo_pdf_init(OctavoPdf *pdf)
{
  if (!pdf) return 0;
  MemoryZeroStruct(pdf);
  ArenaParams raster_params = {
    .reserve_size = OCTAVO_PDF_RASTER_MEMORY_CAP,
    .commit_size = 64 * 1024,
  };
  pdf->raster_arena = arena_alloc(&raster_params);
  if (!pdf->raster_arena) return 0;
  PdfReaderResult result = pdf_reader_init(
    &pdf->reader,
    (PdfReaderConfig){
      .store_limit_bytes = PDF_READER_DEFAULT_STORE_LIMIT_BYTES,
      .memory_limit_bytes = PDF_READER_DEFAULT_MEMORY_LIMIT_BYTES,
    });
  if (result != PdfReaderResult_Ok)
  {
    arena_release(pdf->raster_arena);
    MemoryZeroStruct(pdf);
    return 0;
  }
  pdf->last_result = PdfReaderResult_Ok;
  return octavo_pdf_refresh_frame(pdf) == PdfReaderResult_Ok;
}

FUNCTION B32
octavo_pdf_release(OctavoPdf *pdf)
{
  if (!pdf || pdf->reader.cancel_token_count != 0) return 0;
  if (pdf->reader.initialized &&
      pdf_reader_deinit(&pdf->reader) != PdfReaderResult_Ok)
  {
    return 0;
  }
  if (pdf->raster_arena) arena_release(pdf->raster_arena);
  MemoryZeroStruct(pdf);
  return 1;
}

FUNCTION B32
octavo_pdf_is_open(const OctavoPdf *pdf)
{
  return pdf && pdf->reader.initialized && pdf->reader.document_open &&
    pdf->frame.initialized && pdf->frame.document_open;
}

FUNCTION B32
octavo_pdf_invariants_hold(const OctavoPdf *pdf)
{
  if (!pdf || !pdf->reader.initialized || !pdf->raster_arena ||
      pdf->reader.cancel_token_count != 0 ||
      !pdf_reader_invariants_hold(&pdf->reader) ||
      pdf->arena_allocated_bytes > OCTAVO_PDF_RASTER_MEMORY_CAP ||
      pdf->raster_capacity_bytes > pdf->arena_allocated_bytes)
  {
    return 0;
  }
  if (pdf->reader.document_open != pdf->frame.document_open) return 0;
  if (pdf->frame.document_open &&
      (pdf->frame.page_count == 0 ||
       pdf->frame.page_index >= pdf->frame.page_count))
  {
    return 0;
  }
  if (pdf->bgra_pixels &&
      ((U8 *)pdf->bgra_pixels != pdf->rgba_pixels ||
       pdf->rendered_reader_generation != pdf->frame.generation ||
       pdf->raster_width == 0 || pdf->raster_height == 0))
  {
    return 0;
  }
  return 1;
}

FUNCTION PdfReaderResult
octavo_pdf_open(OctavoPdf *pdf, String8 path)
{
  if (!pdf || !path.str || path.size == 0)
    return PdfReaderResult_InvalidInput;
  PdfReaderOpenTransition transition = {0};
  PdfReaderResult result = pdf_reader_open(
    &pdf->reader, path, (String8){0}, &transition);
  if (result != PdfReaderResult_Ok)
  {
    pdf->last_result = result;
    return result;
  }
  result = octavo_pdf_refresh_frame(pdf);
  if (result != PdfReaderResult_Ok)
  {
    PdfReaderResult close_result = pdf_reader_close(&pdf->reader);
    if (close_result == PdfReaderResult_Ok)
    {
      MemoryZeroStruct(&pdf->frame);
      octavo_pdf_clear_raster(pdf);
      pdf->last_result = result;
      return result;
    }
    pdf->last_result = close_result;
    return close_result;
  }
  if (transition.changed) octavo_pdf_clear_raster(pdf);
  pdf->last_result = PdfReaderResult_Ok;
  return PdfReaderResult_Ok;
}

FUNCTION PdfReaderResult
octavo_pdf_close(OctavoPdf *pdf)
{
  if (!pdf) return PdfReaderResult_InvalidInput;
  PdfReaderResult result = pdf_reader_close(&pdf->reader);
  if (result == PdfReaderResult_Ok)
  {
    MemoryZeroStruct(&pdf->frame);
    octavo_pdf_clear_raster(pdf);
  }
  pdf->last_result = result;
  return result;
}

FUNCTION PdfReaderResult
octavo_pdf_finish_page_change(OctavoPdf *pdf,
                              PdfReaderResult result,
                              PdfReaderPageChange change)
{
  if (!pdf) return PdfReaderResult_InvalidInput;
  if (result == PdfReaderResult_Ok)
  {
    result = octavo_pdf_refresh_frame(pdf);
    if (result == PdfReaderResult_Ok && change.changed)
      octavo_pdf_invalidate_published_raster(pdf);
  }
  pdf->last_result = result;
  return result;
}

FUNCTION PdfReaderResult
octavo_pdf_move_page(OctavoPdf *pdf, S32 direction)
{
  if (!pdf) return PdfReaderResult_InvalidInput;
  PdfReaderPageChange change = {0};
  PdfReaderResult result = pdf_reader_move_page(
    &pdf->reader, direction, &change);
  return octavo_pdf_finish_page_change(pdf, result, change);
}

FUNCTION PdfReaderResult
octavo_pdf_move_history(OctavoPdf *pdf, B32 forward)
{
  if (!pdf) return PdfReaderResult_InvalidInput;
  PdfReaderPageChange change = {0};
  PdfReaderResult result = forward ?
    pdf_reader_history_forward(&pdf->reader, &change) :
    pdf_reader_history_back(&pdf->reader, &change);
  return octavo_pdf_finish_page_change(pdf, result, change);
}

FUNCTION PdfReaderResult
octavo_pdf_seek_page(OctavoPdf *pdf, U64 page_index)
{
  if (!pdf) return PdfReaderResult_InvalidInput;
  if (page_index > UINT32_MAX) return PdfReaderResult_PageOutOfRange;
  PdfReaderPageChange change = {0};
  PdfReaderResult result = pdf_reader_go_to_page(
    &pdf->reader, (U32)page_index, &change);
  return octavo_pdf_finish_page_change(pdf, result, change);
}

FUNCTION B32
octavo_pdf_fit_geometry(PdfReaderRect bounds,
                        S32 content_width,
                        S32 content_height,
                        F32 *out_scale,
                        U32 *out_width,
                        U32 *out_height)
{
  if (out_scale) *out_scale = 0.0f;
  if (out_width) *out_width = 0;
  if (out_height) *out_height = 0;
  F64 page_width = (F64)bounds.x1 - (F64)bounds.x0;
  F64 page_height = (F64)bounds.y1 - (F64)bounds.y0;
  if (!out_scale || !out_width || !out_height || content_width <= 0 ||
      content_height <= 0 || !isfinite(page_width) ||
      !isfinite(page_height) || page_width <= 0.0 || page_height <= 0.0)
  {
    return 0;
  }
  U32 max_width = (U32)MIN(content_width, PDF_READER_MAX_TILE_DIMENSION);
  U32 max_height = (U32)MIN(content_height, PDF_READER_MAX_TILE_DIMENSION);
  F64 candidate = MIN((F64)max_width / page_width,
                      (F64)max_height / page_height);
  candidate = MIN(candidate, 64.0);
  if (!isfinite(candidate) || candidate <= 0.0) return 0;
  F32 scale = (F32)candidate;
  for (U32 attempt = 0; attempt < 8; attempt += 1)
  {
    F64 scaled_width = ceil(page_width * (F64)scale);
    F64 scaled_height = ceil(page_height * (F64)scale);
    if (isfinite(scaled_width) && isfinite(scaled_height) &&
        scaled_width >= 1.0 && scaled_height >= 1.0 &&
        scaled_width <= max_width && scaled_height <= max_height)
    {
      *out_scale = scale;
      *out_width = (U32)scaled_width;
      *out_height = (U32)scaled_height;
      return 1;
    }
    scale *= 0.9999f;
  }
  return 0;
}

FUNCTION PdfReaderResult
octavo_pdf_render_fit(OctavoPdf *pdf,
                      S32 content_width,
                      S32 content_height)
{
  if (!pdf || !octavo_pdf_is_open(pdf)) return PdfReaderResult_NotOpen;
  if (pdf->rendered_reader_generation == pdf->frame.generation &&
      pdf->rendered_content_width == content_width &&
      pdf->rendered_content_height == content_height &&
      pdf->raster_width > 0 && pdf->raster_height > 0 && pdf->bgra_pixels)
  {
    pdf->last_result = PdfReaderResult_Ok;
    return PdfReaderResult_Ok;
  }

  /* A cache miss is a new render transaction. Withdraw the borrowed sprite
     surface before even querying fallible page/geometry/backend state. */
  octavo_pdf_invalidate_published_raster(pdf);
  PdfReaderPageInfo page = {0};
  PdfReaderResult result = pdf_reader_current_page_info(&pdf->reader, &page);
  if (result != PdfReaderResult_Ok)
  {
    octavo_pdf_invalidate_published_raster(pdf);
    pdf->last_result = result;
    return result;
  }
  F32 scale = 0.0f;
  U32 width = 0;
  U32 height = 0;
  if (!octavo_pdf_fit_geometry(page.bounds, content_width, content_height,
                               &scale, &width, &height))
  {
    octavo_pdf_invalidate_published_raster(pdf);
    pdf->last_result = PdfReaderResult_LimitExceeded;
    return pdf->last_result;
  }
  U64 pixel_count = (U64)width * (U64)height;
  U64 one_raster_bytes = pixel_count *
                         PDF_READER_BYTES_PER_PIXEL_RGBA8;
  if (pixel_count == 0 || one_raster_bytes > UINT32_MAX ||
      one_raster_bytes > OCTAVO_PDF_RASTER_MEMORY_CAP)
  {
    octavo_pdf_invalidate_published_raster(pdf);
    pdf->last_result = PdfReaderResult_LimitExceeded;
    return pdf->last_result;
  }
  if (one_raster_bytes > pdf->raster_capacity_bytes)
  {
    arena_clear(pdf->raster_arena);
    pdf->rgba_pixels = (U8 *)arena_push(
      pdf->raster_arena, one_raster_bytes, ALIGN_OF(U32));
    pdf->bgra_pixels = 0;
    pdf->arena_allocated_bytes = arena_pos(pdf->raster_arena);
    if (!pdf->rgba_pixels ||
        pdf->arena_allocated_bytes < one_raster_bytes ||
        pdf->arena_allocated_bytes > OCTAVO_PDF_RASTER_MEMORY_CAP)
    {
      octavo_pdf_clear_raster(pdf);
      pdf->last_result = PdfReaderResult_LimitExceeded;
      return pdf->last_result;
    }
    pdf->raster_capacity_bytes = one_raster_bytes;
  }
  U32 stride_bytes = width * PDF_READER_BYTES_PER_PIXEL_RGBA8;
  /* Do not publish a raster while Reader0 or the complete swizzle is active. */
  PdfReaderTileResult tile = {0};
  result = pdf_reader_render_tile(
    &pdf->reader,
    (PdfReaderTileRequest){
      .page_index = pdf->frame.page_index,
      .scale = scale,
      .target = {
        .pixels = pdf->rgba_pixels,
        .capacity = pdf->raster_capacity_bytes,
        .width = width,
        .height = height,
        .stride_bytes = stride_bytes,
        .format = PdfReaderPixelFormat_RGBA8Premultiplied,
      },
    },
    &tile);
  if (result != PdfReaderResult_Ok || tile.page_index != pdf->frame.page_index ||
      tile.full_width != width || tile.full_height != height)
  {
    pdf->rendered_reader_generation = 0;
    pdf->last_result = result == PdfReaderResult_Ok ?
      PdfReaderResult_BackendError : result;
    return pdf->last_result;
  }

  for (U64 index = 0; index < pixel_count; index += 1)
  {
    U8 *pixel = pdf->rgba_pixels + index * 4;
    U8 red = pixel[0];
    pixel[0] = pixel[2];
    pixel[2] = red;
  }
  pdf->bgra_pixels = (U32 *)pdf->rgba_pixels;
  pdf->raster_width = width;
  pdf->raster_height = height;
  pdf->raster_stride_bytes = stride_bytes;
  pdf->rendered_content_width = content_width;
  pdf->rendered_content_height = content_height;
  pdf->rendered_scale = scale;
  pdf->rendered_reader_generation = pdf->frame.generation;
  pdf->render_generation += 1;
  if (pdf->render_generation == 0) pdf->render_generation = 1;
  pdf->last_result = PdfReaderResult_Ok;
  return PdfReaderResult_Ok;
}

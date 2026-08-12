#include "octavo_pdf.h"

#include <limits.h>
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

FUNCTION B32
octavo_pdf_screen_geometry(const OctavoPdf *pdf,
                           S32 content_x,
                           S32 content_y,
                           S32 content_width,
                           S32 content_height,
                           OctavoPdfScreenGeometry *out_geometry)
{
  if (out_geometry) *out_geometry = (OctavoPdfScreenGeometry){0};
  if (!pdf || !out_geometry || !octavo_pdf_is_open(pdf) ||
      !pdf->bgra_pixels || pdf->raster_width == 0 ||
      pdf->raster_height == 0 || pdf->raster_width > INT32_MAX ||
      pdf->raster_height > INT32_MAX || content_width <= 0 ||
      content_height <= 0 ||
      pdf->rendered_content_width != content_width ||
      pdf->rendered_content_height != content_height ||
      pdf->rendered_reader_generation != pdf->frame.generation ||
      !isfinite(pdf->rendered_scale) || pdf->rendered_scale <= 0.0f)
  {
    return 0;
  }
  PdfReaderRect bounds = pdf->frame.page.bounds;
  if (!isfinite(bounds.x0) || !isfinite(bounds.y0) ||
      !isfinite(bounds.x1) || !isfinite(bounds.y1) ||
      bounds.x1 <= bounds.x0 || bounds.y1 <= bounds.y0)
  {
    return 0;
  }
  S32 raster_width = (S32)pdf->raster_width;
  S32 raster_height = (S32)pdf->raster_height;
  S64 raster_x = (S64)content_x +
    ((S64)content_width - (S64)raster_width) / 2;
  S64 raster_y = (S64)content_y +
    ((S64)content_height - (S64)raster_height) / 2;
  if (raster_x < INT32_MIN || raster_x > INT32_MAX ||
      raster_y < INT32_MIN || raster_y > INT32_MAX)
  {
    return 0;
  }
  *out_geometry = (OctavoPdfScreenGeometry){
    .page_bounds = bounds,
    .scale = pdf->rendered_scale,
    .content_x = content_x,
    .content_y = content_y,
    .content_width = content_width,
    .content_height = content_height,
    .raster_x = (S32)raster_x,
    .raster_y = (S32)raster_y,
    .raster_width = raster_width,
    .raster_height = raster_height,
    .document_generation = pdf->frame.document_generation,
    .publication_generation = pdf->frame.generation,
    .render_generation = pdf->render_generation,
    .page_index = pdf->frame.page_index,
  };
  return 1;
}

FUNCTION B32
octavo_pdf_screen_to_page(const OctavoPdfScreenGeometry *geometry,
                          S32 screen_x,
                          S32 screen_y,
                          PdfReaderPoint *out_point)
{
  if (out_point) *out_point = (PdfReaderPoint){0};
  if (!geometry || !out_point || !isfinite(geometry->scale) ||
      geometry->scale <= 0.0f || geometry->raster_width <= 0 ||
      geometry->raster_height <= 0)
  {
    return 0;
  }
  S64 raster_right = (S64)geometry->raster_x + geometry->raster_width;
  S64 raster_bottom = (S64)geometry->raster_y + geometry->raster_height;
  S64 content_right = (S64)geometry->content_x + geometry->content_width;
  S64 content_bottom = (S64)geometry->content_y + geometry->content_height;
  if ((S64)screen_x < geometry->raster_x ||
      (S64)screen_y < geometry->raster_y ||
      (S64)screen_x >= raster_right || (S64)screen_y >= raster_bottom ||
      (S64)screen_x < geometry->content_x ||
      (S64)screen_y < geometry->content_y ||
      (S64)screen_x >= content_right || (S64)screen_y >= content_bottom)
  {
    return 0;
  }
  F32 page_x = geometry->page_bounds.x0 +
    (F32)(screen_x - geometry->raster_x) / geometry->scale;
  F32 page_y = geometry->page_bounds.y0 +
    (F32)(screen_y - geometry->raster_y) / geometry->scale;
  if (!isfinite(page_x) || !isfinite(page_y)) return 0;
  out_point->x = page_x;
  out_point->y = page_y;
  return 1;
}

FUNCTION B32
octavo_pdf_page_to_screen(const OctavoPdfScreenGeometry *geometry,
                          PdfReaderPoint point,
                          F32 *out_screen_x,
                          F32 *out_screen_y)
{
  if (out_screen_x) *out_screen_x = 0.0f;
  if (out_screen_y) *out_screen_y = 0.0f;
  if (!geometry || !out_screen_x || !out_screen_y ||
      !isfinite(point.x) || !isfinite(point.y) ||
      !isfinite(geometry->scale) || geometry->scale <= 0.0f ||
      geometry->raster_width <= 0 || geometry->raster_height <= 0)
  {
    return 0;
  }
  F32 screen_x = (F32)geometry->raster_x +
    (point.x - geometry->page_bounds.x0) * geometry->scale;
  F32 screen_y = (F32)geometry->raster_y +
    (point.y - geometry->page_bounds.y0) * geometry->scale;
  if (!isfinite(screen_x) || !isfinite(screen_y)) return 0;
  *out_screen_x = screen_x;
  *out_screen_y = screen_y;
  return 1;
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
octavo_pdf_geometry_for_scale(F64 page_width,
                              F64 page_height,
                              F32 scale,
                              U32 max_width,
                              U32 max_height,
                              U64 max_pixel_count,
                              U32 *out_width,
                              U32 *out_height)
{
  if (out_width) *out_width = 0;
  if (out_height) *out_height = 0;
  if (!out_width || !out_height || !isfinite(page_width) ||
      !isfinite(page_height) || page_width <= 0.0 || page_height <= 0.0 ||
      !isfinite(scale) || scale <= 0.0f || max_width == 0 ||
      max_height == 0 || max_pixel_count == 0)
  {
    return 0;
  }

  F64 scaled_width = ceil(page_width * (F64)scale);
  F64 scaled_height = ceil(page_height * (F64)scale);
  if (!isfinite(scaled_width) || !isfinite(scaled_height) ||
      scaled_width < 1.0 || scaled_height < 1.0 ||
      scaled_width > (F64)max_width || scaled_height > (F64)max_height)
  {
    return 0;
  }

  U64 width = (U64)scaled_width;
  U64 height = (U64)scaled_height;
  if (width == 0 || height == 0 || width > max_pixel_count / height)
    return 0;

  *out_width = (U32)width;
  *out_height = (U32)height;
  return 1;
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
  U64 max_pixel_count = OCTAVO_PDF_RASTER_MEMORY_CAP /
                        PDF_READER_BYTES_PER_PIXEL_RGBA8;
  F64 page_area = page_width * page_height;
  if (!isfinite(page_area) || page_area <= 0.0 || max_pixel_count == 0)
    return 0;
  F64 candidate = MIN((F64)max_width / page_width,
                      (F64)max_height / page_height);
  candidate = MIN(candidate, sqrt((F64)max_pixel_count / page_area));
  candidate = MIN(candidate, 64.0);
  if (!isfinite(candidate) || candidate <= 0.0) return 0;

  F32 high_scale = (F32)candidate;
  U32 width = 0;
  U32 height = 0;
  if (octavo_pdf_geometry_for_scale(page_width, page_height, high_scale,
                                    max_width, max_height, max_pixel_count,
                                    &width, &height))
  {
    *out_scale = high_scale;
    *out_width = width;
    *out_height = height;
    return 1;
  }

  /* Casting the continuous limit to F32 or rounding each raster dimension up
     can put the candidate a few pixels over the exact byte cap. The fit
     predicate uses division before multiplication, then this bounded search
     selects the greatest representable scale it can prove fits. */
  F32 low_scale = high_scale * 0.5f;
  if (!octavo_pdf_geometry_for_scale(page_width, page_height, low_scale,
                                     max_width, max_height, max_pixel_count,
                                     &width, &height))
  {
    return 0;
  }
  U32 low_width = width;
  U32 low_height = height;
  for (U32 attempt = 0; attempt < 32; attempt += 1)
  {
    F32 middle_scale = low_scale + (high_scale - low_scale) * 0.5f;
    if (middle_scale <= low_scale || middle_scale >= high_scale) break;
    if (octavo_pdf_geometry_for_scale(page_width, page_height, middle_scale,
                                      max_width, max_height, max_pixel_count,
                                      &width, &height))
    {
      low_scale = middle_scale;
      low_width = width;
      low_height = height;
    }
    else
    {
      high_scale = middle_scale;
    }
  }
  *out_scale = low_scale;
  *out_width = low_width;
  *out_height = low_height;
  return 1;
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

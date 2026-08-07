/* Android Port 10 bounded native text-selection adapter.
 *
 * Reader0 owns the canonical DocSelection. This file owns only the Android
 * page-geometry projection, transient presentation transaction, and bounded
 * JNI packets used by the native View host.
 */

enum
{
  OCTAVO_ANDROID_SELECTION_RESULT_ACCEPTED = 1,
  OCTAVO_ANDROID_SELECTION_RESULT_ALREADY_PRESENTED = 2,
  OCTAVO_ANDROID_SELECTION_RESULT_INVALID = -1,
  OCTAVO_ANDROID_SELECTION_RESULT_UNAVAILABLE = -2,
  OCTAVO_ANDROID_SELECTION_RESULT_BUSY = -3,
  OCTAVO_ANDROID_SELECTION_RESULT_FAILED = -4,
  OCTAVO_ANDROID_SELECTION_RESULT_BOUNDARY = -5,
  OCTAVO_ANDROID_SELECTION_RESULT_LIMIT = -6,
  OCTAVO_ANDROID_SELECTION_SNAPSHOT_VERSION = 2,
  OCTAVO_ANDROID_SELECTION_SNAPSHOT_COUNT = 14,
  OCTAVO_ANDROID_SELECTION_FLAG_READY = 1u << 0,
  OCTAVO_ANDROID_SELECTION_FLAG_ACTIVE = 1u << 1,
  OCTAVO_ANDROID_SELECTION_FLAG_PENDING = 1u << 2,
  OCTAVO_ANDROID_SELECTION_FLAG_START_VISIBLE = 1u << 3,
  OCTAVO_ANDROID_SELECTION_FLAG_END_VISIBLE = 1u << 4,
  OCTAVO_ANDROID_SELECTION_HANDLE_START = 1,
  OCTAVO_ANDROID_SELECTION_HANDLE_END = 2,
};

static B32
octavo_android_selection_row_geometry(
  OctavoAndroidApp *app,
  const EpubReaderFrameStyleRow *row,
  S32 incoming_y,
  OctavoAndroidSelectionRowGeometry *out_geometry)
{
  if (out_geometry)
  {
    memset(out_geometry, 0, sizeof(*out_geometry));
  }
  if (!app || !row || !out_geometry || !app->reader_frame.ready ||
      !app->reader_frame.visible_text.str)
  {
    return 0;
  }

  UI0Rect content = app->reader_view_layout.content_rect;
  S32 base_line_height = MAX(app->typography.line_advance_px, 1);
  S32 char_advance = MAX(
    octavo_android_typography_advance(
      &app->typography, 'n', 0, 1000u),
    1);
  S32 y = incoming_y;
  if (row->line_row == 0 && row->margin_top_rows > 0)
  {
    y += row->margin_top_rows * base_line_height;
  }
  S32 row_height = octavo_android_frame_style_row_height(app, row);
  if (y + row_height > content.y + content.h)
  {
    return 0;
  }

  U32 start = MIN(row->byte_start,
                  (U32)app->reader_frame.visible_text.size);
  U32 end = MIN(row->byte_end,
                (U32)app->reader_frame.visible_text.size);
  end = octavo_reader_visible_row_end(
    app->reader_frame.visible_text.str,
    start,
    end,
    row->soft_wrapped);

  S32 left = MAX(row->margin_left_cols, 0) * char_advance;
  S32 right = MAX(row->margin_right_cols, 0) * char_advance;
  if (row->line_row == 0)
  {
    left += MAX(row->text_indent_cols, 0) * char_advance;
  }
  S32 available = MAX(content.w - left - right, 1);
  S32 row_width = octavo_android_measure_reader_row(app, row, start, end);
  OctavoReaderJustificationPlan justification = {0};
  if (end > start &&
      !octavo_android_reader_justification_plan_for_row(
        app, row, start, end, row_width, available, &justification))
  {
    return 0;
  }
  S32 x = content.x + left;
  if (app->appearance.alignment == 0 &&
      row->text_align == DocTextAlign_Center && row_width < available)
  {
    x += (available - row_width) / 2;
  }
  else if (app->appearance.alignment == 0 &&
           row->text_align == DocTextAlign_Right && row_width < available)
  {
    x += available - row_width;
  }

  out_geometry->row = row;
  out_geometry->start = start;
  out_geometry->end = end;
  out_geometry->x = x;
  out_geometry->y = y;
  out_geometry->height = row_height;
  out_geometry->next_y = y + row_height;
  if (row->block_last_row && row->margin_bottom_rows > 0)
  {
    out_geometry->next_y += row->margin_bottom_rows * base_line_height;
  }
  out_geometry->justification = justification;
  out_geometry->valid = 1;
  return 1;
}

static B32
octavo_android_selection_walk_row(
  OctavoAndroidApp *app,
  const OctavoAndroidSelectionRowGeometry *geometry,
  S32 target_x,
  U32 target_byte,
  B32 resolve_x,
  U32 *out_byte,
  S32 *out_x)
{
  if (out_byte) { *out_byte = 0; }
  if (out_x) { *out_x = 0; }
  if (!app || !geometry || !geometry->valid || !geometry->row ||
      geometry->end <= geometry->start ||
      geometry->end > app->reader_frame.visible_text.size)
  {
    return 0;
  }

  const EpubReaderFrameStyleRow *row = geometry->row;
  U32 row_size = geometry->end - geometry->start;
  U32 segment_start = 0;
  U32 space_index = 0;
  S32 pen_x = geometry->x;
  if (resolve_x && target_x <= pen_x)
  {
    if (out_byte) { *out_byte = geometry->start; }
    if (out_x) { *out_x = pen_x; }
    return 1;
  }
  if (!resolve_x && target_byte <= geometry->start)
  {
    if (out_byte) { *out_byte = geometry->start; }
    if (out_x) { *out_x = pen_x; }
    return 1;
  }

  while (segment_start < row_size)
  {
    U32 segment_end = row_size;
    DocTextStyleFlags flags = 0;
    U32 inline_scale = 1000u;
    octavo_android_reader_segment_style(
      app, row, row_size, segment_start, &segment_end, &flags,
      &inline_scale, 0, 0);
    if (segment_end <= segment_start || segment_end > row_size)
    {
      return 0;
    }
    U32 scale = octavo_android_combined_scale(
      row->font_scale_permille, inline_scale);
    String8 segment = str8(
      app->reader_frame.visible_text.str + geometry->start + segment_start,
      segment_end - segment_start);
    U64 at = 0;
    while (at < segment.size)
    {
      U32 codepoint_start = segment_start + (U32)at;
      U32 codepoint = octavo_android_next_codepoint(
        segment.str, segment.size, &at);
      U32 codepoint_end = segment_start + (U32)at;
      S32 advance = octavo_android_typography_advance(
        &app->typography, codepoint, flags, scale);
      S32 space_extra = 0;
      if (codepoint == ' ' && codepoint_start > 0 &&
          codepoint_start + 1u < row_size &&
          space_index < geometry->justification.space_count)
      {
        space_extra = octavo_reader_justification_space_extra_px(
          &geometry->justification, space_index);
      }
      S32 next_x = pen_x + advance + space_extra;
      U32 absolute_start = geometry->start + codepoint_start;
      U32 absolute_end = geometry->start + codepoint_end;
      if ((resolve_x && target_x < pen_x + (next_x - pen_x) / 2) ||
          (!resolve_x && target_byte <= absolute_start))
      {
        if (out_byte) { *out_byte = absolute_start; }
        if (out_x) { *out_x = pen_x; }
        return 1;
      }
      if (!resolve_x && target_byte < absolute_end)
      {
        if (out_byte) { *out_byte = absolute_end; }
        if (out_x) { *out_x = next_x; }
        return 1;
      }
      pen_x = next_x;
      if (codepoint == ' ' && codepoint_start > 0 &&
          codepoint_start + 1u < row_size &&
          space_index < geometry->justification.space_count)
      {
        space_index += 1u;
      }
    }
    segment_start = segment_end;
  }

  if (out_byte) { *out_byte = geometry->end; }
  if (out_x) { *out_x = pen_x; }
  return 1;
}

static B32
octavo_android_selection_hit_test(OctavoAndroidApp *app,
                                  S32 x,
                                  S32 y,
                                  B32 nearest,
                                  U32 *out_byte)
{
  if (out_byte) { *out_byte = 0; }
  if (!app || !out_byte || !app->reader_frame.ready ||
      app->reader_frame.style_row_count > EPUB_READER_FRAME_STYLE_ROW_CAP)
  {
    return 0;
  }
  S32 row_y = app->reader_view_layout.content_rect.y;
  U32 nearest_byte = 0;
  B32 has_nearest = 0;
  for (U32 index = 0; index < app->reader_frame.style_row_count; ++index)
  {
    OctavoAndroidSelectionRowGeometry geometry = {0};
    if (!octavo_android_selection_row_geometry(
          app, app->reader_frame.style_rows + index, row_y, &geometry))
    {
      break;
    }
    row_y = geometry.next_y;
    if (geometry.end <= geometry.start) { continue; }
    if (y < geometry.y)
    {
      if (!nearest) { return 0; }
      if (!has_nearest)
      {
        return octavo_android_selection_walk_row(
          app, &geometry, geometry.x, 0, 1, out_byte, 0);
      }
      *out_byte = nearest_byte;
      return 1;
    }
    if (y < geometry.y + geometry.height)
    {
      return octavo_android_selection_walk_row(
        app, &geometry, x, 0, 1, out_byte, 0);
    }
    nearest_byte = geometry.end;
    has_nearest = 1;
  }
  if (nearest && has_nearest)
  {
    *out_byte = nearest_byte;
    return 1;
  }
  return 0;
}

static B32
octavo_android_selection_caret(OctavoAndroidApp *app,
                               U32 byte,
                               S32 *out_x,
                               S32 *out_y,
                               S32 *out_row_height)
{
  if (out_x) { *out_x = 0; }
  if (out_y) { *out_y = 0; }
  if (out_row_height) { *out_row_height = 0; }
  if (!app || !out_x || !out_y || !out_row_height)
  {
    return 0;
  }
  S32 row_y = app->reader_view_layout.content_rect.y;
  for (U32 index = 0; index < app->reader_frame.style_row_count; ++index)
  {
    OctavoAndroidSelectionRowGeometry geometry = {0};
    if (!octavo_android_selection_row_geometry(
          app, app->reader_frame.style_rows + index, row_y, &geometry))
    {
      break;
    }
    row_y = geometry.next_y;
    if (geometry.end <= geometry.start || byte < geometry.start ||
        byte > geometry.end)
    {
      continue;
    }
    U32 resolved_byte = 0;
    if (!octavo_android_selection_walk_row(
          app, &geometry, 0, byte, 0, &resolved_byte, out_x))
    {
      return 0;
    }
    *out_y = geometry.y;
    *out_row_height = geometry.height;
    return resolved_byte == byte;
  }
  return 0;
}

static B32
octavo_android_selection_endpoint_caret(OctavoAndroidApp *app,
                                         U64 absolute_byte,
                                         S32 *out_x,
                                         S32 *out_y,
                                         S32 *out_row_height)
{
  if (!app || !app->reader.has_active_selection ||
      app->reader.active_selection.spine_index !=
        app->reader_frame.spine_index ||
      absolute_byte < app->reader_frame.view_byte_offset)
  {
    return 0;
  }
  U64 local = absolute_byte - app->reader_frame.view_byte_offset;
  if (local > app->reader_frame.visible_text.size || local > UINT32_MAX)
  {
    return 0;
  }
  return octavo_android_selection_caret(
    app, (U32)local, out_x, out_y, out_row_height);
}

static B32
octavo_android_selection_edge_target_y(OctavoAndroidApp *app,
                                       S32 direction,
                                       S32 *out_y)
{
  if (out_y) { *out_y = 0; }
  if (!app || !out_y || (direction != -1 && direction != 1))
  {
    return 0;
  }
  S32 row_y = app->reader_view_layout.content_rect.y;
  S32 first_y = 0;
  S32 last_y = 0;
  B32 found = 0;
  for (U32 index = 0; index < app->reader_frame.style_row_count; ++index)
  {
    OctavoAndroidSelectionRowGeometry geometry = {0};
    if (!octavo_android_selection_row_geometry(
          app, app->reader_frame.style_rows + index, row_y, &geometry))
    {
      break;
    }
    row_y = geometry.next_y;
    if (geometry.end <= geometry.start) { continue; }
    S32 middle = geometry.y + MAX(geometry.height / 2, 0);
    if (!found) { first_y = middle; }
    last_y = middle;
    found = 1;
  }
  if (!found) { return 0; }
  *out_y = direction > 0 ? first_y : last_y;
  return 1;
}

static B32
octavo_android_selection_is_space(U32 scalar)
{
  return scalar == ' ' || scalar == '\t' || scalar == '\r' ||
    scalar == '\n' || scalar == 0x00a0u ||
    (scalar >= 0x2000u && scalar <= 0x200au) ||
    scalar == 0x2028u || scalar == 0x2029u || scalar == 0x202fu ||
    scalar == 0x205fu || scalar == 0x3000u;
}

static B32
octavo_android_selection_is_word(U32 scalar)
{
  if (scalar >= 'A' && scalar <= 'Z') { return 1; }
  if (scalar >= 'a' && scalar <= 'z') { return 1; }
  if (scalar >= '0' && scalar <= '9') { return 1; }
  if (scalar == '_' || scalar == '\'' || scalar == 0x2019u) { return 1; }
  if (scalar < 0x80u || octavo_android_selection_is_space(scalar))
  {
    return 0;
  }
  if ((scalar >= 0x2000u && scalar <= 0x206fu) ||
      (scalar >= 0x20a0u && scalar <= 0x20cfu) ||
      (scalar >= 0x2100u && scalar <= 0x214fu) ||
      (scalar >= 0x2190u && scalar <= 0x2bffu) ||
      (scalar >= 0x3000u && scalar <= 0x303fu))
  {
    return 0;
  }
  return 1;
}

static U32
octavo_android_selection_previous_boundary(String8 text, U32 at)
{
  if (at == 0 || at > text.size) { return 0; }
  U32 result = at - 1u;
  while (result > 0 && (text.str[result] & 0xc0u) == 0x80u)
  {
    result -= 1u;
  }
  return result;
}

static B32
octavo_android_selection_word_at(OctavoAndroidApp *app,
                                 U32 byte,
                                 U32 *out_start,
                                 U32 *out_end)
{
  if (out_start) { *out_start = 0; }
  if (out_end) { *out_end = 0; }
  if (!app || !out_start || !out_end ||
      !app->reader_frame.visible_text.str ||
      byte >= app->reader_frame.visible_text.size)
  {
    return 0;
  }
  String8 text = app->reader_frame.visible_text;
  BaseUnicodeDecode hit = base_unicode_utf8_decode(text, byte);
  if (!hit.valid || hit.advance == 0 || hit.advance > text.size - byte ||
      octavo_android_selection_is_space(hit.scalar))
  {
    return 0;
  }
  U32 start = byte;
  U32 end = byte + hit.advance;
  if (octavo_android_selection_is_word(hit.scalar))
  {
    while (start > 0)
    {
      U32 previous = octavo_android_selection_previous_boundary(text, start);
      BaseUnicodeDecode decode = base_unicode_utf8_decode(text, previous);
      if (!decode.valid || decode.advance == 0 ||
          previous + decode.advance != start ||
          !octavo_android_selection_is_word(decode.scalar))
      {
        break;
      }
      start = previous;
    }
    while (end < text.size)
    {
      BaseUnicodeDecode decode = base_unicode_utf8_decode(text, end);
      if (!decode.valid || decode.advance == 0 ||
          decode.advance > text.size - end ||
          !octavo_android_selection_is_word(decode.scalar))
      {
        break;
      }
      end += decode.advance;
    }
  }
  *out_start = start;
  *out_end = end;
  return end > start;
}

static B32
octavo_android_selection_adjust_range(DocSelection *selection,
                                      S32 handle_kind,
                                      U64 absolute,
                                      S32 *out_active_handle)
{
  if (out_active_handle) { *out_active_handle = 0; }
  if (!selection || !out_active_handle ||
      (handle_kind != OCTAVO_ANDROID_SELECTION_HANDLE_START &&
       handle_kind != OCTAVO_ANDROID_SELECTION_HANDLE_END))
  {
    return 0;
  }
  S32 active_handle = handle_kind;
  if (handle_kind == OCTAVO_ANDROID_SELECTION_HANDLE_START)
  {
    if (absolute < selection->text_byte_end)
    {
      selection->text_byte_start = absolute;
    }
    else if (absolute > selection->text_byte_end)
    {
      selection->text_byte_start = selection->text_byte_end;
      selection->text_byte_end = absolute;
      active_handle = OCTAVO_ANDROID_SELECTION_HANDLE_END;
    }
    else
    {
      return 0;
    }
  }
  else
  {
    if (absolute > selection->text_byte_start)
    {
      selection->text_byte_end = absolute;
    }
    else if (absolute < selection->text_byte_start)
    {
      selection->text_byte_end = selection->text_byte_start;
      selection->text_byte_start = absolute;
      active_handle = OCTAVO_ANDROID_SELECTION_HANDLE_START;
    }
    else
    {
      return 0;
    }
  }
  if (selection->text_byte_start >= selection->text_byte_end)
  {
    return 0;
  }
  *out_active_handle = active_handle;
  return 1;
}

static void
octavo_android_selection_page_turn_state_clear(OctavoAndroidApp *app)
{
  if (!app) { return; }
  app->selection_page_turn_previous_page = (SourceReaderPageRange){0};
  app->selection_page_turn_previous_spine_index = 0;
  app->selection_page_turn_previous_byte_offset = 0;
  app->selection_page_turn_direction = 0;
  app->selection_page_turn_waiting_for_present = 0;
}

static void
octavo_android_discard_selection(OctavoAndroidApp *app)
{
  if (!app) { return; }
  epub_reader_clear_selection(&app->reader);
  app->selection_mutation_previous = (DocSelection){0};
  app->selection_mutation_previous_valid = 0;
  app->selection_mutation_waiting_for_present = 0;
  octavo_android_selection_page_turn_state_clear(app);
}

static B32
octavo_android_publish_selection(OctavoAndroidApp *app,
                                 DocSelection selection)
{
  if (!app || app->selection_mutation_waiting_for_present)
  {
    return 0;
  }
  app->selection_mutation_previous = app->reader.active_selection;
  app->selection_mutation_previous_valid = app->reader.has_active_selection;
  EpubReaderResult result = epub_reader_set_selection(&app->reader, selection);
  if (result != EpubReaderResult_Ok)
  {
    app->selection_mutation_previous = (DocSelection){0};
    app->selection_mutation_previous_valid = 0;
    app->selection_failure_count += 1u;
    return 0;
  }
  octavo_android_invalidate_prepared_static_frame(app);
  if (!epub_reader_build_frame(
        &app->reader, &app->reader_frame_storage, &app->reader_frame) ||
      !app->reader_frame.ready || !app->reader_frame.document_open ||
      !app->reader_frame.has_selection)
  {
    if (app->selection_mutation_previous_valid)
    {
      (void)epub_reader_set_selection(
        &app->reader, app->selection_mutation_previous);
    }
    else
    {
      epub_reader_clear_selection(&app->reader);
    }
    (void)epub_reader_build_frame(
      &app->reader, &app->reader_frame_storage, &app->reader_frame);
    app->selection_mutation_previous = (DocSelection){0};
    app->selection_mutation_previous_valid = 0;
    app->selection_failure_count += 1u;
    return 0;
  }
  app->selection_generation += 1u;
  app->selection_mutation_waiting_for_present = 1;
  app->host_frame_waiting_for_present = 1;
  return 1;
}

static B32
octavo_android_publish_selection_clear(OctavoAndroidApp *app)
{
  if (!app || app->selection_mutation_waiting_for_present)
  {
    return 0;
  }
  if (!app->reader.has_active_selection)
  {
    return 1;
  }
  app->selection_mutation_previous = app->reader.active_selection;
  app->selection_mutation_previous_valid = 1;
  epub_reader_clear_selection(&app->reader);
  octavo_android_invalidate_prepared_static_frame(app);
  if (!epub_reader_build_frame(
        &app->reader, &app->reader_frame_storage, &app->reader_frame) ||
      !app->reader_frame.ready || !app->reader_frame.document_open ||
      app->reader_frame.has_selection)
  {
    (void)epub_reader_set_selection(
      &app->reader, app->selection_mutation_previous);
    (void)epub_reader_build_frame(
      &app->reader, &app->reader_frame_storage, &app->reader_frame);
    app->selection_mutation_previous = (DocSelection){0};
    app->selection_mutation_previous_valid = 0;
    app->selection_failure_count += 1u;
    return 0;
  }
  app->selection_generation += 1u;
  app->selection_mutation_waiting_for_present = 1;
  app->host_frame_waiting_for_present = 1;
  return 1;
}

static B32
octavo_android_abort_selection_mutation(OctavoAndroidApp *app)
{
  if (!app) { return 0; }
  if (!app->selection_mutation_waiting_for_present) { return 1; }
  if (app->selection_mutation_previous_valid)
  {
    if (epub_reader_set_selection(
          &app->reader, app->selection_mutation_previous) !=
        EpubReaderResult_Ok)
    {
      return 0;
    }
  }
  else
  {
    epub_reader_clear_selection(&app->reader);
  }
  octavo_android_invalidate_prepared_static_frame(app);
  if (!epub_reader_build_frame(
        &app->reader, &app->reader_frame_storage, &app->reader_frame) ||
      !app->reader_frame.ready || !app->reader_frame.document_open)
  {
    return 0;
  }
  app->selection_mutation_waiting_for_present = 0;
  app->selection_mutation_previous = (DocSelection){0};
  app->selection_mutation_previous_valid = 0;
  app->selection_failure_count += 1u;
  app->host_frame_waiting_for_present = 1;
  return 1;
}

static B32
octavo_android_abort_selection_page_turn(OctavoAndroidApp *app)
{
  if (!app) { return 0; }
  if (!app->selection_page_turn_waiting_for_present) { return 1; }
  if ((app->selection_page_turn_direction != -1 &&
       app->selection_page_turn_direction != 1) ||
      !app->reader.has_current_page)
  {
    return 0;
  }

  app->layout_config.focused_spine_index =
    app->selection_page_turn_previous_spine_index;
  EpubReaderChange rollback = {0};
  EpubReaderResult rollback_result = epub_reader_move_page(
    &app->reader,
    -app->selection_page_turn_direction,
    app->layout_key,
    app->layout_config,
    (EpubReaderPageMoveOptions){
      .preserve_selection = 1,
      .same_spine_only = 1,
    },
    &rollback);
  SourceReaderPageRange previous =
    app->selection_page_turn_previous_page;
  if (rollback_result != EpubReaderResult_Ok || !rollback.changed ||
      !app->reader.has_current_page ||
      app->reader.active_spine_index !=
        app->selection_page_turn_previous_spine_index ||
      app->reader.view_byte_offset !=
        app->selection_page_turn_previous_byte_offset ||
      app->reader.current_page.spine_index != previous.spine_index ||
      app->reader.current_page.first_byte != previous.first_byte ||
      app->reader.current_page.one_past_last_byte !=
        previous.one_past_last_byte)
  {
    return 0;
  }

  if (app->selection_mutation_previous_valid)
  {
    if (epub_reader_set_selection(
          &app->reader, app->selection_mutation_previous) !=
        EpubReaderResult_Ok)
    {
      return 0;
    }
  }
  else
  {
    epub_reader_clear_selection(&app->reader);
  }
  octavo_android_invalidate_prepared_static_frame(app);
  if (!epub_reader_build_frame(
        &app->reader, &app->reader_frame_storage, &app->reader_frame) ||
      !app->reader_frame.ready || !app->reader_frame.document_open)
  {
    return 0;
  }

  app->page_move_waiting_for_present = 0;
  app->page_move_expected_spine_index = 0;
  app->page_move_expected_byte_offset = 0;
  app->selection_mutation_waiting_for_present = 0;
  app->selection_mutation_previous = (DocSelection){0};
  app->selection_mutation_previous_valid = 0;
  octavo_android_selection_page_turn_state_clear(app);
  app->selection_failure_count += 1u;
  app->host_frame_waiting_for_present = 1;
  return 1;
}

static B32
octavo_android_selection_highlight_for_range(const OctavoAndroidApp *app,
                                             U64 start,
                                             U64 end)
{
  return app && app->reader_frame.has_selection && end > start &&
    end > app->reader_frame.selection_start &&
    start < app->reader_frame.selection_end;
}

static void
octavo_android_draw_selection_handles(OctavoAndroidApp *app,
                                      OctavoAndroidPixels pixels)
{
  if (!app || !app->reader.has_active_selection ||
      app->reader.active_selection.text_byte_end <=
        app->reader.active_selection.text_byte_start)
  {
    return;
  }
  S32 start_x = 0;
  S32 start_y = 0;
  S32 start_height = 0;
  S32 end_x = 0;
  S32 end_y = 0;
  S32 end_height = 0;
  B32 start_visible = octavo_android_selection_endpoint_caret(
    app, app->reader.active_selection.text_byte_start,
    &start_x, &start_y, &start_height);
  B32 end_visible = octavo_android_selection_endpoint_caret(
    app, app->reader.active_selection.text_byte_end,
    &end_x, &end_y, &end_height);
  if (!start_visible && !end_visible) { return; }
  S32 radius = MAX(app->selection_handle_radius_px, 2);
  UI0Color color = app->reader_view_theme.colors[UI0ColorRole_Focus];
  UI0Rect clip = octavo_android_intersect_rect(
    app->reader_view_layout.page_surface_rect,
    octavo_android_pixel_bounds(pixels));
  if (start_visible)
  {
    octavo_android_fill_rect(
      pixels,
      ui0_rect(start_x - 1, start_y, 2, start_height + radius),
      clip,
      color);
    octavo_android_fill_rect(
      pixels,
      ui0_rect(start_x - radius, start_y + start_height,
               radius * 2, radius),
      clip,
      color);
  }
  if (end_visible)
  {
    octavo_android_fill_rect(
      pixels,
      ui0_rect(end_x - 1, end_y, 2, end_height + radius),
      clip,
      color);
    octavo_android_fill_rect(
      pixels,
      ui0_rect(end_x - radius, end_y + end_height,
               radius * 2, radius),
      clip,
      color);
  }
}

static jint
octavo_android_publish_word_at(OctavoAndroidApp *app, U32 hit)
{
  U32 start = 0;
  U32 end = 0;
  if (!octavo_android_selection_word_at(app, hit, &start, &end))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_UNAVAILABLE;
  }
  DocSelection selection = {
    .spine_index = app->reader_frame.spine_index,
    .text_byte_start = app->reader_frame.view_byte_offset + start,
    .text_byte_end = app->reader_frame.view_byte_offset + end,
  };
  return octavo_android_publish_selection(app, selection) ?
    OCTAVO_ANDROID_SELECTION_RESULT_ACCEPTED :
    OCTAVO_ANDROID_SELECTION_RESULT_FAILED;
}

static jint
octavo_android_begin_selection(OctavoAndroidApp *app, S32 x, S32 y)
{
  if (!app || !app->reader_initialized || !app->reader_frame.ready ||
      !app->reader_frame.document_open)
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_UNAVAILABLE;
  }
  if (octavo_android_presentation_pending(app))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_BUSY;
  }
  U32 hit = 0;
  if (!octavo_android_selection_hit_test(app, x, y, 0, &hit))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_UNAVAILABLE;
  }
  jint result = octavo_android_publish_word_at(app, hit);
  if (result == OCTAVO_ANDROID_SELECTION_RESULT_UNAVAILABLE && hit > 0)
  {
    /* A hit on the right half of a glyph resolves to its trailing caret. */
    U32 previous = octavo_android_selection_previous_boundary(
      app->reader_frame.visible_text, hit);
    result = octavo_android_publish_word_at(app, previous);
  }
  return result;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_beginSelection(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jfloat x,
  jfloat y)
{
  (void)environment;
  (void)type;
  if (!isfinite(x) || !isfinite(y))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_INVALID;
  }
  return octavo_android_begin_selection(
    octavo_android_from_handle(handle), (S32)x, (S32)y);
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_beginSelectionForAccessibility(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->reader_frame.ready)
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_UNAVAILABLE;
  }
  if (octavo_android_presentation_pending(app))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_BUSY;
  }
  S32 row_y = app->reader_view_layout.content_rect.y;
  for (U32 index = 0; index < app->reader_frame.style_row_count; ++index)
  {
    OctavoAndroidSelectionRowGeometry geometry = {0};
    if (!octavo_android_selection_row_geometry(
          app, app->reader_frame.style_rows + index, row_y, &geometry))
    {
      break;
    }
    row_y = geometry.next_y;
    if (geometry.end > geometry.start)
    {
      String8 text = app->reader_frame.visible_text;
      for (U32 byte = geometry.start; byte < geometry.end;)
      {
        BaseUnicodeDecode decode = base_unicode_utf8_decode(text, byte);
        if (!decode.valid || decode.advance == 0 ||
            decode.advance > geometry.end - byte)
        {
          break;
        }
        if (!octavo_android_selection_is_space(decode.scalar))
        {
          return octavo_android_publish_word_at(app, byte);
        }
        byte += decode.advance;
      }
    }
  }
  return OCTAVO_ANDROID_SELECTION_RESULT_UNAVAILABLE;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_updateSelection(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint handle_kind,
  jfloat x,
  jfloat y)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !isfinite(x) || !isfinite(y) ||
      (handle_kind != OCTAVO_ANDROID_SELECTION_HANDLE_START &&
       handle_kind != OCTAVO_ANDROID_SELECTION_HANDLE_END))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_INVALID;
  }
  if (octavo_android_presentation_pending(app))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_BUSY;
  }
  if (!app->reader.has_active_selection)
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_UNAVAILABLE;
  }
  U32 local = 0;
  if (!octavo_android_selection_hit_test(
        app, (S32)x, (S32)y, 1, &local))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_UNAVAILABLE;
  }
  U64 absolute = app->reader_frame.view_byte_offset + local;
  DocSelection selection = app->reader.active_selection;
  S32 active_handle = 0;
  if (!octavo_android_selection_adjust_range(
        &selection, handle_kind, absolute, &active_handle))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_ALREADY_PRESENTED;
  }
  if (!octavo_android_publish_selection(app, selection))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_FAILED;
  }
  return OCTAVO_ANDROID_SELECTION_RESULT_ACCEPTED |
    (active_handle << 8);
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_extendSelectionAcrossPage(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint handle_kind,
  jint direction,
  jfloat x)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !isfinite(x) ||
      (handle_kind != OCTAVO_ANDROID_SELECTION_HANDLE_START &&
       handle_kind != OCTAVO_ANDROID_SELECTION_HANDLE_END) ||
      (direction != -1 && direction != 1))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_INVALID;
  }
  if (octavo_android_presentation_pending(app))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_BUSY;
  }
  if (!app->resumed || !app->window || !app->reader_frame.ready ||
      !app->reader_frame.document_open ||
      !app->reader.has_active_selection || !app->reader.has_current_page ||
      app->reader.active_selection.spine_index !=
        app->reader.active_spine_index)
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_UNAVAILABLE;
  }

  DocSelection previous_selection = app->reader.active_selection;
  U64 dragged_endpoint =
    handle_kind == OCTAVO_ANDROID_SELECTION_HANDLE_START ?
      previous_selection.text_byte_start :
      previous_selection.text_byte_end;
  S32 endpoint_x = 0;
  S32 endpoint_y = 0;
  S32 endpoint_height = 0;
  if (!octavo_android_selection_endpoint_caret(
        app, dragged_endpoint, &endpoint_x, &endpoint_y, &endpoint_height))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_UNAVAILABLE;
  }

  SourceReaderPageRange previous_page = app->reader.current_page;
  U32 previous_spine_index = app->reader.active_spine_index;
  U64 previous_byte_offset = app->reader.view_byte_offset;
  app->layout_config.focused_spine_index = previous_spine_index;
  EpubReaderChange change = {0};
  EpubReaderResult move_result = epub_reader_move_page(
    &app->reader,
    direction,
    app->layout_key,
    app->layout_config,
    (EpubReaderPageMoveOptions){
      .preserve_selection = 1,
      .same_spine_only = 1,
    },
    &change);
  if (move_result == EpubReaderResult_Boundary)
  {
    app->page_move_boundary_count += 1u;
    return OCTAVO_ANDROID_SELECTION_RESULT_BOUNDARY;
  }
  if (move_result != EpubReaderResult_Ok || !change.changed ||
      !change.after.has_page || change.after.global_page_index == UINT64_MAX ||
      change.after.spine_index != previous_spine_index)
  {
    app->navigation_failure_count += 1u;
    app->selection_failure_count += 1u;
    return OCTAVO_ANDROID_SELECTION_RESULT_FAILED;
  }

  app->selection_mutation_previous = previous_selection;
  app->selection_mutation_previous_valid = 1;
  app->selection_mutation_waiting_for_present = 1;
  app->selection_page_turn_previous_page = previous_page;
  app->selection_page_turn_previous_spine_index = previous_spine_index;
  app->selection_page_turn_previous_byte_offset = previous_byte_offset;
  app->selection_page_turn_direction = direction;
  app->selection_page_turn_waiting_for_present = 1;
  app->page_move_waiting_for_present = 1;
  app->page_move_expected_spine_index = change.after.spine_index;
  app->page_move_expected_byte_offset = change.after.byte_offset;
  octavo_android_invalidate_prepared_static_frame(app);

  B32 frame_ready = epub_reader_build_frame(
    &app->reader, &app->reader_frame_storage, &app->reader_frame) &&
    app->reader_frame.ready && app->reader_frame.document_open;
  S32 target_y = 0;
  U32 local = 0;
  if (!frame_ready ||
      !octavo_android_selection_edge_target_y(app, direction, &target_y) ||
      !octavo_android_selection_hit_test(
        app, (S32)x, target_y, 1, &local))
  {
    if (!octavo_android_abort_selection_page_turn(app))
    {
      app->selection_failure_count += 1u;
    }
    return OCTAVO_ANDROID_SELECTION_RESULT_FAILED;
  }

  U64 absolute = app->reader_frame.view_byte_offset + local;
  DocSelection selection = previous_selection;
  S32 active_handle = 0;
  if (!octavo_android_selection_adjust_range(
        &selection, handle_kind, absolute, &active_handle))
  {
    if (!octavo_android_abort_selection_page_turn(app))
    {
      app->selection_failure_count += 1u;
    }
    return OCTAVO_ANDROID_SELECTION_RESULT_FAILED;
  }

  U64 visible_start = app->reader_frame.view_byte_offset;
  U64 visible_end = visible_start + app->reader_frame.visible_text.size;
  if (active_handle == OCTAVO_ANDROID_SELECTION_HANDLE_END &&
      selection.text_byte_end <= visible_start &&
      app->reader_frame.visible_text.size > 0)
  {
    BaseUnicodeDecode first = base_unicode_utf8_decode(
      app->reader_frame.visible_text, 0);
    if (!first.valid || first.advance == 0 ||
        first.advance > app->reader_frame.visible_text.size)
    {
      if (!octavo_android_abort_selection_page_turn(app))
      {
        app->selection_failure_count += 1u;
      }
      return OCTAVO_ANDROID_SELECTION_RESULT_FAILED;
    }
    selection.text_byte_end = visible_start + first.advance;
  }
  else if (active_handle == OCTAVO_ANDROID_SELECTION_HANDLE_START &&
           selection.text_byte_start >= visible_end &&
           app->reader_frame.visible_text.size > 0)
  {
    U32 previous = octavo_android_selection_previous_boundary(
      app->reader_frame.visible_text,
      (U32)app->reader_frame.visible_text.size);
    selection.text_byte_start = visible_start + previous;
  }

  if (selection.text_byte_start >= selection.text_byte_end ||
      selection.text_byte_end - selection.text_byte_start >
        EPUB_READER_FRAME_VISIBLE_TEXT_CAP)
  {
    jint rejected = selection.text_byte_start < selection.text_byte_end ?
      OCTAVO_ANDROID_SELECTION_RESULT_LIMIT :
      OCTAVO_ANDROID_SELECTION_RESULT_FAILED;
    if (!octavo_android_abort_selection_page_turn(app))
    {
      app->selection_failure_count += 1u;
    }
    return rejected;
  }

  if (epub_reader_set_selection(&app->reader, selection) !=
        EpubReaderResult_Ok ||
      !epub_reader_build_frame(
        &app->reader, &app->reader_frame_storage, &app->reader_frame) ||
      !app->reader_frame.ready || !app->reader_frame.document_open ||
      !app->reader_frame.has_selection)
  {
    if (!octavo_android_abort_selection_page_turn(app))
    {
      app->selection_failure_count += 1u;
    }
    return OCTAVO_ANDROID_SELECTION_RESULT_FAILED;
  }

  U64 active_endpoint =
    active_handle == OCTAVO_ANDROID_SELECTION_HANDLE_START ?
      selection.text_byte_start : selection.text_byte_end;
  if (!octavo_android_selection_endpoint_caret(
        app, active_endpoint, &endpoint_x, &endpoint_y, &endpoint_height))
  {
    if (!octavo_android_abort_selection_page_turn(app))
    {
      app->selection_failure_count += 1u;
    }
    return OCTAVO_ANDROID_SELECTION_RESULT_FAILED;
  }

  app->selection_generation += 1u;
  app->page_move_success_count += 1u;
  app->host_frame_waiting_for_present = 1;
  return OCTAVO_ANDROID_SELECTION_RESULT_ACCEPTED |
    (active_handle << 8);
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_clearSelection(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app) { return OCTAVO_ANDROID_SELECTION_RESULT_INVALID; }
  if (octavo_android_presentation_pending(app))
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_BUSY;
  }
  if (!app->reader.has_active_selection)
  {
    return OCTAVO_ANDROID_SELECTION_RESULT_ALREADY_PRESENTED;
  }
  return octavo_android_publish_selection_clear(app) ?
    OCTAVO_ANDROID_SELECTION_RESULT_ACCEPTED :
    OCTAVO_ANDROID_SELECTION_RESULT_FAILED;
}

JNIEXPORT jlongArray JNICALL
Java_ro_devze_octavo_OctavoNative_selectionSnapshot(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  jlong values[OCTAVO_ANDROID_SELECTION_SNAPSHOT_COUNT] = {0};
  values[0] = OCTAVO_ANDROID_SELECTION_SNAPSHOT_VERSION;
  values[1] = OCTAVO_ANDROID_SELECTION_SNAPSHOT_COUNT;
  if (app && app->reader_frame.ready && app->reader_frame.document_open)
  {
    values[2] |= OCTAVO_ANDROID_SELECTION_FLAG_READY;
    if (app->selection_mutation_waiting_for_present)
    {
      values[2] |= OCTAVO_ANDROID_SELECTION_FLAG_PENDING;
    }
    if (app->reader.has_active_selection &&
        app->reader.active_selection.spine_index ==
          app->reader_frame.spine_index &&
        app->reader.active_selection.text_byte_end >
          app->reader.active_selection.text_byte_start)
    {
      S32 start_x = 0;
      S32 start_y = 0;
      S32 start_height = 0;
      S32 end_x = 0;
      S32 end_y = 0;
      S32 end_height = 0;
      B32 start_visible = octavo_android_selection_endpoint_caret(
        app, app->reader.active_selection.text_byte_start,
        &start_x, &start_y, &start_height);
      B32 end_visible = octavo_android_selection_endpoint_caret(
        app, app->reader.active_selection.text_byte_end,
        &end_x, &end_y, &end_height);
      values[2] |= OCTAVO_ANDROID_SELECTION_FLAG_ACTIVE;
      values[3] = (jlong)app->reader.active_selection.text_byte_start;
      values[4] = (jlong)app->reader.active_selection.text_byte_end;
      if (start_visible)
      {
        values[2] |= OCTAVO_ANDROID_SELECTION_FLAG_START_VISIBLE;
        values[5] = start_x;
        values[6] = start_y + start_height;
        values[9] = start_height;
      }
      if (end_visible)
      {
        values[2] |= OCTAVO_ANDROID_SELECTION_FLAG_END_VISIBLE;
        values[7] = end_x;
        values[8] = end_y + end_height;
        values[10] = end_height;
      }
    }
    values[11] = (jlong)app->selection_generation;
    values[12] = (jlong)app->selection_presented_generation;
    values[13] = (jlong)app->selection_failure_count;
  }
  jlongArray result = (*environment)->NewLongArray(
    environment, OCTAVO_ANDROID_SELECTION_SNAPSHOT_COUNT);
  if (!result) { return 0; }
  (*environment)->SetLongArrayRegion(
    environment, result, 0,
    OCTAVO_ANDROID_SELECTION_SNAPSHOT_COUNT, values);
  return result;
}

JNIEXPORT jbyteArray JNICALL
Java_ro_devze_octavo_OctavoNative_selectedTextUtf8(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || app->selection_mutation_waiting_for_present ||
      !app->reader.has_active_selection || !app->reader.spine_text.str)
  {
    return 0;
  }
  DocSelection selection = app->reader.active_selection;
  if (selection.spine_index != app->reader.active_spine_index ||
      selection.text_byte_start >= selection.text_byte_end ||
      selection.text_byte_end > app->reader.spine_text.size ||
      selection.text_byte_end - selection.text_byte_start >
        EPUB_READER_FRAME_VISIBLE_TEXT_CAP)
  {
    return 0;
  }
  jsize size = (jsize)(selection.text_byte_end - selection.text_byte_start);
  jbyteArray result = (*environment)->NewByteArray(environment, size);
  if (!result) { return 0; }
  (*environment)->SetByteArrayRegion(
    environment, result, 0, size,
    (const jbyte *)(app->reader.spine_text.str +
                    selection.text_byte_start));
  return result;
}

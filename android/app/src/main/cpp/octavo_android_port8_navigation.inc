/*
 * Android Port 8 structural-navigation host policy.
 *
 * This file is included by octavo_android_jni.c after the shared Reader0,
 * UI0, and Readerview0 implementation has been consumed exactly once. EPUB
 * interpretation, canonical targets, pagination, and history remain Reader0
 * responsibilities; this host only stages them behind Android presentation.
 */

enum
{
  OCTAVO_ANDROID_NAVIGATION_PACKET_VERSION = 1,
  OCTAVO_ANDROID_NAVIGATION_HEADER_COUNT = 16,
  OCTAVO_ANDROID_NAVIGATION_ROW_STRIDE = 10,
  OCTAVO_ANDROID_NAVIGATION_ROW_CAP = EPUB_READER_FRAME_NAV_ITEM_CAP,
  OCTAVO_ANDROID_POSITION_QUALIFICATION_FIELD_COUNT = 7,

  OCTAVO_ANDROID_NAVIGATION_FLAG_READY = 1 << 0,
  OCTAVO_ANDROID_NAVIGATION_FLAG_FALLBACK = 1 << 1,
  OCTAVO_ANDROID_NAVIGATION_FLAG_TRUNCATED = 1 << 2,
  OCTAVO_ANDROID_NAVIGATION_FLAG_LOCATION_READY = 1 << 3,
  OCTAVO_ANDROID_NAVIGATION_FLAG_PAGE_MEANINGFUL = 1 << 4,
  OCTAVO_ANDROID_NAVIGATION_FLAG_PENDING = 1 << 5,

  OCTAVO_ANDROID_NAVIGATION_ROW_CURRENT = 1 << 0,
  OCTAVO_ANDROID_NAVIGATION_ROW_DESTINATION_VALID = 1 << 1,
  OCTAVO_ANDROID_NAVIGATION_ROW_PROGRESS_AVAILABLE = 1 << 2,
  OCTAVO_ANDROID_NAVIGATION_ROW_SYNTHETIC = 1 << 3,

  OCTAVO_ANDROID_NAVIGATION_ACCEPTED = 1,
  OCTAVO_ANDROID_NAVIGATION_ALREADY_PRESENTED = 2,
  OCTAVO_ANDROID_NAVIGATION_INVALID = -1,
  OCTAVO_ANDROID_NAVIGATION_UNAVAILABLE = -2,
  OCTAVO_ANDROID_NAVIGATION_BUSY = -3,
  OCTAVO_ANDROID_NAVIGATION_FAILED = -4,

  OCTAVO_ANDROID_PROGRESS_CHAPTER = 0,
  OCTAVO_ANDROID_PROGRESS_PAGE = 1,
  OCTAVO_ANDROID_PROGRESS_LOCATION = 2,
  OCTAVO_ANDROID_PROGRESS_PERCENTAGE = 3,
};

static int
octavo_android_navigation_result(EpubReaderResult result)
{
  if (result == EpubReaderResult_InvalidInput)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (result == EpubReaderResult_Boundary ||
      result == EpubReaderResult_Unsupported)
  {
    return OCTAVO_ANDROID_NAVIGATION_UNAVAILABLE;
  }
  if (result == EpubReaderResult_NotOpen)
  {
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }
  return OCTAVO_ANDROID_NAVIGATION_FAILED;
}

static int
octavo_android_navigation_can_begin(OctavoAndroidApp *app)
{
  return app && app->resumed && app->window && app->reader_initialized &&
    app->reader_frame.ready && app->reader_view_ready &&
    app->presented_anchor_valid &&
    !octavo_android_presentation_pending(app);
}

static EpubReaderNavigationEntry
octavo_android_presented_navigation_entry(
  OctavoAndroidApp *app,
  EpubReaderNavigationReason reason)
{
  EpubReaderNavigationEntry result =
    epub_reader_navigation_entry(&app->reader, reason);
  if (app && app->presented_anchor_valid)
  {
    result.spine_index = app->presented_anchor_spine_index;
    result.byte_offset = app->presented_anchor_byte_offset;
  }
  result.reason = reason;
  return result;
}

static B32
octavo_android_restore_presented_reader(OctavoAndroidApp *app)
{
  if (!app || !app->presented_anchor_valid)
  {
    return 0;
  }
  octavo_android_invalidate_prepared_static_frame(app);
  app->layout_config.focused_spine_index =
    app->presented_anchor_spine_index;
  EpubReaderNavigationResult restoration = {0};
  EpubReaderResult result = epub_reader_navigate_to_location(
    &app->reader,
    app->presented_anchor_spine_index,
    app->presented_anchor_byte_offset,
    EpubReaderNavigationReason_Location,
    app->layout_key,
    app->layout_config,
    (EpubReaderNavigationOptions){.suppress_history = 1},
    &restoration);
  if (result != EpubReaderResult_Ok ||
      restoration.target_spine_index !=
        app->presented_anchor_spine_index ||
      restoration.target_byte_offset !=
        app->presented_anchor_byte_offset ||
      !app->reader.has_current_page ||
      app->reader.current_page.spine_index !=
        app->presented_anchor_spine_index ||
      app->reader.current_page.first_byte >
        app->presented_anchor_byte_offset ||
      app->reader.current_page.one_past_last_byte <=
        app->presented_anchor_byte_offset)
  {
    /* Reader0 could not reconstruct the exact last posted origin. Keep the
       still-visible platform buffer, but make every native publication gate
       fail until the host recreates this reader. A speculative candidate must
       never become a readable or presentable fallback state. */
    octavo_android_invalidate_prepared_static_frame(app);
    app->presented_anchor_valid = 0;
    app->reader_frame.ready = 0;
    app->reader_frame.document_open = 0;
    app->reader_view_ready = 0;
    app->reader_initialized = 0;
    return 0;
  }
  app->layout_config.focused_spine_index = app->reader.active_spine_index;
  return epub_reader_build_frame(
      &app->reader, &app->reader_frame_storage, &app->reader_frame) &&
    app->reader_frame.ready && app->reader_frame.document_open &&
    app->reader_frame.spine_index == app->presented_anchor_spine_index;
}

static int
octavo_android_navigation_failure_after_restore(
  OctavoAndroidApp *app,
  EpubReaderResult result)
{
  if (!octavo_android_restore_presented_reader(app))
  {
    __android_log_print(
      ANDROID_LOG_ERROR,
      "8vo",
      "Unable to restore the last presented reader position after a "
      "failed structural navigation request");
    return OCTAVO_ANDROID_NAVIGATION_FAILED;
  }
  return octavo_android_navigation_result(result);
}

static int
octavo_android_begin_navigation_transaction(
  OctavoAndroidApp *app,
  S32 kind,
  EpubReaderNavigationReason reason,
  EpubReaderNavigationEntry origin,
  U32 target_spine_index,
  U64 target_byte_offset,
  B32 history_traversal,
  B32 history_forward,
  EpubReaderNavigationEntry history_current,
  EpubReaderNavigationEntry history_target)
{
  if (!app || app->semantic_navigation_waiting_for_present ||
      !app->reader.has_current_page ||
      app->reader.current_page.spine_index != target_spine_index ||
      app->reader.current_page.first_byte > target_byte_offset ||
      app->reader.current_page.one_past_last_byte <= target_byte_offset)
  {
    return 0;
  }

  octavo_android_invalidate_prepared_static_frame(app);
  app->semantic_navigation_generation += 1u;
  app->semantic_navigation_lifecycle_generation = app->lifecycle_generation;
  app->semantic_navigation_surface_generation = app->surface_generation;
  app->semantic_navigation_kind = kind;
  app->semantic_navigation_reason = reason;
  app->semantic_navigation_origin = origin;
  app->semantic_navigation_expected_spine_index = target_spine_index;
  app->semantic_navigation_expected_byte_offset = target_byte_offset;
  app->semantic_navigation_history_traversal = history_traversal;
  app->semantic_navigation_history_forward = history_forward;
  app->semantic_navigation_history_current = history_current;
  app->semantic_navigation_history_target = history_target;
  app->semantic_navigation_waiting_for_present = 1;
  app->semantic_navigation_request_count += 1u;
  return 1;
}

static int
octavo_android_begin_direct_navigation(
  OctavoAndroidApp *app,
  S32 kind,
  EpubReaderNavigationReason reason,
  EpubReaderNavigationEntry origin,
  EpubReaderNavigationResult navigation)
{
  if (!octavo_android_begin_navigation_transaction(
        app,
        kind,
        reason,
        origin,
        navigation.target_spine_index,
        navigation.target_byte_offset,
        0,
        0,
        (EpubReaderNavigationEntry){0},
        (EpubReaderNavigationEntry){0}))
  {
    app->semantic_navigation_failure_count += 1u;
    if (!octavo_android_restore_presented_reader(app))
    {
      __android_log_print(
        ANDROID_LOG_ERROR,
        "8vo",
        "Android Port 8 could not restore the presented origin after a "
        "rejected structural transaction");
    }
    return OCTAVO_ANDROID_NAVIGATION_FAILED;
  }
  return OCTAVO_ANDROID_NAVIGATION_ACCEPTED;
}

static B32
octavo_android_abort_structural_navigation(OctavoAndroidApp *app)
{
  if (!app)
  {
    return 0;
  }
  if (!app->semantic_navigation_waiting_for_present)
  {
    return 1;
  }
  if (app->semantic_navigation_kind ==
        OCTAVO_ANDROID_NAVIGATION_KIND_SEARCH)
  {
    app->reader.search_active_index =
      app->search_navigation_previous_active_index;
    app->reader.search_has_active =
      app->search_navigation_previous_has_active;
  }
  B32 restored = octavo_android_restore_presented_reader(app);
  if (app->semantic_navigation_history_traversal)
  {
    epub_reader_history_finish(
      &app->reader,
      app->semantic_navigation_history_forward,
      app->semantic_navigation_history_current,
      app->semantic_navigation_history_target,
      0);
    app->semantic_navigation_history_traversal = 0;
  }
  app->presented_history_back_count = app->reader.back_stack_count;
  app->presented_history_forward_count = app->reader.forward_stack_count;
  if (!restored)
  {
    return 0;
  }
  app->semantic_navigation_waiting_for_present = 0;
  app->semantic_navigation_history_traversal = 0;
  app->semantic_navigation_history_forward = 0;
  app->semantic_navigation_kind = 0;
  app->semantic_navigation_reason = EpubReaderNavigationReason_None;
  app->semantic_navigation_origin = (EpubReaderNavigationEntry){0};
  app->semantic_navigation_history_current =
    (EpubReaderNavigationEntry){0};
  app->semantic_navigation_history_target =
    (EpubReaderNavigationEntry){0};
  app->semantic_navigation_expected_spine_index = 0;
  app->semantic_navigation_expected_byte_offset = 0;
  app->semantic_navigation_lifecycle_generation = 0;
  app->semantic_navigation_surface_generation = 0;
  app->search_navigation_previous_active_index = 0;
  app->search_navigation_previous_has_active = 0;
  app->host_frame_waiting_for_present = 1;
  return 1;
}

static S32
octavo_android_cancel_pending_navigation(OctavoAndroidApp *app)
{
  if (!app)
  {
    return -1;
  }
  B32 changed = 0;
  if (app->semantic_navigation_waiting_for_present)
  {
    changed = 1;
    if (!octavo_android_abort_structural_navigation(app))
    {
      return -1;
    }
  }
  if (app->progress_display_waiting_for_present)
  {
    octavo_android_invalidate_prepared_static_frame(app);
    app->progress_display_mode = app->progress_display_presented_mode;
    app->progress_display_waiting_for_present = 0;
    changed = 1;
  }
  if (app->selection_page_turn_waiting_for_present)
  {
    changed = 1;
    if (!octavo_android_abort_selection_page_turn(app))
    {
      return -1;
    }
  }
  else if (app->selection_mutation_waiting_for_present)
  {
    changed = 1;
    if (!octavo_android_abort_selection_mutation(app))
    {
      return -1;
    }
  }
  if (app->search_mutation_waiting_for_present)
  {
    changed = 1;
    if (!octavo_android_abort_search_mutation(app))
    {
      return -1;
    }
  }
  if (changed)
  {
    app->host_frame_waiting_for_present = 1;
  }
  return changed ? 1 : 0;
}

static B32
octavo_android_commit_structural_navigation(OctavoAndroidApp *app)
{
  if (!app || !app->semantic_navigation_waiting_for_present)
  {
    return 0;
  }
  if (app->semantic_navigation_lifecycle_generation !=
        app->lifecycle_generation ||
      app->semantic_navigation_surface_generation != app->surface_generation)
  {
    app->semantic_navigation_failure_count += 1u;
    (void)octavo_android_abort_structural_navigation(app);
    return 0;
  }

  if (app->semantic_navigation_history_traversal)
  {
    epub_reader_history_finish(
      &app->reader,
      app->semantic_navigation_history_forward,
      app->semantic_navigation_history_current,
      app->semantic_navigation_history_target,
      1);
  }
  else
  {
    EpubReaderNavigationEntry presented =
      epub_reader_navigation_entry(
        &app->reader, EpubReaderNavigationReason_None);
    presented.spine_index =
      app->semantic_navigation_expected_spine_index;
    presented.byte_offset =
      app->semantic_navigation_expected_byte_offset;
    EpubReaderChange recorded =
      epub_reader_record_presented_navigation(
        &app->reader,
        app->semantic_navigation_origin,
        presented,
        app->semantic_navigation_reason);
    if (!epub_reader_navigation_entries_match(recorded.after, presented))
    {
      app->semantic_navigation_failure_count += 1u;
      (void)octavo_android_abort_structural_navigation(app);
      return 0;
    }
  }

  app->presented_anchor_spine_index =
    app->semantic_navigation_expected_spine_index;
  app->presented_anchor_byte_offset =
    app->semantic_navigation_expected_byte_offset;
  app->presented_anchor_valid = 1;
  app->semantic_navigation_presented_generation =
    app->semantic_navigation_generation;
  app->semantic_navigation_presented_count += 1u;
  app->semantic_navigation_waiting_for_present = 0;
  app->semantic_navigation_history_traversal = 0;
  app->semantic_navigation_history_forward = 0;
  app->semantic_navigation_kind = 0;
  app->semantic_navigation_reason = EpubReaderNavigationReason_None;
  app->semantic_navigation_origin = (EpubReaderNavigationEntry){0};
  app->semantic_navigation_history_current =
    (EpubReaderNavigationEntry){0};
  app->semantic_navigation_history_target =
    (EpubReaderNavigationEntry){0};
  app->semantic_navigation_expected_spine_index = 0;
  app->semantic_navigation_expected_byte_offset = 0;
  app->semantic_navigation_lifecycle_generation = 0;
  app->semantic_navigation_surface_generation = 0;
  app->search_navigation_previous_active_index = 0;
  app->search_navigation_previous_has_active = 0;
  app->presented_history_back_count = app->reader.back_stack_count;
  app->presented_history_forward_count = app->reader.forward_stack_count;
  return 1;
}

static B32
octavo_android_effective_semantic_anchor(
  const OctavoAndroidApp *app,
  U32 *out_spine_index,
  U64 *out_byte_offset)
{
  if (out_spine_index) { *out_spine_index = 0; }
  if (out_byte_offset) { *out_byte_offset = 0; }
  if (!app || !out_spine_index || !out_byte_offset)
  {
    return 0;
  }

  if (app->semantic_navigation_waiting_for_present)
  {
    *out_spine_index = app->semantic_navigation_expected_spine_index;
    *out_byte_offset = app->semantic_navigation_expected_byte_offset;
  }
  else if (app->page_move_waiting_for_present)
  {
    *out_spine_index = app->page_move_expected_spine_index;
    *out_byte_offset = app->page_move_expected_byte_offset;
  }
  else if (app->reflow_waiting_for_present)
  {
    *out_spine_index = app->reflow_anchor_spine_index;
    *out_byte_offset = app->reflow_anchor_byte_offset;
  }
  else if (app->presented_anchor_valid)
  {
    *out_spine_index = app->presented_anchor_spine_index;
    *out_byte_offset = app->presented_anchor_byte_offset;
  }
  else if (app->restore_succeeded)
  {
    *out_spine_index = app->resume_spine_index;
    *out_byte_offset = app->resume_byte_offset;
  }
  else if (app->reader_frame.ready && app->reader_frame.document_open)
  {
    *out_spine_index = app->reader_frame.spine_index;
    *out_byte_offset = app->reader_frame.view_byte_offset;
  }
  else
  {
    return 0;
  }
  return 1;
}

static const EpubReaderFrameSectionItem *
octavo_android_section_item_for_nav_index(
  const OctavoAndroidApp *app,
  U32 nav_index)
{
  if (!app || !app->reader_frame.ready)
  {
    return 0;
  }
  for (U32 index = 0;
       index < app->reader_frame.section_item_count;
       index += 1)
  {
    const EpubReaderFrameSectionItem *item =
      app->reader_frame.section_items + index;
    if (item->nav_index == nav_index)
    {
      return item;
    }
  }
  return 0;
}

static void
octavo_android_format_progress_label(OctavoAndroidApp *app)
{
  if (!app)
  {
    return;
  }
  U32 anchor_spine_index = app->reader_frame.spine_index;
  U64 anchor_byte_offset = app->reader_frame.view_byte_offset;
  B32 anchor_valid = octavo_android_effective_semantic_anchor(
    app, &anchor_spine_index, &anchor_byte_offset);

  EpubReaderLocationSummary location = {0};
  if (anchor_valid)
  {
    (void)epub_reader_location_summary_for_position(
      &app->reader,
      anchor_spine_index,
      anchor_byte_offset,
      &location);
  }
  else
  {
    location = app->reader_frame.location;
  }

  EpubReaderCurrentNavPointSummary current_section = {0};
  if (anchor_valid &&
      app->progress_display_mode == OCTAVO_ANDROID_PROGRESS_CHAPTER)
  {
    (void)epub_reader_current_nav_point_summary_for_position(
      &app->reader,
      anchor_spine_index,
      anchor_byte_offset,
      &current_section);
  }
  const EpubReaderFrameSectionItem *section = current_section.available ?
    octavo_android_section_item_for_nav_index(
      app, current_section.nav_index) : 0;
  char chapter[EPUB_READER_FRAME_NAV_LABEL_CAP + 1] = {0};
  if (section && section->label_length > 0 &&
      section->label_length < sizeof(chapter))
  {
    memcpy(chapter, section->label, section->label_length);
    chapter[section->label_length] = 0;
  }
  else
  {
    U64 section_number = current_section.available ?
      (U64)current_section.nav_index + 1u :
      (U64)anchor_spine_index + 1u;
    (void)snprintf(chapter,
                   sizeof(chapter),
                   "Section %llu",
                   (unsigned long long)section_number);
  }

  U64 page_index = app->reader_frame.page_index;
  U64 page_count = app->reader_frame.page_count;
  switch (app->progress_display_mode)
  {
    case OCTAVO_ANDROID_PROGRESS_CHAPTER:
    {
      (void)snprintf(app->progress_label,
                     sizeof(app->progress_label),
                     "%s",
                     chapter);
    } break;

    case OCTAVO_ANDROID_PROGRESS_PAGE:
    {
      if (page_index > 0 && page_count > 0)
      {
        (void)snprintf(app->progress_label,
                       sizeof(app->progress_label),
                       "Page %llu of %llu",
                       (unsigned long long)page_index,
                       (unsigned long long)page_count);
      }
      else if (location.available)
      {
        (void)snprintf(app->progress_label,
                       sizeof(app->progress_label),
                       "Page unavailable | %llu%%",
                       (unsigned long long)location.percent);
      }
      else
      {
        (void)snprintf(app->progress_label,
                       sizeof(app->progress_label),
                       "Page unavailable | %s",
                       chapter);
      }
    } break;

    case OCTAVO_ANDROID_PROGRESS_LOCATION:
    {
      if (location.available)
      {
        (void)snprintf(app->progress_label,
                       sizeof(app->progress_label),
                       "Location %llu of %llu",
                       (unsigned long long)location.location_index,
                       (unsigned long long)location.location_count);
      }
      else
      {
        (void)snprintf(app->progress_label,
                       sizeof(app->progress_label),
                       "Location unavailable | %s",
                       chapter);
      }
    } break;

    case OCTAVO_ANDROID_PROGRESS_PERCENTAGE:
    default:
    {
      if (location.available)
      {
        (void)snprintf(app->progress_label,
                       sizeof(app->progress_label),
                       "%llu%%",
                       (unsigned long long)location.percent);
      }
      else
      {
        (void)snprintf(app->progress_label,
                       sizeof(app->progress_label),
                       "%s | Progress unavailable",
                       chapter);
      }
    } break;
  }
}

JNIEXPORT jlongArray JNICALL
Java_ro_devze_octavo_OctavoNative_contentsSnapshot(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  DocDocumentId location_document_before = app ?
    app->reader.location_document_id : 0;
  U32 location_spine_count_before = app ?
    app->reader.location_spine_count : 0;
  U32 location_next_before = app ?
    app->reader.location_next_spine_index : 0;
  U64 location_total_before = app ?
    app->reader.location_total_text_bytes : 0;
  B32 location_complete_before = app ?
    app->reader.location_cache_complete : 0;
  B32 location_valid_before = app ?
    app->reader.location_cache_valid : 0;
  U32 row_count = app && app->reader_frame.ready ?
    MIN(app->reader_frame.section_item_count,
        (U32)OCTAVO_ANDROID_NAVIGATION_ROW_CAP) : 0;
  U32 total_count = app && app->reader_frame.ready ?
    app->reader_frame.section_item_total_count : 0;
  jsize value_count = OCTAVO_ANDROID_NAVIGATION_HEADER_COUNT +
    (jsize)row_count * OCTAVO_ANDROID_NAVIGATION_ROW_STRIDE;
  jlong values[
    OCTAVO_ANDROID_NAVIGATION_HEADER_COUNT +
    OCTAVO_ANDROID_NAVIGATION_ROW_CAP *
      OCTAVO_ANDROID_NAVIGATION_ROW_STRIDE] = {0};
  values[0] = OCTAVO_ANDROID_NAVIGATION_PACKET_VERSION;
  values[1] = OCTAVO_ANDROID_NAVIGATION_HEADER_COUNT;
  values[2] = OCTAVO_ANDROID_NAVIGATION_ROW_STRIDE;
  values[4] = row_count;
  values[5] = total_count;
  values[6] = -1;

  if (app && app->reader_frame.ready && app->reader_frame.document_open &&
      app->presented_anchor_valid)
  {
    values[3] |= OCTAVO_ANDROID_NAVIGATION_FLAG_READY;
    if (total_count > row_count)
    {
      values[3] |= OCTAVO_ANDROID_NAVIGATION_FLAG_TRUNCATED;
    }
    if (app->reader.location_cache_complete &&
        app->reader.location_cache_valid)
    {
      values[3] |= OCTAVO_ANDROID_NAVIGATION_FLAG_LOCATION_READY;
    }
    if (app->reader_frame.page_index > 0 &&
        app->reader_frame.page_count > 0 &&
        !octavo_android_presentation_pending(app))
    {
      values[3] |= OCTAVO_ANDROID_NAVIGATION_FLAG_PAGE_MEANINGFUL;
      values[14] = (jlong)app->reader_frame.page_index;
      values[15] = (jlong)app->reader_frame.page_count;
    }
    if (octavo_android_presentation_pending(app))
    {
      values[3] |= OCTAVO_ANDROID_NAVIGATION_FLAG_PENDING;
    }
    values[7] = (jlong)app->presented_history_back_count;
    values[8] = (jlong)app->presented_history_forward_count;
    values[9] = (jlong)app->semantic_navigation_generation;
    values[10] = (jlong)app->semantic_navigation_presented_generation;

    EpubReaderLocationSummary presented_location = {0};
    if (epub_reader_location_summary_for_position(
          &app->reader,
          app->presented_anchor_spine_index,
          app->presented_anchor_byte_offset,
          &presented_location))
    {
      values[11] = (jlong)presented_location.location_index;
      values[12] = (jlong)presented_location.location_count;
      values[13] = (jlong)presented_location.percent;
    }

    EpubReaderCurrentNavPointSummary current_section = {0};
    (void)epub_reader_current_nav_point_summary_for_position(
      &app->reader,
      app->presented_anchor_spine_index,
      app->presented_anchor_byte_offset,
      &current_section);
    B32 synthetic_fallback =
      current_section.available &&
      current_section.synthetic_spine_fallback;
    for (U32 row_index = 0; row_index < row_count; row_index += 1)
    {
      const EpubReaderFrameSectionItem *item =
        app->reader_frame.section_items + row_index;
      EpubReaderNavPointDestinationSummary destination = {0};
      B32 destination_valid =
        epub_reader_nav_point_destination_summary(
          &app->reader, item->nav_index, &destination);
      S32 at = OCTAVO_ANDROID_NAVIGATION_HEADER_COUNT +
        (S32)row_index * OCTAVO_ANDROID_NAVIGATION_ROW_STRIDE;
      values[at + 0] = item->nav_index;
      values[at + 1] = item->depth;
      if (destination_valid)
      {
        values[at + 2] |=
          OCTAVO_ANDROID_NAVIGATION_ROW_DESTINATION_VALID;
        values[at + 3] = destination.target_spine_index;
        values[at + 4] = (jlong)destination.target_byte_offset;
        if (destination.synthetic_spine_fallback)
        {
          values[at + 2] |= OCTAVO_ANDROID_NAVIGATION_ROW_SYNTHETIC;
          synthetic_fallback = 1;
        }
        if (destination.location.available)
        {
          values[at + 2] |=
            OCTAVO_ANDROID_NAVIGATION_ROW_PROGRESS_AVAILABLE;
          values[at + 5] = (jlong)destination.location.location_index;
          values[at + 6] = (jlong)destination.location.location_count;
          values[at + 7] = (jlong)destination.location.percent;
        }
      }
      if (current_section.available &&
          item->nav_index == current_section.nav_index)
      {
        values[6] = row_index;
      }
    }
    if (synthetic_fallback)
    {
      values[3] |= OCTAVO_ANDROID_NAVIGATION_FLAG_FALLBACK;
    }
    if (values[6] >= 0 && values[6] < row_count)
    {
      S32 at = OCTAVO_ANDROID_NAVIGATION_HEADER_COUNT +
        (S32)values[6] * OCTAVO_ANDROID_NAVIGATION_ROW_STRIDE;
      values[at + 2] |= OCTAVO_ANDROID_NAVIGATION_ROW_CURRENT;
    }
  }

  if (app &&
      (location_document_before != app->reader.location_document_id ||
       location_spine_count_before != app->reader.location_spine_count ||
       location_next_before != app->reader.location_next_spine_index ||
       location_total_before != app->reader.location_total_text_bytes ||
       location_complete_before != app->reader.location_cache_complete ||
       location_valid_before != app->reader.location_cache_valid))
  {
    octavo_android_invalidate_prepared_static_frame(app);
  }

  jlongArray result = (*environment)->NewLongArray(environment, value_count);
  if (result)
  {
    (*environment)->SetLongArrayRegion(
      environment, result, 0, value_count, values);
  }
  return result;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_contentsLabel(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint row_index)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->reader_frame.ready || row_index < 0 ||
      (U32)row_index >= app->reader_frame.section_item_count ||
      (U32)row_index >= OCTAVO_ANDROID_NAVIGATION_ROW_CAP)
  {
    return (*environment)->NewStringUTF(environment, "");
  }
  const EpubReaderFrameSectionItem *item =
    app->reader_frame.section_items + row_index;
  if (item->label_length >= EPUB_READER_FRAME_NAV_LABEL_CAP)
  {
    return (*environment)->NewStringUTF(environment, "");
  }
  return octavo_android_new_utf8_string(
    environment,
    str8((U8 *)item->label, item->label_length));
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_navigateToContents(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint nav_index)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || nav_index < 0)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (!octavo_android_navigation_can_begin(app))
  {
    if (app) { app->semantic_navigation_gate_block_count += 1u; }
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }

  EpubReaderNavigationEntry origin =
    octavo_android_presented_navigation_entry(
      app, EpubReaderNavigationReason_Section);
  EpubReaderNavPointResult navigation = {0};
  EpubReaderResult result = epub_reader_navigate_to_nav_point(
    &app->reader,
    (U32)nav_index,
    app->layout_key,
    app->layout_config,
    (EpubReaderNavigationOptions){.suppress_history = 1},
    &navigation);
  if (result != EpubReaderResult_Ok)
  {
    app->semantic_navigation_failure_count += 1u;
    return octavo_android_navigation_failure_after_restore(app, result);
  }
  return octavo_android_begin_direct_navigation(
    app,
    1,
    EpubReaderNavigationReason_Section,
    origin,
    navigation.navigation);
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_navigateToChapter(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jlong one_based_chapter)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || one_based_chapter <= 0 ||
      (uint64_t)one_based_chapter > UINT32_MAX)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (!octavo_android_navigation_can_begin(app))
  {
    if (app) { app->semantic_navigation_gate_block_count += 1u; }
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }

  EpubReaderNavigationEntry origin =
    octavo_android_presented_navigation_entry(
      app, EpubReaderNavigationReason_Section);
  EpubReaderChapterNavigationResult navigation = {0};
  EpubReaderResult result = epub_reader_navigate_to_chapter(
    &app->reader,
    (U32)one_based_chapter,
    app->layout_key,
    app->layout_config,
    (EpubReaderNavigationOptions){.suppress_history = 1},
    &navigation);
  if (result != EpubReaderResult_Ok)
  {
    app->semantic_navigation_failure_count += 1u;
    return octavo_android_navigation_failure_after_restore(app, result);
  }
  return octavo_android_begin_direct_navigation(
    app,
    1,
    EpubReaderNavigationReason_Section,
    origin,
    navigation.nav_point.navigation);
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_navigateToLocation(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jlong one_based_location)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || one_based_location <= 0)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (!octavo_android_navigation_can_begin(app))
  {
    if (app) { app->semantic_navigation_gate_block_count += 1u; }
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }

  U32 spine_index = 0;
  U64 byte_offset = 0;
  U64 location_count = 0;
  if (!epub_reader_location_target(
        &app->reader,
        (U64)one_based_location,
        &spine_index,
        &byte_offset,
        &location_count) ||
      (U64)one_based_location > location_count)
  {
    app->semantic_navigation_failure_count += 1u;
    return OCTAVO_ANDROID_NAVIGATION_UNAVAILABLE;
  }

  EpubReaderNavigationEntry origin =
    octavo_android_presented_navigation_entry(
      app, EpubReaderNavigationReason_Location);
  EpubReaderNavigationResult navigation = {0};
  EpubReaderResult result = epub_reader_navigate_to_location(
    &app->reader,
    spine_index,
    byte_offset,
    EpubReaderNavigationReason_Location,
    app->layout_key,
    app->layout_config,
    (EpubReaderNavigationOptions){.suppress_history = 1},
    &navigation);
  if (result != EpubReaderResult_Ok)
  {
    app->semantic_navigation_failure_count += 1u;
    return octavo_android_navigation_failure_after_restore(app, result);
  }
  return octavo_android_begin_direct_navigation(
    app,
    2,
    EpubReaderNavigationReason_Location,
    origin,
    navigation);
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_navigateToAnnotation(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jlong spine_index,
  jlong byte_offset)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || spine_index < 0 || (uint64_t)spine_index > UINT32_MAX ||
      byte_offset < 0)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (!octavo_android_navigation_can_begin(app))
  {
    if (app) { app->semantic_navigation_gate_block_count += 1u; }
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }

  EpubReaderNavigationEntry origin =
    octavo_android_presented_navigation_entry(
      app, EpubReaderNavigationReason_Bookmark);
  EpubReaderNavigationResult navigation = {0};
  EpubReaderResult result = epub_reader_navigate_to_location(
    &app->reader,
    (U32)spine_index,
    (U64)byte_offset,
    EpubReaderNavigationReason_Bookmark,
    app->layout_key,
    app->layout_config,
    (EpubReaderNavigationOptions){.suppress_history = 1},
    &navigation);
  if (result != EpubReaderResult_Ok)
  {
    app->semantic_navigation_failure_count += 1u;
    return octavo_android_navigation_failure_after_restore(app, result);
  }
  return octavo_android_begin_direct_navigation(
    app,
    OCTAVO_ANDROID_NAVIGATION_KIND_ANNOTATION,
    EpubReaderNavigationReason_Bookmark,
    origin,
    navigation);
}

JNIEXPORT jlongArray JNICALL
Java_ro_devze_octavo_OctavoNative_qualifySyncedPosition(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jlong spine_index,
  jlong byte_offset)
{
  (void)type;
  jlong values[OCTAVO_ANDROID_POSITION_QUALIFICATION_FIELD_COUNT] = {0};
  values[0] = OCTAVO_ANDROID_NAVIGATION_INVALID;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (app && spine_index >= 0 && (uint64_t)spine_index <= UINT32_MAX &&
      byte_offset >= 0)
  {
    if (!octavo_android_navigation_can_begin(app) ||
        app->reader.has_active_selection)
    {
      app->semantic_navigation_gate_block_count += 1u;
      values[0] = OCTAVO_ANDROID_NAVIGATION_BUSY;
    }
    else
    {
      EpubReaderNavigationResult navigation = {0};
      EpubReaderResult navigation_result =
        epub_reader_navigate_to_location(
          &app->reader,
          (U32)spine_index,
          (U64)byte_offset,
          EpubReaderNavigationReason_Location,
          app->layout_key,
          app->layout_config,
          (EpubReaderNavigationOptions){.suppress_history = 1},
          &navigation);
      B32 exact = navigation_result == EpubReaderResult_Ok &&
        navigation.target_spine_index == (U32)spine_index &&
        navigation.target_byte_offset == (U64)byte_offset &&
        app->reader.has_current_page &&
        app->reader.current_page.spine_index == (U32)spine_index &&
        app->reader.current_page.first_byte <= (U64)byte_offset &&
        app->reader.current_page.one_past_last_byte > (U64)byte_offset;
      EpubReaderLocationSummary location = {0};
      if (exact)
      {
        (void)epub_reader_location_summary_for_position(
          &app->reader, (U32)spine_index, (U64)byte_offset, &location);
      }

      if (!octavo_android_restore_presented_reader(app))
      {
        values[0] = OCTAVO_ANDROID_NAVIGATION_FAILED;
      }
      else if (!exact)
      {
        values[0] = navigation_result == EpubReaderResult_Ok ?
          OCTAVO_ANDROID_NAVIGATION_UNAVAILABLE :
          octavo_android_navigation_result(navigation_result);
      }
      else
      {
        values[0] = OCTAVO_ANDROID_NAVIGATION_ACCEPTED;
        values[1] = spine_index;
        values[2] = byte_offset;
        values[3] = location.available ? 1 : 0;
        values[4] = (jlong)location.location_index;
        values[5] = (jlong)location.location_count;
        values[6] = (jlong)location.percent;
      }
    }
  }

  jlongArray result = (*environment)->NewLongArray(
    environment, OCTAVO_ANDROID_POSITION_QUALIFICATION_FIELD_COUNT);
  if (result)
  {
    (*environment)->SetLongArrayRegion(
      environment,
      result,
      0,
      OCTAVO_ANDROID_POSITION_QUALIFICATION_FIELD_COUNT,
      values);
  }
  return result;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_navigateToSyncedPosition(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jlong spine_index,
  jlong byte_offset)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || spine_index < 0 || (uint64_t)spine_index > UINT32_MAX ||
      byte_offset < 0)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (!octavo_android_navigation_can_begin(app))
  {
    app->semantic_navigation_gate_block_count += 1u;
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }

  EpubReaderNavigationEntry origin =
    octavo_android_presented_navigation_entry(
      app, EpubReaderNavigationReason_Location);
  EpubReaderNavigationResult navigation = {0};
  EpubReaderResult result = epub_reader_navigate_to_location(
    &app->reader,
    (U32)spine_index,
    (U64)byte_offset,
    EpubReaderNavigationReason_Location,
    app->layout_key,
    app->layout_config,
    (EpubReaderNavigationOptions){.suppress_history = 1},
    &navigation);
  if (result != EpubReaderResult_Ok)
  {
    app->semantic_navigation_failure_count += 1u;
    return octavo_android_navigation_failure_after_restore(app, result);
  }
  if (navigation.target_spine_index != (U32)spine_index ||
      navigation.target_byte_offset != (U64)byte_offset ||
      !app->reader.has_current_page ||
      app->reader.current_page.spine_index != (U32)spine_index ||
      app->reader.current_page.first_byte > (U64)byte_offset ||
      app->reader.current_page.one_past_last_byte <= (U64)byte_offset)
  {
    app->semantic_navigation_failure_count += 1u;
    return octavo_android_navigation_failure_after_restore(
      app, EpubReaderResult_Boundary);
  }
  return octavo_android_begin_direct_navigation(
    app,
    OCTAVO_ANDROID_NAVIGATION_KIND_SYNCED_POSITION,
    EpubReaderNavigationReason_Location,
    origin,
    navigation);
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_navigateToPage(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jlong one_based_page)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || one_based_page <= 0)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (!octavo_android_navigation_can_begin(app))
  {
    if (app) { app->semantic_navigation_gate_block_count += 1u; }
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }

  EpubReaderNavigationEntry origin =
    octavo_android_presented_navigation_entry(
      app, EpubReaderNavigationReason_Page);
  EpubReaderPageNavigationResult navigation = {0};
  EpubReaderResult result =
    epub_reader_navigate_to_current_spine_page(
      &app->reader,
      (U64)one_based_page,
      app->layout_key,
      app->layout_config,
      (EpubReaderNavigationOptions){.suppress_history = 1},
      &navigation);
  if (result != EpubReaderResult_Ok)
  {
    app->semantic_navigation_failure_count += 1u;
    return octavo_android_navigation_failure_after_restore(app, result);
  }
  return octavo_android_begin_direct_navigation(
    app,
    3,
    EpubReaderNavigationReason_Page,
    origin,
    navigation.navigation);
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_navigateToPercent(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint percent)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || percent < 0 || percent > 100)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (!octavo_android_navigation_can_begin(app))
  {
    if (app) { app->semantic_navigation_gate_block_count += 1u; }
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }

  EpubReaderNavigationEntry origin =
    octavo_android_presented_navigation_entry(
      app, EpubReaderNavigationReason_Location);
  EpubReaderPercentageNavigationResult navigation = {0};
  EpubReaderResult result = epub_reader_navigate_to_percentage(
    &app->reader,
    (U32)percent,
    app->layout_key,
    app->layout_config,
    (EpubReaderNavigationOptions){.suppress_history = 1},
    &navigation);
  if (result != EpubReaderResult_Ok)
  {
    app->semantic_navigation_failure_count += 1u;
    return octavo_android_navigation_failure_after_restore(app, result);
  }
  return octavo_android_begin_direct_navigation(
    app,
    4,
    EpubReaderNavigationReason_Location,
    origin,
    navigation.navigation);
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_moveHistory(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jboolean forward)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (!octavo_android_navigation_can_begin(app))
  {
    app->semantic_navigation_gate_block_count += 1u;
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }

  B32 move_forward = forward ? 1 : 0;
  EpubReaderNavigationEntry current = {0};
  EpubReaderNavigationEntry target = {0};
  if (!epub_reader_history_begin(
        &app->reader, move_forward, &current, &target))
  {
    return OCTAVO_ANDROID_NAVIGATION_UNAVAILABLE;
  }
  current = octavo_android_presented_navigation_entry(
    app,
    target.reason == EpubReaderNavigationReason_None ?
      EpubReaderNavigationReason_Location : target.reason);

  EpubReaderNavigationResult navigation = {0};
  EpubReaderResult result = epub_reader_navigate_to_location(
    &app->reader,
    target.spine_index,
    target.byte_offset,
    EpubReaderNavigationReason_Location,
    app->layout_key,
    app->layout_config,
    (EpubReaderNavigationOptions){.suppress_history = 1},
    &navigation);
  if (result != EpubReaderResult_Ok)
  {
    B32 restored = octavo_android_restore_presented_reader(app);
    epub_reader_history_finish(
      &app->reader, move_forward, current, target, 0);
    app->semantic_navigation_failure_count += 1u;
    if (!restored)
    {
      __android_log_print(
        ANDROID_LOG_ERROR,
        "8vo",
        "Unable to restore the presented reader after a failed history move");
      return OCTAVO_ANDROID_NAVIGATION_FAILED;
    }
    return octavo_android_navigation_result(result);
  }
  if (!octavo_android_begin_navigation_transaction(
        app,
        move_forward ? 6 : 5,
        target.reason,
        current,
        navigation.target_spine_index,
        navigation.target_byte_offset,
        1,
        move_forward,
        current,
        target))
  {
    B32 restored = octavo_android_restore_presented_reader(app);
    epub_reader_history_finish(
      &app->reader, move_forward, current, target, 0);
    app->semantic_navigation_failure_count += 1u;
    if (!restored)
    {
      __android_log_print(
        ANDROID_LOG_ERROR,
        "8vo",
        "Unable to restore the presented reader after a rejected history move");
    }
    return OCTAVO_ANDROID_NAVIGATION_FAILED;
  }
  return OCTAVO_ANDROID_NAVIGATION_ACCEPTED;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_setProgressDisplayMode(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint mode)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || mode < OCTAVO_ANDROID_PROGRESS_CHAPTER ||
      mode > OCTAVO_ANDROID_PROGRESS_PERCENTAGE)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (app->progress_display_mode == mode &&
      !app->progress_display_waiting_for_present)
  {
    return OCTAVO_ANDROID_NAVIGATION_ALREADY_PRESENTED;
  }
  if (app->frame_count == 0)
  {
    octavo_android_invalidate_prepared_static_frame(app);
    app->progress_display_mode = mode;
    app->progress_display_presented_mode = mode;
    app->progress_display_generation += 1u;
    app->progress_display_presented_generation =
      app->progress_display_generation;
    return OCTAVO_ANDROID_NAVIGATION_ALREADY_PRESENTED;
  }
  if (!octavo_android_navigation_can_begin(app))
  {
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }
  octavo_android_invalidate_prepared_static_frame(app);
  app->progress_display_mode = mode;
  app->progress_display_generation += 1u;
  app->progress_display_waiting_for_present = 1;
  return OCTAVO_ANDROID_NAVIGATION_ACCEPTED;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_cancelPendingNavigation(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)environment;
  (void)type;
  return octavo_android_cancel_pending_navigation(
    octavo_android_from_handle(handle));
}

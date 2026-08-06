/*
 * Android Port 9 bounded in-book search adapter. Reader0 owns all matching,
 * snippets, locations, and navigation. This host publishes a validated JNI
 * snapshot and stages visible changes through Android presentation.
 */

enum
{
  OCTAVO_ANDROID_SEARCH_PACKET_VERSION = 1,
  OCTAVO_ANDROID_SEARCH_HEADER_COUNT = 10,
  OCTAVO_ANDROID_SEARCH_ROW_STRIDE = 5,
  OCTAVO_ANDROID_SEARCH_ROW_CAP = EPUB_READER_FRAME_NAV_ITEM_CAP,

  OCTAVO_ANDROID_SEARCH_FLAG_READY = 1 << 0,
  OCTAVO_ANDROID_SEARCH_FLAG_TRUNCATED = 1 << 1,
  OCTAVO_ANDROID_SEARCH_FLAG_PENDING = 1 << 2,
};

static void
octavo_android_search_state_capture(const EpubReader *reader,
                                    OctavoAndroidSearchState *state)
{
  if (!reader || !state) { return; }
  memset(state, 0, sizeof(*state));
  memcpy(state->query,
         reader->search_query_storage,
         sizeof(state->query));
  state->query_size = reader->search_query_size;
  memcpy(state->matches,
         reader->search_matches,
         sizeof(state->matches));
  state->match_count = reader->search_match_count;
  state->total_count = reader->search_total_count;
  state->has_more = reader->search_has_more;
  state->active_index = reader->search_active_index;
  state->has_active = reader->search_has_active;
}

static void
octavo_android_search_state_restore(EpubReader *reader,
                                    const OctavoAndroidSearchState *state)
{
  if (!reader || !state) { return; }
  memcpy(reader->search_query_storage,
         state->query,
         sizeof(state->query));
  reader->search_query_size = state->query_size;
  memcpy(reader->search_matches,
         state->matches,
         sizeof(state->matches));
  reader->search_match_count = state->match_count;
  reader->search_total_count = state->total_count;
  reader->search_has_more = state->has_more;
  reader->search_active_index = state->active_index;
  reader->search_has_active = state->has_active;
}

static B32
octavo_android_publish_search_change(OctavoAndroidApp *app)
{
  if (!app) { return 0; }
  octavo_android_invalidate_prepared_static_frame(app);
  if (!epub_reader_build_frame(
        &app->reader, &app->reader_frame_storage, &app->reader_frame) ||
      !app->reader_frame.ready || !app->reader_frame.document_open)
  {
    return 0;
  }
  app->host_frame_waiting_for_present = 1;
  return 1;
}

static B32
octavo_android_abort_search_mutation(OctavoAndroidApp *app)
{
  if (!app) { return 0; }
  if (!app->search_mutation_waiting_for_present) { return 1; }
  octavo_android_search_state_restore(
    &app->reader, &app->search_mutation_previous);
  octavo_android_invalidate_prepared_static_frame(app);
  if (!epub_reader_build_frame(
        &app->reader, &app->reader_frame_storage, &app->reader_frame) ||
      !app->reader_frame.ready || !app->reader_frame.document_open)
  {
    return 0;
  }
  app->search_mutation_waiting_for_present = 0;
  app->search_mutation_previous =
    (OctavoAndroidSearchState){0};
  app->host_frame_waiting_for_present = 1;
  return 1;
}

static const EpubReaderFrameSectionItem *
octavo_android_search_section_for_item(
  OctavoAndroidApp *app,
  const EpubReaderFrameSearchItem *item)
{
  if (!app || !item) { return 0; }
  EpubReaderCurrentNavPointSummary summary = {0};
  if (!epub_reader_current_nav_point_summary_for_position(
        &app->reader,
        item->spine_index,
        item->start_byte,
        &summary) ||
      !summary.available)
  {
    return 0;
  }
  return octavo_android_section_item_for_nav_index(
    app, summary.nav_index);
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_commitSearch(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jbyteArray query_bytes)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !query_bytes)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (!octavo_android_navigation_can_begin(app))
  {
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }
  jsize size = (*environment)->GetArrayLength(environment, query_bytes);
  if (size <= 0 || size >= EPUB_READER_SEARCH_QUERY_CAP)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  U8 query[EPUB_READER_SEARCH_QUERY_CAP] = {0};
  (*environment)->GetByteArrayRegion(
    environment, query_bytes, 0, size, (jbyte *)query);
  if ((*environment)->ExceptionCheck(environment))
  {
    (*environment)->ExceptionClear(environment);
    return OCTAVO_ANDROID_NAVIGATION_FAILED;
  }
  for (jsize index = 0; index < size; ++index)
  {
    if (query[index] == 0)
    {
      return OCTAVO_ANDROID_NAVIGATION_INVALID;
    }
  }

  octavo_android_search_state_capture(
    &app->reader, &app->search_mutation_previous);
  if (!epub_reader_rebuild_search(
        &app->reader, str8(query, (U64)size)) ||
      !octavo_android_publish_search_change(app))
  {
    octavo_android_search_state_restore(
      &app->reader, &app->search_mutation_previous);
    app->search_mutation_previous =
      (OctavoAndroidSearchState){0};
    (void)epub_reader_build_frame(
      &app->reader, &app->reader_frame_storage, &app->reader_frame);
    return OCTAVO_ANDROID_NAVIGATION_FAILED;
  }
  app->search_mutation_generation += 1u;
  app->search_mutation_waiting_for_present = 1;
  return OCTAVO_ANDROID_NAVIGATION_ACCEPTED;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_clearSearch(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app) { return OCTAVO_ANDROID_NAVIGATION_INVALID; }
  if (!octavo_android_navigation_can_begin(app))
  {
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }
  octavo_android_search_state_capture(
    &app->reader, &app->search_mutation_previous);
  epub_reader_clear_search(&app->reader);
  if (!octavo_android_publish_search_change(app))
  {
    octavo_android_search_state_restore(
      &app->reader, &app->search_mutation_previous);
    app->search_mutation_previous =
      (OctavoAndroidSearchState){0};
    (void)epub_reader_build_frame(
      &app->reader, &app->reader_frame_storage, &app->reader_frame);
    return OCTAVO_ANDROID_NAVIGATION_FAILED;
  }
  app->search_mutation_generation += 1u;
  app->search_mutation_waiting_for_present = 1;
  return OCTAVO_ANDROID_NAVIGATION_ACCEPTED;
}

JNIEXPORT jlongArray JNICALL
Java_ro_devze_octavo_OctavoNative_searchSnapshot(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  U32 row_count = app && app->reader_frame.ready ?
    MIN(app->reader_frame.search_item_count,
        (U32)OCTAVO_ANDROID_SEARCH_ROW_CAP) : 0;
  jsize value_count = OCTAVO_ANDROID_SEARCH_HEADER_COUNT +
    (jsize)row_count * OCTAVO_ANDROID_SEARCH_ROW_STRIDE;
  jlong values[
    OCTAVO_ANDROID_SEARCH_HEADER_COUNT +
    OCTAVO_ANDROID_SEARCH_ROW_CAP *
      OCTAVO_ANDROID_SEARCH_ROW_STRIDE] = {0};
  values[0] = OCTAVO_ANDROID_SEARCH_PACKET_VERSION;
  values[1] = OCTAVO_ANDROID_SEARCH_HEADER_COUNT;
  values[2] = OCTAVO_ANDROID_SEARCH_ROW_STRIDE;
  values[4] = row_count;
  values[6] = -1;

  if (app && app->reader_frame.ready &&
      app->reader_frame.document_open && app->presented_anchor_valid)
  {
    values[3] |= OCTAVO_ANDROID_SEARCH_FLAG_READY;
    if (app->reader_frame.search_has_more ||
        app->reader_frame.search_total_count > row_count)
    {
      values[3] |= OCTAVO_ANDROID_SEARCH_FLAG_TRUNCATED;
    }
    if (octavo_android_presentation_pending(app))
    {
      values[3] |= OCTAVO_ANDROID_SEARCH_FLAG_PENDING;
    }
    values[5] = app->reader_frame.search_total_count;
    if (app->reader_frame.search_has_active &&
        app->reader_frame.search_active_index < row_count)
    {
      values[6] = app->reader_frame.search_active_index;
    }
    values[7] = (jlong)app->reader_frame.generation;
    values[8] = (jlong)app->reader_frame.search_query.size;
    values[9] = app->reader_frame.search_has_more ? 1 : 0;
    for (U32 index = 0; index < row_count; ++index)
    {
      const EpubReaderFrameSearchItem *item =
        app->reader_frame.search_items + index;
      S32 at = OCTAVO_ANDROID_SEARCH_HEADER_COUNT +
        (S32)index * OCTAVO_ANDROID_SEARCH_ROW_STRIDE;
      values[at + 0] = item->spine_index;
      values[at + 1] = (jlong)item->start_byte;
      values[at + 2] = (jlong)item->end_byte;
      values[at + 3] = item->match_start_in_snippet;
      values[at + 4] = item->match_size_in_snippet;
    }
  }

  jlongArray result = (*environment)->NewLongArray(
    environment, value_count);
  if (result)
  {
    (*environment)->SetLongArrayRegion(
      environment, result, 0, value_count, values);
  }
  return result;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_searchQuery(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->reader_frame.ready)
  {
    return (*environment)->NewStringUTF(environment, "");
  }
  return octavo_android_new_utf8_string(
    environment, app->reader_frame.search_query);
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_searchMutationPending(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  return app && app->search_mutation_waiting_for_present ?
    JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_searchSection(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint row_index)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->reader_frame.ready || row_index < 0 ||
      (U32)row_index >= app->reader_frame.search_item_count)
  {
    return (*environment)->NewStringUTF(environment, "");
  }
  const EpubReaderFrameSearchItem *item =
    app->reader_frame.search_items + row_index;
  const EpubReaderFrameSectionItem *section =
    octavo_android_search_section_for_item(app, item);
  if (section && section->label_length < EPUB_READER_FRAME_NAV_LABEL_CAP)
  {
    return octavo_android_new_utf8_string(
      environment,
      str8((U8 *)section->label, section->label_length));
  }
  if (item->section_label_length >= EPUB_READER_FRAME_NAV_LABEL_CAP)
  {
    return (*environment)->NewStringUTF(environment, "");
  }
  return octavo_android_new_utf8_string(
    environment,
    str8((U8 *)item->section_label, item->section_label_length));
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_searchSnippet(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint row_index)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->reader_frame.ready || row_index < 0 ||
      (U32)row_index >= app->reader_frame.search_item_count)
  {
    return (*environment)->NewStringUTF(environment, "");
  }
  const EpubReaderFrameSearchItem *item =
    app->reader_frame.search_items + row_index;
  if (item->snippet_length >= EPUB_READER_FRAME_NAV_DETAIL_CAP)
  {
    return (*environment)->NewStringUTF(environment, "");
  }
  return octavo_android_new_utf8_string(
    environment,
    str8((U8 *)item->snippet, item->snippet_length));
}

static S32
octavo_android_navigate_to_search_result(OctavoAndroidApp *app,
                                         U32 match_index)
{
  if (!app || match_index >= app->reader.search_match_count)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (!octavo_android_navigation_can_begin(app))
  {
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }
  U32 previous_index = app->reader.search_active_index;
  B32 previous_has_active = app->reader.search_has_active;
  EpubReaderNavigationEntry origin =
    octavo_android_presented_navigation_entry(
      app, EpubReaderNavigationReason_Search);
  EpubReaderSearchNavigationResult navigation = {0};
  EpubReaderResult result = epub_reader_navigate_to_search_match(
    &app->reader,
    match_index,
    app->layout_key,
    app->layout_config,
    (EpubReaderNavigationOptions){.suppress_history = 1},
    &navigation);
  if (result != EpubReaderResult_Ok)
  {
    app->reader.search_active_index = previous_index;
    app->reader.search_has_active = previous_has_active;
    app->semantic_navigation_failure_count += 1u;
    return octavo_android_navigation_failure_after_restore(app, result);
  }
  S32 begin = octavo_android_begin_direct_navigation(
    app,
    OCTAVO_ANDROID_NAVIGATION_KIND_SEARCH,
    EpubReaderNavigationReason_Search,
    origin,
    navigation.navigation);
  if (begin != OCTAVO_ANDROID_NAVIGATION_ACCEPTED)
  {
    app->reader.search_active_index = previous_index;
    app->reader.search_has_active = previous_has_active;
    (void)octavo_android_restore_presented_reader(app);
    return begin;
  }
  app->search_navigation_previous_active_index = previous_index;
  app->search_navigation_previous_has_active = previous_has_active;
  return begin;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_navigateToSearchResult(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint match_index)
{
  (void)environment;
  (void)type;
  if (match_index < 0)
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  return octavo_android_navigate_to_search_result(
    octavo_android_from_handle(handle), (U32)match_index);
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_moveSearchResult(
  JNIEnv *environment,
  jclass type,
  jlong handle,
  jint direction)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || (direction != -1 && direction != 1))
  {
    return OCTAVO_ANDROID_NAVIGATION_INVALID;
  }
  if (!octavo_android_navigation_can_begin(app))
  {
    return OCTAVO_ANDROID_NAVIGATION_BUSY;
  }
  U32 previous_index = app->reader.search_active_index;
  B32 previous_has_active = app->reader.search_has_active;
  if (!epub_reader_search_step(&app->reader, direction))
  {
    return OCTAVO_ANDROID_NAVIGATION_UNAVAILABLE;
  }
  U32 target = app->reader.search_active_index;
  app->reader.search_active_index = previous_index;
  app->reader.search_has_active = previous_has_active;
  return octavo_android_navigate_to_search_result(app, target);
}

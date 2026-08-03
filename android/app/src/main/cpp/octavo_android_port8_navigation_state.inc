JNIEXPORT jlongArray JNICALL
Java_ro_devze_octavo_OctavoNative_navigationState(
  JNIEnv *environment,
  jclass type,
  jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return 0;
  }
  jlong values[10] = {
    1,
    (app->semantic_navigation_waiting_for_present ||
     app->progress_display_waiting_for_present) ? 1 : 0,
    (jlong)app->semantic_navigation_generation,
    (jlong)app->semantic_navigation_presented_generation,
    (jlong)app->presented_history_back_count,
    (jlong)app->presented_history_forward_count,
    (jlong)app->progress_display_generation,
    (jlong)app->progress_display_presented_generation,
    app->progress_display_mode,
    app->progress_display_presented_mode,
  };
  jlongArray result = (*environment)->NewLongArray(environment, 10);
  if (result)
  {
    (*environment)->SetLongArrayRegion(environment, result, 0, 10, values);
  }
  return result;
}

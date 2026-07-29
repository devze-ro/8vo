#include "octavo_version.h"

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define OCTAVO_ANDROID_PATH_CAPACITY 4096u
#define OCTAVO_ANDROID_STATE_FIELD_COUNT 12

enum
{
  OCTAVO_CLEAR_RED = 24,
  OCTAVO_CLEAR_GREEN = 22,
  OCTAVO_CLEAR_BLUE = 20,
  OCTAVO_CLEAR_ALPHA = 255
};

typedef struct OctavoAndroidApp
{
  ANativeWindow *window;
  char files_path[OCTAVO_ANDROID_PATH_CAPACITY];
  char cache_path[OCTAVO_ANDROID_PATH_CAPACITY];
  int32_t format;
  int32_t width;
  int32_t height;
  int resumed;
  uint64_t surface_generation;
  uint64_t surface_destroy_count;
  uint64_t resume_count;
  uint64_t pause_count;
  uint64_t frame_count;
  uint64_t render_failure_count;
  uint64_t touch_count;
  uint64_t lifecycle_generation;
} OctavoAndroidApp;

static OctavoAndroidApp *
octavo_android_from_handle(jlong handle)
{
  return (OctavoAndroidApp *)(uintptr_t)handle;
}

static int
octavo_android_copy_path(JNIEnv *environment,
                         jstring source,
                         char *destination,
                         size_t destination_capacity)
{
  if (!source || !destination || destination_capacity == 0u)
  {
    return 0;
  }

  const char *source_utf8 =
    (*environment)->GetStringUTFChars(environment, source, 0);
  if (!source_utf8)
  {
    return 0;
  }

  size_t source_length = strlen(source_utf8);
  int copied = source_length > 0u && source_length < destination_capacity;
  if (copied)
  {
    memcpy(destination, source_utf8, source_length + 1u);
  }
  (*environment)->ReleaseStringUTFChars(environment, source, source_utf8);
  return copied;
}

static int
octavo_android_present_frame(OctavoAndroidApp *app)
{
  if (!app || !app->window || !app->resumed)
  {
    return 0;
  }

  if (ANativeWindow_setBuffersGeometry(app->window,
                                       0,
                                       0,
                                       WINDOW_FORMAT_RGBA_8888) != 0)
  {
    app->render_failure_count += 1u;
    __android_log_print(ANDROID_LOG_ERROR,
                        "8vo",
                        "Unable to configure the Android frame buffer");
    return 0;
  }

  ANativeWindow_Buffer buffer;
  memset(&buffer, 0, sizeof(buffer));
  if (ANativeWindow_lock(app->window, &buffer, 0) != 0)
  {
    app->render_failure_count += 1u;
    __android_log_print(ANDROID_LOG_ERROR,
                        "8vo",
                        "Unable to lock the Android frame buffer");
    return 0;
  }

  if (!buffer.bits || buffer.width <= 0 || buffer.height <= 0 ||
      buffer.stride < buffer.width ||
      buffer.format != WINDOW_FORMAT_RGBA_8888)
  {
    app->render_failure_count += 1u;
    (void)ANativeWindow_unlockAndPost(app->window);
    __android_log_print(ANDROID_LOG_ERROR,
                        "8vo",
                        "Unexpected Android frame buffer geometry or format");
    return 0;
  }

  for (int32_t y = 0; y < buffer.height; ++y)
  {
    uint8_t *row = (uint8_t *)buffer.bits +
                   (size_t)y * (size_t)buffer.stride * 4u;
    for (int32_t x = 0; x < buffer.width; ++x)
    {
      row[(size_t)x * 4u + 0u] = OCTAVO_CLEAR_RED;
      row[(size_t)x * 4u + 1u] = OCTAVO_CLEAR_GREEN;
      row[(size_t)x * 4u + 2u] = OCTAVO_CLEAR_BLUE;
      row[(size_t)x * 4u + 3u] = OCTAVO_CLEAR_ALPHA;
    }
  }

  if (ANativeWindow_unlockAndPost(app->window) != 0)
  {
    app->render_failure_count += 1u;
    __android_log_print(ANDROID_LOG_ERROR,
                        "8vo",
                        "Unable to present the Android frame buffer");
    return 0;
  }

  app->format = buffer.format;
  app->width = buffer.width;
  app->height = buffer.height;
  app->frame_count += 1u;
  __android_log_print(ANDROID_LOG_INFO,
                      "8vo",
                      "Android Port 1 frame=%llu surface=%llu size=%dx%d",
                      (unsigned long long)app->frame_count,
                      (unsigned long long)app->surface_generation,
                      app->width,
                      app->height);
  return 1;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_version(JNIEnv *environment, jclass type)
{
  (void)type;
  return (*environment)->NewStringUTF(environment, OCTAVO_VERSION_STRING);
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_platform(JNIEnv *environment, jclass type)
{
  (void)type;
  return (*environment)->NewStringUTF(environment, "android");
}

JNIEXPORT jlong JNICALL
Java_ro_devze_octavo_OctavoNative_create(JNIEnv *environment,
                                          jclass type,
                                          jstring files_path,
                                          jstring cache_path)
{
  (void)type;
  OctavoAndroidApp *app = (OctavoAndroidApp *)calloc(1, sizeof(*app));
  if (!app)
  {
    return 0;
  }

  if (!octavo_android_copy_path(environment,
                                files_path,
                                app->files_path,
                                sizeof(app->files_path)) ||
      !octavo_android_copy_path(environment,
                                cache_path,
                                app->cache_path,
                                sizeof(app->cache_path)))
  {
    __android_log_print(ANDROID_LOG_ERROR,
                        "8vo",
                        "Android application-data paths are empty or too long");
    free(app);
    return 0;
  }

  __android_log_print(ANDROID_LOG_INFO,
                      "8vo",
                      "Android Port 1 state created files=%s cache=%s",
                      app->files_path,
                      app->cache_path);
  return (jlong)(uintptr_t)app;
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_destroy(JNIEnv *environment,
                                           jclass type,
                                           jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return;
  }
  if (app->window)
  {
    ANativeWindow_release(app->window);
    app->window = 0;
  }
  free(app);
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_hostResumed(JNIEnv *environment,
                                               jclass type,
                                               jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || app->resumed)
  {
    return;
  }
  app->resumed = 1;
  app->resume_count += 1u;
  app->lifecycle_generation += 1u;
  (void)octavo_android_present_frame(app);
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_hostPaused(JNIEnv *environment,
                                              jclass type,
                                              jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !app->resumed)
  {
    return;
  }
  app->resumed = 0;
  app->pause_count += 1u;
  app->lifecycle_generation += 1u;
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_surfaceCreated(JNIEnv *environment,
                                                  jclass type,
                                                  jlong handle,
                                                  jobject surface)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app || !surface)
  {
    return;
  }

  ANativeWindow *window = ANativeWindow_fromSurface(environment, surface);
  if (!window)
  {
    return;
  }
  if (app->window)
  {
    ANativeWindow_release(app->window);
  }
  app->window = window;
  app->width = ANativeWindow_getWidth(window);
  app->height = ANativeWindow_getHeight(window);
  app->format = ANativeWindow_getFormat(window);
  app->surface_generation += 1u;

  __android_log_print(ANDROID_LOG_INFO,
                      "8vo",
                      "Android surface generation=%llu size=%dx%d",
                      (unsigned long long)app->surface_generation,
                      app->width,
                      app->height);
  (void)octavo_android_present_frame(app);
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_surfaceChanged(JNIEnv *environment,
                                                  jclass type,
                                                  jlong handle,
                                                  jint format,
                                                  jint width,
                                                  jint height)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return;
  }
  app->format = (int32_t)format;
  app->width = (int32_t)width;
  app->height = (int32_t)height;
  (void)octavo_android_present_frame(app);
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_surfaceDestroyed(JNIEnv *environment,
                                                    jclass type,
                                                    jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return;
  }
  if (app->window)
  {
    ANativeWindow_release(app->window);
    app->window = 0;
  }
  app->format = 0;
  app->width = 0;
  app->height = 0;
  app->surface_destroy_count += 1u;
}

JNIEXPORT jlongArray JNICALL
Java_ro_devze_octavo_OctavoNative_state(JNIEnv *environment,
                                         jclass type,
                                         jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return 0;
  }

  jlong values[OCTAVO_ANDROID_STATE_FIELD_COUNT];
  values[0] = app->resumed ? 1 : 0;
  values[1] = app->window ? 1 : 0;
  values[2] = app->width;
  values[3] = app->height;
  values[4] = (jlong)app->surface_generation;
  values[5] = (jlong)app->surface_destroy_count;
  values[6] = (jlong)app->resume_count;
  values[7] = (jlong)app->pause_count;
  values[8] = (jlong)app->frame_count;
  values[9] = (jlong)app->render_failure_count;
  values[10] = (jlong)app->touch_count;
  values[11] = (jlong)app->lifecycle_generation;

  jlongArray result =
    (*environment)->NewLongArray(environment, OCTAVO_ANDROID_STATE_FIELD_COUNT);
  if (!result)
  {
    return 0;
  }
  (*environment)->SetLongArrayRegion(environment,
                                     result,
                                     0,
                                     OCTAVO_ANDROID_STATE_FIELD_COUNT,
                                     values);
  return result;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_filesPath(JNIEnv *environment,
                                             jclass type,
                                             jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  return app ? (*environment)->NewStringUTF(environment, app->files_path) : 0;
}

JNIEXPORT jstring JNICALL
Java_ro_devze_octavo_OctavoNative_cachePath(JNIEnv *environment,
                                             jclass type,
                                             jlong handle)
{
  (void)type;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  return app ? (*environment)->NewStringUTF(environment, app->cache_path) : 0;
}

JNIEXPORT jint JNICALL
Java_ro_devze_octavo_OctavoNative_clearColorArgb(JNIEnv *environment,
                                                  jclass type)
{
  (void)environment;
  (void)type;
  uint32_t color = ((uint32_t)OCTAVO_CLEAR_ALPHA << 24u) |
                   ((uint32_t)OCTAVO_CLEAR_RED << 16u) |
                   ((uint32_t)OCTAVO_CLEAR_GREEN << 8u) |
                   (uint32_t)OCTAVO_CLEAR_BLUE;
  return (jint)color;
}

JNIEXPORT jboolean JNICALL
Java_ro_devze_octavo_OctavoNative_touch(JNIEnv *environment,
                                        jclass type,
                                        jlong handle,
                                        jint action,
                                        jfloat x,
                                        jfloat y,
                                        jlong event_time_millis)
{
  (void)environment;
  (void)type;
  (void)action;
  (void)x;
  (void)y;
  (void)event_time_millis;
  OctavoAndroidApp *app = octavo_android_from_handle(handle);
  if (!app)
  {
    return JNI_FALSE;
  }
  app->touch_count += 1u;
  return JNI_TRUE;
}

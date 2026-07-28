#include "octavo_version.h"

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

typedef struct OctavoAndroidBootstrap
{
  ANativeWindow *window;
  int32_t format;
  int32_t width;
  int32_t height;
  uint64_t surface_generation;
  uint64_t touch_count;
} OctavoAndroidBootstrap;

static OctavoAndroidBootstrap *
octavo_android_from_handle(jlong handle)
{
  return (OctavoAndroidBootstrap *)(uintptr_t)handle;
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
Java_ro_devze_octavo_OctavoNative_create(JNIEnv *environment, jclass type)
{
  (void)environment;
  (void)type;
  OctavoAndroidBootstrap *bootstrap =
    (OctavoAndroidBootstrap *)calloc(1, sizeof(*bootstrap));
  return (jlong)(uintptr_t)bootstrap;
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_destroy(JNIEnv *environment,
                                           jclass type,
                                           jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidBootstrap *bootstrap = octavo_android_from_handle(handle);
  if (!bootstrap)
  {
    return;
  }
  if (bootstrap->window)
  {
    ANativeWindow_release(bootstrap->window);
    bootstrap->window = 0;
  }
  free(bootstrap);
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_surfaceCreated(JNIEnv *environment,
                                                  jclass type,
                                                  jlong handle,
                                                  jobject surface)
{
  (void)type;
  OctavoAndroidBootstrap *bootstrap = octavo_android_from_handle(handle);
  if (!bootstrap || !surface)
  {
    return;
  }

  ANativeWindow *window = ANativeWindow_fromSurface(environment, surface);
  if (!window)
  {
    return;
  }
  if (bootstrap->window)
  {
    ANativeWindow_release(bootstrap->window);
  }
  bootstrap->window = window;
  bootstrap->width = ANativeWindow_getWidth(window);
  bootstrap->height = ANativeWindow_getHeight(window);
  bootstrap->format = ANativeWindow_getFormat(window);
  bootstrap->surface_generation += 1;

  __android_log_print(ANDROID_LOG_INFO,
                      "8vo",
                      "Android bootstrap surface generation=%llu size=%dx%d",
                      (unsigned long long)bootstrap->surface_generation,
                      bootstrap->width,
                      bootstrap->height);
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
  OctavoAndroidBootstrap *bootstrap = octavo_android_from_handle(handle);
  if (!bootstrap)
  {
    return;
  }
  bootstrap->format = (int32_t)format;
  bootstrap->width = (int32_t)width;
  bootstrap->height = (int32_t)height;
}

JNIEXPORT void JNICALL
Java_ro_devze_octavo_OctavoNative_surfaceDestroyed(JNIEnv *environment,
                                                    jclass type,
                                                    jlong handle)
{
  (void)environment;
  (void)type;
  OctavoAndroidBootstrap *bootstrap = octavo_android_from_handle(handle);
  if (!bootstrap)
  {
    return;
  }
  if (bootstrap->window)
  {
    ANativeWindow_release(bootstrap->window);
    bootstrap->window = 0;
  }
  bootstrap->format = 0;
  bootstrap->width = 0;
  bootstrap->height = 0;
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
  OctavoAndroidBootstrap *bootstrap = octavo_android_from_handle(handle);
  if (!bootstrap)
  {
    return JNI_FALSE;
  }
  bootstrap->touch_count += 1;
  return JNI_TRUE;
}

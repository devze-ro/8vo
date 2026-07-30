/*
 * Android Port 5 application unity build.
 *
 * Each public source package is compiled exactly once through its stable
 * unity source. Ground0 modules remain explicit application dependencies,
 * matching the existing Windows host and Reader0 Android validation target.
 */
#ifndef _LARGEFILE64_SOURCE
#  define _LARGEFILE64_SOURCE 1
#endif

#include "base/base_core.h"
#include "os/os_core.c"
#include "os/os_file.c"
#include "base/base_arena.c"
#include "base/base_strings.c"
#include "base/base_unicode.c"
#include "base/base_text_edit.c"
#include "base/base_text_history.c"
#include "base/base_format.c"
#include "base/base_hash.c"
#include "base/base_text_index.c"
#include "base/base_text_layout.c"
#include "base/base_thread_context.c"
#include "os/os_time.c"
#include "font_provider/font_provider.c"
#include "font_cache/font_cache.h"
#include "draw/draw.c"

#include "reader0.c"
#include "ui0.c"
#include "readerview0.c"

#if OS_ANDROID
#  include "platform/android/os_core_android.c"
#  include "platform/android/os_file_android.c"
#  include "platform/android/os_time_android.c"
#  include "octavo_android_jni.c"
#else
#  error "octavo_android_port5_build.c requires the Android NDK toolchain"
#endif

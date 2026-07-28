# 8vo Android

This directory is the Android application root. Port 0 intentionally keeps the
host small:

- `OctavoActivity` owns Android lifecycle.
- `OctavoSurfaceView` translates surface and touch events.
- `OctavoNative` is the explicit Java/JNI boundary.
- `octavo_android_jni.c` owns one native bootstrap per view and acquires and
  releases its `ANativeWindow`.

There is no process-global mutable application state. The Java view holds the
native handle and destroys it when the activity is destroyed.

## Prerequisites

- JDK 17
- Android SDK Platform 36 and Build Tools 36
- Android SDK Platform Tools and Emulator
- Android NDK `29.0.14206865`
- CMake `3.31.6`
- an API 26 or newer emulator/device

Android Studio can install the SDK components. The project pins Gradle 9.5.0
and Android Gradle Plugin 9.3.0.

## Build

Bootstrap the exact first-party revisions from the repository root before
invoking Gradle:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bootstrap_dependencies.ps1
cd android
.\gradlew.bat :app:assembleDebug
```

Install the debug application on the selected emulator or USB device:

```powershell
.\gradlew.bat :app:installDebug
```

## Instrumentation smoke

With an API 26 or newer emulator/device connected:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

The smoke verifies that the native library loads, reports the expected port
version and platform, launches and recreates the activity, and creates the
surface host on each lifecycle.

Port 0 does not compile the full 8vo application core or render a reader frame.
See [`../docs/android_port0.md`](../docs/android_port0.md) for the milestone
boundary and coordinated Ground0 and Reader0 checks.

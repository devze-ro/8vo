# 8vo Android

This directory is the Android application root. Port 1 keeps the host small and
explicit:

- `OctavoActivity` owns Android lifecycle.
- `OctavoSurfaceView` translates lifecycle, surface, and touch events.
- `OctavoNative` is the explicit Java/JNI boundary.
- `octavo_android_jni.c` owns one native application state per view, copies the
  app-private files/cache paths into bounded storage, and acquires and releases
  its `ANativeWindow`.
- The native state presents a deterministic opaque `#181614` RGBA frame when a
  resumed host has a usable surface.

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

The smoke verifies native loading and version/platform identity, the presented
native pixel, private files/cache paths, pause/resume state, surface replacement,
and Activity recreation. It rejects any recorded native presentation failure.

Port 1 does not yet compile the full 8vo application core or render a reader
page. See [`../docs/android_port1.md`](../docs/android_port1.md) for the current
milestone boundary and [`../docs/android_port0.md`](../docs/android_port0.md)
for the Android foundation and coordinated Ground0/Reader0 checks.

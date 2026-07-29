# 8vo Android

This directory is the Android application root. Port 3 keeps the host small
and explicit while adding bounded interaction to the real shared reader stack:

- `OctavoActivity` owns Android lifecycle.
- `OctavoFixture` stages the packaged deterministic EPUB under app-private
  files.
- `OctavoSurfaceView` forwards lifecycle, surface, system-inset, and raw touch
  values and schedules requested native presentations.
- `OctavoNative` is the explicit Java/JNI boundary.
- `octavo_android_port3_build.c` source-consumes the exact Ground0, Reader0,
  UI0, and Readerview0 revisions once.
- `octavo_android_jni.c` owns one native application/reader/view state per
  surface, classifies inset-aware left/right tap zones, delegates page moves to
  Reader0, enforces the successful-presentation gate, acquires/releases its
  `ANativeWindow`, and presents canonical Reader0 EPUB pages through
  Readerview0 geometry.

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

The suite verifies native/shared version identity, the exact staged EPUB,
canonical Reader0 text and page evidence, a clean Readerview0 frame, actual page
and ink pixels, exact adjacent next/previous indices, changed/restored text,
pixels and progress, both boundary no-ops, rapid-tap presentation gating,
private paths, pause/resume, surface replacement, and Activity recreation. It
rejects any recorded native presentation or navigation failure.

Port 3 intentionally uses Ground0's deterministic bitmap font and does not yet
add document picking, swipes/held navigation, production typography,
persistence, or sync. See
[`../docs/android_port3.md`](../docs/android_port3.md) for the current milestone
boundary and [`../docs/android_port2.md`](../docs/android_port2.md) for the
preceding static-page slice.

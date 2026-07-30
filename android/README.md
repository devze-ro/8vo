# 8vo Android

This directory is the Android application root. Port 5 keeps the host explicit
while adding bounded user-document opening and durable Reader0 location resume:

- `OctavoActivity` owns Android lifecycle and the `ACTION_OPEN_DOCUMENT` picker.
- `OctavoDocumentStore` imports the selected bytes into digest-keyed app-private
  storage and atomically replaces a bounded versioned session record.
- `OctavoFixture` stages the packaged deterministic fallback EPUB under
  app-private files.
- `OctavoTypography` acquires fixed 18sp Android serif faces and publishes a
  caller-owned regular/bold/italic/bold-italic alpha atlas and advance table.
- `OctavoSurfaceView` owns the atlas creation call, forwards lifecycle,
  surface, system-inset, and raw touch values, and schedules requested native
  presentations. Only successfully presented Reader0 locations are persisted.
- `OctavoNative` is the explicit Java/JNI boundary.
- `octavo_android_port5_build.c` source-consumes the exact Ground0, Reader0,
  UI0, and Readerview0 revisions once.
- `octavo_android_jni.c` owns one native application/reader/view/typography
  state per surface, opens and restores through Reader0's existing public APIs,
  gives Reader0 the exact atlas advances for wrapping, paints canonical styled
  Reader0 rows, chooses handset content geometry through Readerview0, classifies
  left/right taps, enforces successful presentation, and owns its
  `ANativeWindow`.

There is no process-global mutable application state. The Java view holds the
native handle and destroys it when the Activity is destroyed.

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

The suite verifies native/shared version identity, exact staged EPUB bytes,
canonical Reader0 text/page evidence, a clean Readerview0 frame, proportional
`i`/`W` advances, all four rasterized styles, readable size/line metrics,
document-picker intent policy, deterministic digest-keyed import, distinct
selected-book text, semantic saved-location restore, corrupt-session fallback,
wide handset geometry, actual ink pixels, adjacent and cross-section
navigation, changed/restored text/pixels/progress, both boundary no-ops,
rapid-tap presentation gating, private paths, pause/resume, surface
replacement, and Activity recreation. It rejects every recorded native
presentation, restore, or navigation failure.

Port 5 intentionally remains a single-document reader. A library/catalog,
multiple saved books, removal/locate flows, user-selectable text settings, a
bundled cross-device-identical font, full Unicode shaping, embedded EPUB fonts,
and proportional title/progress chrome remain deferred. See
[`../docs/android_port5.md`](../docs/android_port5.md) for the current
milestone boundary and [`../docs/android_port4.md`](../docs/android_port4.md)
for the preceding typography slice.

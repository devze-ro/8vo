# Android Port 1: native frame and lifecycle state

Port 1 turns the Port 0 bootstrap into a minimal native 8vo application host.
It proves that Android can own explicit app state, provide private data paths,
and present deterministic native pixels across lifecycle transitions before the
shared reader stack is introduced.

## Delivered boundary

Port 1 adds:

- one caller-owned `OctavoAndroidApp` allocation per `OctavoSurfaceView`;
- bounded native copies of Android's app-private files and cache directories;
- explicit, idempotent host-resumed and host-paused transitions;
- balanced `ANativeWindow` acquisition and release across surface generations;
- a CPU-written `WINDOW_FORMAT_RGBA_8888` frame presented through the native
  window;
- native lifecycle, surface, frame, failure, and touch counters exposed only
  through the package-private JNI test seam; and
- instrumentation coverage for the real presented pixel, app paths,
  pause/resume, surface replacement, and Activity recreation.

The application ID remains `ro.devze.octavo`. Android supplies paths under that
application sandbox, so Port 1 requests no broad or legacy storage permission.

## Deterministic frame contract

The Port 1 frame clears every visible pixel to opaque `#181614` and then calls
`ANativeWindow_unlockAndPost`. A frame is counted only after that presentation
call succeeds. Presentation is attempted when a resumed host receives a usable
surface, when its surface geometry changes, and when a paused host resumes.

The Java `SurfaceView` uses the same color as its background. That prevents a
contrasting flash while Android creates or replaces the native surface, but the
instrumentation test uses `PixelCopy` to verify that the native buffer itself
contains the expected pixel.

## Lifecycle and ownership

`OctavoActivity` forwards `onResume` and `onPause` to its view. The view guards
duplicate transitions and remains the sole owner of the native handle. The
native state owns its acquired `ANativeWindow` reference and releases it on
surface destruction, replacement, or final state destruction.

There is no process-global mutable application state, hidden thread, provider
table, event bus, or dependency-injection layer. Rendering is synchronous on
the Android UI thread for this one-frame milestone; a rendering scheduler is
deferred until a real reader frame demonstrates its requirements.

## Shared-package boundary

Port 1 changes only 8vo. It does not move dependency pins or modify Ground0,
Reader0, UI0, or Readerview0. In particular:

- Reader0 and Readerview0 remain shared unchanged by 8vo and re10;
- Reader0 still owns the canonical document frame rather than platform state;
- Readerview0 still owns portable reader-interface projection rather than an
  Android surface; and
- Android lifecycle, paths, native-window ownership, and presentation policy
  remain in 8vo.

## Deliberately deferred

Port 1 does not:

- compile the full 8vo application core or shared reader/view packages into the
  Android APK;
- open, parse, or render an EPUB;
- translate touch input into reader intents;
- add a document picker, durable reader persistence, synchronization, or
  accessibility adapters; or
- introduce a graphics abstraction in anticipation of other platforms.

## Build and validation

From the Android directory, with exact dependencies available:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
```

Port 1 is accepted when:

1. the debug APK compiles for `arm64-v8a` and `x86_64` with C11 warnings treated
   as errors;
2. exact dependency guards pass without moving any first-party pin;
3. an emulator presents the opaque `#181614` native frame with zero recorded
   render failures;
4. native files/cache paths match Android's app-private directories;
5. pause/resume, surface replacement, and Activity recreation each complete
   without a native crash or stale window reference;
6. the seven-test Windows public smoke remains green; and
7. a physical Android device completes the same lifecycle sequence before the
   milestone is merged.

## Validation record

The automated gates passed on 2026-07-29 using the Android 16/API 36 x86_64
emulator:

- both configured Android ABIs compiled successfully;
- the instrumentation suite passed its native pixel, data-path,
  pause/resume, surface-generation, and Activity-recreation assertions;
- an independent cold launch presented a 1080 by 1920 frame whose sampled
  pixels were exactly ARGB `255,24,22,20`;
- native logs recorded successful frame presentation and no render failure;
- dependency pins remained unchanged; and
- the strict Windows build and all seven public smokes passed against the exact
  declared Ground0, Reader0, UI0, and Readerview0 revisions.

The physical-device lifecycle check remains outstanding and can be completed
when a USB-debugging device is available.

## Next vertical slice

Port 2 should compile the exact shared Ground0, Reader0, UI0, and Readerview0
sources into the Android application through their existing public seams, open
a bundled deterministic EPUB fixture, build one canonical Reader0 frame,
project it through Readerview0, and present one static page. Document picking,
production persistence, and touch navigation should remain separate follow-up
slices.

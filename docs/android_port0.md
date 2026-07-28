# Android Port 0: native bootstrap

Port 0 establishes the Android build, lifecycle, and native-host seams without
claiming application functionality that has not been ported.

## Delivered boundary

The 8vo Android application contains:

- a reproducible Gradle project with pinned Android Gradle Plugin, Gradle, NDK,
  CMake, minimum SDK, and target SDK versions;
- a thin `Activity` and `SurfaceView`;
- an explicit JNI boundary with a caller-owned native handle;
- balanced `ANativeWindow` acquisition and release across surface changes;
- a permanent touch-event seam for later input translation;
- an instrumentation smoke for native loading and activity startup.

The package and application ID are `ro.devze.octavo`. Port 0 builds both
`arm64-v8a` for physical devices and `x86_64` for the emulator.

## Deliberately deferred

Port 0 does not yet:

- compile `octavo.c` or the complete Ground0, Reader0, Readerview0, and UI0
  application graph into the APK;
- render an application frame;
- open an EPUB;
- provide Android file selection, application-data paths, clipboard,
  accessibility, keyboard, or reading persistence;
- synchronize reading state.

Those capabilities belong to later vertical slices after the host, foundation,
and Reader0 smoke targets compile and run reliably.

Android's automatic application backup is disabled in this milestone. Reading
data must not begin syncing through an implicit platform mechanism before the
versioned, user-selected cloud contract is designed and tested.

Reader0 and Readerview0 remain shared by 8vo and re10. Android lifecycle,
surface ownership, document picking, persistence, and synchronization policy
must stay in the 8vo host unless both consumers demonstrate a genuinely shared
mechanism. Port 0 changes no Reader0 public API and makes no Readerview0 change.

## Coordinated repository checks

The same `android/port0-bootstrap` branch name is used in Ground0, Reader0, and
8vo so the work remains easy to review across the public repositories.

Ground0 Port 0 adds Android platform detection plus a native smoke covering
virtual memory, monotonic time, and an app-owned atomic file round trip.
Reader0 Port 0 compiles its existing core/host smoke against that Android
foundation subset. Each repository keeps its own build entry point:

```powershell
# Ground0
powershell -NoProfile -ExecutionPolicy Bypass -File build\android_port0.ps1

# Reader0
powershell -NoProfile -ExecutionPolicy Bypass -File build\android_port0.ps1

# 8vo
cd android
.\gradlew.bat :app:connectedDebugAndroidTest
```

The Ground0 and Reader0 scripts default to an `x86_64` emulator build and can
optionally install and run their native smoke executables through `adb`. They
also accept `arm64-v8a` for physical-device compilation.

Reader0 continues to enforce its exact Ground0 commit pin. The Ground0 Port 0
change must therefore be reviewed and committed, and Reader0's pin updated,
before the coordinated Android Reader0 smoke can pass.

## Promotion order

The shared repositories must be promoted in dependency order after Android
validation:

1. validate and commit Ground0 Port 0;
2. advance Reader0's exact Ground0 pin, run its Windows and Android smokes, and
   commit Reader0 Port 0;
3. refresh re10's Ground0 snapshot and exact Reader0 pin, then run its
   production build and Reader0 smoke;
4. advance 8vo's exact Ground0 and Reader0 pins and rerun its Windows and
   Android suites.

Readerview0 and UI0 pins do not move in Port 0. This order keeps re10 and 8vo
on reviewed, coherent dependency sets rather than temporarily pointing either
consumer at a dirty sibling checkout.

## Acceptance criteria

Port 0 is complete when:

1. the existing Windows validation suites remain green;
2. Ground0 compiles for `x86_64` and `arm64-v8a`, and its smoke passes on an
   emulator;
3. Reader0 compiles for both ABIs and its core/host smoke passes on an emulator;
4. the 8vo debug APK builds for both ABIs;
5. the Android instrumentation smoke passes on an API 26 or newer emulator;
6. a physical Android device can launch the debug activity and complete
   surface create, resize, destroy, and recreation without a native crash.

Most iteration can run on a Windows-hosted emulator. The physical-device check
is retained because emulator success does not exercise every vendor graphics,
lifecycle, and input behavior.

## Validation record

The automated Port 0 gates passed on 2026-07-29 using Windows 11, WHPX, an
Android 16/API 36 x86_64 Google APIs emulator, JDK 17.0.19, NDK
`29.0.14206865`, and CMake `3.31.6`:

- Ground0 revision `770b970b4655facfa9700c3d1025d96102365631` compiled for
  x86_64 and arm64-v8a and passed its emulator smoke.
- Reader0 revision `b604556723c5a196ed7d2b1249f56bd3d976edb4` compiled for
  both ABIs and passed its Android core and generated-EPUB host smokes.
- The 8vo debug APK built for both ABIs and its instrumentation test passed,
  including Activity recreation.
- A cold visible launch created a 1080 by 1920 native surface without a crash.
- Ground0, Reader0, 8vo, and re10 Windows regression gates passed against the
  promoted exact revisions.

The physical-device lifecycle check remains outstanding.

## Next vertical slice

Port 1 should connect a minimal 8vo native application state to the Android
surface, run one deterministic frame, and present a known clear color. It
should also establish Android application-data paths and lifecycle-safe
suspend/resume handling before EPUB loading is introduced.

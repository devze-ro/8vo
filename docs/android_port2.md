# Android Port 2: shared static reader page

Port 2 replaces the bootstrap clear frame with the first real Android reader
slice. It compiles 8vo's exact declared Ground0, Reader0, UI0, and Readerview0
source revisions, opens a bundled EPUB through Reader0, projects portable
reader geometry and chrome through Readerview0, and presents one static page
through the Android native window.

## Delivered boundary

Port 2 adds:

- one Android application unity build that compiles `reader0.c`, `ui0.c`, and
  `readerview0.c` exactly once alongside their explicit Ground0 substrate;
- Gradle-to-CMake paths for the exact first-party dependency checkouts, with
  the existing clean-revision guard still running before every build;
- a 1,927-byte deterministic EPUB fixture with SHA-256
  `35EE6AB86D98D310BAAA0981905652D9D75BA4D814C34A6249AD2F66B45BE00A`;
- synchronous fixture staging under the application's private files directory,
  without storage permissions;
- caller-owned Reader0, Reader0-frame, Readerview0-state, Readerview0-frame,
  theme, layout, native-window, and lifecycle storage;
- full-fixture Reader0 pagination and one canonical `EpubReaderFrame`;
- a minimal paging/progress `ReaderViewProjection`, light UI0 theme, page
  geometry, chrome title, and progress frame;
- a bounded 8vo-owned CPU adapter from the portable reader/view values to an
  opaque Android RGBA buffer;
- Android system-bar insets forwarded as values into the native layout; and
- instrumentation evidence for the exact shared versions, fixture bytes,
  Reader0 text/frame, Readerview0 frame, page geometry, actual page/ink pixels,
  pause/resume, surface replacement, and Activity recreation.

The application ID remains `ro.devze.octavo`.

## Reader pipeline

The vertical slice is explicit and synchronous:

1. Java copies the packaged fixture to
   `<files>/port2/octavo_port2.epub`.
2. The native application initializes Ground0's OS, time, and thread context
   for the Android UI thread.
3. Reader0 opens the private filesystem path, refreshes the active spine,
   paginates the deterministic fixture, and publishes an
   `EpubReaderFrame` into caller-owned storage.
4. Readerview0 resolves the system-inset-aware page geometry and builds a
   values-only UI frame against the exact UI0 light profile.
5. 8vo paints the resolved page surface, canonical Reader0 visible text,
   Readerview0 text bindings, and supported UI0 draw commands into the locked
   `ANativeWindow` buffer.

The document frame is not duplicated in Java or Readerview0. Android surface,
fixture, inset, and presentation policy do not enter Reader0 or Readerview0.

## Temporary raster boundary

Port 2 deliberately uses Ground0's deterministic portable 3-by-5 bitmap font
at an integer scale. This gives the emulator and device tests real, legible,
stable pixels without pretending that the diagnostic font is production
Android typography.

The CPU adapter handles the UI0 operations needed by this static frame and
uses UI0's public icon rasterizer. Production shaping, richer book styling,
images, and a durable graphics/presentation strategy remain later milestones.
This adapter belongs to 8vo; it does not create a platform rendering boundary
inside Reader0 or Readerview0.

## Shared-package boundary

Port 2 changes only 8vo. Ground0, Reader0, UI0, and Readerview0 remain
unchanged at the exact revisions already declared by 8vo. In particular:

- Reader0 and Readerview0 remain one shared implementation consumed by both
  8vo and re10;
- Reader0 still owns EPUB parsing, document state, pagination, and the
  canonical reader frame;
- Readerview0 still consumes only values and caller-owned storage, with no
  Android window, file, lifecycle, or persistence dependency; and
- 8vo remains responsible for platform paths, lifecycle, system insets,
  presentation, and test-only JNI observation.

## Deliberately deferred

Port 2 does not add:

- a document picker or access to user-selected books;
- touch/keyboard page navigation or reader actions;
- production Android font discovery, shaping, or rasterization;
- book images, tables, annotations, selection, search, or settings UI;
- durable reading position, library persistence, or synchronization;
- Android accessibility-node adaptation; or
- a background render scheduler.

The existing touch boundary still records events but does not interpret them.

## Build and validation

From the Android directory, with the exact dependency checkouts available:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
```

Port 2 is accepted when:

1. the APK compiles for `arm64-v8a` and `x86_64` under C11 with warnings
   treated as errors;
2. the exact clean Ground0, Reader0, UI0, and Readerview0 guards pass;
3. Reader0 opens the exact fixture and publishes nonempty canonical text with
   a valid page index/count;
4. Readerview0 builds without projection or record errors;
5. PixelCopy observes the UI0 page color and real Reader0 ink pixels;
6. pause/resume, surface replacement, and Activity recreation remain clean;
7. the strict Windows public smoke suite remains green; and
8. the same lifecycle/page test is completed on a physical Android device
   before merge.

## Validation record

The Android automated gates passed on 2026-07-29 using the Android 16/API 36
x86_64 emulator:

- both configured Android ABIs compiled successfully;
- the staged fixture length and SHA-256 matched the packaged asset;
- Reader0 published 2,754 visible bytes with deterministic emulator hash
  `4f870e903f6fa2d7`;
- Readerview0 published a clean paging/progress frame;
- PixelCopy found the exact app background, page surface, and more than the
  required bounded count of dark reader-text pixels;
- system-bar-aware presentation was visually inspected on a 1080 by 1920
  emulator screen; and
- pause/resume and Activity recreation passed with zero recorded render
  failures;
- the strict 8vo Windows build and all seven public smokes passed against its
  exact declared dependency revisions; and
- unchanged re10 completed its strict product/qualification build and
  `--document_engine_smoke` against its own exact Reader0, UI0, and
  Readerview0 revisions.

The same instrumentation test passed on 2026-07-29 on a physical vivo I2019
(iQOO 9 SE) running Android 14/API 34 on `arm64-v8a`:

- the 1,080 by 2,400 display produced a 1,080 by 2,196 system-inset-aware
  render surface;
- Reader0 again published 2,754 visible bytes with hash
  `4f870e903f6fa2d7`;
- Readerview0 again published three clean draw records;
- PixelCopy, pause/resume, surface replacement, and Activity recreation passed
  in the 3.068-second instrumentation run; and
- the device crash buffer remained empty.

This completes the Port 2 physical-device acceptance check.

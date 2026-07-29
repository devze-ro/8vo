# Android Port 3: bounded page navigation

Port 3 turns the static Port 2 reader page into a bounded interactive slice.
It translates Android taps into previous/next intents, delegates every page
transition to Reader0, rebuilds the canonical Reader0 frame and Readerview0
projection, and requires each changed page to be presented successfully before
another page mutation is accepted.

## Delivered boundary

Port 3 adds:

- deterministic inset-aware left/right tap zones owned by the 8vo Android host;
- bounded tap recognition using a down/up pair in the same zone, at most 500
  milliseconds apart and at most 24 pixels apart;
- previous/next calls through Reader0's existing `epub_reader_move_page` API;
- canonical Reader0-frame and Readerview0 projection rebuilds after every
  successful move;
- updated reader text, section-local page progress, and progress chrome;
- an explicit successful-presentation gate keyed by Reader0's canonical
  `(spine_index, byte_offset)` identity;
- safe Reader0 `Boundary` handling with no page, frame, progress, or pixel
  mutation;
- a reproducible four-section EPUB fixture with enough text for multiple pages
  on the API 36 emulator and the supported physical test viewport;
- instrumentation for adjacent navigation, text/pixel/progress changes,
  boundaries, rapid taps, lifecycle, native-window replacement, and Activity
  recreation; and
- explicit native counters for accepted tap intents, successful Reader0 moves,
  presented moves, boundaries, gated input, and navigation failures.

The application ID remains `ro.devze.octavo`.

## Input and presentation policy

8vo owns the complete host policy:

1. Java forwards raw Android action, coordinate, and event-time values to the
   native application state.
2. Native 8vo excludes system insets and divides the remaining width into
   equal thirds. The left third means previous page, the right third means next
   page, and the middle third is a no-op.
3. A valid tap must begin and end in the same directional zone within the
   bounded duration and movement limits.
4. 8vo calls `epub_reader_move_page` with the current Reader0 layout key and
   layout configuration. Java and Readerview0 contain no duplicated page
   transition logic.
5. A successful Reader0 change closes the mutation gate and records the
   expected Reader0 spine/byte identity. Java only posts the requested native
   presentation callback.
6. The native presenter rebuilds the canonical Reader0 frame and Readerview0
   projection, paints them, and calls `ANativeWindow_unlockAndPost`.
7. The gate reopens only when that call succeeds and the rebuilt frame matches
   the expected Reader0 spine/byte identity. Input arriving while the gate is
   closed is consumed without another page mutation.

This makes rapid taps deterministic: a second tap dispatched before the first
page is posted is blocked, while taps arriving after successful presentation
advance exactly one adjacent Reader0 page each.

A pause or surface loss never reopens the gate. The pending page is presented
by the normal resume/surface-created path before navigation can continue.

## Boundary behavior

`epub_reader_move_page` remains authoritative at both ends of the book.
`EpubReaderResult_Boundary` increments host observation evidence but does not
request a frame, change text/progress, or count as a native failure. The
instrumentation starts with a previous tap at the first page and then advances
through Reader0 until a next tap reports the final boundary.

Reader0's canonical frame page index/count are section-local. A prepared
cross-section window may temporarily publish a known page index with
`page_count == 0`; 8vo preserves that API meaning and does not manufacture a
book-global progress model.

## Deterministic fixture

Port 3 stages:

- asset: `android/app/src/main/assets/port3/octavo_port3.epub`
- private path: `<files>/port3/octavo_port3.epub`
- length: 96,704 bytes
- SHA-256:
  `40EB1FD2CEF876C8EBD80BE7C62DBF5387DE68C4F918D31EEDC5C5E893F19991`

The fixture contains four ordered XHTML sections with 256 uniquely numbered
paragraphs per section. `scripts/build_android_fixture.ps1` writes the EPUB in
fixed entry order, stores every entry without compression, fixes every ZIP
timestamp, and emits the resulting length/hash. Two consecutive builds must
produce the exact bytes above.

At the 1,080 by 1,920 API 36 emulator viewport, Reader0 publishes six pages in
the first section. This guarantees that adjacent 1-to-2-to-1 navigation and
rapid-input gating are tested before a cross-section transition.

## Shared-package boundary

Port 3 changes only 8vo. Ground0, Reader0, UI0, Readerview0, and re10 remain
unchanged.

- Reader0 exclusively owns page adjacency, cross-section movement, boundary
  results, pagination, and canonical frame publication.
- Readerview0 consumes values and caller-owned storage and remains unaware of
  Android input, lifecycle, surfaces, and scheduling.
- 8vo owns touch classification, render scheduling, native-window ownership,
  successful-presentation policy, and test observation.
- `reader0.c`, `ui0.c`, and `readerview0.c` are each source-consumed exactly
  once by `octavo_android_port3_build.c`.

No Android dependency or host policy enters a shared package.

## Automated coverage

The Android suite contains three instrumentation tests:

1. the Port 2 foundation test still verifies exact versions and paths, exact
   fixture bytes, canonical text/ink, Readerview0 output, pause/resume,
   explicit native-window replacement, and Activity recreation;
2. the adjacent-navigation test verifies first-page previous boundary,
   exact 1-to-2-to-1 page indices, visible-text hashes and strings, PixelCopy
   changes/restoration, progress changes/restoration, a complete Reader0-driven
   walk to the final boundary, and zero native/render failures; and
3. the rapid-input test dispatches two right-zone taps in one UI turn, observes
   one successful move pending presentation plus one gated tap, then requires
   exactly page 2 to be presented before testing pause/resume, surface
   replacement, recreation, and navigation after recreation.

Every accepted successful move must have a matching presented-move count when
the application is stable.

## Deliberately deferred

Port 3 does not add:

- swipes, fling velocity, held/repeated navigation, or configurable tap zones;
- document picking or access to user-selected books;
- durable reading-position persistence across Activity recreation;
- production Android typography, shaping, images, tables, annotations,
  selection, search, or settings;
- Android accessibility-node adaptation; or
- a background renderer.

Ground0's deterministic bitmap font remains the temporary raster boundary.

## Build and validation

With the exact dependency directories selected through `OCTAVO_*_DIR`:

```powershell
cd android
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
```

Port 3 is accepted when:

1. the exact clean Ground0, Reader0, UI0, and Readerview0 guards pass;
2. both `arm64-v8a` and `x86_64` debug binaries build;
3. all emulator instrumentation passes with zero render/navigation failures;
4. the strict 8vo Windows build and public smoke suite pass;
5. unchanged re10 passes its strict product/qualification build and
   `--document_engine_smoke`; and
6. the instrumentation and hands-on left/right tap checks pass on the iQOO
   physical device.

## Validation record

On 2026-07-29, the Android 16/API 36 x86_64 emulator passed all 3
instrumentation tests in 15.871 seconds:

- the exact dependency guards passed;
- both configured Android ABIs compiled;
- the fixture reproduced byte-for-byte at the documented length and hash;
- the initial 1,080 by 1,920 surface published page 1 of 6 with 3,489 visible
  bytes, hash `5e00368df8356107`, and three clean Readerview0 draw records;
- page 2 published different text, pixels, and progress, and page 1 was restored
  exactly by a previous tap;
- rapid input produced one pending move and one gated tap, then one successful
  presentation;
- the suite traversed all four sections and observed both book boundaries;
- pause/resume, native-window replacement, and Activity recreation passed; and
- no render or navigation failure was recorded.

The strict Windows gates also passed on 2026-07-29:

- `scripts/run_public_smoke.ps1` completed the strict 8vo build, architecture
  and dependency guards, and all seven public smoke tests;
- unchanged re10 completed its strict product and qualification builds within
  the PE size budgets; and
- unchanged re10's `--document_engine_smoke` passed with four anchors, final
  spine index 3, and hash `f3c13a55f0349720`.

The emulator crash buffer remained empty after instrumentation and direct
right/left tap smoke input. The required physical-device instrumentation and
hands-on tap check remain pending. Port 3 is not physically accepted until
both pass on the iQOO.

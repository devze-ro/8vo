# Android Port 5: document open and durable resume

Port 5 turns the bounded Android reader into a user-document reader without
introducing a library/catalog or moving document policy into shared packages.
The user can choose one EPUB through Android's standard document picker. 8vo
copies the bytes into app-private storage, opens the copy through Reader0, and
durably resumes the last successfully presented Reader0 location.

## Delivered boundary

Port 5 adds:

- an inset-safe **Open EPUB** host control;
- `ACTION_OPEN_DOCUMENT` with `CATEGORY_OPENABLE`, EPUB-compatible MIME
  filters, and read-only URI permission;
- a bounded 512 MiB streaming import into app-private storage;
- SHA-256 document identity and digest-keyed managed filenames;
- native reader replacement only after the selected copy opens successfully;
- a bounded, versioned, atomically replaced session record;
- persistence of Reader0 spine index and byte offset only after presentation;
- debounced saves while reading and synchronous lifecycle/surface flushes;
- Reader0-owned semantic location restore on launch and recreation;
- safe fallback to the deterministic fixture for corrupt, missing, or invalid
  session state; and
- instrumentation covering selection, distinct content, resume, fallback,
  navigation, presentation gating, lifecycle, surfaces, and failures.

The application ID remains `ro.devze.octavo`.

## Ownership and data flow

Document policy remains inside 8vo:

1. `OctavoActivity` launches Android's standard document picker.
2. `OctavoDocumentStore` streams the returned URI once, enforces the size cap,
   computes SHA-256, fsyncs the bytes, and atomically publishes
   `<files>/port5/documents/<sha256>.epub`.
3. 8vo constructs a replacement `OctavoSurfaceView`; its caller-owned native
   state opens the managed path through `epub_reader_open`.
4. The previous reader is released only after the replacement opens
   successfully. Cancellation or invalid EPUB selection leaves the presented
   document untouched.
5. Reader0 publishes the canonical frame and Readerview0 projects it exactly
   as in Ports 2-4.
6. After native-window presentation succeeds, 8vo queries the canonical
   Reader0 spine index and view byte offset.
7. `OctavoDocumentStore` debounces equivalent positions, writes a versioned
   temporary session, fsyncs it, and atomically replaces
   `<files>/port5/reader_session.v1`.
8. A later Activity loads the managed document and asks Reader0's existing
   location-navigation API to rebuild a frame containing the saved byte.

Java does not parse EPUB, paginate, classify page movement, or recreate
navigation logic. Reader0, UI0, and Readerview0 gain no Android dependency,
document-picker policy, persistence policy, or process-global mutable state.

## Successful-presentation persistence gate

A navigation mutation is not durable merely because Reader0 accepted it.
Native 8vo exposes a reading position only when:

- a canonical Reader0 frame is ready and the document is open;
- at least one native-window presentation has succeeded;
- Reader0 has a current page; and
- no page move is waiting for its requested presentation.

`OctavoSurfaceView` captures that position after successful presentation.
Repeated positions are coalesced for 350 ms to avoid a synchronous fsync for
every rapid tap. Pause, surface destruction, and explicit release synchronously
flush any captured position before native state is relinquished. Failed writes
remain visible through counters and log output.

This gate prevents an accepted but unpresented page from becoming the resume
location.

## Semantic restore

The session stores Reader0's spine index and byte offset rather than Android
page number. Page phase and count depend on the current viewport and platform
font metrics, so an exact raster/page-number restore would be brittle.

After layout geometry is known, native 8vo requests Reader0's bounded window
pagination and calls the existing `epub_reader_navigate_to_location` API with
history suppression. Restore succeeds only when Reader0's resulting current
page is in the saved spine and contains the resolved target byte. The restored
frame can therefore begin before the saved byte and can report an unknown
bounded-window page count (`0`); canonical location progress remains
authoritative. A failed semantic restore records a failure and rebuilds from a
safe Reader0 location.

## Persistent record

`reader_session.v1` contains only bounded scalar data:

- magic and version;
- whether the document is imported or the packaged fixture;
- a lowercase 64-character SHA-256 key;
- expected document byte count;
- whether a successfully presented location exists;
- Reader0 spine index; and
- Reader0 byte offset.

The record is capped at 512 bytes. Imported documents must resolve under the
app-private digest-keyed directory with the expected length. Fixture sessions
must match the compiled fixture identity. Invalid, truncated, oversized,
missing-document, or out-of-range records are ignored and the deterministic
fixture opens at its safe initial location.

The app requests no broad storage permission and does not retain dependency on
the provider URI after import.

## Deterministic fixtures

Port 5 stages two reproducible EPUBs:

| Purpose | Asset | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Default/fallback | `assets/port5/octavo_port5.epub` | 219,603 | `F6DA909CF3D2E701633D9CA37356C334055C2443920D9454C985934FF78A466C` |
| Selected-book test | `assets/port5/octavo_port5_selected.epub` | 41,514 | `E47AE862774F391578021438FAF503639971745B0172CFDBB960DE02C77D3643` |

`scripts/build_android_port5_fixtures.ps1` writes both in fixed ZIP-entry
order, stores entries without compression, fixes every ZIP timestamp, and
prints the exact length and hash. The selected fixture has distinct title,
section count, and visible prose so instrumentation proves that the imported
document—not the fallback—was published and restored.

## Automated coverage

The seven-test instrumentation suite requires:

- the exact Port 5 fixture identity and app-private staging path;
- a standard read-only document-picker contract;
- a selected EPUB copied to its SHA-256 managed path;
- visibly distinct selected-book title and canonical Reader0 text;
- adjacent next/previous pages, cross-section moves, and both boundaries;
- changed/restored text, pixels, page indices, and progress;
- rapid-tap successful-presentation gating;
- saved spine/byte evidence after presenting selected-book page 2;
- semantic Reader0 restore containing that saved location after relaunch;
- corrupt-session fallback to the deterministic fixture;
- invalid selected-file handling that preserves the current reader;
- pause/resume, surface replacement, and Activity recreation; and
- zero native render, navigation, restore, session-save, and reader-open
  failures in every accepted state.

## Shared-package boundary

Port 5 changes only 8vo. Ground0, Reader0, UI0, Readerview0, and re10 remain
unchanged.

- `reader0.c`, `ui0.c`, and `readerview0.c` are each source-consumed exactly
  once by `octavo_android_port5_build.c`.
- Reader0 is consumed only through `reader0.h` and its existing open,
  pagination, canonical-frame, page-move, and location-navigation APIs.
- Readerview0 continues to supply layout, projection, progress, and content
  geometry.
- 8vo owns Android URI selection, managed-copy lifetime, session persistence,
  lifecycle, input, drawing, window ownership, and presentation policy.

## Deliberately deferred

Port 5 is not the complete desktop application on Android. It does not add:

- a library/catalog surface, thumbnails, recent-book list, or multiple
  per-book saved positions;
- remove, rename, locate, re-import, share, or provider-permission management;
- a close-book command or return-to-library flow;
- document metadata/details UI;
- user-selectable font family, text size, line spacing, margins, or themes;
- a bundled cross-device-identical font, full Unicode shaping, embedded EPUB
  fonts, images, tables, annotations, selection, search, or accessibility
  adaptation; or
- background document loading/rendering.

The visible **Open EPUB** button is intentionally plain host chrome for this
milestone. A shared, polished library and reader-toolbar flow belongs to later
ports.

## Build and acceptance

With exact clean dependency directories selected through `OCTAVO_*_DIR`:

```powershell
cd android
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
```

Port 5 is accepted when:

1. exact clean Ground0, Reader0, UI0, and Readerview0 guards pass;
2. both `arm64-v8a` and `x86_64` debug binaries build;
3. all seven emulator instrumentation tests pass with zero native/render/
   navigation/restore failures;
4. the strict 8vo Windows build and seven public smoke tests pass;
5. unchanged re10 passes its strict product/qualification build and
   `--document_engine_smoke`; and
6. instrumentation plus hands-on picker, navigation, invalid-selection, and
   relaunch/resume checks pass on the iQOO physical device.

## Validation record

On 2026-07-30, the API 36 x86_64 emulator passed all seven Port 5
instrumentation tests. The exact dependency guard and both configured Android
ABI native builds passed in the same Gradle invocation. The suite exercised
the default fixture, the distinct managed import, saved-location relaunch,
corrupt-session fallback, invalid-file retention, prior navigation/lifecycle
coverage, and zero native/render/navigation failures.

The strict Windows gates also passed on 2026-07-30:

- `scripts/run_public_smoke.ps1` completed the strict C11 build, architecture
  and exact-dependency guards, and all seven public smoke tests;
- unchanged re10 completed its strict optimized product and qualification
  builds within the fixed PE image budgets; and
- unchanged re10's `--document_engine_smoke` passed with four anchors, final
  spine index 3, and hash `f3c13a55f0349720`.

Final API 36 screenshot inspection confirmed that the host-owned **Open EPUB**
row is below the system status bar, does not cover Reader0 content, and retains
Port 4's readable serif geometry. Tapping it launched Android DocumentsUI's
picker, Back returned to the presented book, and the post-check Android crash
buffer was empty.

On 2026-07-31, the vivo I2019/iQOO 9 SE running Android 14/API 34 on ARM64
passed all seven Port 5 instrumentation tests in 45.072 seconds. Its
1,080 by 2,020 reader surface published default page 1 of 52 with 716 visible
bytes and hash `7c6725239a19b8c8`. The suite passed managed import, semantic
Reader0 resume, corrupt-session fallback, invalid-selection retention, exact
adjacent navigation before restore, navigation after bounded restore,
presentation gating, lifecycle, surface replacement, and Activity recreation.
The Android crash buffer was empty.

The user then completed the hands-on flow with the deterministic selected-book
fixture: DocumentsUI selection changed the title and visible text to
**Selected Port 5 Book**, right-third navigation advanced it, removing 8vo
from Recents and reopening resumed the selected book near the saved semantic
location, and both navigation directions remained operational. The temporary
fixture was removed from Downloads after validation; the debug app and its
app-private managed copy remain installed.

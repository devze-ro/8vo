# Android Port 6: library catalog and per-book resume

Port 6 turns the single-document Android reader into a bounded, host-owned
library. The application starts on a native Android library surface, retains a
built-in deterministic sample, imports multiple EPUBs into digest-keyed
app-private storage, and keeps an independent successfully presented Reader0
location for every book.

The milestone remains intentionally smaller than the Windows product library.
It establishes catalog identity, lifetime, open/close flow, and exact per-book
resume without moving Android or product policy into shared packages.

## Delivered boundary

Port 6 adds:

- a system-inset-safe **8vo Library** launch surface;
- a permanent built-in sample plus up to 63 imported EPUB entries;
- **Add EPUB**, **Open/Resume**, **Library**, and imported-book **Remove**
  controls owned by 8vo;
- bounded streaming import through Android's standard document picker;
- SHA-256 identity and one managed copy per distinct byte sequence;
- duplicate import that reopens the existing entry instead of adding a row;
- Reader0-validated titles recorded only after a document opens successfully;
- independent, successfully presented Reader0 spine/byte positions;
- exact same-layout page reconstruction through Reader0's existing canonical
  location-navigation API;
- a bounded, versioned, atomically replaced catalog;
- safe sample-only fallback for a corrupt or unavailable catalog;
- one-time migration of an imported Port 5 session when no Port 6 catalog
  exists;
- managed-copy removal that never deletes the provider-owned source; and
- instrumentation covering the library, two imports, deduplication, exact
  resume, removal, migration, corruption, navigation, lifecycle, surfaces,
  pixels, and failure counters.

The application ID remains `ro.devze.octavo`.

## Ownership and flow

All library policy remains inside 8vo:

1. `OctavoActivity` starts on the library and launches `ACTION_OPEN_DOCUMENT`
   with read-only URI permission.
2. `OctavoLibraryStore` streams the returned URI once, enforces the 512 MiB
   cap, computes SHA-256, fsyncs the copy, and atomically publishes
   `<files>/port6/documents/<sha256>.epub`.
3. Existing catalog identity wins for duplicate bytes. The temporary import is
   discarded and 8vo opens the existing book and saved position.
4. A new `OctavoSurfaceView` creates caller-owned native state and opens the
   managed path through Reader0. The catalog is not mutated if Reader0 rejects
   the EPUB.
5. After a successful open, Reader0's validated document title is stored and
   the library is sorted with the built-in sample first and recent imports
   after it.
6. Reader0 publishes canonical frames and Readerview0 projects content and
   progress exactly as in Ports 2-5.
7. After successful native-window presentation, 8vo captures the current
   Reader0 spine index and page-start byte and saves them against that book's
   SHA-256 key.
8. **Library** flushes the pending presented location before releasing native
   state. A later open asks Reader0 to reconstruct the canonical page
   containing the saved page-start byte.
9. **Remove** commits the catalog without the selected imported entry and then
   deletes only its app-private managed copy. The original provider document
   remains untouched.

Java does not parse EPUB, paginate, classify page movement, or reproduce
Reader0 navigation. Reader0, UI0, and Readerview0 gain no Android dependency,
catalog policy, storage policy, or process-global document state.

## Exact per-book resume

The durable position is still gated by successful presentation. A move that
Reader0 accepted but 8vo has not presented cannot become a saved location.
Equivalent captures are coalesced for 350 ms; pause, surface destruction, and
reader release synchronously flush pending state.

The initial Port 6 candidate used Reader0's bounded-window location path. That
path correctly contained the saved byte but could publish a locally phased
page with unknown page index/count and different visible text. Hands-on Alpha
deduplication exposed the mismatch. Port 6 now calls the existing
`epub_reader_navigate_to_location` canonical path without requesting the
approximate window phase. Under the same viewport and typography it must
restore all of the following exactly:

- spine index;
- presented page-start byte;
- page index and page count;
- visible-text hash; and
- canonical location progress.

Instrumentation asserts those identities after duplicate import, fresh
Activity launch, and Activity recreation. Reader0 remains the sole owner of
pagination and location resolution.

## Catalog contract

`<files>/port6/library.v1` contains a fixed header and at most 64 bounded
entries. The file is capped at 128 KiB. Each entry contains only:

- imported/sample identity;
- lowercase 64-character SHA-256 key;
- expected document byte count;
- Reader0-validated title, capped at 256 Unicode code points;
- added and last-opened timestamps;
- whether a successfully presented position exists;
- Reader0 spine index; and
- Reader0 page-start byte offset.

Catalog writes use a temporary file, fsync, and atomic replacement when the
filesystem supports it. A failed write restores the prior in-memory catalog.
Catalog load rejects invalid headers, duplicate keys, out-of-range values,
missing built-in identity, unmanaged paths, missing files, and size mismatch.
Failure produces a safe in-memory library containing only the deterministic
sample.

The catalog does not retain provider URIs or request broad storage permission.

## Port 5 migration

When `library.v1` does not exist, 8vo checks the bounded Port 5
`reader_session.v1` record. A valid imported session is copied into the Port 6
managed directory, preserving its SHA-256 identity and presented Reader0
position, then published with the sample in the new catalog. The Port 5 source
copy and session remain untouched.

Once the Port 6 catalog exists, it is authoritative and migration is not
repeated. Invalid or unavailable legacy state leaves a sample-only library.

## Deterministic fixtures

Port 6 stages three reproducible EPUBs:

| Purpose | Asset | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Built-in sample | `assets/port6/octavo_port6.epub` | 242,131 | `5D81C6BA136774CB4ADDC01DFC88BEC355D637456EE6AACB3004983A6F055ED3` |
| Imported Alpha | `assets/port6/octavo_port6_alpha.epub` | 44,190 | `DD92F87FA70EA37F761CB9348D5F7B2939AFEA2661F9F4FE16828AC6CA041F80` |
| Imported Beta | `assets/port6/octavo_port6_beta.epub` | 56,036 | `E0CCA3A5283CE0AD3C2C78871B968C2B5E0711AD81E2BBFAAF92BFE3A35CB0A8` |

`scripts/build_android_port6_fixtures.ps1` writes all three with fixed ZIP
entry order, fixed timestamps, and no compression. Alpha and Beta have
different titles, section counts, text, and byte identities. The sample has
enough content for multi-page navigation at both supported validation
viewports.

## Automated coverage

The nine-test instrumentation suite requires:

- library-first launch with the built-in sample;
- standard read-only Android picker intent;
- two distinct digest-keyed managed imports;
- Reader0-validated Alpha and Beta titles and visibly different text;
- duplicate Alpha import without a fourth catalog entry;
- exact per-book page/text/progress/byte restoration;
- provider-source preservation and managed-copy-only removal;
- sample usability after invalid import and corrupt catalog fallback;
- one-time Port 5 imported-session migration;
- adjacent next/previous pages, cross-section moves, changed pixels and
  progress, both boundaries, and rapid-tap presentation gating;
- pause/resume, surface replacement, and Activity recreation; and
- zero native render, Reader0 open, navigation, restore, Readerview0, catalog
  save, and managed-delete failures in every accepted state.

## Shared-package boundary

Port 6 changes only 8vo. Ground0, Reader0, UI0, Readerview0, and re10 remain
unchanged.

- `reader0.c`, `ui0.c`, and `readerview0.c` are each source-consumed exactly
  once by `octavo_android_port6_build.c`.
- Reader0 is consumed only through `reader0.h` and its existing open,
  pagination, frame, page-move, and location-navigation APIs.
- Readerview0 continues to own projection, progress, and content geometry.
- 8vo owns the library, picker, managed copies, catalog, removal, lifecycle,
  touch input, native window, rendering, and presentation policy.

## Deliberately deferred

Port 6 is not yet the complete Windows application on Android. It does not add:

- cover thumbnails, library search/sort controls, folders, tags, or metadata
  details;
- rename, locate, re-import, share, or provider-permission management;
- removal confirmation or undo;
- user-selectable font family, text size, line spacing, margins, or themes;
- embedded EPUB fonts, complete Unicode shaping, images, tables, annotations,
  selection, search, or Android accessibility adaptation; or
- background import, pagination, or rendering.

## Build and acceptance

With exact clean dependency directories selected through `OCTAVO_*_DIR`:

```powershell
cd android
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
```

Port 6 is accepted when:

1. exact clean Ground0, Reader0, UI0, and Readerview0 guards pass;
2. both `arm64-v8a` and `x86_64` debug binaries build;
3. all nine emulator instrumentation tests pass;
4. the strict 8vo Windows build and seven public smoke tests pass;
5. unchanged re10 passes its strict product/qualification build and
   `--document_engine_smoke`; and
6. all nine tests plus hands-on two-book import, duplicate exact resume,
   removal, and reader/library navigation pass on the iQOO physical device.

## Validation record

On 2026-08-01, the API 36 x86_64 emulator passed all nine final Port 6 tests
in 87.467 seconds. A focused duplicate-import, Activity-recreation, and
bootstrap run passed three tests in 27.335 seconds. The exact dependency guard
and both configured Android ABI builds passed in the same build cycle.

The emulator library and reader were also inspected visually. System insets,
library margins, explicit dark summary/status/title text on the cream host
background, controls, reader toolbar, proportional serif content, and progress
geometry were visible and unobstructed.

The strict desktop gates passed:

- `scripts/run_public_smoke.ps1` completed the strict C11 build, architecture
  and exact-dependency guards, and all seven public smoke tests;
- unchanged re10 completed its strict optimized product and qualification
  builds within the fixed PE image budgets; and
- unchanged re10's `--document_engine_smoke` passed with four anchors, final
  spine index 3, and hash `f3c13a55f0349720`.

The vivo I2019/iQOO 9 SE running Android 14/API 34 on ARM64 passed all nine
final tests in 57.974 seconds, with an empty Android crash buffer. The user
completed the hands-on two-book flow: Alpha and Beta imported with distinct
content, navigation and Library return worked, duplicate Alpha did not add a
catalog entry, per-book positions resumed, Alpha removal retained Beta, and
the corrected duplicate Alpha workflow returned to the exact saved page.

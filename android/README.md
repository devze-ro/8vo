# 8vo Android

This directory is the Android application root. Ports 0-7 are accepted. Port 8
is the corrected structural-navigation foundation against Reader0 `0.7.0-dev` /
public API 7 at `5fe949d88258cd96884c44b69e4f4ab6f27dc394`.
Port 9 is the API 36 emulator- and API 34 physical-device-validated bounded
in-book-search candidate on the same exact dependency closure.
Pre-correction Port 8 automation remains predecessor evidence; the corrected
8vo source has completed its final API 36 emulator and API 34 iQOO,
external-restart, and 130%-text/disabled-animation automation. Controlled
real-book review closes the six reported defects. The first bounded Navigation
polish slice now passes its API 36 emulator gates; audible TalkBack, the
remaining Appearance/chrome/Library polish, and user subjective/manual
acceptance remain pending. A 2026-08-05 spacing,
default-size, and Library-gutter refinement has completed its API 36 emulator
and API 34 physical gates; hands-on review remains pending.
Port 9 passes the exact dependency/architecture guards, strict Windows build,
seven public smokes, both real-book desktop search smokes, dual-ABI Android
build, five focused search tests, the API 36 ordinary matrix 90/90, the selected
130%-text/reduced-motion matrix 27/27, confirmed-force-stop restart, empty crash
review, and Paper/Warm dark/High contrast manual visual review. On the iQOO,
focused search passed 5/5, the ordinary matrix 90/90, restart, and the selected
matrix 27/27; representative-book search/jump/Next/page-emphasis review also
passed. Physical TalkBack/touch exploration of the bounded Find surface also
passes; broader whole-app screen-reader and hands-on review remain pending.

The coordinated Port 9 physical run restored font, window, transition, and
animator scales explicitly to their original `1.0` values and verified live
vivo SystemUI animation through 74 rendered frames. Its crash buffer was empty.
All 26 original app files (4,751,505 bytes) restored byte-exact with archive
SHA-256 `10E3C2001C569D4CEA6AB692536AD905AD747DACA483BEEA6E0C9B7015A5E0E3`
and manifest SHA-256
`C3D0DF15FD831D5BECE7A32F13103429714AA2E20972022AAC28539CCD28DA6E`.

With TalkBack 17.0.1 enabled, Google TTS produced spoken output and the physical
user explored the Find title, query, controls, status, current/ordinary results,
activation, Next, Back, open/close announcements, and focus return without
reporting an issue. Cleanup restored TalkBack off, no enabled accessibility
service, touch exploration off, the exact original notification-permission
state, rotation off, original music/TTS volumes, font and animation scales at
`1.0`, and the same byte-exact 8vo app-data backup.

Port 10 is the merged Reader0-authoritative text-selection and Copy baseline on
the same dependency closure. The exact guards, architecture audit, strict
Windows build, seven public smokes, and dual-ABI Android build pass. On API 36
and API 34 focused selection, ordinary, restart, actual
130%-system-text/reduced-motion, empty-crash, and physical/visual gates pass.
The iQOO final measurements are 5/5 focused in 9.667 seconds, 95/95 ordinary in
204.316 seconds, and 32/32 correctly configured large-text/reduced-motion in
34.958 seconds. Same-page long press, handles, Copy, Back, and TalkBack 17.0.1
Select/Copy/Clear actions pass hands-on review. The follow-up now continues
either handle across successfully presented pages inside the active Reader0
spine, repeats after edge dwell, exposes TalkBack Previous/Next extension, and
stops visibly at a chapter boundary. It also supplies a native drag loupe whose
successfully presented content updates continuously without flicker and centers
the active text row. Its API 36 emulator and API 34 physical gates pass, closing
the bounded same-spine selection launch blocker.

For the follow-up, API 36 passes 10/10 focused selection in 73.180 seconds,
100/100 ordinary in 490.402 seconds, external restart seed/confirmed-force-stop/
verify, and 37/37 at actual 130% system text with animations disabled in
101.257 seconds. Paper,
Warm dark, and High Contrast cross-page captures, strict/public smokes, dual-ABI
builds, and empty-crash review pass. Emulator font/animation settings restored
exactly. One unfiltered 102-test diagnostic incorrectly included the external
restart verifier without its seed and is excluded; the explicitly filtered
100/100 matrix is authoritative.

On the iQOO, the follow-up passes 10/10 focused selection in 16.799 seconds,
100/100 ordinary in 178.812 seconds, external confirmed-force-stop restart, and
37/37 selected tests in 39.059 seconds at 130% system text with normal motion
retained. Coordinated touch accepts multi-page handles plus the continuously
updating, flicker-free, row-centered loupe. The user confirmed that TalkBack
exposed Select text, both Previous/Next page-extension actions, and Copy selected
text without a reported issue.

The iQOO began with stored animation scales at `1.0` while Recents motion was
still absent. Republishing the values did not help; restarting Launcher did.
Every cleanup restored the scales explicitly, restarted Launcher, and the final
probe rendered 47 Launcher and 74 SystemUI frames. TalkBack is off, secure
settings match baseline exactly, audio/font/rotation/notification state is
restored, and the crash buffer is empty. After the follow-up and TalkBack
review, all 26 original files (4,751,505 bytes) restored byte-exact with archive
SHA-256
`1EF189A765D02321E1A9DC2203CF69B4F90111A9369D0AA6D585D0592DB46DBE`
and manifest SHA-256
`94A15EE1CCAAC59833EB0647887A55F3FF441FBD8DF4958B24537E8E6EB59B74`.

The Port 11 candidate retains Port 7's premium appearance, semantic reflow,
borderless host-composited chrome, first-frame coverage, and custom-reader
accessibility bridge plus Ports 8-10 navigation, search, and selection. Port 11
now includes the accepted local bookmark slice and an emulator- and physical-
qualified local multi-color highlight slice. The local note/draft/conflict
slice and its revised editor-inset/reader-marker follow-up pass API 36 and API
34 automation. The inset, marker appearance, and marker-tap-to-editor behavior
are physically accepted. Google Drive remains disconnected:

The fourth local slice adds the distinct actor-neutral `O1AP` annotation
container and hardened offline join without a provider, account, network
permission, worker, or UI change. `O1AP` is capped at 16 MiB minus 44 bytes so
every accepted snapshot fits the adopted 16 MiB private `O1AN` wrapper. Two
independently generated test-only goldens lock portable IDs, Reader0 byte
anchors, colors, notes, deletes, multi-actor contexts, concurrent heads,
Unicode, wide counters, and CRC encoding. On API 36 x86_64, the portable suite
passes 8/8, existing store 8/8, full note integration 3/3, ordinary regression
127/127, external restart, exact-limit stress on a 192 MiB heap, and an empty
crash buffer. The production APK contains no golden assets. The connected iQOO
was not touched for this backend-only slice, and Drive remains disconnected.

The fifth backend-only slice adds one product-owned, caller-driven annotation
sync coordinator and the private atomic `O1AS` retry/review file. It stages
exact bounded `O1AP`, requires digest-bound first-merge review, durably gates
remote recreation, retains join-limit input, remembers visible errors, and
recovers conditional create/replace races and uncertain writes without a
clock, thread, provider interface, account, network permission, worker, or UI.
API 36 x86_64 passes the seven coordinator tests together in 56.295 seconds,
the exact 16 MiB-minus-44 remote snapshot alone in 56.531 seconds on the 192
MiB heap, portable/store/note regressions 8/8, 8/8, and 3/3, and all 134
ordinary tests in 264.678 seconds. The crash buffer is empty. Only
`emulator-5554` was addressed; the iQOO was never targeted and was disconnected
before the final matrix. Google Drive remains disconnected.

For notes, the exact dependency and architecture guards plus the dual-ABI build
pass. The marker-tap follow-up passes the revised API 36 13/13 focused
annotation/draft/note tests in 19.071 seconds and all 119 ordinary tests in
317.113 seconds. The focused flow discovers the newly rendered marker pixels,
taps them through the real reader touch path, and verifies the exact durable
note opens without weakening draft recovery. The separate seed/
confirmed-force-stop/fresh-process driver recovers the exact note body,
Reader0 range, and marker projection in 6.409 and 3.811 seconds. At actual 130%
system text with reduced motion, 32/32 configuration-compatible tests pass in
80.645 seconds; at 100% text, the five fixed-pagination tests pass in 26.582
seconds. API 34 passes the marker-tap focus 13/13 in 9.223 seconds and the clean
ordinary rerun 119/119 in 235.657 seconds; one preceding diagnostic window
capture failure passed 1/1 in isolation. The current candidate passes external
restart in 1.948/1.115 seconds; the prior candidate passed 32/32 at 130% text in
75.601 seconds, and 5/5 fixed pagination in 17.714 seconds with normal motion.
Both crash buffers are empty. Font and every animation scale are restored to
`1.0`; the UI0 editor padding, exact-anchor marker appearance, and
marker-tap-to-editor behavior are physically accepted. The protected baseline
was then restored exactly: both saved APK hashes match and all 32 private files
(4,996,158 bytes) match the pretest manifest byte-for-byte.

The exact dependency guard, architecture audit, and dual-ABI Android build
pass. On API 36, the highlight/store focus is 10/10, the combined annotation,
selection, and accessibility regression is 24/24, and the clean-runtime ordinary
matrix is 113/113 in 193.044 seconds. The separate seed/confirmed-force-stop/
fresh-process driver recovers the exact bookmark plus Orange highlight spine,
UTF-8 byte range, and color. The configuration-compatible matrix passes 29/29
in 44.325 seconds at actual 130% system text with animations disabled, and the
five fixed-pagination tests pass separately in 14.909 seconds at 100% text with
animations disabled. The crash buffer is empty; font plus window, transition,
and animator scales were restored explicitly to `1.0`.

On the API 34 iQOO, the second slice passes 10/10 focused tests in 7.088
seconds, the clean ordinary rerun 113/113 in 234.374 seconds, external seed/
confirmed-force-stop/fresh-process restart in 2.092 and 1.172 seconds, 29/29 at
130% system text in 66.954 seconds, and 5/5 fixed-pagination tests at 100% text
in 17.865 seconds. Normal motion remained `1.0`/`1.0`/`1.0`. Four existing UI
timing checks from an earlier diagnostic passed 4/4 in isolation and in the
clean complete rerun above.

The user accepts Yellow highlight creation/rendering, the combined workspace
excerpt and named color, Pink recolor, Go to, removal, and restart behavior.
With TalkBack 17.0.1, the user also accepts all four named selection actions,
the success announcement, heading/count/color/excerpt, four recolor controls,
Go to, Remove, and Done without a reported issue. Cleanup restored TalkBack
off, normal text/motion/rotation/audio, verified 123 Launcher and 107 SystemUI
frames, and left no app/test process. All 32 captured private files match path,
length, and content; the 26 persistent files (4,751,505 bytes) reproduce the
pretest archive SHA-256
`9118B960B212FC67EDC70152314341D8EDEDE0851045335D4B11CF23D79D3699`;
the canonical manifest SHA-256 is
`35368BF22698B1C9AA64FB940512C414AA83EFA50B6934B7248DABC71233719B`.
Both original APKs also match their pretest SHA-256 values. One final unrelated
crash entry was attributed to UID 10220 (`com.myairtelapp`) invoking `dmesg`,
not 8vo; after clearing it the buffer remained empty, while 8vo exit history
contained only expected force stops.

- `OctavoActivity` owns Android lifecycle, the library surface, and the
  `ACTION_OPEN_DOCUMENT` picker. Port 7 also keeps settings surfaces, system
  bars, the synchronous target-theme reader-entry cover, native chrome and its
  SurfaceView scale/translation composition, and successfully presented
  appearance persistence in the Activity host. Ordinary book entry starts with
  chrome hidden; Activity recreation alone may restore its transient visibility.
  The Library installs its 16dp outer gutter synchronously, then adds any real
  system left/right inset delivered by the window.
- `OctavoLibraryStore` owns the bounded versioned catalog, streams distinct
  imports into SHA-256-keyed app-private storage, atomically saves per-book
  successfully presented positions, and removes only managed copies.
- `OctavoAppearance` is the immutable global-only preference contract:
  theme, system family, size, spacing, width, supported alignment,
  publisher-color policy, and reduced motion.
- `OctavoDesignTokens` owns the six independently tuned product palettes,
  semantic UI0 role mapping, spacing, shape, 48dp touch target, 24dp icon, and
  restrained-motion values.
- `OctavoAppearanceStore` owns the bounded version-3, CRC32-protected global
  `<files>/port7/appearance.v1` record. New, missing, or corrupt state uses
  16sp. Loading a valid version-1 18sp default, version-2 16sp default, or
  transitional version-2 18sp value returns a 16sp in-memory appearance while
  preserving every other valid field, marks migration pending, and leaves the
  version-1/version-2 bytes untouched. The transitional value is a bounded
  pre-origin ambiguity: an inherited version-1 18sp default could be republished
  by the version-2 writer after a non-font change. That inherited state cannot
  be distinguished from an explicit v2/18 choice, so the bounded policy migrates
  every v2/18 record. Version-2 21/24/28sp choices and every valid version-3
  record, including 14sp, remain exact; impossible old-schema 14sp is invalid.
  Version 3 is
  published only after the first successfully accepted reader frame. The store
  requires descriptor sync and a same-directory atomic replace; failure
  preserves the old bytes, keeps migration pending, reports a visible
  appearance-save failure, and retries after a later successful presentation.
- `OctavoAppearancePanel` owns the scrollable, host-themed reader-preference
  sheet, complete option labels, 48dp targets, and initial modal focus.
- `OctavoFixture` stages the packaged deterministic sample, Alpha, and Beta
  EPUBs under app-private files for the application and instrumentation.
- `OctavoTypography` acquires the selected Android generic serif or sans-serif
  family at the selected sp/spacing and publishes a bounded caller-owned
  regular/bold/italic/bold-italic sparse alpha atlas with sorted codepoint and
  advance tables. Atlas version 2 contains 233 entries: printable ASCII,
  U+00A0..U+00FF, and 42 curated publication characters. Its immutable one-entry
  cache reuses the atlas for an unchanged family, resolved pixel size, and
  spacing tuple. Port 7 bundles no font asset.
- `OctavoSurfaceView` owns atlas creation and coalesced appearance requests,
  forwards lifecycle, surface, inset, and raw touch values, schedules native
  presentations, and exposes the Android accessibility provider. It classifies
  horizontal-dominant swipes, cancels the native tap before a swipe move, and
  clears gesture state at pause/resume, surface replacement/geometry changes,
  and teardown. Taps, swipes, keyboard, and accessibility moves share the native
  successful-presentation gate. The Activity composes visible chrome by
  uniformly scaling/translating this canonical full-viewport page; hidden chrome
  restores its identity transform. Only successfully presented Reader0
  locations and appearances become durable. Native reader geometry reserves
  two base vertical insets above content and one below it; the reduced canonical
  height reflows through Reader0, while publisher margins and widow/orphan carries
  remain authoritative.
- `OctavoReaderImageBridge` owns serial Android `BitmapFactory` decode for the
  bounded frame-image packet. A frame exposes at most 16 descriptors; encoded
  resource bytes are capped at 16 MiB, and decoded input is capped at 4096
  pixels per dimension and 8 million pixels. Reader0 preflights each selected
  ZIP entry against the remaining encoded-byte budget before output allocation
  or entry decompression. Only that distinct limit result becomes the non-null
  empty-array `CacheFull` sentinel; missing, corrupt, or extraction-failed
  resources remain isolated `DecodeFailed` outcomes. The bridge publishes
  loaded or terminal failure status back to native before presentation. Each
  presentation also caps cumulative encoded input at 16 MiB and cumulative
  decoded input at 8,388,608 pixels. Exhausting either budget marks that
  resource and every remaining unavailable frame resource terminal
  `CacheFull`; the corrected API 36 image/prepared-frame matrix and API 34
  physical automation validate this contract. Controlled real-book map-leaf
  presentation passed; broader media-fidelity review remains pending.
- `OctavoReaderAccessibilityProvider` maps bounded Readerview0 semantics and
  host page content into stable page, previous, next, and progress virtual
  nodes with focus, hover, click, and scroll actions. The host routes real
  Tab/Shift+Tab input through the named native/virtual chain, keeps progress
  read-only, and recovers directionally after an explicit focus clear without
  exposing the raw Surface or chrome containers as blank stops.
- `OctavoNavigation` bounded-copies and validates the native structural snapshot.
  `OctavoNavigationPanel` presents Contents and Go-to as an Android hierarchy
  with current-section state, destination progress, input failures, history,
  focus, and 48dp actions; it does not interpret EPUB data.
- `OctavoSearch` bounded-copies and validates a versioned Reader0 search packet.
  `OctavoSearchPanel` owns native query/IME behavior, status, retained/total
  disclosure, result rows, focus, TalkBack wording, and 48dp actions. It never
  interprets EPUB data or derives destinations. Native query, clear, and result
  jumps retain their prior result/location/history state unless the exact
  candidate presentation succeeds.
- `OctavoSelection` validates a versioned bounded snapshot of Reader0's active
  `DocSelection`. Native hit testing reuses the rendered styled-row, font-
  advance, alignment, and justification geometry, and selection mutation joins
  the successful-presentation gate. Android owns long press, 48dp handle hit
  regions, contextual Copy, clipboard failure, selection-first system Back,
  and virtual-page Select/Copy/Clear actions. Java never stores EPUB offsets or
  interprets publication text.
- `OctavoProgressStore` owns a separate versioned, checksummed global progress-
  display record. Same-directory synchronization and atomic replacement preserve
  prior bytes on failure. The selected Chapter, Page, Location, or Percentage
  mode becomes durable only after its matching frame is presented.
- `OctavoAnnotationStore` owns the bounded, digest-keyed, versioned annotation
  envelope at `<files>/port11/annotations.v1`. Reader0 spine/UTF-8 byte anchors,
  stable record/mutation/tombstone identities, strict atomic publication,
  corruption/future-version recovery, and deterministic causal merge remain
  product state. Managed-book removal does not erase annotations.
- `OctavoAnnotationSyncStore` and `OctavoAnnotationSyncCoordinator` own the
  disconnected annotation-only coordination boundary. The store atomically
  retains binding, converged/in-flight digests, conservative presence history,
  durable attention/review phase, and a staged exact `O1AP`; the caller-driven
  coordinator yields only bounded read or conditional create/replace commands.
  There is no provider adapter, account SDK, hidden worker, scheduler, or
  network request in this slice.
- `OctavoNoteDraftStore` owns one local-only, 8 KiB, checksummed incomplete-note
  envelope at `<files>/port11/note-draft.v1`. It atomically autosaves exact
  editor text for crash recovery but never becomes a portable mutation or
  future sync input; Save/Cancel remain product actions.
- `OctavoBookmarksPanel` is the first native Android annotation workspace. It
  now presents current-book bookmark, highlight, and candidate note sections,
  named color swatches, bounded note editing and every retained conflict body,
  Go to/Edit/recolor/Remove requests, and bounded status/conflict context;
  persistence and Reader0 presentation success remain authoritative before
  rows, selections, or panels claim completion. The note field uses UI0's
  text-input inset metrics on all appearances.
- The native host owns a bounded copied highlight projection. It compares only
  Reader0 spine/UTF-8 byte ranges, resolves semantic colors from product tokens,
  and publishes projection generations through the existing frame gate.
- The native host separately owns a bounded copied note-marker projection.
  It positions a theme-accent page glyph after the exact Reader0 range end,
  clamps spanning ranges to the visible page boundary, changes no text layout,
  and presentation-gates add/remove success and selection clearing. Its
  48dp-class native hit target is enabled only for the matching presented Java
  record projection; a completed tap opens that durable note in the existing
  editor without overwriting a different unsaved draft.
- `OctavoNative` is the explicit Java/JNI boundary.
- `octavo_android_port7_build.c` source-consumes the exact Ground0, Reader0,
  UI0, and Readerview0 revisions once. The current Reader0 boundary is
  `0.7.0-dev` / public API 7; its exact local commit is recorded under
  `vendor/reader0_dependency`. The historical Port 7 unity filename is retained
  because it still compiles each public package exactly once.
- `code/octavo_reader_justification.h` provides one allocation-free publisher
  row-spacing plan to the Windows and Android raster paths. Reader0 stores the
  authoritative `soft_wrapped` provenance in each canonical styled row: measured
  space/em-dash wraps are true, while final/hard-line and image boundaries are
  false. Both hosts consume that field rather than reclassifying bytes. Eligible
  ordinary non-final publisher-justified prose fills the available measure with
  overflow-safe widened arithmetic; layout-only trailing whitespace is excluded
  from measurement/drawing while a visible em dash remains ink. Ragged right and
  intentional hard lines retain natural spacing. The stable Windows theme
  catalog is shared 8vo code; Android palettes remain independently tuned host
  policy.
- `octavo_android_jni.c` owns one native application/reader/view/typography
  state per surface, validates the appearance and exact 26-role palette,
  opens/restores/reflows through Reader0's public APIs, gives Reader0 the exact
  atlas advances for wrapping, paints canonical styled Reader0 rows against one
  borderless full native viewport through Readerview0's distraction-free
  projection, classifies left/center/right taps, accepts page moves from the
  host's swipe/keyboard/accessibility paths, gates every page/reflow/appearance
  generation on presentation, performs bounded sparse-codepoint lookup with
  missing-glyph diagnostics, and owns its `ANativeWindow`.
  Port 8's included structural adapter consumes Reader0 API 7 destination,
  location, percentage, meaningful-page, and bounded-history operations. A jump
  or history move remains provisional until the expected target is contained in
  a successfully posted frame; only then may history or the durable reading
  position advance. History is bounded to the current session and is not
  serialized. Chapter Go-to calls Reader0 directly: the shared resolver prefers
  only tokenized, exact namespace-qualified `epub:type="... chapter ..."`
  semantics, then accepts a complete contiguous numbered-label model, and otherwise fails
  closed. Android does not map a chapter number to a Contents row.
- Reader0 supplies contained synthetic anchors for image-only canonical pages
  and uses canonical byte-zero pagination for predecessor spines at or below
  16 KiB. Android treats images as Reader0 frame resources rather than parsing
  EPUB markup. Native owns a deterministic 32-entry/32-MiB ARGB LRU, pins every
  resource named by the current frame, aspect-fits image-only and in-flow media,
  paints a theme-safe terminal fallback, and advances all vertical traversals
  by Reader0's `visual_units` height so text cannot overlap an image.
- Static candidate preparation is a single-build transaction. The first image
  snapshot records a token over the exact native window, dimensions, lifecycle,
  surface, mutation, Reader0 layout/page/frame, and location-cache identity.
  The verification snapshot and native present reuse that frame; a stale token
  rejects publication. Bounded forced failures retain it for retry, and only an
  accepted presentation and commit consume it.
- The corrected inset policy retains one base reserve below the content and
  adds one full base reserve above it, yielding two top reserves and one bottom
  reserve. The canonical content height shrinks accordingly so Reader0 reflows;
  opening or closing navigation chrome still cannot repaginate the book.

There is no process-global mutable application state. The Java view holds the
native handle and destroys it when the Activity is destroyed.

## Product direction

Port 7 is the accepted premium appearance foundation. Its Reader0 API 6,
companion re10,
exact 8vo guard/audit, clean dual-ABI Android build, strict Windows/public-smoke,
API 36 emulator, physical iQOO, ProcessRestart, 130% accessibility, crash, and
byte-exact backup/restore gates passed. The accepted final ordered matrix
passed 36/36 on both targets: appearance store 9, appearance 15, navigation 5,
library 5, accessibility 1, and bootstrap 1. The emulator took 510.019 seconds
of instrumentation time and the iQOO took 108.467 seconds. Every 33-test API 6
run remains historical evidence for an earlier binary. Port 8 adds structural
navigation without treating the accepted Port 7 measurements as new validation.
Its earlier dependency, build, emulator, accessibility, lifecycle, performance,
and physical-device automation results predate the six corrective changes and
prepared-frame hardening. The corrected candidate's emulator and physical-
device gates pass; audible TalkBack, UI polish, and user subjective/manual
acceptance remain pending.
Neither milestone is the end-state Android experience. The product target is
a premium, local-first reader for user-owned
books that reaches Kindle-class reading, navigation, search, annotation,
library, accessibility, and interaction quality while using a distinct 8vo
design and user-controlled storage.

The public product contract is recorded in
[`../docs/android_product_vision.md`](../docs/android_product_vision.md), the
living capability inventory in
[`../docs/android_feature_parity.md`](../docs/android_feature_parity.md), and
the bounded delivery sequence in
[`../docs/android_roadmap.md`](../docs/android_roadmap.md). The current milestone
contract is [`../docs/android_port11.md`](../docs/android_port11.md). The
selection predecessor is
[`../docs/android_port10.md`](../docs/android_port10.md). Search and
structural-navigation predecessor contracts are in
[`../docs/android_port9.md`](../docs/android_port9.md) and
[`../docs/android_port8.md`](../docs/android_port8.md).

The accepted Port 7 boundary, objective closure, historical evidence, and
remaining broader product-review items are recorded in
[`../docs/android_port7.md`](../docs/android_port7.md).

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

The ordinary connected suite excludes the externally orchestrated process-
restart probe and may uninstall its APKs when the Gradle task completes. From
the repository root, explicitly install both generated APKs before running the
probe; override the serial when the selected target is not the default
emulator:

```powershell
adb -s emulator-5554 install -r android\app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 install -r -t android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android_port7_process_restart.ps1 -Serial emulator-5554
```

The accepted Port 6 suite verifies native/shared version identity, exact
staged EPUB bytes, canonical Reader0 text/page evidence, a clean Readerview0
frame, proportional `i`/`W` advances, all four rasterized styles, readable
size/line metrics,
document-picker intent policy, two deterministic digest-keyed imports,
Reader0-validated titles, duplicate deduplication, exact per-book page/text/
progress/byte resume, managed-copy removal, Port 5 migration, corrupt-catalog
fallback, wide handset geometry, actual ink pixels, adjacent and cross-section
navigation, both boundary no-ops, rapid-tap presentation gating, private paths,
pause/resume, surface replacement, and Activity recreation. It rejects every
recorded native, catalog, presentation, restore, navigation, or removal
failure.

Port 7 keeps the Port 6 behavioral regression surface and the original
`<= 64` deterministic-sample page-count guard with its 14sp default and
full-viewport geometry. It adds deterministic coverage for all six palette
identities, all six font sizes, migration of version-1 18sp, version-2 16sp,
and transitional version-2 18sp to 14sp without changing any other valid field,
preservation of version-2 21/24/28sp and every valid version-3 choice, rejection
of impossible legacy 14sp records, non-mutating v1/v2 load, publication only
after the first successfully accepted reader frame, and a visibly reported
failed atomic publication that remains pending for a later successful-
presentation retry. It also covers preference extremes, global-store
missing/corrupt/save-failure behavior, rapid-request coalescing, presentation-
gated appearance generations, semantic-anchor containment after reflow, and
custom accessibility nodes/actions. Reader coverage also proves the bounded
sparse publication atlas and missing-glyph diagnostic, full-measure ordinary
non-final justified soft wraps without a trailing-delimiter gap, natural
spacing for publisher hard lines and final rows, the borderless full-viewport
page, hidden ordinary entry, visible-chrome
scale/translation versus hidden identity, one-tap `Aa`, no visible
Previous/Next controls, bidirectional swipe navigation, lifecycle gesture
cancellation, no chrome-driven repagination or page mutation, transition-
gesture cancellation, target-theme entry coverage, compact and large
viewports, rotation, recreation, lifecycle, surface replacement, bounded retry
recovery, and visible surface-acquisition failure.

Accessibility coverage injects real Tab/Shift+Tab keys through visible and
hidden chrome, verifies that visible chrome has Library and Reader appearance
but no Previous/Next button, preserves hidden virtual previous/next/progress
actions, exercises Page Up/Page Down and D-pad navigation, and covers explicit
focus clearing, disabled end-of-book actions, and the hide-fade boundary. It
also verifies activation through the existing successful-presentation gate and
the read-only progress stop. Bounded pixel evidence samples each theme and
typography change and the tested dark-transition phases.

### Port 8 validation status

The corrected source consumes Reader0 `0.7.0-dev` / public API 7 at
`5fe949d88258cd96884c44b69e4f4ab6f27dc394`. That Reader0 revision passed its
exact dependency/API audit, MSVC `/W4 /WX` build, core smoke, and host smoke.
Companion re10 at `b1c264f027c90bec480677bfeadfa5e0728776a8`
passed strict product/qualification builds and `--document_engine_smoke` with
four anchors, final spine 3, and hash `f3c13a55f0349720`.
The corrected exact-pin strict Windows 8vo build and all seven public smokes
also pass in 19.6 seconds wall. Stable hashes are host `cd460506f219d652`, Reader View
`e29cfd3afeea51a1`, visual `e6848393c4dc0b95`, cover
`a2fabe96a148a6a4`, and inline image `5b536d3a66934ec8`.

The corrected 8vo source closes the reported MAPS target, path-dependent prose
pagination, intermittent sparse Dramatis Personae page, image-only map-page,
Chapter Go-to, and top-padding defects. It adds bounded image decode/cache/fit/
flow and prepared-frame single-build/retry semantics. The corrected-source
dual-ABI build, exact dependency guard, emulator matrix, process-restart, and
accessibility/reduced-motion probes now pass. The gate included a 5/5 focused
presentation-deferral stress run, an 11/11 image/prepared-frame matrix, and the
ordinary API 36 suite at 67/67 in 468.395 seconds of XML time (494.0 seconds
wall). The external confirmed-force-stop restore passed, as did 15/15 at 130%
system text with animations disabled in 80.538 seconds. Emulator settings were
restored exactly; the crash buffer was empty and process exit history contained
only expected `USER REQUESTED` force stops, with no crash or ANR.

The 2026-08-06 bounded Navigation-polish slice keeps the same exact dependency
pins and Reader0 semantics while replacing screen-local theme mirroring with a
versioned UI0 API 91 snapshot and product-neutral native-Android adapter. The
architecture/dependency guards and dual-ABI debug/test build pass. On the API
36 x86_64 emulator, the focused UI0/Navigation suite passed 26/26 in 1.997
seconds, the ordinary matrix passed 85/85 in 245.948 seconds, the confirmed-
force-stop restart driver passed, and the 130%-text/disabled-animation matrix
passed 22/22 in 22.543 seconds. The crash buffer was empty and exit history
contained only expected `USER REQUESTED` force stops. Emulator settings restored
exactly to font scale `1.0`, window and transition scales `1.0`, and an absent
animator key. On API 26, the corresponding results are 26/26 in 1.449 seconds,
85/85 in 180.560 seconds, restart pass, and 22/22 at 130% text with animations
disabled in 22.217 seconds; its crash buffer was empty. The API 26 run renders
the framework cursor and all three selection handles with the fixed `#8B7560`
compatibility accent and proves at least 3.5:1 contrast on all six UI0-derived
input surfaces.

The desktop light/dark Contents captures and live API 36 Paper, Dusk, and High
Contrast captures now accept bounded portrait-phone Navigation look-and-feel
parity. Non-High Contrast current rails follow UI0 `AccentHover`; High Contrast
keeps its blue identity and uses contrast-safe selected-row text. Pixel-for-
pixel desktop rendering, full-app parity, large-viewport/RTL review, audible
TalkBack, alternate-input review, strict UI0 role-to-face metrics, subjective
motion/touch review, and the remaining Appearance/chrome/Library polish remain
open. The physical phone was not used.

The 2026-08-05 visual refinement passed the exact dependency/architecture
guards, dual-ABI debug/test build, a focused 45/45 matrix, and the ordinary
API 36 matrix at 67/67 in 250.953 seconds. Its external restart driver and
15/15 130%-text/disabled-animation matrix also pass; font and animation
settings restored exactly and the app crash buffer remained empty. On the
1080px-wide emulator Library, the title begins at x=42 and the Add EPUB button
ends at x=1038, proving the intended 16dp outer gutter on both sides. The API 34
physical repeat also passed 67/67 in 167.136 seconds, the confirmed-force-stop
restart driver, and 15/15 at 130% system text with animations disabled in
14.625 seconds. Its settings database restored exactly to font scale `1.0`,
window and transition scales `1.0`, and the previously absent animator-scale
key. A later hands-on check found that some vivo SystemUI animations still
behaved as disabled; explicitly setting the animator duration scale to `1.0`
repaired the device. Future physical reduced-motion runs must be coordinated
with the user; device-wide animations must not be disabled outside that test
window. Restore window, transition, and animator scales explicitly to `1.0`,
then verify visible behavior as well as stored values. The crash buffer
remained empty, and exit history contained only expected force stops. On the
1080px-wide iQOO Library, the corresponding bounds are x=44 and x=1036. A fresh
reader capture places first ink 85px below the app content edge while retaining
the exact one-base canonical bottom reserve. The original 26 files and
4,751,505 bytes were restored byte-exact; archive SHA-256
`1678B1DC0356FC84CF48CCEFA3508C210B23D736327CBFAC71E0EE054AB9FC3F` and
manifest SHA-256
`9EAF4BC7754F53F1FD546C8447E9D474F41BF5988B6071430CB3D1163AE5B0CC`
match the pre-test snapshot.

On the vivo I2019/iQOO 9 SE, Android 14/API 34 ARM64, the corrected ordinary
matrix passed 67/67 in 170.878 seconds of instrumentation time (171.449 seconds
wall). The external confirmed-force-stop restore passed with a 1.987-second
seed, 1.135-second verification, and 4.908-second wall time. At 130% system text
with system animations disabled, the focused matrix passed 15/15 in 16.185
seconds of instrumentation time (18.591 seconds wall). The settings database
restored exactly to font scale `1.0`, window and transition scales `1.0`, and
the previously absent animator-scale key. The crash buffer was empty; exit
history contained only expected `USER REQUESTED` force stops and `PACKAGE
UPDATED`, with no crash or ANR.

Controlled review on the real *Gardens of the Moon* EPUB opened the first MAPS
Contents destination without error and presented three genuine map leaves
before Dramatis Personae. Chapter `1` and `2` resolved to Chapter One and Chapter
Two; Return restored the prior prose origin exactly. From Chapter One, a jump to
Prologue followed by five deliberately waited reverse turns produced the
expected intermediate pages and the full reported Dramatis Personae page
through its expected continuation, with no sparse-page recurrence. The reported
prose pages were full, the increased top breathing room remained coherent, and
no visible bright or black transition was observed. After review, the original
26 app files and 4,751,505 payload bytes were restored byte-exact. The original
archive SHA-256 is
`52C4C27FA8E8D4C268950D6AB918D72DA130864D94556945BD815B1D12A901F2`; the
manifest SHA-256 is
`A060016D369EC0E8902070A10206E09D82BC27BBACEB387F872F2C669F5D0B94`.
Audible TalkBack, the remaining bounded polish, and user subjective/manual
acceptance remain pending. No pushed or merged 8vo revision or APK hash is
claimed here.

#### Pre-correction Port 8 evidence

Before those corrections, the exact dependency guards and dual-ABI debug/test
build passed. The API 36 x86_64 ordinary suite passed 56/56 in 146.291 seconds;
the separate confirmed-force-stop restart probe and a 15/15 130%-system-text/
disabled-animation pass also succeeded. Crash buffers were empty. A
representative synthetic fresh restore was accepted in 169ms end-to-end with
129ms in native stages and zero missing glyphs.

On the vivo I2019/iQOO 9 SE, Android 14/API 34 ARM64, that ordinary suite passed
56/56 in 121.608 seconds of instrumentation time (122.475 seconds wall). The
external seed/confirmed-force-stop/fresh-process probe passed in 4.593 seconds.
At 130% system text with system animations disabled, the focused matrix passed
15/15 in 15.580 seconds (17.244 seconds wall). The settings database restored
exactly to font scale `1.0`, window and transition scales `1.0`, and the
previously absent animator-scale key; the later vivo behavior caveat above
applies. The crash buffer was empty. A staged restore reproduced all 26 durable
files and 4,751,505 payload bytes by path, length, and SHA-256.
Restored cold Library launch took 294ms. On the private real book, the Contents
hierarchy marked Chapter One current at 154/9549 (1%), a controlled jump
presented Chapter Two at 687/9549 (7%), Return restored the origin, and Forward
became available only after that accepted return. The durable anchor remained
exactly `17:0`; only expected last-opened bookkeeping changed. All four Go-to
forms were exposed.

The pre-correction Windows 8vo build and all seven public smokes also passed in
17.6 seconds.
These 56/56, 15/15, timing, restore, Windows, and private-book results remain
pre-correction evidence. They are not transferred to the corrected source.
Audible TalkBack/touch exploration, subjective transition/reduced-motion/touch-
quality review, UI polish, and user manual acceptance remain pending, so Port 8
is not yet accepted.

The accepted Port 7 source consumed Reader0 `0.6.0-dev` / public API 6 at
`59e9efdaca17b316aa2b1f5a7be0cbdebf5e4c26`. Reader0 and companion re10 at
`5830f401750f7631131c4bc9c16d7235b88758a0` passed their final shared-consumer
gates. Exact 8vo guards/audit, strict Windows, all seven public smokes, and a
clean dual-ABI Gradle build passed; the Android build took 1 minute 54.9 seconds.

The 3,516,438-byte main APK has SHA-256
`9B454B9086A5BBD68BC04CEE1D71AE0DDBCCA9036043E351983D38ACE3888BAF`; the
1,041,003-byte test APK has SHA-256
`27F16340E398EDB8815FC47758A3C8CACF8DED433832D2B71BB0AA3C78F29F6F`.

The final ordered matrix passed 36/36 on the API 36 emulator in 510.019 seconds
of instrumentation time (511.173 seconds wall) and 36/36 on the iQOO in 108.467
seconds (109.069 seconds wall): appearance store 9, appearance 15, navigation 5,
library 5, accessibility 1, and bootstrap 1. ProcessRestart, 130% accessibility,
emulator scale restoration, iQOO settings-database restoration, and empty
crash-buffer checks passed.

The fresh 9,141,760-byte pretest backup
(`245FA5B9B3E73E7DC9B1DFC5DCE1DDAEFE80F514C4DB9E8F3BA550280C7F9A1`) restored
all 63 files byte-exact. Restored cold Library start was 287ms. Controlled
imported-book Resume reported `first_ms=138`, 24ms total native stages, zero
missing glyphs, semantic location `1:14:1756`, and no visible reader controls.
The exact snapshot was restored again, and the phone was left on the Library.

The completed API 6 pre-closure source passed exact 8vo guards/audit, dual-ABI
debug/test builds, strict Windows, all seven public smokes, and an API 36 33/33
run in 170.299 seconds of instrumentation time (171.639 seconds wall). Its
restart, 130% accessibility, and crash-buffer checks passed. Its exact ignored
backup replay published a v3/14sp record from a transitional v2/18sp record,
and its iQOO six-class matrix passed 33/33 in 103.741 seconds of instrumentation
time (104.295 seconds wall). Focused justification, restart, 130% accessibility,
crash review, a 287ms restored cold-library start, and controlled 135ms Resume
with zero missing glyphs and hidden chrome also passed. These are historical
regression records because they predate non-mutating migration load, deferred
publication, retry-on-failure coverage, and the additional migration/reader-entry
regressions; they do not accept the current source.

The immediately preceding API 5 full-measure candidate passed the dual-ABI
Android debug build. On the API 36 x86_64 emulator, its authoritative ordered
suite passed 33/33 with zero failures, errors, or skips in 138.917 seconds of
XML test time: accessibility 1, appearance store 9, appearance 12, bootstrap 1,
library 5, and navigation 5. These results are historical evidence for the API
5 integration, not acceptance of the current API 6 consumer.

The API 5 emulator suite exercised its then-current deterministic schema-default
migration cases. A later audit superseded the claim that the ignored phone
backup itself was version 1: its top-level appearance is transitional version 2
at 18sp. The pre-closure API 6 historical migration result is recorded above. At
emulated iQOO geometry, 1080 x 2400
at 440dpi, the exact private ignored EPUB diagnostic sampled five eligible
ordinary non-final rows with three or four interior gaps. The preceding path
left 79--175px residuals; that API 5 path filled the exact 800/800 or 928/928
measures. The focused diagnostic passed 1/1 in 7.902 seconds, and no
private text or diagnostic fixture is tracked.

Reader0 API 5 commit `f41bd1c86cdcb1ef463ecdae0ec6d139f5355871` passed its exact guard,
dependency audit, MSVC `/W4 /WX` build, `--reader-core-smoke`, and
`--host-smoke`. The strict Windows 8vo build and all seven public smokes passed
against that boundary. Companion re10 branch
`android/port7-reader0-justification` at
`789de8410924cb184a0e7aa485bd27fc7a5a8ab4` advanced only its Reader0 pin,
retained re10's own exact UI0 and Readerview0 closure, and passed its strict
product/qualification `/W4 /WX` build plus four-anchor
`--document_engine_smoke` with final spine 3 and hash `f3c13a55f0349720`. The
Reader0 and re10 worktrees were clean.

The earlier post-feedback APK, before the full-measure and final
schema-default migration changes, passed 33/33 on the API 36 emulator in 96.223
seconds of XML time. Its separate restart and 130% system-text probes, crash-
buffer review, private-book punctuation/verse/chrome/swipe/reopen pixels, warm
reader-entry samples of 825, 566, 582, 563, and 595ms (median 582ms), and final
529ms screen-record sample passed under their recorded limits. Those results
are historical and did not accept the later final Port 7 API 6 source.

That same earlier APK passed the selected iQOO six-class matrix
33/33 in 104.855 seconds. Its restart, 130% system-text, crash-buffer,
punctuation/verse, hidden-entry, one-tap appearance, swipe restoration, and
reopen checks passed. Its first preserved-state open measured 140ms, controlled
warm reopens measured 121, 80, and 122ms (median 121ms), and the focused reopen
measured 102ms. These physical results are also historical.

The older borderless/performance binary's 27/27, 411ms ADB-tap, and native-only
220ms results and the reserved-geometry iQOO 23/23 record remain separate
superseded regression evidence. Port 7 intentionally remains a bounded
appearance foundation. Per-book
appearance overrides, a bundled cross-device-identical or embedded font, full
Unicode shaping, complete publication accessibility, cover/library expansion,
annotation search/export and concrete synchronization transport remain deferred.
Port 8 adds structural navigation; Port 9 adds bounded session-local in-book
search; Port 10 adds bounded word selection and Copy. Its local follow-up adds
same-spine cross-page continuation and closes that bounded selection launch
gate. Port 11 adds durable provider-neutral annotation storage, local bookmarks,
the local multi-color highlight workflow, and the local note/recovery candidate
plus disconnected annotation coordination. Its sixth independent local slice
now adds API 36/API 34-qualified bounded provider-neutral `O1RP` reading-position
lanes, private atomic `O1RS` decisions, strict Reader0 anchor/presentation
qualification, and a Kindle-style Go there/Stay here/Retry confirmation while
Drive remains disconnected. Both targets pass the 24/24 focused position
matrix, external force-stop recovery, and the 158/158 ordinary matrix with
empty crash/fatal buffers. The physical gate also closed touch-mode action
focus and restored the accepted APK/private-data baseline exactly. Durable
Unicode-aware indexing, annotation
search/export, concrete provider
transport, and the remaining portable record families remain deferred. Spatial
previews and advanced scrubbing also remain deferred. See
[`../docs/android_port11.md`](../docs/android_port11.md) for the current
annotation/storage/merge boundary,
[`../docs/android_port11_sync_coordinator.md`](../docs/android_port11_sync_coordinator.md)
for the disconnected annotation coordination boundary,
[`../docs/android_port11_position_sync.md`](../docs/android_port11_position_sync.md)
for the separate reading-position/confirmation boundary,
[`../docs/android_port10.md`](../docs/android_port10.md) for the selection
implementation and acceptance boundary,
[`../docs/android_port9.md`](../docs/android_port9.md) for the search predecessor,
[`../docs/android_port8.md`](../docs/android_port8.md) for the navigation
foundation,
[`../docs/android_port7.md`](../docs/android_port7.md) for the accepted appearance
milestone, and
[`../docs/android_port6.md`](../docs/android_port6.md) for the accepted
library milestone.

# 8vo Android

This directory is the Android application root. Ports 0-7 are accepted. Port 8
is the corrected structural-navigation candidate against Reader0 `0.7.0-dev` /
public API 7 at `5fe949d88258cd96884c44b69e4f4ab6f27dc394`.
Pre-correction Port 8 automation remains predecessor evidence; the corrected
8vo source has completed its final API 36 emulator and API 34 iQOO,
external-restart, and 130%-text/disabled-animation automation. Controlled
real-book review closes the six reported defects. Audible TalkBack, UI polish,
and user subjective/manual acceptance remain pending. The
candidate retains Port 7's premium appearance, semantic reflow, borderless
host-composited chrome, first-frame coverage, and custom-reader accessibility
bridge while adding a bounded Contents/Go-to/Return model:

- `OctavoActivity` owns Android lifecycle, the library surface, and the
  `ACTION_OPEN_DOCUMENT` picker. Port 7 also keeps settings surfaces, system
  bars, the synchronous target-theme reader-entry cover, native chrome and its
  SurfaceView scale/translation composition, and successfully presented
  appearance persistence in the Activity host. Ordinary book entry starts with
  chrome hidden; Activity recreation alone may restore its transient visibility.
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
  14sp. Loading a valid version-1 18sp default, version-2 16sp default, or
  transitional version-2 18sp value returns a 14sp in-memory appearance while
  preserving every other valid field, marks migration pending, and leaves the
  version-1/version-2 bytes untouched. The transitional value is a bounded
  pre-origin ambiguity: an inherited version-1 18sp default could be republished
  by the version-2 writer after a non-font change. That inherited state cannot
  be distinguished from an explicit v2/18 choice, so the bounded policy migrates
  every v2/18 record. Version-2 21/24/28sp choices and every valid version-3
  record remain exact; impossible old-schema 14sp is invalid. Version 3 is
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
  locations and appearances become durable.
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
- `OctavoProgressStore` owns a separate versioned, checksummed global progress-
  display record. Same-directory synchronization and atomic replacement preserve
  prior bytes on failure. The selected Chapter, Page, Location, or Percentage
  mode becomes durable only after its matching frame is presented.
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
- The corrected inset policy moves the unchanged-height content rectangle down
  by half the resolved vertical inset. This adds calm top breathing room without
  changing row capacity; opening or closing navigation chrome still cannot
  repaginate the book.

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
contract is [`../docs/android_port8.md`](../docs/android_port8.md).

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

On the vivo I2019/iQOO 9 SE, Android 14/API 34 ARM64, the corrected ordinary
matrix passed 67/67 in 170.878 seconds of instrumentation time (171.449 seconds
wall). The external confirmed-force-stop restore passed with a 1.987-second
seed, 1.135-second verification, and 4.908-second wall time. At 130% system text
with system animations disabled, the focused matrix passed 15/15 in 16.185
seconds of instrumentation time (18.591 seconds wall). Font scale `1.0`, window
and transition scales `1.0`, and the previously absent animator-scale key were
restored exactly. The crash buffer was empty; exit history contained only
expected `USER REQUESTED` force stops and `PACKAGE UPDATED`, with no crash or
ANR.

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
Audible TalkBack, UI polish, and user subjective/manual acceptance remain
pending. No pushed or merged 8vo revision or APK hash is claimed here.

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
15/15 in 15.580 seconds (17.244 seconds wall), and the original system settings
were restored exactly. The crash buffer was empty. A staged restore reproduced
all 26 durable files and 4,751,505 payload bytes by path, length, and SHA-256.
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
scale restoration, and empty crash-buffer checks passed on both targets.

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
search, selection, annotations, and synchronization remain deferred. Port 8
adds structural navigation only; spatial previews and advanced scrubbing remain
deferred. See [`../docs/android_port8.md`](../docs/android_port8.md) for the
current implementation and human acceptance boundary,
[`../docs/android_port7.md`](../docs/android_port7.md) for the accepted appearance
milestone, and
[`../docs/android_port6.md`](../docs/android_port6.md) for the accepted
library milestone.

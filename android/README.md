# 8vo Android

This directory is the Android application root. Ports 0-6 are the accepted
functional foundation. The Port 7 implementation candidate keeps the host
explicit while adding global premium-reader appearance, semantic reflow,
borderless host-composited chrome, first-frame coverage, and the custom-reader
accessibility bridge:

- `OctavoActivity` owns Android lifecycle, the library surface, and the
  `ACTION_OPEN_DOCUMENT` picker. Port 7 also keeps settings surfaces, system
  bars, the synchronous target-theme reader-entry cover, native chrome and its
  SurfaceView scale/translation composition, and successfully presented
  appearance persistence in the Activity host.
- `OctavoLibraryStore` owns the bounded versioned catalog, streams distinct
  imports into SHA-256-keyed app-private storage, atomically saves per-book
  successfully presented positions, and removes only managed copies.
- `OctavoAppearance` is the immutable global-only preference contract:
  theme, system family, size, spacing, width, supported alignment,
  publisher-color policy, and reduced motion.
- `OctavoDesignTokens` owns the six independently tuned product palettes,
  semantic UI0 role mapping, spacing, shape, 48dp touch target, 24dp icon, and
  restrained-motion values.
- `OctavoAppearanceStore` owns the bounded, versioned, CRC32-protected global
  `<files>/port7/appearance.v1` record. It requires descriptor sync and a
  same-directory atomic replace; failure preserves the prior published record.
- `OctavoAppearancePanel` owns the scrollable, host-themed reader-preference
  sheet, complete option labels, 48dp targets, and initial modal focus.
- `OctavoFixture` stages the packaged deterministic sample, Alpha, and Beta
  EPUBs under app-private files for the application and instrumentation.
- `OctavoTypography` acquires the selected Android generic serif or sans-serif
  family at the selected sp/spacing and publishes a bounded caller-owned
  regular/bold/italic/bold-italic alpha atlas and advance table. Its immutable
  one-entry cache reuses the atlas for an unchanged family, resolved pixel
  size, and spacing tuple. Port 7 bundles no font asset.
- `OctavoSurfaceView` owns atlas creation and coalesced appearance requests,
  forwards lifecycle, surface, inset, and raw touch values, schedules native
  presentations, and exposes the Android accessibility provider. The Activity
  composes visible chrome by uniformly scaling/translating this canonical full-
  viewport page; hidden chrome restores its identity transform. Only
  successfully presented Reader0 locations and appearances become durable.
- `OctavoReaderAccessibilityProvider` maps bounded Readerview0 semantics and
  host page content into stable page, previous, next, and progress virtual
  nodes with focus, hover, click, and scroll actions. The host routes real
  Tab/Shift+Tab input through the named native/virtual chain, keeps progress
  read-only, and recovers directionally after an explicit focus clear without
  exposing the raw Surface or chrome containers as blank stops.
- `OctavoNative` is the explicit Java/JNI boundary.
- `octavo_android_port7_build.c` source-consumes the exact Ground0, Reader0,
  UI0, and Readerview0 revisions once.
- `code/octavo_reader_justification.h` provides one allocation-free publisher
  row-spacing plan to the Windows and Android raster paths. The stable Windows
  theme catalog is also shared 8vo code, while Android's six semantic palettes
  remain independently tuned host policy.
- `octavo_android_jni.c` owns one native application/reader/view/typography
  state per surface, validates the appearance and exact 26-role palette,
  opens/restores/reflows through Reader0's public APIs, gives Reader0 the exact
  atlas advances for wrapping, paints canonical styled Reader0 rows against one
  borderless full native viewport through Readerview0's distraction-free
  projection, classifies left/center/right taps, gates page/reflow/appearance
  generations on presentation, and owns its `ANativeWindow`.

There is no process-global mutable application state. The Java view holds the
native handle and destroys it when the Activity is destroyed.

## Product direction

Port 6 remains the accepted functional library foundation. Port 7 implements
the first premium appearance slice. The current borderless/performance
candidate has passed its emulator, dual-ABI, visual, Windows 8vo, and unchanged
re10 gates. The preceding candidate's physical-iQOO run is historical evidence,
not acceptance evidence for the current APK; current physical automation and
timing plus hands-on accessibility, alternate-input, reduced-motion,
prolonged-reading, and subjective dark-room gates remain. Neither milestone is
the end-state Android
experience. The product target is a premium, local-first reader for user-owned
books that reaches Kindle-class reading, navigation, search, annotation,
library, accessibility, and interaction quality while using a distinct 8vo
design and user-controlled storage.

The public product contract is recorded in
[`../docs/android_product_vision.md`](../docs/android_product_vision.md), the
living capability inventory in
[`../docs/android_feature_parity.md`](../docs/android_feature_parity.md), and
the bounded delivery sequence in
[`../docs/android_roadmap.md`](../docs/android_roadmap.md).

The implemented Port 7 boundary and pending evidence are recorded in
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

Port 7 keeps the Port 6 behavioral regression surface and restores the original
`<= 64` deterministic-sample page-count guard with its 16sp default and
full-viewport geometry. It adds deterministic coverage for all six palette
identities, the exact version-1 all-default 18sp-to-version-2 16sp migration,
preservation of every other version-1 tuple and version-2 18sp choice,
preference extremes, global-
store missing/corrupt/save-failure behavior, rapid-request coalescing,
presentation-gated appearance generations, semantic-anchor containment after
reflow, and custom accessibility nodes/actions. Reader coverage also proves
the borderless full-viewport page, visible-chrome scale/translation versus
hidden identity, no chrome-driven repagination or page mutation, transition-
gesture cancellation, target-theme entry coverage, shared publisher
justification versus ragged-right pixels, compact and large viewports,
rotation, recreation, lifecycle, surface replacement, bounded retry recovery,
and visible surface-acquisition failure.

Accessibility coverage injects real Tab/Shift+Tab keys through visible and
hidden chrome, explicit focus clearing, disabled end-of-book actions, and the
hide-fade boundary. It also verifies activation through the existing
successful-presentation gate and the read-only progress stop. Bounded pixel
evidence samples each theme and typography change and the tested dark-
transition phases.

The final API 36 x86_64 emulator run passed 27/27 ordered tests in 406.087
seconds: accessibility 1, appearance-store/migration/palette 6, reader
appearance 11, bootstrap 1, Port 6 library regression 5, and navigation
regression 3. The separate true-process-death driver passed seed 1/1 in 10.093
seconds, confirmed no surviving target process after force-stop, and passed
verification 1/1 in 9.519 seconds. At `font_scale=1.3`, the strengthened
accessibility/settings case passed 1/1 in 19.495 seconds; the emulator was
restored to `font_scale=1.0`. A stale pre-final emulator-system watchdog
stack-dump line was traced to a `keystore2` SELinux denial, not an 8vo PID or
tombstone. After clearing it, an exact-APK bootstrap/lifecycle smoke passed
1/1 in 16.660 seconds and the crash buffer remained empty.

Android Debug native code retains symbols and uses `-O2`; first-frame work
defers whole-book location summaries until after the requested page is
successfully presented and records privacy-safe first-frame timing. Deferred
warming is bounded: terminal summary failure produces a nonfatal accessible
warning, and presentation exhaustion stops the pending poll. On the API
36 emulator, the ignored local Gardens of the Moon Resume repro measured 411ms
from ADB tap to the observed saved page and 220ms from native creation to
successful presentation. All six sampled app-bound transition captures had
zero black/near-black pixels. These debug-emulator measurements are regression
evidence, not a release SLA or a substitute for physical-device timing.

The current Android app/test build compiled `arm64-v8a` and `x86_64`. The
strict Windows 8vo build and 7/7 public smokes passed, as did unchanged re10's
strict product/qualification build and four-anchor document-engine smoke.

The Android 14/API 34 ARM64 iQOO 9 SE/vivo I2019 23/23 run, physical process
probe, accessibility/font-scale checks, and 19-frame transition evidence in
`../docs/android_port7.md` belong to the superseded reserved-geometry
candidate. They remain useful historical evidence but do not validate the
current borderless/performance APK. Current iQOO instrumentation, physical
exact-book timing, audible TalkBack and hands-on keyboard/switch and
reduced-motion review,
extended Paper, Warm dark, and OLED reading, and subjective dark-room comfort
review remain pending.

Port 7 intentionally remains a bounded appearance foundation. Per-book
appearance overrides, a bundled cross-device-identical or embedded font, full
Unicode shaping, complete publication accessibility, cover/library expansion,
table of contents, search, selection, annotations, and synchronization remain
deferred. See
[`../docs/android_port7.md`](../docs/android_port7.md) for the current
implementation and pending acceptance boundary, and
[`../docs/android_port6.md`](../docs/android_port6.md) for the accepted
library milestone.

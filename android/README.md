# 8vo Android

This directory is the Android application root. Ports 0-6 are the accepted
functional foundation. The Port 7 implementation candidate keeps the host
explicit while adding global premium-reader appearance, semantic reflow,
overlay chrome, and the custom-reader accessibility bridge:

- `OctavoActivity` owns Android lifecycle, the library surface, and the
  `ACTION_OPEN_DOCUMENT` picker. Port 7 also keeps settings surfaces, system
  bars, and successfully presented appearance persistence in the Activity
  host.
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
  regular/bold/italic/bold-italic alpha atlas and advance table. Port 7 bundles
  no font asset.
- `OctavoSurfaceView` owns atlas creation and coalesced appearance requests,
  forwards lifecycle, surface, inset, and raw touch values, schedules native
  presentations, and exposes the Android accessibility provider. Only
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
- `octavo_android_jni.c` owns one native application/reader/view/typography
  state per surface, validates the appearance and exact 26-role palette,
  opens/restores/reflows through Reader0's public APIs, gives Reader0 the exact
  atlas advances for wrapping, paints canonical styled Reader0 rows, chooses
  handset content geometry through Readerview0, classifies left/center/right
  taps, gates page/reflow/appearance generations on presentation, and owns its
  `ANativeWindow`.

There is no process-global mutable application state. The Java view holds the
native handle and destroys it when the Activity is destroyed.

## Product direction

Port 6 remains the accepted functional library foundation. Port 7 implements
the first premium appearance slice. Its final emulator, dual-ABI build, visual
automation, physical-iQOO automation, and desktop-regression gates pass, but it
is not accepted until the hands-on accessibility, alternate-input,
reduced-motion, prolonged-reading, and subjective dark-room gates pass.
Neither milestone is the end-state Android
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
restart probe. After that suite has installed the app and test APKs, run the
probe from the repository root; override `-Serial` when the selected target is
not the default emulator:

```powershell
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

Port 7 keeps the Port 6 behavioral regression surface. Its one explicit
diagnostic adjustment raises the deterministic sample page-count ceiling from
`<= 64` to `<= 128`, because Port 7's measured default reader chrome and
content geometry produce 65 pages on the API 36 emulator. Port 7 adds
deterministic coverage for all six palette identities, preference extremes,
global-store missing/corrupt/save-failure behavior, rapid-request coalescing,
presentation-gated appearance generations, semantic-anchor containment after
reflow, overlay-chrome stability, custom accessibility nodes/actions, compact
and large viewports, rotation, recreation, lifecycle, surface replacement,
transactional center-tap chrome rollback/gating, bounded retry
exhaustion/recovery, and visible surface-acquisition failure.
Accessibility coverage injects real Tab/Shift+Tab keys through visible and
hidden chrome, explicit focus clearing, disabled end-of-book actions, and the
hide-fade boundary; it also verifies activation through the existing
successful-presentation gate and the read-only progress stop. Bounded pixel
evidence samples each theme and typography change and the tested dark-
transition phases. A final 19-frame physical iQOO sequence also met its
recorded app-bounds luma limits, but subjective dark-room inspection of every
transition remains pending.

The final API 36 x86_64 emulator run passed 23/23 ordered tests in 399.625
seconds: Bootstrap 1, Port 6 library regression 5, navigation regression 3,
appearance store/palette 3, reader appearance 10, and accessibility 1. The
matrix retained the Port 6 behavioral surface and added store, palette,
preference, reflow, viewport, rotation, surface-replacement, chrome,
transition-pixel, failure-path, and accessibility-structure cases. The
separate `android_port7_process_restart.ps1` driver passed seed 1/1 in
13.761 seconds and verification 1/1 in 9.678 seconds around a confirmed
force-stop, restoring an extreme appearance plus the exact semantic location
after fresh-process book reopen. At 130% system text, the strengthened
accessibility/settings and reader-title-ellipsis test passed 1/1 in 26.582
seconds. The emulator font scale was restored to 1.0, and its crash buffer was
empty after the final automated runs.

The final Android app/test build compiled `arm64-v8a` and `x86_64`. The strict
Windows 8vo build and 7/7 public smokes passed, as did unchanged re10's strict
product/qualification build and four-anchor document-engine smoke.

On the Android 14/API 34 ARM64 iQOO 9 SE/vivo I2019, the final ordered matrix
passed 23/23 tests in 173.775 seconds. Its confirmed-force-stop process probe
passed seed 1/1 in 3.253 seconds and verify 1/1 in 1.689 seconds. The expanded
accessibility regression passed 1/1 at `font_scale=1.0` in 4.390 seconds and
again at `font_scale=1.3` in 4.780 seconds; the 130% title remained end-
ellipsized with both top-chrome actions visible. An objective real-device
UiAutomator sequence reached named Next, Library, Reader appearance, page, and
Previous stops without a blank chrome-container or raw-Surface stop. The
physical font scale, accessibility services, screen timeout, and automatic
brightness mode were restored to their baselines, and its crash buffer was
empty after the final automated runs. Captured Paper, Warm dark, and OLED page
pixels plus the bounded physical transition sequence are recorded in
`../docs/android_port7.md`.

Audible TalkBack and hands-on keyboard/switch and reduced-motion review,
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

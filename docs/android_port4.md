# Android Port 4: readable proportional typography

Port 4 replaces the temporary bitmap reading face from Port 3 with a bounded,
readable Android typography path. The 8vo host acquires four platform-serif
faces, gives Reader0 the exact advances used for rendering, and paints the
canonical styled Reader0 rows from a caller-owned alpha atlas. Navigation,
pagination, lifecycle, native-window ownership, and successful-presentation
policy remain in their Port 3 boundaries.

## Delivered boundary

Port 4 adds:

- a fixed 18sp Android serif reading size;
- regular, bold, italic, and bold-italic faces;
- anti-aliased `ALPHA_8` glyph atlases for printable ASCII plus a visible
  fallback glyph;
- one caller-owned advance table shared by Reader0 measurement and native
  raster placement;
- native rendering of Reader0's canonical style rows and inline style
  fragments;
- handset-width page/content geometry resolved through Readerview0's public
  content-geometry API;
- a deterministic EPUB fixture containing regular, bold, italic, and
  bold-italic content on every page; and
- instrumentation evidence for proportional advances, all four rendered
  styles, readable size/line metrics, wide handset geometry, pixels,
  pagination, navigation, lifecycle, surface replacement, and Activity
  recreation.

The application ID remains `ro.devze.octavo`.

## Ownership and data flow

Android font acquisition remains an 8vo host responsibility:

1. `OctavoTypography` resolves Android's platform `serif` family at the fixed
   reading size for regular, bold, italic, and bold-italic styles.
2. It measures printable ASCII with `Paint.measureText`, rasterizes the same
   faces into one `ALPHA_8` bitmap, and publishes a versioned integer metric
   table plus atlas bytes.
3. `OctavoSurfaceView` passes those caller-owned values through the explicit
   JNI creation boundary.
4. Native 8vo validates and copies the table and atlas into its per-Activity
   application state. It frees the copy during the existing explicit destroy
   path.
5. `SourceReaderLayoutConfig.measure_text` points to an 8vo callback with the
   application state in `measure_user`. Reader0 therefore wraps with the same
   per-style advances that native 8vo later uses to place glyphs.
6. Reader0 remains authoritative for XHTML style resolution, wrapping,
   pagination, and frame publication. Native 8vo consumes each frame's style
   rows and fragments and alpha-blends the matching atlas cells.

Java does not paginate, classify navigation, own a native window, or schedule
document work. Reader0, UI0, and Readerview0 gain no Android dependency or host
policy.

There is no process-global mutable font or document state. The atlas and
counters belong to one `OctavoAndroidApp`.

## Readable handset geometry

Port 3 passed the physical framebuffer width directly into Readerview0's
desktop-oriented default content geometry. Its 660-unit page cap consequently
became a 660-*pixel* cap on high-density phones, leaving unusually large side
margins.

Port 4 keeps Readerview0 authoritative and calls its public
`reader_view_resolve_content_geometry` function with an Android-host style:

- the page uses at least 90 percent of the physical viewport width at supported
  test viewports;
- page and content insets scale from the current viewport with explicit lower
  bounds; and
- the Readerview0 progress rectangle follows the resulting page width.

This is 8vo presentation policy, not a shared-package change. Tap
classification remains based on the inset-safe Android host width and is
unchanged.

## Deterministic fixture

Port 4 stages:

- asset: `android/app/src/main/assets/port4/octavo_port4.epub`
- private path: `<files>/port4/octavo_port4.epub`
- length: 178,644 bytes
- SHA-256:
  `3D962705842816645B48E1F2909C2EA3EF44F9C39FE1DFFAB9BC687C82B42CC4`

The fixture contains four ordered XHTML sections with 256 uniquely numbered
paragraphs per section. Every paragraph contains regular, bold, italic, and
bold-italic spans. `scripts/build_android_fixture.ps1` writes the EPUB in fixed
entry order, stores entries without compression, fixes every ZIP timestamp,
and emits the exact length and hash above.

At the 1,080 by 1,920 API 36 emulator viewport, the first section publishes 35
pages with the Port 4 host typography and geometry. The fixture therefore
continues to cover adjacent navigation, rapid-input gating, section
transitions, and both book boundaries.

## Automated coverage

The four-test instrumentation suite retains all Port 3 checks and additionally
requires:

- a validated native typography atlas;
- a reading size of at least 18 physical pixels and a larger positive line
  advance;
- the regular `i` advance to be narrower than regular `W`;
- exactly four available style faces;
- non-zero raster evidence for regular, bold, italic, and bold-italic text;
- more than 100 rasterized reading glyphs in a presented page;
- a page surface occupying at least 90 percent of the captured framebuffer
  width; and
- exact Port 4 fixture bytes and private staging path.

Reader0's page indices, text hashes, progress values, section transitions,
boundary no-ops, and successful-presentation gate remain the navigation
oracles. PixelCopy still verifies changed and restored output. Every stable
state requires zero native render/navigation failures and a clean Readerview0
frame.

## Shared-package boundary

Port 4 changes only 8vo. Ground0, Reader0, UI0, Readerview0, and re10 remain
unchanged.

- `reader0.c`, `ui0.c`, and `readerview0.c` are each source-consumed exactly
  once by `octavo_android_port4_build.c`.
- Reader0 is consumed only through `reader0.h` and its existing
  `SourceReaderLayoutConfig.measure_text` and canonical-frame APIs.
- Readerview0 supplies layout, projection, progress, and its public
  content-geometry resolver.
- 8vo owns Android font acquisition, the copied atlas lifetime, drawing,
  lifecycle, input, window ownership, and presentation policy.

## Deliberately deferred

Port 4 is a readable default, not a complete ebook typography system. It does
not add:

- user-selectable font family, text size, line spacing, margins, or themes;
- a bundled cross-device-identical font;
- full Unicode atlas coverage, complex-script shaping, ligatures, kerning
  pairs, hyphenation, or justified spacing;
- EPUB embedded-font rasterization or CSS font-family selection;
- proportional Android chrome text (the small title/progress labels retain
  the temporary Ground0 bitmap face);
- document picking, durable reading-position persistence, accessibility-node
  adaptation, images, tables, annotations, selection, or search; or
- a background renderer.

Android's platform serif can differ between OS/vendor images, so exact page
counts are intentionally asserted only within a running device. Pagination and
presentation still agree on that device because both consume the same copied
advance table.

## Build and acceptance

With the exact clean dependency directories selected through
`OCTAVO_*_DIR`:

```powershell
cd android
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
```

Port 4 is accepted when:

1. the exact clean Ground0, Reader0, UI0, and Readerview0 guards pass;
2. both `arm64-v8a` and `x86_64` debug binaries build;
3. all emulator instrumentation passes with zero native/render/navigation
   failures;
4. the strict 8vo Windows build and seven public smoke tests pass;
5. unchanged re10 passes its strict product/qualification build and
   `--document_engine_smoke`; and
6. instrumentation plus hands-on legibility, emphasis, margins, and
   navigation checks pass on the iQOO physical device.

## Validation record

The final API 36 x86_64 emulator build passed all four instrumentation tests in 27.294
seconds on 2026-07-30. The exact dependency guards and both configured Android
ABI builds passed. Visual inspection of the 1,080 by 1,920 frame confirmed:

- proportional serif text at the fixed readable size;
- visibly distinct bold, italic, and bold-italic spans;
- a bold chapter heading;
- substantially wider handset page geometry; and
- clean anti-aliased glyph edges without the Port 3 bitmap/monospace
  appearance.

The strict Windows gates also passed on 2026-07-30:

- `scripts/run_public_smoke.ps1` completed the strict C11 build, architecture
  and exact-dependency guards, and all seven public smoke tests;
- unchanged re10 completed its strict optimized product and qualification
  builds within the fixed PE image budgets; and
- unchanged re10's `--document_engine_smoke` passed with four anchors, final
  spine index 3, and hash `f3c13a55f0349720`.

The vivo I2019/iQOO 9 SE running Android 14/API 34 on ARM64 passed all four
instrumentation tests in 28.424 seconds. Its 1,080 by 2,196 reader surface
published page 1 of 40 with 742 visible bytes and hash `ed263c9f103504a1`.
The Android crash buffer was empty. Physical screenshot inspection confirmed
wide page geometry, anti-aliased serif text, and visibly distinct regular,
bold, italic, and bold-italic faces. The user subsequently confirmed that both
reported font issues (the undersized text and temporary monospace appearance)
were fixed. Physical instrumentation retained the previous/next, boundary,
rapid-input gating, lifecycle, surface-replacement, and recreation checks.

One additional post-audit physical rerun was attempted after the handset had
auto-locked. Test launches timed out while Android reported an asleep,
dreaming-lockscreen state; the crash buffer remained empty. This
device-state-only result does not replace the earlier green physical suite,
hands-on inspection, or the final green emulator run.

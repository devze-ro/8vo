# Android Port 7: premium reader appearance foundation

Status: implementation candidate on
`android/port7-premium-reader-appearance`. The final API 36 emulator,
dual-ABI Android build, Windows 8vo, unchanged re10, and Android 14/API 34
physical-iQOO automated gates passed on 2026-08-01/02. Objective real-device
accessibility/keyboard traversal also passed without blank host stops. Audible
TalkBack and touch-exploration judgment, hands-on keyboard/switch and reduced-
motion review, prolonged-reading comfort, and dark-room acceptance remain
pending.

Port 7 turns the accepted Port 6 library and reader into the first bounded
premium-reading appearance slice. It adds a host-owned semantic visual system,
global reader preferences, canonical reflow restoration, overlay reader
chrome, and the Android custom-reader accessibility bridge without moving
Android policy into Reader0, UI0, Readerview0, or Ground0.

The milestone is an appearance foundation, not a declaration of Kindle-class
feature parity. It deliberately keeps the book and the successfully presented
semantic location authoritative while making the reader more comfortable and
coherent.

## Delivered boundary

Port 7 implements:

- 8vo-owned semantic tokens for reader, chrome, settings, library, dialogs,
  system bars, launch, text hierarchy, dividers, accents, selection, search,
  highlights, failures, spacing, shape, icon size, touch size, and restrained
  motion;
- paper, sepia, dusk, warm-dark, OLED, and accessible high-contrast themes,
  each with an independently specified palette rather than a mechanical
  inversion;
- global reader choices for font family, font size, line spacing,
  margins/content width, supported alignment behavior, publisher colors, and
  reduced motion;
- a bounded, versioned, checksummed, atomically replaced global appearance
  record;
- fresh caller-owned Android glyph atlases for the selected system serif or
  sans-serif family at the selected size and spacing;
- canonical Reader0 repagination after a layout-affecting change, anchored to
  the last successfully presented spine/byte location;
- a presentation gate and coalescing policy for rapid appearance requests;
- an `OctavoAppearancePanel` settings sheet and center-tap show/hide reader
  chrome whose measured top/bottom occlusion defines stable page geometry;
- a target-theme transition cover held through successful native presentation
  and two Java frames, with bounded composed-frame sampling of the covered
  transition phases;
- a bounded Android virtual accessibility tree for the page, previous page,
  next page, and read-only progress, plus deterministic native/virtual keyboard
  focus routing; and
- native diagnostic state for appearance generations, palette identity,
  reflow, chrome, accessibility actions, and failures.

The application ID remains `ro.devze.octavo`.

## Global appearance contract

Port 7 makes an explicit global-default decision. One appearance applies to
every book. Per-book overrides are not inferred from the catalog and are not
silently added to the Port 6 book records.

| Setting | Port 7 values | Default | Pagination effect |
| --- | --- | --- | --- |
| Theme | Paper, Sepia, Dusk, Warm dark, OLED, High contrast | Paper | None |
| Font family | Literary system serif, Clear system sans serif | Literary | Rebuild |
| Font size | 16, 18, 21, 24, or 28sp | 18sp | Rebuild |
| Line spacing | 1150, 1250, 1300, or 1500 permille | 1250, Classic | Rebuild |
| Margins/content width | Wide 720, Balanced 860, Focused 960 permille | Balanced | Rebuild |
| Alignment | Publisher or Ragged right | Publisher | Host rendering policy |
| Publisher colors | Theme safe or Allow publisher colors | Theme safe | None |
| Reduced motion | Off or On | Off | None |

The content-width values are bounded targets. Readerview0 still resolves the
authoritative page and content geometry, and 8vo additionally applies handset
insets and a height-derived comfortable maximum width.

Publisher alignment preserves supported Reader0 row alignment. Ragged-right
mode suppresses centered/end alignment in the current Android raster path; it
does not add host-side EPUB interpretation, justification, or hyphenation.
Theme-safe publisher colors use the active semantic reader text. The explicit
allow policy may use a Reader0 row color except in High contrast, where the
accessibility palette remains authoritative.

Reduced motion makes the Port 7 fast, standard, and deliberate token durations
zero. Port 7 does not add decorative page-turn animation.

## Typography and font licensing

The two curated choices are Android generic families:

- **Literary** maps to `Typeface.SERIF`.
- **Clear** maps to `Typeface.SANS_SERIF`.

8vo does not bundle a font in Port 7 and therefore adds no font license or
cross-device-identical font asset. Android resolves the generic family on the
device. Embedded EPUB fonts remain disabled in the accepted Android layout
key.

`OctavoTypography` rasterizes regular, bold, italic, and bold-italic faces into
a fresh caller-owned alpha atlas. The selected sp value participates in
Android font scaling. Line advance is derived from the selected spacing. Text
size, atlas dimensions, stride, and alpha storage retain explicit bounds that
match the JNI import contract.

Because system glyph metrics may differ across devices or after a preference
change, Port 7 promises the same semantic reading location after reflow, not
the same page number or identical line breaks.

## Theme contract

Every theme specifies semantic roles for the reader page and text, reader
chrome, settings, library and library return, sheets, dialogs, primary/
secondary/muted text, dividers, accent states, selection, search, highlights,
success, warning, error, focus, overlay, controls, status bar, navigation bar,
and launch surface. A deterministic palette hash supports instrumentation.

| Theme | Reading intent |
| --- | --- |
| Paper | Neutral warm light page with restrained warm accents. |
| Sepia | Lower-glare warm paper and brown text hierarchy. |
| Dusk | Cool muted low-light alternative without pure black. |
| Warm dark | Primary night preset: warm charcoal `#1B1917` with warm off-white `#E8E0D4` text. |
| OLED | Optional true-black page, status bar, navigation bar, and launch surface with softened text. |
| High contrast | Independent black-on-white accessibility palette with strong focus and control colors. |

The Java palette is copied into the exact 26-role UI0 color order at the
Java/JNI boundary. UI0 remains product-neutral; it does not acquire Android or
8vo theme policy.

## Durable appearance ownership

The single global record is `<files>/port7/appearance.v1`. It contains a fixed
store header, version, field count, the stable appearance configuration, and a
CRC32 checksum. The version-1 record is 60 bytes and is rejected if it differs
from the exact shape; reads remain capped at 256 bytes.

A missing or invalid record yields the documented default. Saves write one
fixed same-directory temporary file, flush and synchronize its file
descriptor, then require `ATOMIC_MOVE` with `REPLACE_EXISTING`. There is no
non-atomic publication fallback: an unsupported or failed move rejects the
save, removes temporary residue best-effort, and preserves the prior
published record and in-memory appearance. Load/save and missing/corrupt
fallback counters remain visible to deterministic tests.

The Activity owns when a successfully presented appearance becomes durable.
The store is separate from Port 6 per-book locations so a corrupt appearance
record cannot invalidate the library or book positions.

## Reflow and successful-presentation anchoring

Appearance application preserves the existing Reader0 ownership boundary:

1. 8vo keeps the last successfully presented Reader0 spine index and semantic
   byte offset as the reflow anchor.
2. Java coalesces rapid appearance requests for 90 ms and supplies validated
   configuration, semantic colors, and a fresh typography atlas.
3. Native state rejects another page or appearance mutation while a page,
   reflow, or appearance generation is awaiting presentation.
4. A change to family, size, spacing, margins/content width, or resulting font
   metrics marks canonical pagination dirty.
5. 8vo asks Reader0 through its public location-navigation API to rebuild the
   layout and publish the page containing the saved semantic anchor.
6. After the native window successfully posts, 8vo verifies that the new
   Reader0 page has the expected spine and contains the anchor byte.
7. Only then does the new appearance generation become presented and eligible
   for durable publication. A failure remains counted and visible.

Theme-only, publisher-color, reduced-motion, and supported alignment changes
do not invent a new document location. Page movements and appearance changes
share the successful-presentation gate, so a rapid control sequence cannot
persist or expose an unpresented page.

Rotation, surface replacement, Activity/process recreation, pause/resume, and
book reopen use the same semantic-location rule. The API 36 matrix covers every
supported family, size, spacing, and margin value; repeated baseline/extrema
round trips; measured chrome; 320 x 480dp and 600 x 840dp viewports; native
portrait/landscape rotation; surface replacement; Activity recreation; and the
inherited pause/resume regression. These axes run sequentially at
`font_scale=1.0`; a separate `font_scale=1.3` run covers the accessibility
tree and settings surface. This is finite evidence, not a Cartesian-combination
claim. The ordinary connected suite excludes the externally orchestrated
restart probe. After that suite has installed the app and test APKs, run its
seed/force-stop/verify driver from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android_port7_process_restart.ps1 -Serial emulator-5554
```

The driver requires each one-test half to pass, confirms that no target process
survives the external force-stop, then performs the book-reopen verification in
a fresh process. This is deterministic emulator coverage of each axis, not a
claim that every Cartesian combination or physical-device condition has been
accepted.

## Overlay reader chrome

Reader content and page geometry remain resolved whether the chrome is visible
or hidden. A valid short center tap toggles the chrome and requests a redraw;
left and right zones retain previous/next page behavior. A gesture must finish
inside its original zone and remain within the existing duration and slop
bounds, so a center-tap intent cannot become a page turn merely because the
finger moved.

The Android host publishes its measured top and bottom control heights to
native state. Those bands remain excluded from Reader0/Readerview0 content and
from page-turn taps whether the controls are visible or hidden. Hiding controls
therefore changes only their draw/accessibility layer; it does not rebuild
pagination, change the canonical page, accept an accidental band tap, or save
a new location.

For a cross-theme change, the host immediately installs a full-reader cover in
the target `readerPage` color above the Surface and reader chrome but below the
settings sheet. It removes the cover only after the matching generation is
successfully presented and two Java frames have elapsed. Bounded composed-frame
sampling found no bright sample in the tested reader entry/re-entry/exit,
settings open/close, surface-replacement, recreation, and warm-dark-to-OLED
phases. Latest-only retargeting and a settled no-op callback prevent a rapid
request sequence from stranding an obsolete cover. A final 19-frame physical
iQOO composed-screen series spanned a Warm dark panel, OLED selection and
settings close, library return, reader re-entry, and center-tap chrome
hide/show. Inside the app bounds at y=88..2283, every frame had an
above-95%-luma pixel ratio of 0.0000; maximum mean luma was 0.1607 and maximum
above-85%-luma ratio was 0.0233. The maximum full-screen above-95%-luma ratio
was 0.0009, solely from system status icons outside those app bounds. This is
bounded captured-screen evidence, not subjective dark-room or perceptual
no-flash acceptance.

## Android accessibility bridge

`OctavoReaderAccessibilityProvider` establishes a bounded virtual tree for
the custom-rendered surface. Its stable focus order is:

1. page content;
2. previous page;
3. next page; and
4. progress status.

The adapter consumes bounded Readerview0 semantic records for control bounds,
enabled state, names, values, and progress ranges, with safe host fallbacks.
It supports accessibility and keyboard focus, touch-exploration hover,
show-on-screen, page scroll/click actions, and a page-content action to show or
hide controls. Page-changed accessibility events are emitted only after the
native page is successfully presented.

When Java chrome is visible, its Library, Appearance, Previous, Next, and
Progress views own those accessibility nodes. When chrome is hidden, the
provider exposes the equivalent virtual Previous, Next, and Progress nodes and
clears any stale virtual focus. Real Tab/Shift+Tab input follows one
deterministic named native/virtual chain; the top/bottom chrome containers and
raw Surface host are explicitly excluded as blank stops. Progress remains an
informational, read-only stop. Directional recovery after an explicit focus
clear, routing during the hide-fade boundary, and disabled end-of-book routing
all remain inside the named chain. Enter, Space, and D-pad center activate page
and navigation actions through the existing successful-presentation gate;
activating Progress is a no-op that retains focus.

Instrumentation injects real forward/reverse keys and verifies the bounded
semantic packet, roles, names, values, ranges, dynamic ownership, focus order,
explicit focus-clear recovery, hidden-fade and end-of-book boundaries,
activation/no-op behavior, presentation gating, host-node consistency, 48dp
control bounds, the settings sheet's complete option surface, and its initial
focus/z-order. On the iQOO, an objective UiAutomator sequence reached named
Next page, Library, Reader appearance, book-page text, and Previous page stops
without a blank container or raw-Surface stop. An independent closure audit
found no remaining P1/P2 focus issue.

This bridge is the Port 7 accessibility foundation, not complete publication
accessibility. Headings, lists, links, images/alt text, tables, selection,
annotations, language changes, and complex document reading semantics remain
future engine and product work. At 130% system text, strengthened emulator and
physical automation passed; physical visual inspection confirmed that both
top-chrome actions remained visible and the long reader title used end
ellipsis instead of hard clipping. Objective keyboard traversal is automated,
but audible TalkBack speech and touch-exploration judgment, hands-on keyboard/
switch use, and reduced-motion quality still require acceptance.

## Shared-package boundary

Port 7 keeps all Android appearance, persistence, typography acquisition,
touch, window, and accessibility-adapter policy inside 8vo.

- Reader0 is used only through `reader0.h` for canonical pagination, location
  navigation, frames, and page moves.
- UI0 receives a resolved product palette but gains no Android theme policy.
- Readerview0 continues to own portable chrome projection, semantic records,
  and content geometry.
- Ground0, Reader0, UI0, Readerview0, and re10 require no Port 7 product-policy
  change.
- `reader0.c`, `ui0.c`, and `readerview0.c` remain source-consumed exactly
  once.

Android does not duplicate EPUB interpretation, pagination, semantic location
resolution, or navigation.

## Deliberately deferred

Port 7 does not add:

- per-book appearance overrides or appearance synchronization;
- table of contents, go-to, navigation history, Page Flip-class preview, or
  full-text search;
- text selection, bookmarks, highlights, notes, or an annotations workspace;
- cover library, collections, richer metadata, or library search;
- Google Drive or self-hosted synchronization;
- bundled or embedded fonts, complete Unicode shaping, bidi/RTL parity,
  hyphenation, or full publisher-style parity;
- complete image, table, footnote, MathML, fixed-layout, or comics support;
- page brightness controls, scheduled themes, or system-theme following; or
- Kindle branding, assets, labels, layouts, icons, animations, trade dress,
  or copyrighted content.

## Acceptance contract

Port 7 acceptance requires all of the following before this milestone can be
called complete:

- exact semantic position across every layout-affecting setting, recreation,
  rotation, surface replacement, pause/resume, and book reopen;
- no clipping at every supported size, spacing, margin, compact/large
  viewport, orientation, and supported system-font-scale combination;
- deterministic semantic and pixel evidence for all six palettes and every
  preference extreme;
- no bright frame during launch, reader entry/exit, theme switch, settings,
  recreation, or surface replacement;
- TalkBack labels/actions, focus order, minimum touch targets, large text, and
  reduced-motion review;
- exact dependency and cleanliness guards;
- arm64-v8a and x86_64 Android debug and test builds;
- full API 36 emulator instrumentation;
- strict Windows 8vo build and public smoke suite;
- unchanged re10 product/qualification builds and
  `--document_engine_smoke`;
- physical iQOO instrumentation and hands-on extended reading in Paper, Warm
  dark, and OLED; and
- a dark-room review of discomfort, halation, minimum brightness, accent
  intensity, and every transition.

## Validation record

Final fixed-candidate emulator and desktop evidence was recorded on
2026-08-01/02; physical iQOO evidence was recorded on 2026-08-02:

- The exact clean dependency guard passed for Ground0
  `770b970b4655facfa9700c3d1025d96102365631`, Reader0
  `b604556723c5a196ed7d2b1249f56bd3d976edb4`, UI0
  `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`, and Readerview0
  `f97f9d38cf857c2cff1f90357cf5d2e5cf40dc03`.
- `:app:assembleDebug :app:assembleDebugAndroidTest` passed against Android
  API 36 and built both `arm64-v8a` and `x86_64` native variants.
- On the `octavo_port0_api36` API 36 x86_64 emulator, the final ordered suite
  passed 23/23 tests in 399.625 seconds: Bootstrap 1, Port 6 library regression
  5, navigation regression 3, appearance store/palette 3, reader appearance 10,
  and accessibility 1.
- At `font_scale=1.0`, the appearance cases exercise every supported layout
  value sequentially, baseline/extrema round trips, 320 x 480dp and
  600 x 840dp viewports, native portrait/landscape rotation, surface
  replacement, Activity recreation, and inherited lifecycle coverage. They
  verify exact semantic-anchor containment and successful-presentation
  counters rather than claiming every Cartesian combination.
- The deterministic sample page-count diagnostic is the sole adjusted Port 6
  assertion: its ceiling rises from 64 to 128 because Port 7's measured default
  chrome/content geometry produces 65 pages on this API 36 emulator. The Port 6
  behavioral regression surface remains intact.
- Palette and pixel cases verify all six native identities, corrupt and
  cold-open persistence, font-family/alignment/publisher-color behavior,
  nonempty and unclipped atlas-cell/reader ink at tested extrema, and dynamic
  left/right italic overhang in both curated families.
- Failure cases verify pre-publication host-frame gates for lifecycle, surface,
  inset, measured chrome, and touch-driven chrome changes; an immediate side
  tap is rejected while a failed center-tap toggle rolls back and recovers;
  requested chrome-inset retry; bounded presentation exhaustion and explicit
  user retry; visible surface-acquisition failure and
  exact recovery; same-appearance and reduced-motion rollback without durable
  publication; and deferred production-store failure across lifecycle.
- Accessibility automation injects real Tab/Shift+Tab input and uses
  `UiAutomation` plus provider diagnostics to verify named Java/virtual
  ownership, forward/reverse focus order, explicit-clear recovery, hidden-fade
  and end-of-book routing, read-only Progress, host-node consistency, 48dp
  targets, presentation-gated activation, and the complete settings surface.
  An independent closure audit found no remaining P1/P2 issue.
- Bounded composed-frame evidence covers cold library launch, reader entry,
  re-entry and exit, settings open/close, surface replacement, recreation, and
  eight warm-dark-to-OLED samples. No sampled frame exceeded its bright-pixel
  bounds; this finite automation is not exhaustive or subjective no-flash acceptance.
- The separate `android_port7_process_restart.ps1` driver passed seed 1/1 in
  13.761 seconds, an externally confirmed force-stop with no surviving target
  process, and verify 1/1 in 9.678 seconds. It restored the extreme global
  appearance and the page containing the last successfully presented semantic
  byte after book reopen.
- With emulator `font_scale=1.3`, the strengthened accessibility/settings and
  reader-title-ellipsis test passed 1/1 in 26.582 seconds. Visual inspection
  confirmed usable 130% chrome and a scrollable settings sheet opening at its
  unobscured heading; the emulator was restored to `font_scale=1.0`. Ignored
  evidence is retained under `local/validation/android-port7/api36/`.
- The emulator crash buffer was empty after the final ordered, process-restart,
  and 130% font-scale runs.
- On the iQOO 9 SE/vivo I2019, Android 14/API 34 ARM64 at 1080 x 2400 and
  440 dpi, the final ordered suite passed 23/23 tests in 173.775 seconds with
  the same class breakdown as the emulator matrix.
- The physical fresh-process probe passed seed 1/1 in 3.253 seconds, confirmed
  that force-stop left no target process alive, and passed verify 1/1 in
  1.689 seconds.
- The expanded physical accessibility regression passed 1/1 at
  `font_scale=1.0` in 4.390 seconds and again at `font_scale=1.3` in
  4.780 seconds. The 130% title remained end-ellipsized with both top-chrome
  actions visible. The device was restored to `font_scale=1.0`, accessibility
  disabled with no enabled service, a 30-second screen timeout, and automatic
  brightness mode.
- Physical composed pixels recorded Paper `#FFFDF9`, Warm dark `#1B1917`, and
  OLED `#000000` reader pages with their corresponding semantic chrome/system
  surfaces. The final 19-frame transition sequence had no above-95%-luma
  pixels inside app bounds; its small full-screen contribution came solely
  from system status icons. These captures are not subjective comfort or
  dark-room acceptance.
- The physical crash buffer was empty after the final ordered, process-restart,
  and 130% font-scale runs.
- The strict Windows 8vo build passed its dependency/architecture audits and
  all 7 public smokes: `octavo_public_smoke result=pass build=True tests=7`.
- Unchanged re10 at `6b6112a1c1111743a8c57631eca328ad424fe4ed`
  passed its four exact dependency guards, strict product/qualification build,
  image budget, and `--document_engine_smoke` with four anchors, final spine 3,
  and hash `f3c13a55f0349720`. re10 and its participating dependencies remained
  clean at their exact revisions.

The recorded finite emulator and physical automated matrices and desktop gates
passed for this candidate, but Port 7 is not yet accepted. Still required are
audible TalkBack speech and touch-exploration judgment, hands-on keyboard/
switch and reduced-motion review; extended Paper, Warm dark, and OLED reading;
and subjective dark-room review of discomfort, halation, minimum brightness,
accent intensity, and every transition. LCD-class dark-room evidence remains
required when an appropriate display is available.

# Android Port 7: premium reader appearance foundation

Status: post-feedback implementation candidate on
`android/port7-premium-reader-appearance`. The current source adds the refined
16sp default, publication punctuation coverage, full-measure ordinary prose
justification with hard-line safety, hidden-on-entry chrome, single-tap
appearance access, swipe navigation, and staged migration durability described
below. It consumes Reader0 `0.6.0-dev`/API 6 at
`59e9efdaca17b316aa2b1f5a7be0cbdebf5e4c26` for authoritative soft-wrap
provenance. Reader0, companion re10, exact 8vo guards/audit, dual-ABI, strict
Windows/public-smoke, API 36 emulator, physical iQOO, ProcessRestart, 130%
accessibility, crash, and byte-exact backup/restore gates passed. The final
current-source ordered Android matrix passed 36/36 on both targets: appearance
store 9, appearance 15, navigation 5, library 5, accessibility 1, and bootstrap
1. Every completed 33-test API 6 Android run remains historical evidence for an
earlier binary. Audible TalkBack, hands-on alternate-input and reduced-motion,
extended theme reading, and dark-room comfort acceptance remain pending.

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
- a one-entry immutable caller-owned Android sparse glyph-atlas cache keyed only
  by the selected system family, resolved size, and spacing, with explicit
  missing-glyph diagnostics;
- canonical Reader0 repagination after a layout-affecting change, anchored to
  the last successfully presented spine/byte location;
- a presentation gate and coalescing policy for rapid appearance requests;
- an `OctavoAppearancePanel` settings sheet, single-tap `Aa` access, and
  center-tap show/hide reader chrome composited over one canonical full-viewport
  native page, with no page border and no permanently reserved chrome bands;
- hidden controls on ordinary reader entry and book reopen, horizontal swipe
  navigation, and lifecycle-safe gesture cancellation through the same native
  presentation gate used by taps and accessibility actions;
- a target-theme transition cover held through successful native presentation
  and two Java frames, with bounded composed-frame sampling of the covered
  transition phases;
- a bounded Android virtual accessibility tree for the page, previous page,
  next page, and read-only progress, plus deterministic native/virtual keyboard
  focus routing;
- Reader0 `0.6.0-dev`/API 6 canonical `soft_wrapped` provenance plus one
  allocation-free 8vo justification plan consumed by both Windows and Android,
  so eligible publisher-justified prose fills the available measure with
  overflow-safe arithmetic while final/hard-line and image boundaries remain
  unclassified for stretching, without moving EPUB interpretation out of
  Reader0;
- first-frame scheduling that defers whole-book Reader0 location summaries
  until after a successfully presented page, bounds every retry path, and
  exposes terminal summary failure without interrupting reading; and
- native diagnostic state for appearance generations, palette identity,
  reflow, chrome, accessibility actions, justification, first-frame time, and
  failures.

The application ID remains `ro.devze.octavo`.

## Global appearance contract

Port 7 makes an explicit global-default decision. One appearance applies to
every book. Per-book overrides are not inferred from the catalog and are not
silently added to the Port 6 book records.

| Setting | Port 7 values | Default | Pagination effect |
| --- | --- | --- | --- |
| Theme | Paper, Sepia, Dusk, Warm dark, OLED, High contrast | Paper | None |
| Font family | Literary system serif, Clear system sans serif | Literary | Rebuild |
| Font size | 14, 16, 18, 21, 24, or 28sp | 16sp | Rebuild |
| Line spacing | 1150, 1250, 1300, or 1500 permille | 1250, Classic | Rebuild |
| Margins/content width | Wide 720, Balanced 860, Focused 960 permille | Balanced | Rebuild |
| Alignment | Publisher or Ragged right | Publisher | Host rendering policy |
| Publisher colors | Theme safe or Allow publisher colors | Theme safe | None |
| Reduced motion | Off or On | Off | None |

The content-width values are bounded targets. Readerview0 still resolves the
authoritative page and content geometry, and 8vo additionally applies handset
insets and a height-derived comfortable maximum width.

Publisher alignment preserves supported Reader0 row alignment. Reader0
`0.6.0-dev`/API 6 stores authoritative `soft_wrapped` provenance in each
canonical styled row. Measured space/em-dash wraps are true; final/hard-line and
image boundaries are false. Both 8vo hosts consume that field instead of
reclassifying frame bytes. The shared allocation-free raster plan uses widened,
overflow-safe arithmetic to distribute the remaining advance over interior
ASCII spaces across styled fragments. Layout-only trailing whitespace is
excluded from measurement/drawing while a visible em dash remains ink. Final
rows, explicit publisher hard breaks such as `<br>` verse, and Ragged-right
mode retain natural spacing. This is raster policy over Reader0 rows, not
duplicated EPUB interpretation, line breaking, semantic anchoring, or
hyphenation.
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
a caller-owned sparse alpha atlas. Atlas version 2 carries a sorted codepoint
map for 95 printable ASCII characters, the 96 Latin-1 codepoints from U+00A0
through U+00FF, and 42 curated publication characters such as typographic
apostrophes, quotation marks, dashes, and ellipsis: 233 entries in total under
the 256-entry JNI bound. Native lookup is bounded binary search, and a native
diagnostic counter records a codepoint that is not in the map instead of
silently treating replacement glyphs as coverage. This remains a deliberately
bounded Latin/publication set, not full Unicode shaping or fallback.

A bounded one-entry immutable cache reuses the atlas for theme, margin,
alignment, and publisher-color changes; family, size, or spacing changes
replace it. The selected sp value participates in Android font scaling. Line
advance is derived from the selected spacing. Text size, atlas dimensions,
stride, codepoint/advance tables, and alpha storage retain explicit bounds that
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
CRC32 checksum. The current version-3 record is 60 bytes and is rejected if it
differs from the exact shape; reads remain capped at 256 bytes. A missing,
corrupt, or new record uses the current 16sp default; subsequent successful saves
publish version 3.

Compatibility is explicit. Loading a valid version-1 18sp default, version-2
16sp default, or transitional version-2 18sp value returns a 16sp in-memory
appearance while preserving every other valid field and marks migration
pending. Load is non-mutating: the valid version-1/version-2 bytes remain exact.
The transitional value is a bounded pre-origin ambiguity: before explicit
choice-origin metadata, the version-2 writer could republish an inherited
version-1 18sp default after a non-font change. Inherited and explicit v2/18
origins cannot be distinguished, so the bounded policy migrates every v2/18
record. Version-2 21/24/28sp choices and every valid version-3 record remain
exact. Because neither older schema could represent 14sp, a version-1 or
version-2 record retagged with that value is rejected.

The Activity publishes the pending v3/16sp appearance only after the first
successfully accepted reader frame. A library-only launch therefore cannot
rewrite appearance state. Publication writes one fixed same-directory temporary
file, flushes and synchronizes its file descriptor, then requires
`ATOMIC_MOVE` with `REPLACE_EXISTING`. There is no non-atomic fallback. An
unsupported or failed atomic move preserves the old bytes and pending migration,
removes temporary residue best-effort, and visibly reports that the appearance
could not be saved. A later successfully accepted reader frame retries
publication. Load/save, missing/corrupt, and failure counters plus pending-
migration state remain visible to deterministic tests.

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
 restart probe and may uninstall its APKs when Gradle finishes. Reinstall both
 built APKs, then run the seed/force-stop/verify driver from the repository
 root:

```powershell
adb -s emulator-5554 install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r -t android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\android_port7_process_restart.ps1 -Serial emulator-5554
```

The driver requires each one-test half to pass, confirms that no target process
survives the external force-stop, then performs the book-reopen verification in
a fresh process. This is deterministic emulator coverage of each axis, not a
claim that every Cartesian combination or physical-device condition has been
accepted.

## Overlay reader chrome

Reader0 and Readerview0 resolve one borderless page for the full native reader
viewport. An ordinary library open or book reopen starts with chrome hidden;
only Activity recreation restores a transient visible-chrome state saved by
that Activity. Hidden chrome leaves the SurfaceView at its identity transform.
When controls are visible, Android uniformly scales and translates that same
already-presented Surface into the measured area between the host controls.
Showing or hiding chrome therefore does not rebuild pagination, redraw native
chrome, mutate a Reader0 page, change the semantic anchor, or save a location.
The transformed page remains the same canonical buffer and accessibility
bounds use the same host transform.

A valid short center tap toggles the host controls; left and right tap zones
retain previous/next behavior. The visible chrome contains Library, the
ellipsized title, a single-tap `Aa` appearance action, and read-only progress.
It intentionally has no visible Previous or Next button: page movement remains
available through page taps, horizontal swipes, hidden-chrome virtual actions,
and Page Up/Page Down or D-pad Left/Right.

A horizontal-dominant swipe must cross both the 48dp minimum and the host touch-
slop-derived threshold. Once classified, Java cancels the in-progress native
tap and requests exactly one page move through the existing native successful-
presentation gate. Vertical movement cancels the tap without turning a page.
Pause/resume, surface replacement, surface geometry change, and teardown clear
both Java and native gesture state so a pre-lifecycle DOWN cannot become a
post-lifecycle page move. Starting a chrome composition transition likewise
sends native CANCEL, advances a host transition generation, and gates further
gesture delivery until the transform is settled. A gesture crossing any of
those boundaries cannot become an accidental page turn. The full native buffer
is filled with the reader-page color, so there is no page-card border or
contrasting side frame.

For a cross-theme change, the host immediately installs a full-reader cover in
the target `readerPage` color above the Surface and reader chrome but below the
settings sheet. It removes the cover only after the matching generation is
successfully presented and two Java frames have elapsed. Bounded composed-frame
sampling found no bright sample in the tested reader entry/re-entry/exit,
settings open/close, surface-replacement, recreation, and warm-dark-to-OLED
phases. Latest-only retargeting and a settled no-op callback prevent a rapid
 request sequence from stranding an obsolete cover. On the preceding
 reserved-geometry candidate, a 19-frame physical iQOO composed-screen series
 spanned a Warm dark panel, OLED selection and
settings close, library return, reader re-entry, and center-tap chrome
hide/show. Inside the app bounds at y=88..2283, every frame had an
above-95%-luma pixel ratio of 0.0000; maximum mean luma was 0.1607 and maximum
above-85%-luma ratio was 0.0233. The maximum full-screen above-95%-luma ratio
was 0.0009, solely from system status icons outside those app bounds. This is
bounded captured-screen evidence, not subjective dark-room or perceptual
 no-flash acceptance, and it must be repeated for the current borderless APK.

The target-theme cover is also installed synchronously before the reader view
hierarchy replaces the library. It therefore covers the SurfaceView's initial
black compositor layer, not only later cross-theme changes. The cover is
removed only after the first successful native presentation and two Java
frames.

## Reader-entry performance policy

The Android debug native target retains symbols but compiles the native reader
with `-O2`; an `/Od`-equivalent native path was not representative of the
performance-sensitive product. Reader creation no longer synchronously warms
all EPUB spines before the first page. After a page is successfully presented,
the Java host schedules at most one remaining Reader0 location-cache step at a
time. Until that bounded work completes, progress may use a provisional local
page summary; completion requests a same-page metadata refresh and does not
change or persist the page. A terminal summary failure stops further warming
and shows a nonfatal, accessibility-visible progress warning. Warming also
stops after presentation retries are exhausted, so an unpresented page cannot
leave a 32ms deferred-work poll running indefinitely.

The current reader-entry clock begins at the first instruction of `showReader`
and ends after the first `ANativeWindow_unlockAndPost` whose frame satisfies all
native successful-presentation gates. It deliberately excludes input dispatch
before `showReader`, compositor display after the buffer is queued, and the two
Java frames before the target-theme cover is removed. The deterministic
emulator test retains a strict positive-evidence gate below 1500ms and proves
that the first location-warm step follows the accepted frame.

On the earlier post-feedback APK, the exact ignored EPUB's warm
same-process reader-entry samples were 825, 566, 582, 563, and 595ms (median
582ms); the final screen-record sample was 529ms. Its accepted first-frame stage
diagnostics identified native-window lock and unlock/post as the dominant
variable cost rather than Reader0 build, row fill, or draw. Those measurements
are historical until the current API 6 consumer is rerun. The latest Android-only
path fills rows directly, removes a redundant fill, and uses validated direct
ASCII/Latin-1 glyph lookup with a bounded fallback.

The superseded borderless/performance binary measured 220ms using a clock that
began inside native creation and is therefore not comparable with the current
`showReader` boundary. Its separate 411ms ADB-tap-to-observed-frame result is
also retained only as historical regression evidence. All debug-emulator
numbers are regression evidence, not a release SLA or a substitute for
physical-iQOO timing.

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

When Java chrome is visible, Library and Reader appearance are the only visible
actions, the native title is not a duplicate stop, Progress remains read-only,
and the virtual page remains available. There are no visible Previous or Next
buttons. When chrome is hidden, the provider exposes virtual Previous, Next,
and Progress nodes and clears stale virtual focus as ownership changes. Real
Tab/Shift+Tab input follows one deterministic named native/virtual chain; the
top/bottom chrome containers and raw Surface host are explicitly excluded as
blank stops. Directional recovery after an explicit focus clear, routing during
the hide-fade boundary, and disabled end-of-book routing all remain inside the
named chain. Page Up/Page Down and D-pad Left/Right provide direct non-gesture
navigation through the same successful-presentation gate. Enter, Space, and
D-pad center activate the focused page or navigation action; activating Progress
is a no-op that retains focus.

Instrumentation injects real forward/reverse keys and verifies the bounded
semantic packet, roles, names, values, ranges, dynamic ownership, focus order,
explicit focus-clear recovery, hidden-fade and end-of-book boundaries,
activation/no-op behavior, presentation gating, host-node consistency, 48dp
control bounds, the absence of visible Previous/Next controls, one physical tap
opening the `Aa` sheet, the settings sheet's complete option surface, and its
initial focus/z-order. On the earlier post-feedback APK, an iQOO UiAutomator sequence
 reached named
Next page, Library, Reader appearance, book-page text, and Previous page stops
without a blank container or raw-Surface stop. An independent closure audit
found no remaining P1/P2 focus issue.

This bridge is the Port 7 accessibility foundation, not complete publication
accessibility. Headings, lists, links, images/alt text, tables, selection,
annotations, language changes, and complex document reading semantics remain
future engine and product work. The earlier post-feedback APK's focused 130%
cases passed on the emulator in 5.651 seconds and on the iQOO in 4.804 seconds
before both scales were restored. Those results are historical regression
evidence for that APK, not large-text acceptance for the current API 6
candidate. Objective keyboard traversal is automated, but a fresh
physical run, audible TalkBack speech and touch-exploration judgment, hands-on
keyboard/switch use, and reduced-motion quality still require acceptance.

## Shared-package boundary

Port 7 keeps all Android appearance, persistence, typography acquisition,
touch, window, and accessibility-adapter policy inside 8vo.

- Reader0 is used only through `reader0.h` for canonical pagination, location
  navigation, frames, and page moves.
- UI0 receives a resolved product palette but gains no Android theme policy.
- Readerview0 continues to own portable chrome projection, semantic records,
  and content geometry; Android requests its distraction-free projection and
  owns the raster/control composition.
- `octavo_reader_justification.h` is an allocation-free 8vo policy shared by
  the Windows and Android raster paths. It consumes Reader0 API 6's canonical
  `soft_wrapped` row provenance and presentation metrics; it does not interpret
  EPUB, infer wrap boundaries, or paginate. Both paths use widened,
  overflow-safe exact-fill arithmetic and the same layout-whitespace policy.
- Windows theme IDs, labels, UI0 mappings, derived reader roles, and 8vo search/
  highlight extensions live in one stable `octavo_theme` catalog. Android's
  six reading palettes remain independently tuned host policy instead of being
  forced through a false color equivalence.
- Reader0 carries the platform-neutral full-measure composition and canonical
  soft-wrap provenance contract at `0.6.0-dev` / public API 6, pinned at
  `59e9efdaca17b316aa2b1f5a7be0cbdebf5e4c26`. Android appearance policy remains
  in 8vo. Ground0, UI0, and Readerview0 need no Port 7 product-policy change.
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
- companion re10 product/qualification builds at the shared Reader0 boundary
  and `--document_engine_smoke`;
- physical iQOO instrumentation and hands-on extended reading in Paper, Dusk,
  Warm dark, and OLED; and
- a dark-room review of discomfort, halation, minimum brightness, accent
  intensity, and every transition.

## Validation record

### Current source: objective gates pass, human review pending

- Exact dependency and architecture guards passed for Ground0
  `770b970b4655facfa9700c3d1025d96102365631`, Reader0
  `59e9efdaca17b316aa2b1f5a7be0cbdebf5e4c26`, UI0
  `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`, and Readerview0
  `f97f9d38cf857c2cff1f90357cf5d2e5cf40dc03`.
- Reader0 `0.6.0-dev`/API 6 passed its exact Ground0 guard, dependency audit,
  strict MSVC C11 `/W4 /WX` compile, `--reader-core-smoke`, independent
  `--host-smoke`, diff check, and clean-branch check.
- Companion re10 branch `android/port7-reader0-justification` at
  `5830f401750f7631131c4bc9c16d7235b88758a0` passed its strict product/
  qualification build, audits, targeted smokes, and
  `--document_engine_smoke` with four anchors, final spine 3, and hash
  `f3c13a55f0349720`; its branch and dependencies were clean.
- The strict Windows 8vo build and all seven public smokes passed. A clean Gradle
  `clean :app:assembleDebug :app:assembleDebugAndroidTest` completed in
  1 minute 54.9 seconds for both `arm64-v8a` and `x86_64`.
- The 3,516,438-byte main APK has SHA-256
  `9B454B9086A5BBD68BC04CEE1D71AE0DDBCCA9036043E351983D38ACE3888BAF`.
  The 1,041,003-byte test APK has SHA-256
  `27F16340E398EDB8815FC47758A3C8CACF8DED433832D2B71BB0AA3C78F29F6F`.
- On the API 36 x86_64 emulator, the ordered six-class matrix passed 36/36 in
  510.019 seconds of instrumentation time and 511.173 seconds wall time:
  appearance store 9, appearance 15, navigation 5, library 5, accessibility 1,
  and bootstrap 1.
- Emulator ProcessRestart passed seed 1/1 in 11.125 seconds, externally confirmed
  no surviving PID, and passed verify 1/1 in 10.247 seconds. At
  `font_scale=1.3`, Accessibility passed 1/1 in 31.047 seconds of
  instrumentation time and 32.807 seconds wall time; scale was restored to
  `1.0`. The crash buffer was empty.
- On the vivo I2019/iQOO 9 SE, Android 14/API 34 ARM64, the same matrix passed
  36/36 in 108.467 seconds of instrumentation time and 109.069 seconds wall
  time. ProcessRestart passed seed 1/1 in 1.498 seconds, confirmed no surviving
  PID, and passed verify 1/1 in 1.091 seconds. At 130% system text,
  Accessibility passed 1/1 in 4.881 seconds of instrumentation time and 5.587
  seconds wall time; scale was restored to `1.0`. The crash buffer was empty.
- The passing appearance-store and appearance cases prove non-mutating v1/v2
  load, historical v3/14sp publication only after the first successfully
  accepted reader frame, visible pending state after atomic-save failure, and
  retry after a later successful presentation.
- The fresh pretest backup was 9,141,760 bytes with SHA-256
  `245FA5B9B3E73E7DC9B1DFC5DCE1DDAEFE80F514C4DB9E8F3BA550280C7F9A1`.
  After the suite, all 63 files compared byte-exact; the catalog, appearance,
  and imported EPUB hashes remained exact.
- Restored cold Library start reported 287ms and showed one imported book plus
  the sample. Controlled imported-book Resume accepted semantic location
  `1:14:1756` with `first_ms=138`, 24ms total native stages, zero missing
  glyphs, and no visible reader controls.
- The exact snapshot was restored again after timing; the phone was left on the
  Library with catalog, appearance, and EPUB bytes exact. Device-side temporary
  validation state was removed, while the host backup remains ignored.
- Audible TalkBack, hands-on alternate-input and reduced-motion review, extended
  Paper/Dusk/OLED reading, and subjective dark-room comfort remain pending.

### Pre-closure API 6 candidate: historical evidence

The completed API 6 pre-closure binary passed the exact dependency/audit,
dual-ABI debug/test build, strict Windows/all-seven-public-smoke, process-restart,
130% accessibility, and crash-buffer gates. Its API 36 ordered suite passed
33/33 in 170.299 seconds of instrumentation time (171.639 seconds wall).

Its exact ignored backup replay published a v3/14sp record from a transitional
v2/18sp record. On the vivo I2019/iQOO 9 SE, the six-class matrix passed 33/33
in 103.741 seconds of instrumentation time (104.295 seconds wall); focused
justification passed 1/1 in 1.059 seconds. Restart and 130% accessibility probes
passed, all 26 restored files and the private EPUB digest were retained, cold
library start reported 287ms, and controlled Resume logged 135ms, zero missing
glyphs, and hidden chrome.

Those results predate non-mutating migration load, deferred v3 publication,
visible retry-on-failure coverage, and the additional migration/reader-entry
regressions. They are useful history but do not accept the current source.

### Immediately preceding API 5 full-measure candidate: historical evidence

The preceding API 5 source incorporated the 2026-08-03 hands-on findings plus
the first systemic full-measure Reader0/host correction and schema-default
migration to 14sp. Its evidence remains useful but does not accept the current
API 6 consumer:

- Exact clean dependency guards passed for Ground0
  `770b970b4655facfa9700c3d1025d96102365631`, Reader0
  `f41bd1c86cdcb1ef463ecdae0ec6d139f5355871`, UI0
  `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`, and Readerview0
  `f97f9d38cf857c2cff1f90357cf5d2e5cf40dc03`.
- The Android debug build passed for both `arm64-v8a` and `x86_64`.
- On the API 36 x86_64 emulator, the authoritative ordered suite passed 33/33
  with zero failures, errors, or skips in 138.917 seconds of XML test time:
  accessibility 1, appearance store 9, appearance 12, bootstrap 1, library 5,
  and navigation 5.
- The API 5 emulator suite exercised its then-current deterministic migration
  cases. A later audit superseded the claim that the ignored phone backup itself
  was version 1: its top-level state is version 2, Sepia, Literary, 18sp,
  Classic-spacing, Balanced-width, Publisher-alignment, theme-safe, and
  reduced-motion off. It is the bounded transitional v2/18 state; the
  pre-closure API 6 historical migration result is recorded above.
- At emulated iQOO geometry, 1080 x 2400 at 440dpi, the exact private ignored
  EPUB diagnostic sampled five eligible ordinary non-final rows with three or
  four interior gaps. The preceding path left 79--175px residuals; that API 5
  path filled the exact 800/800 or 928/928 measures. The focused diagnostic
  passed 1/1 in 7.902 seconds. No private text or diagnostic fixture is tracked.
- Reader0 at `f41bd1c86cdcb1ef463ecdae0ec6d139f5355871` passed its exact Ground0
  guard, dependency audit, MSVC `/W4 /WX` build, `--reader-core-smoke`, and
  `--host-smoke`; its worktree was clean.
- The strict Windows 8vo build and all seven public smokes passed against the
  updated Reader0 boundary.
- Companion re10 branch `android/port7-reader0-justification` at
  `789de8410924cb184a0e7aa485bd27fc7a5a8ab4` advanced only its Reader0 pin to
  `f41bd1c86cdcb1ef463ecdae0ec6d139f5355871`. It retained re10's own exact UI0
  `b1cf8e4fbe7e06b9799e251665bbe491ae4c22b5` and Readerview0
  `8b4be5faf3c3d5f997c834befa4de02a7840b934` closure. Its strict
  product/qualification `/W4 /WX` build passed, and
  `--document_engine_smoke` passed with four anchors, final spine 3, and hash
  `f3c13a55f0349720`. The worktree was clean; the branch was not pushed.

### Earlier post-feedback APK: historical evidence

The following results were collected after the first 2026-08-03 refinements but
before the full-measure Reader0/host correction and final legacy migration
policy. They remain useful regression evidence but do not accept the current
API 6 candidate:

- A fresh API 36 emulator process passed 33/33 in 96.223 seconds of XML time
  (Gradle 1m59). Its restart seed/force-stop/no-PID/verify probe, focused 130%
  system-text case, and crash-buffer review passed.
- The exact ignored private EPUB passed punctuation, publisher-hard-line verse,
  zero-missing-glyph, hidden-entry chrome, one-tap `Aa`, bidirectional-swipe,
  exact-reopen, pixel, and timing checks. Warm same-process reader-entry
  samples were 825, 566, 582, 563, and 595ms (median 582ms); the final
  screen-record sample was 529ms. Ordered Paper and Dusk entry captures had no
  near-black or bright cover frame under their recorded sampling limits.
- On the vivo I2019/iQOO 9 SE, Android 14/API 34 ARM64 at 1080 x 2400 and
  440dpi, a data-preserving install passed the selected six-class matrix 33/33
  in 104.855 seconds: accessibility 1, appearance store 9, appearance 12,
  bootstrap 1, library 5, and navigation 5.
- Its restart driver passed seed 1/1 in 1.584 seconds, externally confirmed no
  surviving PID, and passed verify 1/1 in 1.109 seconds. The 130% case passed
  1/1 in 4.804 seconds, the backed-up state and original scale were restored,
  and the crash buffer was empty.
- Its first preserved-state open measured 140ms, controlled warm reopens
  measured 121, 80, and 122ms (median 121ms), and the focused reopen measured
  102ms. Punctuation, publisher-hard-line verse, hidden entry, one-tap
  appearance, swipe hash restoration, exact reopen, and the absence of visible
  Previous/Next controls passed.
- A blind-coordinate diagnostic burst briefly navigated Previous when a gated
  center tap was intentionally swallowed. Controlled state-verified cycles
  passed, identifying a harness-only ordering artifact.

The current source objective gates pass as recorded above. Audible TalkBack and
touch-exploration judgment, hands-on alternate-input and reduced-motion review,
extended reading in the supported themes, and subjective dark-room acceptance
remain pending.

### Superseded borderless/performance binary

The following emulator and desktop evidence was recorded through 2026-08-03 for
the previous borderless/performance binary. It remains regression history, not
post-feedback validation:

- Exact clean dependency guards passed at the Ground0, Reader0, UI0, and
  Readerview0 revisions listed below.
- `:app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac` and the final
  `:app:assembleDebug` passed for both `arm64-v8a` and `x86_64`.
- The final API 36 x86_64 emulator passed 27/27 ordered tests in 406.087
  seconds:
  accessibility 1, appearance-store/migration/palette 6, reader appearance 11,
  bootstrap 1, Port 6 library regression 5, and navigation regression 3.
- The suite proves a continuous page-colored native buffer, canonical
  full-viewport geometry, visible-chrome scale/translation and hidden identity,
  chrome/page-state neutrality, transition gesture cancellation, target-theme
  entry coverage, shared publisher justification versus ragged-right pixels,
  16sp migration, deferred location-summary completion and visible terminal
  failure, post-presentation-terminal poll shutdown, and the first-frame timing
  gate.
- The true process-death driver passed seed 1/1 in 10.093 seconds, confirmed no
  surviving target process after force-stop, and passed verify 1/1 in 9.519
  seconds.
- At `font_scale=1.3`, the strengthened accessibility/settings case passed 1/1
  in 19.495 seconds; the emulator was restored to `font_scale=1.0`.
- The exact ignored Gardens of the Moon Resume repro measured 411ms from ADB
  tap to observed frame and 220ms inside native reader creation/presentation;
  the 220ms native-only boundary is not comparable with the current metric.
  Nominal 50ms and 150ms captures still show the library; the 250ms through
  700ms captures show the saved page. Across all six app-bound captures,
  black/near-black pixel ratios were 0.000000. Copyrighted frames and video
  remain ignored and untracked under
  `local/validation/android-port7/api36/gotm-resume-remediation/`.
- One stale pre-final crash-buffer line was traced to an emulator
  `system_server` pre-watchdog stack collection: `keystore2` PID 276 was
  healthy, but `crash_dump64` hit its platform SELinux ptrace denial. No 8vo
  PID or tombstone was involved, and the final ordered, restart, and large-text
  runs added no crash entry. After clearing that system-only line, the exact
  APK bootstrap/lifecycle smoke passed 1/1 in 16.660 seconds and the crash
  buffer remained empty.
- The strict Windows 8vo build and all seven public smokes passed, including
  `theme_catalog=stable6` and Reader View hash `e29cfd3afeea51a1`.
- Unchanged re10 at `6b6112a1c1111743a8c57631eca328ad424fe4ed`
  passed its exact guards, optimized product/qualification build, image budget,
  and four-anchor document-engine smoke with final spine 3 and hash
  `f3c13a55f0349720`. Every participating dependency remained clean.

The current API 6 candidate has not been pushed or merged. Its objective gates
pass as recorded above. Audible accessibility, alternate-input, reduced-motion,
extended-reading, and dark-room gates remain pending.

### Superseded pre-refinement evidence

The following 2026-08-01/02 record describes the preceding reserved-geometry
candidate. It remains useful historical evidence but is not acceptance evidence
for either post-feedback candidate:

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

The current source objective gates pass as recorded above. Audible TalkBack
speech and touch-exploration judgment, hands-on keyboard/switch and reduced-
motion review, extended Paper, Dusk, Warm dark, and OLED reading, and subjective
dark-room review remain pending. LCD-class dark-room evidence remains required
when an appropriate display is available.

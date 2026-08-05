# Android roadmap to the premium reader

Status: directional roadmap adopted 2026-08-01 and reviewed 2026-08-04. Android
Ports 0-7 are accepted; Port 7 was pushed and merged after its Reader0, re10,
exact 8vo guard/build, 36/36 emulator and iQOO, ProcessRestart, 130%
accessibility, crash, byte-exact backup/restore, and hands-on reader-quality
closure. Port 8 is a corrected structural-navigation candidate against Reader0
`0.7.0-dev` / API 7 at `5fe949d88258cd96884c44b69e4f4ab6f27dc394`.
Its corrected Reader0/re10 and strict Windows 8vo gates pass; earlier Port 8
emulator, iQOO, restore, and real-book results are predecessor evidence. The
corrected API 36 emulator and API 34 iQOO, external-restart, accessibility/
reduced-motion, byte-exact restore, and controlled real-book gates pass.
Audible TalkBack, UI polish, and user subjective/manual acceptance remain
pending. Later numbering and boundaries may change as evidence is collected.

This roadmap turns `android_product_vision.md` and
`android_feature_parity.md` into independently testable vertical slices. It is
not a promise to reproduce Kindle screen by screen, and it does not defer
quality until a final polish phase.

## Accepted foundation and current candidate

Ports 0-7 establish the infrastructure on which the product can safely grow:

- exact shared-source Android build and native application ownership;
- lifecycle-, inset-, surface-, recreation-, and presentation-safe rendering;
- canonical Reader0 pagination and deterministic previous/next navigation;
- readable proportional Android serif rasterization and handset geometry;
- user-selected EPUB import into bounded app-private storage;
- a library-first catalog with multiple books, digest identity, deduplication,
  safe removal, and catalog recovery; and
- independent successfully presented locations with exact same-layout resume.

Port 6 is a functional foundation, not a claim of Kindle-level feature or
visual parity.

Port 7 is the accepted appearance foundation. It adds the appearance,
semantic-location reflow, borderless host-composited chrome,
reader-entry-performance, and accessibility-bridge foundation described
below. Ports 0-7 are the accepted Android baseline while Port 8 remains local.

## Delivery method

Each port should be a complete user-visible slice with one clear ownership
story, explicit exclusions, deterministic fixtures, migrations where needed,
and physical acceptance. Work is sequenced to avoid three common traps:

1. **A feature-complete but uncomfortable reader.** Reading comfort and visual
   language start in Port 7.
2. **Polished host UI over duplicated engine policy.** EPUB interpretation,
   pagination, locations, search anchoring, and selection anchoring remain in
   platform-neutral shared packages.
3. **A late reliability rewrite.** Atomic state, successful-presentation
   gating, bounded work, recovery, and cross-consumer tests remain required in
   every slice.

Rendering fidelity, performance, accessibility, and data durability are
continuous lanes. Shared Reader0 or Readerview0 work may be interleaved when a
vertical slice proves it necessary, but it must remain platform-neutral and
pass unchanged-consumer gates in 8vo and re10.

## Port 7: premium reader appearance foundation

The accepted Port 7 implementation makes one mainstream reflowable EPUB
substantially more configurable and coherent before adding another large
feature surface. Extended-session comfort remains an acceptance question, not
an implementation claim.

### Implemented scope

- 8vo semantic design tokens cover page, chrome, surfaces, text hierarchy,
  dividers, accents, selection, errors, spacing, shape, icon size, and motion.
- A calm immersive reader shell presents one borderless canonical
  full-viewport native page. Ordinary entry and book reopen start hidden;
  Activity recreation alone may restore transient visible chrome. Hidden chrome
  is the identity composition; visible chrome uniformly scales and translates
  that same Surface between Library/title/single-tap `Aa` and read-only
  progress without visible Previous/Next buttons, repagination, redraw, or
  semantic-location change. Gestures are canceled and gated across the
  transition.
- The reader-preferences surface offers six font sizes, Android generic serif
  and sans-serif families, four line-spacing choices, three margins/content
  widths, supported publisher/ragged-right alignment, explicit publisher-color
  policy, and reduced motion. The default is 16sp and no font asset is bundled.
- Paper, sepia, dusk, warm-dark, OLED, and high-contrast themes are available.
- Every reader, chrome, and system-bar role is tuned semantically per theme
  instead of mechanically inverting one palette.
- A synchronous target-theme cover masks the initial native Surface layer
  until the first successfully presented frame; it avoids a black or
  wrong-theme reader-entry frame.
- One checksummed version-3 global appearance persists independently from
  per-book locations. New, missing, or corrupt state uses 16sp. Loading a valid
  version-1 18sp default, version-2 16sp default, or transitional version-2
  18sp value returns a 16sp in-memory appearance while preserving every other
  valid field, marks migration pending, and leaves the old bytes untouched.
  The transitional value is a bounded pre-origin ambiguity: the version-2
  writer could republish an inherited version-1 18sp default after a non-font
  change. Inherited and explicit v2/18 origins cannot be distinguished, so
  every v2/18 record migrates. Version-2 21/24/28sp and every valid version-3
  choice, including 14sp, remain exact; impossible old-schema 14sp records are
  rejected.
  Version 3 publishes only after the first successfully accepted reader frame.
  Failed atomic publication remains pending, is visibly reported, and retries
  after a later successful presentation. Per-book overrides are explicitly
  deferred.
- Layout-affecting changes rebuild Reader0 pagination and reconstruct
  the canonical page containing the last successfully presented semantic
  location.
- Reader0 `0.6.0-dev`/API 6 stores authoritative `soft_wrapped` provenance in
  canonical styled rows. Measured space/em-dash wraps are true; final/hard-line
  and image boundaries are false. One allocation-free 8vo word-spacing plan
  shared by desktop and Android consumes that field and uses overflow-safe
  widened arithmetic to fill eligible Publisher prose to the available measure.
  Layout-only trailing whitespace is excluded from measurement/drawing while a
  visible em dash remains ink. Ragged right and intentional hard lines retain
  natural spacing; Reader0 remains the line-breaking authority.
- Android uses one bounded sparse four-style atlas with a sorted 233-codepoint
  map covering printable ASCII, U+00A0..U+00FF, and 42 curated publication
  characters. Native lookup is bounded, missing glyphs are diagnostic, and this
  remains deliberately short of full Unicode shaping/fallback.
- The shared Windows theme catalog centralizes six stable desktop IDs, labels,
  UI0 mappings, and reader/search/highlight roles. Android palettes are
  intentionally independently tuned for Android surfaces and night reading.
- Android native debug builds use `-O2` with symbols, and whole-book
  location metadata no longer blocks first presentation. Bounded one-spine
  warming starts only after a successful frame and refreshes same-page
  metadata; terminal failure is visible and nonfatal, presentation exhaustion
  stops polling, and privacy-safe first-frame timing is retained as evidence.
- Java coalesces preview changes and retains the successful-presentation gate so
  rapid adjustments cannot save or expose an unpresented state.
- Native Android controls and the custom reader's essential actions are
  exposed through the accessibility bridge pattern used by later ports. Hidden
  virtual Previous/Next actions and Page Up/Page Down or D-pad navigation remain
  available without visible Previous/Next buttons, with deterministic named
  Tab/Shift+Tab routing that excludes blank host stops.
- Horizontal-dominant bidirectional swipes cancel the native tap and enter the
  same successful-presentation gate as tap, keyboard, and accessibility moves.
  Lifecycle, surface replacement/geometry change, and chrome transitions clear
  stale gesture state.
- Diagnostic state and stable palette hashes support deterministic evidence
  for themes, preference extremes, insets, rotation, recreation, and
  compact/large viewports.

Port 7 implementation and objective closure are accepted. Reader0, companion re10,
exact 8vo guard/build, 36/36 API 36 and iQOO matrices, ProcessRestart, 130%
accessibility, crash, and byte-exact backup/restore gates passed. Every completed
33-test API 6 run and all earlier records are historical. Broader comfort and
hands-on accessibility review remain product follow-up rather than Port 7
acceptance evidence.

### Deliberately out of Port 7

- table of contents, Page Flip-class preview, in-book search, selection,
  annotations, bookmarks, cover library, collections, or cloud sync;
- full embedded-font, complex-script, image, table, or fixed-layout parity;
- copying Kindle controls, labels, assets, coordinates, or animations; and
- a general Android design framework in shared packages.

### Port 7 acceptance

In addition to permanent build/regression gates, require:

- exact semantic position across every layout-affecting preference, process
  recreation, surface replacement, pause/resume, rotation, and book reopen;
- readable output and no clipped content at the smallest/largest supported
  font, spacing, and margin combinations;
- consistent reader, preferences surface, library return, status bar,
  navigation bar, launch, and transition colors for every theme;
- no bright intermediate frame when entering, leaving, recreating, or
  switching dark themes;
- automated pixel/semantic evidence that theme and typography changes occur;
- TalkBack labels/actions, focus order, large system text, touch targets, and
  reduced-motion review for the new controls;
- a hands-on extended-reading pass on the iQOO in Paper, Dusk, Warm dark, and OLED;
  and
- dark-room comparison of warm-dark and OLED modes, recording discomfort,
  halation, minimum brightness, accent intensity, and any white flash.

### Current validation status

The current Reader0 and re10 revisions above passed their final shared-consumer
gates. Exact 8vo guards/audit, strict Windows/all-seven-public-smoke, and the
clean 1-minute-54.9-second dual-ABI Android build passed.

The final ordered matrix passed 36/36 on the API 36 emulator in 510.019 seconds
of instrumentation time (511.173 seconds wall) and 36/36 on the iQOO in 108.467
seconds (109.069 seconds wall): appearance store 9, appearance 15, navigation 5,
library 5, accessibility 1, and bootstrap 1. ProcessRestart, 130% accessibility,
scale restoration, and empty crash-buffer checks passed on both targets. The
passing migration cases prove non-mutating v1/v2 load, accepted-frame-only
v3/14sp publication, visible pending failure, and later-presentation retry.

The 9,141,760-byte pretest backup restored all 63 files byte-exact. Restored
cold Library start was 287ms, and controlled imported-book Resume reached the
accepted semantic location in 138ms with 24ms total native stages, zero missing
glyphs, and no visible reader controls.

The completed API 6 pre-closure binary passed exact 8vo guards/audit, dual-ABI,
strict Windows/public-smoke, API 36 33/33, restart, 130% accessibility, crash,
backup replay, and iQOO 33/33 gates. Its recorded times were 170.299 seconds
instrumentation on the emulator and 103.741 seconds on the iQOO; its controlled
Resume logged 135ms with zero missing glyphs and hidden chrome. These are
historical results because they predate staged migration durability and the
additional appearance/reader-entry regressions. They do not accept the current
source.

The immediately preceding API 5 full-measure candidate passed the exact
dependency guards and dual-ABI debug build. On the API 36 x86_64 emulator, its
authoritative ordered suite passed 33/33 with zero failures, errors, or skips in
138.917 seconds of XML test time: accessibility 1, appearance store 9,
appearance 12, bootstrap 1, library 5, and navigation 5. These results are
historical evidence and did not accept the later final Port 7 API 6 source.

The API 5 emulator suite exercised its then-current deterministic migration
cases. A later audit superseded the claim that the ignored phone backup itself
was version 1: its top-level appearance is transitional version 2 at 18sp. The
pre-closure API 6 historical migration result is recorded above. At emulated
iQOO geometry, the private ignored EPUB diagnostic sampled five eligible
ordinary non-final rows with three or four gaps: prior residuals were 79--175px
and that API 5 path filled the exact 800/800 or 928/928 measures. The
focused diagnostic passed 1/1 in 7.902 seconds without tracking private text.

Reader0 API 5 commit `f41bd1c86cdcb1ef463ecdae0ec6d139f5355871` passed its exact guard,
dependency audit, `/W4 /WX` build, core smoke, and host smoke. Strict Windows
8vo and all seven public smokes passed. Companion re10 branch
`android/port7-reader0-justification` at
`789de8410924cb184a0e7aa485bd27fc7a5a8ab4` advanced only its Reader0 pin and
passed its strict `/W4 /WX` product/qualification build plus four-anchor
`--document_engine_smoke` with final spine 3 and hash `f3c13a55f0349720`; the
worktree was clean.

The still earlier post-feedback APK's API 36 33/33 run in 96.223
seconds, restart, 130% system-text, crash-buffer, private-book visual/timing, and
Paper/Dusk transition evidence are historical. Its iQOO matrix passed 33/33 in
104.855 seconds, and its restart, 130% system-text, crash, visual, swipe, reopen,
and timing evidence also passed. Its first open was 140ms, median controlled
warm reopen was 121ms, and focused reopen was 102ms. These results do not accept
the later final Port 7 API 6 source. The older borderless/performance 27/27 and
411ms/220ms results remain a separate superseded record.

Port 7 was accepted, pushed, and merged after the final API 6 gates and manual
reader-quality review. Audible TalkBack, broader alternate-input/reduced-motion
coverage, extended theme reading, and subjective dark-room comfort remain
product follow-up, not evidence transferred to the unvalidated Port 8 candidate.

## Android Port 8 current milestone

These are capability slices, not frozen port numbers. Before beginning each
one, convert its relevant parity rows into a bounded milestone contract.

### Structural navigation

The implementation candidate on `android/port8-structural-navigation` follows
the bounded [Port 8 contract](android_port8.md). Reader0 API 7 at
`5fe949d88258cd96884c44b69e4f4ab6f27dc394` owns EPUB structural
interpretation, canonical destinations, exact chapter resolution, contained
image-only anchors, small-spine pagination, and bounded session history. 8vo
owns the calm Android surfaces and the transaction that prevents an unpresented
jump, Return/Forward move, durable position, or progress-display choice from
being committed.

The current corrective slice closes six user-reported defects:

- MAPS/image-only Contents destinations use a contained synthetic Reader0
  anchor immediately and after reopen;
- predecessor spines at or below 16 KiB paginate canonically from byte zero,
  removing path-dependent ordinary and intermittent sparse reverse pages;
- visual image-only map pages are presented instead of appearing blank;
- Chapter Go-to calls Reader0 instead of treating a number as a Contents index;
- exact namespace-qualified `epub:type` semantics win and the numbered-label
  fallback rejects malformed tails, gaps, reversals, and duplicates; and
- the content rectangle moves down without changing its height or row capacity.

The slice also adds narrow image-only and in-flow image presentation. Reader0
owns resources, anchors, placement, and `visual_units`; Java owns serial
platform decode with per-resource bounds; native owns a deterministic
32-entry/32-MiB LRU with current-frame pinning, aspect fit, terminal fallback,
and painting. A prepared-frame token makes the first image snapshot the single
static build, requires verification/present reuse, rejects stale identity,
retains bounded failed attempts for retry, and is consumed only after accepted
presentation and commit. Each Java preparation also caps cumulative encoded
input at 16 MiB and cumulative decoded input at 8,388,608 pixels, terminally
marking the exhausted and remaining unavailable resources `CacheFull`.
Reader0 applies the remaining encoded-byte allowance before allocating or
decompressing the selected ZIP entry, with a distinct limit result so corrupt
or missing resources remain isolated failures. Its strict adversarial smoke
covers compressed oversize, malformed metadata, forged sizes, and arena
rollback. Windows 8vo and re10 adopt the same bounded getter at 32 MiB.
Deterministic API 36/API 34 validation and controlled presentation of three
real-book map leaves pass; broader media-fidelity review remains required.

Corrected Reader0 and companion re10 at
`b1c264f027c90bec480677bfeadfa5e0728776a8` pass their strict gates. The
corrected strict Windows 8vo build and all seven public smokes also pass in 19.6
seconds wall. The corrected API 36 matrix passed 67/67 in 468.395 seconds of XML time
(494.0 seconds wall), after 5/5 presentation-deferral stress and an 11/11 image/
prepared-frame matrix. External restart and 15/15 130%-text/animations-off
probes passed, settings restored exactly, and crash/exit review found no crash
or ANR. The earlier 56/56 API 36, restart, 15/15 large-text/reduced-animation,
56/56 iQOO, restore, and private-book checks remain predecessor evidence only.

On the corrected source, the API 34 ARM64 iQOO ordinary matrix passed 67/67 in
170.878 seconds of instrumentation time (171.449 seconds wall). Its confirmed-
force-stop restore passed with a 1.987-second seed, 1.135-second verification,
and 4.908-second wall time. The 130%-text/animations-off matrix passed 15/15 in
16.185 seconds of instrumentation time (18.591 seconds wall), followed by exact
restoration of font scale `1.0`, window and transition scales `1.0`, and the
previously absent animator-scale key. The crash buffer was empty and exit
history contained only expected `USER REQUESTED` and `PACKAGE UPDATED` records,
with no crash or ANR. Controlled real-book review passed the MAPS, three-map-
leaf, Chapter One/Two, exact Return, waited reverse-navigation, full-page, top-
padding, and no-transition-flash checks. The original 26 app files and 4,751,505
payload bytes were restored byte-exact; archive SHA-256
`52C4C27FA8E8D4C268950D6AB918D72DA130864D94556945BD815B1D12A901F2` and
manifest SHA-256
`A060016D369EC0E8902070A10206E09D82BC27BBACEB387F872F2C669F5D0B94` match the
original backup. Audible TalkBack, UI polish, and user subjective/manual
acceptance remain pending, so no final Port 8 acceptance or 8vo hash is claimed.

The 2026-08-05 visual follow-up changes the fresh default to 16sp, establishes
two base top reserves and one base bottom reserve with canonical Reader0 reflow,
and gives the Library one synchronous 16dp outer gutter. Its API 36 ordinary
matrix passed 67/67 and its API 34 ARM64 repeat passed 67/67 in 167.136 seconds;
both external restart and 15/15 130%-text/disabled-animation gates passed. The
iQOO Library measured x=44 through x=1036 on a 1080px display, settings restored
exactly, the crash buffer remained empty, and all 26 original app files restored
byte-exact with manifest SHA-256
`9EAF4BC7754F53F1FD546C8447E9D474F41BF5988B6071430CB3D1163AE5B0CC`.

- hierarchical table of contents with current-section state;
- go-to page/location/percentage and chapter navigation;
- a reversible navigation history shared by TOC, links, footnotes, search, and
  annotations; and
- progress-display choices that remain coherent after reflow.

### Spatial preview and scrubbing

- thumbnail or lightweight page previews generated from canonical Reader0
  state;
- chapter landmarks and a responsive scrubber;
- browsing that never changes the committed reading position until selected;
  and
- a conspicuous, reliable return to the original page.

### In-book search

- bounded local indexing and Unicode-aware matching;
- grouped results with chapter context and useful snippets;
- active-hit emphasis, next/previous, jump, and return history; and
- index invalidation/migration without blocking ordinary reading.

### Selection, bookmarks, highlights, and notes

- platform-neutral durable text anchors and styled selection geometry;
- Android handles, contextual actions, scrolling, and accessible alternatives;
- bookmarks, theme-tuned multi-color highlights, and notes; and
- atomic local persistence with recovery before synchronization is attempted.

### Annotations workspace

- browse and filter by type, color, chapter, date, and favorite;
- useful excerpts and direct navigation with return history;
- annotation search; and
- user-controlled Markdown/plain-text/structured export and Android sharing.

### Premium library

- cover extraction/cache and responsive grid/list modes;
- author, series, language, progress, and file metadata;
- sort, filter, collections, library search, and reading-status workflows;
- multi-import with visible progress and cancellation; and
- removal confirmation/undo plus locate, re-import, repair, and complete
  portable backup.

### Google Drive synchronization

- specify portable record formats, identity, revisions, tombstones, migration,
  merge rules, and a threat model before authentication UI;
- synchronize settings, positions, annotations, and catalog metadata first;
- make book-file synchronization explicit and optional;
- use least-privilege Google authorization, observable queue/progress/error
  states, retry, metered-network policy, and deterministic conflict tests; and
- preserve complete offline operation and export without an 8vo service.

Google Drive can use its application-data area for app-owned records, while
user-visible files may be more appropriate for portable backups or books.
That allocation is a sync-milestone design decision, not a Port 7 dependency.
The current API distinction is documented at
<https://developers.google.com/workspace/drive/api/guides/appdata>.

### Self-hosted storage and advanced reading tools

After the Google Drive record and conflict model are proven:

- design the second provider and only then extract the minimum shared sync
  boundary needed for Nextcloud/WebDAV or S3-compatible storage;
- add offline dictionary/vocabulary workflows, optional translation/handoff,
  and transparent local entity assistance; and
- separately scope fixed-layout, comics, audiobook/read-along, or additional
  document formats instead of folding them into the EPUB host.

## Continuous rendering-fidelity lane

Reader quality cannot wait behind the UI sequence. Each encountered real-book
defect should become a platform-neutral deterministic fixture and land in the
smallest responsible package. The long-term lane includes:

- complete font fallback and script-aware shaping;
- embedded fonts with license and malformed-input handling;
- language-aware hyphenation, ligatures, script-aware advanced justification,
  bidi, and RTL page direction;
- images, captions/alt text, zoom, and memory-bounded decode;
- tables, lists, poetry, block quotes, drop caps, links, footnotes, and MathML;
- publisher styling balanced against explicit reader overrides; and
- robust malformed/untrusted EPUB handling.

Port 8's narrow frame-image path does not complete this broader media-fidelity
lane: captions/alt text, zoom/pan, formats, color policy, and representative
bomb/lifecycle coverage remain later work.

Shared changes must be qualified in both 8vo and re10. Android host code must
not reproduce the document engine to achieve a screenshot.

## Permanent release gates

Every milestone keeps the existing exact-dependency, both-ABI Android,
emulator, strict Windows 8vo, unchanged re10, and physical-device gates. Add
the following as the product grows:

- versioned migration from every publicly retained durable record;
- process death, low storage, interrupted write/import/sync, corrupt state,
  missing book, and offline recovery tests;
- visual baselines across supported viewports, font scales, themes, system
  bars, and representative publication structures;
- TalkBack, focus order, alternate actions, large display/text, contrast, and
  reduced-motion checks;
- explicit cold-open, page-turn, search, selection, import, memory, storage,
  and battery budgets once representative fixtures exist;
- Android crash/ANR evidence and zero native/render/document-engine failures;
- physical touch and prolonged-reading validation; and
- a hands-on parity review against the neutral requirement in
  `android_feature_parity.md`.

## Definition of the ultimate goal

The Android port is complete only when a reader can manage a substantial
user-owned library, comfortably read representative books for hours, find and
navigate content, create and recover knowledge, move safely between devices
using user-controlled storage, and use the product accessibly without an 8vo
or Amazon service—and when those experiences feel coherent, fast, reliable,
and intentionally designed rather than merely present.

# Android roadmap to the premium reader

Status: directional roadmap adopted 2026-08-01. The current Port 7 refinement
is implemented on its milestone branch and awaiting a new physical-iQOO run
and formal acceptance; later numbering and boundaries may change as evidence
is collected.

This roadmap turns `android_product_vision.md` and
`android_feature_parity.md` into independently testable vertical slices. It is
not a promise to reproduce Kindle screen by screen, and it does not defer
quality until a final polish phase.

## Accepted foundation and current candidate

Ports 0-6 establish the infrastructure on which the product can safely grow:

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

Port 7 is the current implementation candidate. It adds the appearance,
semantic-location reflow, borderless host-composited chrome,
reader-entry-performance, and accessibility-bridge foundation described
below. Until its full acceptance matrix passes, Ports 0-6 remain the accepted
Android baseline.

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

The Port 7 implementation candidate makes one mainstream reflowable EPUB
substantially more configurable and coherent before adding another large
feature surface. Extended-session comfort remains an acceptance question, not
an implementation claim.

### Implemented scope

- 8vo semantic design tokens cover page, chrome, surfaces, text hierarchy,
  dividers, accents, selection, errors, spacing, shape, icon size, and motion.
- A calm immersive reader shell presents one borderless canonical
  full-viewport native page. Hidden chrome is the identity composition;
  visible chrome uniformly scales and translates that same Surface between
  Android controls without repagination, redraw, or semantic-location change.
  Gestures are canceled and gated across the transition.
- The reader-preferences surface offers five font sizes, Android generic serif
  and sans-serif families, four line-spacing choices, three margins/content
  widths, supported publisher/ragged-right alignment, explicit publisher-color
  policy, and reduced motion. The default is 16sp and no font asset is bundled.
- Paper, sepia, dusk, warm-dark, OLED, and high-contrast themes are available.
- Every reader, chrome, and system-bar role is tuned semantically per theme
  instead of mechanically inverting one palette.
- A synchronous target-theme cover masks the initial native Surface layer
  until the first successfully presented frame; it avoids a black or
  wrong-theme reader-entry frame.
- One checksummed version-2 global appearance persists independently from
  per-book locations. Its migration changes only an exact version-1
  all-default 18sp record to the 16sp default. Version 1 stored no separate
  intent bit; every other version-1 tuple and any version-2 18sp choice remains
  unchanged. Per-book overrides are explicitly deferred.
- Layout-affecting changes rebuild Reader0 pagination and reconstruct
  the canonical page containing the last successfully presented semantic
  location.
- Windows and Android use one allocation-free 8vo word-spacing plan over
  validated Reader0 rows. Publisher mode applies controlled inter-word spacing
  to eligible rows, while Ragged right retains natural spacing; Reader0 remains
  the line-breaking authority.
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
  exposed through the accessibility bridge pattern used by later ports, with
  deterministic named Tab/Shift+Tab routing that excludes blank host stops.
- Diagnostic state and stable palette hashes support deterministic evidence
  for themes, preference extremes, insets, rotation, recreation, and
  compact/large viewports.

Implementation of the behavior and diagnostic hooks is present, and the
current API 36 emulator/visual matrix has run. Physical-iQOO evidence from the
preceding candidate is historical and superseded; the current APK still needs
its physical automated and hands-on passes. Human comfort and hands-on
accessibility acceptance remain separate obligations.

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
- a hands-on extended-reading pass on the iQOO in Paper, Warm dark, and OLED;
  and
- dark-room comparison of warm-dark and OLED modes, recording discomfort,
  halation, minimum brightness, accent intensity, and any white flash.

### Current validation status

The current API 36 x86_64 emulator matrix passed 27/27 ordered tests at the
default system font scale, covering the supported preference values,
borderless full-viewport composition, compact/large viewports,
portrait/landscape, lifecycle, rotation, surface replacement, recreation,
accessibility semantics, and deterministic pixel evidence. Its externally
force-stopped fresh-process seed and verification probes passed, the separate
130% system-text run passed, the font scale was restored to 1.0, and the crash
buffer was empty.

For an already imported user-owned *Gardens of the Moon* EPUB,
Library-to-Resume was externally observed at 411ms and native
creation-to-success at 220ms. Samples across that transition contained no
black or near-black app frame. Both Android ABIs built, the strict Windows 8vo
suite passed 7/7 public smokes, and unchanged re10 passed its strict build and
four-anchor document-engine smoke. Exact timings, finite-matrix limits, pins,
and bounded transition evidence are recorded in
[android_port7.md](android_port7.md).

The preceding Port 7 candidate's 23/23 Android 14/API 34 iQOO run, physical
palette captures, and UiAutomator traversal are retained only as historical
evidence; they do not accept the current APK. A new iQOO instrumentation,
process-restart, large-text, real-book timing, and hands-on run is pending,
along with audible TalkBack, keyboard/switch and reduced-motion review,
extended Paper, Warm dark, and OLED reading, and subjective dark-room comfort
review.

## Candidate sequence after Port 7 acceptance

These are capability slices, not frozen port numbers. Before beginning each
one, convert its relevant parity rows into a bounded milestone contract.

### Structural navigation

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

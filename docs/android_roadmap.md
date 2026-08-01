# Android roadmap to the premium reader

Status: directional roadmap adopted 2026-08-01. Port 7 is implemented on its
milestone branch and awaiting formal acceptance; later numbering and
boundaries may change as evidence is collected.

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
semantic-location reflow, overlay chrome, and accessibility-bridge foundation
described below. Until its full acceptance matrix passes, Ports 0-6 remain the
accepted Android baseline.

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
- A calm immersive reader shell shows and hides controls over stable page
  geometry. Final emulator automation covers page-state neutrality, measured
  chrome occlusion, hidden-band tap rejection, and composed dark-transition
  pixels; physical touch and comfort remain acceptance gates.
- The reader-preferences surface offers five font sizes, Android generic serif
  and sans-serif families, four line-spacing choices, three margins/content
  widths, supported publisher/ragged-right alignment, explicit publisher-color
  policy, and reduced motion. No font asset is bundled.
- Paper, sepia, dusk, warm-dark, OLED, and high-contrast themes are available.
- Every reader, chrome, and system-bar role is tuned semantically per theme
  instead of mechanically inverting one palette.
- One versioned, checksummed global appearance persists independently from
  per-book locations. Per-book overrides are explicitly deferred.
- Layout-affecting changes rebuild Reader0 pagination and reconstruct
  the canonical page containing the last successfully presented semantic
  location.
- Java coalesces preview changes and retains the successful-presentation gate so
  rapid adjustments cannot save or expose an unpresented state.
- Native Android controls and the custom reader's essential actions are
  exposed through the accessibility bridge pattern used by later ports.
- Diagnostic state and stable palette hashes support deterministic evidence
  for themes, preference extremes, insets, rotation, recreation, and
  compact/large viewports.

Implementation of the behavior and diagnostic hooks is present, and the final
API 36 emulator/visual matrix has run. Physical-device and human comfort/
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
- a hands-on extended-reading pass on the iQOO in light and warm-dark themes;
  and
- dark-room comparison of warm-dark and OLED modes, recording discomfort,
  halation, minimum brightness, accent intensity, and any white flash.

### Current validation status

The final API 36 x86_64 emulator matrix passed 23/23 ordered tests at the
default system font scale, sequentially exercising every supported preference
value plus compact/large viewports, portrait/landscape, lifecycle, rotation,
surface replacement, and recreation. Both halves of an externally
force-stopped fresh-process probe and a separate 130% system-text
accessibility/settings run passed. Both Android ABIs built, the emulator crash
buffer was empty, the strict Windows 8vo suite passed 7/7 public smokes, and
unchanged re10 passed its strict build and four-anchor document-engine smoke.
Exact timings, finite-matrix limits, pins, and coverage are recorded in
[android_port7.md](android_port7.md). Live TalkBack and alternate-input review,
physical iQOO instrumentation, extended reading,
and dark-room review remain pending.

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
- language-aware hyphenation, ligatures, justification, bidi, and RTL page
  direction;
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

# Android Port 8: structural reader navigation

Status: corrected candidate on `android/port8-structural-navigation`, based
exactly on accepted Port 7 commit
`de7ba5dd5c5730cfb333bb5968d8cf7380203ecd`.
The shared contract is Reader0 `0.7.0-dev` / public API 7 at
`5fe949d88258cd96884c44b69e4f4ab6f27dc394`. Its companion re10 adoption
revision is `b1c264f027c90bec480677bfeadfa5e0728776a8`.
The corrected source closes six reported navigation, pagination, image-page,
chapter-targeting, and top-padding defects; adds bounded image-only and in-flow
image presentation; and defines a prepared-frame reuse contract. Corrected
Reader0, re10, strict Windows, dual-ABI Android, API 36 emulator, and API 34
iQOO automated gates now pass. The earlier Port 8 physical-device results
remain predecessor evidence and do not validate the corrected source;
corrected-source physical evidence is recorded below. Controlled real-book
review closes the six reported defects. A 2026-08-05 spacing, fresh-default,
and Library-gutter refinement now passes the API 36 emulator and API 34
physical gates below; hands-on review remains pending. The first bounded
Navigation-polish slice now passes its API 26 and API 36 emulator gates; audible
TalkBack, remaining Appearance/chrome/Library polish, subjective transition/
touch review, and user manual acceptance remain pending. Nothing in this
document claims final Port 8 acceptance, push, or merge.

Port 8 adds fast, reversible structural navigation to the premium Port 7
reader shell. It keeps the last successfully presented semantic location
authoritative, uses Reader0 for EPUB navigation and canonical targets, and
keeps Android interaction, lifecycle, presentation, accessibility, and
durability policy inside 8vo.

This is a navigation milestone, not a search, preview, selection, annotation,
library, synchronization, or broad EPUB-fidelity milestone.

## Milestone outcome

A reader can:

- open a bounded hierarchical Contents surface;
- identify the current section and useful destination progress;
- jump by Contents row, chapter, canonical location, meaningful page, or
  percentage;
- present bounded image-only and ordinary in-flow publication images with
  deterministic failure fallbacks;
- return exactly to the last successfully presented origin and move forward
  again within a bounded reading session; and
- choose a coherent chapter, page, location, or percentage progress display.

Every jump, Return, Forward, and progress-display mutation remains provisional
until its matching reader frame is successfully posted. An unpresented target
is never persisted, announced as current, or exposed through history.

## Included scope

### Contents

- EPUB 2 NCX and EPUB 3 XHTML navigation documents are interpreted by
  Reader0, never Java or Android-only C.
- Nested source order and bounded depth are preserved in a flattened shared
  record. Android renders that record as an accessible hierarchy.
- The current section is the latest valid destination at or before the last
  successfully presented spine/byte position.
- An image-only canonical page reports Reader0's effective contained synthetic
  byte rather than the raw source byte zero. Current-section queries bound and
  accept that anchor both immediately and after reopen.
- Rows include a Reader0-derived canonical destination location/percentage
  when available and an explicit fallback when whole-book progress is not yet
  available.
- Invalid destinations are omitted or disabled according to the Reader0
  contract without making valid siblings or the book unreadable.
- An absent or unusable navigation document falls back to bounded linear-spine
  sections supplied by Reader0.

### Go to

- **Chapter** invokes Reader0's high-level chapter-navigation API. Android never
  derives a Contents index or substitutes `chapter - 1` arithmetic.
- Exact EPUB 3 entries whose tokenized, namespace-qualified attribute name is
  `epub:type` and whose value includes `chapter` win. Unqualified `type`,
  `xsi:type`, and NCX `class` or depth metadata do not create chapter semantics.
- Only when exact semantics are absent may Reader0 accept a unique, source-order,
  contiguous `Chapter 1` through `Chapter N` label sequence. Numbers may be
  decimal, canonical Roman up to 3999, or English words from one through
  ninety-nine. Malformed English hundreds, alphanumeric or spaced numeric
  tails, gaps, reversals, and duplicate numbers reject the fallback instead
  of guessing.
- If neither exact semantics nor the complete fallback model is valid, Chapter
  navigation fails closed with a visible, accessible error and no reader,
  history, presented-anchor, or durable-state mutation.
- **Location** accepts a one-based canonical Reader0 location.
- **Page in this section** is available only while Reader0 proves a complete
  page count for the current layout and spine. It is deliberately
  layout-dependent and is relabelled or disabled after reflow when no longer
  meaningful.
- **Percentage** accepts 0 through 100 and resolves through Reader0's canonical
  location model.
- Invalid, unavailable, overflowed, or out-of-range input produces a visible,
  accessible failure and does not mutate the reader.

### Reported-defect closure

The corrected source maps the six reported defects to explicit ownership and
bounded behavior:

1. **MAPS Contents target failure.** Reader0 reports the effective contained
   synthetic byte selected for an image-only canonical page instead of exposing
   raw source byte zero. Current-section queries accept and bound that anchor,
   including after reopen.
2. **Inconsistent row counts and unusually large bottom gaps in ordinary
   prose.** Reader0 fully paginates predecessor spines at or below 16 KiB from
   byte zero, preserving canonical widow/orphan-aware boundaries and exact row
   hashes during reverse/forward replay instead of inheriting an arbitrary
   reverse-window phase.
3. **Intermittent sparse Scurve/Dramatis Personae page after reverse
   navigation.** The same canonical small-spine pagination removes the
   path-dependent short or sparse page produced after arbitrary reverse probes.
4. **Apparent blank pages between Contents and Dramatis Personae.** Reader0
   supplies one bounded synthetic canonical page per visual image with exact
   navigation, history, and current-section anchors; Android now owns bounded
   decode, cache, fit, fallback, and painting for those frames.
5. **Go-to Chapter off by one or aimed at a row index.** Android calls Reader0's
   chapter operation directly. Reader0 prefers exact EPUB chapter semantics and
   accepts the label fallback only when the complete fail-closed model above is
   proved.
6. **Reader edge reserves.** Android retains Readerview0's base vertical inset,
   adds one full base inset above the content, and reduces the canonical content
   height by the same amount. The result is an exact two-base top reserve and
   one-base bottom reserve; Reader0 reflows to the new capacity. Opening or
   closing navigation/chrome cannot change this geometry.

The prose pagination corrections do not promise identical row counts or exact
last-glyph alignment on every page. Paragraph and chapter endings plus Reader0's
legitimate widow/orphan constraints may still leave one additional line of
bottom space; the invariant is canonical, path-independent pagination with a
stable minimum page-edge reserve rather than artificial full-page fill.

A subsequent visual refinement also makes 16sp the fresh, missing, or corrupt
appearance default while preserving every valid version-3 choice, including
14sp. The Library installs a 16dp root gutter synchronously before any optional
system-inset callback, and book rows use that single shared outer alignment.

### Return history

- Reader0's existing 32-entry platform-neutral back/forward model is the only
  shared history model.
- Its existing reasons remain suitable for future link, footnote, search,
  bookmark, and annotation consumers; Port 8 implements only Contents and
  Go-to producers.
- A semantic jump is executed with Reader0 history suppression. 8vo retains
  the successfully presented origin. After the target frame is accepted, 8vo
  supplies that origin and the actually presented destination to Reader0's
  `epub_reader_record_presented_navigation` primitive; Reader0 verifies the
  current canonical page before exposing the entry.
- Return/Forward begins a Reader0 history transaction, navigates with history
  suppression, and finishes that transaction only after accepted
  presentation. A rejected or abandoned target restores the stack token.
- Page turns do not create structural history entries.
- Port 8 history is bounded and session-scoped. Durable per-book history and
  cross-device history are explicitly deferred; the committed current
  position remains durable through recreation, process death, and book reopen.

### Progress display

- The global display choice is one of Chapter, Page, Location, or Percentage.
- The choice is stored separately from Port 7 appearance and Port 6 per-book
  locations in a bounded, versioned, checksummed record.
- Publication uses a synchronized same-directory temporary file and requires
  atomic replacement. Failure is visible, preserves the previous bytes, and
  remains retryable.
- The displayed value is rebuilt from the accepted Reader0 frame and
  successfully presented anchor. It is never a persisted page-number claim.
- Chapter, canonical location, and percentage remain semantically coherent
  after typography reflow. Page is explicitly layout-relative and is shown
  only where Reader0 reports it as meaningful.

## Android interaction contract

- Reader chrome remains hidden on ordinary entry and book reopen.
- The existing bottom progress control opens a calm 8vo navigation sheet; the
  sheet contains Contents and Go-to modes plus progress-display choices.
- Return is available through a named 48dp control when Reader0 exposes a
  committed back entry. Android Back closes a modal surface first, then
  performs Return when available, and only then returns to the Library.
- The navigation sheet and appearance sheet are mutually exclusive and share
  one modal ownership policy, focus restoration rule, and page-colored
  transition treatment.
- Opening or closing navigation chrome never repaginates or changes the
  canonical reading position.
- Reduced motion, compact viewports, large viewports, and 130% system text
  retain the same actions without clipping or gesture-only requirements.

The surface is original 8vo product work. Kindle references remain ignored
capability evidence only; no branding, assets, icons, labels, coordinates,
animation, trade dress, or copyrighted book content may enter the repository.

## Presentation transaction

Every structural operation uses one bounded native transaction containing:

- kind and monotonically increasing generation;
- Reader0 reason;
- last successfully presented origin;
- expected target spine and semantic byte;
- optional Reader0 history begin/finish tokens; and
- lifecycle and surface generations against which the request was accepted.

The transaction rules are:

1. Refuse or latest-only coalesce input while another document mutation awaits
   presentation.
2. Resolve and publish the target through a high-level Reader0 public API.
3. Build the canonical candidate frame exactly once and prove that it contains
   the expected target.
4. Prepare every candidate image through the bounded media transaction and
   prove that no resource-backed frame image remains `Unavailable`.
5. Post the prepared native buffer successfully.
6. Only then update the presented anchor, commit Reader0 history, expose the
   new current section/progress/accessibility state, and schedule position or
   preference persistence.
7. On render failure, lifecycle interruption, surface loss, or teardown, keep
   the previous presented anchor authoritative. Do not save or announce the
   provisional target.

Taps, swipes, keyboard moves, accessibility page moves, appearance reflow,
Contents, Go to, Return, Forward, and progress-display changes share the same
pending-presentation exclusion boundary.

### Narrow image media transaction

- Reader0 owns document resource identity, canonical image-only and in-flow
  placement, visual row units, synthetic anchors, and vertical-flow semantics.
  Android does not inspect EPUB markup to recover them.
- The Java bridge owns platform `BitmapFactory` decode. Native code exposes only
  bounded encoded bytes and receives explicit loaded or terminal-failure status
  plus caller-owned ARGB pixels.
- Reader0 stats the selected ZIP entry in the same opened archive and applies
  the caller's remaining encoded-byte allowance before output allocation or
  entry decompression. `DocError_LimitExceeded` alone becomes Android's
  non-null empty-array `CacheFull` sentinel; missing, corrupt, or failed
  extraction remains an isolated `DecodeFailed` result.
- One frame holds at most 16 image descriptors. Each encoded resource is capped
  at 16 MiB; decoded input is capped at 4096 pixels per dimension and 8 million
  pixels. The native cache is capped at 32 entries and 32 MiB of decoded ARGB.
- Cache replacement is deterministic least-recently-used eviction. Resources in
  the current candidate frame are pinned, including terminal per-resource
  status entries. If every possible victim is pinned,
  only that resource becomes `CacheFull` and the global cache does not latch
  permanently full.
- Image-only and ordinary in-flow images are aspect-fitted into Reader0-owned
  placement. Loaded pixels are painted before text; terminal dimension, decode,
  missing-resource, or cache failures receive an explicit theme-safe fallback.
- Reader0's `visual_units` height is the single vertical-flow contract used by
  both image placement and text traversal. An in-flow image consumes its rows,
  so following text cannot overlap it or silently use a different height model.
- Cold open, navigation, reflow, and both appearance candidate and rollback
  paths prepare image status before publication. A resource-backed descriptor
  left `Unavailable` cannot count as a successfully presented frame.
- The Java bridge decodes serially. Each presentation caps cumulative encoded
  input at 16 MiB and cumulative decoded input at 8,388,608 pixels in addition
  to the per-resource limits above. Once either budget is exhausted, that
  resource and every remaining unavailable resource receive terminal
  `CacheFull`; no later decode begins in that transaction.
- Reader0's adversarial smoke uses a highly compressible oversized payload and
  malformed/forged entry metadata to prove exact-limit success, pre-inflate
  rejection, required-size reporting, safe allocation arithmetic, and exact
  caller-arena rollback. Windows 8vo and re10 use the same bounded getter with
  their existing 32 MiB encoded-resource ceilings.

Prepared-frame reuse is part of the same transaction:

- The first Java frame-image snapshot performs the one static candidate build
  and records a bounded identity token over window, surface and lifecycle
  generations, exact dimensions, mutation generation, and Reader0 layout,
  current-page, frame, and location-cache identity.
- The bridge verification snapshot and the immediately following native present
  must reuse that token and frame; present never silently rebuilds the candidate.
  A missing or stale token rejects presentation.
- Lifecycle, surface, layout, navigation, appearance, progress, and chrome
  mutations invalidate the token, as does actual location-cache progress. Image
  decode/cache attachment and test-only failure counters do not.
- Forced pre- or post-publication failures retain the exact token for bounded
  retry. Only accepted presentation and commit consume it.

The corrected source includes counters and a bounded test packet for one-build,
reuse, stale-rejection, retry, and consumption evidence. The corrected API 36
matrix validates those assertions; predecessor Port 8 results still must not be
cited as proof of this contract on the pending physical device.

## Ownership and dependency boundary

- **Reader0** owns EPUB 2/3 navigation interpretation, destination validity,
  fragment resolution, current-section identity, canonical location and
  percentage mapping, meaningful page targets, canonical frames, and the
  bounded history model.
- **8vo native Android host** owns the pending presentation transaction,
  lifecycle/surface generations, coalescing, target validation, commit order,
  diagnostics, and JNI copies.
- **8vo Java host** owns the navigation sheet, input validation presentation,
  focus, TalkBack adaptation, progress-choice persistence, system Back policy,
  and visible failures.
- **Readerview0** continues to provide the portable reader projection used by
  the page. Port 8 does not force Android's navigation-sheet layout into it.
- **UI0** and **Ground0** gain no Android product policy.

Reader0 is consumed only through `reader0.h`. Caller ownership, bounded
storage, explicit failure, and exact unity consumption of `reader0.c`,
`ui0.c`, and `readerview0.c` remain mandatory. Android must not calculate EPUB
destinations, pagination, canonical locations, or history semantics in Java.

The Port 8 startup pins are:

- Ground0 `770b970b4655facfa9700c3d1025d96102365631`;
- Reader0 `0.7.0-dev` / public API 7 at
  `5fe949d88258cd96884c44b69e4f4ab6f27dc394`, advanced from base
  `59e9efdaca17b316aa2b1f5a7be0cbdebf5e4c26` for the shared structural-
  navigation and corrective pagination contract;
- UI0 `cadafcacdae8e63cf0d2b505f54e2a2a228c0bec`; and
- Readerview0 `f97f9d38cf857c2cff1f90357cf5d2e5cf40dc03`.

The exact Reader0 commit is recorded by `vendor/reader0_dependency` only after
the coherent local shared-contract commit is established. The dependency guard
requires that exact clean revision. `LECTERN0_ZERO_FOUNDATION_DIR` must not be
used or reintroduced.

## Bounds and failure policy

- Reader0 retains its documented navigation-document and 32-entry history
  caps. Android bounded-copies every borrowed label before crossing JNI.
- The Android Contents model has an explicit row cap and reports truncation;
  it never silently aliases keys or marks a different row current.
- At most one structural transaction awaits presentation. Rapid input is
  latest-only coalesced where the intent remains meaningful; repeated Return
  cannot pop multiple hidden entries.
- Location-cache warming remains post-first-frame, one bounded step at a time.
  Opening Contents must not regress Port 7 reader entry by synchronously
  warming an entire real book.
- A navigation-document error is nonfatal to reading. A target-resolution,
  render, surface, atomic-save, or capacity failure is visible and retryable.
- Presentation retry remains bounded. No failure path starts a hidden thread,
  unbounded poll, or non-atomic durability fallback.

## Accessibility contract

- Contents is a real Android collection with source-order focus and explicit
  level/parent semantics derived from Reader0 depth.
- Every row exposes its label, hierarchy level, current-section state,
  destination progress, enabled state, and jump action.
- Navigation modes, inputs, validation messages, progress choices, Return,
  Forward, close, and the underlying page have unique labels and deterministic
  focus order.
- Modal opening moves accessibility focus into the sheet; closing or a
  successful jump returns focus predictably to the reader/navigation control.
- Page- and section-changed events are emitted only after successful
  presentation.
- Essential behavior has 48dp touch targets plus TalkBack and keyboard/switch
  paths. Reduced motion has a zero-duration equivalent.

## Deliberately excluded

- Page Flip-style or thumbnail spatial preview and advanced scrubber previews;
- in-book full-text search;
- text selection;
- bookmarks, highlights, notes, and an annotations workspace;
- premium cover library, collections, or library search;
- Google Drive or other synchronization;
- fixed-layout/comics; and
- complete complex-script, embedded-font, image, table, and broad publisher-
  fidelity parity.

The Reader0 history reasons needed by those future consumers may be retained
and tested as a shared contract, but Port 8 must not add placeholder UI or
dummy consumers for them.

## Corrected-component and predecessor evidence

Reader0 at `5fe949d88258cd96884c44b69e4f4ab6f27dc394` passed its exact
dependency/API audit, MSVC `/W4 /WX` build, `--reader-core-smoke`, and
`--host-smoke`. Companion re10 at
`b1c264f027c90bec480677bfeadfa5e0728776a8` passed strict product and
qualification builds plus `--document_engine_smoke` with four anchors, final
spine 3, and hash `f3c13a55f0349720`. The corrected exact-pin strict Windows
8vo build and all seven public smokes also pass in 19.6 seconds wall. Stable
hashes are host `cd460506f219d652`, Reader View `e29cfd3afeea51a1`, visual
`e6848393c4dc0b95`, cover `a2fabe96a148a6a4`, and inline image
`5b536d3a66934ec8`.

The corrected Android candidate passed:

- the exact dependency guard and clean debug/test build for `arm64-v8a` and
  `x86_64`;
- five consecutive deterministic deferred-location/presentation-gate probes
  and an 11/11 mixed image/prepared-frame matrix;
- the complete ordinary API 36 x86_64 emulator matrix, 67/67 with zero skipped,
  failed, or errored tests in 468.395 seconds of XML time and 494.0 seconds wall;
- the external seed, confirmed force-stop, and fresh-process restore driver;
- the focused 130% system-text/disabled-system-animation matrix, 15/15 in
  80.538 seconds, followed by exact restoration of font scale `1.0`, window and
  transition scales `1.0`, and the previously absent animator-scale key; and
- an empty crash buffer. Process exit history contained only expected
  `USER REQUESTED` force stops from instrumentation and the restart driver, with
  no crash or ANR.
- the vivo I2019/iQOO 9 SE, Android 14/API 34 ARM64 ordinary matrix, 67/67
  with zero skipped, failed, or errored tests in 170.878 seconds of
  instrumentation time and 171.449 seconds wall;
- the physical external confirmed-force-stop restore driver, with a
  1.987-second seed, 1.135-second fresh-process verification, and 4.908-second
  wall time;
- the physical 130% system-text/disabled-system-animation matrix, 15/15 in
  16.185 seconds of instrumentation time and 18.591 seconds wall, followed by
  exact restoration of the settings database to font scale `1.0`, window and
  transition scales `1.0`, and the previously absent animator-scale key;
- an empty physical crash buffer. Exit history contained only expected
  `USER REQUESTED` force stops and `PACKAGE UPDATED`, with no crash or ANR;
- controlled real-book review in which the first MAPS Contents destination
  opened without error and presented three genuine map leaves before Dramatis
  Personae; Chapter `1` and `2` resolved to Chapter One and Chapter Two; Return
  restored the prior prose origin exactly; and a Chapter One-to-Prologue jump
  followed by five deliberately waited reverse turns produced the expected
  intermediate pages and full reported Dramatis Personae page through its
  expected continuation, with no sparse-page recurrence;
- full reported prose pages, coherent increased top breathing room, and no
  visible bright or black transition during that review; and
- byte-exact restoration of the original 26 app files and 4,751,505 payload
  bytes. The original archive SHA-256 is
  `52C4C27FA8E8D4C268950D6AB918D72DA130864D94556945BD815B1D12A901F2`, and
  the manifest SHA-256 is
  `A060016D369EC0E8902070A10206E09D82BC27BBACEB387F872F2C669F5D0B94`.

The 2026-08-05 visual refinement passed the exact dependency and architecture
guards, dual-ABI debug/test build, a focused appearance/navigation/library
matrix at 45/45 in 128.97 seconds, and the complete ordinary API 36 matrix at
67/67 in 250.953 seconds. The external seed/confirmed-force-stop/fresh-process
driver passes. The 130% system-text/disabled-animation matrix passes 15/15 in
27.952 seconds; font scale `1.0`, window and transition scales `1.0`, and the
previously absent animator-scale key restored exactly. The app crash buffer is
empty and exit history contains only expected `USER REQUESTED` force stops. A
fresh 1080px-wide Library dump places its title at x=42 and the Add EPUB right
edge at x=1038, proving the synchronous 16dp outer gutter. Repeat API 34
physical validation passed the ordinary matrix 67/67 in 167.136 seconds, the
external confirmed-force-stop restart driver, and the focused matrix 15/15 in
14.625 seconds at 130% system text with animations disabled. The settings
database restored exactly to font scale `1.0`, window and transition scales
`1.0`, and the previously absent animator-scale key. A later hands-on check
found that some vivo SystemUI animations still behaved as disabled; explicitly
setting the animator duration scale to `1.0` repaired the device. Future
physical reduced-motion runs must be coordinated with the user; device-wide
animations must not be disabled outside that test window. Restore window,
transition, and animator scales explicitly to `1.0`, then verify visible
behavior as well as stored values. The crash buffer remained empty and exit
history contained only expected `USER REQUESTED` force stops. A fresh 1080px
iQOO Library dump places the title at x=44 and Add EPUB's right edge at x=1036;
a fresh reader
capture places first ink 85px below the app content edge while retaining the
one-base canonical bottom reserve. The original 26 app files and 4,751,505
bytes were restored byte-exact. The archive SHA-256 is
`1678B1DC0356FC84CF48CCEFA3508C210B23D736327CBFAC71E0EE054AB9FC3F` and the
manifest SHA-256 is
`9EAF4BC7754F53F1FD546C8447E9D474F41BF5988B6071430CB3D1163AE5B0CC`.

Before the six corrective changes and prepared-frame hardening, the 2026-08-04
Port 8 predecessor passed:

- Reader0's strict dependency/API audit, MSVC `/W4 /WX` build,
  `--reader-core-smoke`, and `--host-smoke`;
- the exact 8vo dependency and architecture guards;
- a clean Gradle debug/test build for `arm64-v8a` and `x86_64`;
- the complete ordinary API 36 x86_64 emulator matrix, 56/56 in 146.291
  seconds;
- the separate seed, confirmed force-stop, and fresh-process verification
  driver, including exact restoration of the presented structural target,
  durable Location progress choice, and empty session history;
- the focused 130% system-text and disabled-system-animation matrix, 15/15 in
  18.589 seconds, followed by exact restoration of the emulator settings;
- empty Android crash and fatal-runtime buffers;
- the synthetic EPUB Resume gate with a representative fresh restore accepted
  in 169ms end-to-end, 129ms in native stages, and zero missing glyphs;
- the pre-correction Windows 8vo `/W4 /WX` build plus all seven public smokes in
  17.6 seconds;
  the repeatable host, Reader View, visual, Presentation, cover, and inline
  hashes were respectively `cd460506f219d652`, `e29cfd3afeea51a1`,
  `e6848393c4dc0b95`, `3a3cf46f0444a1bd`,
  `a2fabe96a148a6a4`, and `5b536d3a66934ec8`;
- isolated re10 strict product/qualification builds, product image budgets,
  and `--document_engine_smoke` against the additive Reader0 API 7 adoption.
  The smoke retained four anchors, final spine 3, and hash
  `f3c13a55f0349720`;
- the vivo I2019/iQOO 9 SE, Android 14/API 34 ARM64 ordinary matrix, 56/56 in
  121.608 seconds of instrumentation time and 122.475 seconds wall time;
- the physical seed/confirmed-force-stop/fresh-process driver in 4.593 seconds,
  with 1.786-second seed and 1.087-second verification halves;
- the physical 130% system-text/disabled-system-animation matrix, 15/15 in
  15.580 seconds of instrumentation time and 17.244 seconds wall time, followed
  by exact restoration of the settings database to font scale `1.0`, window
  and transition scales `1.0`, and the previously absent animator-scale key;
- an empty physical crash buffer;
- a staged restore of all 26 durable `files/` records and 4,751,505 payload
  bytes, exact by relative path, length, and SHA-256; and
- a 294ms restored cold Library launch plus a controlled private-book check.
  The live EPUB marked Chapter One current at destination 154/9549 (1%),
  presented Chapter Two at 687/9549 (7%), returned to the Chapter One
  origin, enabled Forward only after that accepted return, and exposed Chapter,
  canonical Location, meaningful Page, and Percentage Go-to controls. The
  durable catalog anchor remained exactly
  `17:0`; only its expected last-opened timestamp changed.

That predecessor API 36 matrix covered deterministic nested, flat, absent,
malformed, and partially invalid navigation; exact destinations/current
identity; all Go-to forms; Return/Forward; rapid and failed operations;
lifecycle, rotation, recreation, surface replacement, reflow, reopen, and
process restart;
accessibility hierarchy/focus/actions; and compact/large layouts. Composed-
frame tests retain the Port 7 bright/black-transition protections.

Those results remain useful predecessor regression evidence, but they do not
validate the corrected 8vo source. The corrected emulator, external restart,
accessibility/reduced-motion, physical iQOO, byte-exact device-data restoration,
and controlled real-book gates are recorded above. The 2026-08-06 bounded
Navigation-polish slice adds a versioned UI0 API 91 snapshot and product-neutral
native-Android adapter without changing Reader0 semantics. Exact guards and
dual-ABI builds pass. Its API 36 emulator results are 26/26 focused in 1.997
seconds, 85/85 ordinary in 245.948 seconds, confirmed-force-stop restart pass,
and 22/22 at 130% system text with animations disabled in 22.543 seconds. API
26 passes 26/26 focused in 1.449 seconds, 85/85 ordinary in 180.560 seconds,
restart, and 22/22 at 130% text with animations disabled in 22.217 seconds. Both
crash buffers were empty. The API 26 run renders the framework cursor and
center/left/right selection handles with the fixed `#8B7560` compatibility
accent and proves at least 3.5:1 contrast against every supported UI0-derived
input surface.

Desktop light/dark Contents captures and live API 36 Paper, Dusk, and High
Contrast captures accept bounded portrait-phone Navigation look-and-feel
parity, including UI0 accent-family current indicators and contrast-safe High
Contrast current-row text. Pixel-for-pixel desktop rendering, full-app parity,
audible TalkBack, attached live-region delivery, manual large-viewport/RTL and
alternate-input review, strict UI0 role-to-face metrics, remaining bounded
polish, subjective transition/touch review, and user manual acceptance remain
required. No physical device was used for this slice.
No pushed or merged 8vo revision or APK hash is claimed here.

## Acceptance contract

Before Port 8 can be called complete:

- deterministic Reader0 fixtures cover nested, flat, absent, malformed, and
  partially invalid NCX/XHTML navigation documents;
- deterministic and real EPUB checks prove exact Contents destinations and
  current-section state;
- Chapter, Location, meaningful Page, and Percentage validate input and land
  on Reader0-authoritative targets;
- Return restores the exact last successfully presented origin and Forward
  restores the accepted jump target;
- rapid jumps/Return, render failure, surface loss, pause/resume, rotation,
  recreation, and teardown cannot corrupt history or persist an unpresented
  target;
- progress choices remain coherent across reflow, theme changes, rotation,
  recreation, surface replacement, process restart, and book reopen;
- image-only Contents targets expose contained synthetic anchors immediately
  and after reopen, while small-spine reverse/forward pagination is canonical
  and path-independent;
- exact namespace-qualified `epub:type` chapter semantics and the complete
  numbered-label fallback resolve valid chapters without accepting unrelated
  type attributes, malformed tails, gaps, reversals, or duplicates;
- bounded image-only and in-flow frames cover success, terminal decode/resource
  failures, aspect fit, `visual_units` flow, cache turnover beyond 32 resources,
  current-frame pinning, reflow, recreation, and presentation rollback;
- prepared-frame diagnostics prove one static build per candidate, exact reuse
  by bridge verification and present, stale-identity rejection, token retention
  across bounded forced failures, and consumption only after accepted commit;
- Java image preparation proves cumulative 16-MiB encoded and 8,388,608-pixel
  decoded budgets, terminal `CacheFull` publication for the exhausted resource
  and every remaining unavailable resource, and no decode beyond exhaustion;
- Reader0's bounded-resource smoke proves that highly compressed oversized,
  malformed zero-compressed/stored-size, and forged near-`UINT64_MAX` entries
  cannot allocate or publish resource bytes past the caller ceiling;
- reader entry and navigation have no bright/black transition frame and do not
  regress the accepted Port 7 Resume boundary;
- TalkBack hierarchy, labels, state, actions, focus order, 48dp targets, 130%
  text, keyboard/switch paths, reduced motion, and compact/large viewports pass;
- exact dependency and cleanliness guards pass;
- arm64-v8a and x86_64 debug/test builds pass;
- the full API 36 x86_64 emulator matrix, process restart, crash-buffer, and
  visual transition checks pass;
- strict Windows 8vo and every public smoke pass;
- Reader0 strict build/core/host smokes pass, and re10 remains unchanged apart
  from the exact Reader0 adoption pin while its strict product/qualification
  and `--document_engine_smoke` gates pass; and
- the physical iQOO matrix and hands-on real-book navigation run only after the
  emulator candidate passes.

Port 8 will end in coherent local commits with every participating worktree
clean. Nothing is pushed or merged without explicit user approval.

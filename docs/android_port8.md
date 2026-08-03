# Android Port 8: structural reader navigation

Status: locally qualified emulator candidate on
`android/port8-structural-navigation`, based exactly on accepted Port 7 commit
`de7ba5dd5c5730cfb333bb5968d8cf7380203ecd`.
The shared structural-navigation contract is Reader0 `0.7.0-dev` / public
API 7 at `58ec6d11575c36176eb85511759d39dc93acb78b`. Reader0, dual-ABI
Android, API 36 emulator, strict Windows 8vo, and isolated re10 qualification
have passed. Physical-device and hands-on real-book acceptance remain pending.
Nothing in this document claims final Port 8 acceptance, push, or merge.

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
- Rows include a Reader0-derived canonical destination location/percentage
  when available and an explicit fallback when whole-book progress is not yet
  available.
- Invalid destinations are omitted or disabled according to the Reader0
  contract without making valid siblings or the book unreadable.
- An absent or unusable navigation document falls back to bounded linear-spine
  sections supplied by Reader0.

### Go to

- **Chapter** selects a valid structural destination.
- **Location** accepts a one-based canonical Reader0 location.
- **Page in this section** is available only while Reader0 proves a complete
  page count for the current layout and spine. It is deliberately
  layout-dependent and is relabelled or disabled after reflow when no longer
  meaningful.
- **Percentage** accepts 0 through 100 and resolves through Reader0's canonical
  location model.
- Invalid, unavailable, overflowed, or out-of-range input produces a visible,
  accessible failure and does not mutate the reader.

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
3. Build a canonical frame and prove that it contains the expected target.
4. Post the native buffer successfully.
5. Only then update the presented anchor, commit Reader0 history, expose the
   new current section/progress/accessibility state, and schedule position or
   preference persistence.
6. On render failure, lifecycle interruption, surface loss, or teardown, keep
   the previous presented anchor authoritative. Do not save or announce the
   provisional target.

Taps, swipes, keyboard moves, accessibility page moves, appearance reflow,
Contents, Go to, Return, Forward, and progress-display changes share the same
pending-presentation exclusion boundary.

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
  `58ec6d11575c36176eb85511759d39dc93acb78b`, advanced from base
  `59e9efdaca17b316aa2b1f5a7be0cbdebf5e4c26` for the shared structural-
  navigation contract;
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

## Current candidate evidence

The 2026-08-03 local candidate passed:

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
- strict Windows 8vo `/W4 /WX` plus all seven public smokes in 17.6 seconds;
  the repeatable host, Reader View, visual, Presentation, cover, and inline
  hashes were respectively `cd460506f219d652`, `e29cfd3afeea51a1`,
  `e6848393c4dc0b95`, `3a3cf46f0444a1bd`,
  `a2fabe96a148a6a4`, and `5b536d3a66934ec8`; and
- isolated re10 strict product/qualification builds, product image budgets,
  and `--document_engine_smoke` against the additive Reader0 API 7 adoption.
  The smoke retained four anchors, final spine 3, and hash
  `f3c13a55f0349720`.

The API 36 matrix covers deterministic nested, flat, absent, malformed, and
partially invalid navigation; exact destinations/current identity; all Go-to
forms; Return/Forward; rapid and failed operations; lifecycle, rotation,
recreation, surface replacement, reflow, reopen, and process restart;
accessibility hierarchy/focus/actions; and compact/large layouts. Composed-
frame tests retain the Port 7 bright/black-transition protections.

The physical iQOO matrix, audible TalkBack/touch-exploration review, and
hands-on navigation in the private real book remain the final acceptance
stage. The user must reconnect and unlock that device before it begins.

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

# Android Port 10 follow-up: cross-page text selection

Status: API 36 emulator- and API 34 physical-device-qualified bounded candidate.
This slice closes the physical Port 10 finding that a selection handle could not
continue onto an adjacent page. It does not add durable annotations,
synchronization, or a second text-anchor model.

## Product outcome

A user can drag either selection handle beyond the top or bottom of the
presented text area, hold briefly, and continue the selection on the adjacent
page. Holding at the edge may continue through further pages, but only after
each requested page has been presented successfully. Copy returns the complete
bounded selected text rather than only the currently visible fragment.

## Shared ownership and bounds

- Reader0 remains the sole owner of the canonical `DocSelection`, pagination,
  exact byte anchors, and adjacent-page resolution. Android uses
  `epub_reader_move_page` with `preserve_selection` and `same_spine_only`.
- The public Reader0 selection contract contains one spine index and one byte
  range. Therefore this slice crosses page boundaries inside that spine and
  stops at a spine/chapter boundary. It must not synthesize a cross-spine Java
  range or concatenate independently interpreted EPUB text.
- The stop is non-destructive and explicit: the current selection remains
  usable, touch repetition stops, and accessibility receives a concise chapter-
  boundary announcement.
- Selected UTF-8 remains bounded by Reader0's public frame text capacity. An
  attempted extension beyond that bound keeps the last presented selection and
  reports a visible failure.

## Native Android interaction

- A handle must be actively dragged outside the presented content edge for a
  350 ms dwell before one page turn. Continued edge holding repeats no faster
  than 450 ms and only after the prior page/selection transaction is presented.
- Moving back inside the content, lifting the pointer, cancellation, a second
  pointer, chrome recomposition, surface replacement, pause, or destruction
  cancels the pending dwell.
- The dragged endpoint resolves to Reader0-backed rendered-row geometry on the
  new page. Forward, backward, contraction, reversal, and handle crossing use
  the same canonical endpoint rules as same-page selection.
- Only an endpoint actually visible on the current page exposes a handle and a
  48 dp hit region. The off-page endpoint remains canonical but does not create
  a false handle at the clipped page edge.
- During an active handle drag on API 28+, Android's native `Magnifier` presents
  a 112dp by 64dp, 1.8x loupe. Its window stays 96dp clear of the handle and
  flips below near the top edge, while its sample is vertically offset by half
  the active rendered-row height so that row, rather than the following row, is
  centered and fully visible. The handle remains the display-position anchor.
- The loupe uses only the successfully presented native selection snapshot. It
  refreshes synchronously when that snapshot is published, at most once per
  presented frame, retains the last stable image while a page transaction is
  pending, and dismisses on pointer release, cancellation, lifecycle/surface
  loss, page-turn ownership change, or selection clear. This avoids both
  flicker and stale content during continuous drag without adding a Java text or
  EPUB model. API 28 uses the platform-compatible placement fallback.
- TalkBack's virtual page keeps Copy and Clear, and adds explicit Extend to
  previous page and Extend to next page actions while a selection is active.

## Presentation, lifecycle, and failure

- Page movement and endpoint mutation form one pending transaction. Neither the
  presented anchor, persistence, action-mode state, nor accessibility state is
  committed until the new pixels are posted successfully.
- Build, geometry, media, buffer, post, retry-exhaustion, lifecycle, and surface
  failures restore the exact prior page and `DocSelection` before presenting a
  recovery frame. A failed rollback is surfaced and requires reopening the
  book.
- Ordinary page navigation, structural navigation, search navigation,
  appearance/reflow, Back, book close, and process replacement retain Port 10's
  existing clear-or-cancel ownership rules.
- Motion follows the native paged reader. Reduced motion adds no animation and
  does not change dwell or successful-presentation semantics.

## Acceptance gates

- Deterministic forward/backward continuation, contraction, handle reversal,
  repeated multi-page extension, chapter boundary, length bound, Copy, Clear,
  Back, pending gate, rollback, surface/lifecycle, and process-restart tests.
- Snapshot validation proves independent start/end visibility, exact global
  byte anchors, 48 dp hit regions, no clipped-edge phantom handle, and a
  flicker-free row-centered loupe that tracks each successfully presented drag
  frame and dismisses at every lifecycle boundary.
- TalkBack actions, labels, announcements, focus retention, keyboard/system
  Back, 130% system text, compact/large viewport, reduced motion, and High
  Contrast checks.
- Exact dependency and architecture guards, strict host/public smokes, dual-ABI
  Android debug/test builds, complete API 36 emulator functional/visual/crash/
  restart gates, then a coordinated API 34 physical run with exact restoration.

## Qualification and physical acceptance

- Exact dependency and architecture guards pass at the unchanged Ground0,
  Reader0, UI0, and Readerview0 pins. The strict Windows `/W4 /WX` build, all
  seven public smokes, dual-ABI Android debug/test builds, and `git diff --check`
  pass.
- The expanded selection class passes 10/10 in 73.180 seconds, including real
  edge dwell, two-page repeat, independent endpoint visibility, contraction,
  handle reversal, busy gating, presentation rollback, bounded Copy, and
  chapter-boundary retention, plus continuous-drag magnifier tracking,
  row-centered sampling, and lifecycle dismissal.
- The correctly filtered ordinary matrix passes 100/100 in 490.402 seconds. The
  external restart driver passes seed 1/1, confirmed force-stop, and verify 1/1.
- The selected matrix passes 37/37 in 101.257 seconds at system font scale 1.3
  with all three animation scales zero. Exact restoration returned font, window,
  and transition to `1.0` and deleted the originally absent animator key.
- One unfiltered 102-test diagnostic incorrectly included the external restart
  verifier without its separately seeded process state and is excluded. The
  explicitly filtered 100/100 run above is the authoritative ordinary matrix.
- Paper, Warm dark, and High Contrast visual inspection passes. Only the visible
  endpoint publishes a handle; the off-page range is highlighted without a
  clipped-edge phantom, and the bounded-length stop is visible. The emulator
  crash buffer is empty.
- On the API 34 ARM64 iQOO, focused selection passes 10/10 in 16.799 seconds,
  the correctly filtered ordinary matrix passes 100/100 in 178.812 seconds,
  external restart passes seed 1/1 plus verify 1/1 around a confirmed force
  stop, and the selected matrix passes 37/37 in 39.059 seconds at system font
  scale 1.3 with normal motion retained at `1.0`/`1.0`/`1.0`.
- Coordinated touch review accepts forward/backward multi-page handle
  continuation and the continuously updating, flicker-free, row-centered loupe.
  With TalkBack enabled, the user confirmed that Select text, Extend selection
  to next page, Extend selection to previous page, and Copy selected text were
  all available through the virtual page actions, with no issue reported.
- Cleanup restored TalkBack off, no enabled or bound accessibility service,
  touch exploration off, font `1.0`, all three animation scales explicitly
  `1.0`, and every other captured device setting. The physical crash buffer is
  empty. All 26 original 8vo files totaling 4,751,505 bytes restored byte-exact;
  archive SHA-256 is
  `1EF189A765D02321E1A9DC2203CF69B4F90111A9369D0AA6D585D0592DB46DBE`
  and exact manifest SHA-256 is
  `94A15EE1CCAAC59833EB0647887A55F3FF441FBD8DF4958B24537E8E6EB59B74`.

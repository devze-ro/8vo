# Android Port 10: bounded text selection and Copy

Status: API 36 emulator- and API 34 physical-device-validated bounded
implementation candidate. Single-page touch, Copy, Back, and audible TalkBack
pass, but physical review confirmed that selection cannot continue across a
page boundary. That remains a launch blocker before durable annotations or
synchronization.

## Outcome

Android readers can long-press selectable page text, extend or contract the
selection with native touch handling, inspect a persistent visual selection,
and copy the exact selected text through Android's contextual action mode.

## Cross-platform invariants

- Reader0 remains the only owner of the canonical selection. Android supplies
  a spine index and UTF-8 byte range to `epub_reader_set_selection`; it does
  not retain a competing text anchor or reinterpret EPUB content.
- Touch hit testing, selection painting, and handle placement reuse the exact
  row order, margins, inline styles, font advances, alignment, justification,
  and UTF-8 traversal used by Android page drawing.
- Selection endpoints are valid UTF-8 codepoint boundaries and a committed
  selection is non-empty, within the active Reader0 spine, and visible in the
  accepted frame.
- Copy obtains the bytes covered by Reader0's validated selection from the
  active canonical spine and publishes them only through Android's clipboard
  service. Clipboard ownership and user feedback remain platform policy.
- Page movement, structural/search navigation, reflow, document replacement,
  surface replacement, lifecycle teardown, and reader teardown clear the
  transient selection. Nothing in this slice is durable or synchronized.
- Selection mutations cannot bypass an outstanding page, appearance, reflow,
  structural-navigation, progress, or search presentation transaction.
  Failure leaves the last successfully presented page authoritative and makes
  the selection failure visible.

## Native Android adaptation

- A long press selects the bounded word containing the pressed glyph. Dragging
  before release extends that range; after release, the start and end handles
  retain at least 48dp touch targets while their visible markers stay compact.
- The Android contextual action mode exposes `Copy` and no speculative
  annotation actions. Copy success clears the selection and announces a short
  confirmation; clipboard failure preserves the selection for retry.
- Tapping outside a committed selection dismisses it before ordinary reader
  tap behavior. Swipes that become page turns cancel an uncommitted selection
  gesture, and multi-touch cancels it.
- System Back dismisses selection before navigation history or Library. API
  33+ uses Android's modern Back dispatcher; the legacy activity callback
  retains the same ordering on older supported releases.
- UI0's semantic Selection color supplies the fill. The host derives a
  contrasting handle color from the resolved native theme while native Views,
  Android touch slop, long-press timing, haptics, contextual actions, and
  accessibility remain Android-owned.
- TalkBack users can invoke `Select text` on the virtual page node, which
  selects a deterministic first visible word, exposes `Copy selected text`,
  and announces selection, copy, dismissal, and failure states. This bounded
  alternative does not claim arbitrary character-range TalkBack editing.
- Activity recreation and process death restore the reading position but not
  the transient selection or contextual action mode.

## Explicit non-goals

- Bookmarks, stored highlights, highlight colors, notes, annotation editing,
  annotation search, workspace/export, or Google Drive synchronization.
- Cross-page or cross-spine selection, automatic page turning while dragging,
  image/alt-text selection, tables with non-linear visual reading order, or
  full publication accessibility.
  This was an intentional Port 10 boundary, not an accidental omission, but the
  physical user could not extend a selection across pages and identified it as
  a real usability issue. The bounded slice may pass while launch-quality text
  selection remains incomplete; resolve that gap before building durable
  bookmarks, highlights, or notes on this anchor model.
- Unicode word breaking, language-specific segmentation, grapheme editing, or
  replacing Reader0's byte anchors with Java character offsets. The first
  slice uses bounded UTF-8 codepoint boundaries and conservative whitespace /
  punctuation word boundaries.
- Pixel-for-pixel desktop popup parity. Android uses its contextual action
  mode and touch conventions while sharing theme identity and selection state.

## Candidate evidence

The exact Ground0, Reader0, UI0, and Readerview0 dependency pins are unchanged.
The dependency guard, architecture audit, strict Windows build, all seven public
smokes, dual-ABI Android debug/test build, and `git diff --check` pass.

On the API 36 x86_64 emulator, the focused selection class passes 5/5 in
25.544 seconds. It covers exact bounded Copy, clipboard-failure preservation,
Reader0 range mutation and presentation rollback, navigation clearing,
Activity recreation, virtual-page Select/Copy/Clear actions, rendered-geometry
long press at a trailing glyph caret, outside dismissal, and real KEYCODE_BACK
selection-first routing. The final ordinary matrix passes 95/95 in 247.619
seconds, and the selected 130%-text/reduced-motion matrix passes 32/32 in
43.913 seconds. The confirmed-force-stop seed and verification halves pass in
3.500 and 2.206 seconds.

Manual Paper, Warm dark, and High Contrast review confirms readable UI0-derived
selection fills, compact visible handles with 48dp hit regions, exact word
selection, and Android's floating Copy action. That review exposed the API 33+
system-Back routing defect; the modern dispatcher fix and actual-key regression
now pass. The emulator crash buffer is empty. Font scale and all three animation
settings were restored exactly to `1.0`, `1.0`, `1.0`, and an absent animator
key after the reduced-motion run.

On the API 34 ARM64 iQOO, the focused selection class passes 5/5 in 9.667
seconds, the correctly filtered ordinary matrix passes 95/95 in 204.316
seconds, and the external restart seed/confirmed-force-stop/verification probe
passes in 1.951 and 1.122 seconds. The final correctly configured selected
matrix passes 32/32 in 34.958 seconds with the actual Android system font scale
at `1.3` and all three animation scales at zero. An earlier run mistakenly
created an unused global `font_scale` key and is not large-text evidence; it is
retained as a failed qualification attempt. The final run used the system
namespace, verified the active values, and restored system font scale to
`1.0` with the global key absent.

Hands-on single-page long press, handle movement, Copy, and selection-first
Back behaved as intended. TalkBack 17.0.1 touch exploration exposed and spoke
Select text, Copy selected text, and Clear selection without a reported issue.
The same review confirmed the explicit limitation: dragging cannot continue
selection onto another page. Port 10 therefore closes its bounded physical
gate but does not close launch-quality selection parity.

Before testing, all three stored animation scales were already `1.0`, but vivo
Recents motion remained behaviorally disabled. Republishing the values did not
repair it; restarting the active Launcher process did. Every reduced-motion
cleanup then restored all three scales explicitly to `1.0`, restarted Launcher,
and verified live behavior. The final post-restore probes rendered 47 Launcher
frames and 74 SystemUI frames. TalkBack is off, enabled accessibility services
are absent, touch exploration is off, secure settings match the captured
baseline exactly, music/accessibility volumes remain 2/3, rotation is off, the
crash buffer is empty, and no 8vo process remains.

All 26 original 8vo files totaling 4,751,505 bytes restored byte-exact. The
pre- and post-restore archive SHA-256 is
`4D97EA76087B7CF57CA5647A591FE517A2674EEA65B1239CA89B2785FA5B3688`;
the exact manifest SHA-256 is
`B8AD189D8DF58419BF89C799B26E28EB3095EF9D490C9EBE0472AE5C5155A9DF`.

## Acceptance gates

- Exact dependency and architecture guards; strict host/public smokes; dual-
  ABI Android debug and test builds.
- Deterministic single-word, punctuation, styled-run, justified-row,
  multi-row, reversed-handle, outside-dismiss, Copy, clipboard-failure,
  pending-presentation, navigation-clear, appearance/reflow-clear, surface,
  Activity-recreation, process-restart, and empty/image-page tests.
- Accessibility labels/actions/focus, keyboard dismissal, 130% system text,
  compact/large viewport, reduced-motion, and high-contrast checks.
- API 36 emulator functional and visual review in representative light, dark,
  and High Contrast themes before requesting the physical device.
- A coordinated API 34 physical run, live bounded selection/Copy/TalkBack
  exploration, crash review, explicit device-setting restoration with behavioral
  verification,
  and byte-exact app-data restoration. This gate passes.
- Cross-page selection behavior must be defined and implemented before the
  Google Play selection feature is considered launch-complete.

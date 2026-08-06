# Android Port 9: bounded in-book search

Status: API 36 emulator- and API 34 physical-device-validated implementation
candidate. This contract is intentionally limited to the first essential-
feature slice after Port 8. The bounded audible TalkBack/search-sheet gate also
passes on the physical device.

## Outcome

Android readers can submit a book-scoped query, inspect bounded contextual
results, move to the previous or next retained result, open a specific result,
and see matching text emphasized on the page. A result jump becomes reading
history only after its exact Reader0 destination is successfully presented.

## Ownership and invariants

- Reader0 remains the only owner of EPUB text, matching, snippets, result
  locations, active-result state, pagination, and navigation semantics.
- Android owns the native search sheet, IME behavior, focus, TalkBack wording,
  touch targets, side-sheet composition, and native result rendering.
- UI0-derived native tokens supply colors, typography, spacing, shapes, and
  component states. Android does not reproduce the desktop layout pixel for
  pixel.
- Queries and results are transient, bounded to Reader0's public caps, and
  never persisted or synchronized by this slice.
- Search executes only after an explicit submit. Empty and over-cap queries
  fail visibly without replacing the last successful result set.
- No Java code interprets EPUB markup or derives a destination. Java consumes
  a versioned, validated, bounded JNI snapshot.
- Direct, previous, and next result movement use Reader0's Search navigation
  reason and Port 8's lifecycle/surface/presentation transaction. Failed or
  cancelled presentation restores the last presented location, history, and
  previously active result.
- Search-result emphasis is derived only from Reader0 frame highlight ranges
  and uses the product theme's search colors.

## Native Android adaptation

- Search is a dedicated reader side sheet opened from the reader chrome.
- The query is a native single-line text field with the Android Search IME
  action; controls and rows have at least 48dp interactive targets.
- Results show structural context when Reader0 can resolve it, a contextual
  snippet, active state, retained/total counts, and truncation disclosure.
- Pending, no-result, unavailable, and failure states remain visible and are
  announced through a polite accessibility live region.
- Back dismisses the sheet through the existing reader modal policy. Motion
  follows the existing UI0-derived duration and the Android reduced-motion
  setting.

## Non-goals

- Unicode-aware case folding, language-aware tokenization, stemming, ranking,
  or a durable full-text index.
- Library-wide or annotation search.
- Persisting a query across process death.
- Text selection, bookmarks, highlights, notes, or synchronization; those are
  separate essential-feature slices.

## Candidate evidence

On the exact Port 8 dependency closure, the candidate passes the dependency
guard, architecture audit, strict Windows build, all seven public smokes, both
existing real-book desktop search smokes, and the dual-ABI debug/test build.
The API 36 emulator passes all five focused search tests, the final complete
ordinary suite 90/90 in 4 minutes 2 seconds wall time, and the selected
130%-text/reduced-motion matrix 27/27 in 30.401 seconds of instrumentation time.
The confirmed-force-stop probe preserves durable reading state while clearing
transient search state. The crash buffer is empty and exit history contains only
the two expected user-requested force stops.

Manual API 36 review passes Paper, Warm dark, and High contrast search sheets
and page emphasis. A real IME/inset settling race found during that review is
covered by a bounded visible retry and a deterministic regression test. Active
and inactive page hits remain visually distinct in every reviewed palette.

On the vivo I2019/iQOO 9 SE, Android 14/API 34 ARM64, the focused search class
passed 5/5 in 7.787 seconds. The correctly selected ordinary matrix passed
90/90 in 194.467 seconds of instrumentation time (195.356 seconds wall), and
the external confirmed-force-stop seed and verification halves passed in 2.095
and 1.138 seconds. The coordinated 130%-text/reduced-motion matrix passed 27/27
in 27.219 seconds of instrumentation time (29.187 seconds wall). An earlier
direct 92-test invocation is excluded because it failed to apply Gradle's
`notAnnotation` filter and therefore ran the external-restart verification half
without its seed; the sole failure was that deliberately invalid lifecycle,
not a product failure.

The physical run began with font scale and all three animation scales explicitly
at `1.0`. Cleanup restored those same four values explicitly. A post-restore
vivo SystemUI notification-shade probe rendered 74 frames, confirming live
animation behavior rather than only stored values. The crash buffer was empty.
All 26 original app files (4,751,505 bytes) were restored byte-exact after the
automated and manual checks; the final archive SHA-256 is
`10E3C2001C569D4CEA6AB692536AD905AD747DACA483BEEA6E0C9B7015A5E0E3` and
the manifest SHA-256 is
`C3D0DF15FD831D5BECE7A32F13103429714AA2E20972022AAC28539CCD28DA6E`.

Representative-book review on *Gardens of the Moon* searched for `Paran`,
reported 630 matches while retaining and disclosing the first 64, and showed
meaningful Dramatis Personae, Prologue, and Chapter One context. Opening result
6 moved the reader from 0% to 3%, Next selected result 7, and the page kept the
active occurrence visually distinct from other highlighted occurrences. The
original reading state was then restored from the protected backup.

With TalkBack 17.0.1 and touch exploration enabled, Google TTS synthesized
spoken feedback while the physical user explored the Find title, query,
controls, result status, ordinary rows, and current-result state; activated a
Chapter One result and Next; and closed the sheet through system Back. No
incorrect or missing announcement was reported. The search sheet announced its
open/close state and returned accessibility focus to Find. Afterward TalkBack,
touch exploration, its input filter, temporary accessibility-volume key, and
notification permission/flags were restored to the exact pre-test states;
rotation returned to off, music/TTS volumes matched their snapshots, and font
plus all animation scales remained `1.0`. The same protected 26-file 8vo backup
again restored byte-exact. Broader whole-app screen-reader and compact/large-
viewport hands-on review remain pending.

## Acceptance gates

- Exact dependency and architecture guards, both Android ABIs, and host tests.
- Deterministic query, no-result, truncation, clear, result-row, previous/next,
  history, presentation-failure rollback, lifecycle, and process-restart tests.
- Accessibility checks for labels, focus order, enabled/selected states,
  large text, compact/large viewports, and reduced motion.
- API 36 emulator functional and visual review, including light, dark, and
  High Contrast page emphasis.
- Coordinated API 34 physical automation, representative-book search, explicit
  device-setting restoration with live behavior verification, empty crash
  review, byte-exact app-data restoration, and bounded audible TalkBack/touch-
  exploration acceptance of the search sheet.

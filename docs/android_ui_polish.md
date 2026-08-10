# Android UI polish contract

Status: bounded, launch-focused implementation contract for the Port 8
candidate. Navigation is the first vertical slice. This contract does not
claim Port 8 acceptance or Google Play readiness.

## Authority and ownership

- 8vo consumes UI0 exactly at
  `b1cf8e4fbe7e06b9799e251665bbe491ae4c22b5`, public API 91. The dependency
  guard requires this app pin to match Readerview0's reviewed nested UI0
  metadata for both Win32 and Android EPUB builds; another checkout is not an
  implicit build input.
- One product-owned 8vo chrome profile must be resolved through
  `UI0TokenPatch` and `UI0ResolvedTheme`. UI0 semantic roles, state roles,
  typography roles, spacing, radii, density, component variants, and derived
  component styles are the canonical ordinary-chrome contract.
- 8vo owns the concrete product profile values, persisted theme identity,
  wording, screen composition, workflows, and domain rendering. UI0 does not
  acquire 8vo product or Android policy.
- Reader0 remains the sole owner of EPUB navigation interpretation,
  pagination, canonical locations, meaningful pages, current-section identity,
  and history semantics. The polish work must not reproduce any of that logic
  in Java or Android-only C.
- Android remains a native host. Views, scrolling, text input, IME/autofill,
  touch, focus, TalkBack, system Back, insets, system bars, lifecycle, and
  platform motion behavior remain Android responsibilities.

## Cross-platform invariants

Desktop and Android must share one recognizable 8vo language without sharing
pixel coordinates or screen composition:

- semantic color and theme identity;
- Page title, Section title, Body, Caption/Metadata, Button, and Reader-chrome
  typography hierarchy;
- spacing rhythm and radius/shape language;
- the UI0 icon family where an icon is used;
- primary, quiet, selected, current, disabled, destructive, active, and focus
  treatment;
- source-order hierarchy, depth, parent/current-section cues, destination
  progress, and unavailable state;
- Contents, Go to, Return, Forward, and progress-display information
  architecture and wording; and
- calm motion character plus a complete zero-duration reduced-motion path.

The same semantic state must have the same meaning on both platforms. Desktop
current rows intentionally carry both the `Selected` surface treatment and an
independent `Current` indicator/wording. Android preserves both signals; neither
substitutes for the other, and a provisional request is never exposed as
current.

## Typed Android theme boundary

The first slice must replace screen-local token reconstruction with one
versioned, typed UI0-derived snapshot:

1. 8vo native code applies the product `UI0TokenPatch`, resolves a
   `UI0ResolvedTheme`, derives the public UI0 control, tree, and text-input
   styles needed by Navigation, and publishes the resolved panel tokens used by
   the native Android composition.
2. A bounded snapshot publishes a magic, snapshot version, UI0 API version,
   appearance kind, exact role and style counts, resolved colors, spacing,
   radii, typography, density, state-role triples, and the required derived-
   style fields. Explicit zero remains distinct from absence while the patch is
   resolved.
3. JNI copies the snapshot by value. An immutable Java record validates every
   version, count, enum, bound, and color encoding before exposing it. A stale,
   truncated, unknown, or malformed snapshot fails visibly; there is no legacy
   style fallback on the migrated surface.
4. One product-neutral native-Android adapter translates the validated record
   into `ColorStateList`, drawables, type bindings, and dp/sp values. It may
   depend only on the Android SDK and public shared-package contracts.
5. An Android density patch treats UI0 logical layout values as dp and raises
   every interactive control, row, menu item, and icon-button target to at
   least 48dp. Icon artwork may remain 24dp inside that target. UI0 line height
   selects native sp-scaled size; the binding validates the default Typeface's
   real font cell and representative advance at 100% and 130% text, and native
   Paint/Layout measurements—not nominal character width—drive wrapping.

The adapter must not know `OctavoActivity`, stores, resource wording, product
theme IDs, book state, or reader workflows. Thin 8vo glue selects the product
profile and supplies the snapshot.

## First vertical slice: Navigation

Included:

- the modal sheet surface, scrim, header, tabs, history controls, status area,
  Contents collection, Go-to forms, progress choices, and focus restoration;
- a single native clickable/focusable Contents row with distinct label and
  metadata typography, a UI0-derived current indicator, bounded hierarchy
  cues, visible disabled/pending/focus states, and one at-least-48dp hit target;
- stable row identity keyed by the validated navigation identity so snapshot
  refresh, pending state, and recoverable failure do not arbitrarily discard
  keyboard or accessibility focus;
- source-order TalkBack collection metadata, explicit level/parent/current/
  progress/enabled/action semantics, unique labels, and deterministic keyboard
  and switch order;
- compact adaptation that may stack a Go-to input and action when horizontal
  measure is insufficient, plus a large-screen side-sheet cap; and
- direction-neutral start/end geometry, RTL-aware motion, inset-safe bounds,
  native scrolling, and IME visibility for the focused field.

Excluded from this slice:

- changes to Reader0 destinations, pagination, locations, history, or EPUB
  interpretation;
- persistence schema changes, new navigation features, collapsible Contents,
  thumbnails, spatial previews, search, selection, or annotations;
- broad Appearance, reader-chrome, or Library restyling;
- Jetpack Compose, a pixel-for-pixel desktop port, or a speculative shared
  Android framework; and
- a UI0 pin advance unless API 91 proves insufficient and the upgrade is made
  explicit with dependency guards and full validation.

## State, presentation, and failure rules

- The last successfully presented Reader0 anchor remains authoritative.
  Contents, Go-to, Return, Forward, and progress-display changes remain
  provisional until their matching frame is posted and accepted.
- The Navigation UI must continue to display the presented current row and
  presented progress choice while a request is pending. Pending intent may be
  shown separately, but must not become a checked/current accessibility state.
- Accepted structural navigation may close the sheet only after presentation.
  Rejection, render failure, retry exhaustion, surface loss, pause, recreation,
  or teardown restores presented state, re-enables valid actions, retains a
  visible accessible error, and restores focus predictably.
- Opening, closing, resizing, focusing, or restyling Navigation must not
  repaginate, mutate the reading position, alter canonical reader geometry, or
  bypass the existing native presentation transaction.
- At most one document mutation awaits presentation. Existing latest-only or
  refusal policy, bounded retries, caller ownership, and successful-
  presentation persistence remain unchanged.
- Status changes use one accessibility announcement mechanism. The sheet must
  not introduce blank or redundant container stops, duplicate live-region
  speech, hidden gesture-only actions, or focus on an unlabeled root.
- Every failure is bounded, visible, and retryable where the underlying
  contract permits. There is no hidden thread, unbounded poll, silent capacity
  fallback, non-atomic durability fallback, or process-global mutable state.
- App reduced motion has an immediate zero-duration equivalent. System animator
  disabling must also complete in a coherent final state; animation callbacks
  cannot own lifecycle or presentation correctness.

## Validation gates

Complete emulator validation before requesting the physical device:

1. Exact dependency, API, source-consumption, architecture, and clean-worktree
   guards pass; arm64-v8a and x86_64 debug/test builds pass.
2. Snapshot/adapter tests cover versions and counts, explicit zero, all state
   mappings, stale/malformed rejection, the 48dp density patch, and Paper,
   Dusk, OLED, and High Contrast samples.
3. Navigation host tests cover current/disabled/pending/focused pixels and
   geometry, separate label/metadata roles, stable row IDs, maximum depth,
   long labels, truncation, empty/fallback/truncated models, and whole-number
   validation.
4. Instrumentation covers 320x480dp and 600x840dp viewports, portrait and
   landscape, 100% and 130% system text, LTR and forced RTL, gesture and
   three-button insets, and the IME over the final Go-to field. All actions stay
   visible or scroll-reachable, non-overlapping, and at least 48dp.
5. Accessibility automation covers the modal pane, collection/item metadata,
   source-order traversal, Tab/Shift+Tab, D-pad, Enter/Space, Back, one semantic
   status mutation per change, stable focus across refresh/failure, and focus
   restoration on close or accepted jump. Attached live-region event delivery,
   audible TalkBack, and touch exploration remain required emulator checks.
6. Forced pending, rejection, pre/post-present failure, retry exhaustion,
   surface replacement, pause/resume, rotation, recreation, and process restart
   prove that neither current-section nor progress-choice UI advances early and
   that rollback restores the exact presented UI.
7. Deterministic captures sample the UI0-derived colors, radii, padding,
   hierarchy/current indicator, focus ring, and disabled state across light,
   dark, and High Contrast themes. Opening and closing produce no bright or
   black transition frame and leave canonical reader geometry unchanged.
8. App reduced-motion tests run with zero duration; the full ordinary Android
   matrix, crash buffer, and process-exit review pass before a coordinated
   physical-device review.

## 2026-08-06 Navigation slice evidence

The first bounded implementation now derives a versioned API 91 snapshot from
the pinned UI0 source, validates and owns it in Java, and translates it through
a product-neutral Android adapter. Navigation consumes the adapter for its
colors, state lists, drawables, typography, spacing, radii, density patch,
hierarchy geometry, and text-input treatment. Selection uses the resolved UI0
role on every supported API. API 29 and newer apply the exact resolved UI0
accent to each field through public cursor/handle setters. API 26-28 instead
bind one fixed native compatibility accent, `#8B7560`, through the public
`colorAccent` and `colorControlActivated` theme attributes. That warm neutral
retains at least 3.5:1 contrast against the actual input surface of all six
supported UI0-derived themes and remains coherent across live theme changes.
It is a legacy Android accessibility adaptation, not a mirrored UI0 product
token. Reader0 navigation, location, history, persistence, and presentation
transactions are unchanged.

Typography currently binds the resolved UI0 role sizes to native Android text
layout. Automated evidence proves sp scaling, bounded fit, and representative
glyph advance at 100% and 130% text scale; it does not yet prove an explicit
role-to-typeface binding or strict metric agreement with the UI0 face. That
contract-tightening check remains open before full Navigation visual parity is
claimed.

The API 36 x86_64 emulator candidate passed:

- exact dependency and architecture guards plus arm64-v8a and x86_64
  debug/test builds;
- the focused UI0 snapshot, adapter, and Navigation host suite, 26/26 in 1.997
  seconds;
- the ordinary matrix, 85/85 in 245.948 seconds;
- the confirmed-force-stop seed/fresh-process restart driver;
- the accessibility/Navigation matrix, 22/22, at 130% system text with all
  three emulator animation scales at zero in 22.543
  seconds; and
- an empty crash buffer, with process exit history containing only expected
instrumentation/confirmed-force-stop 'USER REQUESTED' exits.

The same debug candidate also passed on a clean API 26 x86_64 emulator:

- the focused snapshot/adapter/Navigation suite, 26/26 in 1.449 seconds,
  including exact rendered-color checks of the framework cursor and center,
  left, and right selection handles;
- the ordinary matrix, 85/85 in 180.560 seconds;
- the confirmed-force-stop seed/fresh-process restart driver;
- the 130%-text/disabled-animation matrix, 22/22 in 22.217 seconds; and
- an empty crash buffer.

The API 26 reduced-motion run restored all four setting keys to their pre-run
absent state; a final read reported the default font scale as 1.0 and all three
animation-scale keys absent. API 26 does not expose the modern process-exit
history used by the API 36 review. This closes the API 26-28 cursor/handle code
gap for the debug candidate; signed release and Play-generated artifacts still
require their own minimum-SDK smoke.

The API 36 emulator restored exactly to font scale 1.0, window and transition
animation scales 1.0, and an absent animator-duration key. No physical device
was used. Automated compact-landscape coverage now models 130% text, a 48dp
lateral inset, 24dp status inset, 48dp navigation inset, in-place restyling, and
scroll reachability through the final Go-to choice while preserving a complete
48dp body target.

Deterministic desktop 8vo light/dark Contents captures were compared with live
API 36 Android Paper, Dusk, and High Contrast Navigation captures. The shared
current/selected hierarchy semantics, quiet neutral row surface, accent-family
current rail, visible type hierarchy, spacing rhythm, shapes, and state
treatment now read as one product language while Android retains its native
side sheet and 48dp controls. The review found and closed two defects: non-High
Contrast Android themes had mapped UI0 `Focus` to unrelated blue roles rather
than `AccentHover`, and High Contrast selected text failed 4.5:1 against the
composed current-row fill. All six themes now assert `Focus == AccentHover`, at
least 4.5:1 selected-row text contrast, and at least 3:1 non-text focus
contrast. The desktop runner's re10 comparison was baseline-different because
that checkout intentionally uses newer UI0/Readerview0 pins; no cross-pin pixel
equality is claimed.

This accepts bounded Navigation look-and-feel parity for the reviewed portrait
phone states, not pixel-for-pixel desktop rendering or full-application visual
parity. Strict UI0 role-to-typeface metric agreement, large-viewport manual
review, forced-RTL visual review, attached live-region event delivery, audible
TalkBack/touch exploration, alternate-input handling, and subjective motion
review remain open before requesting the physical device.

Physical reduced-motion testing must not disable device-wide animations while
the user may be interacting with the phone. The earlier database restoration
of an absent vivo animator key was byte-exact but did not restore all SystemUI
behavior; effective restoration required window, transition, and animator
scales explicitly set to `1.0`. Future physical runs must coordinate the test
window, restore all three to explicit `1.0`, and verify behavior as well as
stored values.

## Reuse and extraction rule

Keep the snapshot-to-native adapter and focused tests isolated inside 8vo for
now. Record remaining coupling to the 8vo JNI lifetime, product profile
selection, Activity sheet composition, wording resources, Navigation packet,
and focus-return controls.

Extraction is permitted only after a real re10 Android slice supplies a second
consumer and proves the identical contract. At that point the theme-to-native
adapter, semantic-record accessibility adapter, image bridge, native-window
presentation plumbing, or Android build/lifecycle utilities may be considered
individually. Until then, product workflows, persistence, Library/vault policy,
Activity composition, and lifecycle policy remain in their products.

After Navigation passes these gates, the same proven adapter and contract may
be applied in order to Appearance, reader chrome, and Library. The companion
Google Play launch-gap contract freezes the first-release scope independently
of that later polish.

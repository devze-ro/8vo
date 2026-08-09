# Android Port 11: provider-neutral global appearance synchronization

## Status and scope

This contract freezes the seventh independent Port 11 local slice: bounded,
provider-neutral synchronization bytes and review state for 8vo's existing
single global reader appearance. The implementation is accepted through its
final API 36 local qualification. It remains local-only and uses deterministic
simulated remote bytes. It adds no provider, account,
network permission, background worker, clock, OAuth flow, Google dependency,
or Drive connection.

This slice is deliberately appearance-only. The global Chapter/Page/Location/
Percentage choice has its own Port 8 store, UI, and native presentation
generation; it requires a later independently versioned portable family rather
than a two-store partial transaction. Per-book appearance overrides likewise
remain absent until inheritance, reset, removal, byte-identical re-import, and
different-bytes replacement semantics have their own product contract.

The portable family is `O1PF`. Android's private synchronization/review file is
`O1SS`. Neither is an extension of the Port 7 `O7PA` native packet or `O7ST`
local file.

## Portable profile semantics

An `O1PF` lane contains one complete appearance that a device successfully
presented as one native appearance generation. It has eight semantic fields:

| Field | Exact `u32` wire values | Android mapping |
| --- | --- | --- |
| Theme | `0` Paper, `1` Sepia, `2` Dusk, `3` Warm dark, `4` OLED, `5` High contrast | the six Port 7 palettes |
| Font intent | `0` Literary, `1` Clear | Android generic serif, generic sans serif |
| Text-size tier | `0` Compact, `1` Standard, `2` Comfortable, `3` Large, `4` Larger, `5` Largest | 14, 16, 18, 21, 24, 28sp |
| Line-spacing tier | `0` Compact, `1` Classic, `2` Comfortable, `3` Spacious | 1150, 1250, 1300, 1500 permille |
| Width | `0` Wide margins, `1` Balanced, `2` Focused width | Port 7 margins; 720, 860, 960 permille is derived |
| Alignment | `0` Publisher, `1` Ragged right | Port 7 alignment choices |
| Publisher colors | `0` Theme safe, `1` Allow publisher colors | Port 7 publisher-color policy |
| Reduced motion | `0` Off, `1` On | the explicit 8vo reduced-motion preference |

The numeric wire values are new `O1PF` semantic identifiers even where an
Android implementation value currently happens to match. A desktop or re10
adapter must map the named semantics explicitly, retain unsupported values,
and never reinterpret Android, Win32, SQLite, Reader0, UI0, or database IDs.
Typeface files, atlas bytes, resolved pixels, system font scale, density,
viewport, insets, pagination keys, paths, book IDs, page numbers, and Reader0
locations are not preference bytes. Cross-device portability promises the
same requested semantics and retained Reader0 anchor, not identical line
breaks or pages on different devices.

Reduced motion remains part of the explicit 8vo profile, but it never defeats
a stronger local accessibility signal. System animator-zero and Android touch
exploration suppress prompt motion regardless of a foreign `Off` value, and a
foreign value is never applied without confirmation.

## Identity, lanes, and exact bounds

The private store creates one random 128-bit appearance-device identity,
encoded as exactly 32 lowercase hexadecimal ASCII bytes. It is stable across
restart and independent of the annotation actor, annotation coordinator,
reading-position device ID, database IDs, paths, and future provider/account
binding. Import cannot rotate or overwrite it. A remote snapshot that advances
this device's own lane is rejected rather than treated as a local publication.

Each lane is:

```
(device_id, sequence, complete_profile)
```

`sequence` is in `1..Long.MAX_VALUE` and is comparable only within one device
lane. At most 16 lanes are retained. Capacity exhaustion is visible and
rejects the complete operation; there is no eviction.

Canonical `O1PF` version 1 is big-endian:

```
u32 magic                 // 0x4f315046, "O1PF"
u32 version               // 1
u32 profile_field_count   // exactly 8
u32 lane_count            // 0..16
repeat lane_count times, ascending by raw device_id bytes:
    ascii[32] device_id
    i64       sequence
    u32       theme
    u32       font_intent
    u32       text_size_tier
    u32       line_spacing_tier
    u32       width
    u32       alignment
    u32       publisher_colors
    u32       reduced_motion
u32 crc32                  // IEEE CRC-32 over all preceding bytes
```

The minimum canonical object is exactly 20 bytes. One lane is 72 bytes. Exact
version-1 length is `20 + 72 * lane_count`; the maximum is therefore exactly
1,172 bytes. A recognizable future object is at least the eight-byte
`O1PF`-magic/version prefix, has `version > 1`, and is at most exactly 65,536
bytes. Its remaining bytes are opaque. The first such object is retained
byte-for-byte; replay of identical bytes is unchanged, while different future
bytes cannot overwrite the occupied slot and produce visible attention.
Version-1 input above 1,172 bytes, future input above 65,536 bytes,
noncanonical ordering, duplicate devices, invalid hexadecimal, zero or
negative sequences, unknown field values, incorrect field count, bad CRC,
truncation, or trailing bytes is rejected without mutation.

## Deterministic merge and stale replay

Merge is a join of independently ordered device lanes:

- a previously unseen device lane is retained if capacity permits;
- a greater sequence replaces the prior lane for that same device;
- a lower sequence is stale and changes nothing;
- an equal sequence with the exact same profile is idempotent;
- an equal sequence with any different field is equivocation and rejects the
  complete input without changing memory or disk; and
- sequences from different devices are never compared and no wall-clock,
  device-ID, database-row, or provider-revision winner is elected.

The join is deterministic, commutative, associative, and idempotent for valid
non-equivocating inputs. Remote candidates are reviewed in ascending raw
device-ID order. A user's choice concerns one exact `(device_id, sequence)`;
a newer sequence from that device is a new candidate, while replay of the old
lane cannot resurrect or replace it.

## Successful-presentation and durable-publication gate

An `O1PF` local lane is evidence of a successfully presented appearance, never
of a load, request, preview, or mutable Reader0 state.

The first reader frame after install, migration, or restart qualifies the
currently loaded appearance through `onReaderPresentationChanged`. Explicit
appearance changes qualify only through `onAppearancePresented` after native
appearance generation equality and exact profile equality. Duplicate ordinary
frames are idempotent. Once a persistence failure has been surfaced, duplicate
frames cannot execute the retained Retry intent automatically.

Every layout-affecting apply uses `OctavoSurfaceView.requestAppearance`. Native
8vo retains the last successfully presented Reader0 spine/UTF-8 byte anchor,
rebuilds with locally resolved metrics, navigates through Reader0's public
Location path, and commits only after the posted page contains that anchor.
The sync slice never calls Reader0 rebuild directly and never serializes a
layout key, page number, or viewport. Theme, alignment, publisher colors, and
reduced motion share the same complete appearance generation even where they
do not repaginate.

The Port 7 `appearance.v1` file remains the product's local appearance record.
A durable pending transaction in `O1SS` bridges its separate atomic write:

1. validate the complete target and retain the exact prior local lane/profile;
2. for a remote choice, publish `APPLY_PENDING` before requesting the Surface;
3. require the exact target generation to post successfully;
4. atomically publish the exact target to `O7ST` using descriptor sync and
   same-directory `ATOMIC_MOVE`;
5. atomically finalize `O1SS`, advancing the local lane and recording the
   exact remote decision; and
6. only then expose the new lane through portable export.

For an ordinary local panel change, step 2 may be staged immediately after the
exact target frame posts, but it must precede the `O7ST` write. Initialization
and v1/v2-to-v3 migration use the same staged transaction after the first
successful reader frame. A Library-only launch cannot create or advance a
portable lane.

If staging fails, `O7ST` is untouched and Retry remains visible. If `O7ST`
publication fails, the durable pending transaction remains and the local lane
does not advance. If final `O1SS` publication fails after `O7ST` succeeds,
restart can recognize the exact target bytes plus pending intent and offer a
safe finalization Retry. Export always excludes a merely pending target.
Sequence exhaustion is a visible capacity failure with exact rollback.

Restart recovery never infers an `O7ST` publication from an equal in-memory
fallback. The Port 7 store reports whether it loaded canonical version-3 bytes,
a retained legacy record, a migration candidate, a missing record, or a
corrupt record; only a canonical load or a successful current-process atomic
save proves the local-file step.

If the user chooses to abandon a pending target, reverse publication is also
ordered. The exact origin profile must first post successfully, the origin must
then publish atomically to `O7ST`, and only then may `O1SS` atomically clear the
pending intent and record Later. Clearing private pending state before the
screen and local appearance file both return to the origin is forbidden.

## Offer and confirmation behavior

An appearance from another device is offered only when:

- a reader and its current appearance have posted successfully;
- strict position restore and every native presentation transaction are
  settled;
- the foreign lane is valid, is not this device's lane, and differs from the
  exact presented profile;
- its exact sequence has not been kept, accepted, or dismissed for the current
  review epoch; and
- no Appearance, position-confirmation, navigation, search, bookmarks/note, or
  other modal surface owns the reader.

The prompt says `Another device uses different reading settings`, lists only
the bounded labelled differences, and offers:

- `Use these settings`; and
- `Keep mine`.

`Use these settings` publishes `APPLY_PENDING`, requests the whole profile
through the existing Surface/Reader0 transaction, and reports success only
after the exact frame, `O7ST`, and final `O1SS` publications all succeed.
`Keep mine` moves nothing and durably suppresses only the exact foreign lane.
If the exact foreign profile is already presented, convergence is recorded
without showing a no-op prompt.

System Back is `Later`, not `Keep mine`: it durably dismisses the exact lane
for the current explicit reader-open review epoch. Recreation, pause/resume,
rotation, reflow, and surface replacement do not advance that epoch. Returning
to Library and explicitly opening a reader again does, so the unchanged lane
may be offered again. A newer foreign sequence is always a new review.

While `APPLY_PENDING`, Back cannot silently leave a target-looking screen with
an old durable lane. It retains the visible Retry unless the exact origin has
been successfully re-presented, the origin has been atomically restored to
`O7ST`, and a generation-bound rollback dismissal then publishes atomically in
`O1SS`. Process death retains the pending identity, origin, and target. On
restart the app first presents the proven durable `O7ST` profile, then offers
the exact safe Retry; it does not infer success from requested or in-memory
values.

A successfully presented local appearance change stales an open prompt whose
origin lane no longer matches. The prompt closes, focus returns, and review is
re-evaluated deterministically. A remote supersession similarly invalidates
old callbacks. Viewport change or reflow under the same profile does not stale
the prompt.

## Private `O1SS` state and failures

`<files>/port11/appearance-sync.v1` is a private checksummed file capped at
128 KiB. That bound leaves room for the complete current state plus one
recognizable future portable object at its independent 64 KiB input cap. It
owns only:

- the independent appearance-device ID;
- the current bounded `O1PF` lane set;
- the current explicit-reader-open review epoch;
- one bounded decision for each retained foreign lane;
- at most one exact local or remote pending transaction with origin and target;
- one bounded recognizable future input slot; and
- durable attention/error metadata without private book text or paths.

The temporary file is in the same directory. Publication writes and flushes
the complete candidate, synchronizes its descriptor, and performs
`ATOMIC_MOVE` with `REPLACE_EXISTING`; there is no non-atomic fallback. Decode,
merge, decisions, staging, finalization, rollback, and serialization validate
into temporary state first. Invalid, future, capacity, and pre-replace write
or sync failure preserve prior memory and disk bytes. A replace that reports
failure is treated as uncertain: in-memory/export state does not advance, the
exact bounded destination is reloaded and reconciled, and any noncanonical or
unprovable result blocks mutation with visible attention rather than assuming
which bytes won.

Missing state creates only a private device identity; it does not create a
portable lane before presentation. Corrupt current-version state is
quarantined within a fixed bound and fails visibly. One recognizable bounded
future object is retained without interpretation or overwrite. A different
second future object requires attention rather than silent replacement.

Global appearance is independent of books. Book removal, byte-identical
re-import, a different-bytes replacement, and switching books neither erase
nor rekey it. Reflow, viewport change, rotation, surface replacement, restart,
and process death retain the exact semantic Reader0 anchor and the durable
review transaction described above.

## Accessibility and lifecycle

The confirmation is a separate 8vo-owned native Android modal, not a generic
sync framework. It must provide:

- a named pane and heading, labelled current-to-remote differences, and
  unambiguous Use/Keep/Retry actions;
- at least 48dp targets, touch-mode input focus, TalkBack accessibility focus,
  deterministic initial focus, and restoration to the invoking reader control
  or Surface when the modal closes;
- wrapping and scrolling at large system text without clipped actions;
- modal exclusion of the Surface, reader chrome, other panels, and failure
  banner;
- polite live error status with explicit Retry;
- Back-as-Later semantics and lifecycle-safe reconstruction from `O1SS`; and
- zero entrance/exit motion when the presented 8vo preference requests reduced
  motion, the system animator duration is zero, or touch exploration is active.

The target-theme transition cover stays below the confirmation overlay. A
successfully presented target re-themes the prompt and system chrome without a
bright intermediate frame. Lifecycle hiding never records a decision.

## Separation and interoperability

`O1PF` and `O1SS` do not reuse `O1AP`, `O1AN`, `O1AS`, `O1RP`, `O1RS`, their
actors/devices, private files, CRDTs, coordinators, decisions, or size budgets
as a generic synchronization framework. They add no provider interface or
speculative shared Android package.

Desktop 8vo's `settings.v1` and re10's SQLite `app_setting` rows are local
implementation state. Their enum values, paths, row keys, URI hashes,
`updated_utc`, and save behavior are not portable. A future desktop/re10 peer
must consume canonical `O1PF` bytes through an explicit semantic adapter,
return durable-save failures, and satisfy its own successful-presentation gate.
Unsupported fields must remain retained rather than be silently mapped or
republished as fallbacks.

Before any concrete Drive connection, the launch encryption claim, hostile
schema-valid input policy, provider/account binding fingerprint, authorization
and revocation, `drive.appdata`/`drive.file` ownership, conditional revisions,
duplicates, account switching, disconnect/deletion distinctions, privacy
logging, first-sync/reinstall/rollback policy, and user-visible queue behavior
remain separate required contracts.

## Accepted local-only qualification

The smallest implementation following this contract must:

- create deterministic test-only remote `O1PF` lanes without bundling a
  production fixture;
- round-trip every semantic value and exact byte/count bound;
- prove canonical ordering, stale replay, idempotence, commutativity,
  associativity, equivocation rejection, capacity, sequence exhaustion,
  malformed/future/overbound handling, and atomic rollback;
- prove no prompt before successful presentation and no prompt for an exact
  match;
- prove Use, Keep mine, Later/re-prompt, supersession, local-change staleness,
  reflow/rotation/recreation/process-death recovery, visible failure, explicit
  retry, and duplicate-frame rejection;
- prove a layout-changing Use retains the last successfully presented Reader0
  spine/UTF-8 byte inside the posted page;
- prove modal TalkBack semantics, touch-mode focus, 48dp targets, large-text
  wrapping/scrolling, focus restoration, lifecycle behavior, and reduced
  motion; and
- prove the Port 8 progress choice/store, annotations, positions, catalog, APK
  permissions, dependencies, and production assets remain unchanged.

API 36 emulator validation precedes any request for another physical-device
window. Hands-on physical review can remain deferred without weakening the
local wire/store/presentation claims.

On 2026-08-09, the final exact dependency pins and dual-ABI debug plus
instrumentation build pass. On API 36 `emulator-5554`:

- the four newly added recovery cases pass 4/4 in an isolated 17.709-second
  run;
- the complete focused appearance-sync matrix passes 41/41 in 81.376 seconds;
- the deterministic loaded-pending pair also passes 2/2 in an isolated 9.765-
  second run;
- the corrected legacy appearance regression passes 36/36 in 126.493 seconds,
  after its corrected pair passed 2/2 in an isolated 9.457-second run;
- the externally driven restart seed passes 1/1 in 5.692 seconds, a force-stop
  leaves no surviving process, and the fresh-process verification passes 1/1
  in 3.744 seconds;
- the ordinary matrix, excluding only the two externally driven restart-probe
  methods, passes 200/200 in 532.465 seconds; and
- the crash and fatal buffers are empty.

Only `emulator-5554` was targeted. The iQOO and every other physical device
were not targeted. This accepts the bounded local `O1PF`/`O1SS` wire, store,
presentation, review, recovery, lifecycle, and automated accessibility gate;
it does not authorize a provider, progress-choice family, Drive connection,
encryption claim, Google/Play mutation, commit, push, or merge.

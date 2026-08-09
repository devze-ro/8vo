# Android Port 11: provider-neutral progress-display synchronization

## Status and scope

This contract and its implementation are accepted as the eighth independent
Port 11 local slice: bounded, provider-neutral synchronization bytes and review
state for 8vo's existing single global progress-display choice. The accepted
implementation remains local-only and uses deterministic simulated remote
bytes. It adds no provider, account, network permission, background worker,
clock, OAuth flow, Google dependency, or Drive connection.

The synchronized value is only the user's semantic choice of Chapter, Page,
Location, or Percentage. Current chapter text, page number, location number,
percentage, book identity, Reader0 anchor, pagination data, and viewport state
are derived presentation data and are never synchronized by this family.

The portable family is `O1PC` (progress choice). Android's private
synchronization/review file is `O1PS`. Port 8's `O8PG` file remains the
product-owned local record. None extends or reuses annotation `O1AP`/`O1AS`,
reading-position `O1RP`/`O1RS`, appearance `O1PF`/`O1SS`, or their identities,
decisions, future slots, files, merge code, or coordinators.

## Portable semantics

An `O1PC` lane contains one complete global progress-display choice that was
shown in a real successfully posted reader frame and then durably published to
`O8PG`:

| Exact `u32` wire value | Semantic choice | Android display |
| --- | --- | --- |
| `0` | Chapter | Reader0-authoritative current chapter label |
| `1` | Page | meaningful layout-relative page, or an honest unavailable fallback |
| `2` | Location | Reader0 canonical location |
| `3` | Percentage | Reader0 canonical percentage |

These are new `O1PC` semantic identifiers even where an Android/native enum
currently happens to match. Portable bytes never contain an Android enum ID,
the displayed label, a page/location/percentage value, a book digest, a
Reader0 spine/UTF-8 byte anchor, a path, database/source/catalog ID, timestamp,
provider revision, account, layout key, viewport, or pagination result.

The choice is global. Book removal, byte-identical re-import, different-bytes
replacement, and switching books neither rekey nor erase this family. A Page
choice remains valid when the current layout cannot expose a meaningful page;
the honest local fallback is presentation only and is not republished as a
different choice.

## Identity and exact bounds

`O1PS` creates one cryptographically random 128-bit progress-device identity,
encoded as exactly 32 lowercase hexadecimal ASCII bytes. It is atomically
persisted even when no portable lane exists, remains stable across restart,
and is independent of every annotation, position, appearance, database, path,
and future provider/account identity. Imported bytes cannot rotate it or
advance this installation's own lane.

Each lane is:

```
(device_id, sequence, semantic_choice)
```

`sequence` is in `1..Long.MAX_VALUE` and is comparable only within that one
device lane. At most 16 lanes are retained. Capacity exhaustion rejects the
complete operation visibly; there is no eviction.

Canonical `O1PC` version 1 is big-endian:

```
u32 magic                 // 0x4f315043, "O1PC"
u32 version               // 1
u32 choice_field_count    // exactly 1
u32 lane_count            // 0..16
repeat lane_count times, ascending by raw device_id bytes:
    ascii[32] device_id
    i64       sequence
    u32       semantic_choice
u32 crc32                  // IEEE CRC-32 over all preceding bytes
```

The minimum object is exactly 20 bytes. One lane is exactly 44 bytes. Exact
version-1 length is `20 + 44 * lane_count`, so the maximum is exactly 724
bytes. A recognizable future object is at least the eight-byte magic/version
prefix, has an unsigned version greater than 1, and is at most exactly 65,536
bytes. Its remaining bytes are opaque. The first such object is retained
byte-for-byte; identical replay is unchanged, while different future bytes
cannot replace the occupied slot and create visible attention.

Version-1 input above 724 bytes, future input above 65,536 bytes,
noncanonical ordering, duplicate devices, invalid hexadecimal, zero or
negative sequences, unknown choices, incorrect field count, bad CRC,
truncation, or trailing bytes is rejected without memory or disk mutation.

## Deterministic merge and stale replay

Merge is a join of independently ordered device lanes:

- a previously unseen device lane is retained when capacity permits;
- a greater sequence replaces the prior lane for that same device;
- a lower sequence is stale and changes nothing;
- an equal sequence with the same choice is idempotent;
- an equal sequence with a different choice is equivocation and rejects the
  complete input without mutation; and
- sequences from different devices are never compared, so no wall-clock,
  device-ID, database-row, provider-revision, or account winner is elected.

For valid non-equivocating input, join is deterministic, commutative,
associative, and idempotent. Review order is ascending raw device-ID order.
Candidate identity is the exact `(device_id, sequence, choice)` plus the
review epoch and exact local origin lane. A newer sequence resets only that
foreign lane's decision. Stale replay cannot resurrect an old prompt or
decision. An imported own-lane advance or own-lane equivocation rejects the
whole input; an equal own lane is idempotent and a lower one is stale.

## Real-frame and durable-publication gate

An `O1PC` local lane is evidence of a real successfully posted reader frame
and durable `O8PG` bytes. It is never evidence of a Library launch, load,
fallback, requested mode, Java/native initialization, mutable reader state, or
failed post.

Port 8 initializes its requested and presented progress generations before
the first frame. That constructor state is deliberately insufficient. A new
progress-presentation receipt is available only when:

- the reader, document, Surface, and a committed frame are active;
- `frame_count > 0`;
- strict reading-position recovery is settled;
- host, page, reflow, appearance, progress, and navigation transactions are
  all settled;
- requested and presented progress modes and generations exactly match;
- the current Java mode exactly matches the posted native mode; and
- Reader0's exact presented spine/UTF-8 byte anchor is valid and contained in
  the posted page.

The receipt includes the exact choice, progress generation, frame count,
Reader0 anchor, and page bounds. It supplies stale-callback identity but does
not add any of those values to `O1PC` bytes. Duplicate receipts are idempotent
and cannot execute a previously surfaced Retry intent automatically.

The first real frame after install or restart may qualify the loaded choice.
A canonical version-1 `O8PG` load proves the local-file step only after that
frame. A missing, corrupt, or future local record is not proof. A missing or
corrupt safe fallback must be atomically saved after the frame before creating
lane sequence 1. A future local record is preserved and blocks synchronization
until explicitly resolved. In-memory equality alone is never proof.

## Ordered `O8PG`/`O1PS` transaction

`O8PG` remains the product's local progress record. `O1PS` bridges the two
independently atomic files with at most one durable pending transaction.

For a local Navigation-panel change:

1. validate and atomically stage `LOCAL_PENDING` in `O1PS`, retaining the
   exact durable origin lane/choice and reserved next local sequence;
2. request the semantic choice through `OctavoSurfaceView`;
3. require an exact matching real-frame receipt;
4. atomically publish the target to `O8PG` with descriptor sync and
   same-directory atomic replace;
5. atomically finalize `O1PS`, advancing the local lane; and
6. only then expose the lane through portable export.

For `Use this display`, step 1 instead stages `REMOTE_PENDING` with the exact
foreign candidate and review epoch. Finalization both advances the local lane
and records acceptance of that candidate. `Keep mine` never stages or requests
a Surface change.

Stage failure leaves `O8PG` and the screen untouched. Presentation failure
rolls the native mode back to the last presented origin and retains durable
pending intent plus visible Retry. `O8PG` failure retains the pending
transaction and does not advance the lane. If final `O1PS` publication fails
after `O8PG` succeeds, restart can recognize exact canonical target bytes and
offer an explicit finalization Retry. Export always excludes a pending target.
Atomic-replace outcome uncertainty is reconciled by a bounded reload that may
accept only the exact prior bytes or exact candidate bytes; every other result
blocks visibly.

Back cannot silently leave target pixels with an origin lane. If a pending
target is abandoned, reverse publication is ordered: re-present the exact
origin, atomically publish the origin to `O8PG`, then atomically clear pending
state in `O1PS` and record Later for a remote candidate. Clearing `O1PS` first
is forbidden. Back during a provisional target or rollback is consumed.

Pause, stop, duplicate frames, recreation, and process death never perform a
retained user-action-required Retry implicitly. They preserve enough bounded
private identity, origin, target, decision, and transaction direction to
reconstruct the same Retry surface.

## Offer and confirmation behavior

A foreign progress choice is offered only when:

- an active reader has a current real-frame receipt;
- the receipt choice exactly equals canonical `O8PG` and the finalized local
  `O1PS` lane;
- the foreign lane is valid, current, different, and not this device's lane;
- its exact sequence has not been accepted, kept, or dismissed for the current
  review epoch; and
- no appearance, reading-position, Navigation, search, bookmark/note,
  selection, or other modal owner is active.

The prompt says `Another device uses a different progress display`, compares
`Yours` with `Other device`, explains that the choice changes only the reader's
progress detail and does not move the reading place, and offers:

- `Use this display`; and
- `Keep mine`.

`Use this display` succeeds only after the exact posted receipt, `O8PG` save,
and `O1PS` finalization. `Keep mine` moves no Reader0 state, makes no Surface or
`O8PG` request, and durably suppresses only the exact candidate. If the exact
foreign choice is already the finalized local choice, convergence is recorded
without a no-op prompt.

System Back means `Later`, not `Keep mine`. It dismisses the exact candidate
only for the current explicit-reader-open review epoch. Recreation,
pause/resume, rotation, viewport/reflow, and Surface replacement do not
advance the epoch. Returning to Library and explicitly opening a reader does,
so an unchanged candidate may be offered again. A newer foreign sequence is a
new review. A successfully completed local choice closes and re-evaluates an
open prompt; stale detached callbacks cannot act on a replacement candidate.

Only one reader modal owns accessibility and focus. Deferred prompts drain in
the established order: appearance, reading position, then progress display,
with durable recovery before new candidates within each family. Closing any
modal runs the same bounded drain so another eligible prompt cannot be
stranded.

## Private `O1PS` state and failures

`O1PS` is stored at `<files>/port11/progress-display-sync.v1` with an exact
131,072-byte cap. It contains only bounded product-private state:

- the stable progress-device identity;
- at most 16 current `O1PC` lanes;
- one global explicit-open review epoch;
- one current decision per foreign lane: `NONE`, `ACCEPTED`, `KEPT`, or
  `DISMISSED_AT_EPOCH`;
- at most one exact `LOCAL_PENDING` or `REMOTE_PENDING` transaction;
- at most one retained future `O1PC` object of at most 65,536 bytes; and
- bounded typed error/attention state.

It contains no book, path, Reader0 anchor, displayed progress value, provider,
account, credential, network revision, or wall time. Missing state atomically
publishes only the new stable identity. Current malformed state is quarantined
byte-for-byte to one of three fixed names; inability to quarantine or publish
a fresh identity blocks mutations and export. A recognizable future private
version of at most 131,072 bytes is preserved and blocks. All mutations are
built and validated in candidate memory before descriptor-synced temp write
and same-directory atomic replace; there is no non-atomic fallback or partial
merge.

Missing, invalid, future, overbound, capacity, sequence-exhaustion,
equivocation, stale-callback, presentation, local-save, private-publish, and
uncertain-write failures are distinct and visible. A failure never silently
changes the screen, `O8PG`, portable lane, decision, or retained input.

## Accessibility, lifecycle, and motion

The prompt is a separate product-owned view, not a row in Navigation and not a
generic sync UI framework. It has a real scroll container, accessibility pane
title and heading, a labelled current/remote comparison, polite live error,
48dp minimum actions, touch-mode focus, deterministic initial and restored
focus, and a Retry-only failure state. At 200% text it wraps and scrolls.

While shown, the reader Surface, chrome, and failure banner are excluded with
`NO_HIDE_DESCENDANTS`. Back uses the durable Later path. Pause/resume,
configuration change, window focus, and process recreation neither decide nor
re-prompt implicitly. Focus returns to the Progress affordance when available,
otherwise the reader Surface, unless another modal has opened.

Prompt motion is zero when explicit 8vo reduced motion, system animator-zero,
or touch exploration requires it. Otherwise it uses bounded product motion.
No OS accessibility setting is changed by this slice.

## Desktop/re10 interoperability and separation

Desktop 8vo and re10 currently have no selectable progress-display setting;
both render a fixed combined Percentage/Location projection with a Page
fallback. Their local settings and database IDs therefore are not portable
progress choices. A future adapter must decode the named `O1PC` semantics,
retain unsupported lanes without publishing a fallback choice, return durable
save failures, and publish only after actual presentation. It must never copy
Android/native enum IDs, paths, URIs, source/catalog/database IDs, timestamps,
or re10's older clock-based progress envelope.

`O1PC`/`O1PS` are not a transport and do not authorize Drive. Before any
provider connection, the separate launch encryption, threat model,
provider/account binding, authorization/revocation, ownership scope,
conditional-write mapping, duplicate-object, account-switching,
deletion/disconnect, privacy/logging, first-sync, reinstall, and rollback
contracts remain required.

## Accepted local-only implementation and qualification

The accepted smallest vertical slice:

- encodes, decodes, exports, merges, and retains bounded canonical `O1PC` bytes;
- persists independent private `O1PS` identity/review/pending/future state;
- creates deterministic test-only remote candidates with no production fixture;
- waits for an exact real-frame progress presentation receipt;
- offers the confirmation prompt only when the contract permits;
- applies `Use this display` without moving the Reader0 anchor and claims success
  only after receipt, `O8PG`, and `O1PS` publication;
- implements durable Keep, Later, retry, restart, rollback, supersession, and
  convergence behavior;
- exposes visible typed failure and explicit Retry; and
- adds deterministic codec, store, prompt, receipt, integration, process-death,
  coexistence, accessibility, and legacy-regression tests.

On API 36 `emulator-5554`, the exact dependency guard and architecture audit
pass, and the debug plus instrumentation APKs build for both `x86_64` and
`arm64-v8a`. Acceptance evidence is:

- the isolated preserved-future `O8PG` Retry/Back regression passes 1/1 in
  6.330 seconds;
- the focused progress-display matrix passes 37/37 in 78.279 seconds;
- the legacy `O8PG` store regression passes 7/7 in the final rebuilt rerun in
  0.202 seconds;
- structural navigation integration passes 6/6 in 21.030 seconds;
- the cross-family coexistence regression passes 49/49 in 143.490 seconds;
- the external restart seed passes 1/1 in 7.514 seconds, force-stop is confirmed
  with no surviving process, and fresh-process verification passes 1/1 in
  5.126 seconds;
- the ordinary matrix, excluding only the two externally driven restart probe
  methods, passes 238/238 in 438.899 seconds; and
- crash and fatal buffers are empty.

Only `emulator-5554` was targeted. No physical device was targeted, and this
slice did not connect Google Drive or add a provider interface, permission,
dependency, worker, clock, account, OAuth flow, or network code. `O1PC`, `O1PS`,
and `O8PG` remain separate from each other and from every annotation, position,
and appearance family; this acceptance does not authorize a generic sync
framework or any concrete provider work.

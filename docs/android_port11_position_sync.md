# Android Port 11: provider-neutral reading positions v1

Status: implemented and API 36 emulator/API 34 physical-qualified 2026-08-09
as a disconnected, local-only slice. It does not connect Google Drive, add a
provider or account interface, request network access, schedule background
work, or authorize cloud, OAuth, signing, or Play Console actions.

## Boundary and authority

8vo owns the position device identity, bounded portable bytes, deterministic
merge, private decisions, persistence, retry, and confirmation workflow.
Reader0 remains the sole authority for EPUB interpretation, pagination,
navigation, and locations. A portable anchor is exactly Reader0's zero-based
spine index plus byte offset in Reader0's normalized UTF-8 spine text. It is not
a page number, percentage, CFI, code-point offset, layout coordinate, URI,
path, catalog row, or database identifier.

This is an independent record family. It does not read, write, wrap, embed, or
reuse annotation `O1AP`, private annotation `O1AN`, or annotation coordinator
`O1AS` bytes. It has its own magic, bounds, device ID, sequence lanes, merge,
private file, and user decisions. No generic synchronization framework or
provider abstraction is introduced.

`book_digest` is the exact lowercase 64-hex SHA-256 of the imported EPUB bytes.
The same bytes at another path are the same book. Removal does not erase this
family, so a byte-identical re-import can reconnect. Different bytes, including
a replacement at the same path, have a different digest and never inherit,
display, accept, or publish the old book's positions.

## Stable identity and exact bounds

Each installation has a private, cryptographically random 128-bit
`position_device_id`, encoded as 32 lowercase hex characters. It is created
once and retained independently of the annotation actor; annotation actor
rotation must not change it. A candidate's stable identity is the exact tuple
`(book_digest, position_device_id, position_sequence)`. A sequence is in
`1..Long.MAX_VALUE` and increases only after this device successfully presents
a different authoritative anchor for that book. Exhaustion is a visible,
non-mutating failure.

The portable `O1RP` object describes one exact book and at most 16 device
lanes. A lane contains:

| Field | Encoding and bound |
| --- | --- |
| device ID | length-prefixed UTF-8; exactly 32 lowercase hex bytes |
| position sequence | big-endian `i64`; `1..Long.MAX_VALUE` |
| spine index | big-endian `i64`; `0..0xffffffff` |
| UTF-8 byte offset | big-endian `i64`; `0..Long.MAX_VALUE` |

The container is big-endian: magic `0x4f315250` (`O1RP`), version `1`, header
field count `1`, one length-prefixed 64-byte book digest, lane count `0..16`,
canonical lanes in ascending device-ID byte order, and an IEEE CRC-32 checksum
of all preceding bytes. Duplicate or out-of-order lanes reject the whole
container. Its exact version-1 maximum size is 1,048 bytes. A bounded reader
accepts at most 128 KiB so it can recognize and preserve a future-version
object without interpreting it. Trailing bytes, invalid UTF-8,
noncanonical hex, a bad checksum, a wrong header count, or any over-bound scalar
reject the complete object without mutation. A recognizable future version is
reported distinctly and preserved as input; it is never partially interpreted.

This checksum detects accidental corruption. It is not authentication,
encryption, or a claim that schema-valid input is trusted. Concrete-provider
encryption, threat, account-binding, authorization, duplicate-object, and
ownership policy remains expressly unfrozen.

## Offline merge and stale replay

Merge is a checked, all-or-nothing union of lanes for the same book digest:

- a missing remote object contributes no lanes;
- for a new device ID, retain its lane if the merged count remains at most 16;
- for the same device ID, a greater sequence replaces the lane;
- a lower sequence is a stale replay and is an idempotent no-op;
- an equal sequence with the same anchor is idempotent; and
- an equal sequence with a different anchor is device equivocation and rejects
  the entire candidate without changing durable or in-memory state.

There is no eviction, truncation, partial merge, or wall-clock field. Sequences
from different devices are never compared for causality and there is no single
cross-device winner. When several foreign lanes are eligible for review, UI
selection only is deterministic: the semantically furthest anchor
`(spine, byte)` sorts first, with ascending device ID as the tie-break. This
ordering does not alter or discard any lane.

## Successful-presentation rule

Only the explicit last-successfully-presented Reader0 anchor may advance this
device's lane. Current mutable Reader0 state, a requested navigation target, a
laid-out page, lifecycle teardown, and a frame that failed to post are never
publishable. The check is enforced inside the durable record/publish operation,
not merely by its callers.

Ordinary page moves, reflow, reopen, restore, and **Go there** all use the same
gate:

1. Reader0 returns the requested exact spine/byte target. Because Reader0's
   navigation API deliberately clamps some offsets, equality with both supplied
   values is mandatory; a clamped result is an over-bound failure.
2. The presentation transaction remains pending until a surface post succeeds
   for the expected document, generation, layout, page, and containing range.
3. Only then may 8vo durably record the host's exact Reader0 presented anchor
   and expose it in `O1RP`. Ordinary page turns present a page-start anchor;
   structural Location navigation retains its exact qualified interior-byte
   semantic anchor.

A speculative qualification navigation is always rolled back before a prompt
is shown. It may derive a display-only location label, but a label, percentage,
or page number never validates or replaces the raw anchor. Any failure restores
the prior presented origin, leaves the local lane and decision unchanged, and
produces visible retry state.

## Offer and confirmation semantics

A foreign candidate is offered only after the current book has a successfully
presented frame, the digest matches, its complete `O1RP` input and lane are
valid, Reader0 can resolve the exact anchor without clamping, and all of these
are true:

- it is semantically after the current presented anchor;
- its anchor is outside the currently presented page;
- the exact candidate is not already accepted or declined; and
- it is not dismissed for the current review epoch.

The modal surface says **Another device is at …** using a Reader0-derived label
when available and offers **Go there** and **Stay here**. **Go there** first
durably records the exact candidate as pending, then navigates through Reader0.
It becomes accepted only after the posted page contains the exact qualified
target. The same atomic completion advances this device's lane to that exact
presented semantic anchor. A failed post, surface loss, process death, or navigation
failure leaves a visible pending failure with **Retry**; it never claims the
move succeeded.

**Stay here** is a durable decline of only that exact device/sequence. It does
not navigate or advance the local lane. A later sequence from that device is a
new candidate and may be offered. Back is a durable dismiss, not a decline: it
suppresses that exact candidate for the current review epoch and restores focus
without moving. It becomes eligible again on the next explicit book reopen.
While a Reader0 navigation is still provisional, Back is consumed until the
presentation either commits or rolls back; after rollback, Back may durably
dismiss the retained `go-pending` candidate without exposing provisional state.
Activity recreation, configuration change, and process recreation of the same
open-reader task do not advance the review epoch and therefore do not turn a
dismiss into an immediate re-prompt. A lifecycle pause merely hides the surface;
it records no choice and the same prompt is restored on resume.

The prompt binds the exact book/candidate identity and the local presented
sequence/anchor from which it was created. A viewport or text reflow that
successfully presents the same raw anchor keeps it valid. Any other successful
local move while it is open durably dismisses it for the current epoch and
closes it without inventing a **Stay here** choice. A matching pending
**Go there** presentation instead completes the acceptance transaction.

## Private state and recovery

Private `O1RS` state is stored only under 8vo's application files. It retains
one installation device ID and at most 64 book groups; each group retains at
most 16 merged lanes, the local review epoch, and one bounded decision per
foreign device's current sequence. Decisions are `none`, `go-pending`,
`accepted`, `stayed`, or `dismissed-at-epoch`. Superseding a lane sequence
atomically resets its prior decision. The complete private file is capped at
128 KiB; exceeding any count or byte cap fails rather than evicting data.

Every state transition serializes complete canonical bytes to a same-directory
temporary file, flushes and syncs the file, and performs an atomic replace.
There is no non-atomic replacement fallback. A write, sync, size, or move
failure leaves the last committed state byte-for-byte intact and keeps the
failed intent visibly retryable. A missing file is an empty first run. A future
private version is preserved and blocks mutation visibly. Malformed state is
moved atomically to one of three bounded quarantine names before a fresh state
may be created; inability to quarantine blocks mutation rather than overwriting
the evidence.

Book removal retains the bounded digest group. Byte-identical re-import may use
its last successfully presented local lane as a strict Reader0 restore anchor;
an invalid or over-bound retained anchor fails visibly and is not silently
clamped or replaced. Different-bytes replacement starts an unrelated group.
Reflow and viewport changes preserve the raw anchor; restart and process death
reconstruct committed lanes, decisions, pending Go/retry, and review epoch.

## Accessibility, lifecycle, and motion

The confirmation is a real modal accessibility boundary. While visible, the
reader and unrelated controls leave the accessibility tree; initial focus goes
to the heading or primary action; every action is at least 48dp; state and error
changes are announced as a live region; and focus returns to the invoking
reader control or reader surface after close. Content is scrollable and wraps
without clipping at large font and display scales. Labels do not rely on color.

Back performs the durable dismiss above. Recreation restores the exact pending
surface after the first qualified presentation. Lifecycle hiding does not
accept, decline, or dismiss. Motion is absent when system animator duration is
zero or touch exploration is active, and otherwise uses the existing bounded
product-side panel motion; correctness never depends on an animation callback.

## Portable interoperability and explicit exclusions

A future desktop or re10 adapter consumes and produces only canonical `O1RP`
bytes keyed by exact EPUB SHA-256 and navigates through Reader0 anchors. It must
not copy Android catalog rows, desktop catalog IDs, re10 SQLite source/progress
IDs, timestamps, canonical paths, or URI hashes. Existing desktop/re10 local
restore behavior is precedent only for Reader0 anchor handling, not portable
identity, conflict selection, or presentation gating.

This slice uses deterministic simulated remote bytes only. It adds no Google
dependency, `INTERNET` permission, provider interface, account concept, worker,
clock, OAuth flow, Drive object, logging of digests/device IDs/anchors, or cloud
mutation. Before a concrete Drive adapter, the separate launch-encryption,
hostile-input, binding-fingerprint, authorization/revocation, scope/ownership,
conditional-write, duplicate-object, account-switching, deletion, privacy,
first-sync, reinstall, and rollback contracts must be frozen explicitly.

## Local qualification

On 2026-08-09, the exact Ground0, Reader0, UI0 API 91, and Readerview0
dependency guard and the architecture audit pass. The debug and instrumentation
APKs build for both `x86_64` and `arm64-v8a`. On API 36 `emulator-5554`:

- the portable/store, confirmation-integration, and strict-resume classes pass
  24/24 together in 45.912 seconds;
- the external restart driver passes its 1/1 seed in 5.264 seconds, confirms a
  force-stop with no surviving process, and passes its 1/1 fresh-process check
  in 3.742 seconds;
- the ordinary matrix, excluding only the externally driven restart probe,
  passes 158/158 in 462.672 seconds; and
- the crash and fatal buffers are empty.

The first API 34 iQOO focus run passed 22/24 and exposed that Android `Button`
does not accept input focus in touch mode by default. The confirmation actions
now explicitly accept touch-mode focus, and the test locks all three actions to
that property. On the corrected ARM64 candidate:

- both previously failing focus cases pass 2/2 in 4.388 seconds, and the complete
  focused position matrix passes 24/24 in 23.245 seconds;
- the external restart driver passes its 1/1 seed in 1.992 seconds, confirms a
  force-stop with no surviving process, and passes its 1/1 fresh-process check
  in 1.076 seconds;
- the ordinary matrix passes 158/158 in 317.321 seconds; and
- the crash/fatal buffers are empty and exit history contains only expected
  user-requested instrumentation force-stops and package replacement.

The production APK declares no permissions and contains no simulated remote
snapshot fixture. No physical system setting changed: font and all animation
scales remained `1.0`, while accessibility and touch exploration remained off.
Cleanup restored the accepted app/test APK hashes, the exact 26-file persistent
archive, and every path/length/SHA-256 in the accepted 32-file, 4,996,158-byte
private manifest; no app or test process survived. Automation covers the modal
boundary, 48dp actions, touch-mode focus, synthetic 200% wrapping, and reduced
motion. The user explicitly deferred hands-on iQOO TalkBack/large-text review.
No Google Cloud, OAuth, Drive API, Play Console, or signing-key action was
performed by this slice.

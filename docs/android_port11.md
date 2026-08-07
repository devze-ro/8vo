# Android Port 11: durable annotations and synchronization contract

Status: implementation contract adopted 2026-08-07. The first slice is local
bookmarks only. Google Drive remains disconnected until the portable record,
persistence, and deterministic merge tests pass offline.

## Boundary

8vo owns annotation workflows, durable state, migrations, merge policy, UI,
and future provider coordination. Reader0 remains authoritative for EPUB
interpretation, UTF-8 text bytes, pagination, selection, locations, and
navigation. Readerview0 and UI0 may supply public projection, state, semantic,
panel, and color mechanics; they do not own annotation records or persistence.

Android identifies a book by the lowercase 64-hex SHA-256 digest of its managed
EPUB bytes. An annotation anchor is Reader0-owned data copied by value:

- point: `spine_index`, `text_byte_offset`;
- same-spine range: `spine_index`, `text_byte_start`, `text_byte_end`;
- every byte is a canonical UTF-8 text byte, never a Java character index,
  page number, rendered coordinate, CFI invented by the host, or file path.

The host may ask Reader0 for section labels, excerpts, location summaries, and
navigation. Those projections are replaceable metadata; they cannot change an
anchor. A navigation is committed to history and panel state only after the
target frame is successfully presented.

## Portable records

The provider-neutral state is a canonical, versioned collection of record
envelopes. It is suitable for local files, export, and a later provider adapter.

| Field | Contract |
| --- | --- |
| `record_id` | Stable lowercase 32-hex identity. A bookmark ID is the first 128 bits of SHA-256 over a fixed namespace, book digest, kind, and point anchor. Highlight and note IDs use 128 bits from a cryptographic random source. |
| `kind` | `bookmark`, `highlight`, or `note`; immutable for an identity. |
| `book_digest` | Lowercase 64-hex EPUB digest; immutable for an identity. |
| `anchor` | Validated Reader0 point or same-spine range. Highlight ranges are nonempty. Notes may use a point or a nonempty range and may reference a highlight ID in the same book. |
| `payload` | Bookmark label/excerpt/star; highlight color/excerpt/star; or note text/star. Strings are UTF-8, length-bounded, and preserved exactly after validation. |
| `mutation` | Stable ID, stable actor ID, positive actor counter, causal context, operation (`put` or `delete`), and the complete payload needed to recover that head. |
| `frontier` | Per-record vector of the greatest incorporated counter for each actor. It compacts causally dominated history without erasing live concurrent heads. |

Wall-clock time may be retained only as display metadata. It never chooses a
winner, establishes causality, generates an identity, or orders canonical
bytes.

Bounds for version 1 are part of the file contract: 2,048 record envelopes,
16 actors per record frontier, eight concurrent heads per record, 16 MiB total
file size, 256 UTF-8 bytes for a label, 512 for an excerpt, and 4,096 for note
text. Bookmark and highlight colors are a fixed four-value semantic palette,
not stored ARGB values. Exceeding a bound rejects the whole candidate mutation
or merge and preserves the prior state.

## Mutation and merge

Each installation has one random stable actor ID and a monotonically increasing
local counter stored in the same atomic state. A local mutation:

1. copies the current record frontier and live heads as its causal context;
2. advances the local actor counter without overflow;
3. creates a canonical mutation ID from the complete mutation bytes;
4. replaces only heads it causally observes; and
5. publishes the complete candidate state atomically before the UI claims
   success.

Merge is pure, commutative, associative, and idempotent:

1. validate both complete inputs and immutable envelope fields;
2. union exact mutation identities, rejecting an identity with unequal bytes;
3. take the component-wise maximum frontier;
4. discard a head only when another incorporated mutation causally dominates
   its actor/counter dot;
5. retain all non-dominated concurrent heads, sort actors, heads, and records by
   unsigned canonical byte order, and revalidate every bound.

A record is visibly present when it has at least one live `put` head. A
concurrent `delete` head is retained as conflict state but does not silently
erase an unseen concurrent put. Concurrent bookmark/highlight heads use a
deterministic projection and expose a conflict for later resolution. Every
concurrent note body remains available; the UI must show each body and let the
user save a new mutation that causally observes all heads. A resolved delete is
a durable tombstone, not physical removal. Physical compaction requires proof
that every participating replica incorporated the tombstone and is outside this
port.

## Local persistence and recovery

Android stores one canonical state at `<files>/port11/annotations.v1`. The file
has a magic value, schema version, declared counts, canonical ordering, explicit
lengths, and a CRC32 over all preceding bytes. Loading is all-or-nothing.

Publication writes a bounded same-directory temporary file, flushes it,
synchronizes its descriptor, and uses `ATOMIC_MOVE` with `REPLACE_EXISTING`.
There is no non-atomic fallback. A failed write, sync, or move leaves the prior
published bytes and in-memory state authoritative, keeps the requested action
available for retry, and reports a visible error.

Missing state starts empty without publishing. A known older version is first
fully validated, migrated in memory, and published only through the same atomic
path; no older annotation schema exists yet. A future version is preserved and
blocks mutation instead of being rewritten. Malformed current-version state is
atomically moved to one of three bounded quarantine names before an empty store
becomes writable. If quarantine fails, the store remains read-only and the UI
reports recovery failure. Recovery never silently overwrites suspect bytes.

The catalog, positions, preferences, annotations, and managed EPUBs are
independent atomic files. 8vo does not claim a cross-file transaction.

## Book and layout semantics

- Reflow, rotation, font changes, Activity recreation, and process restart keep
  the same digest and Reader0 byte anchors. Page numbers may change.
- Removing a managed EPUB does not delete its annotations. The Library must
  offer local-data deletion separately. Re-importing byte-identical content
  reconnects the existing digest and records; different bytes are a new book.
- Navigation to a missing, invalid, or no-longer-resolvable anchor fails
  visibly without changing the current position or deleting the record.
- EPUB replacement and repair verify the digest before association. No title,
  path, provider URI, or filename is sufficient identity.

## Android interaction and accessibility

The reader chrome provides an explicit current-location bookmark toggle and an
explicit Bookmarks entry point; neither workflow is gesture-only. The toggle
uses the last successfully presented Reader0 point. Its label, state,
description, TalkBack announcement, and list update change only after atomic
publication. Failure leaves the prior visual and accessible state exact.

The first workspace is a bounded native Android side sheet with a heading,
polite status/error live region, current-book bookmark count, stable ordered
rows, Go to, Remove, and Done. Rows expose a useful section/location label and
excerpt without making either the anchor. All controls have at least 48dp
targets, deterministic keyboard and accessibility traversal, large-text
scrolling, focus restoration, RTL-aware placement, and a non-animated path
under reduced motion or touch exploration. Opening and closing the sheet do not
repaginate. Back closes it before selection/history/Library behavior.

Go to disables stale mutation/navigation controls while a Reader0 destination
is provisional. The sheet closes only after successful presentation; failure
keeps it open and exposes retry. Remove is durable before the row disappears.
Lifecycle teardown flushes no optimistic state because every annotation action
is synchronously published or rejected.

## First local vertical slice

Port 11 begins with:

- load/recovery of the provider-neutral store;
- toggle at the currently presented Reader0 point;
- deterministic label/excerpt projection;
- current-book ordered list, direct navigation, and removal;
- survival across Activity recreation, process restart, book removal, and
  byte-identical re-import;
- exact rollback on capacity, serialization, synchronization, or atomic-move
  failure; and
- TalkBack, keyboard, 130% text, rotation, reduced-motion, and lifecycle tests.

Highlights, note editing, conflict resolution UI, annotation search/export, and
Drive transport follow on the same envelope; they are not simulated in this
slice. No Google account, OAuth client, Drive scope, cloud resource, signing
key, Play Console setting, or provider library is introduced.

## Offline gates before Drive

Drive work may begin only after deterministic tests prove canonical
round-trip, mutation rollback, restart, corruption/future-version handling,
digest re-import, merge order independence, idempotence, associativity,
concurrent put/delete retention, concurrent-note recovery, and every stated
capacity bound. Emulator UI tests must then pass toggle/list/go-to/remove,
presentation failure, accessibility, recreation, process restart, and the
ordinary Port 7-10 regression matrix before a physical device is requested.

The later Drive adapter will use `drive.appdata` only for hidden manifests and
portable state and `drive.file` only for EPUBs the user approves in a visible
8vo folder. It must provide explicit opt-in, Sync now, progress, errors,
conflicts, retry, metered-network policy, disconnect/revocation, and separate
local/cloud deletion. Broad Drive scope, a proprietary 8vo account, and an 8vo
backend are excluded. The product must make an explicit encryption/threat-model
claim decision before launch.

## Current local-slice validation

On 2026-08-07, the provider-neutral store and bookmark slice passed the exact
dependency guard and architecture audit, compiled for Android x86_64 and
arm64-v8a, and passed these API 36 emulator gates:

- 5/5 annotation store tests and 3/3 bookmark integration tests;
- 108/108 ordinary tests with the external-restart verifier correctly excluded
  from the in-process matrix;
- the separate seed, confirmed force-stop, and fresh-process restart driver,
  including exact bookmark digest/spine/byte recovery;
- 26/26 configuration-compatible tests at actual 130% system text with window,
  transition, and animator scales set to `0.0`; and
- 5/5 fixed-pagination navigation tests at 100% text with the same reduced-
  motion configuration.

An unfiltered 110-test diagnostic passed 109 tests and failed only because it
invoked the external-restart verifier without its seed/force-stop driver; it is
not acceptance evidence. A combined 31-test configuration diagnostic passed 29
tests; its two failures were legacy tests deliberately asserting 100%-text
pagination bounds while the device was at 130%. The authoritative split above
keeps those bounds at 100% and exercises the other 26 tests at 130%.

The emulator crash buffer is empty. System text and all three animation scales
were explicitly restored to `1.0` and read back exact. No physical device,
Google account, Drive API, OAuth configuration, cloud resource, signing key, or
Play Console setting was touched during the emulator gate.

The coordinated API 34 iQOO gate then passed 8/8 focused tests in 5.429
seconds, 108/108 ordinary tests in 204.294 seconds, the separate seed/confirmed-
force-stop/fresh-process restart driver in 1.762 and 1.064 seconds, and 26/26
configuration-compatible tests in 57.182 seconds at actual 130% system text
with normal motion retained. The user accepted bookmark add/list/Go to/remove
touch behavior without a reported issue. With TalkBack 17.0.1 enabled, the user
also accepted the bookmark toggle, success/state change, workspace heading and
count, location/excerpt, Go to, Remove, and Done semantics without a focus,
wording, or navigation issue.

Cleanup restored TalkBack off, touch exploration off, no enabled accessibility
service, rotation off, font `1.0`, and every animation scale explicitly `1.0`.
Live vivo Launcher/Recents behavior rendered 75 frames, so motion restoration
was verified rather than inferred from stored values. The crash buffer is
empty, and no 8vo or test process remains. All 26 original app files totaling
4,751,505 bytes restored byte-exact with pre/post archive SHA-256
`9118B960B212FC67EDC70152314341D8EDEDE0851045335D4B11CF23D79D3699`
and canonical path/length/content manifest SHA-256
`35368BF22698B1C9AA64FB940512C414AA83EFA50B6934B7248DABC71233719B`.
No Google, Drive, OAuth, cloud, signing, Play Console, commit, push, or merge
operation was performed.

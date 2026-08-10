# Android Port 11: provider-neutral catalog discovery and managed EPUB transfer v1

## Status and scope

This contract freezes the ninth independent Port 11 local slice: a bounded,
provider-neutral discovery catalog for managed EPUB identities and a separate
private transfer queue. The 2026-08-10 implementation and API 36 acceptance gate
are complete. The slice remains local-only and uses deterministic simulated
remote catalog, manifest, and chunk inputs. It adds no provider or account
interface, network permission, background worker, clock, OAuth flow, Google
dependency, Drive object, cloud mutation, or Play Console change.

The portable catalog family is `O1LC`. It is an add-only set of EPUB content
identities, not a list of files currently downloaded on a device, an upload
approval, a synchronized deletion log, or a cloud-retention policy. Android's
private catalog review state is `O1LS`. A deterministic per-book content
manifest is `O1BM`. Android's private transfer queue is `O1BQ`.

These four families are independent of annotation `O1AP`/`O1AN`/`O1AS`,
reading-position `O1RP`/`O1RS`, appearance `O1PF`/`O1SS`, progress-choice
`O1PC`/`O1PS`, local progress `O8PG`, and the existing Port 6 local catalog.
They do not reuse another family's actor, device identity, sequence, merge,
review decision, future slot, private file, or coordinator as a generic sync
framework.

## Acceptance evidence

On the selected API 36 emulator, Android compilation is green and the accepted
candidate passes:

- the focused catalog/managed-transfer matrix, 80/80 in 1m53s;
- the preserved appearance, reading-position, progress-choice, and structural-
  navigation matrix, 47/47 in 2m36s;
- the ordinary matrix, excluding only the externally driven restart probe,
  313/313 in 8m53s;
- the external-process seed, 1/1 in 4.564 seconds, followed by an actual
  force-stop with no surviving process and fresh-process verification, 1/1 in
  3.293 seconds; and
- the selected 130% system-text matrix with window, transition, and animator
  scales disabled, 21/21 in 63.351 seconds, followed by explicit restoration
  and readback of font plus all three animation scales at `1.0`.

The final crash and fatal buffers are empty. No physical device, provider, Drive
or account path, network or permission change, security/encryption decision,
background worker, cloud resource, or Play Console operation was part of this
acceptance.

## Existing local boundary

Port 6 already owns the app-private Library, read-only Android document picker,
streamed import, exact EPUB SHA-256, one managed copy per distinct byte string,
Reader0 validation, duplicate reopening, and a catalog containing at most one
built-in sample plus 63 imported books. A managed EPUB remains capped at
512 MiB. Removing a Port 6 row deletes only the app-private copy after the
catalog commit; it never deletes the provider-owned source.

The Port 6 `library.v1` file remains a local product projection. Its paths,
timestamps, imported flag, title encoding, saved Port 6 position, row order,
and built-in sample are not portable. `O1LC` does not wrap, copy, upload, or
reinterpret those bytes. Reader0 remains the only EPUB validator and metadata
authority. The built-in engineering sample is never added to `O1LC` and is
never eligible for managed-book transfer.

Before this slice can claim a downloaded book was durably installed in the
Library, its Port 6 path must be hardened at the used boundary:

- an existing digest-named destination must be streamed through SHA-256 rather
  than trusted because its byte count happens to match;
- catalog publication used by transfer finalization must require descriptor
  synchronization and same-directory `ATOMIC_MOVE`, with no non-atomic move
  fallback;
- an unsupported future catalog must remain byte-exact and block mutation
  instead of later being overwritten from sample-only fallback state;
- malformed current state must be quarantined within a fixed bound before a
  fresh writable state is published; and
- a missing or corrupt managed file must be a visible per-book repair state,
  not silently erase unrelated Library entries.

If that hardening is not implemented in the same slice, qualification stops at
verified staged content and must not describe the book as installed.

## Identity and exclusions

The only portable book identity is the lowercase 64-hex SHA-256 of the exact
complete EPUB bytes. The exact portable entry is `(book_digest, byte_count)`.
`byte_count` is in `1..536870912`. The same bytes under another path, URI,
filename, provider object, device, or database row are the same entry.

A digest paired with unequal byte counts, unequal complete bytes, or unequal
`O1BM` chunk hashes is equivocation or local corruption. It is never resolved
by a title, path, timestamp, provider revision, or device ID.

`O1LC` contains no title, author, cover, path, URI, filename, Port 6 row,
Reader0 anchor, position, annotation, preference, progress value, wall clock,
provider handle, account, upload approval, availability bit, or deletion bit.
Before download, the product can identify a candidate only as an EPUB with a
bounded byte count and a short local display of its digest. A title is shown
only after the exact bytes have passed full hashing and Reader0 validation.
Digest displays are local UI projections and must not enter production logs or
analytics.

## Canonical `O1LC` bytes

All integers are big-endian. Fixed digests are lowercase ASCII. Entries are in
strict ascending raw digest-byte order.

```
u32 magic                 // 0x4f314c43, "O1LC"
u32 version               // 1
u32 entry_field_count     // exactly 3
u32 entry_count           // 0..63
repeat entry_count times:
    ascii[64] book_digest
    i64       byte_count  // 1..536870912
    u32       content_kind // exactly 1, EPUB
u32 crc32                 // IEEE CRC-32 over every preceding byte
```

The minimum object is exactly 20 bytes. One entry is exactly 76 bytes. The
exact version-1 length is `20 + 76 * entry_count`, and the exact maximum is
4,808 bytes. `content_kind` is a fixed EPUB assertion, not a speculative
multi-format dispatch table; every other value is invalid in version 1.
Duplicate or out-of-order digests, invalid hexadecimal, a zero or over-bound
count, an invalid byte count or content kind, a wrong field count, bad CRC,
truncation, or trailing bytes rejects the complete object without mutation.

A recognizable future object consists of the eight-byte `O1LC` magic/version
prefix, an unsigned version greater than 1, and at most exactly 65,536 bytes.
Its remaining bytes are opaque. The first such input is retained byte-for-byte
in private review state and blocks interpretation. Identical replay is
unchanged; a different future input cannot overwrite the retained one. Any
version-1 object above 4,808 bytes or future object above 65,536 bytes is an
input-limit failure and is not staged as a valid catalog.

The checksum detects accidental corruption only. It does not authenticate the
source, prove authorship, prevent rollback, or make a schema-valid hostile
catalog trustworthy.

## Add-only merge and replay

Merge is checked set union by exact `(book_digest, byte_count)`:

- an unseen digest is inserted in canonical order if the result contains at
  most 63 entries;
- the same digest and same byte count is idempotent;
- the same digest and a different byte count rejects the complete input as
  equivocation; and
- a union requiring a 64th distinct digest is `LIMIT` and leaves prior durable
  and in-memory state byte-for-byte exact.

For valid inputs whose union fits the bound, merge is deterministic,
commutative, associative, and idempotent. It compares no timestamps, devices,
rows, paths, revisions, or account values. Replaying any older snapshot cannot
remove, replace, reorder, or re-offer an already decided entry.

The 63-entry cap is deliberately lifetime-monotonic in version 1. Local Remove
does not free a portable slot. There is no eviction, least-recently-used rule,
physical compaction, reset-on-overflow, or partial merge. A limit-rejected
snapshot remains retained for visible review/retry, although version 1 has no
operation that can shrink the set. A later withdrawal/compaction design must
use a separately reviewed version or family with explicit replica and cloud
semantics; this slice does not imply it.

## Local discovery publication

`O1LC` is a local discovery index before it is ever provider data. A managed
import may enter the local set only after the exact bytes, digest, size,
Reader0 validation, managed-file publication, and Port 6 catalog association
are durable. The built-in sample is excluded. Publication unions the one exact
descriptor into `O1LS`; it does not queue or approve an upload.

Existing Port 6 entries cannot be trusted merely because their digest-shaped
filename and stored size agree. Bootstrap into `O1LC` is caller-driven and
streams each managed file through SHA-256 before adding it. It does not block
app startup or ordinary reading, and a failed item remains visible for Retry.
There is no whole-Library optimistic publication.

Failure to publish local discovery state never rolls back an already durable
local import. It retains typed private attention and a retryable exact
descriptor. A later provider may export the resulting `O1LC` only after
explicit sync opt-in and first-sync review; local discovery publication itself
is not consent to transmit either metadata or EPUB content.

## Removal, re-import, and replacement

The existing Library **Remove** action remains local-only. It removes the Port
6 row and app-private managed copy through its local recovery path. It does not
change `O1LC`, create a tombstone, cancel annotations or positions, withdraw a
remote object, or delete cloud data. `O1LS` records a private `LOCAL_REMOVED`
decision for that exact entry so the unchanged discovery record does not
immediately prompt for another download.

An `O1LS` failure does not block or roll back a successful local Remove. It
leaves visible sync attention and an exact suppression Retry. Until that retry
publishes, the product must not interpret absence of the managed file as cloud
deletion or silently manufacture a portable withdrawal.

A byte-identical re-import has the same digest and size. After the managed copy
and Port 6 association are durable, it clears the private local-removal
suppression and reconnects existing digest-keyed `O1RP` positions and `O1AP`
annotations. It does not add a second portable entry.

Different bytes are a different book. Importing a changed file, including one
selected from the same URI or carrying the same title, creates an independent
digest. It never inherits the old book's positions, annotations, decisions,
transfer progress, or identity. Version 1 has no portable replace relation.

Withdrawal from a synchronized Library, removal of an app-created remote EPUB,
and deletion of all 8vo-created cloud data are three distinct later user
actions. None is inferred from local Remove or from absence in an `O1LC`
snapshot.

## Private `O1LS` review state

Android stores private catalog state at
`<files>/port11/library-sync.v1`, magic `O1LS`, version 1, with an exact
131,072-byte cap. It owns only:

- the current canonical `O1LC` set;
- one explicit-Library-open review epoch in `0..Long.MAX_VALUE`;
- at most one decision for each of the 63 entries: `NONE`, `DOWNLOADED`,
  `IGNORED`, `LOCAL_REMOVED`, or `DISMISSED_AT_EPOCH`;
- at most one exact staged current, limit-rejected, or recognizable-future
  `O1LC` input and its SHA-256;
- at most one durable cross-file decision-reconciliation marker binding an
  exact catalog entry to an `O1BQ` transfer attempt; and
- one bounded typed attention/error value.

It contains no EPUB content, chunk, title, path, URI, Port 6 position, provider,
account, credential, network revision, resumable-session token, or wall time.
Decisions bind the exact digest and byte count. Because `O1LC` v1 has no
revision for an existing entry, `IGNORED` and `LOCAL_REMOVED` persist until an
explicit local reset or byte-identical re-import, while a Back dismissal is
only for its recorded review epoch.

An untrusted simulated remote catalog is fully inspected before mutation. A
valid first snapshot is atomically staged with a bounded summary and exact
SHA-256, then requires approval echoing that digest before merge. Invalid,
future, equivocal, and over-bound inputs are never interpreted as entries.
`LIMIT` retains the exact current-version snapshot. A later concrete provider
still requires its own source-trust and first-sync review contract.

Missing private state starts empty without publishing until the first durable
mutation. A recognizable future private version of at most 131,072 bytes is
preserved and blocks. Malformed current state is atomically moved to one of
three fixed quarantine names; inability to quarantine blocks rather than
overwrites evidence. Every publication writes a bounded same-directory
temporary file, flushes and synchronizes it, and uses `ATOMIC_MOVE` with
`REPLACE_EXISTING`. There is no non-atomic fallback. A reported replace failure
is uncertain until an exact bounded reload proves either the prior bytes or the
candidate bytes; any other result blocks visibly.

## Canonical `O1BM` content manifest

`O1BM` describes the raw managed EPUB bytes for exactly one `O1LC` entry. It is
deterministic and provider-neutral; it is not a provider upload session, remote
object descriptor, encryption envelope, or proof of authorship.

```
u32 magic                 // 0x4f31424d, "O1BM"
u32 version               // 1
u32 manifest_field_count  // exactly 4
ascii[64] book_digest     // SHA-256 of the complete raw EPUB bytes
i64 byte_count            // 1..536870912
u32 chunk_bytes           // exactly 4194304 (4 MiB)
u32 chunk_count           // ceil(byte_count / chunk_bytes), 1..128
repeat chunk_count times:
    u8[32] chunk_sha256   // SHA-256 of this exact raw chunk
u32 crc32                 // IEEE CRC-32 over every preceding byte
```

The exact length is `96 + 32 * chunk_count`: 128 bytes for one chunk and
4,192 bytes at the exact 512 MiB maximum. Chunks are zero-based and contiguous.
Chunk `i` starts at `i * 4,194,304`; every non-final chunk is exactly
4,194,304 bytes, and the final chunk is the exact positive remainder, including
4,194,304 when the file size is an exact multiple. The complete digest, byte
count, and every chunk digest must agree with the associated `O1LC` entry.

Duplicate/trailing fields, unknown field counts, invalid digest text, an
incorrect chunk count or size, a bad CRC, or a manifest inconsistent with the
catalog entry rejects the complete input. A recognizable future `O1BM` is the
eight-byte magic/version prefix with unsigned version greater than 1 and a
complete size at most 65,536 bytes. It is retained as opaque bounded input and
never partially interpreted. Version-1 input above 4,192 bytes and future input
above 65,536 bytes are input-limit failures.

The implementation hashes through fixed bounded buffers. It never allocates a
512 MiB byte array and never decompresses EPUB ZIP entries while transferring
raw content. Reader0 validation occurs only after full content verification.

## Private `O1BQ` transfer queue

Android stores the queue at `<files>/port11/book-transfer.v1`, magic `O1BQ`,
version 1, with an exact 1,048,576-byte cap. It retains at most 63 intents and
allows exactly one active transfer. Each intent contains only:

- a cryptographically random 128-bit attempt ID as 32 lowercase hex bytes;
- direction `DOWNLOAD` or `UPLOAD`;
- the exact catalog entry and exact current-version `O1BM` bytes;
- a positive private attempt sequence;
- phase and direction-specific durable intent;
- a sequential completed-chunk prefix in `0..chunk_count`;
- one bounded typed attention/error value; and
- for a user cancellation, a durable cleanup-pending bit until partial bytes
  are actually removed.

Managed-file cleanup intents additionally retain a small purpose enum:
`LOCAL_REMOVE`, `REPAIR_REPLACE`, or `UNCATALOGED`. This distinction is
private and durable so restart recovery cannot turn an explicit repair into a
Library removal, or resurrect a deliberately removed book as a repair.

Queue ordering is ascending digest, with attempt ID as tie-break; it never uses
wall time. At most one recognizable future `O1BM` input of 65,536 bytes may be
retained for attention outside the current entries. A different future input
cannot replace it.

`O1BQ` contains no EPUB content, arbitrary path, URI, title, provider object
handle, revision, account subject, credential, resumable upload URL, access
token, remote folder, wall clock, or retry timer. A later provider adapter must
keep provider-specific session state in its own reviewed, bounded, binding-
scoped recovery record or restart the provider session safely.

The queue file uses the same candidate-first validation, descriptor sync,
mandatory same-directory atomic replace, future preservation, three-slot
quarantine, and uncertain-replace reload rules as `O1LS`. Its state is
independently atomic; no transaction with `O1LS`, Port 6, or managed content is
claimed.

Only one controlled staging file may exist for the active download, under the
managed-document directory so final publication can use a same-directory
atomic move. Its name is derived from the exact digest and attempt ID, never
from remote or EPUB text. The file is at most 512 MiB. Therefore at most one
512 MiB raw partial, not 63 sparse partials totaling roughly 31.5 GiB, is
permitted. A transfer holds at most one 4 MiB chunk plus small hashing buffers
in memory.

## Caller-driven transfer commands

The queue is a serialized, caller-driven state machine. It owns no provider
interface, callback table, worker, service, thread, scheduler, clock, or hidden
retry. At most one command is outstanding. A command/result identity contains
the exact direction, digest, SHA-256 of the complete `O1BM`, attempt sequence,
next chunk index, and a fresh live operation token. Wrong, stale, duplicate,
out-of-order, or cross-attempt results are rejected without durable mutation.

Version 1 transfers chunks strictly in ascending order. A result for any index
other than the next durable prefix is invalid. This is intentionally narrower
than a provider's possible range API and avoids sparse-file and bitmap recovery
ambiguity. A future concrete adapter may map one logical 4 MiB chunk to smaller
provider calls without changing the manifest, but it may acknowledge the chunk
to `O1BQ` only after the exact complete chunk is durably resolved.

Provider result categories remain deferred, but local queue results already
distinguish invalid bytes, wrong chunk, chunk hash mismatch, complete-hash
mismatch, input limit, storage capacity, write/sync/atomic-move failure,
Reader0 rejection, local-catalog failure, cancellation cleanup failure, stale
operation, and future/blocked state. None is collapsed into a generic success.

## Download publication saga

Accepting a candidate is recoverable across the independently atomic files:

1. Validate the exact `O1LC` entry and current `O1BM`, check the 63-book and
   one-active-transfer bounds, allocate an attempt ID, and atomically enqueue
   `O1BQ`. No prompt success is claimed before this step.
2. Atomically reconcile `O1LS` to the exact attempt. If this fails, the durable
   queue entry remains the user's intent and the UI exposes Retry; restart can
   perform the same idempotent reconciliation.
3. For the next chunk only, validate the exact length and SHA-256, append it at
   the exact current prefix, flush and synchronize the staging file, then
   atomically advance the `O1BQ` prefix. Queue state is never advanced before
   raw bytes are durable.
4. After the final chunk, require exact file length, stream every chunk hash
   again, and stream the complete EPUB SHA-256. A mismatch retains visible
   failure and never reaches Reader0 or the managed destination.
5. Ask Reader0 to validate the staged EPUB and obtain its bounded title. A
   hash-valid but unreadable EPUB remains a transfer failure; Android does not
   parse or repair it.
6. Publish `<files>/port6/documents/<digest>.epub` only with a same-directory
   atomic move. If that destination already exists, stream and prove its exact
   size and SHA-256 before treating the step as deduplicated. Unequal existing
   bytes are quarantined or block; they are never overwritten merely because
   the filename or length matches.
7. Atomically add or reconcile the Port 6 local catalog entry through the
   hardened boundary. If the catalog is full or publication fails, the exact
   queue intent remains in `PUBLISH_CATALOG` with visible Retry.
8. Only after the managed bytes and local catalog association are both proved
   may `O1LS` record `DOWNLOADED`, after which `O1BQ` records the local-catalog
   link and finalizes. Failure of either final private write is reconciled from
   the exact managed digest, Port 6 entry, and retained attempt on restart.

An implementation must not claim cross-file atomicity. The ordering makes each
intermediate state recognizable and forward-recoverable. Process death before
queue publication changes nothing; after a chunk write but before prefix
publication, restart sees unclaimed extra bytes and requires bounded
verification before Retry; after managed publication it resumes catalog
association; after catalog association it finalizes private state
idempotently.

Late recovery at `MANAGED_PUBLISHED` or `LOCAL_CATALOG_LINKED` never trusts a
queue marker, digest-shaped filename, stored byte count, or shape-only Port 6
row. It first asks Reader0 to validate the exact managed file and retains the
bounded title only in the live recovery operation. It then runs caller-posted,
4 MiB-at-a-time fused identity steps. Before every step, the Activity rebinds
the exact direction, phase, attempt sequence and ID, digest, byte count, and
manifest hash. The final SHA-256 proof and Port 6 association/title publication
occur in one store step, eliminating a hash-to-catalog callback gap and without
advancing last-opened state. Missing, unequal, or locally conflicting bytes are
converted only through the exact origin-bound `REPAIR_REPLACE` cleanup saga;
catalog-full, blocked, stale, or uncertain publication remains visible retained
Retry state and never deletes the managed bytes.

Cancellation first publishes `CLEANUP_PENDING`, then removes only the
controlled partial. The queue entry is removed only after cleanup succeeds or
the partial is proven absent. Cancellation never deletes an already cataloged
managed EPUB, provider original, position, annotation, or remote object.

## Upload boundary

An upload intent is eligible only for a current imported Port 6 entry whose
managed bytes stream to the exact `O1LC` digest and size and whose `O1BM`
recomputes exactly. Upload is separately and explicitly approved per book; an
`O1LC` entry alone is not approval.

The local queue can yield deterministic sequential upload chunks and retain
their simulated acknowledgements. No real remote commit can be claimed in this
slice. A later adapter must prove the complete remote manifest and exact
content after any lost response, duplicate object, restart, or provider
session replacement. Chunk acknowledgements alone never prove a complete
remote object, and a remote upload never rolls back a successful local import.

## Offer and user-review behavior

A foreign catalog entry is offered only on the Library surface after its
complete `O1LC` snapshot has been validated, staged, reviewed when required,
and merged. It is not offered when:

- the exact managed EPUB and local catalog association already exist;
- the entry is ignored, locally removed, or dismissed for this review epoch;
- an exact transfer is already queued or active;
- the required valid `O1BM` is absent or mismatched;
- local catalog or transfer state is future/corrupt/blocked;
- the Library or queue capacity is exhausted; or
- another modal owns focus.

The prompt says **Book available from another device**, labels it as an EPUB,
shows a bounded human-readable byte count, and offers **Download**, **Not now**,
and an explicit **Don't show again** secondary action. It does not invent or
trust a title before Reader0 validation. **Download** performs the durable
queue-first ordering above. **Not now**, including system Back, records
`DISMISSED_AT_EPOCH`; the unchanged entry may be offered after the next
explicit Library open. Recreation, rotation, pause/resume, and process
recreation of the same Library task do not advance the epoch. **Don't show
again** records `IGNORED` for the exact entry without changing `O1LC` or any
file.

An explicit Library action may clear `IGNORED` or `LOCAL_REMOVED` and request a
download again. A stale detached prompt binds the exact entry, review epoch,
manifest digest, and queue absence; any superseding decision or queue attempt
invalidates its callbacks.

`DOWNLOADED` is not availability proof by itself. On every recovery path it is
checked against an exact managed digest and current Port 6 association. Missing
or unequal bytes become a visible repair state and may be downloaded again
only after explicit user action.

For unequal managed bytes, explicit repair first stages a durable
`REPAIR_REPLACE` cleanup, unlinks the blocked local row, deletes or proves
absent only the digest-derived managed path, resets the exact private decision
for a new download, and only then finalizes cleanup. The replacement download
still follows the ordinary manifest, Reader0, atomic-publication, and catalog
association gates; it never overwrites mismatching bytes in place.

Transfer progress is durable and visible as exact completed chunks and bytes,
not an invented timer. Failure shows a typed explanation and explicit Retry or
Cancel where the durable phase still permits cancellation. Back from retained
working, cleanup, or retry UI is a transient UI defer only: it mutates neither
`O1LS` nor `O1BQ`, stops any live posted identity work, preserves the exact
durable state, exposes Library rows and Add EPUB, and leaves a nonmodal Library
attention action that reopens the exact work. Local reading, import, annotation
editing, position saving, and app startup remain available while a transfer is
queued or failed.

## Accessibility, lifecycle, and motion

The Library prompt and transfer status are product-owned native Android views,
not a generic sync UI framework. They provide a named pane and heading, a
polite live status/error region, at least 48dp actions, touch-mode input focus,
deterministic TalkBack focus, and focus restoration to the invoking Library row
or Add EPUB action. Content wraps and scrolls at 200% text without clipped
actions. State is never conveyed only by color, digest text, animation, or a
transient toast.

While a modal is visible, unrelated Library rows are excluded from the
accessibility tree. Back from a catalog candidate offer is the durable **Not
now** action and records its exact review-epoch dismissal. Back from retained
work or retry is only the transient defer described above; reopening its
nonmodal attention restores the exact prompt and focus without changing durable
bytes. Lifecycle hiding does not accept, ignore, cancel, advance a chunk, or
claim completion. Motion is zero when the active 8vo appearance requests
reduced motion, system animator duration is zero, or touch exploration is
active; correctness never depends on an animation callback.

The prompt never interrupts an active reader. Returning to Library may drain
one eligible catalog review after any higher-priority retained Library failure.
Only one catalog/transfer modal owns focus at a time.

## Hostile, corrupt, future, and over-bound inputs

Every decode, inspect, merge, queue mutation, and saga transition builds and
validates a complete candidate before publication. Failure preserves prior
memory, private bytes, Port 6 rows, managed EPUBs, decisions, and retry input
except for explicitly nonauthoritative staging bytes covered by the durable
prefix rules.

Remote bytes are defensively copied only within their declared caps. Counts and
lengths are checked before allocation or multiplication. `O1LC` input cannot
create a content file or transfer automatically. `O1BM` input cannot create a
partial before explicit Download. Each chunk is bounded to 4 MiB and is hashed
before its durable prefix advances. EPUB ZIP content is never extracted by the
queue; Reader0 performs its own bounded validation after the raw hash succeeds.

CRC32 and SHA-256 establish corruption detection, content equality, and stable
identity. They do not authenticate a device or provider. A schema-valid hostile
party can still advertise arbitrary books, fill the 63-entry lifetime set,
consume one 512 MiB staging allowance after user approval, supply a malicious
but hash-consistent EPUB, or replay an older complete snapshot. These limits
make failure bounded; they do not close the provider threat model.

No digest, chunk hash, EPUB bytes, local or remote title, path, provider token,
account value, staged-snapshot digest, or queue attempt ID may enter production
logs or analytics. User-visible local diagnostic display is not permission to
publish that value off device.

## Desktop and re10 interoperability

Desktop 8vo's `library.v1` uses path-local numeric entry IDs, paths, file
times, local metadata, and local progress. re10's SQLite `source` rows use
numeric IDs, canonical URIs, content-hash text, and clock fields. None is an
`O1LC` entry or `O1BQ` queue.

A future desktop or re10 peer must stream the exact EPUB SHA-256 and byte count,
consume and produce canonical `O1LC`/`O1BM` bytes, map managed/local
availability explicitly, and return durable failures. It must not copy a
database, path, URI hash, row ID, clock, title, or provider filename as
portable identity. `O1BQ` and `O1LS` remain Android-private; another host owns
its own bounded review and transfer recovery.

## Deferred Drive and security decisions

This contract does not authorize a concrete provider. Before any Drive
connection, the product must separately freeze and qualify:

- the launch encryption claim: Google-managed protection only, or a versioned
  authenticated client-side envelope with user-controlled key recovery;
- the hostile-but-schema-valid source policy, malicious EPUB policy,
  rollback/reinstall model, catalog-filling response, and first-sync review;
- domain-separated provider/account/application/logical-object binding
  fingerprints and the behavior when private binding state is missing;
- Google Identity authorization, cancellation, expiration, revocation, and
  reauthorization, kept distinct from authentication;
- exact `drive.appdata` ownership for portable state/manifests and `drive.file`
  ownership for only user-approved EPUBs in an app-created visible 8vo folder;
- provider object naming and metadata leakage, especially whether plaintext
  digest, title, or size is visible when encryption is selected;
- atomic conditional create/replace and opaque revision mapping for `O1LC` and
  `O1BM`, including lost responses and revision conflicts;
- duplicate catalog, manifest, folder, and EPUB object detection and reviewed
  reconciliation without choosing by name or modification time;
- provider resumable-upload/download session handling, range semantics, remote
  hash/readback proof, quota, partial objects, metered policy, battery policy,
  and background execution;
- account switching while local queue entries or provider-specific sessions
  exist;
- four separate actions for disconnect, authorization revocation, local-copy
  deletion, and deletion of 8vo-created cloud data;
- synchronized withdrawal/tombstones, remote content garbage collection,
  reinstall/reconnect, rollback, and proof that deletion is safe across
  participating replicas;
- logging, diagnostics, support export, privacy-policy, OAuth-consent, and Play
  Data safety rules; and
- user-visible first sync, pending counts, progress, errors, retry, conflicts,
  and cloud-deletion consequences.

No `INTERNET` permission, Google library, OAuth client, Drive API call, Cloud
resource, signing key, Play Console mutation, or publication is allowed in this
local slice.

## Accepted local-only vertical slice

The accepted implementation under this contract:

- encode, decode, merge, and export canonical `O1LC` at every exact count and
  byte bound;
- store independent `O1LS` review state and stage deterministic simulated
  remote current/future/invalid/limit snapshots;
- deterministically construct `O1BM` for test-owned EPUB bytes and verify
  one-chunk, exact-4-MiB, multi-chunk, and exact-512-MiB streaming boundaries
  without packaging a production transfer fixture;
- persist `O1BQ`, allow one active sequential simulated download, and accept
  only exact token-bound next chunks;
- show the Library confirmation only for an eligible foreign entry and support
  Download, Not now/Back, Don't show again, Retry, and Cancel;
- validate full bytes through SHA-256 and Reader0, publish a managed copy and
  local catalog association only through the ordered saga, and never claim
  remote upload or provider success;
- recover exact review, completed prefix, retry, cancellation cleanup, managed
  publication, and catalog-finalization state after recreation, restart, and
  confirmed process death;
- retain local positions and annotations across local removal and
  byte-identical re-import while isolating different bytes; and
- leave every prior Port 11 family, dependency, permission, production asset,
  and ordinary reading workflow unchanged.

## Deterministic qualification gates

The accepted API 36 instrumentation gate, completed before any physical-device
review or provider authorization, proves:

- exact `O1LC` min/max lengths, canonical order, CRC, round trip, defensive
  copies, union order independence, idempotence, duplicate equality,
  digest/size equivocation, 63/+1 capacity, stale replay, and exact `LIMIT`
  rollback with retained input;
- exact current/future/over-bound handling and byte-exact preservation for
  `O1LC`, `O1LS`, `O1BM`, and `O1BQ`, including quarantine failure and uncertain
  atomic replacement;
- exact `O1BM` chunk count, offsets, final length, per-chunk hashes, complete
  hash, corrupt chunk, mismatched catalog, and streaming memory bounds;
- one-active/global-staging bounds, strict sequential chunks, wrong/stale
  tokens, queue capacity, attempt exhaustion, storage failure, partial write,
  sync failure, cancellation cleanup, and no implicit retry;
- crash recovery before/after enqueue, chunk sync, prefix publication, complete
  hash, Reader0 validation, managed atomic move, Port 6 association, and final
  private reconciliation;
- preexisting exact destination deduplication and mismatched destination
  quarantine/blocking, never same-length trust;
- no prompt before catalog review/merge, no prompt for local/ignored/removed/
  dismissed/queued entries, Download queue-first durability, Back re-prompt on
  a new explicit Library epoch, explicit ignore reset, and stale callback
  rejection;
- 48dp, touch-mode focus, TalkBack semantics, 200% text, scrolling, focus
  restoration, Back, lifecycle, and reduced-motion behavior;
- local Remove/provider-source preservation, byte-identical re-import, and
  different-bytes isolation; and
- exact dependency guards, architecture audit, dual-ABI build, prior Port
  6-11 regressions, external-process recovery, empty crash/fatal buffers,
  manifest/permission/dependency audit, no production simulated fixture, and
  no provider/network activity.

Only the explicitly selected API 36 emulator may be targeted for the initial
backend and UI gate. Physical review follows only after those local gates pass.
